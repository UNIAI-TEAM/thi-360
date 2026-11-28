# Đặc tả: App học viên native (Android, iOS) — khung ứng dụng và xác thực

> Trạng thái: đã duyệt · Bản: 6 (bản 5 duyệt ở G1 ngày 2026-11-25; bản 6 thêm thay đổi yêu cầu D-35, D-36, được M.Trường duyệt cùng kế hoạch ở G2 ngày 2026-11-25) · Người viết: agent (Fable 5.2) · Người rà soát: omp `devin/swe-2-1` mức max
> Theo dõi: UNI-2785

## 1. Tóm tắt

Học viên có hai ứng dụng native (Android, iOS) để vào cổng thi Thi 360. Đợt này app có khung điều hướng bốn mục, toàn bộ luồng xác thực (đăng nhập, đăng ký có OTP, quên và đặt lại mật khẩu, bắt đổi mật khẩu, mở khóa sinh trắc học) và mục Tài khoản. Bố cục tự đổi theo điện thoại, tablet và màn hình gập; màu và chế độ sáng/tối theo cấu hình Tùy chỉnh giao diện của admin. Backend thêm các API đăng nhập cho mobile trả token trong thân phản hồi.

## 2. Nguồn và thử nghiệm

| Nguồn hoặc thử nghiệm | Bản / ngày / lệnh | Bản lưu hoặc kết quả đo |
| --- | --- | --- |
| Yêu cầu và các quyết định của người yêu cầu (M.Trường) | 2026-11-25, trong phiên làm việc | Mục 10; bản đầy đủ ở thư mục phiên `D:/.Vietants_Project/.thi360-dev/mobile-shell/decisions.md` |
| Bản thiết kế 28 màn, đã duyệt | 2026-11-25, canvas `https://claude.ai/artifact/3Rb8TDT9QUgb3SWcHF2MnD` (riêng tư) | `docs/design/app-hoc-vien/` (28 tệp `*.dc.html`, `m3.css`, `ios.css`, `README.md`) |
| `PRD.md` mục 2, 3 (nhóm Mobile, PBI 19–23) | bản trên `develop` `31856a31` | — |
| Luồng xác thực của web học viên | `apps/web-student/src/messages/vi.json` (khóa `login`, `account.register`, `account.forgotPassword`, `account.resetPassword`, `changePassword`) | Câu chữ trong app lấy theo các khóa này |
| Thông số iPhone Duo | Apple Tech Specs `https://support.apple.com/en-in/148588`; Tech Talk "Prepare your app for iPhone Duo" `https://developer.apple.com/videos/play/tech-talks/111461/`, đọc 2026-11-25 | Màn trong 7,6" 1878×2670 px, màn ngoài 5,4" 1398×2034 px. Màn trong báo bề rộng và chiều cao đều là regular; màn ngoài bề rộng compact. Kích thước theo điểm Apple không công bố |
| Thử nghiệm Keycloak: phiên mobile 7 ngày bên cạnh phiên web 8 giờ | 2026-11-25, Keycloak 26.4 tạm trên máy chính, thời gian rút ngắn (8 giờ → 60 giây, 30 ngày → 400 giây, 7 ngày → 300 giây); `python spike.py http://localhost:18089 spike.log` | Cả hai cách ở Q-4 đều chạy. Với offline token cho client mobile: web không dùng thì hết, web làm mới đều vẫn chết ở mốc tối đa (giây 404), mobile làm mới mỗi 100 giây vẫn sống ở giây 602, mobile không dùng 440 giây thì hết; đăng xuất thu hồi ngay; `sid` giữ nguyên qua các lần làm mới; Keycloak trả `refresh_expires_in = 0` cho offline token. Bản ghi ở thư mục phiên `spikes/q4-keycloak-session/RESULT.md` |
| Bản Xcode có simulator iPhone Duo | Ghi chú phát hành Xcode 27.1 `https://developer.apple.com/go/?id=xcode-27_1-sdk-rn`; MacRumors 2026-09-18 `https://www.macrumors.com/2026/09/18/apple-releases-xcode-27-1-beta-iphone-duo-support/`, đọc 2026-11-25 | Simulator và SDK iPhone Duo có từ Xcode 27.1, cần máy Apple silicon chạy macOS 26.6 trở lên |
| Đo trên máy build iOS `devin-mac` | 2026-11-25: `xcodebuild -version`; `xcrun simctl list devicetypes \| grep -i -E "duo\|fold"`; `xcrun simctl list runtimes`; `sw_vers`; `df -h /`; `sudo -n true` | Xcode đang dùng 26.6 (17F113); có thêm Xcode 27.0 RC (27A266a) và runtime iOS 27.0 (24A434); cả hai chưa có simulator iPhone Duo. macOS 26.6.2, có quyền quản trị, đĩa còn trống 23 GB trên 123 GB (Q-3) |
| Kích thước màn iPhone Duo | 2026-11-25, simulator `iPhone Duo` của Xcode 27.1 trên `devin-mac`: `capabilities.plist` của loại máy và ảnh chụp simulator | Màn ngoài 1398×2034 px, tỉ lệ 3 → 466×678 điểm. Màn trong 2007×2853 px, tỉ lệ 3 → 669×951 điểm (951×669 khi cạnh dài nằm ngang) |
| Tính chất máy build iOS | Ghi chú 2026-11-25 ở thư mục phiên `hosts-devin-mac.md` | Máy là phiên Devin Cloud, tự ngủ khi rảnh; khi ngủ mọi tiến trình mất, ổ đĩa giữ nguyên. Chỉ được giữ thức trong lúc có việc đang chạy |

## 3. Phạm vi

- **Trong phạm vi:**
  - Hai dự án native trong repo, ở `apps/android` (Kotlin, Jetpack Compose, Android 8.0 / API 26 trở lên) và `apps/ios` (Swift, SwiftUI, iOS 17 trở lên). Mỗi app có hai cấu hình môi trường: `dev` (backend chạy máy lập trình) và `demo` (`https://thi360.zone17.click`).
  - Khung điều hướng bốn mục, đáp ứng điện thoại, tablet, màn hình gập Android, iPad và iPhone Duo.
  - Xác thực: đăng nhập, đăng xuất, đăng ký hai bước có OTP email, quên mật khẩu, đặt lại mật khẩu qua liên kết trong thư (deep link), bắt đổi mật khẩu, đổi mật khẩu, tự làm mới phiên, mở khóa bằng sinh trắc học.
  - Mục Tài khoản: thông tin tài khoản (chỉ đọc), đổi mật khẩu, bật/tắt sinh trắc học, ngôn ngữ, sáng/tối, đăng xuất.
  - Backend: API xác thực cho mobile; phiên mobile hết sau 7 ngày không dùng, tối đa 30 ngày kể từ lúc đăng nhập.
  - Web học viên phục vụ hai tệp xác minh deep link.
  - Màu và chế độ mặc định theo `GET /api/v1/appearance`; giao diện song ngữ Việt, Anh.
- **Ngoài phạm vi:**
  - Nội dung ba mục Trang chủ, Bài thi của tôi, Lịch sử (đợt này là màn giữ chỗ); danh mục, mua bài, thanh toán, làm bài, kết quả, AI.
  - Đăng nhập Google (UNI-2554); sửa hồ sơ, đổi email, ảnh đại diện trong app.
  - Phát hành lên Google Play, App Store, TestFlight; thông báo đẩy; chế độ ngoại tuyến; ghim chứng chỉ; chặn máy đã root/jailbreak; thu thập phân tích và báo lỗi tự động.
  - Deep link cho các luồng khác (ví dụ quay về sau thanh toán).
- **Không được đổi:**
  - Luồng đăng nhập bằng cookie của hai web và hợp đồng của các endpoint hiện có dưới `/api/v1/auth/**`.
  - Thời hạn phiên của web (8 giờ không dùng).
  - Cách web áp cấu hình giao diện; nội dung và định dạng liên kết trong thư đặt lại mật khẩu.

## 4. Hiện trạng

