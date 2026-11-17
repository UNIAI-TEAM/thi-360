# Plan triển khai: Trình soạn nội dung đầy đủ (UNI-2638)

> Ngày 2026-11-17 · Issue: **UNI-2638** (vừa là issue đặc tả vừa là issue cha; task con tạo ở bước 0) · Spec: `docs/specs/2026-11-17-trinh-soan-noi-dung.md` (gọi tắt "spec") · Bản vẽ: `docs/design/dot-2/SoanThao*.dc.html` · Bảng mẫu HTML: `docs/specs/fixtures/rich-text-samples.json`.
>
> Plan đủ để Advisor chạy `zone17-cook-plan` mà không phải hỏi lại. Khác nhau về *cách làm* thì theo plan; về *hành vi* thì theo spec và báo lại. Quy ước của đợt 1 (`docs/plans/2026-11-13-base-port-pbi-01-02.md` Phần B) và đợt 2 (`docs/plans/2026-11-16-be-pbi-03-04.md` Phần B) vẫn áp dụng. Đã sửa theo review lượt 1 (17 điểm, 2026-11-17).

## A. Cấu hình đợt chạy

| Mục | Giá trị |
|---|---|
| Nhánh tích hợp | `integration/editor`, tách từ `origin/develop` sau khi PR tài liệu của UNI-2638 (spec, plan, bảng mẫu, bản vẽ) đã merge. Lane tách từ đầu nhánh này, PR squash vào nó |
| Về `develop` | Một PR `integration/editor` → `develop` sau QA, CI xanh. Advisor merge khi xanh (người đã cho phép: "xanh thì merge luôn") |
| Đợt chạy | Dùng lại Run `run_29d79f69be23` và thư mục `D:/.Vietants_Project/.thi360-dev/dot-2-be/` (manifest có thêm các lane `ED-*`, hợp đồng `contracts/ED-*.md`, báo cáo `reports/ED-*/`, khởi lead bằng `automation/launch-local-lead-editor.sh`). Worktree theo Orca (`D:/orca/workspaces/cong-thi-360/<lane>`) |
| Issue | ED-0a UNI-2639 · ED-0b UNI-2640 · ED-1 UNI-2641 · ED-2 UNI-2642 · ED-3 UNI-2643 · ED-4 UNI-2644 · ED-5 UNI-2645 · QA UNI-2646 |
| Theo dõi | UniAI: UNI-2638 là cha; mỗi task một issue con. Lead bình luận `[agent]` mỗi mốc. Không tự chuyển `done` |
| Đội | Roster đợt 2 (`dot-2-be/team-rules.md`), Jev qua router luna; build, lint, test chạy trên cloud runner |
| Cổng | worker → tester_cloud (backend verify, web lint/typecheck/test/build) ∥ tester_visual (màn chạm tới, sáng/tối, vi/en) → reviewer_be/reviewer_fe full → vòng sửa (quay lại test) → Advisor |
| Tài liệu repo | `PROGRESS.md`, `TASK.md` do Advisor cập nhật; lane không sửa |

Trọng số (100 điểm): ED-0a 12, ED-0b 18, ED-1 18, ED-2 10, ED-3 12, ED-4 12, ED-5 12, QA 6.

### Bước 0 (Advisor, trước sóng 1)

- Bảng mẫu HTML `docs/specs/fixtures/rich-text-samples.json` đã chốt trong PR tài liệu. Hai lane sóng 1 dùng cùng bảng này; được thêm mẫu, không sửa mẫu đã có khi chưa hỏi Advisor.
- Tạo `integration/editor`, issue con, thư mục đợt chạy, hợp đồng lane.
- Đo mốc kích thước gói: trên đầu `develop`, `pnpm --filter @thi360/web-admin build`, ghi "First Load JS" của route `/questions/new` và `/questions/[id]/edit` từ bảng route của Next vào `editor/bundle-baseline.md`.

## B. Quy ước kỹ thuật

