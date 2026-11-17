# Thiết lập cấu trúc chi tiết đề HSA

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-17 10:01, lấy về ngày 2026-11-17. Nguồn: uniworkos, thư mục "Tài liệu Thi 360" (doc `01M7HXJHQ4C22HKEBP4SMTDYZ2`). Chỉ đọc; bản gốc ở uniworkos.

Sau khi quản trị viên lưu **Thông tin chung và cấu trúc tổng quan** ở bước 1, hệ thống chuyển sang bước 2 để thiết lập cách lấy câu hỏi cho từng phần, từng môn. Đầu ra của bước này là một cấu hình mà hệ thống có thể dùng để sinh đề từ ngân hàng. Quản trị viên không phải chọn từng câu hỏi cụ thể.

**Đề xuất về UX:** dùng một bước **Cấu trúc chi tiết**, có danh sách phần/môn cần cấu hình và một vùng chỉnh sửa mục đang chọn. Khi mở Toán, quản trị viên thấy ngay ma trận Toán. Khi mở Văn, quản trị viên thấy ngay 5 nhóm dạng bài theo thứ tự. Khi tạo bài ôn một chủ đề, quản trị viên chọn chủ đề, số câu và cách phân bổ ngay trong cấu hình môn. Như vậy, người dùng có một luồng thao tác thống nhất nhưng không phải điền cùng một biểu mẫu cho mọi môn.

## 1. Điểm vào và phạm vi cần cấu hình

Khi quản trị viên nhấn **Tiếp tục** tại bước 1, hệ thống đọc kỳ thi, phạm vi thi, các phần, số câu và thời lượng đã lưu để khởi tạo bước 2.

| Lựa chọn ở bước 1 | Nội dung xuất hiện ở bước 2 | Mục mở đầu tiên |
|---|---|---|
| **HSA – Bài thi FULL** | Phần 1 – Toán học và xử lí số liệu. Phần 2 – Văn học – Ngôn ngữ. Phần 3 gồm nhánh Khoa học và nhánh Tiếng Anh. Trong Khoa học có Vật lí, Hóa học, Sinh học, Lịch sử và Địa lí. | Phần 1 – Toán |
| **HSA – Bài thi theo phần – Khoa học** | Phần Khoa học, quy tắc chọn 3 trong 5 môn và cấu hình của cả 5 môn. | Tổng quan phần Khoa học |
| **HSA – Bài thi theo phần – Toán, Văn hoặc Tiếng Anh** | Cấu hình của phần thi đã chọn, khởi tạo theo cấu trúc HSA tương ứng. | Phần đã chọn |
| **HSA – Bài thi theo môn** | Chỉ hiển thị môn đã chọn. Quản trị viên có thể lấy câu trong toàn môn hoặc giới hạn theo chủ đề. | Môn đã chọn |

Với bài thi FULL, phần 3 là **một phần có hai nhánh lựa chọn**. Tổng quan phải hiển thị 3 phần, 150 câu và 195 phút theo cấu trúc HSA đã cung cấp. Khi tính tổng, hệ thống chỉ tính một nhánh của phần 3. Tương tự, trong nhánh Khoa học, hệ thống chỉ tính ba môn học viên sẽ chọn.

## 2. Tổ chức màn hình và điều hướng

