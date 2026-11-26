# Biểu mẫu thiết lập theo môn (bước Cấu trúc của bài thi)

> **Ngày:** 2026-11-19 · **Gọi tắt:** FTM · **Phiên bản:** 4 (sửa theo review vòng 3 và đổi phạm vi UNI-1704) · **Tác giả:** Advisor đợt 3 · **Người review:** Codex (vòng 1, 2, 3)
>
> **Issue:** thay đổi yêu cầu trong đợt 3, issue cha UNI-2702. UNI-2758 (uniworkos UNI-1704) **không** thuộc FTM từ bản 4: lane HSA-D làm theo bản BA 2026-11-19 23:52 (AD-67, AD-68); MT của HSA nhúng thành phần của HSA-D (R-5). Đi sau UNI-2759 (lane CL, khóa mã danh mục, đã merge `b4e575c4`).
>
> **Quan hệ với tài liệu khác:** FTM thay cách **hiển thị** bước Cấu trúc ở `docs/specs/2026-11-17-pbi04-dot-2b.md` (2B) mục 7 cho các môn có biểu mẫu riêng. Mô hình dữ liệu, luật kiểm tra và bộ sinh của 2B giữ nguyên, trừ luật mới ở R-12. Chỗ nào FTM nói khác 2B về giao diện thì theo FTM. Riêng K9/H15 của 2B bị thay ở biểu mẫu theo môn (D-6, phạm vi theo G1-1: A); biểu mẫu mặc định giữ K9.
>
> **Trạng thái:** G1 đạt ngày 2026-11-19 (mục 12, Quyết định G1); đặc tả bản 4 đã cập nhật theo đó. Review vòng 1, 2, 3 xong. **Chưa có G2 thì không mở lane, không viết code**; trước đó chỉ đọc nguồn.
>
> **Đánh số AC:** AC-1 … AC-42 giữ số của phiên bản 1 (plan và review trỏ vào). AC thêm ở vòng 1 đánh tiếp từ AC-43 và đặt trong yêu cầu liên quan. Bản 4 viết lại AC-12 … AC-17 (R-5) và bỏ AC-43; không dùng lại số đã bỏ.

## 1. Tóm tắt

Hôm nay mọi môn của mọi kỳ thi dùng chung một màn môn đầy đủ: chỉ tiêu dạng trả lời, ma trận lớp và độ khó, phạm vi kiến thức, chỉ tiêu chủ đề, danh sách nhóm lấy câu. Quản trị viên thấy nhiều trường mà kỳ thi của mình không dùng. Sau FTM, mỗi cặp **kỳ thi + môn** của bài thi chuẩn mở đúng biểu mẫu mà tài liệu BA của kỳ đó mô tả. Có hai dạng chính: **Ma trận môn** (Toán, Khoa học của HSA, QDA, BCA; Toán, Tiếng Việt của V-ACT) và **Danh sách dòng lấy câu** (các môn còn lại). Mã kỳ thi và mã môn là căn cứ chọn biểu mẫu; mã này đã cố định từ lane CL. Giá trị danh mục admin thêm sau, bài ôn, và bài đã lưu mà biểu mẫu gọn không biểu diễn được đầy đủ vẫn dùng màn hiện tại.

M.Trường: *"phần thiết lập đề đang phức tạp quá… form theo từng môn này đang bị chung"*, *"dọn các field k cần thiết đi nhé. UX simple đi. đừng phức tạp quá"*.

## 2. Nguồn

| Nguồn | Bản đọc | Ghi chú |
|---|---|---|
| Tài liệu BA từng kỳ: `docs/design/dot-2/uninote-pbi04-cau-truc-{hsa,qda,bca,vact,sp2e-spt,thpt,hsca,vsat}.md` | Bản trong repo, BA cập nhật 2026-11-17 và 2026-11-18 | Gọi tắt HSA, QDA, BCA, VACT, SP, THPT, HSCA, VSAT kèm số mục |
| Tài liệu chung `docs/design/dot-2/uninote-pbi04-thiet-lap-bai-thi.md` | 2026-11-17 | Gọi tắt CHUNG; §7.1, AC-04.4, AC-04.5 |
| Đặc tả 2B và phần bổ sung: `docs/specs/2026-11-17-pbi04-dot-2b.md`, `…-yeu-cau.md`, `2026-11-18-pbi04-dot-2b-bo-sung-{hsca-vsat,vact-thpt}.md` | Trên `integration/dot-3` | K, H, R- là mã của 2B; 2B-HV, 2B-VT là hai phần bổ sung |
| Lane CL: hợp đồng `.thi360-dev/dot-3/contracts/CL.md` (ngoài repo), commit `b4e575c4`, migration `V202611191822__catalog_system_values.sql` | Trên `integration/dot-3` | Gọi tắt CL; quy tắc sửa trường của giá trị hệ thống (mã bất biến; tên, mô tả, thứ tự sửa được) |
| uniworkos UNI-1704 bản BA viết lại | 2026-11-19 23:52 | Bản chép: `.thi360-dev/dot-3/uniai/hsa/1704-v2.md` (bản cũ `1704.md` chỉ để đối chiếu) |
| Phân tích khoảng cách | 2026-11-19 | `.thi360-dev/dot-3/cr-form-theo-mon/gap-analysis.md` (ngoài repo) |
| Pha 0 v2 của HSA-D | 2026-11-20 | `reports/HSA-D/phase0.md` trên máy cty (ngoài repo); duyệt ở AD-68, Q1–Q5 theo đề xuất |
| Câu trả lời của M.Trường | 2026-11-19 | `.thi360-dev/dot-3/cr-form-theo-mon/answers.md`, AD-34, AD-35, AD-61, AD-61a, AD-67 (G1) |
| Review vòng 1, 2, 3 | 2026-11-19, 2026-11-20 | `.thi360-dev/dot-3/review-ftm/codex-round{1,2,3}.md`; xử lý ở `fix-round{1,2,3}.md` |

## 3. Phạm vi

- **Trong phạm vi:**
  - bảng đăng ký biểu mẫu theo kỳ + môn, và quy tắc khớp biểu mẫu với cấu trúc đã lưu;
  - biểu mẫu Ma trận môn; với HSA, nhúng nguyên trạng bảng và hộp "Quy tắc MAPID" của lane HSA-D (R-5);
  - biểu mẫu Danh sách dòng lấy câu và màn sửa dòng gọn;
  - điều hướng tới môn và dòng (`?block=`, bấm lỗi, bấm tóm tắt) theo biểu mẫu đang dùng;
  - luật máy chủ cho giới hạn chùm và câu điền của môn Khoa học HSA, QDA ở bài thi chuẩn, và việc kết quả kiểm tra cũ không dùng được để công bố sau khi có luật;
  - dọn các trường không dùng khỏi biểu mẫu theo môn;
  - cập nhật `DESIGN.md`, BE spec (mã kiểm tra mới).
- **Ngoài phạm vi:**
  - đổi lược đồ, migration dữ liệu bài thi hoặc mẫu; đổi hình dạng request/response của API;
  - màn phần (`exam-structure-part-panel.tsx`), khung tổng quan Khoa học HSA, danh sách môn theo mã QDA/BCA, nhánh Tin học, tổng quan bài: giữ nguyên;
  - bài ôn (`buildMode = REVIEW`, màn `ReviewSubjectPanel`): giữ nguyên, không xét bảng đăng ký, không chịu luật R-12;
  - bước 1, tab Thông tin chung, bước Kiểm tra, Xem trước (HSA-A, HSA-E đã sửa);
  - quy tắc MAPID của HSA (dữ liệu, chuyển đổi bài cũ, bộ sinh, luật kiểm tra, bảng và hộp quy tắc): lane HSA-D, UNI-2758 (AD-67, AD-68);
  - bảng kết hợp Lớp × Độ khó ở cấp toàn môn của môn V-SAT nhiều phần (dữ liệu chưa có chỗ lưu, G1-7: A);
  - mâu thuẫn 70–80% lớp 12 của H-SCA (HSCA §4.2): giữ H46, không đổi;
  - đổi danh mục: FTM chỉ đọc mã, không thêm yêu cầu cho lane CL (D-1).
- **Không được đổi:**
  - dữ liệu đã lưu của bài và mẫu: mở bằng biểu mẫu gọn rồi lưu mà không sửa gì thì tài liệu cấu trúc gửi lên giống bản đã nạp (so theo R-2, AC-6). Ngoại lệ duy nhất là chuẩn hóa HSA-C đã có từ trước (R-2 mục "Ngoại lệ HSA-C"); FTM không thêm chuẩn hóa mới nào;
  - biểu mẫu mặc định: hành vi như hôm nay;
  - bộ sinh, `PaperSolver`, `TrimPolicy`, `PaperOrdering` và mọi luật kiểm tra hiện có (kể cả luật tổng của chiều ma trận đã khai, R-8).

## 4. Hiện trạng

Trên `integration/dot-3` `f5df9654`:

- **Chọn màn môn:** `apps/web-admin/src/features/exams/components/exam-structure-view.tsx` vẽ `ReviewSubjectPanel` khi `rendersReviewPanel(section, reviewMode)` (`allocation.ts`), còn lại vẽ `ExamStructureSubjectPanel` cho mọi kỳ và mọi môn. Mã kỳ đã có ở `exam.kind`; `ExamDetail.buildMode` đã ánh xạ ở `api-mapping.ts`.
- **Điều hướng:** `exam-structure-view.tsx` nhận `?block=`, bấm lỗi kiểm tra và bấm tóm tắt; các đường này mở thẳng `BlockEditor` của màn nhóm.
- **Lưu:** `exam-structure-view.tsx` gom Lưu, Lưu và chuyển, tự lưu trước Kiểm tra / Tiếp tục (`runSave`); lỗi tỷ lệ tạm nằm trong editor đang mở.
- **Màn môn hiện tại** (`exam-structure-subject-panel.tsx`, 744 dòng) có, theo thứ tự:
  - đầu môn: tên, nhóm Tự nhiên/Xã hội, trạng thái Đã phân bổ/Thiếu/Vượt, số câu của mục chỉ đọc kèm "Sửa thông tin tổng quan" (HSA-B);
  - thông báo đổi tổng (`TotalChangeNotice`);
  - khối **Chỉ tiêu cấp môn**: dạng trả lời (bảng chỉ tiêu, cách nhập, thêm dạng; có hàng Bộ câu hỏi của HSA-B), khối lớp và độ khó (`BlockMatrixEditor`: kết hợp hay riêng, số câu hay tỷ lệ, `EnforcementToggle`, đơn vị đếm), bảng Đã phân bổ theo lớp và độ khó;
  - `KnowledgeScopeField` (Toàn môn / Theo chủ đề), `ReferenceDistributions`, `TopicQuotaTable` (Chỉ tiêu chủ đề);
  - danh sách **Nhóm lấy câu** (`GroupList`) và nút Thêm nhóm; bấm nhóm mở `block-editor.tsx` (762 dòng).
- **Dấu hiệu khóa theo mã kỳ đã có:** `STRAND_KINDS = ["SP2E", "SPT"]` (`block-editor.tsx`), `isHsaExam`, `HsaExamContext`, `useHsaExam`, `useHsaNormalizedDraft` (`hsa-enforcement.ts`, HSA-C).
- **Mẫu:** khóa theo `kind_code` + `scope` + `part_code` + `subject_code`. Cờ mẫu `fixed_counts` (H27), `fixed_block_counts` (H42), `omitted_locked` (H43, H49), `publishable`; cờ từng nhóm `answerTypeLocked`, `shuffleEditable`, `omitted` có trong tài liệu cấu trúc.
- **Danh mục:** 52 giá trị hệ thống của 6 nhóm cố định, gồm 9 mã kỳ và 18 mã môn (`V202611191822`, CL). Bốn cặp môn trùng nghĩa giữ cả hai mã (AD-35): `TIN`/`INFORMATICS`, `GDKTPL`/`ECON_LAW`, `CNCN`/`TECH_INDUSTRY`, `CNNN`/`TECH_AGRICULTURE`. Mẫu dùng mã thứ hai của mỗi cặp. Migration CL giữ mã cũ đang dùng, đánh dấu system và báo; `system = true` không có nghĩa mã nằm trong bảng đăng ký.
- **Máy chủ:** `ExamCheckRules` không có luật nào theo mã kỳ hay môn; luật tổng của chiều ma trận `REQUIRED` đã khai (`values`) đòi tổng bằng mục tiêu. `ExamPublishService` đọc kết quả kiểm tra đã lưu và hỏi `ExamCheckValidity` (cấu trúc, cấu hình, nguồn đổi chưa), không chạy lại luật. `EffectiveKnowledgeScope` cho phạm vi riêng của nhóm ưu tiên hơn phạm vi môn. `TrimPolicy` đã giữ câu điền khi bỏ câu (`preserveFill`).
- **Tài liệu cấu trúc:** một `jsonb`, ghi qua `PUT /api/v1/admin/exams/{id}/structure`. Mọi trường biểu mẫu mới cần (nhóm, ma trận mục và nhóm, `topicQuotas`, `knowledgeScope`, `answerQuotas`, `tagCodes`, `slots`, `childConditions`) đã có. V-SAT lưu mỗi phần của môn thành một mục riêng (cùng `subjectCode`); chỉ tiêu cấp bài (`examQuotas`) chỉ có một chiều.

## 5. Thuật ngữ

| Thuật ngữ | Nghĩa ở đây |
|---|---|
| Cặp | Một mã kỳ + một mã môn canonical, ví dụ `HSA` + `TOAN`. Có 75 cặp trong 9 kỳ (R-1). Con số này không đếm bí danh, phiên bản mẫu, hai mục Logic/Suy luận cùng mã của V-ACT hay mẫu bài ôn; các chiều đó kiểm riêng |
| Biểu mẫu | Cách màn môn hiện ra: **MT** Ma trận môn, **DS** Danh sách dòng lấy câu, **ON** Bài ôn (màn hiện có), **MD** Mặc định (màn môn hiện tại) |
| Bảng đăng ký | Bảng trong mã web, khóa bằng cặp, cho biết biểu mẫu và cấu hình của cặp (R-1) |
| Khớp | Cấu trúc đã lưu của môn biểu diễn được bằng biểu mẫu của cặp mà không mất hay đổi nghĩa dữ liệu (R-2) |
| Vai trò nhóm | Ở MT: nhóm câu đơn, nhóm câu chùm, nhóm điền đáp án (R-2) |
| Dòng | Một nhóm lấy câu (`QuestionBlock`) hiện trong DS |
| Thêm điều kiện | Menu mở thêm điều kiện tùy chọn của một dòng (DS), của dòng Câu chùm (MT) hoặc của khối Phân bổ đối chiếu |
| Phân bổ đối chiếu | Khối gấp sẵn ở cuối DS, chứa phân bố lớp, độ khó, mạch của mục |
| Mức áp dụng | Bắt buộc (`REQUIRED`) hay Tham khảo/Chỉ đối chiếu (`REFERENCE`) của một phân bố |
| Đơn vị đếm | Câu (`QUESTION`), lệnh hỏi (`PROMPT`), điểm (`POINTS`) |

