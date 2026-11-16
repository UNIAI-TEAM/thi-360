# Mockup 7 kỳ thi và chấm tự luận (UNI-2599)

Ngày: 2026-11-16. Issue cha UNI-2599 (con của UNI-2590). Nền UNI-2600; các nhánh UNI-2601 (G), UNI-2602 (H), UNI-2603 (I), UNI-2604 (J).

Mở rộng mockup web admin của PBI-03 (ngân hàng câu hỏi) và PBI-04 (bài thi) từ hai kỳ thi HSA, QDA lên bảy kỳ thi, thêm dạng câu Tự luận chấm tay. Vẫn chạy trên dữ liệu mẫu (`NEXT_PUBLIC_THI360_CONTENT_MOCK`), không có backend.

## 1. Nguồn

Cấu trúc đề do M.Trường tóm tắt ngày 2026-11-16 từ thư mục Drive "MÔ TẢ CẤU TRÚC ĐỀ THI - ALL KHÓA" (7 tệp docx và `MAPID - BẢN TỔNG HỢP.xlsx`). Agent không đọc được thư mục đó. Số liệu dưới đây lấy từ bản tóm tắt. Chỗ nào bản tóm tắt không ghi thì mẫu dữ liệu để trống hoặc ghi là "tạm", kèm ghi chú cần chuyên gia xác nhận (`expertNote`).

| Kỳ thi | Mã | Cấu trúc theo bản tóm tắt |
|---|---|---|
| ĐGNL ĐHQG Hà Nội | HSA | 150 câu, 195 phút. Toán 50 câu/75 phút, Văn học – Ngôn ngữ 50 câu/60 phút, Khoa học hoặc Tiếng Anh 50 câu/60 phút. Khoa học: chọn 3 trong 5 môn, admin đặt số câu từng môn (tổng 50); chia 17–17–16 chờ chuyên gia xác nhận. Bài lẻ: admin chọn phần bán và đặt thời lượng. |
| ĐGNL Bộ Quốc phòng | QDA | 150 câu, 195 phút. Toán 50 câu/80 phút, Ngôn ngữ – Văn học 50 câu/55 phút, phần lựa chọn 50 câu/60 phút: QĐ01 Lí+Hóa, QĐ02 Hóa+Sinh, QĐ03 Lí+Sinh, QĐ04 Sử+Địa (25 câu mỗi môn), QĐ05 Tiếng Anh 50 câu. Kiểm tra riêng từng mã trước khi bán. |
| Tuyển sinh Công an nhân dân | BCA | 81 câu, 180 phút chung, thang 100. Phần 1: 1 câu tự luận Ngữ văn, 25 điểm. Phần 2: 65 câu, 60 điểm (Toán 35 câu × 1, Lịch sử 10 × 1, Tiếng Anh 20 × 0,75). Phần 3: 15 câu, 15 điểm, CA1 Lí, CA2 Hóa, CA3 Sinh, CA4 Địa. Phần bắt buộc dùng chung cho 4 mã. Phải có hướng dẫn chấm tự luận trước khi công bố. |
| V-ACT 2026 | VACT | 120 câu chọn một, 150 phút chung. Ngôn ngữ: 30 Tiếng Việt và 30 Tiếng Anh. Toán: 30 câu. Tư duy khoa học: 12 câu logic, số liệu và 18 câu suy luận khoa học. Toán theo lớp 10, 11, 12 hoặc liên lớp; các phần khác theo năng lực, chủ đề, MAPID. Thang 1.200 (4 thành phần × 300) chấm bằng IRT; bài luyện tập tạm tính theo câu. |
| ĐHSP Hà Nội | SPT | Mẫu theo môn (12 môn, tài liệu 2025). Mỗi môn có thời lượng, phần, số câu, số ý và cách tính điểm riêng. Ví dụ Toán: 25 câu/90 phút gồm 16 chọn một, 2 đúng/sai, 4 trả lời ngắn và 3 tự luận. |
| ĐH Sư phạm Hà Nội 2 | SP2E | Mẫu theo môn và phiên bản (9 môn), mỗi mẫu có năm cấu trúc và năm ma trận. Toán 2025: 22 câu. Toán 2026: 40 câu gồm 25 lựa chọn (5 câu có thể nhiều đáp án đúng), 5 dữ liệu tổng hợp và 10 trả lời ngắn. Năm cấu trúc khác năm ma trận thì admin phải xác nhận chỉ tiêu chuyên môn. |
| Tốt nghiệp THPT | THPTQG | 12 mẫu theo môn. Ngữ văn: 5 câu đọc hiểu và 2 câu viết. Tin học: đề in 30 câu, học viên làm 28 theo nhánh. Đúng/sai: số ý và bảng điểm theo số ý đúng. |

