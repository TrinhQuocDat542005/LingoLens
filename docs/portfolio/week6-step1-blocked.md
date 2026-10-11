# Tuần 6 — Bước 1: dừng do test fixture thất bại

**ĐÃ GIẢI QUYẾT SAU KHI CHỦ DỰ ÁN CHO TIẾP TỤC:** fixture coi indices thiếu là rỗng; assertions/tests giữ nguyên. Cả năm test đã đạt trong lần chạy lại đầy đủ. Báo cáo dưới đây giữ nguyên như lịch sử lần dừng; kết quả hiện tại xem [bước 1](week6-step1.md).

Ngày 11/10/2026, nhánh feature/offline-lessons. Bước 0 đã commit `29530d9`; **bước 1 chưa commit, chưa hoàn tất**. Dừng theo yêu cầu khi test fail, không sửa để đi tiếp.

## Đã chuẩn bị, chưa nghiệm thu

- Test migration viết trước: fixture database v3 có dữ liệu tám bảng cũ/hai account; đường v1/v2→v4; account-scoped reads, FK chặn tham chiếu chéo và xóa pack đang dùng; rollback session/outbox.
- Thêm bốn entity/DAO và MIGRATION_3_4 cộng thêm; cập nhật registration trong AppContainer và builder của test cũ. Room sinh JSON v4, hiện còn trong working tree để commit cùng bước 1 sau khi đạt.
- Không sửa V1–V12 hoặc Room schemas v1–v3; inventory SHA-256 bước 0 so lại không đổi. Không sửa scheduler/backend, không destructive fallback, không thêm dependency.

## Kết quả kiểm tra lần này

| Kiểm tra | Kết quả |
| --- | --- |
| Android | 56 tests: 51 đạt, **5 test mới fail**, 0 errors/skipped |
| Backend H2/unit | 23 đạt, 0 failure/error/skipped |
| PostgreSQL Testcontainers | 33 đạt, 0 failure/error/skipped |
| Admin TypeScript/Vite | Build thành công, không tính là test |
| Lint bước 1 | Chưa có báo cáo mới hoàn tất trong invocation bị test fail; **không lấy báo cáo cũ làm bằng chứng**. Baseline bước 0 gate 38/0 mới đã đạt. |

Android task kết thúc exit 1. Năm tests đều NPE tại LessonDatabaseMigrationTest.kt:38 khi đọc `entity["indices"].asJsonArray` trong fixture. Schema xuất v1–v3 không ghi field indices cho bảng không có index. Bảng SavedWordEntity đầu tiên không có field này, nên test dừng **trước mở Room/migration**; chưa thể kết luận migration đạt hoặc hỏng.

Ngoài ra compiler báo năm cảnh báo suy luận intersection type ở arrayOf hỗn hợp trong fixture; đây là compiler warning, khác Android lint baseline 38.

## Đề xuất sau khi chủ dự án nói tiếp tục

Đọc indices tùy chọn khi dựng fixture (không bỏ index thật ở bảng có index); khai báo Array<Any> tường minh để hết compiler warning. Sau đó chạy lại toàn bộ Android/backend/PostgreSQL/lint/admin build. Không nới assertion, skip test hoặc sửa schema cũ. Chỉ commit bước 1 khi toàn bộ checks đạt, rồi lại dừng ở checkpoint migration. Chưa bước 2–5, chưa bước 6–7, không merge main.

Report XML/HTML: app/build/test-results/testDebugUnitTest và app/build/reports/tests/testDebugUnitTest/index.html; backend/build/test-results/test và postgresTest. Thay đổi IDE của người dùng giữ nguyên ngoài commit.
