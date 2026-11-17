# Thực hiện bài thi trực tuyến

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-16 15:46, lấy về ngày 2026-11-16. Nguồn: uniworkos, thư mục "Tài liệu Thi 360"; dùng cho PBI-10 (UNI-1639). Chỉ đọc; bản gốc ở uniworkos.

## 1. Mục tiêu và phạm vi

Học viên bắt đầu bài thi từ Bài thi của tôi, làm bài theo cấu trúc đã công bố và nộp bài để nhận kết quả. Luồng làm bài phụ thuộc vào kỳ thi, hình thức đề FULL hoặc bài lẻ, các phần bắt buộc và các lựa chọn mà học viên được phép thực hiện.

Đối với HSA FULL, học viên làm Toán, sau đó làm Văn. Khi đến phần 3, học viên mới chọn Khoa học hoặc Tiếng Anh. Nếu chọn Khoa học, học viên tích chọn đúng 3 trong 5 môn rồi xác nhận. Hệ thống sinh 50 câu từ ma trận của ba môn vừa chọn và mở phần thi Khoa học.

Một đề FULL được quản lý bằng một lượt thi duy nhất. Việc chuyển phần, chọn môn, sinh câu hỏi cho phần tiếp theo hoặc mở lại bài đang làm đều thuộc lượt thi đó.

## 2. Chuẩn bị và bắt đầu bài thi

| Bước | Hành động của học viên | Xử lý của hệ thống |
|---|---|---|
| 1 | Chọn Bắt đầu trên thẻ bài thi | Mở thông tin chuẩn bị gồm tên bài, kỳ thi, FULL/bài lẻ, cấu trúc, thời lượng, quy tắc chuyển phần và nguồn quyền sẽ sử dụng. |
| 2 | Xem cấu trúc và hướng dẫn | Hiển thị trước các lựa chọn sẽ xuất hiện trong lượt thi. Ví dụ, với HSA FULL, học viên biết phần 3 có hai nhánh và nhánh Khoa học yêu cầu chọn 3 môn. |
| 3 | Chọn Bắt đầu bài thi | Máy chủ kiểm tra tài khoản, quyền sử dụng, số lượt và trạng thái lượt đang làm. Nếu đã có lượt hợp lệ, đưa học viên về đúng lượt đó. |
| 4 | Vào phần đầu tiên | Hệ thống chốt phiên bản cấu trúc và ma trận cho lượt thi, sinh và lưu bộ câu của phần mở đầu. Khi phần đầu tiên sẵn sàng, hệ thống mở lượt và ghi nhận thời điểm bắt đầu trên máy chủ. |

Với phần có môn tự chọn, hệ thống lưu hai dữ liệu ở hai thời điểm khác nhau: phiên bản ma trận được chốt khi bắt đầu lượt FULL, còn danh sách câu thực tế của phần tự chọn được chốt sau khi học viên xác nhận lựa chọn. Nhờ đó, quản trị viên có thể cập nhật đề cho lượt mới mà không làm thay đổi cấu trúc của lượt đang diễn ra.

## 3. Luồng làm đề HSA FULL

### 3.1. Phần 1 – Toán học và xử lí số liệu

| Nội dung | Quy tắc |
|---|---|
| Bắt đầu | Sau khi học viên xác nhận bắt đầu HSA FULL, hệ thống mở trực tiếp phần Toán. |
| Bộ câu hỏi | Sinh 50 câu, gồm 35 câu trắc nghiệm 4 lựa chọn và 15 câu điền đáp án. Các câu con trong chùm được tính vào hai chỉ tiêu này theo dạng trả lời của từng câu. |
| Cách làm | Học viên chọn một phương án hoặc nhập đáp án cuối cùng. Câu trả lời ngắn hiển thị quy ước nhập phù hợp, ví dụ số thập phân dùng dấu phẩy hoặc phân số tối giản. |
| Thời gian | 75 phút cho phần Toán. |
| Kết thúc phần | Khi hết giờ, hệ thống lưu các câu trả lời đã nhận, khóa phần Toán và chuyển sang phần Văn. Nếu đề cho phép kết thúc sớm, học viên chọn Hoàn thành phần Toán và xác nhận trước khi chuyển. |

### 3.2. Phần 2 – Văn học và Ngôn ngữ

Hệ thống mở 50 câu trong 60 phút, gồm 25 câu đơn và 5 chùm đọc hiểu, mỗi chùm 5 câu con. Học viên chọn một trong bốn phương án cho mỗi câu. Khi chuyển giữa các câu con, học viên vẫn xem được ngữ liệu chung của chùm.

Khi học viên hoàn thành phần Văn hoặc hết thời gian, hệ thống lưu đáp án và khóa phần Văn. Nếu sản phẩm cho phép cả hai nhánh, màn tiếp theo hiển thị Chọn phần thi thứ ba với hai lựa chọn:

| Lựa chọn | Thông tin học viên nhìn thấy | Bước tiếp theo |
|---|---|---|
| Khoa học | 50 câu · 60 phút · Chọn 3 trong 5 môn | Mở danh sách chọn môn tại mục 3.3. |
| Tiếng Anh | 50 câu · 60 phút | Hiển thị xác nhận vào phần Tiếng Anh tại mục 3.4. |

