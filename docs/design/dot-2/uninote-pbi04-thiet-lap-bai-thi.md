Skip to main content

UNIAI Team

Search…Ctrl+KAsk UNI

- Home

- Inbox

Work

- Tasks

- My tasks

- Projects

- Calendar

Communication

- Email Hub

- Meetings

- Documents

- Chat

- People

- Collapse sidebar

DocumentsTài liệu Thi 360Thiết lập bài thi và ma trận đề

View only

Discussion

You can read and download the committed copy, but not edit it. Editing needs the edit level.

# Thiết lập bài thi và ma trận đề

Updated 05:27 PM by Hưng Nguyễn Duy0 comments

## 1. Thông tin chung

### 1.1. Mục tiêu

Cho phép quản trị viên thiết lập cấu trúc bài thi và ma trận lựa chọn câu hỏi từ ngân hàng. Khi học viên bắt đầu một lượt thi, hệ thống tự động tạo đề theo cấu hình, bảo đảm số lượng câu, phân bổ khối lớp, độ khó, phạm vi kiến thức và quy tắc nhóm câu hỏi.

Ma trận được cấu hình trực tiếp trong bài thi, không yêu cầu tạo một đối tượng ma trận riêng trước khi tạo bài thi.

### 1.2. Tác nhân

-

Platform Admin/người được phân quyền: Tạo, chỉnh sửa, kiểm tra, xem trước và công bố cấu hình bài thi.

-

Hệ thống: Kiểm tra nguồn câu hỏi và sinh đề theo ma trận.

-

Học viên: Lựa chọn phần thi hoặc tổ hợp được phép và thực hiện đề đã sinh.

### 1.3. Phạm vi

PBI bao gồm:

-

Khai báo thông tin bài thi.

-

Thiết lập cấu trúc phần thi, môn thi và tổ hợp.

-

Thiết lập ma trận câu hỏi.

-

Kiểm tra tính hợp lệ và khả năng sinh đề.

-

Sinh đề xem trước.

-

Công bố và quản lý phiên bản cấu hình.

-

Sinh đề thực tế và lưu bản chụp dữ liệu của lượt thi.

Giá bán, thanh toán và cấp quyền được quản lý theo chức năng tương ứng. PBI này kiểm tra quyền trước khi tạo lượt thi.

### 1.4. Nguyên tắc thiết kế

-

Tái sử dụng bốn dạng câu hỏi: Trắc nghiệm, Short Answer, Cloze và Question Set.

-

Số câu được tính theo câu đơn hoặc câu con được chấm độc lập.

-

Ngữ liệu chung không được tính thêm là một câu.

-

Mỗi câu chỉ xuất hiện một lần trong một đề.

-

Question Set và Cloze được lấy nguyên bộ; không tự cắt câu con để đáp ứng ma trận.

-

Khối lớp và độ khó là các điều kiện phải được đáp ứng đồng thời.

-

MAPID cho phép chọn nhiều giá trị.

-

Không tự giảm số câu, thay đổi tỷ lệ hoặc lấy câu ngoài phạm vi để sinh bằng được một đề.

Điểm cần thống nhất: Ma trận có đủ câu ở từng dòng chưa chắc sinh được đề hoàn chỉnh. Các dòng có thể cùng sử dụng một nguồn câu hỏi, hoặc các bộ câu hỏi không ghép vừa số lượng yêu cầu. Vì vậy, phải kiểm tra toàn bộ cấu hình, không chỉ đếm nguồn từng dòng.

## 2. Thuật ngữ và cách tính

Thuật ngữ

Ý nghĩa

Bài thi

Cấu hình tổng thể mà học viên được thực hiện

Phần thi

Một phần trong bài, có thứ tự và thời lượng theo cấu hình

Khối câu hỏi

Nhóm câu trong một phần, cùng phạm vi lựa chọn hoặc quy tắc hiển thị

Dòng ma trận

Một chỉ tiêu gồm điều kiện lựa chọn và số câu cần lấy

Câu đơn

Câu Trắc nghiệm hoặc Short Answer độc lập

Bộ câu hỏi

Question Set hoặc Cloze gồm nhiều câu con/vị trí trả lời

Nguồn hợp lệ

Các câu hỏi đáp ứng điều kiện sử dụng và điều kiện của ma trận

Phiên bản công bố

