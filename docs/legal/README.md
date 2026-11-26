# Văn bản của Thi360

Hai tệp trong thư mục này là nguồn nội dung của trang công khai trên web học viên và màn văn bản trong hai app. Task WEB-1 của đợt UNI-2820 dựng các tệp có cấu trúc ở `contracts/mobile/legal/` từ hai tệp này.

- `thi360-dieu-kien-giao-dich-chung.md`: Điều kiện giao dịch chung.
- `thi360-chinh-sach-bao-mat-thong-tin.md`: Chính sách bảo mật thông tin.

Người yêu cầu chốt phát hành hai văn bản như đang có vào ngày 2026-11-26 (quyết định D-32 của đặc tả `docs/specs/2026-11-26-app-hoc-vien-cong-thi.md`): trạng thái `published`, phiên bản `2026-11-26`, có hiệu lực từ 2026-11-26. Sửa nội dung thì tăng phiên bản ở dòng "Phiên bản" đầu tệp và đổi ngày có hiệu lực; học viên đăng ký sau đó được ghi là đã đồng ý phiên bản mới.

## Nguồn của từng quy tắc

Bố cục theo hai trang cùng tên của hsavnu.edu.vn; nội dung viết lại cho dịch vụ thi thử Thi360. Bảng dưới ghi quy tắc nào lấy từ trang gốc, quy tắc nào theo hành vi của hệ thống, quy tắc nào do người viết đề xuất, để đơn vị vận hành tra khi cần sửa.

### Điều kiện giao dịch chung

Các quy tắc dưới đây là cam kết kinh doanh, lấy từ trang gốc rồi chỉnh cho dịch vụ thi thử, hoặc do người viết đề xuất.

| Mục | Nội dung | Nguồn |
| --- | --- | --- |
| 1.2 | Điểm bài thi thử chỉ có giá trị tham khảo | Người viết đề xuất |
| 1.5 | Từ đủ 15 tuổi, dưới 15 tuổi cần cha mẹ đồng ý | Trang gốc |
| 2 | Cấp bài trong 48 giờ làm việc khi đơn "Đang kiểm tra" | Trang gốc (kích hoạt trong 48 giờ) |
| 2 | Lượt thi đã bắt đầu tính là đã dùng | Hành vi hiện tại của hệ thống |
| 3 | Phản hồi hỗ trợ trong 24 giờ làm việc; cộng thời hạn khi gián đoạn từ 48 giờ | Trang gốc |
| 2, 3 | Giờ làm việc 08h00–17h30, thứ Hai đến thứ Sáu | Người yêu cầu chốt 2026-11-26 (trang gốc ghi hai khung giờ khác nhau) |
| 3 | Lỗi hệ thống thì cấp bù một lượt; sai nội dung thì chấm lại | Hành vi hiện tại của hệ thống |
| 5 | Không đổi bài; mua nhầm xét đổi trong 7 ngày khi chưa bắt đầu lượt nào | Trang gốc (đổi khóa trong 7 ngày, học dưới 20%), chỉnh lại |
| 6.a | Ba trường hợp hoàn tiền và mức hoàn | Hai dòng đầu từ trang gốc, dòng ba do người viết đề xuất |
| 6.b | Không hoàn tiền với đơn có khuyến mãi, voucher, bộ đề giảm giá | Trang gốc |
| 6.b | Tự xóa tài khoản thì lượt thi chưa dùng mất, không hoàn tiền | Người yêu cầu chốt 2026-11-26 |
| 6.a | Thanh toán ghi nhận sau khi tài khoản đã xóa: không cấp bài, hoàn 100% | Người yêu cầu chốt 2026-11-26 |
| 6.c | Yêu cầu trong 7 ngày (trang gốc ghi 24 giờ), phản hồi 3 ngày làm việc, hoàn trong 7 ngày làm việc | Trang gốc, riêng hạn gửi yêu cầu do người viết nới |
| 9 | Số hỗ trợ 0974187313 và email Gmail; trang gốc ghi bốn số điện thoại khác nhau ở các chỗ khác nhau | Trang gốc |
| Đầu tệp | Ngày có hiệu lực 26/11/2026 | Người viết chọn ngày chốt nội dung |

Web hiện ghi ở màn mua: "Bài thi được cấp ngay sau khi giao dịch hoàn tất và không hỗ trợ hoàn tiền sau khi đã kích hoạt lượt thi." Mục 6 khớp với câu này.

### Chính sách bảo mật thông tin

| Mục | Nội dung | Nguồn |
| --- | --- | --- |
| 1 | Danh sách dữ liệu thu thập | Rà theo mã của hệ thống và đặc tả app; trang gốc ghi "website không thu thập dữ liệu", không đúng với Thi360 |
| 2 | Gửi thông tin giới thiệu sản phẩm khi người dùng đồng ý | Trang gốc. Hệ thống hiện chưa có ô đồng ý nhận tin; chưa có ô này thì không được gửi |
| 3, 6 | Tự xóa trong ứng dụng và trên website thì xóa ngay, giữ bài làm ở dạng vô danh, giữ đơn hàng | Người yêu cầu chốt 2026-11-26 |
| 3 | Yêu cầu qua email, điện thoại: xóa trong 30 ngày kể từ khi xác minh | Người viết đề xuất; trang gốc chỉ ghi "xóa khi khách hàng yêu cầu qua email" |
| 3 | Thời hạn lưu nhật ký kỹ thuật | Chưa có con số |
| 4 | Danh sách đối tác nhận dữ liệu (VNPAY, hạ tầng, email, đơn vị phát triển) | Rà theo hệ thống; cần đơn vị vận hành nêu tên cụ thể nếu muốn |
| 6 | Phản hồi yêu cầu trong 3 ngày làm việc; khiếu nại trong 7 ngày làm việc (mục 8) | Người viết đề xuất |
| 5, 6, 8 | Số 0968964334 và email Gmail | Trang gốc |
| 7 | Phân quyền và ghi nhật ký truy cập của nhân sự | Hành vi hiện tại của hệ thống (phân quyền theo vai trò, nhật ký thao tác quản trị) |
| Đầu tệp | Ngày có hiệu lực 26/11/2026 | Người viết chọn ngày chốt nội dung |

Hai điểm ngoài văn bản mà đơn vị vận hành nên biết trước khi đưa app lên kho ứng dụng:

- Chức năng tự xóa tài khoản mà mục 3 và mục 6 mô tả nằm trong đặc tả của đợt UNI-2820 (R-182 đến R-184); văn bản này chỉ đúng khi chức năng đó đã được phát hành.
- Văn bản nên được người phụ trách pháp lý của đơn vị vận hành đọc lại trước khi công bố, nhất là phần căn cứ theo pháp luật về bảo vệ dữ liệu cá nhân và phần dành cho người dùng dưới 15 tuổi.
