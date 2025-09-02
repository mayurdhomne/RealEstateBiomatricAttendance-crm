package com.crm.realestate.data.models

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LoginResponse and EmployeeInfo data models
 * Tests JSON serialization/deserialization
 */
class LoginResponseTest {
    
    private val gson = Gson()
    
    @Test
    fun `test LoginResponse serialization`() {
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP001",
            fullName = "John Doe",
            email = "john.doe@company.com",
            department = "Engineering",
            designation = "Software Developer"
        )
        
        val loginResponse = LoginResponse(
            token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
            employeeId = "EMP001",
            username = "john.doe",
            biometricsRegistered = false,
            employeeInfo = employeeInfo
        )
        
        val json = gson.toJson(loginResponse)
        assertNotNull(json)
        assertTrue(json.contains("\"token\""))
        assertTrue(json.contains("\"employee_id\""))
        assertTrue(json.contains("\"biometrics_registered\""))
    }
    
    @Test
    fun `test LoginResponse deserialization`() {
        val json = """
            {
                "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                "employee_id": "EMP001",
                "username": "john.doe",
                "biometrics_registered": true,
                "employee_info": {
                    "employee_id": "EMP001",
                    "full_name": "John Doe",
                    "email": "john.doe@company.com",
                    "department": "Engineering",
                    "designation": "Software Developer"
                }
            }
        """.trimIndent()
        
        val loginResponse = gson.fromJson(json, LoginResponse::class.java)
        
        assertNotNull(loginResponse)
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", loginResponse.token)
        assertEquals("EMP001", loginResponse.employeeId)
        assertEquals("john.doe", loginResponse.username)
        assertTrue(loginResponse.biometricsRegistered)
        
        // Test nested EmployeeInfo
        assertNotNull(loginResponse.employeeInfo)
        assertEquals("EMP001", loginResponse.employeeInfo.employeeId)
        assertEquals("John Doe", loginResponse.employeeInfo.fullName)
        assertEquals("john.doe@company.com", loginResponse.employeeInfo.email)
        assertEquals("Engineering", loginResponse.employeeInfo.department)
        assertEquals("Software Developer", loginResponse.employeeInfo.designation)
    }
    
    @Test
    fun `test EmployeeInfo serialization`() {
        val employeeInfo = EmployeeInfo(
            employeeId = "EMP002",
            fullName = "Jane Smith",
            email = "jane.smith@company.com",
            department = "Marketing",
            designation = "Marketing Manager"
        )
        
        val json = gson.toJson(employeeInfo)
        assertNotNull(json)
        assertTrue(json.contains("\"employee_id\""))
        assertTrue(json.contains("\"full_name\""))
        assertTrue(json.contains("\"email\""))
        assertTrue(json.contains("\"department\""))
        assertTrue(json.contains("\"designation\""))
    }
    
    @Test
    fun `test EmployeeInfo deserialization`() {
        val json = """
            {
                "employee_id": "EMP002",
                "full_name": "Jane Smith",
                "email": "jane.smith@company.com",
                "department": "Marketing",
                "designation": "Marketing Manager"
            }
        """.trimIndent()
        
        val employeeInfo = gson.fromJson(json, EmployeeInfo::class.java)
        
        assertNotNull(employeeInfo)
        assertEquals("EMP002", employeeInfo.employeeId)
        assertEquals("Jane Smith", employeeInfo.fullName)
        assertEquals("jane.smith@company.com", employeeInfo.email)
        assertEquals("Marketing", employeeInfo.department)
        assertEquals("Marketing Manager", employeeInfo.designation)
    }
    
    @Test
    fun `test LoginResponse with missing optional fields`() {
        val json = """
            {
                "token": "test_token",
                "employee_id": "EMP003",
                "username": "test.user",
                "biometrics_registered": false,
                "employee_info": {
                    "employee_id": "EMP003",
                    "full_name": "Test User",
                    "email": "test@company.com",
                    "department": "IT",
                    "designation": "Tester"
                }
            }
        """.trimIndent()
        
        val loginResponse = gson.fromJson(json, LoginResponse::class.java)
        
        assertNotNull(loginResponse)
        assertEquals("test_token", loginResponse.token)
        assertEquals("EMP003", loginResponse.employeeId)
        assertEquals("test.user", loginResponse.username)
        assertFalse(loginResponse.biometricsRegistered)
        assertNotNull(loginResponse.employeeInfo)
    }
}