package com.crm.realestate.utils

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel with comprehensive error handling capabilities
 * Provides consistent error handling patterns across all ViewModels
 */
abstract class BaseErrorHandlingViewModel : ViewModel() {
    private val _currentError = MutableStateFlow<ErrorHandler.AppError?>(null)
    val currentError: StateFlow<ErrorHandler.AppError?> = _currentError

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Error state
    private val _error = MutableStateFlow<ErrorHandler.AppError?>(null)
    val error: StateFlow<ErrorHandler.AppError?> = _error

    // Success message state
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage

    /**
     * Execute operation with comprehensive error handling
     */
    protected fun <T> executeOperation(
        operation: suspend () -> Result<T>,
        onSuccess: ((T) -> Unit)? = null,
        onError: ((ErrorHandler.AppError) -> Unit)? = null,
        showLoading: Boolean = true,
        context: Context? = null
    ) {
        viewModelScope.launch {
            try {
                if (showLoading) _isLoading.value = true
                clearErrors()

                val result = operation()

                when (result) {
                    is Result.Success -> {
                        onSuccess?.invoke(result.data)
                    }
                    is Result.Error -> {
                        val appError = result.appError ?: context?.let {
                            ErrorHandler.handleException(it, result.exception)
                        } ?: ErrorHandler.AppError.UnknownError(result.exception.message)

                        _error.value = appError
                        onError?.invoke(appError)
                    }
                    is Result.Loading -> {
                        // Handle loading state if needed
                    }
                }
            } catch (e: Exception) {
                val appError = context?.let {
                    ErrorHandler.handleException(it, e)
                } ?: ErrorHandler.AppError.UnknownError(e.message)

                _error.value = appError
                onError?.invoke(appError)
            } finally {
                if (showLoading) _isLoading.value = false
            }
        }
    }

    /**
     * Execute operation with retry mechanism
     */
    open fun <T> executeWithRetry(
        operation: suspend () -> T,
        retryConfig: RetryManager.RetryConfig = RetryManager.NETWORK_RETRY_CONFIG,
        onSuccess: ((T) -> Unit)? = null,
        onError: ((ErrorHandler.AppError) -> Unit)? = null,
        showLoading: Boolean = true,
        context: Context? = null
    ) {
        viewModelScope.launch {
            if (showLoading) _isLoading.value = true

            try {
                val retryManager = RetryManager()
                val result = retryManager.executeWithRetry(retryConfig) { attemptNumber ->
                    operation()
                }
                
                when (result) {
                    is RetryManager.RetryResult.Success -> {
                        onSuccess?.invoke(result.data)
                    }
                    is RetryManager.RetryResult.Failure -> {
                        val error = context?.let { ErrorHandler.handleException(it, result.lastException) }
                            ?: ErrorHandler.AppError.UnknownError(result.lastException.message ?: "Operation failed")
                        onError?.invoke(error) ?: setError(error)
                    }
                }
            } catch (e: Exception) {
                val error = context?.let { ErrorHandler.handleException(it, e) }
                    ?: ErrorHandler.AppError.UnknownError(e.message ?: "Operation failed")
                onError?.invoke(error) ?: setError(error)
            } finally {
                if (showLoading) _isLoading.value = false
            }
        }
    }

    private fun <T> handleResult(
        result: Result<T>,
        onSuccess: ((T) -> Unit)?,
        onError: ((ErrorHandler.AppError) -> Unit)?,
        context: Context?
    ) {
        when (result) {
            is Result.Success -> {
                onSuccess?.invoke(result.data)
            }
            is Result.Error -> {
                val error = context?.let { ErrorHandler.handleException(it, result.exception) }
                    ?: ErrorHandler.AppError.UnknownError(result.exception.message ?: "Operation failed")
                onError?.invoke(error) ?: setError(error)
            }
            is Result.Loading -> {
                // Handle loading state if needed
            }
        }
    }

    /**
     * Set loading state
     */
    protected fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }

    /**
     * Set error state
     */
    fun setError(error: ErrorHandler.AppError) {
        _currentError.value = error
    }

    /**
     * Set success message
     */
    protected fun setSuccessMessage(message: String) {
        _successMessage.value = message
    }

    /**
     * Clear all error states
     */
    open fun clearErrors() {
        _currentError.value = null
        _successMessage.value = null
    }

    /**
     * Clear success message
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /**
     * Check if currently loading
     */
    fun isCurrentlyLoading(): Boolean = _isLoading.value

    /**
     * Get current error
     */
    fun getCurrentError(): ErrorHandler.AppError? = _currentError.value

    /**
     * Get current success message
     */
    fun getCurrentSuccessMessage(): String? = _successMessage.value
}

/**
 * Extension functions for easier ViewModel error handling
 */

/**
 * Handle validation with error state management
 */
fun BaseErrorHandlingViewModel.validateAndExecute(
    validationRules: List<Pair<Boolean, String>>,
    onValidationSuccess: () -> Unit
) {
    val failedValidation = validationRules.firstOrNull { !it.first }
    if (failedValidation != null) {
        setError(ErrorHandler.AppError.ValidationError("Input", failedValidation.second))
    } else {
        clearErrors()
        onValidationSuccess()
    }
}

/**
 * Handle network operations with appropriate error handling
 */
fun BaseErrorHandlingViewModel.executeNetworkOperation(
    operation: suspend () -> Result<Any>,
    onSuccess: ((Any) -> Unit)? = null,
    onError: ((ErrorHandler.AppError) -> Unit)? = null,
    context: Context? = null
) {
    executeWithRetry(
        operation = operation,
        retryConfig = RetryManager.NETWORK_RETRY_CONFIG,
        onSuccess = onSuccess,
        onError = onError,
        context = context
    )
}

/**
 * Handle biometric operations with appropriate error handling
 */
fun BaseErrorHandlingViewModel.executeBiometricOperation(
    operation: suspend () -> Result<Any>,
    onSuccess: ((Any) -> Unit)? = null,
    onError: ((ErrorHandler.AppError) -> Unit)? = null,
    context: Context? = null
) {
    executeWithRetry(
        operation = operation,
        retryConfig = RetryManager.BIOMETRIC_RETRY_CONFIG,
        onSuccess = onSuccess,
        onError = onError,
        context = context
    )
}

/**
 * Handle database operations with appropriate error handling
 */
fun BaseErrorHandlingViewModel.executeDatabaseOperation(
    operation: suspend () -> Result<Any>,
    onSuccess: ((Any) -> Unit)? = null,
    onError: ((ErrorHandler.AppError) -> Unit)? = null,
    context: Context? = null
) {
    executeWithRetry(
        operation = operation,
        retryConfig = RetryManager.DATABASE_RETRY_CONFIG,
        onSuccess = onSuccess,
        onError = onError,
        context = context
    )
}