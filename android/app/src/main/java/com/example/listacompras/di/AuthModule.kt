package com.example.listacompras.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.listacompras.data.auth.AuthRepositoryImpl
import com.example.listacompras.data.auth.KeystoreTokenCipher
import com.example.listacompras.data.auth.TokenCipher
import com.example.listacompras.data.auth.TokenStore
import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.domain.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    // File name is excluded from backups in backup_rules.xml / data_extraction_rules.xml.
    private const val AUTH_DATASTORE = "auth"

    @Provides
    @Singleton
    fun provideTokenCipher(): TokenCipher = KeystoreTokenCipher()

    @Provides
    @Singleton
    fun provideAuthDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
    ) {
        context.preferencesDataStoreFile(AUTH_DATASTORE)
    }

    @Provides
    @Singleton
    fun provideTokenStore(
        dataStore: DataStore<Preferences>,
        cipher: TokenCipher,
        @ApplicationScope scope: CoroutineScope,
    ): TokenStore = TokenStore(dataStore, cipher, scope)

    @Provides
    @Singleton
    fun provideAuthRepository(
        api: PocketBaseApi,
        tokenStore: TokenStore,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): AuthRepository = AuthRepositoryImpl(api, tokenStore, dispatcher)
}
