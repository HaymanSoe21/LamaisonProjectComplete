package com.example.lamaison.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.MaintenanceTicket
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagerMaintenanceHubActivity : AppCompatActivity() {

    private lateinit var rvTickets: RecyclerView
    private lateinit var adapter: ManagerMaintenanceAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var allInboxTickets = listOf<MaintenanceTicket>()
    private var allLogTickets = listOf<MaintenanceTicket>()
    private var isLogActive = false
    private var isFilterApplied = false

    private lateinit var layoutDateFilter: LinearLayout
    private lateinit var btnFilterLog: TextView
    private lateinit var tvHubTitle: TextView
    private lateinit var tvSectionLabel: TextView

    private var activeStartDateMs: Long? = null
    private var activeEndDateMs: Long? = null
    private var activeTicketsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_maintenance_hub)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        tvHubTitle = findViewById(R.id.tvHubTitle)

        rvTickets = findViewById(R.id.rvManagerTickets)
        rvTickets.layoutManager = LinearLayoutManager(this)

        adapter = ManagerMaintenanceAdapter(emptyList(), isLogActive) { clickedTicket ->
            val intent = Intent(this, ManagerTicketDetailsActivity::class.java)
            intent.putExtra("TICKET_ID", clickedTicket.ticketID)
            intent.putExtra("IS_FROM_LOG", isLogActive)
            startActivity(intent)
        }
        rvTickets.adapter = adapter

        layoutDateFilter = findViewById(R.id.layoutDateFilter)
        btnFilterLog = findViewById(R.id.btnFilterLog)
        tvSectionLabel = findViewById(R.id.tvSectionLabel)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation_maintenance)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_maint_inbox -> {
                    isLogActive = false
                    isFilterApplied = false
                    tvHubTitle.text = "Maintenance Inbox"
                    tvSectionLabel.text = "ACTIVE TICKETS"
                    btnFilterLog.visibility = View.GONE
                    adapter.isLogMode = false
                    adapter.updateData(allInboxTickets)
                    true
                }
                R.id.nav_maint_log -> {
                    isLogActive = true
                    isFilterApplied = false
                    tvHubTitle.text = "Maintenance History"
                    tvSectionLabel.text = "RESOLVED & ARCHIVED"
                    btnFilterLog.visibility = View.VISIBLE
                    btnFilterLog.text = "FILTER"
                    adapter.isLogMode = true
                    displaySortedLogTickets(allLogTickets)
                    true
                }
                else -> false
            }
        }

        // --- 1-BUTTON DYNAMIC FILTER LOGIC ---
        btnFilterLog.setOnClickListener {
            if (isFilterApplied) {
                // STATE 2: Filter active -> Clear filter and revert list
                isFilterApplied = false
                btnFilterLog.text = "FILTER"
                tvSectionLabel.text = "RESOLVED & ARCHIVED"
                activeStartDateMs = null
                activeEndDateMs = null
                displaySortedLogTickets(allLogTickets)
                Toast.makeText(this, "Filter cleared. Showing all history.", Toast.LENGTH_SHORT).show()

            } else {
                // STATE 1: No filter -> Open Material Date Range Picker
                val datePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Select Maintenance Date Range")
                    .build()

                datePicker.addOnPositiveButtonClickListener { selection ->
                    val startDateMs = selection.first
                    val endDateMs = selection.second

                    if (startDateMs != null && endDateMs != null) {
                        val endOfDayMs = endDateMs + (24 * 60 * 60 * 1000) - 1

                        isFilterApplied = true
                        btnFilterLog.text = "CLEAR"
                        activeStartDateMs = startDateMs
                        activeEndDateMs = endOfDayMs

                        filterLogByDate()
                    }
                }
                datePicker.show(supportFragmentManager, "DATE_PICKER")
            }
        }

        fetchTickets()
    }

    /**
     * Identifies units supervised by this manager so zero supervised tickets are missed[cite: 11].
     */
    private fun fetchTickets() {
        val managerId = auth.currentUser?.uid ?: return
        db.collection("UNITS").whereEqualTo("assignedManagerID", managerId).get()
            .addOnSuccessListener { unitSnapshots ->
                val assignedUnitIds = unitSnapshots.documents.map { it.id }
                if (assignedUnitIds.isNotEmpty()) {
                    listenToTickets(assignedUnitIds)
                }
            }
    }

    private fun listenToTickets(assignedUnitIds: List<String>) {
        activeTicketsListener?.remove()

        activeTicketsListener = db.collection("MAINTENANCE_TICKETS")
            .orderBy("submissionDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val allTickets = snapshot.toObjects(MaintenanceTicket::class.java)
                val managerTickets = allTickets.filter { it.unitID in assignedUnitIds }

                // Sorts the Active Inbox by submission date (Oldest First)[cite: 11]
                allInboxTickets = managerTickets.filter {
                    it.status.equals("Pending", ignoreCase = true) || it.status.equals("In-Progress", ignoreCase = true)
                }.sortedBy { it.submissionDate }

                // History Log sorted by last updated (Newest First)[cite: 11]
                allLogTickets = managerTickets.filter {
                    it.status.equals("In-Progress", ignoreCase = true) || it.status.equals("Resolved", ignoreCase = true)
                }.sortedByDescending { it.lastUpdatedDate }

                if (isLogActive) {
                    if (isFilterApplied) {
                        filterLogByDate()
                    } else {
                        displaySortedLogTickets(allLogTickets)
                    }
                } else {
                    adapter.updateData(allInboxTickets)
                }
            }
    }

    private fun filterLogByDate() {
        val fromTime = activeStartDateMs ?: return
        val toTime = activeEndDateMs ?: return

        val filtered = allLogTickets
            .filter { it.lastUpdatedDate in fromTime..toTime }
            .sortedByDescending { it.lastUpdatedDate }

        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
        tvSectionLabel.text = "FILTERED: ${sdf.format(Date(fromTime))} - ${sdf.format(Date(toTime))} (${filtered.size})"
        adapter.updateData(filtered)

        if (filtered.isEmpty()) {
            Toast.makeText(this, "No maintenance records found in this date range.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displaySortedLogTickets(tickets: List<MaintenanceTicket>) {
        val sorted = tickets.sortedByDescending { it.lastUpdatedDate }
        adapter.updateData(sorted)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeTicketsListener?.remove()
    }
}