package com.crm.realestate.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.crm.realestate.R
import com.crm.realestate.utils.PrefManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    
    private val SPLASH_DURATION = 3000L // 3 seconds
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)
        
        // Wait for the animation to play, then navigate to the appropriate screen
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, SPLASH_DURATION)
    }
    
    private fun navigateToNextScreen() {
        val prefManager = PrefManager(this)
        
        // Decide which activity to open based on login status
        val intent = if (prefManager.isLoggedIn()) {
            Intent(this, DashboardActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        
        startActivity(intent)
        finish() // Close the splash activity so user can't go back to it
    }
}