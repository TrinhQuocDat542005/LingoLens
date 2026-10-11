# Tuần 5 — Bước 1, checkpoint preflight

Ngày thực hiện: 11/10/2026. Nhánh: `codex/week5/step1-settings`, tách từ `85d3828`. Chỉ hoàn tất phần preflight; **bước 1 chưa hoàn tất**. Không merge hoặc push main.

## Test trước → SQL chỉ đọc → regression

Viết `LearnerSettingsPreflightTests` trước SQL: lần chạy đầu 4/4 fail với `Missing read-only preflight SQL`. Sau bổ sung SQL, bộ đọc SQL trong test ban đầu cắt nhầm dấu chấm phẩy trong chuỗi phân cách lỗi; đã sửa helper và chạy lại, 4/4 đạt. Không chỉnh migration V1–V8 để làm test đạt.

SQL [learner-settings-v9.sql](../../backend/src/main/resources/preflight/learner-settings-v9.sql) ngoài location migration chính. Mở transaction READ ONLY, SELECT các dòng lỗi, COMMIT. [Hướng dẫn vận hành](../product/week5-preflight.md) nêu cách chạy và điều kiện dừng deploy. Query không xuất email, tên, password hoặc token, không sửa giá trị.

## Kết quả fixture PostgreSQL thật

Fixture V8 hợp lệ: `101/A1/1`, `102/B1/8`, `103/B2/50` → **0 dòng lỗi**, giữ nguyên các giá trị.

| User ID fixture | target_level | daily_goal | Lỗi trả về |
| --- | --- | --- | --- |
| 201 | C1 | 0 | TARGET_LEVEL_UNSUPPORTED; DAILY_GOAL_OUT_OF_RANGE |
| 202 | NULL | 51 | TARGET_LEVEL_NULL; DAILY_GOAL_OUT_OF_RANGE |
| 203 | B2 | -1 | DAILY_GOAL_OUT_OF_RANGE |
| 204 | b1 | 5 | TARGET_LEVEL_UNSUPPORTED |
| 205 | ` A1` | 5 | TARGET_LEVEL_UNSUPPORTED |

Dòng `206/B1/10` hợp lệ không xuất hiện trong kết quả. Fixture riêng `301/B1/NULL` → DAILY_GOAL_NULL: ca này mô phỏng schema drift bằng cách bỏ NOT NULL **chỉ trong container test**, vì V8 thật không cho phép daily_goal NULL. target_level NULL có thể tồn tại theo schema V8 hiện tại.

Bốn test mới xác nhận:

1. A1/B1/B2 và goal khác nhau, gồm biên 1/50, không bị sửa hoặc báo lỗi.
2. ID, giá trị, thông báo và danh sách trường đầu ra đúng; dòng hợp lệ không bị báo; snapshot dữ liệu không đổi.
3. NULL goal trong schema drift được báo, không bị tự sửa.
4. PostgreSQL từ chối UPDATE trong transaction của script, SQLSTATE **25006**; goal vẫn nguyên sau rollback.

Snapshot so sánh tất cả bảng public trước/sau preflight, gồm users/password, roles/user_roles, refresh_tokens, learning_entries/tombstone, review_cards/events, sync_receipts và Flyway history. Chỉ test trên container riêng; không kết nối database vận hành.

## Regression checkpoint

| Loại | Kết quả |
| --- | --- |
| Android unit/Robolectric | 46 đạt; 0 fail/error/skip |
| Backend H2 | 16 đạt; 0 fail/error/skip |
| PostgreSQL Testcontainers | 9 đạt = 5 cũ + 4 preflight; 0 fail/error/skip |
| Android lint | 0 lỗi; 38 warnings, 1 hint; 0 warning mới |
| Admin TypeScript/Vite build | Thành công; không phải test suite |

Đã chạy `:app:testDebugUnitTest --rerun :app:lintDebug`, backend `test --rerun postgresTest --rerun` với `--max-workers=2 --console=plain`, và `npm run build` ở admin-dashboard. Dùng JDK21/Android SDK/Docker như baseline bước 0. Test được thực thi lại; lint dùng kết quả up-to-date vì Android source không thay đổi.

Bằng chứng XML: `app/build/test-results/testDebugUnitTest/`, `backend/build/test-results/test/`, `backend/build/test-results/postgresTest/`. Lint XML: `app/build/reports/lint-results-debug.xml`.

## Gate lint và bảo toàn migration

[Inventory 38 warnings](week5-lint-baseline.json) lưu ID/message/file/line/column từ commit `85d3828`. [Assert-LintBaseline.ps1](../../scripts/quality/Assert-LintBaseline.ps1) từ chối lint errors, tổng warning tăng hoặc warning mới; đối chiếu cả số lần xuất hiện để việc xóa một warning cũ không che warning mới. Line/column không dùng làm identity, để thay đổi vị trí dòng không bị xem là warning mới.

Chạy từ root sau lint: `./scripts/quality/Assert-LintBaseline.ps1`. Kết quả checkpoint: `errors=0, warnings=38, baseline=38, new=0`. Không suppress warning hoặc thay dependency/tech stack. Gate được chạy bằng lệnh ở checkpoint; chưa thay CI pipeline.

Checksum byte file V1–V8 đối chiếu [bước 0](week5-step0.md) đều giữ nguyên. Room vẫn v3; chưa thay outbox hoặc scheduler.

## Chưa làm và điểm chờ

**Chưa viết V9, learner_settings hoặc profile adapter.** Chưa có bằng chứng backfill V8 → V9, migration dừng khi invalid, mirror rollback, register/admin qua adapter hoặc PUT A1. Các test này sẽ được viết trước phần triển khai tương ứng sau khi chủ dự án xác nhận preflight; không gọi chúng là đã đạt.

Chưa tự xử lý các dòng lỗi; không đổi B1/B2 → A1. Chưa làm tuần 6 trở đi.

**Dừng chờ xác nhận kết quả preflight.** Sau khi được duyệt mới tiếp tục phần V9/adapter; khi hoàn tất bước 1 chạy lại đầy đủ test/build/lint và dừng báo cáo lần nữa.
