# Đặc tả trình soạn nội dung đầy đủ (UNI-2638)

> Ngày 2026-11-17. Issue UNI-2638. Bổ sung cho đặc tả `docs/specs/2026-11-16-be-pbi-03-04.md` (UNI-2605) mục 4.2 và task FE-4 (UNI-2617). Bản vẽ: trang 07 của canvas, đã xuất vào `docs/design/dot-2/SoanThao*.dc.html`.

## 1. Bối cảnh

FE-4 làm trình soạn TipTap tối thiểu: định dạng chữ, danh sách, bảng thô, ảnh, và hộp "Chèn công thức" chỉ có ô LaTeX. Bản vẽ trang 07 có 10 màn trình soạn cho người soạn đề nhiều môn. M.Trường yêu cầu làm đủ theo bản vẽ (2026-11-17, "làm luôn đi").

Đối tượng dùng: người soạn câu hỏi ở web admin. Mọi màn soạn câu hỏi, ngữ liệu câu chùm và hướng dẫn làm bài dùng chung một trình soạn (`RichTextEditor`), nên mọi tính năng dưới đây có ở mọi ô nội dung, trừ chỗ ghi khác.

## 2. Phạm vi

| Mã | Màn bản vẽ | Nội dung |
|---|---|---|
| E1 | `SoanThaoTongQuan` | Thanh công cụ có nhóm Chèn; menu `/`; gõ `$…$`; khung "Công thức đang chọn" |
| E2 | `SoanThaoCongThucToan` | Hộp công thức toán: bảng nút theo nhóm, ô soạn trực quan đồng bộ với LaTeX, báo hợp lệ, xem trước trong câu, dùng gần đây |
| E3 | `SoanThaoHoaHoc` | Hộp công thức và phương trình hóa học |
| E4 | `SoanThaoKyHieu` | Hộp ký hiệu và đơn vị |
| E5 | `SoanThaoBang` | Hộp bảng số liệu |
| E6 | `SoanThaoHinh` | Hộp hình |
| E7 | `SoanThaoDoanTrich` | Hộp đoạn trích |
| E8 | `SoanThaoTiengAnh` | Gạch chân đánh nhãn; chỗ trống đánh số trong ngữ liệu câu chùm |
| E9 | `SoanThaoSinhHoc` | Hộp sơ đồ lai |
| E10 | `SoanThaoDanTuWord` | Dán từ Word: đổi công thức, đánh dấu chỗ cần xem lại, tách đề, phương án, lời giải |

Ngoài phạm vi:
- Web học viên dựng nội dung khi thi: đợt 3 (lượt thi). Đợt này chuẩn bị `RichTextView` dựng đủ các khối mới để đợt 3 dùng lại.
- Nhập câu hỏi từ tệp Excel vẫn chỉ nhận chữ (BE-3).
- Hình SVG: bản vẽ ghi "Nhận PNG, JPG, SVG", nhưng D17 của đặc tả backend không nhận SVG (rủi ro script). Giữ D17; câu dưới hộp Hình ghi "Nhận PNG, JPG, WebP".
- "Giữ làm hình" cho ảnh công thức khi dán từ Word (mục 4.10): để lại.

## 3. Định dạng lưu (HTML)

Nội dung vẫn là HTML đã lọc, lọc ở máy chủ khi lưu (đặc tả backend 4.2). Đợt này mở rộng danh sách cho phép. Không có `style`, `script`, thuộc tính sự kiện, liên kết ngoài. Mọi giá trị `class` và `data-*` dưới đây là danh sách đóng; giá trị khác bị bỏ và trả 422 `QUESTION_CONTENT_UNSAFE` như hiện tại.

**Dạng chuẩn là bảng mẫu `docs/specs/fixtures/rich-text-samples.json`**, không phải ví dụ trong chữ dưới đây. Bảng mẫu ghi đúng thứ tự thuộc tính và cách viết mà trình soạn xuất ra; mẫu hợp lệ qua bộ lọc phải giữ nguyên từng byte. Bảng mẫu có trước khi các lane bắt đầu (plan, bước 0).

