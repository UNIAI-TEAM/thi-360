# ARCHITECTURE — Thi 360

> Cập nhật: 2026-11-22 · Trạng thái: đã cập nhật hết đợt 3 (QA UNI-2744). Mục nào đổi thì sửa file này trong cùng PR.

## 1. Tổng quan

Một backend chia module (modular monolith), chạy hai vai trò từ cùng một codebase, phục vụ ba loại client.

```
 Web học viên (Next.js) ─┐   thi360.vn: landing, storefront, thanh toán, đăng nhập duy nhất, phòng thi & kết quả
 Web admin (Next.js) ────┼─ HTTPS / nginx ─► thi360-api (Spring Boot) ◄──► Keycloak (chỉ lo xác thực)
 App Android/iOS (sau) ──┘                        │
                                    ┌─────────────┼─────────────┐
                                PostgreSQL      Redis        MinIO/S3
                              (schema theo    (phiên, cache   (tệp import,
                               module, có      quyền, rate    ảnh/audio câu hỏi,
                               bảng outbox)    limit)         ảnh bìa, avatar)
                                    ▲
                             thi360-worker (cùng code, profile `worker`)
                             outbox relay, gửi thư, cấp tài khoản hàng loạt, nhật ký,
                             dọn tài sản/ảnh bìa/avatar, hết hạn đơn/querydr/đối soát VNPAY,
                             dọn/đóng lượt thi quá hạn & thử lại sinh đề
```

**Vì sao gộp thay vì microservice như reap:** các luồng chính cần transaction qua nhiều module (thanh toán → cấp quyền → nhật ký; bắt đầu thi → giữ lượt → tạo snapshot). Trong một cơ sở dữ liệu đó là một transaction. Team nhỏ nên không vận hành Eureka, config server, Kafka. Khi cần chịu tải thì chạy thêm bản API và worker.

## 2. Công nghệ

