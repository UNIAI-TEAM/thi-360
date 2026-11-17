# Thiết lập cấu trúc chi tiết đề BCA

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-17 11:17, lấy về ngày 2026-11-17. Nguồn: uniworkos, thư mục "Tài liệu Thi 360" (doc `01M7KT0X521G0EP584SY4BCBND`). Chỉ đọc; bản gốc ở uniworkos.

Trong bước **Thiết lập cấu trúc chi tiết**, quản trị viên cấu hình phần tự luận Ngữ văn, ba môn trắc nghiệm bắt buộc và ma trận của từng môn tự chọn. Khi học viên chọn mã **CA1, CA2, CA3 hoặc CA4**, hệ thống đọc quan hệ mã–môn từ **Global List** để xác định môn thi ở phần 3. Quản trị viên khai báo mỗi môn một lần.

Đề BCA FULL có **81 câu, 100 điểm và một đồng hồ chung 180 phút**. Trong đó, 1 câu tự luận chiếm 25 điểm. Theo phạm vi sản phẩm hiện tại, quản trị viên có thể thiết lập, lưu nháp và sinh thử cấu trúc FULL. Việc mở thi FULL có chấm điểm cần đáp ứng điều kiện xử lý phần tự luận tại mục 4. Các bài theo phần hoặc theo môn gồm toàn câu chấm tự động có thể mở độc lập.

## 1. Điểm vào và phạm vi cần cấu hình

Sau khi quản trị viên lưu bước 1 và nhấn **Tiếp tục**, hệ thống mở các mục cần thiết lập theo phạm vi của bài thi.

| Lựa chọn tại bước 1 | Nội dung hiển thị tại bước 2 |
|---|---|
| **BCA – Bài thi FULL** | Phần 1 – Tự luận Ngữ văn. Phần 2 – Trắc nghiệm bắt buộc gồm Toán, Lịch sử, Tiếng Anh. Phần 3 – Trắc nghiệm tự chọn gồm các môn được xác định từ Global List. |
| **BCA – Bài thi theo phần – Tự luận Ngữ văn** | Mở cấu hình lấy 1 đề nghị luận xã hội, kèm ngữ liệu, yêu cầu viết và điểm tối đa. |
| **BCA – Bài thi theo phần – Trắc nghiệm bắt buộc** | Chỉ cấu hình Toán, Lịch sử và Tiếng Anh. Theo mẫu BCA, phần này có 65 câu, 60 điểm. |
| **BCA – Bài thi theo phần – Trắc nghiệm tự chọn** | Hiển thị các môn ứng với mã BCA trong Global List. Học viên chọn một mã và làm 15 câu của môn tương ứng. |
| **BCA – Bài thi theo môn** | Mở trực tiếp môn đã chọn. Có thể dùng cấu trúc môn BCA hoặc thiết lập bài ôn theo MAPID. |

**Cấu trúc mặc định của bài FULL**

| Phần | Môn | Vị trí trong đề | Số câu | Điểm tối đa |
|---|---|---|---|---|
| 1. Tự luận bắt buộc | Ngữ văn | Phần I – Tự luận | 1 | 25 |
| 2. Trắc nghiệm bắt buộc | Toán | Câu 1–35 | 35 | 35 |
| 2. Trắc nghiệm bắt buộc | Lịch sử | Câu 36–45 | 10 | 10 |
| 2. Trắc nghiệm bắt buộc | Tiếng Anh | Câu 46–65 | 20 | 15 |
| 3. Trắc nghiệm tự chọn | Một môn theo mã CA1–CA4 | Câu 66–80 | 15 | 15 |
| **Toàn bài** |  | **1 câu tự luận và 80 câu trắc nghiệm** | **81** | **100** |

Câu tự luận được hiển thị riêng ở Phần I. Dải số **1–80** dành cho các câu trắc nghiệm. Thời lượng **180 phút** được khai báo ở cấp toàn bài. Khi tạo bài theo phần hoặc bài theo môn độc lập, quản trị viên sử dụng thời lượng đã đặt tại bước 1.

