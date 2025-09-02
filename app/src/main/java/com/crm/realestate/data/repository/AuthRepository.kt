package com.crm.realestate.data.repository

import android.content.Context
import com.crm.realestate.data.api.AuthApiService
import com.crm.realestate.data.api.dto.NetworkLoginResponse
import com.crm.realestate.data.models.EmployeeInfo
import com.crm.realestate.data.models.LoginResponse
import com.crm.realestate.data.models.Result
import com.crm.realestate.network.ApiConfig
import com.crm.realestate.network.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Repository implementation for authentication operations
 * Handles login API integration, token management, and employee information storage
 */
class AuthRepository(private val context: Context) {
    
    private val tokenManager = ApiConfig.provideTokenManager(context)
    private val authApiService: AuthApiService by lazy {
        ApiConfig.provideRetrofit(context) {
            // Handle unauthorized callback - will be used for automatic logout
        }.create(AuthApiService::class.java)
    }
    
    /**
     * Authenticate user with username and password
     * @param username Employee username provided by admin
     * @param password Employee password provided by admin
     * @return Result containing LoginResponse or error
     */
    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = authApiService.login(username, password)

                if (response.isSuccessful) {
                    val network = response.body()
                    if (network != null) {
                        // Map network DTO to internal model
                        val accessToken = network.access ?: ""
                        val employeeId = network.employeeId ?: ""
                        val fullName = network.name ?: "Employee"

                        val mappedEmployee = EmployeeInfo(
                            employeeId = employeeId,
                            fullName = fullName,
                            email = "",
                            department = "",
                            designation = ""
                        )

                        // Persist access token and employee info
                        if (accessToken.isNotBlank()) {
                            saveAuthToken(accessToken)
                        }
                        saveEmployeeInfo(mappedEmployee)

                        val mapped = LoginResponse(
                            token = accessToken,
                            employeeId = employeeId,
                            username = username,
                            biometricsRegistered = false, // default until backend provides
                            employeeInfo = mappedEmployee
                        )

                        Result.Success(mapped)
                    } else {
                        Result.Error(
                            exception = Exception("Empty response body")
                        )
                    }
                } else {
                    val errorMessage = when (response.code()) {
                        401 -> "Invalid username or password"
                        403 -> "Account access denied"
                        404 -> "Login service not available"
                        422 -> "Invalid login credentials format"
                        500 -> "Server error. Please try again later"
                        else -> "Login failed: ${response.message()}"
                    }
                    
                    Result.Error(
                        exception = HttpException(response)
                    )
                }
            } catch (e: UnknownHostException) {
                Result.Error(
                    exception = e
                )
            } catch (e: SocketTimeoutException) {
                Result.Error(
                    exception = e
                )
            } catch (e: IOException) {
                Result.Error(
                    exception = e
                )
            } catch (e: Exception) {
                Result.Error(
                    exception = e
                )
            }
        }
    }
    
    /**
     * Save authentication token securely
     * @param token JWT token from login response
     */
    suspend fun saveAuthToken(token: String) {
        tokenManager.saveToken(token)
    }
    
    /**
     * Get stored authentication token
     * @return JWT token or null if not available
     */
    suspend fun getAuthToken(): String? {
        return tokenManager.getToken()
    }
    
    /**
     * Check if current token is valid (exists and not expired)
     * @return true if token is valid, false otherwise
     */
    suspend fun isTokenValid(): Boolean {
        return tokenManager.isTokenValid()
    }
    
    /**
     * Refresh authentication token (placeholder for future implementation)
     * @return Result containing new token or error
     */
    suspend fun refreshToken(): Result<String> {
        // TODO: Implement token refresh logic when API endpoint is available
        return Result.Error(
            exception = UnsupportedOperationException("Token refresh not implemented")
        )
    }
    
    /**
     * Check if user is currently logged in
     * @return true if user has valid authentication, false otherwise
     */
    suspend fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }
    
    /**
     * Perform complete logout - clear all stored authentication data
     */
    suspend fun logout() {
        tokenManager.logout()
    }
    
    /**
     * Save employee information from login response
     * @param employeeInfo Employee information to store
     */
    suspend fun saveEmployeeInfo(employeeInfo: EmployeeInfo) {
        tokenManager.saveEmployeeInfo(employeeInfo)
    }
    
    /**
     * Get stored employee information
     * @return EmployeeInfo or null if not available
     */
    suspend fun getEmployeeInfo(): EmployeeInfo? {
        return tokenManager.getEmployeeInfo()
    }
    
    /**
     * Validate login credentials format
     * @param username Username to validate
     * @param password Password to validate
     * @return Pair of validation result and error message
     */
    fun validateCredentials(username: String, password: String): Pair<Boolean, String?> {
        return when {
            username.isBlank() -> Pair(false, "Username is required")
            password.isBlank() -> Pair(false, "Password is required")
            username.length < 3 -> Pair(false, "Username must be at least 3 characters")
            password.length < 6 -> Pair(false, "Password must be at least 6 characters")
            else -> Pair(true, null)
        }
    }
    
    /**
     * Get stored authentication info including LoginResponse
     * @return LoginResponse or null if not available
     */
    suspend fun getStoredAuthInfo(): LoginResponse? {
        val token = getAuthToken()
        val employeeInfo = getEmployeeInfo()
        
        return if (token != null && employeeInfo != null) {
            LoginResponse(
                token = token,
                employeeId = employeeInfo.employeeId,
                username = "", // Username not stored, could be enhanced later
                biometricsRegistered = true, // Default to true for stored auth
                employeeInfo = employeeInfo
            )
        } else {
            null
        }
    }
    
    /**
     * Clear all stored authentication data
     */
    suspend fun clearStoredAuth() {
        tokenManager.logout()
    }
}