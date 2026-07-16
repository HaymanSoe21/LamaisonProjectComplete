package com.example.lamaison.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.PaymentSlip
import com.example.lamaison.data.PaymentSlipUIState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
class PaymentInboxFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvPaymentSlips: RecyclerView
    private lateinit var btnBack: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_payment_inbox, container, false)

        rvPaymentSlips = view.findViewById(R.id.rvPaymentSlips)
        rvPaymentSlips.layoutManager = LinearLayoutManager(requireContext())
        btnBack = view.findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            requireActivity().finish()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        fetchPendingSlips()
    }

    private fun fetchPendingSlips() {
        val managerId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Fetch Units assigned to this Manager
        db.collection("UNITS")
            .whereEqualTo("assignedManagerID", managerId)
            .get()
            .addOnSuccessListener { unitDocuments ->
                val managedUnitIds = unitDocuments.mapNotNull { it.getString("unitID") }

                if (managedUnitIds.isEmpty()) {
                    setupAdapter(emptyList()) // Uses your team member's empty state logic
                    return@addOnSuccessListener
                }

                // 2. Fetch Pending Slips
                db.collection("PAYMENT_SLIPS")
                    .whereEqualTo("status", "Pending")
                    .get()
                    .addOnSuccessListener { slipDocuments ->
                        val slipList = mutableListOf<PaymentSlip>()

                        for (document in slipDocuments) {
                            val slip = document.toObject(PaymentSlip::class.java)
                            // 🔥 SECURITY: Only process if it belongs to the manager!
                            if (managedUnitIds.contains(slip.unitID)) {
                                slipList.add(slip)
                            }
                        }

                        if (slipList.isEmpty()) {
                            setupAdapter(emptyList())
                            return@addOnSuccessListener
                        }

                        // 3. Your team member's UI State Pre-fetch logic
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
                        context?.let { safeContext -> Toast.makeText(safeContext, "Failed to load slips.", Toast.LENGTH_SHORT).show() }
                    }
            }
    }

    private fun checkAndApplyAdapter(resolvedList: MutableList<PaymentSlipUIState>, pendingFetches: Int) {
        if (pendingFetches == 0) {
            resolvedList.sortBy { it.slip.submissionDate }
            setupAdapter(resolvedList)
        }
    }

    private fun setupAdapter(data: List<PaymentSlipUIState>) {
        if (!isAdded || context == null) return // Safety check

        val adapter = PaymentSlipAdapter(data) { clickedState ->
            val intent = Intent(requireContext(), SlipDetailsActivity::class.java)
            intent.putExtra("SLIP_ID", clickedState.slip.slipID)
            startActivity(intent)
        }
        rvPaymentSlips.adapter = adapter
    }
}