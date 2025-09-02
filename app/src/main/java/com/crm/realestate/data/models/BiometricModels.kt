package com.crm.realestate.data.models

import com.google.gson.annotations.SerializedName

/**
 * Data class for biometric registration request payload
 */
data class BiometricRegistrationRequest(
    @SerializedName("employee_id")
    val employeeId: String,
    
    @SerializedName("face_registered")
    val faceRegistered: Boolean,
    
    @SerializedName("fingerprint_registered")
    val fingerprintRegistered: Boolean
)

/**
 * Data class for biometric registration response
 */
data class BiometricRegistrationResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("employee_id")
    val employeeId: String
)

/**
 * Data class representing biometric availability on device
 */
data class BiometricAvailability(
    val hasFaceDetection: Boolean,
    val hasFingerprint: Boolean,
    val canAuthenticateFingerprint: Boolean
)

/**
 * Sealed class representing biometric authentication results
 */
sealed class BiometricResult {
    object Success : BiometricResult()
    data class Error(val message: String, val errorCode: Int? = null) : BiometricResult()
    object Cancelled : BiometricResult()
    object Failed : BiometricResult()
}