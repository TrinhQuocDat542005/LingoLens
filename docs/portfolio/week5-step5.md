# Tuần 5 — Bước 5: Android bài đầu và checkpoint test tay

Ngày 11/10/2026, nhánh `codex/week5/step1-settings`. **Hoàn tất bước 1–5, dừng trước bước 6; chưa nghiệm thu tuần 5, chưa merge main.**

## Thay đổi và bằng chứng

Test lifecycle/network/double-submit/new attempt được viết trước UI/session implementation. Android: Trang chủ → Mở bài học → course DEMO → học từ/mẫu câu → câu chọn nghĩa → nộp → tổng kết số câu đúng/tổng, trạng thái và XP. UI hiện chỉ mở bài đầu. A1 đã có trong selector Cài đặt; B1/B2 cũ không tự đổi. Không tạo UI PRACTICE.

LessonScreenModel lưu account fence, lesson/revision, UUID, answers, vị trí, phase, payload và result qua SavedStateHandle. Payload chứa mode/timestamp/answers được ghi nguyên trước dispatch và giữ cố định cho retry; busy chặn double-submit, payload đã gửi không bị sửa answers. Khôi phục SUBMITTING thành READY để gửi lại cùng payload; kết quả đã xác nhận không trở thành pending chỉ vì tải catalog lỗi. UUID mới chỉ cho attempt mới.

Robolectric kiểm chứng rotation và tạo lại Activity/ViewModel từ Bundle đã save, **dùng cùng factory createSavedStateHandle như app**. Không tuyên bố force-stop/clear data/bỏ task có durable recovery; không thêm Room/outbox hoặc offline lesson.

Kiểm tra bootJar ban đầu phát hiện hai main class sau khi thêm DemoSeedCli; đã chỉ định entrypoint server LingoLensApplicationKt. Seed CLI vẫn là lệnh riêng, không startup hook. Lỗi này là packaging trước khi backend tests chạy, không bỏ/nới test để đi tiếp.

| Checkpoint | Commit | Android tests | Backend test | postgresTest | Admin build | Lint warnings/new |
| --- | --- | --- | --- | --- | --- | --- |
| Bước 1 | 5477f74 | 46 đạt | 16 đạt | 17 đạt | Đạt | 38/0 |
| Bước 2 | ec5ed6c | 46 đạt | 16 đạt | 25 đạt | Đạt | 38/0 |
| Bước 3 | d59158c | 46 đạt | 22 đạt | 25 đạt | Đạt | 38/0 |
| Bước 4 | 42a6e44 | 46 đạt | 23 đạt | 33 đạt | Đạt | 38/0 |
| Bước 5 | Commit chứa báo cáo này | 51 đạt | 23 đạt | 33 đạt | Đạt | 38/0 |

Lượt cuối: **107 tests, 0 failures/errors/skipped**. Android lint 0 lỗi, 38 warnings, 1 hint hiện có; gate xác nhận 0 warning mới. Admin build/assembleDebug/bootJar thành công, không tính là test. Test thực thi lại bằng `--rerun` sau mỗi bước; packaging/lint có thể dùng task up-to-date khi source tương ứng không đổi.

Artifact: `app/build/outputs/apk/debug/app-debug.apk`, endpoint mặc định `http://10.0.2.2:8080/`. SHA-256: `0868BBC8DC9196F859B2AC99CF86DA1C297BBA05F04F701C22FB1C95756D240D`. Đã xác nhận APK chứa LessonScreenModel trong DEX. Backend JAR: `backend/build/libs/lingolens-backend-1.0.0.jar`.

Các XML mới trong app/backend build/test-results. V1–V8 không đổi qua git diff; V9 và V10–V12 đã commit không bị sửa ở bước sau. Room/schema/outbox/scheduler giữ nguyên; không camera–topic/analytics hoặc thư viện/dịch vụ mới.

## Chạy và test tay

[Hướng dẫn PowerShell từng lệnh](../product/week5-testing.md) có: PostgreSQL dev → seedDemo thủ công → bootRun; build/cài APK emulator/USB; chuyển endpoint, ADB reverse và cách ngắt kết nối thực.

Kịch bản theo thứ tự: register/login → mở bài đầu → đúng/sai → nộp → tổng kết → học/nộp lại không thêm XP → rotation → ngắt kết nối lúc nộp rồi gửi lại. 6/8 đạt và thưởng lần đầu 30; 5/8 trượt; học lại cùng lesson +0. UUID retry và UUID attempt mới được phân biệt.

Máy có AVD Medium_Phone; tại thời điểm đọc inventory, ADB chưa có thiết bị/emulator kết nối. **Chưa cài/chạy test tay trên thiết bị thật/emulator trong checkpoint này.** Không mô tả kết quả Robolectric là bằng chứng on-device. Backend/seed runner chưa được chạy vào database dev vận hành; test dùng PostgreSQL containers riêng. Chủ dự án sẽ khởi động theo runbook và test app một lần.

## Phần chưa làm và giới hạn

- Nội dung là DEMO en-US chưa được người khác rà soát. REVIEWED mới được đưa vào migration chính sau duyệt.
- UI bài 2/3, PRACTICE UI, offline lesson/content pack/durable outbox/session thuộc giai đoạn sau; chưa làm tuần 6.
- Feedback/ba dạng bài/TTS mới, camera–topic, analytics, xóa tài khoản/privacy release gate chưa làm trong tuần 5.
- Test system recreation dựa trên saved-state Bundle được hệ thống lưu; không bảo đảm force-stop/clear data/task bị bỏ, cũng không đồng bộ session sang máy khác.
- PostgreSQL là database production. H2 chỉ kiểm tra API/thuật toán; khóa/partial index/concurrency/read-only được test riêng trên PostgreSQL.
- Backend có compiler warnings nullable JDBC types trong test cũ mới; lint Android không tăng. Các request JSON/path hỏng còn đi qua handler legacy; kiểm tra API mở rộng và UX thiết bị là hạng mục cần đánh giá trong nghiệm thu, không tuyên bố mọi ca lỗi đã bao phủ.
- Khi triển khai thật phải chạy preflight trên DB đích; hiện được bỏ qua theo quyết định chỉ có dev data thử.

**Dừng chờ chủ dự án test app.** Chưa thực hiện checklist nghiệm thu bước 6 hoặc tuần 6; chưa gộp vào main.