- **B1. Thư mục.** Mọi thứ của trình soạn ở `apps/web-admin/src/components/rich-text/`:
  - `extensions/` node và mark TipTap, một tệp mỗi khối của spec 3.2. Khối `figure`/`blockquote` là node nguyên khối (atom), dữ liệu trong thuộc tính node, `renderHTML` xuất đúng dạng bảng mẫu. Hai loại node này chỉ được nằm ở cấp đầu của tài liệu (nhóm riêng, không thuộc `block`), để không chèn được vào ô bảng hay mục danh sách (spec 3.3). ED-0b tạo sẵn và đăng ký hai tệp rỗng `slash-menu.ts`, `math-input-rule.ts` để ED-1 không phải sửa `rich-text-editor.tsx`.
  - `insert/registry.ts`: danh sách mục chèn. Mỗi mục: `id`, khóa nhãn và mô tả, biểu tượng, `showIn(fieldContext)`, `load: () => import("./<id>/dialog")`, `openForSelection(node)` (node nào mở lại hộp này), `selectionPanel?` (khung "đang chọn" cho loại node hoặc mark của mục), `editorPlugins?` (plugin ProseMirror của mục, ví dụ decoration của ED-5). ED-0b tạo đủ mục; lane sóng 2 chỉ sửa thư mục `insert/<id>/` của mình. Đổi `registry.ts` thì hỏi Advisor.
  - `insert/shared/`: `SymbolGrid`, `InsertDialogShell` (tiêu đề, mô tả, Hủy/Chèn, cỡ spec 7), `recent-store.ts` (dùng gần đây theo id người dùng, `localStorage` trong try/catch).
  - `view/`: `RichTextView` và phần dựng từng khối; chỉ import `@thi360/ui`, `katex`, React (spec 5).
  - `html-contract.ts`: danh sách thẻ, class, `data-*` và luật cấu trúc, giống hệt bên Java. `sanitize.ts` (lọc khi dán và khi dựng) dùng bảng này.
  - `fieldContext`: prop mới của `RichTextEditor` (`"stem-single-choice" | "set-passage" | "instructions" | "default"`), quyết định mục chèn nào hiện. Nơi gọi trình soạn truyền prop này; ED-0b sửa các nơi gọi.
  - `onRichPaste(clipboardData)`: hook của `RichTextEditor`, gọi **trước** bước lọc HTML khi dán; trả `true` thì trình soạn bỏ qua dán mặc định. ED-0b làm hook, ED-5 dùng.
- **B2. HTML là hợp đồng.** Test Java (ED-0a) và vitest (ED-0b) cùng đọc `docs/specs/fixtures/rich-text-samples.json`. Test Java đọc theo đường tương đối từ `backend/` (`../docs/specs/fixtures/…`); cloud runner sao cả repo nên đọc được. Ảnh: id cố định trong tệp, test Java dùng bộ phân giải ảnh giả.
- **B3. Thư viện mới, đã duyệt.** `mathlive` (MIT, ED-1; tải động; phông chép vào `apps/web-admin/public/mathlive/fonts/`, `soundsDirectory = null`); `@tiptap/suggestion` bản `3.34.0` cùng các gói TipTap khác (ED-1, menu `/`); `katex/contrib/mhchem` (có sẵn trong `katex`). Bộ đổi OMML → LaTeX **tự viết** cho tập con trong spec 4.10 (ED-5), không thêm thư viện. Thư viện khác: hỏi Advisor.
- **B4. Test.** Vitest chạy trong jsdom; `mathlive` giả lập bằng module mock cùng API tối thiểu (`value`, `getValue`, sự kiện `input`, `insert`). Mẫu clipboard Word thật trong `insert/word/__fixtures__/` (HTML lấy từ Word 365 trên Windows, có công thức OMML, ảnh công thức, bốn lỗi tự sửa; README ghi cách lấy).
- **B5. Chữ.** Khóa i18n trong `apps/web-admin/src/messages/{vi,en}.json` dưới `richText.*`. ED-0b tạo sẵn khối khóa rỗng `richText.insert.<id>` cho mọi mục; lane chỉ thêm khóa trong khối của mình. Không nhãn tiếng Việt cứng trong code.
- **B6. `DESIGN.md`.** Mục 5.15 (Trình soạn nội dung) do ED-0b viết lại, có sẵn tiểu mục cho từng hộp; lane sóng 2 chỉ sửa tiểu mục của mình.
- **B7. Tệp chung.** `messages/*.json` và `DESIGN.md` vẫn là tệp chung: lane rebase lên đầu `integration/editor` trước khi nộp; Advisor merge từng PR một và giải xung đột ở khối của lane.
- **B8. Cổng giao diện.** tester_visual chụp từng hộp ở 1440 px và 390 px, sáng/tối, vi/en, đặt cạnh bản vẽ tương ứng; kiểm theo quy tắc rà giao diện của dự án (nhãn lẫn ngôn ngữ, tương phản).