Dữ liệu mẫu chỉ có mẫu SPT Toán và SP2E Toán (2025, 2026), vì bản tóm tắt chỉ ghi hai môn này. THPTQG có đủ 12 môn theo định dạng 2025 của Bộ GD&ĐT; agent tự điền phần này, chưa ai đối chiếu với tài liệu của dự án.

### 1.1. Bảng cấu trúc đề tổng quát

M.Trường gửi thêm bảng tổng quát 7 kỳ thi ngày 2026-11-16. Dữ liệu mẫu và đặc tả backend đã đối chiếu theo bảng này.

| Mã | Đơn vị, hình thức | Thời gian, thang điểm | Phần | Dạng câu, cách chấm |
|---|---|---|---|---|
| HSA | ĐHQG Hà Nội, thi trên máy | 195 phút, 150 điểm | P1 Toán học và Xử lí số liệu 50 câu; P2 Văn học – Ngôn ngữ 50 câu; P3 Khoa học (3A) hoặc Tiếng Anh (3B) 50 câu | Chọn một 4 phương án, điền đáp án (số nguyên, phân số, thập phân), chùm câu. 1 điểm mỗi câu đúng, không trừ điểm. |
| QDA | Bộ Quốc phòng, thi trên máy | 195 phút, 150 điểm | P1 Toán học và Xử lí số liệu 50; P2 Ngôn ngữ – Văn học 50; P3 Khoa học (3A: QĐ01–QĐ04) hoặc Tiếng Anh (3B: QĐ05) 50 | Như HSA. Câu điền đáp án nhận số nguyên, phân số, thập phân dấu phẩy, phải đúng giá trị. Tối đa 50 điểm mỗi phần. |
| BCA | Đánh giá tuyển sinh Công an nhân dân (đề minh họa 2026), thi trên giấy | 180 phút chung, 100 điểm (25 tự luận, 75 trắc nghiệm) | P1 tự luận Ngữ văn: 1 câu nghị luận xã hội, tối thiểu 500 chữ, 25 điểm; P2 bắt buộc Toán, Lịch sử, Tiếng Anh 65 câu, 60 điểm; P3 tự chọn CA1–CA4 15 câu, 15 điểm | Toán có câu trả lời ngắn. Toán, Lịch sử, phần tự chọn 1 điểm mỗi câu; Tiếng Anh 0,75 điểm mỗi câu. |
| SP2E | ĐH Sư phạm Hà Nội 2 (9 môn năm 2026), phiếu trả lời | 60–90 phút tùy môn, 10 điểm mỗi môn | Đa số môn 3 phần: Nhiều lựa chọn, Đúng/Sai, Trả lời ngắn hoặc Ghép đôi | Ngữ văn: đọc hiểu trắc nghiệm và viết bài luận. Đúng/Sai chấm theo số ý đúng. |
| SPT | ĐH Sư phạm Hà Nội (12 môn, đề tham khảo 075) | 60–90 phút, 10 điểm mỗi môn | 3–4 phần | Môn chính: chọn một 4 phương án, Đúng/Sai, Trả lời ngắn, Tự luận. Văn, Sử, GDKT&PL: chọn một, Đúng/Sai, Tự luận. Trắc nghiệm chấm tự động; tự luận và trả lời ngắn chấm theo đáp án chi tiết. |
| THPTQG | Tốt nghiệp THPT, từ 2025 | 50–120 phút, 10 điểm mỗi môn | Toán, KHTN, Địa: 3 phần (chọn một, Đúng/Sai, Trả lời ngắn). Sử, GDKT&PL, Công nghệ, Tin: 2 phần (chọn một, Đúng/Sai). Ngữ văn: tự luận 2 phần | Đúng/Sai theo bảng 0,1 / 0,25 / 0,5 / 1. |
| VACT | ĐHQG TP.HCM, đợt 2 năm 2026, thi trên giấy | 150 phút, 1.200 điểm theo IRT: 4 thành phần, mỗi thành phần 300 (Tiếng Việt, Tiếng Anh, Toán, Tư duy khoa học) | 120 câu: Ngôn ngữ 60 (Tiếng Việt, Tiếng Anh), Toán 30, Tư duy khoa học 30 | Chọn một 4 phương án, câu đơn và chùm câu (dữ kiện, biểu đồ, ngữ liệu liên môn). |

