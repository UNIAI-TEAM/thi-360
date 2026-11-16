# Ngân hàng câu hỏi

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-16 11:22, lấy về ngày 2026-11-16. Nguồn: uniworkos, thư mục "Tài liệu Thi 360". Chỉ đọc; bản gốc ở uniworkos.

## 1. Thông tin chung

### 1.1. Mục tiêu

Tái sử dụng chức năng ngân hàng câu hỏi hiện có để quản lý nội dung phục vụ thi và luyện tập HSA, QDA.

Mỗi câu hỏi được khai báo nội dung, đáp án, lời giải và thông tin phân loại, bao gồm môn học, khối lớp, độ khó, MAPID và các nhãn bổ sung. Hệ thống sử dụng các thông tin này để lựa chọn câu hỏi theo ma trận đề và phân tích kết quả học tập.

### 1.2. Tác nhân

Platform Admin hoặc người dùng được phân quyền quản lý ngân hàng câu hỏi.

### 1.3. Phạm vi

- Quản lý danh sách và tìm kiếm câu hỏi.
- Tạo, chỉnh sửa và xem trước bốn dạng câu hỏi được hỗ trợ.
- Khai báo thông tin phân loại phục vụ ma trận đề.
- Quản lý ngữ liệu chung và câu hỏi con.
- Nhập câu hỏi theo mẫu.
- Quản lý trạng thái và phiên bản câu hỏi.
- Cung cấp nguồn câu hỏi cho chức năng thiết lập và sinh đề.

Ranh giới chức năng: Ngân hàng quản lý câu hỏi và thông tin phân loại. Số lượng câu, tỷ lệ phân bổ và quy tắc lựa chọn được cấu hình tại ma trận của từng đề thi.

## 2. Các dạng câu hỏi được hỗ trợ

Màn hình lựa chọn loại câu hỏi trong phạm vi HSA/QDA hiển thị bốn dạng sau:

| Dạng câu hỏi | Mô tả | Thông tin cần khai báo |
|---|---|---|
| Trắc nghiệm | Học viên chọn một đáp án đúng trong các lựa chọn | Nội dung câu hỏi, danh sách lựa chọn, một đáp án đúng và lời giải |
| Cloze — Điền khuyết | Ngữ liệu có các vị trí khuyết; học viên trả lời tại từng vị trí | Ngữ liệu, danh sách vị trí khuyết, kiểu trả lời được hỗ trợ, đáp án và lời giải cho từng vị trí |
| Short Answer — Trả lời ngắn | Học viên nhập đáp án cuối cùng cho câu hỏi | Nội dung câu hỏi, đáp án chuẩn, quy tắc đối chiếu và lời giải |
| Question Set — Bộ câu hỏi | Một ngữ liệu chung đi kèm nhiều câu hỏi con | Ngữ liệu, danh sách câu con, thứ tự, đáp án và lời giải của từng câu |

Quy tắc:

- Với Trắc nghiệm, phạm vi hiện tại là chọn một đáp án đúng.
- Với Short Answer, học viên chỉ nhập đáp án, không nhập toàn bộ bài giải.
- Với Cloze, học viên nhập đáp án, khớp với đáp án tại ô trống cho sẵn hoặc các acceptance .
- Question Set sử dụng các dạng câu con đã được hỗ trợ; không tạo bộ câu hỏi lồng trong bộ câu hỏi.

## 3. Danh sách ngân hàng câu hỏi

### 3.1. Thông tin hiển thị

| Thông tin | Mô tả |
|---|---|
| Mã câu hỏi | Mã định danh của câu hỏi hoặc bộ câu hỏi, sinh tự động không cần hiển thị |
| Nội dung | Nội dung rút gọn hoặc tên bộ câu hỏi |
| Môn học | Môn học được gán |
| Khối lớp | Khối lớp dùng để phân bổ ma trận |
| Độ khó | Nhận biết, Thông hiểu, Vận dụng hoặc Vận dụng cao |
| Điểm | Mặc định là 1 đối với câu hỏi độc lập, Question Set thì là tổng của các câu hỏi con, mỗi câu hỏi con là 1 điểm. Cloze nếu có nhiều ô trống thì cũng đc coi là 1 Question set |
| MAPID | Tên kiến thức, hiển thị toàn bộ kiến thức theo MAPID được gắn |
| Trạng thái | Đã công bố, Chờ duyệt, Bản nháp |
| Người tạo | Người tạo câu hỏi |
| Thao tác | Xem, chỉnh sửa, xem trước và các thao tác theo quyền |
| Dạng câu hỏi | Hiển thị dạng bài của câu hỏi đc tạo |
|  |  |

### 3.2. Tìm kiếm và lọc

Hệ thống hỗ trợ tìm kiếm theo mã, nội dung và lọc kết hợp nâng cao theo:

