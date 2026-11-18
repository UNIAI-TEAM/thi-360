# Đề test giả định cho 7 kỳ thi

Ngày: 2026-11-17. Cập nhật 2026-11-18 theo mã tại `3d899976` (đợt 2B và phần bổ sung V-ACT, THPTQG). Trạng thái: số của mẫu theo BA; số của bộ đề `TEST-*` là giả định của đội dự án, chờ chuyên gia ra đề.

Tài liệu này mô tả đúng những gì đang có trong mã: 85 mẫu cấu trúc (cộng 7 mẫu bài ôn) và 27 bài `TEST-*` dùng cho E2E, kiểm tra cấu hình và dữ liệu mẫu. Đợt 3 (DS) lấy số ở đây. Khi mã đổi, sửa tài liệu theo mã, không làm ngược lại.

Mọi bảng ghi nguồn bên dưới. Đường dẫn tính từ `backend/src/main/resources/db/` trừ khi ghi khác.

| Nguồn | Nội dung |
|---|---|
| `migration/exam/V202611161801__seed_exam_templates.sql` | các mẫu đầu tiên (cấu trúc v1) |
| `migration/exam/V202611171200__assumed_templates_fill.sql` | điền số giả định cho mẫu trống |
| `migration/exam/V202611180652__exam_templates_ba_numbers.sql` | mẫu HSA, QDA, BCA, SPT, SP2E theo số BA, thêm mẫu theo phần, theo môn, bản cũ SP2E (sinh bởi `backend/src/test/resources/templates/build.mjs`) |
| `migration/exam/V202611180456__exam_review_templates.sql` | `<KỲ>_REVIEW` cho HSA, QDA, BCA, SP2E, SPT |
| `migration/exam/V202611181116__exam_template_flags.sql` | cột `publishable`, `fixed_counts` |
| `migration/exam/V202611181234__correct_sp2e_2026_points.sql` | điểm SP2E Anh, Sử, GDKTPL 2026 |
| `migration/exam/V202611181305__exam_templates_thptqg_vact.sql` | THPTQG (12 mẫu môn và `THPTQG_REVIEW`), V-ACT (8 mẫu và `VACT_REVIEW`) |
| `devdata/R__seed_test_exam_data.sql` | 27 bài `TEST-*` và 1.695 câu `TESTQ-*` (sinh bằng `scripts/generate_test_exam_seed.py`) |
| `devdata/R__seed_questionbank_dev_data.sql` | câu hỏi mẫu có nhãn, MAPID, năng lực cho mọi mẫu mới (UNI-2653, UNI-2675) |
| `backend/src/test/java/vn/unicom/thi360/exam/TestExamSeedIT.java`, `ExamTemplateFlagsCheckIT.java`, `ExamTemplatePolicyValidityIT.java` | kết quả kiểm tra mong đợi của từng bài `TEST-*` |

Ký hiệu: MĐ Một đáp án, NĐ Nhiều đáp án, ĐS Đúng/Sai, TLN Trả lời ngắn, TL Tự luận, GĐ Ghép đôi, ĐK Điền khuyết, chùm a×b là a bộ, mỗi bộ b câu con. Điểm viết `0,25` là điểm mỗi câu (`requiredPointsPerQuestion`). Số thập phân dùng dấu phẩy.

## 1. Quy ước chung

Các điểm này đang đúng trong mã, áp cho mọi mẫu và mọi bài `TEST-*`:

- Câu con của chùm Toán HSA là Một đáp án (`SINGLE_CHOICE`); mọi chùm của mẫu có `childAnswerType` (migration `V202611171108`).
- Nhiều đáp án mặc định `ALL_OR_NOTHING`.
- Các phần chạy theo thứ tự. Không có quy tắc chuyển phần (BA7).
- **Thứ tự và đảo (K4, K12).** Thứ tự nhóm là thứ tự trong mảng `blocks`; không còn `groupOrder` và `shuffle` (test `seededExamStructuresContainNoLegacyOrderingFields` bảo đảm không bài nào còn hai khóa này). Nhóm có `shuffleUnits` (mặc định `true`): câu và chùm đảo trong nhóm, câu con giữ thứ tự. Phương án đảo theo cờ `shuffleOptions` của từng câu (mặc định `true`, UNI-2648); `PaperOrdering` không đảo phương án khi cờ là `false`. Nhóm Đọc ngữ liệu (Văn HSA, QDA) có `shuffleEditable = true`, các nhóm khác không.
- **Không còn điều kiện dạng bài (K11).** Cột `task_type_item_id` đã bỏ (migration `V202611172352`). Nhóm cần đúng một loại câu thì gắn nhãn (`tagCodes`), MAPID hoặc năng lực (`competencyCodes`). Bộ đề `TEST-*` không dùng nhãn, trừ `TEST-ERR-03` (nhãn `TEST-HIEM`) và `TEST-VACT-01` (nhãn `ANH_*`, năng lực `VACT-*`).
- Chỉ tiêu chủ đề là "ít nhất" (K6, H33), kể cả V-ACT và THPTQG.
- HSA Khoa học: học viên chọn 3 trong 5 môn (10 bộ ba). Mỗi môn có 17 câu; một môn trong bộ ba chỉ làm 16 (`countsByPick = [17, 17, 16]`, BA8, BA13).
- `omitted` chỉ hợp lệ ở nhóm `ESSAY`. Câu `omitted` không tính vào số câu, điểm, và không vào kiểm tra. SPT và SP2E Văn dùng `omitted` cho "Tự luận – tạm bỏ qua" (K1, K7).
- THPTQG Ngữ văn: mẫu có `publishable = false`. Lưu nháp được, kiểm tra báo `TEMPLATE_NOT_PUBLISHABLE`, không công bố được (D11, H26).
- V-ACT FULL có `fixed_counts = true`: số câu từng phần, từng tiểu mục khóa; kiểm tra báo `STRUCTURE_COUNT_LOCKED` khi đổi (H27).
- V-ACT tính điểm luyện tập `LINEAR` về 1.200, mỗi câu 10 điểm (D7, H32).
- Bảng điểm Đúng/Sai theo số ý đúng: 0,1 / 0,25 / 0,5 / 1 (`tf_scoring` của hồ sơ SPT, SP2E, THPTQG là `COUNT_TABLE`). Mẫu SP2E Sử, GDKTPL 2026 và mọi mẫu SP2E bản cũ dùng `PER_STATEMENT` (mỗi ý 0,2 điểm của câu 0,8).

## 2. Mẫu cấu trúc trong mã

Nguồn: các migration ở mục đầu tài liệu. `Điểm` là điểm tối đa của mẫu (`max_score`), `phút` là `duration_minutes` của mẫu. Cột `Câu` ghi số câu học viên làm; khi đề in nhiều hơn thì ghi `làm / in`.

### 2.1 HSA

Nguồn: `V202611180652` (HSA_FULL, HSA_PART_*, HSA_SUBJECT_*), `V202611171108` (chùm Toán), `V202611171206` (ghi chú).

