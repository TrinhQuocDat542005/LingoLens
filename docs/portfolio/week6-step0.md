# Tuần 6 — Bước 0: hợp đồng và baseline

Ngày 11/10/2026. Nhánh `feature/offline-lessons` từ origin/main `e1da7b7`. PR #3 tài liệu tuần 5 chưa nằm trong main tại thời điểm fetch; không tự merge. Giữ thay đổi IDE của chủ dự án ngoài commit.

Đặc tả mục 14 ghi các quyết định 1A–6A và điều chỉnh: epoch ms tương thích; pack authenticated có answer key; shared grader/vectors; queue lesson riêng, HTTP 202/LESSON_PREREQUISITE_PENDING chờ điều kiện và BLOCKED cho lỗi vĩnh viễn; mọi bảng Room account-scoped; không chuyển SavedStateHandle cũ; DEMO nghiệm thu kỹ thuật, REVIEWED trước tuần 8.

| Baseline chạy lại trước code | Kết quả |
| --- | --- |
| Android unit/Robolectric | 51 tests, 0 failure/error/skipped |
| Backend H2/unit | 23 tests, 0 failure/error/skipped |
| PostgreSQL Testcontainers | 33 tests, 0 failure/error/skipped |
| Lint gate | 0 errors, 38 warnings, 0 new |
| Admin TypeScript/Vite | Build thành công, không tính là test |

Lệnh: root `gradlew.bat :app:testDebugUnitTest --rerun :app:lintDebug --rerun --max-workers=2 --console=plain`; backend `gradlew.bat test --rerun postgresTest --rerun --max-workers=2 --console=plain`; root `scripts/quality/Assert-LintBaseline.ps1`; admin `npm run build`. JDK21/SDK từ Android Studio, Docker chạy containers riêng. Không chạy vào DB dev của chủ dự án.

[Inventory SHA-256](week6-baseline-files.json) gồm SQL V1–V9, Java migrations V10–V12, SQL resources tương ứng và Room schemas v1–v3. Không sửa các file này trong tuần 6. Room v3 hiện có tám bảng: SavedWordEntity, LearningEvent, VocabularyCache, OutboxOperation, SyncAccount, ReviewCard, ReviewEvent, ReviewSession.

Bước tiếp theo: test trước migration/account/FK/rollback → chỉ thêm Room v4 và DAO → regression đầy đủ → commit bước 1 → **dừng chờ tiếp tục**. Chưa triển khai pack/shared grader/backend offline/sync ở checkpoint này; chưa bước 6–7 hoặc merge main.
