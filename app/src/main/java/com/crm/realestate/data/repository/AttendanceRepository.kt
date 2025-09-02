package com.crm.realestate.data.repository

import android.util.Log
import com.crm.realestate.data.api.AttendanceApiService
import com.crm.realestate.data.models.*
import com.crm.realestate.data.database.dao.OfflineAttendanceDao
import com.crm.realestate.data.database.dao.AttendanceCacheDao
import com.crm.realestate.data.database.entities.OfflineAttendance
import com.crm.realestate.data.database.entities.AttendanceCache
import com.crm.realestate.utils.AttendanceConflictResolver
import java.util.UUID
import com.crm.realestate.network.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Repository for handling attendance-related operations
 * Includes offline support, duplicate prevention, and comprehensive error handling
 */
class AttendanceRepository(
    private val apiService: AttendanceApiService,
    private val offlineAttendanceDao: OfflineAttendanceDao,
    private val attendanceCacheDao: AttendanceCacheDao,
    private val tokenManager: TokenManager
) {

    /**
     * Check in employee with location and biometric scan type
     * Includes duplicate prevention, offline support, and comprehensive error handling
     */
    suspend fun checkIn(
        latitude: Double,
        longitude: Double,
        scanType: String
    ): Result<AttendanceResponse> = withContext(Dispatchers.IO) {
        try {
            // Check for duplicate attendance within 2 minutes
            val canPunch = canPunchAttendance()
            if (!canPunch) {
                return@withContext Result.Error(
                    Exception("Please wait 2 minutes before punching attendance again"),
                    "Please wait 2 minutes before punching attendance again"
                )
            }
            
            // Verify this is a check-in operation
            when (val attendanceTypeResult = getAttendanceType()) {
                is Result.Success -> {
                    if (attendanceTypeResult.data != "check_in") {
                        return@withContext Result.Error(
                            Exception("You have already checked in today. Please check out instead."),
                            "You have already checked in today. Please check out instead."
                        )
                    }
                }
                is Result.Error -> {
                    return@withContext attendanceTypeResult
                }
                else -> {
                    return@withContext Result.Error(
                        Exception("Unable to determine attendance status"),
                        "Unable to determine attendance status"
                    )
                }
            }
            
            val request = CheckInRequest(
                checkInLatitude = latitude,
                checkInLongitude = longitude,
                scanType = scanType
            )
            
            try {
                val response = apiService.checkIn(request)
                
                if (response.isSuccessful) {
                    response.body()?.let { detailResponse ->
                        // Update attendance cache on successful check-in
                        updateAttendanceCache("check_in")
                        
                        // Create a synthetic AttendanceResponse with the detail message
                        val attendanceData = AttendanceResponse(
                            id = UUID.randomUUID().toString(),
                            employeeId = tokenManager.getEmployeeInfo()?.employeeId ?: "unknown",
                            checkInTime = getCurrentTimeString(),
                            checkOutTime = null,
                            status = "success",
                            message = detailResponse.detail
                        )
                        
                        Result.Success(attendanceData)
                    } ?: Result.Error(
                        Exception("Empty response from server. Please check your connection."),
                        "Empty response from server. Please check your connection."
                    )
                } else {
                    // Try to get error details from the response
                    val errorMessage = try {
                        response.errorBody()?.string() ?: "Unknown error"
                    } catch (e: Exception) {
                        "Error reading error response: ${e.message}"
                    }
                    
                    // Log the error for debugging
                    logErrorDetails("Check-in API error: $errorMessage [Code: ${response.code()}]")
                    
                    // Sync attendance status based on server error response
                    syncAttendanceStatusFromError(errorMessage)
                    
                    when (response.code()) {
                        401 -> Result.Error(
                            Exception("Authentication failed. Please login again."),
                            "Authentication failed. Please login again."
                        )
                        403 -> Result.Error(
                            Exception("You don't have permission to check in."),
                            "You don't have permission to check in."
                        )
                        409 -> Result.Error(
                            Exception("You have already checked in today."),
                            "You have already checked in today. Please check out instead."
                        )
                        422 -> Result.Error(
                            Exception("Invalid location or scan data. Please try again."),
                            "Invalid location or scan data. Please try again."
                        )
                        500 -> Result.Error(
                            Exception("Server error. Please try again later."),
                            "Server error. Please try again later."
                        )
                        else -> Result.Error(
                            Exception("Check-in failed: ${response.message()}"),
                            "Check-in failed: ${response.message()}"
                        )
                    }
                }
            } catch (networkException: Exception) {
                logErrorDetails("Check-in network error", networkException)
                // Save for offline sync if network fails
                saveOfflineAttendance(latitude, longitude, scanType, "check_in")
                Result.Error(
                    Exception("Network unavailable. Attendance saved offline and will sync when connection is restored."),
                    "Network unavailable. Attendance saved offline and will sync when connection is restored."
                )
            }
        } catch (e: Exception) {
            logErrorDetails("Check-in general exception", e)
            Result.Error(
                Exception("Check-in failed: ${e.message ?: "Unknown error occurred"}"),
                "Check-in failed: ${e.message ?: "Unknown error occurred"}"
            )
        }
    }

    /**
     * Check out employee with location and biometric scan type
     * Includes duplicate prevention, offline support, and comprehensive error handling
     */
    suspend fun checkOut(
        latitude: Double,
        longitude: Double,
        scanType: String
    ): Result<AttendanceResponse> = withContext(Dispatchers.IO) {
        try {
            // Check for duplicate attendance within 2 minutes
            val canPunch = canPunchAttendance()
            if (!canPunch) {
                return@withContext Result.Error(
                    Exception("Please wait 2 minutes before punching attendance again"),
                    "Please wait 2 minutes before punching attendance again"
                )
            }
            
            // Verify this is a check-out operation
            when (val attendanceTypeResult = getAttendanceType()) {
                is Result.Success -> {
                    if (attendanceTypeResult.data != "check_out") {
                        return@withContext Result.Error(
                            Exception("You need to check in first before checking out."),
                            "You need to check in first before checking out."
                        )
                    }
                }
                is Result.Error -> {
                    return@withContext attendanceTypeResult
                }
                else -> {
                    return@withContext Result.Error(
                        Exception("Unable to determine attendance status"),
                        "Unable to determine attendance status"
                    )
                }
            }
            
            val request = CheckOutRequest(
                checkOutLatitude = latitude,
                checkOutLongitude = longitude,
                scanType = scanType
            )
            
            try {
                val response = apiService.checkOut(request)
                
                if (response.isSuccessful) {
                    response.body()?.let { detailResponse ->
                        // Update attendance cache on successful check-out
                        updateAttendanceCache("check_out")
                        
                        // Create a synthetic AttendanceResponse with the detail message
                        val attendanceData = AttendanceResponse(
                            id = UUID.randomUUID().toString(),
                            employeeId = tokenManager.getEmployeeInfo()?.employeeId ?: "unknown",
                            checkInTime = null,
                            checkOutTime = getCurrentTimeString(),
                            status = "success",
                            message = detailResponse.detail
                        )
                        
                        Result.Success(attendanceData)
                    } ?: Result.Error(
                        Exception("Empty response from server. Please check your connection."),
                        "Empty response from server. Please check your connection."
                    )
                } else {
                    // Try to get error details from the response
                    val errorMessage = try {
                        response.errorBody()?.string() ?: "Unknown error"
                    } catch (e: Exception) {
                        "Error reading error response: ${e.message}"
                    }
                    
                    // Log the error for debugging
                    logErrorDetails("Check-out API error: $errorMessage [Code: ${response.code()}]")
                    
                    // Sync attendance status based on server error response
                    syncAttendanceStatusFromError(errorMessage)
                    
                    when (response.code()) {
                        401 -> Result.Error(
                            Exception("Authentication failed. Please login again."),
                            "Authentication failed. Please login again."
                        )
                        403 -> Result.Error(
                            Exception("You don't have permission to check out."),
                            "You don't have permission to check out."
                        )
                        400 -> Result.Error(
                            Exception("You need to check in first before checking out."),
                            "You need to check in first. Please check in before checking out."
                        )
                        409 -> Result.Error(
                            Exception("You have already checked out today."),
                            "You have already checked out today."
                        )
                        422 -> Result.Error(
                            Exception("Invalid location or scan data. Please try again."),
                            "Invalid location or scan data. Please try again."
                        )
                        500 -> Result.Error(
                            Exception("Server error. Please try again later."),
                            "Server error. Please try again later."
                        )
                        else -> Result.Error(
                            Exception("Check-out failed: ${response.message()}"),
                            "Check-out failed: ${response.message()}"
                        )
                    }
                }
            } catch (networkException: Exception) {
                logErrorDetails("Check-out network exception", networkException)
                // Save for offline sync if network fails
                saveOfflineAttendance(latitude, longitude, scanType, "check_out")
                Result.Error(
                    Exception("Network unavailable. Attendance saved offline and will sync when connection is restored."),
                    "Network unavailable. Attendance saved offline and will sync when connection is restored."
                )
            }
        } catch (e: Exception) {
            logErrorDetails("Check-out general exception", e)
            Result.Error(
                Exception("Check-out failed: ${e.message ?: "Unknown error occurred"}"),
                "Check-out failed: ${e.message ?: "Unknown error occurred"}"
            )
        }
    }

    /**
     * Get today's attendance status to determine if employee should check in or out
     * Since there's no dedicated API endpoint for attendance status, we'll use local cache
     */
    suspend fun getTodayAttendance(): Result<TodayAttendance?> = withContext(Dispatchers.IO) {
        try {
            // Get today's date in the required format
            val today = getCurrentDateString()
            
            // Try to get attendance status from local cache
            val cacheEntry = attendanceCacheDao.getCacheForDate(today)
            
            if (cacheEntry != null) {
                // We have local cache data
                Result.Success(
                    TodayAttendance(
                        hasCheckedIn = cacheEntry.hasCheckedIn,
                        hasCheckedOut = cacheEntry.hasCheckedOut,
                        checkInTime = cacheEntry.checkInTime,
                        checkOutTime = cacheEntry.checkOutTime
                    )
                )
            } else {
                // No cache entry, assume no attendance recorded yet
                Result.Success(
                    TodayAttendance(
                        hasCheckedIn = false,
                        hasCheckedOut = false,
                        checkInTime = null,
                        checkOutTime = null
                    )
                )
            }
        } catch (e: Exception) {
            // If cache access fails, allow check-in as fallback
            Log.e("AttendanceRepo", "Error getting attendance status: ${e.message}", e)
            Result.Success(
                TodayAttendance(
                    hasCheckedIn = false,
                    hasCheckedOut = false,
                    checkInTime = null,
                    checkOutTime = null
                )
            )
        }
    }

    /**
     * Determine attendance type based on today's status with improved error handling
     */
    suspend fun getAttendanceType(): Result<String> {
        return when (val result = getTodayAttendance()) {
            is Result.Success -> {
                val todayAttendance = result.data
                when {
                    todayAttendance == null || !todayAttendance.hasCheckedIn -> {
                        Result.Success("check_in")
                    }
                    todayAttendance.hasCheckedIn && !todayAttendance.hasCheckedOut -> {
                        Result.Success("check_out")
                    }
                    else -> {
                        Result.Error(
                            Exception("You have already completed attendance for today"),
                            "You have already completed attendance for today"
                        )
                    }
                }
            }
            is Result.Error -> result
            is Result.Loading -> Result.Error(Exception("Loading state not expected"), "Loading state not expected")
        }
    }
    
    /**
     * Sync attendance status from server response and update local cache
     */
    private suspend fun syncAttendanceStatusFromError(errorResponse: String) = withContext(Dispatchers.IO) {
        try {
            val today = getCurrentDateString()
            when {
                errorResponse.contains("Already checked in", ignoreCase = true) -> {
                    // User is already checked in, update cache
                    val existingCache = attendanceCacheDao.getCacheForDate(today)
                    val updatedCache = if (existingCache != null) {
                        existingCache.copy(
                            hasCheckedIn = true,
                            checkInTime = existingCache.checkInTime ?: getCurrentTimeString()
                        )
                    } else {
                        AttendanceCache(
                            date = today,
                            lastPunchTime = System.currentTimeMillis(),
                            hasCheckedIn = true,
                            hasCheckedOut = false,
                            checkInTime = getCurrentTimeString(),
                            checkOutTime = null
                        )
                    }
                    attendanceCacheDao.insertOrUpdateCache(updatedCache)
                }
                errorResponse.contains("need to check in", ignoreCase = true) -> {
                    // User needs to check in first, update cache
                    val existingCache = attendanceCacheDao.getCacheForDate(today)
                    val updatedCache = if (existingCache != null) {
                        existingCache.copy(
                            hasCheckedIn = false,
                            hasCheckedOut = false,
                            checkInTime = null,
                            checkOutTime = null
                        )
                    } else {
                        AttendanceCache(
                            date = today,
                            lastPunchTime = System.currentTimeMillis(),
                            hasCheckedIn = false,
                            hasCheckedOut = false,
                            checkInTime = null,
                            checkOutTime = null
                        )
                    }
                    attendanceCacheDao.insertOrUpdateCache(updatedCache)
                }
            }
        } catch (e: Exception) {
            logErrorDetails("Error syncing attendance status from server response", e)
        }
    }

    /**
     * Save attendance data for offline sync when network is unavailable
     */
    suspend fun saveOfflineAttendance(
        latitude: Double,
        longitude: Double,
        scanType: String,
        attendanceType: String
    ) = withContext(Dispatchers.IO) {
        try {
            val employeeInfo = tokenManager.getEmployeeInfo()
            val employeeId = employeeInfo?.employeeId ?: "unknown"
            
            val offlineAttendance = OfflineAttendance(
                id = UUID.randomUUID().toString(),
                employeeId = employeeId,
                latitude = latitude,
                longitude = longitude,
                scanType = scanType,
                attendanceType = attendanceType,
                timestamp = System.currentTimeMillis(),
                synced = false
            )
            
            offlineAttendanceDao.insertOfflineAttendance(offlineAttendance)
        } catch (e: Exception) {
            // Log error but don't throw - offline save is best effort
        }
    }

    /**
     * Sync all pending offline attendance records with conflict resolution
     */
    suspend fun syncOfflineAttendance(): Result<List<AttendanceResponse>> = withContext(Dispatchers.IO) {
        try {
            val conflictResolver = AttendanceConflictResolver()
            val unsyncedAttendance = offlineAttendanceDao.getUnsyncedAttendance()
            
            // Deduplicate offline records before syncing
            val deduplicatedRecords = conflictResolver.deduplicateOfflineRecords(unsyncedAttendance)
            
            val syncResults = mutableListOf<AttendanceResponse>()
            var syncErrors = 0
            var conflictsResolved = 0
            
            for (attendance in deduplicatedRecords) {
                try {
                    // Validate offline attendance before sync
                    when (val validation = conflictResolver.validateOfflineAttendance(attendance)) {
                        is AttendanceConflictResolver.ValidationResult.Invalid -> {
                            // Mark invalid records as synced to remove them
                            offlineAttendanceDao.markAsSynced(attendance.id)
                            syncErrors++
                            continue
                        }
                        is AttendanceConflictResolver.ValidationResult.Valid -> {
                            // Proceed with sync
                        }
                    }
                    
                    val result = if (attendance.attendanceType == "check_in") {
                        checkInOffline(attendance.latitude, attendance.longitude, attendance.scanType)
                    } else {
                        checkOutOffline(attendance.latitude, attendance.longitude, attendance.scanType)
                    }
                    
                    when (result) {
                        is Result.Success -> {
                            // Resolve conflicts between offline and server data
                            val resolution = conflictResolver.resolveConflict(attendance, result.data)
                            
                            val finalResult = when (resolution) {
                                AttendanceConflictResolver.ConflictResolution.MERGE_DATA -> {
                                    conflictResolver.mergeAttendanceData(attendance, result.data)
                                }
                                AttendanceConflictResolver.ConflictResolution.PREFER_OFFLINE -> {
                                    result.data.copy(message = "${result.data.message} (offline data preferred)")
                                }
                                else -> result.data
                            }
                            
                            if (resolution != AttendanceConflictResolver.ConflictResolution.NO_CONFLICT) {
                                conflictsResolved++
                            }
                            
                            // Mark as synced and add to results
                            offlineAttendanceDao.markAsSynced(attendance.id)
                            syncResults.add(finalResult)
                        }
                        is Result.Error -> {
                            // Check if it's a conflict error (409) and handle appropriately
                            if (result.exception.message?.contains("already") == true || result.message?.contains("already") == true) {
                                // Mark as synced since it already exists on server
                                offlineAttendanceDao.markAsSynced(attendance.id)
                                conflictsResolved++
                            } else {
                                syncErrors++
                            }
                        }
                        else -> syncErrors++
                    }
                } catch (e: Exception) {
                    syncErrors++
                }
            }
            
            // Clean up synced records older than 7 days
            val weekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            offlineAttendanceDao.deleteSyncedOlderThan(weekAgo)
            
            when {
                syncResults.isNotEmpty() -> {
                    val message = buildString {
                        append("Successfully synced ${syncResults.size} attendance records")
                        if (conflictsResolved > 0) {
                            append(", resolved $conflictsResolved conflicts")
                        }
                        if (syncErrors > 0) {
                            append(", $syncErrors errors")
                        }
                    }
                    Result.Success(syncResults)
                }
                syncErrors > 0 -> {
                    Result.Error(
                        Exception("Failed to sync $syncErrors attendance records"),
                        "Failed to sync $syncErrors attendance records"
                    )
                }
                else -> {
                    Result.Success(emptyList())
                }
            }
        } catch (e: Exception) {
            Result.Error(
                Exception("Sync failed: ${e.message}"),
                "Sync failed: ${e.message}"
            )
        }
    }

    /**
     * Check if employee can punch attendance (2-minute cooldown prevention)
     */
    suspend fun canPunchAttendance(): Boolean = withContext(Dispatchers.IO) {
        try {
            val today = getCurrentDateString()
            val recentAttendance = attendanceCacheDao.getRecentPunch(today, System.currentTimeMillis())
            recentAttendance == null
        } catch (e: Exception) {
            // If we can't check, allow the punch (fail open)
            true
        }
    }

    /**
     * Get count of unsynced offline attendance records
     */
    suspend fun getUnsyncedAttendanceCount(): Int = withContext(Dispatchers.IO) {
        try {
            offlineAttendanceDao.getUnsyncedCount()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Update attendance cache for duplicate prevention
     */
    private suspend fun updateAttendanceCache(attendanceType: String) = withContext(Dispatchers.IO) {
        try {
            val today = getCurrentDateString()
            val currentTime = System.currentTimeMillis()
            
            val existingCache = attendanceCacheDao.getCacheForDate(today)
            
            val updatedCache = if (existingCache != null) {
                existingCache.copy(
                    lastPunchTime = currentTime,
                    hasCheckedIn = existingCache.hasCheckedIn || attendanceType == "check_in",
                    hasCheckedOut = existingCache.hasCheckedOut || attendanceType == "check_out",
                    checkInTime = if (attendanceType == "check_in") getCurrentTimeString() else existingCache.checkInTime,
                    checkOutTime = if (attendanceType == "check_out") getCurrentTimeString() else existingCache.checkOutTime
                )
            } else {
                AttendanceCache(
                    date = today,
                    lastPunchTime = currentTime,
                    hasCheckedIn = attendanceType == "check_in",
                    hasCheckedOut = attendanceType == "check_out",
                    checkInTime = if (attendanceType == "check_in") getCurrentTimeString() else null,
                    checkOutTime = if (attendanceType == "check_out") getCurrentTimeString() else null
                )
            }
            
            attendanceCacheDao.insertOrUpdateCache(updatedCache)
        } catch (e: Exception) {
            // Log error but don't throw - cache update is best effort
        }
    }

    /**
     * Internal method for offline check-in sync (bypasses duplicate prevention)
     */
    private suspend fun checkInOffline(
        latitude: Double,
        longitude: Double,
        scanType: String
    ): Result<AttendanceResponse> = withContext(Dispatchers.IO) {
        try {
            val request = CheckInRequest(
                checkInLatitude = latitude,
                checkInLongitude = longitude,
                scanType = scanType
            )
            
            val response = apiService.checkIn(request)
            
            if (response.isSuccessful) {
                response.body()?.let { detailResponse ->
                    // Create a synthetic AttendanceResponse with the detail message
                    val attendanceData = AttendanceResponse(
                        id = UUID.randomUUID().toString(),
                        employeeId = tokenManager.getEmployeeInfo()?.employeeId ?: "unknown",
                        checkInTime = getCurrentTimeString(),
                        checkOutTime = null,
                        status = "success",
                        message = detailResponse.detail
                    )
                    
                    Result.Success(attendanceData)
                } ?: Result.Error(
                    Exception("Empty response from server"),
                    "Empty response from server"
                )
            } else {
                // Try to get error details from the response
                val errorMessage = try {
                    response.errorBody()?.string() ?: "Unknown error"
                } catch (ex: Exception) {
                    "Error reading error response: ${ex.message}"
                }
                
                logErrorDetails("Check-in offline sync error: $errorMessage [Code: ${response.code()}]")
                
                Result.Error(
                    Exception("Check-in sync failed: ${response.message()}"),
                    "Check-in sync failed: ${response.message()}"
                )
            }
        } catch (e: Exception) {
            logErrorDetails("Check-in offline sync exception", e)
            Result.Error(e, e.message)
        }
    }

    /**
     * Internal method for offline check-out sync (bypasses duplicate prevention)
     */
    private suspend fun checkOutOffline(
        latitude: Double,
        longitude: Double,
        scanType: String
    ): Result<AttendanceResponse> = withContext(Dispatchers.IO) {
        try {
            val request = CheckOutRequest(
                checkOutLatitude = latitude,
                checkOutLongitude = longitude,
                scanType = scanType
            )
            
            val response = apiService.checkOut(request)
            
            if (response.isSuccessful) {
                response.body()?.let { detailResponse ->
                    // Create a synthetic AttendanceResponse with the detail message
                    val attendanceData = AttendanceResponse(
                        id = UUID.randomUUID().toString(),
                        employeeId = tokenManager.getEmployeeInfo()?.employeeId ?: "unknown",
                        checkInTime = null,
                        checkOutTime = getCurrentTimeString(),
                        status = "success",
                        message = detailResponse.detail
                    )
                    
                    Result.Success(attendanceData)
                } ?: Result.Error(
                    Exception("Empty response from server"),
                    "Empty response from server"
                )
            } else {
                // Try to get error details from the response
                val errorMessage = try {
                    response.errorBody()?.string() ?: "Unknown error"
                } catch (ex: Exception) {
                    "Error reading error response: ${ex.message}"
                }
                
                logErrorDetails("Check-out offline sync error: $errorMessage [Code: ${response.code()}]")
                
                Result.Error(
                    Exception("Check-out sync failed: ${response.message()}"),
                    "Check-out sync failed: ${response.message()}"
                )
            }
        } catch (e: Exception) {
            logErrorDetails("Check-out offline sync exception", e)
            Result.Error(e, e.message)
        }
    }

    /**
     * Get current date string in YYYY-MM-DD format
     */
    private fun getCurrentDateString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    /**
     * Get current time string in HH:mm:ss format
     */
    private fun getCurrentTimeString(): String {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return timeFormat.format(Date())
    }
    
    /**
     * Log error details for debugging
     */
    private fun logErrorDetails(errorMessage: String, exception: Exception? = null) {
        Log.e("AttendanceRepo", errorMessage, exception)
    }

    /**
     * Clean up old attendance cache records (older than 30 days)
     */
    suspend fun cleanupOldRecords() = withContext(Dispatchers.IO) {
        try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -30)
            val cutoffDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            attendanceCacheDao.deleteOldCache(cutoffDate)
        } catch (e: Exception) {
            // Log error but don't throw - cleanup is best effort
        }
    }
}