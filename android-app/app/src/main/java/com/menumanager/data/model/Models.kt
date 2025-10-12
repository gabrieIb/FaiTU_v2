package com.menumanager.data.model

data class MealProposal(
    val proposalId: String,
    val mealSlot: String,
    val title: String,
    val notes: String,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String
)

data class MealIngredient(
    val ingredientId: String,
    val proposalId: String,
    val name: String,
    val needToBuy: Boolean,
    val updatedAt: String
)

data class ShoppingEntry(
    val shoppingId: String,
    val ingredientId: String?,
    val proposalId: String?,
    val name: String,
    val status: String,
    val updatedAt: String
) {
    val isManual: Boolean
        get() = ingredientId.isNullOrBlank()
}

data class ApiState(
    val proposals: List<MealProposal>,
    val ingredients: List<MealIngredient>,
    val shopping: List<ShoppingEntry>
)