- **Kiến trúc ghi mobile là React Native, chưa có mã.** `ARCHITECTURE.md:44` ghi "Mobile (sau) | React Native (Expo)"; `TASK.md:318` ghi đợt 6 Mobile chưa tạo issue. Repo chưa có thư mục app mobile.
- **Đăng nhập chỉ trả token qua cookie.** `POST /api/v1/auth/login` và `/auth/refresh` đặt cookie HttpOnly và trả `redirectTo`, `mustChangePassword`; `/auth/register/verify` đặt cookie và chỉ trả `redirectTo` (`backend/src/main/java/vn/unicom/thi360/identity/api/AuthController.java:58-147`; `AuthService.LoginResult` tại `identity/application/AuthService.java:829`). Không endpoint nào trả token trong thân phản hồi.
- **Backend đã nhận Bearer.** Chuỗi bảo mật xác minh JWT của hai nguồn phát hành (`shared/security/SecurityConfig.java:129-131`); request có `Authorization: Bearer` được miễn kiểm XSRF (`shared/security/CsrfFilter.java:51-53,82-90`). Phiên vẫn phải có trong Redis: đăng nhập tạo phiên bằng `sessionStore.createSessionIfNotRevoked` (`AuthService.java:309`). Mỗi tài khoản giữ tối đa 5 phiên, phiên thứ sáu đẩy phiên cũ nhất ra; bản ghi phiên ở Redis có hạn 30 ngày (`shared/security/SecurityProperties.java:82-85`).
- **Các kiểm tra khi đăng nhập:** chặn đăng nhập sai (`AuthService.java:213,246,268`), từ chối tài khoản `INACTIVE` bằng 403 (`AuthService.java:263-264`), ghi audit `LOGIN` (`AuthService.java:336`). Tài khoản đã xóa mềm không được tìm thấy nên nhận 401 `INVALID_CREDENTIALS` như sai mật khẩu (`identity/infrastructure/UserAccountRepository.java:14-28`).
- **Giới hạn tần suất theo IP:** đăng nhập 20 lần mỗi phút, gắn với đúng đường dẫn `/api/v1/auth/login` (`shared/security/RateLimitFilter.java:44,347`; `SecurityProperties.java:18-19`); nhóm đăng ký (`/auth/register`, `/resend`, `/verify`) 30 lần mỗi giờ và 100 lần mỗi ngày (`RateLimitFilter.java:381-391`). `/auth/refresh` và `/auth/logout` không có giới hạn riêng.
- **Token lấy từ Keycloak bằng password grant** qua client `thi360-api` (`identity/infrastructure/KeycloakIdentityClient.java:408-412`). Realm chỉ có một client ứng dụng là `thi360-api`, dùng cho mọi đăng nhập (`infra/keycloak/thi-360-realm.json:18`). Realm đặt phiên hết sau 8 giờ không dùng, tối đa 30 ngày (`thi-360-realm.json:13-14`), và không thu hồi refresh token cũ khi làm mới.
- **Vai trò vào cổng:** mỗi tài khoản có `access` là `MANAGE` hoặc `EXAM` (`shared/security/Access.java`). Web đưa `MANAGE` sang web admin; `PLATFORM_ADMIN` thuộc realm `master` (`ARCHITECTURE.md` mục 5.1, 6).
- **Đăng ký hai bước, quên và đặt lại mật khẩu đã có ở backend:** `/auth/register`, `/auth/register/resend`, `/auth/register/verify`, `/auth/password/forgot`, `/auth/password/reset` (`AuthController.java:74-175`). Phản hồi của đăng ký và gửi lại mã có `resendAfterSeconds` (`identity/api/RegisterStartResponse.java:17`); request đăng ký bắt buộc có `termsVersion`, web gửi `"2026-11"` (`identity/api/RegisterRequest.java:38-39`; `apps/web-student/src/features/account/register-view.tsx:48`). Liên kết trong thư đặt lại là `{THI360_WEB_URL}/reset-password?token=…` (`identity/application/PasswordResetService.java:74`), hiệu lực 1 giờ (`application.yml:155`). Yêu cầu quên mật khẩu được xử lý sau khi phản hồi 202 đã gửi, mỗi tài khoản 1 lần mỗi 60 giây và 5 lần trong 24 giờ (`application.yml:154-157`); phản hồi không cho biết thời gian chờ, web tự đếm 60 giây (`apps/web-student/src/features/account/forgot-password-view.tsx:25-26`).
- **Ngôn ngữ của thư:** thư OTP đăng ký theo cookie `thi360_locale` của request (`AuthController.java:69,90,110`). Thư đặt lại mật khẩu theo ngôn ngữ ưu tiên đã lưu của tài khoản (`PasswordResetService.java:177`); đăng nhập thành công ghi ngôn ngữ của cookie vào đó (`AuthService.java:197-198`).
- **Đổi mật khẩu** `POST /api/v1/auth/change-password` kiểm mật khẩu cũ rồi đặt mật khẩu mới (`AuthService.java:693-727`). Thông tin tài khoản: `GET /api/v1/me` (`AuthController.java:177`) và `GET /api/v1/me/profile` trả `username`, `fullName`, `email`, `emailVerified`, `phone` (`identity/api/MeProfileController.java:19,31`).
- **Cấu hình giao diện:** `GET /api/v1/appearance` công khai, có `ETag`, cache 60 giây, trả `palette.presetId` (9 bộ cố định hoặc `custom` kèm màu), `mode` (`light`/`dark`), `version` (`shared/appearance/api/AppearanceController.java:26-27,67-79`, `AppearanceResponse.java`, `AppearancePaletteDto.java`). Web tự suy ra token màu từ cấu hình bằng `deriveTokens` (`packages/ui/src/theme/scale.ts:476`); backend không trả token đã suy ra.
- **CI:** `.github/workflows/web.yml:6,9` chạy cho mọi thay đổi dưới `apps/**`; chưa có workflow cho Android, iOS.

## 5. Thuật ngữ

| Thuật ngữ | Nghĩa ở đây |
| --- | --- |
| App | Ứng dụng học viên trên Android hoặc iOS; yêu cầu áp cho cả hai trừ khi ghi rõ một nền tảng |
| Học viên | Tài khoản realm `thi-360` có `access = EXAM` |
| Vai trò khác | Tài khoản có `access = MANAGE`, hoặc tài khoản `PLATFORM_ADMIN` của realm `master` |
| Phiên mobile | Phiên đăng nhập tạo bởi API mobile, dùng cặp access token và refresh token |
| Bề rộng hẹp | Android: bề rộng cửa sổ dưới 600dp. iOS: horizontal size class là compact |
| Bề rộng rộng | Android: từ 600dp. iOS: horizontal size class là regular |
| Hai cột | Bố cục danh sách bên trái, chi tiết bên phải. Android: bề rộng từ 840dp. iOS: bề rộng regular |
| Khóa app | Trạng thái app che nội dung và đòi sinh trắc học hoặc mật khẩu trước khi dùng tiếp |
| Deep link | Liên kết `https` trên tên miền web học viên mà hệ điều hành mở thẳng vào app (Android App Links, iOS Universal Links) |
| Vai màu thương hiệu | Nền nút chính, chữ trên nút chính, chữ màu chính, nền mục đang chọn, màu nhấn, chữ trên nút nhấn |

## 6. Yêu cầu

### R-1 Hai dự án native chạy được

Repo có một dự án Android và một dự án iOS, build được bằng một lệnh mỗi bên, chạy trên máy ảo với cấu hình `dev` và `demo`.

- Nguồn: D-2, D-11, D-10.
- Áp dụng cho: người phát triển, CI.
- Tiêu chí chấp nhận:
  - **AC-1** Cho một bản checkout sạch và bộ công cụ ghi trong README của từng app, khi chạy lệnh build cấu hình `dev`, thì có tệp cài đặt cho máy ảo Android và bản chạy được trên iOS Simulator, không lỗi, không cảnh báo bị coi là lỗi.
  - **AC-2** Cho app build cấu hình `demo`, khi mở trên máy ảo có mạng, thì màn Đăng nhập gọi được `https://thi360.zone17.click`.
  - **AC-3** Cho app đã cài, khi xem thông tin ứng dụng, thì tên hiển thị là "Thi 360" và định danh ứng dụng là `vn.unicom.thi360` ở cả hai nền tảng; Android nhận từ API 26, iOS nhận từ 17.0.
  - **AC-4** *(Từ chối)* Cho bản build `demo`, khi app gọi tới một địa chỉ `http://` không mã hóa, thì kết nối bị chặn. Chỉ bản `dev` được gọi `http://` tới máy lập trình.
  - **AC-5** Cho một pull request đổi mã của một app, khi CI chạy, thì bước build và test của app đó chạy và chặn merge khi đỏ; cả hai chạy trên GitHub Actions, iOS dùng runner macOS (D-25); pull request chỉ đổi tệp dưới `apps/android` hoặc `apps/ios` thì workflow web không chạy.

### R-2 Khung điều hướng đáp ứng

Sau khi đăng nhập, app có bốn mục: Trang chủ, Bài thi của tôi, Lịch sử, Tài khoản. Kiểu điều hướng và số cột đổi theo bề rộng khả dụng, không theo tên máy.

