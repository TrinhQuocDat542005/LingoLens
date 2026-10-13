# Tuần 8 — Kế hoạch đề xuất, chờ chủ dự án duyệt

**Cập nhật:** chủ dự án đã duyệt với điều chỉnh tại [week8-approved-contracts.md](week8-approved-contracts.md). Bản nháp dưới đây giữ để đối chiếu; cohort/marker, timing REVIEWED, retention commitment và JWT timing trong đề xuất cũ không còn là hợp đồng áp dụng.

Căn cứ: đặc tả v2 §6, §8, §9, §10 và repo `feature/question-types-tts` tại `4b2c65e`. Đây là tài liệu đề xuất; không thay đặc tả đã duyệt, không cho phép code/migration. Không merge PR #4, không mở PR tuần 7. Chủ dự án sẽ kiểm nâng cấp APK 5→7 trước.

Tuần 8: tích hợp nội dung REVIEWED, analytics opt-in/queue/batch/retention90, camera–chủ đề tối thiểu, chuẩn bị phạm vi xóa/privacy. Tuần 9: hoàn thiện hai luồng xóa và release gate. Không thêm thư viện, email provider, CMS, cloud analytics, audio/TTS service hoặc tính năng khác.

## 1. Hiện trạng đã đọc và các điểm cần chốt

| Phần | Hiện trạng trong repo | Tác động tới kế hoạch |
| --- | --- | --- |
| Nội dung | Resources chỉ có demo-week5.json, demo-week7.json, seed.schema.json; fixture test chưa phải REVIEWED. ContentSeedImporter chỉ nhận DEMO/dev-test và nói REVIEWED phải qua main migration | Chưa có gói/biên bản duyệt trong repo. Cần người soạn và người rà soát khác nhau; không tự đổi status DEMO để qua gate |
| Production guard | Đếm mọi receipt/course/revision có content_status=DEMO, không lọc ARCHIVED | Publish REVIEWED không làm database có DEMO chạy production được. Không nới guard hoặc xóa fixture/lịch sử để qua gate |
| Revisions | Pack immutable, snapshot từ/câu, receipt checksum, revisionNo tăng, stable lesson IDs giữ progress/XP | Gói mới cần IDs/checksum/revision mới. Sửa từ công khai không được sửa snapshot/bytes của revision cũ |
| Analytics | Chưa có analytics_events, consent fields, Room queue hoặc endpoint batch. Đặc tả đặt consent ở learner_settings | Reuse learner_settings làm bảng opt-in chính, không tạo thêm nguồn consent backend trùng nhau |
| Queues | Room v4 có OutboxOperation và LessonAttemptOutbox; SyncCoordinator chạy queue nghiệp vụ độc lập. Logout chỉ clear credentials, giữ Room | Analytics cần queue/worker riêng. Logout khác xóa account; không xóa queue nghiệp vụ khi opt-out/logout |
| Topic | PostgreSQL có topics/topic_vocabularies từ V10; DEMO18 từ map chung vào Tiếng Anh hằng ngày. Vocabulary API/DTO chưa trả topic, Room chưa cache topic | Không suy rằng đã có kho chủ đề camera. Cần mapping công khai được duyệt và cache mới |
| Save→review | LearningRepository.save lưu word, learning event, ReviewCard và SAVE_WORD outbox cùng transaction; bỏ imagePath/audioPath | Dùng lại luồng này, không đổi scheduler hoặc thêm XP từ camera |
| Delete FK | Các FK user ở V7–V11/V9 chủ yếu không CASCADE. answers FK ghép tới attempt; progress.last_attempt_id FK đơn. Audit có actor FK và email/target/details TEXT | Không thể chỉ delete users. Cần kế hoạch thứ tự, kiểm cross-account/orphan và cleanup audit text |
| Auth | JWT sub là email; filter findByEmail; token validation so email và expiry. Chưa có endpoint xóa/password recovery/email delivery trong phần đã đọc | Rủi ro token cũ sau xóa và đăng ký lại cùng email; phải kiểm bằng test trước khi bật deletion |
| Web | React/Vite admin hiện chặn learner ở login ROLE_ADMIN; chưa có trang deletion/public privacy | Trang public riêng trong cùng frontend/backend hiện có; không dùng màn admin để xóa account learner |
| Ảnh/cache | captured_images filenames không có account owner; dọn file >24h khi tạo ảnh mới, không có timer. VocabularyCache là cache chung | Không hứa xóa ảnh sau24h hoặc xóa riêng mọi ảnh của A. Cần giải quyết ownership/tác động cache khi làm deletion tuần9 |

