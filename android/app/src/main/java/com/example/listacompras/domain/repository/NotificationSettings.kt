package com.example.listacompras.domain.repository

import kotlinx.coroutines.flow.Flow

/** User choices about notifications. Stored locally; each phone decides for itself. */
interface NotificationSettings {
    /** Whether to notify about items the other person added to the shopping list. On by default. */
    val shoppingEnabled: Flow<Boolean>

    suspend fun setShoppingEnabled(enabled: Boolean)

    /** True once the notification permission has been requested, so it is asked for only once. */
    suspend fun permissionAsked(): Boolean

    suspend fun markPermissionAsked()
}
