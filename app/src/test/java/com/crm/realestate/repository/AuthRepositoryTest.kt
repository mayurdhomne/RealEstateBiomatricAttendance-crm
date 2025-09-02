package com.crm.realestate.repository

import android.content.Context
import com.crm.realestate.data.api.AuthApiService
import com.crm.realestate.data.api.dto.NetworkLoginResponse
import com.crm.realestate.data.models.EmployeeInfo
import com.crm.realestate.data.models.LoginResponse
import com.crm.realestate.data.models.Result
import com.crm.realestate.data.repository.AuthRepository
import com.crm.realestate.network.TokenManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
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
 * Unit tests for AuthRepository
 * Tests authentication operations, token management, and error handling
 */
class AuthRepositoryTest {

    private lateinit var authRepository: AuthRepository
    private val mockContext = mockk<Context>()
    private val mockTokenManager = mockk<TokenManager>()
    private val mockAuthApiService = mockk<AuthApiService>()

    private val testEmployeeInfo = EmployeeInfo(
        employeeId = "EMP001",
        fullName = "John Doe",
        email = "john.doe@company.com",
        department = "IT",
        designation = "Software Engineer"
    )

    private val testLoginResponse = LoginResponse(
        token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test",
        employeeId = "EMP001",
        username = "john.doe",
        biometricsRegistered = false,
        employeeInfo = testEmployeeInfo
    )

    private val testNetworkLoginResponse = NetworkLoginResponse(
        refresh = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.refresh",
        access = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.access",
        employeeId = "EMP001",
        name = "John Doe"
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        // Mock ApiConfig to return our mocked services
        mockkObject(com.crm.realestate.network.ApiConfig)
        every { com.crm.realestate.network.ApiConfig.provideTokenManager(any()) } returns mockTokenManager
        every { 
            com.crm.realestate.network.ApiConfig.provideRetrofit(any(), any()).create(AuthApiService::class.java) 
        } returns mockAuthApiService

        authRepository = AuthRepository(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `login with valid credentials returns success`() = runTest {
        // Given
        val username = "john.doe"
        val password = "password123"
        val mockResponse = mockk<Response<NetworkLoginResponse>>()
        
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body() } returns testNetworkLoginResponse
        coEvery { mockAuthApiService.login(username, password) } returns mockResponse
        coEvery { mockTokenManager.saveToken(any()) } just awaits
        coEvery { mockTokenManager.saveEmployeeInfo(any()) } just awaits

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result is Result.Success)
        assertEquals(testLoginResponse, (result as Result.Success).data)
        coVerify { mockTokenManager.saveToken(testLoginResponse.token) }
        coVerify { mockTokenManager.saveEmployeeInfo(testLoginResponse.employeeInfo) }
    }

    @Test
    fun `login with invalid credentials returns error`() = runTest {
        // Given
        val username = "invalid"
        val password = "wrong"
        val mockResponse = mockk<Response<NetworkLoginResponse>>()
        
        every { mockResponse.isSuccessful } returns false
        every { mockResponse.code() } returns 401
        every { mockResponse.message() } returns "Unauthorized"
        coEvery { mockAuthApiService.login(username, password) } returns mockResponse

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Invalid username or password", (result as Result.Error).message)
    }