Rủi ro JWT là suy luận từ code, không phải test exploit đã chạy: token cũ còn hạn có subject=email; account mới cùng email được filter lookup và validation hiện tại không so stable user ID. Files: security/JwtService.kt, JwtAuthenticationFilter.kt. Đây là blocker trước release deletion, không tự sửa trong lượt lập kế hoạch.

## 2. Hợp đồng REVIEWED đề xuất

### Gói và người duyệt

- Giữ seed payload có schemaVersion/contentStatus/pronunciationLocale/course/topic/unit/vocabularies/lessons, ba bài/mười câu/weight1/tối đa hai câu nghe như tuần7. Seed vẫn kiểm kiểu, references/IDs, variants, fallback, sizes và collision.
- Tạo **approval manifest riêng**, vì schema seed hiện strict additionalProperties=false. Trường: approvalVersion, packId, contentChecksum(SHA256 exactbytes), authoredBy, reviewedBy, reviewedAt(ISO UTC), approvalStatus=APPROVED, evidenceRef(commit/PR/biên bản), pronunciationLocale=en-US. REVIEWED là trạng thái nội dung; DRAFT/PUBLISHED/ARCHIVED vẫn là trạng thái revision, không gộp hai khái niệm.
- Người duyệt khác người soạn và có năng lực tiếng Anh; chủ dự án cung cấp danh tính/biên bản và quyết định phát hành gói. Khai tên trong JSON không tự chứng minh review; checksum manifest + biên bản review/Git là bằng chứng quy trình, không gọi là chữ ký số.
- Duyệt đúng bytes cuối cùng; thay bất kỳ nội dung/IPA/answer key nào phải tạo checksum và review mới. Không dùng AI hoặc fixture test làm người rà soát độc lập.
- Gate REVIEWED của chặng ba bài vẫn phải xong trước code tuần8 theo quyết định trước. Repo chưa có artifact này; không mặc định việc duyệt báo cáo tuần7 là duyệt nội dung.

### Nhập và thay thế

Khuyến nghị: production database riêng, chỉ có REVIEWED; DEMO và lịch sử thử vẫn ở dev/test. Giữ production guard hiện tại.

Gói REVIEWED mới có pack/revision/question UUID mới và revisionNo cao hơn lịch sử; lesson/course IDs giữ nếu cùng chặng, để không reset progress/XP trong dev và phiên cũ vẫn pinned. Receipt DEMO/checksum/snapshot/attempt cũ không đổi. Trong dev có thể cập nhật metadata trạng thái course cho nội dung mới, nhưng không relabel revision/receipt DEMO thành REVIEWED; dev vẫn không đủ điều kiện production. Fresh production chỉ nhập gói REVIEWED; không chuyển DB có DEMO sang production bằng đổi profile.

REVIEWED vào migration chính mới như hợp đồng hiện có, sử dụng resource/approval checksum đã pin, transaction tạo DRAFT→children→archive revision trước→PUBLISHED→receipt. DemoSeedCli vẫn chỉ dev/test/DEMO; không mở importer production qua cờ cấu hình. Có preflight chỉ đọc kiểm IDs, revisionNo, collisions/metadata trước migration, lỗi thì báo và dừng.

