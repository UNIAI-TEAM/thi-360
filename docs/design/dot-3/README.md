# Thiết kế màn hình đợt 3 · Thương mại (PBI-05–09), lượt thi (PBI-10–13)

Bản xem được: https://claude.ai/artifact/4WyWEQAR8DTdZQTmQA5eG7, trang 10–16 (trang riêng tư trên claude.ai). Vẽ ngày 2026-11-17 cho UNI-2622, theo hai đặc tả `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md` (TM) và `docs/specs/2026-11-16-luot-thi-pbi-10-13.md` (LT).

Cách đọc tệp `*.dc.html` giống `docs/design/dot-1/README.md`:

- Lấy bố cục, tên trường, câu chữ, trạng thái, dữ liệu mẫu.
- **Không chép style viết thẳng.** Mã thật dùng token và thành phần của `packages/ui` theo `DESIGN.md`.
- Đặc tả thắng bản vẽ khi hai bên khác nhau. Lệch thì sửa bản vẽ hoặc ghi ở mục "Chỗ cần chốt" dưới đây.
- Nhiều màn có khối **"Trạng thái khác"** ở cuối, gồm rỗng, lỗi, đang tải, thiếu quyền và các biến thể. Khối này là một phần của nghiệm thu.

Màu: các màn mới dùng `#047857` cho nền nút chính và chữ màu chính, theo `DESIGN.md` mục 2.1. 14 màn làm bài cũ chép từ đợt trước còn `#059669`; khi đó token trong `DESIGN.md` thắng.

## Màn nào thuộc task nào

| Task | Trang canvas | Đường dẫn | Màn |
|---|---|---|---|
| FS-1 | 10 | `/`, `/exams/[code]`, 410, 404 | `TrangChuDot3` (6 khối, danh mục ở `#exams`) · `ChiTietBaiThiBan` (nút theo `exam-access`, đủ 8 tình huống ở khối trạng thái khác) · `BaiThiNgungBan` (410, kèm 404) |
| FS-2 | 10 | `/register`, `/verify-email`, `/forgot-password`, `/reset-password`, `/account/profile` | `DangKy` · `XacMinhEmail` · `QuenMatKhau` · `DatLaiMatKhau` · `HoSo`. Đăng nhập theo `docs/design/dot-1/Main.dc.html` |
| FS-3 | 11 | `/checkout/[code]`, `/checkout/result`, `/home`, `/account/orders` | `XacNhanMua` · `ThanhToanThanhCong` · `ThanhToanDangKiemTra` · `ThanhToanThatBai` · `BaiThiCuaToi` · `DonCuaToi` |
| FS-4 | 13 (mới), 06 và 09 (cũ) | thành phần `packages/ui` | Dạng mới: `ThiTraLoiNgan`, `ThiDienKhuyet`, `ThiGhepDoi` (bấm thử được), `ThiTuLuan`, `ThiMobileGhepDoi`. Chế độ `review`: `CauHoiCheDoXemLai`. Dạng cũ, chỉ lấy phần thân câu: `CauHoi1DapAnThi`, `CauHoiNhieuDapAnThi`, `CauHoiDungSaiThi`, `CauHoiChumThi` và các màn `ThiMobile*`. `CauHoiDienDapAnThi` ứng với Điền khuyết; `CauHoiKeoThaThi`, `ThiMobileKeoTha` đã bỏ (thay bằng Ghép đôi) |
| FS-5 | 12 | `/prepare/[entitlementId]`, `/attempts/[id]` | `ChuanBiLamBai` · `DangChuanBiDe` · `LamBaiTheoPhan` · `LamBaiMobileTheoPhan` · `HoanThanhPhan` · `ChonPhan3` (bấm thử được) · `NopBaiXacNhan` · `TiepQuanLuot`. Khung đầu trang và bảng số câu theo phần ở đây thay cho khung của các màn `*Thi` cũ |
| FS-6 | 14 | `/attempts/[id]/result`, `/attempts/[id]/review`, `/history` | `KetQuaLuot` · `KetQuaChoCham` · `XemLaiBaiLam` · `LichSuLamBai` |
| FA-1 | 15 | tab Thông tin bán, tab Kiểm tra, menu | `BaiThiThongTinBan` · `BaiThiKiemTraThuongMai`. Menu: nhóm Thương mại (Khuyến mãi, Đơn hàng), mục Lượt thi |
| FA-2 | 15 | `/promotions` | `KhuyenMai` (bấm thử được tab và dải trạng thái) · `TaoKhuyenMai` (Tweaks `mode`: sửa voucher đã dùng / tạo mới) · `ChiTietKhuyenMai` |
| FA-3 | 16 | `/orders`, chi tiết đơn, `/orders/buyers` | `DonHang` · `ChiTietDonHang` · `HoanTienDonHang` (Tweaks `variant`: api, api409, manual, manualConfirm, unknown) · `NguoiMua` |
| FA-4 | 16 | `/attempts`, `/attempts/[id]`; `/grading` giữ màn đợt 2 | `LuotThiAdmin` · `ChiTietLuotThiAdmin` |

