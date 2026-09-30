package com.myclinic.app.di

import com.myclinic.app.BuildConfig
import com.myclinic.app.data.auth.AuthRedirects
import com.myclinic.app.data.security.EncryptedSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

/** Creates the single Supabase client the whole app shares. */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(sessionManager: EncryptedSessionManager): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY, // public key; RLS protects the data
        ) {
            install(Auth) {
                scheme = AuthRedirects.SCHEME
                host = AuthRedirects.HOST
                // PKCE: an email link only works in the app that requested it,
                // so another app catching the link cannot log in with it.
                flowType = FlowType.PKCE
                // Login tokens are stored encrypted with a key held in Android Keystore.
                this.sessionManager = sessionManager
            }
            install(Postgrest)
            install(Storage)
        }
}
