package com.crm.realestate

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.crm.realestate.integration.CompleteUserFlowIntegrationTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Complete Integration Test Suite
 * 
 * This suite runs all integration tests to verify the complete user flow:
 * - Login to biometric registration
 * - Biometric registration to dashboard
 * - Dashboard to attendance
 * - Offline scenarios
 * - Error handling and recovery
 * 
 * Test Categories:
 * 1. Happy Path: Complete user flow from login to attendance
 * 2. Offline Scenarios: Network disconnection and reconnection
 * 3. Error Handling: Invalid inputs, network failures, biometric failures
 * 4. Edge Cases: Hardware unavailability, API timeouts, data conflicts
 */
@LargeTest
@SdkSuppress(minSdkVersion = 21) // Minimum SDK for biometric support
@RunWith(AndroidJUnit4::class)
@Suite.SuiteClasses(
    CompleteUserFlowIntegrationTest::class
)
class CompleteIntegrationTestSuite {
    
    companion object {
        const val TEST_TIMEOUT_MS = 30000L // 30 seconds timeout for integration tests
        const val BIOMETRIC_TIMEOUT_MS = 10000L // 10 seconds for biometric operations
        const val NETWORK_TIMEOUT_MS = 15000L // 15 seconds for network operations
        
        // Test data constants
        const val TEST_EMPLOYEE_USERNAME = "test_employee"
        const val TEST_EMPLOYEE_PASSWORD = "test_password"
        const val TEST_EMPLOYEE_ID = "EMP001"
        
        // Expected navigation flow
        val EXPECTED_ACTIVITY_FLOW = listOf(
            "LoginActivity",
            "BiometricRegistrationActivity", 
            "DashboardActivity",
            "AttendanceActivity"
        )
        
        // Expected error scenarios
        val EXPECTED_ERROR_SCENARIOS = listOf(
            "Invalid credentials",
            "Network unavailable",
            "Biometric hardware not available",
            "API timeout",
            "Server error",
            "Data validation failure"
        )
        
        // Expected offline behavior
        val EXPECTED_OFFLINE_BEHAVIOR = listOf(
            "Store attendance locally",
            "Show offline indicator",
            "Queue for sync",
            "Resume sync when online"
        )
    }
} 