- Nguồn: D-7, D-8, D-9, D-17; thiết kế `A-Home`, `A-Account`, `A-Fold-Account`, `A-Tablet-Home`, `I-Home`, `I-Account`, `I-Pad-Account`, `I-Duo-Open-Account`.
- Áp dụng cho: học viên đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-6** Cho Android bề rộng hẹp hoặc iPhone bề rộng compact, khi vào app, thì bốn mục nằm ở thanh điều hướng dưới và nội dung một cột.
  - **AC-7** Cho Android bề rộng từ 600dp, khi vào app, thì bốn mục nằm ở thanh điều hướng bên trái; từ 840dp mục Tài khoản hiện hai cột (danh sách, chi tiết).
  - **AC-8** Cho iPad bề rộng regular, khi vào app, thì bốn mục nằm ở sidebar bên trái và nội dung ở bên phải.
  - **AC-9** Cho iPhone Duo đang mở (bề rộng regular), khi vào mục Tài khoản, thì nửa trái hiện danh sách Tài khoản kèm thanh tab, nửa phải hiện chi tiết mục đang chọn; khi gập máy thì về bố cục một cột của AC-6.
  - **AC-10** Cho ba mục Trang chủ, Bài thi của tôi, Lịch sử, khi mở, thì hiện màn giữ chỗ có tiêu đề mục và dòng "Chưa có nội dung", không gọi API nghiệp vụ.
  - **AC-11** Cho một màn có dữ liệu đang nhập dở (ví dụ form Đổi mật khẩu), khi xoay máy, gập/mở máy hoặc đổi kích thước cửa sổ, thì vẫn ở đúng màn đó, dữ liệu đã nhập và mục đang chọn giữ nguyên.
  - **AC-12** Cho điện thoại (Android bề rộng nhỏ nhất dưới 600dp; iPhone, kể cả màn ngoài của iPhone Duo), khi xoay ngang, thì giao diện giữ chiều dọc. Tablet, iPad, màn hình gập đang mở và màn trong của iPhone Duo theo cả hai chiều.
  - **AC-13** Cho tablet hoặc iPad chạy app ở chế độ chia đôi màn hình với bề rộng hẹp, khi thu cửa sổ, thì bố cục chuyển về AC-6 và mọi chức năng vẫn dùng được.

### R-3 API xác thực cho mobile

Backend có nhóm endpoint `/api/v1/auth/mobile/**` trả token trong thân phản hồi và tạo phiên mobile. Các kiểm tra của đăng nhập web giữ nguyên.

- Nguồn: D-4, D-6, D-15.
- Áp dụng cho: mọi client gọi API mobile; không cần đăng nhập trước.
- Hợp đồng:

  | Endpoint | Thân gửi | Thành công |
  | --- | --- | --- |
  | `POST /api/v1/auth/mobile/login` | `identifier`, `password` | 200: `accessToken`, `refreshToken`, `accessExpiresIn`, `refreshExpiresIn` (giây), `mustChangePassword` |
  | `POST /api/v1/auth/mobile/refresh` | `refreshToken` | 200: cùng dạng với đăng nhập |
  | `POST /api/v1/auth/mobile/logout` | `refreshToken` | 204 |
  | `POST /api/v1/auth/mobile/register/verify` | `registrationId`, `code` | 201: cùng dạng với đăng nhập |

  - **Ngôn ngữ:** `mobile/login`, `/auth/register` và `/auth/register/resend` nhận ngôn ngữ từ header `Accept-Language` (`vi` hoặc `en`) khi request không có cookie `thi360_locale`; có cookie thì cookie thắng như hiện nay. `mobile/login` ghi ngôn ngữ đó làm ngôn ngữ ưu tiên của tài khoản, như đăng nhập web. `/auth/password/forgot` không đổi.
  - **Giới hạn tần suất theo IP:** `mobile/login` dùng chung chính sách và bộ đếm với `/auth/login`; `mobile/register/verify` thuộc nhóm đăng ký như `/auth/register/verify`; `mobile/refresh` và `mobile/logout` như `/auth/refresh`, `/auth/logout` (không có giới hạn riêng).
  - **Refresh token:** mỗi lần làm mới trả một refresh token mới; token trước đó vẫn dùng được tới khi phiên kết thúc, như realm hiện nay (D-24).

- Tiêu chí chấp nhận:
  - **AC-14** Cho một học viên đang hoạt động, khi gọi `mobile/login` với đúng tên đăng nhập hoặc email và mật khẩu, thì nhận 200 kèm bốn trường token; gọi `GET /api/v1/me` với `Authorization: Bearer <accessToken>` trả 200 đúng tài khoản; phản hồi không đặt cookie nào.
  - **AC-15** *(Từ chối)* Cho mật khẩu sai, khi gọi `mobile/login`, thì nhận 401 `INVALID_CREDENTIALS`; sai quá số lần cho phép thì nhận 429 `LOGIN_THROTTLED` như web; quá giới hạn theo IP thì nhận 429 `RATE_LIMITED`. Bộ đếm sai dùng chung với đăng nhập web.
  - **AC-16** *(Từ chối)* Cho tài khoản `INACTIVE`, khi gọi `mobile/login` với đúng mật khẩu, thì nhận 403 `ACCOUNT_INACTIVE`, không tạo phiên. Tài khoản đã xóa mềm nhận 401 `INVALID_CREDENTIALS` như web, không phân biệt với sai mật khẩu.
  - **AC-17** *(Từ chối)* Cho tài khoản thuộc vai trò khác, khi gọi `mobile/login` với đúng mật khẩu, thì nhận 403 `APP_STUDENT_ONLY`, không trả token, không để lại phiên ở Redis và Keycloak.
  - **AC-18** Cho một refresh token còn hạn, khi gọi `mobile/refresh`, thì nhận 200 kèm access token mới gọi được `GET /api/v1/me`; hai lời gọi gần như đồng thời với cùng một refresh token đều nhận 200. Gọi bằng refresh token sai, hết hạn hoặc thuộc phiên đã thu hồi thì nhận 401 và không tạo phiên.
  - **AC-19** *(Từ chối)* Cho một phiên mobile của tài khoản vừa được gán vai trò quản trị, khi gọi `mobile/refresh`, thì nhận 403 `APP_STUDENT_ONLY` và phiên bị thu hồi.
  - **AC-20** Cho một phiên mobile, khi gọi `mobile/logout` với refresh token của phiên, thì nhận 204; access token của phiên đó gọi `GET /api/v1/me` nhận 401; gọi lại `mobile/logout` vẫn nhận 204.
  - **AC-21** Cho một phiên đăng ký đang chờ và đúng mã OTP, khi gọi `mobile/register/verify`, thì nhận 201 kèm token, tài khoản học viên được tạo với email đã xác minh; mã sai hoặc hết hạn trả lỗi như endpoint web (`EMAIL_CODE_INVALID` kèm `remainingAttempts`, `EMAIL_CODE_EXPIRED`).
  - **AC-22** Cho đăng nhập mobile thành công, khi xem nhật ký thao tác, thì có sự kiện `LOGIN` của đúng tài khoản, phân biệt được với đăng nhập web; nhật ký và log không chứa mật khẩu, token.
  - **AC-87** Cho `mobile/login` thành công kèm `Accept-Language: en`, khi đọc trường `preferred_locale` của tài khoản trong cơ sở dữ liệu, thì giá trị là `en`; không có header và không có cookie thì giá trị đang lưu giữ nguyên.
  - **AC-88** *(Giới hạn)* Cho một IP gọi `mobile/register/verify` quá giới hạn của nhóm đăng ký, khi gọi thêm, thì nhận 429 `RATE_LIMITED`; các lần gọi `mobile/register/verify` và `/auth/register/verify` cộng chung một bộ đếm.
  - AC-95, AC-96, AC-97: đã bỏ theo D-28 (không còn endpoint kiểm mật khẩu riêng cho màn khóa). Số không dùng lại.
  - **AC-23** Cho bộ test hiện có của đăng nhập web, khi chạy sau thay đổi, thì vẫn xanh và hợp đồng các endpoint hiện có trong OpenAPI không đổi; các endpoint mới có trong OpenAPI.

### R-4 Đăng nhập trong app

Người chưa đăng nhập thấy màn Đăng nhập; đăng nhập đúng thì vào khung app.

