# Health-data law: what likely applies and what the backend needs

> This is an engineering summary to help you brief a lawyer. It is **not
> legal advice.** Before real patients go into the app, have an Egyptian
> data-protection lawyer review it. Also check the current status of
> the Executive Regulations of Law 151/2020 and of any licensing procedures
> the Personal Data Protection Center (PDPC) has published.

## Which laws likely apply

| Law | Applies when | For My Clinic |
|---|---|---|
| **Egypt Personal Data Protection Law No. 151 of 2020** | Personal data of people in Egypt is processed | **Yes, the main law.** Health data counts as *sensitive personal data*, the strictest category. |
| **Egyptian medical confidentiality rules** (Code of Medical Ethics, Ministry of Health / Medical Syndicate rules) | Any doctor in Egypt | **Yes.** Confidentiality and record-keeping duties exist regardless of the app. |
| **GDPR (EU)** | The controller or processor is established in the EU, **or** you offer services to / process data of people in the EU | **Probably not** for Egyptian patients just because the server is in Frankfurt. **Yes** if you treat or consult on EU residents, or open an EU entity. Supabase itself (as processor) follows GDPR for EU hosting. |
| **HIPAA (USA)** | You are a US "covered entity" (US provider/insurer) or its business associate | **Not** for an Egyptian practice. Only relevant if US providers use the app for US patients. Then you need Supabase's HIPAA add-on and a signed BAA. |
| Other Arab states (e.g. Saudi PDPL, UAE health-data law) | If doctors or patients there use the app | Plan for it before expanding; some require in-country hosting. |

## Law 151/2020: key obligations in plain words

1. **Explicit consent** from the patient before processing sensitive data,
   especially before sharing it with another doctor.
   → Built in: consults can't be created without a consent checkbox and date,
   and both are stored with each consult.
2. **Licence / permit from the PDPC** to process sensitive data, and a separate
   permit for **cross-border transfer**. Hosting in Frankfurt means data leaves
   Egypt. Transfer is allowed only to a country with at least equivalent
   protection, with PDPC approval.
   → **Action for you:** apply before launch, or move hosting to Egypt
   (Supabase can be self-hosted; our migrations are portable).
3. **Data Protection Officer (DPO)** appointed and registered with the PDPC.
4. **Security measures** proportionate to the sensitivity.
   → Encryption in transit and at rest, access control and audit (see below).
5. **Breach notification** to the PDPC within **72 hours** of discovering a
   breach, and informing affected people.
   → Needs a written incident plan (Phase 5 deliverable).
6. **Data-subject rights:** patients may request access, correction and, in
   some cases, erasure.
   → Access/correction are app features. Erasure must be balanced against
   medical-record retention duties, which is why we soft-delete.
7. **Purpose limitation and minimisation:** collect only what is needed.
   → Quick-add requires only a name. Anonymized consults hide identifiers.
8. **Retention:** keep records only as long as needed, but no shorter than
   medical-record rules require. Agree a retention period with your lawyer.

## What the backend setup needs (checklist)

**Supabase project**
- [x] Region: EU (Frankfurt), as chosen
- [ ] Paid plan (Pro or higher) before real data: daily backups, no pausing
- [ ] **Point-in-time recovery** add-on (restore to any minute)
- [ ] Sign Supabase's **Data Processing Agreement (DPA)** (dashboard → Legal documents)
- [ ] HIPAA add-on + BAA *only if* US patients / US providers are involved
- [ ] **SSL enforcement** on (Database settings), so all DB connections use TLS
- [ ] **Network restrictions** on direct database access (Database settings)
- [ ] Two-factor authentication for every person with dashboard access; keep that list short
- [ ] `service_role` key never leaves the server side; rotate if exposed
- [ ] Custom SMTP provider for auth emails (in the EU if possible)
- [ ] Separate projects for development (fake data) and production (real data)

**Already built in this codebase**
- [x] Row-Level Security on every table; 77 automated security tests
- [x] Encryption in transit: HTTPS only, no cleartext traffic
- [x] Encryption at rest: Supabase encrypts disks (AES-256). Login tokens are
      encrypted on the phone. The local patient cache will be encrypted
      (SQLCipher, Phase 2).
- [x] Append-only audit log of create/update/delete/share/revoke/verify/view
- [x] Consent checkbox + date required before any sharing
- [x] Time-limited, revocable sharing; anonymization option
- [x] Unverified doctors cannot send or receive shared data
- [x] No backups of app data to Google Drive / device transfer
- [x] Photo metadata (GPS) stripped before upload

**To do in later phases**
- [x] Push notifications with **no patient data** in the text: only the kind of event is sent
- [x] No patient data in logs, crash reports or analytics: no analytics/crash SDKs; release builds strip logging; the test-build crash screen is disabled in release
- [x] Biometric/PIN lock at start and after 5 minutes without use; phones without a screen lock are asked to set one
- [ ] Optional extra column encryption for national ID (Supabase Vault/pgsodium). Not done: data is already encrypted on disk and protected by row-level security, and column encryption would break search. Revisit with your lawyer
- [x] Access log per patient (main doctor) and app-wide activity log (admin) in the app. Retention: the log is never deleted by the app; decide a retention period with your lawyer
- [x] PDF export of a record: main doctor only, with a warning, written to the audit log before the file is made
- [x] Tap-jacking protection, screenshot blocking (FLAG_SECURE), app data excluded from backups
- [ ] Written policies: privacy notice (Arabic + English), consent form text,
      incident-response plan, retention schedule, DPO contact, with your lawyer.
      A draft privacy policy is in docs/PRIVACY_POLICY.md
