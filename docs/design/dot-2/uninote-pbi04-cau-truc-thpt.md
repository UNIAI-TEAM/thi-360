# Thiết lập bài thi và ma trận đề thi tốt nghiệp THPT

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-17 15:09, lấy về ngày 2026-11-18. Nguồn: uniworkos, đính kèm của PBI-04 UNI-1633 (doc `01M7M8EXWPZN7NDH484GJRXDD6`). Chỉ đọc; bản gốc ở uniworkos.

## 1. Phạm vi thiết lập

Tại **Quản lý bài thi → Tạo bài thi**, quản trị viên chọn kỳ thi **Tốt nghiệp THPT**, chọn môn từ danh mục dùng chung và chọn phạm vi bài thi. Hệ thống khởi tạo cấu trúc theo môn. Quản trị viên không cần khai báo năm áp dụng.
| Phạm vi | Cách thiết lập |
|---|---|
| **Bài thi theo môn – Đầy đủ cấu trúc** | Hệ thống điền sẵn các phần, dạng câu, số lượng, thời lượng và thang điểm của môn. |
| **Bài ôn theo môn hoặc chủ đề** | Quản trị viên chọn môn, một hoặc nhiều MAPID, số câu và dạng trả lời. Có thể bổ sung chỉ tiêu độ khó. |
| **Bộ đề để bán** | Quản trị viên chọn các bài đã thiết lập để đóng bộ. Học viên mua bộ một lần và làm từng môn trong các lượt độc lập. |
V1 hỗ trợ chấm tự động cho 11 trong 12 cấu trúc của tài liệu khi ngân hàng đủ nguồn câu. **Ngữ văn là trường hợp ngoại lệ vì cả phần Đọc hiểu và phần Viết đều là tự luận.**

## 2. Cấu trúc mặc định theo môn

Cột **Tổng câu** tính theo số câu một học viên thực hiện. Một câu Đúng/Sai có bốn ý vẫn được tính là một câu. Các đề đầy đủ đều sử dụng thang 10 điểm.
| Môn | Chọn 1 trong 4 | Đúng/Sai | Trả lời ngắn | Tự luận | Tổng câu | Thời lượng |
|---|---|---|---|---|---|---|
| Toán | 12 | 4 | 6 | — | 22 | 90 phút |
| Vật lí | 18 | 4 | 6 | — | 28 | 50 phút |
| Hóa học | 18 | 4 | 6 | — | 28 | 50 phút |
| Sinh học | 18 | 4 | 6 | — | 28 | 50 phút |
| Địa lí | 18 | 4 | 6 | — | 28 | 50 phút |
| Lịch sử | 24 | 4 | — | — | 28 | 50 phút |
| Giáo dục kinh tế và pháp luật | 24 | 4 | — | — | 28 | 50 phút |
| Tin học | 24 | Làm 4 trong 6 câu cấu hình | — | — | 28 | 50 phút |
| Công nghệ – Công nghiệp | 24 | 4 | — | — | 28 | 50 phút |
| Công nghệ – Nông nghiệp | 24 | 4 | — | — | 28 | 50 phút |
| Tiếng Anh | 40 câu thuộc 6 nhóm | — | — | — | 40 | 50 phút |
| Ngữ văn | — | — | — | 5 câu đọc hiểu + 2 câu viết | 7 | 120 phút |
## 3. Luồng thiết lập ma trận

1. **Khai báo tổng quan.** Quản trị viên nhập mã, tên bài, chọn môn và phạm vi. Với đề đầy đủ, hệ thống hiển thị các giá trị mặc định ở mục 2. Với bài theo phần hoặc chủ đề, quản trị viên xác nhận số câu và thời lượng riêng.

2. **Chọn phần cần thiết lập.** Mỗi dòng hiển thị tên phần, dạng trả lời, số câu cần lấy, điểm tối đa và số câu phù hợp trong ngân hàng. Quản trị viên mở từng dòng để chỉnh nguồn.

