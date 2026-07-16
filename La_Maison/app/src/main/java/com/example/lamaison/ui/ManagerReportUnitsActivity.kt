package com.example.lamaison.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.lamaison.R
import com.example.lamaison.data.AuditLog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID

class ManagerReportUnitsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var viewProposalForm: LinearLayout
    private lateinit var viewApprovedDirectory: LinearLayout
    private lateinit var approvedUnitsContainer: LinearLayout
    private lateinit var tvHeaderTitle: TextView

    private lateinit var etUnitCode: EditText
    private lateinit var etUnitNumber: EditText
    private lateinit var etBuildingName: EditText
    private lateinit var etPropertyAddress: EditText
    private lateinit var etBaseRent: EditText
    private lateinit var spinnerOccupancy: Spinner
    private lateinit var spinnerActionType: Spinner
    private lateinit var btnVerifyUnit: TextView
    private lateinit var btnSubmitProposal: Button

    private var approvedUnitsListener: ListenerRegistration? = null
    private var existingUnitId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_report_units)

        val btnBackArrow = findViewById<View>(R.id.btnBackArrow)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        viewProposalForm = findViewById(R.id.viewProposalForm)
        viewApprovedDirectory = findViewById(R.id.viewApprovedDirectory)
        approvedUnitsContainer = findViewById(R.id.approvedUnitsContainer)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavUnits)

        val viewMode = intent.getStringExtra("VIEW_MODE")
        if (viewMode == "APPROVED_ONLY") {
            viewProposalForm.visibility = View.GONE
            bottomNav.visibility = View.GONE
            viewApprovedDirectory.visibility = View.VISIBLE
            tvHeaderTitle.text = "Verified Units Portfolio"
        }

        etUnitCode = findViewById(R.id.etUnitCode)
        etUnitNumber = findViewById(R.id.etUnitNumber)
        etBuildingName = findViewById(R.id.etBuildingName)
        etPropertyAddress = findViewById(R.id.etPropertyAddress)
        etBaseRent = findViewById(R.id.etBaseRent)
        spinnerOccupancy = findViewById(R.id.spinnerOccupancy)
        spinnerActionType = findViewById(R.id.spinnerActionType)
        btnVerifyUnit = findViewById(R.id.btnVerifyUnit)
        btnSubmitProposal = findViewById(R.id.btnSubmitProposal)

        btnBackArrow.setOnClickListener { finish() }

        setupOccupancySpinner(arrayOf("Vacant", "Under Maintenance"))
        setupActionTypeSpinner(arrayOf("Verify Unit First"))

        btnVerifyUnit.setOnClickListener { verifyUnitCodeRelation() }
        btnSubmitProposal.setOnClickListener { submitUnitAction() }
        findViewById<TextView>(R.id.btnClearForm).setOnClickListener {
            clearAndUnlockFields()
            Toast.makeText(this, "Form cleared", Toast.LENGTH_SHORT).show()
        }

        loadApprovedAdditions()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_propose_unit -> {
                    tvHeaderTitle.text = "Propose New Suite"
                    viewProposalForm.visibility = View.VISIBLE
                    viewApprovedDirectory.visibility = View.GONE
                    true
                }
                R.id.nav_approved_units -> {
                    tvHeaderTitle.text = "Approved Additions"
                    viewProposalForm.visibility = View.GONE
                    viewApprovedDirectory.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun setupActionTypeSpinner(options: Array<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerActionType.adapter = adapter
    }

    private fun setupOccupancySpinner(options: Array<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerOccupancy.adapter = adapter
    }

    private fun verifyUnitCodeRelation() {
        val rawCode = etUnitCode.text.toString().trim()
        if (rawCode.isEmpty()) {
            Toast.makeText(this, "⚠️ Enter a Unit Code first.", Toast.LENGTH_SHORT).show()
            return
        }

        // --- ENFORCED VALIDATION: Letters, Hyphen, Digits, Letters ---
        val code = rawCode.uppercase()
        val unitCodeRegex = Regex("^[A-Z]+-\\d+[A-Z]+$")
        if (!unitCodeRegex.matches(code)) {
            Toast.makeText(this, "⚠️ Invalid format! Use Letters-DigitsLetters (e.g., DIA-6B).", Toast.LENGTH_LONG).show()
            return
        }

        val managerId = auth.currentUser?.uid ?: return
        btnVerifyUnit.text = "..."

        db.collection("UNITS").whereEqualTo("unitCode", code).get()
            .addOnSuccessListener { querySnapshot ->
                val unitDoc = querySnapshot.documents.firstOrNull()

                if (unitDoc != null) {
                    val assignedManagerId = unitDoc.getString("assignedManagerID")
                    val status = unitDoc.getString("occupancyStatus") ?: "Vacant"

                    if (assignedManagerId == managerId) {
                        existingUnitId = unitDoc.id

                        etUnitNumber.setText(unitDoc.getString("unitNumber"))
                        etBuildingName.setText(unitDoc.getString("buildingName"))
                        etPropertyAddress.setText(unitDoc.getString("propertyAddress"))
                        etBaseRent.setText(unitDoc.getLong("baseRent")?.toString() ?: "")

                        btnVerifyUnit.text = "VERIFIED ✓"
                        btnVerifyUnit.setTextColor(Color.parseColor("#047857"))
                        etUnitCode.isEnabled = false

                        if (status.equals("Occupied", ignoreCase = true)) {
                            setupActionTypeSpinner(arrayOf("View Only"))
                            setupOccupancySpinner(arrayOf("Occupied"))
                            spinnerOccupancy.isEnabled = false
                            lockField(etUnitNumber)
                            lockField(etBuildingName)
                            lockField(etPropertyAddress)
                            lockField(etBaseRent)
                            Toast.makeText(this, "Unit is occupied. Switched to View Only mode.", Toast.LENGTH_SHORT).show()
                        } else {
                            setupActionTypeSpinner(arrayOf("Update"))
                            setupOccupancySpinner(arrayOf("Vacant", "Under Maintenance", "Inactive"))
                            spinnerOccupancy.isEnabled = true
                            val spinnerAdapter = spinnerOccupancy.adapter as ArrayAdapter<String>
                            val spinnerPosition = spinnerAdapter.getPosition(status)
                            spinnerOccupancy.setSelection(if (spinnerPosition >= 0) spinnerPosition else 0)
                            lockField(etUnitNumber)
                            lockField(etBuildingName)
                            lockField(etPropertyAddress)
                            unlockField(etBaseRent)
                            Toast.makeText(this, "Existing unit found. Switched to Update mode.", Toast.LENGTH_SHORT).show()
                        }
                    } else if (status.equals("Inactive", ignoreCase = true)) {
                        existingUnitId = unitDoc.id
                        etUnitNumber.setText(unitDoc.getString("unitNumber"))
                        etBuildingName.setText(unitDoc.getString("buildingName"))
                        etPropertyAddress.setText(unitDoc.getString("propertyAddress"))
                        etBaseRent.setText(unitDoc.getLong("baseRent")?.toString() ?: "")

                        btnVerifyUnit.text = "CLAIM ✓"
                        btnVerifyUnit.setTextColor(Color.parseColor("#D97706"))
                        etUnitCode.isEnabled = false

                        setupActionTypeSpinner(arrayOf("Take Ownership"))
                        setupOccupancySpinner(arrayOf("Vacant", "Under Maintenance"))
                        spinnerOccupancy.isEnabled = true
                        unlockStaticFields()
                        unlockField(etBaseRent)

                        Toast.makeText(this, "Unit is Inactive. You can claim and reactivate it.", Toast.LENGTH_LONG).show()
                    } else {
                        btnVerifyUnit.text = "VERIFY"
                        Toast.makeText(this, "❌ ERROR: Unit Code '$code' is managed by someone else.", Toast.LENGTH_LONG).show()
                        clearAndUnlockFields()
                    }
                } else {
                    btnVerifyUnit.text = "NEW ✓"
                    btnVerifyUnit.setTextColor(Color.parseColor("#047857"))
                    etUnitCode.isEnabled = false
                    setupActionTypeSpinner(arrayOf("Add New"))
                    setupOccupancySpinner(arrayOf("Vacant", "Under Maintenance"))
                    spinnerOccupancy.isEnabled = true
                    unlockStaticFields()
                    unlockField(etBaseRent)
                    existingUnitId = null
                    etUnitNumber.text.clear()
                    etBuildingName.text.clear()
                    etPropertyAddress.text.clear()
                    etBaseRent.text.clear()
                    spinnerOccupancy.setSelection(0)
                    Toast.makeText(this, "Unit is available. Switched to Add New mode.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                btnVerifyUnit.text = "VERIFY"
                Toast.makeText(this, "Failed to verify unit code.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun submitUnitAction() {
        val action = spinnerActionType.selectedItem.toString()
        val rawCode = etUnitCode.text.toString().trim()
        val number = etUnitNumber.text.toString().trim()
        val building = etBuildingName.text.toString().trim()
        val address = etPropertyAddress.text.toString().trim()
        val rentVal = etBaseRent.text.toString().trim().toIntOrNull()
        val status = spinnerOccupancy.selectedItem.toString()

        if (action == "Verify Unit First") {
            Toast.makeText(this, "⚠️ Please verify a Unit Code first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (rawCode.isEmpty() || number.isEmpty() || building.isEmpty() || address.isEmpty() || rentVal == null) {
            Toast.makeText(this, "⚠️ Please complete all required suite details.", Toast.LENGTH_SHORT).show()
            return
        }

        // --- ENFORCED VALIDATION: Letters, Hyphen, Digits, Letters ---
        val code = rawCode.uppercase()
        val unitCodeRegex = Regex("^[A-Z]+-\\d+[A-Z]+$")
        if (!unitCodeRegex.matches(code)) {
            Toast.makeText(this, "⚠️ Invalid format! Use Letters-DigitsLetters (e.g., DIA-6B).", Toast.LENGTH_LONG).show()
            return
        }

        if (rentVal <= 0) {
            Toast.makeText(this, "❌ ERROR: Base Rent must be greater than $0.", Toast.LENGTH_LONG).show()
            return
        }

        btnSubmitProposal.isEnabled = false
        btnSubmitProposal.text = "⏳ Processing $action..."

        val managerId = auth.currentUser?.uid ?: "MGR_DEMO"
        val timestamp = System.currentTimeMillis()
        val batch = db.batch()

        if (action == "View Only" && existingUnitId != null) {
            val logId = "LOG_" + UUID.randomUUID().toString().take(8).uppercase()
            val logRef = db.collection("AUDIT_LOGS").document(logId)
            val auditLog = AuditLog(
                logID = logId, timestamp = timestamp, actorID = managerId, actorName = "Manager ($managerId)",
                actorRole = "MANAGER", category = "DATABASE", actionType = "VIEWED_SUITE",
                description = "Manager viewed occupied Unit '$code'. No modifications were made.",
                payload = mapOf("unitID" to existingUnitId!!, "status" to "Occupied"), status = "VERIFIED"
            )
            batch.set(logRef, auditLog)
            batch.commit().addOnSuccessListener {
                Toast.makeText(this, "✅ Suite '$code' viewed successfully!", Toast.LENGTH_LONG).show()
                clearAndUnlockFields()
                resetButtonState(btnSubmitProposal)
            }.addOnFailureListener {
                Toast.makeText(this, "❌ Database Error", Toast.LENGTH_SHORT).show()
                resetButtonState(btnSubmitProposal)
            }
            return
        }

        if ((action == "Update" || action == "Take Ownership") && existingUnitId != null) {
            val unitRef = db.collection("UNITS").document(existingUnitId!!)
            batch.update(unitRef, mapOf(
                "baseRent" to rentVal,
                "occupancyStatus" to status,
                "assignedManagerID" to managerId,
                "unitNumber" to number,
                "buildingName" to building,
                "propertyAddress" to address
            ))

            val actionName = if (action == "Take Ownership") "CLAIMED_SUITE" else "UPDATED_SUITE"
            val actionDesc = if (action == "Take Ownership") "Manager claimed inactive unit '$code'." else "Updated Unit '$code'."

            val logId = "LOG_" + UUID.randomUUID().toString().take(8).uppercase()
            val logRef = db.collection("AUDIT_LOGS").document(logId)
            val auditLog = AuditLog(
                logID = logId, timestamp = timestamp, actorID = managerId, actorName = "Manager ($managerId)",
                actorRole = "MANAGER", category = "DATABASE", actionType = actionName,
                description = actionDesc,
                payload = mapOf("unitID" to existingUnitId!!, "baseRent" to rentVal, "status" to status), status = "VERIFIED"
            )
            batch.set(logRef, auditLog)
            batch.commit().addOnSuccessListener {
                Toast.makeText(this, "✅ Suite '$code' saved successfully!", Toast.LENGTH_LONG).show()
                clearAndUnlockFields()
                resetButtonState(btnSubmitProposal)
            }.addOnFailureListener {
                resetButtonState(btnSubmitProposal)
            }
        } else if (action == "Add New") {
            db.collection("UNITS").whereEqualTo("unitCode", code).get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        Toast.makeText(this, "❌ ERROR: Unit Code '$code' is already in use!", Toast.LENGTH_LONG).show()
                        resetButtonState(btnSubmitProposal)
                        return@addOnSuccessListener
                    }
                    val uniqueUnitId = "UNIT_" + UUID.randomUUID().toString().take(8).uppercase()
                    val completeUnitMap = hashMapOf(
                        "unitID" to uniqueUnitId, "unitCode" to code, "unitNumber" to number, "buildingName" to building,
                        "propertyAddress" to address, "baseRent" to rentVal, "occupancyStatus" to status,
                        "currentTenantID" to "", "assignedManagerID" to managerId, "createdAt" to timestamp
                    )
                    val unitRef = db.collection("UNITS").document(uniqueUnitId)
                    batch.set(unitRef, completeUnitMap)

                    val logId = "LOG_" + UUID.randomUUID().toString().take(8).uppercase()
                    val logRef = db.collection("AUDIT_LOGS").document(logId)
                    val auditLog = AuditLog(
                        logID = logId, timestamp = timestamp, actorID = managerId, actorName = "Manager ($managerId)",
                        actorRole = "MANAGER", category = "DATABASE", actionType = "PUBLISHED_NEW_SUITE",
                        description = "Published new Unit '$code' at $$rentVal/mo.",
                        payload = mapOf("unitID" to uniqueUnitId, "unitCode" to code, "baseRent" to rentVal), status = "VERIFIED"
                    )
                    batch.set(logRef, auditLog)
                    batch.commit().addOnSuccessListener {
                        Toast.makeText(this, "✅ Suite '$code' published to inventory!", Toast.LENGTH_LONG).show()
                        clearAndUnlockFields()
                        resetButtonState(btnSubmitProposal)
                    }.addOnFailureListener {
                        resetButtonState(btnSubmitProposal)
                    }
                }
        }
    }

    private fun lockField(editText: EditText) {
        editText.isFocusable = false
        editText.isFocusableInTouchMode = false
        editText.isClickable = false
        editText.setTextColor(Color.parseColor("#94A3B8"))
    }

    private fun unlockField(editText: EditText) {
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        editText.isClickable = true
        editText.setTextColor(Color.parseColor("#0F172A"))
    }

    private fun unlockStaticFields() {
        val fields = listOf(etUnitNumber, etBuildingName, etPropertyAddress)
        fields.forEach { unlockField(it) }
    }

    private fun clearAndUnlockFields() {
        etUnitCode.text.clear()
        etUnitCode.isEnabled = true
        etUnitNumber.text.clear()
        etBuildingName.text.clear()
        etPropertyAddress.text.clear()
        etBaseRent.text.clear()
        setupOccupancySpinner(arrayOf("Vacant", "Under Maintenance"))
        spinnerOccupancy.isEnabled = true
        setupActionTypeSpinner(arrayOf("Verify Unit First"))
        existingUnitId = null
        unlockStaticFields()
        unlockField(etBaseRent)
        btnVerifyUnit.text = "VERIFY"
        btnVerifyUnit.setTextColor(Color.parseColor("#2563EB"))
    }

    private fun resetButtonState(btn: Button) {
        btn.isEnabled = true
        btn.text = "Submit Suite Action"
    }

    private fun loadApprovedAdditions() {
        val currentManagerId = auth.currentUser?.uid ?: return

        approvedUnitsListener = db.collection("UNITS")
            .whereEqualTo("assignedManagerID", currentManagerId)
            .addSnapshotListener { snapshots, error ->
                approvedUnitsContainer.removeAllViews()
                if (error != null) return@addSnapshotListener
                if (snapshots == null || snapshots.isEmpty) {
                    val emptyView = TextView(this).apply {
                        text = "No approved database additions found yet."
                        setTextColor(0xFF64748B.toInt())
                        setPadding(16, 32, 16, 32)
                    }
                    approvedUnitsContainer.addView(emptyView)
                    return@addSnapshotListener
                }

                val sortedDocs = snapshots.documents.sortedBy { it.getString("unitCode") ?: "" }

                for (doc in sortedDocs) {
                    val unitCode = doc.getString("unitCode") ?: "Unknown Code"
                    val unitNum = doc.getString("unitNumber") ?: "Suite"
                    val building = doc.getString("buildingName") ?: "Unknown Building"
                    val address = doc.getString("propertyAddress") ?: "No Address"
                    val rent = doc.getLong("baseRent") ?: 0L
                    val occStatus = doc.getString("occupancyStatus") ?: "Vacant"
                    val currentTenantId = doc.getString("currentTenantID") ?: ""

                    val card = layoutInflater.inflate(R.layout.item_approved_card, approvedUnitsContainer, false)

                    card.findViewById<TextView>(R.id.tvCardTitle).text = "$unitNum (Code: $unitCode)"
                    card.findViewById<TextView>(R.id.tvCardSubtitle).text = building
                    card.findViewById<TextView>(R.id.tvCardExtra1).text = address

                    val tvExtra2 = card.findViewById<TextView>(R.id.tvCardExtra2)
                    tvExtra2.text = "Rent: $$rent/mo"

                    val badge = card.findViewById<TextView>(R.id.tvCardBadge)
                    badge.text = occStatus.uppercase()

                    when (occStatus.lowercase()) {
                        "occupied" -> {
                            badge.setTextColor(Color.parseColor("#047857"))
                            badge.setBackgroundColor(Color.parseColor("#D1FAE5"))
                        }
                        "vacant" -> {
                            badge.setTextColor(Color.parseColor("#D97706"))
                            badge.setBackgroundColor(Color.parseColor("#FEF3C7"))
                        }
                        "inactive" -> {
                            badge.setTextColor(Color.parseColor("#475569"))
                            badge.setBackgroundColor(Color.parseColor("#F1F5F9"))
                        }
                        else -> {
                            badge.setTextColor(Color.parseColor("#DC2626"))
                            badge.setBackgroundColor(Color.parseColor("#FEE2E2"))
                        }
                    }

                    if (currentTenantId.isNotEmpty()) {
                        db.collection("USERS").document(currentTenantId).get().addOnSuccessListener { userDoc ->
                            val username = userDoc.getString("username") ?: "Unknown"
                            tvExtra2.text = "Rent: $$rent/mo • Occupied by @$username"
                        }
                    }
                    approvedUnitsContainer.addView(card)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        approvedUnitsListener?.remove()
    }
}