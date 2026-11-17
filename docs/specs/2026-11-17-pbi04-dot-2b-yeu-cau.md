# PBI-04 đợt 2B: yêu cầu nghiệp vụ rút từ UniNote 2026-11-17

Nguồn (thư mục `docs/design/dot-2/`): **CHUNG** = `uninote-pbi04-thiet-lap-bai-thi.md` (08:56), **HSA** = `uninote-pbi04-cau-truc-hsa.md` (10:01), **QDA** = `uninote-pbi04-cau-truc-qda.md` (10:39), **BCA** = `uninote-pbi04-cau-truc-bca.md` (11:17), **SP** = `uninote-pbi04-cau-truc-sp2e-spt.md` (14:46).
So với: **spec** = `docs/specs/2026-11-16-be-pbi-03-04.md` trên develop (B*, D*, mục 5, 8, 12); **gap** = `.thi360-dev/pbi04-gap-2026-11-17.md`; **LT** = đặc tả lượt thi `2026-11-16-luot-thi-pbi-10-13.md`; **TM** = đặc tả thương mại `2026-11-16-thuong-mai-pbi-05-09.md`.

Bản CHUNG đổi so với bản 16/11 (git HEAD): bỏ chế độ MAPID "khớp tất cả"; bỏ hẳn bảng "6.1 Thông tin khối" và "6.2 Quy tắc cấu trúc", thay bằng câu "xem tài liệu cấu trúc theo từng kỳ"; điểm tối đa chỉ ghi ở bài và phần; "FULL/bài lẻ" đổi thành "FULL/Từng phần".

Cột trạng thái: **mới** (spec chưa có), **đổi** (spec có nhưng phải sửa hoặc mở rộng), **trái** (ngược với spec, LT, TM hoặc mã nguồn đã viết), **đã có**.

## 1. Yêu cầu mới hoặc đổi

### 1.1 Luồng chung

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-01 | Hai bước. **Bước 1 "Thông tin chung và cấu trúc tổng quan"**: tên, kỳ thi, phạm vi, phần hoặc môn, số câu, thời lượng, thang điểm. **Bước 2 "Cấu trúc chi tiết"**: cách lấy câu của từng phần và môn. Bước 2 đọc số câu và thời lượng đã lưu ở bước 1, không bắt nhập lại. | HSA §1, §2; QDA §1; SP §2 | đổi (B8: bước 1 chỉ có thông tin, số câu lấy từ mẫu ở bước Cấu trúc) |
| R-02 | SP2E và SPT có 5 bước: tổng quan → chi tiết → kiểm tra (phần hoặc toàn bài) → sinh đề và dùng thử → xuất bản và thiết lập bán. | SP §2 | đổi |
| R-03 | Bố cục bước 2 gồm 4 vùng. (a) Thông tin bài, có nút "Sửa thông tin tổng quan". (b) Danh sách phần/môn theo thứ tự làm bài; mỗi mục có tình trạng **Đã kiểm tra / Cần kiểm tra lại / Chưa hoàn tất**. (c) Vùng sửa của mục đang chọn; biểu mẫu khác nhau theo loại mục: ma trận, nhóm dạng bài, hoặc quy tắc tổ hợp. (d) Tổng hợp: mục tiêu, số đã phân bổ, số thiếu hoặc vượt; câu chùm hiện cả số chùm và số câu con. Bài chỉ có một môn thì mở thẳng môn đó. FULL mở Phần 1 Toán trước; bài theo phần Khoa học mở tổng quan Khoa học trước. | HSA §1–2; QDA §2; BCA §2 | mới (spec 9.1 chưa có tình trạng theo mục) |
| R-04 | Phản hồi ngay khi nhập, ví dụ "Đã phân bổ 47/50 câu. Còn thiếu 3 câu." Bấm vào một lỗi thì mở đúng môn, nhóm hoặc trường. | HSA §2, §4b | đã có (bảng tổng hợp ở spec 9.1); điều hướng tới trường là mới |
| R-05 | Chuyển môn hoặc quay lại bước 1 khi còn thay đổi chưa lưu: hộp thoại 3 nút **Lưu và chuyển / Bỏ thay đổi / Ở lại**. | HSA §2; QDA §2; BCA §2 | đổi (hiện chỉ có Rời và Ở lại) |
| R-06 | Lưu nháp được khi còn thiếu môn, nhóm hoặc nguồn. Mở lại thì tải cấu hình đã lưu của từng môn. Giá trị mặc định chỉ điền ở lần khởi tạo đầu. Mở nháp hoặc sinh thử không làm đổi cấu hình đã lưu. | HSA §2, §9; QDA §9 | đã có |
| R-07 | Kiểm tra theo phạm vi. **Kiểm tra môn / Kiểm tra phần** chạy cho mục đang chọn. **Kiểm tra toàn bài / Tiếp tục** chạy cho toàn phạm vi: đạt thì lưu và sang bước sau; không đạt thì ở lại bước 2 và dẫn tới lỗi đầu tiên. | HSA §2, §9; QDA §9; BCA §9; SP §2 | mới (spec chỉ kiểm tra cả bài) |
| R-08 | Trạng thái kiểm tra lưu theo môn. Sửa một môn chỉ làm mất hiệu lực môn đó và các tổ hợp hoặc mã chứa môn đó. Ví dụ: sửa Vật lí thì Vật lí và các tổ hợp có Vật lí chuyển "Cần kiểm tra lại". | HSA §9; QDA §2, §6c; SP §9.1 | mới (D14 làm mất hiệu lực cả bài) |
| R-09 | Đổi số câu ở bước 1 (ví dụ Toán 50 → 40): giữ các dòng cấu hình và hiện ngay chênh lệch 10 câu, không nạp lại mẫu. Đổi kỳ thi, môn, phạm vi hoặc phiên bản thì vẫn nạp lại sau khi người dùng xác nhận. | HSA §9; CHUNG §5.3.6 | mới (phần đổi số câu) |
| R-10 | Phiên bản cấu trúc chọn ở bước 2. SP2E mặc định "Cấu trúc 2026 theo đề án", có thể chọn cấu trúc của đề minh họa cũ; mỗi lựa chọn mô tả dạng câu và số lượng. Hạn ngạch chi tiết lấy từ đề cũ chỉ dùng khi chọn cấu trúc cũ. | SP §4; CHUNG §5.1 | đã có (nạp mẫu, B8); dữ liệu mẫu phải đổi |
| R-11 | Phân biệt **lỗi** và **cảnh báo**. Lỗi chặn xuất bản: thiếu chỉ tiêu bắt buộc, thiếu nguồn. Cảnh báo: lệch so với tỷ lệ chỉ dùng tham khảo. | SP §9.1; BCA §7a | mới |
| R-12 | Thêm nhóm kiểm tra "làm và chấm được trên web/app": mỗi dạng câu phải hiển thị được, ghi nhận được câu trả lời, có đáp án và quy tắc chấm. BCA kiểm thêm tài nguyên đính kèm và điều kiện chấm. | SP §9.1; BCA §9 | mới |
| R-13 | Thông báo lỗi chỉ tới mức chùm, vị trí hoặc ý. Ví dụ: "Nhóm 5, chùm 4 – chưa có ngữ liệu về văn học thiếu nhi đủ 5 câu con"; "Tiếng Anh – Đọc hiểu 2 đã phân bổ 7/8 câu"; "ngân hàng chỉ có 4 câu ghép đôi đủ cấu trúc 4 ý trái – 6 lựa chọn phải"; "Địa lí – Phần II cần một câu kỹ năng có đủ 4 ý theo phân bổ 2 NB, 1 TH, 1 VD". | QDA §5; SP §6.3, §9.1 | đổi (cần mã lỗi mới) |
| R-14 | Sinh đề thử dùng cùng bộ sinh đề với lượt thật. Phạm vi thử: một môn, một mã hoặc tổ hợp, hoặc cả bài. Có bảng **Yêu cầu / Thực tế** theo từng tiêu chí. Hiện ngữ liệu cùng các vị trí trả lời của bài đọc điền. Không trừ lượt. Sinh lại được. Mã chọn để thử chỉ dùng cho lần thử đó. | HSA §4b, §7, §9; QDA §9; SP §9.1 | đổi (spec thử theo đường, chưa thử riêng một môn) |
| R-15 | BCA: khi đổi mã trên cùng một đề thử, giữ phần 1–2 đã sinh và chỉ thay câu 66–80. Nút "Sinh đề thử mới" lấy lại toàn bộ đề. | BCA §3b | mới |
| R-16 | "Làm thử như học viên" và chấm thử: hiện điểm từng câu để quản trị viên kiểm tra band Đúng/Sai. | SP §2 bước 4, §13, §14 | mới |
| R-17 | Khi công bố, ghi lại phiên bản quan hệ mã–môn của Global List mà bài dùng. Lượt đã bắt đầu giữ cấu trúc đã ghi. | QDA §3c | đổi (gap: `exam_version.config` chưa có) |
| R-18 | Global List thêm mã mới ghép từ các môn đã có: khi cập nhật bài, chỉ cần kiểm tra khả năng sinh đề của mã mới. Mã có môn chưa thiết lập: báo tên môn cần bổ sung ma trận. | QDA §3c | mới |