- Môn học.
- Khối lớp.
- Độ khó.
- Dạng câu hỏi.
- MAPID.
- Nhãn bổ sung.
- Trạng thái.
- Người tạo
- Lời giải (checkbox có lời giải hay chưa)
- Thời gian tạo từ ngày - đến ngày

Kết quả hỗ trợ phân trang và hiển thị thông báo khi không tìm thấy dữ liệu.

## 4. Thông tin khai báo câu hỏi

### 4.1. Thông tin trên biểu mẫu

Khi thêm mới, người dùng chọn một trong bốn dạng: Trắc nghiệm, Cloze, Short Answer hoặc Question Set. Hệ thống hiển thị phần nhập nội dung và câu trả lời tương ứng, kèm các thông tin phân loại bên dưới.

| Trường thông tin | Yêu cầu | Quy tắc |
|---|---|---|
| Nội dung câu hỏi/ngữ liệu | Bắt buộc | Nhập theo dạng câu hỏi; hỗ trợ văn bản, công thức, hình ảnh và bảng |
| Câu trả lời và đáp án đúng | Bắt buộc | Khai báo theo dạng câu hỏi đã chọn; mỗi câu con hoặc vị trí trả lời phải có đáp án tương ứng |
| Môn học | Bắt buộc | Chọn một môn từ danh mục dùng chung |
| Khối lớp | Bắt buộc | Chọn một khối lớp để phân bổ câu hỏi theo ma trận |
| Độ khó | Bắt buộc | Chọn một mức độ từ danh mục dùng chung |
|  |  |  |
| MAPID | Không bắt buộc | Cho phép chọn nhiều giá trị từ danh mục dùng chung |
| Điểm | Có giá trị mặc định | Mặc định 1 điểm cho mỗi câu đơn hoặc câu con; cho phép điều chỉnh thành số lớn hơn 0 |
| Lời giải | Không bắt buộc | Nhập nội dung giải thích đáp án |
| Lỗi sai thường gặp | Không bắt buộc | Mô tả cách hiểu sai, nhầm lẫn hoặc lỗi học viên thường mắc; có thể làm dữ liệu tham khảo cho AI giải thích |
| Đính kèm | Không bắt buộc | Tải lên tài liệu hoặc tài nguyên bổ trợ theo loại tệp và dung lượng hệ thống hỗ trợ |
| Nhãn bổ sung | Không bắt buộc | Chọn các nhãn phục vụ phân loại và tìm kiếm |

### 4.2. Quy tắc đối với Question Set và Cloze

- Ngữ liệu chung được khai báo một lần.
- Mỗi câu con hoặc vị trí trả lời được chấm độc lập có đáp án, điểm.
- Cho phép nhập phân loại chung và áp dụng cho tất cả câu con
- Mỗi câu con mặc định 1 điểm.
- Điểm của Question Set hoặc Cloze được hệ thống tự tính bằng tổng điểm các câu con, hiển thị chỉ đọc; không cộng thêm điểm cho ngữ liệu chung.

Ví dụ: Question Set có 5 câu con, mỗi câu 1 điểm thì tổng điểm của bộ là 5 điểm.

### 4.3. Quy tắc kiểm tra và sử dụng trong ma trận

- Khi lưu, hệ thống kiểm tra nội dung, đáp án và các trường phân loại bắt buộc; thông báo lỗi ngay tại trường chưa hợp lệ.
- Các trường được cấu hình làm tiêu chí lựa chọn trong ma trận phải có dữ liệu trước khi câu hỏi được sử dụng cho ma trận đó.
- Câu hỏi thiếu thông tin của một tiêu chí không được đưa vào nguồn lựa chọn theo tiêu chí đó.
- Một câu gắn nhiều MAPID vẫn chỉ được chọn và tính một lần trong cùng đề.
- Với Question Set và Cloze, số câu, điểm và phân bổ khối lớp/độ khó được tính theo câu cha
- Đáp án, lời giải và lỗi sai thường gặp không được hiển thị trong lúc thi; việc công bố thực hiện theo cấu hình xem kết quả.

Lưu ý về MAPID: Chọn nhiều MAPID phù hợp để phân loại. Tuy nhiên, nếu ma trận quy định số câu riêng cho từng MAPID, hệ thống phải phân bổ mỗi câu vào một chỉ tiêu khi sinh đề, tránh dùng cùng một câu để đồng thời đáp ứng nhiều chỉ tiêu.

## 5. Phân loại theo MAPID và nhãn bổ sung

### 5.1. Khai báo MAPID

MAPID được sử dụng để xác định kiến thức của câu hỏi và hỗ trợ lựa chọn câu theo chủ đề.

Hệ thống cho phép:

- Tìm kiếm MAPID theo mã hoặc tên.
- Chọn MAPID từ cây kiến thức.
- Gán một hoặc nhiều MAPID liên quan.

Mỗi MAPID cần xác định được bộ nguồn, môn học, mã và quan hệ cha–con. Các mã trùng tên ở hai bộ nguồn không được tự động gộp thành một.

### 5.2. Nhãn bổ sung