Nếu từ đã có (đặc biệt V5) cần sửa IPA/nội dung sau review: lập diff biên tập theo đúng vocabulary ID, kiểm expected giá trị cũ, cập nhật bằng migration mới được duyệt; không sửa V5, không regex IPA, không sửa lesson snapshots cũ. Sự thay đổi word/meaning ngoài mô hình word UNIQUE phải dừng hỏi, không tự tách từ đa nghĩa.

Vocab/topic mapping cho camera cần gói dữ liệu công khai riêng được rà soát, không ép vào pack ba bài hiện chỉ có một topic/unit. REVIEWED không yêu cầu làm đủ18 bài ngay tuần8; phần mở rộng toàn nội dung ở tuần9.

## 3. Analytics: schema, queue và hợp đồng

### PostgreSQL analytics_events

| Field | Kiểu | Ràng buộc đề xuất |
| --- | --- | --- |
| id | UUID | PK, UUID ổn định khi retry |
| user_id | BIGINT | NN, FK users.id ON DELETE CASCADE; server lấy từ principal |
| event_name | VARCHAR(40) | NN, CHECK allowlist5 events |
| session_id | UUID | NULL; bắt buộc theo event app_open/review_session_completed; metadata không FK vào session local |
| attempt_id | UUID | NULL; bắt buộc theo lesson event; không FK vì attempt offline có thể chưa tới server |
| occurred_at | TIMESTAMPTZ | NN, timestamp khai báo và kiểm hợp lệ |
| received_at | TIMESTAMPTZ | NN, server ghi, căn cứ retention |
| app_version | VARCHAR(40) | NN, validated; nếu producer server không biết version phải ghi rõ unknown, không bịa version |
| timezone | VARCHAR(64) | NN, IANA hợp lệ |
| properties | JSONB | NN, allowlist/giới hạn size, không free text/raw answer/ảnh/email/token |
| payload_hash | CHAR(64) | NN, hash canonical validated payload; UUID khác payload bị từ chối |
| consent_revision | BIGINT | NN, generation của consent đã xác nhận |

Index user/time theo đặc tả và received_at để prune. Unique có điều kiện theo user/event/attempt cho lesson events và user/event/session cho app_open/review completion, tránh tạo hai UUID cho cùng hành vi. Không dùng analytics làm bằng chứng cấp XP/progress.

### Bảng opt-in

Backend **learner_settings** là nguồn chính theo §6, thêm:

| Field | Kiểu | Ràng buộc |
| --- | --- | --- |
| analytics_opt_in | BOOLEAN | NN DEFAULT false; mọi account cũ không tự được opt-in |
| analytics_consent_version | VARCHAR(30) | NULL; phải có khi đã opt-in |
| analytics_consent_at | TIMESTAMPTZ | NULL; server ghi lúc đồng ý |
| analytics_consent_revision | BIGINT | NN DEFAULT0, tăng mỗi lần đổi consent |

Đề xuất endpoint settings consent riêng, không đổi tên/kiểu GET/PUT profile cũ. Re-enable không nhận queue thuộc consent generation trước. API đổi consent có kiểm revision để thao tác cũ/thiết bị khác không ghi đè lựa chọn mới; xung đột trả409 và tải lại trạng thái.

D1/D7: đề xuất định nghĩa cohort từ app_open đầu tiên được server chấp nhận sau lần opt-in đầu tiên của account, cố định timezone cho cohort; không tạo cohort mới khi opt-out rồi opt-in lại, không suy timezone từ TIMESTAMP legacy. Anchor/timezone chỉ giữ trong cửa sổ90 ngày, thêm marker đã khởi tạo cohort để prune không biến người cũ thành cohort mới. Marker là metadata kỹ thuật consent, không giữ aggregate vô hạn. Không có anchor/đủ ngày quan sát thì không tính D1/D7. Chốt nghĩa “đầu tiên được chấp nhận” so với occurred_at trong hợp đồng trước test/code; timestamp offline đến sai thứ tự cần vector riêng.

### Room v5 và batch

