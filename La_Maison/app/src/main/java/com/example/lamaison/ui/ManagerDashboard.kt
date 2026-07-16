package com.example.lamaison.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lamaison.R
import com.example.lamaison.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ManagerDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var sessionManager: SessionManager

    private lateinit var tvWelcomeName: TextView
    private lateinit var tvSlipsCount: TextView
    private lateinit var tvIssuesCount: TextView
    private lateinit var tvNextBatchDueDate: TextView

    private var slipsListener: ListenerRegistration? = null
    private var issuesListener: ListenerRegistration? = null
    private var unitsListener: ListenerRegistration? = null

    companion object {
        //private const val BATCH_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000L
        private const val BATCH_COOLDOWN_MS = 60L * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_dashboard)

        sessionManager = SessionManager(this)

        tvWelcomeName = findViewById(R.id.tvWelcomeName)
        tvSlipsCount = findViewById(R.id.tvSlipsCount)
        tvIssuesCount = findViewById(R.id.tvIssuesCount)
        tvNextBatchDueDate = findViewById(R.id.tvNextBatchDueDate)
        bottomNav = findViewById(R.id.bottom_navigation_manager)

        val btnSignOutTop = findViewById<TextView>(R.id.btnSignOutTop)
        val cardSlipsQueued = findViewById<LinearLayout>(R.id.cardSlipsQueued)
        val cardOpenIssues = findViewById<LinearLayout>(R.id.cardOpenIssues)

        val btnActionRentRoll = findViewById<LinearLayout>(R.id.btnActionRentRoll)
        val btnActionPaymentQueue = findViewById<LinearLayout>(R.id.btnActionPaymentQueue)
        val btnActionMaintenanceLog = findViewById<LinearLayout>(R.id.btnActionMaintenanceLog)
        val btnActionReportUnits = findViewById<LinearLayout>(R.id.btnActionReportUnits)
        val btnActionReportLeases = findViewById<LinearLayout>(R.id.btnActionReportLeases)
        val btnProcessPenalties = findViewById<Button>(R.id.btnProcessPenalties)

        loadManagerProfile()
        loadSupervisoryCounters()
        loadNextBatchSchedule()

        cardSlipsQueued.setOnClickListener { highlightAndTrigger(btnActionPaymentQueue) }
        cardOpenIssues.setOnClickListener { highlightAndTrigger(btnActionMaintenanceLog) }

        btnActionRentRoll.setOnClickListener { startActivity(Intent(this, MasterRentRollActivity::class.java)) }
        btnActionPaymentQueue.setOnClickListener { startActivity(Intent(this, PaymentVerificationActivity::class.java)) }
        btnActionMaintenanceLog.setOnClickListener { startActivity(Intent(this, ManagerMaintenanceHubActivity::class.java)) }
        btnActionReportUnits.setOnClickListener { startActivity(Intent(this, ManagerReportUnitsActivity::class.java)) }
        btnActionReportLeases.setOnClickListener { startActivity(Intent(this, ManagerReportLeasesActivity::class.java)) }

        btnProcessPenalties.setOnClickListener {
            val managerId = auth.currentUser?.uid
            if (managerId == null) {
                Toast.makeText(this, "Error: Authentication missing.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnProcessPenalties.isEnabled = false
            btnProcessPenalties.alpha = 0.5f

            db.collection("USERS").document(managerId).get()
                .addOnSuccessListener { document ->
                    val currentTime = System.currentTimeMillis()
                    val lastRunTime = document.getLong("lastBatchRunTime") ?: 0L
                    val nextAvailableTime = document.getLong("nextAvailableBatchTime") ?: 0L

                    // =======================================================
                    // COOLDOWN TOGGLE: Swap these blocks for your presentation
                    // =======================================================

                    // --- 1. PRODUCTION MODE: Lock button until the 1st day of next month (ACTIVE) ---
                    // val isRestricted = (currentTime < nextAvailableTime)
                    //val timeToShow = nextAvailableTime

                    // --- 2. DEMO MODE: Lock button for 1 minute (COMMENTED OUT) ---
                    val isRestricted = (currentTime - lastRunTime < BATCH_COOLDOWN_MS)
                    val timeToShow = lastRunTime + BATCH_COOLDOWN_MS

                    // =======================================================

                    if (isRestricted) {
                        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                        val formattedDate = sdf.format(Date(timeToShow))

                        val htmlMessage = androidx.core.text.HtmlCompat.fromHtml(
                            "Monthly late fee processing has already been completed.<br><br>You can run it again on: <b>$formattedDate</b>",
                            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
                        )

                        val titleView = layoutInflater.inflate(R.layout.dialog_title_warning, null)
                        val btnCloseCross = titleView.findViewById<android.widget.TextView>(R.id.btnCloseCross)

                        @android.annotation.SuppressLint("ClickableViewAccessibility")
                        btnCloseCross.setOnTouchListener { view, event ->
                            val textView = view as android.widget.TextView
                            when (event.action) {
                                android.view.MotionEvent.ACTION_DOWN -> textView.setTextColor(Color.parseColor("#DC2626"))
                                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> textView.setTextColor(Color.parseColor("#1E293B"))
                            }
                            false
                        }

                        val customDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ManagerDashboard)
                            .setCustomTitle(titleView)
                            .setMessage(htmlMessage)
                            .setPositiveButton("OK", null)
                            .setCancelable(true)
                            .show()

                        btnCloseCross.setOnClickListener { customDialog.dismiss() }

                        val okButton = customDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                        okButton.setTextColor(Color.parseColor("#0F172A"))
                        okButton.typeface = android.graphics.Typeface.DEFAULT_BOLD

                        btnProcessPenalties.isEnabled = true
                        btnProcessPenalties.alpha = 1.0f
                    } else {
                        // --- FRONTEND DATE CALCULATION LOGIC ---
                        // Get current system time, add 1 month, set to 1st day, set to midnight
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MONTH, 1)
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)

                        val newDueDateMillis = calendar.timeInMillis

                        // Pass the calculated timestamp to the batch execution
                        executeBatchProcess(managerId, currentTime, newDueDateMillis, btnProcessPenalties)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to verify batch status.", Toast.LENGTH_SHORT).show()
                    btnProcessPenalties.isEnabled = true
                    btnProcessPenalties.alpha = 1.0f
                }
        }

        btnSignOutTop.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_signout, null)
            val customDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            customDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            dialogView.findViewById<Button>(R.id.btnDialogCancel).setOnClickListener {
                customDialog.dismiss()
            }

            dialogView.findViewById<Button>(R.id.btnDialogConfirm).setOnClickListener {
                customDialog.dismiss()
                sessionManager.logout()
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            customDialog.show()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_manager_home -> true
                R.id.nav_manager_profile -> {
                    startActivity(Intent(this, UserProfileActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    private fun highlightAndTrigger(targetView: View) {
        targetView.animate()
            .scaleX(1.03f)
            .scaleY(1.03f)
            .alpha(0.5f)
            .setDuration(150)
            .withEndAction {
                targetView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(150)
                    .withEndAction { targetView.performClick() }
                    .start()
            }
            .start()
    }

    private fun loadManagerProfile() {
        val cachedName = sessionManager.getCachedName()
        if (cachedName != null) {
            tvWelcomeName.text = "Welcome, $cachedName"
        } else {
            tvWelcomeName.text = "Welcome, Loading..."
        }

        val userId = auth.currentUser?.uid ?: return
        db.collection("USERS").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val fullName = document.getString("fullName") ?: "Manager Portal"
                    tvWelcomeName.text = "Welcome, $fullName"
                    sessionManager.saveManagerDetails(fullName, "Assigned Jurisdiction: La Maison Diamond Wing")
                }
            }
            .addOnFailureListener {
                if (cachedName == null) tvWelcomeName.text = "Welcome, Manager Portal"
            }
    }

    private fun executeBatchProcess(managerId: String, executionTime: Long, newDueDate: Long, button: Button) {
        lifecycleScope.launch(Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val url = "http://10.0.2.2:8080/api/batch-process/penalties?managerId=$managerId&newDueDate=$newDueDate"

            val request = okhttp3.Request.Builder()
                .url(url)
                .post(okhttp3.FormBody.Builder().build())
                .build()

            try {
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {

                        // =======================================================
                        // SAVE TOGGLE: Swap these blocks for your presentation
                        // =======================================================

                        // --- 1. PRODUCTION MODE: Saves the monthly timestamp (ACTIVE) ---
                        // val data = mapOf(
                        //    "lastBatchRunTime" to executionTime,
                        //  "nextAvailableBatchTime" to newDueDate
                        //)

                        // --- 2. DEMO MODE: Saves only the last run time (COMMENTED OUT) ---
                        val data = mapOf("lastBatchRunTime" to executionTime)

                        // =======================================================

                        db.collection("USERS").document(managerId).set(data, SetOptions.merge())
                        Toast.makeText(this@ManagerDashboard, "⚙️ Batch Processing finished successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@ManagerDashboard, "⚙️ Server Error: Code ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.util.Log.e("BatchProcess", "Network Request Failed", e)
                    Toast.makeText(this@ManagerDashboard, "⚙️ Request Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    button.isEnabled = true
                    button.alpha = 1.0f
                }
            }
        }
    }

    /**
     * Cross-references both Payment Slips and Maintenance Tickets against units managed by THIS manager.
     */
    private fun loadSupervisoryCounters() {
        val currentManagerId = auth.currentUser?.uid ?: return

        val activeTicketStatuses = listOf("pending", "unresolved", "in progress", "in-progress", "open")

        // 1. Fetch the units managed by this manager first
        unitsListener = db.collection("UNITS")
            .whereEqualTo("assignedManagerID", currentManagerId)
            .addSnapshotListener { unitSnapshots, unitError ->
                if (unitError != null || unitSnapshots == null) return@addSnapshotListener

                val managedUnitIds = unitSnapshots.documents.map { it.id }
                val managedUnitCodes = unitSnapshots.documents.mapNotNull { it.getString("unitCode") }

                slipsListener?.remove()
                issuesListener?.remove()

                // 2. Real-time counter for Payment Slips (CROSS-REFERENCED BY UNIT OR MANAGER ID)
                slipsListener = db.collection("PAYMENT_SLIPS")
                    .addSnapshotListener { snapshots, error ->
                        if (error != null || snapshots == null) {
                            tvSlipsCount.text = "0"
                            return@addSnapshotListener
                        }

                        val pendingCount = snapshots.documents.count { doc ->
                            val status = doc.getString("status") ?: ""
                            val slipUnitId = doc.getString("unitID") ?: doc.getString("unitCode") ?: ""
                            val slipManagerId = doc.getString("managerID") ?: ""

                            status.equals("Pending", ignoreCase = true) &&
                                    (slipManagerId == currentManagerId || managedUnitIds.contains(slipUnitId) || managedUnitCodes.contains(slipUnitId))
                        }
                        tvSlipsCount.text = pendingCount.toString()
                    }

                // 3. Real-time counter for Maintenance Tickets (CROSS-REFERENCED BY UNIT)
                issuesListener = db.collection("MAINTENANCE_TICKETS")
                    .addSnapshotListener { snapshots, error ->
                        if (error != null || snapshots == null) {
                            tvIssuesCount.text = "0"
                            return@addSnapshotListener
                        }

                        val activeCount = snapshots.documents.count { doc ->
                            val status = doc.getString("status")?.lowercase() ?: ""
                            val ticketUnitId = doc.getString("unitID") ?: doc.getString("unitCode") ?: ""

                            activeTicketStatuses.contains(status) &&
                                    (managedUnitIds.contains(ticketUnitId) || managedUnitCodes.contains(ticketUnitId))
                        }
                        tvIssuesCount.text = activeCount.toString()
                    }
            }
    }

    /**
     * Pulls the earliest (minimum) next due date strictly across currently ACTIVE leases,
     * completely ignoring stale timestamps from expired or terminated contracts.
     */
    private fun loadNextBatchSchedule() {
        val currentManagerId = auth.currentUser?.uid ?: return
        val currentTime = System.currentTimeMillis()

        // 1. Query all leases under this manager to find currently ACTIVE tenants
        db.collection("LEASES")
            .whereEqualTo("managerID", currentManagerId)
            .get()
            .addOnSuccessListener { leaseSnaps ->
                if (leaseSnaps.isEmpty) {
                    tvNextBatchDueDate.text = "Next Penalty Batch Due: No Active Leases"
                    tvNextBatchDueDate.setTextColor(Color.parseColor("#475569"))
                    return@addOnSuccessListener
                }

                // Filter for leases that are explicitly Active AND haven't passed their end date
                val activeTenantIds = leaseSnaps.documents
                    .filter { doc ->
                        val status = doc.getString("status") ?: ""
                        val endDate = doc.getLong("endDate") ?: 0L
                        status.equals("Active", ignoreCase = true) && (endDate == 0L || endDate > currentTime)
                    }
                    .mapNotNull { it.getString("tenantID") }
                    .toSet()

                if (activeTenantIds.isEmpty()) {
                    tvNextBatchDueDate.text = "Next Penalty Batch Due: No Active Leases"
                    tvNextBatchDueDate.setTextColor(Color.parseColor("#475569"))
                    return@addOnSuccessListener
                }

                // 2. Query ledgers and ONLY calculate minimum due date for those active tenants
                db.collection("FINANCIAL_LEDGERS")
                    .whereEqualTo("managerID", currentManagerId)
                    .get()
                    .addOnSuccessListener { ledgerSnaps ->
                        val validDueDates = ledgerSnaps.documents
                            .filter { doc ->
                                val tenantId = doc.getString("tenantID") ?: doc.id
                                activeTenantIds.contains(tenantId) // ⚡ STRICT ACTIVE LEASE FILTER
                            }
                            .mapNotNull { it.getLong("nextDueDate") }
                            .filter { it > 0L }

                        val earliestDueDate = validDueDates.minOrNull()

                        if (earliestDueDate != null) {
                            val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.US)
                            val formattedDate = sdf.format(Date(earliestDueDate))

                            // Highlights red if the active schedule date is past due
                            if (earliestDueDate < currentTime) {
                                tvNextBatchDueDate.text = "Next Penalty Batch Due: $formattedDate (OVERDUE)"
                                tvNextBatchDueDate.setTextColor(Color.parseColor("#DC2626"))
                            } else {
                                tvNextBatchDueDate.text = "Next Penalty Batch Due: $formattedDate"
                                tvNextBatchDueDate.setTextColor(Color.parseColor("#475569"))
                            }
                        } else {
                            tvNextBatchDueDate.text = "Next Penalty Batch Due: Not Scheduled"
                            tvNextBatchDueDate.setTextColor(Color.parseColor("#475569"))
                        }
                    }
                    .addOnFailureListener {
                        tvNextBatchDueDate.text = "Next Penalty Batch Due: Offline"
                        tvNextBatchDueDate.setTextColor(Color.parseColor("#475569"))
                    }
            }
            .addOnFailureListener {
                tvNextBatchDueDate.text = "Next Penalty Batch Due: Offline"
                tvNextBatchDueDate.setTextColor(Color.parseColor("#475569"))
            }
    }

    override fun onResume() {
        super.onResume()
        if (::bottomNav.isInitialized) {
            bottomNav.selectedItemId = R.id.nav_manager_home
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        slipsListener?.remove()
        issuesListener?.remove()
        unitsListener?.remove()
    }
}