### 1.2 Phạm vi bài và bài ôn

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-19 | Phạm vi theo kỳ thi:<br>• HSA: FULL; PART (Toán, Văn, Tiếng Anh, Khoa học); SUBJECT.<br>• QDA: FULL; PART (Toán, Văn, Tiếng Anh, Khoa học); SUBJECT.<br>• BCA: FULL; PART (Tự luận Văn, TN bắt buộc, TN tự chọn); SUBJECT.<br>• SP2E và SPT: SUBJECT (Đầy đủ cấu trúc hoặc Ôn theo chủ đề); PART (một phần của môn). | HSA §1; QDA §1; BCA §1; SP §1, §8, §10 | đổi (spec: HSA và QDA {FULL, PART}, BCA {FULL}, SPT và SP2E {SUBJECT}) |
| R-20 | Bài theo môn của kỳ thi có mã (QDA, BCA): học viên vào làm thẳng, không chọn mã. Ví dụ: bài Hóa QDA không hỏi QĐ01 hay QĐ02. | QDA §8; BCA §3b, §8 | mới |
| R-21 | Bài theo môn có hai cách khởi tạo. **"Dùng cấu trúc môn [kỳ thi]"** nạp ma trận mặc định (ví dụ QDA Hóa 25 câu: lớp 6/5/14, độ khó 4/20/1). **"Tự thiết lập bài ôn"** tạo bài ôn. HSA, QDA, BCA chọn ở bước 2; SP2E, SPT chọn ở bước 1. | HSA §8; QDA §8; BCA §8; SP §1 | mới |
| R-22 | Bài ôn: số câu và thời lượng lấy từ bước 1. Màn rút gọn hiện trước 3 trường: phạm vi kiến thức, dạng câu, số lượng. Phân bố lớp, độ khó và câu chùm đóng sẵn, chỉ mở khi cần. Ví dụ:<br>• HSA đồ thị hàm số, 10 câu, 20 phút: 7 TN + 3 điền; độ khó 3 TH / 5 VD / 2 VDC.<br>• QDA Di truyền y học: 7 câu đơn + 1 chùm 3 câu.<br>• BCA đọc biểu đồ Địa: 7 câu đơn + 1 chùm 3 câu.<br>• SP2E Toán đồ thị hàm số: 7 TN + 3 trả lời ngắn. | HSA §8; QDA §8; BCA §8; SP §8 | mới |
| R-23 | Bài theo phần của SP2E và SPT: chép các dòng ma trận của phần đó; được giữ hoặc sửa số câu, thời lượng, thang. Giữ cách chấm gốc thì thang bằng điểm của phần (Địa lí Phần II là 4 điểm). Muốn công bố trên thang 10 thì phải xác nhận quy tắc quy đổi và xem trước. | SP §8 | mới (có thể dùng lại `LINEAR` của D11) |

### 1.3 Ma trận theo môn, mã đề và Global List

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-24 | QDA và BCA: **mỗi môn một ma trận**, dùng chung cho mọi mã có môn đó (Hóa có trong QĐ01 và QĐ02 vẫn chỉ có một ma trận Hóa 25 câu). Màn chỉ liệt kê môn, mỗi môn một lần. Quản trị viên không chọn mã, không gán môn cho mã, không khai lại ma trận. | QDA đầu, §3, §9 AC1; BCA đầu, §3a | **trái** (D26 và mã nguồn: một ma trận cho mỗi mã tổ hợp, `ExamPartOption`, `V202611161802`) |
| R-25 | Global List lưu kỳ thi, mã, **nhánh (Khoa học hoặc Tiếng Anh)**, **danh sách môn có thứ tự**, trạng thái, phiên bản. Bài thi chỉ đọc danh mục này để biết những môn cần chuẩn bị. | QDA §3a; BCA §3a | mới (chưa có thuộc tính nhánh và thứ tự môn) |
| R-26 | Quan hệ mã–môn hiện hành:<br>• QĐ01: Lý → Hóa. QĐ02: Hóa → Sinh. QĐ03: Lý → Sinh. QĐ04: Sử → Địa. Mỗi môn 25 câu.<br>• QĐ05: Tiếng Anh 50 câu.<br>• CA1 Lý, CA2 Hóa, CA3 Sinh, CA4 Địa, mỗi mã 15 câu. | QDA §3b; BCA §3a | **trái** (dữ liệu mẫu QD05 = "Toán, Vật lí, Tiếng Anh") |
| R-27 | Thứ tự ghép môn lấy từ mã. Ví dụ QĐ02: vị trí 1–25 là Hóa, 26–50 là Sinh. Môn đứng thứ nhất hay thứ hai trong mã không đổi cách khai ma trận. | QDA §3c, §6a | mới |
| R-28 | Danh sách môn cần thiết lập suy từ Global List theo phạm vi bài:<br>• QDA FULL, phần 3: Lý, Hóa, Sinh, Sử, Địa, Tiếng Anh.<br>• QDA theo phần Khoa học: 5 ma trận.<br>• BCA phần 3: 4 ma trận. | QDA §1; BCA §3a | mới |
| R-29 | Lỗi theo môn kèm các mã bị ảnh hưởng: "Hóa học chưa hoàn tất cấu hình 25 câu. Các mã bị ảnh hưởng: QĐ01, QĐ02." Bấm vào lỗi thì mở thẳng môn đó. | QDA §9 | mới |
| R-30 | Kiểm tra toàn bài xét mọi mã trong phạm vi bài, dựa trên ma trận của các môn. Kết quả và bảng đối chiếu phân bố hiện theo từng mã. | QDA §9; BCA §9 | đã có (mọi đường); phải đổi theo mô hình theo môn |

### 1.4 HSA

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-31 | Đề có 3 phần, 150 câu, 195 phút (75/60/60), 150 điểm. Phần 3 là **một phần có hai nhánh** (Khoa học hoặc Tiếng Anh). Tổng chỉ tính một nhánh; nhánh Khoa học chỉ tính 3 môn. | HSA §1; CHUNG §5.1 | đã có |
| R-32 | Toán mặc định: 50 câu, 75 phút, toàn môn; 35 TN 4 lựa chọn + 15 điền đáp án; lớp 3/20/27; độ khó 2/21/20/7; 1 chùm 3 câu tại vị trí 48–50 (khai số chùm); chưa có chỉ tiêu chủ đề. Câu con của chùm tính vào 35 hoặc 15 theo dạng thật của nó. | HSA §4a–b | đã có (spec 5.2) |
| R-33 | Văn: 5 nhóm theo thứ tự.<br>(1) Từ khác nhóm nghĩa: 5 câu đơn.<br>(2) Từ sai ngữ pháp, ngữ nghĩa, logic hoặc phong cách: 5 câu đơn.<br>(3) Cặp từ điền chỗ trống: 5 câu đơn.<br>(4) Một ngữ liệu, một câu: 10 câu, mỗi câu có ngữ liệu riêng.<br>(5) 5 chùm × 5 câu con = 25 câu.<br>Sửa một nhóm thì chọn dạng bài, cách lấy (câu đơn hoặc câu chùm), số lượng; nút "Thêm điều kiện" để khai MAPID, loại văn bản, mức độ. | HSA §5a–b | đổi (mẫu chưa có số câu đơn; thiếu mã TASK_TYPE) |
| R-34 | Điều kiện riêng cho từng chùm. Ví dụ nhóm 5: mỗi loại văn bản lấy 1 chùm (nghị luận xã hội, thông tin, khoa học – chính luận, thơ, văn xuôi). | HSA §5b | mới |
| R-35 | Khoa học: 50 câu, 60 phút, học viên chọn 3 trong 5 môn. Quản trị viên thiết lập đủ 5 môn, mỗi môn một **ma trận 17 câu** (MAPID, số câu đơn, 1–2 chùm × 3 câu con). Danh sách 5 môn có tình trạng, nút "Thiết lập" và nút "Môn tiếp theo". | HSA §6a–b | đổi |
| R-36 | Ma trận 17 câu mặc định cho 5 môn: xem mục 2. | HSA §6b | đổi (mẫu chưa có số) |
| R-37 | **Quy tắc môn 16 câu**: chỉ khai ma trận 17 câu. Môn phải làm 16 câu thì sinh 17 câu theo ma trận, rồi **bỏ ngẫu nhiên 1 câu đơn**, không bỏ câu con của chùm. Lý, Hóa, Sinh phải còn ít nhất 1 câu điền đáp án; nếu câu điền duy nhất là câu đơn thì giữ nó lại. Số câu của lớp và độ khó chứa câu bị bỏ giảm 1; BA chấp nhận lệch ma trận. Lượt lưu đúng 50 câu cuối cùng và khôi phục được khi tải lại. | HSA §6c | **trái** (D25, spec 5.2: chỉ tiêu theo %, nhóm "lấy phần còn lại", giải riêng trường hợp 16 và 17; `ExamCheckRules:308`) |
| R-38 | Chọn môn làm 16 câu: 2 tự nhiên + 1 xã hội thì môn xã hội; 1 tự nhiên + 2 xã hội thì môn tự nhiên; 3 môn tự nhiên thì chọn ngẫu nhiên 1 trong 3. | HSA §6a, §6c | đã có (B10) |
| R-39 | Tiếp tục: kiểm tra Toán, Văn, Tiếng Anh và 10 tổ hợp 3 trong 5 môn. Với R-37, chỉ cần chứng minh mỗi môn sinh được 17 câu và luôn bỏ được 1 câu đơn hợp lệ. | HSA §9 | đổi (12 trường hợp × 10 bài toán con được rút gọn) |
| R-40 | Tiếng Anh: 11 nhóm, mỗi nhóm có vị trí cố định (bảng ở mục 2). Màn hiện dải số câu tự tính. Giữ thứ tự 11 nhóm; câu trong nhóm đổi theo từng đề. Bài đọc điền giữ đúng ánh xạ chỗ trống – đáp án. | HSA §7 | đổi (mẫu chưa có dải vị trí) |

