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
7. **Dải tab và nút Công bố của KiemTra2B (AD-106, đã chốt: giữ như ứng dụng).** Bản vẽ tách hai tab Kiểm tra và Xem trước, có nút "Công bố" ở đầu trang Kiểm tra. Ứng dụng giữ một tab "Kiểm tra và xem trước" có hai tab con; nút Công bố ở tab Phiên bản như hiện có. Đặc tả thắng bản vẽ.

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

## Bản vẽ bổ sung G2b · V-ACT, THPTQG (UNI-2678)

Vẽ ngày 2026-11-18 theo `docs/specs/2026-11-18-pbi04-dot-2b-bo-sung-vact-thpt.md` (gọi tắt **spec bổ sung**) và `docs/plans/2026-11-18-pbi04-dot-2b-bo-sung-vact-thpt.md`. Sáu màn dưới đây chưa đưa lên canvas. Cách đọc, màu và bề rộng theo phần đầu của tệp này.

### Màn nào thuộc task nào

| Task | Màn | Bề rộng | Nội dung | Dòng nghiệm thu |
|---|---|---|---|---|
| FE-3b (phần thêm) | `PhanBoVact2B` (bấm thử được) | 1040px | Hai cột Số câu và Tỷ lệ, nhập theo số câu hoặc tỷ lệ; dòng Phần còn lại; Chờ mapping và nút Gắn MAPID; "Chưa có dữ liệu đối chiếu"; tiến độ phân bổ; "Đủ nguồn theo ma trận"; ô số câu của mục chỉ đọc khi mẫu khóa; nhãn "ít nhất"; bảng tham khảo 8 dòng của Tiếng Việt. Trạng thái khác: bảng cấp nhóm 18 câu, tổng tỷ lệ khác 100%, vượt số câu, đổi tổng 30 xuống 20, THPTQG Địa lí | FE-3b 1–12 |
| FE-3a (phần thêm) | `NhomChumLinhHoat2B` (bấm thử được) | 1040px | Chùm cỡ linh hoạt: chỉ nhập tổng câu con hoặc khai số chùm × số câu con; ô "Mọi câu con thuộc chủ đề"; nhóm Đúng/Sai với dòng "4 câu · 16 ý · tối đa 4 điểm". Trạng thái khác: không ghép được đúng tổng, thiếu chùm 10 câu con, bài ôn bật sẵn ô | FE-3a 1–4 |
| FE-4 (phần thêm) | `KiemTraVact2B` | 1440px | Câu chữ của ba mã kiểm tra mới; Logic và Suy luận chung một đơn vị kiểm tra; Ngữ văn khóa nút Công bố | FE-4 4 |
| FE-4 (phần thêm) | `SinhThuVact2B` | 1440px | Cột Chênh lệch; "Chưa có dữ liệu đối chiếu" ở ô Thực tế và Chênh lệch; điểm tối đa từng câu (V-ACT 10 điểm; THPTQG Toán 0,25 / 1 / 0,5). Trạng thái khác: không sinh được do chờ mapping hoặc thiếu nguồn | FE-4 1–3 |
| FE-5 | `TaoBaiThiVact2B` (bấm thử được) | 1440px | Toàn bài, Theo phần, Theo môn với tên đọc được; số câu khóa, tỷ lệ trong phần và toàn bài; lỗi thiếu thời lượng của bài riêng; bài ôn | FE-5 1–4 |
| FE-5 | `TaoBaiThiThpt2B` (bấm thử được) | 1440px | Theo môn, Theo phần (chọn môn rồi chọn phần); đủ 12 môn; thông báo Ngữ văn, "Chỉ lưu nháp"; lỗi thiếu thời lượng của bài theo phần | FE-5 5–6 |

Dòng "Kỳ thi chỉ có một phạm vi (V-ACT, THPTQG)" trong khối Trạng thái khác của `TaoBaiThi2B` hết hiệu lực từ spec bổ sung mục 1; bước 1 của hai kỳ thi này theo `TaoBaiThiVact2B` và `TaoBaiThiThpt2B`.

### Đã chốt khi nghiệm thu bản vẽ (Advisor, 2026-11-18)

Các mục 1–4 giữ như bản vẽ. Mục 5 chốt: FE-4 giữ một cách hiện theo `KiemTra2B`, tức lời lỗi là chính và mã kỹ thuật là chữ phụ, cho mọi kỳ thi.

