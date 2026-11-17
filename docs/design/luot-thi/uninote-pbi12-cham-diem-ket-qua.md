# Chấm điểm tự động và hiển thị kết quả bài thi

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-16 16:46, lấy về ngày 2026-11-16. Nguồn: uniworkos, thư mục "Tài liệu Thi 360"; dùng cho PBI-12 (UNI-1641). Chỉ đọc; bản gốc ở uniworkos.

## 1. Mục tiêu và quy ước tên gọi

Sau khi học viên nộp bài hoặc hết thời gian làm bài, hệ thống chấm tự động theo đáp án, quy tắc tính điểm và band điểm đã khai báo trong ngân hàng. Học viên xem kết quả trực tiếp trên web hoặc ứng dụng.

Tài liệu và giao diện sử dụng thống nhất các tên gọi sau.

| Phạm vi thi | Tên hiển thị | Cách hiểu |
|---|---|---|
| Toàn bộ đề | Bài thi FULL | Học viên thực hiện đầy đủ các phần bắt buộc và phần tự chọn theo cấu trúc của đề. Toàn bộ bài được ghi nhận trong một lượt thi. |
| Từng phần | Bài thi theo phần | Học viên thực hiện một phần được cấu hình độc lập. Một phần có thể gồm nhiều môn, chẳng hạn phần Khoa học của HSA. |
| Từng môn | Bài thi theo môn | Học viên thực hiện bài thi của một môn được cấu hình độc lập, chẳng hạn môn Toán hoặc Vật lí. |

Khi hiển thị kết quả, hệ thống căn cứ vào phạm vi của bài thi mà học viên đã bắt đầu. Ví dụ, môn Toán trong một bài thi FULL được hiển thị là điểm thành phần của lượt FULL. Nếu học viên bắt đầu một bài thi theo môn Toán, điểm Toán là điểm tổng của lượt thi đó.

## 2. Kết thúc và ghi nhận bài làm

### 2.1. Học viên chủ động nộp bài

- Học viên chọn Nộp bài. Hệ thống hiển thị số câu đã trả lời và số câu còn bỏ trống trong phạm vi chuẩn bị nộp.

- Nếu còn câu trả lời đang lưu, hệ thống hiển thị trạng thái lưu và thử đồng bộ trước khi hoàn tất. Nếu đồng bộ thất bại, học viên được thông báo những thay đổi nào chưa được ghi nhận.

- Khi học viên xác nhận, máy chủ kiểm tra lượt thi, thời hạn và các câu trả lời đã tiếp nhận hợp lệ, sau đó khóa phạm vi đã nộp.

- Với bài thi FULL, nếu học viên mới hoàn thành một phần và đề còn phần tiếp theo, hệ thống khóa phần vừa hoàn thành và chuyển tiếp theo cấu trúc đề. Kết quả toàn bài được lập sau khi lượt FULL kết thúc.

- Với bài thi theo phần hoặc bài thi theo môn, hệ thống chấm và hiển thị kết quả ngay khi lượt thi độc lập đó kết thúc.

### 2.2. Hết thời gian hoặc gửi yêu cầu nộp nhiều lần

- Máy chủ sử dụng thời hạn của phần hoặc toàn bài theo cấu hình đề. Khi hết giờ, hệ thống tự khóa phạm vi tương ứng và sử dụng các câu trả lời đã tiếp nhận hợp lệ trong thời gian làm bài.

- Nếu học viên nhấn Nộp bài đúng lúc hệ thống tự nộp, lượt thi chỉ kết thúc một lần và có một kết quả.

- Nếu mất kết nối sau khi nhấn nộp, khi mở lại bài, học viên thấy trạng thái thực tế của lượt thi trên máy chủ. Nếu máy chủ đã ghi nhận việc nộp bài, hệ thống mở màn xử lý hoặc xem kết quả.

## 3. Dữ liệu và quy tắc chấm điểm

### 3.1. Dữ liệu dùng để chấm

Hệ thống chấm theo bản dữ liệu đã cố định cho lượt thi (snapshot), gồm phiên bản đề, phần hoặc môn thực tế đã làm, danh sách câu hỏi và câu con, thứ tự lựa chọn sau khi đảo, đáp án chuẩn, điểm tối đa và quy tắc áp dụng band điểm. Bài làm dùng để chấm là các câu trả lời cuối cùng đã được máy chủ ghi nhận hợp lệ.

Band điểm có thể được dùng ở hai phạm vi. Khi thiết lập, cần chỉ rõ phạm vi áp dụng để hệ thống chọn đúng bảng.

