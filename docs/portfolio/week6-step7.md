# Tuần 6 — Bước 7: ma trận nghiệm thu kỹ thuật

Ngày **13/10/2026**, múi giờ **Asia/Saigon**. Nhánh `feature/offline-lessons`; code bước 0–6 đến commit `7533538`. **Bước 7 đạt gate nghiệm thu kỹ thuật** sau lượt kiểm tra chung mới. Các tiêu chí thiết bị vẫn đạt một phần; chưa merge main. Bước 7 nằm trong commit chứa báo cáo này; PR được mở sau commit theo yêu cầu chủ dự án.

Chủ dự án đã cho phép bỏ checkpoint test tay trước nghiệm thu kỹ thuật và mở PR tuần 6. Quyết định này không cung cấp bằng chứng thiết bị: mọi tiêu chí chỉ có Room/Robolectric hoặc chưa ai kiểm chứng trên thiết bị giữ trạng thái **Đạt một phần – chưa kiểm chứng trên thiết bị**. Nội dung DEMO được phép dùng cho nghiệm thu kỹ thuật; REVIEWED vẫn là gate trước tuần 8.

## Lịch sử điều kiện dừng và phạm vi bước 7

[Báo cáo dừng trước đó](week6-step7-blocked.md) giữ lại lịch sử, không phải kết quả gate cuối:

- Đặc tả §14.1 từng ghi locale en-US nằm trong manifest, trong khi wire thực tế chỉ có `LessonPack.pronunciationLocale`. Chủ dự án chọn **A**, đính chính vị trí trường trong tài liệu, giữ nguyên manifest và code production.
- Bổ sung test locale: seed giữ en-US, từ chối locale khác; Android từ chối pack locale khác dù checksum hợp lệ; PostgreSQL kiểm pack server xuất en-US. Không tự chuyển đổi locale, không thêm field vào manifest.
- Lượt PostgreSQL trước bị lỗi khởi tạo Testcontainers do môi trường Docker, chưa chạy assertions. Sau khi môi trường Docker hoạt động, lượt chẩn đoán riêng **09:17:09–09:19:47 UTC+07** chạy **45/45 PostgreSQL tests đạt**, không failure/error/skipped, gồm assertion locale mới. Lượt này chỉ chứng minh PostgreSQL ở thời điểm đó; không thay thế gate đủ bộ bên dưới.
- Gate regression chung **09:20:32–09:25:43 UTC+07** đã hoàn tất: Android 94/94, backend unit 26/26, PostgreSQL 45/45; không failure/error/skipped. Lint mới 0 errors/38 warnings/0 new, admin build và APK đạt. Không lấy report cũ hoặc kết quả chạy riêng để ghi gate chung đạt.

Bước 7 chỉ tổng hợp bằng chứng, đính chính tài liệu và bổ sung assertions locale theo quyết định A. Không thay migration V1–V13, schema Room v1–v4, production guard DEMO hoặc thêm tính năng tuần 7. Không merge main.

## Gate kiểm tra chung ngày 13/10

Docker version kết nối **Client 29.8.0 và Server Linux Engine 29.8.0**, context desktop-linux. Các tiến trình Android/backend/admin được khởi chạy đồng thời trong cùng lượt; giờ bên dưới UTC+07. Backend unit và postgresTest chạy theo thứ tự trong cùng lệnh Gradle. Mốc XML là lúc report phần đó được xuất, không suy đoán thời điểm từng assertion.

| Kiểm tra | Kết quả gate chung | Giờ chạy / report mới |
| --- | --- | --- |
| Android unit/Robolectric | **94/94 đạt**, 0 failure/error/skipped | Lệnh chung 09:20:32–09:25:43, XML mới tại `app/build/test-results/testDebugUnitTest` |
| Backend unit/H2 | **26/26 đạt**, 0 failure/error/skipped | Lệnh backend bắt đầu 09:20:35; unit XML xuất **09:21:36** |
| PostgreSQL 15 Testcontainers | **45/45 đạt**, 0 failure/error/skipped, **0 initialization error** | Trong cùng lệnh backend sau unit; XML xuất **09:25:18**, lệnh kết thúc **09:25:19** |
| Lint analyze/report/debug | **0 errors / 38 warnings / 0 new** | Lệnh chung 09:20:32–09:25:43; XML mới **09:25:42**, Assert-LintBaseline đạt |
| Admin TypeScript/Vite | **Build đạt** | **09:20:33–09:20:41**, npm run build; không tính là test |
| APK debug | **Build/đóng gói lại đạt** | Lệnh chung có packageDebug --rerun/assembleDebug; APK xuất **09:23:59**, endpoint emulator `http://10.0.2.2:8080/` |

