# BÁO CÁO TIẾN ĐỘ THỰC TẬP TỐT NGHIỆP - TUẦN 1
**Dự án:** LingoLens - Ứng dụng quét vật thể học tiếng Anh phân cấp độ
**Sinh viên thực hiện:** Nguyễn Quốc Đạt
**Thời gian hoàn thành:** Tuần 1 (Từ Ngày 1 đến Ngày 7)

---

## 1. Mục tiêu đã hoàn thành (Achieved Objectives)
- Khởi tạo thành công dự án Android Kotlin Jetpack Compose với Gradle Kotlin DSL (`build.gradle.kts`).
- Thiết lập cấu trúc thư mục dự án sạch sẽ chuẩn theo hướng MVVM/Clean Architecture (gồm các thư mục: `model`, `navigation`, `service`, `ui/screens`, `ui/theme`).
- Cấu hình file Gradle, đồng bộ hóa Version Catalog (`libs.versions.toml`) với các thư viện:
  - Jetpack Navigation Compose (cho việc chuyển màn hình)
  - CameraX & CameraX Compose (chuẩn bị cho quét camera ở tuần sau)
  - Firebase Android BoM (chuẩn bị cho tích hợp đám mây)
- Thiết lập bảng màu Curated Theme hiện đại (Sleek Indigo & Slate Dark) giúp tăng độ tương phản và tối ưu trải nghiệm người dùng (UX).
- Xây dựng hoàn chỉnh khung giao diện (UI skeleton) của 6 màn hình cốt lõi với dữ liệu giả lập (mock data):
  1. **HomeScreen:** Dashboard tiến độ, Streak học tập, biểu đồ tóm tắt nhanh và các từ học gần đây.
  2. **CameraScreen:** Giao diện kính ngắm camera, mô phỏng chụp ảnh và hiệu ứng phân tích AI (Loading 1.5 giây).
  3. **ResultScreen:** Hiển thị kết quả quét ("cat", "dog", "book", "laptop", "cup"), định nghĩa từ vựng, từ đồng nghĩa và câu ví dụ phân chia theo cấp độ B1 & B2.
  4. **MyWordsScreen:** Sổ tay từ vựng đã lưu, hỗ trợ tìm kiếm và lọc từ vựng theo từ loại (Danh từ, Động từ...).
  5. **StatsScreen:** Thống kê chi tiết, biểu đồ cột hoạt động học tập hằng tuần và tỉ lệ phân bố từ vựng theo cấp độ B1/B2.
  6. **SettingsScreen:** Cho phép người dùng chuyển đổi trình độ học tập (B1/B2), cấu hình nhắc nhở và thông tin tác giả đồ án.
- Thiết lập hệ thống Router Navigation với Bottom Navigation Bar điều hướng mượt mà giữa các màn hình chính (Home, Camera, MyWords, Stats, Settings).

---

## 2. Chi tiết các file được tạo mới & chỉnh sửa (Created & Modified Files)

- **Cấu hình hệ thống & Thư viện:**
  - `gradle/libs.versions.toml`: Thêm dependency cho Navigation, CameraX, và Firebase.
  - `app/build.gradle.kts`: Đồng bộ cấu hình và nạp các dependency cần thiết.
- **Mô hình dữ liệu (Data Models):**
  - [LearnedWord.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/model/LearnedWord.kt): Đại diện cho một từ vựng đã quét và lưu trữ (cấp độ B1/B2, ví dụ, từ đồng nghĩa, đường dẫn ảnh chụp...).
  - [User.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/model/User.kt): Lưu trạng thái học tập của người dùng (streak, mục tiêu cấp độ).
- **Điều hướng & Cấu trúc nền tảng:**
  - [Screen.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/navigation/Screen.kt): Định nghĩa các Route điều hướng.
  - [AppNavigation.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/navigation/AppNavigation.kt): Setup NavHost chính và thiết kế thanh Bottom Navigation Bar thông minh (tự động ẩn khi vào màn hình Camera/Result).
  - [MainActivity.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/MainActivity.kt): Khởi chạy ứng dụng và nạp hệ thống điều hướng `AppNavigation`.