Nếu sản phẩm chỉ cấp một nhánh, hệ thống đưa học viên thẳng đến bước chuẩn bị của nhánh được cấp. Ví dụ, đề chỉ mở nhánh Khoa học thì sau phần Văn, học viên được đưa trực tiếp đến bước chọn 3 môn.

### 3.3. Phần 3A – Chọn 3 môn và làm bài Khoa học

| Bước | Hành động hoặc điều kiện | Hành vi yêu cầu |
|---|---|---|
| 1. Hiển thị môn | Học viên chọn Khoa học | Hiển thị 5 ô chọn gồm Vật lí, Hóa học, Sinh học, Lịch sử, Địa lí và số môn đã chọn, ví dụ Đã chọn 0/3 môn. |
| 2. Chọn môn | Học viên tích hoặc bỏ chọn từng môn | Cập nhật số lượng ngay sau mỗi thao tác. Khi đã chọn 3 môn, học viên phải bỏ một môn trước khi chọn môn khác. |
| 3. Kiểm tra lựa chọn | Học viên đã chọn đủ 3 môn | Bật nút Xác nhận và bắt đầu phần Khoa học. Hiển thị tên ba môn đã chọn, tổng 50 câu và thời lượng 60 phút để học viên kiểm tra. |
| 4. Sinh phần thi | Học viên nhấn xác nhận | Máy chủ kiểm tra có đúng 3 môn khác nhau thuộc danh sách được phép. Hệ thống xác định số câu mỗi môn, lấy ngẫu nhiên câu từ ngân hàng theo ma trận đã chốt và kiểm tra đủ 50 câu. |
| 5. Mở phần thi | Bộ câu hỏi hợp lệ đã được lưu | Lưu ba môn, bộ câu, thứ tự và thời điểm bắt đầu phần Khoa học vào lượt FULL hiện tại. Sau thời điểm này, lựa chọn môn được khóa. |
| 6. Làm và nộp bài | Học viên làm bài trong thời gian của phần 3 | Hiển thị câu hỏi theo nhóm môn. Ba môn dùng chung 60 phút. Khi học viên nộp hoặc hết giờ, hệ thống kết thúc lượt HSA FULL. |

Quy tắc phân bổ 50 câu

| Ba môn học viên chọn | Phân bổ số câu |
|---|---|
| 2 môn tự nhiên và 1 môn xã hội | Mỗi môn tự nhiên 17 câu, môn xã hội 16 câu. Ví dụ Vật lí 17 + Hóa học 17 + Lịch sử 16. |
| 1 môn tự nhiên và 2 môn xã hội | Môn tự nhiên 16 câu, mỗi môn xã hội 17 câu. Ví dụ Sinh học 16 + Lịch sử 17 + Địa lí 17. |
| Cả 3 môn tự nhiên | Hai môn 17 câu và một môn 16 câu. Đề xuất cần xác nhận: hệ thống chọn ngẫu nhiên môn nhận 16 câu trước khi lấy câu từ ngân hàng, để giữ nguyên chùm và đủ câu điền đáp án bắt buộc. |

Mỗi môn được lấy theo ma trận riêng về khối lớp, độ khó, MAPID, dạng trả lời và chùm câu. Ví dụ, khi học viên chọn Vật lí – Hóa học – Lịch sử, hệ thống chỉ lấy nguồn của ba môn này. Nếu môn Lịch sử có cấu hình 2 chùm × 3 câu, phần Lịch sử cần thêm 10 câu đơn để đủ 16 câu.

Nếu sinh đề thất bại sau khi học viên xác nhận, hệ thống giữ kết quả phần Toán, phần Văn và ba môn đã chọn. Học viên có thể Thử lại ngay tại phần Khoa học. Hệ thống chỉ bắt đầu đồng hồ của phần này sau khi lưu thành công bộ câu hợp lệ. Nhấn xác nhận nhiều lần hoặc tải lại trang sau khi đã sinh thành công phải mở đúng bộ câu đã lưu.

### 3.4. Phần 3B – Tiếng Anh

Khi học viên xác nhận Bắt đầu phần Tiếng Anh, hệ thống sinh 50 câu trong 60 phút, gồm 35 câu đơn và 3 chùm × 5 câu con. Hệ thống lấy ngẫu nhiên trong từng khối dạng bài và hiển thị theo thứ tự đã công bố trong ma trận.

Học viên chọn một trong bốn phương án cho từng câu, kể cả các vị trí đọc điền. Các câu thuộc cùng bài đọc hiển thị liền nhau. Khi phần Tiếng Anh đã mở, hệ thống khóa lựa chọn nhánh. Nộp phần này hoặc hết giờ sẽ kết thúc lượt HSA FULL.

## 4. Luồng làm đề QDA FULL

QDA FULL có ba phần liên tiếp. Ở phần thứ ba, học viên chọn một mã tổ hợp. Hệ thống lấy danh sách và thứ tự môn từ mã đã công bố trong cấu hình bài thi.

