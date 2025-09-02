package com.crm.realestate.data.repository

import com.crm.realestate.data.api.AttendanceApiService
import com.crm.realestate.data.models.AttendanceOverview
import com.crm.realestate.data.models.LeaveInfo
import com.crm.realestate.utils.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for dashboard-related data operations
 * Handles fetching attendance overview and leave information
 */
class DashboardRepository(
    private val attendanceApiService: AttendanceApiService
) {
    
    /**
     * Fetch attendance overview data for dashboard
     * @return Result containing AttendanceOverview or error
     */
    suspend fun getAttendanceOverview(): Result<AttendanceOverview> = withContext(Dispatchers.IO) {
        try {
            val response = attendanceApiService.getAttendanceOverview()
            if (response.isSuccessful) {
                response.body()?.let { apiResponse ->
                    if (apiResponse.success) {
                        apiResponse.data?.let { attendanceData ->
                            Result.Success(attendanceData)
                        } ?: Result.Error(Exception("No attendance overview data received"))
                    } else {
                        Result.Error(Exception(apiResponse.message ?: "Failed to fetch attendance overview"))
                    }
                } ?: Result.Error(Exception("Empty response from server"))
            } else {
                Result.Error(Exception("Failed to fetch attendance overview: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    /**
     * Fetch leave information data for dashboard
     * @return Result containing LeaveInfo or error
     */
    suspend fun getLeaveInformation(): Result<LeaveInfo> = withContext(Dispatchers.IO) {
        try {
            val response = attendanceApiService.getLeaveInfo()
            if (response.isSuccessful) {
                response.body()?.let { apiResponse ->
                    if (apiResponse.success) {
                        apiResponse.data?.let { leaveData ->
                            Result.Success(leaveData)
                        } ?: Result.Error(Exception("No leave information data received"))
                    } else {
                        Result.Error(Exception(apiResponse.message ?: "Failed to fetch leave information"))
                    }
                } ?: Result.Error(Exception("Empty response from server"))
            } else {
                Result.Error(Exception("Failed to fetch leave information: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}