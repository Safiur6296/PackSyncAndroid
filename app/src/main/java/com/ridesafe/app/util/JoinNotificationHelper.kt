package com.ridesafe.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ridesafe.app.MainActivity
import com.ridesafe.app.R
import com.ridesafe.app.RideSafeApp
import com.ridesafe.app.data.model.JoinRequest
import com.ridesafe.app.receiver.JoinRequestReceiver

object JoinNotificationHelper {

    private const val JOIN_REQUEST_BASE_ID = 4000
    private const val DECLINED_NOTIFICATION_ID = 5001

    fun getNotificationIdForRequest(riderId: String): Int {
        return JOIN_REQUEST_BASE_ID + ((riderId.hashCode() and 0x7FFFFFFF) % 10000)
    }

    /**
     * Shows a heads-up notification to convoy leader / existing riders when a new join request arrives.
     * Contains inline "Approve" and "Decline" action buttons.
     */
    fun showJoinRequestNotification(context: Context, request: JoinRequest) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val notifId = getNotificationIdForRequest(request.riderId)

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Tapping body opens MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(context, notifId, contentIntent, pendingFlags)

        // Approve action
        val approveIntent = Intent(context, JoinRequestReceiver::class.java).apply {
            action = JoinRequestReceiver.ACTION_RESPOND_JOIN
            putExtra(JoinRequestReceiver.EXTRA_RIDE_CODE, request.rideCode)
            putExtra(JoinRequestReceiver.EXTRA_RIDER_ID, request.riderId)
            putExtra(JoinRequestReceiver.EXTRA_APPROVED, true)
            putExtra(JoinRequestReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val approvePendingIntent = PendingIntent.getBroadcast(context, notifId * 2 + 1, approveIntent, pendingFlags)

        // Decline action
        val declineIntent = Intent(context, JoinRequestReceiver::class.java).apply {
            action = JoinRequestReceiver.ACTION_RESPOND_JOIN
            putExtra(JoinRequestReceiver.EXTRA_RIDE_CODE, request.rideCode)
            putExtra(JoinRequestReceiver.EXTRA_RIDER_ID, request.riderId)
            putExtra(JoinRequestReceiver.EXTRA_APPROVED, false)
            putExtra(JoinRequestReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(context, notifId * 2 + 2, declineIntent, pendingFlags)

        val notification = NotificationCompat.Builder(context, RideSafeApp.JOIN_REQUEST_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Join Request: ${request.riderName}")
            .setContentText("${request.riderName} wants to join ride ${request.rideCode}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${request.riderName} wants to join your convoy (${request.rideCode}). Tap Approve to let them in.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.checkbox_on_background, "Approve", approvePendingIntent)
            .addAction(android.R.drawable.ic_delete, "Decline", declinePendingIntent)
            .setTimeoutAfter(120_000L) // Auto dismiss after 2 minutes
            .build()

        notificationManager.notify(notifId, notification)
    }

    /**
     * Cancels the join request notification once resolved or cancelled.
     */
    fun cancelJoinRequestNotification(context: Context, riderId: String) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        notificationManager.cancel(getNotificationIdForRequest(riderId))
    }

    /**
     * Shows notification to the joiner when leader declines their request (Fix 2).
     */
    fun showJoinDeclinedNotification(
        context: Context,
        message: String = "The leader has declined your joing request"
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(context, DECLINED_NOTIFICATION_ID, contentIntent, pendingFlags)

        val notification = NotificationCompat.Builder(context, RideSafeApp.JOIN_REQUEST_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("PackSync Convoy")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(DECLINED_NOTIFICATION_ID, notification)
    }
}
