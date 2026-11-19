# Đề test giả định cho 9 kỳ thi

Ngày: 2026-11-17. Cập nhật 2026-11-19 theo mã tại `380be740` (đợt 2B và hai phần bổ sung V-ACT/THPTQG, H-SCA/V-SAT, gồm FIX-QA, FIX-AUDIO, FIX-RELOAD) và theo UNI-2701 (đợt 3, TS): mục 4 mô tả bộ đề `TEST-*` dựng lại từ mẫu hiện hành. Trạng thái: số của mẫu theo BA; số của bộ đề `TEST-*` là giả định của đội dự án, chờ chuyên gia ra đề.

Tài liệu này mô tả đúng những gì đang có trong mã: 107 mẫu cấu trúc (cộng 9 mẫu bài ôn) và 27 bài `TEST-*` dùng cho E2E, kiểm tra cấu hình và dữ liệu mẫu. Đợt 3 (DS) lấy số ở đây. Khi mã đổi, sửa tài liệu theo mã, không làm ngược lại.

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
| `devdata/R__seed_test_exam_data.sql` | 27 bài `TEST-*` và 1.703 câu `TESTQ-*` (sinh bằng `scripts/generate_test_exam_seed.py`, đọc cấu trúc nguyên văn từ các migration mẫu) |
| `devdata/R__seed_questionbank_dev_data.sql` | câu hỏi mẫu có nhãn, MAPID, năng lực cho mọi mẫu mới (UNI-2653, UNI-2675) |
| `backend/src/test/java/vn/unicom/thi360/exam/TestExamSeedIT.java`, `TestExamSeedComplianceIT.java`, `TestExamSeedUpgradeIT.java`, `ExamTemplateFlagsCheckIT.java`, `ExamTemplatePolicyValidityIT.java` | kết quả kiểm tra mong đợi của từng bài `TEST-*`, khớp mẫu, nâng cấp seed 2B |
| `migration/exam/V202611182201__exam_template_block_locks.sql` | cờ `fixed_block_counts`, `omitted_locked`; mẫu `PART` được mang `subject_code` (H41–H43) |
| `migration/exam/V202611182210__exam_templates_hsca_vsat.sql` | mẫu H-SCA (11) và V-SAT (9), `HSCA_REVIEW`, `VSAT_REVIEW` (sinh bởi `hsca.templates.mjs`, `vsat.templates.mjs`) |
| `migration/exam/V202611182211__exam_templates_vsat_anh.sql` | V-SAT Tiếng Anh FULL và ba mẫu `PART` (sinh bởi `vsat-anh.templates.mjs`) |
| `devdata/assets/` + `questionbank/application/DevQuestionAssetSeeder.java` | 18 tệp MP3 mẫu và bộ nạp đưa chúng vào kho lưu trữ (hồ sơ `dev`, `test`) |

Ký hiệu: MĐ Một đáp án, NĐ Nhiều đáp án, ĐS Đúng/Sai, TLN Trả lời ngắn, TL Tự luận, GĐ Ghép đôi, ĐK Điền khuyết, chùm a×b là a bộ, mỗi bộ b câu con. Điểm viết `0,25` là điểm mỗi câu (`requiredPointsPerQuestion`). Số thập phân dùng dấu phẩy.

## 1. Quy ước chung

Các điểm này đang đúng trong mã, áp cho mọi mẫu và mọi bài `TEST-*`:

- Câu con của chùm Toán HSA là Một đáp án (`SINGLE_CHOICE`); mọi chùm của mẫu có `childAnswerType` (migration `V202611171108`).
- Nhiều đáp án mặc định `ALL_OR_NOTHING`.
- Các phần chạy theo thứ tự. Không có quy tắc chuyển phần (BA7).
- **Thứ tự và đảo (K4, K12).** Thứ tự nhóm là thứ tự trong mảng `blocks`; không còn `groupOrder` và `shuffle` (test `seededExamStructuresContainNoLegacyOrderingFields` bảo đảm không bài nào còn hai khóa này). Nhóm có `shuffleUnits` (mặc định `true`): câu và chùm đảo trong nhóm, câu con giữ thứ tự. Phương án đảo theo cờ `shuffleOptions` của từng câu (mặc định `true`, UNI-2648); `PaperOrdering` không đảo phương án khi cờ là `false`. Nhóm Đọc ngữ liệu (Văn HSA, QDA) có `shuffleEditable = true`, các nhóm khác không.
- **Không còn điều kiện dạng bài (K11).** Cột `task_type_item_id` đã bỏ (migration `V202611172352`). Nhóm cần đúng một loại câu thì gắn nhãn (`tagCodes`), MAPID hoặc năng lực (`competencyCodes`). Bài nền `TEST-*` mang nhãn, `slots`, MAPID và năng lực đúng như mẫu của chúng; `TEST-ERR-03` thêm nhãn `TEST-HIEM` vào nhóm Ngữ cảnh của Toán.
- Chỉ tiêu chủ đề là "ít nhất" (K6, H33), kể cả V-ACT và THPTQG.
- HSA Khoa học: học viên chọn 3 trong 5 môn (10 bộ ba). Mỗi môn có 17 câu; một môn trong bộ ba chỉ làm 16 (`countsByPick = [17, 17, 16]`, BA8, BA13).
- `omitted` chỉ hợp lệ ở nhóm `ESSAY`. Câu `omitted` không tính vào số câu, điểm, và không vào kiểm tra. SPT và SP2E Văn dùng `omitted` cho "Tự luận – tạm bỏ qua" (K1, K7).
- Đề `SHARED` có một đồng hồ chung, không lấy giờ từ dòng phần; đồng hồ trống hoặc không dương báo `INFO_MISSING` khi kiểm tra, kể cả sau khi nạp lại mẫu (FIX-RELOAD). Mọi mẫu H-SCA, V-SAT đều `SHARED` nên thời lượng của bài theo phần luôn nhập khi tạo.
- Mẫu có `fixed_block_counts` (H42): bài `SUBJECT` không đổi được dạng, số câu, số chùm, cỡ chùm của từng nhóm; đổi báo `BLOCK_COUNT_LOCKED`. Chỉ xét bài `SUBJECT`; bài `PART` và bài ôn nạp từ cùng mẫu không bị khóa.
- Mẫu có `omitted_locked` (H43): nhóm `omitted` của mẫu không bật lại được (`OMITTED_BLOCK_LOCKED`), và không tạo được bài `PART` từ phần chỉ gồm nhóm đã lược (`EXAM_PART_NOT_FOUND`). Hiện có ở `HSCA_SUBJECT_VAN`, `VSAT_SUBJECT_VAN`.
- Mục (section) có thể khai `audioRequirement` (`REQUIRED`, `FORBIDDEN`); chỉ H-SCA Tiếng Anh dùng. Phần có thể khai `displaySegments` (ranh giới hiển thị, không đổi thứ tự câu); chỉ V-SAT Tiếng Anh dùng.
- H-SCA chọn nhiều theo `mc_rule = PARTIAL` (tạm, Q-32); V-SAT Đúng/Sai theo bảng `COUNT_TABLE [1,2,3,6]` ở hồ sơ.
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
| 1 · Toán học và xử lí số liệu, 75 phút, 50 điểm | Chỉ tiêu dạng trả lời MĐ 32 / TLN 15 / Bộ câu hỏi 3 (UNI-2754, `V202611190100`: dòng Bộ câu hỏi đếm câu con của nhóm chùm, nên MĐ chỉ còn 32 câu đơn); lớp 10/11/12 = 3/20/27; độ khó NB/TH/VD/VDC = 2/21/20/7 (cả hai `REQUIRED`). Nhóm `HSA_MATH_SINGLE` MĐ 32; nhóm `HSA_MATH_SINGLE` TLN 15; nhóm `HSA_MATH_CONTEXT` chùm cuối 1×3 (câu con MĐ). Điểm mọi câu 1 |
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
| 1 · Toán, 80 phút, 50 điểm | Chỉ tiêu MĐ 32 / TLN 15 / Bộ câu hỏi 3 (UNI-2754); lớp 5/15/30 (`REQUIRED`); không khai độ khó. Nhóm `QDA_TOAN_SINGLE` MĐ 32, chùm `QDA_TOAN_CONTEXT` 1×3, `QDA_TOAN_SINGLE` TLN 15 (định dạng đáp án `INTEGER`, `DECIMAL`, `FRACTION`). Điểm 1 |
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

