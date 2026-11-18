# Đợt 2B, phần bổ sung: cấu trúc THPT và V-ACT

Ngày: 2026-11-18. Trạng thái: đã duyệt ngày 2026-11-18 sau hai vòng rà độc lập. Issue: UNI-2671. Căn cứ: quyết định D5 (2026-11-18, "Làm trọn trong 2B").

Tài liệu này là **phần sửa** của `docs/specs/2026-11-17-pbi04-dot-2b.md` (gọi tắt **2B**). Mục nào của 2B không nhắc ở đây thì giữ nguyên.

**Nguồn:** ghi chú BA `uninote-pbi04-cau-truc-thpt.md` (bản 15:09, **T**) và `uninote-pbi04-cau-truc-vact.md` (bản 16:20, **V**). Bảng đối chiếu từng dòng: `gap.md` (mã T-nn, V-nn). Quyết định D5–D13: `decisions/USER-DECISIONS.md`.

Những chỗ ghi **(suy luận)** là cách làm do agent đề xuất, ghi chú không nói trực tiếp.

## 1. Quyết định bị thay

| Quyết định cũ | Thay đổi |
|---|---|
| H16, Q-23 (giữ nguyên mẫu V-ACT, THPTQG) | **Bỏ.** Mẫu viết lại theo mục 4 và 5. Phần K2 của H16 (định hướng Tin chọn ở màn chuẩn bị) vẫn giữ |
| 2B mục 2, dòng "V-ACT, THPTQG theo mô hình mới (H16)" | Thay bằng phạm vi của tài liệu này |
| 2B mục 5.1, dòng V-ACT `FULL`, THPTQG `SUBJECT` | V-ACT `FULL`, `PART`, `SUBJECT`; THPTQG `SUBJECT`, `PART` (D8). Cả hai có bài ôn |
| 2B mục 10, dòng V-ACT, THPTQG | Thay bằng mục 4 và 5 |
| K1 (đề có tự luận vẫn được mở) | **Ngoại lệ hẹp, đã chốt (D11):** THPTQG Ngữ văn lưu nháp được, không công bố được, không vào bộ đề bán (T §6.2). K1 giữ nguyên cho mọi mẫu khác; BCA có tự luận vẫn bán |

Giữ nguyên: K2, K3, K6 (đã chốt ở D13, xem H33), K10, H2, H6, H7, H8, H20, H22.

## 2. Quyết định mới của agent