| Khu vực | Nội dung hiển thị | Hành vi |
|---|---|---|
| **Thông tin bài thi** | Tên bài, kỳ thi, phạm vi thi, tổng số câu và thời lượng từ bước 1. | Có thao tác **Sửa thông tin tổng quan** để quay lại bước 1. Số câu và thời lượng đã nhập không phải khai báo lần hai. |
| **Danh sách phần/môn** | Các phần theo thứ tự làm bài. Riêng phần Khoa học cho phép mở danh sách 5 môn. Mỗi mục hiển thị tình trạng cấu hình. | Chọn một mục để chỉnh sửa. Với bài chỉ có một môn, mở thẳng cấu hình môn đó. |
| **Cấu hình mục đang chọn** | Tên phần/môn, số câu mục tiêu và các trường phù hợp. Toán hiển thị ma trận. Văn và Tiếng Anh hiển thị các nhóm dạng bài. Khoa học hiển thị quy tắc tổ hợp khi chọn cấp phần và ma trận khi chọn từng môn. | Chỉnh sửa trực tiếp. Có thể mở chi tiết một nhóm để khai báo nguồn câu hoặc quy tắc riêng. |
| **Tổng hợp cấu hình** | Số câu mục tiêu, số câu đã phân bổ, số còn thiếu hoặc vượt. Đối với câu chùm, hiển thị cả số chùm và số câu con. | Cập nhật khi người dùng thay đổi dữ liệu. Chọn một lỗi để quay về đúng môn, nhóm hoặc trường cần sửa. |
| **Thao tác** | **Lưu nháp**, **Kiểm tra cấu hình**, **Sinh đề thử**, **Tiếp tục**. | Lưu nháp được phép khi cấu hình chưa hoàn tất. Kiểm tra và sinh thử có thể thực hiện ở phạm vi môn đang chọn. **Tiếp tục** kiểm tra toàn bộ phạm vi của bài thi. |

Khi chuyển sang môn khác hoặc quay lại bước 1 mà còn thay đổi chưa lưu, hệ thống cho phép **Lưu và chuyển**, **Bỏ thay đổi** hoặc **Ở lại**. Khi mở lại bản nháp, hệ thống tải cấu hình đã lưu của từng môn. Các giá trị mặc định chỉ được điền khi khởi tạo lần đầu.

## 3. Quy tắc nhập liệu dùng chung

Trong cấu hình từng môn, cần phân biệt rõ ba thông tin vì chúng quyết định ba hành vi khác nhau của bộ sinh đề.

| Thông tin | Câu hỏi mà quản trị viên cần trả lời | Ví dụ |
|---|---|---|
| **Phạm vi lấy câu** | Hệ thống được phép lấy câu từ đâu? | Toàn bộ ngân hàng Toán hoặc chỉ các câu gắn MAPID *Đồ thị hàm số*. |
| **Phân bổ số câu** | Trong phạm vi trên, cần lấy bao nhiêu câu theo từng tiêu chí? | 35 câu trắc nghiệm, 15 câu điền đáp án. Trong toàn bộ 50 câu có 7 câu vận dụng cao. |
| **Thứ tự và cách ghép câu** | Các câu được đưa vào đề theo thứ tự nào? Câu nào phải đi cùng nhau? | Văn giữ thứ tự 5 nhóm. Một ngữ liệu đọc hiểu đi liền với 5 câu con. |

**Quy tắc đối với MAPID**

- Trường **Phạm vi kiến thức** có hai lựa chọn là **Toàn môn** và **Theo chủ đề**. Chọn **Theo chủ đề** thì xuất hiện trường **Chủ đề/MAPID**, cho phép tìm kiếm và chọn nhiều giá trị thuộc môn hiện tại.
- Khi chọn nhiều MAPID, mặc định lấy các câu thuộc **ít nhất một** MAPID đã chọn. Nếu chọn một chủ đề cha, hệ thống mặc định lấy cả các chủ đề con.
- Nếu chỉ cần giới hạn nguồn, quản trị viên dừng ở thao tác chọn MAPID. Nếu cần kiểm soát số lượng từng chủ đề, chọn **Phân bổ số câu theo chủ đề**. Ví dụ, 6 câu đồ thị và 4 câu cực trị.
- Một câu mang nhiều MAPID chỉ xuất hiện một lần trong đề. Khi có hạn ngạch theo chủ đề, câu đó chỉ được tính vào một hạn ngạch chủ đề. Câu vẫn đồng thời được tính vào phân bố lớp, độ khó và dạng trả lời.

