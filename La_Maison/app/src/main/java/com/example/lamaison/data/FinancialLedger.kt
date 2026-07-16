package com.example.lamaison.data

data class FinancialLedger(
    val ledgerID: String = "",
    val tenantID: String = "",       // FK pointing to User
    val unitID: String = "",         // FK pointing to Unit (e.g., UNIT_A1B2C3D4)
    val unitCode: String = "",       // Reference code for display (e.g., DIA-6B)
    val managerID: String = "",      // FK pointing to Manager

    val currentBalance: Int = 0,
    val securityDepositHeld: Int = 0,
    val nextDueDate: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val status: String = "Settled"   // "Settled", "OnTime", or "Overdue" or "InDebt"
)