1. **Dòng "4 câu · 16 ý · tối đa 4 điểm".** Yêu cầu giao việc đặt dòng này ở khối chùm linh hoạt, còn R-107 và FE-3a dòng 4 gắn nó với nhóm Đúng/Sai. Bản vẽ theo spec: nhóm Đúng/Sai dùng đúng dòng này; chùm linh hoạt không có "ý" nên ghi "18 câu con · tối đa 180 điểm".
2. **Tên mức của bảng.** Ghi chú BA dùng "Áp dụng khi sinh đề" và "Chỉ đối chiếu". Bản vẽ giữ "Bắt buộc" và "Tham khảo" như các màn 2B đã có.
3. **Tên hai môn Công nghệ.** Ghi chú BA viết "Công nghệ – Công nghiệp", danh mục môn viết "Công nghệ (định hướng công nghiệp)". Bản vẽ theo danh mục vì ô Môn đọc từ danh mục.
4. **Theo phần của Tiếng Anh và Ngữ văn.** Hai mẫu này chỉ có một phần (H23), nên ô chọn phần chỉ có một lựa chọn. Ngữ văn theo phần vẫn hiện thông báo và chỉ lưu nháp.
5. **Mã kiểm tra trên màn.** `KiemTra2B` hiện mã kỹ thuật cạnh lời lỗi; `KiemTraVact2B` chỉ hiện lời, vì G2b yêu cầu không để lộ mã thô. Bảng dưới ghi mã ứng với từng lời. Nếu muốn thống nhất thì cần chốt một trong hai cách.

### Do agent tự đặt (đổi được khi review)

**Lời của ba mã kiểm tra mới**

| Mã | Lời trên màn |
|---|---|
| `TOPIC_QUOTA_MAPPING_MISSING` | "Dòng Xác suất (ít nhất 3 câu, mức Bắt buộc) chưa gắn MAPID. Gắn mã rồi kiểm tra lại." |
| `STRUCTURE_COUNT_LOCKED` | "Tiểu mục đang có 10 câu, mẫu V-ACT toàn bài giữ 12 câu. Đưa số câu về 12." |
| `TEMPLATE_NOT_PUBLISHABLE` | Tiêu đề "Môn này chưa công bố được", sau đó là câu thông báo Ngữ văn và "Bài chỉ lưu nháp, không công bố và không đưa vào bộ đề bán." |

**Câu chữ chưa có trong đặc tả**

- "Đã phân bổ 32/30 · Vượt 2 câu" (đặc tả chỉ nói "báo vượt 2 câu");
- "Mẫu đã khóa số câu", "Hệ thống tự tính, không lưu", "Thực lấy 26,7%", "Mẫu số: 30 câu của Toán học";
- lỗi tổng tỷ lệ "Tổng tỷ lệ đang là 90%. Bảng này phân bổ toàn bộ số câu nên tổng phải bằng 100%.";
- xác nhận đổi tổng: "Áp dụng số mới", "Giữ số cũ";
- "Chỉ nhập tổng câu con", "Khai số chùm và số câu con", "Tổng số câu con", "18 câu con · tối đa 180 điểm";
- lời giải thích của ô "Mọi câu con thuộc chủ đề" và lỗi "Thiếu chùm phù hợp";
- "Chưa nhập thời lượng. Bài riêng của V-ACT không có thời lượng mặc định." và câu tương ứng của bài theo phần, bài ôn THPTQG;
- "Chỉ lưu nháp", "Bài này chỉ lưu nháp, không công bố được và không đưa vào bộ đề bán.", nút "Lưu nháp và tiếp tục";
- "Dùng cấu trúc môn V-ACT", "Dùng cấu trúc môn THPTQG";
- chip "Chờ mapping" ở cột Kết quả của bảng đối chiếu.

**Cách hiện**

