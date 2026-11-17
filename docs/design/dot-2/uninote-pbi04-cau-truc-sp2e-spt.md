# Thiết lập cấu trúc và ma trận đề thi SP2E và SPT

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-17 14:46, lấy về ngày 2026-11-17. Nguồn: uniworkos, thư mục "Tài liệu Thi 360" (doc `01M7KX97BXHDM9H1S2VX47SWPB`). Chỉ đọc; bản gốc ở uniworkos.

## 1. Điểm vào và phạm vi thiết lập

Tại chức năng **Quản lý bài thi → Tạo bài thi**, quản trị viên nhập tên bài, chọn kỳ thi **SP2E hoặc SPT**, chọn môn và phạm vi bài thi. Hệ thống sử dụng danh mục môn đã khai báo trong Global List.

| Nhu cầu | Khai báo tại bước 1 | Nội dung mở tại bước 2 |
|---|---|---|
| Tạo đề đầy đủ của một môn | Chọn **Bài thi theo môn**, chọn môn, chọn **Đầy đủ cấu trúc** | Mở toàn bộ các phần hoặc nhóm dạng bài của môn, điền sẵn số câu và thời lượng theo cấu trúc |
| Tạo bài ôn một hay nhiều chủ đề | Chọn **Bài thi theo môn**, chọn môn, chọn **Ôn theo chủ đề** | Mở cấu hình rút gọn để chọn MAPID, số câu, thời lượng và dạng câu cần lấy |
| Tạo bộ đề để bán | Từ chức năng thiết lập bán, chọn **Tạo bộ đề SP2E** | Chọn các môn và bài thi đã/chuẩn bị cấu hình tương ứng. Mỗi bài giữ ma trận, thời lượng và lượt làm riêng |

Ví dụ, **SP2E – Toán – Đề đầy đủ**, **SP2E – Toán – Luyện phần trả lời ngắn** và **SP2E – Toán – Luyện đồ thị hàm số** đều sử dụng ngân hàng Toán. Ba lựa chọn mở ba phạm vi thiết lập khác nhau.

## 2. Trình tự thiết lập và điều hướng

| Bước | Thao tác của quản trị viên | Kết quả hệ thống |
|---|---|---|
| 1. Khai báo tổng quan | Nhập tên bài, chọn kỳ thi, môn và phạm vi. Xác nhận số câu, thời lượng, thang điểm | Tạo bản nháp và mở đúng môn hoặc phần cần thiết lập |
| 2. Thiết lập cấu trúc chi tiết | Chọn từng phần hoặc nhóm dạng bài. Khai báo số câu, cách lấy, phạm vi ngân hàng, phân bổ và quy tắc điểm | Hiển thị số câu đã phân bổ, số còn thiếu hoặc vượt, số lệnh hỏi và tổng điểm |
| 3. Kiểm tra cấu hình | Chọn **Kiểm tra phần** hoặc **Kiểm tra toàn bài** | Đối chiếu cấu trúc với ngân hàng. Mỗi lỗi ghi rõ môn, phần, chỉ tiêu cần sửa và cho phép mở đúng vị trí |
| 4. Sinh và dùng thử | Chọn **Sinh đề thử**, xem cấu trúc thực tế hoặc làm thử như học viên | Dùng cùng quy tắc sinh đề với lượt thi thật, hiển thị đối chiếu yêu cầu và thực tế, không trừ lượt |
| 5. Xuất bản và thiết lập bán | Xuất bản bài đã kiểm tra. Chọn bán riêng hoặc đưa vào bộ đề | Lưu phiên bản đã công bố và quyền sử dụng tương ứng với sản phẩm bán |

Tại bước 2, phần đầu hiển thị tên bài, môn, số câu mục tiêu, thời lượng và thang điểm từ bước 1. Bên dưới là danh sách phần theo thứ tự làm bài. Khi chọn một phần, hệ thống chỉ hiển thị các trường phù hợp với dạng bài của phần đó. **Lưu nháp** được phép khi cấu hình còn thiếu. Khi mở lại, hệ thống giữ các thiết lập đã lưu.

## 3. Quy tắc dùng chung khi thiết lập ma trận

### 3.1. Phân biệt số câu, số lệnh hỏi và số điểm

Đây là quy tắc cần thống nhất trước khi thiết kế biểu mẫu. Ví dụ, **4 câu Đúng/Sai, mỗi câu 4 ý** đóng góp **4 câu vào cấu trúc đề** và **16 lệnh hỏi vào ma trận theo ý**. Điểm của 4 câu này do quy tắc chấm được cấu hình quyết định.

| Cách lấy | Quản trị viên khai báo | Hệ thống tính và kiểm tra |
|---|---|---|
| Câu đơn | Dạng bài, dạng trả lời, số câu cần lấy | Mỗi câu đơn được tính một lần vào tổng số câu |
| Câu có nhiều ý | Số câu, số ý trong mỗi câu, dạng trả lời và cách chấm từng ý hoặc cả câu | Hiển thị cả số câu và số ý. Khi lấy từ ngân hàng phải giữ đủ các ý thuộc cùng một câu |
| Chùm dùng ngữ liệu chung | Số chùm, số câu con mỗi chùm, điều kiện về ngữ liệu và thứ tự câu con | Chọn nguyên chùm. Mỗi câu con đóng góp vào tổng số câu của đề. Ngữ liệu chung không cộng thêm một câu |
| Phân bổ theo điểm | Điểm tối đa của phần và quy tắc điểm áp dụng từ ngân hàng | Đối chiếu tổng điểm có thể đạt với thang điểm của bài. Hiển thị rõ phần nào gây chênh lệch |

Trước khi nhập tỷ lệ, quản trị viên phải thấy rõ tỷ lệ đang tính **theo câu, theo lệnh hỏi hay theo điểm**. Nếu quản trị viên dùng tỷ lệ để đặt hạn ngạch sinh đề, hệ thống hiển thị số lượng sau quy đổi để xác nhận. Ví dụ, mục tiêu 30% của 40 câu là 12 câu. Quy tắc này chỉ áp dụng khi mẫu số được chọn là 40 câu.

### 3.2. Phạm vi lấy câu và hạn ngạch