| Giai đoạn | Cách học viên làm bài | Quy tắc chuyển bước |
|---|---|---|
| Phần 1 – Toán học và xử lí số liệu | Làm 50 câu trong 80 phút, gồm 35 câu trắc nghiệm và 15 câu điền đáp án. | Hoàn thành hoặc hết giờ thì lưu, khóa phần Toán và chuyển sang phần 2. |
| Phần 2 – Ngôn ngữ và Văn học | Làm 50 câu trong 55 phút, gồm 25 câu đơn và 5 chùm × 5 câu con. | Hoàn thành hoặc hết giờ thì lưu, khóa phần Văn và mở bước chọn mã cho phần 3. |
| Chọn mã phần 3 | Học viên chọn một mã trong danh sách sản phẩm cho phép. Mỗi mã hiển thị kèm tên môn để học viên kiểm tra trước khi xác nhận. | Máy chủ kiểm tra mã thuộc quyền mua và dùng quan hệ mã – môn của phiên bản đã chốt cho lượt thi. |
| Phần 3 – Làm bài theo mã | Mã Khoa học sinh 2 môn × 25 câu. Mã Tiếng Anh sinh 50 câu Tiếng Anh. | Toàn phần dùng 60 phút. Nộp bài hoặc hết giờ thì kết thúc lượt QDA FULL. |
| Mã khởi tạo | Nội dung phần 3 hệ thống sinh |
|---|---|
| QĐ01 | 25 câu Vật lí và 25 câu Hóa học |
| QĐ02 | 25 câu Hóa học và 25 câu Sinh học |
| QĐ03 | 25 câu Vật lí và 25 câu Sinh học |
| QĐ04 | 25 câu Lịch sử và 25 câu Địa lí |
| QĐ05 | 50 câu Tiếng Anh |

Các mã trên là dữ liệu khởi tạo của danh mục dùng chung. Ví dụ, khi chọn QĐ02, học viên thấy Hóa học và Sinh học, xác nhận một lần và làm hai môn trong cùng phần thi. Nếu sản phẩm chỉ cấp một mã, hệ thống hiển thị sẵn mã đó cùng tên môn để học viên xác nhận bắt đầu.

## 5. Luồng làm bài lẻ HSA và QDA

Bài lẻ mở trực tiếp phần học viên đã mua. Tổng số câu, thời lượng và các lựa chọn lấy từ cấu hình của sản phẩm.

| Sản phẩm | Luồng làm bài |
|---|---|
| Bài lẻ Toán, Văn hoặc Tiếng Anh | Xem thông tin phần đã mua → chọn Bắt đầu → hệ thống sinh câu theo ma trận phần đó → làm bài → nộp bài. |
| Bài lẻ phần Khoa học HSA có quyền chọn 3 môn | Mở bước chọn 3 trong 5 môn ngay khi chuẩn bị bài → xác nhận → sinh đủ câu theo tổ hợp → làm và nộp phần Khoa học. |
| Bài lẻ phần 3 QDA có nhiều mã được phép | Xem các mã và cặp môn → chọn một mã → xác nhận → sinh câu theo mã đã chọn → làm và nộp bài. |
| Bài lẻ đã xác định môn hoặc mã ngay khi mua | Hiển thị môn hoặc mã đi kèm quyền mua → xác nhận bắt đầu → sinh phần thi tương ứng. |

Mỗi lần làm bài lẻ là một lượt của sản phẩm bài lẻ. Nộp phần đã mua sẽ kết thúc lượt đó.

## 6. Luồng làm bài thi đánh giá tuyển sinh Công an nhân dân (BCA)

### 6.1. Chọn mã bài thi và bắt đầu

Đề BCA gồm 81 câu trong 180 phút, sử dụng một đồng hồ chung cho toàn bài. Hai phần bắt buộc gồm Ngữ văn và nhóm môn Toán, Lịch sử, Tiếng Anh. Phần tự chọn gồm một môn được xác định theo mã CA1–CA4.

| Mã bài thi | Môn của phần tự chọn | Số câu |
|---|---|---|
| CA1 | Vật lí | 15 |
| CA2 | Hóa học | 15 |
| CA3 | Sinh học | 15 |
| CA4 | Địa lí | 15 |

- Học viên mở bài BCA đã mua. Màn chuẩn bị hiển thị cấu trúc ba phần, thời lượng 180 phút và các mã thuộc phạm vi sử dụng.

- Nếu sản phẩm cho phép nhiều mã, học viên chọn một mã trước khi bắt đầu. Nếu sản phẩm chỉ cấp một mã, hệ thống hiển thị sẵn mã và tên môn để học viên kiểm tra.

- Khi học viên nhấn Bắt đầu bài thi, máy chủ kiểm tra quyền và mã đã chọn, lấy câu cho hai phần bắt buộc và môn tự chọn tương ứng.

- Sau khi sinh đủ đề, hệ thống lưu mã, bộ câu, thứ tự hiển thị và thời điểm hết giờ. Từ thời điểm này, mã bài thi được khóa cho lượt hiện tại.

### 6.2. Thực hiện từng phần

