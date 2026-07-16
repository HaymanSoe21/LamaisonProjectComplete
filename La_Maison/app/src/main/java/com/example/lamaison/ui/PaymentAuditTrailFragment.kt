package com.example.lamaison.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.PaymentSlip
import com.example.lamaison.data.PaymentSlipUIState
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentAuditTrailFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvAuditSlips: RecyclerView
    private lateinit var btnSearch: TextView // Updated from Button to TextView to match Maintenance Hub pill style
    private lateinit var tvFeedTitle: TextView

    private var isFilterApplied = false
    private var activeStartDateMs: Long? = null
    private var activeEndDateMs: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_payment_audit_trail, container, false)

        rvAuditSlips = view.findViewById(R.id.rvAuditSlips)
        rvAuditSlips.layoutManager = LinearLayoutManager(requireContext())

        btnSearch = view.findViewById(R.id.btnSearch)
        tvFeedTitle = view.findViewById(R.id.tvFeedTitle)

        // Set default state
        btnSearch.text = "FILTER"
        tvFeedTitle.text = "PROCESSED TRANSACTIONS"

        // Load default audit slips (all records, newest first)
        fetchAuditSlips(null, null)

        // --- DYNAMIC FILTER LOGIC (1-BUTTON DESIGN) ---
        btnSearch.setOnClickListener {
            if (isFilterApplied) {
                // STATE 2: Filter is active -> Clicking clears the filter and reverts UI
                isFilterApplied = false
                btnSearch.text = "FILTER"
                tvFeedTitle.text = "PROCESSED TRANSACTIONS"
                activeStartDateMs = null
                activeEndDateMs = null

                fetchAuditSlips(null, null)
                Toast.makeText(requireContext(), "Filter cleared. Showing all records.", Toast.LENGTH_SHORT).show()

            } else {
                // STATE 1: No filter -> Clicking launches the calendar range picker
                val datePicker = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Select Audit Date Range")
                    .build()

                datePicker.addOnPositiveButtonClickListener { selection ->
                    val startDateMs = selection.first
                    val endDateMs = selection.second

                    if (startDateMs != null && endDateMs != null) {
                        val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                        tvFeedTitle.text = "FILTERED: ${sdf.format(Date(startDateMs))} - ${sdf.format(Date(endDateMs))}"

                        // Adjust end date by adding 23:59:59 to capture the entire final day
                        val endOfDayMs = endDateMs + (24 * 60 * 60 * 1000) - 1

                        // Transform button into "CLEAR" state
                        isFilterApplied = true
                        btnSearch.text = "CLEAR"

                        activeStartDateMs = startDateMs
                        activeEndDateMs = endOfDayMs

                        fetchAuditSlips(startDateMs, endOfDayMs)
                    }
                }
                datePicker.show(parentFragmentManager, "DATE_PICKER")
            }
        }

        view.findViewById<ImageView>(R.id.tvBackToDashboard).setOnClickListener {
            requireActivity().finish()
        }

        return view
    }

    /**
     * Dynamically queries supervisory audit slips. If dates are provided, it filters by range.
     * All original manager unit verification and data enrichment logic is kept completely intact.
     */
    private fun fetchAuditSlips(startDate: Long?, endDate: Long?) {
        val managerId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("UNITS")
            .whereEqualTo("assignedManagerID", managerId)
            .get()
            .addOnSuccessListener { unitDocuments ->
                // Collect Document IDs, unitIDs, and unitCodes to ensure zero supervised suites are missed
                val managedUnitIds = unitDocuments.documents.map { it.id } +
                        unitDocuments.mapNotNull { it.getString("unitID") } +
                        unitDocuments.mapNotNull { it.getString("unitCode") }

                if (managedUnitIds.isEmpty()) {
                    setupAdapter(emptyList())
                    return@addOnSuccessListener
                }

                db.collection("PAYMENT_SLIPS")
                    .whereIn("status", listOf("Verified", "Rejected"))
                    .get()
                    .addOnSuccessListener { slipDocuments ->
                        val slipList = mutableListOf<PaymentSlip>()

                        for (document in slipDocuments) {
                            val slip = document.toObject(PaymentSlip::class.java)

                            // Safe check against transferDate or submissionDate fallback
                            val timestamp = if (slip.transferDate > 0) slip.transferDate else slip.submissionDate

                            // Enforce supervisory manager security check
                            val belongsToManager = managedUnitIds.contains(slip.unitID)

                            // Apply date range check ONLY if a range is actively selected
                            val withinDateRange = if (startDate != null && endDate != null) {
                                timestamp in startDate..endDate
                            } else {
                                true
                            }

                            if (belongsToManager && withinDateRange) {
                                slipList.add(slip)
                            }
                        }

                        if (slipList.isEmpty()) {
                            setupAdapter(emptyList())
                            return@addOnSuccessListener
                        }

                        val resolvedList = mutableListOf<PaymentSlipUIState>()
                        var pendingFetches = slipList.size

                        for (slip in slipList) {
                            if (slip.unitID.isEmpty() || slip.tenantID.isEmpty()) {
                                resolvedList.add(PaymentSlipUIState(slip, "Unknown Unit", "Unknown Tenant"))
                                checkAndApplyAdapter(resolvedList, --pendingFetches)
                                continue
                            }

                            db.collection("UNITS").document(slip.unitID).get().addOnSuccessListener { unitDoc ->
                                val unitCode = unitDoc.getString("unitCode") ?: slip.unitID
                                db.collection("USERS").document(slip.tenantID).get().addOnSuccessListener { userDoc ->
                                    val fullName = userDoc.getString("fullName") ?: "Unknown Tenant"
                                    resolvedList.add(PaymentSlipUIState(slip, unitCode, fullName))
                                    checkAndApplyAdapter(resolvedList, --pendingFetches)
                                }.addOnFailureListener {
                                    resolvedList.add(PaymentSlipUIState(slip, unitCode, "Unknown Tenant"))
                                    checkAndApplyAdapter(resolvedList, --pendingFetches)
                                }
                            }.addOnFailureListener {
                                resolvedList.add(PaymentSlipUIState(slip, slip.unitID, "Unknown Tenant"))
                                checkAndApplyAdapter(resolvedList, --pendingFetches)
                            }
                        }
                    }
                    .addOnFailureListener {
                        context?.let { safeContext -> Toast.makeText(safeContext, "Failed to load audit trail.", Toast.LENGTH_SHORT).show() }
                    }
            }
    }

    private fun checkAndApplyAdapter(resolvedList: MutableList<PaymentSlipUIState>, pendingFetches: Int) {
        if (pendingFetches == 0) {
            resolvedList.sortByDescending { it.slip.transferDate }
            setupAdapter(resolvedList)
        }
    }

    private fun setupAdapter(data: List<PaymentSlipUIState>) {
        if (!isAdded || context == null) return

        val adapter = AuditSlipAdapter(data) { clickedState ->
            val intent = Intent(requireContext(), SlipDetailsActivity::class.java)
            intent.putExtra("SLIP_ID", clickedState.slip.slipID)
            intent.putExtra("IS_AUDIT_MODE", true)
            startActivity(intent)
        }
        rvAuditSlips.adapter = adapter
    }
}