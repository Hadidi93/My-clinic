# Setup: run My Clinic yourself

Doing this the first time takes about 30–45 minutes. You need:

- a computer (Windows, Mac or Linux)
- an Android phone with Android 10 or newer, or the emulator built into Android Studio

> **Use fake demo patients only** until Phase 5 security hardening is
> complete and the legal steps in [COMPLIANCE.md](COMPLIANCE.md) are done.

---

## 1. Create the Supabase project (the backend)

1. Go to <https://supabase.com> and sign up. Turn on two-factor authentication
   for your Supabase account (Account → Security).
2. Click **New project**:
   - **Name:** `my-clinic-dev`
   - **Database password:** use a long random one and store it in a password manager
   - **Region:** **Central EU (Frankfurt)**, the EU region you chose
3. Wait about 2 minutes for it to start.

## 2. Create the database tables and security rules

1. In Supabase, open **SQL Editor** → **New query**.
2. Open `supabase/migrations/20260930000100_doctors_and_audit.sql` from this
   repository, copy all of it, paste it into the editor and click **Run**.
3. Do the same for the other migration files, **in filename order**:
   `…0200_patient_records.sql`, `…0300_consultations.sql`,
   `…0400_storage_profile_files.sql`, `…0500_referrals_and_sync.sql`,
   `…0600_investigations_and_files.sql`.
   (Already set up Phase 2? Just run the new `…0600` file. Supabase may warn
   that it "includes destructive operations": that is expected, because it
   replaces a few security rules with stricter versions. No data is deleted.)

Each should end with "Success. No rows returned".