Nhãn bổ sung dùng để phục vụ quản lý nội dung, chẳng hạn nguồn câu hỏi hoặc đặc điểm phân loại khác.

MAPID và nhãn bổ sung cần được tách biệt: Nhãn có thể linh hoạt, còn MAPID phải có cấu trúc và định danh ổn định để sinh đề, thống kê điểm mạnh/yếu và gợi ý luyện tập.

### 5.3. Liên kết danh mục dùng chung

- Môn học, khối lớp, mức độ, dạng bài và nhãn lấy từ danh mục đã cấu hình.
- Chỉ cho phép chọn mới các giá trị đang hoạt động.
- Giá trị ngừng sử dụng vẫn hiển thị tại dữ liệu cũ.
- Việc thay đổi danh mục không tự làm thay đổi phân loại đã chốt trong lượt thi và kết quả lịch sử.

## 6. Quản lý Cloze và Question Set

### 6.1. Ngữ liệu và câu hỏi con

Cloze và Question Set được quản lý theo cấu trúc gồm ngữ liệu chung và các câu con/vị trí trả lời.

Mỗi câu con hoặc vị trí trả lời có:

- Định danh riêng.
- Nội dung hoặc vị trí trong ngữ liệu.
- Đáp án và lời giải.

### 6.2. Quy tắc tính số câu

- Một câu trắc nghiệm hoặc Short Answer độc lập được tính là một câu.
- Question Set được tính theo số câu con.
- Cloze được tính theo các vị trí trả lời được cấu hình chấm độc lập.
- Ngữ liệu chung không được tính thêm là một câu.

Ví dụ: Một bộ đọc hiểu có năm câu con, gồm ba câu Nhận biết và hai câu Thông hiểu, đóng góp năm câu vào ma trận với đúng phân bổ đó.

### 6.3. Quy tắc lựa chọn và thứ tự

- Khi chọn một bộ câu hỏi, hệ thống lấy nguyên bộ theo cấu hình; không tự cắt câu con để bù đủ số lượng.
- Các câu con thuộc cùng bộ phải hiển thị liền nhau.
- Câu con thuộc bộ không được đồng thời lấy thêm như câu độc lập trong cùng đề.
- Với môn Văn, chỉ đảo vị trí các nhóm khi cấu hình đề cho phép; giữ các câu trong nhóm liền nhau.
- Với Tiếng Anh, giữ thứ tự các khối dạng bài theo cấu trúc đề.
- Câu có lựa chọn phụ thuộc vị trí hoặc nội dung như “cả A và B” phải được tắt đảo đáp án.
- Nếu không thể đáp ứng ma trận bằng các bộ nguyên vẹn, hệ thống báo thiếu nguồn hoặc không có tổ hợp phù hợp; không tự phá cấu trúc bộ.

## 7. Tạo và cập nhật câu hỏi

### 7.1. Tạo mới

1. Người dùng chọn Thêm câu hỏi.
2. Chọn một trong bốn dạng.
3. Hệ thống mở trình soạn thảo tương ứng.
4. Người dùng nhập nội dung, đáp án và lời giải.
5. Khai báo thông tin phân loại.
6. Với Cloze hoặc Question Set, bổ sung các câu con và kiểm tra phân loại từng câu.
7. Chọn Lưu nháp.
8. Hệ thống kiểm tra và lưu dữ liệu.

### 7.2. Điều kiện chuyển sang Sẵn sàng

- Đầy đủ nội dung bắt buộc.
- Có đáp án hợp lệ cho mọi câu được chấm.
- Đầy đủ khối lớp, độ khó và MAPID chính.
- Các giá trị phân loại hợp lệ và phù hợp với môn học.
- Không có câu con hoặc vị trí khuyết thiếu đáp án.
- Tài nguyên hình ảnh, công thức và ngữ liệu có thể hiển thị.
- Cấu trúc câu hỏi thuộc phạm vi được hỗ trợ.

Nếu chưa đáp ứng, hệ thống chỉ cho phép lưu nháp và chỉ rõ vị trí cần bổ sung.

### 7.3. Cập nhật

- Câu hỏi chưa được sử dụng được chỉnh sửa theo quyền.
- Câu hỏi đã được sử dụng phải tạo phiên bản mới khi thay đổi nội dung, đáp án hoặc phân loại ảnh hưởng tới ma trận và kết quả.
- Phiên bản mới phải được kiểm tra trước khi chuyển sang Sẵn sàng.
- Việc cập nhật không thay thế câu hỏi hoặc đáp án trong lượt thi đang diễn ra.
- Kết quả lịch sử giữ nguyên phiên bản đã sử dụng.

## 8. Đáp án, lời giải và xem trước

### 8.1. Trắc nghiệm

- Phải có đủ lựa chọn theo mẫu được hỗ trợ.
- Có đúng một đáp án đúng.
- Khi đảo lựa chọn, hệ thống giữ đúng liên kết giữa lựa chọn và đáp án.

