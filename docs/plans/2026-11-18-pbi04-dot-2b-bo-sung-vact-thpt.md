# Đợt 2B, kế hoạch bổ sung: cấu trúc THPT và V-ACT

Ngày: 2026-11-18. Trạng thái: đã duyệt ngày 2026-11-18 sau hai vòng rà độc lập. Issue: UNI-2671. Đặc tả: `docs/specs/2026-11-18-pbi04-dot-2b-bo-sung-vact-thpt.md`. Bổ sung cho `docs/plans/2026-11-17-pbi04-dot-2b.md` (gọi tắt **Plan**).

Hiện trạng lúc viết (nhánh `integration/dot-2b` tại `f2fa1fc7`): BE-5 đã merge; BE-9 và FE-2 đang chạy.

## 1. Sổ tiến độ

**Mã issue:** BE-11 `UNI-2672`, BE-12 `UNI-2673`, BE-13 `UNI-2674`, BE-14 `UNI-2675`, BE-15 `UNI-2676`, FE-5 `UNI-2677`, G2b `UNI-2678`.

`scope_version` tăng từ 1 lên 2. Số task từ 17 lên 23. Tổng điểm từ **100** lên **127**. Phần đã nghiệm thu giữ 63 điểm (đã gồm BE-5), nên tiến độ hiển thị giảm từ 63% xuống 49,6%.

**Task mới (21 điểm)**

| Task | Tên | Nhóm | Điểm |
|---|---|---|---|
| BE-11 | Hợp đồng mở rộng: cờ mẫu, `percent`, `everyChildInScope` | Nền | 2 |
| BE-12 | Bộ giải: chùm cỡ linh hoạt, mọi câu con thuộc chủ đề | Bộ giải | 5 |
| BE-13 | Mẫu THPTQG, V-ACT, phạm vi mới, bài ôn | Bài thi | 6 |
| BE-14 | Nhãn và dữ liệu câu hỏi mẫu cho THPTQG, V-ACT | Nền | 2 |
| BE-15 | Kiểm tra: chặn công bố, khóa số câu, chờ mapping | Kiểm tra | 3 |
| FE-5 | Web admin: bước 1 cho V-ACT, THPTQG | Web admin | 3 |

**Gộp vào task chưa mở (6 điểm)**

| Task | Điểm | Phần thêm | Yêu cầu |
|---|---|---|---|
| FE-3a | 7 → 8 | Chùm cỡ linh hoạt (chỉ nhập tổng câu con); ô `everyChildInScope`; dòng "4 câu · 16 ý · tối đa 4 điểm" | R-107, R-116, R-120 |
| FE-3b | 6 → 9 | Hai cột Số câu và Tỷ lệ; Phần còn lại; quy đổi khi đổi tổng; Chờ mapping; tiến độ phân bổ; nhãn "ít nhất"; ô số câu của mục chỉ đọc khi mẫu khóa số câu | R-118, R-121, R-122, R-123, R-126 |
| FE-4 | 6 → 7 | Cột Chênh lệch; điểm từng câu; "Chưa có dữ liệu đối chiếu"; câu chữ của ba mã kiểm tra mới | R-112, R-122, R-124 |
| QA | 6 → 7 | E2E V-ACT FULL, THPTQG Tiếng Anh, Tin học từng nhánh, Ngữ văn không công bố được; danh sách báo BA | Toàn bộ |

Cộng theo nhóm sau bổ sung: Nền 25, Bộ giải 15, Kiểm tra 18, Bài thi 21, Web admin 41, Nghiệm thu 7. Tổng 127.

BE-9, FE-2 **không đổi phạm vi và điểm**. BE-5 đã đóng, không nhận thêm việc.

## 2. Hợp đồng task

Mọi task theo nghiệm thu chung ở Plan A7 và quy tắc migration ở Plan B2. Đường dẫn Java dưới đây tính từ `backend/src/main/java/vn/unicom/thi360/`.

### BE-11 · Hợp đồng mở rộng

**Nhóm:** Nền. **Điểm:** 2. **Phụ thuộc:** không có; mở được ngay. **Spec:** mục 6.

**Sở hữu:** `exam/domain/{TopicQuota,KnowledgeScope,ExamTemplate}.java`, `exam/domain/ExamStructureOps.java`, `exam/domain/ExamStructureValidator.java`, `exam/api/ExamTemplateResponse.java`, `exam/application/ExamTemplateService.java` (chỉ `toResponse`), một migration mới trong `db/migration/exam`, `packages/api-client/openapi.json`, `packages/api-client/src`.

