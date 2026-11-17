# Thiết lập cấu trúc chi tiết đề QDA

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-17 10:39, lấy về ngày 2026-11-17. Nguồn: uniworkos, thư mục "Tài liệu Thi 360" (doc `01M7KR186YCP6DZVGM1H38557J`). Chỉ đọc; bản gốc ở uniworkos.


Trong bước **Thiết lập cấu trúc chi tiết**, quản trị viên khai báo **cách lấy câu hỏi cho từng môn**. Quan hệ giữa mã đề và các môn đã được khai báo tại **Global List**. Vì vậy, màn thiết lập bài thi chỉ hiển thị danh sách môn cần cấu hình. Quản trị viên không phải chọn lại mã, gán môn cho mã hoặc khai báo ma trận nhiều lần cho cùng một môn.

Ví dụ, Hóa học xuất hiện trong cả QĐ01 và QĐ02 nhưng quản trị viên chỉ thiết lập **một ma trận Hóa học 25 câu** cho bài thi. Khi học viên chọn QĐ01, hệ thống sinh 25 câu Vật lí rồi đến 25 câu Hóa học. Khi học viên chọn QĐ02, hệ thống sinh 25 câu Hóa học rồi đến 25 câu Sinh học. Câu hỏi trong từng môn được lấy ngẫu nhiên theo ma trận, còn thứ tự ghép môn được xác định từ mã học viên chọn.

## 1. Điểm vào và phạm vi cần cấu hình

Sau khi quản trị viên lưu **Thông tin chung và cấu trúc tổng quan** ở bước 1 và nhấn **Tiếp tục**, hệ thống xác định các phần, môn cần thiết lập dựa trên phạm vi bài thi.

| Lựa chọn tại bước 1 | Nội dung hiển thị tại bước 2 |
|---|---|
| **QDA – Bài thi FULL** | Phần 1 – Toán học và xử lí số liệu. Phần 2 – Ngôn ngữ – Văn học. Phần 3 – Tự chọn, gồm các môn Khoa học và Tiếng Anh được xác định từ Global List. Với danh mục hiện tại, quản trị viên thiết lập Vật lí, Hóa học, Sinh học, Lịch sử, Địa lí và Tiếng Anh. |
| **QDA – Bài thi theo phần – Khoa học** | Danh sách các môn thuộc mã Khoa học trong Global List. Với danh mục hiện tại, có 5 ma trận môn cần thiết lập. |
| **QDA – Bài thi theo phần – Toán, Ngôn ngữ – Văn học hoặc Tiếng Anh** | Mở trực tiếp cấu hình của phần đã chọn. |
| **QDA – Bài thi theo môn** | Mở trực tiếp môn đã chọn. Quản trị viên có thể dùng cấu trúc môn QDA hoặc giới hạn nội dung để tạo bài ôn theo chủ đề. |

**Cấu trúc mặc định của bài thi FULL**

| Phần | Số câu học viên làm | Thời lượng | Điểm tối đa |
|---|---|---|---|
| Phần 1 – Toán học và xử lí số liệu | 50 | 80 phút | 50 |
| Phần 2 – Ngôn ngữ – Văn học | 50 | 55 phút | 50 |
| Phần 3 – Tự chọn theo mã đề | 50 | 60 phút | 50 |
| **Toàn bài** | **150** | **195 phút** | **150** |

Ở phần 3, quản trị viên thiết lập đủ các môn để phục vụ mọi lựa chọn hợp lệ. Mỗi học viên chỉ làm **50 câu của một mã đã chọn**. Với mã Khoa học, hai môn dùng chung thời lượng 60 phút.

## 2. Tổ chức màn hình và điều hướng

Đối với bài thi FULL, khi vào bước 2, hệ thống mở **Phần 1 – Toán**. Quản trị viên có thể chuyển trực tiếp tới phần hoặc môn muốn chỉnh sửa. Đối với bài thi theo môn, hệ thống mở thẳng ma trận môn đó.

