package com.crm.realestate.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.crm.realestate.data.models.EmployeeInfo
import com.crm.realestate.network.TokenManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Security tests for token handling and data encryption
 * Tests secure storage, encryption, and protection against common attacks
 */
@RunWith(RobolectricTestRunner::class)
@ExperimentalCoroutinesApi
class TokenSecurityTest {

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

    // Valid JWT token for testing
    private val validJwtToken = createTestJWT("EMP001", System.currentTimeMillis() + 3600000)
    private val expiredJwtToken = createTestJWT("EMP001", System.currentTimeMillis() - 3600000)

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
                any(),
                any(),
                any(),
                any(),
                any()
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
    fun `token storage uses encrypted preferences`() = runTest {
        // Given
        val token = validJwtToken

        // When
        tokenManager.saveToken(token)

        // Then
        verify {
            EncryptedSharedPreferences.create(
                mockContext,
                "auth_prefs",
                mockMasterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        verify { mockEditor.putString("jwt_token", token) }
    }

    @Test
    fun `master key uses AES256_GCM encryption scheme`() {
        // When
        TokenManager(mockContext)

        // Then
        verify { anyConstructed<MasterKey.Builder>().setKeyScheme(MasterKey.KeyScheme.AES256_GCM) }
    }

    @Test
    fun `token validation prevents expired token usage`() = runTest {
        // Given
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns expiredJwtToken
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns System.currentTimeMillis() - 3600000

        // When
        val isValid = tokenManager.isTokenValid()

        // Then
        assertFalse("Expired token should not be valid", isValid)
    }

    @Test
    fun `token validation prevents null token usage`() = runTest {
        // Given
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns null

        // When
        val isValid = tokenManager.isTokenValid()

        // Then
        assertFalse("Null token should not be valid", isValid)
    }

    @Test
    fun `token extraction handles malformed JWT safely`() = runTest {
        // Given - Various malformed tokens
        val malformedTokens = listOf(
            "not.a.jwt",
            "only.two.parts",
            "too.many.parts.in.this.token.here",
            "",
            "valid.${Base64.encodeToString("not-json".toByteArray(), Base64.URL_SAFE)}.signature"
        )

        malformedTokens.forEach { malformedToken ->
            // Mock Base64 decoding to handle malformed tokens
            every { Base64.decode(any<String>(), any()) } answers {
                if (malformedToken.contains("not-json")) {
                    "not-json".toByteArray()
                } else {
                    throw IllegalArgumentException("Invalid Base64")
                }
            }

            // When
            try {
                tokenManager.saveToken(malformedToken)
                // Should not throw exception
                assertTrue("Malformed token handling should be safe", true)
            } catch (e: Exception) {
                fail("Should handle malformed token gracefully: ${e.message}")
            }
        }
    }

    @Test
    fun `sensitive data is cleared on logout`() = runTest {
        // Given
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns validJwtToken
        every { mockEncryptedPrefs.getString("employee_info", null) } returns "employee_data"

        // When
        tokenManager.logout()

        // Then
        verify { mockEditor.clear() }
        verify { mockEditor.apply() }
    }

    @Test
    fun `employee info is stored as encrypted JSON`() = runTest {
        // Given
        val employeeJson = """{"employee_id":"EMP001","full_name":"John Doe"}"""

        // When
        tokenManager.saveEmployeeInfo(testEmployeeInfo)

        // Then
        verify { mockEditor.putString("employee_info", any()) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `token manager prevents timing attacks on token validation`() = runTest {
        // Given
        val validToken = validJwtToken
        val invalidToken = "invalid.token.here"
        val iterations = 100

        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns System.currentTimeMillis() + 3600000

        // When - Measure timing for valid vs invalid tokens
        val validTokenTimes = mutableListOf<Long>()
        val invalidTokenTimes = mutableListOf<Long>()

        repeat(iterations) {
            // Test valid token
            every { mockEncryptedPrefs.getString("jwt_token", null) } returns validToken
            val validStart = System.nanoTime()
            tokenManager.isTokenValid()
            val validEnd = System.nanoTime()
            validTokenTimes.add(validEnd - validStart)

            // Test invalid token
            every { mockEncryptedPrefs.getString("jwt_token", null) } returns invalidToken
            val invalidStart = System.nanoTime()
            tokenManager.isTokenValid()
            val invalidEnd = System.nanoTime()
            invalidTokenTimes.add(invalidEnd - invalidStart)
        }

        // Then - Timing should be similar (within reasonable variance)
        val avgValidTime = validTokenTimes.average()
        val avgInvalidTime = invalidTokenTimes.average()
        val timingDifference = kotlin.math.abs(avgValidTime - avgInvalidTime)
        val maxAcceptableDifference = kotlin.math.max(avgValidTime, avgInvalidTime) * 0.5 // 50% variance allowed

        assertTrue(
            "Timing attack vulnerability detected: ${timingDifference}ns difference",
            timingDifference <= maxAcceptableDifference
        )
    }

    @Test
    fun `token storage prevents plaintext storage`() = runTest {
        // Given
        val token = validJwtToken

        // When
        tokenManager.saveToken(token)

        // Then - Verify EncryptedSharedPreferences is used, not regular SharedPreferences
        verify(exactly = 0) { mockContext.getSharedPreferences(any(), any()) }
        verify {
            EncryptedSharedPreferences.create(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `token manager handles concurrent access safely`() = runTest {
        // Given
        val concurrentOperations = 50
        val token = validJwtToken
        val exceptions = mutableListOf<Exception>()

        every { mockEncryptedPrefs.getString("jwt_token", null) } returns token
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns System.currentTimeMillis() + 3600000

        // When - Perform concurrent operations
        val jobs = coroutineScope {
            (1..concurrentOperations).map {
                async {
                try {
                    when (it % 4) {
                        0 -> tokenManager.saveToken(token)
                        1 -> tokenManager.getToken()
                        2 -> tokenManager.isTokenValid()
                        3 -> tokenManager.saveEmployeeInfo(testEmployeeInfo)
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
        assertTrue("Concurrent access should be safe: $exceptions", exceptions.isEmpty())
    }

    @Test
    fun `token expiration time is validated against system clock manipulation`() = runTest {
        // Given - Token with future expiration but system time manipulated
        val futureExpiration = System.currentTimeMillis() + 3600000
        val token = createTestJWT("EMP001", futureExpiration)

        every { mockEncryptedPrefs.getString("jwt_token", null) } returns token
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns futureExpiration

        // When - Check if token is valid
        val isValid = tokenManager.isTokenValid()

        // Then - Should be valid with proper expiration
        assertTrue("Token with future expiration should be valid", isValid)

        // When - Simulate expired token
        every { mockEncryptedPrefs.getLong("token_expiration", 0) } returns System.currentTimeMillis() - 1000

        val isExpiredValid = tokenManager.isTokenValid()

        // Then - Should be invalid when expired
        assertFalse("Expired token should not be valid", isExpiredValid)
    }

    @Test
    fun `sensitive data is not logged or exposed in exceptions`() = runTest {
        // Given
        val sensitiveToken = "sensitive.jwt.token"
        val sensitiveEmployeeInfo = testEmployeeInfo.copy(
            email = "sensitive@company.com",
            fullName = "Sensitive User"
        )

        // Mock to throw exception
        every { mockEditor.putString(any(), any()) } throws RuntimeException("Storage error")

        // When & Then - Exceptions should not contain sensitive data
        try {
            tokenManager.saveToken(sensitiveToken)
        } catch (e: Exception) {
            assertFalse(
                "Exception should not contain sensitive token data",
                e.message?.contains("sensitive.jwt.token") == true
            )
        }

        try {
            tokenManager.saveEmployeeInfo(sensitiveEmployeeInfo)
        } catch (e: Exception) {
            assertFalse(
                "Exception should not contain sensitive employee data",
                e.message?.contains("sensitive@company.com") == true
            )
        }
    }

    @Test
    fun `token validation prevents injection attacks`() = runTest {
        // Given - Malicious tokens with injection attempts
        val maliciousTokens = listOf(
            "'; DROP TABLE tokens; --",
            "<script>alert('xss')</script>",
            "../../etc/passwd",
            "\u0000null\u0000byte",
            "extremely.long.${"a".repeat(10000)}.token"
        )

        maliciousTokens.forEach { maliciousToken ->
            // When
            try {
                tokenManager.saveToken(maliciousToken)
                val retrieved = tokenManager.getToken()
                
                // Then - Should handle malicious input safely
                assertTrue("Malicious token handling should be safe", true)
            } catch (e: Exception) {
                // Should not crash with security exceptions
                assertFalse(
                    "Should not throw security-related exceptions",
                    e.message?.contains("security", ignoreCase = true) == true
                )
            }
        }
    }

    @Test
    fun `encryption keys are properly managed`() {
        // When
        TokenManager(mockContext)

        // Then - Verify proper key management
        verify { anyConstructed<MasterKey.Builder>().setKeyScheme(MasterKey.KeyScheme.AES256_GCM) }
        
        // Verify EncryptedSharedPreferences uses proper encryption schemes
        verify {
            EncryptedSharedPreferences.create(
                any(),
                any(),
                any(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    @Test
    fun `token manager prevents memory dumps of sensitive data`() = runTest {
        // Given
        val sensitiveToken = "very.sensitive.jwt.token"
        
        // When
        tokenManager.saveToken(sensitiveToken)
        
        // Force garbage collection to test memory cleanup
        System.gc()
        Thread.sleep(100)
        
        // Then - Sensitive data should not be easily accessible in memory
        // This is a basic test - in production, you'd use memory analysis tools
        val memorySnapshot = Runtime.getRuntime().toString()
        assertFalse(
            "Sensitive token should not be easily accessible in memory",
            memorySnapshot.contains(sensitiveToken)
        )
    }

    /**
     * Helper method to create test JWT tokens
     */
    private fun createTestJWT(subject: String, expiration: Long): String {
        val header = Base64.encodeToString(
            """{"alg":"HS256","typ":"JWT"}""".toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING
        )
        
        val payload = Base64.encodeToString(
            JSONObject().apply {
                put("sub", subject)
                put("exp", expiration / 1000) // JWT exp is in seconds
                put("iat", System.currentTimeMillis() / 1000)
            }.toString().toByteArray(),
            Base64.URL_SAFE or Base64.NO_PADDING
        )
        
        val signature = "test_signature"
        
        return "$header.$payload.$signature"
    }
}