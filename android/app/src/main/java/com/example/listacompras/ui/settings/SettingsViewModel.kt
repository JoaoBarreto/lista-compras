package com.example.listacompras.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.listacompras.domain.repository.EventNotificationSettings
import com.example.listacompras.domain.repository.NotificationSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: NotificationSettings,
    private val eventSettings: EventNotificationSettings,
) : ViewModel() {

    val shoppingNotifications: StateFlow<Boolean> =
        settings.shoppingEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun onShoppingNotificationsChange(enabled: Boolean) {
        viewModelScope.launch { settings.setShoppingEnabled(enabled) }
    }

    val eventNotifications: StateFlow<Boolean> =
        eventSettings.eventsEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun onEventNotificationsChange(enabled: Boolean) {
        viewModelScope.launch { eventSettings.setEventsEnabled(enabled) }
    }

    /** True the first time only; the caller then asks the system for the permission. */
    suspend fun shouldAskPermission(): Boolean {
        if (settings.permissionAsked()) return false
        settings.markPermissionAsked()
        return true
    }
}
