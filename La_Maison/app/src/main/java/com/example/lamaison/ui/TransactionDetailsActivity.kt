package com.example.lamaison.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.lamaison.R
import com.example.lamaison.data.LedgerTransaction

class TransactionDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_details)

        val btnBackArrow = findViewById<View>(R.id.btnBackArrow)
        val btnBackToLedger = findViewById<Button>(R.id.btnBackToLedger)

        val tvAmount = findViewById<TextView>(R.id.tvDetailAmount)
        val tvStatus = findViewById<TextView>(R.id.tvDetailStatus)
        val tvCategory = findViewById<TextView>(R.id.tvDetailCategory) // ADJUSTED: Bound new category field!
        val tvTxnId = findViewById<TextView>(R.id.tvDetailTxnId)
        val tvDate = findViewById<TextView>(R.id.tvDetailDate)
        val tvMethod = findViewById<TextView>(R.id.tvDetailMethod)
        val tvBankName = findViewById<TextView>(R.id.tvDetailBankName)
        val tvBankAddress = findViewById<TextView>(R.id.tvDetailBankAddress)
        val tvNotes = findViewById<TextView>(R.id.tvDetailNotes)

        btnBackArrow.setOnClickListener { finish() }
        btnBackToLedger.setOnClickListener { finish() }

        // Retrieve transaction passed from Ledger screen[cite: 12]
        val txn = intent.getSerializableExtra("EXTRA_TXN") as? LedgerTransaction

        if (txn != null) {
            tvAmount.text = String.format("-$%,d.00", txn.amount)
            tvStatus.text = txn.status
            tvCategory.text = txn.category // ADJUSTED: Populated category text!
            tvTxnId.text = txn.transactionId
            tvDate.text = txn.dateString
            tvMethod.text = txn.paymentMethod
            tvBankName.text = txn.bankName
            tvBankAddress.text = txn.bankAddress
            tvNotes.text = txn.notes

            if (txn.status.equals("Paid", ignoreCase = true) || txn.status.equals("Settled", ignoreCase = true)) {
                tvStatus.setTextColor(Color.parseColor("#047857"))
                tvStatus.setBackgroundColor(Color.parseColor("#D1FAE5"))
            } else if (txn.status.equals("Overdue", ignoreCase = true)) {
                tvStatus.setTextColor(Color.parseColor("#DC2626"))
                tvStatus.setBackgroundColor(Color.parseColor("#FEE2E2"))
            } else {
                tvStatus.setTextColor(Color.parseColor("#D97706"))
                tvStatus.setBackgroundColor(Color.parseColor("#FEF3C7"))
            }
        }
    }
}