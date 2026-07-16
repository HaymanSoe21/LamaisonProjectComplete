package com.example.lamaison.data

data class User(
    val userID: String = "",
    val role: String = "",
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val nationality: String = "",

    // NEW: Explicitly states what kind of ID this is
    val identityDocumentType: String = "",

    val identityID: String = "",
    val identityProofURLs: List<String> = emptyList(),
    val profilePicURL: String = "",
    val createdAt: Long = System.currentTimeMillis()
)