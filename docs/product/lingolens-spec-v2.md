# LingoLens — Đặc tả sản phẩm và kiến trúc v2

**Trạng thái:** chủ dự án đã cho phép bắt đầu **chỉ tuần 5**. Hiện chỉ thực hiện bước 0 sau khi cập nhật hợp đồng; kết thúc bước 0 và mỗi bước tiếp theo phải báo cáo rồi chờ chỉ thị **“tiếp tục”**. Tuần 6 trở đi chưa được phép triển khai.

**Căn cứ:** đặc tả ban đầu trong cuộc trò chuyện, đối chiếu migration V1–V8, Room v3 và quyết định **1A, 2A, 3A, 4A, 5A, 6A**, cùng các bổ sung của chủ dự án. Tài liệu này là đặc tả đích, không phải báo cáo tính năng đã triển khai.

## 0. Nhật ký thay đổi so với đặc tả ban đầu

Ký hiệu **[THAY ĐỔI]** là sửa quyết định cũ; **[BỔ SUNG]** là quy tắc trước đây còn thiếu. Phần không có ký hiệu giữ định hướng cũ hoặc mô tả hiện trạng đã đối chiếu.

| Mục | Quyết định trước | Quyết định v2 |
| --- | --- | --- |
| Kế hoạch tuần 5–9 | Làm catalog/curriculum/CMS trước vòng học | **[THAY ĐỔI]** Làm một chặng ba bài theo lát cắt dọc; CMS bài học làm sau |
| Nhập nội dung | Sớm có quản trị bài học | **[THAY ĐỔI]** File JSON được kiểm tra và seed có kiểm soát |
| Thiết lập | Phân tán users và learner_settings | **[THAY ĐỔI]** Nguồn chính là learner_settings; API chuyển tiếp tương thích |
| Từ đa nghĩa | Chưa chốt thời điểm tách | **[THAY ĐỔI — 1A]** Giữ một mục từ/word trong MVP |
| Version khóa học | Enrollment ghim course version | **[THAY ĐỔI — 2A]** Bỏ course version; giữ lesson revision bất biến |
| Snapshot nội dung bài | Snapshot đầy đủ mục từ | **[THAY ĐỔI — 3A]** Giữ snapshot tối thiểu các trường bài thực sự dùng |
| Mở bài | Đạt 80% | **[THAY ĐỔI — 4A]** Đạt ít nhất 70% |
| Streak | Hoàn thành bài/ôn ≥5 từ, xác nhận trong 72 giờ | **[THAY ĐỔI — 5A]** Giữ cách tính hiện tại; tuần 9 xem lại và thêm hoàn thành bài; chưa có hạn 72 giờ |
| Analytics | Chưa chốt | **[BỔ SUNG — 6A]** Opt-in, PostgreSQL, sự kiện thô tối đa 90 ngày |
| Tuần 7 | Bài tập, TTS, analytics, camera–chủ đề | **[THAY ĐỔI]** Chỉ ba dạng bài và TTS/fallback; phần còn lại tuần 8–9 |
| Nội dung | Ước lượng gộp, thiếu đệm | **[BỔ SUNG]** Workstream riêng, có đệm; người rà soát khác người biên soạn |
| Phát âm | TTS en-US nhưng chưa có chuẩn nội dung | **[BỔ SUNG]** Anh–Mỹ; rà soát phiên âm seed hiện có |
| Xóa tài khoản | Chưa có luồng hoàn chỉnh | **[BỔ SUNG]** Trong app và trang web là điều kiện bắt buộc trước phát hành |
| Xóa ảnh | Dễ bị hiểu là bảo đảm 24 giờ | **[THAY ĐỔI]** Mô tả đúng dọn cache theo thao tác, không cam kết mốc 24 giờ |

## 1. Hiện trạng đã đối chiếu

- Backend V1–V8 có users/roles/token, vocabulary, recognition/report/audit, learning_entries/sync_receipts và review_cards/review_events.
- Room v3 có SavedWordEntity, LearningEvent, VocabularyCache, OutboxOperation, SyncAccount, ReviewCard, ReviewEvent, ReviewSession. Các entity học/ôn không khai báo Room ForeignKey.
- Chưa có learner_settings, topic, course, lesson/revision, question/attempt, XP hoặc analytics trong phần mã nguồn đã đối chiếu.
- V5 seed 12 từ, đều mang mức B1; đây không phải bộ nội dung nhập môn đã rà soát.
- UpdateProfileRequest hiện chỉ chấp nhận B1/B2. Một số ràng buộc NOT NULL trong JPA không có tương ứng trong migration V1.
- Sổ tay, cache và review đang dùng chuỗi word làm khóa. Lookup trả một mục từ. Admin hiện có thể sửa/xóa vocabulary.
- Streak trên Home/Stats dựa vào ngày first-save và review, theo timezone thiết bị; một hoạt động có thể tạo ngày học. Không có điều kiện ôn 5 từ hay giới hạn trễ 72 giờ.
- Backend review nhận timestamp dương, không vượt tương lai 24 giờ; V8 chưa có received_at cho review_events.
- Ảnh ở cache captured_images. Các API nhận diện đã kiểm tra gửi metadata, không gửi ảnh. LearningRepository bỏ imagePath/audioPath khỏi snapshot lưu từ.
- Cơ chế dọn ảnh cũ hơn 24 giờ chạy khi tạo ảnh mới; một số thao tác thoát/chụp lại xóa ảnh. Không có bảo đảm xóa mọi ảnh đúng sau 24 giờ.
- Có TTS kiểm tra ngôn ngữ, xử lý lỗi và lifecycle; chưa chọn voice offline theo thuộc tính của voice. Chưa có luồng xóa tài khoản trong app/web.

Đây là đối chiếu mã/schema trong repository, không phải xác nhận cấu hình database production, log hosting, hành vi toàn bộ SDK hoặc kết quả thử trên thiết bị thật.

## 2. Sản phẩm, MVP và tech stack

LingoLens là app Android học tiếng Anh theo lộ trình/chủ đề, kết hợp luyện tập và ôn cách quãng. Camera bổ sung từ trong đời sống vào việc học.

### 2.1. Đúng năm nhóm tính năng MVP

| Nhóm | Phạm vi |
| --- | --- |
| Tài khoản và mục tiêu | Auth hiện có; onboarding mục tiêu, mức nội dung và thiết lập học |
| Lộ trình và chủ đề | Một lộ trình nhập môn, sáu chủ đề, các chặng/bài có thứ tự |
| Bài học và bài luyện | Từ, mẫu câu, chọn nghĩa, nghe chọn từ, điền từ, phản hồi và tổng kết |
| Sổ tay và review | Lưu từ, nội dung đã tải, flashcard, lịch ôn, phiên tiếp tục và sync |
| Tiến độ và động lực | Tiến độ bài/chặng, mục tiêu, XP và streak theo chính sách mục 5 |

Mục tiêu toàn MVP: 18 bài, 90–120 từ độc nhất, 150–180 câu hỏi; mỗi bài khoảng 8–10 câu, 5–8 từ và 1–2 mẫu câu. Không nhân 18 × số từ để tuyên bố số từ độc nhất. Mức A1 là định hướng biên soạn, không là chứng nhận năng lực.