| Phần | Cấu trúc học viên thực hiện | Hành vi trên cổng thi |
|---|---|---|
| Phần 1 – Ngữ văn | 1 câu nghị luận xã hội theo ngữ liệu, tối đa 25 điểm | Hiển thị ngữ liệu, yêu cầu viết và ô nhập toàn bài. Với cấu trúc minh họa đã cung cấp, hiển thị yêu cầu tối thiểu 500 chữ. Hệ thống lưu nội dung trong quá trình viết. |
| Phần 2 – Toán | 35 câu gồm 30 câu trắc nghiệm 4 lựa chọn và 5 câu trả lời ngắn | Học viên chọn phương án hoặc nhập đáp số theo từng câu. Các câu thuộc chùm sử dụng cùng dữ kiện. |
| Phần 2 – Lịch sử | 10 câu gồm 7 câu đơn và 1 chùm 3 câu | Hiển thị tư liệu chung cùng các câu con. |
| Phần 2 – Tiếng Anh | 20 câu gồm 2 câu ngữ âm, 8 câu từ vựng – ngữ pháp, 5 câu đọc điền và 5 câu đọc hiểu | Giữ thứ tự các nhóm dạng bài. Các vị trí đọc điền và câu đọc hiểu gắn với đúng ngữ liệu. |
| Phần 3 – Môn tự chọn | 15 câu gồm 12 câu đơn và 1 chùm 3 câu theo mã đã chọn | Hiển thị rõ tên môn và mã. Ví dụ, lượt CA2 mở 15 câu Hóa học tại phần này. |

Thanh tiến độ toàn bài tính trên 81 câu. Trong nhóm bắt buộc, số câu và số điểm có thể khác nhau: 65 câu Toán, Lịch sử và Tiếng Anh có tổng tối đa 60 điểm. Hệ thống chấm theo thang của từng phần đã công bố.

### 6.3. Chuyển phần và nộp bài

Học viên sử dụng Chuyển phần để chuyển giữa Ngữ văn, nhóm bắt buộc và môn tự chọn. Thao tác chuyển chỉ thay đổi phần đang hiển thị. Đồng hồ 180 phút tiếp tục chạy. Đề xuất cho phép học viên quay lại rà soát và chỉnh sửa các phần cho đến khi nộp toàn bài hoặc hết giờ.

Khi học viên chọn Nộp bài, hệ thống hiển thị số câu còn bỏ trống và tình trạng của bài viết Ngữ văn. Sau khi nộp, phần có đáp án tự động được chuyển chấm và bài viết được chuyển sang Chờ chấm. Tổng điểm hoàn chỉnh được công bố sau khi có đủ điểm các phần.

## 7. Luồng làm bài V-ACT – Đánh giá năng lực ĐHQG TP.HCM

### 7.1. Bắt đầu bài thi

Đề V-ACT gồm 120 câu trắc nghiệm chọn một đáp án trong 150 phút. Cả ba phần đều bắt buộc. Học viên xem cấu trúc, xác nhận bắt đầu và vào làm bài trực tiếp.

Khi khởi tạo lượt, hệ thống sinh đủ 120 câu theo ma trận đã công bố, lưu các ngữ liệu, bảng số liệu, biểu đồ và thứ tự câu. Đồng hồ bắt đầu sau khi bộ đề hợp lệ đã được lưu.

### 7.2. Thứ tự làm bài

| Phần | Nhóm nội dung | Vị trí trong đề | Số câu |
|---|---|---|---|
| Phần 1 – Sử dụng ngôn ngữ | Tiếng Việt | Câu 1–30 | 30 |
| Phần 1 – Sử dụng ngôn ngữ | Tiếng Anh | Câu 31–60 | 30 |
| Phần 2 – Toán học | Toán học | Câu 61–90 | 30 |
| Phần 3 – Tư duy khoa học | Logic và phân tích số liệu | Câu 91–102 theo mẫu đã cung cấp | 12 |
| Phần 3 – Tư duy khoa học | Suy luận khoa học | Câu 103–120 theo mẫu đã cung cấp | 18 |

Học viên có thể điều hướng theo phần và theo số câu. Với nhóm câu sử dụng chung biểu đồ, bảng dữ liệu hoặc đoạn đọc, khi chuyển sang câu con tiếp theo, hệ thống tiếp tục hiển thị dữ kiện chung. Chuyển từ câu 60 sang câu 61 hoặc từ câu 90 sang câu 91 không khóa phần trước hoặc đặt lại đồng hồ theo phương án điều hướng tại mục 12.1.

### 7.3. Nộp bài và chuyển dữ liệu chấm

Khi nộp bài hoặc hết 150 phút, hệ thống đóng toàn bộ 120 câu trong cùng một lượt. Dữ liệu chấm cần phân biệt ba phần làm bài với bốn thành phần kết quả gồm Tiếng Việt, Tiếng Anh, Toán học và Tư duy khoa học.

Điểm cần chốt đối với V-ACT: nếu sản phẩm luyện thi chưa có mô hình và dữ liệu hiệu chuẩn IRT, kết quả nên hiển thị số câu đúng theo bốn thành phần hoặc điểm luyện tập theo phương pháp đã công bố. Không lấy số câu đúng nhân 10 rồi ghi là điểm IRT trên thang 1.200.

## 8. Luồng làm bài SP2E – Đại học Sư phạm Hà Nội 2

### 8.1. Xác định môn và phiên bản đề

Học viên mở bài của môn đã mua. Màn chuẩn bị hiển thị môn, phiên bản cấu trúc, số phần, số câu và thời lượng. Khi học viên xác nhận bắt đầu, hệ thống sinh một đề theo đúng cặp môn + phiên bản cấu trúc.

