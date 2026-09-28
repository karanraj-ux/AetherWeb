package com.aetherweb.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object LocalDnsServer {
    private var socket: DatagramSocket? = null
    private var isRunning = false
    
    fun start(hostIp: String) {
        if (isRunning) return
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Attempt to bind to DNS port 53 (Note: typically requires root on Android, but some custom enterprise/educational OS builds allow it, or it will fail gracefully here)
                socket = DatagramSocket(53)
                isRunning = true
                Log.i("LocalDnsServer", "DNS Server bound to port 53. Intercepting 'nexus.run' -> $hostIp")
                
                val buffer = ByteArray(1024)
                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    
                    // Super basic DNS response intercepting "nexus.run"
                    // Real implementation would parse the DNS query header and questions
                    // For the sake of the Citadel, we blindly return the A record for the Host IP
                    val response = buildFakeDnsResponse(packet.data, packet.length, hostIp)
                    if (response != null) {
                        val responsePacket = DatagramPacket(response, response.size, packet.address, packet.port)
                        socket?.send(responsePacket)
                    }
                }
            } catch (e: Exception) {
                Log.w("LocalDnsServer", "Could not bind DNS port 53 (requires root on standard Android). Falling back to mDNS / captive portal routing. Error: ${e.message}")
            }
        }
    }
    
    fun stop() {
        isRunning = false
        socket?.close()
        socket = null
    }
    
    private fun buildFakeDnsResponse(query: ByteArray, length: Int, hostIp: String): ByteArray? {
        if (length < 12) return null
        
        val response = ByteArray(length + 16)
        System.arraycopy(query, 0, response, 0, length)
        
        // Modify Header: Response flag
        response[2] = (response[2].toInt() or 0x80).toByte() 
        // Answer RRs: 1
        response[7] = 1
        
        // Append Answer (Pointer to Question, Type A, Class IN, TTL, Data Length, IP)
        var offset = length
        response[offset++] = 0xC0.toByte()
        response[offset++] = 0x0C.toByte()
        // Type A (1)
        response[offset++] = 0x00
        response[offset++] = 0x01
        // Class IN (1)
        response[offset++] = 0x00
        response[offset++] = 0x01
        // TTL (60)
        response[offset++] = 0x00
        response[offset++] = 0x00
        response[offset++] = 0x00
        response[offset++] = 0x3C
        // Data Length (4)
        response[offset++] = 0x00
        response[offset++] = 0x04
        
        // IP Address Bytes (guaranteed 4-byte IPv4 A-record)
        val ipBytes = try {
            val addr = InetAddress.getByName(hostIp.trim())
            if (addr is java.net.Inet4Address) addr.address else null
        } catch (e: Exception) {
            null
        } ?: return null

        response[offset++] = ipBytes[0]
        response[offset++] = ipBytes[1]
        response[offset++] = ipBytes[2]
        response[offset++] = ipBytes[3]
        
        return response.copyOf(offset)
    }
}
