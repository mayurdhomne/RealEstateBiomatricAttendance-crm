package com.crm.realestate.utils

import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

/**
 * Integration test for duplicate prevention system
 * Tests the complete flow of duplicate prevention logic
 */
class DuplicatePreventionIntegrationTest {

    @Test
    fun `complete duplicate prevention flow test`() {
        // Given - Simulate attendance punch scenario
        val baseTime = System.currentTimeMillis()
        
        // Test 1: First punch should be allowed
        val firstPunchValidation = DuplicatePreventionHelper.validateAttendancePunch(null, baseTime)
        assertTrue("First punch should be allowed", firstPunchValidation is DuplicatePreventionHelper.AttendancePunchValidation.Allowed)
        
        // Test 2: Immediate second punch should be blocked
        val immediateSecondPunch = DuplicatePreventionHelper.validateAttendancePunch(baseTime, baseTime + 1000) // 1 second later
        assertTrue("Immediate second punch should be blocked", immediateSecondPunch is DuplicatePreventionHelper.AttendancePunchValidation.Blocked)
        
        // Test 3: Punch after 1 minute should be blocked
        val oneMinuteLater = baseTime + TimeUnit.MINUTES.toMillis(1)
        val oneMinuteValidation = DuplicatePreventionHelper.validateAttendancePunch(baseTime, oneMinuteLater)
        assertTrue("Punch after 1 minute should be blocked", oneMinuteValidation is DuplicatePreventionHelper.AttendancePunchValidation.Blocked)
        
        // Test 4: Punch after exactly 2 minutes should be allowed
        val twoMinutesLater = baseTime + TimeUnit.MINUTES.toMillis(2)
        val twoMinuteValidation = DuplicatePreventionHelper.validateAttendancePunch(baseTime, twoMinutesLater)
        assertTrue("Punch after exactly 2 minutes should be allowed", twoMinuteValidation is DuplicatePreventionHelper.AttendancePunchValidation.Allowed)
        
        // Test 5: Punch after 3 minutes should be allowed
        val threeMinutesLater = baseTime + TimeUnit.MINUTES.toMillis(3)
        val threeMinuteValidation = DuplicatePreventionHelper.validateAttendancePunch(baseTime, threeMinutesLater)
        assertTrue("Punch after 3 minutes should be allowed", threeMinuteValidation is DuplicatePreventionHelper.AttendancePunchValidation.Allowed)
    }

    @Test
    fun `cooldown message accuracy test`() {
        // Given
        val baseTime = System.currentTimeMillis()
        
        // Test various time differences
        val thirtySecondsAgo = baseTime - TimeUnit.SECONDS.toMillis(30)
        val oneMinuteAgo = baseTime - TimeUnit.MINUTES.toMillis(1)
        val twoMinutesAgo = baseTime - TimeUnit.MINUTES.toMillis(2)
        
        // When/Then
        val thirtySecMessage = DuplicatePreventionHelper.getCooldownMessage(thirtySecondsAgo, baseTime)
        val oneMinuteMessage = DuplicatePreventionHelper.getCooldownMessage(oneMinuteAgo, baseTime)
        val twoMinuteMessage = DuplicatePreventionHelper.getCooldownMessage(twoMinutesAgo, baseTime)
        
        assertTrue("30 seconds ago should show remaining time", thirtySecMessage.contains("90 seconds"))
        assertTrue("1 minute ago should show remaining time", oneMinuteMessage.contains("60 seconds"))
        assertEquals("2 minutes ago should allow punch", "You can now punch attendance", twoMinuteMessage)
    }

    @Test
    fun `boundary condition comprehensive test`() {
        // Given - Test all critical boundary conditions
        val baseTime = System.currentTimeMillis()
        val testCases = mapOf(
            "119 seconds ago" to baseTime - TimeUnit.SECONDS.toMillis(119),
            "120 seconds ago" to baseTime - TimeUnit.SECONDS.toMillis(120),
            "121 seconds ago" to baseTime - TimeUnit.SECONDS.toMillis(121)
        )
        
        // When/Then
        testCases.forEach { (description, timestamp) ->
            val validation = DuplicatePreventionHelper.validateAttendancePunch(timestamp, baseTime)
            val isWithinCooldown = DuplicatePreventionHelper.isWithinCooldownPeriod(timestamp, baseTime)
            
            when (description) {
                "119 seconds ago" -> {
                    assertTrue("$description should be blocked", validation is DuplicatePreventionHelper.AttendancePunchValidation.Blocked)
                    assertTrue("$description should be within cooldown", isWithinCooldown)
                }
                "120 seconds ago" -> {
                    assertTrue("$description should be allowed", validation is DuplicatePreventionHelper.AttendancePunchValidation.Allowed)
                    assertFalse("$description should not be within cooldown", isWithinCooldown)
                }
                "121 seconds ago" -> {
                    assertTrue("$description should be allowed", validation is DuplicatePreventionHelper.AttendancePunchValidation.Allowed)
                    assertFalse("$description should not be within cooldown", isWithinCooldown)
                }
            }
        }
    }

    @Test
    fun `real world scenario simulation`() {
        // Given - Simulate a real employee attendance scenario
        val workDayStart = System.currentTimeMillis()
        
        // Scenario: Employee tries to punch multiple times in quick succession
        val punchAttempts = listOf(
            workDayStart,                                    // Initial punch
            workDayStart + TimeUnit.SECONDS.toMillis(30),   // 30 seconds later (accidental double tap)
            workDayStart + TimeUnit.MINUTES.toMillis(1),    // 1 minute later (impatient retry)
            workDayStart + TimeUnit.MINUTES.toMillis(2),    // 2 minutes later (legitimate retry)
            workDayStart + TimeUnit.MINUTES.toMillis(3)     // 3 minutes later (another attempt)
        )
        
        var lastSuccessfulPunch: Long? = null
        val results = mutableListOf<String>()
        
        // When - Process each punch attempt
        punchAttempts.forEachIndexed { index, attemptTime ->
            val validation = DuplicatePreventionHelper.validateAttendancePunch(lastSuccessfulPunch, attemptTime)
            
            when (validation) {
                is DuplicatePreventionHelper.AttendancePunchValidation.Allowed -> {
                    lastSuccessfulPunch = attemptTime
                    results.add("Attempt ${index + 1}: SUCCESS")
                }
                is DuplicatePreventionHelper.AttendancePunchValidation.Blocked -> {
                    results.add("Attempt ${index + 1}: BLOCKED (${validation.remainingSeconds}s remaining)")
                }
            }
        }
        
        // Then - Verify expected behavior
        assertEquals("Should have 5 attempts", 5, results.size)
        assertTrue("First attempt should succeed", results[0].contains("SUCCESS"))
        assertTrue("Second attempt should be blocked", results[1].contains("BLOCKED"))
        assertTrue("Third attempt should be blocked", results[2].contains("BLOCKED"))
        assertTrue("Fourth attempt should succeed", results[3].contains("SUCCESS"))
        assertTrue("Fifth attempt should be blocked", results[4].contains("BLOCKED"))
        
        // Verify only 2 successful punches
        val successCount = results.count { it.contains("SUCCESS") }
        assertEquals("Should have exactly 2 successful punches", 2, successCount)
    }
}