### 8.2. Short Answer

- Khai báo đáp án chuẩn để đối chiếu, cho phép thêm các Accepted answer.
- Chấm theo quy tắc khớp đáp án đã cấu hình, không dùng AI tự quyết định điểm.
- Hiển thị hướng dẫn nhập đáp án cho học viên.
- Quy ước số thập phân sử dụng dấu phẩy và phân số sử dụng dạng tối giản.
- Không tự chấp nhận các cách biểu diễn tương đương.

### 8.3. Cloze

- Mỗi vị trí khuyết phải liên kết đúng với đáp án tương ứng.
- Không cho phép vị trí khuyết thiếu đáp án hoặc đáp án không gắn với vị trí nào.
- Khai báo đáp án chuẩn cho từng vị trí để đối chiếu, cho phép thêm các Accepted answer.

### 8.4. Lời giải và AI

- Lời giải chuẩn được lưu cùng phiên bản câu hỏi.
- Chức năng AI có thể sử dụng câu hỏi, đáp án, lời giải và MAPID để giải thích theo phạm vi được cấp quyền.
- Nội dung AI sinh ra không tự ghi đè đáp án hoặc lời giải chuẩn.
- Đáp án và lời giải không được cung cấp cho học viên trong lúc thi nếu chế độ thi không cho phép.

### 8.5. Xem trước

Người quản trị được xem trước nội dung tương tự giao diện học viên, bao gồm:

- Công thức, hình ảnh và bảng.
- Các lựa chọn hoặc ô nhập đáp án.
- Ngữ liệu và thứ tự câu con.
- Các vị trí khuyết của Cloze.
- Cách hiển thị trên web và màn hình điện thoại.

## 9. Trạng thái, ngừng sử dụng và xóa

| Trạng thái | Ý nghĩa | Được chọn cho lượt thi mới |
|---|---|---|
| Nháp | Đang biên soạn hoặc chưa đủ điều kiện | Không |
| Sẵn sàng | Đã đáp ứng điều kiện sử dụng | Có, nếu khớp ma trận và cấu hình đề |
| Ngừng sử dụng | Không tiếp tục đưa vào lượt thi mới | Không |

Quy tắc:

- Chỉ phiên bản Sẵn sàng hợp lệ được đưa vào nguồn sinh đề.
- Khi ngừng sử dụng, hệ thống cảnh báo các cấu hình đề có thể bị thiếu nguồn.
- Nếu nguồn không đủ, chức năng sinh đề phải báo lỗi; không lấy câu Nháp hoặc Ngừng sử dụng để bù.
- Không xóa cứng câu hỏi đã có dữ liệu tham chiếu hoặc lịch sử thi.
- Câu hỏi chưa được sử dụng có thể xóa theo quyền và sau khi xác nhận.
- Ngừng sử dụng không làm mất nội dung của lượt thi đang làm hoặc kết quả cũ.

## 10. Thay đổi mới so với bản 1 từ đầu mục 1-9

Ngân hàng câu hỏi

Hệ thống tái sử dụng ngân hàng câu hỏi hiện có để quản lý nội dung phục vụ thi và luyện tập cho các kỳ thi HSA, QDA, BCA, SP2E, SPT, tốt nghiệp THPT và V-ACT. Platform Admin hoặc người dùng được phân quyền có thể tìm kiếm, tạo, chỉnh sửa, nhập theo mẫu, xem trước và quản lý phiên bản câu hỏi.

Mỗi câu hỏi được lưu cùng nội dung, thông tin trả lời, điểm và các thuộc tính phân loại. Hệ thống sử dụng các thuộc tính này để lựa chọn câu hỏi từ ngân hàng khi sinh đề và phân tích kết quả học tập theo kiến thức, khối lớp và độ khó. Số câu cần lấy, tỷ lệ phân bổ và thứ tự các phần được thiết lập tại ma trận của từng bài thi.

Khi tạo câu hỏi, người biên soạn chọn dạng theo cách học viên trả lời. Hệ thống hiển thị các trường khai báo tương ứng. Dạng bài chuyên môn như đọc hiểu, viết lại câu hoặc xử lý số liệu được khai báo riêng để phục vụ lựa chọn theo ma trận. Ví dụ, một câu thuộc dạng bài đọc hiểu có thể yêu cầu học viên chọn một đáp án hoặc nhập câu trả lời ngắn.

