# Xóa tài khoản trong app — gate kỹ thuật

Nhánh feature/account-deletion từ camera-topic; không website deletion, backup/log retention commitment, PR hoặc merge. Chưa có kiểm chứng UI/WorkManager/lifecycle trên thiết bị.

## Hợp đồng receipt đã được chủ dự án duyệt

- Client SecureRandom tạo 32 bytes (256 bit), base64url không padding. Proof mã hóa bằng TokenCipher trong DataStore deletion_proofs riêng; edit hoàn tất trước HTTP, không lưu password. Không bị SessionStorage.clear/Room purge xóa cùng account.
- POST /api/v1/users/me/deletion cần JWT đúng stable userId và nhập lại password; X-LingoLens-Account fence. POST /api/v1/account-deletion/status không cần auth, chỉ trả status DELETED/UNKNOWN; không trả account/email/hash/expiry. Code gửi trong body, không URL.
- V17 chỉ CREATE account_deletion_receipts/code_hash/status/expires_at/index; SHA256 LF `93269f53f125e202465b6730abd23778b35ffc5e8eaaf00d90b1b3f00c970398`. Không account/email/password/raw code trong bảng. Hash SHA256 của code, so sánh MessageDigest.isEqual với chuỗi hash độ dài cố định, kể cả không tìm thấy. Không tuyên bố toàn bộ HTTP/DB lookup có thời gian hằng định.
- Receipt ghi cùng transaction xóa; error rollback cả hai. Receipt hết hiệu lực đúng 7 ngày; job startup/daily prune hết hạn, status đã loại expiry dù job chậm. Job test disabled, test prune dùng Instant chỉ định. Code đã dùng không được phép xóa live account khác.
- DELETED → block dispatch account, cancel work, await mutex idle của word/review, lesson và analytics engine, purge Room account trong transaction, clear credentials/profile đúng identity + prompt flag, rồi xóa proof. Cleanup NonCancellable; nếu process chết giữa các bước, proof còn để resume.
- UNKNOWN + account/token còn hợp lệ → yêu cầu nhập lại password để retry cùng code (không lưu password). UNKNOWN + không có identity/token401 → báo không xác nhận được kết quả, không tự dọn Room hoặc proof. Status mất mạng giữ proof/data. B đang login không bị cleanup receipt A xóa credentials B.

## Xóa dữ liệu và khóa

FOR UPDATE trên users chỉ dùng ở deletion: cần loại các FK inserts trong khi xóa. Không thay SQL profile adapter. PostgreSQL test login concurrent deletion bảo vệ không token orphan; đây không là chỉnh SQL để H2 chạy.

Transaction server: audit có actor/email/USER target/REPORT target/email trong details của account; unlink resolved_by trên report account khác; progress trước attempts, answers qua attempt, XP; attempts/enrollment/review/learning/receipts/analytics/reports/history/token/roles/settings; user cuối rồi receipt. Nội dung chung/topics/roles definitions/content imports giữ nguyên. Dữ liệu B nguyên vẹn; tham chiếu resolved_by vào user đã xóa phải được unlink để không dangling FK.

Room purge: analytics rows/consent, lesson outbox→session→pack, curriculum state, business outbox, review events/session/card, learning events/saved words/SyncAccount. VocabularyCache/TopicCache/TopicVocabularyCache công khai giữ; không destructive fallback hoặc sửa Room export 1–5.

## Gate mới 14/10/2026 (+07)

Android 19:11:32–19:13:31 đạt 173/173; backend unit và PostgreSQL cùng lượt 19:11–19:14 đạt 48/48 và 80/80, không failure/error/skipped/initialization error. Lint báo cáo mới đúng38/0mới; admin build19:11:32–19:11:34 và assembleDebug đạt.

Tests mới: DeletionPostgresTests6 (FK/no orphan/isolation/token/reregister, wrong-password/rollback, anonymous lost-response/idempotence/no-PII, expiry/prune, two-delete concurrency, login vs delete); DeletionMigrationTests1 (V16→17 mọi bảng cũ/rows/history/checksum nguồn V1–V17); DeletionRepositoryTest7 (persist-before-send/lost response/restart/UNKNOWN401/B/same-code/password/secure generator); AccountDataCleanerTest1 (13 bảng, rollback/từng hàng B/shared cache/FK/idempotent).

Giới hạn: proof-store test dùng cipher giả, không chứng minh Android Keystore trên thiết bị; worker stop/drain và UI mới chưa instrumentation. Backend/Room mới chưa cài vào emulator của chủ dự án. APK hiện tại chỉ là artifact gate kỹ thuật.
