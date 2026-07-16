package com.example.lamaison.ui

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lamaison.R
import com.example.lamaison.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        sessionManager = SessionManager(this)

        // Failsafe: If somehow already logged in, redirect immediately
        if (sessionManager.isLoggedIn() && auth.currentUser != null) {
            routeToDashboard(sessionManager.getUserRole())
            return
        }

        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvSignUpAction = findViewById<TextView>(R.id.tvSignUpAction)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        tvSignUpAction.paintFlags = tvSignUpAction.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        tvForgotPassword.paintFlags = tvForgotPassword.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        tvForgotPassword.setOnClickListener {
            val emailAddress = etEmail.text.toString().trim()
            if (emailAddress.isEmpty()) {
                Toast.makeText(this, "Please enter your email in the field above first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(emailAddress)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Password reset email sent! Check your inbox.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter your email and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: return@addOnCompleteListener

                        FirebaseFirestore.getInstance().collection("USERS").document(userId).get()
                            .addOnSuccessListener { document ->
                                if (document != null && document.exists()) {
                                    val role = document.getString("role") ?: "Tenant"
                                    val fullName = document.getString("fullName") ?: "User"

                                    // Save the core session
                                    sessionManager.saveUserSession(userId, fullName, role)

                                    if (role == "Tenant") {
                                        sessionManager.saveTenantUnitProfile("", "", "...", "Loading Suite details...", "")
                                        sessionManager.saveTenantFinancials("$0.00", "Calculating...")
                                    } else if (role == "Manager") {
                                        sessionManager.saveManagerDetails(fullName, "Assigned Jurisdiction: La Maison Diamond Wing")
                                    }

                                    routeToDashboard(role)
                                } else {
                                    Toast.makeText(this@LoginActivity, "User profile not found in database.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this@LoginActivity, "Database Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvSignUpAction.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun routeToDashboard(role: String?) {
        val targetActivity = when (role) {
            "Tenant" -> TenantDashboard::class.java
            "Manager" -> ManagerDashboard::class.java
            "Admin" -> AdminDashboard::class.java
            else -> null
        }

        if (targetActivity != null) {
            val intent = Intent(this, targetActivity)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            Toast.makeText(this, "Error: Unknown role assigned to this account.", Toast.LENGTH_LONG).show()
        }
    }
}