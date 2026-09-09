package com.example.androidkiosk.data.repository

import com.example.androidkiosk.admin.AuthManager
import com.example.androidkiosk.domain.repository.AppSettingsRepository
import com.example.androidkiosk.model.AppSettings
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppSettingsRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
    private val authManager: AuthManager,
    private val branchPathProvider: BranchPathProvider
) : AppSettingsRepository {

    override fun observeAppSettings(): Flow<AppSettings> =
        authManager.authorizationState.flatMapLatest { authorization ->
            if (!authorization.isAuthorized) flowOf(AppSettings()) else observeAuthorizedSettings()
        }

    private fun observeAuthorizedSettings(): Flow<AppSettings> = callbackFlow {
        val settingsRef = database.getReference("${branchPathProvider.branchPath}/appSettings")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val backgroundImage = snapshot.child("backgroundImage")
                        .getValue(String::class.java)
                    val backgroundTheme = snapshot.child("backgroundTheme")
                        .getValue(String::class.java) ?: "Dark"

                    val settings = AppSettings(
                        backgroundImage = backgroundImage,
                        backgroundTheme = backgroundTheme
                    )
                    trySend(settings)
                    Timber.d("AppSettings updated: backgroundImage=${backgroundImage != null}, backgroundTheme=$backgroundTheme")
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing appSettings")
                    trySend(AppSettings())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (error.code == DatabaseError.PERMISSION_DENIED) authManager.reportAuthorizationDenied()
                Timber.e(error.toException(), "AppSettings listener cancelled")
                trySend(AppSettings())
            }
        }

        settingsRef.addValueEventListener(listener)

        awaitClose {
            settingsRef.removeEventListener(listener)
        }
    }
}
