package com.crm.realestate.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.crm.realestate.R
import com.crm.realestate.data.models.BiometricResult
import com.crm.realestate.data.models.BiometricRegistrationResponse
import com.crm.realestate.data.repository.AuthRepository
import com.crm.realestate.data.repository.BiometricRepository
import com.crm.realestate.utils.BaseErrorHandlingActivity
import com.crm.realestate.utils.ErrorHandler
import com.crm.realestate.utils.LoadingStateManager
import com.crm.realestate.utils.Result
import com.crm.realestate.utils.RetryManager
import kotlinx.coroutines.launch

/**
 * Activity for biometric registration with adaptive flow based on device capabilities:
 * 
 * 1. If the mobile camera is not working for face scan, register using fingerprint
 * 2. If the device doesn't support fingerprint, register using face scan
 * 3. If both options are available, register with both methods
 */
class BiometricRegistrationActivity : BaseErrorHandlingActivity() {

    private lateinit var biometricRepository: BiometricRepository
    private lateinit var authRepository: AuthRepository
    
    private lateinit var btnScanFaceID: MaterialButton
    private lateinit var btnScanFingerprintID: MaterialButton
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var tvProgressText: TextView
    private lateinit var tvInstructions: TextView
    
    private var employeeId: String? = null
    private var faceRegistered = false
    private var fingerprintRegistered = false
    private var currentStep = RegistrationStep.FACE

    companion object {
        private const val REQUEST_FACE_SCAN = 1001
    }
    
    private enum class RegistrationStep {
        FACE, FINGERPRINT, COMPLETE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_biometric_registration)

        initializeViews()
        initializeRepositories()
        setupClickListeners()
        