## 6. Yêu cầu

### R-1 Bảng đăng ký biểu mẫu

Mỗi cặp có đúng một mục trong bảng đăng ký. Mục cho biết biểu mẫu và các cấu hình ở bảng dưới.

**Thứ tự chọn** (dùng ở cả web và R-12):

1. Bài ôn (`buildMode = REVIEW`) → ON nếu `rendersReviewPanel` đúng, ngược lại MD. Không xét bảng đăng ký, không thử MT/DS, kể cả bài ôn của kỳ admin thêm sau hoặc bài ôn có dữ liệu cũ nhiều nhóm.
2. Bài chuẩn (`FULL`, `PART`, `SUBJECT`): tra cặp. Không có mục → MD, không dòng chữ.
3. Có mục mà dạng đó đang tắt (cờ triển khai) → MD, không dòng chữ.
4. Có mục, dạng đang bật, cấu trúc không khớp (R-2) → MD kèm dòng chữ.
5. Còn lại → dạng của mục.

**Mã canonical theo kỳ** (nguồn: bảng kỳ/môn của BA, mẫu đang hoạt động, gap-analysis mục 1):

| Kỳ | Mã môn | Số cặp | MT | DS |
|---|---|---|---|---|
| `HSA` | `TOAN`, `VAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA`, `ANH` | 8 | `TOAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA` | `VAN` (5 nhóm, nhóm 5 có điều kiện từng chùm và công tắc đảo), `ANH` (11 dòng cố định) |
| `QDA` | `TOAN`, `VAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA`, `ANH` | 8 | `TOAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA` | `VAN` (5 dòng theo thứ tự; dòng 5 là một nhóm 5 chùm × 5, mỗi chùm có nhãn, một công tắc đảo), `ANH` (10 dòng) |
| `BCA` | `VAN`, `TOAN`, `SU`, `ANH`, `LY`, `HOA`, `SINH`, `DIA` | 8 | `TOAN`, `SU`, `LY`, `HOA`, `SINH`, `DIA` | `VAN` (1 dòng tự luận), `ANH` (5 dòng) |
| `VACT` | `VIETNAMESE`, `ANH`, `TOAN`, `SCIENTIFIC_THINKING` | 4 | `TOAN`, `VIETNAMESE` (cấu trúc câu cố định + bảng mạch, R-3, R-6) | `ANH` (5 khối), `SCIENTIFIC_THINKING` (hai mục Logic, Suy luận) |
| `SP2E` | `TOAN`, `VAN`, `LY`, `HOA`, `SINH`, `DIA`, `SU`, `ECON_LAW`, `ANH` | 9 | — | cả 9 |
| `SPT` | `TOAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA`, `ECON_LAW`, `TECH_INDUSTRY`, `TECH_AGRICULTURE`, `VAN`, `ANH`, `INFORMATICS` | 12 | — | cả 12 |
| `THPTQG` | `TOAN`, `LY`, `HOA`, `SINH`, `DIA`, `SU`, `ECON_LAW`, `TECH_INDUSTRY`, `TECH_AGRICULTURE`, `INFORMATICS`, `ANH`, `VAN` | 12 | — | cả 12 (`VAN` chỉ đọc) |
| `HSCA` | `TOAN`, `LY`, `HOA`, `SINH`, `VAN`, `ANH` | 6 | — | cả 6 |
| `VSAT` | `TOAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA`, `VAN`, `ANH` | 8 | — | cả 8 |

**Bí danh:** `TIN`→`INFORMATICS`, `GDKTPL`→`ECON_LAW`, `CNCN`→`TECH_INDUSTRY`, `CNNN`→`TECH_AGRICULTURE`. Bí danh chỉ dùng để **tra** bảng đăng ký, và chỉ ở kỳ có mã canonical tương ứng trong bảng trên (SP2E: `ECON_LAW`; SPT, THPTQG: cả bốn). Bí danh không đổi dữ liệu: `subjectCode` gốc giữ nguyên trong thân `PUT`, nguồn MAPID không hợp nhất, mục cùng mã không gộp. Mã không có trong bảng, kể cả mã hệ thống cũ được CL giữ lại, dùng MD. Đổi tên hiển thị của mã không đổi biểu mẫu (biểu mẫu theo mã, không theo tên).

Cấu hình theo kỳ (chi tiết ở R-8 đến R-12):

| Kỳ | Nhập % (R-9) | Mức áp dụng (R-8) | Bảng kết hợp (R-10) | Thêm hoặc tách dòng (R-7) |
|---|---|---|---|---|
| HSA | Không | Bắt buộc, cố định (trừ ngoại lệ HSA-C) | Không | Không |
| QDA | Không | Chiều đã khai là Bắt buộc, chiều để trống không áp | Không | Không |
| BCA | Không | Tham khảo, cố định | Không | Không |
| VACT | Có, hai cột Số câu / Tỷ lệ | Chỉ đối chiếu, có nút "Áp dụng khi sinh đề" với phân bố theo câu | Không | `SCIENTIFIC_THINKING` |
| SP2E, SPT | Có | Như VACT | Không | Không |
| THPTQG | Không | Như QDA | Không | Không |
| HSCA | Có | Như VACT | Không | `ANH` |
| VSAT | Có | Như VACT | Có, cấp phần (G1-7) | Không |

- Nguồn: gap-analysis mục 1, 3; AD-34 (2), AD-35, AD-61; CHUNG §7; CL.
- Áp cho: mọi quản trị viên có quyền sửa bài; mọi trạng thái bài mà bước Cấu trúc đang cho sửa.
- Tiêu chí chấp nhận:
  - **AC-1** Với mỗi cặp ở bảng trên, bảng đăng ký trả đúng biểu mẫu; đủ 75 cặp, không thừa.
  - **AC-2** Ở SPT và THPTQG, `TIN`/`INFORMATICS`, `GDKTPL`/`ECON_LAW`, `CNCN`/`TECH_INDUSTRY`, `CNNN`/`TECH_AGRICULTURE` trả cùng một mục; ở SP2E, `GDKTPL`/`ECON_LAW` trả cùng mục. Bí danh ở kỳ không có mã canonical đó (ví dụ `HSA` + `TIN`, `SP2E` + `CNCN`) trả không có mục → MD.
  - **AC-3** Kỳ hoặc môn không có trong bảng (ví dụ kỳ `TEST_NEW` admin thêm, môn mới trong kỳ `HSA`, mã hệ thống cũ được CL giữ lại) mở MD, không lỗi.
  - **AC-4** Bài ôn mở ON (hoặc MD theo `rendersReviewPanel`) ở mọi kỳ, kể cả kỳ admin thêm sau; không bao giờ mở MT/DS.
  - **AC-55** Bài SPT dùng mã `GDKTPL` mở DS; lưu không sửa gì thì thân `PUT` vẫn là `GDKTPL`. Đổi tên hiển thị của `ECON_LAW` ở danh mục không đổi biểu mẫu của cặp.

### R-2 Khớp biểu mẫu, ổn định trong phiên, rơi về MD

**Lúc tính.** Biểu mẫu của một môn tính một lần khi mở bước Cấu trúc (hoặc khi nạp mẫu vào bài), trên bản đã nạp sau chuẩn hóa HSA-C (và chuyển đổi MAPID của HSA-D, R-5). Trong phiên sửa, biểu mẫu không đổi, kể cả khi người dùng sửa ở MD cho tới lúc cấu trúc khớp. Lần mở sau (tải lại trang, mở lại bài) tính lại theo cấu trúc đã lưu: nay khớp thì mở biểu mẫu gọn. Không có nút chuyển qua lại giữa hai biểu mẫu, không có cờ biểu mẫu trong dữ liệu.

**Không khớp** (một điều là đủ) thì vẽ MD và hiện dòng chữ: "Môn này đang dùng thiết lập đầy đủ vì có thiết lập mà biểu mẫu gọn không hiển thị được."

- Chung:
  - ma trận kiểu kết hợp (`COMBINED`) ở kỳ không có bảng kết hợp (R-10), hoặc ở cấp mà R-10 không cho;
  - nhóm có số câu để trống (`questionCount = null`, "lấy phần còn lại");
  - nhập theo tỷ lệ ở kỳ không cho nhập % (R-9);
  - đơn vị đếm khác `QUESTION` ở phân bố Bắt buộc;
  - điều kiện lớp đang Bắt buộc ở chỗ R-11 ẩn khối lớp (G1-1).
- MT (theo vai trò nhóm):
  - mỗi vai trò có nhiều nhất một nhóm: **nhóm câu đơn** (dạng trắc nghiệm, hoặc dạng để trống và chia theo chỉ tiêu dạng trả lời của môn như Toán HSA), **nhóm câu chùm** (`QUESTION_SET`), **nhóm điền đáp án** (`SHORT_ANSWER`). Hai nhóm cùng vai trò (ví dụ hai nhóm trắc nghiệm khác nguồn) → MD;
  - nhóm có dạng khác ba vai trò trên;
  - nhóm có ma trận riêng, chỉ tiêu chủ đề riêng, điều kiện vị trí hoặc điều kiện từng chùm;
  - nhóm câu đơn hoặc điền đáp án có phạm vi kiến thức riêng hoặc nhãn riêng (`tagCodes`); nhóm câu chùm được có, hiện ở "Nguồn chùm" (R-3);
  - **riêng HSA:** dữ liệu MAPID của mục và nhóm (`mapidMode`, `mapidRules`, `knowledgeScope.nodeIds`, `topicQuotas`) không phải điều kiện khớp, vì do thành phần của HSA-D hiện và ghi (R-5); hai điều kiện ngay trên chỉ xét phần còn lại (ma trận, điều kiện vị trí, từng chùm, `tagCodes`);
  - `answerQuotas` không thuộc một trong ba dạng: trống; dạng HSA-B (trắc nghiệm đơn + Bộ câu hỏi + điền); dạng cũ (trắc nghiệm gồm câu con của chùm + điền);
  - nhóm chùm **cỡ cố định** có `setCount` ≤ 0, hoặc số câu khác số chùm × cỡ chùm (dữ liệu đang có `SET_SIZE_MISMATCH`). Nhóm chùm **cỡ linh hoạt** (`setCount`, `setSize` đều `null`, H28, như V-ACT Toán) không xét điều kiện này;
  - mục **đang vi phạm giới hạn R-12 ngay khi nạp** (bài cũ lưu nháp qua API hay qua MD): mở MD, lưu nháp như trước; bước Kiểm tra báo lỗi R-12 như mọi bài.
- DS: nhóm có điều kiện mà màn sửa dòng không có mục tương ứng (R-7).

**Ngoại lệ HSA-D (bản 4).** Bài HSA cũ chưa có `mapidMode` được hàm của HSA-D chuyển sang quy tắc khi nạp và ghi ở lần lưu sau (R-5). Biểu mẫu tính sau chuyển đổi đó; AC-6 so với bản đã nạp sau HSA-C và sau chuyển đổi của HSA-D.

**Ngoại lệ HSA-C (đã có, giữ nguyên).** Với HSA, `useHsaNormalizedDraft` chuyển phân bố Tham khảo có số sang Bắt buộc khi nạp, nên lưu lại ghi thay đổi đó (E2E `exam-hsa-matrix.spec.ts:127–162` đang khẳng định). Không chuyển: chiều ma trận trống, chỉ tiêu nhiều môn (H6), phân bố lớp Tiếng Anh chờ chuyên gia (mẫu `HSA_FULL`, `HSA_PART_ENGLISH`, `HSA_SUBJECT_ANH`). FTM không mở rộng, không thu hẹp chuẩn hóa này và không ép Bắt buộc lên các ngoại lệ.

- Nguồn: gap-analysis 5.1, 5.2; review vòng 1 S1-02, S1-16; "không đổi dữ liệu đã lưu" (mục 3).
- Tiêu chí chấp nhận:
  - **AC-5** Mỗi điều kiện "không khớp" ở trên, một mình, làm môn mở MD kèm dòng chữ. Mọi mẫu chuẩn đang hoạt động của 75 cặp (không gồm mẫu bài ôn) khớp biểu mẫu của cặp.
  - **AC-6** Mở môn bằng biểu mẫu gọn rồi bấm Lưu mà không sửa gì: các trường cấu trúc của thân `PUT /structure` (so toàn bộ wire theo plan B4) bằng bản đã nạp sau chuẩn hóa HSA-C và chuyển đổi của HSA-D.
  - **AC-56** Bài mở MD vì không khớp; sửa trên MD tới khi khớp: biểu mẫu không đổi trong phiên; lưu rồi tải lại thì mở biểu mẫu gọn. Ba mẫu lớp Tiếng Anh chờ chuyên gia của HSA giữ mức Tham khảo sau khi mở và lưu.

### R-3 Ma trận môn: cấu trúc câu

MT không có bảng dạng trả lời và không có danh sách nhóm. Thay vào đó là khối **Cấu trúc câu** gồm:

- **Trắc nghiệm** (số câu, đã gồm câu con của chùm) và **Điền đáp án** (số câu). Ô chỉ hiện với vai trò mà mẫu của cặp có. Dưới khối hiện "Đã phân bổ x/y câu", thiếu hoặc vượt tô màu như HSA-B.
- **Câu chùm:** số chùm × số câu con. Cỡ chùm cố định theo mẫu (3 với HSA, QDA, BCA), chỉ sửa số chùm, **tối thiểu 1** (R-12 thêm tối đa 2 với Khoa học HSA, QDA). V-ACT Toán nhập tổng số câu con, cỡ chùm linh hoạt (H28: `setCount`/`setSize` để trống là hợp lệ).
- **Trắc nghiệm đơn** tự tính bằng Trắc nghiệm trừ câu con của chùm, chỉ đọc.
- **Nguồn chùm** (QDA, BCA, V-ACT; HSA dùng quy tắc `SOURCE` của HSA-D, R-5): mục trong "Thêm điều kiện" của dòng Câu chùm, sửa MAPID (`block.knowledgeScope`) và nhãn (`tagCodes`) của riêng nhóm câu chùm. Có sẵn giá trị thì mục hiện mở. Phạm vi môn và nguồn của nhóm câu đơn không đổi theo.

**Ô nhập và ô tự tính.** Người dùng nhập ba số: **Trắc nghiệm** T (tổng, gồm câu con của chùm), **Số chùm** k (cỡ chùm z cố định theo mẫu; V-ACT Toán nhập tổng câu con thay cho k × z), **Điền đáp án** S. Tự tính, chỉ đọc: **Trắc nghiệm đơn** = T − số câu con (k × z với chùm cỡ cố định; tổng câu con với chùm cỡ linh hoạt). Nếu T nhỏ hơn số câu con thì lỗi tại ô Trắc nghiệm ("Ít hơn số câu con của chùm") và chặn lưu. Số chùm 0 ở chùm cỡ cố định thì lỗi tại ô ("Ít nhất 1 chùm") và chặn lưu.

