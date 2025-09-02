package com.crm.realestate.data.models

import com.google.gson.annotations.SerializedName

/**
 * Data class for check-in request payload
 */
data class CheckInRequest(
    @SerializedName("check_in_latitude")
    val checkInLatitude: Double,
    
    @SerializedName("check_in_longitude")
    val checkInLongitude: Double,
    
    @SerializedName("scan_type")
    val scanType: String // The API accepts only "face" and "finger" as valid choices
)

/**
 * Data class for check-out request payload
 */
data class CheckOutRequest(
    @SerializedName("check_out_latitude")
    val checkOutLatitude: Double,
    
    @SerializedName("check_out_longitude")
    val checkOutLongitude: Double,
    
    @SerializedName("scan_type")
    val scanType: String // The API accepts only "face" and "finger" as valid choices
)

/**
 * Data class for attendance API response
 */
data class AttendanceResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("employee_id")
    val employeeId: String,
    
    @SerializedName("check_in_time")
    val checkInTime: String?,
    
    @SerializedName("check_out_time")
    val checkOutTime: String?,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("message")
    val message: String
)

/**
 * Data class representing today's attendance status
 */
data class TodayAttendance(
    val hasCheckedIn: Boolean,
    val hasCheckedOut: Boolean,
    val checkInTime: String?,
    val checkOutTime: String?
)

/**
 * Data class for attendance overview on dashboard
 */
data class AttendanceOverview(
    @SerializedName("days_present")
    val daysPresent: Int,
    
    @SerializedName("total_working_days")
    val totalWorkingDays: Int,
    
    @SerializedName("last_check_in")
    val lastCheckIn: String?,
    
    @SerializedName("present_percentage")
    val presentPercentage: Float
)

/**
 * Data class for leave information
 */
data class LeaveInfo(
    @SerializedName("sick_leaves")
    val sickLeaves: Int,
    
    @SerializedName("other_leaves")
    val otherLeaves: Int,
    
    @SerializedName("total_leaves")
    val totalLeaves: Int
)