| # | Nội dung | Quyết định |
|---|---|---|
| H23 | Phần của THPTQG | Mỗi mẫu môn tách theo T §4 thành 2 hoặc 3 `ExamPart` có `maxPoints`. Mẫu đổi `timing` từ `PER_PART` sang `SHARED`, phần không mang thời lượng riêng (như `SP2E_MATH_2025`). Tiếng Anh, Ngữ văn giữ một phần và `timing` hiện có; Tin học giữ nguyên |
| H24 | Tiểu mục V-ACT | Năm tiểu mục là năm `ExamSection`. Logic và Suy luận là hai mục cùng môn `SCIENTIFIC_THINKING` trong một phần, được giải chung trong một đơn vị kiểm tra theo H2 (suy luận). `ExamStructureValidator` và `ExamCheckUnitPlanner` hiện không cấm hai mục cùng môn trong một phần; bộ giải được kiểm sớm bằng test ở BE-13. Advisor đã chấp nhận; nếu bộ giải không nhận thì lùi về một mục hai nhóm và báo BA |
| H25 | Phạm vi riêng của V-ACT | Phần, tiểu mục không phải môn dùng `PART`; môn dùng `SUBJECT` (mục 5.3) (suy luận, D10) |
| H26 | Chặn công bố Ngữ văn (D11) | Dùng cờ `exam_template.publishable`, không dùng `omitted`. `ExamStructureValidator` chỉ cho `omitted` ở nhóm `ESSAY` (nhóm Đọc hiểu là `QUESTION_SET`), và `ExamCheckRules` bỏ qua `PART_EMPTY` khi mọi nhóm của phần đều `omitted`, nên đánh `omitted` thì đề 0 câu vẫn qua được kiểm tra |
| H27 | Khóa số câu bài FULL | Cờ `exam_template.fixed_counts`. Web khóa ô nhập ở bước 1 và bước 2; kiểm tra báo `STRUCTURE_COUNT_LOCKED`. Không chặn lưu nháp. Được đổi phân bổ bên trong tiểu mục (V §3.1) |
| H28 | Chùm cỡ linh hoạt | Nhóm `QUESTION_SET` có `questionCount`, để trống cả `setCount` và `setSize`: bộ giải chọn các chùm nguyên có tổng đúng `questionCount`. Không thêm trường. `ExamStructureValidator` đã cho để trống cả hai. `ExamCheckRules` hiện báo `SET_SIZE_MISMATCH` cho nhóm chùm thiếu hai số này (hai chỗ), nên phải miễn cho trường hợp này |
| H29 | Mọi câu con thuộc chủ đề | Trường `KnowledgeScope.everyChildInScope`, mặc định `false`, bật sẵn trong `VACT_REVIEW`. Các kỳ thi khác không đổi hành vi |
| H30 | Nhập theo tỷ lệ | `count` vẫn là số được giải. `TopicQuota.percent` chỉ lưu số gốc để quy đổi lại; dòng nhập theo số câu thì để trống. Dòng **Phần còn lại** do web tính, không lưu |
| H31 | Dòng chờ mapping (D12) | `nodeIds` rỗng được lưu nháp. `REQUIRED` + rỗng: lỗi `TOPIC_QUOTA_MAPPING_MISSING`, chặn kiểm tra đạt, sinh thử và công bố. `REFERENCE` + rỗng: không lỗi, bảng đối chiếu ghi "Chưa có dữ liệu đối chiếu". Quản trị viên gắn MAPID bằng tay vào dòng đã có; không có bảng đối chiếu mã đối tác |
| H32 | Thang điểm V-ACT (D7) | 2B **không đổi số** (10 điểm mỗi câu, 1.200, `LINEAR`), vì ghi chú không đưa thang thay thế. Mẫu phạm vi riêng lấy điểm tối đa bằng số câu × 10. Cách hiển thị kết quả để đợt 3 quyết |
| H33 | Nhãn "ít nhất" (D13) | K6 giữ cho mọi kỳ thi, kể cả V-ACT và THPTQG. Cột Số câu của chỉ tiêu chủ đề ghi "ít nhất", cả ở bảng hai cột Số câu và Tỷ lệ. Bộ giải không thêm ràng buộc "đúng số câu". Lệch với T §3.3 và V §5.3, phải báo BA (mục 9) |
| H34 | Dải câu | `positionRange` vẫn do hệ thống tính và đánh số lại trong từng phần (2B mục 3.3); mẫu không lưu. THPTQG Tiếng Anh chỉ có một phần nên ra đúng dải 1–40 của T §6.1. V-ACT FULL: dải 1–120 của V §2 suy ra từ thứ tự phần, mục; màn không hiện số chạy suốt bài (mục 7) |
| H35 | Thời lượng bài riêng | Mẫu phạm vi riêng của V-ACT có `timing = SHARED`, `duration_minutes` trống. Luật hiện có (`requireSharedDuration`) đòi thời lượng ngay khi tạo bài: thiếu thì từ chối với `EXAM_DURATION_REQUIRED`. Bài theo phần THPTQG của các môn đã tách phần cũng vậy. Như thế quản trị viên luôn xác nhận thời lượng trước khi công bố (V §1, T §3.1) |

## 3. Yêu cầu mới

