package com.aetherweb.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtils {

    fun hasInternet(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) || activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    // Prioritize interface names. Lower index = higher priority.
    private val priorityInterfaces = listOf("ap0", "ap1", "swlan0", "wlan1", "wlan0")

    fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            val ips = mutableMapOf<String, String>()
            
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                // Skip inactive or loopback interfaces
                if (intf.isLoopback || !intf.isUp) continue
                
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        ips[intf.name] = ip
                    }
                }
            }

            // 1. Look for known hotspot/wifi interfaces in priority order
            for (intfName in priorityInterfaces) {
                if (ips.containsKey(intfName)) {
                    return ips[intfName]!!
                }
            }

            // 2. Look for ANY interface that starts with "wlan", "ap", "swlan"
            for ((name, ip) in ips) {
                if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")) {
                    return ip
                }
            }

            // 3. Look for standard private IP ranges (Hotspot often uses 192.168.x.x)
            // But skip common cellular interfaces like "rmnet"
            for ((name, ip) in ips) {
                if (name.startsWith("rmnet") || name.startsWith("ccmni")) continue // Skip cellular
                
                if (ip.startsWith("192.168.43.") || ip.startsWith("192.168.49.") || ip.startsWith("192.168.")) {
                    return ip
                }
            }
            
            // 4. Look for ANY non-cellular interface
            for ((name, ip) in ips) {
                if (!name.startsWith("rmnet") && !name.startsWith("ccmni") && !name.startsWith("pdp") && !name.startsWith("cellular")) {
                    return ip
                }
            }

            // 5. Fallback to any available IP
            return ips.values.firstOrNull() ?: "127.0.0.1"

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    /**
     * Network Throttling & Packet Compression:
     * Compresses payloads exceeding threshold using GZIP + Base64 encoding.
     * Shrinks large JSON/canvas/state payloads by 60-80% for weak 2G or satellite links.
     */
    fun compressPayload(text: String): String {
        if (text.length < 180) return text
        return try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val bos = java.io.ByteArrayOutputStream()
            java.util.zip.GZIPOutputStream(bos).use { it.write(bytes) }
            val compressedBytes = bos.toByteArray()
            val base64 = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
            val candidate = "GZ:$base64"
            if (candidate.length < text.length) candidate else text
        } catch (e: Exception) {
            text
        }
    }

    /**
     * Decompresses payload if prefixed with GZ:.
     * Guards against decompression bombs (max 5MB decompressed length) and catches all Throwables.
     */
    fun decompressPayload(payload: String): String {
        if (!payload.startsWith("GZ:")) return payload
        return try {
            val b64 = payload.removePrefix("GZ:")
            val compressedBytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
            val bis = java.io.ByteArrayInputStream(compressedBytes)
            val gis = java.util.zip.GZIPInputStream(bis)
            val maxBytes = 5 * 1024 * 1024 // 5 MB max safe decompressed limit
            val buffer = CharArray(1024)
            val writer = java.io.StringWriter()
            val reader = gis.bufferedReader(Charsets.UTF_8)
            var totalChars = 0
            while (true) {
                val charsRead = reader.read(buffer)
                if (charsRead == -1) break
                totalChars += charsRead
                if (totalChars > maxBytes) {
                    return payload // Decompression bomb defense: abort and return raw
                }
                writer.write(buffer, 0, charsRead)
            }
            writer.toString()
        } catch (t: Throwable) {
            payload
        }
    }
}