## 2. Tổ chức màn hình và điều hướng

| Khu vực | Nội dung hiển thị | Thao tác |
|---|---|---|
| **Thông tin bài thi** | Tên bài, kỳ thi BCA, phạm vi thi, số câu, tổng điểm và thời lượng. Với FULL, hiển thị **180 phút dùng chung**. | Chọn **Sửa thông tin tổng quan** để quay lại bước 1. |
| **Danh sách phần/môn** | Ba phần theo thứ tự. Mở phần 2 để chọn Toán, Lịch sử hoặc Tiếng Anh. Mở phần 3 để chọn từng môn tự chọn. | Nhấn **Thiết lập** tại môn cần chỉnh sửa. Bài theo môn mở thẳng ma trận của môn đó. |
| **Cấu hình mục đang chọn** | Ngữ văn hiển thị điều kiện lấy đề viết. Tiếng Anh hiển thị các nhóm dạng bài theo thứ tự. Các môn còn lại hiển thị số câu đơn, chùm, lớp, độ khó và MAPID. | Sửa các chỉ tiêu trong phạm vi môn đang chọn. |
| **Tổng hợp** | Số câu và điểm mục tiêu, số đã phân bổ, số thiếu hoặc vượt. Hiển thị riêng **Tự luận 25 điểm** và **Trắc nghiệm 75 điểm**. | Chọn một lỗi để mở đúng phần, môn hoặc nhóm cần sửa. |
| **Thao tác cuối bước** | **Lưu nháp**, **Kiểm tra môn**, **Sinh đề thử**, **Tiếp tục**. | Cho phép lưu nháp khi còn thiếu nguồn. Kết quả kiểm tra cấu trúc và điều kiện mở thi được hiển thị riêng. |

Khi chuyển môn mà còn thay đổi chưa lưu, hệ thống cho phép **Lưu và chuyển**, **Bỏ thay đổi** hoặc **Ở lại**. Khi mở lại bản nháp, hệ thống tải đúng dữ liệu đã lưu.

## 3. Quy tắc sử dụng mã bài thi từ Global List

### a. Quan hệ mã và môn

| Mã bài thi | Môn tự chọn | Nội dung lấy cho phần 3 |
|---|---|---|
| **CA1** | Vật lí | 15 câu Vật lí |
| **CA2** | Hóa học | 15 câu Hóa học |
| **CA3** | Sinh học | 15 câu Sinh học |
| **CA4** | Địa lí | 15 câu Địa lí |

Global List lưu kỳ thi, mã, môn tương ứng, trạng thái và phiên bản. Tại màn thiết lập bài thi, quản trị viên thấy **4 ma trận môn tự chọn** theo danh mục hiện tại. Hệ thống tự xác định mối liên hệ giữa các ma trận này với CA1–CA4.

### b. Xử lý khi sinh đề

1. Với bài FULL, hệ thống sử dụng chung cấu hình phần Ngữ văn, Toán, Lịch sử và Tiếng Anh cho cả bốn mã. Mã chỉ quyết định môn ở câu 66–80.
2. Học viên chọn một mã. Đề xuất đặt thao tác này trên **màn chuẩn bị trước khi bắt đầu tính 180 phút**, vì BCA dùng một đồng hồ cho cả bài. Mỗi lựa chọn hiển thị cả mã và tên môn, ví dụ **CA2 – Hóa học**.
3. Sau khi học viên xác nhận bắt đầu, hệ thống sinh phần bắt buộc và đúng một môn tự chọn, sau đó lưu mã, phiên bản đề, ngữ liệu, câu hỏi và thứ tự câu vào lượt thi.
4. Khi học viên tiếp tục lượt đang làm, hệ thống khôi phục đúng đề và thời gian còn lại. Với bài thi theo môn đã xác định, học viên vào làm trực tiếp môn đó.