Giữ camera và admin vocabulary hiện có. Hoãn CMS bài học đầy đủ, bảng xếp hạng/thử thách/huy hiệu, trò chơi bổ sung và chức năng AI đến sau MVP. Không thêm các tính năng đó vào tuần 5–9.

### 2.2. Stack giữ nguyên

| Thành phần | Công nghệ |
| --- | --- |
| Android/UI | Kotlin, Jetpack Compose, Material 3, ViewModel/StateFlow |
| Dữ liệu Android | Room, DataStore, Keystore; Repository |
| Networking/sync | Retrofit/OkHttp, WorkManager, durable outbox |
| Backend | Kotlin, Spring Boot, Java 21; modular monolith |
| Database | PostgreSQL, Flyway |
| Auth | Spring Security, JWT, refresh token xoay vòng |
| Admin | React, TypeScript, Vite; component/CSS tokens hiện có |
| Kiểm thử/triển khai | JUnit, Robolectric, Compose UI, Testcontainers, Docker Compose, GitHub Actions |

Không thêm microservices, broker, kho analytics hay nhà cung cấp TTS cloud. shared/ chỉ chứa thuật toán thuần; quyền xác nhận kết quả/XP thuộc backend.

## 3. Schema đích và kế hoạch chuyển đổi

**Chưa sửa migration.** Các bảng/trường dưới đây chỉ là hợp đồng để triển khai sau. PK = khóa chính; FK = khóa ngoại; UQ = duy nhất; NN = NOT NULL; NULL = được phép thiếu.

### 3.1. [THAY ĐỔI] Thiết lập và bảng users

users tiếp tục giữ id, email duy nhất, password hash, name, enabled, created_at, updated_at; roles/user_roles quản lý phân quyền. refresh_tokens giữ cấu trúc hiện có. target_level/daily_goal/streak_days là cột legacy trong thời gian chuyển tiếp, không lập tức xóa.

daily_goal và daily_review_goal không trùng nghĩa; target_level và learning_goal cũng không trùng nghĩa. Gom nơi lưu, không nhập chúng thành một trường.

| Field của learner_settings | Data Type | Constraints/ý nghĩa |
| --- | --- | --- |
| user_id | BIGINT | PK, FK → users.id |
| target_level | VARCHAR(10) | NN; mức nội dung, hỗ trợ A1 và các giá trị cũ B1/B2 |
| learning_goal | VARCHAR(30) | NN; mặc định UNSET đến khi người dùng chọn |
| daily_new_word_goal | INT | NN, CHECK 1–50; kế thừa users.daily_goal |
| daily_review_goal | SMALLINT | NN, CHECK 5–100 |
| timezone | VARCHAR(64) | NULL trước onboarding; sau đó IANA timezone được xác thực |
| onboarding_completed_at | TIMESTAMPTZ | NULL |
| updated_at | TIMESTAMPTZ | NN |

Kế hoạch expand → chuyển ứng dụng → contract:

1. Tạo bảng mới ở migration tương lai; báo cáo dữ liệu null/không hợp lệ trước backfill.
2. Sao chép target_level/daily_goal nguyên giá trị hợp lệ. Không đổi B1/B2 thành A1 để phù hợp nội dung mới.
3. Hợp đồng tuần 5 đã duyệt: tài khoản mới A1, daily_new_word_goal = 5, daily_review_goal = 10, learning_goal = UNSET, timezone/onboarding_completed_at = NULL. Người dùng cũ giữ nguyên target_level/daily_goal hợp lệ; các trường mới dùng 10/UNSET/NULL tương ứng, không tự đổi B1/B2 thành A1.
4. Backend dùng learner_settings làm nguồn chính, tiếp tục trả DTO profile cũ qua adapter. Những client cũ cập nhật profile phải đi qua cùng nguồn ghi.
5. Không để users và learner_settings là hai nguồn độc lập; nếu cần mirror legacy thì cập nhật cùng transaction, có thời hạn loại bỏ.
6. Chuyển Android/cache profile và admin DTO, kiểm tra phiên cũ và nâng cấp.
7. Chỉ xóa cột legacy sau khi không còn consumer. Việc bỏ streak_days tách khỏi quyết định thay đổi cách tính streak.

Không tự áp timezone mới lên lịch sử trước thời điểm chuyển chính sách streak.

### 3.2. [THAY ĐỔI — 1A] Vocabulary và topic

Giữ vocabularies.word UNIQUE và một mục từ cho mỗi word trong MVP. Giữ các trường hiện có: id BIGINT PK; word/translation/part_of_speech/definition/level/is_published NN; phonetic/example_sentence/example_sentence_b2/synonyms NULL; created_at/updated_at TIMESTAMP NN theo V4.

Chưa thêm mô hình nhiều nghĩa hoặc chuyển khóa Sổ tay/review/cache. Nội dung không dùng các nghĩa khác nhau của cùng word như hai mục độc lập. Khi có lesson tham chiếu vocabulary, không cho admin hard-delete mục đang được tham chiếu; unpublish không làm mất revision cũ.

| Field của topics | Data Type | Constraints |
| --- | --- | --- |
| id | UUID | PK |
| slug | VARCHAR(80) | NN, UQ |
| title | VARCHAR(120) | NN |
| description | TEXT | NN |
| icon_key | VARCHAR(50) | NN |
| display_order | INT | NN, CHECK ≥ 0 |
| is_published | BOOLEAN | NN |
| created_at | TIMESTAMPTZ | NN |
| updated_at | TIMESTAMPTZ | NN |

| Field của topic_vocabularies | Data Type | Constraints |
| --- | --- | --- |
| topic_id | UUID | PK ghép, FK → topics.id |
| vocabulary_id | BIGINT | PK ghép, FK → vocabularies.id |
| display_order | INT | NN, CHECK ≥ 0 |

### 3.3. [THAY ĐỔI — 2A/3A] Khóa học và lesson revision

| Field của courses | Data Type | Constraints |
| --- | --- | --- |
| id | UUID | PK |
| code | VARCHAR(60) | NN, UQ; không còn version |
| title | VARCHAR(150) | NN |
| description | TEXT | NN |
| level | VARCHAR(10) | NN |
| status | VARCHAR(20) | NN; DRAFT/PUBLISHED/ARCHIVED |
| published_at | TIMESTAMPTZ | NULL |
| created_at | TIMESTAMPTZ | NN |

| Field của course_units | Data Type | Constraints |
| --- | --- | --- |
| id | UUID | PK |
| course_id | UUID | NN, FK → courses.id |
| topic_id | UUID | NN, FK → topics.id |
| title | VARCHAR(150) | NN |
| position | INT | NN, CHECK ≥ 1; UQ(course_id, position) |

| Field của lessons | Data Type | Constraints |
| --- | --- | --- |
| id | UUID | PK |
| unit_id | UUID | NN, FK → course_units.id |
| title | VARCHAR(150) | NN |
| position | INT | NN, CHECK ≥ 1; UQ(unit_id, position) |
| estimated_minutes | SMALLINT | NN, CHECK 1–30 |

| Field của lesson_revisions | Data Type | Constraints |
| --- | --- | --- |
| id | UUID | PK |
| lesson_id | UUID | NN, FK → lessons.id |
| revision_no | INT | NN, CHECK ≥ 1; UQ(lesson_id, revision_no) |
| introduction | TEXT | NN |
| sentence_patterns | JSONB | NN; kiểm tra cấu trúc |
| status | VARCHAR(20) | NN; DRAFT/PUBLISHED/ARCHIVED |
| published_at | TIMESTAMPTZ | NULL |
| created_at | TIMESTAMPTZ | NN |

