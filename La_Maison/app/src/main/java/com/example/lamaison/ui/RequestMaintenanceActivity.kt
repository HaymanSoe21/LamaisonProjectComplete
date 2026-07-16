package com.example.lamaison.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lamaison.BuildConfig
import com.example.lamaison.R
import com.example.lamaison.data.MaintenanceTicket
import com.example.lamaison.utils.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class RequestMaintenanceActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var etIssueTitle: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var etDescription: EditText
    private lateinit var tvUploadStatus: TextView
    private lateinit var btnSubmitTicket: Button

    private var selectedCategory: String = "General"
    private var photoUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            photoUri = it
            tvUploadStatus.text = "Photo Selected Successfully"
            tvUploadStatus.setTextColor(Color.parseColor("#16A34A"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_maintenance)

        etIssueTitle = findViewById(R.id.etIssueTitle)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        etDescription = findViewById(R.id.etDescription)
        tvUploadStatus = findViewById(R.id.tvUploadStatus)
        btnSubmitTicket = findViewById(R.id.btnSubmitTicket)

        findViewById<ImageView>(R.id.btnBackHeader).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvCancel).setOnClickListener { finish() }

        val btnUploadPhoto = findViewById<LinearLayout>(R.id.btnUploadPhoto)
        btnUploadPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }

        setupCategorySpinner()

        btnSubmitTicket.setOnClickListener {
            submitMaintenanceTicket()
        }
    }

    private fun setupCategorySpinner() {
        val categories = arrayOf("General", "Plumbing", "Electrical", "Appliance", "HVAC", "Structural","Safety and Security","Others")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCategory = categories[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun submitMaintenanceTicket() {
        val title = etIssueTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val currentUID = auth.currentUser?.uid ?: return

        if (title.isEmpty()) {
            etIssueTitle.error = "Title cannot be blank"
            return
        }
        if (description.isEmpty()) {
            etDescription.error = "Description cannot be blank"
            return
        }

        btnSubmitTicket.isEnabled = false
        btnSubmitTicket.text = "Verifying Active Lease..."

        // STRICT LIVE FIRESTORE LEASE CHECK
        db.collection("LEASES")
            .whereEqualTo("tenantID", currentUID)
            .get()
            .addOnSuccessListener { snaps ->
                // Check if any lease is genuinely Active and not expired
                val activeLease = snaps.documents.firstOrNull { doc ->
                    val status = doc.getString("status") ?: ""
                    val endDate = doc.getLong("endDate") ?: 0L
                    status.equals("Active", ignoreCase = true) && (endDate == 0L || endDate > System.currentTimeMillis())
                }

                if (activeLease == null) {
                    btnSubmitTicket.isEnabled = true
                    btnSubmitTicket.text = "Submit Ticket"
                    Toast.makeText(this, "❌ Access Denied: You must have an active apartment lease to submit maintenance requests.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val verifiedUnitID = activeLease.getString("unitID") ?: ""
                if (verifiedUnitID.isEmpty()) {
                    btnSubmitTicket.isEnabled = true
                    btnSubmitTicket.text = "Submit Ticket"
                    Toast.makeText(this, "❌ Error: Your active lease is missing a linked Unit ID.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                // Proceed to upload photo and save ticket
                proceedWithTicketCreation(currentUID, verifiedUnitID, title, description)
            }
            .addOnFailureListener { e ->
                btnSubmitTicket.isEnabled = true
                btnSubmitTicket.text = "Submit Ticket"
                Toast.makeText(this, "❌ Network error verifying lease: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun proceedWithTicketCreation(currentUID: String, unitID: String, title: String, description: String) {
        btnSubmitTicket.text = "Submitting Ticket..."

        lifecycleScope.launch {
            var finalPhotoUrl = ""

            photoUri?.let { uri ->
                try {
                    val base64Str = getBase64FromUri(uri)
                    if (base64Str.isNotEmpty()) {
                        finalPhotoUrl = uploadToImgBB(base64Str) ?: ""
                    }
                } catch (e: Exception) {
                    Log.e("MAINTENANCE_REQ", "ImgBB upload error: ${e.message}")
                }
            }

            val generatedTicketId = "TKT-" + UUID.randomUUID().toString().take(8).uppercase()
            val newTicket = MaintenanceTicket(
                ticketID = generatedTicketId,
                tenantID = currentUID,
                unitID = unitID,
                issueTitle = title,
                issueCategory = selectedCategory,
                description = description,
                maintenancePhotoURL = finalPhotoUrl,
                status = "Pending",
                submissionDate = System.currentTimeMillis(),
                lastUpdatedDate = System.currentTimeMillis()
            )

            db.collection("MAINTENANCE_TICKETS").document(generatedTicketId)
                .set(newTicket)
                .addOnSuccessListener {
                    // Replaced the activity launch with a direct floating dialog
                    showSuccessDialog(title, selectedCategory, "Pending")
                }
                .addOnFailureListener { e ->
                    btnSubmitTicket.isEnabled = true
                    btnSubmitTicket.text = "Submit Ticket"
                    Toast.makeText(this@RequestMaintenanceActivity, "Firestore Save Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    /**
     * Renders the smooth, rounded floating success card directly over the current screen.
     */
    private fun showSuccessDialog(title: String, category: String, status: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_ticket_success, null)

        // Bind dynamic data to your card's text views
        dialogView.findViewById<TextView>(R.id.tvTicketTitle).text = title
        dialogView.findViewById<TextView>(R.id.tvTicketCategory).text = "CATEGORY: ${category.uppercase()}"
        dialogView.findViewById<TextView>(R.id.tvTicketStatus).text = "• ${status.uppercase()}"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Removes Android's sharp default window box so your rounded card floats smoothly
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnBackHome).setOnClickListener {
            dialog.dismiss()
            // Return cleanly to Dashboard
            val intent = Intent(this, TenantDashboard::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        dialog.show()
    }

    private fun getBase64FromUri(uri: Uri): String {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return ""
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            ""
        }
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
                null
            } catch (e: Exception) {
                Log.e("IMGBB_MAINTENANCE", "Error: ${e.message}")
                null
            }
        }
    }
}