| Lớp | Lựa chọn | Ghi chú |
|---|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Modulith, Spring Security, Spring Data JPA, Flyway, MapStruct, springdoc | Giữ Java để port được mã reap |
| Xác thực | Keycloak, realm `thi-360` | Giữ mật khẩu, phiên, bắt đổi mật khẩu; sau này cắm Google. **Không** giữ quyền nghiệp vụ |
| Phân quyền | Bảng `permission`, `role`, `role_permission`, `user_role`, `user_permission` trong schema `identity` | Kiểm tra bằng `@RequirePermission` ở máy chủ |
| Dữ liệu | PostgreSQL 16, Redis 7, MinIO | Mỗi module một schema |
| Bất đồng bộ | Bảng outbox trong PostgreSQL + worker | Chưa dùng Kafka |
| Gửi thư | `spring-boot-starter-mail`, `spring-boot-starter-thymeleaf` | Hàng thư `shared.email_message` + mẫu `vi`/`en` trên scheduler riêng |
| Ảnh đại diện | `net.coobird:thumbnailator` | Chuẩn hóa ảnh đại diện PNG 256×256px, lưu ở bucket riêng (`THI360_AVATAR_BUCKET`) |
| File Excel | Apache POI | Cấp tài khoản hàng loạt, nhập/xuất danh mục, nhập câu hỏi |
| Đọc khung MP3 | javazoom:jlayer (`1.0.1`) | Đọc khung MPEG để đo thời lượng tệp nghe MP3 trên máy chủ mà không giải mã âm thanh hay tạo tệp tạm (~105 KB, không phụ thuộc bắc cầu) |
| Web | Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4, TanStack Query, TanStack Table, react-hook-form + zod, next-intl, lucide-react, dnd-kit, `@tiptap/react` | Hai app riêng: `web-admin` (soạn nội dung/câu hỏi bằng Tiptap + MathLive), `web-student` (trình soạn bài tự luận bằng Tiptap) |
| UI kit | `packages/ui`: thành phần tự viết trên Radix UI + class-variance-authority, token theo `DESIGN.md`; `@thi360/ui/question` & `@thi360/ui/rich-text` (`katex` + `mhchem`) | Dùng chung giữa `web-admin` và `web-student`, **không dùng MUI** |
| Web kit | `packages/web-kit`: proxy `/api/[...path]`, ký/đọc cookie `thi360_ctx`, làm mới phiên, kiểm tra `callbackUrl`, cookie tùy chọn, `pickName` | Chia sẻ hạ tầng Next.js giữa hai web |
| Client API | `packages/api-client` sinh từ OpenAPI | Web và mobile dùng chung |
| Mobile (sau) | React Native (Expo) | Chưa làm trong đợt 3 |
| Kiểm thử | JUnit 5, Testcontainers (PostgreSQL, Redis, Keycloak, MinIO), GreenMail, Vitest, Playwright (`e2e/`) | |
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
│       │   ├── shared/        lỗi chuẩn, phân trang, idempotency, bảo mật dùng chung, outbox, appearance/, mail/, testapi/ (chỉ profile e2e)
│       │   ├── identity/      tài khoản, đăng ký/xác minh email, quên/đặt lại mật khẩu, ảnh đại diện, phiên, quyền, vai trò, cấp hàng loạt
│       │   ├── catalog/       danh mục dùng chung, tổ hợp, cấu trúc kiến thức
│       │   ├── classroom/     lớp học, thành viên và lịch sử lớp
│       │   ├── audit/         nhật ký thao tác bất biến
│       │   ├── questionbank/  ngân hàng câu hỏi 8 dạng, phiên bản, tài sản (ảnh/audio/tệp), nhập Excel
│       │   ├── exam/          9 kỳ thi, mẫu, bài thi, kiểm tra sâu, phiên bản công bố, thông tin bán/ảnh bìa, bộ giải sinh đề
│       │   ├── commerce/      bảng chiếu storefront, khuyến mãi/voucher, bộ đề, đơn hàng, thanh toán VNPAY, hoàn tiền, đối soát, quyền dùng
│       │   └── attempt/       chuẩn bị, bắt đầu, sinh đề nền, phiên làm bài duy nhất, audio giới hạn lượt, lưu câu trả lời, chấm tự động & tự luận, kết quả/xem lại
│       ├── main/resources/db/migration/{shared,identity,catalog,classroom,audit,questionbank,exam,commerce,attempt}/
│       └── test/java/...
├── apps/
│   ├── web-admin/             Next.js, trang quản trị
│   └── web-student/           Next.js, đầy đủ trang công khai, mua bài, làm bài, kết quả, lịch sử, hồ sơ & tài khoản
├── packages/
│   ├── ui/                    token, thành phần giao diện, `@thi360/ui/question`, `@thi360/ui/rich-text`
│   ├── web-kit/               proxy, cookie `thi360_ctx`, làm mới phiên, điều hướng, `pickName` dùng chung cho hai web
│   ├── api-client/            sinh từ OpenAPI, không sửa tay
│   └── config/                eslint, tsconfig, tailwind preset
├── e2e/                       kiểm thử đầu-cuối Playwright (`@thi360/e2e`)
├── infra/
│   ├── docker-compose.yml     postgres, redis, keycloak, minio, mailpit
│   ├── keycloak/              realm export `thi-360`
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
| `shared` | `shared` | `outbox_event`, `idempotency_key`, `system_setting` (cấu hình giao diện), `email_message` (hàng thư) | 1, 3 |
| `identity` | `identity` | Tài khoản & phân quyền: `user_account`, `user_identity`, `platform_admin`, `permission`, `role`, `role_permission`, `user_role`, `user_permission`; cấp hàng loạt: `import_batch`, `import_row`; đăng ký, đặt lại mật khẩu & ảnh đại diện: `email_code`, `password_reset_token`, `user_avatar`, `avatar_object_cleanup` | 1, 3 |
| `catalog` | `catalog` | `catalog_group`, `catalog_item`, `catalog_group_ref`, `catalog_item_ref`, `combination_version`, `combination_subject`, `catalog_import_preview`, `catalog_revision` | 1, 2 |
| `classroom` | `classroom` | `class_room`, `class_member`, `class_member_history` (module và schema tách riêng) | 1 |
| `audit` | `audit` | `audit_event` (chỉ ghi thêm, phân vùng theo quý), `audit_event_archive` (phân vùng theo năm) | 1 |
| `questionbank` | `questionbank` | Câu hỏi và phiên bản: `question`, `question_version`, `question_knowledge`, `question_tag`, `question_source_code_claim`; độ mới nguồn cho kiểm tra bài thi: `question_source_revision`; nơi dùng: `question_usage_ledger`; tài sản (ảnh, audio, tệp đính kèm, kiểm chữ ký tệp, giới hạn kích thước và tần suất): `question_asset`, `question_asset_ref`, `question_asset_pending_delete`; nhập từ Excel: `question_import`, `question_import_upload` | 2 |
| `exam` | `exam` | Bài thi và cấu trúc: `exam_kind_profile`, `exam_template`, `exam`, `exam_structure`, `exam_block_ref`; kiểm tra và kiểm tra sâu: `exam_check_run`, `exam_check_job`, `exam_check_unit`; phiên bản công bố: `exam_version`; thông tin bán & ảnh bìa: `exam_listing`, `exam_cover_asset`. Chỉ dùng `questionbank/spi` (`QuestionSourcePort`, `QuestionAttachmentPort`, `HtmlContentSanitizer`; `ExamQuestionUsageProvider` cài `QuestionUsageProvider`) | 2, 3 |
| `commerce` | `commerce` | Bảng chiếu & bộ đề: `storefront_product`, `bundle`, `bundle_item`, `bundle_version`; khuyến mãi: `promotion`, `promotion_scope_item`, `promotion_audience`, `promotion_redemption`, `promotion_change_log`; đơn hàng, thanh toán, hoàn tiền & đối soát: `purchase_order`, `purchase_order_item`, `payment`, `payment_event`, `order_history`, `refund`, `reconciliation_report`; quyền sử dụng: `entitlement`, `entitlement_grant`, `entitlement_usage`. Cung cấp `commerce/spi` (`EntitlementPort`, `AttemptStatusProvider`); dùng `exam/spi`, `catalog/spi`, `identity/spi` | 3 |
| `attempt` | `attempt` | Lượt thi, phần, câu và nhật ký lượt: `attempt`, `attempt_part`, `attempt_item`, `attempt_event`; audio giới hạn lượt: `audio_asset_duration`, `attempt_audio_play`; hàng chờ chấm tự luận: `essay_grade`. Dùng `commerce/spi`, `exam/spi`, `questionbank/spi`, `identity/spi`, `catalog/spi` | 3 |
| `ai`, `support`, `report` | theo tên | — | sau |

