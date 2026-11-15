# Thiết kế màn hình đợt 2 · PBI-03 Ngân hàng câu hỏi, PBI-04 Bài thi

Bản xem được: https://claude.ai/artifact/4WyWEQAR8DTdZQTmQA5eG7, trang 05–08 (trang riêng tư trên claude.ai). Cách đọc tệp `*.dc.html` giống `docs/design/dot-1/README.md`: lấy bố cục, tên trường, câu chữ, trạng thái, dữ liệu mẫu; **không chép style viết thẳng**, mã thật dùng token và thành phần của `packages/ui` theo `DESIGN.md`.

Đợt này là **mockup trên dữ liệu mẫu** (UNI-2590): chưa có backend, quy ước ở `DESIGN.md` mục 5.14. Hai tệp `uninote-*.md` là bản chép UniNote của BA ngày 2026-11-14 để đối chiếu nghiệp vụ.

## Màn nào thuộc issue nào

| Issue | Đường dẫn | Màn |
|---|---|---|
| UNI-2591 nền | menu Nội dung, `/exams/[id]` (khung) | Khung trang soạn bài thi lấy theo phần đầu của `BaiThiCauTruc`; `ChonKienThuc` bản đầu |
| UNI-2592 | `/questions` | `NganHangCauHoi` danh sách, bộ lọc · `ChonKienThuc` chọn MAPID · `KiemTraSanSang` chưa chuyển được sang Sẵn sàng · `NgungCauHoi` ngừng sử dụng |
| UNI-2593 | `/questions/new`, `/questions/[id]`, `/questions/[id]/edit`, `/questions/[id]/usage`, `/questions/[id]/versions` | `CauHoiChonDang` chọn dạng · `CauHoi*Form` soạn theo dạng · `CauHoi*ChiTiet` chi tiết · `CauHoiPhienBan` lịch sử phiên bản |
| UNI-2594 | `/questions/import` | bước 1 theo kiểu `docs/design/dot-1/CapBuoc1TaiTep` · `NhapCauHoiAnhXa` · `NhapCauHoiKiemTra` · `NhapCauHoiKetQua` |
| UNI-2595 | `/exams`, `/exams/new`, `/exams/[id]`, `/exams/[id]/structure` | `BaiThi` · `TaoBaiThi` (trang riêng) · Thông tin chung (chưa vẽ, theo trường của `TaoBaiThi`) · `BaiThiCauTruc` · `BaiThiToHop` |
| UNI-2596 | `/exams/[id]/review`, `/exams/[id]/versions` | `BaiThiKiemTra` · `BaiThiXemTruoc` · `BaiThiPhienBan` · `CongBoBaiThi` |

## Chỗ bản vẽ khác nhau hoặc chờ BA

- Bản vẽ có 6 dạng câu, UniNote ghi 4 (Trắc nghiệm, Cloze, Short Answer, Question Set). Mockup theo 6 dạng.
- Màn chi tiết các dạng câu (vẽ ở đợt 1) ghi trạng thái "Hoạt động"; mockup dùng Nháp / Sẵn sàng / Ngừng sử dụng theo UniNote mục 9.
- Trình soạn thảo đầy đủ (trang 07 của canvas) chưa làm trong đợt mockup; ô đề bài là ô nhập nhiều dòng.
- Mã môn, khối, độ khó, cây kiến thức, mã tổ hợp trong dữ liệu mẫu là mẫu; khi nối backend lấy từ danh mục dùng chung.
