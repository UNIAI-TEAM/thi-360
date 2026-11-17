# Lưu bài và khôi phục lượt thi

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-16 16:29, lấy về ngày 2026-11-16. Nguồn: uniworkos, thư mục "Tài liệu Thi 360"; dùng cho PBI-11 (UNI-1640). Chỉ đọc; bản gốc ở uniworkos.

## 1. Mục tiêu và phạm vi

Trong khi học viên làm bài, hệ thống tự động lưu câu trả lời và hiển thị rõ thay đổi nào đã được máy chủ ghi nhận. Khi học viên tải lại trang, mở lại ứng dụng, đăng nhập lại hoặc chuyển thiết bị, hệ thống cho phép tiếp tục đúng lượt thi, đúng bộ câu hỏi và đúng thời gian còn lại.

PBI-11 áp dụng cho web và ứng dụng học viên, bao gồm đề FULL, bài lẻ và các kỳ thi HSA, QDA, BCA, V-ACT, SP2E, SPT, tốt nghiệp THPT. Quy tắc lưu bài dùng chung cho mọi kỳ thi. Bước được khôi phục phụ thuộc vào cấu trúc và tiến độ thực tế của từng lượt.

## 2. Dữ liệu cần lưu trong một lượt thi

| Nhóm dữ liệu | Nội dung và quy tắc |
|---|---|
| Lượt thi | Mã lượt, tài khoản sở hữu, bài thi, nguồn quyền đã dùng để bắt đầu và trạng thái hiện tại. |
| Đề của lượt thi | Bản lưu cố định của đề đã sinh, gồm phiên bản cấu trúc, nội dung câu, thứ tự câu và phương án, ngữ liệu, câu con và tài nguyên đi kèm. |
| Phần hoặc nhánh thi | Phần đã đóng, phần đang mở, bước đang chờ chọn, môn, mã tổ hợp hoặc định hướng học viên đã xác nhận. Với phần sinh sau khi chọn môn, lưu cả lựa chọn đã xác nhận và trạng thái sinh đề. |
| Câu trả lời | Mã câu trong bộ đề của lượt, mã câu con hoặc vị trí trả lời nếu có, nội dung trả lời, phiên được phép sửa, thứ tự cập nhật và thời điểm máy chủ ghi nhận. |
| Tiến độ | Câu đang xem, câu đã đánh dấu xem lại và trạng thái trả lời của từng câu. Tiến độ được xác định trên các câu học viên thực sự phải làm. |
| Thời gian | Thời điểm bắt đầu, thời điểm hết giờ của phần hoặc toàn bài, thời điểm đóng phần và nộp bài. Máy chủ quản lý các mốc này. |

Khi khôi phục, hệ thống đọc bộ đề đã lưu cho lượt thi. Nếu quản trị viên đã sửa câu hỏi hoặc xuất bản phiên bản mới, học viên vẫn tiếp tục với nội dung và thứ tự phương án đã nhận lúc làm bài.

## 3. Tự động lưu câu trả lời

### 3.1. Thời điểm và nội dung lưu

Học viên không cần nhấn Lưu sau mỗi câu. Khi học viên nhập, thay đổi hoặc xóa câu trả lời, hệ thống tự gửi thay đổi đến máy chủ. Với bài viết dài, có thể gộp các thao tác gõ liên tiếp thành một lần gửi. Khi học viên chuyển câu, chuyển phần hoặc chủ động nộp bài, hệ thống phải xử lý cả phần nội dung vừa nhập.

| Dạng câu hỏi | Quy tắc lưu |
|---|---|
| Trắc nghiệm một đáp án | Lưu mã phương án đang chọn. Khi đổi từ A sang C, đáp án hiện tại của câu là C. |
| Đúng/Sai | Lưu riêng từng mệnh đề với ba giá trị Đúng, Sai, Chưa trả lời. Khi mới làm 2 trong 4 ý, hai ý còn lại giữ trạng thái chưa trả lời. |
| Ghép đôi | Lưu từng cặp theo định danh của hai mục. Học viên đổi một cặp thì các cặp còn lại được giữ nguyên. |
| Trả lời ngắn và Cloze | Lưu nội dung theo đúng câu hoặc vị trí khuyết. Nếu học viên xóa nội dung đã nhập, thao tác xóa cũng phải được ghi nhận. |
| Question Set | Lưu riêng đáp án từng câu con, gắn với đúng chùm và ngữ liệu của lượt thi. |
| Tự luận | Tự lưu nội dung đang viết, kể cả khi học viên chưa chuyển câu. Khi mở lại, hiển thị đúng bản gần nhất máy chủ đã xác nhận. |

