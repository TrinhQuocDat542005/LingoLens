# Portfolio week 2 — persistent learning and real vocabulary

Scope: replace sample learning data with account-owned Room storage, connect published backend content, enable cached/offline reads and real device pronunciation.

## Delivered

- Room database v1 with a tracked schema, composite account/word keys, saved snapshots, first-learning events and a shared vocabulary cache.
- Atomic save/event transactions, case-insensitive deduplication and account-scoped deletion. Resaving an existing word does not alter its original save date; deleting and resaving never duplicates the first-learning event.
- Authenticated published catalog and case-insensitive lookup endpoints. SQL filters publication status; drafts and missing words return structured `404` responses.
- Full paginated catalog download followed by a transactional cache replacement. A failed page leaves the previous catalog intact.
- Cache-first result screens, explicit content refresh, loading/error notices and no invented dictionary entries or phonetics.
- Actual Home/My Words/Stats data: profile name/goal, saved library, first-save daily counts, seven local calendar days, streak and all represented CEFR levels.
- Cached profile restoration on connection failure; account rejection clears the session. Token refresh transport failures preserve credentials. Navigation is recreated when account identity changes.
- Android Text-to-Speech with asynchronous initialization, English availability checks, install-voice action, speaking/error state, stop on background and shutdown on disposal.
- Learning database, tokens and cached profile excluded from cloud backup and device transfer.
- Sample word/dictionary services removed; README demo, data ownership, API contract and limitations updated.

## Verification

Commands from the repository root (JDK 21 and Android SDK 36.1):

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --max-workers=2
.\backend\gradlew.bat -p backend test postgresTest bootJar --max-workers=2
git diff --check
```

Android tests include database close/reopen persistence, cross-account isolation, normalization/deduplication, delete/resave event history, offline cache access, draft eviction, HTTP authentication rejection and partial catalog refresh failure. Calendar tests cover empty activity, gaps, yesterday's streak and local timezone boundaries.

Backend tests cover authenticated access, draft publication/unpublication, absent words, case-insensitive lookup, translation search and pagination bounds. PostgreSQL integration tests exercise all migrations, recognition ownership/report constraints and published lookup/search on a real container.

Validated locally on **2026-10-11 (Asia/Saigon)** with JDK 21.0.10, Room 2.8.5, KSP 2.3.6 and Robolectric 4.16.1.

| Check | Result |
| --- | --- |
| Android unit / SQLite tests | 28 tests, 0 failures/errors/skips |
| Android lint | 0 errors, 29 warnings (existing dependency/platform and UI warnings remain visible) |
| Android debug build | Passed; APK generated |
| Backend H2 API tests | 10 tests, 0 failures/errors/skips |
| PostgreSQL integration | 3 tests, 0 failures/errors/skips; real disposable PostgreSQL 15 container |
| Backend packaging | Executable boot JAR generated |
| Git patch formatting | Passed |

The PostgreSQL regression exposed publication filtering after an out-of-band SQL update inside a shared persistence context. Moving the published predicate into the lookup SQL resolved it, and all suites passed on the final code. GitHub CI results are available from the README badge after push; local results above are independent of the hosted runner.

## Device verification checklist

These checks require an emulator/phone and are not claimed by JVM tests:

1. Login, download the catalog and save a detected word. Kill/reopen the app and verify the library.
2. Log out, create a second account and verify its empty library; return to the first account.
3. Disconnect networking after a successful login/download, reopen and browse saved/downloaded words.
4. Update a published definition in admin; use result **Refresh content** and verify the update.
5. Unpublish a word: refresh the catalog and verify shared content is removed; a personal saved snapshot shows its notice.
6. Play a word and background/navigate away; verify audio stops. Test with and without an installed English voice.
7. Save the same word twice, delete and resave it; daily new-word activity must not increase.

## Remaining boundaries

The library is persistent on one installation, with no server sync or durable outbox yet. Uninstall removes local learning data. Offline snapshots cannot immediately reflect server-side revocation or publication changes. TTS voice availability/audio quality and physical-device model accuracy remain device checks. Spaced repetition, retention metrics, background retry and cloud conflict resolution belong to later milestones.