**Quy tắc đối với câu chùm**

Quản trị viên khai báo **số chùm** và **số câu con mỗi chùm**. Hệ thống tự tính số câu đóng góp vào đề. Ví dụ, 5 chùm × 5 câu con đóng góp 25 câu. Khi lấy từ ngân hàng, hệ thống chọn nguyên chùm, giữ ngữ liệu cùng các câu con và tính lớp, độ khó, dạng trả lời theo từng câu con.

## 4. Luồng cấu hình phần Toán

### a. Trạng thái khi mở phần Toán

Nếu bài sử dụng cấu trúc Toán HSA đầy đủ, hệ thống khởi tạo các giá trị dưới đây. Quản trị viên thấy ngay cấu hình có thể sử dụng và chỉ cần chỉnh những chỉ tiêu muốn kiểm soát.

| Nhóm thông tin | Giá trị khởi tạo | Thao tác |
|---|---|---|
| **Mục tiêu** | 50 câu, 75 phút | Hiển thị từ cấu trúc tổng quan. |
| **Nguồn câu** | Toàn môn Toán | Chọn **Giới hạn theo MAPID** nếu cần thu hẹp nguồn. |
| **Dạng trả lời** | 35 câu trắc nghiệm 4 lựa chọn, 15 câu điền đáp án | Nhập số lượng cho từng dạng. Hiển thị tổng ngay dưới bảng. |
| **Phân bố theo lớp** | Lớp 10 có 3 câu, lớp 11 có 20 câu, lớp 12 có 27 câu | Sửa số lượng từng lớp. |
| **Phân bố theo độ khó** | 2 nhận biết, 21 thông hiểu, 20 vận dụng, 7 vận dụng cao | Sửa số lượng từng mức. |
| **Câu chùm** | Ma trận minh họa có một chùm 3 câu tại vị trí 48–50 | Khai báo số lượng câu chùm |
| **Phân bổ theo chủ đề** | Chưa áp dụng | Chọn **Thêm chỉ tiêu chủ đề**, chọn MAPID và nhập số câu cần lấy. |

### b. Luồng thao tác

1. Quản trị viên chọn **Phần 1 – Toán** và xem các phân bố được điền sẵn.
2. Nếu dùng ma trận mặc định, chọn **Kiểm tra cấu hình**. Nếu muốn đề tập trung vào một số nội dung, mở **Phân bổ theo chủ đề** để thêm chỉ tiêu.
3. Khi thêm một chỉ tiêu, quản trị viên chọn MAPID và số câu. Có thể giới hạn thêm lớp hoặc độ khó cho riêng chỉ tiêu đó. Ví dụ, *Hàm số – 2 câu – Vận dụng cao*. Các câu này nằm trong tổng 7 câu vận dụng cao đã khai báo.
4. Hệ thống đối chiếu từng phân bố với mục tiêu 50 câu. Ba câu con trong chùm đã nằm trong tổng 35 câu trắc nghiệm và 15 câu điền đáp án theo dạng trả lời thực tế của chúng.
5. Quản trị viên nhấn **Sinh đề thử** để xem danh sách câu được lấy và bảng so sánh **Yêu cầu / Thực tế** theo từng tiêu chí.

**Ví dụ phản hồi khi nhập:** nếu sửa số câu điền đáp án từ 15 xuống 12, hệ thống hiển thị ngay dưới nhóm **Dạng trả lời** thông báo *“Đã phân bổ 47/50 câu. Còn thiếu 3 câu.”* Quản trị viên có thể lưu nháp và quay lại sửa.

## 5. Luồng cấu hình phần Văn học – Ngôn ngữ

### a. Trạng thái khi mở phần Văn

Hệ thống hiển thị danh sách 5 nhóm theo thứ tự. Mỗi dòng gồm **thứ tự, tên dạng bài, cách lấy, số lượng, nguồn câu và thao tác Sửa**.

