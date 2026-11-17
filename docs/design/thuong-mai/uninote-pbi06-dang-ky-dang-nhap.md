# Đăng ký, đăng nhập và thông tin cá nhân

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-16 14:53, lấy về ngày 2026-11-16. Nguồn: uniworkos, thư mục "Tài liệu Thi 360", đính kèm task UNI-1635 (PBI-06). Chỉ đọc; bản gốc ở uniworkos.

## 1. Mục tiêu và phạm vi

Khách có thể tự đăng ký tài khoản học viên bằng tên đăng nhập tự chọn và mật khẩu. Sau khi đăng nhập, học viên có thể mua bài thi cho tài khoản hiện tại, sử dụng quyền đã mua, tiếp tục lượt thi đang làm và xem kết quả học tập của mình.

PBI-06 áp dụng cho cổng web học viên, gồm đăng ký, đăng nhập bằng tên đăng nhập và mật khẩu, quên mật khẩu, cập nhật thông tin cá nhân, đăng xuất và xác thực lại khi hết phiên.

Mỗi học viên có một mã tài khoản duy nhất. Đơn hàng, quyền sử dụng bài thi, lượt làm bài và kết quả được gắn với mã này. Khi học viên cập nhật họ tên, email hoặc mật khẩu, hệ thống vẫn nhận diện đúng tài khoản đã mua bài.

## 2. Điểm truy cập và điều hướng

| Điểm bắt đầu | Hành động | Đích sau khi đăng nhập thành công |
|---|---|---|
| Trang giới thiệu hoặc danh mục bài thi | Chọn Đăng nhập hoặc Đăng ký | Trang chủ học viên. |
| Thẻ hoặc trang chi tiết bài thi | Chọn Mua bài thi khi chưa đăng nhập | Quay lại đúng sản phẩm. Hệ thống kiểm tra trạng thái bán, giá hiện tại và quyền của tài khoản để hiển thị Xác nhận mua hoặc Vào bài thi. |
| Bài thi của tôi, kết quả hoặc hồ sơ cá nhân | Mở đường dẫn khi chưa đăng nhập hoặc đã hết phiên | Quay lại nội dung đã yêu cầu nếu tài khoản có quyền truy cập. |
| Lượt thi đang thực hiện | Xác thực lại khi hết phiên | Tiếp tục đúng lượt thi nếu còn thời gian. Nếu đã hết giờ, xử lý theo quy tắc kết thúc lượt thi. |

Khi người dùng chuyển từ Đăng nhập sang Đăng ký hoặc Quên mật khẩu, hệ thống ghi nhớ sản phẩm hoặc nội dung đang truy cập. Đăng nhập thành công từ luồng mua đưa học viên đến bước xác nhận. Học viên vẫn cần kiểm tra thông tin và chủ động xác nhận mua.

## 3. Đăng ký tài khoản

Khách chọn Đăng ký tài khoản và khai báo các thông tin sau.

| Trường thông tin | Yêu cầu | Quy tắc |
|---|---|---|
| Tên đăng nhập | Bắt buộc | Học viên tự đặt. Tên phải duy nhất sau khi chuẩn hóa chữ hoa và chữ thường. Hiển thị hướng dẫn về độ dài và ký tự hợp lệ tại trường nhập. |
| Họ và tên | Bắt buộc | Dùng làm tên hiển thị trong hồ sơ và thông tin học tập. |
| Email | Bắt buộc | Dùng để liên hệ và khôi phục tài khoản. Email phải được xác minh trước khi sử dụng để đặt lại mật khẩu. |
| Mật khẩu | Bắt buộc | Học viên tự đặt theo yêu cầu mật khẩu hiển thị trên biểu mẫu. |
| Chấp nhận điều khoản sử dụng | Bắt buộc | Người dùng có thể mở nội dung điều khoản trước khi xác nhận. |