## C. Sóng thực thi

```
Bước 0:  Advisor — bảng mẫu (đã có), nhánh, issue, mốc gói
Sóng 1:  ED-0a (BE)  ∥  ED-0b (FE nền)
Sóng 2:  ED-1 ∥ ED-2 ∥ ED-3 ∥ ED-4 ∥ ED-5      (khi CẢ ED-0a và ED-0b đã merge)
Sóng 3:  QA                                      (khi mọi lane đã merge)
```

Sóng 2 chờ cả ED-0a vì lưu nội dung có khối mới sẽ trả 422 khi bộ lọc chưa mở rộng. Đường găng: ED-0b → ED-1 → QA. Thiếu tài nguyên thì ưu tiên ED-1, ED-4, ED-5.

## D. Hợp đồng từng task

### ED-0a · Backend: bộ lọc, luật cấu trúc, quy tắc sẵn sàng, `blankNumberStart`

**Spec:** 3, 6.
**Sản phẩm:**
- `QuestionHtmlSanitizer`: mở rộng danh sách phẳng đúng spec 3.2–3.3, và thêm **lượt kiểm theo phần tử** cho luật cấu trúc spec 3.3 (danh sách phẳng `attributeAllowed` không diễn đạt được cha/con, số lượng con, `class` một giá trị).
- `QuestionBodyValidator`: năm mã spec 6, kiểm cả câu con.
- `QuestionSetBody.blankNumberStart` (spec 3.4): mọi chỗ dựng lại `QuestionSetBody` mang trường này — `QuestionService` (tạo, phiên bản mới, sao chép, gán id câu con; hiện ở khoảng dòng 294, 580, 1027) và `QuestionImportDraftMapper`. OpenAPI và `packages/api-client` sinh lại.
- Không migration dữ liệu.
**Sở hữu:** `backend/src/main/java/vn/unicom/thi360/questionbank/**`, test tương ứng, `packages/api-client` (sinh).
**Nghiệm thu:** test bộ lọc chạy mọi mẫu của bảng mẫu (hợp lệ: không vi phạm và giữ nguyên chuỗi; vi phạm: đúng danh sách); IT lưu và đọc lại từng khối; IT năm quy tắc; IT `blankNumberStart` khi tạo, phiên bản mới, sao chép, câu cũ, giá trị ngoài khoảng (422); backend verify xanh trên cloud.

### ED-0b · Web nền: node TipTap, `RichTextView`, thanh công cụ, registry

**Spec:** 3, 4.1 (thanh công cụ, phím, khung đang chọn), 5, 7.
**Sản phẩm:**
- Node/mark cho mọi khối spec 3.2 (atom cho `figure`/`blockquote`; mark `underlineLabel` ưu tiên hơn gạch chân thường; node `blank`), parse và render đúng bảng mẫu; giữ hai node công thức và bảng thường cũ.
- `RichTextView` dựng đủ khối, KaTeX + mhchem, công thức lỗi hiện khung cảnh báo, prop `blankNumberStart`.
- Thanh công cụ theo spec 4.1 (Hoàn tác, Làm lại, hai nhóm, roving tabindex), khung "đang chọn" lấy từ registry, mở lại hộp qua `openForSelection`.
- Registry, `InsertDialogShell`, `SymbolGrid`, `recent-store`, `html-contract.ts`, `sanitize.ts` mở rộng theo bảng hợp đồng, `fieldContext`, `onRichPaste`.
- **Hộp công thức cũ** (đang viết thẳng trong `rich-text-editor.tsx`, khoảng dòng 278–521) chuyển nguyên vào `insert/math/` làm bản tạm của mục `math`, giữ khóa `richText.formula.*` và test cũ xanh. Các mục khác dùng hộp tạm "Đang làm".
- Khối khóa i18n rỗng (B5), `DESIGN.md` 5.15 viết lại (B6).
**Sở hữu:** `components/rich-text/**` trừ `insert/<id>/` của mục khác `math`; các nơi gọi `RichTextEditor` (chỉ thêm `fieldContext`); khóa `richText.*` chung; `DESIGN.md` 5.15.
**Nghiệm thu:** vitest: mỗi mẫu hợp lệ nạp vào trình soạn rồi xuất ra đúng chuỗi (giá trị `onChange`; nếu TipTap tự thêm `<p></p>` sau khối cuối thì bỏ đoạn rỗng đó trước khi phát); `RichTextView` dựng mỗi mẫu (kiểm vai trò, nhãn); `sanitize.ts` loại đúng các mẫu vi phạm; mẫu `legacy-plain-table` đi qua trình soạn rồi ra đúng chuỗi (khẳng định `sanitize.ts` bỏ `style`, `colgroup` của TableKit); xác nhận StarterKit có thêm đoạn rỗng cuối hay không và ghi vào báo cáo; thanh công cụ dùng được bằng bàn phím; test cũ của trình soạn và màn soạn câu xanh. Visual: thanh công cụ và mỗi khối dựng, 1440/390, sáng/tối, vi/en.

