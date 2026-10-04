# Architecture

## The pieces

```
┌──────────────── Android app (Kotlin) ────────────────┐
│  ui/        Jetpack Compose screens + ViewModels      │  what you see
│  data/      Repositories: talk to Supabase            │  fetching / saving
│  di/        Hilt: wires the pieces together           │
└──────────────┬────────────────────────────────────────┘
               │ uses
┌──────────────▼─────────────┐
│  core/domain  (pure Kotlin)│  models, validation, permission rules
└────────────────────────────┘  (unit-tested, no Android code)

               │ HTTPS (TLS) only
┌──────────────▼──────────────── Supabase (EU, Frankfurt) ─────┐
│  Auth      email + password, email verification, reset        │
│  Postgres  tables + Row-Level Security + audit triggers       │
│  Storage   private buckets (photos, licences, later results)  │
└───────────────────────────────────────────────────────────────┘
```

- **MVVM.** Each screen has a ViewModel that holds its state (what was typed,
  loading, errors). The screen just draws that state. Screens survive rotation
  and language switches without losing input.
- **Repositories** (`AuthRepository`, `DoctorRepository`) are interfaces. The
  Supabase versions are used in the app, and fakes are used in tests.
- **Root state machine** (`RootViewModel`) decides which flow to show:
  - signed out
  - reset password
  - complete profile
  - main app

  Screens never have to juggle this.
- **Offline first (Phase 2).** Patient screens read only from an encrypted
  copy on the phone, so they are instant and work without signal in theatre
  or clinic.

## Offline sync, in plain words

```
 screen ──save──▶ local copy (Room + SQLCipher) ──▶ outbox ──(when online)──▶ Supabase
 screen ◀─shows── local copy ◀──────── pull changes / purge lost access ◀──── Supabase
```

- **Encrypted cache:** one generic table (`cached_rows`) holds every row as
  the server's JSON. It is encrypted with SQLCipher (AES-256), using a random
  password that is itself encrypted by an Android Keystore key.
- **Outbox:** every save writes the local copy and a "pending change" in one
  step. `SyncWorker` (WorkManager) uploads them in order as soon as there is
  a connection, even if the app was closed, and again every 15 minutes.
- **Conflicts:** a row with an un-uploaded local change keeps the local
  version; otherwise the latest server version wins (last write wins per
  entry). Two doctors editing the *same* entry at the same moment is the
  only case where one edit replaces the other; separate entries never clash.
- **Refused changes** (e.g. co-management ended while offline) are kept
  aside and shown with a "Discard" button, never silently dropped.
- **Lost access:** after each sync the phone deletes patients it may no
  longer edit. Signing out deletes the whole offline copy (with a warning if
  anything is unsynced). If another doctor signs in on the same phone, the
  previous doctor's cache is wiped first.
- **Files (photos, PDFs):** a file added offline is kept on the phone
  encrypted (AES-256-GCM, key protected by Android Keystore) and uploaded by
  the sync just before its attachment entry, so an entry never points to a
  missing file. Opened files are held in memory only, never saved to the
  gallery or shared with other apps. Photos are re-encoded first, which
  removes GPS and camera details.
- **What stays online-only:** consult views (Phase 4), the previous
  owner's read-only history after a transfer, and the whole lab/radiology
  staff side (their inbox is never stored on the phone).

## Notifications, in plain words

```
 something happens ──▶ database trigger writes a notification row (kind + id only)
                         │
                         ├──▶ the app's bell list reads it (when open)
                         └──▶ Database Webhook ──▶ Edge Function send-push ──▶ Firebase ──▶ phone
                                                    (data only: {"kind": ...})
 phone ──▶ shows a translated generic text ("New consult request") ──▶ tap ──▶ app loads details after sign-in
```

## Decision: Supabase vs Firebase

| | Supabase (chosen) | Firebase |
|---|---|---|
| Data model | Relational (Postgres). Fits patient → sections → consults naturally. | Documents. Relations need duplication. |
| Access rules | SQL Row-Level Security, unit-tested (77 checks). Per-section, time-limited, revocable sharing is a few lines of SQL. | Security Rules language. Possible, but harder to express and test. |
| Audit | Triggers the app cannot skip. | Needs Cloud Functions; reads can't be logged. |
| Data residency | Open source, so it can move to an Egyptian or hospital server later without rewriting. | Google Cloud only. |
| Offline | Built by us (Room + sync), with more control. | Built in. |
| Push | Uses Firebase Cloud Messaging only to deliver notifications (no patient data). | Built in. |
| Android SDK | Community Kotlin SDK (supabase-kt), actively maintained. | Official and very mature. |

## Security measures in Phase 1

| Measure | Where |
|---|---|
| All access rules enforced server-side | `supabase/migrations/*.sql`, tested in `supabase/tests/` |
| Unverified doctors can't share or receive data | `can_read_section`, `create_consult`, `search_doctors` |
| Doctors can't change their own role/verification | column-level grants on `doctors` |
| Licence change ⇒ re-verification | trigger `doctors_reverify` |
| Login tokens encrypted on the phone | `EncryptedSessionManager` + `KeystoreCipher` (AES-256-GCM, Android Keystore) |
| PKCE for email links | `SupabaseModule` (`FlowType.PKCE`) |
| HTTPS only | `usesCleartextTraffic="false"` |
| No cloud/device backups of app data | `allowBackup="false"`, `data_extraction_rules.xml` |
| Screenshot blocking | `SecureScreen { }` (used on admin approvals; every patient screen from Phase 2) |
| Photo metadata (GPS) stripped before upload | `ImageCompressor` re-encodes images |
| Private file storage, short-lived links | storage policies + signed URLs (5–10 min) |
| Append-only audit log | `audit_log`; no insert/update/delete permission for anyone |

| Encrypted offline database | `LocalDatabase` (SQLCipher) |
| Encrypted offline files, in-memory viewing | `ClinicalFileStore`, `FileViewerScreen` |
| Lab/radiology staff see the minimum | `staff_*` functions in migration 6; no table access |
| Record views audit-logged | `PatientRepository.logView` → `log_record_view` |

## Security measures added in Phase 5

| Measure | Where |
|---|---|
| App lock: fingerprint / face / phone PIN at start and after 5 min without use | `AppLockManager`, `LockScreen`, `LockPolicy` |
| Phones without a screen lock must set one | `LockScreen` |
| Access log per patient and app-wide activity log | migration 10, `AccessLogScreen` |
| Consults share name, age and sex unless anonymized | migration 11, `PatientIdentityMasker` |
| Duplicate warning in history lists | `DuplicateCheck`, `EntryFormViewModel` |
| PDF export logged before the file is made; main doctor only; temporary file deleted at next start | `log_record_export`, `PdfExporter` |
| Tap-jacking protection (touches ignored under overlays) | `MainActivity` |
| Release build shrunk and obfuscated (R8); logging stripped; test-build crash screen off | `proguard-rules.pro`, `CrashReporter` |
| The release build is also launch-tested on an emulator in CI (catches broken shrink rules) | `.github/workflows/ci.yml` |

Decided not to do (for now):
- **Certificate pinning.** Supabase rotates its certificates; a pinned app
  would stop working without warning until updated. TLS with the system's
  trusted certificates, HTTPS-only, is used instead.
- **Root / Play Integrity checks.** They mainly protect paid content; our data
  is protected by the server rules, encryption and the app lock. Can be added
  if a hospital requires it.
- **Penetration test.** Recommended before go-live with real patients by an
  independent tester; the automated security tests are a starting point, not a replacement.
