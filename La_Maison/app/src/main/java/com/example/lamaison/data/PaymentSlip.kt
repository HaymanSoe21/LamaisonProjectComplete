package com.example.lamaison.data

data class PaymentSlip(
    val slipID: String = "",
    val tenantID: String = "",       // FK pointing to User
    val unitID: String = "",         // FK pointing to Unit (e.g., UNIT_A1B2C3D4)
    val category: String = "Monthly Rent Payment",
    val amountPaid: Int = 0,
    val slipPhotoURL: String = "",
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankAddress: String = "",
    val transferDate: Long = System.currentTimeMillis(),
    val notes: String = "",
    val status: String = "Pending", //Verified,Rejected
    val submissionDate: Long = System.currentTimeMillis(),
    val approvedByManagerID: String = "",
    val approvalDate: Long = 0L
)