package com.example.lamaison.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.RentRollUIState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*

class MasterRentRollActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var rvRentRoll: RecyclerView
    private lateinit var adapter: RentRollAdapter

    private var activeRentRollList = mutableListOf<RentRollUIState>()
    private var collectionsList = mutableListOf<RentRollUIState>()
    private var isShowingActive = true

    private var rentRollListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_master_rent_roll)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val tvTabActive = findViewById<TextView>(R.id.tvTabActive)
        val tvTabCollections = findViewById<TextView>(R.id.tvTabCollections)

        tvTabActive.setOnClickListener { switchTab(true) }
        tvTabCollections.setOnClickListener { switchTab(false) }

        rvRentRoll = findViewById(R.id.rvRentRoll)
        rvRentRoll.layoutManager = LinearLayoutManager(this)

        adapter = RentRollAdapter(
            rentRollList = activeRentRollList,
            onLedgerClick = { unitId, tenantId, unitName, tenantName ->
                val intent = Intent(this, LedgerHistoryActivity::class.java).apply {
                    putExtra("UNIT_ID", unitId)
                    putExtra("TENANT_ID", tenantId)
                    putExtra("UNIT_NAME", unitName)
                    putExtra("TENANT_NAME", tenantName)
                }
                startActivity(intent)
            },
            onContactClick = { email, phone, tenantContextInfo ->
                showContactDialog(email, phone, tenantContextInfo)
            }
        )
        rvRentRoll.adapter = adapter

        val etSearchBuilding = findViewById<EditText>(R.id.etSearchBuilding)
        val btnSearch = findViewById<Button>(R.id.btnSearch)

        btnSearch.setOnClickListener { filterList(etSearchBuilding.text.toString().trim()) }

        etSearchBuilding.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterList(s.toString().trim())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadMasterRentRoll()
    }

    private fun loadMasterRentRoll() {
        val managerId = auth.currentUser?.uid ?: return

        rentRollListener = db.collection("UNITS")
            .whereEqualTo("assignedManagerID", managerId)
            .addSnapshotListener { unitSnaps, error ->
                if (error != null || unitSnaps == null || unitSnaps.isEmpty) {
                    finalizeListUpdate(emptyList(), emptyList())
                    return@addSnapshotListener
                }

                val tempActiveList = mutableListOf<RentRollUIState>()
                val tempCollectionsList = mutableListOf<RentRollUIState>()

                // 1. Map all units controlled by this manager
                val managerUnits = mutableMapOf<String, Pair<String, String>>()
                for (doc in unitSnaps.documents) {
                    val uNum = doc.getString("unitNumber") ?: "Suite"
                    val bName = doc.getString("buildingName") ?: "La Maison Wing"
                    managerUnits[doc.id] = Pair(uNum, bName)
                }

                // 2. Fetch ALL Leases to find past and present tenants
                db.collection("LEASES").get().addOnSuccessListener { leaseSnaps ->

                    val relevantLeases = leaseSnaps.documents.filter { managerUnits.containsKey(it.getString("unitID")) }
                    val uniqueTenantIds = relevantLeases.mapNotNull { it.getString("tenantID") }.distinct()

                    if (uniqueTenantIds.isEmpty()) {
                        finalizeListUpdate(tempActiveList, tempCollectionsList)
                        return@addOnSuccessListener
                    }

                    var processedCount = 0
                    val totalTenants = uniqueTenantIds.size

                    for (tenantId in uniqueTenantIds) {

                        val tenantLeases = relevantLeases.filter { it.getString("tenantID") == tenantId }

                        // FIX: Explicitly prioritize ONLY "Active" leases
                        val activeLease = tenantLeases.firstOrNull {
                            val s = it.getString("status") ?: ""
                            s.equals("Active", ignoreCase = true)
                        }

                        val displayLease = activeLease ?: tenantLeases.maxByOrNull { it.getLong("endDate") ?: 0L }

                        val leaseStatus = displayLease?.getString("status") ?: "Unknown"
                        val expiry = displayLease?.getLong("endDate") ?: 0L
                        val unitId = displayLease?.getString("unitID") ?: ""

                        val unitInfo = managerUnits[unitId]
                        val unitAndBuilding = "Unit ${unitInfo?.first} ${unitInfo?.second}"

                        // 3. Fetch Ledger Balance
                        db.collection("FINANCIAL_LEDGERS").document(tenantId).get().addOnSuccessListener { ledgerDoc ->
                            val balance = ledgerDoc.getLong("currentBalance")?.toInt() ?: 0
                            val ledgerStatus = ledgerDoc.getString("status") ?: "Settled"

                            // 4. Fetch User Profile
                            db.collection("USERS").document(tenantId).get().addOnSuccessListener { userDoc ->
                                val tenantName = userDoc.getString("fullName") ?: userDoc.getString("username") ?: "Resident"
                                val email = userDoc.getString("email") ?: ""
                                val phone = userDoc.getString("phoneNumber") ?: ""

                                // 5. Fetch Latest Verified Payment Slip
                                db.collection("PAYMENT_SLIPS").whereEqualTo("tenantID", tenantId).get().addOnSuccessListener { slipSnaps ->
                                    val latestSlip = slipSnaps.documents
                                        .filter { it.getString("status").equals("Verified", ignoreCase = true) }
                                        .maxByOrNull { it.getLong("transferDate") ?: 0L }

                                    val lastRemit = latestSlip?.getLong("transferDate") ?: 0L
                                    val payMethod = latestSlip?.getString("bankName") ?: "Bank Transfer"

                                    val item = RentRollUIState(
                                        unitID = unitId, tenantID = tenantId, buildingName = unitInfo?.second ?: "",
                                        unitAndBuilding = unitAndBuilding, tenantName = tenantName,
                                        tenantEmail = email, tenantPhone = phone,
                                        currentBalance = balance, ledgerStatus = ledgerStatus,
                                        leaseExpiry = expiry, lastRemittanceDate = lastRemit, paymentMethod = payMethod
                                    )

                                    // --- THE BULLETPROOF 2-WAY SPLIT LOGIC ---
                                    // FIX: Only strictly active leases go into the active rent roll tab!
                                    val isStrictlyActive = leaseStatus.equals("Active", ignoreCase = true)

                                    if (isStrictlyActive) {
                                        // TAB 1: ACTIVE LEASES ONLY
                                        tempActiveList.add(item)
                                    } else if (balance > 0) {
                                        // TAB 2: EXPIRED & DEBT
                                        // Tenant's lease is Expired/Terminated AND they still owe money.
                                        // Note: Pending_Leases will be skipped entirely here because their balance is forced to $0!
                                        tempCollectionsList.add(item)
                                    }

                                    processedCount++
                                    if (processedCount == totalTenants) finalizeListUpdate(tempActiveList, tempCollectionsList)

                                }.addOnFailureListener {
                                    processedCount++
                                    if (processedCount == totalTenants) finalizeListUpdate(tempActiveList, tempCollectionsList)
                                }
                            }.addOnFailureListener {
                                processedCount++
                                if (processedCount == totalTenants) finalizeListUpdate(tempActiveList, tempCollectionsList)
                            }
                        }.addOnFailureListener {
                            processedCount++
                            if (processedCount == totalTenants) finalizeListUpdate(tempActiveList, tempCollectionsList)
                        }
                    }
                }.addOnFailureListener {
                    finalizeListUpdate(tempActiveList, tempCollectionsList)
                }
            }
    }

    private fun finalizeListUpdate(newActive: List<RentRollUIState>, newCollections: List<RentRollUIState>) {
        activeRentRollList = newActive.sortedByDescending { it.currentBalance }.toMutableList()
        collectionsList = newCollections.sortedByDescending { it.currentBalance }.toMutableList()

        val currentQuery = findViewById<EditText>(R.id.etSearchBuilding).text.toString().trim()
        filterList(currentQuery)
    }

    private fun switchTab(showActive: Boolean) {
        isShowingActive = showActive

        val tvTabActive = findViewById<TextView>(R.id.tvTabActive)
        val tvTabCollections = findViewById<TextView>(R.id.tvTabCollections)

        if (showActive) {
            tvTabActive.setTextColor(Color.parseColor("#FFFFFF"))
            tvTabCollections.setTextColor(Color.parseColor("#64748B"))
        } else {
            tvTabActive.setTextColor(Color.parseColor("#64748B"))
            tvTabCollections.setTextColor(Color.parseColor("#FFFFFF"))
        }

        val currentQuery = findViewById<EditText>(R.id.etSearchBuilding).text.toString().trim()
        filterList(currentQuery)
    }

    private fun filterList(query: String) {
        val targetList = if (isShowingActive) activeRentRollList else collectionsList

        if (query.isEmpty()) {
            adapter.updateData(targetList)
        } else {
            val filtered = targetList.filter {
                it.buildingName.contains(query, ignoreCase = true) ||
                        it.tenantName.contains(query, ignoreCase = true) ||
                        it.unitAndBuilding.contains(query, ignoreCase = true)
            }
            adapter.updateData(filtered)
        }
    }

    private fun showContactDialog(email: String, phone: String, tenantContextInfo: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_contact_user, null)
        val contactDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        contactDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Contact Tenant"
        dialogView.findViewById<TextView>(R.id.tvDialogSubtitle).text = "How would you like to reach the resident for $tenantContextInfo?"

        val btnEmail = dialogView.findViewById<Button>(R.id.btnEmailContact)
        btnEmail.text = "✉️  Email Tenant"

        val btnCall = dialogView.findViewById<Button>(R.id.btnCallContact)
        btnCall.text = "📞  Call Tenant"

        btnEmail.setOnClickListener {
            contactDialog.dismiss()
            if (email.isEmpty()) {
                Toast.makeText(this, "No email registered for this tenant.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_SUBJECT, "Property Management Notice - $tenantContextInfo")
            }
            try {
                startActivity(emailIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "No email client installed.", Toast.LENGTH_SHORT).show()
            }
        }

        btnCall.setOnClickListener {
            contactDialog.dismiss()
            if (phone.isEmpty()) {
                Toast.makeText(this, "No phone number registered for this tenant.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val phoneIntent = Intent(Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:$phone")
            }
            try {
                startActivity(phoneIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "No phone dialer available.", Toast.LENGTH_SHORT).show()
            }
        }

        contactDialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        rentRollListener?.remove()
    }
}