Thay đổi trong dữ liệu mẫu sau khi đối chiếu:

- Tên kỳ thi: SP2E là ĐH Sư phạm Hà Nội 2 (trước ghi nhầm ĐHSP TP.HCM); BCA là "Đánh giá tuyển sinh Công an nhân dân".
- Tên phần của mẫu HSA_FULL và QDA_FULL theo bảng.
- BCA:
  - Khối tự luận đổi tên thành "Nghị luận xã hội (tối thiểu 500 chữ)".
  - Phần 2 tính điểm theo bảng: Toán 35 × 1, Lịch sử 10 × 1, Tiếng Anh 20 × 0,75, cộng 60 điểm. Hết chỗ "tạm" 60 điểm cho 65 câu.
  - Bài mẫu `ex-bca-f-001` có 5 câu Trả lời ngắn trong môn Toán. Bảng không ghi Toán có bao nhiêu câu trả lời ngắn, nên mẫu cấu trúc vẫn để 35 câu chọn một và ghi chú chờ xác nhận.
- V-ACT:
  - Thang của kỳ thi và mẫu là 1.200.
  - Điểm phần 600 / 300 / 300.
  - Bài luyện tập tạm tính 10 điểm mỗi câu tới khi có IRT.
  - Bài mẫu `ex-vact-f-001` vẫn để trống thang điểm, để giữ ví dụ lỗi "thiếu thang điểm" trong kiểm tra cấu hình.

Chưa đưa vào dữ liệu mẫu:

- Câu Ghép đôi của SP2E. Dự kiến dùng dạng Kéo thả (`DRAG_DROP`), chờ xác nhận.
- Số chữ tối thiểu của câu tự luận mới là thông tin trong đề bài, chưa thành ràng buộc khi chấm.

## 2. Quyết định (M.Trường, 2026-11-16)

1. **Tự luận chấm tay.** Câu Tự luận có điểm tối đa và tiêu chí chấm (rubric). Phần trắc nghiệm được chấm ngay khi nộp. Phần tự luận hiện "Đang chấm" tới khi người chấm xong; khi đó lượt thi mới có điểm. Admin có màn hàng chờ chấm.
2. **Đúng/Sai nhiều ý chấm theo bảng điểm.** Điểm tính theo số ý đúng. Mặc định THPTQG, 4 ý: đúng 1 ý được 0,1; 2 ý 0,25; 3 ý 0,5; 4 ý 1 điểm. Bảng mặc định lấy từ mẫu của kỳ thi và sửa được.
3. **Nhiều đáp án đúng có hai quy tắc.** "Đúng hết mới có điểm" (mặc định), hoặc "Chấm từng phần": điểm theo tỉ lệ phương án đúng đã chọn, chọn sai một phương án là 0 điểm.
4. **Quy tắc chấm lưu ở câu hỏi.**
   - Kỳ thi (`examKinds[].scoringDefaults`) cho giá trị mặc định khi soạn câu.
   - Khối câu hỏi của bài thi giữ quy tắc mong đợi (`QuestionBlock.expectedScoring`).
   - Kiểm tra cấu hình báo các câu trong nguồn của khối có quy tắc khác quy tắc mong đợi.