APK SHA-256: `76412B4438F8089BD7731A9994FDA42F7ABC71239E7E1688AC59FDD4DA76AAA8`.

| Class PostgreSQL trong lượt chung | Tests thật | Failure / error / skipped / initialization error |
| --- | --- | --- |
| AttemptConfirmationMigrationTests | 1 | 0 / 0 / 0 / 0 |
| LessonAttemptPostgresTests | 8 | 0 / 0 / 0 / 0 |
| OfflineAttemptPostgresTests | 8 | 0 / 0 / 0 / 0 |
| ContentPackPostgresTests | 3 | 0 / 0 / 0 / 0 |
| ContentSeedTests | 8 | 0 / 0 / 0 / 0 |
| PostgresIntegrationTests | 9 | 0 / 0 / 0 / 0 |
| LearnerSettingsMigrationTests | 4 | 0 / 0 / 0 / 0 |
| LearnerSettingsPreflightTests | 4 | 0 / 0 / 0 / 0 |
| **Tổng** | **45** | **0 / 0 / 0 / 0** |

Assertion `pronunciationLocale=en-US` trong ContentPackPostgresTests đã chạy và đạt. Android từ chối pack en-GB/fr-FR dù checksum hợp lệ; backend validator từ chối seed locale không được phép. Manifest không thêm field.

Lệnh gate:

```powershell
# Root, JAVA_HOME JDK21 và ANDROID_HOME SDK đã cấu hình
.\gradlew.bat :app:testDebugUnitTest --rerun :app:lintDebug --rerun :app:lintAnalyzeDebug --rerun :app:lintReportDebug --rerun :app:packageDebug --rerun :app:assembleDebug -PapiBaseUrl=http://10.0.2.2:8080/ --max-workers=2 --console=plain
.\scripts\quality\Assert-LintBaseline.ps1
# Working directory: backend
.\gradlew.bat test --rerun postgresTest --rerun --max-workers=2 --console=plain
# Working directory: admin-dashboard
npm run build
```

## Ma trận từng tiêu chí mục 10 — tuần 6

Phạm vi mục 10 gồm cả ba bài trọn vòng, content pack/session/offline/reconnect và revision/snapshot. Tách các tiêu chí thành hàng kiểm chứng riêng. “Đạt” bên dưới chỉ áp dụng cho phạm vi server/thuật toán có test tương ứng **đã chạy lại trong lượt chung**, không suy rộng thành nghiệm thu thiết bị. Các hàng trước đây chỉ có bằng chứng PostgreSQL lịch sử được cập nhật từ lượt chung 45/45 mới; mọi hàng chỉ có Robolectric giữ mức một phần.