| Dạng câu hỏi | Cách học viên làm bài | Thông tin người biên soạn khai báo |
|---|---|---|
| Trắc nghiệm một đáp án | Chọn một phương án đúng | Nội dung câu hỏi, danh sách phương án và một đáp án đúng |
| Trắc nghiệm nhiều đáp án | Chọn các phương án đúng | Nội dung câu hỏi, danh sách phương án và tập đáp án đúng |
| Trả lời ngắn (Short Answer) | Nhập đáp án cuối cùng | Nội dung câu hỏi, đáp án chuẩn, các đáp án khác được chấp nhận nếu có và hướng dẫn nhập |
| Điền khuyết (Cloze) | Trả lời tại từng vị trí khuyết trong ngữ liệu | Ngữ liệu, vị trí khuyết, kiểu trả lời, đáp án và điểm của từng vị trí |
| Bộ câu hỏi (Question Set) | Sử dụng chung một ngữ liệu để trả lời nhiều câu con | Ngữ liệu, danh sách câu con, thứ tự hiển thị, dạng trả lời, đáp án hoặc hướng dẫn chấm và điểm của từng câu con |
| Đúng/Sai theo mệnh đề | Chọn Đúng hoặc Sai cho từng mệnh đề | Phần dẫn, danh sách mệnh đề, đáp án từng ý và quy tắc tính điểm |
| Ghép đôi | Chọn nội dung ở cột phải tương ứng với từng mục ở cột trái | Hai danh sách, các cặp đúng, điểm từng cặp và quy tắc dùng lại phương án |
| Tự luận | Viết đoạn văn, bài văn hoặc trình bày lời giải | Yêu cầu bài làm, hướng dẫn chấm, điểm tối đa và các tiêu chí thành phần nếu có |

Danh sách ngân hàng hiển thị nội dung rút gọn của câu hỏi hoặc tên bộ câu hỏi, dạng câu hỏi, môn học, khối lớp, độ khó, điểm, các MAPID đã gắn, trạng thái sử dụng và người tạo. Hệ thống tự sinh mã định danh cho từng câu hoặc bộ câu hỏi. Người dùng có thể tìm theo mã dù mã không hiển thị thành một cột trong danh sách.

Người dùng có thể tìm kiếm theo mã hoặc nội dung và kết hợp các bộ lọc theo môn học, khối lớp, độ khó, dạng câu hỏi, MAPID, nhãn bổ sung, trạng thái, người tạo, khoảng thời gian tạo và tình trạng đã có lời giải hay chưa. Kết quả được phân trang. Khi không có câu hỏi phù hợp, hệ thống thông báo không tìm thấy dữ liệu theo điều kiện đã chọn. Từ danh sách, người dùng có thể xem chi tiết, chỉnh sửa, xem trước và thực hiện các thao tác khác theo quyền được cấp.

Biểu mẫu thêm mới hoặc chỉnh sửa câu hỏi sử dụng các trường sau.

| Trường thông tin | Yêu cầu | Quy tắc khai báo |
|---|---|---|
| Dạng câu hỏi | Bắt buộc | Chọn một trong tám dạng khi thêm mới. Hệ thống mở trình soạn thảo tương ứng. |
| Nội dung câu hỏi hoặc ngữ liệu | Bắt buộc khi đưa vào sử dụng | Hỗ trợ văn bản, công thức, hình ảnh và bảng theo trình soạn thảo hiện có. |
| Câu trả lời và đáp án | Bắt buộc theo dạng | Khai báo phương án, đáp án, cặp ghép hoặc đáp án từng vị trí. Với tự luận, khai báo hướng dẫn chấm và điểm tối đa. |
| Môn học | Bắt buộc khi đưa vào sử dụng | Chọn một môn từ danh mục dùng chung. |
| Khối lớp | Bắt buộc khi đưa vào sử dụng | Chọn một khối lớp chính để tính phân bổ trong ma trận. Với bộ câu hỏi, có thể áp dụng chung và điều chỉnh cho từng câu con. |
| Độ khó | Bắt buộc khi đưa vào sử dụng | Chọn một mức độ từ danh mục dùng chung, chẳng hạn Nhận biết, Thông hiểu, Vận dụng hoặc Vận dụng cao. |
| Dạng bài | Theo cấu trúc đề | Chọn dạng bài chuyên môn khi ma trận có yêu cầu phân bổ theo dạng bài. |
| MAPID | Cho phép chọn nhiều | Chọn các nội dung kiến thức liên quan. Câu hỏi phải có MAPID phù hợp nếu ma trận sử dụng tiêu chí này để chọn câu. |
| Điểm | Có giá trị mặc định | Mỗi câu độc lập hoặc câu con được chấm riêng mặc định 1 điểm. Người biên soạn có thể điều chỉnh thành giá trị lớn hơn 0. |
| Lời giải | Tùy chọn | Nhập nội dung giải thích đáp án để phục vụ xem kết quả và hỗ trợ AI giải thích. |
| Lỗi sai thường gặp | Tùy chọn | Mô tả cách hiểu sai, nhầm lẫn hoặc lỗi học viên thường mắc khi trả lời. |
| Đính kèm | Tùy chọn | Tải lên tài liệu hoặc tài nguyên bổ trợ theo loại tệp và dung lượng hệ thống hỗ trợ. |
| Nhãn bổ sung | Tùy chọn | Chọn nhãn phục vụ tìm kiếm và quản lý nội dung, chẳng hạn nguồn câu hỏi. |