Khi quản trị viên sinh thử để so sánh các mã trên **cùng một đề mẫu**, hệ thống giữ phần 1 và phần 2 đã sinh, chỉ thay khối câu 66–80. Thao tác **Sinh đề thử mới** có thể lấy lại toàn bộ câu theo ma trận. Nhờ đó, quản trị viên kiểm tra được chính xác phần nào thay đổi khi đổi mã.

## 4. Luồng thiết lập phần tự luận Ngữ văn

Khi mở **Phần 1 – Tự luận Ngữ văn**, quản trị viên thiết lập cách lấy **một đề nghị luận xã hội có ngữ liệu đi kèm**.

| Trường | Giá trị mặc định và quy tắc |
|---|---|
| **Môn và dạng bài** | Ngữ văn – Tự luận nghị luận xã hội. |
| **Số đề lấy** | 1 đề viết cho mỗi lượt thi. |
| **Nguồn đề** | Lấy từ ngân hàng Ngữ văn. Có thể giới hạn theo chủ đề/MAPID. |
| **Ngữ liệu** | Đoạn trích, nguồn trích dẫn và yêu cầu viết thuộc cùng một bản ghi. Khi sinh đề, hệ thống lấy nguyên bộ để yêu cầu viết luôn phù hợp với đoạn trích. |
| **Yêu cầu độ dài** | Hiển thị yêu cầu **tối thiểu 500 chữ** trong đề bài. |
| **Điểm tối đa** | 25 điểm. |

Quản trị viên chọn **Sinh thử** để kiểm tra đoạn trích, nguồn và yêu cầu viết. Chủ đề *sự kiên trì* trong đề minh họa là một ví dụ nguồn. Quản trị viên có thể sử dụng các chủ đề nghị luận xã hội khác qua MAPID.

**Điều kiện mở thi trong giai đoạn hiện tại:** đề viết có đáp án tham khảo hoặc hướng dẫn chấm vẫn cần người đánh giá bài làm của học viên. Khi cấu trúc hợp lệ nhưng chức năng chấm tự luận chưa được triển khai, hệ thống cho phép lưu và xem thử, đồng thời hiển thị khi mở thi: *“Bài thi có phần tự luận Ngữ văn 25 điểm chưa hỗ trợ chấm. Chưa thể mở bài thi FULL có kết quả trên thang 100 điểm.”* Nếu cần phát hành trước, quản trị viên tạo **Bài thi theo phần – Trắc nghiệm bắt buộc** trên thang 60 điểm hoặc **Bài thi theo phần – Trắc nghiệm tự chọn** trên thang 15 điểm.

Đối với v1, hiện tại chưa đáp ứng câu hỏi liên quan đến tự luận, tạm chưa thiết lập câu ngữ văn

## 5. Luồng thiết lập các môn trắc nghiệm bắt buộc

### a. Cấu trúc câu hỏi và thứ tự mặc định

| Môn | Vị trí | Cách lấy câu | Điểm tối đa |
|---|---|---|---|
| **Toán** | 1–27 | 27 câu đơn, mỗi câu chọn 1 trong 4 phương án | 27 |
|  | 28–30 | 1 chùm × 3 câu con trắc nghiệm, dùng chung dữ kiện | 3 |
|  | 31–35 | 5 câu trả lời ngắn, học viên nhập kết quả | 5 |
| **Lịch sử** | 36–42 | 7 câu đơn, mỗi câu chọn 1 trong 4 phương án | 7 |
|  | 43–45 | 1 chùm tư liệu × 3 câu con | 3 |
| **Tiếng Anh** | 46–47 | 2 câu ngữ âm. Mẫu minh họa gồm 1 câu phát âm và 1 câu trọng âm. | 1,5 |
|  | 48–55 | 8 câu đơn về từ vựng – ngữ pháp | 6 |
|  | 56–60 | 1 chùm đọc điền × 5 câu. Mỗi vị trí chọn phương án phù hợp. | 3,75 |
|  | 61–65 | 1 chùm đọc hiểu × 5 câu | 3,75 |
| **Cộng** | **1–65** | **65 câu** | **60** |

