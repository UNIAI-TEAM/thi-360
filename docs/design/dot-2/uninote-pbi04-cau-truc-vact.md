# Thiết lập cấu trúc và ma trận bài thi V-ACT

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-17 16:20, lấy về ngày 2026-11-18. Nguồn: uniworkos, đính kèm của PBI-04 UNI-1633 (doc `01M7M9949WMCV57HW8Y65RGWQV`). Chỉ đọc; bản gốc ở uniworkos.

## 1. Phạm vi thiết lập

Tại **Quản lý bài thi → Tạo bài thi → V-ACT**, quản trị viên nhập mã, tên bài và chọn phạm vi. Hệ thống hỗ trợ các lựa chọn sau:
| Lựa chọn | Nội dung hệ thống khởi tạo |
|---|---|
| **Bài thi FULL** | Toàn bộ 5 tiểu mục, 120 câu, một đồng hồ chung 150 phút. Học viên làm đủ các phần trong cùng một lượt. |
| **Bài luyện theo môn hoặc tiểu mục** | Ví dụ, Tiếng Anh 30 câu hoặc Logic và phân tích số liệu 12 câu. Chỉ cần thiết lập nguồn của tiểu mục đó. |
| **Ôn theo chủ đề** | Chọn tiểu mục, một hoặc nhiều MAPID, số câu và cách lấy câu đơn hoặc chùm. |
Thời lượng **150 phút** được điền sẵn cho bài FULL. Tài liệu không quy định thời gian chuẩn riêng từng tiểu mục, nên khi tạo bài luyện riêng, quản trị viên xác nhận thời lượng trước khi xuất bản.

## 2. Cấu trúc mặc định của bài FULL
| Phần học viên làm | Tiểu mục thiết lập | Dải câu | Số câu | Thành phần kết quả |
|---|---|---|---|---|
| 1. Sử dụng ngôn ngữ | Tiếng Việt | 1–30 | 30 | Tiếng Việt |
| 1. Sử dụng ngôn ngữ | Tiếng Anh | 31–60 | 30 | Tiếng Anh |
| 2. Toán học | Toán học | 61–90 | 30 | Toán học |
| 3. Tư duy khoa học | Logic và phân tích số liệu | 91–102 | 12 | Tư duy khoa học |
| 3. Tư duy khoa học | Suy luận khoa học | 103–120 | 18 | Tư duy khoa học |
| **Tổng** | **5 tiểu mục** | **1–120** | **120** | **4 thành phần, mỗi thành phần 30 câu** |
Trong luồng làm bài, học viên thực hiện đủ 5 tiểu mục, không có bước chọn tổ hợp môn. Đồng hồ tiếp tục chạy khi chuyển phần. Cấu trúc và số câu từng tiểu mục được đối chiếu với thông tin của ĐHQG-HCM.

## 3. Thiết lập số câu và tỷ lệ phân bổ trong ma trận

### 3.1. Xác định số câu mục tiêu

Sau khi quản trị viên chọn loại bài thi, hệ thống khởi tạo các phần và tiểu mục tương ứng. Mỗi tiểu mục hiển thị **số câu mục tiêu**, **tỷ lệ trong phần** và **tỷ lệ trong toàn bài**.

Đối với bài FULL V-ACT, hệ thống khởi tạo cấu trúc 120 câu, thời lượng 150 phút. Phân bổ 12 và 18 câu trong phần Tư duy khoa học lấy theo đề nguồn khách hàng cung cấp.
| Phần | Tiểu mục thiết lập | Số câu mục tiêu | Tỷ lệ trong phần | Tỷ lệ trong bài FULL |
|---|---|---|---|---|
| Sử dụng ngôn ngữ · 60 câu | Tiếng Việt | 30 | 50% | 25% |
| Sử dụng ngôn ngữ · 60 câu | Tiếng Anh | 30 | 50% | 25% |
| Toán học · 30 câu | Toán học | 30 | 100% | 25% |
| Tư duy khoa học · 30 câu | Logic và phân tích số liệu | 12 | 40% | 10% |
| Tư duy khoa học · 30 câu | Suy luận khoa học | 18 | 60% | 15% |
| **Toàn bài** |  | **120** |  | **100%** |
Với **bài FULL**, hệ thống giữ số câu của từng tiểu mục theo cấu trúc ở mục 2. Quản trị viên được thay đổi cách phân bổ bên trong tiểu mục. Với **bài theo phần, theo môn hoặc theo chủ đề**, quản trị viên được nhập số câu và thời lượng riêng.