| Mẫu | Phạm vi | Timing | Phút | Điểm | Câu |
|---|---|---|---|---|---|
| `HSA_FULL` | FULL | PER_PART | 195 | 150 | 150 |
| `HSA_PART_MATH` | PART `TOAN` | PER_PART | 75 | 50 | 50 |
| `HSA_PART_LITERATURE` | PART `VAN` | PER_PART | 60 | 50 | 50 |
| `HSA_PART_SCIENCE` | PART `SCIENCE` | PER_PART | 60 | 50 | 50 |
| `HSA_PART_ENGLISH` | PART `ANH` | PER_PART | 60 | 50 | 50 |
| `HSA_SUBJECT_TOAN`, `_VAN`, `_ANH` | SUBJECT | PER_PART | không đặt | 50 | 50 |
| `HSA_SUBJECT_LY`, `_HOA`, `_SINH`, `_SU`, `_DIA` | SUBJECT | PER_PART | không đặt | 17 | 17 |

Cấu trúc `HSA_FULL` (các mẫu theo phần và theo môn chép từ đây), `structure_year = matrix_year = 2026`:

| Phần | Nội dung |
|---|---|
| 1 · Toán học và xử lí số liệu, 75 phút, 50 điểm | Chỉ tiêu dạng trả lời MĐ 35 / TLN 15; lớp 10/11/12 = 3/20/27; độ khó NB/TH/VD/VDC = 2/21/20/7 (cả hai `REQUIRED`). Nhóm `HSA_MATH_SINGLE` MĐ 32; nhóm `HSA_MATH_SINGLE` TLN 15; nhóm `HSA_MATH_CONTEXT` chùm cuối 1×3 (câu con MĐ). Điểm mọi câu 1 |
| 2 · Ngôn ngữ – Văn học, 60 phút, 50 điểm | 5 nhóm có nhãn: `HSA_VN_ODD_WORD` 5 (`VAN_TU_KHAC_NHOM`), `HSA_VN_WORD_USAGE` 5 (`VAN_TU_SAI`), `HSA_VN_WORD_PAIR` 5 (`VAN_CAP_TU_DIEN`), `HSA_VN_PASSAGE_SINGLE` 10 (`VAN_NGU_LIEU_MOT_CAU`), `HSA_VN_PASSAGE` chùm 5×5 (câu con MĐ) có 5 `slots` theo nhãn `VAN_NGHI_LUAN_XA_HOI`, `VAN_THONG_TIN`, `VAN_KHOA_HOC_CHINH_LUAN`, `VAN_THO`, `VAN_VAN_XUOI`, `shuffleEditable = true`. Điểm mọi câu 1 |
| 3 · Khoa học hoặc Tiếng Anh, 60 phút, 50 điểm (`participation` `CHOICE`, chọn 1) | Phương án `SCIENCE` (`SUBJECT_CHOICE`, chọn 3, `countsByPick` [17, 17, 16]): 5 môn × 17 câu. Lí, Hóa, Sinh: MĐ 13 + chùm 1×3 + TLN 1, nhóm `NATURAL`. Sử, Địa: MĐ 14 + chùm 1×3, nhóm `SOCIAL`. Phương án `ANH` (`SUBJECT`): 50 câu, 11 nhóm có nhãn |

Ma trận bắt buộc (lớp 10/11/12; NB/TH/VD) của năm môn Khoa học:

| Môn | Lớp | Độ khó |
|---|---|---|
| Vật lí | 5/6/6 | 3/1/13 |
| Hóa học | 2/8/7 | 4/5/8 |
| Sinh học | 3/5/9 | 7/7/3 |
| Lịch sử | 2/5/10 | 7/8/2 |
| Địa lí | 2/4/11 | 5/9/3 |

Tiếng Anh phần 3 (50 câu, mỗi câu 1 điểm): `HSA_EN_SENTENCE` 10 (`ANH_HOAN_THANH_CAU`), `HSA_EN_SYNONYM` 2 (`ANH_DONG_NGHIA`), `HSA_EN_ANTONYM` 2 (`ANH_TRAI_NGHIA`), `HSA_EN_REWRITE` 4 (`ANH_VIET_LAI_CAU`), `HSA_EN_COMBINE` 4 (`ANH_KET_HOP_CAU`), `HSA_EN_DIALOGUE` 4 (`ANH_HOI_THOAI`), `HSA_EN_DIALOGUE_ORDER` 4 (`ANH_SAP_XEP_HOI_THOAI`), `HSA_EN_CLOZE` chùm 1×5 (`ANH_DOC_DIEN`), `HSA_EN_READING` chùm 1×5 (`ANH_DOC_HIEU_1`), `HSA_EN_READING_2` chùm 1×5 (`ANH_DOC_HIEU_2`), `HSA_EN_LOGIC` 5 (`ANH_TU_DUY_LOGIC`). Chỉ tiêu cấp bài `hsa-english-grade` (tham khảo): lớp 10 và 11 gộp 30%, lớp 12 70%. Hai mẫu `HSA_PART_ENGLISH` và `HSA_SUBJECT_ANH` còn ghi chú "Chờ chuyên gia xác nhận".

### 2.2 QDA

Nguồn: `V202611180652`, `V202611161802` (phần 3 và danh sách mã). Mẫu theo phần chép từ `QDA_FULL`: `QDA_PART_MATH` 80 phút, `QDA_PART_LITERATURE` 55, `QDA_PART_SCIENCE` 60, `QDA_PART_ENGLISH` 60, mỗi mẫu 50 điểm. Mẫu theo môn (`PER_PART`, không đặt phút): `QDA_SUBJECT_TOAN`, `_VAN`, `_ANH` 50 điểm; `_LY`, `_HOA`, `_SINH`, `_SU`, `_DIA` 25 điểm.

`QDA_FULL`: FULL, `PER_PART`, 195 phút, 150 điểm, 150 câu.

| Phần | Nội dung |
|---|---|
| 1 · Toán, 80 phút, 50 điểm | Chỉ tiêu MĐ 35 / TLN 15; lớp 5/15/30 (`REQUIRED`); không khai độ khó. Nhóm `QDA_TOAN_SINGLE` MĐ 32, chùm `QDA_TOAN_CONTEXT` 1×3, `QDA_TOAN_SINGLE` TLN 15 (định dạng đáp án `INTEGER`, `DECIMAL`, `FRACTION`). Điểm 1 |
| 2 · Ngôn ngữ – Văn học, 55 phút, 50 điểm | 5 nhóm theo thứ tự: `QDA_VAN_ODD_WORD` 5 (`VAN_TU_KHAC_NHOM`), `QDA_VAN_WORD_PAIR` 5 (`VAN_CAP_TU_DIEN`), `QDA_VAN_WORD_USAGE` 5 (`VAN_TU_SAI`), `QDA_VAN_PASSAGE_SINGLE` 10 (`VAN_NGU_LIEU_MOT_CAU`), `QDA_VAN_PASSAGE` chùm 5×5 (câu con MĐ) có 5 `slots` theo nhãn **khác HSA**: `VAN_THONG_TIN_NGHI_LUAN`, `VAN_NGHI_LUAN`, `VAN_THO`, `VAN_THIEU_NHI`, `VAN_TRUYEN`, `shuffleEditable = true`. Chỉ `VAN_THO` trùng với năm nhãn của HSA; không dùng nhãn HSA để dựng ngữ liệu QDA. Điểm 1 |
| 3 · Khoa học hoặc Tiếng Anh, 60 phút, 50 điểm | `codeSource` = `GLOBAL_LIST`, danh sách mã `QD01`–`QD05` lấy từ Global List (5 phương án), `subjectPool` 5 môn × 25 câu (Lí, Hóa, Sinh: MĐ 21 + chùm 1×3 + TLN 1; Sử, Địa: MĐ 22 + chùm 1×3) và Tiếng Anh 50 câu, 10 nhóm có nhãn: `QDA_ANH_SENTENCE` 10 (`ANH_HOAN_THANH_CAU`), `QDA_ANH_SYN_ANT` 4 (hai nhãn `ANH_DONG_NGHIA`, `ANH_TRAI_NGHIA`), `QDA_ANH_DIALOGUE` 4 (`ANH_HOI_THOAI`), `QDA_ANH_NOTICE` 4 (`ANH_THONG_BAO`), `QDA_ANH_REWRITE` 4 (`ANH_VIET_LAI_CAU`), `QDA_ANH_COMBINE` 4 (`ANH_KET_HOP_CAU`), `QDA_ANH_CLOZE` chùm 1×5 (`ANH_DOC_DIEN`), `QDA_ANH_READING` 1×5 (`ANH_DOC_HIEU_1`), `QDA_ANH_READING_2` 1×5 (`ANH_DOC_HIEU_2`), `QDA_ANH_INFERENCE` 5 (`ANH_SUY_LUAN`) |