## Chỗ cần chốt (M.Trường hoặc BA)

1. **`wordLimit` của câu Tự luận** là số chữ tối thiểu hay tối đa. Bản vẽ hiểu là tối thiểu, theo ví dụ bài viết 500 chữ của BCA.
2. **Nhãn lượt còn câu chưa chấm.** `DESIGN.md` 5.14 ghi "Đang chấm", LT ghi "Chờ chấm". Bản vẽ theo LT; cần sửa `DESIGN.md` khi chốt.
3. **Đánh số câu.** Bản vẽ đánh lại từ 1 ở mỗi phần. Đặc tả không nói; UniNote V-ACT đánh liền 1–120.
4. **UniNote khác L7.** UniNote PBI-10 mục 6.3 và 12.1 cho đề đồng hồ chung quay lại phần trước. Bản vẽ theo L7: làm tuần tự, không quay lại.
5. **Màn cũ `CauHoiDienDapAnThi`** có dòng "Số thập phân gõ bằng dấu phẩy hoặc dấu chấm đều được", trái BE B7 (khớp đúng cách viết). Bỏ dòng này khi làm FS-4.
6. **Chưa vẽ:**
   - còn lượt nhưng đơn mua thêm đang kẹt (`hasPendingFulfillment`) ở trang chi tiết, vì TM 11.3 không nói cách hiện;
   - trang chi tiết đơn của học viên `/account/orders/<code>`.

## Cần vẽ lại theo đợt 2B

Đặc tả LT và TM đã sửa ngày 2026-11-17 theo `docs/specs/2026-11-17-pbi04-dot-2b.md`. Các bản vẽ dưới đây vẽ trước lần sửa đó, nên lệch đặc tả. Khi làm task, theo đặc tả.

| Màn | Lệch | Task |
|---|---|---|
| `ChuanBiLamBai` | Chưa có bước chọn định hướng Tin học (LT 6.2: chọn ở màn chuẩn bị, như mã BCA). Chưa hiện điểm tối đa của đường. | FS-5 |
| `ChonPhan3` | Còn ô giữ chỗ "Câu theo định hướng". QDA phải hiện mã kèm tên môn theo thứ tự ("QĐ02 – Hóa học và Sinh học"). | FS-5 |
| `NopBaiXacNhan` | Còn cảnh báo "chưa chọn định hướng". | FS-5 |
| Bộ đề (chưa vẽ) | Màn admin "Bộ đề" (danh sách, tạo, sửa); thẻ và trang chi tiết bộ; trang mua bộ; nhóm thẻ theo bộ ở `/home` (TM 16). | FB |
| Làm thử như học viên (chưa vẽ) | Nút "Làm thử" ở tab Xem trước, màn làm thử, bảng điểm từng câu (2B H17). | FA-4 |

## Do agent tự đặt (đổi được khi review)

**Nhãn trạng thái**