- **Dịch vụ mock dữ liệu:**
  - [FakeDictionaryService.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/service/FakeDictionaryService.kt): Mô phỏng dịch thuật và định nghĩa phân cấp học tập cho các vật thể scan thử ("cat", "dog", "book", "laptop", "cup").
  - [FakeWordRepository.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/service/FakeWordRepository.kt): Quản lý cơ sở dữ liệu tạm thời trong bộ nhớ của ứng dụng để demo lưu/xóa từ vựng thời gian thực.
- **Màn hình UI:**
  - [HomeScreen.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/ui/screens/HomeScreen.kt)
  - [CameraScreen.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/ui/screens/CameraScreen.kt)
  - [ResultScreen.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/ui/screens/ResultScreen.kt)
  - [MyWordsScreen.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/ui/screens/MyWordsScreen.kt)
  - [StatsScreen.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/ui/screens/StatsScreen.kt)
  - [SettingsScreen.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/ui/screens/SettingsScreen.kt)
- **Giao diện & Theme:**
  - [Color.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/ui/theme/Color.kt)
  - [Theme.kt](file:///e:/AndroidProjects/ThucTapTotNghiep/LingoLens/app/src/main/java/com/quocdat/lingolens/ui/theme/Theme.kt)

---

## 3. Luồng kiểm tra & Demo sản phẩm (Demo Flow)
Khi cài đặt ứng dụng chạy trên máy ảo hoặc thiết bị thật, bạn có thể thực hiện kiểm thử luồng hoạt động chính:
1. **Khởi động:** Màn hình Home hiển thị dashboard học tập (Streak 5 ngày, mục tiêu đạt 3/5 từ).
2. **Kích hoạt quét:** Bấm nút **"Quét vật thể mới"** trên Home hoặc chuyển qua tab **Camera** trên Bottom Bar.
3. **Chụp hình & Phân tích:** Tại màn hình Camera giả lập, bạn có thể bấm chọn nhanh vật thể muốn quét ở khay bên dưới (ví dụ: `cat`, `dog`, `laptop`...). Sau đó bấm nút **Chụp**. 
4. **Phản hồi:** Hệ thống hiển thị màn hình Loading giả lập trong 1.5 giây để mô phỏng phân tích AI, sau đó tự động chuyển đến màn hình kết quả **ResultScreen**.
5. **Hiển thị học tập:** Màn hình Result hiển thị tên tiếng Anh, cách phát âm, dịch nghĩa tiếng Việt, định nghĩa đầy đủ, danh sách từ đồng nghĩa và 2 ví dụ phân cấp độ B1 - B2.
6. **Lưu từ vựng:** Bấm nút **"Lưu từ vựng"**. Hệ thống lưu từ vào database tạm thời và tự động dẫn bạn đến màn hình **MyWordsScreen** (Sổ tay). Bạn sẽ thấy từ vừa quét xuất hiện ở đầu danh sách.
7. **Tìm kiếm & Lọc:** Thử gõ tìm kiếm trên ô Search hoặc click bộ lọc từ loại (Noun, Verb...) để kiểm tra phản hồi mượt mà của giao diện.
8. **Thống kê:** Chuyển qua tab **Thống kê** để xem biểu đồ cột học tập và tỉ lệ phần trăm phân bố trình độ B1/B2 cập nhật tương ứng.
9. **Cấu hình mục tiêu:** Chuyển qua tab **Cài đặt**, click thay đổi Level từ B1 sang B2 để thấy thay đổi.

---

## 4. Kế hoạch Tuần 2 tiếp theo (Next Steps)
- Tích hợp luồng camera thực tế bằng **CameraX** (kết nối trực tiếp luồng camera preview vào Jetpack Compose).
- Tích hợp thư viện nhận dạng chữ viết/vật thể **Google ML Kit** offline trực tiếp trên thiết bị để phát hiện văn bản và vật thể thực tế.
