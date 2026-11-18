# Đề test giả định cho 7 kỳ thi

Ngày: 2026-11-17. Trạng thái: giả định, chờ chuyên gia ra đề.

Tài liệu này cho số liệu tạm để dựng mẫu cấu trúc, bộ câu hỏi mẫu và dữ liệu E2E trong lúc chuyên gia chưa trả lời các điểm ở `docs/specs/2026-11-16-be-pbi-03-04.md` mục 12 ("Chuyên gia ra đề"). Mọi số ghi "giả định" là do đội dự án tự đặt, không phải số của khách hàng. Khi chuyên gia trả lời, thay số ở đây và trong migration mẫu, rồi chạy lại kiểm tra cấu hình của bộ đề test.

Quy ước đọc bảng: cột hoặc ô có chữ "giả định" là số tự đặt. Số không ghi "giả định" lấy từ nguồn ghi bên cạnh (UniNote PBI-04 mục 5.1, PBI-10, mẫu `V202611161801__seed_exam_templates.sql`).

## 1. Quy ước chung đã chốt

Các điểm này đã có quyết định, áp cho mọi đề test:

- Câu con của chùm Toán HSA là Một đáp án (`SINGLE_CHOICE`).
- Nhiều đáp án mặc định `ALL_OR_NOTHING`.
- Các phần chạy theo thứ tự. Không có quy tắc chuyển phần.
- Câu và phương án luôn được đảo khi sinh đề. Không có cờ đảo trên từng câu. Thứ tự nhóm vẫn theo mẫu (`groupOrder`, D24).
- HSA Khoa học: học viên chọn 3 môn bất kỳ trong 5 môn. Chia 17–17–16 theo B10.
- Chỉ tiêu cấp môn của 5 môn Khoa học khai theo tỉ lệ, quy ra 16 hoặc 17 câu bằng phần dư lớn nhất (PBI-04 mục 7.3).
- 6 kỳ thi khác HSA giữ cấu trúc cũ: mỗi khối một dạng câu, môn không có chỉ tiêu (D26).
- V-ACT tính điểm luyện tập `LINEAR` về 1.200 (D11).
- Đúng/Sai 1 điểm của SPT, SP2E dùng bảng điểm theo số ý đúng như THPTQG: 0,1 / 0,25 / 0,5 / 1 (giả định, spec mục 8).

## 2. Các điểm chờ chuyên gia và giá trị giả định

### 2.1 Nhóm lấy câu của từng môn HSA

**Chỗ trống.** PBI-04 mục 6.1 chỉ nêu ví dụ nhóm. PBI-10 cho tổng: Văn học – Ngôn ngữ 25 câu đơn + 5 chùm × 5; Tiếng Anh 35 câu đơn + 3 chùm × 5. Mẫu `HSA_FULL` để trống số câu các nhóm câu đơn, số chùm Toán, và mỗi môn Khoa học chỉ có một nhóm "lấy phần còn lại".

**Toán học và xử lí số liệu** (50 câu, 75 phút, 50 điểm; nguồn PBI-04 mục 6.1.2):

| Nhóm (`templateGroupCode`) | Loại | Số câu (giả định) | Dạng trả lời | Điểm/câu |
|---|---|---|---|---|
| `HSA_MATH_SINGLE` | câu đơn, `questionType = null` | 41 | theo chỉ tiêu môn: 26 Một đáp án + 15 Trả lời ngắn | 1 |
| `HSA_MATH_CONTEXT` | chùm `QUESTION_SET` | 3 chùm × 3 câu con = 9 | câu con `SINGLE_CHOICE` (đã chốt) | 1 |

Chỉ tiêu môn giữ theo nguồn: `SINGLE_CHOICE` 35, `SHORT_ANSWER` 15; lớp 10/11/12 = 3/20/27; NB/TH/VD/VDC = 2/21/20/7. Chùm trừ 9 vào chỉ tiêu 35, nên nhóm câu đơn lấy 26 + 15.

Lý do: 3 chùm × 3 câu đủ để có chùm trên đề mà không lấn chỉ tiêu Trả lời ngắn. Câu con chỉ có một dạng nên chùm không bao giờ làm vượt chỉ tiêu 15.

**Văn học – Ngôn ngữ** (50 câu, 60 phút, 50 điểm, `groupOrder = SHUFFLE`):