| Field của lesson_vocabularies | Data Type | Constraints |
| --- | --- | --- |
| lesson_revision_id | UUID | PK ghép, FK → lesson_revisions.id |
| vocabulary_id | BIGINT | PK ghép, FK → vocabularies.id |
| position | INT | NN, CHECK ≥ 1; UQ(lesson_revision_id, position) |
| content_snapshot | JSONB | NN; snapshot tối thiểu, bất biến khi xuất bản |

Snapshot chứa word, translation, phonetic, part_of_speech, definition và example thực sự dùng. Không sao chép field quản trị, media path hoặc nội dung không xuất hiện trong bài. Gói bài dùng snapshot này thay vì đọc vocabulary mới nhất để hiển thị.

Revision đã xuất bản không sửa; revision archived vẫn phục vụ attempt/gói offline cũ. Snapshot Sổ tay của tuần 2–4 vẫn giữ nguyên.

Không có course version: enrollment không tái dựng chính xác cấu trúc khóa học ở thời điểm đăng ký. ID lesson/unit ổn định; không xóa/di chuyển lesson đã có kết quả trong MVP. Cho phép bổ sung bài cuối chặng; tiến độ hiển thị theo cấu trúc hiện tại nên mẫu số có thể tăng, phải giải thích trong UI. Muốn tái cấu trúc lớn cần đặc tả riêng sau MVP.

### 3.4. Câu hỏi, attempt và tiến độ

| Field của questions | Data Type | Constraints |
| --- | --- | --- |
| id | UUID | PK |
| lesson_revision_id | UUID | NN, FK → lesson_revisions.id; UQ(id, lesson_revision_id) |
| type | VARCHAR(20) | NN; MEANING_CHOICE/LISTEN_CHOICE/FILL_GAP |
| prompt | TEXT | NN |
| speech_text | TEXT | NULL; bắt buộc với câu nghe |
| explanation | TEXT | NN |
| answer_key | JSONB | NN; cấu trúc theo loại câu hỏi |
| position | INT | NN, CHECK ≥ 1; UQ(lesson_revision_id, position) |
| weight | SMALLINT | NN, CHECK 1–10; bộ MVP dùng trọng số bằng nhau |

| Field của question_options | Data Type | Constraints |
| --- | --- | --- |
| question_id | UUID | PK ghép, FK → questions.id |
| option_key | VARCHAR(8) | PK ghép |
| label | TEXT | NN |
| position | SMALLINT | NN, CHECK ≥ 1; UQ(question_id, position) |

| Field của lesson_attempts | Data Type | Constraints |
| --- | --- | --- |
| id | UUID | PK; không đổi khi retry |
| user_id | BIGINT | NN, FK → users.id |
| lesson_revision_id | UUID | NN, FK → lesson_revisions.id; UQ(id, lesson_revision_id) |
| mode | VARCHAR(20) | NN; LESSON/PRACTICE |
| score_percent | SMALLINT | NN, CHECK 0–100; phần trăm trọng số làm tròn xuống, chỉ để hiển thị |
| passed | BOOLEAN | NN; backend tính từ tỷ lệ chưa làm tròn |
| submitted_offline | BOOLEAN | NN |
| client_completed_at | TIMESTAMPTZ | NN; thiết bị khai báo, không là trusted clock |
| received_at | TIMESTAMPTZ | NN; máy chủ ghi |
| request_hash | VARCHAR(64) | NN; phát hiện UUID tái dùng với payload khác |

| Field của attempt_answers | Data Type | Constraints |
| --- | --- | --- |
| attempt_id | UUID | PK ghép |
| question_id | UUID | PK ghép |
| lesson_revision_id | UUID | NN |
| submitted_answer | JSONB | NN |
| is_correct | BOOLEAN | NN; backend tính |
| awarded_weight | SMALLINT | NN, CHECK ≥ 0 |

FK ghép (attempt_id, lesson_revision_id) → lesson_attempts và (question_id, lesson_revision_id) → questions. Backend kiểm tra tập câu hỏi đầy đủ, option hợp lệ và không có câu trả lời trùng.

| Field của enrollments | Data Type | Constraints |
| --- | --- | --- |
| user_id | BIGINT | PK ghép, FK → users.id |
| course_id | UUID | PK ghép, FK → courses.id; không ghim course version |
| status | VARCHAR(20) | NN; ACTIVE/PAUSED/COMPLETED |
| enrolled_at | TIMESTAMPTZ | NN |
| completed_at | TIMESTAMPTZ | NULL |

| Field của lesson_progress | Data Type | Constraints |
| --- | --- | --- |
| user_id | BIGINT | PK ghép, FK → users.id |
| lesson_id | UUID | PK ghép, FK → lessons.id |
| best_score_percent | SMALLINT | NN, CHECK 0–100 |
| first_passed_at | TIMESTAMPTZ | NULL |
| last_attempt_id | UUID | NULL, FK → lesson_attempts.id |
| updated_at | TIMESTAMPTZ | NN |

Service xác nhận attempt thuộc đúng user/lesson. Revision thay đổi không tự cấp lại thưởng cho lesson.

### 3.5. Dữ liệu tuần 1–4 giữ lại

| Bảng | Hợp đồng hiện tại và giới hạn |
| --- | --- |
| learning_entries | PK(user_id, word); content TEXT JSON, saved_at/first_learned_at epoch ms nullable; deleted BOOLEAN NN; FK user_id |
| review_cards | PK(user_id, word); due_at epoch ms NN; interval_days/ease/repetitions/lapses/stage NN; last_reviewed_at nullable; FK user_id |
| review_events | PK(user_id, operation_id); word/grade/reviewed_at NN; epoch ms; FK user_id |
| sync_receipts | PK(user_id, operation_id); request_hash/operation_kind NN; remote_id BIGINT NULL; created_at TIMESTAMPTZ NN |

Bổ sung CHECK và FK ghép cho các bảng này là ràng buộc mục tiêu, không mô tả là đã có trong V8. Chỉ thêm sau kiểm tra dữ liệu và tác động xóa tài khoản. received_at cho review_events là bổ sung tương lai nếu phép đo cần; không suy diễn timestamp nhận cho mọi lượt ôn cũ. Attempt UUID không ép vào sync_receipts.remote_id BIGINT.

Room hiện có tiếp tục lưu từ/cache/card/event/session/outbox. Những cấu trúc mới cần có ContentPack (revisionId PK, manifest/checksum/downloadedAt/complete), LessonSession (id PK, accountId/revisionId/mode/order/position/answers/phase/start/update) và hàng đợi analytics riêng. Chi tiết Room mới và migration được thiết kế khi bắt đầu code; không xóa content pack còn được session/outbox tham chiếu.

### 3.6. XP

| Field của xp_events | Data Type | Constraints |
| --- | --- | --- |
| id | UUID | PK |
| user_id | BIGINT | NN, FK → users.id |
| reason | VARCHAR(30) | NN; MVP: LESSON_FIRST_PASS |
| source_key | VARCHAR(100) | NN; khóa lesson ổn định |
| amount | INT | NN, CHECK > 0 |
| rules_version | SMALLINT | NN, CHECK ≥ 1 |
| awarded_at | TIMESTAMPTZ | NN; máy chủ ghi |

