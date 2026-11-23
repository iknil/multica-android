# GANY-28 main integration verification

Historical record for the 2026-11-22 main commit. For the current main adaptation, use `gany28-main-20261124-verification.md` and `patches/GANY-28-main-ff7c4a36.patch`.

## Source and patch

- Upstream repository: `multica-ai/multica`
- Checked upstream `main`: `4ceffd536603c67620c7460f6c412dcbf092299e`
- Isolated integration commit: `fc958e25c55f71d253dc45605c4beccf7446632d`
- Attached patch: `0001-GANY-28-add-Android-browser-auth-handoff-on-main.patch`
- Patch SHA-256: `0d555c6b51a0e8cb93cc474634de961cc01956a928257f6118cc228d625cee60`
- `git apply --check` passed before applying the source patch; `git apply --check --reverse` passed against the integrated commit. `git diff --check` passed. `make sqlc` completed with no unexpected generated changes.
- Upstream migrations ended at 722; this patch adds 723–725. Concurrent index migrations 724 and 725 match `concurrentIndexCleanups` entries in `server/cmd/migrate/main.go`.

## Local regression results

- `GOMAXPROCS=2 go test -p 1 ./cmd/migrate ./internal/migrations ./cmd/server -run 'TestAndroidAuthRoutes|TestConcurrentIndexCleanup' -count=1` (from `server/`): passed on 2026-11-22.
- `pnpm --filter @multica/web test`: 50 files, 465 tests passed on 2026-11-22.
- Earlier in the same isolated checkout: views 9,377 tests passed; core 3,627 passed and 1 skipped; Web, views and core type checks passed; Web lint passed, views/core lint had 0 errors with 21/2 warnings; `bash scripts/selfhost-config.test.sh` passed.
- Handler tests were run with `GOMAXPROCS=2 go test -p 1` and passed where the database was not required. PostgreSQL-dependent integration cases were skipped because localhost:5432 refused the connection; a real migrated database remains required for those cases.

## Publishing and acceptance limits

- SSH push to `multica-ai/multica` was rejected: `Permission to multica-ai/multica.git denied to iknil`. No `iknil/multica` fork exists, and the available browser is not signed into GitHub, so no remote branch or PR was created. An upstream maintainer can apply the attached patch to the stated `main` commit, push a branch and open a GANY-28 PR.
- Official maintainers must deploy Web/API and migrations, publish the domain association with the public release certificate fingerprint, and confirm the **existing** Google OAuth configuration supports `https://multica.ai/auth/callback`. No OAuth secret handoff or separate Android OAuth client is required by this design.
- Production OAuth, domain association, ingress behavior, database integration and Android device end-to-end acceptance remain unverified.
