# Tuần 7 — Chạy bản ba dạng bài và kiểm tra TTS/fallback

**Hướng dẫn kiểm tra, chưa phải xác nhận đã test thiết bị.** Nhánh `feature/question-types-tts`; không tự merge main. Nội dung vẫn DEMO, phải rà soát độc lập thành REVIEWED **trước tuần 8**. Chưa có instrumentation/kiểm chứng âm thanh thực trên ba thiết bị.

## 1. Backend dev và seed tuần 7 explicit

Docker Desktop phải chạy. Credential bên dưới là mặc định dev đã có trong repo; nếu volume PostgreSQL cũ dùng credential khác, giữ credential đó. Không xóa volume/reset dữ liệu. Terminal thứ nhất:

```powershell
Set-Location E:\Projects\LingoLens
git branch --show-current
# Kỳ vọng feature/question-types-tts; nếu đang nhánh khác và working tree cho phép:
# git switch feature/question-types-tts
$env:JAVA_HOME = 'E:/Program Files/Android/Android Studio/jbr'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/lingolens'
$env:DB_USERNAME = 'lingolens'
$env:DB_PASSWORD = 'lingolens_password'
docker compose up -d postgres
docker compose ps postgres
# Chờ postgres healthy; tránh hai backend cùng chiếm cổng 8080.
docker compose stop backend
Set-Location backend
.\gradlew.bat seedDemo -PseedFile=src/main/resources/content/demo-week7.json --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Seed failed; stop and inspect validation or existing revision metadata' }
.\gradlew.bat bootRun --args='--spring.profiles.active=dev' --console=plain
```

Runner default vẫn là `demo-week5.json`; cần `-PseedFile=...demo-week7.json` để app thấy nội dung ba dạng. Không sửa seed đã nhập để đổi checksum. Import tuần 7 tạo pack/revision/question UUID mới, giữ course/lesson/enrollment/progress/XP cũ; archive revision cũ, không sửa câu cũ. Receipt cùng checksum là no-op. Revision mới phải có revisionNo lớn hơn max hiện có: nếu dev đã có revision thử nghiệm cao hơn 2, runner sẽ từ chối; dừng và báo, không xóa dữ liệu hoặc sửa gói để vượt guard. Fresh DB nhập tuần 7 trước rồi chạy seed mặc định tuần 5 cũng bị từ chối do revision cũ, không đổi manifest lại tuần 5.

Seed runner chỉ dev/test; prod/production bị từ chối, production startup guard vẫn từ chối DEMO. Preflight trên database mục tiêu chưa được chạy. Không coi nhập được DEMO là nội dung đã REVIEWED.

Terminal thứ hai kiểm health:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 2. Backup database debug trước cài đè

Không uninstall, không clear data. Chọn đúng serial từ `adb devices`. Bản backup có database/WAL/SHM nếu có; **không** backup DataStore/token, Android Keystore hoặc ảnh camera. `run-as` chỉ hoạt động với app debug. Nếu app chưa từng được cài thì chưa có database để backup; ghi trường hợp cài mới riêng, không gọi đó là kiểm thử nâng cấp.

```powershell
Set-Location E:\Projects\LingoLens
$env:JAVA_HOME = 'E:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/ASUS/AppData/Local/Android/Sdk'
$adbPath = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$serial = 'emulator-5554'
& $adbPath devices
# Phải thấy device; xử lý unauthorized trước khi tiếp tục.
$backupDir = Join-Path $env:USERPROFILE 'LingoLens-backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$backupFile = Join-Path $backupDir ('before-week7-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.tar')
& $adbPath -s $serial shell am force-stop com.quocdat.lingolens
& $adbPath -s $serial shell run-as com.quocdat.lingolens mkdir -p files
& $adbPath -s $serial shell run-as com.quocdat.lingolens tar -cf files/week7-backup.tar databases
if ($LASTEXITCODE -ne 0) { throw 'Database archive failed; do not install yet' }
$backupBase64 = & $adbPath -s $serial exec-out run-as com.quocdat.lingolens base64 files/week7-backup.tar
if ($LASTEXITCODE -ne 0) { throw 'Database export failed; do not install yet' }
[IO.File]::WriteAllBytes($backupFile, [Convert]::FromBase64String(($backupBase64 -join '')))
Get-Item -LiteralPath $backupFile
Get-FileHash -LiteralPath $backupFile -Algorithm SHA256
```

