package com.crm.realestate.utils

import android.content.Context
import com.crm.realestate.database.AppDatabase
import com.crm.realestate.network.ApiConfig
import com.crm.realestate.network.TokenManager

/**
 * Simple test class to verify infrastructure components are properly configured
 */
class InfrastructureTest {
    
    fun testInfrastructure(context: Context): Boolean {
        return try {
            // Test database initialization
            val database = AppDatabase.getDatabase(context)
            val offlineDao = database.offlineAttendanceDao()
            val cacheDao = database.attendanceCacheDao()
            
            // Test API configuration
            val retrofit = ApiConfig.provideRetrofit(context)
            val tokenManager = ApiConfig.provideTokenManager(context)
            
            // Test token manager
            val testTokenManager = TokenManager(context)
            
            // If we reach here, all components initialized successfully
            true
        } catch (e: Exception) {
            false
        }
    }
}