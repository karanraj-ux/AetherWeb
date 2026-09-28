package com.aetherweb.app

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EventStatus { SUCCESS, INFO, ERROR, PENDING }

data class DiagnosticEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val component: String,
    val action: String,
    val detail: String,
    val status: EventStatus
)

object DiagnosticLogger {
    private const val TAG = "DiagnosticLogger"

    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events: StateFlow<List<DiagnosticEvent>> = _events.asStateFlow()

    fun log(component: String, action: String, detail: String, status: EventStatus = EventStatus.INFO) {
        val event = DiagnosticEvent(
            component = component,
            action = action,
            detail = detail,
            status = status
        )
        _events.update { current ->
            (current + event).takeLast(100) // Keep last 100 events
        }
        val prefix = when(status) {
            EventStatus.SUCCESS -> "✅ "
            EventStatus.INFO -> "ℹ️ "
            EventStatus.ERROR -> "❌ "
            EventStatus.PENDING -> "⏳ "
        }
        Log.d(TAG, "$prefix[$component] $action: $detail")
    }

    fun logWebToMeshHop(messageId: String, sender: String, payload: String, targetInterface: String) {
        log("Router", "Web -> Mesh", "Interface: $targetInterface, ID: $messageId, Sender: $sender, Payload: $payload", EventStatus.SUCCESS)
    }

    fun logMeshToWebHop(messageId: String, sender: String, payload: String) {
        log("Router", "Mesh -> Web", "ID: $messageId, Sender: $sender, Payload: $payload", EventStatus.SUCCESS)
    }
    
    fun logHop(direction: String, messageId: String, detail: String) {
        log("Router", direction, "ID: $messageId, Detail: $detail", EventStatus.INFO)
    }
    
    fun clear() {
        _events.value = emptyList()
    }
}
