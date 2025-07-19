package com.example.hushbot

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

object DNDHelper {

    private const val TAG = "DNDHelper"

    /**
     * Enable Do Not Disturb mode
     * @param context Application context
     * @return true if successful, false if permission not granted
     */
    fun enableDND(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                try {
                    // Set DND to total silence mode
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                    Log.d(TAG, "DND enabled successfully")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable DND: ${e.message}")
                    false
                }
            } else {
                Log.w(TAG, "DND permission not granted")
                false
            }
        } else {
            // For older Android versions, we can't control DND programmatically
            Log.w(TAG, "DND control not available on this Android version")
            false
        }
    }

    /**
     * Disable Do Not Disturb mode
     * @param context Application context
     * @return true if successful, false if permission not granted
     */
    fun disableDND(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                try {
                    // Set back to normal mode (all notifications allowed)
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    Log.d(TAG, "DND disabled successfully")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to disable DND: ${e.message}")
                    false
                }
            } else {
                Log.w(TAG, "DND permission not granted")
                false
            }
        } else {
            Log.w(TAG, "DND control not available on this Android version")
            false
        }
    }

    /**
     * Check if the app has permission to modify DND settings
     * @param context Application context
     * @return true if permission is granted
     */
    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            false
        }
    }

    /**
     * Open the system settings to grant DND permission
     * @param context Application context
     */
    fun requestPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    /**
     * Get current DND status
     * @param context Application context
     * @return String describing current DND state
     */
    fun getCurrentDNDStatus(context: Context): String {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                "Permission not granted"
            } else {
                when (notificationManager.currentInterruptionFilter) {
                    NotificationManager.INTERRUPTION_FILTER_NONE -> "Total Silence"
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority Only"
                    NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms Only"
                    NotificationManager.INTERRUPTION_FILTER_ALL -> "All Notifications"
                    else -> "Unknown"
                }
            }
        } else {
            "Not supported on this Android version"
        }
    }
}