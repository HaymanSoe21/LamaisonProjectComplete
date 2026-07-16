package com.example.lamaison.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.lamaison.R
import com.example.lamaison.data.MaintenanceTicket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagerMaintenanceAdapter(
    private var ticketList: List<MaintenanceTicket>,
    var isLogMode: Boolean,
    private val onTicketClick: (MaintenanceTicket) -> Unit
) : RecyclerView.Adapter<ManagerMaintenanceAdapter.ManagerViewHolder>() {

    class ManagerViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val tvTicketId: TextView = view.findViewById(R.id.tvTicketId)
        val tvTicketTitle: TextView = view.findViewById(R.id.tvTicketTitle)
        val tvTicketDate: TextView = view.findViewById(R.id.tvTicketDate)
        val tvTicketStatus: TextView = view.findViewById(R.id.tvTicketStatus)
        val cardStatusPill: CardView = view.findViewById(R.id.cardStatusPill)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManagerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_maintenance_ticket, parent, false)
        return ManagerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ManagerViewHolder, position: Int) {
        val ticket = ticketList[position]

        holder.tvTicketId.text = ticket.ticketID
        holder.tvTicketTitle.text = ticket.issueTitle

        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)


        if (isLogMode) {
            holder.tvTicketDate.text = "Updated: ${sdf.format(Date(ticket.lastUpdatedDate))}"
        } else {
            holder.tvTicketDate.text = "Submitted: ${sdf.format(Date(ticket.submissionDate))}"
        }


        if (ticket.status.equals("Pending", ignoreCase = true)) {
            holder.cardStatusPill.setCardBackgroundColor(Color.parseColor("#FEE2E2"))
            holder.tvTicketStatus.setTextColor(Color.parseColor("#DC2626"))
            holder.tvTicketStatus.text = "UNRESOLVED"
        } else if (ticket.status.equals("In-Progress", ignoreCase = true)) {
            holder.cardStatusPill.setCardBackgroundColor(Color.parseColor("#DBEAFE"))
            holder.tvTicketStatus.setTextColor(Color.parseColor("#1D4ED8"))
            holder.tvTicketStatus.text = "IN PROGRESS"
        } else {
            holder.cardStatusPill.setCardBackgroundColor(Color.parseColor("#DCFCE7"))
            holder.tvTicketStatus.setTextColor(Color.parseColor("#15803D"))
            holder.tvTicketStatus.text = "COMPLETED"
        }

        holder.itemView.setOnClickListener { onTicketClick(ticket) }
    }

    override fun getItemCount(): Int = ticketList.size

    fun updateData(newList: List<MaintenanceTicket>) {
        this.ticketList = newList
        notifyDataSetChanged()
    }
}