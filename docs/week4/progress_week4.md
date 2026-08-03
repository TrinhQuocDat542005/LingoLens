# BÁO CÁO TIẾN ĐỘ TUẦN 4 — NHẬN DIỆN VẬT THỂ OFFLINE

## Mục tiêu

Thay kết quả `cat` cố định bằng nhận diện ảnh thật trên thiết bị, hiển thị độ tin cậy và cung cấp fallback an toàn khi mô hình không nhận diện được hoặc không chắc chắn.

## Giải pháp kỹ thuật

LingoLens sử dụng ML Kit Image Labeling với model được bundle trong APK (`com.google.mlkit:image-labeling:17.0.9`). Model hỗ trợ hơn 400 nhãn tổng quát, hoạt động ngay lần đầu, không cần Firebase và không cần tải model qua mạng.

Pipeline:

```text
CameraX JPEG → FileProvider URI → ML Kit InputImage
             → danh sách label + confidence
             → ánh xạ bộ từ LingoLens
             → tự chọn hoặc yêu cầu người dùng xác nhận
```

ML Kit Object Detection mặc định chỉ phân loại thành các nhóm rộng như food, fashion goods, home goods, places và plants. Vì LingoLens cần tên cụ thể như `cat`, `dog`, `keyboard`, bản tuần 4 dùng Image Labeling cho ảnh tĩnh và giới hạn kết quả theo bộ từ mục tiêu.

## Công việc đã triển khai

### Nhận diện

- Model được đóng gói trong APK và chạy hoàn toàn offline.
- Result tự bắt đầu phân tích ảnh sau khi chụp.
- Trả nhãn gốc, từ chuẩn hóa và confidence.
- Lọc trùng và lấy tối đa 3 ứng viên thuộc bộ từ hỗ trợ.
- Confidence từ 65% trở lên được tự chọn; thấp hơn phải được người dùng xác nhận.
- Hiển thị tối đa 5 nhãn gốc để hỗ trợ kiểm thử model.
- Đóng tài nguyên `ImageLabeler` khi ViewModel bị hủy.

### Fallback và UX

- Trạng thái: chưa chạy, đang xử lý, thành công, không khớp và lỗi.
- Nếu AI không chắc, hiển thị các ứng viên cùng phần trăm confidence.
- Nếu không khớp, không tự gán `cat`; người dùng có thể tìm/chọn từ trong danh sách.
- Cho phép nhập từ tiếng Anh thủ công khi từ chưa nằm trong bộ hỗ trợ.
- Có thử lại, chụp lại và hủy.
- Ảnh không được tải lên backend.

### Bộ từ mục tiêu

30 từ/nhóm từ được hỗ trợ trong tuần 4:

`apple`, `backpack`, `banana`, `bicycle`, `bird`, `book`, `bottle`, `bread`, `cake`, `car`, `cat`, `chair`, `clock`, `computer`, `cup`, `dog`, `flower`, `food`, `keyboard`, `laptop`, `mouse`, `orange`, `pen`, `phone`, `plant`, `shoe`, `table`, `television`, `tree`, `watch`.

Các alias phổ biến như `mug → cup`, `mobile phone → phone`, `computer keyboard → keyboard` được chuẩn hóa trước khi hiển thị.

### Giao diện xác thực

- Thiết kế lại Login/Register để đồng bộ chất lượng với giao diện chính.
- Background gradient, logo LingoLens, card responsive và nội dung giới thiệu ngắn.
- Input có leading icon, màu chữ/con trỏ tương phản, trạng thái focus rõ.
- Loading, lỗi API, checkbox điều khoản và nút chuyển Login/Register được trình bày rõ ràng.
- Màn hình scroll và hỗ trợ bàn phím trên thiết bị nhỏ.

## Kiểm thử tự động

- Mapping nhãn chính xác và alias về từ chuẩn.
- Không đoán nhãn không được hỗ trợ.
- Confidence thấp yêu cầu xác nhận.
- Confidence cao có thể tự chọn.
- Thuật toán giảm kích thước ảnh vẫn được kiểm thử.
- `testDebugUnitTest`, `assembleDebug` và `lintDebug` là tiêu chí bắt buộc.

## Checklist trên điện thoại thật

- [ ] Chụp `cat`, `dog`, `book`, `cup` và một thiết bị điện tử trong điều kiện đủ sáng.
- [ ] Result tự chạy nhận diện, không còn trả `cat` cố định.
- [ ] Confidence và nhãn gốc hiển thị hợp lý.
- [ ] Chọn ứng viên khác làm thay đổi từ vựng bên dưới.
- [ ] Ảnh ngoài bộ từ mở được danh sách chọn thủ công.
- [ ] Nhập từ thủ công và lưu vào My Words.
- [ ] Tắt mạng sau khi đăng nhập; nhận diện ảnh vẫn chạy.
- [ ] Chụp ảnh mờ/tối; app không crash và có fallback.
- [ ] Xoay màn hình hoặc đưa app xuống nền trong lúc xử lý; app không crash.
- [ ] Login/Register hiển thị tốt ở dark mode và bàn phím không che nút chính.

## Giới hạn có chủ đích

- Đây là image classification/labeling cho ảnh tĩnh, chưa vẽ bounding box nhiều vật thể.
- Chất lượng phụ thuộc ánh sáng, góc chụp và tập nhãn của model tổng quát.
- Dictionary API và Text-to-Speech thật thuộc tuần 5; một số từ mới đang dùng nghĩa/ví dụ cơ bản cục bộ.
- Đánh giá model bằng bộ ảnh chuẩn và custom TFLite model có thể được bổ sung nếu độ chính xác thực tế chưa đạt yêu cầu đồ án.

## Tiêu chí hoàn thành

- [x] Ảnh thật được xử lý bởi model offline.
- [x] Có confidence, suggestions, no-match và error state.
- [x] Có fallback chọn/nhập từ thủ công.
- [x] Không còn kết quả `cat` cố định trong luồng camera.
- [x] Login/Register được thiết kế lại.
- [ ] Hoàn thành checklist nhận diện trên điện thoại thật và ghi lại kết quả từng vật thể.
