package com.example.lamaison.ui

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lamaison.BuildConfig
import com.example.lamaison.R
import com.example.lamaison.data.AuditLog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class ManagerReportLeasesActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var viewLeaseForm: LinearLayout
    private lateinit var viewExecutedLeases: LinearLayout
    private lateinit var executedLeasesContainer: LinearLayout
    private lateinit var tvHeaderTitle: TextView
    private lateinit var btnUploadContractDoc: TextView

    private lateinit var etUnitCode: EditText
    private lateinit var etTenantUsername: EditText
    private lateinit var etMonthlyRent: EditText
    private lateinit var etSecurityDeposit: EditText
    private lateinit var etStartDate: EditText
    private lateinit var spinnerAction: Spinner
    private lateinit var spinnerDuration: Spinner
    private lateinit var btnVerifyUser: TextView
    private lateinit var btnVerifyUnitCode: TextView
    private lateinit var btnSubmitLeasePacket: Button

    private var contractDocUri: Uri? = null
    private var executedLeasesListener: ListenerRegistration? = null

    private var verifiedTenantUid: String? = null
    private var verifiedCleanUsername: String? = null
    private var currentActiveLeaseId: String? = null
    private var verifiedUnitId: String? = null

    private var startTimestamp: Long = 0L
    private var endTimestamp: Long = 0L
    private var isAutoFilling = false
    private var currentSelectedAction = "Verify User First"

    // TRACKS WHETHER A RENEWAL IS FOR An EXPIRED OR ACTIVE LEASE
    private var isCurrentlyExpiredBeforeAction = false

    private val durationMap = mapOf(
        "1 Month" to 1, "3 Months" to 3, "6 Months" to 6,
        "12 Months (1 Year)" to 12, "24 Months (2 Years)" to 24
    )

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            contractDocUri = it
            btnUploadContractDoc.text = "✅ Contract Image Selected"
            btnUploadContractDoc.setTextColor(0xFF047857.toInt())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_report_leases)

        findViewById<ImageView>(R.id.btnBackArrow).setOnClickListener { finish() }
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        viewLeaseForm = findViewById(R.id.viewLeaseForm)
        viewExecutedLeases = findViewById(R.id.viewExecutedLeases)
        executedLeasesContainer = findViewById(R.id.executedLeasesContainer)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavLeases)

        etUnitCode = findViewById(R.id.etUnitCode)
        etTenantUsername = findViewById(R.id.etTenantUsername)
        etMonthlyRent = findViewById(R.id.etMonthlyRent)
        etSecurityDeposit = findViewById(R.id.etSecurityDeposit)
        etStartDate = findViewById(R.id.etStartDate)
        spinnerAction = findViewById(R.id.spinnerAction)
        spinnerDuration = findViewById(R.id.spinnerDuration)
        btnVerifyUser = findViewById(R.id.btnVerifyUser)
        btnVerifyUnitCode = findViewById(R.id.btnVerifyUnitCode)
        btnSubmitLeasePacket = findViewById(R.id.btnSubmitLeasePacket)
        btnUploadContractDoc = findViewById(R.id.btnUploadContractDoc)

        etMonthlyRent.isFocusable = false
        etMonthlyRent.isFocusableInTouchMode = false
        etMonthlyRent.isClickable = false

        btnUploadContractDoc.setOnClickListener { pickImageLauncher.launch("image/*") }

        setupActionSpinner(arrayOf("Verify User First"))
        setupDurationSpinner()

        spinnerAction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isAutoFilling) return
                val newAction = parent?.getItemAtPosition(position).toString()
                if (newAction != currentSelectedAction) {
                    applyUIState(newAction)
                    if (newAction == "Bound") {
                        etUnitCode.text.clear()
                        etMonthlyRent.text.clear()
                        etSecurityDeposit.text.clear()
                        etStartDate.text.clear()
                        contractDocUri = null
                        btnUploadContractDoc.text = "Tap to Upload Image 📸"
                        btnUploadContractDoc.setTextColor(0xFF2563EB.toInt())
                        resetUnitVerifyButton()
                        verifiedUnitId = null
                        startTimestamp = 0L
                        endTimestamp = 0L
                        Toast.makeText(this@ManagerReportLeasesActivity, "Fields reset for new lease binding.", Toast.LENGTH_SHORT).show()
                    }
                    currentSelectedAction = newAction
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnVerifyUser.setOnClickListener { verifyTenantAndFetchLease() }
        btnVerifyUnitCode.setOnClickListener { verifyUnitAndFetchRent() }
        etStartDate.setOnClickListener { showDatePicker() }

        spinnerDuration.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                calculateEndDate()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSubmitLeasePacket.setOnClickListener { validateAndSubmitForm() }
        loadExecutedLeaseHistory()

        findViewById<TextView>(R.id.btnClearForm).setOnClickListener {
            clearFormFields()
            Toast.makeText(this, "Form cleared", Toast.LENGTH_SHORT).show()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_submit_packet -> {
                    tvHeaderTitle.text = "Submit Lease Packet"
                    viewLeaseForm.visibility = View.VISIBLE
                    viewExecutedLeases.visibility = View.GONE
                    true
                }
                R.id.nav_executed_leases -> {
                    tvHeaderTitle.text = "Bound Contracts"
                    viewLeaseForm.visibility = View.GONE
                    viewExecutedLeases.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }
    }

    private fun setupActionSpinner(options: Array<String>) {
        isAutoFilling = true
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerAction.adapter = adapter
        currentSelectedAction = options[0]
        applyUIState(currentSelectedAction)
        spinnerAction.post { isAutoFilling = false }
    }

    private fun applyUIState(action: String) {
        when (action) {
            "Terminate" -> {
                etUnitCode.isEnabled = false
                btnVerifyUnitCode.isEnabled = false
                etSecurityDeposit.isEnabled = false
                etStartDate.isEnabled = false
                spinnerDuration.isEnabled = false
                btnUploadContractDoc.isEnabled = false
            }
            "Bound", "Verify User First" -> {
                etUnitCode.isEnabled = true
                btnVerifyUnitCode.isEnabled = true
                etSecurityDeposit.isEnabled = true
                etStartDate.isEnabled = true
                spinnerDuration.isEnabled = true
                btnUploadContractDoc.isEnabled = true
            }
            "Update", "Renew" -> {
                etUnitCode.isEnabled = false
                btnVerifyUnitCode.isEnabled = false
                etSecurityDeposit.isEnabled = false
                etStartDate.isEnabled = true
                spinnerDuration.isEnabled = true
                btnUploadContractDoc.isEnabled = true
            }
        }
    }

    private fun setupDurationSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, durationMap.keys.toList())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDuration.adapter = adapter
        spinnerDuration.setSelection(2)
    }

    private fun calculateEndDate() {
        if (startTimestamp == 0L) return
        val selectedDurationLabel = spinnerDuration.selectedItem?.toString() ?: return
        val monthsToAdd = durationMap[selectedDurationLabel] ?: 6
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = startTimestamp
        cal.add(Calendar.MONTH, monthsToAdd)
        cal.add(Calendar.MILLISECOND, -1)
        endTimestamp = cal.timeInMillis
    }

    private fun verifyTenantAndFetchLease() {
        val rawUsername = etTenantUsername.text.toString().trim()
        if (rawUsername.isEmpty()) {
            Toast.makeText(this, "⚠️ Enter username first.", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanUsername = rawUsername.removePrefix("@").lowercase()
        val managerId = auth.currentUser?.uid ?: return

        btnVerifyUser.text = "..."

        db.collection("USERNAMES").document(cleanUsername).get()
            .addOnSuccessListener { usernameDoc ->
                if (!usernameDoc.exists()) {
                    Toast.makeText(this, "❌ Username not found in inventory. Form has been reset.", Toast.LENGTH_LONG).show()
                    clearFormFields()
                    return@addOnSuccessListener
                }

                val targetUid = usernameDoc.getString("uid") ?: return@addOnSuccessListener

                db.collection("USERS").document(targetUid).get()
                    .addOnSuccessListener { userDoc ->
                        val role = userDoc.getString("role") ?: ""

                        if (!role.equals("Tenant", ignoreCase = true)) {
                            Toast.makeText(this, "❌ Access Denied: @$cleanUsername is a $role. Only Tenants can sign leases.", Toast.LENGTH_LONG).show()
                            clearFormFields()
                            return@addOnSuccessListener
                        }

                        verifiedTenantUid = targetUid
                        verifiedCleanUsername = cleanUsername

                        db.collection("FINANCIAL_LEDGERS").document(verifiedTenantUid!!).get()
                            .addOnSuccessListener { ledgerDoc ->
                                val currentBalance = ledgerDoc.getLong("currentBalance") ?: 0L

                                db.collection("LEASES")
                                    .whereEqualTo("tenantID", verifiedTenantUid)
                                    .get()
                                    .addOnSuccessListener { leaseSnap ->

                                        val currentTimeMs = System.currentTimeMillis()

                                        val hasExpiredLease = leaseSnap.documents.any { doc ->
                                            var s = doc.getString("status") ?: ""
                                            val end = doc.getLong("endDate") ?: 0L
                                            val start = doc.getLong("startDate") ?: 0L

                                            // Handle live evaluation of Pending leases
                                            if (s == "Pending_Lease" && start <= currentTimeMs) {
                                                s = "Active"
                                            }

                                            s.equals("Expired", ignoreCase = true) ||
                                                    s.equals("Terminated", ignoreCase = true) ||
                                                    (s.equals("Active", ignoreCase = true) && end > 0L && end <= currentTimeMs)
                                        }

                                        val globalActiveLease = leaseSnap.documents.firstOrNull { doc ->
                                            var s = doc.getString("status") ?: "None"
                                            val end = doc.getLong("endDate") ?: 0L
                                            val start = doc.getLong("startDate") ?: 0L

                                            if (s == "Pending_Lease" && start <= currentTimeMs) {
                                                s = "Active"
                                            }
                                            if (s == "Active" && end > 0L && end <= currentTimeMs) {
                                                s = "Expired"
                                            }
                                            s == "Active" || s == "Pending_Lease"
                                        }

                                        if (globalActiveLease == null && hasExpiredLease && currentBalance > 0L) {
                                            btnVerifyUser.text = "VERIFY"
                                            btnVerifyUser.setTextColor(Color.parseColor("#2563EB"))
                                            Toast.makeText(this, "❌ Access Denied: Tenant's lease is expired with an unpaid debt of $$currentBalance. Debt must be cleared to $0 before binding or renewing.", Toast.LENGTH_LONG).show()
                                            clearFormFields()
                                            return@addOnSuccessListener
                                        }

                                        btnVerifyUser.text = "VERIFIED ✓"
                                        btnVerifyUser.setTextColor(Color.parseColor("#047857"))
                                        etTenantUsername.isEnabled = false

                                        if (globalActiveLease != null) {
                                            val leaseManagerId = globalActiveLease.getString("managerID") ?: ""

                                            if (leaseManagerId != managerId) {
                                                Toast.makeText(this, "❌ ERROR: Tenant already holds an active or pending lease with another manager.", Toast.LENGTH_LONG).show()
                                                clearFormFields()
                                                return@addOnSuccessListener
                                            } else {
                                                currentActiveLeaseId = globalActiveLease.id
                                                verifiedUnitId = globalActiveLease.getString("unitID")
                                                isCurrentlyExpiredBeforeAction = false

                                                setupActionSpinner(arrayOf("Update", "Terminate"))

                                                etUnitCode.setText(globalActiveLease.getString("unitCode"))
                                                etMonthlyRent.setText(globalActiveLease.getLong("monthlyRent")?.toString())
                                                etSecurityDeposit.setText(globalActiveLease.getLong("securityDeposit")?.toString())
                                                startTimestamp = globalActiveLease.getLong("startDate") ?: 0L
                                                endTimestamp = globalActiveLease.getLong("endDate") ?: 0L

                                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                if (startTimestamp > 0L) etStartDate.setText(sdf.format(Date(startTimestamp)))

                                                btnVerifyUnitCode.text = "AUTO ✓"
                                                btnVerifyUnitCode.setTextColor(Color.parseColor("#047857"))
                                                btnVerifyUnitCode.isEnabled = false

                                                val lStatus = globalActiveLease.getString("status") ?: "Active"
                                                if (lStatus == "Pending_Lease") {
                                                    Toast.makeText(this, "Pending lease found! Form auto-filled.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(this, "Active lease found! Form auto-filled.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            val previousManagerLease = leaseSnap.documents
                                                .filter { it.getString("managerID") == managerId }
                                                .maxByOrNull { it.getLong("createdAt") ?: 0L }

                                            if (previousManagerLease != null) {
                                                currentActiveLeaseId = previousManagerLease.id
                                                isCurrentlyExpiredBeforeAction = true
                                                setupActionSpinner(arrayOf("Renew", "Bound"))
                                                etUnitCode.setText(previousManagerLease.getString("unitCode"))
                                                verifiedUnitId = previousManagerLease.getString("unitID")
                                                etMonthlyRent.setText(previousManagerLease.getLong("monthlyRent")?.toString())
                                                etSecurityDeposit.setText(previousManagerLease.getLong("securityDeposit")?.toString())
                                                btnVerifyUnitCode.text = "AUTO ✓"
                                                btnVerifyUnitCode.setTextColor(Color.parseColor("#047857"))
                                            } else {
                                                currentActiveLeaseId = null
                                                isCurrentlyExpiredBeforeAction = true
                                                setupActionSpinner(arrayOf("Bound"))
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        btnVerifyUser.text = "VERIFY"
                                        Toast.makeText(this, "❌ Error fetching leases: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                btnVerifyUser.text = "VERIFY"
                                Toast.makeText(this, "❌ Network/DB Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
            }
    }

    private fun verifyUnitAndFetchRent() {
        val unitCode = etUnitCode.text.toString().trim()
        if (unitCode.isEmpty()) {
            Toast.makeText(this, "⚠️ Enter a Unit Code first.", Toast.LENGTH_SHORT).show()
            return
        }
        val currentManagerId = auth.currentUser?.uid ?: return
        val currentAction = spinnerAction.selectedItem?.toString() ?: ""

        btnVerifyUnitCode.text = "..."

        db.collection("UNITS").whereEqualTo("unitCode", unitCode).get().addOnSuccessListener { snap ->
            val unitDoc = snap.documents.firstOrNull()
            if (unitDoc != null) {
                val assignedManagerId = unitDoc.getString("assignedManagerID")
                val occupancyStatus = unitDoc.getString("occupancyStatus") ?: "Vacant"
                val currentTenantId = unitDoc.getString("currentTenantID") ?: ""

                if (assignedManagerId != currentManagerId) {
                    resetUnitVerifyButton()
                    Toast.makeText(this, "❌ Unauthorized. You must claim ownership of this unit via the Report Units screen first.", Toast.LENGTH_LONG).show()
                    clearUnitFields()
                    return@addOnSuccessListener
                }

                if (currentAction == "Bound") {
                    if (!occupancyStatus.equals("Vacant", ignoreCase = true)) {
                        resetUnitVerifyButton()
                        Toast.makeText(this, "❌ Unit '$unitCode' is $occupancyStatus. Only 'Vacant' units can be bound to new leases.", Toast.LENGTH_LONG).show()
                        clearUnitFields()
                        return@addOnSuccessListener
                    }
                } else if (currentAction == "Renew") {
                    if (!occupancyStatus.equals("Occupied", ignoreCase = true) && !occupancyStatus.equals("Vacant", ignoreCase = true)) {
                        resetUnitVerifyButton()
                        Toast.makeText(this, "❌ Unit '$unitCode' cannot be renewed in its current state.", Toast.LENGTH_LONG).show()
                        clearUnitFields()
                        return@addOnSuccessListener
                    }
                    if (currentTenantId.isNotEmpty() && currentTenantId != verifiedTenantUid) {
                        resetUnitVerifyButton()
                        Toast.makeText(this, "❌ Unit occupied by another tenant.", Toast.LENGTH_LONG).show()
                        clearUnitFields()
                        return@addOnSuccessListener
                    }
                } else {
                    if (!occupancyStatus.equals("Occupied", ignoreCase = true)) {
                        resetUnitVerifyButton()
                        Toast.makeText(this, "❌ Unit '$unitCode' is not Occupied.", Toast.LENGTH_LONG).show()
                        clearUnitFields()
                        return@addOnSuccessListener
                    }
                    if (currentTenantId.isNotEmpty() && currentTenantId != verifiedTenantUid) {
                        resetUnitVerifyButton()
                        Toast.makeText(this, "❌ Unit occupied by another tenant.", Toast.LENGTH_LONG).show()
                        clearUnitFields()
                        return@addOnSuccessListener
                    }
                }

                verifiedUnitId = unitDoc.id
                val baseRent = unitDoc.getLong("baseRent")?.toString() ?: "0"
                etMonthlyRent.setText(baseRent)
                etUnitCode.isEnabled = false
                btnVerifyUnitCode.text = "VERIFIED ✓"
                btnVerifyUnitCode.setTextColor(Color.parseColor("#047857"))
                Toast.makeText(this, "Unit verified.", Toast.LENGTH_SHORT).show()
            } else {
                resetUnitVerifyButton()
                Toast.makeText(this, "❌ Unit code not found in inventory.", Toast.LENGTH_SHORT).show()
                clearUnitFields()
            }
        }.addOnFailureListener {
            resetUnitVerifyButton()
            Toast.makeText(this, "❌ Network error. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetUnitVerifyButton() {
        btnVerifyUnitCode.text = "VERIFY"
        btnVerifyUnitCode.setTextColor(Color.parseColor("#2563EB"))
    }

    private fun clearUnitFields() {
        etUnitCode.text.clear()
        etMonthlyRent.text.clear()
        verifiedUnitId = null
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        // Set minimum boundary to the 1st millisecond of the CURRENT month
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val minAllowedDate = cal.timeInMillis

        // UPDATED: Allows future months by removing the PointBackward validator
        val constraintsBuilder = com.google.android.material.datepicker.CalendarConstraints.Builder()
            .setStart(minAllowedDate)
            .setValidator(com.google.android.material.datepicker.DateValidatorPointForward.from(minAllowedDate))

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Lease Start Month")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(constraintsBuilder.build())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val selectCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            selectCal.timeInMillis = selection
            selectCal.set(Calendar.DAY_OF_MONTH, 1)
            selectCal.set(Calendar.HOUR_OF_DAY, 0)
            selectCal.set(Calendar.MINUTE, 0)
            selectCal.set(Calendar.SECOND, 0)
            selectCal.set(Calendar.MILLISECOND, 0)
            startTimestamp = selectCal.timeInMillis

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            etStartDate.setText(sdf.format(selectCal.time))
            calculateEndDate()
        }
        datePicker.show(supportFragmentManager, "MATERIAL_DATE_PICKER")
    }

    private fun validateAndSubmitForm() {
        val action = spinnerAction.selectedItem.toString()
        val unitCodeInput = etUnitCode.text.toString().trim()
        val rentVal = etMonthlyRent.text.toString().trim().toIntOrNull()
        val depositVal = etSecurityDeposit.text.toString().trim().toIntOrNull()

        if (verifiedTenantUid == null || action == "Verify User First") {
            Toast.makeText(this, "⚠️ Please verify a Tenant Username first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (unitCodeInput.isEmpty() || rentVal == null || depositVal == null || startTimestamp == 0L || endTimestamp == 0L) {
            Toast.makeText(this, "⚠️ Please complete ALL required lease parameters.", Toast.LENGTH_SHORT).show()
            return
        }
        if (contractDocUri == null && action != "Terminate") {
            Toast.makeText(this, "⚠️ Please upload a signed contract document.", Toast.LENGTH_SHORT).show()
            return
        }
        if (verifiedUnitId == null) {
            Toast.makeText(this, "⚠️ Please VERIFY the Unit Code.", Toast.LENGTH_LONG).show()
            return
        }

        btnSubmitLeasePacket.isEnabled = false
        btnSubmitLeasePacket.text = "⏳ Processing $action Action..."
        val currentManagerId = auth.currentUser?.uid ?: return

        db.collection("UNITS").whereEqualTo("unitCode", unitCodeInput).get()
            .addOnSuccessListener { unitQuery ->
                if (unitQuery.isEmpty) {
                    Toast.makeText(this, "❌ Unit Code does not exist in inventory!", Toast.LENGTH_LONG).show()
                    resetButtonState()
                    return@addOnSuccessListener
                }

                val unitDoc = unitQuery.documents.first()
                val assignedManagerId = unitDoc.getString("assignedManagerID")
                val occupancyStatus = unitDoc.getString("occupancyStatus") ?: "Vacant"
                val currentTenantId = unitDoc.getString("currentTenantID") ?: ""

                if (assignedManagerId != currentManagerId) {
                    Toast.makeText(this, "❌ Unauthorized. You must claim ownership of this unit via the Report Units screen first.", Toast.LENGTH_LONG).show()
                    resetButtonState()
                    return@addOnSuccessListener
                }

                if (action == "Bound" && !occupancyStatus.equals("Vacant", ignoreCase = true)) {
                    Toast.makeText(this, "❌ Unit '$unitCodeInput' is $occupancyStatus. Only 'Vacant' units can be bound to new leases.", Toast.LENGTH_LONG).show()
                    resetButtonState()
                    return@addOnSuccessListener
                } else if (action == "Renew") {
                    if (!occupancyStatus.equals("Occupied", ignoreCase = true) && !occupancyStatus.equals("Vacant", ignoreCase = true)) {
                        Toast.makeText(this, "❌ Unit '$unitCodeInput' cannot be renewed in its current state.", Toast.LENGTH_LONG).show()
                        resetButtonState()
                        return@addOnSuccessListener
                    }
                    if (currentTenantId.isNotEmpty() && currentTenantId != verifiedTenantUid) {
                        Toast.makeText(this, "❌ Unit occupied by another tenant.", Toast.LENGTH_LONG).show()
                        resetButtonState()
                        return@addOnSuccessListener
                    }
                } else if (action == "Update" || action == "Terminate") {
                    if (!occupancyStatus.equals("Occupied", ignoreCase = true)) {
                        Toast.makeText(this, "❌ Unit '$unitCodeInput' is not Occupied.", Toast.LENGTH_LONG).show()
                        resetButtonState()
                        return@addOnSuccessListener
                    }
                    if (currentTenantId.isNotEmpty() && currentTenantId != verifiedTenantUid) {
                        Toast.makeText(this, "❌ Unit occupied by another tenant.", Toast.LENGTH_LONG).show()
                        resetButtonState()
                        return@addOnSuccessListener
                    }
                }

                db.collection("FINANCIAL_LEDGERS").document(verifiedTenantUid!!).get()
                    .addOnSuccessListener { ledgerDoc ->
                        val currentBalance = ledgerDoc.getLong("currentBalance") ?: 0L

                        if (action == "Bound" || action == "Renew") {
                            db.collection("LEASES").whereEqualTo("tenantID", verifiedTenantUid!!).get()
                                .addOnSuccessListener { leaseSnap ->
                                    val currentTimeMs = System.currentTimeMillis()
                                    val hasActiveLease = leaseSnap.documents.any { doc ->
                                        var s = doc.getString("status") ?: ""
                                        val end = doc.getLong("endDate") ?: 0L
                                        val start = doc.getLong("startDate") ?: 0L
                                        if (s == "Pending_Lease" && start <= currentTimeMs) s = "Active"

                                        (s.equals("Active", ignoreCase = true) || s.equals("Pending_Lease", ignoreCase = true)) &&
                                                (end == 0L || end > currentTimeMs)
                                    }

                                    if (!hasActiveLease && currentBalance > 0L) {
                                        Toast.makeText(this@ManagerReportLeasesActivity, "❌ Cannot execute $action: Tenant's previous lease expired with an unpaid balance of $$currentBalance. Clear balance to $0 first.", Toast.LENGTH_LONG).show()
                                        resetButtonState()
                                        return@addOnSuccessListener
                                    }
                                    proceedWithUpload(action, unitCodeInput, rentVal, depositVal, currentBalance)
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this@ManagerReportLeasesActivity, "❌ Failed to verify lease status.", Toast.LENGTH_SHORT).show()
                                    resetButtonState()
                                }
                        } else {
                            proceedWithUpload(action, unitCodeInput, rentVal, depositVal, currentBalance)
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@ManagerReportLeasesActivity, "❌ Failed to verify tenant ledger balance.", Toast.LENGTH_SHORT).show()
                        resetButtonState()
                    }
            }
    }

    private fun proceedWithUpload(action: String, unitCode: String, rentVal: Int, depositVal: Int, currentBalance: Long) {
        lifecycleScope.launch {
            var uploadedUrl = ""
            if (contractDocUri != null) {
                btnSubmitLeasePacket.text = "⏳ Uploading Document..."
                val base64Image = getBase64FromUri(contractDocUri!!)
                val resultUrl = uploadToImgBB(base64Image)
                if (resultUrl == null) {
                    Toast.makeText(this@ManagerReportLeasesActivity, "❌ Document upload failed.", Toast.LENGTH_SHORT).show()
                    resetButtonState()
                    return@launch
                }
                uploadedUrl = resultUrl
            }

            btnSubmitLeasePacket.text = "⚡ Executing Atomic Binding..."
            val currentTime = System.currentTimeMillis()
            val isTimeExpired = (endTimestamp <= currentTime)
            val isFutureLease = (startTimestamp > currentTime)

            // UPDATED STATUS LOGIC
            val finalStatus = when {
                action == "Terminate" || isTimeExpired -> "Expired"
                isFutureLease -> "Pending_Lease"
                else -> "Active"
            }

            val managerId = auth.currentUser?.uid ?: "MGR_DEMO"
            val timestamp = System.currentTimeMillis()
            val batch = db.batch()

            val finalLeaseId = if (action == "Bound") {
                "LSE_" + UUID.randomUUID().toString().take(8).uppercase()
            } else {
                currentActiveLeaseId ?: return@launch
            }
            val leaseRef = db.collection("LEASES").document(finalLeaseId)

            if (action == "Bound") {
                val leaseMap = hashMapOf<String, Any>(
                    "leaseID" to finalLeaseId, "unitID" to verifiedUnitId!!, "unitCode" to unitCode,
                    "tenantID" to verifiedTenantUid!!, "managerID" to managerId, "monthlyRent" to rentVal,
                    "securityDeposit" to depositVal, "startDate" to startTimestamp, "endDate" to endTimestamp,
                    "status" to finalStatus, "createdAt" to timestamp
                )
                if (uploadedUrl.isNotEmpty()) leaseMap["contractDocURL"] = uploadedUrl
                batch.set(leaseRef, leaseMap)
            } else {
                val updateMap = mutableMapOf<String, Any>(
                    "monthlyRent" to rentVal, "securityDeposit" to depositVal, "startDate" to startTimestamp,
                    "endDate" to endTimestamp, "status" to finalStatus, "lastUpdated" to timestamp
                )
                if (uploadedUrl.isNotEmpty()) updateMap["contractDocURL"] = uploadedUrl
                batch.update(leaseRef, updateMap)
            }

            val unitRef = db.collection("UNITS").document(verifiedUnitId!!)
            val isOccupied = (finalStatus == "Active" || finalStatus == "Pending_Lease")
            batch.update(unitRef, mapOf(
                "occupancyStatus" to if (isOccupied) "Occupied" else "Vacant",
                "currentTenantID" to if (isOccupied) verifiedTenantUid!! else "",
                "assignedManagerID" to managerId
            ))

            if (finalStatus == "Active") {
                val nextMonthCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                nextMonthCal.timeInMillis = startTimestamp
                nextMonthCal.add(Calendar.MONTH, 1)
                nextMonthCal.set(Calendar.DAY_OF_MONTH, 1)
                val calculatedNextDueDate = nextMonthCal.timeInMillis
                val ledgerRef = db.collection("FINANCIAL_LEDGERS").document(verifiedTenantUid!!)

                if (action == "Bound" || (action == "Renew" && isCurrentlyExpiredBeforeAction)) {
                    val resetBaseBalance = 0
                    val newStartingBalance = resetBaseBalance + rentVal + depositVal

                    if (action == "Bound") {
                        batch.set(ledgerRef, mapOf(
                            "ledgerID" to "LDG_" + verifiedTenantUid!!.take(8).uppercase(),
                            "tenantID" to verifiedTenantUid!!, "unitID" to verifiedUnitId!!,
                            "unitCode" to unitCode, "managerID" to managerId, "currentBalance" to newStartingBalance,
                            "securityDepositHeld" to depositVal, "lastUpdated" to timestamp,
                            "nextDueDate" to calculatedNextDueDate, "status" to "OnTime"
                        ))
                    } else {
                        batch.set(ledgerRef, mapOf(
                            "currentBalance" to newStartingBalance,
                            "securityDepositHeld" to depositVal, "lastUpdated" to timestamp,
                            "nextDueDate" to calculatedNextDueDate, "status" to "OnTime"
                        ), SetOptions.merge())
                    }
                } else {
                    batch.set(ledgerRef, mapOf(
                        "securityDepositHeld" to depositVal, "lastUpdated" to timestamp, "nextDueDate" to calculatedNextDueDate
                    ), SetOptions.merge())
                }
            } else if (finalStatus == "Pending_Lease") {
                // Do NOT touch the ledger immediately!
                // The activatePendingLease() function will handle it when the date successfully arrives.
            } else {
                val ledgerRef = db.collection("FINANCIAL_LEDGERS").document(verifiedTenantUid!!)
                val newStatus = if (currentBalance > 0) "Indebt" else "Settled"

                batch.set(ledgerRef, mapOf(
                    "status" to newStatus,
                    "lastUpdated" to timestamp
                ), SetOptions.merge())
            }

            val logId = "LOG_" + UUID.randomUUID().toString().take(8).uppercase()
            val logRef = db.collection("AUDIT_LOGS").document(logId)
            val auditLog = AuditLog(
                logID = logId, timestamp = timestamp, actorID = managerId, actorName = "Manager",
                actorRole = "MANAGER", category = "LEASE", actionType = "ACTION_$action",
                description = "Manager performed [$action] on Lease $finalLeaseId for Unit $unitCode. Status is now [$finalStatus].",
                payload = mapOf("leaseID" to finalLeaseId, "tenantID" to verifiedTenantUid!!, "status" to finalStatus), status = "VERIFIED"
            )
            batch.set(logRef, auditLog)

            batch.commit().addOnSuccessListener {
                Toast.makeText(this@ManagerReportLeasesActivity, "⚡ SUCCESS: Lease action [$action] executed!", Toast.LENGTH_LONG).show()
                clearFormFields()
                resetButtonState()
            }.addOnFailureListener {
                Toast.makeText(this@ManagerReportLeasesActivity, "❌ Action Failed", Toast.LENGTH_LONG).show()
                resetButtonState()
            }
        }
    }

    // NEW FUNCTION: Seamlessly evaluates and processes pending leases dynamically when their time has come
    private fun activatePendingLease(leaseDoc: com.google.firebase.firestore.DocumentSnapshot) {
        val leaseId = leaseDoc.id
        val tenantUid = leaseDoc.getString("tenantID") ?: return
        val unitId = leaseDoc.getString("unitID") ?: return
        val unitCode = leaseDoc.getString("unitCode") ?: return
        val managerId = leaseDoc.getString("managerID") ?: return
        val rentVal = leaseDoc.getLong("monthlyRent")?.toInt() ?: 0
        val depositVal = leaseDoc.getLong("securityDeposit")?.toInt() ?: 0
        val startTimestamp = leaseDoc.getLong("startDate") ?: 0L

        val timestamp = System.currentTimeMillis()
        val batch = db.batch()

        val leaseRef = db.collection("LEASES").document(leaseId)
        batch.update(leaseRef, mapOf("status" to "Active", "lastUpdated" to timestamp))

        val unitRef = db.collection("UNITS").document(unitId)
        batch.update(unitRef, mapOf(
            "occupancyStatus" to "Occupied",
            "currentTenantID" to tenantUid,
            "assignedManagerID" to managerId
        ))

        val nextMonthCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        nextMonthCal.timeInMillis = startTimestamp
        nextMonthCal.add(Calendar.MONTH, 1)
        nextMonthCal.set(Calendar.DAY_OF_MONTH, 1)
        val calculatedNextDueDate = nextMonthCal.timeInMillis

        val ledgerRef = db.collection("FINANCIAL_LEDGERS").document(tenantUid)
        val newStartingBalance = rentVal + depositVal

        batch.set(ledgerRef, mapOf(
            "ledgerID" to "LDG_" + tenantUid.take(8).uppercase(),
            "tenantID" to tenantUid,
            "unitID" to unitId,
            "unitCode" to unitCode,
            "managerID" to managerId,
            "currentBalance" to newStartingBalance,
            "securityDepositHeld" to depositVal,
            "lastUpdated" to timestamp,
            "nextDueDate" to calculatedNextDueDate,
            "status" to "OnTime"
        ), SetOptions.merge())

        batch.commit()
    }

    private fun clearFormFields() {
        etTenantUsername.text.clear()
        etTenantUsername.isEnabled = true
        etUnitCode.text.clear()
        etUnitCode.isEnabled = true
        btnVerifyUnitCode.isEnabled = true
        etMonthlyRent.text.clear()
        etSecurityDeposit.text.clear()
        etStartDate.text.clear()
        resetUnitVerifyButton()
        verifiedTenantUid = null
        verifiedCleanUsername = null
        currentActiveLeaseId = null
        verifiedUnitId = null
        startTimestamp = 0L
        endTimestamp = 0L
        isCurrentlyExpiredBeforeAction = false
        btnVerifyUser.text = "VERIFY"
        btnVerifyUser.setTextColor(Color.parseColor("#2563EB"))
        setupActionSpinner(arrayOf("Verify User First"))
        spinnerDuration.setSelection(2)
        contractDocUri = null
        btnUploadContractDoc.text = "Tap to Upload Image 📸"
        btnUploadContractDoc.setTextColor(0xFF2563EB.toInt())
    }

    private fun resetButtonState() {
        btnSubmitLeasePacket.isEnabled = true
        btnSubmitLeasePacket.text = "Execute Lease Action"
    }

    private fun getBase64FromUri(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private suspend fun uploadToImgBB(base64Image: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.IMGBB_API_KEY
                val url = URL("https://api.imgbb.com/1/upload?key=$apiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                val postData = "image=" + URLEncoder.encode(base64Image, "UTF-8")
                connection.outputStream.write(postData.toByteArray())

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    return@withContext jsonObject.getJSONObject("data").getString("url")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }

    private fun loadExecutedLeaseHistory() {
        val currentManagerId = auth.currentUser?.uid ?: return

        executedLeasesListener = db.collection("LEASES")
            .whereEqualTo("managerID", currentManagerId)
            .addSnapshotListener { snapshots, error ->
                executedLeasesContainer.removeAllViews()

                if (error != null) return@addSnapshotListener
                if (snapshots == null || snapshots.isEmpty) {
                    val emptyView = TextView(this).apply {
                        text = "No bound lease agreements found yet."
                        setTextColor(0xFF64748B.toInt())
                        setPadding(16, 32, 16, 32)
                    }
                    executedLeasesContainer.addView(emptyView)
                    return@addSnapshotListener
                }

                val sortedDocs = snapshots.documents.sortedByDescending { it.getLong("createdAt") ?: 0L }
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

                for (doc in sortedDocs) {
                    var status = doc.getString("status") ?: "Active"
                    val startMs = doc.getLong("startDate") ?: 0L
                    val dbEndDate = doc.getLong("endDate") ?: 0L
                    val currentTimeMs = System.currentTimeMillis()

                    // DYNAMIC EVALUATION: Fires the background ledger builder if a future lease's time has arrived!
                    if (status == "Pending_Lease" && startMs <= currentTimeMs) {
                        activatePendingLease(doc)
                        status = "Active"
                    }

                    if (status == "Active" && dbEndDate > 0L && dbEndDate <= currentTimeMs) {
                        status = "Expired"
                    }

                    val tenantUid = doc.getString("tenantID") ?: ""
                    val unitCode = doc.getString("unitCode") ?: doc.getString("unitID") ?: "Unknown Code"
                    val rent = doc.getLong("monthlyRent") ?: 0L
                    val deposit = doc.getLong("securityDeposit") ?: 0L
                    val startDateStr = if (startMs > 0) sdf.format(Date(startMs)) else "N/A"
                    val endDateStr = if (dbEndDate > 0) sdf.format(Date(dbEndDate)) else "N/A"

                    val card = layoutInflater.inflate(R.layout.item_approved_card, executedLeasesContainer, false)

                    card.findViewById<TextView>(R.id.tvCardTitle).text = "Unit Code: $unitCode"
                    card.findViewById<TextView>(R.id.tvCardSubtitle).text = "Tenant: Loading..."
                    card.findViewById<TextView>(R.id.tvCardExtra1).text = "Rent: $$rent/mo • Deposit: $$deposit"
                    card.findViewById<TextView>(R.id.tvCardExtra2).text = "Term: $startDateStr to $endDateStr"

                    val badge = card.findViewById<TextView>(R.id.tvCardBadge)
                    when (status.lowercase()) {
                        "active" -> {
                            badge.text = "ACTIVE"
                            badge.setTextColor(Color.parseColor("#047857"))
                            badge.setBackgroundColor(Color.parseColor("#D1FAE5"))
                        }
                        "pending_lease" -> {
                            badge.text = "PENDING LEASE"
                            badge.setTextColor(Color.parseColor("#8B5CF6"))
                            badge.setBackgroundColor(Color.parseColor("#EDE9FE"))
                        }
                        "pending_renewal", "pending renewal", "pending" -> {
                            badge.text = "PENDING RENEWAL"
                            badge.setTextColor(Color.parseColor("#D97706"))
                            badge.setBackgroundColor(Color.parseColor("#FEF3C7"))
                        }
                        "terminated", "expired" -> {
                            badge.text = status.uppercase()
                            badge.setTextColor(Color.parseColor("#DC2626"))
                            badge.setBackgroundColor(Color.parseColor("#FEE2E2"))
                        }
                        else -> {
                            badge.text = status.uppercase().replace("_", " ")
                            badge.setTextColor(Color.parseColor("#2563EB"))
                            badge.setBackgroundColor(Color.parseColor("#DBEAFE"))
                        }
                    }

                    if (tenantUid.isNotEmpty()) {
                        db.collection("USERS").document(tenantUid).get().addOnSuccessListener { userDoc ->
                            val username = userDoc.getString("username") ?: "Unknown"
                            card.findViewById<TextView>(R.id.tvCardSubtitle).text = "Tenant: @$username"
                        }
                    } else {
                        card.findViewById<TextView>(R.id.tvCardSubtitle).text = "Tenant: Not Assigned"
                    }
                    executedLeasesContainer.addView(card)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        executedLeasesListener?.remove()
    }
}