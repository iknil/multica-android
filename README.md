# Multica for Android

An installable Android client for the Multica web application. The app opens
`https://multica.ai/login` in Android WebView, so the workspace routes and
features come from the production web deployment. Android code handles app
links, back/forward navigation, file selection, HTTPS downloads, external
links, generated exports and connection errors.

## Build and install

Requires JDK 17 or later and Android SDK 35. Set `ANDROID_HOME` to the SDK
directory, then run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed with the local Android debug key and can be sideloaded
on Android 8.0 or newer. Each builder's debug key is different, so upgrading
an APK built on another machine can require uninstalling the old copy first.

The app needs a network connection and a running Multica web deployment.
Google sign-in in an embedded WebView can be rejected by the identity provider;
the **Open in browser** menu item opens the current page in the device browser.
That browser session cannot yet return to the WebView cookie session; the
required server exchange is described in the coverage document.

See [Web feature coverage](docs/web-feature-coverage.md) and
[verification record](docs/verification.md) for the scope and checks.
