package com.crm.realestate.network

import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*
import retrofit2.Retrofit

@RunWith(MockitoJUnitRunner::class)
class ApiConfigTest {

    @Mock
    private lateinit var context: Context

    @Test
    fun `provideRetrofit should return configured Retrofit instance`() {
        // When
        val retrofit = ApiConfig.provideRetrofit(context)

        // Then
        assertNotNull(retrofit)
        assertTrue(retrofit is Retrofit)
        assertEquals("https://shubhamgharde28.pythonanywhere.com/", retrofit.baseUrl().toString())
    }

    @Test
    fun `provideRetrofit with callback should return configured Retrofit instance`() {
        // Given
        var callbackInvoked = false
        val onUnauthorized = { callbackInvoked = true }

        // When
        val retrofit = ApiConfig.provideRetrofit(context, onUnauthorized)

        // Then
        assertNotNull(retrofit)
        assertTrue(retrofit is Retrofit)
        assertEquals("https://shubhamgharde28.pythonanywhere.com/", retrofit.baseUrl().toString())
        assertFalse(callbackInvoked) // Callback should not be invoked during setup
    }

    @Test
    fun `provideTokenManager should return TokenManager instance`() {
        // When
        val tokenManager = ApiConfig.provideTokenManager(context)

        // Then
        assertNotNull(tokenManager)
        assertTrue(tokenManager is TokenManager)
    }

    @Test
    fun `getBaseUrl should return correct base URL`() {
        // When
        val baseUrl = ApiConfig.getBaseUrl()

        // Then
        assertEquals("https://shubhamgharde28.pythonanywhere.com/", baseUrl)
    }

    @Test
    fun `provideRetrofit should create different instances but with same configuration`() {
        // When
        val retrofit1 = ApiConfig.provideRetrofit(context)
        val retrofit2 = ApiConfig.provideRetrofit(context)

        // Then
        assertNotNull(retrofit1)
        assertNotNull(retrofit2)
        assertNotSame(retrofit1, retrofit2) // Different instances
        assertEquals(retrofit1.baseUrl(), retrofit2.baseUrl()) // Same configuration
    }

    @Test
    fun `provideTokenManager should create different instances for same context`() {
        // When
        val tokenManager1 = ApiConfig.provideTokenManager(context)
        val tokenManager2 = ApiConfig.provideTokenManager(context)

        // Then
        assertNotNull(tokenManager1)
        assertNotNull(tokenManager2)
        assertNotSame(tokenManager1, tokenManager2) // Different instances
    }
}