**Sản phẩm:** hai cột `publishable`, `fixed_counts`; hai trường API; `TopicQuota.percent`; `KnowledgeScope.everyChildInScope`. `TopicQuota` giữ hàm dựng 9 tham số cũ; `KnowledgeScope` giữ hàm dựng 2 tham số cũ và đưa trường mới vào `equals`, `hashCode`.

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Đọc danh sách mẫu hiện có | Mọi mẫu có `publishable = true`, `fixedCounts = false` |
| 2 | Lưu `TopicQuota.percent` = 101 hoặc âm | 422 `EXAM_STRUCTURE_INVALID`, `fieldErrors` chỉ tới `sections.topicQuotas` hoặc `blocks.topicQuotas` |
| 3 | Lưu rồi đọc lại `percent`, `everyChildInScope` | Giữ nguyên giá trị, kể cả sau `ExamStructureOps` (nơi duy nhất dựng lại `TopicQuota`) |
| 4 | Tài liệu cấu trúc cũ không có hai trường | Đọc được; ghi lại không sinh thêm trường; hành vi không đổi |
| 5 | Lưu cấu trúc chỉ đổi `everyChildInScope` | Phần đó nằm trong danh sách phần đã đổi; đơn vị kiểm tra của môn chuyển "Cần kiểm tra lại" |
| 6 | Biên dịch và chạy bộ test backend hiện có trên nền BE-11 | Xanh, không sửa mã gọi hàm dựng cũ |

### BE-12 · Bộ giải: chùm cỡ linh hoạt, mọi câu con thuộc chủ đề

**Nhóm:** Bộ giải. **Điểm:** 5. **Cứng:** BE-11. **Mềm:** BE-9 (cùng sửa `SolverInputBuilder`). **Spec:** H28, H29.

**Sở hữu:** `exam/domain/solver/**`, `exam/application/SolverInputBuilder.java`, `exam/application/ExamQuestionUsageProvider.java`, `exam/application/ExamCheckRules.java` (chỉ hai chỗ báo `SET_SIZE_MISMATCH`), `questionbank/application/{QuestionSourceAdapter,AvailableQuestionCountService}.java`, `questionbank/spi/QuestionSourcePort.java`, `questionbank/api/AvailableQuestionCountController.java`, `packages/api-client` (sinh lại vì thêm tham số đếm câu).

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Nhóm 18 câu con, nguồn có chùm cỡ 3, 4, 5 | `VALID`; đề chứng minh gồm các chùm nguyên, tổng đúng 18 câu con |
| 2 | Nhóm 5 câu con, nguồn chỉ có chùm cỡ 3 | Không đạt, mã `SET_SHORTAGE` tại nhóm; không cắt chùm |
| 3 | Nhóm có `setCount`, `setSize` | Bộ test bộ giải hiện có vẫn xanh, không đổi kết quả |
| 4 | Chùm cỡ linh hoạt 18 câu con cùng chỉ tiêu lớp và độ khó `REQUIRED` ở cấp mục | `VALID`; đếm lớp và độ khó trên đề chứng minh khớp đúng chỉ tiêu |
| 5 | Kiểm tra cấu trúc có nhóm chùm chỉ có `questionCount` | Không có `SET_SIZE_MISMATCH` |
| 6 | Nhóm chùm có `setCount` mà thiếu `setSize`, hoặc tích khác `questionCount` | Vẫn báo `SET_SIZE_MISMATCH` như trước |
| 7 | `everyChildInScope = true`, chùm 2 câu Xác suất + 1 câu Hình học, phạm vi Xác suất | Chùm bị loại khỏi nguồn và khỏi số đếm câu |
| 8 | `everyChildInScope` không đặt | Chùm trên vẫn được nhận (luật hiện tại: gốc hoặc một câu con khớp) |
| 9 | Đo `perf` đề 120 câu có nhóm chùm cỡ linh hoạt 18 câu con | Trong hạn 30 giây của bộ giải; ghi số đo vào báo cáo |

### BE-13 · Mẫu THPTQG, V-ACT, phạm vi mới, bài ôn

**Nhóm:** Bài thi. **Điểm:** 6. **Cứng:** BE-11. **Mềm:** BE-9, BE-12, BE-14 (chỉ nộp khi cả ba đã merge). **Spec:** mục 4, 5.

