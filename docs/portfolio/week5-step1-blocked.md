# Tuần 5 — Bước 1 dừng khi regression thất bại

**Báo cáo lịch sử:** lỗi dưới đây đã được xử lý sau khi chủ dự án duyệt last-write-wins; kết quả mới ở [bước 1 hoàn tất](week5-step1.md). Không áp dụng đề xuất FOR UPDATE trong báo cáo này.

Ngày: 11/10/2026. Nhánh: `codex/week5/step1-settings`. Bước 1 chưa hoàn tất; chưa sang bước 2–5 hoặc bước 6. Không merge/push main.

## Phần đã viết, chưa được xác nhận đạt

Test được viết trước phần triển khai: nâng cấp PostgreSQL V8 → V9, bảo toàn B1/B2/goal và các bảng cũ, invalid backfill rollback, DTO/profile/register/admin, A1 validation và mirror rollback khi PostgreSQL trigger gây lỗi.

Đã viết V9 expand/backfill, entity/repository learner_settings và profile adapter; nối GET/PUT, đăng ký, admin đọc profile và admin bootstrap vào adapter. V9 giữ nguyên giá trị cũ, không UPDATE dữ liệu users, chỉ đổi default target_level cho account tạo mới. File V1–V8 không được chỉnh. Chưa chạy migration trên database dev/vận hành.

Các thay đổi còn trong working tree; **chưa tạo commit bước 1**, vì regression không đạt. Commit đã push gần nhất vẫn là `179d82e` — checkpoint preflight, không phải hoàn tất bước 1.

## Kết quả lượt kiểm tra bước 1

| Kiểm tra | Kết quả mới |
| --- | --- |
| Android unit/Robolectric | 46 đạt |
| Android lint gate | 0 lỗi, 38 warnings, 0 warning mới; hint hiện có giữ nguyên |
| Backend H2 `test` | 16 chạy, 15 đạt, 1 thất bại |
| PostgreSQL `postgresTest` | Chưa chạy trong lượt này: Gradle dừng sau `test` thất bại |
| Admin `npm run build` | Thành công; đây là TypeScript/Vite build, không phải test |

Lệnh backend là `./gradlew.bat test --rerun postgresTest --rerun --max-workers=2 --console=plain` với JDK21. Không gọi kết quả PostgreSQL từ checkpoint preflight là bằng chứng mới cho V9/adapter.

Backend compile test còn có cảnh báo Kotlin về nullable JDBC return types; đây không phải warning lint Android và không phải nguyên nhân test thất bại.

## Lỗi và bằng chứng

Test cũ `AuthControllerTests.login profile update refresh and logout work end to end()` thất bại tại dòng 62: PUT `/api/v1/users/me` mong **200**, nhận **500**.

XML: `backend/build/test-results/test/TEST-com.quocdat.lingolens.auth.AuthControllerTests.xml`; HTML: `backend/build/reports/tests/test/index.html`.

Log cho thấy truy vấn khóa row do Hibernate sinh ra kết thúc bằng `FOR NO KEY UPDATE`; H2 báo SQLSTATE `42000` vì không hỗ trợ cú pháp này. `UserRepository.findLockedById` mới dùng `PESSIMISTIC_WRITE`; cấu hình chung đang chỉ định PostgreSQL dialect, và application-test dùng H2 nhưng kế thừa dialect đó. GET/register chạy được; đường PUT có truy vấn khóa mới làm lộ sự khác biệt này.

Không kết luận V9 backfill hoặc transaction PostgreSQL đã đạt khi suite PostgreSQL chưa chạy. Không sửa, bỏ test, tắt khóa hoặc thay assertion để đi tiếp.

## Hướng xử lý đề xuất, chưa thực hiện

Xử lý truy vấn khóa bằng `FOR UPDATE` tường minh, tương thích PostgreSQL và H2, vẫn giữ row lock và transaction. Sau đó chạy lại toàn bộ Android/backend/PostgreSQL/admin build/lint. Một phương án khác là sửa test profile dùng đúng H2 dialect; cần bảo đảm test PostgreSQL tiếp tục dùng PostgreSQL dialect. Chưa áp dụng phương án nào.

Chờ chủ dự án cho phép xử lý lỗi này. Không cần đổi quy tắc sản phẩm; không tắt hoặc nới kiểm chứng concurrency/rollback.

## Trạng thái test app

Chưa có seed runner/DEMO/production guard, grading, attempt/XP API hoặc UI bài đầu: bước 2–5 chưa làm. Vì vậy chưa có lệnh seed DEMO hoặc APK tuần 5 đã xác nhận để hướng dẫn cài/test bài học. Kịch bản đăng nhập → mở bài → chấm/XP → retry/rotation/network phải chờ các bước đó đạt; không đưa lệnh hoặc mô tả tính năng chưa tồn tại.

Những rủi ro còn mở: regression PUT hiện thất bại trên H2; V9/adapter mới chỉ được viết, chưa có bằng chứng PostgreSQL mới; bốn cảnh báo compile test cần xem lại khi được phép sửa. Chưa chạy preflight trên database vận hành theo chỉ thị chủ dự án; phải chạy trước triển khai thật.
