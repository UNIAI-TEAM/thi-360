# Thiết lập bài thi và ma trận đề

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-17 08:56, lấy về ngày 2026-11-17. Nguồn: uniworkos, thư mục "Tài liệu Thi 360". Chỉ đọc; bản gốc ở uniworkos.

## 1. Thông tin chung

### 1.1. Mục tiêu

Cho phép quản trị viên thiết lập cấu trúc bài thi và ma trận lựa chọn câu hỏi từ ngân hàng. Khi học viên bắt đầu một lượt thi, hệ thống tự động tạo đề theo cấu hình, bảo đảm số lượng câu, phân bổ khối lớp, độ khó, phạm vi kiến thức và quy tắc nhóm câu hỏi.

Ma trận được cấu hình trực tiếp trong bài thi, không yêu cầu tạo một đối tượng ma trận riêng trước khi tạo bài thi.

### 1.2. Tác nhân

- Platform Admin/người được phân quyền: Tạo, chỉnh sửa, kiểm tra, xem trước và công bố cấu hình bài thi.
- Hệ thống: Kiểm tra nguồn câu hỏi và sinh đề theo ma trận.
- Học viên: Lựa chọn phần thi hoặc tổ hợp được phép và thực hiện đề đã sinh.

### 1.3. Phạm vi

PBI bao gồm:

- Khai báo thông tin bài thi.
- Thiết lập cấu trúc phần thi, môn thi và tổ hợp.
- Thiết lập ma trận câu hỏi.
- Kiểm tra tính hợp lệ và khả năng sinh đề.
- Sinh đề xem trước.
- Công bố và quản lý phiên bản cấu hình.
- Sinh đề thực tế và lưu bản chụp dữ liệu của lượt thi.

Giá bán, thanh toán và cấp quyền được quản lý theo chức năng tương ứng. PBI này kiểm tra quyền trước khi tạo lượt thi.

### 1.4. Nguyên tắc thiết kế

- Tái sử dụng bốn dạng câu hỏi: Trắc nghiệm, Short Answer, Cloze và Question Set.
- Số câu được tính theo câu đơn hoặc câu con được chấm độc lập.
- Ngữ liệu chung không được tính thêm là một câu.
- Mỗi câu chỉ xuất hiện một lần trong một đề.
- Question Set và Cloze được lấy nguyên bộ; không tự cắt câu con để đáp ứng ma trận.
- Khối lớp và độ khó là các điều kiện phải được đáp ứng đồng thời.
- MAPID cho phép chọn nhiều giá trị.
- Không tự giảm số câu, thay đổi tỷ lệ hoặc lấy câu ngoài phạm vi để sinh bằng được một đề.

> Điểm cần thống nhất: Ma trận có đủ câu ở từng dòng chưa chắc sinh được đề hoàn chỉnh. Các dòng có thể cùng sử dụng một nguồn câu hỏi, hoặc các bộ câu hỏi không ghép vừa số lượng yêu cầu. Vì vậy, phải kiểm tra toàn bộ cấu hình, không chỉ đếm nguồn từng dòng.

## 2. Thuật ngữ và cách tính

| Thuật ngữ | Ý nghĩa |
|---|---|
| Bài thi | Cấu hình tổng thể mà học viên được thực hiện |
| Phần thi | Một phần trong bài, có thứ tự và thời lượng theo cấu hình |
| Khối câu hỏi | Nhóm câu trong một phần, cùng phạm vi lựa chọn hoặc quy tắc hiển thị |
| Dòng ma trận | Một chỉ tiêu gồm điều kiện lựa chọn và số câu cần lấy |
| Câu đơn | Câu Trắc nghiệm hoặc Short Answer độc lập |
| Bộ câu hỏi | Question Set hoặc Cloze gồm nhiều câu con/vị trí trả lời |
| Nguồn hợp lệ | Các câu hỏi đáp ứng điều kiện sử dụng và điều kiện của ma trận |
| Phiên bản công bố | Cấu hình đã kiểm tra và được áp dụng cho lượt thi mới |
| Snapshot lượt thi | Bản lưu cấu hình, câu hỏi, đáp án, điểm và thứ tự thực tế của một lượt thi |

Ví dụ: Một Question Set có năm câu con, mỗi câu một điểm, được tính là năm câu và năm điểm. Nếu ba câu thuộc mức Nhận biết và hai câu thuộc mức Thông hiểu thì bộ đóng góp đúng phân bổ 3 NB + 2 TH vào ma trận.

