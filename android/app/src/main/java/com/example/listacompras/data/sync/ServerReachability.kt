package com.example.listacompras.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Reachability { Unknown, Reachable, Unreachable }

/**
 * Whether the server answered recently. Written only by the realtime connection (the one component
 * that runs whenever the status is visible), so there is a single source of truth and nothing to
 * flip-flop between.
 */
class ServerReachability {
    private val _state = MutableStateFlow(Reachability.Unknown)
    val state: StateFlow<Reachability> = _state

    fun markReachable() { _state.value = Reachability.Reachable }
    fun markUnreachable() { _state.value = Reachability.Unreachable }
    fun markUnknown() { _state.value = Reachability.Unknown }
}
