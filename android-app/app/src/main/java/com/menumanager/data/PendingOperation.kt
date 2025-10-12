package com.menumanager.data

import com.menumanager.data.model.MealIngredient
import com.menumanager.data.model.MealProposal
import com.menumanager.data.model.ShoppingEntry

sealed class PendingOperation {
    data class SaveProposalWithIngredients(
        val proposal: MealProposal,
        val ingredients: List<MealIngredient>
    ) : PendingOperation()

    data class SaveProposal(val proposal: MealProposal) : PendingOperation()

    data class DeleteProposal(val proposalId: String) : PendingOperation()

    data class SaveIngredient(
        val ingredient: MealIngredient,
        val expectedUpdatedAt: String?
    ) : PendingOperation()

    data class DeleteIngredient(val ingredientId: String) : PendingOperation()

    data class SaveManualShopping(val entry: ShoppingEntry) : PendingOperation()

    data class DeleteShopping(val shoppingId: String) : PendingOperation()
}