## 3. Bố cục chức năng

Trong màn hình tạo/chỉnh sửa bài thi, bố trí các khu vực:

| Khu vực | Nội dung |
|---|---|
| Thông tin chung | Tên bài, kỳ thi, hình thức FULL/Từng phần, mô tả |
| Cấu trúc và ma trận | Phần thi, môn/tổ hợp, khối câu hỏi, số câu và tiêu chí lựa chọn |
| Kiểm tra và xem trước | Lỗi cấu hình, nguồn câu hỏi, kết quả sinh thử và thống kê phân bổ |
| Phiên bản và công bố | Lưu cấu hình đang sửa, công bố và xem phiên bản |

Các thao tác chính: Lưu, Kiểm tra cấu hình, Sinh đề xem trước, Công bố/Cập nhật công bố.

Bảng tổng hợp cấu trúc luôn hiển thị:

- Tổng số câu dự kiến.
- Tổng thời gian.
- Tổng điểm hoặc quy tắc xác định tổng điểm.
- Số câu đã phân bổ và còn thiếu.
- Các lỗi cần xử lý.

## 4. Thông tin chung của bài thi

Quản trị viên khai báo thông tin để nhận diện bài thi, phân loại theo kỳ thi và xác định phạm vi học viên sẽ thực hiện.

| Trường thông tin | Yêu cầu | Mô tả |
|---|---|---|
| Mã bài thi | Bắt buộc | Mã duy nhất để quản lý và tra cứu bài thi. Đề xuất hệ thống tự sinh khi tạo mới. |
| Tên bài thi | Bắt buộc | Tên hiển thị cho quản trị viên và học viên. Ví dụ “Luyện thi QDA – Đề toàn phần số 01”. |
| Kỳ thi | Bắt buộc | Chọn từ danh mục dùng chung, gồm HSA, QDA, BCA, SP2E, SPT, V-ACT và tốt nghiệp THPT. |
| Phạm vi bài thi | Bắt buộc | Chọn Toàn bài, Theo phần hoặc Theo môn trong các phạm vi phù hợp với kỳ thi đã chọn. |
| Phần thi hoặc môn thi | Bắt buộc khi tạo bài theo phần hoặc môn | Chọn phần hoặc môn học viên sẽ làm. Ví dụ “Toán học và xử lí số liệu” đối với bài theo phần của HSA hoặc “Toán” đối với bài theo môn của SP2E. |
| Mô tả bài thi | Tùy chọn khi tạo nháp | Giới thiệu mục tiêu, đối tượng phù hợp và nội dung chính của bài thi. |
| Ảnh giới thiệu | Tùy chọn | Hình ảnh đại diện của bài thi khi hiển thị cho học viên. |
| Hướng dẫn làm bài | Tùy chọn khi tạo nháp | Nội dung học viên cần biết trước khi bắt đầu, chẳng hạn cách chọn tổ hợp hoặc quy định về thời gian làm bài. |

Kỳ thi và phạm vi đã chọn là căn cứ để thiết lập cấu trúc ở mục 5. Ví dụ, với HSA – Theo phần – Toán, quản trị viên thiết lập riêng phần Toán. Với SP2E – Theo môn – Toán, quản trị viên thiết lập đầy đủ các phần của bài thi môn Toán.

## 5. Cấu trúc tổng quan của đề

### 5.1. Khởi tạo cấu trúc theo loại bài thi

Hệ thống khởi tạo danh sách phần thi, thứ tự, môn hoặc tổ hợp, số câu và thời lượng phù hợp với phân loại tại mục 4. Nếu một kỳ thi hoặc môn có cấu trúc khác nhau theo năm, quản trị viên chọn phiên bản tại bước thiết lập cấu trúc. Ví dụ, SP2E môn Toán năm 2025 có 22 câu, còn cấu trúc năm 2026 có 40 câu.

Các thông số dưới đây là căn cứ khởi tạo theo tài liệu khách hàng cung cấp.

