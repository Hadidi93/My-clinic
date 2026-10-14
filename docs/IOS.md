# The iPhone app

My Clinic for iPhone is the **same app** as on Android: the screens, rules,
Supabase access, offline copy and sync are shared Kotlin code (`:shared`,
`:core:domain`). The folder `iosApp/` is only a small iPhone shell. You do
**not** need a Mac: GitHub's Mac computers build and test it on every push
(CI job **ios**).

## What is different on iPhone

| | Android | iPhone |
|---|---|---|
| Login storage | Encrypted with an Android Keystore key | iPhone Keychain, "this device only" |
| Offline copy | SQLCipher (AES-256) | iOS complete data protection (AES-256, tied to the passcode) |
| Photos/PDFs waiting to upload | AES-GCM + Keystore | Files with complete data protection |
| App lock | Fingerprint / face / PIN | Face ID / Touch ID / passcode |
| Screenshots | Blocked (FLAG_SECURE) | Can't be blocked by apps: the screen is hidden while recorded or mirrored, and the app switcher shows a cover |
| Background sync | Every 15 minutes (WorkManager) | When the app opens, after every change, and every 15 minutes while open |
| Language | In-app switch, immediately | In-app switch, applied the next time the app starts (or Settings → My Clinic → Language) |
| Push notifications | Firebase | Added once the Apple Developer account exists (section 4) |

## 1. Seeing the app without an Apple account

Every CI run builds the app for the iPhone simulator, starts it and checks
it is still running after 25 seconds. The run's artifact
**ios-simulator-screenshot** contains a screenshot and the app's log.

## 2. Apple Developer account (once)

1. On your iPhone: Settings → your name → Sign-In & Security → two-factor authentication on.
2. <https://developer.apple.com/programs/enroll/> → **Individual** → US$99/year, identity check (1–2 days).

## 3. TestFlight (install on your iPhone and colleagues')

1. **App Store Connect** (<https://appstoreconnect.apple.com>) → Apps → **+** →
   New App: platform iOS, name *My Clinic*, language English, bundle ID
   **com.myclinic.app** (register it under Certificates, Identifiers &
   Profiles → Identifiers if it isn't offered), SKU `myclinic`.
2. **API key for GitHub**: Users and Access → **Integrations** → App Store
   Connect API → **+** → name "GitHub", access **App Manager** → Generate.
   Download the `.p8` file (only possible once) and note the **Key ID** and
   the **Issuer ID** shown above the list.
3. Your **Team ID**: <https://developer.apple.com/account> → Membership details.
4. Turn the key into text (PowerShell on your laptop):
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\Downloads\AuthKey_XXXXXXXXXX.p8")) | Set-Clipboard
   ```
5. GitHub → Settings → Secrets and variables → Actions → New repository secret, four times:

   | Name | Value |
   |---|---|
   | `APP_STORE_CONNECT_KEY_BASE64` | paste (Ctrl+V) |
   | `APP_STORE_CONNECT_KEY_ID` | the Key ID |
   | `APP_STORE_CONNECT_ISSUER_ID` | the Issuer ID |
   | `APPLE_TEAM_ID` | your Team ID |

6. GitHub → Actions → CI → **Run workflow**. The ios job signs the app and
   uploads it to TestFlight (each run is a new build number).
7. App Store Connect → My Clinic → **TestFlight**: after Apple's automatic
   check (10–30 minutes) add yourself under **Internal Testing**; install the
   **TestFlight** app on your iPhone and open the invitation. Colleagues
   outside your account go under **External Testing** (Apple reviews the
   first build, usually within a day).

Keep the `.p8` file safe like a password. If it leaks, revoke the key in
App Store Connect and make a new one.

## 4. Push notifications (after section 3)

Needs the Apple account: an APNs key uploaded to Firebase and the Firebase
file for iPhone. I add this step when your account is ready.

## 5. App Store review notes (later, for the public release)

- Apple requires a way to **delete the account from inside the app**
  (a request; medical records stay as the law requires).
- Give Apple's reviewers a **demo doctor account with fake patients only**.
- Health-data declarations: the same answers as RELEASE.md section 5.