## 3. Mô hình (nền UNI-2600)

Tất cả là kiểu TypeScript và JSON mẫu của web admin. Khi có backend, các kiểu này thành bí danh của OpenAPI.

### 3.1 Danh mục (`features/content`)

- `ContentReference.examKinds: ExamKindOption[]` có 7 mã: HSA, QDA, BCA, VACT, SPT, SP2E, THPTQG. Mỗi kỳ thi có:
  - `formats`: hình thức được phép. HSA, QDA có FULL và SINGLE; các kỳ thi khác chỉ có FULL.
  - `templateBy`: `KIND` (một mẫu cho cả kỳ thi) hoặc `SUBJECT` (mẫu theo môn và năm: SPT, SP2E, THPTQG).
  - `timing`: `PER_PART` hoặc `SHARED`.
  - `maxScore`: thang điểm.
  - `scoringDefaults`: `trueFalseTable` và `multipleChoiceRule`.
- `ExamKind` là `string` (mã trong danh mục). `EXAM_KINDS` liệt kê 7 mã của dữ liệu mẫu. Không còn so `kind === "HSA"` để suy ra quy tắc; quy tắc đọc từ danh mục.
- Môn bổ sung: `VIETNAMESE` (Tiếng Việt), `ECON_LAW` (GDKT&PL), `INFORMATICS`, `TECH_INDUSTRY`, `TECH_AGRICULTURE`, `SCIENTIFIC_THINKING` (Tư duy khoa học, V-ACT).
- Lớp bổ sung: `GRADE_MIXED` (Liên lớp).
- Tổ hợp có `examKindCode`. Mã tổ hợp theo kỳ thi:
  - HSA: KH-1..3.
  - QDA: QD01..QD04, tên hiển thị "QĐ01 · …". Đây là các tổ hợp KH-4, KH-5, KH-6 cũ đổi mã, thêm QD03 Lí+Sinh. QĐ05 là nhánh Tiếng Anh (`englishOption`), không phải tổ hợp.
  - BCA: CA1..CA4, mỗi mã một môn.
  - `combinationsForKind` lọc theo `examKindCode`.
- `competencies: CompetencyOption[]`: năng lực của V-ACT (`VACT-NN-TV`, `VACT-NN-TA`, `VACT-TOAN`, `VACT-TD-LOGIC`, `VACT-TD-SL`).

### 3.2 Bài thi (`features/exams/types.ts`)

- `ExamSummary` có thêm:
  - `subjectCode`: môn của bài, với kỳ thi có mẫu theo môn.
  - `timing`.
  - `maxScore`.
  - `hasEssay`.
- `ExamDetail` có thêm `structureYear`, `matrixYear`, `yearsConfirmed`. Hai năm khác nhau mà chưa xác nhận thì kiểm tra cấu hình chặn công bố.
- `ExamPart`:
  - `durationMinutes: number | null`. `null` khi bài dùng thời lượng chung; khi đó thời lượng chung là `ExamSummary.durationMinutes`.
  - `points?: number | null`: điểm của phần (BCA 25/60/15).
  - Phần `CHOICE` có `sections` không rỗng: các môn đó là phần chung, cộng thêm một lựa chọn (THPTQG Tin học: 2 câu đúng/sai chung cộng 2 câu của nhánh).
- `ExamPartOption`:
  - `kind` có thêm `BRANCH`, kèm `name` (nhánh Khoa học máy tính, Tin học ứng dụng).
  - `expertConfirmed?: boolean`. `false` nghĩa là số câu từng môn chờ chuyên gia xác nhận (HSA 17–17–16).
- `QuestionBlock.expectedScoring`: `trueFalseTable` và `multipleChoiceRule`.
- `KnowledgeScope.competencyCodes?` và `BlockMatrix.byCompetency?`: lọc và chia ma trận theo năng lực.
- `ExamTemplate`:
  - Có thêm `subjectCode`, `structureYear`, `matrixYear`, `timing`, `durationMinutes`, `maxScore`, `expertNote`.
  - `parts[]` có thêm `points`, `sections` (phần nhiều môn hoặc phần chung), `options` (lựa chọn gắn sẵn, kèm `subjectCounts` và `expertConfirmed`) và `blocks` (khối gợi ý theo dạng câu, gồm điểm mỗi câu, năng lực và quy tắc chấm mong đợi).
