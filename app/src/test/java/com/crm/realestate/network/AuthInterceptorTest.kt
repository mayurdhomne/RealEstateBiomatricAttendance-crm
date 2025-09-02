package com.crm.realestate.network

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import okhttp3.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

@RunWith(MockitoJUnitRunner::class)
class AuthInterceptorTest {

    @Mock
    private lateinit var tokenManager: TokenManager

    @Mock
    private lateinit var chain: Interceptor.Chain

    @Mock
    private lateinit var request: Request

    @Mock
    private lateinit var response: Response

    @Mock
    private lateinit var requestBuilder: Request.Builder

    @Mock
    private lateinit var httpUrl: HttpUrl

    private lateinit var authInterceptor: AuthInterceptor
    private var logoutCallbackInvoked = false

    @Before
    fun setup() {
        logoutCallbackInvoked = false
        authInterceptor = AuthInterceptor(tokenManager) {
            logoutCallbackInvoked = true
        }
    }

    @Test
    fun `intercept should add Authorization header for valid token`() {
        // Given
        val token = "valid_jwt_token"
        val apiUrl = "https://shubhamgharde28.pythonanywhere.com/api/dashboard/"
        
        `when`(chain.request()).thenReturn(request)
        `when`(request.url).thenReturn(httpUrl)
        `when`(httpUrl.toString()).thenReturn(apiUrl)
        `when`(request.newBuilder()).thenReturn(requestBuilder)
        `when`(requestBuilder.addHeader(anyString(), anyString())).thenReturn(requestBuilder)
        `when`(requestBuilder.build()).thenReturn(request)
        `when`(chain.proceed(request)).thenReturn(response)
        `when`(response.code).thenReturn(200)
        
        // Mock token manager responses using mockito-kotlin for suspend functions
        runBlocking {
            `when`(tokenManager.getToken()).thenReturn(token)
            `when`(tokenManager.isTokenValid()).thenReturn(true)
        }

        // When
        val result = authInterceptor.intercept(chain)

        // Then
        verify(requestBuilder).addHeader("Authorization", "Bearer $token")
        assertEquals(response, result)
    }

    @Test
    fun `intercept should not add Authorization header for login endpoint`() {
        // Given
        val loginUrl = "https://shubhamgharde28.pythonanywhere.com/login/"
        
        `when`(chain.request()).thenReturn(request)
        `when`(request.url).thenReturn(httpUrl)
        `when`(httpUrl.toString()).thenReturn(loginUrl)
        `when`(chain.proceed(request)).thenReturn(response)
        `when`(response.code).thenReturn(200)

        // When
        val result = authInterceptor.intercept(chain)

        // Then
        verify(request, never()).newBuilder()
        assertEquals(response, result)
    }

    @Test
    fun `intercept should not add Authorization header for register endpoint`() {
        // Given
        val registerUrl = "https://shubhamgharde28.pythonanywhere.com/register/"
        
        `when`(chain.request()).thenReturn(request)
        `when`(request.url).thenReturn(httpUrl)
        `when`(httpUrl.toString()).thenReturn(registerUrl)
        `when`(chain.proceed(request)).thenReturn(response)
        `when`(response.code).thenReturn(200)

        // When
        val result = authInterceptor.intercept(chain)

        // Then
        verify(request, never()).newBuilder()
        assertEquals(response, result)
    }

    @Test
    fun `intercept should clear token when token is invalid`() {
        // Given
        val token = "invalid_jwt_token"
        val apiUrl = "https://shubhamgharde28.pythonanywhere.com/api/dashboard/"
        
        `when`(chain.request()).thenReturn(request)
        `when`(request.url).thenReturn(httpUrl)
        `when`(httpUrl.toString()).thenReturn(apiUrl)
        `when`(chain.proceed(request)).thenReturn(response)
        `when`(response.code).thenReturn(200)
        
        // Mock token manager responses
        runBlocking {
            `when`(tokenManager.getToken()).thenReturn(token)
            `when`(tokenManager.isTokenValid()).thenReturn(false)
        }

        // When
        authInterceptor.intercept(chain)

        // Then
        runBlocking {
            verify(tokenManager).clearToken()
        }
    }