UQ(user_id, reason, source_key). Bản đầu 30 XP cho vượt lesson lần đầu trong mode LESSON. PRACTICE không mở khóa hay nhận thưởng này. Retry/học lại/revision mới không cấp lại. Backend chấm, ghi attempt/progress/XP/receipt cùng transaction.

**[THAY ĐỔI]** Chưa tạo daily_activity làm nguồn streak chính trong MVP. Bảng tổng hợp này được hoãn; tuần 9 bổ sung hoạt động hoàn thành bài vào lịch sử tính streak trước, theo mục 5.

### 3.7. Quan hệ và index bắt buộc

| Quan hệ | Kiểu |
| --- | --- |
| users → learner_settings | 1–0 hoặc 1 trong chuyển tiếp, sau đó 1–1 |
| users ↔ roles; topics ↔ vocabularies | Nhiều–nhiều qua bảng nối |
| courses → units → lessons → revisions | 1–nhiều từng cấp |
| revisions ↔ vocabularies | Nhiều–nhiều, có snapshot tối thiểu |
| revisions → questions → options | 1–nhiều từng cấp |
| users ↔ courses/lessons | Qua enrollment/progress |
| users → attempts → answers | 1–nhiều; question phải cùng revision |
| users → xp_events/analytics_events | 1–nhiều |

**[BỔ SUNG]** PostgreSQL partial unique index:

- lesson_revisions: unique trên lesson_id, chỉ hàng status = PUBLISHED.
- enrollments: unique trên user_id, chỉ hàng status = ACTIVE.
- Khóa và status NOT NULL. Đây là “tối đa một”, không bảo đảm tồn tại một hàng.
- Chuyển trạng thái cũ trước trạng thái mới trong cùng transaction; kiểm thử hai request đồng thời trên PostgreSQL thật.

Index thêm cho thứ tự nội dung, attempts(user_id, received_at), review_cards(user_id, due_at), review_events(user_id, reviewed_at), xp_events(awarded_at, user_id), analytics_events(user_id, occurred_at). Tên/cột index cuối cùng được kiểm tra để không tạo trùng index có sẵn.

Không sửa V1–V8; thêm migration tương lai. Không cấp XP hồi tố khi không có attempt xác minh. Migration phải giữ saved words, events, review state, sessions và outbox cũ; destructive migration bị cấm.

## 4. Luồng học và điều hướng

Năm tab đích: Hôm nay, Học, Ôn tập, Khám phá, Cá nhân. Đây là thay đổi điều hướng đích, chưa triển khai.

| Luồng | Các bước và kết quả |
| --- | --- |
| Bắt đầu | Auth → xác nhận mục tiêu/mức/timezone → enrollment → bài đầu; client cũ giữ profile tương thích |
| Theo lộ trình | Tải revision → học từ/mẫu câu → trả lời → giải thích → nộp → server chấm → progress/XP → bài kế tiếp |
| Chủ đề | Duyệt → tra từ → chọn học/lưu → luyện → ôn; không tự hoàn thành lesson từ thao tác duyệt |
| Offline | Gói hoàn chỉnh → session Room → câu trả lời/outbox atomic → trạng thái tạm → reconnect → cùng UUID xác nhận một lần |
| Review | Due saved words → nhớ → reveal → grade → lịch sử/lịch/outbox → tổng kết; giữ scheduler tuần 4 |

**[THAY ĐỔI — 4A]** Mở bài tiếp theo khi đạt ≥70% tỷ lệ đúng có trọng số, trước làm tròn. Với trọng số bằng nhau: 8 câu cần 6 câu đúng; 10 câu cần 7. Không đạt vẫn giữ câu trả lời và gợi ý ôn lỗi sai. Có thể luyện lại không giới hạn; không phạt XP hoặc khóa ôn bằng “mạng sống”.

Offline (từ tuần 6, chưa triển khai tuần 5) hiển thị điểm/tiến độ tạm, XP chờ xác nhận; có thể đi tiếp trên thiết bị theo kết quả tạm. Không tuyên bố điểm cục bộ là điểm chính thức. Bài chỉ gồm câu nghe chưa làm do thiếu TTS không được ngầm xem như đã vượt.

## 5. [THAY ĐỔI — 5A] Streak, review và thời gian

### 5.1. Chính sách streak và XP review

- Trước khi có tích hợp bài học: giữ first-save/review activity hiện tại, theo timezone thiết bị; không thêm điều kiện ôn 5 từ.
- **Tuần 9 bắt buộc xem lại streak.** Khi có attempts/XP, thêm hoàn thành bài hợp lệ vào tập hoạt động đủ điều kiện trước. Hoàn thành bài không đồng nghĩa đạt 70%; bài phải có đủ câu trả lời và được server chấp nhận, không chỉ có analytics lesson_completed.
- Chỉ thêm ngày từ attempts, không lọc lại hoặc xóa các ngày first-save/review cũ. Không thay bằng users.streak_days.
- **Chưa áp cửa sổ 72 giờ**, cho bài học hoặc ôn; dữ liệu trễ không bị loại chỉ vì trễ hơn 72 giờ.
- Timestamp không hợp lệ vẫn bị xử lý theo validation; giới hạn timestamp tương lai khác với giới hạn đồng bộ trễ.
- Không chuyển sang múi giờ tài khoản để tính lại lịch sử trong tuần 9 nếu chưa có kế hoạch chuyển tiếp được duyệt. timezone lưu trong settings phục vụ onboarding/analytics; streak hiện tại vẫn dùng timezone thiết bị.
- Review không nhận XP trong MVP. Dùng dữ liệu hoàn thành phiên/số từ khác nhau/D1/D7 để xem lại sau MVP; không thay quy tắc thưởng ngay hoặc suy ra năng lực từ tự đánh giá.

### 5.2. Quy ước thời gian duy nhất

| Phạm vi | Quy ước |
| --- | --- |
| PostgreSQL trường mới | TIMESTAMPTZ; backend dùng Instant |
| Room, shared scheduler, sync learning/review hiện tại | Epoch milliseconds Long/BIGINT; không dùng epoch seconds |
| Chuyển đổi | Mapper tại biên API/persistence; không tự đổi trong Composable |
| API mới cho timestamp lesson/analytics | ISO-8601 UTC cho Instant; mapping sang epoch ms tại Android; API sync cũ giữ kiểu số |
| Timestamp do client khai báo | Giữ riêng với received_at; không coi là trusted clock |
| TIMESTAMP legacy | Giữ kiểu hiện tại; chỉ chuyển sau kiểm tra timezone ghi thực tế; không đoán offset để backfill |
| Streak/biểu đồ học hiện tại | Local calendar theo ZoneId.systemDefault(); giữ hành vi cũ, ghi rõ khác thiết bị có thể khác ngày |
| D1/D7 analytics | Timezone tại app_open đầu tiên của tài khoản đã opt-in, cố định cho cohort đó |
| Tuần cạnh tranh sau MVP | Thứ Hai 00:00 UTC; đầu bao gồm, cuối không bao gồm; chưa triển khai tuần 5–9 |
| Lịch ôn | Một ngày = 86.400.000 ms, giữ thuật toán chung; không là nửa đêm lịch |

