# Thiết kế màn hình đợt 3 · Thương mại (PBI-05–09), lượt thi (PBI-10–13)

Bản xem được: https://claude.ai/artifact/4WyWEQAR8DTdZQTmQA5eG7, trang 10–16 và trang 20 (trang riêng tư trên claude.ai). Vẽ ngày 2026-11-17 cho UNI-2622; sửa và bổ sung theo đợt 2B cùng ngày (UNI-2647, trang 12 và 20), theo hai đặc tả `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md` (TM) và `docs/specs/2026-11-16-luot-thi-pbi-10-13.md` (LT).

Cách đọc tệp `*.dc.html` giống `docs/design/dot-1/README.md`:

- Lấy bố cục, tên trường, câu chữ, trạng thái, dữ liệu mẫu.
- **Không chép style viết thẳng.** Mã thật dùng token và thành phần của `packages/ui` theo `DESIGN.md`.
- Đặc tả thắng bản vẽ khi hai bên khác nhau. Lệch thì sửa bản vẽ hoặc ghi ở mục "Chỗ cần chốt" dưới đây.
- Nhiều màn có khối **"Trạng thái khác"** ở cuối, gồm rỗng, lỗi, đang tải, thiếu quyền và các biến thể. Khối này là một phần của nghiệm thu.

Màu: các màn mới dùng `#047857` cho nền nút chính và chữ màu chính, theo `DESIGN.md` mục 2.1. 14 màn làm bài cũ chép từ đợt trước còn `#059669`; khi đó token trong `DESIGN.md` thắng.

Sáng và tối: 7 màn của trang 20 viết màu bằng biến CSS (bộ sáng theo `DESIGN.md` mục 2, bộ tối theo `[data-theme="dark"]` của `packages/ui/src/styles/tokens.css`) và có Tweaks `dark` để xem chế độ tối. Các màn trang 10–16 chỉ vẽ chế độ sáng.

## Màn nào thuộc task nào

| Task | Trang canvas | Đường dẫn | Màn |
|---|---|---|---|
| FS-1 | 10 | `/`, `/exams/[code]`, 410, 404 | `TrangChuDot3` (6 khối, danh mục ở `#exams`) · `ChiTietBaiThiBan` (nút theo `exam-access`, đủ 8 tình huống ở khối trạng thái khác) · `BaiThiNgungBan` (410, kèm 404) |
| FS-2 | 10 | `/register`, `/verify-email`, `/forgot-password`, `/reset-password`, `/account/profile` | `DangKy` · `XacMinhEmail` · `QuenMatKhau` · `DatLaiMatKhau` · `HoSo`. Đăng nhập theo `docs/design/dot-1/Main.dc.html` |
| FS-3 | 11 | `/checkout/[code]`, `/checkout/result`, `/home`, `/account/orders` | `XacNhanMua` · `ThanhToanThanhCong` · `ThanhToanDangKiemTra` · `ThanhToanThatBai` · `BaiThiCuaToi` · `DonCuaToi` |
| FS-4 | 13 (mới), 06 và 09 (cũ) | thành phần `packages/ui` | Dạng mới: `ThiTraLoiNgan`, `ThiDienKhuyet`, `ThiGhepDoi` (bấm thử được), `ThiTuLuan`, `ThiMobileGhepDoi`. Chế độ `review`: `CauHoiCheDoXemLai`. Dạng cũ, chỉ lấy phần thân câu: `CauHoi1DapAnThi`, `CauHoiNhieuDapAnThi`, `CauHoiDungSaiThi`, `CauHoiChumThi` và các màn `ThiMobile*`. `CauHoiDienDapAnThi` ứng với Điền khuyết; `CauHoiKeoThaThi`, `ThiMobileKeoTha` đã bỏ (thay bằng Ghép đôi) |
| FS-5 | 12 | `/prepare/[entitlementId]`, `/attempts/[id]` | `ChuanBiLamBai` (chọn mã BCA và định hướng Tin học trước khi bắt đầu; số câu, thời lượng, điểm tối đa của đường) · `DangChuanBiDe` · `LamBaiTheoPhan` · `LamBaiMobileTheoPhan` · `HoanThanhPhan` · `ChonPhan3` (bấm thử được; QDA hiện mã kèm tên môn theo thứ tự) · `NopBaiXacNhan` · `TiepQuanLuot`. Khung đầu trang và bảng số câu theo phần ở đây thay cho khung của các màn `*Thi` cũ |
| FS-6 | 14 | `/attempts/[id]/result`, `/attempts/[id]/review`, `/history` | `KetQuaLuot` · `KetQuaChoCham` · `XemLaiBaiLam` · `LichSuLamBai` |
| FA-1 | 15 | tab Thông tin bán, tab Kiểm tra, menu | `BaiThiThongTinBan` · `BaiThiKiemTraThuongMai`. Menu: nhóm Thương mại (Khuyến mãi, Đơn hàng), mục Lượt thi |
| FA-2 | 15 | `/promotions` | `KhuyenMai` (bấm thử được tab và dải trạng thái) · `TaoKhuyenMai` (Tweaks `mode`: sửa voucher đã dùng / tạo mới) · `ChiTietKhuyenMai` |
| FA-3 | 16 | `/orders`, chi tiết đơn, `/orders/buyers` | `DonHang` · `ChiTietDonHang` · `HoanTienDonHang` (Tweaks `variant`: api, api409, manual, manualConfirm, unknown) · `NguoiMua` |
| FA-4 | 16, 20 | `/attempts`, `/attempts/[id]`; `/grading` giữ màn đợt 2; làm thử ở tab Xem trước của bài thi | `LuotThiAdmin` · `ChiTietLuotThiAdmin` · làm thử như học viên (trang 20): `LamThuNhuHocVien` (có nút vào ở tab Xem trước, bấm thử được phần và câu) · `KetQuaLamThu` (điểm từng câu, từng ý Đúng/Sai) |
| FB | 20 | `/bundles`, `/bundles/new`, `/bundles/[id]`; `/exams/[code]` với mã bộ; `/checkout/[code]` với mã bộ; `/home` | Admin: `BoDeAdmin` (dải trạng thái bấm thử được) · `TaoBoDe` (tích bài và đổi thứ tự bấm thử được). Học viên: `ChiTietBoDe` (kèm thẻ bộ ở danh mục) · `XacNhanMuaBo` · `BaiThiCuaToiTheoBo` |

