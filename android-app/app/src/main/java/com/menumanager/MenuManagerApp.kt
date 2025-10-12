package com.menumanager

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application root used to scope singletons.
 */
class MenuManagerApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val container: ServiceLocator by lazy {
        ServiceLocator(applicationContext, appScope)
    }
}
