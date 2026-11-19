# Đợt 2B, kế hoạch bổ sung: cấu trúc H-SCA và V-SAT

Ngày: 2026-11-18. Trạng thái: đã duyệt ngày 2026-11-18 sau hai vòng rà độc lập. Issue: UNI-2687. Đặc tả: `docs/specs/2026-11-18-pbi04-dot-2b-bo-sung-hsca-vsat.md`. Bổ sung cho `docs/plans/2026-11-17-pbi04-dot-2b.md` (**Plan**) và kế hoạch bổ sung UNI-2671.

Hiện trạng lúc viết: `integration/dot-2b` tại `3d899976`. FIX-QA (UNI-2686, `06fdbf90`) chưa merge; QA (UNI-2666) đang chạy. **Mọi task mới chỉ mở sau khi FIX-QA merge.** FIX-QA sửa `ExamService.java`, `ExamCheckRules.java`, `SolverInputBuilder.java`, `api-mapping.ts`, `types.ts`, `create-exam-schema.ts`, `create-exam-page.tsx`, `messages/{vi,en}.json`; các tệp này đều có task mới đụng tới, nên không task nào được tách nhánh từ `3d899976`.

Quyết định đã chốt: D16 (làm trong 2B), D17 (Q-32 đến Q-36 theo mặc định, BE-21 theo phương án B), D18 (giữ 160 điểm; H48 chỉ xét tệp đính kèm), D19 (Q-38 theo đề xuất: theo lệnh chỉ đối chiếu, tính theo nhãn cấp câu). Không còn câu nào chờ người dùng.

## 1. Sổ tiến độ

**Mã issue:** BE-16 `UNI-2688`, BE-17 `UNI-2689`, BE-18 `UNI-2690`, BE-19 `UNI-2691`, BE-20 `UNI-2692`, BE-21 `UNI-2693`, FE-6 `UNI-2694`, FE-7 `UNI-2695`, FE-8 `UNI-2696`, G2c `UNI-2697`.

`scope_version` tăng từ 2 lên 3. Số task từ 23 lên 32 (sổ `progress-ledger.json` từ 25 dòng lên 34, vì có thêm hai dòng `FE-3a-addon`, `FE-4-addon`). Tổng điểm từ **127** lên **160**. Phần đã nghiệm thu giữ 120 điểm, nên tiến độ hiển thị giảm từ 94,5% xuống **75,0%**.

| Task | Tên | Nhóm | Điểm |
|---|---|---|---|
| BE-16 | Hợp đồng mở rộng, mã kỳ thi, nhãn | Nền | 3 |
| BE-17 | Nguồn câu: audio, dạng câu con, số phương án, chiều điểm | Bộ giải | 4 |
| BE-18 | Mẫu H-SCA, V-SAT, hồ sơ, phạm vi | Bài thi | 6 |
| BE-19 | Dữ liệu câu mẫu, có audio | Nền | 3 |
| BE-20 | Kiểm tra: khóa dòng, tệp audio, thang, đối chiếu theo lệnh | Kiểm tra | 3 |
| BE-21 | Tiếng Anh V-SAT: bài đọc dùng chung (phương án B) | Bài thi | 3 |
| FE-6 | Bước 1 cho H-SCA, V-SAT; giữ trường mới khi lưu | Web admin | 3 |
| FE-7 | Bước 2: audio, khóa dòng, ví dụ chấm | Web admin | 4 |
| FE-8 | Kiểm tra và sinh thử: audio, phân đoạn, thông điệp | Web admin | 2 |
| QA | Nghiệm thu đợt (7 → 9) | Nghiệm thu | +2 |
| **Cộng thêm** | | | **33** |

Cộng theo nhóm sau bổ sung: Nền 25 + 6 = 31, Bộ giải 15 + 4 = 19, Kiểm tra 18 + 3 = 21, Bài thi 21 + 9 = 30, Web admin 41 + 9 = 50, Nghiệm thu 7 + 2 = 9. Tổng 160.

Vòng rà đã thêm việc vào BE-16 (sửa mọi chỗ chép cấu trúc), BE-20 (cổng tệp đính kèm, kho lưu trữ, sinh thử) và FE-6 (R-146) nhưng **giữ nguyên điểm**, theo tiền lệ D14; D18 đã chốt giữ 160. Vòng rà thứ hai bỏ cột `excluded_parts` (spec H49) và không đổi điểm.

## 2. Hợp đồng task

Mọi task theo nghiệm thu chung ở Plan A7, quy tắc migration ở Plan B2 và B3 (không rẽ nhánh `if (kind == X)` trong bộ giải). Đường dẫn Java tính từ `backend/src/main/java/vn/unicom/thi360/`; web tính từ `apps/web-admin/src/features/exams/`.

### BE-16 · Hợp đồng mở rộng, mã kỳ thi, nhãn

**Nhóm:** Nền. **Điểm:** 3. **Cứng:** FIX-QA. **Spec:** H36, H38, H39, H42–H44, mục 6.

**Sở hữu:** `exam/domain/{ExamSection,ExamPart,ChildCondition,ItemShape,ExamTemplate}.java`, `exam/domain/ExamStructureOps.java`, `exam/domain/ExamStructureValidator.java`, `exam/api/ExamTemplateResponse.java`, `exam/application/ExamTemplateService.java` (chỉ `toResponse`), một migration mới trong `db/migration/exam` (hai cột cờ `fixed_block_counts`, `omitted_locked` và ràng buộc H41; không có cột `excluded_parts`, spec H49), `R__seed_catalog_dev_data.sql`, `R__seed_exam_dev_data.sql`, `messages{,_vi,_en}.properties` (năm mã kiểm tra mới), `packages/api-client`.

Ngoài ra, vì chạy một mình ở sóng 1, BE-16 được sửa hai nhóm sau trong tệp của lane khác:

- **Khai báo enum:** `FilterDimension` (`SolverInputBuilder.java:98`) và `FilterKind` (`ExamCheckRules.java:572`) thêm `AUDIO`, `POINTS`, cùng ánh xạ mã ở `ExamCheckRules.java:596-598`. Hai enum phải trùng tên: `DefaultExamChecker.java:308` đổi bằng `FilterKind.valueOf(dimension.name())`.
- **Mọi chỗ dựng lại `ExamPart`, `ExamSection`, `ChildCondition`, `ItemShape`** trong `backend/src/main/java` (16 tệp theo `git grep -c -E "new (ExamSection|ExamPart|ChildCondition|ItemShape)\("`, gồm `ExamOverview`, `ExamService`, `ExamStructurePresentation`, `ExamCheckDraftInputs`, `PreviewSectionTarget`, `SolverInputBuilder`): phải chuyển tiếp trường mới. Giữ hàm dựng cũ cho mã test, nhưng mã chạy thật không được dùng hàm dựng cũ để chép một bản ghi đã có.

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Đọc mẫu hiện có | `fixedBlockCounts = false`, `omittedLocked = false` |
| 2 | Tài liệu cấu trúc cũ không có trường mới | Đọc được; ghi lại không sinh thêm trường; hành vi không đổi |
| 3 | Lưu rồi đọc lại `audioRequirement`, `displaySegments`, `ChildCondition.answerType` và `itemShape`, `ItemShape.options` | Giữ nguyên |
| 4 | Một cấu trúc có đủ các trường mới đi qua: tạo bài có bảng tổng quan (`ExamOverview.apply`), `ExamStructureOps`, nhân bản bài, đầu vào kiểm tra, đích sinh thử | Sau mỗi chặng, `audioRequirement` và điều kiện câu con còn nguyên; `displaySegments` còn nguyên ở bài `SUBJECT` |
| 5 | `displaySegments` có khoảng hở, chồng lấn, không phủ hết số câu của phần, hoặc tổng điểm khác `maxPoints` của phần | 422 `EXAM_STRUCTURE_INVALID`, `fieldErrors` chỉ tới phân đoạn |
| 6 | Nhóm chùm để trống `childAnswerType`, điều kiện có `answerType` không phủ kín 1 đến `setSize` hoặc chồng nhau | 422 `EXAM_STRUCTURE_INVALID` |
| 7 | Chỉ tiêu cấp bài và chỉ tiêu chủ đề `basis = PROMPT`, `REFERENCE` | Lưu được |
| 8 | Chỉ tiêu cấp bài hoặc chỉ tiêu chủ đề `basis = PROMPT`, `REQUIRED` | 422 |
| 9 | Mẫu `PART` có `subject_code` | Migration chạy, ràng buộc nhận; mẫu `FULL` có `subject_code` vẫn bị từ chối |
| 10 | Danh mục dev | Có `HSCA`, `VSAT`, hồ sơ đúng H36, hai nhãn Địa lí |
| 11 | Bộ test backend hiện có | Xanh; kết quả kiểm tra và sinh thử của bảy kỳ thi cũ không đổi |

Ca 7 và 8 theo D19 (Q-38 đã chốt): theo lệnh chỉ có mức đối chiếu.

### BE-17 · Nguồn câu: audio, dạng câu con, số phương án, chiều điểm

**Nhóm:** Bộ giải. **Điểm:** 4. **Cứng:** BE-16. **Spec:** H38, H39, H47.

**Sở hữu:** `questionbank/application/{QuestionSourceAdapter,AvailableQuestionCountService}.java`, `questionbank/api/AvailableQuestionCountController.java`, `questionbank/spi/QuestionSourcePort.java`, `exam/application/SolverInputBuilder.java`, `exam/application/DefaultExamChecker.java` (đoạn đổi số đếm bộ lọc thành bằng chứng).

Không dùng `Unit.hasAttachment` cho audio: trường này đúng với mọi loại tệp. Không sửa `exam/domain/solver/**`.

Trong `DefaultExamChecker.java` chỉ sửa hàm `filterEvidence`; hàm dựng và lời gọi `ExamCheckUnitEvidence.witnessFacts` thuộc BE-20. `QuestionSourcePort.Filter`, `ItemShape`, `ChildCondition` đang được dựng bằng hàm dựng đủ tham số ở 11 tệp, gồm `SampleQuestionCoverageIT` của BE-18 (19 chỗ) và `QuestionSourcePortIT` (32 chỗ): thêm trường thì **giữ hàm dựng cũ** để các tệp đó vẫn biên dịch; BE-17 chỉ sửa test của chính mình.

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | `REQUIRED`, câu đơn có tệp MP3 | Được nhận |
| 2 | `REQUIRED`, câu chỉ có tệp PDF hoặc ảnh | Bị loại |
| 3 | `REQUIRED`, chùm có audio ở câu gốc, câu con không có tệp | Cả chùm được nhận |
| 4 | `FORBIDDEN`, chùm có audio ở câu gốc | Cả chùm bị loại |
| 5 | `ANY` hoặc không đặt | Kết quả như trước khi có trường |
| 6 | Số đếm khả dụng với cùng điều kiện | Bằng số đơn vị bộ giải nhận được |
| 7 | Kiểm tra bài có dòng cần 3 chùm × 4 câu có audio; nguồn có 2 chùm có audio và thừa chùm không audio | `AUDIO_SHORTAGE` tại đúng dòng, `required = 3`, `available = 2` (ví dụ của HS §9) |
| 8 | Chùm 8 câu con: vị trí 1–2 Đúng/Sai bốn ý, 3–8 chọn một | Được nhận |
| 9 | Chùm 8 câu chọn một, hoặc Đúng/Sai ba ý ở vị trí 1–2 | Bị loại, mã `CHILD_CONDITION_SHORTAGE` tại nhóm |
| 10 | `ChildCondition` chỉ có `nodeIds` như cũ | Kết quả không đổi |
| 11 | `ItemShape.options = 4`, câu năm phương án | Bị loại, mã `ITEM_SHAPE_SHORTAGE` khi vì thế mà thiếu |
| 12 | Nguồn đủ câu nhưng điểm mỗi câu là 1, mẫu cần 6 | `POINTS_SHORTAGE` tại nhóm, không phải thiếu nguồn chung |
| 13 | Đo `perf` đề 75 câu có lọc audio | Trong hạn 30 giây của bộ giải; ghi số đo vào báo cáo |

### BE-18 · Mẫu H-SCA, V-SAT, hồ sơ, phạm vi

**Nhóm:** Bài thi. **Điểm:** 6. **Cứng:** BE-16. **Mềm:** BE-17, BE-19 (chỉ nộp khi cả hai đã merge). **Spec:** mục 4, 5, H37, H41–H43, H45, H46, H49.

Vì sao chờ: `SampleQuestionCoverageIT` duyệt mọi mẫu `STANDARD`, nên mẫu mới chỉ xanh khi dữ liệu của BE-19 đã có và bộ lọc audio của BE-17 đã chạy. Đây là thứ tự của BE-13, BE-14 ở kế hoạch bổ sung UNI-2671. Lane làm song song từ sóng 2, chỉ nộp ở sóng 3.

