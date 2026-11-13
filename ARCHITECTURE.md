# ARCHITECTURE — Thi 360

> Cập nhật: 2026-11-13 · Trạng thái: đề xuất, áp dụng cho đợt 1 (UNI-2537). Mục nào đổi thì sửa file này trong cùng PR.

## 1. Tổng quan

Một backend chia module (modular monolith), chạy hai vai trò từ cùng một codebase, phục vụ ba loại client.

```
 Web học viên (Next.js) ─┐
 Web admin (Next.js) ────┼─ HTTPS / nginx ─► thi360-api (Spring Boot) ◄──► Keycloak (chỉ lo xác thực)
 App Android/iOS (sau) ──┘                        │
                                    ┌─────────────┼─────────────┐
                                PostgreSQL      Redis        MinIO/S3
                              (schema theo    (phiên, cache   (file import,
                               module, có      quyền, rate    file kết quả,
                               bảng outbox)    limit)         ảnh câu hỏi)
                                    ▲
                             thi360-worker (cùng code, profile `worker`)
                             ghi nhật ký, cấp tài khoản hàng loạt, gửi email,
                             về sau: chấm điểm, AI, đối soát VNPAY
```

**Vì sao gộp thay vì microservice như reap:** các luồng chính cần transaction qua nhiều module (thanh toán → cấp quyền → nhật ký; bắt đầu thi → giữ lượt → tạo snapshot). Trong một cơ sở dữ liệu đó là một transaction. Team nhỏ nên không vận hành Eureka, config server, Kafka. Khi cần chịu tải thì chạy thêm bản API và worker.

## 2. Công nghệ

| Lớp | Lựa chọn | Ghi chú |
|---|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Modulith, Spring Security, Spring Data JPA, Flyway, MapStruct, springdoc | Giữ Java để port được mã reap |
| Xác thực | Keycloak, realm `thi360` | Giữ mật khẩu, phiên, bắt đổi mật khẩu; sau này cắm Google. **Không** giữ quyền nghiệp vụ |
| Phân quyền | Bảng `permission`, `role`, `role_permission`, `user_role`, `user_permission` trong schema `identity` | Kiểm tra bằng `@RequirePermission` ở máy chủ |
| Dữ liệu | PostgreSQL 16, Redis 7, MinIO | Mỗi module một schema |
| Bất đồng bộ | Bảng outbox trong PostgreSQL + worker | Chưa dùng Kafka |
| File Excel | Apache POI | Cấp tài khoản hàng loạt, nhập/xuất danh mục |
| Web | Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4, TanStack Query, TanStack Table, react-hook-form + zod, next-intl, lucide-react, dnd-kit | Hai app riêng: `web-admin`, `web-student` |
| UI kit | `packages/ui`: thành phần tự viết trên Radix UI + class-variance-authority, token theo `DESIGN.md` | Cùng cách với `exam-portal` của reap. **Không dùng MUI** |
| Client API | `packages/api-client` sinh từ OpenAPI | Web và mobile dùng chung |
| Mobile (sau) | React Native (Expo) | Chưa làm trong đợt 1 |
| Kiểm thử | JUnit 5, Testcontainers (PostgreSQL, Redis, Keycloak), Vitest, Playwright | |
| Vận hành | Docker Compose (dev), nginx, GitHub Actions, Prometheus + Grafana, Sentry | |

## 3. Cấu trúc thư mục