Khi người dùng nhấn Đăng ký, máy chủ kiểm tra các trường bắt buộc, định dạng dữ liệu và tính duy nhất của tên đăng nhập. Ví dụ, nếu đã có HocVien01, hệ thống thông báo tên hocvien01 đã được sử dụng và yêu cầu chọn tên khác. Khi hai người đăng ký cùng một tên tại cùng thời điểm, chỉ một yêu cầu được tạo tài khoản thành công.

Sau khi tạo tài khoản, hệ thống hướng dẫn học viên xác minh email và hiển thị trạng thái xác minh trong hồ sơ. Học viên tự đăng ký được sử dụng theo luồng khách lẻ, không bắt buộc chọn lớp hoặc đơn vị.

## 4. Đăng nhập

- Học viên nhập tên đăng nhập, mật khẩu và chọn Đăng nhập.

- Máy chủ chuẩn hóa tên đăng nhập theo cùng quy tắc đã áp dụng khi đăng ký, sau đó kiểm tra mật khẩu và trạng thái tài khoản.

- Nếu xác thực thành công, hệ thống tạo phiên, ghi nhận mã tài khoản và điều hướng theo mục 2.

- Nếu thông tin chưa đúng, hệ thống hiển thị “Tên đăng nhập hoặc mật khẩu chưa đúng.” Học viên có thể nhập lại hoặc chọn Quên mật khẩu.

- Khi số lần đăng nhập sai đạt ngưỡng cấu hình, hệ thống tạm hạn chế thử lại và hướng dẫn người dùng cách tiếp tục. Tài khoản đang bị khóa không được tạo phiên mới.

Sau khi đăng nhập, học viên thấy các bài đã mua cùng số lượt còn lại, thời hạn và lịch sử tương ứng. Việc đăng nhập lại không làm thay đổi các giá trị này.

## 5. Quên và đặt lại mật khẩu

- Học viên chọn Quên mật khẩu, nhập email đã đăng ký và gửi yêu cầu.

- Hệ thống hiển thị cùng một thông báo cho mọi địa chỉ email: “Nếu email này đủ điều kiện khôi phục tài khoản, bạn sẽ nhận được hướng dẫn đặt lại mật khẩu.”

- Nếu email đã được xác minh và đủ điều kiện khôi phục, hệ thống gửi liên kết hoặc mã đặt lại mật khẩu. Liên kết hoặc mã có thời hạn và chỉ dùng được một lần.

- Học viên mở liên kết hoặc nhập mã, khai báo mật khẩu mới và xác nhận. Máy chủ kiểm tra hiệu lực của yêu cầu trước khi cập nhật mật khẩu.

- Sau khi cập nhật thành công, học viên đăng nhập bằng mật khẩu mới. Nếu liên kết hoặc mã đã hết hạn hoặc đã sử dụng, hệ thống hướng dẫn gửi yêu cầu mới.

Hệ thống giới hạn tần suất gửi yêu cầu và số lần thử mã theo cấu hình vận hành. Đặt lại mật khẩu không mở khóa tài khoản đang bị quản trị viên khóa.

## 6. Thông tin cá nhân

Học viên chọn Tài khoản → Thông tin cá nhân để xem và cập nhật hồ sơ.

| Thông tin | Quy tắc hiển thị và cập nhật |
|---|---|
| Tên đăng nhập | Hiển thị ở chế độ chỉ đọc để học viên nhận biết tài khoản đang sử dụng. |
| Avatar | Upload file <= 5MB |
| Họ và tên | Cho phép cập nhật. Sau khi lưu thành công, hệ thống hiển thị tên mới trong hồ sơ. |
| Email | Hiển thị địa chỉ hiện tại và trạng thái Đã xác minh hoặc Chưa xác minh. Cho phép yêu cầu xác minh hoặc thay đổi địa chỉ. |
| Số điện thoại | Cho học viên điền SĐT |

Khi học viên nhập email mới, hệ thống gửi yêu cầu xác minh đến địa chỉ mới. Nếu địa chỉ hiện tại đã được xác minh, địa chỉ này tiếp tục được dùng để khôi phục tài khoản trong thời gian chờ. Hệ thống chỉ thay thế email sau khi học viên xác minh thành công địa chỉ mới. Nếu yêu cầu hết hạn, hồ sơ giữ nguyên email hiện tại.

