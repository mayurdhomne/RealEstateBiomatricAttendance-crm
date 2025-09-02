package com.crm.realestate.utils

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * Helper class for implementing accessibility features throughout the app
 * Ensures the app is usable by people with disabilities
 */
object AccessibilityHelper {
    
    /**
     * Setup accessibility for a view hierarchy
     */
    fun setupAccessibility(rootView: ViewGroup) {
        traverseViewHierarchy(rootView) { view ->
            when (view) {
                is Button -> setupButtonAccessibility(view)
                is EditText -> setupEditTextAccessibility(view)
                is ImageView -> setupImageViewAccessibility(view)
                is TextView -> setupTextViewAccessibility(view)
                is ViewGroup -> setupViewGroupAccessibility(view)
            }
        }
    }
    
    /**
     * Setup accessibility for buttons
     */
    private fun setupButtonAccessibility(button: Button) {
        // Ensure minimum touch target size (48dp)
        ensureMinimumTouchTarget(button)
        
        // Add role information
        ViewCompat.setAccessibilityDelegate(button, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
                info.isClickable = true
                
                // Add action description if content description is available
                if (!button.contentDescription.isNullOrEmpty()) {
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_CLICK,
                            "Activate ${button.contentDescription}"
                        )
                    )
                }
            }
        })
    }
    
    /**
     * Setup accessibility for EditText fields
     */
    private fun setupEditTextAccessibility(editText: EditText) {
        // Ensure minimum touch target size
        ensureMinimumTouchTarget(editText)
        
        ViewCompat.setAccessibilityDelegate(editText, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = EditText::class.java.name
                info.isEditable = true
                info.isFocusable = true
                
                // Add hint as accessibility description if no content description
                if (editText.contentDescription.isNullOrEmpty() && !editText.hint.isNullOrEmpty()) {
                    info.contentDescription = "Input field for ${editText.hint}"
                }
            }
        })
    }
    
    /**
     * Setup accessibility for ImageView
     */
    private fun setupImageViewAccessibility(imageView: ImageView) {
        // Only setup if the image is clickable or focusable
        if (imageView.isClickable || imageView.isFocusable) {
            ensureMinimumTouchTarget(imageView)
        }
        
        // Ensure decorative images are ignored by screen readers
        if (imageView.contentDescription.isNullOrEmpty() && !imageView.isClickable) {
            ViewCompat.setImportantForAccessibility(imageView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO)
        }
    }
    
    /**
     * Setup accessibility for TextView
     */
    private fun setupTextViewAccessibility(textView: TextView) {
        // If TextView is clickable, ensure minimum touch target
        if (textView.isClickable) {
            ensureMinimumTouchTarget(textView)
        }
        
        // Add role information for clickable text views
        if (textView.isClickable) {
            ViewCompat.setAccessibilityDelegate(textView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                    info.isClickable = true
                }
            })
        }
    }
    
    /**
     * Setup accessibility for ViewGroup
     */
    private fun setupViewGroupAccessibility(viewGroup: ViewGroup) {
        // If ViewGroup is clickable (like cards), ensure accessibility
        if (viewGroup.isClickable) {
            ensureMinimumTouchTarget(viewGroup)
            
            ViewCompat.setAccessibilityDelegate(viewGroup, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                    info.isClickable = true
                }
            })
        }
    }
    
    /**
     * Ensure minimum touch target size (48dp)
     */
    private fun ensureMinimumTouchTarget(view: View) {
        val minSize = (48 * view.context.resources.displayMetrics.density).toInt()
        
        view.post {
            val layoutParams = view.layoutParams
            var needsUpdate = false
            
            if (view.width < minSize) {
                view.minimumWidth = minSize
                needsUpdate = true
            }
            
            if (view.height < minSize) {
                view.minimumHeight = minSize
                needsUpdate = true
            }
            
            if (needsUpdate) {
                view.requestLayout()
            }
        }
    }
    
    /**
     * Traverse view hierarchy and apply function to each view
     */
    private fun traverseViewHierarchy(view: View, action: (View) -> Unit) {
        action(view)
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                traverseViewHierarchy(view.getChildAt(i), action)
            }
        }
    }
    
    /**
     * Announce text to screen reader
     */
    fun announceForAccessibility(view: View, text: String) {
        if (isAccessibilityEnabled(view.context)) {
            view.announceForAccessibility(text)
        }
    }
    
    /**
     * Send accessibility event
     */
    fun sendAccessibilityEvent(view: View, eventType: Int) {
        if (isAccessibilityEnabled(view.context)) {
            view.sendAccessibilityEvent(eventType)
        }
    }
    
    /**
     * Check if accessibility services are enabled
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return accessibilityManager.isEnabled
    }
    
    /**
     * Setup loading state accessibility
     */
    fun setupLoadingAccessibility(
        loadingView: View,
        isLoading: Boolean,
        loadingMessage: String = "Loading"
    ) {
        if (isLoading) {
            ViewCompat.setAccessibilityLiveRegion(loadingView, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
            loadingView.contentDescription = loadingMessage
            announceForAccessibility(loadingView, loadingMessage)
        } else {
            ViewCompat.setAccessibilityLiveRegion(loadingView, ViewCompat.ACCESSIBILITY_LIVE_REGION_NONE)
        }
    }
    
    /**
     * Setup error state accessibility
     */
    fun setupErrorAccessibility(
        errorView: View,
        errorMessage: String,
        hasRetryAction: Boolean = false
    ) {
        ViewCompat.setAccessibilityLiveRegion(errorView, ViewCompat.ACCESSIBILITY_LIVE_REGION_ASSERTIVE)
        
        val fullMessage = if (hasRetryAction) {
            "$errorMessage. Double tap to retry."
        } else {
            errorMessage
        }
        
        errorView.contentDescription = fullMessage
        announceForAccessibility(errorView, fullMessage)
    }
    
    /**
     * Setup success state accessibility
     */
    fun setupSuccessAccessibility(
        successView: View,
        successMessage: String
    ) {
        ViewCompat.setAccessibilityLiveRegion(successView, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        successView.contentDescription = successMessage
        announceForAccessibility(successView, successMessage)
    }
    
    /**
     * Setup form field accessibility with validation
     */
    fun setupFormFieldAccessibility(
        field: EditText,
        label: String,
        isRequired: Boolean = false,
        errorMessage: String? = null
    ) {
        val description = buildString {
            append(label)
            if (isRequired) append(", required")
            if (!errorMessage.isNullOrEmpty()) {
                append(", error: $errorMessage")
            }
        }
        
        field.contentDescription = description
        
        // Set up live region for error announcements
        if (!errorMessage.isNullOrEmpty()) {
            ViewCompat.setAccessibilityLiveRegion(field, ViewCompat.ACCESSIBILITY_LIVE_REGION_ASSERTIVE)
            announceForAccessibility(field, "Error: $errorMessage")
        } else {
            ViewCompat.setAccessibilityLiveRegion(field, ViewCompat.ACCESSIBILITY_LIVE_REGION_NONE)
        }
    }
    
    /**
     * Setup biometric scan accessibility
     */
    fun setupBiometricScanAccessibility(
        scanView: View,
        scanType: String, // "face" or "fingerprint"
        isScanning: Boolean
    ) {
        val message = if (isScanning) {
            "Scanning $scanType, please wait"
        } else {
            "Ready to scan $scanType, double tap to start"
        }
        
        scanView.contentDescription = message
        
        if (isScanning) {
            ViewCompat.setAccessibilityLiveRegion(scanView, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
            announceForAccessibility(scanView, message)
        }
    }
    
    /**
     * Setup attendance card accessibility
     */
    fun setupAttendanceCardAccessibility(
        cardView: View,
        title: String,
        value: String,
        additionalInfo: String? = null
    ) {
        val description = buildString {
            append("$title: $value")
            if (!additionalInfo.isNullOrEmpty()) {
                append(", $additionalInfo")
            }
        }
        
        cardView.contentDescription = description
        
        // If card is clickable, add action description
        if (cardView.isClickable) {
            ViewCompat.setAccessibilityDelegate(cardView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(
                        AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_CLICK,
                            "View details for $title"
                        )
                    )
                }
            })
        }
    }
    
    /**
     * Setup navigation accessibility
     */
    fun setupNavigationAccessibility(
        navigationView: View,
        currentScreen: String,
        destinationScreen: String
    ) {
        navigationView.contentDescription = "Navigate from $currentScreen to $destinationScreen"
        
        ViewCompat.setAccessibilityDelegate(navigationView, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        "Go to $destinationScreen"
                    )
                )
            }
        })
    }
}