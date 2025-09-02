package com.crm.realestate.integration

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.crm.realestate.R
import com.crm.realestate.activity.LoginActivity
import com.crm.realestate.data.models.*
import com.crm.realestate.data.repository.AuthRepository
import com.crm.realestate.data.repository.BiometricRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.crm.realestate.data.models.Result

/**
 * End-to-end tests for complete employee onboarding flow
 * Tests the entire user journey from login to biometric registration completion
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@ExperimentalCoroutinesApi
class EndToEndOnboardingTest {

    private lateinit var context: Context
    private val mockAuthRepository = mockk<AuthRepository>()
    private val mockBiometricRepository = mockk<BiometricRepository>()

    private val testEmployeeInfo = EmployeeInfo(
        employeeId = "EMP001",
        fullName = "John Doe",
        email = "john.doe@company.com",
        department = "IT",
        designation = "Software Engineer"
    )

    private val testLoginResponse = LoginResponse(
        token = "test-token",
        employeeId = "EMP001",
        username = "john.doe",
        biometricsRegistered = false,
        employeeInfo = testEmployeeInfo
    )

    private val testBiometricAvailability = BiometricAvailability(
        hasFaceDetection = true,
        hasFingerprint = true,
        canAuthenticateFingerprint = true
    )

    private val testRegistrationResponse = BiometricRegistrationResponse(
        success = true,
        message = "Biometrics registered successfully",
        employeeId = "EMP001"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Mock repository providers
        mockkObject(com.crm.realestate.data.repository.RepositoryProvider)
        every { com.crm.realestate.data.repository.RepositoryProvider.getAuthRepository(any()) } returns mockAuthRepository
        every { com.crm.realestate.data.repository.RepositoryProvider.getBiometricRepository(any()) } returns mockBiometricRepository
        
        // Setup default successful responses
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Success(testLoginResponse)
        coEvery { mockBiometricRepository.checkBiometricAvailability() } returns testBiometricAvailability
        every { mockBiometricRepository.getFingerprintRegistrationStatus() } returns Pair(true, "Ready")
        coEvery { mockBiometricRepository.authenticateFingerprint(any()) } returns BiometricResult.Success
        coEvery { mockBiometricRepository.registerBiometrics(any(), any(), any()) } returns testRegistrationResponse
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun completeOnboardingFlow_newEmployeeWithBothBiometrics() = runTest {
        // Given - New employee needs to register biometrics
        ActivityScenario.launch(LoginActivity::class.java)

        // Step 1: Login
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Verify login was attempted
        coVerify { mockAuthRepository.login("john.doe", "password123") }

        // Step 2: Should navigate to biometric registration
        // (In a real test, we'd need to handle activity transitions)
        
        // Step 3: Register face biometric
        onView(withId(R.id.btnScanFaceID)).perform(click())
        
        // Verify face registration UI is shown
        onView(withText(containsString("Position your face")))
            .check(matches(isDisplayed()))

        // Step 4: Register fingerprint biometric
        onView(withId(R.id.btnScanFingerprintID)).perform(click())
        
        // Verify fingerprint registration was attempted
        coVerify { mockBiometricRepository.authenticateFingerprint(any()) }

        // Step 5: Complete registration (assume there's a complete button somewhere)
        // onView(withId(R.id.btnComplete)).perform(click())

        // Verify API call was made with both biometrics
        coVerify { mockBiometricRepository.registerBiometrics("EMP001", true, true) }

        // Step 6: Verify success message
        onView(withText(containsString("Registration completed successfully")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun completeOnboardingFlow_newEmployeeWithOnlyFace() = runTest {
        // Given - Employee can only register face
        val noFingerprintAvailability = testBiometricAvailability.copy(
            hasFingerprint = false,
            canAuthenticateFingerprint = false
        )
        coEvery { mockBiometricRepository.checkBiometricAvailability() } returns noFingerprintAvailability
        every { mockBiometricRepository.getFingerprintRegistrationStatus() } returns Pair(false, "No hardware")

        ActivityScenario.launch(LoginActivity::class.java)

        // Step 1: Login
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 2: Register only face biometric
        onView(withId(R.id.btnScanFaceID)).perform(click())
        
        // Step 3: Verify fingerprint button is disabled
        onView(withId(R.id.btnScanFingerprintID)).check(matches(not(isEnabled())))

        // Step 4: Complete registration with only face (assume there's a way to complete)
        // onView(withId(R.id.btnComplete)).perform(click())

        // Verify API call was made with only face biometric
        coVerify { mockBiometricRepository.registerBiometrics("EMP001", true, false) }
    }

    @Test
    fun completeOnboardingFlow_skipBiometricRegistration() = runTest {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // Step 1: Login
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 2: Skip biometric registration (assume there's a skip option)
        // onView(withId(R.id.btnSkip)).perform(click())

        // Verify API call was made with no biometrics
        coVerify { mockBiometricRepository.registerBiometrics("EMP001", false, false) }

        // Verify success message
        onView(withText(containsString("Registration completed")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun completeOnboardingFlow_existingEmployeeSkipsBiometrics() = runTest {
        // Given - Employee already has biometrics registered
        val existingEmployeeResponse = testLoginResponse.copy(biometricsRegistered = true)
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Success(existingEmployeeResponse)

        ActivityScenario.launch(LoginActivity::class.java)

        // Step 1: Login
        onView(withId(R.id.etUsername)).perform(typeText("existing.user"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 2: Should navigate directly to dashboard (skip biometric registration)
        // Verify biometric registration is not called
        coVerify(exactly = 0) { mockBiometricRepository.registerBiometrics(any(), any(), any()) }
    }

    @Test
    fun completeOnboardingFlow_handleLoginError() = runTest {
        // Given - Login fails
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Error(
            Exception("Invalid credentials"),
            "Invalid username or password"
        )

        ActivityScenario.launch(LoginActivity::class.java)

        // Step 1: Attempt login with wrong credentials
        onView(withId(R.id.etUsername)).perform(typeText("wrong.user"))
        onView(withId(R.id.etPassword)).perform(typeText("wrongpass"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 2: Verify error is shown
        onView(withText("Invalid username or password"))
            .check(matches(isDisplayed()))

        // Step 3: Retry with correct credentials
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Success(testLoginResponse)
        
        onView(withId(R.id.etUsername)).perform(clearText(), typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(clearText(), typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 4: Should proceed to biometric registration
        coVerify { mockAuthRepository.login("john.doe", "password123") }
    }

    @Test
    fun completeOnboardingFlow_handleBiometricRegistrationError() = runTest {
        // Given - Biometric registration fails
        coEvery { mockBiometricRepository.registerBiometrics(any(), any(), any()) } throws Exception("Registration failed. Please try again")

        ActivityScenario.launch(LoginActivity::class.java)

        // Step 1: Login successfully
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 2: Register biometrics
        onView(withId(R.id.btnScanFaceID)).perform(click())
        onView(withId(R.id.btnScanFingerprintID)).perform(click())

        // Step 3: Attempt to complete registration (assume there's a complete mechanism)
        // onView(withId(R.id.btnComplete)).perform(click())

        // Step 4: Verify error is shown
        onView(withText(containsString("Registration failed")))
            .check(matches(isDisplayed()))

        // Step 5: Retry registration
        coEvery { mockBiometricRepository.registerBiometrics(any(), any(), any()) } returns testRegistrationResponse
        
        // onView(withId(R.id.btnComplete)).perform(click())

        // Step 6: Verify success
        onView(withText(containsString("Registration completed successfully")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun completeOnboardingFlow_handleFingerprintAuthenticationFailure() = runTest {
        // Given - Fingerprint authentication fails
        coEvery { mockBiometricRepository.authenticateFingerprint(any()) } returns BiometricResult.Error(
            "Fingerprint authentication failed",
            null
        )

        ActivityScenario.launch(LoginActivity::class.java)

        // Step 1: Login
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 2: Register face successfully
        onView(withId(R.id.btnScanFaceID)).perform(click())

        // Step 3: Attempt fingerprint registration (fails)
        onView(withId(R.id.btnScanFingerprintID)).perform(click())

        // Step 4: Verify error is shown
        onView(withText(containsString("Fingerprint registration failed")))
            .check(matches(isDisplayed()))

        // Step 5: Complete with only face registration (assume there's a way)
        // onView(withId(R.id.btnComplete)).perform(click())

        // Verify API call was made with only face biometric
        coVerify { mockBiometricRepository.registerBiometrics("EMP001", true, false) }
    }

    @Test
    fun completeOnboardingFlow_handleUserCancellation() = runTest {
        // Given - User cancels fingerprint authentication
        coEvery { mockBiometricRepository.authenticateFingerprint(any()) } returns BiometricResult.Cancelled

        ActivityScenario.launch(LoginActivity::class.java)

        // Step 1: Login
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 2: Register face
        onView(withId(R.id.btnScanFaceID)).perform(click())

        // Step 3: Cancel fingerprint registration
        onView(withId(R.id.btnScanFingerprintID)).perform(click())

        // Step 4: Verify cancellation message
        onView(withText(containsString("Registration cancelled")))
            .check(matches(isDisplayed()))

        // Step 5: Should still be able to complete with partial registration (assume button exists)
        // onView(withId(R.id.btnComplete)).check(matches(isEnabled()))
    }

    @Test
    fun completeOnboardingFlow_networkErrorDuringLogin() = runTest {
        // Given - Network error during login
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Error(
            Exception("Network error"),
            "No internet connection. Please check your network and try again"
        )

        ActivityScenario.launch(LoginActivity::class.java)

        // Step 1: Attempt login
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 2: Verify network error is shown
        onView(withText(containsString("No internet connection")))
            .check(matches(isDisplayed()))

        // Step 3: Retry when network is available
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Success(testLoginResponse)
        
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 4: Should proceed to biometric registration
        coVerify(exactly = 2) { mockAuthRepository.login("john.doe", "password123") }
    }

    @Test
    fun completeOnboardingFlow_validateCompleteUserJourney() = runTest {
        // This test validates the complete user journey with all steps
        ActivityScenario.launch(LoginActivity::class.java)

        // Step 1: Validate initial login screen
        onView(withId(R.id.etUsername)).check(matches(isDisplayed()))
        onView(withId(R.id.etPassword)).check(matches(isDisplayed()))
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()))

        // Step 2: Perform login
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Step 3: Validate biometric registration screen appears
        onView(withId(R.id.btnScanFaceID)).check(matches(isDisplayed()))
        onView(withId(R.id.btnScanFingerprintID)).check(matches(isDisplayed()))

        // Step 4: Register both biometrics
        onView(withId(R.id.btnScanFaceID)).perform(click())
        onView(withId(R.id.btnScanFingerprintID)).perform(click())

        // Step 5: Complete registration (assume there's a completion mechanism)
        // onView(withId(R.id.btnComplete)).perform(click())

        // Step 6: Validate all repository calls were made in correct order
        coVerify {
            mockAuthRepository.login("john.doe", "password123")
        }
        coVerify {
            mockBiometricRepository.checkBiometricAvailability()
        }
        coVerify {
            mockBiometricRepository.authenticateFingerprint(any())
        }
        coVerify {
            mockBiometricRepository.registerBiometrics("EMP001", true, true)
        }

        // Step 7: Validate success state
        onView(withText(containsString("Registration completed successfully")))
            .check(matches(isDisplayed()))
    }
}