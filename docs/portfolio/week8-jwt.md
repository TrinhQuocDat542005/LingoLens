# Tuần8 — checkpoint JWT gắn identity

Nhánh `feature/identity-bound-auth` từ `feature/question-types-tts` tại `4b2c65e`. Đây là bước JWT theo6B, sau khi test trước/gate đầy đủ mới commit. Dừng tại checkpoint này để chủ dự án duyệt; chưa schema analytics/consent/batch, Roomv5 hoặc camera–topic. Không merge main/PR#4, không mở PR tuần7.

## Thay đổi và hợp đồng

- JWT thêm `userId` numeric positive từ persisted User, không cho extraClaims ghi đè. `sub` email và response JSON auth giữ nguyên để principal/adapter/logout tương thích; filter lookup theo userId và kiểm email hiện hành. Role/disabled status lấy từ DB, không tin roles trong JWT. Legacy thiếu claim, string/fraction/negative/missing account/mismatched email bị401; expired và tamper vẫn bị từ chối.
- V14 `revoke_legacy_refresh_tokens` chỉ UPDATE revoked_at=CURRENT_TIMESTAMP với refresh token chưa revoked. Đây là chuyển tiếp có chủ đích để login lại một lần; refresh token vẫn opaque/hash/FK, không thành JWT. Không xóa rows/hashes/device_info/user/password/settings/learning/review/attempt/answer/progress/XP. Token đã revoked giữ timestamp; Flyway chỉ chạy một lần, token mới sau rollout không bị thu hồi khi restart.
- Android login mới clear cached profile kể cả cùng email, tạo generation riêng cho mỗi lần login; refresh giữ generation. Profile response được ràng buộc với generation lúc request, response từ login cũ không ghi đè account mới. Chỉ metadata DataStore thay đổi, Room v4 và queue cũ giữ nguyên. Test codec có injection để kiểm DataStore; production vẫn AES/GCM/AndroidKeyStore, không thêm crypto/service/library mới.
- Reregister email là identity mới. Queue cũ vẫn ở old accountId, không copy/rekey/import vào account mới. Chưa làm full account deletion API/UI; test physical deletion là fixture, không nghiệm thu deletion production.

Hợp đồng tuần8/điều chỉnh D1D7/retention đề xuất và liên hệ web placeholder tại [week8-approved-contracts.md](../product/week8-approved-contracts.md), đặc tả§16. REVIEWED import **bỏ qua/chưa làm**, vì chưa có biên bản người soạn/người duyệt khác/ngày/checksum; không relabel DEMO, production guard giữ nguyên. Backup/log retention vẫn là đề xuất chờ hosting, không cam kết. Analytics là phần cắt đầu tiên nếu thời gian căng, chưa tự cắt hoặc nhận hoàn thành.

## Test trước và bằng chứng

| Test | Hành vi |
| --- | --- |
| IdentityBoundAuthTests (4 H2) | Login/refresh cùng stable ID, response field types/names giữ; refresh rotation/logout; legacy/malformed/wrong ID qua profile/curriculum/recognition/sync/admin boundaries; expired/tamper/role forgery/reserved claim; cross-account logout và logout-all |
| IdentityBoundAuthPostgresTests (2) | PostgreSQL thật: old access+refresh sau xóa rồi đăng ký lại cùng email ra ID mới đều401; pending old word request không replay bằng old access hoặc new access (403 account mismatch); DB mới không có entries/receipts cũ; published/archived pack authenticated không hỏng |
| IdentityTokenRolloutMigrationTests (1 PostgreSQL) | V13 có hai account B1/B2/goals khác, token/roles/settings/library/review/receipts/attempts/answers/progress/XP/content →V14 so từng row/field; chỉ active refresh revoked_at thay; already-revoked timestamp nguyên; indexes/checksums/physical heap giữ; second Flyway run giữ new token active |
| SessionIdentityStorageTest (2 Robolectric) | Sameemail freshlogin bỏ cached oldID; stale profile không ghi lại; reopen giữ newID; normalrefresh giữ profile/generation; lateoldrefresh/clear không đè login mới |
| SyncEngineTest bổ sung1 | Old word/review queue không dispatch/adopt vào new accountId; giữ old UUID/sequence/payload/rows |
| LessonSyncEngineTest bổ sung1 | Old frozen lesson UUID/payload/queue không dispatch/copy vào new identity; new identity không nhận XP cũ |

