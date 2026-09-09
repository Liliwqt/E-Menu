package com.example.androidkiosk.data.repository

import android.content.Context
import com.example.androidkiosk.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the kiosk's provisioned company and branch into canonical RTDB paths.
 * Release builds must receive these values through provisioning or Gradle properties;
 * the kiosk never chooses an arbitrary branch at runtime.
 */
@Singleton
class BranchPathProvider @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val companyId: String
        get() = preferences.getString(KEY_COMPANY_ID, BuildConfig.DEFAULT_COMPANY_ID).orEmpty()

    val branchId: String
        get() = preferences.getString(KEY_BRANCH_ID, BuildConfig.DEFAULT_BRANCH_ID).orEmpty()

    val isConfigured: Boolean
        get() = companyId.isNotBlank() && branchId.isNotBlank()

    val branchPath: String
        get() {
            check(companyId.isNotBlank() && branchId.isNotBlank()) {
                "Kiosk branch is not configured. Provision companyId and branchId before ordering."
            }
            return "$companyId/branches/$branchId"
        }

    fun configure(companyId: String, branchId: String) {
        require(companyId.matches(COMPANY_ID_PATTERN)) { "Invalid company ID" }
        require(branchId.matches(BRANCH_ID_PATTERN)) { "Invalid branch ID" }
        preferences.edit()
            .putString(KEY_COMPANY_ID, companyId)
            .putString(KEY_BRANCH_ID, branchId)
            .apply()
    }

    private companion object {
        const val PREFERENCES = "kiosk_branch_configuration"
        const val KEY_COMPANY_ID = "companyId"
        const val KEY_BRANCH_ID = "branchId"
        val COMPANY_ID_PATTERN = Regex("company-[a-z0-9-]+")
        val BRANCH_ID_PATTERN = Regex("branch-[a-z0-9-]+")
    }
}