- Nguồn: D-5; thiết kế `Main` (Android), `I-Login`, `A-Tablet-Login`, `I-Pad-Login`, `I-Duo-Cover-Login`, `I-Duo-Open-Login`.
- Áp dụng cho: người chưa đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-24** Cho app chưa có phiên, khi mở, thì hiện màn Đăng nhập với ô "Tên đăng nhập hoặc email", ô "Mật khẩu" có nút hiện/ẩn, liên kết "Quên mật khẩu?", nút "Đăng nhập", liên kết "Đăng ký" và nút đổi ngôn ngữ.
  - **AC-25** Cho thông tin đúng của một học viên không bị bắt đổi mật khẩu, khi bấm Đăng nhập, thì vào mục Trang chủ; mở lại app sau khi tắt hẳn thì vẫn đăng nhập.
  - **AC-26** *(Từ chối)* Cho ô để trống, khi bấm Đăng nhập, thì hiện lỗi dưới từng ô ("Vui lòng nhập tên đăng nhập hoặc email.", "Vui lòng nhập mật khẩu.") và không gọi API.
  - **AC-27** *(Từ chối)* Cho API trả `INVALID_CREDENTIALS`, `ACCOUNT_INACTIVE`, `LOGIN_THROTTLED` hoặc `RATE_LIMITED`, khi nhận phản hồi, thì hiện thông báo tương ứng theo câu chữ khóa `login.errors` của web, giữ nguyên tên đăng nhập đã nhập, xóa mật khẩu.
  - **AC-28** Cho đang gửi yêu cầu đăng nhập, khi chờ phản hồi, thì nút Đăng nhập ở trạng thái đang xử lý và không gửi được lần hai.

### R-5 Chỉ học viên vào app

- Nguồn: D-6; thiết kế `A-RoleNotice`, `I-RoleNotice`.
- Áp dụng cho: tài khoản thuộc vai trò khác.
- Tiêu chí chấp nhận:
  - **AC-29** Cho tài khoản thuộc vai trò khác, khi đăng nhập đúng mật khẩu trong app, thì hiện màn "Ứng dụng này dành cho học viên" có tên đăng nhập vừa dùng và nút "Đăng nhập bằng tài khoản khác"; app không lưu token nào.
  - **AC-30** Cho học viên đang dùng app mà tài khoản vừa được gán vai trò quản trị, khi app làm mới phiên, thì app xóa phiên đã lưu và hiện màn của AC-29.

### R-6 Phiên mobile và tự làm mới

Phiên mobile hết khi người dùng không quay lại app trong 7 ngày, và hết muộn nhất 30 ngày kể từ lúc đăng nhập dù vẫn được dùng đều (D-35, thay D-23). App tự làm mới token, người dùng không thấy. Phiên mobile đi qua một client Keycloak riêng cho mobile; client `thi360-api` và thời hạn của web giữ nguyên. Cách đặt mốc 7 ngày trên Keycloak chốt bằng thử nghiệm (Q-4).

- Nguồn: D-15, D-35.
- Áp dụng cho: học viên đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-94** *(Giới hạn; sửa ở bản 6 theo D-35)* Cho một phiên mobile được dùng ít nhất một lần trong mỗi 7 ngày, khi đã quá 30 ngày kể từ lúc đăng nhập, thì lần làm mới kế tiếp bị từ chối và app về màn Đăng nhập kèm dòng hết hạn (AC-32); trước mốc 30 ngày thì phiên vẫn làm mới được. Kiểm bằng test tích hợp trên Keycloak với thời hạn rút ngắn theo cùng cách của thử nghiệm ở mục 2.
  - **AC-31** Cho một phiên mobile dùng lần cuối cách đây dưới 7 ngày và đăng nhập chưa quá 30 ngày, khi mở app, thì vào thẳng app (hoặc màn khóa nếu đã bật sinh trắc học), không phải nhập mật khẩu.
  - **AC-32** *(Giới hạn)* Cho một phiên mobile không dùng quá 7 ngày, khi mở app, thì hiện màn Đăng nhập kèm dòng "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
  - **AC-33** Cho access token đã hết hạn và refresh token còn hạn, khi app gọi một API, thì app tự làm mới rồi gọi lại, người dùng nhận kết quả bình thường; nhiều lời gọi cùng lúc chỉ gây một lần làm mới.
  - **AC-34** *(Từ chối)* Cho tài khoản bị ngừng hoạt động, bị đặt lại mật khẩu hoặc phiên bị thu hồi ở máy chủ, khi app gọi API hoặc làm mới phiên và nhận 401, thì app xóa phiên đã lưu và về màn Đăng nhập kèm dòng của AC-32.
  - **AC-35** Cho phiên của web (cookie), khi không dùng 8 giờ, thì hết hạn như trước thay đổi này.
  - **AC-36** Cho app không có mạng lúc mở, khi phiên đã lưu còn hạn theo đồng hồ máy, thì app không đăng xuất; hiện trạng thái mất kết nối kèm nút "Thử lại".
  - **AC-90** Cho app không có mạng lúc mở và phiên đã lưu quá hạn theo đồng hồ máy, khi mở app, thì app vẫn ở trạng thái mất kết nối của AC-36, không tự đăng xuất. App không bao giờ kết luận phiên hết hạn theo đồng hồ máy, kể cả khi có mạng: chỉ kết quả làm mới phiên quyết định (AC-31 hoặc AC-32).

### R-7 Đăng ký hai bước có OTP

- Nguồn: D-5; thiết kế `A-Register`, `A-Otp`, `I-Register`, `I-Otp`; luật hiện có ở đặc tả `docs/specs/2026-11-22-xac-minh-otp-khi-dang-ky.md`.
- Áp dụng cho: người chưa có tài khoản.
- Tiêu chí chấp nhận:
  - **AC-37** Cho màn Đăng ký, khi nhập họ tên, tên đăng nhập, email, mật khẩu đạt đủ năm điều kiện và tích đồng ý Điều khoản sử dụng rồi bấm Đăng ký, thì app chuyển sang màn "Xác minh email" ghi rõ email nhận mã.
  - **AC-38** *(Từ chối)* Cho dữ liệu sai quy tắc (tên đăng nhập sai mẫu, email sai dạng, mật khẩu thiếu điều kiện, chưa tích Điều khoản), khi bấm Đăng ký, thì lỗi hiện dưới đúng ô theo câu chữ khóa `account.register` và không gọi API; khi gõ mật khẩu, từng điều kiện đổi trạng thái đạt/chưa đạt ngay.
  - **AC-39** *(Từ chối)* Cho API trả `USERNAME_TAKEN` hoặc `EMAIL_TAKEN`, khi nhận phản hồi, thì lỗi hiện dưới ô tương ứng và dữ liệu đã nhập giữ nguyên.
  - **AC-40** Cho màn Xác minh email, khi nhập đúng mã 6 chữ số, thì tài khoản được tạo, app đăng nhập luôn và đi tiếp như AC-25.
  - **AC-41** *(Từ chối)* Cho mã sai, khi bấm Xác minh, thì hiện "Mã chưa đúng. Bạn còn {n} lần thử."; mã hết hạn thì hiện hướng dẫn bấm "Gửi lại mã".
  - **AC-42** Cho vừa gửi mã, khi chưa hết thời gian chờ máy chủ trả về (`resendAfterSeconds` trong phản hồi; `retryAfter` khi nhận 429), thì nút gửi lại bị khóa và hiện đếm ngược; hết chờ thì bấm được, bấm xong hiện "Đã gửi mã mới. Mã cũ không còn dùng được."
  - **AC-43** Cho màn Xác minh email, khi bấm "Quay lại sửa thông tin", thì về màn Đăng ký với dữ liệu đã nhập, trừ mật khẩu.
  - **AC-91** Cho đăng ký từ app, khi gửi `/auth/register`, thì `termsVersion` trùng chuỗi web đang gửi (`2026-11`).
  - **AC-92** *(Giới hạn)* Cho app bị tắt hẳn khi đang ở màn Xác minh email, khi mở lại, thì app ở màn Đăng nhập, không khôi phục bước OTP; người dùng đăng ký lại từ đầu với cùng email và nhận mã mới.

### R-8 Quên mật khẩu và đặt lại qua deep link

