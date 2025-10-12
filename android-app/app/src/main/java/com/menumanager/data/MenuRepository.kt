package com.menumanager.data

import com.menumanager.data.model.ApiState
import com.menumanager.data.model.MealIngredient
import com.menumanager.data.model.MealProposal
import com.menumanager.data.model.ShoppingEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

class MenuRepository(
    private val apiClient: MenuApiClient,
    private val cache: MenuCache,
    private val scope: CoroutineScope
) {
    private val stateMutex = Mutex()
    private val _state: MutableStateFlow<Loadable<ApiState>> = MutableStateFlow(Loadable.Idle)
    val state: StateFlow<Loadable<ApiState>> = _state
    private val pendingOperations = mutableListOf<PendingOperation>()
    private val _hasPending = MutableStateFlow(false)
    val hasPending: StateFlow<Boolean> = _hasPending.asStateFlow()

    init {
        cache.read()?.let { cached -> _state.value = Loadable.Ready(cached) }
        pendingOperations += cache.readPending()
        _hasPending.value = pendingOperations.isNotEmpty()
    }

    fun refresh() {
        scope.launch {
            val current = _state.value
            if (current !is Loadable.Ready) {
                _state.value = Loadable.Loading
            }
            runCatching {
                withContext(Dispatchers.IO) { apiClient.fetchState() }
            }.onSuccess { fresh ->
                stateMutex.withLock { _state.value = Loadable.Ready(fresh) }
                cache.write(fresh)
            }.onFailure { error ->
                if (_state.value !is Loadable.Ready) {
                    _state.value = Loadable.Error(error)
                }
            }
        }
    }

    fun createUuid(): String = UUID.randomUUID().toString()

    fun nowIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    suspend fun saveProposalWithIngredients(proposal: MealProposal, ingredients: List<MealIngredient>) {
        enqueue(PendingOperation.SaveProposalWithIngredients(proposal, ingredients))
        updateState { current ->
            val proposals = current.proposals.toMutableList().apply {
                removeAll { it.proposalId == proposal.proposalId }
                add(proposal)
            }
            val ingredientsList = current.ingredients.toMutableList().apply {
                removeAll { it.proposalId == proposal.proposalId }
                addAll(ingredients)
            }
            val shopping = rebuildShopping(ingredientsList, current.shopping)
            ApiState(proposals = proposals, ingredients = ingredientsList, shopping = shopping)
        }
    }

    suspend fun saveProposal(proposal: MealProposal) {
        enqueue(PendingOperation.SaveProposal(proposal))
        updateState { current ->
            val proposals = current.proposals.toMutableList().apply {
                val index = indexOfFirst { it.proposalId == proposal.proposalId }
                if (index >= 0) {
                    set(index, proposal)
                } else {
                    add(proposal)
                }
            }
            current.copy(proposals = proposals)
        }
    }

    suspend fun deleteProposal(proposalId: String) {
        enqueue(PendingOperation.DeleteProposal(proposalId))
        updateState { current ->
            val proposals = current.proposals.filterNot { it.proposalId == proposalId }
            val remainingIngredients = current.ingredients.filterNot { it.proposalId == proposalId }
            val shopping = rebuildShopping(remainingIngredients, current.shopping)
            ApiState(proposals = proposals, ingredients = remainingIngredients, shopping = shopping)
        }
    }

    suspend fun saveIngredient(ingredient: MealIngredient) {
        val snapshot = currentStateSnapshot()
        val previousUpdatedAt = snapshot.ingredients.firstOrNull { it.ingredientId == ingredient.ingredientId }?.updatedAt
        enqueue(PendingOperation.SaveIngredient(ingredient, previousUpdatedAt))
        updateState { current ->
            val ingredients = current.ingredients.toMutableList().apply {
                val index = indexOfFirst { it.ingredientId == ingredient.ingredientId }
                if (index >= 0) {
                    set(index, ingredient)
                } else {
                    add(ingredient)
                }
            }
            val shopping = rebuildShopping(ingredients, current.shopping)
            current.copy(ingredients = ingredients, shopping = shopping)
        }
    }

    suspend fun deleteIngredient(ingredientId: String) {
        enqueue(PendingOperation.DeleteIngredient(ingredientId))
        updateState { current ->
            val ingredients = current.ingredients.filterNot { it.ingredientId == ingredientId }
            val shopping = current.shopping.filterNot { it.shoppingId == ingredientId }
            current.copy(ingredients = ingredients, shopping = shopping)
        }
    }

    suspend fun saveManualShoppingItem(shoppingId: String, name: String) {
        val entry = ShoppingEntry(
            shoppingId = shoppingId,
            ingredientId = null,
            proposalId = null,
            name = name,
            status = "pending",
            updatedAt = nowIso()
        )
        enqueue(PendingOperation.SaveManualShopping(entry))
        updateState { current ->
            current.copy(shopping = current.shopping + entry)
        }
    }

    suspend fun deleteShoppingItem(shoppingId: String) {
        enqueue(PendingOperation.DeleteShopping(shoppingId))
        updateState { current ->
            current.copy(shopping = current.shopping.filterNot { it.shoppingId == shoppingId })
        }
    }

    suspend fun resetAll() {
        withContext(Dispatchers.IO) { apiClient.resetAll() }
        stateMutex.withLock {
            pendingOperations.clear()
            _hasPending.value = false
            _state.value = Loadable.Ready(ApiState(emptyList(), emptyList(), emptyList()))
        }
        cache.write(ApiState(emptyList(), emptyList(), emptyList()))
        cache.writePending(emptyList())
    }

    suspend fun syncPending(): Result<Unit> {
        val snapshot = stateMutex.withLock { pendingOperations.toList() }
        if (snapshot.isEmpty()) {
            refresh()
            return Result.success(Unit)
        }
        return runCatching {
            val remoteState = withContext(Dispatchers.IO) { apiClient.fetchState() }
            val remoteProposals = remoteState.proposals.associateBy { it.proposalId }.toMutableMap()
            val remoteIngredients = remoteState.ingredients.associateBy { it.ingredientId }.toMutableMap()
            val remoteShopping = remoteState.shopping.associateBy { it.shoppingId }.toMutableMap()
            val operationsToApply = mutableListOf<PendingOperation>()
            snapshot.forEach { op ->
                when (op) {
                    is PendingOperation.SaveProposal -> {
                        val remote = remoteProposals[op.proposal.proposalId]
                        val shouldSkip = remote?.let { existing ->
                            isServerUpToDate(existing.updatedAt, op.proposal.updatedAt) &&
                                existing.title == op.proposal.title &&
                                existing.notes == op.proposal.notes &&
                                existing.mealSlot == op.proposal.mealSlot
                        } ?: false
                        if (!shouldSkip) {
                            operationsToApply += op
                            remoteProposals[op.proposal.proposalId] = op.proposal
                        }
                    }
                    is PendingOperation.SaveProposalWithIngredients -> {
                        operationsToApply += op
                        remoteProposals[op.proposal.proposalId] = op.proposal
                        op.ingredients.forEach { ingredient ->
                            remoteIngredients[ingredient.ingredientId] = ingredient
                        }
                    }
                    is PendingOperation.DeleteProposal -> {
                        val exists = remoteProposals.containsKey(op.proposalId)
                        if (exists) {
                            operationsToApply += op
                            remoteProposals.remove(op.proposalId)
                            remoteIngredients.entries.removeIf { it.value.proposalId == op.proposalId }
                        }
                    }
                    is PendingOperation.SaveIngredient -> {
                        val remote = remoteIngredients[op.ingredient.ingredientId]
                        val expected = op.expectedUpdatedAt
                        val conflict = when {
                            expected == null && remote != null -> true
                            expected != null && remote == null -> true
                            expected != null && remote != null && remote.updatedAt != expected -> true
                            else -> false
                        }
                        if (!conflict) {
                            val shouldSkip = remote?.let { existing ->
                                isServerUpToDate(existing.updatedAt, op.ingredient.updatedAt) &&
                                    existing.needToBuy == op.ingredient.needToBuy &&
                                    existing.name == op.ingredient.name
                            } ?: false
                            if (!shouldSkip) {
                                operationsToApply += op
                                remoteIngredients[op.ingredient.ingredientId] = op.ingredient
                            }
                        }
                    }
                    is PendingOperation.DeleteIngredient -> {
                        val exists = remoteIngredients.containsKey(op.ingredientId)
                        if (exists) {
                            operationsToApply += op
                            remoteIngredients.remove(op.ingredientId)
                        }
                    }
                    is PendingOperation.SaveManualShopping -> {
                        val exists = remoteShopping.containsKey(op.entry.shoppingId)
                        if (!exists) {
                            operationsToApply += op
                            remoteShopping[op.entry.shoppingId] = op.entry
                        }
                    }
                    is PendingOperation.DeleteShopping -> {
                        val exists = remoteShopping.containsKey(op.shoppingId)
                        if (exists) {
                            operationsToApply += op
                            remoteShopping.remove(op.shoppingId)
                        }
                    }
                }
            }
            if (operationsToApply.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    operationsToApply.forEach { op ->
                        when (op) {
                            is PendingOperation.SaveProposalWithIngredients -> apiClient.saveProposalWithIngredients(op.proposal, op.ingredients)
                            is PendingOperation.SaveProposal -> apiClient.saveProposal(op.proposal)
                            is PendingOperation.DeleteProposal -> apiClient.deleteProposal(op.proposalId)
                            is PendingOperation.SaveIngredient -> apiClient.saveIngredient(op.ingredient)
                            is PendingOperation.DeleteIngredient -> apiClient.deleteIngredient(op.ingredientId)
                            is PendingOperation.SaveManualShopping -> apiClient.saveManualShoppingItem(op.entry)
                            is PendingOperation.DeleteShopping -> apiClient.deleteShoppingItem(op.shoppingId)
                        }
                    }
                }
            }
            stateMutex.withLock {
                pendingOperations.clear()
                _hasPending.value = false
            }
            cache.writePending(emptyList())
            refresh()
        }
    }

    private fun isServerUpToDate(serverTimestamp: String?, localTimestamp: String?): Boolean {
        val server = runCatching { Instant.parse(serverTimestamp) }.getOrNull()
        val local = runCatching { Instant.parse(localTimestamp) }.getOrNull()
        if (server == null || local == null) {
            return false
        }
        return !local.isAfter(server)
    }

    private suspend fun updateState(transform: (ApiState) -> ApiState) {
        val updated = stateMutex.withLock {
            val snapshot = when (val value = _state.value) {
                is Loadable.Ready -> value.data
                else -> ApiState(emptyList(), emptyList(), emptyList())
            }
            val next = transform(snapshot)
            _state.value = Loadable.Ready(next)
            next
        }
        cache.write(updated)
    }

    private suspend fun enqueue(operation: PendingOperation) {
        val snapshot = stateMutex.withLock {
            pendingOperations.add(operation)
            _hasPending.value = true
            pendingOperations.toList()
        }
        cache.writePending(snapshot)
    }

    private suspend fun currentStateSnapshot(): ApiState = stateMutex.withLock {
        when (val value = _state.value) {
            is Loadable.Ready -> value.data
            else -> ApiState(emptyList(), emptyList(), emptyList())
        }
    }

    private fun rebuildShopping(ingredients: List<MealIngredient>, shopping: List<ShoppingEntry>): List<ShoppingEntry> {
        val manual = shopping.filter { it.isManual }
        val ingredientEntries = ingredients.filter { it.needToBuy }.map { ingredient ->
            ShoppingEntry(
                shoppingId = ingredient.ingredientId,
                ingredientId = ingredient.ingredientId,
                proposalId = ingredient.proposalId,
                name = ingredient.name,
                status = "pending",
                updatedAt = ingredient.updatedAt
            )
        }
        return manual + ingredientEntries
    }
}

sealed class Loadable<out T> {
    object Idle : Loadable<Nothing>()
    object Loading : Loadable<Nothing>()
    data class Ready<T>(val data: T) : Loadable<T>()
    data class Error(val throwable: Throwable) : Loadable<Nothing>()
}