### 3.1 Công thức (E2, E3)

- Giữ nguyên: `<span class="math-inline">\( … \)</span>` và `<span class="math-block">\[ … \]</span>`.
- Hóa học dùng cú pháp mhchem trong cùng hai span: `\(\ce{CaCO3(s) <=> CaO(s) + CO2(g)}\)`. KaTeX dựng bằng phần mở rộng `katex/contrib/mhchem`, có ở cả trình soạn và `RichTextView`. Trong HTML đã lưu, dấu `<`, `>`, `&` của LaTeX viết thành `&lt;`, `&gt;`, `&amp;` (dạng trình soạn và jsoup xuất ra).
- Công thức khối là `span.math-block` ở cấp đầu của nội dung, không nằm trong `p` (như node hiện có).
- Máy chủ không đổi: chỉ kiểm cặp dấu mở và đóng (NO_GAPS).

### 3.2 Khối mới và mô hình soạn

Các khối `figure` và `blockquote` là **node nguyên khối** (atom) trong TipTap: không sửa tại chỗ, nhấp đúp hoặc chọn rồi bấm nút trên thanh công cụ để mở lại hộp tương ứng. Bảng thường đã có từ FE-4 (`table` trần, ô chứa `p`) giữ nguyên để nội dung cũ đọc được; bảng số liệu là node riêng.

| Khối | HTML (rút gọn; dạng đủ ở bảng mẫu) |
|---|---|
| Bảng số liệu (E5) | `<figure class="data-table"><figcaption>Tên bảng</figcaption><table><thead><tr><th scope="col">…</th></tr></thead><tbody><tr><th scope="row">…</th><td class="num">1096,1</td></tr></tbody></table><p class="figure-note">Đơn vị: …</p><p class="figure-source">Nguồn: …</p></figure>` |
| Hình (E6) | `<figure class="image" data-width="medium" data-zoom="true"><img src="…" alt="…" data-asset-id="…"><figcaption>Chú thích</figcaption><p class="figure-source">Nguồn: …</p></figure>` |
| Đoạn trích (E7) | `<blockquote class="quote" data-kind="poem" data-line-numbers="5"><p>dòng 1<br>dòng 2</p><p>khổ 2…</p><footer>(Nguyễn Du, <cite>Truyện Kiều</cite>)</footer><p class="figure-source">…</p></blockquote>` |
| Gạch chân đánh nhãn (E8) | `<u data-label="A">has been</u>` |
| Chỗ trống đánh số (E8) | `<span class="blank" data-blank="1"></span>` |
| Sơ đồ lai (E9) | `<figure class="cross"><table><tbody><tr><th scope="row">P</th><td>…</td></tr>…</tbody></table></figure>`; mỗi hàng một thế hệ |

Ô của bảng số liệu và sơ đồ lai chứa chữ trực tiếp (không bọc `p`), được có `strong`, `em`, `sub`, `sup` và `span.math-inline`. Không ô nào có `colspan="1"` hay `rowspan="1"` (chỉ ghi khi lớn hơn 1).

Đoạn trích thơ: mỗi khổ một `p`, các dòng trong khổ cách nhau bằng `br`. Số dòng đếm liên tục qua các khổ. Văn xuôi và tư liệu: mỗi đoạn một `p`, không `br`, không đánh số.

Gạch chân đánh nhãn là một mark riêng (`underlineLabel`), ưu tiên phân tích cao hơn mark gạch chân thường và loại trừ nó: `<u data-label>` thành nhãn, `<u>` không thuộc tính vẫn là gạch chân thường.

### 3.3 Luật cấu trúc (máy chủ kiểm, trình soạn tuân theo)