## 5. Luồng dữ liệu

### 5.1 Đăng nhập (web)

```
Trình duyệt ──POST /api/auth/login──► Next.js (route proxy) ──► thi360-api
                                                               │ 1. chuẩn hóa tên đăng nhập, kiểm tra chặn đăng nhập sai (Redis)
                                                               │ 2. đổi tên đăng nhập/mật khẩu lấy token ở Keycloak
                                                               │ 3. đọc identity.user_account: trạng thái, bắt đổi mật khẩu
                                                               │ 4. ghi phiên vào Redis, ghi audit LOGIN
Trình duyệt ◄── cookie HttpOnly (access, refresh) + XSRF + redirectTo ─┘
```

- Trình duyệt không bao giờ giữ token trong JavaScript.
- **Một nơi đăng nhập cho cả hai web** (spec mục 15): biểu mẫu đăng nhập chỉ có ở web học viên (tên miền chính). Cookie đặt cho tên miền cha (`THI360_COOKIE_DOMAIN`, ví dụ `.thi360.vn`) nên web admin (`admin.thi360.vn`) dùng chung phiên.
- Máy chủ trả `redirectTo`: `callbackUrl` nếu hợp lệ (đường dẫn tương đối hoặc tên miền trong danh sách cho phép), nếu không thì theo trường `access` của vai trò: có vai trò `MANAGE` → web admin, còn lại → `/home` của web học viên.
- Web admin không có trang đăng nhập: chưa đăng nhập thì chuyển về trang đăng nhập kèm `callbackUrl`; thiếu quyền thì hiện trang 403.
- Tài khoản `INACTIVE` hoặc đã xóa mềm bị từ chối ở bước 3, kể cả khi Keycloak trả token.
- Ngừng hoạt động tài khoản: vô hiệu ở Keycloak, xóa phiên trong Redis, request kế tiếp của phiên cũ nhận 401.

