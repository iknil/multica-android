# Verification record — Android 0.1.2

November 22, 2026. Linux aarch64, JDK 21, Android SDK 35, Gradle 8.13,
Android Gradle Plugin 8.11.1. The APK uses this host's debug signing key.

| Area | Evidence | Remaining device check |
| --- | --- | --- |
| QA P2-2 exports | Chromium 153 headless ran `scripts/test-download-chromium.mjs`: detached CSV with synchronous revoke, detached theme ZIP with delayed revoke, mounted Mermaid SVG/PNG with delayed revoke, and a mounted data URL user click. All five filenames, MIME types and file bytes matched; each produced one bridge export. `scripts/test-download-capture.mjs` also passed. | Save/open each export from Android Downloads, including immediate revoke. |
| Theme directory import | `ThemeFolderArchiveTest` checked stored ZIP relative paths and unsafe names. `scripts/test-theme-folder-import.mjs` checked picker interception and synthetic FileList reconstruction. Web `readThemePackage` accepts a single ZIP from a file input. | SAF tree picker, theme preview/install, and Android 8–9 FileList support. |
| Google login callback | `AuthCallbackPolicyTest` checked callback origin/path/state, malformed and expired code rejection. Android builds a PKCE challenge, returns through a validated app callback, and POSTs a one-use code to the planned same-origin exchange endpoint. | Requires GANY-28 Web/API implementation, production deployment, signed App Links association, and browser-to-WebView cookie check. |
| Prior P1/P2 fixes | Existing URL-policy JUnit tests and Android compile/lint passed. | `window.open`, SPA history, Android 15 insets, cold-start retry, URL handling and attachments remain device checks. |

| Check | Command | Result |
| --- | --- | --- |
| Android unit tests, lint and build | `ANDROID_HOME=/home/iknil/Android/Sdk ./gradlew --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` | Passed; 9 JUnit tests; lint 0 errors, 6 warnings |
| Browser export regression | `node scripts/test-download-chromium.mjs` | Passed in real Google Chrome 153.0.8010.52; five exports, exact bytes and filenames |
| Export script unit check | `node scripts/test-download-capture.mjs` | Passed |
| Theme input script check | `node scripts/test-theme-folder-import.mjs` | Passed |
| APK signature | `apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk` | Passed; v2 signature, one signer |
| APK metadata | `aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk` | `ai.multica.android` 0.1.2 (3); min SDK 26, target SDK 35 |
| Device inventory | `adb devices -l` | Empty device list |

APK SHA-256: `9314164b66bb51fd717cc65c9bb45ba9be98ab986f3382b832743b703cdcea4e`.
The six lint warnings concern target/compile SDK currency, required JavaScript,
backup configuration, launcher icon shape, and untranslated brand text.

## Unverified and required resources

No authorized ADB device or usable emulator is available on this host. No APK
installation, Android runtime interaction, screenshots, email login, Google
login, file picker, download, or visual result has been observed. A device
owner must install this APK and run these scenarios independently.

GANY-28 requires official maintainers to deploy the Web/API changes and
handoff-code migrations, and to confirm the existing Google OAuth configuration
works with the production browser redirect. The release owner must provide the
public SHA-256 fingerprint of the production signing certificate. The domain
owner must publish
`/.well-known/assetlinks.json` for `ai.multica.android` and that fingerprint.
The release keystore stays with its owner. An authorized Android 8–15 device
with a test account and workspace, or a working emulator host, is needed for
end-to-end acceptance. See `docs/android-auth-contract.md` for the exact API
contract and negative test cases.