- Nguồn: D-5, D-13; thiết kế `A-Forgot`, `A-NewPassword`, `I-Forgot`, `I-NewPassword`.
- Dữ liệu người yêu cầu cấp khi làm tới phần deep link (D-14): Apple Team ID cho tệp xác minh của iOS. Bản `dev`/`demo` của Android ký bằng một kho khóa debug dùng chung cho mọi máy build và CI (không phải khóa phát hành), để vân tay trong tệp xác minh khớp ở mọi nơi.
- Áp dụng cho: người chưa đăng nhập hoặc đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-44** Cho màn Quên mật khẩu, khi nhập một email đúng dạng và bấm "Gửi hướng dẫn", thì app hiện màn "Hãy kiểm tra hộp thư" với cùng nội dung dù email có tồn tại hay không, kèm nút gửi lại đếm ngược 60 giây như web. Bấm gửi lại luôn hiện cùng nội dung đó, kể cả khi máy chủ bỏ qua yêu cầu.
  - **AC-45** Cho máy đã cài app (bản dùng tên miền đã xác minh), khi bấm liên kết `…/reset-password?token=…` trong thư, thì hệ điều hành mở app ở màn "Đặt mật khẩu mới", không qua trình duyệt; máy chưa cài app thì liên kết mở trang web như hiện nay.
  - **AC-46** Cho màn Đặt mật khẩu mới mở từ liên kết còn hiệu lực, khi nhập mật khẩu đạt điều kiện, nhập lại khớp và bấm lưu, thì app về màn Đăng nhập kèm dòng "Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới."; nếu máy đang có phiên, của bất kỳ tài khoản nào, thì phiên đó bị xóa khỏi máy (app không biết liên kết thuộc tài khoản nào nên không so).
  - **AC-47** *(Từ chối)* Cho liên kết đã hết hạn hoặc đã dùng (`RESET_TOKEN_INVALID`), khi bấm lưu, thì hiện "Liên kết không còn dùng được" kèm nút yêu cầu liên kết mới (mở màn Quên mật khẩu).
  - **AC-48** *(Từ chối)* Cho mật khẩu nhập lại không khớp hoặc chưa đạt điều kiện, khi bấm lưu, thì lỗi hiện dưới ô và không gọi API.
  - **AC-49** Cho app đang ở trạng thái khóa, khi mở từ liên kết đặt lại mật khẩu, thì màn Đặt mật khẩu mới hiện ngay, không đòi mở khóa.
  - **AC-50** Cho tên miền web học viên của môi trường `demo`, khi gọi `GET /.well-known/assetlinks.json` và `GET /.well-known/apple-app-site-association`, thì nhận 200, `Content-Type: application/json`, nội dung khai đúng định danh hai app và hai đường dẫn `/reset-password` và `/checkout/result/app`.
    - *Ghi chú sửa (`UNI-2872`): sửa từ "chỉ đường dẫn `/reset-password`" sang hai đường dẫn theo đặc tả `docs/specs/2026-11-26-app-hoc-vien-cong-thi.md` mục 7 (R-40).*
  - **AC-51** Mã trong liên kết không xuất hiện trong log của app, không ghi xuống bộ nhớ lưu trữ của máy, và không còn trong trạng thái của màn sau khi rời màn: quay lại màn đó không gửi lại được mã cũ.

### R-9 Bắt đổi mật khẩu và đổi mật khẩu

- Nguồn: D-5; thiết kế `A-NewPassword` và `I-NewPassword` (biến thể `forced`), `A-Fold-Account`, `I-Duo-Open-Account`.
- Áp dụng cho: học viên đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-52** Cho tài khoản có `mustChangePassword = true`, khi đăng nhập thành công, thì app hiện màn "Đặt mật khẩu mới" có ô Mật khẩu hiện tại; mọi mục khác bị chặn tới khi đổi xong; lối ra duy nhất còn lại là đăng xuất.
  - **AC-53** Cho màn của AC-52, khi nhập đúng mật khẩu hiện tại và mật khẩu mới hợp lệ rồi lưu, thì app vào khung app, không phải đăng nhập lại; lần đăng nhập sau không còn bị bắt đổi.
  - **AC-54** Cho mục Tài khoản, khi chọn "Đổi mật khẩu" và lưu thành công, thì hiện thông báo đã đổi và người dùng vẫn đăng nhập trên máy này.
  - **AC-89** Cho học viên đang dùng app, khi một lần làm mới phiên trả `mustChangePassword = true`, thì app hiện màn của AC-52 và chặn mọi mục khác, như sau một lần đăng nhập.
  - **AC-55** *(Từ chối)* Cho mật khẩu hiện tại sai, khi lưu, thì hiện "Mật khẩu hiện tại không đúng." dưới ô đó; mật khẩu mới trùng tên đăng nhập hoặc thiếu điều kiện thì hiện lỗi tương ứng.

### R-10 Mở khóa bằng sinh trắc học

- Nguồn: D-5, D-16, D-28; thiết kế `A-BioEnable`, `A-BioUnlock`, `I-BioEnable`, `I-BioUnlock`.
- Màn khóa ở đây là màn khóa của riêng app, không phải màn khóa của điện thoại. Mật khẩu hay vân tay mở máy do hệ điều hành xử lý, app không thấy và không tính vào đâu.
- Áp dụng cho: học viên đã đăng nhập, trên máy có sinh trắc học đã đăng ký.
- Tiêu chí chấp nhận:
  - **AC-56** Cho máy có sinh trắc học đã đăng ký, khi đăng nhập bằng mật khẩu thành công (sau bước bắt đổi mật khẩu nếu có), thì app hỏi bật mở khóa sinh trắc học, trừ khi câu trả lời gần nhất trên máy này là "Để sau". Câu trả lời "Để sau" lưu theo máy, còn sau khi đăng xuất.
  - **AC-57** Cho người dùng chọn bật, khi hệ điều hành xác nhận sinh trắc học thành công, thì tính năng bật; hủy hoặc thất bại thì vẫn tắt.
  - **AC-58** Cho tính năng đang bật, khi mở app sau khi tắt hẳn hoặc quay lại sau khi rời app quá 5 phút, thì hiện màn khóa có tên người dùng và ba nút: mở khóa, "Nhập mật khẩu", "Đăng nhập bằng tài khoản khác"; nội dung app không lộ ở màn khóa và ở ảnh xem trước của trình chuyển ứng dụng.
  - **AC-59** Cho màn khóa, khi sinh trắc học thành công, thì về đúng màn đang xem trước khi khóa. Rời app dưới 5 phút thì không khóa.
  - **AC-60** *(Từ chối)* Cho sinh trắc học thất bại, bị hủy hoặc bị hệ điều hành khóa tạm, khi ở màn khóa, thì app vẫn khóa. "Nhập mật khẩu" mở màn Đăng nhập với tên đăng nhập của tài khoản đang khóa điền sẵn; quay lại từ màn đó thì về màn khóa, phiên đang giữ còn nguyên. Đăng nhập ở đây là một lần đăng nhập thường qua `mobile/login`: sai thì báo lỗi và bị tính như AC-15, AC-27 (D-28). Đúng thì app dùng phiên mới và vào Trang chủ; app gọi `mobile/logout` cho phiên cũ, lời gọi đó lỗi thì phiên cũ tự hết hạn và không ảnh hưởng phiên mới. Trạng thái sinh trắc học của phiên cũ bị xóa (AC-93) và app hỏi bật lại theo AC-56.
  - **AC-61** Cho màn khóa, khi chọn "Đăng nhập bằng tài khoản khác", thì app đăng xuất phiên đang giữ và về màn Đăng nhập.
  - **AC-62** *(Giới hạn)* Cho máy không có sinh trắc học hoặc chưa đăng ký, khi đăng nhập, thì app không hỏi bật; công tắc trong Tài khoản bị khóa kèm dòng giải thích.
  - **AC-63** *(Giới hạn)* Cho tính năng đang bật, khi bộ sinh trắc học của máy thay đổi (thêm vân tay, đăng ký lại khuôn mặt), thì lần mở khóa kế tiếp đòi mật khẩu và tính năng tự tắt tới khi người dùng bật lại.
  - **AC-64** Cho tính năng đang bật, khi tắt công tắc trong Tài khoản, thì app không còn khóa; bật lại thì phải qua xác nhận sinh trắc học như AC-57.
  - **AC-65** Cho màn khóa và máy có mạng, khi mở khóa bằng sinh trắc học thành công mà lần làm mới phiên ngay sau đó cho biết phiên đã hết hạn hoặc bị thu hồi (AC-32, AC-34), thì app về màn Đăng nhập kèm dòng hết hạn. Mất mạng thì sinh trắc học mở khóa bình thường; phiên đã chết được phát hiện ở lần có mạng kế tiếp (AC-34).
  - **AC-93** Trạng thái bật của tính năng và dữ liệu được bảo vệ đi kèm phiên: đăng xuất (AC-69), chọn tài khoản khác (AC-61) hoặc phiên bị xóa khỏi máy (AC-34, AC-46) đều tắt tính năng và xóa dữ liệu đó.
  - **AC-66** Tên hiển thị theo máy: Android ghi "vân tay hoặc khuôn mặt"; iOS ghi "Face ID" hoặc "Touch ID" đúng loại máy có.