### 2.8 H-SCA

Nguồn: `V202611182210` (sinh bởi `hsca.templates.mjs`), `V202611182201` (cờ khóa), spec bổ sung `2026-11-18-pbi04-dot-2b-bo-sung-hsca-vsat.md` mục 4. Hồ sơ (`R__seed_exam_dev_data.sql`): `SHARED`, `template_by = SUBJECT`, `mc_rule = PARTIAL`, `max_score` trống (thang lấy từ mẫu). Bài `PART` cắt từ mẫu môn, `SHARED`, thời lượng nhập khi tạo.

| Mẫu | Phút | Điểm | Câu | Cờ và ghi chú |
|---|---|---|---|---|
| `HSCA_SUBJECT_TOAN`, `_LY`, `_HOA`, `_SINH` | 90 | 10 | 40 | `fixed_block_counts`; 3 phần × 0,25 điểm mỗi câu |
| `HSCA_SUBJECT_TOAN_SAMPLE`, `_LY_SAMPLE`, `_HOA_SAMPLE`, `_SINH_SAMPLE` | 90 | 10 | 40 | biến thể "Sát đề minh họa" (H45), `is_default = false`, `fixed_block_counts`; cùng cấu trúc, khác ma trận độ khó từng nhóm |
| `HSCA_SUBJECT_VAN` | nhập khi tạo | 20 | 20 | `omitted_locked`; Đọc hiểu + hai phần viết `omitted` |
| `HSCA_SUBJECT_ANH` | nhập khi tạo | 75 | 75 | Nghe `audioRequirement = REQUIRED`, Đọc `FORBIDDEN` |
| `HSCA_REVIEW` | – | – | – | khung bài ôn, `build_mode = REVIEW` |

Bốn môn tự nhiên (`HSCA_SUBJECT_{TOAN,LY,HOA,SINH}` và biến thể `_SAMPLE`), 3 phần một đồng hồ 90 phút:

| Phần | Nhóm | Số câu | Điểm |
|---|---|---|---|
| 1 · Trắc nghiệm | Chọn một, bốn phương án | 20 | 5 |
| 1 · Trắc nghiệm | Chọn nhiều | 5 | 1,25 |
| 2 · Dữ liệu tổng hợp | `QUESTION_SET` tổng 5 câu con, không ràng dạng | 5 | 1,25 |
| 3 · Điền đáp án | Trả lời ngắn | 10 | 2,5 |

Phân bố đối chiếu toàn môn (`examQuotas`, `basis = QUESTION`, `REFERENCE`):

| Môn | Lớp 10 / 11 / 12 | NB / TH / VD |
|---|---|---|
| Toán | 0 / 14 / 26 | 9 / 15 / 16 |
| Vật lí | 0 / 12 / 28 | 11 / 14 / 15 |
| Hóa học | 10 / 18 / 12 | 12 / 15 / 13 |
| Sinh học | 5 / 5 / 30 | 10 / 16 / 14 |

Ma trận độ khó từng nhóm của biến thể "Sát đề minh họa" (`_SAMPLE`, `mode = SEPARATE`, `REFERENCE`):

| Môn | Chọn một · 20 | Chọn nhiều · 5 | Chùm · 5 | Điền · 10 |
|---|---|---|---|---|
| Toán | 9 / 10 / 1 | 0 / 1 / 4 | 0 / 2 / 3 | 0 / 2 / 8 |
| Vật lí | 11 / 9 / 0 | 0 / 3 / 2 | 0 / 2 / 3 | 0 / 0 / 10 |
| Hóa học | 12 / 8 / 0 | 0 / 4 / 1 | 0 / 2 / 3 | 0 / 1 / 9 |
| Sinh học | 10 / 9 / 1 | 0 / 4 / 1 | 0 / 2 / 3 | 0 / 1 / 9 |

`HSCA_SUBJECT_VAN`: phần Đọc hiểu 20 câu, ba chùm 1×5 `VAN_THONG_TIN`, 1×7 `VAN_NGHI_LUAN`, 1×8 `VAN_THO`, câu con MĐ 1 điểm, ma trận độ khó từng nhóm lần lượt 2/3/0, 2/3/2, 2/4/2 (`REFERENCE`). Hai phần sau (`questionCount = 0`) mỗi phần một nhóm `ESSAY` `omitted` 1 câu; `omitted_locked` nên không bật lại được và không tạo được bài `PART` cho phần viết.

`HSCA_SUBJECT_ANH` (75 câu, mọi câu MĐ 1 điểm): phần Nghe 35 câu `audioRequirement = REQUIRED` — 8 câu đơn, chùm 3×4, chùm 3×5, câu con MĐ; phần Đọc 40 câu `FORBIDDEN` — chùm 4×10, câu con MĐ `itemShape.options = 4`. Không có mẫu `PART` riêng: bài theo phần cắt từ mẫu này.

### 2.9 V-SAT

