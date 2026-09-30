# My Clinic

An Android app for doctors to manage patient records, request investigations,
and consult colleagues securely. It supports Arabic and English and runs on
Android 10+.

**Stack:**
- Kotlin, Jetpack Compose (Material 3), MVVM, Hilt, Coroutines/Flow
- Supabase: Postgres, Auth, Storage, Row-Level Security (EU region)
- Room for the offline cache (from Phase 2)

## Status

| Phase | Scope | Status |
|---|---|---|
| 1 | Project setup, database schema + ER diagram, auth, doctor profile, admin approval | ✅ approved |
| 2 | Patients and all record sections, encrypted offline cache + sync, timeline, vitals chart, search; referral rules (database) | ✅ built, awaiting review |
| 3 | Investigation requests, file uploads, camera | ⏳ |
| 4 | Consultations, referrals (co-management / transfer) screens, notifications | ⏳ (database rules already written and tested) |
| 5 | Security hardening, audit views, testing, Play Store release | ⏳ |

## Where things are

```
app/                    Android app (screens, ViewModels, Supabase access)
core/domain/            Plain Kotlin: models, validation, permission rules + tests
supabase/migrations/    Database tables and security rules (SQL)
supabase/tests/         Database security tests
docs/SETUP.md           How to set up Supabase and run the app (start here)
docs/DATABASE.md        ER diagram and security model
docs/ARCHITECTURE.md    How the app is organised; Supabase vs Firebase
docs/COMPLIANCE.md      Health-data law (Egypt 151/2020, GDPR, HIPAA) + backend checklist
```

## Quick start

See **[docs/SETUP.md](docs/SETUP.md)**.

⚠️ Use **fake demo patients only** until Phase 5 and the legal steps in
[docs/COMPLIANCE.md](docs/COMPLIANCE.md) are complete.
