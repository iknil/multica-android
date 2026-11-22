# Web feature coverage

Compared with the local `multica-ai/multica` web source at `9cb59baec`
(November 14, 2026). The Android client hosts the deployed web app; availability
of a route does not prove that its interactions work on a device.

| Web capability | Android implementation | Evidence and remaining check |
| --- | --- | --- |
| Login, invitations, onboarding, workspace creation | Production pages in WebView | Route mapping inspected; live sign-in blocked on device access; Google sign-in needs server session exchange |
| Issues, issue detail, deliverables, my issues, saved views | Production pages in WebView | Route mapping inspected; authenticated flows need device QA |
| Chat and inbox | Production pages in WebView | Route mapping inspected; authenticated flows need device QA |
| Projects, agents, squads, skills, autopilots | Production pages in WebView | Route mapping inspected; authenticated flows need device QA |
| Runtimes, usage, billing, settings, members | Production pages in WebView | Route mapping inspected; authenticated flows need device QA |
| File uploads | Android document picker through WebChromeClient | Build/lint only; device picker and upload need QA |
| Theme folder import | Android Storage Access Framework tree picker; native files become a stored ZIP for the existing web importer | Archive unit test and JavaScript event test; directory picker and import need device QA |
| HTTPS attachments | DownloadManager with WebView cookies | Code inspection; download and access need device QA |
| Blob/data exports | Programmatic anchor and document click capture, WebView fallback, chunked Android write | Chromium 153 checked CSV, theme ZIP, Mermaid SVG/PNG and data URL bytes with detached/mounted anchors and synchronous/delayed revoke; device export needs QA. Android 10+ saves to public Downloads; Android 8–9 saves to app external files |
| Same-origin links | `https://multica.ai` remains in WebView | URL policy tests; manifest requests verification, but App Links association needs production `assetlinks.json` and device check |
| External links and new windows | User-gesture popup URL opens browser or main WebView | Code inspection; `window.open` and `target=_blank` need device QA |
| Back, forward, reload, home, retry | Native toolbar and WebView history callback | Code inspection; SPA history and cold-start offline retry need device QA |
| Android 15 system bars | Root view applies system-bar insets | Build/lint only; visual device QA needed |

## Google sign-in dependency

The Android callback now creates a state and PKCE challenge, opens the system
browser, validates the return, and POSTs the short-lived code from WebView to a
same-origin exchange URL. GANY-28 has a Web/API patch for the
`platform=android` handoff and exchange endpoint; official maintainers must
deploy it and confirm the existing Google OAuth browser configuration works.
The flow cannot complete in the official environment until that deployment and
the [Android auth contract](android-auth-contract.md) are verified.
Existing `multica://auth/callback?token=...` yields a JWT but cannot safely
create the current HttpOnly cookie/CSRF session. This client never writes that
JWT into legacy localStorage.

## Other uncovered items

- No device or emulator was available for installation, layout, authenticated
  journeys, downloads, and OAuth return. The build is installable but those
  flows remain unverified.
- Production `https://multica.ai/.well-known/assetlinks.json` must bind the
  production signing certificate and package; the local debug certificate does
  not establish production App Links ownership.
- The folder picker bridge is limited to 1 MB, 64 files and eight directory
  levels, matching the Web theme package size limit. Android 8–9 WebView
  support for synthetic `FileList` needs device verification.
- Push notifications, share targets, offline content and background uploads
  have no Android implementation in this release.
