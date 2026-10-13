# Tuần 6 — Bước 7: báo cáo dừng và ma trận nghiệm thu kỹ thuật dự thảo

**LỊCH SỬ — ĐÃ GỠ ĐIỀU KIỆN DỪNG.** Chủ dự án đã bật Docker và cho tiếp tục; gate chung mới đạt trong [báo cáo bước 7](week6-step7.md). Các số/ma trận dưới đây giữ nguyên để ghi lịch sử lượt bị chặn, không phải trạng thái nghiệm thu hiện tại.

Ngày 13/10/2026 (Asia/Saigon), nhánh `feature/offline-lessons`, HEAD `7533538`. Chủ dự án bỏ gate test tay trước bước 7, nhưng **không bỏ yêu cầu ghi rõ thiếu bằng chứng thiết bị**. Không merge main. Bước 7 chưa hoàn tất, chưa mở PR hoặc tạo nhánh/code tuần 7.

## Điều kiện dừng đã gặp

1. Đặc tả §14.1 ghi manifest chứa en-US; wire thực tế không có locale trong PackManifest/PackReference, chỉ có LessonPack.pronunciationLocale. Chủ dự án chọn **A**: chỉ sửa câu đặc tả cho đúng vị trí hiện có, giữ nguyên manifest/wire format. Đã đính chính câu đó, không thêm field hoặc sửa production API.
2. Lượt postgresTest mới thất bại khi khởi tạo Testcontainers: **8 class initialization errors**, không phải tám assertion nghiệp vụ. Root cause XML: `Could not find a valid Docker environment`. Kiểm tra chỉ đọc `docker version` báo không kết nối được pipe `//./pipe/dockerDesktopLinuxEngine` vì pipe không tồn tại. Chưa tự đổi Docker config/driver, khởi động engine hoặc chạy lại để bỏ qua điều kiện dừng.

Không commit bổ sung, mở PR hay tiếp tục tuần 7 khi gate PostgreSQL chưa đạt. Kết quả PostgreSQL 45/45 ngày 12/10 trong báo cáo bước 6 là lịch sử, **không thay thế** lượt nghiệm thu ngày 13/10.

## Quyết định A và test locale

Backend đã có `ContentSeedTests.semantic seed errors are rejected before writes`, bao gồm đổi pronunciationLocale sang en-GB, bảo vệ seed bị từ chối trước ghi dữ liệu trên PostgreSQL. Chưa có assertion riêng cho locale pack server xuất ra và chưa có test Android từ chối locale sai bằng checksum hợp lệ.

Bổ sung theo yêu cầu chủ dự án, không thay code production:

- `SeedLocaleTests.en US seed is accepted with its original locale`: seed en-US qua validator, không tự thay giá trị.
- `SeedLocaleTests.unsupported seed locales are rejected without silently converting to en US`: en-GB/fr-FR/en/rỗng bị validator từ chối; không tự chuyển về en-US. Đây là unit test không cần Docker.
- `ContentPackRepositoryTest.non US pack locale is rejected before commit even with matching checksum`: pack en-GB/fr-FR có checksum/size hợp lệ vẫn bị từ chối với `Invalid content pack`, không có pack complete/ready hoặc schedule; en-US được nhận.
- `ContentPackPostgresTests.manifest has three stable immutable packs including answer keys`: thêm assertion mọi payload pack server xuất `pronunciationLocale=en-US`. Assertion mới **chưa chạy trên PostgreSQL**, chờ engine hợp lệ. Manifest không thêm field.

Lượt kiểm tra mới sau bổ sung A (13/10/2026, Asia/Saigon):

| Kiểm tra | Kết quả | Giới hạn / bằng chứng |
| --- | --- | --- |
| Android | **94/94 đạt**, 0 failure/error/skipped | XML mới nhất 09:13:23; gồm test locale mới |
| Backend unit | **26/26 đạt**, 0 failure/error/skipped | XML mới nhất 09:12:45; hai SeedLocaleTests mới đạt |
| PostgreSQL | **Fail khởi tạo 8 class**, chưa chạy assertions | Lượt 09:08:46, chưa retry môi trường Docker; assertion pack output locale mới chưa thực thi |
| Lint | **0 errors / 38 warnings / 0 new** | Analyze/report chạy lại, XML **09:13:27**, Assert-LintBaseline đạt |
| Admin | **Build đạt** | npm run build trong lượt đầu hôm nay; không tính là test |
| APK | **assembleDebug đạt, UP-TO-DATE** | Production code không đổi; APK bước 6 giữ nguyên, không phải bản có tính năng tuần 7 |