Cấu hình đã kiểm tra và được áp dụng cho lượt thi mới

Snapshot lượt thi

Bản lưu cấu hình, câu hỏi, đáp án, điểm và thứ tự thực tế của một lượt thi

Ví dụ: Một Question Set có năm câu con, mỗi câu một điểm, được tính là năm câu và năm điểm. Nếu ba câu thuộc mức Nhận biết và hai câu thuộc mức Thông hiểu thì bộ đóng góp đúng phân bổ 3 NB + 2 TH vào ma trận.

## 3. Bố cục chức năng

Trong màn hình tạo/chỉnh sửa bài thi, bố trí các khu vực:

Khu vực

Nội dung

Thông tin chung

Tên bài, kỳ thi, hình thức FULL/bài lẻ, mô tả

Cấu trúc và ma trận

Phần thi, môn/tổ hợp, khối câu hỏi, số câu và tiêu chí lựa chọn

Kiểm tra và xem trước

Lỗi cấu hình, nguồn câu hỏi, kết quả sinh thử và thống kê phân bổ

Phiên bản và công bố

Lưu cấu hình đang sửa, công bố và xem phiên bản

Các thao tác chính: Lưu, Kiểm tra cấu hình, Sinh đề xem trước, Công bố/Cập nhật công bố.

Bảng tổng hợp cấu trúc luôn hiển thị:

-

Tổng số câu dự kiến.

-

Tổng thời gian.

-

Tổng điểm hoặc quy tắc xác định tổng điểm.

-

Số câu đã phân bổ và còn thiếu.

-

Các lỗi cần xử lý.

## 4. Khai báo thông tin bài thi

Trường

Yêu cầu

Mô tả

Mã bài thi

Bắt buộc

Mã duy nhất; có thể được hệ thống tự sinh

Tên bài thi

Bắt buộc

Tên hiển thị cho quản trị viên và học viên

Kỳ thi

Bắt buộc

Chọn HSA hoặc QDA từ danh mục

Hình thức

Bắt buộc

FULL hoặc bài lẻ

Phần/môn áp dụng

Bắt buộc theo hình thức

Xác định phạm vi của bài lẻ hoặc các phần trong bài FULL

Mô tả

Không bắt buộc

Giới thiệu nội dung và mục tiêu bài thi

Ảnh giới thiệu

Không bắt buộc

Hình ảnh hiển thị tại danh sách hoặc trang giới thiệu

Quy tắc:

-

Chọn kỳ thi và hình thức để nạp mẫu cấu trúc tương ứng.

-

Mẫu chỉ cung cấp cấu hình ban đầu; quản trị viên vẫn phải kiểm tra trước khi công bố.

-

Khi đổi kỳ thi hoặc hình thức sau khi đã cấu hình ma trận, hệ thống cảnh báo phần dữ liệu bị ảnh hưởng và yêu cầu xác nhận trước khi thay thế.

-

Số câu và thời lượng hiển thị cho học viên được lấy từ cấu trúc đã công bố, không nhập lại ở một nơi khác.

## 5. Thiết lập cấu trúc bài thi

### 5.1. Cấu trúc khởi tạo

Theo dữ liệu nghiệp vụ đã cung cấp, hệ thống khởi tạo các mẫu:

Mẫu bài thi

Phần 1

Phần 2

Phần 3

HSA FULL

Toán: 50 câu, 75 phút

Văn: 50 câu, 60 phút

Khoa học hoặc Tiếng Anh: 50 câu, 60 phút

QDA FULL

Toán: 50 câu, 80 phút

Văn: 50 câu, 55 phút

Khoa học hoặc Tiếng Anh: 50 câu, 60 phút

Đây là mẫu cấu hình của hệ thống luyện thi theo đầu vào dự án, không phải cơ chế tự động cập nhật quy chế thi chính thức.

Bài lẻ chỉ gồm phần/môn được lựa chọn, có số câu và thời lượng riêng.

### 5.2. Thông tin phần thi

Trường

Yêu cầu

Quy tắc

Tên phần thi

Bắt buộc

Tên hiển thị

Thứ tự

Bắt buộc

Xác định vị trí trong bài

Môn hoặc nhánh lựa chọn

Bắt buộc

Môn cố định hoặc các phương án được phép chọn

Số câu

Bắt buộc

