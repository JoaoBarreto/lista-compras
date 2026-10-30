package com.example.listacompras.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.domain.model.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TokenStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val cipher = FakeTokenCipher()
    private val scopes = mutableListOf<CoroutineScope>()

    /** DataStore allows one active instance per file, so close a store's scope before reopening the file. */
    private suspend fun closeStores() {
        scopes.forEach { it.coroutineContext[Job]!!.cancelAndJoin() }
        scopes.clear()
    }

    private fun newStore(file: File): TokenStore {
        val scope = CoroutineScope(Dispatchers.Default + Job()).also(scopes::add)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        return TokenStore(dataStore, cipher, scope)
    }

    @After
    fun tearDown() = runBlocking { closeStores() }

    private suspend fun TokenStore.awaitLoaded(): AuthState = state.first { it !is AuthState.Loading }

    @Test
    fun emptyStoreIsLoggedOutWithoutToken() = runBlocking {
        val store = newStore(tmp.newFile("a.preferences_pb").also { it.delete() })

        assertEquals(AuthState.LoggedOut, store.awaitLoaded())
        assertNull(store.token())
    }

    @Test
    fun tokenPersistsAcrossInstances() = runBlocking {
        val file = tmp.newFile("b.preferences_pb").also { it.delete() }
        val session = UserSession("user1", "Ana")
        newStore(file).save("tok123", session)
        closeStores()

        val reopened = newStore(file)

        assertEquals(AuthState.LoggedIn(session), reopened.awaitLoaded())
        assertEquals("tok123", reopened.token())
    }

    @Test
    fun clearLogsOut() = runBlocking {
        val store = newStore(tmp.newFile("c.preferences_pb").also { it.delete() })
        store.save("tok", UserSession("u", null))
        store.state.first { it is AuthState.LoggedIn }

        store.clear()

        assertEquals(AuthState.LoggedOut, store.state.first { it is AuthState.LoggedOut })
        assertNull(store.token())
    }

    @Test
    fun tokenFlowEmitsOnRefreshOfTheSameUserAndOnLogout() = runBlocking {
        val store = newStore(tmp.newFile("e.preferences_pb").also { it.delete() })
        val session = UserSession("u", "Ana")
        val seen = mutableListOf<String?>()
        val collector = launch(Dispatchers.Unconfined) { store.tokenFlow.collect { seen += it } }

        store.save("t1", session)
        store.save("t2", session)
        store.save("t2", session)
        store.clear()
        collector.cancelAndJoin()

        // Each distinct token once (the repeated "t2" is not re-emitted), then null after logout.
        assertEquals(listOf("t1", "t2"), seen.filterNotNull())
        assertNull(seen.last())
    }

    @Test
    fun undecryptableTokenCountsAsLoggedOut() = runBlocking {
        val file = tmp.newFile("d.preferences_pb").also { it.delete() }
        newStore(file).save("tok", UserSession("u", "Ana"))
        closeStores()
        cipher.failDecrypt = true

        val reopened = newStore(file)

        assertEquals(AuthState.LoggedOut, reopened.awaitLoaded())
        assertNull(reopened.token())
    }
}