Trường phiên bản cần được sử dụng khi sinh đề. Ví dụ, Toán theo đề minh họa 2025 có 22 câu/90 phút, còn Toán theo cấu trúc 2026 có 40 câu/90 phút. Nếu chỉ lưu môn và thời lượng, hệ thống không đủ thông tin để xác định bộ câu cần sinh.

### 8.2. Luồng theo môn với cấu trúc SP2E 2026

| Môn | Thứ tự các phần học viên thực hiện | Số câu | Thời lượng |
|---|---|---|---|
| Toán | Phần 1 gồm 20 câu chọn một đáp án và 5 câu chọn nhiều đáp án. Phần 2 gồm 5 câu chọn một đáp án dựa trên dữ liệu tổng hợp. Phần 3 gồm 10 câu nhập đáp số. | 40 | 90 phút |
| Ngữ văn | Đọc ngữ liệu và làm 20 câu trắc nghiệm → viết 1 đoạn văn → viết 1 bài văn. | 22 | 90 phút |
| Vật lí, Hóa học, Sinh học, Địa lí | 16 câu chọn một đáp án → 4 câu Đúng/Sai, mỗi câu 4 ý → 8 câu trả lời ngắn. Mỗi môn là một bài độc lập. | 28/môn | 60 phút/môn |
| Lịch sử, Giáo dục kinh tế và pháp luật | 26 câu chọn một đáp án → 6 câu Đúng/Sai, mỗi câu 4 ý. | 32/môn | 60 phút/môn |
| Tiếng Anh | Làm 50 câu trắc nghiệm theo thứ tự các khối dạng bài trong ma trận môn. Các câu đọc cùng ngữ liệu được giữ trong cùng nhóm. | 50 | 60 phút |

Trong phần Toán, câu chọn một đáp án chỉ cho phép giữ một lựa chọn. Khi đến câu yêu cầu chọn nhiều đáp án, hệ thống hiển thị hướng dẫn Chọn các phương án đúng và cho phép tích hoặc bỏ từng phương án. Hai dạng này phải có hành vi nhập và lưu đáp án riêng.

### 8.3. Xử lý đề theo phiên bản khác

Nếu sản phẩm sử dụng đề minh họa SP2E 2025, hệ thống lấy cấu trúc đã công bố cho chính sản phẩm đó. Ví dụ, mẫu Vật lí 2025 có phần ghép đôi. Khi đến phần này, học viên chọn một mục ở cột phải cho từng mục ở cột trái. Hệ thống lưu từng cặp và khôi phục đúng các cặp đã chọn khi học viên quay lại câu.

Các phần của một môn dùng chung đồng hồ. Khi nộp môn Ngữ văn, hệ thống lưu riêng bài viết đoạn và bài viết dài để người chấm có thể chấm từng yêu cầu.

## 9. Luồng làm bài SPT – Đại học Sư phạm Hà Nội

### 9.1. Bắt đầu và làm bài theo môn

Học viên mở sản phẩm của môn đã mua. Hệ thống hiển thị cấu trúc theo đúng bộ đề tham khảo gắn với sản phẩm. Bảng dưới sử dụng bộ đề SPT mã 075 trong tài liệu khách hàng cung cấp.

| Môn | Các phần theo thứ tự thực hiện | Số câu học viên làm | Thời lượng |
|---|---|---|---|
| Toán | 16 trắc nghiệm → 2 Đúng/Sai → 4 trả lời ngắn → 3 tự luận | 25 | 90 phút |
| Ngữ văn | 10 câu đọc hiểu trên 2 ngữ liệu → 1 đoạn nghị luận xã hội → 1 bài nghị luận văn học | 12 | 90 phút |
| Vật lí | 12 trắc nghiệm → 3 Đúng/Sai → 4 trả lời ngắn → 2 tự luận | 21 | 60 phút |
| Hóa học | 20 trắc nghiệm → 2 Đúng/Sai → 4 trả lời ngắn → 1 tự luận có nhiều ý | 27 | 60 phút |
| Tiếng Anh | 18 câu sử dụng tiếng Anh → 10 câu đọc hiểu → 4 câu điền dạng đúng của từ → 1 bài viết đoạn | 33 | 60 phút |
| Sinh học | 12 trắc nghiệm → 3 Đúng/Sai → 4 trả lời ngắn → 3 tự luận | 22 | 60 phút |
| Lịch sử | 20 trắc nghiệm → 2 Đúng/Sai → 3 tự luận | 25 | 60 phút |
| Địa lí | 12 trắc nghiệm → 3 Đúng/Sai → 4 trả lời ngắn → 1 tự luận | 20 | 60 phút |
| Giáo dục kinh tế và pháp luật | 16 trắc nghiệm → 3 Đúng/Sai → 2 tự luận | 21 | 60 phút |
| Công nghệ – Công nghiệp | 16 trắc nghiệm → 2 Đúng/Sai → 4 trả lời ngắn → 2 tự luận | 24 | 60 phút |
| Công nghệ – Nông nghiệp | 16 trắc nghiệm → 2 Đúng/Sai → 4 trả lời ngắn → 2 tự luận | 24 | 60 phút |
| Tin học | 20 trắc nghiệm → 2 câu Đúng/Sai thuộc phạm vi phải làm → 3 câu tự luận thuộc phạm vi phải làm | 25 trong cấu trúc 27 câu | 60 phút |