Các bảng chi tiết tại mục 4–6 là **ma trận tham khảo từ đề VETA EDU – Đợt 2**. Khi chọn **Dùng ma trận tham khảo**, hệ thống điền sẵn số câu để quản trị viên có điểm bắt đầu. Tỷ lệ khối lớp, độ khó và MAPID trong mẫu có thể điều chỉnh theo mục tiêu của bài luyện.

### 3.2. Luồng thiết lập một tiểu mục
| Bước | Thao tác của quản trị viên | Hệ thống hiển thị hoặc xử lý |
|---|---|---|
| 1. Chọn tiểu mục | Chọn **Thiết lập** tại Tiếng Việt, Tiếng Anh, Toán học, Logic và phân tích số liệu hoặc Suy luận khoa học. | Hiển thị phạm vi và mẫu số đang cấu hình, ví dụ **Toán học · 30 câu** . |
| 2. Khai báo cách lấy câu | Chọn câu đơn, chùm hoặc kết hợp. Khai báo các khối dạng bài theo thứ tự nếu tiểu mục yêu cầu. | Hiển thị số câu đơn, số câu con trong chùm và tổng số câu đã phân bổ. |
| 3. Chọn tiêu chí | Chọn độ khó, MAPID hoặc khối lớp nếu tiểu mục có phân lớp. Với mỗi bảng, chọn **Áp dụng khi sinh đề** hoặc **Chỉ đối chiếu** . | Chỉ tiêu được áp dụng trở thành điều kiện bắt buộc khi lấy câu. Chỉ tiêu đối chiếu xuất hiện trong báo cáo sinh thử để chuyên môn xem chênh lệch. |
| 4. Nhập phân bổ | Chọn **Nhập theo số câu** hoặc **Nhập theo tỷ lệ (%)** cho từng bảng. | Hiển thị hai cột **Số câu** và **Tỷ lệ** cạnh nhau. Khi sửa một cột, hệ thống tính lại cột kia và báo còn thiếu hoặc vượt bao nhiêu câu. |
| 5. Kiểm tra nguồn | Chọn **Kiểm tra ma trận** . | Kiểm tra ngân hàng có thể ghép được một đề thỏa đồng thời số câu, dạng bài, chùm và các tiêu chí đã bật. |
| 6. Xác nhận | Xem các chỉ tiêu đã quy đổi, sửa dòng có lỗi và lưu. | Lưu số câu nguyên đã xác nhận làm căn cứ cho sinh đề thử và sinh đề thật. |
Khi nạp mẫu, hệ thống áp dụng các dòng cấu trúc câu. Các bảng chuyên môn về lớp, độ khó và MAPID được điền sẵn ở chế độ **Chỉ đối chiếu**. Quản trị viên có thể bật riêng từng bảng. Ví dụ, có thể bắt buộc đúng tỷ lệ lớp của môn Toán nhưng chỉ dùng độ khó để theo dõi.

