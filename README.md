# Thi 360

Cổng luyện thi trực tuyến cho 9 kỳ thi (`HSA`, `QDA`, `BCA`, `V-ACT`, `SP2E`, `SPT`, `THPTQG`, `H-SCA`, `V-SAT`), gồm ngân hàng câu hỏi, soạn và công bố đề, thương mại/thanh toán (khuyến mãi, bộ đề, VNPAY) và phòng thi/chấm điểm học viên.

| Muốn biết | Đọc |
|---|---|
| Sản phẩm là gì, cho ai | [PRD.md](PRD.md) |
| Công nghệ, thư mục, luồng dữ liệu | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Màu, chữ, thành phần giao diện | [DESIGN.md](DESIGN.md) |
| Quy tắc làm việc | [RULES.md](RULES.md) |
| Task và tiến độ | [TASK.md](TASK.md), [PROGRESS.md](PROGRESS.md) |

## Cần cài

- JDK 21
- Node.js 22 (CI ghim 22; máy có nhiều bản Node thì kiểm tra `node -v` trước khi chạy `pnpm`) và pnpm 10.34.5 (`corepack enable`)
- Docker (có Docker Compose); backend test dùng Testcontainers nên Docker phải chạy
- `uniai` CLI và `gh` (để làm việc theo issue, xem `RULES.md` mục 2)

Trên Windows, `./mvnw` chạy được từ Git Bash khi `JAVA_HOME` trỏ tới JDK 21 theo đường dẫn Windows (ví dụ `C:\Users\<tên>\.jdks\corretto-21.0.10`). Nếu `./mvnw` báo sai `JAVA_HOME`, hoặc khi dùng PowerShell, dùng `mvnw.cmd`: `./mvnw.cmd` (Git Bash) hoặc `.\mvnw.cmd` (PowerShell, đặt các tham số `-D...` trong dấu nháy kép).

## Chạy từ bản clone mới

Làm lần lượt, mỗi bước một cửa sổ terminal khi nói "giữ chạy".

**1. Lấy mã và dựng dịch vụ nền** (PostgreSQL, Redis, Keycloak, MinIO, Mailpit):

```sh
git clone <địa chỉ kho> thi-360 && cd thi-360
git config core.hooksPath .githooks                     # hook tự thêm "Refs: UNI-nnn" vào commit
cp .env.example .env                                    # không bắt buộc: mọi biến đều có mặc định cho máy dev
docker compose --env-file .env -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps           # chờ các dịch vụ healthy; keycloak-init thoát với mã 0
```

Chưa có file `.env` thì bỏ `--env-file .env`. Không bao giờ `docker compose down -v` trên máy có dữ liệu cần giữ (xóa volume là mất cơ sở dữ liệu).

**2. Backend** (giữ chạy). Profile `api,worker,dev`: `worker` chạy outbox relay và các job nền (nhật ký, cấp hàng loạt), `dev` nạp tài khoản và dữ liệu mẫu:

```sh
cd backend
./mvnw -B -ntp spring-boot:run -Dspring-boot.run.profiles=api,worker,dev      # Windows: ./mvnw.cmd -B -ntp spring-boot:run -Dspring-boot.run.profiles=api,worker,dev
# kiểm tra: http://localhost:8080/actuator/health  và  http://localhost:8080/api/v1/ping
```

Backend không đọc file `.env`: biến nào khác mặc định phải `export` (hoặc đặt) trong chính shell chạy backend.

**3. Hai web** (giữ chạy, từ thư mục gốc):

```sh
pnpm install
pnpm dev                                                 # web học viên http://localhost:3000, web admin http://localhost:3001
```

**4. Đăng nhập thử** tại http://localhost:3000/login bằng một tài khoản mẫu bên dưới. Quản trị viên được chuyển sang web admin, học viên tới `/home`.

Lưu từ web admin cần backend biết địa chỉ web admin (`THI360_ADMIN_URL`, mặc định `http://localhost:3001`); nếu bạn chạy web ở cổng khác, đặt `THI360_WEB_URL` và `THI360_ADMIN_URL` cho backend và `THI360_API_URL` cho web cho khớp.

### Máy Windows dùng chung cơ sở dữ liệu với nhánh khác