Câu trả lời ngắn hiển thị ô nhập kết quả. Câu tự luận hiển thị vùng viết lời giải hoặc bài viết. Nếu một câu tự luận có nhiều ý, hệ thống lưu nội dung theo cách khai báo trong câu hỏi và vẫn tính đó là một câu trong tiến độ toàn bài.

### 9.2. Chọn định hướng trong môn Tin học SPT

Tin học SPT có lựa chọn ảnh hưởng đến cả phần Đúng/Sai và phần tự luận. Học viên xác nhận định hướng một lần cho toàn lượt.

| Bước | Hành vi yêu cầu |
|---|---|
| 1. Làm phần chung | Học viên làm 20 câu trắc nghiệm chung. |
| 2. Gặp câu riêng đầu tiên | Khi học viên mở nội dung dành cho định hướng, hệ thống yêu cầu chọn Khoa học máy tính hoặc Tin học ứng dụng. |
| 3. Xác nhận | Hiển thị định hướng đã chọn và thông báo rằng lựa chọn này áp dụng cho các phần riêng còn lại. Sau xác nhận, hệ thống lưu lựa chọn vào lượt thi. |
| 4. Làm phần Đúng/Sai | Hiển thị 1 câu chung và 1 câu của định hướng đã chọn. Học viên trả lời từng mệnh đề. |
| 5. Làm phần tự luận | Hiển thị 2 câu chung và 1 câu thuộc cùng định hướng. Học viên không phải chọn lại. |
| 6. Nộp bài | Tính tiến độ và chấm trên 25 câu gồm 20 + 2 + 3. Hai câu thuộc định hướng còn lại không được ghi nhận là bỏ trống. |

Đồng hồ 60 phút chạy liên tục từ khi bắt đầu môn, bao gồm thời gian học viên chọn định hướng. Khi tải lại trang hoặc đổi thiết bị, hệ thống khôi phục đúng định hướng đã xác nhận.

## 10. Luồng làm bài thi tốt nghiệp THPT

### 10.1. Cấu trúc theo môn

Mỗi môn là một bài thi độc lập. Khi học viên chọn Bắt đầu, hệ thống mở đúng môn đã mua và dùng một đồng hồ cho toàn môn. Bảng dưới áp dụng cho cấu trúc từ năm 2025 trong bộ tài liệu của dự án.

| Môn | Thứ tự các phần | Số câu học viên làm | Thời lượng |
|---|---|---|---|
| Ngữ văn | Đọc hiểu ngữ liệu → thực hiện phần Viết. Bộ đề tham khảo của dự án gồm 5 câu đọc hiểu và 2 yêu cầu viết. | 7 theo mẫu | 120 phút |
| Toán | 12 trắc nghiệm chọn một → 4 Đúng/Sai → 6 trả lời ngắn | 22 | 90 phút |
| Vật lí, Hóa học, Sinh học, Địa lí | 18 trắc nghiệm chọn một → 4 Đúng/Sai → 6 trả lời ngắn | 28/môn | 50 phút/môn |
| Lịch sử, Giáo dục kinh tế và pháp luật | 24 trắc nghiệm chọn một → 4 Đúng/Sai | 28/môn | 50 phút/môn |
| Công nghệ – Công nghiệp, Công nghệ – Nông nghiệp | 24 trắc nghiệm chọn một → 4 Đúng/Sai theo đúng môn Công nghệ đã mở | 28/môn | 50 phút/môn |
| Tiếng Anh | 40 câu trắc nghiệm theo các nhóm dạng bài và ngữ liệu của đề | 40 | 50 phút |
| Tin học | 24 trắc nghiệm chọn một → 2 câu Đúng/Sai chung → 2 câu Đúng/Sai của định hướng đã chọn | 28 trong cấu trúc 30 câu | 50 phút |

### 10.2. Chọn định hướng trong môn Tin học THPT

- Học viên bắt đầu môn và làm phần trắc nghiệm chung.

- Tại phần Đúng/Sai, học viên được làm 2 câu chung. Khi mở nhóm câu riêng, hệ thống yêu cầu chọn Khoa học máy tính hoặc Tin học ứng dụng.

- Sau xác nhận, hệ thống chỉ mở 2 câu riêng của định hướng đã chọn. Đồng hồ 50 phút tiếp tục chạy.

- Danh sách điều hướng, số câu chưa trả lời và màn xác nhận nộp bài đều tính trên 28 câu học viên phải làm.

- Khi nộp, hệ thống lưu định hướng cùng bài làm. Các câu thuộc định hướng còn lại được loại khỏi phạm vi chấm và thống kê điểm yếu.

Đối với Công nghệ, định hướng Công nghiệp hoặc Nông nghiệp đã được xác định từ môn của sản phẩm. Học viên mở bài Công nghệ – Công nghiệp sẽ làm cấu trúc của môn đó ngay từ đầu.

### 10.3. Ghi nhận câu Đúng/Sai và bài tự luận

Mỗi câu Đúng/Sai có bốn mệnh đề. Hệ thống lưu riêng từng ý với ba giá trị Đúng, Sai hoặc Chưa trả lời. Khi học viên mới trả lời một phần, danh sách điều hướng cần cho biết câu còn thiếu ý. Mẫu số của tiến độ môn Toán vẫn là 22 câu, kể cả khi có 16 mệnh đề trong 4 câu Đúng/Sai.

