# Tuần 6 — Cài bản bước 6 và test tay

Nhánh `feature/offline-lessons`. Đây là hướng dẫn **chủ dự án kiểm tra**, chưa phải xác nhận đã chạy trên thiết bị. Chỉ nghiệm thu kỹ thuật bằng DEMO; nội dung phải được rà soát độc lập thành REVIEWED trước tuần 8. Bước 7, PR và merge vẫn chờ kết quả test tay.

## 1. Chạy backend dev có DEMO

Docker Desktop phải chạy. Các credential dưới đây là mặc định **dev** trong repo; nếu volume đã dùng credential khác, dùng credential của volume đó. Không xóa volume hoặc reset dữ liệu để chạy hướng dẫn.

```powershell
Set-Location E:\Projects\LingoLens
git branch --show-current
$env:JAVA_HOME = 'E:/Program Files/Android/Android Studio/jbr'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/lingolens'
$env:DB_USERNAME = 'lingolens'
$env:DB_PASSWORD = 'lingolens_password'
docker compose up -d postgres
docker compose ps postgres
docker compose stop backend
Set-Location backend
.\gradlew.bat seedDemo --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Seed failed; inspect the error before continuing' }
.\gradlew.bat bootRun --args='--spring.profiles.active=dev' --console=plain
```

Chờ PostgreSQL healthy trước seed. Runner áp dụng migration hiện có đến V13; seed nhập một lần theo checksum. Đã nhập thì báo `DEMO pack already imported; unchanged`. Không sửa gói đã nhập hoặc đổi DEMO thành REVIEWED để qua guard. Runner từ chối prod/production, server production cũng kiểm DEMO lúc khởi động. Nếu preflight/migration báo dữ liệu lỗi, dừng và báo chủ dự án. Preflight trên database mục tiêu trước deploy thật vẫn chưa thực hiện.

API: `http://localhost:8080`. Kiểm tra từ một PowerShell khác:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 2. Sao lưu database debug trước khi cài đè

Giữ backend ở terminal trước; mở PowerShell thứ hai. Không gỡ app, không clear data. Bản sao chứa database và WAL/SHM nếu có; không sao lưu DataStore/token, Android Keystore hoặc ảnh camera. `run-as` chỉ dùng được với app debug. Nếu lệnh lỗi, dừng trước khi install.

```powershell
Set-Location E:\Projects\LingoLens
$env:JAVA_HOME = 'E:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/ASUS/AppData/Local/Android/Sdk'
$adbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$serial = 'emulator-5554'
& $adbPath devices
# Thiết bị phải là device; nếu unauthorized, xử lý quyền ADB trước.
$backupDir = Join-Path $env:USERPROFILE 'LingoLens-backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$backupFile = Join-Path $backupDir ('before-week6-step6-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.tar')
& $adbPath -s $serial shell am force-stop com.quocdat.lingolens
& $adbPath -s $serial shell run-as com.quocdat.lingolens mkdir -p files
& $adbPath -s $serial shell run-as com.quocdat.lingolens tar -cf files/week6-backup.tar databases
if ($LASTEXITCODE -ne 0) { throw 'Database archive failed' }
$backupBase64 = & $adbPath -s $serial exec-out run-as com.quocdat.lingolens base64 files/week6-backup.tar
if ($LASTEXITCODE -ne 0) { throw 'Database export failed' }
[IO.File]::WriteAllBytes($backupFile, [Convert]::FromBase64String(($backupBase64 -join '')))
Get-Item -LiteralPath $backupFile
Get-FileHash -LiteralPath $backupFile -Algorithm SHA256
```

Force-stop trước tar để không sao chép database đang ghi. Xuất base64 tránh hỏng dữ liệu nhị phân do chuyển hướng PowerShell. Backup không thay kiểm chứng migration trên thiết bị; giữ nguyên bản cài cũ để kiểm tra 3→4.

## 3. Build APK mới, cài đè và mở app

APK bước 6 đã build nằm ở `app/build/outputs/apk/debug/app-debug.apk`. Có thể build lại từ nhánh này:

```powershell
Set-Location E:\Projects\LingoLens
.\gradlew.bat :app:assembleDebug -PapiBaseUrl=http://10.0.2.2:8080/ --max-workers=2 --console=plain
if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
Get-FileHash .\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
& $adbPath -s $serial install -r .\app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed; keep existing app data and inspect signing/version errors' }
& $adbPath -s $serial shell am start -n com.quocdat.lingolens/.MainActivity
```

