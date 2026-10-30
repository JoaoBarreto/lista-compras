package com.example.listacompras.di

import com.example.listacompras.BuildConfig
import com.example.listacompras.data.auth.TokenStore
import com.example.listacompras.data.remote.AuthInterceptor
import com.example.listacompras.data.remote.PocketBaseApi
import com.example.listacompras.data.remote.PocketBaseApiFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.example.listacompras.data.remote.TokenAuthenticator
import com.example.listacompras.data.remote.TokenRefreshApi
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Client without authenticator; used to build the refresh API so refreshing cannot recurse. */
    @Provides
    @Singleton
    @BaseClient
    fun provideBaseClient(tokenStore: TokenStore): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideTokenRefreshApi(@BaseClient client: OkHttpClient): TokenRefreshApi =
        PocketBaseApiFactory.createRefreshApi(BuildConfig.SERVER_URL, client)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @BaseClient base: OkHttpClient,
        tokenStore: TokenStore,
        refreshApi: TokenRefreshApi,
    ): OkHttpClient = base.newBuilder()
        .authenticator(TokenAuthenticator(tokenStore, refreshApi))
        .build()

    @Provides
    @Singleton
    fun providePocketBaseApi(client: OkHttpClient): PocketBaseApi =
        PocketBaseApiFactory.create(BuildConfig.SERVER_URL, client)
}