Nguồn: `V202611182210` (sinh bởi `vsat.templates.mjs`), `V202611182211` (Tiếng Anh, sinh bởi `vsat-anh.templates.mjs`), spec bổ sung mục 5. Hồ sơ: `SHARED`, `template_by = SUBJECT`, `tf_scoring` `COUNT_TABLE [1,2,3,6]`. Cột "Lệnh" là số ý/lệnh chấm của bài (mẫu số đối chiếu theo `basis = PROMPT`).

| Mẫu | Phút | Điểm | Câu | Lệnh | Cờ |
|---|---|---|---|---|---|
| `VSAT_SUBJECT_TOAN` | 90 | 150 | 25 | 67 | `fixed_block_counts` |
| `VSAT_SUBJECT_LY`, `_HOA`, `_SINH`, `_SU`, `_DIA` | 60 | 150 | 25 | 67 | `fixed_block_counts` |
| `VSAT_SUBJECT_HOA_50` | 50 | 150 | 25 | 67 | biến thể 50 phút (Q-34), `is_default = false`, `fixed_block_counts` |
| `VSAT_SUBJECT_VAN` | nhập khi tạo | 120 | 20 | 62 | `fixed_block_counts`, `omitted_locked` |
| `VSAT_SUBJECT_ANH` | 60 | 150 | 25 | 67 | `fixed_block_counts`, một phần + `displaySegments` |
| `VSAT_PART_ANH_P2`, `_P3`, `_P4` | nhập khi tạo | theo phần | 6 / 5 / 5 | 6 / 20 / 5 | mẫu `PART` có `subject_code = ANH` (H41) |
| `VSAT_REVIEW` | – | – | – | – | khung bài ôn |

Sáu môn Toán, Vật lí, Hóa học, Sinh học, Lịch sử, Địa lí dùng chung khung bốn phần (Q-36, tạm):

| Phần | Nhóm | Câu | Lệnh | Điểm |
|---|---|---|---|---|
| I · Đúng/Sai | `TRUE_FALSE` `itemShape.statements = 4`, `expectedScoring` bảng `[1,2,3,6]` | 9 | 36 | 54 |
| II · Chọn một | chùm 2×3, câu con MĐ | 6 | 6 | 36 |
| III · Ghép đôi | `MATCHING` `itemShape.leftItems = 4` | 5 | 20 | 30 |
| IV · Trả lời ngắn | `SHORT_ANSWER` | 5 | 5 | 30 |

Mọi câu 6 điểm. Địa lí Phần II có hai `slots` theo nhãn `DIA_BANG_SO_LIEU` và `DIA_BIEU_DO` (Q-45).

`VSAT_SUBJECT_VAN`: Phần I–III như khung trên (Phần II có `slots` `VAN_THO`, `VAN_TRUYEN`), Phần IV `questionCount = 0` chứa một nhóm `ESSAY` `omitted` 1 câu 30 điểm — phần Viết lưu tham chiếu, không tính vào 20 câu, 62 lệnh, 120 điểm (H49), không bật lại được (`omitted_locked`), không tạo được bài `PART` riêng.

`VSAT_SUBJECT_ANH` (`V202611182211`): một phần duy nhất 25 câu 150 điểm — ĐS độc lập 7 × 6 (bảng `[1,2,3,6]`); bài đọc dùng chung chùm 1×8 × 6 điểm mỗi câu con với `childConditions` vị trí 1–2 `TRUE_FALSE` (`itemShape.statements = 4`), 3–8 `SINGLE_CHOICE`; `MATCHING` 5 × 6; `CLOZE` chùm 1×5, 6 điểm mỗi vị trí. `displaySegments` chỉ để hiển thị: P1 câu 1–9 (36 lệnh, 54 điểm), P2 10–15 (6, 36), P3 16–20 (20, 30), P4 21–25 (5, 30). Ba mẫu `PART` riêng có `subject_code = ANH`: `P2` chùm 1×6 MĐ (36 điểm), `P3` ghép đôi 5 × 6 (30), `P4` Đọc điền 1×5 (30); không có mẫu `P1`, tạo bài Phần I báo `EXAM_PART_NOT_FOUND`.

Phân bố đối chiếu theo lệnh (`examQuotas`, `basis = PROMPT`, `REFERENCE`):

| Môn | Lớp 10 / 11 / 12 | NB / TH / VD | Mẫu số |
|---|---|---|---|
| Toán | 0 / 17 / 50 | 13 / 35 / 19 | 67 |
| Vật lí | 0 / 25 / 42 | 19 / 32 / 16 | 67 |
| Hóa học | 0 / 23 / 44 | 14 / 32 / 21 | 67 |
| Sinh học | 0 / 13 / 54 | 15 / 28 / 24 | 67 |
| Lịch sử | 6 / 2 / 59 | 15 / 40 / 12 | 67 |
| Địa lí | không có | 20 / 42 / 5 | 67 |
| Tiếng Anh | không có | 21 / 39 / 7 | 67 |
| Ngữ văn | không có | 12 / 33 / 17 | 62 |

Mạch đối chiếu (`TopicQuota` cấp mục, `basis = PROMPT`, `REFERENCE`, `nodeIds` rỗng — chờ mapping): Ngữ văn 5 mạch Tùy bút, nhật kí 16; Văn bản thông tin 16; Truyện, kịch 15; Văn bản nghị luận 12; Thơ 3 (tổng 62). Địa lí 5 mạch: Các ngành kinh tế 26; Các vùng kinh tế 17; Dân cư 8; Tự nhiên 10; Kỹ năng bảng số liệu, biểu đồ, Atlat 6 (tổng 67). Tiếng Anh 3 mạch: Đọc hiểu 42; Giao tiếp qua văn bản 4; Ngữ pháp và từ vựng 21 (tổng 67).

### 2.10 Bài ôn và phạm vi

Nguồn: `V202611180456`, `V202611181305`, `V202611182210`, `V202611182211`. Mỗi kỳ thi có một mẫu `<KỲ>_REVIEW` (HSA, QDA, BCA, SP2E, SPT, THPTQG, VACT, HSCA, VSAT): `build_mode = REVIEW`, `scope = SUBJECT`, không số câu, không điểm, quản trị viên nhập. `VACT_REVIEW`, `HSCA_REVIEW` và `VSAT_REVIEW` đều bật `everyChildInScope` ở phạm vi của môn; cả hai mẫu mới này `timing = SHARED`, không đặt trước `duration_minutes` (quản trị viên nhập như mọi mẫu ôn). Phạm vi của hồ sơ kỳ thi (`exam_kind_profile.scopes`): HSA, QDA, BCA, V-ACT `FULL, PART, SUBJECT`; SPT, SP2E, THPTQG, HSCA, VSAT `SUBJECT, PART` (test `examKindProfilesSeedScopesPerSpec2B`).

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
| H-SCA bốn môn tự nhiên | phân bố lớp 10/11/12: định hướng BA 70–80% lớp 12, mức đối chiếu nạp số đề mẫu (Q-33); Toán 26/40 và Hóa 12/40 dưới 28 câu | `expert_note` của `HSCA_SUBJECT_*`, chờ BA |
| H-SCA Ngữ văn, Tiếng Anh | thang điểm bằng số câu, 1 điểm/câu (Q-39); số phương án của phần Nghe không ràng buộc | `expert_note` |
| V-SAT cơ cấu bốn phần | 9 + 6 + 5 + 5 (Q-36, tạm) | `expert_note` của `VSAT_SUBJECT_{TOAN,LY,HOA,SINH,SU,DIA}` và `VSAT_SUBJECT_HOA_50`, chờ BA |
| V-SAT Ngữ văn thời lượng | để trống, nhập khi tạo (Q-35); V-SAT Hóa 50' là biến thể tạm (Q-34) | `expert_note` |