| Phạm vi áp dụng | Cách sử dụng |
|---|---|
| Câu hỏi | Quy đổi mức độ hoàn thành của một câu thành số điểm được hưởng. Ví dụ, câu Đúng/Sai có bốn ý được tính điểm theo số ý trả lời đúng và bảng điểm đã khai báo. |
| Kết quả bài, phần hoặc môn | Quy đổi điểm tổng của đúng phạm vi thành mức kết quả, nếu ngân hàng đã cấu hình bảng quy đổi tương ứng. |

### 3.2. Quy tắc theo dạng câu hỏi

| Dạng câu hỏi | Quy tắc chấm |
|---|---|
| Trắc nghiệm một đáp án | Đối chiếu phương án học viên chọn với mã phương án đúng. Nếu đề có đảo đáp án, hệ thống chấm theo mã phương án đã lưu trong lượt thi. |
| Trắc nghiệm nhiều đáp án | Đối chiếu tập phương án học viên chọn với tập đáp án chuẩn. Điểm toàn phần hoặc từng phần được xác định theo quy tắc đã khai báo cho câu. |
| Đúng/Sai nhiều ý | Chấm từng ý, sau đó áp dụng bảng điểm của câu. Một câu gồm bốn ý vẫn được tính là một câu trong thống kê tổng số câu. |
| Trả lời ngắn | Chấp nhận khi câu trả lời khớp hoàn toàn với một đáp án hợp lệ đã khai báo. Ví dụ, nếu đáp án được khai báo là 0,5, hệ thống chỉ chấp nhận 1/2 khi giá trị này cũng đã được khai báo. |
| Cloze | Chấm từng vị trí trống theo đáp án và điểm tương ứng, sau đó cộng điểm các vị trí thuộc bài Cloze. |
| Bộ câu hỏi (Question Set) | Chấm từng câu con theo dạng và đáp án riêng. Ngữ liệu chung không được tính thêm điểm. |
| Ghép đôi | Đối chiếu các cặp học viên đã ghép với đáp án chuẩn và áp dụng quy tắc tính điểm của câu. |

Câu bỏ trống nhận 0 điểm. Câu được chấm từng phần nhận đúng số điểm theo cấu hình. AI có thể giải thích kết quả sau khi chấm. Việc tạo lời giải bằng AI không quyết định điểm và không làm chậm việc hiển thị bảng điểm.

### 3.3. Điều kiện để một đề được chấm tự động

Trước khi mở đề cho học viên, hệ thống phải kiểm tra tất cả các phần, môn và nhánh được phép chọn đều có đủ đáp án và quy tắc chấm. Nếu dùng band điểm, bảng quy đổi phải bao phủ các giá trị điểm hợp lệ và xác định được duy nhất một kết quả ở mỗi giá trị, kể cả tại điểm biên.

Một bài tự luận có đáp án tham khảo và thang điểm vẫn cần người đánh giá nếu hệ thống chưa có quy tắc chấm tự động cho dạng đó. Trong phạm vi PBI này, nếu bài thi FULL có phần tự luận bắt buộc như vậy, hệ thống phải chỉ rõ phần chưa đáp ứng khi kiểm tra đề và chặn mở thi FULL. Các bài thi theo phần hoặc theo môn có cấu hình chấm tự động đầy đủ vẫn có thể được mở độc lập. Hệ thống không công bố điểm của những phần chấm được dưới tên điểm tổng của bài thi FULL.

## 4. Tổng hợp và hiển thị điểm theo phạm vi thi

| Bài thi học viên thực hiện | Điểm tổng trên màn kết quả | Chi tiết bên dưới |
|---|---|---|
| Bài thi FULL | Điểm toàn bài theo thang điểm và quy tắc tổng hợp của đề | Điểm từng phần bắt buộc và nhánh học viên đã chọn. Nếu một phần gồm nhiều môn, hiển thị điểm các môn thực tế đã làm theo cấu hình. |
| Bài thi theo phần | Điểm đạt được trên thang điểm của phần | Tên phần, các môn hoặc nhóm câu thuộc phần đã làm và điểm tương ứng nếu có cấu hình. |
| Bài thi theo môn | Điểm đạt được trên thang điểm của môn | Tên môn và điểm theo các phần trong môn nếu đề có chia phần. |

Ví dụ với HSA. Học viên làm bài thi FULL và chọn Vật lí, Hóa học, Sinh học ở phần Khoa học. Bảng điểm hiển thị kết quả toàn bài, điểm Toán, điểm Văn và điểm phần Khoa học. Khi xem phần Khoa học, học viên thấy ba môn đã chọn. Hai môn còn lại không được đưa vào mẫu số, số câu bỏ trống hoặc thống kê điểm yếu. Nếu học viên chỉ đăng ký bài thi theo phần Khoa học, màn kết quả hiển thị điểm của riêng phần Khoa học và ba môn đã làm.