- PostgreSQL của bộ Compose dùng chung trên máy Windows chuẩn của nhóm nghe ở cổng `54320`, vì `54360` rơi vào dải cổng Windows giữ chỗ (xem `netsh int ipv4 show excludedportrange protocol=tcp`). Dựng Compose với `THI360_DB_PORT=54320` (đặt trong `.env`), rồi đặt cùng biến đó cho backend và cho Playwright (backend không đọc `.env`).
- Cơ sở dữ liệu dùng chung đã được nhánh khác migrate: một migration `V<yyyyMMddHHmm>` có thời điểm sớm hơn migration đã áp dụng thì Flyway báo "resolved migration not applied". Khi đó chạy backend với `SPRING_FLYWAY_OUT_OF_ORDER=true` (chỉ là biến môi trường, không sửa mã; cơ sở dữ liệu mới và CI không cần).

```sh
THI360_DB_PORT=54320 SPRING_FLYWAY_OUT_OF_ORDER=true ./mvnw.cmd -B -ntp spring-boot:run -Dspring-boot.run.profiles=api,worker,dev   # Git Bash, trong backend/
```

## Ba bộ kiểm thử

Chạy từ thư mục gốc (Node 22 trên PATH). Ba bộ độc lập; CI chạy cả ba (`.github/workflows/`).

| Bộ | Lệnh | Ghi chú |
|---|---|---|
| Backend | `cd backend && ./mvnw -B -ntp verify` | Unit + tích hợp (Testcontainers: PostgreSQL, Redis, Keycloak, MinIO; cần Docker, nặng). Chạy một lần trên một máy tại một thời điểm. Xuất `backend/target/openapi.json`. Test hẹp: `./mvnw -B -ntp test -Dtest=<Lớp>` |
| Web | `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build` | `pnpm test` chạy Vitest của hai web và các gói, không gồm e2e |
| E2E | `pnpm --filter @thi360/e2e exec playwright install chromium` (một lần), rồi `E2E_USE_BUILT=1 pnpm --filter @thi360/e2e test` | Cần Compose đang chạy và `pnpm build` đã chạy. Playwright (`e2e/playwright.config.ts` và `.github/workflows/e2e.yml`) tự khởi động backend với profile `api,worker,dev,e2e` (mở đồng hồ điều khiển được `/api/v1/test/clock`, hộp thư thử `/api/v1/test/mail`, giả lập lỗi tệp nghe `/api/v1/test/audio-faults/**` và cổng thanh toán giả lập `/mock-vnpay` khi `THI360_VNPAY_MODE=MOCK`) cùng hai web ở 8080/3000/3001 rồi tắt khi xong; `reuseExistingServer` bật ở máy dev nên cổng đang có server sẽ bị dùng lại, hãy giải phóng 8080/3000/3001 trước |

Kiểm tra không lệch OpenAPI sau `verify`: `diff -u packages/api-client/openapi.json backend/target/openapi.json` rồi `pnpm --filter @thi360/api-client generate && git diff --exit-code packages/api-client` đều phải sạch. Trên Windows có DB dùng chung xem mục trên: `THI360_DB_PORT=54320 SPRING_FLYWAY_OUT_OF_ORDER=true E2E_USE_BUILT=1 pnpm --filter @thi360/e2e test`.

## App học viên (Android & iOS)

Ứng dụng di động cho cổng thi học viên được viết native riêng cho từng nền tảng (đợt đầu gồm khung điều hướng và xác thực, `UNI-2785`):

- **Android** (`apps/android/README.md`, tạo trong task `AND-1`): Kotlin, Jetpack Compose, Material 3, hỗ trợ từ Android 8.0 (API 26).
- **iOS** (`apps/ios/README.md`, tạo trong task `IOS-1`): Swift, SwiftUI, hỗ trợ từ iOS 17, sinh dự án bằng XcodeGen trên macOS.
- **Hợp đồng dùng chung** (`contracts/mobile/`, tạo trong `BE-1`, `WEB-1`): tệp mẫu phản hồi `auth/`, `api/`, `appearance/` và bản lọc OpenAPI `contracts/mobile/openapi-mobile.json` để sinh client lúc build.
- **Tài liệu đợt chạy:** [Đặc tả](docs/specs/2026-11-25-app-hoc-vien-khung-xac-thuc.md), [Kế hoạch](docs/plans/2026-11-25-app-hoc-vien-khung-xac-thuc.md), [Bản thiết kế 28 màn](docs/design/app-hoc-vien/README.md); quy ước giao diện app nằm ở `DESIGN.md` mục 11 và kiến trúc xác thực/phiên/màu ở `ARCHITECTURE.md` mục 5.9.

