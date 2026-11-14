# Multica Android

Native Android implementation built with Kotlin and Jetpack Compose. The current
vertical slice covers sign-in, workspace selection, a task list, task detail,
and read-only comments/activity. It includes loading, empty, retry, local
incremental timeline reveal, and expired-session states.

## Safe default

Debug builds use a deterministic local fixture by default and never contact a
service. Use `demo@multica.local` with code `123456`, then select one of the
fixture scenarios shown in the app. The generated APK is not a connected or
release-ready build.

## Build and verify

Requirements: JDK 17 and Android SDK 35. Use the repository entry point so the build does not inherit host-global Gradle properties:

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./scripts/verify-build.sh
```

The script uses an isolated `.gradle-verification` user home, builds JVM tests, the debug app APK and the instrumentation APK, and runs lint. Linux x86-64 uses Maven AAPT2 normally. Linux aarch64 requires an executable host-native AAPT2 under `ANDROID_HOME/build-tools`, or an explicit `MULTICA_AAPT2_PATH`. Before the candidate is invoked, the script hashes its bytes and requires the repository-approved SHA-256; only then does it run the version probe and pass the Gradle override. The pinned acquisition contract is SDK Manager package `build-tools;37.0.0` (`sdkmanager --sdk_root="$ANDROID_HOME" "build-tools;37.0.0"`); the retrieved tool is accepted only if its digest matches and is not redistributed by this repository. Other host combinations are not claimed by this feasibility baseline.

With a ready ADB device/emulator, run the platform AndroidKeyStore lifecycle check separately:

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./scripts/verify-keystore-device.sh
```

A missing device exits non-zero and is **not** counted as a pass.

See `docs/android-minimal-flow.md` for authorized environment injection,
AndroidKeyStore session design, fixture behavior, and the exact validation
boundary. `docs/android-native-architecture.md` records the wider contracts,
gaps, decisions, and native estimate.