**Sở hữu:** `backend/src/test/resources/templates/{hsca,vsat}.templates.mjs`, `build.mjs` (thêm hai cột cờ vào `columns`, mặc định `false`; đổi tập `migrated` thành hai tệp nguồn mới), `expected-totals.json`, migration mẫu sinh ra, `exam/application/ExamService.java` (tạo bài theo phạm vi, `assertTemplateMatches`), `backend/src/test/java/vn/unicom/thi360/exam/{SampleQuestionCoverageIT,ExamTemplateTotalsIT}.java` (đọc điều kiện audio, dạng câu con, số phương án của mẫu).

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Tổng từng mẫu H-SCA bốn môn | 40 câu, 4 dòng 20 + 5 + 5 + 10, 90 phút, 10 điểm |
| 2 | Bảng lớp, độ khó, biến thể "Sát đề minh họa" | Từng số khớp spec 4.1 |
| 3 | H-SCA Ngữ văn | 20 câu, 3 chùm 5, 7, 8; hai phần viết `omitted`, `omittedLocked = true`; tạo `PART` phần viết trả `EXAM_PART_NOT_FOUND`. Tạo `PART` phần Viết của SP2E Ngữ văn (mẫu không có cờ) vẫn như hiện tại |
| 4 | H-SCA Tiếng Anh | Nghe 35 (8, 12, 15) `REQUIRED`, Đọc 40 (4 × 10) `FORBIDDEN`; tạo được `SUBJECT` 75 câu và từng `PART` |
| 5 | Tổng từng mẫu V-SAT sáu môn | 25 câu, 67 lệnh, 150 điểm; thời lượng đúng spec 5.1 |
| 6 | V-SAT Ngữ văn | 20 câu, 62 lệnh, 120 điểm, thời lượng trống; phần thứ tư "Viết" chỉ có một nhóm `ESSAY` đã lược, 1 câu, 30 điểm (spec H49), `omittedLocked = true`; kiểm tra bài FULL không báo `PART_EMPTY`, `PART_TOTAL_MISMATCH`, `PART_POINTS_MISMATCH`; tạo `PART` P4 trả `EXAM_PART_NOT_FOUND` |
| 7 | V-SAT Hóa học | Mặc định 60 phút; biến thể 50 phút chọn được |
| 8 | Phân bổ đối chiếu V-SAT | Từng số khớp spec 5.3, mức `REFERENCE`, `basis = PROMPT`; mạch của Ngữ văn, Địa lí là `TopicQuota` chờ mapping |
| 9 | Tạo `PART` một phần của môn thường, không gửi thời lượng | 422 `EXAM_DURATION_REQUIRED`; gửi thời lượng thì tạo được, chỉ có phần đó, `maxScore` bằng điểm phần |
| 10 | Bài `PART` Phần II Toán V-SAT đổi 2 chùm thành 3 chùm | Lưu và kiểm tra không báo `BLOCK_COUNT_LOCKED` và không báo `STRUCTURE_COUNT_LOCKED` (mọi mẫu của hai kỳ thi có `fixedCounts = false`, spec H42); dòng tổng 9 câu, 9 lệnh, 54 điểm (VS §3) |
| 11 | Nạp lại mẫu cho bài `PART` của H-SCA và V-SAT | Không báo `EXAM_TEMPLATE_MISMATCH` |
| 12 | Tạo bài ôn `HSCA_REVIEW`, `VSAT_REVIEW` | Tạo được theo môn đã chọn |
| 13 | Sinh thử H-SCA Tiếng Anh Nghe và Đọc trên dữ liệu dev | Đạt; mọi câu phần Nghe có audio, phần Đọc không có |
| 14 | `SampleQuestionCoverageIT`, `ExamTemplateTotalsIT` | Xanh với mọi mẫu `STANDARD`, gồm mẫu mới |
| 15 | Mẫu của 7 kỳ thi cũ | `expected-totals.json` các dòng cũ không đổi |

**Số hiệu migration.** Lane tạo tệp ở sóng 2 nhưng nộp ở sóng 3, và Flyway của repo không bật `outOfOrder`. Ngay trước khi nộp, đặt lại số hiệu để lớn hơn mọi migration đang có trên `integration/dot-2b`, gồm migration cột của BE-16.

### BE-19 · Dữ liệu câu mẫu, có audio

**Nhóm:** Nền. **Điểm:** 3. **Cứng:** BE-17. **Mềm:** BE-20 (ca 5). **Spec:** R-145, mục 6 "Dữ liệu audio cho dev".

Không chờ BE-18, BE-21: dữ liệu làm theo bảng ở spec mục 4 và 5, gồm cả Tiếng Anh V-SAT (một chùm 8 câu trộn dạng, một chùm 6 câu chọn một, một Cloze 5 vị trí, câu ghép hợp 4 mục). Mỗi dòng nghiệm thu bằng một ca đếm nguồn qua `QuestionSourcePort`, cùng kiểu BE-14.

**Sở hữu:** `R__seed_questionbank_dev_data.sql`, tệp MP3 mẫu nhỏ trong `backend/src/main/resources/db/devdata/assets/`, bộ nạp mới `questionbank/application/DevQuestionAssetSeeder.java` (`ApplicationRunner`, hồ sơ `dev` và `test`), `docs/specs/2026-11-17-de-test-gia-dinh.md` (phần dữ liệu).

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Đếm nguồn cho từng dòng của spec 4.1–4.3, 5.1, 5.2 với đúng bộ lọc của dòng | Đủ số đơn vị dòng đó cần |
| 2 | Nguồn Tiếng Anh H-SCA | Đủ 8 câu đơn, 3 chùm × 4, 3 chùm × 5 có audio; đủ 4 chùm × 10 không audio, bốn phương án |
| 3 | Câu V-SAT theo đúng điểm mẫu | Đúng/Sai 6 điểm bảng `[1,2,3,6]`, ghép hợp 1,5 mỗi cặp, Cloze 6 mỗi vị trí |
| 4 | Dựng lại dữ liệu dev từ đầu, chạy bộ nạp hai lần | Không lỗi, không tạo trùng; `ObjectStorage.exists` đúng với mọi tệp audio mẫu; `size`, `sha256`, `mime` của dòng `question_asset` khớp tệp |
| 5 | Kiểm tra một bài dùng câu audio mẫu, sau khi BE-20 merge | Không có `ATTACHMENT_MISSING` |
| 6 | Dữ liệu của 7 kỳ thi cũ | `SampleQuestionCoverageIT` các mẫu cũ vẫn xanh |

