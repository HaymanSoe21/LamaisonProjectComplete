package com.example.lamaison.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lamaison.R
import com.example.lamaison.data.AuditLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class AdminDataHygieneActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var btnSyncLeases: Button
    private lateinit var btnPurgeGhostUnits: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_data_hygiene)

        findViewById<ImageView>(R.id.btnBackArrow).setOnClickListener { finish() }

        btnSyncLeases = findViewById(R.id.btnSyncLeases)
        btnPurgeGhostUnits = findViewById(R.id.btnPurgeGhostUnits)

        btnSyncLeases.setOnClickListener {
            runLeaseLifecycleSync()
        }

        btnPurgeGhostUnits.setOnClickListener {
            runGhostUnitPurge()
        }
    }

    private fun runLeaseLifecycleSync() {
        btnSyncLeases.isEnabled = false
        btnSyncLeases.text = "Scanning Database..."

        val currentTime = System.currentTimeMillis()
        val adminId = auth.currentUser?.uid ?: "ADM_SYS"

        // 1. Query all currently Active leases
        db.collection("LEASES")
            .whereEqualTo("status", "Active")
            .get()
            .addOnSuccessListener { snapshots ->

                // 2. Filter locally for leases that have passed their end date
                val expiredLeases = snapshots.documents.filter { doc ->
                    val endDate = doc.getLong("endDate") ?: 0L
                    endDate > 0L && endDate <= currentTime
                }

                if (expiredLeases.isEmpty()) {
                    Toast.makeText(this, "✅ Database is clean. No expired leases found.", Toast.LENGTH_SHORT).show()
                    resetButton(btnSyncLeases, "Scan & Sync Expirations")
                    return@addOnSuccessListener
                }

                btnSyncLeases.text = "Fixing ${expiredLeases.size} anomalies..."
                val batch = db.batch()
                var resolvedCount = 0

                // 3. Batch Update: Mark Lease as Expired, Release Unit, Log it
                for (doc in expiredLeases) {
                    val leaseId = doc.id
                    val unitId = doc.getString("unitID") ?: continue
                    val unitCode = doc.getString("unitCode") ?: "Unknown"

                    val leaseRef = db.collection("LEASES").document(leaseId)
                    batch.update(leaseRef, mapOf(
                        "status" to "Expired",
                        "lastUpdated" to currentTime
                    ))

                    val unitRef = db.collection("UNITS").document(unitId)
                    batch.update(unitRef, mapOf(
                        "occupancyStatus" to "Vacant",
                        "currentTenantID" to ""
                    ))

                    val logId = "LOG_" + UUID.randomUUID().toString().take(8).uppercase()
                    val logRef = db.collection("AUDIT_LOGS").document(logId)
                    val auditLog = AuditLog(
                        logID = logId,
                        timestamp = currentTime,
                        actorID = adminId,
                        actorName = "System Automator",
                        actorRole = "ADMIN",
                        category = "DATABASE",
                        actionType = "DATA_HYGIENE_SYNC",
                        description = "System Hygiene: Automatically expired Lease '$leaseId' and released Unit '$unitCode' to Vacant.",
                        payload = mapOf("leaseID" to leaseId, "unitID" to unitId),
                        status = "VERIFIED"
                    )
                    batch.set(logRef, auditLog)
                    resolvedCount++
                }

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "🚀 Success: Reconciled $resolvedCount expired leases.", Toast.LENGTH_LONG).show()
                        resetButton(btnSyncLeases, "Scan & Sync Expirations")
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "❌ Sync Failed: ${e.message}", Toast.LENGTH_LONG).show()
                        resetButton(btnSyncLeases, "Scan & Sync Expirations")
                    }

            }.addOnFailureListener {
                Toast.makeText(this, "❌ Database query failed.", Toast.LENGTH_SHORT).show()
                resetButton(btnSyncLeases, "Scan & Sync Expirations")
            }
    }

    private fun runGhostUnitPurge() {
        btnPurgeGhostUnits.isEnabled = false
        btnPurgeGhostUnits.text = "Scanning Units..."

        val currentTime = System.currentTimeMillis()
        val adminId = auth.currentUser?.uid ?: "ADM_SYS"

        db.collection("UNITS")
            .whereEqualTo("occupancyStatus", "Occupied")
            .whereEqualTo("currentTenantID", "") // Looking for the specific anomaly
            .get()
            .addOnSuccessListener { snapshots ->

                if (snapshots.isEmpty) {
                    Toast.makeText(this, "✅ Database is clean. No Ghost Units found.", Toast.LENGTH_SHORT).show()
                    resetButton(btnPurgeGhostUnits, "Scan & Purge Ghost Units")
                    return@addOnSuccessListener
                }

                btnPurgeGhostUnits.text = "Purging ${snapshots.size()} ghost units..."
                val batch = db.batch()

                for (doc in snapshots.documents) {
                    val unitId = doc.id
                    val unitCode = doc.getString("unitCode") ?: "Unknown"

                    val unitRef = db.collection("UNITS").document(unitId)
                    batch.update(unitRef, mapOf(
                        "occupancyStatus" to "Vacant"
                    ))

                    val logId = "LOG_" + UUID.randomUUID().toString().take(8).uppercase()
                    val logRef = db.collection("AUDIT_LOGS").document(logId)
                    val auditLog = AuditLog(
                        logID = logId,
                        timestamp = currentTime,
                        actorID = adminId,
                        actorName = "System Automator",
                        actorRole = "ADMIN",
                        category = "DATABASE",
                        actionType = "DATA_HYGIENE_PURGE",
                        description = "System Hygiene: Reverted Ghost Unit '$unitCode' from Occupied to Vacant.",
                        payload = mapOf("unitID" to unitId),
                        status = "VERIFIED"
                    )
                    batch.set(logRef, auditLog)
                }

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "🚀 Success: Purged ${snapshots.size()} Ghost Units.", Toast.LENGTH_LONG).show()
                        resetButton(btnPurgeGhostUnits, "Scan & Purge Ghost Units")
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "❌ Purge Failed: ${e.message}", Toast.LENGTH_LONG).show()
                        resetButton(btnPurgeGhostUnits, "Scan & Purge Ghost Units")
                    }

            }.addOnFailureListener {
                Toast.makeText(this, "❌ Database query failed.", Toast.LENGTH_SHORT).show()
                resetButton(btnPurgeGhostUnits, "Scan & Purge Ghost Units")
            }
    }

    private fun resetButton(button: Button, originalText: String) {
        button.isEnabled = true
        button.text = originalText
    }
}