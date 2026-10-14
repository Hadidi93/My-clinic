package com.myclinic.app.di

import com.myclinic.app.BuildConfig
import com.myclinic.app.data.export.PdfExporter
import com.myclinic.app.data.export.RecordPdfWriter
import com.myclinic.app.data.export.ResearchExporter
import com.myclinic.app.data.export.ResearchZipWriter
import com.myclinic.app.data.files.AndroidFileVault
import com.myclinic.app.data.files.PickedFileReader
import com.myclinic.app.data.files.StagedFileVault
import com.myclinic.app.data.local.AndroidLocalDatabase
import com.myclinic.app.data.local.LocalDatabase
import com.myclinic.app.data.media.ImageCompressor
import com.myclinic.app.data.push.PushChannel
import com.myclinic.app.data.push.PushManager
import com.myclinic.app.data.security.EncryptedSessionManager
import com.myclinic.app.data.sync.SyncScheduler
import com.myclinic.app.data.sync.SyncTrigger
import com.myclinic.app.data.sync.SyncWorker
import com.myclinic.app.security.AndroidUptimeClock
import com.myclinic.app.security.AppLockManager
import com.myclinic.app.security.UptimeClock
import com.myclinic.app.ui.files.AndroidFileRenderer
import com.myclinic.app.ui.files.FileRenderer
import io.github.jan.supabase.auth.SessionManager
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Android's own pieces: Keystore-encrypted login and database, Firebase push, WorkManager sync, PDF writer. */
val androidModule = module {
    single { AppConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) }
    singleOf(::EncryptedSessionManager) { bind<SessionManager>() }
    single<LocalDatabase> { AndroidLocalDatabase.open(get()) }
    singleOf(::AndroidFileVault) { bind<StagedFileVault>() }
    singleOf(::PickedFileReader)
    singleOf(::ImageCompressor)
    singleOf(::PdfExporter) { bind<RecordPdfWriter>() }
    singleOf(::ResearchExporter) { bind<ResearchZipWriter>() }
    singleOf(::PushManager) { bind<PushChannel>() }
    singleOf(::SyncScheduler) { bind<SyncTrigger>() }
    single<UptimeClock> { AndroidUptimeClock }
    singleOf(::AppLockManager)
    singleOf(::AndroidFileRenderer) { bind<FileRenderer>() }
    workerOf(::SyncWorker)
}

val appModules = listOf(androidModule, sharedDataModule, viewModelModule)