**Sở hữu:** `backend/src/test/resources/templates/{thptqg,vact}.templates.mjs` (mới), `legacy.templates.mjs` (bỏ THPTQG, V-ACT), `build.mjs` (thêm hai cột cờ), `expected-totals.json`, một migration mới, `db/devdata/R__seed_exam_dev_data.sql` (`scopes`), `db/devdata/R__seed_test_exam_data.sql`, `scripts/generate_test_exam_seed.py`, `exam/application/ExamService.java` (chỉ điều kiện ở `assertTemplateMatches`, dòng 1182), `ExamTemplateTotalsIT`, `ExamCreateIT`, `TestExamSeedIT`, `SampleQuestionCoverageIT`.

Vì sao chờ: `SampleQuestionCoverageIT` duyệt mọi mẫu `STANDARD`, nên mẫu mới chỉ xanh khi dữ liệu của BE-14 đã có; đề `TEST-VACT-01` chỉ đạt khi bộ giải của BE-12 đã có; BE-9 cũng sửa `ExamTemplateTotalsIT`, `expected-totals.json`.

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Toán THPTQG | 3 phần, 12 + 4 + 6 = 22 câu, 3 + 4 + 3 = 10 điểm, 90 phút, `SHARED` |
| 2 | Lý, Hóa, Sinh, Địa | 3 phần, 18 + 4 + 6 = 28 câu, 4,5 + 4 + 1,5 = 10 điểm, 50 phút |
| 3 | Sử, GDKTPL, hai môn Công nghệ | 2 phần, 24 + 4 = 28 câu, 6 + 4 = 10 điểm, 50 phút |
| 4 | Nhóm Đúng/Sai của chín môn trên | `itemShape.statements = 4`, có `expectedScoring` |
| 5 | THPTQG Tiếng Anh, đọc `GET structure` của bài tạo từ mẫu | 6 nhóm đúng thứ tự; `positionRange` 1–6, 7–12, 13–17, 18–22, 23–30, 31–40; điểm nhóm 1,5 / 1,5 / 1,25 / 1,25 / 2 / 2,5; tổng 40 câu, 10 điểm |
| 6 | THPTQG Tin học | Không đổi so với mẫu hiện tại: 30 vị trí, 28 câu, 10 điểm |
| 7 | THPTQG Ngữ văn | `publishable = false`; 7 câu, 10 điểm, 120 phút; Đọc hiểu có độ khó `REFERENCE` 2/2/1 |
| 8 | Tạo Địa lí theo phần, chọn `P3`, có nhập thời lượng | 6 câu, thang 1,5, `RAW` |
| 9 | Tạo bài theo phần THPTQG hoặc bài riêng V-ACT không nhập thời lượng | Bị từ chối; `fieldErrors` có `durationMinutes` mã `EXAM_DURATION_REQUIRED` |
| 10 | Tạo bài ôn THPTQG | Nạp `THPTQG_REVIEW` |
| 11 | `VACT_FULL` | 3 phần 60/30/30 câu, 600/300/300 điểm; 5 mục theo thứ tự 30/30/30/12/18 (cộng dồn ra 1–30, 31–60, 61–90, 91–102, 103–120); 120 câu, 150 phút, 1.200 điểm; `fixed_counts = true` |
| 12 | Bảng tham khảo V-ACT | Đúng từng số ở spec mục 5.1, 5.2; mọi bảng `REFERENCE`; Tiếng Việt có 8 chỉ tiêu `nodeIds` rỗng |
| 13 | Kiểm tra `TEST-VACT-01` | `VALID`; Logic và Suy luận nằm chung một đơn vị kiểm tra của `SCIENTIFIC_THINKING` |
| 14 | Tạo V-ACT `PART` `LOGIC` và `SUBJECT` `ANH` | 201; lần lượt 12 câu 120 điểm và 30 câu 300 điểm |
| 15 | Tạo `VACT_REVIEW` | Nguồn có `everyChildInScope = true` |
| 16 | `ExamCreateIT` | V-ACT `SUBJECT` `TOAN` tạo được; ca bị cấm đổi sang THPTQG `FULL`, vẫn 422 `EXAM_SCOPE_NOT_ALLOWED` |
| 17 | `SampleQuestionCoverageIT`, `ExamTemplateTotalsIT` | Xanh với mọi mẫu `STANDARD`, gồm mẫu mới |
| 18 | Bài THPTQG Tiếng Anh, nhóm 6 giới hạn vào MAPID chỉ có các bài đọc dưới 10 câu con | Kiểm tra không đạt, `SET_SHORTAGE` tại nhóm 6 (cần 1 chùm, có 0), dù tổng câu con của nguồn lớn hơn 10 |

### BE-14 · Nhãn và dữ liệu câu hỏi mẫu

