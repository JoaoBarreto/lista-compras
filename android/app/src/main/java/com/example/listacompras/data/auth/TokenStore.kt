package com.example.listacompras.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.listacompras.data.remote.TokenProvider
import com.example.listacompras.data.repository.CurrentUserProvider
import com.example.listacompras.domain.model.AuthState
import com.example.listacompras.domain.model.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Persists the auth token (encrypted) and the user's identity in DataStore.
 * The latest value is cached in [state] so [token] can be read synchronously from OkHttp threads.
 */
class TokenStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
    scope: CoroutineScope,
) : TokenProvider, CurrentUserProvider {

    private data class Stored(val token: String, val session: UserSession)

    // Only this class writes the DataStore, so after the initial load the cache is updated directly
    // by save()/clear(); concurrent OkHttp threads then see changes immediately.
    private val stored = MutableStateFlow<Stored?>(UNLOADED)

    init {
        scope.launch {
            val initial = dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map(::read)
                .first()
            stored.compareAndSet(UNLOADED, initial)
        }
    }

    val state: StateFlow<AuthState> = stored
        .map { value ->
            when {
                value === UNLOADED -> AuthState.Loading
                value == null -> AuthState.LoggedOut
                else -> AuthState.LoggedIn(value.session)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    /**
     * The current token (null while loading or logged out). Unlike [state] it also emits when the
     * token is refreshed for the same user, which the realtime subscription needs to re-authorize.
     */
    val tokenFlow: Flow<String?> = stored
        .map { value -> if (value === UNLOADED) null else value?.token }
        .distinctUntilChanged()

    override fun token(): String? = stored.value?.takeIf { it !== UNLOADED }?.token

    override fun currentUserId(): String? = currentSession()?.userId

    /** Current user, or null when logged out or not loaded yet. */
    fun currentSession(): UserSession? = stored.value?.takeIf { it !== UNLOADED }?.session

    suspend fun save(token: String, session: UserSession) {
        val encrypted = cipher.encrypt(token)
        dataStore.edit {
            it[TOKEN] = encrypted
            it[USER_ID] = session.userId
            session.displayName?.let { name -> it[USER_NAME] = name } ?: it.remove(USER_NAME)
        }
        stored.value = Stored(token, session)
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
        stored.value = null
    }

    /** An undecryptable token (e.g. lost Keystore key) counts as logged out. */
    private fun read(prefs: Preferences): Stored? {
        val token = prefs[TOKEN]?.let(cipher::decrypt) ?: return null
        val userId = prefs[USER_ID] ?: return null
        return Stored(token, UserSession(userId, prefs[USER_NAME]))
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val UNLOADED = Stored("", UserSession("", null))
    }
}