Người biên soạn có thể lưu nháp khi chưa hoàn tất nội dung. Trạng thái không phải là trường cần chọn trên biểu mẫu. Khi đưa câu hỏi vào sử dụng, hệ thống kiểm tra nội dung bắt buộc, đáp án hoặc hướng dẫn chấm, môn học, khối lớp, độ khó và cấu trúc của dạng câu hỏi. Với câu có nhiều thành phần, hệ thống chỉ rõ câu con, mệnh đề, cặp ghép hoặc vị trí khuyết cần bổ sung. Hình ảnh, công thức và ngữ liệu phải hiển thị được khi xem trước.

MAPID được quản lý theo cây kiến thức. Người biên soạn có thể tìm theo mã hoặc tên và chọn một hoặc nhiều MAPID trong cùng một trường. Mỗi MAPID xác định được bộ nguồn, môn học, mã và quan hệ cha–con. Hai mã trùng tên nhưng thuộc các bộ nguồn khác nhau được quản lý riêng.

Một câu gắn nhiều MAPID vẫn chỉ được lấy một lần trong cùng đề. Nếu ma trận quy định số câu riêng cho từng MAPID, hệ thống chỉ ghi nhận câu đã chọn vào một chỉ tiêu phân bổ. Ví dụ, câu được gắn cả hai MAPID A và B có thể thuộc nguồn của cả hai, nhưng khi đã được chọn để đáp ứng chỉ tiêu A thì hệ thống phải lấy câu khác để đáp ứng chỉ tiêu B.

Môn học, khối lớp, độ khó, dạng bài và nhãn bổ sung lấy từ danh mục dùng chung. Khi khai báo mới, người biên soạn chỉ chọn được các giá trị đang hoạt động. Giá trị đã ngừng sử dụng vẫn hiển thị trên dữ liệu cũ. Việc sửa tên hoặc ngừng sử dụng một giá trị danh mục không làm thay đổi thông tin phân loại đã ghi nhận trong lượt thi và kết quả lịch sử.

Với câu trắc nghiệm một đáp án, người biên soạn nhập đủ số phương án theo cấu trúc đề và đánh dấu đúng một phương án. Nếu cấu hình cho phép đảo thứ tự hiển thị, hệ thống chấm theo mã phương án. Với lựa chọn có nội dung phụ thuộc vị trí như “cả A và B”, người biên soạn phải tắt đảo đáp án cho câu đó.

Với câu trắc nghiệm nhiều đáp án, người biên soạn đánh dấu từ hai phương án đúng. Học viên có thể chọn và bỏ chọn từng phương án trước khi nộp bài. Hệ thống so sánh tập phương án học viên đã chọn với tập đáp án đúng, kể cả khi các phương án được đảo thứ tự. Quy tắc chấm đề xuất cần khách hàng xác nhận là học viên được đủ điểm khi chọn đúng toàn bộ tập đáp án. Chọn thiếu hoặc thừa phương án được 0 điểm. Ví dụ, nếu đáp án là A và C thì chọn A, C và D được 0 điểm.

Với Short Answer, học viên chỉ nhập đáp án cuối cùng. Người biên soạn khai báo đáp án chuẩn và có thể bổ sung các cách trả lời được chấp nhận. Hệ thống chấm bằng cách đối chiếu với các giá trị này. Đối với câu yêu cầu số thập phân dùng dấu phẩy hoặc phân số ở dạng tối giản, hướng dẫn nhập phải hiển thị cho học viên. Một cách viết tương đương chỉ được chấp nhận nếu phù hợp với quy tắc đối chiếu đã cấu hình hoặc có trong danh sách đáp án được chấp nhận.

Với Cloze, người biên soạn nhập ngữ liệu một lần và đánh dấu từng vị trí khuyết. Mỗi vị trí có định danh, đáp án chuẩn, điểm và danh sách đáp án khác được chấp nhận nếu có. Hệ thống kiểm tra mọi vị trí khuyết đều có đáp án và mọi đáp án đã khai báo đều gắn với một vị trí. Khi chấm, câu trả lời của học viên được đối chiếu với đáp án tại chính vị trí đó.

Với Question Set, người biên soạn nhập ngữ liệu chung, tạo các câu con và xác định thứ tự hiển thị. Mỗi câu con có định danh riêng, nội dung, dạng trả lời, điểm và đáp án hoặc hướng dẫn chấm tương ứng. Người biên soạn có thể áp dụng phân loại chung cho cả bộ, sau đó điều chỉnh khối lớp, độ khó hoặc MAPID của từng câu con. Mỗi bộ sử dụng một cấp câu con. Hệ thống không cho phép lồng một Question Set vào một Question Set khác.

Điểm của Question Set hoặc Cloze được tính bằng tổng điểm của các câu con hoặc vị trí được chấm độc lập. Tổng điểm hiển thị ở chế độ chỉ đọc và tự cập nhật khi thay đổi điểm thành phần. Ngữ liệu chung không được cộng thêm điểm. Ví dụ, một bài đọc có năm câu con, mỗi câu 1 điểm, có tổng điểm là 5.