| Màn | Agent chọn |
|---|---|
| Phân bổ | Dòng Phần còn lại nằm ngay dưới các chỉ tiêu, không có ô nhập. Nhập theo tỷ lệ thì cột Số câu chỉ đọc và hiện "Thực lấy" cạnh ô tỷ lệ. Tổng tính từ số câu gốc nên sáu dòng 16,7% vẫn ghi 100%. |
| Chùm linh hoạt | Chế độ chỉ nhập tổng ẩn hẳn ô Số chùm và Số câu con mỗi chùm. Ô "Mọi câu con thuộc chủ đề" chỉ hiện khi chọn Theo chủ đề. |
| Kiểm tra | Lỗi Ngữ văn không có liên kết "Mở chỗ lỗi" vì không có ô nào để sửa. |
| Sinh thử | Chênh lệch bằng Thực tế trừ Yêu cầu, có dấu + hoặc −. Chỉ tiêu "ít nhất" có thực tế nhiều hơn yêu cầu vẫn là Đạt. |
| Bước 1 V-ACT | Thời lượng chung là một ô riêng trên bảng; bài toàn bài điền sẵn 150 phút và vẫn sửa được. Điểm tối đa bằng số câu nhân 10, chỉ đọc. Bảng không hiện dải câu chạy suốt bài (H34). |
| Bước 1 THPTQG | Mở màn ở Ngữ văn để thấy ngay thông báo. Các dòng của Ngữ văn không gạch bỏ và vẫn tính 7 câu, 10 điểm (H26). |

**Dữ liệu mẫu:** số câu, điểm, thời lượng, tỷ lệ theo spec bổ sung và ghi chú BA. Cột Thực tế ở `SinhThuVact2B`, câu hỏi mẫu, dòng Xác suất, lỗi Logic 10 câu và tên bài là dữ liệu mẫu.

## Bản vẽ bổ sung G2c · H-SCA, V-SAT (UNI-2697)

Vẽ ngày 2026-11-18 theo `docs/specs/2026-11-18-pbi04-dot-2b-bo-sung-hsca-vsat.md` (gọi tắt **spec H-SCA, V-SAT**) và `docs/plans/2026-11-18-pbi04-dot-2b-bo-sung-hsca-vsat.md`. Năm màn dưới đây chưa đưa lên canvas. Cách đọc, màu và bề rộng theo phần đầu của tệp này.

### Màn nào thuộc task nào

| Task | Màn | Bề rộng | Nội dung | Dòng nghiệm thu |
|---|---|---|---|---|
| FE-6 | `TaoBaiThiHscaVsat2B` (bấm thử được) | 1440px | Kỳ thi, môn, phạm vi; H-SCA Tiếng Anh có Nghe, Đọc, Nghe và Đọc; ô thời lượng điền sẵn hoặc bắt buộc nhập; số câu chỉ đọc ở bài cả môn của mẫu khóa dòng; V-SAT Tiếng Anh theo phần chỉ có Phần II, III, IV; phần Viết không có trong danh sách. Trạng thái khác: lỗi thiếu thời lượng, V-SAT Hóa học, khung hẹp, đang tải, lỗi, thiếu quyền | FE-6 1–5, 10–12 |
| FE-7 | `NhomAudio2B` (bấm thử được) | 1040px | Dòng lấy câu phần Nghe 8 × 1, 3 × 4, 3 × 5: nhãn Có audio, tổng câu, tỷ lệ, nguồn khả dụng; nhập theo số lượng hoặc tỷ lệ; phần Đọc nhãn Không có audio; dòng bị khóa của H-SCA Toán. Trạng thái khác: tỷ lệ không ra chùm nguyên, thiếu nguồn có audio, sai điểm, vượt số câu, bài theo phần | FE-7 1–5 |
| FE-7, FE-8 | `BaiDocDungChung2B` | 1040px | V-SAT Tiếng Anh câu 8–15: điều kiện theo vị trí, 8 câu, 14 lệnh, 48 điểm; dải 25 câu với ranh giới Phần I và II cắt giữa chùm; mạch đối chiếu theo lệnh; đề thử có bốn tiêu đề Phần I–IV, bài đọc hiện ở cả hai phần | FE-7 6, 9; FE-8 2, 3 |
| FE-7 | `ViDuCham2B` (bấm thử được) | 1040px | V-SAT "Đúng 3/4: Đúng/Sai 3 điểm, Ghép đôi 4,5 điểm", bảng điểm hai dạng, các dạng còn lại; H-SCA đúng, đúng một phần, sai, bỏ trống kèm chú thích chờ BA xác nhận | FE-7 7, 8 |
| FE-8 | `SinhThuAudio2B` (bấm thử được) | 1440px | Trình phát ở câu đơn và ở đầu chùm, thao tác bằng bàn phím; đối chiếu theo lệnh "Toán cả môn · 67 lệnh" có dòng Chưa phân loại. Trạng thái khác: lỗi phát, tệp mất khỏi kho lưu trữ, câu chữ của năm mã kiểm tra mới | FE-8 1, 4, 5 |

