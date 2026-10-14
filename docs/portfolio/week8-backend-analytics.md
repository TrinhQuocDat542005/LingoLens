# Tuần 8 — checkpoint consent/batch backend đã đạt

Ngày 14/10/2026, múi giờ +07. Nhánh `feature/analytics-consent`, từ checkpoint JWT `e16beda` trên `feature/identity-bound-auth`. Bước này dùng commit `Week 8 step consent/batch: add opt-in analytics and isolate PostgreSQL fixtures`. Không merge main hoặc mở PR; dừng trước Room v5 để chủ dự án test emulator.

## Phạm vi hoàn thành

- V15 thêm bốn cột consent vào learner_settings (false/null/null/0), analytics_events/FK user CASCADE và index time/source. Không sửa nguồn V1–V15 trong các lượt sửa alias/fixture, không sửa Room export1–4. Chỉ áp V15 trong fixture/Testcontainers; **chưa áp vào database dev/mục tiêu của chủ dự án**.
- API authenticated GET/PUT consent có revision CAS; batch có per-item validation/status, UUID/hash/source dedupe, account fence, opt-out/stale generation. Profile API/adapter cũ giữ hợp đồng và không ghi đè consent.
- Server lesson_passed sau commit: chỉ opted-in LESSON CONFIRMED passed, không PRACTICE/WAITING, retry không tạo thêm event. Analytics lỗi không rollback XP/progress; business rollback không phát passed.
- Prune raw90 ngày từ received_at lúc startup/hằng ngày; report luôn lọc hết hạn. D1/D7 truy vấn trực tiếp app_open theo timezone từng event, không lưu cohort/anchor/marker. Mẫu đủ quan sát48 giờ/8 ngày, giới hạn clock/consent/window được ghi trong hợp đồng, không phải lifetime retention.
- SQL runtime dùng `AS event_day`; rà native SQL không tìm thấy alias từ khóa khác cần sửa. Chi tiết tại [week8-native-sql-audit.md](week8-native-sql-audit.md).
- Android: INVALID_TOKEN/ACCOUNT_DISABLED hoặc refresh401/403 kết thúc phiên hiện hành; TOKEN_EXPIRED vẫn refresh tối đa một lần. Response cũ không xóa login mới. Room/saved words/queue giữ nguyên, cùng stable accountId tiếp tục gửi cùng UUID sau login; ID mới cùng email không nhận queue cũ.

Hợp đồng: [week8-analytics-contract.md](../product/week8-analytics-contract.md). Lệnh pg_dump/pg_restore: [server-database-backup.md](../product/server-database-backup.md). Source V15 pin: [week8-analytics-migration-checksums.json](week8-analytics-migration-checksums.json). Không thực hiện backup/restore/preflight trên database mục tiêu.

## Fixture và job isolation

`AnalyticsPostgresTests` ghi lại mọi account do từng test đăng ký, gồm account phụ. AfterEach xóa trong transaction theo đúng FK: analytics/progress/answers/XP/attempts/enrollment/settings/tokens/roles/account, rồi assert account và analytics không còn. Không xóa toàn bảng hoặc account của test khác. Ba event received_at20/03/2026 được dọn cùng account test. Nội dung DEMO/course/roles là fixture dùng chung trong container riêng của lớp; không xóa curriculum bất biến giữa các ca.

Giữ nguyên expected1 và toàn bộ assertion của test retention; không đổi production prune hoặc bỏ test. Các assertion sau dòng formerly113 (sau prune: report bằng nhau, ba rows còn lại, không anchor, ngày trùng không D1, admin endpoint) đều đã chạy và đạt.

Job startup/daily được tách khỏi listener lesson_passed. `application-test.yml` đặt `app.analytics.retention.enabled=false`: bean AnalyticsRetentionLifecycle không được tạo, nên không có startup/daily job dùng real clock xóa fixture. Explicit analytics.prune(queryNow) vẫn chạy thật trong test. Production mặc định enabled: body prune, cron03:00UTC và query không đổi.

Test bảo vệ: AnalyticsRetentionLifecycleTests (2 ca context config mặc định/disabled, ready delegation và scheduled task), AnalyticsPostgresTests.`automatic retention jobs are disabled in PostgreSQL tests` (bean prune vắng, listener passed còn).

## Kiểm riêng và thứ tự khác nhau

| Lượt | Thời gian +07 | Bằng chứng |
| --- | --- | --- |
| Prune riêng + unit job config | 07:27:58–07:28:57 | 1/1 PostgreSQL + 2/2 unit, 0fail/error/skipped |
| Cả lớp analytics, seed20260320 | 07:29:17–07:29:52 | 14/14 PostgreSQL |
| Cả lớp trong full gate, seed20261101 | Full gate dưới đây | 14/14 PostgreSQL; đối chiếu XML cho thấy thứ tự khác |

`@TestMethodOrder(MethodOrderer.Random)` giữ trong CI; Gradle nhận `-PjunitRandomSeed` để tái lập. Ví dụ từ backend/ với JAVA_HOME đúng:

```powershell
.\gradlew.bat postgresTest --rerun --tests '*AnalyticsPostgresTests.retention filters before prune*' -PjunitRandomSeed=20260320
.\gradlew.bat postgresTest --rerun --tests '*AnalyticsPostgresTests' -PjunitRandomSeed=20260320
.\gradlew.bat test --rerun postgresTest --rerun -PjunitRandomSeed=20261101 --max-workers=2 --console=plain
```

## Full gate mới nhất, chạy cùng lượt

Docker Client/Server29.8.0 kết nối được. XML mới của tất cả bộ test: 0fail/error/skipped; không initialization error. Không dùng XML lịch sử để nhận đạt.

| Phần | Thời gian14/10 (+07) | Kết quả |
| --- | --- | --- |
| Android test/lint/APK command | 07:30:04–07:31:46 | 134/134 tests đạt |
| Backend unit | Bắt đầu07:30:06, XML mới07:30:56 | 48/48 tests đạt |
| postgresTest | Cùng command backend, kết thúc07:33:19 | 70/70 tests trên PostgreSQL15 thật; exit0 |
| Lint mới | XML07:31:45 | 0errors, 38warnings, baseline38, new0 |
| Admin build | 07:30:07–07:30:16 | tsc/vite exit0; đây là build |
| APK debug | File mới07:31:33 | assembleDebug thành công; đây là build |

APK `app/build/outputs/apk/debug/app-debug.apk`, SHA256 `069494D4241AE9281D349F8481516D0A4D4065AC66A2E693664D46A4E18E7323`.

| PostgreSQL class | Tests | Fail/error/skipped |
| --- | ---: | --- |
| AnalyticsMigrationTests | 1 | 0/0/0 |
| AnalyticsPostgresTests | 14 | 0/0/0 |
| AttemptConfirmationMigrationTests | 1 | 0/0/0 |
| LessonAttemptPostgresTests | 8 | 0/0/0 |
| OfflineAttemptPostgresTests | 8 | 0/0/0 |
| Week7QuestionPostgresTests | 6 | 0/0/0 |
| IdentityBoundAuthPostgresTests | 2 | 0/0/0 |
| IdentityTokenRolloutMigrationTests | 1 | 0/0/0 |
| ContentPackPostgresTests | 4 | 0/0/0 |
| ContentSeedTests | 8 | 0/0/0 |
| PostgresIntegrationTests | 9 | 0/0/0 |
| LearnerSettingsMigrationTests | 4 | 0/0/0 |
| LearnerSettingsPreflightTests | 4 | 0/0/0 |

## Bằng chứng hành vi quan trọng

- AnalyticsValidationTests (5): allowlist/PII rejection, source IDs/event names, clock90 ngày/+5phút/timezone, canonical hash, metadata bounds.
- AnalyticsPostgresTests: auth/fence/profile isolation; consent CAS/default/no backfill; batch partial failure/conflict/ownership; concurrency opt-out/duplicate; retention/prune/admin; DST/maturity; account cascade; passed confirmed only; analytics/business rollback; automatic job isolation.
- Ba ca bổ sung: `New York 23 and 25 hour DST days count calendar D1 and D7`; `same instants use distinct New York and Ho Chi Minh calendar dates`; `offline app opens arriving D7 D1 then first day use occurred order and frozen UUID`. Cả ba đạt PostgreSQL thật.
- AnalyticsMigrationTests: nâng cấp14→15 có hai account B1/B2/goals khác nhau, so từng row password/roles/settings/token/words/review/receipts/content/enrollment/attempts/answers/progress/XP. Token mới sauV14 vẫn active, partial unique PUBLISHED/ACTIVE hoạt động, learner_settings filenode không đổi. Source/history checksum V1–V15 được kiểm; XP không đổi.
- TokenAuthenticatorTest (8): revoked refresh, missing identity claim, second401/no loop, generation isolation và refresh bình thường. LessonSyncEngineTest (12) gồm `revoked refresh pauses without clearing Room and login resumes same frozen attempt`: saved word/exact session/UUID/payload giữ nguyên rồi tiếp tục CONFIRMED cùng account.

## Chưa làm / giới hạn

- Roomv5, analytics queue/consent UI/worker/hooks Android, TopicCache và camera–chủ đề chưa làm. Room hiện vẫn v4; chưa bắt đầu bước kế.
- REVIEWED import bỏ qua vì chưa có biên bản người soạn/người duyệt khác/ngày/checksum. DEMO tiếp tục chỉ dev/test, production guard giữ nguyên.
- Upgrade APK/emulator, process death/vuốt tắt/reboot và điều hướng reauthentication trên thiết bị: **Đạt một phần – chưa kiểm chứng trên thiết bị**. Room/Robolectric/fake auth không thay instrumentation.
- Preflight/backup/restore trên database mục tiêu, hosting retention và hai luồng xóa account chưa nghiệm thu; không cam kết backup/log retention. Không thêm thư viện/dịch vụ, không destructive fallback, không merge hoặc mở PR.