Mã tổ hợp QDA trong danh mục: `QD01` Lí–Hóa, `QD02` Hóa–Sinh, `QD03` Lí–Sinh, `QD04` Sử–Địa, `QD05` Tiếng Anh. Cả năm mã nằm trong `devdata/R__seed_catalog_dev_data.sql`; `R__seed_test_exam_data.sql` chỉ chèn lại `QD05` khi danh mục chưa có.

### 2.3 BCA

Nguồn: `V202611180652`. `BCA_FULL`: FULL, `SHARED`, 180 phút (đồng hồ chung), 100 điểm, 81 câu. Mẫu theo phần: `BCA_PART_VAN_TU_LUAN` (25 điểm), `BCA_PART_TN_BAT_BUOC` (60 điểm, 65 câu), `BCA_PART_TN_TU_CHON` (15 điểm, 15 câu). Mẫu theo môn (cũng `SHARED`, không đặt phút): `BCA_SUBJECT_TOAN` 35 điểm, `_VAN` 25, `_SU` 10, `_ANH`, `_LY`, `_HOA`, `_SINH`, `_DIA` mỗi mẫu 15.

| Phần | Nội dung |
|---|---|
| I · Tự luận Ngữ văn, 25 điểm | 1 TL 25 điểm, nhãn `VAN_NGHI_LUAN_XA_HOI`, không `omitted` (K1) |
| II · Trắc nghiệm bắt buộc, 60 điểm, 65 câu | Toán 35 câu: MĐ 27 + chùm 1×3 + TLN 5, 1 điểm; lớp 4/7/24 (`REQUIRED`), độ khó 2/12/15/6 (`REFERENCE`). Sử 10 câu: MĐ 7 + chùm tư liệu 1×3 (`SU_TU_LIEU_LICH_SU`), 1 điểm; lớp 1/2/7, độ khó 1/6/3/0. Tiếng Anh 20 câu, **0,75 điểm**: phát âm 1 (`ANH_PHAT_AM`), trọng âm 1 (`ANH_TRONG_AM`), từ vựng ngữ pháp 8 (`ANH_TU_VUNG_NGU_PHAP`), đọc điền 1×5 (`ANH_DOC_DIEN`), đọc hiểu 1×5 (`ANH_DOC_HIEU`); độ khó 3/9/8/0 |
| III · Trắc nghiệm tự chọn, 15 điểm, 15 câu | `codeSource` = `GLOBAL_LIST`, `subjectPool` Lí, Hóa, Sinh, Địa mỗi môn 15 câu (MĐ 12 + chùm 1×3), 1 điểm; mã `CA1`–`CA4` mỗi mã một môn |

Chỉ tiêu cấp bài tham khảo: `bca-grade` (lớp 10 và 11 gộp 18, lớp 12 42, trừ Văn và Anh) và `bca-level` (NB và TH 24, VD 40, VDC 16).

### 2.4 V-ACT

Nguồn: `V202611181305`, spec `docs/specs/2026-11-18-pbi04-dot-2b-bo-sung-vact-thpt.md` mục 5. Mọi bảng phân bố ở chế độ `REFERENCE`; mọi câu 10 điểm.

| Mẫu | Phạm vi | Phút | Điểm | Câu | `fixed_counts` |
|---|---|---|---|---|---|
| `VACT_FULL` | FULL, `SHARED` | 150 | 1.200 | 120 | **true** |
| `VACT_PART_LANGUAGE` | PART | nhập khi tạo | 600 | 60 | false |
| `VACT_PART_THINKING` | PART | nhập khi tạo | 300 | 30 | false |
| `VACT_PART_LOGIC` | PART | nhập khi tạo | 120 | 12 | false |
| `VACT_PART_REASONING` | PART | nhập khi tạo | 180 | 18 | false |
| `VACT_SUBJECT_VIETNAMESE`, `_ANH`, `_TOAN` | SUBJECT | nhập khi tạo | 300 | 30 | false |

Cấu trúc `VACT_FULL`:

| Phần | Mục (`subjectCode`, năng lực) | Câu | Nhóm |
|---|---|---|---|
| 1 · Sử dụng ngôn ngữ, 600 điểm | Tiếng Việt (`VIETNAMESE`, `VACT-NN-TV`) | 30 | `VACT_VN` MĐ 30. Độ khó 6/17/7 (NB/TH/VD). Tám chỉ tiêu chủ đề tham khảo (bốn mạch × mức), `nodeIds` rỗng (chờ mapping): Chính tả từ vựng 5 NB + 2 TH; Thể loại thi pháp 1 NB + 7 TH + 4 VD; Ngữ pháp liên kết 3 TH; Đọc hiểu 5 TH + 3 VD |
| | Tiếng Anh (`ANH`, `VACT-NN-TA`) | 30 | `VACT_ANH_CLOZE` 5 (`ANH_HOAN_THANH_CAU`; 3/2/0); `VACT_ANH_ERROR` 5 (`ANH_PHAT_HIEN_LOI`; 0/5/0); `VACT_ANH_EQUIVALENT` 5 (`ANH_DIEN_DAT_TUONG_DUONG`; 0/1/4); `VACT_ANH_READING_1` chùm 1×7 (`ANH_DOC_HIEU`; 1/4/2); `VACT_ANH_READING_2` chùm 1×8 (`ANH_DOC_HIEU`; 2/4/2) |
| 2 · Toán học, 300 điểm | Toán (`TOAN`, `VACT-TOAN`) | 30 | `VACT_MATH_SINGLE` MĐ 12; `VACT_MATH_SET` chùm 18 câu con MĐ, cỡ linh hoạt (không `setCount`, `setSize`). Lớp 10/11/12/`GRADE_MIXED` = 12/9/8/1; độ khó 4/10/16 |
| 3 · Tư duy khoa học, 300 điểm | Logic (`SCIENTIFIC_THINKING`, `VACT-TD-LOGIC`) | 12 | `VACT_LOGIC` chùm 4×3; độ khó 1/4/7 |
| | Suy luận (`SCIENTIFIC_THINKING`, `VACT-TD-SL`) | 18 | `VACT_SCIENCE` chùm 6×3; độ khó 4/9/5 |

