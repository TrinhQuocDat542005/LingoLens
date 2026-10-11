# Tuần 5 — Preflight learner_settings trước V9

SQL độc lập: [learner-settings-v9.sql](../../backend/src/main/resources/preflight/learner-settings-v9.sql). File nằm ngoài `db/migration`; Flyway không tự chạy. Không có V9 trong checkpoint này.

## Cách chạy trên database đích

Từ root repo, dùng `psql` đã có sẵn của môi trường vận hành:

```text
psql --dbname=service=lingolens_preflight --no-psqlrc --set=ON_ERROR_STOP=1 --csv --file=backend/src/main/resources/preflight/learner-settings-v9.sql
```

`lingolens_preflight` là service PostgreSQL do người vận hành cấu hình để trỏ đúng database, dùng tài khoản chỉ có SELECT và phương thức quản lý credential hiện có. Không đưa mật khẩu vào command line/report. Không cài thêm công cụ trong project. Nếu môi trường không dùng service file, thay tham số kết nối theo cấu hình vận hành đã xác minh.

Script mở transaction `READ ONLY`, kiểm tra rồi COMMIT. Chạy trước deploy, lưu kết quả và chạy lại sát thời điểm migration. Script không sửa dữ liệu, không kiểm tra hoặc xuất email/password/token. PostgreSQL cưỡng chế từ chối ghi trong transaction này.

| Cột kết quả | Ý nghĩa |
| --- | --- |
| user_id | ID dòng cần xử lý |
| target_level | Giá trị cũ, kể cả NULL |
| daily_goal | Giá trị cũ, kể cả NULL |
| errors | TARGET_LEVEL_NULL/UNSUPPORTED, DAILY_GOAL_NULL/OUT_OF_RANGE; nhiều lỗi nối bằng `; ` |

Mức hợp lệ **A1/B1/B2** đúng chữ hoa, không tự trim hoặc chuyển mức. Goal hợp lệ **1–50**. `daily_goal` V8 hiện NOT NULL; vẫn kiểm tra NULL để phát hiện schema drift. Không sửa schema đích để chạy preflight.

**Có bất kỳ dòng kết quả nào thì dừng deploy và báo chủ dự án.** Lệnh SQL thành công không có nghĩa dữ liệu hợp lệ: exit code 0 là truy vấn chạy thành công, người vận hành phải kiểm tra số dòng findings. Lỗi kết nối/quyền/schema làm lệnh thất bại qua ON_ERROR_STOP. Không dùng exit code 0 một mình làm deployment gate tự động.

Không tự biến giá trị lỗi thành A1/5. Khi được phép viết V9, migration vẫn phải tự kiểm tra và dừng với thông báo rõ nếu còn dòng lỗi; báo cáo preflight không thay thế kiểm tra đó.

## Fixture và phạm vi bằng chứng

`LearnerSettingsPreflightTests` chạy SQL trên Testcontainers PostgreSQL 15, migrate đúng V8. Fixture hợp lệ: A1/1, B1/8, B2/50. Fixture lỗi: C1/0, NULL/51, B2/-1, b1/5, khoảng trắng trước A1/5. Một ca riêng mô phỏng `daily_goal` NULL bằng cách nới NOT NULL **chỉ trong container test**.

So sánh snapshot tất cả bảng public trước/sau chạy SQL, gồm users/password, roles/user_roles, refresh_tokens, learning_entries và tombstone, review_cards/events, sync_receipts và Flyway history. Có test PostgreSQL từ chối UPDATE với SQLSTATE 25006 trong transaction của script.

Đây là kiểm chứng preflight chỉ đọc, **chưa phải kiểm chứng nâng cấp V8 → V9**. Backfill, rollback migration, mirror transaction, DTO profile/register/admin và A1 validation sẽ được test trước khi viết V9/adapter sau khi chủ dự án xác nhận checkpoint này.