| Trường thiết lập | Hành vi |
|---|---|
| Phạm vi kiến thức | Mặc định **Toàn môn**. Khi chọn **Theo chủ đề**, quản trị viên tìm và chọn một hoặc nhiều MAPID thuộc môn. Chọn MAPID cha có thể bao gồm các chủ đề con. |
| Phân bổ theo MAPID | Nếu chỉ chọn MAPID, hệ thống dùng các mã đó để lọc nguồn. Nếu bật **Phân bổ theo chủ đề**, quản trị viên nhập số lượng cần lấy cho từng chủ đề. |
| Khối lớp | Cho phép đặt hạn ngạch lớp 10, 11, 12 khi cấu trúc có yêu cầu. Với nội dung phân loại theo kỹ năng, chẳng hạn đọc biểu đồ, hệ thống giữ nhóm kỹ năng riêng. |
| Độ khó | Sử dụng các mức NB, TH, VD, VDC từ Global List. Cho phép áp dụng ở cấp toàn môn, phần hoặc từng dòng ma trận. |
| Dạng bài và dạng trả lời | Dạng bài dùng để chọn đúng nội dung. Dạng trả lời quyết định cách học viên nhập đáp án và hệ thống chấm. Ví dụ, *sắp xếp hội thoại* trong Tiếng Anh SP2E vẫn có thể trả lời bằng cách chọn một trong bốn phương án. |
| Thứ tự | Giữ thứ tự các phần và các nhóm có vị trí xác định. Trong mỗi nhóm, hệ thống lấy ngẫu nhiên các câu hoặc chùm thỏa điều kiện. |

Một câu có nhiều MAPID chỉ xuất hiện một lần trong đề và chỉ đóng góp vào một hạn ngạch chủ đề cùng cấp. Câu đó vẫn đồng thời được tính vào phân bố lớp, độ khó và dạng câu. Khi ma trận yêu cầu độ khó theo từng ý, ngân hàng phải có mức độ của từng ý để hệ thống kiểm tra được chỉ tiêu đó.

## 4. Cấu trúc khởi tạo cho 9 môn SP2E

Đề xuất dùng cột **Cấu trúc 2026 theo đề án** trong tài liệu khách hàng làm mặc định khi chọn **Đầy đủ cấu trúc**. Năm là thông tin về nguồn của cấu trúc. Nếu đơn vị muốn luyện theo đề minh họa cũ, quản trị viên có thể chọn một cấu trúc khác ngay trong bước 2, với mô tả thể hiện rõ các dạng câu và số lượng.

| Môn | Phần hoặc nhóm được khởi tạo mặc định | Tổng số câu | Thời lượng |
|---|---|---|---|
| Toán | I. 20 câu chọn một đáp án và 5 câu có thể chọn nhiều đáp án. II. 5 câu dựa trên dữ liệu tổng hợp. III. 10 câu trả lời ngắn | 40 | 90 phút |
| Ngữ văn | I. Đọc hiểu 20 câu trắc nghiệm. II. Viết đoạn 1 câu. III. Viết bài 1 câu | 22 | 90 phút |
| Vật lí | I. 16 câu chọn một đáp án. II. 4 câu Đúng/Sai × 4 ý. III. 8 câu trả lời ngắn | 28 | 60 phút |
| Hóa học | I. 16 câu chọn một đáp án. II. 4 câu Đúng/Sai × 4 ý. III. 8 câu trả lời ngắn | 28 | 60 phút |
| Sinh học | I. 16 câu chọn một đáp án. II. 4 câu Đúng/Sai × 4 ý. III. 8 câu trả lời ngắn | 28 | 60 phút |
| Lịch sử | I. 26 câu chọn một đáp án. II. 6 câu Đúng/Sai × 4 ý | 32 | 60 phút |
| Địa lí | I. 16 câu chọn một đáp án. II. 4 câu Đúng/Sai × 4 ý. III. 8 câu trả lời ngắn | 28 | 60 phút |
| Tiếng Anh | 50 câu chọn một đáp án, chia thành 9 nhóm dạng bài có thứ tự | 50 | 60 phút |
| Giáo dục kinh tế và pháp luật | I. 26 câu chọn một đáp án. II. 6 câu Đúng/Sai × 4 ý | 32 | 60 phút |

**Quy tắc khởi tạo từ tài liệu khách hàng.** Toán, Ngữ văn, Vật lí, Hóa học, Sinh học và Lịch sử có cấu trúc tổng quan mới nhưng ma trận chi tiết trong tài liệu mô tả đề minh họa cũ. Hệ thống được điền sẵn số phần, số câu, dạng câu và thời lượng của cấu trúc mới. Các hạn ngạch chi tiết lấy từ đề cũ chỉ sử dụng khi quản trị viên chủ động chọn cấu trúc tương ứng. Ví dụ, phần III của Vật lí mặc định là **8 câu trả lời ngắn**. Dạng **ghép đôi** thuộc cấu trúc minh họa cũ.

Tỷ trọng mức độ tư duy ở bảng tổng quan được hiển thị để đối chiếu:

| Môn | Nhận biết | Thông hiểu | Vận dụng | Vận dụng cao |
|---|---|---|---|---|
| Toán | 30% | 30% | 25% | 15% |
| Ngữ văn | 20% | 30% | 30% | 20% |
| Bảy môn còn lại | 30% | 30% | 20% | 20% |

Có trường hợp tỷ trọng tổng quan và ma trận chi tiết sử dụng cách phân loại khác nhau. Ví dụ, Địa lí có bảng tổng quan bốn mức nhưng ma trận chi tiết theo lệnh hỏi chỉ có ba mức. Khi chọn ma trận chi tiết để sinh đề, hệ thống dùng các chỉ tiêu của ma trận đó và hiển thị bảng tổng quan ở chế độ tham khảo. Quản trị viên có thể thấy rõ bộ chỉ tiêu nào đang thực sự chi phối việc lấy câu.

## 5. Luồng thiết lập Toán và Ngữ văn

### 5.1. Môn Toán

Khi mở **Cấu trúc chi tiết → Toán**, hệ thống hiển thị ba phần theo thứ tự. Phần I có hai dòng riêng vì cách làm và cách chấm của hai dạng câu khác nhau.

