package com.myclinic.app

import android.app.Application
import androidx.work.Configuration
import com.myclinic.app.data.export.PdfExporter
import com.myclinic.app.data.push.PushManager
import com.myclinic.app.debug.CrashReporter
import com.myclinic.app.di.appModules
import com.myclinic.app.security.AppLockManager
import com.myclinic.app.security.watchAndroidLifecycle
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.startKoin

/**
 * Application entry point. Starts Koin (dependency injection: it hands every
 * screen and worker the repositories it needs, on Android and later on iPhone);
 * the WorkManager configuration lets background sync workers receive them too.
 */
class MyClinicApp : Application(), Configuration.Provider {

    private val push: PushManager by inject()
    private val appLock: AppLockManager by inject()
    private val pdfExporter: PdfExporter by inject()

    override fun onCreate() {
        CrashReporter.install(this) // test builds only; first, to catch crashes during startup
        super.onCreate()
        startKoin {
            androidContext(this@MyClinicApp)
            modules(appModules)
        }
        push.init() // push notifications, if Firebase is configured
        appLock.watchAndroidLifecycle() // fingerprint / PIN at start and after 5 minutes away
        pdfExporter.cleanUp() // exported PDFs never stay on the phone
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(KoinWorkerFactory()).build()
}
