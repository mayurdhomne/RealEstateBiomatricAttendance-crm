package com.crm.realestate.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.crm.realestate.R
import com.crm.realestate.data.models.BiometricResult
import com.crm.realestate.data.models.Result
import com.crm.realestate.data.repository.AuthRepository
import com.crm.realestate.data.repository.BiometricRepository
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_register)

        initializeViews()
        initializeRepositories()
        setupClickListeners()
        
        // Get employee ID from intent or stored auth info
        lifecycleScope.launch {
            employeeId = authRepository.getEmployeeInfo()?.employeeId
            if (employeeId == null) {
                Toast.makeText(this@RegisterActivity, "Employee information not found. Please login again.", Toast.LENGTH_LONG).show()
                redirectToLogin()
                return@launch
            }
            
            checkBiometricAvailability()
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
        
        val tvLogin: TextView = findViewById(R.id.tvLogin)
        tvLogin.setOnClickListener {
            redirectToLogin()
        }
    }

    private suspend fun checkBiometricAvailability() {
        val availability = biometricRepository.checkBiometricAvailability()
        
        runOnUiThread {
            // Update UI based on hardware availability
            if (!availability.hasFaceDetection) {
                btnScanFaceID.isEnabled = false
                btnScanFaceID.text = "Face Detection Not Available"
            }
            
            if (!availability.hasFingerprint) {
                btnScanFingerprintID.isEnabled = false
                btnScanFingerprintID.text = "Fingerprint Not Available"
            }
            
            // If no biometric hardware is available, show error
            if (!availability.hasFaceDetection && !availability.hasFingerprint) {
                Toast.makeText(this, "No biometric hardware available on this device", Toast.LENGTH_LONG).show()
                redirectToDashboard() // Skip biometric registration
                return@runOnUiThread
            }
            
            updateUIForCurrentStep()
        }
    }
    
    private fun startFaceRegistration() {
        showLoading(true, "Preparing face registration...")
        
        val intent = Intent(this, FacescanActivity::class.java)
        intent.putExtra("REGISTRATION_MODE", true)
        startActivityForResult(intent, REQUEST_FACE_SCAN)
    }
    
    private fun startFingerprintRegistration() {
        showLoading(true, "Preparing fingerprint registration...")
        
        lifecycleScope.launch {
            val result = biometricRepository.authenticateFingerprint(this@RegisterActivity)
            
            runOnUiThread {
                showLoading(false)
                
                when (result) {
                    is BiometricResult.Success -> {
                        fingerprintRegistered = true
                        Toast.makeText(this@RegisterActivity, "Fingerprint registered successfully!", Toast.LENGTH_SHORT).show()
                        proceedToNextStep()
                    }
                    is BiometricResult.Error -> {
                        Toast.makeText(this@RegisterActivity, "Fingerprint registration failed: ${result.message}", Toast.LENGTH_LONG).show()
                        updateUIForCurrentStep()
                    }
                    is BiometricResult.Cancelled -> {
                        Toast.makeText(this@RegisterActivity, "Fingerprint registration cancelled", Toast.LENGTH_SHORT).show()
                        updateUIForCurrentStep()
                    }
                    is BiometricResult.Failed -> {
                        Toast.makeText(this@RegisterActivity, "Fingerprint not recognized. Please try again.", Toast.LENGTH_SHORT).show()
                        updateUIForCurrentStep()
                    }
                }
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        showLoading(false)
        
        if (requestCode == REQUEST_FACE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                faceRegistered = true
                Toast.makeText(this, "Face registered successfully!", Toast.LENGTH_SHORT).show()
                proceedToNextStep()
            } else {
                Toast.makeText(this, "Face registration cancelled or failed", Toast.LENGTH_SHORT).show()
                updateUIForCurrentStep()
            }
        }
    }
    
    private fun proceedToNextStep() {
        when (currentStep) {
            RegistrationStep.FACE -> {
                currentStep = RegistrationStep.FINGERPRINT
                updateUIForCurrentStep()
            }
            RegistrationStep.FINGERPRINT -> {
                currentStep = RegistrationStep.COMPLETE
                completeRegistration()
            }
            RegistrationStep.COMPLETE -> {
                // Already complete
            }
        }
    }
    
    private fun updateUIForCurrentStep() {
        when (currentStep) {
            RegistrationStep.FACE -> {
                tvInstructions.text = "Step 1: Register your face for attendance"
                btnScanFaceID.isEnabled = true
                btnScanFaceID.backgroundTintList = getColorStateList(R.color.colorPrimary)
                btnScanFaceID.setTextColor(getColor(android.R.color.white))
                
                btnScanFingerprintID.isEnabled = false
                btnScanFingerprintID.backgroundTintList = getColorStateList(android.R.color.darker_gray)
                btnScanFingerprintID.setTextColor(getColor(android.R.color.black))
                
                updateProgress(25)
            }
            RegistrationStep.FINGERPRINT -> {
                tvInstructions.text = "Step 2: Register your fingerprint for attendance"
                btnScanFaceID.isEnabled = false
                btnScanFaceID.backgroundTintList = getColorStateList(android.R.color.darker_gray)
                btnScanFaceID.setTextColor(getColor(android.R.color.black))
                btnScanFaceID.text = "✓ Face Registered"
                
                btnScanFingerprintID.isEnabled = true
                btnScanFingerprintID.backgroundTintList = getColorStateList(R.color.colorPrimary)
                btnScanFingerprintID.setTextColor(getColor(android.R.color.white))
                
                updateProgress(50)
            }
            RegistrationStep.COMPLETE -> {
                tvInstructions.text = "Registration complete! Redirecting to dashboard..."
                btnScanFaceID.text = "✓ Face Registered"
                btnScanFingerprintID.text = "✓ Fingerprint Registered"
                
                updateProgress(100)
            }
        }
    }
    
    private fun completeRegistration() {
        showLoading(true, "Completing registration...")
        
        lifecycleScope.launch {
            employeeId?.let { id ->
                val result = biometricRepository.registerBiometrics(
                    employeeId = id,
                    faceRegistered = faceRegistered,
                    fingerprintRegistered = fingerprintRegistered
                )
                
                runOnUiThread {
                    showLoading(false)
                    
                    when (result) {
                        is Result.Success<*> -> {
                            Toast.makeText(this@RegisterActivity, "Biometric registration completed successfully!", Toast.LENGTH_SHORT).show()
                            redirectToDashboard()
                        }
                        is Result.Error -> {
                            Toast.makeText(this@RegisterActivity, "Registration failed: ${result.message}", Toast.LENGTH_LONG).show()
                            updateUIForCurrentStep()
                        }
                    }
                }
            }
        }
    }
    
    private fun showLoading(show: Boolean, message: String = "Loading...") {
        if (show) {
            progressIndicator.visibility = View.VISIBLE
            tvProgressText.text = message
            tvProgressText.visibility = View.VISIBLE
            btnScanFaceID.isEnabled = false
            btnScanFingerprintID.isEnabled = false
        } else {
            progressIndicator.visibility = View.GONE
            tvProgressText.visibility = View.GONE
            updateUIForCurrentStep()
        }
    }
    
    private fun updateProgress(progress: Int) {
        tvProgressText.text = "Registration Progress: $progress%"
    }
    
    private fun redirectToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
