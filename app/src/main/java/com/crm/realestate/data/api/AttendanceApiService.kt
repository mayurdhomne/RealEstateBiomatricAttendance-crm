package com.crm.realestate.data.api

import com.crm.realestate.data.models.AttendanceOverview
import com.crm.realestate.data.models.AttendanceResponse
import com.crm.realestate.data.models.CheckInRequest
import com.crm.realestate.data.models.CheckOutRequest
import com.crm.realestate.data.models.DetailResponse
import com.crm.realestate.data.models.LeaveInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * Retrofit API service interface for attendance endpoints
 */
interface AttendanceApiService {
    
    /**
     * Check-in attendance endpoint
     * @param request CheckInRequest containing location and scan type
     * @return DetailResponse with a success message
     */
    @POST("attendance/check-in/")
    suspend fun checkIn(
        @Body request: CheckInRequest
    ): Response<DetailResponse>
    
    /**
     * Check-out attendance endpoint
     * @param request CheckOutRequest containing location and scan type
     * @return DetailResponse with a success message
     */
    @PUT("attendance/check-out/")
    suspend fun checkOut(
        @Body request: CheckOutRequest
    ): Response<DetailResponse>
    
    /**
     * Get attendance overview for dashboard
     * @return AttendanceOverview with days present, percentage, etc.
     */
    @GET("attendance/overview/")
    suspend fun getAttendanceOverview(): Response<com.crm.realestate.data.models.ApiResponse<AttendanceOverview>>
    
    /**
     * Get leave information for dashboard
     * @return LeaveInfo with sick leaves, other leaves, etc.
     */
    @GET("attendance/leaves/")
    suspend fun getLeaveInfo(): Response<com.crm.realestate.data.models.ApiResponse<LeaveInfo>>
    
    // Note: The server doesn't have a specific endpoint for getting today's attendance status
    // We'll need to implement a local approach to determine attendance status
}