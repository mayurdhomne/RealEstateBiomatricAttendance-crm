package com.crm.realestate.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.crm.realestate.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages loading states and progress indicators throughout the application
 * Provides consistent loading UI patterns and state management
 */
class LoadingStateManager(private val context: Context) {
    
    /**
     * Loading state data class
     */
    data class LoadingState(
        val isLoading: Boolean = false,
        val message: String = "Loading...",
        val canCancel: Boolean = false,
        val progress: Int? = null, // For progress bars (0-100)
        val operationType: OperationType = OperationType.GENERAL
    )
    
    /**
     * Types of operations for different loading behaviors
     */
    enum class OperationType {
        GENERAL,
        NETWORK,
        BIOMETRIC,
        LOCATION,
        DATABASE,
        FILE_UPLOAD,
        AUTHENTICATION
    }
    
    // Loading state flow
    private val _loadingState = MutableStateFlow(LoadingState())
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()
    
    // Current loading dialog
    private var currentDialog: AlertDialog? = null
    
    // Loading overlay views
    private var loadingOverlay: View? = null
    
    /**
     * Show loading state with message
     */
    fun showLoading(
        message: String = "Loading...",
        canCancel: Boolean = false,
        operationType: OperationType = OperationType.GENERAL,
        onCancel: (() -> Unit)? = null
    ) {
        _loadingState.value = LoadingState(
            isLoading = true,
            message = message,
            canCancel = canCancel,
            operationType = operationType
        )
        
        showLoadingDialog(message, canCancel, onCancel)
    }
    
    /**
     * Show loading with progress
     */
    fun showLoadingWithProgress(
        message: String = "Loading...",
        progress: Int,
        canCancel: Boolean = false,
        onCancel: (() -> Unit)? = null
    ) {
        _loadingState.value = LoadingState(
            isLoading = true,
            message = message,
            canCancel = canCancel,
            progress = progress
        )
        
        updateLoadingProgress(progress)
    }
    
    /**
     * Update loading message
     */
    fun updateLoadingMessage(message: String) {
        val currentState = _loadingState.value
        if (currentState.isLoading) {
            _loadingState.value = currentState.copy(message = message)
            updateDialogMessage(message)
        }
    }
    
    /**
     * Update loading progress
     */
    fun updateProgress(progress: Int) {
        val currentState = _loadingState.value
        if (currentState.isLoading) {
            _loadingState.value = currentState.copy(progress = progress)
            updateLoadingProgress(progress)
        }
    }
    
    /**
     * Hide loading state
     */
    fun hideLoading() {
        _loadingState.value = LoadingState(isLoading = false)
        dismissLoadingDialog()
        hideLoadingOverlay()
    }
    
    /**
     * Show loading overlay on a specific view
     */
    fun showLoadingOverlay(
        parentView: ViewGroup,
        message: String = "Loading...",
        canCancel: Boolean = false,
        onCancel: (() -> Unit)? = null
    ) {
        hideLoadingOverlay() // Remove any existing overlay
        
        val overlay = createLoadingOverlay(parentView.context, message, canCancel, onCancel)
        parentView.addView(overlay)
        loadingOverlay = overlay
        
        _loadingState.value = LoadingState(
            isLoading = true,
            message = message,
            canCancel = canCancel
        )
    }
    
