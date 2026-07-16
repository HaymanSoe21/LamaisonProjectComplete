package com.example.lamaison.data

data class AuditLog(
    val logID: String = "",              // Unique ID (e.g., "LOG_8841")
    val timestamp: Long = System.currentTimeMillis(), // Exact time of action for chronological sorting

    // --- Initiating Actor Details ---
    val actorID: String = "",            // Firebase Auth UID (e.g., "MGR_8841" or "ADM_001")
    val actorName: String = "",          // Display Name (e.g., "Manager Aung San" or "System Automator")
    val actorRole: String = "",          // Role category: "ADMIN", "MANAGER", "TENANT", or "SYSTEM"

    // --- Action Classification ---
    val category: String = "",           // Broad grouping: "DATABASE", "LEASE", "FINANCE", or "SECURITY"
    val actionType: String = "",         // Specific action tag (e.g., "AUTO_BIND_LEASE", "CREATED_UNIT", "OVERRIDE_RENT")
    val description: String = "",        // Human-readable summary of what happened

    // --- The Digital Footprint (Cryptographic / Data Snapshot) ---
    val payload: Map<String, Any> = emptyMap(), // Key-value map storing the exact data parameters changed
    val status: String = "VERIFIED"      // Immutability verification tag (e.g., "VERIFIED", "FLAGGED_ANOMALY")
)