Bộ lọc hiện tại chỉ là danh sách phẳng (`attributeAllowed(tag, name, value)`). Đợt này thêm **một lượt kiểm theo phần tử** sau khi lọc; vi phạm thì bỏ phần tử vi phạm (cả khối nếu là `figure`/`blockquote`) và trả 422 kèm tên thẻ. Mỗi vi phạm báo một lần: sai hoặc thiếu giá trị thuộc tính thì báo thẻ kèm thuộc tính; sai cấu trúc thì chỉ báo thẻ; phần tử đã bị báo ở lượt này thì không báo tiếp các con của nó. Vi phạm của danh sách phẳng (thẻ lạ, `style`, `on*`) vẫn báo như cũ:

| Luật | Ghi chú |
|---|---|
| `class` chỉ có đúng một giá trị trong danh sách đóng | `class="image data-table"` là vi phạm |
| `figure` chỉ có `class` ∈ {`data-table`, `image`, `cross`}; nằm ở cấp đầu của nội dung (cha là gốc), không trong `td`, `li`, `figure`, `blockquote` | |
| `figure.image`: đúng một `img` là con trực tiếp, sau đó tùy chọn `figcaption`, `p.figure-source`; `data-width` ∈ {`small`, `medium`, `full`}, `data-zoom` ∈ {`true`, `false`} | `data-width`, `data-zoom` chỉ ở `figure.image`. Ô `allowImages=false` thì `figure.image` là vi phạm |
| `figure.data-table`: tùy chọn `figcaption` đầu tiên, đúng một `table` con trực tiếp, tùy chọn `p.figure-note`, `p.figure-source` | |
| `figure.cross`: đúng một `table` con trực tiếp | |
| `figcaption` chỉ dưới `figure` | |
| `blockquote` chỉ có `class="quote"`, `data-kind` ∈ {`poem`, `prose`, `source`}, `data-line-numbers` ∈ {`0`, `5`} (khác `0` chỉ khi `poem`); nằm ở cấp đầu như `figure`; con là một hay nhiều `p`, rồi tùy chọn `footer`, `p.figure-source` | |
| `footer` chỉ dưới `blockquote.quote`; `cite` chỉ dưới `footer` | |
| `p.figure-note`, `p.figure-source` chỉ là con trực tiếp của `figure` hoặc `blockquote` | |
| `th`: `scope` nếu có thì ∈ {`col`, `row`}; `td`: `class` nếu có thì là `num` | Bảng thường cũ có `th` không `scope` vẫn hợp lệ |
| `span.blank` không có con (kể cả chữ), `data-blank` bắt buộc, số nguyên 1–20 (giới hạn 20 câu con của `QuestionBodyValidator`) | |
| `u` chỉ có `data-label` là một chữ `A`–`H` | |

Luật theo ngữ cảnh trường (kiểm tra sẵn sàng, mục 6, vì bộ lọc không biết trường):
- `span.blank` chỉ ở ngữ liệu câu chùm.
- `u[data-label]` chỉ ở đề bài câu Trắc nghiệm 1 đáp án (kể cả câu con dạng này).

### 3.4 `blankNumberStart`

- Trường mới của phần thân câu chùm (`QUESTION_SET`): số nguyên 1–200, mặc định 1. API nhận và trả; câu cũ không có thì đọc ra 1. Ngoài khoảng: 422 lỗi trường `body.blankNumberStart`, dùng lại mã lỗi trường số sẵn có của body (không thêm mã mới).
- Chỉ dùng cho **soạn và xem trước trong ngân hàng câu hỏi**. Khi câu chùm nằm trong một đề, số chỗ trống lấy theo vị trí câu con trong đề (đợt 3, lượt thi), không theo trường này.
- Số hiển thị của chỗ trống thứ n = `blankNumberStart` + n − 1.
- Mọi chỗ dựng lại `QuestionSetBody` phải mang trường này: `QuestionService` (tạo, phiên bản mới, sao chép, gán id câu con), `QuestionImportDraftMapper`.

### 3.5 Chỗ trống và câu con

