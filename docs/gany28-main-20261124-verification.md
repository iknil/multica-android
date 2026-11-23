# GANY-28 current-main integration verification — 2026-11-24

## Source and changes

- Upstream repository: `multica-ai/multica`; current `main` checked before and after the adaptation: `ff7c4a36e1c6d6247ae444b570398a59fa5e609f`.
- Isolated integration commit: `51ffe4ccab9153993c8330ff66220ca159da1d95` (parent: the `main` commit above).
- Deliverable patch: `patches/GANY-28-main-ff7c4a36.patch`, SHA-256 `e854e9f2f9e66d9940c7c28e1b55eb9f8f3c5d315b9506ecd5d50a8b4ba53259`. `git apply --check --reverse` passed on the integration commit.
- Previous GANY-28 code was applied to the new main. The callback preserves upstream's single-use OAuth code guard; the updated Web test ESLint override is retained. New upstream migrations occupy 723–734, so Android migrations are now 735–737, with 736/737 registered in `concurrentIndexCleanups`. `make sqlc` regenerated code without unrelated changes. `git diff --cached --check` passed before the integration commit.

## Regression results

- `corepack pnpm --filter @multica/web test`: 54 files, 489 tests passed.
- `corepack pnpm --filter @multica/views test -- auth/login-page.test.tsx`: 745 files, 9,583 tests passed (the package command runs the full suite).
- `corepack pnpm --filter @multica/core test -- api/schema.test.ts`: 281 files, 3,699 passed, 1 skipped.
- Web, views and core `typecheck`: passed after `next typegen`. Web lint: passed. Views lint: 0 errors, 22 warnings; core lint: 0 errors, 2 warnings.
- `GOMAXPROCS=2 go vet ./internal/handler ./cmd/server ./cmd/migrate ./internal/migrations` and `bash scripts/selfhost-config.test.sh`: passed.
- Isolated PostgreSQL 16.15 on `127.0.0.1:18432`: full migrations through 737 passed. `android_auth_code_hash_idx` was valid and unique; `android_auth_code_expiry_idx` was valid. With `DATABASE_URL` and `TEST_DATABASE_URL` pointed at this migrated database, `go test -p 1 ./internal/migrations ./cmd/migrate -count=1 -v` passed, including migration numbering and concurrent-index cleanup tests. `go test -p 1 ./internal/handler ./cmd/server -run 'TestAndroid|TestVerifyCode|TestGoogleLogin|TestGetMe' -count=1 -v` passed, including Android cookie exchange and route cases. The test database was stopped and removed afterward.

## Publishing and acceptance limits

- SSH push to `multica-ai/multica` was rejected (`Permission ... denied to iknil`); no `iknil/multica` fork exists. The Web/API integration has no remote branch or PR. A maintainer with repository write access can apply the attached patch to the stated main commit and open a GANY-28 PR. Recheck migration numbers if main advances again.
- Official maintainers must deploy Web/API and migrations, publish domain association with the public release certificate fingerprint, and confirm the **existing** Google OAuth browser redirect at `https://multica.ai/auth/callback` works. No OAuth secret handoff or separate Android OAuth client is required by this design.
- Production OAuth, domain association, ingress behavior, and Android device end-to-end acceptance remain unverified. The parent issue remains externally blocked.
