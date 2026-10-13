# Tuần 6 — Bước 6: UI ba bài và checkpoint trước test thiết bị

Ngày **12/10/2026**, múi giờ Asia/Saigon. Nhánh `feature/offline-lessons`, base checkpoint bước 5 `c6f4fb9`. Bước 6 nằm trong một commit riêng mang thông điệp `week6 step 6: connect durable three lesson UI and verified lifecycle teardown`; hash xem commit chứa báo cáo này và báo cáo gửi chủ dự án. **Dừng sau bước 6; chưa làm bước 7, mở PR hoặc merge main.**

## Thay đổi thực hiện

- `LessonScreen` dùng `OfflineLessonScreenModel`/`LessonStudyRepository`, không còn dispatch attempt qua gateway online của UI tuần 5. Catalog/enroll vẫn dùng endpoint backend hiện có, với account header; enrollment/server snapshot được lưu trước khi bắt đầu học offline.
- UI tải chặng, chỉ cho học khi đủ ba pack hợp lệ. Tải dở có trạng thái số bài đã tải và tiếp tục tải; pack complete không bị ghi đè. Cả ba bài có luồng từ/mẫu câu → chọn nghĩa → nộp → tổng kết.
- Room giữ UUID/revision/answers/vị trí và payload. SavedStateHandle chỉ chứa account/selection/view để điều hướng; không nhập payload tuần 5 cũ. Mở bài đang học/chờ sẽ resume session cũ; chỉ thao tác Học lại sau confirmed tạo session mới dùng revision mới.
- Chấm tạm bằng shared grader, mở bài kế offline nếu đạt; nhãn “Đạt tạm • Chờ server xác nhận”. UI chỉ hiện số đúng/tổng, không hiện phần trăm hoặc XP tạm. Tổng XP lộ trình là tổng snapshot server của các chặng đã tải, có ghi rõ phạm vi.
- Các Flow theo account của pack/session/outbox/curriculum đưa kết quả worker vào UI. CONFIRMED dùng kết quả server; mở khóa đã xác nhận dùng server progress, không dùng local grade của attempt đã confirmed. BLOCKED hiện mã lỗi và đáp án đã lưu, không sửa/gửi lại payload hoặc tự tạo UUID.
- Thêm quyền ACCESS_NETWORK_STATE để lấy trạng thái kết nối khi đóng băng submittedOffline. Không thêm thư viện/dịch vụ, dạng bài, PRACTICE UI, camera/review scheduler/analytics, migration backend hoặc schema Room mới.

Các test quan trọng được viết trước phần UI/repository adapter trong lượt bước 6. Các test SavedStateHandle tuần 5 vẫn giữ lại; chúng kiểm lớp legacy, không được coi là bằng chứng UI durable mới.

## SQLITE_BUSY: chẩn đoán trước sửa

Lượt đầy đủ trước sửa: Android 92/93, ca `empty saved state and database reopen retain UUID answers position and frozen payload` lỗi SQLiteDatabaseLockedException. Theo yêu cầu chủ dự án, **giữ nguyên code/assertion** và chạy riêng ca này ba lần:

| Lượt chẩn đoán | Test | Failure | Kết luận sử dụng |
| --- | --- | --- | --- |
| 1 | 1 | 1 SQLITE_BUSY | Chỉ chẩn đoán |
| 2 | 1 | 1 SQLITE_BUSY | Chỉ chẩn đoán |
| 3 | 1 | 1 SQLITE_BUSY | Chỉ chẩn đoán |

Fail đều 3/3 trong mẫu này; không lấy lượt chạy riêng làm bằng chứng đạt. Một lượt thêm query/lifecycle logging tạm thời cũng tái hiện lỗi. XML các lượt trước sửa nằm trong `.local/week6-step6-diagnostics/` (ignore khỏi Git); report cuối ở build/test-results chứa lượt regression sau sửa.

