package com.example.lamaison.data

data class PaymentSlipUIState(
    val slip: PaymentSlip,
    val unitCode: String,
    val tenantName: String
)