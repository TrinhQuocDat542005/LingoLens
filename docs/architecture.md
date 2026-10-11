# Architecture and development decisions

## Boundaries

Android captures images, corrects orientation, checks quality and runs a bundled EfficientDet-Lite0 detector. Supported labels map to canonical vocabulary words. When there is no supported detection, ML Kit labels the whole image; manual selection remains available. Fallback labels do not have detector bounding boxes.

Images stay in Android cache and are cleaned after 24 hours when creating another capture. The API receives metadata and correction reports, not images. History synchronization currently records the primary detection, not every selected object.

The API owns accounts, roles, hashed refresh tokens, published vocabulary, recognition history, correction reports and admin audit logs. Flyway migrates PostgreSQL at startup; Hibernate validates the schema. Android reads only published vocabulary through authenticated catalog/lookup endpoints.

Room is Android's persistent library and catalog store. Saved words and first-learning events use `(accountId, normalizedWord)` composite keys. Every read/write supplies the signed-in account ID, and navigation is recreated when the account changes. Save and first-event insertion are atomic; deleting a library entry leaves historical learning activity intact.

The shared catalog contains published vocabulary only. A full refresh stages all pages before replacing cache rows in a single transaction. Saved personal snapshots are separate from the catalog: withdrawing publication removes shared content at the next successful catalog refresh or lookup, but preserves words the learner previously saved. Cache content is explicitly labeled and manual refresh checks the server. This is a snapshot cache, not a server revocation mechanism.

Learning statistics use first-save events and local calendar dates. The total library count changes on deletion; historical daily counts do not. Pronunciation uses the device TTS engine, checks English language availability, stops on lifecycle pause and shuts down when its composition is disposed.

Session restoration caches a matching profile after successful server calls and uses it only on network failures. HTTP authentication rejection clears the session; transient refresh transport failures do not. Tokens, profiles and the learning database are excluded from backup and device transfer. Local data remains after logout so the same account can recover its library on the same installation; uninstalling removes it.

## Separate builds in a monorepo

Android and the API have independent Gradle wrappers; admin has its own npm lockfile. CI checks them separately, so API work does not require an Android SDK. Docker builds the backend with JDK 21 and runs it with a JRE and a non-root user.

## Environment configuration

Android debug defaults to the emulator host alias and supports Gradle/environment overrides for a phone. Release artifacts require an explicitly configured HTTPS endpoint. Source-set resource overrides permit HTTP only in debug.

The API defaults to `dev`, importing an optional root `.env` as Java properties. `prod` requires deployment environment values and does not import that file. CORS defaults to local admin origins; deployments supply an allowlist. Admin uses Vite's public build-time API URL.

References: [Spring configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html) and [Android network security configuration](https://developer.android.com/privacy-and-security/security-config).

## Account-state enforcement

The JWT filter verifies tokens and loads the current database user. Disabled users receive HTTP 401 / `ACCOUNT_DISABLED`; role changes affect authorization without waiting for token expiry. Admin disabling also revokes outstanding refresh tokens.

Refresh tokens are stored as SHA-256 hashes and rotated. Logout revokes refresh tokens; an already issued access token otherwise remains valid until expiry while the account is enabled. Re-enabling an account can make an unexpired access token usable again. Permanent access-token revocation and concurrent refresh coordination are future decisions.

JWT key length is checked at startup. The public example development secret is for local use only.

## Verification boundaries

- H2 API tests: auth lifecycle, disabled users, removed admin role, validation and recognition/report flow.
- `postgresTest`: all migrations, seed content, recognition ownership and duplicate reports on a real container. Missing Docker fails this task instead of silently skipping it.
- Android: validation/recognition tests, date/streak tests and Robolectric SQLite tests for Room persistence, account isolation, deduplication, cache failures and publication withdrawal; lint and APK assembly.
- Admin: locked dependency installation, TypeScript and Vite build.
- Compose CI: image build, database readiness, migrations and API health.
- Physical device: permissions/orientation, selecting boxes, lifecycle and model quality. JVM tests do not replace these checks.

## Next milestone

Use a local database as Android's read source, record learning operations locally and drain an account-scoped outbox through persistent background work. Stable operation IDs prevent duplicate events on retries. Reference: [Android offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first).
