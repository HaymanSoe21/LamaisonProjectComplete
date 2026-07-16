package com.example.lamaison.data

data class MaintenanceTicket(
    val ticketID: String = "",
    val tenantID: String = "", // FK pointing to User
    val unitID: String = "", // FK pointing to Unit
    val issueTitle: String = "",
    val issueCategory: String = "",
    val description: String = "",
    val maintenancePhotoURL: String = "",
    val status: String = "Pending", // Pending / In-Progress / Resolved
    val submissionDate: Long = System.currentTimeMillis(),
    val lastUpdatedDate: Long = System.currentTimeMillis()
)