## Chỗ cần chốt (M.Trường hoặc BA)

1. **`wordLimit` của câu Tự luận** là số chữ tối thiểu hay tối đa. Bản vẽ hiểu là tối thiểu, theo ví dụ bài viết 500 chữ của BCA.
2. **Nhãn lượt còn câu chưa chấm.** `DESIGN.md` 5.14 ghi "Đang chấm", LT ghi "Chờ chấm". Bản vẽ theo LT; cần sửa `DESIGN.md` khi chốt.
3. **Đánh số câu.** Bản vẽ đánh lại từ 1 ở mỗi phần. Đặc tả không nói; UniNote V-ACT đánh liền 1–120.
4. **UniNote khác L7.** UniNote PBI-10 mục 6.3 và 12.1 cho đề đồng hồ chung quay lại phần trước. Bản vẽ theo L7: làm tuần tự, không quay lại.
5. **Màn cũ `CauHoiDienDapAnThi`** có dòng "Số thập phân gõ bằng dấu phẩy hoặc dấu chấm đều được", trái BE B7 (khớp đúng cách viết). Bỏ dòng này khi làm FS-4.
6. **Thời lượng Tin học và Văn SP2E.** `ChuanBiLamBai` ghi THPTQG Tin học 50 phút theo bảng kỳ thi của đợt 1; SPT V1 và Văn SP2E "Đọc hiểu" để quản trị viên nhập (2B mục 10, R-57, R-68), bản vẽ ghi `[THỜI LƯỢNG]` hoặc "theo bài đã công bố".
7. **Giá bộ đề** và giá lẻ ở trang 20 là dữ liệu mẫu. TM 16.2 không cho tự tính giảm so với tổng giá lẻ, nên màn không hiện mức tiết kiệm.
8. **Chưa vẽ:**
   - còn lượt nhưng đơn mua thêm đang kẹt (`hasPendingFulfillment`) ở trang chi tiết, vì TM 11.3 không nói cách hiện;
   - trang chi tiết đơn của học viên `/account/orders/<code>`.

## Đã vẽ lại theo đợt 2B

Đặc tả LT và TM đã sửa ngày 2026-11-17 theo `docs/specs/2026-11-17-pbi04-dot-2b.md`. Các bản vẽ dưới đây đã sửa hoặc vẽ mới cùng ngày (UNI-2647).

