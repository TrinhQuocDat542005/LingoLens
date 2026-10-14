# Danh sách dọn dẹp portfolio

Các mục dưới đây mới được ghi nhận, chưa thực hiện. Không thay đổi code, migration hoặc schema Room.

- Thay đường dẫn riêng của máy trong tài liệu hướng dẫn (tên tài khoản Windows, ổ E:, vị trí Android Studio) bằng biến môi trường hoặc đường dẫn tương đối từ root repository. Dùng `$env:JAVA_HOME`, `$env:ANDROID_HOME` và hướng dẫn người đọc cấu hình theo máy của họ; đường dẫn backup dùng thư mục người dùng. Kiểm tra lại lệnh PowerShell, Markdown links và hướng dẫn Windows/Linux sau khi thay. Không đưa tên tài khoản hay đường dẫn máy người viết vào hướng dẫn dùng chung.

- Quét bí mật trong toàn bộ lịch sử git và các refs: khóa, JWT secret, mật khẩu thật, token, `.env`, keystore. Nếu có bí mật thật, báo loại bí mật và vị trí/commit để chủ dự án đổi hoặc thu hồi giá trị; không công khai giá trị bí mật trong báo cáo. Chưa quét, chưa xác nhận lịch sử sạch, không tự viết lại lịch sử.
- Rà cấu hình mẫu: `.env.example` và `application-dev` chỉ chứa giá trị dev được đánh dấu rõ. Kiểm tra JWT secret không để cứng giá trị có thể dùng cho production; kiểm tra cấu hình production yêu cầu secret riêng. Chưa sửa cấu hình.
- Hoàn thiện README: mô tả sản phẩm, ảnh chụp, video/GIF, sơ đồ kiến trúc, hướng dẫn Docker Compose cho Windows và Linux/macOS, tính năng và quyết định kỹ thuật. Ghi rõ nội dung DEMO, việc dùng AI hỗ trợ và giới hạn kiểm chứng; không quảng cáo phần chưa triển khai là đã có.
- Gom tài liệu: giữ đặc tả, kiến trúc, quyết định kỹ thuật và hướng dẫn chạy; chuyển báo cáo từng bước vào `docs/archive`, kiểm tra và sửa liên kết. Rà tham chiếu trong test/scripts trước khi di chuyển baseline, checksum hoặc fixture; chưa chuyển file.
- CI: huy hiệu liên kết đúng workflow/main; xác nhận bằng lượt chạy thật trên GitHub runner rằng PostgreSQL dùng Testcontainers và main xanh. Cấu hình workflow hoặc test local không thay cho bằng chứng CI; chưa chạy/xác nhận lượt CI mới.
- Releases: chuẩn bị APK kèm phiên bản và mã băm SHA-256, ghi rõ bản portfolio/demo và giới hạn. Không đưa khóa ký hoặc mật khẩu ký vào repo; chưa tạo release hoặc upload APK.
- Nhánh/PR: đề xuất tên dễ hiểu và thứ tự gộp theo phụ thuộc; chỉ gộp khi main build/test xanh và chủ dự án cho phép. Không tự tạo PR, merge, rebase/force-push hoặc viết lại lịch sử git.
- Rà `LICENSE` và `.gitignore`: chọn license với chủ dự án; kiểm tra loại trừ `build/`, `.idea/`, `local.properties`, file database cục bộ và sidecar WAL/SHM. Kiểm tra cả file đã tracked vì `.gitignore` không tự bỏ chúng khỏi git; chưa sửa license/ignore hoặc bỏ tracked files.