### 3.3. Quy tắc nhập số câu và tỷ lệ
| Tình huống | Quy tắc |
|---|---|
| Nhập theo số câu | Nhập số nguyên từ 0 trở lên. Hệ thống tính tỷ lệ bằng số câu của dòng chia cho số câu mục tiêu của bảng. Ví dụ, nhập 12 câu trên tổng 30 câu thì hiển thị 40%. |
| Nhập theo tỷ lệ | Nhập tỷ lệ cho từng dòng. Với bảng phân bổ toàn bộ số câu, tổng tỷ lệ yêu cầu bằng 100%. Hệ thống hiển thị số câu nguyên tương ứng trước khi xác nhận. |
| Tỷ lệ quy đổi ra số lẻ | Lấy phần nguyên của từng dòng trước. Các câu còn dư lần lượt được phân cho dòng có phần thập phân lớn nhất. Nếu bằng nhau, ưu tiên dòng đứng trước trong bảng. |
| Chỉ cần ấn định một số MAPID | Cho phép dùng dòng **Phần còn lại** . Ví dụ, đặt Xác suất bằng 10% trên tổng 30 câu thì hệ thống hiển thị **Xác suất 3 câu · Phần còn lại 27 câu** . |
| Chỉ cần giới hạn nguồn theo MAPID | Quản trị viên chọn một hoặc nhiều MAPID và để trống phân bổ. Hệ thống lấy ngẫu nhiên trong các câu thuộc ít nhất một mã đã chọn. |
| Thay đổi tổng số câu của bài luyện | Bảng nhập theo tỷ lệ sẽ tính lại số câu và hiển thị phương án mới để xác nhận. Bảng nhập theo số câu sẽ giữ số đã nhập và báo phần thiếu hoặc vượt. |
| Một câu có nhiều thuộc tính | Một câu có thể đồng thời đáp ứng một chỉ tiêu lớp, một chỉ tiêu độ khó và một chỉ tiêu MAPID. Trong cùng bảng phân bổ MAPID, một câu chỉ được tính vào một dòng dù có nhiều mã phù hợp. |
**Ví dụ quy đổi tỷ lệ.** Quản trị viên thiết lập 30 câu Toán, nhập tỷ lệ Nhận biết / Thông hiểu / Vận dụng là **25% / 50% / 25%**.
| Độ khó | Tỷ lệ nhập | Số câu trước làm tròn | Số câu sẽ lấy | Tỷ lệ thực lấy |
|---|---|---|---|---|
| Nhận biết | 25% | 7,5 | 8 | 26,7% |
| Thông hiểu | 50% | 15 | 15 | 50% |
| Vận dụng | 25% | 7,5 | 7 | 23,3% |
| **Tổng** | **100%** | **30** | **30** | **100%** |
Hệ thống hiển thị tỷ lệ đến một chữ số thập phân. Các bảng tham khảo được khởi tạo từ **số câu gốc**. Ví dụ, sáu dòng cùng có 3/18 câu đều hiển thị 16,7%, nhưng tổng vẫn là 18/18 câu. Hệ thống dùng dữ liệu gốc khi kiểm tra tổng hoặc khi quản trị viên chuyển đổi giữa hai cách nhập.

### 3.4. Quy tắc đối với câu chùm và tiến độ cấu hình

- **Đơn vị phân bổ là câu được chấm.** Một ngữ liệu có 3 câu con đóng góp 3 câu vào ma trận. Ngữ liệu không cộng thêm một câu.

- **Lấy nguyên chùm.** Nếu một dòng chỉ nhận chùm 3 câu và quản trị viên đặt mục tiêu 5 câu, hệ thống báo dòng đó không thể ghép đủ. Quản trị viên có thể sửa số câu hoặc cấu hình lại kích thước chùm.

- **Phân biệt đủ số lượng với đủ nguồn.** Ví dụ, nhập 12 câu đơn và 15 câu con cho môn Toán thì hiển thị **Đã phân bổ 27/30 · Còn thiếu 3 câu**. Khi sửa thành 12 + 18, hiển thị **Đã phân bổ 30/30**. Sau khi kiểm tra ghép thành công, hệ thống mới hiển thị **Đủ nguồn theo ma trận**.

- **Mẫu số thay đổi theo nơi đang chỉnh.** Khi sửa độ khó của toàn môn Toán, tỷ lệ tính trên 30 câu. Khi mở riêng dòng *Ứng dụng đạo hàm, hàm số* có 5 câu, tỷ lệ độ khó trong dòng đó tính trên 5 câu.

