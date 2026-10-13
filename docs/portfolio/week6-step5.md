# Tuần 6 — Bước 5: lesson sync và checkpoint

Nhánh `feature/offline-lessons`, 11/10/2026. Kết thúc bước 5, **dừng**; không merge main, chưa làm bước 6–7/UI cả ba bài/nghiệm thu thiết bị.

## Thay đổi

LessonSyncEngine xử lý LessonAttemptOutbox riêng theo account/course/vị trí bài rồi queuedAt/UUID. Payload đã đóng băng được gửi nguyên vẹn; kiểm hash/UUID/revision với session, không tạo UUID mới khi timeout/retry. Pending prerequisite giữ WAITING_PREREQUISITE và được thử lại; 400/404/409/lỗi dữ liệu vĩnh viễn BLOCKED giữ payload/error, 401/403 pause, network/408/429/5xx retry. BLOCKED không được gửi lại tự động.

Mất response hoặc local commit lỗi giữ queue để retry. Result, course snapshot đã xác nhận và acknowledge cùng Room transaction; không cộng xpAwarded vào tổng local. Tổng XP/progress lấy snapshot server. Course có queue chưa xác nhận không được cập nhật snapshot ngoài transaction xác nhận để tránh ghi dở khi ack rollback. Không ghi response sau đổi account.

RemoteLessonSyncGateway dùng client authenticated hiện có, header X-LingoLens-Account đã được AuthInterceptor/TokenAuthenticator bảo vệ. Backend kiểm header với principal trên submit và endpoint state; mismatch 403/ACCOUNT_MISMATCH, không ghi attempt. Header optional cho client tuần 5 cũ; payload attempt không đổi.

GET `/api/v1/curriculum/courses/{id}/state` trả courseId/enrollmentStatus/progress[{lessonId,bestScorePercent,passed}]/xpTotal theo account/course; pending attempts không có progress/XP trong snapshot. Endpoint yêu cầu đăng nhập. Đặc tả mục 14.3 ghi rõ hợp đồng cụ thể này.

SyncCoordinator gọi queue library/review và lesson độc lập, kể cả một queue BLOCKED/retry/lỗi; SyncWorker dùng coordinator, network constraints/backoff/periodic scheduler giữ nguyên. Tải đủ pack hợp lệ schedule pull state; tải dở không schedule. Logout không xóa pending, xóa account vẫn thuộc tuần 8. Không thay thuật toán review hoặc queue cũ.

## Test viết trước và đạt

LessonSyncEngineTest (SQLite database trên đĩa/Room trong Robolectric, fake network gateway):

| Test | Bằng chứng |
| --- | --- |
| `lost response and database reopen retry same UUID payload without adding XP locally` | Server fake đã nhận nhưng response mất; pending/retry count giữ lại, close/reopen DB rồi retry cùng UUID/payload/hash, tổng XP không nhân đôi |
| `lesson dependency order wins over queued timestamps` | Đảo queuedAt; gửi bài 1→2→3, xác nhận XP 90 |
| `pending prerequisite stays retryable and freezes payload until confirmed` | 202 chờ vẫn cùng payload/UUID, XP chưa xác nhận; retry chuyển confirmed |
| `permanent conflict blocks and retains payload while auth only pauses` | 401 giữ row; 409 BLOCKED giữ UUID/payload/code, không tự gửi lại |
| `network HTTP retry statuses preserve pending UUID and payload` | 408/429/500 giữ queue và payload, retry thành công |
| `account change after response cannot write acknowledgement or another account data` | Chuyển account giữa response/commit, không ack hoặc ghi cho account khác; quay lại retry được |
| `result snapshot and acknowledgement rollback together before local commit` | Inject lỗi sau ghi result/snapshot nhưng trước ack: rollback cả ba, giữ queue, retry xác nhận |
| `unexpected acknowledgement never confirms another UUID` | Response UUID khác không ack, giữ pending |
| `blocked legacy queue does not stop lesson queue and reverse also holds` | Coordinator vẫn gọi cả hai queue khi một queue BLOCKED hoặc ném network exception |

ContentPackRepositoryTest thêm `complete download schedules confirmed state pull but partial download does not`.

PostgreSQL 15 Testcontainers, OfflineAttemptPostgresTests thêm hai ca HTTP thật:

- `course snapshot contains only server confirmed progress and course XP`: pending bài 2 cho snapshot XP 0/progress rỗng; bài 1 confirmed cho XP 30/progress passed.
- `account header mismatch rejects submit and snapshot before data access`: 403 với code, chưa insert attempts/XP; endpoint state chưa đăng nhập trả 401.

