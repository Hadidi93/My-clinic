package com.myclinic.app.di

import android.content.Context
import com.myclinic.app.data.local.LocalDao
import com.myclinic.app.data.local.LocalDatabase
import com.myclinic.app.data.records.CachedPatientRepository
import com.myclinic.app.data.records.PatientRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LocalDatabase = LocalDatabase.open(context)

    @Provides
    fun provideDao(db: LocalDatabase): LocalDao = db.dao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordsModule {
    @Binds @Singleton
    abstract fun bindPatientRepository(impl: CachedPatientRepository): PatientRepository
}
