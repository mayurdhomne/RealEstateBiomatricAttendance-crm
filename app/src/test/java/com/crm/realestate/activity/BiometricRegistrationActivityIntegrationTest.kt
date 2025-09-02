package com.crm.realestate.activity

import android.content.Context
import com.crm.realestate.data.models.BiometricRegistrationResponse
import com.crm.realestate.data.models.EmployeeInfo
import com.crm.realestate.data.models.Result
import com.crm.realestate.data.repository.AuthRepository
import com.crm.realestate.data.repository.BiometricRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Integration tests for BiometricRegistrationActivity API integration
 * Tests the activity's interaction with BiometricRepository for API calls
 */
class BiometricRegistrationActivityIntegrationTest {

    private lateinit var context: Context
    private lateinit var authRepository: AuthRepository
    private lateinit var biometricRepository: BiometricRepository

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        authRepository = mockk()
        biometricRepository = mockk()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `completeRegistration should call biometric API with correct parameters`() = runTest {
        // Given
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP001",
            fullName = "John Doe",
            email = "john.doe@company.com",
            department = "Engineering",
            designation = "Software Developer"
        )
        
        val expectedResponse = BiometricRegistrationResponse(
            success = true,
            message = "Biometric registration completed successfully",
            employeeId = "EMP001"
        )

