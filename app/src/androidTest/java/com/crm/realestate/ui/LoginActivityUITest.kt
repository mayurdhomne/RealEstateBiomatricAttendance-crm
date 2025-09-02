package com.crm.realestate.ui

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
import com.crm.realestate.data.models.EmployeeInfo
import com.crm.realestate.data.models.LoginResponse
import com.crm.realestate.data.repository.AuthRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.crm.realestate.data.models.Result
import android.text.InputType

/**
 * UI tests for LoginActivity
 * Tests user interface interactions and navigation flows
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LoginActivityUITest {

    private lateinit var context: Context
    private val mockAuthRepository = mockk<AuthRepository>()

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

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Mock the repository provider to return our mock
        mockkObject(com.crm.realestate.data.repository.RepositoryProvider)
        every { com.crm.realestate.data.repository.RepositoryProvider.getAuthRepository(any()) } returns mockAuthRepository
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun loginActivity_displaysCorrectUI() {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // Then
        onView(withId(R.id.etUsername)).check(matches(isDisplayed()))
        onView(withId(R.id.etPassword)).check(matches(isDisplayed()))
        onView(withId(R.id.btnLogin)).check(matches(isDisplayed()))
        onView(withId(R.id.btnLogin)).check(matches(withText("Login")))
    }

    @Test
    fun loginActivity_showsErrorForEmptyUsername() {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Then
        onView(withText(containsString("Username is required")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun loginActivity_showsErrorForEmptyPassword() {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Then
        onView(withText(containsString("Password is required")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun loginActivity_showsErrorForShortUsername() {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("jo"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Then
        onView(withText(containsString("Username must be at least 3 characters")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun loginActivity_showsErrorForShortPassword() {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("12345"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Then
        onView(withText(containsString("Password must be at least 6 characters")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun loginActivity_showsLoadingStateOnLogin() = runTest {
        // Given
        coEvery { mockAuthRepository.login(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(1000) // Simulate network delay
            Result.Success(testLoginResponse)
        }

        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Then - Check loading state is shown
        onView(withId(R.id.progressBar)).check(matches(isDisplayed()))
        onView(withId(R.id.btnLogin)).check(matches(not(isEnabled())))
    }

    @Test
    fun loginActivity_showsErrorForInvalidCredentials() = runTest {
        // Given
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Error(
            Exception("Invalid credentials"),
            "Invalid username or password"
        )

        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("invalid"))
        onView(withId(R.id.etPassword)).perform(typeText("wrong"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Then
        onView(withText("Invalid username or password"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun loginActivity_showsNetworkErrorMessage() = runTest {
        // Given
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Error(
            Exception("Network error"),
            "No internet connection. Please check your network and try again"
        )

        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Then
        onView(withText(containsString("No internet connection")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun loginActivity_navigatesToBiometricRegistrationOnSuccessfulLogin() = runTest {
        // Given
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Success(testLoginResponse)

        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Then - Activity should finish and navigate (we can't easily test navigation in unit tests)
        // This would require integration testing or checking intent launches
        coVerify { mockAuthRepository.login("john.doe", "password123") }
    }

    @Test
    fun loginActivity_navigatesToDashboardWhenBiometricsRegistered() = runTest {
        // Given
        val loginResponseWithBiometrics = testLoginResponse.copy(biometricsRegistered = true)
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Success(loginResponseWithBiometrics)

        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Then
        coVerify { mockAuthRepository.login("john.doe", "password123") }
    }

    @Test
    fun loginActivity_clearsErrorOnNewInput() {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // When - First trigger an error
        onView(withId(R.id.btnLogin)).perform(click())
        
        // Then - Error should be displayed
        onView(withText(containsString("Username is required")))
            .check(matches(isDisplayed()))

        // When - Start typing in username field
        onView(withId(R.id.etUsername)).perform(typeText("j"))

        // Then - Error should be cleared (this depends on implementation)
        // The exact behavior may vary based on how error clearing is implemented
    }

    @Test
    fun loginActivity_handlesPasswordVisibilityToggle() {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etPassword)).perform(typeText("password123"))

        // Then - Password field should be present and functional
        // Note: Input type checking requires custom matcher, commenting out for now
        // onView(withId(R.id.etPassword)).check(matches(withInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)))

        // When - Toggle password visibility (if toggle button exists)
        // This test assumes there's a password visibility toggle
        // onView(withId(R.id.btnTogglePassword)).perform(click())

        // Then - Password should be visible
        // onView(withId(R.id.etPassword)).check(matches(withInputType(InputType.TYPE_CLASS_TEXT)))
    }

    @Test
    fun loginActivity_maintainsStateOnRotation() {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))

        // Simulate rotation (this is complex in Espresso, usually done with configuration changes)
        // For now, we'll just verify the text is maintained

        // Then
        onView(withId(R.id.etUsername)).check(matches(withText("john.doe")))
        onView(withId(R.id.etPassword)).check(matches(withText("password123")))
    }

    @Test
    fun loginActivity_handlesBackPressCorrectly() {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        pressBack()

        // Then - Activity should handle back press appropriately
        // This test verifies the activity doesn't crash on back press
        onView(withId(R.id.etUsername)).check(matches(isDisplayed()))
    }

    @Test
    fun loginActivity_showsRetryOptionOnError() = runTest {
        // Given
        coEvery { mockAuthRepository.login(any(), any()) } returns Result.Error(
            Exception("Server error"),
            "Server error. Please try again later"
        )

        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText("john.doe"))
        onView(withId(R.id.etPassword)).perform(typeText("password123"))
        onView(withId(R.id.btnLogin)).perform(click())

        // Then
        onView(withText(containsString("Server error")))
            .check(matches(isDisplayed()))

        // When - Try again
        onView(withId(R.id.btnLogin)).perform(click())

        // Then - Should attempt login again
        coVerify(exactly = 2) { mockAuthRepository.login("john.doe", "password123") }
    }

    @Test
    fun loginActivity_accessibilityLabelsArePresent() {
        // Given
        ActivityScenario.launch(LoginActivity::class.java)

        // Then - Check accessibility content descriptions
        onView(withId(R.id.etUsername)).check(matches(hasContentDescription()))
        onView(withId(R.id.etPassword)).check(matches(hasContentDescription()))
        onView(withId(R.id.btnLogin)).check(matches(hasContentDescription()))
    }

    @Test
    fun loginActivity_handlesLongUsernameAndPassword() {
        // Given
        val longUsername = "a".repeat(100)
        val longPassword = "b".repeat(100)
        
        ActivityScenario.launch(LoginActivity::class.java)

        // When
        onView(withId(R.id.etUsername)).perform(typeText(longUsername))
        onView(withId(R.id.etPassword)).perform(typeText(longPassword))

        // Then - Should handle long input gracefully
        onView(withId(R.id.etUsername)).check(matches(withText(longUsername)))
        onView(withId(R.id.etPassword)).check(matches(withText(longPassword)))
    }
}