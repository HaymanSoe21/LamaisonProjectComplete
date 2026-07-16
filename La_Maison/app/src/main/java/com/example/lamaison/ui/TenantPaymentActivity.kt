package com.example.lamaison.ui

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lamaison.BuildConfig
import com.example.lamaison.R
import com.example.lamaison.data.PaymentSlip
import com.example.lamaison.utils.SessionManager
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class TenantPaymentActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var photoUri: Uri? = null
    private var selectedTransferTimestamp: Long = 0L
    private lateinit var tvUploadStatus: TextView

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            photoUri = it
            tvUploadStatus.text = "✅ Bank Receipt Attached Correctly"
            tvUploadStatus.setTextColor(0xFF047857.toInt())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tenant_payment)

        val btnBackArrow = findViewById<View>(R.id.btnBackArrow)
        val spinnerPaymentCategory = findViewById<Spinner>(R.id.spinnerPaymentCategory)
        val etAmountPaid = findViewById<EditText>(R.id.etAmountPaid)
        val spinnerBankName = findViewById<Spinner>(R.id.spinnerBankName)
        val etBankAccountNumber = findViewById<EditText>(R.id.etBankAccountNumber)
        val etBankAddress = findViewById<EditText>(R.id.etBankAddress)
        val etTransferDate = findViewById<EditText>(R.id.etTransferDate)
        val etNotes = findViewById<EditText>(R.id.etNotes)
        val btnUploadSlip = findViewById<LinearLayout>(R.id.btnUploadSlip)
        val btnSubmitPayment = findViewById<Button>(R.id.btnSubmitPayment)
        val btnCancel = findViewById<TextView>(R.id.btnCancel)

        tvUploadStatus = findViewById(R.id.tvUploadStatus)

        btnBackArrow.setOnClickListener { finish() }
        btnCancel.setOnClickListener { finish() }

        val categories = arrayOf("Monthly Rent Payment", "Monthly Rent Fee + Penalty", "Deposit", "Other")
        spinnerPaymentCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        val banks = arrayOf("KBZ Bank", "AYA Bank", "CB Bank", "Yoma Bank", "KBZPay", "WavePay", "Other")
        spinnerBankName.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, banks)

        etTransferDate.setOnClickListener {
            // Add 24 hours (86,400,000 ms) to .now() so UTC timezone differences don't block the current local day!
            val endOfTodayBuffer = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)

            val constraintsBuilder = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.before(endOfTodayBuffer))

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Transfer Date")
                .setTheme(R.style.CustomMaterialCalendarTheme)
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .setCalendarConstraints(constraintsBuilder.build())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedTransferTimestamp = selection
                val formatted = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(selection))
                etTransferDate.setText(formatted)
            }
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }

        btnUploadSlip.setOnClickListener { pickImageLauncher.launch("image/*") }

        // --- STRICT VALIDATION & FINTECH GUARDRAIL LOGIC ---
        btnSubmitPayment.setOnClickListener {

            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            val sessionManager = SessionManager(this)
            val cachedUnitId = sessionManager.getCachedTrueUnitId()

            val category = spinnerPaymentCategory.selectedItem.toString()
            val amountStr = etAmountPaid.text.toString().trim()
            val amount = amountStr.toIntOrNull()
            val bankName = spinnerBankName.selectedItem.toString()
            val accNumber = etBankAccountNumber.text.toString().trim()
            val branchAddr = etBankAddress.text.toString().trim()
            val dateStr = etTransferDate.text.toString().trim()
            val notes = etNotes.text.toString().trim()

            // 1. INPUT CHECKS
            if (amountStr.isEmpty() || accNumber.isEmpty() || branchAddr.isEmpty() || dateStr.isEmpty()) {
                Toast.makeText(this, "⚠️ Please complete ALL required fields before submitting.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amount == null || amount <= 0) {
                Toast.makeText(this, "❌ ERROR: Please enter a valid remittance amount greater than 0.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (selectedTransferTimestamp == 0L) {
                Toast.makeText(this, "⚠️ Please select the date of your bank transfer.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. DEFENSIVE BACKEND VALIDATION: Block future dates explicitly
            if (selectedTransferTimestamp > System.currentTimeMillis()) {
                Toast.makeText(this, "❌ ERROR: The transfer date cannot be in the future.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (photoUri == null) {
                Toast.makeText(this, "⚠️ Please attach a screenshot of your transfer receipt.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSubmitPayment.isEnabled = false
            btnSubmitPayment.text = "⏳ Verifying Account Status..."

            // 3. REAL-TIME FINTECH GUARDRAIL: Check Ledger Balance first
            db.collection("FINANCIAL_LEDGERS")
                .whereEqualTo("tenantID", userId)
                .get()
                .addOnSuccessListener { ledgerSnaps ->
                    val ledgerDoc = ledgerSnaps.documents.firstOrNull()
                    val currentBalance = ledgerDoc?.getLong("currentBalance") ?: 0L

                    // 4. Next, check their latest lease agreement
                    db.collection("LEASES")
                        .whereEqualTo("tenantID", userId)
                        .get()
                        .addOnSuccessListener { leaseSnaps ->
                            val latestLease = leaseSnaps.documents.maxByOrNull { it.getLong("createdAt") ?: 0L }
                            val leaseStatus = latestLease?.getString("status") ?: "None"
                            val lastKnownUnitID = latestLease?.getString("unitID") ?: ""

                            val isActiveOrRenewing = leaseStatus.equals("Active", ignoreCase = true) ||
                                    leaseStatus.equals("Pending_Renewal", ignoreCase = true) ||
                                    leaseStatus.equals("Pending Renewal", ignoreCase = true)
                            val hasOutstandingDebt = currentBalance > 0L

                            // THE RULE: Block ONLY if they have NO active contract AND owe NO money!
                            if (!isActiveOrRenewing && !hasOutstandingDebt) {
                                Toast.makeText(this@TenantPaymentActivity, "❌ Access Denied: You have no active contract and your ledger is fully settled.", Toast.LENGTH_LONG).show()
                                btnSubmitPayment.isEnabled = true
                                btnSubmitPayment.text = "Submit Payment Slip"
                                return@addOnSuccessListener
                            }

                            // Smart Routing: Use cached unit ID if active, or fallback to historical unit ID if expired
                            val targetUnitId = if (!cachedUnitId.isNullOrEmpty()) cachedUnitId else lastKnownUnitID
                            if (targetUnitId.isEmpty()) {
                                Toast.makeText(this@TenantPaymentActivity, "❌ Error: Could not locate your assigned unit profile.", Toast.LENGTH_LONG).show()
                                btnSubmitPayment.isEnabled = true
                                btnSubmitPayment.text = "Submit Payment Slip"
                                return@addOnSuccessListener
                            }

                            // 5. FIRE UPLOAD PROTOCOL!
                            submitPayment(targetUnitId, category, amount, bankName, accNumber, branchAddr, notes, btnSubmitPayment)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this@TenantPaymentActivity, "❌ Verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            btnSubmitPayment.isEnabled = true
                            btnSubmitPayment.text = "Submit Payment Slip"
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this@TenantPaymentActivity, "❌ Ledger check failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    btnSubmitPayment.isEnabled = true
                    btnSubmitPayment.text = "Submit Payment Slip"
                }
        }
    }

    private fun submitPayment(trueUnitIdFk: String, category: String, amount: Int, bank: String, acc: String, addr: String, notes: String, btn: Button) {
        val userId = auth.currentUser?.uid ?: return

        btn.text = "⏳ Uploading Receipt to ImgBB..."

        lifecycleScope.launch {
            val base64 = getBase64FromUri(photoUri!!)
            val imageUrl = uploadToImgBB(base64)

            if (imageUrl == null) {
                Toast.makeText(this@TenantPaymentActivity, "❌ Receipt upload failed! Check internet connection.", Toast.LENGTH_LONG).show()
                btn.isEnabled = true
                btn.text = "Submit Payment Slip"
                return@launch
            }

            btn.text = "⚡ Recording Remittance in Database..."

            val slipId = "SLP_" + UUID.randomUUID().toString().take(8).uppercase()
            val timestamp = System.currentTimeMillis()

            val slip = PaymentSlip(
                slipID = slipId,
                tenantID = userId,
                unitID = trueUnitIdFk,
                category = category,
                amountPaid = amount,
                slipPhotoURL = imageUrl,
                bankName = bank,
                bankAccountNumber = acc,
                bankAddress = addr,
                transferDate = selectedTransferTimestamp,
                notes = notes,
                status = "Pending",
                submissionDate = timestamp
            )

            db.collection("PAYMENT_SLIPS").document(slipId).set(slip)
                .addOnSuccessListener { showSuccessDialog(amount) }
                .addOnFailureListener { e ->
                    btn.isEnabled = true
                    btn.text = "Submit Payment Slip"
                    Toast.makeText(this@TenantPaymentActivity, "❌ Database Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showSuccessDialog(amount: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_success, null)
        dialogView.findViewById<TextView>(R.id.tvDialogAmount).text = "Amount: %,d MMK".format(amount)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<Button>(R.id.btnBackHome).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.show()
    }

    private fun getBase64FromUri(uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return ""
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private suspend fun uploadToImgBB(base64Image: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.imgbb.com/1/upload?key=${BuildConfig.IMGBB_API_KEY}")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.outputStream.write("image=${URLEncoder.encode(base64Image, "UTF-8")}".toByteArray())

                if (conn.responseCode == 200) {
                    val res = conn.inputStream.bufferedReader().use { it.readText() }
                    return@withContext JSONObject(res).getJSONObject("data").getString("url")
                }
            } catch (e: Exception) { Log.e("IMGBB", "Error: ${e.message}") }
            null
        }
    }
}