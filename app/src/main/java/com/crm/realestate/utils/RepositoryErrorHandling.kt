package com.crm.realestate.utils

import android.content.Context
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Repository error handling utilities
 * Provides consistent error handling patterns across all repositories
 */
object RepositoryErrorHandling {
    
    /**
     * Execute repository operation with comprehensive error handling
     */
    suspend fun <T> executeWithErrorHandling(
        context: Context,
        operation: suspend () -> T
    ): Result<T> {
        return try {
            val result = operation()
            Result.Success(result)
        } catch (e: Exception) {
            val appError = handleRepositoryException(context, e)
            Result.Error(e, appError)
        }
    }
    
    /**
     * Execute network operation with specific network error handling
     */
    suspend fun <T> executeNetworkOperation(
        context: Context,
        operation: suspend () -> T
    ): Result<T> {
        return try {
            val result = operation()
            Result.Success(result)
        } catch (e: Exception) {
            val appError = handleNetworkException(context, e)
            Result.Error(e, appError)
        }
    }
    
    /**
     * Execute database operation with specific database error handling
     */
    suspend fun <T> executeDatabaseOperation(
        context: Context,
        operation: suspend () -> T
    ): Result<T> {
        return try {
            val result = operation()
            Result.Success(result)
        } catch (e: Exception) {
            val appError = handleDatabaseException(context, e)
            Result.Error(e, appError)
        }
    }
    
    /**
     * Execute biometric operation with specific biometric error handling
     */
    suspend fun <T> executeBiometricOperation(
        context: Context,
        operation: suspend () -> T
    ): Result<T> {
        return try {
            val result = operation()
            Result.Success(result)
        } catch (e: Exception) {
            val appError = handleBiometricException(context, e)
            Result.Error(e, appError)
        }
    }
    
    /**
     * Handle repository-specific exceptions
     */
    private fun handleRepositoryException(context: Context, exception: Exception): ErrorHandler.AppError {
        return when (exception) {
            is HttpException -> handleHttpException(exception)
            is IOException -> handleIOException(context, exception)
            is SecurityException -> handleSecurityException(context, exception)
            is IllegalArgumentException -> ErrorHandler.AppError.ValidationError("Input", exception.message ?: "Invalid input")
            is IllegalStateException -> ErrorHandler.AppError.UnknownError("Invalid operation state: ${exception.message}")
            else -> ErrorHandler.handleException(context, exception)
        }
    }
    
    /**
     * Handle network-specific exceptions
     */
    private fun handleNetworkException(context: Context, exception: Exception): ErrorHandler.AppError {
        return when (exception) {
            is HttpException -> handleHttpException(exception)
            is UnknownHostException -> {
                if (NetworkConnectivityMonitor(context).isCurrentlyConnected()) {
                    ErrorHandler.AppError.ServerError("Unable to reach server")
                } else {
                    ErrorHandler.AppError.NetworkUnavailable()
                }
            }
            is SocketTimeoutException -> ErrorHandler.AppError.NetworkTimeout()
            is SSLException -> ErrorHandler.AppError.ServerError("Secure connection failed")
            is IOException -> {
                if (NetworkConnectivityMonitor(context).isCurrentlyConnected()) {
                    ErrorHandler.AppError.ServerError("Network communication error")
                } else {
                    ErrorHandler.AppError.NetworkUnavailable()
                }
            }
            else -> ErrorHandler.handleException(context, exception)
        }
    }
    
    /**
     * Handle database-specific exceptions
     */
    private fun handleDatabaseException(context: Context, exception: Exception): ErrorHandler.AppError {
        return when (exception) {
            is android.database.sqlite.SQLiteException -> {
                when {
                    exception.message?.contains("UNIQUE constraint failed") == true -> 
                        ErrorHandler.AppError.ValidationError("Data", "Record already exists")
                    exception.message?.contains("NOT NULL constraint failed") == true -> 
                        ErrorHandler.AppError.ValidationError("Data", "Required field is missing")
                    exception.message?.contains("database is locked") == true -> 
                        ErrorHandler.AppError.DatabaseError("Database is busy, please try again")
                    else -> ErrorHandler.AppError.DatabaseError("Database operation failed")
                }
            }
            is android.database.sqlite.SQLiteFullException -> 
                ErrorHandler.AppError.DatabaseError("Storage is full")
            is android.database.sqlite.SQLiteDiskIOException -> 
                ErrorHandler.AppError.DatabaseError("Storage error occurred")
            else -> ErrorHandler.handleException(context, exception)
        }
    }
    
    /**
     * Handle biometric-specific exceptions
     */
    private fun handleBiometricException(context: Context, exception: Exception): ErrorHandler.AppError {
        return when {
            exception.message?.contains("biometric", ignoreCase = true) == true -> {
                when {
                    exception.message?.contains("not available") == true -> ErrorHandler.AppError.BiometricUnavailable()
                    exception.message?.contains("not enrolled") == true -> ErrorHandler.AppError.BiometricNotEnrolled(context)
                    exception.message?.contains("failed") == true -> ErrorHandler.AppError.BiometricAuthenticationFailed()
                    else -> ErrorHandler.AppError.BiometricAuthenticationFailed()
                }
            }
            exception.message?.contains("face", ignoreCase = true) == true -> 
                ErrorHandler.AppError.FaceDetectionFailed()
            exception.message?.contains("fingerprint", ignoreCase = true) == true -> 
                ErrorHandler.AppError.FingerprintNotRecognized()
            else -> ErrorHandler.handleException(context, exception)
        }
    }
    
