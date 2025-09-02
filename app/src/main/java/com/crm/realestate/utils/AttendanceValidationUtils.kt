package com.crm.realestate.utils

import android.content.Context
import android.location.Location
import android.location.LocationManager
import kotlin.math.*

/**
 * Utility class for attendance-related validations and calculations
 */
object AttendanceValidationUtils {

    // Time constants
    const val DUPLICATE_PREVENTION_MINUTES = 2
    const val MAX_WORKING_HOURS = 12
    const val MIN_WORKING_MINUTES = 30

    /**
     * Validate if attendance can be recorded (duplicate prevention)
     */
    fun canRecordAttendance(lastPunchTime: Long?): Boolean {
        if (lastPunchTime == null) return true
        
        val currentTime = System.currentTimeMillis()
        val timeDifference = currentTime - lastPunchTime
        val minutesDifference = timeDifference / (1000 * 60)
        
        return minutesDifference >= DUPLICATE_PREVENTION_MINUTES
    }

    /**
     * Calculate working duration between check-in and check-out
     */
    fun calculateWorkingDuration(checkInTime: Long, checkOutTime: Long): WorkingDuration {
        val durationMillis = checkOutTime - checkInTime
        val hours = durationMillis / (1000 * 60 * 60)
        val minutes = (durationMillis % (1000 * 60 * 60)) / (1000 * 60)
        
        return WorkingDuration(
            hours = hours.toInt(),
            minutes = minutes.toInt(),
            totalMinutes = (durationMillis / (1000 * 60)).toInt(),
            isValidDuration = isValidWorkingDuration(durationMillis)
        )
    }

    /**
     * Check if working duration is valid
     */
    private fun isValidWorkingDuration(durationMillis: Long): Boolean {
        val totalMinutes = durationMillis / (1000 * 60)
        return totalMinutes >= MIN_WORKING_MINUTES && totalMinutes <= (MAX_WORKING_HOURS * 60)
    }

    /**
     * Check if GPS is enabled
     */
    fun isGpsEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * Validate location accuracy
     */
    fun isLocationAccurate(location: Location, requiredAccuracy: Float = 50f): Boolean {
        return location.accuracy <= requiredAccuracy
    }

    /**
     * Format location for display
     */
    fun formatLocation(latitude: Double, longitude: Double, accuracy: Float): String {
        return "Lat: ${String.format("%.6f", latitude)}, " +
                "Lng: ${String.format("%.6f", longitude)}\n" +
                "Accuracy: ${String.format("%.1f", accuracy)}m"
    }

    /**
     * Calculate distance between two locations in meters
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000 // meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Validate attendance timing (not too early, not too late)
     */
    fun isValidAttendanceTime(): AttendanceTimeValidation {
        val calendar = java.util.Calendar.getInstance()
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(java.util.Calendar.MINUTE)
        val currentTime = currentHour * 60 + currentMinute // minutes since midnight

        return when {
            currentTime < 6 * 60 -> // Before 6 AM
                AttendanceTimeValidation(false, "Attendance cannot be recorded before 6:00 AM")
            currentTime > 23 * 60 -> // After 11 PM
                AttendanceTimeValidation(false, "Attendance cannot be recorded after 11:00 PM")
            else ->
                AttendanceTimeValidation(true, "Valid time for attendance")
        }
    }

    /**
     * Data class for working duration
     */
    data class WorkingDuration(
        val hours: Int,
        val minutes: Int,
        val totalMinutes: Int,
        val isValidDuration: Boolean
    ) {
        fun formatDuration(): String {
            return if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes}m"
            }
        }
    }

    /**
     * Data class for attendance time validation
     */
    data class AttendanceTimeValidation(
        val isValid: Boolean,
        val message: String
    )

    /**
     * Biometric validation constants
     */
    object BiometricValidation {
        const val MAX_RETRY_ATTEMPTS = 3
        const val LOCKOUT_DURATION_MINUTES = 30
        
        fun canRetryBiometric(currentAttempts: Int): Boolean {
            return currentAttempts < MAX_RETRY_ATTEMPTS
        }
        
        fun getRemainingAttempts(currentAttempts: Int): Int {
            return maxOf(0, MAX_RETRY_ATTEMPTS - currentAttempts)
        }
    }
}
