# BÁO CÁO TIẾN ĐỘ TUẦN 2 — LINGOLENS

## Mục tiêu

Kết nối Android với Spring Boot, hoàn thiện xác thực JWT/refresh token, lưu phiên đăng nhập và áp dụng phân quyền `USER`/`ADMIN` phía backend.

## Công việc đã triển khai

### Backend

- Chuẩn hóa success/error response và mã lỗi nghiệp vụ.
- Đăng ký, đăng nhập, refresh token rotation, logout và logout toàn bộ thiết bị.
- Refresh token chỉ được lưu dưới dạng SHA-256 hash.
- JWT filter, response `401`, response `403` và RBAC cho `/api/v1/admin/**`.
- API đọc/cập nhật profile; client không thể cập nhật role hay trạng thái tài khoản.
- Migration Flyway bổ sung hồ sơ, trạng thái tài khoản và metadata phiên đăng nhập.
- Swagger/OpenAPI và integration test cho authentication flow.

### Android

- Retrofit, OkHttp và Gson cho REST API.
- DataStore lưu ciphertext; access/refresh token được mã hóa AES-GCM bằng khóa trong Android Keystore.
- Interceptor tự gắn Bearer token; Authenticator refresh tối đa một lần khi gặp `401`.
- Splash kiểm tra phiên, auth navigation và main navigation tách biệt.
- Màn hình đăng nhập/đăng ký có validation, loading và lỗi thân thiện.
- Settings hiển thị profile thật, cập nhật B1/B2 và đăng xuất.
- Không ghi token vào HTTP log; release không bật logging interceptor.

## Luồng demo

1. Chạy PostgreSQL và backend.
2. Mở app lần đầu để vào Login.
3. Đăng ký tài khoản mới.
4. Đăng nhập và tải `/users/me`.
5. Đóng/mở app để xác nhận khôi phục phiên.
6. Đổi B1/B2 trong Settings.
7. Đăng xuất và xác nhận quay về Login.
8. Dùng Swagger xác nhận user gọi admin API nhận `403`.

## Kiểm thử

- Backend: validation, duplicate email, login, profile, refresh rotation, logout, `401` và `403`.
- Android: email/password validation và password confirmation.

## Giới hạn tuần 2

CameraX thật, nhận diện vật thể, learned-words API, trò ghép từ và admin dashboard được giữ cho các tuần sau.
