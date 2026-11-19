# Đợt 2B, phần bổ sung: cấu trúc H-SCA và V-SAT

Ngày: 2026-11-18. Trạng thái: đã duyệt ngày 2026-11-18 sau hai vòng rà độc lập. Issue: UNI-2687. Căn cứ: D16 (2026-11-18, "Update làm luôn đợt này đi"), D17 (Advisor chốt tạm Q-32 đến Q-36, chọn phương án B cho BE-21), D18 (giữ 160 điểm, H48 chỉ xét tệp đính kèm) và D19 (người dùng chốt Q-38 theo đề xuất).

Tài liệu này là **phần sửa** của `docs/specs/2026-11-17-pbi04-dot-2b.md` (**2B**) và phần bổ sung THPT, V-ACT (UNI-2671). Mục nào không nhắc ở đây thì giữ nguyên.

**Nguồn:** ghi chú BA `uninote-pbi04-cau-truc-hsca.md` (bản 10:43, **HS**) và `uninote-pbi04-cau-truc-vsat.md` (bản 15:13, **VS**). Bảng đối chiếu từng dòng: `gap.md` (mã HS-nn, VS-nn).

Những chỗ ghi **(suy luận)** là cách làm do agent đề xuất, ghi chú không nói trực tiếp.

## 1. Quyết định bị thay

| Quyết định cũ | Thay đổi |
|---|---|
| 2B mục 2 và D16: đợt có 7 kỳ thi | Đợt có **9 kỳ thi**, thêm `HSCA`, `VSAT` |
| 2B mục 5.1, bảng phạm vi | Thêm hai dòng: `HSCA` và `VSAT` có `SUBJECT`, `PART` và bài ôn |
| Ràng buộc `chk_exam_template_scope_fields`: mẫu `PART` không có mã môn | Mẫu `PART` **được có** `subject_code` (H41) |
| `ExamStructureValidator.validateExamQuotas`: chỉ tiêu cấp bài chỉ theo `QUESTION` | Cho thêm `PROMPT` khi `enforcement = REFERENCE` (H44). `REQUIRED` vẫn chỉ theo `QUESTION` |
| `ExamStructureValidator.validateTopicQuotas`: chỉ tiêu chủ đề chỉ theo `QUESTION` | Cho thêm `PROMPT` khi `enforcement = REFERENCE` (H44), để nạp mạch đối chiếu V-SAT theo lệnh. `REQUIRED` vẫn chỉ theo `QUESTION` |
| `ExamService.assertTemplateMatches`: bài `PART` nạp mẫu `SUBJECT` chỉ cho `SP2E`, `SPT`, `THPTQG` | Cho mọi kỳ thi có `template_by = SUBJECT`, gồm `HSCA`, `VSAT` |

Giữ nguyên: K1, K3, K6 (D13), K7, K10, H5, H6, H7, H8, H17, H20, H24, H27, H28, H31 (D12), H34, H35, D3, D11.

## 2. Quyết định mới của agent

