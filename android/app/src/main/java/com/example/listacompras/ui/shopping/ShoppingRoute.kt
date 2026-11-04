package com.example.listacompras.ui.shopping

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.listacompras.ui.settings.SettingsDialog
import com.example.listacompras.ui.settings.SettingsViewModel
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.listacompras.R
import com.example.listacompras.ui.calendar.CalendarScreen
import com.example.listacompras.ui.calendar.CalendarViewModel
import com.example.listacompras.ui.main.MainTab

/**
 * Hosts the three tabs (two lists and the calendar). Each tab is a complete screen with its own view model, so the text typed in
 * one add field can never be added to the other list; the saveable state holder keeps each tab's
 * scroll position and typed text while the other one is shown.
 */
@Composable
fun ShoppingRoute(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(MainTab.SHOPPING) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val settingsViewModel = hiltViewModel<SettingsViewModel>()
    val notificationsOn by settingsViewModel.shoppingNotifications.collectAsStateWithLifecycle()
    val eventNotificationsOn by settingsViewModel.eventNotifications.collectAsStateWithLifecycle()
    val askPermission = rememberNotificationPermissionRequester()
    // Asked once, here, right after the first login: that is when the user learns the app can notify.
    LaunchedEffect(Unit) { if (settingsViewModel.shouldAskPermission()) askPermission() }
    val stateHolder = rememberSaveableStateHolder()

    stateHolder.SaveableStateProvider(selected.name) {
        val kind = selected.listKind
        if (kind == null) {
            val viewModel = hiltViewModel<CalendarViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            CalendarScreen(
                state = state,
                tabs = { MainTabs(selected = selected, onSelect = { selected = it }) },
                onOpenSettings = { showSettings = true },
                onErrorAcknowledged = viewModel::onErrorAcknowledged,
                onPreviousMonth = viewModel::onPreviousMonth,
                onNextMonth = viewModel::onNextMonth,
                onToday = viewModel::onToday,
                onSelectDate = viewModel::onSelectDate,
                onSaveEvent = viewModel::onSaveEvent,
                onDeleteEvent = viewModel::onDeleteEvent,
                onSaveType = viewModel::onSaveType,
                onDeleteType = viewModel::onDeleteType,
                modifier = modifier,
            )
        } else {
            val viewModel = hiltViewModel<ShoppingViewModel, ShoppingViewModel.Factory>(
                key = selected.name,
                creationCallback = { factory -> factory.create(kind) },
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ShoppingScreen(
                state = state,
                texts = shoppingTexts(kind),
                tabs = { MainTabs(selected = selected, onSelect = { selected = it }) },
                onAdd = viewModel::onAdd,
                onCheckedChange = viewModel::onCheckedChange,
                onDelete = viewModel::onDelete,
                onEdit = viewModel::onEdit,
                onClearChecked = viewModel::onClearChecked,
                onErrorAcknowledged = viewModel::onErrorAcknowledged,
                onOpenSettings = { showSettings = true },
                modifier = modifier,
            )
        }
    }
    if (showSettings) {
        SettingsDialog(
            shoppingNotifications = notificationsOn,
            onShoppingNotificationsChange = {
                settingsViewModel.onShoppingNotificationsChange(it)
                if (it) askPermission() // a no-op when already granted or blocked by the system
            },
            eventNotifications = eventNotificationsOn,
            onEventNotificationsChange = {
                settingsViewModel.onEventNotificationsChange(it)
                if (it) askPermission()
            },
            onLogout = { showSettings = false; onLogout() },
            onDismiss = { showSettings = false },
        )
    }
}

/** Returns a function that asks for the notification permission (Android 13+) if it is missing. */
@Composable
private fun rememberNotificationPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun MainTabs(selected: MainTab, onSelect: (MainTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        MainTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        stringResource(
                            when (tab) {
                                MainTab.SHOPPING -> R.string.tab_shopping
                                MainTab.TASKS -> R.string.tab_tasks
                                MainTab.CALENDAR -> R.string.tab_calendar
                            },
                        ),
                    )
                },
            )
        }
    }
}