**Ghi khi lưu.**

- Cập nhật đúng nhóm theo `id`, giữ các số nhất quán: nhóm chùm cỡ cố định `setCount` = k, `setSize` = z, `questionCount` = k × z; nhóm chùm cỡ linh hoạt giữ `setCount`, `setSize` là `null`, `questionCount` = tổng câu con (nguồn sự thật); nhóm câu đơn `questionCount` = T − k × z; nhóm điền `questionCount` = S. Ví dụ BCA Sử (7 đơn + 1 × 3, T = 10): đổi số chùm lên 2 mà giữ T = 10 thì nhóm đơn 4, nhóm chùm 2 × 3 = 6.
- Giữ thứ tự nhóm và mọi trường MT không chỉnh (tên, `tagCodes`, `templateGroupCode`, điểm, khóa, `everyChildInScope`, …). Không đổi `id` của nhóm nào.
- **Không xóa, không tạo nhóm.** Theo vai trò:
  - nhóm câu đơn, nhóm điền: về 0 thì nhóm giữ nguyên `id` và mọi trường, `questionCount` = 0; đặt lại lớn hơn 0 thì dùng lại chính nhóm đó. Máy chủ giữ nhãn, mã mẫu, khóa theo `id` đã lưu (`ExamStructureOps.restoreBlock`), nên không mất gì;
  - nhóm chùm cỡ cố định: không về 0 ở MT (ghi `setCount` 0 thì máy chủ báo `SET_SIZE_MISMATCH`, `ExamCheckRules.java:790–803`, và `GroupQuota` không nhận chùm 0). Muốn bỏ hẳn chùm thì dùng MD như hôm nay;
  - nhóm chùm cỡ linh hoạt: tổng câu con được về 0 như nhóm câu đơn, `setCount`, `setSize` vẫn `null`.
- Bài cũ mà vai trò của mẫu đã bị xóa (không còn nhóm): MT không có ô cho vai trò đó; muốn thêm lại thì dùng MD. Các vai trò còn lại vẫn khớp nếu đủ điều kiện R-2.
- `answerQuotas` **chỉ** tính lại khi người dùng đổi một ô của Cấu trúc câu, và giữ đúng dạng đang lưu (G1-6: A): trống thì để trống; dạng HSA-B thì tính lại theo HSA-B (hàng Bộ câu hỏi đếm câu con); dạng cũ thì tính lại theo dạng cũ (câu con của chùm đếm vào trắc nghiệm). Lưu không sửa Cấu trúc câu thì `answerQuotas` giữ nguyên byte.

**V-ACT Tiếng Việt.** Cấu trúc câu là một nhóm câu đơn. Bài FULL: mục tiêu 30 câu chỉ đọc (H27 khóa tổng). Bài theo phần hoặc tiểu mục: lấy mục tiêu đã lưu, sửa được (VACT §3.1; 2B-VT §5.3, các mẫu phạm vi riêng `fixed_counts = false`).

- Nguồn: HSA §4a (dòng 65–79), QDA §4b.2, §6a, BCA §5c bước 2, §6, §6.1, VACT §3.1, §5.1; AD-61 câu 5; `V202611190100__answer_quota_question_set.sql`.
- Tiêu chí chấp nhận:
  - **AC-7** HSA Toán mặc định hiện Trắc nghiệm 35, Điền đáp án 15, Câu chùm 1 × 3, Trắc nghiệm đơn 32; sửa Điền đáp án thành 12 thì hiện "Đã phân bổ 47/50 câu, còn thiếu 3 câu" và vẫn lưu nháp được.
  - **AC-8** Bài HSA Toán từ mẫu hiện tại, sửa Trắc nghiệm rồi lưu: `answerQuotas` dạng HSA-B đúng số mới; bước Kiểm tra không báo `ANSWER_QUOTA_DUPLICATE` hay lệch dạng trả lời.
  - **AC-9** V-ACT Toán nhập tổng câu con 18 thì lưu nhóm chùm cỡ linh hoạt tổng 18, không chia đều.
  - **AC-44** (a) Môn có hai nhóm trắc nghiệm khác nguồn mở MD. (b) BCA Sử (T = 10) sửa số chùm từ 1 lên 2: nhóm chùm `setCount` 2, `questionCount` 6; nhóm đơn `questionCount` 4; `id`, `tagCodes` `SU_TU_LIEU_LICH_SU`, `templateGroupCode`, thứ tự, điểm của cả hai nhóm giữ nguyên; bước Kiểm tra không báo `SET_SIZE_MISMATCH`. (c) Bài Toán cũ lưu 35/15: sửa Điền đáp án thì `answerQuotas` vẫn dạng cũ với số mới. (d) Môn có nhóm nhưng `answerQuotas` trống: lưu sau khi sửa vẫn trống. (e) Mở rồi lưu không sửa: giống hệt (AC-6). (f) Bài HSA Sử dựng riêng có nhóm điền 1 câu (mẫu mặc định HSA Sử chỉ có 14 đơn + 1 × 3, không có nhóm điền, `V202611180652__exam_templates_ba_numbers.sql:104`): Điền 1 → 0, lưu, tải lại, 0 → 1, lưu: nhóm điền vẫn cùng `id`, nhãn, điểm, mã mẫu qua `PUT`/`GET`. (g) T nhỏ hơn số câu con: lỗi tại ô, không gửi `PUT`. (h) BCA Sử: Số chùm 0 báo lỗi tại ô, không gửi `PUT`; 1 và 2 lưu được, Kiểm tra không có `SET_SIZE_MISMATCH`. (i) V-ACT Toán từ mẫu thật mở MT; lưu không sửa và lưu sau khi đổi tổng câu con: thân `PUT` giữ `setCount`, `setSize` là `null`.
  - **AC-45** BCA Hóa: mở "Nguồn chùm", đặt một MAPID: chỉ `knowledgeScope` của nhóm chùm đổi; phạm vi môn và nhóm câu đơn không đổi.
  - **AC-50** (phần cấu trúc) V-ACT Tiếng Việt bài FULL hiện 30 chỉ đọc; bài tiểu mục 12 câu sửa được số.

### R-4 Ma trận môn: khối lớp và độ khó

MT hiện hai hàng ô số: **Khối lớp** (Lớp 10, 11, 12, và Liên lớp với V-ACT Toán) và **Độ khó** (Nhận biết, Thông hiểu, Vận dụng, Vận dụng cao) của toàn mục. Không có nút Khai báo/Bỏ, không có lựa chọn kết hợp, không có đơn vị đếm. Mỗi ô hiện số đã phân bổ và thiếu ngay dưới. Tỷ lệ % hiện bên cạnh, hoặc nhập được theo R-9. Mức áp dụng theo R-8.

- Hàng khối lớp ẩn theo R-11.
- **V-ACT Tiếng Việt:** lớp là tùy chọn (VACT §4.1). Hàng khối lớp không hiện mặc định; có mục "Khối lớp" trong "Thêm điều kiện". Bài đã có số lớp thì hàng hiện sẵn (không giấu luật đang có hiệu lực). Độ khó có hai nơi: hàng **Độ khó toàn tiểu mục** (ma trận của mục) và cột độ khó **trong từng mạch** của bảng mạch (R-6). Hai nơi lưu riêng.

- Nguồn: HSA §4a, QDA §6b, VACT §4.1, §5.1; gap-analysis 4.1.
- Tiêu chí chấp nhận:
  - **AC-10** HSA Toán hiện lớp 3/20/27 và độ khó 2/21/20/7 lấy từ mẫu, ghi ma trận `SEPARATE`, đơn vị `QUESTION`.
  - **AC-11** V-ACT Tiếng Việt không có hàng khối lớp khi chưa có số lớp; V-ACT Toán có cột Liên lớp.
  - **AC-50** (phần lớp, độ khó) V-ACT Tiếng Việt bài cũ đã có số lớp: hàng lớp hiện sẵn với số đó. Mẫu thật có 8 dòng mạch chờ mapping (2B-VT §5.2, H31): hiện "Chờ mapping", lưu nháp được.

### R-5 Ma trận môn HSA: Quy tắc MAPID (của lane HSA-D)

**Đổi ở bản 4.** BA viết lại UNI-1704 thành đặc tả quy tắc MAPID (`.thi360-dev/dot-3/uniai/hsa/1704-v2.md`, 2026-11-19 23:52). UNI-1704 (UNI-2758) ra khỏi FTM; lane HSA-D làm riêng, giao `develop` trước (AD-22) rồi forward-merge vào `integration/dot-3`; hình dạng đã duyệt ở AD-68 (pha 0 v2 của HSA-D). Thiết kế "Toàn bài / Theo ma trận MAPID" tự làm của FTM bản 1–3 (hai nhánh A/B, bảng nạp và lưu, so tập ứng viên) bỏ hẳn. FTM không làm lại, không đổi dữ liệu, luật kiểm tra, bộ sinh hay giao diện của quy tắc MAPID.

**HSA-D giao gì** (tóm tắt để đọc; nguồn chính là pha 0 v2 mục b, d, e):

- Dữ liệu trong tài liệu cấu trúc, không migration: `section.mapidMode` (`ALL` | `MATRIX`) và `section.mapidRules[]` với 4 loại `PRESENT`, `COUNT`, `ONLY_IN_GROUP`, `SOURCE`; `groupId` trỏ `QuestionBlock.id`, `null` là cả môn. Chỉ HSA; không giới hạn số quy tắc mỗi mục (bỏ mức 20 cũ theo BA, uniworkos UNI-1718, 2026-11-26, `UNI-2822`). Hộp thêm/sửa quy tắc cảnh báo, không chặn, khi tổng số câu các quy tắc cần vượt số câu của môn ở cấu trúc (uniworkos UNI-1719). Khi có `mapidMode`, máy chủ biên dịch quy tắc và bỏ qua `knowledgeScope.nodeIds`, `topicQuotas` của mục và của nhóm (năng lực và điều kiện khác giữ).
- Bài HSA cũ chưa có `mapidMode`: web của HSA-D chuyển phạm vi và chỉ tiêu cũ thành quy tắc khi nạp, không mất dữ liệu, ghi ở lần lưu sau (HSA-D AC-10).
- Giao diện: lựa chọn "Toàn bài (không giới hạn MAPID)" / "Theo ma trận MAPID"; bảng "Quy tắc MAPID" (mỗi quy tắc một câu, sửa, xóa); hộp "Thêm quy tắc" (Áp dụng cho, Loại quy tắc, MAPID, số câu hoặc %); cột "Phạm vi nguồn" của nhóm; mã `MAPID_RULE_CONFLICT` và thông báo thiếu theo quy tắc.

**FTM làm ở MT của HSA** (6 môn `TOAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA`; bài `FULL`, `PART`, `SUBJECT`):

- Khối MAPID của MT là **thành phần của HSA-D, dùng nguyên trạng**: cùng component bảng "Quy tắc MAPID", hộp "Thêm quy tắc" và hàm (`mapid-rules.ts`) mà HSA-D dùng ở MD, cùng props, cùng chữ. Không bản sao, không nhánh riêng. Cần thêm prop hay đổi gì trong thành phần đó thì gửi HSA-D (HSA-D đã đóng thì qua Advisor), không sửa tại chỗ.
- MT của HSA không hiện "Phạm vi kiến thức", "Thêm chỉ tiêu chủ đề" (R-6) hay "Nguồn chùm" (R-3); nguồn của nhóm đi qua quy tắc `SOURCE` theo nhóm.
- "Áp dụng cho" trong hộp thêm quy tắc liệt kê nhóm của môn như HSA-D hiện (loại, cỡ, đơn vị); ở MT, mỗi nhóm hiện bằng tên vai trò (Trắc nghiệm đơn, Câu chùm, Điền đáp án). "Phạm vi nguồn" của nhóm hiện dưới hàng vai trò tương ứng của Cấu trúc câu, bằng hàm của HSA-D.
- Đổi số ở Cấu trúc câu thì quy tắc theo % của nhóm tính lại bằng hàm của HSA-D, như khi đổi số câu nhóm ở MD. Nhóm câu đơn hay điền về 0 vẫn giữ `id` (R-3), nên quy tắc trỏ tới nó không mất.
- Khớp (R-2): ở HSA, dữ liệu MAPID của mục và nhóm (`mapidMode`, `mapidRules`, `knowledgeScope.nodeIds`, `topicQuotas`) không phải điều kiện khớp; biểu mẫu tính sau chuyển đổi của HSA-D. Mọi điều kiện khác của R-2 giữ.
- Lưu không sửa (AC-6): chuyển đổi bài cũ của HSA-D là ngoại lệ thứ hai bên cạnh HSA-C (R-2); FTM không thêm chuẩn hóa nào.
- Điều hướng (R-15): lỗi `MAPID_RULE_CONFLICT` và thiếu theo quy tắc mở môn MT, đặt focus vào hàng quy tắc của bảng HSA-D (theo neo HSA-D có; không có neo thì vào đầu bảng).
- Phụ thuộc: phần HSA của MT (task CR-2H của plan) chỉ mở khi HSA-D đã forward-merge vào `integration/dot-3`. Trước đó HSA dùng MD, nơi HSA-D đã gắn thành phần của mình.

- Nguồn: `uniai/hsa/1704-v2.md`; pha 0 v2 HSA-D (`reports/HSA-D/phase0.md` trên cty) mục (b), (d), (e), Q1–Q5; AD-67, AD-68.
- Tiêu chí chấp nhận (thay AC-12 … AC-17 của bản 3; AC-43 bỏ):
  - **AC-12** MT của HSA Toán hiện bảng "Quy tắc MAPID" và hộp "Thêm quy tắc" của HSA-D (cùng component với MD, kiểm bằng component và `data-testid` của HSA-D); không có Phạm vi kiến thức, Thêm chỉ tiêu chủ đề, Nguồn chùm.
  - **AC-13** Cùng thao tác trên cùng bài (thêm `COUNT` đúng 3 câu Hàm số cho nhóm Điền đáp án; thêm `SOURCE` cho nhóm Câu chùm) ở MT và ở MD cho thân `PUT` giống nhau ở `mapidMode`, `mapidRules`.
  - **AC-14** Đổi Điền đáp án ở Cấu trúc câu từ 15 sang 12: quy tắc % của nhóm đó tính lại giống MD. Đưa Điền đáp án về 0 rồi lên lại: quy tắc vẫn trỏ đúng `id`.
  - **AC-15** Bài HSA cũ có phạm vi và chỉ tiêu chủ đề mở MT (không rơi MD vì dữ liệu MAPID); lưu không sửa thì thân `PUT` khác bản đã nạp chỉ ở phần do hàm chuyển đổi của HSA-D tạo ra trên cùng bản nạp.
  - **AC-16** Bấm lỗi `MAPID_RULE_CONFLICT` ở bước Kiểm tra mở môn MT và đặt focus vào bảng quy tắc (vào hàng nếu HSA-D có neo).
  - **AC-17** Trên trình duyệt: HSA Toán ở MT, thêm quy tắc loại 2 và 5 (kịch bản AC-12 của HSA-D) rồi sinh thử: đề đạt cả hai quy tắc.
  - **AC-43** Bỏ ở bản 4: so tập ứng viên khi chuyển dữ liệu MAPID cũ thuộc HSA-D (AC-10 của HSA-D).