3. **Chọn phạm vi lấy câu.** Mặc định là **Toàn môn**. Khi chọn **Theo mạch kiến thức**, quản trị viên có thể chọn nhiều MAPID. Câu mang ít nhất một mã đã chọn được đưa vào nguồn. Nếu cần đúng số câu của từng mạch, quản trị viên bật **Phân bổ theo MAPID** và nhập số lượng.

4. **Thiết lập lớp và độ khó nếu áp dụng.** Với môn có dữ liệu phân lớp, quản trị viên có thể khai báo hạn ngạch lớp 10/11/12. Với **Lịch sử, Địa lí, Giáo dục kinh tế và pháp luật, Tiếng Anh**, hệ thống lọc theo mạch kiến thức hoặc kỹ năng trong MAPID. Câu thiếu khối lớp vẫn đủ điều kiện nếu đáp ứng các tiêu chí đã chọn.

5. **Kiểm tra và sinh thử.** Hệ thống so sánh số câu yêu cầu, số câu có thể ghép và số câu thực tế trong đề thử. Một câu gắn nhiều MAPID chỉ xuất hiện một lần và chỉ đóng góp vào một hạn ngạch chủ đề cùng cấp.

**Quy tắc khởi tạo:** File khách hàng cung cấp số câu và điểm theo phần. Với những môn chưa có tỷ lệ lớp, độ khó hoặc MAPID cụ thể, hệ thống để các chỉ tiêu đó ở chế độ tùy chọn. Quản trị viên vẫn có thể sinh đề theo đúng dạng câu và số lượng mặc định.

## 4. Thiết lập các môn theo phần
| Nhóm môn | Phần I | Phần II | Phần III |
|---|---|---|---|
| Toán | 12 câu trắc nghiệm, 3 điểm | 4 câu Đúng/Sai, 4 điểm | 6 câu trả lời ngắn, **3 điểm** |
| Vật lí, Hóa học, Sinh học, Địa lí | 18 câu trắc nghiệm, 4,5 điểm | 4 câu Đúng/Sai, 4 điểm | 6 câu trả lời ngắn, **1,5 điểm** |
| Lịch sử, Giáo dục kinh tế và pháp luật, Công nghệ – Công nghiệp, Công nghệ – Nông nghiệp | 24 câu trắc nghiệm, 6 điểm | 4 câu Đúng/Sai, 4 điểm | — |
**Phần I – Trắc nghiệm.** Hệ thống lấy câu có bốn lựa chọn và một đáp án đúng. Nếu nhiều câu cùng khai thác một đoạn tư liệu, hình hoặc bảng số liệu, quản trị viên có thể khai báo số chùm và số câu con. Các câu con đã nằm trong tổng số câu của phần.

**Phần II – Đúng/Sai.** Mỗi câu lấy nguyên nội dung dẫn, bốn mệnh đề a–d và đáp án của từng mệnh đề. Khi yêu cầu 4 câu, giao diện hiển thị **4 câu · 16 ý · tối đa 4 điểm**. Hệ thống tính điểm riêng cho từng câu theo mục 7.

**Phần III – Trả lời ngắn.** Câu phải có đáp án và quy tắc đối sánh mà hệ thống chấm được. Nếu yêu cầu đơn vị hoặc làm tròn, hướng dẫn đó phải hiển thị cùng câu hỏi. Điểm mỗi câu lấy theo cấu hình môn: **Toán 0,5 điểm**, các môn còn lại có phần này **0,25 điểm**.

**Ví dụ bài luyện theo phần:** Quản trị viên chọn **Địa lí → Phần III – Trả lời ngắn**, giữ 6 câu, giới hạn nguồn theo MAPID *Kỹ năng biểu đồ – bảng số liệu* và nhập thời lượng. Nếu dùng cách chấm gốc, bài luyện có thang **1,5 điểm**. Thời lượng 50 phút ở mục 2 áp dụng cho toàn môn.

## 5. Thiết lập môn Tin học theo định hướng

