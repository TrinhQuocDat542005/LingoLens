# Tuần 5 — Bước 2: schema và seed DEMO

Ngày 11/10/2026, nhánh riêng `codex/week5/step1-settings`; chưa merge main.

Test seed/index/guard viết trước phần triển khai. Đã thêm V10–V12 bằng Flyway Java migrations có checksum CRC32 của hai resource SQL mỗi phiên bản (chuẩn hóa CRLF/LF). Schema chung chạy ở H2/PostgreSQL; PostgreSQL-only resource chứa partial unique index và trigger immutable/stable lesson. H2 không được dùng để chứng minh các ràng buộc đó. V1–V9 không sửa.

V10: curriculum/topic/question/snapshot và published revision index. V11: enrollment/attempt/answers/progress/XP và active enrollment index. V12: provenance/receipt, không có dòng DEMO. Gói DEMO ở resource `content/demo-week5.json`, 18 từ, 24 câu, 3 bài; JSON Schema `content/seed.schema.json`, validator kiểm tra cấu trúc/ngữ nghĩa, không thêm thư viện engine. `.gitattributes` chỉ giữ LF cho JSON nội dung mới để checksum byte ổn định giữa checkout.

Seed runner `seedDemo` gọi tường minh, dev/test only, prod/production cấm kể cả mixed profiles. Một transaction và advisory transaction lock theo pack UUID: checksum giống no-op, khác từ chối; không upsert vocabulary cũ. Snapshot được dựng từ đúng vocabularyWords đã kiểm tra. Seed runner không là ApplicationRunner, không chạy khi backend khởi động. REVIEWED chờ migration đã duyệt sau này.

ProductionContentGuard khởi tạo sau Flyway, từ chối DEMO trong receipts hoặc course/revision kể cả receipt đã thiếu. Test bean initialization xác nhận lỗi trước khi context hoạt động. Legacy V5 không bị gắn DEMO.

| Suite/check | Kết quả mới |
| --- | --- |
| Android | 46 đạt |
| Backend H2 | 16 đạt |
| PostgreSQL | 25 đạt = 17 bước 1 + 8 content tests |
| Lint gate | 0 lỗi, 38 warnings, 0 warning mới |
| Admin TypeScript/Vite build | Thành công, không tính là test |

Chạy đủ cùng bộ lệnh như bước 1 với task test `--rerun`. PostgreSQL kiểm chứng atomic rollback/collision, concurrent seed, duplicate checksum, malformed/semantic JSON, hai partial index, immutable published/archived content và production guard. 12 vocabulary V5 giữ nguyên sau import.

Chưa có grading/API/UI bài học (bước 3–5). Chưa gọi runner vào database dev vận hành. Chưa có REVIEWED content; DEMO chỉ để thử vòng học, không hứa nội dung đã được người khác rà soát. Không camera/review scheduler/analytics/CMS.
