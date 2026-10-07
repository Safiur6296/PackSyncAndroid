package com.ridesafe.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ridesafe.app.data.repository.RideRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles action buttons directly from the system notification for Join Requests (Approve / Decline).
 */
class JoinRequestReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RESPOND_JOIN = "com.ridesafe.app.ACTION_RESPOND_JOIN"
        const val EXTRA_RIDE_CODE = "extra_ride_code"
        const val EXTRA_RIDER_ID = "extra_rider_id"
        const val EXTRA_APPROVED = "extra_approved"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RESPOND_JOIN) {
            val rideCode = intent.getStringExtra(EXTRA_RIDE_CODE) ?: return
            val riderId = intent.getStringExtra(EXTRA_RIDER_ID) ?: return
            val approved = intent.getBooleanExtra(EXTRA_APPROVED, false)
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

            // Dismiss notification immediately
            val notifManager = context.getSystemService(NotificationManager::class.java)
            if (notifId != 0) {
                notifManager?.cancel(notifId)
            }

            Log.d("RideSafeDebug", "JoinRequestReceiver onReceive: ride=$rideCode, rider=$riderId, approved=$approved")

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = RideRepository()
                    val myId = repo.getOrCreateRiderId()
                    repo.respondToJoinRequest(rideCode, riderId, approved, myId)
                } catch (e: Exception) {
                    Log.e("RideSafeDebug", "Error in JoinRequestReceiver: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
