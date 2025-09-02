package com.crm.realestate.activity

import android.app.Activity
import android.content.Intent
import org.junit.Test
import org.junit.Assert.*

class FacescanActivityTest {

    @Test
    fun testRegistrationModeConstants() {
        // Test that the constants are properly defined
        assertEquals("REGISTRATION_MODE", FacescanActivity.EXTRA_REGISTRATION_MODE)
        assertEquals("REGISTRATION_SUCCESS", FacescanActivity.EXTRA_REGISTRATION_SUCCESS)
        assertEquals("REGISTRATION_MESSAGE", FacescanActivity.EXTRA_REGISTRATION_MESSAGE)
        assertEquals("QUALITY_SCORE", FacescanActivity.EXTRA_QUALITY_SCORE)
    }

    @Test
    fun testRegistrationModeIntent() {
        // Test that registration mode intent can be created properly
        val intent = Intent().apply {
            putExtra(FacescanActivity.EXTRA_REGISTRATION_MODE, true)
        }
        
        assertTrue(intent.getBooleanExtra(FacescanActivity.EXTRA_REGISTRATION_MODE, false))
    }

    @Test
    fun testRegistrationResultIntent() {
        // Test that registration result intent can be created properly
        val resultIntent = Intent().apply {
            putExtra(FacescanActivity.EXTRA_REGISTRATION_SUCCESS, true)
            putExtra(FacescanActivity.EXTRA_REGISTRATION_MESSAGE, "Test message")
            putExtra(FacescanActivity.EXTRA_QUALITY_SCORE, 0.85f)
        }
        
        assertTrue(resultIntent.getBooleanExtra(FacescanActivity.EXTRA_REGISTRATION_SUCCESS, false))
        assertEquals("Test message", resultIntent.getStringExtra(FacescanActivity.EXTRA_REGISTRATION_MESSAGE))
        assertEquals(0.85f, resultIntent.getFloatExtra(FacescanActivity.EXTRA_QUALITY_SCORE, 0f), 0.01f)
    }

    @Test
    fun testRegistrationFailureIntent() {
        // Test that registration failure intent can be created properly
        val resultIntent = Intent().apply {
            putExtra(FacescanActivity.EXTRA_REGISTRATION_SUCCESS, false)
            putExtra(FacescanActivity.EXTRA_REGISTRATION_MESSAGE, "Registration failed")
            putExtra(FacescanActivity.EXTRA_QUALITY_SCORE, 0f)
        }
        
        assertFalse(resultIntent.getBooleanExtra(FacescanActivity.EXTRA_REGISTRATION_SUCCESS, true))
        assertEquals("Registration failed", resultIntent.getStringExtra(FacescanActivity.EXTRA_REGISTRATION_MESSAGE))
        assertEquals(0f, resultIntent.getFloatExtra(FacescanActivity.EXTRA_QUALITY_SCORE, 1f), 0.01f)
    }
}