Số còn lại của mẫu là số BA (không có `expert_note`), trừ ghi chú riêng của V-SAT Tiếng Anh: `VSAT_SUBJECT_ANH` có `expert_note` riêng (một phần thật, bốn dải hiển thị Phần I–IV; câu 8–15 dùng chung một bài đọc), trong đó cấu hình bài đọc dùng chung là Q-36, chờ BA, chưa phải số BA đã xác nhận (`V202611182211__exam_templates_vsat_anh.sql:4`, spec bổ sung mục 5.2). Không bịa thêm: chỗ BA chưa nêu, để trống và ghi vào mục câu hỏi mở của spec.

## 4. Bộ đề `TEST-*`

Bộ đề nằm trong `devdata/R__seed_test_exam_data.sql` (id cố định; sinh bằng `scripts/generate_test_exam_seed.py`, không sửa tay) và chạy cùng dữ liệu câu mẫu. Mọi bài ở `DRAFT`, `check_status = UNCHECKED`, `practice_scoring = RAW`; riêng `TEST-VACT-01` là `LINEAR`. Kết quả kiểm tra là kết quả mà `TestExamSeedComplianceIT` khẳng định trên dữ liệu seed thật, không phải số suy ra; cột "Kết quả" ghi bộ mã (cảnh báo gồm trong bộ mã, chỉ `ERROR` làm bài `INVALID`).

**Bài nền trùng mẫu (UNI-2701).** Mỗi bài nền (14 bài ở mục 4.1) là bản sao nguyên văn cấu trúc của mẫu hiện hành trong cơ sở dữ liệu sau mọi migration: cùng phần, mục, nhóm, nhãn, chùm, `slots`, ma trận, `examQuotas`, `templateGroupCode`, `omitted` và cùng id từng phần, mục, nhóm với mẫu, như bài tạo bằng API từ mẫu. Trình sinh đọc cấu trúc từ `V202611180652` (HSA, QDA, BCA, SPT, SP2E) và `V202611181305` (THPTQG, V-ACT), cộng bản vá điểm SP2E ở `V202611181234`; không tự dựng cấu trúc. Thời lượng, điểm, năm lấy từ dòng mẫu; chỗ khác mẫu nêu ở mục 4.4. DS lấy số HSA, QDA, BCA, SP2E, SPT ở mục 2; các bài này trùng số đó.

**Nâng cấp từ seed 2B.** Seed 2B ghi `structure_hash = 'seed-<mã thường>'` và `edited = false`; mọi lần ghi cấu trúc qua API thay dấu đó bằng SHA-256 và đặt `edited`. Seed mới ghi `seed3-<mã thường>`. PATCH thông tin chung (`code`, `name`, `description`, `instructions`, `durationMinutes`, `maxScore`, `referenceMax`, `practiceScoring`, `yearsConfirmed`) chỉ đổi dòng `exam.exam`, để nguyên dấu và `edited = false` của cấu trúc, nên dấu không đủ để biết admin đã sửa. Một bài được nâng cấp khi đủ mọi điều kiện: dấu `seed-<mã thường>`, `edited = false`, `status = 'DRAFT'`, chưa có dòng `exam_version`, và chín cột tiêu đề trên bằng đúng giá trị seed 2B ghi (bảng `WAVE_2B_HEADERS` của trình sinh, chép nguyên từ fixture `seed-2b`). Điều kiện được đánh giá một lần cho cả câu lệnh (một câu `WITH` mỗi bài). Với bài đủ điều kiện, seed thay `parts` và `exam_quotas`, đổi dấu sang `seed3-`, xóa `exam_block_ref` bản làm việc cũ (dòng mới chèn ngay sau đó), đặt lại tên và mô tả của bài mới (an toàn vì tiêu đề còn đúng bản 2B, không đè chữ của admin) và làm mới các cột suy ra từ mẫu và cấu trúc: `template_code`, `structure_year`, `matrix_year`, `timing`, `duration_minutes`, `max_score`, `question_count`, `has_essay`, `has_english_option`, `combination_count` (đọc từ danh mục hiện hành, xem 4.4); `check_status` khác `UNCHECKED` thành `NEEDS_RECHECK` (dòng `exam_check_run` giữ nguyên), `revision` tăng 1 đúng một lần. Bài admin đã sửa cấu trúc (PUT) hay tiêu đề (PATCH), đã công bố, có bản công bố hay đã nạp lại mẫu không bị đụng; chạy lần hai không còn dấu `seed-` nên không đổi gì. Chọn dấu cộng so tiêu đề với bản chụp 2B thay vì so nội dung cấu trúc, để khỏi so JSON với dạng đã chuẩn hóa v2. Bước nâng cấp giả định không có tiến trình API cũ nào ghi trong lúc Flyway chạy (dev/demo triển khai lại bằng container mới); đầu tệp seed ghi rõ điều này.

### 4.1 Bài hợp lệ

Cột Câu là số câu học viên làm, Điểm là cột `max_score` của bài (ghi riêng khi khác). Phút chép từ cột `duration_minutes` của bài.

