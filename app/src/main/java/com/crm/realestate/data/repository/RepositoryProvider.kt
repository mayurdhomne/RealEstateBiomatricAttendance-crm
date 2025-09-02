package com.crm.realestate.data.repository

import android.content.Context
import com.crm.realestate.data.api.AttendanceApiService
import com.crm.realestate.data.api.AuthApiService
import com.crm.realestate.data.api.BiometricApiService
import com.crm.realestate.data.database.AttendanceDatabase
import com.crm.realestate.network.ApiConfig
import com.crm.realestate.network.TokenManager

/**
 * Provider class for repository instances
 * Handles dependency injection and repository creation
 */
object RepositoryProvider {
    
    /**
     * Provide AttendanceRepository with all required dependencies
     */
    fun provideAttendanceRepository(context: Context): AttendanceRepository {
        val database = AttendanceDatabase.getDatabase(context)
        val tokenManager = ApiConfig.provideTokenManager(context)
        val retrofit = ApiConfig.provideRetrofit(context)
        val apiService = retrofit.create(AttendanceApiService::class.java)
        
        return AttendanceRepository(
            apiService = apiService,
            offlineAttendanceDao = database.offlineAttendanceDao(),
            attendanceCacheDao = database.attendanceCacheDao(),
            tokenManager = tokenManager
        )
    }
    
    /**
     * Provide AuthRepository with required dependencies
     */
    fun provideAuthRepository(context: Context): AuthRepository {
        return AuthRepository(context)
    }
    
    /**
     * Provide BiometricRepository with required dependencies
     */
    fun provideBiometricRepository(context: Context): BiometricRepository {
        return BiometricRepository(context)
    }
    
    /**
     * Provide DashboardRepository with required dependencies
     */
    fun provideDashboardRepository(context: Context): DashboardRepository {
        val retrofit = ApiConfig.provideRetrofit(context)
        val apiService = retrofit.create(AttendanceApiService::class.java)
        
        return DashboardRepository(apiService)
    }
    
    /**
     * Get AttendanceRepository instance (alias for provideAttendanceRepository)
     */
    fun getAttendanceRepository(context: Context): AttendanceRepository {
        return provideAttendanceRepository(context)
    }
    
    /**
     * Get AuthRepository instance (alias for provideAuthRepository)
     */
    fun getAuthRepository(context: Context): AuthRepository {
        return provideAuthRepository(context)
    }
    
    /**
     * Get BiometricRepository instance (alias for provideBiometricRepository)
     */
    fun getBiometricRepository(context: Context): BiometricRepository {
        return provideBiometricRepository(context)
    }
}