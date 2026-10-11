# Tuần 5 — Bước 1 hoàn tất: settings và profile adapter

Ngày 11/10/2026, nhánh `codex/week5/step1-settings`. Không merge main. [Lỗi H2 trước đó](week5-step1-blocked.md) là lịch sử đã xử lý theo quyết định tiếp theo của chủ dự án.

## Thay đổi

- V9 tạo learner_settings, backfill nguyên target_level/daily_goal cũ; constraint dừng khi giá trị không hợp lệ. Không sửa V1–V8. Mức mới A1, goal 5/10, learning_goal UNSET; timezone/onboarding NULL. Không đổi mức cũ B1/B2.
- Profile adapter giữ nguyên DTO GET/PUT và register, admin đọc từ nguồn settings. Admin bootstrap tạo settings cho tài khoản mới.
- PUT dùng last-write-wins, hai UPDATE cột profile theo thứ tự users → learner_settings trong cùng transaction. Không SELECT khóa bi quan tường minh, không FOR UPDATE, không cập nhật khóa/email/password/roles. Hai bản mirror rollback cùng nhau khi lỗi. Refresh entity sau JDBC để response không lấy cache cũ.
- Test viết trước phần triển khai. Preflight trên database vận hành được bỏ qua theo chỉ thị (chỉ dữ liệu dev thử); vẫn phải chạy trước triển khai thật.

## Kiểm chứng mới

| Suite/check | Kết quả |
| --- | --- |
| Android unit/Robolectric | 46 đạt |
| Backend H2 API regression | 16 đạt |
| PostgreSQL Testcontainers | 17 đạt: 9 integration + 4 migration + 4 preflight |
| Lint | 0 lỗi, 38 warnings, 0 warning mới |
| Admin TypeScript/Vite build | Thành công, không tính là test |

Lệnh: root `:app:testDebugUnitTest --rerun :app:lintDebug`, gate `scripts/quality/Assert-LintBaseline.ps1`; backend `test --rerun postgresTest --rerun`; admin `npm run build`. Gradle dùng `--max-workers=2 --console=plain`, JDK21 và SDK như bước 0. XML kết quả trong các thư mục test-results của app/backend. Backend hoàn tất khoảng 19:15:39 +07:00.

PostgreSQL xác nhận backfill B1/8, B2/23, A1/1 nguyên giá trị; password, roles, token, learning_entries/tombstone, review_cards/events và sync_receipts không đổi khi nâng V8 → V9. Unsupported/null level và goal ngoài khoảng khiến V9 rollback cả schema, history vẫn ở V8.

API integration xác nhận đúng tập trường/kiểu legacy, A1 được nhận, mức lạ bị từ chối, register defaults và admin đọc settings khi giá trị legacy khác. Trigger PostgreSQL gây lỗi sau users write xác nhận rollback cả hai bản mirror.

Concurrency chỉ ở postgresTest: hai profile PUT để lại một bộ name/level/goal hoàn chỉnh; login hoàn tất và chèn refresh token khi profile UPDATE còn chưa commit, đang bị giữ ở learner_settings. Không đổi PostgreSQL locking để H2 chạy được. H2 chỉ regression hợp đồng API.

Chưa làm bước 2–5 trong commit bước 1; tiếp tục theo quyền đã cấp. Backend có cảnh báo compile test nullable JDBC return types, không phải cảnh báo Android lint hoặc test failure; không bỏ/nới test để che lỗi.