### R-6 Ma trận môn ngoài HSA: phạm vi và chỉ tiêu chủ đề

QDA, BCA, V-ACT giữ hai khái niệm như tài liệu của các kỳ này mô tả, xếp gọn:

- **Phạm vi kiến thức** (`KnowledgeScopeField`): Toàn môn / Theo chủ đề.
- Nút **Thêm chỉ tiêu chủ đề** (QDA §4 bước 3): bấm mới hiện bảng chỉ tiêu chủ đề. Bảng có MAPID, số câu (hoặc tỷ lệ với V-ACT), giới hạn lớp và độ khó trong "Thêm điều kiện". Số câu là số tối thiểu; Phần còn lại lấy từ phạm vi của môn (mở). Bài đã có dòng thì bảng hiện sẵn.
- **Nguồn chùm:** R-3.
- **V-ACT Tiếng Việt:** bảng mạch (các dòng chỉ tiêu chủ đề, có cột độ khó trong mạch, mẫu đã đặt) là phần chính, luôn hiện. Dòng chưa gắn MAPID hiện "Chờ mapping" (H31).

- Nguồn: QDA §4 (dòng 95, 105, 144), BCA §5c, §6, §7b (dòng 159–163), VACT §3.3 (dòng 62), §4.1; 2B-VT §5.2.
- Tiêu chí chấp nhận:
  - **AC-18** QDA Toán hiện Phạm vi kiến thức và nút "Thêm chỉ tiêu chủ đề"; chưa có dòng thì không hiện bảng.
  - **AC-19** V-ACT Toán đặt Xác suất 10% trên 30 câu thì hiện "Xác suất 3 câu · Phần còn lại 27 câu".

### R-7 Danh sách dòng lấy câu

DS gồm:

- **Đầu môn** như hôm nay (tên, trạng thái phân bổ, số câu chỉ đọc).
- **Bảng dòng** theo thứ tự của mẫu, mỗi bảng thuộc đúng một mục (`sectionId`). Mục có nhiều phân đoạn hiển thị (`displaySegments`) thì hiện tiêu đề phân đoạn nhưng vẫn là một bảng. Cột: Thứ tự (hoặc dải vị trí câu), Tên, Cách lấy (Câu đơn / Câu chùm, kèm loại câu), Số lượng (n câu hoặc a chùm × b), Điều kiện (tóm tắt một dòng), nút Sửa. Dòng có audio hiện chip audio.
- **Chỉ số chỉ đọc của dòng** theo mẫu và 2B-HV R-133/R-139/R-140: số lệnh hỏi, điểm, thang, ví dụ "4 câu Đ/S · 16 ý · thang 4 điểm". Ví dụ cách chấm hiện như hôm nay. Không thêm ô sửa điểm.
- **Phân bổ đối chiếu** ở cuối, mặc định đóng: phân bố lớp, độ khó, mạch của mục với mức áp dụng theo R-8, đơn vị và mẫu số theo R-9.
- **Màn sửa dòng** gọn:
  - Tên (dòng của mẫu cho sửa tên, HSCA §6.3);
  - Cách lấy: Câu đơn / Câu chùm; theo khóa ở bảng "Khóa" dưới;
  - Số câu, hoặc Số chùm và Số câu con;
  - nút **Thêm điều kiện**, mỗi mục ghi vào một trường riêng, có giá trị sẵn thì mục hiện mở:

    | Mục | Ghi vào | Nghĩa |
    |---|---|---|
    | Nguồn / Loại văn bản | `tagCodes` | Lọc câu theo nhãn, ví dụ loại văn bản của chùm Văn QDA, HSA (K11) |
    | MAPID (lọc) | `block.knowledgeScope` | Chỉ lấy câu thuộc các MAPID |
    | Phân bổ theo MAPID | `block.topicQuotas` | Số câu tối thiểu theo từng MAPID, nằm trong số câu của dòng (SP §5.1 dòng 98, THPT §3 bước 3) |
    | Độ khó, Khối lớp | `block.matrix` `SEPARATE`, theo câu | Phân bố của dòng |
    | Điều kiện từng chùm | `slots` | |
    | Theo vị trí câu con | `childConditions` | Chỉ SP2E Anh; V-SAT Anh do mẫu khóa |

  - công tắc "Đảo thứ tự các chùm" chỉ khi mẫu cho (`shuffleEditable`; nhóm 5 Văn của HSA và QDA, K4); công tắc "Tạm bỏ qua" chỉ ở nhóm tự luận không bị khóa;
  - các trường do mẫu đặt (hình dạng câu, định dạng đáp án, điểm mỗi câu, năng lực, cách chấm dự kiến) hiện thành một dòng chữ chỉ đọc, ví dụ "Theo mẫu: 4 ý · 0,25 điểm".

**Khóa.** Mỗi loại khóa hiện kèm lý do bằng chữ (`DESIGN.md` 5.14, mục khóa):

| Khóa | Nguồn | Áp khi | DS làm gì |
|---|---|---|---|
| Khóa tổng | `fixed_counts` (H27) | Bài theo mẫu có cờ (ví dụ V-ACT FULL); không xét phạm vi | Số câu của mục chỉ đọc ở đầu môn; dòng vẫn sửa được, lệch tổng thì hiện thiếu/vượt |
| Khóa dòng | `fixed_block_counts` (H42) | **Chỉ bài phạm vi `SUBJECT`** tạo từ mẫu môn đầy đủ của 4 môn H-SCA (Toán, Lý, Hóa, Sinh) và 8 môn V-SAT. Bài `PART` và bài ôn từ cùng mẫu không khóa | Cách lấy, Số câu, Số chùm, Số câu con chỉ đọc; lý do "Theo mẫu đề chuẩn, không đổi số lượng" |
| Khóa dạng | `answerTypeLocked` của nhóm | Nhóm có cờ, mọi phạm vi | Cách lấy chỉ đọc |
| Khóa nhóm đã lược | `omitted_locked` (H43, H49) | H-SCA Ngữ văn, V-SAT Ngữ văn | Dòng "Viết – tạm bỏ qua", không có công tắc bật lại |
| Không khóa | Nhóm tự luận lược của SP2E, SPT | — | Công tắc "Tạm bỏ qua" đổi được |

**Thêm và tách dòng** chỉ ở cặp được đăng ký (H-SCA Anh, V-ACT `SCIENTIFIC_THINKING`) và chỉ ở dòng không chịu khóa dòng hay khóa dạng. Các cặp khác không có nút thêm, lên, xuống, xóa dòng của mẫu. Luật tách:

- **Khi nào được tách.** Nút tách khóa, kèm lý do "Dòng có điều kiện riêng; bỏ điều kiện trước khi tách", khi dòng cha có một trong: `slots`, `childConditions`, `matrix`, `topicQuotas` của dòng, hoặc được một chỉ tiêu chủ đề của mục tham chiếu qua `groupIds`. Nút tách cũng khóa khi dòng là **chùm cỡ linh hoạt** (`setCount`, `setSize` đều `null`, H28: không có cỡ để chia theo chùm; lý do "Dòng không có cỡ chùm cố định") hoặc là **nhóm đang tạm bỏ qua** (`omitted`; lý do "Dòng đang tạm bỏ qua"). FTM chưa có quy tắc chia các điều kiện này mà giữ nguyên nghĩa (ví dụ slot ở vị trí 4 của nhóm 4 chùm không còn hợp lệ ở dòng con 1 chùm, `ExamStructureValidator` từ chối). Ca Đọc 4 × 10 của H-SCA Anh và Logic 4 × 3 của V-ACT trong mẫu không có điều kiện riêng nên tách được.
- Tách trong đúng một mục và một dòng (`sectionId`, `blockId`). Dòng cha bị **thay** bằng n dòng con đứng ngay tại vị trí dòng cha; tổng số câu (hoặc số chùm × cỡ) của các dòng con bằng dòng cha. Tổng mục, các dòng khác, các mục khác (kể cả mục cùng `subjectCode`, như Logic và Suy luận) không đổi.
- **Trường của dòng con:**
  - Dòng con đầu tiên giữ `id` của dòng cha; các dòng con sau có `id` mới duy nhất. Máy chủ giữ `templateGroupCode`, `answerTypeLocked`, `shuffleEditable` theo `id` đã lưu và bỏ chúng ở `id` mới của bài theo mẫu (`ExamStructureOps.restoreBlock`); web không tự gán các trường này.
  - Sao chép từ dòng cha: dạng, `childAnswerType`, cỡ chùm, `knowledgeScope`, `tagCodes`, `shuffleUnits` (đúng giá trị, kể cả `false`; máy chủ mặc định `true` khi thiếu, `QuestionBlock.java:35–38`), điểm mỗi câu, cách chấm dự kiến, hình dạng câu, định dạng đáp án.
  - Tên mặc định "<tên cha> 1…n" (sửa được); số lượng chia lại.
  - Yêu cầu audio thuộc **mục** (`section.audioRequirement`), không thuộc dòng: tách không đổi nó. H-SCA Anh: mục Nghe `REQUIRED`, mục Đọc `FORBIDDEN` giữ nguyên.
- Chia mặc định đều theo đơn vị nguyên (câu, hoặc chùm với dòng chùm), phần dư cho dòng đứng trước. Không chia được (ví dụ 4 chùm thành 5 dòng) thì không cho chọn n đó. Người dùng sửa số từng dòng con được. Mỗi dòng con phải là số nguyên dương (ít nhất 1 câu, hoặc 1 chùm với dòng chùm); 0 hay âm thì lỗi tại dòng. Tổng khác dòng cha thì lỗi tại dòng. Cả hai chặn lưu.
- Không tách theo phân đoạn hiển thị (`displaySegments`): bài đọc dùng chung của V-SAT Anh (H40) là một dòng.
- Không dùng bí danh môn làm khóa ghi; không gộp các mục cùng mã.

**THPTQG Văn:** chỉ hiện "Tự luận – tạm bỏ qua", không có ô sửa. **BCA Văn:** một dòng tự luận, sửa được nguồn và MAPID (K1).

- Nguồn: HSA §5a–§5b (dòng 93–103), QDA §5 (dòng 120–130), HSCA §6.3–6.5, VACT §6.2 (dòng 185–194), SP §5.1, §7.5, THPT §3, §4, §6.2, BCA §4; K1, K4, K11, R-92, R-93; 2B-VT H24, H27, H28; 2B-HV H40–H43, H49, R-133, R-139, R-140.
- Tiêu chí chấp nhận:
  - **AC-20** HSA Văn hiện 5 dòng đúng thứ tự mẫu với cột như trên; nhóm 5 hiện 5 chùm con có loại văn bản; công tắc đảo chùm có ở nhóm 5.
  - **AC-21** Sửa dòng Câu đơn sang Câu chùm hiện Số chùm và Số câu con; "Thêm điều kiện" → Độ khó ghi `block.matrix` `SEPARATE` theo số câu.
  - **AC-22** H-SCA Anh có nút tách dòng Đọc; HSA Văn không có nút thêm, tách, lên, xuống, xóa.
  - **AC-23** H-SCA Toán bài `SUBJECT` từ mẫu môn đầy đủ: Cách lấy và số lượng chỉ đọc, có lý do; bài `PART` cắt từ cùng mẫu: sửa được. Thân `PUT` của bài `SUBJECT` không đổi các trường khóa.
  - **AC-24** THPTQG Văn không có ô sửa; BCA Văn có một dòng tự luận sửa được MAPID.
  - **AC-25** SP2E Toán Phần II đổi được Câu đơn ↔ Câu chùm (SP §5.1).
  - **AC-47** H-SCA Ngữ văn và V-SAT Ngữ văn: dòng viết đã lược không có công tắc bật lại; gửi thẳng API bật lại vẫn ra `OMITTED_BLOCK_LOCKED` như hôm nay. SP2E Ngữ văn: công tắc "Tạm bỏ qua" đổi được và lưu `omitted`. V-SAT Toán bài `SUBJECT` khóa dòng, bài `PART` sửa được.
  - **AC-51** (a) V-ACT: tách Logic 12 câu (4 chùm × 3) thành 4 dòng 1 × 3: dòng Logic bị thay, tổng mục Logic vẫn 12, mục Suy luận 18 không đổi. (b) H-SCA Anh: tách Đọc 4 × 10 thành 4 dòng 1 × 10: mục Nghe và `audioRequirement` của hai mục (Nghe `REQUIRED`, Đọc `FORBIDDEN`) không đổi qua `PUT`/`GET`; dòng con đầu giữ `id` và `templateGroupCode`. (c) V-SAT Anh: bài đọc liên phần hiện một dòng, mở rồi lưu không sinh hai bản. (d) Chia 4 chùm thành 5 dòng không chọn được. (e) Dòng có `slots`, và dòng được chỉ tiêu chủ đề Bắt buộc tham chiếu qua `groupIds`: nút tách khóa kèm lý do; `PUT`/`GET` và bước Kiểm tra không đổi. (f) Sau khi tách (a), (b): bước Kiểm tra không phát sinh lỗi khóa hay lỗi cấu trúc mới. (g) Tách 4 → 4 rồi sửa một dòng con về 0 (0 + 2 + 1 + 1): lỗi tại dòng, không gửi `PUT`. (h) Dòng chùm cỡ linh hoạt và dòng đang tạm bỏ qua: nút tách khóa kèm lý do. (i) Dòng có `shuffleUnits: false` tách thành n dòng: mọi dòng con `false` qua `PUT`/`GET`.
  - **AC-52** (a) QDA Văn hiện 5 dòng đúng thứ tự; dòng 5 có 5 chùm × 5, mỗi chùm đúng nhãn (`slots`), một công tắc đảo đổi được. (b) SP2E Toán: "MAPID (lọc)" ghi `block.knowledgeScope`, "Phân bổ theo MAPID" ghi `block.topicQuotas`; hai trường khác nhau. (c) Dòng THPTQG 4 câu Đ/S hiện 16 ý và thang 4 điểm. (d) Ví dụ cách chấm của 2B-HV vẫn hiện.

### R-8 Mức áp dụng theo kỳ

Biểu mẫu theo môn không có nút Bắt buộc/Tham khảo chung. Mức áp dụng theo kỳ ở bảng R-1:

- **HSA:** mọi phân bố theo câu của một môn là Bắt buộc, chip "Bắt buộc" (HSA-C); ngoại lệ HSA-C ở R-2 giữ chip Tham khảo kèm ghi chú như hôm nay.
- **QDA, THPTQG:** theo từng **chiều** (lớp, độ khó) của ma trận mục (`section.matrix`; QDA ở MT, THPTQG ở Phân bổ đối chiếu của DS). Chiều để trống hết thì ghi mảng rỗng (`byGrade: []` hoặc `byDifficulty: []`, đúng kiểu `BlockMatrix`), không áp; cả hai chiều trống và ma trận đã lưu là `null` thì giữ `null` (QDA §4a "chỉ áp dụng hạn ngạch khi quản trị viên khai báo"; THPT §3 "chế độ tùy chọn"). Chiều đã khai ít nhất một ô là Bắt buộc và giữ luật 2B: tổng các ô phải bằng số câu của mục, ô để trống trong chiều đã khai tính là 0. Web hiện "Đã khai x/y" ngay dưới hàng; tổng lệch vẫn lưu nháp được, bước Kiểm tra báo như hôm nay. Muốn tổng thiếu mà vẫn sinh phần còn lại ngoài chỉ tiêu là thay đổi nghiệp vụ, không thuộc FTM.
- **BCA:** phân bố lớp và độ khó là Tham khảo, chip "Tham khảo" (BCA dòng 108, H6).
- **VACT, SP2E, SPT, HSCA, VSAT:** mặc định Chỉ đối chiếu. Nút "Áp dụng khi sinh đề" (VACT §3.2.3, HSCA §3, VSAT §5.1 bước 4) chỉ bật được với phân bố **theo câu và của riêng một môn**; bật thì lưu `REQUIRED`. Phân bố theo lệnh hỏi (`PROMPT`, H44, D19) và chỉ tiêu nhiều môn (H6) chỉ để đối chiếu: nút khóa, kèm lý do "Phân bố theo lệnh hỏi chỉ dùng để đối chiếu" hoặc "Chỉ tiêu nhiều môn chỉ dùng để đối chiếu". Giữ nguyên đơn vị, mẫu số (ví dụ "Toán FULL · 67 lệnh") và dòng "Chưa phân loại" của câu thiếu nhãn (H44, R-139). Không tự đổi `basis`, không lấy số lệnh làm số câu. Cần áp dụng thì người dùng thêm phân bố theo câu ở cấp phần qua "Thêm điều kiện".

- Tiêu chí chấp nhận:
  - **AC-26** QDA Lý (25 câu): khai độ khó 10/10/5, để trống Vận dụng cao: ma trận lưu `REQUIRED` với 3 giá trị, không có giá trị Vận dụng cao.
  - **AC-27** BCA Toán hiện chip Tham khảo, không có nút đổi; HSA Toán hiện chip Bắt buộc, không có nút đổi.
  - **AC-28** V-SAT Toán: phân bố 67 lệnh của mẫu (`PROMPT`, `REFERENCE`) hiện mẫu số lệnh, nút "Áp dụng khi sinh đề" khóa kèm lý do; lưu không đổi `basis` hay mức. Thêm phân bố độ khó theo câu ở Phần II rồi bật nút: lưu `REQUIRED`, `QUESTION`.
  - **AC-48** V-SAT môn có câu thiếu nhãn: Phân bổ đối chiếu hiện dòng "Chưa phân loại"; chỉ tiêu nhiều môn hiện nút khóa kèm lý do.
  - **AC-53** Qua bước Kiểm tra (không chỉ serializer), nhập từ web rồi kiểm:
    - QDA Lý, mục 25 câu (MT): (a) cả chiều độ khó trống → `byDifficulty: []`, không lỗi ma trận; (b) khai 10/10/5, trống Vận dụng cao → không lỗi; (c) khai 10/10/5/0 → lưu ô 0, không lỗi; (d) khai 10/5/5 (tổng 20) → `SUBJECT_MATRIX_TOTAL_MISMATCH`, đúng `sectionId`, chiều `DIFFICULTY`, như hôm nay.
    - THPTQG Lý, mục Phần I 18 câu (DS): (a) chiều trống → không lỗi; (b) khai 8/6/4, trống Vận dụng cao → không lỗi; (c) 8/6/4/0 → không lỗi; (d) 8/6/2 (tổng 16) → `SUBJECT_MATRIX_TOTAL_MISMATCH` ở mục Phần I, chiều `DIFFICULTY`; hai mục Phần II, III không bị.

### R-9 Nhập theo tỷ lệ

| Kỳ | Bảng nhập được % | Mẫu số |
|---|---|---|
| VACT | Cấu trúc câu (Toán: 12 đơn / 18 câu con), lớp, độ khó, bảng mạch, chỉ tiêu chủ đề (hai cột Số câu / Tỷ lệ, R-121) | Số câu của nơi đang sửa: tiểu mục (mục) |
| HSCA, VSAT, SP2E, SPT | Phân bổ đối chiếu (lớp, độ khó, MAPID) của mục; số lượng dòng ở màn sửa dòng | Phân bổ: số câu (hoặc số lệnh với phân bố `PROMPT`) của mục đang mở; dòng: số câu của mục chứa dòng |
| HSA, QDA, BCA, THPTQG | Không; % hiện chỉ đọc bên cạnh số câu | — |

- Hai giá trị hiện cạnh nhau; nhập % thì hiện ngay số nguyên quy ra và mẫu số (ví dụ "30% của 40 câu = 12 câu") trước khi lưu.
- **Quy đổi:** dùng lại `apportionByPercent` (`matrix.ts:49–70`): lấy phần nguyên của từng dòng, phần còn thiếu cho dòng có phần thập phân lớn nhất; bằng nhau thì dòng đứng trước (VACT §3.2.4, VSAT §5.1 bước 3). Không viết thuật toán chia dư mới. Có test đối chiếu kết quả với cách quy đổi của máy chủ cho ma trận `PERCENT`.
- **Khi nào cần đủ 100%.** Chỉ ở bảng chia **cả mục** theo một chiều: ma trận lớp, độ khó của mục (và Phân bổ đối chiếu), và Cấu trúc câu của V-ACT khi nhập % cho mọi vai trò. Một dòng DS, một dòng chỉ tiêu chủ đề hay dòng bảng mạch (có Phần còn lại) quy % trên mẫu số riêng (số câu của mục chứa dòng), được dưới 100%, không xét tổng (HSCA §6.3 dòng 117–126).
- **Ma trận chưa đủ 100%:** giữ chặn ở web như hôm nay (`allocation.ts:242–266`; lỗi `MATRIX_PERCENT` chặn `PUT` ở `exam-structure-view.tsx:347–366`; FU AC-7 của plan đợt 3 giữ guard này): hiện "Tổng tỷ lệ x%, cần 100%", không gửi `PUT`. Máy chủ vẫn kiểm `PERCENT_TOTAL_INVALID` khi dữ liệu đến bằng API. FTM không đổi guard; đổi là thay đổi nghiệp vụ, phải quyết cùng FU.
- **Cấu trúc câu V-ACT nhập % chưa đủ 100%:** không quy ra số, hiện "cần 100%", chặn lưu như lỗi nhập thô (R-15).
- **Lưu, theo loại** (không đổi lược đồ):

  | Nơi nhập % | Lưu | Mở lại sau khi lưu |
  |---|---|---|
  | Ma trận lớp, độ khó của mục (V-ACT; Phân bổ đối chiếu H-SCA, V-SAT, SP2E, SPT) | `unit: PERCENT`, các giá trị là %; máy chủ quy ra câu | Hiện lại đúng % đã nhập |
  | Chỉ tiêu chủ đề, bảng mạch, phân bổ theo MAPID | `count` đã quy đổi và `percent` gốc (H30); không ghi dòng Phần còn lại (R-121) | Hiện lại % gốc và số |
  | Ô Cấu trúc câu (V-ACT), số lượng của dòng (DS) | Chỉ lưu số câu / số chùm; % chỉ là trợ giúp nhập | Hiện số đã lưu và % tính từ số; không hứa giữ % gốc hay chế độ nhập |

- **Đổi tổng** (`TotalChangeNotice`): trong phiên, chỗ đang nhập theo % giữ % và tính lại số; chỗ nhập theo số giữ số, hiện thiếu/vượt. Ngoài phiên (đã lưu rồi đổi tổng ở lần mở sau): ma trận `PERCENT` và chỉ tiêu có `percent` tính lại từ % đã lưu; ô cấu trúc và dòng giữ số đã lưu, hiện thiếu/vượt.
- **Chùm nguyên:** dòng chùm cỡ cố định k mà số câu quy ra không chia hết cho k thì lỗi tại dòng ("Tỷ lệ này ra 10 câu, không chia được thành chùm 4 câu") và chặn lưu (HSCA §6.3, R-142).

- Nguồn: VACT §3.2–3.4, §5.2, HSCA §3, §6.3, VSAT §5.1 bước 3, SP dòng 42; HSA/QDA/BCA ghi "n câu (x%)"; CHUNG AC-04.5; H30, R-121, R-142.
- Tiêu chí chấp nhận:
  - **AC-29** HSA Toán không có lựa chọn nhập theo tỷ lệ; % hiện cạnh số câu.
  - **AC-30** Fixture dựng tại chỗ: mục 40 câu không khóa dòng, một dòng câu đơn nhập 30% hiện "12 câu" trước khi lưu, lưu 12. Với mẫu H-SCA Toán thật, mẫu số là số câu của **mục** chứa dòng, không phải 40 của cả môn (ví dụ bài `PART`, mục 25 câu, 40% hiện "10 câu").
  - **AC-54** (a) V-ACT Toán độ khó 25/50/25% của 30 câu: hiện 8/15/7, lưu `unit: PERCENT` với 25/50/25; tải lại vẫn 25/50/25%; đổi tổng sang 40 (trong phiên và sau khi tải lại) ra 10/20/10. (b) V-ACT Toán Cấu trúc câu nhập 40% / 60% của 30: lưu nhóm đơn 12, chùm tổng 18; tải lại hiện 12/18 và 40%/60% tính từ số. (c) Chỉ tiêu chủ đề V-ACT nhập 10% của 30: lưu `count` 3, `percent` 10; dòng chỉ tiêu nhập số 5 giữ 5 khi đổi tổng. (d) Fixture riêng (tách khỏi ca AC-30): mục 40 câu có dòng chùm cỡ 4, nhập 25% quy ra 10 câu: lỗi tại dòng, mọi đường lưu và Kiểm tra không gửi request. (e) Một dòng DS của SP2E nhập % rồi lưu: lưu số câu; tải lại hiện số. (f) Ma trận độ khó V-ACT tổng 90%: hiện "cần 100%", web không gửi `PUT`; gửi thẳng API thì bước Kiểm tra ra `PERCENT_TOTAL_INVALID`. Một dòng DS nhập 30% (dưới 100%) quy ra số và lưu được. (g) Kết quả `apportionByPercent` khớp quy đổi của máy chủ trên bảng ca có hòa.

### R-10 Bảng kết hợp Lớp × Độ khó

Chỉ V-SAT có bảng kết hợp trong biểu mẫu theo môn (`answers.md` câu 1). Theo G1-7 (A):

- Mở từ **"Thêm điều kiện"** của khối Phân bổ đối chiếu ở **cấp phần** (mỗi phần của môn V-SAT là một mục). Lưu vào ma trận của mục (`section.matrix`, `COMBINED`), chỗ lưu đã có.
- Cấp toàn môn chỉ có bảng riêng từng chiều: chỉ tiêu cấp bài hiện chỉ có một chiều, chưa có chỗ lưu bảng kết hợp toàn môn.
- Khi đổi giữa riêng từng chiều và kết hợp mà đã có số, hiện bảng xem trước dữ liệu sẽ chuyển (CHUNG §7.1) và chỉ ghi khi người dùng xác nhận.
- Đơn vị và nút áp dụng theo R-8.
- Kỳ khác chỉ có riêng từng chiều; MD giữ cả hai (CHUNG AC-04.4 vẫn đạt nhờ MD).

- Nguồn: VSAT §5.1 bước 1–2 (dòng 46–47); CHUNG §7.1; HSA, QDA, BCA, VACT, HSCA chỉ có bảng riêng từng chiều; AD-61 câu 1.
- Tiêu chí chấp nhận:
  - **AC-31** V-SAT Toán Phần I: "Thêm điều kiện" → "Kết hợp Lớp × Độ khó", xem trước rồi xác nhận, lưu `section.matrix` `COMBINED`; mở lại vẫn ở DS với bảng kết hợp.
  - **AC-32** HSA, QDA, BCA, VACT, HSCA, SP2E, SPT, THPTQG không có mục kết hợp trong "Thêm điều kiện"; bài cũ của các kỳ này có `COMBINED` thì mở MD (R-2).
  - **AC-49** V-SAT: hủy ở bước xem trước thì ma trận không đổi; khối cấp toàn môn không có mục kết hợp.

### R-11 Ẩn khối lớp

Theo G1-1 (A): biểu mẫu theo môn không hiện khối lớp (ở Phân bổ đối chiếu, Thêm điều kiện, hàng lớp của MT) cho:

- SP2E, SPT, THPTQG: Lịch sử, Địa lí, GDKTPL (cả `ECON_LAW` và `GDKTPL`), Tiếng Anh (R-61, SP §7.1, THPT §3 bước 4);
- HSA: Tiếng Anh;
- V-SAT: Địa lí, Tiếng Anh, Ngữ văn (VSAT §5.2, cột lớp ghi "—").

Chỉ ẩn ở màn soạn theo môn (MT, DS). Bước Kiểm tra, Sinh thử, MD giữ hiển thị như hôm nay. Dữ liệu lớp đã có trong mẫu và bài giữ nguyên, chỉ không hiện; lưu lại không xóa. Môn có điều kiện lớp đang **Bắt buộc** ở chỗ bị ẩn thì mở MD (R-2), để người dùng thấy và sửa; không tự gỡ ràng buộc. Lịch sử, Địa lí của HSA, QDA, BCA và Lịch sử của V-SAT vẫn hiện khối lớp vì tài liệu của các kỳ đó có số liệu lớp (HSA dòng 128, QDA dòng 155, BCA dòng 113, VSAT dòng 60).

- Nguồn: M.Trường chọn A ngày 2026-11-19 (D-6, AD-61a); phạm vi từng kỳ và bề mặt theo G1-1 (A).
- Tiêu chí chấp nhận:
  - **AC-33** SP2E Lịch sử không có khối lớp ở mọi chỗ của DS; mở rồi lưu không sửa gì thì phân bố lớp `REFERENCE` của mẫu còn nguyên trong thân `PUT`.
  - **AC-34** HSA Lịch sử (MT) vẫn có hàng khối lớp.
  - **AC-57** Tham số hóa theo bảng trên, gồm cả hai mã `ECON_LAW`, `GDKTPL`: DS/MT ẩn lớp; bước Kiểm tra và MD vẫn hiện lớp; bài có điều kiện lớp `REQUIRED` ở chỗ ẩn mở MD và giữ ràng buộc.