`install -r` giữ app data, applicationId/signing key debug phải tương thích bản cũ. App thật đăng ký migration 1→2→3→4, không destructive fallback. UUID/payload SavedStateHandle của bài tuần 5 **không** chuyển vào Room; từ tuần 6 phiên mới mới dùng Room. Từ/review/outbox cũ phải còn nguyên.

Điện thoại USB: đổi `$serial` theo `adb devices`, chạy `adb reverse` rồi build với endpoint debug tương ứng:

```powershell
& $adbPath -s $serial reverse tcp:8080 tcp:8080
.\gradlew.bat :app:assembleDebug -PapiBaseUrl=http://127.0.0.1:8080/ --console=plain
# Sau đó install -r và am start như trên.
```

USB reverse có thể vẫn tới backend khi tắt Wi-Fi; để test mất kết nối, ngắt đường tới API thật hoặc dừng backend rồi khởi động lại. Khi quay về emulator, build lại với `http://10.0.2.2:8080/`.

## 4. Bảng test tay

Tạo learner dev mới A để kiểm tra tổng XP ban đầu bằng 0, và B để kiểm tra account isolation. Trang chủ → **Mở bài học** → **Tải chặng**. Sau tải đủ, cả ba bài hiện trên lộ trình; bài 2/3 mở khi đủ điều kiện. Kết quả luôn là số câu đúng/tổng. DEMO hiện có 8 câu/bài, trọng số đều: 6/8 đạt, 5/8 trượt; fixture 10 câu chỉ dùng test format, không phải nội dung app.

| Thứ tự / ca | Thao tác | Kỳ vọng / ghi bằng chứng |
| --- | --- | --- |
| 1. Nâng cấp APK tuần 5 không uninstall | Trước cài ghi lại account, mức B1/B2, từ đã lưu, review/session dở và trạng thái sync; backup rồi install -r | Dữ liệu cũ còn nguyên; mở/ghi được Room v4. Ghi bản cài trước/sau và các số đếm. Không coi việc chuyển bài SavedStateHandle cũ là tiêu chí migration |
| 2. Tải dở | Bắt đầu tải trên account chưa có pack; ngắt API giữa tải. Mở lại app, thử học rồi kết nối và Tiếp tục tải chặng | Chưa đủ ba pack không học được; pack dở không được complete, pack tốt còn nguyên, retry tải đủ. Gói nhỏ tải nhanh: nếu đã đủ trước khi ngắt, chưa tái hiện được ca dở; dùng breakpoint hướng dẫn bên dưới hoặc ghi chưa tái hiện |
| 3. Học offline cả ba bài | Tải đủ/enroll khi online, ngắt mạng; học và vượt bài 1→2→3, nộp mỗi bài. Thử Câu trước và xoay màn hình | Đáp án/vị trí giữ lại; kết quả “Đạt tạm • Chờ server xác nhận”; bài kế mở tạm; không hiển thị XP tạm như chính thức. Account A mới vẫn XP chính thức 0 trước reconnect |
| 4. Reconnect / retry | Bật lại API/mạng; chờ worker, hoặc Yêu cầu đồng bộ lại | Worker gửi theo thứ tự phụ thuộc. UI tự chuyển xác nhận qua Room. A lần đầu vượt cả ba được tổng 90 XP; học lại một bài đã xác nhận không thưởng lần hai. Ghi UUID/attempt count/XP qua truy vấn đọc bên dưới |
| 5. Mất response | Ngắt kết nối sát lúc nộp, kiểm tra server đã có UUID nhưng app còn chờ; bật mạng và retry | Cùng UUID/payload, một attempt, XP không nhân đôi. Nếu server chưa nhận thì chỉ là mất mạng trước commit, chưa chứng minh mất response. Breakpoint sau gateway.submit chứng minh crash trước local ack, không được gọi là mất response mạng. Test tự động có injection response lost sau server commit |
| 6. Process death | Khi đã lưu một vài đáp án, đưa app về background, am kill rồi mở lại → lộ trình → Mở bài. Lặp lại sau khi đã nộp offline | Dù SavedStateHandle không phục hồi, Room giữ UUID/revision/đáp án/vị trí/payload. Không tự tạo attempt khác cho lượt đang chờ. Kiểm process đã bị kill; am kill có thể không diệt process foreground |
| 7. Vuốt tắt / force-stop / reboot | Học dở hoặc đã enqueue offline, vuốt task; mở lại. Lặp lại force-stop rồi reboot emulator | Mở bài tiếp tục từ Room. Force-stop tạm dừng worker theo Android; cần mở app lại để resume sync. Không yêu cầu worker chạy khi app đang force-stopped |
| 8. Hai tài khoản | A nộp offline; giữ nguyên queue, logout; B login và mở lộ trình. Online cho B rồi quay lại A | B không thấy pack/session/đáp án/XP của A; không gửi bài A bằng token B. Queue A vẫn còn; khi login A mới gửi. Đối chiếu user_id/UUID server trước/sau |
| 9. Revision mới | A đang học revision cũ; clone/publish revision mới bằng fixture SQL dev bên dưới; Kiểm tra nội dung mới rồi mở lại bài | Phiên đang học giữ UUID/answers/revision cũ, submit được ARCHIVED. Sau xác nhận, Học lại tạo phiên mới dùng revision mới; cùng lesson không thưởng lại |
| 10. Admin xóa từ | Trong admin dev thử xóa từ đang được lesson tham chiếu; xem Network request hoặc Swagger | HTTP 409/VOCABULARY_IN_USE, thông báo backend rõ; từ/snapshot/lesson còn nguyên. Admin UI hiện chưa bắt riêng lỗi delete để hiện toast; không lấy việc thiếu toast làm bằng chứng backend trả 500 |
| 11. Server từ chối vĩnh viễn | Với fixture dev làm payload xung đột như hướng dẫn bên dưới, reconnect | UI BLOCKED/mã lỗi, không tự gửi lại, giữ các đáp án và payload, không cấp XP/progress mới hoặc mở bài kế từ lượt này |

