# Tuần 8 — rà alias SQL native (14/10/2026)

Phạm vi: toàn bộ `backend/src/main`, gồm SQL trong Kotlin/JdbcTemplate, các SQL resource migration/content-schema/preflight và kiểm tra khai báo native query JPA. Các `@Query` trong AdminRepositories/UserRepository là JPQL, không phải native SQL. Không thấy `nativeQuery=true`, NamedNativeQuery hoặc createNativeQuery. Không rà SQL do Hibernate tự sinh như thể đó là source SQL của repo.

Đã tìm và đọc các SELECT/CTE/JOIN/RETURNING, phân biệt keyword trong ORDER BY/GROUP BY, kiểu TIMESTAMP WITH TIME ZONE, lời chú thích và literal nội dung seed với alias thật. Đây là rà source thủ công có hỗ trợ rg, không phải parser SQL hoặc lời bảo đảm về mọi alias được thêm sau này.

| Source | Kết quả |
| --- | --- |
| analytics/AnalyticsService.kt | Alias trần `day` sau `::date` gây syntax error trên PostgreSQL15. Đổi thành `AS event_day`, cập nhật DISTINCT ON/order/existence references. Giữ nguyên filter, maturity, timezone, counts và hợp đồng |
| assessment/LessonService.kt | Table alias c/u/l/p/r/x và column alias lesson_id/revision_id/lesson_position/unit_position; không thấy bare alias trùng nhóm day/year/month/time/user/order/group |
| curriculum/ContentPackService.kt | Alias r/l/u/c và lesson_id; không thấy alias từ khóa cần sửa |
| curriculum/ContentSeedImporter.kt, ProductionContentGuard.kt | Không thấy alias từ khóa cần sửa |
| settings/ProfileAdapter.kt, sync/ReviewStore.kt, sync/SyncController.kt, admin/AdminService.kt | Không thấy alias từ khóa cần sửa. Không thay SQL khóa hoặc scheduler/review để xử lý vấn đề alias |
| SQL resource V1–V15, content-schema/PostgreSQL trigger, preflight/learner-settings-v9.sql | Không thấy alias từ khóa khác cần sửa. user_id trong preflight đã có AS; OLD/NEW/INTO là ngữ cảnh trigger/PLpgSQL. Giữ nguyên toàn bộ nguồn migration |

Chỉ một điểm cần sửa được tìm thấy: day trong query runtime D1/D7, không nằm trong migration. V15 chưa áp vào database mục tiêu; chỉ áp trong fixture/Testcontainers. Checksum source V1–V15 và schema Room1–4 được giữ nguyên trong lượt sửa.

## Các test PostgreSQL bảo vệ truy vấn đã sửa

Assertion của hai test fail cũ giữ nguyên:

- AnalyticsPostgresTests.`direct report respects DST calendar day and excludes immature observations`: America/New_York ngày23 giờ, D1/D7 và mẫu chưa chín/rate null.
- AnalyticsPostgresTests.`retention filters before prune and direct D1 D7 uses event timezone without stored anchors`: filter90 ngày trước prune, count không đổi sau prune, calendar date, admin endpoint, không anchor schema.

Bổ sung trước khi sửa source:

- `New York 23 and 25 hour DST days count calendar D1 and D7`: 08/03 và01/11/2026; tự assert elapsed23/25 giờ, rồi assert D1/D7 theo ngày địa phương. Clock query cố định là fixture, không phải clock production.
- `same instants use distinct New York and Ho Chi Minh calendar dates`: cùng timestamp, New York cùng ngày nên không D1; Asia/Ho_Chi_Minh đã qua ngày nên có D1; cả hai D7. Không đổi timezone/mốc thành trường lưu cố định.
- `offline app opens arriving D7 D1 then first day use occurred order and frozen UUID`: gửi qua batch thật theo thứ tự D7→D1→ngày đầu, kiểm received order bằng SQL, query dùng occurred order; retry cùng UUID dedupe, vẫn ba rows.

Fixture AfterEach dọn mọi account do test tạo cùng analytics và dữ liệu phụ thuộc, giữ nguyên expected1/assertion. Job startup/daily không có bean trong profile test; production default vẫn bật, body prune không đổi. Test riêng1/1, cả lớp hai seed khác nhau14/14; full gate PostgreSQL70/70 đạt. Bằng chứng mới nhất tại [week8-backend-analytics.md](week8-backend-analytics.md).
