package com.crm.realestate.utils

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Base activity that provides comprehensive error handling and user feedback capabilities
 * All activities should extend this class to get consistent error handling behavior
 */
abstract class BaseErrorHandlingActivity : AppCompatActivity() {
    
    protected lateinit var feedbackManager: UserFeedbackManager
    protected lateinit var loadingManager: LoadingStateManager
    protected lateinit var retryManager: RetryManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeErrorHandling()
    }
    
    private fun initializeErrorHandling() {
        feedbackManager = UserFeedbackManager.create(this)
        loadingManager = LoadingStateManager.create(this)
        retryManager = RetryManager()
        
        // Setup accessibility for the entire activity
        setupAccessibility()
        
        // Setup responsive design
        setupResponsiveDesign()
    }
    
    /**
     * Setup accessibility features for the activity
     */
    private fun setupAccessibility() {
        findViewById<ViewGroup>(android.R.id.content)?.let { rootView ->
            AccessibilityHelper.setupAccessibility(rootView)
        }
    }
    
    /**
     * Setup responsive design for the activity
     */
    private fun setupResponsiveDesign() {
        // This can be overridden by subclasses for specific responsive behavior
    }
    
    /**
     * Handle result with comprehensive error handling and user feedback
     */
    protected fun <T> handleResult(
        result: Result<T>,
        onSuccess: (T) -> Unit,
        onError: ((ErrorHandler.AppError) -> Unit)? = null,
        showLoading: Boolean = true,
        anchorView: View? = null
    ) {
        result.handle(
            onSuccess = { data ->
                if (showLoading) loadingManager.hideLoading()
                onSuccess(data)
            },
            onError = { appError ->
                if (showLoading) loadingManager.hideLoading()
                handleError(appError, anchorView, onError)
            },
            onLoading = { message ->
                if (showLoading) loadingManager.showLoading(message)
            },
            context = this
        )
    }
    
    /**
     * Handle error with user feedback and retry options
     */
    protected fun handleError(
        error: ErrorHandler.AppError,
        anchorView: View? = null,
        customHandler: ((ErrorHandler.AppError) -> Unit)? = null,
        onRetry: (() -> Unit)? = null
    ) {
        // Log error for analytics
        ErrorAnalytics.logFromErrorHandler(
            context = this,
            error = error,
            activityContext = this::class.java.simpleName,
            networkStatus = if (NetworkConnectivityMonitor(this).isCurrentlyConnected()) "online" else "offline"
        )
        
        // Use custom handler if provided
        customHandler?.let { 
            it(error)
            return
        }
        
        // Handle specific error types
        when (error) {
            is ErrorHandler.AppError.NetworkUnavailable,
            is ErrorHandler.AppError.NetworkTimeout,
            is ErrorHandler.AppError.ServerError -> {
                feedbackManager.showNetworkError(error, anchorView, onRetry)
            }
            is ErrorHandler.AppError.BiometricUnavailable,
            is ErrorHandler.AppError.BiometricNotEnrolled,
            is ErrorHandler.AppError.BiometricAuthenticationFailed,
            is ErrorHandler.AppError.FaceDetectionFailed,
            is ErrorHandler.AppError.FingerprintNotRecognized -> {
                val biometricType = when (error) {
                    is ErrorHandler.AppError.FaceDetectionFailed -> "face"
                    is ErrorHandler.AppError.FingerprintNotRecognized -> "fingerprint"
                    else -> "biometric"
                }
                feedbackManager.showBiometricError(error, biometricType, anchorView, onRetry)
            }
            is ErrorHandler.AppError.UnauthorizedError -> {
                handleUnauthorizedError()
            }
            else -> {
                feedbackManager.showError(error, anchorView, onRetry)
            }
        }
    }
    
    /**
     * Handle unauthorized error by redirecting to login
     */
    private fun handleUnauthorizedError() {
        feedbackManager.showErrorDialog(
            ErrorHandler.AppError.UnauthorizedError(),
            "Session Expired",
            onDismiss = {
                // Clear user session and redirect to login
                redirectToLogin()
            }
        )
    }
    
    /**
     * Execute operation with retry mechanism
     */
    protected fun <T> executeWithRetry(
        operation: suspend () -> T,
        retryConfig: RetryManager.RetryConfig = RetryManager.NETWORK_RETRY_CONFIG,
        onResult: (Result<T>) -> Unit
    ) {
        lifecycleScope.launch {
            val result = retryManager.executeWithRetry(retryConfig) { attemptNumber ->
                operation()
            }
            
            val finalResult = when (result) {
                is RetryManager.RetryResult.Success -> Result.Success(result.data)
                is RetryManager.RetryResult.Failure -> Result.Error(result.lastException)
            }
            
            onResult(finalResult)
        }
    }
    
    /**
     * Show success feedback
     */
    protected fun showSuccess(
        message: String,
        anchorView: View? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        feedbackManager.showSuccess(message, anchorView = anchorView, onDismiss = onDismiss)
        
        // Log successful resolution if this follows an error
        ErrorAnalytics.getInstance(this).logErrorResolution(
            errorType = "Unknown", // Would need to track previous error
            context = this::class.java.simpleName,
            resolutionMethod = "user_success"
        )
    }
    
    /**
     * Show loading with message
     */
    protected fun showLoading(
        message: String = "Loading...",
        canCancel: Boolean = false,
        operationType: LoadingStateManager.OperationType = LoadingStateManager.OperationType.GENERAL,
        onCancel: (() -> Unit)? = null
    ) {
        loadingManager.showLoading(message, canCancel, operationType, onCancel)
    }
    
    /**
     * Hide loading
     */
    protected fun hideLoading() {
        loadingManager.hideLoading()
    }
    
    /**
     * Show loading overlay on specific view
     */
    protected fun showLoadingOverlay(
        parentView: ViewGroup,
        message: String = "Loading...",
        canCancel: Boolean = false,
        onCancel: (() -> Unit)? = null
    ) {
        loadingManager.showLoadingOverlay(parentView, message, canCancel, onCancel)
    }
    
    /**
     * Update loading message
     */
    protected fun updateLoadingMessage(message: String) {
        loadingManager.updateLoadingMessage(message)
    }
    
    /**
     * Show confirmation dialog
     */
    protected fun showConfirmation(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        feedbackManager.showConfirmationDialog(title, message, onConfirm = onConfirm, onCancel = onCancel)
    }
    
    /**
     * Handle biometric error with specific guidance
     */
    protected fun handleBiometricError(
        error: ErrorHandler.AppError,
        biometricType: String,
        onRetry: (() -> Unit)? = null,
        onAlternative: (() -> Unit)? = null
    ) {
        feedbackManager.showBiometricError(error, biometricType, onRetry = onRetry, onAlternative = onAlternative)
    }
    
    /**
     * Animate success on view
     */
    protected fun animateSuccess(view: View, onComplete: (() -> Unit)? = null) {
        feedbackManager.animateSuccess(view, onComplete)
    }
    
    /**
     * Animate error on view
     */
    protected fun animateError(view: View, onComplete: (() -> Unit)? = null) {
        feedbackManager.animateError(view, onComplete)
    }
    
    /**
     * Check network connectivity and show appropriate feedback
     */
    protected fun checkNetworkAndExecute(
        operation: () -> Unit,
        showOfflineOption: Boolean = false,
        onOfflineMode: (() -> Unit)? = null
    ) {
        val networkMonitor = NetworkConnectivityMonitor(this)
        if (networkMonitor.isCurrentlyConnected()) {
            operation()
        } else {
            val error = ErrorHandler.AppError.NetworkUnavailable()
            if (showOfflineOption && onOfflineMode != null) {
                feedbackManager.showNetworkError(error, onOfflineMode = onOfflineMode)
            } else {
                feedbackManager.showError(error)
            }
        }
    }
    
    /**
     * Handle graceful degradation for hardware unavailability
     */
    protected fun handleHardwareUnavailable(
        hardwareType: String,
        alternativeAction: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        val message = when (hardwareType.lowercase()) {
            "camera" -> "Camera is not available on this device"
            "fingerprint" -> "Fingerprint sensor is not available on this device"
            "location" -> "Location services are not available"
            else -> "$hardwareType is not available on this device"
        }
        
        val error = ErrorHandler.AppError.UnknownError(message)
        
        if (alternativeAction != null) {
            feedbackManager.showErrorDialog(
                error,
                "Hardware Unavailable",
                onDismiss = onDismiss
            )
        } else {
            feedbackManager.showError(error, onDismiss = onDismiss)
        }
    }
    
    /**
     * Abstract method to handle logout/redirect to login
     * Must be implemented by subclasses
     */
    protected abstract fun redirectToLogin()
    
    override fun onDestroy() {
        super.onDestroy()
        loadingManager.cleanup()
    }
}