| Khu vực | Nội dung và hành vi |
|---|---|
| **Thông tin bài thi** | Hiển thị tên bài, kỳ thi QDA, phạm vi thi, tổng số câu và thời lượng từ bước 1. Chọn **Sửa thông tin tổng quan** để quay lại khi cần. |
| **Danh sách phần/môn** | Hiển thị các phần theo thứ tự. Khi mở **Phần 3 – Tự chọn**, hệ thống hiển thị nhóm **Khoa học** với các môn thành phần và mục **Tiếng Anh**. Mỗi môn xuất hiện một lần. |
| **Cấu hình môn đang chọn** | Toán và các môn Khoa học hiển thị ma trận. Văn và Tiếng Anh hiển thị danh sách nhóm dạng bài theo thứ tự. Quản trị viên chỉnh sửa trực tiếp trong môn. |
| **Tổng hợp cấu hình** | Hiển thị số câu mục tiêu, số đã phân bổ và số thiếu hoặc vượt. Với câu chùm, hiển thị cả số chùm và số câu con. |
| **Thao tác** | **Lưu nháp**, **Kiểm tra môn**, **Sinh đề thử** và **Tiếp tục**. Khi có lỗi, chọn thông báo để mở đúng môn, nhóm hoặc trường cần sửa. |

Tại phần Khoa học, danh sách có thể hiển thị như sau:

| Môn | Mục tiêu | Tình trạng minh họa | Thao tác |
|---|---|---|---|
| Vật lí | 25 câu | Đã kiểm tra | Thiết lập |
| Hóa học | 25 câu | Cần kiểm tra lại | Thiết lập |
| Sinh học | 25 câu | Chưa hoàn tất | Thiết lập |
| Lịch sử | 25 câu | Đã kiểm tra | Thiết lập |
| Địa lí | 25 câu | Đã kiểm tra | Thiết lập |

Khi chuyển môn mà còn thay đổi chưa lưu, hệ thống cho phép **Lưu và chuyển**, **Bỏ thay đổi** hoặc **Ở lại**. Khi mở lại bản nháp, hệ thống tải ma trận đã lưu của từng môn.

## 3. Quy tắc sử dụng mã đề từ Global List

### a. Trách nhiệm của từng chức năng

| Chức năng | Dữ liệu hoặc thao tác phụ trách |
|---|---|
| **Global List** | Lưu kỳ thi, mã đề, nhánh Khoa học/Tiếng Anh, danh sách môn theo thứ tự, trạng thái và phiên bản của mã. |
| **Thiết lập cấu trúc bài thi** | Đọc danh mục để xác định các môn cần chuẩn bị. Lưu một ma trận cho mỗi môn trong phiên bản bài thi. |
| **Thực hiện bài thi** | Hiển thị các mã thuộc phạm vi bài thi để học viên chọn. Dựa vào mã đã chọn, hệ thống xác định môn, lấy câu theo ma trận từng môn và ghép thành phần thi. |

### b. Quan hệ mã và môn hiện tại

Bảng dưới đây mô tả dữ liệu đã khai báo tại Global List. Quản trị viên không nhập lại bảng này khi tạo đề.

| Mã học viên chọn | Nội dung được sinh cho phần 3 | Tổng |
|---|---|---|
| **QĐ01** | Vật lí 25 câu → Hóa học 25 câu | 50 câu |
| **QĐ02** | Hóa học 25 câu → Sinh học 25 câu | 50 câu |
| **QĐ03** | Vật lí 25 câu → Sinh học 25 câu | 50 câu |
| **QĐ04** | Lịch sử 25 câu → Địa lí 25 câu | 50 câu |
| **QĐ05** | Tiếng Anh 50 câu | 50 câu |

### c. Xử lý khi học viên chọn mã

