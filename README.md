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
- Node.js 22 trở lên và pnpm 10 (`corepack enable`)
- Docker (có Docker Compose)
- `uniai` CLI và `gh` (để làm việc theo issue, xem `RULES.md` mục 2)

## Chạy lần đầu

```sh
cp .env.example .env                                   # không bắt buộc: mọi biến đều có mặc định cho máy dev
docker compose --env-file .env -f infra/docker-compose.yml up -d
git config core.hooksPath .githooks                     # hook tự thêm "Refs: UNI-nnn" vào commit

cd backend && ./mvnw verify && cd ..                    # build và test backend
pnpm install && pnpm build                              # build hai web
```

Chưa có file `.env` thì bỏ `--env-file .env`.

## Chạy khi phát triển

```sh
cd backend && ./mvnw spring-boot:run                    # API: http://localhost:8080/actuator/health
pnpm dev                                                # web học viên :3000, web admin :3001
```

## Dịch vụ dev (Docker Compose)

| Dịch vụ | Địa chỉ | Ghi chú |
|---|---|---|
| PostgreSQL | `localhost:54360` | cơ sở dữ liệu `thi360`; Keycloak dùng cơ sở dữ liệu `keycloak` riêng |
| Redis | `localhost:63790` | |
| Keycloak | http://localhost:8360 | realm `thi360` được nạp từ `infra/keycloak/thi360-realm.json` |
| MinIO | API `localhost:9360`, bảng điều khiển http://localhost:9361 | |
| Mailpit | SMTP `localhost:1360`, xem thư http://localhost:8361 | bắt mọi email gửi thử |

Tài khoản và mật khẩu mặc định nằm trong `.env.example`, **chỉ dùng cho máy dev**.

## Lệnh thường dùng

| Lệnh | Việc |
|---|---|
| `pnpm lint` / `pnpm typecheck` / `pnpm build` | Kiểm tra và build toàn bộ web |
| `cd backend && ./mvnw verify` | Build và test backend |
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
