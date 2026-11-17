# Quản lý bài thi đã mua

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-16 15:26, lấy về ngày 2026-11-16. Nguồn: uniworkos, thư mục "Tài liệu Thi 360", đính kèm task UNI-1638 (PBI-09). Chỉ đọc; bản gốc ở uniworkos.

## 1. Mục tiêu và phạm vi

Sau khi đăng nhập, học viên xem các bài thi của mình ngay trên trang chủ. Từ mỗi thẻ bài thi, học viên biết bài được cấp theo lớp hay do mình mua, còn bao nhiêu lượt, được bắt đầu thi đến khi nào và có thể thực hiện thao tác gì.

PBI-09 bao gồm hiển thị danh sách, phân biệt nguồn quyền, xác định hành động trên từng thẻ và điều hướng đến bước chuẩn bị thi, lượt đang làm, kết quả hoặc mua lại. Bài đã được cấp quyền cho tài khoản mua phải xuất hiện trên web và ứng dụng sau khi đồng bộ.

## 2. Vị trí hiển thị và phân nhóm bài thi

| Khu vực trên trang chủ | Điều kiện hiển thị | Nội dung |
|---|---|---|
| Bài đã mua | Tài khoản có bài đã mua, có lịch sử làm bài từ quyền mua hoặc có đơn đã thanh toán đang được kiểm tra cấp quyền | Hiển thị bài gắn với tài khoản đang đăng nhập. Bài đã cấp quyền hiển thị nhãn Đã mua. Bài đang kiểm tra cấp quyền hiển thị thông báo riêng. |
| Khám phá bài thi | Luôn có tại khu vực danh sách | Dẫn đến danh mục bài thi đang bán. Khi danh sách rỗng, nút nằm cùng thông báo rỗng. Khi đã có bài, nút vẫn hiển thị cạnh tiêu đề khu vực. |

Nếu cùng một sản phẩm vừa được cấp theo lớp vừa do học viên tự mua, hệ thống hiển thị bài trong cả hai nhóm, với số lượt và thời hạn tương ứng của từng nguồn. Ví dụ, học viên chọn Bắt đầu trên thẻ của lớp 12A thì lượt thi sử dụng quyền của lớp 12A. Số lượt cá nhân đã mua được giữ nguyên.

Nếu học viên chưa có bài từ bất kỳ nguồn nào, hiển thị “Bạn chưa có bài thi nào” và nút Khám phá bài thi. Học viên tự đăng ký không cần tham gia lớp để xem và sử dụng bài đã mua.

## 3. Thông tin trên thẻ bài thi đã mua

| Thông tin | Quy tắc hiển thị |
|---|---|
| Tên bài thi | Hiển thị tên sản phẩm học viên đã mua. |
| Kỳ thi và hình thức | Hiển thị kỳ thi, ví dụ HSA, QDA |
| Nguồn quyền | Hiển thị Đã mua để phân biệt với bài theo lớp. |
| Số câu và thời lượng | Hiển thị số câu học viên thực hiện và thời gian của một lượt làm bài. Với đề có nhánh tự chọn, chỉ tính nhánh học viên phải làm. |
| Lượt còn lại | Hiển thị số lượt còn có thể dùng để bắt đầu lượt mới theo quyền đã mua. Nếu đang có lượt làm dở, thẻ vẫn cho Tiếp tục kể cả khi số lượt có thể bắt đầu mới bằng 0. |
| Tình trạng sử dụng | Hiển thị thông tin tình trạng bài làm với học viên: Có thể bắt đầu, Đang làm, Hết lượt |
| Hành động | Hiển thị hành động chính theo mục 4. Khi có lịch sử hoặc kết quả còn được phép xem, hiển thị thêm hành động tương ứng. |

Số lượt và thời hạn trên thẻ lấy từ quyền đã cấp cho tài khoản. Ví dụ, học viên đã mua 3 lượt trong 30 ngày thì thẻ tiếp tục phản ánh quyền của lần mua đó, kể cả khi quản trị viên đổi sản phẩm đang bán thành 5 lượt trong 60 ngày.