## 4. Thiết lập phần Sử dụng ngôn ngữ

### 4.1. Tiếng Việt

Tiểu mục Tiếng Việt có **30 câu**. Quản trị viên thiết lập theo **mạch kiến thức, MAPID và độ khó**. Khối lớp là thông tin tùy chọn.
| Mạch kiến thức | Số câu | Tỷ lệ trên 30 câu | Nhận biết | Thông hiểu | Vận dụng |
|---|---|---|---|---|---|
| Chính tả, từ vựng và ngữ nghĩa | 7 | 23,3% | 5 | 2 | 0 |
| Thể loại, thi pháp và lí luận văn học | 12 | 40% | 1 | 7 | 4 |
| Ngữ pháp, liên kết và logic diễn đạt | 3 | 10% | 0 | 3 | 0 |
| Đọc hiểu văn bản, nội dung và lập luận | 8 | 26,7% | 0 | 5 | 3 |
| **Tổng** | **30** | **100%** | **6** | **17** | **7** |
Quản trị viên chọn mạch, gắn một hoặc nhiều MAPID, sau đó nhập số câu hoặc tỷ lệ. Nếu cần kiểm soát sâu hơn, quản trị viên mở dòng để khai báo độ khó. Ví dụ, trong 7 câu *Chính tả, từ vựng và ngữ nghĩa*, mẫu lấy 5 câu Nhận biết và 2 câu Thông hiểu. Nếu đồng thời bật độ khó toàn tiểu mục, hệ thống kiểm tra tổng theo cột của các mạch có khớp chỉ tiêu toàn tiểu mục hay không.

**Cấu hình mạch kiến thức và cấu hình ngữ liệu được khai báo riêng.** Một văn bản có thể chứa câu từ vựng, câu ngữ pháp và câu đọc hiểu. Do đó, yêu cầu 8 câu mang MAPID *Đọc hiểu* không đồng nghĩa ngân hàng phải có một văn bản chứa đúng 8 câu. Quản trị viên chỉ định số chùm hoặc kích thước chùm khi thực sự muốn kiểm soát cấu trúc ngữ liệu.

### 4.2. Tiếng Anh

Tiểu mục Tiếng Anh có **30 câu**. Khi dùng mẫu, hệ thống khởi tạo năm khối theo thứ tự. Quản trị viên thiết lập số lượng cho từng khối và có thể chọn thêm MAPID để giới hạn nguồn.
| Thứ tự | Dạng bài | Cách lấy mặc định | Số câu | Tỷ lệ trên 30 câu | Nhận biết | Thông hiểu | Vận dụng |
|---|---|---|---|---|---|---|---|
| 1 | Điền khuyết | Chọn phương án điền khuyết | 5 | 16,7% | 3 | 2 | 0 |
| 2 | Phát hiện lỗi sai | Chọn phương án chứa lỗi | 5 | 16,7% | 0 | 5 | 0 |
| 3 | Diễn đạt tương đương | Chọn cách diễn đạt tương đương | 5 | 16,7% | 0 | 1 | 4 |
| 4 | Đọc hiểu – bài đọc 1 | 1 ngữ liệu × 7 câu con | 7 | 23,3% | 1 | 4 | 2 |
| 5 | Đọc hiểu – bài đọc 2 | 1 ngữ liệu × 8 câu con | 8 | 26,7% | 2 | 4 | 2 |
|  | **Tổng** |  | **30** | **100%** | **6** | **16** | **8** |
Hai khối đọc hiểu chiếm **15/30 câu, tương ứng 50%**. Nếu quản trị viên đổi hai khối thành 6 và 9 câu, hệ thống vẫn ghi nhận đủ 50% đọc hiểu nhưng phải kiểm tra được **một ngữ liệu có 6 câu con và một ngữ liệu khác có 9 câu con**. Khi sinh đề, hệ thống chọn ngẫu nhiên trong từng khối, giữ thứ tự năm khối và giữ các câu của cùng ngữ liệu liền nhau.

## 5. Thiết lập phần Toán học bằng ma trận dùng chung

