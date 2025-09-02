package com.crm.realestate.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.crm.realestate.R
import com.crm.realestate.data.models.LoginResponse
import com.crm.realestate.databinding.ActivityLoginBinding
import com.crm.realestate.utils.BaseErrorHandlingActivity
import com.crm.realestate.utils.ErrorHandler
import com.crm.realestate.utils.LoadingStateManager
import com.crm.realestate.utils.Result
import com.crm.realestate.viewmodel.AuthViewModel
import com.crm.realestate.viewmodel.AuthViewModelFactory
import kotlinx.coroutines.launch

class LoginActivity : BaseErrorHandlingActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: AuthViewModel by viewModels { AuthViewModelFactory(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        observeViewModel()
        checkExistingLogin()
    }
    
    private fun setupUI() {
        // Set up login button click listener
        binding.btnLogin.setOnClickListener {
            performLogin()
        }
        
        // Remove create account functionality for employee app
        binding.tvCreateAccountLink.visibility = View.GONE
        binding.tvCreateAccount.visibility = View.GONE
        
        // Remove forgot password for now (can be added later if needed)
        binding.tvForgotPassword.visibility = View.GONE
        
        // Update welcome text for employee context
        binding.tvWelcome.text = "Enter your employee credentials"
    }
    
    private fun observeViewModel() {
        // Observe login state with comprehensive error handling
        lifecycleScope.launch {
            authViewModel.loginState.collect { result ->
                result?.let {
                    handleResult(
                        result = it,
                        onSuccess = { loginResponse ->
                            handleLoginSuccess(loginResponse)
                        },
                        onError = { appError ->
                            handleLoginError(appError)
                        },
                        showLoading = false, // We handle loading separately
                        anchorView = binding.root
                    )
                }
            }
        }
        
        // Observe loading state
        lifecycleScope.launch {
            authViewModel.isLoading.collect { isLoading ->
                if (isLoading) {
                    showLoading("Authenticating...", false, LoadingStateManager.OperationType.AUTHENTICATION)
                } else {
                    hideLoading()
                }
                updateUILoadingState(isLoading)
            }
        }
        
        // Observe validation errors
        lifecycleScope.launch {
            authViewModel.validationError.collect { validationError ->
                validationError?.let {
                    handleValidationError(it)
                    authViewModel.clearErrors()
                }
            }
        }
    }
    
    private fun checkExistingLogin() {
        authViewModel.checkLoginStatus()
    }
    
    private fun performLogin() {
        val username = binding.etUsername.text.toString()
        val password = binding.etPassword.text.toString()
        
        // Clear any existing errors
        clearInputErrors()
        
        // Perform login
        attemptLogin(username, password)
    }

    private fun attemptLogin(username: String, password: String) {
        showLoading("Signing in...", operationType = LoadingStateManager.OperationType.NETWORK)

        lifecycleScope.launch {
            val result = authViewModel.login(username, password)
            handleResult<LoginResponse>(
                result = result,
                onSuccess = { response ->
                    redirectToNextScreen(response)
                },
                onError = { error ->
                    handleError(error, onRetry = { attemptLogin(username, password) })
                }
            )
        }
    }
    
    private fun redirectToNextScreen(loginResponse: LoginResponse) {
        // Show success message with animation
        val welcomeName = loginResponse.employeeInfo.fullName.ifBlank {
            loginResponse.username.ifBlank { loginResponse.employeeId }
        }
        showSuccess("Welcome $welcomeName!") {
            // Navigate based on biometric registration status
            if (loginResponse.biometricsRegistered) {
                // User has already registered biometrics, go to dashboard
                navigateToDashboard()
            } else {
                // User needs to register biometrics, go to biometric registration
                navigateToBiometricRegistration()
            }
            
            // Finish login activity
            finish()
        }
    }
    
    private fun handleLoginSuccess(loginResponse: com.crm.realestate.data.models.LoginResponse) {
        // Show success message with animation
        val welcomeName = loginResponse.employeeInfo.fullName.ifBlank {
            loginResponse.username.ifBlank { loginResponse.employeeId }
        }
        showSuccess("Welcome $welcomeName!") {
            // Navigate based on biometric registration status
            if (loginResponse.biometricsRegistered) {
                // User has already registered biometrics, go to dashboard
                navigateToDashboard()
            } else {
                // User needs to register biometrics, go to biometric registration
                navigateToBiometricRegistration()
            }
            
            // Finish login activity
            finish()
        }
    }
    
    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
    
    private fun navigateToBiometricRegistration() {
        // Navigate to BiometricRegistrationActivity for biometric setup
        val intent = Intent(this, BiometricRegistrationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
    
    private fun updateUILoadingState(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.etUsername.isEnabled = !isLoading
        binding.etPassword.isEnabled = !isLoading
        
        if (isLoading) {
            binding.btnLogin.text = "Logging in..."
        } else {
            binding.btnLogin.text = "Log In"
        }
    }
    
    private fun handleLoginError(appError: ErrorHandler.AppError) {
        when (appError) {
            is ErrorHandler.AppError.UnauthorizedError -> {
                // Show specific error for invalid credentials
                animateError(binding.btnLogin) {
                    feedbackManager.showError(
                        ErrorHandler.AppError.ValidationError("Credentials", "Invalid username or password"),
                        binding.root,
                        onRetry = { performLogin() }
                    )
                }
            }
            is ErrorHandler.AppError.NetworkUnavailable,
            is ErrorHandler.AppError.NetworkTimeout,
            is ErrorHandler.AppError.ServerError -> {
                // Show network error with retry option
                feedbackManager.showNetworkError(
                    appError,
                    binding.root,
                    onRetry = { performLogin() }
                )
            }
            else -> {
                // Show generic error with retry option
                feedbackManager.showError(
                    appError,
                    binding.root,
                    onRetry = { performLogin() }
                )
            }
        }
    }
    
    private fun handleValidationError(message: String) {
        when {
            message.contains("Username", ignoreCase = true) -> {
                binding.tilUsername.error = message
                animateError(binding.tilUsername)
            }
            message.contains("Password", ignoreCase = true) -> {
                binding.tilPassword.error = message
                animateError(binding.tilPassword)
            }
            else -> {
                feedbackManager.showError(
                    ErrorHandler.AppError.ValidationError("Input", message),
                    binding.root
                )
            }
        }
    }
    
    private fun clearInputErrors() {
        binding.tilUsername.error = null
        binding.tilPassword.error = null
    }
    
    override fun redirectToLogin() {
        // Already in login activity, just clear any stored credentials
        authViewModel.logout()
    }
}