- Mẫu có sẵn trong `exams.json` (20 mẫu):
  - HSA FULL, HSA bài lẻ, QDA FULL.
  - BCA_FULL, VACT_FULL.
  - SPT_MATH_2025, SP2E_MATH_2025, SP2E_MATH_2026 (cấu trúc 2026, ma trận 2025).
  - THPTQG 2025: 12 môn. Tin học có hai phần và nhánh.
- `CreateExamRequest` và `UpdateExamInfoRequest` có thêm `subjectCode`, `durationMinutes` (thời lượng chung) và `maxScore`. `UpdateExamInfoRequest` có thêm `yearsConfirmed`.

### 3.3 Câu hỏi (`features/questions/types.ts`)

- `QUESTION_TYPES` thêm `ESSAY`. Câu con của câu chùm được là Tự luận, dùng cho đọc hiểu Ngữ văn.
- `QuestionBody`:
  - `MULTIPLE_CHOICE.scoringRule`: `ALL_OR_NOTHING` hoặc `PARTIAL`.
  - `TRUE_FALSE.scoreTable: number[]`: phần tử thứ i là điểm khi đúng i + 1 ý. Độ dài bằng số ý; phần tử cuối bằng điểm của câu.
  - `ESSAY`: `maxPoints` (bằng `points` của câu), `rubric[]` (mỗi tiêu chí có id, mô tả, điểm; tổng điểm bằng `maxPoints`), `sampleAnswer` và `wordLimit`.
- `QuestionSummary.competencyCode?`.
- Biểu mẫu soạn (`editor-form.ts`):
  - `EditorBody` có thêm `scoringRule`, `scoreTable`, `rubric`, `sampleAnswer` và `wordLimit`.
  - `editorToBody(type, body, content, points)`: câu Đúng/Sai không có bảng điểm đủ dòng thì chia đều theo `evenScoreTable`.

### 3.4 Những gì nền chưa làm

Nền chỉ sửa đủ để mã biên dịch và test cũ chạy. Giao diện đầy đủ thuộc các nhánh dưới đây.

- Các chỗ hiển thị thời lượng phần đang dùng `durationMinutes ?? 0`.
- Ô chọn kỳ thi ở trang Tạo bài thi và bộ lọc danh sách vẫn chỉ có HSA và QDA.
- `templateFor` lấy mẫu đầu tiên theo kỳ thi và hình thức, chưa chọn theo môn và năm.
- `validateBody` chưa kiểm tra câu Tự luận.
- Chưa có giao diện soạn rubric, bảng điểm đúng/sai và quy tắc nhiều đáp án.

## 4. Các nhánh

Bốn nhánh chạy song song, mỗi nhánh một worktree tách từ commit nền. Mỗi nhánh chỉ sửa tệp và nhóm thông điệp của mình. Kiểu dùng chung chỉ được thêm, không đổi tên hay xóa.

### Lane G · UNI-2601 · Tạo bài thi, danh sách, Thông tin chung

