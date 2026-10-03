# Publishing on Google Play (closed testing first)

Allow about 2 hours of your time, plus Google's waiting times. You need:
a Google account, a payment card (one-time US$25), your ID for Google's
identity check, and the privacy policy (docs/PRIVACY_POLICY.md) reviewed.

> ⚠️ Real patient data only after the legal steps in docs/COMPLIANCE.md and
> the production Supabase project (section 6 below) are done.

## 1. Create your upload key (once, on your laptop)

The upload key proves that new versions really come from you. Keep it and its
passwords safe (e.g. in a password manager). If you lose it, Google can reset
it, but that takes days.

1. Open **PowerShell** and run (Android Studio includes `keytool`):
   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
     -keystore $HOME\my-clinic-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
   It asks for a password (twice) and your name/organisation. Use the same
   password when it asks for the key password.
2. Turn the file into text for GitHub:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\my-clinic-upload.jks")) | Set-Clipboard
   ```
3. GitHub → repository → **Settings → Secrets and variables → Actions → New repository secret**, four times:
   | Name | Value |
   |---|---|
   | `RELEASE_KEYSTORE_BASE64` | paste (Ctrl+V) |
   | `RELEASE_KEYSTORE_PASSWORD` | your password |
   | `RELEASE_KEY_ALIAS` | `upload` |
   | `RELEASE_KEY_PASSWORD` | your password |
4. GitHub → **Actions → CI → Run workflow**. When it finishes, the run has an
   artifact **my-clinic-play-store-bundle** containing `app-release.aab`.
   Download and unzip it: this is the file you upload to Google Play.
   (Each run makes a new version number automatically.)

## 2. Google Play developer account

1. <https://play.google.com/console> → sign up. Choose **Personal** (your name)
   or **Organisation** (needs a D-U-N-S number for a company/clinic).
2. Pay the US$25 fee, complete identity verification (can take a few days).
3. New personal accounts must run a **closed test with at least 12 testers for
   14 days** before they can publish publicly. That suits us: colleagues test first.

## 3. Create the app

1. **Create app** → name *My Clinic*, default language English (add Arabic
   later under *Store listing → Manage translations*), **App**, **Free**.
2. **Policy → App content**, answer each item:
   - **Privacy policy:** a public web address of your policy. Simplest: create
     a page with Google Sites (sites.google.com), paste docs/PRIVACY_POLICY.md,
     publish, copy the link.
   - **App access:** "All or some functionality is restricted" → add a demo
     doctor account (email + password) for Google's reviewers, with fake patients only.
   - **Ads:** No ads.
   - **Content rating:** questionnaire → category *Reference, News or Educational*
     / *Utility*; answer "No" to violence etc. Result: Everyone/3+.
   - **Target audience:** 18 and over.
   - **Health apps:** declare *Medical: clinical / patient records*. Say it is
     for licensed healthcare professionals and is not a medical device.
   - **Data safety:** use the answers in section 5.
3. **Store listing:** short description, full description, app icon (512×512),
   feature graphic (1024×500), at least 2 phone screenshots (use fake patients only).

## 4. Closed testing

1. **Testing → Closed testing → Create track** (e.g. "Doctors").
2. **Testers:** create an email list with your colleagues' Google accounts (12+).
3. **Create new release** → upload `app-release.aab` → release notes → **Review → Start rollout**.
4. Share the **opt-in link** with testers. They install from Google Play.
5. For each new version: run CI, upload the new `.aab` to the same track.
6. After 14 days with 12+ testers: **Production → Apply for access**, then release.

## 5. Data safety answers

| Question | Answer |
|---|---|
| Does the app collect or share user data? | **Yes, collects.** No sharing with third parties (processors acting for you don't count as sharing) |
| Encrypted in transit? | **Yes** |
| Can users request deletion? | **Yes** (by email; medical records may be kept as law requires: explain in the policy) |
| Personal info: name, email, phone, other (licence/staff number) | Collected · required · *Account management*, *App functionality* |
| Photos | Collected · optional · *App functionality* (licence photo, clinical photos) |
| Files and docs | Collected · optional · *App functionality* (PDF results) |
| Health and fitness → Health info | Collected · required · *App functionality* |
| App activity → Other actions (access log) | Collected · required · *Fraud prevention, security* |
| Device IDs (push address) | Collected · optional · *App functionality* |
| Location, contacts, financial info, messages (SMS/email), web history, audio | **Not collected** |
| Analytics / advertising | **None** |

## 6. Before real patients: production setup

1. A **new Supabase project** (paid plan, EU region) for real data; keep the
   current one for testing with fake data.
2. Run all migrations there (SETUP.md section 2), configure Auth (section 3)
   with a real email sender (SMTP), and re-do section 9 (push) for it.
3. Change the GitHub secrets `SUPABASE_URL` / `SUPABASE_ANON_KEY` to the
   production project before building the release you publish. (Ask me to set
   up a separate "test" build pointing at the test project if you want both.)
4. Work through docs/COMPLIANCE.md with your lawyer.
