package com.example.lamaison.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.lamaison.R // Make sure to import R
import com.example.lamaison.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ADD THIS LINE: Replace 'activity_splash' with your actual layout file name
        setContentView(R.layout.activity_splash)

        val sessionManager = SessionManager(this)
        val auth = FirebaseAuth.getInstance()

        Handler(Looper.getMainLooper()).postDelayed({
            // Ensure SharedPreferences says they are logged in AND Firebase agrees
            if (sessionManager.isLoggedIn() && auth.currentUser != null) {
                val role = sessionManager.getUserRole()

                val targetActivity = when (role) {
                    "Tenant" -> TenantDashboard::class.java
                    "Manager" -> ManagerDashboard::class.java
                    "Admin" -> AdminDashboard::class.java
                    else -> LoginActivity::class.java
                }

                startActivity(Intent(this, targetActivity))
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 1500)
    }
}