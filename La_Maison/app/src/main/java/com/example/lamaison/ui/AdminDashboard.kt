package com.example.lamaison.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.lamaison.R
import com.example.lamaison.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AdminDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var sessionManager: SessionManager
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var tvAdminName: TextView
    private lateinit var tvActiveStaffCount: TextView
    private lateinit var tvAnomalyCount: TextView

    // Listeners
    private var staffTokenListener: ListenerRegistration? = null
    private var leaseAnomalyListener: ListenerRegistration? = null
    private var unitAnomalyListener: ListenerRegistration? = null

    // Tracking variables to combine the counts
    private var expiredLeaseCount = 0
    private var ghostUnitCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        sessionManager = SessionManager(this)

        tvAdminName = findViewById(R.id.tvAdminName)
        tvActiveStaffCount = findViewById(R.id.tvActiveStaffCount)
        tvAnomalyCount = findViewById(R.id.tvAnomalyCount)
        bottomNav = findViewById(R.id.bottom_navigation_admin)

        val btnSignOutAdmin = findViewById<TextView>(R.id.btnSignOutAdmin)
        val btnActionStaffIam = findViewById<LinearLayout>(R.id.btnActionStaffIam)
        val btnActionDataHygiene = findViewById<LinearLayout>(R.id.btnActionDataHygiene)

        loadAdminProfile()
        loadDiagnosticCounters()

        // --- QUICK ACTION NAVIGATION ---
        btnActionStaffIam.setOnClickListener {
            startActivity(Intent(this, StaffIamActivity::class.java))
        }

        btnActionDataHygiene.setOnClickListener {
            startActivity(Intent(this, AdminDataHygieneActivity::class.java))
        }

        // --- BOTTOM NAVIGATION BAR ---
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_admin_home -> true
                R.id.nav_admin_logs -> {
                    startActivity(Intent(this, AdminAuditLogActivity::class.java))
                    false
                }
                R.id.nav_admin_profile -> {
                    startActivity(Intent(this, UserProfileActivity::class.java))
                    false
                }
                else -> false
            }
        }

        // --- SIGN OUT ---
        btnSignOutAdmin.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_signout, null)
            val customDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            customDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialogView.findViewById<Button>(R.id.btnDialogCancel).setOnClickListener { customDialog.dismiss() }
            dialogView.findViewById<Button>(R.id.btnDialogConfirm).setOnClickListener {
                customDialog.dismiss()
                sessionManager.logout()
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            customDialog.show()
        }
    }

    private fun loadAdminProfile() {
        val cachedName = sessionManager.getCachedName()
        tvAdminName.text = if (cachedName != null) "Welcome, $cachedName" else "Welcome, Systems Admin"

        val userId = auth.currentUser?.uid ?: return
        db.collection("USERS").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val fullName = doc.getString("fullName") ?: "Executive Admin"
                    tvAdminName.text = "Welcome, $fullName"
                    sessionManager.saveUserSession(userId, fullName, "Admin")
                }
            }
    }

    private fun loadDiagnosticCounters() {
        // 1. IAM: Active Manager Tokens
        staffTokenListener = db.collection("MANAGER_TOKENS").whereEqualTo("status", "ACTIVE").addSnapshotListener { snaps, _ ->
            tvActiveStaffCount.text = snaps?.size()?.toString() ?: "0"
        }

        // 2. HYGIENE A: Count Leases that need to be Synced (Expired)
        leaseAnomalyListener = db.collection("LEASES")
            .whereEqualTo("status", "Active")
            .addSnapshotListener { snaps, _ ->
                if (snaps != null && !snaps.isEmpty) {
                    val currentTime = System.currentTimeMillis()
                    expiredLeaseCount = snaps.documents.count {
                        val endDate = it.getLong("endDate") ?: 0L
                        endDate in 1..currentTime
                    }
                } else {
                    expiredLeaseCount = 0
                }
                updateAnomalyTotal()
            }

        // 3. HYGIENE B: Count Ghost Units (Occupied but missing Tenant ID)
        unitAnomalyListener = db.collection("UNITS")
            .whereEqualTo("occupancyStatus", "Occupied")
            .whereEqualTo("currentTenantID", "")
            .addSnapshotListener { snaps, _ ->
                ghostUnitCount = snaps?.size() ?: 0
                updateAnomalyTotal()
            }
    }

    private fun updateAnomalyTotal() {
        // Combine both hygiene scans into one central diagnostic number
        val totalAnomalies = expiredLeaseCount + ghostUnitCount
        tvAnomalyCount.text = totalAnomalies.toString()

        // Visual indicator: Turn green if clean, red if anomalies exist
        if (totalAnomalies == 0) {
            tvAnomalyCount.setTextColor(android.graphics.Color.parseColor("#047857")) // Green
        } else {
            tvAnomalyCount.setTextColor(android.graphics.Color.parseColor("#DC2626")) // Red
        }
    }

    override fun onResume() {
        super.onResume()
        if (::bottomNav.isInitialized) {
            bottomNav.selectedItemId = R.id.nav_admin_home
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        staffTokenListener?.remove()
        leaseAnomalyListener?.remove()
        unitAnomalyListener?.remove()
    }
}