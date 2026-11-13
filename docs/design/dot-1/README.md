# Thiết kế màn hình đợt 1

Chốt ngày 2026-11-13 (M.Trường). Bản xem được: https://claude.ai/artifact/4WyWEQAR8DTdZQTmQA5eG7 (trang riêng tư trên claude.ai, chỉ người được chia sẻ mới mở được).

Thư mục này là **mã nguồn của từng màn** trong bản thiết kế đó, chép vào repo để người làm task đọc được mà không cần mở trang trên.

## Cách đọc

- Mỗi tệp `*.dc.html` là một màn. Phần trong `<x-dc>` là bố cục và chữ trên màn; phần `<script>` là dữ liệu mẫu và trạng thái của màn (bấm thử trên bản xem được).
- `{{tên}}` là chỗ điền dữ liệu, `<sc-for>` là vòng lặp, `<sc-if>` là điều kiện. Tệp **không tự chạy** khi mở bằng trình duyệt; đọc như tài liệu.
- Dùng để lấy: bố cục, thứ tự và tên trường, nhãn và câu chữ tiếng Việt, các trạng thái, dữ liệu mẫu.
- **Không chép style viết thẳng trong tệp.** Mã thật chỉ dùng token và thành phần của `packages/ui` theo `DESIGN.md`.
- Bản vẽ khác spec ở điểm nào thì **theo spec** và ghi lại trong PR.

## Màn nào thuộc task nào

| Task | Issue | Màn | Ghi chú |
|---|---|---|---|
| T06 | UNI-2543 | `BangDieuKhien.dc.html` — Bảng điều khiển (trang chủ web admin)<br>`TaiKhoan.dc.html` — Danh sách tài khoản<br>`Forbidden.dc.html` — Trang 403 | Khung trang quản trị (sidebar, thanh trên, breadcrumb, nút avatar) lấy theo phần khung của màn Bảng điều khiển hoặc Danh sách tài khoản; menu của đợt này không có nhóm Nội dung. Trang 403 dùng chung kiểu với web học viên. |
| T18 | UNI-2556 | `Main.dc.html` — Đăng nhập (thi360.vn/dang-nhap)<br>`Landing.dc.html` — Trang giới thiệu tạm (thi360.vn)<br>`DoiMatKhau.dc.html` — Đổi mật khẩu bắt buộc<br>`Home.dc.html` — Trang học viên tạm (/home)<br>`Forbidden.dc.html` — Trang 403 | — |
| T08 | UNI-2545 | `VaiTro.dc.html` — Phân quyền · tab Vai trò<br>`TaoVaiTro.dc.html` — Dialog tạo vai trò · chọn quyền theo đối tượng<br>`Quyen.dc.html` — Phân quyền · tab Quyền<br>`NguoiDungTheoQuyen.dc.html` — Phân quyền · tab Người dùng theo quyền<br>`GanQuyenHangLoat.dc.html` — Phân quyền · tab Gán hàng loạt<br>`ChonQuyen.dc.html` — Dialog chọn quyền theo đối tượng (thêm quyền riêng, gán hàng loạt) | Nút "+ Thêm quyền" ở tab Gán hàng loạt mở dialog `ChonQuyen` (cùng `ObjectPermissionPicker` với dialog tạo vai trò). |
| T09 | UNI-2546 | `TaiKhoan.dc.html` — Danh sách tài khoản<br>`TaiKhoanChonNhieu.dc.html` — Danh sách tài khoản · đang chọn nhiều dòng<br>`TaoTaiKhoan.dc.html` — Dialog tạo tài khoản<br>`ChiTietTaiKhoan.dc.html` — Chi tiết tài khoản · tab Vai trò & quyền<br>`ChiTietTaiKhoanThi.dc.html` — Chi tiết tài khoản · chỉ có vai trò Thi<br>`ChonQuyen.dc.html` — Dialog chọn quyền theo đối tượng (thêm quyền riêng, gán hàng loạt) | `TaiKhoanChonNhieu`: thanh thao tác khi chọn nhiều dòng. `ChiTietTaiKhoanThi`: tài khoản chỉ có vai trò Thi. Nút "Thêm quyền" ở chi tiết tài khoản mở dialog `ChonQuyen`. |
| T10 | UNI-2547 | `LopHoc.dc.html` — Danh sách lớp học<br>`ChiTietLop.dc.html` — Chi tiết lớp | — |
| T11 | UNI-2548 | `CapBuoc1TaiTep.dc.html` — Cấp hàng loạt · bước 1 tải tệp<br>`CapHangLoat.dc.html` — Cấp tài khoản hàng loạt · bước xem trước<br>`CapBuoc3XacNhan.dc.html` — Cấp hàng loạt · bước 3 xác nhận<br>`CapBuoc4DangXuLy.dc.html` — Cấp hàng loạt · bước 4 đang xử lý<br>`CapBuoc4KetQua.dc.html` — Cấp hàng loạt · bước 4 kết quả | Theo thứ tự: bước 1 tải tệp, bước 2 xem trước (`CapHangLoat`), bước 3 xác nhận, bước 4 đang xử lý, bước 4 kết quả. |
| T12 | UNI-2549 | `ChiTietTaiKhoan.dc.html` — Chi tiết tài khoản · tab Vai trò & quyền | Không có màn riêng: hồ sơ học viên là các tab của Chi tiết tài khoản, dùng cùng khung. |
| T17 | UNI-2555 | `TuyChinhGiaoDien.dc.html` — Tùy chỉnh giao diện | Dải màu trong bản vẽ pha với trắng/đen cho nhanh; bản thật tính bằng OKLCH theo spec §13.1 nên màu các bậc khác đôi chút. |
| T15 | UNI-2552 | `CauHinhChung.dc.html` — Cấu hình chung (danh mục dùng chung)<br>`DanhMucCay.dc.html` — Cấu hình chung · danh mục dạng cây (Cấu trúc kiến thức)<br>`ThemGiaTri.dc.html` — Dialog giá trị · kiểu Danh sách (thêm môn học)<br>`MaToHop.dc.html` — Dialog giá trị · kiểu Tổ hợp (sửa mã tổ hợp)<br>`TaoDanhMucCay.dc.html` — Dialog thêm danh mục · kiểu cây<br>`ThemNutCay.dc.html` — Dialog giá trị · kiểu Cây (thêm bài, có liên kết)<br>`ChuyenNhanhCay.dc.html` — Dialog chuyển nhánh<br>`NgungNutCay.dc.html` — Dialog ngừng sử dụng nút · phạm vi ảnh hưởng<br>`NhapCayExcel.dc.html` — Dialog nhập Excel cho cây · bước xem trước | Mở lại ngày 2026-11-14 (plan A7). |

## Chưa có bản vẽ

- Màn tra cứu Nhật ký: đợt này chỉ có API (T05), chưa có task giao diện.
- Các task chỉ có backend (T02, T03, T04, T05, T07, T13, T14) và T16 không có màn.
