# Web feature coverage

Compared against the local `multica-ai/multica` source snapshot at commit
`9cb59baec` (November 14, 2026), especially `apps/web/app` routes. This client
renders the deployed web app; it does not reimplement those screens. A route
listed as available still requires an authenticated account, suitable workspace
permissions, and a successful browser interaction.

| Web route or capability | Android path | Verification |
| --- | --- | --- |
| Login, invitations, onboarding, workspace creation | Same web pages in WebView | Route mapping inspected; live login untested |
| Issues, issue detail, deliverables, my issues, saved views | Same web pages in WebView | Route mapping inspected; live flows untested |
| Chat and inbox | Same web pages in WebView | Route mapping inspected; live flows untested |
| Projects, agents, squads, skills, autopilots | Same web pages in WebView | Route mapping inspected; live flows untested |
| Runtimes, usage, billing, settings, members | Same web pages in WebView | Route mapping inspected; live flows untested |
| Attachments | Android document picker via WebChromeClient; HTTPS downloads via DownloadManager | Build and lint passed; device flow untested |
| Workspace and issue links | `https://multica.ai/...` opens the same route in WebView | URL policy unit tests passed; device intent untested |
| Back, forward, reload, home, browser escape | Native toolbar/menu | Build and lint passed; device interaction untested |

## Known gaps

- Google OAuth can reject embedded WebView user agents. The browser menu gives
  access to the full web login, but its browser session is separate from the
  WebView session. An Android native OAuth handoff needs a server-supported
  cookie/session exchange to keep the user signed in inside the app.
- System push notifications, share targets, offline content and background
  uploads are not implemented. They are outside the web route surface.
- No Android device or emulator was connected during this build, so no end to
  end interaction or visual layout was verified. Independent QA should test
  each web route and attachment flow on a device.
- Downloads are stored in the app's external files Downloads directory and
  shown through Android's download notification and Downloads screen. Some
  device file managers may not show the app directory by default.
