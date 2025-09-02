package com.crm.realestate.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API configuration object for Retrofit setup with authentication
 * Provides configured Retrofit instance and TokenManager
 */
object ApiConfig {
    
    private const val BASE_URL = "https://shubhamgharde29.pythonanywhere.com"
    private const val TIMEOUT_SECONDS = 30L
    
    /**
     * Provide configured Retrofit instance with authentication interceptor
     */
    fun provideRetrofit(context: Context, onUnauthorized: (() -> Unit)? = null): Retrofit {
        val tokenManager = TokenManager(context)
        
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val authInterceptor = AuthInterceptor(tokenManager, onUnauthorized)
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    /**
     * Provide TokenManager instance with error handling
     */
    fun provideTokenManager(context: Context): TokenManager {
        return try {
            TokenManager(context)
        } catch (e: Exception) {
            // If the default initialization fails, try again after clearing stored data
            context.deleteSharedPreferences("auth_prefs")
            try {
                TokenManager(context)
            } catch (e2: Exception) {
                // Last resort - recreate with new instance
                TokenManager(context.applicationContext)
            }
        }
    }
    
    /**
     * Get base URL for API endpoints
     */
    fun getBaseUrl(): String = BASE_URL
}