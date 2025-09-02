package com.crm.realestate.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.crm.realestate.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/**
 * Centralized user feedback management system
 * Handles success messages, error dialogs, loading states, and retry mechanisms
 */
class UserFeedbackManager(private val context: Context) {
    
    /**
     * Show success message with optional animation
     */
    fun showSuccess(
        message: String,
        duration: Int = Toast.LENGTH_SHORT,
        anchorView: View? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        if (anchorView != null) {
            val snackbar = Snackbar.make(anchorView, message, Snackbar.LENGTH_LONG)
            snackbar.setBackgroundTint(ContextCompat.getColor(context, R.color.success))
            snackbar.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            snackbar.setAction("OK") { snackbar.dismiss() }
            snackbar.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    onDismiss?.invoke()
                }
            })
            snackbar.show()
        } else {
            Toast.makeText(context, "✓ $message", duration).show()
            onDismiss?.invoke()
        }
    }
    
    /**
     * Show error message with retry option
     */
    fun showError(
        error: ErrorHandler.AppError,
        anchorView: View? = null,
        onRetry: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        val message = ErrorHandler.getDisplayMessage(error)
        
        if (anchorView != null) {
            val snackbar = Snackbar.make(anchorView, message, Snackbar.LENGTH_LONG)
            snackbar.setBackgroundTint(ContextCompat.getColor(context, R.color.error))
            snackbar.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            
            when {
                ErrorHandler.canRetry(error) && onRetry != null -> {
                    snackbar.setAction("RETRY") { onRetry.invoke() }
                }
                ErrorHandler.requiresUserAction(error) -> {
                    snackbar.setAction("SETTINGS") {
                        ErrorHandler.getUserActionIntent(error)?.let { intent ->
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Unable to open settings", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                else -> {
                    snackbar.setAction("OK") { snackbar.dismiss() }
                }
            }
            
            snackbar.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    onDismiss?.invoke()
                }
            })
            snackbar.show()
        } else {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onDismiss?.invoke()
        }
    }
    
    /**
     * Show error dialog with detailed information and actions
     */
    fun showErrorDialog(
        error: ErrorHandler.AppError,
        title: String = "Error",
        onRetry: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(ErrorHandler.getDisplayMessage(error))
            .setCancelable(true)
            .setOnDismissListener { onDismiss?.invoke() }
        
        when {
            ErrorHandler.canRetry(error) && onRetry != null -> {
                builder.setPositiveButton("Retry") { _, _ -> onRetry.invoke() }
                builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            }
            ErrorHandler.requiresUserAction(error) -> {
                builder.setPositiveButton("Open Settings") { _, _ ->
                    ErrorHandler.getUserActionIntent(error)?.let { intent ->
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to open settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            }
            else -> {
                builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            }
        }
        
        builder.show()
    }
    
    /**
     * Show loading dialog with message
     */
    fun showLoadingDialog(
        message: String = "Loading...",
        cancellable: Boolean = false,
        onCancel: (() -> Unit)? = null
    ): AlertDialog {
        val builder = MaterialAlertDialogBuilder(context)
            .setView(R.layout.dialog_loading)
            .setCancelable(cancellable)
        
        if (cancellable && onCancel != null) {
            builder.setOnCancelListener { onCancel.invoke() }
        }
        
        val dialog = builder.create()
        dialog.show()
        
        // Set loading message if custom layout supports it
        // This would require a custom loading dialog layout
        
        return dialog
    }
    
    /**
     * Show confirmation dialog
     */
    fun showConfirmationDialog(
        title: String,
        message: String,
        positiveButtonText: String = "Yes",
        negativeButtonText: String = "No",
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { _, _ -> onConfirm.invoke() }
            .setNegativeButton(negativeButtonText) { _, _ -> onCancel?.invoke() }
            .setCancelable(true)
            .show()
    }
    
    /**
     * Show success animation on a view
     */
    fun animateSuccess(view: View, onComplete: (() -> Unit)? = null) {
        try {
            val animation = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
            animation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    onComplete?.invoke()
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
            view.startAnimation(animation)
        } catch (e: Exception) {
            // Fallback if animation fails
            onComplete?.invoke()
        }
    }
    
    /**
     * Show error animation on a view (shake effect)
     */
    fun animateError(view: View, onComplete: (() -> Unit)? = null) {
        try {
            val animation = AnimationUtils.loadAnimation(context, R.anim.shake)
            animation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    onComplete?.invoke()
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
            view.startAnimation(animation)
        } catch (e: Exception) {
            // Fallback if animation fails
            onComplete?.invoke()
        }
    }
    
    /**
     * Show biometric-specific error with appropriate guidance
     */
    fun showBiometricError(
        error: ErrorHandler.AppError,
        biometricType: String, // "face" or "fingerprint"
        anchorView: View? = null,
        onRetry: (() -> Unit)? = null,
        onAlternative: (() -> Unit)? = null
    ) {
        val title = when (biometricType) {
            "face" -> "Face Recognition Error"
            "fingerprint" -> "Fingerprint Error"
            else -> "Biometric Error"
        }
        
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(ErrorHandler.getDisplayMessage(error))
            .setCancelable(true)
        
        when {
            ErrorHandler.canRetry(error) && onRetry != null -> {
                builder.setPositiveButton("Try Again") { _, _ -> onRetry.invoke() }
                if (onAlternative != null) {
                    builder.setNeutralButton("Use Alternative") { _, _ -> onAlternative.invoke() }
                }
                builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            }
            ErrorHandler.requiresUserAction(error) -> {
                builder.setPositiveButton("Open Settings") { _, _ ->
                    ErrorHandler.getUserActionIntent(error)?.let { intent ->
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to open settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                if (onAlternative != null) {
                    builder.setNeutralButton("Use Alternative") { _, _ -> onAlternative.invoke() }
                }
                builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            }
            else -> {
                if (onAlternative != null) {
                    builder.setPositiveButton("Use Alternative") { _, _ -> onAlternative.invoke() }
                    builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                } else {
                    builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                }
            }
        }
        
        builder.show()
    }
    
    /**
     * Show network-specific error with offline options
     */
    fun showNetworkError(
        error: ErrorHandler.AppError,
        anchorView: View? = null,
        onRetry: (() -> Unit)? = null,
        onOfflineMode: (() -> Unit)? = null
    ) {
        if (anchorView != null) {
            val snackbar = Snackbar.make(anchorView, ErrorHandler.getDisplayMessage(error), Snackbar.LENGTH_INDEFINITE)
            snackbar.setBackgroundTint(ContextCompat.getColor(context, R.color.warning))
            snackbar.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            
            if (onRetry != null) {
                snackbar.setAction("RETRY") { onRetry.invoke() }
            } else if (onOfflineMode != null) {
                snackbar.setAction("OFFLINE") { onOfflineMode.invoke() }
            } else {
                snackbar.setAction("OK") { snackbar.dismiss() }
            }
            
            snackbar.show()
        } else {
            showErrorDialog(error, "Network Error", onRetry)
        }
    }
    
    companion object {
        /**
         * Create UserFeedbackManager instance for an Activity
         */
        fun create(activity: Activity): UserFeedbackManager {
            return UserFeedbackManager(activity)
        }
        
        /**
         * Create UserFeedbackManager instance for a Context
         */
        fun create(context: Context): UserFeedbackManager {
            return UserFeedbackManager(context)
        }
    }
}