| # | Nội dung | Quyết định |
|---|---|---|
| H36 | Mã kỳ thi | `HSCA`, `VSAT`. Hồ sơ: `scopes {SUBJECT,PART}`, `template_by = SUBJECT`, `timing = SHARED`, `practice_scoring = RAW`, `max_score = NULL` (thang lấy từ mẫu vì khác nhau theo môn). H-SCA `mc_rule = PARTIAL` (tạm, Q-32). V-SAT `tf_scoring` `COUNT_TABLE [1,2,3,6]` |
| H37 | Từ của BA | "FULL" là `SUBJECT`; "theo phần" là `PART`; "chủ đề" là bài ôn `REVIEW` (`HSCA_REVIEW`, `VSAT_REVIEW`) |
| H38 | Điều kiện audio | Trường `ExamSection.audioRequirement` (`ANY`, `REQUIRED`, `FORBIDDEN`); các nhóm trong mục kế thừa, không chỉnh theo từng nhóm. Xét tệp MIME `audio/%` **ở câu gốc** vì câu con không có tệp đính kèm (`QuestionBody.ChildQuestion`). `QuestionSourcePort.Unit.hasAttachment` đã có nhưng đúng với mọi loại tệp (PDF, ảnh), nên không dùng được; phải nối `question_asset` để lấy MIME |
| H39 | Chùm trộn dạng | Mở rộng `ChildCondition` thêm `answerType`, `itemShape`, không tạo khái niệm mới. Điều kiện theo vị trí câu con trong chùm. Nhóm trộn dạng để `childAnswerType` trống; các điều kiện có `answerType` phải phủ kín vị trí 1 đến `setSize`, không chồng nhau, để tính được số lệnh của nhóm |
| H40 | Bài đọc dùng chung của Tiếng Anh V-SAT | **Phương án B (D17):** bài FULL là **một `ExamPart`, một mục, bốn nhóm** đúng bảng VS §4.2; ranh giới Phần I–IV chỉ để hiển thị qua `ExamPart.displaySegments`. Chùm được chọn một lần vì chỉ có một nhóm. Đồng hồ chung nên không mất chức năng (suy luận). Luật của phân đoạn ở mục 5.2; việc đợt 3 phải làm ở mục 7. Phương án A xem mục 7 của plan |
| H41 | Bài theo phần của Tiếng Anh V-SAT | Mẫu `PART` riêng có `subject_code = ANH`: `P2` (1 chùm × 6 câu chọn một), `P3` (5 ghép hợp), `P4` (Cloze 5 vị trí). `doCreate` tìm mẫu `PART` riêng trước, không có mới cắt từ mẫu môn. Môn đã có mẫu `PART` riêng thì **không** cắt từ mẫu môn nữa: `P1` của Tiếng Anh bị từ chối bằng `EXAM_PART_NOT_FOUND`. Nếu không chặn, `selectSubjectPart` sẽ lấy `P1` là cả bài 25 câu, vì mẫu FULL chỉ có một `ExamPart`. Không mở `P1` vì ghi chú không định nghĩa (Q-43) |
| H42 | Khóa dòng | Cột `exam_template.fixed_block_counts`, khác cột `fixed_counts` đã có (H27, chỉ khóa tổng phần và mục). Khi bật, dạng, số câu, số chùm, cỡ chùm của từng nhóm phải giống mẫu. Bật cho bốn môn H-SCA FULL và tám mẫu V-SAT FULL, gồm Tiếng Anh (V-SAT là suy luận từ VS §3: chỉ bài luyện mới đổi số lượng). **Chỉ xét với bài phạm vi `SUBJECT`.** Bài `PART` và bài ôn nạp từ cùng mẫu không bị khóa, vì VS §1 và §3 cho bài luyện đổi số lượng (2 chùm lên 3 chùm). V-ACT không bật, giữ H27. **Mẫu của hai kỳ thi để `fixed_counts = false`:** khóa H27 so cả mẫu với bài theo `exam.template_code` và không xét phạm vi (`ExamCheckTemplateFacts.locked`, `ExamCheckRules.lockedCountIssues`), nên nếu bật thì bài `PART` cắt từ mẫu môn bị báo `STRUCTURE_COUNT_LOCKED` cho các phần không có trong bài. Khóa dòng đã bao hàm khóa tổng, nên không cần bật cả hai. Khi so dòng, nhóm đã lược không tính là một dòng |
| H43 | Phần viết H-SCA Ngữ văn | Hai phần viết lưu `omitted = true` trong mẫu để tham chiếu. Cột `exam_template.omitted_locked`: bật lại nhóm đã lược thì báo lỗi `OMITTED_BLOCK_LOCKED` (cùng tinh thần D11). Với mẫu có `omitted_locked`, tạo bài `PART` cho phần chỉ gồm nhóm đã lược bị từ chối bằng `EXAM_PART_NOT_FOUND`. Mẫu không có cờ (SP2E, SPT) giữ hành vi hiện tại |
| H44 | Đối chiếu theo lệnh | **Đã chốt (D19, Q-38).** Chỉ tiêu cấp bài và chỉ tiêu chủ đề được có `basis = PROMPT`, chỉ ở mức `REFERENCE`. Vì ý chưa có nhãn riêng, số lệnh của một câu được tính theo **nhãn cấp câu**; câu thiếu nhãn vào dòng "Chưa phân loại". Chỉ tiêu bắt buộc đặt theo câu (H7, D3). Nhãn riêng từng ý và chỉ tiêu bắt buộc theo lệnh không làm ở 2B; chỗ lệch với VS §5.1 điểm 4 và 6 báo BA (mục 9) |
| H45 | Biến thể mẫu | Dùng cơ chế `structure-version-picker.tsx` đã có: "Sát đề minh họa" của bốn môn H-SCA (ma trận từng dòng HS §4.4), "Mô phỏng đề mẫu 50 phút" của V-SAT Hóa học |
| H46 | Mặc định đối chiếu | Mọi bảng lớp, độ khó, mạch của hai kỳ thi nạp ở mức `REFERENCE`. V-SAT theo VS §5.1; H-SCA là suy luận từ HS §5.3 |
| H47 | Thiếu điểm | Thêm chiều lọc `POINTS`: nguồn đủ câu nhưng sai điểm mỗi câu thì báo `POINTS_SHORTAGE` thay vì thiếu nguồn chung |
| H48 | Kiểm tra tệp đính kèm | `ExamCheckUnitEvidence` nạp giá trị thật cho `attachmentsReadable`. Hiện hai chỗ gọi `addFacts` (dòng 75 và 114) luôn truyền `null`, nên `ATTACHMENT_MISSING` không bao giờ phát sinh. **Đọc bảng `question_asset` là chưa đủ:** khóa ngoại `question_asset_ref.asset_id` và `QuestionAssetCleanupJob` (chỉ xóa tệp không còn tham chiếu) bảo đảm dòng dữ liệu luôn còn. Tệp chỉ thiếu được ở kho lưu trữ, nên giá trị thật là `ObjectStorage.exists(object_key)` của từng tệp trong `attachmentIds` ở câu gốc. Câu không có tệp đính kèm giữ `null`. Với chùm, `addFacts` hiện chỉ sinh bằng chứng cho từng câu con; tệp nằm ở câu gốc nên H48 thêm **một** bằng chứng mang mã câu gốc, không lặp lại ở từng câu con. Lỗi kết nối kho lưu trữ làm lần kiểm tra thất bại, không suy ra thiếu tệp. Áp dụng cho **mọi kỳ thi**, vì mã `ATTACHMENT_MISSING` vốn đã có trong 2B. Giới hạn ở cuối mục này |
| H49 | Phần bị loại khỏi thang (V-SAT Ngữ văn) | **Không thêm cột `excluded_parts`.** Mẫu V-SAT Ngữ văn lưu phần Viết như H-SCA Ngữ văn (H43): phần thứ tư "Viết" có `questionCount = 0`, không khai `maxPoints`, gồm một nhóm `ESSAY` `omitted = true`, `questionCount = 1`, `requiredPointsPerQuestion = 30`; mẫu bật `omitted_locked` (suy luận từ VS §7: V1 chưa chấm bài viết). Mẫu `SP2E_VAN_OLD` đã lưu đúng kiểu này (nhóm viết 1 câu, 4 điểm, `omitted`). Đã lần theo mã: nhóm đã lược không góp vào tổng câu, tổng điểm, số lệnh, đầu vào bộ giải, mẫu số đối chiếu và đề sinh thử (`ExamCheckRules.totalsForTuple` và `fixedCount`, `ExamCheckUnitSummary`, `SolverInputBuilder`, `ExamCheckDistribution`, `ExamPreviewService`, `PreviewCompliance`). Điều kiện: phần chỉ có nhóm đã lược phải để số câu 0 và không khai `maxPoints`, nếu không sẽ báo `PART_TOTAL_MISMATCH` hoặc `PART_POINTS_MISMATCH`. Luật báo thang ở mục 6, mã `SCALE_INCLUDES_EXCLUDED_PART` |

