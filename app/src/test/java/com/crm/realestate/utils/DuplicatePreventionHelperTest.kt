package com.crm.realestate.utils

import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

/**
 * Comprehensive unit tests for DuplicatePreventionHelper
 * Tests all edge cases and boundary conditions for the 2-minute cooldown logic
 */
class DuplicatePreventionHelperTest {

    @Test
    fun `isWithinCooldownPeriod returns true for punch within 1 minute`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val oneMinuteAgo = currentTime - TimeUnit.MINUTES.toMillis(1)

        // When
        val result = DuplicatePreventionHelper.isWithinCooldownPeriod(oneMinuteAgo, currentTime)

        // Then
        assertTrue("Should be within cooldown period for 1 minute", result)
    }

    @Test
    fun `isWithinCooldownPeriod returns true for punch at 119 seconds`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val justUnderTwoMinutes = currentTime - TimeUnit.SECONDS.toMillis(119)

        // When
        val result = DuplicatePreventionHelper.isWithinCooldownPeriod(justUnderTwoMinutes, currentTime)

        // Then
        assertTrue("Should be within cooldown period at 119 seconds", result)
    }

    @Test
    fun `isWithinCooldownPeriod returns false for punch at exactly 2 minutes`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val exactlyTwoMinutes = currentTime - TimeUnit.MINUTES.toMillis(2)

        // When
        val result = DuplicatePreventionHelper.isWithinCooldownPeriod(exactlyTwoMinutes, currentTime)

        // Then
        assertFalse("Should not be within cooldown period at exactly 2 minutes", result)
    }

    @Test
    fun `isWithinCooldownPeriod returns false for punch at 120 seconds`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val exactlyTwoMinutesInSeconds = currentTime - TimeUnit.SECONDS.toMillis(120)

        // When
        val result = DuplicatePreventionHelper.isWithinCooldownPeriod(exactlyTwoMinutesInSeconds, currentTime)

        // Then
        assertFalse("Should not be within cooldown period at exactly 120 seconds", result)
    }

    @Test
    fun `isWithinCooldownPeriod returns false for punch over 2 minutes ago`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val threeMinutesAgo = currentTime - TimeUnit.MINUTES.toMillis(3)

        // When
        val result = DuplicatePreventionHelper.isWithinCooldownPeriod(threeMinutesAgo, currentTime)

        // Then
        assertFalse("Should not be within cooldown period for 3 minutes", result)
    }

    @Test
    fun `getRemainingCooldownSeconds returns correct value for 1 minute ago`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val oneMinuteAgo = currentTime - TimeUnit.MINUTES.toMillis(1)

        // When
        val remainingSeconds = DuplicatePreventionHelper.getRemainingCooldownSeconds(oneMinuteAgo, currentTime)

        // Then
        assertEquals("Should have 60 seconds remaining", 60L, remainingSeconds)
    }

    @Test
    fun `getRemainingCooldownSeconds returns correct value for 30 seconds ago`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val thirtySecondsAgo = currentTime - TimeUnit.SECONDS.toMillis(30)

        // When
        val remainingSeconds = DuplicatePreventionHelper.getRemainingCooldownSeconds(thirtySecondsAgo, currentTime)

        // Then
        assertEquals("Should have 90 seconds remaining", 90L, remainingSeconds)
    }

    @Test
    fun `getRemainingCooldownSeconds returns 0 for punch at exactly 2 minutes`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val exactlyTwoMinutes = currentTime - TimeUnit.MINUTES.toMillis(2)

        // When
        val remainingSeconds = DuplicatePreventionHelper.getRemainingCooldownSeconds(exactlyTwoMinutes, currentTime)

        // Then
        assertEquals("Should have 0 seconds remaining at exactly 2 minutes", 0L, remainingSeconds)
    }

    @Test
    fun `getRemainingCooldownSeconds returns 0 for punch over 2 minutes ago`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val threeMinutesAgo = currentTime - TimeUnit.MINUTES.toMillis(3)

        // When
        val remainingSeconds = DuplicatePreventionHelper.getRemainingCooldownSeconds(threeMinutesAgo, currentTime)

        // Then
        assertEquals("Should have 0 seconds remaining for 3 minutes", 0L, remainingSeconds)
    }

    @Test
    fun `getCooldownMessage returns appropriate message for active cooldown`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val oneMinuteAgo = currentTime - TimeUnit.MINUTES.toMillis(1)

        // When
        val message = DuplicatePreventionHelper.getCooldownMessage(oneMinuteAgo, currentTime)

        // Then
        assertEquals("Should show remaining time message", "Please wait 60 seconds before punching attendance again", message)
    }

    @Test
    fun `getCooldownMessage returns appropriate message for expired cooldown`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val threeMinutesAgo = currentTime - TimeUnit.MINUTES.toMillis(3)

        // When
        val message = DuplicatePreventionHelper.getCooldownMessage(threeMinutesAgo, currentTime)

        // Then
        assertEquals("Should show allowed message", "You can now punch attendance", message)
    }

    @Test
    fun `validateAttendancePunch returns Allowed for null lastPunchTime`() {
        // Given
        val currentTime = System.currentTimeMillis()

        // When
        val result = DuplicatePreventionHelper.validateAttendancePunch(null, currentTime)

        // Then
        assertTrue("Should allow punch when no previous punch", result is DuplicatePreventionHelper.AttendancePunchValidation.Allowed)
    }

    @Test
    fun `validateAttendancePunch returns Blocked for recent punch`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val oneMinuteAgo = currentTime - TimeUnit.MINUTES.toMillis(1)

        // When
        val result = DuplicatePreventionHelper.validateAttendancePunch(oneMinuteAgo, currentTime)

        // Then
        assertTrue("Should block punch for recent punch", result is DuplicatePreventionHelper.AttendancePunchValidation.Blocked)
        
        val blockedResult = result as DuplicatePreventionHelper.AttendancePunchValidation.Blocked
        assertEquals("Should have correct message", "Please wait 2 minutes before punching attendance again", blockedResult.message)
        assertEquals("Should have correct remaining seconds", 60L, blockedResult.remainingSeconds)
    }

    @Test
    fun `validateAttendancePunch returns Allowed for old punch`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val threeMinutesAgo = currentTime - TimeUnit.MINUTES.toMillis(3)

        // When
        val result = DuplicatePreventionHelper.validateAttendancePunch(threeMinutesAgo, currentTime)

        // Then
        assertTrue("Should allow punch for old punch", result is DuplicatePreventionHelper.AttendancePunchValidation.Allowed)
    }

    @Test
    fun `validateAttendancePunch boundary test - exactly 2 minutes`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val exactlyTwoMinutes = currentTime - TimeUnit.MINUTES.toMillis(2)

        // When
        val result = DuplicatePreventionHelper.validateAttendancePunch(exactlyTwoMinutes, currentTime)

        // Then
        assertTrue("Should allow punch at exactly 2 minutes", result is DuplicatePreventionHelper.AttendancePunchValidation.Allowed)
    }

    @Test
    fun `validateAttendancePunch boundary test - just under 2 minutes`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val justUnderTwoMinutes = currentTime - TimeUnit.SECONDS.toMillis(119)

        // When
        val result = DuplicatePreventionHelper.validateAttendancePunch(justUnderTwoMinutes, currentTime)

        // Then
        assertTrue("Should block punch just under 2 minutes", result is DuplicatePreventionHelper.AttendancePunchValidation.Blocked)
        
        val blockedResult = result as DuplicatePreventionHelper.AttendancePunchValidation.Blocked
        assertEquals("Should have 1 second remaining", 1L, blockedResult.remainingSeconds)
    }

    @Test
    fun `cooldown period constant is correct`() {
        // Given/When/Then
        assertEquals("Cooldown period should be 2 minutes in milliseconds", 120_000L, DuplicatePreventionHelper.COOLDOWN_PERIOD_MS)
        assertEquals("Cooldown period should equal 2 minutes", TimeUnit.MINUTES.toMillis(2), DuplicatePreventionHelper.COOLDOWN_PERIOD_MS)
    }

    @Test
    fun `edge case - future timestamp handling`() {
        // Given - Future timestamp (should not happen in real scenarios)
        val currentTime = System.currentTimeMillis()
        val futureTime = currentTime + TimeUnit.MINUTES.toMillis(1)

        // When
        val isWithinCooldown = DuplicatePreventionHelper.isWithinCooldownPeriod(futureTime, currentTime)
        val remainingSeconds = DuplicatePreventionHelper.getRemainingCooldownSeconds(futureTime, currentTime)

        // Then
        // Note: Future timestamps result in negative time difference, which is less than cooldown period
        // This is expected behavior - the system treats future timestamps as within cooldown
        assertTrue("Future timestamp results in negative time difference, treated as within cooldown", isWithinCooldown)
        assertEquals("Should have 0 remaining seconds for future timestamp", 0L, remainingSeconds)
    }

    @Test
    fun `edge case - same timestamp`() {
        // Given
        val currentTime = System.currentTimeMillis()

        // When
        val isWithinCooldown = DuplicatePreventionHelper.isWithinCooldownPeriod(currentTime, currentTime)
        val remainingSeconds = DuplicatePreventionHelper.getRemainingCooldownSeconds(currentTime, currentTime)

        // Then
        assertTrue("Should be within cooldown for same timestamp", isWithinCooldown)
        assertEquals("Should have 120 seconds remaining for same timestamp", 120L, remainingSeconds)
    }

    @Test
    fun `multiple validation calls with different timestamps`() {
        // Given
        val baseTime = System.currentTimeMillis()
        val timestamps = listOf(
            baseTime - TimeUnit.SECONDS.toMillis(30),   // 30 seconds ago
            baseTime - TimeUnit.SECONDS.toMillis(90),   // 90 seconds ago
            baseTime - TimeUnit.SECONDS.toMillis(119),  // 119 seconds ago
            baseTime - TimeUnit.SECONDS.toMillis(120),  // 120 seconds ago
            baseTime - TimeUnit.SECONDS.toMillis(150)   // 150 seconds ago
        )

        // When/Then
        val results = timestamps.map { timestamp ->
            DuplicatePreventionHelper.validateAttendancePunch(timestamp, baseTime)
        }

        // Verify results
        assertTrue("30 seconds should be blocked", results[0] is DuplicatePreventionHelper.AttendancePunchValidation.Blocked)
        assertTrue("90 seconds should be blocked", results[1] is DuplicatePreventionHelper.AttendancePunchValidation.Blocked)
        assertTrue("119 seconds should be blocked", results[2] is DuplicatePreventionHelper.AttendancePunchValidation.Blocked)
        assertTrue("120 seconds should be allowed", results[3] is DuplicatePreventionHelper.AttendancePunchValidation.Allowed)
        assertTrue("150 seconds should be allowed", results[4] is DuplicatePreventionHelper.AttendancePunchValidation.Allowed)
    }
}