- AnalyticsConsentState: accountId PK; server-confirmed enabled/version/revision, pending opt-out, timestamp epoch ms. Đây là projection của server, không nguồn opt-in thứ hai. Opt-in cần ack online trước khi ghi telemetry; opt-out tắt tại máy ngay, purge queue và lưu pending cập nhật server nếu offline.
- AnalyticsOutbox: PK(accountId,eventId), FK accountId→AnalyticsConsentState ON DELETE CASCADE; eventName/sessionId/attemptId/occurredAt(epochms)/timezone/appVersion/propertiesJSON/payloadHash/consentRevision/state/retryCount/lastError. Không FK vào LessonAttemptOutbox hoặc OutboxOperation.
- Logout giữ dữ liệu nghiệp vụ như hiện tại, dừng worker A; B không dùng token để gửi queue A. Opt-out chỉ xóa analytics queue A. Xóa account xóa cả analytics state/queue A; không đụng B.
- Worker/unique work riêng, không làm analytics thành điều kiện thành công của SyncCoordinator nghiệp vụ. Hooks đọc hành vi commit: foreground app_open một lần, start attempt, freeze lesson_completed, review finished với ít nhất một ReviewEvent. Không ghi telemetry trong transaction khiến lưu bài/ôn thất bại; dùng stable source UUID và reconciliation để chống duplicate/crash nếu cần.
- lesson_passed chỉ backend phát khi **LESSON, CONFIRMED và passed**, gồm trường hợp WAITING_PREREQUISITE sau đó được xác nhận; không nhận event này từ Android hoặc suy từ điểm tạm/PRACTICE. Thử lại không tạo thêm event. Không có opt-in hiện hành thì không tạo/backfill sự kiện trước đó.
- POST batch authenticated, account fence, đề xuất tối đa50 events/128KiB; validate từng item trước ghi. Per-item accepted/duplicate/dropped(expired,opt-out,stale consent)/rejected(invalid,UUID conflict), không lộ event account khác. Validation failure analytics không làm lesson/review queue BLOCKED. Retry transient giữ UUID/payload; không retry vô hạn lỗi permanent.
- API timestamp mới ISO UTC; biên Android chuyển epochms↔Instant. Giữ nguyên payload/hash lesson và sync cũ.

Retention: raw xóa sau90 ngày từ received_at; query không đọc hết hạn dù daily job chưa chạy. Không nhận occurred_at cũ hơn90 ngày, timestamp âm/sai đơn vị/quá tương lai; clock sai chỉ ảnh hưởng telemetry. Android prune queue90 ngày khi worker/startup; opt-in lại không hồi sinh lịch sử. Backend prune lúc startup và ít nhất mỗi ngày, không thêm scheduler service. Opt-out đã gửi vẫn hết hạn90 theo §6, trừ account deletion/xóa riêng. Policy backup phải nói rõ bản backup có thể giữ đến expiry, restore cần purge expired và áp deletion manifest trước nhận traffic.

Hộp thoại đề xuất: “Cho phép LingoLens thu thập các sự kiện mở app, bắt đầu/hoàn thành/đạt bài và hoàn thành phiên ôn để cải thiện lộ trình? Dữ liệu gắn với tài khoản, không gồm ảnh, đáp án bạn nhập, mật khẩu hoặc token. Sự kiện thô trên hệ thống đang chạy được giữ tối đa90 ngày. Bạn vẫn dùng đầy đủ app nếu từ chối và có thể tắt trong Cài đặt; tắt sẽ bỏ sự kiện chưa gửi, các sự kiện đã gửi hết hạn theo chính sách hoặc bị xóa khi xóa tài khoản.” Link privacy gồm backup/log và ngoại lệ. Hai nút ngang mức “Cho phép”/“Không, cảm ơn”, mặc định false, không bật sẵn hoặc gộp với đăng ký. Đây là wording chờ duyệt.

## 4. Xóa account và FK: chuẩn bị tuần8, triển khai tuần9