| Kỳ thi | Cấu trúc tổng quan | Thời lượng và thang điểm tham chiếu |
|---|---|---|
| HSA | Ba phần. Toán học và xử lí số liệu 50 câu. Văn học – Ngôn ngữ 50 câu. Phần 3 chọn Khoa học hoặc Tiếng Anh, 50 câu. Nhánh Khoa học gồm ba trong năm môn theo tổ hợp được phép. | 195 phút, lần lượt 75, 60 và 60 phút cho ba phần. Tối đa 150 điểm. |
| QDA | Ba phần. Toán 50 câu, Ngôn ngữ – Văn học 50 câu và phần lựa chọn 50 câu. Học viên chọn một mã trong phạm vi bài thi cho phép. QĐ01–QĐ04 là các tổ hợp Khoa học, mỗi tổ hợp gồm hai môn, mỗi môn 25 câu. QĐ05 là Tiếng Anh. | 195 phút, lần lượt 80, 55 và 60 phút cho ba phần. Tối đa 150 điểm. |
| BCA | Ba phần, tổng cộng 81 câu. Phần 1 gồm một câu tự luận Ngữ văn. Phần 2 gồm 65 câu bắt buộc thuộc Toán, Lịch sử và Tiếng Anh. Phần 3 gồm 15 câu thuộc môn lựa chọn tương ứng với mã CA1–CA4. | 180 phút dùng chung. Tối đa 100 điểm, phân bổ theo ba phần là 25, 60 và 15 điểm. |
| SP2E | Khởi tạo theo môn và phiên bản trong bộ tài liệu 9 môn. Số phần, dạng bài và số câu phụ thuộc vào môn và năm áp dụng. | 60–90 phút tùy môn. Thang 10 điểm/môn. |
| SPT | Khởi tạo theo từng môn hoặc định hướng trong bộ 12 mẫu. Ví dụ, Toán 2025 gồm 4 phần: trắc nghiệm chọn một đáp án, đúng/sai, trả lời ngắn và tự luận. | 60–90 phút tùy môn. Thang 10 điểm/môn. Riêng Toán 2025 có 25 câu trong 90 phút. |
| Tốt nghiệp THPT | Khởi tạo theo từng môn hoặc định hướng trong bộ 12 mẫu. Ngữ văn gồm Đọc hiểu và Viết. Tin học có phần lựa chọn nên số câu xuất hiện trên đề và số câu học viên phải làm khác nhau. | 50–120 phút tùy môn. Thang 10 điểm/môn. |
| V-ACT | Ba phần bắt buộc, tổng cộng 120 câu. Sử dụng ngôn ngữ 60 câu, gồm Tiếng Việt 30 câu và Tiếng Anh 30 câu. Toán học 30 câu. Tư duy khoa học 30 câu. | 150 phút dùng chung. Thang tham chiếu 1.200 điểm theo IRT. Thang điểm thực tế dùng cho bài luyện cần được xác nhận riêng. |

### Bảng số câu và thời lượng mặc định theo kỳ thi, môn thi

Khi quản trị viên chọn kỳ thi và môn thi, hệ thống điền số câu và thời lượng theo bảng dưới. Mỗi ô ghi số câu học viên phải làm / thời lượng làm bài. Một câu Đúng/Sai gồm bốn ý vẫn tính là một câu khi xác định tổng số câu của đề.

| Môn thi | SP2E theo cấu trúc 2026 | SPT theo bộ đề tham khảo khách hàng cung cấp | Tốt nghiệp THPT từ 2025 |
|---|---|---|---|
| Toán | 40 câu / 90 phút | 25 câu / 90 phút | 22 câu / 90 phút |
| Ngữ văn | 22 câu / 90 phút | 12 câu / 90 phút | 7 câu / 120 phút |
| Tiếng Anh | 50 câu / 60 phút | 33 câu / 60 phút | 40 câu / 50 phút |
| Vật lí | 28 câu / 60 phút | 21 câu / 60 phút | 28 câu / 50 phút |
| Hóa học | 28 câu / 60 phút | 27 câu / 60 phút | 28 câu / 50 phút |
| Sinh học | 28 câu / 60 phút | 22 câu / 60 phút | 28 câu / 50 phút |
| Lịch sử | 32 câu / 60 phút | 25 câu / 60 phút | 28 câu / 50 phút |
| Địa lí | 28 câu / 60 phút | 20 câu / 60 phút | 28 câu / 50 phút |
| Giáo dục kinh tế và pháp luật | 32 câu / 60 phút | 21 câu / 60 phút | 28 câu / 50 phút |
| Tin học | — | 25 câu / 60 phút | 28 câu / 50 phút |
| Công nghệ – định hướng công nghiệp | — | 24 câu / 60 phút | 28 câu / 50 phút |
| Công nghệ – định hướng nông nghiệp | — | 24 câu / 60 phút | 28 câu / 50 phút |

