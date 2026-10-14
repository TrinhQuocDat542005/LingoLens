# Tuần 8 — analytics Android: hợp đồng triển khai

Nhánh mới từ Roomv5 ca09b05: codex/analytics-mobile. Nhánh Room codex/local-storage đã được chủ dự án cho phép push. Không PR/merge, không sửa migrationsV1–V15 hoặc schemasRoom1–5. Phạm vi checkpoint chỉ consent/queue/worker/hooks; dừng báo cáo trước camera.

- Consent mặc định tắt. Bật chỉ sau PUT analytics-v1 được server ack; lỗi/offline không bật. Tắt tại máy trong transaction ngay, purge AnalyticsOutbox của account đó và lưu pendingOptOut khi offline; worker gửi PUT tắt với expectedRevision, CAS409 tải lại trước thử lại. Không xóa hai queue nghiệp vụ.
- Giữ server consentAt; updatedAt của projection là biên bắt đầu thu thập ở máy khi ack một generation mới. Refresh cùng revision giữ biên này. Không ghi/reconcile sự kiện trước biên ack, không backfill lịch sử trước opt-in. GET consent là cấu hình tài khoản, không batch telemetry.
- EventId xác định từ account+eventName+source UUID, payload frozen từ Room (ISO UTC tại biên API). ACKNOWLEDGED giữ trong cùng hàng outbox tối đa90 ngày để tránh replay hook sau restart; worker chỉ dispatch PENDING. BLOCKED giữ lỗi/payload hữu hạn90 ngày, không gửi lại tự động; permanent drop bị xóa. Opt-out purge cả ACK/BLOCKED, không hồi sinh sau opt-in mới.
- Worker/unique WorkManager work riêng, không thêm vào SyncCoordinator. Kiểm account và login generation trước/sau mọi response; X-LingoLens-Account bảo vệ transport. Logout hủy work analytics account cũ, giữ queue; B không dispatch hoặc ack A. Các worker nghiệp vụ hoạt động độc lập.
- Hooks chạy sau business commit, chỉ enqueue ngắn, không network trong transaction học/ôn. Analytics lỗi không làm việc lưu bài/ôn báo thất bại. Reconciliation từ sessions đã commit trong biên consent phục hồi crash giữa commit và hook, không scan lịch sử trước ack. lesson_passed chỉ backend, không client event.
- app_open một UUID mỗi lần vào foreground; rotation giữ cùng UUID, recomposition không sinh UUID. Hook dùng startedAt của foreground, nên opt-in giữa foreground không backfill app_open trước đó; lần vào foreground sau mới ghi. Login mới trong foreground được kiểm theo account riêng.
- lesson_started dùng durable session.id/startedAt; lesson_completed dùng id/clientCompletedAt trong frozen payload, không raw đáp án/correctness tạm. PRACTICE UI không làm. review_session_completed chỉ finishedAt!=null và có ít nhất một ReviewEvent; không sự kiện cho phiên0answers.
- Hộp thoại giữ nguyên wording đã duyệt, hai OutlinedButton ngang mức/chiều rộng; không preselect. Settings luôn tắt được và bật qua cùng dialog. UI chỉ lưu cờ đã xem theo account trên máy bằng SharedPreferences sẵn có, không coi đó là consent hoặc thêm Room field. Cờ này cần xóa theo account ở tuần9; máy/cài mới có thể hỏi lại nếu server chưa có consentVersion. Nội dung privacy trong app ghi backup/log còn chờ xác nhận hosting, không hứa xóa mọi backup hoặc ảnh sau24h; không bịa URL/email.

Tests viết trước: consent ack/default/offline/purge/CAS/race, frozen retry/UUID/generation/two-account/retention/partial blocked, hooks/recovery/no backfill/rotation, queue independence và UI policy. Full Android/backend/postgresTest/lint38/admin/APK trước commit. Fail/contract change thì dừng, không nới assertion. Hardware/instrumentation chưa chạy phải ghi phần hạn chế.

## Checkpoint mới nhất — 14/10/2026

Docker version đã xác nhận Client và Server 29.8.0, Docker Desktop 4.91.0; lượt mới đầy đủ bên dưới. Hướng dẫn backup/chạy backend/truy vấn sự kiện chưa thực thi trên database của chủ dự án.