**Nhóm:** Nền. **Điểm:** 2. **Phụ thuộc:** không có; mở được ngay, làm theo spec mục 4 và 5. **Spec:** R-125.

**Sở hữu:** `db/devdata/R__seed_questionbank_dev_data.sql`, `db/devdata/R__seed_catalog_dev_data.sql` (6 nhãn của D9).

Mỗi dòng nghiệm thu bằng một ca đếm nguồn qua `QuestionSourcePort` (cùng kiểu với `SampleQuestionCoverageIT`). Việc "mẫu kiểm tra đạt" nghiệm thu ở BE-13 dòng 17.

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Danh mục `QUESTION_TAG` | Có 6 nhãn mới, tên vi và en |
| 2 | Tiếng Anh, câu 0,25 điểm | Có chùm `ANH_DOC_DIEN` cỡ 6 (ít nhất 2), `ANH_DOC_DIEN_CAU` cỡ 5, `ANH_DOC_HIEU` cỡ 8 và cỡ 10; đủ 5 câu đơn nhãn sắp xếp |
| 3 | Câu V-ACT, 10 điểm, lọc theo `competencyCodes` | `VACT-NN-TV` ≥ 30 câu đơn; `VACT-NN-TA` đủ 5 câu đơn cho mỗi nhãn Điền khuyết, Phát hiện lỗi sai, Diễn đạt tương đương, và có bài đọc 7, 8 câu con; `VACT-TOAN` ≥ 12 câu đơn và các chùm ghép được tổng 18; `VACT-TD-LOGIC` ≥ 4 chùm cỡ 3; `VACT-TD-SL` ≥ 6 chùm cỡ 3 |
| 4 | Chùm Logic theo nhãn loại dữ kiện | Mỗi nhãn `TD_*` có ít nhất 1 chùm cỡ 3 |
| 5 | Câu Đúng/Sai THPTQG, 1 điểm, lọc `itemShape` 4 mệnh đề | Mỗi môn có phần Đúng/Sai có ít nhất 4 câu |

### BE-15 · Kiểm tra cho THPTQG, V-ACT

**Nhóm:** Kiểm tra. **Điểm:** 3. **Cứng:** BE-9, BE-11. **Mềm:** BE-12, BE-13. **Spec:** mục 6.

Dòng 10 và 11 không cần mã mới nên 3 điểm vẫn đủ: `ExamCheckRules.topicQuotas` không cộng theo cột, nhưng bộ giải không ghép được thì `ExamCheckSolverIssues` đổi `SUBJECT_QUOTA_INFEASIBLE` hoặc `NO_JOINT_SOLUTION` thành mã `SUBJECT_QUOTA_INFEASIBLE`; hết hạn 30 giây thì thành `OVERLAP_UNCONFIRMED`, cũng không đạt. Kết luận này đọc từ mã, chưa chạy; nếu ca test cho kết quả khác thì báo Advisor.

**Sở hữu:** `exam/application/ExamCheckRules.java`, `exam/application/{DefaultExamChecker,ExamCheckUnitStates}.java` (chỉ phần đưa cờ của mẫu vào luật), `exam/application/ExamCheckPathPresenter.java`, `messages*.properties`, `TestExamSeedIT` (chuyển `TEST-THPTQG-02` khỏi danh sách hợp lệ).

