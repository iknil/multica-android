# Android browser sign-in contract

This is the Android half of GANY-28. The server and Web changes described here
are required before Google sign-in works end to end.

## Request and return

1. When the WebView tries to navigate to Google's OAuth endpoint, Android
   generates independent 256-bit `android_state` and PKCE `code_verifier`
   values. It stores the verifier and state privately for five minutes and
   opens an external browser at
   `https://multica.ai/login?platform=android&android_state=<state>&android_challenge=<S256(verifier)>`.
   The challenge and state are base64url without padding.
2. The browser login page must preserve both values through email and Google
   sign-in. After the browser has the normal web cookie session, it requests a
   one-use Android handoff code from the server. The prepare endpoint must
   require the browser's authenticated human session and CSRF protection and
   bind the code to the state, PKCE challenge, app identity, user/session and
   a lifetime no longer than five minutes.
3. The browser redirects to
   `multica-android://auth/callback?code=<base64url>&state=<base64url>`.
   After production App Links verification, it may instead use
   `https://multica.ai/auth/android/callback?code=...&state=...`.
   No JWT or session cookie appears in either callback URL.
4. Android accepts only these exact callback origins and paths. It rejects
   mismatched/expired state, duplicate parameters and malformed code; it
   clears the pending state on success and uses `WebView.postUrl` to POST
   `code=<code>&state=<state>&code_verifier=<verifier>` to
   `https://multica.ai/auth/android/exchange`.
5. The same-origin exchange endpoint consumes the code atomically, verifies
   the PKCE S256 challenge and all bindings, creates a normal web session,
   sets the existing HttpOnly authentication cookie and readable CSRF cookies,
   and responds with a safe same-origin redirect to the authenticated app.
   It must reject replay, expiry, wrong verifier/state/app and disabled users.
   It must not return a bearer token to JavaScript or use legacy localStorage.

Use `Cache-Control: no-store` and `Referrer-Policy: no-referrer` for
handoff responses. Do not log the code, verifier or full callback URL.
The production Web rewrite for `/auth/:path*` must route the exchange POST
to the server while the callback page remains browser-visible.

## Independently testable server work

GANY-28 can add server and Web integration tests without an Android device:
issue a code from an authenticated browser session, exchange it with a matching
verifier, assert normal auth and CSRF cookies, then assert that replay, expiry,
wrong verifier/state, wrong app binding, missing browser authentication and
disabled users fail without cookies. Test Google OAuth state propagation and
email sign-in return separately. Android unit tests cover callback parsing
and rejection; they cannot prove WebView cookie reception.

## Production materials and access

- The release APK/package ID and **public SHA-256 signing certificate
  fingerprint** for `ai.multica.android`. The owner keeps the private
  keystore; the debug APK fingerprint is not a production substitute.
- Deployment access for `multica-ai/multica` Web and API plus its handoff-code
  store, and domain ownership/access to publish
  `https://multica.ai/.well-known/assetlinks.json` with
  `delegate_permission/common.handle_all_urls` for the release certificate.
- Google OAuth configuration permission to confirm the existing production
  `https://multica.ai/auth/callback` redirect and the Android handoff path.
  No OAuth client secret needs to be shared with this Android worktree.
- An authorized ADB device (Android 8–15) with a test account and workspace,
  or an emulator host with an installed system image and working KVM/binder,
  to install the new APK and record login, cookie persistence, directory
  import and App Links behavior. The current host has no such device/runtime.

Until the server is deployed, the new Android callback and folder import can
be built and unit tested, but Google login remains blocked. HTTPS App Links
also remain unverified until the domain association and release signing
fingerprint are in place.