Kiểm thử bắt buộc: nửa đêm/đúng biên; cùng Instant ở hai timezone; đổi timezone và tác động lịch sử theo chính sách hiện tại; DST ngày 23/25 giờ; ngày chưa hoàn thành và gap streak; duplicates/nhiều thiết bị; thêm lesson completion không làm giảm streak; Chủ Nhật/thứ Hai UTC; sai đơn vị epoch/âm/quá tương lai/clock rollback; offline >72 giờ vẫn giữ dữ liệu; mapper round-trip; timestamp legacy với cấu hình timezone khác. Nếu sau này duyệt hạn 72 giờ, mới thêm các test trước/đúng/sau hạn và chính sách chuyển tiếp.

## 6. [BỔ SUNG — 6A] Analytics tối thiểu

Opt-in tách khỏi auth và điều kiện dùng app. Không opt-in vẫn học/đồng bộ/nhận XP. Dữ liệu nghiệp vụ bắt buộc không trở thành dữ liệu opt-in chỉ vì được dùng để vận hành tiến độ.

| Sự kiện | Nguồn | Định nghĩa |
| --- | --- | --- |
| app_open | Android | Một lần mỗi phiên foreground; không theo recomposition |
| lesson_started | Android | Bắt đầu phiên/attempt |
| lesson_completed | Android | Trả lời đủ bài tại máy; không phải xác nhận kết quả |
| lesson_passed | Backend | Attempt LESSON đạt ≥70%; dùng cùng định danh nghiệp vụ để dedupe |
| review_session_completed | Android | Phiên kết thúc và có ít nhất một lượt ôn thực |
| D1/D7 | Truy vấn tổng hợp | Có app_open ngày +1/+7 so với lần đầu, theo timezone cohort cố định; chỉ cohort đủ thời gian quan sát |

| Field của analytics_events | Data Type | Constraints |
| --- | --- | --- |
| id | UUID | PK; ổn định khi retry |
| user_id | BIGINT | NN, FK → users.id; là dữ liệu gắn tài khoản, không gọi là ẩn danh |
| event_name | VARCHAR(40) | NN; allowlist |
| session_id | UUID | NULL |
| attempt_id | UUID | NULL; metadata, không FK tới attempt chưa đồng bộ |
| occurred_at | TIMESTAMPTZ | NN; client time hoặc server time cho sự kiện server |
| received_at | TIMESTAMPTZ | NN; máy chủ ghi |
| app_version | VARCHAR(40) | NN |
| timezone | VARCHAR(64) | NN, IANA được kiểm tra |
| properties | JSONB | NN; allowlist gồm số câu/số từ khác nhau/offline và mã nội dung cần thiết |

learner_settings bổ sung analytics_opt_in BOOLEAN NN mặc định false, analytics_consent_version VARCHAR(30) NULL, analytics_consent_at TIMESTAMPTZ NULL. Backend kiểm tra trạng thái opt-in; UUID bị dùng lại với dữ liệu khác bị từ chối.

Hàng đợi Room riêng → endpoint batch → PostgreSQL. Analytics lỗi không chặn outbox nghiệp vụ. Không gửi email, token, ảnh, raw answer, ghi chú tự do hoặc hardware/advertising ID.

**Retention:** raw events hết hạn sau 90 ngày từ received_at; không nhận lịch sử analytics cũ hơn 90 ngày theo timestamp hợp lệ; dọn ít nhất mỗi ngày để dữ liệu hết hạn không còn phục vụ báo cáo. Queue Android cũng có hạn, không khôi phục telemetry cũ sau opt-in. Opt-out dừng sự kiện mới, bỏ queue chưa gửi; raw đã gửi tiếp tục theo hạn 90 ngày, trừ yêu cầu xóa riêng/xóa tài khoản. Không tạo kho aggregate lưu vô hạn trong MVP. Backup retention là hạng mục phải chốt trước release, không coi dọn database là đã xóa mọi backup.

Báo cáo tối thiểu: start→complete, complete→pass, số lần thử đến khi đạt, tỷ lệ phiên ôn hoàn thành, D1/D7 và số tài khoản trong mẫu opt-in. Không đặt tỷ lệ thành công bịa sẵn; dùng baseline sau có dữ liệu. Không suy ra nhân quả giữa XP/ôn và retention từ tương quan.

## 7. [BỔ SUNG] TTS và chuẩn phát âm

**Chuẩn nội dung: Anh–Mỹ (en-US).** IPA, audio/TTS mẫu và ví dụ phải nhất quán. Rà soát 12 từ seed hiện có, đặc biệt các dạng Anh–Anh như dog, bottle, phone, banana; không tự thay bằng regex. V5 không sửa lại: điều chỉnh dữ liệu bằng bản cập nhật nội dung/migration mới khi được phép code.

Ưu tiên voice en-US không yêu cầu mạng nếu có. Nếu thiếu giọng: hướng dẫn cài dữ liệu voice; vẫn cho xem phiên âm, học từ và làm dạng không cần nghe. Không giả phát âm, không ngầm tính câu nghe là đúng hoặc loại khỏi mẫu số để vượt bài. Bài luyện chứa câu nghe chưa thực hiện được giữ dang dở cho đến khi giọng hoạt động; có thể chuyển sang PRACTICE không nghe nhưng PRACTICE không thay kết quả LESSON.

TTS test ít nhất ba thiết bị thật, engine/Android khác nhau và có máy cấu hình thấp; bộ 20 từ/10 câu, gồm trọng âm và từ dễ nhầm. Chạy online, airplane mode, thiếu voice, chuyển nền, đổi từ và phát liên tiếp. Ghi engine/voice/thiết bị/version, latency khởi phát, độ rõ, lỗi cắt/dừng và đánh giá người rà soát độc lập. Không tuyên bố đã kiểm chứng offline chỉ vì setLanguage thành công. Chưa thêm cloud TTS hoặc bộ audio có bản quyền mới.

## 8. [BỔ SUNG] Camera nối chủ đề — tuần 8–9

Chụp book → chọn detection → tra mục từ xuất bản → thấy chủ đề Đồ vật → lưu vào Sổ tay → thấy book trong bộ từ cá nhân của chủ đề → review khi đến hạn.

- Chủ đề từ mapping seed vocabulary–topic; không tự phân loại AI.
- Bộ từ cá nhân là lọc Sổ tay theo mapping, không sửa kho chủ đề dùng chung.
- Không hoàn thành lesson/cấp XP từ chụp hoặc lưu.
- Không có mục từ xuất bản thì báo thiếu, không sinh nội dung giả.
- Offline chưa có mapping vẫn lưu/ôn như hiện tại; bổ sung chủ đề sau tải dữ liệu.
- Không lưu/upload ảnh để nối luồng này; tái dùng metadata và từ đã lưu.

## 9. [BỔ SUNG] Quyền riêng tư và xóa tài khoản trước phát hành

### 9.1. Dữ liệu và mô tả đúng hiện trạng

Tài khoản/email/tên/password cho xác thực; token/cached profile; thiết lập học; saved words/learning/review; metadata nhận diện và báo cáo; device_info nếu backend được truyền; attempts/progress/XP dự kiến; analytics opt-in mới. Ảnh chụp được xử lý trên thiết bị trong luồng đã kiểm tra. Không suy đoán log/IP/SDK/hosting từ schema; cần audit riêng bản release.

