package com.crm.realestate.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.crm.realestate.data.models.EmployeeInfo
import com.google.gson.Gson
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import android.util.Base64

/**
 * Unit tests for TokenManager
 * Tests secure token storage, validation, and employee information management
 */
class TokenManagerTest {

    private lateinit var tokenManager: TokenManager
    private val mockContext = mockk<Context>()
    private val mockEncryptedPrefs = mockk<EncryptedSharedPreferences>()
    private val mockEditor = mockk<SharedPreferences.Editor>()
    private val mockMasterKey = mockk<MasterKey>()

    private val testEmployeeInfo = EmployeeInfo(
        employeeId = "EMP001",
        fullName = "John Doe",
        email = "john.doe@company.com",
        department = "IT",
        designation = "Software Engineer"
    )

    // Valid JWT token with exp claim (expires in year 2030)
    private val validJwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            Base64.encodeToString(
                JSONObject().apply {
                    put("exp", 1893456000) // Year 2030
                    put("sub", "EMP001")
                }.toString().toByteArray(),
                Base64.URL_SAFE or Base64.NO_PADDING
            ) + ".signature"

    // Expired JWT token
    private val expiredJwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            Base64.encodeToString(
                JSONObject().apply {
                    put("exp", 1577836800) // Year 2020 (expired)
                    put("sub", "EMP001")
                }.toString().toByteArray(),
                Base64.URL_SAFE or Base64.NO_PADDING
            ) + ".signature"

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        // Mock static methods
        mockkStatic(Base64::class)
        mockkConstructor(MasterKey.Builder::class)
        mockkStatic(EncryptedSharedPreferences::class)

        // Mock MasterKey creation
        every { anyConstructed<MasterKey.Builder>().setKeyScheme(any()) } returns mockk {
            every { build() } returns mockMasterKey
        }

        // Mock EncryptedSharedPreferences creation
        every {
            EncryptedSharedPreferences.create(
                any<Context>(),
                any<String>(),
                any<MasterKey>(),
                any<EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
                any<EncryptedSharedPreferences.PrefValueEncryptionScheme>()
            )
        } returns mockEncryptedPrefs

        // Mock editor
        every { mockEncryptedPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.clear() } returns mockEditor
        every { mockEditor.apply() } just Runs

