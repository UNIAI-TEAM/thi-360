# Mua bài thi, thanh toán và Cấp quyền sử dụng

Bản lưu UniNote của BA (Hưng Nguyễn Duy), cập nhật 2026-11-16 15:14, lấy về ngày 2026-11-16. Nguồn: uniworkos, thư mục "Tài liệu Thi 360", đính kèm task UNI-1636 (PBI-07) và UNI-1637 (PBI-08). Chỉ đọc; bản gốc ở uniworkos.

## 1. Mục tiêu và phạm vi

Người mua chọn bài thi, xác nhận tài khoản nhận bài thi, sau đó có thể chọn sử dụng Voucher (mã giảm giá) để nhận quyền miễn phí hoặc chọn Thanh toán qua VNPAY.

- Nếu dùng voucher hợp lệ (giảm 100% hoặc giá trị tương ứng), hệ thống tự động cấp quyền mà không cần chuyển sang cổng thanh toán.

- Nếu thanh toán qua VNPAY, ngay khi giao dịch thành công, hệ thống tự động cấp quyền cho tài khoản đã ghi trên đơn. Người dùng đăng nhập bằng tài khoản đó có thể vào mục quản lý bài thi để sử dụng số lượt đã mua.

| Thành phần | Trách nhiệm |
|---|---|
| Hệ thống thi 360 (Client Portal) | Hiển thị thông tin mua, hỗ trợ nhập voucher, xác định tài khoản nhận, tạo đơn, tích hợp cổng VNPAY, tiếp nhận kết quả/mã giảm giá, cấp quyền và hiển thị bài thi đã mua. |
| VNPAY | Cung cấp giao diện thanh toán trực tuyến, xử lý giao dịch và trả kết quả để hệ thống ghi nhận, đối chiếu (chỉ áp dụng khi không dùng voucher miễn phí). |
| Hệ thống thi 360 (Platform Admin) | Cung cấp menu Quản lý đơn hàng bài thi để tra cứu đơn, kiểm tra hình thức thanh toán (VNPAY/Voucher), theo dõi trạng thái cấp quyền và xử lý các giao dịch phát sinh lỗi. |

## 2. Quy tắc nghiệp vụ

| Mã | Quy tắc |
|---|---|
| BR-01 | Khi tạo đơn, máy chủ lấy mã tài khoản từ phiên đăng nhập và lưu vào đơn hàng. Giao diện chỉ hiển thị thông tin tài khoản để người dùng kiểm tra. |
| BR-02 | Tài khoản trên đơn được cố định tại thời điểm tạo. Khi giao dịch thành công (qua VNPAY hoặc áp dụng Voucher thành công), hệ thống cấp quyền theo mã tài khoản đã lưu, kể cả khi người dùng đã đóng trình duyệt hoặc đổi phiên đăng nhập. |
| BR-03 | Mỗi đơn mua một sản phẩm bài thi với một giá bán bằng VND (đã gồm thuế, phí) hoặc được miễn phí hoàn toàn nếu áp dụng mã voucher hợp lệ. |
| BR-04 | Khi người dùng nhấn thanh toán qua VNPAY hoặc áp dụng voucher, hệ thống kiểm tra lại tài khoản sở hữu đơn, tính hợp lệ của voucher, trạng thái mở bán và giá hiện hành của bài thi. |
| BR-05 | Sản phẩm ngừng bán thì dừng giao dịch. Sản phẩm thay đổi giá thì hiển thị thông báo giá mới, chỉ tạo đơn mới khi người dùng xác nhận. |
| BR-06 | Ngay khi máy chủ xác nhận thanh toán VNPAY thành công hoặc áp dụng Voucher hợp lệ, hệ thống tự động cấp quyền cho tài khoản trên đơn. Mỗi đơn chỉ phát sinh một quyền duy nhất. |
| BR-07 | Các phiên đăng nhập vào cùng một tài khoản sử dụng chung quyền đã mua, số lượt còn lại và thời hạn. Việc đăng nhập trên thiết bị khác không làm phát sinh lượt mới. |
| BR-08 | Số lượt và điều kiện sử dụng được cấp theo đơn đã mua. Mỗi lượt thi mới dùng phiên bản đề mới nhất tại thời điểm bắt đầu. |
| BR-09 | Chỉ cho phép hoàn tiền (đối với giao dịch VNPAY) khi đã xác minh lỗi hệ thống. Trường hợp người dùng nhập nhầm tài khoản nhưng vẫn xác nhận mua sẽ không đủ điều kiện hoàn tiền. |
| BR-10 | Quy tắc về Voucher: Mỗi mã voucher có thể giới hạn số lần sử dụng, thời hạn hiệu lực và giá trị giảm giá. Nếu voucher giảm 100% (hoặc số tiền giảm bằng giá trị đơn hàng), đơn hàng chuyển thẳng sang trạng thái "Hoàn thành - Đã cấp quyền" mà không cần qua VNPAY. Nếu voucher giảm một phần, số tiền còn lại sẽ được chuyển sang cổng VNPAY để thanh toán tiếp. |

