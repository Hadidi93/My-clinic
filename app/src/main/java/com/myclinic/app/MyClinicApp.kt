package com.myclinic.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.myclinic.app.debug.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. @HiltAndroidApp switches on dependency injection;
 * the WorkManager configuration lets background sync workers receive their
 * dependencies too.
 */
@HiltAndroidApp
class MyClinicApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        CrashReporter.install(this) // test builds only; first, to catch crashes during startup
        super.onCreate()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