| Thứ tự | Nhóm dạng bài | Cấu hình mặc định |
|---|---|---|
| 1 | Chọn từ hoặc cụm từ không cùng nhóm nghĩa | 5 câu đơn |
| 2 | Xác định từ hoặc cụm từ sai về ngữ pháp, ngữ nghĩa, logic hoặc phong cách | 5 câu đơn |
| 3 | Chọn cặp từ hoặc cụm từ thích hợp điền vào chỗ trống | 5 câu đơn |
| 4 | Đọc một ngữ liệu, trả lời một câu hỏi | 10 câu, mỗi câu đi cùng ngữ liệu riêng |
| 5 | Đọc một ngữ liệu, trả lời nhiều câu hỏi | 5 chùm × 5 câu con, tổng 25 câu |

### b. Luồng chỉnh sửa một nhóm

1. Quản trị viên chọn **Sửa** tại nhóm cần điều chỉnh. Hệ thống hiển thị tên nhóm, dạng bài dùng để lọc ngân hàng, cách lấy câu và số lượng.
2. Nếu chọn **Câu đơn**, nhập số câu. Nếu chọn **Câu chùm**, nhập số chùm và số câu con mỗi chùm. Với nhóm 4, nguồn câu phải có ngữ liệu gắn với từng câu.
3. Nếu cần giới hạn nội dung, chọn **Thêm điều kiện** để khai báo MAPID, loại văn bản hoặc mức độ. Ví dụ, tại nhóm 5 có thể yêu cầu mỗi loại văn bản lấy một chùm gồm nghị luận xã hội, thông tin, khoa học – chính luận, thơ và văn xuôi.
4. Khi lưu nhóm, hệ thống tính lại tổng số câu và vị trí của nhóm trong phần Văn.
5. Khi sinh đề, hệ thống lấy ngẫu nhiên nội dung phù hợp cho từng nhóm. Thứ tự nhóm mặc định là 1 đến 5. Quản trị viên có thể cho phép đảo thứ tự các chùm trong nhóm 5. Các câu con của một chùm luôn xuất hiện liền nhau theo thứ tự đã cấu hình.

**Điều kiện để UX này hoạt động:** ngân hàng phải phân biệt được **dạng bài** và **dạng trả lời**. Cả ba nhóm ngôn ngữ đầu tiên đều có thể là trắc nghiệm 4 lựa chọn. Nếu ngân hàng chỉ lưu thuộc tính *Trắc nghiệm*, quản trị viên sẽ không thể cấu hình “lấy đúng 5 câu tìm từ khác nhóm nghĩa”. Trường **Dạng bài** trên câu hỏi phải sử dụng cùng danh mục với trường chọn trong cấu trúc đề.

## 6. Luồng cấu hình phần Khoa học

### a. Chọn cấp phần để xem quy tắc tổ hợp

Khi chọn **Phần 3 – Khoa học**, quản trị viên thấy mục tiêu **50 câu, 60 phút, học viên chọn 3 trong 5 môn**. Bên dưới là danh sách 5 môn. Mỗi dòng hiển thị tên môn, tình trạng cấu hình cho 17 câu, nút **Thiết lập**.

Quản trị viên khai báo đủ 5 môn tại bước này. Đến lượt thi, **học viên** là người chọn 3 môn. Bộ sinh đề dùng đúng cấu hình tương ứng với tổ hợp đã chọn.

| Tổ hợp học viên chọn | Số câu mỗi môn |
|---|---|
| 2 môn tự nhiên và 1 môn xã hội | Mỗi môn tự nhiên 17 câu (Lý, Hoá Sinh), môn xã hội 16 câu (Sử, Địa) |
| 1 môn tự nhiên và 2 môn xã hội | Môn tự nhiên 16 câu (Lý, Hoá, Sinh), mỗi môn xã hội 17 câu (Sử, Địa) |
| 3 môn tự nhiên | Hai môn 17 câu và một môn 16 câu (random bỏ 1 câu của 1 trong 3 môn), tổng 50 câu |