**Ảnh hưởng của H48 tới bảy kỳ thi đã có.** Dữ liệu dev không đổi kết quả: mọi câu mẫu trong `R__seed_questionbank_dev_data.sql` và `R__seed_test_exam_data.sql` có `attachmentIds` rỗng. Dữ liệu thật chỉ đổi kết quả với câu có tệp đính kèm mà object đã mất khỏi kho lưu trữ; khi đó bài chuyển từ đạt sang lỗi, đúng ý của mã này. Chi phí thêm: một lần gọi `exists` cho mỗi tệp đính kèm khác nhau của các câu trong đề chứng minh, mỗi lần kiểm tra.

**Giới hạn của H48.** Chỉ xét các câu nằm trong đề chứng minh của lần kiểm tra, không quét cả nguồn; tệp hỏng ở câu không được chọn thì chưa bị phát hiện. Chỉ biết tệp còn hay mất; không giải mã âm thanh, nên "không phát được" (HS §6.2) chưa kiểm được. Ảnh trong nội dung (`IMAGE`) không thuộc H48. Ba điểm này ghi ở mục 9.

## 3. Yêu cầu mới

| R | Yêu cầu | Nguồn | Task |
|---|---|---|---|
| R-127 | Kỳ thi `HSCA`, `VSAT` có trong danh mục, hồ sơ, bộ lọc và nhãn hai ngôn ngữ | HS §1, VS §1 | BE-16, FE-6 |
| R-128 | Mẫu H-SCA bốn môn 40 câu, Ngữ văn, Tiếng Anh đúng mục 4 | HS §4–6 | BE-18 |
| R-129 | Mẫu V-SAT tám môn đúng mục 5 (Tiếng Anh thuộc BE-21) | VS §3–5, §7 | BE-18, BE-21 |
| R-130 | Lọc nguồn theo Có audio / Không có audio, số đếm khớp bộ giải; thiếu thì báo `AUDIO_SHORTAGE` tại dòng, kèm số cần và số có | HS §6.2, §6.3, §9 | BE-16, BE-17, FE-7 |
| R-131 | Câu chọn một lọc được đúng bốn phương án | HS §4.1, §5.2, §6.5 | BE-17 |
| R-132 | Chùm ràng buộc được dạng câu con theo vị trí | VS §4.2 | BE-17 |
| R-133 | Tiếng Anh V-SAT FULL: 25 câu, 67 lệnh, chọn bài đọc 8–15 một lần, hiển thị Phần I–IV | VS §4.2 | BE-16, BE-20, BE-21, FE-8 |
| R-134 | Luyện riêng Phần II, III, IV Tiếng Anh V-SAT | VS §4.2, §9 | BE-21, FE-6 |
| R-135 | Khóa từng dòng của bài FULL theo H42 | HS §9 | BE-20, FE-7 |
| R-136 | Tệp audio thiếu báo lỗi tài nguyên tại đúng câu (giới hạn ở H48) | HS §6.2 | BE-20 |
| R-137 | Báo riêng câu sai điểm so với mẫu | VS §7 | BE-16, BE-17 |
| R-138 | Thang lệch đúng bằng điểm của phần đã lược phải nêu tên và số điểm phần đó (H49) | VS §8 | BE-18 (dữ liệu mẫu), BE-20 (luật) |
| R-139 | Đối chiếu theo lệnh, có dòng "Chưa phân loại" | VS §5.1, §5.2 | BE-16, BE-20, FE-8 |
| R-140 | Ví dụ chấm ở bước 2: H-SCA đúng, một phần, sai, bỏ trống; V-SAT Đúng/Sai 3/4 được 3 điểm, ghép hợp 3/4 được 4,5 điểm | HS §2 b4, VS §2 b4, §7 | FE-7 |
| R-141 | Bước 1 chọn phạm vi; bắt buộc nhập thời lượng khi mẫu để trống và với mọi bài theo phần của hai kỳ thi | HS §2, §6.3, VS §2, §4.1, §9 | FE-6 |
| R-142 | Dòng lấy câu hiện nhãn audio, tổng câu, tỷ lệ; tỷ lệ không ra chùm nguyên báo tại dòng | HS §6.3 | FE-7 |
| R-143 | Sinh thử phát được audio (suy luận từ HS §9) | HS §9 | BE-20, FE-8 |
| R-144 | Phần viết H-SCA Ngữ văn và V-SAT Ngữ văn lưu tham chiếu, không bật lại được, không tạo được bài `PART` riêng | HS §1, §5.1, VS §7 | BE-18, BE-20, FE-6 |
| R-145 | Dữ liệu câu mẫu đủ để mọi mẫu mới sinh thử được, có câu chứa audio | Quy ước 2B (`SampleQuestionCoverageIT`) | BE-19, BE-18 |
| R-146 | Web admin đọc, giữ và gửi lại nguyên vẹn các trường mới của cấu trúc khi lưu bước 2. `api-mapping.ts` và `create-parts.ts` hiện chép từng trường, trường lạ bị bỏ; không sửa thì lần lưu đầu tiên, hoặc lần tạo bài có sửa bảng tổng quan, làm mất điều kiện audio và phân đoạn Phần I–IV | Mã hiện có (`mapBlock`, `mapSection`, `mapPart`, `templateSection`, `blockFromTemplate`) | FE-6 |

## 4. Mẫu H-SCA

