package com.menumanager.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

private const val HOUSEHOLDS_COLLECTION = "households"
private const val INVITE_CODE_LENGTH = 6

private val Context.householdDataStore by preferencesDataStore(name = "household_prefs")

class HouseholdRepository( 
	context: Context,
	private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
	private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
	private val dataStore = context.applicationContext.householdDataStore

	private val householdIdKey = stringPreferencesKey("household_id")
	private val inviteCodeKey = stringPreferencesKey("invite_code")

	val householdIdFlow: Flow<String?> = dataStore.data
		.map { prefs -> prefs[householdIdKey] }
		.distinctUntilChanged()

	suspend fun getHouseholdId(): String? = dataStore.data.first()[householdIdKey]

	suspend fun getInviteCode(): String? = dataStore.data.first()[inviteCodeKey]

	suspend fun createHousehold(): Pair<String, String> {
		val uid = auth.currentUser?.uid ?: throw IllegalStateException("Utente non autenticato")
		val householdId = UUID.randomUUID().toString()
		val inviteCode = generateInviteCode()
		val householdRef = firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId)

		val payload = mapOf(
			"inviteCode" to inviteCode,
			"createdAt" to FieldValue.serverTimestamp(),
			"members" to listOf(uid)
		)

		householdRef.set(payload).await()
		storeHouseholdLocally(householdId, inviteCode)
		return householdId to inviteCode
	}

	suspend fun joinHousehold(inviteCode: String): String {
		val uid = auth.currentUser?.uid ?: throw IllegalStateException("Utente non autenticato")
		val trimmed = inviteCode.trim().uppercase(Locale.ROOT)

		val querySnapshot = firestore.collection(HOUSEHOLDS_COLLECTION)
			.whereEqualTo("inviteCode", trimmed)
			.limit(1)
			.get()
			.await()

		val document = querySnapshot.documents.firstOrNull()
			?: throw IllegalArgumentException("Codice invito non trovato")

		document.reference.update("members", FieldValue.arrayUnion(uid)).await()

		val invite = document.getString("inviteCode") ?: trimmed
		val householdId = document.id
		storeHouseholdLocally(householdId, invite)
		return householdId
	}

	suspend fun overrideHouseholdId(householdId: String): String? {
		val uid = auth.currentUser?.uid ?: throw IllegalStateException("Utente non autenticato")

		val snapshot = firestore.collection(HOUSEHOLDS_COLLECTION)
			.document(householdId)
			.get()
			.await()

		if (!snapshot.exists()) {
			throw IllegalArgumentException("Famiglia non trovata")
		}

		snapshot.reference.update("members", FieldValue.arrayUnion(uid)).await()
		val inviteCode = snapshot.getString("inviteCode")
		storeHouseholdLocally(householdId, inviteCode)
		return inviteCode
	}

	private suspend fun storeHouseholdLocally(householdId: String, inviteCode: String?) {
		dataStore.edit { prefs ->
			prefs[householdIdKey] = householdId
			if (inviteCode.isNullOrBlank()) {
				prefs.remove(inviteCodeKey)
			} else {
				prefs[inviteCodeKey] = inviteCode
			}
		}
	}

	private fun generateInviteCode(): String {
		val alphabet = (('A'..'Z') + ('0'..'9')).toTypedArray()
		return buildString(INVITE_CODE_LENGTH) {
			repeat(INVITE_CODE_LENGTH) {
				append(alphabet[Random.nextInt(alphabet.size)])
			}
		}
	}
}
