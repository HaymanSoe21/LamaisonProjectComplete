package com.example.lamaison.data

data class RentRollUIState(
    val unitID: String,
    val tenantID: String,
    val buildingName: String,
    val unitAndBuilding: String,
    val tenantName: String,
    val tenantEmail: String, // NEW
    val tenantPhone: String, // NEW
    val currentBalance: Int,
    val ledgerStatus: String,
    val leaseExpiry: Long,
    val lastRemittanceDate: Long,
    val paymentMethod: String
)