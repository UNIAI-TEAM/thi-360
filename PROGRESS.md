# PROGRESS — Thi 360

> Cập nhật mỗi lần dừng việc. Trạng thái issue sống nằm trên UniAI; file này ghi **đã làm gì, vướng gì, làm gì tiếp**.

## Trạng thái hiện tại (2026-11-13)

**Giai đoạn:** đợt 1. **T01 (UNI-2538) đã dựng xong và push** lên nhánh `feature/UNI-2538-khoi-tao-monorepo-ci-moi-truong-dev`; chưa mở PR. Đang dừng chờ M.Trường.

**Bước tiếp theo**

1. M.Trường giao một phiên Advisor chạy `zone17-cook-plan` với plan `docs/plans/2026-11-13-base-port-pbi-01-02.md`. Plan đã có đủ cấu hình đợt chạy (Phần A), quy ước kỹ thuật (Phần B), sóng thực thi (Phần C) và hợp đồng từng task (Phần D).
2. Bước 0 của Advisor: tạo `integration/dot-1` từ đầu nhánh này (đã gồm T01 và toàn bộ tài liệu).
3. Sóng 1: T02 (UNI-2539) và T06 (UNI-2543) chạy song song.

## Đã làm

### 2026-11-13 — Chỉnh menu và bổ sung phân quyền

- Spec §5.2 viết lại (một menu Phân quyền, bốn tab), thêm §5.8 Người dùng theo quyền và §5.9 Gán hàng loạt; §7 Cấp hàng loạt thành nút trong màn Tài khoản; §13 Tùy chỉnh giao diện chuyển ra menu chính.
- Plan: T04 thêm ba endpoint (`/permissions/{id}/users`, `/authz/bulk/preview`, `/authz/bulk`), T08 viết lại, T06/T09/T11/T17 chỉnh menu và lối vào.
- Bản thiết kế màn hình (artifact) cập nhật theo.

### 2026-11-13 — Plan triển khai chi tiết

- Viết lại `docs/plans/2026-11-13-base-port-pbi-01-02.md` thành plan tự đủ cho phiên Advisor: nhánh tích hợp `integration/dot-1`, phạm vi ủy quyền, trọng số 100 điểm, thư viện và quy ước chốt sẵn, bảng `bit_index` của quyền, hợp đồng T02–T18.
- Bổ sung spec: đăng nhập một nơi (mục 15), trường `access` của vai trò và hai realm Keycloak (mục 5.6), cách máy chủ xử lý phân quyền (mục 5.7).
- Gộp nhánh tài liệu vào nhánh này để làm gốc cho nhánh tích hợp.

### 2026-11-13 — T01 khởi tạo monorepo, CI, môi trường dev (UNI-2538)

- Monorepo pnpm + Turborepo: `backend/`, `apps/web-admin`, `apps/web-student`, `packages/{ui,api-client,config}`, `infra/`.
- Backend Spring Boot 3.5.16, Java 21, Maven wrapper, chỉ có health check (module và cơ sở dữ liệu thuộc T02).
- Hai web Next.js 16.2.6 + Tailwind 4, dùng token trong `packages/ui/src/styles/tokens.css`, font Be Vietnam Pro.
- `infra/docker-compose.yml`: PostgreSQL 16, Redis 7, Keycloak 26.4 (tự nạp realm `thi360`), MinIO, Mailpit. Cổng lệch khỏi cổng chuẩn (54360, 63790, 8360, 9360/9361, 1360/8361).
- CI: `backend.yml`, `web.yml`, `uniai-link.yml`; mẫu PR.
- `scripts/uniai.sh` và hook `prepare-commit-msg` chỉnh từ UniWork (chạy được cả khi máy chỉ có `python`).
- **Đã chạy thật trên máy dev (Windows):**
  - `cd backend && ./mvnw -B -ntp verify` → 1 test xanh, BUILD SUCCESS.
  - `pnpm install`, `pnpm lint`, `pnpm typecheck`, `pnpm build` → xanh; CSS build ra có `--color-primary-600:#059669`.
  - `docker compose -f infra/docker-compose.yml up -d` → postgres, redis, keycloak, mailpit `healthy`; minio trả 200 ở `/minio/health/live`; realm `thi360` trả 200; lấy được token `client_credentials` cho client `thi360-api`; PostgreSQL có hai cơ sở dữ liệu `thi360` và `keycloak`.
- **Chưa kiểm tra:** chạy trên "máy sạch" khác; kết quả CI xem ở mục dưới.

### 2026-11-13 — Chốt câu hỏi mở, cập nhật tài liệu

- M.Trường duyệt kiến trúc theo đề xuất và chốt 6 câu hỏi mở cùng phạm vi tùy chỉnh giao diện (spec mục 14, `PRD.md` mục 6).
- Cập nhật spec, plan, `PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`, `TASK.md` theo các điểm đã chốt.
- Thêm task T17 **UNI-2555** (tùy chỉnh giao diện toàn hệ thống). Bình luận "phạm vi đổi" trên UNI-2537, 2540, 2542, 2544, 2547, 2548, 2550, 2554.
- Repo: đã push `main` và `develop` lên `UNIAI-TEAM/thi-360`; `develop` là nhánh mặc định. Commit đầu `7eec907`.