**Không có lời bảo đảm “ảnh bị xóa sau 24 giờ”.** Mô tả hiện tại: ảnh tạm trong cache, dọn file cũ khi tạo ảnh mới và xóa tại một số thao tác. Không có timer xóa đúng hạn. Mọi cải thiện chính sách dọn cache cần triển khai/kiểm thử trước khi công bố.

Chính sách riêng tư cần URL hoạt động và link trong app; mô tả mục đích, nơi xử lý, retention/xóa, SDK/TTS và quyền người dùng. Data Safety đối chiếu bản release, không chỉ manifest. Chủ dự án tự xác nhận thị trường, đối tượng tuổi, nghĩa vụ pháp lý và thông tin nhà cung cấp; tài liệu này không khẳng định tuân thủ pháp luật.

### 9.2. Luồng trong app — bắt buộc

1. Cá nhân → Tài khoản → Xóa tài khoản.
2. Hiển thị dữ liệu sẽ xóa, phần không thể khôi phục và cách xử lý ngoại lệ nếu có.
3. Xác minh lại quyền sở hữu bằng cơ chế auth phù hợp; không chỉ nhận email/userId do client gửi.
4. Xác nhận rõ ràng → gửi yêu cầu idempotent. Offline/lỗi mạng hiển thị chưa hoàn tất, không báo đã xóa.
5. Backend ngăn ghi/sync mới, thu hồi phiên và xử lý xóa dữ liệu liên quan theo FK/transaction hoặc quy trình xóa có theo dõi.
6. Sau xác nhận xóa: hủy WorkManager của account, bỏ outbox/session/token/profile và dữ liệu cá nhân Room; xóa cache ảnh do app quản lý trên thiết bị đó theo phạm vi có thể truy cập.
7. Lần truy cập từ thiết bị khác không còn được server chấp nhận. Xóa cục bộ trên thiết bị đang offline chỉ có thể diễn ra khi reconnect/app nhận trạng thái; không hứa remote wipe tức thì.

### 9.3. Luồng trang web — bắt buộc

Trang công khai HTTPS, ghi tên LingoLens/chủ thể phát hành, giải thích phạm vi xóa và cách yêu cầu. Dùng lại backend cho quy trình xác minh và xóa; không cần full CMS. Có thể bắt đầu yêu cầu từ web ngoài app, xác minh sở hữu tài khoản, nhận trạng thái yêu cầu và thông báo hoàn tất không lộ dữ liệu. Không xóa chỉ bằng biết email. Phương thức xác minh cụ thể được đặc tả trước triển khai; nếu cần nhà cung cấp email thì phải chốt, không tự thêm vào stack.

### 9.4. Phạm vi xóa/giữ

| Dữ liệu | Xử lý mục tiêu khi xóa tài khoản |
| --- | --- |
| users, user_roles, learner_settings, refresh_tokens | Xóa identity/settings/quan hệ, thu hồi token |
| saved learning, tombstones, review cards/events | Xóa dữ liệu account; xóa Sổ tay một từ khác với xóa tài khoản |
| attempts, answers, lesson_progress, enrollments, XP | Xóa mọi dữ liệu của account |
| sync_receipts/outbox | Xóa dữ liệu account; phiên cũ không được phép upload lại sau xóa |
| recognition history/reports | Xóa dữ liệu account; xử lý mọi FK trước khi xóa user |
| analytics raw và queue | Xóa dữ liệu account, không chờ hết 90 ngày |
| audit log có danh tính/tham chiếu account | Xóa hoặc ẩn danh thực sự các dấu vết account; rà cả email/details, không chỉ FK |
| content chung, roles, nội dung bài, metadata không gắn người dùng | Giữ; không phải tài sản cá nhân của account |
| ảnh/cache/token trên thiết bị hiện tại | Dọn sau xác nhận; thiết bị offline khác không được hứa xóa tức thì |
| backup/log hosting hoặc ngoại lệ cần giữ | Chưa mặc định giữ vô hạn; phải có danh mục, mục đích, thời hạn và cơ chế loại khỏi bản restore được duyệt trước phát hành |

Receipts/tombstones hiện được giữ để replay an toàn trong vòng đời account; đó không là lý do giữ dữ liệu cá nhân sau xóa account. Một đăng ký mới cùng email không tái dùng identity/lịch sử cũ.

**Release gate:** cả hai luồng xóa hoạt động, xác minh/permission/idempotency được thử, FK được xử lý, không có sync tái tạo dữ liệu, policy/Data Safety đúng bản build và chính sách backup/ngoại lệ có thời hạn cụ thể. Nếu chưa chốt backup/log/ngoại lệ thì chưa phát hành; không bịa thời hạn hoàn tất xóa trong tài liệu.

## 10. [THAY ĐỔI] Lộ trình tuần 5–9 theo lát cắt dọc

Tuần là khung dự kiến, không thay tiêu chí nghiệm thu. Không tăng nội dung hoặc làm CMS để che vòng học chưa chạy trọn.

| Tuần | Phạm vi | Tiêu chí hoàn thành |
| --- | --- | --- |
| 5 | Seed một chặng ba bài từ JSON có kiểm tra; hoàn thiện bài đầu end-to-end với chọn nghĩa, attempt, chấm ≥70%, XP và sync online; chuyển thiết lập tương thích | DB mới nạp được nội dung; một bài chạy từ học tới progress/XP; retry không cấp thêm; payload đổi UUID bị từ chối; account isolation; profile cũ không mất mục tiêu |
| 6 | Cả ba bài trọn vòng; content pack, session resume, nộp offline/reconnect; revision/snapshot | Học mất mạng, reopen giữ vị trí; retry/lost response không nhân đôi; revision cũ chấm được; nâng cấp không mất outbox/library/review tuần 1–4 |
| 7 | **Chỉ** ba dạng bài (chọn nghĩa/nghe chọn từ/điền từ) và TTS/fallback en-US, trên chặng ba bài | Phản hồi/giải thích hợp lệ; chuẩn hóa input có giới hạn; thiếu TTS không giả hoàn thành; thử trên thiết bị và lỗi/lifecycle được ghi nhận. Không analytics, camera mapping hoặc CMS mới |
| 8 | Tích hợp nội dung đã duyệt qua pipeline seed; analytics opt-in/queue/PostgreSQL; camera–topic mức tối thiểu; chuẩn bị phạm vi xóa/privacy | Số lượng/ID/đáp án được kiểm tra; analytics dedupe/opt-out/retention không chặn sync; camera save→topic→review chạy; dependency và phạm vi xóa được chốt |
| 9 | Hoàn tất nội dung còn lại; nghiệm thu cả vòng; xem lại streak và thêm hợp lệ lesson completion; hoàn thiện luồng xóa trong app/web, privacy/Data Safety chuẩn bị release | 18 bài, 90–120 từ, 150–180 câu đã duyệt; không giảm streak cũ, chưa có hạn 72h; hồi quy offline/timezone/security; release gate xóa tài khoản đạt; không còn chỗ lưu/giữ dữ liệu chưa được quyết định |

