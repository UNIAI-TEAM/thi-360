# Thiết lập cấu trúc đề động V-SAT

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-18 15:13, lấy về ngày 2026-11-18. Nguồn: uniworkos, đính kèm của PBI-04 UNI-1633 (doc `01M7PD12Q8PQNWM4W9MFB71738`). Chỉ đọc; bản gốc ở uniworkos.

## 1. Phạm vi thiết lập
Quản trị viên chọn **V-SAT → Môn thi → Phạm vi bài**. Mỗi môn có cấu trúc, thời lượng và kết quả riêng. Tám môn trong tài liệu gồm Toán, Vật lí, Hóa học, Sinh học, Lịch sử, Địa lí, Ngữ văn và Tiếng Anh.
| Phạm vi | Hành vi trong V1 |
|---|---|
| **Bài FULL theo môn** | Nạp toàn bộ cấu trúc của một môn. Áp dụng cho bảy môn có thể chấm tự động toàn bài, khi ngân hàng đủ câu và đáp án. |
| **Bài theo phần hoặc chủ đề trong môn** | Quản trị viên chọn các phần hoặc nguồn kiến thức cần luyện, điều chỉnh số lượng và xác nhận thời lượng. Kết quả dùng thang điểm của phần học viên thực làm. |
| **Bộ bài nhiều môn** | Chọn các bài đã xuất bản để đóng bộ bán. Sau khi mua, học viên chọn từng môn để làm. Mỗi môn có lượt, đồng hồ và kết quả riêng. |
## 2. Luồng thiết lập của quản trị viên
| Bước | Thao tác | Phản hồi của hệ thống |
|---|---|---|
| **1. Khai báo chung** | Nhập mã, tên bài. Chọn V-SAT, môn và phạm vi. | Nạp các phần tương ứng từ cấu hình của môn. Môn, dạng câu và độ khó lấy từ danh mục dùng chung. |
| **2. Chọn cấu trúc** | Với bài FULL, xem toàn bộ các phần. Với bài theo phần, chọn phần đưa vào bài và nhập thời lượng. | Hiển thị thứ tự, dải số câu, số lệnh hỏi và điểm tối đa. Cập nhật các tổng ngay khi thay đổi phạm vi. |
| **3. Thiết lập ma trận** | Mở từng phần, chọn nguồn câu, cách lấy câu đơn/chùm và số lượng. Bật phân bổ lớp, độ khó hoặc MAPID khi cần. | Hiển thị số đã phân bổ, số còn thiếu hoặc vượt, nguồn hợp lệ và chỉ tiêu nào sẽ được dùng khi sinh đề. |
| **4. Xác nhận cách chấm** | Kiểm tra đáp án, quy tắc chấm và band điểm đã khai báo trong ngân hàng. | Hiển thị điểm tối đa của phần và ví dụ chấm câu đúng một phần. |
| **5. Kiểm tra và dùng thử** | Kiểm tra cấu hình, sinh đề thử, làm bài và nộp thử. | Trả kết quả đối chiếu theo từng dòng ma trận. Lượt thử không tiêu hao quyền của học viên. |
| **6. Xuất bản và mở bán** | Xác nhận phạm vi bài, giá, lượt, thời hạn và voucher nội bộ nếu áp dụng. | Lưu phiên bản đã kiểm tra. Trang mua, màn bắt đầu và màn kết quả sử dụng cùng cấu hình. |
## 3. Thiết lập cách lấy câu của từng môn
Mỗi phần có một hoặc nhiều **dòng lấy câu**. Một dòng gồm dạng trả lời, nguồn ngân hàng, cách lấy câu độc lập hoặc nguyên chùm, số lượng và vị trí trong bài. Với chùm, quản trị viên khai báo **số chùm × số câu trong mỗi chùm**. Hệ thống tự tính tổng câu, lệnh hỏi và điểm.
Ví dụ, để thiết lập phần II môn Toán, quản trị viên chọn **Trắc nghiệm chọn một → Nguyên chùm → 2 chùm → 3 câu mỗi chùm**. Kết quả hiển thị là **6 câu, 6 lệnh hỏi, 36 điểm**. Nếu tạo bài luyện riêng và tăng lên 3 chùm cùng kích thước, kết quả đổi thành **9 câu, 9 lệnh hỏi, 54 điểm**.
### 4.1. Mặc định theo môn trong đề mẫu khách hàng cung cấp
| Môn | Cách lấy phần II | Điều kiện cần giữ khi sinh đề |
|---|---|---|
| Toán | 2 chùm dữ kiện × 3 câu | Giữ bảng hoặc dữ kiện chung. Có thể lọc thêm lớp, độ khó và MAPID. |
| Vật lí | 2 chùm × 3 câu | Giữ bảng thông số, hình, đồ thị và đơn vị mà câu hỏi sử dụng. |
| Hóa học | 2 chùm × 3 câu | Giữ hình thí nghiệm, đồ thị và công thức đi cùng chùm. |
| Sinh học | 2 chùm × 3 câu | Một chùm có thể dùng nhiều hình hoặc bảng. Hệ thống lấy đầy đủ tài nguyên của chùm. |
| Lịch sử | 2 chùm sử liệu × 3 câu | Giữ đoạn trích và nguồn. Có thể lọc theo mạch kiến thức/MAPID. |
| Địa lí | 2 chùm × 3 câu | Mẫu gồm một bảng số liệu và một biểu đồ. Giữ chú giải, mốc thời gian và đơn vị. |
| Ngữ văn | 2 chùm × 3 câu | Mẫu gồm một chùm thơ và một chùm truyện. Phần I và III vẫn giữ đúng dạng Đúng/Sai và ghép hợp. |
| Tiếng Anh | 6 câu chọn một dùng chung bài đọc với 2 câu cuối phần I | Cấu hình một chùm liên kết qua hai phần theo mục 4.2. |
**Thời lượng mặc định** là 90 phút cho Toán FULL và 60 phút cho từng môn FULL còn lại trong V1. Riêng Hóa học, tài liệu khách hàng ghi 50 phút trên đề minh họa, còn quy chế ghi 60 phút. Đề xuất dùng **60 phút cho bài FULL** và cho phép xác nhận **50 phút cho bài luyện mô phỏng đề mẫu**. Ngữ văn toàn môn có 90 phút gồm cả bài viết, vì vậy quản trị viên phải xác nhận thời lượng riêng cho bài trắc nghiệm I–III.
### 4.2. Cấu hình riêng của Tiếng Anh
| Vị trí | Dòng lấy câu | Kết quả |
|---|---|---|
| Câu 1–7 | Lấy 7 câu Đúng/Sai độc lập, mỗi câu có ngữ liệu ngắn và 4 nhận định | 7 câu, 28 lệnh |
| Câu 8–15 | Lấy **một Question Set** gồm 2 câu Đúng/Sai và 6 câu chọn một. Hai câu đầu hiển thị ở phần I, sáu câu sau hiển thị ở phần II | 8 câu, 14 lệnh |
| Câu 16–20 | Lấy 5 câu ghép hợp, mỗi câu 4 mục | 5 câu, 20 lệnh |
| Câu 21–25 | Lấy **một đoạn Cloze** có 5 vị trí, mỗi vị trí điền một từ và có đáp án riêng | 5 câu, 5 lệnh |
Khi tạo bài FULL, bộ sinh chỉ chọn bài đọc câu 8–15 **một lần**. Nếu học viên chỉ luyện phần II, hệ thống giao sáu câu chọn một cùng đầy đủ bài đọc. Có thể dùng nhánh sáu câu của chùm liên phần khi ngân hàng xác nhận nhánh này làm được độc lập. Nếu chưa có nguồn phù hợp, màn kiểm tra chỉ rõ đang thiếu chùm sáu câu cho phần II.
## 5. Ma trận động theo lớp, độ khó và MAPID
### 5.1. Cách nhập và áp dụng chỉ tiêu
1. **Chọn phạm vi phân bổ.** Quản trị viên chọn toàn môn hoặc một phần. Hệ thống hiển thị mẫu số ngay trên bảng, ví dụ **Toán FULL · 67 lệnh** hoặc **Toán phần III · 20 lệnh**.
2. **Chọn tiêu chí.** Có thể dùng khối lớp, độ khó, mạch kiến thức hoặc MAPID. Nếu cần đúng số ý Vận dụng của riêng lớp 12, quản trị viên dùng bảng kết hợp **Lớp × Độ khó**.
3. **Chọn cách nhập.** Cho nhập số lệnh hoặc tỷ lệ %. Hai giá trị hiển thị cạnh nhau. Khi quy đổi %, lấy phần nguyên trước, phân số lệnh còn dư cho các dòng có phần thập phân lớn nhất. Quản trị viên thấy số nguyên thực lấy trước khi xác nhận.
4. **Chọn mức áp dụng.** Mặc định các tỷ lệ từ đề minh họa ở mức **Chỉ đối chiếu**. Khi bật **Áp dụng khi sinh đề**, bộ sinh phải đáp ứng chỉ tiêu đó cùng các điều kiện khác của phần.
5. **Lọc bằng MAPID khi cần.** MAPID là trường chọn nhiều. Để trống để dùng toàn bộ nguồn của dòng. Khi chọn nhiều mã, lấy câu/ý khớp ít nhất một mã. Khi phân hạn ngạch theo MAPID, mỗi lệnh chỉ được tính một lần trong cùng bảng.
6. **Xử lý dữ liệu thiếu nhãn.** Câu Đúng/Sai hoặc ghép hợp vẫn được lấy nguyên câu. Ma trận đếm theo nhãn của từng ý. Chỉ dùng nhãn chung cho cả bốn ý khi chuyên môn xác nhận nhãn áp dụng cho cả câu. Dữ liệu thiếu hiển thị **Chưa phân loại**. Đối tác có thể hoàn thiện mapping sau cho các tiêu chí đang để đối chiếu.
### 5.2. Phân bổ tham khảo của tám môn
Mỗi ô ghi **số lệnh (tỷ lệ làm tròn)**. Đây là số liệu phân tích từ đề mẫu khách hàng cung cấp. Dấu “—” nghĩa là nguồn phân loại theo mạch thay cho lớp. Một số hàng có tổng tỷ lệ 99% do làm tròn từng ô, hệ thống dùng số lệnh gốc khi nạp mẫu.
| Môn | Lớp 10 | Lớp 11 | Lớp 12 | Nhận biết | Thông hiểu | Vận dụng |
|---|---|---|---|---|---|---|
| Toán | 0 (0%) | 17 (25%) | 50 (75%) | 13 (19%) | 35 (52%) | 19 (28%) |
| Vật lí | 0 (0%) | 25 (37%) | 42 (63%) | 19 (28%) | 32 (48%) | 16 (24%) |
| Hóa học | 0 (0%) | 23 (34%) | 44 (66%) | 14 (21%) | 32 (48%) | 21 (31%) |
| Sinh học | 0 (0%) | 13 (19%) | 54 (81%) | 15 (22%) | 28 (42%) | 24 (36%) |
| Lịch sử | 6 (9%) | 2 (3%) | 59 (88%) | 15 (22%) | 40 (60%) | 12 (18%) |
| Địa lí | — | — | — | 20 (30%) | 42 (63%) | 5 (7%) |
| Tiếng Anh | — | — | — | 21 (31%) | 39 (58%) | 7 (10%) |
| Ngữ văn I–III | — | — | — | 12 (19%) | 33 (53%) | 17 (27%) |
Với **Lịch sử**, tài liệu có số liệu lớp để đối chiếu. Nếu MAPID ngân hàng chỉ thể hiện mạch kiến thức, quản trị viên dùng mạch và độ khó để sinh đề. Chỉ bật ràng buộc lớp khi ngân hàng có trường lớp riêng đã xác nhận.
| Môn | Mạch nội dung dùng để đối chiếu hoặc thiết lập hạn ngạch |
|---|---|
| **Ngữ văn V1 · 62 lệnh** | Tùy bút/nhật kí 16, văn bản thông tin 16, truyện/kịch 15, văn bản nghị luận 12, thơ 3. Đã loại bài viết khỏi mẫu số. |
| **Địa lí · 67 lệnh** | Các ngành kinh tế 26, các vùng kinh tế 17, dân cư 8, tự nhiên 10, kỹ năng bảng số liệu/biểu đồ/Atlat 6. |
| **Tiếng Anh · 67 lệnh** | Đọc hiểu 42, giao tiếp qua văn bản 4, ngữ pháp và từ vựng 21. |
## 6. Quy tắc lấy ngẫu nhiên và kiểm tra khả năng ghép đề
Bộ sinh phải tìm **một bộ đề đáp ứng đồng thời** cấu trúc, nguồn và các chỉ tiêu bắt buộc. Hệ thống lấy nguyên chùm, ghi nhận số câu và phân loại thực tế mà chùm đóng góp, sau đó điền các vị trí còn lại. Một câu đã xuất hiện trong chùm sẽ không được lấy thêm như câu độc lập.
Ví dụ, quản trị viên tạo **Toán – phần II**, yêu cầu hai chùm, mỗi chùm ba câu, phân bổ **50% Thông hiểu và 50% Vận dụng**. Một kết quả hợp lệ có thể gồm:
| Nguồn đã lấy | Thông hiểu | Vận dụng | Tổng |
|---|---|---|---|
| Chùm A | 1 | 2 | 3 |
| Chùm B | 2 | 1 | 3 |
| **Đề được giao** | **3** | **3** | **6** |
Nếu ngân hàng có sáu câu đúng độ khó nhưng chỉ có một chùm đủ ba câu, hệ thống báo **“Phần II cần 2 chùm × 3 câu, hiện có 1 chùm phù hợp”**. Nếu quản trị viên bật thêm MAPID Xác suất, hệ thống kiểm tra lại điều kiện chùm và độ khó trên nguồn Xác suất.
Sau khi sinh thành công, hệ thống lưu phiên bản câu hỏi, ngữ liệu, đáp án, thứ tự, cấu hình điểm và thời lượng của lượt. Học viên mở lại trên web hoặc app sẽ tiếp tục đúng đề đã giao. Sinh đề thất bại trước khi bắt đầu không làm mất lượt.
## 7. Quy tắc chấm và band điểm
| Dạng câu | Quy tắc V1 | Ví dụ |
|---|---|---|
| Đúng/Sai | Đúng 0, 1, 2, 3, 4 ý lần lượt được **0, 1, 2, 3, 6 điểm** | Đúng 3/4 ý được **3 điểm** |
| Chọn một đáp án | Mỗi câu đúng được **6 điểm** | Đúng 2/3 câu trong chùm được **12/18** |
| Ghép hợp | Mỗi cặp đúng được **1,5 điểm** | Đúng 3/4 cặp được **4,5/6** |
| Trả lời ngắn | Khớp đáp án hoặc biến thể đã khai báo trong ngân hàng được **6 điểm** | Dùng định dạng và yêu cầu làm tròn của câu |
| Cloze Tiếng Anh | Mỗi vị trí có đáp án riêng, đúng được **6 điểm** | Đúng 4/5 vị trí được **24/30** |
Điểm dễ nhầm nhất là **cùng đúng 3/4 nhưng Đúng/Sai được 3 điểm, Ghép hợp được 4,5 điểm**. Khi kiểm tra cấu hình, hệ thống phải cho quản trị viên thấy ví dụ này và phát hiện câu còn dùng mặc định 1 điểm. Quy tắc trên phù hợp với hướng dẫn chấm điểm thô V-SAT 2025. [hvnh.edu.vn](https://hvnh.edu.vn/bavts/vi/ky-thi-v-sat/cach-tinh-diem-263.html?utm_source=chatgpt.com)
Kết quả V1 hiển thị **điểm đạt được / điểm tối đa của phạm vi đã làm**. Band nhận xét chỉ hiển thị khi đã cấu hình đúng môn và phạm vi. Ngữ văn I–III dùng thang 120. AI giải thích theo đáp án và lời giải sau khi trả điểm. V1 chưa thực hiện chấm bài viết hoặc quy đổi sang điểm năng lực tuyển sinh.
## 8. Kiểm tra và dùng thử
Trước khi mở bán, quản trị viên có hai thao tác riêng.
| Thao tác | Nội dung kiểm tra |
|---|---|
| **Kiểm tra cấu hình** | Kiểm tra tổng câu, lệnh, điểm, thời lượng, đáp án, tài nguyên, liên kết chùm và khả năng đáp ứng đồng thời các chỉ tiêu. Báo đúng môn, phần, dòng cần sửa. Phân biệt lỗi chặn với sai khác ở bảng chỉ đối chiếu. |
| **Sinh đề và làm thử** | Hiển thị đề theo cách học viên nhìn thấy, gồm ngữ liệu, hình, bảng, ô nhập, thứ tự và đồng hồ. Quản trị viên có thể trả lời, nộp bài, xem điểm và so sánh phân bổ thực tế với ma trận. |
Ví dụ, ở Tiếng Anh FULL, bài đọc có đủ sáu câu chọn một nhưng thiếu hai câu Đúng/Sai phải được báo lỗi ngay tại dòng **Bài đọc dùng chung – câu 8–15**. Tương tự, ở Ngữ văn V1, cấu hình đủ 20 câu nhưng đang đặt thang 150 phải chỉ rõ chênh lệch **30 điểm của phần Viết**.
## 9. Xuất bản và sử dụng trên web/app
Khi xuất bản, hệ thống lưu một phiên bản cấu trúc và dùng phiên bản đó cho lượt mới. Thay đổi ngân hàng hoặc ma trận sau này giữ nguyên đề và kết quả của lượt đã làm. Học viên xem kết quả trực tiếp sau khi chấm tự động.
| Bài hiển thị cho học viên | Số câu | Thời lượng | Điểm tối đa |
|---|---|---|---|
| V-SAT Toán – FULL | 25 | 90 phút | 150 |
| V-SAT Ngữ văn – Trắc nghiệm (phần I–III) | 20 | Thời lượng đã xác nhận cho ba phần | 120 |
| V-SAT Tiếng Anh – Điền từ, một đoạn theo mẫu | 5 | Thời lượng đã xác nhận cho bài luyện | 30 |
Ba thông tin **số câu, thời lượng và điểm tối đa** phải lấy trực tiếp từ cấu trúc đã xuất bản, để học viên thấy cùng một phạm vi ở trang mua, màn bắt đầu và màn kết quả.