| Nhóm | Loại | Số câu (giả định trừ chỗ ghi nguồn) | Dạng | Điểm/câu |
|---|---|---|---|---|
| `HSA_VN_WORD_USAGE` Nhận diện lỗi dùng từ | câu đơn | 10 | `SINGLE_CHOICE` (mẫu khóa) | 1 |
| `HSA_VN_SINGLE` Câu đơn khác (nhóm mới, giả định) | câu đơn, không lọc dạng bài | 15 | `SINGLE_CHOICE` | 1 |
| `HSA_VN_PASSAGE` Đọc ngữ liệu | chùm | 5 × 5 = 25 (nguồn PBI-10 mục 3.2) | câu con `SINGLE_CHOICE` (giả định) | 1 |

Lý do: PBI-10 cho 25 câu đơn nhưng BA chỉ nêu một nhóm câu đơn. Tách 10 + 15 để test được hai nhóm câu đơn dùng chung nguồn và đảo cả nhóm. Không lọc dạng bài ở nhóm mới nên không cần thêm giá trị `TASK_TYPE`. Môn không khai chỉ tiêu lớp, độ khó (giả định; mẫu để `null`).

**Tiếng Anh** (50 câu, 60 phút, 50 điểm, `groupOrder = FIXED`):

| Thứ tự | Nhóm | Số câu (giả định trừ chỗ ghi nguồn) | Dạng | Điểm/câu |
|---|---|---|---|---|
| 1 | `HSA_EN_SYNONYM` Từ đồng nghĩa | 5 | `SINGLE_CHOICE` | 1 |
| 2 | `HSA_EN_ANTONYM` Từ trái nghĩa | 5 | `SINGLE_CHOICE` | 1 |
| 3 | `HSA_EN_DIALOGUE` Hoàn thành hội thoại | 5 | `SINGLE_CHOICE` | 1 |
| 4 | `HSA_EN_SENTENCE` Hoàn thành câu (nhóm mới, giả định, `TASK_TYPE` `SENTENCE_COMPLETION`) | 20 | `SINGLE_CHOICE` | 1 |
| 5 | `HSA_EN_READING` Đọc hiểu | 3 × 5 = 15 (nguồn PBI-10 mục 3.4) | câu con `SINGLE_CHOICE` | 1 |

Lý do: 35 câu đơn phải chia vào các nhóm. PBI-04 mục 6 nêu "hoàn thành câu" là một khối của phần Tiếng Anh, nên thêm nhóm này nhận phần lớn số câu. Nếu không muốn thêm giá trị danh mục, chia 12/12/11 cho ba nhóm cũ.

**Khoa học** (phương án `SUBJECT_CHOICE`, 5 môn chọn 3, 50 câu, 60 phút, 50 điểm). Cả 5 môn (Vật lí, Hóa học, Sinh học, Lịch sử, Địa lí) dùng cùng một khung giả định:

| Nhóm | Loại | Số câu (giả định) | Dạng | Điểm/câu |
|---|---|---|---|---|
| Chùm môn (`HSA_<MÔN>_CLUSTER`, nhóm mới) | chùm `QUESTION_SET` | 2 chùm × 3 câu con = 6 | câu con `SINGLE_CHOICE` | 1 |
| `HSA_<MÔN>_REMAINDER` lấy phần còn lại | câu đơn, `questionType = null` | 10 (môn nhận 16) hoặc 11 (môn nhận 17) | theo chỉ tiêu môn | 1 |

Chỉ tiêu cấp môn theo tỉ lệ (giả định) và số câu sau khi quy đổi:

| Chỉ tiêu | Tỉ lệ (giả định) | Môn nhận 16 | Môn nhận 17 |
|---|---|---|---|
| Dạng trả lời `SINGLE_CHOICE` / `SHORT_ANSWER` | 75% / 25% | 12 / 4 | 13 / 4 |
| Lớp 10 / 11 / 12 | 20% / 30% / 50% | 3 / 5 / 8 | 3 / 5 / 9 |
| NB / TH / VD / VDC | 20% / 40% / 30% / 10% | 3 / 6 / 5 / 2 | 3 / 7 / 5 / 2 |

