# BÁO CÁO TIẾN ĐỘ THỰC TẬP TỐT NGHIỆP - TUẦN 1 (PHẦN BACKEND)
**Dự án:** LingoLens - Hệ thống học tiếng Anh phân cấp độ qua hình ảnh
**Công nghệ:** Spring Boot (Kotlin) + PostgreSQL + Spring Security + Flyway
**Sinh viên thực hiện:** Nguyễn Quốc Đạt
**Thời gian hoàn thành:** Tuần 1 (Tích hợp thêm kiến trúc tự dựng)

---

## 1. Kết quả đạt được (Key Deliverables)
- Khởi chạy thành công cơ sở dữ liệu **PostgreSQL 15** qua **Docker Compose** chạy độc lập trên máy chủ/cục bộ (`port 5432`).
- Thiết lập dự án **Spring Boot 3.4.0** viết hoàn toàn bằng **Kotlin 2.0.21** với Gradle Kotlin DSL.
- Tích hợp **Flyway Migration** để tự động quản lý phiên bản cơ sở dữ liệu.
- Thiết lập hệ thống bảo mật **Spring Security** mã hóa mật khẩu bằng **BCrypt** và cấu hình **Stateless JWT Filter** để xác định danh tính từ Header.
- Xây dựng thành công tính năng xác thực hoàn chỉnh bao gồm đăng ký, đăng nhập và tự động quay vòng token bằng cơ chế **Refresh Token**.
- Tích hợp thành công **Swagger/OpenAPI 3** giúp hiển thị giao diện mô tả API trực quan và cho phép kiểm thử trực tiếp từ trình duyệt.
- Tích hợp hệ thống **Spring Boot Actuator** để kiểm tra sức khỏe hệ thống.
- Xây dựng bộ **Kiểm thử tích hợp tự động (Integration Tests)** toàn diện kiểm tra các kịch bản xác thực và phân quyền (401, 403).

---

## 2. Thiết kế Cơ sở dữ liệu (Database Schema)
Các bảng dữ liệu được tạo tự động thông qua các file Flyway migration (`V1__create_users_and_roles.sql` và `V2__seed_roles.sql`):
1. **users:** Lưu thông tin tài khoản người dùng, mục tiêu học tập (B1/B2), và số ngày học liên tục (streak).
2. **roles:** Quản lý nhóm quyền hạn của dự án (mặc định chèn `ROLE_USER`, `ROLE_ADMIN`).
3. **user_roles:** Bảng trung gian liên kết nhiều-nhiều giữa tài khoản và quyền hạn.
4. **refresh_tokens:** Lưu trữ mã khóa refresh token, thời gian hết hạn để quản lý phiên đăng nhập lâu dài và thu hồi khi người dùng Log out.

---

## 3. Danh sách các API đã cung cấp (Exposed APIs)
Hệ thống chạy tại `http://localhost:8080` cung cấp các API endpoints:

### Xác thực & Phân quyền (Public):
- `POST /api/v1/auth/register`: Đăng ký tài khoản mới (Hệ thống tự động mã hóa BCrypt password trước khi lưu).
- `POST /api/v1/auth/login`: Xác thực tài khoản, tạo và trả về Access Token + Refresh Token cùng siêu dữ liệu người dùng.
- `POST /api/v1/auth/refresh`: Dùng Refresh Token hợp lệ để lấy Access Token mới (cơ chế xoay vòng token giúp chống đánh cắp).
- `POST /api/v1/auth/logout`: Hủy phiên đăng nhập, thu hồi và xóa Refresh Token khỏi Database.

### Quản lý thông tin (Được bảo mật bằng JWT):
- `GET /api/v1/users/me`: Trả về thông tin hồ sơ của tài khoản đang đăng nhập tương ứng với thông tin giải mã từ JWT Access Token gửi kèm trong Header.
- `GET /api/v1/admin/hello` (Yêu cầu quyền `ROLE_ADMIN`): Endpoint kiểm tra phân quyền quản trị viên.

### Kiểm tra sức khỏe & Sức mạnh hệ thống:
- `GET /actuator/health` (Công khai): Trả về trạng thái hoạt động của Server và kết nối Cơ sở dữ liệu (`{"status":"UP"}`).

---

## 4. Hướng dẫn chạy thử nghiệm & Kiểm thử (How to run & verify)

### Bước 1: Khởi động Database PostgreSQL
Mở Terminal ở thư mục gốc của dự án, chạy lệnh:
```powershell
docker-compose up -d
```

### Bước 2: Chạy kiểm thử tự động (Integration Tests)
Di chuyển vào thư mục `backend/` và chạy bộ test tự động:
```powershell
cd backend
.\gradlew test
```
*Hệ thống sẽ chạy toàn bộ các kịch bản kiểm tra đăng ký, đăng nhập, password yếu, trùng email, chặn token, chặn quyền truy cập admin (403).*

### Bước 3: Chạy ứng dụng Spring Boot
Khởi chạy máy chủ:
```powershell
.\gradlew bootRun
```
*Server sẽ khởi động thành công trên cổng `8080`.*

### Bước 4: Kiểm thử trên Swagger UI
Truy cập link: `http://localhost:8080/swagger-ui/index.html`
- **Đăng ký tài khoản:** Sử dụng endpoint `/api/v1/auth/register` truyền JSON gồm `email`, `password`, `name`.
- **Đăng nhập:** Gọi `/api/v1/auth/login` với tài khoản vừa đăng ký. Hệ thống sẽ trả về chuỗi `accessToken` (JWT).
- **Thử nghiệm bảo mật:** Copy chuỗi `accessToken`, click vào nút **Authorize** ở góc phải trên Swagger UI, paste token vào dưới dạng `Bearer <token>` ➔ Gọi thử API `/api/v1/users/me` (ROLE_USER được phép) và `/api/v1/admin/hello` (ROLE_USER bị từ chối với mã 403 Forbidden) để kiểm tra khả năng phân quyền và giải mã dữ liệu của Backend.
