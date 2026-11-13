# PRD — Thi 360

> Cập nhật: 2026-11-13 · Nguồn yêu cầu: SRS-HSA-QDA-001 và 26 PBI trong project "Thi 360" trên uniworkos · Issue theo dõi: UNI-2537

## 1. Thi 360 là gì

Thi 360 là cổng luyện thi trực tuyến cho hai kỳ thi đánh giá năng lực:

- **HSA** — Đánh giá năng lực ĐHQG Hà Nội.
- **QDA** — Đánh giá năng lực Bộ Quốc phòng.

Học viên mua bài thi, làm bài trên web hoặc app, nhận điểm ngay, xem lời giải và nhận xét của AI, rồi luyện tiếp theo phần còn yếu. Đội vận hành quản lý ngân hàng câu hỏi, dựng đề theo ma trận, bán bài thi, hỗ trợ học viên và theo dõi số liệu.

Sản phẩm kế thừa một phần mã từ **reap-elearning** (xác thực, nhật ký, đơn hàng VNPAY, lượt thi) nhưng là sản phẩm riêng: không đa tenant, không dùng bảng Moodle.

## 2. Dành cho ai

| Người dùng | Họ cần gì | Dùng ở đâu |
|---|---|---|
| **Học viên** (học sinh THPT ôn HSA/QDA) | Thi thử giống thi thật, biết điểm ngay, hiểu vì sao sai, biết nên luyện gì tiếp | Web học viên, app Android/iOS |
| **Quản trị nội dung** | Quản lý danh mục, ngân hàng câu hỏi, ma trận, xuất bản bài thi | Web admin |
| **Vận hành / CSKH / kế toán** | Tra đơn, phục hồi quyền, hoàn tiền, xử lý yêu cầu hỗ trợ, quản lý tài khoản và lớp | Web admin |
| **Platform Admin** | Phân quyền, cấu hình hệ thống, xem nhật ký và báo cáo | Web admin |

## 3. Phạm vi toàn sản phẩm (26 PBI)

| Nhóm | PBI |
|---|---|
| Quản trị nền | 01 Tài khoản và phân quyền · 02 Danh mục dùng chung |
| Nội dung | 03 Ngân hàng câu hỏi · 04 Thiết lập bài thi và ma trận |
| Học viên trên web | 05 Trang giới thiệu, danh mục · 06 Đăng ký, đăng nhập, hồ sơ |
| Thương mại | 07 Mua và thanh toán VNPAY · 08 Đơn hàng, người mua · 09 Bài thi đã mua |
| Thi và kết quả | 10 Làm bài · 11 Lưu bài, khôi phục lượt · 12 Chấm điểm · 13 Chi tiết kết quả, lịch sử |
| AI | 14 Giải thích câu hỏi · 15 Điểm mạnh/yếu · 16 Đề xuất ôn luyện · 17 Tiến bộ · 18 Quản lý sử dụng AI |
| Mobile | 19 Tài khoản · 20 Danh mục, bài thi của tôi · 21 Làm bài · 22 Kết quả và AI · 23 Đồng bộ web/app |
| Vận hành | 24 Hỗ trợ · 25 Báo cáo · 26 Nhật ký vận hành |

## 4. Phạm vi đợt 1 (đang làm)

Đợt 1 dựng nền và làm trọn hai PBI quản trị đầu tiên. Chi tiết ở `docs/specs/2026-11-13-base-port-pbi-01-02.md`.

**Có trong đợt 1**

- Nền: monorepo, backend, Keycloak, PostgreSQL, Redis, nhật ký thao tác, design system, khung web admin.
- Tùy chỉnh giao diện toàn hệ thống (sáng/tối, bộ phối màu), là một mục trong menu chính.
- **PBI-01**:
  - Quản lý tài khoản: danh sách, tạo, sửa, ngừng hoạt động/kích hoạt lại, reset mật khẩu, xóa mềm.
  - Phân quyền theo vai trò: bảng quyền, vai trò gán quyền, gán vai trò và quyền cho từng user, xem người dùng theo quyền, gán hàng loạt.
  - Lớp học: tạo/sửa, thêm, loại, chuyển học viên, có lịch sử.
  - Cấp tài khoản hàng loạt bằng file.
  - Hồ sơ học viên: phần tài khoản và lớp; phần học tập chờ module thi.
- **PBI-02**: màn Cấu hình chung để quản lý nhóm danh mục, giá trị, mã tổ hợp có phiên bản, nơi đang dùng và lịch sử thay đổi.

**Không có trong đợt 1**

- Đăng nhập Google (backlog, UNI-2554). Quy tắc khi bật đã chốt, xem mục 6.
- Học viên tự đăng ký trên web (PBI-06), ngân hàng câu hỏi, bài thi, thanh toán, AI, mobile.

## 5. Tiêu chí thành công của đợt 1

- AC-01.1 đến AC-01.8 và AC-02 đều có test tự động chứng minh.
- Người thiếu quyền không làm được thao tác bằng cách gọi thẳng API (trả 403), kể cả khi nút bị ẩn trên giao diện.
- Mọi thay đổi tài khoản, lớp, quyền, danh mục đều có bản ghi nhật ký: ai, lúc nào, đổi gì, lý do, kết quả.
- Thêm một mã tổ hợp mới không cần sửa mã nguồn.

## 6. Quyết định sản phẩm đã chốt

