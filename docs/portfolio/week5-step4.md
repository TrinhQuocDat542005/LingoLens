# Tuần 5 — Bước 4: API, attempts, progress và XP

Ngày 11/10/2026, nhánh riêng; chưa merge main. Test quan trọng viết trước service/controller. Lần build đầu lỗi cú pháp `!!==`; test backend chưa chạy khi đó. Chỉ sửa khoảng trắng, không đổi hành vi; lượt đầy đủ sau đó đạt.

API authenticated: GET curriculum, POST enroll course, GET published lesson (snapshots/patterns/questions/options, không answer key), POST lesson-attempts, GET owner attempt result. Enrollment tối đa một ACTIVE, bài trước phải vượt để học bài sau. UI tuần 5 chỉ dùng bài đầu; chưa có giao diện PRACTICE.

Server chấm; một transaction ghi attempt/answers/progress/XP. Account lock dùng FOR NO KEY UPDATE trên PostgreSQL để tuần tự hóa cấp XP, riêng với last-write-wins profile. Advisory transaction lock theo UUID attempt xử lý cả UUID bị tranh giữa account. Hành vi PostgreSQL chỉ kiểm tra trong postgresTest; H2 kiểm tra auth/validation hợp đồng API, không gọi SQL khóa assessment.

Canonical hash gồm revision/mode/client timestamp/offline flag và answers sắp theo question ID; thứ tự field JSON/answer không quyết định hash. UUID cũ payload khác → 409; account khác → 404, không lộ payload/owner. Retry trả result đã ghi, không chấm theo vocabulary mới. Client timestamp audit không quyết định XP. Week 5 từ chối nộp offline flag; retry direct online không đi outbox.

XP event ID dùng attempt UUID đã thắng first-pass, source_key là lesson ID ổn định, UQ user/reason/source giữ đúng một thưởng 30. Vì event gắn UUID attempt, xpAwarded của receipt có thể đọc lại ổn định (30 cho attempt thắng, 0 cho attempt khác); XP tổng hiện tại nằm ở catalog riêng. Không cần thay schema/migration đã commit để giữ result ổn định. PRACTICE không progress/unlock/XP.

| Kiểm tra | Kết quả mới |
| --- | --- |
| Android | 46 đạt |
| Backend test/H2 + grader | 23 đạt |
| PostgreSQL | 33 đạt = 25 cũ + 8 attempt/API tests |
| Lint | 0 lỗi, 38 warnings, 0 warning mới |
| Admin TypeScript/Vite build | Thành công, không phải test |

Đủ bộ lệnh như các checkpoint trước, test `--rerun`. PostgreSQL kiểm chứng: trọng số/pass/count, LESSON thưởng một lần, cùng UUID replay dù đảo answers, đổi mode conflict, foreign ownership (service và authenticated HTTP), PRACTICE/fail không thưởng, hai ca concurrent bắt buộc (cùng UUID và khác UUID), và lỗi XP rollback cả attempt/answers/progress/XP rồi retry thành công. Lesson view không trả answer key và dùng published snapshot.

Chưa có UI bài đầu/SavedStateHandle (bước 5); chưa làm offline pack/outbox, camera, review scheduler, analytics hoặc tuần 6. Các partial index/guard migrations giữ nguyên.