### 1.5 QDA

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-41 | Đề FULL: Toán 50 câu, 80 phút, 50 điểm; Văn 50 câu, 55 phút, 50 điểm; Phần 3 50 câu, 60 phút, 50 điểm; cả bài 150 câu, 195 phút, 150 điểm. Mã Khoa học: hai môn dùng chung 60 phút. | QDA §1 | đã có (thời lượng); thêm điểm theo phần |
| R-42 | Toán: 35 TN + 15 điền đáp án (số nguyên, phân số tối giản, số thập phân dùng dấu phẩy); lớp 5/15/30; **độ khó không mặc định**; mỗi chùm có 3 câu con TN. Quản trị viên nhập số chùm, hệ thống tự tính số TN đơn (1 chùm: 32 + 3 + 15). | QDA §4 | mới |
| R-43 | Văn: 5 nhóm, **thứ tự khác HSA**: (1) từ khác nhóm nghĩa 5; (2) cặp từ điền 5; (3) từ sai 5; (4) đoạn trích một câu 10; (5) 5 chùm × 5. Chùm 1–5 của nhóm 5 có loại ngữ liệu mặc định: thông tin – nghị luận, nghị luận, đoạn trích thơ, văn học thiếu nhi, đoạn trích truyện. | QDA §5 | mới |
| R-44 | Mỗi môn Khoa học 25 câu: 1 hoặc 2 chùm × 3 câu con. Số câu điền đáp án: Lý, Hóa, Sinh tối thiểu 1; Sử, Địa được 0. **Số TN đơn tự tính = 25 − số câu con trong chùm − số câu điền** (Lý 2 chùm + 1 điền: 18 + 6 + 1; đổi về 1 chùm thì TN đơn thành 21). | QDA §6a | mới |
| R-45 | Ma trận 25 câu mặc định cho 5 môn: xem mục 2. | QDA §6b | mới |
| R-46 | Tiếng Anh: 10 nhóm có vị trí (bảng ở mục 2). Nhóm đồng nghĩa/trái nghĩa lấy 4 câu trong cùng nhóm; tỷ lệ giữa hai dạng chỉ áp khi có khai. | QDA §7 | mới |

### 1.6 BCA

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-47 | FULL: 81 câu, 100 điểm, **một đồng hồ chung 180 phút**.<br>• Phần I: tự luận Văn, 25 điểm, hiển thị riêng.<br>• Câu 1–65: TN bắt buộc, 60 điểm.<br>• Câu 66–80: môn tự chọn theo mã, 15 điểm.<br>Bài theo phần hoặc theo môn dùng thời lượng của bước 1. | BCA đầu, §1 | đã có (mẫu); vị trí và điểm phải sửa |
| R-48 | Vị trí và cách lấy ở phần 2:<br>• Toán: 1–27 đơn; 28–30 chùm 3 câu TN; 31–35 trả lời ngắn.<br>• Sử: 36–42 đơn; 43–45 chùm tư liệu 3 câu.<br>• Anh: 46–47 ngữ âm (1 phát âm + 1 trọng âm); 48–55 từ vựng – ngữ pháp; 56–60 chùm đọc điền 5 câu (chọn phương án); 61–65 chùm đọc hiểu 5 câu.<br>Nguồn Anh chọn riêng cho từng nhóm. | BCA §5a, §5c | đổi (mẫu chưa chia nhóm như vậy) |
| R-49 | Điểm: Toán và Sử 1 điểm/câu; **Anh 0,75 điểm/câu** (theo nhóm: 1,5 / 6 / 3,75 / 3,75). Câu chùm tính điểm theo câu con. Tổng hợp hiện tổng điểm của từng môn, và tách "Tự luận 25 / Trắc nghiệm 75", để phát hiện ngân hàng đang để mặc định 1 điểm cho câu Anh. | BCA §2, §5a | đổi (spec chỉ có điểm mỗi câu ở cấp khối, chưa có tổng điểm môn) |
| R-50 | Phần 3: mỗi môn 15 câu, 15 điểm, gồm 12 câu đơn (66–77) và 1 chùm 3 câu con (78–80). Đề thử phải hiện dữ liệu đính kèm: bảng nguyên tử khối, phả hệ, biểu đồ. Chùm tính phân bố theo từng câu con (chùm Lý có câu con lớp 11 và lớp 12). | BCA §6 | đổi |
| R-51 | Ma trận tham khảo của phần 2 và phần 3: xem mục 2. Anh không chia theo lớp ("lấy theo chủ đề"). Độ khó do người biên soạn tài liệu ước lượng. | BCA §5b, §6 | mới |
| R-52 | **Chỉ tiêu cấp bài trải nhiều môn**, chỉ để tham khảo, đối chiếu theo từng mã:<br>• Lớp, tính trên 60 câu (Toán + Sử + môn tự chọn, không gồm Anh): lớp 12 khoảng 42 câu (70%); lớp 10 và 11 gộp lại khoảng 18 câu (30%).<br>• Độ khó, tính trên 80 câu: NB và TH gộp khoảng 24 (30%), VD khoảng 40 (50%), VDC khoảng 16 (20%).<br>Lệch chỉ thành lỗi khi quản trị viên đặt phân bố đó là bắt buộc. | BCA §7a | mới |
| R-53 | Học viên chọn mã ở **màn chuẩn bị, trước khi tính 180 phút**. Mỗi lựa chọn hiện mã và môn, ví dụ "CA2 – Hóa học". | BCA §3b | đã có (LT F4) |
| R-54 | Tự luận Văn: lấy 1 đề nghị luận xã hội; đoạn trích, nguồn trích và yêu cầu viết nằm trong một bản ghi; tối thiểu 500 chữ; 25 điểm. **V1: "tạm chưa thiết lập câu ngữ văn".** Chưa chấm được tự luận thì không mở thi FULL, với thông báo "Bài thi có phần tự luận Ngữ văn 25 điểm chưa hỗ trợ chấm…". Có thể phát hành bài theo phần TN bắt buộc (60 điểm) hoặc TN tự chọn (15 điểm). | BCA §4 | **trái** (LT L2: tự luận chấm tay, FULL vẫn mở; mẫu BCA đang có khối tự luận) |