## 3. FR-01 — Chọn bài thi, áp dụng Voucher và Xác nhận thông tin mua

- Điểm truy cập: Trang chi tiết bài thi → Mua bài thi.

- Nếu chưa đăng nhập, người dùng hoàn tất đăng nhập rồi quay lại sản phẩm đã chọn. Hệ thống mở màn hình Xác nhận mua bài thi với các thông tin sau:

| Khu vực | Nội dung và cách hiển thị |
|---|---|
| Bài thi đã chọn | Ảnh đại diện, tên đầy đủ, loại bài thi, cấp độ và kỹ năng theo cấu hình sản phẩm. |
| Cấu trúc bài thi | Số câu hỏi hoặc mô tả cấu trúc sinh đề, thời lượng của mỗi lượt thi. |
| Quyền sử dụng | Số lượt được cấp, thời hạn sử dụng và các mốc thời hạn liên quan. |
| Tài khoản mua bài thi | Hiển thị tên đăng nhập và họ tên từ phiên đăng nhập hiện tại (chế độ chỉ xem). |
| Mã giảm giá (Voucher) | Ô nhập mã voucher kèm nút Áp dụng. Nếu mã hợp lệ, hệ thống hiển thị thông báo thành công, trừ trực tiếp số tiền giảm vào tổng thanh toán (hoặc hiển thị “Miễn phí 100%”). |
| Tổng thanh toán | Hiển thị số tiền sau khi đã áp dụng voucher (Ví dụ: 0 đ (Đã áp dụng voucher) hoặc 150.000 đ). |
| Xác nhận trước khi trả tiền | Ngay trên nút hành động, hiển thị: “Sau khi hoàn tất, bài thi sẽ được cấp cho tài khoản [tên đăng nhập].” |
| Thao tác | Nút Sử dụng Voucher & Nhận bài thi (nếu miễn phí) hoặc Thanh toán qua VNPAY (nếu còn tiền phải trả). |

- Trường hợp đổi tài khoản: Nếu màn hình đang hiển thị tài khoản A nhưng phiên đăng nhập đã chuyển sang B, hệ thống phải dừng thao tác, tải lại màn hình với thông tin của B và yêu cầu xác nhận lại.

## 4. FR-02 — Tạo đơn, xử lý Voucher / chuyển sang VNPAY

Khi người dùng xác nhận:

- Máy chủ lấy tài khoản từ phiên đăng nhập, kiểm tra sản phẩm và tạo đơn hàng.

- Kiểm tra Voucher:Nếu người dùng nhập mã voucher hợp lệ (giá trị đơn hàng sau giảm về 0 đ): Hệ thống chuyển đơn sang trạng thái Đã thanh toán (qua Voucher) và kích hoạt ngay quy trình cấp quyền (FR-03).Nếu không dùng voucher hoặc voucher chỉ giảm một phần: Hệ thống tạo đơn ở trạng thái Chờ thanh toán, gắn kết nối với cổng VNPAY và chuyển hướng người dùng sang VNPAY.

