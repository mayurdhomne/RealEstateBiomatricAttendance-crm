package com.crm.realestate.utils

import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Manages retry logic for operations that may fail due to transient errors
 */
class RetryManager {
    
    /**
     * Configuration for retry behavior
     */
    data class RetryConfig(
        val maxAttempts: Int = 3,
        val initialDelayMs: Long = 1000,
        val maxDelayMs: Long = 10000,
        val backoffMultiplier: Double = 2.0,
        val retryOnExceptions: Set<Class<out Throwable>> = setOf(
            java.net.SocketTimeoutException::class.java,
            java.net.ConnectException::class.java,
            java.io.IOException::class.java
        )
    )
    
    /**
     * Result of retry operation
     */
    sealed class RetryResult<out T> {
        data class Success<T>(val data: T) : RetryResult<T>()
        data class Failure(val lastException: Throwable, val attemptCount: Int) : RetryResult<Nothing>()
    }
    
    /**
     * Execute operation with retry logic
     */
    suspend fun <T> executeWithRetry(
        config: RetryConfig = NETWORK_RETRY_CONFIG,
        operation: suspend (attemptNumber: Int) -> T
    ): RetryResult<T> {
        var lastException: Throwable? = null
        
        repeat(config.maxAttempts) { attempt ->
            try {
                val result = operation(attempt + 1)
                return RetryResult.Success(result)
            } catch (e: Throwable) {
                lastException = e
                
                // Check if we should retry this exception
                val shouldRetry = config.retryOnExceptions.any { exceptionClass ->
                    exceptionClass.isAssignableFrom(e::class.java)
                }
                
                // If this is the last attempt or we shouldn't retry, return failure
                if (attempt == config.maxAttempts - 1 || !shouldRetry) {
                    return RetryResult.Failure(e, attempt + 1)
                }
                
                // Calculate delay for next attempt
                val delay = calculateDelay(attempt, config)
                delay(delay)
            }
        }
        
        return RetryResult.Failure(
            lastException ?: RuntimeException("Operation failed without exception"),
            config.maxAttempts
        )
    }
    
    /**
     * Calculate delay with exponential backoff
     */
    private fun calculateDelay(attempt: Int, config: RetryConfig): Long {
        val delay = (config.initialDelayMs * config.backoffMultiplier.pow(attempt.toDouble())).toLong()
        return minOf(delay, config.maxDelayMs)
    }
    
    companion object {
        /**
         * Default retry configuration for network operations
         */
        val NETWORK_RETRY_CONFIG = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 1000,
            maxDelayMs = 10000,
            backoffMultiplier = 2.0,
            retryOnExceptions = setOf(
                java.net.SocketTimeoutException::class.java,
                java.net.ConnectException::class.java,
                java.io.IOException::class.java,
                java.net.UnknownHostException::class.java
            )
        )
        
        /**
         * Retry configuration for biometric operations
         */
        val BIOMETRIC_RETRY_CONFIG = RetryConfig(
            maxAttempts = 2,
            initialDelayMs = 500,
            maxDelayMs = 2000,
            backoffMultiplier = 1.5,
            retryOnExceptions = setOf(
                RuntimeException::class.java
            )
        )
        
        /**
         * Retry configuration for database operations
         */
        val DATABASE_RETRY_CONFIG = RetryConfig(
            maxAttempts = 2,
            initialDelayMs = 100,
            maxDelayMs = 1000,
            backoffMultiplier = 2.0,
            retryOnExceptions = setOf(
                android.database.sqlite.SQLiteException::class.java,
                java.io.IOException::class.java
            )
        )

        /**
         * Quick retry configuration for tests
         */
        val QUICK_RETRY_CONFIG = RetryConfig(
            maxAttempts = 2,
            initialDelayMs = 100, // Shorter initial delay
            maxDelayMs = 500,   // Shorter max delay
            backoffMultiplier = 1.5,
            retryOnExceptions = setOf(
                java.io.IOException::class.java // Example, can be adjusted
            )
        )
    }
}
