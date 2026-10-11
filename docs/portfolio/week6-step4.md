# Tuần 6 — Bước 4: durable session/outbox

Nhánh feature/offline-lessons, 11/10/2026. Thêm LessonSessionRepository và query session theo account; AppContainer cung cấp repository. Không đổi schema JSON v1–v4/migration hoặc UI tuần 5.

UUID/revision/thứ tự câu được lưu Room trước học; đáp án/vị trí được commit mỗi lần answer. Chỉ học khi có đủ pack complete và enrollment ACTIVE đã cache. Bài sau cần prerequisite đã xác nhận hoặc bài trước đã nộp đạt tạm; progress/XP confirmed không tăng do grade tạm. PRACTICE không dùng để mở khóa LESSON.

Submit cả online/offline của repository mới đi qua transaction: validate đủ đáp án, đóng băng payload/timestamp/submittedOffline, hash payload, cập nhật session và enqueue cùng UUID. Retry trả payload đã lưu, không đổi timestamp/cờ offline/answers hoặc tạo UUID mới. Sau enqueue không sửa đáp án. Schedule chỉ sau commit. Logout/account switch không xóa queue; mọi read/write scoped-account và kiểm session đang đăng nhập. Không chuyển SavedStateHandle cũ.

## Test viết trước và đã đạt

LessonSessionRepositoryTest dùng SQLite database trên đĩa trong Robolectric, không fake DAO:

- `answers position UUID and revision survive actual database close reopen`: lưu đáp án/vị trí/UUID/revision, đóng/mở lại Room rồi so toàn session.
- `submit freezes same payload and UUID across retry and crash after commit`: retry với clock/offline flag khác và sau reopen giữ payload/hash/outbox; chặn sửa answer đã nộp.
- `crash before commit writes neither frozen session nor outbox`: injected failure sau enqueue rollback session/outbox, không schedule; retry thành công.
- `account switch and logout keep pending data isolated`: không đọc/gửi session account khác; pending account cũ giữ lại qua logout.
- `offline provisional pass opens next lesson without official XP`: chặn bài sau khi thiếu prerequisite, mở bài 2 sau bài 1 đạt tạm, chưa mở bài 3 và XP confirmed vẫn 0.
- `invalid answers and incomplete submission do not mutate durable state`: question/option sai và thiếu answers bị từ chối; không enqueue hoặc ghi dở.

## Regression đầy đủ mới

Android **72/72**, backend **24/24**, PostgreSQL **43/43**, không failures/errors/skipped. Admin npm run build đạt (build). Lint XML mới **23:21:02 UTC+07:00**, errors=0, warnings=38, baseline=38, new=0; gate đạt. Android và backend Gradle BUILD SUCCESSFUL. Inventory V1–V12 và Room v1–v3 không đổi; Room v4 git hash bằng HEAD. V13 được checksum test bảo vệ.

Đây là bằng chứng repository/database, chưa là instrumentation force-stop/reboot hoặc UI cả ba bài. UI tuần 5 vẫn dùng luồng trực tiếp cũ; tích hợp UI durable/cả ba bài thuộc bước 6–7 chưa được phép làm. Worker lesson sync chưa có ở checkpoint này, làm bước 5 sau khi regression đạt. Không destructive fallback/thư viện/dịch vụ mới; không đổi review scheduler/analytics/camera.
