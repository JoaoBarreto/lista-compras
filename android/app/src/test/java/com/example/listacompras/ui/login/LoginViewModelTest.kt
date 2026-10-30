package com.example.listacompras.ui.login

import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.domain.model.UserSession
import com.example.listacompras.domain.repository.AuthRepository
import com.example.listacompras.domain.repository.LoginError
import com.example.listacompras.domain.repository.LoginResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private class FakeAuthRepository(var result: LoginResult) : AuthRepository {
        var calls = 0
        override val authState: Flow<AuthState> = MutableStateFlow(AuthState.LoggedOut)

        override suspend fun login(email: String, password: String): LoginResult {
            calls++
            return result
        }

        override suspend fun refreshSessionIfNeeded() = Unit

        override suspend fun logout() = Unit
    }

    private val success = LoginResult.Success(UserSession("u", "Ana"))

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun cannotSubmitWithoutCredentials() = runTest {
        val repo = FakeAuthRepository(success)
        val vm = LoginViewModel(repo)

        vm.onLoginClick()
        advanceUntilIdle()

        assertEquals(0, repo.calls)
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun failureExposesErrorAndStopsLoading() = runTest {
        val repo = FakeAuthRepository(LoginResult.Failure(LoginError.Network))
        val vm = LoginViewModel(repo)
        vm.onEmailChange("a@b.pt")
        vm.onPasswordChange("secret")

        vm.onLoginClick()
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()

        assertEquals(LoginError.Network, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun editingClearsError() = runTest {
        val repo = FakeAuthRepository(LoginResult.Failure(LoginError.InvalidCredentials))
        val vm = LoginViewModel(repo)
        vm.onEmailChange("a@b.pt")
        vm.onPasswordChange("bad")
        vm.onLoginClick()
        advanceUntilIdle()

        vm.onPasswordChange("good")

        assertNull(vm.uiState.value.error)
    }
}