Lý do: khung "2 chùm × 3 câu + nhóm còn lại 10 hoặc 11" lấy đúng ví dụ Lịch sử của PBI-10 mục 3.3 (spec B10). Dùng chung cho 5 môn để bộ câu mẫu sinh bằng một vòng lặp. Các tỉ lệ được chọn để phần dư lớn nhất cho kết quả khác nhau giữa 16 và 17, nhờ đó test được quy đổi (spec mục 10, "chia phần trăm bằng phần dư lớn nhất").

### 2.2 Điểm từng câu của SPT, SP2E

**Chỗ trống.** Spec mục 12. Mẫu `SPT_MATH_2025`, `SP2E_MATH_2025`, `SP2E_MATH_2026` có điểm tạm.

**Giả định:** giữ điểm tạm của mẫu, vì tổng đã đúng 10:

- SPT Toán 2025: 16 × 0,25 + 2 Đúng/Sai × 1 + 4 Trả lời ngắn × 0,25 + 3 Tự luận × 1 = 10.
- SP2E Toán 2025: 12 × 0,25 + 4 × 1 + 6 × 0,5 = 10.
- SP2E Toán 2026: 20 Một đáp án + 5 Nhiều đáp án + 1 chùm × 5 + 10 Trả lời ngắn, mỗi câu 0,25 = 10.

Môn khác: xem mục 2.4. Quy tắc chung của giả định: Một đáp án 0,25 hoặc 0,2; Đúng/Sai 1; phần dư dồn vào Trả lời ngắn và Tự luận để tổng đúng 10 (thang 10 điểm/môn, PBI-04 mục 5.1).

### 2.3 SP2E 2025: chia dạng câu và thời lượng

**Chỗ trống.** Mẫu `SP2E_MATH_2025` ghi "chia dạng câu 12–4–6 và điểm là tạm; thời lượng chưa có".

**Giả định:** 22 câu = 12 Một đáp án (0,25) + 4 Đúng/Sai (1) + 6 Trả lời ngắn (0,5); **90 phút**.

Lý do: 22 câu và cách chia này trùng cấu trúc THPTQG Toán 2025 (22 câu, 90 phút). SP2E Toán 2026 cũng 90 phút (PBI-04 mục 5.1). Ghi thêm: mẫu `SP2E_MATH_2026` đang để `duration_minutes = NULL` dù mục 5.1 ghi 90 phút; nên điền 90.

### 2.4 Cấu trúc 11 môn SPT và 8 môn SP2E còn lại

**Chỗ trống.** Mẫu có đủ số câu, thời lượng theo PBI-04 mục 5.1 nhưng `blocks` rỗng ("Cấu trúc nhóm chưa được BA cung cấp").

**Giả định:** mỗi môn một phần, chia khối như bảng. Số câu, thời lượng lấy từ mục 5.1; chia khối và điểm là giả định. Ký hiệu: MĐ Một đáp án, NĐ Nhiều đáp án, ĐS Đúng/Sai, TLN Trả lời ngắn, TL Tự luận, GĐ Ghép đôi, ĐK Điền khuyết, chùm a×b là a bộ, b câu con Một đáp án.

SPT 2025 (thang 10):

| Môn | Câu / phút (nguồn) | Khối và điểm/câu (giả định) |
|---|---|---|
| Ngữ văn | 12 / 90 | chùm Đọc hiểu 2×5 × 0,4; TL viết đoạn 1 × 2; TL viết bài 1 × 4 |
| Tiếng Anh | 33 / 60 | MĐ 20 × 0,25; chùm Đọc hiểu 2×6 × 0,25; TL viết 1 × 2 |
| Vật lí | 21 / 60 | MĐ 12 × 0,25; ĐS 4 × 1; TLN 4 × 0,5; TL 1 × 1 |
| Hóa học | 27 / 60 | MĐ 18 × 0,25; ĐS 4 × 1; TLN 5 × 0,3 |
| Sinh học | 22 / 60 | MĐ 12 × 0,25; ĐS 4 × 1; TLN 6 × 0,5 |
| Lịch sử | 25 / 60 | MĐ 20 × 0,25; ĐS 4 × 1; TL 1 × 1 |
| Địa lí | 20 / 60 | MĐ 8 × 0,25; ĐS 4 × 1; TLN 8 × 0,5 |
| GD kinh tế và pháp luật | 21 / 60 | MĐ 16 × 0,25; ĐS 4 × 1; TL 1 × 2 |
| Tin học | 25 / 60 | MĐ 20 × 0,25; ĐS 4 × 1; TL 1 × 1 |
| Công nghệ – công nghiệp | 24 / 60 | MĐ 16 × 0,25; ĐS 4 × 1; TLN 4 × 0,5 |
| Công nghệ – nông nghiệp | 24 / 60 | MĐ 16 × 0,25; ĐS 4 × 1; TLN 4 × 0,5 |