### 5.2. Khai báo cấu trúc từng phần

Mỗi bài thi gồm một hoặc nhiều phần có thứ tự. Trong một phần, quản trị viên xác định nội dung bắt buộc và các phương án học viên được lựa chọn.

| Thành phần | Quy tắc khai báo |
|---|---|
| Tên phần thi | Tên dùng để nhận diện phần trong đề, ví dụ “Toán học”, “Sử dụng ngôn ngữ” hoặc “Phần tự chọn”. |
| Thứ tự phần thi | Xác định vị trí của phần trong bài. Với bài theo phần hoặc theo môn, chỉ sử dụng thứ tự trong phạm vi bài đã chọn. |
| Quy tắc tham gia | Xác định học viên phải làm toàn bộ nội dung của phần hay được chọn một số phương án. Nếu có lựa chọn, khai báo số phương án phải chọn. |
| Môn hoặc tổ hợp | Với phần bắt buộc, khai báo các môn học viên phải làm. Với phần lựa chọn, khai báo danh sách môn, nhánh hoặc mã tổ hợp được phép. Mã tổ hợp lấy từ danh mục dùng chung. |
| Số câu trên đề | Tổng số câu xuất hiện trong phần, bao gồm cả nội dung lựa chọn nếu đề trình bày nhiều phương án. |
| Số câu phải làm | Số câu học viên thực hiện theo phương án đã chọn. Khi toàn bộ câu đều bắt buộc, hệ thống sử dụng số câu trên đề làm giá trị mặc định. |
| Thời lượng | Với đồng hồ riêng, khai báo số phút cho từng phần. Với đồng hồ chung, khai báo thời lượng một lần ở cấp bài thi. |
| Điểm tối đa | Ghi nhận ở cấp bài, phần theo cấu trúc của kỳ thi. |

Ví dụ, trong phần Sử dụng ngôn ngữ của V-ACT, Tiếng Việt và Tiếng Anh đều bắt buộc nên học viên làm đủ 60 câu. Trong phần 3 của QDA, nhiều mã tổ hợp có thể được mở, nhưng mỗi học viên chỉ chọn một mã và làm 50 câu.

### 5.3. Tổng hợp và kiểm tra cấu trúc

1. Số phần thi. Hệ thống tổng hợp từ các phần thuộc phạm vi bài đang thiết lập. Bài HSA toàn phần có ba phần, còn bài luyện riêng phần Toán chỉ có phần Toán.
2. Tổng số câu phải làm. Hệ thống cộng các phần bắt buộc và số câu của phương án học viên chọn. Ví dụ, QDA toàn phần có tổng 150 câu cho mỗi phương án hợp lệ, kể cả khi quản trị viên mở đủ năm mã.
3. Tổng thời gian. Với đồng hồ riêng, hệ thống cộng thời lượng các phần học viên thực hiện. Với đồng hồ chung, hệ thống lấy thời lượng đã khai báo ở cấp bài thi.
4. Thang điểm. Hệ thống ghi nhận thang điểm theo cấu trúc áp dụng. Với bài sử dụng phương pháp tính điểm riêng như V-ACT, cần lưu riêng thang điểm tham chiếu và cách tính điểm luyện tập.
5. Điều kiện hoàn tất. Bài thi phải có ít nhất một phần, xác định được số câu phải làm, thời lượng và các phương án lựa chọn hợp lệ. Nếu một phương án có tổng số câu hoặc thời lượng sai, hệ thống chỉ rõ phần cần điều chỉnh. Quản trị viên được lưu nháp để tiếp tục hoàn thiện.
6. Thay đổi phân loại. Khi quản trị viên thay đổi kỳ thi, môn, phạm vi hoặc phiên bản sau khi đã chỉnh sửa cấu trúc, hệ thống thông báo những phần sẽ được nạp lại và chỉ thay thế sau khi quản trị viên xác nhận.

## 6. Thiết lập khối câu hỏi

Khối câu hỏi dùng để giữ cấu trúc chuyên môn và thứ tự các dạng bài trong một phần thi.

Chi tiết xem tại các phần thiết lập cấu trúc theo từng chi tiết đề

## 7. Thiết lập ma trận lựa chọn câu hỏi

### 7.1. Cấu hình phân bổ

Trong mỗi khối, quản trị viên xác định phân bổ theo:

- Khối lớp: Lớp 10, 11, 12.
- Độ khó: Nhận biết, Thông hiểu, Vận dụng, Vận dụng cao.
- MAPID: Khi cần giới hạn hoặc phân bổ theo kiến thức.

Hệ thống hỗ trợ hai cách khai báo:

| Cách khai báo | Mô tả |
|---|---|
| Phân bổ riêng theo từng chiều | Khai báo tổng số câu theo khối lớp và tổng số câu theo độ khó; hệ thống lựa chọn sao cho đáp ứng đồng thời cả hai |
| Phân bổ kết hợp khối lớp × độ khó | Khai báo trực tiếp số câu cho từng ô, ví dụ Lớp 10–NB, Lớp 11–TH |

Mỗi khối sử dụng một cách khai báo phân bổ. Nếu chuyển cách khai báo, hệ thống hiển thị dữ liệu chuyển đổi để quản trị viên kiểm tra; không âm thầm giữ hai bộ ràng buộc mâu thuẫn.

### 7.2. Ví dụ ma trận kết hợp

Khối gồm 20 câu:

| Khối lớp | Nhận biết | Thông hiểu | Vận dụng | Vận dụng cao | Tổng |
|---|---|---|---|---|---|
| Lớp 10 | 2 | 1 | 1 | 0 | 4 |
| Lớp 11 | 2 | 2 | 1 | 1 | 6 |
| Lớp 12 | 2 | 4 | 3 | 1 | 10 |
| Tổng | 6 | 7 | 5 | 2 | 20 |

Mỗi câu được chọn phải thuộc đúng khối lớp và độ khó của ô tương ứng.

### 7.3. Nhập theo số câu hoặc tỷ lệ

- Cho phép chọn chế độ nhập Số câu hoặc Tỷ lệ %.
- Số câu phải là số nguyên không âm.
- Tổng số câu phải bằng chỉ tiêu của khối.
- Nếu nhập tỷ lệ, tổng tỷ lệ của từng phân bổ phải bằng 100%.
- Hệ thống quy đổi tỷ lệ thành số nguyên và hiển thị kết quả trước khi công bố.

Quy tắc quy đổi đề xuất:

1. Tính số câu lý thuyết theo tỷ lệ.
2. Lấy phần nguyên.
3. Phân bổ số câu còn thiếu cho các mục có phần thập phân lớn nhất.
4. Nếu bằng nhau, ưu tiên theo thứ tự hiển thị.
5. Quản trị viên kiểm tra số câu cuối cùng trước khi công bố.

Cấu hình công bố lưu số câu nguyên làm căn cứ sinh đề. Mặc định phải đáp ứng đúng số câu này; hệ thống không tự nới tỷ lệ khi thiếu nguồn.

### 7.4. Lựa chọn nhiều MAPID

Mỗi phạm vi MAPID có chế độ đối chiếu rõ ràng, Có ít nhất một mã trong danh sách đã chọn

Nếu thiết lập chỉ tiêu riêng cho từng nhóm MAPID:

- Tổng chỉ tiêu phải bằng số câu cần phân bổ theo kiến thức.
- Một câu thuộc nhiều nhóm chỉ được phân bổ vào một chỉ tiêu trong lần sinh đề.
- Không cộng một câu nhiều lần để báo đủ ma trận.
- Phần kiểm tra phải phát hiện các nhóm MAPID có nguồn giao nhau.

Không bắt buộc chia số câu riêng cho từng MAPID nếu yêu cầu chỉ là lấy ngẫu nhiên trong một phạm vi kiến thức.

### 7.5. Trường hợp mức độ gộp

Nếu tài liệu chuyên môn yêu cầu nhóm như NB–TH, cấu hình có thể đặt chỉ tiêu cho tập hai mức độ.

Ví dụ: “10 câu thuộc NB hoặc TH” nghĩa là tổng hai mức bằng 10; không mặc định thành 5 NB và 5 TH.

Nếu cần tỷ lệ cụ thể giữa NB và TH, quản trị viên phải tách thành hai chỉ tiêu.

## 8. Nguồn câu hỏi và quy tắc ghép đề

### 8.1. Điều kiện nguồn hợp lệ

Một câu hỏi được đưa vào nguồn khi:

- Thuộc ngân hàng mà bài thi được phép sử dụng.
- Có đầy đủ nội dung, đáp án và tài nguyên cần thiết.
- Có dữ liệu phân loại đáp ứng các tiêu chí của ma trận.
- Thuộc dạng câu và dạng bài được cấu hình.
- Không bị ngừng sử dụng.
- Có phiên bản hợp lệ để tạo lượt thi mới.

Việc không hiển thị trường trạng thái trên biểu mẫu câu hỏi không bỏ qua bước kiểm tra tính hợp lệ của nguồn.

### 8.2. Lấy nguyên bộ

Với Question Set và Cloze:

- Kiểm tra toàn bộ các câu con.
- Cộng số câu theo phân loại thực tế của từng câu con.
- Chọn bộ chỉ khi toàn bộ bộ đáp ứng cấu trúc và không làm vượt chỉ tiêu.
- Nếu phạm vi MAPID áp dụng cho toàn khối, các câu con phải đáp ứng phạm vi đó.
- Không chỉ dựa vào nhãn của bộ để suy ra mọi câu con có cùng khối lớp hoặc độ khó.

Ví dụ: Ma trận cần 3 NB và 2 TH; một bộ có 4 NB và 1 TH không được xem là tương đương chỉ vì cùng có năm câu.

### 8.3. Loại trùng

- Không lặp lại cùng một câu trong cùng đề.
- Không lấy nhiều phiên bản của cùng một câu trong cùng đề.
- Không chọn câu con vừa nằm trong bộ vừa xuất hiện độc lập.
- Các khối và phần phải dùng chung cơ chế kiểm tra trùng cho toàn đề.

Giữa các lượt thi khác nhau có thể trùng một số câu. Không cam kết mỗi lượt là đề hoàn toàn mới nếu chưa có chính sách tránh lặp riêng và nguồn đủ lớn.

### 8.4. Điểm của đề

- Câu đơn mặc định một điểm; bộ lấy tổng điểm câu con.
- Điểm thực tế được lấy từ phiên bản câu hỏi đã chọn và lưu trong lượt thi.
- Tỷ lệ ma trận tính theo số câu, không mặc định tính theo điểm.

Nếu ngân hàng có các câu khác điểm, cùng một ma trận số câu có thể sinh ra các đề khác tổng điểm. Vì vậy, với bài thi yêu cầu tổng điểm cố định, cấu hình phải có ràng buộc điểm phù hợp và kiểm tra trước khi công bố; không chỉ dựa vào số câu.

## 9. Kiểm tra cấu hình

### 9.1. Luồng thực hiện

1. Quản trị viên chọn Kiểm tra cấu hình.
2. Hệ thống kiểm tra cấu trúc và các chỉ tiêu.
3. Xác định nguồn hợp lệ cho từng khối/dòng ma trận.
4. Kiểm tra nguồn giao nhau và các bộ nguyên vẹn.
5. Tìm một phương án ghép đề đáp ứng toàn bộ ràng buộc.
6. Hiển thị kết quả và vị trí cần xử lý.

### 9.2. Các nhóm kiểm tra

| Nhóm | Nội dung kiểm tra |
|---|---|
| Thông tin | Trường bắt buộc, danh mục hợp lệ |
| Cấu trúc | Phần/môn/tổ hợp đầy đủ, số câu và thời lượng hợp lệ |
| Ma trận | Tổng phân bổ đúng, không có chỉ tiêu âm hoặc mâu thuẫn |
| Nguồn | Đủ câu đáp ứng các điều kiện |
| Bộ câu hỏi | Số bộ, số câu con và phân bổ tương thích |
| Giao nhau | Không dùng một câu để đáp ứng nhiều chỉ tiêu |
| Điểm | Đáp ứng ràng buộc điểm nếu bài có cấu hình |
| Hiển thị | Ngữ liệu, công thức, hình ảnh và đáp án đầy đủ |

Phải kiểm tra tất cả tổ hợp được mở cho học viên, không chỉ tổ hợp đầu tiên.

### 9.3. Kết quả kiểm tra

Hiển thị theo từng phần/khối:

| Thông tin | Mô tả |
|---|---|
| Số câu yêu cầu | Chỉ tiêu cần đáp ứng |
| Nguồn phù hợp | Số câu thỏa bộ lọc trước khi giải quyết trùng giữa các chỉ tiêu |
| Kết quả ghép | Có hoặc chưa tìm được phương án thỏa toàn bộ ràng buộc |
| Vấn đề | Thiếu nguồn, nguồn giao nhau, bộ không phù hợp hoặc cấu hình sai |
| Vị trí xử lý | Liên kết đến khối hoặc dòng ma trận tương ứng |