### 5.1. Khởi tạo cấu hình môn Toán

Tại **Cấu trúc và ma trận → Toán học → Thiết lập**, hệ thống mở cùng luồng cấu hình môn đã dùng cho các kỳ thi trước. Quản trị viên thực hiện theo bốn bước.
| Bước | Quản trị viên thực hiện | Giá trị khởi tạo cho Toán V-ACT |
|---|---|---|
| **1. Chọn nguồn** | Chọn nguồn câu hỏi theo kỳ thi, phần/môn và dạng trả lời. Có thể giới hạn thêm bằng MAPID. | Nguồn Toán học dùng cho V-ACT, dạng trắc nghiệm chọn một đáp án. |
| **2. Thiết lập cấu trúc câu** | Khai báo từng dòng câu đơn hoặc chùm, số lượng và thứ tự. | Bài FULL có 30 câu. Dòng 1 lấy 12 câu đơn. Dòng 2 lấy 18 câu con từ các chùm dữ kiện. |
| **3. Thiết lập phân bổ** | Chọn **Thêm tiêu chí** → chọn **Khối lớp** , **Độ khó** hoặc **MAPID** → nhập số câu hoặc tỷ lệ. | Điền sẵn phân bổ lớp và độ khó từ đề tham khảo. MAPID có thể để trống. |
| **4. Kiểm tra và sinh thử** | Kiểm tra tổng câu, khả năng ghép nguồn và xem đề được sinh. | Hiển thị số câu yêu cầu, số câu thực lấy và chênh lệch theo từng tiêu chí đã bật. |
Khi tạo bài cho kỳ thi nào, hệ thống nạp cấu hình của kỳ thi đó. Ví dụ, quản trị viên đã biết cách thiết lập Toán HSA có thể thao tác tương tự cho V-ACT, nhưng khi mở V-ACT sẽ thấy mục tiêu **30 câu** và các dạng trả lời phù hợp với V-ACT.

### 5.2. Khai báo số câu, khối lớp và độ khó

Ba bảng dưới đây cùng áp dụng trên **một tập 30 câu Toán**. Quản trị viên chọn nhập theo **Số câu** hoặc **Tỷ lệ (%)** cho từng bảng. Hệ thống tự quy đổi và hiển thị cột còn lại theo quy tắc tại mục 3.

**a. Cấu trúc câu**
| Cách lấy câu | Số câu mặc định | Tỷ lệ trên 30 câu |
|---|---|---|
| Câu đơn | 12 | 40% |
| Câu con trong chùm dữ kiện | 18 | 60% |
| **Tổng** | **30** | **100%** |
Với dòng thứ hai, quản trị viên chỉ cần nhập **18 câu con** nếu không có yêu cầu về số chùm. Hệ thống tự chọn các chùm nguyên có tổng bằng 18. Nếu chuyên môn yêu cầu số chùm hoặc kích thước cụ thể, quản trị viên khai báo thêm tại dòng này.

**b. Phân bổ theo khối lớp**
| Chỉ tiêu | Lớp 10 | Lớp 11 | Lớp 12 | Liên lớp |
|---|---|---|---|---|
| Số câu tham khảo | 12 | 9 | 8 | 1 |
| Tỷ lệ trên 30 câu | 40% | 30% | 26,7% | 3,3% |
**c. Phân bổ theo độ khó**
| Chỉ tiêu | Nhận biết | Thông hiểu | Vận dụng |
|---|---|---|---|
| Số câu tham khảo | 4 | 10 | 16 |
| Tỷ lệ trên 30 câu | 13,3% | 33,3% | 53,3% |
Theo quy tắc tại mục 3, hai bảng **Khối lớp** và **Độ khó** được điền sẵn ở chế độ **Chỉ đối chiếu**. Quản trị viên chọn **Áp dụng khi sinh đề** cho bảng cần kiểm soát. Có thể bật riêng từng bảng hoặc bật đồng thời.