Đọc toàn bộ stack cho thấy bên lỗi là:

`RoomDatabase.withTransaction → internalBeginTransaction → InvalidationTracker.syncBlocking → syncTriggers → BEGIN IMMEDIATE → SQLiteDatabaseLockedException`.

`runBlockingUninterruptible` trong stack là code compatibility nội bộ Room 2.8.5, không phải runBlocking được thêm vào production của LingoLens. Log SQL gắn instance/thread và lifecycle tìm thấy:

```text
before second cancel room=2 open=true  job cancelled=false, completed=false, children=1
after second cancel  room=2 open=true  job cancelled=true,  completed=false, children=1
after second close   room=2 open=false job cancelled=true,  completed=false, children=1
opened               room=3 open=false job cancelled=true, completed=false, children=1
SQL room=2 thread=arch_disk_io_1 END TRANSACTION
SQL room=3 ... BEGIN IMMEDIATE TRANSACTION
... SQLITE_BUSY trong syncTriggers
```

Instance đầu **đã được gọi close trước open mới** và isOpen=false, nhưng coroutine quan sát/project snapshot của instance cũ chưa kết thúc. Transaction SQLite của Room 2 vẫn unwinding khi Room 3 bắt đầu sync invalidation triggers. Log không có native connection ID; định danh instance/thread là logging của fixture. Đây là bằng chứng giao thoa đời sống instance cũ/mới, không phải bằng chứng worker đang chạy.

Fixture dùng `application=android.app.Application`, không khởi động AppContainer/WorkManager/SyncWorker; callbacks sync chỉ tăng bộ đếm. Lệnh choose/next/submit trong test được await tuần tự. Các observer Flow/invalidation tracker có chạy nền; cancel() chỉ yêu cầu hủy, không bảo đảm coroutine/SQLite cleanup đã hoàn tất.

**Phân loại: lỗi fixture mô phỏng process death.** OS process death xóa các thread/connection cũ; fixture vẫn cho thread cũ chạy sau close/reopen. Sửa helper `stopProcess`: cancel tất cả root jobs, join tất cả jobs, assert completed và không còn children, rồi close Room và assert !isOpen, sau đó mới open instance mới. Áp dụng cho hai lần reopen và teardown. Không nới/xóa assertion UUID/answers/vị trí/payload; không thêm busy_timeout, retry SQLite, sleep, đổi journal mode/SQL hoặc bỏ qua test. Query/lifecycle logging tạm đã gỡ khỏi fixture cuối.

Sau sửa, cả 9 test model/Room chạy riêng đạt, rồi **toàn bộ 93 Android tests** đạt trong lượt regression mới. Lượt test riêng sau sửa chỉ là kiểm tra cục bộ; checkpoint dựa bộ đầy đủ bên dưới.

## Transaction và các hợp đồng bảo vệ

Mỗi lần lưu đáp án gọi một transaction `LessonSessionRepository.answer`: đọc session/pack đã complete, kiểm question/option/vị trí, cập nhật JSON đáp án/vị trí bằng một upsert, kiểm account trước commit. Không có HTTP, scheduler hoặc chờ worker bên trong transaction. Di chuyển câu có transaction lưu vị trí riêng; UI chỉ phản ánh thao tác thành công sau commit. Đây là transaction có phạm vi ngắn, không giữ nó qua vòng đời màn hình hoặc request mạng; không tuyên bố đã đo một giới hạn mili giây trên thiết bị.

Nộp bài đóng băng timestamp/flag/answers/payload/hash, đổi session phase và insert LessonAttemptOutbox **cùng một Room transaction**. Scheduler gọi sau commit. Các test bước 4 `crash before commit writes neither frozen session nor outbox` và `submit freezes same payload and UUID across retry and crash after commit` tiếp tục đạt. Result/snapshot/ack khi sync vẫn cùng transaction, test bước 5 rollback/lost response còn đạt.

