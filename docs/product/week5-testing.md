# Tuần 5 — Chạy dev và test tay bài đầu

Phạm vi: nhánh `codex/week5/step1-settings`, bước 1–5. **Chưa nghiệm thu bước 6, chưa merge main.** DEMO chỉ ở dev/test; không sử dụng các lệnh seed này cho production.

## 1. Backend dev và seed

Mở PowerShell ở root repo. Docker Desktop cần hoạt động; dùng JDK21 từ Android Studio. Các giá trị dưới đây là cấu hình dev mặc định của repo. Nếu volume dev đã dùng credential khác, dùng đúng credential hiện có.

```powershell
Set-Location E:\Projects\LingoLens
$env:JAVA_HOME = 'E:/Program Files/Android/Android Studio/jbr'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/lingolens'
$env:DB_USERNAME = 'lingolens'
$env:DB_PASSWORD = 'lingolens_password'
docker compose up -d postgres
docker compose ps postgres
Set-Location backend
.\gradlew.bat seedDemo --console=plain
```

Chờ PostgreSQL healthy trước seed. Runner migrate V1–V12 và nhập gói một lần. Kết quả mong đợi: `DEMO pack imported once`. Chạy lại chính lệnh `seedDemo` phải trả `DEMO pack already imported; unchanged`, không tăng nội dung. Có thể dùng `-PseedFile=C:/path/to/pack.json` khi cần nhập file tường minh; không sửa file/pack UUID đã nhập.

Trong terminal này, chạy server:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=dev' --console=plain
```

Backend ở `http://localhost:8080`. Nếu backend LingoLens từ Docker Compose đang chiếm 8080, dừng riêng service đó ở root bằng `docker compose stop backend`, rồi chạy server Gradle. Không dừng các service của project khác. Nếu 8080 do ứng dụng khác dùng, có thể chạy `--server.port=8081`, đồng thời build Android với `-PapiBaseUrl=http://10.0.2.2:8081/` (emulator) hoặc `http://127.0.0.1:8081/` và `adb reverse tcp:8081 tcp:8081` (USB).

Production/prod kể cả bật kèm dev sẽ bị runner từ chối; server production từ chối startup nếu còn DEMO trong receipts hoặc course/revision. Không tự chuyển DEMO thành REVIEWED. V9 dừng khi legacy settings không hợp lệ; báo lỗi cho chủ dự án, không tự reset/đổi B1/B2 hoặc xóa database. Theo quyết định hiện tại, chưa chạy preflight trên database vận hành; trước triển khai thật phải làm theo [preflight](week5-preflight.md).

## 2. Cài trên emulator

Mở terminal PowerShell thứ hai, giữ backend chạy ở terminal thứ nhất:

```powershell
Set-Location E:\Projects\LingoLens
$env:JAVA_HOME = 'E:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/ASUS/AppData/Local/Android/Sdk'
$adbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$emulatorPath = Join-Path $env:ANDROID_HOME 'emulator/emulator.exe'
& $emulatorPath -list-avds
```

Máy hiện có AVD `Medium_Phone`. Mở bằng Android Studio Device Manager, hoặc chạy lệnh sau trong một terminal riêng để không chặn các lệnh tiếp theo:

```powershell
& 'C:/Users/ASUS/AppData/Local/Android/Sdk/emulator/emulator.exe' -avd Medium_Phone
```

Quay lại terminal thứ hai, chờ emulator boot:

```powershell
& $adbPath devices
.\gradlew.bat :app:assembleDebug --console=plain
& $adbPath install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adbPath shell am start -n com.quocdat.lingolens/.MainActivity
```

APK debug mặc định gọi `http://10.0.2.2:8080/`, địa chỉ emulator tới máy host. `install -r` cập nhật app, không yêu cầu clear dữ liệu. Nếu ADB có nhiều thiết bị, thêm `-s <serial>` sau `$adbPath` cho từng lệnh install/shell; serial lấy từ `adb devices`.

## 3. Cài trên điện thoại qua USB

Bật Developer options/USB debugging, cắm cáp và chấp nhận khóa máy tính. Dùng terminal thứ hai đã đặt JAVA_HOME/ANDROID_HOME:

```powershell
& $adbPath devices
& $adbPath reverse tcp:8080 tcp:8080
.\gradlew.bat :app:assembleDebug -PapiBaseUrl=http://127.0.0.1:8080/ --console=plain
& $adbPath install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adbPath shell am start -n com.quocdat.lingolens/.MainActivity
```