| Tiêu chí | Trạng thái | Test, commit và giới hạn bằng chứng |
| --- | --- | --- |
| Cả ba bài trọn vòng; học mất mạng và mở bài kế bằng kết quả tạm | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `OfflineLessonScreenModelTest.all three offline lessons show provisional counts unlock next and never official XP`, `7533538`. SQLite/Room trong Robolectric và gateway fake; chưa chạy app trên thiết bị |
| Content pack hoàn chỉnh; tải dở/sai checksum/sai revision không dùng được; pack tốt không bị ghi đè | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `ContentPackRepositoryTest.interrupted download never stores a complete pack`, `bad checksum preserves good pack and retry finishes course`, `wrong revision is rejected even with matching checksum`, `incomplete database pack cannot be read or used`, `changed manifest cannot overwrite a good immutable pack` (`18bb4eb`); UI `partial download cannot open course and retry preserves complete pack` (`7533538`). Fake transport/Robolectric |
| Reopen giữ vị trí, đáp án, UUID và revision | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `LessonSessionRepositoryTest.answers position UUID and revision survive actual database close reopen` (`1da4fe4`); UI `empty saved state and database reopen retain UUID answers position and frozen payload` (`7533538`). Database trên đĩa nhưng trong Robolectric; không thay process death thực |
| Nộp offline/reconnect/lost response retry không nhân đôi attempt/XP trên cả vòng | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `LessonSyncEngineTest.lost response and database reopen retry same UUID payload without adding XP locally` (`c6f4fb9`) mô phỏng server đã nhận nhưng response mất. PostgreSQL thật bảo vệ idempotency/concurrency: `LessonAttemptPostgresTests.concurrent same UUID has one attempt and one award`, `concurrent distinct UUIDs have two attempts and one award`; `OfflineAttemptPostgresTests.concurrent pending retries and different UUIDs award only once` (`5bbf850`). Chưa thử mất response/reconnect trên thiết bị |
| Revision cũ ARCHIVED vẫn chấm được, snapshot cũ không đổi | **Đạt** cho server | `OfflineAttemptPostgresTests.archived revision remains gradable without changing snapshot` (`5bbf850`); `ContentPackPostgresTests.pack endpoint requires login and unpublished revisions are rejected` (`18bb4eb`) kiểm bytes pack không đổi sau archive. PostgreSQL thật, lượt chung mới 45/45 đã đạt |
| Session đang học không tự chuyển sang revision mới | **Đạt một phần – chưa kiểm chứng trên thiết bị** | UI `new revision download leaves active session pinned while explicit new attempt uses latest`, `7533538`. Session cũ giữ revision/answers; Học lại sau confirmed mới tạo UUID/revision mới. Room/Robolectric |
| Nâng cấp không mất outbox/library/review tuần 1–4 | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `LessonDatabaseMigrationTest.v3 upgrade retains every legacy row and opens real v4 database`, `v1 and v2 upgrades preserve original words events cache and outbox` (`6234a03`). So từng dòng/cột tám bảng, hai account, sequence/UUID/payload/retry count, SyncAccount và review session dở. Chưa cài đè APK tuần 5→6 trên thiết bị |
| Process death thực, vuốt tắt và reboot | **Đạt một phần – chưa kiểm chứng trên thiết bị** | Có đóng/mở DB và cancel/join coroutine cũ trước reopen trong Robolectric (`7533538`). Chưa instrumentation hoặc thao tác trên thiết bị; không khẳng định OS/WorkManager đã được nghiệm thu |
| Hai account trên cùng thiết bị: logout giữ queue, B không thấy dữ liệu hoặc gửi bài A bằng token B | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `LessonSyncEngineTest.unsent A lesson survives logout and B login without any A dispatch`; `TokenAuthenticatorTest.A account header cannot dispatch using B token after logout login`; UI `account switch shows no old course session answers or queue` (`7533538`). Backend `OfflineAttemptPostgresTests.account header mismatch rejects submit and snapshot before data access` (`c6f4fb9`) đã chạy PostgreSQL thật. Chưa hai account trên máy thật |

## Ma trận các hợp đồng hỗ trợ mục 14