### 5.2 Kiểm tra quyền cho một request quản trị

```
Request ─► CookieAuthFilter (xác minh JWT bằng khóa công khai Keycloak)
        ─► SessionFilter: một lệnh MGET tới Redis lấy cả phiên lẫn authz:{userId}
              └─ authz:{userId} = { access, dãy bit quyền, ver }   (chưa có thì dựng bằng một câu SQL)
        ─► kiểm tra cổng quản trị: access == MANAGE
        ─► @RequirePermission("USER.MODIFY"): thử một bit trong dãy bit quyền
        ─► use case ─► PostgreSQL + outbox_event (cùng transaction)
```

- JWT chỉ dùng để biết **ai**. Quyền và `access` luôn đọc từ ảnh chụp của ứng dụng, không nằm trong token.
- Mỗi request thông thường: **một lần gọi Redis, không truy vấn cơ sở dữ liệu** cho phần xác thực và phân quyền.
- Khi đổi phân quyền, use case xóa `authz:{userId}` của các user liên quan, nên thay đổi có hiệu lực ở request kế tiếp (spec mục 5.7).
- `PLATFORM_ADMIN` (tài khoản realm `master`) có mọi quyền (dãy bit toàn 1) và không có trong danh sách user.
- Middleware của hai web dùng cookie `thi360_ctx` có chữ ký để điều hướng mà không gọi backend; cookie này không thay cho kiểm tra ở API.

### 5.3 Ghi nhật ký

```
Use case ──(cùng transaction)──► shared.outbox_event
thi360-worker ── đọc outbox (SELECT … FOR UPDATE SKIP LOCKED) ──► audit.audit_event
```

Thao tác nghiệp vụ và sự kiện nhật ký thành công hoặc thất bại cùng nhau. `audit_event` có trigger chặn `UPDATE` và `DELETE`.

**Phân vùng và lưu trữ:** `audit_event` phân vùng theo quý trên `occurred_at`; job tạo sẵn phân vùng quý kế tiếp. Phân vùng cũ hơn ngưỡng (mặc định 8 quý) được job lưu trữ chép sang `audit_event_archive` (phân vùng theo năm), đối chiếu số dòng rồi tách bỏ. Nhật ký lưu vĩnh viễn.

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

### 5.6 Giao diện toàn hệ thống

```
Người có APPEARANCE.MODIFY ─► PUT /api/v1/admin/appearance ─► shared.system_setting('appearance') + audit, xóa cache
Mọi trang (kể cả đăng nhập, web học viên) ─► GET /api/v1/appearance (công khai, cache)
                                          ─► layout phía máy chủ của Next.js chèn biến CSS vào <html> ─► không nháy màu
```

Token trong `packages/ui` đều là biến CSS, nên đổi cấu hình không cần build lại.

### 5.7 Thương mại, VNPAY & Cấp quyền

