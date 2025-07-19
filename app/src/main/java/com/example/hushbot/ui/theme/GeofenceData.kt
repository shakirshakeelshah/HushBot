package com.example.hushbot

data class GeofenceData(
   val name: String,
   val latitude: Double,
   val longitude: Double,
   val radius: Float,
   var enabled: Boolean = true
)