| # | Quyết định | Ai chốt, khi nào |
|---|---|---|
| P1 | Phân quyền theo **vai trò → quyền**; ngoài ra gán được quyền trực tiếp cho từng user | M.Trường, 2026-11-13 |
| P2 | Đăng nhập Google để backlog | M.Trường, 2026-11-13 |
| P3 | Khi bật Google: đăng nhập Google bằng email X sẽ vào **đúng tài khoản đã có email X** (gộp theo email), không tạo tài khoản thứ hai. Điểm này **khác SRS gốc** ("không tự gộp theo email"), cần BA cập nhật SRS mục 5.2 và AC-19 | M.Trường, 2026-11-13 |
| P4 | Màn danh mục dùng chung làm đầy đủ hơn bản "Cấu hình chung" của reap: thêm nơi đang dùng, lịch sử, phiên bản tổ hợp, sắp xếp kéo thả, nhập/xuất Excel | M.Trường, 2026-11-13 |
| P5 | Giao diện tham khảo phong cách hsavnu.edu.vn (xem `DESIGN.md`); nút chính giữ màu `#059669` | M.Trường, 2026-11-13 |
| P6 | Một học viên được thuộc nhiều lớp | M.Trường, 2026-11-13 |
| P7 | Tài khoản do admin cấp: **người tạo nhập mật khẩu** theo quy tắc (≥ 8 ký tự, đủ chữ hoa, chữ thường, số, ký tự đặc biệt); cấp hàng loạt lấy từ cột Mật khẩu trong tệp, dòng trống thì hệ thống sinh. Bắt đổi ở lần đăng nhập đầu là tùy chọn, mặc định không. File kết quả cấp hàng loạt không hết hạn | M.Trường, 2026-11-13 (sửa theo góp ý của BA) |
| P17 | Danh sách tài khoản không có cột Mã và cột Đăng nhập cuối | BA góp ý, M.Trường duyệt, 2026-11-13 |
| P8 | Tài khoản có ba mức: hoạt động → ngừng hoạt động → xóa mềm. Có giao dịch thì không xóa được, chỉ ngừng hoạt động. Không có giao dịch thì xóa mềm và tên đăng nhập, email dùng lại được. **Khác SRS** | M.Trường, 2026-11-13 |
| P9 | Danh mục ở môi trường thật để trống giá trị, admin/BA tự nhập; local và test có bộ mẫu | M.Trường, 2026-11-13 |
| P10 | Nhật ký lưu vĩnh viễn, không che; phân vùng theo quý, dữ liệu cũ chuyển sang bảng lưu trữ phân vùng theo năm | M.Trường, 2026-11-13 |
| P11 | Giữ bộ tùy chỉnh giao diện như reap, áp dụng toàn hệ thống; là một mục trong menu chính của trang quản trị (không ở menu profile, không dùng nút nổi) | M.Trường, 2026-11-13 |
| P19 | Quyền theo mô hình đối tượng × mức: mỗi đối tượng hai mức Xem / Xem và sửa, cộng quyền riêng cho việc nhạy cảm. Đợt 1 có 7 đối tượng, 13 quyền. Thêm vai trò mặc định "Quản trị hệ thống" | M.Trường, 2026-11-13 |
| P18 | Quyền là danh sách cố định theo phần mềm (không tạo, sửa, xóa trên giao diện); vai trò do admin tự do tạo và gán | M.Trường, 2026-11-13 |
| P15 | Quyền và vai trò gom thành một menu "Phân quyền" có bốn tab: Vai trò, Quyền, Người dùng theo quyền, Gán hàng loạt | M.Trường, 2026-11-13 |
| P16 | Cấp tài khoản hàng loạt không có menu riêng; là nút trong màn Tài khoản | M.Trường, 2026-11-13 |
| P13 | Hai web trên hai tên miền; landing và đăng nhập ở tên miền chính; đăng nhập một lần, điều hướng theo vai trò (admin → trang quản trị, học viên → `/home`) hoặc theo `callbackUrl`; trang đích kiểm tra quyền, sai thì ra trang mã lỗi | M.Trường, 2026-11-13 |
| P14 | Vai trò có trường phạm vi truy cập `access` (`MANAGE` / `EXAM`), chọn trong biểu mẫu vai trò; đăng nhập điều hướng theo trường này. Tài khoản Thi 360 nằm ở realm riêng (`thi-360`); realm `master` chỉ có `PLATFORM_ADMIN`, mặc định có mọi quyền và `MANAGE`, là người khởi tạo, không hiển thị trong danh sách user | M.Trường, 2026-11-13 |
| P12 | Kiến trúc theo đề xuất: modular monolith, Keycloak, một UI kit Tailwind | M.Trường, 2026-11-13 |

## 7. Việc cần BA cập nhật trong SRS

Sáu câu hỏi mở của đợt 1 đã được M.Trường chốt ngày 2026-11-13 (xem mục 6 và spec mục 14). Bốn điểm khác SRS gốc, M.Trường tự báo BA:

1. Đăng nhập Google gộp theo email (P3).
2. Không bắt buộc đổi mật khẩu ở lần đăng nhập đầu (P7).
3. Tên đăng nhập và email dùng lại được sau khi xóa mềm (P8).
4. Trạng thái tài khoản còn hai mức hiển thị: hoạt động, ngừng hoạt động (P8).
