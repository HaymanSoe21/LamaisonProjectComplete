package com.example.lamaison.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.PaymentSlipUIState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditSlipAdapter(
    private val slipList: List<PaymentSlipUIState>,
    private val onItemClick: (PaymentSlipUIState) -> Unit
) : RecyclerView.Adapter<AuditSlipAdapter.AuditViewHolder>() {

    class AuditViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAuditNameSuite: TextView = view.findViewById(R.id.tvAuditNameSuite)
        val tvAuditAmount: TextView = view.findViewById(R.id.tvAuditAmount)
        val tvAuditBank: TextView = view.findViewById(R.id.tvAuditBank)
        val tvAuditDate: TextView = view.findViewById(R.id.tvAuditDate)
        val tvAuditRef: TextView = view.findViewById(R.id.tvAuditRef)
        val tvAuditStatus: TextView = view.findViewById(R.id.tvAuditStatus)
        val container: View = view.findViewById(R.id.itemContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AuditViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_audit_slip, parent, false)
        return AuditViewHolder(view)
    }

    override fun onBindViewHolder(holder: AuditViewHolder, position: Int) {
        val state = slipList[position]
        val slip = state.slip

        // Instantly bind the pre-fetched data
        holder.tvAuditNameSuite.text = "${state.tenantName} (${state.unitCode})"

        val format = NumberFormat.getCurrencyInstance(Locale.US)
        holder.tvAuditAmount.text = format.format(slip.amountPaid)

        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        holder.tvAuditBank.text = "Bank Name\n${slip.bankName}"
        holder.tvAuditDate.text = "Transfer Date\n${sdf.format(Date(slip.transferDate))}"
        holder.tvAuditRef.text = "Ref: ${slip.slipID.takeLast(8).uppercase()}"

        holder.tvAuditStatus.text = slip.status.uppercase()
        if (slip.status == "Verified") {
            holder.tvAuditStatus.setTextColor(Color.parseColor("#16A34A"))
            holder.tvAuditStatus.setBackgroundColor(Color.parseColor("#DCFCE7"))
        } else {
            holder.tvAuditStatus.setTextColor(Color.parseColor("#DC2626"))
            holder.tvAuditStatus.setBackgroundColor(Color.parseColor("#FEE2E2"))
        }

        holder.container.setOnClickListener { onItemClick(state) }
    }

    override fun getItemCount() = slipList.size
}