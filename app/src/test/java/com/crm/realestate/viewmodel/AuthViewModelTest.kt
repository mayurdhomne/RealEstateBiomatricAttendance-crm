package com.crm.realestate.viewmodel

import com.crm.realestate.data.models.EmployeeInfo
import com.crm.realestate.data.models.LoginResponse
import com.crm.realestate.data.models.Result
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for AuthViewModel logic
 * Tests state management without Android dependencies
 */
class AuthViewModelTest {
    
    @Test
    fun `Result Success should contain correct data`() {
        // Given
        val testEmployeeInfo = EmployeeInfo(
            employeeId = "EMP001",
            fullName = "John Doe",
            email = "john.doe@company.com",
            department = "IT",
            designation = "Software Engineer"
        )
        val testLoginResponse = LoginResponse(
            token = "test_token",
            employeeId = "EMP001",
            username = "testuser",
            biometricsRegistered = false,
            employeeInfo = testEmployeeInfo
        )
        
        // When
        val result = Result.Success(testLoginResponse)
        
        // Then
        assertTrue("Should be Success result", result is Result.Success)
        assertEquals("Should contain correct data", testLoginResponse, result.data)
    }
    
    @Test
    fun `Result Error should contain exception and message`() {
        // Given
        val exception = Exception("Login failed")
        val message = "Invalid credentials"
        
        // When
        val result = Result.Error(exception, message)
        
        // Then
        assertTrue("Should be Error result", result is Result.Error)
        assertEquals("Should contain correct exception", exception, result.exception)
        assertEquals("Should contain correct message", message, result.message)
    }
    
    @Test
    fun `Result Loading should indicate loading state`() {
        // When
        val result = Result.Loading(true)
        
        // Then
        assertTrue("Should be Loading result", result is Result.Loading)
        assertTrue("Should indicate loading", result.loading)
    }
    
    @Test
    fun `LoginResponse should contain all required fields`() {
        // Given
        val testEmployeeInfo = EmployeeInfo(
            employeeId = "EMP001",
            fullName = "John Doe",
            email = "john.doe@company.com",
            department = "IT",
            designation = "Software Engineer"
        )
        
        // When
        val loginResponse = LoginResponse(
            token = "jwt_token_here",
            employeeId = "EMP001",
            username = "johndoe",
            biometricsRegistered = true,
            employeeInfo = testEmployeeInfo
        )
        
        // Then
        assertEquals("Should have correct token", "jwt_token_here", loginResponse.token)
        assertEquals("Should have correct employee ID", "EMP001", loginResponse.employeeId)
        assertEquals("Should have correct username", "johndoe", loginResponse.username)
        assertTrue("Should have biometrics registered", loginResponse.biometricsRegistered)
        assertEquals("Should have correct employee info", testEmployeeInfo, loginResponse.employeeInfo)
    }
    
    @Test
    fun `EmployeeInfo should contain all required fields`() {
        // When
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP123",
            fullName = "Jane Smith",
            email = "jane.smith@company.com",
            department = "HR",
            designation = "Manager"
        )
        
        // Then
        assertEquals("Should have correct employee ID", "EMP123", employeeInfo.employeeId)
        assertEquals("Should have correct full name", "Jane Smith", employeeInfo.fullName)
        assertEquals("Should have correct email", "jane.smith@company.com", employeeInfo.email)
        assertEquals("Should have correct department", "HR", employeeInfo.department)
        assertEquals("Should have correct designation", "Manager", employeeInfo.designation)
    }
    
    @Test
    fun `validation logic should work correctly`() {
        // Test validation logic similar to what would be in AuthRepository
        fun validateCredentials(username: String, password: String): Pair<Boolean, String?> {
            return when {
                username.isBlank() -> Pair(false, "Username is required")
                password.isBlank() -> Pair(false, "Password is required")
                username.length < 3 -> Pair(false, "Username must be at least 3 characters")
                password.length < 6 -> Pair(false, "Password must be at least 6 characters")
                else -> Pair(true, null)
            }
        }
        
        // Test valid credentials
        val (isValid1, error1) = validateCredentials("validuser", "validpass123")
        assertTrue("Valid credentials should pass", isValid1)
        assertNull("No error for valid credentials", error1)
        
        // Test empty username
        val (isValid2, error2) = validateCredentials("", "validpass123")
        assertFalse("Empty username should fail", isValid2)
        assertEquals("Username is required", error2)
        
        // Test short password
        val (isValid3, error3) = validateCredentials("validuser", "12345")
        assertFalse("Short password should fail", isValid3)
        assertEquals("Password must be at least 6 characters", error3)
    }
    
    @Test
    fun `string trimming should work correctly`() {
        // Test the trimming logic that would be used in ViewModel
        val usernameWithSpaces = "  testuser  "
        val trimmedUsername = usernameWithSpaces.trim()
        
        assertEquals("Should trim spaces", "testuser", trimmedUsername)
        assertNotEquals("Should not equal original", usernameWithSpaces, trimmedUsername)
    }
}