package com.ridesafe.app.data

import com.ridesafe.app.data.model.JoinRequest
import com.ridesafe.app.data.model.JoinRequestStatus
import com.ridesafe.app.data.repository.RideRepository
import com.ridesafe.app.util.JoinNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCodeAndJoinRequestTest {

    @Test
    fun testGenerateRideCodeFormat() {
        val repo = RideRepository()
        for (i in 1..50) {
            val code = repo.generateRideCode()
            assertEquals("Ride code must be exactly 6 characters", 6, code.length)
            assertTrue("Ride code must be uppercase alphanumeric: $code", code.all { it.isLetterOrDigit() && (!it.isLetter() || it.isUpperCase()) })
        }
    }

    @Test
    fun testJoinRequestStatusFlags() {
        val pending = JoinRequest(id = "user1", status = JoinRequestStatus.PENDING.name)
        assertTrue(pending.isPending)
        assertFalse(pending.isApproved)
        assertFalse(pending.isDeclined)
        assertFalse(pending.isCancelled)

        val approved = JoinRequest(id = "user1", status = JoinRequestStatus.APPROVED.name)
        assertFalse(approved.isPending)
        assertTrue(approved.isApproved)
        assertFalse(approved.isDeclined)

        val declined = JoinRequest(id = "user1", status = JoinRequestStatus.DECLINED.name)
        assertFalse(declined.isPending)
        assertFalse(declined.isApproved)
        assertTrue(declined.isDeclined)

        val cancelled = JoinRequest(id = "user1", status = JoinRequestStatus.CANCELLED.name)
        assertTrue(cancelled.isCancelled)
        assertFalse(cancelled.isPending)
    }

    @Test
    fun testJoinNotificationIdConsistency() {
        val riderId = "rider_abc_123"
        val id1 = JoinNotificationHelper.getNotificationIdForRequest(riderId)
        val id2 = JoinNotificationHelper.getNotificationIdForRequest(riderId)
        assertEquals("Notification ID must be deterministic for the same riderId", id1, id2)
        assertTrue("Notification ID must be positive", id1 >= 4000)
    }
}