Trình phát trong bản vẽ chỉ mô phỏng thao tác, không phát tiếng và thanh vị trí không tự chạy.

### Chỗ cần chốt (M.Trường hoặc BA)

1. **Dòng bị khóa không vẽ trên mẫu Nghe.** Yêu cầu giao việc đặt trạng thái khóa cùng mẫu Nghe 8 × 1, 3 × 4, 3 × 5, nhưng spec H-SCA, V-SAT H42 chỉ bật khóa dòng cho bốn môn H-SCA 40 câu và tám môn V-SAT; mục 4.3 cho H-SCA Tiếng Anh chỉnh được. Bản vẽ theo spec: phần Nghe sửa được, trạng thái khóa vẽ trên H-SCA Toán.
2. **Chữ "FULL" trên màn (đã chốt, D21).** Mẫu số của bảng đối chiếu ghi "Toán cả môn · 67 lệnh", tiếng Anh "Math, whole subject · 67 commands". Dòng nghiệm thu FE-8 5 đọc theo câu chữ này.
3. **Tên dạng ghép cặp (đã chốt, D22).** Mọi màn dùng "Ghép đôi", đúng nhãn loại câu hỏi của ngân hàng. Ghi chú BA và spec viết "Ghép hợp" cho cùng dạng này; Advisor báo BA.
4. **Phần IV của V-SAT Tiếng Anh.** Các môn khác là Trả lời ngắn; Tiếng Anh là một đoạn năm vị trí (spec mục 5.2). Bản vẽ ghi "Điền khuyết" theo tên loại câu hỏi của ngân hàng, không dùng chữ "Cloze".
5. **Tên phần của H-SCA Ngữ văn.** Spec mục 4.2 không đặt tên phần; bản vẽ ghi "Đọc hiểu" theo ghi chú BA mục 1.
6. **Chữ "BA" trên màn.** Dòng nghiệm thu FE-7 8 yêu cầu chú thích luật chờ BA xác nhận, nên `ViDuCham2B` ghi đúng như vậy. Chú thích này bỏ khi BA chốt Q-32.

### Do agent tự đặt (đổi được khi review)

**Lời của năm mã kiểm tra mới**

| Mã | Lời trên màn |
|---|---|
| `AUDIO_SHORTAGE` | "Nghe › Dòng 2 · Hội thoại 4 câu: cần 3 chùm × 4 câu có audio, nguồn hiện ghép được 2 chùm." |
| `POINTS_SHORTAGE` | "Phần III · Ghép đôi. Cần 5 câu 6 điểm, nguồn chỉ có 2 câu đúng điểm." |
| `BLOCK_COUNT_LOCKED` | "Phần 1 › Dòng 1 · Chọn một. Dòng đang có 22 câu, mẫu giữ 20 câu. Đưa số câu về 20." |
| `OMITTED_BLOCK_LOCKED` | "Ngữ văn › Phần Viết. Phần này chưa có trong V1 nên không bật lại được. Hãy tắt nhóm này." |
| `SCALE_INCLUDES_EXCLUDED_PART` | "V-SAT Ngữ văn. Thang đang là 150, tổng điểm của bài là 120. Chênh 30 điểm đúng bằng phần Viết. Đặt thang 120." |

**Câu chữ chưa có trong đặc tả**