### ED-1 · Menu `/`, `$…$`, khung "Công thức đang chọn", hộp công thức toán

**Spec:** 4.1, 4.2.
**Sản phẩm:** menu `/` (`@tiptap/suggestion`) có lọc và phím; input rule `$…$`, `$$…$$`, `\$`; khung "Công thức đang chọn" theo bản vẽ (khai báo qua registry của mục `math`); hộp công thức toán thay bản tạm: `mathlive` tải động, `latex-expanded`, Chèn tắt khi lỗi hoặc còn giữ chỗ, tám tab từ `math-palette.ts`, đồng bộ hai chiều, nhãn hợp lệ, xem trước trong câu, dùng gần đây. Xóa khóa `richText.formula.*` không còn dùng; cập nhật `__tests__/rich-text-editor.test.tsx`, `__tests__/math-command.test.ts`, `features/questions/__tests__/editor-test-kit.tsx`.
**Sở hữu:** `insert/math/`, `extensions/slash-menu.ts`, `extensions/math-input-rule.ts`, ba tệp test vừa nêu, `public/mathlive/`.
**Nghiệm thu:** vitest cho menu, input rule (kể cả `\$`, "5$", "$5"), đồng bộ hai chiều (mock), lỗi cú pháp, Chèn tắt khi còn giữ chỗ; "First Load JS" của hai route ở bước 0 không tăng quá 30 KB gzip so với mốc khi chưa mở hộp. Visual: hộp công thức cạnh bản vẽ; E2E gõ công thức qua ô trực quan thật (mathlive thật).

### ED-2 · Hóa học, Ký hiệu, Sơ đồ lai

**Spec:** 4.3, 4.4, 4.9.
**Sở hữu:** `insert/chemistry/`, `insert/symbols/`, `insert/cross/`.
**Nghiệm thu:** vitest: gõ nhanh → `\ce{…}` đúng, mũi tên có điều kiện trên/dưới, ghi sau là công thức riêng, mở lại tách lại điều kiện; ký hiệu chèn đúng ký tự, `sub`/`sup` cho đơn vị và sinh học, tìm theo tên vi/en, hộp không đóng sau mỗi lần bấm; sơ đồ lai thêm thế hệ, mở lại để sửa, xuất đúng mẫu `cross-diagram`. Visual từng hộp.

### ED-3 · Bảng số liệu, Hình, Đoạn trích

**Spec:** 4.5, 4.6, 4.7.
**Sở hữu:** `insert/table/`, `insert/image/`, `insert/quote/`.
**Nghiệm thu:** vitest: dán vùng ô tab/xuống dòng, gộp ô, tiêu đề hàng/cột, nhận diện ô số và `class="num"`, giới hạn 30×15, mở lại để sửa, xuất đúng mẫu `data-table`; hình bắt buộc mô tả, độ rộng, phóng to, thay/xóa ảnh, không có ở `fieldContext="instructions"`, xuất đúng mẫu `image-figure`; đoạn trích thơ nhiều khổ, đánh số liên tục qua khổ, văn xuôi không đánh số, xuất đúng mẫu `quote-*`. Visual từng hộp.

### ED-4 · Tiếng Anh: gạch chân đánh nhãn, chỗ trống đánh số

