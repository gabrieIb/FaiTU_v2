package com.menumanager.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val HOUSEHOLDS_COLLECTION = "households"
private const val PROPOSALS_COLLECTION = "proposals"
private const val INGREDIENTS_COLLECTION = "ingredients"
private const val SHOPPING_COLLECTION = "shopping"

class FirestoreMenuDataSource(
	private val firestore: FirebaseFirestore,
	private val householdId: String
) : MenuRemoteDataSource {

	private val householdRef
		get() = firestore.collection(HOUSEHOLDS_COLLECTION).document(householdId)

	private val proposalsRef
		get() = householdRef.collection(PROPOSALS_COLLECTION)

	private val ingredientsRef
		get() = householdRef.collection(INGREDIENTS_COLLECTION)

	private val shoppingRef
		get() = householdRef.collection(SHOPPING_COLLECTION)

	override fun streamProposals(): Flow<List<RemoteProposal>> = callbackFlow {
		val registration = proposalsRef.addSnapshotListener { snapshot, error ->
			if (error != null) {
				close(error)
				return@addSnapshotListener
			}

			val items = snapshot?.documents?.mapNotNull { doc ->
				val proposalId = doc.getString("proposalId") ?: doc.id
				val mealSlot = doc.getString("mealSlot") ?: return@mapNotNull null
				val title = doc.getString("title") ?: return@mapNotNull null
				val createdBy = doc.getString("createdBy") ?: ""
				val createdAt = doc.getString("createdAt") ?: return@mapNotNull null
				val updatedAt = doc.getString("updatedAt") ?: createdAt
				val status = doc.getString("status") ?: RemoteProposal.DEFAULT_STATUS
				RemoteProposal(
					proposalId = proposalId,
					mealSlot = mealSlot,
					title = title,
					notes = doc.getString("notes"),
					createdBy = createdBy,
					createdAt = createdAt,
					updatedAt = updatedAt,
					status = status
				)
			} ?: emptyList()

			trySend(items).isSuccess
		}

		awaitClose { registration.remove() }
	}

	override fun streamIngredients(): Flow<List<RemoteIngredient>> = callbackFlow {
		val registration = ingredientsRef.addSnapshotListener { snapshot, error ->
			if (error != null) {
				close(error)
				return@addSnapshotListener
			}

			val items = snapshot?.documents?.mapNotNull { doc ->
				val ingredientId = doc.getString("ingredientId") ?: doc.id
				val proposalId = doc.getString("proposalId") ?: return@mapNotNull null
				val name = doc.getString("name") ?: return@mapNotNull null
				val needToBuy = doc.getBoolean("needToBuy") ?: false
				val updatedAt = doc.getString("updatedAt") ?: return@mapNotNull null
				RemoteIngredient(
					ingredientId = ingredientId,
					proposalId = proposalId,
					name = name,
					needToBuy = needToBuy,
					updatedAt = updatedAt
				)
			} ?: emptyList()

			trySend(items).isSuccess
		}

		awaitClose { registration.remove() }
	}

	override fun streamShopping(): Flow<List<RemoteShoppingItem>> = callbackFlow {
		val registration = shoppingRef.addSnapshotListener { snapshot, error ->
			if (error != null) {
				close(error)
				return@addSnapshotListener
			}

			val items = snapshot?.documents?.mapNotNull { doc ->
				val shoppingId = doc.getString("shoppingId") ?: doc.id
				val name = doc.getString("name") ?: return@mapNotNull null
				val status = doc.getString("status") ?: "pending"
				val updatedAt = doc.getString("updatedAt") ?: return@mapNotNull null
				RemoteShoppingItem(
					shoppingId = shoppingId,
					ingredientId = doc.getString("ingredientId"),
					proposalId = doc.getString("proposalId"),
					name = name,
					status = status,
					updatedAt = updatedAt
				)
			} ?: emptyList()

			trySend(items).isSuccess
		}

		awaitClose { registration.remove() }
	}

	override suspend fun upsertProposal(proposal: RemoteProposal) {
		val payload = hashMapOf(
			"proposalId" to proposal.proposalId,
			"mealSlot" to proposal.mealSlot,
			"title" to proposal.title,
			"notes" to proposal.notes,
			"createdBy" to proposal.createdBy,
			"createdAt" to proposal.createdAt,
			"updatedAt" to proposal.updatedAt,
			"status" to proposal.status
		)
		proposalsRef.document(proposal.proposalId).set(payload).await()
	}

	override suspend fun deleteProposal(proposalId: String) {
		proposalsRef.document(proposalId).delete().await()
	}

	override suspend fun upsertIngredient(ingredient: RemoteIngredient) {
		val payload = hashMapOf(
			"ingredientId" to ingredient.ingredientId,
			"proposalId" to ingredient.proposalId,
			"name" to ingredient.name,
			"needToBuy" to ingredient.needToBuy,
			"updatedAt" to ingredient.updatedAt
		)
		ingredientsRef.document(ingredient.ingredientId).set(payload).await()
	}

	override suspend fun deleteIngredient(ingredientId: String) {
		ingredientsRef.document(ingredientId).delete().await()
	}

	override suspend fun upsertShoppingItem(item: RemoteShoppingItem) {
		val payload = hashMapOf(
			"shoppingId" to item.shoppingId,
			"ingredientId" to item.ingredientId,
			"proposalId" to item.proposalId,
			"name" to item.name,
			"status" to item.status,
			"updatedAt" to item.updatedAt
		)
		shoppingRef.document(item.shoppingId).set(payload).await()
	}

	override suspend fun deleteShoppingItem(shoppingId: String) {
		shoppingRef.document(shoppingId).delete().await()
	}
}
