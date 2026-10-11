# Tuần 6 — Bước 2: dừng khi PostgreSQL test lỗi

Ngày 11/10/2026, nhánh `feature/offline-lessons`. HEAD vẫn là `6234a03` (bước 1); bước 2 **chưa commit**. Dừng theo yêu cầu chủ dự án ngay khi phát hiện test fail. Chưa sửa nguyên nhân, chưa làm bước 3–5, chưa merge main.

## Phần đã chuẩn bị, chưa nghiệm thu

- Viết test trước cho cấu hình Room production, bộ vector chấm dùng chung, tải/kiểm tra pack, account isolation và fixture lớn.
- Chuyển hàm chấm thuần vào `shared/`; wrapper Android/backend dùng chung hàm và tài nguyên test vector.
- Thêm manifest/pack backend yêu cầu đăng nhập, pack có answer key; repository Android kiểm checksum, kích thước, cấu trúc và chỉ lưu pack hoàn chỉnh theo account.
- Fixture test có ba bài, mỗi bài sáu từ, mười câu, lựa chọn dài. Không đưa fixture này vào seed production.

Đây là thay đổi đang nằm trong working tree, không phải kết luận hoàn thành bước 2. Luồng pack PostgreSQL chưa được xác minh vì context không khởi tạo được; chưa nghiệm thu offline end-to-end.

## Room production và CI

`AppContainer` đăng ký MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4; không có destructive fallback trong nguồn Android main. Test mới `ProductionRoomConfigurationTest.production builder registers every migration without destructive fallback` kiểm tra builder và quét nguồn main để chặn fallback. Test đã đạt trong lượt Android mới.

Workflow hiện có `.github/workflows/ci.yml` chạy `testDebugUnitTest`, nên sẽ tự chạy test bảo vệ này khi thay đổi được commit/push. Hiện test chưa commit; chưa có kết quả GitHub Actions cho bước 2. Đây là kiểm tra tĩnh cấu hình, không thay thế test mở database cũ hoặc thử nâng cấp trên emulator.

## Kết quả các bộ kiểm tra đã khởi chạy

| Kiểm tra | Kết quả |
| --- | --- |
| Android unit/Robolectric | 62 tests, 0 failures/errors/skipped; đạt |
| Backend H2/unit | 24 tests, 0 failures/errors/skipped; đạt |
| PostgreSQL Testcontainers | 36 tests, 3 failures, 0 errors/skipped; không đạt. 33 test còn lại đạt |
| Lint mới | 0 errors, 38 warnings, baseline 38, 0 warning mới; gate đạt |
| Admin TypeScript/Vite | `npm run build` thành công; đây là build, không tính là test |

Android/lint Gradle kết thúc `BUILD SUCCESSFUL`; lintAnalyzeDebug và lintReportDebug thực thi, không lấy báo cáo cũ. XML lint có LastWriteTime **11/10/2026 22:38:05 UTC+07:00**. `Assert-LintBaseline.ps1` trả `errors=0, warnings=38, baseline=38, new=0` sau khi Gradle kết thúc.

Backend invocation chạy unit test trước postgresTest và kết thúc exit 1 tại postgresTest. Báo cáo XML:

- `app/build/test-results/testDebugUnitTest`
- `backend/build/test-results/test`
- `backend/build/test-results/postgresTest/TEST-com.quocdat.lingolens.curriculum.ContentPackPostgresTests.xml`

## Ba test lỗi và nguyên nhân

| Test trong ContentPackPostgresTests | Kết quả thực tế |
| --- | --- |
| `manifest has three stable immutable packs including answer keys()` | Spring context lỗi khi Flyway khởi tạo; chưa chạy assertions |
| `large format fixture retains ten questions long labels and six words in each lesson()` | Spring bỏ lần khởi tạo context tiếp theo vì vượt failure threshold; chưa chạy assertions |
| `pack endpoint requires login and unpublished revisions are rejected()` | Cùng failure threshold; chưa chạy assertions |

Nguyên nhân gốc ghi trong XML: `Driver org.h2.Driver claims to not accept jdbcUrl, jdbc:postgresql://localhost:58747/test?loggerLevel=OFF`.