- **Tệp:** `create-exam-*.tsx`, `create-exam-schema.ts`, `exam-list-*.tsx`, `exam-general-view.tsx`, phần tạo và sửa thông tin trong `exams/api.ts` (`createExam`, `updateExamInfo`, `suggestExamCode`, `buildParts`, `partsFromInput`, `assertScope`, `assertInputParts`), test `list-create-*`.
- **Thông điệp:** `exams.create`, `exams.list`, `exams.general`, `exams.kinds`.
- **Trang `/exams/new`:**
  - Chọn kỳ thi trong 7 mã lấy từ danh mục. Hình thức theo `formats` của kỳ thi.
  - Kỳ thi `templateBy = SUBJECT`: chọn môn rồi chọn mẫu theo năm. Hiện năm cấu trúc và năm ma trận; hai năm khác nhau thì hiện ô xác nhận chuyên môn (`yearsConfirmed`). Môn chưa có mẫu thì tạo cấu trúc trống.
  - Hiện `expertNote` của mẫu.
  - Khối "Thời gian và thang điểm":
    - Kỳ thi có thời lượng chung: một ô thời lượng chung, các phần không có ô thời lượng.
    - Điểm của phần (BCA).
    - Thang điểm; V-ACT để trống được.
  - Nạp mẫu nhiều môn: phần nhiều môn (BCA Phần 2), phần chung kèm nhánh (Tin học), tổ hợp một môn (CA1–CA4).
  - `initialParts` dựng phần, môn và khối gợi ý từ mẫu. Khối có `expectedScoring` và `competencyCode` lấy từ mẫu.
  - Thay ô chọn ảnh bìa gốc của trình duyệt (chữ tiếng Anh) bằng nút chọn tệp của kit.
- **Danh sách `/exams`:**
  - Lọc theo 7 kỳ thi và theo môn (`ListExamsQuery.subjectCode`).
  - Cột lựa chọn hiển thị chung: tổ hợp, nhánh hoặc môn.
  - Cột thời lượng ghi "chung" khi `timing = SHARED`.
  - Huy hiệu "Có tự luận".
  - Mã gợi ý theo kỳ thi và môn, ví dụ `THPTQG-F-TOAN-001`.
- **Thông tin chung:** sửa được các trường mới. Đổi phạm vi thì nạp lại mẫu, như hiện nay.

### Lane H · UNI-2602 · Cấu trúc, ma trận, kiểm tra, xem trước

- **Tệp:** `exam-structure-*.tsx`, `block-*.tsx`, `combination-options.tsx`, `matrix.ts`, `check.ts`, `review-*.ts(x)`, `exam-preview-*.tsx`, `exam-versions-*.tsx`, `updateExamStructure` và `getAvailableQuestionCounts` trong `exams/api.ts`, test `structure-*`, `check-exam`, `matrix-logic`, `review-*`, `versions-*`.
- **Thông điệp:** `exams.structure`, `exams.review`, `exams.versions`, `exams.preview`.
- **Thời lượng và điểm:**
  - Bài dùng thời lượng chung: phần không có ô thời lượng; tổng thời lượng là thời lượng chung.
  - Điểm theo phần: tổng điểm các khối của phần phải khớp `points`, nếu không thì báo lỗi.
- **Phần tự chọn:**
  - Phần `CHOICE` có phần chung: soạn môn và khối chung, rồi từng nhánh.
  - Đường lựa chọn = phần chung cộng một nhánh. Số câu học viên làm là 28 dù đề in 30.
  - Sinh tổ hợp "chọn k trong n môn" cho HSA (5 môn chọn 3 → 10 tổ hợp), cho phép bỏ bớt.
  - `expertConfirmed`: hiện cờ "Chờ chuyên gia xác nhận" và nút xác nhận. Kiểm tra cấu hình chặn công bố khi còn lựa chọn chưa xác nhận.
- **Ma trận:**
  - Chiều năng lực `byCompetency` cho V-ACT.
  - Lớp "Liên lớp".
- **Khối:**
  - Hiện quy tắc chấm mong đợi và cho sửa.
  - Kiểm tra cấu hình báo câu nguồn có quy tắc khác khối (bảng điểm đúng/sai, quy tắc nhiều đáp án).
  - Khối Tự luận: báo khi câu nguồn thiếu tiêu chí chấm.
- **`check.ts`:**
  - Năm cấu trúc khác năm ma trận mà chưa xác nhận thì chặn công bố.
  - Tổng điểm khớp `maxScore` khi kỳ thi có thang.
  - Xem trước hiện điểm theo phần và nhãn "Chấm tay" ở khối Tự luận.