- `data-blank` = n trỏ tới câu con thứ n theo thứ tự hiện tại.
- Câu con không có chỗ trống là hợp lệ (bộ câu hỏi đọc hiểu có câu không gắn chỗ trống).
- Lỗi `BLANK_CHILD_MISMATCH` (chặn Sẵn sàng) chỉ khi: n lớn hơn số câu con, hoặc hai chỗ trống cùng n.
- Trình soạn tự giữ khớp: đổi thứ tự câu con thì đánh lại `data-blank` theo thứ tự mới; xóa câu con k thì xóa chỗ trống của nó và giảm 1 các số lớn hơn k; thêm câu con không tự thêm chỗ trống; xóa chỗ trống không đổi câu con.

## 4. Hành vi trình soạn

### 4.1 Thanh công cụ, menu `/`, `$…$` (E1)

- Thanh công cụ theo `SoanThaoTongQuan`: Hoàn tác, Làm lại; nhóm "Văn bản" (đậm, nghiêng, gạch chân, gạch ngang, chỉ số trên, chỉ số dưới, danh sách); nhóm "Chèn" (Công thức, Hóa học, Ký hiệu, Bảng, Hình, Đoạn trích; thêm Gạch chân đánh nhãn khi ô là đề bài câu Trắc nghiệm 1 đáp án; Chỗ trống khi ô là ngữ liệu câu chùm). Sơ đồ lai chỉ có trong menu `/`.
- Điều hướng phím (roving tabindex): cả thanh công cụ là một điểm dừng Tab; mũi tên trái/phải di chuyển giữa các nút, Home/End về đầu/cuối; Tab ra khỏi thanh vào nội dung. Nút giữ `aria-pressed` cho định dạng đang bật.
- Gõ `/` ở đầu dòng hoặc sau khoảng trắng mở menu chèn có ô lọc (dùng `@tiptap/suggestion`); mỗi mục có tên và một dòng mô tả như bản vẽ. Esc đóng, Enter chọn, mũi tên di chuyển.
- Gõ `$…$` (đóng bằng `$`) đổi thành công thức cùng dòng; `$$…$$` thành công thức khối. `\$` giữ ký tự `$`. Số tiền kiểu "5$" hoặc "$5" không có cặp đóng thì không đổi.
- Chọn một node đặc biệt trong nội dung thì hiện **khung "đang chọn"** ngay dưới thanh công cụ. Mỗi loại node khai báo khung của mình qua registry (plan B1). Khung công thức theo bản vẽ: mã LaTeX sửa trực tiếp, Trong dòng/Dòng riêng, "Xóa công thức", nút "Sửa" mở hộp công thức (công thức bắt đầu bằng `\ce{` thì mở hộp Hóa học).
- Mở lại hộp: nhấp đúp node, hoặc chọn node rồi bấm nút cùng loại trên thanh công cụ hoặc nút "Sửa" trong khung đang chọn.

### 4.2 Hộp công thức toán (E2)

- Ô soạn trực quan dùng thư viện `mathlive` (MIT): Tab sang ô kế tiếp, `/` tạo phân số, `^` mũ, `_` chỉ số. Ô mã LaTeX và ô trực quan đồng bộ hai chiều; sửa bên nào bên kia cập nhật.
- Lấy LaTeX từ ô trực quan bằng `getValue("latex-expanded")`. Nút Chèn tắt khi KaTeX báo lỗi hoặc còn ô giữ chỗ (`\placeholder`). Cấu hình `MathfieldElement.fontsDirectory` trỏ tới phông chép vào `public/` và `soundsDirectory = null` (gói Next không tải được phông theo đường mặc định).
- Bảng nút theo tab: Thường dùng, Phân số căn, Mũ chỉ số, Giới hạn tích phân, Hệ ma trận, Hình học vectơ, Tập hợp logic, Hy Lạp. Bấm nút chèn mẫu vào vị trí con trỏ của ô trực quan. Danh sách mẫu của mỗi tab nằm trong một tệp dữ liệu (`math-palette.ts`), không viết rải trong JSX.
- Nhãn "Hợp lệ"/"Lỗi cú pháp" dưới ô LaTeX: dựng thử bằng KaTeX (`throwOnError: true`); lỗi thì hiện thông báo của KaTeX đã dịch sang câu dễ hiểu ở mức "Thiếu dấu đóng", "Lệnh không hỗ trợ: \…"; còn lại "Công thức chưa dựng được".
- Xem trước: chọn Trong dòng/Dòng riêng; xem trước đặt công thức vào câu đang soạn (một đoạn chữ trước và sau vị trí chèn, tối đa 80 ký tự mỗi bên).
- Dùng gần đây: tối đa 8 công thức gần nhất của người dùng, lưu ở `localStorage` theo id người dùng (đọc ghi trong try/catch; lỗi thì không hiện mục này).
- `mathlive` chỉ tải khi mở hộp (dynamic import), không vào gói chung của trang.
- Câu dưới cùng: "Công thức lưu dưới dạng LaTeX. Khi thi, học viên thấy công thức đã dựng, không thấy mã."