Force-stop trước tar để không sao chép database đang ghi. Base64 tránh hỏng binary do PowerShell redirect; kiểm tra file backup thật trước cài. Không gọi backup này là chứng minh migration hoặc công cụ khôi phục token.

## 3. Build APK và cài đè

Backend giữ chạy terminal trước. Terminal Android đã có `$adbPath`/`$serial` từ phần backup:

```powershell
Set-Location E:\Projects\LingoLens
.\gradlew.bat :app:assembleDebug -PapiBaseUrl=http://10.0.2.2:8080/ --max-workers=2 --console=plain
if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
Get-Item .\app\build\outputs\apk\debug\app-debug.apk
Get-FileHash .\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
& $adbPath -s $serial install -r .\app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed; keep current app data and inspect signing/version errors' }
& $adbPath -s $serial shell am start -n com.quocdat.lingolens/.MainActivity
```

`install -r` cần applicationId/debug signing key tương thích bản cũ. Tuần 7 không nâng version Room (v4), không migration mới; từ/review/outbox và session revision tuần 6 vẫn phải còn nguyên. Giữ một phiên tuần 6 đang dở hoặc đã nộp chưa sync để kiểm old pack tương thích; tải revision mới không tự thay session cũ. Để thấy bài tuần 7, tạo phiên mới sau confirmed hoặc dùng learner mới và tải chặng; không tự bỏ UUID phiên đang chờ.

Điện thoại USB: đổi serial đúng thiết bị, dùng reverse và build endpoint tương ứng, rồi install -r/am start như trên:

```powershell
& $adbPath -s $serial reverse tcp:8080 tcp:8080
.\gradlew.bat :app:assembleDebug -PapiBaseUrl=http://127.0.0.1:8080/ --max-workers=2 --console=plain
```

USB reverse có thể giữ đường API khi tắt Wi-Fi. Để test offline thật, xác nhận API không tới được hoặc dừng backend dev rồi bật lại sau ca; đừng dùng việc tắt Wi-Fi một mình làm bằng chứng. Khi trở về emulator, build lại endpoint `10.0.2.2`.

## 4. Bảng test ba dạng bài

Đăng ký learner dev mới A để kiểm XP ban đầu 0, B để kiểm account isolation. Trang chủ → **Mở bài học** → **Tải chặng** hoặc **Kiểm tra nội dung mới**. Pack tuần 7 có ba bài, mỗi bài sáu từ/mười câu: bốn chọn nghĩa, hai nghe chọn từ, bốn điền từ; weight=1. Kết quả hiển thị số câu đúng/tổng. Với phiên cũ pinned tuần 6 vẫn tám câu; đừng dùng ngưỡng 7/10 để đánh giá phiên tám câu.