### R-11 Mục Tài khoản

- Nguồn: D-7; thiết kế `A-Account`, `I-Account`, `I-Pad-Account`.
- Áp dụng cho: học viên đã đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-67** Cho mục Tài khoản, khi mở, thì hiện họ tên, tên đăng nhập, email và nhãn "Email đã xác minh" hoặc "Chưa xác minh" lấy từ `GET /api/v1/me/profile`; tài khoản không có email thì không hiện dòng email. Các thông tin này chỉ đọc.
  - **AC-68** Cho mục Tài khoản, khi đang tải, tải lỗi hoặc mất mạng, thì hiện lần lượt khung chờ, thông báo lỗi kèm nút "Thử lại", hoặc trạng thái mất kết nối; các mục cài đặt lưu trên máy (ngôn ngữ, giao diện, sinh trắc học) vẫn dùng được.
  - **AC-69** Cho mục Tài khoản, khi bấm "Đăng xuất" và xác nhận, thì app gọi `mobile/logout`, xóa token và trạng thái sinh trắc học của phiên, về màn Đăng nhập. Mất mạng lúc đăng xuất thì máy vẫn xóa phiên và về màn Đăng nhập.
  - **AC-70** Cho người dùng A đăng xuất rồi người dùng B đăng nhập trên cùng máy, khi B vào app, thì không còn dữ liệu nào của A; lựa chọn ngôn ngữ và sáng/tối của máy giữ nguyên.

### R-12 Giao diện theo cấu hình của admin

App lấy màu và chế độ mặc định theo cấu hình của `GET /api/v1/appearance`; app đọc cấu hình đó qua một route của web học viên, nơi tính sẵn bảng màu (D-36). Admin đổi cấu hình thì app đổi theo, không cần cập nhật app.

- Nguồn: D-18, D-36; D-9 cho phần kiểu thành phần theo hệ điều hành; `DESIGN.md` mục 9.
- Áp dụng cho: mọi màn của app, kể cả trước đăng nhập.
- Tiêu chí chấp nhận:
  - **AC-71** Cho mỗi bộ trong 9 bộ phối màu cố định và một cấu hình `custom`, ở cả sáng và tối, khi app áp cấu hình, thì các vai màu thương hiệu của app có mã màu trùng với token web suy ra cho cùng cấu hình (`primary-fill`, `on-primary`, `primary-text`, `primary-50`/`primary-100`, `accent-500`, `on-accent` của `deriveTokens`).
  - **AC-72** Cho admin vừa lưu một bộ màu khác, khi người dùng mở app hoặc đưa app từ nền trở lại sau ít nhất 60 giây, thì app hiện màu mới ở mọi màn mà không phải cài lại hay đăng nhập lại.
  - **AC-73** Cho người dùng chưa tự chọn sáng/tối trên máy, khi app chạy, thì dùng `mode` của cấu hình; không theo chế độ sáng/tối của hệ điều hành (D-20).
  - **AC-74** Cho người dùng đã chọn Sáng hoặc Tối trong Tài khoản, khi admin đổi `mode`, thì lựa chọn của người dùng giữ nguyên; lựa chọn lưu trên máy như web lưu ở trình duyệt (cookie `thi360_theme`), còn sau khi tắt app và sau khi đăng xuất.
  - **AC-75** Cho app đã từng lấy được cấu hình, khi mở lúc mất mạng, thì dùng cấu hình đã lưu gần nhất. Lần mở đầu tiên chưa lấy được cấu hình thì dùng bộ `thi360-green`, chế độ sáng.
  - **AC-76** Cho mọi cấu hình của AC-71, khi kiểm bộ màu app suy ra bằng phép kiểm tĩnh trên cùng danh sách cặp chữ–nền mà `packages/ui/src/styles/tokens.test.ts` kiểm cho web, thì chữ đạt tương phản 4,5:1 và thành phần không phải chữ đạt 3:1 (ngưỡng của `DESIGN.md` mục 8).
  - **AC-77** *(Giới hạn)* Cho API trả `presetId` mà app không biết, khi áp cấu hình, thì app giữ cấu hình đang dùng và không lỗi.
  - **AC-78** Nền, bề mặt, viền và chữ trung tính dùng đúng mã màu token trung tính của web ở từng chế độ (`DESIGN.md` mục 2.3 cho chế độ sáng; bộ tối trong `packages/ui/src/styles/tokens.css`) và không đổi theo bộ màu, như web (D-21). Kiểu thành phần giữ theo từng hệ điều hành như bản thiết kế.

### R-13 Ngôn ngữ

- Nguồn: `RULES.md` mục 3 (song ngữ); thiết kế nút đổi ngôn ngữ ở Đăng nhập và mục Ngôn ngữ ở Tài khoản.
- Áp dụng cho: mọi màn.
- Tiêu chí chấp nhận:
  - **AC-79** Cho lần mở đầu tiên, khi người dùng chưa chọn ngôn ngữ, thì app dùng tiếng Việt, bất kể ngôn ngữ của máy (D-22).
  - **AC-80** Cho màn Đăng nhập hoặc mục Tài khoản, khi đổi ngôn ngữ, thì mọi chữ giao diện đổi ngay; lựa chọn còn sau khi tắt app và sau khi đăng xuất.
  - **AC-81** Cho mỗi ngôn ngữ, khi rà toàn bộ chuỗi của app, thì hai bản có cùng bộ khóa; màn tiếng Anh không còn chữ giao diện tiếng Việt và ngược lại.
  - **AC-82** Cho app đang ở tiếng Anh, khi yêu cầu mã OTP đăng ký, thì thư gửi bằng tiếng Anh; ở tiếng Việt thì thư tiếng Việt. Thư đặt lại mật khẩu theo ngôn ngữ ưu tiên của tài khoản, tức ngôn ngữ của app ở lần đăng nhập gần nhất (AC-87).
  - **AC-83** Cho máy chủ trả một mã lỗi app chưa có câu chữ, khi hiện lỗi, thì app dùng câu chung của màn đó ("Không thể đăng nhập. Vui lòng thử lại." và tương tự), không hiện mã hay thông báo kỹ thuật.

### R-14 Lưu thông tin đăng nhập an toàn trên máy

- Nguồn: `RULES.md` mục 5 (token không nằm ở nơi lưu thường), D-4.
- Áp dụng cho: mọi phiên mobile.
- Tiêu chí chấp nhận:
  - **AC-84** Cho một phiên đang lưu, khi kiểm tra dữ liệu app trên máy, thì token chỉ nằm trong kho bảo mật của hệ điều hành (Android: mã hóa bằng khóa trong Keystore; iOS: Keychain chỉ dùng trên máy này), không có trong tệp cấu hình thường, bản sao lưu của hệ điều hành hay log.
  - **AC-85** Cho bản build `demo`, khi đọc log của app trong lúc đăng nhập, đăng ký, đặt lại mật khẩu, thì không thấy mật khẩu, mã OTP, token, mã đặt lại.
  - **AC-86** Cho app bị gỡ rồi cài lại, khi mở, thì không còn phiên cũ.

## 7. Ảnh hưởng tới phần đang có

