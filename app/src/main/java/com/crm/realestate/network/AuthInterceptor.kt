package com.crm.realestate.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * HTTP Interceptor for automatic Bearer token injection and 401 response handling
 * Automatically adds Authorization header to requests and handles token expiration
 */
class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val onUnauthorized: (() -> Unit)? = null
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Skip token injection for login and registration endpoints
        val url = originalRequest.url.toString()
        val isAuthEndpoint = url.contains("/login/") || url.contains("/register/")
        
        val request = if (!isAuthEndpoint) {
            // Get token synchronously for interceptor
            val token = runBlocking { tokenManager.getToken() }
            val isTokenValid = runBlocking { tokenManager.isTokenValid() }
            
            if (token != null && isTokenValid) {
                originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                // Token is expired or invalid, clear it
                if (token != null) {
                    runBlocking { tokenManager.clearToken() }
                }
                originalRequest
            }
        } else {
            originalRequest
        }
        
        val response = chain.proceed(request)
        
        // Handle 401 responses by clearing token and triggering logout
        if (response.code == 401 && !isAuthEndpoint) {
            runBlocking { 
                tokenManager.logout()
            }
            // Notify about unauthorized access for UI handling
            onUnauthorized?.invoke()
        }
        
        return response
    }
}

/**
 * Interface for handling authentication events
 */
interface AuthEventListener {
    fun onTokenExpired()
    fun onUnauthorizedAccess()
    fun onLogoutRequired()
}