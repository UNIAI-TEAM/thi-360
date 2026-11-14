# Thi 360

Cổng luyện thi trực tuyến cho kỳ thi đánh giá năng lực HSA và QDA.

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

**4. Đăng nhập thử** tại http://localhost:3000/dang-nhap bằng một tài khoản mẫu bên dưới. Quản trị viên được chuyển sang web admin, học viên tới `/home`.

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
| E2E | `pnpm --filter @thi360/e2e exec playwright install chromium` (một lần), rồi `E2E_USE_BUILT=1 pnpm --filter @thi360/e2e test` | Cần Compose đang chạy và `pnpm build` đã chạy. Playwright tự khởi động backend (`api,worker,dev`) và hai web ở 8080/3000/3001 rồi tắt khi xong; `reuseExistingServer` bật ở máy dev nên cổng đang có server sẽ bị dùng lại, hãy giải phóng 8080/3000/3001 trước |

Kiểm tra không lệch OpenAPI sau `verify`: `diff -u packages/api-client/openapi.json backend/target/openapi.json` rồi `pnpm --filter @thi360/api-client generate && git diff --exit-code packages/api-client` đều phải sạch. Trên Windows có DB dùng chung xem mục trên: `THI360_DB_PORT=54320 SPRING_FLYWAY_OUT_OF_ORDER=true E2E_USE_BUILT=1 pnpm --filter @thi360/e2e test`.

## Đăng nhập dev (tài khoản mẫu)

Khi chạy backend với profile `dev` (`api,worker,dev`), `DevDataInitializer` tự tạo sẵn các tài khoản sau (chỉ ở môi trường dev). Đăng nhập tại http://localhost:3000/dang-nhap (một nơi đăng nhập cho cả web học viên và web admin):

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
4. **Proxy web & IP thực của người dùng (`THI360_TRUST_REAL_IP`)**: Next.js 16 không còn `request.ip`, và route proxy `/api/[...path]` của cả hai web luôn bỏ qua `X-Forwarded-For` do trình duyệt gửi. Khi triển khai sau nginx, cấu hình `proxy_set_header X-Real-IP $remote_addr;` trên nginx, đặt `THI360_TRUST_REAL_IP=1` cho hai web để proxy ghi đè `X-Forwarded-For` từ `X-Real-IP`, và đưa IP của máy chủ web vào `THI360_TRUSTED_PROXIES` ở backend. Không bật `THI360_TRUST_REAL_IP=1` nếu request đi thẳng tới Next.js mà không qua reverse proxy đáng tin cậy.

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
packages/ui/        token và thành phần giao diện dùng chung
packages/api-client/  client sinh từ OpenAPI
packages/config/    cấu hình TypeScript dùng chung
infra/              Docker Compose, realm Keycloak
docs/               spec, plan
scripts/            công cụ làm việc với UniAI
```