APK SHA-256: `F4895E0524A14D779DB034FBF2653E6C688DCCC802ACF025315CC0E8502EE120`. Không gán test PostgreSQL chưa chạy là đạt. Sau lỗi Docker, chỉ thực hiện phần đính chính/test locale được chủ dự án yêu cầu riêng qua quyết định A; chưa triển khai tuần 7 hoặc tự sửa môi trường để đi tiếp.

## Ma trận mục 10 — tuần 6

Đây là ma trận **dự thảo để xem xét**, không tuyên bố bước 7/tuần 6 được nghiệm thu hoàn toàn. Bằng chứng server PostgreSQL bên dưới đã đạt ở commit/báo cáo bước 2–6 ngày 11–12/10; regression PostgreSQL mới đang bị chặn môi trường. Các hàng chỉ có Room/Robolectric/fake và chưa chạy thiết bị đều ghi rõ mức một phần.

| Tiêu chí | Trạng thái | Test / commit và giới hạn |
| --- | --- | --- |
| Cả ba bài trọn vòng; học mất mạng và mở bài kế bằng kết quả tạm | **Đạt một phần – chưa kiểm chứng trên thiết bị** | OfflineLessonScreenModelTest: `all three offline lessons show provisional counts unlock next and never official XP`, `7533538`. Room/Robolectric + gateway fake |
| Content pack hoàn chỉnh; tải dở/checksum/revision sai không sử dụng được | **Đạt một phần – chưa kiểm chứng trên thiết bị** | ContentPackRepositoryTest: `interrupted download never stores a complete pack`, `bad checksum preserves good pack and retry finishes course`, `wrong revision is rejected even with matching checksum`, `incomplete database pack cannot be read or used`, `changed manifest cannot overwrite a good immutable pack` (`18bb4eb`); UI `partial download cannot open course and retry preserves complete pack` (`7533538`) |
| Reopen giữ vị trí, đáp án, UUID và revision | **Đạt một phần – chưa kiểm chứng trên thiết bị** | LessonSessionRepositoryTest: `answers position UUID and revision survive actual database close reopen` (`1da4fe4`); UI `empty saved state and database reopen retain UUID answers position and frozen payload` (`7533538`). Không thay process death thật |
| Retry/lost response không nhân đôi attempt/XP end-to-end | **Đạt một phần – chưa kiểm chứng trên thiết bị** | LessonSyncEngineTest: `lost response and database reopen retry same UUID payload without adding XP locally` (`c6f4fb9`) là fake mất response. Server PostgreSQL lịch sử: LessonAttemptPostgresTests `concurrent same UUID has one attempt and one award`, `concurrent distinct UUIDs have two attempts and one award`; OfflineAttemptPostgresTests `concurrent pending retries and different UUIDs award only once` (`5bbf850`) |
| Revision cũ ARCHIVED vẫn chấm được, snapshot giữ nguyên | **Đạt** bằng chứng server lịch sử; gate PostgreSQL mới chưa đạt | OfflineAttemptPostgresTests: `archived revision remains gradable without changing snapshot` (`5bbf850`); ContentPackPostgresTests `pack endpoint requires login and unpublished revisions are rejected` (`18bb4eb`) kiểm bytes không đổi sau archive. Lượt mới chưa chạy assertions vì Docker |
| Session đang học không tự đổi sang revision mới | **Đạt một phần – chưa kiểm chứng trên thiết bị** | UI `new revision download leaves active session pinned while explicit new attempt uses latest`, `7533538`, Room/Robolectric |
| Nâng cấp giữ outbox/library/review tuần 1–4 | **Đạt một phần – chưa kiểm chứng trên thiết bị** | LessonDatabaseMigrationTest: `v3 upgrade retains every legacy row and opens real v4 database`, `v1 and v2 upgrades preserve original words events cache and outbox`, `6234a03`. So dữ liệu tám bảng, account, sequence/UUID/payload/retry và review session dở. Chưa cài đè APK tuần 5→6 trên thiết bị |
| Process death, vuốt tắt, reboot, cài đè APK không uninstall | **Đạt một phần – chưa kiểm chứng trên thiết bị** | Có đóng/mở DB và teardown coroutine trong Robolectric (`7533538`), chưa instrumentation hoặc chạy thiết bị thật |
| Hai account, logout giữ queue, không dùng token B gửi bài A | **Đạt một phần – chưa kiểm chứng trên thiết bị** | LessonSyncEngineTest: `unsent A lesson survives logout and B login without any A dispatch`; TokenAuthenticatorTest: `A account header cannot dispatch using B token after logout login`; UI `account switch shows no old course session answers or queue` (`7533538`). Server account header test lịch sử `c6f4fb9`; chưa hai account trên máy thật |

