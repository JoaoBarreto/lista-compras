package com.example.listacompras.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.listacompras.domain.repository.SyncScheduler
import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.domain.repository.AuthRepository
import com.example.listacompras.domain.repository.ListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes the auth state that decides which top-level destination is shown. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val listRepository: ListRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    init {
        viewModelScope.launch { authRepository.refreshSessionIfNeeded() }
        // Needs the network once (right after login, or on a later start if that attempt failed);
        // afterwards the list id is local and adding items works offline.
        viewModelScope.launch {
            authRepository.authState
                // Map before filtering so LoggedIn -> LoggedOut -> LoggedIn (same user) fires again.
                .map { (it as? AuthState.LoggedIn)?.session?.userId }
                .distinctUntilChanged()
                .filterNotNull()
                .collect {
                    listRepository.ensureActiveList()
                    // Restarts sending after a login (e.g. a run that ended because the session was lost).
                    syncScheduler.requestSync()
                }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
