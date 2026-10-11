# Architecture and development decisions

## Boundaries

Android captures images, corrects orientation, checks quality and runs a bundled EfficientDet-Lite0 detector. Supported labels map to canonical vocabulary words. When there is no supported detection, ML Kit labels the whole image; manual selection remains available. Fallback labels do not have detector bounding boxes.

Images stay in Android cache and are cleaned after 24 hours when creating another capture. The API receives metadata and correction reports, not images. History synchronization currently records the primary detection, not every selected object.

The API owns accounts, roles, hashed refresh tokens, published vocabulary, recognition history, correction reports and admin audit logs. Flyway migrates PostgreSQL at startup; Hibernate validates the schema. Android reads only published vocabulary through authenticated catalog/lookup endpoints.

Room is Android's persistent library and catalog store. Saved words and first-learning events use `(accountId, normalizedWord)` composite keys. Every read/write supplies the signed-in account ID, and navigation is recreated when the account changes. Save and first-event insertion are atomic; deleting a library entry leaves historical learning activity intact.

The shared catalog contains published vocabulary only. A full refresh stages all pages before replacing cache rows in a single transaction. Saved personal snapshots are separate from the catalog: withdrawing publication removes shared content at the next successful catalog refresh or lookup, but preserves words the learner previously saved. Cache content is explicitly labeled and manual refresh checks the server. This is a snapshot cache, not a server revocation mechanism.

Learning statistics use first-save events and local calendar dates. The total library count changes on deletion; historical daily counts do not. Pronunciation uses the device TTS engine, checks English language availability, stops on lifecycle pause and shuts down when its composition is disposed.

Session restoration caches a matching profile after successful server calls and uses it only on network failures. HTTP authentication rejection clears the session; transient refresh transport failures do not. Tokens, profiles and the learning database are excluded from backup and device transfer. Local data remains after logout so the same account can recover its library on the same installation; uninstalling removes local files; successfully synchronized learning data can be restored after signing in again.

## Separate builds in a monorepo

Android and the API have independent Gradle wrappers; admin has its own npm lockfile. CI checks them separately, so API work does not require an Android SDK. Docker builds the backend with JDK 21 and runs it with a JRE and a non-root user.

## Environment configuration

Android debug defaults to the emulator host alias and supports Gradle/environment overrides for a phone. Release artifacts require an explicitly configured HTTPS endpoint. Source-set resource overrides permit HTTP only in debug.

The API defaults to `dev`, importing an optional root `.env` as Java properties. `prod` requires deployment environment values and does not import that file. CORS defaults to local admin origins; deployments supply an allowlist. Admin uses Vite's public build-time API URL.

References: [Spring configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html) and [Android network security configuration](https://developer.android.com/privacy-and-security/security-config).

## Account-state enforcement

The JWT filter verifies tokens and loads the current database user. Disabled users receive HTTP 401 / `ACCOUNT_DISABLED`; role changes affect authorization without waiting for token expiry. Admin disabling also revokes outstanding refresh tokens.

Refresh tokens are stored as SHA-256 hashes and rotated. Logout revokes refresh tokens; an already issued access token otherwise remains valid until expiry while the account is enabled. Re-enabling an account can make an unexpired access token usable again. Android coordinates refresh attempts and compares the current session before saving rotated credentials or clearing a rejected session. Permanent access-token revocation remains a future decision.

JWT key length is checked at startup. The public example development secret is for local use only.

## Verification boundaries

- H2 API tests: auth lifecycle, disabled users, removed admin role, validation and recognition/report flow.
- `postgresTest`: all migrations, seed content, recognition ownership and duplicate reports on a real container. Missing Docker fails this task instead of silently skipping it.
- Android: validation/recognition tests, date/streak tests and Robolectric SQLite tests for Room persistence, account isolation, deduplication, cache failures and publication withdrawal; lint and APK assembly.
- Admin: locked dependency installation, TypeScript and Vite build.
- Compose CI: image build, database readiness, migrations and API health.
- Physical device: permissions/orientation, selecting boxes, lifecycle and model quality. JVM tests do not replace these checks.

## Durable synchronization

Room v2 adds a FIFO outbox and per-account sync metadata through a non-destructive migration. Save/delete and upload intent commit together. Recognition and corrections queue metadata locally; images remain on-device. Startup/session recovery and WorkManager connected jobs drain the current account only, using stable UUIDs and exponential retry. Periodic jobs cover the gap between a Room commit and WorkManager enqueue.

The sync API checks expected account identity before effects or reads. Each account's mutation transaction locks its user row, applies the effect and stores a payload hash/receipt. This serializes concurrent duplicate requests; failed transactions leave neither receipt nor effect. Recognition corrections depend on an already acknowledged recognition UUID. Receipts and deletion tombstones have no automatic expiry, preserving replay safety.

After draining, the client downloads the account's complete learning snapshot and merges it in one Room transaction. Words with a new local operation during download are protected. First-learning timestamps merge to the earliest date; save/delete conflicts use server arrival order. This is explicit eventual consistency, with no version-vector or manual merge UI. See [sync protocol](sync-protocol.md).

References: [Android offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first), [WorkManager versions](https://developer.android.com/jetpack/androidx/releases/work) and [unique work policies](https://developer.android.com/reference/androidx/work/ExistingWorkPolicy).

## Spaced review

Room v3 adds account-scoped review cards, immutable answer events and installation-local sessions. Due queries join the saved library. A grade transaction updates the schedule and cursor, appends history and queues a stable UUID; deleting a word preserves its prior schedule and answers. Explicit 1 → 2 → 3 migration initializes old libraries.

Android and Spring compile the same dependency-free Kotlin scheduler from `shared/`; the backend Docker build copies that directory as well as `backend/`. Four grades select documented learning steps and bounded day intervals. Flyway V8 backfills cloud cards; account locking and transactional receipts protect concurrent review retries. Full learning and review snapshots restore schedules/history while pending words remain protected. See [algorithm and user flow](spaced-review.md).

Review statistics report actual answer activity and self-reported recall; they do not assert measured retention or proficiency. Streak includes both saving and reviewing; the new-word goal retains its first-save semantics.

## Next milestone

Physical-device failure testing, model benchmarks, demo video and release.