| R | Yêu cầu | Nguồn | Task |
|---|---|---|---|
| R-106 | Mẫu THPTQG tách phần, điểm từng phần đúng bảng T §4 | T-16, T-36 | BE-13 |
| R-107 | Nhóm Đúng/Sai đòi 4 mệnh đề; dòng nhóm hiện "4 câu · 16 ý · tối đa 4 điểm" | T-18, T-19 | BE-13, BE-14, FE-3a |
| R-108 | THPTQG Tiếng Anh 6 nhóm có thứ tự, dải câu, điểm nhóm; thiếu bài đọc đủ cỡ thì báo thiếu chùm | T-24, T-25, T-26 | BE-13, BE-14 |
| R-109 | THPTQG Ngữ văn lưu được, không công bố được; hiện thông báo ở bước 1 | T-28, T-29, T-30 | BE-11, BE-13, BE-15, FE-5 |
| R-110 | Có `THPTQG_REVIEW` | T-03 | BE-13 |
| R-111 | THPTQG bài theo phần: chọn môn, chọn phần, tự nhập thời lượng | T-12 | BE-13, FE-5 |
| R-112 | Sinh thử hiện điểm tối đa của từng câu. Điểm đạt được của lượt làm thử thuộc đợt 3 (H17) | T-34 | FE-4 |
| R-113 | V-ACT FULL có 5 tiểu mục, thứ tự và số câu theo V §2 | V-07 | BE-13 |
| R-114 | Bảng tham khảo Tiếng Việt, Tiếng Anh, Toán, Tư duy khoa học ở chế độ Chỉ đối chiếu | V-11, V-23, V-24, V-27, V-33, V-34, V-42 | BE-13 |
| R-115 | Tiếng Anh V-ACT 5 khối có thứ tự, mỗi khối một nhãn dạng câu | V-26, V-28 | BE-13, BE-14 |
| R-116 | Toán V-ACT 12 câu đơn + 18 câu con, chùm cỡ linh hoạt | V-31, V-32 | BE-12, BE-13, BE-14, FE-3a |
| R-117 | Logic 4×3, Suy luận 6×3, nguồn tách bằng `competencyCodes`; có nhãn loại dữ kiện để tách dòng | V-41, V-43, V-44 | BE-13, BE-14 |
| R-118 | Bài FULL khóa số câu từng tiểu mục | V-10 | BE-11, BE-13, BE-15, FE-3b, FE-5 |
| R-119 | V-ACT bài theo phần, môn, tiểu mục; phải nhập thời lượng khi tạo | V-02, V-04 | BE-13, FE-5 |
| R-120 | `VACT_REVIEW`; bài giới hạn chủ đề đòi mọi câu con thuộc chủ đề | V-03, V-37 | BE-11, BE-12, BE-13, FE-3a |
| R-121 | Hai cột Số câu và Tỷ lệ, một chữ số thập phân, Phần còn lại, đổi tổng thì quy đổi lại | V-13, V-15, V-17, V-19 | BE-11, FE-3b |
| R-122 | Trạng thái Chờ mapping; dòng bắt buộc chưa gắn mã thì chặn kiểm tra và sinh thử | V-39, V-40 | BE-15, FE-3b, FE-4 |
| R-123 | Tiến độ "Đã phân bổ 27/30 · Còn thiếu 3 câu", "Đủ nguồn theo ma trận"; tỷ lệ trong phần và toàn bài; mẫu số theo cấp đang chỉnh | V-09, V-21, V-22 | FE-3b, FE-5 |
| R-124 | Sinh thử có cột Yêu cầu / Thực tế / Chênh lệch | V-50 | FE-4 |
| R-125 | Dữ liệu câu hỏi mẫu và nhãn đủ để mọi mẫu mới kiểm tra đạt | T-26, V-28, V-44, gap mục 4 | BE-14 |
| R-126 | Cột Số câu của chỉ tiêu chủ đề ghi "ít nhất" ở bài V-ACT và THPTQG (H33) | D13 | FE-3b |

## 4. Mẫu THPTQG

Mã mẫu giữ nguyên (`THPTQG_*_2025`). Số câu, thời lượng, thang 10 không đổi.