Nhánh Room `codex/local-storage` đã push; checkpoint analytics trên `codex/analytics-mobile` được phép commit/push sau full gate này. Không mở PR, không merge, chưa làm camera hoặc xóa account. Phạm vi portfolio mới trong đặc tả thay gate phát hành Google Play/nội dung REVIEWED/preflight database mục tiêu.

| Gate mới, giờ Việt Nam | Kết quả |
|---|---|
| Android, 18:04:02–18:06:06 | 162/162 đạt; 0 failure/error/skipped |
| Backend unit, 18:04:02–18:05:06 | 48 đạt; 0 failure/error/skipped |
| PostgreSQL thật, 18:05:10–18:07:43 | 70/70 đạt trong 13 class; 0 failure/error/skipped, không initialization error |
| Admin build, 18:03:38–18:04:02 | Đạt (TypeScript và Vite); đây là build, không phải test |
| Lint mới, báo cáo xuất 18:06:16 | Đạt: 0 errors, đúng 38 warnings, 0 mới |
| APK, artifact ghi 18:06:11 | assembleDebug đạt |

Số test PostgreSQL thực tế theo class: AnalyticsMigrationTests 1; AnalyticsPostgresTests 14; AttemptConfirmationMigrationTests 1; LessonAttemptPostgresTests 8; OfflineAttemptPostgresTests 8; Week7QuestionPostgresTests 6; IdentityBoundAuthPostgresTests 2; IdentityTokenRolloutMigrationTests 1; ContentPackPostgresTests 4; ContentSeedTests 8; PostgresIntegrationTests 9; LearnerSettingsMigrationTests 4; LearnerSettingsPreflightTests 4. Mọi class 0 failure/error/skipped.

Fixture đã được sửa theo phê duyệt: manifest đủ ba pack, SHA-256/byteLength tính từ đúng UTF-8 content lưu trong Room, có downloadUrl và contentStatus. Không thay parser production. Test `AnalyticsBusinessHookTest.lesson telemetry failure after commit cannot rollback durable start freeze or business queue` đã đạt: cả onStarted/onCompleted chạy, ném lỗi được inject, số lần gọi được assert trước kiểm tra durable data; session, frozenPayloadJson và business outbox vẫn tồn tại. Giữ mọi assertion cũ, thêm kiểm tra frozen payload.

Theo phê duyệt, rememberChoice đã chuyển sang KTX edit { } mặc định apply; không suppress, đổi baseline hoặc thêm dependency. Test mới `AnalyticsRepositoryTest.prompt seen flag never grants consent and removing it cannot enable telemetry` đạt: cờ có/không/xóa không bật consent; ngay trước ack response, Room vẫn tắt và không ghi event; chỉ server ack mới bật projection Room; opt-out vẫn tắt khi thay cờ.

Không có điều kiện dừng trong lượt gate mới. Không đổi cấu hình PostgreSQL/Testcontainers để che lỗi môi trường trước đó. Chủ dự án cho phép push nhánh này, không PR/merge.

Việc còn lại: `ContentPackRepository.ready()` hiện ném lỗi khi manifest trong Room hỏng, không trả empty/chưa sẵn sàng; JSON sai cú pháp ném parse error, JSON `{}` ném NullPointerException tại lessons. Màn hình học có bắt Exception để hiện thông báo, nhưng repository chưa có cơ chế phục hồi cache hỏng; giữ production nguyên trạng theo yêu cầu.

UI consent, WorkManager trên máy thật, logout/đổi tài khoản, lifecycle và nâng cấp APK chưa có instrumentation/kiểm chứng trên thiết bị. Test độc lập queue kiểm tra coordinator và repository bằng callbacks; chưa chứng minh lịch chạy WorkManager thực tế. Room schema 1–5 và SQL migrations không có thay đổi trong diff. Nội dung vẫn DEMO, công bố trong README; không còn gate REVIEWED hoặc preflight database mục tiêu cho portfolio. Chưa làm camera.

Hạng mục phục hồi cache hỏng đã đưa vào backlog tuần 9 trong đặc tả mục 10: coi pack chưa sẵn sàng khi parse lỗi, tải lại qua kiểm tra manifest/checksum, không xóa session hoặc hàng đợi. Chưa triển khai production.

