# Mẫu clipboard Word (UNI-2645, ED-5)

**Các tệp `synthetic-*.html` là mẫu tổng hợp, không phải clipboard thật.** Chưa có mẫu Word 365 chụp từ máy người dùng
(đã hỏi Advisor). Chúng được dựng theo cấu trúc clipboard HTML của Word 365 trên Windows (Generator `Microsoft Word 15`):

- vỏ `<html xmlns:w="urn:schemas-microsoft-com:office:word" xmlns:m="http://schemas.microsoft.com/office/2004/12/omml">`,
  `<meta name=Generator content="Microsoft Word 15">`, `<!--StartFragment-->` … `<!--EndFragment-->`;
- công thức Word là OMML trong chú thích `<!--[if gte msEquation 12]><m:oMath>…</m:oMath><![endif]-->`, theo sau là bản dự
  phòng `<![if !msEquation]>…<![endif]>`;
- công thức dạng ảnh (MathType) là `<!--[if gte vml 1]>…<![endif]--><![if !vml]><img … v:shapes="_x0000_iNNNN"><![endif]>` kèm
  đối tượng `<o:OLEObject ProgID="Equation.DSMT4" ShapeID="_x0000_iNNNN">`; ảnh trỏ vào tệp cục bộ `file:///…` nên trình duyệt không đọc được;
- danh sách là đoạn `MsoListParagraph` với `mso-list:` và nhãn trong `<![if !supportLists]>…<![endif]>`.

| Tệp | Nội dung |
|---|---|
| `synthetic-word365-choice.html` | Câu Trắc nghiệm 1 đáp án theo bản vẽ `SoanThaoDanTuWord`: đề bài có 2 công thức, phương án A–D (A và D là hệ "hoặc" bị đổi thành phân số, B và C đúng), "Lời giải" có 1 công thức đúng, 1 công thức tên hàm bị tách chữ (`s`,`i`,`n`) và 3 ảnh công thức MathType |
| `synthetic-word365-extras.html` | Ký hiệu độ (`30°`), công thức hóa học bọc ngoặc `(H₂SO₄)`, OMML ngoài tập con (`m:sPre`), hệ phương trình `m:eqArr`, chữ đậm/nghiêng/chỉ số/gạch chân, danh sách chấm, bảng, ảnh thường |

Các mẫu này kiểm đúng hành vi của bộ đọc (`parse.ts`), bộ đổi OMML → LaTeX (`omml.ts`), bốn bộ tự sửa (`fixers.ts`) và bộ
tách nhãn (`split.ts`), nhưng **chưa chứng minh** được hình dạng clipboard của mọi bản Office. Khi có mẫu thật: thêm nó ở
đây (không đặt tên `synthetic-`), chạy lại `pnpm --filter @thi360/web-admin exec vitest run src/components/rich-text/insert/word`
và sửa bộ đọc nếu khác.

## Cách chụp một mẫu thật

1. Trong Word 365 (Windows) soạn một câu có công thức (Insert ▸ Equation), một công thức MathType nếu có, danh sách A. B. C. D.
2. Chọn nội dung, Ctrl+C.
3. Trong trình duyệt mở một trang bất kỳ, DevTools ▸ Console:

   ```js
   document.addEventListener("paste", (e) => { console.log(e.clipboardData.getData("text/html")); e.preventDefault(); }, { once: true });
   ```

   rồi Ctrl+V trên trang. Chép nguyên chuỗi (kể cả phần `<html …>` và chú thích `<!--[if gte msEquation 12]>`) vào một tệp `.html` mới.
4. Không sửa nội dung; chỉ thay họ tên, đường dẫn máy (`C:/Users/<tên>/…`) bằng giá trị giả nếu có.

Bản Word khác nhau (Word 2016, Word Online, Word trên Mac) cho HTML khác nhau: ghi bản Word vào tên tệp
(`word365-…`, `word2016-…`).
