package com.crm.realestate.data.models

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ApiResponse and Result classes
 * Tests JSON serialization/deserialization and utility functions
 */
class ApiResponseTest {
    
    private val gson = Gson()
    
    @Test
    fun `test ApiResponse serialization`() {
        val apiResponse = ApiResponse(
            success = true,
            message = "Operation successful",
            data = "test_data"
        )
        
        val json = gson.toJson(apiResponse)
        assertNotNull(json)
        assertTrue(json.contains("\"success\""))
        assertTrue(json.contains("\"message\""))
        assertTrue(json.contains("\"data\""))
        assertTrue(json.contains("true"))
        assertTrue(json.contains("test_data"))
    }
    
    @Test
    fun `test ApiResponse deserialization`() {
        val json = """
            {
                "success": true,
                "message": "Data retrieved successfully",
                "data": {
                    "id": "123",
                    "name": "Test"
                }
            }
        """.trimIndent()
        
        val apiResponse = gson.fromJson(json, ApiResponse::class.java)
        
        assertNotNull(apiResponse)
        assertTrue(apiResponse.success)
        assertEquals("Data retrieved successfully", apiResponse.message)
        assertNotNull(apiResponse.data)
    }
    
    @Test
    fun `test ApiResponse with null data`() {
        val json = """
            {
                "success": false,
                "message": "Operation failed",
                "data": null
            }
        """.trimIndent()
        
        val apiResponse = gson.fromJson(json, ApiResponse::class.java)
        
        assertNotNull(apiResponse)
        assertFalse(apiResponse.success)
        assertEquals("Operation failed", apiResponse.message)
        assertNull(apiResponse.data)
    }
    
    @Test
    fun `test Result Success`() {
        val successResult = Result.Success("test_data")
        
        assertTrue(successResult is Result.Success)
        assertEquals("test_data", successResult.data)
        assertTrue(successResult.isSuccess())
        assertFalse(successResult.isError())
        assertFalse(successResult.isLoading())
        assertEquals("test_data", successResult.getDataOrNull())
    }
    
    @Test
    fun `test Result Error`() {
        val exception = RuntimeException("Test error")
        val errorResult = Result.Error(exception, "Custom error message")
        
        assertTrue(errorResult is Result.Error)
        assertEquals(exception, errorResult.exception)
        assertEquals("Custom error message", errorResult.message)
        assertFalse(errorResult.isSuccess())
        assertTrue(errorResult.isError())
        assertFalse(errorResult.isLoading())
        assertNull(errorResult.getDataOrNull())
    }
    
    @Test
    fun `test Result Error without custom message`() {
        val exception = RuntimeException("Test error")
        val errorResult = Result.Error(exception)
        
        assertTrue(errorResult is Result.Error)
        assertEquals(exception, errorResult.exception)
        assertNull(errorResult.message)
    }
    
    @Test
    fun `test Result Loading`() {
        val loadingResult = Result.Loading()
        val loadingFalseResult = Result.Loading(false)
        
        assertTrue(loadingResult is Result.Loading)
        assertTrue(loadingResult.loading)
        assertFalse(loadingResult.isSuccess())
        assertFalse(loadingResult.isError())
        assertTrue(loadingResult.isLoading())
        assertNull(loadingResult.getDataOrNull())
        
        assertTrue(loadingFalseResult is Result.Loading)
        assertFalse(loadingFalseResult.loading)
    }
    
    @Test
    fun `test Result extension functions`() {
        val successResult: Result<String> = Result.Success("data")
        val errorResult: Result<String> = Result.Error(RuntimeException("error"))
        val loadingResult: Result<String> = Result.Loading()
        
        // Test isSuccess()
        assertTrue(successResult.isSuccess())
        assertFalse(errorResult.isSuccess())
        assertFalse(loadingResult.isSuccess())
        
        // Test isError()
        assertFalse(successResult.isError())
        assertTrue(errorResult.isError())
        assertFalse(loadingResult.isError())
        
        // Test isLoading()
        assertFalse(successResult.isLoading())
        assertFalse(errorResult.isLoading())
        assertTrue(loadingResult.isLoading())
        
        // Test getDataOrNull()
        assertEquals("data", successResult.getDataOrNull())
        assertNull(errorResult.getDataOrNull())
        assertNull(loadingResult.getDataOrNull())
    }
    
    @Test
    fun `test ApiResponse with complex data type`() {
        data class TestData(val id: String, val value: Int)
        
        val testData = TestData("test_id", 42)
        val apiResponse = ApiResponse(
            success = true,
            message = "Success",
            data = testData
        )
        
        assertTrue(apiResponse.success)
        assertEquals("Success", apiResponse.message)
        assertNotNull(apiResponse.data)
        assertEquals("test_id", apiResponse.data?.id)
        assertEquals(42, apiResponse.data?.value)
    }
    
    @Test
    fun `test Result with different data types`() {
        val stringResult = Result.Success("string_data")
        val intResult = Result.Success(123)
        val booleanResult = Result.Success(true)
        
        assertEquals("string_data", stringResult.data)
        assertEquals(123, intResult.data)
        assertTrue(booleanResult.data)
        
        assertTrue(stringResult.isSuccess())
        assertTrue(intResult.isSuccess())
        assertTrue(booleanResult.isSuccess())
    }
}