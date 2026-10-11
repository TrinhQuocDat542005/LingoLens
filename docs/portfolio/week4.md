# Portfolio week 4 — offline spaced review

Scope: a complete recall/reveal/grade loop, resumable local sessions, deterministic shared scheduling, durable answer synchronization and honest review statistics.

## Delivered

- Four grades (Again, Hard, Good, Easy) with interval previews and device Text-to-Speech.
- Finite sessions of up to 20 due cards, saved position/visibility, completion summary and deleted-word skipping.
- Account-isolated Room v3 cards, immutable answer events and sessions; exported schema and explicit 1 → 2 → 3 migrations.
- Atomic grade + schedule + answer + cursor + outbox transactions; reveal gate and expected-position protection against duplicate taps.
- One pure Kotlin scheduling source compiled by Android and Spring, including the backend Docker build.
- Flyway V8 schedules/events, old-library backfill, server-computed schedules and replay-safe review operations.
- Review snapshot restoration, answer deduplication and protection for local grades made while cloud data downloads.
- Review entry points in Home, My Words and Stats; due/next-due counts, today/seven-day answers, self-reported recall and state distribution.
- Streak based on both learning and reviewing, while the new-word goal keeps its first-save semantics.
- Professional README, architecture/protocol updates, [algorithm contract](../spaced-review.md) and repeatable HTTP smoke script.

## Verification

Validated locally on **2026-10-11 (Asia/Saigon)** with JDK 21, Android SDK 36.1, Room 2.8.5, Robolectric 4.16.1 and a real PostgreSQL 15 container.

| Check | Result |
| --- | --- |
| Android JVM / Robolectric tests | 46 tests, zero failures/errors/skips |
| Android lint | Zero errors; 38 warnings and 1 hint remain visible |
| Android packaging | Debug APK assembled |
| Backend H2 API tests | 16 tests, zero failures/errors/skips |
| PostgreSQL integration | 5 tests, zero failures/errors/skips; all eight migrations and concurrent review retry verified |
| Backend packaging | Executable boot JAR built |
| Docker image | Root-context build succeeded with shared Kotlin scheduler |
| HTTP smoke against built image | Health UP; authenticated save/review/replay/snapshot/delete/delayed-review assertions passed |

New Android checks exercise grade steps/bounds/clock rollback, reveal gating, duplicate grading, persistence across reopen, due boundaries, deletion/resave, exported v2 migration preserving an existing outbox, cloud answer deduplication and a local grade during snapshot download. The existing exported v1 test now upgrades through v3. API tests verify rollback on invalid grade/time, account fencing, payload-conflict rejection and history retention after deletion. PostgreSQL runs two simultaneous requests with one review UUID and verifies one answer and one interval advance.

Reproduce from the repository root:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --max-workers=2
.\backend\gradlew.bat -p backend test postgresTest bootJar --max-workers=2
docker build -f backend/Dockerfile -t lingolens-review .
git diff --check
```

With a disposable development API running:

```powershell
.\scripts\smoke-review.ps1 -ApiBaseUrl http://localhost:8080
```

The smoke script creates a separate learner per run and prints only assertion results. Local testing used dedicated containers and a separate localhost port; unrelated containers were untouched. Hosted CI is triggered by the push; the README badge links its current result.

## Portfolio demo narrative

1. Recognize an object, open published vocabulary, pronounce it and save it.
2. Disconnect networking, reveal a due flashcard and grade it; show real pending sync status.
3. Close and resume a partially completed session. Explain the single Room transaction and duplicate-tap protection.
4. Reconnect and restore schedules/answer statistics on another installation. Explain the stable UUID, server receipt and server acceptance conflict policy.
5. Show the shared scheduler, migration test, concurrent PostgreSQL test and CI artifacts as evidence of engineering decisions.

Use the [device walkthrough](../spaced-review.md#device-acceptance-walkthrough) before recording this demo. Physical-device execution, voice quality, Android process-kill behavior and two-phone reconnection timing remain manual acceptance checks; they were not claimed as completed here.

## Limits and next milestone

This custom scheduler is not FSRS/SM-2 and has no measured retention study. Recall percentages describe user-selected grades. Sessions stay on their installation; cloud schedules and immutable answer history synchronize in full snapshots. Receipts, tombstones and review history are retained indefinitely. Cross-device reviews apply in server acceptance order, with a monotonic scheduling anchor rather than a globally trusted device clock.

The week 5 plan recorded at delivery was physical-device benchmarks/failure tests, model evaluation, demo video and a release package. The project owner subsequently revised the upcoming work: the [v2 product specification](../product/lingolens-spec-v2.md) now defines weeks 5–9 around a vertical learning slice and reviewed curriculum content. This update does not change the week 4 implementation or verification results. Large-history delta sync/pagination, retention experiments and timed review reminders remain separate future work.
