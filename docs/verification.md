# Verification record

Environment: Linux aarch64, JDK 21, Android SDK 35, Gradle 8.13,
Android Gradle Plugin 8.11.1. The build uses the SDK installed at
`$ANDROID_HOME`.

| Check | Command | Result |
| --- | --- | --- |
| URL policy | `./gradlew :app:testDebugUnitTest` | Passed: 3 tests |
| APK build | `./gradlew :app:assembleDebug` | Passed |
| Static analysis | `./gradlew :app:lintDebug` | Passed with warnings |
| APK signature | `apksigner verify --verbose app-debug.apk` | Passed; v2 signature, 1 signer |
| APK metadata | `aapt2 dump badging app-debug.apk` | `ai.multica.android`, version `0.1.0`, min SDK 26, target SDK 35 |
| Device installation and flows | `adb devices -l` | No device attached; not run |

The Gradle tasks above were run together with `--offline` on November 22,
2026. Lint warnings include the deliberate JavaScript WebView setting and
Android target SDK/version guidance. The production web app requires JavaScript.

The installable artifact is `app/build/outputs/apk/debug/app-debug.apk` after
building. It is ignored by Git and should be delivered as a Multica comment
attachment. Verify any delivered copy with `apksigner verify --verbose` and a
SHA-256 checksum before installing it. The APK built for this delivery has
SHA-256 `d24e2fee4eac792087faab154b4416fcf50f3026993ad785e3aabe96a835855d`.
