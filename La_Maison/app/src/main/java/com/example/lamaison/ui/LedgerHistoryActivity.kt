package com.example.lamaison.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.PaymentSlip
import com.google.firebase.firestore.FirebaseFirestore

class LedgerHistoryActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvLedgerHistory: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ledger_history)

        val unitId = intent.getStringExtra("UNIT_ID") ?: return
        val tenantId = intent.getStringExtra("TENANT_ID") ?: return

        // Bind directly to the UI elements in activity_ledger_history.xml
        findViewById<TextView>(R.id.tvLedgerUnit).text = intent.getStringExtra("UNIT_NAME")
        findViewById<TextView>(R.id.tvLedgerTenant).text = intent.getStringExtra("TENANT_NAME")
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        rvLedgerHistory = findViewById(R.id.rvLedgerHistory)
        rvLedgerHistory.layoutManager = LinearLayoutManager(this)

        fetchHistory(unitId, tenantId)
    }

    private fun fetchHistory(unitId: String, tenantId: String) {
        // Only query by Unit ID to bypass Firebase Index crashes
        db.collection("PAYMENT_SLIPS")
            .whereEqualTo("unitID", unitId)
            .get()
            .addOnSuccessListener { documents ->

                // Filter by tenant, check status, and sort newest-first LOCALLY in Kotlin
                val slips = documents.toObjects(PaymentSlip::class.java)
                    .filter { slip ->
                        slip.tenantID == tenantId &&
                                (slip.status.equals("Verified", ignoreCase = true) || slip.status.equals("Rejected", ignoreCase = true))
                    }
                    .sortedByDescending { it.transferDate }

                rvLedgerHistory.adapter = LedgerHistoryAdapter(slips)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show()
            }
    }
}