### R-12 Giới hạn môn Khoa học HSA, QDA

Áp cho **bài chuẩn** (`buildMode` khác `REVIEW`) của kỳ HSA, QDA, ở mọi mục có môn dưới đây, dù mục nằm ở `sections`, `subjectPool` hay `options[].sections` (bài `FULL`, `PART`, `SUBJECT`). **Máy chủ** kiểm mọi bài chuẩn này, dù bài được soạn bằng biểu mẫu nào hay gửi qua đường nào (UI hay API). **Web** chỉ chặn trong MT, với lỗi do người dùng sửa trong MT; MD và ON giữ hành vi hôm nay (R-14), lưu nháp được, lỗi hiện ở bước Kiểm tra. Mục đang vi phạm ngay khi nạp mở MD (R-2).

| Cặp | Giới hạn |
|---|---|
| HSA, QDA: `LY`, `HOA`, `SINH` | Số chùm 1 hoặc 2, mỗi chùm 3 câu con; Điền đáp án ≥ 1 |
| HSA, QDA: `SU`, `DIA` | Số chùm 1 hoặc 2, mỗi chùm 3 câu con; Điền đáp án được 0 |

- **Web (MT):** ô Số chùm chỉ nhận 1–2; ô Điền đáp án của Lý, Hóa, Sinh chỉ nhận ≥ 1; sai thì lỗi tại ô và chặn mọi đường lưu (R-15).
- **Máy chủ:** bước kiểm tra cấu hình (`ExamCheckRules`) báo lỗi chặn công bố, cấp môn, hai mã mới:
  - `SECTION_SET_COUNT_RANGE`: số chùm của môn ngoài 1–2 hoặc cỡ chùm khác 3;
  - `SHORT_ANSWER_MIN`: Lý, Hóa, Sinh không có câu điền đáp án nào.

  Máy chủ đọc mã kỳ và `buildMode` của bài, mã môn của mục (không có môn nào của luật nằm trong bốn cặp bí danh). Lưu nháp qua API vẫn nhận, chỉ công bố bị chặn.
- **Kết quả kiểm tra cũ:** bài chuẩn HSA, QDA có môn Khoa học chỉ công bố được khi kết quả kiểm tra **cả bài** (phạm vi `EXAM`) đang lưu **đã được đánh giá dưới luật mới** (mang dấu), và vẫn mới so với cấu trúc, cấu hình, nguồn đang lưu (kiểm độ mới như hôm nay). Dấu **không phụ thuộc kết luận tổng** `VALID` hay `INVALID`: một lần kiểm cả bài chạy xong và lưu dưới luật mới là có dấu. Kiểm một môn, một phần, hay lần kiểm không hoàn tất không cấp dấu. Thiếu dấu thì công bố báo cần kiểm tra lại với lý do `RULES_CHANGED`; GET kết quả kiểm tra cũng trả lý do này. Có dấu thì công bố từng đường theo E24 (AD-60, AD-60a): đường đủ điều kiện công bố được; đường lỗi chưa phục vụ được để lại; đường lỗi đang phục vụ chặn công bố, phiên bản không đổi. Không migration, không đánh dấu chung mọi bài, không dựa vào thời điểm deploy. Kiểm tra và lần công bố thất bại không đổi bản đã công bố hay đường đang phục vụ; lần công bố thành công đổi đường như hôm nay. Bài kỳ khác và bài ôn không bao giờ nhận lý do này, kể cả ở GET. Cách lưu dấu chốt cùng lane EX-1 trước khi sửa vùng của EX-1 (plan CR-4).
- **Web hiện đúng:** hai mã mới hiện đúng tên và vị trí ở bước Kiểm tra (không bị đổi thành mã chung); lý do `RULES_CHANGED` hiện bằng chữ ở trạng thái kết quả kiểm tra và ở hộp công bố khi máy chủ trả `CHECK_REQUIRED`, kèm lối kiểm tra lại. Không đổi bố cục bước Kiểm tra; chỉ thêm ánh xạ mã, lý do và nhãn.
- Nguồn: QDA §6a (dòng 141), §8 (dòng 190–196), HSA §6b–§6c (dòng 131, 135–150), §8 (dòng 188–191); RULES.md (máy chủ là nơi chặn); AD-61 câu 4; `ExamPublishService.java:64–114`.
- Tiêu chí chấp nhận:
  - **AC-35** QDA Lý nhập 3 chùm: lỗi tại ô, không lưu được; nhập 0 câu điền: lỗi tại ô.
  - **AC-36** Gửi thẳng `PUT /structure` cho QDA Lý 3 chùm: lưu được; chạy kiểm tra cấu hình ra `SECTION_SET_COUNT_RANGE`, công bố bị từ chối.
  - **AC-37** HSA Hóa không có câu điền: kiểm tra ra `SHORT_ANSWER_MIN`; HSA Sử 0 câu điền: không có lỗi này.
  - **AC-38** Kỳ khác (ví dụ BCA Lý 3 chùm, H-SCA Lý) không bị hai luật này.
  - **AC-39** Hai mã có thông điệp ở `messages.properties`, `messages_vi.properties`, `messages_en.properties` và nhãn ở `vi.json`, `en.json` (`exams.review.issue`).
  - **AC-46** Bài ôn QDA Sinh theo BA (7 câu đơn + 1 chùm, không câu điền) và bài ôn HSA toàn trắc nghiệm: không có hai mã mới; bài chuẩn QDA Sinh cùng cấu hình sai: có mã, kể cả khi gửi qua API hay mở bằng MD. Luồng `?block=` của bài ôn mở như hôm nay.
  - **AC-58** (a) Bài chuẩn QDA Lý 3 chùm có kết quả `VALID` lưu trước khi có luật: công bố bị từ chối (`RULES_CHANGED`); GET kết quả kiểm tra trả cùng lý do; kiểm cả bài thì ra `SECTION_SET_COUNT_RANGE` và kết quả mang dấu. (b) Bài chuẩn HSA hợp lệ có `VALID` cũ: kiểm một môn → công bố vẫn bị từ chối; kiểm cả bài → công bố được. (c) Bài QDA có kết quả tổng `INVALID` dưới luật mới: đường lỗi **chưa phục vụ** → không còn `RULES_CHANGED`, các đường đủ điều kiện công bố được; đường lỗi **đang phục vụ** → từ chối theo E24, phiên bản không đổi. (d) Qua các lần kiểm tra và lần công bố thất bại ở trên, bản đã công bố và đường đang phục vụ không đổi. (e) Bài BCA và bài ôn QDA có `VALID` cũ: công bố như hôm nay, GET không có lý do mới.
  - **AC-60** Trang Kiểm tra thật (`ExamReviewCheck`, qua adapter thật) với GET `stale` + `RULES_CHANGED` và hai lỗi mới ở hai môn khác nhau: hiện `SECTION_SET_COUNT_RANGE`, `SHORT_ANSWER_MIN` đúng mã (không thành `INFO_MISSING`), đúng môn và đích; nhãn lý do cũ hiện bằng chữ cả vi và en; nút kiểm tra lại chạy. Hộp công bố nhận 409 `CHECK_REQUIRED` lý do `RULES_CHANGED` hiện chữ (không thành `UNKNOWN`) và dẫn kiểm tra lại.

### R-13 Dọn trường

Các trường dưới đây không có trong MT và DS. Chúng chỉ còn ở MD:

| Trường | Lý do |
|---|---|
| Bảng dạng trả lời 6 cột (Chỉ tiêu, Quy đổi, Đã phân bổ, Còn thiếu, Nguồn) và nút Thêm dạng | MT thay bằng Cấu trúc câu (R-3); DS ghi loại câu ở dòng |
| Khai báo/Bỏ từng khối chỉ tiêu | BA luôn hiện các khối của môn |
| Lựa chọn Kết hợp / Riêng từng chiều ở đầu khối ma trận | Riêng từng chiều là mặc định; V-SAT mở bảng kết hợp từ "Thêm điều kiện" (R-10) |
| Chọn đơn vị đếm (Câu / Lệnh hỏi / Điểm) | H7: phân bố Bắt buộc chỉ nhận `QUESTION`; đơn vị của phân bố đối chiếu hiện thành nhãn (R-8) |
| Chọn trường hợp "Xem theo số câu của môn" | H3: môn Khoa học luôn 17 câu |
| "Lấy phần còn lại của môn" ở bài chuẩn | H3 |
| Bảng "Đã phân bổ" theo lớp và độ khó riêng | Số đã phân bổ nằm ngay dưới ô (R-4) |
| Lên/Xuống/Xóa dòng của mẫu | K4, R-92 |
| Sửa năng lực, cách chấm dự kiến, hình dạng câu, định dạng đáp án, điểm mỗi câu ở mức dòng | Mẫu đặt; DS hiện chỉ đọc (R-7) |
| Cột "Nguồn" của bảng dạng trả lời | Cùng tinh thần HSA-C bỏ "Nguồn phù hợp" (UNI-1703) |

- Tiêu chí chấp nhận:
  - **AC-40** MT và DS của mọi cặp không hiện trường nào ở bảng trên; MD vẫn hiện đủ như hôm nay.

### R-14 Biểu mẫu mặc định và bài ôn giữ nguyên

MD là màn môn hiện tại, không đổi hành vi. ON là `ReviewSubjectPanel` hiện tại.

- Tiêu chí chấp nhận:
  - **AC-41** Các test hiện có của màn môn, màn nhóm, bài ôn và E2E bước Cấu trúc xanh khi chạy với bài mở MD (fixture MD/ON); test nào đổi điểm quan sát vì màn mới thì theo bảng ở plan (B5), không bỏ assert về kết quả sinh.

### R-15 Điều hướng, lưu, giao diện

**Điều hướng.** Biểu mẫu được chọn theo mục cha (R-1) cho cả mục và nhóm. `?block=`, bấm lỗi kiểm tra và bấm dòng tóm tắt:

- MT: mở môn, đưa focus tới ô hoặc điều kiện tương ứng (Cấu trúc câu, Nguồn chùm, bảng Quy tắc MAPID của HSA-D); không mở editor nhóm đầy đủ.
- DS: mở môn, mở màn sửa dòng của đúng dòng.
- MD, ON: như hôm nay.
- Không lộ nút thêm, xóa, đổi thứ tự trái biểu mẫu.

**Lưu.** Lỗi chặn lưu của MT/DS (giới hạn R-12, tỷ lệ R-9, chùm nguyên, Trắc nghiệm nhỏ hơn câu con, tổng dòng khi tách) chỉ xét ở mục đang dùng MT/DS theo biểu mẫu đã ghim trong phiên (R-2); mục MD không bị xét. Lỗi tính trên bản nháp **cộng trạng thái nhập thô** (giá trị đã gõ mà chưa ghi được vào bản nháp, ví dụ tỷ lệ quy ra không chia hết cỡ chùm), giữ theo `sectionId`/`blockId`, không trên component đang mở: môn chưa mở hay đã đóng vẫn được xét, đóng màn sửa dòng không làm mất lỗi. Trạng thái nhập thô không vào thân `PUT`. Bỏ thay đổi thì xóa đúng trạng thái nhập thô của phần bị bỏ.

**Thay đổi chưa lưu.** Có thay đổi chưa lưu khi bản nháp khác bản đã nạp **hoặc** còn trạng thái nhập thô (không chỉ so JSON bản nháp). Trạng thái này dùng cho mọi chỗ hỏi trước khi mất dữ liệu: cảnh báo rời trang và tải lại (`useUnsavedChangesGuard`), hộp chuyển mục, đổi phiên bản mẫu (`StructureVersionPicker`), chuyển tab của bài. Chọn Ở lại thì không mất gì. Chọn Bỏ thay đổi, hoặc xác nhận nạp mẫu, thì xóa trạng thái nhập thô tương ứng và ghim lại biểu mẫu theo bản mới; sau đó không còn lỗi chặn nào từ `id` cũ. Dữ liệu nền tải về lại không thay bản nháp đang sửa. Giá trị nhập chưa hợp lệ không bao giờ vào thân `PUT`.

- Lưu, Lưu và chuyển: không gửi `PUT` khi còn lỗi.
- Kiểm tra, Tiếp tục: xét lỗi **trước** cả khi bản nháp không đổi (`dirty = false`); còn lỗi thì không gửi kiểm tra, không gửi `PUT`.
- Mọi đường đưa người dùng tới ô lỗi đầu tiên và đặt focus. Lưu một môn không ghi thay đổi vào môn khác.

**Giao diện.**

- Mọi chữ mới có khóa ở cả `vi.json` và `en.json`; không có chữ tiếng Việt viết thẳng trong component; không trộn hai ngôn ngữ trên một màn.
- Biểu mẫu dùng thành phần có sẵn (`Card`, `FormField`, `StatusChip`, `ScrollableTable`, `AllocationGap`, `EnforcementToggle`, `KnowledgePickerDialog`) và token màu của `DESIGN.md`.
- Ở 390px không có cuộn ngang trang; bảng dòng cuộn trong `ScrollableTable`.
- Khóa nào cũng có lý do bằng chữ, không chỉ dựa vào màu hay ô mờ.

- Tiêu chí chấp nhận:
  - **AC-42** Kiểm tra giao diện: HSA Toán (MT với bảng Quy tắc MAPID của HSA-D nhúng vào), QDA Lý (MT có lỗi giới hạn, focus vào ô lỗi), HSA Văn (DS, màn sửa dòng, Thêm điều kiện mở), H-SCA Anh (DS có audio, tách dòng), H-SCA Toán `SUBJECT` (khóa dòng có lý do), V-SAT Toán (bảng kết hợp), một bài MD có dòng chữ R-2, khối Phân bổ đối chiếu gấp và mở; sáng và tối, vi và en, 390 và 1280; tương phản chữ ≥ 4,5:1; không trộn ngôn ngữ.
  - **AC-59** Mở URL có `?block=` của nhóm thuộc môn MT thì vào ô tương ứng của MT; của môn DS thì mở đúng dòng; bấm lỗi và tóm tắt cũng vậy. Môn MT khác có lỗi R-12 do người dùng sửa (2 → 3 chùm) rồi chuyển môn: Lưu và chuyển, Kiểm tra, Tiếp tục không gửi `PUT` hay kiểm tra, focus về ô lỗi; sửa đúng rồi lưu được và môn khác không đổi. Nhập tỷ lệ không chia hết cỡ chùm ở DS khi bản nháp còn sạch: Kiểm tra, Tiếp tục không gửi request. Bài QDA cũ lưu nháp 3 chùm ở Lý: mục Lý mở MD có dòng chữ, các môn khác vẫn lưu được, Lưu không sửa vẫn gửi `PUT` giống bản đã nạp. Bỏ thay đổi thì lỗi nhập thô của phần bị bỏ mất và lưu lại được. Chỉ gõ tỷ lệ chưa hợp lệ (bản nháp sạch) rồi chuyển tab, tải lại hoặc đổi mẫu: có hỏi; Ở lại giữ chữ đã gõ; Bỏ thay đổi hoặc nạp mẫu xóa chữ đó và không còn lỗi từ `id` cũ.