### Phạm vi/server và thứ tự

| Nhóm | Xử lý đề xuất |
| --- | --- |
| lesson_progress | Xóa theo user trước attempts vì last_attempt_id FK |
| attempt_answers | Xóa qua owned attempts; không xóa questions/revisions/content snapshots chung |
| attempts, XP, enrollments | Xóa mọi row user; giữ content chung |
| learning_entries/tombstones, review_cards/events, sync_receipts | Xóa theo user; receipts là replay metadata account, không giữ vô hạn sau deletion |
| analytics_events + consent/settings | Xóa theo user ngay, không chờ90 ngày; FK mới CASCADE là phòng vệ, service vẫn theo transaction có kiểm chứng |
| refresh_tokens/user_roles | Thu hồi và xóa; schema hiện CASCADE users |
| recognition_history/reports | Xóa rows thuộc user; resolved_by trên report của người khác phải NULL trước delete actor; history link V6 đã SET NULL |
| admin_audit_logs | Khuyến nghị xóa rows liên quan actor/USER target và mọi email/details gắn account; không chỉ NULL actor FK. Audit retention riêng không hợp thức hóa giữ PII sau deletion |
| content_import_receipts | Giữ: đây là receipt seed nội dung chung, không có user_id; phân biệt sync_receipts |
| deletion receipt/restore manifest tương lai | Tách kỹ thuật: token hash/request UUID/status/expiry; manifest restore chứa identity tối thiểu có thời hạn, vẫn coi là dữ liệu gắn account và công bố. Không lưu password/token/email/raw request |

Không tự đổi các FK V1–V13 sang CASCADE. Service xóa theo thứ tự trong transaction và kiểm guard account/concurrent writers; analytics FK mới CASCADE. Preflight kiểm orphan/cross-owner references (progress.last_attempt_id hiện không FK ghép user); nếu có lỗi dừng, không xóa dữ liệu B để xóa A. FK mới ghép account hoặc bổ sung constraint cũ chỉ sau preflight và migration được duyệt riêng, không auto sửa dữ liệu.

Receipt deletion có thể giữ hashed capability tối đa7 ngày theo phương án khuyến nghị để kiểm kết quả sau lost response; không giữ FK bắt buộc vào users đã xóa. Trước xóa cấp proof/status capability ngắn hạn trong phiên reauthenticated; sau xóa chỉ proof đó đọc status. Không cho xem theo email/userId/UUID đoán được. Phải chốt state machine/idempotency và thời hạn trước tuần9; không coi generic401 là “đã xóa thành công”.

### Room cleanup

Hủy work A và đợi dispatch/coroutine dừng; transaction xóa AnalyticsOutbox/ConsentState, LessonAttemptOutbox→LessonSession→ContentPack, CurriculumAccountState, OutboxOperation, ReviewEvent/ReviewSession/ReviewCard, SavedWordEntity/LearningEvent, SyncAccount thuộc A; dọn credentials/profile A sau xác nhận backend. Room không có users parent, không áp CASCADE tưởng tượng cho bảng cũ. Giữ dữ liệu B và VocabularyCache/topic mapping công khai chung.

Ảnh tạm hiện chưa có owner: kế hoạch tuần9 phải bổ sung ownership cho ảnh mới hoặc chốt cleanup shared temporary cache với tác động được giải thích/test. Chưa hứa xóa riêng mọi ảnh A trong cache cũ. Không hứa remote wipe thiết bị offline tức thì; reconnect cần xác minh account gone có proof identity trước purge, không dùng mọi401 như lệnh wipe.

### App/web và auth

App: Cài đặt/Tài khoản→phạm vi xóa→nhập lại password→proof mục đích deletion ngắn hạn, dùng một lần→confirm→transaction xóa→local cleanup. Offline không xác nhận thành công, không cho yêu cầu deletion đi qua queue analytics hoặc outbox bài học.

