package com.crm.realestate.repository

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.fragment.app.FragmentActivity
import com.crm.realestate.data.api.BiometricApiService
import com.crm.realestate.data.models.*
import com.crm.realestate.data.repository.BiometricRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Unit tests for BiometricRepository
 * Tests biometric hardware detection, authentication, and registration API integration
 */
class BiometricRepositoryTest {

    private lateinit var biometricRepository: BiometricRepository
    private val mockContext = mockk<Context>()
    private val mockPackageManager = mockk<PackageManager>()
    private val mockBiometricManager = mockk<BiometricManager>()
    private val mockBiometricApiService = mockk<BiometricApiService>()
    private val mockActivity = mockk<FragmentActivity>()

    private val testRegistrationRequest = BiometricRegistrationRequest(
        employeeId = "EMP001",
        faceRegistered = true,
        fingerprintRegistered = true
    )

    private val testRegistrationResponse = BiometricRegistrationResponse(
        success = true,
        message = "Biometrics registered successfully",
        employeeId = "EMP001"
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        every { mockContext.packageManager } returns mockPackageManager
        
        // Mock ApiConfig to return our mocked services
        mockkObject(com.crm.realestate.network.ApiConfig)
        every { 
            com.crm.realestate.network.ApiConfig.provideRetrofit(any(), any()).create(BiometricApiService::class.java) 
        } returns mockBiometricApiService

        // Mock BiometricManager.from() static method
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(mockContext) } returns mockBiometricManager

        biometricRepository = BiometricRepository(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `checkBiometricAvailability with all hardware available returns correct availability`() = runTest {
        // Given
        every { mockPackageManager.hasSystemFeature("android.hardware.camera.front") } returns true
        every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_SUCCESS

        // When
        val result = biometricRepository.checkBiometricAvailability()

        // Then
        assertTrue(result.hasFaceDetection)
        assertTrue(result.hasFingerprint)
        assertTrue(result.canAuthenticateFingerprint)
    }

    @Test
    fun `checkBiometricAvailability with no camera returns false for face detection`() = runTest {
        // Given
        every { mockPackageManager.hasSystemFeature("android.hardware.camera.front") } returns false
        every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_SUCCESS

        // When
        val result = biometricRepository.checkBiometricAvailability()

        // Then
        assertFalse(result.hasFaceDetection)
        assertTrue(result.hasFingerprint)
        assertTrue(result.canAuthenticateFingerprint)
    }

    @Test
    fun `checkBiometricAvailability with no fingerprint hardware returns false for fingerprint`() = runTest {
        // Given
        every { mockPackageManager.hasSystemFeature("android.hardware.camera.front") } returns true
        every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        // When
        val result = biometricRepository.checkBiometricAvailability()

        // Then
        assertTrue(result.hasFaceDetection)
        assertFalse(result.hasFingerprint)
        assertFalse(result.canAuthenticateFingerprint)
    }

    @Test
    fun `checkBiometricAvailability with fingerprint hardware unavailable returns correct status`() = runTest {
        // Given
        every { mockPackageManager.hasSystemFeature("android.hardware.camera.front") } returns true
        every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE

        // When
        val result = biometricRepository.checkBiometricAvailability()

        // Then
        assertTrue(result.hasFaceDetection)
        assertFalse(result.hasFingerprint)
        assertFalse(result.canAuthenticateFingerprint)
    }

    @Test
    fun `checkBiometricAvailability with no fingerprints enrolled returns hardware available but cannot authenticate`() = runTest {
        // Given
        every { mockPackageManager.hasSystemFeature("android.hardware.camera.front") } returns true
        every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        // When
        val result = biometricRepository.checkBiometricAvailability()

        // Then
        assertTrue(result.hasFaceDetection)
        assertTrue(result.hasFingerprint) // Hardware is available
        assertFalse(result.canAuthenticateFingerprint) // But cannot authenticate
    }

    @Test
    fun `getFingerprintRegistrationStatus with success returns ready status`() {
        // Given
        every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_SUCCESS

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertTrue(result.first)
        assertEquals("Fingerprint hardware is ready for registration", result.second)
    }

    @Test
    fun `getFingerprintRegistrationStatus with no hardware returns unavailable status`() {
        // Given
        every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse(result.first)
        assertEquals("No fingerprint hardware available", result.second)
    }

    @Test
    fun `getFingerprintRegistrationStatus with hardware unavailable returns unavailable status`() {
        // Given
        every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse(result.first)
        assertEquals("Fingerprint hardware is currently unavailable", result.second)
    }

    @Test
    fun `getFingerprintRegistrationStatus with none enrolled returns enrollment required status`() {
        // Given
        every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse(result.first)
        assertEquals("No fingerprints enrolled. Please add a fingerprint in device settings first", result.second)
    }

    @Test
    fun `registerBiometrics with valid data returns success`() = runTest {
        // Given
        val mockResponse = mockk<Response<BiometricRegistrationResponse>>()
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body() } returns testRegistrationResponse
        coEvery { mockBiometricApiService.registerBiometrics(testRegistrationRequest) } returns mockResponse

        // When
        val result = biometricRepository.registerBiometrics("EMP001", true, true)

        // Then
        assertTrue(result is Result.Success<*>)
        assertEquals(testRegistrationResponse, (result as Result.Success<*>).data)
    }