| Vị trí | Số câu | Nội dung quản trị viên thiết lập | Quy tắc sinh đề |
|---|---|---|---|
| Phần I – Chọn một đáp án | 20 | Phạm vi MAPID, phân bổ lớp và độ khó nếu áp dụng | Lấy 20 câu có 4 phương án và một đáp án đúng |
| Phần I – Có thể chọn nhiều đáp án | 5 | Phạm vi kiến thức và quy tắc chấm tập đáp án | Lấy 5 câu có kiểu trả lời cho phép chọn nhiều phương án. Giữ ánh xạ đáp án nếu đảo thứ tự lựa chọn |
| Phần II – Dữ liệu tổng hợp | 5 | Nguồn dữ liệu, số câu độc lập hoặc cách ghép các câu cùng sử dụng một bảng, biểu đồ hay đoạn thông tin | Lấy đủ 5 câu có dữ liệu tương ứng. Nếu dùng chùm, giữ dữ liệu cùng các câu con. Số chùm do quản trị viên thiết lập |
| Phần III – Trả lời ngắn | 10 | Phạm vi kiến thức, đáp án và quy tắc đối sánh lấy từ ngân hàng | Lấy 10 câu có thể nhập và chấm đáp số theo quy tắc đã thiết lập |

Khi quản trị viên chọn một dòng, hệ thống hiển thị **Số câu cần lấy**, **Nguồn phù hợp**, **Phạm vi MAPID** và **Thêm phân bổ**. Ví dụ, trong 10 câu trả lời ngắn, quản trị viên yêu cầu 3 câu thuộc MAPID Hàm số. Ba câu này đã nằm trong chỉ tiêu 10 câu. Nếu chỉ chọn Hàm số ở trường lọc nguồn thì cả 10 câu đều phải thuộc phạm vi đó.

### 5.2. Môn Ngữ văn

### a. Cấu trúc hiển thị khi chọn môn Ngữ văn

Khi quản trị viên chọn **SP2E → Ngữ văn**, hệ thống hiển thị hai phần theo thứ tự của đề minh họa.

| Phần | Dạng bài | Số lượng | Điểm tối đa | Xử lý trong V1 |
|---|---|---|---|---|
| I. Đọc hiểu | Trắc nghiệm 4 lựa chọn, chọn 1 đáp án đúng | 4 văn bản, tổng 30 câu | 6 điểm | Cho phép thiết lập ma trận, sinh đề, làm bài và chấm tự động |
| II. Viết | Viết bài văn nghị luận văn học, khoảng 800 chữ (700–900 chữ) | 1 bài | 4 điểm | Hiển thị ghi chú **“Tự luận – tạm bỏ qua trong V1”**. Quản trị viên không cần thiết lập phần này |

### b. Thiết lập ma trận phần Đọc hiểu

1. Quản trị viên chọn **Thiết lập** tại phần Đọc hiểu. Hệ thống khởi tạo **4 dòng**, tương ứng với 4 văn bản. Mục tiêu của toàn phần là **30 câu, 6 điểm**.
2. Tại mỗi dòng, quản trị viên khai báo **số câu hỏi đi kèm văn bản**. Có thể chọn thêm loại văn bản, phạm vi MAPID hoặc độ khó nếu cần giới hạn nguồn. Tổng số câu của 4 dòng phải bằng 30. Tài liệu không quy định số câu cố định cho từng văn bản nên hệ thống không mặc định chia đều.
3. Khi chọn **Kiểm tra cấu hình**, hệ thống đối chiếu từng dòng với các chùm câu hỏi trong ngân hàng và kiểm tra có thể chọn đủ **4 văn bản khác nhau** cho cùng một đề. Ví dụ, nếu một dòng yêu cầu 8 câu, nguồn phải có chùm gồm một văn bản và đủ 8 câu phù hợp.
4. Khi chọn **Sinh đề thử**, hệ thống lấy ngẫu nhiên 4 chùm thỏa ma trận. Mỗi văn bản hiển thị cùng toàn bộ câu hỏi của chùm đó. Các câu thuộc cùng một văn bản nằm liền nhau. Bản xem thử hiển thị số câu dưới từng văn bản và tổng số câu toàn phần để quản trị viên đối chiếu.

### c. Thời lượng, xuất bản và kết quả trong V1

- **Thời lượng:** 90 phút là thời lượng của **cả hai phần**. Tài liệu chưa nêu thời lượng riêng cho Đọc hiểu, vì vậy quản trị viên khai báo thời lượng của bài Đọc hiểu khi thiết lập.
- **Xuất bản:** Bài triển khai trong V1 hiển thị cho học viên với tên phạm vi **SP2E – Ngữ văn – Đọc hiểu**. Điều kiện xuất bản chỉ kiểm tra cấu hình của 4 văn bản và 30 câu trắc nghiệm. Phần Viết được bỏ qua khi kiểm tra và sinh đề.
- **Kết quả:** Sau khi nộp bài, học viên xem điểm Đọc hiểu trên **thang 6**, ví dụ **4,8/6 điểm**. Phần Viết không phát sinh câu làm bài, điểm 0 hoặc trạng thái chờ chấm.

## 6. Thiết lập ma trận các môn Khoa học tự nhiên

### 6.1. Cấu trúc chung của Vật lí, Hóa học và Sinh học

Khi quản trị viên chọn **SP2E → Bài thi theo môn → Vật lí, Hóa học hoặc Sinh học → Đầy đủ cấu trúc**, hệ thống khởi tạo ba phần theo bảng dưới. Ba môn dùng chung cách chia phần, nhưng mỗi môn sử dụng ngân hàng, MAPID và phân bổ độ khó riêng.

| Phần | Dải câu | Dạng câu hỏi | Số câu | Số lệnh hỏi | Điểm |
|---|---|---|---|---|---|
| I. Trắc nghiệm | 1–10 | Mỗi câu có 4 phương án, chọn 1 đáp án đúng | 10 | 10 | 2 |
| II. Đúng/Sai | 11–15 | Mỗi câu có 4 mệnh đề a–d. Học viên chọn Đúng hoặc Sai cho từng mệnh đề | 5 | 20 | 4 |
| III. Ghép đôi | 16–20 | Mỗi câu có 4 ý a–d ở cột trái và 6 lựa chọn ở cột phải, gồm 2 lựa chọn dư | 5 | 20 | 4 |
| **Tổng** | **1–20** | **60 phút** | **20** | **50** | **10** |