Web: public HTTPS `/delete-account` và `/privacy`, dùng frontend React/Vite/backend hiện có, không yêu cầu ROLE_ADMIN hoặc reinstall. Nhập email/password chỉ để xác minh bằng PasswordEncoder hiện có; xác nhận deletion proof mới trước xóa. Không dùng session token lâu trong URL/localStorage, không log password/body, không chỉ dựa email hoặc token đang đăng nhập. Nếu chưa nhớ password: phương án này chưa có self-service recovery, phải công bố hỗ trợ; không bịa endpoint reset/email delivery.

Đề xuất token mới vẫn có response auth như cũ nhưng thêm stable userId claim; filter kiểm claim khớp identity hiện hành. Trước bật deletion cần từ chối legacy token không bound identity; lựa chọn đơn giản là một lần đăng nhập lại. Test bắt buộc: old access+refresh token sau delete, đăng ký lại cùng email với ID mới, pending queue cũ không nhập sang identity mới. Thời điểm làm sửa auth cần chủ dự án duyệt, ưu tiên cùng tuần9 deletion.

Google Play yêu cầu lối yêu cầu xóa trong app và web không bắt cài lại app, URL hoạt động/nhận diện tên app và scope xóa: [hướng dẫn chính thức](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en). Đây là yêu cầu nền tảng, không phải kết luận pháp lý. Chủ dự án phải xác nhận thị trường/độ tuổi, hosting/SDK/log, nghĩa vụ và policy bản phát hành.

Privacy inventory cần xác nhận: account/password hash/token/device_info, learning/review/recognition reports và notes, attempts/raw answers/progress/XP, analytics consent/events, admin audit, cache ảnh. TTS network voice/SDK/hosting cần audit thực tế; không khẳng định mọi TTS luôn offline. Không bảo đảm xóa ảnh sau24h. Không đưa password/raw answer/ảnh vào analytics. Không bổ sung retention90 cho dữ liệu học nghiệp vụ như thể đó là telemetry.

## 5. Camera–chủ đề tối thiểu

Khuyến nghị một chủ đề Đồ vật, mapping REVIEWED book/bottle/phone có trong catalog V5 và recognition supported words. Đây là mapping, không thêm bài/XP hoặc tự phân loại AI. Nếu IPA từ V5 cần đổi, thuộc workstream nội dung đã duyệt, giữ snapshot cũ.

Luồng: chụp→chọn detection→lookup published vocabulary→hiện topic nếu có mapping→lưu bằng LearningRepository.save cũ→Sổ tay lọc topic qua JOIN cache public với account words→review theo due_at hiện có. Không tự chèn câu mới vào lesson pack. Không có published word thì báo thiếu; không có mapping thì vẫn lưu/review như cũ và ghi chưa có chủ đề. Offline mapping cache thiếu không chặn lưu; sau tải mapping bổ sung topic mà không tạo SAVE_WORD/ReviewCard trùng.

Backend trả topic mapping public đã xuất bản, giữ Vocabulary response cũ qua endpoint riêng; không sửa hash/payload SAVE_WORD. Room thêm TopicCache(id PK, slug UNIQUE,title,version/fetchedAt) và TopicVocabularyCache(PK topicId,word; FK topicId→TopicCache). Đây là nội dung công khai, không có account/ảnh. Sổ tay cá nhân luôn filter accountId trước JOIN, không tạo bản sao library thứ hai. Migration Room4→5 gom bốn bảng mới analytics/topic, không sửa schema1–4.

## 6. Các bước nhỏ sau khi được duyệt và gate nội dung đạt

Số migration chỉ là dự kiến, chưa đặt/viết: REVIEWED trước, analytics/consent sau, FK/deletion chỉ khi duyệt phạm vi. Mỗi bước một commit trên nhánh mới từ tuần7; không merge PR#4 hoặc mở PR tuần7. Chủ dự án tự test upgrade5→7 trước khi cho mở PR tuần7; chưa có kết quả thì ghi partial, không tự biến việc đó thành quyền mở PR. Tests quan trọng viết trước; đủ Android/backend/postgresTest/lint38/admin build/APK sau mỗi bước. Failure/contract change/new dependency thì dừng.

