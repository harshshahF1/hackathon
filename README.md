# App Risk Auditor

Native Android app (Kotlin, Jetpack Compose, Material 3) that inventories installed apps, checks requested and special access, and explains app risk scores locally. It works without a network connection; only the optional, consent-gated SHA-256 lookup uses the network.

## What it does

- Scans installed apps and shows their icon, package, version, install/update dates, installer and target SDK.
- Shows declared versus granted permissions, protection level, plain-English descriptions, nine permission categories and device-wide totals.
- Checks accessibility services, device-admin apps, notification listeners, overlay, install-apps and usage-stat access against Android's current settings state.
- Calculates deterministic 0–100 scores with four bands, named combination rules, context-mismatch signals, permission-change history and manifest checks.
- Monitors package installs and updates using package broadcasts plus a 15-minute WorkManager diff; high/critical events appear in Alerts and Android notifications.
- Offers app-settings shortcuts, permission recommendations, PDF/JSON export, a rubric screen and eight isolated synthetic self-test profiles.
- Shows an experimental permission-only logistic score alongside the rule score. The experimental result never changes the rule score.

## Architecture

```text
MainActivity (single Activity, Navigation Compose)
  ├─ ui/ (Compose screens, ViewModel, Material 3 theme)
  ├─ domain/ (PermissionCatalog, RiskWeights, RiskEngine, ML scorer)
  ├─ data/scanner/ (PackageManager scanner, AppOps/special-access checks, APK hash)
  ├─ data/storage/ (SharedPreferences JSON snapshots, permission diffs, alerts)
  ├─ receiver/ + worker/ (package broadcasts and periodic WorkManager scan)
  └─ util/ (PDF/JSON reports)
```

The app persists only local settings, package permission snapshots and alert history in SharedPreferences JSON. No Room database or server is required.

## Build and install

Requirements: JDK 17+, Android SDK platform 34 and build-tools 34.0.0.

```bash
./gradlew assembleDebug
./gradlew test
```

Install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it. The GitHub Actions workflow also copies the built APKs to `release/AppRiskAuditor.apk` (debug) and `release/AppRiskAuditor-release.apk`, and uploads them as the `AppRiskAuditor-apks` artifact; download it from the Actions run page. To copy locally: `mkdir -p release && cp app/build/outputs/apk/debug/app-debug.apk release/AppRiskAuditor.apk`. `./gradlew assembleRelease` builds a release variant signed with the debug keystore for hackathon installation; it is not a production signing setup.

## Permissions and privacy

- `QUERY_ALL_PACKAGES`: needed to inventory installed apps and their package metadata.
- `POST_NOTIFICATIONS`: optional Android 13+ permission for install/update alerts.
- `PACKAGE_USAGE_STATS`: declaration supports detection of usage-access capability; the user must enable access in Android Settings for the relevant apps.
- `INTERNET`: used only when the user explicitly chooses the optional SHA-256 hash lookup.

The app does not request root, ADB access, accessibility, device-admin, SMS, contacts, camera, microphone or location permissions for itself. It reads app metadata and settings state available to ordinary apps. Notification permission can be granted from the first-launch card or later in Android app settings.

The hash lookup is off by default. The user must consent before a request is sent, and only the SHA-256 APK fingerprint is sent. Lookup endpoint is configurable in Settings. The app file is never uploaded. Local matching uses a synthetic text fingerprint only; no malware binaries or samples are included.

## Build variants

- `assembleDebug`: package `com.hackathon.apprisk`, debug keystore.
- `assembleRelease`: package `com.hackathon.apprisk`, debug keystore (for demo installation only).

## Known limitations

- Android limits background package broadcast delivery on newer versions; the app combines an in-process receiver, manifest receiver and 15-minute WorkManager diff. WorkManager is periodic/best-effort, not a real-time guarantee.
- Some OEM settings panels or permission operations can be absent or behave differently. Every settings shortcut falls back to the app's general settings page.
- Package visibility, AppOps state and service metadata can differ across OEM builds. Unknown AppOps results remain labeled unknown rather than being guessed.
- The experimental ML coefficients are hand-set until the training script is run on a labeled public dataset. Its percentage is not a calibrated malware probability.
- The theme follows the system light/dark setting on first launch and can be changed in Settings.
- Hidden-app detection queries the MAIN/LAUNCHER activities of each package directly.
- Debug/release APKs are demo builds, not Play Store production-signed packages.
- No emulator/device is available in the build container, so runtime smoke checks on Android API 26, 30, 34 and API 35 (Android 15) must be performed on an emulator or device. The project includes JVM scoring tests and CI APK builds.

## 3-minute demo script

1. **0:00–0:30 — problem and dashboard:** explain that apps can ask for sensitive access and that the scanner keeps the analysis on-device. Show the risk counts, last scan and top-risk list.
2. **0:30–1:30 — inspect a risky app:** open the highest-scored app. Walk through Why flagged, special-access states, permission groups and targeted settings/fix buttons. Mention that device-admin access may need revoking before uninstall.
3. **1:30–2:15 — live install monitoring:** install an organizer test APK. Show the new app/updated app alert in Alerts and tap through to its details.
4. **2:15–2:45 — changed permissions and context:** open an updated app and show added/newly granted permissions; use the synthetic self-test to demonstrate the context-mismatch rule without mixing demo profiles into the real inventory.
5. **2:45–3:00 — report and transparency:** export the PDF and open Settings → Scoring rubric. Point out the local-only scan and optional hash consent.

## Optional ML training

Install Python dependencies with `python -m pip install -r requirements.txt`, then run:

```bash
python ml/train_permission_model.py path/to/labeled-permissions.csv \
  --label-column label --positive-label malware
```

The script accepts binary permission columns, or a comma/semicolon-separated permission list via `--permissions-column`. It exports the feature order, coefficients and intercept to `app/src/main/assets/ml_model.json`. Use a reputable public dataset and review its labels and license before training.

## Android CI

`.github/workflows/build-apk.yml` runs the JVM tests, builds the debug and release APKs on each push and pull request, and uploads them (plus the test report) as workflow artifacts.