- **Bảng chiếu storefront & idempotency:** khi bài thi công bố hoặc ngừng bán (`ExamPublished` / `ExamDiscontinued`), `OutboxRelay` đẩy sự kiện tới `StorefrontEventHandler` để cập nhật bảng chiếu `commerce.storefront_product` (đọc từ `PublishedExamPort.findServing` với `projection_hash`, không tin nội dung thô từ payload) và cập nhật trạng thái bộ đề (`UNAVAILABLE` nếu có bài ngừng bán). Các lệnh tạo đơn và tạo hoàn tiền dùng bean `IdempotentCommand` qua bảng `shared.idempotency_key`.
- **Hai transaction tách nhau khi thanh toán:**
  - *Transaction 1* (`PaymentOutcomeApplier` trong `VnpayIpnService` hoặc luồng đơn 0 đồng ở `OrderService`): khóa hàng theo thứ tự toàn cục `purchase_order (ORDER BY id ASC) -> payment -> promotion`, áp dụng kết quả thanh toán (chuyển đơn sang `PAID`, xác nhận `promotion_redemption` sang `CONFIRMED`) rồi commit.
  - *Transaction 2* (`FulfillmentService.fulfill` với `PROPAGATION_REQUIRES_NEW`): chạy sau khi transaction 1 commit để cấp `entitlement` / `entitlement_grant` (FIFO theo `seq`) và chuyển đơn sang `FULFILLED`. Lỗi cấp quyền không làm mất trạng thái `PAID` hay khiến IPN trả lỗi; `FulfillmentRetryWorker` tự thử lại trong cửa sổ 30 phút (đơn vẫn ở `PAID` quá 30 phút được ghi nhận để vận hành rà soát/cấp quyền tay hoặc hoàn tiền).
- **Ma trận đếm BS 14 (khoản được tính):** chỉ mục duy nhất `uq_payment_counted_per_order` bảo đảm mỗi đơn chỉ có tối đa một `payment` `SUCCEEDED` với `is_duplicate = false`. Các ca thu thừa (`AFTER_COUNTED` / `REJECTED_AFTER_RECONCILE`), tiền về muộn trên lần đã `FAILED` (`late_success_review = PENDING`), đơn đã thay thế (`REPLACED`) và giao dịch treo `UNKNOWN` được ghi nhận nguyên trạng để `QueryDrWorker`, `OrderExpiryWorker`, `ReconciliationWorker` và màn quản trị xử lý đối soát/hoàn tiền.

### 5.8 Lượt thi, Sinh đề nền, Audio & Chấm điểm

- **Bắt đầu lượt & sinh đề nền (`AttemptStartService`):** giữ lượt trong Tx1 (`STARTING`, khóa `entitlement`), sinh đề ngoài database transaction qua `GenerationExecutor` (gọi `PaperSolverPort` trong hạn `THI360_EXAM_SOLVER_GENERATE_DEADLINE` và kiểm tra tệp audio), rồi lưu đề + đánh dấu dùng lượt trong Tx2 (`IN_PROGRESS`). Lỗi sinh đề hoặc quá hạn sẽ chuyển `ABORTED` và nhả lượt (`RELEASED`), hoặc được `AttemptStaleWorker` dọn sau `stale-starting-after`. Với phần chọn sau (`AWAITING_CHOICE`), `AttemptChoiceService` sinh bất đồng bộ (`GENERATING` → `OPEN`) và `PartGenerationRetryWorker` tự động thử lại khi kẹt/lỗi.
- **Phiên làm bài duy nhất & điều hướng:** `EditSessionService` (cài đặt `AttemptSessionChecker`) giữ khóa phiên soạn duy nhất (`edit_session_id`), hỗ trợ tiếp quản thiết bị (`claimSession`, trả `409 ATTEMPT_OPEN_ELSEWHERE` cho tab mới chưa tiếp quản và `409 ATTEMPT_SESSION_REVOKED` cho tab/thiết bị cũ bị thu hồi). `AttemptNavigationPolicy` quyết định chế độ điều hướng: `THPTQG` (dùng thời lượng chung hoặc 1 phần) mở `FREE`, mọi kỳ thi khác chạy `SEQUENTIAL`. Ở profile `e2e`, `TestClockController` điều khiển `MutableClock` để kiểm thử đóng bài/phần theo hạn nộp.
- **Audio giới hạn lượt & Chấm điểm:** `AttemptAudioService` khóa dòng `attempt_audio_play` để giới hạn tối đa 2 lần nghe, đo thời lượng MP3 bằng `javazoom:jlayer` lưu ở `audio_asset_duration`, cấp lease (`attempt.audio.lease-duration`) và kiểm chống tua theo `seek-tolerance`. Khi nộp bài hoặc `AttemptDeadlineWorker` đóng quá hạn, `AttemptGradingService` chạy trong `REQUIRES_NEW` qua `ItemGrader` + `ExactRational` + `ScoreAggregator`: câu tự động ra `CORRECT`/`PARTIAL`/`WRONG`/`BLANK`, câu Tự luận giữ `PENDING_REVIEW` đưa lượt sang `PENDING_REVIEW` (hoặc `SCORED` nếu không còn câu chờ chấm; lỗi ghi `GRADING_ERROR`). Hàng chờ chấm ẩn danh (`EssayGradingService` + `essay_grade`) giấu danh tính học viên; `AttemptSegmentResultBuilder` và `ScoreAggregator` tính điểm từng phân đoạn cho H-SCA/V-SAT và điểm luyện tập + 2 tiểu mục Tư duy khoa học cho V-ACT.

