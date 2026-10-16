# App Risk Auditor — scoring rubric

| Rule or permission | Points |
|---|---:|
| **Bands**: Low 0–24; Medium 25–49; High 50–74; Critical 75–100; clamp 0–100 | |
| Declared but not granted | 30% of the listed permission value |
| **Installer multiplier**: Google Play | ×0.85 on permission subtotal |
| Other known store | ×1.00 on permission subtotal |
| Sideloaded / unknown | ×1.25 on permission subtotal |
| **Special access**: enabled accessibility service / active device admin / notification listener / overlay / install packages / usage stats | +30 / +25 / +20 / +20 / +15 / +10 |
| SMS: READ_SMS / RECEIVE_SMS / SEND_SMS | +15 / +8 / +12 |
| Contacts & call logs: READ_CONTACTS / READ_CALL_LOG / WRITE_CONTACTS or WRITE_CALL_LOG | +8 / +10 / +4 |
| Camera / microphone: CAMERA / RECORD_AUDIO | +6 / +8 |
| Location: fine / coarse / background | +6 / +3 / +10 |
| Phone: READ_PHONE_STATE / READ_PHONE_NUMBERS / CALL_PHONE | +4 / +4 / +5 |
| Storage: each READ_MEDIA_* or READ_EXTERNAL_STORAGE; category cap | +4 each; cap +8 |
| MANAGE_EXTERNAL_STORAGE | +12 |
| Background: RECEIVE_BOOT_COMPLETED / FOREGROUND_SERVICE (including all FOREGROUND_SERVICE_* variants) / ignore battery optimization; category cap | +4 / +2 / +4; cap +8 |
| INTERNET alone | +1 before installer multiplier (always Low) |
| R1 OTP stealer: SMS read/receive + internet + boot persistence | +30 sideloaded/unknown; +12 Google Play |
| R2 Banking trojan: enabled accessibility + granted overlay | +35 |
| R3 Rogue device admin: active admin outside trusted list | +30 |
| R4 OTP via notifications: enabled notification listener + internet; plus SMS | +15; +10 SMS bonus |
| R5 Silent installer: install-packages granted + sideloaded installer | +20 |
| R6 Spy kit: camera/mic + background location/foreground service + internet + boot | +20 |
| R7 Contact harvester: contacts + call log + internet | +15 |
| R8 Full storage: manage-all-files + internet + sideloaded | +15 |
| R9 Stalkerware: background location + camera/mic + boot + no launcher icon | +25 |
| R10 Old target: target SDK <23 and a dangerous permission | +8 |
| Hidden app with sensitive permissions; simple-utility context mismatch; update adds sensitive permission | +10; +20; +10 |
| Manifest checks: debuggable; cleartext traffic; exported unprotected component | +8; +4; +2 each (cap +10) |
| Trusted security/MDM/password-manager package | −20; R3 does not apply |

**Trusted prefixes / packages:** `com.google.android.apps.work.clouddpc`, `com.microsoft.intune`, `com.airwatch.androidagent`, `com.lastpass.lpandroid`, `com.x8bit.bitwarden`, `com.kaspersky.*`, `com.avast.*`, `com.bitdefender.*`, `com.eset.*`, `com.lookout`.

**R1 store note:** the implemented positive bonus is +12 for Google Play and +30 for sideloaded/unknown; other recognized stores do not receive the Play-specific bonus.

**Privacy:** scanning, permission checks, scoring and report generation run on-device. The optional hash lookup is off by default and sends only an APK SHA-256 fingerprint after explicit consent. No app file, app inventory, analytics or user data is uploaded. Synthetic local fingerprint entries are not malware samples.
