package com.example.lamaison.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.LedgerTransaction

class LedgerTransactionAdapter(
    private var transactions: List<LedgerTransaction>,
    private val onItemClicked: (LedgerTransaction) -> Unit
) : RecyclerView.Adapter<LedgerTransactionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCategory: TextView = view.findViewById(R.id.tvTxnCategory)
        val tvNotes: TextView = view.findViewById(R.id.tvTxnNotes)
        val tvDate: TextView = view.findViewById(R.id.tvTxnDate)
        val tvAmount: TextView = view.findViewById(R.id.tvTxnAmount)
        val tvStatus: TextView = view.findViewById(R.id.tvTxnStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ledger_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val txn = transactions[position]

        holder.tvCategory.text = txn.category

        if (txn.notes.isNotBlank()) {
            holder.tvNotes.visibility = View.VISIBLE
            holder.tvNotes.text = "\"${txn.notes}\""
        } else {
            holder.tvNotes.visibility = View.GONE
        }

        holder.tvDate.text = txn.dateString
        holder.tvAmount.text = String.format("-$%,d.00", txn.amount)
        holder.tvStatus.text = txn.status

        if (txn.status.equals("Paid", ignoreCase = true) || txn.status.equals("Settled", ignoreCase = true)) {
            holder.tvStatus.setTextColor(Color.parseColor("#047857"))
            holder.tvStatus.setBackgroundColor(Color.parseColor("#D1FAE5"))
        } else if (txn.status.equals("Overdue", ignoreCase = true)) {
            holder.tvStatus.setTextColor(Color.parseColor("#DC2626"))
            holder.tvStatus.setBackgroundColor(Color.parseColor("#FEE2E2"))
        } else {
            holder.tvStatus.setTextColor(Color.parseColor("#D97706"))
            holder.tvStatus.setBackgroundColor(Color.parseColor("#FEF3C7"))
        }

        holder.itemView.setOnClickListener { onItemClicked(txn) }
    }

    override fun getItemCount() = transactions.size

    fun updateData(newList: List<LedgerTransaction>) {
        transactions = newList
        notifyDataSetChanged()
    }
}