**Cách tính điểm để cấu hình hệ thống:** tài liệu cung cấp điểm tổng theo môn. Điểm ở bảng trên quy đổi đều theo số câu, tương ứng **1 điểm/câu Toán và Lịch sử**, **0,75 điểm/câu Tiếng Anh**. Khi lấy chùm, hệ thống tính điểm theo từng câu con. Quản trị viên cần thấy cả tổng điểm môn để phát hiện trường hợp ngân hàng đang mặc định 1 điểm cho cả 20 câu Tiếng Anh.

### b. Ma trận tham khảo theo môn

Các số dưới đây lấy từ đề minh họa. Phân bố độ khó là **ước lượng của người biên soạn tài liệu**. Trên màn thiết lập, số câu là giá trị dùng để sinh đề. Tỷ lệ phần trăm được làm tròn để dễ đọc.

| Môn | Phân bố theo **khối lớp** | Phân bố theo **độ khó** |
|---|---|---|
| **Toán – 35 câu** | Lớp 10 · 4 câu (11%) Lớp 11 · 7 câu (20%) Lớp 12 · 24 câu (69%) | Nhận biết · 2 câu (6%) Thông hiểu · 12 câu (34%) Vận dụng · 15 câu (43%) Vận dụng cao · 6 câu (17%) |
| **Lịch sử – 10 câu** | Lớp 10 · 1 câu (10%) Lớp 11 · 2 câu (20%) Lớp 12 · 7 câu (70%) | Nhận biết · 1 câu (10%) Thông hiểu · 6 câu (60%) Vận dụng · 3 câu (30%) Vận dụng cao · 0 câu (0%) |
| **Tiếng Anh – 20 câu** | Lấy theo chủ đề | Nhận biết · 3 câu (15%) Thông hiểu · 9 câu (45%) Vận dụng · 8 câu (40%) Vận dụng cao · 0 câu (0%) |

### c. Thao tác của quản trị viên

1. Chọn môn trong **Phần 2 – Trắc nghiệm bắt buộc**. Hệ thống mở các nhóm và vị trí đã điền sẵn.
2. Chọn **Sửa** tại nhóm cần điều chỉnh. Quản trị viên chọn nguồn câu, MAPID và các chỉ tiêu lớp/độ khó cần áp dụng. Với Tiếng Anh, nguồn được chọn riêng cho từng nhóm để câu ngữ âm, đọc điền và đọc hiểu vào đúng vị trí.
3. Với 5 câu trả lời ngắn môn Toán, ngân hàng phải có đáp án hợp lệ. Nếu đề yêu cầu làm tròn, yêu cầu đó phải xuất hiện trong câu hỏi và đáp án chấp nhận phải tương ứng. Bộ chấm dùng quy tắc đã khai báo cho câu.
4. Nhấn **Kiểm tra môn**. Hệ thống đối chiếu đồng thời số câu, dạng trả lời, chùm, điểm và các chỉ tiêu đã bật.

## 6. Luồng thiết lập các môn trắc nghiệm tự chọn

Khi mở **Phần 3 – Trắc nghiệm tự chọn**, quản trị viên thấy bốn môn cần chuẩn bị theo Global List hiện tại. Mỗi môn có cùng cấu trúc **15 câu trắc nghiệm 4 lựa chọn, 15 điểm**, gồm **12 câu đơn tại vị trí 66–77** và **1 chùm nguyên vẹn có 3 câu con tại vị trí 78–80**.