Khối lớp và độ khó được tính cho từng câu đơn hoặc từng câu con. Một câu con lớp 11, mức Vận dụng, đồng thời đóng góp **một vị trí** vào chỉ tiêu câu chùm, lớp 11 và Vận dụng. Riêng dòng **Liên lớp** cần có câu đã được phân loại tương ứng trong ngân hàng. Câu chưa khai báo lớp không tự động được đưa vào dòng này.

### 5.3. Thiết lập MAPID theo nhu cầu

MAPID là trường chọn nhiều. Quản trị viên sử dụng theo một trong ba nhu cầu sau.
| Nhu cầu | Cách khai báo | Kết quả khi sinh đề |
|---|---|---|
| **Chỉ cần phân bổ theo lớp, độ khó** | Để trống MAPID. | Lấy ngẫu nhiên từ nguồn Toán đã chọn, thỏa các chỉ tiêu lớp và độ khó đang áp dụng. |
| **Chỉ lấy câu trong một phạm vi kiến thức** | Tại **Lọc nguồn** , chọn một hoặc nhiều MAPID. | Mọi câu được lấy phải thuộc ít nhất một mã đã chọn. Quản trị viên không cần chia tỷ lệ giữa các mã. |
| **Cần đúng số câu theo mạch kiến thức** | Chọn **Phân bổ theo MAPID** → thêm dòng → chọn một hoặc nhiều mã cho dòng → nhập số câu hoặc tỷ lệ. | Ví dụ, dòng *Xác suất* lấy 3/30 câu, tương ứng 10%. Dòng **Phần còn lại** nhận 27 câu. |
Một câu có nhiều MAPID chỉ chiếm một vị trí trong đề và một vị trí trong cùng bảng hạn ngạch kiến thức. Khi đồng thời bật lớp, độ khó và MAPID, bộ sinh phải tìm được **cùng một tập câu** thỏa cả ba tiêu chí.

Đối với bài luyện giới hạn toàn bộ nguồn theo một chủ đề, hệ thống kiểm tra MAPID trên từng câu con. Ví dụ, một chùm có 2 câu Xác suất và 1 câu Hình học sẽ không phù hợp với bài yêu cầu cả 3 câu đều thuộc Xác suất và phải lấy nguyên chùm.

### 5.4. Bổ sung mapping khi đối tác hoàn tất thống kê

Hệ thống cho phép hoàn thiện cấu trúc đề trước khi có danh sách MAPID chi tiết. Đối tác có thể bàn giao bảng đối chiếu gồm **bộ nguồn, phần/môn, mã nguồn của đối tác, tên mạch kiến thức và MAPID tương ứng trong ngân hàng**. Một mạch như *Xác suất* có thể tương ứng với nhiều mã. Bảng đối chiếu đã xác nhận được dùng lại cho các đề cùng nguồn.
| Trạng thái cấu hình | Hệ thống xử lý |
|---|---|
| Ma trận chỉ áp dụng lớp và độ khó | Cho kiểm tra, sinh thử và xuất bản khi đủ nguồn theo các tiêu chí đã bật. |
| Đã dự kiến một dòng kiến thức nhưng chưa gắn mã | Cho lưu nháp, ví dụ **Xác suất · 3 câu · Chờ mapping** . Nếu dòng chỉ dùng để đối chiếu, hiển thị **Chưa có dữ liệu đối chiếu** . |
| Dòng chưa gắn mã đang được chọn làm điều kiện bắt buộc | Chỉ rõ dòng thiếu mapping. Yêu cầu gắn mã trước khi sinh đề theo điều kiện đó để tránh trường hợp hệ thống báo đủ 3 câu Xác suất nhưng thực tế lấy ngẫu nhiên 3 câu Toán. |
| Đã nhận mapping | Gắn mã vào dòng đã có, kiểm tra lại nguồn và sinh thử. Quản trị viên giữ được cấu trúc câu, số lượng và các bảng lớp, độ khó đã khai báo. |
## 6. Thiết lập phần Tư duy khoa học bằng cấu trúc chùm dùng chung