| Hợp đồng | Trạng thái | Test, commit và giới hạn |
| --- | --- | --- |
| Endpoint pack cần login, không trả DRAFT; manifest/checksum/bytes ổn định, snapshot có answer key | **Đạt** cho server | `ContentPackPostgresTests.manifest has three stable immutable packs including answer keys`, `pack endpoint requires login and unpublished revisions are rejected`, `18bb4eb`; PostgreSQL thật |
| Locale đúng vị trí wire: server pack en-US, seed locale khác bị từ chối | **Đạt** cho server/validator | ContentPackPostgresTests assertion pronunciationLocale=en-US đã đạt trong lượt chung; `SeedLocaleTests.en US seed is accepted with its original locale`, `unsupported seed locales are rejected without silently converting to en US`; ContentSeedTests semantic seed errors kiểm en-GB không ghi dữ liệu. Không thêm locale vào manifest; bổ sung thuộc commit bước 7 |
| Android từ chối pack locale khác dù checksum hợp lệ | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `ContentPackRepositoryTest.non US pack locale is rejected before commit even with matching checksum`, test bổ sung bước 7 đã chạy trong Android 94/94; Room/Robolectric |
| Fixture lớn ba bài, mỗi bài sáu từ/mười câu/lựa chọn dài, không coi là REVIEWED | **Đạt** cho kiểm tra format | `ContentPackPostgresTests.large format fixture retains ten questions long labels and six words in each lesson`; `ContentPackRepositoryTest.large three lesson fixture validates complete format and account isolation`, `18bb4eb` |
| Cùng hàm chấm và bộ vectors Android/backend; ngưỡng số nguyên 70%, phần trăm floor | **Đạt** cho thuật toán/adapter | `PackGraderTest.Android mapper agrees with shared grader and approved vectors`; `SharedGraderVectorTests.backend adapter matches shared grader and identical Android vectors`, `18bb4eb`. Test thuật toán thuần không chứng minh UI trên thiết bị |
| Mọi attempt hợp lệ được ghi; thiếu prerequisite chưa cấp official progress/XP; retry xác nhận một lần | **Đạt** cho server | `OfflineAttemptPostgresTests.out of order valid attempts persist then confirm only after prerequisites`, `pending confirmation rollback retains original grade and waiting state`, `course snapshot contains only server confirmed progress and course XP`, `5bbf850`/`c6f4fb9`; PostgreSQL thật |
| Đóng băng payload và enqueue atomic; retry giữ timestamp/cờ offline/answers/UUID | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `LessonSessionRepositoryTest.crash before commit writes neither frozen session nor outbox`, `submit freezes same payload and UUID across retry and crash after commit`, `1da4fe4`. Inject lỗi/SQLite reopen trong Robolectric |
| Result/snapshot/ack atomic, response sai UUID không xác nhận nhầm | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `LessonSyncEngineTest.result snapshot and acknowledgement rollback together before local commit`, `unexpected acknowledgement never confirms another UUID`, `c6f4fb9`; fake gateway/Room trong Robolectric |
| WAITING/BLOCKED giữ đáp án/payload; không trình bày XP tạm như chính thức | **Đạt một phần – chưa kiểm chứng trên thiết bị** | UI `waiting confirmation keeps zero official XP and a passed provisional next lesson`, `server rejection keeps all answers frozen and cannot automatically retry or unlock`, `worker confirmation is observed and server counts replace provisional counts`, `7533538` |
| Queue lesson độc lập queue library/review, retry/auth/BLOCKED giữ dữ liệu | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `LessonSyncEngineTest.blocked legacy queue does not stop lesson queue and reverse also holds`, `network HTTP retry statuses preserve pending UUID and payload`, `permanent conflict blocks and retains payload while auth only pauses`, `c6f4fb9`. Coordinator fake và Room/Robolectric, không worker/mạng thực |
| Room v4 account-scoped, FK không xuyên account, rollback không ghi dở, reopen đọc/ghi được | **Đạt một phần – chưa kiểm chứng trên thiết bị** | `LessonDatabaseMigrationTest.new tables isolate identical identifiers by account and survive database reopen`, `foreign keys prevent cross account references and removal of referenced pack`, `failed durable enqueue rolls back session and outbox together`, `6234a03` |
| Builder production đăng ký 1→2→3→4, không destructive fallback | **Đạt** cho kiểm tra cấu hình | `ProductionRoomConfigurationTest.production builder registers every migration without destructive fallback`, `18bb4eb`. Kiểm builder/source, không thay nâng cấp APK thực |
| V13 cộng thêm; dữ liệu V12, XP, checksums và partial unique indexes giữ nguyên; không rewrite heap | **Đạt** cho PostgreSQL fixture | `AttemptConfirmationMigrationTests.V12 to V13 preserves every week5 row checksums indexes and physical heap`, `5bbf850`; PostgreSQL thật, snapshot 13 bảng. Không thay chứng minh database mục tiêu |
| Admin xóa từ đang được lesson tham chiếu trả 409 và giữ dữ liệu | **Đạt** cho backend | `OfflineAttemptPostgresTests.admin deleting referenced vocabulary returns conflict and preserves rows`, `5bbf850`; PostgreSQL/HTTP thật. Admin UI chưa có toast lỗi delete riêng |
| Instrumentation các luồng tuần 6 | **Chưa đạt** | Chưa có instrumentation test/thiết bị; gate test tay được bỏ không tạo bằng chứng mới |
| Preflight trên database mục tiêu | **Chưa đạt** | Chỉ có fixture/migration tests, chưa chạy database mục tiêu |
| Nội dung REVIEWED | **Chưa đạt** | DEMO hợp lệ cho nghiệm thu kỹ thuật tuần 6; gate REVIEWED độc lập trước tuần 8 |
| Gate regression chung bước 7 | **Đạt** | Android94/backend26/PostgreSQL45 không failure/error/skipped; lint report mới 0/38/new0; admin build và APK đóng gói lại đạt trong lượt chung 09:20–09:25 ngày 13/10 |

## Commit theo bước

