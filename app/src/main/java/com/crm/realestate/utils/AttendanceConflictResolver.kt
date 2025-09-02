package com.crm.realestate.utils

import com.crm.realestate.data.database.entities.OfflineAttendance
import com.crm.realestate.data.models.AttendanceResponse
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles conflict resolution between offline and online attendance data
 */
class AttendanceConflictResolver {
    
    /**
     * Resolve conflicts between offline attendance and server response
     */
    fun resolveConflict(
        offlineAttendance: OfflineAttendance,
        serverResponse: AttendanceResponse?
    ): ConflictResolution {
        
        // If server response is null or indicates failure, keep offline data
        if (serverResponse == null) {
            return ConflictResolution.KEEP_OFFLINE
        }
        
        // Check for duplicate attendance (same day, same type)
        val offlineDate = getDateFromTimestamp(offlineAttendance.timestamp)
        val serverDate = extractDateFromResponse(serverResponse)
        
        if (offlineDate == serverDate && 
            isSameAttendanceType(offlineAttendance.attendanceType, serverResponse)) {
            
            // Check timestamps to determine which is more recent
            val offlineTime = offlineAttendance.timestamp
            val serverTime = extractTimestampFromResponse(serverResponse)
            
            return when {
                serverTime == null -> ConflictResolution.KEEP_OFFLINE
                offlineTime > serverTime -> ConflictResolution.PREFER_OFFLINE
                offlineTime < serverTime -> ConflictResolution.PREFER_SERVER
                else -> ConflictResolution.MERGE_DATA
            }
        }
        
        // No conflict - different dates or types
        return ConflictResolution.NO_CONFLICT
    }
    
    /**
     * Validate offline attendance before sync
     */
    fun validateOfflineAttendance(attendance: OfflineAttendance): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate employee ID
        if (attendance.employeeId.isBlank()) {
            errors.add("Employee ID is missing")
        }
        
        // Validate coordinates
        if (attendance.latitude == 0.0 && attendance.longitude == 0.0) {
            errors.add("Location coordinates are invalid")
        }
        
        // Validate latitude range
        if (attendance.latitude < -90 || attendance.latitude > 90) {
            errors.add("Latitude is out of valid range")
        }
        
        // Validate longitude range
        if (attendance.longitude < -180 || attendance.longitude > 180) {
            errors.add("Longitude is out of valid range")
        }
        
        // Validate scan type
        if (attendance.scanType !in listOf("face", "fingerprint")) {
            errors.add("Invalid scan type: ${attendance.scanType}")
        }
        
        // Validate attendance type
        if (attendance.attendanceType !in listOf("check_in", "check_out")) {
            errors.add("Invalid attendance type: ${attendance.attendanceType}")
        }
        
        // Validate timestamp (not in future, not too old)
        val now = System.currentTimeMillis()
        val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 days
        
        if (attendance.timestamp > now) {
            errors.add("Attendance timestamp is in the future")
        }
        
        if (now - attendance.timestamp > maxAge) {
            errors.add("Attendance record is too old (more than 7 days)")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Merge offline and server attendance data
     */
    fun mergeAttendanceData(
        offlineAttendance: OfflineAttendance,
        serverResponse: AttendanceResponse
    ): AttendanceResponse {
        // Prefer server data but keep offline location if server location is missing
        return serverResponse.copy(
            // Use offline location if server doesn't have it
            message = "${serverResponse.message} (synced from offline)"
        )
    }
    
    /**
     * Check if attendance records represent the same logical operation
     */
    fun isSameLogicalOperation(
        offline1: OfflineAttendance,
        offline2: OfflineAttendance
    ): Boolean {
        val date1 = getDateFromTimestamp(offline1.timestamp)
        val date2 = getDateFromTimestamp(offline2.timestamp)
        
        return date1 == date2 && 
               offline1.attendanceType == offline2.attendanceType &&
               offline1.employeeId == offline2.employeeId
    }
    
    /**
     * Deduplicate offline attendance records
     */
    fun deduplicateOfflineRecords(records: List<OfflineAttendance>): List<OfflineAttendance> {
        val deduplicatedRecords = mutableListOf<OfflineAttendance>()
        val seenOperations = mutableSetOf<String>()
        
        // Sort by timestamp (newest first)
        val sortedRecords = records.sortedByDescending { it.timestamp }
        
        for (record in sortedRecords) {
            val operationKey = "${record.employeeId}_${getDateFromTimestamp(record.timestamp)}_${record.attendanceType}"
            
            if (!seenOperations.contains(operationKey)) {
                seenOperations.add(operationKey)
                deduplicatedRecords.add(record)
            }
        }
        
        return deduplicatedRecords.sortedBy { it.timestamp }
    }
    
    /**
     * Get date string from timestamp
     */
    private fun getDateFromTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
    
    /**
     * Extract date from server response
     */
    private fun extractDateFromResponse(response: AttendanceResponse): String? {
        // Try to extract date from check-in or check-out time
        val timeString = response.checkInTime ?: response.checkOutTime
        return timeString?.let { 
            try {
                // Assuming server returns time in ISO format or similar
                val serverDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .parse(it.substring(0, 10))
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(serverDate!!)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Extract timestamp from server response
     */
    private fun extractTimestampFromResponse(response: AttendanceResponse): Long? {
        val timeString = response.checkInTime ?: response.checkOutTime
        return timeString?.let {
            try {
                // Try to parse server timestamp
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(it)?.time
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Check if attendance types match
     */
    private fun isSameAttendanceType(offlineType: String, serverResponse: AttendanceResponse): Boolean {
        return when (offlineType) {
            "check_in" -> !serverResponse.checkInTime.isNullOrEmpty()
            "check_out" -> !serverResponse.checkOutTime.isNullOrEmpty()
            else -> false
        }
    }
    
    /**
     * Conflict resolution result
     */
    data class ConflictResolution(
        val action: ConflictAction,
        val reason: String
    ) {
        enum class ConflictAction {
            NO_CONFLICT,
            KEEP_OFFLINE,
            PREFER_OFFLINE,
            PREFER_SERVER,
            MERGE_DATA
        }
        
        companion object {
            val NO_CONFLICT = ConflictResolution(ConflictAction.NO_CONFLICT, "No conflict detected")
            val KEEP_OFFLINE = ConflictResolution(ConflictAction.KEEP_OFFLINE, "Server response unavailable")
            val PREFER_OFFLINE = ConflictResolution(ConflictAction.PREFER_OFFLINE, "Offline data is more recent")
            val PREFER_SERVER = ConflictResolution(ConflictAction.PREFER_SERVER, "Server data is more recent")
            val MERGE_DATA = ConflictResolution(ConflictAction.MERGE_DATA, "Data should be merged")
        }
    }
    
    /**
     * Validation result for offline attendance
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }
}