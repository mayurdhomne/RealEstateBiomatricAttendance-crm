package com.crm.realestate.activity

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import com.crm.realestate.utils.BaseErrorHandlingActivity
import com.crm.realestate.utils.ErrorHandler
import com.crm.realestate.utils.LoadingStateManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.crm.realestate.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FacescanActivity : BaseErrorHandlingActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var scanningText: TextView
    private lateinit var instructionText: TextView
    private lateinit var faceDetector: FaceDetector
    private var progress = 0
    private var isRegistrationMode = false
    private var faceDetectionCount = 0
    private var consecutiveDetections = 0
    private var registrationQualityScore = 0f
    private var isProcessingComplete = false
    private var registrationStartTime = 0L
    
    companion object {
        const val EXTRA_REGISTRATION_MODE = "REGISTRATION_MODE"
        const val EXTRA_REGISTRATION_SUCCESS = "REGISTRATION_SUCCESS"
        const val EXTRA_REGISTRATION_MESSAGE = "REGISTRATION_MESSAGE"
        const val EXTRA_QUALITY_SCORE = "QUALITY_SCORE"
        
        private const val MIN_FACE_SIZE = 0.15f // Reduced minimum face size for easier detection
        private const val MIN_DETECTION_COUNT = 20 // Reduced minimum successful detections
        private const val MIN_CONSECUTIVE_DETECTIONS = 5 // Reduced consecutive detections
        private const val REQUIRED_QUALITY_SCORE = 0.6f // Lowered quality score for easier registration
        private const val REGISTRATION_TIMEOUT_MS = 90000L // 90 seconds timeout for registration
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_facescan)

        initializeViews()
        cameraExecutor = Executors.newSingleThreadExecutor()
        initializeFaceDetector()
        
        // Check if this is registration mode
        isRegistrationMode = intent.getBooleanExtra(EXTRA_REGISTRATION_MODE, false)
        
        // Update UI for registration mode
        if (isRegistrationMode) {
            setupRegistrationUI()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }
    
    private fun initializeViews() {
        progressIndicator = findViewById(R.id.scanningProgress)
        progressText = findViewById(R.id.progressText)
        scanningText = findViewById(R.id.scanningText)
        instructionText = findViewById(R.id.instructionText)
    }
    
    private fun setupRegistrationUI() {
        scanningText.text = "Face Registration"
        instructionText.text = "Position your face in the frame and hold still for registration"
        progressIndicator.max = 100
        registrationStartTime = System.currentTimeMillis()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                showPermissionDeniedDialog()
            }
        }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            showConfirmation(
                "Camera Permission Needed",
                "This app requires camera access to scan your face.",
                onConfirm = { requestPermissionLauncher.launch(Manifest.permission.CAMERA) },
                onCancel = { finish() }
            )
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showPermissionDeniedDialog() {
        val error = ErrorHandler.AppError.UnknownError("Camera permission is required for face scanning").apply {
            requiresUserAction = true
            userActionIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }
        
        handleError(error, onRetry = { finish() })
    }

    private fun initializeFaceDetector() {
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE) // Change from ALL to NONE to avoid unknown landmark errors
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(MIN_FACE_SIZE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE) // Disable contours for better performance
                .build()

            faceDetector = FaceDetection.getClient(options)
            Log.d("FaceScan", "Face detector initialized with optimized settings")
        } catch (e: Exception) {
            Log.e("FaceScan", "Failed to initialize face detector", e)
            // Fallback to basic detector
            val basicOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(MIN_FACE_SIZE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .build()
            faceDetector = FaceDetection.getClient(basicOptions)
        }
    }

    private fun startCamera() {
        showLoading("Initializing camera...", operationType = LoadingStateManager.OperationType.GENERAL)
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val cameraPreview: PreviewView = findViewById(R.id.cameraPreview)
                
                // Configure camera preview with explicit resolution
                cameraPreview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                
                val preview = Preview.Builder()
                    .setTargetResolution(android.util.Size(640, 480)) // Set explicit resolution
                    .build()
                    .also {
                        it.surfaceProvider = cameraPreview.surfaceProvider
                    }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480)) // Match preview resolution
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FaceAnalyzer())
                    }

                // Try to use the front camera, fallback to back camera if needed
                val cameraSelector = try {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } catch (e: Exception) {
                    Log.w("FaceScan", "Front camera not available, using back camera", e)
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                try {
                    // Must unbind before binding
                    cameraProvider.unbindAll()
                    
                    // Bind camera to lifecycle
                    val camera = cameraProvider.bindToLifecycle(
                        this, 
                        cameraSelector, 
                        preview, 
                        imageAnalyzer
                    )
                    
                    // Enable torch mode if available (for low light conditions)
                    if (camera.cameraInfo.hasFlashUnit()) {
                        camera.cameraControl.enableTorch(false) // Start with torch off
                    }
                    
                    Log.d("FaceScan", "Camera successfully bound to lifecycle")
                } catch (e: Exception) {
                    Log.e("FaceScan", "Use case binding failed: ${e.message}", e)
                    throw e
                }
                
                hideLoading()
                
            } catch (exc: Exception) {
                hideLoading()
                Log.e("FaceScan", "Camera setup failed", exc)
                handleHardwareUnavailable(
                    "camera",
                    alternativeAction = { finish() },
                    onDismiss = { finish() }
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            if (isProcessingComplete) {
                imageProxy.close()
                return
            }
            
            val mediaImage = imageProxy.image ?: return
            
            // Log image details for debugging
            Log.d("FaceScan", "Processing image: ${mediaImage.width}x${mediaImage.height}, " +
                              "rotation: ${imageProxy.imageInfo.rotationDegrees}")
            
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    Log.d("FaceScan", "Face detection completed. Found ${faces.size} faces")
                    if (isRegistrationMode) {
                        processRegistrationFaces(faces, mediaImage.width, mediaImage.height)
                    } else {
                        processLegacyFaces(faces)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FaceScan", "Face detection failed", e)
                    if (isRegistrationMode) {
                        val error = ErrorHandler.AppError.FaceDetectionFailed()
                        runOnUiThread {
                            handleBiometricError(error, "face", onRetry = {
                                // Reset detection state and continue
                                consecutiveDetections = 0
                                isProcessingComplete = false
                            })
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
        
        private fun processRegistrationFaces(faces: List<com.google.mlkit.vision.face.Face>, imageWidth: Int, imageHeight: Int) {
            // Check for timeout
            if (System.currentTimeMillis() - registrationStartTime > REGISTRATION_TIMEOUT_MS) {
                runOnUiThread {
                    handleRegistrationError("Registration timeout. Please try again.")
                }
                return
            }
            
            if (faces.isEmpty()) {
                consecutiveDetections = 0
                runOnUiThread {
                    updateRegistrationFeedback("No face detected", "Please position your face in the frame")
                }
                return
            }
            
            if (faces.size > 1) {
                consecutiveDetections = 0
                runOnUiThread {
                    updateRegistrationFeedback("Multiple faces detected", "Please ensure only one person is in the frame")
                }
                return
            }
            
            val face = faces[0] // Use the first detected face
            val qualityScore = calculateFaceQuality(face, imageWidth, imageHeight)
            
            // Log face detection details for debugging
            Log.d("FaceReg", "Face detected - Quality: ${String.format("%.2f", qualityScore)}, " +
                    "Size: ${face.boundingBox.width()}x${face.boundingBox.height()}, " +
                    "Count: $faceDetectionCount/$MIN_DETECTION_COUNT")
            
            if (qualityScore >= REQUIRED_QUALITY_SCORE) {
                faceDetectionCount++
                consecutiveDetections++
                registrationQualityScore = if (registrationQualityScore == 0f) {
                    qualityScore
                } else {
                    (registrationQualityScore + qualityScore) / 2f
                }
                
                val progressValue = (faceDetectionCount * 100) / MIN_DETECTION_COUNT
                val clampedProgress = minOf(progressValue, 100)
                
                runOnUiThread {
                    updateProgress(clampedProgress)
                    updateRegistrationFeedback(
                        "Good face detection (${String.format("%.0f", qualityScore * 100)}%)",
                        "Hold still... ${faceDetectionCount}/${MIN_DETECTION_COUNT} captures - Keep your face centered"
                    )
                }
                
                if (faceDetectionCount >= MIN_DETECTION_COUNT && consecutiveDetections >= MIN_CONSECUTIVE_DETECTIONS) {
                    isProcessingComplete = true
                    Log.d("FaceReg", "Registration complete - Final quality: ${String.format("%.2f", registrationQualityScore)}")
                    runOnUiThread {
                        handleRegistrationSuccess()
                    }
                }
            } else {
                consecutiveDetections = 0
                val qualityPercentage = String.format("%.0f", qualityScore * 100)
                runOnUiThread {
                    updateRegistrationFeedback(
                        "Face quality: $qualityPercentage% (need ${String.format("%.0f", REQUIRED_QUALITY_SCORE * 100)}%+)",
                        "Move closer to camera, improve lighting, face camera directly, and stay centered"
                    )
                }
            }
        }
        
        private fun processLegacyFaces(faces: List<com.google.mlkit.vision.face.Face>) {
            if (faces.isNotEmpty() && progress < 100) {
                progress += 10
                if (progress > 100) progress = 100
                updateProgress(progress)
            }
        }
        
        private fun calculateFaceQuality(face: com.google.mlkit.vision.face.Face, imageWidth: Int, imageHeight: Int): Float {
            var qualityScore = 0f
            
            // Check face size (should be at least 15% of image) - reduced requirement
            val faceArea = face.boundingBox.width() * face.boundingBox.height()
            val imageArea = imageWidth * imageHeight
            val faceSizeRatio = faceArea.toFloat() / imageArea.toFloat()
            
            // Improved face size scoring - give more weight to this reliable metric
            if (faceSizeRatio >= MIN_FACE_SIZE) {
                qualityScore += 0.5f // Increased weight for face size
            } else {
                // Partial credit for smaller faces
                qualityScore += (faceSizeRatio / MIN_FACE_SIZE) * 0.5f
            }
            
            // Check if face is centered - more lenient
            val faceCenterX = face.boundingBox.centerX()
            val faceCenterY = face.boundingBox.centerY()
            val imageCenterX = imageWidth / 2
            val imageCenterY = imageHeight / 2
            
            val centerDistance = kotlin.math.sqrt(
                ((faceCenterX - imageCenterX) * (faceCenterX - imageCenterX) + 
                 (faceCenterY - imageCenterY) * (faceCenterY - imageCenterY)).toDouble()
            ).toFloat()
            
            val maxDistance = kotlin.math.sqrt((imageWidth * imageWidth + imageHeight * imageHeight).toDouble()).toFloat() / 3 // More lenient
            val centerScore = kotlin.math.max(0f, 1f - (centerDistance / maxDistance))
            qualityScore += centerScore * 0.4f // Increased weight
            
            // Check head rotation (Euler angles) if available - but don't depend on it
            face.headEulerAngleY?.let { yaw ->
                // Much more lenient yaw tolerance
                val yawScore = kotlin.math.max(0f, 1f - (kotlin.math.abs(yaw) / 45f))
                qualityScore += yawScore * 0.1f
            }
            
            // Since we're not using landmarks, compensate with a base score
            // This ensures even with minimal features, we can get a good enough score
            qualityScore += 0.2f
            
            // Cap the quality score at 1.0
            return kotlin.math.min(qualityScore, 1.0f)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateProgress(value: Int) {
        val animation = ObjectAnimator.ofInt(progressIndicator, "progress", progressIndicator.progress, value)
        animation.duration = 500 // Smooth animation
        animation.start()
        progressText.text = "$value%"

        if (value == 100 && !isRegistrationMode) {
            showSuccessBottomSheet()
        }
    }
    
    private fun updateRegistrationFeedback(status: String, instruction: String) {
        scanningText.text = status
        instructionText.text = instruction
    }
    
    private fun handleRegistrationSuccess() {
        updateRegistrationFeedback("Registration Complete", "Face registered successfully!")
        
        // Show success animation
        animateSuccess(progressIndicator) {
            val animation = ObjectAnimator.ofInt(progressIndicator, "progress", progressIndicator.progress, 100)
            animation.duration = 500
            animation.start()
            progressText.text = "100%"
            
            // Delay to show completion, then return result
            progressIndicator.postDelayed({
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_REGISTRATION_SUCCESS, true)
                    putExtra(EXTRA_REGISTRATION_MESSAGE, "Face registered successfully with quality score: ${String.format("%.2f", registrationQualityScore)}")
                    putExtra(EXTRA_QUALITY_SCORE, registrationQualityScore)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }, 1500)
        }
    }
    
    private fun handleRegistrationError(errorMessage: String) {
        isProcessingComplete = true
        updateRegistrationFeedback("Registration Failed", errorMessage)
        
        // Show error animation
        animateError(progressIndicator) {
            // Delay to show error, then return failure result
            progressIndicator.postDelayed({
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_REGISTRATION_SUCCESS, false)
                    putExtra(EXTRA_REGISTRATION_MESSAGE, errorMessage)
                    putExtra(EXTRA_QUALITY_SCORE, 0f)
                }
                setResult(Activity.RESULT_CANCELED, resultIntent)
                finish()
            }, 2000)
        }
    }

    private fun showSuccessBottomSheet() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_success, null)
        val bottomSheetDialog = BottomSheetDialog(this, R.style.BottomSheetTheme)
        bottomSheetDialog.setContentView(dialogView)

        val btnGoToDashboard = dialogView.findViewById<Button>(R.id.btnGoToDashboard)
        
        // Update button text based on mode
        if (isRegistrationMode) {
            btnGoToDashboard.text = "Continue Registration"
        }
        
        btnGoToDashboard.setOnClickListener {
            bottomSheetDialog.dismiss()

            if (isRegistrationMode) {
                // For registration mode, return success with quality data
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_REGISTRATION_SUCCESS, true)
                    putExtra(EXTRA_REGISTRATION_MESSAGE, "Face registered successfully")
                    putExtra(EXTRA_QUALITY_SCORE, registrationQualityScore)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                // For legacy mode, return form data
                val resultIntent = Intent()
                resultIntent.putExtra("FULL_NAME", intent.getStringExtra("FULL_NAME"))
                resultIntent.putExtra("EMAIL", intent.getStringExtra("EMAIL"))
                resultIntent.putExtra("PASSWORD", intent.getStringExtra("PASSWORD"))
                resultIntent.putExtra("CONFIRM_PASSWORD", intent.getStringExtra("CONFIRM_PASSWORD"))

                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }

        bottomSheetDialog.show()
    }


    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (isRegistrationMode && !isProcessingComplete) {
            // Show confirmation dialog for registration mode
            showConfirmation(
                "Cancel Registration?",
                "Are you sure you want to cancel face registration? You'll need to start over.",
                onConfirm = {
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_REGISTRATION_SUCCESS, false)
                        putExtra(EXTRA_REGISTRATION_MESSAGE, "Registration cancelled by user")
                        putExtra(EXTRA_QUALITY_SCORE, 0f)
                    }
                    setResult(Activity.RESULT_CANCELED, resultIntent)
                    super.onBackPressed()
                }
            )
        } else {
            super.onBackPressed()
        }
    }
    
    override fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceDetector.close()
    }
}
