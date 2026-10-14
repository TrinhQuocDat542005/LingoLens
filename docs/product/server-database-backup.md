# Sao lưu server trước V14/V15

Các lệnh dưới đây là hướng dẫn cho chủ dự án; chưa chạy sao lưu/khôi phục trên database mục tiêu. Compose hiện dùng postgres15, user/database `lingolens`, cổng host5433. Nếu database/credentials thực tế khác, dùng giá trị thực tế. Chưa kiểm chứng backup/log retention của hosting, không coi là cam kết7 ngày.

Sao lưu trước khi chạy backend có migration mới. Dừng backend để không có writer trong lúc chọn điểm khôi phục; nếu chạy `bootRun` ở terminal thì dừng terminal đó nữa. `pg_dump` vẫn tạo snapshot nhất quán.

```powershell
Set-Location E:\Projects\LingoLens
docker compose stop backend
$backupDir = Join-Path $env:USERPROFILE 'LingoLens-backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$dumpName = 'before-v14-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.dump'
$backupFile = Join-Path $backupDir $dumpName
docker compose exec -T postgres pg_dump -U lingolens -d lingolens -Fc -f "/tmp/$dumpName"
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }
$pgCid = (docker compose ps -q postgres).Trim()
docker cp "${pgCid}:/tmp/$dumpName" $backupFile
if ($LASTEXITCODE -ne 0) { throw 'Backup copy failed' }
Get-FileHash $backupFile -Algorithm SHA256
```

`-Fc` là định dạng custom. File được ghi trong container rồi docker cp ra máy, tránh PowerShell redirect làm hỏng binary. Sao lưu chứa thông tin tài khoản/hash/token và lịch sử học: giữ trong thư mục riêng được bảo vệ, không commit hoặc upload công khai. Thay tiền tố before-v14 thành before-v15 nếu đã rollout V14.

Khôi phục vào **database mới**, giữ database cũ để đối chiếu. Chạy tiếp trong cùng PowerShell có biến ở trên; terminal mới phải đặt `$backupFile` đúng file đã lưu và `$dumpName` đúng tên mong muốn trong container.

```powershell
$restoreDb = 'lingolens_restore_' + (Get-Date -Format 'yyyyMMddHHmmss')
$pgCid = (docker compose ps -q postgres).Trim()
docker cp $backupFile "${pgCid}:/tmp/$dumpName"
if ($LASTEXITCODE -ne 0) { throw 'Restore copy failed' }
docker compose exec -T postgres createdb -U lingolens $restoreDb
if ($LASTEXITCODE -ne 0) { throw 'createdb failed' }
docker compose exec -T postgres pg_restore -U lingolens -d $restoreDb --exit-on-error --no-owner --no-privileges "/tmp/$dumpName"
if ($LASTEXITCODE -ne 0) { throw 'Restore failed' }
docker compose exec -T postgres psql -U lingolens -d $restoreDb -c "SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;"
if ($LASTEXITCODE -ne 0) { throw 'Restore verification failed' }
```

Đối chiếu counts và sample rows với snapshot trước rollout, kiểm tra Flyway history, users/settings/tokens/words/review/receipts/attempts/answers/progress/XP. Chỉ sau khi kiểm chứng mới cấu hình backend vào database khôi phục (`jdbc:postgresql://127.0.0.1:5433/<restoreDb>` nếu chạy trên host; port5432 và hostpostgres nếu trong Compose). Không chạy backend mới vào bản khôi phục để “rollback V14”: backend mới sẽ tự áp migration lại. Khôi phục trước rollout cần đúng bản backend và phải đánh giá việc token cũ sống lại. Restore rehearsal, purge analytics hết hạn và áp deletion manifest trước mở traffic là release gate chưa thực hiện.

Không dùng `docker compose down -v`, không xóa volume đang chứa dữ liệu. Không sao chép thẳng volume live để thay pg_dump.
