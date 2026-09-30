package com.myclinic.app.di

import com.myclinic.app.data.auth.AuthRepository
import com.myclinic.app.data.auth.SupabaseAuthRepository
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.app.data.doctor.SupabaseDoctorRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Tells Hilt which implementation to use for each repository interface.
 * Screens depend only on the interfaces, so tests can swap in fakes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds @Singleton
    abstract fun bindDoctorRepository(impl: SupabaseDoctorRepository): DoctorRepository
}
