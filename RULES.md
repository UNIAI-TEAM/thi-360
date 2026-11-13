# RULES — Thi 360

> Cập nhật: 2026-11-13 · Áp dụng cho mọi người và mọi agent lập trình (Claude Code, Codex, agent UniAI).
> Quy tắc theo dõi công việc kế thừa từ UniWork (`dev-uniwork/docs/engineering/UNIAI_TRACKING.md`), chỉnh cho dự án này.

## 1. Đọc gì trước khi làm

1. `PROGRESS.md` — đang ở đâu, bước tiếp theo.
2. `TASK.md` — task nào, mã issue nào.
3. Spec và plan của đợt đang làm trong `docs/specs/`, `docs/plans/`.
4. `ARCHITECTURE.md`, `DESIGN.md` khi task đụng tới cấu trúc hoặc giao diện.

## 2. Theo dõi công việc trên UniAI

UniAI (workspace `uni2026`, project **Thi 360** `9c107463-64c7-4a3a-bb62-d4466e897954`) là nơi trả lời "ai đang làm gì, đến đâu, bằng chứng ở đâu". Repo giữ mã và tài liệu.

| # | Luật |
|---|---|
| 1 | **Không issue, không code.** Mọi thay đổi mã hoặc tài liệu gắn với một issue `UNI-nnn` thuộc project Thi 360. Việc phát sinh thì tạo issue trước. |
| 2 | **Nhánh mang mã issue:** `feature/UNI-2541-rbac-engine`, `fix/UNI-…`, `docs/UNI-…`. Tách từ `develop`. Một nhánh chỉ một issue. |
| 3 | **Trạng thái do người làm cập nhật đúng lúc:** `todo → in_progress` khi bắt đầu, `in_review` khi mở PR, `done` khi PR đã merge và đủ tiêu chí xong, `blocked` ngay khi kẹt kèm bình luận nêu lý do. |
| 4 | **Bằng chứng nằm trong bình luận issue:** URL của PR, lệnh đã chạy thật và kết quả, ảnh chụp, quyết định nhỏ. Không sửa tiêu đề và mô tả issue để viết lại lịch sử. |
| 5 | **Plan = sub-issue.** Mỗi task lớn trong `docs/plans/` là một sub-issue của issue cha. |

**Commit và PR**

- Conventional Commits: `feat(identity): khóa tài khoản thu hồi phiên`. Trailer `Refs: UNI-nnn` do hook thêm từ tên nhánh.
- Tiêu đề PR bắt đầu bằng mã issue: `UNI-2541: RBAC engine`. PR vào `develop`; `main` chỉ nhận merge từ `develop` khi phát hành.
- Không push thẳng vào `main` và `develop`.

**Thêm cho agent**

1. Mở phiên bằng `uniai issue get UNI-nnn --output json` để đọc mô tả, bình luận, sub-issue. Không đoán phạm vi từ tên nhánh.
2. Agent **không tự chuyển issue sang `done`**. Agent được đặt `in_progress`, `in_review`, `blocked` và bình luận; `done` do người quyết định.
3. Mỗi lần dừng phải để lại bình luận có tiền tố `[agent]`: đã làm gì, lệnh nào đã chạy thật, còn gì, bị chặn bởi gì. Đồng thời cập nhật `PROGRESS.md`.
4. Không tạo issue mới ngoài sub-issue của issue đang làm, trừ khi người yêu cầu.
5. Agent đề xuất qua PR, người merge.

## 3. Quy tắc viết code chung

- Thay đổi nhỏ nhất đáp ứng được yêu cầu. Không sửa "tiện tay" phần ngoài phạm vi issue.
- Tên trong mã (class, biến, bảng, cột, mã quyền, mã lỗi) bằng tiếng Anh. Chuỗi hiển thị cho người dùng nằm trong file dịch: tiếng Việt là mặc định, tiếng Anh là ngôn ngữ thứ hai (quyết định 2026-11-14); mỗi khóa phải có ở cả `vi.json` và `en.json`.
- Không để mã chết, `TODO` không có mã issue, hoặc mã bị comment lại.
- Mỗi hành vi mới có test. Sửa lỗi thì thêm test tái hiện lỗi trước.
- Bí mật (mật khẩu, khóa, token) chỉ nằm trong biến môi trường. Cập nhật `.env.example` khi thêm biến.

## 4. Backend (Java, Spring Boot)

- **Ranh giới module:** module chỉ gọi module khác qua package `spi` hoặc sự kiện. Không import `domain`, `infrastructure`, `application` của module khác. Không join bảng qua schema của module khác.
- **Lớp:** `api` (controller, DTO) → `application` (use case, transaction) → `domain` → `infrastructure`. Controller không chứa nghiệp vụ, không trả entity JPA.
- **Phân quyền:** mọi endpoint dưới `/api/v1/admin/**` có `@RequirePermission("MÃ_QUYỀN")`. Mã quyền là hằng trong `Permissions`, khớp với dữ liệu seed. Không kiểm tra theo tên vai trò.
- **Nhật ký:** mọi use case làm thay đổi dữ liệu ghi sự kiện audit trong cùng transaction (qua outbox). Không ghi mật khẩu, token, link reset.
- **Cơ sở dữ liệu:**
  - Chỉ đổi schema bằng migration Flyway mới. **Không sửa migration đã merge.**
  - Khóa chính UUID. Thời gian dùng `timestamptz`, lấy theo giờ máy chủ.
  - Ràng buộc duy nhất đặt ở cơ sở dữ liệu, không chỉ kiểm tra trong mã. Mã và tên đăng nhập so sánh trên cột đã chuẩn hóa (chữ thường, bỏ khoảng trắng đầu cuối).
  - Bảng có sửa đồng thời dùng cột `version` (khóa lạc quan).
  - Xóa tài khoản, lớp là xóa mềm (`deleted_at`).