| Môn | Phần I | Phần II | Phần III | Tổng | Thời lượng |
|---|---|---|---|---|---|
| Toán | 12 trắc nghiệm × 0,25 = 3 | 4 Đúng/Sai × 1 = 4 | 6 trả lời ngắn × 0,5 = 3 | 22 câu, 10 điểm | 90 phút |
| Lý, Hóa, Sinh, Địa | 18 × 0,25 = 4,5 | 4 × 1 = 4 | 6 × 0,25 = 1,5 | 28 câu, 10 điểm | 50 phút |
| Sử, GDKTPL, CN Công nghiệp, CN Nông nghiệp | 24 × 0,25 = 6 | 4 × 1 = 4 | Không có | 28 câu, 10 điểm | 50 phút |

Nhóm Đúng/Sai của chín môn trên có `itemShape.statements = 4` và `expectedScoring` như mẫu SPT (bảng 0,1 / 0,25 / 0,5 / 1 theo số ý đúng).

**Tiếng Anh** (một phần, một mục, 40 câu, 50 phút, mọi câu 0,25 điểm). Cột Dải câu là `positionRange` hệ thống tính từ thứ tự và số câu của nhóm (H34), mẫu không lưu:

| Thứ tự | Nhóm | Dạng | Dải câu | Nhãn | Điểm |
|---|---|---|---|---|---|
| 1 | Reading 1 – Đọc điền | Chùm 1×6 | 1–6 | `ANH_DOC_DIEN` | 1,5 |
| 2 | Reading 2 – Đọc điền | Chùm 1×6 | 7–12 | `ANH_DOC_DIEN` | 1,5 |
| 3 | Sắp xếp hội thoại hoặc văn bản | 5 câu đơn | 13–17 | `ANH_SAP_XEP_HOI_THOAI`, `ANH_SAP_XEP_DOAN` | 1,25 |
| 4 | Reading 3 – Đọc điền câu | Chùm 1×5 | 18–22 | `ANH_DOC_DIEN_CAU` | 1,25 |
| 5 | Reading 4 – Đọc hiểu | Chùm 1×8 | 23–30 | `ANH_DOC_HIEU` | 2 |
| 6 | Reading – Đọc hiểu | Chùm 1×10 | 31–40 | `ANH_DOC_HIEU` | 2,5 |

Mọi chùm có `childAnswerType = SINGLE_CHOICE` (T §6.1). Sáu nhãn trên đã có trong danh mục mẫu. Nhóm 1 và 2 lấy hai ngữ liệu khác nhau vì mỗi câu chỉ xuất hiện một lần trong đề.

**Ngữ văn:** giữ cấu trúc hiện có (chùm đọc hiểu 1×5 × 0,8 = 4 điểm; viết 2 + 4 điểm; 7 câu, 120 phút), `publishable = false`. Đọc hiểu thêm phân bố độ khó `REFERENCE` 2 NB, 2 TH, 1 VD.

**Tin học:** giữ nguyên (T-08): 30 vị trí, một học viên làm 28 câu, 10 điểm, 50 phút.

**Bài theo phần (D8):** dùng lại `selectSubjectPart` (`P1`, `P2`, `P3`) như SP2E, SPT. Hồ sơ THPTQG đã có `template_by = SUBJECT`, nên chỉ cần thêm `PART` vào `scopes` và sửa điều kiện ở `ExamService.assertTemplateMatches` (dòng 1182) để THPTQG được dùng mẫu môn cho bài `PART`. Bài lấy thang điểm của phần (`RAW`, ví dụ Địa lí Phần III 1,5 điểm) và thời lượng do quản trị viên nhập (H35).

**`THPTQG_REVIEW`:** khung như `<KỲ>_REVIEW` của BE-6 (`V202611180456`).

## 5. Mẫu V-ACT

### 5.1 `VACT_FULL`

