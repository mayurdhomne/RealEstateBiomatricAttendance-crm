package com.crm.realestate.activity

import android.content.Context
import androidx.biometric.BiometricManager
import com.crm.realestate.data.models.BiometricAvailability
import com.crm.realestate.data.repository.BiometricRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Integration tests for fingerprint registration functionality in BiometricRegistrationActivity
 */
class BiometricRegistrationFingerprintIntegrationTest {

    private lateinit var context: Context
    private lateinit var biometricRepository: BiometricRepository
    private lateinit var biometricManager: BiometricManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        biometricManager = mockk(relaxed = true)
        
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
    fun `fingerprint registration status should be available when hardware is ready`() {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_SUCCESS

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertTrue("Fingerprint should be available for registration", result.first)
        assertEquals("Should have correct status message", "Fingerprint hardware is ready for registration", result.second)
    }

    @Test
    fun `fingerprint registration status should not be available when no hardware`() {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse("Fingerprint should not be available for registration", result.first)
        assertEquals("Should have correct error message", "No fingerprint hardware available", result.second)
    }

    @Test
    fun `fingerprint registration status should not be available when no fingerprints enrolled`() {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        // When
        val result = biometricRepository.getFingerprintRegistrationStatus()

        // Then
        assertFalse("Fingerprint should not be available for registration", result.first)
        assertTrue("Should mention fingerprint enrollment", result.second.contains("No fingerprints enrolled"))
    }

    @Test
    fun `biometric availability should correctly identify fingerprint hardware status`() = runTest {
        // Given - Hardware available but no fingerprints enrolled
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        every { context.packageManager.hasSystemFeature("android.hardware.camera.front") } returns true

        // When
        val availability = biometricRepository.checkBiometricAvailability()

        // Then
        assertTrue("Should have fingerprint hardware", availability.hasFingerprint)
        assertFalse("Should not be able to authenticate without enrolled fingerprints", availability.canAuthenticateFingerprint)
        assertTrue("Should have face detection", availability.hasFaceDetection)
    }

    @Test
    fun `biometric availability should handle no fingerprint hardware correctly`() = runTest {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        every { context.packageManager.hasSystemFeature("android.hardware.camera.front") } returns true

        // When
        val availability = biometricRepository.checkBiometricAvailability()

        // Then
        assertFalse("Should not have fingerprint hardware", availability.hasFingerprint)
        assertFalse("Should not be able to authenticate", availability.canAuthenticateFingerprint)
        assertTrue("Should have face detection", availability.hasFaceDetection)
    }

    @Test
    fun `biometric availability should handle fingerprint hardware unavailable correctly`() = runTest {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
        every { context.packageManager.hasSystemFeature("android.hardware.camera.front") } returns true

        // When
        val availability = biometricRepository.checkBiometricAvailability()

        // Then
        assertFalse("Should not have available fingerprint hardware", availability.hasFingerprint)
        assertFalse("Should not be able to authenticate", availability.canAuthenticateFingerprint)
        assertTrue("Should have face detection", availability.hasFaceDetection)
    }

    @Test
    fun `biometric availability should handle security update required correctly`() = runTest {
        // Given
        every { biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) } returns BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
        every { context.packageManager.hasSystemFeature("android.hardware.camera.front") } returns true

        // When
        val availability = biometricRepository.checkBiometricAvailability()

        // Then
        assertTrue("Should have fingerprint hardware (needs update)", availability.hasFingerprint)
        assertFalse("Should not be able to authenticate without update", availability.canAuthenticateFingerprint)
        assertTrue("Should have face detection", availability.hasFaceDetection)
    }
}