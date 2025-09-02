package com.crm.realestate.performance

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import com.crm.realestate.data.models.BiometricAvailability
import com.crm.realestate.data.repository.BiometricRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis

/**
 * Performance tests for biometric operations
 * Tests response times, memory usage, and resource efficiency
 */
@RunWith(RobolectricTestRunner::class)
class BiometricPerformanceTest {

    private lateinit var biometricRepository: BiometricRepository
    private val mockContext = mockk<Context>()
    private val mockPackageManager = mockk<PackageManager>()
    private val mockBiometricManager = mockk<BiometricManager>()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.hasSystemFeature("android.hardware.camera.front") } returns true
        
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(mockContext) } returns mockBiometricManager
        every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_SUCCESS

        // Mock ApiConfig
        mockkObject(com.crm.realestate.network.ApiConfig)
        every { 
            com.crm.realestate.network.ApiConfig.provideRetrofit(any(), any()).create(any<Class<*>>()) 
        } returns mockk(relaxed = true)

        biometricRepository = BiometricRepository(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `checkBiometricAvailability performance test`() = runTest {
        // Given
        val iterations = 1000
        val maxAcceptableTimeMs = 50L // 50ms max per call

        // When
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                biometricRepository.checkBiometricAvailability()
            }
        }

        // Then
        val averageTime = totalTime / iterations
        assertTrue(
            "Average time per call ($averageTime ms) exceeds maximum acceptable time ($maxAcceptableTimeMs ms)",
            averageTime <= maxAcceptableTimeMs
        )
        
        println("Biometric availability check - Average time: ${averageTime}ms over $iterations iterations")
    }

    @Test
    fun `checkBiometricAvailability consistency test`() = runTest {
        // Given
        val iterations = 100
        val results = mutableListOf<BiometricAvailability>()

        // When
        repeat(iterations) {
            results.add(biometricRepository.checkBiometricAvailability())
        }

        // Then - All results should be identical (consistent)
        val firstResult = results.first()
        assertTrue("Results should be consistent across multiple calls", 
            results.all { it == firstResult })
    }

    @Test
    fun `getFingerprintRegistrationStatus performance test`() {
        // Given
        val iterations = 1000
        val maxAcceptableTimeMs = 10L // 10ms max per call

        // When
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                biometricRepository.getFingerprintRegistrationStatus()
            }
        }

        // Then
        val averageTime = totalTime / iterations
        assertTrue(
            "Average time per call ($averageTime ms) exceeds maximum acceptable time ($maxAcceptableTimeMs ms)",
            averageTime <= maxAcceptableTimeMs
        )
        
        println("Fingerprint status check - Average time: ${averageTime}ms over $iterations iterations")
    }

    @Test
    fun `concurrent biometric availability checks`() = runTest {
        // Given
        val concurrentCalls = 50
        val maxAcceptableTimeMs = 200L // 200ms max for all concurrent calls

        // When
        val totalTime = measureTimeMillis {
            val jobs = coroutineScope {
                (1..concurrentCalls).map {
                    async {
                    biometricRepository.checkBiometricAvailability()
                }
            }
            }
            jobs.forEach { it.await() }
        }

        // Then
        assertTrue(
            "Concurrent calls took too long ($totalTime ms) for $concurrentCalls calls",
            totalTime <= maxAcceptableTimeMs
        )
        
        println("Concurrent biometric checks - Total time: ${totalTime}ms for $concurrentCalls calls")
    }

    @Test
    fun `biometric availability check memory usage`() = runTest {
        // Given
        val iterations = 1000
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // When
        repeat(iterations) {
            biometricRepository.checkBiometricAvailability()
        }

        // Force garbage collection
        System.gc()
        Thread.sleep(100)

        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory

        // Then - Memory increase should be minimal (less than 1MB)
        val maxAcceptableMemoryIncrease = 1024 * 1024 // 1MB
        assertTrue(
            "Memory increase ($memoryIncrease bytes) exceeds acceptable limit ($maxAcceptableMemoryIncrease bytes)",
            memoryIncrease <= maxAcceptableMemoryIncrease
        )
        
        println("Memory usage - Increase: ${memoryIncrease / 1024}KB after $iterations calls")
    }

    @Test
    fun `biometric manager state changes performance`() = runTest {
        // Given
        val states = listOf(
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        )
        val iterationsPerState = 100
        val maxTimePerState = 50L // 50ms max per state

        // When & Then
        states.forEach { state ->
            every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns state
            
            val timeForState = measureTimeMillis {
                repeat(iterationsPerState) {
                    biometricRepository.checkBiometricAvailability()
                }
            }
            
            val averageTimeForState = timeForState / iterationsPerState
            assertTrue(
                "State $state took too long (${averageTimeForState}ms average)",
                averageTimeForState <= maxTimePerState
            )
            
            println("State $state - Average time: ${averageTimeForState}ms")
        }
    }

    @Test
    fun `fingerprint status check with different hardware states`() {
        // Given
        val states = listOf(
            BiometricManager.BIOMETRIC_SUCCESS,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN
        )
        val iterationsPerState = 100
        val maxTimePerState = 20L // 20ms max per state

        // When & Then
        states.forEach { state ->
            every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns state
            
            val timeForState = measureTimeMillis {
                repeat(iterationsPerState) {
                    biometricRepository.getFingerprintRegistrationStatus()
                }
            }
            
            val averageTimeForState = timeForState / iterationsPerState
            assertTrue(
                "Fingerprint status check for state $state took too long (${averageTimeForState}ms average)",
                averageTimeForState <= maxTimePerState
            )
        }
    }

    @Test
    fun `biometric operations under memory pressure`() = runTest {
        // Given - Create memory pressure
        val memoryPressure = mutableListOf<ByteArray>()
        try {
            // Allocate memory to create pressure (but not cause OOM)
            repeat(100) {
                memoryPressure.add(ByteArray(1024 * 1024)) // 1MB each
            }
        } catch (e: OutOfMemoryError) {
            // Reduce pressure if we hit OOM
            memoryPressure.clear()
            repeat(50) {
                memoryPressure.add(ByteArray(1024 * 1024))
            }
        }

        val maxAcceptableTime = 100L // 100ms max under memory pressure

        // When
        val timeUnderPressure = measureTimeMillis {
            repeat(100) {
                biometricRepository.checkBiometricAvailability()
                biometricRepository.getFingerprintRegistrationStatus()
            }
        }

        // Then
        assertTrue(
            "Operations under memory pressure took too long (${timeUnderPressure}ms)",
            timeUnderPressure <= maxAcceptableTime * 100 // 100 iterations
        )
        
        println("Under memory pressure - Total time: ${timeUnderPressure}ms for 200 operations")
    }

    @Test
    fun `biometric availability check thread safety`() = runTest {
        // Given
        val threadCount = 10
        val iterationsPerThread = 100
        val results = mutableListOf<BiometricAvailability>()
        val exceptions = mutableListOf<Exception>()

        // When
        val jobs = coroutineScope {
            (1..threadCount).map {
                async {
                try {
                    repeat(iterationsPerThread) {
                        val result = biometricRepository.checkBiometricAvailability()
                        synchronized(results) {
                            results.add(result)
                        }
                    }
                } catch (e: Exception) {
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                }
            }
        }
        }
        
        jobs.forEach { it.await() }

        // Then
        assertTrue("Exceptions occurred during concurrent access: $exceptions", exceptions.isEmpty())
        assertEquals("Not all results were collected", threadCount * iterationsPerThread, results.size)
        
        // All results should be identical (thread-safe)
        val firstResult = results.first()
        assertTrue("Results should be consistent across threads", 
            results.all { it == firstResult })
    }

    @Test
    fun `biometric operations resource cleanup`() = runTest {
        // Given
        val iterations = 1000
        val initialHandles = getApproximateHandleCount()

        // When
        repeat(iterations) {
            biometricRepository.checkBiometricAvailability()
            biometricRepository.getFingerprintRegistrationStatus()
        }

        // Force cleanup
        System.gc()
        Thread.sleep(100)

        val finalHandles = getApproximateHandleCount()
        val handleIncrease = finalHandles - initialHandles

        // Then - Handle count should not increase significantly
        val maxAcceptableHandleIncrease = 10
        assertTrue(
            "Handle count increased too much ($handleIncrease handles)",
            handleIncrease <= maxAcceptableHandleIncrease
        )
        
        println("Resource cleanup - Handle increase: $handleIncrease after $iterations operations")
    }

    @Test
    fun `biometric operations performance regression test`() = runTest {
        // Given - Baseline performance expectations
        val baselineAvailabilityCheckMs = 50L
        val baselineFingerprintStatusMs = 20L
        val iterations = 100

        // When - Test availability check performance
        val availabilityTime = measureTimeMillis {
            repeat(iterations) {
                biometricRepository.checkBiometricAvailability()
            }
        }
        val avgAvailabilityTime = availabilityTime / iterations

        // Test fingerprint status performance
        val fingerprintTime = measureTimeMillis {
            repeat(iterations) {
                biometricRepository.getFingerprintRegistrationStatus()
            }
        }
        val avgFingerprintTime = fingerprintTime / iterations

        // Then
        assertTrue(
            "Biometric availability check performance regression: ${avgAvailabilityTime}ms > ${baselineAvailabilityCheckMs}ms",
            avgAvailabilityTime <= baselineAvailabilityCheckMs
        )
        
        assertTrue(
            "Fingerprint status check performance regression: ${avgFingerprintTime}ms > ${baselineFingerprintStatusMs}ms",
            avgFingerprintTime <= baselineFingerprintStatusMs
        )
        
        println("Performance regression test passed:")
        println("  Availability check: ${avgAvailabilityTime}ms (baseline: ${baselineAvailabilityCheckMs}ms)")
        println("  Fingerprint status: ${avgFingerprintTime}ms (baseline: ${baselineFingerprintStatusMs}ms)")
    }

    /**
     * Approximate handle count for resource leak detection
     * This is a simplified approach - in real scenarios you might use more sophisticated tools
     */
    private fun getApproximateHandleCount(): Int {
        // This is a simplified approximation
        // In real testing, you might use JVM monitoring tools
        return Runtime.getRuntime().availableProcessors() + 
               Thread.activeCount() + 
               (Runtime.getRuntime().totalMemory() / (1024 * 1024)).toInt()
    }
}