## 6. Đồng bộ với Keycloak

Keycloak có hai realm:

| Realm | Chứa | Ứng dụng làm gì với nó |
|---|---|---|
| `thi-360` | Mọi tài khoản Thi 360 (học viên, quản trị viên) | Tạo, vô hiệu, xóa user qua service account; xác thực đăng nhập |
| `master` | Chỉ tài khoản `PLATFORM_ADMIN` (realm role `PLATFORM_ADMIN`) | Chỉ xác thực đăng nhập. Không tạo, không sửa |

Backend chấp nhận token của cả hai nguồn phát hành; token của `master` phải có role `PLATFORM_ADMIN`. Khi đăng nhập, máy chủ chọn realm theo việc tên đăng nhập có trong `user_account` hay không (spec mục 15.1a).

`identity.user_account` là nguồn dữ liệu nghiệp vụ của realm `thi-360`; `id` trùng với `sub` của Keycloak. `PLATFORM_ADMIN` không có dòng trong `user_account`.

- **Tạo:** ghi `user_account` trạng thái `PROVISIONING` → tạo user ở Keycloak → cập nhật trạng thái. Job đối soát xử lý bản ghi kẹt ở `PROVISIONING`.
- **Ngừng hoạt động:** đổi trạng thái trong `user_account`, vô hiệu user ở Keycloak, thu hồi phiên.
- **Xóa mềm** (chỉ khi không có giao dịch): đặt `deleted_at`, **xóa user ở Keycloak** để giải phóng tên đăng nhập và email. Tính duy nhất trong `user_account` dùng chỉ mục có điều kiện `WHERE deleted_at IS NULL`.
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
| A7 | Giao diện là cấu hình toàn hệ thống lưu ở máy chủ, áp bằng biến CSS | Giữ khả năng tùy chỉnh như reap mà không phụ thuộc MUI |
| A8 | Nhật ký phân vùng theo quý, lưu trữ theo năm | Lưu vĩnh viễn nhưng bảng chính vẫn gọn |
| A10 | Hai realm: `thi-360` cho mọi tài khoản ứng dụng, `master` chỉ cho `PLATFORM_ADMIN` | Người khởi tạo tách khỏi dữ liệu người dùng; đánh đổi là backend phải xác thực với hai realm |
| A9 | Hai web trên hai tên miền, một nơi đăng nhập, cookie phiên dùng chung tên miền cha | Người dùng đăng nhập một lần; đánh đổi là hai web không còn tách phiên, bù bằng `HttpOnly`, XSRF, kiểm tra `Origin` cho API quản trị, CSP |

## 8. Bảo mật

