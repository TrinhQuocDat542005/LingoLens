# BÁO CÁO TIẾN ĐỘ TUẦN 3 — CAMERAX VÀ CHỤP ẢNH THẬT

## Mục tiêu

Thay luồng camera giả bằng CameraX, xử lý quyền camera đúng vòng đời, chụp ảnh vào bộ nhớ tạm và truyền ảnh an toàn sang màn hình Result. Nhận diện AI thật nằm trong phạm vi tuần 4.

## Công việc đã triển khai

### CameraX

- Camera preview thật bằng `PreviewView` và CameraX `Preview`.
- Bind `Preview` và `ImageCapture` theo `LifecycleOwner`; tự giải phóng use case khi rời màn hình.
- Chụp ưu tiên độ trễ thấp, khóa nút chụp trong lúc xử lý và hiển thị các trạng thái `OpeningCamera`, `Ready`, `Capturing`, `Captured`, `Error`.
- Chuyển camera trước/sau nếu thiết bị hỗ trợ.
- Bật/tắt torch; nút flash tự vô hiệu hóa trên camera không có flash.
- Thông báo lỗi và cho phép thử mở camera lại.

### Quyền và quyền riêng tư

- Khai báo `CAMERA` permission và camera hardware ở chế độ không bắt buộc để ứng dụng vẫn cài được trên thiết bị không có camera phù hợp.
- Giải thích lý do cần camera trước khi yêu cầu quyền.
- Xử lý cấp quyền, từ chối, từ chối vĩnh viễn và mở App Settings.
- Tự kiểm tra lại quyền khi ứng dụng resume từ Settings.
- Không xin quyền đọc/ghi toàn bộ bộ nhớ.

### Lưu và hiển thị ảnh

- Ảnh JPEG được lưu trong `cacheDir/captured_images` với UUID.
- Chia sẻ URI bằng `FileProvider`; không truyền đường dẫn file trực tiếp và không truyền `Bitmap` qua Navigation.
- Tự dọn ảnh cache quá 24 giờ và xóa ảnh hiện tại khi người dùng hủy, chụp lại hoặc lưu kết quả.
- Decode ảnh ngoài main thread, giảm kích thước ảnh lớn để hạn chế tràn bộ nhớ.
- Đọc EXIF orientation và xoay ảnh trước khi hiển thị.
- Result có trạng thái ảnh sẵn sàng, nút phân tích mẫu, chụp lại, hủy và lưu từ.

## Luồng demo

1. Đăng nhập và chọn **Quét vật thể mới** hoặc tab Camera.
2. Cấp quyền camera; nếu đã từ chối vĩnh viễn, mở Settings và bật lại.
3. Đặt vật thể vào khung, thử flash hoặc chuyển camera.
4. Chụp ảnh và chờ chuyển sang Result.
5. Kiểm tra ảnh đúng chiều, nhấn **Phân tích vật thể** để nhận kết quả mẫu `cat`.
6. Chọn **Chụp lại**, **Hủy** hoặc **Lưu từ**.

## Kiểm thử tự động

- Unit test kiểm tra thuật toán chọn `inSampleSize` cho ảnh ngang, ảnh dọc và ảnh kích thước nhỏ.
- `testDebugUnitTest` chạy thành công.
- `assembleDebug` tạo APK debug thành công.

## Checklist kiểm thử trên thiết bị thật

- [ ] Cấp quyền camera lần đầu và preview xuất hiện.
- [ ] Từ chối quyền rồi yêu cầu lại.
- [ ] Từ chối vĩnh viễn, mở Settings, cấp quyền và quay lại app.
- [ ] Chụp ảnh dọc; Result hiển thị đúng chiều.
- [ ] Chụp liên tiếp qua luồng Chụp lại, không crash và không dùng lại ảnh cũ.
- [ ] Camera trước và sau hoạt động nếu thiết bị có cả hai.
- [ ] Flash bật/tắt trên camera hỗ trợ; camera không hỗ trợ không crash.
- [ ] Chuyển app xuống nền rồi quay lại, camera tiếp tục hoạt động.
- [ ] Camera → Result → Chụp lại → Camera hoạt động đúng.
- [ ] Hủy ảnh về Home; ảnh tạm được xóa.
- [ ] Đăng xuất sau khi rời luồng camera vẫn quay về Login.
- [ ] Từ chối quyền camera không làm crash ứng dụng.

## Giới hạn có chủ đích

- Nút phân tích trả dữ liệu mẫu để kiểm chứng riêng luồng camera. Custom TFLite/ML Kit recognition được tích hợp ở tuần 4.
- Ảnh không được tải lên backend và không được lưu vào thư viện ảnh của người dùng.
- Kết quả từ điển và phát âm vẫn là mock theo roadmap; Dictionary API và TTS thuộc tuần 5.

## Tiêu chí hoàn thành

- [x] CameraX preview và ImageCapture được triển khai.
- [x] Permission flow và App Settings fallback được triển khai.
- [x] Ảnh cache được truyền bằng content URI sang Result.
- [x] Có chụp lại, hủy, flash, đổi camera, loading và error state.
- [x] Build và unit test thành công.
- [ ] Hoàn tất checklist trên ít nhất một điện thoại Android thật.