Phân biệt ba kết quả:

- Hợp lệ: Đã tìm được phương án đáp ứng.
- Không hợp lệ: Có lỗi xác định, chẳng hạn tổng số câu sai hoặc nguồn ít hơn chỉ tiêu.
- Chưa xác nhận được khả năng sinh: Quá trình kiểm tra chưa tìm được phương án trong giới hạn xử lý; không được kết luận chắc chắn là ngân hàng không có phương án.

Hai trường hợp sau không được công bố cho phạm vi đang lỗi.

### 9.4. Thông báo mẫu

- “Khối Toán – Trả lời ngắn cần 5 câu Lớp 12/Vận dụng, hiện có 3 câu phù hợp.”
- “Hai nhóm MAPID đang dùng chung nguồn; chưa đủ câu duy nhất để đáp ứng tổng chỉ tiêu.”
- “Khối Đọc hiểu yêu cầu 2 bộ × 5 câu, nguồn hiện chỉ có 1 bộ hợp lệ.”
- “Tổng phân bổ là 49 câu, yêu cầu của phần thi là 50 câu.”

## 10. Sinh đề xem trước

### 10.1. Luồng thực hiện

1. Quản trị viên chọn phương án/tổ hợp cần xem.
2. Chọn Sinh đề xem trước.
3. Hệ thống sử dụng cơ chế sinh đề thực tế.
4. Hiển thị đề hoàn chỉnh và bảng thống kê phân bổ.
5. Quản trị viên có thể sinh lại hoặc quay về chỉnh sửa.

### 10.2. Nội dung hiển thị

- Cấu trúc các phần và khối.
- Câu hỏi, ngữ liệu, câu con và thứ tự thực tế.
- Số câu và tổng điểm.
- Thống kê theo khối lớp, độ khó và MAPID.
- Đối chiếu chỉ tiêu yêu cầu với kết quả sinh.
- Đáp án và lời giải trong chế độ quản trị.

Đề xem trước phải được đánh dấu rõ, không trừ lượt thi và không tạo kết quả học tập.

Sinh thử thành công một lần không bảo đảm mọi tổ hợp đều hợp lệ; kết quả kiểm tra tổng thể vẫn là điều kiện công bố.

## 11. Công bố và cập nhật ma trận

### 11.1. Công bố lần đầu

1. Quản trị viên lưu cấu hình.
2. Thực hiện kiểm tra.
3. Xử lý toàn bộ lỗi chặn.
4. Chọn Công bố.
5. Hệ thống lưu một phiên bản cấu hình để sử dụng cho lượt thi mới.

### 11.2. Chỉnh sửa sau công bố

- Chỉnh sửa tạo bản cấu hình đang làm việc.
- Bản đã công bố tiếp tục phục vụ trong thời gian chỉnh sửa.
- Bản mới chỉ có hiệu lực sau khi kiểm tra và công bố thành công.
- Lượt thi mới sử dụng bản công bố mới nhất phù hợp với quyền đã mua.
- Lượt thi đang diễn ra và kết quả cũ giữ nguyên snapshot.

Thay đổi phạm vi sản phẩm, chẳng hạn từ FULL thành bài lẻ hoặc bỏ tổ hợp đã bán, phải xử lý theo quyền sử dụng đã cấp; không tự áp dụng làm giảm quyền của người mua.

### 11.3. Khi nguồn câu hỏi thay đổi

Việc thêm, sửa hoặc ngừng sử dụng câu hỏi có thể làm kết quả kiểm tra trước đó không còn phù hợp.

Hệ thống phải:

- Nhận biết cấu hình cần kiểm tra lại.
- Kiểm tra nguồn hiện tại trước khi tạo đề thực tế.
- Không áp dụng phiên bản câu hỏi thiếu dữ liệu.
- Không sửa câu hỏi đã chốt trong lượt thi cũ.

## 12. Sinh đề khi học viên bắt đầu thi

### 12.1. Luồng xử lý