- Mọi endpoint dưới `/api/v1/admin/**` bắt buộc có `@RequirePermission`; có test quét và làm hỏng build nếu thiếu.
- Cookie `HttpOnly`, `Secure`, `SameSite=Lax`; kiểm tra XSRF cho request ghi.
- **Chống lộ đáp án, nguồn đề & IDOR:**
  - `ForbiddenKeysAssert` quét đệ quy payload JSON lúc làm bài (`correct`, `answers`, `pairs`, `scoreTable`, `solution`, `gradingGuide`, `rubric`, `sampleAnswer`, `statements[].points`, `statements[].answer`, MAPID, độ khó…) và trên bảng chiếu storefront (`commerce.storefront_product`).
  - Mọi endpoint `/api/v1/me/**` (đơn hàng, quyền sử dụng, lượt thi, kết quả, xem lại, tài sản) kiểm tra quyền sở hữu trước và trả `404` cho cả tài nguyên không tồn tại lẫn thuộc người dùng khác; tải ảnh lời giải (`/api/v1/me/attempts/{attemptId}/solution-assets/{assetId}`) chỉ mở khi `resultView == FULL_SOLUTION` và lượt ở trạng thái `SCORED` (hoặc `PENDING_REVIEW` đang `regrading`).
- **Giới hạn tần suất (`RateLimitFilter` & `VoucherQuoteRateLimiter`):** áp dụng theo IP trên Redis cho đăng nhập, tải tệp nhập Excel, `GET`/`HEAD /api/v1/public/**` (120 req/phút; miễn cho IP/CIDR trong `THI360_SECURITY_RATELIMIT_EXEMPTIPS`; luôn miễn `/api/v1/public/payments/vnpay/ipn` để không mất IPN), cửa sổ trượt đăng ký (`THI360_REGISTER_IP_HOUR_LIMIT` / `THI360_REGISTER_IP_DAY_LIMIT`), quên/đặt lại mật khẩu (`THI360_FORGOT_PER_IP_LIMIT` / `THI360_RESET_PER_IP_LIMIT`), và giới hạn báo giá kèm mã voucher theo tài khoản trên Redis (`VoucherQuoteRateLimiter`: `THI360_VOUCHER_QUOTE_MINUTE_LIMIT` / `THI360_VOUCHER_QUOTE_DAY_LIMIT`).
- `callbackUrl` sau đăng nhập chỉ được là đường dẫn tương đối hoặc URL thuộc `THI360_WEB_URL`, `THI360_ADMIN_URL`; trường hợp khác bị bỏ qua (chống chuyển hướng tới trang giả mạo).
- API quản trị (`/api/v1/admin/**`) chỉ nhận request ghi có `Origin` là `THI360_ADMIN_URL`.
- **Băm mã nhạy cảm & chốt chặn môi trường thật:**
  - Mã OTP xác minh email và mã đặt lại mật khẩu chỉ lưu dạng HMAC-SHA256 dùng pepper phía máy chủ (`THI360_TOKEN_PEPPER` qua `TokenPepperProperties`, bắt buộc ngoài `dev`/`test`/`e2e`); không ghi mật khẩu, token, link reset vào log hay nhật ký.
  - `/api/v1/test/**` chỉ đăng ký khi bật profile `e2e` (tắt thì `TestApiAbsentSecurityConfig` trả `404` trước chuỗi xác thực) và `E2eProfileGuard` chặn khởi động nếu `e2e` đi cùng `prod` (lưu ý `E2eProfileGuard` và `VnpayModeStartupGuard` chỉ kích hoạt chốt chặn môi trường thật khi profile `prod` được bật).
  - Trang giả lập `/mock-vnpay` chỉ mở khi `THI360_VNPAY_MODE=MOCK` trong `(dev | test | e2e) & !prod` (`MockVnpayPageCondition`); `VnpayModeStartupGuard`, `VnpayRealModeStartupGuard` và `DevEmailCodeGuard` chặn cấu hình thử nghiệm lọt sang môi trường thật (khi bật profile `prod`).
- Bí mật nằm trong biến môi trường, không commit (`.env` nằm trong `.gitignore`, có `.env.example`).