Các ca trên **chờ chủ dự án thực hiện**. Robolectric/đóng-mở DB không thay instrumentation hoặc test thiết bị. Các lượt test riêng chẩn đoán SQLITE_BUSY không phải bằng chứng nghiệm thu.

Lệnh điều khiển emulator (dùng terminal đã có `$adbPath`/`$serial`):

```powershell
# Ngắt/bật mạng; kiểm tra API thật không còn tới được khi ngắt.
& $adbPath -s $serial shell svc wifi disable
& $adbPath -s $serial shell svc data disable
# Khi cần reconnect:
& $adbPath -s $serial shell svc wifi enable
& $adbPath -s $serial shell svc data enable
# Background trước khi am kill:
& $adbPath -s $serial shell input keyevent KEYCODE_HOME
& $adbPath -s $serial shell am kill com.quocdat.lingolens
& $adbPath -s $serial shell pidof com.quocdat.lingolens
# Force-stop rồi mở lại:
& $adbPath -s $serial shell am force-stop com.quocdat.lingolens
& $adbPath -s $serial shell am start -n com.quocdat.lingolens/.MainActivity
# Reboot, chờ boot thật rồi mở app:
& $adbPath -s $serial reboot
& $adbPath -s $serial wait-for-device
& $adbPath -s $serial shell getprop sys.boot_completed
# Chỉ khi sys.boot_completed trả 1:
& $adbPath -s $serial shell am start -n com.quocdat.lingolens/.MainActivity
```

Để tái hiện tải dở chắc hơn mà không thêm service/debug API: Android Studio → Attach debugger to Android process (app debug), đặt breakpoint ở `ContentPackRepository.downloadCourse`, dòng `val bytes=gateway.download(reference.revisionId)`, với điều kiện `reference.position == 2`. Khi dừng trước pack 2, ngắt API/mạng, bỏ breakpoint và resume. Pack 1 đã commit; pack 2 tải thất bại. Nếu máy/IDE không tái hiện được, ghi rõ thay vì đánh dấu đạt.

Để kiểm crash trước local ack, đặt breakpoint trong `LessonSyncEngine.run` ngay **sau** `val result=gateway.submit(account,request)`, đọc UUID đã có trên server rồi force-stop app. Mở lại để retry; không dùng thao tác này thay bằng chứng mất response mạng.

## 5. Truy vấn chỉ đọc và fixture dev

Từ root, mở psql trong container database dev (đúng user cấu hình volume):

```powershell
Set-Location E:\Projects\LingoLens
docker compose exec postgres psql -U lingolens -d lingolens
```

Đọc attempt/XP/revision trước–sau, không in token/password:

```sql
SELECT a.id,u.email,a.lesson_revision_id,a.confirmation_status,a.score_percent,a.passed,a.submitted_offline
FROM lesson_attempts a JOIN users u ON u.id=a.user_id ORDER BY a.received_at;
SELECT u.email,x.source_key,count(*) AS rewards,sum(x.amount) AS xp
FROM xp_events x JOIN users u ON u.id=x.user_id GROUP BY u.email,x.source_key ORDER BY u.email,x.source_key;
SELECT r.id,r.lesson_id,r.revision_no,r.status FROM lesson_revisions r ORDER BY r.lesson_id,r.revision_no;
SELECT v.id,v.word FROM vocabularies v WHERE EXISTS(SELECT 1 FROM lesson_vocabularies lv WHERE lv.vocabulary_id=v.id);
```

