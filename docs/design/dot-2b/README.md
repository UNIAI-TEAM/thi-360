# Thiết kế màn hình đợt 2B · Bài thi: bước 1, bước 2, kiểm tra, sinh thử (PBI-04)

Bản xem được: https://claude.ai/artifact/4WyWEQAR8DTdZQTmQA5eG7, trang 17–19 (trang riêng tư trên claude.ai). Vẽ ngày 2026-11-17 cho UNI-2647 (cổng G2 của plan `docs/plans/2026-11-17-pbi04-dot-2b.md`), theo đặc tả `docs/specs/2026-11-17-pbi04-dot-2b.md` (2B), mục 15.

Cách đọc tệp `*.dc.html` giống `docs/design/dot-1/README.md`:

- Lấy bố cục, tên trường, câu chữ, trạng thái, dữ liệu mẫu.
- **Không chép style viết thẳng.** Mã thật dùng token và thành phần của `packages/ui` theo `DESIGN.md`.
- Đặc tả thắng bản vẽ khi hai bên khác nhau. Lệch thì sửa bản vẽ hoặc ghi ở mục "Chỗ cần chốt" dưới đây.
- Mọi màn có khối **"Trạng thái khác"** ở cuối, gồm lỗi, đang tải, thiếu quyền và các biến thể theo kỳ thi. Khối này là một phần của nghiệm thu.

Bố cục ở canvas trang 08 (`BaiThiCauTruc`, `BaiThiToHop`, `BaiThiKiemTra`, `BaiThiXemTruoc`, thư mục `docs/design/dot-2/`) đã cũ; trang 08 có ghi chú trỏ sang đây. `BaiThiToHop` bỏ, thay bằng danh sách môn của phần có mã (`PhanCoMa2B`).

Màu: `#047857` cho nền nút chính và chữ màu chính, theo `DESIGN.md` mục 2.1. Sáng và tối: mọi màn viết màu bằng biến CSS (bộ sáng theo `DESIGN.md` mục 2, bộ tối theo `[data-theme="dark"]` của `packages/ui/src/styles/tokens.css`) và có Tweaks `dark` để xem chế độ tối.

Màn bước 1, khung bước 2, kiểm tra và sinh thử vẽ cả trang (1440px). Vùng sửa (c) vẽ riêng phần vùng (1040px), không lặp lại khung trang.

## Màn nào thuộc task nào