```
thi-360/
├── PRD.md  ARCHITECTURE.md  DESIGN.md  RULES.md  TASK.md  PROGRESS.md  AGENTS.md
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/vn/unicom/thi360/
│       │   ├── Thi360Application.java
│       │   ├── shared/        lỗi chuẩn, phân trang, idempotency, bảo mật dùng chung, outbox
│       │   ├── identity/      tài khoản, phiên, quyền, vai trò, lớp, cấp hàng loạt
│       │   ├── catalog/       danh mục dùng chung
│       │   └── audit/         nhật ký thao tác
│       ├── main/resources/db/migration/{identity,catalog,audit,shared}/
│       └── test/java/...
├── apps/
│   ├── web-admin/             Next.js, trang quản trị
│   └── web-student/           Next.js, đợt 1 chỉ có khung
├── packages/
│   ├── ui/                    token + thành phần giao diện
│   ├── api-client/            sinh từ OpenAPI, không sửa tay
│   └── config/                eslint, tsconfig, tailwind preset
├── infra/
│   ├── docker-compose.yml     postgres, redis, keycloak, minio, mailpit
│   ├── keycloak/              realm export `thi360`
│   └── nginx/
├── docs/
│   ├── specs/                 đặc tả theo đợt
│   ├── plans/                 kế hoạch, mỗi task là một sub-issue UniAI
│   └── adr/                   quyết định kiến trúc
└── scripts/                   uniai.sh, hook git
```

**Bên trong một module backend**

```
identity/
├── api/            controller + DTO (lớp duy nhất module khác và client nhìn thấy qua HTTP)
├── application/    use case, transaction, kiểm tra quyền
├── domain/         entity, quy tắc nghiệp vụ, sự kiện
├── infrastructure/ repository JPA, client Keycloak, lưu file
└── spi/            interface module khác được phép gọi hoặc cài đặt
```

Module khác chỉ được dùng `spi/` và sự kiện đã công bố. Test `ApplicationModules.verify()` làm hỏng build nếu vi phạm.

## 4. Module và schema

| Module | Schema | Bảng chính | Đợt |
|---|---|---|---|
| `shared` | `shared` | `outbox_event`, `idempotency_key` | 1 |
| `identity` | `identity` | `user_account`, `user_identity`, `permission`, `role`, `role_permission`, `user_role`, `user_permission`, `class_room`, `class_member`, `class_member_history`, `import_batch`, `import_row` | 1 |
| `catalog` | `catalog` | `catalog_group`, `catalog_item`, `combination_version`, `combination_subject` | 1 |
| `audit` | `audit` | `audit_event` (chỉ ghi thêm) | 1 |
| `questionbank`, `exam`, `attempt`, `scoring`, `commerce`, `ai`, `support`, `report` | theo tên | — | sau |

## 5. Luồng dữ liệu

### 5.1 Đăng nhập (web)

```
Trình duyệt ──POST /api/auth/login──► Next.js (route proxy) ──► thi360-api
                                                               │ 1. chuẩn hóa tên đăng nhập, kiểm tra chặn đăng nhập sai (Redis)
                                                               │ 2. đổi tên đăng nhập/mật khẩu lấy token ở Keycloak
                                                               │ 3. đọc identity.user_account: trạng thái, bắt đổi mật khẩu
                                                               │ 4. ghi phiên vào Redis, ghi audit LOGIN
Trình duyệt ◄── cookie HttpOnly (access, refresh) + XSRF ──────┘
```

- Trình duyệt không bao giờ giữ token trong JavaScript.
- Tài khoản `LOCKED` hoặc đã xóa mềm bị từ chối ở bước 3, kể cả khi Keycloak trả token.
- Khóa tài khoản: vô hiệu ở Keycloak, xóa phiên trong Redis, request kế tiếp của phiên cũ nhận 401.

### 5.2 Kiểm tra quyền cho một request quản trị

```
Request ─► CookieAuthFilter (xác minh JWT bằng khóa công khai Keycloak)
        ─► SessionFilter (phiên còn hiệu lực trong Redis?)
        ─► @RequirePermission("USER_LOCK")
              └─ PermissionService.effective(userId)
                    ├─ Redis `perm:{userId}` (nếu có)
                    └─ PostgreSQL: quyền từ vai trò ∪ quyền gán trực tiếp  → ghi lại Redis
        ─► use case ─► PostgreSQL + outbox_event (cùng transaction)
```