Số nguyên dương

Thời lượng

Bắt buộc

Số phút lớn hơn 0

Các khối câu hỏi

Bắt buộc

Phân chia cấu trúc và tiêu chí sinh câu

Quy tắc chuyển phần

Bắt buộc theo chế độ thi

Xác định cách chuyển phần và khả năng quay lại phần trước

Mỗi đường lựa chọn hợp lệ của học viên phải tạo thành một cấu trúc đầy đủ, có tổng số câu và thời lượng xác định.

### 5.3. Thiết lập tổ hợp môn

-

Chọn tổ hợp từ danh mục dùng chung.

-

Chỉ các tổ hợp được gắn vào bài thi mới được học viên lựa chọn.

-

Mỗi tổ hợp có danh sách môn theo thứ tự và số câu được phân bổ cho từng môn.

-

Tổng số câu các môn phải bằng số câu của phần thi.

-

Không tự thêm một tổ hợp vào bài đang công bố khi danh mục có thêm mã mới.

Đối với HSA Khoa học: Nếu phần thi gồm 50 câu và ba môn, phải khai báo số câu từng môn, chẳng hạn 17–17–16. Hệ thống không tự chia thành 51 câu rồi bỏ một câu ngẫu nhiên.

Đối với QDA Khoa học: Mẫu hai môn có thể khởi tạo 25–25 theo đầu vào đã cung cấp.

Phân bổ cụ thể cần được lưu rõ trong từng tổ hợp; không để hệ thống tự chọn môn bị giảm số câu ở mỗi lượt thi.

## 6. Thiết lập khối câu hỏi

Khối câu hỏi dùng để giữ cấu trúc chuyên môn và thứ tự các dạng bài trong một phần thi.

Ví dụ: Phần Tiếng Anh có các khối hoàn thành câu, chọn từ, đọc điền và đọc hiểu.

### 6.1. Thông tin khối

Trường

Yêu cầu

Quy tắc

Tên khối

Bắt buộc

Tên quản trị; có thể dùng làm tiêu đề hiển thị

Thứ tự khối

Bắt buộc

Vị trí trong phần thi

Môn học

Bắt buộc

Kế thừa từ phần/môn đang cấu hình

Dạng câu hỏi

Bắt buộc

Trắc nghiệm, Short Answer, Cloze hoặc Question Set

Dạng bài

Không bắt buộc

Chọn khi cần giới hạn một dạng bài chuyên môn

Số câu cần lấy

Bắt buộc

Tính theo câu đơn hoặc câu con

Số bộ và số câu mỗi bộ

Bắt buộc nếu cấu trúc quy định

Ví dụ: ba bộ, mỗi bộ năm câu

Phạm vi MAPID

Không bắt buộc

Chọn nhiều MAPID để giới hạn kiến thức

Bao gồm MAPID cấp dưới

Có giá trị mặc định

Mặc định không; khi bật sẽ lấy cả các mã con

Quy tắc đảo

Bắt buộc

Giữ thứ tự hoặc đảo theo các quy tắc được hỗ trợ

Nếu không chọn MAPID, nguồn gồm các câu thuộc môn và thỏa các điều kiện còn lại; giao diện cần thể hiện rõ “Không giới hạn MAPID”.

### 6.2. Quy tắc cấu trúc

-

Tổng số câu các khối phải bằng số câu của phần/môn.

-

Nếu yêu cầu ba bộ × năm câu, nguồn được lấy phải đáp ứng đúng số bộ và kích thước bộ.

-

Không tự thay một bộ năm câu bằng năm câu độc lập.

-

Không tự thay Cloze bằng Question Set dù có cùng số câu.

-

Thứ tự cố định của các dạng bài Tiếng Anh phải được bảo toàn.

-

Với nhóm Văn được phép đảo, chỉ đảo cả nhóm; các câu trong nhóm vẫn liền nhau.

## 7. Thiết lập ma trận lựa chọn câu hỏi

### 7.1. Cấu hình phân bổ

Trong mỗi khối, quản trị viên xác định phân bổ theo:

-

Khối lớp: Lớp 10, 11, 12.

-

Độ khó: Nhận biết, Thông hiểu, Vận dụng, Vận dụng cao.

-

MAPID: Khi cần giới hạn hoặc phân bổ theo kiến thức.