| Mã | Mẫu (nguồn cấu trúc) | Phạm vi | Timing, phút | Điểm | Câu | Dùng để kiểm | Kết quả |
|---|---|---|---|---|---|---|---|
| `TEST-HSA-01` | `HSA_FULL` | FULL | `PER_PART`, 195 | 150 | 150 | 3 phần; phần 3 chọn 1: Khoa học (10 bộ ba môn) hoặc Tiếng Anh; Văn có `slots`; `examQuotas` Tiếng Anh | VALID; `EXAM_QUOTA_DEVIATION` (cảnh báo) |
| `TEST-HSA-02` | `HSA_PART_MATH` | PART `TOAN` | `PER_PART`, 75 | 50 | 50 | chỉ tiêu 32 + 15 + Bộ câu hỏi 3, lớp và độ khó hai chiều, chùm 1×3 | VALID, không mã |
| `TEST-HSA-03` | `HSA_PART_LITERATURE` | PART `VAN` | `PER_PART`, 60 | 50 | 50 | 4 nhóm câu đơn có nhãn, chùm 5×5 có 5 `slots` | VALID, không mã |
| `TEST-HSA-04` | `HSA_PART_SCIENCE` | PART `SCIENCE` | `PER_PART`, 60 | 50 | 50 | `SUBJECT_CHOICE`, tỉ lệ ra 17/17/16, ma trận riêng từng môn, 10 bộ ba | VALID, không mã; 10 bộ ba đều VALID |
| `TEST-QDA-01` | `QDA_FULL` | FULL | `PER_PART`, 195 | 150 | 150 | mã tổ hợp 5 (`QD01`–`QD05`), `codeSource` `GLOBAL_LIST` | VALID, không mã |
| `TEST-BCA-01` | `BCA_FULL` | FULL | `SHARED`, 180 | 100 | 81 | đồng hồ chung, Anh 0,75, TL 25 điểm, 4 mã `CA1`–`CA4`, `examQuotas` | VALID; `MANUAL_GRADING_REQUIRED`, `DISTRIBUTION_DEVIATION`, `EXAM_QUOTA_DEVIATION` (cảnh báo) |
| `TEST-VACT-01` | `VACT_FULL` | FULL | `SHARED`, 150 | `max_score` NULL; `reference_max` 1.200 (mốc quy đổi `LINEAR`); tổng điểm thô 1.200 (120 câu × 10, mẫu `max_score` 1.200) | 120 | 120 câu, `LINEAR`, Logic và Suy luận giải chung một đơn vị kiểm tra | VALID; chỉ được có cảnh báo `DISTRIBUTION_DEVIATION` |
| `TEST-SPT-01` | `SPT_MATH_2025` | SUBJECT `TOAN` | `PER_PART`, 90 (mẫu không đặt) | 7 | 22 | 3 phần, Tự luận 3 câu `omitted` | VALID; `OMITTED_GROUPS_PRESENT` (cảnh báo) |
| `TEST-SP2E-01` | `SP2E_MATH_2025` | SUBJECT `TOAN` | `SHARED`, 90 | 10 | 22 | phiên bản 2025 | VALID, không mã |
| `TEST-SP2E-02` | `SP2E_MATH_2026`, `years_confirmed = true` | SUBJECT `TOAN` | `SHARED`, 90 | 10 | 40 | đổi phiên bản 2025 → 2026, Nhiều đáp án, Phần II dữ liệu tổng hợp có nhãn | VALID; `EXAM_QUOTA_DEVIATION` (cảnh báo) |
| `TEST-SP2E-03` | `SP2E_ANH_2026` | SUBJECT `ANH` | `SHARED`, 60 | 10 | 50 | 9 nhóm có nhãn × 0,2 (mục 2.6) | VALID; `EXAM_QUOTA_DEVIATION` (cảnh báo) |
| `TEST-THPTQG-01` | `THPTQG_MATH_2025` | SUBJECT `TOAN` | `SHARED`, 90 | 10 | 22 | ĐS bảng điểm, 3 phần, E2E; không có cờ chính sách mẫu | VALID, không mã, công bố được |
| `TEST-THPTQG-02` | `THPTQG_LITERATURE_2025` | SUBJECT `VAN` | `PER_PART`, 120 | 10 | 7 | chùm có câu con TL; **chặn công bố** | **INVALID** (`TEMPLATE_NOT_PUBLISHABLE`; cảnh báo `MANUAL_GRADING_REQUIRED`, `DISTRIBUTION_DEVIATION`), lưu nháp được |
| `TEST-THPTQG-03` | `THPTQG_INFORMATICS_2025` | SUBJECT `INFORMATICS` | `SHARED`, 50 | 10 | 28 (in 30) | nhánh CS/ICT, 28 phải làm | VALID, không mã |

Cấu trúc từng bài nền là cấu trúc của mẫu ở cột "Mẫu" (mục 2), id trùng mẫu; bốn chỗ cần nhắc:

| Mã | Cấu trúc |
|---|---|
| `TEST-HSA-01` | `HSA_FULL` mục 2.1: ba phần, phần 3 chọn 1 giữa phương án `SCIENCE` (`SUBJECT_CHOICE`, chọn 3, `countsByPick` [17, 17, 16]) và phương án `ANH` (11 nhóm có nhãn); hai `examQuotas` tham khảo của Tiếng Anh |
| `TEST-HSA-04` | `HSA_PART_SCIENCE`: chỉ có phương án `SCIENCE` (không có nhánh Tiếng Anh, nhánh này thuộc `HSA_PART_ENGLISH`) |
| `TEST-QDA-01` | `QDA_FULL` mục 2.2: phần 3 không có `options`, có `codeSource` `GLOBAL_LIST` và `subjectPool` 5 môn × 25 và Tiếng Anh 50; cột `combination_count` = 5 |
| `TEST-BCA-01` | `BCA_FULL` mục 2.3: Toán 27 + chùm 1×3 + TLN 5, Sử 7 + chùm 1×3, Anh 5 nhóm có nhãn × 0,75, `subjectPool` Lí, Hóa, Sinh, Địa mỗi môn 12 + chùm 1×3; hai `examQuotas` (`bca-grade`, `bca-level`); `combination_count` = 4 |

### 4.2 Bài lỗi

Mỗi bài dựng từ một bài hợp lệ, chỉ đổi một chỗ (chỗ đó là hàm `errNN` tương ứng trong trình sinh; `TestExamSeedIT.errorFixturesDifferByOneChange` khẳng định bài lỗi và bài gốc chỉ khác chỗ đó). Cột "Mã thực tế" là bộ mã mà `TestExamSeedComplianceIT.errorFixturesReportExactCodes` khẳng định (so bằng tập, không thừa, không thiếu); một chỗ sửa thường kéo theo vài mã hệ quả. Bài dựng từ bài nền có cảnh báo (BCA, SP2E) mang theo cảnh báo của bài nền.

