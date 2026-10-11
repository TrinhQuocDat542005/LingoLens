# Tuần 6 — Bước 3: backend offline và V13

Nhánh feature/offline-lessons, 11/10/2026. Hoàn tất sau khi chủ dự án cho bổ sung kiểm chứng V13; chưa merge main.

## V13 và bảo toàn dữ liệu

V13 chỉ ADD COLUMN confirmation_status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED', CHECK CONFIRMED/WAITING_PREREQUISITE vào lesson_attempts. Không DROP/đổi kiểu/UPDATE các cột cũ. Trạng thái riêng cần để phân biệt attempt đã xác nhận và attempt đang chờ prerequisite qua restart/retry; XP/progress không đủ để suy ra vì PRACTICE/bài trượt/nhiều attempts có thể không có XP.

Attempt tuần 5 nhận CONFIRMED, không thay grade/hash/answers/timestamp/progress/XP/enrollment. Constant default trên PostgreSQL 15 tránh rewrite heap; vẫn có DDL lock và CHECK có thể scan. Không tuyên bố zero downtime hoặc đã chạy vào database mục tiêu.

`AttemptConfirmationMigrationTests.V12 to V13 preserves every week5 row checksums indexes and physical heap` chạy PostgreSQL 15 Testcontainers: migrate đến V12, import DEMO, dựng user B2/goal 17/token/library/review/sync receipt và hai attempts (đạt/trượt), 16 answers, progress/XP/enrollment. Snapshot JSONB toàn bộ cột cũ từng dòng của 13 bảng trước–sau bằng equality; chỉ loại cột mới khỏi snapshot attempts và kiểm riêng default CONFIRMED. XP vẫn 30. History checksum V1–V12, định nghĩa hai partial indexes và pg_relation_filenode không đổi. Thử second PUBLISHED/second ACTIVE bị chặn; CHECK status lạ cũng bị chặn.

SHA-256 LF V13: `188d6a3b3cd7c9e3191a471c201e63f884a8a9143cb7ab7ea8865801d58e6668`. Flyway checksum V13 trên PostgreSQL: **-1711790286**, ghi trong XML test. Inventory cố định [week6-migration-checksums.json](week6-migration-checksums.json) gồm nguồn V1–V12 (SQL/migration Kotlin/resources) và V13. Chuẩn hóa CRLF→LF để cùng kết quả Windows/Linux, không cập nhật expected trong test.

`PostgresIntegrationTests.all migrations apply and seed vocabulary on PostgreSQL` không còn chỉ đếm 12: kiểm inventory SHA-256, Flyway validate, exact version list 1–13 và checksum V13 hiện diện; giữ assertions roles/published vocabulary/PostgreSQL. Bảo vệ nguồn migration không bị đổi âm thầm và history khớp nguồn; không thay thế test dữ liệu/hành vi hoặc review inventory khi PR sửa expected. Đặc tả mục 14.4 có danh sách migration và phạm vi V13.

## Backend

Valid offline/out-of-order attempts ghi grade/answers nguyên tử. Pending trả HTTP 202/LESSON_PREREQUISITE_PENDING/WAITING_PREREQUISITE/retryable=true, chưa progress/XP. Retry cùng UUID/hash reevaluate prerequisite rồi xác nhận grade đã lưu; confirmed retry ổn định. Payload conflict/ownership/permanent codes, archived revision, timestamp epoch ms và no 72h cutoff giữ theo hợp đồng. Server không tin điểm/XP client.

Giữ FOR NO KEY UPDATE production và advisory UUID lock; không đổi SQL vì H2. Sáu OfflineAttemptPostgresTests đã đạt: out-of-order/30-day offline; pending concurrency và distinct UUID one award; injected XP rollback; HTTP/codes/ownership; archived grading; admin delete 409 + giữ vocabulary/references. 33 PostgreSQL tests tuần 5 và ba pack tests bước 2 tiếp tục đạt. Admin FK conflict không cascade; precheck và flush FK error mapping giữ transaction rollback.

## Kết quả mới

| Bộ kiểm tra | Kết quả |
| --- | --- |
| Android unit/Robolectric | 66/66 |
| Backend unit/H2 | 24/24 |
| PostgreSQL Testcontainers | 43/43 |
| Lint mới | 0 errors, 38 warnings, 0 warning mới; gate đạt |
| Admin build | npm run build đạt; không tính là test |

Không failures/errors/skipped. Lint XML mới 23:11:58 UTC+07:00; Gradle Android và backend BUILD SUCCESSFUL. Inventory SHA-256 bước 0 và Room v4 hash ở HEAD không đổi. Test reports nằm trong build directories, không commit.

Báo cáo week6-step3-blocked.md giữ như lịch sử lỗi assertion cũ đã giải quyết. V12 fixture không phải dữ liệu production; preflight mục tiêu và gate REVIEWED trước tuần 8 vẫn còn. Chưa UI cả ba bài/instrumentation. Bước tiếp theo là durable session/outbox rồi sync; bước 6–7 chưa được phép.
