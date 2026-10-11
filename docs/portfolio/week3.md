# Portfolio week 3 — durable offline sync

Scope: persistent write queue, connected background retry, idempotent backend effects, account isolation and cloud learning restoration.

## Delivered

- Room v2 outbox / account sync metadata and explicit migration from the exported v1 schema.
- Atomic learning edits plus upload intent; FIFO operation processing with durable UUIDs, attempts and visible blocked errors.
- WorkManager connected constraints, exponential backoff, unique immediate chains, periodic recovery and signed-in startup resume.
- Local queuing for recognition history and dependent correction reports, including offline corrections.
- Authenticated sync API with account checks, canonical request hashes, per-account write locks and transactional receipts.
- PostgreSQL migration V7 for cloud learning snapshots, historical events/tombstones and receipts.
- Cloud restore/merge after upload, local-edit protection during download and explicit server-arrival conflict policy.
- Sync status / manual retry cards in My Words and Stats; result UI reports local queueing accurately.
- Coordinated token refresh with session compare-before-save/clear, protecting newer logins from late refresh responses.
- Updated README, architecture and [protocol documentation](../sync-protocol.md).

## Verification

Run from the repository root with JDK 21 / Android SDK 36.1 and Docker running:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --max-workers=2
.\backend\gradlew.bat -p backend test postgresTest bootJar --max-workers=2
git diff --check
```

Validated locally on **2026-10-11 (Asia/Saigon)** with JDK 21.0.10, Room 2.8.5, WorkManager 2.11.2 and Robolectric 4.16.1.

| Check | Result |
| --- | --- |
| Android tests | 39 tests, 0 failures/errors/skips |
| Android lint | 0 errors, 38 warnings, 1 hint; dependency/platform, version-catalog and UI findings remain visible |
| Android build | Debug APK generated (86,219,080 bytes) |
| Backend H2 API tests | 14 tests, 0 failures/errors/skips |
| PostgreSQL integration | 4 tests, 0 failures/errors/skips; all seven migrations and concurrent duplicate sync verified |
| Backend packaging | Executable boot JAR generated |
| Patch formatting | `git diff --check` passed |

New checks exercise exported-schema migration, durable FIFO/reopen, ambiguous acknowledgement, bootstrap, account mismatch, transient/permanent failures, pending-edit protection during pull, coordinated/late token refresh, transactional rollback, dependent reports, replay after deletion and concurrent requests on a real PostgreSQL 15 container.

The previous week 2 GitHub workflow completed successfully. The week 3 workflow is triggered by this commit's push; use the README CI badge for hosted-run results.

## Device demo / failure checklist

1. Upgrade a week 2 APK without clearing data; existing words and learning history must remain.
2. Sign in, disconnect networking, save/delete words and queue a recognition/correction. Pending count increases; local edits remain immediate.
3. Kill/reopen the app while offline, reconnect and verify pending operations drain once. Confirm one recognition/report on the server.
4. Log out with pending operations; sign in as another account. The previous account's operations must remain unsent and hidden. Return to it to resume.
5. Use the same account on a second installation; request sync and verify library/activity restoration.
6. Change a word while a cloud download is underway. Its new local operation must prevent the download from overwriting it.
7. Disable an account in admin while work is pending; verify it pauses and does not drop the queue.
8. Background the app, test reconnect/Doze and observe eventual scheduling. WorkManager does not promise exact timing.

These physical-device checks are not claimed by JVM tests. Large-library delta pagination, receipt retention horizons, manual conflict resolution and spaced repetition remain future work.
