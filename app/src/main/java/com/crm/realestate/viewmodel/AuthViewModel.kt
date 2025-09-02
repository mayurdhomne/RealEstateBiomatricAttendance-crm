package com.crm.realestate.viewmodel

import androidx.lifecycle.viewModelScope
import com.crm.realestate.data.models.LoginResponse
import com.crm.realestate.data.repository.AuthRepository
import com.crm.realestate.utils.BaseErrorHandlingViewModel
import com.crm.realestate.utils.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val authRepository: AuthRepository) : BaseErrorHandlingViewModel() {
    
    private val _loginState = MutableStateFlow<Result<LoginResponse>?>(null)
    val loginState: StateFlow<Result<LoginResponse>?> = _loginState

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        setLoading(true)

        return try {
            // Validate inputs
            if (username.isBlank()) {
                _validationError.value = "Username is required"
                Result.Error(Exception("Username is required"))
            } else if (password.isBlank()) {
                _validationError.value = "Password is required"
                Result.Error(Exception("Password is required"))
            } else {
                // Convert repository result to utils.Result
                val result = authRepository.login(username, password)
                when (result) {
                    is com.crm.realestate.data.models.Result.Success ->
                        Result.Success(result.data)
                    is com.crm.realestate.data.models.Result.Error ->
                        Result.Error(result.exception)
                    is com.crm.realestate.data.models.Result.Loading ->
                        Result.Loading(result.loading, result.loadingMessage ?: "Loading...")
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        } finally {
            setLoading(false)
        }
    }

    fun checkLoginStatus() {
        viewModelScope.launch {
            setLoading(true)
            try {
                val storedAuthInfo = authRepository.getStoredAuthInfo()
                if (storedAuthInfo != null) {
                    _loginState.value = Result.Success(storedAuthInfo)
                } else {
                    _loginState.value = Result.Error(Exception("No stored login"))
                }
            } catch (e: Exception) {
                _loginState.value = Result.Error(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            setLoading(true)
            try {
                authRepository.clearStoredAuth()
                _loginState.value = null
            } catch (e: Exception) {
                _loginState.value = Result.Error(e)
            } finally {
                setLoading(false)
            }
        }
    }

    override fun clearErrors() {
        _validationError.value = null
        _loginState.value = null
        super.clearErrors()
    }
}