package com.example.lamaison.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("LaMaisonSession", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "cached_user_id"
        private const val KEY_FULL_NAME = "cached_full_name"
        private const val KEY_ROLE = "cached_role"

        // --- STRICT RELATIONAL UNIT PROFILE KEYS ---
        private const val KEY_TRUE_UNIT_ID = "cached_true_unit_id"
        private const val KEY_UNIT_CODE = "cached_unit_code"
        private const val KEY_UNIT_NUMBER = "cached_unit_number"
        private const val KEY_BUILDING_NAME = "cached_building_name"
        private const val KEY_ADDRESS = "cached_address"
        private const val KEY_LEASE_STATUS = "cached_lease_status" // --> NEW: Caches Lease Status!

        private const val KEY_BALANCE = "cached_balance"
        private const val KEY_DUE_DATE = "cached_due_date"
        private const val KEY_JURISDICTION = "cached_jurisdiction"

        // --- LEGACY BACKUP KEY ---
        private const val KEY_SUITE = "cached_suite"
    }

    // ==========================================
    // 1. RESTORED LEGACY METHODS
    // ==========================================

    fun isLoggedIn(): Boolean {
        return prefs.getString(KEY_USER_ID, null) != null
    }

    fun getUserRole(): String? {
        return prefs.getString(KEY_ROLE, null)
    }

    fun saveLoginSession(userId: String, role: String) {
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_ROLE, role)
            apply()
        }
    }

    fun getRole(): String? = prefs.getString(KEY_ROLE, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun saveTenantDetails(name: String, suite: String, balance: String, dueDate: String) {
        prefs.edit().apply {
            putString(KEY_FULL_NAME, name)
            putString(KEY_SUITE, suite)
            putString(KEY_BALANCE, balance)
            putString(KEY_DUE_DATE, dueDate)
            apply()
        }
    }

    fun getCachedSuite(): String? = prefs.getString(KEY_UNIT_NUMBER, prefs.getString(KEY_SUITE, null))


    // ==========================================
    // 2. NEW UPGRADED RELATIONAL METHODS
    // ==========================================

    fun saveUserSession(userId: String, fullName: String, role: String) {
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_FULL_NAME, fullName)
            putString(KEY_ROLE, role)
            apply()
        }
    }

    fun saveTenantUnitProfile(unitId: String, unitCode: String, unitNumber: String, buildingName: String, address: String, status: String = "Active") {
        prefs.edit().apply {
            putString(KEY_TRUE_UNIT_ID, unitId)
            putString(KEY_UNIT_CODE, unitCode)
            putString(KEY_UNIT_NUMBER, unitNumber)
            putString(KEY_BUILDING_NAME, buildingName)
            putString(KEY_ADDRESS, address)
            putString(KEY_LEASE_STATUS, status)
            apply()
        }
    }

    fun clearTenantUnitProfile() {
        prefs.edit().apply {
            remove(KEY_TRUE_UNIT_ID)
            remove(KEY_UNIT_CODE)
            remove(KEY_UNIT_NUMBER)
            remove(KEY_BUILDING_NAME)
            remove(KEY_ADDRESS)
            remove(KEY_LEASE_STATUS)
            remove(KEY_SUITE)
            apply()
        }
    }

    fun saveTenantFinancials(balance: String, dueDate: String) {
        prefs.edit().apply {
            putString(KEY_BALANCE, balance)
            putString(KEY_DUE_DATE, dueDate)
            apply()
        }
    }

    fun saveManagerDetails(fullName: String, jurisdiction: String) {
        prefs.edit().apply {
            putString(KEY_FULL_NAME, fullName)
            putString(KEY_JURISDICTION, jurisdiction)
            apply()
        }
    }

    // --- RELATIONAL GETTERS ---
    fun getCachedTrueUnitId(): String? = prefs.getString(KEY_TRUE_UNIT_ID, null)
    fun getCachedUnitCode(): String? = prefs.getString(KEY_UNIT_CODE, null)
    fun getCachedName(): String? = prefs.getString(KEY_FULL_NAME, null)
    fun getCachedBalance(): String? = prefs.getString(KEY_BALANCE, null)
    fun getCachedDueDate(): String? = prefs.getString(KEY_DUE_DATE, null)
    fun getCachedJurisdiction(): String? = prefs.getString(KEY_JURISDICTION, null)
    fun getCachedLeaseStatus(): String? = prefs.getString(KEY_LEASE_STATUS, null)

    fun getCachedFullSuiteDisplay(): String {
        val number = prefs.getString(KEY_UNIT_NUMBER, prefs.getString(KEY_SUITE, null))
        val building = prefs.getString(KEY_BUILDING_NAME, null)
        val address = prefs.getString(KEY_ADDRESS, null)

        return if (number != null && building != null && address != null) {
            "Unit $number, $building\n$address"
        } else if (number != null) {
            "Unit $number"
        } else {
            "Not yet Assigned"
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}