SP2E 2026 (thang 10):

| Môn | Câu / phút (nguồn) | Khối và điểm/câu (giả định) |
|---|---|---|
| Ngữ văn | 22 / 90 | MĐ 10 × 0,2; chùm Đọc hiểu 2×5 × 0,2; TL viết đoạn 1 × 2; TL viết bài 1 × 4 |
| Tiếng Anh | 50 / 60 | MĐ 30 × 0,2; chùm Đọc hiểu 2×5 × 0,2; ĐK 1 câu × 10 vị trí × 0,2 |
| Vật lí, Hóa học, Sinh học, Địa lí | 28 / 60 | MĐ 16 × 0,25; NĐ 4 × 0,25; GĐ 2 × 1; TLN 6 × 0,5 |
| Lịch sử, GD kinh tế và pháp luật | 32 / 60 | MĐ 24 × 0,25; NĐ 4 × 0,25; GĐ 2 × 1; TLN 2 × 0,5 |

Lý do: SPT lấy khung Toán 2025 (có Tự luận, PBI-04 mục 5.1) và khung THPTQG 2025 (MĐ 0,25, ĐS 1). SP2E lấy khung Toán 2026 (có Nhiều đáp án) và thêm Ghép đôi vì spec mục 8 ghi "Câu Ghép đôi của SP2E dùng dạng `MATCHING`". SP2E Tiếng Anh có Điền khuyết để bộ mẫu có ít nhất một bài dùng `CLOZE`. Mọi dòng cộng đúng số câu của mục 5.1 và đúng 10 điểm.

### 2.5 Đối chiếu THPTQG 12 môn

**Chỗ trống.** Đối chiếu 12 mẫu THPTQG với tài liệu dự án. Kết quả đối chiếu với PBI-04 mục 5.1 (cột "Tốt nghiệp THPT từ 2025"):

| Mẫu | Câu / phút | Khớp mục 5.1 | Tổng điểm |
|---|---|---|---|
| `THPTQG_MATH_2025` | 22 / 90: MĐ 12 × 0,25, ĐS 4 × 1, TLN 6 × 0,5 | có | 10 |
| `THPTQG_LITERATURE_2025` | 7 / 120: chùm 1×5 TL × 0,8, TL 1 × 2, TL 1 × 4 | có | 10 |
| `THPTQG_ENGLISH_2025` | 40 / 50: MĐ 40 × 0,25 | có | 10 |
| Vật lí, Hóa học, Sinh học, Địa lí | 28 / 50: MĐ 18 × 0,25, ĐS 4 × 1, TLN 6 × 0,25 | có | 10 |
| Lịch sử, GD KT&PL, CN công nghiệp, CN nông nghiệp | 28 / 50: MĐ 24 × 0,25, ĐS 4 × 1 | có | 10 |
| `THPTQG_INFORMATICS_2025` | 28 phải làm / 50, đồng hồ chung: MĐ 24, ĐS chung 2, ĐS nhánh CS hoặc ICT 2 | có | 10 |

Đủ 12 môn, số câu và thời lượng khớp. Một điểm lệch nhỏ: Tin học ghi "đề in 30 câu" nhưng phần II có `questionCountOnPaper = 4`; theo đề in thì phải là 6 (2 chung + 2 CS + 2 ICT). Giả định: sửa thành 6, tổng trên đề 30. Chờ chuyên gia xác nhận cả bảng.

### 2.6 BCA: số câu Trả lời ngắn trong 35 câu Toán

**Giả định:** 25 Một đáp án + 10 Trả lời ngắn, mỗi câu 1 điểm. Khối Toán của mẫu `BCA_FULL` tách làm hai khối.

Lý do: tỉ lệ gần với HSA Toán (35/15, tức 70/30; 35 × 30% ≈ 10). Tổng Phần 2 vẫn 35 + 10 + 15 = 60 (spec mục 8).

### 2.7 SPT: Trả lời ngắn chấm tự động hay chấm tay