Đây là endpoint debug qua USB reverse, không phải cấu hình release. Khi quay về emulator, build lại với `-PapiBaseUrl=http://10.0.2.2:8080/`. Ngắt mạng để test bằng Wi-Fi/mobile data khi không dùng USB reverse, hoặc tạm tắt kết nối tới backend (dừng backend rồi bật lại); USB reverse vẫn có thể tới host dù tắt Wi-Fi, nên tắt Wi-Fi một mình không chứng minh request mất kết nối trên điện thoại dùng USB.

## 4. Kịch bản test tay theo thứ tự

1. **Đăng ký/đăng nhập:** tạo account mới, hoặc đăng nhập account dev cũ. Account mới có A1/goal 5; account B1/B2 cũ phải giữ mức cũ. Vào Trang chủ → Mở bài học. Lộ trình có nhãn DEMO; XP account mới là 0.
2. **Mở bài đầu:** chọn Học bài đầu. Xem sáu từ và mẫu câu. Hiện chỉ mở luồng UI bài đầu; chưa kiểm tra bài 2/3 như tính năng đã hoàn tất.
3. **Đúng/sai:** bấm Bắt đầu trả lời, chọn từng đáp án; lựa chọn có dấu ✓, Câu trước giữ lựa chọn cũ. Thử để trống một câu: không thể tiếp tục câu đó. Cho lần đầu, chọn đúng sáu câu đầu và sai hai câu cuối.
4. **Nộp:** ở cuối có 8/8 câu đã trả lời; Nộp bài. Bấm nhanh nhiều lần phải không gửi thêm khi đang xử lý. Gói DEMO trọng số bằng nhau; 6/8 đạt, 5/8 trượt.
5. **Tổng kết:** lần vượt đầu hiển thị `6/8 câu đúng`, đã đạt, `+30 XP`, tổng XP 30. Không dùng phần trăm đã làm tròn để quyết định pass. Lượt trượt không thưởng.
6. **Học/nộp lại cùng bài:** chọn Học lại bài này, trả lời và nộp lần mới. UUID mới cho attempt mới nhưng cùng lesson không thưởng lần hai: `+0 XP`, tổng vẫn 30. Đăng nhập account khác phải có tổng XP riêng.
7. **Xoay màn hình:** ở lần học mới, trả lời 2–3 câu rồi xoay ngang/dọc. Câu hiện tại và lựa chọn còn nguyên. Tiếp tục nộp; không tạo thêm XP. Có thể xoay lúc đang nộp, rồi gửi lại nếu chưa xác nhận.
8. **Mất kết nối lúc nộp:** chuẩn bị đủ answers, ngắt kết nối thật tới backend rồi nộp. App báo chưa xác nhận, cho gửi lại; không cho thay answers của payload đã gửi. Bật kết nối, bấm Gửi lại để xác nhận. Kết quả về đúng một attempt, không thưởng lặp. Không tự động đưa attempt vào outbox.

Đáp án DEMO bài đầu theo **nhãn**, tránh phụ thuộc vị trí: xin chào; tạm biệt; làm ơn; cảm ơn; vâng; không; xin chào; tạm biệt. Sáu câu đầu đúng + hai cuối chọn nhãn khác → 6/8.

Để kiểm tra hệ thống thu hồi process thêm: khi app ở background, dùng `adb shell am kill com.quocdat.lingolens`, rồi mở lại từ Recents. SavedStateHandle bảo vệ state hệ thống đã lưu; không dùng force-stop, clear data hoặc bỏ task để kết luận có durable session. Không quảng cáo đây là session Room/offline pack của tuần 6.

## 5. Kiểm tra tự động và giới hạn

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun :app:lintDebug --max-workers=2 --console=plain
.\scripts\quality\Assert-LintBaseline.ps1
Push-Location backend
.\gradlew.bat test --rerun postgresTest --rerun --max-workers=2 --console=plain
Pop-Location
Push-Location admin-dashboard
npm run build
Pop-Location
```

JDK21 và Docker cần hoạt động. Android/H2/PostgreSQL là test; admin TypeScript/Vite, APK và bootJar là build. Partial index, concurrency, khóa, transaction chỉ đọc được kiểm chứng trên PostgreSQL thật; H2 không thay thế các bằng chứng đó.

Chưa có nội dung REVIEWED, UI PRACTICE, UI bài 2/3 hoàn chỉnh, offline lesson/outbox, content pack hoặc resume bền vững. Chưa làm TTS dạng bài mới, camera–topic, analytics, xóa tài khoản/release gate hay tuần 6+. Không thay review scheduler. Test lifecycle dùng Robolectric, không thay test tay trên thiết bị thật. Bước 6 chờ chủ dự án test xong.
