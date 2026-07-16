package com.example.lamaison.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.RentRollUIState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RentRollAdapter(
    private var rentRollList: List<RentRollUIState>,
    private val onLedgerClick: (String, String, String, String) -> Unit,
    private val onContactClick: (String, String, String) -> Unit
) : RecyclerView.Adapter<RentRollAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUnitBuilding: TextView = view.findViewById(R.id.tvUnitBuilding)
        val tvStatusBadge: TextView = view.findViewById(R.id.tvStatusBadge)
        val tvTenantName: TextView = view.findViewById(R.id.tvTenantName)
        val tvBalance: TextView = view.findViewById(R.id.tvBalance)
        val tvExpiry: TextView = view.findViewById(R.id.tvExpiry)
        val tvLastRemit: TextView = view.findViewById(R.id.tvLastRemit)
        val tvPayMethod: TextView = view.findViewById(R.id.tvPayMethod)
        val btnLedgerHistory: Button = view.findViewById(R.id.btnLedgerHistory)
        val btnContact: Button = view.findViewById(R.id.btnContact)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rent_roll, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = rentRollList[position]

        holder.tvUnitBuilding.text = item.unitAndBuilding
        holder.tvTenantName.text = item.tenantName

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
        holder.tvBalance.text = currencyFormat.format(item.currentBalance)

        val dateSdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        holder.tvExpiry.text = if (item.leaseExpiry > 0L) dateSdf.format(Date(item.leaseExpiry)) else "N/A"
        holder.tvLastRemit.text = if (item.lastRemittanceDate > 0L) dateSdf.format(Date(item.lastRemittanceDate)) else "No records"

        holder.tvPayMethod.text = if (item.paymentMethod.isNotEmpty()) item.paymentMethod else "N/A"

        holder.tvStatusBadge.text = item.ledgerStatus.uppercase()

        // Exclusively checks for your 4 specific database ledger statuses
        when (item.ledgerStatus.lowercase()) {
            "settled", "ontime" -> {
                holder.tvStatusBadge.setTextColor(Color.parseColor("#16A34A"))
                holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#DCFCE7"))
            }
            "overdue", "indebt" -> {
                holder.tvStatusBadge.setTextColor(Color.parseColor("#DC2626"))
                holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#FEE2E2"))
            }
            else -> {
                // Fallback blue color just in case of a database typo or missing data
                holder.tvStatusBadge.setTextColor(Color.parseColor("#2563EB"))
                holder.tvStatusBadge.setBackgroundColor(Color.parseColor("#DBEAFE"))
            }
        }

        holder.btnLedgerHistory.setOnClickListener {
            onLedgerClick(item.unitID, item.tenantID, item.unitAndBuilding, item.tenantName)
        }

        holder.btnContact.setOnClickListener {
            val tenantContextInfo = "${item.tenantName} - ${item.unitAndBuilding}"
            onContactClick(item.tenantEmail, item.tenantPhone, tenantContextInfo)
        }
    }

    override fun getItemCount() = rentRollList.size

    fun updateData(newList: List<RentRollUIState>) {
        rentRollList = newList
        notifyDataSetChanged()
    }
}