**Giả định:** chấm tự động, khớp đúng sau chuẩn hóa (B7), như spec đang để. Câu Trả lời ngắn mẫu của SPT có danh sách đáp án và hướng dẫn nhập (dấu phẩy thập phân). Chỉ Tự luận vào hàng chờ chấm.

### 2.8 BCA Tiếng Anh 0,75 điểm mỗi câu

**Giả định:** soạn câu riêng 0,75 điểm cho BCA (khối đặt `requiredPointsPerQuestion = 0.75`, như mẫu). Không quy điểm theo phần.

Lý do: B5 đặt điểm ở câu hỏi; mẫu `BCA_FULL` đã lọc nguồn theo 0,75. Quy điểm theo phần cần thêm quy tắc chấm mới. Hệ quả: bộ mẫu cần nhóm câu Tiếng Anh 0,75 điểm riêng; khi có câu thật, sao chép câu 1 điểm rồi sửa điểm.

### 2.9 QDA, V-ACT: phần mẫu còn trống

Không có trong mục 12 nhưng đề test cần. Mẫu `QDA_FULL` có Phần 1, 2 với `blocks` rỗng. **Giả định:** Toán 1 khối MĐ 50 × 1; Văn 1 khối MĐ 50 × 1. Phần 3 giữ QĐ01–QĐ04 (2 môn × 25 MĐ) và QĐ05 Tiếng Anh 50 MĐ. Tổng 150. V-ACT giữ mẫu: 120 MĐ × 10 điểm, `LINEAR` về 1.200.

## 3. Bộ đề test

Mã bài viết hoa. Cột "Kết quả" là kết quả kiểm tra cấu hình mong đợi. Bài lỗi dựng từ bài hợp lệ cùng kỳ thi, chỉ đổi đúng một chỗ.

### 3.1 Bài hợp lệ

| Mã | Kỳ thi | Phạm vi | Mẫu | Dùng để kiểm | Kết quả |
|---|---|---|---|---|---|
| TEST-HSA-01 | HSA | Toàn bài | `HSA_FULL` + mục 2.1 | đủ 3 phần, 11 đường, 10 bộ ba Khoa học (12 trường hợp chia), xem trước với 3 môn | VALID |
| TEST-HSA-02 | HSA | Theo phần | `HSA_PART_MATH` | chỉ tiêu 35 + 15, lớp và độ khó hai chiều, chùm 3×3 | VALID |
| TEST-HSA-03 | HSA | Theo phần | `HSA_PART_LITERATURE` | hai nhóm câu đơn dùng chung nguồn, chùm 5×5, đảo nhóm | VALID |
| TEST-HSA-04 | HSA | Theo phần | `HSA_PART_SCIENCE` | `SUBJECT_CHOICE`, nhóm lấy phần còn lại, tỉ lệ ra 16/17; nhánh Tiếng Anh giữ thứ tự 5 nhóm | VALID |
| TEST-QDA-01 | QDA | Toàn bài | `QDA_FULL` + mục 2.9 | 5 mã phần 3, mã tổ hợp 2 môn × 25 | VALID |
| TEST-BCA-01 | BCA | Toàn bài | `BCA_FULL` + mục 2.6 | đồng hồ chung 180 phút, 100 điểm (25 + 60 + 15), Tiếng Anh 0,75, Tự luận 25 điểm, CA1–CA4 | VALID |
| TEST-VACT-01 | V-ACT | Toàn bài | `VACT_FULL` | 120 câu, `LINEAR` 1.200 | VALID |
| TEST-SPT-01 | SPT | Theo môn Toán | `SPT_MATH_2025` | 4 dạng, Tự luận chờ chấm, Trả lời ngắn chấm tự động | VALID |
| TEST-SP2E-01 | SP2E | Theo môn Toán | `SP2E_MATH_2025` | phiên bản 2025, 90 phút | VALID |
| TEST-SP2E-02 | SP2E | Theo môn Toán | `SP2E_MATH_2026`, `years_confirmed = true` | đổi phiên bản 2025 → 2026 có xác nhận, Nhiều đáp án, chùm 1×5 | VALID |
| TEST-SP2E-03 | SP2E | Theo môn Tiếng Anh | `SP2E_ANH_2026` + mục 2.4 | Điền khuyết đếm theo vị trí | VALID |
| TEST-THPTQG-01 | THPTQG | Theo môn Toán | `THPTQG_MATH_2025` | ĐS bảng điểm, E2E spec mục 10 | VALID |
| TEST-THPTQG-02 | THPTQG | Theo môn Ngữ văn | `THPTQG_LITERATURE_2025` | chùm có câu con Tự luận, 120 phút | VALID |
| TEST-THPTQG-03 | THPTQG | Theo môn Tin học | `THPTQG_INFORMATICS_2025` | nhánh CS/ICT, 30 trên đề, 28 phải làm | VALID |