Hệ thống hỗ trợ hai cách khai báo:

Cách khai báo

Mô tả

Phân bổ riêng theo từng chiều

Khai báo tổng số câu theo khối lớp và tổng số câu theo độ khó; hệ thống lựa chọn sao cho đáp ứng đồng thời cả hai

Phân bổ kết hợp khối lớp × độ khó

Khai báo trực tiếp số câu cho từng ô, ví dụ Lớp 10–NB, Lớp 11–TH

Mỗi khối sử dụng một cách khai báo phân bổ. Nếu chuyển cách khai báo, hệ thống hiển thị dữ liệu chuyển đổi để quản trị viên kiểm tra; không âm thầm giữ hai bộ ràng buộc mâu thuẫn.

### 7.2. Ví dụ ma trận kết hợp

Khối gồm 20 câu:

Khối lớp

Nhận biết

Thông hiểu

Vận dụng

Vận dụng cao

Tổng

Lớp 10

2

1

1

0

4

Lớp 11

2

2

1

1

6

Lớp 12

2

4

3

1

10

Tổng

6

7

5

2

20

Mỗi câu được chọn phải thuộc đúng khối lớp và độ khó của ô tương ứng.

### 7.3. Nhập theo số câu hoặc tỷ lệ

-

Cho phép chọn chế độ nhập Số câu hoặc Tỷ lệ %.

-

Số câu phải là số nguyên không âm.

-

Tổng số câu phải bằng chỉ tiêu của khối.

-

Nếu nhập tỷ lệ, tổng tỷ lệ của từng phân bổ phải bằng 100%.

-

Hệ thống quy đổi tỷ lệ thành số nguyên và hiển thị kết quả trước khi công bố.

Quy tắc quy đổi đề xuất:

-

Tính số câu lý thuyết theo tỷ lệ.

-

Lấy phần nguyên.

-

Phân bổ số câu còn thiếu cho các mục có phần thập phân lớn nhất.

-

Nếu bằng nhau, ưu tiên theo thứ tự hiển thị.

-

Quản trị viên kiểm tra số câu cuối cùng trước khi công bố.

Cấu hình công bố lưu số câu nguyên làm căn cứ sinh đề. Mặc định phải đáp ứng đúng số câu này; hệ thống không tự nới tỷ lệ khi thiếu nguồn.

### 7.4. Lựa chọn nhiều MAPID

Mỗi phạm vi MAPID có chế độ đối chiếu rõ ràng:

Chế độ

Điều kiện câu hỏi

Khớp ít nhất một MAPID

Có ít nhất một mã trong danh sách đã chọn; đề xuất làm mặc định

Khớp tất cả MAPID

Có đầy đủ các mã đã chọn

Nếu thiết lập chỉ tiêu riêng cho từng nhóm MAPID:

-

Tổng chỉ tiêu phải bằng số câu cần phân bổ theo kiến thức.

-

Một câu thuộc nhiều nhóm chỉ được phân bổ vào một chỉ tiêu trong lần sinh đề.

-

Không cộng một câu nhiều lần để báo đủ ma trận.

-

Phần kiểm tra phải phát hiện các nhóm MAPID có nguồn giao nhau.

Không bắt buộc chia số câu riêng cho từng MAPID nếu yêu cầu chỉ là lấy ngẫu nhiên trong một phạm vi kiến thức.

### 7.5. Trường hợp mức độ gộp

Nếu tài liệu chuyên môn yêu cầu nhóm như NB–TH, cấu hình có thể đặt chỉ tiêu cho tập hai mức độ.

Ví dụ: “10 câu thuộc NB hoặc TH” nghĩa là tổng hai mức bằng 10; không mặc định thành 5 NB và 5 TH.

Nếu cần tỷ lệ cụ thể giữa NB và TH, quản trị viên phải tách thành hai chỉ tiêu.

## 8. Nguồn câu hỏi và quy tắc ghép đề

### 8.1. Điều kiện nguồn hợp lệ

Một câu hỏi được đưa vào nguồn khi:

-

Thuộc ngân hàng mà bài thi được phép sử dụng.

-

Có đầy đủ nội dung, đáp án và tài nguyên cần thiết.

-

Có dữ liệu phân loại đáp ứng các tiêu chí của ma trận.

-

Thuộc dạng câu và dạng bài được cấu hình.

-