| Task | Trang canvas | Màn |
|---|---|---|
| FE-1 · Bước 1 | 17 | `TaoBaiThi2B` (bấm thử được): QDA, chọn phạm vi cả bài / phần / môn; danh sách phần; chọn môn và thẻ `build_mode` ("Dùng cấu trúc môn QDA", "Tự thiết lập bài ôn"); bảng tổng quan sửa trên dòng, có tổng; xác nhận "Đổi và nạp lại" khi bảng đã sửa. Trạng thái khác: SPT Toán có dòng tự luận tạm bỏ qua, SP2E Địa lí Phần I, BCA theo phần, V-ACT chỉ một phạm vi, đang tải, lỗi, thiếu quyền |
| FE-2 · Khung bước 2 | 17 | `BaiThiBuoc2`: QDA FULL bốn vùng: (a) thông tin bài và "Sửa thông tin tổng quan"; (b) danh sách phần, môn, huy hiệu trạng thái, dòng mã chỉ đọc; (c) vùng sửa môn Hóa học; (d) tổng hợp, bấm lỗi để tới chỗ sai. Trạng thái khác: ô chọn phiên bản cấu trúc (SP2E Vật lí), danh sách HSA, quy tắc mở mục đầu tiên, 412, màn 390px. `HopThoaiChuaLuu2B`: hộp thoại 3 nút Ở lại / Bỏ thay đổi / Lưu và chuyển, biến thể lưu lỗi. `DoiPhienBan2B`: đổi phiên bản, hỏi nạp lại rồi hỏi giữ số đã sửa hay lấy số của mẫu (`EXAM_OVERVIEW_CONFIRM`). Nút "Thiết lập", "Môn tiếp theo" của HSA Khoa học ở `KhoaHocHSA2B` (trang 18) |
| FE-3a · Nhóm, phạm vi kiến thức | 18 | `NhomChum2B`: nhóm câu chùm Văn HSA (5 chùm × 5 câu), công tắc "Đảo thứ tự các chùm", "Toàn môn" / "Theo chủ đề", menu "Thêm điều kiện", điều kiện riêng từng chùm, điều kiện theo vị trí câu con (biến thể SP2E Tiếng Anh). `NhomCauDon2B`: nhóm câu đơn QDA Tiếng Anh câu 11–14, nhãn "khớp một trong các nhãn", điểm mỗi câu; biến thể định dạng đáp án, hình dạng Ghép đôi (lỗi `ITEM_SHAPE`), Đúng/Sai 4 ý, BCA Phát âm 0,75 điểm, MAPID, công tắc tạm bỏ qua cho nhóm tự luận |
| FE-3b · Phân bổ, chỉ tiêu, phần có mã | 18 | `PhanBoMon2B` (bấm thử được): HSA Toán, dạng trả lời, khối lớp, độ khó theo %, nhãn Bắt buộc / Tham khảo riêng cho lớp và độ khó, chỉ tiêu chủ đề "ít nhất"; phân bố tham khảo (HSA Tiếng Anh, BCA Tiếng Anh, SP2E Hóa học theo lệnh hỏi); lỗi 422 quá 8 chỉ tiêu. `PhanCoMa2B`: QDA phần 3, bảng 6 môn và mã chỉ đọc; lỗi `COMBINATION_SUBJECT_MISSING`; BCA CA1–CA4. `KhoaHocHSA2B`: 5 môn × 17 câu, lỗi `TRIM_IMPOSSIBLE`. `ChiTieuCapBai2B`: BCA điểm theo môn, chỉ tiêu cấp bài mức Tham khảo; SP2E Toán một môn. `BaiOn2B`: bài ôn rút gọn |
| FE-4 · Kiểm tra, sinh thử | 19 | `KiemTra2B` (bấm thử được): lỗi và cảnh báo tách riêng, nhóm theo môn hoặc theo mã, `affectedCodes`, trạng thái từng đơn vị; cảnh báo `COMBINATION_LIST_CHANGED`, `MANUAL_GRADING_REQUIRED`, `EXAM_QUOTA_DEVIATION`, `OMITTED_GROUPS_PRESENT`, `DISTRIBUTION_DEVIATION`; đạt, đang chạy, quá 30 giây chưa xong. `SinhThu2B` (bấm thử được): chọn phạm vi (môn, mã "QĐ02 – Hóa học và Sinh học"), bảng Yêu cầu / Thực tế, tab theo phần, câu mẫu, "Xem thứ tự gốc", "Sinh đề thử mới". `SinhThuBienThe2B`: HSA Vật lí 16 câu (đã bỏ 1 câu), SP2E Hóa học lệch phân bố tham khảo, bài đọc điền, BCA đổi mã giữ phần 1–2, `PREVIEW_EXPIRED`, sinh lỗi, đang tải |

## Chỗ cần chốt (M.Trường hoặc BA)