### 4.1. Toán, Vật lí, Hóa học, Sinh học (`SUBJECT`, 90 phút chung, 10 điểm, `fixed_block_counts`)

| Phần | Nhóm | Dải | Số câu | Điểm mỗi câu | Tổng điểm |
|---|---|---|---|---|---|
| 1. Trắc nghiệm | Chọn một, bốn phương án | 1–20 | 20 | 0,25 | 5 |
| 1. Trắc nghiệm | Chọn nhiều | 21–25 | 5 | 0,25 | 1,25 |
| 2. Dữ liệu tổng hợp | Chùm, tổng 5 câu con (Q-40) | 26–30 | 5 | 0,25 | 1,25 |
| 3. Điền đáp án | Trả lời ngắn | 31–40 | 10 | 0,25 | 2,5 |
| **Tổng** | **4 dòng** | **1–40** | **40** | | **10** |

Mức đối chiếu toàn môn:

| Môn | Lớp 10 / 11 / 12 | NB / TH / VD |
|---|---|---|
| Toán | 0 / 14 / 26 | 9 / 15 / 16 |
| Vật lí | 0 / 12 / 28 | 11 / 14 / 15 |
| Hóa học | 10 / 18 / 12 | 12 / 15 / 13 |
| Sinh học | 5 / 5 / 30 | 10 / 16 / 14 |

Biến thể "Sát đề minh họa" (H45), ma trận từng dòng theo NB / TH / VD:

| Môn | Chọn một · 20 | Chọn nhiều · 5 | Chùm · 5 | Điền · 10 |
|---|---|---|---|---|
| Toán | 9 / 10 / 1 | 0 / 1 / 4 | 0 / 2 / 3 | 0 / 2 / 8 |
| Vật lí | 11 / 9 / 0 | 0 / 3 / 2 | 0 / 2 / 3 | 0 / 0 / 10 |
| Hóa học | 12 / 8 / 0 | 0 / 4 / 1 | 0 / 2 / 3 | 0 / 1 / 9 |
| Sinh học | 10 / 9 / 1 | 0 / 4 / 1 | 0 / 2 / 3 | 0 / 1 / 9 |

### 4.2. Ngữ văn (`SUBJECT`, thời lượng nhập khi tạo, chỉnh được số câu)

| Thứ tự | Nhóm | Nhãn | Chùm × câu con | Dải | NB / TH / VD |
|---|---|---|---|---|---|
| 1 | Văn bản thông tin | `VAN_THONG_TIN` | 1 × 5 | 1–5 | 2 / 3 / 0 |
| 2 | Văn bản nghị luận | `VAN_NGHI_LUAN` | 1 × 7 | 6–12 | 2 / 3 / 2 |
| 3 | Thơ | `VAN_THO` | 1 × 8 | 13–20 | 2 / 4 / 2 |
| **Tổng** | | | **20 câu** | | **6 / 10 / 4** |

Câu con chọn một, bốn phương án. Lớp tắt. Hai phần Viết đoạn, Viết bài `omitted` (H43). Điểm theo Q-39.

### 4.3. Tiếng Anh (`SUBJECT` là Nghe và Đọc, `PART` là từng phần; thời lượng nhập khi tạo, chỉnh được)

| Phần | Dòng | Audio | Cách lấy | Tổng câu | Tỷ lệ trong phần |
|---|---|---|---|---|---|
| Nghe | 1 | `REQUIRED` | 8 câu đơn | 8 | 22,9% |
| Nghe | 2 | `REQUIRED` | 3 chùm × 4 | 12 | 34,3% |
| Nghe | 3 | `REQUIRED` | 3 chùm × 5 | 15 | 42,9% |
| Đọc | 1 | `FORBIDDEN` | 4 chùm × 10, chọn một bốn phương án | 40 | 100% |

Mọi câu của phần Nghe là câu chọn một (HS §6.1); ghi chú không nêu số phương án của phần Nghe nên không ràng buộc. Bài `SUBJECT` có 75 câu, bài `PART` Nghe 35 câu, bài `PART` Đọc 40 câu. Lớp và độ khó tắt (HS §6.3).

## 5. Mẫu V-SAT

### 5.1. Sáu môn có Phần IV và Ngữ văn I–III (`SUBJECT`, `fixed_block_counts`) (suy luận, D17 chốt tạm Q-36)

| Phần | Dòng | Dải | Số câu | Lệnh | Điểm |
|---|---|---|---|---|---|
| I | Đúng/Sai bốn ý, bảng `[1,2,3,6]` | 1–9 | 9 | 36 | 54 |
| II | Chọn một, 2 chùm × 3 | 10–15 | 6 | 6 | 36 |
| III | Ghép hợp bốn mục, 1,5 điểm mỗi cặp | 16–20 | 5 | 20 | 30 |
| IV | Trả lời ngắn, 6 điểm | 21–25 | 5 | 5 | 30 |
| **Tổng** | | **1–25** | **25** | **67** | **150** |

Áp dụng cho Toán, Vật lí, Hóa học, Sinh học, Lịch sử, Địa lí. Địa lí Phần II có hai vị trí theo nhãn: bảng số liệu, biểu đồ (Q-45).

**Ngữ văn I–III:** chỉ Phần I, II, III như trên, 20 câu, 62 lệnh, 120 điểm. Phần II gồm một chùm `VAN_THO`, một chùm `VAN_TRUYEN`, mỗi chùm 3 câu. Phần Viết lưu thành phần thứ tư đã lược, 30 điểm, không tính vào 20 câu và 120 điểm (H49); mẫu bật `omitted_locked`. Bài theo phần chỉ có Phần I, II, III.

**Thời lượng:** Toán 90; Vật lí, Sinh học, Lịch sử, Địa lí, Tiếng Anh 60; Hóa học 60, biến thể 50 (Q-34); Ngữ văn để trống, nhập khi tạo (Q-35).

