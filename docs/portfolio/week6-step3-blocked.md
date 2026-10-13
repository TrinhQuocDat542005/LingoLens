# Tuần 6 — Bước 3: dừng tại assertion đếm migration

11/10/2026, nhánh `feature/offline-lessons`. Bước 2 đã hoàn tất tại commit **18bb4eb**; bước 3 chưa commit. Không làm bước 4–5 hoặc 6–7; không merge main.

## Bước 2 đã đạt

Android 66/66, backend unit 24/24, PostgreSQL 36/36; lint mới 0 errors/38 warnings/0 warning mới; admin build đạt. Cấu hình test dùng lại mẫu Testcontainers/DynamicPropertySource cũ, kiểm metadata PostgreSQL thật. Không đổi cấu hình production để sửa driver. Chi tiết coverage trong [week6-step2.md](week6-step2.md).

## Phần bước 3 đang nằm trong working tree

- V13 mới thêm confirmation_status, NOT NULL/default CONFIRMED/check trạng thái. Không sửa V1–V12.
- Backend chấp nhận submittedOffline, giữ timestamp epoch ms và giới hạn tương lai; không giới hạn sync trễ 72 giờ.
- Attempt hợp lệ đến trước prerequisite được chấm/ghi answers, HTTP 202/LESSON_PREREQUISITE_PENDING, chưa progress/XP. Retry cùng UUID có thể xác nhận khi đủ điều kiện; giữ grade/answers đã ghi và khóa FOR NO KEY UPDATE/advisory hiện có.
- Payload khác UUID cũ bị từ chối, result theo owner; confirmed retry ổn định. PRACTICE không cấp progress/XP LESSON.
- Xóa từ bị lesson tham chiếu trả 409/VOCABULARY_IN_USE, không cascade. Test PostgreSQL HTTP xác nhận giữ rows.

Test được viết trước phần triển khai. Chưa gọi bước 3 hoàn tất hoặc tuyên bố upgrade V12 có dữ liệu đã được nghiệm thu: vẫn cần kiểm chứng backfill/default và snapshot dữ liệu cũ riêng trước triển khai.

## Regression mới sau bước 3

| Kiểm tra | Kết quả |
| --- | --- |
| Android unit/Robolectric | 66/66 đạt, không failures/errors/skipped |
| Backend unit/H2 | 24/24 đạt, không failures/errors/skipped |
| PostgreSQL Testcontainers | 42 tests, **1 failure**, không errors/skipped; 41 đạt |
| Lint chạy lại | XML 22:58:44 UTC+07:00; errors=0, warnings=38, baseline=38, new=0; gate đạt |
| Admin TypeScript/Vite | npm run build đạt; đây là build |

Backend Gradle kết thúc exit 1, BUILD FAILED tại postgresTest. XML mới 22:59:56 UTC+07:00. Kiểm tra SHA-256 inventory V1–V12/Room v1–v3 và git hash Room v4 đạt; các file bất biến không đổi. Có hai cảnh báo compiler trong test mới do queryForObject Int? truyền vào assertEquals Int; chưa sửa sau điểm dừng, chúng không phải cảnh báo Android lint.

## Failure duy nhất và đề xuất

`PostgresIntegrationTests.all migrations apply and seed vocabulary on PostgreSQL()` dòng 172:

`AssertionFailedError: expected: <12> but was: <13>`.

Test cũ đếm flyway_schema_history versioned successful migrations và hard-code 12. Thêm V13 làm số thực tế thành 13; bỏ sót cập nhật assertion này. Không có failure khác trong XML lượt mới. Chưa sửa hoặc nới assertion sau khi phát hiện failure.

Khi chủ dự án cho tiếp tục: cập nhật expected inventory chính xác thành 13 và kiểm các version 1–13/success, giữ các assertions roles/vocabulary/PostgreSQL. Bổ sung kiểm chứng nâng cấp V12→V13 có dữ liệu; làm sạch cảnh báo nullability test mà không giảm assertions. Sau đó chạy lại đủ bộ trước commit bước 3.

## Sáu test mới PostgreSQL đã đạt

Class `OfflineAttemptPostgresTests`:

1. `out of order valid attempts persist then confirm only after prerequisites`: bài 3/2 tới trước vẫn ghi đủ answers, pending retry không thêm attempt, không progress/XP; sau bài 1/2 mới xác nhận đúng thứ tự, timestamp offline 30 ngày vẫn hợp lệ.
2. `concurrent pending retries and different UUIDs award only once`: hai retry pending cùng UUID sau prerequisite cho cùng kết quả; hai UUID bài 3 khác nhau chỉ có một thưởng.
3. `pending confirmation rollback retains original grade and waiting state`: trigger PostgreSQL gây lỗi XP; transaction rollback giữ pending/answers và chưa ghi progress/XP mới; retry sau bỏ trigger xác nhận được.
4. `HTTP pending errors and ownership have stable codes`: 202 + code/retryable; đổi payload 409; answers sai revision 400; revision không tồn tại 404; account khác không đọc result.
5. `archived revision remains gradable without changing snapshot`: archived vẫn nộp được, học lại không thưởng lần hai.
6. `admin deleting referenced vocabulary returns conflict and preserves rows`: HTTP 409 với mã rõ; vocabulary và lesson references giữ nguyên.

Các test này đã chạy trên PostgreSQL 15 Testcontainers, không H2. Chúng không thay thế test migration V12→V13 có dữ liệu hoặc instrumentation thiết bị.

Giữ nguyên file IDE của chủ dự án. Dừng chờ chủ dự án cho tiếp tục; chưa commit bước 3, chưa bắt đầu durable session/outbox hoặc lesson sync.
