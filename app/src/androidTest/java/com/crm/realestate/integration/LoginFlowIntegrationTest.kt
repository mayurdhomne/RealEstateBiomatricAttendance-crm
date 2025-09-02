package com.crm.realestate.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crm.realestate.data.models.EmployeeInfo
import com.crm.realestate.data.models.LoginResponse
import com.crm.realestate.data.models.Result
import com.crm.realestate.data.repository.AuthRepository
import com.crm.realestate.network.TokenManager
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Integration tests for the complete login flow
 * Tests the interaction between AuthRepository, TokenManager, and API services
 */
@RunWith(AndroidJUnit4::class)
class LoginFlowIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var authRepository: AuthRepository
    private lateinit var tokenManager: TokenManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        authRepository = AuthRepository(context)
        tokenManager = TokenManager(context)
        
        // Clear any existing data
        runTest {
            tokenManager.clearToken()
        }
    }
    
    @Test
    fun testCredentialValidation() {
        // Test valid credentials
        val (isValid1, error1) = authRepository.validateCredentials("validuser", "validpass123")
        assertTrue("Valid credentials should pass", isValid1)
        assertNull("No error for valid credentials", error1)
        
        // Test empty username
        val (isValid2, error2) = authRepository.validateCredentials("", "validpass123")
        assertFalse("Empty username should fail", isValid2)
        assertEquals("Username is required", error2)
        
        // Test empty password
        val (isValid3, error3) = authRepository.validateCredentials("validuser", "")
        assertFalse("Empty password should fail", isValid3)
        assertEquals("Password is required", error3)
        
        // Test short username
        val (isValid4, error4) = authRepository.validateCredentials("ab", "validpass123")
        assertFalse("Short username should fail", isValid4)
        assertEquals("Username must be at least 3 characters", error4)
        
        // Test short password
        val (isValid5, error5) = authRepository.validateCredentials("validuser", "12345")
        assertFalse("Short password should fail", isValid5)
        assertEquals("Password must be at least 6 characters", error5)
    }
    
    @Test
    fun testTokenManagerIntegration() = runTest {
        // Test initial state
        assertFalse("Should not be logged in initially", authRepository.isLoggedIn())
        assertNull("Should have no token initially", authRepository.getAuthToken())
        assertNull("Should have no employee info initially", authRepository.getEmployeeInfo())
        
        // Test saving token and employee info
        val testToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjk5OTk5OTk5OTl9.Lmx3-BAIyLdEiwrwcpn5LO6HbM4_4dIa_NjJWxV_yHg"
        val testEmployeeInfo = EmployeeInfo(
            employeeId = "EMP001",
            fullName = "John Doe",
            email = "john.doe@company.com",
            department = "IT",
            designation = "Software Engineer"
        )
        
        authRepository.saveAuthToken(testToken)
        authRepository.saveEmployeeInfo(testEmployeeInfo)
        
        // Verify data is saved
        assertEquals("Token should be saved", testToken, authRepository.getAuthToken())
        assertEquals("Employee info should be saved", testEmployeeInfo, authRepository.getEmployeeInfo())
        assertTrue("Should be logged in after saving token", authRepository.isLoggedIn())
        
        // Test logout
        authRepository.logout()
        assertFalse("Should not be logged in after logout", authRepository.isLoggedIn())
        assertNull("Should have no token after logout", authRepository.getAuthToken())
        assertNull("Should have no employee info after logout", authRepository.getEmployeeInfo())
    }
    
    @Test
    fun testTokenValidation() = runTest {
        // Test with expired token (exp: 1516239022 - January 18, 2018)
        val expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyMzkwMjJ9.4Adcj3UFYzPUVaVF43FmMab6RlaQD8A9V8wFzzht-KQ"
        authRepository.saveAuthToken(expiredToken)
        
        assertFalse("Expired token should not be valid", authRepository.isTokenValid())
        assertFalse("Should not be logged in with expired token", authRepository.isLoggedIn())
        
        // Test with valid token (exp: 9999999999 - far future)
        val validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjk5OTk5OTk5OTl9.Lmx3-BAIyLdEiwrwcpn5LO6HbM4_4dIa_NjJWxV_yHg"
        authRepository.saveAuthToken(validToken)
        
        assertTrue("Valid token should be valid", authRepository.isTokenValid())
        assertTrue("Should be logged in with valid token", authRepository.isLoggedIn())
    }
    
    @Test
    fun testRefreshTokenNotImplemented() = runTest {
        val result = authRepository.refreshToken()
        
        assertTrue("Refresh token should return error", result is Result.Error)
        val errorResult = result as Result.Error
        assertEquals("Token refresh is not currently supported", errorResult.message)
        assertTrue("Should be UnsupportedOperationException", 
            errorResult.exception is UnsupportedOperationException)
    }
    
    @Test
    fun testEmployeeInfoSerialization() = runTest {
        // Test saving and retrieving complex employee info
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP123",
            fullName = "Jane Smith",
            email = "jane.smith@company.com",
            department = "Human Resources",
            designation = "HR Manager"
        )
        
        authRepository.saveEmployeeInfo(employeeInfo)
        val retrievedInfo = authRepository.getEmployeeInfo()
        
        assertNotNull("Employee info should be retrieved", retrievedInfo)
        assertEquals("Employee ID should match", employeeInfo.employeeId, retrievedInfo?.employeeId)
        assertEquals("Full name should match", employeeInfo.fullName, retrievedInfo?.fullName)
        assertEquals("Email should match", employeeInfo.email, retrievedInfo?.email)
        assertEquals("Department should match", employeeInfo.department, retrievedInfo?.department)
        assertEquals("Designation should match", employeeInfo.designation, retrievedInfo?.designation)
    }
    
    @Test
    fun testMultipleLoginLogoutCycles() = runTest {
        val testToken1 = "token1"
        val testToken2 = "token2"
        val employeeInfo1 = EmployeeInfo("EMP001", "John Doe", "john@company.com", "IT", "Developer")
        val employeeInfo2 = EmployeeInfo("EMP002", "Jane Smith", "jane@company.com", "HR", "Manager")
        
        // First login
        authRepository.saveAuthToken(testToken1)
        authRepository.saveEmployeeInfo(employeeInfo1)
        assertTrue("Should be logged in", authRepository.isLoggedIn())
        assertEquals("Should have first token", testToken1, authRepository.getAuthToken())
        assertEquals("Should have first employee info", employeeInfo1, authRepository.getEmployeeInfo())
        
        // Logout
        authRepository.logout()
        assertFalse("Should be logged out", authRepository.isLoggedIn())
        assertNull("Should have no token", authRepository.getAuthToken())
        assertNull("Should have no employee info", authRepository.getEmployeeInfo())
        
        // Second login
        authRepository.saveAuthToken(testToken2)
        authRepository.saveEmployeeInfo(employeeInfo2)
        assertTrue("Should be logged in again", authRepository.isLoggedIn())
        assertEquals("Should have second token", testToken2, authRepository.getAuthToken())
        assertEquals("Should have second employee info", employeeInfo2, authRepository.getEmployeeInfo())
        
        // Final logout
        authRepository.logout()
        assertFalse("Should be logged out finally", authRepository.isLoggedIn())
    }
}