**Kho lưu trữ trong test.** Bộ test tích hợp dùng `InMemoryObjectStorage` (`thi360.s3.mode: memory`, bean ở `TestSecurity`), trống khi khởi động. Bộ nạp chạy cả ở hồ sơ `test` nên mọi ngữ cảnh test có sẵn tệp audio mẫu; `ExamStructureV2DevdataDevIT` chạy hồ sơ `dev` với kho trong bộ nhớ cũng vậy. Bộ nạp chỉ ghi kho lưu trữ, không cần Keycloak. Ca 4 chạy trên kho trong bộ nhớ; chạy thật với MinIO là việc của QA trên máy chủ dev.

### BE-20 · Kiểm tra: khóa dòng, tệp audio, thang, đối chiếu theo lệnh

**Nhóm:** Kiểm tra. **Điểm:** 3. **Cứng:** BE-16. **Spec:** H40 (luật 3), H42–H44, H48, H49, mục 6.

**Sở hữu:** `exam/application/{ExamCheckRules,ExamCheckUnitEvidence,ExamCheckTemplateFacts,ExamCheckDistribution,PreviewCompliance,PreviewSnapshotAssembler,ExamPreviewService,PreviewPaperOrdering}.java`, `exam/application/DefaultExamChecker.java` (chỉ hàm dựng và lời gọi `ExamCheckUnitEvidence.witnessFacts`, để truyền cổng tệp đính kèm), `exam/api/ExamPreviewController.java`, hai tệp mới `questionbank/spi/QuestionAttachmentPort.java` và `questionbank/application/QuestionAttachmentAdapter.java`.

`ExamPreviewService` và `PreviewPaperOrdering` là nơi dựng `PreviewPart`, `PreviewItem`, `PreviewQuestion`; thiếu hai tệp này thì không thêm được trường sinh thử. Cổng tệp đính kèm nằm ở tệp riêng để không sửa chung `QuestionSourcePort.java`, `QuestionSourceAdapter.java` với BE-17. `AUDIO_SHORTAGE`, `POINTS_SHORTAGE` không thuộc task này: mã do BE-16 khai báo, bằng chứng do BE-17 tính.

Test dùng mẫu giả dựng trong test, không chờ mẫu thật của BE-18. Ca 5 và 6 chạy trên `InMemoryObjectStorage` của bộ test: test tự chèn câu có tệp, `put` rồi `delete` object, và trả kho về như cũ sau khi chạy (bean dùng chung cho các lớp test cùng ngữ cảnh). Ca 7 không dựng được bằng kho trong bộ nhớ: dùng test đơn vị của `QuestionAttachmentAdapter` với một `ObjectStorage` giả ném `ExternalServiceException`, đúng lỗi mà `S3ObjectStorage.exists` ném khi mất kết nối.

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Bài `SUBJECT` từ mẫu khóa dòng, đổi phần 1 thành 22 + 3 | `BLOCK_COUNT_LOCKED` tại dòng, kèm số của mẫu và số hiện có |
| 2 | Bài `PART` từ cùng mẫu đổi số chùm | Không báo `BLOCK_COUNT_LOCKED` |
| 3 | V-ACT FULL chuyển số câu giữa các nhóm trong một mục, tổng của mục giữ nguyên | Không báo `STRUCTURE_COUNT_LOCKED`, không báo `BLOCK_COUNT_LOCKED` (H27 giữ nguyên) |
| 4 | Bài từ mẫu `omittedLocked`: bật lại nhóm viết đã lược. Bài từ mẫu không có cờ: bật lại nhóm đã lược | Ca đầu `OMITTED_BLOCK_LOCKED` tại nhóm; ca sau không báo mã này |
| 5 | Nguồn có đúng số câu audio dòng cần; object của một tệp bị xóa khỏi kho lưu trữ (dòng `question_asset` vẫn còn) | `ATTACHMENT_MISSING` nêu đúng mã câu đó (với chùm là mã câu gốc, một lần), trạng thái kiểm tra không đạt, công bố bị từ chối |
| 6 | Đề có câu kèm tệp đính kèm còn đủ; đề không có tệp đính kèm nào | Không phát sinh lỗi mới ở cả hai; đề không có tệp không gọi kho lưu trữ |
| 7 | Kho lưu trữ không kết nối được | Lần kiểm tra thất bại như lỗi hạ tầng khác, không ghi `ATTACHMENT_MISSING` |
| 8 | Bài từ mẫu `omittedLocked` có phần "Viết" đã lược 30 điểm, tổng điểm 120, đặt thang 150 | `SCALE_INCLUDES_EXCLUDED_PART`, chi tiết "Viết", 30; không kèm `POINTS_TOTAL_MISMATCH` |
| 9 | Cùng bài đặt thang 140. Bài SP2E Ngữ văn (mẫu không có cờ, phần Viết đã lược 4 điểm, tổng 6) đặt thang 10 | Cả hai báo `POINTS_TOTAL_MISMATCH` như cũ, không có mã mới |
| 10 | Đối chiếu Toán FULL theo lệnh | Mẫu số 67; câu thiếu nhãn vào dòng "Chưa phân loại" |
| 11 | Sinh thử câu đơn có audio và chùm có audio ở câu gốc | `PreviewQuestion.attachments` và `PreviewItem.attachments` có id, tên, MIME |
| 12 | Sinh thử bài có `displaySegments` | `PreviewPart.displaySegments` trả đủ; lệnh và điểm thật của từng dải bằng số khai báo, lệch thì có lỗi tại phần |

### BE-21 · Tiếng Anh V-SAT: bài đọc dùng chung

**Nhóm:** Bài thi. **Điểm:** 3. **Cứng:** BE-17, BE-18. **Spec:** H40, H41, mục 5.2.

