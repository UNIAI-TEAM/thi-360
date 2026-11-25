# Kế hoạch: App học viên native (Android, iOS) — khung ứng dụng và xác thực

> Status: approved 2026-11-25 (G2, M.Trường) · Version: 4 · Spec: `docs/specs/2026-11-25-app-hoc-vien-khung-xac-thuc.md` bản 6 (bản 5 duyệt 2026-11-25; bản 6 thêm thay đổi yêu cầu D-35, D-36 do người yêu cầu quyết cùng ngày)
> Tracking: UNI-2785 (issue cha). Sub-issue của từng task: BE-1 UNI-2798 · WEB-1 UNI-2799 · DOC-1 UNI-2800 · AND-1 UNI-2801 · IOS-1 UNI-2802 · AND-2 UNI-2803 · IOS-2 UNI-2804 · AND-3 UNI-2805 · IOS-3 UNI-2806 · AND-4 UNI-2807 · IOS-4 UNI-2808 · QA UNI-2809.

Quy ước mã: `R-n`, `D-n`, `Q-n` là của đặc tả. Tiêu chí chấp nhận của đặc tả viết `ĐT n` (ví dụ `ĐT 14` là AC-14 của đặc tả). Trong Phần D, `AC-n` là tiêu chí riêng của task.

# Part A: Run setup

## A1. Repository and branches

- Kho `UNIAI-TEAM/thi-360`, nhánh mặc định `main`. PR cuối của đợt chạy vào `develop`.
- Nhánh tích hợp: `integration/app-hoc-vien`, tách từ `origin/develop` tại `31856a31`. Trước khi mở lane đầu tiên, Orchestrator kiểm lại đầu `develop`; nếu đã đổi thì ghi SHA mới vào manifest.
- Nhánh task: `feature/UNI-<issue>-<ten-ngan>`, PR vào `integration/app-hoc-vien`.
- Tài liệu (đặc tả, bản thiết kế, kế hoạch này) đang ở nhánh `docs/UNI-2785-app-hoc-vien-khung-xac-thuc`; Orchestrator đưa nhánh này vào nhánh tích hợp trước sóng 1.

## A2. Gates

| Gate | Condition | State |
| --- | --- | --- |
| G1 | Đặc tả được duyệt | đạt 2026-11-25 (bản 5, D-29) |
| G2 | Kế hoạch này được duyệt; `plan-lint` 0 lỗi | đạt 2026-11-25 (bản 4; `plan-lint` 0 lỗi, 0 cảnh báo) |
| G3 | Người yêu cầu ra lệnh bắt đầu triển khai; roster được xác nhận; nhánh gốc được kiểm | chờ |
| G-MAC | Máy `devin-mac` có agent CLI đã cài và đăng nhập (D-12) | đạt 2026-11-25: AGY CLI 1.5.16 ở `~/.local/bin/agy`; người yêu cầu báo đã đăng nhập |
| G-XCODE | Xcode 27.1 có trên `devin-mac` (D-30) | đạt 2026-11-25: Xcode 27.1 (27A9275), runtime iOS 27.1, simulator iPhone Duo khởi động được |
| G-TEAMID | Người yêu cầu cấp Apple Team ID (D-14) | chờ, chỉ cần khi triển khai tệp xác minh thật |

`G-MAC` chặn mọi task iOS và đã đạt. `G-XCODE` đã đạt nên các phép kiểm iPhone Duo chạy trên simulator thật; kích thước đo được là 466×678 điểm (màn ngoài) và 669×951 điểm (màn trong). `G-TEAMID` không chặn task nào: tệp xác minh đọc Team ID từ biến môi trường.

## A3. What the run may do

- Lane được commit, đẩy nhánh task, mở PR vào `integration/app-hoc-vien`. Lane owner nghiệm thu; Orchestrator chạy lại phép kiểm rồi merge vào nhánh tích hợp.
- Lane được đặt issue của task sang `in_progress`, `in_review`, `blocked` và bình luận. Không đặt `done`.
- PR cuối `integration/app-hoc-vien` → `develop` chỉ được mở, không merge.
- Không triển khai lên bản demo, không sửa realm trên Keycloak dùng chung, không phát hành app. Các việc đó nằm ngoài đợt chạy.
- Không sửa migration đã merge, không `--force`, không `--no-verify` (`RULES.md` mục 7).

## A4. Tracking

- Sub-issue của từng task đã tạo ngày 2026-11-25 (mã ở dòng Tracking đầu tài liệu); Orchestrator ghi mã vào manifest khi mở đợt chạy.
- Mỗi lần lane dừng: bình luận `[agent]` trên issue của task (đã làm gì, lệnh đã chạy, còn gì, bị chặn bởi gì).
- `PROGRESS.md` và `TASK.md` chỉ do Orchestrator và task QA sửa (B2).

## A5. Environment

Hồ sơ dự án: `D:/.Vietants_Project/.thi360-dev/mobile-shell/project-profile.md`. Quy tắc máy build iOS: `D:/.Vietants_Project/.thi360-dev/mobile-shell/hosts-devin-mac.md`.

| Việc | Lệnh | Chạy ở đâu |
| --- | --- | --- |
| Backend, toàn bộ | `cd backend && ./mvnw -B -ntp verify` | máy chính hoặc cloud runner |
| Backend, một lớp IT | `cd backend && ./mvnw -B -ntp verify -Dit.test=<Lớp> -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false` | máy chính |
| Backend, một lớp unit | `cd backend && ./mvnw -B -ntp test -Dtest=<Lớp>` | máy chính |
| OpenAPI và client | `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false && cd .. && diff -u packages/api-client/openapi.json backend/target/openapi.json && pnpm --filter @thi360/api-client generate && git diff --exit-code packages/api-client && pnpm typecheck` | máy chính |
| Web, toàn bộ | `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build` | máy chính |
| Web, một tệp test | `pnpm --filter @thi360/web-student test <tệp>`; `pnpm --filter @thi360/ui test <tệp>` | máy chính |
| Android, build | `cd apps/android && ./gradlew :app:assembleDevDebug :app:assembleDemoDebug` | máy chính |
| Android, unit và giao diện trên JVM | `cd apps/android && ./gradlew :app:testDevDebugUnitTest --tests '<mẫu>'` | máy chính |
| Android, ảnh chụp màn | `cd apps/android && ./gradlew :app:verifyRoborazziDevDebug` | máy chính |
| Android, lint | `cd apps/android && ./gradlew :app:lintDevDebug` | máy chính |
| Android, trên máy ảo | `cd apps/android && ./gradlew :app:connectedDevDebugAndroidTest` (máy ảo Pixel 7 API 34) | máy chính hoặc CI |
| iOS, chọn Xcode | `export DEVELOPER_DIR=/Applications/Xcode-27.1.0.app/Contents/Developer` trước mọi lệnh iOS (Xcode mặc định của máy vẫn là 26.6) | `devin-mac` |
| iOS, sinh dự án | `cd apps/ios && xcodegen generate` | `devin-mac` |
| iOS, build và test | `cd apps/ios && xcodebuild -project Thi360.xcodeproj -scheme Thi360Dev -destination 'platform=iOS Simulator,name=iPhone 17' test [-only-testing:<Target>/<Lớp>]` | `devin-mac` |
| iOS, build cấu hình demo | `cd apps/ios && xcodebuild -project Thi360.xcodeproj -scheme Thi360Demo -destination 'generic/platform=iOS Simulator' build` | `devin-mac` |

- Bản công cụ: JDK 21, Node 22 trở lên, pnpm 10 như hiện có. Android: Android Gradle Plugin và Kotlin bản ổn định mới nhất lúc mở AND-1, `compileSdk` mới nhất, `minSdk` 26; ghi bản đã chọn vào `apps/android/gradle/libs.versions.toml`. iOS: Xcode 27.1; XcodeGen cài bằng Homebrew; ghi bản vào `apps/ios/README.md`. Simulator dùng trong lệnh: `iPhone 17` (runtime iOS 26.5), `iPad Air 11-inch (M4)`, `iPhone Duo` (runtime iOS 27.1); lane tự tạo máy còn thiếu trên runtime 27.1 bằng `xcrun simctl create` ở đầu bước.
- Máy chính: ổ C: còn dưới 10 GB. Lane Android đặt `GRADLE_USER_HOME` và `ANDROID_AVD_HOME` sang ổ D: trước lệnh Gradle đầu tiên.
- `devin-mac`: chỉ một lane iOS chạy tại một thời điểm. Trước mỗi lượt giao việc phải kiểm máy thức; bước có khoảng lặng dài thì `touch ~/.keep-awake` trước và `rm ~/.keep-awake` khi xong; mỗi bước build và test khởi động lại simulator ở đầu bước.
- Backend và web chạy như các đợt trước: test tích hợp dùng Testcontainers; stack dev của lane dùng compose project riêng `thi360-<task>`.
- Model của từng vai xác nhận lại ở G3 theo `~/.agents/zone17-roster.json`: lane iOS chạy bằng AGY trên `devin-mac` dùng bộ Antigravity (các khóa ở gốc tệp); mọi lane khác dùng khối `ompRoles`.

## A6. Acceptance common to every task

- Tiêu chí xong của `RULES.md` mục 8.
- `worker-principles.md` đạt; gói bằng chứng của task qua `verify-evidence.mjs` (thoát 0): test theo kế hoạch đã chạy trên đúng `tested_sha`, phạm vi hồi quy xanh, số test trước và sau được đối chiếu.
- Task rủi ro `high` có bằng chứng `red-first`: test mới được chứng minh là đỏ trên `base_sha` trước khi có mã.
- Mật khẩu, mã OTP, token, mã đặt lại không xuất hiện trong log, ảnh chụp, bằng chứng và bình luận issue.
- Chuỗi hiển thị có đủ hai bản `vi`, `en` cùng bộ khóa.

# Part B: Conventions for this run

## B1. Foundational contracts decided first

**B1.1 Phản hồi token của API mobile** (BE-1 sở hữu):

```text
MobileTokenResponse { accessToken: string, refreshToken: string,
                      accessExpiresIn: int (giây), refreshExpiresIn: int (giây),
                      mustChangePassword: boolean }
Lỗi: { code, message, fieldErrors[], traceId } như hiện có. Mã mới: APP_STUDENT_ONLY (403).
```

`refreshExpiresIn` do backend tự đặt bằng thời hạn không dùng của phiên offline trong realm (604800 giây), vì Keycloak trả 0 cho offline token (thử nghiệm Q-4). Một test khẳng định hai giá trị này bằng nhau (BE-1). Phiên còn bị cắt ở mốc 30 ngày kể từ lúc đăng nhập (D-35); app không dựa vào con số này để tự kết luận hết hạn.

**B1.2 Kênh đăng nhập** (BE-1): `LoginChannel { WEB, MOBILE }` lưu cùng bản ghi phiên ở Redis. Kênh quyết định client Keycloak dùng khi làm mới và đăng xuất, và việc kiểm `APP_STUDENT_ONLY` khi làm mới.

**B1.3 Máy trạng thái phiên của app** (AND-2, IOS-2 hiện thực cùng một hình):

```text
SignedOut(reason?: Expired | StudentOnly(username) | PasswordReset | None)
  -- đăng nhập đúng --> MustChangePassword | Active
MustChangePassword -- đổi xong --> Active        -- đăng xuất --> SignedOut
Active -- rời app > 5 phút hoặc mở nguội, đã bật sinh trắc học --> Locked
Locked -- sinh trắc học đúng --> Active           -- "Nhập mật khẩu" --> màn Đăng nhập (phiên còn)
Active | Locked -- 401 khi làm mới, đăng xuất, đặt lại mật khẩu --> SignedOut(reason)
```

Mọi màn chỉ đọc trạng thái này; không màn nào tự giữ token.

**B1.4 Bảng màu cho app** (WEB-1 sở hữu). Web học viên có một route mới, là nơi duy nhất chạy `deriveTokens`. Route đọc `GET /api/v1/appearance` rồi trả cả cấu hình lẫn bảng màu đã suy ra; app chỉ gọi route này:

```text
GET {THI360_WEB_URL}/mobile/appearance-tokens.json   (công khai)
200 { "version": int, "mode": "light"|"dark", "presetId": string,
      "light": Palette, "dark": Palette }
    ETag = version hiện tại; Cache-Control: no-cache; If-None-Match khớp thì 304
503 khi backend lỗi, hoặc khi `presetId` của backend không có trong danh sách bộ màu của web
Palette { primaryFill, onPrimary, primaryText, primaryContainer, primaryContainerStrong,
          accent, onAccent, bgPage, bgSurface, bgSubtle, text, textSecondary, textMuted,
          border, borderStrong, dangerText, dangerBg }   // mỗi giá trị là "#RRGGBB"
```

- Route luôn trả phiên bản hiện tại; không có tham số phiên bản và không cho cache dài, nên không có bản cũ nằm lại ở cache chung.
- App áp phản hồi 200 khi `version` khác bản đang lưu; mọi kết quả khác (503, lỗi mạng, JSON sai hình) thì giữ bảng đang dùng. Nhờ nhánh 503 cho `presetId` lạ, app không bao giờ rơi về màu mặc định một cách im lặng (ĐT 77).
- Không port công thức suy ra màu sang Kotlin hay Swift.
- Người yêu cầu đã đồng ý cách lấy qua route của web (D-36); đặc tả bản 6 ghi lại điều này ở R-12.

**B1.5 Tệp hợp đồng dùng chung** ở `contracts/mobile/`: `auth/*.json` (BE-1, một tệp cho mỗi phản hồi thành công và mỗi mã lỗi của API mobile), `api/*.json` (BE-1, phản hồi thành công và lỗi của các endpoint dùng chung mà app gọi: `me`, `me/profile`, đăng ký, gửi lại mã, quên mật khẩu, đặt lại mật khẩu, đổi mật khẩu), `appearance/tokens-thi360-green.json` (WEB-1). Một test ở bên sở hữu khẳng định tệp khớp phản hồi thật; hai app dùng các tệp này làm dữ liệu cho máy chủ giả trong test. Sửa hợp đồng là sửa tệp và test của bên sở hữu trước.

**B1.6 Client API của app**: sinh lúc build từ `contracts/mobile/openapi-mobile.json`; không commit mã sinh ra. Tệp này là bản lọc của `packages/api-client/openapi.json` do BE-1 sinh và giữ khớp: chỉ gồm các thao tác app gọi và không gồm thao tác có tham số cookie. Android dùng plugin Gradle của OpenAPI Generator (`kotlin`, `jvm-retrofit2`, `kotlinx_serialization`); iOS dùng `swift-openapi-generator`. Đã thử 2026-11-25 (`spikes/b16-openapi-clients/RESULT.md` ở thư mục phiên): Swift build đạt; Kotlin build đạt khi tài liệu không có thao tác cookie.

**B1.7 Khung giao diện của app** (AND-1, IOS-1): thành phần gốc của hệ điều hành; màu đọc từ một đối tượng `BrandPalette` (mặc định là bộ `thi360-green` ghi sẵn trong app); `AppPreferences { locale: vi|en, themeMode: light|dark|null }` lưu trên máy. Các task sau chỉ dùng hai đối tượng này, không tự đọc màu hay ngôn ngữ.

## B2. Shared files and conflict zones

| File or area | Owner task | Others may |
| --- | --- | --- |
| `.github/workflows/web.yml`, `pnpm-workspace.yaml`, `turbo.json`, `.gitignore` | AND-1 | đọc; IOS-1 nêu nhu cầu cho AND-1 qua Orchestrator |
| `.github/workflows/android.yml` | AND-1 | AND-2..AND-4 thêm bước test của mình |
| `.github/workflows/ios.yml` | IOS-1 | IOS-2..IOS-4 thêm bước test của mình |
| `apps/android/app/src/main/**/shell/AppNavHost.kt` | AND-1 | mỗi task Android thêm đúng một dòng gọi đồ thị của mình, rebase trước khi mở PR |
| `apps/ios/Thi360/Shell/AppRouter.swift` | IOS-1 | như trên cho iOS |
| Chuỗi hiển thị Android | mỗi task một cặp tệp `res/values/strings_<task>.xml` và `res/values-en/strings_<task>.xml` | không sửa tệp của task khác |
| Chuỗi hiển thị iOS | mỗi task một String Catalog riêng (`Shell.xcstrings`, `Auth.xcstrings`, `Recovery.xcstrings`, `Account.xcstrings`) | không sửa tệp của task khác |
| `apps/ios/project.yml` | IOS-1 (khung dự án, target, scheme) | IOS-2 thêm khối package và plugin sinh client; IOS-3 thêm khóa entitlements của target; IOS-4 thêm khóa `NSFaceIDUsageDescription` vào phần Info của target. Các lane iOS chạy lần lượt nên chỉ cần rebase trước khi mở PR. Thêm tệp nguồn thì không cần sửa vì nguồn được gom theo thư mục |
| `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts` | BE-1 | đọc |
| `contracts/mobile/auth/**`, `contracts/mobile/api/**`, `contracts/mobile/openapi-mobile.json`, `.github/workflows/backend.yml` | BE-1 | đọc |
| `contracts/mobile/appearance/**`, `packages/ui/src/theme/mobile-tokens.ts` | WEB-1 | đọc |
| `backend/src/main/resources/messages*.properties` | BE-1 | đọc |
| `infra/keycloak/thi-360-realm.json` | BE-1 | đọc |
| `.env.example` | BE-1, WEB-1 và AND-1, mỗi task chỉ thêm khối của mình ở cuối tệp. AND-1 chỉ thêm một dòng: vân tay SHA-256 của kho khóa debug cho `THI360_ANDROID_CERT_SHA256` | task merge sau rebase; không sửa dòng của task khác |
| `ARCHITECTURE.md`, `DESIGN.md`, `README.md` | DOC-1 | đọc; lane nêu đề xuất sửa trong báo cáo |
| `PROGRESS.md`, `TASK.md` | Orchestrator, QA | đọc |

