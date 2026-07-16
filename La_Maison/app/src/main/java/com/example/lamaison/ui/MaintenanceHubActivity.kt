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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.firestore.ListenerRegistration

class MaintenanceHubActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tabLayout: TabLayout
    private lateinit var rvTickets: RecyclerView
    private lateinit var llEmptyHistoryState: LinearLayout
    private lateinit var llFilterContainer: LinearLayout
    private lateinit var btnFilterHistory: TextView
    private lateinit var tvHistoryTitle: TextView
    private lateinit var btnBackHub: ImageView
    private lateinit var ticketAdapter: MaintenanceTicketAdapter

    private var activeTicketListener: ListenerRegistration? = null

    private var baseFilteredList: List<MaintenanceTicket> = emptyList()
    private var insideHistoryTab: Boolean = false
    private var isFilterApplied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maintenance_hub)

        tabLayout = findViewById(R.id.tabLayoutMaintenance)
        rvTickets = findViewById(R.id.rvMaintenanceTickets)
        llEmptyHistoryState = findViewById(R.id.llEmptyHistoryState)
        llFilterContainer = findViewById(R.id.llFilterContainer)
        btnFilterHistory = findViewById(R.id.btnFilterHistory)
        tvHistoryTitle = findViewById(R.id.tvHistoryTitle)
        btnBackHub = findViewById(R.id.btnBackHub)

        rvTickets.layoutManager = LinearLayoutManager(this)

        ticketAdapter = MaintenanceTicketAdapter(emptyList()) { selectedTicket ->
            val intent = Intent(this, MaintenanceDetailActivity::class.java).apply {
                putExtra("TICKET_ID", selectedTicket.ticketID)
            }
            startActivity(intent)
        }
        rvTickets.adapter = ticketAdapter

        btnBackHub.setOnClickListener { finish() }

        setupTabs()
        setupFilterButton()

        // Load default Active Requests
        fetchMaintenanceTickets(loadHistory = false, startDate = null, endDate = null)
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                resetFilterState()

                when (tab?.position) {
                    0 -> {
                        insideHistoryTab = false
                        llFilterContainer.visibility = View.GONE
                        fetchMaintenanceTickets(loadHistory = false, null, null)
                    }
                    1 -> {
                        insideHistoryTab = true
                        llFilterContainer.visibility = View.VISIBLE
                        fetchMaintenanceTickets(loadHistory = true, null, null)
                    }
                    2 -> {
                        tabLayout.post { tabLayout.getTabAt(0)?.select() }
                        startActivity(Intent(this@MaintenanceHubActivity, RequestMaintenanceActivity::class.java))
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (tab?.position == 2) {
                    tabLayout.post { tabLayout.getTabAt(0)?.select() }
                    startActivity(Intent(this@MaintenanceHubActivity, RequestMaintenanceActivity::class.java))
                }
            }
        })
    }

    private fun setupFilterButton() {
        btnFilterHistory.setOnClickListener {
            if (isFilterApplied) {
                // CLEAR FILTER
                resetFilterState()
                fetchMaintenanceTickets(loadHistory = true, startDate = null, endDate = null)
                Toast.makeText(this, "Filter cleared.", Toast.LENGTH_SHORT).show()
            } else {
                // APPLY FILTER
                val datePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Select Date Range")
                    .build()

                datePicker.addOnPositiveButtonClickListener { selection ->
                    val startDateMs = selection.first
                    val endDateMs = selection.second

                    if (startDateMs != null && endDateMs != null) {
                        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                        tvHistoryTitle.text = "FILTERED: ${sdf.format(Date(startDateMs))} - ${sdf.format(Date(endDateMs))}"

                        isFilterApplied = true
                        btnFilterHistory.text = "CLEAR"

                        // Adjust end date to capture the very end of the selected day (11:59 PM)
                        val endOfDay = endDateMs + (24 * 60 * 60 * 1000) - 1
                        fetchMaintenanceTickets(loadHistory = true, startDateMs, endOfDay)
                    }
                }
                datePicker.show(supportFragmentManager, "DATE_PICKER")
            }
        }
    }

    private fun resetFilterState() {
        isFilterApplied = false
        btnFilterHistory.text = "FILTER"
        tvHistoryTitle.text = "PAST REQUESTS"
    }

    private fun fetchMaintenanceTickets(loadHistory: Boolean, startDate: Long?, endDate: Long?) {
        val currentUID = auth.currentUser?.uid ?: return

        // 1. Remove any existing listener before starting a new one
        activeTicketListener?.remove()

        // 2. Save the new listener to the variable
        activeTicketListener = db.collection("MAINTENANCE_TICKETS")
            .whereEqualTo("tenantID", currentUID)
            .addSnapshotListener { snapshots, error ->

                if (error != null) {
                    // 3. SILENT FAIL: If the user just signed out, ignore the error!
                    if (auth.currentUser == null) return@addSnapshotListener

                    Toast.makeText(this, "Failed to load requests: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val fullList = snapshots.toObjects(MaintenanceTicket::class.java)

                    baseFilteredList = if (loadHistory) {
                        fullList.filter {
                            (it.status.equals("Resolved", ignoreCase = true) || it.status.equals("Rejected", ignoreCase = true)) &&
                                    (startDate == null || it.lastUpdatedDate >= startDate) &&
                                    (endDate == null || it.lastUpdatedDate <= endDate)
                        }.sortedByDescending { it.lastUpdatedDate } // Most recent first
                    } else {
                        fullList.filter {
                            !it.status.equals("Resolved", ignoreCase = true) && !it.status.equals("Rejected", ignoreCase = true)
                        }.sortedByDescending { it.lastUpdatedDate } // Most recent first
                    }

                    // 4. Update the UI
                    renderList()
                }
            }
    }

    private fun renderList() {
        ticketAdapter.updateData(baseFilteredList)

        if (baseFilteredList.isEmpty()) {
            if (insideHistoryTab) {
                llEmptyHistoryState.visibility = View.VISIBLE
            }
            rvTickets.visibility = View.GONE
        } else {
            llEmptyHistoryState.visibility = View.GONE
            rvTickets.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (::tabLayout.isInitialized) {
            val selectedIndex = tabLayout.selectedTabPosition
            // Only fetch if not currently filtered, otherwise we lose the filter on resume
            if (!isFilterApplied) {
                fetchMaintenanceTickets(loadHistory = selectedIndex == 1, null, null)
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // 4. Kill the listener when the screen closes so it stops listening to Firestore!
        activeTicketListener?.remove()
    }
}