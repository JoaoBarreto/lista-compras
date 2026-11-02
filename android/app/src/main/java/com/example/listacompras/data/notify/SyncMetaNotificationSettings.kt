package com.example.listacompras.data.notify

import com.example.listacompras.data.local.dao.SyncMetaDao
import com.example.listacompras.data.local.entity.SyncMetaEntity
import com.example.listacompras.domain.repository.NotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SyncMetaNotificationSettings(private val dao: SyncMetaDao) : NotificationSettings {

    override val shoppingEnabled: Flow<Boolean> = dao.observe(KEY_SHOPPING).map { it != "0" }

    override suspend fun setShoppingEnabled(enabled: Boolean) =
        dao.put(SyncMetaEntity(KEY_SHOPPING, if (enabled) "1" else "0"))

    override suspend fun permissionAsked() = dao.get(KEY_PERMISSION_ASKED) == "1"

    override suspend fun markPermissionAsked() = dao.put(SyncMetaEntity(KEY_PERMISSION_ASKED, "1"))

    private companion object {
        const val KEY_SHOPPING = "notifyShopping"
        const val KEY_PERMISSION_ASKED = "notifyPermissionAsked"
    }
}