**Sở hữu:** `backend/src/test/resources/templates/vsat-anh.templates.mjs`, migration mẫu riêng, `build.mjs` (đổi tập `migrated` thành `vsat-anh.templates.mjs` để sinh migration riêng), nhánh tìm mẫu `PART` riêng trong `exam/application/ExamService.java` ở cả ba chỗ đang gọi `selectSubjectPart`: `doCreate`, `updateExamInfo` (đổi phạm vi), `loadTemplate`; và `exam/application/ExamTemplateService.java`, `exam/application/ExamCheckUnitSummary.java` (số lệnh của nhóm trộn dạng).

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Tổng mẫu FULL | 25 câu, 67 lệnh, 150 điểm, 60 phút, bốn nhóm 7, 8, 5, 5; `fixedBlockCounts = true` |
| 2 | `displaySegments` | Phần I 1–9, II 10–15, III 16–20, IV 21–25; lệnh 36, 6, 20, 5; điểm 54, 36, 30, 30 |
| 3 | Tóm tắt cấu trúc của bài FULL | Nhóm 8–15 hiện 8 câu, 14 lệnh, 48 điểm; cả bài 25 câu, 67 lệnh, 150 điểm |
| 4 | Sinh thử FULL | Bài đọc 8–15 xuất hiện một lần, hai câu Đúng/Sai ở số 8, 9, sáu câu chọn một ở số 10–15 |
| 5 | Nguồn chỉ có chùm 8 câu chọn một | `CHILD_CONDITION_SHORTAGE` tại nhóm "Bài đọc dùng chung – câu 8–15" |
| 6 | Nguồn chỉ có chùm 6 câu chọn một, không có chùm 8 câu | `SET_SHORTAGE` tại cùng nhóm đó |
| 7 | Tạo `PART` P2 | 1 chùm × 6 câu chọn một, 6 lệnh, 36 điểm, kèm bài đọc |
| 8 | Tạo `PART` P3 | 5 câu ghép hợp 4 mục, 20 lệnh, 30 điểm |
| 9 | Tạo `PART` P4 | Một Cloze 5 vị trí, 5 câu, 5 lệnh, 30 điểm |
| 10 | Tạo `PART` P1 Tiếng Anh V-SAT; đổi một bài Tiếng Anh V-SAT sang `PART` P1 bằng PATCH | `EXAM_PART_NOT_FOUND` ở cả hai; không tạo ra bài 25 câu |
| 11 | Không có chùm sáu câu cho P2 | Báo thiếu chùm sáu câu tại nhóm, không lấy nhánh của chùm 8 câu |
| 12 | Tạo `PART` môn V-SAT khác | Vẫn cắt từ mẫu môn |
| 13 | `SampleQuestionCoverageIT` | Xanh với ba mẫu `PART` và mẫu FULL của Tiếng Anh |

### FE-6 · Bước 1 cho H-SCA, V-SAT; giữ trường mới khi lưu

**Nhóm:** Web admin. **Điểm:** 3. **Cứng:** BE-16, G2c. **Spec:** R-127, R-134, R-141, R-144, R-146.

**Sở hữu:** `types.ts`, `api-mapping.ts`, `create-parts.ts`, `create-exam-schema.ts`, `create-exam-overview.ts`, `components/create-exam-page.tsx`, `components/create-exam-scope-fields.tsx`, `components/exam-list-view.tsx`, nhóm khóa kỳ thi trong `messages/{vi,en}.json`.

Ô thời lượng hiện theo mẫu và phạm vi, không theo mã kỳ thi (bỏ hằng `DURATION_ENTERED_KIND = "THPTQG"` ở `create-exam-schema.ts`).

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Chọn H-SCA Tiếng Anh | Có Nghe, Đọc, Nghe và Đọc; không có nhãn lẫn ngôn ngữ |
| 2 | Chọn V-SAT Ngữ văn | Bắt buộc nhập thời lượng, bỏ trống không tạo được |
| 3 | Chọn V-SAT Toán | Thời lượng 90 phút điền sẵn |
| 4 | Chọn H-SCA Toán theo phần, V-SAT Tiếng Anh Phần IV | Bắt buộc nhập thời lượng |
| 5 | Chọn V-SAT Tiếng Anh theo phần | Chỉ có Phần II, III, IV |
| 6 | THPTQG theo phần | Hành vi nhập thời lượng không đổi |
| 7 | Nạp một cấu trúc có `audioRequirement`, `displaySegments`, `childConditions` có `answerType` và `itemShape`, `itemShape.options`; sửa tên một nhóm; lưu | Thân yêu cầu gửi lên còn nguyên bốn trường; đọc lại không đổi |
| 8 | Cấu trúc của 7 kỳ thi cũ qua `api-mapping` | Không sinh thêm trường |
| 9 | Tạo bài từ mẫu có `audioRequirement` và `childConditions` có `answerType`, sửa một số câu ở bảng tổng quan rồi tạo | Cấu trúc gửi lên còn nguyên các trường đó (`create-parts.ts`) |
| 10 | H-SCA Ngữ văn, V-SAT Ngữ văn theo phần | Danh sách phần không có phần viết đã lược |
| 11 | Bài `SUBJECT` từ mẫu `fixedBlockCounts` ở bảng tổng quan bước 1 | Ô số câu chỉ đọc, như mẫu `fixedCounts` hiện nay; bài `PART` vẫn nhập được |
| 12 | Sáng, tối, chiều rộng điện thoại | Đạt theo bản vẽ G2c |

Ca 5 dùng dữ liệu giả cho đến khi BE-21 merge; QA chạy lại trên máy chủ thật.

### FE-7 · Bước 2: audio, khóa dòng, ví dụ chấm

**Nhóm:** Web admin. **Điểm:** 4. **Cứng:** BE-16, FE-6, G2c. **Mềm:** BE-17 (ca 2, chỉ nộp khi đã merge), BE-21 (ca 6). **Spec:** R-130, R-135, R-140, R-142.

**Sở hữu:** `components/block-editor.tsx`, `components/block-set-conditions.tsx`, `components/block-scoring-editor.tsx`, `components/exam-structure-view.tsx` (nơi tính cờ khóa `countLocked` từ mẫu, hiện chỉ đọc `fixedCounts`), `api.http.ts` (hai lời gọi đếm câu khả dụng), `reference-distribution.ts`, tệp mới `components/scoring-examples.tsx`, nhóm khóa bước 2 trong `messages/{vi,en}.json`.

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Dòng trong phần Nghe | Nhãn "Có audio", không đổi được ở dòng; phần Đọc nhãn "Không có audio" |
| 2 | Số khả dụng của dòng trong phần Nghe | Lời gọi đếm gửi `audioRequirement`; số hiện bằng số API trả |
| 3 | Dòng lấy câu | Hiện tổng câu và tỷ lệ trong phần (8 câu, 22,9%) |
| 4 | Dòng chùm nhập tỷ lệ không ra chùm nguyên (10 câu, cỡ 4) | Báo lỗi ngay tại dòng, không lưu được |
| 5 | Bài `SUBJECT` từ mẫu `fixedBlockCounts` | Ô số lượng và dạng chỉ đọc, có lý do; bài `PART` từ cùng mẫu vẫn sửa được |
| 6 | Chùm trộn dạng | Hiện điều kiện vị trí 1–2 Đúng/Sai, 3–8 chọn một; dòng tổng 8 câu, 14 lệnh |
| 7 | Ví dụ chấm V-SAT | Đúng/Sai 3/4 được 3; ghép hợp 3/4 được 4,5 |
| 8 | Ví dụ chấm H-SCA | Đúng, một phần, sai, bỏ trống; chú thích luật chờ BA xác nhận |
| 9 | Mạch đối chiếu V-SAT | Hiện đúng bảng spec 5.3 theo lệnh, trạng thái Chờ mapping |
| 10 | Sáng, tối, hai ngôn ngữ | Không có nhãn lẫn ngôn ngữ |

