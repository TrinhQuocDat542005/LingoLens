# Tuần 7 — Bước 1: chấm ba dạng và pack tương thích

Ngày 13/10/2026, nhánh `feature/question-types-tts`. Tests/vector được viết trước implementation. Không migration hoặc thư viện/dịch vụ mới; chưa nối UI ba dạng/TTS ở bước này.

- Shared grader thêm MEANING_CHOICE/LISTEN_CHOICE/FILL_GAP và chấm từng câu. Điền từ chuẩn hóa whitespace Unicode (kể cả đầu/cuối), Locale.ROOT; chỉ approved variants, không fuzzy hoặc bỏ dấu câu. Giữ raw answer trong marks/payload. Blank/duplicate/foreign answers bị từ chối; threshold 70% dùng số nguyên trước floor phần trăm.
- Android/backend chạy cùng chín typed vectors, so aggregate và marks của shared; sáu vectors lịch sử giữ nguyên. `TypedPackGraderTest` (4), `TypedGraderVectorTests` (4) bảo vệ variants/typo/locale/7–6 trên 10 và invalid input.
- DTO chỉ thêm fields nullable/optional. `PackWireCodec` dùng NON_NULL trên bản copy của mapper dành riêng cho pack; global mapper/hash attempt không đổi. `PackWireCodecTests` (2) so JSON legacy đầy đủ và roundtrip typed metadata.
- `ContentPackPostgresTests.week6 published and archived pack bytes equal frozen pre week7 golden` so exact bytes/size/SHA của cả ba revision với fixture lấy tại `c339c4e`; thử PUBLISHED và ARCHIVED. Test fixture lớn cũ vẫn so toàn bộ JSON qua codec production, không lọc field hoặc nới assertion.

## Gate mới, UTC+07

| Phần | Kết quả | Giờ |
| --- | --- | --- |
| Android unit/Robolectric | 98/98, 0 failure/error/skipped | 15:10:16–15:13:00 |
| Backend unit/H2 | 32/32, 0 failure/error/skipped | Backend 15:10:19–15:13:39 |
| PostgreSQL Testcontainers | 46/46, 0 failure/error/skipped/initialization error | Cùng lệnh backend sau unit |
| Lint mới | 0 errors / 38 warnings / 0 new | XML 15:12:58; Assert-LintBaseline đạt |
| Admin | Build đạt, không tính là test | 15:10:18–15:10:26 |
| APK debug | Build đạt, đóng gói lại | APK 15:12:59 |

Bước 2 còn seed/server mapping và Room lưu raw fill; bước 3 còn UI/TTS/fallback/luyện sai. Test thiết bị, chất lượng giọng thật và nâng cấp APK chưa kiểm chứng; không đánh dấu đạt cho những phần này. Không merge main hoặc PR tuần 6.