1. Khi đến phần tự chọn, hệ thống hiển thị **mã và tên môn theo thứ tự** để học viên biết mình sẽ làm những môn nào. Ví dụ, **QĐ02 – Hóa học và Sinh học**.
2. Học viên chọn một mã và xác nhận bắt đầu. Mã đã xác định đầy đủ các môn, nên học viên chỉ cần xác nhận một lần.
3. Hệ thống đọc danh sách môn của mã, lấy đúng ma trận của từng môn trong phiên bản bài thi và sinh câu.
4. Với mã Khoa học, hệ thống ghép thành hai nhóm liên tiếp. Ví dụ, trong phần tự chọn của QĐ02, vị trí 1–25 là Hóa học, vị trí 26–50 là Sinh học.
5. Hệ thống lưu mã, thứ tự môn và toàn bộ câu đã sinh vào lượt thi. Khi học viên tiếp tục lượt đang làm, hệ thống khôi phục đúng bộ câu này.

**Quy tắc phiên bản.** Khi xuất bản bài thi, hệ thống ghi nhận phiên bản quan hệ mã–môn từ Global List dùng cho bài đó. Nếu sau này Global List bổ sung một mã ghép từ các môn đã có, khi cập nhật bài thi, quản trị viên chỉ cần kiểm tra khả năng sinh đề theo mã mới. Nếu mã có thêm một môn chưa được thiết lập, hệ thống chỉ rõ môn cần bổ sung ma trận. Lượt thi đã bắt đầu tiếp tục theo cấu trúc đã ghi nhận.

## 4. Luồng thiết lập phần Toán học và xử lí số liệu

### a. Cấu hình mặc định

| Nhóm thông tin | Giá trị khởi tạo | Hành vi |
|---|---|---|
| **Mục tiêu** | 50 câu, 80 phút | Hiển thị từ cấu trúc tổng quan. |
| **Phạm vi kiến thức** | Toàn môn Toán | Cho phép giới hạn nguồn theo MAPID hoặc khai báo số câu cho từng chủ đề. |
| **Dạng trả lời** | 35 câu trắc nghiệm 4 lựa chọn, 15 câu điền đáp án | Tổng hai dạng phải đủ 50 câu. Câu điền đáp án hỗ trợ số nguyên, phân số tối giản hoặc số thập phân dùng dấu phẩy theo đáp án trong ngân hàng. |
| **Phân bố theo lớp** | Lớp 10 · 5 câu (10%) Lớp 11 · 15 câu (30%) Lớp 12 · 30 câu (60%) | Tính theo từng câu được chấm, bao gồm câu con trong chùm. |
| **Phân bố theo độ khó** | Tùy chỉnh | Chỉ áp dụng hạn ngạch khi quản trị viên khai báo. |
| **Câu chùm** | Mỗi chùm có 3 câu con trắc nghiệm | Quản trị viên nhập số chùm. Các câu con được tính trong tổng 35 câu trắc nghiệm. |

### b. Luồng thao tác

1. Quản trị viên chọn **Phần 1 – Toán** và kiểm tra các phân bố đã điền sẵn.
2. Nhập số chùm cần lấy. Hệ thống tính số câu trắc nghiệm đơn còn lại. Ví dụ, với 1 chùm, phần Toán gồm **32 câu trắc nghiệm đơn + 3 câu con trắc nghiệm + 15 câu điền đáp án = 50 câu**.
3. Nếu cần kiểm soát nội dung, chọn **Thêm chỉ tiêu chủ đề**, chọn MAPID, nhập số câu và độ khó tương ứng.
4. Chọn **Kiểm tra môn**. Hệ thống kiểm tra khả năng đồng thời đáp ứng dạng trả lời, khối lớp, chùm và chỉ tiêu chủ đề. Khi đạt, quản trị viên có thể sinh thử riêng phần Toán.

## 5. Luồng thiết lập phần Ngôn ngữ – Văn học

Khi mở phần Văn, hệ thống hiển thị **5 nhóm theo thứ tự**, tổng cộng **50 câu trắc nghiệm trong 55 phút**.