Các hợp đồng hỗ trợ:

| Hợp đồng | Trạng thái | Bằng chứng |
| --- | --- | --- |
| Chấm dùng cùng hàm/vectors Android và backend | **Đạt** cho thuật toán/adapter | PackGraderTest: `Android mapper agrees with shared grader and approved vectors`; SharedGraderVectorTests: `backend adapter matches shared grader and identical Android vectors`, `18bb4eb` |
| Ghi mọi attempt hợp lệ nhưng chưa cấp progress/XP khi thiếu prerequisite | **Đạt** bằng chứng server lịch sử; gate PostgreSQL mới chưa đạt | OfflineAttemptPostgresTests: `out of order valid attempts persist then confirm only after prerequisites`, `pending confirmation rollback retains original grade and waiting state`, `course snapshot contains only server confirmed progress and course XP`, `5bbf850`/`c6f4fb9` |
| Đóng băng payload và enqueue atomic; result/snapshot/ack rollback cùng nhau | **Đạt một phần – chưa kiểm chứng trên thiết bị** | LessonSessionRepositoryTest: `crash before commit writes neither frozen session nor outbox`, `submit freezes same payload and UUID across retry and crash after commit` (`1da4fe4`); LessonSyncEngineTest `result snapshot and acknowledgement rollback together before local commit` (`c6f4fb9`) |
| WAITING/BLOCKED giữ đáp án, không trình bày XP tạm như chính thức | **Đạt một phần – chưa kiểm chứng trên thiết bị** | UI `waiting confirmation keeps zero official XP and a passed provisional next lesson`, `server rejection keeps all answers frozen and cannot automatically retry or unlock`, `worker confirmation is observed and server counts replace provisional counts`, `7533538` |
| Queue lesson và library/review độc lập | **Đạt một phần – chưa kiểm chứng trên thiết bị** | LessonSyncEngineTest: `blocked legacy queue does not stop lesson queue and reverse also holds`, `c6f4fb9`; fake coordinator, không worker/mạng thật |
| V13 giữ dữ liệu và partial indexes, không rewrite heap | **Đạt** bằng chứng PostgreSQL lịch sử; gate mới chưa đạt | AttemptConfirmationMigrationTests: `V12 to V13 preserves every week5 row checksums indexes and physical heap`, `5bbf850` |
| Production Room đăng ký migration, không destructive fallback | **Đạt** cho kiểm tra cấu hình | ProductionRoomConfigurationTest: `production builder registers every migration without destructive fallback`, `18bb4eb`; không thay test cài đè APK thật |
| Instrumentation trên thiết bị | **Chưa đạt** | Chưa có instrumentation test cho các luồng tuần 6 |
| Preflight trên database mục tiêu | **Chưa đạt** | Mới có fixtures; chưa chạy database mục tiêu |
| Nội dung REVIEWED | **Chưa đạt** | DEMO đủ cho nghiệm thu kỹ thuật theo quyết định đã duyệt; gate REVIEWED trước tuần 8 còn nguyên |
| Gate đủ bộ kiểm tra bước 7 ngày 13/10 | **Chưa đạt** | postgresTest fail initialization; không dùng lượt 45/45 cũ thay thế |

## Commit theo bước tuần 6

| Bước | Commit | Android / backend unit / PostgreSQL đã đạt tại checkpoint lịch sử |
| --- | --- | --- |
| 0 | 29530d9 | 51 / 23 / 33 |
| 1 | 6234a03 | 56 / 23 / 33 |
| 2 | 18bb4eb | 66 / 24 / 36 |
| 3 | 5bbf850 | 66 / 24 / 43 |
| 4 | 1da4fe4 | 72 / 24 / 43 |
| 5 | c6f4fb9 | 82 / 24 / 45 |
| 6 | 7533538 | 93 / 24 / 45 |
| 7 | **Chưa commit** | Gate mới chưa đạt; bổ sung A và báo cáo vẫn trong working tree |

Checkpoint lịch sử có lint 0 errors/38 warnings/0 new và admin build đạt; xem báo cáo từng bước. Không đồng nhất chúng với lượt regression hôm nay.

## Tuần 7 — tiến độ và giả định chờ chủ dự án xác nhận

**Chỉ đã audit đọc-only**, chưa nhánh mới, hợp đồng tuần 7 chính thức, code, test vector hay commit tuần 7. Audit thấy không bắt buộc thêm migration: V10 đã có ba loại question, speech_text nullable và answer_key JSONB; V11 submitted_answer JSONB; Room v4 lưu pack/answers/payload bằng JSON. Việc validator hiện chỉ nhận MEANING_CHOICE là phạm vi mở rộng dự kiến tuần 7, không phải lý do thay schema.