Giữ 120 câu, 150 phút, `SHARED`, thang 1.200 (phần 600 / 300 / 300), mọi câu 10 điểm; `fixed_counts = true`. Mọi bảng dưới đây ở chế độ `REFERENCE`. Chùm nào cũng có `childAnswerType = SINGLE_CHOICE` (V §5.1 cho Toán; các mục khác là suy luận).

| Phần | Mục (`subjectCode`) | Dải câu trong bài | Số câu | `competencyCodes` của mục | Nhóm | Bảng tham khảo ở cấp mục |
|---|---|---|---|---|---|---|
| 1 | Tiếng Việt (`VIETNAMESE`) | 1–30 | 30 | `VACT-NN-TV` | 30 câu đơn | `topicQuotas` mục 5.2; độ khó 6/17/7 |
| 1 | Tiếng Anh (`ANH`) | 31–60 | 30 | `VACT-NN-TA` | 5 nhóm, bảng dưới | Độ khó ở từng nhóm |
| 2 | Toán học (`TOAN`) | 61–90 | 30 | `VACT-TOAN` | 12 câu đơn; chùm 18 câu con cỡ linh hoạt (H28) | Lớp G10 12, G11 9, G12 8, `GRADE_MIXED` 1; độ khó 4/10/16 |
| 3 | Logic và phân tích số liệu (`SCIENTIFIC_THINKING`) | 91–102 | 12 | `VACT-TD-LOGIC` | Chùm 4×3 | Độ khó 1/4/7 |
| 3 | Suy luận khoa học (`SCIENTIFIC_THINKING`) | 103–120 | 18 | `VACT-TD-SL` | Chùm 6×3 | Độ khó 4/9/5 |

Tiếng Anh (tổng độ khó 6/16/8):

| Thứ tự | Nhóm | Dạng | Nhãn (D9) | NB | TH | VD |
|---|---|---|---|---|---|---|
| 1 | Điền khuyết | 5 câu đơn | `ANH_HOAN_THANH_CAU` | 3 | 2 | 0 |
| 2 | Phát hiện lỗi sai | 5 câu đơn | `ANH_PHAT_HIEN_LOI` | 0 | 5 | 0 |
| 3 | Diễn đạt tương đương | 5 câu đơn | `ANH_DIEN_DAT_TUONG_DUONG` | 0 | 1 | 4 |
| 4 | Đọc hiểu – bài đọc 1 | Chùm 1×7 | `ANH_DOC_HIEU` | 1 | 4 | 2 |
| 5 | Đọc hiểu – bài đọc 2 | Chùm 1×8 | `ANH_DOC_HIEU` | 2 | 4 | 2 |

Nhóm Logic và Suy luận mặc định không gắn nhãn (V §6.2, "chỉ cần đủ số chùm"). Bốn nhãn `TD_SAP_XEP_DIEU_KIEN`, `TD_XEP_HANG`, `TD_BANG_SO_LIEU`, `TD_BIEU_DO` (D9) có trong danh mục để quản trị viên tách dòng theo loại dữ kiện; lĩnh vực tư liệu của Suy luận tách bằng MAPID.

### 5.2 Chỉ tiêu tham khảo Tiếng Việt

Tám `TopicQuota` `REFERENCE`, `nodeIds` rỗng (chờ mapping), mỗi dòng một cặp mạch và mức (`difficultyCodes`). Vừa đúng giới hạn 8 của H20.

| Mạch | NB | TH | VD | Cộng |
|---|---|---|---|---|
| Chính tả, từ vựng và ngữ nghĩa | 5 | 2 | | 7 |
| Thể loại, thi pháp và lí luận văn học | 1 | 7 | 4 | 12 |
| Ngữ pháp, liên kết và logic diễn đạt | | 3 | | 3 |
| Đọc hiểu văn bản, nội dung và lập luận | | 5 | 3 | 8 |
| **Cộng** | **6** | **17** | **7** | **30** |

### 5.3 Phạm vi riêng (suy luận, H25, D10)

