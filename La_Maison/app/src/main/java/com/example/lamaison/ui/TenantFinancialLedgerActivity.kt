package com.example.lamaison.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.FinancialLedger
import com.example.lamaison.data.LedgerTransaction
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class TenantFinancialLedgerActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var tvOutstandingAmount: TextView
    private lateinit var tvNextDueDate: TextView
    private lateinit var etFromDate: EditText
    private lateinit var etToDate: EditText
    private lateinit var rvTransactions: RecyclerView
    private lateinit var adapter: LedgerTransactionAdapter

    private var allTransactions = mutableListOf<LedgerTransaction>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tenant_financial_ledger)

        val btnBackArrow = findViewById<View>(R.id.btnBackArrow)
        tvOutstandingAmount = findViewById(R.id.tvOutstandingAmount)
        tvNextDueDate = findViewById(R.id.tvNextDueDate)
        etFromDate = findViewById(R.id.etFromDate)
        etToDate = findViewById(R.id.etToDate)
        val btnSearch = findViewById<Button>(R.id.btnSearchTransactions)
        val btnClear = findViewById<Button>(R.id.btnClearFilter)
        rvTransactions = findViewById(R.id.rvTransactions)

        btnBackArrow.setOnClickListener { finish() }

        // Setup RecyclerView
        rvTransactions.layoutManager = LinearLayoutManager(this)
        adapter = LedgerTransactionAdapter(emptyList()) { selectedTxn ->
            val intent = Intent(this, TransactionDetailsActivity::class.java)
            intent.putExtra("EXTRA_TXN", selectedTxn)
            startActivity(intent)
        }
        rvTransactions.adapter = adapter

        // Setup Date Pickers
        setupDatePicker(etFromDate)
        setupDatePicker(etToDate)

        // Load Live Data from Firestore
        loadOutstandingBalance()
        loadTransactionHistory()

        // Filter and Clear Action Listeners
        btnSearch.setOnClickListener { filterTransactions() }
        btnClear.setOnClickListener { clearFilter() }
    }

    private fun setupDatePicker(editText: EditText) {
        editText.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setTheme(R.style.CustomMaterialCalendarTheme)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val formattedDate = dateFormat.format(Date(selection))
                editText.setText(formattedDate)
            }

            datePicker.show(supportFragmentManager, "MATERIAL_DATE_PICKER")
        }
    }

    private fun loadOutstandingBalance() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("FINANCIAL_LEDGERS").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val ledger = doc.toObject(FinancialLedger::class.java)
                    if (ledger != null) {
                        tvOutstandingAmount.text = String.format("$%,d.00", ledger.currentBalance)
                        tvNextDueDate.text = "Next Due: ${dateFormat.format(Date(ledger.nextDueDate))}"

                        if (ledger.status.equals("Overdue", ignoreCase = true)) {
                            tvNextDueDate.setTextColor(0xFFDC2626.toInt())
                            tvNextDueDate.text = "OVERDUE: ${dateFormat.format(Date(ledger.nextDueDate))}"
                        }
                    } else {
                        clearBalanceDisplay()
                    }
                } else {
                    clearBalanceDisplay()
                }
            }
            .addOnFailureListener { clearBalanceDisplay() }
    }

    private fun clearBalanceDisplay() {
        tvOutstandingAmount.text = "$0.00"
        tvNextDueDate.text = "No active balance due"
    }

    private fun loadTransactionHistory() {
        val userId = auth.currentUser?.uid ?: return

        // Query ONLY by tenantID to bypass Firebase Composite Index crashes
        db.collection("PAYMENT_SLIPS")
            .whereEqualTo("tenantID", userId)
            .get()
            .addOnSuccessListener { snapshots ->
                allTransactions.clear()

                if (!snapshots.isEmpty) {
                    for (doc in snapshots.documents) {
                        // Strictly grab submissionDate with a safe fallback to current time
                        val timestamp = doc.getLong("submissionDate") ?: System.currentTimeMillis()

                        val amount = doc.getLong("amountPaid")?.toInt() ?: doc.getLong("amount")?.toInt() ?: 0
                        val status = doc.getString("status") ?: "Pending"
                        val notes = doc.getString("notes") ?: ""

                        val category = doc.getString("category") ?: "Monthly Rent Payment"
                        val bankName = doc.getString("bankName") ?: "Bank Transfer"
                        val bankAddress = doc.getString("bankAddress") ?: ""

                        allTransactions.add(
                            LedgerTransaction(
                                transactionId = doc.id.take(12).uppercase(),
                                timestamp = timestamp,
                                dateString = dateFormat.format(Date(timestamp)),
                                category = category,
                                amount = amount,
                                status = status,
                                notes = notes,
                                bankName = bankName,
                                bankAddress = bankAddress
                            )
                        )
                    }
                }

                // Sort descending LOCALLY in Kotlin (most recent submission first)
                allTransactions.sortByDescending { it.timestamp }

                // Populate adapter immediately on load
                adapter.updateData(allTransactions)

                if (allTransactions.isEmpty()) {
                    Toast.makeText(this, "No payment records found for this account.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                allTransactions.clear()
                adapter.updateData(emptyList())
                Toast.makeText(this, "Failed to load history: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun filterTransactions() {
        val fromStr = etFromDate.text.toString().trim()
        val toStr = etToDate.text.toString().trim()

        if (fromStr.isEmpty() || toStr.isEmpty()) {
            Toast.makeText(this, "Please select both a Start Date and End Date.", Toast.LENGTH_SHORT).show()
            return
        }

        val fromTime = try { dateFormat.parse(fromStr)?.time ?: 0L } catch (e: Exception) { 0L }

        // Add 86,399,999 ms (23:59:59) to include the entire end date selected
        val toTime = try {
            val parsed = dateFormat.parse(toStr)?.time
            if (parsed != null) parsed + 86399999L else Long.MAX_VALUE
        } catch (e: Exception) { Long.MAX_VALUE }

        val filtered = allTransactions.filter { txn ->
            txn.timestamp in fromTime..toTime
        }

        adapter.updateData(filtered)

        if (filtered.isEmpty()) {
            Toast.makeText(this, "No payment slips found in this date range.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearFilter() {
        etFromDate.text.clear()
        etToDate.text.clear()

        // Revert adapter back to the originally sorted master list
        adapter.updateData(allTransactions)
        Toast.makeText(this, "Filter cleared. Showing all recent records.", Toast.LENGTH_SHORT).show()
    }
}