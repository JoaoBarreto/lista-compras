package com.example.listacompras.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Reachability { Unknown, Reachable, Unreachable }

/**
 * Whether the server answered recently. Written only by the realtime connection (the one component
 * that runs whenever the status is visible), so there is a single source of truth and nothing to
 * flip-flop between.
 *
 * It also knows whether the network is up, so a connection that finishes its handshake just after
 * the network dropped cannot overwrite the reset with a stale "Reachable". The network change and
 * the reachable mark take the same lock, which makes that race impossible.
 */
class ServerReachability {
    private val _state = MutableStateFlow(Reachability.Unknown)
    val state: StateFlow<Reachability> = _state

    private var networkUp = true

    /** Called on every network change; losing the network forgets what we knew about the server. */
    @Synchronized
    fun onNetworkChanged(up: Boolean) {
        networkUp = up
        if (!up) _state.value = Reachability.Unknown
    }

    /** Ignored while the network is down. */
    @Synchronized
    fun markReachable() {
        if (networkUp) _state.value = Reachability.Reachable
    }

    @Synchronized
    fun markUnreachable() { _state.value = Reachability.Unreachable }

    @Synchronized
    fun markUnknown() { _state.value = Reachability.Unknown }
}