### 4.3 Hóa học (E3)

- Ô "Gõ nhanh" nhận cú pháp mhchem gần với viết tay: `H2SO4`, `Fe^3+`, `->`, `<=>`, `(s)`, `(aq)`. Không tự viết bộ chuyển đổi: chuỗi gõ nhanh chính là nội dung `\ce{…}`.
- Nút: mũi tên (→ ⇌ ← ↑ ↓), trạng thái, điện tích, ΔrH°, nồng độ `[ ]`, liên kết đôi, ba, đồng vị. Bấm nút chèn đoạn mhchem tương ứng vào ô gõ nhanh.
- "Trên mũi tên", "Dưới mũi tên": ghép vào mũi tên đầu tiên trong chuỗi theo cú pháp `->[trên][dưới]`.
- "Ghi sau phương trình": chèn **một công thức cùng dòng riêng** đặt ngay sau phương trình, cách một khoảng trắng.
- Mặc định chèn cùng dòng; có Trong dòng/Dòng riêng như hộp toán.
- Mở lại (công thức bắt đầu bằng `\ce{`): ô gõ nhanh nhận nội dung trong `\ce{…}`; điều kiện của mũi tên đầu tiên tách lại vào "Trên/Dưới mũi tên"; "Ghi sau" không lấy lại (là công thức riêng).
- Xem trước dựng bằng KaTeX + mhchem. Dùng gần đây như 4.2 (danh sách riêng).

### 4.4 Ký hiệu và đơn vị (E4)

- Nhóm: Dùng gần đây, Chữ Hy Lạp, Toán tử và quan hệ, Mũi tên, Đơn vị đo, Sinh học, Phiên âm IPA (danh sách đúng bản vẽ, trong tệp dữ liệu).
- Bấm ký hiệu chèn ký tự Unicode thường vào vị trí con trỏ (không phải công thức). Ký hiệu có mũ hoặc chỉ số chèn bằng `sup`/`sub`: đơn vị (m/s², W/m²), sinh học (F₁, Xᴬ dùng `sub`/`sup` thật, không dùng ký tự Unicode chỉ số).
- Hộp không đóng sau mỗi lần bấm; có ô tìm theo tên ("alpha", "ohm", "độ C").

### 4.5 Bảng số liệu (E5)

- Trường: Tên bảng, Đơn vị, Nguồn; lưới ô sửa trực tiếp; Thêm hàng, Thêm cột, Xóa hàng, Xóa cột, Gộp ô; "Hàng đầu là tiêu đề", "Cột đầu là tiêu đề", "Căn phải cột số".
- Ô số: chỉ gồm chữ số, dấu trừ, dấu phẩy thập phân, dấu chấm hoặc khoảng trắng phân cách nghìn, có thể có `%` cuối ("1096,1", "2 268,5", "-3,2%"). "Căn phải cột số" đặt `class="num"` cho ô số trong cột mà mọi ô không phải tiêu đề đều là số.
- Dán vùng ô từ Excel hoặc Google Sheets (văn bản phân cách bằng tab và xuống dòng) vào lưới: điền từ ô đang chọn, tự thêm hàng cột.
- Chọn một bảng số liệu trong nội dung rồi bấm Bảng (hoặc nhấp đúp): mở lại hộp với dữ liệu hiện có.
- Tối đa 30 hàng, 15 cột.