Dải câu trong từng phần do hệ thống tính (`positionRange`, H34), mẫu không lưu. `VACT_REVIEW` có `everyChildInScope = true`.

### 2.5 SPT

Nguồn: `V202611180652`. 12 môn, phiên bản 2025, `PER_PART`, **không đặt thời lượng** (quản trị viên nhập khi tạo). Mọi nhóm TL ở trạng thái `omitted`, nên cột Câu và Điểm chỉ tính phần chấm tự động. Đúng/Sai có `itemShape.statements = 4` và bảng điểm 0,1/0,25/0,5/1.

| Mẫu | Điểm | Câu | Phần I | Phần II | Phần III |
|---|---|---|---|---|---|
| `SPT_MATH_2025` | 7 | 22 | MĐ 16 × 0,25 | ĐS 2 × 1 | TLN 4 × 0,25; TL 3 `omitted` |
| `SPT_LY_2025` | 7 | 19 | MĐ 12 × 0,25 | ĐS 3 × 1 | TLN 4 × 0,25; TL 2 `omitted` |
| `SPT_HOA_2025` | 8 | 26 | MĐ 20 × 0,25 | ĐS 2 × 1 | TLN 4 × 0,25; TL 1 `omitted` |
| `SPT_SINH_2025` | 7 | 19 | MĐ 12 × 0,25 | ĐS 3 × 1 | TLN 4 × 0,25; TL 3 `omitted` |
| `SPT_DIA_2025` | 7 | 19 | MĐ 12 × 0,25 | ĐS 3 × 1 | TLN 4 × 0,25; TL 1 `omitted` |
| `SPT_SU_2025` | 7 | 22 | MĐ 20 × 0,25 | ĐS 2 × 1; TL 3 `omitted` | không có |
| `SPT_ECON_LAW_2025` | 7 | 19 | MĐ 16 × 0,25 | ĐS 3 × 1; TL 2 `omitted` | không có |
| `SPT_TECH_INDUSTRY_2025` | 7 | 22 | MĐ 16 × 0,25 | ĐS 2 × 1 | TLN 4 × 0,25; TL 2 `omitted` |
| `SPT_TECH_AGRICULTURE_2025` | 7 | 22 | MĐ 16 × 0,25 | ĐS 2 × 1 | TLN 4 × 0,25; TL 2 `omitted` |
| `SPT_INFORMATICS_2025` | 7 | 22 (đề in 23) | MĐ 20 × 0,25 | ĐS 1 chung + 1 theo nhánh `CS` hoặc `ICT`, × 1; TL 3 `omitted` | không có |
| `SPT_ANH_2025` | 8 | 32 | MĐ 5 + chùm 2×6 + MĐ 1 (sắp xếp hội thoại), × 0,25 | chùm 2×5 × 0,25 | ĐK 4 vị trí × 0,25; TL 1 `omitted` |
| `SPT_VAN_2025` | 3 | 10 | chùm 2×5 × 0,3 (hai `slots`) | không có phần chấm tự động | hai nhóm TL `omitted` (nghị luận xã hội, nghị luận văn học) |

`SPT_REVIEW`: khung bài ôn, không có số.

### 2.6 SP2E

Nguồn: `V202611180652`, `V202611181234`. 9 môn bản 2026 và 5 bản cũ ("Đề minh họa cũ", mã `_OLD`, `is_default = false` trừ Văn). `SHARED`. Mặc định: bản 2026, riêng Văn mặc định là `SP2E_VAN_OLD` (K15). Điểm từng dạng của bản 2026 là số tạm (`expert_note` "chờ chuyên gia xác nhận").

| Mẫu | Phút | Điểm | Câu | Phần I | Phần II | Phần III |
|---|---|---|---|---|---|---|
| `SP2E_MATH_2025` | 90 | 10 | 22 | MĐ 12 × 0,25 | ĐS 4 × 1 | TLN 6 × 0,5 |
| `SP2E_MATH_2026` | 90 | 10 | 40 | MĐ 20 + NĐ 5, × 0,25 | MĐ 5 × 0,25, nhãn `TOAN_DU_LIEU_TONG_HOP` | TLN 10 × 0,25 |
| `SP2E_LY_2026`, `_HOA_2026`, `_SINH_2026` | 60 | 10 | 28 | MĐ 16 × 0,25 | ĐS 4 × 1 | TLN 8 × 0,25 |
| `SP2E_DIA_2026` | 60 | 10 | 28 | MĐ 16 × 0,25 | ĐS 3 + 1 (nhãn `DIA_CAU_KY_NANG`), × 1 | TLN 8 × 0,25 |
| `SP2E_SU_2026` | 60 | 10 | 32 | MĐ 26 × 0,2 | ĐS 6 × 0,8 (`PER_STATEMENT`) | không có |
| `SP2E_ECON_LAW_2026` | 60 | 10 | 32 | MĐ 20 + chùm 2×3, × 0,2 | ĐS 6 × 0,8 (`PER_STATEMENT`) | không có |
| `SP2E_ANH_2026` | 60 | 10 | 50 | 9 nhóm có nhãn, × 0,2: đọc điền câu 1×10, đồng nghĩa 4, kết hợp câu 4, điền từ 1×5, điền cụm 1×5, giao tiếp 4, sắp xếp 5, đọc hiểu 1 (1×5), đọc hiểu 2 (1×8) | | |
| `SP2E_VAN_2026` | 90 | 5 | 20 | MĐ 20 × 0,25 (đọc hiểu) | viết đoạn, TL `omitted` | viết bài, TL `omitted` |
| `SP2E_VAN_OLD` (mặc định) | nhập khi tạo | 6 | 30 | 4 chùm đọc hiểu 8/8/7/7 × 0,2 | viết bài TL 4 điểm `omitted` | không có |
| `SP2E_LY_OLD`, `_HOA_OLD`, `_SINH_OLD`, `_SU_OLD` | 60 | 10 | 20 | MĐ 10 × 0,2 | ĐS 5 × 0,8 | GĐ 5 × 0,8 |

`SP2E_REVIEW`: khung bài ôn. Mẫu bản cũ nào chưa có phân bố riêng thì chỉ tiêu cấp bài tham khảo của chúng nằm trong `expert_note`.

### 2.7 THPTQG

Nguồn: `V202611181305`, spec bổ sung mục 4. 12 môn, phiên bản 2025. Mẫu tách phần theo T §4 và đổi sang `SHARED` (trừ Tiếng Anh và Ngữ văn giữ `PER_PART`). Đúng/Sai có `itemShape.statements = 4` và bảng điểm 0,1/0,25/0,5/1.