Mọi mẫu dưới đây có `timing = SHARED`, thời lượng trống (H35), `fixed_counts = false` nên số câu sửa được. Nội dung từng mẫu chép từ mục tương ứng của `VACT_FULL`.

| Mã mẫu | Phạm vi | `partCode` hoặc môn | Số câu | Điểm tối đa |
|---|---|---|---|---|
| `VACT_PART_LANGUAGE` | `PART` | `LANGUAGE` | 60 | 600 |
| `VACT_PART_THINKING` | `PART` | `THINKING` | 30 | 300 |
| `VACT_PART_LOGIC` | `PART` | `LOGIC` | 12 | 120 |
| `VACT_PART_REASONING` | `PART` | `REASONING` | 18 | 180 |
| `VACT_SUBJECT_VIETNAMESE` | `SUBJECT` | `VIETNAMESE` | 30 | 300 |
| `VACT_SUBJECT_ANH` | `SUBJECT` | `ANH` | 30 | 300 |
| `VACT_SUBJECT_TOAN` | `SUBJECT` | `TOAN` | 30 | 300 |
| `VACT_REVIEW` | `SUBJECT`, `build_mode = REVIEW` | Chọn môn khi tạo | Quản trị viên nhập | Không đặt |

Phần Toán học chỉ có ở dạng môn (`VACT_SUBJECT_TOAN`), không có mẫu `PART` riêng.

Bước 1 chỉ chào môn có mẫu theo môn, nên `VACT_REVIEW` chọn được Tiếng Việt, Tiếng Anh, Toán học. Bài ôn chủ đề của Tư duy khoa học, Logic, Suy luận dùng mẫu `PART` tương ứng: giới hạn MAPID ở mục, sửa số câu, bật ô "mọi câu con thuộc chủ đề" (suy luận; Advisor đã chấp nhận, xin BA xác nhận cùng Q-31).

## 6. Mô hình dữ liệu và API

Mọi thay đổi đều là **thêm**, không đổi nghĩa trường cũ.

| Vị trí | Thay đổi |
|---|---|
| `exam.exam_template` | Thêm `publishable boolean NOT NULL DEFAULT true`, `fixed_counts boolean NOT NULL DEFAULT false` |
| `ExamTemplateResponse` | Thêm `publishable`, `fixedCounts` |
| `TopicQuota` | Thêm `percent` (`BigDecimal`, tùy chọn, 0–100) |
| `KnowledgeScope` | Thêm `everyChildInScope` (`Boolean`, tùy chọn) |
| `QuestionSourcePort`, API đếm câu | Thêm tham số `everyChildInScope` |
| `QuestionBlock` | Không thêm trường; đổi luật theo H28 |
| `exam_kind_profile.scopes` | VACT `{FULL,PART,SUBJECT}`; THPTQG `{SUBJECT,PART}` |
| `QUESTION_TAG` | Thêm 6 nhãn của D9 |

Không thêm endpoint. `TopicQuota` (record) giữ thêm hàm dựng 9 tham số cũ; `KnowledgeScope` (lớp, không phải record) giữ hàm dựng 2 tham số cũ và đưa trường mới vào `equals`, `hashCode`, để lane đang chạy không vỡ biên dịch và để việc đổi cờ làm đơn vị kiểm tra cũ đi.

**Mã kiểm tra mới** (mã của kết quả kiểm tra, không phải mã lỗi HTTP)

| Mã | Mức | Điều kiện |
|---|---|---|
| `TEMPLATE_NOT_PUBLISHABLE` | ERROR | Mẫu chuẩn theo môn (`STANDARD`, mặc định) của cùng kỳ thi và môn có `publishable = false`. Áp cho bài theo môn, theo phần và bài ôn của môn đó. Kết quả kiểm tra không đạt nên `ExamPublishService` từ chối công bố |
| `STRUCTURE_COUNT_LOCKED` | ERROR | Mẫu đã nạp của bài có `fixed_counts = true` và số câu của phần hoặc mục khác mẫu (so theo id phần, mục của mẫu) |
| `TOPIC_QUOTA_MAPPING_MISSING` | ERROR | Chỉ tiêu `REQUIRED` có `nodeIds` rỗng. Sinh thử trả `FAILED` kèm mã này |

