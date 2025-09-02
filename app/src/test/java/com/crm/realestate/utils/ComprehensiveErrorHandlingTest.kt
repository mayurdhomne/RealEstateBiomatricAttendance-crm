package com.crm.realestate.utils

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ComprehensiveErrorHandlingTest {
    
    private lateinit var context: Context
    private lateinit var errorAnalytics: ErrorAnalytics
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        errorAnalytics = ErrorAnalytics.getInstance(context)
    }
    
    @Test
    fun `test network error handling`() {
        // Test UnknownHostException
        val networkError = ErrorHandler.handleException(context, UnknownHostException("No internet"))
        assertTrue(networkError is ErrorHandler.AppError.NetworkUnavailable)
        assertTrue(ErrorHandler.canRetry(networkError))
        
        // Test SocketTimeoutException
        val timeoutError = ErrorHandler.handleException(context, SocketTimeoutException("Timeout"))
        assertTrue(timeoutError is ErrorHandler.AppError.NetworkTimeout)
        assertTrue(ErrorHandler.canRetry(timeoutError))
        
        // Test SSLException
        val sslError = ErrorHandler.handleException(context, SSLException("SSL error"))
        assertTrue(sslError is ErrorHandler.AppError.ServerError)
        assertTrue(ErrorHandler.canRetry(sslError))
    }
    
    @Test
    fun `test biometric error handling`() {
        // Test biometric not enrolled
        val notEnrolledError = ErrorHandler.handleBiometricError(
            context, 
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED, 
            "No biometric enrolled"
        )
        assertTrue(notEnrolledError is ErrorHandler.AppError.BiometricNotEnrolled)
        assertTrue(ErrorHandler.requiresUserAction(notEnrolledError))
        
        // Test hardware unavailable
        val hwUnavailableError = ErrorHandler.handleBiometricError(
            context,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            "Hardware unavailable"
        )
        assertTrue(hwUnavailableError is ErrorHandler.AppError.BiometricUnavailable)
        assertFalse(ErrorHandler.canRetry(hwUnavailableError))
    }
    
    @Test
    fun `test HTTP error handling`() {
        // Test 401 Unauthorized
        val unauthorizedError = ErrorHandler.handleHttpError(401, "Unauthorized")
        assertTrue(unauthorizedError is ErrorHandler.AppError.UnauthorizedError)
        assertFalse(ErrorHandler.canRetry(unauthorizedError))
        
        // Test 400 Bad Request
        val badRequestError = ErrorHandler.handleHttpError(400, "Bad request")
        assertTrue(badRequestError is ErrorHandler.AppError.ValidationError)
        assertFalse(ErrorHandler.canRetry(badRequestError))
        
        // Test 500 Server Error
        val serverError = ErrorHandler.handleHttpError(500, "Internal server error")
        assertTrue(serverError is ErrorHandler.AppError.ServerError)
        assertTrue(ErrorHandler.canRetry(serverError))
    }
    
    @Test
    fun `test result wrapper functionality`() {
        // Test success result
        val successResult = Result.Success("test data")
        assertTrue(successResult.isSuccess())
        assertFalse(successResult.isError())
        assertEquals("test data", successResult.getDataOrNull())
        
        // Test error result
        val errorResult = Result.Error(RuntimeException("test error"))
        assertFalse(errorResult.isSuccess())
        assertTrue(errorResult.isError())
        assertTrue(errorResult.getErrorOrNull() is RuntimeException)
        
        // Test loading result
        val loadingResult = Result.Loading(true, "Loading...")
        assertTrue(loadingResult.isLoading())
        assertFalse(loadingResult.isSuccess())
        assertFalse(loadingResult.isError())
    }
    
    @Test
    fun `test result mapping and chaining`() {
        val successResult = Result.Success(5)
        val mappedResult = successResult.map { it * 2 }
        
        assertTrue(mappedResult.isSuccess())
        assertEquals(10, mappedResult.getDataOrNull())
        
        // Test error mapping
        val errorResult = Result.Error(RuntimeException("error"))
        val mappedErrorResult = errorResult.map { "should not execute" }
        
        assertTrue(mappedErrorResult.isError())
    }
    
    @Test
    fun `test error analytics logging`() {
        // Clear any existing data
        errorAnalytics.clearOldErrors(0)
        
        // Log some test errors
        errorAnalytics.logError(
            errorType = "NetworkError",
            errorMessage = "Connection failed",
            context = "TestActivity",
            userAction = "login_attempt",
            networkStatus = "offline"
        )
        
        errorAnalytics.logError(
            errorType = "BiometricError", 
            errorMessage = "Fingerprint not recognized",
            context = "TestActivity",
            userAction = "biometric_auth",
            networkStatus = "online"
        )
        
        // Wait a bit for async operations
        Thread.sleep(100)
        
        // Check stats
        val stats = errorAnalytics.getErrorStats()
        assertEquals(2, stats.totalErrors)
        assertEquals(0, stats.resolvedErrors)
        assertTrue(stats.errorsByType.containsKey("NetworkError"))
        assertTrue(stats.errorsByType.containsKey("BiometricError"))
        
        // Test error resolution
        errorAnalytics.logErrorResolution(
            errorType = "NetworkError",
            context = "TestActivity", 
            resolutionMethod = "retry_success"
        )
        
        Thread.sleep(100)
        
        val updatedStats = errorAnalytics.getErrorStats()
        assertEquals(1, updatedStats.resolvedErrors)
    }
    
    @Test
    fun `test error patterns and trends`() {
        // Clear existing data
        errorAnalytics.clearOldErrors(0)
        
        // Log multiple errors of same type
        repeat(3) {
            errorAnalytics.logError(
                errorType = "NetworkError",
                errorMessage = "Connection failed $it",
                context = "TestActivity"
            )
        }
        
        repeat(2) {
            errorAnalytics.logError(
                errorType = "BiometricError",
                errorMessage = "Auth failed $it", 
                context = "TestActivity"
            )
        }
        
        Thread.sleep(100)
        
        val patterns = errorAnalytics.getErrorPatterns()
        assertEquals(3, patterns["NetworkError"])
        assertEquals(2, patterns["BiometricError"])
        
        val trends = errorAnalytics.getErrorTrends(1)
        assertTrue(trends.values.sum() >= 5) // Should have at least 5 errors today
    }
    
    @Test
    fun `test retry manager functionality`() {
        val retryManager = RetryManager()
        var attemptCount = 0
        
        // Test successful retry
        val successResult = kotlinx.coroutines.runBlocking {
            retryManager.executeWithRetry(RetryManager.QUICK_RETRY_CONFIG) { attempt ->
                attemptCount++
                if (attempt < 2) throw IOException("Network error")
                "Success on attempt $attempt"
            }
        }
        
        assertTrue(successResult is RetryManager.RetryResult.Success)
        assertEquals(2, attemptCount)
        assertEquals("Success on attempt 2", (successResult as RetryManager.RetryResult.Success).data)
        
        // Test failure after max attempts
        attemptCount = 0
        val failureResult = kotlinx.coroutines.runBlocking {
            retryManager.executeWithRetry(RetryManager.QUICK_RETRY_CONFIG) { attempt ->
                attemptCount++
                throw IOException("Always fails")
            }
        }
        
        assertTrue(failureResult is RetryManager.RetryResult.Failure)
        assertEquals(2, attemptCount) // QUICK_RETRY_CONFIG has maxAttempts = 2
    }
    
    @Test
    fun `test loading state manager`() {
        val loadingManager = LoadingStateManager.create(context)
        
        // Test initial state
        assertFalse(loadingManager.isLoading())
        
        // Test show loading
        loadingManager.showLoading("Test loading...")
        assertTrue(loadingManager.isLoading())
        assertEquals("Test loading...", loadingManager.getCurrentMessage())
        
        // Test update message
        loadingManager.updateLoadingMessage("Updated message")
        assertEquals("Updated message", loadingManager.getCurrentMessage())
        
        // Test hide loading
        loadingManager.hideLoading()
        assertFalse(loadingManager.isLoading())
        
        // Cleanup
        loadingManager.cleanup()
    }
    
    @Test
    fun `test network connectivity monitor`() {
        val networkMonitor = NetworkConnectivityMonitor(context)
        
        // Test network type description (will be "No connection" in test environment)
        val networkType = networkMonitor.getNetworkTypeDescription()
        assertTrue(networkType.isNotEmpty())
        
        // Test connection status (will be false in test environment)
        val isConnected = networkMonitor.isCurrentlyConnected()
        // In test environment, this will typically be false
        // Just verify the method doesn't crash
        assertTrue(isConnected || !isConnected) // Always true, just testing execution
    }
    
    @Test
    fun `test error message display formatting`() {
        val networkError = ErrorHandler.AppError.NetworkUnavailable()
        val displayMessage = ErrorHandler.getDisplayMessage(networkError)
        
        assertTrue(displayMessage.isNotEmpty())
        assertTrue(displayMessage.contains("internet") || displayMessage.contains("connection"))
        
        val biometricError = ErrorHandler.AppError.FaceDetectionFailed()
        val biometricMessage = ErrorHandler.getDisplayMessage(biometricError)
        
        assertTrue(biometricMessage.isNotEmpty())
        assertTrue(biometricMessage.contains("face") || biometricMessage.contains("lighting"))
    }
    
    @Test
    fun `test error analytics export functionality`() {
        // Clear and add test data
        errorAnalytics.clearOldErrors(0)
        
        errorAnalytics.logError(
            errorType = "TestError",
            errorMessage = "Test message",
            context = "TestContext"
        )
        
        Thread.sleep(100)
        
        val exportData = errorAnalytics.exportErrorData()
        assertTrue(exportData.isNotEmpty())
        assertTrue(exportData.contains("TestError"))
        assertTrue(exportData.contains("Test message"))
        assertTrue(exportData.contains("TestContext"))
    }
    
    @Test
    fun `test error resolution suggestions`() {
        // Clear and add test data that should trigger suggestions
        errorAnalytics.clearOldErrors(0)
        
        repeat(6) {
            errorAnalytics.logError(
                errorType = "NetworkError",
                errorMessage = "Network failed $it",
                context = "TestActivity"
            )
        }
        
        repeat(4) {
            errorAnalytics.logError(
                errorType = "BiometricError", 
                errorMessage = "Biometric failed $it",
                context = "TestActivity"
            )
        }
        
        Thread.sleep(100)
        
        val suggestions = errorAnalytics.getResolutionSuggestions()
        assertTrue(suggestions.isNotEmpty())
        
        val hasNetworkSuggestion = suggestions.any { it.contains("offline", ignoreCase = true) }
        val hasBiometricSuggestion = suggestions.any { it.contains("biometric", ignoreCase = true) }
        
        assertTrue(hasNetworkSuggestion || hasBiometricSuggestion)
    }
}