### 5.2. Tiếng Anh FULL (một phần, 60 phút)

| Nhóm | Dải | Nội dung | Số câu | Lệnh | Điểm |
|---|---|---|---|---|---|
| Đúng/Sai độc lập | 1–7 | 7 câu, bốn nhận định | 7 | 28 | 42 |
| **Bài đọc dùng chung – câu 8–15** | 8–15 | 1 chùm 8 câu con: vị trí 1–2 Đúng/Sai bốn ý, vị trí 3–8 chọn một | 8 | 14 | 12 + 36 |
| Ghép hợp | 16–20 | 5 câu, 4 mục | 5 | 20 | 30 |
| Cloze | 21–25 | 1 đoạn, 5 vị trí, 6 điểm mỗi vị trí | 5 | 5 | 30 |
| **Tổng** | **1–25** | | **25** | **67** | **150** |

`displaySegments`: Phần I câu 1–9 (36 lệnh, 54 điểm), Phần II câu 10–15 (6, 36), Phần III câu 16–20 (20, 30), Phần IV câu 21–25 (5, 30).

Nhóm "Bài đọc dùng chung – câu 8–15" khai báo: `questionType = QUESTION_SET`, `setCount = 1`, `setSize = 8`, `childAnswerType` trống, `requiredPointsPerQuestion = 6`, hai `childConditions`: vị trí 1–2 `answerType = TRUE_FALSE` với `itemShape.statements = 4`, vị trí 3–8 `answerType = SINGLE_CHOICE`. Mẫu bật `fixed_block_counts`, nên phân đoạn không lệch khỏi cấu trúc.

**Luật của `displaySegments` (H40):**

1. `fromPosition`, `toPosition` là số thứ tự câu trong phần, tính từ 1. Các phân đoạn phải nối nhau phủ kín từ câu 1 đến `questionCount` của phần, không hở, không chồng. Tổng `maxPoints` của các phân đoạn bằng `maxPoints` của phần. Sai thì 422 `EXAM_STRUCTURE_INVALID` khi lưu.
2. Ranh giới phân đoạn **được nằm giữa một chùm** (câu 9 và 10 cùng một bài đọc). Phân đoạn không tham gia bộ giải, không đổi thứ tự câu, không đổi đánh số.
3. `commandCount` và `maxPoints` của từng phân đoạn là số khai báo. Lúc lưu chỉ kiểm được tổng. Lúc sinh thử, hệ thống cộng lệnh và điểm thật của các câu trong từng dải và so với số khai báo; lệch thì báo lỗi tại phần.
4. Bài `PART` và bài ôn không mang `displaySegments`.
5. Tóm tắt cấu trúc tính số lệnh của nhóm trộn dạng từ `childConditions`: 2 × 4 + 6 × 1 = 14. `ExamCheckUnitSummary.block` hiện trả số lệnh trống khi `childAnswerType` trống, nên nếu không sửa thì tổng "67 lệnh" của bài không hiện được.

**Lần theo phương án B qua mã hiện có** (cho R-133):

| Chặng | Hiện trạng | Kết luận |
|---|---|---|
| Lọc nguồn | `QuestionSourceAdapter` lọc `childConditions` theo vị trí bằng SQL, hiện chỉ theo `nodeIds` | BE-17 thêm dạng và hình dạng theo vị trí. Hai câu Đúng/Sai đứng trước sáu câu chọn một là điều kiện của nguồn, không phải việc sắp xếp |
| Bộ giải | `Prep` và `SolverInputBuilder.eligible` chỉ so `childAnswerType` khi có giá trị; cỡ chùm so với `setSize` | Không sửa lõi bộ giải. Một nhóm một chùm nên bài đọc được chọn đúng một lần |
| Kiểm tra | `filterIssues` báo `CHILD_CONDITION_SHORTAGE` khi có chùm đúng cỡ nhưng sai dạng; không có chùm 8 câu thì báo `SET_SHORTAGE`. Cả hai gắn vào nhóm | Đúng VS §8: lỗi nằm tại dòng "Bài đọc dùng chung – câu 8–15" |
| Sinh thử | Chùm là một `PreviewItem` có `passage` và 8 câu con đánh số liền nhau; `PaperOrdering` không đảo câu con | Bài đọc xuất hiện một lần. `PreviewPart` chưa có `displaySegments`: BE-20 thêm |
| Hiển thị | `exam-preview-paper.tsx` vẽ theo phần và nhóm | FE-8 chèn tiêu đề Phần I–IV theo dải câu. Chùm bị cắt ở ranh giới thì bài đọc hiện ở cả hai phân đoạn |
| Phiên bản công bố | `ExamVersionSnapshot` lưu nguyên tài liệu cấu trúc | `displaySegments` đi theo phiên bản, không cần sửa |

### 5.3. Phân bổ đối chiếu theo lệnh

| Môn | Lớp 10 / 11 / 12 | NB / TH / VD | Mẫu số |
|---|---|---|---|
| Toán | 0 / 17 / 50 | 13 / 35 / 19 | 67 |
| Vật lí | 0 / 25 / 42 | 19 / 32 / 16 | 67 |
| Hóa học | 0 / 23 / 44 | 14 / 32 / 21 | 67 |
| Sinh học | 0 / 13 / 54 | 15 / 28 / 24 | 67 |
| Lịch sử | 6 / 2 / 59 | 15 / 40 / 12 | 67 |
| Địa lí | không có | 20 / 42 / 5 | 67 |
| Tiếng Anh | không có | 21 / 39 / 7 | 67 |
| Ngữ văn I–III | không có | 12 / 33 / 17 | 62 |

Mạch đối chiếu, tính theo lệnh (VS §5.2):

