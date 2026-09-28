package com.aetherweb.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WhatsApp-Style Proximity Sensor Listener & Screen Blackout.
 * Automatically dims/blacks out the screen when the phone is held against the ear
 * during voice calls, preventing accidental touch inputs and conserving battery.
 */
class ProximitySensorHelper(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _isNear = MutableStateFlow(false)
    val isNear: StateFlow<Boolean> = _isNear.asStateFlow()

    private var isListening = false

    fun start() {
        if (isListening || proximitySensor == null) return
        sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        isListening = true
        Log.i("ProximityHelper", "Proximity sensor listener started")
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        _isNear.value = false
        Log.i("ProximityHelper", "Proximity sensor listener stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            // If distance < maxRange or distance == 0, phone is against the ear
            val near = distance < maxRange && distance < 4f
            _isNear.value = near
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
