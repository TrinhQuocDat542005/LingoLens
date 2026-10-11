# Tuần 6 — Bước 1: Room v4 và migration bảo toàn dữ liệu

Ngày 11/10/2026, nhánh `feature/offline-lessons`. Hoàn thành bước 1 sau khi chủ dự án cho sửa fixture; **dừng chờ tiếp tục trước bước 2**. Không merge main, chưa làm pack/backend offline/session UI/sync hoặc bước 6–7.

## 1. Xác nhận lỗi cũ và sửa fixture

Đọc XML trước sửa: cả năm testcase của LessonDatabaseMigrationTest đều `java.lang.NullPointerException` tại `fixture():38`, do đọc entity.indices vắng mặt như field bắt buộc. Không có lỗi khác trong năm test ở lượt đó; chúng chưa chạy tới migration.

Sửa thành đọc indices tùy chọn, thiếu field tương đương danh sách rỗng; nếu có thì tạo toàn bộ index trong schema. Không xóa test, skip test hoặc nới assertion. Các arrayOf chứa giá trị hỗn hợp được khai báo Array<Any> để loại cảnh báo intersection type; không thay dữ liệu fixture.

## 2. Schema/migration được thực hiện

- Room `LearningDatabase` v3→v4, thêm ContentPack, LessonSession, LessonAttemptOutbox, CurriculumAccountState và LessonDao.
- Mọi bảng mới dùng composite PK bắt đầu bằng accountId; FK session→pack và outbox→session cũng gồm accountId, NO ACTION, không cascade xóa pack đang dùng.
- MIGRATION_3_4 chỉ CREATE TABLE/INDEX. Không sửa bảng cũ, MIGRATION_1_2/MIGRATION_2_3, V1–V12 hoặc DataStore/TokenCipher. AppContainer đăng ký đủ đường 1→2→3→4, không destructive fallback.
- File `app/schemas/com.quocdat.lingolens.data.local.LearningDatabase/4.json` do Room/KSP sinh và được commit cùng bước 1; không viết tay schema JSON.
- Hai builder test sync/review cũ được thêm MIGRATION_3_4, giữ nguyên assertions và nghiệp vụ.

Schema fixture v1–v3 đọc trực tiếp JSON Room đã xuất và lưu từ lịch sử repo:

| Schema | Commit chứa lần thay đổi gần nhất |
| --- | --- |
| 1.json | 526fe07 — persist account learning and connect published vocabulary |
| 2.json | f5ca6da — durable account sync with idempotent operations |
| 3.json | 6f0bca7 — offline spaced review with shared scheduling and durable sync |

Fixture không viết tay DDL entity/index cũ: lấy createSql, primary keys và index SQL từ các JSON đó. Dữ liệu mẫu được tự tạo; room_master_table dùng identityHash của đúng schema. So inventory SHA-256 [bước 0](week6-baseline-files.json) sau test: V1–V12/resources và Room v1–v3 không đổi.

## 3. Từng test mới kiểm chứng gì

Nguồn: `app/src/test/java/com/quocdat/lingolens/lesson/LessonDatabaseMigrationTest.kt`. Cả năm test dùng database SQLite trên đĩa trong Robolectric và Room thật để mở/migrate/validate; không phải fake DAO hoặc chỉ tạo DB mới in-memory. Không coi đây là instrumentation trên thiết bị thật.

