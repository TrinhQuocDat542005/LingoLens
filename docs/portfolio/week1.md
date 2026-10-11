# Portfolio week 1 — reproducible foundation

Scope: environment configuration, account-state enforcement, CI and documentation. Persistent learning and spaced repetition are later milestones.

## Implemented

- Configurable Android URLs, emulator default, debug-only HTTP and required release HTTPS endpoint.
- Root environment example covering database, JWT, admin bootstrap and CORS; development file import and explicit production configuration.
- Compose database readiness and backend image with JDK/JRE 21 and a non-root runtime user.
- Disabled-user enforcement for existing JWTs and startup key-length validation.
- Regression tests for account disable and removed admin role.
- Dedicated PostgreSQL migration/seed and recognition ownership/report integration task.
- Testcontainers aligned to 1.21.4 for the current Docker Engine API; Gradle wrapper line endings normalized for Linux through `.gitattributes`.
- CI jobs for Android, backend, admin and Compose; reports and artifacts retained.
- Pinned admin dependencies, fixed dev/preview port and ignored generated TypeScript metadata.
- README and architecture documentation aligned with actual features and limitations.

## Verification

Validated locally on **2026-10-11 (Asia/Saigon)** with JDK 21.0.10, Node 24.21.0, Docker Engine 29.8.0 and Compose 5.5.1.

| Check | Result |
| --- | --- |
| Android `testDebugUnitTest lintDebug assembleDebug` | Passed: 20 tests, no failures/skips; APK generated. Lint: 0 errors, 29 warnings. |
| Backend `test bootJar` | Passed: 7 H2 API tests; executable JAR generated. |
| Backend `postgresTest` | Passed: 2 tests on a real PostgreSQL container; all six migrations, seeds and ownership/report flow checked. |
| Admin `npm ci` / `npm run build` | Passed: pinned lockfile install, TypeScript and Vite. Build metadata stays in ignored cache. |
| Release endpoint guard | Missing URL and HTTP rejected; explicit HTTPS accepted by `preReleaseBuild`. No release was published. |
| Workflow syntax | Passed `rhysd/actionlint:1.7.7`, including shell checks. |
| Docker clean build/start | Passed in an isolated project using ports 18080/15433; health `UP`, recognition paths in OpenAPI, six successful migrations, runtime UID 999. |
| Development `.env` | Passed using an isolated fixture: local database connection, admin bootstrap/login and allowed/rejected CORS origins. |
| Production configuration | With the fixture `.env` still present, startup rejected missing deployment `JWT_SECRET`, confirming dev-file isolation. |
| Weak JWT key | Rejected at startup with `WeakKeyException`. |
| Model integrity | Bundled model SHA-256 matches the documented value. |
| Git patch formatting | `git diff --check` passed. |

The first container build caught Windows CRLF wrapper execution; normalization fixed it and the Linux build passed. The initial PostgreSQL run exposed an old Testcontainers/Docker API mismatch; the dependency update fixed it and both integration tests passed. These failures were resolved before the final results above.

GitHub CI itself has not been run because changes have not been pushed. Local validation and workflow syntax checks do not substitute for the first GitHub runner execution. Remaining Android lint warnings and Gradle deprecation notices are not suppressed; framework/dependency maintenance remains follow-up work.

## Manual device checks

- Install the debug APK with the correct emulator/LAN endpoint.
- Register/login, capture a well-lit scene and select a bounding box.
- Check history and submit a correction that appears in admin.
- Disable the user in admin and verify the next API request is rejected.
- Measure model quality using the existing evaluation protocol.

Device measurements and demo media require real-device evidence and are not claimed by this milestone.
