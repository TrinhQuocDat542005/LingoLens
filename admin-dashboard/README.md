# LingoLens Admin Console

Dashboard quản trị React kết nối trực tiếp với Spring Boot API.

## Chạy local

1. Sao chép `.env.example` thành `.env` nếu backend không chạy tại `http://localhost:8080`.
2. Chạy `npm install`.
3. Chạy `npm run dev` và mở `http://localhost:5173`.

Backend cần được khởi động với `ADMIN_EMAIL` và `ADMIN_PASSWORD` (ít nhất 12 ký tự) để tự tạo hoặc đồng bộ tài khoản quản trị đầu tiên. Không giữ hai biến này trong file được commit lên Git.
