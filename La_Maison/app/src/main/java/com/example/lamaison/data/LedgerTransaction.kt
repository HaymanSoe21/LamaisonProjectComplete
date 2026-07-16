package com.example.lamaison.data

import java.io.Serializable

data class LedgerTransaction(
    val transactionId: String = "",
    val timestamp: Long = 0L,
    val dateString: String = "",
    val category: String = "Monthly Rent Payment", // Accounting Title
    val notes: String = "",                        // Personal Context
    val amount: Int = 0,
    val status: String = "Paid",//Pending
    val paymentMethod: String = "Bank Transfer",
    val bankName: String = "KBZ Bank",
    val bankAddress: String = "Mandalay Branch 78st"
) : Serializable