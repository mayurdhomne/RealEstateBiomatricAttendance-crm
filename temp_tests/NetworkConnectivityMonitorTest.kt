package com.crm.realestate.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.crm.realestate.utils.NetworkConnectivityMonitor
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for NetworkConnectivityMonitor
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class NetworkConnectivityMonitorTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var mockNetwork: Network
    private lateinit var mockNetworkCapabilities: NetworkCapabilities
    private lateinit var networkMonitor: NetworkConnectivityMonitor
    
    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockConnectivityManager = mockk(relaxed = true)
        mockNetwork = mockk(relaxed = true)
        mockNetworkCapabilities = mockk(relaxed = true)
        
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        
        networkMonitor = NetworkConnectivityMonitor(mockContext)
    }
    
    @Test
    fun `test is currently connected returns true when network has internet and is validated`() {
        // Given
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        
        // When
        val isConnected = networkMonitor.isCurrentlyConnected()
        
        // Then
        assertTrue(isConnected)
    }
    
    @Test
    fun `test is currently connected returns false when network has no internet`() {
        // Given
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
        
        // When
        val isConnected = networkMonitor.isCurrentlyConnected()
        
        // Then
        assertFalse(isConnected)
    }
    
    @Test
    fun `test is currently connected returns false when network is not validated`() {
        // Given
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { mockNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
        
        // When
        val isConnected = networkMonitor.isCurrentlyConnected()
        
        // Then
        assertFalse(isConnected)
    }
    
    @Test
    fun `test is currently connected returns false when no active network`() {
        // Given
        every { mockConnectivityManager.activeNetwork } returns null
        
        // When
        val isConnected = networkMonitor.isCurrentlyConnected()
        
        // Then
        assertFalse(isConnected)
    }
    
    @Test
    fun `test is currently connected returns false when no network capabilities`() {
        // Given
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns null
        
        // When
        val isConnected = networkMonitor.isCurrentlyConnected()
        
        // Then
        assertFalse(isConnected)
    }
    
    @Test
    fun `test has network connection returns true for WiFi`() {
        // Given
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        
        // When
        val hasConnection = networkMonitor.hasNetworkConnection()
        
        // Then
        assertTrue(hasConnection)
    }
    
    @Test
    fun `test has network connection returns true for cellular`() {
        // Given
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        
        // When
        val hasConnection = networkMonitor.hasNetworkConnection()
        
        // Then
        assertTrue(hasConnection)
    }
    
    @Test
    fun `test has network connection returns true for ethernet`() {
        // Given
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns true
        
        // When
        val hasConnection = networkMonitor.hasNetworkConnection()
        
        // Then
        assertTrue(hasConnection)
    }
    
    @Test
    fun `test has network connection returns false when no transport available`() {
        // Given
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        
        // When
        val hasConnection = networkMonitor.hasNetworkConnection()
        
        // Then
        assertFalse(hasConnection)
    }
    
    @Test
    fun `test get network type description returns WiFi`() {
        // Given
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        
        // When
        val description = networkMonitor.getNetworkTypeDescription()
        
        // Then
        assertEquals("WiFi", description)
    }
    
    @Test
    fun `test get network type description returns Mobile Data`() {
        // Given
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        
        // When
        val description = networkMonitor.getNetworkTypeDescription()
        
        // Then
        assertEquals("Mobile Data", description)
    }
    
    @Test
    fun `test get network type description returns Ethernet`() {
        // Given
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns true
        
        // When
        val description = networkMonitor.getNetworkTypeDescription()
        
        // Then
        assertEquals("Ethernet", description)
    }
    
    @Test
    fun `test get network type description returns No connection when no active network`() {
        // Given
        every { mockConnectivityManager.activeNetwork } returns null
        
        // When
        val description = networkMonitor.getNetworkTypeDescription()
        
        // Then
        assertEquals("No connection", description)
    }
    
    @Test
    fun `test get network type description returns Unknown for unrecognized transport`() {
        // Given
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        
        // When
        val description = networkMonitor.getNetworkTypeDescription()
        
        // Then
        assertEquals("Unknown", description)
    }
}