### 3.2. Trạng thái học viên nhìn thấy

| Trạng thái | Khi nào hiển thị | Ý nghĩa với học viên |
|---|---|---|
| Đang lưu | Có thay đổi đang được gửi hoặc đang chờ phản hồi | Học viên có thể tiếp tục làm bài. Thay đổi mới nhất chưa được xác nhận. |
| Đã lưu | Máy chủ đã xác nhận đúng phiên bản nội dung mới nhất đang hiển thị | Nội dung này có thể khôi phục khi mở lại lượt thi. |
| Chưa lưu | Thiết bị mất kết nối, gửi thất bại hoặc có thay đổi chưa gửi được | Hiển thị nguyên nhân và hướng dẫn. Ví dụ: “Mất kết nối. Câu trả lời mới nhất chưa được lưu. Thời gian làm bài vẫn tiếp tục.” |

Đã lưu và Đã trả lời có ý nghĩa khác nhau. Ví dụ, câu Đúng/Sai có 2/4 ý đã làm có thể hiện Đã lưu, đồng thời danh sách câu vẫn hiển thị Còn 2 ý chưa trả lời. Nếu học viên sửa tiếp trong lúc máy chủ đang phản hồi lần lưu trước, giao diện chỉ hiện Đã lưu sau khi bản sửa mới được xác nhận.

### 3.3. Kiểm tra tại máy chủ

Trước khi ghi nhận thay đổi, máy chủ kiểm tra tài khoản sở hữu lượt, phiên còn quyền sửa, câu thuộc đúng bộ đề và nhánh đã chọn, phần đang mở và thời hạn nhận câu trả lời.

Mỗi thay đổi có thứ tự cập nhật trong phạm vi câu hoặc vị trí trả lời. Ví dụ, học viên chọn A rồi đổi sang C ở câu 12. Nếu yêu cầu lưu C đến trước yêu cầu lưu A, kết quả cuối cùng vẫn là C. Quy tắc này áp dụng riêng cho câu 12. Một đáp án hợp lệ của câu 11 đến sau vẫn được lưu nếu phần thi còn mở.

Nếu máy chủ đã lưu thành công nhưng thiết bị mất phản hồi, việc gửi lại cùng thay đổi chỉ trả về kết quả đã ghi nhận. Hệ thống không nhân đôi đáp án, khôi phục lựa chọn đã xóa hoặc ghi đè một bản sửa mới hơn.

## 4. Khôi phục lượt thi

Khi học viên chọn Tiếp tục tại Bài thi của tôi, hệ thống xác minh tài khoản rồi đọc trạng thái mới nhất của lượt từ máy chủ. Nếu lượt còn mở, hệ thống khôi phục bộ câu, đáp án đã xác nhận, dấu xem lại và vị trí đang làm. Thời gian còn lại được tính từ hạn của phần hoặc toàn bài.

| Trạng thái khi học viên quay lại | Bước được khôi phục |
|---|---|
| Đang làm một phần và còn giờ | Mở lại câu đang xem trong phần đó, cùng ngữ liệu và đáp án đã lưu. |
| HSA đã đóng phần Văn, chưa xác nhận phần 3 | Mở bước chọn nhánh hoặc chọn 3 trong 5 môn nếu còn trong thời gian cho phép theo PBI-10. |
| HSA đã xác nhận 3 môn hoặc QDA đã xác nhận mã, nhưng chưa rõ phần 3 đã sinh thành công hay chưa | Kiểm tra trạng thái tại máy chủ. Nếu bộ câu đã tồn tại, mở đúng bộ đó. Nếu chưa sinh thành công, cho thử lại từ lựa chọn đã xác nhận trong cùng lượt. |
| BCA đã chọn mã hoặc Tin học SPT/THPT đã chọn định hướng | Giữ đúng mã hoặc định hướng. Khôi phục các câu chung và câu riêng thuộc phạm vi học viên phải làm. |
| Phần cũ đã hết giờ trong lúc học viên rời trang | Đưa học viên đến phần hoặc bước hiện được phép theo PBI-10. Phần đã đóng chỉ dùng dữ liệu được ghi nhận hợp lệ. |
| Đã nộp hoặc đã hết giờ toàn bài | Hiển thị trạng thái đã nộp, chờ chấm hoặc kết quả theo tiến độ xử lý. |
| Quyền mua hết hạn sau khi lượt đã bắt đầu hợp lệ | Cho tiếp tục lượt hiện tại nếu còn thời gian làm bài. |