| Thứ tự | Dạng bài | Cách lấy mặc định |
|---|---|---|
| 1 | Chọn từ/cụm từ có nghĩa không cùng nhóm | 5 câu đơn |
| 2 | Chọn cặp từ/cụm từ thích hợp điền vào chỗ trống | 5 câu đơn |
| 3 | Xác định từ/cụm từ sai về ngữ pháp, ngữ nghĩa, logic hoặc phong cách | 5 câu đơn |
| 4 | Đọc một đoạn trích và trả lời một câu hỏi | 10 câu đơn, mỗi câu gắn với ngữ liệu riêng |
| 5 | Đọc một ngữ liệu và trả lời nhiều câu hỏi | 5 chùm × 5 câu con, tổng 25 câu |

Khi nhấn **Sửa** tại nhóm 1–4, quản trị viên chỉnh sửa dạng bài, số câu, nguồn câu và MAPID. Khi mở nhóm 5, hệ thống hiển thị từng chùm để thiết lập điều kiện ngữ liệu.

| Chùm | Loại ngữ liệu mặc định | Số câu con |
|---|---|---|
| 1 | Văn bản thông tin – nghị luận | 5 |
| 2 | Văn bản nghị luận | 5 |
| 3 | Đoạn trích thơ | 5 |
| 4 | Văn bản về văn học thiếu nhi | 5 |
| 5 | Đoạn trích truyện | 5 |

Khi sinh đề, hệ thống chọn ngẫu nhiên nội dung phù hợp trong từng nhóm. Với mỗi chùm, hệ thống lấy nguyên ngữ liệu và 5 câu con, giữ các câu con liền nhau. Nếu thiếu nguồn, thông báo phải xác định đúng dòng cần sửa, ví dụ *“Nhóm 5, chùm 4 – chưa có ngữ liệu về văn học thiếu nhi đủ 5 câu con.”*

## 6. Luồng thiết lập ma trận các môn Khoa học

### a. Chọn môn để thiết lập

Quản trị viên mở **Phần 3 – Tự chọn → Khoa học** và nhấn **Thiết lập** tại một môn. Hệ thống mở ma trận **25 câu** của môn đó. Việc một môn xuất hiện ở vị trí thứ nhất hay thứ hai trong một mã không làm thay đổi cách khai báo ma trận.

| Trường | Quy tắc |
|---|---|
| **Số chùm** | Chọn 1 hoặc 2 chùm. Mỗi chùm gồm 1 ngữ liệu và 3 câu con. |
| **Số câu điền đáp án** | Vật lí, Hóa học và Sinh học có ít nhất 1 câu điền đáp án. Lịch sử và Địa lí cho phép nhập 0. |
| **Số câu trắc nghiệm đơn** | Tự tính bằng **25 − số câu con trong chùm − số câu điền đáp án**. |
| **Khối lớp và độ khó** | Mỗi phân bố được tính trên cùng 25 câu. Câu con được tính theo lớp và độ khó đã khai báo cho chính câu đó. |
| **Chủ đề/MAPID** | Có thể giới hạn nguồn cho toàn môn hoặc phân bổ một số lượng cụ thể theo chủ đề. |

Ví dụ, Vật lí có **2 chùm và 1 câu điền đáp án** thì hệ thống hiển thị **18 câu trắc nghiệm đơn + 6 câu con + 1 câu điền đáp án = 25 câu**. Khi đổi về 1 chùm, số câu trắc nghiệm đơn tự tăng lên 21.

### b. Ma trận mặc định theo môn

