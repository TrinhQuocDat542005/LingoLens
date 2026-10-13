# Tuần 6 — Bước 2: pack và shared grader

Nhánh `feature/offline-lessons`, 11/10/2026. Hoàn tất sau khi chủ dự án cho sửa cấu hình postgresTest; không merge main.

## Sửa lỗi test

XML lượt trước có một root cause: org.h2.Driver không nhận URL PostgreSQL. Hai test sau bị Spring failure threshold, chưa chạy assertions. Các test Spring cũ dùng DynamicPropertySource với bốn properties (URL/user/password/driver); test migration/seed cũ dùng datasource trực tiếp từ container. Không có base class chung.

Class mới dùng lại mẫu LessonAttemptPostgresTests: PostgreSQL 15 Testcontainers, @Container @JvmField, @DynamicPropertySource và org.postgresql.Driver. Không đổi cấu hình production hoặc SQL để xử lý lỗi driver. Test kiểm databaseProductName=PostgreSQL và JDBC URL PostgreSQL đã đạt.

## Thay đổi và bằng chứng

Backend có endpoint manifest/pack authenticated, trả snapshot theo revision PUBLISHED/ARCHIVED; bytes ổn định và SHA-256. Android tải/validate pack rồi commit Room theo account; không ghi đè pack complete, course chỉ sẵn sàng khi đủ ba pack. Không thêm dependency hoặc schema/migration.

Hàm chấm thuần chuyển vào shared/assessment/core, backend wrapper và Android PackGrader cùng gọi hàm đó. Hai test adapter dùng cùng assessment-vectors.json để đối chiếu điểm/ngưỡng nguyên 70%/floor/số đúng. Fixture lớn chỉ dành cho test: ba bài, sáu từ và mười câu mỗi bài, lựa chọn dài.

| Yêu cầu | Test |
| --- | --- |
| PostgreSQL thật, manifest ba bài, checksum/size/bytes bất biến, answer key | ContentPackPostgresTests: `manifest has three stable immutable packs including answer keys` |
| Chưa đăng nhập bị từ chối; DRAFT bị từ chối, PUBLISHED/ARCHIVED đọc được | ContentPackPostgresTests: `pack endpoint requires login and unpublished revisions are rejected` |
| Fixture lớn giữ đúng format | ContentPackPostgresTests: `large format fixture retains ten questions long labels and six words in each lesson`; ContentPackRepositoryTest: `large three lesson fixture validates complete format and account isolation` |
| Tải dở, retry | ContentPackRepositoryTest: `interrupted download never stores a complete pack` |
| Checksum sai giữ pack tốt | `bad checksum preserves good pack and retry finishes course` |
| Revision sai dù checksum đúng | `wrong revision is rejected even with matching checksum` |
| Incomplete không đọc/dùng được | `incomplete database pack cannot be read or used` |
| Manifest đổi không ghi đè pack tốt | `changed manifest cannot overwrite a good immutable pack` |
| Structure lỗi, đổi account trong download | `invalid structure is not made complete even with matching checksum`; `account change during download cannot persist the response` |
| Room production đủ migrations, không fallback | ProductionRoomConfigurationTest; tự chạy qua testDebugUnitTest trong CI hiện có |
| Chấm Android/backend dùng cùng vector/hàm | PackGraderTest, SharedGraderVectorTests |

## Regression mới

Android **66/66**, backend unit **24/24**, PostgreSQL **36/36**, không failures/errors/skipped. Admin `npm run build` đạt (build, không phải test). Lint XML mới 22:51:14 UTC+07:00; gate errors=0, warnings=38, new=0. Các task test/analyze/report chạy lại; không lấy báo cáo lịch sử. Inventory SHA-256 V1–V12 và Room v1–v3 đạt, Room v4 git hash bằng HEAD bước 1.

Chưa có instrumentation/upgrade APK trên thiết bị hoặc UI offline cả ba bài. DEMO chưa được rà soát, REVIEWED gate trước tuần 8 giữ nguyên. Báo cáo lỗi trước sửa vẫn lưu tại week6-step2-blocked.md như lịch sử, đã được giải quyết trong lượt này.
