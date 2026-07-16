package com.example.lamaison.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.lamaison.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class PaymentMainManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_main_manager)

        // 1. Find the Bottom Navigation View
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 2. Find the NavController from the FragmentContainerView
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // 3. Link them together automatically!
        bottomNavigation.setupWithNavController(navController)
    }
}