### b. Chọn từng môn để thiết lập ma trận

Khi nhấn **Thiết lập** tại một môn, hệ thống mở ma trận của riêng môn đó. Cấu hình 17 câu được khởi tạo từ dữ liệu tham khảo sau.

| Môn | Phân bố theo **khối lớp** | Phân bố theo **độ khó** |
|---|---|---|
| **Vật lí** | Lớp 10 · 5 câu (30%) Lớp 11 · 6 câu (35%) Lớp 12 · 6 câu (35%) | Nhận biết · 3 câu (18%) Thông hiểu · 1 câu (6%) Vận dụng · 13 câu (76%) |
| **Hóa học** | Lớp 10 · 2 câu (15%) Lớp 11 · 8 câu (45%) Lớp 12 · 7 câu (40%) | Nhận biết · 4 câu (24%) Thông hiểu · 5 câu (29%) Vận dụng · 8 câu (47%) |
| **Sinh học** | Lớp 10 · 3 câu (18%) Lớp 11 · 5 câu (29%) Lớp 12 · 9 câu (53%) | Nhận biết · 7 câu (41%) Thông hiểu · 7 câu (41%) Vận dụng · 3 câu (18%) |
| **Lịch sử** | Lớp 10 · 2 câu (12%) Lớp 11 · 5 câu (29%) Lớp 12 · 10 câu (59%) | Nhận biết · 7 câu (41%) Thông hiểu · 8 câu (47%) Vận dụng · 2 câu (12%) |
| **Địa lí** | Lớp 10 · 2 câu (12%) Lớp 11 · 4 câu (23%) Lớp 12 · 11 câu (65%) | Nhận biết · 5 câu (29%) Thông hiểu · 9 câu (53%) Vận dụng · 3 câu (18%) |

Mỗi môn cho phép khai báo phạm vi MAPID, số câu đơn và 1–2 chùm, mỗi chùm có 3 câu con. Sau khi lưu một môn, quản trị viên có thể chọn **Môn tiếp theo** hoặc quay về tổng quan Khoa học để kiểm tra tiến độ.

### c. Quy tắc sinh đề đối với môn có 16 câu

Quản trị viên chỉ thiết lập **ma trận 17 câu** cho từng môn Khoa học. Khi tổ hợp của học viên yêu cầu một môn có 16 câu, hệ thống tự động **bỏ ngẫu nhiên 1 câu đơn hợp lệ** của môn đó. Tất cả các chùm được giữ nguyên ngữ liệu, số câu con và thứ tự câu con. Quản trị viên không cần khai báo thêm ma trận 16 câu.

**1. Xác định môn cần bỏ một câu**

Khi học viên xác nhận 3 môn tại phần Khoa học, hệ thống xác định môn làm 16 câu theo tổ hợp đã chọn.

| Tổ hợp học viên chọn | Môn làm 16 câu | Hai môn còn lại |
|---|---|---|
| 2 môn tự nhiên và 1 môn xã hội | Môn xã hội | Mỗi môn 17 câu |
| 1 môn tự nhiên và 2 môn xã hội | Môn tự nhiên | Mỗi môn 17 câu |
| 3 môn tự nhiên | Chọn ngẫu nhiên 1 trong 3 môn | Mỗi môn 17 câu |

**2. Chọn câu đơn được phép bỏ**