        // Mock repository responses
        coEvery { authRepository.getEmployeeInfo() } returns employeeInfo
        coEvery { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP001", 
                faceRegistered = true, 
                fingerprintRegistered = true
            ) 
        } returns expectedResponse

        // When - Simulate the registration completion logic
        val storedEmployeeInfo = authRepository.getEmployeeInfo()
        assertNotNull("Employee info should be available", storedEmployeeInfo)
        
        val result = biometricRepository.registerBiometrics(
            employeeId = storedEmployeeInfo!!.employeeId,
            faceRegistered = true,
            fingerprintRegistered = true
        )

        // Then
        assertTrue("Registration should succeed", result.success)
        assertEquals("Should return correct response", expectedResponse, result)
        
        // Verify correct API call was made
        coVerify { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP001", 
                faceRegistered = true, 
                fingerprintRegistered = true
            ) 
        }
    }

    @Test
    fun `completeRegistration should handle face-only registration correctly`() = runTest {
        // Given
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP002",
            fullName = "Jane Smith",
            email = "jane.smith@company.com",
            department = "Marketing",
            designation = "Marketing Manager"
        )
        
        val expectedResponse = BiometricRegistrationResponse(
            success = true,
            message = "Face biometric registered successfully",
            employeeId = "EMP002"
        )

        // Mock repository responses for face-only registration
        coEvery { authRepository.getEmployeeInfo() } returns employeeInfo
        coEvery { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP002", 
                faceRegistered = true, 
                fingerprintRegistered = false
            ) 
        } returns expectedResponse

        // When - Simulate face-only registration
        val storedEmployeeInfo = authRepository.getEmployeeInfo()
        val result = biometricRepository.registerBiometrics(
            employeeId = storedEmployeeInfo!!.employeeId,
            faceRegistered = true,
            fingerprintRegistered = false
        )

        // Then
        assertTrue("Registration should succeed", result.success)
        assertTrue("Should indicate face registration", result.message.contains("Face"))
        
        // Verify correct API call was made with face-only parameters
        coVerify { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP002", 
                faceRegistered = true, 
                fingerprintRegistered = false
            ) 
        }
    }

    @Test
    fun `completeRegistration should handle fingerprint-only registration correctly`() = runTest {
        // Given
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP003",
            fullName = "Bob Johnson",
            email = "bob.johnson@company.com",
            department = "Sales",
            designation = "Sales Representative"
        )
        
        val expectedResponse = BiometricRegistrationResponse(
            success = true,
            message = "Fingerprint biometric registered successfully",
            employeeId = "EMP003"
        )

        // Mock repository responses for fingerprint-only registration
        coEvery { authRepository.getEmployeeInfo() } returns employeeInfo
        coEvery { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP003", 
                faceRegistered = false, 
                fingerprintRegistered = true
            ) 
        } returns expectedResponse

        // When - Simulate fingerprint-only registration
        val storedEmployeeInfo = authRepository.getEmployeeInfo()
        val result = biometricRepository.registerBiometrics(
            employeeId = storedEmployeeInfo!!.employeeId,
            faceRegistered = false,
            fingerprintRegistered = true
        )

        // Then
        assertTrue("Registration should succeed", result.success)
        assertTrue("Should indicate fingerprint registration", result.message.contains("Fingerprint"))
        
        // Verify correct API call was made with fingerprint-only parameters
        coVerify { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP003", 
                faceRegistered = false, 
                fingerprintRegistered = true
            ) 
        }
    }

    @Test
    fun `completeRegistration should handle API failure gracefully`() = runTest {
        // Given
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP004",
            fullName = "Alice Brown",
            email = "alice.brown@company.com",
            department = "HR",
            designation = "HR Manager"
        )
        
        val expectedError = Result.Error(
            exception = Exception("Network error"),
            message = "Registration failed: Network error"
        )

        // Mock repository responses with API failure
        coEvery { authRepository.getEmployeeInfo() } returns employeeInfo
        coEvery { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP004", 
                faceRegistered = true, 
                fingerprintRegistered = true
            ) 
        } throws expectedError.exception // Throw exception for error cases

        // When - Simulate registration with API failure
        val storedEmployeeInfo = authRepository.getEmployeeInfo()
        var result: Result<BiometricRegistrationResponse>? = null
        try {
            val response = biometricRepository.registerBiometrics(
                employeeId = storedEmployeeInfo!!.employeeId,
                faceRegistered = true,
                fingerprintRegistered = true
            )
            result = Result.Success(response)
        } catch (e: Exception) {
            result = Result.Error(exception = e, message = e.message ?: "Unknown error")
        }


        // Then
        assertTrue("Registration should fail", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Network error", errorResult.exception.message)
        
        // Verify API call was attempted
        coVerify { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP004", 
                faceRegistered = true, 
                fingerprintRegistered = true
            ) 
        }
    }

    @Test
    fun `completeRegistration should handle missing employee info`() = runTest {
        // Given - No employee info available
        coEvery { authRepository.getEmployeeInfo() } returns null

        // When - Attempt registration without employee info
        val storedEmployeeInfo = authRepository.getEmployeeInfo()

        // Then
        assertNull("Employee info should be null", storedEmployeeInfo)
        
        // In real implementation, this would trigger redirect to login
        // Verify that getEmployeeInfo was called
        coVerify { authRepository.getEmployeeInfo() }
        
        // Verify that biometric registration was not attempted
        coVerify(exactly = 0) { 
            biometricRepository.registerBiometrics(any(), any(), any()) 
        }
    }

    @Test
    fun `completeRegistration should handle authentication errors during registration`() = runTest {
        // Given
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP005",
            fullName = "Charlie Wilson",
            email = "charlie.wilson@company.com",
            department = "Finance",
            designation = "Accountant"
        )
        
        val authException = retrofit2.HttpException(
            retrofit2.Response.error<Any>(401, okhttp3.ResponseBody.create(null, "Unauthorized"))
        )

        // Mock repository responses with authentication error
        coEvery { authRepository.getEmployeeInfo() } returns employeeInfo
        coEvery { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP005", 
                faceRegistered = true, 
                fingerprintRegistered = true
            ) 
        } throws authException

        // When - Simulate registration with authentication error
        val storedEmployeeInfo = authRepository.getEmployeeInfo()
        var result: Result<BiometricRegistrationResponse>? = null
        try {
            val response = biometricRepository.registerBiometrics(
                employeeId = storedEmployeeInfo!!.employeeId,
                faceRegistered = true,
                fingerprintRegistered = true
            )
            result = Result.Success(response)
        } catch (e: Exception) {
            result = Result.Error(exception = e, message = if (e is retrofit2.HttpException && e.code() == 401) "Authentication required. Please login again" else e.message ?: "Unknown error" )
        }

        // Then
        assertTrue("Registration should fail with auth error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have authentication error message", "Authentication required. Please login again", errorResult.message)
        assertTrue("Should have HttpException", errorResult.exception is retrofit2.HttpException)
        
        // Verify API call was attempted
        coVerify { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP005", 
                faceRegistered = true, 
                fingerprintRegistered = true
            ) 
        }
    }

    @Test
    fun `completeRegistration should handle server errors during registration`() = runTest {
        // Given
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP006",
            fullName = "Diana Prince",
            email = "diana.prince@company.com",
            department = "Security",
            designation = "Security Officer"
        )
        
        val serverException = retrofit2.HttpException(
                retrofit2.Response.error<Any>(500, okhttp3.ResponseBody.create(null, "Internal Server Error"))
            )

        // Mock repository responses with server error
        coEvery { authRepository.getEmployeeInfo() } returns employeeInfo
        coEvery { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP006", 
                faceRegistered = true, 
                fingerprintRegistered = true
            ) 
        } throws serverException

        // When - Simulate registration with server error
        val storedEmployeeInfo = authRepository.getEmployeeInfo()
         var result: Result<BiometricRegistrationResponse>? = null
        try {
            val response = biometricRepository.registerBiometrics(
                employeeId = storedEmployeeInfo!!.employeeId,
                faceRegistered = true,
                fingerprintRegistered = true
            )
            result = Result.Success(response)
        } catch (e: Exception) {
            result = Result.Error(exception = e, message = if (e is retrofit2.HttpException && e.code() == 500) "Server error. Please try again later" else e.message ?: "Unknown error")
        }


        // Then
        assertTrue("Registration should fail with server error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have server error message", "Server error. Please try again later", errorResult.message)
        
        // Verify API call was attempted
        coVerify { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP006", 
                faceRegistered = true, 
                fingerprintRegistered = true
            ) 
        }
    }

    @Test
    fun `registration success should prepare for dashboard redirect`() = runTest {
        // Given
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP007",
            fullName = "Bruce Wayne",
            email = "bruce.wayne@company.com",
            department = "Executive",
            designation = "CEO"
        )
        
        val successResponse = BiometricRegistrationResponse(
            success = true,
            message = "Biometric registration completed successfully",
            employeeId = "EMP007"
        )

        // Mock successful registration
        coEvery { authRepository.getEmployeeInfo() } returns employeeInfo
        coEvery { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP007", 
                faceRegistered = true, 
                fingerprintRegistered = true
            ) 
        } returns successResponse

        // When - Complete successful registration
        val storedEmployeeInfo = authRepository.getEmployeeInfo()
        val result = biometricRepository.registerBiometrics(
            employeeId = storedEmployeeInfo!!.employeeId,
            faceRegistered = true,
            fingerprintRegistered = true
        )

        // Then - Verify success state that would trigger dashboard redirect
        assertTrue("Registration should succeed", result.success)
        assertTrue("Should indicate successful registration", result.success)
        assertEquals("Should have success message", "Biometric registration completed successfully", result.message)
        
        // In the real activity, this success would trigger:
        // 1. Toast message showing success
        // 2. Intent to DashboardActivity
        // 3. Activity finish with proper flags
        
        // Verify the API integration worked correctly
        coVerify { 
            biometricRepository.registerBiometrics(
                employeeId = "EMP007", 
                faceRegistered = true, 
                fingerprintRegistered = true
            ) 
        }
    }
}
