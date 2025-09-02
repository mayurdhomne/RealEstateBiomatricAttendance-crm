package com.crm.realestate.data.repository

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import com.crm.realestate.data.api.BiometricApiService
import com.crm.realestate.data.models.BiometricAvailability
import com.crm.realestate.data.models.BiometricRegistrationRequest
import com.crm.realestate.data.models.BiometricRegistrationResponse
import com.crm.realestate.data.models.BiometricResult
import com.crm.realestate.data.models.Result
import com.crm.realestate.network.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Repository implementation for biometric operations
 * Handles hardware detection, biometric authentication, and registration API integration
 */
class BiometricRepository(private val context: Context) {
    
    private val biometricApiService: BiometricApiService by lazy {
        ApiConfig.provideRetrofit(context) {
            // Handle unauthorized callback
        }.create(BiometricApiService::class.java)
    }
    
    /**
     * Check availability of biometric hardware on device
     * @return BiometricAvailability containing hardware status
     */
    suspend fun checkBiometricAvailability(): BiometricAvailability {
        return withContext(Dispatchers.Main) {
            val biometricManager = BiometricManager.from(context)
            
            // Enhanced fingerprint availability checking
            val fingerprintStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            val canAuthenticateFingerprint = fingerprintStatus == BiometricManager.BIOMETRIC_SUCCESS
            
            // Check if device has fingerprint hardware (more comprehensive check)
            val hasFingerprint = when (fingerprintStatus) {
                BiometricManager.BIOMETRIC_SUCCESS -> true
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> true // Hardware available but no fingerprints enrolled
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> true // Hardware available but needs update
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> false
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> false
                else -> false
            }
            
            // Check if device has camera for face detection (simplified check)
            val hasFaceDetection = context.packageManager.hasSystemFeature("android.hardware.camera.front")
            
            BiometricAvailability(
                hasFaceDetection = hasFaceDetection,
                hasFingerprint = hasFingerprint,
                canAuthenticateFingerprint = canAuthenticateFingerprint
            )
        }
    }
    