| Ca / thứ tự | Thao tác | Kỳ vọng và bằng chứng cần ghi |
| --- | --- | --- |
| 1. Nội dung và ba loại | Tải đủ chặng tuần 7, mở bài mới; đi hết mười câu | Bốn MEANING, hai LISTEN, bốn FILL; không thêm PRACTICE UI. Ghi lesson/revision ID nếu dùng debugger/DB, phân biệt phiên tám câu cũ |
| 2. Chọn nghĩa và feedback | Chọn một option đúng, một option sai ở câu khác; kiểm phản hồi/giải thích, quay câu trước | Đúng/sai và explanation tương ứng; đáp án lưu bền vững, không thưởng XP riêng sau từng câu. Input/feedback chưa phải kết quả server |
| 3. Fill spacing/case/approved variants | Ở bài 1 điền `  HeLLo  ` thay `hello`; kiểm feedback. Biến thể khác chỉ thử nếu thật sự có trong answer_key | Trim/gộp whitespace/không phân biệt hoa thường; raw được lưu nguyên vẹn. Seed hiện mỗi từ chỉ có một variant. Vector tự động phủ multiword spacing; không tự thêm variant vào seed đã nhập để thử |
| 4. Fill typo/punctuation/incomplete/length | Điền `hellp`, `hello!`, rồi xóa về rỗng; thử chuỗi >500 ký tự | Typo/dấu câu ngoài approved variants bị sai, không fuzzy. Rỗng được lưu khi chỉnh nháp nhưng không được nộp như đầy đủ; input quá dài bị chặn. Không tự coi câu rỗng là đúng hoặc bỏ mẫu số |
| 5. 7/10 đạt, 6/10 trượt | Dùng hai lượt/learner phù hợp. Với 7/10: trả lời đúng câu 1–7, nhập `zzzz` ở câu 8–10. Với 6/10: đúng câu 1–6, nhập `zzzz` cho cả bốn FILL | Nộp 7/10 đạt, 6/10 chưa đạt, mẫu số luôn 10. Mọi câu sai vẫn có đáp án hợp lệ/nonblank. Offline hiển thị tạm chờ server; server chấm lại. Sau first pass confirmed mới 30 XP/bài; học lại cùng lesson không thưởng lần hai |
| 6. Nghe en-US | Đến hai câu nghe, bấm phát, nghe lại, chọn option và kiểm feedback | Có âm thanh không đồng nghĩa trả lời đúng; phải tự chọn option. Không tự submit/grade khi callback TTS success. Tổng bài vẫn mười câu |
| 7. Thiếu voice/fallback | Trên máy thiếu en-US hoặc chỉ có giọng yêu cầu mạng khi offline, bấm hướng dẫn cài hoặc chọn câu thay thế | Thông báo rõ; fallback được gắn nhãn, giữ cùng câu/options/answer key/trọng số/mẫu số. Người dùng phải trả lời; không tự đúng hoặc bỏ câu nghe. Nếu chưa tái hiện được thiếu voice, ghi chưa kiểm chứng |
| 8. Cài voice rồi quay lại | Bấm cài voice nếu engine có installer; trong hệ thống chọn/cài English (United States), quay lại app và thử nghe | App đọc lại voice thực, ưu tiên en-US không cần mạng. Installer không có activity hoặc cài thất bại vẫn giữ fallback. Không đổi tiếng Anh khác thành en-US |
| 9. TTS lifecycle | Trong lúc phát: sang câu khác, HOME/background, quay lại, xoay màn hình, rời bài hoặc logout | Phát cũ dừng, callback cũ không đổi trạng thái câu/account mới; không tự ghi/chấm đáp án. Quay lại vẫn cùng session/đáp án/vị trí. Ghi âm còn phát nền/cắt tiếng hoặc lỗi nếu có |
| 10. Luyện lại câu sai | Nộp bài có câu sai → luyện lại sai → sửa từng câu và kiểm feedback; thử lúc còn chờ server và sau confirmed | Nhãn luyện cục bộ không đổi kết quả đã nộp. UUID/frozen payload/outbox/progress/XP gốc không đổi; không tạo attempt/PRACTICE request và không thưởng XP luyện lại |
| 11. Offline ba bài và reconnect | Tải/enroll online trước, ngắt API thật, vượt ba bài, rồi bật API/mạng | Đạt tạm mở bài kế, XP chính thức chưa tăng; reconnect xác nhận theo thứ tự. First pass cả ba cho A mới là 90 XP, không nhân đôi retry |
| 12. Mất response và giữ raw UUID | Tái hiện server đã nhận nhưng app chưa ack, mở lại/retry | Cùng UUID/raw payload; một attempt và thưởng một lần. Nếu server chưa nhận thì chỉ chứng minh mất mạng trước commit. Không sửa raw fill sau frozen, dù normalization cho cùng từ |
| 13. Process death/reboot/account | Học/nộp dở rồi am kill/force-stop/reboot; A pending logout, B login, online B, quay A | Room giữ raw answers/position/UUID/pinned revision; B không thấy/gửi queue A; A mới drain queue. Chưa coi fake/Robolectric là bằng chứng ca này |
| 14. Cài đè/revision cũ/admin delete | Backup+cài đè bản tuần 6; giữ old session/queue; seed revision tuần 7 và làm ca admin xóa từ tham chiếu | Old session/pack bytes đọc được và ARCHIVED vẫn chấm; mới dùng ba loại. Dữ liệu cũ không mất; xóa từ lesson trả409 và dữ liệu còn. Admin UI thiếu toast riêng là rủi ro đã biết |

