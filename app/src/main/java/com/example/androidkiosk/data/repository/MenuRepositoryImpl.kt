package com.example.androidkiosk.data.repository

import com.example.androidkiosk.admin.AuthManager
import com.example.androidkiosk.data.local.dao.MenuItemDao
import com.example.androidkiosk.data.local.entity.MenuItemEntity
import com.example.androidkiosk.domain.repository.MenuRepository
import com.example.androidkiosk.model.CategoryWithItems
import com.example.androidkiosk.model.MenuItem
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MenuRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase,
    private val menuItemDao: MenuItemDao,
    private val externalScope: CoroutineScope,
    private val authManager: AuthManager,
    private val branchPathProvider: BranchPathProvider
) : MenuRepository {

    private val firebaseSyncStarted = AtomicBoolean(false)
    private var categoriesRef: com.google.firebase.database.DatabaseReference? = null
    private var categoriesListener: ValueEventListener? = null
    override fun observeCategories(): Flow<List<CategoryWithItems>> {
        // Only attach Firebase listener once auth is confirmed
        ensureFirebaseSync()

        return menuItemDao.getAllMenuItems().map { entities ->
            entities.groupBy { it.categoryName }
                .map { (categoryName, items) ->
                    CategoryWithItems(
                        categoryName = categoryName,
                        items = items.map(FirebaseMenuMapper::toDomain)
                    )
                }
        }
    }

    override fun observeBestSellers(): Flow<List<MenuItem>> {
        return menuItemDao.getBestSellers().map { entities ->
            entities.map(FirebaseMenuMapper::toDomain)
        }
    }

    override fun observeInventoryStock(): Flow<Map<String, Map<String, Int>>> =
        authManager.authorizationState.flatMapLatest { authorization ->
            if (!authorization.isAuthorized) flowOf(emptyMap()) else observeAuthorizedInventory()
        }

    private fun observeAuthorizedInventory(): Flow<Map<String, Map<String, Int>>> = callbackFlow {
        val inventoryRef = database.getReference("${branchPathProvider.branchPath}/inventory")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(FirebaseMenuMapper.parseInventory(snapshot.value))
            }

            override fun onCancelled(error: DatabaseError) {
                if (error.code == DatabaseError.PERMISSION_DENIED) authManager.reportAuthorizationDenied()
                close(error.toException())
            }
        }
        inventoryRef.addValueEventListener(listener)
        awaitClose { inventoryRef.removeEventListener(listener) }
    }

    override suspend fun refresh() {
        check(authManager.authorizationState.value.isAuthorized) { "Kiosk UID is not registered" }
        val snapshot = database.getReference("${branchPathProvider.branchPath}/categories").get().await()
        replaceCacheFrom(snapshot)
        ensureFirebaseSync()
    }

    /** Waits for Firebase anonymous auth before attaching the ValueEventListener. */
    private fun ensureFirebaseSync() {
        if (!firebaseSyncStarted.compareAndSet(false, true)) return

        externalScope.launch {
            authManager.authorizationState
                .map { it.isAuthorized }
                .distinctUntilChanged()
                .collect { authorized ->
                    if (authorized) attachFirebaseListener() else detachFirebaseListener()
                }
        }
    }

    @Synchronized
    private fun detachFirebaseListener() {
        val reference = categoriesRef
        val listener = categoriesListener
        if (reference != null && listener != null) {
            reference.removeEventListener(listener)
        }
        categoriesRef = null
        categoriesListener = null
    }

    @Synchronized
    private fun attachFirebaseListener() {
        if (categoriesListener != null) return
        val reference = database.getReference("${branchPathProvider.branchPath}/categories")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                externalScope.launch {
                    try {
                        replaceCacheFrom(snapshot)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to sync menu items to Room")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (error.code == DatabaseError.PERMISSION_DENIED) authManager.reportAuthorizationDenied()
                Timber.e(error.toException(), "Firebase listener cancelled")
            }
        }
        categoriesRef = reference
        categoriesListener = listener
        reference.addValueEventListener(listener)
    }

    private suspend fun replaceCacheFrom(snapshot: DataSnapshot) {
        val entities = mutableListOf<MenuItemEntity>()
        for (categorySnapshot in snapshot.children) {
            val categoryName = categorySnapshot.key ?: continue
            for (itemSnapshot in categorySnapshot.children) {
                try {
                    val item = itemSnapshot.getValue(MenuItem::class.java) ?: continue
                    val entity = FirebaseMenuMapper.toEntity(
                        item = item,
                        categoryName = categoryName,
                        snapshotKey = itemSnapshot.key.orEmpty(),
                        sizes = FirebaseMenuMapper.parseSizes(itemSnapshot.child("sizes").value)
                    ) ?: continue
                    entities += entity
                } catch (e: Exception) {
                    Timber.w(e, "Skipping malformed item in category: %s", categoryName)
                }
            }
        }
        menuItemDao.replaceAll(entities)
        Timber.d("Synced %d menu items to local cache", entities.size)
    }

}