## Đăng nhập dev (tài khoản mẫu)

Khi chạy backend với profile `dev` (`api,worker,dev`), `DevDataInitializer` tự tạo sẵn các tài khoản sau (chỉ ở môi trường dev). Đăng nhập tại http://localhost:3000/login (một nơi đăng nhập cho cả web học viên và web admin):

| Tên đăng nhập | Mật khẩu | Vai trò / phạm vi |
|---|---|---|
| `sys.admin` | `Thi360@dev` | Quản trị hệ thống (`SYSTEM_ADMIN`, `MANAGE`) |
| `content.admin` | `Thi360@dev` | Quản trị nội dung (`CONTENT_ADMIN`, `MANAGE`) |
| `ops.admin` | `Thi360@dev` | Vận hành (`OPERATIONS`, `MANAGE`) |
| `student01` … `student05` | `Thi360@dev` | Học viên (`STUDENT`, `EXAM`) |
| `platform` | `platform-dev-only` (`THI360_DEV_PLATFORM_PASSWORD`) | Tài khoản khởi tạo ở realm `master` (`PLATFORM_ADMIN`) |

Dữ liệu mẫu dev khác: lớp `LOP-A`, `LOP-B` (`DevClassSeeder`, chỉ profile `dev`) và nhóm danh mục mẫu (`db/devdata`, profile `dev` và `test`). Cách chạy ba bộ kiểm thử xem mục "Ba bộ kiểm thử".

## Dịch vụ dev (Docker Compose)

| Dịch vụ | Địa chỉ | Ghi chú |
|---|---|---|
| PostgreSQL | `localhost:54360` | cơ sở dữ liệu `thi360`; Keycloak dùng cơ sở dữ liệu `keycloak` riêng |
| Redis | `localhost:63790` | |
| Keycloak | http://localhost:8360 | realm `thi-360` được nạp từ `infra/keycloak/thi-360-realm.json` (`keycloak-init` cấu hình realm `master` và tài khoản mẫu `platform`) |
| MinIO | API `localhost:9360`, bảng điều khiển http://localhost:9361 | |
| Mailpit | SMTP `localhost:1360`, xem thư http://localhost:8361 | bắt mọi email gửi thử |

Tài khoản và mật khẩu mặc định nằm trong `.env.example`, **chỉ dùng cho máy dev**.

## Xác thực & cấu hình Keycloak thật

Ứng dụng dùng hai realm Keycloak tách biệt (xem `ARCHITECTURE.md` mục 6 và spec mục 5.6):

- Realm ứng dụng (`THI360_KC_REALM`, mặc định `thi-360` ở dev): chứa mọi tài khoản học viên và quản trị viên.
- Realm nền tảng (`THI360_KC_PLATFORM_REALM`, mặc định `master`): chỉ dùng để xác thực tài khoản `PLATFORM_ADMIN`.

Trên máy chủ Keycloak thật (dùng chung với các dự án khác), ứng dụng **không** tự tạo hay sửa cấu hình realm. Người vận hành cần cấu hình tay các bước sau trước khi triển khai:

