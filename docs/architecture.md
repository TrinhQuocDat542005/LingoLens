# Architecture and development decisions

## Boundaries

Android captures images, corrects orientation, checks quality and runs a bundled EfficientDet-Lite0 detector. Supported labels map to canonical vocabulary words. When there is no supported detection, ML Kit labels the whole image; manual selection remains available. Fallback labels do not have detector bounding boxes.

Images stay in Android cache and are cleaned after 24 hours when creating another capture. The API receives metadata and correction reports, not images. History synchronization currently records the primary detection, not every selected object.

The API owns accounts, roles, hashed refresh tokens, published vocabulary, recognition history, correction reports and admin audit logs. Flyway migrates PostgreSQL at startup; Hibernate validates the schema. Admin vocabulary is not yet connected to Android's sample dictionary.

Android's word list is currently a process-wide in-memory sample repository. It is not persistent or account-scoped. Room, user ownership and background sync are the next milestone.

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
- Android: unit tests for validation, labels/confidence, quality and bounding boxes; lint and APK assembly.
- Admin: locked dependency installation, TypeScript and Vite build.
- Compose CI: image build, database readiness, migrations and API health.
- Physical device: permissions/orientation, selecting boxes, lifecycle and model quality. JVM tests do not replace these checks.

## Next milestone

Use a local database as Android's read source, record learning operations locally and drain an account-scoped outbox through persistent background work. Stable operation IDs prevent duplicate events on retries. Reference: [Android offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first).
