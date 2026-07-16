package com.example.lamaison.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lamaison.R
import com.example.lamaison.data.AuditLog
import com.example.lamaison.data.MaintenanceTicket
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ManagerTicketDetailsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var ticketId: String? = null
    private var isFromLog: Boolean = false

    private lateinit var btnUpdateProgress: Button
    private lateinit var btnMarkResolved: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_ticket_details)

        ticketId = intent.getStringExtra("TICKET_ID")
        isFromLog = intent.getBooleanExtra("IS_FROM_LOG", false)

        findViewById<ImageView>(R.id.btnBackDetails).setOnClickListener { finish() }

        btnUpdateProgress = findViewById(R.id.btnUpdateProgress)
        btnMarkResolved = findViewById(R.id.btnMarkResolved)

        btnUpdateProgress.setOnClickListener { updateTicketStatus("In-Progress", btnUpdateProgress) }
        btnMarkResolved.setOnClickListener { updateTicketStatus("Resolved", btnMarkResolved) }

        if (ticketId != null) fetchTicketDetails()
    }

    private fun fetchTicketDetails() {
        db.collection("MAINTENANCE_TICKETS").document(ticketId!!).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val ticket = document.toObject(MaintenanceTicket::class.java) ?: return@addOnSuccessListener

                    findViewById<TextView>(R.id.tvDetailTicketId).text = ticket.ticketID

                    // --- ASYNCHRONOUS TENANT NAME LOOKUP ---
                    val tvDetailTenant = findViewById<TextView>(R.id.tvDetailTenant)
                    if (ticket.tenantID.isNotEmpty()) {
                        tvDetailTenant.text = "Loading Name..."
                        db.collection("USERS").document(ticket.tenantID).get()
                            .addOnSuccessListener { userDoc ->
                                val fullName = userDoc.getString("fullName") ?: "Unknown Tenant"
                                tvDetailTenant.text = fullName
                            }
                            .addOnFailureListener {
                                // Fallback to raw ID if network or query fails
                                tvDetailTenant.text = ticket.tenantID
                            }
                    } else {
                        tvDetailTenant.text = "N/A"
                    }

                    // --- ASYNCHRONOUS UNIT CODE LOOKUP ---
                    val tvDetailUnit = findViewById<TextView>(R.id.tvDetailUnitId)
                    if (ticket.unitID.isNotEmpty()) {
                        tvDetailUnit.text = "Loading Unit..."
                        db.collection("UNITS").document(ticket.unitID).get()
                            .addOnSuccessListener { unitDoc ->
                                val unitCode = unitDoc.getString("unitCode") ?: ticket.unitID
                                tvDetailUnit.text = unitCode
                            }
                            .addOnFailureListener {
                                // Fallback to raw ID if network or query fails
                                tvDetailUnit.text = ticket.unitID
                            }
                    } else {
                        tvDetailUnit.text = "N/A"
                    }

                    findViewById<TextView>(R.id.tvDetailCategory).text = ticket.issueCategory
                    findViewById<TextView>(R.id.tvDetailDescription).text = ticket.description

                    val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
                    findViewById<TextView>(R.id.tvDetailDate).text = sdf.format(Date(ticket.submissionDate))

                    val tvStatus = findViewById<TextView>(R.id.tvDetailStatus)
                    val layoutActionButtons = findViewById<LinearLayout>(R.id.layoutActionButtons)

                    if (isFromLog) {
                        layoutActionButtons.visibility = View.GONE
                        applyStatusStyle(tvStatus, ticket.status)
                    } else {
                        layoutActionButtons.visibility = View.VISIBLE
                        applyStatusStyle(tvStatus, ticket.status)

                        if (ticket.status.equals("Pending", ignoreCase = true)) {
                            btnUpdateProgress.visibility = View.VISIBLE
                            btnMarkResolved.visibility = View.VISIBLE
                        } else if (ticket.status.equals("In-Progress", ignoreCase = true)) {
                            btnUpdateProgress.visibility = View.GONE
                            btnMarkResolved.visibility = View.VISIBLE
                        } else {
                            layoutActionButtons.visibility = View.GONE
                        }
                    }

                    val ivPhoto = findViewById<ImageView>(R.id.ivDetailPhoto)
                    if (ticket.maintenancePhotoURL.isNotEmpty() && ticket.maintenancePhotoURL != "No Photo") {
                        ivPhoto.visibility = View.VISIBLE
                        loadImageFromUrl(ticket.maintenancePhotoURL, ivPhoto)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load ticket details.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyStatusStyle(tvStatus: TextView, status: String) {
        when {
            status.equals("Pending", ignoreCase = true) -> {
                tvStatus.text = "UNRESOLVED"
                tvStatus.setTextColor(Color.parseColor("#DC2626"))
            }
            status.equals("In-Progress", ignoreCase = true) -> {
                tvStatus.text = "IN PROGRESS"
                tvStatus.setTextColor(Color.parseColor("#2563EB"))
            }
            else -> {
                tvStatus.text = "COMPLETED"
                tvStatus.setTextColor(Color.parseColor("#15803D"))
            }
        }
    }

    private fun updateTicketStatus(newStatus: String, clickedButton: Button) {
        if (ticketId == null) return

        btnUpdateProgress.isEnabled = false
        btnMarkResolved.isEnabled = false
        val originalText = clickedButton.text
        clickedButton.text = "Updating..."

        val updates = mapOf(
            "status" to newStatus,
            "lastUpdatedDate" to System.currentTimeMillis()
        )

        db.collection("MAINTENANCE_TICKETS").document(ticketId!!).update(updates)
            .addOnSuccessListener {
                createAuditLog(newStatus)
                Toast.makeText(this, "Ticket updated to $newStatus", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                btnUpdateProgress.isEnabled = true
                btnMarkResolved.isEnabled = true
                clickedButton.text = originalText
            }
    }

    private fun createAuditLog(newStatus: String) {
        val managerId = auth.currentUser?.uid ?: return
        val logId = "LOG_${UUID.randomUUID().toString().take(6).uppercase()}"
        val auditLog = AuditLog(
            logId, System.currentTimeMillis(), managerId, "Manager", "MANAGER", "DATABASE", "UPDATE_TICKET_STATUS",
            "Updated ticket $ticketId status to $newStatus", mapOf("ticketID" to ticketId!!, "newStatus" to newStatus), "VERIFIED"
        )
        db.collection("AUDIT_LOGS").document(logId).set(auditLog)
    }

    private fun loadImageFromUrl(url: String, imageView: ImageView) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    BitmapFactory.decodeStream(connection.inputStream)
                }
                imageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}