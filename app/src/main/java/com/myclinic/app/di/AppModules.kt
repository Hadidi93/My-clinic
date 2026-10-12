package com.myclinic.app.di

import com.myclinic.app.BuildConfig
import com.myclinic.app.data.audit.AuditRepository
import com.myclinic.app.data.audit.SupabaseAuditRepository
import com.myclinic.app.data.auth.AuthRedirects
import com.myclinic.app.data.auth.AuthRepository
import com.myclinic.app.data.auth.SupabaseAuthRepository
import com.myclinic.app.data.consults.ConsultRepository
import com.myclinic.app.data.consults.SupabaseConsultRepository
import com.myclinic.app.data.doctor.DoctorRepository
import com.myclinic.app.data.doctor.SupabaseDoctorRepository
import com.myclinic.app.data.export.PdfExporter
import com.myclinic.app.data.export.ResearchExporter
import com.myclinic.app.data.facilities.FacilityRepository
import com.myclinic.app.data.facilities.SupabaseFacilityRepository
import com.myclinic.app.data.files.ClinicalFileStore
import com.myclinic.app.data.files.PickedFileReader
import com.myclinic.app.data.local.LocalDatabase
import com.myclinic.app.data.media.ImageCompressor
import com.myclinic.app.data.notifications.NotificationRepository
import com.myclinic.app.data.notifications.SupabaseNotificationRepository
import com.myclinic.app.data.push.PushManager
import com.myclinic.app.data.records.CachedPatientRepository
import com.myclinic.app.data.records.PatientRepository
import com.myclinic.app.data.security.EncryptedSessionManager
import com.myclinic.app.data.staff.StaffRepository
import com.myclinic.app.data.staff.SupabaseStaffRepository
import com.myclinic.app.data.sync.SyncEngine
import com.myclinic.app.data.sync.SyncScheduler
import com.myclinic.app.data.sync.SyncWorker
import com.myclinic.app.security.AppLockManager
import com.myclinic.app.ui.admin.AdminApprovalsViewModel
import com.myclinic.app.ui.audit.AccessLogViewModel
import com.myclinic.app.ui.auth.ForgotPasswordViewModel
import com.myclinic.app.ui.auth.LoginViewModel
import com.myclinic.app.ui.auth.ResetPasswordViewModel
import com.myclinic.app.ui.auth.SignUpViewModel
import com.myclinic.app.ui.consults.ConsultDetailViewModel
import com.myclinic.app.ui.consults.ConsultsViewModel
import com.myclinic.app.ui.consults.NewConsultViewModel
import com.myclinic.app.ui.consults.NewReferralViewModel
import com.myclinic.app.ui.consults.NotificationsViewModel
import com.myclinic.app.ui.consults.ReferralsViewModel
import com.myclinic.app.ui.files.FileViewerViewModel
import com.myclinic.app.ui.home.HomeViewModel
import com.myclinic.app.ui.patients.EntryFormViewModel
import com.myclinic.app.ui.patients.InvestigationViewModel
import com.myclinic.app.ui.patients.PatientDetailViewModel
import com.myclinic.app.ui.patients.PatientListViewModel
import com.myclinic.app.ui.profile.ProfileViewModel
import com.myclinic.app.ui.research.ResearchExportViewModel
import com.myclinic.app.ui.root.RootViewModel
import com.myclinic.app.ui.staff.StaffHomeViewModel
import com.myclinic.app.ui.staff.StaffRequestViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The single Supabase client the whole app shares. */
fun createAppSupabaseClient(sessionManager: EncryptedSessionManager): SupabaseClient =
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

/** Supabase, the encrypted offline copy, files, push, sync and the app lock: one of each for the whole app. */
val dataModule = module {
    singleOf(::EncryptedSessionManager)
    single { createAppSupabaseClient(get()) }
    single { LocalDatabase.open(get()) }
    single { get<LocalDatabase>().dao() }

    singleOf(::SupabaseAuthRepository) { bind<AuthRepository>() }
    singleOf(::SupabaseDoctorRepository) { bind<DoctorRepository>() }
    singleOf(::SupabaseFacilityRepository) { bind<FacilityRepository>() }
    singleOf(::SupabaseStaffRepository) { bind<StaffRepository>() }
    singleOf(::SupabaseConsultRepository) { bind<ConsultRepository>() }
    singleOf(::SupabaseAuditRepository) { bind<AuditRepository>() }
    singleOf(::SupabaseNotificationRepository) { bind<NotificationRepository>() }
    singleOf(::CachedPatientRepository) { bind<PatientRepository>() }

    singleOf(::ClinicalFileStore)
    singleOf(::PickedFileReader)
    singleOf(::ImageCompressor)
    singleOf(::PdfExporter)
    singleOf(::ResearchExporter)
    singleOf(::PushManager)
    singleOf(::SyncEngine)
    singleOf(::SyncScheduler)
    singleOf(::AppLockManager)
    workerOf(::SyncWorker)
}

/** One entry per screen's view model. */
val viewModelModule = module {
    viewModelOf(::RootViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::SignUpViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::ResetPasswordViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::AdminApprovalsViewModel)
    viewModelOf(::PatientListViewModel)
    viewModelOf(::PatientDetailViewModel)
    viewModelOf(::EntryFormViewModel)
    viewModelOf(::InvestigationViewModel)
    viewModelOf(::FileViewerViewModel)
    viewModelOf(::AccessLogViewModel)
    viewModelOf(::ResearchExportViewModel)
    viewModelOf(::ConsultsViewModel)
    viewModelOf(::ConsultDetailViewModel)
    viewModelOf(::NewConsultViewModel)
    viewModelOf(::NewReferralViewModel)
    viewModelOf(::ReferralsViewModel)
    viewModelOf(::NotificationsViewModel)
    viewModelOf(::StaffHomeViewModel)
    viewModelOf(::StaffRequestViewModel)
}

val appModules = listOf(dataModule, viewModelModule)
