package com.example.listacompras.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.domain.repository.AuthRepository
import com.example.listacompras.domain.repository.ListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Exposes the auth state that decides which top-level destination is shown. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val listRepository: ListRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    init {
        viewModelScope.launch { authRepository.refreshSessionIfNeeded() }
        // Needs the network once (right after login, or on a later start if that attempt failed);
        // afterwards the list id is local and adding items works offline.
        viewModelScope.launch {
            authRepository.authState
                .filterIsInstance<AuthState.LoggedIn>()
                .distinctUntilChangedBy { it.session.userId }
                .collect { listRepository.ensureActiveList() }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