Ví dụ, bài V-ACT hết giờ lúc 10:30. Học viên đóng ứng dụng lúc 10:00 và mở lại lúc 10:12 thì còn 18 phút. Nếu mở lại lúc 10:35, hệ thống hiển thị trạng thái sau khi hết giờ. Cả hai tình huống đều dùng cùng mã lượt và bộ đề.

## 5. Tiếp tục làm bài trên thiết bị hoặc tab khác

Mỗi lượt thi chỉ có một phiên được phép sửa bài tại một thời điểm. Quy tắc áp dụng cả khi học viên mở hai tab trên cùng trình duyệt hoặc chuyển từ web sang điện thoại.

- Khi thiết bị mới mở lượt đang có phiên sửa khác, hệ thống hiển thị “Bài thi đang mở ở một phiên khác. Bạn có muốn tiếp tục làm bài trên thiết bị này?” và hai lựa chọn Tiếp tục tại đây hoặc Hủy.

- Nếu chọn Hủy, phiên cũ tiếp tục làm bài. Nếu xác nhận tiếp quản, máy chủ chuyển quyền sửa sang phiên mới và thu hồi quyền sửa của phiên cũ trong cùng thao tác.

- Thiết bị mới tải lại đáp án gần nhất đã được máy chủ xác nhận, lựa chọn môn hoặc nhánh và thời gian còn lại. Các thay đổi còn nằm riêng trên thiết bị cũ chưa thể xuất hiện trên thiết bị mới.

- Nếu phiên cũ tiếp tục gửi đáp án, chuyển phần hoặc nộp bài, máy chủ từ chối và thông báo “Lượt thi đã được tiếp tục ở thiết bị khác.”

Ví dụ, học viên chọn A trên máy tính khi mất mạng, sau đó tiếp quản lượt trên điện thoại và chọn C. Khi máy tính kết nối lại, yêu cầu lưu A từ phiên cũ không được phép thay đáp án C.

## 6. Mất kết nối, đóng phần và nộp bài

| Tình huống | Hành vi yêu cầu |
|---|---|
| Mất mạng khi đang làm | Hiển thị Chưa lưu với các thay đổi chưa được xác nhận. Đồng hồ tiếp tục chạy. Khi kết nối lại, hệ thống kiểm tra phiên, trạng thái phần và thời hạn trước khi gửi lại. |
| Đóng ứng dụng khi còn dữ liệu chưa lưu | Nếu thiết bị cho phép, cảnh báo trước khi rời màn. Nếu có lưu nháp tạm, bản nháp phải gắn với đúng tài khoản, lượt và phiên sửa. Khi người khác đăng nhập trên thiết bị, họ không được xem bản nháp đó. |
| Học viên chủ động hoàn thành phần hoặc nộp khi còn thay đổi đang gửi | Gửi các thay đổi cuối và chờ máy chủ xác nhận thao tác đóng. Nếu lỗi kết nối, hiển thị “Chưa xác nhận nộp bài”. Khi kết nối lại, tra cứu trạng thái trước khi cho gửi lại. |
| Máy chủ đã nhận bài nộp nhưng thiết bị chưa nhận phản hồi | Khi học viên mở lại hoặc nhấn nộp lần nữa, trả về trạng thái và thời điểm nộp đã ghi nhận. Một lượt chỉ được đóng một lần. |
| Hết giờ trong khi thiết bị mất mạng | Máy chủ khóa phần hoặc toàn bài theo giờ hệ thống và dùng các đáp án đã nhận hợp lệ trước hạn. Thời điểm nhập do thiết bị tự ghi không đủ căn cứ để nhận đáp án gửi muộn. |
| Lỗi khởi tạo trước khi lượt bắt đầu | Nếu hệ thống đã tạm giữ một lượt nhưng sinh đề thất bại, phải giải phóng phần giữ chỗ. Học viên có thể thử lại bằng quyền ban đầu. |
| Lỗi sinh phần sau của đề FULL | Giữ các phần đã làm, lựa chọn đã xác nhận và quyền của lượt hiện tại. Thử lại tại phần lỗi theo PBI-10. |