Không bị ngừng sử dụng.

-

Có phiên bản hợp lệ để tạo lượt thi mới.

Việc không hiển thị trường trạng thái trên biểu mẫu câu hỏi không bỏ qua bước kiểm tra tính hợp lệ của nguồn.

### 8.2. Lấy nguyên bộ

Với Question Set và Cloze:

-

Kiểm tra toàn bộ các câu con.

-

Cộng số câu theo phân loại thực tế của từng câu con.

-

Chọn bộ chỉ khi toàn bộ bộ đáp ứng cấu trúc và không làm vượt chỉ tiêu.

-

Nếu phạm vi MAPID áp dụng cho toàn khối, các câu con phải đáp ứng phạm vi đó.

-

Không chỉ dựa vào nhãn của bộ để suy ra mọi câu con có cùng khối lớp hoặc độ khó.

Ví dụ: Ma trận cần 3 NB và 2 TH; một bộ có 4 NB và 1 TH không được xem là tương đương chỉ vì cùng có năm câu.

### 8.3. Loại trùng

-

Không lặp lại cùng một câu trong cùng đề.

-

Không lấy nhiều phiên bản của cùng một câu trong cùng đề.

-

Không chọn câu con vừa nằm trong bộ vừa xuất hiện độc lập.

-

Các khối và phần phải dùng chung cơ chế kiểm tra trùng cho toàn đề.

Giữa các lượt thi khác nhau có thể trùng một số câu. Không cam kết mỗi lượt là đề hoàn toàn mới nếu chưa có chính sách tránh lặp riêng và nguồn đủ lớn.

### 8.4. Điểm của đề

-

Câu đơn mặc định một điểm; bộ lấy tổng điểm câu con.

-

Điểm thực tế được lấy từ phiên bản câu hỏi đã chọn và lưu trong lượt thi.

-

Tỷ lệ ma trận tính theo số câu, không mặc định tính theo điểm.

Nếu ngân hàng có các câu khác điểm, cùng một ma trận số câu có thể sinh ra các đề khác tổng điểm. Vì vậy, với bài thi yêu cầu tổng điểm cố định, cấu hình phải có ràng buộc điểm phù hợp và kiểm tra trước khi công bố; không chỉ dựa vào số câu.

## 9. Kiểm tra cấu hình

### 9.1. Luồng thực hiện

-

Quản trị viên chọn Kiểm tra cấu hình.

-

Hệ thống kiểm tra cấu trúc và các chỉ tiêu.

-

Xác định nguồn hợp lệ cho từng khối/dòng ma trận.

-

Kiểm tra nguồn giao nhau và các bộ nguyên vẹn.

-

Tìm một phương án ghép đề đáp ứng toàn bộ ràng buộc.

-

Hiển thị kết quả và vị trí cần xử lý.

### 9.2. Các nhóm kiểm tra

Nhóm

Nội dung kiểm tra

Thông tin

Trường bắt buộc, danh mục hợp lệ

Cấu trúc

Phần/môn/tổ hợp đầy đủ, số câu và thời lượng hợp lệ

Ma trận

Tổng phân bổ đúng, không có chỉ tiêu âm hoặc mâu thuẫn

Nguồn

Đủ câu đáp ứng các điều kiện

Bộ câu hỏi

Số bộ, số câu con và phân bổ tương thích

Giao nhau

Không dùng một câu để đáp ứng nhiều chỉ tiêu

Điểm

Đáp ứng ràng buộc điểm nếu bài có cấu hình

Hiển thị

Ngữ liệu, công thức, hình ảnh và đáp án đầy đủ

Phải kiểm tra tất cả tổ hợp được mở cho học viên, không chỉ tổ hợp đầu tiên.

### 9.3. Kết quả kiểm tra

Hiển thị theo từng phần/khối:

Thông tin

Mô tả

Số câu yêu cầu

Chỉ tiêu cần đáp ứng

Nguồn phù hợp

Số câu thỏa bộ lọc trước khi giải quyết trùng giữa các chỉ tiêu

Kết quả ghép

Có hoặc chưa tìm được phương án thỏa toàn bộ ràng buộc

Vấn đề

Thiếu nguồn, nguồn giao nhau, bộ không phù hợp hoặc cấu hình sai

Vị trí xử lý

Liên kết đến khối hoặc dòng ma trận tương ứng

