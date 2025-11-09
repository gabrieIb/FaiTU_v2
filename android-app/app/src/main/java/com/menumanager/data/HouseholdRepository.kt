package com.menumanager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

private val Context.householdDataStore: DataStore<Preferences> by preferencesDataStore(name = "household")

/**
 * Gestisce la "famiglia" (household) condivisa tra i due dispositivi.
 * Schema Firestore:
 * - households/{householdId}: { inviteCode, createdAt, members: [uid1, uid2] }
 * - households/{householdId}/proposals/{proposalId}
 * - households/{householdId}/ingredients/{ingredientId}
 * - households/{householdId}/shopping/{shoppingId}
 */
class HouseholdRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val householdIdKey = stringPreferencesKey("household_id")

    val householdIdFlow: Flow<String?> = context.householdDataStore.data
        .map { prefs -> prefs[householdIdKey] }

    suspend fun getHouseholdId(): String? = householdIdFlow.firstOrNull()

    /**
     * Crea una nuova famiglia e restituisce householdId e inviteCode.
     */
    suspend fun createHousehold(): Pair<String, String> {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Utente non autenticato")
        val householdId = UUID.randomUUID().toString()
        val inviteCode = generateInviteCode()
        
        val household = mapOf(
            "inviteCode" to inviteCode,
            "createdAt" to FieldValue.serverTimestamp(),
            "members" to listOf(uid)
        )
        
        firestore.collection("households").document(householdId).set(household).await()
        saveHouseholdId(householdId)
        
        return Pair(householdId, inviteCode)
    }

    /**
     * Unisciti a una famiglia esistente tramite codice invito.
     * Cerca l'household con quel codice e aggiunge l'uid corrente ai members.
     */
    suspend fun joinHousehold(inviteCode: String): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Utente non autenticato")
        
        // Cerca household con questo inviteCode
        val querySnapshot = firestore.collection("households")
            .whereEqualTo("inviteCode", inviteCode)
            .limit(1)
            .get()
            .await()
        
        if (querySnapshot.isEmpty) {
            throw IllegalArgumentException("Codice invito non valido")
        }
        
        val householdDoc = querySnapshot.documents.first()
        val householdId = householdDoc.id
        
        // Aggiungi uid ai members (se non c'è già)
        firestore.collection("households").document(householdId)
            .update("members", FieldValue.arrayUnion(uid))
            .await()
        
        saveHouseholdId(householdId)
        return householdId
    }

    /**
     * Recupera il codice invito per la famiglia corrente (per mostrarlo all'utente).
     */
    suspend fun getInviteCode(): String? {
        val householdId = getHouseholdId() ?: return null
        val doc = firestore.collection("households").document(householdId).get().await()
        return doc.getString("inviteCode")
    }

    suspend fun overrideHouseholdId(householdId: String): String? {
        val trimmed = householdId.trim()
        require(trimmed.isNotEmpty()) { "ID famiglia non valido" }
        saveHouseholdId(trimmed)
        return getInviteCode()
    }

    private suspend fun saveHouseholdId(householdId: String) {
        context.householdDataStore.edit { prefs ->
            prefs[householdIdKey] = householdId
        }
    }

    private fun generateInviteCode(): String {
        // Genera un codice di 6 cifre/lettere maiuscole (es. "A3F9K2")
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // escluse I, O, 0, 1 per evitare confusione
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