1. Xác định tài khoản, quyền sử dụng, lượt còn lại và thời hạn.
2. Xác nhận phần/tổ hợp được học viên chọn thuộc quyền cho phép.
3. Chốt phiên bản cấu hình áp dụng.
4. Xác định nguồn câu hỏi hợp lệ.
5. Lựa chọn ngẫu nhiên câu/bộ đáp ứng toàn bộ ma trận.
6. Sắp xếp theo thứ tự khối và quy tắc đảo.
7. Kiểm tra lại số câu, phân bổ, trùng câu và điểm.
8. Lưu snapshot đề và khởi tạo lượt thi.
9. Chuyển học viên vào luồng làm bài hiện có.

### 12.2. Quy tắc xử lý lỗi

- Nếu không sinh được đề hợp lệ, không trả về đề thiếu câu.
- Không bắt đầu tính giờ khi chưa khởi tạo thành công lượt thi.
- Không làm mất lượt do lỗi sinh đề.
- Không tự thay tổ hợp hoặc nới điều kiện ma trận.
- Ghi nhận nguyên nhân để quản trị viên xử lý.
- Nhấn bắt đầu nhiều lần hoặc gửi lại yêu cầu không được tạo nhiều lượt thi cho cùng một thao tác khởi tạo.

### 12.3. Dữ liệu snapshot

Snapshot tối thiểu gồm:

- Phiên bản bài thi và ma trận.
- Phần/tổ hợp đã chọn.
- Danh sách câu và phiên bản câu.
- Nội dung, ngữ liệu và tài nguyên.
- Thứ tự câu, câu con và lựa chọn trả lời.
- Đáp án, quy tắc chấm và điểm.
- Khối lớp, độ khó, MAPID tại thời điểm tạo đề.
- Thời lượng và quy tắc chuyển phần.

Đáp án và quy tắc chấm được lưu phía hệ thống; không gửi kèm dữ liệu đề làm bài cho học viên.

## 13. Phân quyền và nhật ký

Các quyền được kiểm soát riêng theo vai trò: xem, tạo, chỉnh sửa, kiểm tra, xem trước và công bố.

Nhật ký ghi nhận:

- Người thực hiện và thời điểm.
- Bài thi, phiên bản bị tác động.
- Nội dung cấu hình trước và sau thay đổi.
- Kết quả kiểm tra/công bố.
- Lỗi sinh đề và cấu hình liên quan.

## 14. Tiêu chí chấp nhận

| Mã | Tiêu chí |
|---|---|
| AC-04.1 | Ma trận được tạo và chỉnh sửa trực tiếp trong bài thi |
| AC-04.2 | Hỗ trợ FULL và bài lẻ; mỗi đường lựa chọn có số câu, môn và thời lượng xác định |
| AC-04.3 | Tổng số câu các phần, khối và chỉ tiêu ma trận được kiểm tra nhất quán |
| AC-04.4 | Hỗ trợ phân bổ riêng theo khối lớp/độ khó hoặc theo bảng kết hợp; đề sinh đáp ứng đồng thời các chỉ tiêu |
| AC-04.5 | Tỷ lệ được quy đổi thành số câu nguyên, hiển thị cho quản trị viên kiểm tra trước khi công bố |
| AC-04.6 | MAPID cho phép chọn nhiều; chế độ đối chiếu được xác định rõ và không đếm trùng câu |
| AC-04.7 | Question Set/Cloze được chọn nguyên bộ; số câu và phân bổ tính theo câu con |
| AC-04.8 | Không lặp câu hoặc các phiên bản của cùng câu trong một đề |
| AC-04.9 | Kiểm tra toàn bộ cấu hình và nguồn giao nhau; đủ câu từng dòng không tự động được coi là hợp lệ |
| AC-04.10 | Tất cả tổ hợp mở cho học viên đều phải được kiểm tra trước khi công bố |
| AC-04.11 | Đề xem trước sử dụng cùng quy tắc sinh với đề thực tế và có bảng đối chiếu phân bổ |
| AC-04.12 | Lỗi sinh đề không tạo đề thiếu, không bắt đầu tính giờ và không làm mất lượt |
| AC-04.13 | Lượt thi mới sử dụng cấu hình công bố phù hợp; thay đổi sau đó không tác động snapshot đã tạo |
| AC-04.14 | Thứ tự khối cố định và tính liền nhau của các câu trong bộ được bảo toàn |
| AC-04.15 | Tổng điểm được tính đúng từ câu đã chọn; bài yêu cầu tổng điểm cố định phải kiểm tra được ràng buộc này |
| AC-04.16 | Có kiểm soát quyền và nhật ký thay đổi, công bố, lỗi sinh đề |