### 1.7 SP2E

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-55 | Cấu trúc 2026 của 9 môn là mặc định khi chọn "Đầy đủ cấu trúc" (bảng ở mục 2). | SP §4; CHUNG §5.1 | đổi (mẫu đang dùng số giả định) |
| R-56 | Toán, Phần I có hai dòng: 20 câu một đáp án và 5 câu nhiều đáp án (có quy tắc chấm tập đáp án, giữ ánh xạ khi đảo phương án). Phần II: 5 câu dữ liệu tổng hợp, có thể là câu độc lập hoặc chùm; số chùm do quản trị viên đặt. Phần III: 10 câu trả lời ngắn. | SP §5.1 | mới (mẫu) |
| R-57 | Văn V1 chỉ có Đọc hiểu: 4 dòng ứng với 4 văn bản; nhập số câu từng dòng, tổng 30, không chia đều; 4 văn bản phải khác nhau; 6 điểm. Phần Viết (4 điểm) hiện nhãn "Tự luận – tạm bỏ qua trong V1". Tên bài cho học viên: "SP2E – Ngữ văn – Đọc hiểu". Thời lượng Đọc hiểu do quản trị viên nhập (90 phút là của cả hai phần). Kết quả trên thang 6, ví dụ 4,8/6. Không có câu Viết, không có điểm 0, không có trạng thái chờ chấm. | SP §5.2 | **trái** (LT: Văn SP2E có 2 câu tự luận chấm tay); tự mâu thuẫn về số câu, xem mục 2 |
| R-58 | Lý, Hóa, Sinh ở mục 6: 20 câu (10 TN 0,2 điểm; 5 Đúng/Sai × 4 ý; 5 ghép đôi gồm 4 ý trái và 6 lựa chọn phải; 0,2 điểm/ý, tối đa 0,8 điểm/câu), 50 lệnh hỏi, 10 điểm, 60 phút. | SP §6.1, §6.3 | trái với mục 4 của chính tài liệu (28 câu), xem mục 2 |
| R-59 | Ma trận tham khảo ghi rõ đơn vị: Lý và Hóa tính theo 50 lệnh hỏi, Sinh theo 20 câu. Hóa có nhóm **"Kiến thức tổng hợp" 22 lệnh hỏi**, giữ riêng, không dồn vào một lớp. | SP §6.2 | mới |
| R-60 | Ghép đôi: kiểm tra cấu trúc bên trong (4 ý – 6 lựa chọn). Đảo cột phải thì vẫn giữ ánh xạ đáp án. | SP §6.1, §6.3 | đã có (MATCHING); phần kiểm tra cấu trúc là mới |
| R-61 | Sử, Địa, GDKTPL, Anh: **không hiện Khối lớp** ở phân bổ, bộ lọc nguồn và bảng kiểm tra. Lọc theo mạch kiến thức (MAPID). Độ khó chỉ áp khi quản trị viên bật và ngân hàng có dữ liệu. | SP §7.1 | mới |
| R-62 | Sử theo mạch: thế giới 7 câu/13 lệnh hỏi; VN 1930–1975 6/15; VN từ 1975 2/8; tích hợp 3/6; chưa xác định 2/8. Hai câu chưa xác định lấy trong "Toàn môn – Phần II", hoặc quản trị viên chọn mạch. | SP §7.2 | mới; số câu mâu thuẫn, xem mục 2 |
| R-63 | Địa: 28 câu, 40 lệnh hỏi, 60 phút, 10 điểm.<br>• Phần I, 16 câu: Đông Nam Á 2, tự nhiên 2, dân cư 2, ngành kinh tế 5, vùng kinh tế 1, biểu đồ 4; 4 điểm.<br>• Phần II, 4 câu × 4 ý: mỗi mạch 1 câu (tự nhiên, dân cư, kinh tế, kỹ năng); mỗi câu có 2 ý NB, 1 ý TH, 1 ý VD; 4 điểm.<br>• Phần III, 8 câu trả lời ngắn: tự nhiên 2, dân cư 2, kinh tế 4; 2 điểm.<br>Tiêu chí lớp trong tài liệu không thành điều kiện lọc. | SP §7.3 | mới |
| R-64 | GDKTPL: 32 câu, 50 lệnh hỏi. Phần I có 26 câu; theo đề mã 473 là 20 câu độc lập + 2 chùm × 3. Phần II: 6 câu Đúng/Sai dạng tình huống. Mạch Kinh tế 27/50 lệnh hỏi, Pháp luật 23/50, chỉ để tham khảo; nút **"Áp dụng phân bổ này"** chuyển thành hạn ngạch. | SP §7.4 | mới |
| R-65 | Anh: 9 nhóm (bảng ở mục 2). Mỗi nhóm chọn **Theo nhóm** hoặc **Theo vị trí**. Theo vị trí: gắn MAPID cho một vị trí hoặc dải vị trí, ví dụ Đọc hiểu 2 câu 48–49 là "Suy luận", câu 50 là "Ý chính"; hệ thống phải chọn ngữ liệu có đủ câu con khớp. Mặc định không chia độ khó. | SP §7.5 | mới |
| R-66 | Tỷ trọng mức độ ở bảng tổng quan chỉ để tham khảo: Toán 30/30/25/15; Văn 20/30/30/20; các môn khác 30/30/20/20. Khi dùng ma trận chi tiết thì ma trận đó quyết định cách lấy câu; màn phải cho thấy bộ chỉ tiêu nào đang được dùng. | SP §4 | mới |

### 1.8 SPT

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-67 | Cấu trúc V1 của 12 môn đã bỏ phần tự luận (bảng ở mục 2). Theo đề tham khảo mã 075. | SP §10, §11 | **trái** (mẫu SPT có tự luận; LT lấy Tin SPT mẫu số 25) |
| R-68 | Thời lượng V1 do quản trị viên xác nhận; mốc 60 và 90 phút là của đề đầy đủ. Danh mục bán và màn chuẩn bị hiện số câu, thời lượng và điểm tối đa của bài V1. | SP §11 | mới |
| R-69 | Tỷ lệ tính theo điểm toàn đề thì phải tính lại trên các phần có trong V1. Ví dụ: chuyên đề Hóa chỉ nằm trong câu tự luận 2 điểm thì không tạo hạn ngạch trong bài V1 thang 8. | SP §12.1 | mới |
| R-70 | Sử, Địa, GDKTPL lọc theo mạch. Tỷ lệ lớp chỉ để đối chiếu; câu không phân lớp vẫn được chọn. | SP §12.1 | mới |
| R-71 | Trả lời ngắn: kiểm tra **kiểu đáp án** (phân số, làm tròn đến hàng phần trăm; Hóa có tên chất, công thức, phương trình). Chỉ nhận câu có định dạng nhập và đáp án đối sánh được. | SP §12.1 | đổi (B7 khớp đúng; chưa có kiểu đáp án để lọc nguồn) |
| R-72 | Văn: 2 chùm, mỗi chùm 1 ngữ liệu + 5 TN. Mỗi chùm chọn loại văn bản và MAPID riêng (đề mẫu: khoa học, thông tin). Hai ngữ liệu phải khác nhau. 0,3 điểm/câu. Tên bài: "SPT – Ngữ văn – Đọc hiểu", 10 câu, 3 điểm. | SP §12.2 | mới |
| R-73 | Anh:<br>• I Use of English: 5 câu đơn + 2 đoạn điền từ × 6 + 1 câu sắp xếp hội thoại. Hai đoạn điền từ là **chọn phương án**, mỗi chỗ trống có bộ lựa chọn riêng.<br>• II Reading: 2 bài × 5 câu.<br>• III Word Formation: 1 đoạn có 4 chỗ **tự nhập**, chấm riêng từng chỗ.<br>Câu cùng MAPID nhưng khác cách trả lời thì không thay nhau được. | SP §12.3 | đổi (CLOZE hiện chỉ có dạng tự nhập) |
| R-74 | Tin học: Phần I 20 TN chung; Phần II 1 câu Đúng/Sai chung + 1 câu Đúng/Sai theo định hướng (Khoa học máy tính hoặc Tin học ứng dụng); tổng 22 câu, 7 điểm. Quản trị viên cấu hình cả hai nhánh. **Học viên chọn định hướng ở màn chuẩn bị.** Hệ thống chỉ sinh phần chung và nhánh đã chọn, lưu định hướng vào lượt. Sinh thử và kết quả kiểm tra hiện riêng từng nhánh. | SP §12.4, §14 | **trái** (LT F4, §6.2: chọn trong lúc làm, sinh sẵn cả hai nhánh, mẫu số 25) |
| R-75 | Công nghệ công nghiệp và Công nghệ nông nghiệp là hai môn riêng, chọn khi tạo bài, mỗi môn có MAPID riêng, không chọn nhánh trong lượt. | SP §12.4 | đã có |
| R-76 | Chấm: TN 0,25; TN đọc hiểu Văn 0,3; Đúng/Sai theo band **của từng câu**, 0/0,1/0,25/0,5/1; trả lời ngắn và Word Formation 0,25. | SP §13 | đã có (COUNT_TABLE); điểm câu theo kỳ thi: Q-03 |