| Thứ bị ảnh hưởng | Ở đâu | Thay đổi | Yêu cầu |
| --- | --- | --- | --- |
| Module `identity`: thêm controller và use case mobile | `backend/src/main/java/vn/unicom/thi360/identity/api`, `identity/application/AuthService.java`, `SelfRegistrationService.java` | Thêm đường mobile dùng lại các kiểm tra của web; không đổi hành vi đường cookie | R-3 |
| Bộ lọc bảo mật | `shared/security/SecurityConfig.java:98-102`, `RateLimitFilter.java:44,381-391` | Khai các đường `/api/v1/auth/mobile/**` là công khai; `mobile/login` theo chính sách đăng nhập, `mobile/register/verify` theo nhóm đăng ký | R-3 |
| Lấy ngôn ngữ | `AuthController.java:69,90,110`, `AuthService.java:197-198`, `SelfRegistrationService.java` | Thêm nguồn `Accept-Language` khi không có cookie ở đăng nhập mobile, đăng ký và gửi lại mã. Luồng quên mật khẩu (`PasswordForgotDispatcher`, `PasswordResetService.java:177`) không đổi | R-3, R-13 |
| Keycloak realm `thi-360` | `infra/keycloak/thi-360-realm.json`; `identity/infrastructure/KeycloakIdentityClient.java:408-436`; realm trên Keycloak dùng chung của bản demo | Thêm client riêng cho mobile và cấu hình thời hạn (Q-4); backend chọn client theo kênh đăng nhập. Realm trên máy chủ phải cập nhật tay khi triển khai, chưa cập nhật thì đăng nhập mobile lỗi còn web không ảnh hưởng | R-6 |
| Giới hạn 5 phiên và hạn 30 ngày của bản ghi phiên | `shared/security/SecurityProperties.java:82-85`, `shared/security/SessionStore.java` | Giới hạn 5 phiên không đổi, phiên mobile tính chung (D-24). Hạn 30 ngày của bản ghi phiên trùng mốc tối đa của phiên mobile (AC-94), không cần đổi; phiên web không đổi | R-6 |
| Nhật ký `LOGIN` | `AuthService.java:336` | Thêm thông tin kênh đăng nhập | R-3 |
| OpenAPI và client sinh tự động | `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`, `OpenApiExportIT`, bước `api-client-drift` của `.github/workflows/web.yml` | Có thêm endpoint mobile; phải sinh lại client | R-3 |
| Thông báo lỗi backend | `backend/src/main/resources/messages*.properties` (ba tệp, `MessageBundlesTest`) | Thêm `APP_STUDENT_ONLY` | R-3 |
| Web học viên | `apps/web-student/public/` hoặc route tương đương; nginx của bản demo | Phục vụ hai tệp `/.well-known/…` đúng kiểu nội dung | R-8 |
| CI | `.github/workflows/web.yml:6,9` (bộ lọc `apps/**`), workflow mới cho Android và iOS | Sửa bộ lọc để `apps/android/**`, `apps/ios/**` không kích hoạt workflow web; thêm build và test cho từng app | R-1 |
| Workspace JS | `pnpm-workspace.yaml` (`apps/*`), `turbo.json` | `apps/android` và `apps/ios` không được bị coi là gói JS; `pnpm install`, `pnpm lint`, `pnpm typecheck` cho kết quả như trước | R-1 |
| Tài liệu | `ARCHITECTURE.md` mục 1, 2, 3; `TASK.md` bảng "Các đợt sau"; `DESIGN.md` (mục mới cho app); `README.md` (cách chạy app); `.env.example` nếu thêm biến | Ghi công nghệ native thay React Native, quy ước giao diện app, lệnh chạy | R-1, R-2, R-12 |
| Nguồn suy ra token màu | `packages/ui/src/theme/scale.ts:476`, `packages/ui/src/theme/palettes.ts` | App phải cho cùng kết quả; khi công thức web đổi thì phép kiểm của AC-71 phải đỏ | R-12 |

## 8. Dữ liệu và chuyển đổi

- Không đổi lược đồ cơ sở dữ liệu. Nếu cần ghi kênh đăng nhập vào nhật ký thì đó là trường thêm trong nội dung sự kiện, bản ghi cũ không đổi.
- Keycloak: thay đổi cấu hình realm là thêm, không ảnh hưởng phiên web đang chạy. Hoàn tác: gỡ cấu hình mobile; các phiên mobile hết hạn theo cấu hình cũ.
- Trên máy người dùng: token trong kho bảo mật; lựa chọn ngôn ngữ, sáng/tối, trạng thái sinh trắc học và bản lưu cấu hình giao diện trong bộ nhớ riêng của app. Chưa có bản app nào trước đây nên không có dữ liệu cũ cần chuyển.
- Các endpoint mobile là thêm mới; tắt chúng không ảnh hưởng hai web.

## 9. Yêu cầu phi chức năng

| Loại | Yêu cầu | Cách kiểm |
| --- | --- | --- |
| Bảo mật | Endpoint mobile chịu cùng chặn đăng nhập sai, giới hạn tần suất, từ chối tài khoản ngừng hoạt động như web; không mở đường nào bỏ qua xác thực | Test tích hợp backend với Testcontainers (AC-15..AC-20) |
| Bảo mật | Token, mật khẩu, OTP, mã đặt lại không vào log, nhật ký, `toString` của request | AC-22, AC-85; `CredentialRequestToStringTests` mở rộng cho request mới |
| Bảo mật | Bản `demo` chỉ gọi HTTPS | AC-4 |
| Hiệu năng | Từ lúc mở app (đã có phiên, có mạng) tới lúc thấy màn đầu tiên dùng được: không quá 2 giây trên máy ảo tham chiếu (Pixel 7 API 34; iPhone 17 iOS 26). Làm mới phiên không chặn việc vẽ màn | Đo bằng công cụ đo khởi động của từng nền tảng, 5 lần, lấy trung vị |
| Truy cập | Vùng bấm tối thiểu 48dp (Android), 44pt (iOS); mọi điều khiển có nhãn cho TalkBack và VoiceOver; thứ tự đọc theo thứ tự nhìn | Kiểm tự động của bộ test giao diện từng nền tảng, kèm rà tay bằng trình đọc màn hình trên màn Đăng nhập và Tài khoản |
| Truy cập | Cỡ chữ hệ thống tới 130% không cắt hay chồng chữ; tới 200% mọi màn vẫn cuộn được và dùng được | Ảnh chụp màn ở hai mức cỡ chữ |
| Ngôn ngữ | Hai bản dịch cùng bộ khóa | AC-81 |
| Môi trường build iOS | Mỗi bước build và test iOS chạy lại được từ đầu trên máy vừa khởi động: không dựa vào tiến trình hay simulator còn sống từ bước trước | Chạy bước đó ngay sau khi máy `devin-mac` thức dậy, kết quả như lần chạy liên tục |
| Tương thích | Android: 360×640dp, 412×915dp, màn hình gập mở 840×720dp, tablet 1280×800dp. iOS: iPhone 390×844pt, iPad 1180×820pt và 820×1180pt, kích thước màn ngoài và màn trong của iPhone Duo (Q-3, D-24). Mỗi kích thước ở sáng và tối | Ảnh chụp màn tự động cho các màn Đăng nhập, Đăng ký, Khóa, Trang chủ, Tài khoản; không tràn ngang, không cắt nội dung |

## 10. Quyết định