Ca 6: số lệnh của dòng do máy chủ trả (`commandCount`), và máy chủ chỉ tính được 14 lệnh cho nhóm trộn dạng sau khi BE-21 sửa `ExamCheckUnitSummary`. Ca này dùng dữ liệu giả cho đến khi BE-21 merge; QA chạy lại trên máy chủ thật.

### FE-8 · Kiểm tra và sinh thử: audio, phân đoạn, thông điệp

**Nhóm:** Web admin. **Điểm:** 2. **Cứng:** BE-20, G2c. **Mềm:** FE-7, BE-21. **Spec:** R-133, R-139, R-143.

**Sở hữu:** `components/exam-preview-paper.tsx`, `components/exam-preview-compliance.tsx`, `review-compliance.ts`, `review-api.ts`, `review-types.ts`, nhóm khóa kiểm tra trong `messages/{vi,en}.json`.

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Sinh thử câu đơn có audio và chùm có audio | Có trình phát ở câu, ở đầu chùm; thao tác được bằng bàn phím; nguồn là `GET /api/v1/admin/question-assets/{id}` |
| 2 | Sinh thử Tiếng Anh V-SAT FULL | Đề có bốn tiêu đề Phần I–IV theo `displaySegments`; câu 8, 9 dưới Phần I, 10–15 dưới Phần II; bài đọc hiện ở cả hai phần; số câu liền từ 1 đến 25 |
| 3 | Sinh thử bài không có `displaySegments` | Hiển thị như hiện tại |
| 4 | Năm mã kiểm tra mới | Có câu chữ vi, en; bấm dẫn tới đúng dòng |
| 5 | Đối chiếu theo lệnh | Mẫu số "Toán FULL · 67 lệnh", có dòng "Chưa phân loại" |

Ca 2 dùng dữ liệu giả cho đến khi BE-21 merge (mẫu Tiếng Anh V-SAT chưa có); QA chạy lại trên máy chủ thật.

## 3. Pipeline

| Task | Cứng | Mềm | Mở khi |
|---|---|---|---|
| G2c | | | Ngay, song song FIX-QA |
| BE-16 | FIX-QA | | FIX-QA merge |
| BE-17 | BE-16 | | BE-16 merge |
| BE-20 | BE-16 | | BE-16 merge |
| BE-18 | BE-16 | BE-17, BE-19 | BE-16 merge; chỉ nộp sau BE-17, BE-19 |
| FE-6 | BE-16, G2c | | BE-16 merge và có bản vẽ |
| BE-19 | BE-17 | BE-20 | BE-17 merge |
| FE-7 | BE-16, FE-6, G2c | BE-17, BE-21 | FE-6 merge |
| FE-8 | BE-20, G2c | FE-7, BE-21 | BE-20 merge |
| BE-21 | BE-17, BE-18 | | BE-18 merge |
| QA phần mở rộng | Mọi task trên | | Sau BE-21, FE-7, FE-8 |

| Sóng | Task | Chạy song song |
|---|---|---|
| 0 | FIX-QA merge; G2c | Có |
| 1 | BE-16 | Một lane |
| 2 | BE-17, BE-20, BE-18 (làm, chưa nộp), FE-6 | Bốn lane |
| 3 | BE-19, FE-7, FE-8; BE-18 nộp sau BE-19 | Bốn lane |
| 4 | BE-21 | Một lane |
| 5 | QA phần mở rộng | Một lane |

```text
FIX-QA ──> BE-16 ──┬─> BE-17 ──> BE-19 ──┐
                   │      └──────────────┼─> BE-18 (nộp) ──> BE-21 ──┐
                   ├─> BE-18 (làm) ──────┘                           │
                   ├─> BE-20 ──> FE-8 ───────────────────────────────┼─> QA
G2c ───────────────┴─> FE-6 ──> FE-7 ────────────────────────────────┘
```

**Đường dài nhất:** BE-16 → BE-17 → BE-19 → BE-18 (nộp) → BE-21 → QA phần mở rộng, 3 + 4 + 3 + 6 + 3 + 2 = 21 điểm. BE-18 làm song song từ sóng 2 nên phần chờ thật chỉ là lúc nộp. Tối đa 4 lane cùng lúc.

Bảng phụ thuộc không có vòng: BE-19 không chờ BE-18 hay BE-21; BE-18 không chờ BE-20, BE-21; BE-21 không chờ lane FE nào. Sóng chỉ là thứ tự mở sớm nhất: FE-8 mở khi BE-20 merge, BE-19 mở khi BE-17 merge, nên lane sóng 3 có thể chạy cùng lúc với lane sóng 2 chưa xong. Mục 5 xét xung đột theo mọi cặp lane có thể chạy cùng lúc, không theo từng sóng.

**G2c, cổng thiết kế (0 điểm).** Làm song song với FIX-QA, thêm vào `docs/design/dot-2b/`. FIX-QA không sửa thư mục này. Lane FE chờ bản vẽ, lane backend không chờ.

| Bản vẽ | Nội dung | Task |
|---|---|---|
| `TaoBaiThiHscaVsat2B` | Bước 1: kỳ thi, môn, phạm vi Nghe/Đọc, ô thời lượng | FE-6 |
| `NhomAudio2B` | Dòng lấy câu có nhãn audio, tổng câu, tỷ lệ, dòng bị khóa | FE-7 |
| `BaiDocDungChung2B` | Chùm trộn dạng; ranh giới Phần I–IV cắt giữa chùm, bài đọc hiện ở cả hai phần | FE-7, FE-8 |
| `ViDuCham2B` | Khối ví dụ chấm ở bước 2 | FE-7 |
| `SinhThuAudio2B` | Trình phát trong đề sinh thử (câu đơn và đầu chùm), đối chiếu theo lệnh | FE-8 |

## 4. Thay đổi lane QA (UNI-2666)

Điểm 7 → 9. Phần E2E đang làm giữ nguyên và nghiệm thu trước; phần mở rộng chờ sóng 4 xong. Advisor cập nhật `contracts/QA.md`: `plan_task` thành 9/160, thêm đường dẫn đặc tả và kế hoạch này, `base_sha` mới. Thêm:

- H-SCA Toán FULL: tạo, khóa dòng, kiểm tra, sinh thử, công bố.
- H-SCA Tiếng Anh Nghe và Đọc: Nghe chỉ có câu audio, nghe được audio trong đề sinh thử; bài có tệp audio bị mất khỏi kho thì kiểm tra báo `ATTACHMENT_MISSING` và không công bố được.
- H-SCA Ngữ văn: công bố bài Đọc hiểu, không bật lại được phần viết.
- V-SAT Toán FULL: 25 câu, 67 lệnh, 150 điểm.
- V-SAT Tiếng Anh FULL: bài đọc 8–15 xuất hiện một lần, hiển thị Phần I–IV; lưu bước 2 rồi sinh thử lại vẫn còn Phần I–IV (R-146).
- V-SAT Tiếng Anh theo phần: chỉ có Phần II, III, IV trên máy chủ thật; Phần IV 5 câu, 30 điểm.
- V-SAT Ngữ văn: thang 150 báo đúng 30 điểm phần Viết; không bật lại được phần Viết; theo phần chỉ có Phần I, II, III.
- Một bài của mỗi kỳ thi cũ có câu kèm tệp đính kèm: kết quả kiểm tra không đổi sau H48.
- Cập nhật `docs/specs/2026-11-17-de-test-gia-dinh.md`; báo cáo các Q còn mở ở spec mục 8, 9.

## 5. Xung đột sở hữu

| Tệp | Task | Cách xử lý |
|---|---|---|
| Enum `FilterDimension`, `FilterKind`, ánh xạ mã | BE-17, BE-20 | BE-16 khai báo trước, ở sóng 1 |
| Chỗ chép `ExamPart`, `ExamSection` trong tệp của lane khác | BE-16 | Sửa hết ở sóng 1, trước khi lane nào mở |
| `SolverInputBuilder.java` | BE-17 | Chỉ BE-17 sửa sau sóng 1 |
| `DefaultExamChecker.java` | BE-17, BE-20 (cùng sóng 2) | Chia theo hàm: BE-17 chỉ sửa `filterEvidence`; BE-20 chỉ sửa hàm dựng và lời gọi `witnessFacts`. Hai vùng cách nhau khoảng 80 dòng; lane nộp sau rebase |
| Hàm dựng của `QuestionSourcePort.Filter`, `ItemShape`, `ChildCondition` | BE-17; BE-18 dùng trong `SampleQuestionCoverageIT` | BE-17 giữ hàm dựng cũ. BE-18 chuyển sang hàm dựng mới sau khi rebase lên BE-17 (BE-18 vốn chỉ nộp sau BE-17) |
| `QuestionSourcePort.java`, `QuestionSourceAdapter.java` | BE-17 | BE-20 không sửa; dùng `QuestionAttachmentPort` ở tệp riêng |
| `ExamCheckRules.java`, `ExamPreviewService.java` | BE-20 | Chỉ BE-20 sửa sau sóng 1 |
| `ExamCheckUnitSummary.java` | BE-21 | Chỉ BE-21 sửa |
| `ExamService.java` | BE-16, BE-18, BE-21 | Nối tiếp, không lane nào chạy cùng lúc: BE-16 (sóng 1, chỗ chép cấu trúc), BE-18 (sóng 2 và 3), BE-21 (sóng 4). BE-17, BE-19, BE-20 không sửa tệp này |
| `ExamTemplateService.java` | BE-16, BE-21 | BE-16 chỉ `toResponse`; BE-21 chạy sau |
| `build.mjs`, `expected-totals.json` | BE-18, BE-21 | Nối tiếp. `build.mjs` tự quét mọi tệp `*.templates.mjs` trong thư mục, không cần đăng ký tệp nguồn; chỉ tập `migrated` quyết định tệp nào vào migration đang sinh. BE-21 tự tạo `vsat-anh.templates.mjs`, đổi `migrated` và sinh lại `expected-totals.json` (tệp sinh ra, không gộp tay) sau khi rebase lên BE-18 |
| `SampleQuestionCoverageIT.java` | BE-18 | BE-19, BE-21 chỉ chạy, không sửa |
| `R__seed_questionbank_dev_data.sql` | BE-19 | Chỉ BE-19 sửa |
| `docs/specs/2026-11-17-de-test-gia-dinh.md` | BE-19, QA | Nối tiếp: QA mở sau khi BE-19 merge |
| `packages/api-client` (`openapi.json`, `src/schema.d.ts`) | BE-16, BE-17, BE-20 | Tệp sinh ra. BE-17 (tham số đếm) và BE-20 (trường sinh thử) chạy song song: lane merge sau lấy nguyên bản trên nhánh tích hợp khi rebase rồi sinh lại, không gộp tay. BE-18, BE-19, BE-21 không đổi API |
| `messages{,_vi,_en}.properties` | BE-16 | Thêm sẵn năm mã; lane khác không sửa |
| `types.ts`, `api-mapping.ts` | FE-6 | FE-7 mở sau FE-6; FE-8 chỉ sửa `review-types.ts`, `review-api.ts` |
| `components/exam-structure-view.tsx`, `api.http.ts` | FE-7 | FE-6 và FE-8 không sửa; FIX-QA đã merge trước |
| `messages/{vi,en}.json` | FE-6, FE-7, FE-8 | Mỗi lane chỉ thêm khóa trong nhóm của mình (kỳ thi, bước 2, kiểm tra), không sắp xếp lại tệp. FE-8 có thể chạy cùng lúc với FE-6 và FE-7; lane nộp sau rebase |
| Bản vẽ `BaiDocDungChung2B` | FE-7, FE-8 | Chỉ đọc |
| Số hiệu migration | BE-16, BE-18, BE-21 | Thứ tự bắt buộc: cột của BE-16, rồi mẫu của BE-18, rồi mẫu Tiếng Anh của BE-21. Theo Plan B2 lấy giờ phút lúc tạo tệp; riêng BE-18 tạo tệp sớm mà nộp muộn nên đặt lại số hiệu ngay trước khi nộp (Flyway không bật `outOfOrder`). BE-19 chỉ sửa tệp `R__`, không có số hiệu |

Sóng 2 (BE-17, BE-18, BE-20, FE-6) có hai tệp chung: `packages/api-client` (BE-17, BE-20) và `DefaultExamChecker.java` (BE-17, BE-20, chia theo hàm). Sóng 3 (BE-19, FE-7, FE-8, cùng BE-18 đang chờ nộp) chỉ chung `messages/{vi,en}.json` giữa FE-7 và FE-8. Với FIX-QA không có xung đột vì mọi task tách nhánh sau khi FIX-QA merge.

