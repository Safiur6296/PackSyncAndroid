package com.ridesafe.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

/**
 * RideSafeApp is the Application class for the app.
 *
 * In Android (like Java's main application context), this class is instantiated before
 * any activity or service starts. We use it here to:
 * 1. Initialize Firebase services and offline persistence.
 * 2. Create the Notification Channel required by Android 8.0+ (API 26) for our
 *    Foreground Location Tracking Service.
 */
class RideSafeApp : Application() {

    companion object {
        // Notification channel ID for the foreground location tracking service
        const val LOCATION_CHANNEL_ID = "location_tracking_channel"
        // Notification channel ID for critical emergency alerts
        const val EMERGENCY_CHANNEL_ID = "emergency_alerts_channel"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Enable Firebase Realtime Database disk persistence so that if a rider briefly
        // loses cell signal on the highway, updates are queued locally and synced once reconnected.
        try {
            FirebaseDatabase.getInstance("https://ridesafe-a46dc-default-rtdb.asia-southeast1.firebasedatabase.app")
                .setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Persistence must be set before any database reference is created.
        }
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Fallback for default instance
        }

        // 2. Create Notification Channels
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Notification channels are only required on Android 8.0 (API level 26) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return

            val trackingChannelName = getString(R.string.tracking_notification_channel_name)
            val trackingChannelDesc = getString(R.string.tracking_notification_channel_desc)

            // 1. Foreground tracking channel (low importance, no vibration/sound on every update)
            val trackingChannel = NotificationChannel(
                LOCATION_CHANNEL_ID,
                trackingChannelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = trackingChannelDesc
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(trackingChannel)

            // 2. Critical Emergency Alert channel (high importance, heads-up display, vibration & sound)
            val emergencyChannel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                "PackSync Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts when a rider in your convoy triggers emergency status"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }
}
