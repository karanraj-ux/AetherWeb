package com.aetherweb.app
import com.aetherweb.app.MeshNode


import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BleMeshManager(
    private val context: Context,
    // Door 4 (BLE + browser): invoked when a web guest sends chat over the
    // MESH_WEB_CHAR_UUID GATT characteristic. Default no-op keeps the existing
    // single-arg call site compiling.
    private val onWebBleMessage: (text: String, sender: String) -> Unit = { _, _ -> }
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser? get() = bluetoothAdapter?.bluetoothLeAdvertiser

    // Unique UUIDs for our Mesh application
    private val MESH_SERVICE_UUID = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
    private val MESH_CHAR_UUID = UUID.fromString("0000FEAB-0000-1000-8000-00805F9B34FB")
    private val PARCEL_UUID = ParcelUuid(MESH_SERVICE_UUID)
    // Door 4 (BLE + browser): web-guest chat characteristic.
    // Random 128-bit UUID generated once via python3 uuid.uuid4():
    // ca7061c5-06fc-4258-9ed5-e04a8f6a02fa
    private val MESH_WEB_CHAR_UUID = UUID.fromString("ca7061c5-06fc-4258-9ed5-e04a8f6a02fa")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    private val _incomingPackets = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val incomingPackets = _incomingPackets.asSharedFlow()
    
    private val _activeNodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val activeNodes: StateFlow<List<MeshNode>> = _activeNodes.asStateFlow()


    private var isScanning = false
    var emergencyMode = false 
    var longRangeMode = true // BLE Coded PHY (S=8) long-range fallback
    private var isAdvertising = false
    private var gattServer: BluetoothGattServer? = null

    // Door 4 state: web-BLE guests with CCCD notify enabled (device addresses),
    // plus per-device chunk reassembly buffers keyed by msgId.
    private val webBleSubscribed = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val webBleReassembly = ConcurrentHashMap<String, MutableMap<Int, WebBleChunks>>()

    // Door 4 web-BLE chat chunk protocol (20-byte ATT chunks), mirrored by the
    // web client in WebPortalTemplate.kt:
    //   byte0     : type (0x01 = chat JSON)
    //   bytes1-2  : msgId, big-endian uint16
    //   byte3     : seq (0-based)
    //   byte4     : total chunks
    //   bytes5-19 : payload (15 bytes)
    // Payload = UTF-8 JSON {"n":senderName,"t":text}. Max 64 chunks per message
    // (960 payload bytes); oversized or inconsistent messages are dropped.
    private data class WebBleChunks(val total: Int, val chunks: Array<ByteArray?>, var lastSeen: Long)

    enum class BleDutyCycle(val scanWindowMs: Long, val scanRestMs: Long, val label: String) {
        HIGH_ACTIVITY(4000L, 3000L, "High Discovery (57% Duty)"),
        BALANCED(2500L, 10000L, "Balanced Adaptive (20% Duty)"),
        ECO_SAVER(1500L, 18500L, "Battery Saver (7.5% Duty)"),
        CONTINUOUS(0L, 0L, "Continuous Low Latency (100% Duty)")
    }

    var currentDutyCycle: BleDutyCycle = BleDutyCycle.BALANCED
        private set
    private var dutyCycleJob: kotlinx.coroutines.Job? = null
    private var shouldScan = false
    private var lastPeerActivityTime = System.currentTimeMillis()

    fun notifyPeerActivity() {
        lastPeerActivityTime = System.currentTimeMillis()
    }

    fun setDutyCycleMode(mode: BleDutyCycle) {
        currentDutyCycle = mode
        DiagnosticLogger.log("BLE Optimizer", "Duty Cycle", "Adaptive duty cycle mode changed to: ${mode.label}", EventStatus.INFO)
    }

    // Phase 1 thermal/battery safeguard: automatically drop to the ECO_SAVER duty
    // cycle when the screen turns off or the battery runs low (<20%), and restore
    // BALANCED when the screen comes back on or the battery recovers. The mesh
    // keeps working — it just sniffs less often while idle.
    private var throttleReceiver: android.content.BroadcastReceiver? = null
    private var autoThrottleActive = false

    fun initAutoThrottle() {
        if (autoThrottleActive) return
        autoThrottleActive = true
        // Honor the current battery level immediately via the sticky battery broadcast.
        try {
            val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 100) ?: 100
            val scale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level * 100 / scale.coerceAtLeast(1) < 20) {
                setDutyCycleMode(BleDutyCycle.ECO_SAVER)
                android.util.Log.i("BleMeshManager", "Battery below 20% at startup — ECO_SAVER engaged")
            }
        } catch (e: Exception) { }

        throttleReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    android.content.Intent.ACTION_SCREEN_OFF -> {
                        setDutyCycleMode(BleDutyCycle.ECO_SAVER)
                        android.util.Log.i("BleMeshManager", "Screen off — throttling BLE to ECO_SAVER")
                    }
                    android.content.Intent.ACTION_SCREEN_ON -> {
                        if (currentDutyCycle == BleDutyCycle.ECO_SAVER) {
                            setDutyCycleMode(BleDutyCycle.BALANCED)
                            android.util.Log.i("BleMeshManager", "Screen on — restoring BALANCED duty cycle")
                        }
                    }
                    android.content.Intent.ACTION_BATTERY_LOW -> {
                        setDutyCycleMode(BleDutyCycle.ECO_SAVER)
                        android.util.Log.i("BleMeshManager", "Battery low — ECO_SAVER engaged")
                    }
                    android.content.Intent.ACTION_BATTERY_OKAY -> {
                        if (currentDutyCycle == BleDutyCycle.ECO_SAVER) {
                            setDutyCycleMode(BleDutyCycle.BALANCED)
                            android.util.Log.i("BleMeshManager", "Battery recovered — restoring BALANCED duty cycle")
                        }
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_BATTERY_LOW)
            addAction(android.content.Intent.ACTION_BATTERY_OKAY)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(throttleReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(throttleReceiver, filter)
            }
        } catch (e: Exception) {
            android.util.Log.w("BleMeshManager", "Auto-throttle receiver registration failed", e)
        }
    }

    fun releaseAutoThrottle() {
        try { throttleReceiver?.let { context.unregisterReceiver(it) } } catch (e: Exception) { }
        throttleReceiver = null
        autoThrottleActive = false
    }

    val isCodedPhySupported: Boolean
        get() = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && bluetoothAdapter?.isLeCodedPhySupported == true)
    
    // The current payload we are hosting for others to read
    private var currentPayload: ByteArray = ByteArray(0)
    private var pendingPwd: String? = null
    private var pendingIp: String? = null
    
    // Queue for payloads to broadcast
    private val broadcastQueue = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    init {
        scope.launch {
            for (payload in broadcastQueue) {
                val adapter = bluetoothAdapter
                val adv = advertiser
                if (adapter?.isEnabled == true && adv != null) {
                    if (payload.contains("\"sys_handshake\"")) {
                        try {
                            val mainObj = org.json.JSONObject(payload)
                            val innerObj = org.json.JSONObject(mainObj.getString("payload"))
                            val ssid = innerObj.getString("ssid")
                            val pwd = innerObj.getString("pwd")
                            val ip = innerObj.getString("ip")
                            val isPrivate = innerObj.optBoolean("isPrivate", false)
                            
                            // Start with SSID, queue the rest for automatic sequential reads
                            pendingPwd = if (isPrivate) null else pwd
                            pendingIp = ip
                            val chunk = if (isPrivate) "S:PRIVATE|$ssid" else "S:$ssid"
                            val chunks = listOf(chunk) // Just advertise S:, the rest happens on read
                            for (c in chunks) {
                                currentPayload = c.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                                val txPower = if (emergencyMode) android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_HIGH else android.bluetooth.le.AdvertiseSettings.ADVERTISE_TX_POWER_LOW
                                val advMode = if (emergencyMode) android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY else android.bluetooth.le.AdvertiseSettings.ADVERTISE_MODE_BALANCED
                                val data = android.bluetooth.le.AdvertiseData.Builder()
                                    .setIncludeDeviceName(false)
                                    .addServiceUuid(PARCEL_UUID)
                                    .build()
                                val settings = android.bluetooth.le.AdvertiseSettings.Builder()
                                    .setAdvertiseMode(advMode)
                                    .setConnectable(true)
                                    .setTimeout(0)
                                    .setTxPowerLevel(txPower)
                                    .build()
                                try {
                                    adv.stopAdvertising(advertiseCallback)
                                    adv.startAdvertising(settings, data, advertiseCallback)
                                    isAdvertising = true
                                } catch (e: Exception) {}

                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    if (adapter.isLeCodedPhySupported && adapter.isLeExtendedAdvertisingSupported) {
                                        try {
                                            val params = android.bluetooth.le.AdvertisingSetParameters.Builder()
                                                .setLegacyMode(false)
                                                .setConnectable(true)
                                                .setInterval(android.bluetooth.le.AdvertisingSetParameters.INTERVAL_LOW)
                                                .setTxPowerLevel(android.bluetooth.le.AdvertisingSetParameters.TX_POWER_HIGH)
                                                .setPrimaryPhy(android.bluetooth.BluetoothDevice.PHY_LE_CODED)
                                                .setSecondaryPhy(android.bluetooth.BluetoothDevice.PHY_LE_CODED)
                                                .build()
                                            
                                            if (currentAdvertisingSet != null) {
                                                adv.stopAdvertisingSet(advertiseSetCallback as android.bluetooth.le.AdvertisingSetCallback)
                                            }
                                            adv.startAdvertisingSet(params, data, null, null, null, advertiseSetCallback as android.bluetooth.le.AdvertisingSetCallback)
                                        } catch (e: Exception) {}
                                    }
                                }
                                kotlinx.coroutines.delay(2000)
                            }
                            continue
                        } catch(e: Exception) { e.printStackTrace() }
                    }
                    
                    val payloadBytes = payload.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                    // If payload exceeds safe BLE GATT MTU (450 bytes), frame it into BitChat binary chunks with CRC16
                    if (payloadBytes.size > 450) {
                        val groupId = UUID.randomUUID()
                        val rawChunks = payloadBytes.asList().chunked(450).map { it.toByteArray() }
                        val totalChunks = rawChunks.size
                        Log.d("BleMeshManager", "Splitting large BLE payload (${payloadBytes.size} B) into $totalChunks BitChat binary chunks")

                        for (i in 0 until totalChunks) {
                            val chunkData = rawChunks[i]
                            val binaryFrame = BlePacketFramer.encodeChunk(
                                groupId = groupId,
                                totalChunks = totalChunks,
                                chunkIndex = i,
                                ttl = MeshRouter.MAX_TTL_HOPS,
                                flags = BlePacketFramer.FLAG_CHUNK,
                                chunkPayload = chunkData
                            )
                            currentPayload = binaryFrame

                            val txPower = if (emergencyMode) AdvertiseSettings.ADVERTISE_TX_POWER_HIGH else AdvertiseSettings.ADVERTISE_TX_POWER_LOW
                            val advMode = if (emergencyMode) AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY else AdvertiseSettings.ADVERTISE_MODE_BALANCED
                            val settings = AdvertiseSettings.Builder()
                                .setAdvertiseMode(advMode)
                                .setConnectable(true)
                                .setTimeout(0)
                                .setTxPowerLevel(txPower)
                                .build()

                            val data = AdvertiseData.Builder()
                                .setIncludeDeviceName(false)
                                .addServiceUuid(PARCEL_UUID)
                                .build()

                            try {
                                adv.stopAdvertising(advertiseCallback)
                                adv.startAdvertising(settings, data, advertiseCallback)
                                isAdvertising = true
                            } catch (e: Exception) {}
                            delay(1800) // Pacing delay between sequential chunks to avoid GATT congestion
                        }
                    } else {
                        currentPayload = payloadBytes
                        android.util.Log.d("BleMeshManager", "Setting current payload to ${currentPayload.size} bytes")
                        
                        val txPower = if (emergencyMode) AdvertiseSettings.ADVERTISE_TX_POWER_HIGH else AdvertiseSettings.ADVERTISE_TX_POWER_LOW
                        val advMode = if (emergencyMode) AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY else AdvertiseSettings.ADVERTISE_MODE_BALANCED
                        val settings = AdvertiseSettings.Builder()
                            .setAdvertiseMode(advMode)
                            .setConnectable(true)
                            .setTimeout(0)
                            .setTxPowerLevel(txPower)
                            .build()

                        val data = AdvertiseData.Builder()
                            .setIncludeDeviceName(false)
                            .addServiceUuid(PARCEL_UUID)
                            .build()

                        try {
                            adv.stopAdvertising(advertiseCallback)
                            adv.startAdvertising(settings, data, advertiseCallback)
                            isAdvertising = true
                        } catch (e: Exception) {
                            Log.e("BleMeshManager", "Failed to start advertising", e)
                        }
                    }
                }
                delay(4000) // Hold the advertisement for 4 seconds so peers can read it
            }
        }
    }

    // Handshake cache for sequential BLE reads
    private var lastHandshakeSsid: String? = null
    private var lastHandshakePwd: String? = null
    private var lastHandshakeIp: String? = null

    // BitChat-grade binary chunk assembly buffers: GroupID -> Map of ChunkIndex to ByteArray
    private val binaryChunkAssembly = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<Int, ByteArray>>()
    private val binaryChunkExpectation = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val binaryChunkTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun handleBinaryChunkFrame(framed: BlePacketFramer.FramedChunk) {
        val groupId = framed.groupId
        val total = framed.totalChunks
        val index = framed.chunkIndex

        val chunkMap = binaryChunkAssembly.getOrPut(groupId) { java.util.concurrent.ConcurrentHashMap() }
        chunkMap[index] = framed.payload
        binaryChunkExpectation[groupId] = total
        binaryChunkTimestamps[groupId] = System.currentTimeMillis()

        if (chunkMap.size == total) {
            val totalBytes = (0 until total).sumOf { chunkMap[it]?.size ?: 0 }
            val assembled = java.nio.ByteBuffer.allocate(totalBytes)
            for (i in 0 until total) {
                chunkMap[i]?.let { assembled.put(it) }
            }
            binaryChunkAssembly.remove(groupId)
            binaryChunkExpectation.remove(groupId)
            binaryChunkTimestamps.remove(groupId)

            val fullJson = String(assembled.array(), java.nio.charset.StandardCharsets.UTF_8)
            Log.i("BleMeshManager", "BitChat binary reassembly complete: $groupId ($total chunks, $totalBytes bytes)")
            DiagnosticLogger.log("BLE Mesh", "Binary Reassembly", "BitChat frame reassembled ($total chunks, $totalBytes B)", EventStatus.SUCCESS)
            _incomingPackets.tryEmit(fullJson)
        }
        cleanStaleChunks()
    }

    private fun cleanStaleChunks() {
        val now = System.currentTimeMillis()
        val it = binaryChunkTimestamps.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (now - entry.value > 15_000L) { // 15-second chunk assembly timeout
                binaryChunkAssembly.remove(entry.key)
                binaryChunkExpectation.remove(entry.key)
                it.remove()
            }
        }
    }

    // Phase 1: BitChat-Grade GATT Connection Governor (Strictly max 3 concurrent outbound connections)
    private val MAX_CONCURRENT_GATT = 3
    private val gattGovernorSemaphore = java.util.concurrent.Semaphore(MAX_CONCURRENT_GATT, true)
    private val activeGattCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val pendingGattQueue = java.util.concurrent.ConcurrentLinkedQueue<BluetoothDevice>()
    private val gattWatchdogs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    private fun releaseGattSlot(deviceAddress: String) {
        gattWatchdogs.remove(deviceAddress)?.cancel()
        val currentCount = activeGattCount.decrementAndGet()
        try {
            gattGovernorSemaphore.release()
        } catch (e: Exception) {}
        Log.d("BleMeshGovernor", "Released GATT connection slot for $deviceAddress (Active: ${kotlin.math.max(0, currentCount)}/$MAX_CONCURRENT_GATT)")

        // Drain pending queue
        val nextDevice = pendingGattQueue.poll()
        if (nextDevice != null) {
            scope.launch {
                connectAndRead(nextDevice)
            }
        }
    }

    // Keep track of recently read devices to avoid infinite read loops
    private val recentDevices = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d("BleMeshManager", "GATT Server connection state change: $status -> $newState")
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Door 4: drop web-guest state for departed devices.
                webBleSubscribed.remove(device.address)
                webBleReassembly.remove(device.address)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic.uuid == MESH_CHAR_UUID) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
                if (value != null && value.isNotEmpty()) {
                    val framed = BlePacketFramer.decodeChunk(value)
                    if (framed != null) {
                        handleBinaryChunkFrame(framed)
                    } else {
                        val payloadString = String(value, java.nio.charset.StandardCharsets.UTF_8)
                        _incomingPackets.tryEmit(payloadString)
                    }
                }
            } else if (characteristic.uuid == MESH_WEB_CHAR_UUID) {
                // Door 4: web guest -> host chat chunk. Never touches the
                // MESH_CHAR_UUID phone-to-phone flow above.
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
                if (value != null && value.size >= 5) {
                    handleWebBleChunk(device.address, value)
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == MESH_CHAR_UUID) {
                // GATT allows reading in chunks. `offset` tells us where to start.
                val payload = currentPayload
                val value = if (offset in 0 until payload.size) {
                    val length = kotlin.math.min(payload.size - offset, 500)
                    payload.copyOfRange(offset, offset + length)
                } else {
                    ByteArray(0)
                }
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                
                // Automatic Sequential Fallback (SSID -> PWD -> IP)
                val sentStr = String(value, java.nio.charset.StandardCharsets.UTF_8)
                if (sentStr.startsWith("S:")) {
                    if (pendingPwd != null) {
                        currentPayload = ("P:" + pendingPwd).toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                    } else if (pendingIp != null) {
                        currentPayload = ("I:" + pendingIp).toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                    }
                } else if (sentStr.startsWith("P:") && pendingIp != null) {
                    currentPayload = ("I:" + pendingIp).toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                }
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            // Door 4: track CCCD notify subscriptions from web guests.
            // ENABLE_NOTIFICATION_VALUE = {0x01, 0x00}.
            if (descriptor.uuid == CCCD_UUID && descriptor.characteristic?.uuid == MESH_WEB_CHAR_UUID) {
                val enabled = value != null && value.size >= 2 && value[0] == 0x01.toByte()
                if (enabled) webBleSubscribed.add(device.address)
                else webBleSubscribed.remove(device.address)
                Log.d("BleMeshManager", "Web-BLE notify ${if (enabled) "enabled" else "disabled"} for ${device.address}")
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val now = System.currentTimeMillis()
            notifyPeerActivity()
            
            val rssi = result.rssi
            _activeNodes.update { nodes ->
                val existing = nodes.find { it.id == device.address }
                if (existing != null) {
                    nodes.map { if (it.id == device.address) it.copy(signalStrength = rssi) else it }
                } else {
                    val name = device.name ?: "Unknown Device"
                    nodes + MeshNode(device.address, name, rssi)
                }
            }

            // Connectionless Swarm Data: If advertised service data is present, process it directly without connecting GATT
            val serviceData = result.scanRecord?.getServiceData(PARCEL_UUID)
            if (serviceData != null && serviceData.isNotEmpty()) {
                val framed = BlePacketFramer.decodeChunk(serviceData)
                if (framed != null) {
                    handleBinaryChunkFrame(framed)
                    return
                }
                val payloadString = String(serviceData, java.nio.charset.StandardCharsets.UTF_8)
                if (payloadString.startsWith("S:") || payloadString.startsWith("P:") || payloadString.startsWith("I:") || payloadString.startsWith("{")) {
                    _incomingPackets.tryEmit(payloadString)
                    return
                }
            }
            
            // For GATT reads: BitChat-grade adaptive backoff (12s in Balanced/Eco, 5s in High Activity)
            val readCooldown = if (currentDutyCycle == BleDutyCycle.HIGH_ACTIVITY) 5000L else 12000L
            if (!recentDevices.containsKey(device.address) || (now - recentDevices[device.address]!! > readCooldown)) {
                recentDevices[device.address] = now
                connectAndRead(device)
            }
        }
    }
    
    private fun connectAndRead(device: BluetoothDevice) {
        if (!gattGovernorSemaphore.tryAcquire()) {
            // All 3 slots occupied, queue device if queue isn't saturated (< 10)
            if (pendingGattQueue.size < 10 && !pendingGattQueue.contains(device)) {
                pendingGattQueue.offer(device)
                Log.d("BleMeshGovernor", "All 3 GATT slots occupied. Queued ${device.address} (Queue size: ${pendingGattQueue.size})")
            }
            return
        }

        activeGattCount.incrementAndGet()
        Log.d("BleMeshGovernor", "Acquired GATT slot for ${device.address}. Active: ${activeGattCount.get()}/$MAX_CONCURRENT_GATT")

        var activeGattRef: BluetoothGatt? = null
        val watchdog = scope.launch {
            delay(3500L) // 3.5s hard watchdog timeout
            Log.w("BleMeshGovernor", "GATT watchdog timeout (3.5s) for ${device.address}. Forcing disconnect and cleanup.")
            try {
                activeGattRef?.disconnect()
                activeGattRef?.close()
            } catch (e: Exception) {}
            releaseGattSlot(device.address)
        }
        gattWatchdogs[device.address] = watchdog

        activeGattRef = device.connectGatt(context, false, object : BluetoothGattCallback() {
            private var isSlotReleased = false
            private var fullPayloadBytes = ByteArray(0)

            private fun safeClose(gatt: BluetoothGatt) {
                if (!isSlotReleased) {
                    isSlotReleased = true
                    try { gatt.close() } catch (e: Exception) {}
                    releaseGattSlot(device.address)
                }
            }

            private fun handleReceivedData(data: ByteArray?) {
                if (data == null || data.isEmpty()) return

                // BitChat-grade binary frame decoding with CRC16 verification
                val framed = BlePacketFramer.decodeChunk(data)
                if (framed != null) {
                    handleBinaryChunkFrame(framed)
                    return
                }

                val payloadString = String(data, java.nio.charset.StandardCharsets.UTF_8)
                
                if (payloadString.startsWith("S:")) {
                    val rawSsid = payloadString.substring(2)
                    if (rawSsid.startsWith("PRIVATE|")) {
                        lastHandshakeSsid = rawSsid.substring(8)
                        lastHandshakePwd = "PRIVATE"
                    } else {
                        lastHandshakeSsid = rawSsid
                    }
                } else if (payloadString.startsWith("P:")) {
                    lastHandshakePwd = payloadString.substring(2)
                } else if (payloadString.startsWith("I:")) {
                    lastHandshakeIp = payloadString.substring(2)
                    if (lastHandshakeSsid != null && lastHandshakePwd != null) {
                        try {
                            val hsJson = org.json.JSONObject()
                            hsJson.put("type", "handshake")
                            hsJson.put("ssid", lastHandshakeSsid)
                            hsJson.put("pwd", lastHandshakePwd)
                            hsJson.put("ip", lastHandshakeIp)
                            _incomingPackets.tryEmit(hsJson.toString())
                        } catch(e: Exception) {}
                    }
                } else {
                    fullPayloadBytes += data
                    val fullPayloadString = String(fullPayloadBytes, java.nio.charset.StandardCharsets.UTF_8)
                    _incomingPackets.tryEmit(fullPayloadString)
                }
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w("BleMeshManager", "GATT connection failed with status $status, closing immediately")
                    safeClose(gatt)
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BleMeshManager", "Connected to GATT server on ${gatt.device.address}, optimizing link")
                    try {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    } catch (e: Exception) {}
                    val mtuRequested = gatt.requestMtu(512)
                    if (!mtuRequested) {
                        Log.d("BleMeshManager", "requestMtu failed, discovering services immediately")
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BleMeshManager", "Disconnected from GATT server on ${gatt.device.address}")
                    safeClose(gatt)
                }
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                Log.d("BleMeshManager", "MTU changed to $mtu for ${gatt.device.address}, discovering services")
                gatt.discoverServices()
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(MESH_SERVICE_UUID)
                    val char = service?.getCharacteristic(MESH_CHAR_UUID)
                    if (char != null) {
                        Log.d("BleMeshManager", "Found Mesh Characteristic, requesting read")
                        gatt.readCharacteristic(char)
                    } else {
                        Log.w("BleMeshManager", "Mesh Characteristic not found on ${gatt.device.address}")
                        safeClose(gatt)
                    }
                } else {
                    Log.w("BleMeshManager", "Service discovery failed with status $status")
                    safeClose(gatt)
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == MESH_CHAR_UUID) {
                    handleReceivedData(value)
                }
                safeClose(gatt)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == MESH_CHAR_UUID) {
                    handleReceivedData(characteristic.value)
                } else {
                    Log.e("BleMeshManager", "Read failed with status $status")
                }
                safeClose(gatt) // Disconnect after reading and release governor slot
            }
        })
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("BleMeshManager", "Advertising started successfully")
            isAdvertising = true
            DiagnosticLogger.log("BLE", "Advertising", "Started successfully", EventStatus.SUCCESS)
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e("BleMeshManager", "Advertising failed: $errorCode")
            isAdvertising = false
            try { 
                gattServer?.clearServices() 
                gattServer?.close() 
                gattServer = null 
            } catch (e: Exception) {}
            DiagnosticLogger.log("BLE", "Advertising", "Failed with code: $errorCode", EventStatus.ERROR)
        }
    }

    fun startScanning() {
        if (bluetoothAdapter?.isEnabled == true && scanner != null) {
            shouldScan = true
            startGattServer()
            if (dutyCycleJob?.isActive == true) return

            dutyCycleJob = scope.launch {
                Log.d("BleMeshManager", "Started adaptive BLE sniffing engine (${currentDutyCycle.label})")
                DiagnosticLogger.log("BLE Optimizer", "Sniffing Loop", "Started adaptive duty cycle engine: ${currentDutyCycle.label}", EventStatus.SUCCESS)

                while (shouldScan && isActive) {
                    val mode = when {
                        emergencyMode -> BleDutyCycle.CONTINUOUS
                        System.currentTimeMillis() - lastPeerActivityTime < 15000 -> BleDutyCycle.HIGH_ACTIVITY
                        currentDutyCycle == BleDutyCycle.ECO_SAVER -> BleDutyCycle.ECO_SAVER
                        else -> currentDutyCycle
                    }

                    startSingleScanSession()

                    if (mode == BleDutyCycle.CONTINUOUS) {
                        while (shouldScan && emergencyMode && isActive) {
                            kotlinx.coroutines.delay(1000)
                        }
                    } else {
                        kotlinx.coroutines.delay(mode.scanWindowMs)
                        if (!shouldScan || !isActive) break
                        stopSingleScanSession()
                        kotlinx.coroutines.delay(mode.scanRestMs)
                    }
                }
                stopSingleScanSession()
            }
        } else {
             Log.w("BleMeshManager", "Cannot start scanning. Adapter enabled: ${bluetoothAdapter?.isEnabled}, scanner: $scanner")
        }
    }

    private fun startSingleScanSession() {
        val btAdapter = bluetoothAdapter
        val sc = scanner
        if (btAdapter?.isEnabled == true && !isScanning && sc != null) {
            val filter = ScanFilter.Builder()
                .setServiceUuid(PARCEL_UUID)
                .build()
            val settingsBuilder = ScanSettings.Builder()
                .setScanMode(if (emergencyMode) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                settingsBuilder.setPhy(if (emergencyMode || longRangeMode) ScanSettings.PHY_LE_ALL_SUPPORTED else android.bluetooth.BluetoothDevice.PHY_LE_1M)
            }
            val settings = settingsBuilder.build()
            try {
                sc.startScan(listOf(filter), settings, scanCallback)
                isScanning = true
            } catch (e: Exception) {
                Log.e("BleMeshManager", "Failed to start single scan session", e)
            }
        }
    }

    private fun stopSingleScanSession() {
        val sc = scanner
        if (isScanning && sc != null) {
            try {
                sc.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e("BleMeshManager", "Failed to stop single scan session", e)
            }
            isScanning = false
        }
    }
    
    private fun startGattServer() {
        if (gattServer == null && bluetoothManager != null) {
            try {
                gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
                val service = BluetoothGattService(MESH_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                val characteristic = BluetoothGattCharacteristic(
                    MESH_CHAR_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
                )
                service.addCharacteristic(characteristic)
                // Door 4: web-guest chat characteristic (write + notify) with CCCD.
                val webChar = BluetoothGattCharacteristic(
                    MESH_WEB_CHAR_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                )
                val cccd = BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                webChar.addDescriptor(cccd)
                service.addCharacteristic(webChar)
                // Fresh server => no live subscriptions from a previous life.
                webBleSubscribed.clear()
                webBleReassembly.clear()
                gattServer?.addService(service)
                Log.d("BleMeshManager", "Started GATT server with Read/Write support")
            } catch (e: Exception) {
                Log.e("BleMeshManager", "Failed to start GATT server", e)
            }
        }
    }

    // Door 4: reassemble one 20-byte chunk from a web guest. On a complete
    // message, parses {"n":name,"t":text} and invokes onWebBleMessage.
    // Runs on a GATT Binder thread; all downstream handling is thread-safe.
    private fun handleWebBleChunk(deviceAddress: String, chunk: ByteArray) {
        try {
            if (chunk[0] != 0x01.toByte()) return
            val msgId = ((chunk[1].toInt() and 0xFF) shl 8) or (chunk[2].toInt() and 0xFF)
            val seq = chunk[3].toInt() and 0xFF
            val total = chunk[4].toInt() and 0xFF
            if (total < 1 || total > 64 || seq >= total) return
            val payload = chunk.copyOfRange(5, chunk.size)
            val devMap = webBleReassembly.getOrPut(deviceAddress) { mutableMapOf() }
            val now = System.currentTimeMillis()
            // Lazy expiry of stale partial messages.
            val staleKeys = devMap.entries.filter { now - it.value.lastSeen > 30_000 }.map { it.key }
            for (k in staleKeys) devMap.remove(k)
            val msg = devMap.getOrPut(msgId) { WebBleChunks(total, arrayOfNulls(total), now) }
            if (msg.total != total) return
            msg.chunks[seq] = payload
            msg.lastSeen = now
            if (msg.chunks.all { it != null }) {
                devMap.remove(msgId)
                val json = String(
                    msg.chunks.filterNotNull().flatMap { it.asList() }.toByteArray(),
                    StandardCharsets.UTF_8
                )
                val obj = org.json.JSONObject(json)
                val text = obj.optString("t", "")
                if (text.isEmpty()) return
                val name = obj.optString("n", "Web guest").ifBlank { "Web guest" }
                try {
                    onWebBleMessage(text, name)
                } catch (e: Exception) {
                    Log.e("BleMeshManager", "onWebBleMessage failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e("BleMeshManager", "Web-BLE chunk handling failed", e)
        }
    }

    // Door 4 fan-out: push a chat message to every subscribed web-BLE guest.
    // No-op when nobody is subscribed. Per-device try/catch so one dead link
    // can't break the fan-out. Thread-safe.
    fun notifyWebBleGuests(text: String, sender: String) {
        val subscribers = webBleSubscribed.toList()
        if (subscribers.isEmpty()) return
        try {
            val json = "{\"n\":" + org.json.JSONObject.quote(sender) +
                ",\"t\":" + org.json.JSONObject.quote(text) + "}"
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            val total = (bytes.size + 14) / 15
            if (total < 1 || total > 64) return
            val msgId = (System.currentTimeMillis() % 65536).toInt()
            val webChar = gattServer?.getService(MESH_SERVICE_UUID)?.getCharacteristic(MESH_WEB_CHAR_UUID)
                ?: return
            for (i in 0 until total) {
                val start = i * 15
                val end = minOf(start + 15, bytes.size)
                val chunk = ByteArray(20)
                chunk[0] = 0x01
                chunk[1] = (msgId shr 8).toByte()
                chunk[2] = (msgId and 0xFF).toByte()
                chunk[3] = i.toByte()
                chunk[4] = total.toByte()
                System.arraycopy(bytes, start, chunk, 5, end - start)
                // The pre-API-33 notify path mutates characteristic.value, so
                // serialize per-chunk delivery across devices.
                synchronized(webChar) {
                    for (address in subscribers) {
                        try {
                            val device = bluetoothAdapter?.getRemoteDevice(address) ?: continue
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                gattServer?.notifyCharacteristicChanged(device, webChar, false, chunk)
                            } else {
                                @Suppress("DEPRECATION")
                                webChar.value = chunk
                                @Suppress("DEPRECATION")
                                gattServer?.notifyCharacteristicChanged(device, webChar, false)
                            }
                        } catch (e: Exception) {
                            Log.w("BleMeshManager", "Web-BLE notify failed for $address", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BleMeshManager", "notifyWebBleGuests failed", e)
        }
    }

    fun stopScanning() {
        shouldScan = false
        dutyCycleJob?.cancel()
        dutyCycleJob = null
        stopSingleScanSession()
        try { 
            gattServer?.clearServices() 
            gattServer?.close() 
            gattServer = null 
        } catch (e: Exception) {}
        Log.d("BleMeshManager", "Stopped BLE scanning and duty cycle loop")
        DiagnosticLogger.log("BLE Optimizer", "Scanning", "Stopped BLE sniffing loop", EventStatus.INFO)
    }

    fun stopAdvertising() {
        val adv = advertiser
        if (isAdvertising && adv != null) {
            try {
                adv.stopAdvertising(advertiseCallback)
            } catch (e: Exception) {
                Log.e("BleMeshManager", "Failed to stop advertising", e)
            }
            isAdvertising = false
            try { 
                gattServer?.clearServices() 
                gattServer?.close() 
                gattServer = null 
            } catch (e: Exception) {}
            Log.d("BleMeshManager", "Stopped advertising")
        }
    }

    // 3-Tier Long Range Upgrade
    private var currentAdvertisingSet: android.bluetooth.le.AdvertisingSet? = null
    private val advertiseSetCallback = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        object : android.bluetooth.le.AdvertisingSetCallback() {
            override fun onAdvertisingSetStarted(advertisingSet: android.bluetooth.le.AdvertisingSet?, txPower: Int, status: Int) {
                currentAdvertisingSet = advertisingSet
            }
        }
    } else null

    fun pulseBleChunk(chunk: String) {
        val btAdapter = bluetoothAdapter
        val adv = advertiser
        if (btAdapter?.isEnabled == true && adv != null) {
            currentPayload = chunk.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            val txPower = if (emergencyMode) AdvertiseSettings.ADVERTISE_TX_POWER_HIGH else AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            val advMode = if (emergencyMode) AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY else AdvertiseSettings.ADVERTISE_MODE_BALANCED
            
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(PARCEL_UUID)
                .build()
            
            // 1. Start Legacy BLE (Tier 2 - normal range for old phones)
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(advMode)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(txPower)
                .build()
            try {
                adv.stopAdvertising(advertiseCallback)
                adv.startAdvertising(settings, data, advertiseCallback)
                isAdvertising = true
            } catch (e: Exception) {}

            // 2. Start Coded PHY (S=8) Extended Advertising (Tier 3 - 1km range for Android 8+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (btAdapter.isLeCodedPhySupported && btAdapter.isLeExtendedAdvertisingSupported) {
                    try {
                        val params = android.bluetooth.le.AdvertisingSetParameters.Builder()
                            .setLegacyMode(false)
                            .setConnectable(true)
                            .setInterval(android.bluetooth.le.AdvertisingSetParameters.INTERVAL_LOW)
                            .setTxPowerLevel(android.bluetooth.le.AdvertisingSetParameters.TX_POWER_HIGH)
                            .setPrimaryPhy(if (emergencyMode || longRangeMode) android.bluetooth.BluetoothDevice.PHY_LE_CODED else android.bluetooth.BluetoothDevice.PHY_LE_1M)
                            .setSecondaryPhy(if (emergencyMode || longRangeMode) android.bluetooth.BluetoothDevice.PHY_LE_CODED else android.bluetooth.BluetoothDevice.PHY_LE_2M)
                            .build()
                        
                        if (currentAdvertisingSet != null) {
                            adv.stopAdvertisingSet(advertiseSetCallback as android.bluetooth.le.AdvertisingSetCallback)
                        }
                        adv.startAdvertisingSet(params, data, null, null, null, advertiseSetCallback as android.bluetooth.le.AdvertisingSetCallback)
                    } catch (e: Exception) {
                        android.util.Log.e("BleMeshManager", "Failed to start Coded PHY advertising", e)
                    }
                }
            }
        }
    }

    fun broadcastPacket(payload: String) {
        val btAdapter = bluetoothAdapter
        val adv = advertiser
        if (btAdapter?.isEnabled == true && adv != null) {
            broadcastQueue.trySend(payload)
        } else {
            Log.w("BleMeshManager", "Cannot broadcast. Adapter enabled: ${btAdapter?.isEnabled}, advertiser: $adv")
        }
    }

    // Phase 1: Ephemeral DM Pipeline (Connect ➔ Flush ➔ Release in < 600ms)
    fun sendDirectDm(targetDeviceAddress: String, payload: String, onComplete: ((Boolean) -> Unit)? = null) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onComplete?.invoke(false)
            return
        }
        val device = try {
            adapter.getRemoteDevice(targetDeviceAddress)
        } catch (e: Exception) {
            onComplete?.invoke(false)
            return
        }

        scope.launch {
            if (!gattGovernorSemaphore.tryAcquire()) {
                Log.w("BleMeshGovernor", "All 3 GATT slots busy, queuing DM for $targetDeviceAddress")
                pendingGattQueue.offer(device)
                onComplete?.invoke(false)
                return@launch
            }

            activeGattCount.incrementAndGet()
            Log.d("BleMeshGovernor", "Acquired GATT slot for direct DM to $targetDeviceAddress")

            var activeGatt: BluetoothGatt? = null
            var isReleased = false

            fun safeRelease(success: Boolean) {
                if (!isReleased) {
                    isReleased = true
                    try {
                        activeGatt?.disconnect()
                        activeGatt?.close()
                    } catch (e: Exception) {}
                    releaseGattSlot(targetDeviceAddress)
                    onComplete?.invoke(success)
                }
            }

            val watchdog = scope.launch {
                delay(3500L) // 3.5s hard watchdog timeout
                Log.w("BleMeshGovernor", "DM watchdog timeout (3.5s) for $targetDeviceAddress. Forcing release.")
                safeRelease(false)
            }
            gattWatchdogs[targetDeviceAddress] = watchdog

            activeGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                        safeRelease(false)
                        return
                    }
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try {
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        } catch (e: Exception) {}
                        val mtuRequested = gatt.requestMtu(512)
                        if (!mtuRequested) {
                            gatt.discoverServices()
                        }
                    }
                }

                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    super.onMtuChanged(gatt, mtu, status)
                    gatt.discoverServices()
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt.getService(MESH_SERVICE_UUID)
                        val char = service?.getCharacteristic(MESH_CHAR_UUID)
                        if (char != null) {
                            val payloadBytes = payload.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                            val framedPayload = if (payloadBytes.size > 450) {
                                BlePacketFramer.encodeChunk(
                                    groupId = UUID.randomUUID(),
                                    totalChunks = 1,
                                    chunkIndex = 0,
                                    ttl = 1,
                                    flags = BlePacketFramer.FLAG_CHUNK,
                                    chunkPayload = payloadBytes.take(450).toByteArray()
                                )
                            } else {
                                payloadBytes
                            }

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeCharacteristic(char, framedPayload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                            } else {
                                @Suppress("DEPRECATION")
                                char.value = framedPayload
                                @Suppress("DEPRECATION")
                                gatt.writeCharacteristic(char)
                            }
                        } else {
                            safeRelease(false)
                        }
                    } else {
                        safeRelease(false)
                    }
                }

                override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    Log.d("BleMeshGovernor", "DM flushed to ${gatt.device.address} with status $status (<600ms)")
                    safeRelease(status == BluetoothGatt.GATT_SUCCESS)
                }
            })
        }
    }
}