Mỗi câu đúng ở phần I được **0,2 điểm**. Ở phần II và III, mỗi ý đúng được **0,2 điểm**, tối đa **0,8 điểm/câu**. Hệ thống lấy nguyên câu cùng các ý, hình ảnh, bảng số liệu và phương án đi kèm. Với câu ghép đôi, nếu đảo thứ tự lựa chọn ở cột phải, hệ thống phải giữ đúng ánh xạ đáp án của từng ý.

### 6.2. Phân bổ riêng của từng môn

Khi chọn **Dùng ma trận tham khảo**, hệ thống hiển thị đơn vị tính ngay cạnh chỉ tiêu. Điều này đặc biệt quan trọng với Sinh học vì tài liệu phân bổ theo **20 câu**, trong khi Vật lí và Hóa học có bảng phân bổ độ khó theo **50 lệnh hỏi**.

| Môn | Đơn vị tính | Lớp 10 | Lớp 11 | Lớp 12 | Kiến thức tổng hợp |
|---|---|---|---|---|---|
| Vật lí | 50 lệnh hỏi | 0 | 10 (20%) | 40 (80%) | — |
| Hóa học | 50 lệnh hỏi | 4 (8%) | 5 (10%) | 19 (38%) | 22 (44%) |
| Sinh học | 20 câu | 2 (10%) | 4 (20%) | 14 (70%) | — |
| Môn | Đơn vị tính | Nhận biết | Thông hiểu | Vận dụng | Vận dụng cao |
|---|---|---|---|---|---|
| Vật lí | 50 lệnh hỏi | 15 (30%) | 15 (30%) | 14 (28%) | 6 (12%) |
| Hóa học | 50 lệnh hỏi | 25 (50%) | 16 (32%) | 9 (18%) | Mẫu dùng 3 mức |
| Sinh học | 20 câu | 4 (20%) | 9 (45%) | 7 (35%) | Mẫu dùng 3 mức |

**Quy tắc riêng của Hóa học:** 22 lệnh hỏi thuộc nhóm **Kiến thức tổng hợp**. Khi dùng ma trận này, quản trị viên phải chọn được nguồn tương ứng trong ngân hàng. Hệ thống giữ nguyên nhóm tổng hợp khi đối chiếu, tránh dồn 22 lệnh hỏi vào một khối lớp và làm sai phân bổ.

### 6.3. Luồng thao tác tại từng môn

1. Quản trị viên mở môn cần thiết lập. Hệ thống hiển thị ba phần cùng tổng mục tiêu **20 câu, 50 lệnh hỏi, 10 điểm**.
2. Quản trị viên chọn một phần để xem số lượng cần lấy, nguồn câu hợp lệ và cách tính điểm. Nếu chỉ cần lấy ngẫu nhiên theo cấu trúc, có thể giữ phạm vi **Toàn môn**. Nếu cần kiểm soát nội dung, chọn một hoặc nhiều MAPID và nhập số lượng cho từng mạch.
3. Khi bật phân bổ lớp hoặc độ khó, hệ thống hiển thị đơn vị theo mẫu của môn. Với Vật lí, một câu Đúng/Sai có thể đóng góp 4 ý thuộc các mức độ khác nhau. Với mẫu Sinh học theo số câu, câu Đúng/Sai đó chỉ đóng góp **1 câu** vào phân bổ độ khó.
4. Quản trị viên chọn **Kiểm tra phần**. Hệ thống kiểm tra cả dạng câu và cấu trúc bên trong. Ví dụ, phần III yêu cầu 5 câu ghép đôi nhưng ngân hàng chỉ có 4 câu đủ cấu trúc **4 ý trái – 6 lựa chọn phải**, hệ thống báo thiếu 1 câu ghép đôi.
5. Khi chọn **Sinh đề thử**, hệ thống giữ thứ tự ba phần và hiển thị cả **số câu**, **số lệnh hỏi** và **điểm**, để quản trị viên đối chiếu đúng mẫu số.

## 7. Thiết lập các môn Khoa học xã hội và Tiếng Anh theo mạch kiến thức

### 7.1. Quy tắc chọn nguồn từ MAPID

Quy tắc này áp dụng cho **Lịch sử, Địa lí, Giáo dục kinh tế và pháp luật, Tiếng Anh**. Trong cấu hình các môn này, quản trị viên thao tác với các trường sau:

| Trường | Cách sử dụng |
|---|---|
| Phần hoặc nhóm dạng bài | Xác định vị trí sẽ lấy câu, ví dụ *Lịch sử – Đúng/Sai* hoặc *Tiếng Anh – Đọc hiểu 2* |
| Phạm vi kiến thức | Chọn **Toàn môn** hoặc **Theo mạch kiến thức**. Khi chọn theo mạch, hệ thống mở cây MAPID của môn hiện tại |
| Mạch kiến thức/MAPID | Cho phép tìm kiếm và chọn nhiều mã. Nếu chỉ lọc nguồn, câu có ít nhất một mã phù hợp được đưa vào tập có thể chọn |
| Số lượng theo mạch | Chỉ nhập khi cần kiểm soát phân bổ. Hệ thống hiển thị rõ đơn vị là câu hay lệnh hỏi |
| Độ khó | Áp dụng khi ngân hàng có dữ liệu mức độ tương ứng và quản trị viên bật chỉ tiêu này |
| Khối lớp | Không hiển thị trong cấu hình phân bổ, bộ lọc nguồn hoặc bảng kiểm tra của các môn trên |

Một câu gắn nhiều MAPID chỉ xuất hiện một lần trong đề. Nếu quản trị viên đặt hạn ngạch cho hai mạch có nguồn giao nhau, câu được chọn chỉ tính vào một hạn ngạch cùng cấp. Ví dụ, một câu Địa lí gắn cả *Dân cư* và *Biểu đồ – bảng số liệu* vẫn chỉ đóng góp một câu vào tổng phần I.

### 7.2. Môn Lịch sử

Lịch sử là trường hợp cần phân biệt **cấu trúc đề** với **cách phân loại nguồn**. Theo đề minh họa trong tài liệu, môn này cũng có **10 câu trắc nghiệm, 5 câu Đúng/Sai và 5 câu Ghép đôi**, tổng **20 câu, 50 lệnh hỏi, 60 phút, 10 điểm**. Khi thiết lập nguồn, quản trị viên chọn theo giai đoạn, chủ đề hoặc mạch lịch sử.

