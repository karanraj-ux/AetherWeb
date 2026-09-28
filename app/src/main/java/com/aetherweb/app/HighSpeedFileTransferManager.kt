package com.aetherweb.app

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

data class FileTransferProgress(
    val isTransferring: Boolean = false,
    val isSender: Boolean = false,
    val fileName: String = "",
    val totalBytes: Long = 0L,
    val bytesTransferred: Long = 0L,
    val speedMBps: Double = 0.0,
    val progressPercent: Int = 0,
    val isCompleted: Boolean = false,
    val isVerified: Boolean = false,
    val error: String? = null
)

class HighSpeedFileTransferManager(private val context: Context) {
    companion object {
        const val FILE_TRANSFER_PORT = 8890
        const val CHUNK_SIZE = 1024 * 1024 // 1 MB Streaming Buffer
        private const val TAG = "HighSpeedTransfer"
    }

    private val _transferState = MutableStateFlow(FileTransferProgress())
    val transferState: StateFlow<FileTransferProgress> = _transferState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)

    fun startServer() {
        if (isRunning.get()) return
        isRunning.set(true)
        scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(FILE_TRANSFER_PORT))
                }
                Log.i(TAG, "High-Speed 1MB Chunk Server listening on port $FILE_TRANSFER_PORT")

                while (isActive && isRunning.get()) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleIncomingFileStream(clientSocket) }
                }
            } catch (e: Exception) {
                if (isRunning.get()) Log.e(TAG, "Server socket error", e)
            }
        }
    }

    fun stopServer() {
        isRunning.set(false)
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
    }

    // ==========================================
    // 1. SENDER: 1MB Chunk Streaming
    // ==========================================
    fun sendFile(
        peerIp: String,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        onComplete: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            _transferState.update {
                FileTransferProgress(
                    isTransferring = true,
                    isSender = true,
                    fileName = fileName,
                    totalBytes = fileSize,
                    bytesTransferred = 0L
                )
            }

            var socket: Socket? = null
            try {
                // Calculate SHA-256 hash for integrity verification
                val sha256 = calculateSha256(uri)

                socket = Socket().apply {
                    tcpNoDelay = true
                    sendBufferSize = 2 * CHUNK_SIZE
                    connect(InetSocketAddress(peerIp, FILE_TRANSFER_PORT), 8000)
                }

                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), CHUNK_SIZE))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                // 1. Send Manifest
                val manifest = JSONObject().apply {
                    put("fileName", fileName)
                    put("fileSize", fileSize)
                    put("sha256", sha256)
                    put("chunkSize", CHUNK_SIZE)
                }.toString()

                out.writeUTF(manifest)
                out.flush()

                // 2. Wait for receiver ACK
                val ack = input.readUTF()
                if (ack != "READY_TO_STREAM") {
                    throw IOException("Receiver rejected stream: $ack")
                }

                // 3. Adaptive Chunking & Network Throttling for Weak 2G / Satellite Links
                val isDataSaver = MeshNetworkManager._uiState.value.isDataSaverEnabled
                val effectiveChunkSize = if (isDataSaver) 32 * 1024 else CHUNK_SIZE
                val buffer = ByteArray(effectiveChunkSize)
                var bytesSent = 0L
                var lastTime = System.currentTimeMillis()
                var lastBytes = 0L

                if (isDataSaver) {
                    DiagnosticLogger.log("Network Optimizer", "Throttled Transfer", "Applying 2G/Satellite rate throttling with 32KB pacing", EventStatus.INFO)
                }

                val contentStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Failed to open file stream for $uri")
                contentStream.use { fileIn ->
                    while (isActive) {
                        val read = fileIn.read(buffer, 0, effectiveChunkSize)
                        if (read == -1) break

                        out.write(buffer, 0, read)
                        bytesSent += read

                        // Network Throttling: Pace 2G/Satellite transmissions to prevent buffer overflows
                        if (isDataSaver) {
                            kotlinx.coroutines.delay(30)
                        }

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 400) {
                            val speed = ((bytesSent - lastBytes).toDouble() / (1024.0 * 1024.0)) / (elapsed.toDouble() / 1000.0)
                            val percent = if (fileSize > 0) ((bytesSent * 100) / fileSize).toInt() else 0
                            _transferState.update {
                                it.copy(
                                    bytesTransferred = bytesSent,
                                    speedMBps = (speed * 10).toInt() / 10.0,
                                    progressPercent = percent
                                )
                            }
                            lastTime = now
                            lastBytes = bytesSent
                        }
                    }
                    out.flush()
                }

                // 4. Verify Integrity ACK
                val verifyResult = input.readUTF()
                val isVerified = verifyResult == "VERIFIED_OK"

                _transferState.update {
                    it.copy(
                        isTransferring = false,
                        isCompleted = true,
                        isVerified = isVerified,
                        progressPercent = 100
                    )
                }
                withContext(Dispatchers.Main) {
                    onComplete(isVerified, if (isVerified) null else "Checksum mismatch")
                }
            } catch (e: Exception) {
                Log.e(TAG, "File transfer failed", e)
                _transferState.update {
                    it.copy(isTransferring = false, error = e.localizedMessage)
                }
                withContext(Dispatchers.Main) {
                    onComplete(false, e.localizedMessage)
                }
            } finally {
                try { socket?.close() } catch (e: Exception) {}
            }
        }
    }

    // ==========================================
    // 2. RECEIVER: Direct High-Speed Ingestion
    // ==========================================
    private suspend fun handleIncomingFileStream(socket: Socket) = withContext(Dispatchers.IO) {
        var inputStream: DataInputStream? = null
        var outputStream: DataOutputStream? = null

        try {
            socket.tcpNoDelay = true
            socket.receiveBufferSize = 2 * CHUNK_SIZE

            inputStream = DataInputStream(BufferedInputStream(socket.getInputStream(), CHUNK_SIZE))
            outputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            // 1. Read Manifest
            val manifestJson = inputStream.readUTF()
            val manifest = JSONObject(manifestJson)
            val rawFileName = manifest.getString("fileName")
            val fileName = MeshStorageManager.sanitizeFileName(rawFileName)
            val fileSize = manifest.getLong("fileSize")
            val expectedSha256 = manifest.getString("sha256")

            val destFile = MeshStorageManager.getCacheFile(context, fileName)
            val tempFile = File(destFile.parentFile, "${destFile.name}.download")

            _transferState.update {
                FileTransferProgress(
                    isTransferring = true,
                    isSender = false,
                    fileName = fileName,
                    totalBytes = fileSize,
                    bytesTransferred = 0L
                )
            }

            // 2. Acknowledge Ready
            outputStream.writeUTF("READY_TO_STREAM")
            outputStream.flush()

            // 3. Receive 1MB chunks and compute SHA-256 rolling digest
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(CHUNK_SIZE)
            var totalReceived = 0L
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            FileOutputStream(tempFile).use { fileOut ->
                while (totalReceived < fileSize) {
                    val remaining = fileSize - totalReceived
                    val toRead = minOf(CHUNK_SIZE.toLong(), remaining).toInt()
                    val read = inputStream.read(buffer, 0, toRead)
                    if (read == -1) break

                    fileOut.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    totalReceived += read

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastTime
                    if (elapsed >= 400) {
                        val speed = ((totalReceived - lastBytes).toDouble() / (1024.0 * 1024.0)) / (elapsed.toDouble() / 1000.0)
                        val percent = if (fileSize > 0) ((totalReceived * 100) / fileSize).toInt() else 0
                        _transferState.update {
                            it.copy(
                                bytesTransferred = totalReceived,
                                speedMBps = (speed * 10).toInt() / 10.0,
                                progressPercent = percent
                            )
                        }
                        lastTime = now
                        lastBytes = totalReceived
                    }
                }
                fileOut.flush()
            }

            // 4. Verify SHA-256 hash and file size completeness
            val calculatedHash = digest.digest().joinToString("") { "%02x".format(it) }
            val isVerified = (totalReceived == fileSize) && calculatedHash.equals(expectedSha256, ignoreCase = true)

            try {
                outputStream.writeUTF(if (isVerified) "VERIFIED_OK" else "CHECKSUM_FAIL")
                outputStream.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send verification status to sender", e)
            }

            if (isVerified) {
                // Atomically move completed verified file to destination
                if (destFile.exists()) destFile.delete()
                tempFile.renameTo(destFile)
                // Storage Footprint: Automatically enforce 100MB threshold on incoming media
                MeshStorageManager.autoPurgeCache(context)
            } else {
                tempFile.delete() // Clean up truncated or corrupt download
            }

            _transferState.update {
                it.copy(
                    isTransferring = false,
                    isCompleted = true,
                    isVerified = isVerified,
                    progressPercent = 100,
                    error = if (!isVerified) "File incomplete or checksum mismatch" else null
                )
            }

            if (isVerified) {
                // Post to Room Database and Chat UI
                val ip = NetworkUtils.getLocalIpAddress()
                val url = "http://$ip:8080/files/${android.net.Uri.encode(fileName)}"
                val msg = "📂 Received 1GB+ High-Speed File: $fileName (${(fileSize / (1024 * 1024))} MB) [SHA-256 Verified] $url"

                com.aetherweb.app.data.MeshChatDatabase.getDatabase(context).chatDao().insertMessage(
                    com.aetherweb.app.data.ChatMessageEntity(
                        senderId = "high_speed_transfer",
                        senderName = "Wi-Fi Direct High-Speed Transfer",
                        message = msg,
                        timestamp = System.currentTimeMillis(),
                        isFromMe = false
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving high-speed stream", e)
            _transferState.update {
                it.copy(isTransferring = false, error = e.localizedMessage)
            }
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun calculateSha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_SIZE)
        val stream = context.contentResolver.openInputStream(uri) 
            ?: throw FileNotFoundException("Cannot open stream for URI: $uri")
        stream.use { s ->
            while (true) {
                val read = s.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