        tokenManager = TokenManager(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `saveToken stores token and extracts expiration time`() = runTest {
        // Given
        val token = validJwtToken
        val expectedExpiration = 1893456000L * 1000 // Convert to milliseconds

        // Mock Base64 decoding
        every { Base64.decode(any<String>(), any()) } returns JSONObject().apply {
            put("exp", 1893456000)
        }.toString().toByteArray()

        // When
        tokenManager.saveToken(token)

        // Then
        verify { mockEditor.putString("jwt_token", token) }
        verify { mockEditor.putLong("token_expiration", expectedExpiration) }
        verify(exactly = 2) { mockEditor.apply() }
    }

    @Test
    fun `saveToken with invalid JWT uses default expiration`() = runTest {
        // Given
        val invalidToken = "invalid.jwt.token"
        
        // Mock Base64 decoding to throw exception
        every { Base64.decode(any<String>(), any()) } throws IllegalArgumentException()

        // When
        tokenManager.saveToken(invalidToken)

        // Then
        verify { mockEditor.putString("jwt_token", invalidToken) }
        verify { mockEditor.putLong("token_expiration", any()) }
        verify(exactly = 2) { mockEditor.apply() }
    }

    @Test
    fun `getToken returns stored token`() = runTest {
        // Given
        val expectedToken = "test-token"
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns expectedToken

        // When
        val result = tokenManager.getToken()

        // Then
        assertEquals(expectedToken, result)
        verify { mockEncryptedPrefs.getString("jwt_token", null) }
    }

    @Test
    fun `getToken returns null when no token stored`() = runTest {
        // Given
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns null

        // When
        val result = tokenManager.getToken()

        // Then
        assertNull(result)
        verify { mockEncryptedPrefs.getString("jwt_token", null) }
    }

    @Test
    fun `isTokenExpired returns false for valid token`() = runTest {
        // Given
        val futureTime = System.currentTimeMillis() + 3600000 // 1 hour from now
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns futureTime

        // When
        val result = tokenManager.isTokenExpired()

        // Then
        assertFalse(result)
        verify { mockEncryptedPrefs.getLong("token_expiration", 0) }
    }

    @Test
    fun `isTokenExpired returns true for expired token`() = runTest {
        // Given
        val pastTime = System.currentTimeMillis() - 3600000 // 1 hour ago
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns pastTime

        // When
        val result = tokenManager.isTokenExpired()

        // Then
        assertTrue(result)
        verify { mockEncryptedPrefs.getLong("token_expiration", 0) }
    }

    @Test
    fun `isTokenExpired returns true when no expiration stored`() = runTest {
        // Given
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns 0

        // When
        val result = tokenManager.isTokenExpired()

        // Then
        assertTrue(result)
        verify { mockEncryptedPrefs.getLong("token_expiration", 0) }
    }

    @Test
    fun `isTokenValid returns true when token exists and not expired`() = runTest {
        // Given
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns "valid-token"
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns System.currentTimeMillis() + 3600000

        // When
        val result = tokenManager.isTokenValid()

        // Then
        assertTrue(result)
    }

    @Test
    fun `isTokenValid returns false when token is null`() = runTest {
        // Given
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns null

        // When
        val result = tokenManager.isTokenValid()

        // Then
        assertFalse(result)
    }

    @Test
    fun `isTokenValid returns false when token is expired`() = runTest {
        // Given
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns "expired-token"
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns System.currentTimeMillis() - 3600000

        // When
        val result = tokenManager.isTokenValid()

        // Then
        assertFalse(result)
    }

    @Test
    fun `getTokenExpirationTime returns expiration time when available`() = runTest {
        // Given
        val expectedTime = System.currentTimeMillis() + 3600000
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns expectedTime

        // When
        val result = tokenManager.getTokenExpirationTime()

        // Then
        assertEquals(expectedTime, result)
        verify { mockEncryptedPrefs.getLong("token_expiration", 0) }
    }

    @Test
    fun `getTokenExpirationTime returns null when no expiration stored`() = runTest {
        // Given
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns 0

        // When
        val result = tokenManager.getTokenExpirationTime()

        // Then
        assertNull(result)
        verify { mockEncryptedPrefs.getLong("token_expiration", 0) }
    }

    @Test
    fun `clearToken clears all stored data`() = runTest {
        // When
        tokenManager.clearToken()

        // Then
        verify { mockEditor.clear() }
        verify { mockEditor.apply() }
    }

    @Test
    fun `isLoggedIn returns result of isTokenValid`() = runTest {
        // Given
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns "valid-token"
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns System.currentTimeMillis() + 3600000

        // When
        val result = tokenManager.isLoggedIn()

        // Then
        assertTrue(result)
    }

    @Test
    fun `saveEmployeeInfo stores employee info as JSON`() = runTest {
        // Given
        val expectedJson = Gson().toJson(testEmployeeInfo)

        // When
        tokenManager.saveEmployeeInfo(testEmployeeInfo)

        // Then
        verify { mockEditor.putString("employee_info", expectedJson) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `getEmployeeInfo returns parsed employee info`() = runTest {
        // Given
        val employeeJson = Gson().toJson(testEmployeeInfo)
        every { mockEncryptedPrefs.getString("employee_info", null) } returns employeeJson

        // When
        val result = tokenManager.getEmployeeInfo()

        // Then
        assertEquals(testEmployeeInfo, result)
        verify { mockEncryptedPrefs.getString("employee_info", null) }
    }

    @Test
    fun `getEmployeeInfo returns null when no employee info stored`() = runTest {
        // Given
        every { mockEncryptedPrefs.getString("employee_info", null) } returns null

        // When
        val result = tokenManager.getEmployeeInfo()

        // Then
        assertNull(result)
        verify { mockEncryptedPrefs.getString("employee_info", null) }
    }

    @Test
    fun `getEmployeeInfo returns null when JSON parsing fails`() = runTest {
        // Given
        every { mockEncryptedPrefs.getString("employee_info", null) } returns "invalid-json"

        // When
        val result = tokenManager.getEmployeeInfo()

        // Then
        assertNull(result)
        verify { mockEncryptedPrefs.getString("employee_info", null) }
    }

    @Test
    fun `logout calls clearToken`() = runTest {
        // When
        tokenManager.logout()

        // Then
        verify { mockEditor.clear() }
        verify { mockEditor.apply() }
    }

    @Test
    fun `extractExpirationFromJWT handles malformed tokens gracefully`() = runTest {
        val testCases = listOf(
            "invalid-token",
            "only.one.part",
            "too.many.parts.in.this.token",
            "",
            "valid.${Base64.encodeToString("invalid-json".toByteArray(), Base64.URL_SAFE)}.signature"
        )

        testCases.forEach { token ->
            // Mock Base64 decoding to handle various scenarios
            every { Base64.decode(any<String>(), any()) } answers {
                if (token.contains("invalid-json")) {
                    "invalid-json".toByteArray()
                } else {
                    throw IllegalArgumentException("Invalid Base64")
                }
            }

            // When
            tokenManager.saveToken(token)

            // Then - should not throw exception and should use default expiration
            verify { mockEditor.putString("jwt_token", token) }
            verify { mockEditor.putLong("token_expiration", any()) }
        }
    }

    @Test
    fun `extractExpirationFromJWT handles JWT without exp claim`() = runTest {
        // Given
        val tokenWithoutExp = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                Base64.encodeToString(
                    JSONObject().apply {
                        put("sub", "EMP001")
                        // No exp claim
                    }.toString().toByteArray(),
                    Base64.URL_SAFE or Base64.NO_PADDING
                ) + ".signature"

        every { Base64.decode(any<String>(), any()) } returns JSONObject().apply {
            put("sub", "EMP001")
        }.toString().toByteArray()

        // When
        tokenManager.saveToken(tokenWithoutExp)

        // Then - should use default expiration (24 hours)
        verify { mockEditor.putString("jwt_token", tokenWithoutExp) }
        verify { mockEditor.putLong("token_expiration", any()) }
    }
}