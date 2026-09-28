package com.aetherweb.app

data class LocationMessage(
    val type: String = "location",
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val timestamp: Long = System.currentTimeMillis()
)
