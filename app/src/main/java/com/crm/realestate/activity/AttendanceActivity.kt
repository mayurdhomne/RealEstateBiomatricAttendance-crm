package com.crm.realestate.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.crm.realestate.R
import com.crm.realestate.data.api.AttendanceApiService
import com.crm.realestate.data.repository.AttendanceRepository
import com.crm.realestate.network.ApiConfig
import com.crm.realestate.utils.BaseErrorHandlingActivity
import com.crm.realestate.utils.ErrorHandler
import com.crm.realestate.utils.LoadingStateManager
import com.crm.realestate.viewmodel.AttendanceViewModel
import com.google.android.gms.location.*
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

/**
 * Enhanced AttendanceActivity with comprehensive validations:
 * - Biometric authentication with retry limits (3-5 attempts)
 * - Location capture without office boundary validation
 * - Time rules for check-in/check-out prevention
 * - GPS requirement validation
 */
class AttendanceActivity : BaseErrorHandlingActivity() {

    companion object {
        private const val FACE_SCAN_REQUEST_CODE = 1002
        private const val MAX_BIOMETRIC_RETRIES = 3
        private const val LOCATION_UPDATE_INTERVAL = 10000L // 10 seconds
        private const val LOCATION_FASTEST_INTERVAL = 5000L // 5 seconds
    }

    private lateinit var viewModel: AttendanceViewModel
    private lateinit var repository: AttendanceRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var executor: Executor
    private lateinit var locationCallback: LocationCallback

    // UI Components
    private lateinit var btnBack: ImageView
    private lateinit var tvAttendanceStatus: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvCurrentDate: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var tvLocationInfo: TextView
    private lateinit var cardFaceOption: CardView
    private lateinit var cardFingerprintOption: CardView
    private lateinit var tvFaceStatus: TextView
    private lateinit var tvFingerprintStatus: TextView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var tvLoadingMessage: TextView

    // Attendance State
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    private var locationAccuracy: Float = 0f
    private var biometricType: String = ""
    private var isOfflineMode: Boolean = false
    private var attendanceType: String = "check_in" // Will be set from intent
    private var enableFallback: Boolean = false // Whether to enable fallback to fingerprint
    private var biometricRetryCount = 0
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        // Get intent extras
        biometricType = intent.getStringExtra("biometric_type") ?: ""
        attendanceType = intent.getStringExtra("attendance_type") ?: "check_in"
        isOfflineMode = intent.getBooleanExtra("offline_mode", false)
        enableFallback = intent.getBooleanExtra("enable_fallback", false)

        initializeComponents()
        setupViewModel()
        setupObservers()
        setupClickListeners()
        setupBiometric()
        setupLocation()
        
        // Update UI based on attendance type
        updateAttendanceTypeUI()
        
        // Start time updates
        updateCurrentTime()
        