Các ca là **kế hoạch chưa thực hiện trên thiết bị**, không có hàng nào mặc định đạt. Các thao tác có debugger/SQL, tải dở, mất response, revision mới, account và nâng cấp tuần 5→6 giữ hướng dẫn chi tiết tại [week6-testing.md](week6-testing.md). Không chạy SQL xóa/reset hoặc uninstall để làm ca nâng cấp “đạt”.

## 5. TTS thật trên ít nhất ba thiết bị

Theo đặc tả cần ba thiết bị thật, khác Android/engine và có máy cấu hình thấp. Emulator dùng smoke test riêng, không thay một thiết bị thật. Điền bảng sau bằng quan sát; nếu chưa test ghi **Chưa kiểm chứng**.

| Thiết bị/Android | Engine/package/version | Voice.name/locale | isNetworkConnectionRequired | Online / offline / thiếu voice | Latency, độ rõ, lỗi cắt/dừng | Người kiểm/ngày |
| --- | --- | --- | --- | --- | --- | --- |
| Thiết bị 1 — chưa chọn | Chưa kiểm chứng | Chưa kiểm chứng | Chưa kiểm chứng | Chưa kiểm chứng | Chưa kiểm chứng | Chưa có |
| Thiết bị 2 — engine/Android khác | Chưa kiểm chứng | Chưa kiểm chứng | Chưa kiểm chứng | Chưa kiểm chứng | Chưa kiểm chứng | Chưa có |
| Thiết bị 3 — cấu hình thấp | Chưa kiểm chứng | Chưa kiểm chứng | Chưa kiểm chứng | Chưa kiểm chứng | Chưa kiểm chứng | Chưa có |

Xác định voice bằng thuộc tính thực của Android `Voice`, không chỉ `setLanguage` hoặc một lần nghe được. Trong app debug có thể attach Android Studio và quan sát `voice.locale.toLanguageTag()`, `voice.isNetworkConnectionRequired`, `voice.name` ở `AndroidLessonSpeechEngine.availableVoices`; controller giữ bản chiếu `selectedVoice.languageTag/requiresNetwork`. Không in token hoặc nội dung cá nhân khi ghi evidence. Nếu chưa đo/đọc được thuộc tính, ghi chưa kiểm chứng thay vì đoán offline voice.

Bộ kiểm nội dung âm thanh đề xuất **20 từ + 10 câu**, chỉ là checklist phát âm, không thêm nội dung seed/audio:

- 18 từ DEMO: hello, goodbye, please, thanks, yes, no, water, bread, rice, milk, tea, coffee, mother, father, sister, brother, family, friend; thêm `record` và `present` để rà soát trọng âm theo ngữ cảnh.
- Câu: “Hello, my friend.”; “Goodbye, see you soon.”; “Please sit here.”; “Thanks for your help.”; “Yes, I am ready.”; “No, thank you.”; “I drink water.”; “I like coffee.”; “This is my family.”; “My sister is my friend.”

UI bài hiện phát `speechText` của những câu nghe đã có, không có màn hình phát tùy ý 20 từ/10 câu. Bộ dài có thể được người rà soát kiểm qua preview engine trên thiết bị/Android Studio test harness; ghi rõ phương tiện và không coi preview đó là test end-to-end app. Người rà soát tiếng Anh độc lập đánh giá accent en-US, trọng âm, độ rõ và từ dễ nhầm. Chưa có kết quả thì gate thiết bị/TTS chất lượng chỉ **Đạt một phần – chưa kiểm chứng trên thiết bị**.

