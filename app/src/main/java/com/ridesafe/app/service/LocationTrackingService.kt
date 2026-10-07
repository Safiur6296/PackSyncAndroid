package com.ridesafe.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ridesafe.app.MainActivity
import com.ridesafe.app.R
import com.ridesafe.app.RideSafeApp
import com.ridesafe.app.data.model.Rider
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.data.repository.RideRepository
import com.ridesafe.app.util.JoinNotificationHelper
import com.ridesafe.app.util.LocationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * LocationTrackingService is a Foreground Service that continuously tracks GPS location
 * and syncs it to Firebase Realtime Database.
 *
 * It also actively observes the convoy's riders: when another rider sets status to EMERGENCY,
 * it immediately triggers a high-priority heads-up notification with their name and calculated
 * distance, and vibrates the receiver's phone.
 */
class LocationTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = RideRepository()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var currentRideCode: String = ""
    private var currentRiderId: String = ""
    private var currentRiderName: String = ""
    private var currentStatus: RiderStatus = RiderStatus.RIDING

    // Emergency tracking
    private var emergencyObservationJob: Job? = null
    private var joinRequestObservationJob: Job? = null
    private var sessionObservationJob: Job? = null
    private var currentSessionCreatorId: String = ""
    private var latestConvoyRiders: List<Rider> = emptyList()
    private val activeEmergencyRiders = mutableMapOf<String, Rider>()
    private var lastKnownLat: Double = 0.0
    private var lastKnownLng: Double = 0.0

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EMERGENCY_NOTIFICATION_BASE_ID = 2000

        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val ACTION_UPDATE_STATUS = "ACTION_UPDATE_STATUS"

        const val EXTRA_RIDE_CODE = "EXTRA_RIDE_CODE"
        const val EXTRA_RIDER_ID = "EXTRA_RIDER_ID"
        const val EXTRA_RIDER_NAME = "EXTRA_RIDER_NAME"
        const val EXTRA_STATUS = "EXTRA_STATUS"

        /**
         * Helper method to start or update the tracking service from an Activity or Composable.
         */
        fun startTracking(
            context: Context,
            rideCode: String,
            riderId: String,
            riderName: String
        ) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RIDE_CODE, rideCode)
                putExtra(EXTRA_RIDER_ID, riderId)
                putExtra(EXTRA_RIDER_NAME, riderName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Helper method to update the stop status (e.g. Refueling, Emergency).
         */
        fun updateStatus(context: Context, status: RiderStatus) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_UPDATE_STATUS
                putExtra(EXTRA_STATUS, status.name)
            }
            context.startService(intent)
        }

        /**
         * Helper method to stop the tracking service.
         */
        fun stopTracking(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentRideCode = intent.getStringExtra(EXTRA_RIDE_CODE) ?: ""
                currentRiderId = intent.getStringExtra(EXTRA_RIDER_ID) ?: ""
                currentRiderName = intent.getStringExtra(EXTRA_RIDER_NAME) ?: "Rider"

                android.util.Log.d("RideSafeDebug", "[Service] onStartCommand ACTION_START: rideCode='$currentRideCode', riderId='$currentRiderId', name='$currentRiderName'")
                startInForeground()
                startLocationUpdates()
                startObservingEmergencyAlerts()
                startObservingJoinRequests()
            }
            ACTION_UPDATE_STATUS -> {
                val statusName = intent.getStringExtra(EXTRA_STATUS)
                currentStatus = RiderStatus.fromString(statusName)

                // Push status update to Firebase
                if (currentRideCode.isNotEmpty() && currentRiderId.isNotEmpty()) {
                    repository.updateStatus(currentRideCode, currentRiderId, currentStatus)
                }
                // Update notification text to reflect new status
                val notification = buildNotification()
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                clearEmergencyAlerts()
                stopObservingJoinRequests()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // START_STICKY tells the OS to recreate the service if killed due to memory pressure
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        // Tapping the notification brings the rider back to MainActivity
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action button directly on the notification to Stop/Leave Ride
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (currentStatus == RiderStatus.RIDING) {
            "Sharing live GPS with Ride $currentRideCode"
        } else {
            "Status: ${currentStatus.emoji} ${currentStatus.displayName} (Ride $currentRideCode)"
        }

        return NotificationCompat.Builder(this, RideSafeApp.LOCATION_CHANNEL_ID)
            .setContentTitle("PackSync • Never Lose Your Pack")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true) // Cannot be swiped away by user while ride is active
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Leave Ride", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // -------------------------------------------------------------
    // EMERGENCY NOTIFICATION & VIBRATION DISPATCHER
    // -------------------------------------------------------------

    private fun startObservingEmergencyAlerts() {
        emergencyObservationJob?.cancel()
        if (currentRideCode.isEmpty()) return

        emergencyObservationJob = serviceScope.launch {
            repository.observeRiders(currentRideCode)
                .catch { e ->
                    android.util.Log.e("RideSafeDebug", "[Service] Error observing emergencies: ${e.message}", e)
                }
                .collect { riders ->
                    handleRidersEmergencyStatus(riders)
                }
        }
    }

    private fun handleRidersEmergencyStatus(riders: List<Rider>) {
        latestConvoyRiders = riders
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return

        // Filter other riders currently in EMERGENCY state
        val currentEmergencies = riders.filter {
            it.id != currentRiderId && it.riderStatus == RiderStatus.EMERGENCY
        }
        val currentEmergencyIds = currentEmergencies.map { it.id }.toSet()

        // 1. Cancel notifications for riders who resolved their emergency or left
        val resolvedRiderIds = activeEmergencyRiders.keys - currentEmergencyIds
        for (resolvedId in resolvedRiderIds) {
            val notifId = EMERGENCY_NOTIFICATION_BASE_ID + ((resolvedId.hashCode() and 0x7FFFFFFF) % 10000)
            notificationManager.cancel(notifId)
            activeEmergencyRiders.remove(resolvedId)
        }

        // 2. Post / update notification and vibrate for active emergencies
        for (rider in currentEmergencies) {
            val isNewEmergency = !activeEmergencyRiders.containsKey(rider.id)
            activeEmergencyRiders[rider.id] = rider

            val distanceStr = calculateDistanceString(rider.lat, rider.lng)
            val notifId = EMERGENCY_NOTIFICATION_BASE_ID + ((rider.id.hashCode() and 0x7FFFFFFF) % 10000)
            val notification = buildEmergencyNotification(rider, distanceStr)

            notificationManager.notify(notifId, notification)

            // Vibrate receiver device immediately when a new emergency is declared!
            if (isNewEmergency) {
                triggerEmergencyVibration()
            }
        }
    }

    private fun calculateDistanceString(riderLat: Double, riderLng: Double): String {
        return if (lastKnownLat != 0.0 && lastKnownLng != 0.0 && riderLat != 0.0 && riderLng != 0.0) {
            val distMeters = LocationUtils.calculateDistanceMeters(lastKnownLat, lastKnownLng, riderLat, riderLng)
            "${LocationUtils.formatDistance(distMeters)} away"
        } else {
            "Locating GPS..."
        }
    }

    private fun buildEmergencyNotification(rider: Rider, distanceStr: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            rider.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val riderName = rider.name.ifEmpty { "Rider" }
        val title = "🚨 EMERGENCY ALERT: $riderName"
        val body = "$riderName needs urgent assistance! Distance: $distanceStr"

        val vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)

        return NotificationCompat.Builder(this, RideSafeApp.EMERGENCY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$body\nTap immediately to view their location on the convoy map.")
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpenApp)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(vibrationPattern)
            .setLights(android.graphics.Color.RED, 500, 500)
            .build()
    }

    private fun triggerEmergencyVibration() {
        try {
            android.util.Log.d("RideSafeDebug", "[Service] Triggering emergency vibration on receiver phone!")
            val pattern = longArrayOf(0, 500, 200, 500, 200, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RideSafeDebug", "[Service] Error triggering emergency vibration: ${e.message}", e)
        }
    }

    private fun refreshEmergencyDistances() {
        if (activeEmergencyRiders.isEmpty()) return
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return

        for ((id, rider) in activeEmergencyRiders) {
            val distanceStr = calculateDistanceString(rider.lat, rider.lng)
            val notifId = EMERGENCY_NOTIFICATION_BASE_ID + ((id.hashCode() and 0x7FFFFFFF) % 10000)
            val notification = buildEmergencyNotification(rider, distanceStr)
            notificationManager.notify(notifId, notification)
        }
    }

    private fun clearEmergencyAlerts() {
        emergencyObservationJob?.cancel()
        emergencyObservationJob = null

        val notificationManager = getSystemService(NotificationManager::class.java)
        for (id in activeEmergencyRiders.keys) {
            val notifId = EMERGENCY_NOTIFICATION_BASE_ID + ((id.hashCode() and 0x7FFFFFFF) % 10000)
            notificationManager?.cancel(notifId)
        }
        activeEmergencyRiders.clear()
    }

    private fun startObservingJoinRequests() {
        joinRequestObservationJob?.cancel()
        sessionObservationJob?.cancel()
        if (currentRideCode.isEmpty()) return

        sessionObservationJob = serviceScope.launch {
            repository.observeSession(currentRideCode)
                .catch { }
                .collect { session ->
                    currentSessionCreatorId = session?.createdBy ?: ""
                }
        }

        joinRequestObservationJob = serviceScope.launch {
            repository.observeJoinRequests(currentRideCode)
                .catch { }
                .collect { requests ->
                    val isCreatorActive = latestConvoyRiders.any { it.id == currentSessionCreatorId }
                    val shouldIHandle = if (isCreatorActive) {
                        currentRiderId == currentSessionCreatorId
                    } else {
                        latestConvoyRiders.any { it.id == currentRiderId }
                    }

                    if (shouldIHandle) {
                        val now = System.currentTimeMillis()
                        val pending = requests.filter { it.isPending && (now - it.timestamp <= 120_000L) }
                        for (req in pending) {
                            JoinNotificationHelper.showJoinRequestNotification(this@LocationTrackingService, req)
                        }
                    }
                }
        }
    }

    private fun stopObservingJoinRequests() {
        joinRequestObservationJob?.cancel()
        joinRequestObservationJob = null
        sessionObservationJob?.cancel()
        sessionObservationJob = null
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // 1. Immediately push last known location if available
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                android.util.Log.d("RideSafeDebug", "[Location] Service lastLocation callback: location=$location")
                if (location != null) {
                    lastKnownLat = location.latitude
                    lastKnownLng = location.longitude
                    refreshEmergencyDistances()

                    if (currentRideCode.isNotEmpty() && currentRiderId.isNotEmpty()) {
                        android.util.Log.d("RideSafeDebug", "[Location] Service pushing initial lastLocation: lat=${location.latitude}, lng=${location.longitude}")
                        repository.updateLocation(
                            rideCode = currentRideCode,
                            riderId = currentRiderId,
                            lat = location.latitude,
                            lng = location.longitude,
                            speed = location.speed
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            android.util.Log.e("RideSafeDebug", "[Location] Service SecurityException on lastLocation: ${e.message}", e)
        }

        // 2. Continuous location requests:
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).apply {
            setMinUpdateIntervalMillis(2500L)
            setMinUpdateDistanceMeters(0f)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            android.util.Log.d("RideSafeDebug", "[Location] Service requesting continuous location updates...")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            android.util.Log.d("RideSafeDebug", "[Location] Service requestLocationUpdates registered successfully.")
        } catch (e: SecurityException) {
            android.util.Log.e("RideSafeDebug", "[Location] Service SecurityException on requestLocationUpdates: ${e.message}. Calling stopSelf()!", e)
            stopSelf()
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                lastKnownLat = location.latitude
                lastKnownLng = location.longitude
                refreshEmergencyDistances()

                android.util.Log.d(
                    "RideSafeDebug",
                    "[Location] Service onLocationResult: lat=${location.latitude}, lng=${location.longitude}, speed=${location.speed}, accuracy=${location.accuracy}"
                )

                if (currentRideCode.isNotEmpty() && currentRiderId.isNotEmpty()) {
                    // Update Firebase with fresh coordinates, speed, and timestamp
                    repository.updateLocation(
                        rideCode = currentRideCode,
                        riderId = currentRiderId,
                        lat = location.latitude,
                        lng = location.longitude,
                        speed = location.speed
                    )
                }
            }
        }
    }

    private fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        // Cleanly remove rider from Firebase upon stopping
        if (currentRideCode.isNotEmpty() && currentRiderId.isNotEmpty()) {
            repository.leaveRide(currentRideCode, currentRiderId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        clearEmergencyAlerts()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