### 1.9 Chỉ tiêu (chủ đề, lớp, độ khó, tham khảo hay bắt buộc, cấp bài)

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-77 | Phạm vi kiến thức: **Toàn môn** hoặc **Theo chủ đề** (SP gọi là "Theo mạch kiến thức"). Chọn nhiều MAPID, có tìm kiếm, chỉ trong môn đang sửa. Khớp khi câu có **ít nhất một** mã đã chọn. **Bỏ chế độ "khớp tất cả".** Chọn chủ đề cha thì mặc định gồm cả chủ đề con. | CHUNG §7.4; HSA §3; BCA §7b; SP §3.2 | **trái** (mã nguồn còn `KnowledgeMatch.ALL`, `knowledge-scope-field.tsx:106`); phần chủ đề con đã có (D23) |
| R-78 | Ba khái niệm khác nhau: **phạm vi lấy câu** (lọc), **phân bổ số câu** (hạn ngạch), **thứ tự và cách ghép**. Đặt MAPID ở bộ lọc thì mọi câu của dòng phải thuộc phạm vi đó. Đặt chỉ tiêu chủ đề thì chỉ ràng buộc số câu đã khai. Ví dụ: 3 trong 10 câu trả lời ngắn thuộc Hàm số. | HSA §3; SP §5.1 | đã có (spec 5.2); cần đổi UX |
| R-79 | Nút **"Thêm chỉ tiêu chủ đề"**: chọn MAPID, nhập số câu, và tùy chọn giới hạn lớp hoặc độ khó của riêng chỉ tiêu đó (ví dụ Hàm số – 2 câu – VDC, nằm trong tổng 7 câu VDC). Chỉ tiêu đặt được ở cấp môn, phần hoặc dòng. | HSA §4b; QDA §4b; SP §3.2 | mới (spec chỉ có chỉ tiêu nhóm MAPID ở cấp khối) |
| R-80 | Câu gắn nhiều MAPID chỉ xuất hiện một lần trong đề và chỉ tính vào **một** hạn ngạch chủ đề cùng cấp. Câu đó vẫn tính vào lớp, độ khó và dạng trả lời. | HSA §3; QDA §8; BCA §7b; SP §3.2, §7.1 | **trái** với mã nguồn (`Prep.java:105–119` tính vào mọi nhóm khớp); nguyên tắc đã có ở CHUNG §7.4 |
| R-81 | Độ khó đặt được ở cấp toàn môn, phần, hoặc từng dòng. Các mức NB, TH, VD, VDC lấy từ Global List. Có mẫu chỉ dùng 3 mức (Hóa, Sinh, Địa). | SP §3.2, §6.2 | đổi (spec có cấp môn và cấp nhóm, chưa có cấp phần) |
| R-82 | Phân bố có hai loại: **tham khảo** (chỉ hiện để đối chiếu, lệch thì cảnh báo) và **bắt buộc** (là hạn ngạch, lệch thì lỗi). Có nút "Dùng ma trận tham khảo" và "Áp dụng phân bổ này". | BCA §5b, §7a; SP §4, §6.2, §7.4 | mới |
| R-83 | Chỉ tiêu cấp bài trải nhiều môn, mỗi tiêu chí có tập câu áp dụng riêng (BCA: lớp tính trên 60 câu, độ khó tính trên 80 câu), có mức gộp (lớp 10+11, NB+TH). | BCA §7a | mới (mức gộp đã có ở CHUNG §7.5 nhưng chỉ trong một khối) |
| R-84 | Mỗi tỷ lệ phải ghi rõ đơn vị: **theo câu, theo lệnh hỏi hay theo điểm**. Hiện số câu sau quy đổi để xác nhận (30% của 40 câu là 12). | SP §3.1, §6.2 | mới (CHUNG §8.4 vẫn ghi "tỷ lệ tính theo số câu") |
| R-85 | Đúng/Sai có **độ khó cho từng ý** khi ma trận tính theo lệnh hỏi. Ngân hàng phải lưu mức độ của từng ý thì mới kiểm tra được. | SP §3.2, §6.3, §7.3 | mới |
| R-86 | Tổng phân bổ phải bằng số câu nguyên. Tỷ lệ % làm tròn độc lập có thể cộng thành 101%; hệ thống kiểm tra theo số câu. | BCA §6 | đã có |

### 1.10 Dạng bài và đơn vị "lệnh hỏi"

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-87 | Đơn vị **lệnh hỏi**. Câu Đúng/Sai 4 ý tính 1 câu trong cấu trúc đề và 4 lệnh hỏi trong ma trận. Ghép đôi 4 ý tính 4 lệnh hỏi. Câu đơn tính 1. Tổng hợp, kiểm tra và đề thử hiện cả số câu, số lệnh hỏi và điểm. | SP §3.1, §6.1, §6.3 | mới |
| R-88 | Câu nhiều ý: khai số ý mỗi câu, dạng trả lời, cách chấm theo ý hay cả câu. Khi lấy câu phải lấy đủ các ý. | SP §3.1 | đã có (TRUE_FALSE); kiểm số ý là mới |
| R-89 | Dạng bài và dạng trả lời là hai trường riêng. Dạng bài trên câu hỏi dùng chung danh mục TASK_TYPE với cấu trúc đề. Ví dụ: "sắp xếp hội thoại" là dạng bài, nhưng học viên trả lời bằng cách chọn 1 trong 4. Cần mã TASK_TYPE cho mọi nhóm đã nêu ở R-33, R-40, R-43, R-46, R-48, R-65, R-73. | HSA §5b; SP §3.2 | đổi (D22 đã tách hai trường; thiếu mã) |
| R-90 | Nhóm có vị trí cố định thì màn hiện dải vị trí tự tính (1–10, 11–12…). | HSA §7; QDA §7; BCA §5a; SP §7.5 | mới |

### 1.11 Tự luận trong V1

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-91 | V1 không có câu tự luận ở BCA (Văn 25 điểm), SP2E (Văn, phần Viết) và SPT (mọi phần viết bài, trình bày lập luận, trình bày lời giải). Màn quản trị vẫn hiện cấu trúc gốc với nhãn **"Tự luận – tạm bỏ qua trong V1"**. Không tạo câu làm bài, không tính vào điểm tối đa, không có trạng thái chờ chấm, không chặn xuất bản. | BCA §4; SP §5.2, §10, §13, §14 | **trái** (LT L2 và PBI-12: tự luận chấm tay, vẫn mở; mẫu và dữ liệu test còn tự luận) |

### 1.12 Đảo và thứ tự nhóm

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-92 | Giữ thứ tự các phần và các nhóm có vị trí: Anh HSA 11 nhóm, Anh QDA 10 nhóm, Anh SP2E 9 nhóm, các vị trí 1–80 của BCA, Phần I → III của SP2E và SPT. Trong mỗi nhóm, lấy ngẫu nhiên câu hoặc chùm thỏa điều kiện. | HSA §7; QDA §7; SP §3.2, §7.5 | **trái** (`groupOrder` đã bị xóa ở `V202611171119` nhưng LT F22 vẫn đọc) |
| R-93 | Văn HSA: thứ tự nhóm mặc định 1 → 5. Quản trị viên **có thể cho phép** đảo thứ tự các chùm trong nhóm 5. | HSA §5b | **trái** (mẫu Văn đang `SHUFFLE`; quyết định 17/11: luôn đảo, không có cờ) |
| R-94 | Câu con của chùm luôn liền nhau, đúng thứ tự đã cấu hình. Đúng/Sai và ghép đôi lấy nguyên câu. Đảo phương án hoặc cột thì vẫn giữ ánh xạ đáp án. | HSA §5b; SP §5.1, §6.1 | đã có (LT F22) |