| Bước | Công việc | Tests trước và tiêu chí hoàn thành |
| --- | --- | --- |
| 0 | Chốt sáu quyết định, REVIEWED artifact/approval; inventory/FK/retention và preflight chỉ đọc | Thiếu reviewer/checksum/metadata/production strategy thì dừng. Theo dõi upgrade5→7 của owner và ghi partial nếu chưa có kết quả; git/source checksums/baseline |
| 1 | Pipeline REVIEWED và migration nội dung mới | PostgreSQL fresh install chỉ REVIEWED qua production guard; DB dev13 có DEMO/history nhập reviewed mới giữ từng row/password/token/entries/review/receipt/attempt/answer/progress/XP; oldpack bytes và archivedgrade nguyên vẹn; tampered approval/collision/revisionNo không tăng rollback |
| 2 | Schema analytics/settings mới và Room4→5 | PG upgrade từ13 giữ mọi dữ liệu cũ, consentfalse; Room mở fixture schema4 đã export với hai accounts/full queues và so từng dòng; paths1/2/3/4→5/reopen/rollback giữa migration; commit schema5; builder có4→5, không destructive fallback |
| 3 | Consent + backend batch/retention, chưa hook UI event | Opt-infalse từ chối; opted UUIDsame dedupe/differentpayloadconflict; accountfence; timezone/clock/90day boundaries; partial item failure; consent-generation/opt-out concurrentbatch và delete trên PG thật; expired không vào reports; settings adapter cũ không đổi |
| 4 | Android consent/AnalyticsWorker/hook events và D1/D7 queries | Không có telemetry trước ack; không backfill; opt-outoffline purge và cập nhậtserver; logoutA/loginB khôngdispatchA; analyticsBLOCKED khôngchặn2queuebusiness và ngược lại; recomposition/lifecycle/duplicate/crash khôngnhândôi; WAITING_PREREQUISITE chưa có passed; review0answers khôngevent; cohort/DST/offline/retention tests |
| 5 | Mapping/camera→Sổ tay chủ đề→review | Published only, unknown/offlinemissing mapping khôngchặnsave; cachedmapping hiệnsau; duplicateSAVE/ReviewCard không tăng; accountA/B join; snapshot/UUIDreview/scheduler/outbox cũ giữ nguyên; khônguploadảnh/XP/lessonmutation |
| 6 | Privacy/delete contract hoàn chỉnh, gate kỹ thuật tuần8 | Ma trận dữ liệu/FK và retention được duyệt, endpoint/stateflow/reauth/JWT/retry/restore testplan sẵn cho tuần9; reviewed content evidence; matrix ghi partial cho hardware chưa chạy; không triển khai hai luồng deletion hoàn chỉnh ở tuần8 |

Không nhồi biên soạn/rà soát nội dung vào thời gian code: chặng đầu3bài dự toán2–3 ngày soạn +1–2 ngày review độc lập +1–2 ngày sửa và đệm; phụ thuộc người thật. Workstream toànMVP14–23 ngày công trong §10.1 vẫn giữ, không mặc định tuần8 sẽ soạn xong18bài.

Rủi ro chính: production guard/DEMO coexistence; sửa IPA collideword UNIQUE; migration locks/fixtures old queues; consent races/cross-device queue; event clocks/cohort/pruning; analytics failure gây chậm sync nếu dùng chung worker; ownership FK/delete order và email-bound JWT; raw audit/log/backup khôi phục PII; ảnh cache chưa account owner. Xử lý bằng preflight, snapshots before/after, golden checksums, PG concurrency, Room migrations có data, các gate và restore rehearsal; không drop dữ liệu để đạt test.

## 7. Sáu quyết định cần chủ dự án trả lời

