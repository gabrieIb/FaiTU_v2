package com.menumanager.data.remote

import kotlinx.coroutines.flow.Flow

data class RemoteProposal(
	val proposalId: String,
	val mealSlot: String,
	val title: String,
	val notes: String? = null,
	val createdBy: String,
	val createdAt: String,
	val updatedAt: String,
	val status: String = DEFAULT_STATUS
) {
	companion object {
		const val DEFAULT_STATUS: String = "pending"
	}
}

data class RemoteIngredient(
	val ingredientId: String,
	val proposalId: String,
	val name: String,
	val needToBuy: Boolean,
	val updatedAt: String
)

data class RemoteShoppingItem(
	val shoppingId: String,
	val ingredientId: String?,
	val proposalId: String?,
	val name: String,
	val status: String,
	val updatedAt: String
)

interface MenuRemoteDataSource {
	fun streamProposals(): Flow<List<RemoteProposal>>
	fun streamIngredients(): Flow<List<RemoteIngredient>>
	fun streamShopping(): Flow<List<RemoteShoppingItem>>

	suspend fun upsertProposal(proposal: RemoteProposal)
	suspend fun deleteProposal(proposalId: String)

	suspend fun upsertIngredient(ingredient: RemoteIngredient)
	suspend fun deleteIngredient(ingredientId: String)

	suspend fun upsertShoppingItem(item: RemoteShoppingItem)
	suspend fun deleteShoppingItem(shoppingId: String)
}