| Mã | Dựng từ | Đổi gì | Mã chính | Mã thực tế |
|---|---|---|---|---|
| `TEST-ERR-01` | `TEST-THPTQG-01` | nhóm MĐ có `matrix` (lớp 12 = 12) nhưng chưa chọn cách áp (chung hay riêng) | `MATRIX_INCOMPLETE` | `MATRIX_INCOMPLETE` |
| `TEST-ERR-02` | `TEST-BCA-01` | cả năm nhóm Tiếng Anh đổi 0,75 → 1 (Phần 2: 65 > 60; tổng 105 ≠ 100) | `PART_POINTS_MISMATCH` | `PART_POINTS_MISMATCH`, `POINTS_TOTAL_MISMATCH` và các cảnh báo của `TEST-BCA-01` (`MANUAL_GRADING_REQUIRED`, `DISTRIBUTION_DEVIATION`, `EXAM_QUOTA_DEVIATION`) |
| `TEST-ERR-03` | `TEST-HSA-02` | nhóm Ngữ cảnh của Toán (`HSA_MATH_CONTEXT`, 1 chùm 3 câu, không có `slots`) thêm nhãn `TEST-HIEM`; không chùm Toán nào mang nhãn này | `SET_SHORTAGE` | `SET_SHORTAGE`, không mã khác. Dựng từ `TEST-HSA-02` vì nhóm có `slots` (Đọc ngữ liệu của `TEST-HSA-03`) báo `SLOT_SOURCE_SHORTAGE` theo slot, còn `SET_SHORTAGE` chỉ ra ở nhóm chùm không có `slots` |
| `TEST-ERR-04` | `TEST-HSA-02` | nhóm MĐ 31 thay 32 (môn 49 câu) | `SECTION_TOTAL_MISMATCH` | `SECTION_TOTAL_MISMATCH`, `PART_POINTS_MISMATCH`, `PATH_TOTAL_MISMATCH`, `POINTS_TOTAL_MISMATCH`, `CELL_SHORTAGE` |
| `TEST-ERR-05` | `TEST-HSA-02` | chỉ tiêu TLN 14 (32 + 14 + 3 = 49) | `ANSWER_QUOTA_TOTAL_MISMATCH` | `ANSWER_QUOTA_TOTAL_MISMATCH`, `CELL_SHORTAGE` |
| `TEST-ERR-06` | `TEST-HSA-02` | khối Trả lời ngắn (15 câu) đổi `questionType` thành Một đáp án; chỉ tiêu Trả lời ngắn 15 của môn Toán không còn khối nào nhận | `ANSWER_TYPE_SHORTAGE` | `ANSWER_TYPE_SHORTAGE`, không mã khác. Thu hẹp phạm vi MAPID không tới được mã này: bộ giải kiểm nguồn của từng nhóm trước chỉ tiêu loại đáp án (`SubproblemSolver.insufficiencyReason`, vòng nhóm trước vòng chỉ tiêu), nên thiếu TLN dưới nút báo `CELL_SHORTAGE` ở nhóm; chỉ tiêu loại đáp án chỉ báo khi mọi nhóm đủ nguồn mà chỉ tiêu không có câu |
| `TEST-ERR-07` | `TEST-HSA-04` | Địa lí đặt phạm vi nút `TEST-DIA-HEP`: dưới nút có 13 câu MĐ dùng được và 2 chùm, nhóm MĐ của mẫu cần 14 khi Địa lí nhận 17 câu (thiếu một câu) | `CELL_SHORTAGE` | `CELL_SHORTAGE`; 10 bộ ba, **6 bộ có Địa lí INVALID**, 4 bộ còn lại VALID |
| `TEST-ERR-08` | `TEST-HSA-04` | Vật lí: nhóm TLN bỏ `templateGroupCode` (thành nhóm còn lại). **Hiện VALID**: mã `REMAINDER_GROUP_INVALID` không còn trong mã | không còn lỗi | VALID, không mã; 10 bộ ba đều VALID. Giữ làm bài đối chứng |
| `TEST-ERR-09` | `TEST-HSA-04` | Hóa học khai chỉ tiêu dạng trả lời 12 + 4 = 16 cho môn 17 câu | `ANSWER_QUOTA_TOTAL_MISMATCH` | `ANSWER_QUOTA_TOTAL_MISMATCH`, `CELL_SHORTAGE`; 6 bộ ba có Hóa học INVALID |
| `TEST-ERR-10` | `TEST-HSA-04` | `countsByPick` = 17, 17, 17 | `SUBJECT_CHOICE_COUNTS_INVALID` | `SUBJECT_CHOICE_COUNTS_INVALID`, `PART_EMPTY`, `PART_POINTS_MISMATCH`, `PART_TOTAL_MISMATCH`, `PATH_TOTAL_MISMATCH`, `POINTS_TOTAL_MISMATCH` |
| `TEST-ERR-11` | `TEST-THPTQG-01` | ô Lớp 12 × VDC của nhóm MĐ đặt 12 câu (bộ mẫu có tối đa 4 câu ô này) | `CELL_SHORTAGE` | `CELL_SHORTAGE` (`required` 12, `available` 4) |
| `TEST-ERR-12` | `TEST-THPTQG-01` | nhóm TLN bỏ điểm mỗi câu | `POINTS_TOTAL_MISMATCH` | `POINTS_TOTAL_MISMATCH` |
| `TEST-ERR-13` | `TEST-SP2E-02` | `years_confirmed = false` (cấu trúc 2026, ma trận 2025) | `YEARS_UNCONFIRMED` | `YEARS_UNCONFIRMED` và cảnh báo `EXAM_QUOTA_DEVIATION` của `TEST-SP2E-02` |

Mã `SUBJECT_QUOTA_COUNT_VARIES` mà bản cũ của tài liệu ghi cho `TEST-ERR-09` không còn trong mã.

### 4.3 Nguồn câu hỏi của bộ đề

Quy tắc của plan BE-2a (`docs/plans/2026-11-16-be-pbi-03-04.md`): mỗi ô, mỗi mức điểm có số câu `READY` ít nhất gấp đôi chỉ tiêu. Nguồn lọc theo môn, loại câu và điểm (và nhãn, MAPID khi nhóm có), không theo kỳ thi, nên bài khác kỳ thi dùng chung nguồn khi trùng bộ lọc. Không còn lọc theo dạng bài (K11).

Có 1.703 câu `TESTQ-*`: 1.507 `READY`, 98 `DRAFT`, 98 `RETIRED`. Câu Nháp và Ngừng sử dụng đi kèm mỗi dòng để chắc chúng không được đếm. Sinh bằng `generate_series` trong `scripts/generate_test_exam_seed.py`, không viết tay. Cột "Tối thiểu" là số mà `TestExamSeedIT.readyPoolsMeetAssumedMinimums` khẳng định; "Có" là số `READY` đếm trên cơ sở dữ liệu đã nạp seed. Đơn vị là đơn vị đếm (câu đơn, câu con, vị trí Điền khuyết); "bộ" là chùm nguyên. Từ UNI-2701, mọi nhóm Văn, Tiếng Anh và V-ACT của mẫu có nhãn hoặc năng lực nên nguồn của chúng là `R__seed_questionbank_dev_data.sql`; họ `TESTQ-*` của các môn đó giữ lại (cột "Dùng cho" ghi "chưa nhóm nào dùng") và không đổi mã. Câu mới chỉ thêm ở cuối trình sinh để mã của câu cũ không đổi.