| Môn | Phân bố theo **khối lớp** | Phân bố theo **độ khó** |
|---|---|---|
| **Vật lí – 15 câu** | Lớp 10 · 2 câu (13%) Lớp 11 · 3 câu (20%) Lớp 12 · 10 câu (67%) | Nhận biết · 3 câu (20%) Thông hiểu · 4 câu (27%) Vận dụng · 7 câu (47%) Vận dụng cao · 1 câu (7%) |
| **Hóa học – 15 câu** | Lớp 10 · 3 câu (20%) Lớp 11 · 3 câu (20%) Lớp 12 · 9 câu (60%) | Nhận biết · 1 câu (7%) Thông hiểu · 7 câu (47%) Vận dụng · 6 câu (40%) Vận dụng cao · 1 câu (7%) |
| **Sinh học – 15 câu** | Lớp 10 · 2 câu (13%) Lớp 11 · 3 câu (20%) Lớp 12 · 10 câu (67%) | Nhận biết · 4 câu (27%) Thông hiểu · 5 câu (33%) Vận dụng · 4 câu (27%) Vận dụng cao · 2 câu (13%) |
| **Địa lí – 15 câu** | Lớp 10 · 2 câu (13%) Lớp 11 · 1 câu (7%) Lớp 12 · 12 câu (80%) | Nhận biết · 2 câu (13%) Thông hiểu · 10 câu (67%) Vận dụng · 3 câu (20%) Vận dụng cao · 0 câu (0%) |

Với môn có 15 câu, một câu tương ứng khoảng 6,67%. Vì từng tỷ lệ được làm tròn độc lập, cột độ khó của Vật lí và Hóa học hiển thị tổng 101%. Hệ thống kiểm tra tổng bằng **15 câu nguyên**.

**Luồng cấu hình một môn**

1. Quản trị viên chọn môn, ví dụ **Hóa học**, rồi nhấn **Thiết lập**. Hệ thống mở ma trận 15 câu và hai dòng nguồn gồm **12 câu đơn** và **1 chùm × 3 câu**.
2. Quản trị viên chọn phạm vi kiến thức và nguồn chùm. Có thể giới hạn bằng MAPID, chẳng hạn *Điện phân*, nếu bài cần tập trung vào chủ đề đó.
3. Nếu câu hoặc chùm sử dụng dữ liệu đính kèm, đề thử phải hiển thị dữ liệu cùng câu hỏi. Ví dụ, bảng nguyên tử khối của Hóa học, hình/phả hệ của Sinh học hoặc bảng số liệu/biểu đồ của Địa lí.
4. Hệ thống lấy nguyên chùm và tính phân bố theo từng câu con. Quy tắc này quan trọng với Vật lí vì một chùm trong đề minh họa có câu con thuộc cả lớp 11 và lớp 12.
5. Nhấn **Kiểm tra môn**. Nếu ngân hàng đủ 15 câu riêng lẻ nhưng thiếu chùm phù hợp, hiển thị *“Hóa học cần 1 chùm gồm 3 câu con. Nguồn đang chọn chưa có chùm phù hợp.”*

## 7. Quy tắc áp dụng ma trận toàn bài và MAPID

### a. Các tỷ lệ tham khảo cho toàn bộ 80 câu trắc nghiệm

Hệ thống đối chiếu ma trận riêng cho từng mã CA1–CA4. Mỗi tiêu chí sử dụng số câu thuộc phạm vi áp dụng của tiêu chí đó.

| Tiêu chí | Các câu được tính | Tổng số câu | Phân bố tham khảo |
|---|---|---|---|
| **Khối lớp** | 35 câu Toán + 10 câu Lịch sử + 15 câu của môn tự chọn theo mã | **60** | Lớp 12 khoảng **42 câu (70%)**. Lớp 10 và 11 gộp chung khoảng **18 câu (30%)**. |
| **Độ khó** | 35 câu Toán + 10 câu Lịch sử + 20 câu Tiếng Anh + 15 câu của môn tự chọn theo mã | **80** | Nhận biết và thông hiểu gộp chung khoảng **24 câu (30%)**. Vận dụng khoảng **40 câu (50%)**. Vận dụng cao khoảng **16 câu (20%)**. |

Câu tự luận Ngữ văn nằm ngoài cả hai phép tính. Khi chọn mã để kiểm tra hoặc sinh thử, hệ thống chỉ cộng **15 câu của môn tự chọn tương ứng**.