    @Test
    fun `login with network error returns appropriate error`() = runTest {
        // Given
        val username = "john.doe"
        val password = "password123"
        
        coEvery { mockAuthApiService.login(username, password) } throws UnknownHostException()

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message?.contains("No internet connection") == true)
    }

    @Test
    fun `login with timeout error returns appropriate error`() = runTest {
        // Given
        val username = "john.doe"
        val password = "password123"
        
        coEvery { mockAuthApiService.login(username, password) } throws SocketTimeoutException()

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message?.contains("Connection timeout") == true)
    }

    @Test
    fun `login with IO error returns appropriate error`() = runTest {
        // Given
        val username = "john.doe"
        val password = "password123"
        
        coEvery { mockAuthApiService.login(username, password) } throws IOException("Network error")

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message?.contains("Network error") == true)
    }

    @Test
    fun `login with empty response body returns error`() = runTest {
        // Given
        val username = "john.doe"
        val password = "password123"
        val mockResponse = mockk<Response<NetworkLoginResponse>>()
        
        every { mockResponse.isSuccessful } returns true
        every { mockResponse.body() } returns null
        coEvery { mockAuthApiService.login(username, password) } returns mockResponse

        // When
        val result = authRepository.login(username, password)

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Login failed: Invalid response from server", (result as Result.Error).message)
    }

    @Test
    fun `validateCredentials with valid input returns true`() {
        // Given
        val username = "john.doe"
        val password = "password123"

        // When
        val result = authRepository.validateCredentials(username, password)

        // Then
        assertTrue(result.first)
        assertNull(result.second)
    }

    @Test
    fun `validateCredentials with blank username returns false`() {
        // Given
        val username = ""
        val password = "password123"

        // When
        val result = authRepository.validateCredentials(username, password)

        // Then
        assertFalse(result.first)
        assertEquals("Username is required", result.second)
    }

    @Test
    fun `validateCredentials with blank password returns false`() {
        // Given
        val username = "john.doe"
        val password = ""

        // When
        val result = authRepository.validateCredentials(username, password)

        // Then
        assertFalse(result.first)
        assertEquals("Password is required", result.second)
    }

    @Test
    fun `validateCredentials with short username returns false`() {
        // Given
        val username = "jo"
        val password = "password123"

        // When
        val result = authRepository.validateCredentials(username, password)

        // Then
        assertFalse(result.first)
        assertEquals("Username must be at least 3 characters", result.second)
    }

    @Test
    fun `validateCredentials with short password returns false`() {
        // Given
        val username = "john.doe"
        val password = "12345"

        // When
        val result = authRepository.validateCredentials(username, password)

        // Then
        assertFalse(result.first)
        assertEquals("Password must be at least 6 characters", result.second)
    }

    @Test
    fun `saveAuthToken calls tokenManager saveToken`() = runTest {
        // Given
        val token = "test-token"
        coEvery { mockTokenManager.saveToken(token) } just awaits

        // When
        authRepository.saveAuthToken(token)

        // Then
        coVerify { mockTokenManager.saveToken(token) }
    }

    @Test
    fun `getAuthToken returns token from tokenManager`() = runTest {
        // Given
        val expectedToken = "test-token"
        coEvery { mockTokenManager.getToken() } returns expectedToken

        // When
        val result = authRepository.getAuthToken()

        // Then
        assertEquals(expectedToken, result)
        coVerify { mockTokenManager.getToken() }
    }

    @Test
    fun `isTokenValid returns tokenManager result`() = runTest {
        // Given
        coEvery { mockTokenManager.isTokenValid() } returns true

        // When
        val result = authRepository.isTokenValid()

        // Then
        assertTrue(result)
        coVerify { mockTokenManager.isTokenValid() }
    }

    @Test
    fun `isLoggedIn returns tokenManager result`() = runTest {
        // Given
        coEvery { mockTokenManager.isLoggedIn() } returns true

        // When
        val result = authRepository.isLoggedIn()

        // Then
        assertTrue(result)
        coVerify { mockTokenManager.isLoggedIn() }
    }

    @Test
    fun `logout calls tokenManager logout`() = runTest {
        // Given
        coEvery { mockTokenManager.logout() } just awaits

        // When
        authRepository.logout()

        // Then
        coVerify { mockTokenManager.logout() }
    }

    @Test
    fun `saveEmployeeInfo calls tokenManager saveEmployeeInfo`() = runTest {
        // Given
        coEvery { mockTokenManager.saveEmployeeInfo(testEmployeeInfo) } just awaits

        // When
        authRepository.saveEmployeeInfo(testEmployeeInfo)

        // Then
        coVerify { mockTokenManager.saveEmployeeInfo(testEmployeeInfo) }
    }

    @Test
    fun `getEmployeeInfo returns tokenManager result`() = runTest {
        // Given
        coEvery { mockTokenManager.getEmployeeInfo() } returns testEmployeeInfo

        // When
        val result = authRepository.getEmployeeInfo()

        // Then
        assertEquals(testEmployeeInfo, result)
        coVerify { mockTokenManager.getEmployeeInfo() }
    }

    @Test
    fun `refreshToken returns unsupported operation error`() = runTest {
        // When
        val result = authRepository.refreshToken()

        // Then
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message?.contains("Token refresh is not currently supported") == true)
    }

    @Test
    fun `login handles different HTTP error codes correctly`() = runTest {
        val testCases = listOf(
            401 to "Invalid username or password",
            403 to "Account access denied",
            404 to "Login service not available",
            422 to "Invalid login credentials format",
            500 to "Server error. Please try again later"
        )

        testCases.forEach { (errorCode, expectedMessage) ->
            // Given
            val mockResponse = mockk<Response<NetworkLoginResponse>>()
            every { mockResponse.isSuccessful } returns false
            every { mockResponse.code() } returns errorCode
            every { mockResponse.message() } returns "HTTP $errorCode"
            coEvery { mockAuthApiService.login(any(), any()) } returns mockResponse

            // When
            val result = authRepository.login("test", "test")

            // Then
            assertTrue("Failed for error code $errorCode", result is Result.Error)
            assertEquals("Failed for error code $errorCode", expectedMessage, (result as Result.Error).message)
        }
    }
}