### 4.6 Hình (E6)

- Tải ảnh qua `POST /question-assets` như hiện có (5 MB; PNG, JPEG, WebP). Thay ảnh, Xóa ảnh. Câu dưới hộp: "Nhận PNG, JPG, WebP." (khác bản vẽ, D17).
- Mô tả thay thế bắt buộc (nút Chèn tắt khi trống), câu hướng dẫn "Trình đọc màn hình đọc mô tả này. Không ghi đáp án vào đây."
- Chú thích dưới hình, Nguồn (không bắt buộc). Độ rộng Nhỏ/Vừa/Rộng hết khung. "Cho phép học viên phóng to hình khi thi" (lưu `data-zoom`; hành vi phóng to làm ở đợt 3).
- Ô nội dung có `allowImages = false` (hướng dẫn làm bài) không có mục Hình.

### 4.7 Đoạn trích (E7)

- Kiểu: Thơ, Văn xuôi, Tư liệu. Nội dung bắt buộc; Tác giả, Tác phẩm, Ghi chú nguồn.
- Thơ: "Giữ nguyên ngắt dòng" (mỗi dòng thành `br`; dòng trống tách khổ, mỗi khổ một `p`; tắt thì mỗi khổ là một dòng liền; khi dựng, giữa hai khổ có một khoảng trống bằng một dòng), "Đánh số dòng, 5 dòng một lần". Văn xuôi và Tư liệu không ngắt dòng, không đánh số.
- Xem trước như bản vẽ; số dòng hiện ở cột trái, chỉ dòng 5, 10, 15…

### 4.8 Tiếng Anh: gạch chân đánh nhãn, chỗ trống đánh số (E8)

- Gạch chân đánh nhãn (đề bài câu Trắc nghiệm 1 đáp án, kể cả câu con dạng này): bôi đen một cụm, bấm "Gạch chân đánh nhãn", nhãn tự lấy chữ kế tiếp chưa dùng (A, B, C, D…). Chọn một cụm đã gạch: khung "Cụm đang chọn: nhãn C" có "Đổi nhãn", "Bỏ gạch chân".
- "Phương án tự lấy theo các cụm gạch chân" (chỉ Trắc nghiệm 1 đáp án; form `question-editor-choice.tsx` dùng chung cho Nhiều đáp án thì không hiện): không lưu thành trường riêng. Khi tạo cụm thứ hai trong câu mới thì bật. Khi mở câu đã lưu thì chỉ bật nếu mọi phương án đang có chữ đúng bằng chữ của cụm cùng nhãn. Bật thì phương án A–D lấy chữ thuần (bỏ định dạng) của cụm cùng nhãn và cập nhật khi sửa cụm; số phương án khác số cụm thì báo ngay dưới danh sách phương án. Tắt thì phương án soạn tay như cũ.
- Chỗ trống đánh số (ngữ liệu câu chùm): bấm "Chỗ trống" chèn `span.blank` gắn với câu con đầu tiên chưa có chỗ trống (không còn câu con nào chưa có chỗ trống thì báo "Thêm câu con trước"). Ô "Đánh số từ" (`blankNumberStart`) cạnh ngữ liệu. Bảng "Chỗ trống và câu con" liệt kê từng chỗ trống, câu con gắn với nó, dạng và đáp án. Giữ khớp theo 3.5.
- Dựng: `span.blank` hiện "(31) ____"; `u[data-label]` hiện gạch chân và nhãn chữ nhỏ phía dưới như bản vẽ.

### 4.9 Sơ đồ lai (E9)