        // Validate GPS is enabled
        validateGpsEnabled()
    }

    /**
     * Initialize UI components
     */
    private fun initializeComponents() {
        btnBack = findViewById(R.id.btnBack)
        tvAttendanceStatus = findViewById(R.id.tvAttendanceStatus)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvCurrentDate = findViewById(R.id.tvCurrentDate)
        tvInstructions = findViewById(R.id.tvInstructions)
        cardFaceOption = findViewById(R.id.cardFaceOption)
        cardFingerprintOption = findViewById(R.id.cardFingerprintOption)
        tvFaceStatus = findViewById(R.id.tvFaceStatus)
        tvFingerprintStatus = findViewById(R.id.tvFingerprintStatus)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        progressIndicator = findViewById(R.id.progressIndicator)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)

        // Add location info TextView (you might need to add this to the layout)
        try {
            tvLocationInfo = findViewById(R.id.tvLocationInfo)
        } catch (e: Exception) {
            // If not found, create it programmatically or handle gracefully
            tvLocationInfo = TextView(this)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
    }

    /**
     * Setup location callback for continuous updates
     */
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                result.lastLocation?.let { location ->
                    updateLocationInfo(location)
                }
            }
        }
    }

    /**
     * Setup ViewModel with repository
     */
    private fun setupViewModel() {
        try {
            val retrofit = ApiConfig.provideRetrofit(this) { 
                redirectToLogin()
            }
            val apiService = retrofit.create(AttendanceApiService::class.java)
            val tokenManager = ApiConfig.provideTokenManager(this)
            val database = com.crm.realestate.data.database.AttendanceDatabase.getDatabase(this)
            
            repository = AttendanceRepository(
                apiService = apiService,
                offlineAttendanceDao = database.offlineAttendanceDao(),
                attendanceCacheDao = database.attendanceCacheDao(),
                tokenManager = tokenManager
            )
            
            viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return AttendanceViewModel(repository) as T
                }
            })[AttendanceViewModel::class.java]
        } catch (e: Exception) {
            val error = ErrorHandler.handleException(this, e)
            handleError(error, onRetry = { recreate() })
        }
    }

    /**
     * Setup observers for ViewModel LiveData
     */
    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                showLoadingOverlay("Processing attendance...")
            } else {
                hideLoadingOverlay()
            }
        }

        viewModel.attendanceResult.observe(this) { result ->
            result?.let {
                showSuccess("${if (attendanceType == "check_in") "Check-in" else "Check-out"} successful: ${it.message}") {
                    finish()
                }
                viewModel.clearAttendanceResult()
            }
        }

        viewModel.error.observe(this) { errorMessage ->
            errorMessage?.let {
                val error = ErrorHandler.AppError.UnknownError(it)
                handleError(error, onRetry = { 
                    // On error, allow retry
                })
                viewModel.clearError()
            }
        }
    }
    
    /**
     * Update UI based on attendance type from intent
     */
    private fun updateAttendanceTypeUI() {
        if (attendanceType == "check_out") {
            tvAttendanceStatus.text = "Ready to Check Out"
            tvAttendanceStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red))
            tvInstructions.text = "Select your preferred biometric method to check out"
        } else {
            tvAttendanceStatus.text = "Ready to Check In"
            tvAttendanceStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green))
            tvInstructions.text = "Select your preferred biometric method to check in"
        }
        
        // Auto-start biometric if specified
        if (biometricType.isNotEmpty()) {
            when (biometricType) {
                "face" -> startFaceAttendance()
                "fingerprint" -> startFingerprintAttendance()
            }
        }
    }

    /**
     * Setup click listeners
     */
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        cardFaceOption.setOnClickListener {
            startFaceAttendance()
        }

        cardFingerprintOption.setOnClickListener {
            startFingerprintAttendance()
        }
    }

    /**
     * Setup enhanced biometric authentication with retry limits
     */
    private fun setupBiometric() {
        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this as FragmentActivity,
            executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            val error = ErrorHandler.AppError.ValidationError("Biometric", "Authentication cancelled")
                            handleError(error)
                        }
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            val error = ErrorHandler.AppError.BiometricAuthenticationFailed()
                            handleError(error)
                            finish()
                        }
                        else -> {
                            biometricRetryCount++
                            if (biometricRetryCount < MAX_BIOMETRIC_RETRIES) {
                                val error = ErrorHandler.AppError.BiometricAuthenticationFailed()
                                handleError(error)
                                // Allow user to retry
                            } else {
                                val error = ErrorHandler.AppError.BiometricAuthenticationFailed()
                                handleError(error)
                                finish()
                            }
                        }
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    biometricRetryCount = 0 // Reset retry count on success
                    // Use "finger" for the API as per API requirements
                    continueWithAttendanceProcess("finger", actualBiometricType = "fingerprint")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    biometricRetryCount++
                    
                    if (biometricRetryCount >= MAX_BIOMETRIC_RETRIES) {
                        val error = ErrorHandler.AppError.BiometricAuthenticationFailed()
                        handleError(error)
                        finish()
                    } else {
                        val error = ErrorHandler.AppError.BiometricAuthenticationFailed()
                        handleError(error)
                        // BiometricPrompt will allow retry automatically
                    }
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Attendance - ${if (attendanceType == "check_in") "Check In" else "Check Out"}")
            .setSubtitle("Use your fingerprint to record attendance")
            .setNegativeButtonText("Cancel")
            .build()
    }

    /**
     * Setup location services with GPS validation
     */
    private fun setupLocation() {
        if (!isGpsEnabled()) {
            showGpsRequiredDialog()
            return
        }
        
        requestLocationPermissions()
    }

    /**
     * Validate GPS is enabled
     */
    private fun validateGpsEnabled() {
        if (!isGpsEnabled()) {
            showGpsRequiredDialog()
        }
    }

    /**
     * Check if GPS is enabled
     */
    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * Show GPS required dialog
     */
    private fun showGpsRequiredDialog() {
        showConfirmation(
            title = "GPS Required",
            message = "GPS must be turned ON to record attendance. Would you like to enable it?",
            onConfirm = {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            onCancel = {
                val error = ErrorHandler.AppError.ValidationError("GPS", "GPS is required for attendance recording")
                handleError(error)
                finish()
            }
        )
    }

    /**
     * Request location permissions
     */
    private fun requestLocationPermissions() {
        if (!hasLocationPermissions()) {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else {
            startLocationUpdates()
        }
    }

    /**
     * Check if location permissions are granted
     */
    private fun hasLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Location permission launcher
     */
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startLocationUpdates()
        } else {
            val error = ErrorHandler.AppError.ValidationError("Location", "Location permission is required for attendance recording")
            handleError(error)
            finish()
        }
    }

    /**
     * Start location updates
     */
    private fun startLocationUpdates() {
        if (!hasLocationPermissions()) {
            requestLocationPermissions()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            .setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            
            // Also get last known location immediately
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { 
                    updateLocationInfo(it) 
                } ?: run {
                    // If no last known location, show loading and set timeout
                    showLoadingOverlay("Getting your location...")
                    setLocationTimeout()
                }
            }.addOnFailureListener {
                // If getting last location fails, show loading and set timeout
                showLoadingOverlay("Getting your location...")
                setLocationTimeout()
            }
        } catch (e: SecurityException) {
            val error = ErrorHandler.AppError.ValidationError("Location", "Location permission denied")
            handleError(error)
        }
    }
    
    /**
     * Set timeout for location acquisition
     */
    private fun setLocationTimeout() {
        // Set 15-second timeout for location acquisition
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (currentLocation == null) {
                hideLoadingOverlay()
                showConfirmation(
                    title = "Location Timeout",
                    message = "Unable to get your location. Would you like to proceed with default location or try again?",
                    onConfirm = {
                        // Use default location (office location or 0,0)
                        userLatitude = 0.0
                        userLongitude = 0.0
                        locationAccuracy = 0f
                        currentLocation = android.location.Location("default").apply {
                            latitude = userLatitude
                            longitude = userLongitude
                            accuracy = locationAccuracy
                        }
                        updateLocationInfo(currentLocation!!)
                    },
                    onCancel = {
                        // Try again
                        startLocationUpdates()
                    }
                )
            }
        }, 15000) // 15 seconds timeout
    }

    /**
     * Update location information
     */
    private fun updateLocationInfo(location: Location) {
        currentLocation = location
        userLatitude = location.latitude
        userLongitude = location.longitude
        locationAccuracy = location.accuracy
        
        // Hide loading overlay once location is obtained
        hideLoadingOverlay()
        
        // Update UI with location info
        try {
            tvLocationInfo.text = "Location: ${String.format("%.6f", userLatitude)}, ${String.format("%.6f", userLongitude)}\nAccuracy: ${String.format("%.1f", locationAccuracy)}m"
            tvLocationInfo.visibility = View.VISIBLE
        } catch (e: Exception) {
            // Handle case where tvLocationInfo is not in layout
        }
        
        // Auto-start biometric if location was being waited for
        if (biometricType.isNotEmpty()) {
            when (biometricType) {
                "face" -> startFaceAttendance()
                "fingerprint" -> startFingerprintAttendance()
            }
        }
    }

    /**
     * Start face attendance process with enhanced validation
     */
    private fun startFaceAttendance() {
        // Validate GPS first
        if (!isGpsEnabled()) {
            showGpsRequiredDialog()
            return
        }

        // Validate location
        if (currentLocation == null) {
            showLoadingOverlay("Getting your location...")
            startLocationUpdates()
            return
        }

        // Hide location loading and proceed with face scan
        hideLoadingOverlay()
        
        checkBiometricAvailability { canUseFace ->
            if (canUseFace) {
                val intent = Intent(this, FacescanActivity::class.java)
                intent.putExtra("attendance_mode", true)
                intent.putExtra("attendance_type", attendanceType)
                startActivityForResult(intent, FACE_SCAN_REQUEST_CODE)
            } else {
                handleHardwareUnavailable("camera")
            }
        }
    }

    /**
     * Start fingerprint attendance process with enhanced validation
     */
    private fun startFingerprintAttendance() {
        // Validate GPS first
        if (!isGpsEnabled()) {
            showGpsRequiredDialog()
            return
        }

        // Validate location
        if (currentLocation == null) {
            showLoadingOverlay("Getting your location...")
            startLocationUpdates()
            return
        }

        // Hide location loading and proceed with fingerprint
        hideLoadingOverlay()

        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                val error = ErrorHandler.AppError.BiometricUnavailable()
                handleError(error)
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                val error = ErrorHandler.AppError.BiometricUnavailable()
                handleError(error)
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                val error = ErrorHandler.AppError.BiometricNotEnrolled(this)
                handleError(error)
            }
        }
    }

    /**
     * Check biometric availability
     */
    private fun checkBiometricAvailability(callback: (Boolean) -> Unit) {
        // For face detection, we assume it's available if camera is available
        callback(packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT))
    }

    /**
     * Continue with attendance process after biometric verification
     * @param apiScanType The scan type to send to API (either "face" or "finger")
     * @param actualBiometricType The actual biometric method used (for UI/logging)
     */
    private fun continueWithAttendanceProcess(apiScanType: String, actualBiometricType: String) {
        // Final validation before processing
        if (!isGpsEnabled()) {
            val error = ErrorHandler.AppError.ValidationError("GPS", "GPS must be enabled to record attendance")
            handleError(error)
            return
        }

        if (currentLocation == null) {
            val error = ErrorHandler.AppError.ValidationError("Location", "Location not available. Please ensure GPS is enabled and try again.")
            handleError(error)
            return
        }

        showLoadingOverlay("Recording ${if (attendanceType == "check_in") "check-in" else "check-out"}...")
        processAttendance(apiScanType, actualBiometricType)
    }

    /**
     * Process attendance with comprehensive validation
     * @param apiScanType The scan type to send to API
     * @param actualBiometricType The actual biometric method used (for UI/logging)
     */
    private fun processAttendance(apiScanType: String, actualBiometricType: String) {
        // Validate duplicate prevention (this is handled in the repository)
        // The repository will check for recent punches and prevent duplicates
        
        // Log the actual biometric method used for internal tracking
        Toast.makeText(
            this,
            "Recording ${if (attendanceType == "check_in") "check-in" else "check-out"} using $actualBiometricType authentication",
            Toast.LENGTH_SHORT
        ).show()
        
        // Record attendance with location and timestamp
        viewModel.recordAttendance(
            attendanceType = attendanceType,
            latitude = userLatitude,
            longitude = userLongitude,
            scanType = apiScanType,
            isOfflineMode = isOfflineMode
        )
    }

    /**
     * Handle activity results
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            FACE_SCAN_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    // Face scan successful, proceed with attendance
                    continueWithAttendanceProcess("face", actualBiometricType = "face")
                } else {
                    // Face scan failed, show fallback option if enabled
                    if (enableFallback) {
                        showFaceFailureFallbackDialog()
                    } else {
                        val error = ErrorHandler.AppError.FaceDetectionFailed()
                        handleError(error)
                    }
                }
            }
        }
    }
    
    /**
     * Show dialog offering fingerprint fallback when face scan fails
     */
    private fun showFaceFailureFallbackDialog() {
        showConfirmation(
            title = "Face Scan Failed",
            message = "Face recognition was unsuccessful. Would you like to use fingerprint authentication instead?",
            onConfirm = {
                // Start fingerprint authentication as fallback
                startFingerprintAttendance()
            },
            onCancel = {
                // User cancelled, finish activity
                finish()
            }
        )
    }

    /**
     * Update current time display
     */
    private fun updateCurrentTime() {
        val currentTime = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        
        tvCurrentTime.text = timeFormat.format(currentTime.time)
        tvCurrentDate.text = dateFormat.format(currentTime.time)
        
        // Update every minute
        tvCurrentTime.postDelayed({ updateCurrentTime() }, 60000)
    }

    /**
     * Show loading overlay
     */
    private fun showLoadingOverlay(message: String) {
        tvLoadingMessage.text = message
        loadingOverlay.visibility = View.VISIBLE
    }

    /**
     * Hide loading overlay
     */
    private fun hideLoadingOverlay() {
        loadingOverlay.visibility = View.GONE
    }

    override fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    /**
     * Handle hardware unavailable error
     */
    private fun handleHardwareUnavailable(hardwareType: String) {
        val message = when (hardwareType) {
            "camera" -> "Camera is not available for face recognition"
            "fingerprint" -> "Fingerprint sensor is not available"
            else -> "Required hardware is not available"
        }
        val error = ErrorHandler.AppError.ValidationError("Hardware", message)
        handleError(error)
    }

    override fun onResume() {
        super.onResume()
        // Re-validate GPS when returning to activity
        if (!isGpsEnabled()) {
            showGpsRequiredDialog()
        } else if (hasLocationPermissions()) {
            if (currentLocation == null) {
                startLocationUpdates()
            }
        } else {
            requestLocationPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop location updates to save battery
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Handle gracefully
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up location updates
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Handle gracefully
        }
    }
}