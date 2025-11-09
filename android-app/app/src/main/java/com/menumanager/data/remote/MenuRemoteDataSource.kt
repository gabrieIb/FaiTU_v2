package com.menumanager.data.remote

import kotlinx.coroutines.flow.Flow

/**
 * Contratto astratto per la sorgente dati remota (Sheets o Firebase).
 * Permette una migrazione incrementale dietro feature flag senza cambiare il resto dell'app.
 */
interface MenuRemoteDataSource {
    // --- Proposals (MenuPlan) ---
    fun streamProposals(): Flow<List<RemoteProposal>>
    suspend fun upsertProposal(proposal: RemoteProposal)
    suspend fun deleteProposal(proposalId: String)

    // --- Ingredients ---
    fun streamIngredients(): Flow<List<RemoteIngredient>>
    suspend fun upsertIngredient(ingredient: RemoteIngredient)
    suspend fun deleteIngredient(ingredientId: String)

    // --- Shopping list ---
    fun streamShopping(): Flow<List<RemoteShoppingItem>>
    suspend fun upsertShoppingItem(item: RemoteShoppingItem)
    suspend fun deleteShoppingItem(shoppingId: String)
}

// Remote DTOs (semplificati); saranno mappati ai domain model esistenti nel Repository.
data class RemoteProposal(
    val proposalId: String,
    val mealSlot: String,
    val title: String,
    val notes: String?,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String
)

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