| Mạch nội dung trong đề minh họa | Số câu | Số lệnh hỏi |
|---|---|---|
| Lịch sử thế giới | 7 | 13 |
| Việt Nam giai đoạn 1930–1975 | 6 | 15 |
| Việt Nam từ 1975 đến nay | 2 | 8 |
| Chủ đề tích hợp, liên hệ | 3 | 6 |
| Chưa xác định nội dung do bản đề thiếu trang | 2 | 8 |
| **Tổng** | **20** | **50** |

Khi thiết lập, quản trị viên chọn từng phần, gắn các mạch MAPID tương ứng và khai báo số lượng nếu cần. Ở phần II, mỗi câu giữ nguyên tư liệu lịch sử cùng 4 mệnh đề. Ở phần III, hệ thống lấy nguyên câu ghép đôi cùng hai cột dữ liệu.

**Hai câu chưa xác định trong bản tham khảo không tạo thành một MAPID.** Khi dùng mẫu này, hệ thống cho phép để hai câu đó lấy trong phạm vi **Toàn môn – Phần II – Đúng/Sai**, hoặc cho quản trị viên chọn mạch cụ thể nếu có căn cứ chuyên môn.

### 7.3. Môn Địa lí

Địa lí sử dụng **28 câu, 40 lệnh hỏi, 60 phút, 10 điểm**. Phần III của môn này là **Trả lời ngắn**. Quản trị viên thiết lập theo mạch nội dung và kỹ năng địa lí.

| Phần | Cấu trúc | Phân bổ mặc định theo mạch kiến thức | Điểm |
|---|---|---|---|
| I. Trắc nghiệm | 16 câu chọn 1 đáp án | Đông Nam Á 2 câu, tự nhiên 2, dân cư 2, ngành kinh tế 5, vùng kinh tế 1, biểu đồ – bảng số liệu 4 | 4 |
| II. Đúng/Sai | 4 câu × 4 ý | Mỗi mạch lấy 1 câu: tự nhiên, dân cư, kinh tế, kỹ năng biểu đồ – bảng số liệu | 4 |
| III. Trả lời ngắn | 8 câu | Tự nhiên 2 câu, dân cư 2 câu, kinh tế 4 câu | 2 |

Khi mở phần II, hệ thống hiển thị **4 dòng mạch kiến thức**. Theo ma trận tham khảo, mỗi dòng yêu cầu 1 câu có **2 ý Nhận biết, 1 ý Thông hiểu và 1 ý Vận dụng**. Hệ thống kiểm tra được yêu cầu này khi ngân hàng đã phân loại độ khó đến từng ý. Các tiêu chí về lớp xuất hiện trong tài liệu tham khảo không được chuyển thành điều kiện lọc câu của hệ thống.

### 7.4. Môn Giáo dục kinh tế và pháp luật

Môn này có **32 câu, 50 lệnh hỏi, 60 phút, 10 điểm**. Quản trị viên chọn nguồn theo hai mạch chính **Kinh tế** và **Pháp luật**, sau đó có thể mở các MAPID cấp dưới để giới hạn chuyên đề.

| Phần | Cấu trúc | Cách thiết lập |
|---|---|---|
| I. Trắc nghiệm | 26 câu chọn 1 đáp án | Cho phép kết hợp câu độc lập và chùm tình huống. Nếu dùng bố cục của đề tham khảo mã 473, khởi tạo 20 câu độc lập và 2 chùm × 3 câu |
| II. Đúng/Sai | 6 câu × 4 ý | Mỗi câu gồm một tình huống hoặc đoạn dữ liệu và 4 nhận định. Hệ thống chọn nguyên tình huống cùng các nhận định |

Trong đề tham khảo, mạch **Kinh tế có 27/50 lệnh hỏi**, mạch **Pháp luật có 23/50 lệnh hỏi**. Đây là phân bổ quan sát từ một đề. Quản trị viên có thể giữ làm số liệu đối chiếu hoặc chủ động chọn **Áp dụng phân bổ này** để chuyển thành hạn ngạch sinh đề.

### 7.5. Môn Tiếng Anh

Tiếng Anh dùng hai tiêu chí song song: **dạng bài** xác định thứ tự và số câu, còn **MAPID** xác định kiến thức hoặc kỹ năng cần lấy trong từng dạng bài. Chẳng hạn, *Đọc hiểu 2* là nhóm dạng bài, còn *Suy luận* và *Ý chính văn bản* là các mạch có thể gắn vào vị trí cụ thể trong nhóm.

| Thứ tự | Nhóm dạng bài | Dải câu | Số câu |
|---|---|---|---|
| 1 | Đọc điền câu | 1–10 | 10 |
| 2 | Câu đồng nghĩa | 11–14 | 4 |
| 3 | Kết hợp câu | 15–18 | 4 |
| 4 | Đọc điền từ vào đoạn văn | 19–23 | 5 |
| 5 | Đọc điền cụm từ, mệnh đề hoặc câu vào đoạn văn | 24–28 | 5 |
| 6 | Giao tiếp | 29–32 | 4 |
| 7 | Sắp xếp hội thoại hoặc đoạn văn | 33–37 | 5 |
| 8 | Đọc hiểu 1 | 38–42 | 5 |
| 9 | Đọc hiểu 2 | 43–50 | 8 |
|  | **Tổng** | **1–50** | **50** |

Quản trị viên chọn một trong hai cách thiết lập cho từng nhóm:

- **Theo nhóm:** Chọn dạng bài và các MAPID được phép lấy. Hệ thống sinh đủ số câu trong nhóm. Cách này phù hợp khi chỉ cần đúng dạng và phạm vi kiến thức.
- **Theo vị trí:** Chọn vị trí hoặc dải vị trí, sau đó gắn MAPID tương ứng. Ví dụ, trong nhóm *Đọc hiểu 2*, câu 48–49 yêu cầu kỹ năng **Suy luận**, câu 50 yêu cầu **Xác định ý chính văn bản**. Hệ thống phải chọn một ngữ liệu có đủ các câu con đáp ứng các vị trí đó.