### 3.2 Bài lỗi, mỗi bài một mã kiểm tra

| Mã | Dựng từ | Đổi gì | Mã mong đợi |
|---|---|---|---|
| TEST-ERR-01 | TEST-THPTQG-01 | khối MĐ có `matrix` nhưng chưa chọn cách áp dụng (chung hay riêng) | `MATRIX_INCOMPLETE` |
| TEST-ERR-02 | TEST-BCA-01 | Phần 2 đặt điểm tối đa 60, khối Tiếng Anh đổi 0,75 → 1 (tổng 65) | `PART_POINTS_MISMATCH` (kèm `POINTS_TOTAL_MISMATCH` 105 ≠ 100) |
| TEST-ERR-03 | TEST-HSA-03 | nhóm Đọc ngữ liệu thêm lọc nhãn `TEST-HIEM`; bộ mẫu chỉ có 2 bộ mang nhãn này, cần 5 | `SET_SHORTAGE` |
| TEST-ERR-04 | TEST-HSA-02 | nhóm câu đơn 40 thay 41 (môn 49 câu) | `SECTION_TOTAL_MISMATCH` |
| TEST-ERR-05 | TEST-HSA-02 | chỉ tiêu Trả lời ngắn 14 (tổng 49) | `ANSWER_QUOTA_TOTAL_MISMATCH` |
| TEST-ERR-06 | TEST-HSA-02 | phạm vi MAPID môn đặt vào nút `TEST-TOAN-HEP`; dưới nút có 10 Trả lời ngắn, cần 15 | `ANSWER_TYPE_SHORTAGE` (có thể kèm `SUBJECT_QUOTA_INFEASIBLE`) |
| TEST-ERR-07 | TEST-HSA-04 | Địa lí đặt phạm vi nút `TEST-DIA-HEP`, đủ đúng cho 16 câu (2 chùm, 6 MĐ, 4 TLN), thiếu cho 17 | INVALID; `selections` đúng 3 trường hợp Địa lí nhận 17 (1 môn tự nhiên + Lịch sử + Địa lí) |
| TEST-ERR-08 | TEST-HSA-04 | Vật lí bỏ nhóm lấy phần còn lại, thay bằng nhóm cố định 11 câu | `REMAINDER_GROUP_INVALID` |
| TEST-ERR-09 | TEST-HSA-04 | Hóa học khai chỉ tiêu dạng trả lời theo số câu (12 + 4) | `SUBJECT_QUOTA_COUNT_VARIES` |
| TEST-ERR-10 | TEST-HSA-04 | `countsByPick` = 17, 17, 17 | `SUBJECT_CHOICE_COUNTS_INVALID` |
| TEST-ERR-11 | TEST-THPTQG-01 | ô Lớp 12 × VDC của khối MĐ đặt 12 câu; bộ mẫu có tối đa 4 câu ô này | `CELL_SHORTAGE` |
| TEST-ERR-12 | TEST-THPTQG-01 | khối Trả lời ngắn bỏ điểm mỗi câu yêu cầu | `POINTS_TOTAL_MISMATCH` (thang cố định, B5) |
| TEST-ERR-13 | TEST-SP2E-02 | `years_confirmed = false` (cấu trúc 2026, ma trận 2025) | `YEARS_UNCONFIRMED` |

### 3.3 Nguồn tối thiểu để bài hợp lệ ra VALID

Quy tắc của plan BE-2a (`docs/plans/2026-11-16-be-pbi-03-04.md` dòng 311): mỗi ô, mỗi mức điểm có số câu `READY` ít nhất gấp đôi chỉ tiêu. Nguồn lọc theo môn, dạng, điểm (và dạng bài nếu khối lọc), không theo kỳ thi, nên bài khác kỳ thi dùng chung nguồn khi trùng bộ lọc. Mỗi dòng dưới đây là gấp đôi nhu cầu lớn nhất của một đề trong mục 3.1. Đơn vị là đơn vị đếm (câu đơn, câu con, vị trí Điền khuyết); "bộ" là chùm nguyên.