Analytics/camera có thể hoàn tất ở tuần 9 nếu chưa đạt tuần 8; không mở rộng phạm vi. Xóa tài khoản/privacy là công việc riêng có dự toán sau khi chốt xác minh và backup/log, không xem là checkbox tài liệu. Nếu chưa đủ năng lực/thời gian thì kéo dài chặng nghiệm thu; không bỏ release gate để giữ lịch tuần.

### 10.1. [BỔ SUNG] Workstream nội dung riêng và đệm

| Công việc | Khối lượng | Ngày công ước lượng |
| --- | --- | --- |
| Chặng thử nghiệm | 3 bài, 15–24 từ, 24–30 câu; đã nằm trong tổng MVP | 2–3 biên soạn + 1–2 rà soát |
| Biên soạn toàn MVP | Tổng 90–120 từ độc nhất, 150–180 câu, ví dụ/mẫu câu/giải thích | 6–10; đã bao gồm chặng thử nghiệm, không cộng chặng lần hai |
| Rà soát độc lập | Nghĩa, đáp án/nhiễu, độ khó, IPA Anh–Mỹ, tính nhất quán | 3–5 |
| Sửa và kiểm tra trong app | Toàn bộ bộ nội dung | 2–3 |
| Đệm nội dung | Chờ phản hồi, câu cần viết lại, kiểm tra phát âm | 3–5 |

Tổng nội dung dự kiến **14–23 ngày công**, không phải thời gian code và không mặc định là 14–23 ngày lịch. **Người rà soát phải khác người biên soạn**, có năng lực tiếng Anh phù hợp; AI hỗ trợ không thay thế vòng rà soát độc lập. Mỗi gói có người soạn/người duyệt/ngày/trạng thái; thiếu người duyệt thì chưa xuất bản. Phần chặng đầu cần duyệt trước nghiệm thu tuần 5–6. Nếu cùng người làm code và biên soạn thì cộng công việc, không mặc định song song.

## 11. Cấu trúc dự án và guardrails

Giữ repository hiện có, chuyển dần theo nghiệp vụ: Android core/data/domain/feature(auth, onboarding, today, curriculum, topics, lesson, practice, review, discovery, progress, profile)/sync/navigation; backend auth/user/vocabulary/curriculum/assessment/learning/review/gamification/sync/admin/common; admin features tương ứng khi CMS được triển khai sau. shared/review/core giữ scheduler, shared/assessment/core chỉ thêm thuật toán thuần khi cần. docs/product chứa đặc tả này; roadmap tuần 5–9 nằm trong mục 10, không dùng báo cáo lịch sử làm kế hoạch mới.

Năm nguyên tắc bắt buộc:

1. Backend kiểm tra identity/ownership, chấm kết quả và quyết định XP; không tin score/XP/unlock client. Offline answer key không được quảng cáo là chống gian lận tuyệt đối.
2. Giao dịch atomic và UUID ổn định bảo vệ attempt/progress/XP/outbox; kiểm thử lost response và concurrent retry.
3. Revision/snapshot đã xuất bản bất biến; seed/migration không phá data; không sửa V1–V8, không destructive Room upgrade.
4. Composable không gọi SQL/tự tính XP; ViewModel quản lý session, Repository dữ liệu, service nghiệp vụ; validation/lỗi tập trung, cancellation không bị nuốt.
5. Test clock/timezone/device, nội dung độc lập được duyệt và accessibility; telemetry tối thiểu; release gate privacy/xóa tài khoản không được bỏ. XP/streak/tự đánh giá không là chứng nhận trình độ.

## 12. Điều kiện dừng và bước tiếp theo

Tài liệu chốt phạm vi MVP và kế hoạch. Hợp đồng tuần 5 được bổ sung tại mục 13. Bước 0 chỉ lấy baseline, chưa thay code/schema. Sau bước 0 và từng bước nhỏ phải dừng báo cáo, chờ chủ dự án nói **“tiếp tục”**. Không chuyển tuần 6 khi chưa được duyệt. Xác minh xóa web và backup/log retention vẫn là hợp đồng của giai đoạn sau, không triển khai trong tuần 5.

## 13. [BỔ SUNG — ĐÃ DUYỆT] Hợp đồng thực hiện chỉ tuần 5

### 13.1. Defaults, preflight chỉ đọc và profile adapter

Defaults đúng mục 3.1. Trước V9, có bước preflight **chỉ đọc**, có thể chạy vận hành độc lập trên database đích để báo cáo user id và giá trị target_level/daily_goal lỗi; không xuất password/email/token. Kiểm tra mức không thuộc A1/B1/B2 hoặc NULL, goal NULL/ngoài 1–50, và điều kiện backfill cần thiết. Chưa truy cập database thực để chạy preflight ở bước 0; kiểm chứng fixture và xây công cụ thuộc bước 1.

Không sửa dữ liệu trong preflight. Chạy lại ngay trước deploy và lưu bằng chứng. V9 kiểm tra lại trong transaction, vẫn dừng nếu còn lỗi để tránh phụ thuộc báo cáo cũ hoặc dữ liệu thay đổi giữa preflight và deploy. Không tự fallback cho dòng lỗi; báo chủ dự án xử lý.

GET/PUT profile giữ field và kiểu DTO cũ, đọc/ghi từ learner_settings qua adapter; cập nhật tên ở users. Validation mức hỗ trợ A1/B1/B2. Đăng ký mới, cached profile Android và admin profile cũng theo cùng nguồn chính. Mirror target_level/daily_goal legacy cùng transaction trong thời gian tương thích; streakDays trả như hiện tại. Không xóa cột cũ tuần 5.

### 13.2. JSON seed và schema kiểm tra

Gói JSON bất biến, ID UUID cố định; schemaVersion = 1, packId UUID, contentStatus DEMO/REVIEWED, pronunciationLocale en-US. Có course, topic, unit và đúng ba lessons; vocabulary/snapshot/câu hỏi nằm trong gói với tham chiếu rõ ràng.

| Thành phần | Kiểm tra bắt buộc |
| --- | --- |
| course/topic/unit | Các ID, code/slug/title/description/position đúng kiểu; FK nội bộ hợp lệ |
| vocabularies | Word chuẩn hóa duy nhất; nghĩa/định nghĩa/phiên âm/ví dụ/level đúng cấu trúc; không ghi đè vocabulary cũ |
| lessons | Đúng 3; ID và position duy nhất; unitId đúng; có revision/snapshot tối thiểu |
| questions | 8–10 câu/bài; tuần 5 chỉ MEANING_CHOICE; prompt/explanation không rỗng |
| options/answerKey | 4 option/câu; key và position duy nhất; một đáp án tham chiếu option có thật |
| weight | Integer 1–10; nội dung demo trọng số 1; test thuật toán có trọng số khác nhau |
| Toàn gói | Field bắt buộc/độ dài/allowlist; từ chối field lạ, ID trùng, tham chiếu sai hoặc snapshot không khớp |

JSON Schema đi kèm là hợp đồng kiểm tra, không là lời hứa hỗ trợ toàn bộ chuẩn. Dùng Jackson và validation cho cấu trúc/ngữ nghĩa cụ thể, không thêm thư viện engine. Kiểm tra toàn gói trước khi ghi; import nguyên tử. File/checksum của gói đã nhập không được thay âm thầm.

### 13.3. [SỬA THIẾT KẾ] DEMO không nằm trong migration chính

