# Tuần 8 — Room v5 đã đạt full gate

Lượt mới nhất14/10/2026 (+07). `feature/analytics-consent` tại d96216f đã push lên origin. Roomv5 trên nhánh `codex/local-storage`, từ checkpoint đó, commit bước `Week 8 Room v5: preserve legacy data and add analytics topic storage`. Không merge main hoặc mở PR. Dừng sau Roomv5, analytics Android/camera chưa bắt đầu.

## Phạm vi đã viết

Migration4→5 chỉ CREATE bốn bảng/index; không ALTER/UPDATE/DROP bảng cũ. Room compiler đã export5.json, identity b5005cc0f47f6c288fa5c0e60cf1b585, SHA256 LF7f39cf91829490a33762abc9c93d69638be5f1284a1a413cbb1214b5ba1fd455. Schema5 được đưa vào commit bước Roomv5. JSON1–4 và migration backendV1–V15 nguyên trạng; không áp migration vào database mục tiêu.

| Bảng mới | Khóa / phạm vi |
| --- | --- |
| AnalyticsConsentState | accountId PK; enabled=false mặc định, consentVersion/revision/at, pendingOptOut/updatedAt. Projection server, không tự bật consent cho account cũ |
| AnalyticsOutbox | PK(accountId,eventId); FK accountId→ConsentState CASCADE; session/attempt IDs, occurredAt/queuedAt epochms, timezone/appVersion/propertiesJSON/hash/revision/state/retry/error. Không FK vào queue bài hoặc từ/review |
| TopicCache | id PK, slug UNIQUE, title/version/fetchedAt epochms. Nội dung công khai dùng chung theo kế hoạch đã duyệt |
| TopicVocabularyCache | PK(topicId,word), displayOrder; FK topic→TopicCache CASCADE. Không thay SavedWordEntity theo account |

DAO chỉ đọc/ghi storage; chưa worker, consent UI, API client/hooks hoặc camera. Builder AppContainer thật có MIGRATION_4_5 và không destructive fallback.

## Bằng chứng mới

AnalyticsDatabaseMigrationTest **7/7 đạt**, ProductionRoomConfigurationTest **1/1 đạt** trên Room/Robolectric. Đây chưa là instrumentation hoặc test upgrade APK trên emulator/thiết bị.

| Test | Kiểm chứng |
| --- | --- |
| exported v4 two account upgrade preserves every column row and old schema | Fixture tạo bằng createSql/indices của JSON4 export ở commit6234a03, không viết tay schema. 12 bảng/31 rows, mọi tên cột/giá trị/null trước–sau; DDL/index/FK cũ nguyên trạng. Hai account có words/events/cache, PENDING/BLOCKED legacy queue giữ sequence/UUID/payload/retry/error, SyncAccount, review cards/events/session dở, content pack, lesson session dở, hai trạng thái lesson outbox, curriculum/progress/XP local. Mở lạiRoom5, đọc/ghi SavedWord |
| all exported versions one through four upgrade to five without losing original rows | Các đường1→5,2→5,3→5,4→5; snapshot từng bảng đã có, oldDDL và reopen; ReviewCard backfill phiên bản cũ vẫn đúng |
| failure halfway through four to five rolls back DDL version identity and data | Inject lỗi sau CREATE thứ ba trong migration transaction. Không có bảng mới ghi dở, user_version vẫn4, identity_hash cũ, oldDDL/rows nguyên trạng; lần mở đúng tiếp theo thành5 |
| new consent queue and topic tables read write reopen isolate accounts and cascade only their rows | Cùng eventId ở hai account; giữ payload qua reopen; không đọc được account3; xóa ConsentA chỉ cascade analyticsA, B/oldtables nguyên trạng. Topic mapping đọc/ghi và cascade riêng |
| analytics and topic foreign keys reject missing parents | Không enqueue nếu thiếu consent của account đó, không cache membership nếu thiếu topic |
| compiler exported v5 contains exactly four new tables and unchanged v4 entity definitions | Schema5 chỉ thêm đúng bốn entity; entity JSON của cả12 bảng v4 giống nguyên bản |
| fixture schemas one through four remain byte identical to committed exports after LF normalization | SHA256 LF pin cho export1–4, không regenerate/viết tay fixture schema |

## Full gate mới nhất

| Phần | Thời gian14/10 (+07) | Kết quả |
| --- | --- | --- |
| Android test/lint/APK command | 13:10:35–13:13:00 | 141/141 tests, 0fail/error/skipped |
| Backend unit | Command bắt đầu13:10:36, XML13:11:31 | 48/48 tests, 0fail/error/skipped |
| postgresTest | Cùng backend command, kết thúc13:14:17 | 70/70 PostgreSQL thật, 0fail/error/skipped, không initialization error; exit0 |
| Admin build | 13:10:35–13:10:43 | tsc/vite exit0; đây là build |
| Lint mới | XML13:12:57 | 0errors, 38warnings, baseline38, new0 |
| APK Roomv5 | File mới13:12:59 | assembleDebug đạt; chưa cài/test nâng cấp trên thiết bị |

APK `app/build/outputs/apk/debug/app-debug.apk`, SHA256 `1FD5B9B37079B4FFB58F9F6A00C1352FA9013FD6AF7C505F5E57825E30FD125D`.

Hai builder fixture ReviewRepositoryTest và SyncEngineTest đã đăng ký MIGRATION_4_5; giữ nguyên mọi assertion, production migration và JSON1–4. Test migration cũ tương ứng, cả7 test mới,5 test LessonDatabaseMigrationTest và ProductionRoomConfigurationTest đều đạt. Test guard production giữ trong CI.

## Phạm vi chưa làm / giới hạn

Storage migration được kiểm bằng Room/Robolectric có file SQLite và schema export thực; chưa có instrumentation/owner test APK trên emulator. Upgrade không uninstall, process death/vuốt tắt/reboot/hai account trên thiết bị: **Đạt một phần – chưa kiểm chứng trên thiết bị**; không lấy unit test thay bằng chứng thiết bị.

REVIEWED chưa có biên bản; DEMO/prod guard giữ nguyên. Preflight/backup/restore hoặc V15 trên database mục tiêu chưa thực hiện. Không thêm thư viện/dịch vụ, không destructive fallback, không sửa V1–V15 hoặc JSONRoom1–4. Worker/consent UI/hooks analytics và camera–chủ đề nằm ở bước sau, chưa làm trong checkpoint này.

