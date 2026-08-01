# Tài Liệu Đặc Tả Yêu Cầu Hệ Thống - LingoLens

Tài liệu này đặc tả các yêu cầu chức năng (functional requirements), yêu cầu phi chức năng (non-functional requirements) và yêu cầu bảo mật (security requirements) cho dự án LingoLens.

---

## 1. Các Tác Nhân Trong Hệ Thống (Actors)

### 1.1. Actor: Người dùng (User / Learner)
Là đối tượng sử dụng ứng dụng di động Android để học tiếng Anh qua hình ảnh. Các chức năng dành cho User gồm:
- **Tài khoản:** Đăng ký tài khoản mới, Đăng nhập hệ thống, Thay đổi mật khẩu, Chỉnh sửa hồ sơ cá nhân (Tên, Trình độ mục tiêu), Đăng xuất và Yêu cầu xóa tài khoản.
- **Camera & AI:** Quét vật thể xung quanh qua Camera hoặc chọn từ ảnh chụp; Xem kết quả nhận diện tự động từ mô hình AI cục bộ (ML Kit).
- **Học tập:** Xem định nghĩa từ vựng, phiên âm, từ loại, phát âm chuẩn (Text-to-Speech) và ví dụ phân cấp độ B1 - B2 tương ứng với trình độ mục tiêu.
- **Hiệu chỉnh:** Sửa lại nhãn nhận diện khi AI nhận dạng sai (gửi phản hồi/báo cáo lỗi lên hệ thống).
- **Sổ tay từ vựng:** Lưu từ vựng đã quét, tìm kiếm từ vựng, lọc danh sách từ vựng theo từ loại (Danh từ, Động từ, Tính từ...) và Xóa từ vựng khỏi sổ tay.
- **Trò chơi ôn tập:** Chơi game ghép từ (Matching Game) được tạo ngẫu nhiên từ bộ từ vựng đã học hoặc các từ thường sai để ôn tập. Xem lại lịch sử và điểm số.
- **Thống kê:** Xem thống kê tiến độ học tập (Tổng từ đã học, số từ học hôm nay, chuỗi ngày học liên tục - Streak, tỷ lệ trả lời game chính xác).

### 1.2. Actor: Quản trị viên (Admin)
Là đối tượng sử dụng ứng dụng Web Dashboard để quản trị nội dung và giám sát hệ thống. Các chức năng của Admin gồm:
- **Hệ thống:** Đăng nhập trang quản trị.
- **Giám sát:** Xem bảng số liệu thống kê toàn hệ thống (Tổng user, số lượt quét vật thể, số lượt chơi game, thống kê các vật thể nhận diện sai nhiều nhất).
- **Quản lý người dùng:** Xem danh sách tài khoản, Khóa / Mở khóa tài khoản người dùng vi phạm.
- **Quản lý từ điển (CRUD Vocabulary):** Thêm, Sửa, Xóa, Tìm kiếm các từ vựng hệ thống hỗ trợ (Định nghĩa, ví dụ B1/B2, từ loại, phiên âm).
- **Xử lý phản hồi:** Xem danh sách báo cáo nhận dạng sai từ người dùng để hiệu chỉnh lại mô hình AI hoặc bổ sung từ điển.
- **Quản lý game:** Quản lý ngân hàng câu hỏi, nội dung và cấu hình của trò chơi ghép từ.

---

## 2. Yêu Cầu Chức Năng (Functional Requirements)

Hệ thống phải thực hiện đầy đủ các nhóm chức năng sau:
- **Authentication Module:** Đăng ký, đăng nhập bảo mật qua JWT. Cấp access token ngắn hạn và refresh token dài hạn để giữ phiên đăng nhập an toàn.
- **Vocabulary Module:** Cung cấp thông tin chi tiết từ vựng theo ngữ cảnh quét và tự động phân hóa các ví dụ mẫu phù hợp cho cấp độ B1 và B2.
- **Image Scanning & Recognition Module:** Hỗ trợ camera preview thời gian thực, chụp ảnh và xử lý nhận diện vật thể nhanh chóng.
- **Gamification Module:** Tạo phiên chơi game, ghi nhận đáp án của từng câu hỏi và tính điểm trực tiếp ở phía Backend để tránh gian lận.

---

## 3. Yêu Cầu Phi Chức Năng (Non-Functional Requirements)

- **Hiệu năng (Performance):** 
  - Tốc độ nhận diện vật thể offline trên thiết bị phải dưới 500ms.
  - Các API của Backend phải phản hồi dưới 1 giây trong điều kiện mạng bình thường.
- **Tính khả dụng (Usability):** Giao diện Jetpack Compose trên Android và React trên Web Admin phải trực quan, hỗ trợ chế độ tối (Dark Mode), tương thích với nhiều kích thước màn hình.
- **Khả năng ngoại tuyến (Offline Capabilities):** Ứng dụng di động phải có khả năng hiển thị danh sách từ vựng đã lưu cũ ngay cả khi không có kết nối internet nhờ cơ chế cache dữ liệu cục bộ.

---

## 4. Yêu Cầu Bảo Mật (Security Requirements)

- **Mật khẩu:** Mật khẩu của người dùng bắt buộc phải được mã hóa bằng thuật toán băm **BCrypt** trước khi lưu vào Database.
- **Xác thực API:** Mọi yêu cầu API gửi từ Client lên Server ngoại trừ đăng ký/đăng nhập phải mang kèm JWT Access Token hợp lệ trong Header `Authorization: Bearer <token>`.
- **Phân quyền (RBAC):** Backend áp dụng cơ chế Role-Based Access Control để kiểm tra quyền hạn (ví dụ: chỉ có ADMIN mới được gọi các API `/api/v1/admin/**`).
- **Phòng chống gian lận:** Điểm số và kết quả các trò chơi ôn tập phải được chấm điểm và kiểm tra logic ở phía Backend, không tin cậy hoàn toàn vào dữ liệu Client truyền lên.

---

## 5. Phạm Vi MVP (Minimum Viable Product) và Định Hướng Tương Lai

### 5.1. Phạm vi MVP (Sản phẩm tối thiểu 2 tháng)
- Luồng quét vật thể giả lập ➔ nhận diện offline ML Kit ➔ hiển thị từ điển B1/B2 ➔ lưu trữ đồng bộ database Spring Boot.
- Hệ thống Auth hoàn chỉnh JWT và phân quyền cơ bản.
- Game ghép từ cơ bản chấm điểm backend.
- Trang Web Admin cơ bản quản lý người dùng và từ vựng.

### 5.2. Các tính năng phát triển sau (Future Backlog)
- Lưu trữ ảnh vật thể trực tiếp lên đám mây Cloudinary/S3.
- Tính năng ôn tập lặp lại ngắt quãng (Spaced Repetition) nâng cao.
- Cộng đồng chia sẻ từ vựng và thách đấu game ghép từ trực tuyến.