    @Test
    fun `registerBiometrics with network error returns appropriate error`() = runTest {
        // Given
        coEvery { mockBiometricApiService.registerBiometrics(any()) } throws UnknownHostException()

        // When
        val result = biometricRepository.registerBiometrics("EMP001", true, true)

        // Then
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message?.contains("No internet connection") == true)
    }

    @Test
    fun `registerBiometrics with timeout error returns appropriate error`() = runTest {
        // Given
        coEvery { mockBiometricApiService.registerBiometrics(any()) } throws SocketTimeoutException()

        // When
        val result = biometricRepository.registerBiometrics("EMP001", true, true)

        // Then
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message?.contains("Connection timeout") == true)
    }

    @Test
    fun `registerBiometrics with IO error returns appropriate error`() = runTest {
        // Given
        coEvery { mockBiometricApiService.registerBiometrics(any()) } throws IOException("Network error")

        // When
        val result = biometricRepository.registerBiometrics("EMP001", true, true)

        // Then
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message?.contains("Network error") == true)
    }

    @Test
    fun `registerBiometrics with empty response body returns error`() = runTest {
        // Given
        val mockResponse = mockk<Response<BiometricRegistrationResponse>>()
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body() } returns null
        coEvery { mockBiometricApiService.registerBiometrics(any()) } returns mockResponse

        // When
        val result = biometricRepository.registerBiometrics("EMP001", true, true)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Registration failed: Invalid response from server", (result as Result.Error).message)
    }

    @Test
    fun `registerBiometrics handles different HTTP error codes correctly`() = runTest {
        val testCases = listOf(
            400 to "Invalid registration data",
            401 to "Authentication required. Please login again",
            403 to "Access denied for biometric registration",
            404 to "Registration service not available",
            409 to "Biometrics already registered for this employee",
            422 to "Invalid biometric data format",
            500 to "Server error. Please try again later"
        )

        testCases.forEach { (errorCode, expectedMessage) ->
            // Given
            val mockResponse = mockk<Response<BiometricRegistrationResponse>>()
            every { mockResponse.isSuccessful } returns false
            every { mockResponse.code() } returns errorCode
            every { mockResponse.message() } returns "HTTP $errorCode"
            coEvery { mockBiometricApiService.registerBiometrics(any()) } returns mockResponse

            // When
            val result = biometricRepository.registerBiometrics("EMP001", true, true)

            // Then
            assertTrue("Failed for error code $errorCode", result is Result.Error)
            assertEquals("Failed for error code $errorCode", expectedMessage, (result as Result.Error).message)
        }
    }

    @Test
    fun `checkBiometricAvailability handles all biometric manager states correctly`() = runTest {
        val testCases = listOf(
            BiometricManager.BIOMETRIC_SUCCESS to Triple(true, true, true),
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED to Triple(true, true, false),
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE to Triple(true, false, false),
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE to Triple(true, false, false),
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED to Triple(true, true, false),
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED to Triple(true, false, false),
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN to Triple(true, false, false)
        )

        testCases.forEach { (biometricStatus, expected) ->
            // Given
            every { mockPackageManager.hasSystemFeature("android.hardware.camera.front") } returns true
            every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns biometricStatus

            // When
            val result = biometricRepository.checkBiometricAvailability()

            // Then
            assertEquals("Face detection failed for status $biometricStatus", expected.first, result.hasFaceDetection)
            assertEquals("Fingerprint hardware failed for status $biometricStatus", expected.second, result.hasFingerprint)
            assertEquals("Fingerprint auth failed for status $biometricStatus", expected.third, result.canAuthenticateFingerprint)
        }
    }

    @Test
    fun `getFingerprintRegistrationStatus handles all biometric manager states correctly`() {
        val testCases = listOf(
            BiometricManager.BIOMETRIC_SUCCESS to Pair(true, "Fingerprint hardware is ready for registration"),
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE to Pair(false, "No fingerprint hardware available"),
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE to Pair(false, "Fingerprint hardware is currently unavailable"),
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED to Pair(false, "No fingerprints enrolled. Please add a fingerprint in device settings first"),
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED to Pair(false, "Security update required for fingerprint authentication"),
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED to Pair(false, "Fingerprint authentication is not supported"),
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN to Pair(false, "Fingerprint authentication status unknown")
        )

        testCases.forEach { (biometricStatus, expected) ->
            // Given
            every { mockBiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns biometricStatus

            // When
            val result = biometricRepository.getFingerprintRegistrationStatus()

            // Then
            assertEquals("Status failed for $biometricStatus", expected.first, result.first)
            assertEquals("Message failed for $biometricStatus", expected.second, result.second)
        }
    }
}