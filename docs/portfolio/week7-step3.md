# Tuần 7 — Bước 3: UI ba dạng, feedback và TTS/fallback

Ngày 13/10/2026, nhánh `feature/question-types-tts` từ tuần 6. Tests viết trước implementation; gate đầy đủ sau mỗi bước đạt trước commit. Không merge main. [PR tuần 6 #4](https://github.com/TrinhQuocDat542005/LingoLens/pull/4) đã kiểm tra ngày 13/10 lúc 20:30 UTC+07: open, merged=false; head feature/offline-lessons, base main. Nhánh tuần 7 không nằm trong PR này.

## Thay đổi

- UI cả ba bài hỗ trợ chọn nghĩa, nghe chọn từ và điền từ/cụm từ. Raw fill được lưu qua repository Room; editor giữ input ngay khi gõ, model serialize từng lần ghi bằng mutex thay cho bỏ thao tác khi busy. Blank không đi tiếp/nộp; input quá 500 có thông báo. Shared mark hiển thị đúng/sai và explanation; câu điền có nút kiểm tra, UI chỉ đi tiếp sau feedback.
- TTS lesson đọc các thuộc tính Voice.locale/network/quality thực, chọn en-US offline trước. Chỉ có voice cần mạng thì dùng khi online và hiển thị rõ. Thiếu giọng/lỗi có hướng dẫn cài voice và fallback được đánh dấu, giữ nguyên question/options/answer key/trọng số/mẫu số. Không tự chọn đáp án hoặc giả hoàn thành. Stop khi đổi câu/background/rời bài; close khi dispose/đổi account; callbacks cũ bị chặn.
- Luyện lại sai chỉ cục bộ sau frozen submission. Giữ số đúng/tổng của lượt đã nộp, UUID/raw payload/outbox/progress/XP, không sinh attempt hoặc request PRACTICE. UI không hiển thị XP tạm như chính thức.
- Không sửa PronunciationButton/camera/review scheduler, migration V1–V13 hoặc Room exports v1–v4; không thêm library/cloud TTS/audio/dịch vụ. Backend bước 3 không đổi so với bước 2.

## Tests mới

`MixedLessonScreenModelTest` (7 Room/Robolectric): raw fill/UUID/revision/vị trí qua reopen; rapid edits không rơi mất; blank không nộp/đi tiếp và feedback sai; fallback giữ mẫu số/payload; luyện sai so nguyên session/outbox/curriculum/pack trước–sau; feedback cùng shared mark và không luyện sai trước nộp; cả ba bài typed enqueue offline với 0 XP chính thức. Fixture cancel và join mọi model job trước close Room, không busy_timeout/retry/sleep.

`LessonSpeechControllerTest` (10 fake engine): thiếu giọng, từ chối en-GB, ưu tiên offline, network-only online/offline, refresh sau cài giọng, init/speak lỗi, callbacks cũ, stop/background và close. `AndroidLessonSpeechEngineTest` (1 Robolectric): chiếu Voice.locale/network/quality thật của đối tượng Android, không chỉ setLanguage. Các test này không chứng minh âm thanh hoặc installer trên thiết bị.

## Gate mới, UTC+07

| Phần | Kết quả | Giờ |
| --- | --- | --- |
| Android unit/Robolectric | 126/126, 0 failure/error/skipped | 20:28:52–20:31:58 |
| Backend unit/H2 | 37/37, 0 failure/error/skipped | Backend 20:28:56–20:32:47; unit XML 20:29:56 |
| PostgreSQL 15 Testcontainers | 52/52, 0 failure/error/skipped/initialization error | XML 20:32:47, cùng lệnh backend sau unit |
| Lint analyze/report mới | 0 errors / 38 warnings / 0 new | XML 20:31:56, Assert-LintBaseline đạt |
| Admin TypeScript/Vite | Build đạt, không tính là test | 20:28:55–20:29:04 |
| APK debug | Build đạt, packageDebug chạy lại | APK 20:31:57, endpoint emulator 10.0.2.2:8080 |

APK SHA-256: `08CEF911884224AC2E689E6D0BF37DBEBF787396453B0152BAC60F7772D80D42`.

| PostgreSQL class | Tests thật | Failure / error / skipped |
| --- | --- | --- |
| AttemptConfirmationMigrationTests | 1 | 0 / 0 / 0 |
| LessonAttemptPostgresTests | 8 | 0 / 0 / 0 |
| OfflineAttemptPostgresTests | 8 | 0 / 0 / 0 |
| Week7QuestionPostgresTests | 6 | 0 / 0 / 0 |
| ContentPackPostgresTests | 4 | 0 / 0 / 0 |
| ContentSeedTests | 8 | 0 / 0 / 0 |
| PostgresIntegrationTests | 9 | 0 / 0 / 0 |
| LearnerSettingsMigrationTests | 4 | 0 / 0 / 0 |
| LearnerSettingsPreflightTests | 4 | 0 / 0 / 0 |
| Tổng | 52 | 0 / 0 / 0 |

## Ma trận tuần 7 theo §10

| Tiêu chí | Trạng thái | Bằng chứng và giới hạn |
| --- | --- | --- |
| Ba dạng trên chặng ba bài, server chấm cùng Android | Đạt | TypedPackGraderTest/TypedGraderVectorTests cùng chín vectors; Week7QuestionPostgresTests; mixed seed/large fixture ba bài |
| Chuẩn hóa input có giới hạn, variants hữu hạn, không fuzzy; 70% trước floor | Đạt | Vectors Unicode/locale/typo/punctuation/7–6 trên 10; PG raw conflict/invalid rollback; vector lịch sử giữ nguyên |
| Feedback đúng/sai, explanation và luyện lại sai không sửa lượt đã nộp | Đạt một phần – chưa kiểm chứng trên thiết bị | MixedLessonScreenModelTest so shared mark và exact Room snapshots; Compose build đạt, chưa instrumentation/interaction trên máy |
| Thiếu TTS không giả hoàn thành, fallback cùng câu/mẫu số | Đạt một phần – chưa kiểm chứng trên thiết bị | Controller 10 tests + model fallback test; chưa kiểm engine/installer thực |
| Ưu tiên en-US offline theo thuộc tính Voice, lifecycle/error được xử lý | Đạt một phần – chưa kiểm chứng trên thiết bị | Fake controller và Voice mapping Robolectric; chưa nghe/đo chất lượng trên ba thiết bị |
| Giữ dữ liệu/queue/revision cũ, XP chính thức một lần | Đạt một phần – chưa kiểm chứng trên thiết bị | PG upgrade từng dòng, old pack golden, retry/XP tests; Room reopen/freeze/account tests; APK upgrade/process death/reboot/hai account thật chưa chạy |
| Không làm phần ngoài tuần 7 | Đạt | Không migration/dependency mới; không analytics/camera–topic/xóa account/CMS/bảng xếp hạng/PRACTICE UI |

## Commits theo bước và phần còn lại

| Bước | Commit | Android / backend / PostgreSQL | Lint / builds |
| --- | --- | --- | --- |
| 0 — hợp đồng/baseline/golden | 67c6368 | 94 / 26 / 45 | 38, admin/APK đạt |
| 1 — shared typed grader/wire | 7d979e7 | 98 / 32 / 46 | 38, admin/APK đạt |
| 2 — seed/server/Room raw input | 6af3585 | 108 / 37 / 52 | 38, admin/APK đạt |
| 3 — UI/TTS/feedback/luyện sai | Commit chứa báo cáo này | 126 / 37 / 52 | 38, admin/APK đạt |

Chi tiết từng lượt tại [bước 0](week7-step0.md), [bước 1](week7-step1.md), [bước 2](week7-step2.md). [Ma trận tuần 6](week6-step7.md) giữ nguyên các hàng chỉ Robolectric là đạt một phần.

Triển khai kỹ thuật phạm vi tuần 7 đã xong; **chưa nghiệm thu thiết bị**. Instrumentation, chất lượng âm thanh/voice installer, process death/vuốt tắt/reboot, nâng cấp APK tuần5→6→7 không uninstall và hai account trên máy thật chưa được kiểm chứng. Không lấy build/Robolectric làm bằng chứng cho các ca đó.

Nội dung vẫn DEMO chưa được rà soát độc lập, REVIEWED phải hoàn tất trước tuần 8; fixture lớn chỉ kiểm format. Preflight chưa chạy trên database mục tiêu. Answer key offline không chống gian lận tuyệt đối. Admin delete FK backend trả409 nhưng admin chưa toast riêng. Force-stop có thể ngăn worker đến khi mở app lại. Raw input khác với cùng UUID vẫn409 dù normalized đúng; giữ payload, không sửa queue để vượt lỗi.

Giả định chờ xác nhận tại [week7-plan.md](../product/week7-plan.md): chuẩn hóa hữu hạn, fallback cùng câu, network voice có nhãn khi online, luyện sai cục bộ, weight1 chỉ nội dung mới và fixture mới tách khỏi legacy. Không coi giả định là rà soát nội dung/thiết bị.

Lệnh seed dev, backup database, build/install-r APK và bảng test tay tại [week7-testing.md](../product/week7-testing.md). Không bắt đầu tuần 8, không merge main hoặc PR tuần 6.