## 4. Quy tắc hiển thị và xử lý các nút

| Tình huống | Nút hoặc thông báo | Xử lý khi học viên thao tác |
|---|---|---|
| Có lượt đang làm và còn thời gian thi | Tiếp tục | Mở lại đúng lượt, bộ câu hỏi, câu trả lời đã lưu và thời gian còn lại theo máy chủ. Đây là hành động ưu tiên trên thẻ. |
| Quyền mua hết hạn trong khi lượt đã bắt đầu hợp lệ vẫn còn thời gian | Tiếp tục | Cho hoàn thành lượt hiện tại. Sau khi lượt kết thúc, học viên cần quyền hợp lệ để bắt đầu lượt tiếp theo. |
| Không có lượt đang làm, còn lượt và còn trong thời hạn được bắt đầu | Bắt đầu | Mở bước chuẩn bị thi và hiển thị nguồn quyền sắp sử dụng. Việc mở bước chuẩn bị chưa làm chạy đồng hồ hoặc tiêu hao lượt. |
| Đã có lượt hoàn thành, kết quả đã sẵn sàng và còn hạn xem | Xem kết quả hoặc Lịch sử | Xem kết quả mở kết quả của lượt tương ứng. Lịch sử mở danh sách các lượt để học viên chọn lần muốn xem. Các nút này có thể xuất hiện cùng Bắt đầu, Tiếp tục hoặc Mua lại. |
| Đã hết lượt hoặc hết hạn, không có lượt được tiếp tục và sản phẩm còn bán | Mua lại trên web | Mở bước xác nhận mua với giá và quyền lợi đang công bố. Lịch sử của lần mua trước vẫn gắn với tài khoản. |
| Đã hết quyền và sản phẩm ngừng bán | Hết lượt hoặc Hết hạn | Giữ đường dẫn tới lịch sử còn được phép xem. Ẩn Mua lại. |
| Đã xác nhận thu tiền nhưng quyền chưa cấp xong hoặc đang phục hồi | Đang kiểm tra | Hiển thị hướng dẫn kiểm tra lại hoặc liên hệ hỗ trợ theo mã đơn. Hệ thống không đề nghị thanh toán lại cho đơn này. |

Hệ thống kiểm tra quyền và trạng thái lượt tại máy chủ khi học viên thực hiện thao tác. Ví dụ, nếu trang chủ chưa cập nhật và vẫn hiện Bắt đầu trong khi học viên vừa mở lượt trên điện thoại, hệ thống chuyển đến lượt đang làm thay vì tạo lượt thứ hai.

## 5. Đồng bộ và xử lý ngoại lệ

| Tình huống | Yêu cầu xử lý |
|---|---|
| Quyền vừa được cấp sau thanh toán | Bài xuất hiện trên trang chủ web và trong Bài đã mua trên ứng dụng sau khi đồng bộ đúng tài khoản. Học viên không cần yêu cầu quản trị viên kích hoạt lại. |
| Quyền được phục hồi sau lỗi hệ thống | Cập nhật từ Đang kiểm tra sang trạng thái sử dụng thực tế. Việc tải lại danh sách hoặc xử lý lại thông báo thanh toán không làm nhân đôi số lượt. |
| Lỗi tải danh sách hoặc mất kết nối | Hiển thị thông báo và Thử lại. Nếu đang hiển thị dữ liệu đã tải trước đó, ghi rõ dữ liệu chưa được cập nhật. Không hiển thị lỗi tải thành trạng thái “chưa có bài thi”. |
| Sản phẩm ngừng bán sau khi đã mua | Danh mục mua mới ngừng bán sản phẩm. Trên trang chủ, hệ thống xác định khả năng sử dụng theo quyền đã cấp và chính sách xử lý quyền cũ. |
| Học viên sử dụng ứng dụng di động | Hiển thị cùng quyền, lượt đang làm và lịch sử như trên web. Khám phá bài thi mở danh mục. Hành động Mua lại chỉ hiển thị khi đã có kênh mua được hỗ trợ trên ứng dụng. |
