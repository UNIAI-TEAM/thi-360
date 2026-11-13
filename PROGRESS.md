# PROGRESS — Thi 360

> Cập nhật mỗi lần dừng việc. Trạng thái issue sống nằm trên UniAI; file này ghi **đã làm gì, vướng gì, làm gì tiếp**.

## Trạng thái hiện tại (2026-11-13)

**Giai đoạn:** đợt 1, đang làm **T01 (UNI-2538)** — khởi tạo monorepo, CI, môi trường dev.

**Bước tiếp theo**

1. Hoàn tất T01 và mở PR vào `develop`.
2. T02 (UNI-2539) khung backend và T06 (UNI-2543) design system chạy song song được.

## Đã làm

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
| Phân quyền | Vai trò → quyền, có gán quyền trực tiếp cho user |
| Trạng thái tài khoản | `ACTIVE` / `INACTIVE` / xóa mềm. Có giao dịch thì không xóa được |
| Dùng lại tên đăng nhập, email | Được, sau khi xóa mềm (chỉ mục duy nhất `WHERE deleted_at IS NULL`) |
| Mật khẩu | Hệ thống sinh; bắt đổi lần đầu là tùy chọn, mặc định không; file kết quả cấp hàng loạt không hết hạn |
| Lớp | Một học viên thuộc nhiều lớp |
| Danh mục | Môi trường thật chỉ có nhóm; giá trị mẫu chỉ nạp ở `dev`, `test` |
| Nhật ký | Lưu vĩnh viễn, phân vùng theo quý, lưu trữ theo năm |
| Giao diện | Tùy chỉnh toàn hệ thống, mở từ menu profile, không có nút nổi; nút chính `#059669` |
| Google | Backlog; khi bật thì gộp theo email |

## Chờ người khác

| Việc | Ai |
|---|---|
| Báo BA cập nhật SRS ở 4 điểm khác bản gốc (`PRD.md` mục 7) | M.Trường |
| Giá trị danh mục thật (dạng câu, dạng bài, môn của từng tổ hợp) nhập trên web khi có màn Cấu hình chung | Admin / BA |
| Logo Thi 360 (không dùng logo HSA Education) | Chưa có người nhận |

## Lỗi đã gặp

Chưa có.

## Ghi chú cho phiên sau

- Trang uniworkos cần đăng nhập; đọc task và UniNote qua trình duyệt của Orca, không gọi được API trực tiếp.
- `uniai` CLI đang trỏ workspace `uni2026`; project Thi 360 có id `9c107463-64c7-4a3a-bb62-d4466e897954`.
- Thư mục `reap-elearning/lms-core` và `lms-exam` ở gốc chỉ có một file test lạc; mã thật nằm trong `reap-elearning/be-elearning/`.
- Chưa có SRS-HSA-QDA-001 bản đầy đủ trong repo; mới có nội dung trích trong các PBI.