Phân biệt ba kết quả:

-

Hợp lệ: Đã tìm được phương án đáp ứng.

-

Không hợp lệ: Có lỗi xác định, chẳng hạn tổng số câu sai hoặc nguồn ít hơn chỉ tiêu.

-

Chưa xác nhận được khả năng sinh: Quá trình kiểm tra chưa tìm được phương án trong giới hạn xử lý; không được kết luận chắc chắn là ngân hàng không có phương án.

Hai trường hợp sau không được công bố cho phạm vi đang lỗi.

### 9.4. Thông báo mẫu

-

“Khối Toán – Trả lời ngắn cần 5 câu Lớp 12/Vận dụng, hiện có 3 câu phù hợp.”

-

“Hai nhóm MAPID đang dùng chung nguồn; chưa đủ câu duy nhất để đáp ứng tổng chỉ tiêu.”

-

“Khối Đọc hiểu yêu cầu 2 bộ × 5 câu, nguồn hiện chỉ có 1 bộ hợp lệ.”

-

“Tổng phân bổ là 49 câu, yêu cầu của phần thi là 50 câu.”

## 10. Sinh đề xem trước

### 10.1. Luồng thực hiện

-

Quản trị viên chọn phương án/tổ hợp cần xem.

-

Chọn Sinh đề xem trước.

-

Hệ thống sử dụng cơ chế sinh đề thực tế.

-

Hiển thị đề hoàn chỉnh và bảng thống kê phân bổ.

-

Quản trị viên có thể sinh lại hoặc quay về chỉnh sửa.

### 10.2. Nội dung hiển thị

-

Cấu trúc các phần và khối.

-

Câu hỏi, ngữ liệu, câu con và thứ tự thực tế.

-

Số câu và tổng điểm.

-

Thống kê theo khối lớp, độ khó và MAPID.

-

Đối chiếu chỉ tiêu yêu cầu với kết quả sinh.

-

Đáp án và lời giải trong chế độ quản trị.

Đề xem trước phải được đánh dấu rõ, không trừ lượt thi và không tạo kết quả học tập.

Sinh thử thành công một lần không bảo đảm mọi tổ hợp đều hợp lệ; kết quả kiểm tra tổng thể vẫn là điều kiện công bố.

## 11. Công bố và cập nhật ma trận

### 11.1. Công bố lần đầu

-

Quản trị viên lưu cấu hình.

-

Thực hiện kiểm tra.

-

Xử lý toàn bộ lỗi chặn.

-

Chọn Công bố.

-

Hệ thống lưu một phiên bản cấu hình để sử dụng cho lượt thi mới.

### 11.2. Chỉnh sửa sau công bố

-

Chỉnh sửa tạo bản cấu hình đang làm việc.

-

Bản đã công bố tiếp tục phục vụ trong thời gian chỉnh sửa.

-

Bản mới chỉ có hiệu lực sau khi kiểm tra và công bố thành công.

-

Lượt thi mới sử dụng bản công bố mới nhất phù hợp với quyền đã mua.

-

Lượt thi đang diễn ra và kết quả cũ giữ nguyên snapshot.

Thay đổi phạm vi sản phẩm, chẳng hạn từ FULL thành bài lẻ hoặc bỏ tổ hợp đã bán, phải xử lý theo quyền sử dụng đã cấp; không tự áp dụng làm giảm quyền của người mua.

### 11.3. Khi nguồn câu hỏi thay đổi

Việc thêm, sửa hoặc ngừng sử dụng câu hỏi có thể làm kết quả kiểm tra trước đó không còn phù hợp.

Hệ thống phải:

-

Nhận biết cấu hình cần kiểm tra lại.

-

Kiểm tra nguồn hiện tại trước khi tạo đề thực tế.

-

Không áp dụng phiên bản câu hỏi thiếu dữ liệu.

-

Không sửa câu hỏi đã chốt trong lượt thi cũ.

## 12. Sinh đề khi học viên bắt đầu thi

### 12.1. Luồng xử lý

-

Xác định tài khoản, quyền sử dụng, lượt còn lại và thời hạn.

-

Xác nhận phần/tổ hợp được học viên chọn thuộc quyền cho phép.

-

Chốt phiên bản cấu hình áp dụng.

-

Xác định nguồn câu hỏi hợp lệ.