Với Ngữ văn, hệ thống lưu đáp án từng câu đọc hiểu và nội dung từng yêu cầu viết. Khi nộp thành công, học viên thấy trạng thái Đã nộp – Chờ chấm cho các nội dung cần người chấm.

## 11. Thao tác chung trên màn làm bài

| Thành phần | Yêu cầu |
|---|---|
| Thông tin lượt thi | Hiển thị tên bài, kỳ thi, phần hoặc môn hiện tại. Khi có lựa chọn, hiển thị mã bài thi, tổ hợp hoặc định hướng đã xác nhận. |
| Đồng hồ | Ghi rõ thời gian còn lại của phần đối với HSA/QDA có đồng hồ từng phần hoặc thời gian còn lại của bài đối với đề dùng chung thời gian. |
| Điều hướng | Hiển thị các câu thuộc phạm vi học viên phải làm. Phân biệt câu chưa trả lời, đã trả lời một phần, đã trả lời và câu được đánh dấu xem lại. |
| Chọn đáp án | Câu chọn một cho phép thay thế lựa chọn. Câu chọn nhiều cho phép tích và bỏ từng phương án. Câu Đúng/Sai và ghép đôi lưu từng ý hoặc từng cặp. |
| Nhập đáp án | Trả lời ngắn hiển thị quy ước nhập của chính câu. Tự luận lưu nội dung học viên đã viết. Với câu điền khuyết, từng câu trả lời gắn với đúng vị trí trong ngữ liệu. |
| Câu chùm | Các câu con đi liền với ngữ liệu chung. Khi chuyển câu hoặc khôi phục lượt, hệ thống giữ đúng quan hệ giữa ngữ liệu, câu con và đáp án đã nhập. |
| Lưu bài | Hiển thị Đang lưu, Đã lưu hoặc Chưa đồng bộ theo phản hồi thực tế. Chỉ hiển thị Đã lưu sau khi máy chủ xác nhận. |
| Hoàn thành phần | Dùng cho phần có quy tắc khóa riêng. Trước khi khóa sớm, hiển thị tên phần, số câu hoặc ý còn thiếu và yêu cầu xác nhận. |
| Nộp toàn bài | Hiển thị số câu còn bỏ trống hoặc làm dở trong phạm vi của học viên. Sau khi xác nhận nộp thành công, đóng quyền chỉnh sửa và hiển thị trạng thái chấm hoặc kết quả. |

Trong lúc thi, thiết bị học viên chỉ nhận nội dung phục vụ làm bài. Đáp án chuẩn, lời giải và giải thích AI được cung cấp theo chính sách công bố sau thi.

## 12. Quy tắc thời gian, chuyển phần và khôi phục lượt

### 12.1. Đồng hồ và quyền quay lại

| Loại bài | Cách tính giờ | Quy tắc chuyển phần |
|---|---|---|
| HSA FULL, QDA FULL | Mỗi phần dùng thời lượng riêng theo cấu trúc đã công bố. | Khi kết thúc phần, lưu đáp án và khóa phần đó. Đến phần tự chọn, thực hiện đúng bước chọn môn hoặc mã của kỳ thi. |
| BCA, V-ACT | Một đồng hồ cho toàn bài. | Đề xuất cho phép chuyển và quay lại giữa các phần còn thuộc lượt thi cho đến khi nộp hoặc hết giờ. |
| SP2E, SPT, tốt nghiệp THPT | Một đồng hồ cho toàn môn. | Chuyển nhóm dạng câu không làm đặt lại giờ. Với Tin học, định hướng đã xác nhận tiếp tục áp dụng khi học viên quay lại câu trước. |
| Bài lẻ | Dùng thời lượng công bố riêng cho bài đã mua. | Nộp bài lẻ kết thúc một lượt của sản phẩm đó. |

D04 cần xác nhận hai hành vi: quyền quay lại đối với bài dùng đồng hồ chung và thời gian chờ chọn ở phần 3 HSA/QDA. Với HSA/QDA, đề xuất bắt đầu đồng hồ phần 3 sau khi bộ câu đã sinh thành công, đồng thời quy định giới hạn chờ chọn và cách kết thúc nếu học viên bỏ dở ở bước này. Với Tin học SPT/THPT, bước chọn định hướng diễn ra trong thời gian làm môn.

### 12.2. Mất kết nối, tải lại và tiếp tục thi

