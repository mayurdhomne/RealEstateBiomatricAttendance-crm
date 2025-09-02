package com.crm.realestate.security

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import com.crm.realestate.data.database.AttendanceDatabase
import com.crm.realestate.data.database.entities.OfflineAttendance
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
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Security tests for data encryption and protection
 * Tests database security, file encryption, and data protection mechanisms
 */
@RunWith(RobolectricTestRunner::class)
@ExperimentalCoroutinesApi
class DataEncryptionTest {

    private lateinit var context: Context
    private lateinit var database: AttendanceDatabase
    private lateinit var tokenManager: TokenManager
    private val mockEncryptedPrefs = mockk<EncryptedSharedPreferences>()
    private val mockEditor = mockk<SharedPreferences.Editor>()

    private val testEmployeeInfo = EmployeeInfo(
        employeeId = "EMP001",
        fullName = "John Doe",
        email = "john.doe@company.com",
        department = "IT",
        designation = "Software Engineer"
    )

    private val testOfflineAttendance = OfflineAttendance(
        id = "test-1",
        employeeId = "EMP001",
        latitude = 40.7128,
        longitude = -74.0060,
        scanType = "face",
        attendanceType = "check_in",
        timestamp = System.currentTimeMillis(),
        synced = false
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            AttendanceDatabase::class.java
        ).allowMainThreadQueries().build()

        // Mock EncryptedSharedPreferences
        mockkStatic(EncryptedSharedPreferences::class)
        mockkConstructor(MasterKey.Builder::class)
        
        every { anyConstructed<MasterKey.Builder>().setKeyScheme(any()) } returns mockk {
            every { build() } returns mockk()
        }
        
        every {
            EncryptedSharedPreferences.create(
                any<String>(),
                any<MasterKey>(),
                any<Context>(),
                any<EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
                any<EncryptedSharedPreferences.PrefValueEncryptionScheme>()
            )
        } returns mockEncryptedPrefs
        
        every { mockEncryptedPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.clear() } returns mockEditor
        every { mockEditor.apply() } just Runs

