package com.example.hushbot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
        private const val CHANNEL_ID = "geofence_notifications"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "GeofenceBroadcastReceiver triggered")

        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            showToast(context, "Geofence error: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        Log.d(TAG, "Geofence transition: $geofenceTransition")

        // Process each triggered geofence
        geofencingEvent.triggeringGeofences?.forEach { geofence ->
            val name = geofence.requestId

            when (geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    Log.d(TAG, "Entered $name - Activating DND")
                    val success = DNDHelper.enableDND(context)
                    val message = if (success) {
                        "Entered $name - DND activated"
                    } else {
                        "Entered $name - DND activation failed (check permissions)"
                    }
                    showNotification(context, "Geofence Alert", message)
                    showToast(context, message)
                }

                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    Log.d(TAG, "Exited $name - Deactivating DND")
                    val success = DNDHelper.disableDND(context)
                    val message = if (success) {
                        "Exited $name - DND deactivated"
                    } else {
                        "Exited $name - DND deactivation failed (check permissions)"
                    }
                    showNotification(context, "Geofence Alert", message)
                    showToast(context, message)
                }

                else -> {
                    Log.w(TAG, "Unknown transition type: $geofenceTransition")
                    return@forEach
                }
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android 8.0 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Geofence Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for geofence entry/exit events"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}