Các số checkpoint 0–6 là **lịch sử** trong báo cáo từng bước, không thay kết quả gate hôm nay. Checkpoint đó có lint 0 errors/38 warnings/0 new và admin build đạt.

| Bước | Commit | Phạm vi | Android / backend unit / PostgreSQL tại checkpoint lịch sử |
| --- | --- | --- | --- |
| 0 | `29530d9` | Hợp đồng/baseline/inventory | 51 / 23 / 33 |
| 1 | `6234a03` | Room v4/migration/schema JSON | 56 / 23 / 33 |
| 2 | `18bb4eb` | Content pack/shared grading/fixture lớn | 66 / 24 / 36 |
| 3 | `5bbf850` | V13/attempt offline và pending/admin 409 | 66 / 24 / 43 |
| 4 | `1da4fe4` | Durable session/frozen payload/outbox | 72 / 24 / 43 |
| 5 | `c6f4fb9` | Sync/coordinator/account-safe retries | 82 / 24 / 45 |
| 6 | `7533538` | UI ba bài/resume/trạng thái server | 93 / 24 / 45 |
| 7 | **Commit chứa báo cáo này** | Nghiệm thu kỹ thuật/đính chính locale/test assertions | **94 / 26 / 45**, gate chung ngày 13/10, lint 38 và builds đạt |

## Việc còn lại / rủi ro đã biết

- **Nâng cấp APK tuần 5→6 trên thiết bị thật chưa kiểm chứng.** Room fixture không chứng minh signing/install, token/profile DataStore qua APK hoặc hành vi OS. Không thấy test nâng cấp DataStore/token/profile APK riêng; authenticator tests dùng fake credential store.
- **Instrumentation test chưa có.** Process death thực, vuốt tắt/reboot và hai account trên máy thật chưa kiểm chứng; các hàng tương ứng giữ mức một phần.
- **Nội dung vẫn DEMO.** Gate REVIEWED phải xong trước tuần 8, người rà soát khác người biên soạn; fixture format không thay rà soát ngôn ngữ.
- **Preflight chưa chạy trên database mục tiêu.** PostgreSQL Testcontainers không thay database đích; khi deploy cần preflight/backup/kế hoạch DDL lock. V13 dùng constant default nhưng DDL vẫn cần khóa và CHECK có thể quét dữ liệu.
- Answer key offline không chống gian lận tuyệt đối. XP/progress chính thức do server xác nhận; score tạm không phải chứng nhận học lực.
- Không chuyển payload SavedStateHandle tuần 5 sang Room. Không suy đoán gộp/xóa attempt cũ mất UUID; unique lesson XP bảo vệ thưởng một lần.
- Force-stop có thể ngăn background worker đến khi mở app; app có luồng resume sync nhưng hành vi thực chưa kiểm chứng. Offline phải tải đủ chặng/enroll khi có mạng trước; đăng nhập account khác lần đầu cần API.
- Catalog title fallback “Chặng đã tải” khi mở offline; UI tổng XP chỉ là tổng snapshot những chặng đã tải và có nhãn phạm vi.
- Admin delete từ có FK đã trả 409 ở backend; admin React chưa xử lý riêng promise lỗi delete bằng toast.
- Chưa có PRACTICE UI, ba dạng bài mới/TTS, analytics, camera–topic, CMS, bảng xếp hạng hoặc xóa account. Chúng không phải phần được nghiệm thu ở báo cáo này.

## APK và PR

Lệnh backend/seed, **backup database trước cài đè**, build/cài APK và bảng test tay vẫn tại [week6-testing.md](../product/week6-testing.md). Không uninstall hoặc clear data. Khi chủ dự án muốn test, dùng APK đã được gate xác minh:

```powershell
cd E:\Projects\LingoLens
$env:ANDROID_HOME = 'C:/Users/ASUS/AppData/Local/Android/Sdk'
$adbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
& $adbPath devices
# Sau khi đã chạy backup theo week6-testing.md
& $adbPath -s emulator-5554 install -r .\app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed; keep current app data' }
& $adbPath -s emulator-5554 shell am start -n com.quocdat.lingolens/.MainActivity
```

**PR tuần 6:** mở sau commit báo cáo từ `feature/offline-lessons` vào `main`, gồm code/tài liệu tuần 6. URL được gắn với task sau khi tạo thành công; không merge. Báo cáo này không tự cấp trạng thái “đã nghiệm thu thiết bị”.