| Mẫu | Phút | Điểm | Câu | Phần I | Phần II | Phần III |
|---|---|---|---|---|---|---|
| `THPTQG_MATH_2025` | 90 | 10 | 22 | MĐ 12 × 0,25 = 3 | ĐS 4 × 1 = 4 | TLN 6 × 0,5 = 3 |
| `THPTQG_PHYSICS_2025`, `_CHEMISTRY_2025`, `_BIOLOGY_2025`, `_GEOGRAPHY_2025` | 50 | 10 | 28 | MĐ 18 × 0,25 = 4,5 | ĐS 4 × 1 = 4 | TLN 6 × 0,25 = 1,5 |
| `THPTQG_HISTORY_2025`, `_ECON_LAW_2025`, `_TECH_INDUSTRY_2025`, `_TECH_AGRICULTURE_2025` | 50 | 10 | 28 | MĐ 24 × 0,25 = 6 | ĐS 4 × 1 = 4 | không có |
| `THPTQG_INFORMATICS_2025` | 50 | 10 | 28 làm / 30 in | MĐ 24 × 0,25 = 6 | ĐS 2 chung + một nhánh `CS` hoặc `ICT` 2 = 4 điểm | không có |
| `THPTQG_ENGLISH_2025` | 50 (`PER_PART`) | 10 | 40 | một phần, mọi câu 0,25 | | |
| `THPTQG_LITERATURE_2025` | 120 (`PER_PART`) | 10 | 7 | một phần: chùm Đọc hiểu 1×5 TL × 0,8 = 4; TL 1 × 2; TL 1 × 4. `publishable = false`; độ khó Đọc hiểu `REFERENCE` 2/2/1 | | |

Tin học: phần II có `questionCountOnPaper = 6` (2 chung + 2 CS + 2 ICT) và `questionCountRequired = 4`; đề in 30 câu, học viên làm 28. Điểm lệch cũ (`questionCountOnPaper = 4`) đã sửa.

Tiếng Anh, 6 nhóm có thứ tự, mọi chùm có câu con MĐ (dải câu do hệ thống tính):

| Nhóm | Dạng | Dải | Nhãn | Điểm nhóm |
|---|---|---|---|---|
| `THPTQG_ANH_CLOZE_1` Reading 1 – Đọc điền | chùm 1×6 | 1–6 | `ANH_DOC_DIEN` | 1,5 |
| `THPTQG_ANH_CLOZE_2` Reading 2 – Đọc điền | chùm 1×6 | 7–12 | `ANH_DOC_DIEN` | 1,5 |
| `THPTQG_ANH_ORDER` Sắp xếp hội thoại hoặc văn bản | 5 câu đơn | 13–17 | `ANH_SAP_XEP_HOI_THOAI`, `ANH_SAP_XEP_DOAN` | 1,25 |
| `THPTQG_ANH_GAP_SENT` Reading 3 – Đọc điền câu | chùm 1×5 | 18–22 | `ANH_DOC_DIEN_CAU` | 1,25 |
| `THPTQG_ANH_READ_1` Reading 4 – Đọc hiểu | chùm 1×8 | 23–30 | `ANH_DOC_HIEU` | 2 |
| `THPTQG_ANH_READ_2` Reading – Đọc hiểu | chùm 1×10 | 31–40 | `ANH_DOC_HIEU` | 2,5 |

`THPTQG_REVIEW`: khung bài ôn, không có số. Tin học SPT và THPTQG giữ phần chọn định hướng (K2) ở màn chuẩn bị, đợt 3.

### 2.8 Bài ôn và phạm vi

Nguồn: `V202611180456`, `V202611181305`. Mỗi kỳ thi có một mẫu `<KỲ>_REVIEW` (HSA, QDA, BCA, SP2E, SPT, THPTQG, VACT): `build_mode = REVIEW`, `scope = SUBJECT`, không số câu, không điểm, quản trị viên nhập. `VACT_REVIEW` bật `everyChildInScope`. Phạm vi của hồ sơ kỳ thi (`exam_kind_profile.scopes`): HSA, QDA, BCA, V-ACT `FULL, PART, SUBJECT`; SPT, SP2E, THPTQG `SUBJECT, PART` (test `examKindProfilesSeedScopesPerSpec2B`).

## 3. Số còn là giả định

Số nào mẫu còn ghi chờ chuyên gia (cột `expert_note`), hoặc do đội dự án tự đặt:

| Chỗ | Số đang dùng | Nguồn |
|---|---|---|
| HSA Tiếng Anh (`HSA_PART_ENGLISH`, `HSA_SUBJECT_ANH`) | 11 nhóm và số câu mỗi nhóm; phân bố lớp 30/70 | `expert_note` "Chờ chuyên gia xác nhận" |
| SP2E 2026, điểm từng dạng (Toán, Lí, Hóa, Sinh, Địa, Anh, Sử, GDKTPL) | 0,25 / 0,2 / 0,8 / 1 như bảng mục 2.6 | `expert_note` "số tạm", sửa lần cuối ở `V202611181234` |
| SP2E Toán 2025 | 22 câu chia 12–4–6, 90 phút | `expert_note` "giả định" |
| SP2E bản cũ (`_OLD`) và Văn 2026 | số câu mỗi văn bản, điểm Đọc hiểu, phân bố tham khảo | `expert_note` |
| THPTQG Ngữ văn | điểm từng câu của chùm Đọc hiểu theo hướng dẫn chấm | `expert_note`; mẫu `publishable = false` (D11) |
| V-ACT | 10 điểm mỗi câu, 1.200 (tạm tính, thật dùng IRT); phạm vi riêng, nhãn D9 | `expert_note` của `VACT_FULL`, spec bổ sung Q-30, Q-31 |
| THPTQG bài theo phần | bật `PART` | spec bổ sung Q-29 |

Số còn lại của mẫu là số BA (không có `expert_note`). Không bịa thêm: chỗ BA chưa nêu, để trống và ghi vào mục câu hỏi mở của spec.

## 4. Bộ đề `TEST-*`

Bộ đề nằm trong `devdata/R__seed_test_exam_data.sql` (id cố định, `ON CONFLICT DO NOTHING`; chạy lại không ghi đè bài đã sửa) và chạy cùng dữ liệu câu mẫu. Mọi bài ở `DRAFT`, `check_status = UNCHECKED`, `practice_scoring = RAW`; riêng `TEST-VACT-01` là `LINEAR`. Kết quả kiểm tra là kết quả mà `TestExamSeedIT` khẳng định, không phải số suy ra.

**Quan trọng cho đợt 3.** Chỉ `TEST-THPTQG-01` và `TEST-VACT-01` trùng từng nhóm với mẫu hiện hành. Các bài còn lại giữ cấu trúc "giả định" của ngày 2026-11-17 (trước số BA của `V202611180652`): chia nhóm đơn giản hơn mẫu. Phần lớn giữ đúng tổng số câu và điểm của mẫu; ngoại lệ là `TEST-SPT-01` (25 câu, 10 điểm, TL 3 câu tính điểm, so với mẫu `SPT_MATH_2025` 22 câu, 7 điểm, TL `omitted`; xem mục 4.4). Mục 4.4 liệt kê chỗ lệch. Khi cần một bài cấu trúc đúng mẫu BA, tạo bài từ mẫu bằng API, không dùng `TEST-*`.

### 4.1 Bài hợp lệ

Cột Câu là số câu học viên làm, Điểm là cột `max_score` của bài (ghi riêng khi khác). Phút chép từ cột `duration_minutes` của bài.

