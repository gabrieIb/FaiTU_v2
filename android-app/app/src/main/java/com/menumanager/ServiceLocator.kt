package com.menumanager

import android.content.Context
import com.menumanager.data.HouseholdRepository
import com.menumanager.data.MenuRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope

/**
 * Very small DI container so that composables/viewmodels can get repositories.
 */
class ServiceLocator(
    context: Context,
    scope: CoroutineScope
) {
    // Inizializzazione Firebase (idempotente)
    private val firebaseApp by lazy { FirebaseApp.initializeApp(context) ?: FirebaseApp.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    init {
        firebaseApp // Force init so Firestore is configured before repositories
    }

    // HouseholdRepository per gestire la famiglia
    val householdRepository: HouseholdRepository by lazy {
        HouseholdRepository(context.applicationContext)
    }

    val menuRepository: MenuRepository = MenuRepository(
        firestore = firestore,
        householdRepository = householdRepository,
        scope = scope
    )
}
