# TASK — Thi 360

> Cập nhật: 2026-11-13 · Trạng thái sống nằm trên UniAI (project Thi 360). File này là bản chụp để đọc nhanh trong repo; khi lệch thì UniAI đúng.
> Chi tiết từng task (hợp đồng, thứ tự sóng, trọng số): `docs/plans/2026-11-13-base-port-pbi-01-02.md`. Phụ thuộc trong bảng dưới theo Phần C của plan.

## Đợt 1 — Port nền + PBI-01 + PBI-02

Issue cha: **UNI-2537** · Thi 360 · Port nền + PBI-01 Tài khoản & phân quyền + PBI-02 Danh mục dùng chung

| Task | Issue | Việc | Phụ thuộc | Trạng thái |
|---|---|---|---|---|
| T01 | UNI-2538 | Nền · Khởi tạo monorepo, CI, môi trường dev (Docker Compose) | — | in_review (đã merge vào `develop` ngày 2026-11-13; người chuyển `done`) |
| T02 | UNI-2539 | Nền · Khung backend modular monolith (Spring Boot, Flyway, OpenAPI, lỗi chuẩn) | T01 | in_review (đã merge vào `integration/dot-1` qua PR #4 ngày 2026-11-13) |
| T03 | UNI-2540 | Nền · Xác thực: Keycloak, đăng nhập/refresh/đăng xuất, phiên, chặn đăng nhập sai | T02 | in_review (đã merge vào `integration/dot-1` qua PR #6 ngày 2026-11-13) |
| T04 | UNI-2541 | PBI-01 · Phân quyền: bảng quyền, vai trò gán quyền, gán vai trò và quyền cho user, người dùng theo quyền, gán hàng loạt (API + engine) | T03 | in_review (đã merge vào `integration/dot-1` qua PR #9 ngày 2026-11-13) |
| T05 | UNI-2542 | Nền · Nhật ký thao tác: bảng chỉ ghi thêm, outbox, worker, API tra cứu | T02 | in_review (đã merge vào `integration/dot-1` qua PR #8 ngày 2026-11-13) |
| T06 | UNI-2543 | Nền · Design system (`packages/ui`) và khung web admin | T01 | in_review (đã merge vào `integration/dot-1` qua PR #5 ngày 2026-11-13) |
| T06b | UNI-2559 | Nền · Tương phản chữ/nền chế độ sáng >= 4,5:1 (con của UNI-2543, ngoài điểm plan) | T06 | in_review (đã merge vào `integration/dot-1` qua PR #7 ngày 2026-11-13) |
| T07 | UNI-2544 | PBI-01 · API quản lý tài khoản | T03, T04, T05 | in_review (đã merge vào `integration/dot-1` qua PR #12 ngày 2026-11-14) |
| T08 | UNI-2545 | PBI-01 · UI phân quyền: một menu bốn tab (Vai trò, Quyền, Người dùng theo quyền, Gán hàng loạt) | T04, T06, T18 | in_review (đã merge vào integration/dot-1, PR #19) |
| T09 | UNI-2546 | PBI-01 · UI quản lý tài khoản | T06, T07, T18 | in_review (đã merge vào integration/dot-1, PR #16) |
| T10 | UNI-2547 | PBI-01 · Lớp học: API + UI | T07, T09 | in_review (đã merge vào integration/dot-1: API PR #15, UI PR #21) |
| T11 | UNI-2548 | PBI-01 · Cấp tài khoản hàng loạt | T07, T10 | in_review (đã merge vào integration/dot-1: backend PR #17, UI PR #22) |
| T12 | UNI-2549 | PBI-01 · Hồ sơ học viên (khung) | T09, T10 | in_review (đã merge vào integration/dot-1, PR #18) |
| T13 | UNI-2550 | PBI-02 · Danh mục: schema, dữ liệu mặc định, API nhóm và giá trị, API lựa chọn | T04, T05 | in_review (đã merge vào `integration/dot-1` qua PR #10 ngày 2026-11-14) |
| T14 | UNI-2551 | PBI-02 · Mã tổ hợp có phiên bản, nơi đang dùng, quy tắc ngừng và xóa | T13 | in_review (đã merge vào `integration/dot-1` qua PR #14 ngày 2026-11-14) |
| T15 | UNI-2552 | PBI-02 · UI Cấu hình chung | T06, T13, T14, T18 | in_review (đã merge vào integration/dot-1: PR #23) |
| T17 | UNI-2555 | Nền · Tùy chỉnh giao diện toàn hệ thống (mục trong menu chính, port từ reap) | T04, T05, T06 | in_review (đã merge vào `integration/dot-1` qua PR #11 ngày 2026-11-14) |
| T18 | UNI-2556 | Nền · Đăng nhập một nơi (SSO) và điều hướng theo vai trò | T03, T04, T06 | in_review (đã merge vào `integration/dot-1` qua PR #13 ngày 2026-11-14) |
| T19 | UNI-2561 | Nền · Đa ngôn ngữ (tiếng Việt mặc định, thêm tiếng Anh) và chế độ sáng/tối theo từng người; hai nút ở thanh trên cùng | T17, T18 | in_review (đã merge vào integration/dot-1, PR #20) |
| T16 | UNI-2553 | Kiểm thử nghiệm thu nền, PBI-01 và kiểm thử bảo mật gọi thẳng API | T02–T15, T17, T18, T19 | in_review (đã merge vào integration/dot-1: PR #24) |

### Việc phát sinh sau đợt 1 (2026-11-14)

Không thuộc điểm của plan đợt 1; mỗi việc một issue con của UNI-2537 và một PR vào `develop`.

| Issue | Việc | PR | Trạng thái |
|---|---|---|---|
| UNI-2574 | Lỗi · Hết hạn access token: trang bị tải lại hoặc về trang đăng nhập thay vì tự làm mới phiên | #26 | in_review |
| UNI-2577 | Lỗi · Danh sách bị nhảy khi đổi bộ lọc | #28 | in_review |
| UNI-2578 | Hộp thoại Tạo tài khoản: bố cục hai cột, hiện đủ trong một khung | #27 | in_review |
| UNI-2576 | Đổi đường dẫn trang của hai web sang tiếng Anh (chồng lên ba PR trên) | #29 | in_review |
| UNI-2598 | Danh sách tài khoản: thêm cột Lớp theo bản vẽ | #38, #39 | in_review |

## Đợt 2 — Mockup web admin PBI-03 + PBI-04 (dữ liệu mẫu)

Issue cha: **UNI-2590** · Mockup web admin PBI-03 Ngân hàng câu hỏi và PBI-04 Bài thi, chạy trên dữ liệu mẫu, chưa nối backend. Bản vẽ: `docs/design/dot-2/`. Quy ước: `DESIGN.md` mục 5.14. Bật bằng `NEXT_PUBLIC_THI360_CONTENT_MOCK` (dev mặc định bật, bản build mặc định tắt).

| Mã | Issue | Việc | Trạng thái |
|---|---|---|---|
| Nền | UNI-2591 | Lớp dữ liệu mẫu, menu Nội dung, kiểu dữ liệu, khung trang soạn bài thi, `ChonKienThuc` | in_review |
| A | UNI-2592 | `/questions`: danh sách, bộ lọc, chọn kiến thức, kiểm tra sẵn sàng, ngừng sử dụng | in_review |
| B | UNI-2593 | Soạn câu hỏi theo 6 dạng, chi tiết, nơi đang dùng, lịch sử phiên bản | in_review |
| C | UNI-2594 | `/questions/import`: nhập câu hỏi từ tệp, bốn bước | in_review |
| D | UNI-2595 | `/exams`, trang Tạo bài thi `/exams/new`, Thông tin chung, Cấu trúc và tổ hợp | in_review |
| E | UNI-2596 | Kiểm tra, xem trước, phiên bản, công bố bài thi | in_review |

Khi có backend: chỉ thay thân các hàm trong `features/<x>/api.ts` (và `*-api.ts`), giữ chữ ký; bỏ `mockOnly` ở menu và `notFound()` theo cờ ở trang.

### Mở rộng 7 kỳ thi và chấm tự luận (dữ liệu mẫu)

Issue cha: **UNI-2599** (con của UNI-2590) · HSA, QDA, BCA, V-ACT, SPT, SP2E, THPTQG; dạng câu Tự luận chấm tay; quy tắc chấm trên câu hỏi. Đặc tả: `docs/specs/2026-11-16-mockup-7-ky-thi.md`.

| Mã | Issue | Việc | Trạng thái |
|---|---|---|---|
| Nền | UNI-2600 | Danh mục mẫu 7 kỳ thi, mẫu cấu trúc, kiểu dữ liệu, dạng Tự luận, quy tắc chấm; seed đủ 12 môn THPT | in_review |
| G | UNI-2601 | Tạo bài thi, danh sách, Thông tin chung cho 7 kỳ thi | in_review |
| H | UNI-2602 | Cấu trúc, ma trận, kiểm tra cấu hình, xem trước, phiên bản; bài mẫu BCA, V-ACT, THPTQG Tin học | in_review |
| I | UNI-2603 | Ngân hàng câu hỏi: Tự luận, bảng điểm Đúng/Sai, quy tắc nhiều đáp án, năng lực, nhập tệp | in_review |
| J | UNI-2604 | Hàng chờ chấm tự luận `/grading` | in_review |

### Backend PBI-03, PBI-04

Issue cha: **UNI-2606** · Thi 360 · Đợt 2 · Backend PBI-03 + PBI-04 và nối web admin. Đặc tả UNI-2605 (`docs/specs/2026-11-16-be-pbi-03-04.md`), plan `docs/plans/2026-11-16-be-pbi-03-04.md` (hợp đồng ở Phần D, sóng ở Phần C). Nhánh tích hợp `integration/dot-2`.

| Task | Issue | Việc | Phụ thuộc | Điểm | Trạng thái |
|---|---|---|---|---|---|
| BE-1 | UNI-2607 | Khung module, quyền, danh mục `COMPETENCY`, idempotency, `content-reference` | — | 8 | in_review (đã merge vào `integration/dot-2` qua PR #46 ngày 2026-11-16) |
| FE-3a | UNI-2608 | Kiểu dữ liệu và dữ liệu mẫu theo spec mục 9.2 | — | 6 | in_review (merge PR #49) |
| BE-2a | UNI-2609 | Câu hỏi: lưu, 8 dạng, trạng thái, revision, tìm kiếm, lọc HTML | BE-1 | 10 | in_review (merge PR #48, #51) |
| BE-4 | UNI-2610 | Bài thi: hồ sơ kỳ thi, mẫu, tạo, cấu trúc, sao chép | BE-1 | 8 | in_review (merge PR #52) |
| BE-5a | UNI-2611 | `PaperSolver` (thuần thuật toán) | BE-1 | 9 | in_review (merge PR #50) |
| FE-3b | UNI-2612 | Giao diện ngân hàng câu hỏi theo UniNote mới | FE-3a | 7 | in_review (merge PR #54) |
| FE-3c | UNI-2613 | Giao diện bài thi theo UniNote mới | FE-3a | 7 | in_review (merge PR #56) |
| BE-2b | UNI-2614 | Ảnh và tệp đính kèm | BE-2a | 5 | in_review (merge PR #53) |
| BE-3 | UNI-2615 | Nhập câu hỏi từ tệp | BE-2a | 7 | in_review (merge PR #55) |
| BE-5b | UNI-2616 | Nguồn câu hỏi, kiểm tra cấu hình, hiệu lực, xem trước | BE-2a, BE-4, BE-5a | 7 | in_review (merge PR #57) |
| FE-4 | UNI-2617 | Trình soạn HTML (TipTap) và KaTeX, nối vào các màn | FE-3b, FE-3c | 5 | in_review (merge PR #58) |
| BE-6 | UNI-2618 | Phiên bản, công bố, ngừng bán, nơi đang dùng | BE-5b | 7 | in_review (merge PR #59) |
| FE-1 | UNI-2619 | Nối API ngân hàng câu hỏi, nhập tệp; chuyển test sang giả lập client | FE-4, BE-2a, BE-2b, BE-3 | 5 | in_review (merge PR #60) |
| FE-2 | UNI-2620 | Nối API bài thi; chuyển test sang giả lập client | FE-3c, FE-4, BE-4, BE-5b, BE-6 | 6 | in_review (merge PR #61) |
| QA | UNI-2621 | E2E, xóa mock PBI-03/04, kiểm thử bảo mật, tài liệu | tất cả | 3 | in_review (merge PR #62) |
| Việc tồn | UNI-2634 | Trả lời BA 17/11 (chùm Toán HSA một đáp án, luôn đảo câu), bộ đề test giả định, lỗi còn lại | QA | — | in_review (merge PR #64 vào `develop`) |

Sóng: 1 BE-1 · FE-3a → 2 BE-2a · BE-4 · BE-5a · FE-3b · FE-3c → 3 BE-2b · BE-3 · BE-5b · FE-4 → 4 BE-6 · FE-1 → 5 FE-2 → 6 QA. `integration/dot-2` vào `develop` qua PR #63 (`8986f0f`) ngày 2026-11-17; việc tồn UNI-2634 làm thẳng trên `develop`.

### Trình soạn nội dung đầy đủ (UNI-2638)

Issue cha: **UNI-2638**. Đặc tả `docs/specs/2026-11-17-trinh-soan-noi-dung.md`, plan `docs/plans/2026-11-17-trinh-soan-noi-dung.md`, đặc tả bổ sung `docs/specs/2026-11-17-giu-thu-tu-phuong-an.md`. Nhánh tích hợp `integration/editor`.

| Task | Issue | Việc | Điểm | Trạng thái |
|---|---|---|---|---|
| ED-0a | UNI-2639 | Backend: bộ lọc HTML, luật cấu trúc, quy tắc Sẵn sàng, `blankNumberStart` | 12 | in_review (merge PR #69) |
| ED-0b | UNI-2640 | Web nền: node TipTap, `RichTextView`, thanh công cụ, registry | 18 | in_review (merge PR #68) |
| ED-1 | UNI-2641 | Menu `/`, `$…$`, khung công thức đang chọn, hộp công thức toán (mathlive) | 18 | in_review (merge PR #73) |
| ED-2 | UNI-2642 | Hóa học, Ký hiệu, Sơ đồ lai | 10 | in_review (merge PR #76) |
| ED-3 | UNI-2643 | Bảng số liệu, Hình, Đoạn trích | 12 | in_review (merge PR #74) |
| ED-4 | UNI-2644 | Gạch chân đánh nhãn, chỗ trống đánh số | 12 | in_review (merge PR #75) |
| ED-5 | UNI-2645 | Dán từ Word (OMML sang LaTeX, bốn bộ tự sửa) | 12 | in_review (merge PR #72) |
| QA | UNI-2646 | E2E 7 luồng, kích thước gói, việc để lại | 6 | in_review (merge PR #77) |
| Cờ thứ tự | UNI-2648 | Cờ "Giữ thứ tự phương án", cảnh báo `OPTION_REFERS_TO_OTHERS`, cột nhập | — | in_review (merge PR #79) |

### Thương mại và lượt thi (PBI-05 đến PBI-13)

| Issue | Việc | Trạng thái |
|---|---|---|
| UNI-2622 | Đặc tả thương mại `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md` (trang giới thiệu, danh mục, đăng ký, email, khuyến mãi, voucher, đơn hàng, VNPAY, hoàn tiền, Bài thi của tôi) và đặc tả lượt thi `docs/specs/2026-11-16-luot-thi-pbi-10-13.md` (làm bài 7 kỳ thi, lưu và khôi phục, chấm, kết quả, lịch sử). Plan triển khai `docs/plans/2026-11-17-thuong-mai-luot-thi-pbi-05-13.md` (36 task, 103 điểm, sửa theo đợt 2B: cổng G0b, bộ đề CM-6, FB), đã qua 2 lượt review và các lượt review theo 2B; M.Trường duyệt 2026-11-17; chưa cook | in_progress |

### Đợt 2B · Cập nhật bài thi và ma trận theo UniNote PBI-04 2026-11-17

| Issue | Việc | Trạng thái |
|---|---|---|
| UNI-2647 | Đặc tả `docs/specs/2026-11-17-pbi04-dot-2b.md`, bảng yêu cầu `docs/specs/2026-11-17-pbi04-dot-2b-yeu-cau.md`, plan `docs/plans/2026-11-17-pbi04-dot-2b.md` (17 task, 100 điểm). Đã qua 4 lượt review của Fable; M.Trường chốt K6 đến K15 và duyệt 2026-11-17; PR #70 đã merge. Bản vẽ G2: PR #78 (`docs/design/dot-2b/`). Cook ở một đợt chạy riêng sau khi trình soạn vào `develop` | in_progress |
| UNI-2649 | Issue cha đợt 2B (cook từ 2026-11-17, nhánh `integration/dot-2b` từ `develop` 23c64377, Orca Run `run_89ebffeca5a8`) | in_progress |

Sub-issue theo plan A5 (17 task, 100 điểm):

| Task | Issue | Điểm | Sóng | Task | Issue | Điểm | Sóng |
|---|---|---|---|---|---|---|---|
| BE-1 | UNI-2650 | 10 | 1 | BE-9 | UNI-2660 | 4 | 4 |
| BE-2 | UNI-2651 | 4 | 1 | FE-1 | UNI-2661 | 6 | 2 |
| BE-3 | UNI-2652 | 4 | 1 | FE-2 | UNI-2662 | 8 | 2 |
| BE-10 | UNI-2653 | 3 | 2 | FE-3a | UNI-2663 | 8 | 3 |
| BE-4a | UNI-2654 | 6 | 2 | FE-3b | UNI-2664 | 9 | 4 |
| BE-4b | UNI-2655 | 4 | 3 | FE-4 | UNI-2665 | 7 | 3 |
| BE-5 | UNI-2656 | 10 | 3 | QA | UNI-2666 | 9 | 5 |
| BE-8 | UNI-2657 | 5 | 3 | | | | |
| BE-6 | UNI-2658 | 5 | 2 | | | | |
| BE-7 | UNI-2659 | 6 | 2 | | | | |

Bổ sung V-ACT, THPTQG (UNI-2671, kế hoạch `docs/plans/2026-11-18-pbi04-dot-2b-bo-sung-vact-thpt.md`). Tổng đợt tăng từ 100 lên 127 điểm.

| Task | Issue | Điểm | Nội dung |
|---|---|---|---|
| BE-11 | UNI-2672 | 2 | Hợp đồng mở rộng: cờ mẫu, `percent`, `everyChildInScope` |
| BE-12 | UNI-2673 | 5 | Bộ giải: chùm cỡ linh hoạt, mọi câu con thuộc chủ đề |
| BE-13 | UNI-2674 | 6 | Mẫu THPTQG, V-ACT, phạm vi mới, bài ôn |
| BE-14 | UNI-2675 | 2 | Nhãn và dữ liệu câu hỏi mẫu |
| BE-15 | UNI-2676 | 3 | Kiểm tra: chặn công bố, khóa số câu, chờ mapping |
| FE-5 | UNI-2677 | 3 | Web admin: bước 1 cho V-ACT, THPTQG |
| G2b | UNI-2678 | 0 | Bản vẽ bổ sung (cổng thiết kế) |

Bổ sung H-SCA, V-SAT (UNI-2687, kế hoạch `docs/plans/2026-11-18-pbi04-dot-2b-bo-sung-hsca-vsat.md`). Tổng đợt tăng từ 127 lên 160 điểm.

| Task | Issue | Điểm | Nội dung |
|---|---|---|---|
| BE-16 | UNI-2688 | 3 | Hợp đồng mở rộng, mã kỳ thi, nhãn |
| BE-17 | UNI-2689 | 4 | Nguồn câu: audio, dạng câu con, số phương án, chiều điểm |
| BE-18 | UNI-2690 | 6 | Mẫu H-SCA, V-SAT, hồ sơ, phạm vi |
| BE-19 | UNI-2691 | 3 | Dữ liệu câu mẫu, có audio |
| BE-20 | UNI-2692 | 3 | Kiểm tra: khóa dòng, tệp audio, thang, đối chiếu theo lệnh |
| BE-21 | UNI-2693 | 3 | Tiếng Anh V-SAT: bài đọc dùng chung |
| FE-6 | UNI-2694 | 3 | Web admin: bước 1 cho H-SCA, V-SAT |
| FE-7 | UNI-2695 | 4 | Web admin: bước 2, audio, khóa dòng, ví dụ chấm |
| FE-8 | UNI-2696 | 2 | Web admin: kiểm tra và sinh thử |
| G2c | UNI-2697 | 0 | Bản vẽ bổ sung (cổng thiết kế) |

Đã merge vào `integration/dot-2b` (issue chuyển `in_review`, chờ người duyệt):

- BE-3 UNI-2652, PR #81 (`fef09eb7`), 2026-11-18.
- BE-1 UNI-2650, PR #82 (`829c51fa`), 2026-11-18.
- BE-2 UNI-2651, PR #83 (`ce3a53e9`), 2026-11-18.
- BE-4a UNI-2654, PR #84 (`f2d19d44`), 2026-11-18.
- BE-6 UNI-2658, PR #85 (`93cd54f6`), 2026-11-18.
- BE-7 UNI-2659, PR #86 (`a378a74c`), 2026-11-18.
- FE-1 UNI-2661, PR #87 (`5adff8ce`), 2026-11-18.
- BE-4b UNI-2655, PR #88 (`d7424481`), 2026-11-18.
- BE-10 UNI-2653, PR #89 (`750133d7`), 2026-11-18.
- BE-8 UNI-2657, PR #90 (`98a42e93`), 2026-11-18.
- BE-5 UNI-2656, PR #91 (`89c040e9`), 2026-11-18.
- G2b UNI-2678, PR #92 (`d30122ea`), 2026-11-18.
- FE-2 UNI-2662, PR #93 (`f8911d89`), 2026-11-18.
- BE-11 UNI-2672, PR #94 (`2df2b9d6`), 2026-11-18.
- BE-9 UNI-2660, PR #95 (`8aaa964d`), 2026-11-18.
- FE-3a UNI-2663, PR #97 (`d5b7ea05`), phần gốc, 2026-11-18.
- BE-14 UNI-2675, PR #96 (`8bb2a79a`), 2026-11-18.
- BE-12 UNI-2673, PR #98 (`e7c92466`), 2026-11-18.
- FE-4 UNI-2665, PR #99 (`567e9cb7`), 2026-11-18, phần gốc; phần bổ sung làm sau BE-15.
- BE-13 UNI-2674, PR #100 (`2c0684f2`), 2026-11-18.
- FE-3a phần thêm UNI-2663, PR #101 (`2f014645`), 2026-11-18.
- FE-5 UNI-2677, PR #102 (`379549a0`), 2026-11-18.
- FE-3b UNI-2664, PR #103 (`c5aecbfd`), 2026-11-18.
- BE-15 UNI-2676, PR #104 (`5660ae73`), 2026-11-18.
- FE-4 phần thêm UNI-2665, PR #105 (`fed64900`), 2026-11-18.
- FIX-QA UNI-2686, PR #106 (`b82b3bb0`), 2026-11-18.
- G2c UNI-2697, PR #107 (`2a7dfbd1`), 2026-11-18.
- BE-16 UNI-2688, PR #108 (`bb622ceb`), 2026-11-18.
- FE-6 UNI-2694, PR #109 (`b682cc16`), 2026-11-19.
- BE-17 UNI-2689, PR #110 (`70287be8`), 2026-11-19.
- BE-19 UNI-2691, PR #111 (`6e6445ea`), 2026-11-19.
- BE-20 UNI-2692, PR #113 (`f610e74e`), 2026-11-19.

### Thứ tự làm

```
Sóng 1:  T02 · T06
Sóng 2:  T03 · T05
Sóng 3:  T04
Sóng 4:  T18 · T07 · T17
Sóng 5:  T08 · T09 · T10 (API)
Sóng 6:  T10 (UI) · T11 · T12
Sóng 7:  T16
Đa ngôn ngữ: T19 (sau T17, T18)
```

**Danh mục mở lại (2026-11-14, M.Trường):** T13, T14, T15 (PBI-02 Danh mục dùng chung) từng hoãn ngày 2026-11-13, nay làm trong đợt chạy này. Chi tiết: plan mục A7.

## Backlog

| Issue | Việc | Ghi chú |
|---|---|---|
| UNI-2554 | Đăng nhập Google và gộp tài khoản theo email | Quy tắc đã chốt, xem spec mục 4.5. Cần báo BA cập nhật SRS |

## Các đợt sau (chưa tạo issue)

| Đợt | PBI | Ghi chú |
|---|---|---|
| 2 · Nội dung | 03 Ngân hàng câu hỏi, 04 Bài thi và ma trận | Mockup giao diện ở UNI-2590, UNI-2599; đặc tả backend ở UNI-2605; triển khai ở UNI-2606 (mục Đợt 2 ở trên) |
| 3 · Thi | 10–13 Làm bài, lưu bài, chấm, kết quả | Port một phần từ `lms-exam` |
| 4 · Thương mại | 05, 06, 07–09 | Port nhiều từ `lms-exam/commercial` và `exam-portal` |
| 5 · AI | 14–18 | Làm mới |
| 6 · Mobile | 19–23 | React Native |
| 7 · Vận hành | 24–26 | Port `commercial/support`, phần xuất báo cáo |