| Mã | Mẫu (nguồn cấu trúc) | Phạm vi | Timing, phút | Điểm | Câu | Dùng để kiểm | Kết quả |
|---|---|---|---|---|---|---|---|
| `TEST-HSA-01` | `HSA_FULL` | FULL | `PER_PART`, 195 | 150 | 150 | 3 phần, đường Khoa học và đường Tiếng Anh, 10 bộ ba môn | VALID |
| `TEST-HSA-02` | `HSA_PART_MATH` | PART `TOAN` | `PER_PART`, 75 | 50 | 50 | chỉ tiêu 35 + 15, lớp và độ khó hai chiều, chùm 3×3 | VALID |
| `TEST-HSA-03` | `HSA_PART_LITERATURE` | PART `VAN` | `PER_PART`, 60 | 50 | 50 | hai nhóm câu đơn dùng chung nguồn, chùm 5×5 | VALID |
| `TEST-HSA-04` | `HSA_PART_SCIENCE` | PART `SCIENCE` | `PER_PART`, 60 | 50 | 50 | `SUBJECT_CHOICE`, tỉ lệ ra 17/17/16; nhánh Tiếng Anh 5 nhóm | VALID |
| `TEST-QDA-01` | `QDA_FULL` | FULL | `PER_PART`, 195 | 150 | 150 | mã tổ hợp 5 (`QD01`–`QD05`), `codeSource` | VALID |
| `TEST-BCA-01` | `BCA_FULL` | FULL | `SHARED`, 180 | 100 | 81 | đồng hồ chung, Anh 0,75, TL 25 điểm, 4 mã `CA1`–`CA4` | VALID, kèm cảnh báo `MANUAL_GRADING_REQUIRED` |
| `TEST-VACT-01` | `VACT_FULL` (giống mẫu) | FULL | `SHARED`, 150 | `max_score` NULL; `reference_max` 1.200 (mốc quy đổi `LINEAR`); tổng điểm thô 1.200 (120 câu × 10, mẫu `max_score` 1.200) | 120 | 120 câu, `LINEAR`, Logic và Suy luận giải chung một đơn vị kiểm tra | VALID; chỉ được có cảnh báo `DISTRIBUTION_DEVIATION` |
| `TEST-SPT-01` | `SPT_MATH_2025` | SUBJECT `TOAN` | `PER_PART`, 90 | 10 | 25 | 4 dạng, Tự luận chờ chấm | VALID, kèm `MANUAL_GRADING_REQUIRED` |
| `TEST-SP2E-01` | `SP2E_MATH_2025` | SUBJECT `TOAN` | `PER_PART`, 90 | 10 | 22 | phiên bản 2025 | VALID |
| `TEST-SP2E-02` | `SP2E_MATH_2026`, `years_confirmed = true` | SUBJECT `TOAN` | `PER_PART`, 90 | 10 | 40 | đổi phiên bản 2025 → 2026, Nhiều đáp án, chùm 1×5 | VALID |
| `TEST-SP2E-03` | `SP2E_ANH_2026` | SUBJECT `ANH` | `PER_PART`, 60 | 10 | 50 | Điền khuyết đếm theo vị trí | VALID |
| `TEST-THPTQG-01` | `THPTQG_MATH_2025` (giống mẫu) | SUBJECT `TOAN` | `SHARED`, 90 | 10 | 22 | ĐS bảng điểm, 3 phần, E2E; không có cờ chính sách mẫu | VALID, công bố được |
| `TEST-THPTQG-02` | `THPTQG_LITERATURE_2025` | SUBJECT `VAN` | `PER_PART`, 120 | 10 | 7 | chùm có câu con TL; **chặn công bố** | **INVALID** (`TEMPLATE_NOT_PUBLISHABLE`, `MANUAL_GRADING_REQUIRED`), lưu nháp được |
| `TEST-THPTQG-03` | `THPTQG_INFORMATICS_2025` | SUBJECT `INFORMATICS` | `SHARED`, 50 | 10 | 28 (in 30) | nhánh CS/ICT, 28 phải làm | VALID |

Cấu trúc từng bài (nguồn `exam.exam_structure` của seed; ghi "mẫu: …" khi lệch mẫu hiện hành):

| Mã | Phần, nhóm | Chỉ tiêu và ma trận |
|---|---|---|
| `TEST-HSA-01` | P1 Toán 75′: 41 câu đơn (không khóa dạng) + chùm 3×3 (con MĐ). P2 Văn 60′: nhận diện lỗi dùng từ 10 + câu đơn khác 15 + Đọc ngữ liệu chùm 5×5. P3 60′ chọn 1: `SCIENCE` 5 môn × 17 (mỗi môn chùm 2×3 + câu đơn 11), `ANH` 50 (đồng nghĩa 5, trái nghĩa 5, hội thoại 5, hoàn thành câu 20, đọc hiểu chùm 3×5). Mọi câu 1 điểm | Toán: MĐ 35 + TLN 15; lớp 3/20/27; độ khó 2/21/20/7. Khoa học mỗi môn: MĐ 13 + TLN 4; lớp 3/5/9; độ khó 3/7/5/2. Cả hai `REQUIRED` |
| `TEST-HSA-02` | như P1 của `TEST-HSA-01` | như trên |
| `TEST-HSA-03` | như P2 của `TEST-HSA-01` | không có |
| `TEST-HSA-04` | như P3 của `TEST-HSA-01`, tên phần "Phần 3 · Khoa học hoặc Tiếng Anh" | như trên |
| `TEST-QDA-01` | P1 Toán 80′: MĐ 50. P2 Văn 55′: MĐ 50. P3 60′ chọn 1, `codeSource` `GLOBAL_LIST`, `subjectPool` Lí, Hóa, Sinh, Sử, Địa mỗi môn MĐ 25 và Anh MĐ 50. Điểm 1 | không có |
| `TEST-BCA-01` | P1 TL Văn 1 × 25. P2 65 câu: Toán MĐ 25 + TLN 10, Sử MĐ 10, Anh MĐ 20 × 0,75. P3 15 câu chọn 1, `codeSource`, `subjectPool` Lí, Hóa, Sinh, Địa mỗi môn MĐ 15. Điểm P2 60, P3 15 | không có |
| `TEST-VACT-01` | giống `VACT_FULL` (mục 2.4), id khác | giống mẫu |
| `TEST-SPT-01` | một phần 90′: MĐ 16 × 0,25, ĐS 2 × 1, TLN 4 × 0,25, TL 3 × 1 (không `omitted`) | không có |
| `TEST-SP2E-01` | một phần 90′: MĐ 12 × 0,25, ĐS 4 × 1, TLN 6 × 0,5 | không có |
| `TEST-SP2E-02` | một phần 90′: MĐ 20 × 0,25, NĐ 5 × 0,25, chùm 1×5 × 0,25 (không khai dạng câu con), TLN 10 × 0,25; `structure_year` 2026, `matrix_year` 2025, `years_confirmed = true` | không có |
| `TEST-SP2E-03` | một phần 60′: MĐ 30, chùm 2×5 (con MĐ), ĐK 1 câu × 10 vị trí; mọi câu 0,2 | không có |
| `TEST-THPTQG-01` | giống `THPTQG_MATH_2025` (mục 2.7), id khác | giống mẫu |
| `TEST-THPTQG-02` | một phần 120′: chùm Đọc hiểu 1×5 (con TL) × 0,8; TL × 2; TL × 4 | không có |
| `TEST-THPTQG-03` | P1 MĐ 24 × 0,25; P2 chọn 1: ĐS chung 2 × 1, `CS` 2, `ICT` 2; đề in 6 câu, làm 4 | không có |