Khi sinh đề, hệ thống giữ thứ tự 9 nhóm, giữ ánh xạ giữa chỗ trống và phương án ở bài đọc điền, giữ ngữ liệu cùng các câu con ở bài đọc hiểu. Ma trận Tiếng Anh được cung cấp không phân mức độ cho từng câu, nên cấu hình mặc định dùng **dạng bài và MAPID**. Quản trị viên chỉ bật thêm hạn ngạch độ khó khi nguồn ngân hàng đã có dữ liệu phù hợp.

## 8. Luồng rút gọn cho bài thi theo phần và ôn theo chủ đề

**Bài thi theo phần.** Quản trị viên chọn môn và phần cần luyện. Hệ thống sao chép các dòng ma trận thuộc đúng phần đó, cho phép giữ hoặc sửa số câu, thời lượng, thang điểm. Ví dụ, chọn **Địa lí → Phần II – Đúng/Sai** sẽ mở cấu hình 4 câu × 4 ý. Nếu giữ cách chấm gốc, điểm tối đa của bài luyện là **4 điểm**. Nếu muốn công bố theo thang 10, quản trị viên phải xác nhận quy tắc quy đổi và xem trước kết quả hiển thị.

**Bài ôn theo chủ đề.** Ví dụ, khi tạo *SP2E – Toán – Luyện đồ thị hàm số*, luồng thao tác như sau:

1. Chọn **SP2E → Toán → Ôn theo chủ đề**, nhập mục tiêu **10 câu, 20 phút**.
2. Chọn MAPID **Đồ thị hàm số**. Hệ thống hiển thị nguồn câu phù hợp theo từng dạng.
3. Chọn **7 câu trắc nghiệm một đáp án** và **3 câu trả lời ngắn**. Nếu cần kiểm soát mức độ, nhập phân bổ, ví dụ 3 TH, 5 VD và 2 VDC.
4. Chọn **Kiểm tra cấu hình**. Nếu nguồn chỉ có 1 câu VDC hợp lệ, hệ thống báo *“Đồ thị hàm số cần 2 câu Vận dụng cao, hiện có 1 câu phù hợp”* và mở đúng dòng cần sửa.
5. Khi kiểm tra đạt, chọn **Sinh đề thử**. Hệ thống hiển thị đề 10 câu theo cấu hình bài ôn.

## 9. Kiểm tra, sinh thử, xuất bản và đóng bộ để bán

### 9.1. Kiểm tra và sinh đề thử

| Nội dung kiểm tra | Phản hồi cần hiển thị |
|---|---|
| Đủ cấu trúc | So sánh số phần, số nhóm, số câu, số lệnh hỏi và tổng điểm với mục tiêu đã lưu. Ví dụ, *“Tiếng Anh – Đọc hiểu 2 đã phân bổ 7/8 câu”*. |
| Đủ nguồn để ghép toàn bài | Kiểm tra đồng thời các hạn ngạch, các nguồn giao nhau và quy tắc không trùng câu. Hai dòng cùng nhìn thấy một câu trong ngân hàng chỉ có thể sử dụng câu đó một lần. |
| Đúng quan hệ giữa câu và ngữ liệu | Kiểm tra số câu con, số ý, thứ tự và dữ liệu đi kèm. Ví dụ, *“Địa lí – Phần II cần một câu kỹ năng có đủ 4 ý theo phân bổ 2 NB, 1 TH, 1 VD”*. |
| Có thể làm và chấm trên web/app | Xác nhận từng dạng có cách hiển thị, ghi nhận câu trả lời, đáp án và quy tắc chấm phù hợp. Nêu cụ thể phần cần xử lý nếu chưa thể công bố đủ điểm. |
| Phân biệt lỗi và cảnh báo | Thiếu chỉ tiêu bắt buộc hoặc thiếu nguồn để sinh đề là lỗi chặn xuất bản. Chênh lệch với một tỷ lệ chỉ dùng tham khảo là cảnh báo để quản trị viên xem xét. |

**Sinh đề thử** tạo một đề cụ thể bằng cùng bộ sinh đề của lượt thật. Quản trị viên xem được ngữ liệu, câu con, thứ tự, điểm và bảng **Yêu cầu / Thực tế**. Có thể sinh lại để kiểm tra việc thay câu giữa các lượt. Một lần sinh thử thành công không thay thế bước kiểm tra cấu hình. Khi sửa phần nào, hệ thống yêu cầu kiểm tra lại các chỉ tiêu bị ảnh hưởng trước khi xuất bản.

### 9.2. Xuất bản và thiết lập bán

- **Bán riêng từng bài.** Quản trị viên chọn bài đã xuất bản, khai báo giá, số lượt, thời hạn và voucher dành cho học viên nội bộ nếu áp dụng.
- **Đóng bộ theo môn.** Quản trị viên chọn **Tạo bộ đề SP2E**, tích các môn rồi xác nhận bài tương ứng của từng môn. Khi một môn có nhiều bài đã xuất bản, hệ thống hiển thị tên bài, số câu và thời lượng để chọn đúng. Bộ có giá bán riêng và hiển thị quyền lợi theo từng môn.
- **Sử dụng sau khi mua.** Học viên mở bộ đề và chủ động chọn môn. Mỗi bài có đồng hồ, lượt làm, thao tác tiếp tục và kết quả riêng. Nộp bài Toán kết thúc lượt Toán. Các môn còn lại chỉ bắt đầu khi học viên chủ động chọn làm.
- **Bảo toàn cấu hình và quyền đã cấp.** Mỗi lượt thật lưu đề, ngữ liệu, thứ tự, đáp án và quy tắc điểm tại lúc bắt đầu. Sửa ma trận sau đó không thay đổi lượt đang làm hoặc kết quả cũ. Thay đổi thành phần bộ hoặc số lượt bán áp dụng cho đơn mua mới.

## 10. Phạm vi bổ sung cho đề SPT

Khi quản trị viên chọn **Kỳ thi → SPT**, hệ thống sử dụng luồng tạo bài thi, chọn môn, khai báo MAPID, kiểm tra nguồn, sinh đề thử và thiết lập bán đã mô tả tại mục 1–9. Các mục dưới đây quy định riêng **cấu trúc từng môn, cách lấy câu và quy tắc tính điểm của SPT** theo đề tham khảo mã 075.