Backend confirmation_status chỉ CONFIRMED/WAITING_PREREQUISITE, do LessonService đặt; PENDING/BLOCKED là local queue states. Chờ prerequisite chưa có official progress/XP. PostgreSQL `out of order valid attempts persist then confirm only after prerequisites` và `course snapshot contains only server confirmed progress and course XP` bảo vệ quy tắc này. Bài trượt đủ prerequisite được CONFIRMED nhưng không thưởng/mở bài kế.

| Test bổ sung / giữ lại | Hành vi kiểm chứng |
| --- | --- |
| OfflineLessonScreenModelTest: `partial download cannot open course and retry preserves complete pack` | Tải dở không mở chặng; retry giữ nguyên pack tốt, đủ ba bài mới ready |
| `all three offline lessons show provisional counts unlock next and never official XP` | Học/nộp cả ba offline bằng Room, số đúng/tổng, mở bài sau tạm, XP chính thức 0, ba payload submittedOffline |
| `empty saved state and database reopen retain UUID answers position and frozen payload` | Hai lần đóng/mở database thật trong Robolectric, SavedStateHandle rỗng, giữ UUID/answers/vị trí và toàn bộ row queue/payload; thêm assertion teardown hoàn tất |
| `worker confirmation is observed and server counts replace provisional counts` | Flow cập nhật UI sau Room transaction result/snapshot/ack; số đúng server thay tạm, server trượt không mở bài kế |
| `server rejection keeps all answers frozen and cannot automatically retry or unlock` | BLOCKED/error hiện lên, answers/payload/UUID còn nguyên, không sửa/gửi thêm/mở bài kế từ lượt bị từ chối |
| `waiting confirmation keeps zero official XP and a passed provisional next lesson` | WAITING vẫn chưa có XP/result chính thức, cho progress tạm đã đạt |
| `new revision download leaves active session pinned while explicit new attempt uses latest` | Tải revision mới không thay session/answers cũ; Học lại sau confirmed dùng revision mới và UUID mới |
| `account switch shows no old course session answers or queue` | A logout, B không thấy dữ liệu A; queue A giữ nguyên và quay lại resume được |
| `double submit queues once and navigation persists before returning` | Một attempt/queue, vị trí đã được lưu khi action hoàn thành |
| LessonSyncEngineTest: `unsent A lesson survives logout and B login without any A dispatch` | A có queue chưa gửi, logout/B login không gọi submit A; queue giữ nguyên, login A mới gửi |
| TokenAuthenticatorTest: `A account header cannot dispatch using B token after logout login` | OkHttp interceptor chặn trước terminal interceptor khi signed out/account B; không gắn B bearer cho request account A; trở về A mới dispatch với A token |
| LessonSyncEngineTest: `blocked legacy queue does not stop lesson queue and reverse also holds` | Coordinator gọi cả hai queue dù một queue BLOCKED/network error; đây là test fake orchestration, không phải thiết bị/mạng thật |

Các test dữ liệu Room/Robolectric và gateway fake là bằng chứng cho repository/UI model. PostgreSQL thật tiếp tục bảo vệ grading/idempotency/prerequisite/concurrency/transaction/ownership. Chưa có instrumentation Compose/force-stop/reboot hoặc test tay của chủ dự án cho bước 6.

## Kết quả checkpoint mới

| Kiểm tra | Kết quả | Bằng chứng |
| --- | --- | --- |
| Android unit/Robolectric | **93/93**, 0 failure/error/skipped | app/build/test-results/testDebugUnitTest; XML mới nhất 00:25:28 UTC+07 |
| Backend unit/H2 | **24/24**, 0 failure/error/skipped | backend/build/test-results/test; 00:25:16 UTC+07 |
| PostgreSQL 15 Testcontainers | **45/45**, 0 failure/error/skipped | backend/build/test-results/postgresTest; 00:26:21 UTC+07 |
| Lint analyze/report/debug | **0 errors, 38 warnings, 0 new** | XML xuất mới **00:25:38 UTC+07**, Assert-LintBaseline đạt, kiểm danh tính/multiplicity cảnh báo |
| Admin TypeScript/Vite | **Build đạt** | npm run build; không tính là test |
| APK debug | **Build đạt**, endpoint emulator 10.0.2.2:8080 | APK mới **00:25:50 UTC+07**, 86,219,139 bytes; dex có OfflineLessonScreenModel/LessonStudyRepository |

