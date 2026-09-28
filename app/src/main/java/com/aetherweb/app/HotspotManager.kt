package com.aetherweb.app

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HotspotInfo(
    val isActive: Boolean = false,
    val ssid: String = "",
    val password: String = "",
    val error: String? = null
)

class HotspotManager(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    private val _hotspotState = MutableStateFlow(HotspotInfo())
    val hotspotState: StateFlow<HotspotInfo> = _hotspotState.asStateFlow()

    var activeNetworkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    @SuppressLint("MissingPermission")
    fun startHotspot() {
        if (wifiManager == null) {
            _hotspotState.value = HotspotInfo(isActive = false, error = "Wi-Fi Manager not available on this device")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        super.onStarted(reservation)
                        this@HotspotManager.reservation = reservation
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val softApConfig = reservation?.softApConfiguration
                                _hotspotState.value = HotspotInfo(
                                    isActive = true,
                                    ssid = softApConfig?.ssid ?: "Unknown",
                                    password = softApConfig?.passphrase ?: ""
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                val config = reservation?.wifiConfiguration
                                _hotspotState.value = HotspotInfo(
                                    isActive = true,
                                    ssid = config?.SSID ?: "Unknown",
                                    password = config?.preSharedKey ?: ""
                                )
                            }
                            Log.i("HotspotManager", "Hotspot started: ${_hotspotState.value.ssid}")
                            DiagnosticLogger.log("Hotspot", "Started", "SSID: ${_hotspotState.value.ssid}", EventStatus.SUCCESS)
                        } catch (e: Exception) {
                            _hotspotState.value = HotspotInfo(isActive = false, error = "Failed to parse hotspot config: ${e.message}")
                            Log.e("HotspotManager", "Error parsing hotspot configuration", e)
                        }
                    }

                    override fun onStopped() {
                        super.onStopped()
                        reservation = null
                        _hotspotState.value = HotspotInfo(isActive = false)
                        Log.i("HotspotManager", "Hotspot stopped")
                        DiagnosticLogger.log("Hotspot", "Stopped", "Local hotspot turned off", EventStatus.INFO)
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)
                        val errorMsg = when(reason) {
                            1 -> "ERROR_NO_CHANNEL (1): No available Wi-Fi channel."
                            2 -> "ERROR_GENERIC (2): Ensure Wi-Fi and Location are turned on in device Settings, and disable active system hotspots."
                            3 -> "ERROR_INCOMPATIBLE_MODE (3): Another mode is active."
                            4 -> "ERROR_TETHERING_DISALLOWED (4): Tethering is not permitted."
                            else -> "Failed to start: $reason"
                        }
                        _hotspotState.value = HotspotInfo(isActive = false, error = errorMsg)
                        Log.e("HotspotManager", "Hotspot failed: $reason - $errorMsg")
                        DiagnosticLogger.log("Hotspot", "Failed", errorMsg, EventStatus.ERROR)
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                val errorMsg = if (e.message?.contains("Location", ignoreCase = true) == true) {
                    "Precise Location permission is required to create a Local Hotspot. Please enable it in Android Settings."
                } else {
                    e.message
                }
                _hotspotState.value = HotspotInfo(isActive = false, error = errorMsg)
                DiagnosticLogger.log("Hotspot", "Error", errorMsg ?: "Unknown error", EventStatus.ERROR)
            }
        } else {
            _hotspotState.value = HotspotInfo(isActive = false, error = "Requires Android 8.0+")
            DiagnosticLogger.log("Hotspot", "Error", "Requires Android 8.0+", EventStatus.ERROR)
        }
    }

    fun clearError() {
        _hotspotState.value = _hotspotState.value.copy(error = null)
    }

    @SuppressLint("NewApi")
    fun stopHotspot() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            reservation?.close()
        }
        reservation = null
        _hotspotState.value = HotspotInfo(isActive = false)
    }

    @SuppressLint("NewApi")
    fun disconnectFromHotspot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                activeNetworkCallback?.let {
                    connectivityManager.unregisterNetworkCallback(it)
                }
                connectivityManager.bindProcessToNetwork(null)
                activeNetworkCallback = null
                Log.i("HotspotManager", "Unregistered network callback and cleared process binding.")
            } catch (e: Exception) {
                Log.e("HotspotManager", "Error unregistering network", e)
            }
        }
    }

    @SuppressLint("NewApi")
    fun connectToHotspot(ssid: String, pass: String, ip: String? = null) {
        disconnectFromHotspot() // Clear any existing connection request first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifierBuilder = android.net.wifi.WifiNetworkSpecifier.Builder()
                .setSsid(ssid)

            if (pass.length in 8..63) {
                specifierBuilder.setWpa2Passphrase(pass)
            }

            val specifier = specifierBuilder.build()

            val request = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            
            val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)
                    Log.i("HotspotManager", "Connected and bound to hotspot network.")
                    DiagnosticLogger.log("Hotspot", "Connected", "Joined mesh Wi-Fi", EventStatus.SUCCESS)
                    if (ip != null) {
                        MeshNetworkManager.wifiSocketManager?.connectToPeer(ip, 8888, network)
                    }
                }

                override fun onLost(network: android.net.Network) {
                    super.onLost(network)
                    connectivityManager.bindProcessToNetwork(null)
                    Log.i("HotspotManager", "Lost hotspot network connection.")
                    DiagnosticLogger.log("Hotspot", "Disconnected", "Lost mesh Wi-Fi", EventStatus.INFO)
                }
            }
            activeNetworkCallback = networkCallback
            try {
                connectivityManager.requestNetwork(request, networkCallback)
                DiagnosticLogger.log("Hotspot", "Connecting", "Requesting connection to $ssid", EventStatus.INFO)
            } catch (e: Exception) {
                DiagnosticLogger.log("Hotspot", "Error", "Failed to request network: ${e.message}", EventStatus.ERROR)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Auto-connect blocked by Android. Please use Copy Password fallback.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        } else {
            _hotspotState.value = HotspotInfo(isActive = false, error = "Programmatic connection requires Android 10+")
            DiagnosticLogger.log("Hotspot", "Error", "Requires Android 10+ to auto-connect", EventStatus.ERROR)
        }
    }
}