Đối với thao tác nộp chủ động, đề xuất gửi nội dung sửa cuối cùng cùng yêu cầu nộp để máy chủ ghi nhận đáp án rồi đóng lượt trong cùng một lần xử lý. Trường hợp cần bảo vệ là học viên vừa gõ xong đoạn tự luận cuối và nhấn Nộp bài ngay: màn hình chỉ được báo Đã nộp khi phần nội dung đó đã được xử lý và lượt đã đóng thành công.

## 7. Theo dõi và xử lý sự cố

Hệ thống ghi nhật ký các sự kiện cần đối chiếu gồm lỗi lưu, mất quyền sửa, tiếp quản phiên, lỗi sinh đề, chuyển phần, nộp bài và tự đóng khi hết giờ. Nhật ký phải cho phép tra cứu theo mã lượt, phần thi, thời điểm máy chủ và kết quả xử lý.

Khi có phản ánh mất bài, vận hành cần xác định được bản trả lời cuối đã được máy chủ xác nhận, phần thi đã đóng lúc nào và lượt có chuyển sang thiết bị khác hay không. Nếu cần phục hồi quyền hoặc cấp bù do lỗi hệ thống, thao tác được xử lý theo quyền vận hành và ghi rõ lý do. Đáp án và thời gian của lượt đã đóng tiếp tục được lưu để đối chiếu.

## 8. Tiêu chí nghiệm thu

| Mã | Tình huống kiểm tra | Kết quả cần đạt |
|---|---|---|
| AC-11.01 | Học viên làm một số câu, thấy Đã lưu, đóng trang rồi mở lại | Khôi phục đúng bộ đề, thứ tự phương án, câu trả lời đã xác nhận và thời gian còn lại. |
| AC-11.02 | Học viên đổi đáp án từ A sang C, yêu cầu lưu A đến máy chủ sau C | Đáp án cuối là C. Phản hồi cho bản A không làm giao diện báo Đã lưu cho bản C khi C chưa được xác nhận. |
| AC-11.03 | Học viên xóa đáp án ngắn hoặc bỏ chọn một phương án rồi tải lại | Khôi phục đúng nội dung sau khi xóa hoặc bỏ chọn. |
| AC-11.04 | Học viên trả lời 2/4 mệnh đề Đúng/Sai hoặc mới điền một số ô Cloze | Khôi phục đúng từng ý, từng ô. Phần chưa trả lời vẫn để trống. |
| AC-11.05 | Máy chủ đã lưu nhưng thiết bị mất phản hồi và gửi lại | Chỉ có một thay đổi được ghi nhận về mặt nghiệp vụ. Đáp án mới hơn không bị ghi đè. |
| AC-11.06 | Học viên đang thi HSA mất kết nối sau khi xác nhận ba môn | Khi quay lại, giữ đúng lựa chọn và bộ câu đã sinh. Nếu lần sinh trước thất bại, thử lại trong cùng lượt. |
| AC-11.07 | Học viên chuyển từ web sang điện thoại trong lúc máy tính còn bản lưu chờ gửi | Chỉ điện thoại được sửa sau khi xác nhận tiếp quản. Yêu cầu lưu hoặc nộp từ phiên cũ bị từ chối. |
| AC-11.08 | Học viên vừa nhập đoạn tự luận cuối rồi nhấn nộp, hoặc mất phản hồi sau khi nộp | Hệ thống xác định được bản cuối đã nhận, hiển thị đúng trạng thái nộp và không đóng lượt lần hai. |
| AC-11.09 | Thiết bị gửi đáp án sau khi phần đã đóng hoặc hết giờ | Máy chủ từ chối thay đổi muộn. Khi mở lại, học viên thấy đúng trạng thái phần thi hoặc bài đã nộp. |
| AC-11.10 | Sinh đề lỗi trước khi mở lượt hoặc lỗi ở phần 3 sau hai phần đã hoàn thành | Lỗi trước khi bắt đầu không giữ chỗ vĩnh viễn. Lỗi ở phần 3 không làm mất hai phần trước hoặc tiêu hao thêm lượt. |
