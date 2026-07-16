package com.example.lamaison.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.example.lamaison.R
import com.example.lamaison.data.MaintenanceTicket
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MaintenanceDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maintenance_detail)

        findViewById<ImageView>(R.id.btnBackDetail).setOnClickListener { finish() }

        val ticketId = intent.getStringExtra("TICKET_ID") ?: return finish()

        db.collection("MAINTENANCE_TICKETS").document(ticketId).get()
            .addOnSuccessListener { doc ->
                val ticket = doc.toObject(MaintenanceTicket::class.java)

                // Extract tenantID explicitly from document data
                val tenantId = doc.getString("tenantID") ?: ""

                if (ticket != null) {
                    populateDetails(ticket, tenantId)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateDetails(ticket: MaintenanceTicket, tenantId: String) {
        findViewById<TextView>(R.id.tvDetailTitle).text = ticket.issueTitle
        findViewById<TextView>(R.id.tvDetailCategory).text = ticket.issueCategory

        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        findViewById<TextView>(R.id.tvDetailDate).text = sdf.format(Date(ticket.submissionDate))

        val formattedDescription = "\"${ticket.description}\""
        findViewById<TextView>(R.id.tvDetailDescription).text = formattedDescription

        // --- ASYNCHRONOUS TENANT NAME FETCH ---
        // Ensure you have an ID named 'tvDetailTenant' in your XML layout!


        // --- DYNAMICALLY STYLE THE STATUS PILL ---
        val tvStatus = findViewById<TextView>(R.id.tvDetailStatus)
        val cardStatus = findViewById<CardView>(R.id.cardDetailStatus)

        tvStatus.text = ticket.status.uppercase()

        when (ticket.status.lowercase()) {
            "pending", "unresolved" -> {
                tvStatus.setTextColor(Color.parseColor("#D97706"))
                cardStatus.setCardBackgroundColor(Color.parseColor("#FEF3C7"))
            }
            "in-progress", "in progress" -> {
                tvStatus.setTextColor(Color.parseColor("#1D4ED8"))
                cardStatus.setCardBackgroundColor(Color.parseColor("#EFF6FF"))
            }
            "resolved", "completed" -> {
                tvStatus.setTextColor(Color.parseColor("#047857"))
                cardStatus.setCardBackgroundColor(Color.parseColor("#DCFCE7"))
            }
            else -> {
                tvStatus.setTextColor(Color.parseColor("#B91C1C"))
                cardStatus.setCardBackgroundColor(Color.parseColor("#FEF2F2"))
            }
        }

        val ivPhoto = findViewById<ImageView>(R.id.ivDetailPhoto)
        if (ticket.maintenancePhotoURL.isNotEmpty()) {
            ivPhoto.visibility = View.VISIBLE
            Glide.with(this).load(ticket.maintenancePhotoURL).into(ivPhoto)
        } else {
            ivPhoto.visibility = View.GONE
        }
    }
}