| ID | Quyết định | Ai, khi nào | Nguyên văn | Thay cho |
| --- | --- | --- | --- | --- |
| D-1 | Tạo issue cha UniAI cho việc này (UNI-2785) | M.Trường, 2026-11-25 | "1- ok" | — |
| D-2 | App viết native riêng từng nền tảng, chỉ cho cổng thi | M.Trường, 2026-11-25 | "app làm native cho từng loại android và ios để dễ tùy biến" | `ARCHITECTURE.md:44` (React Native) |
| D-3 | Máy Mac của Devin là môi trường build iOS | M.Trường, 2026-11-25 | "dùng nó làm môi trường dev app ios nhé" | — |
| D-4 | API đăng nhập mobile trả token trong thân, app gọi bằng Bearer | M.Trường, 2026-11-25 | "4- ok" | — |
| D-5 | Xác thực gồm đăng nhập, đăng xuất, đăng ký OTP, quên mật khẩu, bắt đổi mật khẩu, tự làm mới phiên, thêm sinh trắc học | M.Trường, 2026-11-25 | "thêm vân tay/faceid" | — |
| D-6 | Chỉ học viên vào app, vai trò khác thấy màn thông báo | M.Trường, 2026-11-25 | "học viên vào thi thôi. vai trò khác thì hiện màn thông báo" | — |
| D-7 | Bốn mục; ba mục đầu giữ chỗ, Tài khoản làm thật | M.Trường, 2026-11-25 | "7- ok" | — |
| D-8 | Điện thoại chỉ dọc; tablet và màn hình gập xoay được | M.Trường, 2026-11-25 | "8- ok" | — |
| D-9 | Giao diện theo chuẩn từng hệ điều hành; làm thiết kế trước | M.Trường, 2026-11-25 | "b . và làm design trước đã" | — |
| D-10 | Tên app tạm là Thi 360 | M.Trường, 2026-11-25 | "tên app chưa chốt, tạm đặt là Thi 360 đi" | — |
| D-11 | Kotlin + Compose (Android 8.0+), Swift + SwiftUI (iOS 17+), không dùng khung chia sẻ mã | M.Trường, 2026-11-25 | "2 - ok" | — |
| D-12 | Agent CLI cài trên Mac của Devin; điều phối ở máy chính | M.Trường, 2026-11-25 | "t sẽ cài agent cli trên đó để làm, điều phối thì vẫn ở máy này" | — |
| D-13 | Deep link làm trong đợt này | M.Trường, 2026-11-25 | "bên ios có tài khoản dev-er rồi, nên cứ làm luôn trong đợt này đi" | — |
| D-14 | Tài khoản Google Play và Apple Developer đã có, cấp khi cần | M.Trường, 2026-11-25 | "2 tài khoản đều có rồi, khi nào dev xong và cần t sẽ cấp" | — |
| D-15 | Phiên trên app hết sau 7 ngày không dùng | M.Trường, 2026-11-25 | "app 7 ngày" | — |
| D-16 | Sinh trắc học: hỏi bật sau lần đăng nhập đầu, khóa lại sau 5 phút rời app, hỏng thì nhập mật khẩu, bật/tắt ở Tài khoản | M.Trường, 2026-11-25 | "12- ok" | — |
| D-17 | iOS phải hỗ trợ iPhone Duo | M.Trường, 2026-11-25 | "ios có iphone duo nữa đó nhé" | — |
| D-18 | App theo cấu hình Tùy chỉnh giao diện của admin | M.Trường, 2026-11-25 | "khi admin tùy chỉnh giao diện thì app cũng cần ăn theo nhé" | — |
| D-19 | Duyệt bản thiết kế 28 màn | M.Trường, 2026-11-25 | "ok chốt giao diện này, làm việc tiếp theo đi" | — |
| D-20 | Sáng/tối do từng người tự chọn như trên web; mặc định theo cấu hình admin, không theo hệ điều hành | M.Trường, 2026-11-25 | "sáng tối thì cho tự cấu hình theo từng tài khoản như trên web chứ" | A-1 |
| D-21 | Màu của app theo bộ màu như web, kể cả màu trung tính | M.Trường, 2026-11-25 | "theo bộ màu như web đi" | A-2 |
| D-22 | Ngôn ngữ mặc định là tiếng Việt | M.Trường, 2026-11-25 | "mặc định tiếng việt đi cho dễ" | A-3 |
| D-23 | Phiên app không có giới hạn tối đa; chỉ hết khi không quay lại trong 7 ngày. Đã bị D-35 thay | M.Trường, 2026-11-25 | "k cần phiên 30 ngày, cứ trong vòng 7 ngày có quay lại 1 lần thì k giới hạn" | A-4 |
| D-24 | Xác nhận: nhiều máy cùng đăng nhập trong giới hạn 5 phiên; deep link chỉ trên tên miền demo; iPhone Duo kiểm bằng bố cục co giãn; Điều khoản dùng bản tạm của web; refresh token cũ không bị thu hồi khi làm mới | M.Trường, 2026-11-25 | "5- ok", "6- ok", "7- ok", "8- ok", "10- ok" | A-5, A-6, A-7, A-8, A-10 |
| D-25 | CI của cả hai app chạy trên GitHub Actions | M.Trường, 2026-11-25 | "ci chạy trên github" | Q-2 |
| D-26 | Định danh app tạm là `vn.unicom.thi360` theo đề xuất ban đầu; thông tin tài khoản Apple và Google cấp khi cần | M.Trường, 2026-11-25 | "nãy chốt rồi mà nhỉ?" | Q-1 |
| D-27 | Nhập sai mật khẩu ở màn khóa không tính vào chặn đăng nhập sai. Đã bị D-28 thay: người yêu cầu trả lời khi đang hiểu màn khóa là màn khóa của điện thoại | M.Trường, 2026-11-25 | "câu 9: không tính." | A-9 |
| D-28 | Nhập mật khẩu Thi 360 từ màn khóa của app là một lần đăng nhập thường và có tính vào chặn đăng nhập sai | M.Trường, 2026-11-25 | "à thế thì vẫn tính nhé" | D-27, A-11 |
| D-35 | Phiên app có mốc tối đa 30 ngày kể từ lúc đăng nhập, ngoài mốc 7 ngày không dùng | M.Trường, 2026-11-25 | "đặt trần 1 tháng." | D-23 |
| D-36 | App lấy bảng màu qua route của web học viên, nơi duy nhất tính màu từ cấu hình | M.Trường, 2026-11-25 | "3- ok." | — |

Các số D-29 đến D-34 là quyết định về tiến trình (duyệt đặc tả, cài công cụ, phạm vi nghiệm thu), ghi ở thư mục phiên và kế hoạch; chúng không đổi yêu cầu nào ở đây.

## 11. Giả định cần xác nhận

Không còn giả định nào chờ xác nhận.

Các giả định cũ, giữ số để truy vết: A-1 → D-20 (sáng/tối); A-2 → D-21 (màu trung tính); A-3 → D-22 (ngôn ngữ mặc định); A-4 → D-23 (giới hạn tối đa của phiên); A-5 (nhiều máy, 5 phiên), A-6 (deep link trên tên miền demo), A-7 (cách kiểm iPhone Duo), A-8 (Điều khoản dùng bản tạm), A-10 (refresh token cũ không bị thu hồi khi làm mới, thêm ở bản 2) → D-24; A-9 (nhập mật khẩu ở màn khóa qua `mobile/login`, tính vào chặn đăng nhập sai) → D-28; A-11 (sai 5 lần ở màn khóa thì thu hồi phiên, thêm ở bản 4) → bỏ theo D-28.

## 12. Câu hỏi mở

| ID | Câu hỏi | Đề xuất | Người trả lời | Chặn |
| --- | --- | --- | --- | --- |
| Q-3 | Đã trả lời 2026-11-25: Xcode 27.1 và runtime iOS 27.1 đã cài trên `devin-mac` (D-30); simulator iPhone Duo chạy được; kích thước màn ghi ở mục 2 | — | M.Trường | Không còn chặn |
| Q-4 | Đã trả lời 2026-11-25 bằng thử nghiệm (mục 2): dùng offline token cho client mobile, realm của web không đổi thời hạn nào. Sau đó D-35 đặt thêm mốc tối đa 30 ngày cho phiên offline | — | Agent | Không còn chặn |

## 13. Nhật ký rà soát

| Vòng | Ngày | Người rà soát | Chặn | Nên sửa | Nhỏ | Bác (kèm lý do) | Ghi chú | Báo cáo |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 (toàn bộ) | 2026-11-25 | omp `devin/swe-2-1` mức max | 1, đã sửa (S1-1) | 8, đã sửa (S1-2..S1-9) | 12, đã sửa (S1-10..S1-21) | Người rà soát tự bác 5 ứng viên (SF-1..SF-5) | Tác giả không bác phát hiện nào. NH-1 (giả định mục 11) đã có trả lời của người yêu cầu; NH-2 xử theo hướng giữ giống web (AC-16) | thư mục phiên `reviews/spec-r1-report.md` |
| 2 (phần thay đổi) | 2026-11-25 | omp `devin/swe-2-1` mức max | 0 | 2 (S2-1, S2-2), đã xử lý ở AC-60 bản 5: app gọi `mobile/logout` cho phiên cũ, trạng thái sinh trắc học của phiên cũ bị xóa và app hỏi bật lại | 12, đã sửa (S2-3..S2-14) | Người rà soát tự bác 3 ứng viên (SF2-1..SF2-3) | 21/21 phát hiện vòng 1 được xác nhận đã xử lý. Tác giả không bác phát hiện nào | thư mục phiên `reviews/spec-r2-report.md` |
| 3 (vòng cuối, phần đổi sau vòng 2) | 2026-11-25 | omp `devin/swe-2-1` mức max | 1 (S3-1): endpoint kiểm mật khẩu ở màn khóa vẫn bị Keycloak đếm vào khóa tài khoản. Đã xử lý bằng D-28: bỏ endpoint, màn khóa dùng đăng nhập thường | 2 (S3-2, S3-5), hết áp dụng sau D-28 | 2: S3-3 hết áp dụng sau D-28; S3-4 đã sửa (AC-65) | Người rà soát tự bác 2 ứng viên (SF3-1, SF3-2) | 12 phát hiện vòng 2 đã xử lý, 2 hết áp dụng. S3-6 (cần người quyết) đã có D-28. Hết 3 vòng; phần sửa theo D-28 chưa qua rà soát độc lập, tác giả tự đối chiếu với S1-1, S2-1, S2-2 | thư mục phiên `reviews/spec-r3-report.md` |
| Thay đổi yêu cầu sau G1 | 2026-11-25 | omp `devin/swe-2-1` mức max, cùng vòng 3 của kế hoạch | 0 | 0 | 1, đã sửa (P3-5: dòng phạm vi ở mục 3) | — | Bản 6: D-35 (mốc tối đa 30 ngày: R-6, AC-31, AC-94, mục 7) và D-36 (câu chữ R-12). Bản 5 còn cập nhật mục 2 và Q-3, Q-4 bằng kết quả đo, không đổi yêu cầu | thư mục phiên `decisions.md`, `reviews/plan-r3-report.md` |