### 4.2 Bài lỗi

Mỗi bài dựng từ một bài hợp lệ, chỉ đổi một chỗ. Cột "Mã thực tế" là bộ mã mà `TestExamSeedIT.assumedExamsHaveTheirSpecifiedCheckResults` khẳng định (so bằng tập, không thừa, không thiếu); một chỗ sửa thường kéo theo vài mã hệ quả.

| Mã | Dựng từ | Đổi gì | Mã chính | Mã thực tế |
|---|---|---|---|---|
| `TEST-ERR-01` | `TEST-THPTQG-01` | nhóm MĐ có `matrix` (lớp 12 = 12) nhưng chưa chọn cách áp (chung hay riêng) | `MATRIX_INCOMPLETE` | `MATRIX_INCOMPLETE` |
| `TEST-ERR-02` | `TEST-BCA-01` | khối Tiếng Anh đổi 0,75 → 1 (Phần 2: 65 > 60; tổng 105 ≠ 100) | `PART_POINTS_MISMATCH` | `PART_POINTS_MISMATCH`, `POINTS_TOTAL_MISMATCH`, `MANUAL_GRADING_REQUIRED` |
| `TEST-ERR-03` | `TEST-HSA-03` | nhóm Đọc ngữ liệu thêm nhãn `TEST-HIEM`; nguồn chỉ có 2 bộ mang nhãn, cần 5 | `SET_SHORTAGE` | `SET_SHORTAGE` |
| `TEST-ERR-04` | `TEST-HSA-02` | nhóm câu đơn 40 thay 41 (môn 49 câu) | `SECTION_TOTAL_MISMATCH` | `SECTION_TOTAL_MISMATCH`, `PART_POINTS_MISMATCH`, `PATH_TOTAL_MISMATCH`, `POINTS_TOTAL_MISMATCH`, `CELL_SHORTAGE` |
| `TEST-ERR-05` | `TEST-HSA-02` | chỉ tiêu TLN 14 (tổng 49) | `ANSWER_QUOTA_TOTAL_MISMATCH` | `ANSWER_QUOTA_TOTAL_MISMATCH`, `CELL_SHORTAGE` |
| `TEST-ERR-06` | `TEST-HSA-02` | phạm vi MAPID của môn đặt vào nút `TEST-TOAN-HEP`; dưới nút có 10 TLN, cần 15 | `ANSWER_TYPE_SHORTAGE` | `ANSWER_TYPE_SHORTAGE` |
| `TEST-ERR-07` | `TEST-HSA-04` | Địa lí đặt phạm vi nút `TEST-DIA-HEP`: đủ cho 16 câu, thiếu cho 17 (ô cần 11, có 10) | `CELL_SHORTAGE` | `CELL_SHORTAGE`; 10 bộ ba, **6 bộ có Địa lí INVALID**, 4 bộ còn lại VALID |
| `TEST-ERR-08` | `TEST-HSA-04` | Vật lí: 6 câu chùm + 11 câu đơn, đổi tên nhóm; giải đủ 17 câu trước khi bỏ một câu. **Hiện VALID**: mã `REMAINDER_GROUP_INVALID` không còn trong mã | không còn lỗi | VALID, không có mã. Giữ làm bài đối chứng cho môn nhận 16 câu |
| `TEST-ERR-09` | `TEST-HSA-04` | Hóa học khai chỉ tiêu dạng trả lời 12 + 4 = 16 cho môn 17 câu | `ANSWER_QUOTA_TOTAL_MISMATCH` | `ANSWER_QUOTA_TOTAL_MISMATCH`, `CELL_SHORTAGE` |
| `TEST-ERR-10` | `TEST-HSA-04` | `countsByPick` = 17, 17, 17 | `SUBJECT_CHOICE_COUNTS_INVALID` | `SUBJECT_CHOICE_COUNTS_INVALID`, `PART_EMPTY`, `PART_POINTS_MISMATCH`, `PART_TOTAL_MISMATCH`, `PATH_TOTAL_MISMATCH`, `POINTS_TOTAL_MISMATCH` |
| `TEST-ERR-11` | `TEST-THPTQG-01` | ô Lớp 12 × VDC của nhóm MĐ đặt 12 câu (bộ mẫu có tối đa 4 câu ô này) | `CELL_SHORTAGE` | `CELL_SHORTAGE` (`required` 12, `available` 4) |
| `TEST-ERR-12` | `TEST-THPTQG-01` | nhóm TLN bỏ điểm mỗi câu | `POINTS_TOTAL_MISMATCH` | `POINTS_TOTAL_MISMATCH` |
| `TEST-ERR-13` | `TEST-SP2E-02` | `years_confirmed = false` (cấu trúc 2026, ma trận 2025) | `YEARS_UNCONFIRMED` | `YEARS_UNCONFIRMED` |

Mã `SUBJECT_QUOTA_COUNT_VARIES` mà bản cũ của tài liệu ghi cho `TEST-ERR-09` không còn trong mã.

### 4.3 Nguồn câu hỏi của bộ đề

Quy tắc của plan BE-2a (`docs/plans/2026-11-16-be-pbi-03-04.md`): mỗi ô, mỗi mức điểm có số câu `READY` ít nhất gấp đôi chỉ tiêu. Nguồn lọc theo môn, loại câu và điểm (và nhãn, MAPID khi nhóm có), không theo kỳ thi, nên bài khác kỳ thi dùng chung nguồn khi trùng bộ lọc. Không còn lọc theo dạng bài (K11).

Có 1.695 câu `TESTQ-*`: 1.499 `READY`, 98 `DRAFT`, 98 `RETIRED`. Câu Nháp và Ngừng sử dụng đi kèm mỗi dòng để chắc chúng không được đếm. Sinh bằng `generate_series` trong `scripts/generate_test_exam_seed.py`, không viết tay. Cột "Tối thiểu" là số mà `TestExamSeedIT.readyPoolsMeetAssumedMinimums` khẳng định; "Có" là số `READY` đếm trên cơ sở dữ liệu đã nạp seed. Đơn vị là đơn vị đếm (câu đơn, câu con, vị trí Điền khuyết); "bộ" là chùm nguyên.

