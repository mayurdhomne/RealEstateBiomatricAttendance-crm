package com.crm.realestate.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.util.Base64
import com.google.gson.Gson
import com.crm.realestate.data.models.EmployeeInfo
import androidx.core.content.edit
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Secure token management system using EncryptedSharedPreferences
 * Handles JWT token storage, validation, and employee information persistence
 * Includes fallback mechanism for encryption errors
 */
class TokenManager(private val context: Context) {
    
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    // Regular SharedPreferences as fallback
    private val fallbackPrefs by lazy {
        context.getSharedPreferences("auth_prefs_fallback", Context.MODE_PRIVATE)
    }
    
    // Use lazy initialization to handle exceptions gracefully
    private val encryptedPrefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                "auth_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing EncryptedSharedPreferences: ${e.message}")
            // If encryption fails, delete the file to force recreation
            context.deleteSharedPreferences("auth_prefs")
            
            // Try to recreate the preferences after deletion
            try {
                EncryptedSharedPreferences.create(
                    context,
                    "auth_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to recreate EncryptedSharedPreferences: ${e2.message}")
                // Fall back to regular SharedPreferences if all else fails
                fallbackPrefs
            }
        }
    }
    
    private val gson = Gson()
    
    /**
     * Helper method to safely access SharedPreferences
     * Tries to use encrypted preferences first, falls back to regular preferences if error occurs
     */
    private fun getActivePrefs(): SharedPreferences {
        return try {
            encryptedPrefs
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing EncryptedSharedPreferences: ${e.message}")
            fallbackPrefs
        }
    }
    
    /**
     * Save JWT token with automatic expiration extraction
     */
    suspend fun saveToken(token: String) = withContext(Dispatchers.IO) {
        try {
            val prefs = getActivePrefs()
            prefs.edit().putString(KEY_JWT_TOKEN, token).apply()
            // Extract and save token expiration time for local validation
            val expirationTime = extractExpirationFromJWT(token)
            prefs.edit().putLong(KEY_TOKEN_EXPIRATION, expirationTime).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving token: ${e.message}")
            // If encrypted storage fails completely, try regular prefs as last resort
            fallbackPrefs.edit().putString(KEY_JWT_TOKEN, token).apply()
            val expirationTime = extractExpirationFromJWT(token)
            fallbackPrefs.edit().putLong(KEY_TOKEN_EXPIRATION, expirationTime).apply()
        }
    }
    
    /**
     * Retrieve stored JWT token
     */
    suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            getActivePrefs().getString(KEY_JWT_TOKEN, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving token: ${e.message}")
            // Try fallback if encrypted storage fails
            fallbackPrefs.getString(KEY_JWT_TOKEN, null)
        }
    }
    
    /**
     * Check if current token is expired
     */
    suspend fun isTokenExpired(): Boolean = withContext(Dispatchers.IO) {
        val expirationTime = try {
            getActivePrefs().getLong(KEY_TOKEN_EXPIRATION, 0)
        } catch (e: Exception) {
            fallbackPrefs.getLong(KEY_TOKEN_EXPIRATION, 0)
        }
        
        System.currentTimeMillis() > expirationTime
    }
    
    /**
     * Check if token is valid (exists and not expired)
     */
    suspend fun isTokenValid(): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        token != null && !isTokenExpired()
    }
    
    /**
     * Get token expiration time in milliseconds
     */
    suspend fun getTokenExpirationTime(): Long? = withContext(Dispatchers.IO) {
        val expirationTime = try {
            getActivePrefs().getLong(KEY_TOKEN_EXPIRATION, 0)
        } catch (e: Exception) {
            fallbackPrefs.getLong(KEY_TOKEN_EXPIRATION, 0)
        }
        
        if (expirationTime > 0) expirationTime else null
    }
    
    /**
     * Clear all stored authentication data
     */
    suspend fun clearToken() = withContext(Dispatchers.IO) {
        try {
            getActivePrefs().edit { clear() }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing token from encrypted storage: ${e.message}")
            fallbackPrefs.edit { clear() }
        }
        
        // Always clear fallback as well
        fallbackPrefs.edit { clear() }
        
        // Try to fully reset the encrypted storage
        try {
            context.deleteSharedPreferences("auth_prefs")
        } catch (e: Exception) {
            Log.e(TAG, "Could not delete auth_prefs: ${e.message}")
        }
    }
    
    /**
     * Check if user is logged in (has valid token)
     */
    suspend fun isLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        isTokenValid()
    }
    
    /**
     * Save employee information from login response
     */
    suspend fun saveEmployeeInfo(employeeInfo: EmployeeInfo) = withContext(Dispatchers.IO) {
        val employeeJson = gson.toJson(employeeInfo)
        
        try {
            getActivePrefs().edit { putString(KEY_EMPLOYEE_INFO, employeeJson) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving employee info to encrypted storage: ${e.message}")
            fallbackPrefs.edit { putString(KEY_EMPLOYEE_INFO, employeeJson) }
        }
    }
    
    /**
     * Retrieve stored employee information
     */
    suspend fun getEmployeeInfo(): EmployeeInfo? = withContext(Dispatchers.IO) {
        // Try first from encrypted storage
        var employeeJson = try {
            getActivePrefs().getString(KEY_EMPLOYEE_INFO, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving employee info from encrypted storage: ${e.message}")
            null
        }
        
        // If not found, try from fallback
        if (employeeJson == null) {
            employeeJson = fallbackPrefs.getString(KEY_EMPLOYEE_INFO, null)
        }
        
        if (employeeJson != null) {
            try {
                gson.fromJson(employeeJson, EmployeeInfo::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing employee info: ${e.message}")
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Perform complete logout - clear all stored data
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        clearToken()
    }
    
    /**
     * Extract expiration time from JWT token payload
     */
    private fun extractExpirationFromJWT(token: String): Long {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return getDefaultExpirationTime()
            
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
            val json = JSONObject(payload)
            val exp = json.optLong("exp", 0)
            
            if (exp > 0) {
                exp * 1000 // Convert to milliseconds
            } else {
                getDefaultExpirationTime()
            }
        } catch (e: Exception) {
            // If we can't parse the token, assume it expires in 24 hours
            getDefaultExpirationTime()
        }
    }
    
    /**
     * Get default expiration time (24 hours from now)
     */
    private fun getDefaultExpirationTime(): Long {
        return System.currentTimeMillis() + (24 * 60 * 60 * 1000)
    }
    
    companion object {
        private const val TAG = "TokenManager"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_TOKEN_EXPIRATION = "token_expiration"
        private const val KEY_EMPLOYEE_INFO = "employee_info"
    }
}