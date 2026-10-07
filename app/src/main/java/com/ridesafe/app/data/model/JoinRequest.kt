package com.ridesafe.app.data.model

import com.google.firebase.database.IgnoreExtraProperties

enum class JoinRequestStatus {
    PENDING,
    APPROVED,
    DECLINED,
    CANCELLED,
    TIMED_OUT
}

/**
 * JoinRequest represents a permission request from a rider attempting to join a ride session.
 */
@IgnoreExtraProperties
data class JoinRequest(
    val id: String = "",
    val rideCode: String = "",
    val riderId: String = "",
    val riderName: String = "",
    val timestamp: Long = 0L,
    val status: String = JoinRequestStatus.PENDING.name,
    val respondedBy: String? = null
) {
    val isPending: Boolean
        get() = status == JoinRequestStatus.PENDING.name

    val isApproved: Boolean
        get() = status == JoinRequestStatus.APPROVED.name

    val isDeclined: Boolean
        get() = status == JoinRequestStatus.DECLINED.name

    val isCancelled: Boolean
        get() = status == JoinRequestStatus.CANCELLED.name
}