| Tình huống | Hành vi yêu cầu |
|---|---|
| Tải lại hoặc đăng nhập lại khi đang làm | Khôi phục đúng lượt, phần, bộ câu, lựa chọn đã chốt, đáp án máy chủ đã nhận và thời gian còn lại. |
| Mất kết nối khi đang nhập đáp án | Hiển thị trạng thái chưa đồng bộ. Khi kết nối lại, đối chiếu trạng thái lượt trước khi gửi phần còn chờ. Một bản lưu cũ không được ghi đè đáp án mới hơn đã được xác nhận. |
| Mất kết nối trong lúc xác nhận chọn nhánh | Khi quay lại, kiểm tra lựa chọn trên máy chủ. Nếu đã xác nhận thành công, mở đúng nhánh. Nếu chưa ghi nhận, quay lại bước chọn trong thời gian còn cho phép. |
| Nhấn bắt đầu, xác nhận mã hoặc nộp bài nhiều lần | Mỗi thao tác chỉ có một kết quả cho cùng lượt. Tải lại sau khi nộp hiển thị kết quả nộp đã ghi nhận. |
| Lỗi sinh đề trước khi mở lượt | Báo lỗi và cho thử lại. Chưa bắt đầu đồng hồ hoặc tiêu hao lượt. |
| Lỗi sinh phần tự chọn sau khi đã làm phần bắt buộc | Giữ các phần đã hoàn thành và lựa chọn đã xác nhận. Thử sinh lại trong cùng lượt. Đồng hồ phần chưa mở chỉ chạy sau khi lưu thành công bộ câu. |
| Hết hạn quyền mua trong lúc đang thi | Cho tiếp tục lượt đã bắt đầu hợp lệ theo đồng hồ của lượt đó. Khi lượt kết thúc, kiểm tra quyền trước khi mở lượt mới. |
| Hết giờ khi học viên đang ngoại tuyến | Máy chủ đóng phần hoặc toàn bài theo mốc đã lưu. Khi kết nối lại, hiển thị trạng thái thực tế và các đáp án đã được ghi nhận hợp lệ trước hạn. |

### 12.3. Ghi nhận lượt sử dụng

Theo phương án D05 đang chờ xác nhận, hệ thống giữ một lượt khi khởi tạo bài thành công và ghi nhận đã dùng khi bài kết thúc do nộp hoặc hết giờ. Chuyển phần, chọn môn, xác nhận định hướng, thử lại do lỗi hoặc đổi thiết bị đều sử dụng cùng mã lượt. Với đề FULL, hệ thống ghi nhận một lượt cho toàn bài.

## 13. Tiêu chí nghiệm thu PBI-10

| Mã | Tình huống kiểm tra | Kết quả cần đạt |
|---|---|---|
| AC-10.01 | Làm HSA FULL và chọn Khoa học sau phần Văn | Chỉ mở phần Khoa học sau khi chọn đúng 3 trong 5 môn. Sinh đủ 50 câu đúng ba môn, đúng phân bổ và giữ nguyên các chùm. |
| AC-10.02 | Làm HSA FULL và chọn Tiếng Anh | Sinh 50 câu theo đúng thứ tự khối. Cả lượt FULL chỉ sử dụng một lượt mua. |
| AC-10.03 | Chọn QĐ02 ở phần 3 QDA | Sinh 25 câu Hóa học và 25 câu Sinh học. Tải lại vẫn giữ QĐ02, bộ câu và thời điểm hết giờ. |
| AC-10.04 | Mở bài lẻ có lựa chọn môn hoặc mã | Bước chọn xuất hiện ngay khi chuẩn bị bài lẻ. Nộp phần đã mua kết thúc lượt. |
| AC-10.05 | Làm BCA mã CA2 | Sinh đúng 81 câu, phần tự chọn gồm 15 câu Hóa học. Chuyển giữa bài viết và câu trắc nghiệm vẫn dùng chung 180 phút. Nộp bài lưu cả hai loại đáp án. |
| AC-10.06 | Làm V-ACT và chuyển từ câu 60 sang 61, từ 90 sang 91 | Hiển thị đúng phần mới, giữ đồng hồ 150 phút. Khi nộp, lưu đủ dữ liệu theo bốn thành phần kết quả. |
| AC-10.07 | Mở hai sản phẩm Toán SP2E có phiên bản 2025 và 2026 | Sinh lần lượt 22 và 40 câu theo mẫu tương ứng. Trong phiên bản 2026, năm câu chọn nhiều cho phép lưu nhiều phương án. |
| AC-10.08 | Làm Ngữ văn SP2E hoặc SPT | Lưu đúng nhóm đọc hiểu, bài viết đoạn và bài viết dài. Có thể khôi phục nội dung đã lưu. Sau khi nộp, phần viết hiển thị trạng thái chờ chấm. |
| AC-10.09 | Chọn Tin học ứng dụng trong đề Tin học SPT | Cùng định hướng được áp dụng cho phần Đúng/Sai và tự luận. Tiến độ và phạm vi chấm là 25 câu trong 60 phút. |
| AC-10.10 | Chọn một định hướng trong đề Tin học THPT | Học viên thực hiện 24 câu trắc nghiệm, 2 câu Đúng/Sai chung và 2 câu Đúng/Sai riêng. Màn nộp bài và kết quả đều dùng mẫu số 28. |
| AC-10.11 | Học viên mới trả lời 2 trong 4 ý của một câu Đúng/Sai | Hệ thống lưu từng ý, hiển thị câu đang làm dở và giữ hai ý còn lại ở trạng thái chưa trả lời. |
| AC-10.12 | Mất mạng, chỉnh giờ thiết bị hoặc mở cùng lượt trên thiết bị khác | Thời gian còn lại lấy từ máy chủ. Khôi phục đúng bộ câu và lựa chọn đã xác nhận. Không phát sinh lượt mới. |
| AC-10.13 | Sinh phần sau bị lỗi hoặc yêu cầu nộp được gửi lặp | Phần đã làm vẫn được lưu. Thử lại không tính thêm lượt. Một lượt chỉ có một lần nộp cuối cùng được ghi nhận. |