## 7. Ảnh hưởng tới phần đang có

| Phần bị ảnh hưởng | Ở đâu | Đổi thế nào | Yêu cầu |
|---|---|---|---|
| Chọn màn môn, điều hướng | `exam-structure-view.tsx` (đoạn vẽ `ReviewSubjectPanel` / `ExamStructureSubjectPanel`, `?block=`, bấm lỗi, tóm tắt) | Chọn biểu mẫu theo R-1, R-2; ánh xạ đích theo biểu mẫu | R-1, R-2, R-15 |
| Lưu | `exam-structure-view.tsx` (`runSave`, lưu và chuyển, tự lưu trước Kiểm tra / Tiếp tục) | Thêm lỗi chặn lưu tính trên bản nháp | R-15 |
| Màn môn hiện tại | `exam-structure-subject-panel.tsx`, `block-editor.tsx` và thành phần con | Thành MD, không đổi hành vi; một số thành phần được dùng lại | R-14 |
| Thành phần của HSA-B, HSA-C | `allocation-gap.tsx`, `edit-overview-link.tsx`, `enforcement-toggle.tsx`, `hsa-enforcement.ts`, `topic-quota-table.tsx`, `knowledge-scope-field.tsx` | Dùng lại cho kỳ không phải HSA (R-6); không thêm prop cho HSA | R-4, R-6, R-8 |
| Thành phần quy tắc MAPID của HSA-D | `mapid-rules.ts`, `mapid-rules-table.tsx`, `mapid-rule-dialog.tsx` (HSA-D tạo) | MT của HSA nhúng nguyên trạng; FTM không sửa | R-5 |
| Hàm phân bổ | `allocation.ts`, `subject-matrix.ts`, `matrix.ts`, `reference-distribution.ts` | Đọc lại để tính "đã phân bổ" cho MT và DS; không đổi hàm cũ | R-3, R-4, R-9 |
| Lưu nháp, cảnh báo rời trang | `exam-structure-draft.ts`, `exam-structure-unsaved-guard.tsx`, `exam-structure-changes.ts`, `exam-structure-view.tsx` (hộp chuyển mục, `StructureVersionPicker`) | Biểu mẫu mới ghi vào cùng bản nháp; danh sách thay đổi hiện đúng; "thay đổi chưa lưu" gồm trạng thái nhập thô | R-2 (AC-6), R-15 |
| Luật kiểm tra | `backend/.../exam/application/ExamCheckRules.java`, nơi gọi (`DefaultExamChecker`) | Hai mã mới, cần mã kỳ và `buildMode` | R-12 |
| Công bố | `ExamPublishService`, `ExamCheckValidity`, `ExamCheckCompletion`, `ExamCheckResultQuery` (vùng của EX-1) | Kết quả kiểm tra cũ không qua cổng công bố; GET kết quả trả lý do mới | R-12 (AC-58) |
| Hiện kết quả kiểm tra và công bố trên web | `review-types.ts` (`CHECK_ISSUE_CODES`, danh sách lý do), `review-api.ts`, `publish-exam-dialog.tsx` | Thêm hai mã và lý do `RULES_CHANGED` vào ánh xạ và nhãn; không đổi bố cục | R-12 (AC-60) |
| Thông điệp | `backend/src/main/resources/messages*.properties`; `apps/web-admin/src/messages/{vi,en}.json` | Khóa mới | R-12, R-15 |
| BE spec | `docs/specs/2026-11-16-be-pbi-03-04.md` (bảng mã kiểm tra), `docs/specs/2026-11-17-pbi04-dot-2b.md` (bảng mã) | Thêm hai mã và lý do `RULES_CHANGED` | R-12 |
| Thiết kế | `DESIGN.md` mục 5.14 ("Soạn theo môn", "Nhóm lấy câu") | Ghi MT, DS, Thêm điều kiện, Phân bổ đối chiếu, khóa | R-15 |
| E2E bước Cấu trúc | `e2e/tests/exam-hsa-structure.spec.ts`, `exam-hsa-matrix.spec.ts`, `exam-allocation.spec.ts`, `exam-structure-frame.spec.ts`, `exam-preview-qda.spec.ts` | Một số ca đổi điểm quan sát (plan B5) | R-1–R-15 |
| Lane HSA-D (UNI-2758) | nhánh `fix/UNI-2758-hsa-mapid-rules` từ `develop`, forward-merge `fix/UNI-2758-forward` vào `integration/dot-3` | Làm riêng (AD-67, AD-68); phần HSA của MT chờ forward-merge | R-5 |
| Task FU của đợt 3 (UNI-2700) | `apps/web-admin/src/features/exams/**`, `exam/**` kiểm tra đề | Cùng vùng tệp; danh sách mục FU theo biểu mẫu ở plan B2 | R-13, R-14 |
| Seed bài test và bài demo | `R__seed_test_exam_data.sql`, dữ liệu demo | Không đổi; dùng để kiểm R-2, R-12 | R-2, R-12 |

## 8. Dữ liệu và migration

- Không đổi lược đồ, không thêm migration, không đổi hình dạng request/response hay `packages/api-client`. Lý do `RULES_CHANGED` là một giá trị mới của trường chuỗi đã có.
- Bài và mẫu đã lưu: đọc như hôm nay. Bài không khớp thì mở MD (R-2). FTM không có chuẩn hóa MAPID nào; chuyển dữ liệu MAPID cũ của HSA là của HSA-D (R-5).
- Quay lui: tắt cờ biểu mẫu là trở về MD cho mọi môn; dữ liệu ghi bởi MT/DS đọc được bằng MD. Hai mã kiểm tra mới chỉ thêm lỗi, gỡ là xong.

## 9. Yêu cầu phi chức năng

| Loại | Yêu cầu | Kiểm thế nào |
|---|---|---|
| Quyền | Không đổi: bước Cấu trúc cần quyền sửa bài như hôm nay; máy chủ chặn công bố theo R-12 | IT kiểm tra cấu hình và công bố, có và không có quyền như test hiện có |
| Hiệu năng | Chọn biểu mẫu và kiểm tra khớp là hàm thuần chạy trên dữ liệu đã nạp, không gọi request. Mở một môn bằng MT hoặc DS thật không gọi nhiều request hơn mở cùng môn bằng MD, cùng fixture, cùng trạng thái cache; không tính request do người dùng chủ động mở hộp chọn (MAPID, nguồn). Không đặt ngưỡng thời gian cứng | CR-1: test router chọn biểu mẫu không gọi request. CR-2, CR-3: test component đếm request khi mở MT, DS thật so với MD |
| API | OpenAPI không đổi | So JSON OpenAPI đã chuẩn hóa xuất từ baseline và từ head của task bằng cùng profile |
| Ngôn ngữ, truy cập | Như R-15; nhãn ô gắn `label`, lỗi tại ô có `aria-describedby`, chip không chỉ dựa vào màu, focus tới ô lỗi | Test component, kiểm tra giao diện |

## 10. Quyết định

| Mã | Quyết định | Ai, khi nào | Lời gốc | Thay cho |
|---|---|---|---|---|
| D-1 | Mã (giá trị) của 6 nhóm danh mục cố định là bất biến và là căn cứ chọn biểu mẫu. Các trường khác sửa được theo quy tắc của CL (tên, thứ tự…); FTM không thêm yêu cầu cho danh mục | M.Trường, 2026-11-19 (AD-34); quy tắc sửa trường theo đặc tả CL | "mã/giá trị không sửa, để lấy làm căn cứ cho các dạng form" | — |
| D-2 | Giá trị admin thêm sau dùng biểu mẫu mặc định | M.Trường, 2026-11-19 | "2 được thêm, theo form mặc định (form hiện tại)" | — |
| D-3 | Danh mục giữ như hiện tại, kể cả bốn cặp môn trùng; bảng đăng ký tra cả hai mã vào một mục ở kỳ có mã đó | M.Trường, 2026-11-19 (AD-35) | "danh mục như hiện tại là ổn đó" | — |
| D-4 | Dọn trường, giao diện gọn | M.Trường, 2026-11-19 | "dọn các field k cần thiết đi nhé. UX simple đi. đừng phức tạp quá" | — |
| D-5 | Câu 1–5 chốt theo tài liệu BA (R-3, R-8, R-9, R-10, R-12) | M.Trường, 2026-11-19 (AD-61) | "Từng câu ưu tiên mô tả trong tài liệu. Nếu k được thì hỏi t" | Đề xuất ban đầu của Advisor |
| D-6 | Ẩn khối lớp của Sử, Địa, GDKTPL, Anh (phương án A); phạm vi từng kỳ, bề mặt và dữ liệu Bắt buộc theo G1-1 (A) | M.Trường, 2026-11-19 (AD-61a) | "A" | K9, H15 của 2B ở biểu mẫu theo môn |
| D-7 | UNI-1704 (UNI-2758) ra khỏi FTM: lane HSA-D làm theo bản BA 23:52, `develop` trước rồi forward-merge; MT của HSA nhúng thành phần của HSA-D | M.Trường 2026-11-19 23:55 (AD-67); Advisor AD-68 | "Còn plan thì cứ làm hết rồi deploy" | D-7 bản 3 (gộp UNI-1704 vào task Ma trận môn) |
| D-9 | G1: G1-2, G1-3, G1-4, G1-8 do UNI-1704 bản BA 23:52 / AD-68 trả lời; G1-1, G1-5, G1-6, G1-7, G1-9 theo đề xuất (A) | M.Trường 2026-11-19 23:55 (AD-67) | "Còn plan thì cứ làm hết rồi deploy" | Mục 12 bản 3 |
| D-8 | Giới hạn R-12 làm cả ở máy chủ | Theo D-5 và RULES.md | — | "chỉ ở web, máy chủ để backlog" |

Các điểm mở của phân tích khoảng cách chốt theo tài liệu:

| Điểm | Chốt | Căn cứ |
|---|---|---|
| Q-07 Độ khó ở mức dòng | Mục "Độ khó" trong Thêm điều kiện, ghi `block.matrix` `SEPARATE` | HSA §5b.3 "mức độ", HSCA §5.3 |
| Q-10 Thêm, tách dòng | Chỉ H-SCA Anh và V-ACT Logic/Suy luận, luật tách ở R-7 | HSCA §6.5, VACT §6.2; tài liệu kỳ khác không mô tả |
| Q-12 Phần còn lại của bảng chỉ tiêu | QDA, BCA, V-ACT mở (R-6); HSA theo quy tắc MAPID của HSA-D (R-5) | VACT dòng 62; QDA dòng 95, 144; BCA §7b |
| Q-13 Bài ôn của kỳ thêm sau | ON/MD theo `buildMode` và dữ liệu bài ôn, không theo mã | Hành vi hiện tại `rendersReviewPanel` |
| Q-14 SP2E Toán Phần II | DS cho đổi Câu đơn ↔ Câu chùm, không sửa mẫu | SP §5.1 |

## 11. Giả định cần xác nhận

Hai giả định review đồng ý giữ ở đây. Các giả định review không đồng ý đã chuyển thành câu hỏi G1-6, G1-7; giả định về bài đang ở MD đã sửa theo review và ghi vào R-2.

| Giả định | Vì sao thấy an toàn | Xác nhận |
|---|---|---|
| Quy tắc MAPID chỉ áp cho HSA; QDA, BCA, V-ACT giữ Phạm vi kiến thức + Thêm chỉ tiêu chủ đề (R-6) | UNI-1704 là lỗi của HSA; tài liệu QDA, BCA, V-ACT mô tả hai khái niệm riêng và Phần còn lại mở | Đã xác nhận: HSA-D Q4, AD-68 |
| R-12 chặn ở bước kiểm tra cấu hình (chặn công bố), không chặn lưu nháp qua API | Mọi luật cấu trúc khác của 2B chạy ở bước kiểm tra; lưu nháp dở dang vẫn cần được (HSA §9, QDA §9). Review đồng ý với điều kiện loại bài ôn (R-12) và kết quả kiểm tra cũ không lọt (AC-58) | |

## 12. Quyết định G1

G1 đạt ngày 2026-11-19. M.Trường 23:55: *"Fix xong hết các task uniworkos hôm nay thì deploy demo những phần đó. Còn plan thì cứ làm hết rồi deploy."* (AD-67). Bốn câu về MAPID của HSA do bản BA mới của UNI-1704 (23:52) và AD-68 trả lời; các câu còn lại theo đề xuất. Câu nào muốn mở lại thì người duyệt yêu cầu; không chờ thêm câu trả lời nào.

| Câu | Quyết định | Căn cứ | Ảnh hưởng tới đặc tả |
|---|---|---|---|
| G1-1 Ẩn khối lớp | **A**, theo đề xuất | M.Trường 23:55; D-6 | R-11 như bảng; bài cũ Bắt buộc lớp ở chỗ ẩn mở MD (R-2) |
| G1-2 Phần câu còn lại của "Theo ma trận MAPID" | **Đã trả lời bởi UNI-1704 bản BA 23:52 / AD-68.** Không còn "phần còn lại" hai nhánh: quy tắc chỉ ràng buộc những gì nó nêu; câu chưa gắn MAPID vẫn được lấy (HSA-D Q2 A) | `1704-v2.md`; AD-68 | R-5 trỏ HSA-D; bảng A/B bỏ |
| G1-3 "Ma trận nâng cao" | **Đã trả lời bởi UNI-1704 bản BA 23:52 / AD-68.** Bốn loại quy tắc; không có lưới MAPID × lớp × độ khó, quy tắc không có lớp, độ khó (HSA-D Q3 A) | `1704-v2.md`; AD-68 | Mục 3 bỏ dòng "ma trận ô" |
| G1-4 Môn và màn áp luồng MAPID | **Đã trả lời bởi UNI-1704 bản BA 23:52 / AD-68.** Chỉ HSA, 6 môn dùng Ma trận môn (Toán, Lý, Hóa, Sinh, Sử, Địa), bài chuẩn; Văn, Anh, kỳ khác không đổi (HSA-D Q4) | AD-68 | R-5 |
| G1-5 Nhãn hai lựa chọn | **A**, theo đề xuất; nhãn do HSA-D giữ: "Toàn bài (không giới hạn MAPID)" / "Theo ma trận MAPID" | M.Trường 23:55; pha 0 v2 mục (d) | FTM không đặt nhãn riêng |
| G1-6 Chỉ tiêu dạng trả lời khi sửa Cấu trúc câu | **A**, theo đề xuất | M.Trường 23:55; AD-25, `answers.md:12` | R-3 như viết |
| G1-7 Bảng kết hợp V-SAT | **A**, theo đề xuất | M.Trường 23:55; `answers.md:8` | R-10 như viết |
| G1-8 Demo sớm luồng MAPID | **Đã trả lời bởi UNI-1704 bản BA 23:52 / AD-68.** HSA-D giao `develop` trước (AD-22) và lên demo khi xong; FTM lên demo cùng đợt 3 | AD-67, AD-68 | Không lát demo HSA trong FTM |
| G1-9 Tính điểm FTM | **A**: giữ 138 của phần đã duyệt, báo FTM riêng, ghi tổng phạm vi. Lúc duyệt là 23/161 tạm tính; plan v4 tách CR-2H nên nay 24/162 | Theo đề xuất, coordinator 2026-11-20 | Plan A5 |

