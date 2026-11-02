package com.example.listacompras.ui.navigation

import com.example.listacompras.data.repository.CountingSyncScheduler
import com.example.listacompras.domain.model.ListKind
import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.domain.model.UserSession
import com.example.listacompras.domain.repository.AuthRepository
import com.example.listacompras.domain.repository.ListRepository
import com.example.listacompras.domain.repository.LoginResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    private class FakeAuthRepository : AuthRepository {
        override val authState = MutableStateFlow<AuthState>(AuthState.Loading)
        override suspend fun login(email: String, password: String): LoginResult = error("not used")
        override suspend fun refreshSessionIfNeeded() = Unit
        override suspend fun logout() = Unit
    }

    private class CountingListRepository : ListRepository {
        var calls = 0
        override fun hasList(kind: ListKind) = MutableStateFlow(true)
        override suspend fun ensureLists(): Boolean { calls++; return true }
    }

    private val auth = FakeAuthRepository()
    private val lists = CountingListRepository()
    private val scheduler = CountingSyncScheduler()
    private val session = UserSession("u1", "Ana")

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `logging in again as the same user requests a sync and the list again`() = runTest {
        SessionViewModel(auth, lists, scheduler)

        auth.authState.value = AuthState.LoggedIn(session)
        advanceUntilIdle()
        assertEquals(1, scheduler.requests)
        assertEquals(1, lists.calls)

        auth.authState.value = AuthState.LoggedOut
        advanceUntilIdle()
        auth.authState.value = AuthState.LoggedIn(session)
        advanceUntilIdle()

        assertEquals(2, scheduler.requests)
        assertEquals(2, lists.calls)
    }

    @Test
    fun `nothing is requested while logged out`() = runTest {
        SessionViewModel(auth, lists, scheduler)

        auth.authState.value = AuthState.LoggedOut
        advanceUntilIdle()

        assertEquals(0, scheduler.requests)
        assertEquals(0, lists.calls)
    }
}
