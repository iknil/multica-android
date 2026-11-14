# Multica Android

Native Android feasibility skeleton built with Kotlin and Jetpack Compose.
It freezes the verified HTTP and WebSocket client identity at the code boundary,
but intentionally does not call a production service until environment and
release configuration are supplied.

## Build

Requirements: JDK 17 and Android SDK 35.

```bash
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew --no-daemon clean testDebugUnitTest assembleDebug lintDebug
```

The default `API_BASE_URL` is the non-routable `https://api.example.invalid`.
Do not treat the generated debug APK as a connected or release-ready app.
See `docs/android-native-architecture.md` for contracts, gaps, and estimates.