> **Error `type "verification_status" already exists` (or any "already
> exists")?** That file was already run, and running it twice is not allowed.
> To see which files are installed, run `supabase/dev/check_migrations.sql`.
> It only reads, so it is always safe. Then run only the files marked ❌,
> in order.
> After all files are installed, run `supabase/dev/check_rules.sql` to
> confirm every table is protected: all 22 rows should say ✅.
> If a run stopped halfway and a file keeps failing, the development
> database can be wiped with `supabase/dev/reset_dev_database.sql` (fake
> data only, **never** on real patients), then all files run again from
> the first.

*(Alternative for later: install the Supabase CLI and run
`supabase link` then `supabase db push`, which applies all migrations in one go.)*

## 3. Configure sign-in (Authentication settings)

In Supabase, open **Authentication**:

1. **Sign In / Providers → Email**
   - Enable Email provider: **on**
   - Confirm email: **on** (email verification)
   - Minimum password length: **10**
   - Password requirements: **letters and digits**
   - Secure email change: **on**
2. **URL Configuration**
   - Site URL: `myclinic://auth-callback`
   - Redirect URLs → **Add URL**: `myclinic://auth-callback/**`

   This lets the "verify email" and "reset password" links open the app.
3. **Emails → SMTP settings** *(before real users)*: Supabase's built-in email
   is rate-limited and meant for testing only. Connect a proper email provider
   (for example Resend, Postmark or Amazon SES) before inviting colleagues.
4. **Rate Limits:** leave the defaults.

## 4. Get the app keys

In Supabase: **Project Settings → API**. Copy:

- **Project URL** (looks like `https://abcd1234.supabase.co`)
- **anon public** key

The anon key is designed to be public, because the security rules protect the data.
**Never** put the `service_role` key in the app or in git.

## 5. Open the app in Android Studio

1. Install **Android Studio** (latest stable) from <https://developer.android.com/studio>.
2. **File → Open…** → choose this repository folder. Wait for "Gradle sync" to finish,
   which downloads everything (several minutes the first time).
3. In the project folder, copy `local.properties.example` to `local.properties`.
   Android Studio usually creates `local.properties` with `sdk.dir` already,
   in which case just add the two lines. Fill in:
   ```
   SUPABASE_URL=https://abcd1234.supabase.co
   SUPABASE_ANON_KEY=eyJhbGciOi...
   ```
   `local.properties` is git-ignored, so your keys stay on your computer.
4. Connect your phone with USB debugging on, or create an emulator
   (Device Manager → **+** → Pixel, Android 14). Then press the green **Run ▶** button.

## 6. Make yourself the administrator

The first admin has to be set directly in the database; after that, admins
approve everyone else inside the app.

1. In the app, **Create account** with your email, verify it from the email
   link **on the phone**, then complete your profile.
2. In Supabase **SQL Editor**, run (with your email):
   ```sql
   update public.doctors
      set role = 'admin', verification_status = 'verified', verified_at = now()
    where email = 'you@example.com';
   ```
3. Close and reopen the app. A shield icon appears at the top of the home
   screen, which opens **Doctor approvals**.

## 7. Test checklist for Phase 1

Use a second email address (for example a Gmail `+test` alias:
`you+demo1@gmail.com`) to play a second doctor.

| # | Try this | Expected |
|---|---|---|
| 1 | Create account with password `short` | "Use at least 10 characters" |
| 2 | Create account properly | "Check your email" screen; email arrives |
| 3 | Sign in before verifying | "Please verify your email first" |
| 4 | Tap the email link on the phone | App opens and shows "Complete your profile" |
| 5 | Save with an empty licence number | Field is marked in red |
| 6 | Complete profile, add photo and licence photo | "Profile saved"; home shows "Verification pending" |
| 7 | Switch to العربية | Whole app turns Arabic and right-to-left |
| 8 | As admin: open Doctor approvals | The new doctor is listed; licence photo opens |
| 9 | Try to screenshot the approvals screen | Blocked (black image or "can't take screenshot") |
| 10 | Approve the doctor, reopen their app | Pending banner is gone |
| 11 | Change licence number, save | Account goes back to "Verification pending" |
| 12 | Sign out → **Forgot password?** → open link on phone | "Choose a new password" screen; new password works |
| 13 | Turn on airplane mode, try to sign in | "No connection" message |
| 14 | Turn phone to dark mode | App follows, with readable contrast |

## 7b. Test checklist for Phase 2 (patients)

Use **fake demo patients only**.

| # | Try this | Expected |
|---|---|---|
| 1 | Home → **Add patient**, type only a name, **Save and open** | Record opens in a few seconds |
| 2 | Add an allergy with severity "Life-threatening" | Red **ALLERGIES** banner on the record and every form |
| 3 | Add chronic diseases: pick "Hypertension" from the chips, then type a rare one yourself | Both listed under Past medical history |
| 4 | Add 3 examinations over different times with pulse and BP | **Vitals** tab shows a trend; tap a dot to see its value; out-of-range dots are marked |
| 5 | Add a surgery with a planned date next week, tick some checklist items | Shows "3 of 10 done"; the operation appears on Home under Upcoming operations |
| 6 | Add a post-op follow-up to that surgery | Listed under Post-op follow-up and on the **Timeline** |
| 7 | Open **Timeline** | Everything in date order, newest first |
| 8 | Patient list: search by part of the name, file number, or diagnosis; filter by tag and "Today" | List narrows correctly; Arabic names match with or without hamza (أحمد / احمد) |
| 9 | Airplane mode on → add an entry → close the app → airplane mode off → open the app | "Offline"/"waiting to sync" banner, then it disappears; the entry is in Supabase (Table Editor) |
| 10 | Edit an entry → **Mark as entered in error** | Entry disappears from the record; Supabase shows `deleted_at` set |
| 11 | Delete the patient (⋮ menu) → list → trash icon | Patient shows as Deleted; restore works |
| 12 | Try a screenshot on any patient screen | Blocked |
| 13 | Airplane mode on, add an entry, then Profile → Sign out | Warning about unsynced changes |

## 7c. Test checklist for Phase 3 (investigations and files)

You need two accounts: your doctor account, and a second one (for example
`you+lab@gmail.com`) to play a lab technician. **Fake demo patients only.**

**Set up the lab account**

1. Sign up with the second email. In **Complete your profile**, choose
   **Lab / radiology staff**, fill in job title and staff ID, then
   **Add department**: type "Lab", name "Demo Main Lab", your hospital.
2. Upload any photo as the staff ID card and save. The app shows
   "Department inbox" with "Verification pending".
3. As admin (your doctor account): **Doctor approvals** shows the new account
   marked "Lab/radiology staff · Demo Main Lab". Approve it.

**Try it out**

| # | Try this | Expected |
|---|---|---|
| 1 | Doctor: open a demo patient → Investigations → **Request**. Type Lab, pick "CBC", urgency Urgent, **Send to department: Demo Main Lab**, save | Request listed as "Requested · Urgent" |
| 2 | Lab account: tap the refresh button (↻) in the inbox | The request appears with the patient's name, age, sex; urgent ones are marked |
| 3 | Lab: open it | Allergies banner, requested tests, the doctor's notes. No other parts of the record |
| 4 | Lab: **Sample taken**, then fill the Hb/WBC values (one below the normal range), take a photo of any paper, **Send result to the doctor** | Back to the inbox; the request now says "Result ready" |
| 5 | Doctor: Home screen | "Results to review" shows 1 and lists the request |
| 6 | Doctor: open it | The values (low ones in red with ↓), the photo opens full screen and zooms |
| 7 | Doctor: tap **Open** on the result | "Uploaded by the department": can't be edited, only marked as entered in error |
| 8 | Doctor: **Mark result as reviewed** | Lab inbox: the request disappears (the lab no longer has access) |
| 9 | Doctor: **Add result** on the patient (no request), type a CBC with an Hb value, attach a PDF | Result listed; **Trends** tab now offers "Hb" next to the vital signs |
| 10 | Airplane mode on: add a post-op follow-up with a wound photo, then airplane mode off | The photo uploads with the next sync and opens from the follow-up |
| 11 | Try a screenshot on the inbox, a request, or a file | Blocked |
| 12 | Lab account: look for patients or the doctor directory | Not available: staff only see their inbox |

## 8. Run the automated tests

- **Rules and ViewModel tests:** in Android Studio open the Gradle panel →
  `MyClinic → Tasks → verification → test`, or in a terminal:
  `./gradlew :core:domain:test :app:testDebugUnitTest`
- **Database security tests:** need a local PostgreSQL 15+ install:
  `PGHOST=localhost PGUSER=postgres supabase/tests/run_local.sh`
- Both also run automatically on GitHub for every push (the **Actions** tab).
  Each run also produces a downloadable debug APK under **Artifacts**. To have
  that APK talk to your Supabase project, add repository secrets
  `SUPABASE_URL` and `SUPABASE_ANON_KEY` (GitHub → Settings → Secrets and
  variables → Actions).
