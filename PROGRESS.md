# PROGRESS — Thi 360

> Cập nhật mỗi lần dừng việc. Mới nhất ở trên. Trạng thái issue sống nằm trên UniAI; file này ghi **đã làm gì, vướng gì, làm gì tiếp**.

## Trạng thái hiện tại (2026-11-13)

**Giai đoạn:** lập kế hoạch đợt 1. Chưa có dòng mã nào.

**Bước tiếp theo**

1. Người duyệt spec `docs/specs/2026-11-13-base-port-pbi-01-02.md` và các quyết định kiến trúc trong `ARCHITECTURE.md` mục 7.
2. Commit bộ tài liệu này lên `https://github.com/UNIAI-TEAM/thi-360.git` (repo đang trống; chưa commit, chưa push).
3. Bắt đầu T01 (UNI-2538): khởi tạo monorepo, CI, Docker Compose.

## Đã làm

### 2026-11-13 — Khảo sát và lập kế hoạch

- Đọc UNI-1630 (PBI-01) kèm UniNote "Quản lý User", UNI-1631 (PBI-02) kèm UniNote "Quản lý danh mục dùng chung", và tóm tắt 26 PBI của project Thi 360 trên uniworkos.
- Khảo sát `reap-elearning`: xác định phần port được (xác thực, bộ lọc bảo mật, quy tắc tên đăng nhập, lớp học, import có kiểm tra trước, nhật ký, danh mục `global_list`, đơn hàng VNPAY) và phần phải làm mới (phân quyền vai trò → quyền, mật khẩu tạm, lưu đợt cấp hàng loạt, phiên bản mã tổ hợp, ngân hàng câu hỏi).
- Đo phong cách giao diện từ hsavnu.edu.vn (trang chủ, Thi thử, Khóa học, chi tiết khóa học, Tin tức, Đăng nhập) → `DESIGN.md`.
- Viết `PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`, `RULES.md`, `TASK.md`, spec và plan đợt 1.
- Tạo trên UniAI (project Thi 360): issue cha **UNI-2537** và 17 sub-issue **UNI-2538 → UNI-2554** (T01–T16 ở `todo`, đăng nhập Google ở `backlog`).

## Quyết định đã chốt

| Ngày | Quyết định | Ai |
|---|---|---|
| 2026-11-13 | Phân quyền theo vai trò → quyền, có gán quyền trực tiếp cho user | M.Trường |
| 2026-11-13 | Đăng nhập Google để backlog; khi bật thì gộp vào tài khoản đã có cùng email | M.Trường |
| 2026-11-13 | Màn danh mục dùng chung làm đầy đủ hơn bản của reap | M.Trường |
| 2026-11-13 | Giao diện tham khảo hsavnu.edu.vn | M.Trường |

## Đề xuất đang dùng làm mặc định, chờ xác nhận

Các điểm này do agent đề xuất và đã đưa vào tài liệu; chưa có xác nhận rõ ràng của người.

| Điểm | Đang dùng | Ghi ở đâu |
|---|---|---|
| Kiến trúc backend | Modular monolith + worker, không microservice | `ARCHITECTURE.md` A1 |
| Xác thực | Giữ Keycloak, quyền do ứng dụng giữ | A2 |
| Hàng đợi | Outbox trong PostgreSQL, chưa dùng Kafka | A3 |
| Giao diện | Một UI kit Tailwind cho cả hai web, không dùng MUI | A4 |
| Mobile | React Native (Expo), làm ở đợt sau | `ARCHITECTURE.md` mục 2 |
| Quyền tự tạo trên màn Quyền | Tạo được, nhưng chỉ có tác dụng khi mã nguồn hoặc menu kiểm tra mã quyền đó | Spec mục 5.2 |

## Vướng mắc và câu hỏi mở

| # | Nội dung | Chờ ai |
|---|---|---|
| Q1 | Học viên thuộc một hay nhiều lớp | BA |
| Q2 | Bàn giao tài khoản hàng loạt: mật khẩu tạm hay link kích hoạt | BA |
| Q3 | Tên đăng nhập, email của tài khoản đã xóa có dùng lại được không | BA |
| Q4 | Giá trị Dạng câu, Dạng bài; danh sách môn của QĐ01–QĐ05; tổ hợp của HSA | BA |
| Q5 | Thời hạn lưu và ẩn dữ liệu cá nhân trong nhật ký (D15) | BA |
| Q6 | Quy tắc gộp Google theo email **khác SRS gốc** ("không tự gộp theo email"); cần BA cập nhật SRS mục 4.1, 5.2, AC-19 | M.Trường báo BA |

## Lỗi đã gặp

Chưa có (chưa viết mã).

## Ghi chú cho phiên sau

- Trang uniworkos cần đăng nhập; đọc task và UniNote qua trình duyệt của Orca, không gọi được API trực tiếp.
- `uniai` CLI đang trỏ workspace `uni2026`; project Thi 360 có id `9c107463-64c7-4a3a-bb62-d4466e897954`.
- Thư mục `reap-elearning/lms-core` và `lms-exam` ở gốc chỉ có một file test lạc; mã thật nằm trong `reap-elearning/be-elearning/`.
- Chưa có SRS-HSA-QDA-001 bản đầy đủ trong repo; mới có nội dung trích trong các PBI.