Vì sao chờ BE-9: BE-9 đang nối `COMBINATION_LIST_CHANGED` và ba mã thiếu nguồn vào chính các tệp kiểm tra này.

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Kiểm tra bài THPTQG Ngữ văn theo môn | Không đạt, có `TEMPLATE_NOT_PUBLISHABLE`; gọi công bố bị từ chối; lưu nháp vẫn được |
| 2 | Bài ôn hoặc bài theo phần THPTQG Ngữ văn | Cũng có `TEMPLATE_NOT_PUBLISHABLE` |
| 3 | `TEST-THPTQG-02` trong `TestExamSeedIT` | Chuyển sang nhóm không hợp lệ với đúng mã trên |
| 4 | Bài từ `VACT_FULL` đổi Logic từ 12 thành 10 câu | Lưu được; kiểm tra có `STRUCTURE_COUNT_LOCKED`, `location` chỉ tới mục Logic |
| 5 | Bài từ `VACT_FULL` đổi phân bổ độ khó trong tiểu mục | Không có `STRUCTURE_COUNT_LOCKED` |
| 6 | Bài từ `VACT_PART_LOGIC` đổi 12 thành 10 câu | Không có `STRUCTURE_COUNT_LOCKED` |
| 7 | Chỉ tiêu `REQUIRED`, `nodeIds` rỗng | Kiểm tra có `TOPIC_QUOTA_MAPPING_MISSING`, `location` chỉ đúng mục hoặc nhóm chứa dòng; sinh thử trả `FAILED` kèm mã này |
| 8 | Chỉ tiêu `REFERENCE`, `nodeIds` rỗng | Không lỗi; kiểm tra và sinh thử chạy bình thường |
| 9 | Mẫu HSA, QDA, BCA, SP2E, SPT (`ExamTemplateCheckIT`, `TestExamSeedIT`) | Kết quả kiểm tra không đổi |
| 10 | Tiểu mục có độ khó `REQUIRED` (ví dụ NB 5) và các chỉ tiêu mạch `REQUIRED` đã gắn mã, lọc theo mức, có tổng một mức vượt số đó (NB 6); nguồn đủ cho từng dòng riêng lẻ (V §4.1) | Luật hiện có, BE-15 chỉ thêm ca test: kiểm tra không đạt, mã `SUBJECT_QUOTA_INFEASIBLE` (ERROR) mang `subjectCode` của tiểu mục; không có `TOPIC_QUOTA_INFEASIBLE` |
| 11 | Như dòng 10 nhưng tổng một mức của các dòng mạch nhỏ hơn số của tiểu mục, và các dòng không phủ hết số câu | Kiểm tra đạt: chỉ tiêu mạch là "ít nhất" (K6, D13) |

### FE-5 · Bước 1 cho V-ACT, THPTQG

**Nhóm:** Web admin. **Điểm:** 3. **Cứng:** BE-11, G2b. **Mềm:** BE-13, FE-2 (cùng chạm `types.ts`, `api-mapping.ts`, tệp thông điệp). **Spec:** R-109, R-111, R-118, R-119, R-123.

**Sở hữu** (trong `apps/web-admin/src/features/exams/`): `create-exam-schema.ts`, `create-exam-overview.ts`, `components/create-exam-page.tsx`, `components/create-exam-scope-fields.tsx`, `components/create-exam-fields.tsx`, `components/exam-overview-table.tsx`, `__tests__/create-step1.test.tsx`, `__tests__/list-create-schema.test.ts`; `types.ts` và `api-mapping.ts` chỉ thêm hai trường `publishable`, `fixedCounts`; khóa thông điệp mới vi và en (tên bốn phần V-ACT, thông báo Ngữ văn, hai cột tỷ lệ).

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | V-ACT, chọn Theo phần | Có Sử dụng ngôn ngữ, Tư duy khoa học, Logic và phân tích số liệu, Suy luận khoa học; không hiện mã thô |
| 2 | V-ACT, chọn Theo môn | Có Tiếng Việt, Tiếng Anh, Toán học |
| 3 | V-ACT FULL | Ô số câu của 5 tiểu mục chỉ đọc; hiện tỷ lệ trong phần 50/50/100/40/60% và tỷ lệ toàn bài 25/25/25/10/15% |
| 4 | Bài riêng V-ACT | Ô số câu sửa được; để trống thời lượng thì báo lỗi ở ô thời lượng, không gửi yêu cầu tạo |
| 5 | THPTQG, chọn Ngữ văn | Hiện đúng câu "Toàn bộ môn Ngữ văn là tự luận – tạm bỏ qua trong V1"; vẫn tạo được bài nháp |
| 6 | THPTQG, Theo phần | Chọn môn rồi chọn phần; Toán có 3 phần, Lịch sử có 2 phần |
| 7 | Đổi ngôn ngữ, đổi sáng tối | Không lẫn ngôn ngữ, không vỡ giao diện |

### Phần thêm của task chưa mở

Nghiệm thu dưới đây cộng vào nghiệm thu của task gốc trong Plan D. Ca nào không ghi khác thì là vitest.

**FE-3a** (R-107, R-116, R-120). Mọi ca nằm trong tệp FE-3a sở hữu theo hợp đồng lane (`block-editor.tsx`, `knowledge-scope-field.tsx`, tệp biểu mẫu nhóm).

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Nhóm chùm, chọn chỉ nhập tổng câu con, nhập 18 | Gửi `questionCount = 18`, `setCount` và `setSize` trống; không hiện ô Số chùm, Cỡ chùm |
| 2 | Chuyển lại sang khai số chùm 6 × 3 | Gửi `setCount = 6`, `setSize = 3` |
| 3 | Phạm vi kiến thức có chọn MAPID ở nhóm chùm | Có ô "mọi câu con thuộc chủ đề"; bật thì gửi `everyChildInScope = true`; bài từ `VACT_REVIEW` bật sẵn |
| 4 | Nhóm Đúng/Sai 4 câu, 4 mệnh đề, 1 điểm mỗi câu | Dòng nhóm ghi "4 câu · 16 ý · tối đa 4 điểm"; đổi thành 3 câu thì ghi "3 câu · 12 ý · tối đa 3 điểm" |

