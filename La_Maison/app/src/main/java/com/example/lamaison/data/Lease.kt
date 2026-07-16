package com.example.lamaison.data

data class Lease(
    val leaseID: String = "",
    val unitID: String = "",         // FK pointing to UNITS (e.g., "DIA-4B")
    val tenantID: String = "",       // FK pointing to USERS (Tenant UID)
    val managerID: String = "",      // FK pointing to USERS (Manager UID)

    val monthlyRent: Int = 0,        // Locked-in rent rate for this specific contract
    val securityDeposit: Int = 0,    // Deposit paid upfront

    val startDate: Long = 0L,        // Timestamp of move-in
    val endDate: Long = 0L,          // Timestamp when contract

    val contractDocURL: String = "", // ImgBB link to signed PDF/photo

    // Status can be: "Active", "Expired"
    val status: String = "Active",

    val createdAt: Long = System.currentTimeMillis()
)