## B3. Migrations, generated code and run-wide rules

- Không có migration Flyway. Task nào thấy cần thì dừng và báo Orchestrator.
- Mã sinh từ OpenAPI cho web vẫn commit như hiện nay; mã sinh cho app thì không commit (B1.6).
- Kho khóa debug dùng chung của Android nằm ở `apps/android/keystores/debug.keystore` (không phải khóa phát hành); vân tay SHA-256 ghi ở `apps/android/README.md` và `.env.example`.
- Thêm thư viện phải nêu lý do trong mô tả PR (`RULES.md` mục 7).
- Câu chữ lấy theo các khóa của web nêu ở đặc tả mục 2; chữ mới của app phải có ở cả `vi` và `en`.

# Part C: Pipeline

## C1. Dependency table

Total weight: 62

| Task | Hard deps | Soft deps | Wave | Weight | Risk |
| --- | --- | --- | --- | --- | --- |
| BE-1 | - | - | 1 | 8 | high |
| WEB-1 | - | - | 1 | 3 | standard |
| DOC-1 | - | - | 1 | 1 | standard |
| AND-1 | - | - | 1 | 5 | standard |
| IOS-1 | G-MAC | - | 1 | 5 | standard |
| AND-2 | AND-1, BE-1 | - | 2 | 6 | high |
| IOS-2 | IOS-1, BE-1 | - | 2 | 6 | high |
| AND-3 | AND-2 | - | 3 | 5 | high |
| IOS-3 | IOS-2 | - | 3 | 5 | high |
| AND-4 | AND-2, AND-3 | WEB-1 | 4 | 7 | high |
| IOS-4 | IOS-2, IOS-3 | WEB-1 | 4 | 7 | high |
| QA | BE-1, WEB-1, DOC-1, AND-1, IOS-1, AND-2, IOS-2, AND-3, AND-4, IOS-3, IOS-4 | - | 5 | 4 | standard |

AND-2 và IOS-2 chờ BE-1 merge vì client của app sinh từ tài liệu OpenAPI đã có endpoint mobile. AND-4 và IOS-4 chờ AND-3, IOS-3 vì chúng gắn màn Đổi mật khẩu vào Tài khoản và cho liên kết đặt lại đi qua màn khóa.

## C2. Diagram

```text
Sóng 1 ─ BE-1 ─ WEB-1 ─ DOC-1 ─ AND-1 ─ IOS-1
Sóng 2 ─ AND-2 ◄─ AND-1, BE-1               IOS-2 ◄─ IOS-1, BE-1
Sóng 3 ─ AND-3 ◄─ AND-2                      IOS-3 ◄─ IOS-2
Sóng 4 ─ AND-4 ◄─ AND-2, AND-3 (mềm: WEB-1)  IOS-4 ◄─ IOS-2, IOS-3 (mềm: WEB-1)
Sóng 5 ─ QA ◄─ tất cả
```

## C3. Critical path and priorities

- Đường dài nhất theo trọng số: BE-1 → AND-2 → AND-3 → AND-4 → QA và BE-1 → IOS-2 → IOS-3 → IOS-4 → QA (30 điểm mỗi đường).
- Mở trước: BE-1 (mọi task từ sóng 2 chờ nó), AND-1, IOS-1.
- Số lane cùng lúc: máy chính tối đa 3 (BE-1, WEB-1 hoặc DOC-1, một lane Android); `devin-mac` tối đa 1.
- Khi `devin-mac` không dùng được: các lane Android và backend chạy tiếp; lane iOS đặt `blocked` kèm lý do.

## C4. Spec coverage

| Spec ID | Task(s) |
| --- | --- |
| R-1 / ĐT 1, 3, 4 | AND-1, IOS-1 |
| R-1 / ĐT 2 | AND-1, IOS-1 (URL của bản demo), QA (gọi tới máy chủ demo) |
| R-1 / ĐT 5 | AND-1, IOS-1 |
| R-2 / ĐT 6, 10, 12, 13 | AND-1, IOS-1 |
| R-2 / ĐT 7 | AND-1 (khung), AND-4 (hai cột của Tài khoản) |
| R-2 / ĐT 8, 9 | IOS-1 (khung), IOS-4 (hai cột của Tài khoản) |
| R-2 / ĐT 11 | AND-1, IOS-1 (giữ trạng thái), AND-3, IOS-3 (form đổi mật khẩu) |
| R-3 / ĐT 14–23, 87, 88 | BE-1 |
| R-4 / ĐT 24–28 | AND-2, IOS-2 |
| R-5 / ĐT 29, 30 | AND-2, IOS-2 (phía máy chủ: BE-1) |
| R-6 / ĐT 35, 94 | BE-1 |
| R-6 / ĐT 31–34, 36, 90 | AND-2, IOS-2 |
| R-7 / ĐT 37–43, 91, 92 | AND-3, IOS-3 |
| R-8 / ĐT 44–49, 51 | AND-3, IOS-3 |
| R-8 / ĐT 50 | WEB-1 |
| R-9 / ĐT 52–55, 89 | AND-3, IOS-3 |
| R-10 / ĐT 56–66, 93 | AND-4, IOS-4 |
| R-11 / ĐT 67–70 | AND-4, IOS-4 |
| R-12 / ĐT 71, 76 | WEB-1 |
| R-12 / ĐT 77 | WEB-1 (route trả 503), AND-4, IOS-4 (giữ bảng đang dùng) |
| R-12 / ĐT 72–75, 78 | AND-4, IOS-4 |
| R-13 / ĐT 79–81 | AND-1, IOS-1 |
| R-13 / ĐT 82 | BE-1 (ngôn ngữ thư), AND-3, IOS-3 (gửi header) |
| R-13 / ĐT 83 | AND-2, IOS-2 |
| R-14 / ĐT 84–86 | AND-2, IOS-2 |
| Mục 9 (phi chức năng) | task tương ứng; QA kiểm lại hiệu năng khởi động, truy cập và ma trận kích thước |
| Mục 7 (tài liệu) | DOC-1 |

# Part D: Task contracts

## BE-1 · Backend: API xác thực mobile và phiên mobile

**Spec:** R-3 (ĐT 14–23, 87, 88), R-6 (ĐT 35, 94), R-5 phía máy chủ (ĐT 17, 19), R-13 (ĐT 82); D-4, D-6, D-15, D-24, D-28, D-35.

**Lane owner:** lead

**Domain shape:**

- `LoginChannel { WEB, MOBILE }` trong bản ghi phiên Redis (B1.2); `MobileTokenResponse` (B1.1).
- `KeycloakIdentityClient` nhận kênh để chọn client: `thi360-api` cho WEB, `thi360-mobile` (offline token) cho MOBILE.
- Nguồn ngôn ngữ: cookie `thi360_locale`, nếu không có thì header `Accept-Language`, nếu không có thì giữ giá trị đang lưu.
- Tài khoản realm `master` gọi `mobile/login` được xác thực bằng client nền tảng như web (`AuthService.java:355-440`), không cấp offline token; xác thực đúng thì đóng phiên Keycloak vừa sinh rồi trả `APP_STUDENT_ONLY`, xác thực sai thì trả lỗi như web.

**Deliverables:**

- `POST /api/v1/auth/mobile/login`, `/refresh`, `/logout`, `/register/verify` theo hợp đồng mục R-3 của đặc tả và B1.1.
- Từ chối `APP_STUDENT_ONLY` khi đăng nhập và khi làm mới cho tài khoản `MANAGE` và tài khoản realm `master`; không để lại phiên ở Redis và Keycloak.
- Client `thi360-mobile` trong `infra/keycloak/thi-360-realm.json` (confidential, direct grant, scope mặc định có `offline_access`), `offlineSessionIdleTimeout: 604800`, `offlineSessionMaxLifespanEnabled: true`, `offlineSessionMaxLifespan: 2592000` (D-35); biến `THI360_KC_MOBILE_CLIENT_ID`, `THI360_KC_MOBILE_CLIENT_SECRET` trong cấu hình và `.env.example`.
- Mốc 30 ngày được ép ở hai lớp độc lập: Keycloak (thời hạn tối đa của phiên offline) và hạn 30 ngày sẵn có của bản ghi phiên Redis tính từ lúc tạo. Bản ghi hết hạn thì `mobile/refresh` trả 401 dù Keycloak còn nhận refresh token. Không thêm cơ chế gia hạn; nếu `sid` xoay thì bản ghi mới giữ hạn còn lại của bản ghi cũ, không tính lại 30 ngày.
- Thu hồi: ngừng hoạt động tài khoản, đặt lại mật khẩu và đăng xuất xóa cả phiên Redis lẫn phiên offline ở Keycloak.
- `Accept-Language` cho `mobile/login`, `/auth/register`, `/auth/register/resend`; sự kiện `LOGIN` có thông tin kênh.
- Giới hạn tần suất: `mobile/login` chung chính sách và bộ đếm với `/auth/login`; `mobile/register/verify` thuộc nhóm đăng ký.
- `APP_STUDENT_ONLY` trong ba tệp `messages*.properties`; OpenAPI và client web sinh lại; tệp hợp đồng `contracts/mobile/auth/*.json` và `contracts/mobile/api/*.json` kèm test khẳng định khớp.
- `contracts/mobile/openapi-mobile.json` (B1.6): một test backend xuất bản lọc ra `backend/target/openapi-mobile.json`; workflow backend thêm bước `diff` với bản đã commit, như bước đang có cho `openapi.json`.