| Môn | Mạch và số lệnh | Tổng |
|---|---|---|
| Ngữ văn I–III | Tùy bút, nhật kí 16; văn bản thông tin 16; truyện, kịch 15; văn bản nghị luận 12; thơ 3 | 62 |
| Địa lí | Các ngành kinh tế 26; các vùng kinh tế 17; dân cư 8; tự nhiên 10; kỹ năng bảng số liệu, biểu đồ, Atlat 6 | 67 |
| Tiếng Anh | Đọc hiểu 42; giao tiếp qua văn bản 4; ngữ pháp và từ vựng 21 | 67 |

Mỗi mạch là một `TopicQuota` cấp mục: `enforcement = REFERENCE`, `basis = PROMPT` (H44), `nodeIds` rỗng ở trạng thái Chờ mapping (H31, D12) (suy luận, theo cách V-ACT đã làm ở phần bổ sung UNI-2671 mục 5.2). Nhiều nhất năm dòng một môn, dưới giới hạn 8 của H20. Chưa gắn mã thì màn đối chiếu ghi "Chưa có dữ liệu đối chiếu" như R-124.

## 6. Mô hình dữ liệu và API

**Cột mới của `exam.exam_template`** (một migration): `fixed_block_counts boolean NOT NULL DEFAULT false`, `omitted_locked boolean NOT NULL DEFAULT false`. Không có cột `excluded_parts` (H49). Đổi `chk_exam_template_scope_fields` theo H41. **Không có bảng mới.**

**Tài liệu cấu trúc (jsonb), mọi trường đều tùy chọn và tương thích ngược:**

| Bản ghi | Trường mới |
|---|---|
| `ExamSection` | `audioRequirement` |
| `ExamPart` | `displaySegments: [{ code, name, fromPosition, toPosition, commandCount, maxPoints }]` |
| `ChildCondition` | `answerType`, `itemShape`; `nodeIds` được rỗng khi có `answerType` |
| `ItemShape` | `options` |
| `TopicQuota`, `ExamQuota` | Không thêm trường; `basis` nhận `PROMPT` khi `REFERENCE` (H44) |

**Giữ trường khi chép.** Thêm trường vào record mà giữ hàm dựng cũ thì mã vẫn biên dịch nhưng trường mới **bị bỏ lặng lẽ** ở mọi chỗ dựng lại `ExamPart`, `ExamSection`, `ChildCondition`, `ItemShape`. Trên nhánh FIX-QA có 16 tệp như vậy (`git grep -c -E "new (ExamSection|ExamPart|ChildCondition|ItemShape)\("`), trong đó `ExamOverview` (chạy ở mọi lần tạo bài có bảng tổng quan), `ExamService`, `ExamCheckDraftInputs`, `PreviewSectionTarget`, `SolverInputBuilder`. BE-16 phải sửa hết các chỗ này; sót một chỗ thì bộ giải không thấy điều kiện audio.

**Cổng nguồn câu:** `QuestionSourcePort.Filter` thêm `audioRequirement`; `QuestionSourcePort.ItemShape` thêm `options`; `QuestionSourcePort.ChildCondition` thêm `answerType`, `itemShape`. `FilterDimension` (trong `SolverInputBuilder`) và `FilterKind` (trong `ExamCheckRules`) thêm `AUDIO`, `POINTS`; hai enum phải trùng tên vì `DefaultExamChecker` đổi qua lại bằng `valueOf`.

**Cổng tệp đính kèm (mới):** `questionbank/spi/QuestionAttachmentPort`, tách khỏi `QuestionSourcePort` để lane kiểm tra không sửa chung tệp với lane nguồn câu. Nhận danh sách phiên bản câu gốc, trả cho mỗi phiên bản danh sách `{ id, name, mime, readable }`; `readable` lấy từ `ObjectStorage.exists` (H48). Dùng cho cả kiểm tra và sinh thử.

**API:** `ExamTemplateResponse` thêm `fixedBlockCounts`, `omittedLocked`. API đếm câu khả dụng nhận `audioRequirement`. Sinh thử thêm `attachments: [{ id, name, mime }]` ở **`PreviewItem`** (chùm: tệp của câu gốc, nơi đặt audio của mọi chùm Nghe) và ở `PreviewQuestion` (câu đơn); `PreviewPart` thêm `displaySegments`. Web phát audio qua API đã có `GET /api/v1/admin/question-assets/{id}` (quyền `EXAM_VIEW` đã đủ), không thêm API tải tệp.

**Mã kiểm tra:**

| Mã | Mức | Khi nào |
|---|---|---|
| `AUDIO_SHORTAGE` | Lỗi | Bỏ điều kiện audio thì nguồn đủ, giữ thì thiếu; kèm `required`, `available` |
| `POINTS_SHORTAGE` | Lỗi | Bỏ điều kiện điểm mỗi câu thì nguồn đủ, giữ thì thiếu; kèm `required`, `available` |
| `BLOCK_COUNT_LOCKED` | Lỗi | Dòng của mẫu khóa bị đổi dạng hoặc số lượng; chỉ với bài `SUBJECT` (H42) |
| `OMITTED_BLOCK_LOCKED` | Lỗi | Bật lại phần viết đã lược |
| `SCALE_INCLUDES_EXCLUDED_PART` | Lỗi | Bài nạp từ mẫu có `omitted_locked`, và thang trừ tổng điểm của bài đúng bằng điểm các nhóm đã lược của một phần (số câu × điểm mỗi câu, đọc từ cấu trúc của chính bài); chi tiết có tên phần và số điểm. **Thay cho** `POINTS_TOTAL_MISMATCH` trong trường hợp này, không báo cả hai. Bài của mẫu không có cờ (SP2E, SPT) vẫn báo `POINTS_TOTAL_MISMATCH` như cũ |
| `ATTACHMENT_MISSING` (đã có) | Như hiện tại | Nay phát sinh thật theo H48 |

Câu chữ máy chủ của năm mã mới nằm trong `messages*.properties` như các mã đã có.

