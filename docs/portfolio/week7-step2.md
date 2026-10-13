# Tuần 7 — Bước 2: nội dung ba dạng và lưu đáp án bền vững

Ngày 13/10/2026, nhánh `feature/question-types-tts`. Tests được viết trước implementation. Không migration, đổi Room schema, dependency hoặc dịch vụ mới; V1–V13, Room exports v1–v4 và seed tuần 5 giữ nguyên.

- DEMO tuần 7 dùng cùng course/topic/unit/lesson IDs, pack/revision/question IDs mới, mỗi bài 4 chọn nghĩa/2 nghe/4 điền, 6 từ, 10 câu weight1. Seed schema/validator thêm speech/fallback/approved variants; chỉ dev/test, production guard giữ nguyên. Import explicit: `seedDemo -PseedFile=src/main/resources/content/demo-week7.json`; default vẫn tuần 5.
- Importer kiểm metadata cũ, khóa course/pack, tạo DRAFT và children rồi archive status của revision cũ, publish mới và receipt cùng transaction. Receipt cùng checksum no-op; revisionNo mới phải tăng, không cho seed cũ quay lại manifest trước. Không sửa snapshot/nội dung published.
- Backend pack xuất typed metadata bằng codec riêng; server chấm cùng shared grader và giữ raw answer/hash. Raw tương đương sau chuẩn hóa nhưng khác payload với UUID cũ vẫn 409. Input quá 500 hoặc trống khi nộp bị từ chối.
- Room v4 lưu raw fill kể cả draft trống, UUID/revision/vị trí không đổi qua reopen. Submit kiểm đủ đáp án và freeze/enqueue cùng transaction; không normalize dữ liệu đã lưu. Cache/session cũ giữ nguyên khi có pack mới.

## Bằng chứng

`SeedQuestionTypesTests` (5 unit): legacy không đổi, mixed10/weight1, fallback/speech bắt buộc, variants/options, giới hạn nghe và câu hỏi.

`Week7QuestionPostgresTests` (6 PostgreSQL thật): so từng dòng attempts/answers/progress/XP/enrollment và receipt/questions/pack bytes cũ khi nhập revision mới; ARCHIVED vẫn chấm và không thưởng lại; 7/10 đạt, 6/10 trượt, retry/raw conflict; invalid input không ghi dở; large fixture roundtrip; metadata rollback/concurrent seed nhập một lần; fresh tuần7 từ chối seed tuần5 giữ manifest/receipt/bytes. Override đủ URL/user/password/driver và assert JDBC PostgreSQL.

`TypedContentPackRepositoryTest` (3 Robolectric): ba pack mixed lớn; 18 nhóm metadata lỗi dù checksum đúng không ghi Room; legacy weighted pack vẫn hợp lệ.

`TypedLessonSessionRepositoryTest` (7 Robolectric): raw/UUID/revision/vị trí reopen; blank draft không submit; payload/hash/freeze/retry; rollback freeze/outbox; account isolation; manifest mới giữ phiên/grade cũ; invalid length/key/question/position không ghi dở.

## Gate mới, UTC+07

| Phần | Kết quả | Giờ |
| --- | --- | --- |
| Android unit/Robolectric | 108/108, 0 failure/error/skipped | 20:21:34–20:25:50 |
| Backend unit/H2 | 37/37, 0 failure/error/skipped | Backend 20:21:34–20:26:30 |
| PostgreSQL Testcontainers | 52/52, 0 failure/error/skipped/initialization error | Trong cùng lệnh backend sau unit |
| Lint mới | 0 errors / 38 warnings / 0 new | XML 20:25:48, Assert-LintBaseline đạt |
| Admin | Build đạt | 20:21:36–20:21:49 |
| APK debug | Build đạt, đóng gói lại | APK 20:25:40 |

Bước 3 còn UI ba dạng, TTS/fallback, feedback và luyện sai cục bộ. Những gì chỉ có Room/Robolectric vẫn **đạt một phần – chưa kiểm chứng trên thiết bị**. Fixture lớn kiểm format, không phải nội dung đã rà soát. DEMO cần REVIEWED trước tuần 8; preflight database mục tiêu chưa chạy. Không merge main.