### 1.13 Những gì liên quan tới học viên

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-95 | QDA: học viên chọn mã khi tới phần tự chọn. Màn hiện mã và tên các môn theo thứ tự ("QĐ02 – Hóa học và Sinh học"). Xác nhận một lần. Lượt lưu mã, thứ tự môn, bộ câu, và khôi phục đúng khi tiếp tục. | QDA §3c, §9 | đã có (LT) |
| R-96 | BCA chọn mã ở màn chuẩn bị (R-53). HSA chọn 3 môn và khôi phục đủ 50 câu (R-37). | BCA §3b; HSA §6c | đã có |
| R-97 | SPT Tin học chọn định hướng ở **màn chuẩn bị** (R-74). | SP §12.4 | **trái** (LT F4, §6.2) |
| R-98 | Bài theo môn và bài ôn: học viên vào làm thẳng, không có bước chọn mã (R-20). | QDA §8; BCA §8 | mới |
| R-99 | Màn chuẩn bị và danh mục bán hiện số câu, thời lượng và điểm tối đa của **bài học viên thực sự làm**. | SP §11 | mới |
| R-100 | Có kết quả ngay sau khi nộp, theo thang của V1: SPT tối đa 7, 8 hoặc 3 điểm; SP2E Văn 6 điểm; BCA bài theo phần 60 hoặc 15 điểm. Không hiện điểm 0 hay trạng thái chờ chấm cho tự luận đã bỏ. | SP §5.2c, §11, §14; BCA §4 | đổi (LT dùng thang của đề đầy đủ) |
| R-101 | Bộ đề: học viên mở bộ rồi tự chọn môn. Mỗi bài có đồng hồ, lượt làm, nút tiếp tục và kết quả riêng. Nộp bài Toán chỉ kết thúc lượt Toán; các môn khác chỉ bắt đầu khi học viên chọn làm. | SP §9.2 | mới |

### 1.14 Bán và bộ đề

| Mã | Nội dung | Nguồn | Trạng thái |
|---|---|---|---|
| R-102 | Bán riêng từng bài: giá, số lượt, **thời hạn**, voucher cho học viên nội bộ. | SP §9.2 | đã có (TM C1, voucher); **trái** ở phần thời hạn (TM C8 bỏ thời hạn) |
| R-103 | **Bộ đề SP2E**, và bộ nhiều môn SPT. Từ màn thiết lập bán, chọn "Tạo bộ đề SP2E", tích các môn, chọn bài cho từng môn (màn hiện tên bài, số câu, thời lượng). Bộ có giá riêng và hiện quyền lợi theo từng môn. Mỗi bài trong bộ giữ ma trận, thời lượng, lượt và kết quả riêng. | SP §1, §9.2, §14 | **trái** (TM C1: một bài là một sản phẩm; `purchase_order` chỉ có một `exam_id`) |
| R-104 | Đổi thành phần bộ hoặc số lượt bán chỉ áp cho đơn mua mới. Mỗi lượt thật lưu đề, ngữ liệu, thứ tự, đáp án, quy tắc điểm lúc bắt đầu. | SP §9.2 | mới (phần bộ); snapshot đã có |
| R-105 | Mở thi BCA FULL thì kiểm tra thêm việc chấm được và công bố được kết quả của đúng phạm vi sản phẩm (R-54). | BCA §9 | **trái** (LT L2) |

## 2. Số liệu mẫu cấu trúc theo kỳ thi và môn (V1, đúng số BA ghi)

### 2.1 HSA (FULL: 150 câu, 195 phút, 150 điểm)

| Phần | Số câu | Thời lượng | Dạng và cách lấy |
|---|---|---|---|
| 1 Toán | 50 | 75 phút | 35 TN 4 lựa chọn + 15 điền đáp án. Lớp 3/20/27. NB/TH/VD/VDC 2/21/20/7. 1 chùm × 3 câu ở vị trí 48–50 |
| 2 Văn học – Ngôn ngữ | 50 | 60 phút | Nhóm 1–3: mỗi nhóm 5 câu đơn. Nhóm 4: 10 câu, mỗi câu một ngữ liệu. Nhóm 5: 5 chùm × 5 câu |
| 3a Khoa học (3 trong 5 môn) | 17 + 17 + 16 | 60 phút | Mỗi môn: ma trận 17 câu, 1–2 chùm × 3 câu con. Môn làm 16 câu thì bỏ 1 câu đơn |
| 3b Tiếng Anh | 50 | 60 phút | 1–10 hoàn thành câu; 11–12 đồng nghĩa; 13–14 trái nghĩa; 15–18 viết lại câu; 19–22 kết hợp câu; 23–26 hoàn thành hội thoại; 27–30 sắp xếp hội thoại; 31–35 đọc điền 1 chùm × 5; 36–40 đọc hiểu 1 chùm × 5; 41–45 đọc hiểu 1 chùm × 5; 46–50 tư duy logic 5 câu đơn |

Ma trận 17 câu của từng môn Khoa học (lớp 10/11/12; NB/TH/VD): Lý 5/6/6, 3/1/13. Hóa 2/8/7, 4/5/8. Sinh 3/5/9, 7/7/3. Sử 2/5/10, 7/8/2. Địa 2/4/11, 5/9/3.

### 2.2 QDA (FULL: 150 câu, 195 phút, 150 điểm)

| Phần | Số câu | Thời lượng | Điểm | Dạng và cách lấy |
|---|---|---|---|---|
| 1 Toán | 50 | 80 phút | 50 | 35 TN (chùm 3 câu con TN tính vào đây) + 15 điền. Lớp 5/15/30. Độ khó không mặc định |
| 2 Ngôn ngữ – Văn học | 50 | 55 phút | 50 | Khác nhóm nghĩa 5; cặp từ điền 5; từ sai 5; đoạn trích 1 câu 10; 5 chùm × 5 (thông tin – nghị luận, nghị luận, thơ, văn học thiếu nhi, truyện) |
| 3 Khoa học, QĐ01–04 | 25 + 25 | 60 phút chung | 50 | Mỗi môn: 1–2 chùm × 3; điền ≥1 (Lý, Hóa, Sinh) hoặc ≥0 (Sử, Địa); TN đơn = 25 − câu con − điền |
| 3 Tiếng Anh, QĐ05 | 50 | 60 phút | 50 | 1–10 hoàn thành câu; 11–14 đồng nghĩa/trái nghĩa; 15–18 hội thoại; 19–22 thông báo, biển báo; 23–26 viết lại câu; 27–30 kết hợp câu; 31–35 đọc điền 1 × 5; 36–40 đọc hiểu 1 × 5; 41–45 đọc hiểu 1 × 5; 46–50 suy luận 5 câu đơn |

Ma trận 25 câu (lớp 10/11/12; độ khó): Lý 6/5/14, độ khó tùy chỉnh. Hóa 6/5/14, NB/TH/VD 4/20/1. Sinh 3/6/16, tùy chỉnh. Sử 2/6/17, NB/TH/VD 8/10/7. Địa 4/4/17, tùy chỉnh.

### 2.3 BCA (FULL: 81 câu, 100 điểm, 180 phút chung)

| Phần, môn | Vị trí | Số câu | Điểm | Cách lấy | Lớp 10/11/12 | NB/TH/VD/VDC |
|---|---|---|---|---|---|---|
| I Tự luận Văn (V1 bỏ) | Phần I | 1 | 25 | 1 đề nghị luận xã hội, ≥500 chữ | – | – |
| II Toán | 1–35 | 35 | 35 | 27 đơn + chùm 3 TN + 5 trả lời ngắn | 4/7/24 | 2/12/15/6 |
| II Lịch sử | 36–45 | 10 | 10 | 7 đơn + chùm tư liệu 3 | 1/2/7 | 1/6/3/0 |
| II Tiếng Anh | 46–65 | 20 | 15 | 2 ngữ âm (1,5) + 8 từ vựng – ngữ pháp (6) + đọc điền 5 (3,75) + đọc hiểu 5 (3,75) | theo chủ đề | 3/9/8/0 |
| III Lý, CA1 | 66–80 | 15 | 15 | 12 đơn + chùm 3 | 2/3/10 | 3/4/7/1 |
| III Hóa, CA2 | 66–80 | 15 | 15 | như trên | 3/3/9 | 1/7/6/1 |
| III Sinh, CA3 | 66–80 | 15 | 15 | như trên | 2/3/10 | 4/5/4/2 |
| III Địa, CA4 | 66–80 | 15 | 15 | như trên | 2/1/12 | 2/10/3/0 |

Chỉ tiêu tham khảo cấp bài: lớp 12 ≈ 42/60, lớp 10–11 ≈ 18/60; NB+TH ≈ 24/80, VD ≈ 40/80, VDC ≈ 16/80.

### 2.4 SP2E (mỗi môn thang 10)

