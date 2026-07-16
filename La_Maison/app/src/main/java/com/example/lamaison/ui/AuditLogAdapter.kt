package com.example.lamaison.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.AuditLog
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AuditLogAdapter(
    private var logs: List<AuditLog>
) : RecyclerView.Adapter<AuditLogAdapter.ViewHolder>() {

    // Resolves UIDs to Human Names asynchronously with caching
    private val db = FirebaseFirestore.getInstance()
    private val userNameCache = mutableMapOf<String, String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLogId: TextView = view.findViewById(R.id.tvAuditLogId)
        val tvTime: TextView = view.findViewById(R.id.tvAuditTime)
        val tvActionTitle: TextView = view.findViewById(R.id.tvActionTitle)
        val tvRole: TextView = view.findViewById(R.id.tvAuditRole)

        val llReceiptDetails: LinearLayout = view.findViewById(R.id.llReceiptDetails)
        val tvDetailedData: TextView = view.findViewById(R.id.tvDetailedData)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_audit_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]

        holder.tvLogId.text = if (log.logID.isNotEmpty()) log.logID else "LOG_SYSTEM"
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        holder.tvTime.text = sdf.format(Date(log.timestamp))

        // Clean Action Title Formatting
        holder.tvActionTitle.text = formatCleanTitle(log.actionType)

        holder.llReceiptDetails.visibility = View.VISIBLE
        holder.tvDetailedData.text = "Resolving internal ledger..."

        resolveUidsAndBuildReceipt(log, holder.tvDetailedData)

        apply3DCapsuleStyle(holder.tvRole, log.actorRole, log.status)
    }

    override fun getItemCount() = logs.size

    fun updateData(newLogs: List<AuditLog>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    private fun formatCleanTitle(rawAction: String): String {
        return when (rawAction.uppercase()) {
            "PUBLISHED_NEW_SUITE" -> "Action: PUBLISHED NEW SUITE"
            "AUTO_BIND_LEASE" -> "Action: LEASE AGREEMENT"
            else -> "Action: " + rawAction.replace("_", " ").uppercase()
        }
    }

    private fun resolveUidsAndBuildReceipt(log: AuditLog, tvDetails: TextView) {
        val payload = log.payload ?: emptyMap()
        val actorId = log.actorID
        val tenantId = payload["tenantID"] as? String ?: ""

        // Helper function to fetch or use cached name
        fun getName(uid: String, fallback: String, onResult: (String) -> Unit) {
            if (uid.isEmpty()) {
                onResult(fallback)
                return
            }
            if (userNameCache.containsKey(uid)) {
                onResult(userNameCache[uid]!!)
            } else {
                db.collection("USERS").document(uid).get().addOnSuccessListener { doc ->
                    val name = doc.getString("fullName") ?: fallback
                    userNameCache[uid] = name
                    onResult(name)
                }.addOnFailureListener {
                    onResult(fallback)
                }
            }
        }

        // Fetch Actor (Admin or Manager), then fetch Tenant, then build receipt
        getName(actorId, log.actorName.substringBefore(" (")) { actorName ->
            if (tenantId.isNotEmpty()) {
                getName(tenantId, "Unknown Tenant") { tenantName ->
                    val finalReceipt = formatReceiptHtml(log, actorName, tenantName)
                    tvDetails.text = Html.fromHtml(finalReceipt, Html.FROM_HTML_MODE_COMPACT)
                }
            } else {
                val finalReceipt = formatReceiptHtml(log, actorName, null)
                tvDetails.text = Html.fromHtml(finalReceipt, Html.FROM_HTML_MODE_COMPACT)
            }
        }
    }

    private fun formatReceiptHtml(
        log: AuditLog,
        actorName: String,
        tenantName: String?
    ): String {
        val sb = java.lang.StringBuilder()
        val actionType = log.actionType
        val payload = log.payload ?: emptyMap()

        // --- 1. ADMIN SYSTEM ACTIONS (IAM & Kill Switch) ---
        if (log.actorRole.equals("ADMIN", ignoreCase = true)) {
            sb.append("<b>Administrator:</b> ").append(actorName).append("<br>")

            // If the log contains a specific description, show it here instead of payload data
            if (log.description.isNotEmpty()) {
                sb.append("<b>Details:</b> ").append(log.description).append("<br>")
            }

            var result = sb.toString()
            if (result.endsWith("<br>")) result = result.dropLast(4)
            return result
        }

        // --- 2. MANAGER PROPERTY ACTIONS (Units & Leases) ---
        val isLease = actionType.equals("AUTO_BIND_LEASE", ignoreCase = true)

        sb.append("<b>Manager:</b> ").append(actorName).append("<br>")

        if (tenantName != null) {
            sb.append("<b>Tenant:</b> ").append(tenantName).append("<br>")
        }

        val unit = payload["unitCode"] ?: payload["unitNumber"] ?: "Unknown Suite"
        sb.append("<b>Unit Code:</b> ").append(unit).append("<br>")

        val rent = payload["baseRent"] ?: payload["rentFee"] ?: "0"
        sb.append("<b>Monthly Rent:</b> $").append(rent).append("<br>")

        if (isLease) {
            val deposit = payload["deposit"] ?: payload["securityDeposit"] ?: "N/A"
            sb.append("<b>Security Deposit:</b> $").append(deposit).append("<br>")

            val leaseStatus = payload["status"] ?: payload["leaseStatus"] ?: "Active"
            sb.append("<b>Status:</b> ").append(leaseStatus).append("<br>")

            val startDate = payload["startDate"] ?: payload["leaseStart"] ?: "N/A"
            sb.append("<b>Lease Start Date:</b> ").append(startDate).append("<br>")

            val expiry = payload["endDate"] ?: payload["expiryDate"] ?: "N/A"
            sb.append("<b>Lease Expiry Date:</b> ").append(expiry).append("<br>")
        } else {
            val status = payload["status"] ?: payload["occupancyStatus"] ?: "Vacant"
            sb.append("<b>Status:</b> ").append(status).append("<br>")
        }

        var result = sb.toString()
        if (result.endsWith("<br>")) {
            result = result.dropLast(4)
        }
        return result
    }

    private fun apply3DCapsuleStyle(badge: TextView, role: String, status: String) {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = 100f

        when {
            status.equals("FLAGGED_ANOMALY", ignoreCase = true) || status.equals("ANOMALY", ignoreCase = true) -> {
                badge.text = "• ANOMALY FLAGGED"
                badge.setTextColor(Color.parseColor("#B91C1C"))
                drawable.setColor(Color.parseColor("#FEF2F2"))
                drawable.setStroke(3, Color.parseColor("#F87171"))
            }
            status.equals("OVERRIDDEN", ignoreCase = true) -> {
                badge.text = "• EXECUTIVE OVERRIDE"
                badge.setTextColor(Color.parseColor("#B45309"))
                drawable.setColor(Color.parseColor("#FFFBEB"))
                drawable.setStroke(3, Color.parseColor("#FCD34D"))
            }
            role.equals("ADMIN", ignoreCase = true) -> {
                badge.text = "• SYSTEMS ADMIN"
                badge.setTextColor(Color.parseColor("#9333EA"))
                drawable.setColor(Color.parseColor("#F3E8FF"))
                drawable.setStroke(3, Color.parseColor("#C084FC"))
            }
            role.equals("MANAGER", ignoreCase = true) -> {
                badge.text = "• PROPERTY MANAGER"
                badge.setTextColor(Color.parseColor("#2563EB"))
                drawable.setColor(Color.parseColor("#DBEAFE"))
                drawable.setStroke(3, Color.parseColor("#60A5FA"))
            }
            else -> {
                badge.text = "• ${role.uppercase()}"
                badge.setTextColor(Color.parseColor("#047857"))
                drawable.setColor(Color.parseColor("#ECFDF5"))
                drawable.setStroke(3, Color.parseColor("#34D399"))
            }
        }
        badge.background = drawable
    }
}