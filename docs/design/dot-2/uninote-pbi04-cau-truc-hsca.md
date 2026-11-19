# Thiết lập cấu trúc đề thì H-SCA

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-18 10:43, lấy về ngày 2026-11-18. Nguồn: uniworkos, đính kèm của PBI-04 UNI-1633 (doc `01M7MEFX0EC1B1BH6FBAY4N982`). Chỉ đọc; bản gốc ở uniworkos.

## 1. Phạm vi và loại bài thi
H-SCA gồm sáu bài thi độc lập: Toán, Vật lí, Hóa học, Sinh học, Ngữ văn và Tiếng Anh. Vì vậy, trong hệ thống, **bài FULL là bài đầy đủ của một môn**. Khi bán bộ nhiều môn, học viên chọn từng bài để làm, mỗi bài có lượt, thời lượng và kết quả riêng.
| Môn | Cấu trúc đầy đủ theo tài liệu khách hàng | Thời lượng toàn môn | Phạm vi triển khai V1 |
|---|---|---|---|
| Toán, Vật lí, Hóa học, Sinh học | Mỗi môn 3 phần, 40 câu | 90 phút/môn | Bài FULL từng môn hoặc bài theo phần/chủ đề khi đủ nguồn và quy tắc chấm |
| Ngữ văn | Đọc hiểu 20 câu, viết đoạn 1 bài, viết bài 1 bài | 90 phút | Xuất bản bài **Ngữ văn – Đọc hiểu** . Lưu hai phần viết trong cấu trúc tham chiếu |
| Tiếng Anh | Nghe, Nói, Đọc, Viết. File có 35 câu Nghe, 40 câu Đọc và 2 bài Viết | 180 phút | Xuất bản bài Nghe, bài Đọc hoặc bài kết hợp Nghe–Đọc. Phần Nói chưa có ma trận chi tiết trong file |
**Phương án tái sử dụng:** H-SCA dùng ba cấu hình khởi tạo trên cùng công cụ ma trận: một cấu hình chung cho bốn môn 40 câu, một cho Ngữ văn và một cho Tiếng Anh. Khi quản trị viên chọn môn, hệ thống điền sẵn các dòng cấu trúc và số liệu của môn đó.
## 2. Luồng thiết lập bài thi
| Bước | Thao tác của quản trị viên | Hệ thống xử lý |
|---|---|---|
| 1. Khai báo chung | Nhập mã, tên bài. Chọn kỳ thi **H-SCA** , môn và phạm vi: FULL, theo phần/kỹ năng hoặc chủ đề | Nạp cấu trúc của môn. Với bài theo phần, chỉ nạp các phần được chọn |
| 2. Xác nhận cấu trúc | Xem thứ tự các phần, số câu, dạng trả lời, câu đơn/chùm và thời gian | Hiển thị tổng số câu thực tế. Với bài rút gọn, quản trị viên xác nhận thời lượng riêng |
| 3. Thiết lập ma trận | Chọn nguồn câu. Bật các tiêu chí cần dùng: lớp, độ khó, dạng bài hoặc MAPID | Cho nhập theo **số câu** hoặc **tỷ lệ** . Hiển thị số đã phân bổ, số thiếu hoặc vượt |
| 4. Thiết lập chấm | Chọn đáp án, quy tắc chấm từng dạng và thang/band điểm của bài | Cho xem trước kết quả với trường hợp đúng, đúng một phần, sai và bỏ trống |
| 5. Kiểm tra, dùng thử | Kiểm tra ngân hàng, sinh đề và thực hiện lượt thử | Đối chiếu **Yêu cầu / Thực tế / Chênh lệch** đến từng dòng cấu hình |
| 6. Mở bán | Xác nhận phạm vi, giá, số lượt, thời hạn, quyền xem kết quả và voucher nội bộ nếu áp dụng | Xuất bản phiên bản đã kiểm tra. Thẻ bài thi thể hiện đúng môn, phần, số câu và thời lượng |
## 3. Quy tắc ma trận động
Đơn vị thiết lập là **Môn → Phần → Dòng lấy câu**. Một phần có thể chứa nhiều dạng trả lời. Ví dụ, phần Trắc nghiệm môn Toán có hai dòng: 20 câu chọn một đáp án và 5 câu chọn nhiều đáp án. Học viên vẫn thấy đây là **một phần thi**.
| Tiêu chí | Quy tắc cấu hình |
|---|---|
| Số câu và tỷ lệ | Hiển thị hai cột cạnh nhau. Chỉnh một cột thì tính lại cột kia trên tổng câu của phạm vi đang mở. Nếu tỷ lệ ra số lẻ, lấy phần nguyên rồi phân câu dư cho dòng có phần thập phân lớn nhất. Nếu bằng nhau, ưu tiên thứ tự dòng. Quản trị viên thấy số câu thực lấy trước khi lưu |
| Khối lớp và độ khó | Cho chọn **Chỉ đối chiếu** hoặc **Áp dụng khi sinh đề** . Khi bật đồng thời hai bảng, hệ thống phải ghép được cùng một tập câu thỏa cả hai |
| MAPID | Có thể để trống, chọn nhiều mã để lọc nguồn, hoặc ấn định số câu cho từng nhóm mã. Một câu có nhiều mã chỉ chiếm một vị trí trong đề và một dòng trong cùng bảng hạn ngạch |
| Mapping đối tác | Cho lưu nháp dòng kiến thức chờ mapping. Chỉ dòng dùng MAPID làm điều kiện bắt buộc mới cần hoàn tất mapping trước khi sinh. Bài chỉ dùng lớp và độ khó vẫn có thể triển khai khi đủ nguồn |
| Câu nhiều đáp án | Một câu có 4–5 phương án hoặc ý vẫn được tính là **một câu** trong ma trận. Độ khó khai báo cho cả câu |
| Câu chùm | Ngữ liệu, hình hoặc audio được khai báo ở cấp chùm. Số câu, độ khó, đáp án và MAPID kiến thức được tính theo từng câu con. Hệ thống lấy nguyên chùm và giữ các câu con liền nhau |
Ví dụ, để tạo bài Toán 12 câu ôn Xác suất, quản trị viên chọn nguồn Toán, lọc MAPID Xác suất, rồi nhập tỷ lệ độ khó **25% / 50% / 25%**. Hệ thống cần tìm được **3 câu Nhận biết, 6 câu Thông hiểu và 3 câu Vận dụng** trong chính nguồn Xác suất.
## 4. Thiết lập Toán, Vật lí, Hóa học và Sinh học
### 4.1. Khung chung của bài FULL
| Phần thi | Dòng lấy câu | Dải câu | Số câu | Tỷ lệ trên 40 câu |
|---|---|---|---|---|
| 1. Trắc nghiệm | Câu đơn, chọn một trong bốn phương án | 1–20 | 20 | 50% |
| 1. Trắc nghiệm | Chọn nhiều phương án đúng | 21–25 | 5 | 12,5% |
| 2. Dữ liệu tổng hợp | Câu chùm | 26–30 | 5 | 12,5% |
| 3. Điền đáp án | Nhập đáp án ngắn theo yêu cầu từng câu | 31–40 | 10 | 25% |
| **Tổng** | **3 phần, 4 dòng lấy câu** | **1–40** | **40** | **100%** |
Bài FULL có đồng hồ chung **90 phút**. Khi sinh đề, hệ thống giữ thứ tự bốn dòng và chọn ngẫu nhiên câu phù hợp trong từng dòng. Riêng câu 21–25 cần bộ trả lời **chọn nhiều phương án** vì cách chấm phụ thuộc vào tập phương án học viên đã chọn.
### 4.2. Phân bổ khối lớp và độ khó
| Môn | Lớp 10 | Lớp 11 | Lớp 12 |
|---|---|---|---|
| Toán | 0 câu · 0% | 14 câu · 35% | 26 câu · 65% |
| Vật lí | 0 câu · 0% | 12 câu · 30% | 28 câu · 70% |
| Hóa học | 10 câu · 25% | 18 câu · 45% | 12 câu · 30% |
| Sinh học | 5 câu · 12,5% | 5 câu · 12,5% | 30 câu · 75% |
| Môn | Nhận biết | Thông hiểu | Vận dụng |
|---|---|---|---|
| Toán | 9 câu · 22,5% | 15 câu · 37,5% | 16 câu · 40% |
| Vật lí | 11 câu · 27,5% | 14 câu · 35% | 15 câu · 37,5% |
| Hóa học | 12 câu · 30% | 15 câu · 37,5% | 13 câu · 32,5% |
| Sinh học | 10 câu · 25% | 16 câu · 40% | 14 câu · 35% |
**Điểm cần xử lý khi thiết lập:** file đồng thời nêu định hướng khoảng **70–80% nội dung kiến thức lớp 12**. Nếu chuyên môn muốn quy đổi định hướng đó thành số câu, một bài 40 câu sẽ cần **28–32 câu lớp 12**. Trong khi đó, mẫu Hóa học chỉ được người biên soạn phân loại **12/40 câu lớp 12**. Hệ thống cần cho quản trị viên chọn mục tiêu áp dụng và xác nhận số câu cụ thể. Tự áp cả hai sẽ tạo ra ma trận không thể sinh đề.
### 4.4. Phân bổ độ khó chi tiết theo dạng
Khi chỉ cần quản lý độ khó toàn môn, quản trị viên có thể dừng ở mục 4.3. Khi cần lấy sát mẫu, mở từng dòng để khai báo chi tiết. Mỗi ô dưới đây lần lượt là số câu **Nhận biết / Thông hiểu / Vận dụng**.
| Môn | Chọn một · 20 câu | Chọn nhiều · 5 câu | Dữ liệu tổng hợp · 5 câu | Điền đáp án · 10 câu |
|---|---|---|---|---|
| Toán | 9 / 10 / 1 | 0 / 1 / 4 | 0 / 2 / 3 | 0 / 2 / 8 |
| Vật lí | 11 / 9 / 0 | 0 / 3 / 2 | 0 / 2 / 3 | 0 / 0 / 10 |
| Hóa học | 12 / 8 / 0 | 0 / 4 / 1 | 0 / 2 / 3 | 0 / 1 / 9 |
| Sinh học | 10 / 9 / 1 | 0 / 4 / 1 | 0 / 2 / 3 | 0 / 1 / 9 |
## 5. Thiết lập Ngữ văn
### 5.1. Phạm vi thiết lập
Chức năng Ngữ văn trong phiên bản hiện tại hỗ trợ **thiết lập bài Đọc hiểu**, sử dụng câu trắc nghiệm chọn một đáp án trong các chùm ngữ liệu.
Khi chọn **H-SCA → Ngữ văn**, hệ thống khởi tạo cấu hình Đọc hiểu. Quản trị viên thiết lập nguồn ngữ liệu, số câu, độ khó, MAPID và thời lượng luyện tập. Phần Viết thuộc phạm vi phát triển sau.
### 5.2. Cấu trúc mặc định
Theo đề minh họa, hệ thống khởi tạo **3 nhóm ngữ liệu, tổng 20 câu**.
| Thứ tự | Loại ngữ liệu | Số chùm × câu con | Số câu | Tỷ lệ trên 20 câu |
|---|---|---|---|---|
| 1 · câu 1–5 | Văn bản thông tin | 1 × 5 | 5 | 25% |
| 2 · câu 6–12 | Văn bản nghị luận | 1 × 7 | 7 | 35% |
| 3 · câu 13–20 | Thơ | 1 × 8 | 8 | 40% |
| **Tổng** | **3 ngữ liệu** | | **20** | **100%** |
Mỗi câu con có bốn phương án và một đáp án đúng. Quản trị viên sử dụng công cụ ma trận dùng chung để điều chỉnh số câu hoặc tỷ lệ của từng nhóm. Khi thay đổi số lượng, hệ thống kiểm tra lại khả năng lấy nguyên chùm từ ngân hàng.
### 5.3. Thông tin thiết lập
| Thông tin | Yêu cầu xử lý |
|---|---|
| **Nguồn ngữ liệu** | Chọn nguồn tương ứng cho từng nhóm Văn bản thông tin, Văn bản nghị luận và Thơ. Nguồn hoặc thuộc tính phân loại phải xác định được loại ngữ liệu |
| **Số câu hoặc tỷ lệ** | Điền sẵn 5 / 7 / 8 câu. Hiển thị số đã phân bổ, còn thiếu hoặc vượt so với tổng mục tiêu |
| **Độ khó** | Cho phân bổ Nhận biết, Thông hiểu và Vận dụng cho toàn phần hoặc từng nhóm. Nếu áp dụng cả hai cấp, tổng các nhóm phải khớp chỉ tiêu toàn phần |
| **MAPID** | Chọn nhiều mã để lọc nguồn hoặc khai báo số câu theo mạch kiến thức. Có thể để trống khi chỉ cần lấy theo loại ngữ liệu và độ khó |
| **Khối lớp** | Tắt mặc định theo dữ liệu mẫu. Chỉ áp dụng khi ngân hàng có phân loại lớp và quản trị viên cần sử dụng tiêu chí này |
| **Thứ tự** | Giữ thứ tự nhóm đã cấu hình. Các câu thuộc cùng ngữ liệu hiển thị liền nhau |
| **Thời lượng** | Quản trị viên nhập thời gian luyện riêng cho phần Đọc hiểu |
Phân bổ độ khó tham khảo được điền sẵn như sau.
| Nhóm ngữ liệu | Nhận biết | Thông hiểu | Vận dụng | Tổng |
|---|---|---|---|---|
| Văn bản thông tin | 2 | 3 | 0 | 5 |
| Văn bản nghị luận | 2 | 3 | 2 | 7 |
| Thơ | 2 | 4 | 2 | 8 |
| **Toàn phần** | **6 · 30%** | **10 · 50%** | **4 · 20%** | **20** |
Quản trị viên chọn **Áp dụng khi sinh đề** nếu muốn bắt buộc phân bổ này. Hệ thống chọn ngẫu nhiên nguyên chùm cùng các câu con, đáp ứng đồng thời nguồn, số lượng và các tiêu chí đã bật.
## 6. Thiết lập cấu trúc Tiếng Anh
### 6.1. Phạm vi thiết lập
Trong V1, quản trị viên tạo **bài Nghe, bài Đọc hoặc bài kết hợp Nghe và Đọc**, sử dụng câu trắc nghiệm chọn một đáp án và Question Set hiện có.
Hệ thống tái sử dụng luồng **Chọn nguồn → Thiết lập dòng cấu trúc → Nhập số lượng → Kiểm tra và sinh thử**. Mẫu H-SCA điền sẵn 35 câu Nghe và 40 câu Đọc. Quản trị viên có thể điều chỉnh để tạo bài luyện theo phần. Nói và Viết thuộc giai đoạn sau.
### 6.2. Quy tắc nhận diện nguồn câu hỏi
Hệ thống tự xác định **Có audio / Không có audio** từ tài nguyên thuộc nội dung đề bài. Quy ước V1 coi audio tại đây là ngữ liệu cần nghe.
| Dữ liệu trong ngân hàng | Phân nhóm nguồn |
|---|---|
| Câu đơn có audio trong nội dung đề bài | Nghe |
| Question Set có audio tại ngữ liệu chung hoặc nội dung câu con | Nghe. Phân nhóm áp dụng cho cả chùm được lấy |
| Câu đơn hoặc Question Set thuộc nguồn Tiếng Anh, đúng dạng trả lời, toàn bộ đề bài không có audio | Đọc |
| Audio chỉ nằm trong lời giải hoặc phản hồi sau khi chấm | Xác định Nghe/Đọc theo nội dung đề bài |
| Có tham chiếu audio nhưng file thiếu, hỏng hoặc không phát được | Báo lỗi tài nguyên của nguồn Nghe |
Câu con được kế thừa audio ở ngữ liệu chung. Ví dụ, Question Set có một audio và bốn câu con thì cả chùm được chọn vào nguồn Nghe, dù từng câu con không đính kèm file riêng.
Ảnh, bảng hoặc tài liệu đính kèm vẫn được sử dụng như ngữ liệu thông thường.
### 6.3. Luồng và thông tin cấu hình
1. Quản trị viên chọn **H-SCA → Tiếng Anh**, chọn phạm vi **Nghe**, **Đọc** hoặc **Nghe và Đọc**.
2. Hệ thống tạo các phần tương ứng. Phần Nghe kế thừa bộ lọc **Có audio**, phần Đọc kế thừa bộ lọc **Không có audio**.
3. Quản trị viên mở từng phần để thêm hoặc chỉnh các dòng lấy câu.
4. Hệ thống tính tổng số câu, tỷ lệ, số còn thiếu hoặc vượt.
5. Quản trị viên nhập thời lượng, kiểm tra nguồn và sinh thử.
| Trường trên dòng cấu trúc | Yêu cầu |
|---|---|
| **Tên dòng, thứ tự** | Điền sẵn theo mẫu, cho phép chỉnh sửa |
| **Nguồn câu hỏi** | Chọn nguồn Tiếng Anh. Điều kiện audio được kế thừa từ phần đang thiết lập |
| **Cách lấy** | Chọn câu đơn hoặc Question Set |
| **Số lượng** | Câu đơn nhập số câu. Question Set nhập số chùm và số câu con mỗi chùm |
| **Tổng câu, tỷ lệ** | Hiển thị đồng thời. Khi nhập tỷ lệ, quy đổi thành số câu theo tổng của phần |
| **MAPID** | Tùy chọn. Chọn nhiều mã để lọc nguồn hoặc mở phân bổ khi đã có mapping |
| **Lớp, độ khó** | Tắt mặc định theo dữ liệu mẫu. Cho phép bật bằng ma trận chung khi ngân hàng có dữ liệu phân loại |
Với Question Set, hệ thống tính **Tổng câu = Số chùm × Số câu con**. Nếu chỉ tiêu nhập bằng tỷ lệ không ghép được thành chùm nguyên, hệ thống báo ngay tại dòng. Ví dụ, yêu cầu 10 câu với kích thước cố định 4 câu/chùm cần được điều chỉnh trước khi sinh đề.
### 6.4. Thiết lập phần Nghe
Mẫu mặc định có **35 câu**, tất cả các dòng lấy từ nguồn **Có audio**.
| Thứ tự | Cách lấy | Số đơn vị/chùm | Câu mỗi đơn vị/chùm | Tổng câu | Tỷ lệ trên 35 câu |
|---|---|---|---|---|---|
| 1 | Câu đơn có audio hoặc Question Set có 1 câu con | 8 | 1 | 8 | 22,9% |
| 2 | Question Set có audio | 3 | 4 | 12 | 34,3% |
| 3 | Question Set có audio | 3 | 5 | 15 | 42,9% |
| **Tổng** | | **14** | | **35** | **100%** |
Quản trị viên sử dụng cùng thao tác thiết lập cho cả ba dòng. Hệ thống chọn ngẫu nhiên các đơn vị phù hợp, giữ thứ tự dòng và giữ câu con cùng chùm liền nhau. Mỗi câu và đơn vị ngữ liệu chỉ được lấy một lần trong đề.
**Số đơn vị/chùm là số ngữ liệu được chọn.** Một ngữ liệu có thể chứa nhiều file audio. Tỷ lệ luôn tính theo số câu được chấm.
Với dữ liệu hiện tại, điều kiện được kiểm tra là **có audio và đúng cấu trúc 8 × 1, 3 × 4, 3 × 5**. Khi đối tác bổ sung phân loại thông báo, hội thoại hoặc bài giảng, quản trị viên có thể gắn thêm điều kiện nguồn vào từng dòng.
### 6.5. Thiết lập phần Đọc
Mẫu mặc định tạo một dòng lấy **4 Question Set × 10 câu con = 40 câu**.
| Thông tin | Giá trị mặc định |
|---|---|
| Nguồn | Tiếng Anh, không có audio trong ngữ liệu chung hoặc nội dung câu con |
| Cách lấy | Question Set |
| Số chùm | 4 |
| Số câu con mỗi chùm | 10 |
| Tổng số câu | 40 |
| Tỷ lệ mỗi chùm | 25% |
| Dạng trả lời | Trắc nghiệm chọn một trong bốn phương án |
| MAPID | Tùy chọn |
Hệ thống chọn bốn ngữ liệu khác nhau và lấy nguyên các câu con. Các liên kết đến đoạn văn, câu gạch chân hoặc vị trí chèn phải được bảo toàn.
Nếu muốn đặt điều kiện riêng cho từng bài đọc, quản trị viên tách dòng tổng thành **bốn dòng, mỗi dòng 1 chùm × 10 câu**. Sau đó chọn nguồn hoặc MAPID riêng tại từng dòng. Khi chưa cần phân loại sâu, có thể sử dụng trực tiếp cấu trúc 4 × 10.
## 7. Quy tắc chấm điểm
| Dạng câu | Yêu cầu xử lý |
|---|---|
| Chọn một đáp án | So khớp lựa chọn với đáp án của phiên bản câu đã giao. Các câu con trong chùm được chấm độc lập |
| Chọn nhiều đáp án | File nêu hai trường hợp: chọn duy nhất một phương án đúng được **nửa điểm của câu** ; chọn đủ và chính xác toàn bộ đáp án đúng được **điểm tối đa** . Chuyên môn cần xác nhận thêm cách chấm khi chọn kèm phương án sai hoặc chọn nhiều phương án đúng nhưng vẫn còn thiếu |
| Điền đáp án | Đối chiếu với đáp án chuẩn hoặc các cách viết được chấp nhận trong ngân hàng. Hiển thị đơn vị, yêu cầu làm tròn và cách nhập chuỗi ký hiệu ngay tại câu hỏi |
| Điểm và band | Sử dụng điểm, band và quy tắc đã xác nhận cho đúng phạm vi bài. File dùng **0,25 điểm/câu** làm quy ước thống kê cho bài 40 câu. Nếu áp dụng cho phòng luyện, một câu được nửa điểm sẽ nhận **0,125** . Cộng điểm trước, làm tròn ở kết quả cuối |
| Ngữ văn và Tiếng Anh V1 | Hiển thị kết quả phần đã làm. Chỉ dùng band riêng khi đã có cấu hình được duyệt cho chính phần đó |
Bảng kết quả cần có trạng thái **Đúng một phần**. Nếu học viên chọn được một phương án đúng và nhận nửa điểm, việc xếp câu đó vào nhóm “Sai” sẽ khiến số liệu kết quả và phân tích điểm yếu thiếu chính xác.
## 8. Luồng làm bài và xem kết quả
Học viên mở bài đã mua trên web/app, chọn môn hoặc bài thành phần trong bộ. Màn chuẩn bị hiển thị số câu, phạm vi, thời lượng, định dạng đáp án và chính sách nghe nếu có.
Khi bắt đầu, máy chủ kiểm tra quyền, ghép đủ đề rồi mới mở lượt và chạy đồng hồ. Hệ thống lưu cấu trúc, phiên bản câu, đáp án, ngữ liệu, thứ tự và quy tắc chấm. Khi học viên quay lại, hệ thống khôi phục đúng đề, thời gian và đáp án đã lưu. Trên điện thoại, câu Hóa học có đáp án là ion hoặc chuỗi ký hiệu phải cho nhập được các ký tự tương ứng.
Sau khi nộp bài hoặc hết giờ, kết quả hiển thị trực tiếp trên web/app. Phân tích mạnh/yếu và gợi ý ôn tập dùng MAPID đã xác nhận tại thời điểm giao đề. Câu chưa có mapping vẫn được chấm và tính điểm, chỉ chưa đóng góp vào thống kê chủ đề.
## 9. Kiểm tra, sinh thử và xuất bản
| Hạng mục | Điều kiện |
|---|---|
| Kiểm tra cấu trúc | Đối chiếu đúng phạm vi, số phần, số câu và thời lượng. Với bài FULL 40 câu, phải đủ bốn dòng **20 + 5 + 5 + 10** |
| Kiểm tra nguồn | Kiểm tra đồng thời câu đơn/chùm, kích thước chùm, đáp án, lớp, độ khó, MAPID bắt buộc và tài nguyên liên quan |
| Sinh thử | Dùng cùng bộ sinh với lượt thật. Khi lỗi, chỉ đúng vị trí, ví dụ **Tiếng Anh → Nghe → Part 2 · Cần 3 hội thoại × 4 câu · Nguồn hiện ghép được 2** |
| Chấm thử | Kiểm tra trường hợp đạt toàn điểm, đạt một phần, sai, bỏ trống, đáp án có đơn vị và chuỗi ký hiệu. Kiểm tra nghe một lần trên web/app |
| Xuất bản | Cho mở bán khi phạm vi thực hiện đã đủ nguồn và đủ quy tắc chấm. Nếu chỉ phần Đọc hiểu Ngữ văn đạt điều kiện, có thể mở bán riêng phần đó |
| Cập nhật | Cấu hình mới áp dụng cho lượt phù hợp tiếp theo. Lịch sử và kết quả cũ giữ phiên bản đề, đáp án và band đã dùng |
