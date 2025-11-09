package com.menumanager.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Implementazione Firebase Firestore con schema household.
 * Struttura collezioni:
 *  - households/{householdId}/proposals/{proposalId}
 *  - households/{householdId}/ingredients/{ingredientId}
 *  - households/{householdId}/shopping/{shoppingId}
 * householdId viene passato al costruttore (recuperato da HouseholdRepository).
 */
class FirestoreMenuDataSource(
    private val db: FirebaseFirestore,
    private val householdId: String
) : MenuRemoteDataSource {

    private val householdDoc get() = db.collection("households").document(householdId)
    private val proposalsCol get() = householdDoc.collection("proposals")
    private val ingredientsCol get() = householdDoc.collection("ingredients")
    private val shoppingCol get() = householdDoc.collection("shopping")

    override fun streamProposals(): Flow<List<RemoteProposal>> = callbackFlow {
        val reg = proposalsCol.orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    // In caso di errore, emettiamo lista vuota per non bloccare la UI
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toRemoteProposal()
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override suspend fun upsertProposal(proposal: RemoteProposal) {
        proposalsCol.document(proposal.proposalId).set(proposal.toMap()).await()
    }

    override suspend fun deleteProposal(proposalId: String) {
        // Delete proposal and cascade delete ingredients linked to it
        db.runBatch { batch ->
            batch.delete(proposalsCol.document(proposalId))
            // delete linked ingredients
            // NOTE: query in batch only for IDs retrieval; actual deletes batched.
        }.await()
        // Firestore batch does not support query deletes; do a small follow-up
        val ing = ingredientsCol.whereEqualTo("proposalId", proposalId).get().await()
        db.runBatch { b ->
            ing.documents.forEach { b.delete(it.reference) }
        }.await()
        val shopping = shoppingCol.whereEqualTo("proposalId", proposalId).get().await()
        if (!shopping.isEmpty) {
            db.runBatch { b ->
                shopping.documents.forEach { b.delete(it.reference) }
            }.await()
        }
    }

    override fun streamIngredients(): Flow<List<RemoteIngredient>> = callbackFlow {
        val reg = ingredientsCol.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull { it.toRemoteIngredient() } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    override suspend fun upsertIngredient(ingredient: RemoteIngredient) {
        ingredientsCol.document(ingredient.ingredientId).set(ingredient.toMap()).await()
    }

    override suspend fun deleteIngredient(ingredientId: String) {
        ingredientsCol.document(ingredientId).delete().await()
    }

    override fun streamShopping(): Flow<List<RemoteShoppingItem>> = callbackFlow {
        val reg = shoppingCol.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull { it.toRemoteShopping() } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    override suspend fun upsertShoppingItem(item: RemoteShoppingItem) {
        shoppingCol.document(item.shoppingId).set(item.toMap()).await()
    }

    override suspend fun deleteShoppingItem(shoppingId: String) {
        shoppingCol.document(shoppingId).delete().await()
    }
}

// --- Mapping helpers ---

private fun Map<String, Any?>.getString(key: String): String? = this[key] as? String
private fun Map<String, Any?>.getBool(key: String): Boolean? = this[key] as? Boolean

private fun com.google.firebase.firestore.DocumentSnapshot.toRemoteProposal(): RemoteProposal? {
    val d = data ?: return null
    val id = d.getString("proposalId") ?: id
    return RemoteProposal(
        proposalId = id,
        mealSlot = d.getString("mealSlot") ?: return null,
        title = d.getString("title") ?: return null,
        notes = d.getString("notes"),
        createdBy = d.getString("createdBy") ?: "app",
        createdAt = d.getString("createdAt") ?: return null,
        updatedAt = d.getString("updatedAt") ?: return null
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toRemoteIngredient(): RemoteIngredient? {
    val d = data ?: return null
    val id = d.getString("ingredientId") ?: id
    return RemoteIngredient(
        ingredientId = id,
        proposalId = d.getString("proposalId") ?: return null,
        name = d.getString("name") ?: return null,
        needToBuy = d.getBool("needToBuy") ?: false,
        updatedAt = d.getString("updatedAt") ?: return null
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toRemoteShopping(): RemoteShoppingItem? {
    val d = data ?: return null
    val id = d.getString("shoppingId") ?: id
    return RemoteShoppingItem(
        shoppingId = id,
        ingredientId = d.getString("ingredientId"),
        proposalId = d.getString("proposalId"),
        name = d.getString("name") ?: return null,
        status = d.getString("status") ?: "pending",
        updatedAt = d.getString("updatedAt") ?: return null
    )
}

private fun RemoteProposal.toMap() = mapOf(
    "proposalId" to proposalId,
    "mealSlot" to mealSlot,
    "title" to title,
    "notes" to notes,
    "createdBy" to createdBy,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt
)

private fun RemoteIngredient.toMap() = mapOf(
    "ingredientId" to ingredientId,
    "proposalId" to proposalId,
    "name" to name,
    "needToBuy" to needToBuy,
    "updatedAt" to updatedAt
)

private fun RemoteShoppingItem.toMap() = mapOf(
    "shoppingId" to shoppingId,
    "ingredientId" to ingredientId,
    "proposalId" to proposalId,
    "name" to name,
    "status" to status,
    "updatedAt" to updatedAt
)