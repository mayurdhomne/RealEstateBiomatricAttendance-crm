package com.crm.realestate.data.models

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for biometric-related data models
 * Tests JSON serialization/deserialization and model behavior
 */
class BiometricModelsTest {
    
    private val gson = Gson()
    
    @Test
    fun `test BiometricRegistrationRequest serialization`() {
        val request = BiometricRegistrationRequest(
            employeeId = "EMP001",
            faceRegistered = true,
            fingerprintRegistered = false
        )
        
        val json = gson.toJson(request)
        assertNotNull(json)
        assertTrue(json.contains("\"employee_id\""))
        assertTrue(json.contains("\"face_registered\""))
        assertTrue(json.contains("\"fingerprint_registered\""))
        assertTrue(json.contains("true"))
        assertTrue(json.contains("false"))
    }
    
    @Test
    fun `test BiometricRegistrationRequest deserialization`() {
        val json = """
            {
                "employee_id": "EMP001",
                "face_registered": true,
                "fingerprint_registered": true
            }
        """.trimIndent()
        
        val request = gson.fromJson(json, BiometricRegistrationRequest::class.java)
        
        assertNotNull(request)
        assertEquals("EMP001", request.employeeId)
        assertTrue(request.faceRegistered)
        assertTrue(request.fingerprintRegistered)
    }
    
    @Test
    fun `test BiometricRegistrationResponse serialization`() {
        val response = BiometricRegistrationResponse(
            success = true,
            message = "Biometrics registered successfully",
            employeeId = "EMP001"
        )
        
        val json = gson.toJson(response)
        assertNotNull(json)
        assertTrue(json.contains("\"success\""))
        assertTrue(json.contains("\"message\""))
        assertTrue(json.contains("\"employee_id\""))
    }
    
    @Test
    fun `test BiometricRegistrationResponse deserialization`() {
        val json = """
            {
                "success": true,
                "message": "Biometrics registered successfully",
                "employee_id": "EMP001"
            }
        """.trimIndent()
        
        val response = gson.fromJson(json, BiometricRegistrationResponse::class.java)
        
        assertNotNull(response)
        assertTrue(response.success)
        assertEquals("Biometrics registered successfully", response.message)
        assertEquals("EMP001", response.employeeId)
    }
    
    @Test
    fun `test BiometricAvailability model`() {
        val availability = BiometricAvailability(
            hasFaceDetection = true,
            hasFingerprint = false,
            canAuthenticateFingerprint = false
        )
        
        assertTrue(availability.hasFaceDetection)
        assertFalse(availability.hasFingerprint)
        assertFalse(availability.canAuthenticateFingerprint)
    }
    
    @Test
    fun `test BiometricResult sealed class`() {
        val successResult = BiometricResult.Success
        val errorResult = BiometricResult.Error("Authentication failed", 401)
        val cancelledResult = BiometricResult.Cancelled
        val failedResult = BiometricResult.Failed
        
        assertTrue(successResult is BiometricResult.Success)
        assertTrue(errorResult is BiometricResult.Error)
        assertTrue(cancelledResult is BiometricResult.Cancelled)
        assertTrue(failedResult is BiometricResult.Failed)
        
        // Test error result properties
        assertEquals("Authentication failed", errorResult.message)
        assertEquals(401, errorResult.errorCode)
    }
    
    @Test
    fun `test BiometricResult error without error code`() {
        val errorResult = BiometricResult.Error("Generic error")
        
        assertEquals("Generic error", errorResult.message)
        assertNull(errorResult.errorCode)
    }
    
    @Test
    fun `test BiometricRegistrationRequest with both biometrics false`() {
        val request = BiometricRegistrationRequest(
            employeeId = "EMP002",
            faceRegistered = false,
            fingerprintRegistered = false
        )
        
        assertEquals("EMP002", request.employeeId)
        assertFalse(request.faceRegistered)
        assertFalse(request.fingerprintRegistered)
    }
    
    @Test
    fun `test BiometricRegistrationResponse failure case`() {
        val json = """
            {
                "success": false,
                "message": "Biometric registration failed",
                "employee_id": "EMP002"
            }
        """.trimIndent()
        
        val response = gson.fromJson(json, BiometricRegistrationResponse::class.java)
        
        assertNotNull(response)
        assertFalse(response.success)
        assertEquals("Biometric registration failed", response.message)
        assertEquals("EMP002", response.employeeId)
    }
}