| # | Câu hỏi / lựa chọn | Khuyến nghị |
| --- | --- | --- |
| 1 | REVIEWED/production: **A** production DB riêng chỉ REVIEWED, DEMO/history giữ ở dev, guard giữ nguyên; **B** muốn đưa DB đang có DEMO lên production, phải thiết kế riêng việc purge/guard/transition trước. Với cả hai, cung cấp người soạn/người rà soát khác nhau và artifact ký duyệt/checksum; chưa có artifact thì giữ gate chưa code tuần8 | **1A**, không relabel old DEMO hoặc suy approval từ báo cáo kỹ thuật |
| 2 | Web deletion: **A** email+password reauth và short deletion proof, không email provider; người quên password cần hỗ trợ chưa có automated reset. **B** xác minh/recovery bằng emailed one-time link, cần quyết định provider/authorization thêm hạ tầng, chưa được triển khai trong giới hạn hiện tại | **2A**, dùng auth/library hiện có |
| 3 | Retention ngoài analytics: **A** backup tối đa7ngày, operational logs tối đa7ngày, deletion status receipts7ngày, restore deletion manifest8ngày; **B** backup30ngày/log14ngày/status receipt30ngày/restore manifest31ngày. Cả hai xóa audit chứa PII account khi account deletion, không giữ logs/body/token/password/rawanswers; thời hạn là đề xuất vận hành, phải kiểm hosting thực tế | **3A** cho MVP, không gọi prune DB là xóa mọi backup. Nếu hosting không đáp ứng phải báo trước release |
| 4 | Consent: **A** hộp thoại wording ở mục3 sau onboarding một lần, hai nút ngang mức, defaultfalse và Settings luôn đổi được; **B** chỉ bật tại Settings với cùng giải thích, không hiện onboarding prompt. Raw đã gửi giữ90 ngày theo §6; không thay rule này | **4A**, cần duyệt chính xác wording và cohort semantics/metadata mô tả ở mục3 |
| 5 | Camera/topic: **A** một chủ đề Đồ vật, mapping được duyệt book/bottle/phone, hiển thị Result và filter Sổ tay; **B** tất cả topic mapping đã duyệt của chặng hiện có, chưa bổ sung topic Đồ vật nên book có thể chưa mapped | **5A**, mức nhỏ nhất chứng minh camera→topic→review, không thêm bài hoặc model |
| 6 | JWT boundidentity trước deletion: **A** chốt contract bây giờ, triển khai tuần9 cùng deletion; reject legacy unbound tokens và một lần login lại trước bật deletion. **B** duyệt một bước authsecurity riêng ở tuần8, cùng behavior và tests; đây là bổ sung implementation scope tuần8 cần bạn cho phép | **6A**, giữ tuần8 là chuẩn bị deletion, nhưng release tuyệt đối phải đóng ca oldtoken/emailreuse |

## 8. Có thể chuẩn bị trước và phải chờ

Trong lượt kế hoạch có thể làm độc lập: inventory/FK graph/test scenarios; draft schemas/consent/privacy wording; checklist reviewer/IPA V5; mapping đề xuất; fixture và preflight **thiết kế**; kiểm nguồn/export/baseline. Việc này không cho phép viết code/tests/migration khi chưa duyệt. Nếu chạy preflight trên DB mục tiêu sau này cần xác định đúng database và chỉ đọc.

Phải chờ: sáu quyết định; artifact REVIEWED và người rà soát; backup/log/hosting/domain thông tin thực; explicit permission bắt đầu code. PR tuần7 phải chờ owner test upgradeAPK5→7 và cho phép mở. Nhập REVIEWED/tạo migration/prompt analytics/cameraUI/reauth/JWT/deleteweb không làm trước. Email provider chưa được phép thêm dù chọn hướng email; cần chốt lại giới hạn stack. Full deletion app/web và restore rehearsal là tuần9 trước phát hành.

Chưa chạy test/build cho lượt lập kế hoạch; không lấy gate tuần7 làm nghiệm thu tuần8. Dừng chờ chủ dự án duyệt.
