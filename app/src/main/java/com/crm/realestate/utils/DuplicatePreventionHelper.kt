package com.crm.realestate.utils

import java.util.concurrent.TimeUnit

/**
 * Helper class for duplicate attendance prevention logic
 * Provides utility methods for cooldown period calculations and validation
 */
object DuplicatePreventionHelper {
    
    /**
     * Cooldown period in milliseconds (2 minutes)
     */
    const val COOLDOWN_PERIOD_MS = 120_000L // 2 minutes in milliseconds
    
    /**
     * Check if a punch is within the cooldown period
     * @param lastPunchTime The timestamp of the last punch
     * @param currentTime The current timestamp
     * @return true if within cooldown period, false otherwise
     */
    fun isWithinCooldownPeriod(lastPunchTime: Long, currentTime: Long): Boolean {
        val timeDifference = currentTime - lastPunchTime
        return timeDifference < COOLDOWN_PERIOD_MS
    }
    
    /**
     * Calculate remaining cooldown time in seconds
     * @param lastPunchTime The timestamp of the last punch
     * @param currentTime The current timestamp
     * @return remaining cooldown time in seconds, or 0 if cooldown has expired
     */
    fun getRemainingCooldownSeconds(lastPunchTime: Long, currentTime: Long): Long {
        val timeDifference = currentTime - lastPunchTime
        val remainingMs = COOLDOWN_PERIOD_MS - timeDifference
        return if (remainingMs > 0) TimeUnit.MILLISECONDS.toSeconds(remainingMs) else 0L
    }
    
    /**
     * Get user-friendly cooldown message
     * @param lastPunchTime The timestamp of the last punch
     * @param currentTime The current timestamp
     * @return user-friendly message about remaining cooldown time
     */
    fun getCooldownMessage(lastPunchTime: Long, currentTime: Long): String {
        val remainingSeconds = getRemainingCooldownSeconds(lastPunchTime, currentTime)
        return if (remainingSeconds > 0) {
            "Please wait ${remainingSeconds} seconds before punching attendance again"
        } else {
            "You can now punch attendance"
        }
    }
    
    /**
     * Validate if attendance punch is allowed based on cooldown
     * @param lastPunchTime The timestamp of the last punch, null if no previous punch
     * @param currentTime The current timestamp
     * @return AttendancePunchValidation result
     */
    fun validateAttendancePunch(lastPunchTime: Long?, currentTime: Long): AttendancePunchValidation {
        return if (lastPunchTime == null) {
            AttendancePunchValidation.Allowed
        } else if (isWithinCooldownPeriod(lastPunchTime, currentTime)) {
            val remainingSeconds = getRemainingCooldownSeconds(lastPunchTime, currentTime)
            AttendancePunchValidation.Blocked(
                message = "Please wait 2 minutes before punching attendance again",
                remainingSeconds = remainingSeconds
            )
        } else {
            AttendancePunchValidation.Allowed
        }
    }
    
    /**
     * Sealed class representing attendance punch validation result
     */
    sealed class AttendancePunchValidation {
        object Allowed : AttendancePunchValidation()
        data class Blocked(
            val message: String,
            val remainingSeconds: Long
        ) : AttendancePunchValidation()
    }
}