Tin học có **30 vị trí trong cấu trúc**, nhưng một học viên chỉ làm **28 câu**. Phần II gồm 2 câu chung và hai nhánh, mỗi nhánh 2 câu. Học viên làm cả hai câu thuộc cùng một định hướng
| Khối nội dung | Quản trị viên thiết lập | Một học viên làm |
|---|---|---|
| Phần I – Trắc nghiệm chung | 24 câu | 24 câu |
| Phần II – Đúng/Sai chung | 2 câu | 2 câu |
| Phần II – Khoa học máy tính | 2 câu | 2 câu nếu chọn nhánh này |
| Phần II – Tin học ứng dụng | 2 câu | 2 câu nếu chọn nhánh này |
| **Tổng** | **30 vị trí** | **28 câu, tối đa 10 điểm** |
Quản trị viên thiết lập nguồn cho cả hai nhánh. **Đề xuất cho V1** là đặt bộ chọn định hướng ở màn chuẩn bị thi. Học viên có thể đổi lựa chọn trước khi nhấn **Bắt đầu**. Khi bắt đầu, hệ thống sinh phần chung và nhánh đã chọn, lưu định hướng cùng lượt thi. Khi tiếp tục lượt đang làm, hệ thống khôi phục đúng nhánh và bộ câu.

Hai định hướng **Công nghệ – Công nghiệp** và **Công nghệ – Nông nghiệp** được chọn ngay khi quản trị viên tạo bài. Mỗi định hướng là một cấu hình môn riêng, có nguồn MAPID riêng.

## 6. Thiết lập Tiếng Anh và phạm vi Ngữ văn

### 6.1. Tiếng Anh

Hệ thống khởi tạo **6 nhóm theo thứ tự, tổng 40 câu**. Toàn bộ câu hỏi trả lời bằng cách chọn một trong bốn phương án, kể cả bài đọc điền và sắp xếp.
| Nhóm dạng bài | Dải câu | Số câu | Điểm | Cách lấy |
|---|---|---|---|---|
| Reading 1 – Đọc điền | 1–6 | 6 | 1,5 | Lấy ngữ liệu có 6 vị trí, giữ phương án tương ứng từng vị trí |
| Reading 2 – Đọc điền | 7–12 | 6 | 1,5 | Lấy ngữ liệu khác nhóm 1, có đủ 6 vị trí |
| Sắp xếp hội thoại hoặc văn bản | 13–17 | 5 | 1,25 | Lấy 5 câu đúng dạng. Học viên chọn phương án chứa thứ tự đúng |
| Reading 3 – Đọc điền câu | 18–22 | 5 | 1,25 | Lấy ngữ liệu có 5 vị trí điền câu |
| Reading 4 – Đọc hiểu | 23–30 | 8 | 2 | Lấy một bài đọc cùng 8 câu con |
| Reading – Đọc hiểu | 31–40 | 10 | 2,5 | Lấy một bài đọc cùng 10 câu con |
Khi mở một nhóm, quản trị viên có thể giới hạn nguồn theo MAPID ngữ pháp, từ vựng hoặc kỹ năng đọc. Bộ sinh giữ thứ tự nhóm và quan hệ giữa văn bản, vị trí trống và câu trả lời. Ví dụ, nếu ngân hàng có 10 câu đọc hiểu rải trên nhiều văn bản nhưng chưa có một văn bản đủ 10 câu con, nhóm cuối vẫn được báo **thiếu một chùm 10 câu**.

### 6.2. Ngữ văn
| Phần | Cấu trúc trong tài liệu | Điểm | Phạm vi V1 |
|---|---|---|---|
| I. Đọc hiểu | Một ngữ liệu, 5 câu tự luận ngắn gồm 2 nhận biết, 2 thông hiểu và 1 vận dụng | 4 | Tạm bỏ qua |
| II. Viết | Một đoạn văn khoảng 200 chữ và một bài văn khoảng 600 chữ | 2 + 4 | Tạm bỏ qua |
Khi chọn Ngữ văn, hệ thống hiển thị **“Toàn bộ môn Ngữ văn là tự luận – tạm bỏ qua trong V1”**. Năm câu đọc hiểu cần đánh giá nội dung học viên viết. Vì vậy, hệ thống lưu được cấu trúc tham khảo nhưng chưa cho xuất bản hoặc thêm môn này vào bộ đề bán dưới dạng bài chấm tự động.