Lệnh đã chạy:

```powershell
# Root, JAVA_HOME JDK21 và ANDROID_HOME SDK đã đặt
.\gradlew.bat :app:testDebugUnitTest --rerun :app:lintDebug --rerun :app:lintAnalyzeDebug --rerun :app:lintReportDebug --rerun :app:assembleDebug -PapiBaseUrl=http://10.0.2.2:8080/ --max-workers=2 --console=plain
.\scripts\quality\Assert-LintBaseline.ps1
# backend working directory
.\gradlew.bat test --rerun postgresTest --rerun --max-workers=2 --console=plain
# admin-dashboard working directory
npm run build
```

APK SHA-256: `F4895E0524A14D779DB034FBF2653E6C688DCCC802ACF025315CC0E8502EE120`.

Source checksums V1–V13 trong week6-migration-checksums.json giữ nguyên; `git diff HEAD -- app/schemas` rỗng (v1–v4 không sửa). Không thêm dependency hoặc destructive fallback. Giữ thay đổi IDE chủ dự án ngoài commit. Chưa mở PR, không khẳng định GitHub Actions cho bước 6 đã chạy; workflow hiện tại chạy trên PR/main, chưa push feature là bằng chứng CI.

## Cài, test tay và việc còn lại

[Hướng dẫn đầy đủ](../product/week6-testing.md) có lệnh backend dev/seed, backup cả database/WAL/SHM bằng run-as/tar/base64 trước cài đè, assembleDebug, install -r và mở app; bảng kiểm tải dở, offline ba bài, reconnect, mất response, process death, vuốt tắt/reboot, nâng cấp tuần 5 không uninstall, hai account, revision mới, admin delete và BLOCKED. Có fixture dev cho revision/conflict và truy vấn chỉ đọc đối chiếu UUID/user/XP.

- **Đang chờ chủ dự án test thiết bị**, nhất là migration APK tuần 5, process death/force-stop/reboot/account switch. Không đánh dấu tuần 6 nghiệm thu hoàn tất hoặc làm bước 7 dựa riêng JVM.
- DEMO chưa được rà soát; gate REVIEWED trước tuần 8 giữ nguyên. Answer key trong pack phục vụ offline, không chống gian lận tuyệt đối.
- Preflight/database mục tiêu chưa chạy. Không chuyển payload SavedStateHandle cũ sang Room; không tự gộp/xóa attempts mất UUID cũ. Unique lesson XP vẫn chống thưởng lại.
- Force-stop chặn background worker theo Android; app resume sync khi mở lại. Offline phải tải đủ/enroll online trước; đăng nhập account khác lần đầu cần API.
- Catalog title có fallback “Chặng đã tải” khi khởi động offline; bài/từ/câu hỏi/revision vẫn từ pack đã lưu. Các chặng đã tải có XP snapshot riêng; UI nêu rõ phạm vi tổng XP.
- Admin delete từ có lesson FK đã trả 409/VOCABULARY_IN_USE từ bước 3. Admin React hiện không bắt riêng promise lỗi delete để hiện toast; kiểm HTTP/body trong Network/Swagger. Chưa sửa admin UI ở bước 6.
- PRACTICE UI, dạng bài mới/TTS/analytics/camera/CMS/account deletion nằm ngoài bước 6; không thêm. Bước 7 và PR chờ kết quả chủ dự án.