Fixture revision mới: **chỉ trên database dev thử nghiệm**, không phải migration/CMS và không tự sửa nội dung PUBLISHED. Trong psql chọn đúng revision cũ đã hiển thị trên app; thay UUID ví dụ bằng giá trị truy vấn ở trên. Transaction tạo revision DRAFT, clone snapshots/questions/options với UUID question mới, archive bản cũ, publish bản mới. Tên bài giữ nguyên; phần giới thiệu có dấu “Revision test mới” để phân biệt. Chỉ chạy một lần cho lượt thử, không đổi seed đã nhập.

```sql
\set old_revision 'THAY-BANG-UUID-REVISION-CU-DANG-HOC'
BEGIN;
CREATE TEMP TABLE revision_test ON COMMIT DROP AS
SELECT r.id AS old_id,gen_random_uuid() AS new_id,r.lesson_id,
 (SELECT max(x.revision_no)+1 FROM lesson_revisions x WHERE x.lesson_id=r.lesson_id) AS next_no
FROM lesson_revisions r WHERE r.id=:'old_revision'::uuid AND r.status='PUBLISHED';
-- Phải trả đúng 1 dòng; nếu 0 thì ROLLBACK và chọn lại UUID.
TABLE revision_test;
INSERT INTO lesson_revisions(id,lesson_id,revision_no,introduction,sentence_patterns,status,content_status,pack_id)
SELECT t.new_id,r.lesson_id,t.next_no,'Revision test mới. '||r.introduction,r.sentence_patterns,'DRAFT',r.content_status,r.pack_id
FROM revision_test t JOIN lesson_revisions r ON r.id=t.old_id;
INSERT INTO lesson_vocabularies(lesson_revision_id,vocabulary_id,position,content_snapshot)
SELECT t.new_id,v.vocabulary_id,v.position,v.content_snapshot FROM revision_test t JOIN lesson_vocabularies v ON v.lesson_revision_id=t.old_id;
CREATE TEMP TABLE question_test ON COMMIT DROP AS
SELECT q.id AS old_id,gen_random_uuid() AS new_id,t.new_id AS revision_id
FROM revision_test t JOIN questions q ON q.lesson_revision_id=t.old_id;
INSERT INTO questions(id,lesson_revision_id,type,prompt,speech_text,explanation,answer_key,position,weight)
SELECT t.new_id,t.revision_id,q.type,q.prompt,q.speech_text,q.explanation,q.answer_key,q.position,q.weight
FROM question_test t JOIN questions q ON q.id=t.old_id;
INSERT INTO question_options(question_id,option_key,label,position)
SELECT t.new_id,o.option_key,o.label,o.position FROM question_test t JOIN question_options o ON o.question_id=t.old_id;
UPDATE lesson_revisions SET status='ARCHIVED' WHERE id IN (SELECT old_id FROM revision_test);
UPDATE lesson_revisions SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP WHERE id IN (SELECT new_id FROM revision_test);
COMMIT;
```

Fixture từ chối vĩnh viễn: trên **account dev thử riêng**, nộp một bài offline, lấy `payloadJson` của queue bằng Android Studio App Inspection → Database Inspector (app foreground). Trên máy tính mở `http://localhost:8080/swagger-ui.html`, đăng nhập đúng learner đó và Authorize bằng access token. Gửi POST `/api/v1/lesson-attempts` với bản sao payload, giữ UUID/revision/answers/timestamp nhưng đổi `mode` thành `PRACTICE` (backend đã hỗ trợ, không có PRACTICE UI). Server ghi attempt này mà không cấp progress/XP. Reconnect app: payload LESSON gốc xung đột với cùng UUID, server trả 409/ATTEMPT_PAYLOAD_CONFLICT. Không sửa row/hash trong Room hoặc migration. Ca này cố ý tạo dữ liệu thử; giữ bản backup và ghi UUID. Nếu chưa thiết lập được fixture, đánh dấu chưa test; test tự động đã kiểm BLOCKED và giữ đáp án/payload.

## 6. Gửi kết quả để làm bước 7

Với mỗi ca, gửi Đạt/Chưa đạt/Chưa tái hiện, account thử, thiết bị/Android version, ảnh màn hình hoặc UUID cùng truy vấn trước–sau. Nếu lỗi, giữ database/queue và báo màn hình, mã lỗi, thao tác cuối; không uninstall/clear data. Sau chủ dự án test xong mới làm bước 7 và mở PR. REVIEWED, instrumentation và phát hành không được coi là đã hoàn tất chỉ vì test JVM đạt.