**FE-3b** (R-118, R-121, R-122, R-123, R-126)

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Chỉ tiêu chủ đề trên 30 câu, nhập Tỷ lệ 10 | Số câu 3; hiện "Phần còn lại 27 câu"; gửi `count = 3`, `percent = 10`; dòng Phần còn lại không gửi |
| 2 | Nhập Số câu 12 trên 30 | Tỷ lệ 40%; `percent` trống |
| 3 | Độ khó 25/50/25 trên 30 câu | Số câu 8/15/7; tỷ lệ thực 26,7% / 50% / 23,3% |
| 4 | Sáu dòng 3 câu trên 18 | Mỗi dòng 16,7%; tổng ghi 18/18 câu |
| 5 | Bảng phân bổ toàn bộ số câu, tổng tỷ lệ khác 100% | Báo lỗi ở bảng, không lưu |
| 6 | Đổi tổng từ 30 xuống 20 | Bảng nhập theo tỷ lệ hiện số câu mới và chờ xác nhận; bảng nhập theo số câu giữ số cũ và báo thiếu hoặc vượt |
| 7 | 12 câu đơn + 15 câu con trên 30 | "Đã phân bổ 27/30 · Còn thiếu 3 câu"; sửa thành 12 + 18 thì "Đã phân bổ 30/30"; 12 + 20 thì báo vượt 2 câu |
| 8 | Sau khi kiểm tra môn đạt | Hiện "Đủ nguồn theo ma trận"; trước đó không hiện |
| 9 | Bảng ở cấp mục và bảng ở cấp nhóm | Tỷ lệ chia cho số câu của mục, của nhóm tương ứng |
| 10 | Dòng chỉ tiêu chưa gắn MAPID | Lưu được, ghi "Chờ mapping"; gắn mã thì hết nhãn, giữ số câu và các bảng lớp, độ khó |
| 11 | Chỉ tiêu chủ đề của bài V-ACT, THPTQG | Cột Số câu ghi "ít nhất" |
| 12 | Bài từ mẫu có `fixedCounts` | Ô số câu của mục ở bước 2 chỉ đọc; phân bổ bên trong mục vẫn sửa được |

**FE-4** (R-112, R-122, R-124)

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Bảng đối chiếu, yêu cầu 12, thực tế 10 | Ba cột Yêu cầu / Thực tế / Chênh lệch; ô chênh lệch ghi −2; bằng nhau ghi 0 |
| 2 | Đề thử THPTQG Toán | Mỗi câu hiện điểm tối đa lấy từ `points`: trắc nghiệm 0,25; Đúng/Sai 1; trả lời ngắn 0,5 |
| 3 | Chỉ tiêu `REFERENCE` có `nodeIds` rỗng (tra theo id chỉ tiêu trong cấu trúc) | Ô Thực tế và Chênh lệch ghi "Chưa có dữ liệu đối chiếu" |
| 4 | Ba mã `TEMPLATE_NOT_PUBLISHABLE`, `STRUCTURE_COUNT_LOCKED`, `TOPIC_QUOTA_MAPPING_MISSING` (thêm vào 15 mã của FU-FE2-2 trong hợp đồng FE-4) | Mỗi mã có câu chữ vi và en riêng, không rơi về câu của `INFO_MISSING`; bấm `TOPIC_QUOTA_MAPPING_MISSING` chọn đúng mục hoặc nhóm ở bước 2 |

**QA**

| # | Kịch bản (E2E) | Kết quả |
|---|---|---|
| 1 | V-ACT FULL: tạo, kiểm tra, sinh thử, công bố | Kiểm tra đạt; đề thử 120 câu; công bố được |
| 2 | THPTQG Tiếng Anh: tạo, sinh thử | Đề thử 40 câu, 6 nhóm đúng dải câu |
| 3 | THPTQG Tin học: sinh thử từng nhánh | Mỗi nhánh 28 câu, 10 điểm |
| 4 | THPTQG Ngữ văn: tạo, lưu, kiểm tra, công bố | Lưu được; kiểm tra có `TEMPLATE_NOT_PUBLISHABLE`; không công bố được |
| 5 | Báo cáo cuối đợt | Có đủ 8 mục của spec bổ sung mục 9 |

