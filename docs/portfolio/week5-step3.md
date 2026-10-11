# Tuần 5 — Bước 3: chấm bài thuần

Ngày 11/10/2026, nhánh riêng; chưa merge main. Sáu test thuật toán được viết trước LessonGrader, không database/HTTP/XP trong bước này.

So sánh đạt bài bằng số nguyên `100L * earned >= 70L * total`. Phần trăm hiển thị là chia nguyên làm tròn xuống. Kết quả có correctCount/questionCount riêng earnedWeight/totalWeight. Không tin điểm client; câu thiếu/trùng/ngoài revision hoặc option lạ bị từ chối.

| Kiểm tra | Kết quả mới |
| --- | --- |
| Android | 46 đạt |
| Backend test | 22 đạt = 16 regression + 6 grader |
| PostgreSQL | 25 đạt |
| Lint | 0 lỗi, 38 warnings, 0 warning mới |
| Admin build | Thành công, không phải test |

Test xác nhận đúng 70%, dưới/trên ngưỡng, 8 câu cần 6 đúng, trọng số 16/23 bị trượt dù làm tròn gần nhất có thể thành 70, hiển thị 69; 10/13 hiển thị 76 và đạt. Đảo thứ tự answers không đổi kết quả; marks có trọng số server.

Chạy lại đủ bộ như bước 1–2 với `--rerun`; backend hoàn tất khoảng 19:29:01 +07:00. Chưa triển khai API/progress/XP hoặc UI (bước 4–5).