Chạy lần lượt online, airplane mode (xác nhận network voice không còn đường mạng), máy thiếu voice, chuyển nền/đổi câu, phát liên tiếp. Khi có cả giọng offline và network, kiểm app ưu tiên giọng en-US có `isNetworkConnectionRequired=false`; nếu chỉ có network voice online thì nhãn phải rõ cần mạng. Không có en-US hoặc phát lỗi thì dùng fallback/cài voice, không ngầm hoàn thành.

## 6. Lệnh lifecycle và truy vấn đối chiếu

Terminal đã có `$adbPath`/`$serial`:

```powershell
# Emulator: tắt/bật mạng; vẫn cần kiểm đường API thật.
& $adbPath -s $serial shell svc wifi disable
& $adbPath -s $serial shell svc data disable
& $adbPath -s $serial shell svc wifi enable
& $adbPath -s $serial shell svc data enable
# Background trước am kill; kiểm pid đã biến mất.
& $adbPath -s $serial shell input keyevent KEYCODE_HOME
& $adbPath -s $serial shell am kill com.quocdat.lingolens
& $adbPath -s $serial shell pidof com.quocdat.lingolens
# Mở lại hoặc force-stop rồi mở lại:
& $adbPath -s $serial shell am force-stop com.quocdat.lingolens
& $adbPath -s $serial shell am start -n com.quocdat.lingolens/.MainActivity
# Reboot là thao tác riêng; chờ boot thật.
& $adbPath -s $serial reboot
& $adbPath -s $serial wait-for-device
& $adbPath -s $serial shell getprop sys.boot_completed
# Chỉ khi sys.boot_completed trả 1:
& $adbPath -s $serial shell am start -n com.quocdat.lingolens/.MainActivity
```

Force-stop ngăn worker chạy nền cho tới lúc mở app; không yêu cầu sync trong khi force-stopped. `am kill` foreground có thể không giết process, nên đưa background và kiểm pid; vuốt task không luôn tương đương process death.

Từ root mở psql dev; chỉ đọc trước/sau luyện lại/retry để so attempt count/XP/raw answers. Dùng đúng credential volume hiện có:

```powershell
Set-Location E:\Projects\LingoLens
docker compose exec postgres psql -U lingolens -d lingolens
```

```sql
SELECT a.id,u.email,a.lesson_revision_id,a.confirmation_status,a.score_percent,a.passed,a.submitted_offline
FROM lesson_attempts a JOIN users u ON u.id=a.user_id ORDER BY a.received_at;
SELECT u.email,x.source_key,count(*) AS rewards,sum(x.amount) AS xp
FROM xp_events x JOIN users u ON u.id=x.user_id GROUP BY u.email,x.source_key ORDER BY u.email,x.source_key;
SELECT r.id,r.lesson_id,r.revision_no,r.status FROM lesson_revisions r ORDER BY r.lesson_id,r.revision_no;
-- Thay UUID bằng đúng attempt của learner dev đang thử, không xem dữ liệu account khác.
SELECT question_id,submitted_answer,is_correct,awarded_weight
FROM attempt_answers WHERE attempt_id='THAY-BANG-UUID-ATTEMPT'::uuid ORDER BY question_id;
```

Room frozen payload/hash/outbox có thể đối chiếu qua Database Inspector của app debug; không tự UPDATE để “sửa” UUID/answers. Luyện lại cục bộ không đổi các row này và không sinh request; raw fill input còn nguyên dấu cách/hoa thường trong `submitted_answer.optionKey` trên server.

## 7. Những gì hướng dẫn này chưa chứng minh

Chưa có instrumentation/thiết bị cho TTS chất lượng, lifecycle/process death, vuốt tắt/reboot, nâng cấp APK và hai account. Chưa preflight database mục tiêu. DEMO chưa REVIEWED. Fake engine/unit/Robolectric kiểm logic nhưng không chứng minh âm thanh, voice installer hoặc hành vi OEM/OS. Không có cloud TTS/audio mới/PRACTICE UI/analytics/camera–topic/xóa account/CMS/bảng xếp hạng trong bản tuần 7.
