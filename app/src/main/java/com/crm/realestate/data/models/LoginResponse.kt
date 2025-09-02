package com.crm.realestate.data.models

import com.google.gson.annotations.SerializedName

/**
 * Data class representing the response from login API
 * Contains authentication token and employee information
 */
data class LoginResponse(
    @SerializedName("token")
    val token: String,
    
    @SerializedName("employee_id")
    val employeeId: String,
    
    @SerializedName("username")
    val username: String,
    
    @SerializedName("biometrics_registered")
    val biometricsRegistered: Boolean,
    
    @SerializedName("employee_info")
    val employeeInfo: EmployeeInfo
)

/**
 * Data class representing employee information
 */
data class EmployeeInfo(
    @SerializedName("employee_id")
    val employeeId: String,
    
    @SerializedName("full_name")
    val fullName: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("department")
    val department: String,
    
    @SerializedName("designation")
    val designation: String
)