Trong V1, quản trị viên nhìn thấy đầy đủ cấu trúc gốc của môn. Các phần viết bài, trình bày lập luận hoặc trình bày quá trình giải được đánh dấu **“Tự luận – tạm bỏ qua trong V1”**. Phần trả lời ngắn chỉ yêu cầu kết quả cuối vẫn được đưa vào bài thi khi ngân hàng có đáp án mà hệ thống có thể chấm tự động.

## 11. Cấu trúc khởi tạo theo môn

Khi chọn **SPT → Bài thi theo môn**, hệ thống điền sẵn các phần theo đúng thứ tự trong bảng. Mỗi câu Đúng/Sai gồm 4 ý. Cột **Số câu V1** tính một câu Đúng/Sai là một câu, dù học viên phải trả lời cả bốn ý.

| Môn | Các phần thực hiện trong V1, theo thứ tự | Phần tự luận tạm bỏ qua | Số câu V1 | Điểm tối đa V1 | Thời lượng đề gốc |
|---|---|---|---|---|---|
| Toán | 16 trắc nghiệm + 2 Đúng/Sai + 4 trả lời ngắn | 3 câu tự luận | 22 | 7 | 90 phút |
| Vật lí | 12 trắc nghiệm + 3 Đúng/Sai + 4 trả lời ngắn | 2 câu tự luận | 19 | 7 | 60 phút |
| Hóa học | 20 trắc nghiệm + 2 Đúng/Sai + 4 trả lời ngắn | 1 câu tự luận gồm 4 ý | 26 | 8 | 60 phút |
| Ngữ văn | 10 trắc nghiệm đọc hiểu, gồm 2 ngữ liệu × 5 câu | 1 đoạn nghị luận xã hội và 1 bài nghị luận văn học | 10 | 3 | 90 phút |
| Tiếng Anh | 18 câu *Use of English* + 10 câu *Reading* + 4 câu *Word Formation* | 1 bài *Writing* | 32 | 8 | 60 phút |
| Sinh học | 12 trắc nghiệm + 3 Đúng/Sai + 4 trả lời ngắn | 3 câu tự luận | 19 | 7 | 60 phút |
| Lịch sử | 20 trắc nghiệm + 2 Đúng/Sai | 3 câu tự luận | 22 | 7 | 60 phút |
| Địa lí | 12 trắc nghiệm + 3 Đúng/Sai + 4 trả lời ngắn | 1 câu tự luận | 19 | 7 | 60 phút |
| Tin học | 20 trắc nghiệm + 2 Đúng/Sai theo định hướng học viên chọn | 3 câu tự luận học viên phải làm theo định hướng | 22 | 7 | 60 phút |
| Giáo dục kinh tế và pháp luật | 16 trắc nghiệm + 3 Đúng/Sai | 2 câu tự luận | 19 | 7 | 60 phút |
| Công nghệ – định hướng công nghiệp | 16 trắc nghiệm + 2 Đúng/Sai + 4 trả lời ngắn | 2 câu tự luận | 22 | 7 | 60 phút |
| Công nghệ – định hướng nông nghiệp | 16 trắc nghiệm + 2 Đúng/Sai + 4 trả lời ngắn | 2 câu tự luận | 22 | 7 | 60 phút |

**Thời lượng làm bài V1** do quản trị viên xác nhận khi thiết lập. Các mốc 60 và 90 phút trong tài liệu dành cho đề đầy đủ, bao gồm thời gian làm tự luận. Danh mục bán và màn chuẩn bị thi hiển thị số câu, thời lượng và điểm tối đa của bài học viên thực sự được làm.

## 12. Thiết lập ma trận và nguồn câu theo môn

### 12.1. Các môn thiết lập theo phần và dạng trả lời

Áp dụng cho Toán, Vật lí, Hóa học, Sinh học, Lịch sử, Địa lí, Giáo dục kinh tế và pháp luật cùng hai định hướng Công nghệ.

1. Quản trị viên mở **Cấu trúc chi tiết**. Hệ thống hiển thị các phần của môn, số câu mục tiêu và điểm tối đa từng phần. Ví dụ, Vật lí có ba phần triển khai trong V1, lần lượt là **12 câu trắc nghiệm – 3 điểm**, **3 câu Đúng/Sai – 3 điểm** và **4 câu trả lời ngắn – 1 điểm**.
2. Tại mỗi phần, quản trị viên chọn **Toàn môn** hoặc giới hạn nguồn theo một hay nhiều MAPID. Nếu cần kiểm soát số lượng theo mạch kiến thức hoặc độ khó, quản trị viên thêm chỉ tiêu ngay trong phần đó.
3. Đối với **Lịch sử, Địa lí và Giáo dục kinh tế và pháp luật**, hệ thống lọc theo mạch kiến thức trong MAPID. Các tỷ lệ lớp nêu trong tài liệu tham khảo chỉ phục vụ đối chiếu. Câu thuộc đúng mạch vẫn được chọn khi ngân hàng không phân lớp 10/11/12.
4. Với phần **trả lời ngắn**, hệ thống kiểm tra cả kiểu đáp án. Ví dụ, Toán có câu yêu cầu phân số hoặc làm tròn đến hàng phần trăm. Hóa học có thể yêu cầu tên chất, công thức hoặc phương trình. Chỉ đưa vào nguồn V1 những câu có định dạng nhập và đáp án đối sánh phù hợp.
5. Khi tài liệu cho tỷ lệ theo **điểm toàn đề**, hệ thống giữ rõ mẫu số 10 điểm. Nếu quản trị viên dùng tỷ lệ làm chỉ tiêu sinh đề V1, hệ thống phải tính lại trên các phần đang triển khai. Ví dụ, một chuyên đề Hóa học nằm hoàn toàn trong câu tự luận 2 điểm sẽ không tạo ra hạn ngạch tương ứng trong bài V1 có thang 8.

### 12.2. Ngữ văn – Thiết lập hai chùm đọc hiểu

Phần Đọc hiểu SPT khởi tạo **2 chùm**, mỗi chùm gồm **1 ngữ liệu và 5 câu trắc nghiệm**. Quản trị viên có thể chọn loại văn bản và MAPID riêng cho từng chùm. Đề tham khảo sử dụng một văn bản khoa học và một văn bản thông tin.

Khi sinh đề, hệ thống lấy hai ngữ liệu khác nhau, giữ 5 câu con đi liền với từng ngữ liệu và chấm **0,3 điểm/câu**. Bài V1 hiển thị cho học viên là **SPT – Ngữ văn – Đọc hiểu**, gồm **10 câu, tối đa 3 điểm**.