- Lưới thế hệ: P (♂ × ♀), G, F₁; "Thêm thế hệ" thêm F₂, F₃. Mỗi ô là chữ thường có ký hiệu (♂ ♀ × ; :), chỉ số dưới gõ bằng `_` hoặc nút.
- Lưu thành `figure.cross` (mục 3.2). Chọn sơ đồ trong nội dung để mở lại hộp.

### 4.10 Dán từ Word (E10)

- Kích hoạt: dán nội dung của Word vào ô đề bài một câu đang soạn, hoặc bấm "Dán từ Word" ở đầu form soạn câu. Nhận diện Word: HTML trên clipboard có namespace `urn:schemas-microsoft-com:office:word` hoặc `<meta name="Generator" content="Microsoft Word…">`. Hộp mở ra, không dán thẳng vào ô. Bộ bắt sự kiện dán phải nhận `clipboardData` gốc **trước** bước lọc HTML hiện có (bước lọc bỏ chú thích HTML, nơi Word đặt OMML).
- Công thức Word (OMML trong khối `<!--[if gte msEquation 12]>`) đổi sang LaTeX phía client bằng bộ đổi tự viết cho tập con: phân số (`m:f`), căn (`m:rad`), mũ/chỉ số (`m:sSup`, `m:sSub`, `m:sSubSup`), ngoặc (`m:d`), hệ (`m:eqArr`), tổng/tích/tích phân (`m:nary`), hàm (`m:func`), ma trận (`m:m`), dấu trên (`m:acc`, `m:bar`), giới hạn (`m:limLow`, `m:limUpp`), chữ (`m:r`). Phần tử ngoài tập con: công thức đó vào nhóm "Cần gõ lại".
- Công thức chỉ có dạng ảnh (MathType, ảnh chụp): bản HTML trên clipboard chỉ trỏ tới tệp cục bộ, trình duyệt không đọc được, nên **đợt này không có "Giữ làm hình"** (khác bản vẽ; để lại). Mỗi ảnh công thức là một mục "Cần gõ lại" với nút "Gõ lại công thức" (mở hộp công thức toán, chèn vào đúng chỗ) hoặc "Bỏ" (xóa chỗ đó, hỏi xác nhận). "Đưa vào biểu mẫu" tắt khi còn mục "Cần gõ lại" chưa xử lý.
- Tự sửa có đánh dấu chờ xác nhận, đúng bốn lỗi trong bản vẽ: tên hàm bị tách chữ (`s i n` → `\sin`), hệ "hoặc" bị đổi thành phân số (→ `\left[\begin{array}…`), công thức hóa học bị bọc ngoặc, ký hiệu độ. Mỗi chỗ: "Chấp nhận", "Sửa tay"; nút "Chấp nhận mọi chỗ tự sửa".
- "Tách theo nhãn A., B., C., D. và Lời giải" là ô chọn, mặc định bật với Trắc nghiệm 1 đáp án và Nhiều đáp án, ẩn với dạng khác: "A."… đầu dòng thành phương án, "Lời giải" thành lời giải, phần trước thành đề bài. Tắt hoặc dạng khác thì đưa toàn bộ vào ô đang dán.
- Thanh tóm tắt: số công thức đổi đúng, số đã tự sửa chờ xác nhận, số ảnh công thức cần gõ lại. "Chỉ hiện mục chưa xử lý".
- "Đưa vào biểu mẫu" điền các ô của form (ô trống thì điền; ô đã có nội dung thì hỏi xác nhận ghi đè). Chỗ tự sửa chưa xác nhận vẫn được đánh dấu trong ô sau khi đưa vào bằng decoration của ProseMirror (không vào HTML, mất khi tải lại trang). Lưu câu không bị chặn bởi đánh dấu này.
- Chỉ xử lý phía client; không thêm endpoint. Ảnh thường (không phải công thức) trong bản dán cũng không đọc được: liệt kê "Ảnh chưa đưa vào" để người soạn chèn lại bằng hộp Hình.

## 5. Dựng nội dung (`RichTextView`)

