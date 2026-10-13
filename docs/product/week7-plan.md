# Tuần 7 — Hợp đồng, bước nhỏ và giả định

Ngày 13/10/2026, nhánh `feature/question-types-tts` từ `feature/offline-lessons` tại `c339c4e`. PR tuần 6 [#4](https://github.com/TrinhQuocDat542005/LingoLens/pull/4) mở, không merge. Chỉ ba dạng bài/TTS/fallback en-US trên chặng ba bài; không PRACTICE UI, analytics, camera–chủ đề, xóa account, CMS/bảng xếp hạng hoặc hạ tầng mới.

## Hợp đồng trước code

- MEANING_CHOICE và LISTEN_CHOICE dùng options/optionKey; FILL_GAP dùng input raw trong cùng trường `optionKey` của payload cũ. JSON attempt/hash/UUID không đổi cấu trúc; normalized input chỉ dùng để chấm, không ghi đè raw input hoặc frozen payload. Cùng UUID với raw payload khác vẫn bị từ chối, dù hai input chuẩn hóa về cùng đáp án.
- Grader thuần shared có phép chấm một câu để feedback và chấm toàn bài dùng cùng phép đó. Ngưỡng nguyên 70%, phần trăm floor; hiển thị số đúng/tổng. Nội dung mới 10 câu/weight1; grader/vector/revision lịch sử có trọng số khác vẫn hợp lệ.
- FILL_GAP: trim/gộp whitespace/lowercase Locale.ROOT; chỉ acceptedAnswers trong answer_key sau cùng phép chuẩn hóa. Không fuzzy, không tự bỏ dấu câu. Không cho UI nộp input trống; dữ liệu đầu vào/biến thể có giới hạn độ dài theo validation seed/pack.
- LISTEN_CHOICE: tối đa hai câu trong mỗi bài 10 câu. speechText và fallbackPrompt bắt buộc với câu nghe. Fallback được đánh dấu, giữ nguyên question ID/options/answer key/trọng số/mẫu số; không ngầm đúng hoặc bỏ câu. speech_text và fallback/variants JSONB đã có chỗ lưu, không cần cột mới.
- Pack giữ schemaVersion1, thêm fields **nullable/optional** theo loại: speechText/fallbackPrompt và acceptedAnswers. Pack cũ thiếu các field vẫn đọc được; không dựa vào Kotlin constructor defaults khi Gson đọc field vắng. Manifest không đổi, locale chỉ trong LessonPack.
- Backend có codec riêng cho pack, dựa mapper hiện có và omit field mới null; không sửa mapper global/hash attempt. Golden bytes/size/SHA của cả ba pack tuần 6 được lấy **trước khi đổi DTO**, sau đó cố định trong test để bảo vệ checksum của revision cũ, kể cả archived.
- DEMO mới chỉ dev/test, pack/revision/question UUID mới; giữ stable course/unit/lesson IDs để enrollment ACTIVE/XP cũ còn đúng. Không sửa demo-week5.json, receipt/checksum/gói đã nhập hoặc nội dung published. Runner tạo DRAFT children, archive bản trước, publish mới cùng transaction; retry pack nhập một lần. Word collisions phải tương thích như trước.
- Room v4 không đổi schema: JSON pack/answers/payload chứa dữ liệu mới. Lưu đáp án/vị trí vẫn transaction ngắn; submit và enqueue atomic; account isolation/logout giữ queue; retry và server regrade bằng revision đã đóng băng giữ nguyên.
- TTS dùng engine trên máy, chọn theo Voice.locale en-US và isNetworkConnectionRequired; ưu tiên voice offline. Không chỉ dựa setLanguage; thiếu voice/phát lỗi hướng dẫn cài voice và cho fallback rõ ràng. Không thêm cloud TTS/audio. Stop khi màn hình xuống background; shutdown khi rời màn hình/đổi account.
- Feedback đúng/sai và explanation sau mỗi câu dùng grader shared. Luyện lại câu sai **cục bộ sau nộp**: giữ nguyên kết quả chính thức/tạm đã nộp, UUID/payload/outbox/progress/XP; không gọi start/answer/submit hoặc tạo PRACTICE request. Hiển thị “Luyện lại — không thay kết quả đã nộp”.

## Các bước / test viết trước

| Bước | Phạm vi | Kiểm chứng trước commit |
| --- | --- | --- |
| 0 | Tài liệu hợp đồng/giả định, baseline và golden pack tuần 6 | PostgreSQL hiện có xuất bytes gốc, provenance c339c4e; đủ regression/baseline, không thay production |
| 1 | Shared typed grading/feedback, DTO optional, codec wire tương thích và vectors | Android/backend cùng typed vectors; chuẩn hóa có giới hạn, variants/typo, 7/10 pass6/10fail; vector cũ giữ nguyên; golden bytes/hash cũ không đổi |
| 2 | Seed/pack/backend ba dạng, revision mới trên chặng cũ và durable fill answers | PostgreSQL seed upgrade giữ old attempts/answers/progress/XP/enrollment, archived old grading; schema/type/fallback/weight validation; UUID raw payload conflict; Room reopen/freeze raw input |
| 3 | TTS controller/fallback và UI ba dạng, feedback/luyện câu sai | Test voice selection/engine error/lifecycle bằng fake, không fake-complete; UI model durable fill/listen/fallback, luyện sai không sửa hàng dữ liệu cũ; APK và manual guide |

Mỗi bước một commit riêng, test quan trọng viết trước code; sau mỗi bước đủ Android/backend/postgresTest, lint mới gate baseline38, admin build, APK. Không tự merge main hoặc PR tuần 6. Dừng ngay khi test fail/lint tăng, hợp đồng cần đổi, migration có nguy cơ phá dữ liệu hoặc cần thư viện/dịch vụ ngoài phạm vi. Chỉ cộng thêm migration nếu thật sự cần và báo cáo trước commit; kế hoạch hiện tại không cần migration.

## Giả định chờ chủ dự án xác nhận

Các mặc định dưới đây theo prompt được phép chọn để đi tiếp, chưa phải xác nhận chất lượng trên thiết bị:

1. Chuẩn hóa whitespace Unicode/hoa thường; accepted variants hữu hạn, không fuzzy/punctuation stripping.
2. Fallback là prompt không cần nghe cho cùng câu, không đổi denominator hoặc kết quả.
3. Nếu chỉ có en-US voice cần mạng: có kết nối có thể dùng với nhãn cần mạng; offline chuyển fallback/cài voice. Có offline voice thì luôn ưu tiên nó.
4. Luyện lại sai sau submit chỉ là luyện cục bộ, không sửa frozen attempt/kết quả hoặc thưởng XP. Feedback trước submit dựa đáp án hiện tại; kết quả nộp vẫn được server chấm lại.
5. Weight1/10 câu áp dụng seed tuần 7 mới; không áp lại vào các revision/vector/attempts lịch sử.
6. Mở rộng họ fixture lớn bằng large-packs-week7.json để phủ ba dạng và giữ large-packs.json làm fixture tương thích cũ. DEMO không tự đổi thành REVIEWED.

## Rủi ro còn lại

Field JSON mới null có thể đổi checksum nếu serializer tự xuất null; golden bytes/codec phải chặn. Nhập gói mới cùng stable IDs cần kiểm metadata và rollback atomic; không lách ACTIVE enrollment bằng xóa dữ liệu. Input raw và chuẩn hóa phải tách để idempotency không đổi. TTS voice availability/lifecycle/âm thanh thật và process death/reboot/install APK trên thiết bị chưa kiểm chứng; fake/Robolectric chỉ đạt một phần. Nội dung mới vẫn DEMO chưa rà soát độc lập; REVIEWED gate trước tuần8. Preflight chưa chạy trên database mục tiêu.
