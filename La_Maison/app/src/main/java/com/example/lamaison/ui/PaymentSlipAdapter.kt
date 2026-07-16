package com.example.lamaison.ui

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

class PaymentSlipAdapter(
    private val slipList: List<PaymentSlipUIState>,
    private val onItemClick: (PaymentSlipUIState) -> Unit
) : RecyclerView.Adapter<PaymentSlipAdapter.SlipViewHolder>() {

    class SlipViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSuiteAndName: TextView = view.findViewById(R.id.tvSuiteAndName)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val container: View = view.findViewById(R.id.itemContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payment_slip, parent, false)
        return SlipViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlipViewHolder, position: Int) {
        val state = slipList[position]
        val slip = state.slip

        // Instantly bind the pre-fetched data
        holder.tvSuiteAndName.text = "${state.unitCode} • ${state.tenantName}"

        val format = NumberFormat.getCurrencyInstance(Locale.US)
        holder.tvAmount.text = format.format(slip.amountPaid)

        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        holder.tvDate.text = "Submitted: ${sdf.format(Date(slip.submissionDate))}"

        holder.container.setOnClickListener {
            onItemClick(state)
        }
    }

    override fun getItemCount() = slipList.size
}