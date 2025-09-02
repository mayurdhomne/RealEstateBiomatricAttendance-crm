package com.crm.realestate.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Error analytics and reporting system
 * Tracks error patterns, frequency, and provides insights for improving user experience
 */
class ErrorAnalytics private constructor(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("error_analytics", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Error event data class
     */
    data class ErrorEvent(
        val errorType: String,
        val errorMessage: String,
        val timestamp: Long,
        val context: String, // Activity/Fragment name
        val userAction: String?, // What user was trying to do
        val networkStatus: String, // online/offline
        val retryAttempts: Int = 0,
        val resolved: Boolean = false,
        val resolutionMethod: String? = null
    )
    
    /**
     * Error statistics
     */
    data class ErrorStats(
        val totalErrors: Int,
        val resolvedErrors: Int,
        val mostCommonError: String?,
        val errorsByType: Map<String, Int>,
        val averageRetryAttempts: Double,
        val resolutionRate: Double
    )
    
    /**
     * Log an error event
     */
    fun logError(
        errorType: String,
        errorMessage: String,
        context: String,
        userAction: String? = null,
        networkStatus: String = "unknown",
        retryAttempts: Int = 0
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val errorEvent = ErrorEvent(
                    errorType = errorType,
                    errorMessage = errorMessage,
                    timestamp = System.currentTimeMillis(),
                    context = context,
                    userAction = userAction,
                    networkStatus = networkStatus,
                    retryAttempts = retryAttempts,
                    resolved = false
                )
                
                saveErrorEvent(errorEvent)
                
                // Log to system for debugging
                Log.w("ErrorAnalytics", "Error logged: $errorType in $context - $errorMessage")
                
            } catch (e: Exception) {
                Log.e("ErrorAnalytics", "Failed to log error", e)
            }
        }
    }
    
    /**
     * Log error resolution
     */
    fun logErrorResolution(
        errorType: String,
        context: String,
        resolutionMethod: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateErrorResolution(errorType, context, resolutionMethod)
                Log.i("ErrorAnalytics", "Error resolved: $errorType in $context via $resolutionMethod")
            } catch (e: Exception) {
                Log.e("ErrorAnalytics", "Failed to log error resolution", e)
            }
        }
    }
    
    /**
     * Get error statistics
     */
    fun getErrorStats(): ErrorStats {
        return try {
            val errorEvents = getAllErrorEvents()
            calculateStats(errorEvents)
        } catch (e: Exception) {
            Log.e("ErrorAnalytics", "Failed to get error stats", e)
            ErrorStats(0, 0, null, emptyMap(), 0.0, 0.0)
        }
    }
    
    /**
     * Get recent errors (last 24 hours)
     */
    fun getRecentErrors(): List<ErrorEvent> {
        return try {
            val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            getAllErrorEvents().filter { it.timestamp >= oneDayAgo }
        } catch (e: Exception) {
            Log.e("ErrorAnalytics", "Failed to get recent errors", e)
            emptyList()
        }
    }
    
    /**
     * Get error patterns (most common error types)
     */
    fun getErrorPatterns(): Map<String, Int> {
        return try {
            val errorEvents = getAllErrorEvents()
            errorEvents.groupBy { it.errorType }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(10)
                .toMap()
        } catch (e: Exception) {
            Log.e("ErrorAnalytics", "Failed to get error patterns", e)
            emptyMap()
        }
    }
    
    /**
     * Get error trends (errors over time)
     */
    fun getErrorTrends(days: Int = 7): Map<String, Int> {
        return try {
            val startTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000)
            val errorEvents = getAllErrorEvents().filter { it.timestamp >= startTime }
            
            val trends = mutableMapOf<String, Int>()
            val calendar = Calendar.getInstance()
            
            for (i in 0 until days) {
                calendar.timeInMillis = startTime + (i * 24 * 60 * 60 * 1000)
                val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                
                val dayStart = calendar.timeInMillis
                val dayEnd = dayStart + (24 * 60 * 60 * 1000)
                
                val dayErrors = errorEvents.count { it.timestamp in dayStart until dayEnd }
                trends[dayKey] = dayErrors
            }
            
            trends
        } catch (e: Exception) {
            Log.e("ErrorAnalytics", "Failed to get error trends", e)
            emptyMap()
        }
    }
    
    /**
     * Export error data for analysis
     */
    fun exportErrorData(): String {
        return try {
            val errorEvents = getAllErrorEvents()
            val jsonArray = JSONArray()
            
            errorEvents.forEach { event ->
                val jsonObject = JSONObject().apply {
                    put("errorType", event.errorType)
                    put("errorMessage", event.errorMessage)
                    put("timestamp", event.timestamp)
                    put("context", event.context)
                    put("userAction", event.userAction ?: "")
                    put("networkStatus", event.networkStatus)
                    put("retryAttempts", event.retryAttempts)
                    put("resolved", event.resolved)
                    put("resolutionMethod", event.resolutionMethod ?: "")
                }
                jsonArray.put(jsonObject)
            }
            
            jsonArray.toString(2)
        } catch (e: Exception) {
            Log.e("ErrorAnalytics", "Failed to export error data", e)
            "{\"error\": \"Failed to export data\"}"
        }
    }
    
    /**
     * Clear old error data (older than specified days)
     */
    fun clearOldErrors(olderThanDays: Int = 30) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000)
                val errorEvents = getAllErrorEvents()
                val recentErrors = errorEvents.filter { it.timestamp >= cutoffTime }
                
                saveAllErrorEvents(recentErrors)
                Log.i("ErrorAnalytics", "Cleared errors older than $olderThanDays days")
            } catch (e: Exception) {
                Log.e("ErrorAnalytics", "Failed to clear old errors", e)
            }
        }
    }
    
    /**
     * Get error resolution suggestions based on patterns
     */
    fun getResolutionSuggestions(): List<String> {
        return try {
            val errorPatterns = getErrorPatterns()
            val suggestions = mutableListOf<String>()
            
            errorPatterns.forEach { (errorType, count) ->
                when {
                    errorType.contains("Network", ignoreCase = true) && count > 5 -> {
                        suggestions.add("Consider implementing better offline support for network-related operations")
                    }
                    errorType.contains("Biometric", ignoreCase = true) && count > 3 -> {
                        suggestions.add("Improve biometric error handling and provide clearer user guidance")
                    }
                    errorType.contains("Location", ignoreCase = true) && count > 3 -> {
                        suggestions.add("Add better location permission handling and fallback options")
                    }
                    errorType.contains("Database", ignoreCase = true) && count > 2 -> {
                        suggestions.add("Review database operations for potential optimization")
                    }
                }
            }
            
            if (suggestions.isEmpty()) {
                suggestions.add("Error patterns look good! Continue monitoring for improvements.")
            }
            
            suggestions
        } catch (e: Exception) {
            Log.e("ErrorAnalytics", "Failed to get resolution suggestions", e)
            listOf("Unable to generate suggestions at this time")
        }
    }
    
    private fun saveErrorEvent(errorEvent: ErrorEvent) {
        val errorEvents = getAllErrorEvents().toMutableList()
        errorEvents.add(errorEvent)
        saveAllErrorEvents(errorEvents)
    }
    
    private fun updateErrorResolution(errorType: String, context: String, resolutionMethod: String) {
        val errorEvents = getAllErrorEvents().toMutableList()
        
        // Find the most recent unresolved error of this type in this context
        val errorIndex = errorEvents.indexOfLast { 
            it.errorType == errorType && it.context == context && !it.resolved 
        }
        
        if (errorIndex != -1) {
            errorEvents[errorIndex] = errorEvents[errorIndex].copy(
                resolved = true,
                resolutionMethod = resolutionMethod
            )
            saveAllErrorEvents(errorEvents)
        }
    }
    
    private fun getAllErrorEvents(): List<ErrorEvent> {
        return try {
            val jsonString = prefs.getString("error_events", "[]") ?: "[]"
            val jsonArray = JSONArray(jsonString)
            val errorEvents = mutableListOf<ErrorEvent>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val errorEvent = ErrorEvent(
                    errorType = jsonObject.getString("errorType"),
                    errorMessage = jsonObject.getString("errorMessage"),
                    timestamp = jsonObject.getLong("timestamp"),
                    context = jsonObject.getString("context"),
                    userAction = if (jsonObject.has("userAction")) jsonObject.getString("userAction") else null,
                    networkStatus = jsonObject.optString("networkStatus", "unknown"),
                    retryAttempts = jsonObject.optInt("retryAttempts", 0),
                    resolved = jsonObject.optBoolean("resolved", false),
                    resolutionMethod = if (jsonObject.has("resolutionMethod")) jsonObject.getString("resolutionMethod") else null
                )
                errorEvents.add(errorEvent)
            }
            
            errorEvents
        } catch (e: Exception) {
            Log.e("ErrorAnalytics", "Failed to load error events", e)
            emptyList()
        }
    }
    
    private fun saveAllErrorEvents(errorEvents: List<ErrorEvent>) {
        try {
            val jsonArray = JSONArray()
            
            errorEvents.forEach { event ->
                val jsonObject = JSONObject().apply {
                    put("errorType", event.errorType)
                    put("errorMessage", event.errorMessage)
                    put("timestamp", event.timestamp)
                    put("context", event.context)
                    put("userAction", event.userAction ?: "")
                    put("networkStatus", event.networkStatus)
                    put("retryAttempts", event.retryAttempts)
                    put("resolved", event.resolved)
                    put("resolutionMethod", event.resolutionMethod ?: "")
                }
                jsonArray.put(jsonObject)
            }
            
            prefs.edit().putString("error_events", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("ErrorAnalytics", "Failed to save error events", e)
        }
    }
    
    private fun calculateStats(errorEvents: List<ErrorEvent>): ErrorStats {
        if (errorEvents.isEmpty()) {
            return ErrorStats(0, 0, null, emptyMap(), 0.0, 0.0)
        }
        
        val totalErrors = errorEvents.size
        val resolvedErrors = errorEvents.count { it.resolved }
        val resolutionRate = if (totalErrors > 0) (resolvedErrors.toDouble() / totalErrors) * 100 else 0.0
        
        val errorsByType = errorEvents.groupBy { it.errorType }
            .mapValues { it.value.size }
        
        val mostCommonError = errorsByType.maxByOrNull { it.value }?.key
        
        val totalRetryAttempts = errorEvents.sumOf { it.retryAttempts }
        val averageRetryAttempts = if (totalErrors > 0) totalRetryAttempts.toDouble() / totalErrors else 0.0
        
        return ErrorStats(
            totalErrors = totalErrors,
            resolvedErrors = resolvedErrors,
            mostCommonError = mostCommonError,
            errorsByType = errorsByType,
            averageRetryAttempts = averageRetryAttempts,
            resolutionRate = resolutionRate
        )
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ErrorAnalytics? = null
        
        fun getInstance(context: Context): ErrorAnalytics {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ErrorAnalytics(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        /**
         * Helper method to log errors from ErrorHandler
         */
        fun logFromErrorHandler(
            context: Context,
            error: ErrorHandler.AppError,
            activityContext: String,
            userAction: String? = null,
            networkStatus: String = "unknown",
            retryAttempts: Int = 0
        ) {
            getInstance(context).logError(
                errorType = error::class.java.simpleName,
                errorMessage = error.message,
                context = activityContext,
                userAction = userAction,
                networkStatus = networkStatus,
                retryAttempts = retryAttempts
            )
        }
    }
}