package com.menumanager

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application root used to scope singletons.
 */
class MenuManagerApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val container: ServiceLocator by lazy {
        ServiceLocator(applicationContext, appScope)
    }

    override fun onCreate() {
        super.onCreate()
        // Inizializza Firebase
        FirebaseApp.initializeApp(this)
        
        // Abilita persistenza offline Firestore
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
        
        // Sign-in anonimo automatico
        appScope.launch {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("MenuManagerApp", "Sign-in anonimo riuscito: ${auth.currentUser?.uid}")
                    } else {
                        Log.e("MenuManagerApp", "Sign-in anonimo fallito", task.exception)
                    }
                }
            } else {
                Log.d("MenuManagerApp", "Utente già autenticato: ${auth.currentUser?.uid}")
            }
        }
    }
}
