package com.menumanager.data

import com.menumanager.data.model.ApiState
import com.menumanager.data.model.MealIngredient
import com.menumanager.data.model.MealProposal
import com.menumanager.data.model.ShoppingEntry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MenuApiClient(
    private val baseUrl: String,
    private val token: String
) {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json".toMediaType()

    suspend fun fetchState(): ApiState {
        require(baseUrl.isNotBlank()) { "Base URL non configurato" }
        require(token.isNotBlank()) { "Token API non configurato" }
        val separator = if (baseUrl.contains("?")) "&" else "?"
        val url = "$baseUrl${separator}action=listState&token=$token"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Errore ${response.code}")
        val body = response.body?.string() ?: throw IOException("Corpo vuoto")
        val json = JSONObject(body)
        return ApiState(
            proposals = parseProposals(json.optJSONArray("proposals")),
            ingredients = parseIngredients(json.optJSONArray("ingredients")),
            shopping = parseShopping(json.optJSONArray("shopping"))
        )
    }

    suspend fun saveProposal(proposal: MealProposal) {
        postAction(
            action = "saveProposal",
            payload = JSONObject().apply {
                put("proposal_id", proposal.proposalId)
                put("meal_slot", proposal.mealSlot)
                put("title", proposal.title)
                put("notes", proposal.notes)
                put("created_by", proposal.createdBy)
                put("created_at", proposal.createdAt)
                put("updated_at", proposal.updatedAt)
            }
        )
    }

    suspend fun saveProposalWithIngredients(proposal: MealProposal, ingredients: List<MealIngredient>) {
        val payload = JSONObject().apply {
            put("proposal", JSONObject().apply {
                put("proposal_id", proposal.proposalId)
                put("meal_slot", proposal.mealSlot)
                put("title", proposal.title)
                put("notes", proposal.notes)
                put("created_by", proposal.createdBy)
                put("created_at", proposal.createdAt)
                put("updated_at", proposal.updatedAt)
            })
            put("ingredients", JSONArray().apply {
                ingredients.forEach { ingredient ->
                    put(JSONObject().apply {
                        put("ingredient_id", ingredient.ingredientId)
                        put("proposal_id", ingredient.proposalId)
                        put("name", ingredient.name)
                        put("need_to_buy", ingredient.needToBuy)
                        put("updated_at", ingredient.updatedAt)
                    })
                }
            })
        }
        postAction(action = "saveProposalWithIngredients", payload = payload)
    }

    suspend fun deleteProposal(proposalId: String) {
        postAction(
            action = "deleteProposal",
            payload = JSONObject().apply { put("proposal_id", proposalId) }
        )
    }

    suspend fun saveIngredient(ingredient: MealIngredient) {
        postAction(
            action = "saveIngredient",
            payload = JSONObject().apply {
                put("ingredient_id", ingredient.ingredientId)
                put("proposal_id", ingredient.proposalId)
                put("name", ingredient.name)
                put("need_to_buy", ingredient.needToBuy)
                put("updated_at", ingredient.updatedAt)
            }
        )
    }

    suspend fun deleteIngredient(ingredientId: String) {
        postAction(
            action = "deleteIngredient",
            payload = JSONObject().apply { put("ingredient_id", ingredientId) }
        )
    }

    private fun parseProposals(array: JSONArray?): List<MealProposal> {
        if (array == null) return emptyList()
        return List(array.length()) { index ->
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
    }

    private fun parseIngredients(array: JSONArray?): List<MealIngredient> {
        if (array == null) return emptyList()
        return List(array.length()) { index ->
            val obj = array.getJSONObject(index)
            MealIngredient(
                ingredientId = obj.optString("ingredient_id"),
                proposalId = obj.optString("proposal_id"),
                name = obj.optString("name"),
                needToBuy = parseBoolean(obj.opt("need_to_buy")),
                updatedAt = obj.optString("updated_at")
            )
        }
    }

    private fun parseShopping(array: JSONArray?): List<ShoppingEntry> {
        if (array == null) return emptyList()
        return List(array.length()) { index ->
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
    }

    suspend fun saveManualShoppingItem(entry: ShoppingEntry) {
        postAction(
            action = "saveShoppingItem",
            payload = JSONObject().apply {
                put("shopping_id", entry.shoppingId)
                put("ingredient_id", entry.ingredientId ?: "")
                put("proposal_id", entry.proposalId ?: "")
                put("name", entry.name)
                put("status", entry.status)
            }
        )
    }

    suspend fun deleteShoppingItem(shoppingId: String) {
        postAction(
            action = "deleteShoppingItem",
            payload = JSONObject().apply { put("shopping_id", shoppingId) }
        )
    }

    private fun parseBoolean(raw: Any?): Boolean = when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> raw.equals("true", ignoreCase = true) || raw == "1"
        else -> false
    }

    private fun postAction(action: String, payload: JSONObject) {
        val requestJson = JSONObject().apply {
            put("action", action)
            put("token", token)
            put("payload", payload)
        }
        val request = Request.Builder()
            .url(baseUrl)
            .post(requestJson.toString().toRequestBody(jsonMedia))
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Errore ${response.code} per azione $action")
        }
    }
}