Hồi quy toàn bộ hiện có chạy lại: profile adapter/mirror rollback, admin role removal/disabled, login-refresh FK với profile PUT đồng thời, word/review sync ownership/retries, lesson grading/retry/concurrency/XP/archived pack/locale/golden bytes, Room migration/lifecycle và TTS. Không nới assertions hoặc xóa test. Tests H2 kiểm API; migration/index/concurrency vẫn PostgreSQL thật.

MigrationChecksums giữ nguồnV1–V13 nguyên vẹn. V14 có SHA256LF pin tại [week8-jwt-migration-checksum.json](week8-jwt-migration-checksum.json), test đối chiếu nguồn và ghi checksum Flyway thành công; không chỉ đổi13→14 trong assertion đếm migration. V14 không đổi schema/heap hoặc SQL khóa production. Full deletion/restore race tests thuộc giai đoạn deletion, chưa gọi là đã hoàn thành ở checkpoint này.

## Gate mới ngày13/10/2026, UTC+07

| Phần | Kết quả | Giờ |
| --- | --- | --- |
| Android unit/Robolectric | 130/130, 0 failure/error/skipped | 21:18:00–21:21:40 |
| Backend unit/H2 | 41/41, 0 failure/error/skipped | Backend21:18:05–21:22:43; unitXML21:19:47 |
| PostgreSQL15 Testcontainers | 55/55, 0 failure/error/skipped/initialization error | Trong cùng lệnh backend; XML21:22:43 |
| Lint mới | 0 errors/38 warnings/0 new | XML21:21:36, Assert-LintBaseline đạt |
| Admin TS/Vite | Build đạt, không gọi là test | 21:18:03–21:18:13 |
| APK debug | Build đạt, packageDebug chạy lại | APK21:21:38, endpoint emulator10.0.2.2:8080 |

APK SHA256: `85CBA179AC816AC57089F0B53DF29D1A8844FD9CD183B449FC54CB411B91950C`.

PostgreSQL class counts: IdentityBoundAuthPostgresTests2, IdentityTokenRolloutMigrationTests1, AttemptConfirmationMigrationTests1, LessonAttemptPostgresTests8, OfflineAttemptPostgresTests8, Week7QuestionPostgresTests6, ContentPackPostgresTests4, ContentSeedTests8, PostgresIntegrationTests9, LearnerSettingsMigrationTests4, LearnerSettingsPreflightTests4. Tổng55, không initialization errors. Android130=baseline126+4; backend41=37+4; PG55=52+3.

## Triển khai/test tay và phần chưa làm

- Chưa chạy V14 trên database dev/mục tiêu của chủ dự án; chỉ H2/PostgreSQL test fixtures. Khi thật sự rollout phải backup/preflight và stop backend phiên bản cũ trước migration/newserver, tránh cấp token cũ trong lúc rollout. Không downgrade backend tuần7 vào cùng DB sau khi đã applyV14 hoặc sửa Flyway history để qua validation.
- APK `app/build/outputs/apk/debug/app-debug.apk` hiện thuộc **checkpoint JWT tuần8**, không phải APK tuần7. Chủ dự án test5→7 phải build từ feature/question-types-tts theo week7-testing.md trước, dùng backend/db tương ứng; không gọi installAPK này là test5→7.
- Khi muốn test checkpointJWT: backup DB local theo [week7-testing.md](../product/week7-testing.md), install-r APK nhánh này; login lại một lần khi backendV14 bật, kiểm saved words/review/pending lessons account cũ vẫn còn và drain sau login. Không uninstall/clear data.
- Chưa instrumentation/hardware walkthrough, upgradeAPK5→7/8 hoặc AndroidKeyStore thực mới; fake cipher/DataStore/Robolectric không chứng minh các ca thiết bị đó. Giữ mức một phần cho thiết bị, không gọi fixture deletion là luồng xóa thật.
- Chưa REVIEWED import, analytics/schema/consent/batch/Roomv5/camera–topic. Chưa web deletion/privacy UI; manual contact placeholder đã ghi nhưng địa chỉ chưa có. Preflight database mục tiêu/hosting backup-log chưa xác nhận. Không thêmlibrary/service hoặc đổi production guard.

Chờ chủ dự án duyệt checkpoint JWT và nói tiếp tục trước các bước còn lại.