    /**
     * Authenticate user using fingerprint biometric for registration
     * @param activity FragmentActivity required for BiometricPrompt
     * @return BiometricResult indicating success or failure
     */
    suspend fun authenticateFingerprint(activity: FragmentActivity): BiometricResult {
        return suspendCoroutine { continuation ->
            // First check if biometric authentication is available
            val biometricManager = BiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            
            when (canAuthenticate) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                    continuation.resume(BiometricResult.Error("No fingerprint hardware available on this device", canAuthenticate))
                    return@suspendCoroutine
                }
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                    continuation.resume(BiometricResult.Error("Fingerprint hardware is currently unavailable", canAuthenticate))
                    return@suspendCoroutine
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    continuation.resume(BiometricResult.Error("No fingerprints enrolled. Please add a fingerprint in device settings first", canAuthenticate))
                    return@suspendCoroutine
                }
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                    continuation.resume(BiometricResult.Error("Security update required for fingerprint authentication", canAuthenticate))
                    return@suspendCoroutine
                }
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                    continuation.resume(BiometricResult.Error("Fingerprint authentication is not supported on this device", canAuthenticate))
                    return@suspendCoroutine
                }
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                    continuation.resume(BiometricResult.Error("Fingerprint authentication status unknown", canAuthenticate))
                    return@suspendCoroutine
                }
            }
            
            val executor = ContextCompat.getMainExecutor(context)
            
            val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Validate that the authentication was successful and the fingerprint was recognized
                    if (result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
                        continuation.resume(BiometricResult.Success)
                    } else {
                        continuation.resume(BiometricResult.Error("Fingerprint authentication failed: Invalid authentication type", null))
                    }
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    
                    val errorMessage = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED -> {
                            continuation.resume(BiometricResult.Cancelled)
                            return
                        }
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            continuation.resume(BiometricResult.Cancelled)
                            return
                        }
                        BiometricPrompt.ERROR_HW_UNAVAILABLE -> "Fingerprint hardware is unavailable. Try again later"
                        BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> "Unable to process fingerprint. Please try again"
                        BiometricPrompt.ERROR_TIMEOUT -> "Fingerprint authentication timed out. Please try again"
                        BiometricPrompt.ERROR_NO_SPACE -> "Not enough storage space for fingerprint operation"
                        BiometricPrompt.ERROR_CANCELED -> "Fingerprint authentication was cancelled"
                        BiometricPrompt.ERROR_LOCKOUT -> "Too many failed attempts. Please try again later"
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Fingerprint authentication is permanently locked. Use device credentials"
                        BiometricPrompt.ERROR_VENDOR -> "Vendor-specific fingerprint error occurred"
                        else -> errString.toString()
                    }
                    
                    continuation.resume(BiometricResult.Error(errorMessage, errorCode))
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Don't immediately fail - let user try again
                    continuation.resume(BiometricResult.Failed)
                }
            })
            
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Register Fingerprint")
                .setSubtitle("Place your finger on the sensor to complete registration")
                .setDescription("This will register your fingerprint for attendance tracking")
                .setNegativeButtonText("Cancel")
                .setConfirmationRequired(true) // Require explicit confirmation
                .build()
            
            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                continuation.resume(BiometricResult.Error("Failed to start fingerprint authentication: ${e.message}", null))
            }
        }
    }
    
    /**
     * Validate fingerprint registration by performing multiple authentication attempts
     * This ensures the fingerprint is properly registered and can be consistently recognized
     * @param activity FragmentActivity required for BiometricPrompt
     * @return BiometricResult indicating validation success or failure
     */
    suspend fun validateFingerprintRegistration(activity: FragmentActivity): BiometricResult {
        return suspendCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(context)
            
            val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Validate that the authentication was successful
                    if (result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
                        continuation.resume(BiometricResult.Success)
                    } else {
                        continuation.resume(BiometricResult.Error("Fingerprint validation failed: Invalid authentication type", null))
                    }
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || 
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        continuation.resume(BiometricResult.Cancelled)
                    } else {
                        continuation.resume(BiometricResult.Error("Fingerprint validation failed: $errString", errorCode))
                    }
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    continuation.resume(BiometricResult.Failed)
                }
            })
            
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Validate Fingerprint Registration")
                .setSubtitle("Place your finger on the sensor to validate registration")
                .setDescription("Confirming your fingerprint was registered successfully")
                .setNegativeButtonText("Skip Validation")
                .build()
            
            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                continuation.resume(BiometricResult.Error("Failed to validate fingerprint: ${e.message}", null))
            }
        }
    }

    /**
     * Check if fingerprint hardware is available and ready for registration
     * @return Pair<Boolean, String> - (isAvailable, statusMessage)
     */
    fun getFingerprintRegistrationStatus(): Pair<Boolean, String> {
        val biometricManager = BiometricManager.from(context)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        
        return when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> Pair(true, "Fingerprint hardware is ready for registration")
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Pair(false, "No fingerprint hardware available")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Pair(false, "Fingerprint hardware is currently unavailable")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Pair(false, "No fingerprints enrolled. Please add a fingerprint in device settings first")
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> Pair(false, "Security update required for fingerprint authentication")
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> Pair(false, "Fingerprint authentication is not supported")
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> Pair(false, "Fingerprint authentication status unknown")
            else -> Pair(false, "Fingerprint authentication is not available")
        }
    }

    /**
     * Register biometrics with backend API
     * @param employeeId Employee ID from login response
     * @param faceRegistered Whether face registration was successful
     * @param fingerprintRegistered Whether fingerprint registration was successful
     * @return Result containing registration response or error
     */
    suspend fun registerBiometrics(
        employeeId: String,
        faceRegistered: Boolean,
        fingerprintRegistered: Boolean
    ): BiometricRegistrationResponse {
        return withContext(Dispatchers.IO) {
            try {
                val request = BiometricRegistrationRequest(
                    employeeId = employeeId,
                    faceRegistered = faceRegistered,
                    fingerprintRegistered = fingerprintRegistered
                )
                
                val response = biometricApiService.registerBiometrics(request)
                
                if (response.isSuccessful) {
                    val registrationResponse = response.body()
                    if (registrationResponse != null) {
                        registrationResponse
                    } else {
                        throw Exception("Empty response body")
                    }
                } else {
                    val errorMessage = when (response.code()) {
                        400 -> "Invalid registration data"
                        401 -> "Authentication required. Please login again"
                        403 -> "Access denied for biometric registration"
                        404 -> "Registration service not available"
                        500 -> "Server error. Please try again later"
                        else -> "Registration failed: ${response.message()}"
                    }
                    throw Exception(errorMessage)
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }
}