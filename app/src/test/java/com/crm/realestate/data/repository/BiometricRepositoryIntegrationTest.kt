package com.crm.realestate.data.repository

import android.content.Context
import com.crm.realestate.data.api.BiometricApiService
import com.crm.realestate.data.models.BiometricRegistrationRequest
import com.crm.realestate.data.models.BiometricRegistrationResponse
import com.crm.realestate.data.models.Result
import com.crm.realestate.network.ApiConfig
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Integration tests for BiometricRepository API integration
 * Tests the biometric registration API calls with various scenarios
 */
class BiometricRepositoryIntegrationTest {

    private lateinit var context: Context
    private lateinit var biometricRepository: BiometricRepository
    private lateinit var mockApiService: BiometricApiService

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        mockApiService = mockk()
        
        // Mock ApiConfig to return our mock service
        mockkObject(ApiConfig)
        val mockRetrofit = mockk<retrofit2.Retrofit>()
        every { ApiConfig.provideRetrofit(context, any()) } returns mockRetrofit
        every { mockRetrofit.create(BiometricApiService::class.java) } returns mockApiService
        
        biometricRepository = BiometricRepository(context)
    }

    @After
    fun tearDown() {
        unmockkObject(ApiConfig)
    }

    @Test
    fun `registerBiometrics should return success when API call succeeds`() = runTest {
        // Given
        val employeeId = "EMP001"
        val faceRegistered = true
        val fingerprintRegistered = true
        val expectedRequest = BiometricRegistrationRequest(
            employeeId = employeeId,
            faceRegistered = faceRegistered,
            fingerprintRegistered = fingerprintRegistered
        )
        val expectedResponse = BiometricRegistrationResponse(
            success = true,
            message = "Biometric registration completed successfully",
            employeeId = employeeId
        )
        val mockResponse = Response.success(expectedResponse)
        
        coEvery { mockApiService.registerBiometrics(expectedRequest) } returns mockResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, faceRegistered, fingerprintRegistered)

        // Then
        assertTrue("Result should be success", result is Result.Success<*>)
        val successResult = result as Result.Success<BiometricRegistrationResponse>
        assertEquals("Should return correct response", expectedResponse, successResult.data)
        
        // Verify API was called with correct parameters
        coVerify { mockApiService.registerBiometrics(expectedRequest) }
    }

    @Test
    fun `registerBiometrics should return success when only face is registered`() = runTest {
        // Given
        val employeeId = "EMP002"
        val faceRegistered = true
        val fingerprintRegistered = false
        val expectedRequest = BiometricRegistrationRequest(
            employeeId = employeeId,
            faceRegistered = faceRegistered,
            fingerprintRegistered = fingerprintRegistered
        )
        val expectedResponse = BiometricRegistrationResponse(
            success = true,
            message = "Face biometric registered successfully",
            employeeId = employeeId
        )
        val mockResponse = Response.success(expectedResponse)
        
        coEvery { mockApiService.registerBiometrics(expectedRequest) } returns mockResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, faceRegistered, fingerprintRegistered)

        // Then
        assertTrue("Result should be success", result is Result.Success<*>)
        val successResult = result as Result.Success<BiometricRegistrationResponse>
        assertEquals("Should return correct response", expectedResponse, successResult.data)
    }

    @Test
    fun `registerBiometrics should return success when only fingerprint is registered`() = runTest {
        // Given
        val employeeId = "EMP003"
        val faceRegistered = false
        val fingerprintRegistered = true
        val expectedRequest = BiometricRegistrationRequest(
            employeeId = employeeId,
            faceRegistered = faceRegistered,
            fingerprintRegistered = fingerprintRegistered
        )
        val expectedResponse = BiometricRegistrationResponse(
            success = true,
            message = "Fingerprint biometric registered successfully",
            employeeId = employeeId
        )
        val mockResponse = Response.success(expectedResponse)
        
        coEvery { mockApiService.registerBiometrics(expectedRequest) } returns mockResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, faceRegistered, fingerprintRegistered)

        // Then
        assertTrue("Result should be success", result is Result.Success<*>)
        val successResult = result as Result.Success<BiometricRegistrationResponse>
        assertEquals("Should return correct response", expectedResponse, successResult.data)
    }

    @Test
    fun `registerBiometrics should handle empty response body`() = runTest {
        // Given
        val employeeId = "EMP004"
        val mockResponse = Response.success<BiometricRegistrationResponse>(null)
        
        coEvery { mockApiService.registerBiometrics(any()) } returns mockResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Registration failed: Invalid response from server", errorResult.message)
        assertTrue("Should have correct exception type", errorResult.exception is Exception)
    }

    @Test
    fun `registerBiometrics should handle 400 Bad Request error`() = runTest {
        // Given
        val employeeId = "EMP005"
        val errorResponse = Response.error<BiometricRegistrationResponse>(
            400, 
            "Bad Request".toResponseBody()
        )
        
        coEvery { mockApiService.registerBiometrics(any()) } returns errorResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Invalid registration data", errorResult.message)
        assertTrue("Should have HttpException", errorResult.exception is HttpException)
    }

    @Test
    fun `registerBiometrics should handle 401 Unauthorized error`() = runTest {
        // Given
        val employeeId = "EMP006"
        val errorResponse = Response.error<BiometricRegistrationResponse>(
            401, 
            "Unauthorized".toResponseBody()
        )
        
        coEvery { mockApiService.registerBiometrics(any()) } returns errorResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Authentication required. Please login again", errorResult.message)
        assertTrue("Should have HttpException", errorResult.exception is HttpException)
    }

    @Test
    fun `registerBiometrics should handle 403 Forbidden error`() = runTest {
        // Given
        val employeeId = "EMP007"
        val errorResponse = Response.error<BiometricRegistrationResponse>(
            403, 
            "Forbidden".toResponseBody()
        )
        
        coEvery { mockApiService.registerBiometrics(any()) } returns errorResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Access denied for biometric registration", errorResult.message)
    }

    @Test
    fun `registerBiometrics should handle 404 Not Found error`() = runTest {
        // Given
        val employeeId = "EMP008"
        val errorResponse = Response.error<BiometricRegistrationResponse>(
            404, 
            "Not Found".toResponseBody()
        )
        
        coEvery { mockApiService.registerBiometrics(any()) } returns errorResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Registration service not available", errorResult.message)
    }

    @Test
    fun `registerBiometrics should handle 409 Conflict error`() = runTest {
        // Given
        val employeeId = "EMP009"
        val errorResponse = Response.error<BiometricRegistrationResponse>(
            409, 
            "Conflict".toResponseBody()
        )
        
        coEvery { mockApiService.registerBiometrics(any()) } returns errorResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Biometrics already registered for this employee", errorResult.message)
    }

    @Test
    fun `registerBiometrics should handle 422 Unprocessable Entity error`() = runTest {
        // Given
        val employeeId = "EMP010"
        val errorResponse = Response.error<BiometricRegistrationResponse>(
            422, 
            "Unprocessable Entity".toResponseBody()
        )
        
        coEvery { mockApiService.registerBiometrics(any()) } returns errorResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Invalid biometric data format", errorResult.message)
    }

    @Test
    fun `registerBiometrics should handle 500 Internal Server Error`() = runTest {
        // Given
        val employeeId = "EMP011"
        val errorResponse = Response.error<BiometricRegistrationResponse>(
            500, 
            "Internal Server Error".toResponseBody()
        )
        
        coEvery { mockApiService.registerBiometrics(any()) } returns errorResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Server error. Please try again later", errorResult.message)
    }

    @Test
    fun `registerBiometrics should handle unknown HTTP error codes`() = runTest {
        // Given
        val employeeId = "EMP012"
        val errorResponse = Response.error<BiometricRegistrationResponse>(
            418, 
            "I'm a teapot".toResponseBody()
        )
        
        coEvery { mockApiService.registerBiometrics(any()) } returns errorResponse

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Registration failed: Response.error()", errorResult.message)
    }

    @Test
    fun `registerBiometrics should handle network connectivity issues`() = runTest {
        // Given
        val employeeId = "EMP013"
        
        coEvery { mockApiService.registerBiometrics(any()) } throws UnknownHostException("Unable to resolve host")

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "No internet connection. Please check your network and try again", errorResult.message)
        assertTrue("Should have UnknownHostException", errorResult.exception is UnknownHostException)
    }

    @Test
    fun `registerBiometrics should handle connection timeout`() = runTest {
        // Given
        val employeeId = "EMP014"
        
        coEvery { mockApiService.registerBiometrics(any()) } throws SocketTimeoutException("Connection timed out")

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Connection timeout. Please try again", errorResult.message)
        assertTrue("Should have SocketTimeoutException", errorResult.exception is SocketTimeoutException)
    }

    @Test
    fun `registerBiometrics should handle general IO exceptions`() = runTest {
        // Given
        val employeeId = "EMP015"
        
        coEvery { mockApiService.registerBiometrics(any()) } throws IOException("Network error")

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Network error. Please check your connection and try again", errorResult.message)
        assertTrue("Should have IOException", errorResult.exception is IOException)
    }

    @Test
    fun `registerBiometrics should handle unexpected exceptions`() = runTest {
        // Given
        val employeeId = "EMP016"
        val unexpectedException = RuntimeException("Unexpected error")
        
        coEvery { mockApiService.registerBiometrics(any()) } throws unexpectedException

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Registration failed: Unexpected error", errorResult.message)
        assertEquals("Should have the original exception", unexpectedException, errorResult.exception)
    }

    @Test
    fun `registerBiometrics should handle exception with null message`() = runTest {
        // Given
        val employeeId = "EMP017"
        val exceptionWithNullMessage = RuntimeException(null as String?)
        
        coEvery { mockApiService.registerBiometrics(any()) } throws exceptionWithNullMessage

        // When
        val result = biometricRepository.registerBiometrics(employeeId, true, true)

        // Then
        assertTrue("Result should be error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Should have correct error message", "Registration failed: Unknown error occurred", errorResult.message)
        assertEquals("Should have the original exception", exceptionWithNullMessage, errorResult.exception)
    }

    @Test
    fun `registerBiometrics should create correct request payload for all combinations`() = runTest {
        // Test all possible combinations of biometric registration
        val testCases = listOf(
            Triple("EMP018", true, true),   // Both registered
            Triple("EMP019", true, false),  // Only face registered
            Triple("EMP020", false, true),  // Only fingerprint registered
            Triple("EMP021", false, false)  // Neither registered (edge case)
        )
        
        testCases.forEach { (employeeId, faceRegistered, fingerprintRegistered) ->
            // Given
            val expectedRequest = BiometricRegistrationRequest(
                employeeId = employeeId,
                faceRegistered = faceRegistered,
                fingerprintRegistered = fingerprintRegistered
            )
            val mockResponse = Response.success(BiometricRegistrationResponse(
                success = true,
                message = "Registration completed",
                employeeId = employeeId
            ))
            
            coEvery { mockApiService.registerBiometrics(expectedRequest) } returns mockResponse

            // When
            val result = biometricRepository.registerBiometrics(employeeId, faceRegistered, fingerprintRegistered)

            // Then
            assertTrue("Result should be success for $employeeId", result is Result.Success<*>)
            
            // Verify the exact request was made
            coVerify { mockApiService.registerBiometrics(expectedRequest) }
        }
    }

    @Test
    fun `registerBiometrics should handle concurrent API calls correctly`() = runTest {
        // Given
        val employeeIds = listOf("EMP022", "EMP023", "EMP024")
        val responses = employeeIds.map { employeeId ->
            Response.success(BiometricRegistrationResponse(
                success = true,
                message = "Registration completed",
                employeeId = employeeId
            ))
        }
        
        // Mock different responses for different employee IDs
        employeeIds.forEachIndexed { index, employeeId ->
            val request = BiometricRegistrationRequest(
                employeeId = employeeId,
                faceRegistered = true,
                fingerprintRegistered = true
            )
            coEvery { mockApiService.registerBiometrics(request) } returns responses[index]
        }

        // When - Make concurrent calls
        val results = employeeIds.map { employeeId ->
            biometricRepository.registerBiometrics(employeeId, true, true)
        }

        // Then
        results.forEach { result ->
            assertTrue("All results should be successful", result is Result.Success<*>)
        }
        
        // Verify all API calls were made
        employeeIds.forEach { employeeId ->
            val expectedRequest = BiometricRegistrationRequest(
                employeeId = employeeId,
                faceRegistered = true,
                fingerprintRegistered = true
            )
            coVerify { mockApiService.registerBiometrics(expectedRequest) }
        }
    }
}