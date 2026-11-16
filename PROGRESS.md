# PROGRESS — Thi 360

> Cập nhật mỗi lần dừng việc. Trạng thái issue sống nằm trên UniAI; file này ghi **đã làm gì, vướng gì, làm gì tiếp**.

## Trạng thái hiện tại (2026-11-16)

**Giai đoạn:** đợt 1 (nền, PBI-01, PBI-02) đã merge vào `develop` ngày 2026-11-14 (PR #25, `14f983c`; 105/105 điểm của plan). Đợt 2 đã có mockup giao diện PBI-03, PBI-04 trên dữ liệu mẫu (UNI-2590), mở rộng cho 7 kỳ thi và chấm tự luận (UNI-2599); đặc tả backend đã viết (UNI-2605); triển khai backend đang chạy ở UNI-2606 từ 2026-11-16. Các việc phát sinh sau khi dùng thử bản demo: mỗi việc một issue và một PR vào `develop`. Các mục `T…` bên dưới là nhật ký của đợt 1, đường dẫn trang trong đó là tên cũ (xem bảng đổi tên ở mục "Sau đợt 1").

### Sau đợt 1 (2026-11-14)

- **Bản demo tạm** chạy trên VPS từ `develop`: web học viên `https://thi360.zone17.click`, web admin `https://admin.thi360.zone17.click` (profile `api,worker,dev`, có tài khoản mẫu). Cách vận hành nằm trên VPS ở `~/deploy/thi360-demo/README.md`, ngoài kho mã.
- **UNI-2574** (PR #26): access token hết hạn (5 phút) làm trang tải lại hoặc bị đưa về trang đăng nhập. Sửa: proxy `/api/[...path]` và middleware trang của hai web tự làm mới phiên ở phía máy chủ; trang đăng nhập quay lại `callbackUrl` hợp lệ khi đã có phiên. Triển khai sau nginx cần `proxy_buffer_size 16k` (README, mục chạy trên máy chủ Keycloak thật); cách làm mới dựa vào việc realm không bật "Revoke Refresh Token".
- **UNI-2577** (PR #28): danh sách bị nhảy khi đổi bộ lọc. Sửa: bảng giữ dữ liệu cũ trong lúc tải lại (`Table.refreshing`, `keepPreviousWhileSameScope`), giữ chỗ cho thanh cuộn dọc.
- **UNI-2578** (PR #27): hộp thoại Tạo tài khoản bố cục hai cột, hiện đủ trong một khung (cỡ `xl` của `DialogContent`, `PasswordField.rulesColumns`).
- **UNI-2576** (PR #29): đổi đường dẫn trang sang tiếng Anh. Chữ hiển thị không đổi; không có chuyển hướng từ đường dẫn cũ.

  | Web | Cũ | Mới |
  |---|---|---|
  | học viên | `/dang-nhap` | `/login` |
  | học viên | `/doi-mat-khau` | `/change-password` |
  | admin | `/tai-khoan`, `/tai-khoan/[id]` | `/users`, `/users/[id]` |
  | admin | `/tai-khoan/cap-hang-loat`, `/tai-khoan/cap-hang-loat/[id]` | `/users/bulk-import`, `/users/bulk-import/[id]` |
  | admin | `/lop-hoc`, `/lop-hoc/[id]` | `/classes`, `/classes/[id]` |
  | admin | `/phan-quyen` | `/rbac` |
  | admin | `/cau-hinh-chung` | `/catalog` |
  | admin | `/tuy-chinh-giao-dien` | `/appearance` |

  Spec và plan của đợt 1 (`docs/specs/`, `docs/plans/`) là tài liệu theo ngày và vẫn ghi đường dẫn cũ. Tên tệp mẫu tải về `mau-cap-tai-khoan-hang-loat.xlsx` không phải đường dẫn nên giữ nguyên.
- **UNI-2579** (PR #30): nâng cấp bộ giao diện dùng chung và tab "Người dùng theo quyền" theo bộ hướng dẫn modern-web-guidance (tab có gạch chân, bảng cuộn ngang bằng bàn phím, bóng mờ `scroll-shadows-y`, bố cục theo vùng chứa); quy ước ghi vào `DESIGN.md` mục 5.13.
- **UNI-2581** (PR chồng lên #30, 2026-11-15): rà soát và nâng cấp các màn còn lại, 75 phát hiện chia bốn nhóm (bộ giao diện, tài khoản và cấp hàng loạt, lớp học và cấu hình chung, khung trang và web học viên). Có thêm `LiveStatus`, lỗi trong `ConfirmDialog`, `Table.stickyColumns`, focus tự trả về ở `Dialog`/`Drawer`, `StatusPage embedded`; bảng giá trị danh mục chọn cột theo bề rộng thẻ (lỗi bảng tổ hợp môn ở 1883px); test chặn `outline-none` và vòng focus viết tay ở hai web. Quy ước mới ghi vào `DESIGN.md` (5.1, 5.2, 5.3, 5.5, 5.9–5.11, 5.13, 7, 8).
  - Đã chốt 2026-11-15: S-09 mặc định Sáng, không theo hệ điều hành (PR #33, DESIGN.md mục 9); UNI-2562 làm theo phương án b (mục dưới).
  - Để lại: ô chọn có tìm kiếm và tải từng trang cho danh sách người dùng dài (C-23, kit chưa có); `Tooltip` không cho bỏ `aria-describedby` của chính nó nên ô Lớp học được mô tả hai lần (U-24); chỉ truyền bản dịch của khung trang và feature đang mở cho client (S-20, đo được 15–23 KB gzip mỗi lần tải trang admin).
- **UNI-2586** (PR chồng lên #31, 2026-11-15): web admin dùng được từ 360px. Dưới 1024px sidebar thành ngăn menu mở từ nút ở thanh trên (`DrawerContent side="left"`), bỏ bề rộng tối thiểu 480px của vùng nội dung, lề 16px dưới 640px; các màn dùng thanh lọc gọn, bảng dạng gọn có dòng tóm tắt, Cấu hình chung đổi danh sách nhóm thành ô chọn, cây thụt ít hơn. Từ 1024px bố cục như cũ. E2E `admin-responsive.spec.ts` kiểm 390px và 1280px. DESIGN.md 5.8 đổi phạm vi admin thành 360–1920px.
  - Để lại: hai bản lớp thanh lọc (`list-filters.ts` của Tài khoản và `FILTER_GRID` của Phân quyền) nên gộp thành thành phần chung của kit; `Table` chưa cho gắn lớp lên `th`/`td` theo cột nên màn ẩn cột bằng `:has([data-col=…])`.
- **UNI-2587** (PR #34, đã merge 2026-11-15): mọi bảng của web admin thành danh sách thẻ khi khung hẹp (mặc định dưới 640px của khung; Cấu hình chung 416px, cột tệp mẫu 512px, lỗi nhập Excel 480px, Người dùng theo quyền 576px, tab Quyền 672px). `Table.cards` tự đo khung và chỉ dựng một dạng; thẻ có tiêu đề (liên kết hoặc nút phủ cả thẻ), thao tác, dòng phụ, chip trạng thái và phân loại, thông tin "Nhãn: giá trị"; dạng thẻ có ô "Chọn tất cả" có nhãn và ô chọn "Sắp xếp theo". Thành phần dùng chung trong kit: `ListCard`, `ListCardTitleLink`, `ListCardTitleButton`, `ListCardTag`, `CardList`, `CardListItem`, `useCardMode`. Thay cho dạng gọn có dòng tóm tắt của UNI-2586. Kèm hai sửa: câu gợi ý trên bảng tài khoản bị cắt ở màn hẹp; thông báo rỗng/lỗi của bảng rộng hơn khung nằm giữa phần nhìn thấy. DESIGN.md 5.9, 5.11.
  - Để lại: ô chọn sắp xếp của dạng thẻ chưa có lựa chọn "thứ tự mặc định"; kéo thả đổi thứ tự trên thẻ chưa thử bằng cảm ứng thật.
- **UNI-2562** (2026-11-15, phương án b): ở chế độ tối nền nút chính (`primary-fill` và hover/bấm) và viền thẻ/chip/ô đang chọn (`primary-600`) được `deriveTokens` nâng độ sáng tới khi đạt 3:1 với nền trang, nền thẻ và nền phụ (WCAG 1.4.11), với mọi bộ phối màu. Chữ trên nút giữ trắng nếu vẫn đạt 4,5:1, không thì nút sáng tới khi chữ tối `#11181c` đạt 7:1; cả 9 bộ có sẵn đều thành nền sáng chữ tối ở chế độ tối (mặc định: `#55b38f`). Thêm token `on-danger` và `switch-thumb` (trắng cả hai chế độ) cho nút nguy hiểm và switch tắt. Cảnh báo "chữ tối trên nút" ở Tùy chỉnh giao diện xét theo chế độ sáng. `tokens.test.ts` kiểm cặp 3:1. DESIGN.md mục 2 và 8, spec 13.1.
- **UNI-2588** (2026-11-15): sau khi đóng ngăn menu ở màn hẹp, tooltip "Mở menu điều hướng" treo cạnh nút menu (focus trả về nút làm `Tooltip` tự mở, kể cả khi chạm). `Tooltip` của kit giờ chỉ mở theo focus khi thao tác gần nhất là phím điều hướng; rê chuột vẫn mở. Áp dụng cho mọi nút icon mở hộp thoại hoặc ngăn. DESIGN.md 5.9.
- **UNI-2598** (2026-11-16): danh sách tài khoản thiếu cột Lớp so với bản vẽ (T09 làm trước khi có module lớp học; T10 chỉ thêm bộ lọc). API `GET /api/v1/admin/users` trả thêm `classes` (id, mã, tên các lớp hiện tại, sắp theo mã, lấy cho cả trang bằng một truy vấn qua `ClassMembershipPort.findCurrentClassesByUserIds`); bảng có cột Lớp giữa Vai trò và Nguồn, ẩn sau cột Nguồn khi khung hẹp, dạng thẻ có dòng Lớp. Không thêm cột Số điện thoại (chốt theo bản vẽ; spec §4.3 đã sửa).
- Việc để lại đã biết: test `privilege-escalation.test.tsx` (hộp thoại Tạo tài khoản, 403) phụ thuộc thời điểm, từng fail một lần khi máy rất bận; chưa chạy thử phiên tự làm mới với Keycloak bật thu hồi refresh token.

### Đợt 2 · Mockup PBI-03, PBI-04 (2026-11-15)

- **UNI-2590** (cha), UNI-2591 đến UNI-2596: các màn web admin của Ngân hàng câu hỏi và Bài thi chạy trên dữ liệu mẫu, chưa nối backend. Bản vẽ ở `docs/design/dot-2/` (canvas trang 05–08). Làm song song năm lane trên nền chung UNI-2591, gộp một PR vào `develop`.
  - Đường dẫn: `/questions`, `/questions/new`, `/questions/[id]` (Nội dung, Nơi đang dùng, Lịch sử phiên bản), `/questions/[id]/edit`, `/questions/import`; `/exams`, `/exams/new`, `/exams/[id]` (Thông tin chung, Cấu trúc, Kiểm tra, Phiên bản).
  - Luồng dữ liệu: component → hook (`queries.ts`) → `api.ts` → `mockRequest` + kho mẫu (`lib/mock/content-mock.ts`). Nối backend chỉ thay thân hàm `api.ts`. URL `?mockState=loading|empty|error` dựng các trạng thái; chữ mẫu tiếng Việt nằm trong `mock/*.json`.
  - Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK`: chưa đặt thì dev bật, bản build tắt (menu ẩn, trang trả 404). Bản demo đặt `true`.
  - Quy ước mới ở `DESIGN.md` 5.14: trang Tạo cho đối tượng nhiều phần thay hộp thoại, dải trạng thái có số lượng và chip bộ lọc, layout chung cho trang con, luồng nhiều bước, cột `Table` phải ổn định.
  - **Chờ BA:** 6 dạng câu (bản vẽ) hay 4 (UniNote); câu Nháp có bắt buộc gắn kiến thức không; sửa câu Sẵn sàng thì về Nháp hay tạo phiên bản mới; công bố một phần khi còn lỗi; tạo bài thi Nháp khi còn mục "Cần xử lý"; danh mục tổ hợp chưa có trường kỳ thi nên lọc tổ hợp theo kỳ thi đang dựa vào dữ liệu mẫu; môn của KH-2; ảnh bìa lưu ở đâu.
  - Để lại: trình soạn thảo đầy đủ (canvas trang 07) chưa làm, ô đề bài là ô nhập nhiều dòng; test `question-import-flow` và `users-list` chậm khi chạy cả bộ trên máy bận (đạt khi chạy riêng).

### Đợt 2 · Mở rộng 7 kỳ thi và chấm tự luận (2026-11-16)

- **UNI-2599** (cha), nền UNI-2600, bốn lane UNI-2601 đến UNI-2604; đặc tả `docs/specs/2026-11-16-mockup-7-ky-thi.md` (cấu trúc đề do M.Trường tóm tắt từ thư mục Drive "MÔ TẢ CẤU TRÚC ĐỀ THI - ALL KHÓA").
  - Quyết định của M.Trường: tự luận chấm tay, có màn hàng chờ chấm `/grading`; câu Đúng/Sai chấm theo bảng điểm số ý đúng (mặc định THPTQG 0,1/0,25/0,5/1); câu nhiều đáp án "Đúng hết mới có điểm" hoặc "Chấm từng phần"; quy tắc chấm lưu ở câu hỏi, khối của bài thi giữ quy tắc mong đợi để kiểm tra cấu hình đối chiếu.
  - Danh mục mẫu: 7 kỳ thi (QDA là ĐGNL Bộ Quốc phòng), mã tổ hợp gắn kỳ thi (KH-n, QĐ01–QĐ04, CA1–CA4), năng lực V-ACT, lớp Liên lớp; 20 mẫu cấu trúc. Seed danh mục dev/test có đủ 12 môn thi THPT.
  - Quy ước mới ở `DESIGN.md` 5.14: nhãn "Chấm tay", chip "Chờ chuyên gia xác nhận", thời lượng chung, bảng điểm sửa được, nút chọn tệp của kit, màn chấm.
  - **Cần chuyên gia hoặc BA xác nhận:** THPTQG 12 môn do agent điền theo định dạng 2025 của Bộ GD&ĐT; SPT, SP2E chỉ có môn Toán, điểm từng câu là tạm, SP2E thiếu thời lượng; HSA chia 17–17–16; số câu Trả lời ngắn trong môn Toán của BCA; câu Trả lời ngắn của SPT chấm tự động hay chấm tay; quy tắc chấm lệch khối nên chỉ cảnh báo hay chặn công bố; tổ hợp sinh ngoài danh mục có vào danh mục chung không; có cho chấm lại sau khi hoàn tất không. Tiêu chí chấm, bài làm mẫu trong `/grading` do agent tự viết.
  - **Đối chiếu bảng cấu trúc đề tổng quát (2026-11-16):** SP2E là ĐH Sư phạm Hà Nội 2; BCA Phần 2 tính Toán, Sử 1 điểm, Tiếng Anh 0,75 điểm mỗi câu; V-ACT thang 1.200 (4 thành phần × 300, IRT sau); tên phần HSA, QDA theo bảng; câu điền số nhận phân số. Chi tiết ở mục 1.1 của đặc tả 7 kỳ thi và mục 8, 12 của đặc tả backend.
  - Để lại: câu mẫu môn Tin học, Tiếng Việt, Tư duy khoa học chưa có nút kiến thức trong cây mẫu; hai nhánh Tin học (Khoa học máy tính, Tin học ứng dụng) lấy chung một nguồn câu.
- **UNI-2605** đặc tả backend PBI-03, PBI-04: `docs/specs/2026-11-16-be-pbi-03-04.md`. M.Trường chốt: phạm vi PBI-03 + PBI-04 (lượt thi, chấm làm sau); nội dung HTML lọc + LaTeX + ảnh MinIO; công bố bài thi là quyền riêng `EXAM.PUBLISH`. Điểm agent tự quyết ghi ở mục 1.1.
  - **Theo UniNote BA sửa chiều 2026-11-16** (PBI-03 bản 11:22, PBI-04 bản 11:12; bản lưu ở `docs/design/dot-2/uninote-*.md`), M.Trường chốt B5–B8: điểm nằm ở câu hỏi; 8 dạng câu theo BA (tách Trả lời ngắn và Điền khuyết, Ghép đôi thay Kéo thả, Đúng/Sai hai cách chấm); Trả lời ngắn khớp đúng kèm đáp án được chấp nhận; bước 1 Tạo bài thi theo phạm vi bài thi (Toàn bài, Theo phần, Theo môn), có Hướng dẫn làm bài, phiên bản năm chọn ở bước Cấu trúc. Mockup chưa sửa: sửa giao diện là task FE-3 của đợt backend (đặc tả mục 9.1).
  - **Sửa theo review của Codex (19 điểm, 2026-11-16):** thêm D13–D20 ở mục 1.1. Khóa lạc quan bằng `revision` riêng; kết quả kiểm tra gắn dấu vân tay đầu vào và revision nguồn, công bố so lại trong transaction; `PaperSolver` dùng chung giải cả đường lựa chọn, có đề chứng minh; `exam_version` chụp đủ cấu hình; `COMPETENCY` thành nhóm cây một cấp; tìm kiếm bằng cột `search_text` bỏ dấu thay `unaccent`; bảng trạng thái câu hỏi; MAPID và tiêu chí chấm tùy chọn, hướng dẫn chấm bắt buộc (theo BA); không cho Điền khuyết làm câu con; cú pháp tệp nhập (mục 4.6); tải tệp không nhận SVG, phát qua proxy; idempotency chung (mục 7.1); `anyOf` và migration cấp quyền; sổ câu đã dùng; điểm thô và điểm hiển thị; bảng hợp đồng dữ liệu đổi so với mock (mục 9.2); thêm ca kiểm thử.
  - **Plan cook (2026-11-16):** `docs/plans/2026-11-16-be-pbi-03-04.md`, 15 task trên 100 điểm (BE-1, BE-2a/2b, BE-3, BE-4, BE-5a/5b, BE-6, FE-3a/3b/3c, FE-4, FE-1, FE-2, QA), nhánh tích hợp `integration/dot-2`. **Đang chạy từ 2026-11-16** (M.Trường: "cook đi"): issue cha UNI-2606, sub-issue UNI-2607…UNI-2621 (bảng ở `TASK.md`), nhánh `integration/dot-2` từ `develop` @ `2ff5015`; sổ tiến độ của Advisor ở `.thi360-dev/dot-2-be/` ngoài repo. Kèm theo: xung đột revision trả 412 như đợt 1 (spec đã sửa), lỗi nghiệp vụ 422 cần thêm handler ở BE-1.
    - Sửa theo review plan (19 điểm): gán quyền mới trong `PermissionCatalogSync` thay migration; hồ sơ kỳ thi vào bộ mẫu dev; `content-reference` ở module `exam`; bộ mẫu câu hỏi cho E2E; kiểm tra kỹ chạy nền (proxy cắt 60 giây); giới hạn tải lên 22 MB và handler 413 chung; tách cờ mock của `/grading`; `question_asset_ref`; tách FE-4 (trình soạn HTML); mỗi lane backend một DB dev.
  - **Ma trận theo môn (UniNote PBI-04 bản 11:54, 2026-11-16):** BA viết lại mục 6.1 khi đợt chạy `run_29d79f69be23` đã bắt đầu. Spec thêm B9, D21–D26 (chỉ tiêu dạng trả lời, lớp, độ khó ở cấp môn; nhóm lấy câu kế thừa MAPID của môn; HSA Khoa học là một phương án chọn 3 trong 5 môn thay 10 tổ hợp KH-n; bộ giải tách theo môn); plan sửa BE-4, BE-5a, BE-5b, BE-6, FE-3a, FE-3c, FE-2 và thêm A7 (ảnh hưởng tới BE-1, FE-3a, BE-5a đang chạy). Chờ BA: cách chia 17–17–16, chỉ tiêu cấp môn khi số câu môn thay đổi, có áp cho 6 kỳ thi còn lại không (spec mục 12).
    - Sửa theo review plan lượt hai (6 điểm): FE-4 xuống sóng 3 và là lane duy nhất nối trình soạn vào các màn; trạng thái kiểm tra kỹ lưu ở bảng `exam_check_job`, FE-3a/FE-3c/FE-2 có phần giao diện và kiểu `ExamCheckRun`; FE-1, FE-2 chuyển test sang giả lập client, QA chỉ xóa mock và giữ phần `/grading` còn import (`questionStore`); hướng dẫn làm bài không nhận ảnh; test duyệt mọi câu trong bộ mẫu; BE-1 cấu hình loại test `perf`. Điểm: FE-1 5, FE-2 6, QA 3. Chưa commit, nằm ở worktree `p-2605b`, nhánh `docs/UNI-2605-sua-plan-review`.

### Nhật ký đợt 1

- T02 (UNI-2539) đã nghiệm thu và merge vào `integration/dot-1` (PR #4, `dda1504`).
- T06 (UNI-2543) đã nghiệm thu và merge vào `integration/dot-1` (PR #5, `4a3c7ac`).
- T03 (UNI-2540) đã nghiệm thu và merge vào `integration/dot-1` (PR #6, `a996ab4`).
- UNI-2559 (tương phản màu chế độ sáng, con của UNI-2543, ngoài điểm plan) đã nghiệm thu và merge vào `integration/dot-1` (PR #7, `dec8d5b`).
- T05 (UNI-2542) đã nghiệm thu và merge vào `integration/dot-1` (PR #8, `457b80d`).
- T04 (UNI-2541) đã nghiệm thu và merge vào `integration/dot-1` (PR #9, `b252db2`).
- T17 (UNI-2555) đã nghiệm thu và merge vào `integration/dot-1` (PR #11, `feece8f`).
- T13 (UNI-2550) đã nghiệm thu và merge vào `integration/dot-1` (PR #10, `8f1a579`).
- T07 (UNI-2544) đã nghiệm thu và merge vào `integration/dot-1` (PR #12).
- T18 (UNI-2556) đã nghiệm thu và merge vào `integration/dot-1` (PR #13, `27d4984`).
- T14 (UNI-2551) đã nghiệm thu và merge vào `integration/dot-1` (PR #14).
- T10 (UNI-2547) pha A (API lớp học) đã nghiệm thu và merge vào `integration/dot-1` (PR #15, `edfd106`): module `classroom`, API `/api/v1/admin/classes`, `GET /users/{id}/classes`, `classIds` khi tạo tài khoản (cần thêm `CLASS.MODIFY`) và lọc `classId`; mã `CLASS_NOT_SUPPORTED` đã bỏ. Pha B (UI, lớp mẫu dev) làm sau khi T09 merge; T10 chưa tính điểm cho tới khi pha B merge.
- T09 (UNI-2546) đã nghiệm thu và merge vào `integration/dot-1` (PR #16, `4b57a54`): `/tai-khoan` (danh sách, lọc, thao tác hàng loạt), tạo, reset mật khẩu, `/tai-khoan/[id]`; proxy web chuyển tiếp `Idempotency-Key`. Tab "Vai trò & quyền" và "Lớp học" ở chi tiết tài khoản còn là chỗ giữ chỗ (T08 và T10 pha B nối vào). Việc để lại ghi thành issue con của UNI-2546.
- CSDL dev dùng chung: migration lớp học của T10 (`V202611140200`) có dấu thời gian sớm hơn migration của T14 đã chạy, nên backend dev trên CSDL cũ cần biến môi trường `SPRING_FLYWAY_OUT_OF_ORDER=true` (CSDL mới, test và CI không ảnh hưởng). Migration mới phải có dấu thời gian muộn hơn mọi migration đang có trên nhánh tích hợp.
- T11 (UNI-2548) pha A (backend cấp tài khoản hàng loạt) đã nghiệm thu và merge vào `integration/dot-1` (PR #17, `4ff86fb`): kho tệp S3/MinIO mã hóa AES-256-GCM (`THI360_FILE_ENC_KEY`, bắt buộc ngoài dev), bảng `import_batch`/`import_row`, API `/api/v1/admin/import-batches`, job xử lý có khóa lease và khôi phục. Tệp có cột Mã lớp thì người tải lên cần thêm `CLASS.MODIFY`. Pha B (giao diện bốn bước) đang làm; T11 chưa tính điểm cho tới khi pha B merge.
- T12 (UNI-2549) đã nghiệm thu và merge vào `integration/dot-1` (PR #18, `5d6ae73`): `GET /api/v1/admin/users/{id}/profile`, `GET …/learning`, `identity/spi/StudentLearningProvider`, tab "Hồ sơ học tập" (cần `USER.LEARNING_VIEW`). AC-01.7 mới đạt phần tài khoản và lớp; phần học tập (quyền sử dụng, lượt thi, đánh giá, ôn luyện, yêu cầu hỗ trợ) hiển thị "Chưa có dữ liệu" và chờ module thi (PBI-09 đến 17) nối qua `StudentLearningProvider`.
- T08 (UNI-2545) đã nghiệm thu và merge vào `integration/dot-1` (PR #19): `/phan-quyen` bốn tab (Vai trò, Quyền, Người dùng theo quyền, Gán hàng loạt), tab "Vai trò & quyền" ở chi tiết tài khoản. Trong đợt 1, muốn gán vai trò/quyền cần thêm `ROLE.VIEW` / `PERMISSION.VIEW` ngoài `USER.ASSIGN` (backend cho đọc danh sách tra cứu bằng `USER.ASSIGN` để sau, issue con). Sửa kèm lỗi vòng lặp vẽ lại của `Table` trong `packages/ui` làm treo trang khi mở hộp thoại tạo vai trò. Kiểm tra giao diện còn thiếu vài trạng thái (đang tải, một số trạng thái lỗi/trống), ghi ở issue con.
- T19 (UNI-2561) đã nghiệm thu và merge vào `integration/dot-1` (PR #20): giao diện song ngữ (tiếng Việt mặc định, thêm tiếng Anh; cookie `thi360_locale`), nút sáng/tối theo từng người (cookie `thi360_theme`) ở thanh trên cùng của hai web và trang công khai, backend trả thông báo theo `Accept-Language` (`messages_en.properties`). Quy tắc từ nay: mọi khóa thông báo backend có ở cả ba file `messages*.properties`; mọi khóa web có ở cả `vi.json` và `en.json`; không viết chữ tiếng Việt trong component (có test quét). Chưa kiểm tra: màn hình hẹp; giao diện tiếng Anh trên trình duyệt của các màn merge sau T19 (phân quyền, lớp học, cấp hàng loạt, cấu hình chung) — làm ở T16. Tệp kết quả của đợt cấp tài khoản do job nền dựng vẫn là tiếng Việt (UNI-2566).
- T10 (UNI-2547) pha B (giao diện lớp học) đã nghiệm thu và merge vào `integration/dot-1` (PR #21, `6f1b1c5`): `/lop-hoc`, `/lop-hoc/[id]`, menu Lớp học, tab "Lớp học" ở chi tiết tài khoản, lọc theo lớp và chọn lớp khi tạo tài khoản, lớp mẫu dev LOP-A/LOP-B (`DevClassSeeder`). Bộ e2e: đăng nhập `platform` dùng chung (`e2e/tests/helpers/platform.ts`), nâng ngưỡng đăng nhập theo IP khi chạy e2e bằng biến môi trường `THI360_SECURITY_RATELIMIT_LOGINPERIPLIMIT`, test tìm theo mã trước khi kiểm tra dòng mới trong danh sách phân trang. T10 xong cả hai pha.
- T11 (UNI-2548) pha B (giao diện cấp tài khoản hàng loạt) đã nghiệm thu và merge vào `integration/dot-1` (PR #22): `/tai-khoan/cap-hang-loat` bốn bước, nút "Cấp hàng loạt" ở danh sách tài khoản (cần `USER.MODIFY`), cảnh báo rời trang khi chưa tải tệp kết quả có mật khẩu do hệ thống sinh. Lỗi theo dòng có thêm mã cột ổn định `columnKey` để giao diện dịch tên cột theo ngôn ngữ. Bộ e2e: hàm đăng nhập `platform` dùng chung tự kiểm tra và đăng nhập lại khi phiên bị loại; lượt chạy e2e nâng giới hạn phiên mỗi tài khoản bằng `THI360_SECURITY_SESSION_MAXPERUSER` (mặc định sản phẩm là 5 phiên, phiên cũ nhất bị loại). T11 xong cả hai pha.
- T15 (UNI-2552) đã nghiệm thu và merge vào `integration/dot-1` (PR #23, `80363ea`): màn `/cau-hinh-chung` (nhóm menu Nội dung, cần `CATALOG.VIEW`; nút ghi cần `CATALOG.MODIFY`) gồm danh mục dạng danh sách, mã tổ hợp, cây kiến thức nhiều cấp (thêm nút, chuyển nhánh, ngừng theo cấp trên, danh mục liên kết và lọc cây theo liên kết), nhập/xuất Excel; thành phần `TreeView` trong `packages/ui`. Hộp thoại dùng chung: phần đầu và phần chân dính khi nội dung cuộn, nút đóng luôn bấm được. Dưới 1600px ngăn chi tiết nằm dưới cây và tự cuộn vào tầm nhìn khi chọn nút. Thông báo "phiên bản mới" của mã tổ hợp đang dùng chưa kiểm tra được vì chưa module nào tham chiếu mã tổ hợp.
- T16 (UNI-2553) đã nghiệm thu và merge vào `integration/dot-1` (PR #24, `14cdf95`): bảng nghiệm thu `docs/acceptance/dot-1.md` (tiêu chí spec mục 12, 15.7 và T19 ↔ tên test; mục "Chưa đạt / hoãn"; mục "Việc để lại"), `AdminApiForbiddenMatrixIT` (78 endpoint quản trị: thiếu quyền → 403, học viên → 403, không cookie → 401), `NoHardDeleteIT`, `AuditAppLoginIT`, sửa số ít/số nhiều tiếng Anh ở cấp hàng loạt và chuyển nhánh, README chạy từ bản clone mới, bỏ theo dõi `tsconfig.tsbuildinfo`. Lượt chạy cuối trên Windows ở `f4c61f0`: backend 215 test, web và e2e 29/29 đều qua, không lệch OpenAPI. AC-01.7 chỉ đạt phần tài khoản và lớp; phần học tập chờ module thi.
- 2026-11-14: M.Trường yêu cầu làm luôn phần danh mục đã hoãn (PBI-02: T13, T14, T15). Plan mục A7, A4, Phần C, hợp đồng T13–T15 và T16 đã sửa theo; T13 (UNI-2550) mở ngay trên VPS, T14 sau T13, T15 sau T13, T14, T18.
- 2026-11-14: M.Trường yêu cầu đa ngôn ngữ (tiếng Việt mặc định, thêm tiếng Anh). Thêm T19 (UNI-2561) vào plan, chạy sau T17 và T18; mọi lane có giao diện thêm khóa tiếng Anh cho chuỗi của mình ngay từ bây giờ (`en.json` cùng bộ khóa với `vi.json`).
- 2026-11-14: M.Trường chốt chế độ sáng/tối **theo từng người**, bật tắt bằng nút ở thanh trên cùng (cạnh nút ngôn ngữ, bên trái avatar). Cấu hình hệ thống của T17 chỉ còn giữ chế độ mặc định. Nút và cookie `thi360_theme` làm trong T19. Spec §13, `DESIGN.md` §5/§8/§9 và plan T17/T19 đã sửa.

**Bước tiếp theo**

1. Người duyệt và merge các PR sau đợt 1 theo thứ tự #26, #28, #27 rồi #29 (UNI-2576, chồng lên ba PR kia). Việc để lại của đợt 1 nằm ở mục 6 của `docs/acceptance/dot-1.md`.
2. Tài khoản mẫu dev (B13: `sys.admin`, `content.admin`, `ops.admin`, `student01`…`05`) làm trong T18; lớp mẫu làm trong T10.

**Vướng / việc treo**

- DB dev dùng chung: migration `V202611131020` đã sửa trong lúc review, nên DB dev cũ lệch checksum Flyway. Cách xử lý: xóa các schema `shared`, `identity`, `catalog`, `audit` trong DB dev rồi chạy lại backend (chỉ có bảng khung rỗng).
- Test tích hợp backend dùng chung một PostgreSQL Testcontainers và giữ nhiều ngữ cảnh Spring: `backend/src/test/resources/application-test.yml` (Hikari tối đa 4) và `spring.properties` (giữ tối đa 8 ngữ cảnh) chặn lỗi "too many clients". Test nào cần nhiều kết nối hơn thì đặt pool riêng và `@DirtiesContext`. Test dựa vào `outboxRelay.relayBatch()` phải xả hết tồn đọng (vòng lặp), vì thứ tự lớp test trên Windows khác Linux.
- Việc còn lại sau T05 (không chặn): tách login role riêng cho job nhật ký (hiện tài khoản ứng dụng có thể `SET ROLE` sang vai trò bảo trì); thiếu test đầu-cuối ghi người thực hiện khi đăng nhập realm APP. Ghi thành issue con của UNI-2542.

## Đã làm

### 2026-11-14 — T14 (UNI-2551) mã tổ hợp, nơi đang dùng, quy tắc ngừng và xóa, Excel; merge vào integration/dot-1

- `combination_version`, `combination_subject`; sửa môn của tổ hợp tạo phiên bản mới khi phiên bản hiện tại đang được tham chiếu, nếu không thì sửa tại chỗ; `GET /items/{id}/versions`, `GET /combination-versions/{id}`.
- `CatalogUsageProvider` + hai provider nội bộ (tổ hợp dùng môn/kỳ thi; nút cây liên kết giá trị); `usages` cho giá trị và nhóm (cây: gộp cả nhánh); mã lỗi `CATALOG_ITEM_IN_USE`, `CATALOG_CHANGE_BLOCKED`, `CATALOG_CODE_LOCKED`.
- Excel: xuất; nhập hai bước (xem trước, xác nhận) cho danh sách và cây (ô gộp, dùng lại nút đã có), nhóm tổ hợp trả `IMPORT_NOT_SUPPORTED`; giới hạn 5 MB, 5.000 dòng, chặn công thức, macro, zip bomb, vùng gộp quá lớn.
- Review BE: 3 lỗi high (vùng gộp làm phình bộ nhớ, quan hệ cha con cũ khi xác nhận, tạo tổ hợp tranh chấp với ngừng môn) và 7 medium, đã sửa hết.
- Lệnh đã chạy: Cursor cloud runner trên `b92a02e` `./mvnw -B -ntp verify` (177 + 162 test) và `pnpm` lint/typecheck/test/build → exit 0; Advisor chạy lại trên Windows trên `0ca1e55` `./mvnw -B -ntp verify`, kiểm tra lệch openapi/api-client, `pnpm typecheck` → exit 0.
- Chạy thử trên bộ dịch vụ dev riêng thực hiện trước vòng sửa review; phần sửa được phủ bằng test tích hợp, e2e của T15 sẽ chạy lại luồng nhập/xuất qua giao diện.
- Merge squash qua PR #14.

### 2026-11-14 — T18 (UNI-2556) đăng nhập một nơi và điều hướng theo vai trò, merge vào integration/dot-1

- Backend: `RedirectResolver` (thứ tự spec §15.2, kiểm tra `callbackUrl` §15.3), `redirectTo` khi đăng nhập, `homeUrl` ở `/me`, đăng xuất xóa đủ cookie; `DevDataInitializer` tạo tài khoản mẫu B13 (chỉ profile `dev`): `sys.admin`, `content.admin`, `ops.admin`, `student01`…`05`, mật khẩu `Thi360@dev`.
- `packages/web-kit` (kiểm tra `thi360_ctx` bằng HMAC); web học viên: proxy, landing, `/dang-nhap`, `/doi-mat-khau`, `/home`, `/forbidden`, 404, trang lỗi; web admin: `proxy.ts` chuyển về trang đăng nhập của web học viên, `/forbidden`, làm mới phiên khi 403; bỏ phiên giả cho dev. Hai route proxy ghi đè `X-Forwarded-For`.
- Package `e2e/` (Playwright) và `e2e.yml`. Chặn chung cho test backend: Hikari tối đa 4, giữ tối đa 8 ngữ cảnh Spring.
- Review BE sạch; review FE có 1 lỗi high (vòng lặp chuyển hướng khi 401) và 8 medium, đã sửa; kiểm tra giao diện hai vòng ở cả sáng và tối, có màn 390px và trang lỗi 500.
- Lệnh đã chạy: Cursor cloud runner trên `b5ab706` `./mvnw -B -ntp verify` (177 + 135 test) và `pnpm` lint/typecheck/test/build → exit 0; Advisor chạy lại trên Windows trên `b9e1fc5` các lệnh đó, kiểm tra lệch openapi/api-client → exit 0, và `THI360_DB_PORT=54320 E2E_USE_BUILT=1 pnpm --filter @thi360/e2e test` bằng Node 22 → 6/6 qua.
- Lệch thiết kế: mục "Hồ sơ" trong menu avatar đang ẩn (chưa có trang hồ sơ). Việc nhỏ còn lại ghi thành issue con của UNI-2556.
- Chạy e2e trên Windows: nếu môi trường có biến `NoDefaultCurrentDirectoryInExePath` thì Playwright không tìm thấy `mvnw.cmd`; bỏ biến đó hoặc đặt `E2E_BACKEND_CMD`.
- Merge squash qua PR #13 (`27d4984`).

### 2026-11-14 — T07 (UNI-2544) API quản lý tài khoản, merge vào integration/dot-1

- `UsernamePolicy`, `PasswordPolicy`, `PasswordGenerator`; API `/api/v1/admin/users`: danh sách có lọc và sắp xếp, tạo (PROVISIONING → Keycloak → ACTIVE), xem, sửa, ngừng hoạt động (thu hồi phiên), kích hoạt, reset mật khẩu, xóa mềm có `AccountDeletionGuard`, kiểm tra trước khi xóa/ngừng, thao tác hàng loạt có `Idempotency-Key`, kiểm tra tên đăng nhập.
- `ProvisioningReconcilerJob` (worker, 5 phút, có công tắc `thi360.identity.provisioning-reconcile.scheduling.enabled`, tắt trong test); sự kiện `UserDeleted` qua outbox.
- Thu hồi phiên chặn cả lượt đăng nhập đang dở: bộ đếm thu hồi theo tài khoản trong Redis (`SessionStore`), áp cho cả làm mới phiên.
- Lớp học: API đã có từ T10 pha A (`classIds`, lọc `classId` chạy thật); giao diện lớp học chờ pha B.
- Review BE ba vòng (12 + 4 lỗi đã sửa); vòng cuối không còn lỗi high. Ba lỗi medium về bù trừ khi reset/kích hoạt chạy chồng nhau ghi thành issue con của UNI-2544.
- Nghiệm thu bị trả lại một lần: job đối soát chạy ngay khi mỗi ngữ cảnh test khởi động và đụng test của T17; hết kết nối PostgreSQL. Đã sửa.
- Lệnh đã chạy trên `fbfe30a`: Cursor cloud runner `./mvnw -B -ntp verify` (146 + 132 test) và `pnpm` lint/typecheck/test/build → exit 0; Advisor chạy lại trên Windows các lệnh đó và kiểm tra lệch openapi/api-client → exit 0.
- Merge squash qua PR #12.

### 2026-11-14 — T13 (UNI-2550) danh mục dùng chung: schema, seed, API, merge vào integration/dot-1

- Schema `catalog`: `catalog_group` (tên Việt bắt buộc, tên Anh tùy chọn), `catalog_item` (cây: `parent_id`, `depth`, `path`), `catalog_group_ref`, `catalog_item_ref`; seed 9 nhóm hệ thống, dữ liệu mẫu dev.
- API `/api/v1/admin/catalog` (nhóm, giá trị, thứ tự, cây, chuyển nhánh, liên kết) và API đọc `GET /api/v1/catalog/options|tree|tree/descendants`; `value` đánh số dưới khóa dòng nhóm, không dùng lại; cache Redis có chặn ghi đè cũ; `CatalogLookup` cho module khác; nhật ký có vai trò và lý do.
- Review BE: vòng 1 có 3 lỗi high về xử lý đồng thời và 12 medium, vòng 2 còn 3 medium; đã sửa hết.
- Nghiệm thu bị trả lại ba lần vì môi trường test, không phải tính năng: test `AuthzEvictionIT` phụ thuộc thứ tự lớp (tồn đọng outbox quá 200 sự kiện); lần gộp integration làm rỗng hai tệp `messages*.properties`; hết kết nối PostgreSQL khi có thêm ngữ cảnh test. Cả ba đã sửa.
- Lệnh đã chạy trên `59e466e`: Cursor cloud runner `./mvnw -B -ntp verify` (101 + 101 test) và `pnpm` lint/typecheck/test/build → exit 0; Advisor chạy lại trên Windows các lệnh đó và kiểm tra lệch openapi/api-client → exit 0.
- Còn mở cho T19: tên các cấp của cây (`Chương`, `Bài`, `Kiến thức`) mới có một ngôn ngữ.
- Merge squash qua PR #10 (`8f1a579`).

### 2026-11-14 — T17 (UNI-2555) tùy chỉnh giao diện toàn hệ thống, merge vào integration/dot-1

- Backend: `AppearanceService` trên `system_setting('appearance')`; `GET /api/v1/appearance` công khai có cache và ETag, `PUT /api/v1/admin/appearance` và `POST …/reset` cần `APPEARANCE.MODIFY`, bắt buộc `version` (412 khi cũ), ghi nhật ký `APPEARANCE_UPDATED`.
- `packages/ui/src/theme`: 9 bộ màu, `deriveTokens` (OKLCH), kiểm tra tương phản, `buildThemeVars`. Hai web sinh biến màu của cả hai chế độ trong một thẻ `<style>` phía máy chủ (không đặt trên `<html style>`), nên đổi `data-theme` là đổi chế độ, không nháy màu.
- Web admin: màn `/tuy-chinh-giao-dien` (chế độ mặc định, 9 thẻ bộ màu, tự tùy chỉnh, xem trước, lưu, khôi phục). Hai app dùng `@thi360/api-client`; có `en.json` cùng bộ khóa với `vi.json`.
- Review BE (7 lỗi) và FE (9 lỗi) đã sửa, vòng 2 sạch; kiểm tra giao diện hai vòng ở cả sáng và tối.
- Lệnh đã chạy: Cursor cloud runner trên `5522345` `./mvnw -B -ntp verify` (101 + 59 test) và `pnpm` lint/typecheck/test/build → exit 0; Advisor chạy lại trên Windows trên `4492e9d` các lệnh đó và kiểm tra lệch openapi/api-client → exit 0.
- Còn lại: kiểm tra HTML phía máy chủ của `/dang-nhap` chờ T18; tương phản nền nút chính ở chế độ tối (UNI-2562) chờ quyết định.
- Merge squash qua PR #11 (`feece8f`).

### 2026-11-13 — T04 (UNI-2541) phân quyền, merge vào integration/dot-1

- Engine: migration bảng quyền/vai trò, `Permission` (13 bit), đồng bộ catalog lúc khởi động, `PermissionSetNormalizer`, `@RequirePermission` (aspect), `AuthzSnapshotService` (1 lệnh Redis, 0 SQL mỗi request admin khi có snapshot), `/me` và `thi360_ctx` lấy quyền thật.
- API admin: quyền, vai trò, quyền và vai trò của user, người dùng theo quyền (+ xuất xlsx), gán hàng loạt có xem trước và khóa idempotency; `PrivilegeGuard` chặn nâng quyền, `SELF_LOCKOUT` chặn tự khóa; `AUDIT.VIEW` cho API nhật ký (D-T05-P1).
- Review BE: vòng 1 có 2 lỗi high (tự khóa khi sửa vai trò, bulk song song lách `SELF_LOCKOUT`) và 9 medium, đã sửa; vòng 2 không còn lỗi.
- Nghiệm thu lần 1 bị từ chối: test `AuthzEvictionIT` đặt `next_attempt_at = now()` theo đồng hồ DB còn relay so với đồng hồ ứng dụng; trên Windows (Docker chạy nhanh hơn vài ms) test hỏng. Đã sửa riêng test.
- Lệnh đã chạy trên `9e5a103`: Cursor cloud runner `./mvnw -B -ntp verify` (99 + 46 test) → exit 0; Advisor chạy lại trên Windows `./mvnw -B -ntp verify` và kiểm tra lệch openapi → exit 0 (web và api-client đã chạy trên `40f7c12`, bản sửa chỉ đổi test).
- AC-2 trên bộ dịch vụ riêng `thi360-t04`: `content.admin` gọi `/me` trả `access: MANAGE`, quyền `[CATALOG.MODIFY, CATALOG.VIEW]`.
- Merge squash qua PR #9 (`b252db2`).

### 2026-11-13 — T05 (UNI-2542) nhật ký thao tác, merge vào integration/dot-1

- Schema `audit`: bảng `audit_event` phân vùng theo quý (PK `(id, occurred_at)`), `audit_event_archive` theo năm, trigger chặn UPDATE/DELETE không có đường vòng; vai trò NOLOGIN `thi360_audit_maint` sở hữu bảng, vai trò ứng dụng chỉ SELECT/INSERT, job dùng `SET LOCAL ROLE`.
- Ghi nhật ký qua outbox (`AuditRecorderImpl`, che khóa nhạy cảm gồm link đặt lại mật khẩu), worker ghi idempotent, job tạo phân vùng và job lưu trữ, API `GET /api/v1/admin/audit-events` có lọc. Bỏ `NoopAuditRecorder` của T03; nhật ký ghi đúng người thực hiện (MASTER → PLATFORM, APP → USER), kể cả lúc đăng nhập thành công.
- Bộ xuất OpenAPI ghi LF (hết lệch giả trên Windows).
- Review BE sáu vòng; vòng cuối không còn lỗi chặn.
- Lệnh đã chạy trên `ba92d8a`: Cursor cloud runner `./mvnw -B -ntp verify` (86 + 28 test) và `pnpm` lint/typecheck/test/build → exit 0; Advisor chạy lại trên Windows `./mvnw -B -ntp verify`, kiểm tra lệch openapi/api-client, `pnpm` lint/typecheck/test/build → exit 0.
- Merge squash qua PR #8 (`457b80d`).

### 2026-11-13 — UNI-2559 tương phản màu chế độ sáng, merge vào integration/dot-1

- Người dùng chọn phương án 2 (D-USER-CONTRAST): chữ màu chính trên nền nhạt và nền nút chính dùng `#047857`; nút nhấn giữ `#F59223`, chữ `#1A1A1F`; chữ lỗi `#b91c1c`; chữ màu chính chế độ tối `#10b981`.
- `packages/ui`: token `primary-text`, `primary-fill`, `on-accent`, cập nhật các thành phần dùng màu; `tokens.test.ts` liệt kê mọi cặp chữ/nền đang dùng và kiểm tra >= 4,5:1 cả sáng lẫn tối. `DESIGN.md` và spec mục 13, 13.1, 14 sửa cho khớp.
- Review FE hai vòng không còn lỗi; nghiệm thu giao diện hai vòng, 0 phần tử dưới 4,5:1.
- Lệnh đã chạy trên `f457153`: Cursor cloud runner `pnpm` lint/typecheck/test/build → exit 0; Advisor chạy lại trên Windows (Node 22) cùng các lệnh → exit 0.
- Merge squash qua PR #7 (`dec8d5b`).

### 2026-11-13 — T03 (UNI-2540) xác thực, merge vào integration/dot-1

- Tệp realm đổi thành `infra/keycloak/thi-360-realm.json` (Keycloak 26.4 từ chối nạp khi tên tệp khác tên realm), thêm `init-master.sh` và dịch vụ `keycloak-init`.
- Migration identity; `KeycloakIdentityClient`; `AuthService`/`AuthController` (đăng nhập, làm mới, đăng xuất, đổi mật khẩu, `/me`); chuỗi bộ lọc B5, JWT hai realm có kiểm tra audience/azp, phiên trên Redis (tối đa 5), chặn đăng nhập sai, giới hạn theo IP qua proxy tin cậy (`THI360_TRUSTED_PROXIES`), ký cookie ngữ cảnh. SPI nhật ký dùng chung với T05.
- Review bảo mật: 9 lỗi đã sửa (gồm lộ tài khoản bị khóa, thiếu kiểm tra audience, thu hồi phiên từ sid chưa xác minh, chỉ mục phiên không nguyên tử, khóa dev dùng được ở production); review lại không còn lỗi.
- Lệnh đã chạy trên `c48a073`: Cursor cloud runner `./mvnw -B -ntp verify` (53 + 26 test) và `pnpm` lint/typecheck/test/build → exit 0; Advisor chạy lại trên Windows cùng các lệnh và kiểm tra lệch openapi/api-client → exit 0.
- Việc còn lại: proxy web phải ghi đè `X-Forwarded-For` (T18).
- Merge squash qua PR #6 (`a996ab4`).

### 2026-11-13 — T06 (UNI-2543) design system và khung web admin, merge vào integration/dot-1

- `packages/ui`: token màu (sáng, tối), 27 thành phần có test; `apps/web-admin`: route proxy, AppShell, menu lọc theo quyền, phiên giả cho dev, `usePermissions`/`<Can>`, xử lý 401/403, next-intl, trang `/`, `/forbidden`, 404, `/dev/ui`; `web.yml` thêm bước `pnpm test`.
- Review FE và BE (route proxy): vòng đầu 12 lỗi FE, 5 lỗi BE đã sửa; hai vòng review lại không còn lỗi. Sau khi lấy T02 vào nhánh, `vite` bị hạ xuống 7 khi gộp lockfile; đã ghim `vite ^8.3.8`.
- Nghiệm thu giao diện trên trình duyệt (Windows): lần 1 chưa đạt vì chữ màu xanh ở chế độ tối dưới 4,5:1 và sidebar cuộn mất; đã sửa token chế độ tối (có test tương phản) và cho sidebar dính.
- Lệnh đã chạy trên `7ff408c`: Cursor cloud runner `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build` → exit 0 (194 test); Advisor chạy lại trên Windows lint, typecheck, test, build → exit 0.
- Ghi chú thiết kế (chưa sửa, chờ người quyết): ở chế độ sáng chữ `primary-600` trên nền xanh nhạt 3,4–3,6:1, chữ trắng trên nút chính 3,77:1, chữ trắng trên nút màu nhấn 2,33:1, đều dưới 4,5:1 của DESIGN.md §9.
- Merge squash qua PR #5 (`4a3c7ac`).

### 2026-11-13 — T02 (UNI-2539) khung backend modular monolith, merge vào integration/dot-1

- Khung Spring Modulith (shared, identity, catalog, audit; `ModularityTest`), Flyway theo B3 với migration `shared` (outbox_event, idempotency_key, system_setting), `ApiError` + `GlobalExceptionHandler` (mã B4), `TraceIdFilter` (B15), `IdempotencyService`, outbox (publisher, relay `SKIP LOCKED`, backoff), `GET /api/v1/ping`, xuất OpenAPI cố định trong `verify`, `packages/api-client` cùng kiểm tra lệch trong CI.
- Review: vòng đầu 15 lỗi đã sửa, hai vòng review lại không còn lỗi. Nghiệm thu lần 1 của Advisor bắt được test outbox không ổn định (bộ quét theo lịch chạy chen vào test); đã tắt bộ quét trong profile test.
- Lệnh đã chạy trên `3aedeb1` (bằng chứng nghiệm thu): Cursor cloud runner `./mvnw -B -ntp verify` + diff openapi → exit 0; `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build` + api-client generate/diff → exit 0. Advisor chạy lại trên Windows: `OutboxRelayTests` 3 lần liên tiếp xanh, `verify` xanh, web xanh.
- Merge squash qua PR #4 (`dda1504`).

### 2026-11-13 — Quy hoạch log của backend (UNI-2558, con của UNI-2539)

- `backend/src/main/resources/logback-spring.xml`: console dạng chữ ở dev/test, JSON ở môi trường khác, ghi tệp khi bật profile `logfile`; mẫu dòng có `trace=` và `user=` ở vị trí cố định; bốn logger theo mục đích (`thi360.access`, `.security`, `.job`, `.external`).
- `LoggingConfigTests` (3 test) kiểm hai định dạng và mức log mặc định. Lệnh đã chạy: `cd backend && ./mvnw -B -ntp verify` → xanh (4 test).
- Plan thêm mục B15 (quy ước ghi log, khóa MDC); T02 bổ sung việc của `TraceIdFilter`.
- Đã merge vào `develop` qua PR #3 theo yêu cầu của M.Trường (2026-11-13).

### 2026-11-13 — Chốt thiết kế, hoãn phần danh mục

- Quyền theo đối tượng × mức (Chỉ xem / Xem và sửa) + quyền riêng; 7 đối tượng, 13 quyền (spec §5.5, plan B6).
- Tùy chỉnh giao diện chỉ còn chế độ sáng/tối và bộ phối màu: 9 bộ cố định + tự tùy chỉnh (spec §13, §13.1); bỏ tùy chọn bố cục và kích thước.
- Biểu mẫu vai trò: phạm vi Thi thì danh sách quyền hiện nhưng vô hiệu (spec §5.2).
- Danh mục (spec §9, chỉ mới ở mức thiết kế): thêm kiểu cây nhiều cấp, liên kết nút tới giá trị danh mục khác (kế thừa theo nhánh), loại Hệ thống / Tùy chỉnh, màn Cấu hình chung thiết kế lại. **Chưa có mã; T13–T15 hoãn.**
- Plan: thêm A7 (phần danh mục đang hoãn), bỏ T13–T15 khỏi các sóng, tiến độ tính trên 83 điểm, T16 nghiệm thu nền + PBI-01, E2E của T08 đổi sang đối tượng Lớp học.
- Bản thiết kế màn hình (artifact) đã cập nhật đủ các điểm trên, gồm các màn danh mục để tham khảo khi mở lại.

### 2026-11-13 — Sửa theo góp ý của BA trên bản thiết kế

- Danh sách tài khoản bỏ cột Mã và cột Đăng nhập cuối.
- Cấp hàng loạt: bỏ ô lớp chung, bỏ danh sách lịch sử đợt cấp, thêm nút Tải file lỗi. Biểu mẫu vai trò chỉ hiện tên quyền.
- Mật khẩu do người tạo nhập, có quy tắc độ mạnh áp chung; cấp hàng loạt thêm cột Mật khẩu. Spec §4.3, §7, §14; plan B12, T07, T09, T11, T18.

### 2026-11-13 — Chỉnh menu và bổ sung phân quyền

- Spec §5.2 viết lại (một menu Phân quyền, bốn tab), thêm §5.8 Người dùng theo quyền và §5.9 Gán hàng loạt; §7 Cấp hàng loạt thành nút trong màn Tài khoản; §13 Tùy chỉnh giao diện chuyển ra menu chính.
- Plan: T04 thêm ba endpoint (`/permissions/{id}/users`, `/authz/bulk/preview`, `/authz/bulk`), T08 viết lại, T06/T09/T11/T17 chỉnh menu và lối vào.
- Bản thiết kế màn hình (artifact) cập nhật theo.

### 2026-11-13 — Plan triển khai chi tiết

- Viết lại `docs/plans/2026-11-13-base-port-pbi-01-02.md` thành plan tự đủ cho phiên Advisor: nhánh tích hợp `integration/dot-1`, phạm vi ủy quyền, trọng số 100 điểm, thư viện và quy ước chốt sẵn, bảng `bit_index` của quyền, hợp đồng T02–T18.
- Bổ sung spec: đăng nhập một nơi (mục 15), trường `access` của vai trò và hai realm Keycloak (mục 5.6), cách máy chủ xử lý phân quyền (mục 5.7).
- Gộp nhánh tài liệu vào nhánh này để làm gốc cho nhánh tích hợp.

### 2026-11-13 — T01 khởi tạo monorepo, CI, môi trường dev (UNI-2538)

- Monorepo pnpm + Turborepo: `backend/`, `apps/web-admin`, `apps/web-student`, `packages/{ui,api-client,config}`, `infra/`.
- Backend Spring Boot 3.5.16, Java 21, Maven wrapper, chỉ có health check (module và cơ sở dữ liệu thuộc T02).
- Hai web Next.js 16.2.6 + Tailwind 4, dùng token trong `packages/ui/src/styles/tokens.css`, font Be Vietnam Pro.
- `infra/docker-compose.yml`: PostgreSQL 16, Redis 7, Keycloak 26.4 (tự nạp realm `thi-360`), MinIO, Mailpit. Cổng lệch khỏi cổng chuẩn (54360, 63790, 8360, 9360/9361, 1360/8361).
- CI: `backend.yml`, `web.yml`, `uniai-link.yml`; mẫu PR.
- `scripts/uniai.sh` và hook `prepare-commit-msg` chỉnh từ UniWork (chạy được cả khi máy chỉ có `python`).
- **Đã chạy thật trên máy dev (Windows):**
  - `cd backend && ./mvnw -B -ntp verify` → 1 test xanh, BUILD SUCCESS.
  - `pnpm install`, `pnpm lint`, `pnpm typecheck`, `pnpm build` → xanh; CSS build ra có `--color-primary-600:#059669`.
  - `docker compose -f infra/docker-compose.yml up -d` → postgres, redis, keycloak, mailpit `healthy`; minio trả 200 ở `/minio/health/live`; realm `thi-360` trả 200; lấy được token `client_credentials` cho client `thi360-api`; PostgreSQL có hai cơ sở dữ liệu `thi360` và `keycloak`.
- **Chưa kiểm tra:** chạy trên "máy sạch" khác; kết quả CI xem ở mục dưới.

### 2026-11-13 — Chốt câu hỏi mở, cập nhật tài liệu

- M.Trường duyệt kiến trúc theo đề xuất và chốt 6 câu hỏi mở cùng phạm vi tùy chỉnh giao diện (spec mục 14, `PRD.md` mục 6).
- Cập nhật spec, plan, `PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`, `TASK.md` theo các điểm đã chốt.
- Thêm task T17 **UNI-2555** (tùy chỉnh giao diện toàn hệ thống). Bình luận "phạm vi đổi" trên UNI-2537, 2540, 2542, 2544, 2547, 2548, 2550, 2554.
- Repo: đã push `main` và `develop` lên `UNIAI-TEAM/thi-360`; `develop` là nhánh mặc định. Commit đầu `7eec907`.

### 2026-11-13 — Khảo sát và lập kế hoạch

- Đọc UNI-1630 (PBI-01) kèm UniNote "Quản lý User", UNI-1631 (PBI-02) kèm UniNote "Quản lý danh mục dùng chung", và tóm tắt 26 PBI của project Thi 360 trên uniworkos.
- Khảo sát `reap-elearning`: phần port được (xác thực, bộ lọc bảo mật, quy tắc tên đăng nhập, lớp học, import có kiểm tra trước, nhật ký, danh mục `global_list`, bộ tùy chỉnh giao diện, đơn hàng VNPAY) và phần phải làm mới (phân quyền vai trò → quyền, lưu đợt cấp hàng loạt, phiên bản mã tổ hợp, ngân hàng câu hỏi).
- Đo phong cách giao diện từ hsavnu.edu.vn → `DESIGN.md`.
- Viết `PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`, `RULES.md`, `TASK.md`, spec và plan đợt 1.
- Tạo trên UniAI (project Thi 360): issue cha **UNI-2537**, sub-issue **UNI-2538 → UNI-2553** (T01–T16), **UNI-2554** (đăng nhập Google, backlog).

## Quyết định đã chốt

Danh sách đầy đủ ở `PRD.md` mục 6 (P1–P12) và spec mục 14. Tóm tắt các điểm ảnh hưởng tới cách viết mã:

| Điểm | Chốt |
|---|---|
| Kiến trúc | Modular monolith + worker; Keycloak chỉ xác thực; outbox trong PostgreSQL; một UI kit Tailwind |
| Phân quyền | **Quyền = đối tượng × mức (Xem / Xem và sửa) + quyền riêng; đợt 1 có 13 quyền.** Quyền cố định theo mã nguồn, vai trò tự do theo admin. Vai trò → quyền, có gán quyền trực tiếp cho user. Vai trò có trường `access` (`MANAGE`/`EXAM`). `PLATFORM_ADMIN` nằm ở realm `master` của Keycloak (realm `thi-360` chứa mọi tài khoản còn lại), là người khởi tạo, mọi quyền, không có trong danh sách user |
| Trạng thái tài khoản | `ACTIVE` / `INACTIVE` / xóa mềm. Có giao dịch thì không xóa được |
| Dùng lại tên đăng nhập, email | Được, sau khi xóa mềm (chỉ mục duy nhất `WHERE deleted_at IS NULL`) |
| Mật khẩu | Người tạo nhập theo quy tắc (≥ 8, hoa, thường, số, ký tự đặc biệt); cấp hàng loạt lấy từ cột trong tệp, trống thì hệ thống sinh; bắt đổi lần đầu là tùy chọn, mặc định không; file kết quả không hết hạn |
| Lớp | Một học viên thuộc nhiều lớp |
| Danh mục | Môi trường thật chỉ có nhóm; giá trị mẫu chỉ nạp ở `dev`, `test` |
| Nhật ký | Lưu vĩnh viễn, phân vùng theo quý, lưu trữ theo năm |
| Giao diện | Tùy chỉnh toàn hệ thống, là mục trong menu chính (không ở menu profile, không nút nổi); nút chính `#059669` |
| Menu admin | Phân quyền là một menu bốn tab (Vai trò, Quyền, Người dùng theo quyền, Gán hàng loạt); Cấp hàng loạt là nút trong màn Tài khoản |
| Google | Backlog; khi bật thì gộp theo email |
| Đăng nhập | Một nơi đăng nhập ở tên miền chính cho cả hai web; điều hướng theo `access` của vai trò hoặc `callbackUrl`; trang đích kiểm tra quyền (spec mục 15, T18) |

## Chờ người khác

| Việc | Ai |
|---|---|
| Báo BA cập nhật SRS ở 4 điểm khác bản gốc (`PRD.md` mục 7) | M.Trường |
| Giá trị danh mục thật (dạng câu, dạng bài, môn của từng tổ hợp) nhập trên web khi có màn Cấu hình chung | Admin / BA |
| Logo Thi 360 (không dùng logo HSA Education) | Chưa có người nhận |

## Lỗi đã gặp

| Ngày | Lỗi | Xử lý |
|---|---|---|
| 2026-11-13 | Image `minio/minio` và `quay.io/minio/minio` trả `unauthorized` (MinIO ngừng phát hành image công khai) | Dùng `cgr.dev/chainguard/minio:latest`. Chưa ghim được phiên bản vì bản miễn phí chỉ có tag `latest`; cần xem lại trước khi lên môi trường thật |
| 2026-11-13 | `pnpm install` bỏ qua script build của `sharp` | Thêm `onlyBuiltDependencies: [sharp]` vào `pnpm-workspace.yaml` |

## Ghi chú cho phiên sau

- Trang uniworkos cần đăng nhập; đọc task và UniNote qua trình duyệt của Orca, không gọi được API trực tiếp.
- `uniai` CLI đang trỏ workspace `uni2026`; project Thi 360 có id `9c107463-64c7-4a3a-bb62-d4466e897954`.
- Thư mục `reap-elearning/lms-core` và `lms-exam` ở gốc chỉ có một file test lạc; mã thật nằm trong `reap-elearning/be-elearning/`.
- Chưa có SRS-HSA-QDA-001 bản đầy đủ trong repo; mới có nội dung trích trong các PBI.