Với câu Đúng/Sai, người biên soạn nhập phần dẫn, danh sách mệnh đề và đáp án Đúng hoặc Sai của từng mệnh đề. Nếu cấu trúc đề quy định bốn ý, hệ thống kiểm tra câu có đủ bốn mệnh đề trước khi đưa vào sử dụng. Học viên trả lời từng ý. Hệ thống lưu riêng ba trường hợp chọn Đúng, chọn Sai và bỏ trống. Ý bỏ trống không được tính là ý trả lời đúng.

Câu Đúng/Sai hỗ trợ hai cách chấm theo cấu trúc đề. Với cách cộng điểm từng ý, mỗi mệnh đề đúng đóng góp số điểm đã cấu hình. Với cách chấm theo số ý đúng, hệ thống đếm số ý học viên trả lời đúng và tra bảng điểm của câu. Ví dụ, một câu gồm bốn ý có thể áp dụng mức 0 điểm cho 0 ý đúng, 0,1 điểm cho 1 ý đúng, 0,25 điểm cho 2 ý đúng, 0,5 điểm cho 3 ý đúng và 1 điểm cho 4 ý đúng. Kết quả lưu cả đáp án từng ý và tổng điểm của câu.

Với câu ghép đôi, người biên soạn nhập các mục ở cột trái, các lựa chọn ở cột phải và đáp án tương ứng của từng mục bên trái. Hai cột có thể có số mục khác nhau. Ví dụ, câu hỏi có bốn mục cần ghép và sáu lựa chọn ở cột phải, trong đó hai lựa chọn là phương án dư. Người biên soạn xác định một lựa chọn có thể được dùng cho nhiều mục hay chỉ dùng một lần. Hệ thống kiểm tra các cặp đáp án phù hợp với quy tắc đã chọn.

Học viên có thể thay đổi từng cặp ghép trước khi nộp bài. Hệ thống chấm theo điểm cấu hình của từng cặp và ghi nhận 0 điểm cho cặp bỏ trống. Khi cho phép đảo thứ tự một hoặc cả hai cột, hệ thống giữ liên kết đáp án theo mã của từng mục.

Với câu tự luận, người biên soạn nhập đề bài, yêu cầu học viên thực hiện, hướng dẫn chấm và điểm tối đa. Câu hỏi có thể yêu cầu viết đoạn văn, viết bài văn hoặc trình bày quá trình giải. Nếu hướng dẫn chấm chia thành nhiều tiêu chí hoặc ý nhỏ, tổng điểm tối đa của các tiêu chí phải bằng điểm tối đa của câu.

Học viên nhập bài tự luận bằng văn bản. Sau khi nộp bài, hệ thống lưu nội dung đã nộp và hiển thị “Chờ chấm” đối với câu cần người chấm đánh giá. Học viên có thể xem kết quả phần đã chấm theo chính sách công bố của bài thi. Khi hoàn tất chấm tự luận, hệ thống cập nhật tổng điểm và lưu kết quả cùng phiên bản hướng dẫn chấm đã áp dụng cho lượt thi.

Khi lựa chọn câu hỏi theo ma trận, hệ thống phân biệt số câu với số ý dùng để chấm điểm. Một câu trắc nghiệm một đáp án, trắc nghiệm nhiều đáp án, Short Answer, Đúng/Sai, ghép đôi hoặc tự luận độc lập đóng góp một câu vào ma trận. Ví dụ, một câu Đúng/Sai có bốn mệnh đề đóng góp một câu vào chỉ tiêu số lượng và có bốn ý để ghi nhận kết quả. Tương tự, một câu ghép đôi có bốn cặp vẫn đóng góp một câu.

Question Set được tính theo số câu con. Cloze được tính theo số vị trí trả lời được cấu hình chấm độc lập. Khi chọn bộ, hệ thống tính phân bổ khối lớp và độ khó theo từng thành phần. Ví dụ, một bài đọc có ba câu con Nhận biết và hai câu con Thông hiểu đóng góp năm câu vào ma trận, gồm ba câu Nhận biết và hai câu Thông hiểu.

Hệ thống lấy nguyên bộ theo cấu hình và hiển thị các câu con liền nhau. Một câu con đã được lấy cùng bộ không được lấy thêm dưới dạng câu độc lập trong cùng đề. Với môn Văn, hệ thống chỉ đảo vị trí các nhóm khi cấu hình đề cho phép. Với môn Tiếng Anh, hệ thống giữ thứ tự các khối dạng bài theo cấu trúc đề. Nếu không thể đáp ứng ma trận bằng các bộ nguyên vẹn, hệ thống báo thiếu nguồn hoặc không tìm được tổ hợp phù hợp và chỉ rõ phần bị ảnh hưởng.