**Hiển thị khi kiểm tra ma trận:** tại tiêu chí **Khối lớp**, hệ thống đối chiếu số câu thực tế với mốc tham khảo **42/18**. Ví dụ, với **CA1 – Vật lí**, ma trận minh họa có **41/60 câu lớp 12** (24 Toán + 7 Lịch sử + 10 Vật lí) và **19/60 câu lớp 10–11**. Giao diện hiển thị lần lượt **68%** và **32%**. Vì 70%/30% là tỷ lệ tham khảo, chênh lệch một câu được hiển thị để đối chiếu và chỉ trở thành lỗi khi quản trị viên đặt phân bố đó là bắt buộc.

### b. Quy tắc MAPID

Quản trị viên có thể chọn **Toàn môn** hoặc **Theo chủ đề**. Khi chọn nhiều MAPID, hệ thống lấy các câu thuộc ít nhất một chủ đề được chọn. Chọn chủ đề cha mặc định bao gồm các chủ đề con.

Một câu mang nhiều MAPID chỉ xuất hiện một lần trong cùng đề. Nếu có hạn ngạch chủ đề, câu đó chỉ đóng góp vào một hạn ngạch chủ đề nhưng vẫn được tính vào lớp, độ khó và dạng trả lời của nó.

## 8. Luồng rút gọn cho bài thi theo phần hoặc theo môn

| Nhu cầu | Cách thiết lập |
|---|---|
| **Luyện phần trắc nghiệm bắt buộc** | Dùng cấu trúc Toán 35 câu, Lịch sử 10 câu, Tiếng Anh 20 câu. Kết quả tối đa 60 điểm. |
| **Luyện phần tự chọn BCA** | Chuẩn bị ma trận 15 câu cho từng môn thuộc các mã. Học viên chọn một mã và nhận bài 15 câu, tối đa 15 điểm. |
| **Luyện riêng một môn** | Chọn môn ở bước 1. Ví dụ, bài theo môn Vật lí mở thẳng ma trận Vật lí và học viên vào làm trực tiếp. |
| **Luyện một chủ đề trong môn** | Dùng số câu, thời lượng và MAPID của bài ôn. Quản trị viên chỉ khai báo thêm phân bố lớp, độ khó hoặc câu chùm khi có nhu cầu. |

**Ví dụ:** quản trị viên tạo **BCA – Luyện đọc biểu đồ Địa lí**, gồm 10 câu. Tại bước 1, chọn **Bài thi theo môn → Địa lí**. Tại bước 2, chọn MAPID *Kĩ năng đọc biểu đồ*, khai báo **7 câu đơn + 1 chùm 3 câu** rồi kiểm tra nguồn. Học viên mở trực tiếp bài ôn 10 câu. Mã CA4 chỉ cần dùng khi học viên đang chọn môn trong phần tự chọn của BCA.

## 9. Kiểm tra, sinh đề thử và điều kiện hoàn thành

| Thao tác | Hành vi hệ thống |
|---|---|
| **Lưu nháp** | Lưu các phần, nhóm dạng bài, nguồn và ma trận đang thiết lập. Quản trị viên có thể tiếp tục sau khi bổ sung câu hỏi vào ngân hàng. |
| **Kiểm tra môn** | Đối chiếu số câu, tổng điểm, dạng trả lời, chùm nguyên vẹn, tài nguyên, điều kiện chấm và các chỉ tiêu ma trận đã bật. Lỗi dẫn về đúng trường cần sửa. |
| **Sinh đề thử** | Cho phép thử riêng một môn hoặc chọn một mã để xem đề ghép. Với FULL, hiển thị Phần I tự luận, câu 1–65 bắt buộc và câu 66–80 thuộc đúng môn đã chọn. |
| **Kiểm tra toàn bài** | Kiểm tra tất cả các mã thuộc phạm vi bài, tổng 81 câu, thang 100 điểm và đồng hồ chung 180 phút. Hiển thị kết quả đối chiếu phân bố theo từng mã. |
| **Tiếp tục / Mở thi** | Cấu trúc hợp lệ có thể chuyển sang bước sau. Khi mở thi, hệ thống kiểm tra thêm khả năng chấm và công bố kết quả của đúng phạm vi sản phẩm. |