        // Get employee ID from intent or stored auth info
        lifecycleScope.launch {
            try {
                employeeId = authRepository.getEmployeeInfo()?.employeeId
                if (employeeId == null) {
                    val error = ErrorHandler.AppError.UnauthorizedError()
                    handleError(error, onRetry = { redirectToLogin() })
                    return@launch
                }
                
                checkBiometricAvailability()
            } catch (e: Exception) {
                val error = ErrorHandler.handleException(this@BiometricRegistrationActivity, e)
                handleError(error, onRetry = { recreate() })
            }
        }
    }
    
    private fun initializeViews() {
        btnScanFaceID = findViewById(R.id.btnScanFaceID)
        btnScanFingerprintID = findViewById(R.id.btnScanFingerprintID)
        progressIndicator = findViewById(R.id.registrationProgress)
        tvProgressText = findViewById(R.id.tvProgressText)
        tvInstructions = findViewById(R.id.tvInstructions)
    }
    
    private fun initializeRepositories() {
        biometricRepository = BiometricRepository(this)
        authRepository = AuthRepository(this)
    }
    
    private fun setupClickListeners() {
        btnScanFaceID.setOnClickListener {
            if (currentStep == RegistrationStep.FACE) {
                startFaceRegistration()
            }
        }

        btnScanFingerprintID.setOnClickListener {
            if (currentStep == RegistrationStep.FINGERPRINT) {
                startFingerprintRegistration()
            }
        }
    }
    
    /**
     * Check for available biometric hardware and configure registration flow accordingly
     * 
     * Registration logic:
     * 1. If camera is not working for face scan, register using fingerprint
     * 2. If device doesn't support fingerprint, register using face scan
     * 3. If both options are available, register with both methods
     */
    private suspend fun checkBiometricAvailability() {
        try {
            val availability = biometricRepository.checkBiometricAvailability()
            val fingerprintStatus = biometricRepository.getFingerprintRegistrationStatus()
            
            runOnUiThread {
                // If no biometric hardware is available, show graceful degradation
                if (!availability.hasFaceDetection && !availability.hasFingerprint) {
                    showHardwareUnavailableDialog(
                        "biometric sensors",
                        alternativeAction = { redirectToDashboard() },
                        onDismiss = { redirectToDashboard() }
                    )
                    return@runOnUiThread
                }
                
                // Determine available biometric methods
                val isFaceAvailable = availability.hasFaceDetection
                val isFingerprintAvailable = availability.hasFingerprint && fingerprintStatus.first
                
                // Configure UI based on available biometric methods
                configureBiometricOptions(isFaceAvailable, isFingerprintAvailable)
                
                // Set registration flow based on available hardware
                when {
                    // Both options available - register with both
                    isFaceAvailable && isFingerprintAvailable -> {
                        currentStep = RegistrationStep.FACE
                        showSuccess("Please register both face and fingerprint for attendance")
                    }
                    
                    // Only face detection available
                    isFaceAvailable && !isFingerprintAvailable -> {
                        currentStep = RegistrationStep.FACE
                        showSuccess("Only face detection is available. Fingerprint registration will be skipped.")
                        btnScanFingerprintID.isEnabled = false
                        btnScanFingerprintID.text = if (!availability.hasFingerprint) {
                            "Fingerprint Not Available"
                        } else {
                            "Fingerprint Setup Required"
                        }
                    }
                    
                    // Only fingerprint available
                    !isFaceAvailable && isFingerprintAvailable -> {
                        currentStep = RegistrationStep.FINGERPRINT
                        showSuccess("Only fingerprint is available. Face registration will be skipped.")
                        btnScanFaceID.isEnabled = false
                        btnScanFaceID.text = "Face Detection Not Available"
                    }
                    
                    // Edge case - should never happen based on the earlier check
                    else -> {
                        showHardwareUnavailableDialog(
                            "biometric sensors",
                            alternativeAction = { redirectToDashboard() },
                            onDismiss = { redirectToDashboard() }
                        )
                        return@runOnUiThread
                    }
                }
                
                updateUIForCurrentStep()
            }
        } catch (e: Exception) {
            runOnUiThread {
                val error = ErrorHandler.handleException(this@BiometricRegistrationActivity, e)
                handleError(error, onRetry = { 
                    lifecycleScope.launch { checkBiometricAvailability() }
                })
            }
        }
    }
    
    /**
     * Configure biometric options UI based on available hardware
     */
    private fun configureBiometricOptions(isFaceAvailable: Boolean, isFingerprintAvailable: Boolean) {
        // Configure face registration option
        if (!isFaceAvailable) {
            btnScanFaceID.isEnabled = false
            btnScanFaceID.text = "Face Detection Not Available"
            btnScanFaceID.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            btnScanFaceID.setTextColor(getColor(android.R.color.black))
        }
        
        // Configure fingerprint registration option
        if (!isFingerprintAvailable) {
            btnScanFingerprintID.isEnabled = false
            btnScanFingerprintID.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            btnScanFingerprintID.setTextColor(getColor(android.R.color.black))
            
            if (!biometricRepository.getFingerprintRegistrationStatus().first) {
                btnScanFingerprintID.text = "Fingerprint Setup Required"
                // Show detailed message about what's needed if fingerprint hardware exists but is not set up
                val error = ErrorHandler.AppError.BiometricNotEnrolled(this@BiometricRegistrationActivity)
                handleError(error)
            } else {
                btnScanFingerprintID.text = "Fingerprint Not Available"
            }
        }
    }
    
    private fun startFaceRegistration() {
        showLoading("Preparing face registration...", operationType = LoadingStateManager.OperationType.BIOMETRIC)
        
        try {
            val intent = Intent(this, FacescanActivity::class.java)
            intent.putExtra(FacescanActivity.EXTRA_REGISTRATION_MODE, true)
            startActivityForResult(intent, REQUEST_FACE_SCAN)
        } catch (e: Exception) {
            hideLoading()
            feedbackManager.showError(
                ErrorHandler.AppError.FaceDetectionFailed(),
                findViewById(android.R.id.content),
                onRetry = { startFaceRegistration() }
            )
        }
    }
    
    private fun startFingerprintRegistration() {
        // Check fingerprint status before starting with more detailed feedback
        val fingerprintStatus = biometricRepository.getFingerprintRegistrationStatus()
        if (!fingerprintStatus.first) {
            // Show specific error message based on fingerprint status
            runOnUiThread {
                feedbackManager.showError(
                    ErrorHandler.AppError.BiometricNotEnrolled(this@BiometricRegistrationActivity),
                    findViewById(android.R.id.content)
                )
                
                // Provide direct link to settings
                val settingsIntent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                if (settingsIntent.resolveActivity(packageManager) != null) {
                    showConfirmation(
                        "Open Settings?",
                        "Would you like to open Security settings to add fingerprints?",
                        onConfirm = { 
                            startActivity(settingsIntent)
                        },
                        onCancel = {
                            updateUIForCurrentStep()
                        }
                    )
                } else {
                    updateUIForCurrentStep()
                }
            }
            return
        }
        
        showLoading("Preparing fingerprint registration...", operationType = LoadingStateManager.OperationType.BIOMETRIC)
        
        lifecycleScope.launch {
            try {
                val result = biometricRepository.authenticateFingerprint(this@BiometricRegistrationActivity)
                hideLoading()
                
                when (result) {
                    is BiometricResult.Success -> {
                        fingerprintRegistered = true
                        // Show success message using Toast instead of Snackbar to avoid OK button
                        Toast.makeText(
                            this@BiometricRegistrationActivity,
                            "Fingerprint registered successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Automatically proceed after a short delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            proceedToNextStep()
                        }, 2000) // 2 second delay
                    }
                    is BiometricResult.Cancelled -> {
                        showSuccess("Fingerprint registration cancelled")
                        updateUIForCurrentStep()
                    }
                    is BiometricResult.Failed -> {
                        feedbackManager.showError(
                            ErrorHandler.AppError.FingerprintNotRecognized(),
                            findViewById(android.R.id.content)
                        )
                        updateUIForCurrentStep()
                    }
                    is BiometricResult.Error -> {
                        feedbackManager.showError(
                            ErrorHandler.AppError.BiometricAuthenticationFailed(),
                            findViewById(android.R.id.content)
                        )
                        updateUIForCurrentStep()
                    }
                }
            } catch (e: Exception) {
                hideLoading()
                feedbackManager.showError(
                    ErrorHandler.AppError.UnknownError("Failed to start fingerprint registration: ${e.message}"),
                    findViewById(android.R.id.content)
                )
                updateUIForCurrentStep()
            }
        }
    }
    
    private fun validateFingerprintRegistration() {
        showLoading("Validating fingerprint registration...", operationType = LoadingStateManager.OperationType.BIOMETRIC)
        
        executeWithRetry<BiometricResult>(
            operation = {
                val validationResult = biometricRepository.validateFingerprintRegistration(this@BiometricRegistrationActivity)
                when (validationResult) {
                    is BiometricResult.Success -> validationResult
                    is BiometricResult.Error -> throw RuntimeException("Validation error: ${validationResult.message}")
                    is BiometricResult.Cancelled -> validationResult
                    is BiometricResult.Failed -> validationResult
                }
            },
            retryConfig = RetryManager.BIOMETRIC_RETRY_CONFIG
        ) { result ->
            handleResult(result,
                onSuccess = { validationResult ->
                    fingerprintRegistered = true
                    when (validationResult) {
                        is BiometricResult.Success -> {
                            // Using Toast instead of feedbackManager.showSuccess to avoid OK button
                            Toast.makeText(
                                this@BiometricRegistrationActivity, 
                                "Fingerprint registered and validated successfully!",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            // Automatically proceed after a short delay
                            Handler(Looper.getMainLooper()).postDelayed({
                                proceedToNextStep()
                            }, 2000) // 2 second delay
                        }
                        is BiometricResult.Cancelled -> {
                            // Using Toast instead of feedbackManager.showSuccess to avoid OK button
                            Toast.makeText(
                                this@BiometricRegistrationActivity, 
                                "Fingerprint registered (validation skipped)",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            // Automatically proceed after a short delay
                            Handler(Looper.getMainLooper()).postDelayed({
                                proceedToNextStep()
                            }, 2000) // 2 second delay
                        }
                        is BiometricResult.Failed -> {
                            // Using Toast instead of feedbackManager.showSuccess to avoid OK button
                            Toast.makeText(
                                this@BiometricRegistrationActivity, 
                                "Fingerprint registered (validation failed but registration successful)",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            // Automatically proceed after a short delay
                            Handler(Looper.getMainLooper()).postDelayed({
                                proceedToNextStep()
                            }, 2000) // 2 second delay
                        }
                        else -> proceedToNextStep()
                    }
                },
                onError = { error ->
                    // Still proceed since initial registration was successful
                    fingerprintRegistered = true
                    // Using Toast instead of feedbackManager.showSuccess to avoid OK button
                    Toast.makeText(
                        this@BiometricRegistrationActivity, 
                        "Fingerprint registered (validation had issues but registration successful)",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Automatically proceed after a short delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        proceedToNextStep()
                    }, 2000) // 2 second delay
                }
            )
        }
    }
    
    private fun handleFingerprintError(message: String, errorCode: Int?) {
        val error = ErrorHandler.handleBiometricError(this, errorCode ?: -1, message)
        
        // For certain errors, provide automatic retry
        val shouldAutoRetry = errorCode == BiometricPrompt.ERROR_TIMEOUT || 
                             errorCode == BiometricPrompt.ERROR_UNABLE_TO_PROCESS
        
        if (shouldAutoRetry) {
            feedbackManager.showError(
                error, 
                findViewById(android.R.id.content),
                onRetry = { 
                    // Auto-retry after a short delay for these recoverable errors
                    btnScanFingerprintID.postDelayed({
                        startFingerprintRegistration()
                    }, 2000)
                }
            )
        } else {
            feedbackManager.showError(
                error, 
                findViewById(android.R.id.content),
                onRetry = { updateUIForCurrentStep() }
            )
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        hideLoading()
        
        if (requestCode == REQUEST_FACE_SCAN) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val success = data?.getBooleanExtra(FacescanActivity.EXTRA_REGISTRATION_SUCCESS, false) ?: false
                    val message = data?.getStringExtra(FacescanActivity.EXTRA_REGISTRATION_MESSAGE) ?: "Face registered"
                    val qualityScore = data?.getFloatExtra(FacescanActivity.EXTRA_QUALITY_SCORE, 0f) ?: 0f
                    
                    if (success) {
                        faceRegistered = true
                        hideLoading()
                        
                        // Show success with dialog and automatically proceed to next step
                        // Using Toast instead of Snackbar to avoid the OK button
                        Toast.makeText(
                            this@BiometricRegistrationActivity,
                            "Face registered successfully! Quality: ${String.format("%.1f", qualityScore * 100)}%",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Automatically proceed after a short delay to allow user to see message
                        Handler(Looper.getMainLooper()).postDelayed({
                            proceedToNextStep()
                        }, 2000) // 2 second delay
                    } else {
                        hideLoading()
                        feedbackManager.showError(
                            ErrorHandler.AppError.FaceDetectionFailed(),
                            findViewById(android.R.id.content),
                            onRetry = { updateUIForCurrentStep() }
                        )
                    }
                }
                Activity.RESULT_CANCELED -> {
                    hideLoading()
                    val message = data?.getStringExtra(FacescanActivity.EXTRA_REGISTRATION_MESSAGE) ?: "Face registration cancelled"
                    showSuccess(message)
                    updateUIForCurrentStep() // Reset UI
                }
                else -> {
                    hideLoading()
                    feedbackManager.showError(
                        ErrorHandler.AppError.FaceDetectionFailed(),
                        findViewById(android.R.id.content),
                        onRetry = { updateUIForCurrentStep() }
                    )
                }
            }
        }
    }
    
    /**
     * Proceed to the next step in the registration flow based on available biometric methods
     * 
     * Registration flow:
     * 1. Face registration (if available)
     * 2. Fingerprint registration (if available)
     * 3. Complete registration
     */
    private fun proceedToNextStep() {
        lifecycleScope.launch {
            val availability = biometricRepository.checkBiometricAvailability()
            val fingerprintStatus = biometricRepository.getFingerprintRegistrationStatus()
            
            // Determine available biometric methods
            val isFaceAvailable = availability.hasFaceDetection
            val isFingerprintAvailable = availability.hasFingerprint && fingerprintStatus.first
            
            runOnUiThread {
                when (currentStep) {
                    RegistrationStep.FACE -> {
                        if (isFingerprintAvailable) {
                            // If fingerprint is available, proceed to fingerprint registration
                            currentStep = RegistrationStep.FINGERPRINT
                            updateUIForCurrentStep()
                        } else {
                            // Skip fingerprint step if not available and complete registration
                            currentStep = RegistrationStep.COMPLETE
                            completeRegistration()
                        }
                    }
                    
                    RegistrationStep.FINGERPRINT -> {
                        // After fingerprint registration, complete the process
                        currentStep = RegistrationStep.COMPLETE
                        completeRegistration()
                    }
                    
                    RegistrationStep.COMPLETE -> {
                        // Already complete, do nothing
                    }
                }
            }
        }
    }
    
    /**
     * Update the UI based on the current registration step and available biometric methods
     * 
     * UI states:
     * 1. Face Registration: Enable face button, disable fingerprint button
     * 2. Fingerprint Registration: Disable face button, enable fingerprint button
     * 3. Complete: Both buttons disabled, show registration status
     */
    private fun updateUIForCurrentStep() {
        lifecycleScope.launch {
            val availability = biometricRepository.checkBiometricAvailability()
            val fingerprintStatus = biometricRepository.getFingerprintRegistrationStatus()
            
            // Determine available biometric methods
            val isFaceAvailable = availability.hasFaceDetection
            val isFingerprintAvailable = availability.hasFingerprint && fingerprintStatus.first
            val bothAvailable = isFaceAvailable && isFingerprintAvailable
            
            runOnUiThread {
                // Update progress and button states based on current step
                when (currentStep) {
                    RegistrationStep.FACE -> {
                        // Calculate appropriate progress
                        val progress = if (bothAvailable) 25 else 50
                        updateProgress(progress)
                        
                        // Update instructions
                        tvInstructions.text = if (bothAvailable) {
                            "Step 1 of 2: Register your face for attendance"
                        } else {
                            "Step 1 of 1: Register your face for attendance"
                        }
                        
                        // Face button - enabled if available
                        if (isFaceAvailable) {
                            btnScanFaceID.isEnabled = true
                            btnScanFaceID.backgroundTintList = getColorStateList(R.color.colorPrimary)
                            btnScanFaceID.setTextColor(getColor(android.R.color.white))
                            btnScanFaceID.text = "Scan Face ID"
                        }
                        
                        // Fingerprint button - disabled during face step
                        btnScanFingerprintID.isEnabled = false
                        btnScanFingerprintID.backgroundTintList = getColorStateList(android.R.color.darker_gray)
                        btnScanFingerprintID.setTextColor(getColor(android.R.color.black))
                        btnScanFingerprintID.text = "Scan Fingerprint ID"
                    }
                    
                    RegistrationStep.FINGERPRINT -> {
                        // Update progress
                        updateProgress(75)
                        
                        // Update instructions
                        tvInstructions.text = "Step 2 of 2: Register your fingerprint for attendance"
                        
                        // Face button - show completion status
                        btnScanFaceID.isEnabled = false
                        btnScanFaceID.backgroundTintList = getColorStateList(android.R.color.darker_gray)
                        btnScanFaceID.setTextColor(getColor(android.R.color.black))
                        btnScanFaceID.text = if (faceRegistered) {
                            "✓ Face Registered"
                        } else {
                            "Face Registration Skipped"
                        }
                        
                        // Fingerprint button - enabled if available
                        if (isFingerprintAvailable) {
                            btnScanFingerprintID.isEnabled = true
                            btnScanFingerprintID.backgroundTintList = getColorStateList(R.color.colorPrimary)
                            btnScanFingerprintID.setTextColor(getColor(android.R.color.white))
                            btnScanFingerprintID.text = "Scan Fingerprint ID"
                        } else {
                            btnScanFingerprintID.isEnabled = false
                            btnScanFingerprintID.backgroundTintList = getColorStateList(android.R.color.darker_gray)
                            btnScanFingerprintID.setTextColor(getColor(android.R.color.black))
                            btnScanFingerprintID.text = if (!availability.hasFingerprint) {
                                "Fingerprint Not Available"
                            } else {
                                "Fingerprint Setup Required"
                            }
                        }
                    }
                    
                    RegistrationStep.COMPLETE -> {
                        // Registration complete - update progress to 100%
                        updateProgress(100)
                        
                        // Update instructions
                        tvInstructions.text = "Registration complete! Redirecting to dashboard..."
                        
                        // Update face button status
                        btnScanFaceID.isEnabled = false
                        btnScanFaceID.backgroundTintList = getColorStateList(android.R.color.darker_gray)
                        btnScanFaceID.setTextColor(getColor(android.R.color.black))
                        btnScanFaceID.text = if (faceRegistered) {
                            "✓ Face Registered"
                        } else {
                            "Face Registration Skipped"
                        }
                        
                        // Update fingerprint button status
                        btnScanFingerprintID.isEnabled = false
                        btnScanFingerprintID.backgroundTintList = getColorStateList(android.R.color.darker_gray)
                        btnScanFingerprintID.setTextColor(getColor(android.R.color.black))
                        btnScanFingerprintID.text = if (fingerprintRegistered) {
                            "✓ Fingerprint Registered"
                        } else {
                            "Fingerprint Registration Skipped"
                        }
                    }
                }
                
                // Show a summary of registration status
                val registeredCount = (if (faceRegistered) 1 else 0) + (if (fingerprintRegistered) 1 else 0)
                val totalPossible = (if (isFaceAvailable) 1 else 0) + (if (isFingerprintAvailable) 1 else 0)
                val statusText = "$registeredCount of $totalPossible biometric methods registered"
                
                // Update the registration info TextView
                findViewById<TextView?>(R.id.tvRegistrationInfo)?.text = statusText
            }
        }
    }
    
    /**
     * Handle hardware unavailability scenarios with clear user guidance
     * Using a different name to avoid conflict with the final method in BaseErrorHandlingActivity
     */
    private fun showHardwareUnavailableDialog(
        hardwareName: String,
        alternativeAction: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    ) {
        val title = "No $hardwareName available"
        val message = "Your device doesn't support the required $hardwareName for biometric registration. " +
                      "Please contact your system administrator for alternative options."
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Continue") { dialog, _ ->
                dialog.dismiss()
                alternativeAction?.invoke()
            }
            
        if (onDismiss != null) {
            dialog.setOnDismissListener { onDismiss.invoke() }
        }
        
        dialog.show()
    }
    
    /**
     * Complete the registration process by sending registered biometric data to server
     */
    private fun completeRegistration() {
        showLoading("Completing registration...", operationType = LoadingStateManager.OperationType.NETWORK)
        
        employeeId?.let { id ->
            executeWithRetry<BiometricRegistrationResponse>(
                operation = {
                    biometricRepository.registerBiometrics(
                        employeeId = id,
                        faceRegistered = faceRegistered,
                        fingerprintRegistered = fingerprintRegistered
                    )
                },
                retryConfig = RetryManager.NETWORK_RETRY_CONFIG
            ) { result ->
                handleResult(result,
                    onSuccess = { response ->
                        // Create detailed success message based on registered methods
                        val registeredMethods = mutableListOf<String>()
                        if (faceRegistered) registeredMethods.add("face")
                        if (fingerprintRegistered) registeredMethods.add("fingerprint")
                        
                        val successMessage = when {
                            registeredMethods.isEmpty() -> "Registration completed with no biometric methods."
                            registeredMethods.size == 1 -> "Registration completed with ${registeredMethods[0]} authentication."
                            else -> "Registration completed with ${registeredMethods.joinToString(" and ")} authentication."
                        }
                        
                        // Using Toast instead of feedbackManager.showSuccess to avoid OK button
                        Toast.makeText(
                            this@BiometricRegistrationActivity, 
                            successMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Automatically redirect to dashboard after a short delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            redirectToDashboard()
                        }, 2000) // 2 second delay
                    },
                    onError = { error ->
                        handleError(error, onRetry = { completeRegistration() })
                    }
                )
            }
        } ?: run {
            hideLoading()
            val error = ErrorHandler.AppError.UnauthorizedError()
            handleError(error, onRetry = { redirectToLogin() })
        }
    }
    
    override fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    /**
     * Update progress indicator and text
     */
    private fun updateProgress(progress: Int) {
        progressIndicator.visibility = View.VISIBLE
        progressIndicator.max = 100
        progressIndicator.progress = progress
        tvProgressText.visibility = View.VISIBLE
        tvProgressText.text = "Registration Progress: $progress%"
    }
    
    /**
     * Show success message with optional action
     */
    private fun showSuccess(message: String, action: (() -> Unit)? = null) {
        runOnUiThread {
            // For face registration success, use Toast to avoid OK button
            if (message.contains("Face registered successfully")) {
                Toast.makeText(this@BiometricRegistrationActivity, message, Toast.LENGTH_SHORT).show()
                // If there's an action, execute it after a short delay
                action?.let {
                    Handler(Looper.getMainLooper()).postDelayed({ it.invoke() }, 2000)
                }
            } else {
                // For other messages, use a snackbar
                val snackbar = com.google.android.material.snackbar.Snackbar
                    .make(findViewById(android.R.id.content), message, 
                         com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                
                if (action != null) {
                    snackbar.setAction("OK") { action.invoke() }
                }
                
                snackbar.show()
            }
        }
    }
    
    /**
     * Navigate to dashboard after registration
     */
    private fun redirectToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    

}