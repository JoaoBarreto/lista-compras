package com.example.listacompras.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerReachabilityTest {

    private val reachability = ServerReachability()

    @Test
    fun `losing the network resets to Unknown`() {
        reachability.markReachable()

        reachability.onNetworkChanged(false)

        assertEquals(Reachability.Unknown, reachability.state.value)
    }

    @Test
    fun `a handshake that finishes after the network dropped cannot mark Reachable`() {
        reachability.onNetworkChanged(false)

        reachability.markReachable()

        assertEquals(Reachability.Unknown, reachability.state.value)
    }

    @Test
    fun `reachable can be marked again once the network is back`() {
        reachability.onNetworkChanged(false)
        reachability.onNetworkChanged(true)

        reachability.markReachable()

        assertEquals(Reachability.Reachable, reachability.state.value)
    }
}