**Dữ liệu audio cho dev.** Tệp SQL chỉ chèn được dòng `question_asset`, không đưa được tệp vào kho lưu trữ. Vì vậy cần một bộ nạp (`ApplicationRunner`, cùng kiểu `DevClassSeeder`) đưa tệp MP3 mẫu vào kho với `object_key` cố định, chạy lại không tạo trùng. Bộ nạp chạy ở hồ sơ `dev` **và `test`**: bộ test tích hợp dùng kho trong bộ nhớ (`thi360.s3.mode: memory` trong `application-test.yml`, bean `InMemoryObjectStorage` ở `TestSecurity`), kho này trống khi khởi động, trong khi tệp `R__` của `db/devdata` vẫn chèn dòng `question_asset` ở hồ sơ `test`. Thiếu bước này thì sau H48 mọi câu audio mẫu đều báo `ATTACHMENT_MISSING`, ở dev lẫn trong test. Dòng `question_asset` trong SQL phải khớp tệp: `size`, `sha256`, `mime`.

**Triển khai:** hồ sơ `exam_kind_profile` chỉ seed ở dev. Môi trường thật phải tạo mục danh mục và hồ sơ hai kỳ thi trước khi dùng mẫu.

## 7. Ngoài phạm vi 2B và ảnh hưởng đợt 3

| Việc | Đợt | Ghi chú cho đợt 3 |
|---|---|---|
| Làm thử, nộp thử, chấm thử, nghe một lần | Đợt 3 (H17, FA-4) | Đọc `audioRequirement`, `displaySegments` từ phiên bản đã công bố |
| Hiển thị và kết quả theo Phần I–IV của Tiếng Anh V-SAT | Đợt 3 | Xem "Việc đợt 3 phải làm với `displaySegments`" bên dưới |
| Kiểm tra tệp audio lúc ghép đề thật | Đợt 3 | H48 chỉ xét đề chứng minh. Lúc mở lượt (HS §8: ghép đủ đề rồi mới chạy đồng hồ) phải kiểm tệp của các câu được chọn, thiếu thì không mở lượt và không trừ lượt |
| Luồng học viên: màn chuẩn bị, chính sách nghe, khôi phục lượt, nhập ion và chuỗi ký hiệu trên điện thoại, hiện đơn vị và yêu cầu làm tròn tại câu, phân tích mạnh yếu theo MAPID, AI giải thích | Đợt 3 | HS §7, §8; VS §6, §7, §9 |
| Chấm chọn nhiều H-SCA | Đợt 3 | Có thể cần giá trị mới của `MultipleChoiceRule` theo Q-32 |
| Trạng thái "Đúng một phần" | Đợt 3 | HS §7 |
| Bộ nhiều môn, giá, lượt, voucher, trang mua | Đợt 3 (TM C17, mục 16, Q16) | `bundle_item` đã dự kiến cho kỳ thi có bài theo môn |
| Band điểm | Không làm (L5) | Chờ BA |
| Nhãn lớp, độ khó riêng từng ý | Ngoài 2B | Đổi mô hình ngân hàng câu hỏi (H7) |
| Tệp đính kèm riêng cho câu con | Ngoài 2B | Đổi trình soạn câu hỏi |
| Chấm bài viết, phần Nói | Ngoài V1 | HS §6.1, VS §7 |
| Ghép nhiều phần trong một bài (HS §2 b1 "các phần được chọn", VS §1 "chọn các phần") | Ngoài 2B (Q-37) | `exam.part_code` chỉ giữ một giá trị; báo BA |
| Bảng đối chiếu mã đối tác | Ngoài 2B (D12) | Gắn MAPID tay vào dòng Chờ mapping |

**Việc đợt 3 phải làm với `displaySegments`** (hệ quả của phương án B):

1. Màn làm bài chèn tiêu đề phân đoạn trước câu `fromPosition`. Đánh số câu liền từ 1 đến 25, không đánh lại theo phân đoạn.
2. Chùm bị cắt ở ranh giới (câu 8–9 thuộc Phần I, 10–15 thuộc Phần II): bài đọc phải xem được ở cả hai phân đoạn. Không được ẩn bài đọc khi học viên sang Phần II.
3. Không tạo đồng hồ, lượt hay điểm sàn theo phân đoạn; bài vẫn một đồng hồ chung.
4. Kết quả theo phần, nếu cần, cộng điểm các câu trong dải `fromPosition`–`toPosition`, mẫu số là `maxPoints` của phân đoạn. Không lấy theo `ExamPart`, vì cả bài chỉ có một.
5. Module `attempt` đọc `displaySegments` từ bản chụp phiên bản đã công bố, không đọc mẫu hiện hành.

## 8. Câu hỏi

Không còn câu nào chờ người dùng: Q-38 đã được chốt ở D19. Q-32 đến Q-36 đã được Advisor chốt tạm ở D17, chờ BA xác nhận. Q-37 và Q-39 đến Q-45 là mặc định của agent, không chặn việc.