| Môn | Phân bố theo **khối lớp** | Phân bố theo **độ khó** |
|---|---|---|
| **Vật lí** | Lớp 10 · 6 câu (24%) Lớp 11 · 5 câu (20%) Lớp 12 · 14 câu (56%) | Tùy chỉnh. Tài liệu chưa quy định tỷ lệ. |
| **Hóa học** | Lớp 10 · 6 câu (24%) Lớp 11 · 5 câu (20%) Lớp 12 · 14 câu (56%) | Nhận biết · 4 câu (16%) Thông hiểu · 20 câu (80%) Vận dụng · 1 câu (4%) |
| **Sinh học** | Lớp 10 · 3 câu (12%) Lớp 11 · 6 câu (24%) Lớp 12 · 16 câu (64%) | Tùy chỉnh. Tài liệu chưa quy định tỷ lệ. |
| **Lịch sử** | Lớp 10 · 2 câu (8%) Lớp 11 · 6 câu (24%) Lớp 12 · 17 câu (68%) | Nhận biết · 8 câu (32%) Thông hiểu · 10 câu (40%) Vận dụng · 7 câu (28%) |
| **Địa lí** | Lớp 10 · 4 câu (16%) Lớp 11 · 4 câu (16%) Lớp 12 · 17 câu (68%) | Tùy chỉnh. Tài liệu chưa quy định tỷ lệ. |

### c. Lưu và kiểm tra

Sau khi lưu, quản trị viên chọn **Kiểm tra môn**, **Môn tiếp theo** hoặc quay lại danh sách Khoa học. Nếu điều chỉnh ma trận Hóa học, hệ thống dùng ma trận mới khi sinh thử mọi mã có Hóa học trong bản đang chỉnh sửa. Quan hệ này được hệ thống xác định tự động từ Global List.

## 7. Luồng thiết lập phần Tiếng Anh

Quản trị viên mở **Phần 3 – Tự chọn → Tiếng Anh** để thiết lập một cấu trúc **50 câu, 60 phút**. Khi học viên chọn mã có môn Tiếng Anh, hệ thống sử dụng cấu trúc này. Với bộ danh mục hiện tại, mã tương ứng là QĐ05.

| Vị trí trong phần | Dạng bài | Cách lấy |
|---|---|---|
| 1–10 | Hoàn thành câu về ngữ pháp, từ vựng, kết hợp từ | 10 câu đơn |
| 11–14 | Chọn từ đồng nghĩa hoặc trái nghĩa | 4 câu đơn |
| 15–18 | Hoàn thành hội thoại | 4 câu đơn |
| 19–22 | Đọc thông báo, biển báo | 4 câu đơn |
| 23–26 | Viết lại câu | 4 câu đơn |
| 27–30 | Kết hợp hai câu | 4 câu đơn |
| 31–35 | Đọc điền từ/cụm từ | 1 chùm × 5 câu |
| 36–40 | Đọc hiểu 1 | 1 chùm × 5 câu |
| 41–45 | Đọc hiểu 2 | 1 chùm × 5 câu |
| 46–50 | Suy luận và giải quyết tình huống | 5 câu đơn |

Quản trị viên chọn **Sửa** tại từng dòng để thiết lập nguồn câu, MAPID và điều kiện ngữ liệu. Hệ thống giữ thứ tự 10 nhóm, lấy ngẫu nhiên nội dung trong từng nhóm và bảo toàn quan hệ giữa các vị trí trống với đáp án của chùm đọc điền. Nhóm đồng nghĩa/trái nghĩa mặc định lấy 4 câu trong cùng nhóm. Tỷ lệ riêng giữa hai dạng chỉ áp dụng khi được khai báo.

## 8. Luồng rút gọn cho bài thi theo môn hoặc ôn theo chủ đề

Đối với bài thi theo môn, môn đã được xác định ở bước 1. Học viên vào làm trực tiếp bài đó. Ví dụ, mua bài luyện Hóa học thì học viên làm bài Hóa học, không phát sinh bước chọn QĐ01 hay QĐ02.

| Cách khởi tạo | Hành vi |
|---|---|
| **Dùng cấu trúc môn QDA** | Nạp ma trận mặc định của môn. Ví dụ, Hóa học có 25 câu, phân bố lớp 6 / 5 / 14 và độ khó 4 / 20 / 1. Thời lượng lấy từ cấu hình của bài theo môn. |
| **Tự thiết lập bài ôn** | Dùng số câu và thời lượng đã khai báo ở bước 1. Quản trị viên chọn toàn môn hoặc giới hạn theo MAPID, sau đó nhập dạng câu và số lượng cần lấy. |

