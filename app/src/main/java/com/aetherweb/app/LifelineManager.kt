package com.aetherweb.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.io.InputStreamReader
import java.io.BufferedReader

object LifelineManager {
    
    fun handleLifelineRequest(context: Context, payload: JSONObject, meshRouter: MeshRouter) {
        val reqId = payload.optString("reqId")
        val targetUrl = payload.optString("url")
        val body = payload.optString("body")
        val method = payload.optString("method", "GET")
        val senderId = payload.optString("senderId")

        if (reqId.isEmpty() || targetUrl.isEmpty()) return
        
        if (!NetworkUtils.hasInternet(context)) {
            Log.i("LifelineManager", "Received lifeline request, but I have no internet to proxy it.")
            return
        }
        
        Log.i("LifelineManager", "I have internet! Fulfilling lifeline request $reqId for $senderId")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(targetUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                
                if (method == "POST" && body.isNotEmpty()) {
                    conn.doOutput = true
                    val writer = OutputStreamWriter(conn.outputStream)
                    writer.write(body)
                    writer.flush()
                    writer.close()
                }
                
                val responseCode = conn.responseCode
                val reader = BufferedReader(InputStreamReader(if (responseCode in 200..299) conn.inputStream else conn.errorStream))
                val responseText = reader.readText()
                reader.close()
                
                val responsePacket = com.aetherweb.app.protocol.MeshPacket.LifelineResponse(
                    reqId = reqId,
                    status = responseCode,
                    response = responseText,
                    recipientId = senderId
                )
                meshRouter.routePacket(responsePacket)
                
            } catch (e: Exception) {
                Log.e("LifelineManager", "Lifeline proxy request failed", e)
                val errorPacket = com.aetherweb.app.protocol.MeshPacket.LifelineResponse(
                    reqId = reqId,
                    status = 500,
                    response = e.message ?: "Unknown error",
                    recipientId = senderId
                )
                meshRouter.routePacket(errorPacket)
            }
        }
    }
}