## 7. Quy tắc chấm điểm và kết quả
| Dạng trả lời | Quy tắc chấm |
|---|---|
| Trắc nghiệm chọn một đáp án | Đúng được 0,25 điểm/câu |
| Đúng/Sai, mỗi câu 4 ý | Đúng 0 ý: 0 điểm. Đúng 1 ý: 0,1 điểm. Đúng 2 ý: 0,25 điểm. Đúng 3 ý: 0,5 điểm. Đúng 4 ý: 1 điểm |
| Trả lời ngắn môn Toán | Đúng được 0,5 điểm/câu |
| Trả lời ngắn môn Vật lí, Hóa học, Sinh học, Địa lí | Đúng được 0,25 điểm/câu |
**Band Đúng/Sai áp dụng trên từng câu.** Hai câu, mỗi câu đúng 2 ý, cho tổng **0,5 điểm**. Nếu câu thứ nhất đúng cả 4 ý và câu thứ hai đúng 0 ý, tổng là **1 điểm**. Màn dùng thử và màn kết quả cần hiển thị điểm từng câu để người dùng đối chiếu được sự khác biệt này.

Khi nộp bài hoặc hết giờ, học viên xem trực tiếp điểm theo phần và toàn bài. Lượt thi lưu phiên bản câu hỏi, thứ tự, đáp án và quy tắc chấm đã sử dụng. Bài luyện theo phần hiển thị thang điểm đã công bố khi mua.

## 8. Kiểm tra cấu hình và dùng thử
| Nội dung kiểm tra | Kết quả cần hiển thị |
|---|---|
| Cấu trúc và điểm | So sánh yêu cầu với thực tế theo từng phần. Ví dụ, Toán phải đủ **12 + 4 + 6 = 22 câu** và **3 + 4 + 3 = 10 điểm** |
| Khả năng ghép toàn bài | Kiểm tra các nguồn MAPID giao nhau, câu trùng, số ý, đáp án và ngữ liệu đi kèm. Nếu thiếu, chỉ rõ phần và số lượng cần bổ sung |
| Tin học | Cho dùng thử độc lập từng định hướng. Mỗi nhánh phải tạo được một lượt 28 câu, tối đa 10 điểm |
| Tiếng Anh | Kiểm tra đủ 6 nhóm, đúng dải câu và đủ số câu con trên từng ngữ liệu |
| Chấm thử trên web và app | Kiểm tra chọn đáp án, nhập trả lời ngắn, chấm band Đúng/Sai và hiển thị điểm. Lượt thử không trừ quyền học viên |
Quản trị viên có thể lưu nháp dù còn thiếu nguồn. Trước khi xuất bản, các phần áp dụng phải kiểm tra đạt. Nếu ngân hàng thay đổi và tại thời điểm bắt đầu không còn ghép được đề hợp lệ, hệ thống báo lỗi, không mở lượt và không trừ quyền của học viên.

## 9. Xuất bản và thiết lập bán

Quản trị viên xác nhận môn, phạm vi, số câu, thời lượng và điểm tối đa trước khi xuất bản. Bài đã xuất bản có thể bán riêng hoặc đưa vào bộ đề. Khi tạo bộ, quản trị viên chọn từng môn và bài tương ứng, khai báo giá, số lượt mỗi bài, thời hạn và voucher dành cho học viên nội bộ nếu áp dụng.

Học viên mua bộ một lần, chủ động chọn môn để làm và nhận kết quả riêng cho môn đó. Mỗi môn có đồng hồ và số lượt riêng. Khi quản trị viên cập nhật giá, thành phần bộ hoặc quyền lợi, hệ thống áp dụng cấu hình mới cho đơn mua mới và bảo toàn quyền đã cấp cùng lịch sử các lượt trước đó.