1. **Trong realm ứng dụng** (`THI360_KC_REALM`, tên cấu hình qua biến môi trường):
   - Bật `Login with email`, tắt `Duplicate emails`, tắt `User registration` và `Forgot password` (quản lý qua ứng dụng).
   - Trong **Realm settings → User profile**, bỏ đánh dấu bắt buộc (`Required`) cho `email`, `firstName`, `lastName` (học viên có thể tạo không có email). Nếu bật `Brute force detection`, đặt `Quick login check = 0 ms` và `Failure factor >= 30` để không chặn nhầm trước giới hạn 5 lần/15 phút của ứng dụng.
   - Đặt **Password Policy**: `length(8) and maxLength(64) and upperCase(1) and lowerCase(1) and digits(1) and specialChars(1) and notUsername`.
   - Tạo client confidential (`THI360_KC_CLIENT_ID`, mặc định `thi360-api`): bật **Client authentication**, **Direct access grants**, **Service accounts roles**; tắt **Standard flow**; cấu hình secret vào `THI360_KC_CLIENT_SECRET`.
   - Trong **Client scopes → `<clientId>-dedicated` → Add mapper → By configuration → Audience** (`oidc-audience-mapper`), đặt **Included Client Audience** = `THI360_KC_CLIENT_ID`, bật **Add to access token**, tắt **Add to ID token** (backend kiểm tra cả `aud` và `azp`).
   - Trong tab **Service account roles** của client trên, gán đúng 3 role của client `realm-management`: `manage-users`, `view-users`, `query-users` (chỉ trong realm ứng dụng; không cấp quyền ở realm khác).
2. **Trong realm nền tảng** (`THI360_KC_PLATFORM_REALM`, mặc định `master`):
   - Tạo realm role `PLATFORM_ADMIN` (`THI360_KC_PLATFORM_ROLE`).
   - Tạo client confidential (`THI360_KC_PLATFORM_CLIENT_ID`, mặc định `thi360-platform`): bật **Client authentication** và **Direct access grants**; tắt **Standard flow** và **Service accounts**; đặt secret vào `THI360_KC_PLATFORM_CLIENT_SECRET` (khuyến nghị đặt `Access Token Lifespan = 300s`).
   - Thêm **Audience** protocol mapper (`oidc-audience-mapper`) cho client trên với **Included Client Audience** = `THI360_KC_PLATFORM_CLIENT_ID`, bật **Add to access token**, tắt **Add to ID token**.
   - Tạo tài khoản khởi tạo (ví dụ `platform`), đặt mật khẩu và gán **duy nhất** realm role `PLATFORM_ADMIN` (không gán role `admin` của Keycloak). Tên đăng nhập tài khoản nền tảng phải nằm trong danh sách tên dành riêng của ứng dụng.
3. **Biến môi trường bắt buộc ở production/staging**: `THI360_KC_URL`, `THI360_KC_REALM`, `THI360_KC_CLIENT_ID`, `THI360_KC_CLIENT_SECRET`, `THI360_KC_PLATFORM_REALM`, `THI360_KC_PLATFORM_ROLE`, `THI360_KC_PLATFORM_CLIENT_ID`, `THI360_KC_PLATFORM_CLIENT_SECRET`, `THI360_CTX_SECRET` (khóa HMAC >= 32 bytes UTF-8; giá trị mặc định trong `.env.example` chỉ áp dụng khi chạy profile `dev` hoặc `test`, khởi động các profile khác sẽ bị từ chối nếu thiếu hoặc ngắn hơn 32 bytes), `THI360_TRUSTED_PROXIES`.
4. **Proxy web & IP thực của người dùng (`THI360_TRUST_REAL_IP`)**: Next.js 16 không còn `request.ip`, và route proxy `/api/[...path]` của cả hai web luôn bỏ qua `X-Forwarded-For` do trình duyệt gửi. Khi triển khai sau nginx, cấu hình `proxy_set_header X-Real-IP $remote_addr;` trên nginx, đặt `THI360_TRUST_REAL_IP=1` cho hai web để proxy ghi đè `X-Forwarded-For` từ `X-Real-IP`, và đưa IP của máy chủ web vào `THI360_TRUSTED_PROXIES` ở backend. Khi các tuyến VNPAY (`/api/v1/public/payments/vnpay/`) được nginx định tuyến thẳng tới `thi360-api` (không đi qua Next.js, xem mục 3 dưới đây), **bắt buộc đưa cả IP/CIDR của nginx biên vào `THI360_TRUSTED_PROXIES`** cùng với IP các máy chủ web Next.js. Không bật `THI360_TRUST_REAL_IP=1` nếu request đi thẳng tới Next.js mà không qua reverse proxy đáng tin cậy.
5. **Bộ đệm header của nginx và phiên tự làm mới**: khi access token hết hạn (5 phút) mà refresh token còn hạn, hai web tự làm mới phiên ở phía máy chủ và trả về bốn cookie mới (`thi360_at`, `thi360_rt`, `thi360_ctx`, `thi360_xsrf`) ngay trên phản hồi của trang hoặc của API. Header của phản hồi đó vượt bộ đệm mặc định 4k/8k của nginx và nginx trả 502 ("upstream sent too big header"). Trong các `location` trỏ tới hai web đặt `proxy_buffer_size 16k; proxy_buffers 8 16k; proxy_busy_buffers_size 32k;`. Cách làm mới này dựa vào việc realm **không** bật "Revoke Refresh Token" (mặc định của Keycloak): nếu bật, một refresh token chỉ dùng được một lần và người dùng có thể bị đăng xuất khi hai web cùng làm mới.
6. **PostgreSQL `pg_trgm` và giới hạn tải lên**: backend dùng extension `pg_trgm` cho tìm kiếm câu hỏi (schema `questionbank`) và tìm kiếm sản phẩm (`commerce.storefront_product`). Migration `CREATE EXTENSION IF NOT EXISTS pg_trgm` tự chạy khi tài khoản ứng dụng đủ quyền (`CREATE` trên database); nếu chính sách DB không cho phép, DBA chạy `CREATE EXTENSION pg_trgm` trước bằng tài khoản quản trị — migration sau đó là no-op. Backend chấp nhận tệp tới 21 MB (`spring.servlet.multipart.max-file-size`); trên nginx của môi trường triển khai đặt `client_max_body_size 22m;` trở lên cho các `location` trỏ tới `thi360-api` để không chặn tệp đính kèm 20 MB trước khi tới backend.

