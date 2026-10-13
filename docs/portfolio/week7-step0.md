# Tuần 7 — Bước 0: hợp đồng và baseline

Ngày 13/10/2026, nhánh `feature/question-types-tts` từ `c339c4e`. PR tuần 6 [#4](https://github.com/TrinhQuocDat542005/LingoLens/pull/4) đang mở, chưa merge. Bước này chỉ ghi hợp đồng/giả định và lưu fixture tương thích trước khi đổi DTO; chưa thêm chức năng tuần 7.

Hợp đồng và các bước tại [week7-plan.md](../product/week7-plan.md). Ba pack golden lấy trực tiếp từ `ContentPackService.pack` trên PostgreSQL 15 với DEMO tuần 5, trước khi thêm field nullable. Manifest fixture ghi commit nguồn, revision, byte length và SHA-256. Các bytes cố định sẽ bảo vệ pack/checksum của revision đã phát hành hoặc archived, thay vì tính expected bằng serializer mới.

## Gate mới

Các phần khởi chạy trong cùng lượt ngày 13/10, UTC+07. Backend unit và PostgreSQL nối tiếp trong cùng lệnh Gradle; admin là build, không tính là test. Tất cả XML test mới có 0 failures/errors/skipped.

| Phần | Kết quả | Giờ chạy / report |
| --- | --- | --- |
| Android unit/Robolectric | 94/94 | 14:59:28–15:02:32 |
| Backend unit/H2 | 26/26 | Backend 15:00:10–15:03:43; unit XML 15:01:55 |
| PostgreSQL Testcontainers | 45/45, không initialization error | XML 15:03:42 |
| Lint analyze/report mới | 0 errors / 38 warnings / 0 new | XML 15:02:30; Assert-LintBaseline đạt |
| Admin TypeScript/Vite | Build đạt | 15:00:12–15:00:22 |
| APK debug | Build đạt, packageDebug chạy lại | APK 15:01:24 |

APK SHA-256: `76412B4438F8089BD7731A9994FDA42F7ABC71239E7E1688AC59FDD4DA76AAA8`.

Lượt yêu cầu trước đó bị automatic approval review chặn vì giới hạn sử dụng, nên không có test chạy trong lượt đó. Sau khi chủ dự án yêu cầu tiếp tục, cùng lệnh được review cho phép và chạy thành công; không lấy kết quả cũ để thay gate này.

Không sửa migration V1–V13 hoặc schema Room v1–v4. Bằng chứng thiết bị tuần 6, chất lượng TTS thật và nâng cấp APK vẫn **đạt một phần – chưa kiểm chứng trên thiết bị**. DEMO chưa được rà soát độc lập; REVIEWED là gate trước tuần 8. Preflight chưa chạy trên database mục tiêu.
