package com.ridesafe.app.data

import com.ridesafe.app.data.repository.RideRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCodeAndJoinRequestTest {

    @Test
    fun testGenerateRideCodeFormat() {
        val repo = RideRepository()
        for (i in 1..100) {
            val code = repo.generateRideCode()
            assertEquals("Ride code must be exactly 6 characters", 6, code.length)
            assertTrue("Ride code must be uppercase alphanumeric: $code", code.all { it.isLetterOrDigit() && (!it.isLetter() || it.isUpperCase()) })
            assertTrue("Ride code must contain at least one digit: $code", code.any { it.isDigit() })
            assertTrue("Ride code must contain at least one letter: $code", code.any { it.isLetter() })
        }
    }

    @Test
    fun testGenerateRideCodeHighEntropy() {
        val repo = RideRepository()
        val codes = (1..1000).map { repo.generateRideCode() }.toSet()
        // With over 1.86 billion mixed alphanumeric combinations, 1000 samples should have almost zero collisions
        assertTrue("Expected >= 995 unique codes out of 1000, got ${codes.size}", codes.size >= 995)
    }
}