| Q | Câu hỏi | Quyết định hoặc mặc định | Người chốt |
|---|---|---|---|
| Q-32 | H-SCA chọn nhiều: chọn kèm phương án sai, hoặc chọn đúng nhiều nhưng chưa đủ, chấm thế nào? | **D17 (tạm):** có phương án sai được 0; chưa đủ và không sai được nửa điểm. 2B chỉ khai báo `PARTIAL`; đợt 3 chấm | Advisor, chờ BA |
| Q-33 | H-SCA lớp 12: theo định hướng 70–80% hay theo số đề mẫu? | **D17 (tạm):** nạp số đề mẫu ở mức đối chiếu, ghi định hướng vào `expert_note`; admin tự nhập mục tiêu và bật áp dụng. Lưu ý Toán 26/40 và Hóa 12/40 đều dưới 28 | Advisor, chờ BA |
| Q-34 | V-SAT Hóa học 50 hay 60 phút? | **D17 (tạm):** FULL 60 phút, biến thể mô phỏng 50 phút (đúng đề xuất của BA ở VS §4.1) | Advisor, chờ BA |
| Q-35 | V-SAT Ngữ văn I–III bao nhiêu phút? | **D17 (tạm):** không đặt số, admin bắt buộc nhập | Advisor, chờ BA |
| Q-36 | V-SAT mỗi phần gồm gì? Ghi chú không có bảng | **D17 (tạm):** I 9 Đúng/Sai, II 6 chọn một, III 5 ghép hợp, IV 5 trả lời ngắn. Đối chiếu lại với ghi chú: 25 câu, 36 + 6 + 20 + 5 = 67 lệnh, 54 + 36 + 30 + 30 = 150 điểm | Advisor, chờ BA |
| Q-38 | V-SAT "Áp dụng khi sinh đề" theo lệnh và đếm theo nhãn từng ý cần đổi ngân hàng câu hỏi. Có chấp nhận chưa làm ở 2B? | **D19:** theo đề xuất. Ở 2B chỉ đối chiếu theo lệnh, tính theo nhãn cấp câu; chỉ tiêu bắt buộc đặt theo câu (H7, D3). Không làm nhãn riêng từng ý và chỉ tiêu bắt buộc theo lệnh | Chủ dự án |
| Q-37 | Một bài có được ghép nhiều phần, ví dụ Phần I + III? | Một phần mỗi bài. Ví dụ cụ thể trong hai ghi chú đều là một phần hoặc cả môn, dù lời văn dùng số nhiều (HS §2 b1, VS §1). Báo BA | Agent |
| Q-39 | H-SCA dùng 0,25 điểm/câu chính thức? Ngữ văn, Tiếng Anh thang nào? | Bốn môn 0,25, tổng 10. Ngữ văn, Tiếng Anh 1 điểm/câu, thang bằng số câu | Agent |
| Q-40 | H-SCA phần 2: cỡ chùm và dạng câu con? | Chùm linh hoạt tổng 5 câu con, không giới hạn dạng | Agent |
| Q-41 | H-SCA Nghe dòng 1 có nhận chùm một câu con? | Không, chỉ câu đơn; ngân hàng yêu cầu chùm tối thiểu 2 câu con | Agent |
| Q-42 | H-SCA Ngữ văn: admin có được bật lại hai phần viết đã lưu tham chiếu? | Không, theo HS §5.1 và tiền lệ D11 (H43). Phần Viết của V-SAT Ngữ văn cũng vậy (H49, VS §7) | Agent |
| Q-43 | V-SAT Tiếng Anh: luyện riêng Phần I, dùng lại nhánh sáu câu? | Không mở Phần I. Phần II chỉ lấy chùm sáu câu riêng, thiếu thì báo | Agent |
| Q-44 | Band điểm ở hai ghi chú trái L5 | Không làm | Agent |
| Q-45 | Mã nhãn Địa lí cho bảng số liệu, biểu đồ | `DIA_BANG_SO_LIEU`, `DIA_BIEU_DO`, tạm thời đến khi BA xác nhận | Agent |

## 9. Việc phải báo BA

1. **Năm quyết định tạm của Advisor cần BA xác nhận (D17):** Q-32, Q-33, Q-34, Q-35, Q-36.
2. HS §4.2 chỉ nêu Hóa học dưới định hướng lớp 12; **Toán 26/40 (65%) cũng dưới mức 28 câu**.
3. HS §4.4 dẫn "mục 4.3" nhưng ghi chú không có mục này.
4. HS §6.4 cho phép Question Set một câu con; ngân hàng yêu cầu chùm tối thiểu 2 câu con (Q-41).
5. HS §6.2 nói audio ở nội dung câu con; trình soạn hiện chỉ gắn tệp ở câu gốc.
6. HS §3, VS §5.1 ấn định số câu theo MAPID; hệ thống giữ "ít nhất" (K6, D13).
7. Hai ghi chú nhắc band điểm; đặc tả lượt thi L5 không có band (Q-44).
8. HS §6.4 cột tỷ lệ cộng ra 100,1% do làm tròn, bảng ghi 100%.
9. VS mục đánh số nhảy từ 3 sang 4.1; không có bảng cấu trúc theo phần (Q-36).
10. HS §2 b1 và VS §1 viết "các phần được chọn"; 2B chỉ cho một phần mỗi bài (Q-37).
11. HS §6.2 "file thiếu, hỏng hoặc không phát được": 2B chỉ phát hiện tệp mất khỏi kho lưu trữ, ở các câu trong đề chứng minh của lần kiểm tra. Không giải mã âm thanh; ảnh trong nội dung chưa được kiểm (H48).
12. VS §4.2 nói câu 8–15 "liên kết qua hai phần"; 2B làm thành một phần có bốn phân đoạn hiển thị (H40, D17). Học viên vẫn thấy Phần I–IV và bài đọc chỉ được chọn một lần.
13. **Lệch với VS §5.1 điểm 4 và 6 (D19, Q-38):** điểm 4 cho bật "Áp dụng khi sinh đề" với chỉ tiêu theo lệnh, còn 2B chỉ đối chiếu theo lệnh và đặt chỉ tiêu bắt buộc theo câu; điểm 6 đếm theo nhãn từng ý, còn 2B đếm theo nhãn cấp câu (H44).
14. Các mã nhãn và mặc định tạm của agent: Q-39 (thang điểm H-SCA Ngữ văn, Tiếng Anh), Q-40 (cỡ chùm phần 2 H-SCA), Q-42 (không bật lại phần viết, gồm phần Viết của V-SAT Ngữ văn), Q-43 (không mở Phần I Tiếng Anh V-SAT), Q-45 (hai nhãn Địa lí).