- Xử lý khi tiếp tục một đơn hàng đang chờ:Đúng tài khoản, sản phẩm còn bán, giá khớp: Cho phép tiếp tục thanh toán qua VNPAY hoặc nhập lại voucher.Sai tài khoản (Tài khoản B mở liên kết của A): Yêu cầu sử dụng đúng tài khoản để tiếp tục. B không được thanh toán tiếp hoặc nhận quyền từ đơn của A.Sản phẩm ngừng bán / Giá thay đổi / Đơn hết hạn: Hiển thị thông báo tương ứng, yêu cầu tạo đơn mới hoặc hủy đơn cũ theo quy định.

## 5. FR-03 — Cấp quyền sử dụng ngay sau thanh toán hoặc dùng Voucher

- Hệ thống đọc mã tài khoản đã lưu trên đơn, mã sản phẩm và điều kiện sử dụng đã mua.

- Kiểm tra xem đơn đã phát sinh quyền hay chưa. Nếu chưa, tạo quyền mới cho tài khoản trên đơn (ghi nhận số lượt được cấp, số lượt đã dùng bằng 0, thời điểm cấp và các mốc thời hạn).

- Khi cấp quyền thành công, bài thi sẽ xuất hiện trong danh mục Bài thi của tôi ngay sau khi đăng nhập, đồng thời cập nhật trạng thái đơn thành Đã hoàn tất — Đã cấp quyền.

## 6. FR-06 — Tra cứu thanh toán và hoàn tiền trên Platform Admin

Điểm truy cập: Platform Admin → Quản lý đơn hàng bài thi.

Platform Admin sử dụng màn hình này để tra cứu khoản thanh toán của từng tài khoản và thực hiện hoàn tiền cho giao dịch đủ điều kiện theo FR-07.

### 6.1. Danh sách đơn hàng

Mỗi dòng tương ứng với một đơn mua bài thi, sắp xếp theo thời điểm tạo đơn mới nhất.

| Thông tin | Yêu cầu hiển thị |
|---|---|
| Mã đơn hàng | Cho phép mở chi tiết để xem các giao dịch VNPAY thuộc đơn. |
| Tài khoản mua | Tên đăng nhập của tài khoản đã tạo đơn. |
| Bài thi | Tên sản phẩm tại thời điểm mua. |
| Giá trị đơn | Số tiền phải thanh toán, hiển thị bằng VND. |
| Thời gian | Ngày giờ tạo đơn và ngày giờ thanh toán thành công, nếu có. |
| Trạng thái thanh toán | Thành công, Thất bại, Đã hoàn tiền. |
| Thao tác | Xem giao dịch |

Cho phép tìm theo mã đơn, tên đăng nhập hoặc mã giao dịch VNPAY. Cho phép lọc theo bài thi, khoảng thời gian, trạng thái thanh toán và trạng thái hoàn tiền.

### 6.2. Xem giao dịch, tiếp tục thanh toán và hoàn tiền

Khi chọn Xem giao dịch, hệ thống hiển thị từng lần thanh toán thuộc đơn, gồm mã giao dịch VNPAY, số tiền thực thu, thời điểm thanh toán, trạng thái và lịch sử hoàn tiền. Nếu một đơn bị thu hai lần, admin phải chọn được đúng giao dịch thu dư để hoàn.

- Admin chọn giao dịch đã xác nhận thu tiền và nhấn Hoàn tiền.

- Exam hiển thị mã đơn, tài khoản mua, mã giao dịch VNPAY, số tiền đã thu, số tiền đã hoàn và số tiền đề nghị hoàn.

- Admin ghi rõ lỗi hệ thống đã xác minh và căn cứ xử lý, sau đó xác nhận giao dịch và số tiền.