    @Test
    fun `intercept should handle 401 response and trigger logout`() {
        // Given
        val token = "valid_jwt_token"
        val apiUrl = "https://shubhamgharde28.pythonanywhere.com/api/dashboard/"
        
        `when`(chain.request()).thenReturn(request)
        `when`(request.url).thenReturn(httpUrl)
        `when`(httpUrl.toString()).thenReturn(apiUrl)
        `when`(request.newBuilder()).thenReturn(requestBuilder)
        `when`(requestBuilder.addHeader(anyString(), anyString())).thenReturn(requestBuilder)
        `when`(requestBuilder.build()).thenReturn(request)
        `when`(chain.proceed(request)).thenReturn(response)
        `when`(response.code).thenReturn(401)
        
        // Mock token manager responses
        runBlocking {
            `when`(tokenManager.getToken()).thenReturn(token)
            `when`(tokenManager.isTokenValid()).thenReturn(true)
        }

        // When
        val result = authInterceptor.intercept(chain)

        // Then
        runBlocking {
            verify(tokenManager).logout()
        }
        assertTrue(logoutCallbackInvoked)
        assertEquals(response, result)
    }

    @Test
    fun `intercept should not trigger logout for 401 on auth endpoints`() {
        // Given
        val loginUrl = "https://shubhamgharde28.pythonanywhere.com/login/"
        
        `when`(chain.request()).thenReturn(request)
        `when`(request.url).thenReturn(httpUrl)
        `when`(httpUrl.toString()).thenReturn(loginUrl)
        `when`(chain.proceed(request)).thenReturn(response)
        `when`(response.code).thenReturn(401)

        // When
        val result = authInterceptor.intercept(chain)

        // Then
        runBlocking {
            verify(tokenManager, never()).logout()
        }
        assertFalse(logoutCallbackInvoked)
        assertEquals(response, result)
    }

    @Test
    fun `intercept should proceed with original request when no token exists`() {
        // Given
        val apiUrl = "https://shubhamgharde28.pythonanywhere.com/api/dashboard/"
        
        `when`(chain.request()).thenReturn(request)
        `when`(request.url).thenReturn(httpUrl)
        `when`(httpUrl.toString()).thenReturn(apiUrl)
        `when`(chain.proceed(request)).thenReturn(response)
        `when`(response.code).thenReturn(200)
        
        // Mock token manager responses
        runBlocking {
            `when`(tokenManager.getToken()).thenReturn(null)
        }

        // When
        val result = authInterceptor.intercept(chain)

        // Then
        verify(request, never()).newBuilder()
        assertEquals(response, result)
    }

    @Test
    fun `intercept should handle various HTTP status codes correctly`() {
        // Given
        val token = "valid_jwt_token"
        val apiUrl = "https://shubhamgharde28.pythonanywhere.com/api/dashboard/"
        
        `when`(chain.request()).thenReturn(request)
        `when`(request.url).thenReturn(httpUrl)
        `when`(httpUrl.toString()).thenReturn(apiUrl)
        `when`(request.newBuilder()).thenReturn(requestBuilder)
        `when`(requestBuilder.addHeader(anyString(), anyString())).thenReturn(requestBuilder)
        `when`(requestBuilder.build()).thenReturn(request)
        `when`(chain.proceed(request)).thenReturn(response)
        
        // Mock token manager responses
        runBlocking {
            `when`(tokenManager.getToken()).thenReturn(token)
            `when`(tokenManager.isTokenValid()).thenReturn(true)
        }

        // Test different status codes
        val statusCodes = listOf(200, 201, 400, 403, 404, 500)
        
        statusCodes.forEach { statusCode ->
            `when`(response.code).thenReturn(statusCode)
            
            // When
            val result = authInterceptor.intercept(chain)
            
            // Then
            assertEquals(response, result)
            if (statusCode != 401) {
                runBlocking {
                    verify(tokenManager, never()).logout()
                }
            }
        }
    }
}