package com.example.hushbot

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.widget.Toast

class MockLocationHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun enableMockLocation() {
        try {
            // Add your app as a mock location provider
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, false, true, true,
                android.location.Criteria.POWER_LOW,
                android.location.Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            Toast.makeText(context, "Mock location enabled", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to enable mock location: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun setMockLocation(latitude: Double, longitude: Double) {
        try {
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                this.latitude = latitude
                this.longitude = longitude
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                accuracy = 1.0f
            }

            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
            Toast.makeText(context, "Mock location set to: $latitude, $longitude", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to set mock location: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun disableMockLocation() {
        try {
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            Toast.makeText(context, "Mock location disabled", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to disable mock location: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}