Thang điểm, trọng số và cách quy đổi được lấy từ cấu hình của từng đề. Đối với đề có phương pháp tính điểm riêng, chẳng hạn IRT, hệ thống chỉ hiển thị điểm theo phương pháp đó khi đã có mô hình và dữ liệu tương ứng. Nếu sản phẩm sử dụng điểm luyện tập, màn kết quả phải ghi rõ Điểm luyện tập cùng thang điểm thực tế.

## 5. Nội dung màn xem kết quả

| Nhóm thông tin | Nội dung hiển thị |
|---|---|
| Thông tin lượt thi | Tên bài thi, kỳ thi, phạm vi thi, lần làm bài, thời điểm nộp và thời gian làm bài thực tế. |
| Phần, môn hoặc tổ hợp | Tên phần, môn, nhánh hoặc mã tổ hợp học viên thực tế đã làm. |
| Kết quả chung | Điểm đạt được, điểm tối đa và band điểm nếu có cấu hình cho đúng phạm vi. Ví dụ, Điểm phần Khoa học: 36/50. |
| Kết quả thành phần | Điểm theo từng phần hoặc từng môn theo quy tắc tại mục 4. |
| Thống kê trả lời | Số câu đúng, sai, bỏ trống. Với câu được hưởng một phần điểm, hiển thị thêm Đúng một phần để mỗi câu chỉ thuộc một nhóm thống kê. |
| Chi tiết bài làm | Nếu chính sách công bố cho phép, học viên xem lại câu hỏi, câu trả lời đã nộp, điểm được hưởng, đáp án và lời giải tương ứng. |

Sau khi chấm xong, hệ thống hiển thị kết quả trực tiếp trên web và ứng dụng. Học viên có thể mở lại cùng kết quả từ Lịch sử làm bài trong thời hạn được xem. Hệ thống không gửi email bảng điểm.

## 6. Trạng thái xử lý và bảo toàn kết quả

| Trạng thái | Hành vi hệ thống |
|---|---|
| Đang chấm | Hiển thị xác nhận đã nhận bài và thông báo đang tính điểm. Học viên có thể rời màn hình rồi quay lại. Điểm tổng chỉ xuất hiện khi tính xong. |
| Đã có kết quả | Hiển thị bảng điểm của lượt thi. Web và ứng dụng truy xuất cùng một kết quả. |
| Lỗi xử lý kết quả | Thông báo bài làm đã được ghi nhận, lưu lỗi để xử lý và cho phép hệ thống chấm lại từ dữ liệu của lượt thi. Học viên không phải làm lại bài hoặc sử dụng thêm lượt. |

Khi chấm lại do lỗi kỹ thuật, hệ thống sử dụng cùng bài làm, đáp án và band điểm của lượt thi. Việc quản trị viên sửa câu hỏi, đáp án hoặc band điểm trong ngân hàng sau đó không tự động thay đổi kết quả đã công bố.

## 7. Tiêu chí nghiệm thu

| Mã | Điều kiện nghiệm thu |
|---|---|
| AC-12.01 | Học viên nộp bài đúng thời điểm hết giờ. Hệ thống ghi nhận một lần kết thúc và một kết quả cho lượt thi. |
| AC-12.02 | Học viên hoàn thành phần đầu của bài thi FULL. Hệ thống khóa phần đó và chuyển sang phần tiếp theo theo cấu trúc đề. Điểm toàn bài chỉ được lập khi lượt FULL kết thúc. |
| AC-12.03 | Với bài thi theo phần hoặc bài thi theo môn, màn kết quả hiển thị đúng tên và thang điểm của phạm vi đã làm. |
| AC-12.04 | Với phần có lựa chọn môn hoặc tổ hợp, chỉ các môn đã chọn được tính vào điểm, mẫu số và thống kê đúng, sai, bỏ trống. |
| AC-12.05 | Một câu Đúng/Sai đúng một phần nhận đúng điểm theo bảng cấu hình. Một câu trả lời ngắn chỉ được chấp nhận khi khớp với đáp án đã khai báo. |
| AC-12.06 | Cùng bài làm và cùng snapshot cho cùng kết quả khi chấm lại. Cập nhật ngân hàng sau khi thi không làm thay đổi điểm cũ. |
| AC-12.07 | Khi chấm gặp lỗi, hệ thống giữ bài đã nộp, hiển thị trạng thái xử lý và không hiển thị 0 điểm thay cho kết quả chưa có. |
| AC-12.08 | Bài thi có phần bắt buộc cần chấm tay được phát hiện trước khi mở thi. Hệ thống chỉ rõ phần hoặc câu chưa đáp ứng điều kiện chấm tự động. |
| AC-12.09 | Học viên xem được cùng bảng điểm trên web và ứng dụng. Lỗi tạo lời giải AI không ảnh hưởng đến điểm hoặc thời điểm bảng điểm xuất hiện. |