- Exam kiểm tra quyền thao tác, điều kiện hoàn và số tiền còn được hoàn trước khi gửi yêu cầu. Tạo một giao dịch với mã giao dịch mới và khoản hoàn gắn với giao dịch thanh toán gốc.

- Khi yêu cầu đã được tiếp nhận, Exam hiển thị Đang hoàn. Chỉ chuyển sang Đã hoàn khi có kết quả thành công. Nếu thất bại, hiển thị lý do. Nếu chưa rõ kết quả, tra cứu yêu cầu cũ trước khi cho phép gửi lại.

Lưu người thực hiện, lý do, mã giao dịch gốc, số tiền, thời điểm yêu cầu và kết quả. Nút Hoàn tiền chỉ dành cho admin có quyền hoàn tiền. Nhấn xác nhận nhiều lần không được tạo nhiều khoản hoàn cho cùng yêu cầu.

Tiếp tục thanh toán để cấp lại quyền thi

Nếu Exam đã xác nhận thanh toán thành công nhưng tài khoản trên đơn chưa nhận được bài thi do lỗi hệ thống, màn hình chi tiết hiển thị lý do và nút Tiếp tục thanh toán cho admin có quyền xử lý.

- Admin chọn Tiếp tục thanh toán. Exam hiển thị xác nhận: “Đơn [mã đơn] đã thanh toán thành công. Tiếp tục cấp quyền bài thi [tên bài thi] cho tài khoản [tên đăng nhập]? Người dùng sẽ không bị thu thêm tiền.”

- Khi admin xác nhận, Exam kiểm tra lại khoản thu, tình trạng hoàn tiền và quyền đã phát sinh từ đơn. Hệ thống sử dụng đơn và giao dịch gốc, không tạo yêu cầu thanh toán mới qua VNPAY.

- Nếu chưa có quyền, Exam cấp cho đúng tài khoản trên đơn, với số lượt và thời hạn theo điều kiện đã mua. Nếu quyền đã được tạo nhưng đơn chưa ghi nhận hoàn tất, Exam đồng bộ lại đơn với quyền hiện có và giữ nguyên số lượt đã sử dụng.

- Khi xử lý thành công, bài thi xuất hiện trong Danh sách bài thi. Admin thấy thông báo “Đã cấp quyền bài thi cho tài khoản [tên đăng nhập]”. Nếu xử lý vẫn lỗi, Exam hiển thị nguyên nhân và lưu lần xử lý để admin tiếp tục kiểm tra.

Điều kiện thao tác. Nút chỉ khả dụng khi khoản thu đã được xác nhận và việc cấp quyền chưa hoàn tất do lỗi hệ thống. Nếu khoản thu dùng để cấp quyền đã được hoàn hoặc đang chờ kết quả hoàn, Exam chặn thao tác cấp lại. Nếu hai admin cùng xử lý, hoặc hệ thống tự cấp quyền đúng lúc admin xác nhận, đơn vẫn chỉ phát sinh một quyền.

## 7. FR-05 — Xử lý các tình huống phát sinh đặc biệt

| Tình huống | Quy tắc xử lý |
|---|---|
| Hệ thống thu trùng tiền (VNPAY) | Xác minh các khoản thu thực tế. Nếu xác nhận lỗi hệ thống, xử lý hoàn khoản dư và giữ quyền của khoản thanh toán hợp lệ. |
| Áp dụng Voucher lỗi / Hết lượt | Hệ thống từ chối áp dụng mã, thông báo rõ lý do (mã hết hạn, hết lượt sử dụng) và yêu cầu người dùng thanh toán qua VNPAY hoặc dùng mã khác. |
| Đã thu tiền (hoặc dùng voucher) nhưng lỗi kỹ thuật khi cấp quyền | Hệ thống ưu tiên tự động cấp lại hoặc cho phép Admin kích hoạt tính năng Tiếp tục cấp quyền thủ công trên trang quản trị. |
