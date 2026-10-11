# Tuần 5 — Bước 0: baseline trước triển khai

Ngày kiểm tra: **11/10/2026**, múi giờ Asia/Saigon. Baseline code: commit `67e68f4e433eb0c7ec2ea505693fb401ae4ab678` trên nhánh `main`.

## Phạm vi

Đã cập nhật [hợp đồng tuần 5](../product/lingolens-spec-v2.md#13-bổ-sung--đã-duyệt-hợp-đồng-thực-hiện-chỉ-tuần-5), chạy lại toàn bộ test hiện có và kiểm tra build. Chỉ thay tài liệu; chưa triển khai chức năng hoặc migration mới.

Chọn seed runner gọi thủ công, dev/test, receipt/checksum và transaction; V12 chỉ tạo schema provenance/receipt, không chứa DEMO. Production guard, preflight trước V9, hiển thị số câu đúng/tổng và UUID SavedStateHandle là hợp đồng cho các bước tiếp theo, **chưa được triển khai**.

## Môi trường và lệnh

- Windows, JDK 21.0.10 từ Android Studio JBR; mỗi lệnh Gradle đặt `JAVA_HOME=E:/Program Files/Android/Android Studio/jbr`.
- Android SDK: `C:/Users/ASUS/AppData/Local/Android/Sdk`.
- Docker 29.8.0 đang hoạt động; suite PostgreSQL dùng Testcontainers với PostgreSQL 15 Alpine.
- Node 24.21.0 cho admin dashboard; dùng dependency hiện có, không thêm thư viện.

Các lệnh thực thi:

```text
# Tại root, sau khi đặt JAVA_HOME và ANDROID_HOME
./gradlew.bat :app:testDebugUnitTest --rerun :app:lintDebug :app:assembleDebug --max-workers=2 --console=plain

# Tại backend, sau khi đặt JAVA_HOME
./gradlew.bat test --rerun postgresTest --rerun bootJar --max-workers=2 --console=plain

# Tại admin-dashboard
npm run build
```

`--rerun` ép thực thi lại các task test, không ép biên dịch lại mọi task. Các task build/lint đủ điều kiện dùng cache hoặc được Gradle đánh dấu up-to-date vì source không thay đổi.

## Kết quả thực tế

| Kiểm tra | Kết quả | Bằng chứng trong workspace |
| --- | --- | --- |
| Android unit/Robolectric | 46 tests; 0 failures/errors/skipped | `app/build/test-results/testDebugUnitTest/TEST-*.xml` |
| Backend H2 | 16 tests; 0 failures/errors/skipped | `backend/build/test-results/test/TEST-*.xml` |
| PostgreSQL thật | 5 tests; 0 failures/errors/skipped | `backend/build/test-results/postgresTest/TEST-com.quocdat.lingolens.integration.PostgresIntegrationTests.xml` |
| Android lint | 0 errors, 38 warnings, 1 hint | `app/build/reports/lint-results-debug.xml` |
| APK debug | Gradle BUILD SUCCESSFUL; artifact tồn tại | `app/build/outputs/apk/debug/app-debug.apk` |
| Backend bootJar | Gradle BUILD SUCCESSFUL; artifact tồn tại | `backend/build/libs/lingolens-backend-1.0.0.jar` |
| Admin TypeScript/Vite | `npm run build` thành công | `admin-dashboard/dist/` |

XML test mới có timestamp UTC trong khoảng `2026-10-11T10:32:54Z` đến `2026-10-11T10:33:15Z`; backend hoàn tất lúc khoảng 17:33:52 giờ địa phương. Tổng **67 tests**, không lấy số liệu từ báo cáo lịch sử. Admin `package.json` chưa có script test; build không được tính là test.

Năm test PostgreSQL hiện có:

1. `all migrations apply and seed vocabulary on PostgreSQL()`
2. `concurrent sync retries commit exactly one recognition on PostgreSQL()`
3. `recognition ownership and duplicate reports work on PostgreSQL()`
4. `published vocabulary lookup and search work on PostgreSQL()`
5. `concurrent review retries advance PostgreSQL schedule once()`

Các test này xác nhận baseline V1–V8/sync/review hiện tại; **không** chứng minh partial index, grading, attempt/XP hoặc hai ca đồng thời tuần 5. Các bằng chứng đó phải được bổ sung ở bước 2–4. Không chạy instrumentation trên emulator/thiết bị thật trong bước 0.

## Schema và checksum

Repo hiện có migration **V1–V8**; Room `LearningDatabase` có schema xuất bản **1, 2, 3**, hiện dùng v3. Chưa thêm V9–V12.

SHA-256 dưới đây tính trên byte của file trong working tree Windows; không phải checksum nội bộ Flyway. Đối chiếu trước/sau bước 0: cả tám file giữ nguyên.

| Migration | SHA-256 trước = sau |
| --- | --- |
| V1__create_users_and_roles.sql | `A5B4642CAD63F3CF23E8DC770C93AB6353AC854E078823D8C191F475ED2F6E66` |
| V2__seed_roles.sql | `D331597C071878A398722533AB2745E60D682D364064CA3E1E93E6E3675B07E4` |
| V3__harden_auth_and_profile.sql | `53E5C58FDB5725494BCBB854DF4531FE0183A3289057D2261003D82C208D1D9F` |
| V4__admin_dashboard_foundation.sql | `4B93FA5DBDC713212940B1DBE0F13DB3A0D0E865F3BCD7DBF5AF029037988BFF` |
| V5__seed_core_vocabularies.sql | `D8CD4FF1765B7D4C17212BD685109E10DA887491442DBC61BC6BE182B0BEC66A` |
| V6__link_reports_to_recognition_history.sql | `3DC62751071167EE6CFDA22E822B7A8416397A9EEAFEAE7999DF6CA205589143` |
| V7__durable_learning_sync.sql | `D0818DA1723921E3FB93DDA39F17983083D3D890D4BEF16A4B9B4694B0C63C09` |
| V8__spaced_review.sql | `A2E556FB2F10E6F69DC26A0EB3322A26EF0BA876470D4FF60D554DD3030A84BB` |

## Dữ liệu tuần 1–4 và giới hạn bằng chứng

Bước 0 chỉ dùng database của test; không chạy migration hoặc preflight trên database vận hành. Không chỉnh `users`, learning entries, review, outbox, camera hoặc scheduler. Không sửa Room/schema/code. Các container không thuộc test không được dừng hoặc xóa.

Baseline regression là điểm đối chiếu, chưa đủ để khẳng định migration tương lai bảo toàn dữ liệu. Ở bước 1–2 cần fixture V8 có users A1/B1/B2 và giá trị goal khác nhau, learning entries/tombstones, review state/events và sync receipts; so sánh ID, giá trị và số dòng trước/sau nâng cấp, kiểm tra rollback khi dữ liệu lỗi. Android outbox/Room phải giữ nguyên và chạy lại regression. Không đưa attempt vào outbox tuần 5.

## Điểm dừng

**Bước 0 hoàn tất.** Chưa viết preflight, V9, seed runner, production guard, grading, API hoặc UI lesson. Chưa làm bất kỳ công việc tuần 6 trở đi.

Chờ chủ dự án nói **“tiếp tục”** trước bước 1: test trước → settings/preflight/V9/profile adapter → toàn bộ regression → báo cáo và dừng tiếp.