| Màn | Trang | Đã đổi | Task |
|---|---|---|---|
| `ChuanBiLamBai` | 12 | Thêm khối chọn định hướng Tin học ở màn chuẩn bị (THPTQG, SPT; như chọn mã BCA), mỗi định hướng ghi số câu, thời lượng, điểm tối đa của đường. Cấu trúc và thẻ "Sẵn sàng làm bài" hiện điểm tối đa; khối mã BCA ghi số của đường. | FS-5 |
| `ChonPhan3` | 12 | Bỏ ô giữ chỗ "Câu theo định hướng" (thay bằng ghi chú trỏ về màn chuẩn bị). QDA hiện mã kèm tên môn theo thứ tự, ví dụ "QĐ02 – Hóa học và Sinh học", dòng phụ "Hóa học câu 1–25, Sinh học câu 26–50". | FS-5 |
| `NopBaiXacNhan` | 12 | Bỏ cảnh báo "chưa chọn định hướng"; biến thể Tin học ghi định hướng đã chọn và mẫu số 28 câu (SPT V1 22 câu). | FS-5 |
| `BoDeAdmin`, `TaoBoDe` | 20 | Mới: danh sách bộ đề (trạng thái Nháp, Đang bán, Không bán được, Ngừng bán), tạo và sửa bộ (2–12 bài đang bán cùng kỳ thi, giá, lượt mỗi bài, quyền lợi, mức xem kết quả, công bố, ngừng bán, `UNAVAILABLE`). | FB |
| `ChiTietBoDe`, `XacNhanMuaBo`, `BaiThiCuaToiTheoBo` | 20 | Mới: thẻ bộ ở danh mục và trang chi tiết bộ, xác nhận mua bộ, `/home` nhóm thẻ theo bộ. | FB |
| `LamThuNhuHocVien`, `KetQuaLamThu` | 20 | Mới: nút "Làm thử như học viên" ở tab Xem trước, màn làm thử dùng thành phần câu của màn làm bài, bảng điểm từng câu và từng ý Đúng/Sai; không tạo lượt (2B H17, K12). | FA-4 |

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

**Bộ đề và làm thử (trang 20, agent đặt)**

| Màn | Agent chọn |
|---|---|
| Bộ đề admin | Nhãn trạng thái: `DRAFT` Nháp, `PUBLISHED` Đang bán, `DISCONTINUED` Ngừng bán; bộ có `sale_state = UNAVAILABLE` ghi "Không bán được" kèm tên bài đã ngừng bán; `has_unpublished_changes` ghi "Có thay đổi chưa công bố". Thứ tự bài trong bộ đổi bằng nút lên, xuống. Đổi kỳ thi khi đã chọn bài thì hỏi xác nhận và bỏ bài đã chọn. |
| Bộ đề học viên | Thẻ bộ dùng dải màu `hero-dark` và icon hộp thay ảnh bìa. Trang chi tiết bộ ghi quyền lợi dưới từng bài. Bộ `UNAVAILABLE`: nút Mua bộ đề khóa, ghi "Bộ đề tạm thời chưa bán được". Mã khuyến mãi phạm vi bài thi dùng cho bộ thì báo "Mã này chỉ áp cho một số bài thi, không áp cho bộ đề". |
| `/home` theo bộ | Bài nằm trong hai bộ thì thẻ hiện một lần, dưới bộ mua gần nhất; bộ còn lại ghi dòng trỏ tới nhóm đó. Lượt còn lại gồm cả lượt mua lẻ của cùng bài. |
| Làm thử | Mở trong tab mới. Không tính giờ, chuyển qua lại giữa các phần được. Câu trả lời không lưu ở máy chủ trước khi nộp; rời trang thì hỏi "Bỏ lần làm thử này?". Câu tự luận ghi "Chờ chấm", không có điểm tự động. |

**Đường dẫn giả định** (đặc tả chưa đặt): `/exams/[id]/listing`, `/promotions/new`, `/promotions/[id]/edit`, `/orders/[code]`, `/orders/buyers?exam=`, `/bundles/new`, `/bundles/[id]`, trang làm thử `/exams/[id]/preview/[previewId]/try`.

## Dữ liệu mẫu

Giá, tên người, mã đơn, mã voucher, điểm từng ô và tiêu chí chấm là dữ liệu mẫu. Một số số câu và thời lượng là ước lượng: TSA, SPT Toán, SP2E Ngữ văn và Vật lí, THPT Tiếng Anh. Thông tin chưa có ghi dạng `[SỐ ĐIỆN THOẠI HỖ TRỢ]`, `[EMAIL HỖ TRỢ]`, `[GIỜ HỖ TRỢ]`. Ba câu hỏi thường gặp còn để trống, trong đó có chính sách hoàn tiền (TM C7, Q2).