## 3. Pipeline

**Bổ sung vào bảng Plan C1**

| Task | Phụ thuộc cứng | Phụ thuộc mềm | Thời điểm mở |
|---|---|---|---|
| BE-11 | | | Ngay |
| BE-14 | | | Ngay |
| BE-12 | BE-11 | BE-9 | Sau BE-11; chỉ nộp sau BE-9 |
| BE-13 | BE-11 | BE-9, BE-12, BE-14 | Sau BE-11; chỉ nộp sau BE-9, BE-12, BE-14 |
| BE-15 | BE-9, BE-11 | BE-12, BE-13 | Sau BE-9 và BE-11; chỉ nộp sau BE-12, BE-13 |
| FE-5 | BE-11, G2b | BE-13, FE-2 | Sau BE-11 và G2b; chỉ nộp sau BE-13, FE-2 |
| FE-3a (phần gốc, 7 điểm) | FE-2 | BE-4a, BE-7 | Như cũ: ngay khi FE-2 merge |
| FE-3a (phần thêm, 1 điểm) | FE-2, **BE-11**, **G2b** | **BE-12** | Khi Advisor gửi ghi chú hợp đồng; chỉ nộp sau BE-12 |
| FE-3b | FE-3a (phần gốc), **BE-11**, **G2b** | BE-4b, BE-5, BE-7, **BE-13** | Như cũ |
| FE-4 (phần gốc, 6 điểm) | FE-2 | BE-5, BE-8, FE-3a | Như cũ: ngay khi FE-2 merge |
| FE-4 (phần thêm, 1 điểm) | FE-2, **G2b** | **BE-15** | Khi Advisor gửi ghi chú hợp đồng; chỉ nộp sau BE-15 |
| QA | tất cả | | Cuối |

**FE-3a và FE-4 không chờ phần bổ sung.** Phần gốc của hai lane mở sau FE-2 như Plan C1 và được nộp, tính điểm riêng (7 và 6 điểm); hợp đồng lane đã ghi "chưa làm phần bổ sung cho tới khi Advisor gửi ghi chú". Phần thêm cần kiểu `everyChildInScope`, `fixedCounts` trong `api-client` (BE-11) và bản vẽ G2b. Nếu BE-11 và G2b xong trước khi phần gốc nộp thì gộp chung một PR; nếu chưa thì phần thêm là PR thứ hai trên cùng issue, và 1 điểm thêm chỉ tính khi PR đó merge. FE-3b chỉ chờ phần gốc của FE-3a.

**BE-9 (ADV:BE-9:2, K13):** thêm mã tổ hợp thì giải lại mọi đơn vị. Không ảnh hưởng phần bổ sung: mẫu V-ACT và THPTQG không có phần lấy mã từ Global List, và không dòng nghiệm thu nào ở đây dựa vào việc dùng lại đơn vị sau khi đổi danh sách mã.

Phụ thuộc mềm của BE-13 và BE-15 đi một chiều (BE-14 → BE-13 → BE-15, BE-12 → BE-13), không có vòng. Thứ tự nộp của các lane backend mới: BE-11, BE-14 (bất kỳ lúc nào) → BE-12 (sau BE-9) → BE-13 → BE-15.

```text
Mở ngay:    BE-11 ─┬─► BE-12 ┄(nộp sau BE-9)─┬─► BE-13 ─┬─► BE-15 ──┐
                   │                         │          │           │
            BE-14 ─┼─────────────────────────┘          │           │
                   └─► FE-5 (sau G2b; nộp sau BE-13, FE-2) ─────────┤
Đang chạy:  BE-9 ──────────────────────────────────────►(BE-15 mở)──┤
Đang chạy:  FE-2 ─► FE-3a ─► FE-3b ─┬───────────────────────────────┤
                     └─► FE-4 (nộp sau BE-15) ──────────────────────┴─► QA
```

**Không chặn lane đang chạy**

| Lane | Vì sao không bị chặn |
|---|---|
| BE-9 | Không đổi phạm vi. Không lane mới nào được nộp phần chạm tệp của BE-9 trước khi BE-9 merge: BE-12 (`SolverInputBuilder`), BE-13 (`ExamTemplateTotalsIT`, `expected-totals.json`, migration mẫu) nộp sau; BE-15 (tệp kiểm tra) chỉ mở sau |
| FE-2 | Không đổi phạm vi. FE-5 chỉ sở hữu tệp bước 1 (FE-2 không sở hữu); phần chạm `types.ts`, `api-mapping.ts`, tệp thông điệp thì FE-5 nộp sau FE-2 và chỉ thêm |

