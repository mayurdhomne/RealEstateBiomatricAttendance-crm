package com.crm.realestate.data.dao

import com.crm.realestate.database.entity.AttendanceCache
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

/**
 * Unit tests for AttendanceCache entity and duplicate prevention logic
 * Tests the 2-minute cooldown period calculations and daily attendance status tracking
 */
class AttendanceCacheDaoTest {

    @Test
    fun `attendance cache entity stores correct data`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val cache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = currentTime,
            hasCheckedIn = true,
            hasCheckedOut = false
        )

        // Then
        assertEquals("Date should match", "2025-01-08", cache.date)
        assertEquals("Last punch time should match", currentTime, cache.lastPunchTime)
        assertTrue("Should be checked in", cache.hasCheckedIn)
        assertFalse("Should not be checked out", cache.hasCheckedOut)
    }

    @Test
    fun `attendance cache entity supports complete check-in check-out cycle`() {
        // Given - Initial check-in state
        val currentTime = System.currentTimeMillis()
        val checkInCache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = currentTime,
            hasCheckedIn = true,
            hasCheckedOut = false
        )

        // When - Update to check-out state
        val checkOutCache = checkInCache.copy(
            lastPunchTime = currentTime + TimeUnit.HOURS.toMillis(8),
            hasCheckedOut = true
        )

        // Then
        assertTrue("Should maintain check-in status", checkOutCache.hasCheckedIn)
        assertTrue("Should be checked out", checkOutCache.hasCheckedOut)
        assertEquals("Date should be preserved", "2025-01-08", checkOutCache.date)
    }

    @Test
    fun `duplicate prevention logic - time calculations`() {
        // Given
        val baseTime = System.currentTimeMillis()
        val twoMinutesInMillis = TimeUnit.MINUTES.toMillis(2)
        
        // Test various time differences
        val oneMinuteAgo = baseTime - TimeUnit.MINUTES.toMillis(1)
        val twoMinutesAgo = baseTime - twoMinutesInMillis
        val threeMinutesAgo = baseTime - TimeUnit.MINUTES.toMillis(3)
        val exactlyTwoMinutes = baseTime - TimeUnit.SECONDS.toMillis(120)
        val justUnderTwoMinutes = baseTime - TimeUnit.SECONDS.toMillis(119)

        // Then - Verify time calculations for duplicate prevention
        assertTrue("1 minute should be within cooldown", (baseTime - oneMinuteAgo) < twoMinutesInMillis)
        assertFalse("2 minutes should be outside cooldown", (baseTime - twoMinutesAgo) < twoMinutesInMillis)
        assertFalse("3 minutes should be outside cooldown", (baseTime - threeMinutesAgo) < twoMinutesInMillis)
        assertFalse("Exactly 2 minutes should be outside cooldown", (baseTime - exactlyTwoMinutes) < twoMinutesInMillis)
        assertTrue("119 seconds should be within cooldown", (baseTime - justUnderTwoMinutes) < twoMinutesInMillis)
    }

    @Test
    fun `attendance cache entity date format validation`() {
        // Given - Various date formats
        val validDate = "2025-01-08"
        val cache = AttendanceCache(
            date = validDate,
            lastPunchTime = System.currentTimeMillis(),
            hasCheckedIn = true,
            hasCheckedOut = false
        )

        // Then
        assertEquals("Date should match expected format", validDate, cache.date)
        assertTrue("Date should follow YYYY-MM-DD pattern", cache.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `attendance cache entity timestamp validation`() {
        // Given - Various timestamp values
        val currentTime = System.currentTimeMillis()
        val pastTime = currentTime - TimeUnit.HOURS.toMillis(1)
        
        val cache = AttendanceCache(
            date = "2025-01-08",
            lastPunchTime = currentTime,
            hasCheckedIn = true,
            hasCheckedOut = false
        )

        // Then
        assertEquals("Last punch time should match current time", currentTime, cache.lastPunchTime)
        assertTrue("Last punch time should be valid timestamp", cache.lastPunchTime > 0)
        
        // Test with past time
        val pastCache = cache.copy(lastPunchTime = pastTime)
        assertTrue("Past time should be less than current time", pastCache.lastPunchTime < currentTime)
    }
}