### Bản câu hỏi đã gửi (lưu để đối chiếu)

Nguyên văn bản 3 gửi M.Trường; số và phương án ở đây là của lúc gửi.

#### G1-1. Ẩn "Khối lớp" ở kỳ nào, môn nào, màn nào

**Chuyện gì.** "Khối lớp" là phân bố số câu theo Lớp 10/11/12. Hôm 19/11 anh chọn A: ẩn Khối lớp của Lịch sử, Địa lí, GDKTPL (Giáo dục kinh tế và pháp luật), Tiếng Anh (`answers.md:13`, AD-61a). Phần này đã chốt, không hỏi lại. Tài liệu gốc (R-61, SP §7.1, THPT §3 bước 4) nói về SP2E, SPT, THPTQG. Câu này chỉ hỏi **phần em mở rộng ngoài R-61** theo tài liệu từng kỳ: thêm HSA Anh, và V-SAT Địa, Anh, Văn (VSAT §5.2 ghi "—" ở cột lớp), cùng cách xử lý bài cũ. HSA, QDA, BCA Sử, Địa và V-SAT Sử vẫn hiện vì tài liệu các kỳ đó có số liệu lớp. Ví dụ bài cũ: một đề đã lưu đang **bắt buộc** 5 câu Lớp 12 ở SP2E Lịch sử thì vẫn chỉnh được, bằng màn đầy đủ.

| Phương án | Nghĩa là | Cái giá |
|---|---|---|
| **A (đề nghị)** | Ẩn theo đúng tài liệu từng kỳ như bảng R-11. Chỉ ẩn ở màn soạn; bước Kiểm tra, Sinh thử vẫn hiện số lớp. Bài cũ nào đang bắt buộc theo lớp ở môn bị ẩn thì mở màn đầy đủ để người dùng thấy và sửa, không tự xóa, không tắt luật đang dùng | Một số bài cũ mở màn đầy đủ thay vì màn gọn |
| B | Ẩn đúng 4 môn ở mọi kỳ, kể cả HSA, QDA, BCA, V-SAT Sử, Địa | Giấu số liệu lớp mà tài liệu các kỳ đó có; người dùng không sửa được lớp ở các môn đó trên màn gọn |
| C | Chỉ ẩn ở SP2E, SPT, THPTQG (đúng phạm vi R-61 gốc) | HSA Anh, V-SAT Địa, Anh, Văn hiện hàng lớp trống mà tài liệu không dùng |

#### G1-2. "Theo ma trận MAPID" của HSA: phần câu còn lại lấy ở đâu

**Chuyện gì.** MAPID là mã chủ đề kiến thức. "Theo ma trận MAPID" là bảng: mỗi dòng một chủ đề + số câu tối thiểu. Ví dụ Toán 50 câu, bảng có Hàm số ≥ 10 và Hình học ≥ 8. Còn 32 câu: lấy từ đâu? BA viết "Nếu là toàn bài thì không giới hạn MAPID" (UNI-1704:3–6) nhưng chưa nói 32 câu còn lại ở "Theo ma trận" phải ở đâu; pha 0 HSA-D đề xuất A (`reports/HSA-D/phase0.md:64`). Bài cũ giữ nguyên trong cả hai phương án (bài nào không chắc giữ đúng nguồn thì mở màn đầy đủ, R-5).

| Phương án | Nghĩa là | Cái giá |
|---|---|---|
| **A (đề nghị), "đóng"** | Với bài soạn mới: 32 câu còn lại chỉ lấy trong Hàm số và Hình học. Muốn thêm chủ đề mà không đặt số thì thêm dòng số câu 0 | Bài cũ có bảng nhưng phạm vi để trống sẽ mở màn đầy đủ thay vì màn gọn |
| B, "mở" | Các dòng chỉ là số tối thiểu, 32 câu còn lại lấy từ cả môn | Giống QDA, BCA. "Theo ma trận" khi đó không giới hạn nguồn, chỉ đặt số tối thiểu; bài cũ đã giới hạn phạm vi sẽ mở màn đầy đủ |

#### G1-3. "Ma trận nâng cao" trong UNI-1704 là gì

**Chuyện gì.** Tài liệu HSA §4b.3 mô tả mỗi dòng chủ đề kèm giới hạn lớp, độ khó; tài liệu đã đủ để chốt A. Ví dụ A: "10 câu Hàm số, chỉ lấy Lớp 12". Ví dụ B: "Hàm số × Lớp 12 × Vận dụng: 4 câu", số riêng cho từng ô.

| Phương án | Nghĩa là | Cái giá |
|---|---|---|
| **A (đề nghị)** | Bảng chỉ tiêu chủ đề đang có: mỗi dòng một nhóm MAPID + số câu tối thiểu, thêm giới hạn lớp và độ khó nếu cần; tối đa 8 dòng | Không thêm việc |
| B | Lưới mới MAPID × Lớp × Độ khó, mỗi ô một số câu | Công cụ mới, ngoài FTM: màn mới, phải nới giới hạn 8 dòng ở máy chủ, có thể làm bộ sinh nặng hơn; cần ước lượng riêng |

#### G1-4. Luồng MAPID mới áp cho môn nào của HSA, ở màn nào

**Chuyện gì.** Ảnh của BA chụp môn Toán (UNI-1704:1–8). HSA có 6 môn dùng Ma trận môn: Toán và 5 môn Khoa học (Lý, Hóa, Sinh, Sử, Địa; HSA §4, §6, §8). Câu này hỏi có mở rộng từ Toán sang 5 môn Khoa học không.

| Phương án | Nghĩa là | Cái giá |
|---|---|---|
| A | Chỉ HSA Toán | 5 môn Khoa học HSA dùng cách của QDA (Phạm vi + Thêm chỉ tiêu chủ đề): hai cách trên cùng một kỳ |
| **B (đề nghị)** | Cả 6 môn Ma trận môn của HSA, ở bài thi chuẩn (toàn bài, theo phần, theo môn). Nhóm lấy câu và bài ôn giữ như cũ | Dùng lại cùng màn, nhưng thêm kiểm thử nguồn và kiểm tra cho 5 môn Khoa học; một cách thống nhất trong HSA |
| C | Như B, thêm bài ôn và cấp nhóm | Mở lại phạm vi đã để ngoài FTM (màn bài ôn, màn nhóm của HSA-B), không chỉ là bật thêm; thêm việc và test, cần ước lượng lại |

#### G1-5. Nhãn của hai lựa chọn

**Chuyện gì.** BA viết "Toàn bài" (UNI-1704:4–6), nhưng khối này nằm trong một môn. Chữ BA đủ để chốt A; màn sẽ ghi thêm "Không giới hạn MAPID của môn đang soạn" để không hiểu là đổi cả đề.

| Phương án | Nghĩa là | Cái giá |
|---|---|---|
| **A (đề nghị)** | Giữ chữ BA: "Toàn bài" / "Theo ma trận MAPID" (en "Whole test" / "By MAPID matrix") | Không |
| B | "Toàn môn" / "Theo ma trận MAPID" (en "Whole subject" / "By MAPID matrix") | Không; chỉ khác chữ BA |

#### G1-6. Sửa số câu ở Ma trận môn thì "chỉ tiêu dạng trả lời" đổi thế nào

**Chuyện gì.** "Chỉ tiêu dạng trả lời" là số câu theo từng dạng (trắc nghiệm, điền đáp án, bộ câu hỏi) mà máy dùng để kiểm đề. Bài từ mẫu HSA mới ghi ba loại, ví dụ 32 trắc nghiệm đơn + 3 câu trong bộ câu hỏi + 15 điền; bài cũ ghi hai loại, ví dụ 35 trắc nghiệm (gồm 3 câu của chùm) + 15 điền (`V202611190100`: bài cũ giữ cách đếm cũ). Một số mẫu không có chỉ tiêu này. Các số chỉ minh họa. Màn gọn không hiện bảng chỉ tiêu, nên khi người dùng sửa số ở "Cấu trúc câu" máy có thể tự cập nhật (`answers.md:12`, AD-25).

Đã chốt từ nguồn: mở rồi lưu không sửa thì giữ nguyên; khi sửa thì chỉ tiêu phải khớp với số câu. Anh chỉ cần trả lời khi muốn khác A (tự chuyển bài cũ sang kiểu mới, hoặc tự tạo chỉ tiêu cho bài trước đây không có).

| Phương án | Nghĩa là | Cái giá |
|---|---|---|
| **A (đề nghị)** | Chỉ cập nhật khi người dùng sửa số, giữ đúng kiểu đang lưu: bài mới theo kiểu mới, bài cũ theo kiểu cũ, bài không có thì để không. Mở rồi lưu không sửa: giữ nguyên | Không đổi cách đếm của bài cũ |
| B | Sửa số thì chuyển luôn sang kiểu mới (ba loại) | Bài cũ đổi cách đếm; có thể làm hiện lỗi kiểm tra khác trước |
| C | Màn gọn không đụng chỉ tiêu dạng; lệch thì bước Kiểm tra báo, người dùng sửa ở màn đầy đủ | Người dùng phải sửa hai nơi, trái ý "UX simple" |

#### G1-7. Bảng kết hợp Lớp × Độ khó của V-SAT đặt ở đâu

**Chuyện gì.** Anh đã chốt: riêng V-SAT có bảng kết hợp, mở trong "Thêm điều kiện", không đặt ở từng dòng (`answers.md:8`, VSAT §5.1). Bảng kết hợp đặt số câu cho từng ô, ví dụ "Lớp 12 · Vận dụng: 10". Môn V-SAT Toán lưu thành 4 phần; với môn chỉ có một phần thì "từng phần" cũng là cả môn. Dữ liệu hiện có chỗ lưu bảng kết hợp **cho từng phần**, chưa có chỗ lưu **chung cho môn nhiều phần**. V-SAT còn đếm theo "lệnh hỏi" (một câu Đúng/Sai có 4 lệnh); phân bố theo lệnh chỉ dùng để đối chiếu (2B-HV H44), nên không có bảng kết hợp theo lệnh bật được cho cả môn. Câu này chỉ hỏi: có cần bảng chung cho môn nhiều phần ngay trong FTM không.

| Phương án | Nghĩa là | Cái giá |
|---|---|---|
| **A (đề nghị)** | "Thêm điều kiện" ở khối Phân bổ đối chiếu **của từng phần** mở bảng kết hợp. Cấp cả môn nhiều phần chỉ có bảng riêng từng chiều. Bảng theo câu mới bật được "Áp dụng khi sinh đề"; bảng theo lệnh chỉ đối chiếu | Không thêm việc, không đổi dữ liệu |
| B | Thêm bảng kết hợp chung cho môn nhiều phần | Mở phạm vi, không chỉ đổi vị trí: phải đổi mô hình dữ liệu và máy chủ; ngoài FTM, cần ước lượng lại |
| C | Bảng kết hợp ở từng dòng (màn sửa dòng) | Không có căn cứ trong tài liệu BA; nhiều bảng nhỏ |

#### G1-8. Có cần demo sớm luồng MAPID mới của HSA, hay chờ cả đợt 3

**Chuyện gì.** Bình thường luồng mới đến bản demo cùng cả đợt 3. Nếu anh có mốc demo sớm cho HSA thì cần chọn. Ghi chú kỹ thuật: sửa nóng chỉ được ủy quyền cho lỗi HSA (AD-22, AD-34).

| Phương án | Nghĩa là | Cái giá |
|---|---|---|
| **A (đề nghị)** | Chờ đợt 3: luồng mới lên demo cùng cả đợt | Không thêm việc; demo thấy luồng mới muộn hơn |
| B | Demo sớm riêng HSA: làm một bản nhỏ chỉ gồm luồng MAPID của HSA, không đổi màn các kỳ khác, không có luật giới hạn mới; duyệt và kiểm riêng | Thêm một lần triển khai và kiểm thử riêng (tạm ước khoảng 2 điểm); sau đó hợp nhất lại vào đợt 3 |
| C | Đưa nguyên hai phần việc đầu lên sớm | **Không khả thi**, chỉ giữ để đủ ba lựa chọn đã gửi: vượt ủy quyền sửa nóng (AD-22) và bật màn mới cho QDA, BCA, V-ACT khi chưa có luật máy chủ và nền danh mục |

#### G1-9. Tính điểm FTM vào tiến độ đợt 3 thế nào

**Chuyện gì.** Đợt 3 đã duyệt mẫu số 138 điểm (`TASK.md:217–219`). FTM sau review vòng 2 là **23 điểm tạm tính** (plan A5; vòng 1 ghi 22, tăng 1 ở phần luật máy chủ); chốt lại sau khi có câu trả lời G1.

| Phương án | Nghĩa là | Cái giá |
|---|---|---|
| **A (đề nghị)** | Giữ 138 cho phần đã duyệt, báo FTM riêng 23, ghi tổng phạm vi 161 (tạm tính) để thấy việc phát sinh | Báo cáo có hai dòng tiến độ; tỷ lệ của phần đã duyệt không đổi |
| B | Gộp: mẫu số mới 161 từ ngày anh duyệt, ghi ngày và quyết định | Tỷ lệ hoàn thành đợt 3 giảm một lần khi đổi mẫu số, dù việc đã làm không mất |

## Nhật ký review

| Vòng | Ngày | Người review | Chặn | Nên sửa | Nhỏ | Không nhận, lý do | Báo cáo |
|---|---|---|---|---|---|---|---|
| 1 | 2026-11-19 | Codex | 6 | 8 | 1 | S1-18 là phát hiện sai (review tự ghi): chỉ thêm ghi chú ở mục 5; S1-12, S1-13 chuyển G1 | `.thi360-dev/dot-3/review-ftm/codex-round1.md`, `fix-round1.md` |
| 2 | 2026-11-19 | Codex | 4 | 2 | 1 | Không; ghi chú G1 đã áp, chữ cái và đề nghị giữ nguyên | `.thi360-dev/dot-3/review-ftm/codex-round2.md`, `fix-round2.md` |
| 3 | 2026-11-20 | Codex | 3 | 2 | 0 | S3-02 không còn áp dụng (R-5 thay bằng tham chiếu HSA-D, AD-67/68); các điểm khác đã sửa | `.thi360-dev/dot-3/review-ftm/codex-round3.md`, `fix-round3.md` |