## Triển khai môi trường thật (Production / Staging)

1. **Bắt buộc bật profile `prod`** (`api,prod` cho tiến trình API và `worker,prod` cho tiến trình nền):
   - `E2eProfileGuard` từ chối khởi động ngay ở `BeanFactoryPostProcessor` nếu profile `e2e` được bật cùng `prod` (khóa `/api/v1/test/**` và đồng hồ `MutableClock`; khi tắt `e2e`, `TestApiAbsentSecurityConfig` trả `404` cho `/api/v1/test/**`).
   - `VnpayModeStartupGuard` từ chối khởi động nếu `THI360_VNPAY_MODE` là `MOCK` khi profile `prod` đang bật (ngoài `dev`/`test`/`e2e` cũng chặn `MOCK` nếu có cấu hình `THI360_VNPAY_TMN_CODE` hoặc `THI360_VNPAY_HASH_SECRET`). `MockVnpayPageCondition` tắt `/mock-vnpay` ngoài `(dev | test | e2e) & !prod`, còn `VnpayRealModeStartupGuard` kiểm tra đủ 4 biến cấu hình và định dạng URI (`https` ở `PRODUCTION`, `http`/`https` ở `SANDBOX`).
   - `DevEmailCodeGuard` chặn đặt `THI360_DEV_EMAIL_CODE` trừ khi profile `dev` bật và chỉ đi cùng `api`/`worker`.
   - **Lưu ý:** nếu không bật profile `prod`, các chốt chặn dành riêng cho môi trường thật nói trên không kích hoạt.