1. Hệ thống sinh câu hỏi theo ma trận 17 câu của từng môn đã chọn.
2. Với môn cần giảm còn 16 câu, hệ thống lập danh sách các **câu đơn** có thể bỏ. Toàn bộ câu con thuộc chùm được loại khỏi danh sách này.
3. Với Vật lí, Hóa học và Sinh học, sau khi bỏ câu, đề vẫn phải còn **ít nhất 1 câu điền đáp án**. Nếu câu điền đáp án duy nhất là câu đơn, hệ thống giữ câu đó và chọn trong các câu đơn còn lại.
4. Hệ thống bỏ ngẫu nhiên đúng 1 câu trong danh sách hợp lệ. Số câu thực tế của khối lớp và độ khó tương ứng với câu bị bỏ giảm đi 1. Tổng phần Khoa học là **17 + 17 + 16 = 50 câu**.
5. Hệ thống lưu bộ câu cuối cùng vào lượt thi. Khi học viên tải lại trang, đổi thiết bị hoặc tiếp tục lượt đang làm, hệ thống khôi phục đúng 50 câu đã ghi nhận.

**Ví dụ:** Học viên chọn **Vật lí, Hóa học và Lịch sử**. Lịch sử là môn làm 16 câu. Nếu đề Lịch sử ban đầu có **11 câu đơn và 2 chùm × 3 câu con**, hệ thống bỏ 1 trong 11 câu đơn. Đề thực tế còn **10 câu đơn và đủ 2 chùm**, tổng cộng 16 câu.

## 7. Luồng cấu hình phần Tiếng Anh

Tiếng Anh sử dụng cùng thao tác **chọn nhóm → sửa nguồn và số lượng → kiểm tra → sinh thử** như Văn. Điểm khác biệt là từng dạng bài có vị trí xác định. Hệ thống hiển thị dải số câu tự tính để quản trị viên nhìn được thứ tự ngay khi cấu hình.

| Vị trí trong phần | Dạng bài | Cách lấy |
|---|---|---|
| 1–10 | Hoàn thành câu | 10 câu đơn |
| 11–12 | Chọn từ đồng nghĩa | 2 câu đơn |
| 13–14 | Chọn từ trái nghĩa | 2 câu đơn |
| 15–18 | Viết lại câu | 4 câu đơn |
| 19–22 | Kết hợp hai câu | 4 câu đơn |
| 23–26 | Hoàn thành hội thoại | 4 câu đơn |
| 27–30 | Sắp xếp hội thoại | 4 câu đơn |
| 31–35 | Đọc điền từ hoặc cụm từ | 1 chùm × 5 câu con |
| 36–40 | Đọc hiểu 1 | 1 chùm × 5 câu con |
| 41–45 | Đọc hiểu 2 | 1 chùm × 5 câu con |
| 46–50 | Tư duy logic và giải quyết vấn đề | 5 câu đơn |

Với cấu trúc HSA đầy đủ, hệ thống giữ thứ tự 11 nhóm. Khi sinh đề mới, câu và ngữ liệu trong từng nhóm có thể thay đổi. Riêng nhóm đọc điền phải giữ ánh xạ giữa vị trí trống và câu trả lời tương ứng. Trên đề thử, quản trị viên phải xem được cả ngữ liệu cùng các vị trí trả lời để kiểm tra cách hiển thị thực tế.

## 8. Luồng rút gọn cho bài thi theo môn hoặc ôn theo chủ đề

Với **bài thi theo môn**, sau khi chọn môn ở bước 1, quản trị viên có hai cách bắt đầu bước 2.

| Lựa chọn | Hành vi |
|---|---|
| **Dùng cấu trúc môn trong đề HSA** | Nạp các nhóm và ma trận của môn. Phù hợp khi muốn bán hoặc giao riêng một phần Toán 50 câu, phần Văn 50 câu hoặc một bài luyện theo cấu trúc môn đã có. |
| **Tự thiết lập bài ôn** | Sử dụng số câu và thời lượng đã nhập ở bước 1. Hiển thị trước ba thông tin cần thiết gồm phạm vi kiến thức, dạng câu và số lượng. Các phân bố theo lớp, độ khó và câu chùm chỉ mở khi quản trị viên muốn dùng. |

**Ví dụ thao tác cho bài “HSA – Luyện đồ thị hàm số”, 10 câu, 20 phút**

