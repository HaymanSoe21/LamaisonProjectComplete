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
import com.example.lamaison.R
import com.example.lamaison.utils.SessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TenantDashboard : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var sessionManager: SessionManager

    // Dynamic contact variables (with fallbacks just in case)
    private var assignedManagerEmail: String = "management@lamaison.com"
    private var assignedManagerPhone: String = "09222222222"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tenant_dashboard)

        sessionManager = SessionManager(this)

        bottomNav = findViewById(R.id.bottom_navigation_tenant)
        val tvWelcomeName = findViewById<TextView>(R.id.tvWelcomeName)
        val tvAssignedSuite = findViewById<TextView>(R.id.tvAssignedSuite)
        val tvLedgerBalance = findViewById<TextView>(R.id.tvLedgerBalance)
        val tvNextDueDate = findViewById<TextView>(R.id.tvNextDueDate)
        val btnSignOutTop = findViewById<TextView>(R.id.btnSignOutTop)

        val tvSuiteBadge = findViewById<TextView?>(R.id.tvSuiteBadge) ?: findViewById<TextView?>(R.id.tvCardBadge)

        // Action Bindings
        val btnActionFinancialLedger = findViewById<LinearLayout>(R.id.btnActionFinancialLedger)
        val btnActionUploadPayment = findViewById<LinearLayout>(R.id.btnActionPaymentUpload)
        val btnActionRepair = findViewById<LinearLayout>(R.id.btnActionRepair)
        val btnActionContactManager = findViewById<Button>(R.id.btnActionContactManager)

        // --- 1. INSTANT LOAD FROM LOCAL CACHE ---
        val cachedName = sessionManager.getCachedName()
        val cachedFullSuiteDisplay = sessionManager.getCachedFullSuiteDisplay()
        val cachedBalance = sessionManager.getCachedBalance()
        val cachedDueDate = sessionManager.getCachedDueDate()
        val cachedLeaseStatus = sessionManager.getCachedLeaseStatus()

        if (cachedName != null) {
            tvWelcomeName.text = "Welcome, $cachedName"
            tvAssignedSuite.text = cachedFullSuiteDisplay
            tvLedgerBalance.text = cachedBalance ?: "$0.00"
            tvNextDueDate.text = cachedDueDate ?: "Next Remittance Due: Calculating..."
            tvSuiteBadge?.let { applyStatusBadgeStyle(it, cachedLeaseStatus ?: "Active") }
        } else {
            tvWelcomeName.text = "Welcome, Loading..."
            tvAssignedSuite.text = "Loading Suite Details..."
            tvLedgerBalance.text = "$0.00"
            tvSuiteBadge?.visibility = View.GONE
        }

        // --- 2. BACKGROUND FIRESTORE SYNC ---
        val userId = auth.currentUser?.uid ?: return

        db.collection("USERS").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val fullName = doc.getString("fullName") ?: "Tenant"
                    val firstName = fullName.split(" ").firstOrNull() ?: fullName
                    tvWelcomeName.text = "Welcome, $firstName"
                    sessionManager.saveUserSession(userId, firstName, "Tenant")
                }
            }

        // --- B. STRICT LEASE QUERY WITH DYNAMIC BADGE COLORING & MANAGER FETCH ---
        db.collection("LEASES")
            .whereEqualTo("tenantID", userId)
            .get()
            .addOnSuccessListener { leaseSnapshot ->
                if (!leaseSnapshot.isEmpty) {
                    val leaseDoc = leaseSnapshot.documents.maxByOrNull { it.getLong("createdAt") ?: 0L }
                    if (leaseDoc != null) {
                        val trueUnitId = leaseDoc.getString("unitID") ?: ""
                        val leaseStatus = leaseDoc.getString("status") ?: "Active"

                        tvSuiteBadge?.let {
                            it.visibility = View.VISIBLE
                            applyStatusBadgeStyle(it, leaseStatus)
                        }

                        if (trueUnitId.isNotEmpty()) {
                            db.collection("UNITS").document(trueUnitId).get()
                                .addOnSuccessListener { unitDoc ->
                                    if (unitDoc != null && unitDoc.exists()) {
                                        val unitCode = unitDoc.getString("unitCode") ?: "Ref-None"
                                        val unitNum = unitDoc.getString("unitNumber") ?: "Suite"
                                        val building = unitDoc.getString("buildingName") ?: "La Maison Wing"
                                        val address = unitDoc.getString("propertyAddress") ?: "Mandalay, Myanmar"

                                        val fullDisplayString = "Unit $unitNum, $building\n$address"
                                        tvAssignedSuite.text = fullDisplayString

                                        sessionManager.saveTenantUnitProfile(trueUnitId, unitCode, unitNum, building, address, leaseStatus)

                                        // Fetch the assigned manager's contact details dynamically
                                        val assignedManagerID = unitDoc.getString("assignedManagerID") ?: ""
                                        if (assignedManagerID.isNotEmpty()) {
                                            db.collection("USERS").document(assignedManagerID).get()
                                                .addOnSuccessListener { managerDoc ->
                                                    if (managerDoc != null && managerDoc.exists()) {
                                                        managerDoc.getString("email")?.let { assignedManagerEmail = it }
                                                        managerDoc.getString("phoneNumber")?.let { assignedManagerPhone = it }
                                                    }
                                                }
                                        }

                                    } else {
                                        handleUnassignedState(tvAssignedSuite, tvSuiteBadge)
                                    }
                                }
                                .addOnFailureListener { handleUnassignedState(tvAssignedSuite, tvSuiteBadge) }
                        } else {
                            handleUnassignedState(tvAssignedSuite, tvSuiteBadge)
                        }
                    } else {
                        handleUnassignedState(tvAssignedSuite, tvSuiteBadge)
                    }
                } else {
                    handleUnassignedState(tvAssignedSuite, tvSuiteBadge)
                }
            }

        db.collection("FINANCIAL_LEDGERS").whereEqualTo("tenantID", userId).get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val ledgerDoc = querySnapshot.documents.first()
                    val balance = ledgerDoc.getLong("currentBalance")?.toInt() ?: 0
                    val dueDateEpoch = ledgerDoc.getLong("nextDueDate") ?: System.currentTimeMillis()

                    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
                    val formattedBalance = currencyFormat.format(balance)
                    tvLedgerBalance.text = formattedBalance

                    val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.US)
                    val formattedDueDate = "Next Remittance Due: ${sdf.format(Date(dueDateEpoch))}"
                    tvNextDueDate.text = formattedDueDate

                    sessionManager.saveTenantFinancials(formattedBalance, formattedDueDate)
                }
            }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_tenant_home -> true
                R.id.nav_tenant_profile -> {
                    startActivity(Intent(this, UserProfileActivity::class.java))
                    false
                }
                else -> false
            }
        }

        // --- BUTTON CLICK LISTENERS ---

        btnActionFinancialLedger.setOnClickListener {
            startActivity(Intent(this, TenantFinancialLedgerActivity::class.java))
        }

        btnActionUploadPayment.setOnClickListener {
            startActivity(Intent(this, TenantPaymentActivity::class.java))
        }

        btnActionRepair.setOnClickListener {
            startActivity(Intent(this, MaintenanceHubActivity::class.java))
        }

        // --- UPDATED: CONTACT MANAGER BUTTON WITH CORRECTED GUARDRAIL ---
        btnActionContactManager.setOnClickListener {
            btnActionContactManager.isEnabled = false
            val originalText = btnActionContactManager.text
            btnActionContactManager.text = "⏳ Verifying..."

            // 1. Check Financial Ledger Balance
            db.collection("FINANCIAL_LEDGERS")
                .whereEqualTo("tenantID", userId)
                .get()
                .addOnSuccessListener { ledgerSnaps ->
                    val ledgerDoc = ledgerSnaps.documents.firstOrNull()
                    val currentBalance = ledgerDoc?.getLong("currentBalance") ?: 0L

                    // 2. Check Lease Status
                    db.collection("LEASES")
                        .whereEqualTo("tenantID", userId)
                        .get()
                        .addOnSuccessListener { leaseSnaps ->
                            btnActionContactManager.isEnabled = true
                            btnActionContactManager.text = originalText

                            val latestLease = leaseSnaps.documents.maxByOrNull { it.getLong("createdAt") ?: 0L }
                            val leaseStatus = latestLease?.getString("status") ?: "None"

                            val isLeaseActive = leaseStatus.equals("Active", ignoreCase = true) ||
                                    leaseStatus.equals("Pending_Renewal", ignoreCase = true) ||
                                    leaseStatus.equals("Pending Renewal", ignoreCase = true)

                            val hasOutstandingDebt = currentBalance > 0L

                            // THE RULE: Block ONLY if they have no active lease AND owe no money!
                            if (!isLeaseActive && !hasOutstandingDebt) {
                                Toast.makeText(
                                    this@TenantDashboard,
                                    "❌ Access Denied: You have no active lease and your account balance is fully settled.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@addOnSuccessListener
                            }

                            // Proceed to launch contact dialog if active or has debt to resolve
                            showContactManagerDialog()
                        }
                        .addOnFailureListener { e ->
                            btnActionContactManager.isEnabled = true
                            btnActionContactManager.text = originalText
                            Toast.makeText(this@TenantDashboard, "❌ Error verifying lease: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    btnActionContactManager.isEnabled = true
                    btnActionContactManager.text = originalText
                    Toast.makeText(this@TenantDashboard, "❌ Error verifying balance: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // --- WIRE UP THE LUXURY SIGN-OUT DIALOG ---
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
    }

    /**
     * Renders the multi-option contact modal once guardrail verification passes.
     */
    private fun showContactManagerDialog() {
        val suiteInfo = findViewById<TextView>(R.id.tvAssignedSuite).text.toString().replace("\n", " ")

        val dialogView = layoutInflater.inflate(R.layout.dialog_contact_user, null)
        val contactDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        contactDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Contact Management"
        dialogView.findViewById<TextView>(R.id.tvDialogSubtitle).text = "How would you like to reach the property manager for your assigned suite?"

        val btnEmail = dialogView.findViewById<Button>(R.id.btnEmailContact)
        btnEmail.text = "✉️  Email Office"

        val btnCall = dialogView.findViewById<Button>(R.id.btnCallContact)
        btnCall.text = "📞  Call Office"

        btnEmail.setOnClickListener {
            contactDialog.dismiss()
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:$assignedManagerEmail")
                putExtra(Intent.EXTRA_SUBJECT, "Tenant Inquiry - $suiteInfo")
            }
            try {
                startActivity(emailIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "No email client installed.", Toast.LENGTH_SHORT).show()
            }
        }

        btnCall.setOnClickListener {
            contactDialog.dismiss()
            val phoneIntent = Intent(Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:$assignedManagerPhone")
            }
            try {
                startActivity(phoneIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "No phone dialer available.", Toast.LENGTH_SHORT).show()
            }
        }

        contactDialog.show()
    }

    private fun applyStatusBadgeStyle(badge: TextView, status: String) {
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        drawable.cornerRadius = 100f

        when (status.lowercase()) {
            "active" -> {
                badge.text = "• ACTIVE LEASE"
                badge.setTextColor(Color.parseColor("#047857"))
                drawable.setColor(Color.parseColor("#ECFDF5"))
                drawable.setStroke(3, Color.parseColor("#34D399"))
            }
            "pending_renewal", "pending renewal", "pending" -> {
                badge.text = "• PENDING RENEWAL"
                badge.setTextColor(Color.parseColor("#B45309"))
                drawable.setColor(Color.parseColor("#FFFBEB"))
                drawable.setStroke(3, Color.parseColor("#FCD34D"))
            }
            "terminated" -> {
                badge.text = "• TERMINATED"
                badge.setTextColor(Color.parseColor("#B91C1C"))
                drawable.setColor(Color.parseColor("#FEF2F2"))
                drawable.setStroke(3, Color.parseColor("#F87171"))
            }
            "expired" -> {
                badge.text = "• EXPIRED"
                badge.setTextColor(Color.parseColor("#334155"))
                drawable.setColor(Color.parseColor("#F8FAFC"))
                drawable.setStroke(3, Color.parseColor("#CBD5E1"))
            }
            else -> {
                badge.text = "• " + status.uppercase().replace("_", " ")
                badge.setTextColor(Color.parseColor("#1D4ED8"))
                drawable.setColor(Color.parseColor("#EFF6FF"))
                drawable.setStroke(3, Color.parseColor("#60A5FA"))
            }
        }
        badge.background = drawable
    }

    private fun handleUnassignedState(tvAssignedSuite: TextView, tvSuiteBadge: TextView?) {
        sessionManager.clearTenantUnitProfile()
        tvAssignedSuite.text = "Not yet Assigned"
        tvSuiteBadge?.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (::bottomNav.isInitialized) {
            bottomNav.selectedItemId = R.id.nav_tenant_home
        }
    }
}