Giả định dự kiến theo các mặc định chủ dự án cung cấp, **chưa triển khai**:

1. FILL_GAP trim/gộp whitespace/case-insensitive (Locale.ROOT), chỉ các acceptedAnswers đã duyệt; không fuzzy hoặc tự bỏ dấu câu. Giữ raw input trong payload/hash; chuẩn hóa chỉ lúc chấm.
2. Bài 10 câu có tối đa hai LISTEN_CHOICE; mỗi câu có fallback không cần nghe được gắn nhãn. Cùng question ID/đáp án/trọng số/mẫu số; không tự đúng/bỏ câu khi thiếu voice.
3. Chọn voice bằng locale en-US và thuộc tính isNetworkConnectionRequired, ưu tiên offline. Thiếu/phát lỗi thì hướng dẫn cài voice hoặc fallback; không cloud TTS/audio mới.
4. Phản hồi đúng/sai và explanation sau câu; luyện lại câu sai phải giữ frozen attempt bất biến sau nộp và không cấp XP mới từ luyện cục bộ.
5. Trọng số nội dung tuần 7 đều 1, ngưỡng nguyên 70%; giữ các revision/vector lịch sử có trọng số khác để không phá queue cũ. Android/backend cùng grader/vectors.
6. Fixture lớn phủ ba dạng; DEMO chỉ dev/test. Revision mới dùng pack/revision/question UUID mới, giữ stable lesson/course và revision cũ; không sửa gói đã nhập hoặc nội dung published, không reset enrollment/data.

Kế hoạch dự kiến sau khi gỡ điều kiện dừng: nhánh từ feature/offline-lessons → tài liệu hợp đồng → shared grading/vectors → pack/seed/backend ba dạng → TTS controller/fallback → UI/feedback/luyện câu sai. Mỗi bước test trước, đủ Android/backend/postgresTest/lint/admin/APK, một commit riêng. Chưa coi các lựa chọn wire mở rộng/luyện lại là hợp đồng đã code.

## Việc còn lại / rủi ro đã biết

- **Nâng cấp APK tuần 5→6 trên thiết bị thật chưa kiểm chứng.** Không dùng Room fixture để khẳng định token/profile DataStore hoặc signing/install đã được kiểm chứng qua APK.
- **Instrumentation test chưa có**, chưa process death/vuốt tắt/reboot hoặc hai account trên máy thật.
- **Nội dung vẫn DEMO**, REVIEWED phải xong trước tuần 8, người rà soát khác người biên soạn.
- **Preflight chưa chạy trên database mục tiêu**; Docker Testcontainers, kể cả khi chạy lại được, không thay database mục tiêu.
- PostgreSQL mới chưa chạy assertions vì Linux Docker pipe chưa truy cập được. Chưa xác nhận assertion locale pack output mới.
- Answer key offline không chống gian lận tuyệt đối; SavedStateHandle payload cũ không chuyển vào Room; không tự gộp/xóa attempt mất UUID.
- Force-stop tạm ngừng worker cho tới khi mở app; hành vi thiết bị chưa kiểm chứng. Admin delete từ FK trả 409 ở backend lịch sử; admin UI chưa có toast riêng.
- Tuần 7 cần giữ compatibility pack JSON cũ (Gson không bảo đảm Kotlin constructor defaults cho field thiếu), hash/raw fill input, immutable published content và enrollment ACTIVE hiện có. Không thêm thư viện hoặc migration chỉ để lách các ràng buộc này.

## APK để test khi chủ dự án muốn

Giữ hướng dẫn [backup/cài đè/test tay tuần 6](../product/week6-testing.md). Không uninstall hoặc clear data. Sau backup, từ root:

```powershell
$env:ANDROID_HOME = 'C:/Users/ASUS/AppData/Local/Android/Sdk'
$adbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
& $adbPath devices
& $adbPath -s emulator-5554 install -r .\app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed; keep current app data' }
& $adbPath -s emulator-5554 shell am start -n com.quocdat.lingolens/.MainActivity
```

APK chỉ có chức năng đến bước 6 tuần 6, endpoint emulator 10.0.2.2:8080; chưa ba dạng bài/TTS tuần 7. Chờ Docker environment hợp lệ và chủ dự án cho tiếp tục sau báo cáo fail, rồi mới hoàn tất gate, commit/mở PR tuần 6 và bắt đầu nhánh tuần 7. Không merge main.