1. **Nhãn trạng thái đơn vị.** Brief giao việc ghi "Chưa đủ"; bản vẽ theo đặc tả: Đã kiểm tra, Cần kiểm tra lại, Chưa hoàn tất.
2. **Phân bố khối lớp của HSA Tiếng Anh.** Đặc tả cho mức Tham khảo nhưng chưa có số; bản vẽ ghi "Chờ chuyên gia xác nhận".
3. **Phân bố theo lệnh hỏi** (SP2E Hóa học) chỉ ở mức Tham khảo (2B H7). Bản vẽ khóa cả hai nút "Dùng ma trận tham khảo" và "Áp dụng phân bổ này", kèm lý do. Nếu muốn cho dùng ma trận tham khảo thì mở lại nút đầu.
4. **Phiên bản cũ SP2E Vật lí** ("Đề minh họa cũ", chia Phần I/II/III) là giả định của agent để minh họa ô chọn phiên bản; chip "Chờ chuyên gia xác nhận".
5. **Làm thử như học viên** (2B H17) thuộc đợt 3 (FA-4), vẽ ở trang 20 (`docs/design/dot-3/`). Tab Xem trước của 2B không có nút này.
6. **Tab "Thông tin bán"** (đợt 3, FA-1) không có trong dải tab của bản vẽ 2B. Khi làm cả hai đợt, dải tab theo `docs/design/dot-3/BaiThiThongTinBan.dc.html`.

## Do agent tự đặt (đổi được khi review)

**Câu chữ chưa có trong đặc tả** (đặc tả chỉ có mã)

- câu hỏi xác nhận nạp lại ở bước 1 ("Đổi và nạp lại", "Giữ lựa chọn cũ") và khi đổi phiên bản;
- lời của các lỗi và cảnh báo ở mục 7.3 (`SECTION_INCOMPLETE`, `SLOT_SOURCE_SHORTAGE`, `ITEM_SHAPE`, `TRIM_IMPOSSIBLE`, `COMBINATION_SUBJECT_MISSING`, `PREVIEW_EXPIRED` và các cảnh báo ở `KiemTra2B`);
- dòng tóm tắt ở vùng tổng hợp, ví dụ "Mã QĐ01: Đã phân bổ 47/50 câu. Còn thiếu 3 câu.";
- dòng "(17, đã bỏ 1 câu)" ở bảng Yêu cầu / Thực tế của HSA Khoa học;
- nhãn "· đáp án đúng" khi xem thứ tự gốc.

**Cách hiện**

| Màn | Agent chọn |
|---|---|
| Bước 1 | Ô phiên bản không hiện ở bước 1 (K8); bảng tổng quan có chip "Phiên bản cấu trúc chọn ở bước 2". Thẻ `build_mode` chỉ hiện khi chọn môn. Đổi số câu trên bảng không nạp lại mẫu. |
| Bước 2 | Huy hiệu trạng thái đặt cạnh tên phần, môn ở vùng (b). Dòng mã của phần có mã chỉ đọc, ghi vị trí câu theo môn. Màn 390px chỉ đọc. |
| Hộp thoại chưa lưu | Chuyển trong ứng dụng dùng hộp thoại 3 nút; đóng tab hay tải lại trang dùng hộp thoại của trình duyệt. |
| Phân bổ | Độ khó nhập theo %, hiện số câu quy đổi cạnh từng ô. Bảng có dòng tổng. |
| Chỉ tiêu cấp bài | Chỉ tiêu trải nhiều môn khóa nút "Bắt buộc" (2B H6). |
| Kiểm tra | Nút "Nhóm theo" Môn / Mã; nhóm theo mã thì gom lỗi theo `affectedCodes`. |
| Sinh thử | Bài có phần chọn mã thì "Cả bài" khóa, ghi "hãy chọn một mã". Dòng lệch phân bố tham khảo tô màu cảnh báo, dòng lệch mức Bắt buộc tô màu lỗi. |

## Dữ liệu mẫu

Số câu, thời lượng, điểm của HSA (150 câu, 195 phút, 150 điểm), QDA (50 câu mỗi phần, thời lượng 80/55/60 phút), BCA (81 câu, 180 phút, 100 điểm) theo đặc tả. Các mã QĐ01–QĐ05, CA1–CA4 và cặp môn là ví dụ. Kết quả kiểm tra, số chùm của các môn QDA, câu hỏi mẫu, tên chủ đề, nhãn câu hỏi và các ma trận HSA Khoa học là dữ liệu mẫu.
