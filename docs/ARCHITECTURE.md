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
- **Offline (Room) arrives in Phase 2** with patient records, which are the
  data worth caching. The local database will be encrypted with SQLCipher,
  using a key protected by Android Keystore (`KeystoreCipher` is already
  here for the login session).

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

Coming in Phase 5:
- biometric/PIN lock with auto-lock
- encrypted Room database
- certificate pinning
- release hardening (R8 obfuscation review, Play Integrity)
- penetration-test checklist