- phạm vi "Cả môn", "Theo phần" và lời gợi ý dưới từng lựa chọn;
- "Chưa nhập thời lượng. Môn này không có thời lượng mặc định.", "Chưa nhập thời lượng. Bài theo phần phải có thời lượng riêng.", "Chưa nhập thời lượng của bài ôn.";
- "Điền sẵn theo mẫu, sửa được.", "Bài theo phần không lấy thời lượng của cả môn.", "Mẫu của môn này để trống thời lượng.";
- "Phần Viết chưa có trong V1 nên không chọn được.", "Tiếng Anh luyện riêng được Phần II, III, IV. Phần I không có bài riêng.";
- "Dùng cấu trúc môn H-SCA", "Dùng cấu trúc môn V-SAT";
- chip "Mẫu đã khóa dòng", lý do "Bài cả môn giữ nguyên dạng và số lượng của từng dòng theo mẫu H-SCA. Muốn đổi, hãy tạo bài theo phần từ cùng mẫu.";
- cột "Nhãn audio", "Nguồn khả dụng", "Tổng câu"; tên dòng "Hội thoại 4 câu", "Bài nghe 5 câu", "Bài đọc 10 câu";
- lỗi "Không ghép được chùm nguyên. Tỷ lệ 28,6% ra 10 câu, không chia hết cho cỡ chùm 4 câu con. Chọn 8 hoặc 12 câu.";
- "Loại câu hỏi theo vị trí trong chùm", "Vị trí của nhóm trong bài", "Phần hiển thị của bài", "Ranh giới Phần I và Phần II nằm giữa chùm.", "Cùng bài đọc với câu 8–9";
- lỗi "Đã khai 36 lệnh, 54 điểm · Đề thử có 35 lệnh, 54 điểm." khi đề thử lệch số đã khai của phần;
- "Ví dụ chấm", "Số ý đúng trong câu", "Đang xem", "Không áp dụng", "Các dạng còn lại";
- nhãn trình phát "Phát", "Tạm dừng", "Lùi 5 giây", "Tiến 5 giây", "Vị trí phát", "Tắt tiếng", "Bật tiếng", "Đang tải audio", "Tệp này chỉ phát từ đầu", "Tệp 1/2";
- "Thiếu nhãn" ở cột Kết quả của dòng Chưa phân loại.

**Cách hiện**

| Màn | Agent chọn |
|---|---|
| Bước 1 | Bản vẽ mở ở H-SCA Tiếng Anh, Nghe để thấy ngay ba phạm vi; khi chọn môn, mặc định là Cả môn (Nghe và Đọc với H-SCA Tiếng Anh). Ô Kỳ thi của bản vẽ chỉ bấm được H-SCA, V-SAT; màn thật liệt kê đủ 9 kỳ thi. Thời lượng điền sẵn vẫn sửa được, như bài toàn bài của V-ACT. Dòng chùm nhập số chùm nên bước 1 không thể ra chùm lẻ. Cột Lệnh hỏi chỉ có ở V-SAT. |
| Dòng lấy câu | Nhãn audio là chip chỉ đọc, đặt ở từng dòng và ở tiêu đề phần. Bấm tên dòng để mở phần sửa bên dưới bảng. Nhập theo tỷ lệ thì cột Số lượng chỉ đọc. Tỷ lệ quy ra câu theo cách lấy phần nguyên rồi chia phần dư cho dòng có phần lẻ lớn nhất (ghi chú BA H-SCA mục 3). |
| Dòng bị khóa | Khóa dạng và số lượng; tên dòng, nguồn, phạm vi kiến thức và chỉ tiêu trong dòng vẫn sửa được. |
| Bài đọc dùng chung | Bài đọc luôn mở ở cả hai phần, không thu gọn. Mạch đối chiếu theo lệnh (FE-7 dòng 9) đặt ở màn này vì G2c không có màn riêng, chỉ có mức Tham khảo và không có nút chuyển sang Bắt buộc. |
| Ví dụ chấm | Khối chỉ đọc. Chọn số ý đúng thì câu so sánh và hai bảng đổi theo. Nút V-SAT, H-SCA chỉ có trong bản vẽ; màn thật hiện theo kỳ thi của bài. |
| Trình phát | Dùng nút riêng thay cho điều khiển gốc của trình duyệt, vì chữ của điều khiển gốc đổi theo ngôn ngữ trình duyệt (cùng lý do với nút chọn tệp ở `DESIGN.md` mục 5.14). Mỗi lần chỉ một trình phát chạy. Trạng thái "Tệp này chỉ phát từ đầu" dành cho trường hợp máy chủ chưa hỗ trợ tua (rủi ro ở mục 6 của plan). |
| Năm mã kiểm tra | G2c không có màn Kiểm tra riêng. Lời của năm mã nằm ở khối Trạng thái khác của `SinhThuAudio2B`; bố cục theo `KiemTra2B`. |

**Dữ liệu mẫu:** số câu, lệnh, điểm, thời lượng, tỷ lệ theo spec H-SCA, V-SAT và hai ghi chú BA. Cột Thực tế của bảng đối chiếu, số nguồn khả dụng, tên tệp audio, bài đọc, câu hỏi tiếng Anh và tên bài là dữ liệu mẫu.