| Nhóm | Mã → nhãn |
|---|---|
| Đơn hàng (phía học viên) | `PENDING_PAYMENT` Chờ thanh toán, `PAID` Đang kiểm tra, `FULFILLED` Đã cấp bài thi, `EXPIRED` Hết hạn, `REPLACED` Đã thay thế, `CANCELLED` Đã hủy, `REFUNDED` Đã hoàn tiền |
| Lần dùng voucher | `RESERVED` Đang giữ, `CONFIRMED` Đã dùng, `RELEASED` Đã trả lại |
| Lượt thi | `STARTING` Đang chuẩn bị đề, `GRADING_ERROR` Lỗi chấm, `ABORTED` Đã hủy; lượt chấm xong là "Đã chấm" |

- Cột "Cấp quyền" ở `/orders` có 4 giá trị: Đã cấp, Chưa cấp, Cấp quyền lỗi, Không cấp.
- Tên các loại sự kiện của lượt và trạng thái phần ở màn admin cũng do agent đặt.

**Câu chữ chưa có trong đặc tả** (đặc tả chỉ có mã)

- lỗi voucher; lý do thanh toán thất bại theo mã VNPAY;
- lỗi OTP, gửi lại quá số lần, email trùng, ảnh quá lớn, 503;
- 4 lý do `abort_reason`, hết lượt, không còn mã phục vụ;
- "mở gần đây", tab chỉ xem, "Chưa xác nhận hoàn thành phần";
- thông báo `REVIEW_NOT_INCLUDED`, mô tả mức xem kết quả;
- lỗi ảnh bìa, lỗi 412 và 409 ở admin.

**Cách hiện**

| Màn | Agent chọn |
|---|---|
| Làm bài | Phím tắt 1–4 chọn phương án, ←/→ chuyển câu, F đánh dấu. Ô đã trả lời nhưng chưa lưu có chấm đỏ. Ô Điền khuyết trả lời một phần tô nửa ô. Hộp thoại nộp bài đếm câu bỏ trống của cả bài và của phần đang làm. |
| Ghép đôi | Khi `reuseRight` tắt, lựa chọn đã dùng bị khóa ở mục khác. Trên điện thoại chỉ ghép bằng danh sách. |
| Xem lại | Tự luận chờ chấm không hiện bài viết (LT 10.2). |
| Kết quả | Nhóm "Đúng một phần" ẩn khi bằng 0. Thời gian làm thực tế không tính thời gian chờ chọn phần 3. |
| Khuyến mãi | Tạo mới phải lưu trước rồi mới thử giá (API `price-preview` cần `promotionId`). Chỉ số "Tiền đã giảm" ở chi tiết voucher chưa có trong API. |
| Người mua | Tính đơn đã thu tiền, kể cả đơn 0 đồng nhờ voucher. |

**Bố cục chung**

- Menu admin: mục Lượt thi đặt trong nhóm Người dùng, vì LT 12 ghi nhóm "Học viên" mà menu hiện chưa có nhóm này.
- Đầu trang học viên có nút sáng/tối và nút chọn ngôn ngữ theo `DESIGN.md` mục 8.
- Nút Thi (Bắt đầu, Tiếp tục ở trang chuẩn bị) dùng màu cam theo `DESIGN.md` 5.1.

**Đường dẫn giả định** (đặc tả chưa đặt): `/exams/[id]/listing`, `/promotions/new`, `/promotions/[id]/edit`, `/orders/[code]`, `/orders/buyers?exam=`.

## Dữ liệu mẫu

Giá, tên người, mã đơn, mã voucher, điểm từng ô và tiêu chí chấm là dữ liệu mẫu. Một số số câu và thời lượng là ước lượng: TSA, SPT Toán, SP2E Ngữ văn và Vật lí, THPT Tiếng Anh. Thông tin chưa có ghi dạng `[SỐ ĐIỆN THOẠI HỖ TRỢ]`, `[EMAIL HỖ TRỢ]`, `[GIỜ HỖ TRỢ]`. Ba câu hỏi thường gặp còn để trống, trong đó có chính sách hoàn tiền (TM C7, Q2).