-

Lựa chọn ngẫu nhiên câu/bộ đáp ứng toàn bộ ma trận.

-

Sắp xếp theo thứ tự khối và quy tắc đảo.

-

Kiểm tra lại số câu, phân bổ, trùng câu và điểm.

-

Lưu snapshot đề và khởi tạo lượt thi.

-

Chuyển học viên vào luồng làm bài hiện có.

### 12.2. Quy tắc xử lý lỗi

-

Nếu không sinh được đề hợp lệ, không trả về đề thiếu câu.

-

Không bắt đầu tính giờ khi chưa khởi tạo thành công lượt thi.

-

Không làm mất lượt do lỗi sinh đề.

-

Không tự thay tổ hợp hoặc nới điều kiện ma trận.

-

Ghi nhận nguyên nhân để quản trị viên xử lý.

-

Nhấn bắt đầu nhiều lần hoặc gửi lại yêu cầu không được tạo nhiều lượt thi cho cùng một thao tác khởi tạo.

### 12.3. Dữ liệu snapshot

Snapshot tối thiểu gồm:

-

Phiên bản bài thi và ma trận.

-

Phần/tổ hợp đã chọn.

-

Danh sách câu và phiên bản câu.

-

Nội dung, ngữ liệu và tài nguyên.

-

Thứ tự câu, câu con và lựa chọn trả lời.

-

Đáp án, quy tắc chấm và điểm.

-

Khối lớp, độ khó, MAPID tại thời điểm tạo đề.

-

Thời lượng và quy tắc chuyển phần.

Đáp án và quy tắc chấm được lưu phía hệ thống; không gửi kèm dữ liệu đề làm bài cho học viên.

## 13. Phân quyền và nhật ký

Các quyền được kiểm soát riêng theo vai trò: xem, tạo, chỉnh sửa, kiểm tra, xem trước và công bố.

Nhật ký ghi nhận:

-

Người thực hiện và thời điểm.

-

Bài thi, phiên bản bị tác động.

-

Nội dung cấu hình trước và sau thay đổi.

-

Kết quả kiểm tra/công bố.

-

Lỗi sinh đề và cấu hình liên quan.

## 14. Tiêu chí chấp nhận

Mã

Tiêu chí

AC-04.1

Ma trận được tạo và chỉnh sửa trực tiếp trong bài thi

AC-04.2

Hỗ trợ FULL và bài lẻ; mỗi đường lựa chọn có số câu, môn và thời lượng xác định

AC-04.3

Tổng số câu các phần, khối và chỉ tiêu ma trận được kiểm tra nhất quán

AC-04.4

Hỗ trợ phân bổ riêng theo khối lớp/độ khó hoặc theo bảng kết hợp; đề sinh đáp ứng đồng thời các chỉ tiêu

AC-04.5

Tỷ lệ được quy đổi thành số câu nguyên, hiển thị cho quản trị viên kiểm tra trước khi công bố

AC-04.6

MAPID cho phép chọn nhiều; chế độ đối chiếu được xác định rõ và không đếm trùng câu

AC-04.7

Question Set/Cloze được chọn nguyên bộ; số câu và phân bổ tính theo câu con

AC-04.8

Không lặp câu hoặc các phiên bản của cùng câu trong một đề

AC-04.9

Kiểm tra toàn bộ cấu hình và nguồn giao nhau; đủ câu từng dòng không tự động được coi là hợp lệ

AC-04.10

Tất cả tổ hợp mở cho học viên đều phải được kiểm tra trước khi công bố

AC-04.11

Đề xem trước sử dụng cùng quy tắc sinh với đề thực tế và có bảng đối chiếu phân bổ

AC-04.12

Lỗi sinh đề không tạo đề thiếu, không bắt đầu tính giờ và không làm mất lượt

AC-04.13

Lượt thi mới sử dụng cấu hình công bố phù hợp; thay đổi sau đó không tác động snapshot đã tạo

AC-04.14

Thứ tự khối cố định và tính liền nhau của các câu trong bộ được bảo toàn

AC-04.15

Tổng điểm được tính đúng từ câu đã chọn; bài yêu cầu tổng điểm cố định phải kiểm tra được ràng buộc này

AC-04.16

Có kiểm soát quyền và nhật ký thay đổi, công bố, lỗi sinh đề
