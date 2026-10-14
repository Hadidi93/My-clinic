package com.myclinic.app.ios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.ComposeUIViewController
import com.myclinic.app.data.auth.AuthRepository
import com.myclinic.app.data.export.IosRecordPdfWriter
import com.myclinic.app.data.export.IosResearchZipWriter
import com.myclinic.app.data.export.RecordPdfWriter
import com.myclinic.app.data.export.ResearchZipWriter
import com.myclinic.app.data.export.cleanUpExports
import com.myclinic.app.data.files.IosFileVault
import com.myclinic.app.data.files.StagedFileVault
import com.myclinic.app.data.local.IosLocalDatabase
import com.myclinic.app.data.local.LocalDatabase
import com.myclinic.app.data.push.IosPushChannel
import com.myclinic.app.data.push.PushChannel
import com.myclinic.app.data.security.KeychainSessionManager
import com.myclinic.app.data.security.SecureStore
import com.myclinic.app.data.sync.IosSyncTrigger
import com.myclinic.app.data.sync.SyncTrigger
import com.myclinic.app.di.AppConfig
import com.myclinic.app.di.sharedDataModule
import com.myclinic.app.di.viewModelModule
import com.myclinic.app.security.AppLockManager
import com.myclinic.app.security.IosUptimeClock
import com.myclinic.app.security.UptimeClock
import com.myclinic.app.ui.files.FileRenderer
import com.myclinic.app.ui.files.IosFileRenderer
import com.myclinic.app.ui.root.MyClinicRoot
import com.myclinic.app.ui.theme.MyClinicTheme
import io.github.jan.supabase.auth.SessionManager
import org.koin.compose.KoinContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIViewController

/** iPhone's own pieces: Keychain login, protected files and database, foreground sync, Face ID lock. */
private fun iosModule(config: AppConfig, secureStore: SecureStore) = module {
    single { config }
    single { secureStore }
    single<SessionManager> { KeychainSessionManager(get()) }
    single<LocalDatabase> { IosLocalDatabase.open() }
    single<StagedFileVault> { IosFileVault() }
    single<PushChannel> { IosPushChannel() }
    single<SyncTrigger> { IosSyncTrigger(lazy { get() }) }
    single<UptimeClock> { IosUptimeClock }
    single { AppLockManager(get()) }
    single<FileRenderer> { IosFileRenderer() }
    single<RecordPdfWriter> { IosRecordPdfWriter() }
    single<ResearchZipWriter> { IosResearchZipWriter() }
}

/** Called once by the iPhone app (iosApp/MyClinicApp.swift) when it starts. */
object MyClinicIos {
    private var started = false

    fun start(supabaseUrl: String, supabaseAnonKey: String, secureStore: SecureStore) {
        if (started) return
        started = true
        startKoin { modules(iosModule(AppConfig(supabaseUrl, supabaseAnonKey), secureStore), sharedDataModule, viewModelModule) }
        cleanUpExports() // exported files never stay on the phone
        val lock = KoinPlatform.getKoin().get<AppLockManager>()
        val center = NSNotificationCenter.defaultCenter
        // Face ID / passcode at start and after 5 minutes away.
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ -> lock.onForeground() }
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ -> lock.onBackground() }
    }

    /** An email link (verify / reset password) opened the app. */
    fun handleUrl(url: String) {
        KoinPlatform.getKoin().get<AuthRepository>().handleDeepLink(url)
    }
}

/** The whole app as one iPhone screen; every touch counts as "in use" for the app lock. */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val lock = KoinPlatform.getKoin().get<AppLockManager>()
    KoinContext {
        MyClinicTheme {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            lock.onUserInteraction()
                        }
                    }
                },
            ) {
                MyClinicRoot()
            }
        }
    }
}
