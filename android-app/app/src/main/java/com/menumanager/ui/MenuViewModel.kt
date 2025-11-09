package com.menumanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menumanager.data.Loadable
import com.menumanager.data.MenuRepository
import com.menumanager.data.model.ApiState
import com.menumanager.data.model.MealIngredient
import com.menumanager.data.model.MealProposal
import com.menumanager.data.model.MealStatus
import com.menumanager.data.model.ShoppingEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MenuViewModel(private val repository: MenuRepository) : ViewModel() {
    val state: StateFlow<Loadable<ApiState>> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Loadable.Idle
    )

    fun refresh() {
        repository.refresh()
    }

    fun createProposal(
        mealSlot: String,
        title: String,
        notes: String,
        onResult: (Throwable?) -> Unit
    ) {
        val now = repository.nowIso()
        val proposal = MealProposal(
            proposalId = repository.createUuid(),
            mealSlot = mealSlot,
            title = title,
            notes = notes,
            createdBy = DEFAULT_AUTHOR,
            createdAt = now,
            updatedAt = now,
            status = MealStatus.Pending
        )
        saveProposalInternal(proposal, onResult)
    }

    fun createProposalWithIngredients(
        mealSlot: String,
        title: String,
        notes: String,
        ingredients: List<Pair<String, Boolean>>,
        onResult: (Throwable?) -> Unit
    ) {
        val proposalId = repository.createUuid()
        val now = repository.nowIso()
        val proposal = MealProposal(
            proposalId = proposalId,
            mealSlot = mealSlot,
            title = title,
            notes = notes,
            createdBy = DEFAULT_AUTHOR,
            createdAt = now,
            updatedAt = now,
            status = MealStatus.Pending
        )
        val ingredientModels = ingredients.map { (name, needToBuy) ->
            MealIngredient(
                ingredientId = repository.createUuid(),
                proposalId = proposalId,
                name = name,
                needToBuy = needToBuy,
                updatedAt = repository.nowIso()
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.saveProposalWithIngredients(proposal, ingredientModels)
            }.onSuccess { onResult(null) }
                .onFailure { onResult(it) }
        }
    }

    fun updateProposal(
        existing: MealProposal,
        mealSlot: String,
        title: String,
        notes: String,
        onResult: (Throwable?) -> Unit
    ) {
        val updated = existing.copy(
            mealSlot = mealSlot,
            title = title,
            notes = notes,
            updatedAt = repository.nowIso()
        )
        saveProposalInternal(updated, onResult)
    }

    fun updateProposalStatus(
        proposal: MealProposal,
        status: MealStatus,
        onResult: (Throwable?) -> Unit
    ) {
        val updated = proposal.copy(
            status = status,
            updatedAt = repository.nowIso()
        )
        saveProposalInternal(updated, onResult)
    }

    fun deleteProposal(proposalId: String, onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.deleteProposal(proposalId)
            }.onSuccess { onResult(null) }
                .onFailure { onResult(it) }
        }
    }

    fun createIngredient(
        proposalId: String,
        name: String,
        needToBuy: Boolean,
        onResult: (Throwable?) -> Unit
    ) {
        val ingredient = MealIngredient(
            ingredientId = repository.createUuid(),
            proposalId = proposalId,
            name = name,
            needToBuy = needToBuy,
            updatedAt = repository.nowIso()
        )
        saveIngredientInternal(ingredient, onResult)
    }

    fun updateIngredient(
        ingredient: MealIngredient,
        name: String,
        needToBuy: Boolean,
        onResult: (Throwable?) -> Unit
    ) {
        val updated = ingredient.copy(
            name = name,
            needToBuy = needToBuy,
            updatedAt = repository.nowIso()
        )
        saveIngredientInternal(updated, onResult)
    }

    fun toggleIngredientNeedToBuy(
        ingredient: MealIngredient,
        needToBuy: Boolean,
        onResult: (Throwable?) -> Unit
    ) {
        val updated = ingredient.copy(
            needToBuy = needToBuy,
            updatedAt = repository.nowIso()
        )
        saveIngredientInternal(updated, onResult)
    }

    fun deleteIngredient(ingredientId: String, onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.deleteIngredient(ingredientId)
            }.onSuccess { onResult(null) }
                .onFailure { onResult(it) }
        }
    }

    fun markIngredientPurchased(ingredientId: String, onResult: (Throwable?) -> Unit) {
        val data = (state.value as? Loadable.Ready)?.data ?: run {
            onResult(IllegalStateException("Stato non disponibile"))
            return
        }
        val ingredient = data.ingredients.firstOrNull { it.ingredientId == ingredientId } ?: run {
            onResult(IllegalArgumentException("Ingrediente non trovato"))
            return
        }
        toggleIngredientNeedToBuy(ingredient, needToBuy = false, onResult)
    }

    fun createManualShoppingItem(name: String, onResult: (Throwable?) -> Unit) {
        val shoppingId = repository.createUuid()
        viewModelScope.launch {
            runCatching {
                repository.saveManualShoppingItem(shoppingId = shoppingId, name = name)
            }.onSuccess { onResult(null) }
                .onFailure { onResult(it) }
        }
    }

    fun deleteManualShoppingItem(shoppingId: String, onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.deleteShoppingItem(shoppingId)
            }.onSuccess { onResult(null) }
                .onFailure { onResult(it) }
        }
    }

    fun completeShoppingEntry(entry: ShoppingEntry, onResult: (Throwable?) -> Unit) {
        completeShoppingEntries(listOf(entry), onResult)
    }

    fun completeShoppingEntries(entries: List<ShoppingEntry>, onResult: (Throwable?) -> Unit) {
        if (entries.isEmpty()) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val initial = state.value as? Loadable.Ready<ApiState> ?: run {
                onResult(IllegalStateException("Stato non disponibile"))
                return@launch
            }
            try {
                entries.forEach { entry ->
                    if (entry.isManual) {
                        repository.deleteShoppingItem(entry.shoppingId)
                    } else {
                        val ingredientId = entry.ingredientId ?: throw IllegalArgumentException("Ingrediente non valido")
                        val ingredient = (state.value as? Loadable.Ready)?.data?.ingredients?.firstOrNull { it.ingredientId == ingredientId }
                            ?: initial.data.ingredients.firstOrNull { it.ingredientId == ingredientId }
                            ?: throw IllegalArgumentException("Ingrediente non trovato")
                        repository.saveIngredient(
                            ingredient.copy(
                                needToBuy = false,
                                updatedAt = repository.nowIso()
                            )
                        )
                    }
                }
                onResult(null)
            } catch (t: Throwable) {
                onResult(t)
            }
        }
    }

    fun deleteShoppingEntries(entries: List<ShoppingEntry>, onResult: (Throwable?) -> Unit) {
        if (entries.isEmpty()) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            try {
                entries.forEach { entry ->
                    if (entry.isManual) {
                        repository.deleteShoppingItem(entry.shoppingId)
                    } else {
                        val ingredientId = entry.ingredientId ?: throw IllegalArgumentException("Ingrediente non valido")
                        repository.deleteIngredient(ingredientId)
                    }
                }
                onResult(null)
            } catch (t: Throwable) {
                onResult(t)
            }
        }
    }

    fun resetAll(onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.resetAll()
            }.onSuccess { onResult(null) }
                .onFailure { onResult(it) }
        }
    }

    private fun saveProposalInternal(proposal: MealProposal, onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.saveProposal(proposal)
            }.onSuccess { onResult(null) }
                .onFailure { onResult(it) }
        }
    }

    private fun saveIngredientInternal(ingredient: MealIngredient, onResult: (Throwable?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.saveIngredient(ingredient)
            }.onSuccess { onResult(null) }
                .onFailure { onResult(it) }
        }
    }

    companion object {
        private const val DEFAULT_AUTHOR = "menu-app"
    }
}
