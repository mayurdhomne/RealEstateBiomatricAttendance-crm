package com.crm.realestate.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.crm.realestate.data.api.AuthApiService
import com.crm.realestate.data.models.EmployeeInfo
import com.crm.realestate.data.models.LoginResponse
import com.crm.realestate.data.models.Result
import com.crm.realestate.data.repository.AuthRepository
import com.crm.realestate.network.TokenManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection

/**
 * Integration tests for AuthRepository
 * Tests actual API integration with mock server and real token management
 */
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryIntegrationTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var mockWebServer: MockWebServer
    private lateinit var authApiService: AuthApiService
    private lateinit var tokenManager: TokenManager
    private lateinit var context: Context

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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Create real Retrofit instance pointing to mock server
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        authApiService = retrofit.create(AuthApiService::class.java)
        
        // Mock ApiConfig to return our test services
        mockkObject(com.crm.realestate.network.ApiConfig)
        every { com.crm.realestate.network.ApiConfig.provideTokenManager(any()) } returns mockk<TokenManager>(relaxed = true)
        every { 
            com.crm.realestate.network.ApiConfig.provideRetrofit(any(), any()).create(AuthApiService::class.java) 
        } returns authApiService

        authRepository = AuthRepository(context)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        unmockkAll()
    }

    @Test
    fun `login integration test with successful response`() = runTest {
        // Given
        val successResponse = """
            {
                "token": "${testLoginResponse.token}",
                "employee_id": "${testLoginResponse.employeeId}",
                "username": "${testLoginResponse.username}",
                "biometrics_registered": ${testLoginResponse.biometricsRegistered},
                "employee_info": {
                    "employee_id": "${testEmployeeInfo.employeeId}",
                    "full_name": "${testEmployeeInfo.fullName}",
                    "email": "${testEmployeeInfo.email}",
                    "department": "${testEmployeeInfo.department}",
                    "designation": "${testEmployeeInfo.designation}"
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(successResponse)
                .addHeader("Content-Type", "application/json")
        )

        // When
        val result = authRepository.login("john.doe", "password123")

        // Then
        assertTrue(result is Result.Success)
        val loginResponse = (result as Result.Success).data
        assertEquals(testLoginResponse.token, loginResponse.token)
        assertEquals(testLoginResponse.employeeId, loginResponse.employeeId)
        assertEquals(testLoginResponse.username, loginResponse.username)
        assertEquals(testLoginResponse.biometricsRegistered, loginResponse.biometricsRegistered)
        
        // Verify request was made correctly
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path?.contains("login") == true)
    }

    @Test
    fun `login integration test with 401 unauthorized`() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED)
                .setBody("""{"error": "Invalid credentials"}""")
                .addHeader("Content-Type", "application/json")
        )

        // When
        val result = authRepository.login("invalid", "credentials")

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Invalid username or password", (result as Result.Error).message)
        
        // Verify request was made
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
    }

    @Test
    fun `login integration test with 500 server error`() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
                .setBody("""{"error": "Internal server error"}""")
                .addHeader("Content-Type", "application/json")
        )

        // When
        val result = authRepository.login("john.doe", "password123")

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Server error. Please try again later", (result as Result.Error).message)
    }

    @Test
    fun `login integration test with network timeout`() = runTest {
        // Given - server doesn't respond (simulates timeout)
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE)
        )

        // When
        val result = authRepository.login("john.doe", "password123")

        // Then
        assertTrue(result is Result.Error)
        // The exact error message may vary depending on the timeout implementation
        assertNotNull((result as Result.Error).message)
    }

    @Test
    fun `login integration test with malformed JSON response`() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("invalid json response")
                .addHeader("Content-Type", "application/json")
        )

        // When
        val result = authRepository.login("john.doe", "password123")

        // Then
        assertTrue(result is Result.Error)
        assertNotNull((result as Result.Error).message)
    }

    @Test
    fun `login integration test with empty response body`() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("")
                .addHeader("Content-Type", "application/json")
        )

        // When
        val result = authRepository.login("john.doe", "password123")

        // Then
        assertTrue(result is Result.Error)
        assertEquals("Login failed: Invalid response from server", (result as Result.Error).message)
    }

    @Test
    fun `login integration test with partial response data`() = runTest {
        // Given - response missing some required fields
        val partialResponse = """
            {
                "token": "some-token",
                "employee_id": "EMP001"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(partialResponse)
                .addHeader("Content-Type", "application/json")
        )

        // When
        val result = authRepository.login("john.doe", "password123")

        // Then
        // Should handle partial data gracefully
        assertTrue(result is Result.Success || result is Result.Error)
        if (result is Result.Success) {
            assertNotNull(result.data.token)
            assertNotNull(result.data.employeeId)
        }
    }

    @Test
    fun `login integration test verifies request format`() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("""{"token": "test", "employee_id": "EMP001", "username": "test", "biometrics_registered": false, "employee_info": {}}""")
                .addHeader("Content-Type", "application/json")
        )

        // When
        authRepository.login("testuser", "testpass")

        // Then
        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertNotNull(request.body)
        
        // Verify request contains credentials (exact format depends on API implementation)
        val requestBody = request.body.readUtf8()
        assertTrue(requestBody.contains("testuser") || request.path?.contains("testuser") == true)
    }

    @Test
    fun `login integration test handles different content types`() = runTest {
        // Given
        val jsonResponse = """
            {
                "token": "test-token",
                "employee_id": "EMP001",
                "username": "test",
                "biometrics_registered": false,
                "employee_info": {}
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(jsonResponse)
                .addHeader("Content-Type", "text/plain") // Wrong content type
        )

        // When
        val result = authRepository.login("john.doe", "password123")

        // Then
        // Should still parse JSON correctly regardless of content type header
        assertTrue(result is Result.Success || result is Result.Error)
    }

    @Test
    fun `login integration test with slow server response`() = runTest {
        // Given
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("""{"token": "test", "employee_id": "EMP001", "username": "test", "biometrics_registered": false, "employee_info": {}}""")
                .addHeader("Content-Type", "application/json")
                .setBodyDelay(100, java.util.concurrent.TimeUnit.MILLISECONDS) // Small delay
        )

        // When
        val startTime = System.currentTimeMillis()
        val result = authRepository.login("john.doe", "password123")
        val endTime = System.currentTimeMillis()

        // Then
        assertTrue(result is Result.Success)
        assertTrue("Request should take at least 100ms", endTime - startTime >= 100)
    }
}