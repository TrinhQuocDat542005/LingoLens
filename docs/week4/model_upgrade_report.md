# BÁO CÁO NÂNG CẤP MODEL — EFFICIENTDET-LITE0

## Vấn đề baseline

Pipeline cũ chạy Image Labeling trên toàn ảnh. Model phân loại hơn 400 khái niệm nhưng không xác định vị trí vật thể, dễ ưu tiên bối cảnh và không thể tách nhiều vật thể trong cùng ảnh.

## Pipeline mới

```text
CameraX JPEG
  → decode + EXIF orientation + resize
  → brightness/sharpness quality gate
  → EfficientDet-Lite0 INT8
  → bounding boxes + COCO labels + confidence
  → LingoLens label normalization/filter
  → selectable overlay
  → ML Kit Image Labeling fallback if no supported box exists
  → manual word fallback
```

## Tính năng hoàn thành

- Model EfficientDet-Lite0 chính thức được bundle trong APK và chạy offline.
- Nhận diện tối đa 10 vật thể trong ảnh tĩnh.
- Vẽ bounding box đúng theo `ContentScale.Fit`, kể cả ảnh letterbox.
- Chạm vào bounding box để chọn vật thể muốn học.
- Hiển thị engine, số kết quả, confidence và inference time.
- Chuẩn hóa nhãn COCO (`cell phone`, `dining table`, `tv`) về từ LingoLens.
- Cảnh báo ảnh quá tối, dư sáng hoặc có khả năng bị mờ.
- ML Kit Image Labeling chỉ còn là fallback, không phải engine chính.
- Nhập/chọn từ thủ công vẫn tồn tại khi cả hai model không phù hợp.
- Model và ảnh không được gửi lên backend.

## Phạm vi lớp

Những lớp COCO phù hợp trực tiếp với LingoLens gồm `person` (không hiển thị), `bicycle`, `car`, `bird`, `cat`, `dog`, `backpack`, `bottle`, `cup`, `bowl`, `banana`, `apple`, `orange`, `chair`, `couch`, `bed`, `dining table`, `tv`, `laptop`, `mouse`, `keyboard`, `cell phone`, `book` và `clock`.

Các từ ngoài COCO như một số loại cây/hoa chi tiết có thể xuất hiện qua ML Kit fallback nhưng không có bounding box đáng tin cậy.

## Kiểm thử

- Unit test ánh xạ bounding box giữa ảnh và canvas.
- Unit test hit testing khi người dùng chạm vào box.
- Unit test quality thresholds.
- Unit test alias COCO và confidence policy.
- Android build, unit tests và lint phải thành công.
- Độ chính xác thực tế được đo trên điện thoại bằng protocol trong `docs/model-evaluation`.

## Giới hạn trung thực

- EfficientDet-Lite0 là model pretrained COCO, không phải model tự huấn luyện riêng cho LingoLens.
- Không thể tuyên bố precision/recall trên thiết bị trước khi hoàn thành bảng test vật lý.
- Custom fine-tuning chỉ có ý nghĩa sau khi thu thập và gắn bounding box cho dataset riêng; không tạo số liệu hoặc dataset giả để làm báo cáo.
