package com.crm.realestate.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crm.realestate.data.models.AttendanceResponse
import com.crm.realestate.data.models.TodayAttendance
import com.crm.realestate.data.repository.AttendanceRepository
import com.crm.realestate.data.models.Result
import kotlinx.coroutines.launch

/**
 * ViewModel for AttendanceActivity
 * Manages attendance recording and UI state
 */
class AttendanceViewModel(
    private val attendanceRepository: AttendanceRepository
) : ViewModel() {
    
    private val _attendanceStatus = MutableLiveData<TodayAttendance>()
    val attendanceStatus: LiveData<TodayAttendance> = _attendanceStatus
    
    private val _attendanceResult = MutableLiveData<AttendanceResponse?>()
    val attendanceResult: LiveData<AttendanceResponse?> = _attendanceResult
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    /**
     * Load current attendance status for today
     */
    fun loadAttendanceStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            
            when (val result = attendanceRepository.getTodayAttendance()) {
                is Result.Success -> {
                    _attendanceStatus.value = result.data ?: TodayAttendance(
                        hasCheckedIn = false,
                        hasCheckedOut = false,
                        checkInTime = null,
                        checkOutTime = null
                    )
                }
                is Result.Error -> {
                    _error.value = "Failed to load attendance status: ${result.exception.message}"
                    // Set default status on error
                    _attendanceStatus.value = TodayAttendance(
                        hasCheckedIn = false,
                        hasCheckedOut = false,
                        checkInTime = null,
                        checkOutTime = null
                    )
                }
                is Result.Loading -> {
                    // Loading state is handled by the outer _isLoading
                }
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Record attendance with specific type (check-in or check-out)
     * @param attendanceType Either "check_in" or "check_out"
     * @param latitude User's latitude
     * @param longitude User's longitude
     * @param scanType Biometric scan type ("face" or "finger")
     * @param isOfflineMode Whether to operate in offline mode
     */
    fun recordAttendance(
        attendanceType: String,
        latitude: Double,
        longitude: Double,
        scanType: String,
        isOfflineMode: Boolean = false
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            
            val result = if (attendanceType == "check_in") {
                attendanceRepository.checkIn(latitude, longitude, scanType)
            } else if (attendanceType == "check_out") {
                attendanceRepository.checkOut(latitude, longitude, scanType)
            } else {
                Result.Error(Exception("Invalid attendance type: $attendanceType"))
            }
            
            when (result) {
                is Result.Success -> {
                    _attendanceResult.value = result.data
                    // Reload attendance status after successful recording
                    loadAttendanceStatus()
                }
                is Result.Error -> {
                    _error.value = result.exception.message ?: "Attendance recording failed"
                }
                is Result.Loading -> {
                    // Loading state is handled by the outer _isLoading
                }
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Record attendance (check-in or check-out) based on current status - Legacy method
     */
    fun recordAttendance(
        latitude: Double,
        longitude: Double,
        scanType: String,
        isOfflineMode: Boolean = false
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Determine if this should be check-in or check-out
            when (val typeResult = attendanceRepository.getAttendanceType()) {
                is Result.Success -> {
                    val attendanceType = typeResult.data
                    recordAttendance(attendanceType, latitude, longitude, scanType, isOfflineMode)
                }
                is Result.Error -> {
                    _error.value = typeResult.exception.message ?: "Unable to determine attendance type"
                    _isLoading.value = false
                }
                is Result.Loading -> {
                    // Loading state is handled by the outer _isLoading
                }
            }
        }
    }
    
    /**
     * Check if user can punch attendance (duplicate prevention)
     */
    fun canPunchAttendance(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val canPunch = attendanceRepository.canPunchAttendance()
            callback(canPunch)
        }
    }
    
    /**
     * Sync offline attendance records
     */
    fun syncOfflineAttendance() {
        viewModelScope.launch {
            _isLoading.value = true
            
            when (val result = attendanceRepository.syncOfflineAttendance()) {
                is Result.Success -> {
                    if (result.data.isNotEmpty()) {
                        _attendanceResult.value = result.data.first()
                    }
                    // Reload attendance status after sync
                    loadAttendanceStatus()
                }
                is Result.Error -> {
                    _error.value = "Sync failed: ${result.exception.message}"
                }
                is Result.Loading -> {
                    // Loading state is handled by the outer _isLoading
                }
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Get count of unsynced offline records
     */
    fun getUnsyncedCount(callback: (Int) -> Unit) {
        viewModelScope.launch {
            val count = attendanceRepository.getUnsyncedAttendanceCount()
            callback(count)
        }
    }
    
    /**
     * Clear attendance result after handling
     */
    fun clearAttendanceResult() {
        _attendanceResult.value = null
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
}