- Dựng đủ các khối mục 3 với token của `DESIGN.md`, sáng và tối, vi/en (nhãn như "Nguồn", "Đơn vị" lấy từ HTML đã lưu, không dịch).
- KaTeX nạp thêm mhchem. Công thức lỗi hiện mã gốc trong khung cảnh báo, không làm vỡ trang.
- Đợt 3 chuyển `RichTextView` sang `packages/ui` (plan đợt 3, B8); đợt này giữ ở web admin nhưng không phụ thuộc gì riêng của web admin để chuyển được nguyên khối.
- `RichTextView` nhận prop `blankNumberStart` (mặc định 1). Nơi dựng câu chùm truyền giá trị của câu: chi tiết câu, xem trước câu, so sánh phiên bản, xem trước đề (trong xem trước đề tạm dùng giá trị của câu; đợt 3 đổi sang vị trí trong đề).

## 6. Kiểm tra sẵn sàng

Thêm vào các quy tắc của kiểm tra sẵn sàng (`QuestionBodyValidator`, cùng chỗ với NO_GAPS); kiểm cả câu con của câu chùm:
- `BLANK_CHILD_MISMATCH` (chặn): mục 3.5.
- `BLANK_OUTSIDE_SET_PASSAGE` (chặn): `span.blank` ở trường khác ngữ liệu câu chùm.
- `UNDERLINE_LABEL_DUPLICATE` (chặn): hai `u[data-label]` cùng nhãn trong một câu.
- `UNDERLINE_LABEL_MISPLACED` (chặn): `u[data-label]` ngoài đề bài câu Trắc nghiệm 1 đáp án.
- `IMAGE_ALT_MISSING` (cảnh báo, không chặn): `img` không có `alt` hoặc `alt` rỗng.

Web admin: thêm năm mã vào bảng thông báo kiểm tra sẵn sàng (vi/en).

## 7. Giao diện chung

- Hộp thoại dùng `Dialog` của kit, cỡ theo bản vẽ (rộng 980–1020 px ở màn rộng, toàn màn ở dưới 640 px). Lưới nút ký hiệu, nút công thức dùng một thành phần chung `SymbolGrid` (nút 40×40, có `aria-label` tiếng Việt và tiếng Anh).
- `DESIGN.md` mục 5.15 (Trình soạn nội dung) viết lại: hiện ghi "không thêm nút ngoài danh sách đó" và liệt kê thanh công cụ cũ. Mục mới ghi thanh công cụ, điều hướng phím (4.1), menu `/`, hộp chèn, lưới ký hiệu, khung đang chọn.
- Mọi chữ qua `apps/web-admin/src/messages/{vi,en}.json` dưới `richText.*`; tên ký hiệu và nhóm có bản tiếng Anh.

## 8. Kiểm thử

- Backend: test bộ lọc đọc bảng mẫu (mẫu hợp lệ giữ nguyên byte với bộ phân giải ảnh giả và id ảnh cố định; mẫu vi phạm trả đúng tên); IT lưu câu có mỗi khối, đọc lại giữ nguyên; IT năm quy tắc sẵn sàng mới; IT `blankNumberStart` khi tạo, phiên bản mới, sao chép, câu cũ.
- Web: vitest cho từng hộp (mở, nhập, chèn, mở lại để sửa), menu `/`, `$…$`, đồng bộ LaTeX hai chiều (giả lập `mathlive` trong jsdom), dán Excel vào bảng, gạch chân và phương án tự lấy, chỗ trống đánh lại số khi đổi thứ tự câu con, dán Word với mẫu clipboard thật (tệp mẫu trong `__fixtures__`, gồm OMML, ảnh công thức, bốn lỗi tự sửa).
- E2E trên backend thật: soạn một câu Toán có công thức qua ô trực quan, một câu Hóa có phương trình, một câu Địa có bảng và hình, một câu Văn có đoạn trích thơ, một câu Tiếng Anh tìm lỗi sai, một câu chùm bài đọc điền từ 5 chỗ trống, một câu dán từ Word; lưu, Sẵn sàng, xem trước; sáng/tối, vi/en.