| Môn | Loại, điểm | Tối thiểu | Có | Dùng cho |
|---|---|---|---|---|
| Toán | MĐ đơn 1 điểm | 100 | 168 (60 gắn nút `TEST-TOAN-HEP`) | HSA và QDA 32; BCA 27 |
| Toán | TLN 1 điểm | 30 | 50 (10 gắn `TEST-TOAN-HEP`) | HSA 15; BCA 5 |
| Toán | chùm 3 câu con MĐ 1 điểm | 6 bộ | 12 | HSA, QDA, BCA mỗi bài 1 bộ |
| Toán | MĐ 0,25 | 40 | 41 (bốn câu Lớp 12 × VDC: ba câu theo vòng sinh, một câu `TESTQ-TOAN-MC025-259` chuyển bằng UPDATE, cho `TEST-ERR-11`) | SP2E 2026: 20; SPT: 16 |
| Toán | ĐS 1 điểm (`COUNT_TABLE`) | 8 | 8 | THPTQG, SP2E 2025: 4; SPT: 2 |
| Toán | TLN 0,25 / TLN 0,5 | 20 / 12 | 20 / 12 | SP2E 2026: 10; SPT: 4 / THPTQG, SP2E 2025: 6 |
| Toán | NĐ 0,25 `ALL_OR_NOTHING`; chùm 5 câu con 0,25 | 10; 2 bộ | 10; 2 bộ | SP2E 2026 |
| Toán | TL 1 điểm (có hướng dẫn chấm); MĐ 10 điểm | 6; 60 | 6; 60 | chưa nhóm nào dùng (TL của SPT là `omitted`; V-ACT lấy nguồn ở ngân hàng mẫu) |
| Ngữ văn | MĐ đơn 1 điểm | 100 | 100 | chưa nhóm nào dùng |
| Ngữ văn | chùm 5 câu con MĐ 1 điểm | 10 bộ | 10 (đúng 2 bộ, `TESTQ-VAN-PASSAGE-S001` và `-S002`, gắn nhãn `TEST-HIEM`; `TEST-ERR-03` đòi nhãn này ở nhóm Toán nên không bộ Toán nào thỏa) | chưa nhóm nào dùng |
| Ngữ văn | chùm 5 câu con TL 0,8 | 2 bộ | 2 | THPTQG; không gắn nhãn |
| Ngữ văn | TL 25 / TL 2 / TL 4 | 2 / 2 / 2 | 2 / 2 / 2 | THPTQG: TL 2 và TL 4; TL 25 chưa nhóm nào dùng (Phần I của BCA có nhãn) |
| Tiếng Anh | MĐ đơn 1 điểm | 100 | 100 | chưa nhóm nào dùng |
| Tiếng Anh | chùm 5 câu con MĐ 1 điểm | 6 bộ | 6 | chưa nhóm nào dùng |
| Tiếng Anh | MĐ 0,75 / MĐ 10 | 40 / 60 | 40 / 60 | chưa nhóm nào dùng |
| Tiếng Anh | MĐ 0,2; chùm 5 câu con 0,2; ĐK 2 điểm | 60; 4 bộ; 2 câu | 60; 4; 2 | chưa nhóm nào dùng |
| Lí, Hóa, Sinh, Sử, Địa (mỗi môn) | MĐ đơn 1 điểm | 50 | 50 (Địa 64: 14 gắn `TEST-DIA-HEP`, trong đó 13 dùng được cho ma trận) | HSA 14; QDA 21–22; BCA Sử 7 |
| Lí, Hóa, Sinh, Sử, Địa (mỗi môn) | TLN 1 điểm; chùm 3 câu con MĐ 1 điểm | 8; 4 bộ | 8; 4 (Địa 12 và 6) | HSA: Lí, Hóa, Sinh mỗi môn 1 TLN; mọi môn 1 chùm |
| Tiếng Việt, Tư duy khoa học | MĐ 10 điểm | 60 mỗi môn | 60 | V-ACT |
| Tin học | MĐ 0,25; ĐS 1 điểm | 48; 8 | 48; 8 | THPTQG 24; 2 chung + 2 mỗi nhánh |

Các nhóm có nhãn `ANH_*` và năng lực `VACT-*` của `TEST-VACT-01` lấy nguồn từ `R__seed_questionbank_dev_data.sql` (câu có nhãn, MAPID, năng lực), không từ `TESTQ-*` (ngoài `TEST-HIEM`). Họ `TESTQ-DIA-MC1-HEP` có 14 câu: 6 câu đã có từ đợt 2B (một câu G12 × VDC không vào được ô nào của ma trận) và 8 câu thêm ở UNI-2701 cho `TEST-ERR-07`.

### 4.4 Chỗ bài `TEST-*` còn lệch mẫu hiện hành

Cấu trúc của bài nền (phần, nhóm, nhãn, chùm, `slots`, ma trận, `examQuotas`, `templateGroupCode`, `omitted`, id) trùng mẫu từng dòng; `TestExamSeedIT.everyTestExamMatchesTemplate` khẳng định. Chỉ còn bốn chỗ khác có chủ ý, đều ở dòng bài thi, không ở cấu trúc:

| Bài | Mẫu hiện hành | Bài `TEST-*` | Lý do |
|---|---|---|---|
| `TEST-VACT-01` | `max_score` 1.200 | `max_score` NULL, `reference_max` 1.200 | bài V-ACT quy đổi `LINEAR` về mốc, không có thang cố định (spec 2B mục 5.1) |
| `TEST-SPT-01` | không đặt `duration_minutes` (quản trị viên nhập) | 90 | `PER_PART` mà không phần nào có phút; bài cần một số để chạy thử, số giả định |
| `TEST-SP2E-02` | `matrix_year` 2026 | `matrix_year` 2025, `years_confirmed = true` | bài gốc hợp lệ của `TEST-ERR-13` (cùng bài, `years_confirmed = false`) |
| `TEST-QDA-01`, `TEST-BCA-01` | phần có `codeSource` | `combination_count` 5 và 4 (trên danh mục chưa sửa) | số mã tổ hợp đang hoạt động của kỳ thi, đếm theo mã khác nhau như `ExamService.summarize` qua `CatalogLookup.listCombinations` (nhóm `SUBJECT_COMBINATION` hoạt động, bản hiện hành của mã thuộc kỳ thi, kỳ thi hoạt động, mã và tổ tiên hoạt động); seed tự đọc danh mục lúc chạy, cả khi nâng cấp, nên danh mục có 6 mã QDA thì bài ghi 6 (`QD01`–`QD05`, `CA1`–`CA4` ở danh mục mẫu) |