**Spec:** 3.4, 3.5, 4.8, 6 (phần web). Cần ED-0a và ED-0b.
**Sở hữu:**
- `insert/underline-label/`, `insert/blank/`.
- Form câu hỏi: `features/questions/components/question-editor-choice.tsx` (phương án tự lấy, chỉ Trắc nghiệm 1 đáp án), `question-editor-set.tsx` (ô "Đánh số từ", bảng "Chỗ trống và câu con", giữ khớp khi đổi thứ tự, thêm, xóa câu con).
- `blankNumberStart` trên web: `editor-form.ts`, `editor-schema.ts`, `editor-types.ts`, `wire-body.ts`, `api-mapping.ts`, `types.ts`.
- Năm mã sẵn sàng mới: `readiness-mapping.ts`, `readiness.ts`, khóa thông báo.
- Truyền `blankNumberStart` vào `RichTextView` ở: `question-detail-body.tsx`, `question-detail-content.tsx`, `preview/question-player.tsx`, `question-versions-compare.tsx`, `question-versions-view.tsx`, `features/exams/components/exam-preview-paper.tsx`, `features/question-import/components/question-preview.tsx` (chỉ thêm prop).
**Nghiệm thu:** vitest: nhãn kế tiếp, đổi nhãn, bỏ gạch; phương án tự lấy: bật khi tạo cụm thứ hai, khi mở câu cũ chỉ bật nếu khớp hết, báo thừa/thiếu, không hiện ở Nhiều đáp án; chỗ trống gắn câu con đầu tiên chưa có chỗ trống, đổi thứ tự / xóa câu con đánh lại `data-blank`, "Đánh số từ" đổi số hiển thị; E2E: lưu và Sẵn sàng bị chặn bởi `BLANK_CHILD_MISMATCH`, `UNDERLINE_LABEL_DUPLICATE`. Visual hai màn của `SoanThaoTiengAnh`.

### ED-5 · Dán từ Word

**Spec:** 4.10.
**Sở hữu:** `insert/word/` (gồm bộ đổi OMML → LaTeX, bốn bộ tự sửa, `__fixtures__`), `features/questions/components/question-editor-form.tsx` (nút "Dán từ Word", điền form, hỏi ghi đè), decoration đánh dấu chỗ chưa xác nhận (plugin trong `insert/word/`, gắn qua registry).
**Nghiệm thu:** vitest với mẫu clipboard thật: nhận diện Word; OMML → LaTeX đúng cho từng phần tử của tập con; phần tử ngoài tập con vào "Cần gõ lại"; bốn lỗi tự sửa có Chấp nhận/Sửa tay/Chấp nhận mọi chỗ; ảnh công thức là "Cần gõ lại", "Đưa vào biểu mẫu" tắt tới khi xử lý hết; tách A./B./C./D./Lời giải theo ô chọn; ô có nội dung thì hỏi ghi đè; dán HTML không phải Word đi đường cũ. Visual cạnh bản vẽ.

### QA · Nghiệm thu đợt

**Sản phẩm:** E2E spec 8 trên backend thật (bảy câu mẫu: lưu, Sẵn sàng, xem trước câu, xem trước đề), sáng/tối, vi/en; đo lại kích thước gói so với mốc bước 0; danh sách việc để lại (gồm "Giữ làm hình"); PR `integration/editor` → `develop`.
**Nghiệm thu:** báo cáo E2E có ảnh; tester_cloud xanh ở đầu nhánh; CI PR xanh.

## E. Rủi ro

| Rủi ro | Cách xử lý |
|---|---|
| `mathlive` nặng, không chạy thật trong jsdom | Tải động; test dùng mock (B4); hành vi thật kiểm ở visual và E2E của ED-1 |
| Clipboard Word khác nhau giữa bản Office | Fixture ghi rõ bản Word; OMML ngoài tập con rơi về "Cần gõ lại", không làm hỏng dán |
| HTML hai phía lệch nhau | Một bảng mẫu (B2) cho test Java và vitest, chốt ở bước 0 |
| Lane sóng 2 đụng tệp chung | Bảng sở hữu ở D; registry, khung đang chọn, khóa i18n, tiểu mục DESIGN do ED-0b làm sẵn; merge tuần tự (B7) |
| Máy Windows thiếu RAM khi nhiều lane E2E | Một stack E2E mỗi lúc (`devstack.lock`); build và test qua cloud runner |