**Ví dụ với bài “QDA – Luyện chủ đề Di truyền y học”**

1. Tại bước 1, chọn **Bài thi theo môn → Sinh học**, nhập 10 câu và thời lượng bài ôn.
2. Tại bước 2, chọn **Tự thiết lập bài ôn → Theo chủ đề → Di truyền y học**.
3. Khai báo **7 câu đơn và 1 chùm 3 câu con**. Bổ sung phân bố độ khó nếu cần.
4. Chọn **Kiểm tra môn**. Hệ thống đối chiếu nguồn theo đúng MAPID đã chọn và kiểm tra có đủ một chùm nguyên vẹn.
5. Khi đạt, quản trị viên chọn **Sinh đề thử** hoặc **Tiếp tục**.

MAPID cho phép chọn nhiều giá trị. Khi chọn chủ đề cha, mặc định bao gồm các chủ đề con. Một câu mang nhiều MAPID chỉ được lấy một lần trong đề và chỉ đóng góp vào một hạn ngạch chủ đề khi có phân bổ số lượng.

## 9. Kiểm tra, sinh đề thử và điều kiện hoàn thành

| Thao tác | Nội dung xử lý | Kết quả mong đợi |
|---|---|---|
| **Lưu nháp** | Lưu cấu trúc các phần và ma trận từng môn đã nhập, kể cả khi còn thiếu nguồn câu. | Mở lại được đúng bản đang thực hiện. |
| **Kiểm tra môn** | Kiểm tra tổng số câu, dạng trả lời, chùm, lớp, độ khó, MAPID và khả năng đồng thời đáp ứng các chỉ tiêu. | Hiển thị lỗi tại đúng môn hoặc nhóm. Ví dụ, *“Sinh học cần 2 chùm có 3 câu con nhưng nguồn đã chọn chỉ có 1 chùm phù hợp.”* |
| **Sinh đề thử** | Quản trị viên có thể thử một môn hoặc chọn một mã để mô phỏng lựa chọn của học viên. | Với QĐ02, bản thử hiển thị 25 câu Hóa học trước và 25 câu Sinh học sau. **Mã chọn để thử chỉ áp dụng cho lần xem thử đó.** |
| **Tiếp tục** | Kiểm tra các phần bắt buộc và đối chiếu toàn bộ mã thuộc phạm vi bài thi với ma trận môn đã thiết lập. | Nếu đạt, chuyển bước. Nếu thiếu ma trận, hệ thống nêu tên môn và các mã bị ảnh hưởng, đồng thời cho phép mở trực tiếp môn cần sửa. |

**Điều kiện nghiệm thu**

1. Trong bài FULL, khi Global List có QĐ01 và QĐ02 cùng sử dụng Hóa học, màn thiết lập chỉ xuất hiện **một ma trận Hóa học**.
2. Nếu ma trận Hóa học còn thiếu, hệ thống hiển thị *“Hóa học chưa hoàn tất cấu hình 25 câu. Các mã bị ảnh hưởng: QĐ01, QĐ02.”* Quản trị viên sửa một lần tại môn Hóa học.
3. Học viên chọn QĐ01 thì phần 3 gồm Vật lí → Hóa học. Chọn QĐ02 thì phần 3 gồm Hóa học → Sinh học. Cả hai trường hợp đều có đúng 50 câu trong 60 phút.
4. Học viên chọn QĐ05 thì thực hiện đúng 50 câu Tiếng Anh theo thứ tự nhóm đã thiết lập. Tiếp tục một lượt đang làm phải khôi phục đúng mã và đúng bộ câu ban đầu.
5. Với bài thi theo môn hoặc bài ôn theo chủ đề, hệ thống chỉ kiểm tra ma trận của bài đó và cho học viên vào làm trực tiếp.