    /**
     * Hide loading overlay
     */
    fun hideLoadingOverlay() {
        loadingOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            loadingOverlay = null
        }
    }
    
    /**
     * Show operation-specific loading
     */
    fun showOperationLoading(operationType: OperationType, onCancel: (() -> Unit)? = null) {
        val (message, canCancel) = getOperationLoadingConfig(operationType)
        showLoading(message, canCancel, operationType, onCancel)
    }
    
    /**
     * Create loading overlay view
     */
    private fun createLoadingOverlay(
        context: Context,
        message: String,
        canCancel: Boolean,
        onCancel: (() -> Unit)?
    ): View {
        val overlay = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.loading_overlay))
            elevation = 10f
        }
        
        // Create loading content
        val loadingContent = View.inflate(context, R.layout.loading_overlay, null)
        val progressIndicator = loadingContent.findViewById<CircularProgressIndicator>(R.id.progressIndicator)
        val messageText = loadingContent.findViewById<TextView>(R.id.tvLoadingMessage)
        val cancelButton = loadingContent.findViewById<View>(R.id.btnCancel)
        
        messageText.text = message
        
        if (canCancel && onCancel != null) {
            cancelButton.visibility = View.VISIBLE
            cancelButton.setOnClickListener { onCancel.invoke() }
        } else {
            cancelButton.visibility = View.GONE
        }
        
        overlay.addView(loadingContent)
        return overlay
    }
    
    /**
     * Show loading dialog
     */
    private fun showLoadingDialog(
        message: String,
        canCancel: Boolean,
        onCancel: (() -> Unit)?
    ) {
        dismissLoadingDialog()
        
        val dialogView = View.inflate(context, R.layout.dialog_loading, null)
        val messageText = dialogView.findViewById<TextView>(R.id.tvLoadingMessage)
        messageText.text = message
        
        val builder = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setCancelable(canCancel)
        
        if (canCancel && onCancel != null) {
            builder.setOnCancelListener { onCancel.invoke() }
        }
        
        currentDialog = builder.create()
        currentDialog?.show()
    }
    
    /**
     * Update dialog message
     */
    private fun updateDialogMessage(message: String) {
        currentDialog?.findViewById<TextView>(R.id.tvLoadingMessage)?.text = message
    }
    
    /**
     * Update loading progress in dialog
     */
    private fun updateLoadingProgress(progress: Int) {
        currentDialog?.findViewById<ProgressBar>(R.id.progressBar)?.let { progressBar ->
            progressBar.isIndeterminate = false
            progressBar.progress = progress
        }
    }
    
    /**
     * Dismiss loading dialog
     */
    private fun dismissLoadingDialog() {
        currentDialog?.dismiss()
        currentDialog = null
    }
    
    /**
     * Get loading configuration for operation type
     */
    private fun getOperationLoadingConfig(operationType: OperationType): Pair<String, Boolean> {
        return when (operationType) {
            OperationType.NETWORK -> "Connecting to server..." to true
            OperationType.BIOMETRIC -> "Preparing biometric scan..." to true
            OperationType.LOCATION -> "Getting your location..." to true
            OperationType.DATABASE -> "Saving data..." to false
            OperationType.FILE_UPLOAD -> "Uploading file..." to true
            OperationType.AUTHENTICATION -> "Authenticating..." to false
            OperationType.GENERAL -> "Loading..." to true
        }
    }
    
    /**
     * Check if currently loading
     */
    fun isLoading(): Boolean = _loadingState.value.isLoading
    
    /**
     * Get current loading message
     */
    fun getCurrentMessage(): String = _loadingState.value.message
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        dismissLoadingDialog()
        hideLoadingOverlay()
        _loadingState.value = LoadingState(isLoading = false)
    }
    
    companion object {
        /**
         * Create LoadingStateManager for an Activity
         */
        fun create(activity: Activity): LoadingStateManager {
            return LoadingStateManager(activity)
        }
        
        /**
         * Create LoadingStateManager for a Context
         */
        fun create(context: Context): LoadingStateManager {
            return LoadingStateManager(context)
        }
        
        /**
         * Extension function to show loading on any ViewGroup
         */
        fun ViewGroup.showLoading(
            message: String = "Loading...",
            canCancel: Boolean = false,
            onCancel: (() -> Unit)? = null
        ): LoadingStateManager {
            val manager = LoadingStateManager(context)
            manager.showLoadingOverlay(this, message, canCancel, onCancel)
            return manager
        }
    }
}