**Mã QDA (FU-FE4-D9).** Bài `TEST-QDA-01` không còn phương án `QĐ0x` trong cấu trúc: như mẫu, phần 3 là `codeSource = GLOBAL_LIST` nên mã đọc từ danh mục. Mã của danh mục là `QD01`–`QD05` (ASCII) và tên `QĐ0x · …` (`R__seed_catalog_dev_data.sql`); `R__seed_test_exam_data.sql` chỉ chèn lại `QD05` khi danh mục chưa có, đúng mã và tên đó.

### 4.5 Câu mẫu cho H-SCA và V-SAT (UNI-2691, spec `2026-11-18-pbi04-dot-2b-bo-sung-hsca-vsat.md` mục 4, 5)

Nằm trong `R__seed_questionbank_dev_data.sql`, mã câu `SEED-HSCA-*` và `SEED-VSAT-*`, trạng thái `READY`. Mỗi dòng có nhiều hơn mức mẫu cần để bộ giải còn chỗ chọn. Mọi nội dung là chữ ASCII ngắn do đội tự soạn, không lấy từ đề thật. Câu trắc nghiệm dùng bốn phương án; lớp và độ khó rải đều ba lớp 10, 11, 12 và ba mức NB, TH, VD. Câu Văn và Tiếng Anh của H-SCA seed nhãn lớp 12, mức TH: Văn vì mẫu tắt đối chiếu cấp lớp của cả bài (không có `examQuotas`; ba nhóm Đọc hiểu vẫn giữ ma trận độ khó `REFERENCE` của riêng mình, mục 2.8), còn Tiếng Anh vì mẫu không đặt phân bố lớp lẫn độ khó nào.

**H-SCA** (điểm theo Q-39, chùm theo Q-40):

| Môn | Dòng của mẫu | Dữ liệu mẫu | Mẫu cần |
|---|---|---|---|
| Toán, Vật lí, Hóa học, Sinh học (mỗi môn) | Chọn một, bốn phương án, 0,25 | 24 câu đơn | 20 |
| | Chọn nhiều, 0,25 (`PARTIAL`) | 8 câu đơn | 5 |
| | Chùm phần 2 | 4 bộ, mỗi bộ 5 câu con chọn một 0,25 | 5 câu con từ các chùm nguyên; không chốt số/cỡ chùm |
| | Trả lời ngắn, 0,25 | 12 câu đơn | 10 |
| Ngữ văn | Chùm `VAN_THONG_TIN`, `VAN_NGHI_LUAN`, `VAN_THO` | mỗi nhãn 3 bộ: 5, 7, 8 câu con chọn một 1 điểm | 1 bộ mỗi nhãn |
| Tiếng Anh, Nghe | 8 câu đơn có audio | 10 câu đơn chọn một 1 điểm, mỗi câu một tệp audio | 8 |
| | 3 chùm × 4 câu con có audio | 4 bộ, audio ở câu gốc của bộ | 3 |
| | 3 chùm × 5 câu con có audio | 4 bộ, audio ở câu gốc của bộ | 3 |
| Tiếng Anh, Đọc | 4 chùm × 10 câu con, không audio | 6 bộ, bốn phương án, 1 điểm | 4 |

**V-SAT** (điểm theo spec 5.1, 5.2):

| Môn | Dòng của mẫu | Dữ liệu mẫu | Mẫu cần |
|---|---|---|---|
| Cả tám môn | Đúng/Sai bốn ý, 6 điểm, bảng `[1,2,3,6]` | 12 câu đơn | 9 (Tiếng Anh 7) |
| | Ghép hợp, 4 mục mỗi bên, 1,5 điểm mỗi cặp | 10 câu đơn (6 điểm mỗi câu) | 5 |
| Toán, Lí, Hóa, Sinh, Sử, Địa | Trả lời ngắn, 6 điểm | 10 câu đơn | 5 |
| | Phần II, chùm 3 câu con chọn một, 6 điểm mỗi câu con | 4 bộ | 2 |
| Địa lí | Phần II hai vị trí theo nhãn | 4 bộ: 2 bộ gốc gắn `DIA_BANG_SO_LIEU`, 2 bộ gốc gắn `DIA_BIEU_DO`; câu con không nhãn | 1 bộ mỗi nhãn (2 bộ tổng) |
| Ngữ văn I–III | Phần II, chùm 3 câu con | 3 bộ `VAN_THO`, 3 bộ `VAN_TRUYEN` | 1 mỗi nhãn |
| Tiếng Anh | Bài đọc dùng chung câu 8–15 | 3 bộ 8 câu con: vị trí 1–2 Đúng/Sai bốn ý, vị trí 3–8 chọn một, 6 điểm mỗi câu con | 1 |
| | Phần II theo phần | 3 bộ 6 câu con chọn một, 6 điểm | 1 |
| | Cloze | 3 câu, 5 vị trí, 6 điểm mỗi vị trí | 1 |

**Audio.** 18 tệp MP3 đơn kênh trong `backend/src/main/resources/db/devdata/assets/` (`hsca-en-listening-single-01..10`, `-set4-01..04`, `-set5-01..04`), mỗi tệp khoảng 2 giây âm đơn tự sinh (luồng báo 2,088 s, `ffprobe`), đúng 6.264 byte, cộng 112.752 byte. Dòng `question_asset` (`kind = ATTACHMENT`, `mime = audio/mpeg`, `object_key = questions/dev-seed/<tên tệp>`) cùng `question_asset_ref` phiên bản 1 nằm trong tệp `R__` ở trên; `DevQuestionAssetSeeder` (hồ sơ `dev`, `test`) ghi từng tệp vào kho lưu trữ theo `object_key` của các dòng đó, bỏ qua tệp đã có. Tệp audio chỉ gắn vào câu gốc: câu đơn Nghe có một tệp, chùm Nghe có một tệp ở câu gốc, câu con không có tệp.

## 5. Khi chuyên gia trả lời

- Sửa số trong mục 2 và migration mẫu cùng một PR (migration mới, không sửa migration đã merge, RULES §4). Ghi mã quyết định mới vào spec mục 1.
- Dựng lại bài `TEST-*` bằng `scripts/generate_test_exam_seed.py` theo số mới (chạy hai lần phải ra cùng tệp; `git diff --exit-code` sau lần chạy). Chạy lại `TestExamSeedIT` và `TestExamSeedComplianceIT`: bài hợp lệ phải vẫn VALID. Nếu không, tăng nguồn theo mục 4.3 trước (thêm ở cuối trình sinh, mã cũ giữ nguyên).
- Xóa chữ "giả định" và `expert_note` ở dòng đã có số thật; giữ dòng nào chưa có.
- Bài lỗi vẫn dựng từ bài hợp lệ cùng kỳ thi, chỉ đổi một chỗ, và cập nhật cột "Mã thực tế" ở mục 4.2 từ kết quả chạy thật. Seed đổi cấu trúc thì phải kèm bước nâng cấp bài mang dấu seed cũ (mục 4).
