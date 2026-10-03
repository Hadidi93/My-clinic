package com.myclinic.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.myclinic.app.data.push.PushManager
import com.myclinic.app.debug.CrashReporter
import com.myclinic.app.security.AppLockManager
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
    @Inject lateinit var push: PushManager
    @Inject lateinit var appLock: AppLockManager
    @Inject lateinit var pdfExporter: com.myclinic.app.data.export.PdfExporter

    override fun onCreate() {
        CrashReporter.install(this) // test builds only; first, to catch crashes during startup
        super.onCreate()
        push.init() // push notifications, if Firebase is configured
        appLock.start() // fingerprint / PIN at start and after 5 minutes away
        pdfExporter.cleanUp() // exported PDFs never stay on the phone
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
