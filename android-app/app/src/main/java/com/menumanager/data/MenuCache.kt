package com.menumanager.data

import android.content.Context
import com.menumanager.data.model.ApiState
import com.menumanager.data.model.MealIngredient
import com.menumanager.data.model.MealProposal
import com.menumanager.data.model.ShoppingEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MenuCache(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): ApiState? {
        val raw = prefs.getString(KEY_STATE, null) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    suspend fun write(state: ApiState) {
        val json = encode(state)
        withContext(Dispatchers.IO) { prefs.edit().putString(KEY_STATE, json).apply() }
    }

    fun readPending(): List<PendingOperation> {
        val raw = prefs.getString(KEY_PENDING, null) ?: return emptyList()
        return runCatching { decodePending(raw) }.getOrDefault(emptyList())
    }

    suspend fun writePending(operations: List<PendingOperation>) {
        val json = encodePending(operations)
        withContext(Dispatchers.IO) {
            if (operations.isEmpty()) {
                prefs.edit().remove(KEY_PENDING).apply()
            } else {
                prefs.edit().putString(KEY_PENDING, json).apply()
            }
        }
    }

    private fun encode(state: ApiState): String {
        val root = JSONObject()
        root.put("proposals", JSONArray().apply { state.proposals.forEach { put(encodeProposal(it)) } })
        root.put("ingredients", JSONArray().apply { state.ingredients.forEach { put(encodeIngredient(it)) } })
        root.put("shopping", JSONArray().apply { state.shopping.forEach { put(encodeShopping(it)) } })
        return root.toString()
    }

    private fun decode(raw: String): ApiState {
        val root = JSONObject(raw)
        val proposals = root.optJSONArray("proposals")?.let { decodeProposals(it) } ?: emptyList()
        val ingredients = root.optJSONArray("ingredients")?.let { decodeIngredients(it) } ?: emptyList()
        val shopping = root.optJSONArray("shopping")?.let { decodeShopping(it) } ?: emptyList()
        return ApiState(proposals = proposals, ingredients = ingredients, shopping = shopping)
    }

    private fun encodeProposal(proposal: MealProposal): JSONObject = JSONObject().apply {
        put("proposal_id", proposal.proposalId)
        put("meal_slot", proposal.mealSlot)
        put("title", proposal.title)
        put("notes", proposal.notes)
        put("created_by", proposal.createdBy)
        put("created_at", proposal.createdAt)
        put("updated_at", proposal.updatedAt)
    }

    private fun decodeProposals(array: JSONArray): List<MealProposal> = List(array.length()) { index ->
        val obj = array.getJSONObject(index)
        MealProposal(
            proposalId = obj.optString("proposal_id"),
            mealSlot = obj.optString("meal_slot"),
            title = obj.optString("title"),
            notes = obj.optString("notes"),
            createdBy = obj.optString("created_by"),
            createdAt = obj.optString("created_at"),
            updatedAt = obj.optString("updated_at")
        )
    }

    private fun encodeIngredient(ingredient: MealIngredient): JSONObject = JSONObject().apply {
        put("ingredient_id", ingredient.ingredientId)
        put("proposal_id", ingredient.proposalId)
        put("name", ingredient.name)
        put("need_to_buy", ingredient.needToBuy)
        put("updated_at", ingredient.updatedAt)
    }

    private fun decodeIngredients(array: JSONArray): List<MealIngredient> = List(array.length()) { index ->
        val obj = array.getJSONObject(index)
        MealIngredient(
            ingredientId = obj.optString("ingredient_id"),
            proposalId = obj.optString("proposal_id"),
            name = obj.optString("name"),
            needToBuy = obj.optBoolean("need_to_buy"),
            updatedAt = obj.optString("updated_at")
        )
    }

    private fun encodeShopping(entry: ShoppingEntry): JSONObject = JSONObject().apply {
        put("shopping_id", entry.shoppingId)
        put("ingredient_id", entry.ingredientId ?: "")
        put("proposal_id", entry.proposalId ?: "")
        put("name", entry.name)
        put("status", entry.status)
        put("updated_at", entry.updatedAt)
    }

    private fun decodeShopping(array: JSONArray): List<ShoppingEntry> = List(array.length()) { index ->
        val obj = array.getJSONObject(index)
        ShoppingEntry(
            shoppingId = obj.optString("shopping_id"),
            ingredientId = obj.optString("ingredient_id").takeIf { it.isNotBlank() },
            proposalId = obj.optString("proposal_id").takeIf { it.isNotBlank() },
            name = obj.optString("name"),
            status = obj.optString("status"),
            updatedAt = obj.optString("updated_at")
        )
    }

    private fun encodePending(operations: List<PendingOperation>): String {
        val array = JSONArray()
        operations.forEach { op -> array.put(encodeOperation(op)) }
        return array.toString()
    }

    private fun decodePending(raw: String): List<PendingOperation> {
        val array = JSONArray(raw)
        val results = mutableListOf<PendingOperation>()
        for (i in 0 until array.length()) {
            decodeOperation(array.getJSONObject(i))?.let { results.add(it) }
        }
        return results
    }

    private fun encodeOperation(operation: PendingOperation): JSONObject = when (operation) {
        is PendingOperation.SaveProposalWithIngredients -> JSONObject().apply {
            put("type", "saveProposalWithIngredients")
            put("proposal", encodeProposal(operation.proposal))
            put("ingredients", JSONArray().apply { operation.ingredients.forEach { put(encodeIngredient(it)) } })
        }
        is PendingOperation.SaveProposal -> JSONObject().apply {
            put("type", "saveProposal")
            put("proposal", encodeProposal(operation.proposal))
        }
        is PendingOperation.DeleteProposal -> JSONObject().apply {
            put("type", "deleteProposal")
            put("proposal_id", operation.proposalId)
        }
        is PendingOperation.SaveIngredient -> JSONObject().apply {
            put("type", "saveIngredient")
            put("ingredient", encodeIngredient(operation.ingredient))
            operation.expectedUpdatedAt?.let { put("expected_updated_at", it) }
        }
        is PendingOperation.DeleteIngredient -> JSONObject().apply {
            put("type", "deleteIngredient")
            put("ingredient_id", operation.ingredientId)
        }
        is PendingOperation.SaveManualShopping -> JSONObject().apply {
            put("type", "saveShopping")
            put("entry", encodeShopping(operation.entry))
        }
        is PendingOperation.DeleteShopping -> JSONObject().apply {
            put("type", "deleteShopping")
            put("shopping_id", operation.shoppingId)
        }
    }

    private fun decodeOperation(obj: JSONObject): PendingOperation? = when (obj.optString("type")) {
        "saveProposalWithIngredients" -> PendingOperation.SaveProposalWithIngredients(
            proposal = decodeProposals(JSONArray().apply { put(obj.getJSONObject("proposal")) }).first(),
            ingredients = obj.optJSONArray("ingredients")?.let { decodeIngredients(it) } ?: emptyList()
        )
        "saveProposal" -> PendingOperation.SaveProposal(
            proposal = decodeProposals(JSONArray().apply { put(obj.getJSONObject("proposal")) }).first()
        )
        "deleteProposal" -> PendingOperation.DeleteProposal(obj.optString("proposal_id"))
        "saveIngredient" -> PendingOperation.SaveIngredient(
            ingredient = decodeIngredients(JSONArray().apply { put(obj.getJSONObject("ingredient")) }).first(),
            expectedUpdatedAt = obj.optString("expected_updated_at").takeIf { it.isNotBlank() }
        )
        "deleteIngredient" -> PendingOperation.DeleteIngredient(obj.optString("ingredient_id"))
        "saveShopping" -> PendingOperation.SaveManualShopping(
            entry = decodeShopping(JSONArray().apply { put(obj.getJSONObject("entry")) }).first()
        )
        "deleteShopping" -> PendingOperation.DeleteShopping(obj.optString("shopping_id"))
        else -> null
    }

    companion object {
        private const val FILE_NAME = "menu_cache"
        private const val KEY_STATE = "state"
        private const val KEY_PENDING = "pending"
    }
}