Chùm cỡ linh hoạt không bị báo `SET_SIZE_MISMATCH`. Chùm không ghép được đúng tổng thì báo bằng mã thiếu nguồn hiện có (`SET_SHORTAGE`).

## 7. Ngoài phạm vi 2B và ảnh hưởng tới đợt 3

**Không làm trong 2B**

| Mục | Lý do | Nơi xử lý |
|---|---|---|
| Bảng đối chiếu mã đối tác, "bộ nguồn" (V §5.4, V-38) | D12: BA chưa định nghĩa "bộ nguồn" và định dạng tệp | Việc sau, khi BA bổ sung |
| "Đúng số câu" theo mạch (T §3.3, V §5.3) | D13: K6 giữ "ít nhất" | Báo BA (mục 9) |
| Hiện dải câu chạy suốt bài 61–90, 91–102, 103–120 (V §2) | `positionRange` đánh số trong từng phần (H34) | Báo BA; nếu cần thì web cộng dồn ở bảng tổng quan |
| Phân bố độ khó bên trong một dòng MAPID (V §3.4, ví dụ dòng 5 câu) | `TopicQuota` chỉ có bộ lọc mức; tách thành mỗi dòng một cặp mạch và mức như mục 5.2 (suy luận) | Báo BA |
| Phép cộng tĩnh theo cột của các mạch so với độ khó toàn tiểu mục (V §4.1) | Không thêm luật mới. Khi cả hai là bắt buộc và tổng một mức của các dòng mạch vượt số của tiểu mục, bộ giải không ghép được và kiểm tra báo `SUBJECT_QUOTA_INFEASIBLE` ở tiểu mục (đọc từ `ExamCheckSolverIssues`, chưa chạy; BE-15 có ca test). Mã này không nêu tên dòng mạch. Tổng nhỏ hơn thì vẫn đạt vì chỉ tiêu mạch là "ít nhất" (D13) | BE-15 dòng 10, 11; báo BA |
| Chấm thử trên web và app, điểm đạt được từng câu (T §7, §8) | 2B H17: làm thử như học viên thuộc đợt 3; 2B không có mã app | Đợt 3 (FA-4) |

**Ảnh hưởng tới đợt 3**

| Mục | Ảnh hưởng |
|---|---|
| LT F15 (V-46, V-47) | D7: 2B giữ số; đợt 3 quyết cách hiển thị (V §8 chỉ công bố số đúng, tỷ lệ đúng). Thêm phần xem riêng 12 và 18 câu của Tư duy khoa học |
| LT, phần tuần tự (L7) | Rà lại cho THPTQG sau khi tách phần: T không nói có khóa quay lại giữa các phần (suy luận) |
| LT kết quả (T-35) | Điểm theo phần và toàn bài, lưu phiên bản chấm; bài theo phần hiện thang đã công bố |
| LT sinh lượt (T §8, V-53) | Không ghép được đề lúc bắt đầu thì không mở lượt, không trừ quyền; khôi phục đúng đề đã giao dù mapping đổi |
| LT màn chuẩn bị (T-21) | Chọn định hướng Tin học (K2) |
| TM bộ đề (T-04, T-31) | Bộ đề thuộc đợt 3 (K3). Không cho chọn bài THPTQG Ngữ văn (D11); bài không công bố được nên tự loại (suy luận) |
| Kết quả theo kiến thức (V-48) | Ghi mức bao phủ MAPID |

## 8. Câu hỏi đã chốt

Không còn câu nào chờ chủ dự án. Q-29, Q-30, Q-31 là số tạm của Advisor, chờ BA xác nhận.

