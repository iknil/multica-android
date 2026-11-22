# Verification record

November 22, 2026. Linux aarch64, JDK 21, Android SDK 35, Gradle 8.13,
Android Gradle Plugin 8.11.1. The APK uses the local debug signing key.

| QA finding | Code/change | Automated evidence | Device evidence |
| --- | --- | --- | --- |
| P1 new-window external links/attachments swallowed | User-gesture popup route, download callback and cleanup in `MainActivity` | Gradle compile/lint | Unavailable; retest `window.open`, `target=_blank`, attachment |
| P1 SPA back/forward state stale | `doUpdateVisitedHistory` refreshes toolbar | Gradle compile/lint | Unavailable; retest pushState, back, forward |
| P2 Android 15 system bars overlap | Root window insets | Gradle compile/lint | Unavailable; visual retest |
| P2 blob/data export | JavaScript click capture plus Android download bridge | Node immediate-revoke export test, Gradle compile/lint | Unavailable; retest CSV and file access |
| P2 cold-start offline Retry | Retry loads current app URL or home | Gradle compile/lint | Unavailable; offline launch/reconnect retest |
| P2 spaces and pipe in URLs | URL policy normalizes these characters before host check | JUnit URL policy tests | Unavailable; link retest |
| P2 Google sign-in and verified App Links | Backend/session and domain association dependencies documented | No end-to-end test possible | Blocked pending backend, domain and device |

| Check | Command | Result |
| --- | --- | --- |
| URL policy | `./gradlew --offline :app:testDebugUnitTest` | Passed, 4 tests |
| APK build | `./gradlew --offline :app:assembleDebug` | Passed |
| Static analysis | `./gradlew --offline :app:lintDebug` | Passed, 0 errors and 6 warnings |
| Export capture | `node scripts/test-download-capture.mjs` | Passed, immediate blob URL revoke case |
| APK signature | `apksigner verify --verbose app-debug.apk` | Passed, v2, one signer |
| APK metadata | `aapt2 dump badging app-debug.apk` | `ai.multica.android` 0.1.1 (2), min SDK 26, target SDK 35 |
| Device inventory | `adb devices -l` | Empty device list |

The three Gradle tasks ran together with `ANDROID_HOME=/home/iknil/Android/Sdk`.
Lint's six warnings concern target/compile SDK currency, required JavaScript,
backup configuration, launcher icon shape, and untranslated brand text.
SHA-256 of this APK: `391adfa3530d133b0c5e6a56aa2e6d7f6c98ece02a3b8ed4c18c4b45cb3a91dd`.
An attached APK is the delivery artifact; `app/build` is ignored by Git.

## Device availability

`adb devices -l` found no authorized device. No AVD or Android system image was
installed locally; the available host has no usable Android runtime for app
installation. Device installation, screenshots, login, and interactive QA are
unverified. Independent QA must install the attached APK on a device and run the
checks in the table.
