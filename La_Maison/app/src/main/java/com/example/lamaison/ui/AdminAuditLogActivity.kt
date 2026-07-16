package com.example.lamaison.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.AuditLog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class AdminAuditLogActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvFullAuditLogs: RecyclerView
    private lateinit var auditAdapter: AuditLogAdapter

    private lateinit var tvTotalLogsCount: TextView
    private lateinit var tvFeedTitle: TextView
    private lateinit var btnFilter: TextView
    private lateinit var bottomNav: BottomNavigationView

    private var activeSnapshotListener: ListenerRegistration? = null
    private var isFilterApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_audit_log)

        // --- 1. Bind UI Views ---
        rvFullAuditLogs = findViewById(R.id.rvFullAuditLogs)
        tvTotalLogsCount = findViewById(R.id.tvTotalLogsCount)
        tvFeedTitle = findViewById(R.id.tvFeedTitle)
        btnFilter = findViewById(R.id.btnFilter)
        bottomNav = findViewById(R.id.bottom_navigation_audit)

        // --- 2. Setup Adapter ---
        rvFullAuditLogs.layoutManager = LinearLayoutManager(this)
        auditAdapter = AuditLogAdapter(emptyList())
        rvFullAuditLogs.adapter = auditAdapter

        // Load the default feed (most recent 100 logs)
        loadSurveillanceFeed(null, null)

        // --- 3. DYNAMIC FILTER LOGIC (1-BUTTON DESIGN) ---
        btnFilter.setOnClickListener {
            if (isFilterApplied) {
                // STATE 2: The filter is active, so clicking it CLEARS the filter
                isFilterApplied = false
                btnFilter.text = "FILTER"
                tvFeedTitle.text = "REAL-TIME SYSTEM RECEIPTS"
                loadSurveillanceFeed(null, null)
                Toast.makeText(this, "Filter cleared.", Toast.LENGTH_SHORT).show()

            } else {
                // STATE 1: No filter is active, so clicking it OPENS the calendar
                val datePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Select Audit Date Range")
                    .build()

                datePicker.addOnPositiveButtonClickListener { selection ->
                    val startDateMs = selection.first
                    val endDateMs = selection.second

                    if (startDateMs != null && endDateMs != null) {
                        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                        tvFeedTitle.text = "FILTERED: ${sdf.format(Date(startDateMs))} - ${sdf.format(Date(endDateMs))}"

                        // Transform the button into a "CLEAR" button
                        isFilterApplied = true
                        btnFilter.text = "CLEAR"

                        loadSurveillanceFeed(startDateMs, endDateMs)
                    }
                }
                datePicker.show(supportFragmentManager, "DATE_PICKER")
            }
        }

        // --- 4. BOTTOM NAVIGATION SETUP ---
        bottomNav.selectedItemId = R.id.nav_admin_logs
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_admin_home -> {
                    val intent = Intent(this, AdminDashboard::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_admin_logs -> true
                R.id.nav_admin_profile -> {
                    startActivity(Intent(this, UserProfileActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    /**
     * Dynamically queries the logs. If dates are provided, it filters them.
     * If no dates are provided, it simply pulls the latest 100.
     */
    private fun loadSurveillanceFeed(startDate: Long?, endDate: Long?) {
        // Kill the old listener before creating a new filtered one to prevent duplicate rendering
        activeSnapshotListener?.remove()

        var query = db.collection("AUDIT_LOGS")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        if (startDate != null && endDate != null) {
            // Adjust end date to capture the very end of the selected day (11:59 PM)
            val endOfDay = endDate + (24 * 60 * 60 * 1000) - 1
            query = query
                .whereGreaterThanOrEqualTo("timestamp", startDate)
                .whereLessThanOrEqualTo("timestamp", endOfDay)
        } else {
            // Default cap for performance
            query = query.limit(100)
        }

        activeSnapshotListener = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                Toast.makeText(this, "Error syncing feed: ${error.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshots != null && !snapshots.isEmpty) {
                tvTotalLogsCount.text = "${snapshots.size()} Receipts Logged"
                val logList = snapshots.documents.mapNotNull { it.toObject(AuditLog::class.java) }
                auditAdapter.updateData(logList)
            } else {
                tvTotalLogsCount.text = "0 Receipts"
                auditAdapter.updateData(emptyList())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bottomNav.selectedItemId = R.id.nav_admin_logs
    }

    override fun onDestroy() {
        super.onDestroy()
        activeSnapshotListener?.remove() // Prevents memory leaks!
    }
}