- **Bài mẫu trong `exams.json`:**
  - BCA đủ 3 phần (Nháp, đã có khối).
  - V-ACT (khối theo năng lực).
  - THPTQG Tin học (phần chung và hai nhánh).
  - Thêm câu mẫu cần cho nguồn vào `questions.json`, gồm câu Tự luận và câu đúng/sai 4 ý.

### Lane I · UNI-2603 · Ngân hàng câu hỏi

- **Tệp:** `features/questions/**` trừ `types.ts` (chỉ được thêm), `features/question-import/**`.
- **Thông điệp:** `questions.*`, `questionImport.*`, `content.*`.
- **Soạn câu Tự luận:**
  - Điểm tối đa là ô Điểm của câu.
  - Tiêu chí chấm (thêm, xóa, sắp xếp; tổng điểm phải bằng điểm của câu).
  - Bài làm mẫu, số chữ gợi ý.
  - Dùng được cho câu đơn và câu con của câu chùm.
- **Xem chi tiết và lịch sử phiên bản:** hiện tiêu chí chấm và nhãn "Chấm tay".
- **Đúng/Sai:**
  - Bảng điểm theo số ý đúng.
  - Nút "Theo mẫu kỳ thi…" nạp bảng của kỳ thi đã chọn (THPTQG 0,1/0,25/0,5/1).
  - Số ý đổi thì bảng đổi theo. Phần tử cuối phải bằng điểm của câu, các giá trị không giảm.
- **Nhiều đáp án:** chọn quy tắc "Đúng hết mới có điểm" hoặc "Chấm từng phần", kèm giải thích ngắn.
- **Năng lực:**
  - Trường năng lực ở phần phân loại.
  - Bộ lọc năng lực và lọc dạng Tự luận ở danh sách.
- **Kiểm tra sẵn sàng (`readiness.ts`):**
  - Câu Tự luận cần ít nhất một tiêu chí, tổng điểm tiêu chí bằng điểm của câu.
  - Câu đúng/sai cần bảng điểm hợp lệ.
- **Nhập từ tệp:**
  - Nhận dạng câu Tự luận, ánh xạ cột tiêu chí chấm hoặc để trống (câu về Nháp).
  - Thêm bí danh vào `import-dictionary.json`.

### Lane J · UNI-2604 · Hàng chờ chấm tự luận

- **Tệp:** thư mục mới `features/grading/**`, route `app/(app)/grading/**`, một mục điều hướng `mockOnly` trong `lib/navigation.ts`.
- **Thông điệp:** `grading.*`, `nav.grading`.
- **Dữ liệu mẫu riêng (`features/grading/mock/*.json`):**
  - Lượt thi có câu Tự luận: học viên, bài thi, kỳ thi, thời điểm nộp.
  - Điểm phần trắc nghiệm đã chấm.
  - Bài viết của học viên.
  - Câu hỏi kèm tiêu chí chấm, lấy từ `questionStore` khi trùng id.
- **Màn `/grading`:**
  - Danh sách lượt chờ chấm và đã chấm.
  - Lọc theo kỳ thi, bài thi và người chấm.
  - Trạng thái lượt thi: "Đang chấm" hoặc "Đã có điểm".
- **Màn chấm `/grading/[attemptId]`:**
  - Đề và bài viết bên trái, tiêu chí chấm bên phải.
  - Nhập điểm từng tiêu chí, không vượt điểm tối đa.
  - Nhận xét.
  - Lưu nháp, hoàn tất chấm. Hoàn tất xong thì lượt có tổng điểm.
- **Quyền:** chưa có mã quyền chấm. Dùng chung điều kiện hiện mục menu như các màn mockup khác, và ghi vào báo cáo.

## 5. Ghi chú cho backend sau này

- Quy tắc chấm nằm trong `QuestionBody` nên đi cùng phiên bản câu hỏi; đổi quy tắc tạo phiên bản mới như đổi đáp án.
- Lượt thi có câu Tự luận cần trạng thái riêng (`GRADING`, `SCORED`) và bảng điểm tiêu chí theo người chấm.
- Kỳ thi, tổ hợp và năng lực là danh mục dùng chung (PBI-02), backend trả qua API lựa chọn của danh mục.
