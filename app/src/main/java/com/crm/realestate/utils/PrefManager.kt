package com.crm.realestate.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Preference Manager to handle user session and app preferences
 */
class PrefManager(context: Context) {

    private val pref: SharedPreferences
    private val editor: SharedPreferences.Editor
    private val PRIVATE_MODE = 0
    
    // Shared preferences file name
    private val PREF_NAME = "real-estate-biometric-prefs"
    
    // Shared preferences keys
    private val KEY_IS_LOGGED_IN = "isLoggedIn"
    private val KEY_USER_ID = "userId"
    private val KEY_USER_NAME = "userName"
    private val KEY_USER_EMAIL = "userEmail"

    init {
        pref = context.getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        editor = pref.edit()
    }

    /**
     * Set login status
     */
    fun setLogin(isLoggedIn: Boolean) {
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
        editor.commit()
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    /**
     * Store user details
     */
    fun storeUserDetails(userId: String, name: String, email: String) {
        editor.putString(KEY_USER_ID, userId)
        editor.putString(KEY_USER_NAME, name)
        editor.putString(KEY_USER_EMAIL, email)
        editor.commit()
    }

    /**
     * Get user ID
     */
    fun getUserId(): String? {
        return pref.getString(KEY_USER_ID, null)
    }

    /**
     * Get user name
     */
    fun getUserName(): String? {
        return pref.getString(KEY_USER_NAME, null)
    }

    /**
     * Get user email
     */
    fun getUserEmail(): String? {
        return pref.getString(KEY_USER_EMAIL, null)
    }

    /**
     * Clear all data when user logs out
     */
    fun clearSession() {
        editor.clear()
        editor.commit()
    }
}