Class test mới override URL/username/password từ Testcontainers nhưng thiếu override `spring.datasource.driver-class-name`; profile `application-test.yml` vẫn khai báo `org.h2.Driver`. Vì vậy ba failure chưa chứng minh pack sai hành vi, nhưng cũng chưa có bằng chứng PostgreSQL cho pack mới.

Đề xuất khi được cho tiếp tục: sửa **cấu hình class postgresTest mới** để khai báo `org.postgresql.Driver`, rồi chạy lại đầy đủ bộ kiểm tra trước commit bước 2. Chưa áp dụng đề xuất; không đổi SQL production hoặc hợp đồng đã duyệt.

## Bảo toàn và điểm dừng

Kiểm tra SHA-256 theo inventory bước 0 đạt: V1–V12 và Room JSON v1–v3 không đổi. Room JSON v4 có git hash bằng bản ở HEAD bước 1. Không thêm thư viện/dịch vụ. Giữ nguyên thay đổi IDE của chủ dự án, không đưa vào commit.

Chưa có commit bước 2 và chưa bắt đầu bước 3–5. Chờ chủ dự án cho tiếp tục sau báo cáo lỗi này. DEMO chỉ dùng nghiệm thu kỹ thuật; gate nội dung REVIEWED trước tuần 8 vẫn giữ nguyên.

## Lệnh cho chủ dự án thử nâng cấp trên emulator

Chưa chạy các lệnh này trên emulator của chủ dự án. APK từ working tree hiện tại bao gồm phần bước 2 chưa nghiệm thu; để chỉ thử migration bước 1 đã đạt, dùng bản tại commit `6234a03` trong checkout sạch. Không checkout/reset working tree đang có thay đổi chỉ để chạy thử.

### Build APK từ nhánh hiện tại

```powershell
cd E:\Projects\LingoLens
git branch --show-current # phải là feature/offline-lessons
$env:JAVA_HOME = 'E:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/ASUS/AppData/Local/Android/Sdk'
$adbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
.\gradlew.bat :app:assembleDebug -PapiBaseUrl=http://10.0.2.2:8080/ --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build APK thất bại' }
```

### Sao lưu database app debug trước cài đè

```powershell
& $adbPath devices # emulator-5554 phải có trạng thái device
$backupDir = Join-Path $env:USERPROFILE 'LingoLens-backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$backupFile = Join-Path $backupDir ('week5-database-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.tar')
& $adbPath -s emulator-5554 shell am force-stop com.quocdat.lingolens
& $adbPath -s emulator-5554 shell run-as com.quocdat.lingolens mkdir -p files
& $adbPath -s emulator-5554 shell run-as com.quocdat.lingolens tar -cf files/week5-database.tar databases
if ($LASTEXITCODE -ne 0) { throw 'Không tạo được backup database debug' }
$backupBase64 = & $adbPath -s emulator-5554 exec-out run-as com.quocdat.lingolens base64 files/week5-database.tar
if ($LASTEXITCODE -ne 0) { throw 'Không đọc được backup từ emulator' }
[IO.File]::WriteAllBytes($backupFile, [Convert]::FromBase64String(($backupBase64 -join '')))
Get-Item $backupFile
```

Sao lưu cả thư mục databases, gồm database và WAL/SHM nếu có. Đây không phải backup toàn bộ app: không gồm DataStore/token/ảnh. Dùng base64 để tránh PowerShell làm hỏng dữ liệu nhị phân khi redirect stdout.

### Cài đè, không gỡ app

```powershell
& $adbPath -s emulator-5554 install -r 'app/build/outputs/apk/debug/app-debug.apk'
if ($LASTEXITCODE -ne 0) { throw 'Cài đè thất bại; giữ app cũ, không uninstall' }
& $adbPath -s emulator-5554 shell am start -n com.quocdat.lingolens/.MainActivity
```

APK phải cùng applicationId và cùng debug signing key với bản đang cài. Sau cài đè kiểm tra tài khoản, từ đã lưu, review/session dở và outbox cũ. Migration test Robolectric đã đạt không thay thế xác nhận nâng cấp trên emulator đang có dữ liệu.