BE-11 mở song song với BE-9 được: BE-11 chỉ sửa kiểu domain, bộ kiểm cấu trúc và phần mẫu; hai lane chỉ trùng ở tệp sinh ra (`openapi.json`, `api-client`).

**Việc cần làm trước:** Advisor mở G2b, tức bản vẽ bổ sung cho bảng V-ACT (hai cột, Phần còn lại, Chờ mapping, tiến độ), chùm cỡ linh hoạt, cột Chênh lệch và thông báo Ngữ văn. G2b chặn FE-5, FE-3b và phần thêm của FE-3a, FE-4; phần gốc của FE-3a, FE-4 không chờ. G2b không chặn BE.

## 4. Xung đột sở hữu

| Vùng | Lane đụng nhau | Cách xử lý |
|---|---|---|
| `openapi.json`, `packages/api-client` | BE-11, BE-12, BE-9 | Sinh lại khi merge, không sửa tay |
| `SolverInputBuilder.java` | BE-9 (thêm hàm đọc số đếm nguồn theo bộ lọc), BE-12 | BE-12 nộp sau BE-9 và rebase |
| `ExamCheckRules.java`, `DefaultExamChecker.java`, `ExamCheckUnitStates.java` | BE-9 (nối mã vào luật), BE-12 (hai chỗ `SET_SIZE_MISMATCH`), BE-15 | Nối tiếp: BE-9 → BE-12 → BE-15 |
| `expected-totals.json`, `ExamTemplateTotalsIT`, migration mẫu | BE-9 (điểm SP2E 2026), BE-13 | BE-13 nộp sau BE-9; chạy lại `build.mjs`; migration của BE-13 đặt tên theo giờ nộp để xếp sau cùng (Plan B2) |
| `R__seed_test_exam_data.sql`, `generate_test_exam_seed.py` | Chỉ BE-13 (BE-5 đã merge) | Chạy lại trình sinh |
| `TestExamSeedIT` | BE-13, BE-15 | BE-15 nộp sau BE-13 |
| `R__seed_catalog_dev_data.sql`, `R__seed_questionbank_dev_data.sql` | Chỉ BE-14 | BE-2, BE-10 đã xong |
| `features/exams/types.ts`, `api-mapping.ts` | FE-2, FE-5 | FE-5 nộp sau FE-2, chỉ thêm trường |
| Thông điệp web | FE-2, FE-3a, FE-3b, FE-4, FE-5 | Chỉ thêm khóa mới trong khối của lane |

## 5. Rủi ro

| Rủi ro | Ứng phó |
|---|---|
| Chùm cỡ linh hoạt làm bộ giải vượt 30 giây | BE-12 đo sớm. Nếu vượt, báo Advisor; phương án dự phòng là mẫu Toán dùng chùm cố định 6×3 (chỉ tạm thời, trái V §5.2a nên phải báo BA) |
| Bộ giải không nhận hai mục cùng môn trong một phần (H24) | Chưa truy hết mã bộ giải. BE-13 dòng 13 kiểm sớm; Advisor đã chấp nhận H24 và cách lùi: nếu không được, giữ một mục `SCIENTIFIC_THINKING` với hai nhóm Logic, Suy luận như hiện tại, và ghi độ lệch với V §2 (5 tiểu mục) vào danh sách báo BA |
| BA đổi ba số tạm Q-29, Q-30, Q-31 | Dễ đảo: Q-29 là một giá trị `scopes`; Q-30 là mã nhãn trong dữ liệu mẫu; Q-31 là dữ liệu mẫu, không đổi mã Java |
| Tách phần làm đổi id phần, mục, nhóm của mẫu THPTQG (`build.mjs` sinh id theo vị trí) | Bài nháp THPTQG tạo trước đó giữ cấu trúc cũ; khi nạp lại mẫu thì nhận cấu trúc mới, không ghép dòng cũ theo id. Ghi vào ghi chú phát hành |
| Ngân hàng thật không có câu V-ACT mang 10 điểm | Mẫu kiểm tra không đạt trên môi trường thật vì nhóm lọc đúng 10 điểm. Báo người nhập ngân hàng trước khi nhập; cách hiển thị điểm do đợt 3 quyết (D7) |
| Hồ sơ kỳ thi và 6 nhãn mới chỉ nằm trong `db/devdata` | Khi triển khai môi trường thật phải cập nhật `scopes` qua API hồ sơ kỳ thi và thêm nhãn ở màn danh mục |