| Môn | Cấu trúc 2026, mặc định (SP §4, CHUNG §5.1) | Câu | Phút | Số liệu chi tiết ở mục sau của SP |
|---|---|---|---|---|
| Toán | I: 20 một đáp án + 5 nhiều đáp án. II: 5 dữ liệu tổng hợp. III: 10 trả lời ngắn | 40 | 90 | §5.1 khớp |
| Ngữ văn | I: Đọc hiểu 20 TN. II: Viết đoạn 1 câu. III: Viết bài 1 câu | 22 | 90 | §5.2: Đọc hiểu **4 văn bản, 30 câu, 6 điểm** + Viết 1 bài 4 điểm |
| Vật lí, Hóa, Sinh | I: 16 TN. II: 4 Đúng/Sai × 4. III: 8 trả lời ngắn | 28 | 60 | §6.1: **10 TN (2 điểm) + 5 Đúng/Sai (4 điểm) + 5 ghép đôi (4 điểm) = 20 câu**, 50 lệnh hỏi |
| Lịch sử | I: 26 TN. II: 6 Đúng/Sai × 4 | 32 | 60 | §7.2: **10 TN + 5 Đúng/Sai + 5 ghép đôi = 20 câu**, 50 lệnh hỏi |
| Địa lí | I: 16 TN. II: 4 Đúng/Sai × 4. III: 8 trả lời ngắn | 28 | 60 | §7.3 khớp: 40 lệnh hỏi; 4 + 4 + 2 điểm |
| Tiếng Anh | 9 nhóm, 50 TN: đọc điền câu 1–10; đồng nghĩa 11–14; kết hợp câu 15–18; điền từ 19–23; điền cụm/câu 24–28; giao tiếp 29–32; sắp xếp 33–37; đọc hiểu 1 38–42; đọc hiểu 2 43–50 | 50 | 60 | §7.5 khớp |
| GDKTPL | I: 26 TN (đề 473: 20 độc lập + 2 chùm × 3). II: 6 Đúng/Sai × 4 | 32 | 60 | §7.4 khớp: 50 lệnh hỏi |

Phân bổ tham khảo theo §6.2 (cấu trúc cũ):

| Môn | Đơn vị | Lớp 10/11/12/tổng hợp | NB/TH/VD/VDC |
|---|---|---|---|
| Lý | 50 lệnh hỏi | 0/10/40/– | 15/15/14/6 |
| Hóa | 50 lệnh hỏi | 4/5/19/22 | 25/16/9/– |
| Sinh | 20 câu | 2/4/14/– | 4/9/7/– |

Tỷ trọng tổng quan: Toán 30/30/25/15, Văn 20/30/30/20, các môn còn lại 30/30/20/20.

### 2.5 SPT V1 (đề tham khảo mã 075; bảng ở CHUNG §5.1 là số câu kể cả tự luận)

| Môn | Phần V1 theo thứ tự | Tự luận bỏ | Câu V1 | Điểm V1 | Phút (đề gốc) | Câu ở CHUNG |
|---|---|---|---|---|---|---|
| Toán | 16 TN + 2 Đúng/Sai + 4 trả lời ngắn | 3 | 22 | 7 | 90 | 25 |
| Vật lí | 12 + 3 + 4 | 2 | 19 | 7 (3+3+1) | 60 | 21 |
| Hóa học | 20 + 2 + 4 | 1 (4 ý) | 26 | 8 | 60 | 27 |
| Ngữ văn | 2 ngữ liệu × 5 TN (0,3 điểm/câu) | 2 | 10 | 3 | 90 | 12 |
| Tiếng Anh | 18 Use of English + 10 Reading + 4 Word Formation | 1 Writing | 32 | 8 | 60 | 33 |
| Sinh học | 12 + 3 + 4 | 3 | 19 | 7 | 60 | 22 |
| Lịch sử | 20 + 2 | 3 | 22 | 7 | 60 | 25 |
| Địa lí | 12 + 3 + 4 | 1 | 19 | 7 | 60 | 20 |
| Tin học | 20 + 1 Đúng/Sai chung + 1 Đúng/Sai theo định hướng | 3 | 22 | 7 | 60 | 25 |
| GDKTPL | 16 + 3 | 2 | 19 | 7 | 60 | 21 |
| Công nghệ CN | 16 + 2 + 4 | 2 | 22 | 7 | 60 | 24 |
| Công nghệ NN | 16 + 2 + 4 | 2 | 22 | 7 | 60 | 24 |

Điểm: TN 0,25; Đúng/Sai theo band 0/0,1/0,25/0,5/1; trả lời ngắn 0,25. Đã kiểm: số câu V1 cộng số câu tự luận bằng số câu ở CHUNG; điểm V1 khớp với các mức điểm trên.

### 2.6 Các chỗ BA tự mâu thuẫn

1. **SP2E Lý, Hóa, Sinh:** §4 và CHUNG ghi 28 câu (16 + 4 + 8, có trả lời ngắn). §6.1 và §6.3 (luồng "Đầy đủ cấu trúc") ghi 20 câu (10 + 5 + 5 ghép đôi), 50 lệnh hỏi. Chính §4 lại nói ghép đôi thuộc cấu trúc cũ.
2. **SP2E Lịch sử:** §4 và CHUNG ghi 32 câu (26 + 6). §7.2 ghi 20 câu (10 + 5 + 5 ghép đôi).
3. **SP2E Ngữ văn:** §4 ghi Đọc hiểu 20 câu và hai câu Viết. §5.2 ghi Đọc hiểu 4 văn bản, 30 câu, 6 điểm, và một bài Viết. Điểm của cấu trúc 2026 chưa có.
4. **BCA độ khó cấp bài:** tham khảo là NB+TH 24 / VD 40 / VDC 16. Cộng ma trận mặc định của các môn (CA1) lại ra **40 / 33 / 7** (2+12+1+6+3+9+3+4; 15+3+8+7; 6+0+0+1). Ma trận mặc định không đạt mốc tham khảo, nên nếu đặt mốc thành bắt buộc thì không cấu hình nào hợp lệ. Lớp lệch ít hơn: 41/19 so với 42/18; BA đã ghi nhận điểm này.
5. **BCA tự luận:** §1 và §4 cho thiết lập, lưu và sinh thử FULL có tự luận. Dòng cuối §4 lại ghi V1 "tạm chưa thiết lập câu ngữ văn".
6. **Đúng/Sai của SP2E:** §6.1 chấm 0,2 điểm mỗi ý, cộng dồn. SPT chấm theo band. SP2E 2026 (Địa Phần II: 4 câu, 4 điểm) chưa nói cách chấm.
7. **CHUNG còn ghi nội dung cũ:**
   - §1.4 vẫn ghi "4 dạng câu" (PBI-03 có 8 dạng).
   - §8.4 ghi "tỷ lệ tính theo số câu", trái với SP §3.1 (câu, lệnh hỏi, điểm).
   - §5.1 ghi Khoa học HSA là "ba trong năm môn theo tổ hợp được phép", trong khi HSA §9 cho đủ 10 tổ hợp.
   - §5.1 ghi Tin SPT 25 câu, trong khi V1 là 22 câu.
8. **Thứ tự nhóm Văn:** HSA và QDA đổi chỗ nhóm 2 và nhóm 3. Đây có thể là chủ ý, nhưng cần BA xác nhận vì dùng chung danh mục TASK_TYPE.
9. **HSA quy tắc 16 câu:** bỏ một câu sau khi đã sinh nên phân bố lớp và độ khó của môn 16 câu không còn đúng ma trận. Điều này trái với nguyên tắc CHUNG §1.4 "không tự giảm số câu… để sinh bằng được một đề", dù BA đã ghi rõ việc giảm 1 câu.

## 3. Câu hỏi còn mở cho BA (đã gộp 10 câu của bản phân tích, bỏ trùng)