2. **Biến môi trường mới (đợt 3):** kiểm tra đầy đủ theo `backend/src/main/resources/application.yml` và `.env.example` (không ghi giá trị bí mật vào file cấu hình được commit):
   - **Token pepper, đăng ký & đặt lại mật khẩu:** `THI360_TOKEN_PEPPER` (bắt buộc ở môi trường thật để ký HMAC mã OTP email và mã đặt lại mật khẩu; ứng dụng không khởi động nếu trống; chỉ profile `dev`, `test`, `e2e` có giá trị mặc định công khai), `THI360_DEV_EMAIL_CODE` (mã OTP 6 số cố định, chỉ dùng cho profile `dev`), `THI360_REGISTER_IP_HOUR_LIMIT` (mặc định `30`), `THI360_REGISTER_IP_DAY_LIMIT` (`100`), `THI360_FORGOT_PER_IP_LIMIT` (`60`), `THI360_RESET_PER_IP_LIMIT` (`60`), `THI360_PASSWORD_RESET_TTL` (`PT1H`), `THI360_PASSWORD_RESET_COOLDOWN` (`PT1M`), `THI360_PASSWORD_RESET_DAILY_LIMIT` (`5`), `THI360_PASSWORD_RESET_DAILY_WINDOW` (`PT24H`), `THI360_PASSWORD_RESET_RECONCILE_GRACE` (`PT5M`).
   - **Email / SMTP & Mailpit:** `THI360_SMTP_HOST`, `THI360_SMTP_PORT`, `THI360_SMTP_USERNAME`, `THI360_SMTP_PASSWORD`, `THI360_SMTP_TLS` (`starttls` | `tls` | `none`, mặc định `starttls`; giá trị khác làm ứng dụng không khởi động), `THI360_MAIL_FROM` (mặc định `Thi360 <no-reply@thi360.local>`), `THI360_MAIL_PRODUCT_NAME` (tên sản phẩm trong tiêu đề, nội dung và chữ ký thư; mặc định `Thi360`). Môi trường `dev` (và E2E) gửi mọi email vào Mailpit (`localhost:1360`, giao diện xem thư http://localhost:8361), không gửi tới địa chỉ thật; profile `test` tắt scheduler gửi thư và dùng `GreenMail` trong bộ nhớ.
   - **Ảnh đại diện & MinIO/S3:** `THI360_AVATAR_BUCKET` (mặc định `thi360-avatars`, tách riêng khỏi `THI360_S3_BUCKET` mặc định `thi360-private`). **Bắt buộc tạo sẵn bucket `thi360-avatars` (cùng bucket `THI360_S3_BUCKET`) ở chế độ riêng tư (private — phục vụ qua backend, không mở public-read) trên kho lưu trữ ở môi trường thật** vì `THI360_S3_AUTO_CREATE_BUCKET` mặc định là `false` ngoài `dev`/`test`.
   - **VNPAY & Thương mại:** `THI360_VNPAY_MODE` (`MOCK` dùng cho `dev`/`test`/`e2e` và chỉ được phép trên bản demo chưa cấu hình `THI360_VNPAY_TMN_CODE`/`THI360_VNPAY_HASH_SECRET` ngoài `prod`; `SANDBOX` hoặc `PRODUCTION` bắt buộc có đủ `THI360_VNPAY_TMN_CODE`, `THI360_VNPAY_HASH_SECRET`, `THI360_VNPAY_PAY_URL`, `THI360_VNPAY_API_URL`), `THI360_VNPAY_MIN_AMOUNT` (mặc định trong mã `10000` đồng theo ngưỡng giao dịch tối thiểu chuẩn của VNPAY, cũng là giá bán tối thiểu khi công bố bài thi/bộ đề; đặt `0` để tắt sàn tối thiểu), `THI360_VNPAY_IPN_ALLOWED_IPS` (danh sách IP hoặc CIDR phân tách bằng dấu phẩy; **để trống sẽ tắt kiểm tra IP — fail-open/cho phép mọi IP**, cú pháp IP/CIDR sai sẽ chặn khởi động), `THI360_VNPAY_PAY_ENCODING` (mặc định `JAVA_URL_ENCODER`), `THI360_VNPAY_PAY_HASH_ALGORITHM` (`HMAC_SHA512`), `THI360_VNPAY_QUERYDR_HASH_ALGORITHM` (`HMAC_SHA512`), `THI360_VNPAY_REFUND_HASH_ALGORITHM` (`HMAC_SHA512`), `THI360_VNPAY_CONNECT_TIMEOUT` (`PT5S`), `THI360_VNPAY_READ_TIMEOUT` (`PT10S`), `THI360_VNPAY_SERVER_IP` (mặc định `127.0.0.1`, đặt thành IP công cộng của máy chủ backend ở môi trường thật), `THI360_VNPAY_REFUND_CODE02_POLICY` (`UNKNOWN`), `THI360_VOUCHER_QUOTE_MINUTE_LIMIT` (`10`), `THI360_VOUCHER_QUOTE_DAY_LIMIT` (`50`), `THI360_COMMERCE_RECONCILIATION_CRON` (`0 0 2 * * *`), `THI360_COMMERCE_RECONCILIATION_ZONE` (`Asia/Ho_Chi_Minh`).
   - **Lượt thi, sinh đề & audio:** `THI360_EXAM_SOLVER_GENERATE_DEADLINE` (`30s`), `THI360_ATTEMPT_GEN_THREADS` (`4`), `THI360_ATTEMPT_GEN_QUEUE_CAPACITY` (`32`), `THI360_ATTEMPT_GEN_MAX_QUEUE_WAIT` (`PT60S`), `THI360_ATTEMPT_STALE_STARTING_AFTER` (`PT2M`), `THI360_ATTEMPT_AUDIO_LEASE_DURATION` (`PT45S`), `THI360_ATTEMPT_AUDIO_SEEK_TOLERANCE` (`PT3S`).
   - **Rate limit & Proxy web:** `THI360_SECURITY_RATELIMIT_EXEMPTIPS` (danh sách IP hoặc CIDR của máy chủ web SSR được miễn ngưỡng 120 req/phút trên `GET`/`HEAD /api/v1/public/**`; không miễn đăng nhập và tải tệp), `THI360_TRUST_REAL_IP` (đặt `1` khi đứng sau nginx đã đặt `proxy_set_header X-Real-IP $remote_addr;`) kết hợp `THI360_TRUSTED_PROXIES` (bao gồm cả IP/CIDR máy chủ web Next.js và nginx biên khi proxy thẳng tuyến VNPAY).
   - **Kênh hỗ trợ web học viên:** `NEXT_PUBLIC_SUPPORT_PHONE`, `NEXT_PUBLIC_SUPPORT_EMAIL`, `NEXT_PUBLIC_SUPPORT_HOURS` (biến `NEXT_PUBLIC_*` được nhúng lúc build `web-student`; để trống sẽ hiện thông báo đang cập nhật).

3. **Cấu hình nginx cho VNPAY IPN & Return:**
   - Bắt buộc định tuyến trực tiếp `location ^~ /api/v1/public/payments/vnpay/` thẳng tới `thi360-api` (**không** đi qua proxy Next.js) để IPN từ VNPAY và URL quay lại trình duyệt tới thẳng backend, giữ nguyên IP nguồn cho `THI360_VNPAY_IPN_ALLOWED_IPS` và tránh timeout/CSRF proxy.
   - **Bắt buộc đưa IP/CIDR của nginx biên vào `THI360_TRUSTED_PROXIES`** (cùng với IP của các máy chủ web Next.js): theo `ClientIpResolver`, nếu `remoteAddr` của nginx không nằm trong `THI360_TRUSTED_PROXIES`, `thi360-api` sẽ bỏ qua `X-Forwarded-For` và dùng chính IP của nginx làm IP khách — khiến `VnpayIpnIpAllowlist` từ chối mọi IPN hợp lệ từ VNPAY với `97 Invalid Checksum` và dồn toàn bộ người dùng quay lại `/vnpay/return` vào chung một hạn mức `120 req/phút`.
   - Khuyến nghị dùng `proxy_set_header X-Forwarded-For $remote_addr;` khi nginx là lớp proxy biên ngoài cùng (hoặc `$proxy_add_x_forwarded_for` nếu phía trước nginx có load balancer tin cậy mà IP của nó cũng nằm trong `THI360_TRUSTED_PROXIES`):
     ```nginx
     location ^~ /api/v1/public/payments/vnpay/ {
         proxy_pass http://thi360_api;
         proxy_set_header Host $host;
         proxy_set_header X-Real-IP $remote_addr;
         proxy_set_header X-Forwarded-For $remote_addr;
         proxy_set_header X-Forwarded-Proto $scheme;
     }
     ```
   - **Lưu ý kết quả đo thực tế (VP-2):** các máy chủ đám mây đặt ngoài Việt Nam có thể bị chặn geo-IP khi gọi chủ động tới `sandbox.vnpayment.vn` (`querydr`/`refund`), trong khi trang thanh toán `pay.vnpay.vn` và luồng IPN gọi từ VNPAY tới miền đích vẫn hoạt động bình thường.

4. **Tiến trình `worker` (profile `worker`):** chạy cùng codebase với `thi360-api` (không mở web server khi chạy chỉ profile `worker`) để thực thi các job nền:
   - **Outbox & thư điện tử:** outbox relay (`OutboxRelay` quét `shared.outbox_event`), gửi thư và dọn thư đã gửi (`MailWorker` trên scheduler riêng `mailTaskScheduler`, giữ chỗ bằng `claim_token`).
   - **Tài khoản & nhật ký:** đối soát tài khoản kẹt `PROVISIONING` (`ProvisioningReconcilerJob`), cấp tài khoản hàng loạt (`ImportBatchProcessorJob`), thu hồi phiên sau khi đặt lại mật khẩu (`PasswordResetRevocationWorker`), dọn ảnh đại diện chờ xóa (`AvatarCleanupWorker`), tạo phân vùng và lưu trữ nhật ký (`AuditPartitionJob`, `AuditArchiveJob`).
   - **Nội dung & bài thi:** dọn tài sản/tệp nhập câu hỏi (`QuestionAssetCleanupJob`, `QuestionImportCleanupJob`), dọn ảnh bìa bài thi mồ côi (`ExamCoverCleanupJob`), dọn job kiểm tra cấu hình hết hạn (`ExamCheckRetentionJob`).
   - **Thương mại & VNPAY:** tự động cấp quyền lại cho đơn `PAID` gặp lỗi (`FulfillmentRetryWorker`), hết hạn đơn `PENDING_PAYMENT` sau tra cứu (`OrderExpiryWorker`), tra cứu giao dịch treo qua VNPAY `querydr` (`QueryDrWorker`), lập báo cáo đối soát cuối ngày lúc 02:00 (`ReconciliationWorker`).
   - **Lượt thi:** dọn lượt `STARTING` quá hạn (`AttemptStaleWorker`), đóng phần/bài thi quá hạn (`AttemptDeadlineWorker`), thử lại sinh phần bị kẹt hoặc lỗi (`PartGenerationRetryWorker`).
   - **Chống chạy trùng:** các job hàng đợi dùng `SELECT ... FOR UPDATE SKIP LOCKED` để nhiều worker không lấy cùng bản ghi; tác vụ đối soát cuối ngày (`ReconciliationService`) dùng `pg_try_advisory_xact_lock`.


## Lệnh thường dùng

| Lệnh | Việc |
|---|---|
| `pnpm lint` / `pnpm typecheck` / `pnpm build` | Kiểm tra và build toàn bộ web |
| `cd backend && ./mvnw verify` | Build và test backend (xuất `backend/target/openapi.json`) |
| `cp backend/target/openapi.json packages/api-client/openapi.json && pnpm --filter @thi360/api-client generate` | Cập nhật `openapi.json` đã commit và sinh lại `packages/api-client/src/schema.d.ts` sau khi đổi API backend |
| `scripts/uniai.sh mine` | Issue đang mở của tôi |
| `scripts/uniai.sh start UNI-123` | Nhận issue, tạo nhánh từ `develop` |
| `scripts/uniai.sh pr UNI-123` | Push, mở PR, chuyển issue sang `in_review` |

## Cấu trúc

```
backend/            Spring Boot (modular monolith)
apps/web-admin/     Next.js, trang quản trị
apps/web-student/   Next.js, trang học viên
apps/android/       app học viên Android (Kotlin + Compose, API 26+; tạo trong đợt app học viên)
apps/ios/           app học viên iOS (Swift + SwiftUI, iOS 17+, XcodeGen; tạo trong đợt app học viên)
packages/ui/        token và thành phần giao diện dùng chung (`@thi360/ui`)
packages/web-kit/   proxy, phiên và tiện ích Next.js dùng chung (`@thi360/web-kit`)
packages/api-client/  client sinh từ OpenAPI
packages/config/    cấu hình TypeScript dùng chung
contracts/mobile/   hợp đồng dùng chung giữa backend, web và hai app (`auth/`, `api/`, `appearance/`, `openapi-mobile.json`; tạo trong đợt app học viên)
e2e/                bộ kiểm thử đầu-cuối Playwright (`@thi360/e2e`)
infra/              Docker Compose, realm Keycloak
docs/               spec, plan
scripts/            công cụ làm việc với UniAI
```
