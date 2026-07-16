package com.example.lamaison.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.lamaison.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class StaffIamActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var etManagerName: EditText
    private lateinit var btnGenerateToken: Button
    private lateinit var btnKillSwitch: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_iam)

        etManagerName = findViewById(R.id.etManagerName)
        btnGenerateToken = findViewById(R.id.btnGenerateToken)
        btnKillSwitch = findViewById(R.id.btnKillSwitch)
        val btnBackArrow = findViewById<View>(R.id.btnBackArrow)
        btnBackArrow.setOnClickListener { finish() }

        btnGenerateToken.setOnClickListener {
            val identifier = etManagerName.text.toString().trim()
            if (identifier.isEmpty()) {
                etManagerName.error = "Manager Identifier Required"
                return@setOnClickListener
            }
            generateSecureToken(identifier)
        }

        btnKillSwitch.setOnClickListener {
            confirmAndExecuteKillSwitch()
        }
    }

    private fun generateSecureToken(managerIdentifier: String) {
        btnGenerateToken.isEnabled = false
        btnGenerateToken.text = "Authorizing..."

        // Generate LaMaison specific token
        val rawToken = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        val formattedToken = "LM-MGR-$rawToken"

        val tokenData = hashMapOf(
            "token" to formattedToken,
            "assignedTo" to managerIdentifier,
            "createdBy" to (auth.currentUser?.uid ?: "SYSTEM_ADMIN"),
            "createdAt" to System.currentTimeMillis(),
            "status" to "ACTIVE"
        )

        db.collection("MANAGER_TOKENS").document(formattedToken)
            .set(tokenData)
            .addOnSuccessListener {
                logAdminAction("GENERATED_MANAGER_TOKEN", "Created LaMaison access token for $managerIdentifier")

                etManagerName.text.clear()
                btnGenerateToken.isEnabled = true
                btnGenerateToken.text = "Generate Security Token"

                showTokenDialog(formattedToken, managerIdentifier)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to generate token: ${e.message}", Toast.LENGTH_SHORT).show()
                btnGenerateToken.isEnabled = true
                btnGenerateToken.text = "Generate Security Token"
            }
    }

    private fun confirmAndExecuteKillSwitch() {
        // Inflate the custom luxury warning design
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_kill_switch, null)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnDialogConfirm)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Make the system dialog background transparent to reveal the rounded white card
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            executeKillSwitch()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun executeKillSwitch() {
        btnKillSwitch.isEnabled = false
        btnKillSwitch.text = "EXECUTING..."

        db.collection("MANAGER_TOKENS")
            .whereEqualTo("status", "ACTIVE")
            .get()
            .addOnSuccessListener { snapshots ->
                val batch = db.batch()
                for (doc in snapshots.documents) {
                    batch.update(doc.reference, "status", "REVOKED_BY_ADMIN")
                }

                batch.commit().addOnSuccessListener {
                    logAdminAction("EMERGENCY_KILL_SWITCH", "Revoked ${snapshots.size()} active LaMaison tokens.")
                    Toast.makeText(this, "All active tokens revoked.", Toast.LENGTH_LONG).show()
                    btnKillSwitch.isEnabled = true
                    btnKillSwitch.text = "EMERGENCY KILL SWITCH"
                }.addOnFailureListener {
                    Toast.makeText(this, "Batch execution failed.", Toast.LENGTH_SHORT).show()
                    btnKillSwitch.isEnabled = true
                    btnKillSwitch.text = "EMERGENCY KILL SWITCH"
                }
            }
    }

    private fun showTokenDialog(token: String, managerName: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_token_generated, null)

        val tvManagerId = dialogView.findViewById<TextView>(R.id.tvDialogManagerId)
        val tvToken = dialogView.findViewById<TextView>(R.id.tvDialogToken)
        val btnDone = dialogView.findViewById<Button>(R.id.btnDialogDone)

        tvManagerId.text = "Manager: $managerName"
        tvToken.text = "Token: $token"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        btnDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun logAdminAction(action: String, description: String) {
        val logId = "LOG_" + UUID.randomUUID().toString().take(6).uppercase()

        val logEntry = hashMapOf(
            "logID" to logId,
            "actionType" to action,
            "actorID" to (auth.currentUser?.uid ?: "ADMIN_UID"),
            "actorName" to "System Administrator",
            "actorRole" to "ADMIN",
            "description" to description,
            "timestamp" to System.currentTimeMillis(),
            "status" to "VERIFIED"
        )

        db.collection("AUDIT_LOGS").document(logId).set(logEntry)
    }
}