Backend concurrency/idempotency/grade không chỉ dựa fake Android: các ca PostgreSQL bước 3 và baseline tuần 5 tiếp tục chạy lại. Test Room/Robolectric vẫn chỉ là bằng chứng một phần cho lifecycle; chưa instrumentation force-stop/reboot.

## Kết quả chạy mới

| Checkpoint | Commit | Android | Backend unit | PostgreSQL | Lint | Admin |
| --- | --- | --- | --- | --- | --- | --- |
| Bước 2 | 18bb4eb | 66/66 | 24/24 | 36/36 | 0 errors/38 warnings/new=0 | Build đạt |
| Bước 3 | 5bbf850 | 66/66 | 24/24 | 43/43 | 0 errors/38 warnings/new=0 | Build đạt |
| Bước 4 | 1da4fe4 | 72/72 | 24/24 | 43/43 | 0 errors/38 warnings/new=0 | Build đạt |
| Bước 5 | Commit chứa tài liệu này | **82/82** | **24/24** | **45/45** | **0 errors/38 warnings/new=0** | **Build đạt** |

Không failures/errors/skipped trong lượt test cuối. Lint analyze/report chạy lại thật, XML mới **23:32:21 UTC+07:00**, Assert-LintBaseline gate đạt. Admin TypeScript/Vite là build, không phải test. Lượt đầu backend bước 5 gặp lỗi cú pháp `!!==1` trước khi chạy test; sửa khoảng trắng `!! == 1`, rồi backend unit/PostgreSQL chạy lại đầy đủ BUILD SUCCESSFUL. Không có assertion fail bị bỏ qua/skip/nới.

Android chạy lại đầy đủ sau khi thêm callback schedule cuối; backend/admin không đổi từ lượt regression tương ứng đã đạt. SHA inventory V1–V12 và Room v1–v3 nguyên vẹn, Room v4 git hash bằng HEAD. Checksum V13/upgrade test tiếp tục đạt. Không thêm thư viện/dịch vụ, không destructive fallback.

Lệnh test đã chạy, với JAVA_HOME trỏ JDK21 và ANDROID_HOME trỏ SDK:

```powershell
# Root
.\gradlew.bat :app:testDebugUnitTest --rerun :app:lintDebug --rerun :app:lintAnalyzeDebug --rerun :app:lintReportDebug --rerun --max-workers=2 --console=plain
.\scripts\quality\Assert-LintBaseline.ps1
# backend (working directory)
.\gradlew.bat test --rerun postgresTest --rerun --max-workers=2 --console=plain
# admin-dashboard (working directory)
npm run build
```

Reports tại app/build/test-results/testDebugUnitTest, app/build/reports/lint-results-debug.xml, backend/build/test-results/test và postgresTest. CI hiện có chạy các test này khi mở PR; chưa mở PR hoặc khẳng định GitHub Actions cho checkpoint này đã chạy. CI không tự chạy push feature branch theo workflow hiện tại.

## V13 và giới hạn còn lại

V13 chỉ cộng confirmation_status/default/check, không sửa V1–V12. Test nâng cấp V12 có dữ liệu so từng dòng/cột cũ, giữ XP/default CONFIRMED/checksums/partial indexes và heap filenode. Nguồn/checksum và giải thích chi tiết tại [week6-step3.md](week6-step3.md), [week6-migration-checksums.json](week6-migration-checksums.json), đặc tả mục 14.4.

- **UI hiện tại vẫn là UI tuần 5**, first lesson dùng gateway trực tiếp/SavedStateHandle cũ. Repository/worker mới chưa được nối vào UI; chưa có UI bài 2/3/PRACTICE. Tích hợp UI durable và ba bài thuộc bước 6–7; không tuyên bố app đã học offline end-to-end.
- Chưa chạy instrumentation hoặc test nâng cấp APK/process death/force-stop/reboot/hai account trên thiết bị thật. Đóng/mở lại DB trong Robolectric không thay bằng chứng thiết bị.
- DEMO/fixture lớn chỉ nghiệm thu kỹ thuật, chưa REVIEWED; gate rà soát độc lập trước tuần 8 còn nguyên.
- Chưa chạy preflight/migration trên database mục tiêu; fixture Testcontainers không phải dữ liệu production. V13 vẫn cần kế hoạch DDL lock/backup khi deploy thật.
- Xóa từ bị lesson tham chiếu trả 409 đã xử lý/test ở bước 3. Xóa tài khoản và cleanup theo account thuộc tuần 8, không thêm ở bước 5.
- Không chuyển SavedStateHandle payload cũ sang Room; attempts cũ mất UUID không được suy đoán gộp/xóa. Unique XP vẫn ngăn thưởng lại cùng lesson.

Giữ nguyên thay đổi IDE chủ dự án ngoài commits. **Dừng sau commit/push bước 5, chờ duyệt; không tự làm bước 6–7, không merge main.**
