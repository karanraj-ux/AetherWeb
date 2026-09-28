package com.aetherweb.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationTracker(private val context: Context, private val viewModel: MeshViewModel) {
    private var locationManager: LocationManager? = null
    private var isTracking = false
    private var lastLocation: Location? = null
    private var broadcastJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isEco = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            // Immediately broadcast new coordinate to mesh
            viewModel.sendLocationMessage(location.latitude, location.longitude)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun startTracking(ecoMode: Boolean = false) {
        if (isTracking && isEco == ecoMode) return
        stopTracking()
        isEco = ecoMode

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        try {
            val minTimeMs = if (ecoMode) 10000L else 3000L
            val minDistanceMeters = if (ecoMode) 5f else 1f

            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                try {
                    locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeMs, minDistanceMeters, locationListener)
                } catch (e: Exception) {}
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                try {
                    locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTimeMs, minDistanceMeters, locationListener)
                } catch (e: Exception) {}
            }

            // Immediately query last known location for zero-wait instant radar positioning
            val lastGps = try { locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
            val lastNet = try { locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }
            val bestKnown = lastGps ?: lastNet
            if (bestKnown != null) {
                lastLocation = bestKnown
                viewModel.sendLocationMessage(bestKnown.latitude, bestKnown.longitude)
            }

            isTracking = true
            
            var lastBroadcastLat = 0.0
            var lastBroadcastLng = 0.0
            var lastBroadcastTime = 0L

            val broadcastInterval = if (ecoMode) 10000L else 4000L
            broadcastJob = scope.launch {
                while (isActive) {
                    val loc = lastLocation
                    val now = System.currentTimeMillis()
                    if (loc != null) {
                        val distMoved = FloatArray(1)
                        Location.distanceBetween(lastBroadcastLat, lastBroadcastLng, loc.latitude, loc.longitude, distMoved)
                        // Broadcast if moved > 3 meters, or periodic 30s keepalive
                        if (distMoved[0] > 3f || (now - lastBroadcastTime >= 30000L)) {
                            lastBroadcastLat = loc.latitude
                            lastBroadcastLng = loc.longitude
                            lastBroadcastTime = now
                            viewModel.sendLocationMessage(loc.latitude, loc.longitude)
                        }
                    }
                    delay(broadcastInterval)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun requestSingleScan() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val lastGps = try { locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
            val lastNet = try { locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }
            val bestKnown = lastGps ?: lastNet
            if (bestKnown != null) {
                lastLocation = bestKnown
                viewModel.sendLocationMessage(bestKnown.latitude, bestKnown.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        broadcastJob?.cancel()
        broadcastJob = null
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