| # | Câu hỏi | Quyết định | Người chốt |
|---|---|---|---|
| Q-25 | K1 cho mở đề tự luận chấm tay, T §6.2 không cho xuất bản Ngữ văn. Theo bên nào? | **D11:** theo T §6.2 cho riêng THPTQG Ngữ văn: lưu nháp được, không công bố, không vào bộ đề bán. K1 giữ cho mọi mẫu khác | Chủ dự án |
| Q-26 | V §8 chỉ công bố số đúng và tỷ lệ đúng, LT F15 hiện "Điểm luyện tập" trên 1.200 | **D7:** 2B giữ số hiện có (10 điểm mỗi câu, 1.200, `LINEAR`). Đợt 3 quyết cách hiển thị | Advisor |
| Q-27 | Bảng đối chiếu mã đối tác (V §5.4) có làm trong 2B không? | **D12:** không làm. Quản trị viên gắn MAPID tay vào dòng Chờ mapping. Trạng thái Chờ mapping và việc chặn dòng bắt buộc chưa gắn mã vẫn làm trong 2B | Chủ dự án |
| Q-28 | K6 là "ít nhất", T §3.3 và V §5.3 ghi "đúng số câu" theo mạch | **D13:** giữ K6 cho mọi kỳ thi; màn ghi "ít nhất"; bộ giải không đổi | Chủ dự án |
| Q-29 | T §1 không liệt kê bài theo phần, nhưng T §3.1, §4, §7 có mô tả | **D8 (tạm):** bật | Advisor, chờ BA |
| Q-30 | Mã nhãn cho Phát hiện lỗi sai, Diễn đạt tương đương, Sắp xếp điều kiện, Xếp hạng, Bảng số liệu, Biểu đồ | **D9 (tạm):** `ANH_PHAT_HIEN_LOI`, `ANH_DIEN_DAT_TUONG_DUONG`, `TD_SAP_XEP_DIEU_KIEN`, `TD_XEP_HANG`, `TD_BANG_SO_LIEU`, `TD_BIEU_DO`. Điền khuyết dùng `ANH_HOAN_THANH_CAU` | Advisor, chờ BA |
| Q-31 | Cách chia phạm vi riêng của V-ACT ở mục 5.3 | **D10 (tạm):** theo mục 5.3 | Advisor, chờ BA |

## 9. Việc phải báo BA

Bổ sung cho 2B mục 18. QA đưa danh sách này vào báo cáo cuối đợt.

| # | Nội dung | Căn cứ |
|---|---|---|
| 1 | Chỉ tiêu theo mạch kiến thức là "ít nhất", không phải "đúng số câu" như T §3.3 và V §5.3. Màn ghi rõ "ít nhất" | D13, H33 |
| 2 | Chưa có bảng đối chiếu mã đối tác của V §5.4; quản trị viên gắn MAPID tay. Cần BA định nghĩa "bộ nguồn" và định dạng tệp | D12 |
| 3 | Kết quả V-ACT: 2B giữ 10 điểm mỗi câu, thang 1.200; V §8 chỉ công bố số đúng và tỷ lệ đúng | D7 |
| 4 | Xin xác nhận ba số tạm: bật bài theo phần THPTQG (Q-29), sáu mã nhãn (Q-30), cách chia phạm vi riêng V-ACT kể cả cách ôn chủ đề của Tư duy khoa học (Q-31) | D8, D9, D10 |
| 5 | Dải câu V-ACT hiện theo từng phần, không chạy suốt 1–120 | H34 |
| 6 | Độ khó bên trong một dòng MAPID phải khai thành nhiều dòng, mỗi dòng một mức | Mục 7 |
| 7 | THPTQG Ngữ văn theo đúng T §6.2; K1 vẫn cho bán đề có tự luận ở kỳ thi khác (đã báo ở 2B mục 18) | D11 |
| 8 | Tổng theo mức của các mạch lệch với độ khó toàn tiểu mục (V §4.1) chỉ bị chặn khi không ghép được đề, bằng mã chung `SUBJECT_QUOTA_INFEASIBLE`, không có thông báo riêng nêu dòng mạch | Mục 7 |