### 12.3. Tiếng Anh – Thiết lập ba phần triển khai trong V1

| Phần | Cấu trúc lấy câu | Quy tắc sinh đề |
|---|---|---|
| I. Use of English | 5 câu đơn + 2 đoạn văn điền từ × 6 câu + 1 câu sắp xếp hội thoại | Giữ thứ tự các nhóm. Hai đoạn điền từ dùng dạng chọn phương án, mỗi vị trí trống gắn với đúng bộ lựa chọn của vị trí đó |
| II. Reading | 2 bài đọc × 5 câu | Lấy nguyên bài đọc cùng 5 câu con. Có thể giới hạn MAPID theo kỹ năng đọc |
| III. Word Formation | 1 đoạn văn có 4 vị trí điền dạng đúng của từ cho sẵn | Học viên tự nhập từ. Mỗi vị trí có đáp án riêng trong ngân hàng và được chấm độc lập |

Quản trị viên chọn nguồn theo **dạng bài và mạch kiến thức/MAPID**. Khi kiểm tra, hệ thống phải phân biệt câu **điền từ bằng cách chọn phương án** ở phần I với câu **tự nhập từ** ở phần III. Một câu cùng MAPID nhưng khác cách trả lời không đủ điều kiện thay thế.

### 12.4. Tin học – Thiết lập phần chung và hai định hướng

Tin học là môn cần bổ sung thao tác chọn nhánh. Quản trị viên cấu hình cả hai định hướng để học viên có thể chọn khi thi.

| Thành phần | Quản trị viên thiết lập | Số câu một học viên làm trong V1 |
|---|---|---|
| Phần I – Trắc nghiệm chung | 20 câu dùng cho cả hai định hướng | 20 |
| Phần II – Đúng/Sai chung | 1 câu gồm 4 ý | 1 |
| Phần II – Đúng/Sai theo định hướng | 1 nguồn câu cho **Khoa học máy tính** và 1 nguồn câu cho **Tin học ứng dụng**. Mỗi nhánh yêu cầu 1 câu gồm 4 ý | 1 |
| **Tổng theo định hướng đã chọn** | **20 câu trắc nghiệm + 1 câu Đúng/Sai chung + 1 câu Đúng/Sai riêng** | **22** |

Đề xuất đặt thao tác **Chọn định hướng** tại màn chuẩn bị thi. Khi học viên xác nhận bắt đầu, hệ thống sinh phần chung và đúng một nhánh đã chọn, sau đó lưu định hướng vào lượt thi. Khi học viên tiếp tục lượt đang làm, hệ thống khôi phục cùng nhánh và cùng bộ câu. Khi dùng thử, quản trị viên kiểm tra độc lập cả hai định hướng.

Hai định hướng **Công nghệ công nghiệp** và **Công nghệ nông nghiệp** được chọn ngay khi quản trị viên tạo bài thi theo môn. Mỗi định hướng có nguồn MAPID riêng và không phát sinh bước chọn nhánh bên trong lượt thi như môn Tin học.

## 13. Quy tắc chấm điểm SPT

| Dạng trả lời | Quy tắc áp dụng |
|---|---|
| Trắc nghiệm chọn một đáp án | Mặc định **0,25 điểm/câu đúng** theo các mẫu SPT trong bảng. Riêng trắc nghiệm đọc hiểu Ngữ văn là **0,3 điểm/câu đúng** |
| Đúng/Sai có 4 ý | Chấm theo **tổng số ý đúng trong từng câu**. Đúng 0 ý = 0 điểm, 1 ý = 0,1 điểm, 2 ý = 0,25 điểm, 3 ý = 0,5 điểm, 4 ý = 1 điểm |
| Trả lời ngắn và Word Formation | **0,25 điểm** cho mỗi đáp án đúng theo quy tắc đối sánh đã khai báo trong ngân hàng |
| Tự luận tạm bỏ qua | Không tạo câu làm bài, không tính vào điểm tối đa V1 và không phát sinh trạng thái chờ chấm |

**Band Đúng/Sai được áp dụng riêng cho từng câu.** Ví dụ, học viên làm hai câu Đúng/Sai, mỗi câu đúng 2/4 ý, được `0,25 + 0,25 = 0,5 điểm`. Nếu đúng cả 4 ý ở câu thứ nhất và sai cả 4 ý ở câu thứ hai, điểm là `1 + 0 = 1 điểm`. Hai trường hợp có cùng tổng số ý đúng nhưng cho điểm khác nhau. Màn dùng thử cần hiển thị điểm từng câu để quản trị viên kiểm tra được quy tắc này.

## 14. Kiểm tra, dùng thử và xuất bản bài SPT

| Nội dung kiểm tra | Yêu cầu |
|---|---|
| Phạm vi V1 | Chỉ yêu cầu đủ nguồn và đáp án ở các phần học viên thực hiện. Phần tự luận đã đánh dấu tạm bỏ qua không chặn xuất bản |
| Số câu và thang điểm | Đối chiếu theo từng môn tại mục 11. Ví dụ, Vật lí phải sinh đủ **19 câu, tối đa 7 điểm**. Ngữ văn phải sinh đủ **2 ngữ liệu, 10 câu, tối đa 3 điểm** |
| Quan hệ ngữ liệu và câu con | Kiểm tra đủ số chùm, số câu con và ánh xạ vị trí trống. Nếu Tiếng Anh có đủ 10 câu đọc hiểu nhưng chỉ ghép được một bài đọc hợp lệ, hệ thống báo thiếu tại phần Reading |
| Định hướng Tin học | Sinh thử được cả **Khoa học máy tính** và **Tin học ứng dụng**. Kết quả kiểm tra từng nhánh hiển thị riêng |
| Chấm thử và công bố kết quả | Dùng đáp án, band điểm và thang điểm đã xác nhận cho phiên bản đề. Học viên nhận kết quả ngay sau khi nộp, theo điểm tối đa của phần đã làm |
| Bán riêng hoặc đóng bộ | Áp dụng luồng thương mại tại mục 9. Nếu bán nhiều môn SPT trong một bộ, mỗi môn giữ cấu trúc, thời lượng, lượt làm và kết quả độc lập |