## 7. Đăng xuất và xác thực lại khi đang thi

Khi học viên chọn Đăng xuất, hệ thống kết thúc phiên hiện tại và đưa người dùng về trang giới thiệu. Các trang cá nhân yêu cầu đăng nhập khi được mở lại.

Nếu phiên đăng nhập hết hạn trong lúc thi, hệ thống xử lý theo tài khoản vừa xác thực và trạng thái lượt thi.

| Tình huống | Hành vi của hệ thống |
|---|---|
| Đăng nhập lại đúng tài khoản, lượt thi còn thời gian | Mở lại đúng bộ câu hỏi, thứ tự câu và các câu trả lời đã lưu. Thời gian còn lại được tính theo thời điểm kết thúc đã ghi nhận trên máy chủ. |
| Đăng nhập lại đúng tài khoản, lượt thi đã hết giờ | Áp dụng quy tắc kết thúc lượt thi và hiển thị trạng thái tương ứng. |
| Đăng nhập bằng tài khoản khác | Từ chối truy cập lượt thi trước đó. Chỉ hiển thị dữ liệu thuộc quyền của tài khoản vừa đăng nhập. |
| Mở lại lượt đã nộp | Hiển thị trạng thái đã nộp hoặc kết quả theo quyền xem của học viên. |

Khi xác thực lại, hệ thống sử dụng bản lưu của lượt thi ban đầu, không sinh đề mới, không trừ thêm lượt và không đặt lại đồng hồ.

## 8. Tiêu chí nghiệm thu

| Mã | Tình huống kiểm tra | Kết quả cần đạt |
|---|---|---|
| AC-06.01 | Hai người đồng thời đăng ký HocVien01 và hocvien01 | Máy chủ chỉ tạo một tài khoản. Người còn lại nhận thông báo tên đăng nhập đã được sử dụng. |
| AC-06.02 | Khách chọn mua bài rồi chuyển sang đăng ký hoặc đăng nhập | Sau xác thực, hệ thống quay lại đúng sản phẩm, kiểm tra giá và quyền hiện có trước khi hiển thị hành động tiếp theo. |
| AC-06.03 | Học viên nhập sai mật khẩu nhiều lần hoặc đăng nhập bằng tài khoản bị khóa | Hệ thống áp dụng giới hạn thử lại theo cấu hình. Tài khoản bị khóa không tạo được phiên mới. |
| AC-06.04 | Gửi yêu cầu quên mật khẩu bằng email đã đăng ký và email chưa đăng ký | Hai trường hợp nhận cùng nội dung phản hồi trên web. Mã hoặc liên kết đã dùng hay hết hạn không thể dùng để đổi mật khẩu. |
| AC-06.05 | Học viên thay đổi email nhưng chưa xác minh địa chỉ mới | Email đã xác minh trước đó vẫn dùng được để khôi phục tài khoản. Sau khi xác minh thành công, hồ sơ chuyển sang email mới. |
| AC-06.06 | Học viên đổi họ tên, email hoặc mật khẩu rồi đăng nhập lại | Đơn hàng, quyền đã mua, số lượt còn lại và kết quả vẫn thuộc đúng tài khoản. |
| AC-06.07 | Phiên hết hạn giữa lượt thi | Đăng nhập lại đúng tài khoản khôi phục đúng lượt và thời gian còn lại. Tài khoản khác không thể xem hoặc tiếp tục lượt đó, kể cả khi có đường dẫn trực tiếp. |

Điểm cần chốt theo D11: Cổng thi dùng tài khoản độc lập hay dùng chung tài khoản với LMS. Quyết định này xác định phạm vi kiểm tra trùng tên đăng nhập và việc học viên đã có tài khoản LMS có thể dùng ngay tài khoản đó trên cổng thi hay cần đăng ký riêng.
