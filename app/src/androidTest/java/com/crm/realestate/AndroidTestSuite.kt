package com.crm.realestate

import com.crm.realestate.integration.EndToEndOnboardingTest
import com.crm.realestate.ui.BiometricRegistrationUITest
import com.crm.realestate.ui.LoginActivityUITest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Android instrumentation test suite for UI and end-to-end testing
 * 
 * This test suite includes:
 * - UI tests for critical user flows
 * - End-to-end tests for complete user journeys
 * - Integration tests that require Android framework components
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // UI Tests - Critical User Flows
    LoginActivityUITest::class,
    BiometricRegistrationUITest::class,
    
    // End-to-End Tests - Complete User Journeys
    EndToEndOnboardingTest::class
)
class AndroidTestSuite {
    
    companion object {
        /**
         * Android test coverage summary:
         * 
         * UI Tests:
         * - LoginActivity: Form validation, error handling, loading states, navigation
         * - BiometricRegistrationActivity: Registration flow, hardware detection, progress tracking
         * 
         * End-to-End Tests:
         * - Complete onboarding flow: Login → Biometric Registration → Dashboard
         * - Error recovery scenarios: Network errors, authentication failures
         * - Partial registration flows: Single biometric, skip registration
         * - Hardware limitation scenarios: No camera, no fingerprint sensor
         * 
         * Integration Tests:
         * - Real Android framework components
         * - Actual UI interactions with Espresso
         * - Activity lifecycle management
         * - Intent navigation testing
         */
        
        const val ANDROID_TEST_COUNT = 50 // Approximate number of Android test methods
        
        /**
         * Test execution guidelines:
         * 
         * 1. Run unit tests first (fast feedback):
         *    ./gradlew test
         * 
         * 2. Run Android instrumentation tests (slower):
         *    ./gradlew connectedAndroidTest
         * 
         * 3. Run specific test categories:
         *    ./gradlew testDebugUnitTest --tests "*.repository.*"
         *    ./gradlew testDebugUnitTest --tests "*.performance.*"
         *    ./gradlew testDebugUnitTest --tests "*.security.*"
         * 
         * 4. Generate test reports:
         *    ./gradlew test jacocoTestReport
         * 
         * 5. Run performance tests separately:
         *    ./gradlew testDebugUnitTest --tests "*.performance.*"
         */
    }
}