- **Idempotency:** thao tác có thể bị gửi lặp (xác nhận cấp hàng loạt, và về sau: bắt đầu thi, nộp bài, callback thanh toán) nhận khóa idempotency và trả cùng kết quả khi gửi lại.
- **Lỗi:** dùng định dạng lỗi chung `{code, message, fieldErrors[], traceId}`. Không trả stack trace hay thông báo lỗi kỹ thuật nguyên văn cho client.
- **Gọi Keycloak:** chỉ qua lớp `KeycloakIdentityClient` trong `identity/infrastructure`. Không truy vấn bảng của Keycloak.
- **Kiểm thử:** test tích hợp chạy với Testcontainers (PostgreSQL, Redis, Keycloak thật), không dùng H2. Test phân quyền gồm trường hợp thiếu quyền trả 403.

## 5. Frontend (Next.js, TypeScript)

- TypeScript `strict`. Không dùng `any`; không dùng `@ts-ignore` khi chưa có lý do ghi kèm.
- Gọi API qua `packages/api-client` (sinh từ OpenAPI). Không sửa tay mã sinh ra; không tự viết `fetch` tới backend trong component.
- Dữ liệu máy chủ dùng TanStack Query. Biểu mẫu dùng react-hook-form + zod.
- Giao diện dùng thành phần của `packages/ui` và token trong `DESIGN.md`. **Không** viết mã màu, cỡ chữ, bo góc trực tiếp; **không** thêm thư viện giao diện khác (MUI, Ant Design, HeroUI…).
- Ẩn nút và menu theo quyền chỉ để tiện dùng. Máy chủ mới là nơi chặn; không coi việc ẩn nút là bảo mật.
- Token đăng nhập không nằm trong `localStorage` hay biến JavaScript. Xác thực đi qua cookie HttpOnly và route proxy của Next.js.
- Mỗi màn có đủ bốn trạng thái: đang tải, rỗng, lỗi, có dữ liệu.

## 6. Port mã từ reap-elearning

- Port theo **luồng và quy tắc nghiệp vụ**, không sao chép nguyên khối. Ghi nguồn trong mô tả PR (đường dẫn file gốc).
- **Không mang sang:** đa tenant (`TenantDomainFilter`, chuyển tenant, khóa đơn vị), Eureka, config server, Kafka, bảng `mdl_*` và mọi thứ của Moodle, việc đọc thẳng bảng Keycloak, giám sát thi bằng khuôn mặt, H5P, giao diện MUI.
- Mã port phải qua cùng tiêu chuẩn như mã mới: có kiểm tra quyền, có audit, có test. Ví dụ `PermissionController` và `GET /users/{id}/profile` của reap không có kiểm tra quyền, không được port nguyên trạng.

## 7. Agent KHÔNG được làm

- Không commit, push, mở PR, merge khi người chưa yêu cầu. Không bao giờ push `--force`, không bỏ qua hook (`--no-verify`).
- Không chuyển issue sang `done`. Không tạo issue ngoài phạm vi được giao.
- Không sửa hoặc xóa migration Flyway đã merge. Không chạy lệnh xóa dữ liệu (`DROP`, `TRUNCATE`, `DELETE` không điều kiện, `docker compose down -v`) trên môi trường không phải máy mình.
- Không tắt, bỏ qua hay xóa test để build xanh. Không hạ ngưỡng lint hoặc kiểu.
- Không thêm endpoint quản trị thiếu `@RequirePermission`. Không thêm lối tắt bỏ qua xác thực, kể cả "tạm cho dev".
- Không ghi bí mật vào mã, log, tài liệu, bình luận issue. Không commit `.env`.
- Không thêm thư viện mới khi chưa nêu lý do trong PR. Không đổi công nghệ đã chốt trong `ARCHITECTURE.md` mà chưa có quyết định của người.
- Không bịa dữ liệu nghiệp vụ còn chờ BA (danh sách môn của tổ hợp, giá trị dạng câu…). Để trống, ghi vào mục câu hỏi mở và báo lại.
- Không dùng logo, tên, hình ảnh của HSA Education hay nội dung lấy từ hsavnu.edu.vn.
- Không báo "xong" khi chưa chạy test hoặc test còn đỏ. Nói rõ cái gì đã chạy, cái gì chưa.
- Không sửa `PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`, `RULES.md` theo ý mình. Đề xuất thay đổi trong PR và nêu rõ.

## 8. Tiêu chí xong của một task

- [ ] Hành vi đúng mô tả và tiêu chí chấp nhận trong sub-issue.
- [ ] Test mới xanh; toàn bộ test của module đã chạy và xanh.
- [ ] Endpoint mới có kiểm tra quyền, có audit, có trong OpenAPI.
- [ ] Giao diện mới đủ bốn trạng thái, dùng token của `DESIGN.md`.
- [ ] `TASK.md` và `PROGRESS.md` đã cập nhật.
- [ ] Issue ở `in_review`, có bình luận kèm URL của PR và lệnh đã chạy.