### 2026-11-13 — Khảo sát và lập kế hoạch

- Đọc UNI-1630 (PBI-01) kèm UniNote "Quản lý User", UNI-1631 (PBI-02) kèm UniNote "Quản lý danh mục dùng chung", và tóm tắt 26 PBI của project Thi 360 trên uniworkos.
- Khảo sát `reap-elearning`: phần port được (xác thực, bộ lọc bảo mật, quy tắc tên đăng nhập, lớp học, import có kiểm tra trước, nhật ký, danh mục `global_list`, bộ tùy chỉnh giao diện, đơn hàng VNPAY) và phần phải làm mới (phân quyền vai trò → quyền, lưu đợt cấp hàng loạt, phiên bản mã tổ hợp, ngân hàng câu hỏi).
- Đo phong cách giao diện từ hsavnu.edu.vn → `DESIGN.md`.
- Viết `PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`, `RULES.md`, `TASK.md`, spec và plan đợt 1.
- Tạo trên UniAI (project Thi 360): issue cha **UNI-2537**, sub-issue **UNI-2538 → UNI-2553** (T01–T16), **UNI-2554** (đăng nhập Google, backlog).

## Quyết định đã chốt

Danh sách đầy đủ ở `PRD.md` mục 6 (P1–P12) và spec mục 14. Tóm tắt các điểm ảnh hưởng tới cách viết mã:

| Điểm | Chốt |
|---|---|
| Kiến trúc | Modular monolith + worker; Keycloak chỉ xác thực; outbox trong PostgreSQL; một UI kit Tailwind |
| Phân quyền | Vai trò → quyền, có gán quyền trực tiếp cho user. Vai trò có trường `access` (`MANAGE`/`EXAM`). `PLATFORM_ADMIN` nằm ở realm `master` của Keycloak (realm `thi360` chứa mọi tài khoản còn lại), là người khởi tạo, mọi quyền, không có trong danh sách user |
| Trạng thái tài khoản | `ACTIVE` / `INACTIVE` / xóa mềm. Có giao dịch thì không xóa được |
| Dùng lại tên đăng nhập, email | Được, sau khi xóa mềm (chỉ mục duy nhất `WHERE deleted_at IS NULL`) |
| Mật khẩu | Hệ thống sinh; bắt đổi lần đầu là tùy chọn, mặc định không; file kết quả cấp hàng loạt không hết hạn |
| Lớp | Một học viên thuộc nhiều lớp |
| Danh mục | Môi trường thật chỉ có nhóm; giá trị mẫu chỉ nạp ở `dev`, `test` |
| Nhật ký | Lưu vĩnh viễn, phân vùng theo quý, lưu trữ theo năm |
| Giao diện | Tùy chỉnh toàn hệ thống, là mục trong menu chính (không ở menu profile, không nút nổi); nút chính `#059669` |
| Menu admin | Phân quyền là một menu bốn tab (Vai trò, Quyền, Người dùng theo quyền, Gán hàng loạt); Cấp hàng loạt là nút trong màn Tài khoản |
| Google | Backlog; khi bật thì gộp theo email |
| Đăng nhập | Một nơi đăng nhập ở tên miền chính cho cả hai web; điều hướng theo `access` của vai trò hoặc `callbackUrl`; trang đích kiểm tra quyền (spec mục 15, T18) |

## Chờ người khác

| Việc | Ai |
|---|---|
| Báo BA cập nhật SRS ở 4 điểm khác bản gốc (`PRD.md` mục 7) | M.Trường |
| Giá trị danh mục thật (dạng câu, dạng bài, môn của từng tổ hợp) nhập trên web khi có màn Cấu hình chung | Admin / BA |
| Logo Thi 360 (không dùng logo HSA Education) | Chưa có người nhận |

## Lỗi đã gặp

| Ngày | Lỗi | Xử lý |
|---|---|---|
| 2026-11-13 | Image `minio/minio` và `quay.io/minio/minio` trả `unauthorized` (MinIO ngừng phát hành image công khai) | Dùng `cgr.dev/chainguard/minio:latest`. Chưa ghim được phiên bản vì bản miễn phí chỉ có tag `latest`; cần xem lại trước khi lên môi trường thật |
| 2026-11-13 | `pnpm install` bỏ qua script build của `sharp` | Thêm `onlyBuiltDependencies: [sharp]` vào `pnpm-workspace.yaml` |

## Ghi chú cho phiên sau

- Trang uniworkos cần đăng nhập; đọc task và UniNote qua trình duyệt của Orca, không gọi được API trực tiếp.
- `uniai` CLI đang trỏ workspace `uni2026`; project Thi 360 có id `9c107463-64c7-4a3a-bb62-d4466e897954`.
- Thư mục `reap-elearning/lms-core` và `lms-exam` ở gốc chỉ có một file test lạc; mã thật nằm trong `reap-elearning/be-elearning/`.
- Chưa có SRS-HSA-QDA-001 bản đầy đủ trong repo; mới có nội dung trích trong các PBI.
