# Đặc tả bổ sung: cờ "Giữ thứ tự phương án" (2026-11-17)

M.Trường chốt ngày 2026-11-17, 19:10. Quyết định này đảo một phần BA12b ("luôn đảo, không có cờ"). Ca gốc: câu có phương án
"Cả A và B đều đúng", đáp án B. Khi đảo phương án, chữ A, B trong phương án không còn chỉ đúng phương án nữa.

## 1. Trường dữ liệu

- `body.shuffleOptions` (boolean, mặc định `true`) có ở câu Một đáp án (`SINGLE_CHOICE`) và Nhiều đáp án (`MULTIPLE_CHOICE`),
  kể cả khi hai dạng này là câu con của câu chùm. Đây là trường đã ghi trong đặc tả BE mục 4.3 (bảng dạng câu). Code hiện
  đang bỏ qua trường này (`@JsonIgnoreProperties`), lần này đưa trở lại.
- `false` nghĩa là giữ thứ tự phương án như khi soạn. `true` hoặc thiếu trường nghĩa là đảo như cũ.
- Phần thân câu lưu ở jsonb nên không cần migration. Câu đã có, chưa có trường này, đọc ra `true`.
- Đọc lại, phiên bản mới, sao chép, xuất và nhập đều giữ nguyên giá trị. API đọc trả trường này.
- Dạng khác không có trường này. Đúng/Sai giữ thứ tự ý như cũ; cột phải Ghép đôi vẫn đảo.

## 2. Trình soạn

- Ở khối phương án của câu Một đáp án và Nhiều đáp án có ô chọn "Giữ thứ tự phương án" (en "Keep option order"), mặc định
  không chọn. Chọn nghĩa là `shuffleOptions = false`.
- Gợi ý dưới ô chọn: "Bật khi phương án nhắc tới phương án khác, ví dụ “Cả A và B”." (en: "Turn on when an option refers to
  another option, such as “Both A and B”.")
- Câu con Một đáp án và Nhiều đáp án trong câu chùm có cùng ô chọn.

## 3. Cảnh báo khi chuyển Sẵn sàng

- Mã `OPTION_REFERS_TO_OTHERS`, mức cảnh báo, không chặn Sẵn sàng. Đường dẫn trỏ tới phương án gây cảnh báo
  (`body.options[i].content`, hoặc `body.children[j].body.options[i].content` với câu con).
- Điều kiện: `shuffleOptions` khác `false` và nội dung chữ của phương án (bỏ thẻ HTML, không phân biệt hoa thường, bỏ dấu
  khi so) khớp một trong các mẫu:
  - nhắc tới chữ cái phương án: "A và B", "A, B và C", "cả A", "phương án A", "đáp án A", "câu A", "A hoặc B", và tiếng Anh
    "A and B", "both A", "option A";
  - "tất cả" ("tất cả các phương án/đáp án trên", "tất cả đều đúng/sai"), "cả hai", "cả ba", "không có phương án/đáp án
    nào", "đáp án khác", "phương án khác", "các phương án trên", "các đáp án trên";
  - tiếng Anh: "all of the above", "none of the above", "both of the above", "neither".
- Chữ cái chỉ tính khi đứng riêng (A–J, có ranh giới từ), để "Asia" hay "vitamin A" một mình không khớp. Một chữ cái đứng
  riêng mà không đi với từ nối hay từ "phương án/đáp án/câu/option" thì không tính.
- Thông báo vi: "Phương án nhắc tới phương án khác. Nên bật “Giữ thứ tự phương án” để thứ tự không bị đảo." en: "This option
  refers to other options. Consider turning on “Keep option order” so the options are not shuffled."
- Trình soạn hiện cảnh báo ngay dưới phương án đó (cùng đường hiện cảnh báo Sẵn sàng hiện có), có nút "Bật giữ thứ tự"
  (en "Keep order") để bật cờ.

## 4. Nhập từ tệp

- Mẫu nhập có thêm cột tùy chọn "Giữ thứ tự phương án" (khóa `keepOptionOrder`). Giá trị nhận: `Có`/`Không`, `x`/trống,
  `Yes`/`No`, `TRUE`/`FALSE`, `1`/`0`. Trống nghĩa là không giữ. Giá trị khác là lỗi dòng có tên cột.
- Cột chỉ có nghĩa với Một đáp án và Nhiều đáp án; điền ở dạng khác thì bỏ qua và ghi chú ở bản xem trước nhập.

## 5. Đảo khi sinh đề

- `PaperOrdering` (đợt 2B, K12) và mọi chỗ đảo phương án khác (xem trước đề, làm thử, lượt thi theo LT F22) không đảo
  phương án của câu có `shuffleOptions = false`. Hạt giống vẫn quyết định thứ tự của các câu khác như cũ.
- Đợt 2B và đợt lượt thi đọc mục này khi làm `PaperOrdering` và AT-1. Đến lúc đó, nếu phần xem trước đề hiện tại có đảo
  phương án thì cũng theo cờ.

## 6. Kiểm thử

- Backend: lưu và đọc lại giữ `shuffleOptions = false`; thiếu trường đọc ra `true`; phiên bản mới và sao chép giữ giá trị;
  cảnh báo `OPTION_REFERS_TO_OTHERS` với các mẫu ở mục 3 (ít nhất 12 ca dương, 8 ca âm như "Asia", "vitamin A", "Phương
  án đúng là số dương"); có cờ `false` thì không cảnh báo; câu con trong chùm; nhập tệp với cột mới (giá trị hợp lệ, không
  hợp lệ, ở dạng không dùng).
- Web: ô chọn ghi đúng trường, mở lại giữ trạng thái; cảnh báo hiện dưới phương án, nút bật cờ làm cảnh báo biến mất sau
  lần kiểm tra lại; vi/en; sáng/tối.