| Môn | Dạng, điểm | Tối thiểu | Ghi chú |
|---|---|---|---|
| Toán | MĐ đơn 1 điểm, dạng bài `SINGLE` | 100 | QDA 50; HSA 26; BCA 25 |
| Toán | TLN 1 điểm, dạng bài `SINGLE` | 30 | HSA 15; BCA 10 |
| Toán | chùm 3 câu con MĐ 1 điểm, dạng bài `CLUSTER` | 6 bộ | HSA 3 bộ |
| Toán (HSA) | biên lớp, độ khó trên các câu trên | lớp 10/11/12 ≥ 6/40/54; NB/TH/VD/VDC ≥ 4/42/40/14 | trải đều trên cả MĐ và TLN; thêm 10 TLN + 60 MĐ + 6 bộ gắn nút `TEST-TOAN-HEP` cho TEST-ERR-06 |
| Toán | MĐ 0,25 | 40 | SP2E 2026: 20. Rải đều 12 ô lớp × mức, mỗi ô 3–4 câu (cho TEST-ERR-11) |
| Toán | ĐS 1 điểm, `COUNT_TABLE` | 8 | THPTQG, SP2E 2025: 4 |
| Toán | TLN 0,25 / TLN 0,5 | 20 / 12 | SP2E 2026: 10 / THPTQG, SP2E 2025: 6 |
| Toán | NĐ 0,25 `ALL_OR_NOTHING`; chùm 5 câu con 0,25 | 10; 2 bộ | SP2E 2026 |
| Toán | TL 1 điểm (có hướng dẫn chấm); MĐ 10 điểm | 6; 60 | SPT 3; V-ACT 30 |
| Ngữ văn | MĐ đơn 1 điểm | 100, trong đó ≥ 20 dạng bài `WORD_USAGE_ERROR` | QDA 50; HSA 10 + 15 |
| Ngữ văn | chùm 5 câu con MĐ 1 điểm, `PASSAGE_READING` | 10 bộ | HSA 5 bộ; đúng 2 bộ gắn nhãn `TEST-HIEM` |
| Ngữ văn | TL 25 điểm; chùm 5 câu con TL 0,8; TL 2; TL 4 | 2; 2 bộ; 2; 2 | BCA Phần 1; THPTQG |
| Tiếng Anh | MĐ đơn 1 điểm | 100, trong đó ≥ 10 `SYNONYM`, 10 `ANTONYM`, 10 `DIALOGUE_COMPLETION`, 40 `SENTENCE_COMPLETION` | QDA 50; HSA 35 |
| Tiếng Anh | chùm 5 câu con MĐ 1 điểm, `READING_COMPREHENSION` | 6 bộ | HSA 3 bộ |
| Tiếng Anh | MĐ 0,75 / MĐ 10 | 40 / 60 | BCA 20 / V-ACT 30 |
| Tiếng Anh | MĐ 0,2; chùm 5 câu con 0,2; Điền khuyết 10 vị trí × 0,2 | 60; 4 bộ; 2 câu | SP2E 2026 |
| Lí, Hóa, Sinh, Sử, Địa (mỗi môn) | MĐ đơn 1 điểm | 50 | QDA 25; BCA Sử 10; HSA tối đa 7 |
| Lí, Hóa, Sinh, Sử, Địa (mỗi môn) | TLN 1 điểm; chùm 3 câu con MĐ 1 điểm | 8; 4 bộ | HSA môn nhận 17 |
| Lí, Hóa, Sinh, Sử, Địa (HSA) | biên lớp, độ khó | lớp 10/11/12 ≥ 6/10/18; NB/TH/VD/VDC ≥ 6/14/10/4 | gấp đôi trường hợp lớn nhất của 16 và 17; Địa lí thêm đúng 2 bộ, 6 MĐ, 4 TLN gắn nút `TEST-DIA-HEP` khớp biên của trường hợp 16 (lớp 3/5/8; mức 3/6/5/2) |
| Môn của CA1–CA4 (mỗi mã) | MĐ 1 điểm | 30 | BCA 15 |
| Tiếng Việt, Tư duy khoa học | MĐ 10 điểm | 60 mỗi môn | V-ACT 30 |
| Tin học | MĐ 0,25; ĐS 1 điểm | 48; 8 | THPTQG 24; 2 chung + 2 nhánh mỗi đường |