| # | Câu hỏi | Đề xuất mặc định |
|---|---|---|
| Q-01 | (gap 1) SP2E dùng số nào trong V1: 28 hay 20 câu (Lý, Hóa, Sinh), 32 hay 20 (Sử), Văn Đọc hiểu 20 hay 30? | Mẫu mặc định theo cấu trúc 2026 (SP §4). Số của §5.2, §6, §7.2 đưa vào một mẫu riêng "Đề minh họa cũ" để chọn ở bước 2. Văn V1 dùng 20 câu Đọc hiểu, thang bằng tổng điểm câu (RAW) tới khi có điểm. |
| Q-02 | (gap 2) Chọn "Đầy đủ cấu trúc" hay "Bài ôn" ở bước 1 (SP2E) hay bước 2 (HSA, QDA, BCA)? | Một trường `buildMode` lưu ở bài, chọn ở bước 1 cho mọi kỳ thi (vì quyết định số câu và thời lượng). Đổi ở bước 2 thì nạp lại sau khi xác nhận. |
| Q-03 | (gap 3) Cùng một câu có điểm 1 / 0,75 / 0,3 / 0,25 / 0,2 tùy kỳ thi. Soạn câu riêng cho từng kỳ, hay quy điểm theo cấu trúc? | Điểm ở câu chỉ là mặc định. Dòng hoặc nhóm trong cấu trúc có "điểm áp dụng mỗi câu" ghi đè, được chụp vào phiên bản và lượt. Thay cách hiện tại là dùng điểm làm bộ lọc nguồn (`requiredPointsPerQuestion`). |
| Q-04 | (gap 4) Bỏ 1 câu ở môn 16 câu có được làm vỡ chỉ tiêu chủ đề hay chỉ tiêu bắt buộc không? Sử và Địa có ràng buộc riêng không? | Ưu tiên bỏ câu đơn không nằm trong chỉ tiêu chủ đề bắt buộc. Nếu không có câu như vậy thì bỏ ngẫu nhiên một câu đơn bất kỳ. Sử và Địa không có ràng buộc thêm. |
| Q-05 | (gap 5) Đảo chùm trong nhóm 5 Văn là công tắc theo bài? Mặc định bật hay tắt? Quan hệ với quyết định 17/11 "luôn đảo, không có cờ"? | Thứ tự nhóm theo mẫu (`groupOrder` phải khôi phục). Trong nhóm luôn đảo (F22), trừ nhóm có điều kiện theo vị trí hoặc theo từng chùm (QDA nhóm 5 có loại ngữ liệu theo chùm; SP2E Anh "Theo vị trí"; các vị trí của BCA): những nhóm này giữ thứ tự. Không có công tắc trên màn trong V1. |
| Q-06 | (gap 6) Ai nhập thuộc tính nhánh và thứ tự môn của mã trong Global List? Thêm mã mới thì bài có phải công bố lại không? | Người quản trị danh mục nhập ở Global List; mỗi lần sửa mã thì tăng phiên bản. Bài phải "Cập nhật công bố" sau khi kiểm tra mã mới; trước đó mã mới không bán và không cho chọn. |
| Q-07 | (gap 7) Ai đánh dấu một phân bố là bắt buộc hay tham khảo? | Mẫu đặt mặc định: các số BA ghi "tham khảo", "ước lượng" hoặc "quan sát" là tham khảo; ma trận khởi tạo của môn là bắt buộc. Quản trị viên bật/tắt từng phân bố. Chỉ tiêu cấp bài luôn là tham khảo trong V1. |
| Q-08 | (gap 8) V1 có bán BCA FULL không? PBI-12 (chấm tự luận) còn ở đợt 3 không? | V1 không mở BCA FULL. Bán bài theo phần TN bắt buộc (60), TN tự chọn (15) và bài theo môn. PBI-12 lùi sau V1; LT L2 sửa theo. |
| Q-09 | (gap 9) V1 có cần độ khó cho từng ý Đúng/Sai và chỉ tiêu theo lệnh hỏi không? | V1 chỉ hiện số lệnh hỏi để đối chiếu. Hạn ngạch tính theo câu; độ khó của câu Đúng/Sai lấy ở cấp câu. Các bảng theo lệnh hỏi (Lý, Hóa, Địa) chỉ là tham khảo. |
| Q-10 | (gap 10) Định hướng Tin học chọn trước hay trong khi làm bài? | Theo SP §12.4: chọn ở màn chuẩn bị, chỉ sinh nhánh đã chọn, mẫu số 22. LT F4 và §6.2 sửa theo. Áp tương tự cho THPTQG Tin nếu BA đồng ý. |
| Q-11 | BCA: mốc độ khó cấp bài 30/50/20 không đạt được với ma trận mặc định của các môn (ra 50/41/9). Bên nào thắng? | Ma trận của môn là bắt buộc; mốc cấp bài chỉ cảnh báo. Nhờ BA sửa một trong hai bộ số. |
| Q-12 | HSA theo phần Toán và HSA theo môn Toán khác nhau thế nào? Theo môn được chọn những môn nào? | Theo phần là nguyên một phần của đề (Toán 50 câu/75 phút; Khoa học chọn 3 trong 5). Theo môn là một môn bất kỳ (Toán, Văn, Anh, 5 môn Khoa học), khởi tạo bằng cấu trúc môn hoặc bài ôn. |
| Q-13 | QDA theo phần Khoa học: học viên chọn mã QĐ01–04 rồi làm 50 câu trong 60 phút? Theo phần Tiếng Anh thì là QĐ05? | Có. Mã chọn ở màn chuẩn bị, giống BCA phần tự chọn. |
| Q-14 | Bài theo phần của SP2E/SPT: giữ thang điểm của phần (Địa II là 4 điểm) hay quy về 10? Quy theo cách nào? | Mặc định giữ thang của phần (RAW). Quy về 10 dùng `LINEAR` (D11), quản trị viên xác nhận và xem trước. |
| Q-15 | Điểm từng dạng và cách chấm Đúng/Sai của SP2E 2026 (Toán, Lý, Hóa, Sinh, Sử, GDKTPL, Anh)? §6.1 cộng 0,2 mỗi ý; SPT dùng band. | Đúng/Sai SP2E 2026 dùng band của THPT (0,1/0,25/0,5/1); TN 0,25; trả lời ngắn 0,25. Số còn thiếu lấy theo `2026-11-17-de-test-gia-dinh.md`. |
| Q-16 | Đọc điền **chọn phương án** (HSA/QDA 31–35, BCA 56–60, SPT 2 đoạn × 6, SP2E nhóm 4–5) dùng dạng câu nào? CLOZE hiện chỉ cho tự nhập. | Bộ câu hỏi có ngữ liệu, câu con là Một đáp án và gắn với chỗ trống. CLOZE giữ cho tự nhập (Word Formation). |
| Q-17 | MAPID "Theo vị trí" (SP2E Anh, câu 48–50) có áp cho câu đơn không? Đảo câu trong nhóm sẽ phá vị trí. | V1 chỉ hỗ trợ theo vị trí bên trong một chùm (câu con giữ thứ tự). Nhóm câu đơn chỉ dùng "Theo nhóm". |
| Q-18 | Chỉ tiêu chủ đề "cùng cấp": có cho đặt cùng lúc chỉ tiêu ở chủ đề cha và chủ đề con không? Một câu khớp hai chỉ tiêu thì gán vào chỉ tiêu nào? | V1 không cho chỉ tiêu lồng nhau (cha và con cùng lúc). Bộ giải tự gán câu vào một chỉ tiêu sao cho mọi chỉ tiêu đều đạt; bảng Yêu cầu / Thực tế hiện chỗ đã gán. |
| Q-19 | "Kiến thức tổng hợp" của Hóa SP2E có phải là lớp "Liên lớp" (`GRADE_MIXED`) không? | Có, ánh xạ sang `GRADE_MIXED`. |
| Q-20 | Bộ đề: áp cho những kỳ thi nào (SP2E, SPT, các kỳ khác)? Một đơn mua bộ cấp quyền cho từng bài thế nào? Có bán bộ có giảm giá so với mua lẻ không? | Sản phẩm kiểu "bộ" gồm nhiều bài; một đơn cấp một `entitlement` cho mỗi bài với số lượt của bộ. V1 chỉ có SP2E và SPT. TM C1 và `purchase_order` phải mở rộng (đợt sau 2B). |
| Q-21 | Bán riêng ghi có "thời hạn", trong khi TM C8 đã bỏ thời hạn. | Giữ C8: không có thời hạn. Nhờ BA bỏ chữ này ở SP §9.2. |
| Q-22 | BCA Anh "lấy theo chủ đề": nghĩa là không có chỉ tiêu lớp cho Anh BCA (cũng như Anh SP2E/SPT)? | Đúng. Anh của mọi kỳ thi không hiện chỉ tiêu lớp. |
| Q-23 | V-ACT và THPTQG chưa có tài liệu cấu trúc chi tiết. Giữ mẫu cũ (D26)? | Giữ mẫu cũ; chuyển sang mô hình theo môn khi có tài liệu. |
| Q-24 | "Làm thử như học viên" có ghi lượt hay kết quả, có vào sổ câu đã dùng không? | Không tạo lượt và không có kết quả học tập. Câu ghi sổ `PREVIEW` như xem trước (D18). |
