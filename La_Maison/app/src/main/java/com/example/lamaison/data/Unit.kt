package com.example.lamaison.data

data class Unit(
    val unitID: String = "",
    val unitCode: String = "",
    val unitNumber: String = "",
    val buildingName: String = "",
    val propertyAddress: String = "",
    val baseRent: Int = 0,
    val occupancyStatus: String = "Vacant", // Vacant / Occupied / Under Maintenance
    val currentTenantID: String = "", // FK pointing to User (Tenant)
    val assignedManagerID: String = "",// FK pointing to User (Manager)
)