**Owned paths:** `backend/src/main/java/vn/unicom/thi360/identity/**`, `backend/src/main/java/vn/unicom/thi360/shared/security/**`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/messages*.properties`, `backend/src/test/java/vn/unicom/thi360/identity/**`, `backend/src/test/java/vn/unicom/thi360/shared/security/**`, `backend/src/test/java/vn/unicom/thi360/MobileOpenApiExportIT.java`, `infra/keycloak/thi-360-realm.json`, `.env.example`, `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`, `contracts/mobile/auth/**`, `contracts/mobile/api/**`, `contracts/mobile/openapi-mobile.json`, `.github/workflows/backend.yml`.

**Out of scope:** endpoint cookie của web (không đổi hành vi); endpoint kiểm mật khẩu riêng cho màn khóa (đã bỏ, D-28); đăng nhập Google; cập nhật realm trên Keycloak của bản demo.

**Acceptance:**

- AC-1: học viên đang hoạt động gọi `mobile/login` nhận 200 đủ năm trường của B1.1, không có header `Set-Cookie`; `GET /api/v1/me` bằng Bearer trả đúng tài khoản — evidence: `MobileAuthIT#loginReturnsTokensInBody`.
- AC-2: sai mật khẩu nhận 401 `INVALID_CREDENTIALS`; sai quá ngưỡng nhận 429 `LOGIN_THROTTLED`; bộ đếm sai dùng chung với `/auth/login` theo cả hai chiều; quá giới hạn IP nhận 429 `RATE_LIMITED` — evidence: `MobileAuthIT#wrongPasswordSharesThrottleWithWeb`, `MobileAuthRateLimitIT#loginSharesIpPolicy`.
- AC-3: tài khoản `INACTIVE` nhận 403 `ACCOUNT_INACTIVE`; tài khoản xóa mềm nhận 401 `INVALID_CREDENTIALS`; cả hai không tạo phiên — evidence: `MobileAuthIT#inactiveAndSoftDeleted`.
- AC-4: tài khoản `MANAGE` và tài khoản realm `master` đăng nhập đúng mật khẩu nhận 403 `APP_STUDENT_ONLY`, không có phiên ở Redis, không còn phiên của lần gọi đó ở Keycloak — evidence: `MobileStudentOnlyIT#manageAndPlatformAdminRejected`.
- AC-5: `mobile/refresh` với refresh token còn hạn nhận 200 và access token mới dùng được; hai lời gọi đồng thời cùng token đều 200; token sai, hết hạn hoặc thuộc phiên đã thu hồi nhận 401 — evidence: `MobileAuthIT#refresh`, `MobileAuthIT#concurrentRefreshBothSucceed`.
- AC-6: tài khoản được gán vai trò quản trị sau khi đăng nhập thì `mobile/refresh` nhận 403 `APP_STUDENT_ONLY` và phiên bị thu hồi — evidence: `MobileStudentOnlyIT#refreshAfterRoleGrant`.
- AC-7: `mobile/logout` trả 204, access token của phiên nhận 401 ở `GET /api/v1/me`, gọi lại vẫn 204 — evidence: `MobileAuthIT#logoutIsIdempotent`.
- AC-8: `mobile/register/verify` với mã đúng trả 201 kèm token và tạo tài khoản đã xác minh email; mã sai và mã hết hạn trả lỗi như endpoint web; quá giới hạn nhóm đăng ký nhận 429 và cộng chung bộ đếm với `/auth/register/verify` — evidence: `MobileRegisterIT#verifyReturnsTokens`, `MobileRegisterIT#invalidAndExpiredCode`, `MobileAuthRateLimitIT#registerVerifySharesRegistrationPolicy`.
- AC-9: `Accept-Language: en` ở `mobile/login` ghi `preferred_locale = en`; không header và không cookie thì giá trị giữ nguyên; `/auth/register` với `Accept-Language: en` gửi thư OTP tiếng Anh; có cookie thì cookie thắng — evidence: `MobileLocaleIT`.
- AC-10: sự kiện `LOGIN` của đăng nhập mobile có kênh MOBILE; nhật ký, log và `toString` của request mới không chứa mật khẩu hay token — evidence: `MobileAuthIT#auditHasChannel`, `CredentialRequestToStringTests`.
- AC-11: các test đăng nhập web hiện có xanh; hợp đồng endpoint hiện có trong OpenAPI không đổi, endpoint mới có mặt — evidence: `AuthFlowIT`, `AuthRedirectAndLogoutCookieIT`, `LoginThrottleIT`, `ExpiredTokenLogoutIT`, `OpenApiExportIT` và bước diff.
- AC-12: phiên mobile hết khi không dùng quá thời hạn offline; hết ở mốc tối đa kể từ lúc đăng nhập dù được làm mới đều; trước mốc đó vẫn làm mới được sau khi một phiên web tạo cùng lúc đã hết vì không dùng; `refreshExpiresIn` bằng thời hạn không dùng của phiên offline đọc từ realm — evidence: `MobileSessionLifetimeIT#mobileIdleExpires`, `#mobileEndsAtMaxLifetime`, `#mobileOutlivesIdleWeb`, `#refreshExpiresInMatchesRealm` trên Keycloak của Testcontainers với thời hạn rút ngắn như `spikes/q4-keycloak-session`.
- AC-13: ngừng hoạt động tài khoản, đặt lại mật khẩu thành công và đăng xuất đều làm `mobile/refresh` của phiên mobile nhận 401 và xóa phiên offline ở Keycloak — evidence: `MobileSessionRevocationIT`.
- AC-14: mỗi tệp trong `contracts/mobile/auth/` và `contracts/mobile/api/` khớp phản hồi thật của endpoint tương ứng, gồm cả các phản hồi lỗi của đăng ký, OTP, đặt lại và đổi mật khẩu — evidence: `MobileContractFixturesIT`.
- AC-15: phiên web không dùng quá thời hạn của realm thì làm mới bị từ chối, như trước thay đổi — evidence: `MobileSessionLifetimeIT#webIdleStillExpires`.
- AC-16: `contracts/mobile/openapi-mobile.json` khớp bản lọc sinh từ OpenAPI hiện tại, có đủ các thao tác app gọi và không có thao tác nào có tham số cookie — evidence: `MobileOpenApiExportIT` và bước `diff` trong workflow backend.
- AC-17: bản ghi phiên Redis của một phiên mobile hết hạn thì `mobile/refresh` nhận 401, kể cả khi Keycloak còn nhận refresh token đó — evidence: `MobileSessionLifetimeIT#redisRecordExpiryEndsSession` (hạn bản ghi rút ngắn bằng cấu hình của test; ở ca này realm test tắt mốc tối đa của phiên offline).

**Impact outside the task:**

- Đăng nhập, làm mới, đăng xuất bằng cookie của hai web (`AuthService`, `AuthController`, `KeycloakIdentityClient`) → `AuthFlowIT`, `AuthRedirectAndLogoutCookieIT`, `MultiIssuerIT`, `ExpiredTokenLogoutIT` xanh [owner: this task]
- Chặn đăng nhập sai và giới hạn tần suất (`LoginThrottle`, `RateLimitFilter`) → `LoginThrottleIT`, `PasswordResetRateLimitIT` xanh [owner: this task]
- Đăng ký web và thư OTP (`SelfRegistrationService`) → `EmailVerificationIT` và các IT đăng ký hiện có xanh [owner: this task]
- Bản ghi phiên Redis và giới hạn 5 phiên (`SessionStore`, `SessionFilter`) → `AuthzCallCountIT`, `AuthzEvictionIT` xanh; thêm ca phiên mobile tính chung trong 5 phiên ở `MobileAuthIT#sessionCapCountsMobile` [owner: this task]
- Thu hồi phiên khi ngừng hoạt động và đặt lại mật khẩu (`UserAdminService`, `PasswordResetService`) → `PasswordResetIT`, `NoHardDeleteIT` xanh [owner: this task]
- OpenAPI và client web (`packages/api-client`, bước `api-client-drift` của `web.yml`) → lệnh "OpenAPI và client" ở A5 [owner: this task]
- Realm dev và test (`infra/keycloak/thi-360-realm.json`, Keycloak của Testcontainers nhập cùng tệp) → toàn bộ `./mvnw -B -ntp verify` xanh [owner: this task]
- Realm trên Keycloak của bản demo chưa có client mobile → ghi vào danh sách việc sau merge của QA; không sửa trong đợt chạy [owner: QA]
- Hai app dùng tệp hợp đồng và tài liệu OpenAPI đã lọc (`contracts/mobile/**`) → test máy chủ giả và bước sinh client của AND-2, IOS-2 [owner: AND-2, IOS-2]
- Workflow backend (`.github/workflows/backend.yml:27-28`): thêm bước `diff` cho bản lọc, các bước đang có không đổi → workflow xanh trên PR của task [owner: this task]

**Test plan:**

- AC-1: `MobileAuthIT#loginReturnsTokensInBody`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=MobileAuthIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `MobileAuthIT#wrongPasswordSharesThrottleWithWeb`, `MobileAuthRateLimitIT#loginSharesIpPolicy`, tích hợp, lệnh trên với `-Dit.test='MobileAuthIT,MobileAuthRateLimitIT'`
- AC-3: `MobileAuthIT#inactiveAndSoftDeleted`, tích hợp, lệnh của AC-1
- AC-4: `MobileStudentOnlyIT#manageAndPlatformAdminRejected`, tích hợp, `-Dit.test=MobileStudentOnlyIT`
- AC-5: `MobileAuthIT#refresh`, `#concurrentRefreshBothSucceed`, tích hợp, lệnh của AC-1
- AC-6: `MobileStudentOnlyIT#refreshAfterRoleGrant`, tích hợp, `-Dit.test=MobileStudentOnlyIT`
- AC-7: `MobileAuthIT#logoutIsIdempotent`, tích hợp, lệnh của AC-1
- AC-8: `MobileRegisterIT`, `MobileAuthRateLimitIT#registerVerifySharesRegistrationPolicy`, tích hợp, `-Dit.test='MobileRegisterIT,MobileAuthRateLimitIT'`
- AC-9: `MobileLocaleIT`, tích hợp (thư đọc qua GreenMail), `-Dit.test=MobileLocaleIT`
- AC-10: `MobileAuthIT#auditHasChannel`, tích hợp; `CredentialRequestToStringTests`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=CredentialRequestToStringTests`
- AC-11: `AuthFlowIT`, `AuthRedirectAndLogoutCookieIT`, `LoginThrottleIT`, `ExpiredTokenLogoutIT`, tích hợp; lệnh "OpenAPI và client" ở A5
- AC-12: `MobileSessionLifetimeIT#mobileIdleExpires`, `#mobileEndsAtMaxLifetime`, `#mobileOutlivesIdleWeb`, `#refreshExpiresInMatchesRealm`, tích hợp, `-Dit.test=MobileSessionLifetimeIT`
- AC-13: `MobileSessionRevocationIT`, tích hợp, `-Dit.test=MobileSessionRevocationIT`
- AC-14: `MobileContractFixturesIT`, tích hợp, `-Dit.test=MobileContractFixturesIT`
- AC-15: `MobileSessionLifetimeIT#webIdleStillExpires`, tích hợp, `-Dit.test=MobileSessionLifetimeIT`
- AC-16: `MobileOpenApiExportIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=MobileOpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false && cd .. && diff -u contracts/mobile/openapi-mobile.json backend/target/openapi-mobile.json`
- AC-17: `MobileSessionLifetimeIT#redisRecordExpiryEndsSession`, tích hợp, `-Dit.test=MobileSessionLifetimeIT`
- Regression: mọi dòng ở mục Ảnh hưởng chạy bằng `cd backend && ./mvnw -B -ntp verify`; client web bằng lệnh "OpenAPI và client".
- Risk tests: red-first cho AC-1, AC-4, AC-6, AC-12, AC-13, AC-17 (đỏ trên `base_sha` vì endpoint chưa có). AC-15 phải xanh cả trước và sau thay đổi: đó là phép kiểm hồi quy cho phiên web. Ma trận vai trò ở AC-4: học viên, `MANAGE`, `PLATFORM_ADMIN`. Gửi lặp: AC-7 (đăng xuất hai lần), AC-5 (làm mới đồng thời). Không có dữ liệu cần chuyển đổi.
- Test data: tài khoản tạo trong từng test qua lớp hỗ trợ hiện có của `identity`; Keycloak, Redis, PostgreSQL của Testcontainers nên lane không đụng nhau.
- Not tested: phiên sống thật 7 ngày và 30 ngày (chỉ kiểm với thời hạn rút ngắn); realm của bản demo.

**Design / references:** đặc tả mục R-3, R-6; `D:/.Vietants_Project/.thi360-dev/mobile-shell/spikes/q4-keycloak-session/RESULT.md`.

## WEB-1 · Web học viên: bảng màu cho app và tệp xác minh deep link

**Spec:** R-12 (ĐT 71, 76, 77), R-8 (ĐT 50); D-13, D-18, D-21, D-24.

**Lane owner:** advisor

**Domain shape:**

- `Palette` và phản hồi của B1.4; hàm thuần `toMobilePalette(tokens)` ánh xạ kết quả `deriveTokens` và token trung tính sang `Palette`.
- Cấu hình deep link từ biến môi trường: `THI360_ANDROID_PACKAGE`, `THI360_ANDROID_CERT_SHA256` (danh sách), `THI360_IOS_APP_ID` (`<TeamID>.<bundle id>`).

**Deliverables:**

- Route `GET /mobile/appearance-tokens.json` của web học viên theo B1.4: công khai, `ETag` theo `version`, `Cache-Control: no-cache`, 304 khi `If-None-Match` khớp, 503 khi backend lỗi hoặc `presetId` không có trong danh sách bộ màu của web.
- `packages/ui/src/theme/mobile-tokens.ts` và test; tệp hợp đồng `contracts/mobile/appearance/tokens-thi360-green.json`.
- Route `GET /.well-known/assetlinks.json` và `GET /.well-known/apple-app-site-association`, `Content-Type: application/json`, chỉ khai đường dẫn `/reset-password`; thiếu biến của nền tảng nào thì bỏ phần của nền tảng đó.
- Ba đường trên đi qua lớp điều hướng của web mà không đòi đăng nhập; biến mới trong `.env.example`.

**Owned paths:** `apps/web-student/src/app/mobile/**`, `apps/web-student/src/app/.well-known/**`, `apps/web-student/src/features/mobile/**`, `apps/web-student/src/proxy.ts`, `packages/ui/src/theme/mobile-tokens.ts`, `packages/ui/src/theme/mobile-tokens.test.ts`, `packages/ui/src/theme/index.ts`, `contracts/mobile/appearance/**`, `.env.example` (phần deep link).

**Out of scope:** đổi `deriveTokens` hay token của web; cấu hình nginx và triển khai bản demo; trang `/reset-password` của web.

**Acceptance:**

- AC-1: với mỗi bộ trong 9 bộ cố định và một cấu hình `custom`, ở cả `light` và `dark`, các giá trị thương hiệu của `Palette` trùng mã màu với `deriveTokens` cho cùng cấu hình (`primary-fill`, `on-primary`, `primary-text`, `primary-50`, `primary-100`, `accent-500`, `on-accent`) — evidence: `mobile-tokens.test.ts`.
- AC-2: các giá trị trung tính của `Palette` trùng token trung tính của web ở từng chế độ và không đổi theo bộ màu — evidence: `mobile-tokens.test.ts`.
- AC-3: mọi cặp chữ–nền mà `packages/ui/src/styles/tokens.test.ts` kiểm cho web, khi ghép từ `Palette`, đạt 4,5:1 cho chữ và 3:1 cho thành phần không phải chữ, với cả 10 cấu hình và hai chế độ — evidence: `mobile-tokens.test.ts`.
- AC-4: route trả 200 đúng hình B1.4, có `ETag` và `Cache-Control: no-cache`; `If-None-Match` khớp trả 304; sau khi cấu hình đổi, lời gọi kế tiếp trả `version` mới dù có tham số truy vấn nào; backend lỗi thì 503; `presetId` không có trong danh sách bộ màu của web thì 503, không rơi về bộ mặc định — evidence: `appearance-tokens.route.test.ts`.
- AC-5: hai route `/.well-known/…` trả 200, `Content-Type: application/json`, đúng định danh từ biến môi trường, chỉ có đường dẫn `/reset-password`; không đăng nhập vẫn truy cập được cả ba route — evidence: `well-known.route.test.ts`, `proxy.test.ts`.
- AC-6: tệp `contracts/mobile/appearance/tokens-thi360-green.json` khớp kết quả route cho cấu hình mặc định — evidence: `mobile-tokens.test.ts`.

**Impact outside the task:**

- Lớp điều hướng của web học viên (`apps/web-student/src/proxy.ts`): các trang cần đăng nhập vẫn bị chuyển về đăng nhập → test hiện có của `proxy` và `pnpm --filter @thi360/web-student test` xanh [owner: this task]
- Gói `@thi360/ui` (thêm xuất khẩu ở `theme/index.ts`) → `pnpm --filter @thi360/ui test`, `pnpm typecheck` xanh [owner: this task]
- Bố cục gốc của web đang gọi `GET /api/v1/appearance` (`apps/web-student/src/app/layout.tsx:50`) → `layout.test.tsx` xanh, không đổi tệp đó [owner: this task]
- Hai app đọc `Palette` và tệp hợp đồng → test của AND-4, IOS-4 [owner: AND-4, IOS-4]
- nginx của bản demo phải chuyển `/.well-known/*` và `/mobile/*` tới web học viên → ghi vào danh sách việc sau merge của QA [owner: QA]

**Test plan:**

- AC-1: `mobile-tokens.test.ts` (nhóm "thương hiệu"), unit, `pnpm --filter @thi360/ui test src/theme/mobile-tokens.test.ts`
- AC-2: `mobile-tokens.test.ts` (nhóm "trung tính"), unit, lệnh trên
- AC-3: `mobile-tokens.test.ts` (nhóm "tương phản"), unit, lệnh trên
- AC-4: `appearance-tokens.route.test.ts`, unit với backend giả, `pnpm --filter @thi360/web-student test src/app/mobile`
- AC-5: `well-known.route.test.ts`, `proxy.test.ts`, unit, `pnpm --filter @thi360/web-student test src/app/.well-known src/proxy.test.ts`
- AC-6: `mobile-tokens.test.ts` (nhóm "hợp đồng"), unit, lệnh của AC-1
- Regression: `pnpm lint && pnpm typecheck && pnpm test && pnpm build`.
- Risk tests: không áp dụng (rủi ro `standard`).
- Test data: cấu hình giao diện dựng trong test; không cần backend thật.
- Not tested: hệ điều hành xác minh tệp trên tên miền thật (cần triển khai và Team ID, ngoài đợt chạy).

**Design / references:** `packages/ui/src/theme/scale.ts:476`, `packages/ui/src/theme/vars.ts`, `DESIGN.md` mục 2.3, 8, 9.

## DOC-1 · Tài liệu kiến trúc và quy ước giao diện app

**Spec:** đặc tả mục 7 (hàng Tài liệu); D-2, D-9, D-11, D-21.

**Lane owner:** advisor

**Domain shape:**

- Không có kiểu dữ liệu mới; tài liệu mô tả đúng các hợp đồng B1.1–B1.7 và cấu trúc thư mục `apps/android`, `apps/ios`, `contracts/mobile`.

**Deliverables:**

- `ARCHITECTURE.md` mục 1, 2, 3: mobile là hai app native; luồng đăng nhập mobile; route bảng màu; thư mục mới.
- `DESIGN.md`: mục mới "App học viên" (thành phần gốc từng hệ điều hành, vai màu lấy từ `Palette`, kích thước và bố cục theo bề rộng, bản thiết kế ở `docs/design/app-hoc-vien/`).
- `README.md`: đoạn trỏ tới `apps/android/README.md` và `apps/ios/README.md`.

**Owned paths:** `ARCHITECTURE.md`, `DESIGN.md`, `README.md`.

**Out of scope:** `PRD.md`, `RULES.md`; `TASK.md` và `PROGRESS.md` (QA); README của từng app (AND-1, IOS-1).

**Acceptance:**

- AC-1: ba tệp không còn câu nào nói mobile dùng React Native; mọi đường dẫn và tên endpoint nêu trong phần mới tồn tại trong kế hoạch này hoặc trong repo — evidence: `grep -n "React Native\|Expo" ARCHITECTURE.md DESIGN.md README.md` rỗng; người rà soát mở từng đường dẫn nêu trong phần mới và ghi kết quả.
- AC-2: mục "App học viên" của `DESIGN.md` nêu đủ: nguồn màu, quy tắc bề rộng (600dp, 840dp, compact/regular), quy tắc xoay, ngôn ngữ mặc định, vùng bấm tối thiểu — evidence: người rà soát đối chiếu với đặc tả R-2, R-12, R-13, mục 9.

**Impact outside the task:**

- Workflow `uniai-link` kiểm PR có mã issue (`.github/workflows/uniai-link.yml`) → tiêu đề PR của task có `UNI-nnn`, workflow xanh [owner: this task]
- Lane khác đọc `DESIGN.md` làm chuẩn giao diện → không đổi các mục 1–10 hiện có; kiểm bằng `git diff --stat` chỉ có phần thêm ở mục mới [owner: this task]

**Test plan:**

- AC-1: lệnh `grep` ở trên; danh sách đường dẫn đã mở trong báo cáo rà soát
- AC-2: rà soát theo `review-method.md` với đặc tả làm nguồn
- Regression: `git diff --stat origin/develop -- ARCHITECTURE.md DESIGN.md README.md` chỉ có phần thêm và các dòng về mobile.
- Test data: không có.
- Not tested: nothing left out.

**Design / references:** `docs/design/app-hoc-vien/README.md`, đặc tả mục 7.

## AND-1 · Android: dự án, môi trường, CI và khung điều hướng đáp ứng

**Spec:** R-1 (ĐT 1–5), R-2 (ĐT 6, 7 phần khung, 10–13), R-13 (ĐT 79–81); D-2, D-7, D-8, D-10, D-11, D-22, D-25, D-26.

**Lane owner:** lead

**Domain shape:**

- `AppPreferences(locale: Locale?, themeMode: ThemeMode?)` lưu bằng DataStore; `BrandPalette` (17 màu của B1.4) với giá trị mặc định ghi sẵn.
- `Destination { Home, MyExams, History, Account }`; `NavLayout { BottomBar, Rail }` tính từ bề rộng cửa sổ (dưới 600dp, từ 600dp).

**Deliverables:**

- Dự án Gradle ở `apps/android`: module `:app`, `applicationId` `vn.unicom.thi360`, `minSdk` 26, hai flavor `dev` và `demo` (URL backend và web theo flavor), ký debug bằng kho khóa dùng chung (B3).
- Cấu hình mạng: bản `demo` chặn `http://`; bản `dev` chỉ cho `http://` tới máy lập trình.
- Khung bốn mục với thanh dưới hoặc thanh bên theo bề rộng; ba màn giữ chỗ; khoá chiều dọc trên điện thoại; giữ trạng thái khi xoay, gập, đổi kích thước cửa sổ.
- Cơ chế ngôn ngữ (mặc định tiếng Việt, đổi tức thì, lưu trên máy) và cơ chế sáng/tối đọc `AppPreferences`; chủ đề Material 3 đọc `BrandPalette`.
- Phép kiểm cùng bộ khóa giữa `values` và `values-en`.
- `apps/android/README.md`; workflow `android.yml` (build hai flavor, test JVM, ảnh chụp màn, lint; test trên máy ảo ở job riêng); sửa `web.yml` để `apps/android/**` và `apps/ios/**` không kích hoạt workflow web.

**Owned paths:** `apps/android/**`, `.github/workflows/android.yml`, `.github/workflows/web.yml`, `pnpm-workspace.yaml`, `turbo.json`, `.gitignore`, một dòng vân tay kho khóa debug trong `.env.example`.

**Out of scope:** màn đăng nhập và mọi lời gọi API (AND-2); nội dung mục Tài khoản (AND-4); lấy bảng màu từ máy chủ (AND-4).

**Acceptance:**

- AC-1: từ checkout sạch, lệnh build ở A5 tạo APK cho cả hai flavor, không cảnh báo bị coi là lỗi; app tên "Thi 360", định danh `vn.unicom.thi360`, `minSdk` 26 — evidence: nhật ký build; `ManifestTest`.
- AC-2: bản `demo` từ chối kết nối `http://` và có URL backend lẫn URL web là `https://thi360.zone17.click`; bản `dev` cho phép `http://` tới máy lập trình — evidence: `NetworkSecurityConfigTest` và `FlavorConfigTest` cho từng flavor.
- AC-3: bề rộng dưới 600dp hiện thanh dưới bốn mục và một cột; từ 600dp hiện thanh bên — evidence: `ShellLayoutTest`; ảnh chụp ở 360×640, 412×915, 840×720, 1280×800, sáng và tối.
- AC-4: ba mục Trang chủ, Bài thi của tôi, Lịch sử hiện tiêu đề và "Chưa có nội dung", không gọi mạng — evidence: `PlaceholderScreensTest`.
- AC-5: xoay máy, gập/mở và đổi kích thước cửa sổ giữ nguyên mục đang chọn và chữ đang nhập ở một ô thử — evidence: `StateRestorationTest`.
- AC-6: thiết bị có bề rộng nhỏ nhất dưới 600dp giữ chiều dọc; thiết bị lớn hơn xoay được; cửa sổ chia đôi hẹp chuyển về thanh dưới — evidence: `OrientationPolicyTest`, `ShellLayoutTest#splitScreenCompact`.
- AC-7: lần mở đầu dùng tiếng Việt dù máy ở tiếng Anh; đổi ngôn ngữ thì chữ đổi ngay và còn sau khi mở lại — evidence: `LocalePreferenceTest`.
- AC-8: hai bộ chuỗi `vi` và `en` cùng bộ khóa — evidence: `StringKeyParityTest`.
- AC-9: PR chỉ đổi tệp dưới `apps/android` hoặc `apps/ios` không chạy workflow web và có chạy `android.yml` khi đổi Android — evidence: danh sách checks của PR task này.
- AC-10: `pnpm install --frozen-lockfile`, `pnpm lint`, `pnpm typecheck` cho kết quả như trước khi có `apps/android` — evidence: nhật ký ba lệnh trên nhánh task.

**Impact outside the task:**

- Workflow web (`.github/workflows/web.yml:6,9`) vẫn chạy cho thay đổi dưới `apps/web-admin`, `apps/web-student`, `packages` → một commit thử đổi tệp ở `apps/web-student` trên nhánh task kích hoạt workflow web, rồi hoàn lại [owner: this task]
- Workspace JS (`pnpm-workspace.yaml` mẫu `apps/*`, `turbo.json`) → AC-10 [owner: this task]
- Các task Android sau dùng `AppNavHost`, `AppPreferences`, `BrandPalette` → build và test của AND-2..AND-4 [owner: AND-2, AND-3, AND-4]
- IOS-1 cần `web.yml` bỏ qua `apps/ios/**` → cùng thay đổi ở task này; IOS-1 kiểm bằng checks của PR mình [owner: this task]

**Test plan:**

- AC-1: build hai flavor, `cd apps/android && ./gradlew :app:assembleDevDebug :app:assembleDemoDebug`; `ManifestTest`, unit JVM, `./gradlew :app:testDevDebugUnitTest --tests '*ManifestTest'`
- AC-2: `NetworkSecurityConfigTest`, `FlavorConfigTest`, unit JVM, `./gradlew :app:testDevDebugUnitTest :app:testDemoDebugUnitTest --tests '*NetworkSecurityConfigTest' --tests '*FlavorConfigTest'`
- AC-3: `ShellLayoutTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*ShellLayoutTest'`; ảnh chụp, `./gradlew :app:verifyRoborazziDevDebug`
- AC-4: `PlaceholderScreensTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*PlaceholderScreensTest'`
- AC-5: `StateRestorationTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*StateRestorationTest'`
- AC-6: `OrientationPolicyTest`, `ShellLayoutTest#splitScreenCompact`, giao diện trên JVM, lệnh tương tự
- AC-7: `LocalePreferenceTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*LocalePreferenceTest'`
- AC-8: `StringKeyParityTest`, unit JVM, `./gradlew :app:testDevDebugUnitTest --tests '*StringKeyParityTest'`
- AC-9: checks của PR; ghi ảnh chụp danh sách checks vào bằng chứng
- AC-10: `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck`
- Regression: workflow web chạy cho thay đổi web (commit thử); `pnpm test` xanh.
- Risk tests: không áp dụng (rủi ro `standard`).
- Test data: không cần backend.
- Not tested: thiết bị gập thật (chỉ kiểm bằng kích thước cửa sổ và tính năng gập giả lập của thư viện test).

**Design / references:** `docs/design/app-hoc-vien/A-Home.dc.html`, `A-Tablet-Home.dc.html`, `A-Fold-Account.dc.html`, `m3.css`; đặc tả mục 9.

## IOS-1 · iOS: dự án, môi trường, CI và khung điều hướng đáp ứng

**Spec:** R-1 (ĐT 1–5), R-2 (ĐT 6, 8, 9 phần khung, 10–13), R-13 (ĐT 79–81); D-2, D-7, D-8, D-10, D-11, D-17, D-22, D-24, D-25, D-26.

**Lane owner:** lead

**Domain shape:**

- `AppPreferences` (`locale`, `themeMode`) lưu bằng `UserDefaults`; `BrandPalette` (17 màu của B1.4) với giá trị mặc định ghi sẵn.
- `Destination { home, myExams, history, account }`; kiểu điều hướng tính từ `horizontalSizeClass` và loại máy: compact → thanh tab; iPad regular → sidebar; iPhone regular (iPhone Duo mở) → hai nửa, thanh tab ở nửa trái.

**Deliverables:**

- Dự án ở `apps/ios` sinh bằng XcodeGen (`project.yml`), bundle id `vn.unicom.thi360`, iOS 17.0 trở lên, hai scheme `Thi360Dev` và `Thi360Demo` (URL theo tệp `.xcconfig`).
- App Transport Security: bản demo chỉ HTTPS; bản dev thêm ngoại lệ cho máy lập trình.
- Khung bốn mục theo ba kiểu điều hướng ở trên; ba màn giữ chỗ; khóa chiều dọc trên iPhone bề rộng compact; giữ trạng thái khi xoay, gập, đổi kích thước.
- Cơ chế ngôn ngữ (mặc định tiếng Việt, đổi tức thì) và sáng/tối đọc `AppPreferences`; màu nhấn và màu thương hiệu đọc `BrandPalette`.
- Phép kiểm cùng bộ khóa giữa hai ngôn ngữ của mọi String Catalog.
- `apps/ios/README.md` (bản Xcode, XcodeGen, quy tắc `devin-mac`); workflow `ios.yml` trên runner macOS (sinh dự án, build hai scheme, test).

**Owned paths:** `apps/ios/**`, `.github/workflows/ios.yml`.

**Out of scope:** màn đăng nhập và lời gọi API (IOS-2); nội dung Tài khoản và lấy bảng màu (IOS-4); `web.yml` (AND-1).

**Acceptance:**

- AC-1: từ checkout sạch trên máy vừa khởi động, `xcodegen generate` rồi lệnh build và test ở A5 chạy xanh cho scheme dev; scheme demo build được; tên hiển thị "Thi 360", bundle id `vn.unicom.thi360`, iOS tối thiểu 17.0 — evidence: nhật ký `xcodebuild`; `BundleInfoTests`.
- AC-2: bản demo không có ngoại lệ ATS và có URL backend lẫn URL web là `https://thi360.zone17.click`; bản dev chỉ có ngoại lệ cho máy lập trình — evidence: `TransportSecurityTests` và `EnvironmentConfigTests` đọc `Info.plist` của từng cấu hình.
- AC-3: bề rộng compact hiện thanh tab bốn mục; iPad regular hiện sidebar; iPhone regular hiện hai nửa với thanh tab ở nửa trái — evidence: `ShellLayoutTests`; ảnh chụp ở iPhone 17 (390×844), iPad Air 11 inch ngang và dọc, và simulator iPhone Duo lúc gập (466×678) và lúc mở (669×951, cả hai chiều), sáng và tối.
- AC-4: ba mục giữ chỗ hiện tiêu đề và "Chưa có nội dung", không gọi mạng — evidence: `PlaceholderScreensTests`.
- AC-5: xoay, đổi kích thước (compact sang regular và ngược lại) giữ mục đang chọn và chữ đang nhập ở một ô thử — evidence: `StateRestorationTests`.
- AC-6: iPhone bề rộng compact giữ chiều dọc; iPad và iPhone regular theo cả hai chiều; iPad chia đôi màn hình hẹp chuyển về thanh tab — evidence: `OrientationPolicyTests`, `ShellLayoutTests/testSplitViewCompact`.
- AC-7: lần mở đầu dùng tiếng Việt dù máy ở tiếng Anh; đổi ngôn ngữ thì chữ đổi ngay và còn sau khi mở lại — evidence: `LocalePreferenceTests`.
- AC-8: mọi khóa trong các String Catalog có cả `vi` và `en` — evidence: `StringCatalogParityTests`.
- AC-9: PR chỉ đổi tệp dưới `apps/ios` chạy `ios.yml` và không chạy workflow web — evidence: danh sách checks của PR.

**Impact outside the task:**

- Bộ lọc đường dẫn của workflow web → do AND-1 sửa; task này chỉ kiểm bằng checks của PR mình (AC-9) [owner: AND-1]
- Workspace JS (`apps/*`): `apps/ios` không có `package.json` → `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck` trên nhánh task cho kết quả như trước [owner: this task]
- Các task iOS sau dùng `AppRouter`, `AppPreferences`, `BrandPalette` → build và test của IOS-2..IOS-4 [owner: IOS-2, IOS-3, IOS-4]
- Phút chạy runner macOS của GitHub (D-25) → `ios.yml` chỉ chạy khi đổi `apps/ios/**` hoặc `contracts/mobile/**`; kiểm bằng bộ lọc đường dẫn trong tệp [owner: this task]

**Test plan:**

- AC-1: build và test, `cd apps/ios && xcodegen generate && xcodebuild -project Thi360.xcodeproj -scheme Thi360Dev -destination 'platform=iOS Simulator,name=iPhone 17' test`; build demo theo A5; `BundleInfoTests`, unit
- AC-2: `TransportSecurityTests`, `EnvironmentConfigTests`, unit, thêm `-only-testing:Thi360Tests/TransportSecurityTests -only-testing:Thi360Tests/EnvironmentConfigTests`; chạy thêm với scheme `Thi360Demo`
- AC-3: `ShellLayoutTests`, unit và ảnh chụp, `-only-testing:Thi360Tests/ShellLayoutTests`; chạy thêm với `-destination 'platform=iOS Simulator,name=iPad Air 11-inch (M4)'`
- AC-4: `PlaceholderScreensTests`, unit, `-only-testing:Thi360Tests/PlaceholderScreensTests`
- AC-5: `StateRestorationTests`, giao diện, `-only-testing:Thi360UITests/StateRestorationTests`
- AC-6: `OrientationPolicyTests`, unit; `ShellLayoutTests/testSplitViewCompact`, unit
- AC-7: `LocalePreferenceTests`, giao diện, `-only-testing:Thi360UITests/LocalePreferenceTests`
- AC-8: `StringCatalogParityTests`, unit, `-only-testing:Thi360Tests/StringCatalogParityTests`
- AC-9: checks của PR; ghi ảnh chụp danh sách checks vào bằng chứng
- Regression: `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck` trên nhánh task.
- Risk tests: không áp dụng (rủi ro `standard`).
- Test data: không cần backend.
- Not tested: iPhone Duo thật.

**Design / references:** `docs/design/app-hoc-vien/I-Home.dc.html`, `I-Pad-Account.dc.html`, `I-Duo-Open-Account.dc.html`, `I-Duo-Cover-Login.dc.html`, `ios.css`; `hosts-devin-mac.md`.

## AND-2 · Android: lõi phiên, lưu token an toàn và đăng nhập

**Spec:** R-4 (ĐT 24–28), R-5 (ĐT 29, 30), R-6 (ĐT 31–34, 36, 90), R-13 (ĐT 83), R-14 (ĐT 84–86); D-4, D-6, D-15, D-35.

**Lane owner:** lead

**Domain shape:**

- `SessionState` theo B1.3 là một `StateFlow` duy nhất trong `SessionManager`.
- `TokenStore` (giao diện): `read(): Tokens?`, `write(Tokens)`, `clear()`; hiện thực mã hóa bằng khóa trong Android Keystore, không vào bản sao lưu.
- `ApiError(code, message, fieldErrors)`; `AuthErrorMessage` ánh xạ `code` sang chuỗi, mã lạ dùng câu chung của màn.

**Deliverables:**

- Client API sinh từ `contracts/mobile/openapi-mobile.json` (B1.6) cho `mobile/*`, `me`, `me/profile`, đăng ký, quên và đặt lại mật khẩu, đổi mật khẩu; header `Accept-Language` theo `AppPreferences`.
- Tự làm mới token một lần cho nhiều lời gọi đồng thời; 401 khi làm mới thì xóa phiên và về `SignedOut(Expired)`; 403 `APP_STUDENT_ONLY` thì về `SignedOut(StudentOnly)`.
- Màn Đăng nhập (điện thoại và bố cục rộng) với kiểm tra tại chỗ, trạng thái đang gửi, thông báo lỗi; màn "Ứng dụng này dành cho học viên".
- Mở app: còn phiên thì vào `Active`; mất mạng thì trạng thái mất kết nối kèm "Thử lại", không đăng xuất; không bao giờ kết luận hết hạn theo đồng hồ máy.
- Log không chứa mật khẩu, token; cài lại app không còn phiên.

**Owned paths:** `apps/android/app/src/**/core/network/**`, `apps/android/app/src/**/core/session/**`, `apps/android/app/src/**/core/storage/**`, `apps/android/app/src/**/feature/login/**`, `apps/android/app/src/main/res/values/strings_login.xml`, `apps/android/app/src/main/res/values-en/strings_login.xml`, `apps/android/app/build.gradle.kts` (phần sinh client và thư viện mạng), `apps/android/gradle/libs.versions.toml`, một dòng trong `AppNavHost.kt`, bước test trong `.github/workflows/android.yml`.

**Out of scope:** đăng ký, quên và đổi mật khẩu (AND-3); màn khóa, Tài khoản, bảng màu (AND-4).

**Acceptance:**

- AC-1: chưa có phiên thì hiện màn Đăng nhập đủ thành phần của ĐT 24 — evidence: `LoginScreenTest#showsAllControls`; ảnh chụp ở điện thoại và 1280×800.
- AC-2: đăng nhập đúng vào mục Trang chủ; tắt hẳn rồi mở lại vẫn đăng nhập — evidence: `LoginFlowTest#successAndRelaunch`.
- AC-3: ô trống báo lỗi dưới ô và không gọi mạng; đang gửi thì nút khóa và không gửi lần hai — evidence: `LoginScreenTest#validation`, `#submitsOnce`.
- AC-4: mỗi mã `INVALID_CREDENTIALS`, `ACCOUNT_INACTIVE`, `LOGIN_THROTTLED`, `RATE_LIMITED` hiện đúng câu, giữ tên đăng nhập, xóa mật khẩu; mã lạ hiện câu chung, không hiện mã — evidence: `LoginScreenTest#errorMessages`, `AuthErrorMessageTest`.
- AC-5: 403 `APP_STUDENT_ONLY` khi đăng nhập hiện màn thông báo kèm tên đăng nhập và không lưu token; 403 đó khi làm mới thì xóa phiên và hiện cùng màn — evidence: `StudentOnlyFlowTest`.
- AC-6: access token hết hạn thì app làm mới rồi gọi lại; ba lời gọi đồng thời chỉ gây một lần làm mới — evidence: `TokenRefreshTest#singleFlight`.
- AC-7: 401 khi làm mới thì xóa phiên, về Đăng nhập kèm dòng "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại." — evidence: `TokenRefreshTest#expiredSessionSignsOut`.
- AC-8: mất mạng lúc mở, dù đồng hồ máy cho là phiên quá hạn, app không đăng xuất và hiện trạng thái mất kết nối; có mạng lại thì kết quả làm mới quyết định — evidence: `OfflineStartTest`.
- AC-9: token chỉ nằm trong kho mã hóa bằng khóa Keystore; không có trong tệp cấu hình thường; quy tắc sao lưu loại trừ kho này; cài lại app không còn phiên — evidence: `TokenStoreInstrumentedTest`, `BackupRulesTest`.
- AC-10: log của bản `demo` trong lúc đăng nhập không chứa mật khẩu và token — evidence: `LogRedactionTest`.
- AC-11: client sinh từ `contracts/mobile/openapi-mobile.json`; dữ liệu của máy chủ giả trong test lấy từ `contracts/mobile/auth/*.json` và `contracts/mobile/api/*.json`, không chép tay — evidence: `ContractFixturesTest`.

**Impact outside the task:**

- Khung điều hướng của AND-1 (`AppNavHost.kt`): thêm cổng chặn theo `SessionState` → `ShellLayoutTest`, `StateRestorationTest` của AND-1 vẫn xanh [owner: this task]
- Hợp đồng API mobile (`contracts/mobile/**`) do BE-1 sở hữu → `ContractFixturesTest`; build sinh client từ `contracts/mobile/openapi-mobile.json` của nhánh tích hợp [owner: this task]
- AND-3 và AND-4 dùng `SessionManager`, `TokenStore`, client API → test của hai task đó [owner: AND-3, AND-4]
- Thời gian build của `android.yml` tăng vì bước sinh client → ghi thời gian trước và sau vào báo cáo [owner: this task]

**Test plan:**

- AC-1: `LoginScreenTest#showsAllControls`, giao diện trên JVM, `cd apps/android && ./gradlew :app:testDevDebugUnitTest --tests '*LoginScreenTest'`; ảnh chụp, `./gradlew :app:verifyRoborazziDevDebug`
- AC-2: `LoginFlowTest#successAndRelaunch`, giao diện trên JVM với máy chủ giả, `./gradlew :app:testDevDebugUnitTest --tests '*LoginFlowTest'`
- AC-3: `LoginScreenTest#validation`, `#submitsOnce`, lệnh của AC-1
- AC-4: `LoginScreenTest#errorMessages`, `AuthErrorMessageTest`, unit, `./gradlew :app:testDevDebugUnitTest --tests '*AuthErrorMessageTest'`
- AC-5: `StudentOnlyFlowTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*StudentOnlyFlowTest'`
- AC-6: `TokenRefreshTest#singleFlight`, unit với máy chủ giả, `./gradlew :app:testDevDebugUnitTest --tests '*TokenRefreshTest'`
- AC-7: `TokenRefreshTest#expiredSessionSignsOut`, lệnh của AC-6
- AC-8: `OfflineStartTest`, unit, `./gradlew :app:testDevDebugUnitTest --tests '*OfflineStartTest'`
- AC-9: `TokenStoreInstrumentedTest`, trên máy ảo, `./gradlew :app:connectedDevDebugAndroidTest`; `BackupRulesTest`, unit JVM
- AC-10: `LogRedactionTest`, unit JVM trên biến thể demo, `./gradlew :app:testDemoDebugUnitTest --tests '*LogRedactionTest'`
- AC-11: `ContractFixturesTest`, unit, `./gradlew :app:testDevDebugUnitTest --tests '*ContractFixturesTest'`
- Regression: `./gradlew :app:testDevDebugUnitTest :app:verifyRoborazziDevDebug :app:lintDevDebug`.
- Risk tests: red-first cho AC-5, AC-6, AC-7, AC-9. Đồng thời: AC-6. Gửi lặp: AC-3 (`#submitsOnce`). Ma trận lỗi của AC-4 gồm đủ bốn mã và một mã lạ.
- Test data: máy chủ giả đọc `contracts/mobile/auth`; không cần backend thật ở mức lane. Một lượt chạy thật với backend thuộc QA.
- Not tested: thiết bị có Keystore phần cứng thật (máy ảo dùng Keystore phần mềm).

**Design / references:** `docs/design/app-hoc-vien/Main.dc.html`, `A-Tablet-Login.dc.html`, `A-RoleNotice.dc.html`; B1.1, B1.3.

## IOS-2 · iOS: lõi phiên, lưu token an toàn và đăng nhập

**Spec:** R-4 (ĐT 24–28), R-5 (ĐT 29, 30), R-6 (ĐT 31–34, 36, 90), R-13 (ĐT 83), R-14 (ĐT 84–86); D-4, D-6, D-15, D-35.

**Lane owner:** lead

**Domain shape:**

- `SessionState` theo B1.3, giữ trong một `@Observable SessionStore` duy nhất.
- `TokenStore` (protocol): `read()`, `write(_:)`, `clear()`; hiện thực bằng Keychain với `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`; cờ lần chạy đầu để xóa Keychain còn sót sau khi cài lại.
- `ApiError`, `AuthErrorMessage` như AND-2.

**Deliverables:**

- Client API sinh bằng `swift-openapi-generator` (B1.6) cho cùng nhóm thao tác với AND-2; header `Accept-Language` theo `AppPreferences`.
- Tự làm mới token một lần cho nhiều lời gọi đồng thời (actor); 401 khi làm mới và 403 `APP_STUDENT_ONLY` xử lý như B1.3.
- Màn Đăng nhập cho iPhone, iPad, iPhone Duo gập và mở; màn "Ứng dụng này dành cho học viên".
- Hành vi mở app, mất mạng, log và cài lại như AND-2.

**Owned paths:** `apps/ios/Thi360/Core/Network/**`, `apps/ios/Thi360/Core/Session/**`, `apps/ios/Thi360/Core/Storage/**`, `apps/ios/Thi360/Features/Login/**`, `apps/ios/Thi360/Resources/Auth.xcstrings`, `apps/ios/Thi360Tests/Login/**`, `apps/ios/Thi360Tests/Session/**`, `apps/ios/Thi360UITests/Login/**`, khối package và plugin sinh client trong `apps/ios/project.yml`, một dòng trong `AppRouter.swift`, bước test trong `.github/workflows/ios.yml`.

**Out of scope:** đăng ký, quên và đổi mật khẩu (IOS-3); màn khóa, Tài khoản, bảng màu (IOS-4).

**Acceptance:**

- AC-1: chưa có phiên thì hiện màn Đăng nhập đủ thành phần của ĐT 24 — evidence: `LoginViewTests/testShowsAllControls`; ảnh chụp ở iPhone 17, iPad, simulator iPhone Duo gập và mở.
- AC-2: đăng nhập đúng vào Trang chủ; tắt hẳn rồi mở lại vẫn đăng nhập — evidence: `LoginFlowUITests/testSuccessAndRelaunch`.
- AC-3: ô trống báo lỗi dưới ô và không gọi mạng; đang gửi thì nút khóa và không gửi lần hai — evidence: `LoginViewTests/testValidation`, `testSubmitsOnce`.
- AC-4: bốn mã lỗi hiện đúng câu, giữ tên đăng nhập, xóa mật khẩu; mã lạ hiện câu chung — evidence: `LoginViewTests/testErrorMessages`, `AuthErrorMessageTests`.
- AC-5: 403 `APP_STUDENT_ONLY` khi đăng nhập hiện màn thông báo và không lưu token; khi làm mới thì xóa phiên và hiện cùng màn — evidence: `StudentOnlyFlowTests`.
- AC-6: access token hết hạn thì làm mới rồi gọi lại; ba lời gọi đồng thời chỉ gây một lần làm mới — evidence: `TokenRefreshTests/testSingleFlight`.
- AC-7: 401 khi làm mới thì xóa phiên và về Đăng nhập kèm dòng hết hạn — evidence: `TokenRefreshTests/testExpiredSessionSignsOut`.
- AC-8: mất mạng lúc mở, dù đồng hồ máy cho là quá hạn, app không đăng xuất và hiện trạng thái mất kết nối — evidence: `OfflineStartTests`.
- AC-9: token chỉ nằm trong Keychain với thuộc tính chỉ dùng trên máy này; `UserDefaults` và thư mục tài liệu không chứa token; lần chạy đầu sau khi cài lại xóa mục Keychain cũ — evidence: `TokenStoreTests`, `FirstRunCleanupTests`.
- AC-10: log của bản demo trong lúc đăng nhập không chứa mật khẩu và token — evidence: `LogRedactionTests`.
- AC-11: client sinh từ `contracts/mobile/openapi-mobile.json`; dữ liệu của máy chủ giả lấy từ `contracts/mobile/auth/*.json` và `contracts/mobile/api/*.json` — evidence: `ContractFixturesTests`.

**Impact outside the task:**

- Khung của IOS-1 (`AppRouter.swift`): thêm cổng chặn theo `SessionState` → `ShellLayoutTests`, `StateRestorationTests` của IOS-1 vẫn xanh [owner: this task]
- Hợp đồng API mobile của BE-1 (`contracts/mobile/**`) → `ContractFixturesTests`; build sinh client từ `contracts/mobile/openapi-mobile.json` của nhánh tích hợp [owner: this task]
- IOS-3 và IOS-4 dùng `SessionStore`, `TokenStore`, client API → test của hai task đó [owner: IOS-3, IOS-4]
- `project.yml` của IOS-1: thêm package `swift-openapi-generator`, `swift-openapi-runtime`, `swift-openapi-urlsession` và plugin sinh mã cho target → `xcodegen generate` và build hai scheme xanh; `BundleInfoTests`, `ShellLayoutTests` của IOS-1 xanh [owner: this task]
- Thời gian chạy `ios.yml` trên runner macOS tăng → ghi thời gian trước và sau vào báo cáo [owner: this task]

**Test plan:**

- AC-1: `LoginViewTests/testShowsAllControls`, unit và ảnh chụp, `cd apps/ios && xcodegen generate && xcodebuild -project Thi360.xcodeproj -scheme Thi360Dev -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:Thi360Tests/LoginViewTests`
- AC-2: `LoginFlowUITests/testSuccessAndRelaunch`, giao diện với máy chủ giả trong tiến trình, `-only-testing:Thi360UITests/LoginFlowUITests`
- AC-3: `LoginViewTests/testValidation`, `testSubmitsOnce`, lệnh của AC-1
- AC-4: `LoginViewTests/testErrorMessages`, `AuthErrorMessageTests`, unit, `-only-testing:Thi360Tests/AuthErrorMessageTests`
- AC-5: `StudentOnlyFlowTests`, unit, `-only-testing:Thi360Tests/StudentOnlyFlowTests`
- AC-6: `TokenRefreshTests/testSingleFlight`, unit, `-only-testing:Thi360Tests/TokenRefreshTests`
- AC-7: `TokenRefreshTests/testExpiredSessionSignsOut`, lệnh của AC-6
- AC-8: `OfflineStartTests`, unit, `-only-testing:Thi360Tests/OfflineStartTests`
- AC-9: `TokenStoreTests`, `FirstRunCleanupTests`, unit trên simulator, `-only-testing:Thi360Tests/TokenStoreTests -only-testing:Thi360Tests/FirstRunCleanupTests`
- AC-10: `LogRedactionTests`, unit với scheme demo, `xcodebuild … -scheme Thi360Demo … test -only-testing:Thi360Tests/LogRedactionTests`
- AC-11: `ContractFixturesTests`, unit, `-only-testing:Thi360Tests/ContractFixturesTests`
- Regression: toàn bộ `xcodebuild … -scheme Thi360Dev … test`.
- Risk tests: red-first cho AC-5, AC-6, AC-7, AC-9. Đồng thời: AC-6. Gửi lặp: AC-3 (`testSubmitsOnce`).
- Test data: máy chủ giả đọc `contracts/mobile/auth`. Một lượt chạy thật với backend thuộc QA.
- Not tested: Keychain trên thiết bị thật; gỡ và cài lại thật (kiểm bằng cờ lần chạy đầu trên simulator).

**Design / references:** `docs/design/app-hoc-vien/I-Login.dc.html`, `I-Pad-Login.dc.html`, `I-Duo-Cover-Login.dc.html`, `I-Duo-Open-Login.dc.html`, `I-RoleNotice.dc.html`; B1.1, B1.3.

## AND-3 · Android: đăng ký có OTP, quên và đặt lại mật khẩu, đổi mật khẩu

**Spec:** R-7 (ĐT 37–43, 91, 92), R-8 (ĐT 44–49, 51), R-9 (ĐT 52–55, 89), R-2 (ĐT 11), R-13 (ĐT 82); D-5, D-13, D-24.

**Lane owner:** lead

**Domain shape:**

- `RegistrationState { Form(data), Otp(registrationId, email, resendAt), Done }`; `PasswordRules` (năm điều kiện) là hàm thuần dùng chung cho đăng ký, đặt lại và đổi mật khẩu.
- `ResetLink(token)` phân tích từ `https://<máy chủ web>/reset-password?token=…`; mã chỉ nằm trong trạng thái của màn.
- `NewPasswordMode { FromLink(token), Forced, Voluntary }`.

**Deliverables:**

- Màn Đăng ký và Xác minh email với kiểm tra tại chỗ, lỗi theo ô, đếm ngược theo `resendAfterSeconds` và `retryAfter`, quay lại sửa thông tin; gửi `termsVersion` trùng web.
- Màn Quên mật khẩu (form, trạng thái đã gửi, gửi lại đếm 60 giây).
- App Links cho `/reset-password` trên máy chủ web của từng flavor; màn Đặt mật khẩu mới ba chế độ; liên kết mở được cả khi app đang khóa.
- Cổng chặn `MustChangePassword`: chỉ còn màn đặt mật khẩu mới và lối đăng xuất; áp cho cả khi cờ về qua lần làm mới.
- Đổi mật khẩu từ Tài khoản (màn chi tiết để AND-4 gắn vào).

**Owned paths:** `apps/android/app/src/**/feature/register/**`, `apps/android/app/src/**/feature/recovery/**`, `apps/android/app/src/**/feature/password/**`, `apps/android/app/src/main/res/values/strings_recovery.xml`, `apps/android/app/src/main/res/values-en/strings_recovery.xml`, phần `intent-filter` App Links trong `apps/android/app/src/main/AndroidManifest.xml`, một dòng trong `AppNavHost.kt`, bước test trong `.github/workflows/android.yml`.

**Out of scope:** tệp `assetlinks.json` (WEB-1); màn khóa và mục Tài khoản (AND-4); `SessionManager` (AND-2, chỉ dùng).

**Acceptance:**

- AC-1: đăng ký hợp lệ chuyển sang màn Xác minh email ghi rõ email; request có `termsVersion` trùng chuỗi của web và header `Accept-Language` theo ngôn ngữ app — evidence: `RegisterFlowTest#submitGoesToOtp`.
- AC-2: dữ liệu sai quy tắc báo lỗi dưới đúng ô và không gọi mạng; từng điều kiện mật khẩu đổi trạng thái khi gõ; `USERNAME_TAKEN`, `EMAIL_TAKEN` hiện dưới ô tương ứng và giữ dữ liệu — evidence: `RegisterScreenTest`, `PasswordRulesTest`.
- AC-3: mã đúng thì vào app như sau đăng nhập; mã sai hiện số lần còn lại; mã hết hạn hiện hướng dẫn gửi lại — evidence: `OtpScreenTest`.
- AC-4: nút gửi lại khóa theo `resendAfterSeconds`, và theo `retryAfter` khi nhận 429; hết chờ thì gửi được và hiện thông báo đã gửi mã mới — evidence: `OtpScreenTest#resendCooldown`.
- AC-5: "Quay lại sửa thông tin" giữ dữ liệu trừ mật khẩu; tắt hẳn app ở màn OTP rồi mở lại thì về Đăng nhập — evidence: `RegisterFlowTest#backKeepsData`, `#processDeathReturnsToLogin`.
- AC-6: Quên mật khẩu hiện cùng một màn "Hãy kiểm tra hộp thư" cho mọi email đúng dạng; gửi lại đếm 60 giây và luôn hiện cùng nội dung — evidence: `ForgotPasswordScreenTest`.
- AC-7: intent `VIEW` với `https://<máy chủ web>/reset-password?token=abc` mở màn Đặt mật khẩu mới, kể cả khi app đang ở trạng thái `Locked`; intent tới đường dẫn khác không được app nhận — evidence: `ResetLinkInstrumentedTest`, `ManifestAppLinksTest`.
- AC-8: đặt lại thành công thì về Đăng nhập kèm dòng đã đặt lại và phiên đang có trên máy bị xóa; `RESET_TOKEN_INVALID` hiện "Liên kết không còn dùng được" kèm lối sang Quên mật khẩu; nhập lại không khớp hoặc thiếu điều kiện thì không gọi mạng — evidence: `NewPasswordFromLinkTest`.
- AC-9: mã đặt lại không có trong log, không ghi xuống bộ nhớ lưu trữ, và sau khi rời màn thì quay lại không gửi lại được mã cũ; log của bản `demo` trong lúc đăng ký, xác minh OTP và đổi mật khẩu không chứa mật khẩu, mã OTP — evidence: `ResetTokenHandlingTest`, `RecoveryLogRedactionTest`.
- AC-10: `MustChangePassword` (từ đăng nhập hoặc từ lần làm mới) chỉ cho màn đặt mật khẩu mới và đăng xuất; đổi xong vào app không phải đăng nhập lại — evidence: `ForcedPasswordChangeTest`.
- AC-11: đổi mật khẩu tự nguyện thành công giữ phiên; mật khẩu hiện tại sai, mật khẩu mới trùng tên đăng nhập hoặc thiếu điều kiện hiện lỗi đúng ô — evidence: `ChangePasswordTest`.
- AC-12: xoay máy hoặc đổi kích thước khi đang nhập form đổi mật khẩu giữ nguyên dữ liệu — evidence: `ChangePasswordTest#survivesConfigurationChange`.

**Impact outside the task:**

- Manifest của app (`AndroidManifest.xml`): thêm `intent-filter` không làm đổi màn khởi động → `ManifestTest` của AND-1 xanh [owner: this task]
- `SessionManager` của AND-2: đặt lại mật khẩu xóa phiên, đổi mật khẩu giữ phiên → `TokenRefreshTest`, `LoginFlowTest` của AND-2 xanh [owner: this task]
- AND-4 gắn màn Đổi mật khẩu vào Tài khoản và phải cho liên kết đặt lại đi qua màn khóa → `LockBypassForResetLinkTest` ở AND-4 [owner: AND-4]
- Hợp đồng lỗi của đăng ký và OTP (`EmailCodeErrorResponse`, `RegisterStartResponse`) → dữ liệu giả lấy từ `contracts/mobile/api/*.json` do BE-1 chứng nhận; `ContractFixturesTest` mở rộng [owner: this task]
- Hệ điều hành xác minh App Links cần `assetlinks.json` trên tên miền thật (WEB-1 và triển khai) → ghi vào danh sách việc sau merge của QA [owner: QA]

**Test plan:**

- AC-1: `RegisterFlowTest#submitGoesToOtp`, giao diện trên JVM, `cd apps/android && ./gradlew :app:testDevDebugUnitTest --tests '*RegisterFlowTest'`
- AC-2: `RegisterScreenTest`, `PasswordRulesTest`, giao diện và unit, `./gradlew :app:testDevDebugUnitTest --tests '*RegisterScreenTest' --tests '*PasswordRulesTest'`
- AC-3: `OtpScreenTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*OtpScreenTest'`
- AC-4: `OtpScreenTest#resendCooldown`, lệnh của AC-3 (đồng hồ giả)
- AC-5: `RegisterFlowTest#backKeepsData`, `#processDeathReturnsToLogin`, lệnh của AC-1
- AC-6: `ForgotPasswordScreenTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*ForgotPasswordScreenTest'`
- AC-7: `ResetLinkInstrumentedTest`, trên máy ảo, `./gradlew :app:connectedDevDebugAndroidTest`; `ManifestAppLinksTest`, unit JVM
- AC-8: `NewPasswordFromLinkTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*NewPasswordFromLinkTest'`
- AC-9: `ResetTokenHandlingTest`, `RecoveryLogRedactionTest`, unit trên biến thể demo, `./gradlew :app:testDemoDebugUnitTest --tests '*ResetTokenHandlingTest' --tests '*RecoveryLogRedactionTest'`
- AC-10: `ForcedPasswordChangeTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*ForcedPasswordChangeTest'`
- AC-11: `ChangePasswordTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*ChangePasswordTest'`
- AC-12: `ChangePasswordTest#survivesConfigurationChange`, lệnh của AC-11
- Regression: `./gradlew :app:testDevDebugUnitTest :app:verifyRoborazziDevDebug :app:lintDevDebug`.
- Risk tests: red-first cho AC-7, AC-8, AC-9, AC-10. Gửi lặp: nút gửi của mỗi form chỉ gửi một lần (ca trong từng lớp test màn). Ranh giới: mật khẩu 7, 8, 64, 65 ký tự trong `PasswordRulesTest`.
- Test data: máy chủ giả; đồng hồ giả cho đếm ngược.
- Not tested: bấm liên kết từ ứng dụng thư thật và việc hệ điều hành xác minh tên miền (cần triển khai).

**Design / references:** `docs/design/app-hoc-vien/A-Register.dc.html`, `A-Otp.dc.html`, `A-Forgot.dc.html`, `A-NewPassword.dc.html`, `A-Fold-Account.dc.html`; `docs/specs/2026-11-22-xac-minh-otp-khi-dang-ky.md`.

## AND-4 · Android: Tài khoản, sinh trắc học và màn khóa, giao diện theo cấu hình admin

**Spec:** R-10 (ĐT 56–66, 93), R-11 (ĐT 67–70), R-12 (ĐT 72–75, 77, 78), R-2 (ĐT 7), R-13 (ĐT 80 phần lối vào); D-16, D-18, D-20, D-21, D-28.

**Lane owner:** lead

**Domain shape:**

- `BiometricState { Unavailable(reason), Off, On }` và `PromptMemory { lastAnswer: Yes | Later | null }` (theo máy); dữ liệu bảo vệ là refresh token bọc bằng khóa Keystore đòi sinh trắc học, bị vô hiệu khi bộ sinh trắc học đổi.
- `LockPolicy(backgroundedAt, now) → shouldLock` (ngưỡng 5 phút, mở nguội luôn khóa khi `On`).
- `AppearanceRepository`: `current: StateFlow<BrandPalette>`, `refresh()`; bản lưu gần nhất trên máy; nguồn theo B1.4.

**Deliverables:**

- Mục Tài khoản: thông tin từ `me/profile` (chỉ đọc) với bốn trạng thái; Đổi mật khẩu (màn của AND-3); công tắc sinh trắc học; Ngôn ngữ; Giao diện Sáng/Tối; Đăng xuất có xác nhận. Từ 840dp hiện hai cột.
- Hỏi bật sinh trắc học sau đăng nhập theo ĐT 56; màn khóa ba nút; khóa theo `LockPolicy`; che nội dung ở trình chuyển ứng dụng; "Nhập mật khẩu" mở Đăng nhập điền sẵn tên đăng nhập và quay lại được màn khóa; đăng nhập đúng thì thay phiên, gọi `mobile/logout` cho phiên cũ, hỏi bật lại.
- Lấy bảng màu và chế độ mặc định từ route của B1.4 khi mở app và khi quay lại sau ít nhất 60 giây; áp ngay; giữ bản đang dùng khi lỗi; lựa chọn Sáng/Tối của người dùng thắng `mode`.
- Đăng xuất: gọi `mobile/logout`, xóa token và dữ liệu sinh trắc học, giữ ngôn ngữ và sáng/tối; mất mạng vẫn xóa phiên trên máy.

**Owned paths:** `apps/android/app/src/**/feature/account/**`, `apps/android/app/src/**/feature/lock/**`, `apps/android/app/src/**/core/biometric/**`, `apps/android/app/src/**/core/appearance/**`, `apps/android/app/src/main/res/values/strings_account.xml`, `apps/android/app/src/main/res/values-en/strings_account.xml`, một dòng trong `AppNavHost.kt`, bước test trong `.github/workflows/android.yml`.

**Out of scope:** sửa hồ sơ, đổi email, ảnh đại diện; route bảng màu (WEB-1); màn đổi mật khẩu (AND-3, chỉ gắn vào).

**Acceptance:**

- AC-1: Tài khoản hiện họ tên, tên đăng nhập, email và nhãn xác minh; tài khoản không có email thì ẩn dòng email; có đủ trạng thái đang tải, lỗi kèm "Thử lại", mất kết nối; các cài đặt trên máy vẫn dùng được khi lỗi — evidence: `AccountScreenTest`; ảnh chụp ở điện thoại và 840×720.
- AC-2: từ 840dp Tài khoản hiện danh sách bên trái và chi tiết bên phải; dưới 840dp là một cột — evidence: `AccountScreenTest#twoPane`.
- AC-3: đăng xuất có xác nhận gọi `mobile/logout`, xóa token và dữ liệu sinh trắc học, về Đăng nhập; mất mạng vẫn xóa trên máy; người dùng khác đăng nhập sau đó không thấy dữ liệu của người trước; ngôn ngữ và sáng/tối giữ nguyên — evidence: `LogoutTest`.
- AC-4: máy có sinh trắc học đã đăng ký thì sau đăng nhập bằng mật khẩu app hỏi bật, trừ khi câu trả lời gần nhất trên máy là "Để sau"; máy không có hoặc chưa đăng ký thì không hỏi và công tắc bị khóa kèm giải thích; trả lời "Để sau", đăng xuất rồi đăng nhập lại thì không bị hỏi — evidence: `BiometricPromptPolicyTest`.
- AC-5: bật chỉ thành công khi hệ điều hành xác nhận sinh trắc học; hủy hoặc thất bại thì vẫn tắt; tắt công tắc thì không còn khóa; bật lại phải xác nhận lại — evidence: `BiometricEnableTest`.
- AC-6: khi đã bật, mở nguội hoặc quay lại sau quá 5 phút hiện màn khóa ba nút và che nội dung ở trình chuyển ứng dụng; dưới 5 phút không khóa; mở khóa đúng về đúng màn đang xem — evidence: `LockPolicyTest`, `LockScreenTest`, `RecentsPrivacyInstrumentedTest`.
- AC-7: sinh trắc học thất bại, bị hủy hoặc bị khóa tạm thì app vẫn khóa; "Nhập mật khẩu" mở Đăng nhập điền sẵn tên đăng nhập, quay lại thì về màn khóa và phiên còn nguyên; đăng nhập đúng thì dùng phiên mới, gọi `mobile/logout` cho phiên cũ (lỗi thì bỏ qua), xóa dữ liệu sinh trắc học cũ và hỏi bật lại theo quy tắc của AC-4 — evidence: `LockPasswordFallbackTest`.
- AC-8: "Đăng nhập bằng tài khoản khác" đăng xuất phiên đang giữ, tắt tính năng và xóa dữ liệu sinh trắc học, về Đăng nhập trống — evidence: `LockScreenTest#otherAccount`.
- AC-9: bộ sinh trắc học của máy đổi thì lần mở khóa kế tiếp đòi mật khẩu, `BiometricState` chuyển `Off` và công tắc trong Tài khoản hiện tắt cho tới khi người dùng bật lại — evidence: `BiometricKeyInvalidationInstrumentedTest`, `BiometricInvalidationStateTest`.
- AC-10: có mạng, mở khóa xong mà làm mới báo phiên đã chết thì về Đăng nhập kèm dòng hết hạn; mất mạng thì mở khóa bình thường — evidence: `LockExpiredSessionTest`.
- AC-11: liên kết đặt lại mật khẩu mở màn của AND-3 khi app đang khóa, không đòi mở khóa; đặt lại thành công lúc đang khóa thì về Đăng nhập, tính năng tắt và dữ liệu sinh trắc học bị xóa — evidence: `LockBypassForResetLinkTest`.
- AC-12: với tệp hợp đồng bảng màu và một bảng màu khác, màu thương hiệu và màu trung tính trên màn trùng `Palette` ở cả hai chế độ — evidence: `PaletteApplicationTest`; ảnh chụp hai bảng màu.
- AC-13: route trả `version` khác bản đang lưu thì app áp màu mới ở lần mở hoặc lần quay lại sau ít nhất 60 giây, không cần đăng nhập lại; quay lại sớm hơn thì không gọi mạng; route trả 304 thì không đổi gì — evidence: `AppearanceRefreshTest`.
- AC-14: chưa chọn Sáng/Tối thì theo `mode` của cấu hình và không theo hệ điều hành; đã chọn thì giữ dù `mode` đổi, còn sau khi mở lại và sau đăng xuất — evidence: `ThemeModeTest`.
- AC-15: mất mạng dùng bảng màu đã lưu; lần đầu chưa có thì dùng bộ mặc định sáng; route trả 503 (kể cả vì `presetId` lạ), lỗi mạng hoặc JSON sai hình thì giữ bảng đang dùng và không lỗi — evidence: `AppearanceFallbackTest`.
- AC-16: nhãn trên Android ghi "vân tay hoặc khuôn mặt" — evidence: `LockScreenTest#labels`.

**Impact outside the task:**

- `SessionManager` và cổng chặn của AND-2: thêm trạng thái `Locked` và thay phiên khi đăng nhập lại từ màn khóa → `TokenRefreshTest`, `LoginFlowTest`, `StudentOnlyFlowTest` của AND-2 xanh [owner: this task]
- Màn Đăng nhập của AND-2 nhận tham số "điền sẵn tên đăng nhập, quay lại màn khóa" → `LoginScreenTest` của AND-2 xanh; ca mới ở `LockPasswordFallbackTest` [owner: this task]
- Màn Đổi mật khẩu và liên kết đặt lại của AND-3 → `ChangePasswordTest`, `NewPasswordFromLinkTest` của AND-3 xanh [owner: this task]
- Chủ đề và `BrandPalette` của AND-1 được cấp màu từ máy chủ → `ShellLayoutTest` và ảnh chụp của AND-1 với bảng màu mặc định không đổi [owner: this task]
- Route bảng màu và tệp hợp đồng của WEB-1 → `PaletteApplicationTest` đọc `contracts/mobile/appearance` [owner: this task]

**Test plan:**

- AC-1: `AccountScreenTest`, giao diện trên JVM, `cd apps/android && ./gradlew :app:testDevDebugUnitTest --tests '*AccountScreenTest'`; ảnh chụp, `./gradlew :app:verifyRoborazziDevDebug`
- AC-2: `AccountScreenTest#twoPane`, lệnh của AC-1
- AC-3: `LogoutTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*LogoutTest'`
- AC-4: `BiometricPromptPolicyTest`, unit, `./gradlew :app:testDevDebugUnitTest --tests '*BiometricPromptPolicyTest'`
- AC-5: `BiometricEnableTest`, unit với lớp sinh trắc học giả, `./gradlew :app:testDevDebugUnitTest --tests '*BiometricEnableTest'`
- AC-6: `LockPolicyTest`, unit; `LockScreenTest`, giao diện trên JVM; `RecentsPrivacyInstrumentedTest`, trên máy ảo, `./gradlew :app:connectedDevDebugAndroidTest`
- AC-7: `LockPasswordFallbackTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*LockPasswordFallbackTest'`
- AC-8: `LockScreenTest#otherAccount`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*LockScreenTest'`
- AC-9: `BiometricKeyInvalidationInstrumentedTest`, trên máy ảo, `./gradlew :app:connectedDevDebugAndroidTest`; `BiometricInvalidationStateTest`, unit với lớp sinh trắc học giả, `./gradlew :app:testDevDebugUnitTest --tests '*BiometricInvalidationStateTest'`
- AC-10: `LockExpiredSessionTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*LockExpiredSessionTest'`
- AC-11: `LockBypassForResetLinkTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*LockBypassForResetLinkTest'`
- AC-12: `PaletteApplicationTest`, giao diện trên JVM và ảnh chụp, `./gradlew :app:testDevDebugUnitTest --tests '*PaletteApplicationTest'`
- AC-13: `AppearanceRefreshTest`, unit với đồng hồ giả, `./gradlew :app:testDevDebugUnitTest --tests '*AppearanceRefreshTest'`
- AC-14: `ThemeModeTest`, giao diện trên JVM, `./gradlew :app:testDevDebugUnitTest --tests '*ThemeModeTest'`
- AC-15: `AppearanceFallbackTest`, unit, `./gradlew :app:testDevDebugUnitTest --tests '*AppearanceFallbackTest'`
- AC-16: `LockScreenTest#labels`, lệnh của AC-8
- Regression: `./gradlew :app:testDevDebugUnitTest :app:verifyRoborazziDevDebug :app:lintDevDebug`; các lớp test của AND-2, AND-3 nêu ở mục Ảnh hưởng.
- Risk tests: red-first cho AC-3, AC-6, AC-7, AC-9. Ranh giới của `LockPolicyTest`: 4 phút 59 giây, 5 phút, 5 phút 1 giây, mở nguội. Gửi lặp: bấm Đăng xuất hai lần chỉ gọi một lần.
- Test data: máy chủ giả; lớp sinh trắc học giả cho test trên JVM; máy ảo có vân tay đăng ký bằng `adb -e emu finger touch` cho test trên máy ảo.
- Not tested: cảm biến thật; khóa tạm của hệ điều hành sau nhiều lần sai (giả lập bằng mã lỗi).

**Design / references:** `docs/design/app-hoc-vien/A-Account.dc.html`, `A-Fold-Account.dc.html`, `A-BioEnable.dc.html`, `A-BioUnlock.dc.html`; B1.3, B1.4.

## IOS-3 · iOS: đăng ký có OTP, quên và đặt lại mật khẩu, đổi mật khẩu

**Spec:** R-7 (ĐT 37–43, 91, 92), R-8 (ĐT 44–49, 51), R-9 (ĐT 52–55, 89), R-2 (ĐT 11), R-13 (ĐT 82); D-5, D-13, D-24.

**Lane owner:** lead

**Domain shape:**

- `RegistrationState`, `PasswordRules`, `ResetLink`, `NewPasswordMode` cùng hình với AND-3.
- Universal Links: `applinks:<máy chủ web>` trong entitlements theo từng cấu hình; xử lý qua `onOpenURL` và `NSUserActivity`.

**Deliverables:**

- Các màn và hành vi như AND-3 cho iPhone, iPad, iPhone Duo.
- Entitlement Associated Domains cho đường dẫn `/reset-password`; màn Đặt mật khẩu mới ba chế độ; liên kết mở được khi app đang khóa.
- Tham số khởi chạy `-resetLinkURL` chỉ có hiệu lực ở cấu hình dev, để UI test mô phỏng việc mở liên kết mà không cần lược đồ URL riêng.
- Cổng chặn `MustChangePassword`; màn Đổi mật khẩu để IOS-4 gắn vào.

**Owned paths:** `apps/ios/Thi360/Features/Register/**`, `apps/ios/Thi360/Features/Recovery/**`, `apps/ios/Thi360/Features/Password/**`, `apps/ios/Thi360/Resources/Recovery.xcstrings`, `apps/ios/Thi360/Thi360.entitlements`, khóa entitlements của target trong `apps/ios/project.yml`, `apps/ios/Thi360Tests/Register/**`, `apps/ios/Thi360Tests/Recovery/**`, `apps/ios/Thi360Tests/Password/**`, `apps/ios/Thi360UITests/Recovery/**`, một dòng trong `AppRouter.swift`, bước test trong `.github/workflows/ios.yml`.

**Out of scope:** tệp `apple-app-site-association` (WEB-1); màn khóa và Tài khoản (IOS-4); `SessionStore` (IOS-2, chỉ dùng).

**Acceptance:**

- AC-1: đăng ký hợp lệ chuyển sang màn Xác minh email; request có `termsVersion` trùng web và `Accept-Language` theo ngôn ngữ app — evidence: `RegisterFlowTests/testSubmitGoesToOtp`.
- AC-2: dữ liệu sai quy tắc báo lỗi dưới đúng ô và không gọi mạng; điều kiện mật khẩu đổi trạng thái khi gõ; `USERNAME_TAKEN`, `EMAIL_TAKEN` hiện dưới ô tương ứng — evidence: `RegisterViewTests`, `PasswordRulesTests`.
- AC-3: mã đúng thì vào app; mã sai hiện số lần còn lại; mã hết hạn hiện hướng dẫn gửi lại — evidence: `OtpViewTests`.
- AC-4: nút gửi lại khóa theo `resendAfterSeconds` và `retryAfter`; hết chờ thì gửi được và hiện thông báo — evidence: `OtpViewTests/testResendCooldown`.
- AC-5: "Quay lại sửa thông tin" giữ dữ liệu trừ mật khẩu; tắt hẳn app ở màn OTP rồi mở lại thì về Đăng nhập — evidence: `RegisterFlowTests/testBackKeepsData`, `RegisterFlowUITests/testRelaunchReturnsToLogin`.
- AC-6: Quên mật khẩu hiện cùng một màn cho mọi email đúng dạng; gửi lại đếm 60 giây — evidence: `ForgotPasswordViewTests`.
- AC-7: mở URL `https://<máy chủ web>/reset-password?token=abc` đưa app tới màn Đặt mật khẩu mới, kể cả khi đang `locked`; URL đường dẫn khác bị bỏ qua; entitlements chỉ khai `applinks` của máy chủ web; bản demo bỏ qua tham số khởi chạy `-resetLinkURL` — evidence: `ResetLinkRoutingTests` (có ca chạy với scheme demo), `EntitlementsTests`, `ResetLinkUITests` (UI test khởi chạy app với tham số `-resetLinkURL <url>`, tham số này chỉ được đọc ở cấu hình dev).
- AC-8: đặt lại thành công về Đăng nhập kèm dòng đã đặt lại và xóa phiên trên máy; `RESET_TOKEN_INVALID` hiện "Liên kết không còn dùng được"; không khớp hoặc thiếu điều kiện thì không gọi mạng — evidence: `NewPasswordFromLinkTests`.
- AC-9: mã đặt lại không có trong log, không ghi xuống đĩa, và sau khi rời màn không gửi lại được; log của bản demo trong lúc đăng ký, xác minh OTP và đổi mật khẩu không chứa mật khẩu, mã OTP — evidence: `ResetTokenHandlingTests`, `RecoveryLogRedactionTests`.
- AC-10: `mustChangePassword` (từ đăng nhập hoặc làm mới) chỉ cho màn đặt mật khẩu mới và đăng xuất; đổi xong vào app không phải đăng nhập lại — evidence: `ForcedPasswordChangeTests`.
- AC-11: đổi mật khẩu tự nguyện giữ phiên; các lỗi hiện đúng ô — evidence: `ChangePasswordTests`.
- AC-12: xoay máy hoặc chuyển compact sang regular khi đang nhập form đổi mật khẩu giữ nguyên dữ liệu — evidence: `ChangePasswordUITests/testSurvivesSizeClassChange`.

**Impact outside the task:**

- `project.yml` và entitlements của IOS-1: thêm Associated Domains không làm hỏng build khi chưa có Team ID → build scheme dev và demo trên simulator xanh; `BundleInfoTests` của IOS-1 xanh [owner: this task]
- `SessionStore` của IOS-2: đặt lại mật khẩu xóa phiên, đổi mật khẩu giữ phiên → `TokenRefreshTests`, `LoginFlowUITests` của IOS-2 xanh [owner: this task]
- IOS-4 gắn màn Đổi mật khẩu và cho liên kết đặt lại đi qua màn khóa → `LockBypassForResetLinkTests` ở IOS-4 [owner: IOS-4]
- Universal Links thật cần tệp trên tên miền và Team ID (`G-TEAMID`) → ghi vào danh sách việc sau merge của QA [owner: QA]

**Test plan:**

- AC-1: `RegisterFlowTests/testSubmitGoesToOtp`, unit, `cd apps/ios && xcodegen generate && xcodebuild -project Thi360.xcodeproj -scheme Thi360Dev -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:Thi360Tests/RegisterFlowTests`
- AC-2: `RegisterViewTests`, `PasswordRulesTests`, unit, `-only-testing:Thi360Tests/RegisterViewTests -only-testing:Thi360Tests/PasswordRulesTests`
- AC-3: `OtpViewTests`, unit, `-only-testing:Thi360Tests/OtpViewTests`
- AC-4: `OtpViewTests/testResendCooldown`, lệnh của AC-3 (đồng hồ giả)
- AC-5: `RegisterFlowTests/testBackKeepsData`, unit; `RegisterFlowUITests/testRelaunchReturnsToLogin`, giao diện, `-only-testing:Thi360UITests/RegisterFlowUITests`
- AC-6: `ForgotPasswordViewTests`, unit, `-only-testing:Thi360Tests/ForgotPasswordViewTests`
- AC-7: `ResetLinkRoutingTests`, `EntitlementsTests`, unit, chạy với cả scheme `Thi360Dev` và `Thi360Demo`; `ResetLinkUITests`, giao diện, `-only-testing:Thi360UITests/ResetLinkUITests`
- AC-8: `NewPasswordFromLinkTests`, unit, `-only-testing:Thi360Tests/NewPasswordFromLinkTests`
- AC-9: `ResetTokenHandlingTests`, `RecoveryLogRedactionTests`, unit với scheme demo, `xcodebuild … -scheme Thi360Demo … test -only-testing:Thi360Tests/ResetTokenHandlingTests -only-testing:Thi360Tests/RecoveryLogRedactionTests`
- AC-10: `ForcedPasswordChangeTests`, unit, `-only-testing:Thi360Tests/ForcedPasswordChangeTests`
- AC-11: `ChangePasswordTests`, unit, `-only-testing:Thi360Tests/ChangePasswordTests`
- AC-12: `ChangePasswordUITests/testSurvivesSizeClassChange`, giao diện trên simulator iPad, `-destination 'platform=iOS Simulator,name=iPad Air 11-inch (M4)' -only-testing:Thi360UITests/ChangePasswordUITests`
- Regression: toàn bộ `xcodebuild … -scheme Thi360Dev … test`.
- Risk tests: red-first cho AC-7, AC-8, AC-9, AC-10. Gửi lặp: mỗi form chỉ gửi một lần. Ranh giới mật khẩu 7, 8, 64, 65 ký tự.
- Test data: máy chủ giả; đồng hồ giả.
- Not tested: Universal Links do hệ điều hành xác minh trên tên miền thật; bấm liên kết từ ứng dụng thư thật.

**Design / references:** `docs/design/app-hoc-vien/I-Register.dc.html`, `I-Otp.dc.html`, `I-Forgot.dc.html`, `I-NewPassword.dc.html`, `I-Duo-Open-Account.dc.html`.

## IOS-4 · iOS: Tài khoản, Face ID / Touch ID và màn khóa, giao diện theo cấu hình admin

**Spec:** R-10 (ĐT 56–66, 93), R-11 (ĐT 67–70), R-12 (ĐT 72–75, 77, 78), R-2 (ĐT 8, 9), R-13 (ĐT 80 phần lối vào); D-16, D-17, D-18, D-20, D-21, D-28.

**Lane owner:** lead

**Domain shape:**

- `BiometricState`, `PromptMemory`, `LockPolicy` cùng hình với AND-4; dữ liệu bảo vệ là mục Keychain có `SecAccessControl` `.biometryCurrentSet` (tự vô hiệu khi bộ sinh trắc học đổi).
- `AppearanceRepository` cùng hình với AND-4, nguồn theo B1.4.

**Deliverables:**

- Mục Tài khoản như AND-4; iPad hiện trong vùng nội dung cạnh sidebar; iPhone Duo mở hiện danh sách ở nửa trái và chi tiết ở nửa phải.
- Hỏi bật, màn khóa, che nội dung khi vào nền, đăng nhập lại từ màn khóa, đăng xuất: như AND-4; nhãn "Face ID" hoặc "Touch ID" theo máy; `NSFaceIDUsageDescription` hai ngôn ngữ.
- Lấy và áp bảng màu, sáng/tối như AND-4.

**Owned paths:** `apps/ios/Thi360/Features/Account/**`, `apps/ios/Thi360/Features/Lock/**`, `apps/ios/Thi360/Core/Biometric/**`, `apps/ios/Thi360/Core/Appearance/**`, `apps/ios/Thi360/Resources/Account.xcstrings`, `apps/ios/Thi360/Resources/InfoPlist.xcstrings`, `apps/ios/Thi360Tests/Account/**`, `apps/ios/Thi360Tests/Lock/**`, `apps/ios/Thi360Tests/Appearance/**`, `apps/ios/Thi360UITests/Lock/**`, khóa `NSFaceIDUsageDescription` trong phần Info của target ở `apps/ios/project.yml`, một dòng trong `AppRouter.swift`, bước test trong `.github/workflows/ios.yml`.

**Out of scope:** sửa hồ sơ, đổi email, ảnh đại diện; route bảng màu (WEB-1); màn đổi mật khẩu (IOS-3, chỉ gắn vào).

**Acceptance:**

- AC-1: Tài khoản hiện đúng thông tin và đủ bốn trạng thái như ĐT 67, 68 — evidence: `AccountViewTests`; ảnh chụp ở iPhone 17, iPad, simulator iPhone Duo mở.
- AC-2: iPad regular hiện Tài khoản cạnh sidebar; iPhone regular hiện danh sách nửa trái kèm thanh tab và chi tiết nửa phải; compact là một cột — evidence: `AccountLayoutTests`.
- AC-3: đăng xuất có xác nhận gọi `mobile/logout`, xóa token và dữ liệu sinh trắc học, về Đăng nhập; mất mạng vẫn xóa; người dùng sau không thấy dữ liệu người trước; ngôn ngữ và sáng/tối giữ nguyên — evidence: `LogoutTests`.
- AC-4: máy có sinh trắc học đã đăng ký thì hỏi bật sau đăng nhập bằng mật khẩu, trừ khi câu trả lời gần nhất là "Để sau"; máy không có thì không hỏi và công tắc bị khóa kèm giải thích; trả lời "Để sau", đăng xuất rồi đăng nhập lại thì không bị hỏi — evidence: `BiometricPromptPolicyTests`.
- AC-5: bật chỉ thành công khi hệ điều hành xác nhận; tắt rồi bật lại phải xác nhận lại — evidence: `BiometricEnableTests`.
- AC-6: khi đã bật, mở nguội hoặc quay lại sau quá 5 phút hiện màn khóa ba nút; ảnh xem trước ở trình chuyển ứng dụng không lộ nội dung; dưới 5 phút không khóa; mở khóa đúng về đúng màn đang xem — evidence: `LockPolicyTests`, `LockViewTests`, `LockUITests/testLocksAfterBackground` (đăng ký và khớp sinh trắc học trên simulator bằng thông báo `com.apple.BiometricKit_Sim.*`).
- AC-7: sinh trắc học thất bại hoặc bị hủy thì vẫn khóa; "Nhập mật khẩu" mở Đăng nhập điền sẵn tên đăng nhập, quay lại được màn khóa; đăng nhập đúng thì dùng phiên mới, gọi `mobile/logout` cho phiên cũ (lỗi thì bỏ qua), xóa dữ liệu sinh trắc học cũ và hỏi bật lại theo quy tắc của AC-4 — evidence: `LockPasswordFallbackTests`.
- AC-8: "Đăng nhập bằng tài khoản khác" đăng xuất, tắt tính năng và xóa dữ liệu sinh trắc học, về Đăng nhập trống — evidence: `LockViewTests/testOtherAccount`.
- AC-9: bộ sinh trắc học của máy đổi thì lần mở khóa kế tiếp đòi mật khẩu, `BiometricState` chuyển `off` và công tắc trong Tài khoản hiện tắt cho tới khi người dùng bật lại — evidence: `BiometricInvalidationTests` (mục Keychain không đọc được sau khi đăng ký lại sinh trắc học trên simulator), `BiometricInvalidationStateTests`.
- AC-10: có mạng, mở khóa xong mà làm mới báo phiên đã chết thì về Đăng nhập kèm dòng hết hạn; mất mạng thì mở khóa bình thường — evidence: `LockExpiredSessionTests`.
- AC-11: liên kết đặt lại mật khẩu mở màn của IOS-3 khi app đang khóa; đặt lại thành công lúc đang khóa thì về Đăng nhập, tính năng tắt và dữ liệu sinh trắc học bị xóa — evidence: `LockBypassForResetLinkTests`.
- AC-12: với tệp hợp đồng bảng màu và một bảng màu khác, màu thương hiệu và màu trung tính trên màn trùng `Palette` ở cả hai chế độ — evidence: `PaletteApplicationTests`; ảnh chụp hai bảng màu.
- AC-13: route trả `version` khác bản đang lưu thì áp màu mới ở lần mở hoặc lần quay lại sau ít nhất 60 giây; sớm hơn thì không gọi mạng; route trả 304 thì không đổi gì — evidence: `AppearanceRefreshTests`.
- AC-14: chưa chọn Sáng/Tối thì theo `mode` và không theo hệ điều hành; đã chọn thì giữ, còn sau khi mở lại và sau đăng xuất — evidence: `ThemeModeTests`.
- AC-15: mất mạng dùng bảng màu đã lưu; lần đầu dùng bộ mặc định sáng; route trả 503 (kể cả vì `presetId` lạ), lỗi mạng hoặc JSON sai hình thì giữ bảng đang dùng — evidence: `AppearanceFallbackTests`.
- AC-16: nhãn ghi "Face ID" trên máy có Face ID và "Touch ID" trên máy có Touch ID — evidence: `LockViewTests/testLabelsFollowDevice`.

**Impact outside the task:**

- `SessionStore` và cổng chặn của IOS-2: thêm `locked` và thay phiên khi đăng nhập lại từ màn khóa → `TokenRefreshTests`, `LoginFlowUITests`, `StudentOnlyFlowTests` của IOS-2 xanh [owner: this task]
- Màn Đăng nhập của IOS-2 nhận tham số điền sẵn tên đăng nhập → `LoginViewTests` của IOS-2 xanh; ca mới ở `LockPasswordFallbackTests` [owner: this task]
- Màn Đổi mật khẩu và liên kết đặt lại của IOS-3 → `ChangePasswordTests`, `NewPasswordFromLinkTests` của IOS-3 xanh [owner: this task]
- Khung và `BrandPalette` của IOS-1 → `ShellLayoutTests` và ảnh chụp của IOS-1 với bảng màu mặc định không đổi [owner: this task]
- Route bảng màu và tệp hợp đồng của WEB-1 → `PaletteApplicationTests` đọc `contracts/mobile/appearance` [owner: this task]
- `project.yml` của IOS-1: thêm khóa `NSFaceIDUsageDescription` (chuỗi hai ngôn ngữ ở `InfoPlist.xcstrings`) → `xcodegen generate` và build hai scheme xanh; `BundleInfoTests` của IOS-1 xanh; `LockUITests` thấy được hộp thoại sinh trắc học [owner: this task]

**Test plan:**

- AC-1: `AccountViewTests`, unit và ảnh chụp, `cd apps/ios && xcodegen generate && xcodebuild -project Thi360.xcodeproj -scheme Thi360Dev -destination 'platform=iOS Simulator,name=iPhone 17' test -only-testing:Thi360Tests/AccountViewTests`
- AC-2: `AccountLayoutTests`, unit, `-only-testing:Thi360Tests/AccountLayoutTests`; chạy thêm trên simulator iPad
- AC-3: `LogoutTests`, unit, `-only-testing:Thi360Tests/LogoutTests`
- AC-4: `BiometricPromptPolicyTests`, unit, `-only-testing:Thi360Tests/BiometricPromptPolicyTests`
- AC-5: `BiometricEnableTests`, unit với lớp sinh trắc học giả, `-only-testing:Thi360Tests/BiometricEnableTests`
- AC-6: `LockPolicyTests`, `LockViewTests`, unit; `LockUITests/testLocksAfterBackground`, giao diện trên simulator, `-only-testing:Thi360UITests/LockUITests`
- AC-7: `LockPasswordFallbackTests`, unit, `-only-testing:Thi360Tests/LockPasswordFallbackTests`
- AC-8: `LockViewTests/testOtherAccount`, unit, `-only-testing:Thi360Tests/LockViewTests`
- AC-9: `BiometricInvalidationTests`, giao diện trên simulator, `-only-testing:Thi360UITests/BiometricInvalidationTests`; `BiometricInvalidationStateTests`, unit, `-only-testing:Thi360Tests/BiometricInvalidationStateTests`
- AC-10: `LockExpiredSessionTests`, unit, `-only-testing:Thi360Tests/LockExpiredSessionTests`
- AC-11: `LockBypassForResetLinkTests`, unit, `-only-testing:Thi360Tests/LockBypassForResetLinkTests`
- AC-12: `PaletteApplicationTests`, unit và ảnh chụp, `-only-testing:Thi360Tests/PaletteApplicationTests`
- AC-13: `AppearanceRefreshTests`, unit với đồng hồ giả, `-only-testing:Thi360Tests/AppearanceRefreshTests`
- AC-14: `ThemeModeTests`, unit, `-only-testing:Thi360Tests/ThemeModeTests`
- AC-15: `AppearanceFallbackTests`, unit, `-only-testing:Thi360Tests/AppearanceFallbackTests`
- AC-16: `LockViewTests/testLabelsFollowDevice`, lệnh của AC-8
- Regression: toàn bộ `xcodebuild … -scheme Thi360Dev … test`; các lớp test của IOS-2, IOS-3 nêu ở mục Ảnh hưởng.
- Risk tests: red-first cho AC-3, AC-6, AC-7, AC-9. Ranh giới của `LockPolicyTests`: 4 phút 59 giây, 5 phút, 5 phút 1 giây, mở nguội. Gửi lặp: bấm Đăng xuất hai lần chỉ gọi một lần.
- Test data: máy chủ giả; lớp sinh trắc học giả cho unit; simulator có sinh trắc học đăng ký cho test giao diện.
- Not tested: Face ID và Touch ID trên thiết bị thật; iPhone Duo thật.

**Design / references:** `docs/design/app-hoc-vien/I-Account.dc.html`, `I-Pad-Account.dc.html`, `I-Duo-Open-Account.dc.html`, `I-BioEnable.dc.html`, `I-BioUnlock.dc.html`; B1.3, B1.4.

## QA · Nghiệm thu đợt chạy

**Spec:** all

**Lane owner:** advisor

**Deliverables:**

- Báo cáo QA (`qa-report.md`); sổ việc sau merge, sổ test không ổn định và sổ quyết định đã đóng; `verify-evidence.mjs` và `audit-lanes.mjs` đạt; PR cuối `integration/app-hoc-vien` → `develop` sẵn sàng.
- `TASK.md` và `PROGRESS.md` cập nhật.
- Danh sách việc sau merge: cập nhật realm trên Keycloak của bản demo (client `thi360-mobile`, thời hạn offline 7 ngày và mốc tối đa 30 ngày); cấu hình nginx cho `/.well-known/*` và `/mobile/*`; đặt biến deep link kèm Apple Team ID; kiểm hệ điều hành mở app từ liên kết trong thư trên tên miền demo (ĐT 45); đăng nhập thật từ đầu tới cuối bằng bản demo của hai app trên máy chủ demo sau khi realm được cập nhật; kiểm sinh trắc học, máy gập và việc gỡ rồi cài lại trên thiết bị thật (các mục "Not tested" của AND-2, AND-4, IOS-2, IOS-4).

**Owned paths:** `TASK.md`, `PROGRESS.md`, `docs/acceptance/app-hoc-vien.md`, tệp test trong `apps/android/app/src/androidTest/**/smoke/**` và `apps/ios/Thi360UITests/Smoke/**`.

**Out of scope:** tính năng mới; triển khai; sửa realm trên máy chủ.

**Acceptance:**

- AC-1: mỗi tiêu chí của đặc tả (ĐT 1–94, trừ các số đã bỏ) có bằng chứng xanh trên SHA cuối của nhánh tích hợp, hoặc nằm trong danh sách "chưa kiểm được trong đợt chạy" kèm lý do — evidence: bảng nghiệm thu trong báo cáo QA.
- AC-2: mọi phép kiểm của dự án xanh trên SHA cuối — evidence: lệnh backend, web, OpenAPI, Android (build, test JVM, ảnh chụp, lint, máy ảo), iOS (build hai scheme, test) ở A5.
- AC-3: mọi dòng ảnh hưởng của mọi task xanh trên SHA cuối — evidence: các lệnh hồi quy của từng task.
- AC-4: một lượt chạy thật trên mỗi nền tảng với backend của nhánh tích hợp: đăng ký có OTP (đọc thư ở Mailpit), đăng nhập, xem Tài khoản, đổi mật khẩu, quên mật khẩu và đặt lại bằng mã lấy từ thư, đăng xuất; tài khoản quản trị bị chặn — evidence: `SmokeAgainstBackendTest` (Android, máy ảo) và `SmokeAgainstBackendUITests` (iOS, simulator nối backend trên máy chính qua Tailscale).
- AC-5: thời gian từ mở app tới màn đầu dùng được không quá 2 giây (trung vị 5 lần) trên Pixel 7 API 34 và iPhone 17 — evidence: số đo bằng công cụ đo khởi động của từng nền tảng.
- AC-6: vùng bấm, nhãn trình đọc màn hình và cỡ chữ 130% và 200% đạt trên các màn Đăng nhập, Đăng ký, Khóa, Trang chủ, Tài khoản — evidence: kiểm tự động của bộ test giao diện từng nền tảng và ảnh chụp ở hai mức cỡ chữ.
- AC-7: bản `demo` của mỗi app gọi tới `https://thi360.zone17.click` và nhận được một phản hồi HTTP (mã nào cũng được, vì máy chủ demo chưa có endpoint mobile trong đợt chạy) — evidence: `DemoHostReachabilityTest` (Android), `DemoHostReachabilityUITests` (iOS).

**Impact outside the task:**

- none: chỉ thêm test chạy thật, tài liệu nghiệm thu và cập nhật `TASK.md`, `PROGRESS.md`; đã tìm các nơi tham chiếu hai tệp đó bằng `grep -rn "PROGRESS.md\|TASK.md" AGENTS.md RULES.md docs/` và không có mã nào đọc chúng.

**Test plan:**

- AC-1: rà đặc tả theo từng tiêu chí; `verify-evidence.mjs` trên gói QA
- AC-2: toàn bộ lệnh ở A5 trên SHA cuối
- AC-3: lệnh hồi quy của từng task
- AC-4: `SmokeAgainstBackendTest`, trên máy ảo, `cd apps/android && ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=vn.unicom.thi360.smoke.SmokeAgainstBackendTest`; `SmokeAgainstBackendUITests`, trên simulator, `xcodebuild … -scheme Thi360Dev … test -only-testing:Thi360UITests/SmokeAgainstBackendUITests`
- AC-5: đo khởi động 5 lần mỗi nền tảng, ghi trung vị
- AC-6: kiểm truy cập tự động trong bộ test giao diện và ảnh chụp ở cỡ chữ 130%, 200%
- AC-7: `DemoHostReachabilityTest`, trên máy ảo với bản demo, `cd apps/android && ./gradlew :app:connectedDemoDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=vn.unicom.thi360.smoke.DemoHostReachabilityTest`; `DemoHostReachabilityUITests`, trên simulator, `xcodebuild … -scheme Thi360Demo … test -only-testing:Thi360UITests/DemoHostReachabilityUITests`
- Not tested: hệ điều hành xác minh deep link trên tên miền thật; thiết bị thật; phiên sống thật nhiều ngày; đăng nhập thật trên máy chủ demo (chưa có endpoint mobile ở đó trong đợt chạy).

# Part E: Risks and escalation triggers

| Risk | Sign | Action (lane Advisor/Lead or Orchestrator) |
| --- | --- | --- |
| `devin-mac` ngủ giữa chừng hoặc không thức | lệnh trên Mac treo; `orca status` báo không nối được | Lane iOS dừng, đặt `blocked`; Orchestrator đánh thức theo `hosts-devin-mac.md`; bước đang chạy làm lại từ đầu |
| `devin-mac` hết dung lượng (còn 32 GB lúc lập kế hoạch) | build hoặc tải simulator báo hết chỗ | Lane xóa DerivedData và simulator không dùng; nếu vẫn thiếu thì báo Orchestrator hỏi người yêu cầu gỡ bản Xcode cũ |
| Ổ C: của máy chính đầy | Gradle hoặc máy ảo báo lỗi ghi | Đặt `GRADLE_USER_HOME`, `ANDROID_AVD_HOME` sang ổ D: (A5); dừng nếu còn dưới 5 GB |
| Máy ảo Android không chạy được trên máy chính | `connectedDevDebugAndroidTest` không khởi động | Chạy các test đó ở job máy ảo của `android.yml`; ghi rõ nơi chạy trong bằng chứng |
| Client sinh từ OpenAPI không build được sau khi BE-1 thêm endpoint | bước sinh mã hoặc biên dịch đỏ với `contracts/mobile/openapi-mobile.json` mới | BE-1 chỉnh bản lọc (đã biết: thao tác có tham số cookie làm Kotlin lỗi); nếu vẫn đỏ thì lane báo Orchestrator trước khi viết tay, vì đó là đổi B1.6 |
| Keycloak không ép mốc tối đa của phiên offline tại lúc làm mới (thử nghiệm Q-4 chưa đo mốc này) | `MobileSessionLifetimeIT#mobileEndsAtMaxLifetime` đỏ ở vế Keycloak | Lớp thứ hai đã có trong thiết kế: hạn của bản ghi phiên Redis (BE-1 AC-17). Lane báo Orchestrator kết quả đo và giữ nguyên tiêu chí; không nới ĐT 94 |
| Offline token không bị xóa khi ngừng hoạt động tài khoản | `MobileSessionRevocationIT` đỏ ở vế Keycloak | BE-1 thu hồi bằng API quản trị của Keycloak cho phiên offline; không nới tiêu chí |
| Hợp đồng API đổi sau khi lane app đã bắt đầu | `ContractFixturesTest` đỏ sau khi rebase | BE-1 báo trước qua Orchestrator; lane app cập nhật theo tệp hợp đồng |
| omp tự chuyển sang model dự phòng | log phiên có mục `"role":"fallback"` | Dừng lượt đó, không tính kết quả, chạy lại với model đúng |
| Runner macOS của GitHub tốn nhiều phút | thời gian `ios.yml` vượt 20 phút mỗi lần | IOS-1 thu hẹp bộ lọc đường dẫn và tách job; báo số phút trong báo cáo |
| Chưa có Apple Team ID | biến `THI360_IOS_APP_ID` trống | Tệp xác minh iOS để trống phần iOS; việc kiểm thật chuyển vào danh sách sau merge |

# Review log

| Round | Date | Reviewer / Panel | Blocking | Should fix | Minor | Rejected (with reason) | Report |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 (toàn bộ) | 2026-11-25 | omp `devin/swe-2-1` mức max | 2, đã sửa: P1-1 (thêm phụ thuộc AND-3 → AND-4, IOS-3 → IOS-4, thành 5 sóng), P1-4 (route trả 503 cho `presetId` lạ) | 7, đã sửa: P1-2, P1-3, P1-5 (có thử nghiệm sinh client), P1-6, P1-7, P1-10, P1-11 | 7, đã sửa: P1-8, P1-9, P1-12..P1-16 | Tác giả không bác phát hiện nào. Người rà soát tự bác 3 ứng viên (PF-1..PF-3). NH-1, NH-2 hỏi người duyệt ở G2 | thư mục phiên `reviews/plan-r1-report.md` |
| 2 (phần thay đổi) | 2026-11-25 | omp `devin/swe-2-1` mức max | 1, đã sửa: P2-1 (IOS-2, IOS-3 được sửa phần của mình trong `project.yml`) | 1, đã sửa: P2-2 | 4, đã sửa: P2-3..P2-6 | Tác giả không bác phát hiện nào. Người rà soát tự bác 3 ứng viên (PF2-1..PF2-3). 16/16 phát hiện vòng 1 được xác nhận đã xử lý. Người duyệt đã trả lời NH-1 (D-34), NH-2 (D-35: trần 30 ngày, thành thay đổi yêu cầu ở bản 3 này) và B1.4 (D-36) | thư mục phiên `reviews/plan-r2-report.md` |
| 3 (vòng cuối: phần thay đổi và thay đổi yêu cầu D-35, D-36) | 2026-11-25 | omp `devin/swe-2-1` mức max | 0 | 3, đã sửa: P3-1 (hai lớp ép mốc 30 ngày, thêm AC-17 và hàng rủi ro), P3-2 (IOS-4 được thêm khóa Face ID vào `project.yml`), P3-3 (bổ sung danh sách việc sau merge) | 3, đã sửa: P3-4 (AND-1 ghi vân tay vào `.env.example`), P3-5 (mục 3 của đặc tả), P3-6 (tiêu đề) | Tác giả không bác phát hiện nào. Người rà soát tự bác 4 ứng viên (PF3-1..PF3-4). 6/6 phát hiện vòng 2 được xác nhận đã xử lý. Các sửa sau vòng 3 là sửa nhỏ, chưa qua rà soát độc lập vì đã hết 3 vòng | thư mục phiên `reviews/plan-r3-report.md` |
