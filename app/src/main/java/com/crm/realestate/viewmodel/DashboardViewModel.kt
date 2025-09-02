package com.crm.realestate.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crm.realestate.data.models.AttendanceOverview
import com.crm.realestate.data.models.LeaveInfo
import com.crm.realestate.data.repository.DashboardRepository
import com.crm.realestate.data.repository.AttendanceRepository
import com.crm.realestate.utils.Result
import kotlinx.coroutines.launch

/**
 * ViewModel for DashboardActivity
 * Manages dashboard data and UI state
 */
class DashboardViewModel(
    private val dashboardRepository: DashboardRepository,
    private val attendanceRepository: AttendanceRepository
) : ViewModel() {
    
    private val _attendanceOverview = MutableLiveData<AttendanceOverview>()
    val attendanceOverview: LiveData<AttendanceOverview> = _attendanceOverview
    
    private val _leaveInfo = MutableLiveData<LeaveInfo>()
    val leaveInfo: LiveData<LeaveInfo> = _leaveInfo
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    /**
     * Record attendance (check-in or check-out)
     * @param type "check_in" or "check_out"
     * @param offlineMode true if offline, false otherwise
     * @param latitude user's latitude (default dummy value)
     * @param longitude user's longitude (default dummy value)
     * @param scanType "face" or "finger" (default "finger")
     */
    fun recordAttendance(
        type: String,
        offlineMode: Boolean = false,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        scanType: String = "finger"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = if (type == "check_in") {
                    attendanceRepository.checkIn(latitude, longitude, scanType)
                } else if (type == "check_out") {
                    attendanceRepository.checkOut(latitude, longitude, scanType)
                } else {
                    Result.Error(Exception("Invalid attendance type: $type"))
                }
                
                when (result) {
                    is Result.Success<*> -> {
                        // Reload dashboard data on successful attendance
                        loadDashboardData()
                    }
                    is Result.Error -> {
                        _error.value = result.appError?.message ?: result.exception.message ?: "Attendance operation failed"
                    }
                    is Result.Loading -> {
                        // Loading is already handled by _isLoading
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Attendance operation failed"
            }
            _isLoading.value = false
        }
    }
    
    /**
     * Load dashboard data including attendance overview and leave information
     */
    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Load attendance overview
            when (val result = dashboardRepository.getAttendanceOverview()) {
                is Result.Success -> {
                    _attendanceOverview.value = result.data
                }
                is Result.Error -> {
                    _error.value = "Failed to load attendance data: ${result.exception.message}"
                }
                is Result.Loading -> {
                    // Loading state is handled by the outer _isLoading
                }
            }
            
            // Load leave information
            when (val result = dashboardRepository.getLeaveInformation()) {
                is Result.Success -> {
                    _leaveInfo.value = result.data
                }
                is Result.Error -> {
                    _error.value = "Failed to load leave data: ${result.exception.message}"
                }
                is Result.Loading -> {
                    // Loading state is handled by the outer _isLoading
                }
            }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _error.value = null
    }
}