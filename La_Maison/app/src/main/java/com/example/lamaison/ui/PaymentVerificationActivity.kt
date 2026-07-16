package com.example.lamaison.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.lamaison.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class PaymentVerificationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_verification)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation_verification)

        // Load Inbox by default
        if (savedInstanceState == null) {
            loadFragment(PaymentInboxFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inbox -> {
                    loadFragment(PaymentInboxFragment())
                    true
                }
                R.id.nav_audit_trail -> {
                    loadFragment(PaymentAuditTrailFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}