| Tên test | Kiểm chứng / assertions |
| --- | --- |
| `v3 upgrade retains every legacy row and opens real v4 database` | Dựng v3 có **hai account**; SavedWordEntity, LearningEvent, VocabularyCache, OutboxOperation, SyncAccount, ReviewCard, ReviewEvent, ReviewSession. Chụp toàn bộ cột từng dòng của tám bảng, mở bằng Room v4 rồi so equality trước–sau. Bao gồm saved words/content/timestamp, events, cache, review due/ease/repetitions/lapses, review event/session linkage, session đang dở (finishedAt NULL), outbox PENDING/BLOCKED với **sequence/UUID/payload/retryCount/lastError** và SyncAccount. Kiểm tra DB version 4, review session dở đọc được, outbox từng account giữ nguyên và bốn bảng mới ban đầu rỗng. |
| `v1 and v2 upgrades preserve original words events cache and outbox` | Mỗi vòng dựng từ đúng JSON **v1 hoặc v2**, mở với chuỗi migration đến **v4**. Kiểm tra words/content, timestamp learning event của account 2, cache timestamp; ReviewCard backfill giữ savedAt đúng; v2 giữ UUID outbox cũ. Hai đường 1→4 và 2→4 đều thực thi; test trên bao phủ đường 3→4. |
| `new tables isolate identical identifiers by account and survive database reopen` | Sau migration v3→v4, insert pack/session/outbox/curriculum cho hai account cùng revision/session/attempt/course ID nhưng answers/XP khác nhau. **Đóng Room, mở lại v4**, đọc đúng answers/confirmed XP từng account; account 3 không đọc được pack/session/outbox. Acknowledge account 1 sau reopen chỉ tác động queue account 1; account 2 vẫn pending. Chứng minh cả đọc và ghi sau mở lại. |
| `foreign keys prevent cross account references and removal of referenced pack` | Chỉ account 1 có pack: account 2 không thể tạo session tham chiếu pack đó; account 2 cũng không thể enqueue tham chiếu session account 1. Xóa pack đã có session bị SQLite FK chặn, pack vẫn còn. Assertions bắt buộc exception và không có row sai. |
| `failed durable enqueue rolls back session and outbox together` | Trong Room transaction: lưu session → enqueue → ném lỗi có chủ đích **sau enqueue**. Kiểm tra đúng lỗi injected, session và outbox mới đều không tồn tại; UUID outbox cũ vẫn còn. Không có session/attempt ghi dở. |

Snapshot v3 so giá trị từng cột/dòng, không chỉ count. Các đường v1/v2 kiểm tra những dữ liệu tồn tại ở phiên bản đó và backfill hợp lệ, không giả rằng chúng đã có review tables từ đầu.

## 4. Regression đầy đủ sau sửa

| Kiểm tra | Kết quả mới |
| --- | --- |
| Android unit/Robolectric | **56 tests đạt**, 0 failures/errors/skipped, gồm 51 cũ + 5 mới |
| Backend H2/unit | **23 tests đạt**, 0 failures/errors/skipped |
| PostgreSQL Testcontainers | **33 tests đạt**, 0 failures/errors/skipped |
| Lint | **0 errors, 38 warnings, 0 warning mới** so baseline; gate exit 0 |
| Admin TypeScript/Vite | **Build thành công**, không tính là test |

Tổng **112 tests**, không failure/error/skipped. Không thêm dependency hoặc dịch vụ. Backend không đổi, nhưng test và postgresTest đều được chạy lại, không lấy lượt trước làm kết quả mới.

Lần Android chạy mới kết thúc `BUILD SUCCESSFUL`; explicit task `lintAnalyzeDebug --rerun` và `lintReportDebug --rerun` đều thực thi. XML lint mới có LastWriteTime **11/10/2026 22:18:54 UTC+07:00**, thay report cũ 20:26:40. Gate chỉ chạy sau khi Gradle hoàn tất và xác minh timestamp mới: `Lint gate passed: errors=0, warnings=38, baseline=38, new=0`.

```powershell
# Root, JDK21/Android SDK đã cấu hình
.\gradlew.bat :app:testDebugUnitTest --rerun :app:lintDebug --rerun :app:lintAnalyzeDebug --rerun :app:lintReportDebug --rerun --max-workers=2 --console=plain
.\scripts\quality\Assert-LintBaseline.ps1
# backend
.\gradlew.bat test --rerun postgresTest --rerun --max-workers=2 --console=plain
# admin-dashboard
npm run build
```

Reports: app/build/test-results/testDebugUnitTest, app/build/reports/tests/testDebugUnitTest và app/build/reports/lint-results-debug.xml; backend/build/test-results/test và postgresTest. Không commit build reports.

## 5. Điểm dừng và giới hạn

Chỉ schema/DAO/migration được thêm. Chưa tích hợp Room vào luồng lesson thực tế, chưa tải pack, chưa shared grader, chưa backend pending/offline hoặc lesson sync. Những hợp đồng đó vẫn làm ở bước 2–5 sau cho phép. Không chuyển payload SavedStateHandle cũ sang Room.

Chưa test nâng cấp APK/force-stop/reboot trên thiết bị thật, chưa tuyên bố session UI đã durable. Chưa thay logout hoặc thêm xóa account; schema scoped-account là nền tảng cho các bước sau. Chưa REVIEWED; DEMO dùng nghiệm thu kỹ thuật, gate nội dung trước tuần 8 vẫn còn.

Thay đổi IDE của chủ dự án giữ nguyên và không nằm trong commit. **Dừng sau commit/push bước 1, chờ chủ dự án nói tiếp tục.**