## Hướng dẫn test sau khi full gate đạt

APK đạt gate kỹ thuật; chưa có test tay trên thiết bị. SHA-256: `ACE43CFBE9D49CC664C751072EB75866C29A708D250D6336BD571FC85FC8F92C`.

Sao lưu Room của bản debug trước cài đè (không sao lưu token/DataStore/Keystore). Device phải được ADB nhận là `device`; thay serial nếu khác. Export base64 tránh chuyển hướng nhị phân làm hỏng file trên Windows PowerShell.

```powershell
Set-Location E:\Projects\LingoLens
$adbPath = 'C:\Users\ASUS\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$serial = 'emulator-5554'
& $adbPath devices
$backupDir = Join-Path $env:USERPROFILE 'LingoLens-backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$backupFile = Join-Path $backupDir ('before-analytics-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.tar')
& $adbPath -s $serial shell am force-stop com.quocdat.lingolens
& $adbPath -s $serial shell run-as com.quocdat.lingolens mkdir -p files
& $adbPath -s $serial shell run-as com.quocdat.lingolens tar -cf files/analytics-backup.tar databases
if ($LASTEXITCODE -ne 0) { throw 'Backup failed; do not install' }
$encoded = & $adbPath -s $serial exec-out run-as com.quocdat.lingolens base64 files/analytics-backup.tar
if ($LASTEXITCODE -ne 0) { throw 'Export failed; do not install' }
[IO.File]::WriteAllBytes($backupFile, [Convert]::FromBase64String(($encoded -join '')))
Get-FileHash -LiteralPath $backupFile -Algorithm SHA256
# Chỉ cài sau khi gate đạt; không uninstall/clear data.
& $adbPath -s $serial install -r .\app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -ne 0) { throw 'Install failed; preserve existing app data' }
& $adbPath -s $serial shell am start -n com.quocdat.lingolens/.MainActivity
```

### Sao lưu server và chạy backend mới cho emulator

“Database dev” ở đây là database chủ dự án dùng để thử app, **không phải bản vận hành**. Hướng dẫn mặc định dùng PostgreSQL của Compose tại localhost:5433/lingolens. Nếu Compose của bạn chứa dữ liệu vận hành hoặc credentials khác, không dùng nguyên lệnh này; chọn đúng database thử/credentials trước.

Trước lần chạy backend mới (Flyway tự áp V14 và V15 chưa có), sao lưu bằng pg_dump theo [server-database-backup.md](server-database-backup.md), gồm cả hướng dẫn khôi phục vào database mới. Dừng bootRun cũ bằng Ctrl+C và dừng backend Compose; giữ volume PostgreSQL. Các lệnh này là hướng dẫn, agent chưa áp migration hoặc backup trên database của chủ dự án.

```powershell
Set-Location E:\Projects\LingoLens
git branch --show-current # codex/analytics-mobile
docker version # Phải thấy cả Client và Server
docker compose stop backend
docker compose up -d postgres
docker compose ps postgres # Chờ healthy
$backupDir = Join-Path $env:USERPROFILE 'LingoLens-backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$dumpName = 'before-v14-v15-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.dump'
$backupFile = Join-Path $backupDir $dumpName
docker compose exec -T postgres pg_dump -U lingolens -d lingolens -Fc -f "/tmp/$dumpName"
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed; do not start new backend' }
$pgCid = (docker compose ps -q postgres).Trim()
docker cp "${pgCid}:/tmp/$dumpName" $backupFile
if ($LASTEXITCODE -ne 0) { throw 'Backup copy failed; do not start new backend' }
Get-FileHash -LiteralPath $backupFile -Algorithm SHA256
docker compose exec -T postgres psql -U lingolens -d lingolens -c "SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;"
$env:JAVA_HOME = 'E:/Program Files/Android/Android Studio/jbr'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/lingolens'
$env:DB_USERNAME = 'lingolens'
$env:DB_PASSWORD = 'lingolens_password'
Set-Location backend
.\gradlew.bat bootRun --args='--spring.profiles.active=dev' --console=plain
```

