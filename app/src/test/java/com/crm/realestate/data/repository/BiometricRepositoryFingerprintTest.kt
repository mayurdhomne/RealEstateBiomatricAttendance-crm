package com.crm.realestate.data.repository

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.crm.realestate.data.models.BiometricResult
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for BiometricRepository fingerprint registration functionality
 */
class BiometricRepositoryFingerprintTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var biometricRepository: BiometricRepository
    private lateinit var activity: FragmentActivity
    private lateinit var biometricManager: BiometricManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        activity = mockk(relaxed = true)
        biometricManager = mockk(relaxed = true)
        
        // Mock context.packageManager
        every { context.packageManager } returns packageManager
        
        // Mock BiometricManager.from() static method
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(context) } returns biometricManager
        
        biometricRepository = BiometricRepository(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(BiometricManager::class)
    }

    @Test
    fun `checkBiometricAvailability should return correct fingerprint availability when hardware is available`() = runTest {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_SUCCESS
        every { context.packageManager.hasSystemFeature("android.hardware.camera.front") } returns true

        // When
        val result = biometricRepository.checkBiometricAvailability()

        // Then
        assertTrue("Should have fingerprint hardware", result.hasFingerprint)
        assertTrue("Should be able to authenticate with fingerprint", result.canAuthenticateFingerprint)
        assertTrue("Should have face detection", result.hasFaceDetection)
    }

    @Test
    fun `checkBiometricAvailability should return false when no fingerprint hardware`() = runTest {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        every { context.packageManager.hasSystemFeature("android.hardware.camera.front") } returns true

        // When
        val result = biometricRepository.checkBiometricAvailability()

        // Then
        assertFalse("Should not have fingerprint hardware", result.hasFingerprint)
        assertFalse("Should not be able to authenticate with fingerprint", result.canAuthenticateFingerprint)
    }

    @Test
    fun `checkBiometricAvailability should return true for hardware but false for authentication when no fingerprints enrolled`() = runTest {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        every { context.packageManager.hasSystemFeature("android.hardware.camera.front") } returns true

        // When
        val result = biometricRepository.checkBiometricAvailability()

        // Then
        assertTrue("Should have fingerprint hardware", result.hasFingerprint)
        assertFalse("Should not be able to authenticate without enrolled fingerprints", result.canAuthenticateFingerprint)
    }

    @Test
    fun `getFingerprintRegistrationStatus should return correct status for available hardware`() {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_SUCCESS

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertTrue("Should be available for registration", result.first)
        assertEquals("Should have correct status message", "Fingerprint hardware is ready for registration", result.second)
    }

    @Test
    fun `getFingerprintRegistrationStatus should return correct status for no hardware`() {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse("Should not be available for registration", result.first)
        assertEquals("Should have correct error message", "No fingerprint hardware available", result.second)
    }

    @Test
    fun `getFingerprintRegistrationStatus should return correct status for no enrolled fingerprints`() {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse("Should not be available for registration", result.first)
        assertEquals("Should have correct error message", "No fingerprints enrolled. Please add a fingerprint in device settings first", result.second)
    }

    @Test
    fun `getFingerprintRegistrationStatus should return correct status for hardware unavailable`() {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse("Should not be available for registration", result.first)
        assertEquals("Should have correct error message", "Fingerprint hardware is currently unavailable", result.second)
    }

    @Test
    fun `getFingerprintRegistrationStatus should return correct status for security update required`() {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse("Should not be available for registration", result.first)
        assertEquals("Should have correct error message", "Security update required for fingerprint authentication", result.second)
    }

    @Test
    fun `getFingerprintRegistrationStatus should return correct status for unsupported`() {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse("Should not be available for registration", result.first)
        assertEquals("Should have correct error message", "Fingerprint authentication is not supported", result.second)
    }

    @Test
    fun `getFingerprintRegistrationStatus should return correct status for unknown status`() {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_STATUS_UNKNOWN

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse("Should not be available for registration", result.first)
        assertEquals("Should have correct error message", "Fingerprint authentication status unknown", result.second)
    }
}