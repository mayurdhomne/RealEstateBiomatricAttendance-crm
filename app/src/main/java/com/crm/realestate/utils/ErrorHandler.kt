package com.crm.realestate.utils

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.biometric.BiometricManager
import com.crm.realestate.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Centralized error handling utility for the application
 * Provides user-friendly error messages and actionable solutions
 */
object ErrorHandler {
    
    /**
     * Sealed class representing different types of errors in the application
     */
    sealed class AppError(
        val message: String,
        val actionableMessage: String? = null,
        val canRetry: Boolean = false,
        var requiresUserAction: Boolean = false,
        var userActionIntent: Intent? = null
    ) {
        // Network related errors
        class NetworkUnavailable : AppError(
            message = "No internet connection available",
            actionableMessage = "Please check your internet connection and try again",
            canRetry = true
        )
        
        class NetworkTimeout : AppError(
            message = "Request timed out",
            actionableMessage = "The server is taking too long to respond. Please try again",
            canRetry = true
        )
        
        class ServerError(serverMessage: String? = null) : AppError(
            message = serverMessage ?: "Server error occurred",
            actionableMessage = "There's a problem with our servers. Please try again later",
            canRetry = true
        )
        
        class UnauthorizedError : AppError(
            message = "Authentication failed",
            actionableMessage = "Your session has expired. Please log in again",
            canRetry = false
        )
        
        class ValidationError(field: String, reason: String) : AppError(
            message = "$field: $reason",
            actionableMessage = "Please correct the highlighted field and try again",
            canRetry = false
        )
        
        // Biometric related errors
        class BiometricUnavailable : AppError(
            message = "Biometric authentication is not available",
            actionableMessage = "Your device doesn't support biometric authentication",
            canRetry = false
        )
        
        class BiometricNotEnrolled(context: Context) : AppError(
            message = "No biometric credentials enrolled",
            actionableMessage = "Please set up biometric authentication in your device settings",
            canRetry = false,
            requiresUserAction = true,
            userActionIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
        )
        
        class BiometricAuthenticationFailed : AppError(
            message = "Biometric authentication failed",
            actionableMessage = "Please try again or use an alternative method",
            canRetry = true
        )
        
        class FaceDetectionFailed : AppError(
            message = "Face detection failed",
            actionableMessage = "Please ensure good lighting and position your face clearly in the camera",
            canRetry = true
        )
        
        class FingerprintNotRecognized : AppError(
            message = "Fingerprint not recognized",
            actionableMessage = "Please clean your finger and sensor, then try again",
            canRetry = true
        )
        
        // Location related errors
        class LocationPermissionDenied(context: Context) : AppError(
            message = "Location permission is required",
            actionableMessage = "Please grant location permission to record attendance",
            canRetry = false,
            requiresUserAction = true,
            userActionIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
        )
        
        class LocationServiceDisabled(context: Context) : AppError(
            message = "Location services are disabled",
            actionableMessage = "Please enable location services to record attendance",
            canRetry = false,
            requiresUserAction = true,
            userActionIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        )
        
        class LocationUnavailable : AppError(
            message = "Unable to get current location",
            actionableMessage = "Please ensure you're in an area with good GPS signal and try again",
            canRetry = true
        )
        
        // Database related errors
        class DatabaseError(operation: String) : AppError(
            message = "Database error during $operation",
            actionableMessage = "There was a problem saving your data. Please try again",
            canRetry = true
        )
        
        // Generic errors
        class UnknownError(originalMessage: String? = null) : AppError(
            message = originalMessage ?: "An unexpected error occurred",
            actionableMessage = "Something went wrong. Please try again or contact support if the problem persists",
            canRetry = true
        )
        
        class DuplicateAttendanceError : AppError(
            message = "Duplicate attendance attempt",
            actionableMessage = "You have already recorded attendance recently. Please wait before trying again",
            canRetry = false
        )
        
        class AttendanceOutsideWorkHours : AppError(
            message = "Attendance outside work hours",
            actionableMessage = "Attendance can only be recorded during work hours",
            canRetry = false
        )
    }
    
    /**
     * Convert various exceptions to user-friendly AppError instances
     */
    fun handleException(context: Context, exception: Throwable): AppError {
        return when (exception) {
            is UnknownHostException -> {
                if (isNetworkAvailable(context)) {
                    AppError.ServerError("Unable to reach server")
                } else {
                    AppError.NetworkUnavailable()
                }
            }
            is SocketTimeoutException -> AppError.NetworkTimeout()
            is SSLException -> AppError.ServerError("Secure connection failed")
            is IOException -> {
                if (isNetworkAvailable(context)) {
                    AppError.ServerError("Network communication error")
                } else {
                    AppError.NetworkUnavailable()
                }
            }
            is SecurityException -> {
                when {
                    exception.message?.contains("location", ignoreCase = true) == true -> 
                        AppError.LocationPermissionDenied(context)
                    else -> AppError.UnknownError(exception.message)
                }
            }
            else -> AppError.UnknownError(exception.message)
        }
    }
    
    /**
     * Handle biometric-specific errors
     */
    fun handleBiometricError(context: Context, errorCode: Int, errorMessage: String): AppError {
        return when (errorCode) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AppError.BiometricNotEnrolled(context)
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> AppError.BiometricUnavailable()
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> AppError.BiometricUnavailable()
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> AppError.BiometricUnavailable()
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> AppError.BiometricUnavailable()
            else -> AppError.BiometricAuthenticationFailed()
        }
    }
    
    /**
     * Handle HTTP error responses
     */
    fun handleHttpError(responseCode: Int, responseMessage: String?): AppError {
        return when (responseCode) {
            401 -> AppError.UnauthorizedError()
            400 -> AppError.ValidationError("Request", responseMessage ?: "Invalid request")
            404 -> AppError.ServerError("Service not found")
            429 -> AppError.ServerError("Too many requests. Please try again later")
            in 500..599 -> AppError.ServerError(responseMessage)
            else -> AppError.UnknownError("HTTP $responseCode: $responseMessage")
        }
    }
    
    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Get user-friendly error message for display
     */
    fun getDisplayMessage(error: AppError): String {
        return error.actionableMessage ?: error.message
    }
    
    /**
     * Check if error allows retry
     */
    fun canRetry(error: AppError): Boolean = error.canRetry
    
    /**
     * Check if error requires user action
     */
    fun requiresUserAction(error: AppError): Boolean = error.requiresUserAction
    
    /**
     * Get intent for user action if available
     */
    fun getUserActionIntent(error: AppError): Intent? = error.userActionIntent
}