# Camera–chủ đề: gate kỹ thuật

Nhánh `feature/camera-topic`, từ `feature/analytics-mobile`. Chưa có test trên thiết bị; không PR/merge. Nhánh Room và analytics đã đổi tên thành `feature/room-v5`, `feature/analytics-mobile` và xác minh remote trước xóa tên cũ.

V16 chỉ INSERT topic Đồ vật công khai và mapping book/bottle/phone vào bảng V10, không sửa schema/vocabulary/lesson cũ. SHA-256 LF: `c18bd56f7c6721077ea1382ac883dea678c3252beabacad3585b123802f5a1fa`. Kiểm tra exact versions V1–V16, checksum V16/Flyway và nguồn V1–V15; test nâng cấp V15 giữ từng hàng trong các bảng cũ và lịch sử checksum. V16 chưa áp vào database thử của chủ dự án.

GET /api/v1/topics trả nội dung chung đã published (topic và vocabulary), giữ auth/DTO vocabulary hiện có. Room v5 chỉ thêm DAO, không đổi schema export. Cache transaction, invalid replacement/offline giữ cache tốt; empty published response loại mapping cũ. Result hiển thị topic; save vẫn qua LearningRepository.save. Sổ tay filter accountId trước JOIN. Không upload ảnh, XP, scheduler hoặc lesson mutation.

Gate mới 14/10/2026 (+07): Android 18:45:34–18:46:46 đạt 165/165; backend unit và PostgreSQL trong lượt 18:45–18:48 đạt 48/48 và 73/73 (15 class, không initialization error); lint mới 18:46:42 đúng 38/0 mới; admin build 18:45:33–18:45:37 và APK 18:46:44 đạt. Không failure/error/skipped.

Test mới: TopicRepositoryTest (3) bảo vệ account join/offline/save-review/atomic invalid replacement/dedupe; TopicPostgresTests (2) bảo vệ auth/published-only và DTO cũ; TopicMigrationTests (1) bảo vệ nâng cấp/checksum/no repeat. UI camera→topic→review và migration trên thiết bị vẫn chưa kiểm chứng; không gọi là đạt trên thiết bị.