Tổng ước tính: khoảng 1.600 đơn vị đếm. Sinh bằng `generate_series` như plan BE-2a, không viết tay. Mỗi dòng có câu Nháp và câu Ngừng sử dụng đi kèm để chắc rằng chúng không được đếm.

### 3.4 Câu mẫu cho H-SCA và V-SAT (UNI-2691, spec `2026-11-18-pbi04-dot-2b-bo-sung-hsca-vsat.md` mục 4, 5)

Nằm trong `R__seed_questionbank_dev_data.sql`, mã câu `SEED-HSCA-*` và `SEED-VSAT-*`, trạng thái `READY`. Mỗi dòng có nhiều hơn mức mẫu cần để bộ giải còn chỗ chọn. Mọi nội dung là chữ ASCII ngắn do đội tự soạn, không lấy từ đề thật. Câu trắc nghiệm dùng bốn phương án; lớp và độ khó rải đều ba lớp 10, 11, 12 và ba mức NB, TH, VD (Văn, Tiếng Anh của H-SCA dùng lớp 12, mức TH vì hai môn này tắt lớp và độ khó).

**H-SCA** (điểm theo Q-39, chùm theo Q-40):

| Môn | Dòng của mẫu | Dữ liệu mẫu | Mẫu cần |
|---|---|---|---|
| Toán, Vật lí, Hóa học, Sinh học (mỗi môn) | Chọn một, bốn phương án, 0,25 | 24 câu đơn | 20 |
| | Chọn nhiều, 0,25 (`PARTIAL`) | 8 câu đơn | 5 |
| | Chùm phần 2 | 4 bộ, mỗi bộ 5 câu con chọn một 0,25 | 1 bộ, tổng 5 câu con |
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
| Địa lí | Phần II hai vị trí theo nhãn | bộ gắn `DIA_BANG_SO_LIEU`, `DIA_BIEU_DO` ở câu gốc; câu con 1 gắn bảng số liệu, câu con 2 gắn biểu đồ | 2 |
| Ngữ văn I–III | Phần II, chùm 3 câu con | 3 bộ `VAN_THO`, 3 bộ `VAN_TRUYEN` | 1 mỗi nhãn |
| Tiếng Anh | Bài đọc dùng chung câu 8–15 | 3 bộ 8 câu con: vị trí 1–2 Đúng/Sai bốn ý, vị trí 3–8 chọn một, 6 điểm mỗi câu con | 1 |
| | Phần II theo phần | 3 bộ 6 câu con chọn một, 6 điểm | 1 |
| | Cloze | 3 câu, 5 vị trí, 6 điểm mỗi vị trí | 1 |

**Audio.** 18 tệp MP3 đơn kênh trong `backend/src/main/resources/db/devdata/assets/` (`hsca-en-listening-single-01..10`, `-set4-01..04`, `-set5-01..04`), mỗi tệp 2 giây âm đơn tự sinh, đúng 6.264 byte, cộng 112.752 byte. Dòng `question_asset` (`kind = ATTACHMENT`, `mime = audio/mpeg`, `object_key = questions/dev-seed/<tên tệp>`) cùng `question_asset_ref` phiên bản 1 nằm trong tệp `R__` ở trên; `DevQuestionAssetSeeder` (hồ sơ `dev`, `test`) ghi từng tệp vào kho lưu trữ theo `object_key` của các dòng đó, bỏ qua tệp đã có. Tệp audio chỉ gắn vào câu gốc: câu đơn Nghe có một tệp, chùm Nghe có một tệp ở câu gốc, câu con không có tệp.

## 4. Khi chuyên gia trả lời

- Sửa số trong mục 2 và migration mẫu cùng một PR. Ghi mã quyết định mới vào spec mục 1.
- Chạy lại kiểm tra cấu hình cho cả bộ đề test. Bài hợp lệ phải vẫn VALID; nếu không, tăng nguồn theo mục 3.3 trước.
- Xóa chữ "giả định" ở dòng đã có số thật; giữ dòng nào chưa có.
