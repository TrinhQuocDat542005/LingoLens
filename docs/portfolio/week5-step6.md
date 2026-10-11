# Tuần 5 — Bước 6: ma trận nghiệm thu và bằng chứng

Ngày kiểm tra: **11/10/2026**, múi giờ Asia/Saigon (UTC+07:00). Trạng thái: **đã lập báo cáo, chờ chủ dự án duyệt PR tài liệu**; chưa bắt đầu tuần 6.

## 1. Phân biệt code đã merge và tài liệu mới

- Code bước 1–5 đã được merge vào `main` trong [PR #2](https://github.com/TrinhQuocDat542005/LingoLens/pull/2), merge commit [`e1da7b7`](https://github.com/TrinhQuocDat542005/LingoLens/commit/e1da7b7dc3b2732616d92ccf673f900b5a4f73ed).
- Lượt nghiệm thu này checkout `main`, fast-forward tới `e1da7b7dc3b2732616d92ccf673f900b5a4f73ed`, rồi chạy lại kiểm tra trước khi tạo nhánh `docs/week5-acceptance`.
- **PR bước 6 chỉ thêm tài liệu này.** Không đưa lại code của PR #2 vào diff, không sửa code, migration, test, dependency hoặc workflow.
- Các commit ở bảng dưới là bằng chứng lịch sử của code đã merge, không phải thay đổi mới trong PR tài liệu.
- `.idea/misc.xml` và `.idea/markdown.xml` có thay đổi cục bộ của người dùng; được giữ nguyên và không đưa vào commit/PR.

| Bước đã merge | Commit | Phạm vi lịch sử |
| --- | --- | --- |
| 1 | `5477f74` | V9, learner_settings, preflight và profile adapter |
| 2 | `ec5ed6c` | V10–V12, JSON/schema, runner DEMO và production guard |
| 3 | `d59158c` | Chấm có trọng số, ngưỡng 70% trước làm tròn |
| 4 | `42a6e44` | API attempt/progress/XP, retry, ownership, transaction và concurrency |
| 5 | `93ec286` | Android bài đầu, SavedStateHandle, retry và tổng kết |

## 2. Ma trận tiêu chí hoàn thành tuần 5 — mục 10 đặc tả v2

Nguồn: [mục 10 và hợp đồng tuần 5 tại mục 13](../product/lingolens-spec-v2.md). Từng tiêu chí trong hàng tuần 5 được đối chiếu riêng. “Đạt” áp dụng trong phạm vi dev/test và online đã duyệt, không đồng nghĩa đủ điều kiện phát hành production.

| Tiêu chí mục 10 | Trạng thái | Bằng chứng cụ thể | Lý do / giới hạn |
| --- | --- | --- | --- |
| DB mới nạp được nội dung | **Đạt** | `ContentSeedTests` → `demo import is explicit atomic and checksum stable`, `semantic seed errors are rejected before writes`, `invalid seed fails before any write and collision rolls back`; commit `ec5ed6c`. Testcontainers PostgreSQL tạo schema mới, migrate rồi nhập gói: 3 lessons, 24 questions, 18 lesson_vocabularies; legacy V5 không đổi. | Gói DEMO được kiểm tra cấu trúc/ngữ nghĩa và nhập nguyên tử; chưa có nội dung REVIEWED. Xem gate nội dung ở phần 3. |
| Một bài chạy từ học tới progress/XP | **Đạt** | `LessonGraderTests` → `eight equal weight questions require six correct`, `weighted result below 70 never passes after rounding`; `LessonAttemptPostgresTests` → `first pass grants XP once and all retry results are stable`, `practice and failed lesson do not grant XP or unlock`. Commits `d59158c`, `42a6e44`, `93ec286`; xác nhận test tay M1–M3 ở phần 5. | Bài đầu có UI học → trả lời → nộp → tổng kết; backend lưu progress và thưởng 30 XP lần đầu. Android không tự chấm hoặc quyết định XP. Không diễn giải thành cả ba bài đã hoàn thiện UI. |
| Retry không cấp thêm | **Đạt** | `LessonAttemptPostgresTests` → `first pass grants XP once and all retry results are stable`, `concurrent same UUID has one attempt and one award`, `concurrent distinct UUIDs have two attempts and one award`; `LessonScreenModelTest` → `network retry freezes UUID timestamp and answers`, `double submit sends once while request is in flight`. Commits `42a6e44`, `93ec286`; test tay M4/M6. | Retry cùng UUID giữ kết quả; học lại bằng UUID mới cũng không nhận thưởng LESSON lần hai. Hai ca concurrent chạy trên PostgreSQL thật; UI mất kết nối cho gửi lại thủ công, chưa có outbox. |
| Payload đổi UUID bị từ chối | **Đạt** | `LessonAttemptPostgresTests` → `changed UUID payload is conflict and other accounts cannot read or reuse results`, `authenticated HTTP retry and result ownership enforce the same contract`; commit `42a6e44`. | Cụm trong mục 10 được đọc theo hợp đồng rõ ở mục 13.4: **UUID cũ + payload khác → 409**. Đổi sang UUID mới để bắt đầu attempt mới là hợp lệ; không tuyên bố mọi UUID mới đều bị từ chối. |
| Account isolation | **Đạt** | `LessonAttemptPostgresTests` → `changed UUID payload is conflict and other accounts cannot read or reuse results`, `authenticated HTTP retry and result ownership enforce the same contract`; commit `42a6e44`. | Account khác nhận 404 khi đọc hoặc tái dùng attempt của chủ sở hữu; có kiểm tra HTTP có xác thực, không chỉ gọi service. |
| Profile cũ không mất mục tiêu | **Đạt** | `LearnerSettingsMigrationTests` → `V9 preserves legacy values and all V8 user data`; `PostgresIntegrationTests` → `register legacy profile PUT and admin all use settings adapter`, `profile update rolls back both mirrors when settings write fails`, `concurrent profile PUTs leave a complete last writer mirror`, `login refresh FK is not blocked by an uncommitted profile update`; commit `5477f74`. | Backfill giữ B1/B2 và daily_goal khác nhau. GET/PUT cũ, đăng ký và admin đi qua adapter; mirror cùng transaction. Bằng chứng trên fixture PostgreSQL, **chưa chạy preflight trên database mục tiêu**. |

Kết luận đối với sáu tiêu chí chức năng trên: **Đạt trong phạm vi tuần 5 đã duyệt**. Các gate bổ sung chưa đủ bên dưới được giữ riêng, không bị che bởi kết quả chức năng.

## 3. Hợp đồng bổ sung và gate còn thiếu

| Hạng mục | Trạng thái | Bằng chứng và lý do |
| --- | --- | --- |
| Ngưỡng 70% có trọng số trước làm tròn; UI số câu đúng/tổng | **Đạt** | `LessonGraderTests`: `exact 70 percent passes without floating point`, `weighted result below 70 never passes after rounding`, `above 70 passes and percentage is floor`; commit `d59158c`. Test tay tổng kết đúng/sai do chủ dự án xác nhận. |
| Atomic attempt/answers/progress/XP và hai ca concurrent PostgreSQL | **Đạt** | `LessonAttemptPostgresTests`: `failure at XP write rolls back attempt answers progress and award`, `concurrent same UUID has one attempt and one award`, `concurrent distinct UUIDs have two attempts and one award`; commit `42a6e44`. Cùng UUID: một attempt/một thưởng; khác UUID: hai attempts và một thưởng. |
| Partial unique indexes và revision/snapshot bất biến | **Đạt** | `ContentSeedTests`: `partial indexes enforce at most one published revision and active enrollment`, `published revision snapshots questions and options are immutable`; commit `ec5ed6c`, PostgreSQL thật. |
| DEMO chỉ dev/test, checksum/import một lần, production guard | **Đạt** | `ContentSeedTests`: `demo import is explicit atomic and checksum stable`, `concurrent same pack imports have one effect`, `production refuses seed and guard detects orphan demo content`, `production guard fails bean initialization before serving requests`; commit `ec5ed6c`. CLI cũng kiểm tra profile trước kết nối DB/migration; test từ chối profile nằm ở importer/guard, không phải test chạy CLI subprocess. |
| UUID/state qua rotation và process recreation | **Đạt một phần** | `LessonScreenModelTest`: `rotation retains attempt UUID answers and current question`, `system saved state recreation retries identical payload without old ViewModel`; commit `93ec286`. Robolectric tạo lại state từ Bundle và test tay do chủ dự án xác nhận là bằng chứng một phần. **Chưa có instrumentation lifecycle/process death trên thiết bị thật**; không suy ra mọi tình huống thu hồi process đều đã kiểm chứng. |
| Nội dung chặng đầu được người khác rà soát trước nghiệm thu, theo mục 10.1 | **Đạt một phần** | Có đủ gói DEMO ba bài/18 từ/24 câu và validation kỹ thuật; chưa có người rà soát độc lập hoặc gói REVIEWED. Hợp đồng mục 13.3 cho DEMO dev/test, nhưng không hoàn thành yêu cầu chất lượng nội dung mục 10.1. |
| Preflight trên database mục tiêu trước triển khai | **Chưa đạt** | Đã có công cụ/test: `LearnerSettingsPreflightTests` → `valid A1 B1 B2 and distinct goals produce no findings or mutations`, `invalid rows report exact ids values and reasons without identity secrets`, `preflight opens a read only transaction enforced by PostgreSQL`. Chủ dự án cho bỏ qua chạy DB mục tiêu ở giai đoạn dev; phải chạy lại trước deploy. |

Không coi UI bài 2/3, PRACTICE UI hoặc offline lesson là tiêu chí tuần 5 bị thiếu: chúng chưa nằm trong phạm vi UI tuần 5 đã duyệt. Tuy nhiên vẫn liệt kê ở việc còn lại để tránh hiểu nhầm sản phẩm đã đầy đủ.

## 4. Kiểm tra chạy lại trên main trước PR tài liệu

Code được kiểm tra: `e1da7b7dc3b2732616d92ccf673f900b5a4f73ed`. Chạy ngày 11/10/2026 khoảng 20:25–20:27 UTC+07:00; các task test được yêu cầu chạy lại bằng `--rerun`, không dùng số liệu lịch sử thay kết quả mới.

| Kiểm tra | Kết quả lần chạy này | Nguồn bằng chứng |
| --- | --- | --- |
| Android unit/Robolectric | **51 test đạt**, 0 failures/errors/skipped | `app/build/test-results/testDebugUnitTest/TEST-*.xml` |
| Backend test — H2 và unit | **23 test đạt**, 0 failures/errors/skipped | `backend/build/test-results/test/TEST-*.xml` |
| PostgreSQL Testcontainers | **33 test đạt**, 0 failures/errors/skipped | `backend/build/test-results/postgresTest/TEST-*.xml`, tạo mới khoảng 20:27 |
| Android lint | **0 lỗi, 38 warnings, 1 hint; 0 warning mới** | `app/build/reports/lint-results-debug.xml`; `Assert-LintBaseline.ps1` → `Lint gate passed: errors=0, warnings=38, baseline=38, new=0` |
| Admin TypeScript/Vite | **Build thành công** | `npm run build` → `tsc -b && vite build`, exit 0. Là build, không tính là test. |

Tổng **107 tests, 0 failures/errors/skipped**. Báo cáo XML/HTML nằm trong thư mục build bị ignore, không được commit vào PR tài liệu. Các tên test trong ma trận trỏ tới mã test đã merge; bảng này ghi lại kết quả thực thi mới.

Lệnh thực thi từ PowerShell (JDK21 từ Android Studio, Docker Desktop hoạt động):

```powershell
Set-Location E:\Projects\LingoLens
$env:JAVA_HOME = 'E:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME = 'C:/Users/ASUS/AppData/Local/Android/Sdk'
.\gradlew.bat :app:testDebugUnitTest --rerun :app:lintDebug --rerun --max-workers=2 --console=plain
.\scripts\quality\Assert-LintBaseline.ps1
Push-Location backend
.\gradlew.bat test --rerun postgresTest --rerun --max-workers=2 --console=plain
Pop-Location
Push-Location admin-dashboard
npm run build
Pop-Location
```

Không chạy lại `assembleDebug` hoặc `bootJar` tại checkpoint local này; không gọi kết quả packaging cũ là kết quả mới. CI main dưới đây có kiểm tra APK/bootJar và Docker startup riêng.

### GitHub Actions trên main sau merge

[CI run 38142725986](https://github.com/TrinhQuocDat542005/LingoLens/actions/runs/38142725986), event `push`, head SHA `e1da7b7dc3b2732616d92ccf673f900b5a4f73ed`, tạo lúc **13:19:41 UTC ngày 11/10/2026**; trạng thái đọc lúc nghiệm thu: **completed / success**.

| Job | Trạng thái |
| --- | --- |
| [Backend / tests and PostgreSQL](https://github.com/TrinhQuocDat542005/LingoLens/actions/runs/38142725986/job/114479720875) | success |
| [Android / tests, lint and debug APK](https://github.com/TrinhQuocDat542005/LingoLens/actions/runs/38142725986/job/114479720946) | success |
| [Admin / TypeScript and production build](https://github.com/TrinhQuocDat542005/LingoLens/actions/runs/38142725986/job/114479720972) | success |
| [Docker / API startup smoke test](https://github.com/TrinhQuocDat542005/LingoLens/actions/runs/38142725986/job/114479721060) | success |

Đây là CI của **code main sau PR #2**, không phải kết quả CI của PR tài liệu mới. Workflow hiện chạy lint nhưng không gọi script so baseline 38; gate baseline đã được chạy riêng tại máy và ghi ở bảng trên.

## 5. Test tay do chủ dự án xác nhận

Sau hướng dẫn chạy backend dev, seed DEMO và cài app, chủ dự án phản hồi: **“ok đã test hết và luông ổn hết đều đạt”**. Xác nhận này được ghi theo bộ kịch bản đã hướng dẫn, không phải lượt test mới do agent thực hiện. Không có video, log thiết bị, API level hoặc instrumentation report kèm theo để tái kiểm chứng độc lập.

| Mã | Kịch bản đã hướng dẫn | Bằng chứng / giới hạn |
| --- | --- | --- |
| M1 | Đăng ký/đăng nhập, vào Trang chủ → Mở bài học, xem DEMO | Chủ dự án xác nhận bộ test tay đạt. |
| M2 | Mở bài đầu, học sáu từ, trả lời tám câu đúng/sai | Chủ dự án xác nhận; không suy ra UI bài 2/3 đã được test. |
| M3 | 5/8 trượt, không XP; 6/8 đạt, tổng kết và +30 XP lần đầu | Chủ dự án xác nhận theo kịch bản; test PostgreSQL kiểm tra riêng dữ liệu progress/XP. |
| M4 | Học lại cùng bài, đạt nhưng +0 XP; tổng giữ nguyên | Chủ dự án xác nhận; test retry/concurrency cung cấp bằng chứng backend độc lập. |
| M5 | Xoay màn hình giữa chừng giữ câu/đáp án | Chủ dự án xác nhận; chỉ là một phần bằng chứng lifecycle, chưa instrumentation thiết bị thật. |
| M6 | Dừng backend lúc nộp, khởi động lại, bấm gửi lại | Chủ dự án xác nhận; đây là retry trực tiếp, không là offline pack hoặc outbox tự đồng bộ. |
| M7 | Process bị thu hồi khi app ở background | Có hướng dẫn bổ sung `adb shell am kill` và mở từ Recents, nhưng **không có xác nhận riêng ca này**. Robolectric saved-state recreation chỉ là bằng chứng một phần, chưa đủ kết luận process death trên thiết bị thật. |

SavedStateHandle bảo vệ state hệ thống đã lưu; không phải durable storage. Không dùng xác nhận chung “test hết” để quảng cáo khôi phục sau vuốt bỏ task, force-stop hoặc reboot.

## 6. Các điểm đã rà soát trước nghiệm thu

1. **Collision seed:** DEMO gồm hello/goodbye/please/thanks/yes/no, water/bread/rice/milk/tea/coffee, mother/father/sister/brother/family/friend. V5 gồm cat/dog/book/cup/bottle/laptop/keyboard/mouse/phone/apple/banana/orange. Không có từ trùng. Importer chỉ tái dùng ID khi translation, phonetic, part_of_speech, definition, example_sentence và level khớp; không so mọi cột như synonyms/example_sentence_b2. Collision không tương thích làm rollback cả gói; không upsert từ cũ.
2. **Production:** `DemoSeedCli` từ chối prod/production trước tạo datasource và migrate, kể cả bật kèm dev/test. Importer kiểm tra lại. Startup guard phát hiện DEMO qua receipt và course/revision, kể cả thiếu receipt. Không tự nhập khi server khởi động.
3. **Concurrency:** hai ca attempt concurrent thật trên PostgreSQL đều có test như phần 3. H2 chỉ kiểm tra hợp đồng API/thuật toán, không thay PostgreSQL cho khóa/index/concurrency.
4. **Profile lock:** production dùng last-write-wins, hai UPDATE chọn lọc users → learner_settings cùng transaction; bỏ SELECT khóa bi quan do adapter mới thêm, không thay bằng FOR UPDATE để chiều H2. Test PostgreSQL chèn refresh token khi transaction profile chưa commit đã đạt. Khóa cấp XP là nghiệp vụ khác, vẫn dùng FOR NO KEY UPDATE.
5. **Admin vocabulary:** sửa từ gốc vẫn được phép; lesson đã xuất bản đọc content_snapshot nên không bị sửa nội dung theo. Xóa từ đang có FK từ lesson_vocabularies bị DB từ chối; handler Exception chung hiện trả 500. Đây là đối chiếu code/schema, **chưa có test HTTP riêng cho admin delete collision**, không tuyên bố lỗi này đã được kiểm thử end-to-end.
6. **Migration cũ:** `git diff 85d3828 e1da7b7 --name-only` giới hạn ở V1–V8 không trả file thay đổi. Test V8 → V9 so snapshot users/password, roles/user_roles, refresh_tokens, learning_entries, review_cards/review_events và sync_receipts. Không gọi đây là snapshot kiểm tra database dev của chủ dự án; test dùng container riêng.

## 7. Việc còn lại / rủi ro đã biết

| Việc / rủi ro | Hiện trạng | Hướng xử lý đề xuất, chưa triển khai trong PR này |
| --- | --- | --- |
| Admin xóa vocabulary đang được lesson tham chiếu | FK chặn xóa, handler chung trả **500**; chưa có thông báo nghiệp vụ | Trả **409** kèm thông báo từ đang được bài học sử dụng; bổ sung test PostgreSQL/HTTP. Giữ nguyên code hiện tại theo yêu cầu. |
| Vuốt tắt/bỏ task hoặc khởi động lại máy | Không có durable session để bảo đảm UUID/answers sống sót; SavedStateHandle chưa đáp ứng các ca này | Lưu session/attempt bằng **Room ở tuần 6** theo kế hoạch; chưa bắt đầu thực hiện. Cần instrumentation trên thiết bị thật cho lifecycle/process death. |
| Preflight DB mục tiêu | Chỉ chạy fixture; chưa có báo cáo trên database triển khai | Chạy preflight chỉ đọc trước deploy, xử lý dòng lỗi trước V9, lưu bằng chứng; không tự đổi B1/B2 hay reset DB. |
| Chất lượng nội dung DEMO | Ba bài là nội dung tạm, chưa được rà soát độc lập | Người duyệt khác người biên soạn; kiểm tra nghĩa/đáp án/nhiễu/en-US, rồi đưa REVIEWED qua migration chính mới. Không sửa gói DEMO đã nhập để đổi trạng thái. |
| UI bài 2/3 | Có seed/backend, chưa hoàn thiện luồng UI | Hoàn thiện trong phạm vi tuần 6 sau khi được chủ dự án duyệt. |
| PRACTICE | Backend/test có; chưa có UI | Giữ đúng hợp đồng tuần 5; chưa bổ sung UI. |
| API lỗi JSON/path hỏng | Một số lỗi vẫn đi qua handler legacy, chưa bao phủ toàn bộ | Rà soát riêng sau duyệt, không coi toàn bộ API error handling đã hoàn tất. |

## 8. Quyết định chờ chủ dự án

PR này ghi nhận bước 6 ở mức **báo cáo nghiệm thu**, không tự duyệt các gate còn thiếu và không tự cho phép phát hành. Sáu tiêu chí chức năng mục 10 có bằng chứng đạt; lifecycle/process death và chất lượng nội dung đạt một phần; preflight database mục tiêu chưa đạt.

**Dừng sau khi mở PR tài liệu, chờ chủ dự án duyệt. Không merge PR và không bắt đầu tuần 6.** Các báo cáo bước 1–5 giữ nguyên như lịch sử; trạng thái hiện tại về merge/test tay/CI được cập nhật tại tài liệu này.