### 6.1. Khởi tạo tiểu mục

Phần Tư duy khoa học tái sử dụng dòng **Chùm dùng chung dữ kiện/ngữ liệu**. Khi mở phần này, hệ thống khởi tạo hai tiểu mục.
| Tiểu mục | Số câu | Tỷ lệ trong phần | Cấu trúc tham khảo | Nhận biết | Thông hiểu | Vận dụng |
|---|---|---|---|---|---|---|
| Logic và phân tích số liệu | 12 | 40% | 4 chùm × 3 câu | 1 · 8,3% | 4 · 33,3% | 7 · 58,3% |
| Suy luận khoa học | 18 | 60% | 6 chùm × 3 câu | 4 · 22,2% | 9 · 50% | 5 · 27,8% |
Tỷ lệ độ khó tính trên **12 câu** hoặc **18 câu** của từng dòng. Hai tiểu mục dùng cùng các trường **Nguồn, Số chùm, Số câu con, Độ khó và MAPID**. Bảng khối lớp được ẩn mặc định. Trong bài FULL, học viên làm cả hai tiểu mục.

### 6.2. Chọn mức chi tiết cần kiểm soát
| Nhu cầu của quản trị viên | Cách thiết lập |
|---|---|
| **Chỉ cần đủ số chùm** | Giữ một dòng cho mỗi tiểu mục. Ví dụ, Logic lấy 4 chùm hợp lệ trong nguồn Logic, mỗi chùm 3 câu. |
| **Cần đủ từng loại dữ kiện** | Tách 12 câu Logic thành bốn dòng, ví dụ *Sắp xếp điều kiện, Xếp hạng, Bảng số liệu, Biểu đồ* . Mỗi dòng lấy 1 chùm × 3 câu, tương ứng 25% của tiểu mục. |
| **Cần phân bổ theo lĩnh vực tư liệu** | Có thể tách 18 câu Suy luận thành sáu dòng theo đề tham khảo: Hóa học, Vật lí, Sinh học, Địa lí, Lịch sử và Kinh tế. Mỗi dòng lấy 1 chùm × 3 câu. Quản trị viên được thay đổi tên, số dòng và phân bổ. |
| **Cần giới hạn đến chủ đề cụ thể** | Gắn thêm MAPID vào dòng. Ví dụ, dòng Hóa học có thể lấy tư liệu Hóa học nói chung hoặc giới hạn vào chủ đề Polymer sau khi đã xác nhận mapping. |
Khi tách dòng tổng thành các dòng chi tiết, các dòng mới **chia nhau số câu mục tiêu ban đầu**. Bốn dòng Logic × 3 câu thay thế dòng tổng 12 câu.

Loại dữ kiện được kiểm tra ở **cấp chùm**. Độ khó và MAPID kiến thức được kiểm tra ở **cấp câu con**. Ví dụ, để bảo đảm lấy một chùm Biểu đồ, dòng đó phải liên kết với nguồn hoặc thuộc tính nhận diện biểu đồ. Chỉ nhập tên hiển thị “Biểu đồ” thì chưa đủ để bộ sinh chọn đúng dữ kiện.