    /**
     * Handle HTTP exceptions
     */
    private fun handleHttpException(exception: HttpException): ErrorHandler.AppError {
        val responseBody = try {
            exception.response()?.errorBody()?.string()
        } catch (e: Exception) {
            null
        }
        
        return when (exception.code()) {
            400 -> ErrorHandler.AppError.ValidationError("Request", responseBody ?: "Invalid request")
            401 -> ErrorHandler.AppError.UnauthorizedError()
            403 -> ErrorHandler.AppError.ServerError("Access denied")
            404 -> ErrorHandler.AppError.ServerError("Service not found")
            409 -> {
                when {
                    responseBody?.contains("duplicate", ignoreCase = true) == true -> 
                        ErrorHandler.AppError.DuplicateAttendanceError()
                    responseBody?.contains("attendance", ignoreCase = true) == true -> 
                        ErrorHandler.AppError.AttendanceOutsideWorkHours()
                    else -> ErrorHandler.AppError.ValidationError("Conflict", responseBody ?: "Data conflict occurred")
                }
            }
            422 -> ErrorHandler.AppError.ValidationError("Data", responseBody ?: "Invalid data provided")
            429 -> ErrorHandler.AppError.ServerError("Too many requests. Please try again later")
            in 500..599 -> ErrorHandler.AppError.ServerError(responseBody ?: "Server error occurred")
            else -> ErrorHandler.AppError.UnknownError("HTTP ${exception.code()}: ${responseBody ?: exception.message()}")
        }
    }
    
    /**
     * Handle IO exceptions
     */
    private fun handleIOException(context: Context, exception: IOException): ErrorHandler.AppError {
        return when (exception) {
            is UnknownHostException -> {
                if (NetworkConnectivityMonitor(context).isCurrentlyConnected()) {
                    ErrorHandler.AppError.ServerError("Unable to reach server")
                } else {
                    ErrorHandler.AppError.NetworkUnavailable()
                }
            }
            is SocketTimeoutException -> ErrorHandler.AppError.NetworkTimeout()
            is SSLException -> ErrorHandler.AppError.ServerError("Secure connection failed")
            else -> {
                if (NetworkConnectivityMonitor(context).isCurrentlyConnected()) {
                    ErrorHandler.AppError.ServerError("Network communication error")
                } else {
                    ErrorHandler.AppError.NetworkUnavailable()
                }
            }
        }
    }
    
    /**
     * Handle security exceptions
     */
    private fun handleSecurityException(context: Context, exception: SecurityException): ErrorHandler.AppError {
        return when {
            exception.message?.contains("location", ignoreCase = true) == true -> 
                ErrorHandler.AppError.LocationPermissionDenied(context)
            exception.message?.contains("camera", ignoreCase = true) == true -> 
                ErrorHandler.AppError.ValidationError("Permission", "Camera permission is required")
            exception.message?.contains("biometric", ignoreCase = true) == true -> 
                ErrorHandler.AppError.BiometricUnavailable()
            else -> ErrorHandler.AppError.UnknownError("Permission denied: ${exception.message}")
        }
    }
    
    /**
     * Validate input and return appropriate error
     */
    fun validateInput(
        validationRules: List<Pair<Boolean, String>>
    ): ErrorHandler.AppError? {
        val failedValidation = validationRules.firstOrNull { !it.first }
        return failedValidation?.let { 
            ErrorHandler.AppError.ValidationError("Input", it.second) 
        }
    }
    
    /**
     * Check network connectivity before operation
     */
    fun checkNetworkConnectivity(context: Context): ErrorHandler.AppError? {
        return if (!NetworkConnectivityMonitor(context).isCurrentlyConnected()) {
            ErrorHandler.AppError.NetworkUnavailable()
        } else {
            null
        }
    }
}

/**
 * Extension functions for easier repository error handling
 */

/**
 * Execute repository operation with error handling
 */
suspend fun <T> executeRepositoryOperation(
    context: Context,
    operation: suspend () -> T
): Result<T> = RepositoryErrorHandling.executeWithErrorHandling(context, operation)

/**
 * Execute network operation with error handling
 */
suspend fun <T> executeNetworkCall(
    context: Context,
    operation: suspend () -> T
): Result<T> = RepositoryErrorHandling.executeNetworkOperation(context, operation)

/**
 * Execute database operation with error handling
 */
suspend fun <T> executeDatabaseCall(
    context: Context,
    operation: suspend () -> T
): Result<T> = RepositoryErrorHandling.executeDatabaseOperation(context, operation)

/**
 * Execute biometric operation with error handling
 */
suspend fun <T> executeBiometricCall(
    context: Context,
    operation: suspend () -> T
): Result<T> = RepositoryErrorHandling.executeBiometricOperation(context, operation)