Không chạy SQL V14/V15 thủ công: bootRun dùng Flyway áp các migration còn thiếu và Hibernate validate. Không sửa migration/checksum/history; migration báo dữ liệu lỗi thì dừng. DB thử đã có DEMO được giữ nguyên, không nhập lại/đổi thành REVIEWED. V14 thu hồi refresh token cũ; access token thiếu userId claim bị từ chối, đăng nhập lại một lần, giữ nguyên Room. Không chạy lại backend cũ sau rollout để bỏ qua login.

Ở PowerShell thứ hai, từ root repo, kiểm tra Flyway/health và dữ liệu consent/event:

```powershell
Set-Location E:\Projects\LingoLens
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose exec -T postgres psql -U lingolens -d lingolens -c "SELECT version,description,success FROM flyway_schema_history WHERE version IN ('14','15') ORDER BY installed_rank;"
docker compose exec -T postgres psql -U lingolens -d lingolens -c "SELECT user_id,analytics_opt_in,analytics_consent_version,analytics_consent_revision,analytics_consent_at FROM learner_settings ORDER BY user_id;"
docker compose exec -T postgres psql -U lingolens -d lingolens -c "SELECT id,user_id,event_name,session_id,attempt_id,occurred_at,received_at,consent_revision,properties,payload_hash FROM analytics_events ORDER BY received_at DESC LIMIT 50;"
```

Android emulator dùng 10.0.2.2:8080 để vào backend host. Android Studio → App Inspection → Database Inspector → learning.db: đọc `AnalyticsConsentState` và `AnalyticsOutbox` (bản debug đang chạy). So Room accountId với server user_id, eventId với id, consentRevision, sessionId/attemptId và payloadHash. Room occurredAt/queuedAt là epoch ms; server occurred_at/received_at là TIMESTAMPTZ, received_at không phải queuedAt. ACKNOWLEDGED giữ tối đa 90 ngày để dedupe, không phải queue chưa gửi; worker chỉ gửi PENDING. BLOCKED giữ lý do, không tự resend. lesson_passed là sự kiện server nên không có hàng client tương ứng. Không đưa token/email/password vào ảnh chụp báo cáo.

| Ca | Thao tác và kỳ vọng |
|---|---|
| Nâng cấp | Ghi số từ, review/session dở và queue trước backup; install -r rồi kiểm tra vẫn còn, không đăng xuất hoặc xóa dữ liệu để che lỗi nâng cấp |
| Mặc định/từ chối | Account dev mới: dialog hai lựa chọn ngang mức; chọn Không, cảm ơn, Settings vẫn tắt, học/ôn bình thường, không có batch telemetry |
| Bật online | Settings → bật → Cho phép: chỉ bật sau server ack; đưa app ra nền rồi mở lại, học/nộp/ôn; theo dõi Room AnalyticsOutbox và sự kiện server dev để xác nhận hooks |
| Tắt/bật lại | Tắt trong Settings, queue analytics bị purge; queue từ/review/bài còn nguyên. Bật lại online, không gửi các sự kiện cũ trước consent mới |
| Bật offline | Ngắt API rồi thử bật: hiện lỗi/chưa bật, không enqueue/send telemetry; kết nối lại và chủ động chọn Cho phép để nhận ack |
| Học offline khi đã đồng ý | Sau ack online, ngắt API, học/ôn, reopen; AnalyticsOutbox giữ PENDING. Kết nối lại: batch retry/dedupe, queue nghiệp vụ vẫn chạy |
| Tắt offline | Tắt ngay trên máy, AnalyticsOutbox rỗng và pendingOptOut; reconnect gửi consent tắt trước bất kỳ batch. Muốn bật lại phải hoàn tất đồng bộ tắt |
| Hai account/lifecycle | A có queue, logout → B: không dispatch A bằng B. Đăng nhập lại A, queue vẫn thuộc A; thử xoay/process death/nâng cấp, ghi kết quả thực tế, không coi test Robolectric là bằng chứng thiết bị |

Chưa ai chạy các ca này trên thiết bị trong checkpoint. WorkManager có thể chờ hệ thống/lịch mạng; không khẳng định gửi ngay tức thì. Analytics không có UI debug queue riêng, kiểm chứng dữ liệu bằng Database Inspector/log API dev hoặc truy vấn database dev.
