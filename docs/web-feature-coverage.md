# Web feature coverage

Compared with the local `multica-ai/multica` web source at `9cb59baec`
(November 14, 2026). The Android client hosts the deployed web app; availability
of a route does not prove that its interactions work on a device.

| Web capability | Android implementation | Evidence and remaining check |
| --- | --- | --- |
| Login, invitations, onboarding, workspace creation | Production pages in WebView | Route mapping inspected; live sign-in blocked on device access; Google sign-in needs server session exchange |
| Issues, issue detail, deliverables, my issues, saved views | Production pages in WebView | Route mapping inspected; authenticated flows need device QA |
| Chat and inbox | Production pages in WebView | Route mapping inspected; authenticated flows need device QA |
| Projects, agents, squads, skills, autopilots | Production pages in WebView | Route mapping inspected; authenticated flows need device QA; theme directory import still unsupported |
| Runtimes, usage, billing, settings, members | Production pages in WebView | Route mapping inspected; authenticated flows need device QA |
| File uploads | Android document picker through WebChromeClient | Build/lint only; device picker and upload need QA |
| HTTPS attachments | DownloadManager with WebView cookies | Code inspection; download and access need device QA |
| Blob/data exports | Click capture, WebView fallback, chunked Android write | JavaScript test covers immediate blob URL revocation; device export needs QA. Android 10+ saves to public Downloads; Android 8–9 saves to app external files |
| Same-origin links | `https://multica.ai` remains in WebView | URL policy tests; App Links association needs production `assetlinks.json` and device check |
| External links and new windows | User-gesture popup URL opens browser or main WebView | Code inspection; `window.open` and `target=_blank` need device QA |
| Back, forward, reload, home, retry | Native toolbar and WebView history callback | Code inspection; SPA history and cold-start offline retry need device QA |
| Android 15 system bars | Root view applies system-bar insets | Build/lint only; visual device QA needed |

## Google sign-in dependency

The web login uses an HttpOnly cookie and CSRF token. Android and the web
backend need a one-time browser-to-app exchange: browser OAuth completes in the
system browser, the backend issues a short-lived single-use code bound to this
app's redirect, and the app passes the code to a same-origin WebView endpoint
which sets the normal HttpOnly cookie and CSRF state. The backend must consume
the code atomically and reject replay/expiry. The app then loads the signed-in
web route. Existing `multica://auth/callback?token=...` yields a JWT but no
server endpoint can exchange it for the current cookie session; writing it into
legacy localStorage would revive a known XSS exposure. This integration needs
backend implementation and deployed HTTPS App Links verification before QA can
close Google sign-in.

## Other uncovered items

- No device or emulator was available for installation, layout, authenticated
  journeys, downloads, and OAuth return. The build is installable but those
  flows remain unverified.
- Production `https://multica.ai/.well-known/assetlinks.json` must bind the
  production signing certificate and package; the local debug certificate does
  not establish production App Links ownership.
- Theme directory import, push notifications, share targets, offline content,
  and background uploads have no Android implementation in this release.