## 6. Rủi ro

| Rủi ro | Dấu hiệu | Cách xử lý |
|---|---|---|
| BA trả lời Q-36 khác suy luận | Bảng cấu trúc V-SAT khác spec 5.1 | Chỉ sửa `vsat.templates.mjs` và dữ liệu mẫu. Nên hỏi trước khi mở BE-18 |
| BA không chấp nhận chỗ lệch với VS §5.1 điểm 4 và 6 (D19) | BA yêu cầu chỉ tiêu bắt buộc theo lệnh hoặc đếm theo nhãn từng ý | Task mới ở ngân hàng câu hỏi, không nằm trong 160 điểm; khi đó sửa H44 và ca 7, 8 của BE-16 |
| Trình phát không phát được audio | Ca 1 của FE-8 đỏ: `GET /question-assets/{id}` trả tệp đính kèm với `Content-Disposition: attachment` và chưa rõ có hỗ trợ `Range` | Báo Advisor. Sửa tiêu đề phản hồi cho MIME `audio/*` nằm ở `AdminQuestionAssetController`, chưa lane nào sở hữu; không tự thêm API tải tệp |
| Sót chỗ chép cấu trúc | Bộ giải nhận câu không audio ở phần Nghe; mất Phần I–IV sau khi lưu | Ca 4 của BE-16, ca 7 của FE-6; QA lưu bước 2 rồi sinh thử lại |
| Lọc audio làm chậm truy vấn nguồn | Ca `perf` của BE-17 vượt hạn | Báo Advisor; thêm chỉ mục theo MIME, không tự đổi hạn |
| H48 làm kiểm tra chậm hoặc đỏ vì kho lưu trữ | Ca 6, 7 của BE-20; thời gian kiểm tra tăng ở đề nhiều tệp | Gộp các lần gọi theo tệp khác nhau; không hạ mức lỗi khi chưa hỏi Advisor |
| Tệp audio mẫu không vào kho lưu trữ | Ca 4, 5 của BE-19 đỏ | Sửa bộ nạp; không tắt H48 ở dev |
| Q-32 đổi luật chấm chọn nhiều | BA chọn luật khác `PARTIAL` | 2B chỉ đổi `expectedScoring` trong mẫu; giá trị enum mới làm ở đợt 3 |
| FIX-QA kéo dài | UNI-2686 chưa merge | G2c vẫn chạy; các task khác chờ |

## 7. Task rủi ro nhất: BE-21

Yêu cầu khó nhất là **một Question Set nằm ở hai phần** (VS §4.2, câu 8–15). **D17 đã chọn phương án B.** Bảng dưới giữ lại để biết đã đổi gì lấy gì.

| | Phương án A: đúng chữ ghi chú | Phương án B: bản rút gọn (đã chọn) |
|---|---|---|
| Cách làm | Bốn `ExamPart`, nhóm 8–15 liên kết qua Phần I và II | Một `ExamPart`, bốn nhóm, `displaySegments` chỉ để hiển thị |
| Vùng bị đụng | Đầu vào bộ giải, tổng theo mục, đối chiếu, thứ tự đề, bản chụp phiên bản, kiểm tra theo đơn vị | Dữ liệu mẫu; luật lưu của phân đoạn (BE-16); số lệnh của nhóm trộn dạng (`ExamCheckUnitSummary`, BE-21); trả phân đoạn và so số thật khi sinh thử (BE-20); vẽ tiêu đề phần (FE-8). Không đụng `exam/domain/solver/**` |
| Điểm | 8 (tổng đợt 165) | 3 (tổng đợt 160) |
| Rủi ro | Cao: chạm phần lõi đã nghiệm thu của 7 kỳ thi | Thấp |
| Đáp ứng ghi chú | Đủ | Đủ bảng §4.2: bốn dòng, dải câu, số lệnh, chọn bài đọc một lần, hai câu Đúng/Sai đứng trước sáu câu chọn một, hiển thị ở Phần I và II, báo lỗi tại dòng 8–15 |
| Điểm khác | Tính được tổng theo Phần I, II từ cấu trúc | Tổng theo phần là số khai báo trong `displaySegments`: lúc lưu chỉ kiểm tổng cả bài, lúc sinh thử mới so từng phân đoạn với đề thật |

Phương án B đáp ứng VS §4.2 theo từng chặng ở spec mục 5.2. Hai câu Đúng/Sai đứng trước là điều kiện của nguồn câu (BE-17), không phải việc của phân đoạn. Việc đợt 3 phải làm với `displaySegments` ghi ở spec mục 7.

## 8. Điểm đã quyết và điểm còn chờ Advisor

Đã quyết:

1. **Điểm của BE-16, BE-20, FE-6:** giữ 160 (D18).
2. **Ảnh trong nội dung:** H48 chỉ kiểm tệp đính kèm, chưa kiểm `IMAGE` ở 2B (D18).
3. **`excluded_parts`:** bỏ cột. Vòng rà thứ hai đã lần theo mã và xác nhận nhóm đã lược không lọt vào tổng câu, tổng điểm, số lệnh, bộ giải, đối chiếu và sinh thử; V-SAT Ngữ văn lưu phần Viết thành phần đã lược như H-SCA (spec H49).
4. **Q-38:** theo đề xuất (D19).

Còn chờ Advisor:

1. **Hiệu lực của kết quả kiểm tra đã lưu.** `ExamCheckValidity.currentPolicy` ghi cờ `fixedCounts` của mẫu vào chính sách của lần kiểm tra, để đổi cờ thì kết quả cũ hết hiệu lực. Hai cờ mới chưa có trong chính sách này và chưa task nào sở hữu tệp đó. Ở 2B cờ được đặt cùng lúc tạo mẫu nên chưa có bài nào kiểm tra trước khi có cờ. Đề xuất chưa làm; nếu sau này đổi cờ của một mẫu đã dùng thì thêm vào chính sách trong cùng migration đó.
2. **Phạm vi của bộ nạp audio.** Bộ nạp của BE-19 chạy ở cả hồ sơ `test` để mọi bài test có tệp audio mẫu. Cách khác là giữ hồ sơ `dev` và để từng lớp test tự nạp; cách này ít đụng ngữ cảnh test hơn nhưng mọi test kiểm tra bài có audio phải nhớ gọi. Đề xuất giữ như bản này.