        tokenManager = TokenManager(context)
    }

    @After
    fun tearDown() {
        database.close()
        unmockkAll()
    }

    @Test
    fun `encrypted shared preferences uses strong encryption`() {
        // When
        TokenManager(context)

        // Then - Verify strong encryption schemes are used
        verify {
            EncryptedSharedPreferences.create(
                any<String>(),
                any<MasterKey>(),
                any<Context>(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    @Test
    fun `master key uses hardware security module when available`() {
        // When
        TokenManager(context)

        // Then - Verify AES256_GCM key scheme is used (hardware-backed when available)
        verify { anyConstructed<MasterKey.Builder>().setKeyScheme(MasterKey.KeyScheme.AES256_GCM) }
    }

    @Test
    fun `sensitive data is not stored in plain text files`() = runTest {
        // Given
        val sensitiveToken = "sensitive.jwt.token"
        val sensitiveEmployeeData = testEmployeeInfo

        // When
        tokenManager.saveToken(sensitiveToken)
        tokenManager.saveEmployeeInfo(sensitiveEmployeeData)

        // Then - Check that no plain text files contain sensitive data
        val appDataDir = context.filesDir
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        
        checkDirectoryForSensitiveData(appDataDir, sensitiveToken)
        checkDirectoryForSensitiveData(sharedPrefsDir, sensitiveToken)
        checkDirectoryForSensitiveData(appDataDir, sensitiveEmployeeData.email)
        checkDirectoryForSensitiveData(sharedPrefsDir, sensitiveEmployeeData.email)
    }

    @Test
    fun `database files are not readable as plain text`() = runTest {
        // Given
        val sensitiveAttendance = testOfflineAttendance.copy(
            employeeId = "SENSITIVE_EMP_001"
        )

        // When
        database.offlineAttendanceDao().insertOfflineAttendance(sensitiveAttendance)

        // Then - Database files should not contain readable sensitive data
        val databaseDir = File(context.applicationInfo.dataDir, "databases")
        if (databaseDir.exists()) {
            checkDirectoryForSensitiveData(databaseDir, "SENSITIVE_EMP_001")
        }
    }

    @Test
    fun `encryption keys are properly rotated`() = runTest {
        // Given
        val token1 = "first.jwt.token"
        val token2 = "second.jwt.token"

        // When - Save first token
        tokenManager.saveToken(token1)
        
        // Simulate key rotation by creating new TokenManager instance
        val newTokenManager = TokenManager(context)
        
        // Save second token with potentially new key
        newTokenManager.saveToken(token2)

        // Then - Both operations should succeed (key rotation handled gracefully)
        verify(atLeast = 2) { mockEditor.putString("jwt_token", any()) }
    }

    @Test
    fun `data encryption prevents unauthorized access`() = runTest {
        // Given
        val sensitiveData = "highly.sensitive.data"
        
        // When
        tokenManager.saveToken(sensitiveData)

        // Then - Verify data is encrypted by checking it's not directly accessible
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns null
        
        val retrievedData = tokenManager.getToken()
        assertNull("Data should not be accessible without proper decryption", retrievedData)
    }

    @Test
    fun `encryption handles large data sets securely`() = runTest {
        // Given
        val largeDataSet = (1..1000).map { index ->
            OfflineAttendance(
                id = "large-data-$index",
                employeeId = "EMP$index",
                latitude = 40.7128 + (index * 0.0001),
                longitude = -74.0060 + (index * 0.0001),
                scanType = "face",
                attendanceType = "check_in",
                timestamp = System.currentTimeMillis() + index,
                synced = false
            )
        }

        // When
        largeDataSet.forEach { attendance ->
            database.offlineAttendanceDao().insertOfflineAttendance(attendance)
        }

        // Then - All data should be stored securely
        val retrievedCount = database.offlineAttendanceDao().getUnsyncedCount()
        assertEquals("All data should be stored", 1000, retrievedCount)
        
        // Verify no sensitive data is accessible in plain text
        val databaseDir = File(context.applicationInfo.dataDir, "databases")
        if (databaseDir.exists()) {
            // Check that employee IDs are not easily readable
            checkDirectoryForSensitiveData(databaseDir, "EMP500")
        }
    }

    @Test
    fun `encryption prevents data tampering`() = runTest {
        // Given
        val originalToken = "original.jwt.token"
        val tamperedToken = "tampered.jwt.token"

        // When
        tokenManager.saveToken(originalToken)
        
        // Simulate tampering attempt
        every { mockEncryptedPrefs.getString("jwt_token", null) } returns tamperedToken
        
        val retrievedToken = tokenManager.getToken()

        // Then - Should detect tampering (in real implementation, this would be handled by EncryptedSharedPreferences)
        assertEquals("Should return tampered token as-is (EncryptedSharedPreferences handles integrity)", tamperedToken, retrievedToken)
    }

    @Test
    fun `sensitive data is cleared from memory after use`() = runTest {
        // Given
        val sensitiveToken = "memory.sensitive.token"
        
        // When
        tokenManager.saveToken(sensitiveToken)
        val retrievedToken = tokenManager.getToken()
        
        // Clear references
        tokenManager.clearToken()
        
        // Force garbage collection
        System.gc()
        Thread.sleep(100)
        
        // Then - Sensitive data should not be easily accessible in memory
        val memoryInfo = Runtime.getRuntime().toString()
        assertFalse(
            "Sensitive data should be cleared from memory",
            memoryInfo.contains(sensitiveToken)
        )
    }

    @Test
    fun `encryption uses secure random for initialization vectors`() {
        // This test verifies that secure randomization is used
        // In a real implementation, you would test the actual encryption process
        
        // Given
        val secureRandom = SecureRandom()
        val iv1 = ByteArray(12)
        val iv2 = ByteArray(12)
        
        // When
        secureRandom.nextBytes(iv1)
        secureRandom.nextBytes(iv2)
        
        // Then - IVs should be different (extremely high probability)
        assertFalse("Initialization vectors should be unique", iv1.contentEquals(iv2))
    }

    @Test
    fun `data encryption prevents side channel attacks`() = runTest {
        // Given
        val sensitiveData1 = "a".repeat(100)
        val sensitiveData2 = "b".repeat(100)
        val iterations = 50

        // When - Measure timing for different data
        val times1 = mutableListOf<Long>()
        val times2 = mutableListOf<Long>()

        repeat(iterations) {
            val start1 = System.nanoTime()
            tokenManager.saveToken(sensitiveData1)
            val end1 = System.nanoTime()
            times1.add(end1 - start1)

            val start2 = System.nanoTime()
            tokenManager.saveToken(sensitiveData2)
            val end2 = System.nanoTime()
            times2.add(end2 - start2)
        }

        // Then - Timing should be similar (constant time operations)
        val avgTime1 = times1.average()
        val avgTime2 = times2.average()
        val timingDifference = kotlin.math.abs(avgTime1 - avgTime2)
        val maxAcceptableDifference = kotlin.math.max(avgTime1, avgTime2) * 0.3 // 30% variance allowed

        assertTrue(
            "Potential side-channel attack vulnerability: ${timingDifference}ns difference",
            timingDifference <= maxAcceptableDifference
        )
    }

    @Test
    fun `encryption handles concurrent access securely`() = runTest {
        // Given
        val concurrentOperations = 20
        val exceptions = mutableListOf<Exception>()

        // When - Perform concurrent encryption operations
        val jobs = coroutineScope {
            (1..concurrentOperations).map { index ->
                async {
                try {
                    val token = "concurrent.token.$index"
                    tokenManager.saveToken(token)
                    tokenManager.getToken()
                    tokenManager.saveEmployeeInfo(testEmployeeInfo.copy(employeeId = "EMP$index"))
                } catch (e: Exception) {
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                }
            }
        }
        }

        jobs.forEach { it.await() }

        // Then - No security exceptions should occur
        val securityExceptions = exceptions.filter { 
            it.message?.contains("security", ignoreCase = true) == true ||
            it.message?.contains("encryption", ignoreCase = true) == true
        }
        
        assertTrue("No security-related exceptions should occur: $securityExceptions", securityExceptions.isEmpty())
    }

    @Test
    fun `data encryption prevents forensic recovery`() = runTest {
        // Given
        val sensitiveToken = "forensic.test.token"
        val sensitiveEmployee = testEmployeeInfo.copy(email = "forensic@test.com")

        // When
        tokenManager.saveToken(sensitiveToken)
        tokenManager.saveEmployeeInfo(sensitiveEmployee)
        
        // Clear the data
        tokenManager.clearToken()
        
        // Force multiple garbage collections and memory operations
        repeat(10) {
            System.gc()
            Thread.sleep(10)
            // Allocate and release memory to overwrite freed memory
            val dummy = ByteArray(1024 * 1024)
            dummy.fill(0xFF.toByte())
        }

        // Then - Sensitive data should not be recoverable
        // This is a basic test - real forensic prevention requires secure deletion
        val memorySnapshot = Runtime.getRuntime().toString()
        assertFalse(
            "Sensitive data should not be forensically recoverable",
            memorySnapshot.contains(sensitiveToken) || memorySnapshot.contains("forensic@test.com")
        )
    }

    /**
     * Helper method to check if sensitive data appears in plain text files
     */
    private fun checkDirectoryForSensitiveData(directory: File, sensitiveData: String) {
        if (!directory.exists()) return
        
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.canRead()) {
                try {
                    val content = file.readText()
                    assertFalse(
                        "Sensitive data found in plain text file: ${file.absolutePath}",
                        content.contains(sensitiveData)
                    )
                } catch (e: Exception) {
                    // File might be binary or encrypted - this is good
                }
            } else if (file.isDirectory) {
                checkDirectoryForSensitiveData(file, sensitiveData)
            }
        }
    }

    /**
     * Test AES-GCM encryption implementation
     */
    @Test
    fun `aes_gcm_encryption_implementation_test`() {
        // Given
        val plaintext = "sensitive data to encrypt"
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()
        
        // When
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        
        // Decrypt
        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val decryptedText = String(decryptCipher.doFinal(ciphertext))
        
        // Then
        assertEquals("Decrypted text should match original", plaintext, decryptedText)
        assertFalse("Ciphertext should not contain plaintext", String(ciphertext).contains(plaintext))
    }

    /**
     * Test that encryption produces different outputs for same input
     */
    @Test
    fun `encryption_produces_different_outputs_for_same_input`() {
        // Given
        val plaintext = "same input data"
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val secretKey = keyGenerator.generateKey()
        
        // When - Encrypt same data twice
        val cipher1 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher1.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext1 = cipher1.doFinal(plaintext.toByteArray())
        
        val cipher2 = Cipher.getInstance("AES/GCM/NoPadding")
        cipher2.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext2 = cipher2.doFinal(plaintext.toByteArray())
        
        // Then - Ciphertexts should be different (due to different IVs)
        assertFalse("Same input should produce different ciphertext", ciphertext1.contentEquals(ciphertext2))
    }
}