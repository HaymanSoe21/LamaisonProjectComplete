package com.example.lamaison.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.MaintenanceTicket
import java.text.SimpleDateFormat
import java.util.*

class MaintenanceTicketAdapter(
    private var tickets: List<MaintenanceTicket>,
    private val onItemClick: (MaintenanceTicket) -> Unit
) : RecyclerView.Adapter<MaintenanceTicketAdapter.TicketViewHolder>() {

    class TicketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTicketId: TextView = view.findViewById(R.id.tvTicketId)
        val tvTicketTitle: TextView = view.findViewById(R.id.tvTicketTitle)
        val tvTicketDate: TextView = view.findViewById(R.id.tvTicketDate)
        val tvTicketStatus: TextView = view.findViewById(R.id.tvTicketStatus)
        val cardStatusPill: CardView = view.findViewById(R.id.cardStatusPill)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_maintenance_ticket, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        val ticket = tickets[position]

        holder.tvTicketId.text = if (ticket.ticketID.length > 12) ticket.ticketID.take(12) else ticket.ticketID
        holder.tvTicketTitle.text = ticket.issueTitle
        holder.tvTicketStatus.text = ticket.status

        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        holder.tvTicketDate.text = "Updated: ${sdf.format(Date(ticket.lastUpdatedDate))}"

        // Handle dynamic coloring matching the background designs
        when (ticket.status.lowercase()) {
            "pending" -> {
                holder.tvTicketStatus.setTextColor(android.graphics.Color.parseColor("#D97706"))
                holder.cardStatusPill.setCardBackgroundColor(android.graphics.Color.parseColor("#FEF3C7"))
            }
            "in-progress", "in progress" -> {
                holder.tvTicketStatus.setTextColor(android.graphics.Color.parseColor("#1D4ED8"))
                holder.cardStatusPill.setCardBackgroundColor(android.graphics.Color.parseColor("#EFF6FF"))
            }
            "resolved" -> {
                holder.tvTicketStatus.setTextColor(android.graphics.Color.parseColor("#047857"))
                holder.cardStatusPill.setCardBackgroundColor(android.graphics.Color.parseColor("#DCFCE7"))
            }
            else -> {
                holder.tvTicketStatus.setTextColor(android.graphics.Color.parseColor("#B91C1C"))
                holder.cardStatusPill.setCardBackgroundColor(android.graphics.Color.parseColor("#FEF2F2"))
            }
        }

        holder.itemView.setOnClickListener { onItemClick(ticket) }
    }

    override fun getItemCount() = tickets.size

    fun updateData(newTickets: List<MaintenanceTicket>) {
        this.tickets = newTickets
        notifyDataSetChanged()
    }
}