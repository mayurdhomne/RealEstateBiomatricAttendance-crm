package com.crm.realestate.data.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Matches server payload observed in logs:
 * {
 *   "refresh": "<jwt>",
 *   "access": "<jwt>",
 *   "employee_id": 123,
 *   "name": "John Doe"
 * }
 */
 data class NetworkLoginResponse(
     @SerializedName("refresh") val refresh: String?,
     @SerializedName("access") val access: String?,
     @SerializedName("employee_id") val employeeId: String?,
     @SerializedName("name") val name: String?
 )
