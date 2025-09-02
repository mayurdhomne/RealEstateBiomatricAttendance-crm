package com.crm.realestate.utils

import android.content.Context
import com.crm.realestate.database.AppDatabase
import com.crm.realestate.network.ApiConfig
import com.crm.realestate.network.TokenManager
import retrofit2.Retrofit

object AppConfig {
    
    fun getDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
    
    fun getRetrofit(context: Context): Retrofit {
        return ApiConfig.provideRetrofit(context)
    }
    
    fun getTokenManager(context: Context): TokenManager {
        return ApiConfig.provideTokenManager(context)
    }
}