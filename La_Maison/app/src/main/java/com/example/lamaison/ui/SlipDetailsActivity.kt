package com.example.lamaison.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.lamaison.R
import com.example.lamaison.data.PaymentSlip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SlipDetailsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentSlipId: String? = null
    private var currentAmount: Int = 0

    private var currentTenantId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_slip_details)

        currentSlipId = intent.getStringExtra("SLIP_ID")

        findViewById<ImageView>(R.id.btnBackToInbox).setOnClickListener { finish() }

        if (currentSlipId != null) {
            loadSlipDetails(currentSlipId!!)
        } else {
            Toast.makeText(this, "Error loading slip", Toast.LENGTH_SHORT).show()
            finish()
            return // FIXED CRASH: Prevent code from continuing if slipID is null
        }

        findViewById<Button>(R.id.btnVerifySettle).setOnClickListener {
            updateSlipStatus("Verified")
        }

        findViewById<Button>(R.id.btnRejectSlip).setOnClickListener {
            updateSlipStatus("Rejected")
        }
    }

    private fun loadSlipDetails(slipId: String) {
        db.collection("PAYMENT_SLIPS").document(slipId).get()
            .addOnSuccessListener { document ->
                val slip = document?.toObject(PaymentSlip::class.java)
                if (slip != null) {
                    currentAmount = slip.amountPaid
                    currentTenantId = slip.tenantID

                    db.collection("USERS").document(slip.tenantID).get().addOnSuccessListener { userDoc ->
                        findViewById<TextView>(R.id.tvDetailTenant).text = userDoc.getString("fullName") ?: "Unknown"
                    }

                    db.collection("UNITS").document(slip.unitID).get().addOnSuccessListener { unitDoc ->
                        findViewById<TextView>(R.id.tvDetailSuite).text = unitDoc.getString("unitCode") ?: slip.unitID
                    }

                    val ivReceiptImage = findViewById<ImageView>(R.id.ivReceiptImage)
                    if (slip.slipPhotoURL.isNotEmpty()) {
                        Glide.with(this).load(slip.slipPhotoURL).into(ivReceiptImage)
                    }

                    val format = NumberFormat.getCurrencyInstance(Locale.US)
                    findViewById<TextView>(R.id.tvDetailAmount).text = format.format(slip.amountPaid)

                    val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
                    findViewById<TextView>(R.id.tvDetailDate).text = sdf.format(Date(slip.transferDate))

                    findViewById<TextView>(R.id.tvDetailBank).text = slip.bankName.ifEmpty { "N/A" }
                    findViewById<TextView>(R.id.tvDetailRef).text = slip.slipID.takeLast(8)
                    findViewById<TextView>(R.id.tvDetailBankAddress).text = slip.bankAddress.ifEmpty { "N/A" }

                    val isAuditMode = intent.getBooleanExtra("IS_AUDIT_MODE", false)
                    if (isAuditMode) {
                        findViewById<LinearLayout>(R.id.llActionButtons).visibility = View.GONE
                        findViewById<LinearLayout>(R.id.llAuditInfoSection).visibility = View.VISIBLE

                        val tvStatusBadge = findViewById<TextView>(R.id.tvAuditDetailStatusBadge)
                        tvStatusBadge.text = slip.status.uppercase()

                        if (slip.status == "Verified") {
                            tvStatusBadge.setTextColor(Color.parseColor("#16A34A"))
                            tvStatusBadge.setBackgroundColor(Color.parseColor("#DCFCE7"))
                        } else {
                            tvStatusBadge.setTextColor(Color.parseColor("#DC2626"))
                            tvStatusBadge.setBackgroundColor(Color.parseColor("#FEE2E2"))
                        }

                        val auditSdf = SimpleDateFormat("MMMM dd, yyyy • HH:mm", Locale.US)
                        findViewById<TextView>(R.id.tvAuditDetailDate).text = if(slip.approvalDate > 0) auditSdf.format(Date(slip.approvalDate)) else "N/A"

                        if (slip.approvedByManagerID.isNotEmpty()) {
                            db.collection("USERS").document(slip.approvedByManagerID).get().addOnSuccessListener { managerDoc ->
                                val managerName = managerDoc.getString("fullName") ?: "Unknown Manager"
                                findViewById<TextView>(R.id.tvAuditDetailManager).text = managerName
                            }
                        } else {
                            findViewById<TextView>(R.id.tvAuditDetailManager).text = "System"
                        }
                    }
                }
            }
    }

    private fun updateSlipStatus(newStatus: String) {
        val managerId = auth.currentUser?.uid ?: "UnknownManager"

        db.collection("PAYMENT_SLIPS").document(currentSlipId!!)
            .update(
                mapOf(
                    "status" to newStatus,
                    "approvedByManagerID" to managerId,
                    "approvalDate" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                if (newStatus == "Verified" && currentTenantId != null) {
                    updateTenantLedger(currentTenantId!!, currentAmount) {
                        showActionDialog(newStatus)
                    }
                } else {
                    showActionDialog(newStatus)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showActionDialog(status: String) {
        // FIXED CRASH: Prevent dialog from showing if the activity is already closing
        if (isFinishing || isDestroyed) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_payment_result, null)
        val customDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        customDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvIcon = dialogView.findViewById<TextView>(R.id.tvDialogIcon)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivDialogIcon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvDialogStatus)
        val tvAmount = dialogView.findViewById<TextView>(R.id.tvDialogAmount)
        val btnAction = dialogView.findViewById<Button>(R.id.btnDialogAction)

        val format = NumberFormat.getCurrencyInstance(Locale.US)
        tvAmount.text = format.format(currentAmount)
        tvStatus.text = status

        if (status == "Verified") {
            tvIcon.visibility = android.view.View.VISIBLE
            ivIcon.visibility = android.view.View.GONE
            tvTitle.text = "Payment Verified"
            tvMessage.text = "The payment slip has been successfully verified. The tenant's ledger is updated."
            tvStatus.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            tvIcon.visibility = android.view.View.GONE
            ivIcon.visibility = android.view.View.VISIBLE
            ivIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            ivIcon.setColorFilter(Color.parseColor("#DC2626"))
            tvTitle.text = "Payment Rejected"
            tvMessage.text = "The payment slip was rejected. The tenant will be notified to submit a new slip."
            tvStatus.setTextColor(Color.parseColor("#DC2626"))
        }

        btnAction.setOnClickListener {
            customDialog.dismiss()
            finish()
        }

        customDialog.show()
    }

    private fun updateTenantLedger(tenantId: String, amountPaid: Int, onComplete: () -> Unit) {
        db.collection("FINANCIAL_LEDGERS")
            .whereEqualTo("tenantID", tenantId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val ledgerDoc = documents.documents[0]
                    val currentBalance = ledgerDoc.getLong("currentBalance")?.toInt() ?: 0
                    val newBalance = currentBalance - amountPaid
                    val newStatus = if (newBalance <= 0) "Settled" else "Pending"

                    ledgerDoc.reference.update(
                        mapOf(
                            "currentBalance" to newBalance,
                            "lastUpdated" to System.currentTimeMillis(),
                            "status" to newStatus
                        )
                    ).addOnSuccessListener {
                        onComplete()
                    }.addOnFailureListener {
                        // FIXED CRASH: Safe Toast Check
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, "Slip verified, but Ledger update failed!", Toast.LENGTH_LONG).show()
                        }
                        onComplete()
                    }
                } else {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Warning: No ledger found for this tenant.", Toast.LENGTH_SHORT).show()
                    }
                    onComplete()
                }
            }
            .addOnFailureListener {
                onComplete()
            }
    }
}