| Môn | Loại, điểm | Tối thiểu | Có | Dùng cho |
|---|---|---|---|---|
| Toán | MĐ đơn 1 điểm | 100 | 168 (60 gắn nút `TEST-TOAN-HEP`) | QDA 50; HSA 26; BCA 25 |
| Toán | TLN 1 điểm | 30 | 50 (10 gắn `TEST-TOAN-HEP`) | HSA 15; BCA 10 |
| Toán | chùm 3 câu con MĐ 1 điểm | 6 bộ | 12 | HSA 3 bộ |
| Toán | MĐ 0,25 | 40 | 41 (bốn câu Lớp 12 × VDC: ba câu theo vòng sinh, một câu `TESTQ-TOAN-MC025-259` chuyển bằng UPDATE, cho `TEST-ERR-11`) | SP2E 2026: 20 |
| Toán | ĐS 1 điểm (`COUNT_TABLE`) | 8 | 8 | THPTQG, SP2E 2025: 4 |
| Toán | TLN 0,25 / TLN 0,5 | 20 / 12 | 20 / 12 | SP2E 2026: 10 / THPTQG, SP2E 2025: 6 |
| Toán | NĐ 0,25 `ALL_OR_NOTHING`; chùm 5 câu con 0,25 | 10; 2 bộ | 10; 2 bộ | SP2E 2026 |
| Toán | TL 1 điểm (có hướng dẫn chấm); MĐ 10 điểm | 6; 60 | 6; 60 | SPT 3; V-ACT 30 |
| Ngữ văn | MĐ đơn 1 điểm | 100 | 100 | QDA 50; HSA 10 + 15 |
| Ngữ văn | chùm 5 câu con MĐ 1 điểm | 10 bộ | 10 (đúng 2 bộ, `TESTQ-VAN-PASSAGE-S001` và `-S002`, gắn nhãn `TEST-HIEM` cho `TEST-ERR-03`) | HSA 5 bộ |
| Ngữ văn | chùm 5 câu con TL 0,8 | 2 bộ | 2 | THPTQG; không gắn nhãn |
| Ngữ văn | TL 25 / TL 2 / TL 4 | 2 / 2 / 2 | 2 / 2 / 2 | BCA Phần I; THPTQG |
| Tiếng Anh | MĐ đơn 1 điểm | 100 | 100 | QDA 50; HSA 35 |
| Tiếng Anh | chùm 5 câu con MĐ 1 điểm | 6 bộ | 6 | HSA 3 bộ |
| Tiếng Anh | MĐ 0,75 / MĐ 10 | 40 / 60 | 40 / 60 | BCA 20 / V-ACT |
| Tiếng Anh | MĐ 0,2; chùm 5 câu con 0,2; ĐK 2 điểm | 60; 4 bộ; 2 câu | 60; 4; 2 | SP2E 2026 |
| Lí, Hóa, Sinh, Sử, Địa (mỗi môn) | MĐ đơn 1 điểm | 50 | 50 (Địa 56: 6 gắn `TEST-DIA-HEP`) | QDA 25; BCA Sử 10; HSA tối đa 17 |
| Lí, Hóa, Sinh, Sử, Địa (mỗi môn) | TLN 1 điểm; chùm 3 câu con MĐ 1 điểm | 8; 4 bộ | 8; 4 (Địa 12 và 6) | HSA |
| Tiếng Việt, Tư duy khoa học | MĐ 10 điểm | 60 mỗi môn | 60 | V-ACT |
| Tin học | MĐ 0,25; ĐS 1 điểm | 48; 8 | 48; 8 | THPTQG 24; 2 chung + 2 mỗi nhánh |

Các nhóm có nhãn `ANH_*` và năng lực `VACT-*` của `TEST-VACT-01` lấy nguồn từ `R__seed_questionbank_dev_data.sql` (câu có nhãn, MAPID, năng lực), không từ `TESTQ-*` (ngoài `TEST-HIEM`).

### 4.4 Chỗ bài `TEST-*` lệch mẫu hiện hành

Số liệu dưới đây là đối chiếu giữa `R__seed_test_exam_data.sql` và cấu trúc mẫu trong cơ sở dữ liệu sau khi chạy toàn bộ migration. Cả hai đều đúng trên mã; chúng khác nhau vì bộ đề test chưa dựng lại theo số BA.

| Bài | Mẫu hiện hành | Bài `TEST-*` |
|---|---|---|
| `TEST-HSA-01`, `-02`, `-03`, `-04` | Toán: MĐ 32 + TLN 15 + chùm 1×3. Văn: 5 nhóm có nhãn, chùm có `slots`. Khoa học: Lí, Hóa, Sinh 13 + chùm 1×3 + TLN 1; Sử, Địa 14 + chùm 1×3; ma trận mỗi môn khác nhau. Anh: 11 nhóm có nhãn | Toán: 41 câu đơn + chùm 3×3. Văn: 10 + 15 + chùm 5×5, không nhãn. Khoa học mỗi môn: chùm 2×3 + 11 câu đơn, ma trận cùng 3/5/9 và 3/7/5/2. Anh: 5 + 5 + 5 + 20 + 3×5, không nhãn. Không có `examQuotas` |
| `TEST-QDA-01` | Toán 32 + 1×3 + TLN 15; Văn 5 nhóm; Anh 10 nhóm | mỗi phần một nhóm MĐ |
| `TEST-BCA-01` | Toán 27 + 1×3 + TLN 5; Sử 7 + 1×3; Anh 5 nhóm; `subjectPool` 12 + 1×3 | Toán MĐ 25 + TLN 10; Sử MĐ 10; Anh MĐ 20; `subjectPool` MĐ 15. Cùng tổng 81 câu, 100 điểm |
| `TEST-SPT-01` | 22 câu, 7 điểm (16 + 2 + 4); TL 3 `omitted`; ba phần | 25 câu, 10 điểm (16 + 2 + 4 + 3 câu TL × 1, không `omitted`, cảnh báo chấm tay); một phần 90′ |
| `TEST-SP2E-01`, `-02` | `SHARED`; ba phần; Toán 2026 có nhóm "Dữ liệu tổng hợp" ở Phần II với nhãn | `PER_PART` với 90′ ở một phần duy nhất; `TEST-SP2E-02` có `matrix_year` 2025 |
| `TEST-SP2E-03` | `SP2E_ANH_2026`: `SHARED`, một phần 60′, 9 nhóm có nhãn, không có nhóm Điền khuyết | `PER_PART`, 60′ ở một phần duy nhất; MĐ 30 + chùm 2×5 + ĐK 1 câu × 10 vị trí, có nhóm ĐK, không nhãn |
| `TEST-THPTQG-02` | Đọc hiểu có phân bố độ khó `REFERENCE` 2/2/1, nhóm có `templateGroupCode`, `publishable = false` | không có phân bố độ khó, không `templateGroupCode`; cờ lấy từ mẫu lúc kiểm tra |
| `TEST-THPTQG-03` | nhóm có `templateGroupCode` | không `templateGroupCode` |

## 5. Khi chuyên gia trả lời

- Sửa số trong mục 2 và migration mẫu cùng một PR (migration mới, không sửa migration đã merge, RULES §4). Ghi mã quyết định mới vào spec mục 1.
- Dựng lại bài `TEST-*` bằng `scripts/generate_test_exam_seed.py` theo số mới. Chạy lại `TestExamSeedIT`: bài hợp lệ phải vẫn VALID. Nếu không, tăng nguồn theo mục 4.3 trước.
- Xóa chữ "giả định" và `expert_note` ở dòng đã có số thật; giữ dòng nào chưa có.
- Bài lỗi vẫn dựng từ bài hợp lệ cùng kỳ thi, chỉ đổi một chỗ, và cập nhật cột "Mã thực tế" ở mục 4.2 từ kết quả chạy thật.