- JWT chỉ dùng để biết **ai**. Quyền luôn đọc từ dữ liệu của ứng dụng, nên đổi vai trò có hiệu lực ở request kế tiếp.
- Khi đổi `role_permission`, `user_role`, `user_permission`: xóa khóa Redis của các user liên quan trong cùng use case.
- Vai trò `SUPER_ADMIN` có mọi quyền, không cần liệt kê.

### 5.3 Ghi nhật ký

```
Use case ──(cùng transaction)──► shared.outbox_event
thi360-worker ── đọc outbox (SELECT … FOR UPDATE SKIP LOCKED) ──► audit.audit_event
```

Thao tác nghiệp vụ và sự kiện nhật ký thành công hoặc thất bại cùng nhau. `audit_event` có trigger chặn `UPDATE` và `DELETE`.

### 5.4 Cấp tài khoản hàng loạt

```
Upload file ─► lưu MinIO ─► import_batch(UPLOADED) ─► kiểm tra từng dòng ─► import_row + lỗi theo dòng
Admin xem trước ─► xác nhận (chỉ khi không còn lỗi dữ liệu) ─► import_batch(CONFIRMED)
Worker ─► tạo từng tài khoản (Keycloak + user_account), ghi kết quả từng dòng ─► file kết quả trên MinIO
```

Mỗi đợt có khóa idempotency; mỗi dòng có trạng thái riêng, nên chạy lại chỉ xử lý dòng chưa xong.

### 5.5 Danh mục cấp dữ liệu cho module khác

```
Module khác / web ─► GET /api/v1/catalog/options?group=SUBJECT ─► chỉ giá trị đang hoạt động của nhóm đang hoạt động (cache Redis)
Module khác ─► cài đặt CatalogUsageProvider ─► catalog hỏi "giá trị này đang được dùng ở đâu" trước khi cho ngừng hoặc xóa
```

## 6. Đồng bộ với Keycloak

`identity.user_account` là nguồn dữ liệu nghiệp vụ; `id` trùng với `sub` của Keycloak.

- **Tạo:** ghi `user_account` trạng thái `PROVISIONING` → tạo user ở Keycloak → cập nhật trạng thái. Job đối soát xử lý bản ghi kẹt ở `PROVISIONING`.
- **Khóa / xóa mềm:** đổi trạng thái trong `user_account`, vô hiệu user ở Keycloak, thu hồi phiên.
- **Không** truy vấn trực tiếp bảng của Keycloak (reap làm vậy, không port sang).

## 7. Quyết định kiến trúc

| # | Quyết định | Lý do |
|---|---|---|
| A1 | Modular monolith + worker, không microservice | Transaction qua module, team nhỏ |
| A2 | Keycloak chỉ xác thực; quyền do ứng dụng giữ | Cần quyền chi tiết, vai trò tự tạo, hiệu lực tức thì |
| A3 | Outbox trong PostgreSQL thay Kafka | Đủ cho tải ban đầu, ít hạ tầng |
| A4 | Một UI kit Tailwind dùng cho cả admin và học viên | Một phong cách theo `DESIGN.md`; màn của reap (MUI) chỉ port luồng và logic |
| A5 | Không đa tenant | Thi 360 là một sản phẩm, một đơn vị vận hành |
| A6 | Bảng `user_identity` có từ đầu dù chưa bật Google | Bật Google sau không phải đổi mô hình dữ liệu |

## 8. Bảo mật

- Mọi endpoint dưới `/api/v1/admin/**` bắt buộc có `@RequirePermission`; có test quét và làm hỏng build nếu thiếu.
- Cookie `HttpOnly`, `Secure`, `SameSite=Lax`; kiểm tra XSRF cho request ghi.
- Giới hạn tần suất cho đăng nhập, reset mật khẩu, upload file.
- Không ghi mật khẩu, token, link reset vào log hay nhật ký.
- Bí mật nằm trong biến môi trường, không commit (`.env` nằm trong `.gitignore`, có `.env.example`).
