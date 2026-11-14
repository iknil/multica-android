# Multica Android

Native Android implementation built with Kotlin and Jetpack Compose. The current
vertical slice covers sign-in, workspace selection, and a task list, including
loading, empty, retry, and expired-session states.

## Safe default

Debug builds use a deterministic local fixture by default and never contact a
service. Use `demo@multica.local` with code `123456`, then select one of the
fixture scenarios shown in the app. The generated APK is not a connected or
release-ready build.

## Build and verify

Requirements: JDK 17 and Android SDK 35.

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug
```

See `docs/android-minimal-flow.md` for authorized environment injection,
AndroidKeyStore session design, fixture behavior, and the exact validation
boundary. `docs/android-native-architecture.md` records the wider contracts,
gaps, decisions, and native estimate.
