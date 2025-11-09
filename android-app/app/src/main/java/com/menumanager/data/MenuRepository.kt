package com.menumanager.data

import com.google.firebase.firestore.FirebaseFirestore
import com.menumanager.data.model.ApiState
import com.menumanager.data.model.MealIngredient
import com.menumanager.data.model.MealProposal
import com.menumanager.data.model.MealStatus
import com.menumanager.data.model.ShoppingEntry
import com.menumanager.data.remote.FirestoreMenuDataSource
import com.menumanager.data.remote.MenuRemoteDataSource
import com.menumanager.data.remote.RemoteIngredient
import com.menumanager.data.remote.RemoteProposal
import com.menumanager.data.remote.RemoteShoppingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

class MenuRepository(
    private val firestore: FirebaseFirestore,
    private val householdRepository: HouseholdRepository,
    private val scope: CoroutineScope
) {
    private val _state: MutableStateFlow<Loadable<ApiState>> = MutableStateFlow(Loadable.Idle)
    val state: StateFlow<Loadable<ApiState>> = _state.asStateFlow()

    @Volatile
    private var remoteDataSource: MenuRemoteDataSource? = null

    init {
        scope.launch {
            householdRepository.householdIdFlow.collectLatest { householdId ->
                if (householdId.isNullOrBlank()) {
                    remoteDataSource = null
                    _state.value = Loadable.Idle
                    return@collectLatest
                }

                val dataSource = FirestoreMenuDataSource(firestore, householdId)
                remoteDataSource = dataSource

                combine(
                    dataSource.streamProposals(),
                    dataSource.streamIngredients(),
                    dataSource.streamShopping()
                ) { proposals, ingredients, shopping ->
                    val proposalDomains = proposals.map { it.toDomain() }
                    val ingredientDomains = ingredients.map { it.toDomain() }
                    val shoppingManual = shopping
                        .filter { it.ingredientId.isNullOrBlank() }
                        .map { it.toDomain() }
                    val shoppingFromIngredients = ingredientDomains
                        .filter { it.needToBuy }
                        .map { it.toShoppingEntry() }

                    ApiState(
                        proposals = proposalDomains,
                        ingredients = ingredientDomains,
                        shopping = shoppingManual + shoppingFromIngredients
                    )
                }
                    .onStart { _state.value = Loadable.Loading }
                    .catch { throwable -> _state.value = Loadable.Error(throwable) }
                    .collect { apiState ->
                        _state.value = Loadable.Ready(apiState)
                    }
            }
        }
    }

    fun refresh() {
        // Firestore snapshot listeners keep the state up to date automatically.
    }

    fun createUuid(): String = UUID.randomUUID().toString()

    fun nowIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    suspend fun saveProposalWithIngredients(proposal: MealProposal, ingredients: List<MealIngredient>) {
        val dataSource = requireDataSource()
        withContext(Dispatchers.IO) {
            dataSource.upsertProposal(proposal.toRemote())
            ingredients.forEach { dataSource.upsertIngredient(it.toRemote()) }
        }
    }

    suspend fun saveProposal(proposal: MealProposal) {
        val dataSource = requireDataSource()
        withContext(Dispatchers.IO) {
            dataSource.upsertProposal(proposal.toRemote())
        }
    }

    suspend fun deleteProposal(proposalId: String) {
        val dataSource = requireDataSource()
        withContext(Dispatchers.IO) {
            dataSource.deleteProposal(proposalId)
        }
    }

    suspend fun saveIngredient(ingredient: MealIngredient) {
        val dataSource = requireDataSource()
        withContext(Dispatchers.IO) {
            dataSource.upsertIngredient(ingredient.toRemote())
        }
    }

    suspend fun deleteIngredient(ingredientId: String) {
        val dataSource = requireDataSource()
        withContext(Dispatchers.IO) {
            dataSource.deleteIngredient(ingredientId)
        }
    }

    suspend fun saveManualShoppingItem(shoppingId: String, name: String) {
        val dataSource = requireDataSource()
        val entry = ShoppingEntry(
            shoppingId = shoppingId,
            ingredientId = null,
            proposalId = null,
            name = name,
            status = "pending",
            updatedAt = nowIso()
        )
        withContext(Dispatchers.IO) {
            dataSource.upsertShoppingItem(entry.toRemote())
        }
    }

    suspend fun deleteShoppingItem(shoppingId: String) {
        val dataSource = requireDataSource()
        withContext(Dispatchers.IO) {
            dataSource.deleteShoppingItem(shoppingId)
        }
    }

    suspend fun resetAll() {
        val dataSource = requireDataSource()
        val snapshot = (state.value as? Loadable.Ready)?.data
            ?: ApiState(emptyList(), emptyList(), emptyList())
        withContext(Dispatchers.IO) {
            snapshot.proposals.forEach { dataSource.deleteProposal(it.proposalId) }
            snapshot.ingredients.forEach { dataSource.deleteIngredient(it.ingredientId) }
            snapshot.shopping.filter { it.isManual }
                .forEach { dataSource.deleteShoppingItem(it.shoppingId) }
        }
    }

    private fun requireDataSource(): MenuRemoteDataSource =
        remoteDataSource ?: throw IllegalStateException("Configura la famiglia prima di usare il menu")
}

private fun RemoteProposal.toDomain(): MealProposal = MealProposal(
    proposalId = proposalId,
    mealSlot = mealSlot,
    title = title,
    notes = notes ?: "",
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    status = MealStatus.fromRaw(status)
)

private fun RemoteIngredient.toDomain(): MealIngredient = MealIngredient(
    ingredientId = ingredientId,
    proposalId = proposalId,
    name = name,
    needToBuy = needToBuy,
    updatedAt = updatedAt
)

private fun RemoteShoppingItem.toDomain(): ShoppingEntry = ShoppingEntry(
    shoppingId = shoppingId,
    ingredientId = ingredientId,
    proposalId = proposalId,
    name = name,
    status = status,
    updatedAt = updatedAt
)

private fun MealIngredient.toShoppingEntry(): ShoppingEntry = ShoppingEntry(
    shoppingId = ingredientId,
    ingredientId = ingredientId,
    proposalId = proposalId,
    name = name,
    status = "pending",
    updatedAt = updatedAt
)

private fun MealProposal.toRemote(): RemoteProposal = RemoteProposal(
    proposalId = proposalId,
    mealSlot = mealSlot,
    title = title,
    notes = notes.ifBlank { null },
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    status = status.rawValue
)

private fun MealIngredient.toRemote(): RemoteIngredient = RemoteIngredient(
    ingredientId = ingredientId,
    proposalId = proposalId,
    name = name,
    needToBuy = needToBuy,
    updatedAt = updatedAt
)

private fun ShoppingEntry.toRemote(): RemoteShoppingItem = RemoteShoppingItem(
    shoppingId = shoppingId,
    ingredientId = ingredientId,
    proposalId = proposalId,
    name = name,
    status = status,
    updatedAt = updatedAt
)

sealed class Loadable<out T> {
    object Idle : Loadable<Nothing>()
    object Loading : Loadable<Nothing>()
    data class Ready<T>(val data: T) : Loadable<T>()
    data class Error(val throwable: Throwable) : Loadable<Nothing>()
}