1. Tại bước 1, quản trị viên chọn **Bài thi theo môn → Toán**, nhập 10 câu và 20 phút.
2. Tại bước 2, chọn **Tự thiết lập bài ôn → Theo chủ đề**, tìm và chọn MAPID *Đồ thị hàm số*.
3. Trong **Dạng trả lời**, nhập 7 câu trắc nghiệm và 3 câu điền đáp án. Nếu bài chỉ dùng trắc nghiệm, có thể cấu hình một dòng trắc nghiệm 10 câu.
4. Nếu muốn phân tầng, mở **Phân bố theo độ khó** và nhập 3 thông hiểu, 5 vận dụng, 2 vận dụng cao. Nếu không đặt hạn ngạch lớp, hệ thống lấy câu phù hợp trong nguồn đã chọn.
5. Chọn **Kiểm tra cấu hình**. Nếu nguồn chỉ có một câu vận dụng cao, hệ thống chỉ rõ *“Chủ đề Đồ thị hàm số yêu cầu 2 câu vận dụng cao, hiện có 1 câu phù hợp.”* Quản trị viên có thể sửa phân bố hoặc bổ sung câu vào ngân hàng.
6. Khi kiểm tra đạt, chọn **Sinh đề thử** rồi **Tiếp tục** sang bước kế tiếp.

Điểm quyết định đối với luồng này là **số câu mục tiêu đến từ bài đang tạo**. Bài ôn 10 câu có thể khai báo trong vài thao tác mà không phải mở ma trận Toán 50 câu rồi xóa từng chỉ tiêu không dùng.

## 9. Kiểm tra, sinh thử và điều kiện hoàn thành bước

| Thao tác | Hệ thống kiểm tra | Kết quả hiển thị |
|---|---|---|
| **Lưu nháp** | Kiểm tra định dạng dữ liệu đã nhập. Cho phép còn thiếu môn, thiếu nhóm hoặc chưa đủ nguồn. | Lưu được tiến độ. Mục chưa hoàn tất hiển thị **Chưa hoàn tất** khi mở lại. |
| **Kiểm tra cấu hình** | Kiểm tra tổng số câu, quan hệ câu đơn/chùm, trường bắt buộc, nguồn câu và khả năng đồng thời đáp ứng lớp, độ khó, dạng bài, MAPID. | **Hợp lệ** hoặc danh sách lỗi gắn với đúng phần/môn/nhóm. Kiểm tra phải phát hiện trường hợp từng chỉ tiêu riêng có đủ câu nhưng không thể ghép thành một đề hợp lệ. |
| **Sinh đề thử** | Sinh một đề bằng cùng quy tắc áp dụng cho học viên. Với Khoa học, quản trị viên chọn tổ hợp muốn xem thử. | Hiển thị đề thực tế, thứ tự câu, ngữ liệu và bảng **Yêu cầu / Thực tế**. Sinh thử không sử dụng lượt thi. |
| **Tiếp tục** | Kiểm tra toàn bộ phạm vi đã khai báo ở bước 1. Với FULL có cả hai nhánh, kiểm tra phần Toán, Văn, Tiếng Anh và 10 tổ hợp chọn 3 trong 5 môn Khoa học. | Nếu đạt, lưu cấu hình và chuyển bước. Nếu chưa đạt, giữ tại bước 2 và dẫn tới mục đầu tiên cần sửa. |

**Khi thay đổi dữ liệu đã kiểm tra**, chỉ các mục bị ảnh hưởng chuyển về **Cần kiểm tra lại**. Ví dụ, sửa ma trận Vật lí làm mất hiệu lực kết quả kiểm tra Vật lí và các tổ hợp có Vật lí. Nếu quay lại bước 1 đổi bài Toán từ 50 xuống 40 câu, hệ thống giữ các dòng cấu hình để quản trị viên điều chỉnh và hiển thị ngay chênh lệch 10 câu. Việc mở một bản nháp hoặc sinh đề thử không tự thay đổi cấu hình đã lưu.
