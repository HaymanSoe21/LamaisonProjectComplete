package com.example.lamaison.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.PaymentSlip
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LedgerHistoryAdapter(private val slipList: List<PaymentSlip>) : RecyclerView.Adapter<LedgerHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Bind directly to the UI elements in item_ledger_history.xml
        val tvDate: TextView = view.findViewById(R.id.tvItemDate)
        val tvCategory: TextView = view.findViewById(R.id.tvItemCategory)
        val tvBankRef: TextView = view.findViewById(R.id.tvItemBankRef)
        val tvAmount: TextView = view.findViewById(R.id.tvItemAmount)
        val tvStatus: TextView = view.findViewById(R.id.tvItemStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ledger_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val slip = slipList[position]
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        val dateSdf = SimpleDateFormat("MMM dd\nyyyy", Locale.US)

        holder.tvDate.text = dateSdf.format(Date(slip.transferDate)).uppercase()
        holder.tvCategory.text = slip.category

        // Crash Prevention: Ensure slipID has at least 4 characters before taking the last 4
        val safeSlipRef = if (slip.slipID.length >= 4) slip.slipID.takeLast(4).uppercase() else slip.slipID.uppercase()
        holder.tvBankRef.text = "${slip.bankName} • Ref: #$safeSlipRef"

        holder.tvAmount.text = format.format(slip.amountPaid)
        holder.tvStatus.text = slip.status.uppercase()

        if (slip.status.equals("Verified", ignoreCase = true)) {
            holder.tvStatus.setTextColor(Color.parseColor("#16A34A"))
            holder.tvStatus.setBackgroundColor(Color.parseColor("#DCFCE7"))
        } else {
            holder.tvStatus.setTextColor(Color.parseColor("#DC2626"))
            holder.tvStatus.setBackgroundColor(Color.parseColor("#FEE2E2"))
        }
    }

    override fun getItemCount() = slipList.size
}