Khi tạo mới, người biên soạn chọn dạng câu hỏi, nhập nội dung và thông tin trả lời, khai báo phân loại rồi lưu nháp. Với Cloze hoặc Question Set, người biên soạn bổ sung các vị trí trả lời hoặc câu con và kiểm tra phân loại của từng thành phần. Lời giải, lỗi sai thường gặp và tệp đính kèm có thể được bổ sung trong quá trình biên soạn.

Câu hỏi đủ điều kiện chuyển sang Sẵn sàng khi có đủ nội dung, các trường phân loại bắt buộc và dữ liệu chấm phù hợp với dạng câu. Câu tự động chấm phải có đáp án hợp lệ cho từng đơn vị chấm. Câu tự luận phải có hướng dẫn chấm và điểm tối đa. Câu hỏi có thể đạt trạng thái Sẵn sàng nhưng chỉ được lấy vào một ma trận cụ thể khi đáp ứng đầy đủ tiêu chí lựa chọn của ma trận đó.

Người dùng có quyền được chỉnh sửa câu hỏi chưa sử dụng. Khi thay đổi nội dung, đáp án, hướng dẫn chấm hoặc phân loại của câu đã được sử dụng, hệ thống tạo phiên bản mới. Phiên bản mới phải được kiểm tra trước khi chuyển sang Sẵn sàng. Lượt thi đang diễn ra và kết quả lịch sử tiếp tục sử dụng nội dung, đáp án và quy tắc chấm đã ghi nhận tại thời điểm bắt đầu lượt thi.

Lời giải chuẩn được lưu cùng phiên bản câu hỏi. Chức năng AI có thể sử dụng nội dung câu hỏi, đáp án, lời giải, lỗi sai thường gặp và MAPID để hỗ trợ giải thích theo phạm vi được cấp quyền. Nội dung do AI tạo không tự ghi đè đáp án, lời giải hoặc điểm. Trong lúc thi, học viên không được xem đáp án, lời giải và lỗi sai thường gặp. Sau khi thi, các nội dung này được hiển thị theo chính sách xem kết quả của bài thi.

Người biên soạn được xem trước câu hỏi theo cách học viên sẽ thấy trên web và điện thoại. Nội dung xem trước gồm công thức, hình ảnh, bảng, tệp đính kèm, ngữ liệu, thứ tự câu con và thành phần dùng để trả lời. Người biên soạn có thể thử chọn, đổi hoặc bỏ trống đáp án để đối chiếu kết quả chấm từng ý và toàn câu. Với câu tự luận, phần xem trước cho phép kiểm tra yêu cầu bài làm và đối chiếu bài nộp mẫu với hướng dẫn chấm.

Hệ thống hỗ trợ nhập câu hỏi theo mẫu. Dữ liệu nhập phải thể hiện được tập đáp án đúng đối với trắc nghiệm nhiều đáp án, đáp án từng mệnh đề đối với Đúng/Sai, ánh xạ từng cặp đối với ghép đôi, đáp án từng vị trí đối với Cloze, quan hệ giữa ngữ liệu và câu con đối với Question Set, hướng dẫn chấm và điểm tối đa đối với tự luận. Các dòng dùng mã dạng câu hỏi hiện có tiếp tục được xử lý theo cấu trúc tương ứng. Khi phát hiện lỗi, hệ thống chỉ rõ dòng, câu hoặc ý và trường cần sửa, chẳng hạn thiếu đáp án một mệnh đề, tham chiếu tới phương án ghép không tồn tại hoặc tổng điểm tiêu chí khác điểm tối đa của câu.

Ngân hàng quản lý ba trạng thái sử dụng của câu hỏi.

| Trạng thái | Ý nghĩa | Khả năng sử dụng |
|---|---|---|
| Nháp | Câu hỏi đang được biên soạn hoặc chưa đủ dữ liệu bắt buộc | Người biên soạn có thể tiếp tục chỉnh sửa. Hệ thống chưa lấy câu vào lượt thi mới. |
| Sẵn sàng | Phiên bản câu hỏi đã đáp ứng điều kiện sử dụng | Hệ thống có thể lấy câu vào lượt thi mới nếu phù hợp với ma trận và cấu hình đề. |
| Ngừng sử dụng | Phiên bản câu hỏi đã được rút khỏi nguồn dùng cho lượt thi mới | Nội dung vẫn được lưu để phục vụ lượt thi đang làm và tra cứu kết quả cũ. |

Khi người dùng ngừng sử dụng một câu hỏi, hệ thống cảnh báo các cấu hình đề có thể bị thiếu nguồn. Nếu nguồn còn lại không đủ để sinh đề, hệ thống báo lỗi tại phần ma trận liên quan. Câu hỏi chưa được sử dụng có thể được xóa theo quyền sau khi người dùng xác nhận. Với câu hỏi đã có tham chiếu hoặc lịch sử thi, hệ thống chỉ cho phép ngừng sử dụng để bảo toàn dữ liệu của các lượt thi đã phát sinh.
