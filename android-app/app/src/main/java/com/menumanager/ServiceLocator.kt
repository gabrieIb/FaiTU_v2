package com.menumanager

import android.content.Context
import com.menumanager.data.MenuApiClient
import com.menumanager.data.MenuCache
import com.menumanager.data.MenuRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Very small DI container so that composables/viewmodels can get repositories.
 */
class ServiceLocator(
    context: Context,
    scope: CoroutineScope
) {
    private val apiClient = MenuApiClient(
        baseUrl = AppSecrets.BASE_URL,
        token = AppSecrets.API_TOKEN
    )

    private val cache = MenuCache(context = context.applicationContext)

    val menuRepository: MenuRepository = MenuRepository(apiClient, cache, scope)
}