Chủ dự án chỉ ra đúng: migration trong location chính chạy ở mọi môi trường. Vì vậy **V12 không chứa dữ liệu DEMO**.

**Phương án triển khai được đề xuất trong hợp đồng này: seed runner riêng, gọi tường minh, có checksum và chạy một lần; không hook vào khởi động app.** Không chọn dev-only Flyway location để tránh một database có history V12 mà production không resolve được migration đó.

- V9: learner_settings + kiểm tra/backfill.
- V10: topic/curriculum/revision/snapshot/question/option và partial unique revision PUBLISHED.
- V11: enrollment/attempt/answer/progress/XP, unique ACTIVE và thưởng lần đầu.
- V12: **schema provenance và receipt import nội dung**, không chèn DEMO. Manifest ghi packId/checksum/contentStatus/importedAt; nội dung course/revision có dấu nguồn tương ứng để guard phát hiện DEMO, không chỉ dựa vào tên file JSON.
- Runner chỉ cho profile dev/test, từ chối khi có prod/production kể cả bật kèm dev. Một lệnh vận hành tường minh nhập gói; không tự chạy khi khởi động, không bật bằng cấu hình mặc định.
- Receipt nhập có khóa packId duy nhất. Cùng checksum là no-op; checksum khác bị từ chối. Concurrent import chỉ có một effect; receipt và toàn nội dung cùng transaction.
- Gói ba bài DEMO phục vụ app dev/test và có nhãn nội dung tạm; không khẳng định được rà soát độc lập. Một chặng khoảng 15–18 từ, 24–30 câu chọn nghĩa. Nếu trùng word cũ, chỉ dùng lại khi nội dung tương thích hoặc dừng để xử lý; không upsert mù seed V5/dữ liệu admin.
- **Startup guard production:** sau migration, trước phục vụ request, nếu manifest hoặc nội dung curriculum/revision có contentStatus = DEMO thì từ chối khởi động. Profile prod/production ưu tiên cấm hơn dev/test. Kiểm thử cả database đã nhập DEMO rồi đổi profile và trường hợp nội dung DEMO còn tồn tại dù thiếu receipt import.
- REVIEWED sau này mới được seed qua migration chính mới. Không sửa V12/file đã nhập để nâng DEMO thành REVIEWED. V5 vocabulary legacy không bị tự gắn DEMO và không bị guard chặn chỉ vì chưa có provenance của curriculum mới.

Chi tiết cột provenance/entrypoint runner được thực hiện trong bước 2; không thêm dịch vụ, thư viện hoặc CMS. Runner không là API upload nội dung.

### 13.4. Chấm, hiển thị, retry và UUID Android

- Đạt bài dùng so sánh số nguyên: 100 × tổng trọng số đúng ≥ 70 × tổng trọng số; không dùng tỷ lệ đã làm tròn.
- UI chính: **số câu đúng / tổng số câu**. Nếu hiện phần trăm thì là phần trăm có trọng số **làm tròn xuống**; số câu đúng và điểm trọng số được phân biệt.
- Phải trả lời đủ câu, không trùng câu/option không hợp lệ, tất cả cùng revision. Backend không tin score/XP/unlock client.
- LESSON vượt lần đầu cấp 30 XP theo lesson ID ổn định; PRACTICE chỉ backend/test tuần 5, chưa có UI; không mở bài hoặc cấp thưởng LESSON.
- Cùng UUID và payload ngữ nghĩa trả lại kết quả cũ. UUID cũ với payload khác trả 409. Canonical hash không phụ thuộc thứ tự field hoặc thứ tự danh sách answer; không bỏ qua thay đổi có ý nghĩa như revision/mode/answers.
- Account khác không đọc hoặc tái dùng attempt để lấy kết quả. Không lộ người sở hữu hoặc payload của account khác.
- Khóa ghi theo account và unique XP bảo vệ cạnh tranh. Hai ca concurrent trên **PostgreSQL thật**: cùng UUID → một attempt/thưởng; khác UUID cùng vượt lesson lần đầu → hai attempt nhưng một thưởng. Có test rollback cả attempt/answers/progress/XP khi lỗi giữa transaction.
- Android dùng **SavedStateHandle** để giữ UUID attempt qua rotation và process recreation do hệ thống, cùng revision/mode/answers và trạng thái cần gửi lại. UUID được tạo trước gửi; không tạo lại khi recomposition, retry hoặc phục hồi. Chỉ attempt mới có UUID mới.
- SavedStateHandle không là durable outbox và không bảo đảm phục hồi sau force-stop, clear data hoặc bỏ task. Tuần 5 chưa triển khai content pack/offline lesson/resume bền vững; không mở rộng sang tuần 6 để xử lý các trường hợp đó.
- Attempt nộp trực tiếp online; mất phản hồi giữ cùng UUID để retry. Không đưa attempt vào outbox learning/review tuần 1–4; chưa sửa SyncEngine/Room để đồng bộ lesson offline.

### 13.5. Các bước và điểm dừng

| Bước | Phạm vi được phép khi chủ dự án nói tiếp tục | Bằng chứng |
| --- | --- | --- |
| 0 | Baseline tests/build hiện có; checksum V1–V8; inventory schema/test/env | Báo kết quả thực tế, không gọi kết quả lịch sử là baseline mới |
| 1 | Test trước, settings/preflight/V9/profile adapter | Backfill/invalid data/legacy contract/rollback, regression đầy đủ |
| 2 | Test trước, V10–V12/seed runner/schema/production guard | PostgreSQL partial indexes, import atomic/checksum/concurrent, production DEMO rejection, bảo toàn V8 |
| 3 | Test trước, thuật toán chấm thuần | Trọng số, đúng/dưới/trên 70%, phần trăm floor không quyết định pass |
| 4 | Test trước, API/progress/XP | Ownership, retry/conflict/mode, rollback và hai ca concurrent PostgreSQL |
| 5 | Test trước, UI online bài đầu | UUID SavedStateHandle rotation/process recreation, double-submit/retry, profile compatibility |
| 6 | Nghiệm thu riêng tuần 5 | Bằng chứng từng tiêu chí mục 10, tests/regression và việc chưa làm |

Mỗi bước: test trước hành vi quan trọng → triển khai → chạy toàn bộ tests hiện có (Android unit/Robolectric, backend H2 và postgresTest) → báo kết quả → **dừng chờ “tiếp tục”**, không tự sang bước sau. Admin chưa có test suite trong package.json; build TypeScript/Vite là build, không gọi là test. Lint/APK/bootJar kiểm tra ở baseline và checkpoint tích hợp. Không đụng camera, review scheduler, analytics; không thêm thư viện/dịch vụ. Không sửa V1–V8, destructive migration hoặc dữ liệu database thực để lấy baseline.

Sau bước 6 vẫn dừng để chủ dự án duyệt tuần 5; tuần 6 chưa được cấp quyền tự thực hiện.

Tham chiếu kỹ thuật/phát hành để kiểm tra lại trước release: [partial unique indexes PostgreSQL](https://www.postgresql.org/docs/current/indexes-partial.html), [Android TTS Voice](https://developer.android.com/reference/android/speech/tts/Voice), [Google Play Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en), [account deletion](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en). Các liên kết không là xác nhận app đã tuân thủ.