## 7. Dùng lại ma trận cho bài theo phần, theo môn và theo chủ đề
| Phạm vi | Cách thiết lập |
|---|---|
| **Bài FULL** | Nạp đủ các tiểu mục theo mục 2. Toán và Tư duy khoa học sử dụng ma trận đã thiết lập trong cùng một lượt thi. |
| **Bài theo phần hoặc theo môn** | Chọn phần/môn, nhập số câu và thời lượng. Hệ thống nạp các bảng phân bổ tương ứng. Khi đổi tổng câu, các bảng nhập bằng tỷ lệ được quy đổi lại để quản trị viên xác nhận. |
| **Bài theo chủ đề** | Chọn phần/môn, một hoặc nhiều MAPID đã xác nhận, số câu và thời lượng. Có thể bật thêm phân bổ lớp và độ khó ngay trên tập câu của chủ đề. |
| **Bộ đề thương mại** | Chọn các bài đã xuất bản để đóng bộ. Mỗi bài giữ riêng ma trận, thời lượng, lượt làm và kết quả. |
**Ví dụ tạo bài Toán 12 câu ôn Xác suất.** Quản trị viên chọn *Toán học → Theo chủ đề → Xác suất*, đặt 12 câu đơn. Tại bảng lớp, nhập **25% / 25% / 50%**, tương ứng **3 câu lớp 10, 3 câu lớp 11, 6 câu lớp 12**. Tại bảng độ khó, nhập **25% / 50% / 25%**, tương ứng **3 Nhận biết, 6 Thông hiểu, 3 Vận dụng**. Bộ sinh phải ghép được cùng 12 câu Xác suất thỏa cả hai bảng. Nếu hiện tại chỉ cần bài Toán tổng hợp, quản trị viên chọn **Theo môn** và có thể thiết lập ngay bằng lớp và độ khó.

## 8. Chấm và hiển thị kết quả theo cấu trúc đã giao

Sau khi học viên nộp bài hoặc hết giờ, hệ thống chấm từng câu đơn và từng câu con theo đáp án đã lưu cho lượt đó. Web/app hiển thị số đúng, sai, bỏ trống, thời lượng và tỷ lệ đúng trên toàn bộ số câu của phạm vi đã làm.

Bài FULL hiển thị bốn thành phần **Tiếng Việt, Tiếng Anh, Toán học và Tư duy khoa học**. Học viên có thể mở phần Tư duy khoa học để xem riêng hai tiểu mục 12 và 18 câu. Với bài theo môn hoặc chủ đề, kết quả sử dụng đúng mẫu số của bài đó. V1 công bố số đúng và tỷ lệ đúng. Việc quy đổi sang thang điểm IRT chính thức cần dữ liệu hiệu chuẩn riêng.

Kết quả theo kiến thức dùng MAPID đã xác nhận tại thời điểm giao đề. Ví dụ, nếu **8/12 câu có MAPID**, hệ thống vẫn chấm đủ 12 câu và ghi rõ mức bao phủ khi phân tích chủ đề. Các gợi ý ôn luyện chỉ dựa trên những câu đã có căn cứ phân loại.

## 9. Kiểm tra ma trận, sinh thử và xuất bản
| Bước | Yêu cầu hệ thống |
|---|---|
| **1. Kiểm tra số lượng** | Hiển thị **Mục tiêu / Đã phân bổ / Còn thiếu hoặc vượt** cho từng tiểu mục và từng bảng đang áp dụng. Kiểm tra số câu nguyên sau quy đổi và tổng số câu con trong chùm. |
| **2. Kiểm tra khả năng ghép đề** | Kiểm tra đồng thời nguồn, dạng trả lời, câu đơn/chùm, khối lớp, độ khó và các điều kiện MAPID đã bật. Mỗi câu chỉ xuất hiện một lần. |
| **3. Sinh đề thử** | Dùng cùng bộ sinh với lượt thật. Hiển thị **Yêu cầu / Thực tế / Chênh lệch** . Nếu thiếu nguồn, chỉ đúng dòng cần sửa, ví dụ **Logic → Biểu đồ · Cần 1 chùm có 3 câu con · Có 0 chùm phù hợp** . Lượt thử không trừ quyền học viên. |
| **4. Xuất bản và mở bán** | Cho xuất bản khi thỏa các điều kiện bắt buộc và sinh thử thành công. Quản trị viên xác nhận phạm vi, thời lượng, giá, lượt, thời hạn và voucher nội bộ nếu áp dụng. |
| **5. Sinh và khôi phục lượt thật** | Kiểm tra quyền và ghép đủ đề trước khi mở lượt, chạy đồng hồ. Lưu phiên bản ma trận, câu hỏi, thứ tự, đáp án và phân loại đã dùng. Khi học viên quay lại, khôi phục đúng đề đã giao, kể cả khi đối tác đã cập nhật mapping cho phiên bản mới. |
