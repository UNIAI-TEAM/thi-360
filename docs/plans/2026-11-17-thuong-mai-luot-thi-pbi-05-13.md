# Plan triển khai: Đợt 3, thương mại và lượt thi (PBI-05 đến PBI-13)

> **Ngày:** 2026-11-17 · **Issue đặc tả:** UNI-2622. Issue cha của đợt và các sub-issue do Advisor tạo ở bước 0 (A4).
>
> **Spec:**
> - `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md`, gọi tắt **TM**;
> - `docs/specs/2026-11-16-luot-thi-pbi-10-13.md`, gọi tắt **LT**;
> - `docs/specs/2026-11-16-be-pbi-03-04.md`, gọi tắt **BE** (bản trên `develop` sau khi đợt 2 và đợt 2B về);
> - `docs/specs/2026-11-17-pbi04-dot-2b.md`, gọi tắt **2B**: bài thi sau đợt 2B;
> - `docs/specs/2026-11-19-dot-3-bo-sung.md`, gọi tắt **BS**: quyết định ngày 2026-11-19 và phần học viên của H-SCA, V-SAT. Khác với TM, LT thì theo BS.
>
> **Trạng thái:** bản nâng cấp ngày 2026-11-18 theo khuôn `zone17-builder`, **cập nhật ngày 2026-11-19 theo câu trả lời của M.Trường (BS): 44 task, 10 sóng, 138 điểm; M.Trường duyệt G1b, G2 ngày 2026-11-19 sau ba vòng review của Codex, sau đó thêm task TS (seed đề test, UNI-2701) và FU (việc tồn đọng 2B, UNI-2700)**. Bản trước M.Trường duyệt ngày 2026-11-17 (G1). Chưa chạy; chờ G0, và G0b cho các task đụng `exam`. Đã qua review lượt 1 của Fable (3 Chặn, 18 Nên sửa, 8 Gợi ý; đã xử lý hết, xem Phụ lục). Sửa ngày 2026-11-17 theo đợt 2B: cổng G0b; EX-1, EX-2, DS, AT-1, AT-2a, AT-2b, FS-5, FA-4; thêm CM-6, FB; trọng số (Phụ lục, dòng 11 đến 15). Sửa theo review 2B lượt 1: G0b sinh lại client; FS-4 không sửa web-admin (FA-1 đổi import); CM-2, CM-3a, CM-4a tạo bảng theo hình dạng có bộ đề.
>
> **Cách dùng:**
> - Plan này đủ để một phiên Advisor chạy pha cook của `zone17-builder` mà không phải hỏi lại về nội dung.
> - Bản nâng cấp ngày 2026-11-18 thêm vào mỗi task: mã tiêu chí `AC-n`, Đường dẫn sở hữu, Ảnh hưởng ngoài task, Kế hoạch kiểm thử; thêm cột Điểm và Rủi ro ở C1, bảng phủ đặc tả ở C5. Nội dung đổi theo các quyết định sau ngày 2026-11-17 ghi ở Phụ lục, mục "Bản nâng cấp ngày 2026-11-18".
> - Plan và spec khác nhau về *cách làm* thì theo plan. Khác nhau về *hành vi nghiệp vụ* thì theo spec và báo lại.
> - Quy ước kỹ thuật của đợt 1 (`docs/plans/2026-11-13-base-port-pbi-01-02.md` Phần B) và đợt 2 (`docs/plans/2026-11-16-be-pbi-03-04.md` Phần B) vẫn áp dụng. Phần B ở đây chỉ ghi phần thêm hoặc khác.

## Mục lục

- Phần A: cấu hình đợt chạy (nhánh, cổng chặn, ủy quyền, theo dõi, trọng số, môi trường, nghiệm thu chung)
- Phần B: quy ước kỹ thuật thêm cho đợt này
- Phần C: pipeline: task nào chạy song song, task nào chờ; bảng phủ đặc tả
- Phần D: hợp đồng từng task
- Phần E: rủi ro và cách xử lý khi kẹt
- Phụ lục: quyết định ngày 2026-11-17, bản nâng cấp ngày 2026-11-18 và nhật ký review

---

# Phần A: Cấu hình đợt chạy

## A1. Kho mã và nhánh

| Mục | Giá trị |
|---|---|
| Repo | `https://github.com/UNIAI-TEAM/thi-360.git`. Bản local `D:\.Vietants_Project\cong-thi-360` không dùng để làm lane. |
| Nhánh tích hợp | `integration/dot-3`, tách từ `origin/develop` **sau** cổng G0 (A2). Advisor tạo và quản lý. Khi đợt 2B về `develop` (G0b), Advisor merge `develop` vào `integration/dot-3` (không rebase) trước khi mở EX-1, EX-2. |
| Nhánh lane | `feature/UNI-<số>-<slug>`, tách từ đầu `integration/dot-3` lúc lane bắt đầu |
| Đích merge của lane | `integration/dot-3`, qua PR, **squash** |
| Về `develop` | Một PR `integration/dot-3` → `develop` khi xong mốc M5, hoặc theo mốc nếu người yêu cầu. CI phải xanh trên PR này. |
| Thư mục đợt chạy | `D:/.Vietants_Project/.thi360-dev/dot-3/` (manifest, sổ tiến độ, hợp đồng, báo cáo, bằng chứng VNPAY). Worktree ở `.thi360-dev/worktrees/`. |

- Không rebase hay force-push nhánh tích hợp.
- Lane rebase lên đầu nhánh tích hợp trước khi nộp.

## A2. Cổng chặn (người phải làm trước)

| Cổng | Điều kiện | Chặn task nào |
|---|---|---|
| **G0** | **Đã qua ngày 2026-11-17.** Đợt PBI-03, PBI-04 cook xong: `integration/dot-2` đã merge về `develop` (PR #63). PR spec của UNI-2622 (hai spec TM, LT và plan này) đã merge. | Cả đợt. M.Trường chốt ngày 2026-11-17: làm đợt này sau khi đợt 2 xong, để không xung đột. |
| **G0b** | **Đã qua ngày 2026-11-19:** PR #120 merge vào `develop` (merge commit `1774c74d`, CI xanh trên `7fd84c28`). Việc còn lại khi mở cook: merge `develop` vào `integration/dot-3` và sinh lại API client như dưới. Đợt 2B xong, gồm phần bổ sung V-ACT, THPTQG (`docs/plans/2026-11-18-pbi04-dot-2b-bo-sung-vact-thpt.md`) và phần bổ sung H-SCA, V-SAT (`docs/plans/2026-11-18-pbi04-dot-2b-bo-sung-hsca-vsat.md`), tổng 160 điểm: `integration/dot-2b` đã merge về `develop` (`docs/plans/2026-11-17-pbi04-dot-2b.md`), và `develop` đã merge vào `integration/dot-3`. Sau khi merge, Advisor sinh lại `openapi.json` và `packages/api-client` trên `integration/dot-3` (hai bên đều đã sinh lại, nên xung đột ở các tệp này giải bằng cách sinh lại, không sửa tay), chạy kiểm tra web-admin và web-student, rồi mới mở EX-1. | EX-1, EX-2 và mọi task phụ thuộc vào hai task này (DS, CM-1, FA-1, AT-*, các task sau). Không chặn C-0, C-0b, ML, FS-4, VP-1, CM-4a, ID-1, ID-2, FS-2. |
| **G0c** | **Đã chuyển thành task TS ngày 2026-11-19.** M.Trường quyết: seed `TEST-*` (UNI-2701) làm ở bước đầu của cook đợt 3, tính 3 điểm; chỉ EX-1, EX-2 và các task sau chúng chờ TS, các task khác mở song song. | Không còn là cổng |
| **G1** | M.Trường duyệt plan này. Các phần thêm vào module `exam` (TM Q11, Q15; LT R11) đã được duyệt ngày 2026-11-17. | Cả đợt |
| **G1b** | M.Trường duyệt bản nâng cấp ngày 2026-11-18 (lần duyệt ngày 2026-11-17 không phủ các phần thêm và các thay đổi sau ngày đó). Duyệt plan không phải là lệnh bắt đầu cook. | Cả đợt |
| **G2** | Khóa sandbox VNPAY (`THI360_VNPAY_TMN_CODE`, `THI360_VNPAY_HASH_SECRET`) có trong biến môi trường của máy chạy VP. Merchant sandbox trước đây của dự án reap, từ ngày 2026-11-17 chỉ dùng cho Thi 360 (mỗi Terminal Code chỉ có một URL IPN). M.Trường đã chỉ nơi lấy khóa (ghi trong hợp đồng lane ở thư mục đợt chạy, **không** ghi vào repo). URL IPN `https://thi360.zone17.click/api/v1/public/payments/vnpay/ipn` **đã đặt** trên trang merchant sandbox ngày 2026-11-17, thay URL cũ của reap. Phần còn lại của G2 là nạp khóa vào biến môi trường của máy chạy VP. | VP-1, VP-2. CM-3a, CM-3b làm với cổng giả lập, không chờ G2. |
| **G3** | **Đã cho phép ngày 2026-11-17.** Đưa `integration/dot-3` lên bản demo trên `bro` (`thi360.zone17.click`; sau này có thể chuyển sang VPS khác) và thêm một khối `location` nginx đưa `/api/v1/public/payments/vnpay/` thẳng tới backend (B6). Đang trong giai đoạn phát triển, chưa có dữ liệu thật: không cần giữ dữ liệu cũ của demo. Bản demo giữ `integration/dot-3` sau VP-2. VNPAY môi trường thật làm sau, khi M.Trường báo. | Chỉ VP-2 |

Mã cổng G1, G2, G3 ở bảng này là mã của plan (đã được nhiều tài liệu trỏ tới), không phải số cổng của `zone17-builder`.

**Câu hỏi còn mở nhưng không chặn** (làm theo cách tạm ghi trong spec, đặt sau cấu hình hoặc một hàm riêng để đổi được):

- TM Q1–Q10, Q12, Q13, Q16, Q17; LT R1–R4, R7–R10.
- TM Q9, Q14 (VNPAY): M.Trường đang hỏi VNPAY. Đợt vẫn chạy theo cách tạm; phải có trả lời trước khi lên môi trường thật.
- Đã chốt, không còn mở: TM Q11, Q15; LT R5, R11, R12 (bỏ theo 2B K2), R6 (kết quả V-ACT, BS 3), R13 (làm tuần tự theo LT L7, riêng THPTQG chuyển tự do theo BS 4), R14 (theo BE mục 12, LT F22 và cờ Giữ thứ tự phương án); sinh đề chạy nền (LT L8).

**Câu hỏi của bản nâng cấp ngày 2026-11-18: M.Trường đã trả lời hết ngày 2026-11-19.** Không còn cách tạm nào ở các mục này; nội dung đầy đủ ở BS.

| Mã | Câu hỏi | Trả lời | BS | Task |
|---|---|---|---|---|
| OQ-1 | Bài ôn có bán, có vào bộ đề không | Bán lẻ, chọn được vào bộ; thẻ và trang bộ có nhãn "Bài ôn" | 2 | EX-1, CM-1, CM-6, FS-1, FB, DS |
| OQ-2 | Kết quả V-ACT | "Điểm luyện tập" trên thang 1.200 kèm ghi chú, 4 thành phần, xem riêng 12 và 18 câu | 3 | AT-4, FS-6 |
| OQ-3 | THPTQG có làm tuần tự không | Riêng THPTQG chuyển tự do đến khi nộp hoặc hết giờ | 4 | AT-1, AT-8, FS-5 |
| OQ-4 | Số thứ tự câu | Chạy suốt bài ở mọi đề | 5 | AT-1, AT-2b, FS-5, FS-6 |
| OQ-5 | `wordLimit` | Số chữ tối thiểu, chỉ nhắc, không chặn nộp | 6 | FS-4, FS-5, FA-4 |
| OQ-6 | Vai trò mặc định của chín quyền | Theo bảng BS 7; `ORDER.REFUND` chỉ `system_admin` | 7 | C-0 |
| OQ-7 | Quy tắc E24 | Áp cả lần công bố đầu; đường chưa đủ điều kiện được để lại | 8 | EX-1 |
| OQ-8 | Hạn đề làm thử | Bấm Làm thử thì giữ bằng thời lượng bài cộng 30 phút | 9 | EX-2, AT-5, FA-4 |
| OQ-9 | Dữ liệu màn Khuyến mãi | Thêm cảnh báo giá sàn, tổng tiền đã giảm, lịch sử thay đổi | 10 | CM-2, FA-2 |
| OQ-10 | Số đếm màn chấm | Thêm API đếm theo cùng bộ lọc | 11 | AT-5, FA-4 |
| OQ-11 | Màn Sinh thử | Chuyển sang giao diện câu hỏi dùng chung trong đợt 3 | 12 | FA-5 |
| OQ-12 | Tab Hồ sơ học tập | Nối Quyền sử dụng và Lịch sử lượt thi | 13 | LP |
| OQ-13 | Tiền về trong các ca TM chưa nêu | Mỗi đơn tính một lần thanh toán thành công; khoản thu thừa hoàn riêng; lần đã ghi thất bại chờ vận hành đối soát | 14 | CM-3a, CM-3b, CM-5, FA-3 |
| OQ-14 | Ảnh trong lời giải | Đường tải riêng, không thỏa điều kiện thì 404 | 15 | AT-4, FS-6 |
| OQ-15 | Nhóm menu của Lượt thi | Nhóm mới "Học viên" | 16 | FA-1 |

**Câu hỏi mở còn lại (không chặn):** BA xác nhận luật nửa điểm chọn nhiều của H-SCA (2B bổ sung Q-32).

Các câu nhỏ hơn của từng task nằm ở dòng **Câu hỏi mở** cuối hợp đồng task đó (Phần D).

## A3. Ủy quyền cho đợt chạy

Khi người bảo bắt đầu, plan này là yêu cầu rõ ràng cho đợt chạy. Các việc sau được phép mà không hỏi lại:

- Tạo worktree, nhánh lane; commit; push nhánh lane và nhánh tích hợp.
- Mở PR lane → `integration/dot-3`; Advisor merge sau khi nghiệm thu.
- Trên UniAI: tạo issue cha và sub-issue theo A4; đặt `in_progress`, `in_review`, `blocked`; bình luận `[agent]`.
- Chạy Docker Compose dev (có Mailpit); cài phụ thuộc bằng `pnpm` và Maven.
- Thêm thư viện đã nêu tên ở B7. Thư viện khác phải nêu lý do trong PR.
- Sửa BE spec ở các chỗ TM Q11, Q15, LT R11 đã liệt kê. Sửa spec ở những chỗ plan này ghi "sửa spec". Sửa lỗi chính tả, liên kết.
- Gọi API VNPAY **sandbox** (VP-1, VP-2), sau G2.
- Cho VP-2 (G3 đã có): đưa `integration/dot-3` lên bản demo trên `bro` bằng `demo-setup.sh` sẵn có trên máy demo (`~/deploy/thi360-demo/`, ngoài repo), và thêm khối `location` nginx đã nêu ở G3. Luôn chạy `nginx -t` trước khi nạp lại, vì nginx này dùng chung với dự án khác.

Không được phép (dừng và báo):

- Merge hay push vào `develop`, `main`; force-push; bỏ qua hook; xóa nhánh không do mình tạo.
- Chuyển issue sang `done`.
- Đổi hành vi nghiệp vụ trong spec, `PRD.md`, `RULES.md`.

  Được sửa:
  - `TASK.md`, `PROGRESS.md`;
  - plan này (tick, ghi lỗi);
  - `DESIGN.md` (quy ước giao diện mới);
  - `ARCHITECTURE.md`, khi cấu trúc thật khác tài liệu.
- Sửa migration đã nằm trên `integration/dot-3` hoặc `develop`. Muốn sửa thì thêm migration mới.
- Ghi bí mật thật (khóa VNPAY, `THI360_TOKEN_PEPPER` thật, mật khẩu SMTP) vào repo, log, bình luận issue, báo cáo lane. Bằng chứng VNPAY phải che khóa và chữ ký trước khi lưu.
- Gọi API VNPAY môi trường thật (production).
- Gửi thư tới địa chỉ thật. Dev và test chỉ dùng Mailpit.
- Deploy lên `bro`, trừ trường hợp VP-2 sau G3.
- Mở máy chạy lane ra Internet (đường hầm như ngrok, cloudflared). IPN sandbox chỉ đi qua bản demo (G3).

## A4. Theo dõi công việc

- **Bước 0 của Advisor** (khi người bảo chạy):
  1. Tạo issue cha "Thi 360 · Đợt 3 · Thương mại và lượt thi (PBI-05 đến PBI-13)" trong project Thi 360.
  2. Tạo 43 sub-issue theo bảng A5, tiêu đề `<mã task> · <tên>`.
  3. Ghi mã issue vào bảng A5, `TASK.md` (mục mới "Đợt 3"), `PROGRESS.md`.
  4. Liên kết UNI-2622 vào issue cha bằng bình luận.
- **Mỗi lane:**
  - mở bằng `uniai issue get UNI-<số> --output json`;
  - bắt đầu thì `in_progress`;
  - mở PR thì `in_review` kèm URL;
  - mỗi lần dừng bình luận `[agent]`: đã làm gì, lệnh đã chạy thật và kết quả, còn gì, kẹt gì.
- **Commit:** theo Conventional Commits. Hook `.githooks/prepare-commit-msg` thêm `Refs: UNI-<số>`; mỗi worktree chạy `git config core.hooksPath .githooks`.
- **Tiêu đề PR:** `UNI-<số>: <mô tả>`.
- Sau mỗi lần merge vào nhánh tích hợp, Advisor cập nhật `TASK.md` và `PROGRESS.md` trên nhánh tích hợp.

## A5. Sổ tiến độ (trọng số)

Tổng 138 điểm, 44 task (bản ngày 2026-11-19; bản ngày 2026-11-18 là 103 điểm, 36 task). Chỉ tính điểm khi Advisor đã nghiệm thu và merge vào `integration/dot-3`. Phần trăm tiến độ tính trên 138. Trọng số sửa ngày 2026-11-17 theo đợt 2B: EX-2, AT-2b, FS-5 giảm 1 vì phần định hướng Tin học và cảnh báo chấm tay chuyển đi; thêm CM-6 (3), FB (2); FA-4 tăng 1 vì có thêm làm thử như học viên. Bản nâng cấp ngày 2026-11-18 **giữ nguyên trọng số** dù có chuyển việc giữa các task (Phụ lục).

| Task | Tên | Nhóm | Điểm |
|---|---|---|---|
| C-0 | Khung module `commerce`, `attempt`; quyền bit 18–26; hợp đồng SPI; `StudentLearningProvider` nhận danh sách; pepper; ngôn ngữ ưa dùng; danh sách IP miễn giới hạn tần suất | Nền | 3 |
| C-0b | Profile `e2e`, đồng hồ thử, `/api/v1/test/**`, `ForbiddenKeysAssert` | Nền | 1 |
| ML | Gửi thư `shared/mail` (hàng thư, worker, SMTP, mẫu vi/en, endpoint đọc thư cho E2E) | Nền | 3 |
| TS | Dựng lại seed đề test `TEST-*` theo mẫu BA (UNI-2701, cổng G0c cũ) | Nền | 3 |
| FU | Việc tồn đọng của đợt 2B (UNI-2700, 41 mục) | Tồn đọng 2B | 8 |
| EX-1 | Module `exam`: thông tin bán, ảnh bìa (kèm dọn ảnh bỏ), kiểm tra khi công bố, quy tắc E24, sự kiện, `PublishedExamPort`, đọc giờ qua `Clock` | Nền | 4 |
| EX-2 | Module `exam`: `PaperSolverPort` trên mô hình 2B, `PreviewPaperPort`, đo hiệu năng | Nền | 2 |
| DS | Bộ dữ liệu bài đã công bố cho 9 kỳ thi, dùng cho test, dev, E2E | Nền | 4 |
| VP-1 | Thử VNPAY sandbox trước khi làm cổng thật; vector chữ ký thật | Nền | 1 |
| ID-1 | Đăng ký, xác minh email, đổi email | Tài khoản | 3 |
| ID-2 | Quên và đặt lại mật khẩu, hồ sơ, ảnh đại diện | Tài khoản | 2 |
| CM-1 | Bảng chiếu sản phẩm, API công khai | Thương mại | 3 |
| CM-2 | Khuyến mãi, voucher, tính giá, cảnh báo giá sàn, tổng tiền đã giảm, lịch sử thay đổi | Thương mại | 5 |
| CM-4a | Bảng quyền dùng, `EntitlementPort`, cấp quyền nội bộ | Thương mại | 2 |
| CM-4b | `exam-access`, `/me/exams` | Thương mại | 2 |
| CM-3a | Đơn hàng, cổng giả lập, IPN, trang quay lại, đơn 0 đồng, cấp quyền sau thu tiền, quy tắc một khoản được tính mỗi đơn | Thương mại | 5 |
| CM-3b | Cổng VNPAY thật, `querydr`, đơn hết hạn, tiền về muộn sau hết hạn | Thương mại | 3 |
| CM-5 | Quản lý đơn, người mua, hoàn tiền, tiếp tục cấp quyền, đối soát, xử lý tiền về trên lần đã ghi thất bại | Thương mại | 5 |
| VP-2 | Luồng VNPAY sandbox thật có IPN, qua bản demo | Thương mại | 1 |
| CM-6 | Bộ đề: bảng, công bố, bảng chiếu, đơn nhiều bài, cấp quyền từng bài, hoàn tiền, voucher phạm vi bộ | Thương mại | 3 |
| AT-1 | Lượt thi: bảng, chuẩn bị, bắt đầu và sinh đề chạy nền, đảo, trạng thái, `AttemptGuard` | Lượt thi | 5 |
| AT-2a | Chuyển phần tuần tự, hoàn thành phần, nộp, đóng lượt, worker hạn giờ | Lượt thi | 4 |
| AT-2b | Chọn phần 3, thử lại, sinh lỗi | Lượt thi | 2 |
| AT-3 | Lưu bài, phiên sửa, tiếp quản, tài nguyên của lượt | Lượt thi | 4 |
| AT-4 | Chấm tự động, tổng hợp, kết quả, xem lại, lịch sử, ảnh lời giải, xem riêng 12 và 18 câu | Lượt thi | 5 |
| AT-5 | Hàng chờ chấm tự luận, số đếm, chấm lại, vận hành lượt, API chấm và kéo dài đề làm thử | Lượt thi | 4 |
| AT-6 | Phát audio có giới hạn lần nghe, kiểm tệp audio khi mở lượt | Lượt thi | 3 |
| AT-7 | Chấm H-SCA, V-SAT, kết quả theo phân đoạn | Lượt thi | 2 |
| AT-8 | THPTQG chuyển tự do giữa các phần | Lượt thi | 2 |
| FS-1 | Web học viên: trang giới thiệu, danh mục, chi tiết; `proxy.ts`; thanh đầu trang | Web học viên | 3 |
| FS-2 | Web học viên: đăng ký, xác minh, quên mật khẩu, hồ sơ | Web học viên | 3 |
| FS-3 | Web học viên: xác nhận mua, kết quả thanh toán, Bài thi của tôi, đơn của tôi | Web học viên | 3 |
| FS-4 | `packages/ui`: thành phần 8 dạng câu (làm bài và chỉ đọc); đưa bộ hiển thị nội dung (`RichTextView`, mọi loại nút, KaTeX, mhchem) vào `packages/ui` | Web học viên | 3 |
| FS-5 | Web học viên: chuẩn bị (có chọn định hướng Tin học), chờ sinh đề, màn làm bài, lưu, ngoại tuyến, tiếp quản | Web học viên | 4 |
| FS-6 | Web học viên: kết quả, xem lại, lịch sử, ảnh lời giải, xem riêng 12 và 18 câu | Web học viên | 3 |
| FS-7 | Web học viên: audio, phân đoạn, nhập ký hiệu và kết quả của H-SCA, V-SAT | Web học viên | 3 |
| FB | Bộ đề trên web: màn admin "Bộ đề"; thẻ, chi tiết, mua bộ và nhóm thẻ theo bộ ở web học viên | Web học viên | 2 |
| FA-1 | Web admin: tab Thông tin bán, ảnh bìa, cảnh báo chấm tay, menu của đợt | Web admin | 2 |
| FA-2 | Web admin: Khuyến mãi | Web admin | 3 |
| FA-3 | Web admin: Đơn hàng, người mua, hoàn tiền, đối soát tiền về muộn | Web admin | 4 |
| FA-4 | Web admin: hàng chờ chấm thật, vận hành lượt, làm thử như học viên (phần web) | Web admin | 3 |
| FA-5 | Web admin: màn Sinh thử dùng giao diện câu hỏi chung, audio khi làm thử | Web admin | 2 |
| LP | Web admin: tab Hồ sơ học tập nối Quyền sử dụng và Lịch sử lượt thi | Web admin | 2 |
| QA | E2E đầu cuối, kiểm thử bảo mật, tài liệu | Nghiệm thu | 4 |

Cộng theo nhóm: Nền 21, Tài khoản 5, Thương mại 29, Lượt thi 31, Web học viên 24, Web admin 16, Nghiệm thu 4, Tồn đọng 2B 8. Tổng 138.

## A6. Môi trường thực thi

- **Cần có:** JDK 21, Node ≥ 22, pnpm 10, Docker, `gh`, `uniai`, Python 3, `git`, `grep`.
- **Dịch vụ dev:** `docker compose -f infra/docker-compose.yml up -d`, có sẵn Mailpit.
  - **Mỗi lane backend chạy compose project riêng** (`-p thi360-<task>`, cổng lệch theo lane, ghi trong hợp đồng lane), như đợt 2.
  - Lane chỉ làm web có thể dùng chung bộ dịch vụ của nhánh tích hợp.
- **Test tích hợp backend** dùng Testcontainers.
- **Lệnh kiểm tra chuẩn,** mọi lane chạy trước khi nộp:
  - Backend: `cd backend && ./mvnw -B -ntp verify`. Nhóm `perf` chạy riêng như đợt 2.
  - Web: `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build`
  - E2E (FS-*, FA-*, QA): `pnpm --filter @thi360/e2e test`
- **CI** chỉ chạy trên `develop` và PR vào `develop`. Bằng chứng của lane là báo cáo của Cursor cloud runner (profile `thi360`, `--repo thi360`) chạy đúng lệnh chuẩn trên SHA đầu nhánh lane, kèm log.
- **Profile `e2e` của backend** (C-0b tạo; chưa có trên `develop`). `backend/src/main/resources` không có tệp `application-<profile>.yml`: mỗi profile là một khối `on-profile` trong `backend/src/main/resources/application.yml`, profile `e2e` cũng vậy:
  - đồng hồ điều khiển được và `POST /api/v1/test/clock` (C-0b);
  - đọc thư `GET /api/v1/test/mail` (ML);
  - cổng VNPAY giả lập (CM-3a).

  Mọi đường `/api/v1/test/**` chỉ tồn tại khi bật profile `e2e`. Profile này bật cùng một profile của môi trường thật (`prod`) thì ứng dụng không khởi động. Repo chưa có profile `prod`, nên môi trường thật bắt buộc phải bật profile này thì chốt chặn mới có tác dụng; QA ghi vào tài liệu triển khai.
- **Lệnh chạy một phần:** một lớp test tích hợp `./mvnw -B -ntp verify -Dit.test=<Lớp> -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; một tệp test web `pnpm --filter <gói> test <tệp>` (không thêm `--`). Các lệnh trong Phần D chưa được chạy thử khi viết plan; lane sửa lại cho đúng nếu cần và ghi vào báo cáo.
- **Mã OTP cố định** `THI360_DEV_EMAIL_CODE` chỉ ở profile `dev` (TM 5.3). E2E đọc mã thật qua `/api/v1/test/mail`.
- **Số lane chạy cùng lúc** theo tải máy đo được, không cố định.

## A7. Nghiệm thu chung của một lane

1. Đúng "Sản phẩm" và "Nghiệm thu" của task ở Phần D, và đúng các mục spec được trỏ tới.
2. Lệnh kiểm tra chuẩn xanh, kết quả dán trong bình luận issue.
3. **Endpoint mới:**
   - có `@RequirePermission` (admin), hoặc kiểm tra quyền sở hữu (`/me/**`);
   - có nhật ký cho lệnh ghi;
   - có trong OpenAPI (trừ `/api/v1/test/**`);
   - có test 401 và 403 (từng quyền riêng lẻ);
   - endpoint `/me/**` có test "tài khoản khác nhận 404".
4. **Endpoint công khai:** nằm trong danh sách mở của `SecurityConfig`, `CookieAuthFilter` và `CsrfFilter` (lệnh POST công khai), có giới hạn tần suất, có test gọi không có cookie và test POST kèm cookie cũ, không có XSRF.
5. **Lệnh ghi có idempotency** (B4): có test gửi lại cùng khóa, và cùng khóa khác thân. Lệnh có `expectedRevision` hay `expectedVersion`: có test xung đột.
6. **Migration mới** chạy được trên DB của đợt 2 (Testcontainers chạy toàn bộ migration từ đầu, cộng dữ liệu mẫu). Test không giả định bảng rỗng.
7. **Màn web:**
   - đủ bốn trạng thái (đang tải, rỗng, lỗi, có dữ liệu);
   - chỉ dùng token của `packages/ui`;
   - đúng ở **cả sáng và tối**;
   - **không lẫn ngôn ngữ** ở chữ giao diện (mọi khóa có ở `vi.json` và `en.json`). Nội dung đề không làm song ngữ (M.Trường, 2026-11-19): câu hỏi, tên bài, tên phần, mục, nhóm, phân đoạn lấy từ mẫu hoặc do admin gõ luôn hiện như đã lưu, không tính là lẫn ngôn ngữ. Tên lấy từ danh mục dùng chung (kỳ thi, môn) vẫn song ngữ, hiện bằng `pickName` của `@thi360/web-kit`;
   - **lane có màn hiện tên danh mục dùng chung hoặc nội dung đề** (FS-1, FS-3, FS-5, FS-6, FS-7, FB, FA-4, FA-5, LP) tự nối ở màn của mình (review vòng 4, P4-9): gọi `pickName` cho tên danh mục; gắn `data-exam-content` đúng vào phần tử chứa nội dung đề, không bọc cả khung có nhãn giao diện. Test của lane (E2E hoặc test thành phần) dùng một mục có `nameEn` khác `nameVi` và một mục `nameEn` trống, kiểm ở `en`; phép quét lẫn ngôn ngữ của `visual-matrix.ts` vẫn bắt một nhãn giao diện tiếng Việt ngoài vùng nội dung đề;
   - đúng ở 390px và 1280px.
8. `ApplicationModules.verify()` đạt.
9. Không còn `TODO` thiếu mã issue, không còn mã chết.
10. **Không có bí mật trong diff.** Lane chạy và dán kết quả (chỉ là số dòng khớp, không in nội dung):

    ```
    git diff origin/integration/dot-3...HEAD | grep -ciE "hash_secret=.+|pepper=.{8,}|smtp_password=.+|tmn_?code=[A-Z0-9]{8}"
    git diff --name-only origin/integration/dot-3...HEAD | grep -cE "(^|/)\.env$"
    ```

    Cả hai phải ra `0`. Riêng QA so với `origin/develop`, vì ở đầu nhánh tích hợp diff so với chính nó là rỗng. Giá trị dev công khai (B6) đặt trong khối `on-profile` của `dev`, `e2e`, `test` ở `application.yml`, dưới khóa YAML lồng nhau (không có dạng `pepper=`), giá trị bắt đầu bằng `dev-only-`.
11. **Kiểm chất lượng theo hợp đồng task** (Phần D):
    - mỗi `AC-n` có test nêu trong Kế hoạch kiểm thử đã chạy, kết quả nằm trong báo cáo lane;
    - mọi dòng Ảnh hưởng ngoài task được chạy lại (đây là phạm vi hồi quy của task); dòng có chủ là task khác thì task đó chạy;
    - task rủi ro **cao** có bằng chứng "đỏ trước" cho các tiêu chí được đánh dấu: test chạy đỏ trên code trước thay đổi, xanh sau thay đổi;
    - lane chỉ sửa trong Đường dẫn sở hữu; cần sửa ngoài vùng thì báo Advisor trước;
    - test bị sửa hoặc xóa phải nêu lý do trong PR.

---

# Phần B: Quy ước kỹ thuật thêm cho đợt này

## B1. Module và schema

- **Module mới:**
  - `vn.unicom.thi360.commerce`: sản phẩm, khuyến mãi, đơn, thanh toán, hoàn tiền, quyền dùng.
  - `vn.unicom.thi360.attempt`: lượt thi, sinh đề, chấm, kết quả, lịch sử, hàng chờ chấm.

  Mỗi module có `api/`, `application/`, `domain/`, `infrastructure/`, `spi/`.
- **Schema** `commerce`, `attempt`: thêm vào `spring.flyway.schemas`. Migration ở `db/migration/commerce/`, `db/migration/attempt/`.
- **Bảng thư** nằm ở schema `shared` (TM 4), migration ở `db/migration/shared/`.
- **Chiều phụ thuộc** (TM 3, LT 3):

  ```
  attempt ──► commerce/spi, exam/spi, questionbank/spi, identity/spi
  commerce ──► exam/spi, catalog/spi, identity/spi
  identity ──► shared/mail
  exam ──► (không phụ thuộc commerce, attempt)
  ```

- **Hợp đồng SPI giữa `commerce` và `attempt`** do C-0 khai đủ ngay từ đầu, trong `commerce/spi`:
  - `EntitlementPort` (commerce cài, attempt gọi). Theo TM 8.5, cộng một hàm đọc cho bước chuẩn bị (LT 5.1):
    - `describe(userId, entitlementId)` → lượt còn lại, lần cấp sẽ dùng (E21), `option_codes`, nguồn (mua hay cấp bù, mã đơn, ngày);
    - `reserve(userId, entitlementId, attemptId)` → `grantId`, quyền lợi, `option_codes`;
    - `confirmUse(attemptId)`, `release(attemptId)`, `benefitsOf(attemptId)`, `compensate(attemptId, adminId, reason)`.
  - `AttemptStatusProvider` (attempt cài, commerce gọi), kèm bản rỗng mặc định. `commerce` lấy bản cài qua `ObjectProvider`.
  - Các hàm chỉ `commerce` dùng (`grantForOrder`, `revokeUnused`) nằm ở service nội bộ `commerce.entitlement.application`, **không** ở `spi`.
  - Đổi chữ ký trong `commerce/spi` sau khi C-0 merge phải qua Advisor; lane đổi phải sửa mọi nơi gọi trong cùng PR.
- **Interface sẵn có trong `identity/spi`** mà module mới cài:
  - `ActiveAttemptChecker`: `attempt` cài (lượt `STARTING` hoặc `IN_PROGRESS`).
  - `StudentLearningProvider`: phần `attempts` do `attempt` cài, phần `entitlements` do `commerce` cài. Mỗi module một bean riêng. Trên `integration/dot-2`, `UserAdminService` nhận **một** bean (`@Autowired(required = false) StudentLearningProvider`), nên có hai bean thì ứng dụng không khởi động: C-0 đổi thành nhận `List<StudentLearningProvider>` và gộp kết quả, trước khi module nào cài.
  - `AccountDeletionGuard`: `commerce` cài. CM-4a kiểm "có quyền dùng", CM-3a thêm "có đơn".
- **Cột `jsonb`** dùng `@JdbcTypeCode(SqlTypes.JSON)` như các module trước. Câu trả lời (`attempt_item.answer`) là `sealed interface AnswerValue` với `record` cho từng dạng (LT 4).
- **Khóa ngoại tới bảng của task sau:** bảng tạo trước có cột tham chiếu nhưng **chưa có khóa ngoại**. Task tạo bảng đích thêm khóa ngoại bằng migration của mình:
  - `promotion_redemption.order_id` (CM-2) và `entitlement_grant.order_id` (CM-4a): CM-3a thêm khóa ngoại tới `purchase_order`.
- **Cổng tạm trong cùng module:** khi một task cần dữ liệu của task sau, task đó khai một interface nội bộ kèm bản tạm, và task sau thay bằng bản thật. Bản tạm phải có test nói rõ hành vi tạm.

  | Interface | Bản tạm (ai làm) | Bản thật (ai thay) |
  |---|---|---|
  | `PriceCalculator` | trả giá gốc (CM-1) | CM-2 |
  | `PurchaseHistory` (E8 "tài khoản mới") | "chưa mua lần nào" (CM-2) | CM-3a |
  | `PendingOrderLookup` (đơn đã thu tiền chưa cấp quyền) | "không có" (CM-4b) | CM-3a |
  | `FinalAnswerWriter` (ghi `finalAnswers` khi hoàn thành phần, nộp bài; LT 6.3, AC-11.08) | không ghi gì (AT-2a) | AT-3, qua `AnswerService` |
  | `OverdueAttemptCloser` (đóng lười phần, bài quá hạn; LT F11) | không làm gì (AT-1) | AT-2a |
  | `AttemptSessionChecker` (kiểm phiên sửa bài) | không làm gì (AT-1) | AT-3 |
  | `PartTransitionHook` (chèn bước chờ chọn phần 3) | không làm gì (AT-2a) | AT-2b |
  | `RefundProgress` (TM 8.5 bước 1) | "chưa có lần hoàn" (CM-3a) | CM-5 |

## B2. Mã lỗi, transaction, khóa

- **Khóa lạc quan:** 412 như đợt 1, 2. **Lỗi nghiệp vụ:** 422 `BusinessRuleException`. **Xung đột trạng thái:** 409 `ConflictException`. Không thêm kiểu lỗi mới. Ngoại lệ có giới hạn: phản hồi TM yêu cầu mà `GlobalExceptionHandler` không biểu diễn được (503 khi Keycloak lỗi, `retryAfter`, `remainingAttempts` ở ID-1; `PriceQuote` và 410 ở đơn hàng của CM-3a) xử lý bằng advice riêng của controller đó, không sửa handler chung.
- **Mã lỗi mới** thêm vào cả ba tệp `messages*.properties`, và khóa web vào `vi.json`, `en.json` của app tương ứng.
- **Hai transaction tách nhau** (TM 8.1): mọi đường thu tiền (IPN, `querydr`, đơn 0 đồng) commit `PAID` trước. Cấp quyền chạy ở `FulfillmentService.fulfill(orderId)` với transaction riêng (`REQUIRES_NEW`), gọi sau commit (`TransactionSynchronization.afterCommit`) và từ worker.
- **Khóa nhiều dòng** theo thứ tự cố định:
  - `purchase_order` → `payment` → `promotion` → `entitlement` → `entitlement_grant`;
  - `attempt` → `attempt_part` → `attempt_item`.
- **Không gọi mạng trong transaction đang giữ khóa dòng.** Áp cho VNPAY, SMTP, Keycloak, MinIO. Gọi trước rồi ghi DB sau, hoặc dùng outbox. Riêng Keycloak khi đăng ký và đổi email theo cách bù của PBI-01.
- **Bộ nhận outbox:** mỗi topic chỉ có một handler (`OutboxRelay.register` lần hai thay handler cũ), nên CM-6 mở rộng bộ nhận của CM-1, không đăng ký lại. Handler chạy trong transaction của relay: bộ nhận ghi dữ liệu trong `REQUIRES_NEW`, nếu không một sự kiện lỗi sẽ chặn cả hàng. Sự kiện phát trước khi có handler bị đánh dấu thất bại, nên sau khi CM-1 merge Advisor chạy `rebuild` một lần.
- **Sinh đề chạy nền** (LT L8, 5.2): `GenerationExecutor` chạy trong tiến trình nhận yêu cầu, số luồng và độ dài hàng giới hạn (`thi360.attempt.generation.*`), nhận việc sau commit. Bean này có ở cả profile `api` và `worker`: việc tự thử lại sinh phần (B5) chạy trong tiến trình worker. Bộ giải chạy ngoài transaction. Không dùng outbox cho việc này vì cần bắt đầu ngay; lượt hoặc phần kẹt do worker dọn (B5). Kết quả sinh chỉ được lưu khi trạng thái và `generation_token` còn khớp (LT 5.2 bước 3), để việc đến muộn không ghi đè lượt đã bị worker hủy.

## B3. Quyền

- **Bit cố định** theo TM 13: 18 `PROMOTION.VIEW`, 19 `PROMOTION.MODIFY`, 20 `ORDER.VIEW`, 21 `ORDER.REFUND`, 22 `ORDER.RECOVER`, 23 `GRADING.VIEW`, 24 `GRADING.MODIFY`, 25 `ATTEMPT.VIEW`, 26 `ATTEMPT.COMPENSATE`.
- **`PermissionObject`** thêm `PROMOTION`, `ORDER`, `GRADING`, `ATTEMPT`.
- **Vai trò mặc định:**
  - Gán qua `PermissionCatalogSync` như đợt 2; `SYSTEM_ADMIN` nhận cả 9 quyền.
  - `SYSTEM_ROLE_DEFAULTS` không có vai trò "Giáo viên" (chỉ có `system_admin`, `content_admin`, `operations`, `student`). Không thêm vai trò mới. Chín quyền mới gán mặc định theo bảng BS 7.
- **Chỉ C-0 sửa** `Permission.java`, `PermissionObject.java`, `Permissions.java`, `PermissionCatalogSync`.

## B4. Idempotency

- Dùng `IdempotentCommand` của đợt 2. Phạm vi khóa `<userId>:<thao tác>:<id đối tượng>`.
- **Lệnh bắt buộc khóa:**
  - TM: tạo đơn, `/pay`, hoàn tiền, tạo khuyến mãi, tiếp tục cấp quyền.
  - LT: bắt đầu lượt, chọn phần 3, thử lại, nộp bài, cấp bù.
- Web sinh `crypto.randomUUID()` một lần cho mỗi lần bấm, gửi lại cùng khóa khi thử lại.

## B5. Đồng hồ và worker

- **Mọi chỗ đọc giờ** dùng bean `Clock` (`ClockConfig` sẵn có). Không dùng `Instant.now()` trực tiếp. Module `exam` còn gọi thẳng `Instant.now()` (như `ExamPublishService`, `ExamDiscontinueService`, `ExamService`, `ExamKindService`): EX-1 đổi sang `Clock`, để giờ công bố và ngừng bán đi theo đồng hồ thử. Module `questionbank` (12 chỗ) không sửa trong đợt này.
- **Profile `e2e`:** bean `MutableClock` thay `Clock`, kèm `POST /api/v1/test/clock` để Playwright tua giờ. Do C-0b làm. Kiểm JWT (`TwoIssuerJwtDecoder`) giữ đồng hồ thật, nếu không tua giờ sẽ làm mọi phiên hết hạn.
- **Worker** chạy ở profile `worker` (`WorkerSchedulingConfig` có `@Profile("worker")`; khối `on-profile: worker & !api` trong `application.yml` tắt máy chủ web), bằng `@Scheduled`. **Chống chạy trùng:**
  - việc theo dòng dữ liệu: lấy dòng bằng `SELECT … FOR UPDATE SKIP LOCKED` theo lô, như `OutboxRelay`;
  - việc không có dòng để khóa (đối soát 02:00): `pg_try_advisory_xact_lock(<hằng số của việc>)`; không lấy được khóa thì bỏ lượt chạy đó;
  - không thêm ShedLock hay thư viện khóa khác.

  | Việc | Chu kỳ | Task |
  |---|---|---|
  | Gửi thư | 5 giây | ML |
  | Cấp quyền lại sau lỗi | 1 phút | CM-3a |
  | Đơn hết hạn và `querydr` | 1 và 5 phút | CM-3b |
  | Đối soát | 02:00 | CM-5 |
  | Lượt `STARTING` quá 2 phút | 30 giây | AT-1 |
  | Đóng phần và bài quá hạn | 10 giây | AT-2a |
  | Thử lại sinh phần, phần kẹt `GENERATING` | 30 giây | AT-2b |
  | Dọn ảnh bìa không còn dùng quá 7 ngày (TM 6.3) | mỗi ngày | EX-1 |

## B6. Bảo mật

- **Endpoint công khai mới** (TM 13) có giới hạn tần suất theo IP qua `RateLimitFilter`. Bộ lọc hiện chỉ xét POST đăng nhập và tải lên: **C-0** thêm chính sách cho GET công khai (120 yêu cầu mỗi phút) và miễn IPN (TM 8.4); **ID-1** thêm giới hạn nhiều khung thời gian cho đăng ký, OTP sau khi C-0 đã merge.
- **IP thật của người gọi:** proxy của web (`packages/web-kit/src/proxy.ts`) bỏ `X-Forwarded-For` do trình duyệt gửi. Nó chỉ đặt lại header này từ `x-real-ip` khi `THI360_TRUST_REAL_IP=1`, hoặc qua tùy chọn `resolveClientIp`. Vì vậy:
  - Giới hạn tần suất theo IP của các endpoint công khai gọi qua proxy web (đăng ký, quên mật khẩu, danh mục phía trình duyệt) chỉ đúng khi nginx đặt `x-real-ip` và web chạy với `THI360_TRUST_REAL_IP=1`. Thiếu thì mọi người dùng chung một IP và chặn lẫn nhau. Tài liệu triển khai (QA) ghi điều kiện này; test của ID-1 và CM-1 đặt header ở tầng backend.
  - Trang công khai dựng phía máy chủ gọi API danh mục từ máy chủ web, luôn cùng một IP. Các lời gọi này được lưu đệm 60 giây; Danh sách IP của máy chủ web được miễn giới hạn tần suất, đặt trong cấu hình (`thi360.security.rate-limit.exempt-ips`, mặc định rỗng). Thuộc tính này chưa có trong code: **C-0** thêm vào `RateLimitProperties` và `RateLimitFilter` (`shared/security`) kèm test; CM-1 chỉ dùng. Mọi IP khác vẫn theo ngưỡng 120 yêu cầu mỗi phút. Test của CM-1 có cả hai trường hợp.
  - IPN và trang quay lại của VNPAY phải tới thẳng backend, không qua proxy của web. Trên bản demo là khối `location` nginx ở G3; tài liệu triển khai (QA) ghi rõ yêu cầu này.
  - Lọc IP của IPN (TM Q9) đọc IP từ kết nối tới backend sau nginx, theo cấu hình `server.forward-headers-strategy` hiện có.
- **Không lộ đáp án** (LT 7.3) và **không lộ dữ liệu ẩn của bảng chiếu** (TM 10.1):
  - C-0b tạo `ForbiddenKeysAssert` ở `backend/src/test/java/vn/unicom/thi360/support/`, quét JSON tìm khóa cấm; danh sách khóa cấm ghi ở một nơi trong lớp này.
  - EX-1, CM-1, CM-6, AT-1, AT-2a, AT-2b, AT-3 và QA dùng lớp này (LT 7.3: cả phản hồi của `choice`, `retry`, `answers`, `finish`, `submit`); task nào cần thêm khóa thì thêm vào danh sách chung.
- **Mã OTP và mã đặt lại** băm HMAC bằng `THI360_TOKEN_PEPPER`.
  - Thiếu biến thì ứng dụng không khởi động.
  - Profile `dev`, `e2e`, `test` có giá trị công khai `dev-only-…` trong khối `on-profile` của profile ở `application.yml`, để compose dev, E2E trên CI và bản demo chạy được. Bản demo dùng giá trị riêng trong `.env` của nó.
  - C-0 cập nhật `.github/workflows/e2e.yml` nếu workflow cần biến này.
- **IPN và trang quay lại:** kiểm chữ ký trước mọi xử lý khác. Ghi `payment_event` cả khi chữ ký sai.
- **`/api/v1/test/**`:** chỉ đăng ký khi bật profile `e2e`; không có trong OpenAPI; profile khác gọi thì 404.
- **Trang `/mock-vnpay`:** chỉ có khi `THI360_VNPAY_MODE=MOCK` và profile là `dev`, `test` hoặc `e2e`. Bản demo chạy profile `dev`, nên ở chế độ `SANDBOX` trang này phải trả 404, để không ai tự ký IPN được (kịch bản VP2-S7).

## B7. Thư viện được thêm

| Nơi | Thư viện | Dùng cho | Task |
|---|---|---|---|
| backend | `spring-boot-starter-mail` | Gửi SMTP | ML |
| backend | `spring-boot-starter-thymeleaf` | Mẫu thư HTML và văn bản (TM 4) | ML |
| backend (test) | `com.icegreen:greenmail-junit5` | Test lớp gửi SMTP, mã 5xx | ML |
| backend | `net.coobird:thumbnailator` | Cắt vuông, thu nhỏ ảnh đại diện | ID-2 |
| packages/ui | `katex` 0.19.1 (chuyển từ web-admin; mhchem tải khi cần qua `katex/contrib/mhchem`) | Công thức toán và hóa trong câu hỏi | FS-4 |
| web-student | `@tiptap/react`, `@tiptap/starter-kit` 3.34.0 (cùng phiên bản với web-admin) | Ô viết tự luận | FS-5 |

- **Sửa spec E20:** ảnh đại diện lưu **PNG** 256px. ImageIO không ghi được WebP nếu không có thư viện; dùng PNG thì không cần thêm. ID-2 sửa TM E20 trong PR của mình.
- Không thêm thư viện IndexedDB: FS-5 viết một lớp bọc nhỏ.
- Không có Storybook trong repo và không thêm: thành phần giao diện thử trên trang thử, chỉ có khi chạy dev.
- Phiên bản cố định trong `pom.xml` và `package.json`.

## B8. Web

- **web-student:**
  - Trang công khai dựng phía máy chủ (`revalidate: 60`), phần theo phiên chạy phía client (TM 11.1).
  - **Chỉ FS-1 sửa `proxy.ts` và cấu trúc thanh đầu trang.** FS-1 thêm đủ mọi đường công khai của cả đợt ngay từ đầu (`/`, tiền tố `/exams/`, `/register`, `/forgot-password`, `/reset-password`, `/checkout/result`) và mọi mục của thanh đầu trang (Bài thi của tôi, Lịch sử, Hồ sơ, Đơn của tôi). Mục trỏ tới trang chưa có thì ẩn bằng một danh sách trong cùng tệp; lane làm trang đó bỏ dòng ẩn.
  - Trang công khai đọc phiên theo cách tùy chọn (FS-1): `query-provider.tsx` hiện đưa mọi 401 về `/login`, sẽ làm hỏng trang công khai.
  - Trang bài đã ngừng bán: App Router không trả được HTTP 410 từ trang, nên trang trả 200 và hiện trạng thái ngừng bán; API vẫn trả 410.
  - tiptap chỉ tải khi gặp câu tự luận (FS-5, qua chỗ cắm của FS-4); mathlive không vào web-student.
- **web-admin:**
  - **Chỉ FA-1 sửa cấu trúc menu.** FA-1 thêm nhóm "Thương mại" (Khuyến mãi, Đơn hàng, Bộ đề) và mục "Lượt thi", ẩn theo quyền. Mục trỏ tới trang chưa có thì ẩn như trên; FA-2, FA-3, FA-4, FB bỏ dòng ẩn của mình.
  - FA-4 bỏ cờ `NEXT_PUBLIC_THI360_GRADING_MOCK` và các tệp mock của `/grading` còn từ đợt 2, cùng `questionStore` nếu không còn ai import.
- **packages/ui:**
  - FS-4 làm thành phần câu hỏi; lane khác chỉ dùng. Đổi API thành phần thì qua Advisor.
  - FS-4 đưa bộ hiển thị chỉ đọc từ web-admin vào `packages/ui`: `RichTextView`, KaTeX, mhchem và mọi loại nút của trình soạn (`mathInline`, `mathBlock`, bảng cũ, `dataTable`, `imageFigure`, `crossDiagram`, `quote`, `underlineLabel`, `blank`), kiểm bằng mọi mẫu hợp lệ của `docs/specs/fixtures/rich-text-samples.json`. Mã của trình soạn (tiptap, mathlive) không vào `packages/ui`. `katex` vẫn là phụ thuộc của web-admin vì trình soạn import trực tiếp; toàn repo chỉ dùng một phiên bản 0.19.1. FS-4 **không sửa web-admin**: FS-4 không bị G0b chặn, còn đợt 2B đang viết lại `apps/web-admin/src/features/exams/**`.
  - FA-1 (sau G0b) đổi import của web-admin sang `packages/ui`, xóa bản cũ ở web-admin và chạy lại test của web-admin.
- **Kiểu dữ liệu** sinh từ OpenAPI (`packages/api-client`) là nguồn đúng.
- **Thiết kế:** `docs/design/dot-3/` (canvas trang 10–16 và trang 20), README ghi màn theo task và các chỗ còn chốt. Trang 10–16 chỉ vẽ nền sáng: chế độ tối kiểm theo token `DESIGN.md`, không theo bản vẽ. Lấy bố cục, câu chữ, trạng thái (kể cả khối "Trạng thái khác"); màu, cỡ theo token `DESIGN.md`. Đặc tả thắng bản vẽ khi khác nhau.

## B9. Vùng dễ xung đột khi chạy song song

- **Tệp chung** (`backend/pom.xml`, `application.yml`, `.env.example`, `messages_*.properties`, `SecurityConfig`): thêm ở cuối khối, giữ tối thiểu.
- **Chỉ một task sửa:**
  - `identity/spi/Permission*`, `PermissionCatalogSync`: C-0.
  - Profile `e2e` (khối `on-profile: e2e` trong `application.yml`), `MutableClock`, phần dùng chung của `/api/v1/test/**`, `ForbiddenKeysAssert`: C-0b. ML và CM-3a chỉ thêm endpoint hoặc bean của mình vào profile này.
  - `commerce/spi/**`: C-0; đổi sau đó qua Advisor (B1).
  - `shared/mail/**`: ML.
  - `shared/**` khác: C-0 (gồm `shared/security` cho danh sách IP miễn giới hạn tần suất), riêng phần đồng hồ thử của C-0b. Lane khác cần thì báo Advisor.
  - `exam/**`: EX-1 và EX-2, chia vùng. EX-1 sửa `listing`, `publish`, sự kiện, `PublishedExamPort` **và kiểu kết quả kiểm tra cấu hình** (thêm nhóm "Thông tin bán"; mức cảnh báo và `MANUAL_GRADING_REQUIRED` đã có từ đợt 2B). EX-1 cũng đổi các chỗ đọc giờ của module sang `Clock` (B5). EX-2 sửa `PaperSolverPort`, `PreviewPaperPort` (cổng đọc đề sinh thử cho việc chấm đề làm thử) và phần dựng bài toán từ bản chụp; không sửa thuật toán bộ giải và `PaperOrdering` của đợt 2B. Không task nào của đợt 3 sửa `PaperOrdering`: hàm này đã đọc cờ `shuffleOptions`.
- **`identity/api/AuthController`, `SecurityConfig`, `CookieAuthFilter`, `CsrfFilter`, `RateLimitFilter`, `SecurityProperties`:** ID-1 và ID-2 cùng sửa, sau khi C-0 đã merge; đây là ngoại lệ của vùng `shared/**`. Lane merge sau rebase. CM-1 và các task có endpoint công khai chỉ thêm đường của mình vào danh sách mở.
- **`commerce/**`:** mỗi task một package con.
  - CM-1 `storefront`; CM-2 `pricing`, `promotion`; CM-4a, CM-4b `entitlement`; CM-3a `order`, `payment`, `fulfillment`; CM-3b `payment.vnpay`, `order.expiry`; CM-5 `refund`, `admin`, `reconciliation`; CM-6 `bundle`, và các chỗ sửa nhỏ ở `order`, `fulfillment`, `storefront`, `promotion`, `entitlement` (`exam-access`, `/me/exams`), `refund`, `admin` (sau khi các task chủ đã merge).
  - `VnpayGateway` là interface do CM-3a tạo, gồm `pay`, `verify`, `queryDr`, `refund`. CM-3a cài bản giả lập đủ bốn hàm. CM-3b cài bản thật. CM-5 chỉ gọi interface.
- **`attempt/**`:**
  - AT-1 sở hữu `AttemptGuard`, `AttemptStartService`, `GenerationExecutor`. Thứ tự câu và đảo phương án thuộc `exam/spi/PaperOrdering` của đợt 2B (2B K12); module `attempt` không có lớp đảo riêng.
  - AT-2a sở hữu `AttemptLifecycleService` (chuyển phần, đóng lượt). AT-2b làm `AttemptChoiceService` riêng, chỉ gọi `AttemptLifecycleService` qua hàm công khai và điểm mở AT-2a để sẵn.
  - AT-3 sở hữu `AnswerService`, `EditSessionService`, và bản thật của `FinalAnswerWriter` (B1). AT-2a, AT-2b, AT-3 chỉ kiểm phần đang mở và hạn qua `AttemptGuard`.
  - AT-4 sở hữu bộ chấm từng câu (service nội bộ, hàm thuần, không cần dòng `attempt`). AT-5 dùng bộ chấm này cho API chấm đề làm thử; API nằm trong module `attempt`, không nằm trong `exam`.
- **Ngoại lệ của vùng `exam/**` cho TS và FU** (review vòng 4, P4-1): TS sửa trình sinh seed và seed `TEST-*`; FU sửa phần kiểm tra đề, tóm tắt, xem trước, API soạn đề của đợt 2B sau khi EX-1, EX-2 đã merge, không đụng `listing`, `publish`, `PublishedExamPort`, `PaperSolverPort`, `PreviewPaperPort` của EX-1, EX-2.
- **Bộ dựng dữ liệu của DS:** DS sở hữu `exam/devdata` và `questionbank/devdata` (ngoại lệ của vùng `exam/**`). Bộ SP2E 3 môn nằm ở `commerce/bundle/devdata` của CM-6, chạy sau DS bằng `@Order`, vì `exam` không được phụ thuộc `commerce`. Task khác cần thêm dữ liệu thì báo Advisor.
- **`questionbank/spi`:** AT-3 thêm cổng `QuestionAssetContentPort` để đọc nội dung ảnh của lượt.
- **`e2e/tests/helpers/visual-matrix.ts`:** FS-4 tạo; lane web khác chỉ dùng.
- **`packages/api-client/openapi.json`, `schema.d.ts`:** chỉ lane backend sinh lại, sau khi rebase. Lane web dùng bản trên nhánh tích hợp, không tự sinh. Xung đột thì sinh lại, không sửa tay.
- **`messages/{vi,en}.json`** của từng app: mỗi lane thêm khối riêng, sắp theo khóa.
- **Dấu thời gian migration:** khi rebase, mọi migration của lane phải có dấu thời gian muộn hơn mọi migration đã có trên `integration/dot-3`.

---

# Phần C: Pipeline

## C1. Bảng phụ thuộc

Quy tắc chạy:

- **Mở lane ngay khi mọi phụ thuộc cứng đã merge** vào `integration/dot-3`. Không chờ hết sóng.
- **"Sóng"** chỉ để ước lượng: đó là lượt sớm nhất task có thể bắt đầu.
- **Phụ thuộc mềm:** lane được bắt đầu sớm (dùng hợp đồng trong spec, dữ liệu giả hoặc cổng tạm), nhưng **chỉ nộp được khi phụ thuộc mềm đã merge** và lane đã rebase, chạy lại test.
- G0, G1 và G1b chặn mọi task; bảng không ghi lại.
- **Rủi ro** theo danh sách của `zone17-builder`: `cao` khi task đụng tiền, quyền dùng; xác thực, phân quyền, bí mật; dữ liệu đã có; phép tính lõi (chấm điểm, sinh đề); thao tác gửi lặp hoặc chạy đồng thời; dữ liệu cá nhân; hợp đồng module khác dùng. Task `cao` có thêm dòng "Kiểm thử rủi ro" và tiêu chí đỏ trước (A7 mục 11).

Tổng điểm: 138

| Task | Phụ thuộc cứng | Phụ thuộc mềm | Sóng | Điểm | Rủi ro |
|---|---|---|---|---|---|
| C-0 | | | 1 | 3 | cao |
| C-0b | | | 1 | 1 | chuẩn |
| ML | | C-0b | 1 | 3 | chuẩn |
| TS | G0b | | 1 | 3 | cao |
| FU | EX-1, EX-2, TS | FA-1 | 3 | 8 | cao |
| EX-1 | G0b, TS | C-0b | 2 | 4 | cao |
| EX-2 | G0b, TS | | 2 | 2 | cao |
| FS-4 | | | 1 | 3 | chuẩn |
| VP-1 | G2 | | 1 | 1 | chuẩn |
| CM-4a | C-0 | | 2 | 2 | cao |
| DS | EX-1 | EX-2 | 3 | 4 | chuẩn |
| ID-1 | C-0, ML | | 2 | 3 | cao |
| ID-2 | C-0, ML | | 2 | 2 | cao |
| CM-1 | C-0, C-0b, EX-1 | DS | 3 | 3 | chuẩn |
| FA-1 | EX-1, FS-4 | DS | 3 | 2 | chuẩn |
| AT-1 | C-0, C-0b, EX-1, EX-2, CM-4a, DS | | 4 | 5 | cao |
| CM-2 | CM-1 | | 4 | 5 | cao |
| CM-4b | CM-1, CM-4a | | 4 | 2 | cao |
| FS-1 | CM-1 | CM-4b, FS-4 | 4 | 3 | chuẩn |
| FS-2 | ID-1, ID-2 | FS-1 | 3 | 3 | chuẩn |
| AT-2a | AT-1 | | 5 | 4 | cao |
| AT-3 | AT-1 | AT-2a | 5 | 4 | cao |
| CM-3a | CM-2, CM-4b, ML | | 5 | 5 | cao |
| FA-2 | CM-2, FA-1 | | 5 | 3 | chuẩn |
| AT-2b | AT-2a | | 6 | 2 | cao |
| AT-4 | AT-2a | AT-2b, AT-3 | 6 | 5 | cao |
| AT-6 | AT-2a, AT-3 | | 6 | 3 | cao |
| AT-8 | AT-2a | AT-3, AT-6 | 6 | 2 | cao |
| CM-3b | CM-3a | VP-1 | 6 | 3 | cao |
| CM-5 | CM-3a | | 6 | 5 | cao |
| CM-6 | CM-1, CM-3a, CM-4b, DS | CM-5 | 6 | 3 | cao |
| FS-3 | CM-3a, CM-4b, FS-1 | AT-1, FS-2 | 6 | 3 | chuẩn |
| AT-5 | AT-4 | | 7 | 4 | cao |
| AT-7 | AT-4 | AT-5 | 7 | 2 | cao |
| LP | CM-4b, AT-4, FA-1 | | 7 | 2 | cao |
| FS-5 | FS-4, AT-2b, AT-3, CM-3a, AT-8 | | 7 | 4 | chuẩn |
| FS-6 | FS-4, AT-4, FS-1, CM-3a | | 7 | 3 | chuẩn |
| FA-3 | CM-5, FA-1 | | 7 | 4 | chuẩn |
| FB | CM-6, FS-1, FS-3, FA-1 | FA-3 | 7 | 2 | chuẩn |
| FA-4 | AT-5, FA-1, FS-4, FS-5, FS-6 | | 8 | 3 | chuẩn |
| VP-2 | CM-3b, FS-3, FA-3, DS, G2 | | 8 | 1 | cao |
| FS-7 | FS-5, FS-6, AT-6, AT-7 | | 8 | 3 | cao |
| FA-5 | FA-4, FS-7 | | 9 | 2 | chuẩn |
| QA | C-0, C-0b, ML, TS, FU, EX-1, EX-2, FS-4, VP-1, CM-4a, DS, ID-1, ID-2, CM-1, FA-1, AT-1, CM-2, CM-4b, FS-1, FS-2, AT-2a, AT-3, CM-3a, FA-2, AT-2b, AT-4, AT-6, AT-8, CM-3b, CM-5, CM-6, FS-3, AT-5, AT-7, LP, FS-5, FS-6, FA-3, FB, FA-4, VP-2, FS-7, FA-5 | | 10 | 4 | chuẩn |

Ghi chú cho bảng:

- Lý do của phụ thuộc mềm: ML cần profile `e2e` của C-0b; EX-1 cần `ForbiddenKeysAssert` của C-0b; CM-1, FA-1 nghiệm thu bằng dữ liệu DS; FS-1 cần `exam-access` của CM-4b và `visual-matrix.ts` của FS-4; FS-2 cần `proxy.ts` của FS-1; AT-3 cần phần đã đóng và điểm gọi `FinalAnswerWriter` của AT-2a; CM-3b cần vector thật của VP-1; CM-6 cần hoàn tiền của CM-5; FS-3 cần `prepare` của AT-1; FB cần `/orders` của FA-3.
- QA phụ thuộc mọi task. Nếu tới lúc QA vẫn chưa có G2 thì VP-1, VP-2 được tách khỏi đợt bằng một yêu cầu thay đổi do M.Trường duyệt; QA chạy với cổng giả lập và mẫu số điểm được báo lại rõ ràng (Phần E).
- Bản ngày 2026-11-19 thêm AT-6, AT-7, AT-8, FS-7, FA-5, LP, TS, FU; FU cứng EX-1, EX-2, TS vì cùng sửa module `exam` và seed, không chặn task nào; FS-5 thêm cứng AT-8; EX-1, EX-2 thêm cứng TS, nên FS-1 lùi xuống sóng 4 trong khi FS-2 ở sóng sớm hơn: FS-2 làm trước được nhưng chỉ nộp sau khi FS-1 merge (cảnh báo duy nhất của `plan-lint`, chấp nhận); AT-8 mềm AT-3 vì phải kiểm lưu bài ở mọi phần, mềm AT-6 vì hai task cùng sửa khâu mở lượt nên AT-8 merge sau; AT-7 mềm AT-5 vì chạy lại API chấm đề làm thử.
- So với bản ngày 2026-11-17: FA-1 bỏ phụ thuộc cứng EX-2, thêm mềm DS; AT-3 thêm mềm AT-2a; FS-6 thêm cứng FS-1; FA-4 thêm cứng FS-5, FS-6; VP-2 thêm cứng FS-3, FA-3, DS và sang sóng 7; CM-6 thêm cứng DS; FS-1 thêm mềm FS-4; FS-5, FS-6 thêm cứng CM-3a vì E2E của hai task lấy quyền dùng bằng cách mua qua cổng giả lập.

## C2. Sơ đồ pipeline

Mỗi hàng là một sóng. `◄─` là phụ thuộc cứng; `(┄ X)` là phụ thuộc mềm.

```
G0 (đã qua) + G1b, G2 (đã duyệt)          G0b (đã qua 2026-11-19) mở TS, EX-1, EX-2
TS (seed TEST-*, sóng 1) chặn EX-1, EX-2: chuỗi EX-1, EX-2 và mọi task sau chúng lùi một sóng so với sơ đồ dưới; số sóng đúng là cột Sóng của bảng C1.
FU (việc tồn đọng 2B, sóng 3) chạy sau EX-1, EX-2, TS, không chặn task nào.
│
├ Sóng 1 ─ C-0   C-0b   ML (┄ C-0b)   EX-1 ◄─ G0b (┄ C-0b)   EX-2 ◄─ G0b   FS-4   VP-1 ◄─ G2
│
├ Sóng 2 ─ CM-4a ◄─ C-0              DS ◄─ EX-1 (┄ EX-2)
│          ID-1, ID-2 ◄─ C-0, ML     CM-1 ◄─ C-0, C-0b, EX-1 (┄ DS)
│          FA-1 ◄─ EX-1, FS-4 (┄ DS)
│
├ Sóng 3 ─ AT-1 ◄─ C-0, C-0b, EX-1, EX-2, CM-4a, DS
│          CM-2 ◄─ CM-1              CM-4b ◄─ CM-1, CM-4a
│          FS-1 ◄─ CM-1 (┄ CM-4b, FS-4)    FS-2 ◄─ ID-1, ID-2 (┄ FS-1)
│
├ Sóng 4 ─ AT-2a ◄─ AT-1             AT-3 ◄─ AT-1 (┄ AT-2a)
│          CM-3a ◄─ CM-2, CM-4b, ML  FA-2 ◄─ CM-2, FA-1
│
├ Sóng 5 ─ AT-2b ◄─ AT-2a            AT-4 ◄─ AT-2a (┄ AT-2b, AT-3)
│          AT-6 ◄─ AT-2a, AT-3       AT-8 ◄─ AT-2a (┄ AT-3, AT-6)
│          CM-3b ◄─ CM-3a (┄ VP-1)   CM-5 ◄─ CM-3a   CM-6 ◄─ CM-1, CM-3a, CM-4b, DS (┄ CM-5)
│          FS-3 ◄─ CM-3a, CM-4b, FS-1 (┄ AT-1, FS-2)
│
├ Sóng 6 ─ AT-5 ◄─ AT-4              AT-7 ◄─ AT-4 (┄ AT-5)    LP ◄─ CM-4b, AT-4, FA-1
│          FS-5 ◄─ FS-4, AT-2b, AT-3, CM-3a, AT-8
│          FS-6 ◄─ FS-4, AT-4, FS-1, CM-3a                    FA-3 ◄─ CM-5, FA-1
│          FB ◄─ CM-6, FS-1, FS-3, FA-1 (┄ FA-3)
│
├ Sóng 7 ─ FA-4 ◄─ AT-5, FA-1, FS-4, FS-5, FS-6      VP-2 ◄─ CM-3b, FS-3, FA-3, DS, G2
│          FS-7 ◄─ FS-5, FS-6, AT-6, AT-7
│
├ Sóng 8 ─ FA-5 ◄─ FA-4, FS-7
│
└ Sóng 9 ─ QA ◄─ tất cả
```

Ba luồng chạy song song từ sóng 2:

```
Lượt thi:   C-0 ─► CM-4a ─┐      (C-0b cũng vào AT-1)
            EX-1 ─► DS ───┼─► AT-1 ─► AT-2a ─┬─► AT-4 ─┬─► AT-5 ─► FA-4 ─┐
            EX-2 ─────────┘     └─► AT-3 ─┐  │         └─► AT-7 ─► FS-7 ─┴─► FA-5 ─► QA
                                          │  ├─► AT-2b, AT-8 ─► FS-5 ─► FS-7
                                          └──┴─► AT-6 ─► FS-7

Thương mại: EX-1 ─► CM-1 ─┬─► CM-2 ──┬─► CM-3a ─┬─► CM-5 ─► FA-3 ─► QA
                          └─► CM-4b ─┘          ├─► CM-3b ─► VP-2 (cũng chờ FS-3, FA-3)
                                                └─► CM-6 ─► FB ─► QA

Tài khoản:  ML ─► ID-1, ID-2 ─► FS-2
```

## C3. Đường dài nhất và ưu tiên

- **Đường dài nhất (lượt thi):** G0b → TS → EX-1 → DS → AT-1 → AT-2a → AT-4 → AT-7 → FS-7 → FA-5 → QA, 10 task. `plan-lint` có thể in đường C-0 → CM-4a → AT-1 → … cùng độ dài; đường qua EX-1 mới là đường quyết định vì mang G0b. Đợt 2B nằm trước đường này, nên trong lúc chờ G0b nên làm hết các task không bị chặn: C-0, C-0b, ML, FS-4, VP-1, CM-4a, ID-1, ID-2. FS-2 bắt đầu được nhưng chỉ nộp được sau FS-1, mà FS-1 chờ G0b qua CM-1.
- **Thứ tự merge thật trên đường này:** AT-4 phụ thuộc mềm AT-2b và AT-3, nên chuỗi merge là AT-2a → AT-2b, AT-3 → AT-4 → AT-7 → FS-7 → FA-5.
- **Đường dài thứ hai (thương mại):** EX-1 → CM-1 → CM-2 → CM-3a → CM-5 → FA-3 → VP-2 → QA.
- **Ưu tiên người và máy:**
  1. C-0, C-0b, EX-1, CM-4a, DS: nhỏ, nhưng mở khóa AT-1. Mở ngay ở sóng 1, 2.
  2. AT-1, AT-2a, AT-4: dùng mô hình mạnh nhất có.
  3. CM-3a (IPN, tranh chấp đồng thời).
  4. FS-4, FS-5 (màn làm bài).
- **Song song tối đa** ở sóng 2 đến 6: tới 6 lane cùng lúc nếu máy chịu được. Máy đầy thì giữ lại các task web không nằm trên đường dài nhất (FA-2, FB, FS-2) và VP-1.

## C4. Mốc

| Mốc | Gồm | Kết quả thấy được |
|---|---|---|
| M1 | C-0, C-0b, ML, TS, EX-1, EX-2, FS-4, VP-1 (nếu có G2) | Nền sẵn sàng: thư tới Mailpit, cổng `exam` mới, thành phần câu hỏi và bộ hiển thị nội dung trên trang thử, vector chữ ký VNPAY thật |
| M2 | CM-4a, DS, ID-1, ID-2, CM-1, CM-4b, FA-1, FS-1, FS-2 | Khách xem danh mục thật; đăng ký, xác minh, quên mật khẩu chạy thật; admin sửa thông tin bán |
| M3 | CM-2, CM-3a, CM-3b, FA-2, FS-3 | Mua đầu cuối với cổng giả lập; voucher; Bài thi của tôi. FS-3 cần AT-1 (M4) trước khi nộp. |
| M4 | AT-1, AT-2a, AT-2b, AT-3, AT-4, AT-5, AT-6, AT-7, AT-8, FS-5, FS-6, FS-7, FA-4, FA-5, LP | Làm bài đủ 9 kỳ thi, nghe audio, chấm, kết quả, lịch sử, chấm tự luận |
| M5 | CM-5, CM-6, FA-3, FB, VP-2 (nếu có G2), QA | Quản lý đơn, hoàn tiền; E2E đầu cuối; PR `integration/dot-3` → `develop` |

M3 và M4 chạy song song; M5 cần cả hai.

## C5. Bảng phủ đặc tả

### TM (thương mại, PBI-05 đến PBI-09)

| Mã đặc tả | Nội dung (ngắn) | Task |
|---|---|---|
| C1 | Thông tin bán gắn vào bài thi, chụp vào phiên bản khi công bố | EX-1, FA-1 |
| C2 | Đổi giá, số lượt, quyền lợi chỉ có hiệu lực khi công bố lại | EX-1, CM-1 |
| C3 | Khuyến mãi tự động và mã voucher | CM-2, FA-2, FS-3 |
| C4 | Quy tắc voucher: %, số tiền, hiệu lực, giới hạn, phạm vi, người dùng | CM-2, FA-2 |
| C5 | Voucher về 0 đồng thì cấp quyền ngay, không qua VNPAY | CM-3a, FS-3 |
| C6 | Một đặc tả, làm thật đầu cuối | QA |
| C7 | Câu hỏi thường gặp, hoàn tiền, hỗ trợ nằm trong file dịch | FS-1 |
| C8 | Không có thời hạn sử dụng | FS-1, FS-3 |
| C9 | Một hệ thống tài khoản, realm `thi-360` | ID-1, ID-2 |
| C10 | Email theo cách uniwork: hàng thư, worker, SMTP, mẫu vi/en | ML |
| C11 | VNPAY sandbox, khóa ngoài repo, có cổng giả lập | VP-1, CM-3a, CM-3b, VP-2 |
| C12 | Chỉ có quyền dùng từ mua; cột nguồn `PURCHASE`/`CLASS` | CM-4a |
| C13 | Còn lượt vẫn mua thêm, lượt cộng vào quyền hiện có | CM-4a, CM-4b, FS-1, FS-3 |
| C14 | Hoàn tiền qua API VNPAY; ghi nhận tay khi API lỗi | CM-5, FA-3 |
| C15 | Hoàn toàn bộ thì thu hồi lượt chưa dùng; hoàn khoản trùng giữ quyền | CM-5, CM-4a |
| C16 | Kết quả và lịch sử xem mãi; mức chi tiết theo sản phẩm | AT-4, FS-6 |
| C17 | Sản phẩm bộ đề | CM-6, FB |
| E1 | Module `commerce`, schema `commerce` | C-0 |
| E2 | Bảng chiếu `storefront_product`, API công khai chỉ đọc bảng này | CM-1 |
| E3 | Tên là `exam.name`, mã sản phẩm là `exam.code` | CM-1 |
| E4 | Ba hình thức FULL, theo phần, theo môn; bộ lọc Hình thức | CM-1, FS-1 |
| E5 | Mỗi sản phẩm một khuyến mãi tự động (giảm nhiều nhất) | CM-2 |
| E6 | Cờ `combinable`; hệ thống chọn cách có lợi hơn | CM-2, FS-3 |
| E7 | Làm tròn xuống tới đồng; giá 0 chặn công bố | CM-2, EX-1 |
| E8 | "Tài khoản mới" là chưa có đơn `FULFILLED` | CM-2 (bản tạm), CM-3a (bản thật) |
| E9 | Khuyến mãi tự động không giới hạn người dùng, có tổng số suất | CM-2 |
| E10 | 5 quyền mới của thương mại | C-0 |
| E11 | Sắp xếp danh mục: mới công bố, giá tăng, giá giảm | CM-1, FS-1 |
| E12 | Đơn chờ hết hạn sau 15 phút; tiền về muộn vẫn nhận | CM-3a, CM-3b |
| E13 | Một đơn chờ mỗi sản phẩm; dùng lại hoặc `REPLACED` | CM-3a |
| E14 | Mã đơn `DH…`, `vnp_TxnRef` theo lần thanh toán | CM-3a |
| E15 | OTP 6 số, 10 phút, 5 lần sai, gửi lại sau 60 giây, 5 lần mỗi ngày | ID-1 |
| E16 | Liên kết đặt lại 1 giờ, một lần dùng; giới hạn 1 lần mỗi 60 giây, 5 lần mỗi ngày, 60 lần mỗi phút theo IP | ID-2 |
| E17 | Đổi email bằng OTP tới email mới | ID-1, FS-2 |
| E18 | Email chưa xác minh vẫn đăng nhập và mua được | ID-1, FS-2 |
| E19 | Mỗi IP 30 lần đăng ký mỗi giờ, 100 lần mỗi ngày | ID-1 |
| E20 | Ảnh đại diện ≤ 5 MB, ≤ 4.096 px, cắt vuông 256px (plan đổi sang PNG) | ID-2, FS-2 |
| E21 | Lượt dùng theo lần cấp cũ nhất (FIFO); quyền lợi theo lần cấp | CM-4a |
| E22 | Học viên xem đơn của mình `/account/orders` | CM-3a, FS-3 |
| E23 | Voucher của đơn bị hoàn vẫn tính đã dùng | CM-2, CM-5 |
| E24 | Bài có lựa chọn sau khi bắt đầu phải công bố mọi đường cùng phiên bản | EX-1, CM-1, AT-1 |
| E25 | Đơn và lần cấp chụp `option_codes`; phần giao rỗng thì 409 | CM-3a, CM-4a, AT-1 |
| E26 | Mức tối thiểu VNPAY, `LISTING_PRICE_BELOW_MIN` | VP-1, EX-1, CM-2, CM-3a, CM-3b |
| E27 | Quên mật khẩu chỉ cho realm `thi-360` | ID-2 |
| 3 | `PublishedExamPort`, sự kiện `ExamPublished`/`ExamDiscontinued`, `AccountDeletionGuard`, không phụ thuộc vòng | C-0, EX-1, CM-1, CM-4a, CM-3a |
| 4 | Bảng `email_message`, worker gửi, gửi lại, mẫu Thymeleaf, thiếu mẫu không khởi động | ML |
| 4 | Cột `preferred_locale`, ghi khi đăng ký và đăng nhập | C-0, ID-1 |
| 5.1 | Đăng ký: kiểm tra, tạo tài khoản, Keycloak có bù, phiên, thư OTP | ID-1, FS-2 |
| 5.1 (AC-06.01) | Hai đăng ký trùng tên đồng thời chỉ một thành công | ID-1 |
| 5.2 | Liên kết Đăng ký, Quên mật khẩu trên trang đăng nhập, giữ `callbackUrl` | FS-2 |
| 5.2 | Đăng xuất đưa về `/` | FS-1 |
| 5.3 | Xác minh email, đổi email, bảng `email_code`, 4 API | ID-1, FS-2 |
| 5.3 (AC-06.05) | Email cũ vẫn khôi phục được tới khi xác minh xong | ID-1 |
| 5.3 | Thư đơn (`ORDER_FULFILLED`, `REFUND_DONE`) chỉ gửi khi email đã xác minh | CM-3a, CM-5 |
| 5.3 | `THI360_DEV_EMAIL_CODE` chỉ ở profile `dev` | ID-1 |
| 5.4 | Quên và đặt lại mật khẩu, tác vụ nền, thu hồi phiên | ID-2, FS-2 |
| 5.4 (AC-06.04) | Cùng phản hồi cho email có và không có tài khoản | ID-2 |
| 5.4 | `/reset-password` trả `Referrer-Policy: no-referrer` | FS-2 |
| 5.5 | Hồ sơ, ảnh đại diện, số điện thoại | ID-2, FS-2 |
| 5.5 (AC-06.06) | Đổi họ tên, email, mật khẩu không ảnh hưởng đơn và quyền | ID-2 |
| 6.1 | Bảng `exam_listing`, tăng `revision`, không vào `input_fingerprint`, chụp vào `config.listing` | EX-1 |
| 6.2 | Lỗi `LISTING_*` chặn công bố, kiểm trong transaction công bố | EX-1 |
| 6.2 | Ảnh bìa: bucket, giới hạn; ảnh không còn dùng thì dọn sau 7 ngày | EX-1 |
| 6.2 | Sao chép bài chép cả thông tin bán | EX-1 |
| 6.3 | API `listing`, `cover` | EX-1 |
| 6.4 | Tab Thông tin bán, xem trước thẻ, nhóm "Thông tin bán" ở tab Kiểm tra | FA-1 |
| 7.1 | Bảng `promotion*`, ràng buộc | CM-2 |
| 7.2 | Hiệu lực, phạm vi, tính giá bước 1–7, trừ lượt dùng, sửa khi đã dùng, ngừng, xóa, chống dò mã | CM-2 |
| 7.2 bước 5 | Màn quản trị cảnh báo khi khuyến mãi bị giới hạn bởi mức tối thiểu | CM-2, FA-2 |
| 7.3 | API admin `/promotions/**`, nhật ký | CM-2 |
| 7.3 | Màn `/promotions`: danh sách, tạo và sửa, chi tiết | FA-2 |
| 8.1 | Bảng đơn, thanh toán, sự kiện, lịch sử; trạng thái; hai transaction | CM-3a |
| 8.2 | Trang xác nhận mua `/checkout/[code]` | FS-3 |
| 8.2 | Trang chi tiết đơn của học viên `/account/orders/<code>`, 404 với đơn của người khác | FS-3 |
| 8.3 | Tạo đơn, `/pay`, `/cancel`, danh sách và chi tiết đơn của học viên | CM-3a |
| 8.4 | `VnpayGateway`, cổng giả lập, `/mock-vnpay`, biến môi trường | CM-3a |
| 8.4 | Ký URL thanh toán, `querydr`, `refund` thật | VP-1, CM-3b |
| 8.4 | IPN: thứ tự kiểm tra, `RspCode`, miễn giới hạn tần suất, lọc IP | CM-3a |
| 8.4 (AC-07) | Trang quay lại không đổi trạng thái; IPN lặp chỉ một quyền | CM-3a, FS-3 |
| 8.4 | Worker `querydr` mỗi 5 phút, tối đa 24 giờ; bảng ánh xạ kết quả (các ca ở TM 14 chưa có trong nghiệm thu) | CM-3b |
| 8.4 | Đối soát 02:00 | CM-5, FA-3 |
| 8.4 | Luồng sandbox thật có IPN | VP-2 |
| 8.5 | Bảng `entitlement*`, `EntitlementPort` | C-0, CM-4a |
| 8.5 | Thủ tục cấp quyền, worker cấp lại, thư `ORDER_FULFILLED` | CM-3a |
| 8.5 | `confirmUse`, `compensate` phía lượt thi | AT-2a, AT-5 |
| 8.6 | Tiền về sau `REPLACED`/`CANCELLED`, thu trùng | CM-3a |
| 8.6 | Đơn hết hạn, tiền về sau `EXPIRED` | CM-3b |
| 9.1 | Danh sách đơn, dải "Cần xử lý" | CM-5, FA-3 |
| 9.2 | Chi tiết đơn | CM-5, FA-3 |
| 9.3 (AC-08) | Người mua theo bài thi; tổng là số tài khoản khác nhau | CM-5, FA-3 |
| 9.4 | Hoàn tiền API và ghi tay, `UNKNOWN`, ảnh hưởng quyền, thư `REFUND_DONE` | CM-5, FA-3 |
| 9.5 | Tiếp tục cấp quyền | CM-5, FA-3 |
| 10.1 | Bảng chiếu, `detail`, không chứa dữ liệu ẩn, lệnh dựng lại | EX-1, CM-1 |
| 10.2 | API công khai `filters`, `products`, `products/{code}`, `covers` | CM-1 |
| 10.2 | Giới hạn 120 yêu cầu mỗi phút theo IP; danh sách IP được miễn | C-0 (làm), CM-1 (dùng) |
| 10.2 | `price-quote` | CM-2 |
| 10.2 | `exam-access/query`, 5 trạng thái, `hasPendingFulfillment` | CM-4b |
| 11.1 | Trang và đường dẫn, `proxy.ts`, dựng phía máy chủ | FS-1 |
| 11.1 | Đã đăng nhập mà mở `/register` thì về `/home` | FS-1 |
| 11.2 | Trang giới thiệu 6 khối | FS-1 |
| 11.3 | Thẻ, chi tiết, nút theo `exam-access`, 404, 410, trạng thái rỗng và lỗi | FS-1 |
| 11.3 (AC-06.02) | Giữ sản phẩm qua đăng nhập, đăng ký bằng `callbackUrl` | FS-1, FS-2 |
| 11.3 | `CAN_START` kèm `hasPendingFulfillment` ở trang chi tiết | FS-1 |
| 11.3 | Trang đăng ký hiện hướng dẫn quy tắc tên đăng nhập | FS-2 |
| 12 | Bài thi của tôi `/home`, `GET /me/exams`, các nút | CM-4b, FS-3 |
| 13 | Bit quyền 18–26 | C-0 |
| 13 | Endpoint công khai mới, giới hạn tần suất, idempotency, nhật ký, biến môi trường | ID-1, ID-2, CM-1, CM-3a, CM-5 |
| 14 | Kiểm thử backend và web | từng task; QA |
| 15 | Câu hỏi còn mở Q1–Q17 | QA (chép vào issue cha) |
| 16.1 | Bảng `bundle`, `bundle_item`, `bundle_version` | CM-6 |
| 16.2 | Thành phần, công bố, `UNAVAILABLE` khi bài ngừng bán, ngừng bán bộ, phạm vi `BUNDLE` | CM-6, CM-2 |
| 16.2 | Bài trong bộ công bố lại thì dựng lại `items[]` của dòng bảng chiếu | CM-6 |
| 16.3 | Đơn bộ, `purchase_order_item`, cấp quyền từng bài, hoàn toàn bộ | CM-3a, CM-4a, CM-6, CM-5 |
| 16.4 | API admin bộ đề, nhật ký `BUNDLE_*` | CM-6 |
| 16.4 | Người mua theo bài thi tính cả đơn mua bộ | CM-6 |
| 16.4 | API admin đơn trả các bài của đơn bộ | CM-6 |
| 16.4 | Web admin bộ đề; `/orders` hiện các bài của đơn bộ | FB |
| 16.4 | API công khai và học viên với mã bộ, `bundles[]` | CM-6 |
| 16.4 | Web học viên: thẻ bộ, chi tiết bộ, mua bộ, `/home` theo bộ | FB |
| 16.4 | Thư `ORDER_FULFILLED` liệt kê các bài được cấp | CM-6 |
| 16.5 | Kiểm thử bộ đề | CM-6, FB |

### LT (lượt thi, PBI-10 đến PBI-13)

| Mã đặc tả | Nội dung (ngắn) | Task |
|---|---|---|
| L1 | Một đặc tả, làm thật đầu cuối cho 9 kỳ thi (BS D-1) | QA |
| L2 | Tự luận chấm tay; đề có tự luận vẫn mở | AT-4, AT-5, FA-4 |
| L3 | Kết quả và lịch sử xem mãi | AT-4, FS-6 |
| L4 | Chờ chọn phần 3 tối đa 15 phút | AT-2b, FS-5 |
| L5 | Không band điểm; tổng điểm theo cấu trúc đề | AT-4 |
| L6 | Giữ lượt khi khởi tạo, tính đã dùng khi kết thúc | AT-1, AT-2a, CM-4a |
| L7 | Đề làm tuần tự từng phần, riêng THPTQG chuyển tự do (BS 4) | AT-2a, AT-8, FS-5 |
| L8 | Sinh đề chạy nền, trả 202 | AT-1, AT-2b, FS-5 |
| F1 | Module `attempt` | C-0 |
| F2 | Phiên bản đề của lượt là phiên bản đang phục vụ lúc bắt đầu | AT-1 |
| F3 | Bài ngừng bán còn lượt vẫn bắt đầu được | EX-1, AT-1 |
| F4 | Phần bắt buộc sinh ngay, phần tự chọn sinh sau; Tin học; HSA 17 − 1 | EX-2, AT-1, AT-2b |
| F5 | Sinh đề bằng `PaperSolver`, hạn 30 giây, bỏ câu đã có | EX-2 |
| F6 | Một lượt mở mỗi bài | AT-1 |
| F7 | Chuyển phần tuần tự, mở phần kế ngay; THPTQG theo BS 4 | AT-2a, AT-8 |
| F8 | Lưu theo câu với `seq` | AT-3 |
| F9 | Hạn nhận câu trả lời theo giờ máy chủ; câu kèm yêu cầu nộp được ghi trước khi đóng | AT-2a, AT-3 |
| F10 | Phiên sửa, tiếp quản | AT-3, FS-5 |
| F11 | Worker đóng phần khi hết giờ, `closed_at = deadline_at` | AT-2a |
| F11 | Mọi yêu cầu vào lượt tự đóng phần hoặc bài đã quá hạn trước khi xử lý | AT-1, AT-2a |
| F12 | Bản nháp trong IndexedDB, hai tab dùng chung phiên | AT-3 (máy chủ), FS-5 |
| F12 | Đăng xuất hoặc đổi tài khoản thì xóa bản nháp | FS-5 |
| F13 | `attempt_no` | AT-1 |
| F14 | Đơn vị đếm câu, 5 nhóm thống kê | AT-4, FS-4, FS-6 |
| F15 | Kết quả V-ACT: điểm luyện tập, số câu đúng 4 thành phần | AT-4, FS-6 |
| F16 | Còn chờ chấm thì chưa có điểm tổng | AT-4, FS-6 |
| F17 | Người chấm nhận câu, mốc 30 phút, mở lại | AT-5, FA-4 |
| F18 | Quyền `ATTEMPT.VIEW`, `ATTEMPT.COMPENSATE` | C-0, AT-5 |
| F19 | Phần đã đóng không mở lại | AT-2a |
| F20 | Đánh dấu lượt hỏng, cấp bù | AT-5, FA-4 |
| F20 | Kết quả và lịch sử ghi chú lượt bị ảnh hưởng, đã cấp bù | AT-4, AT-5, FS-6 |
| F21 | Đã bỏ | không cần task |
| F22 | Thứ tự câu và đảo phương án qua `PaperOrdering`; cờ giữ thứ tự phương án (SUP mục 5) | AT-1, FA-4, FS-4 |
| 3 | `PublishedExamPort`, `PaperSolverPort`, `AttemptStatusProvider` | EX-1, EX-2, C-0, AT-1 |
| 4 | Bảng `attempt*`, `essay_grade`, định dạng câu trả lời | AT-1, AT-3, AT-5, FS-4 |
| 5.1 | API và trang chuẩn bị | CM-4a, AT-1, FS-5 |
| 5.2 | Bắt đầu lượt, sinh nền, `ABORTED`, worker `STALE_STARTING` | AT-1 |
| 5.2 (AC-10.13) | Thử lại là yêu cầu mới, khóa mới | AT-1, FS-5 |
| 6 | Luồng theo kỳ thi, mẫu số theo đường | AT-1, AT-2a, AT-2b |
| 6 (AC-10.03, 10.05, 10.06, 10.07) | QDA QĐ02, BCA CA2 81 câu, V-ACT 120 câu, SP2E Toán | AT-2a, AT-2b |
| 6.1 | Chọn phần 3, thử lại, sinh lỗi, quá hạn chọn, mã lỗi | AT-2b, FS-5 |
| 6.1 (AC-10.01, 10.02) | HSA FULL qua Khoa học và Tiếng Anh | AT-2b |
| 6.1 | Lỗi 3 lần liên tiếp thì báo vận hành | AT-2b |
| 6.2 (AC-10.09, 10.10) | Định hướng Tin học chọn ở bước chuẩn bị, mẫu số 22 và 28 | EX-2, AT-1, FS-5 |
| 6.3 | Hoàn thành phần, nộp bài, quy trình đóng lượt | AT-2a |
| 6.3 (AC-11.08) | `finalAnswers` trên `finish` và `submit` | AT-2a (điểm móc), AT-3 (cài và test) |
| 6.3 (AC-12.01) | Nộp trùng lúc hết giờ: một lần đóng | AT-2a |
| 7.1 | API lưu, phiên, heartbeat, tài nguyên của lượt | AT-1, AT-3 |
| 7.1 (AC-11.02–11.05, 11.07, 11.09) | `seq`, bỏ chọn, từng ý, gửi lại, phiên cũ, phần đã đóng | AT-3 |
| 7.2 | Nhịp gửi, trạng thái lưu, đồng hồ, mất mạng, tiếp quản | FS-5 |
| 7.2 (AC-10.12) | Chỉnh giờ thiết bị không đổi thời gian còn lại | FS-5 |
| 7.3 | Không lộ đáp án ở `prepare`, bắt đầu, `AttemptState` | AT-1 |
| 7.3 | Test khóa cấm trên `choice`, `retry`, `answers`, `finish`, `submit` | QA (AT-2a, AT-2b, AT-3 chưa có dòng nghiệm thu) |
| 7.3 | Test đường `assets` | AT-3 |
| 7.4 | Khôi phục đúng bước | AT-1, FS-5 |
| 8 | Màn làm bài, thành phần 8 dạng câu | FS-4, FS-5 |
| 8 | Màn xem trước đề của admin dùng chung thành phần ở chế độ chỉ đọc | FS-4, FA-5 |
| 9.1 | Chấm tự động, tổng hợp, phần `SKIPPED`, trạng thái, lỗi chấm | AT-4 |
| 9.1 (AC-12.06, 12.07) | Chấm lại cùng kết quả; lỗi chấm không hiện 0 | AT-4 |
| 9.2 | Hàng chờ chấm tự luận, chấm ẩn danh | AT-5, FA-4 |
| 9.2 | "Đang chấm lại" phía học viên | AT-4, AT-5, FS-6 |
| 9.3 (AC-12.08) | Cảnh báo `MANUAL_GRADING_REQUIRED` | đợt 2B; FA-1 chỉnh hiển thị |
| 10.1 | API và màn kết quả | AT-4, FS-6 |
| 10.1 (AC-12.04) | Môn không chọn không vào mẫu số | AT-4 |
| 10.2 | Xem lại bài theo `result_view` | AT-4, FS-6 |
| 11 | Lịch sử, bộ lọc trên URL | AT-4, FS-6 |
| 11 (AC-13.01–13.04, 13.06) | Mở đúng kết quả, FULL một dòng, điểm 0, lọc theo bài, người khác 404 | AT-4, FS-6 |
| 12 | Vận hành lượt: tra cứu, chấm lại, đánh dấu hỏng, cấp bù | AT-5, FA-4 |
| 2B H17 | Làm thử như học viên: cổng đọc đề thử, chấm từng câu, endpoint, web | EX-2, AT-4, AT-5, FA-4 |
| 13 | Kiểm thử backend và web | từng task; QA |
| 14 | Câu hỏi còn mở R1–R14 | QA (chép vào issue cha) |

### BS (phần bổ sung ngày 2026-11-19)

| Mã đặc tả | Nội dung (ngắn) | Task |
|---|---|---|
| 2 | Bài ôn bán lẻ, chọn được vào bộ, có nhãn | EX-1, DS, CM-1, CM-6, FS-1, FB |
| 3 | V-ACT: điểm luyện tập, ghi chú, 4 thành phần, 12 và 18 câu | AT-4, FS-6 |
| 4 | THPTQG chuyển tự do giữa các phần | AT-1, AT-8, FS-5 |
| 5 | Số câu chạy suốt bài | AT-1, AT-2b, FS-5, FS-6 |
| 6 | `wordLimit` tối thiểu, chỉ nhắc | FS-4, FS-5, FA-4 |
| 7 | Vai trò mặc định của chín quyền | C-0 |
| 8 | Quy tắc E24 | EX-1 |
| 9 | Hạn đề làm thử | EX-2, AT-5, FA-4 |
| 10 | Cảnh báo giá sàn, tổng tiền đã giảm, lịch sử thay đổi | CM-2, FA-2 |
| 11 | Số đếm hàng chờ chấm | AT-5, FA-4 |
| 12 | Màn Sinh thử dùng giao diện chung, kiểm 9 kỳ thi | FA-5 |
| 13 | Tab Hồ sơ học tập | LP |
| 14 | Một khoản được tính mỗi đơn, khoản thu thừa, chờ đối soát, `needs_review` | CM-3a, CM-3b, CM-5, FA-3, QA |
| 15 | Ảnh lời giải | AT-4, FS-6 |
| 16 | Nhóm menu Học viên | FA-1 |
| 17.1 | Màn chuẩn bị H-SCA, V-SAT | AT-6, FS-7 |
| 17.2 | Nghe tối đa hai lần, không tua | AT-6, FS-7, FA-5 |
| 17.3 | Phân đoạn hiển thị | EX-1, AT-7, FS-7 |
| 17.4 | Kiểm tệp audio khi mở lượt | AT-6 |
| 17.5 | Chấm H-SCA, V-SAT | AT-4, AT-7 |
| 17.6 | Kết quả, "Đúng một phần" | AT-7, FS-7 |
| 17.7 | Nhập đơn vị, ion, ký hiệu | FS-7 |

---

# Phần D: Hợp đồng từng task

Mỗi task gồm các mục sau (nhãn in đậm đầu dòng là nhãn `plan-lint` đọc, không đổi tên):

- **Spec:** mục phải đọc; kèm cổng và phụ thuộc mềm nếu có.
- **Thiết kế:** bản vẽ, với task có giao diện.
- **Sản phẩm:** phải có khi nộp.
- **Đường dẫn sở hữu:** tệp và thư mục task được sửa. Ngoài vùng này task chỉ đọc; "thêm vào" là tệp chung, chỉ thêm ở cuối khối (B9).
- **Ngoài phạm vi:** thứ dễ tưởng là thuộc task nhưng không thuộc.
- **Nghiệm thu:** tiêu chí `AC-n` của riêng task, ngoài A7; mỗi tiêu chí kèm bằng chứng.
- **Ảnh hưởng ngoài task:** thứ nằm ngoài vùng sở hữu có thể đổi hành vi vì task, kèm phép kiểm và chủ. Đây là phạm vi hồi quy của task; các dòng được tra từ code ngày 2026-11-18. Lane tra lại khi bắt đầu, vì code đã đổi.
- **Kế hoạch kiểm thử:** test cho từng `AC-n` (mức và lệnh), hồi quy, kiểm thử rủi ro, dữ liệu test, phần không kiểm.

Số mục trỏ vào TM, LT, BE, 2B. Tên có ghi "(mới)" là thứ task sẽ tạo.

## C-0 · Khung module, quyền, hợp đồng SPI

**Spec:** TM 3, 4 (ngôn ngữ ưa dùng), 8.4 (IPN miễn giới hạn tần suất), 8.5, 10.2, 13; LT 3, 5.1, 12 (quyền); plan B1, B3, B6, B9; BS 7.

**Sản phẩm:**

- Package `commerce`, `attempt` với `package-info.java` (`@ApplicationModule`); `spi` có `@NamedInterface("spi")`.
- `commerce/spi`:
  - `EntitlementPort` đủ các hàm ở B1: `describe`, `reserve`, `confirmUse`, `release`, `benefitsOf`, `compensate`;
  - `AttemptStatusProvider` kèm bản rỗng mặc định;
  - các record đi kèm. Chữ ký theo TM 8.5, LT 3, LT 5.1. `grantForOrder`, `revokeUnused` không nằm ở `spi` (B1).
- `UserAdminService` nhận `List<StudentLearningProvider>` và gộp kết quả (B1): `dataAvailable` đúng khi một bean có dữ liệu, các danh sách nối lại. Có test với hai bean giả.
- Schema `commerce`, `attempt` trong `spring.flyway.schemas`; mỗi bên một migration chỉ có `CREATE SCHEMA IF NOT EXISTS`.
- Quyền bit 18–26 theo B3, gán trong `PermissionCatalogSync`:
  - `PermissionObject` thêm `PROMOTION`, `ORDER`, `GRADING`, `ATTEMPT`;
  - chín quyền mới gán mặc định cho `system_admin`, `operations`, `content_admin` theo bảng BS 7, không thêm vai trò;
  - tên và mô tả của chín quyền, bốn đối tượng trong ba tệp `messages*.properties`.
- `THI360_TOKEN_PEPPER`:
  - khóa `thi360.token.pepper` (`${THI360_TOKEN_PEPPER:}`), lớp `TokenPepperProperties` có `@Validated`, `@NotBlank`, theo mẫu `CtxProperties`;
  - giá trị `dev-only-…` trong khối `on-profile: dev` và `on-profile: test` của `application.yml` (B6); giá trị cho `e2e` do C-0b thêm;
  - `.env.example` chỉ có tên biến.
- Cột `identity.user_account.preferred_locale` (TM 4), `NOT NULL`, mặc định `vi`. Đăng nhập thành công ghi cột này từ cookie `thi360_locale`. Đọc cookie mà không thêm tham số vào OpenAPI.
- Giới hạn tần suất của API công khai trong `shared/security` (TM 10.2, B6):
  - `RateLimitFilter` thêm chính sách cho `GET /api/v1/public/**`: `public-per-ip-limit` 120, `public-per-ip-window` `PT1M`, tiền tố khóa `rl:public:ip:`; thời gian sống của khóa Redis tính cả cửa sổ này;
  - `/api/v1/public/payments/vnpay/ipn` không bị giới hạn (TM 8.4);
  - danh sách `thi360.security.rate-limit.exempt-ips` trong `RateLimitProperties`, mặc định rỗng, nhận IP hoặc CIDR; mục sai định dạng thì không khởi động;
  - danh sách chỉ miễn chính sách công khai; giới hạn đăng nhập và tải tệp giữ nguyên.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/commerce/package-info.java`, `backend/src/main/java/vn/unicom/thi360/commerce/spi/**` (mới)
- `backend/src/main/java/vn/unicom/thi360/attempt/package-info.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/identity/spi/Permission.java`, `PermissionObject.java`, `Permissions.java`
- `backend/src/main/java/vn/unicom/thi360/identity/application/PermissionCatalogSync.java`
- `backend/src/main/java/vn/unicom/thi360/identity/application/UserAdminService.java`
- `backend/src/main/java/vn/unicom/thi360/identity/application/AuthService.java`, `backend/src/main/java/vn/unicom/thi360/identity/api/AuthController.java` (chỉ phần ghi `preferred_locale`)
- `backend/src/main/java/vn/unicom/thi360/identity/domain/UserAccount.java`
- `backend/src/main/java/vn/unicom/thi360/shared/security/RateLimitFilter.java`, `SecurityProperties.java`
- `backend/src/main/java/vn/unicom/thi360/shared/security/TokenPepperProperties.java` (mới)
- `backend/src/main/resources/db/migration/commerce/V*__create_commerce_schema.sql`, `backend/src/main/resources/db/migration/attempt/V*__create_attempt_schema.sql` (mới)
- `backend/src/main/resources/db/migration/identity/V*__add_user_preferred_locale.sql` (mới)
- `backend/src/test/java/vn/unicom/thi360/identity/PermissionCatalogSyncTest.java`
- `backend/src/test/java/vn/unicom/thi360/identity/application/StudentLearningProviderTests.java`
- `backend/src/test/java/vn/unicom/thi360/shared/security/ClientIpResolverTests.java`
- Test mới của task trong `backend/src/test/java/vn/unicom/thi360/{commerce,identity,shared/security}/**`
- thêm vào: `backend/src/main/resources/application.yml` (`spring.flyway.schemas`, pepper của `dev`, `test`, cấu hình giới hạn công khai)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (chín quyền, bốn đối tượng)
- thêm vào: `.env.example` (`THI360_TOKEN_PEPPER=`, `THI360_SECURITY_RATELIMIT_EXEMPTIPS=`)
- thêm vào: `ARCHITECTURE.md` (mục 4: module và schema mới)

**Ngoài phạm vi:** bản cài của `EntitlementPort` (CM-4a); bản cài của `AttemptStatusProvider`, `ActiveAttemptChecker` (AT-1); hai `StudentLearningProvider` thật (CM-4a, AT-1); mở `GET /api/v1/public/**` trong `SecurityConfig`, `CookieAuthFilter` và đặt danh sách IP miễn (CM-1); giá trị pepper của `e2e` (C-0b); ghi `preferred_locale` khi đăng ký (ID-1); băm mã OTP bằng pepper (ID-1, ID-2); giới hạn riêng của đăng ký, quên mật khẩu (ID-1, ID-2).

**Nghiệm thu:**

- AC-1: `ApplicationModules.verify()` đạt với hai module mới — bằng chứng: `ModularityTest`
- AC-2: `commerce/spi` có đủ sáu hàm của `EntitlementPort`, không có `grantForOrder`, `revokeUnused`; bản rỗng của `AttemptStatusProvider` không báo lượt nào — bằng chứng: `CommerceSpiContractTests` (mới)
- AC-3: Hai bean `StudentLearningProvider` cho kết quả gộp: danh sách nối lại, `dataAvailable` đúng khi một bean có dữ liệu — bằng chứng: `StudentLearningProviderTests`, ca `mergesResultsOfTwoProviders`
- AC-4: Không có bean, hoặc bean trả `null`, thì trả kết quả rỗng với `dataAvailable = false` — bằng chứng: `StudentLearningProviderTests`, ca `noProviderOrNullResultIsEmpty`
- AC-5: Ứng dụng khởi động với hai bean thật trong context, `GET /api/v1/admin/users/{id}/learning` trả dữ liệu gộp — bằng chứng: `UserLearningMultiProviderIT` (mới)
- AC-6: Flyway tạo schema `commerce`, `attempt` trên DB mới — bằng chứng: `Wave3SchemaMigrationIT` (mới), ca `createsCommerceAndAttemptSchemas`
- AC-7: DB mới: chín quyền đúng mã và bit 18–26, `system_admin` có đủ — bằng chứng: `PermissionCatalogSyncTest`, ca `freshDb_systemAdminHoldsNineWave3Permissions`
- AC-8: `operations` có đúng sáu quyền, `content_admin` đúng ba quyền, `student` không có quyền nào trong chín quyền mới, `ORDER.REFUND` chỉ `system_admin` có (BS 7) — bằng chứng: `PermissionCatalogSyncTest`, ca `systemRolesGetAgreedWave3Permissions`
- AC-9: DB của đợt 2: `SYSTEM_ADMIN` có thêm chín quyền, quyền đã bị gỡ tay vẫn không quay lại, cache quyền được xóa — bằng chứng: `PermissionCatalogSyncTest`, ca `wave2Db_keepsManuallyRemovedGrant_andGainsNineNewPermissions_andEvictsAuthzCache`
- AC-10: Chạy đồng bộ quyền lần hai không đổi dòng nào — bằng chứng: `PermissionCatalogSyncTest`, ca `secondSyncChangesNothing`
- AC-11: Mỗi đối tượng mới có đúng một quyền `VIEW`; vai trò tự tạo chỉ chọn `ORDER.REFUND` thì được thêm `ORDER.VIEW` — bằng chứng: `Wave3PermissionsTests` (mới)
- AC-12: Tên, mô tả của chín quyền và tên bốn đối tượng có đủ ở `vi`, `en` — bằng chứng: `Wave3PermissionsTests`, ca `everyNewPermissionHasLabels`; `MessageBundlesTest`
- AC-13: `AdminApiForbiddenMatrixIT` vẫn xanh — bằng chứng: `AdminApiForbiddenMatrixIT`
- AC-14: Thiếu pepper ở profile không có giá trị mặc định thì lỗi khởi động, thông báo nêu tên khóa — bằng chứng: `TokenPepperPropertiesTests` (mới), ca `missingPepperFailsStartup`
- AC-15: Profile `dev`, `test` khởi động không cần biến môi trường, giá trị bắt đầu bằng `dev-only-` — bằng chứng: `TokenPepperPropertiesTests`, ca `devAndTestUseDevOnlyValue`
- AC-16: `.env.example` chỉ có tên `THI360_TOKEN_PEPPER`, không có giá trị; hai lệnh quét bí mật của A7 ra `0` — bằng chứng: `grep -cE "^THI360_TOKEN_PEPPER=.+" .env.example` ra `0`; lệnh A7 mục 10
- AC-17: Compose dev khởi động được sau khi merge — bằng chứng: kịch bản C0-S1: `docker compose -f infra/docker-compose.yml up -d`, chạy backend với `api,worker,dev`, `GET /actuator/health` trả `UP`
- AC-18: Tài khoản có sẵn trước migration nhận `preferred_locale = vi`, dữ liệu khác không đổi — bằng chứng: `Wave3SchemaMigrationIT`, ca `existingAccountsGetDefaultLocale`
- AC-19: Đăng nhập với cookie `thi360_locale` là `en` hoặc `vi` thì cột nhận đúng giá trị đó — bằng chứng: `PreferredLocaleLoginIT` (mới), ca `loginStoresLocaleFromCookie`
- AC-20: Cookie thiếu hoặc giá trị lạ: đăng nhập vẫn thành công, cột giữ giá trị cũ — bằng chứng: `PreferredLocaleLoginIT`, ca `missingOrUnknownCookieKeepsStoredLocale`
- AC-21: Đăng nhập sai mật khẩu không đổi cột — bằng chứng: `PreferredLocaleLoginIT`, ca `failedLoginDoesNotTouchLocale`
- AC-22: OpenAPI không đổi sau task — bằng chứng: `diff -u ../packages/api-client/openapi.json target/openapi.json` rỗng
- AC-23: Một IP gọi `GET /api/v1/public/**`: yêu cầu 120 qua, yêu cầu 121 nhận 429 `RATE_LIMITED` — bằng chứng: `RateLimitPublicPolicyIT` (mới), ca `limitsAt121WithinWindow`
- AC-24: Hết cửa sổ thì IP đã bị chặn gọi lại được — bằng chứng: `RateLimitPublicPolicyIT`, ca `windowResetAllowsAgain`
- AC-25: 130 yêu cầu song song từ một IP: đúng 120 yêu cầu qua — bằng chứng: `RateLimitPublicPolicyIT`, ca `concurrentRequestsNeverExceedLimit`
- AC-26: IP trong `exempt-ips` (IP đơn và CIDR) gọi quá 120 yêu cầu không bị chặn — bằng chứng: `RateLimitPublicPolicyIT`, ca `exemptIpIsNotLimited`
- AC-27: Mặc định danh sách rỗng: không IP nào được miễn — bằng chứng: `RateLimitPublicPolicyIT`, ca `defaultListExemptsNobody`
- AC-28: Yêu cầu của học viên đi qua máy chủ web được miễn, có `X-Forwarded-For` hợp lệ, tính theo IP học viên — bằng chứng: `RateLimitPublicPolicyIT`, ca `clientBehindExemptProxyIsCountedByClientIp`
- AC-29: Máy không tin cậy tự gửi `X-Forwarded-For` chứa IP được miễn vẫn bị giới hạn — bằng chứng: `RateLimitPublicPolicyIT`, ca `spoofedForwardedHeaderIsNotExempt`
- AC-30: IP được miễn vẫn chịu giới hạn đăng nhập và tải tệp — bằng chứng: `RateLimitPublicPolicyIT`, ca `exemptIpStaysLimitedOnLoginAndUpload`
- AC-31: Mục sai định dạng trong `exempt-ips` làm ứng dụng không khởi động — bằng chứng: `RateLimitExemptIpsPropertiesTests` (mới)
- AC-32: `/api/v1/public/payments/vnpay/ipn` không bị giới hạn dù gọi quá 120 lần — bằng chứng: `RateLimitPublicPolicyIT`, ca `vnpayIpnIsNeverLimited`
- AC-33: DB của đợt 2: `operations`, `content_admin` nhận đúng quyền theo BS 7; quyền đã bị gỡ tay khỏi hai vai trò này không tự quay lại — bằng chứng: `PermissionCatalogSyncTest`, ca `wave2Db_grantsAgreedRolesAndKeepsManualRemovals`

**Ảnh hưởng ngoài task:**

- Test đếm cứng số quyền của `system_admin` (`backend/src/test/java/vn/unicom/thi360/identity/PermissionCatalogSyncTest.java:298`, `hasSize(17)`) → đổi thành `Permission.values().length - 1`; `PermissionCatalogSyncTest` xanh [chủ: task này]
- Test dựng thẳng record giới hạn tần suất bốn tham số (`backend/src/test/java/vn/unicom/thi360/shared/security/ClientIpResolverTests.java:15`) → cập nhật theo record mới; `ClientIpResolverTests` xanh [chủ: task này]
- Nơi dựng `UserAdminService` trong test (`backend/src/test/java/vn/unicom/thi360/identity/application/StudentLearningProviderTests.java:135`) → truyền danh sách; `StudentLearningProviderTests` xanh [chủ: task này]
- API hồ sơ học tập (`AdminUserController`, `GET /api/v1/admin/users/{id}/learning`) và màn dùng nó (`e2e/tests/learning-profile.spec.ts`) → `UserLearningMultiProviderIT` (mới); `pnpm --filter @thi360/e2e test -- tests/learning-profile.spec.ts` xanh [chủ: task này]
- Luồng đăng nhập (`AuthService.login`, `AuthController.login`) → `AuthFlowIT`, `AuthRedirectAndLogoutCookieIT`, `LoginThrottleIT`, `AuditAppLoginIT`, `SessionIT` xanh [chủ: task này]
- Thực thể `UserAccount` có thêm cột, được tạo ở `DevDataInitializer.java:138`, `UserAdminService.java:525` → `Thi360ApplicationTests` khởi động với `ddl-auto: validate`; `UserAccountAdminIT` xanh [chủ: task này]
- Bộ lọc giới hạn tần suất đang chỉ xử lý POST (`backend/src/main/java/vn/unicom/thi360/shared/security/RateLimitFilter.java:63`) → `RateLimitFilterIT`, `TrustedProxyTomcatIT`, `QuestionImportUploadIT`, `ImportBatchAdminIT` xanh [chủ: task này]
- Test audit chạy Flyway trên toàn bộ `classpath:db/migration` với bốn schema (`backend/src/test/java/vn/unicom/thi360/audit/AuditIntegrationTests.java:569`, `backend/src/test/java/vn/unicom/thi360/audit/AuditSharedRoleIntegrationTests.java:66`) → migration mới có `CREATE SCHEMA IF NOT EXISTS`; `AuditIntegrationTests`, `AuditSharedRoleIntegrationTests` xanh [chủ: task này]
- Danh sách schema của Flyway (`backend/src/main/resources/application.yml:23`) → `Thi360ApplicationTests` xanh [chủ: task này]
- Ba tệp thông điệp phải khớp khóa (`backend/src/test/java/vn/unicom/thi360/shared/web/MessageBundlesTest.java`) → `MessageBundlesTest` xanh [chủ: task này]
- Màn phân quyền hiện mã quyền lạ bằng tên backend trả về (`apps/web-admin/src/features/rbac/messages.ts`) → `pnpm --filter @thi360/web-admin test -- src/features/rbac/__tests__/permissions-tab-cards.test.tsx`; `pnpm --filter @thi360/e2e test -- tests/rbac.spec.ts` xanh [chủ: task này]
- Quyền và cache quyền (`AuthzEvictionIT`, `AuthzApiIT`, `BulkAuthzIT`, `MultiIssuerIT`, `RequirePermissionAnyOfIT`, `EndpointPermissionCoverageTest`) → các test này xanh [chủ: task này]
- Workflow E2E chạy `api,worker,dev` (`.github/workflows/e2e.yml:66`, `e2e/playwright.config.ts:5`) → backend lấy pepper từ khối `dev`, không cần sửa workflow; kịch bản C0-S1 đạt [chủ: task này]
- Nơi khởi tạo schema ngoài Flyway — không có: đã tìm `questionbank` trong `infra/`, `.github/` → không cần sửa; `Thi360ApplicationTests` xanh [chủ: task này]
- Menu `/grading` dùng `GRADING.VIEW` (`apps/web-admin/src/lib/navigation.ts:96`, `apps/web-admin/src/lib/navigation.test.ts`): từ khi có quyền này, `system_admin` qua được cổng quyền và trang báo "chưa nối API" khi tắt cờ mock → FA-4 nối API thật; `navigation.test.ts` xanh sau FA-4 [chủ: FA-4]
- Mở `GET /api/v1/public/**`, đặt `exempt-ips` (`SecurityConfig`, `CookieAuthFilter`) → `StorefrontRateLimitIT` (mới của CM-1) có cả hai trường hợp [chủ: CM-1]
- E2E gọi mọi API từ một IP nên có thể chạm 120 yêu cầu mỗi phút (`e2e/playwright.config.ts:72`) → FS-1 đặt `THI360_SECURITY_RATELIMIT_EXEMPTIPS`; bộ E2E của FS-1 không có phản hồi 429 [chủ: FS-1]
- `EntitlementPort`, `StudentLearningProvider` phần `entitlements` (`commerce/spi`, mới) → test của CM-4a chạy trên chữ ký đã khai [chủ: CM-4a]
- `AttemptStatusProvider`, `StudentLearningProvider` phần `attempts` (`commerce/spi`, mới) → test của AT-1 chạy trên chữ ký đã khai [chủ: AT-1]
- Pepper dùng để băm OTP, mã đặt lại (`TokenPepperProperties`, mới) → test băm mã của ID-1, ID-2 đọc khóa này [chủ: ID-1, ID-2]
- Ghi `preferred_locale` khi đăng ký (`UserAccount`) → test đăng ký của ID-1 kiểm cột này [chủ: ID-1]
- `operations`, `content_admin` có quyền mới theo BS 7 → test quyền của FA-1, FA-2, FA-3, FA-4 vẫn dùng vai trò tự tạo để kiểm từng quyền riêng lẻ [chủ: FA-1, FA-2, FA-3, FA-4]
- Giới hạn theo IP chỉ đúng khi web chạy với `THI360_TRUST_REAL_IP=1` (`packages/web-kit/src/proxy.ts`) → tài liệu triển khai ghi điều kiện này [chủ: QA]
- Bản demo cần pepper riêng trong `.env` của nó (`~/deploy/thi360-demo/.env` trên máy demo, ngoài repo) → kịch bản VP2-S1 của VP-2 đạt [chủ: VP-2]
- C-0b, ML cùng sửa `application.yml`, `.env.example` trong đợt 1 (B9) → thêm ở cuối khối; lane merge sau rebase và chạy lại `Thi360ApplicationTests` [chủ: C-0b, ML]

**Kế hoạch kiểm thử:**

- AC-1: `ModularityTest`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=ModularityTest`
- AC-2: `CommerceSpiContractTests`, hợp đồng, `cd backend && ./mvnw -B -ntp test -Dtest=CommerceSpiContractTests`
- AC-3: `StudentLearningProviderTests#mergesResultsOfTwoProviders`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=StudentLearningProviderTests`
- AC-4: `StudentLearningProviderTests#noProviderOrNullResultIsEmpty`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=StudentLearningProviderTests`
- AC-5: `UserLearningMultiProviderIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=UserLearningMultiProviderIT`
- AC-6: `Wave3SchemaMigrationIT#createsCommerceAndAttemptSchemas`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=Wave3SchemaMigrationIT`
- AC-7: `PermissionCatalogSyncTest#freshDb_systemAdminHoldsNineWave3Permissions`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PermissionCatalogSyncTest`
- AC-8: `PermissionCatalogSyncTest#systemRolesGetAgreedWave3Permissions`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PermissionCatalogSyncTest`
- AC-9: `PermissionCatalogSyncTest#wave2Db_keepsManuallyRemovedGrant_andGainsNineNewPermissions_andEvictsAuthzCache`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PermissionCatalogSyncTest`
- AC-10: `PermissionCatalogSyncTest#secondSyncChangesNothing`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PermissionCatalogSyncTest`
- AC-11: `Wave3PermissionsTests`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=Wave3PermissionsTests`
- AC-12: `Wave3PermissionsTests#everyNewPermissionHasLabels` và `MessageBundlesTest`, unit, `cd backend && ./mvnw -B -ntp test -Dtest='Wave3PermissionsTests,MessageBundlesTest'`
- AC-13: `AdminApiForbiddenMatrixIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AdminApiForbiddenMatrixIT`
- AC-14: `TokenPepperPropertiesTests#missingPepperFailsStartup`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=TokenPepperPropertiesTests`
- AC-15: `TokenPepperPropertiesTests#devAndTestUseDevOnlyValue`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=TokenPepperPropertiesTests`
- AC-16: quét tệp và diff, hợp đồng, `grep -cE "^THI360_TOKEN_PEPPER=.+" .env.example` và hai lệnh ở A7 mục 10
- AC-17: kịch bản C0-S1, E2E, `docker compose -f infra/docker-compose.yml up -d && cd backend && ./mvnw -B -ntp spring-boot:run -Dspring-boot.run.profiles=api,worker,dev`, rồi `curl -s http://localhost:8080/actuator/health` trả `{"status":"UP"...}`
- AC-18: `Wave3SchemaMigrationIT#existingAccountsGetDefaultLocale`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=Wave3SchemaMigrationIT`
- AC-19: `PreferredLocaleLoginIT#loginStoresLocaleFromCookie`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=PreferredLocaleLoginIT`
- AC-20: `PreferredLocaleLoginIT#missingOrUnknownCookieKeepsStoredLocale`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=PreferredLocaleLoginIT`
- AC-21: `PreferredLocaleLoginIT#failedLoginDoesNotTouchLocale`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=PreferredLocaleLoginIT`
- AC-22: so OpenAPI, hợp đồng, `cd backend && ./mvnw -B -ntp verify && diff -u ../packages/api-client/openapi.json target/openapi.json`
- AC-23: `RateLimitPublicPolicyIT#limitsAt121WithinWindow`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RateLimitPublicPolicyIT`
- AC-24: `RateLimitPublicPolicyIT#windowResetAllowsAgain`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RateLimitPublicPolicyIT`
- AC-25: `RateLimitPublicPolicyIT#concurrentRequestsNeverExceedLimit`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RateLimitPublicPolicyIT`
- AC-26: `RateLimitPublicPolicyIT#exemptIpIsNotLimited`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RateLimitPublicPolicyIT`
- AC-27: `RateLimitPublicPolicyIT#defaultListExemptsNobody`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RateLimitPublicPolicyIT`
- AC-28: `RateLimitPublicPolicyIT#clientBehindExemptProxyIsCountedByClientIp`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RateLimitPublicPolicyIT`
- AC-29: `RateLimitPublicPolicyIT#spoofedForwardedHeaderIsNotExempt`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RateLimitPublicPolicyIT`
- AC-30: `RateLimitPublicPolicyIT#exemptIpStaysLimitedOnLoginAndUpload`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RateLimitPublicPolicyIT`
- AC-31: `RateLimitExemptIpsPropertiesTests`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=RateLimitExemptIpsPropertiesTests`
- AC-32: `RateLimitPublicPolicyIT#vnpayIpnIsNeverLimited`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RateLimitPublicPolicyIT`
- AC-33: `PermissionCatalogSyncTest#wave2Db_grantsAgreedRolesAndKeepsManualRemovals`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PermissionCatalogSyncTest`
- Hồi quy:
  - Toàn bộ backend, gồm mọi test ở các dòng chủ "task này": `cd backend && ./mvnw -B -ntp verify`
  - Web quản trị: `pnpm --filter @thi360/web-admin test -- src/features/rbac/__tests__/permissions-tab-cards.test.tsx`
  - E2E: `pnpm --filter @thi360/e2e test -- tests/rbac.spec.ts tests/learning-profile.spec.ts`
  - Dòng của FA-1, FA-2, FA-3, FA-4, CM-1, CM-4a, AT-1, ID-1, ID-2, FS-1, QA, VP-2, C-0b, ML: task chủ chạy phép kiểm đã ghi ở dòng đó.
- Kiểm thử rủi ro:
  - Từng quyền riêng lẻ và khi thiếu: C-0 chưa thêm endpoint nào dùng chín quyền mới; AC-8, AC-11 kiểm việc gán, `AdminApiForbiddenMatrixIT` kiểm endpoint hiện có. Task thêm endpoint tự kiểm 401, 403 theo A7.
  - Gửi lại, đồng thời: AC-10 (đồng bộ quyền chạy lại), AC-25 (130 yêu cầu song song).
  - Migration trên DB có dữ liệu: AC-9 (DB của đợt 2), AC-18 (Flyway dừng ở migration ngay trước, chèn tài khoản, rồi chạy tiếp).
  - Giá trị biên: AC-23 (120 và 121), AC-24 (hết cửa sổ), AC-31 (mục sai định dạng).
  - Giả mạo: AC-29 (`X-Forwarded-For` từ máy không tin cậy).
  - đỏ trước: AC-5, AC-9, AC-14, AC-19, AC-23, AC-26, AC-29
- Dữ liệu test: Testcontainers Postgres, Redis, Keycloak riêng của từng lane; bean `StudentLearningProvider` giả trong test; `RateLimitPublicPolicyIT` dựng thẳng `RateLimitFilter` với `MockHttpServletRequest` trên Redis thật vì chưa có endpoint công khai trước CM-1; mỗi ca dùng dải IP riêng để không chung bộ đếm; không dùng khóa thật, chỉ giá trị `dev-only-`.
- Không kiểm: hai tiến trình cùng thêm quyền mới lúc khởi động (hành vi sẵn có của `PermissionCatalogSync`, đợt này không đổi); giới hạn tần suất đi qua proxy web và nginx (phụ thuộc triển khai, B6); `questionbank` còn gọi `Instant.now()` (B5, không liên quan task này).

**Câu hỏi mở:** TM 4 không nói đăng nhập không có cookie `thi360_locale` thì ghi gì; C-0 giữ giá trị đang lưu (AC-20), BA chốt.

## C-0b · Profile e2e, đồng hồ thử, `ForbiddenKeysAssert`

**Spec:** plan A6, B5, B6, B9; LT 7.3, 13; TM 10.1, 14.

**Sản phẩm:**

- Profile `e2e`:
  - khối `on-profile: e2e` trong `application.yml`, kèm giá trị `dev-only-…` của `thi360.token.pepper`;
  - chốt chặn khởi động (A6): bật `e2e` cùng `prod` thì ứng dụng không khởi động;
  - `MutableClock` thay bean `Clock` (`@Primary`, chỉ ở `e2e`): giữ độ lệch so với giờ hệ thống, nên giờ vẫn chạy sau khi tua;
  - `POST /api/v1/test/clock` nhận đúng một trong `{at}`, `{advanceSeconds}`, `{reset: true}`, trả `{serverNow}`; `GET /api/v1/test/clock` trả `{serverNow}`;
  - JWT của Keycloak vẫn kiểm hạn theo giờ thật ở `e2e`: dùng `TwoIssuerJwtDecoder` dựng bằng hàm một tham số (giờ hệ thống), vì token do Keycloak cấp theo giờ thật;
  - quy tắc `/api/v1/test/**` theo B6: gọi không cần đăng nhập khi bật `e2e`; không bật thì 404; không có trong OpenAPI (`@Hidden`). ML và CM-3a thêm endpoint của mình theo quy tắc này.
- Thêm `e2e` vào profile backend của bộ E2E (`e2e/playwright.config.ts`, `.github/workflows/e2e.yml`).
- `ForbiddenKeysAssert` trong `backend/src/test/java/vn/unicom/thi360/support/`:
  - hai danh sách khóa cấm đặt tên riêng, ghi ở một nơi trong lớp: lượt thi (LT 7.3), bảng chiếu (TM 10.1);
  - quét đệ quy cả mảng và đối tượng lồng nhau;
  - báo đường dẫn JSON của khóa vi phạm, không in giá trị;
  - có test của chính lớp này.
- Khối `on-profile: e2e` nạp thêm `classpath:db/devdata` vào `spring.flyway.locations`, như `dev` và `test`, để bộ dữ liệu của DS chạy được ở profile này.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/shared/testapi/**` (mới)
- `backend/src/test/java/vn/unicom/thi360/shared/testapi/**` (mới)
- `backend/src/test/java/vn/unicom/thi360/support/ForbiddenKeysAssert.java`, `backend/src/test/java/vn/unicom/thi360/support/ForbiddenKeysAssertTests.java` (mới)
- `e2e/tests/test-clock.spec.ts` (mới)
- thêm vào: `backend/src/main/resources/application.yml` (khối `on-profile: e2e`)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java` (`/api/v1/test/**`)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/CookieAuthFilter.java` (tiền tố `/api/v1/test/`)
- thêm vào: `e2e/playwright.config.ts` (profile `e2e` trong lệnh backend mặc định)
- thêm vào: `.github/workflows/e2e.yml` (profile `e2e` trong `E2E_BACKEND_CMD`)

**Ngoài phạm vi:** `GET /api/v1/test/mail` (ML); endpoint và cổng giả lập của VNPAY ở profile `e2e` (CM-3a); đổi `Instant.now()` sang `Clock` trong `exam` (EX-1); `Instant.now()` trong `questionbank` (không sửa trong đợt, B5); dùng `ForbiddenKeysAssert` trên API của từng task (EX-1, CM-1, CM-6, AT-1, AT-2a, AT-2b, AT-3, QA).

**Nghiệm thu:**

- AC-1: Profile `api` thường gọi `POST /api/v1/test/clock` nhận 404 — bằng chứng: `TestApiAbsentWithoutE2eIT` (mới), ca `clockEndpointAnswers404`
- AC-2: Không profile nào ngoài `e2e` đăng ký handler dưới `/api/v1/test` — bằng chứng: `TestApiAbsentWithoutE2eIT`, ca `noHandlerUnderTestPrefix`
- AC-3: Profile `e2e` tua thêm giờ bằng `advanceSeconds`, giờ mới thấy được qua `GET /api/v1/test/clock` — bằng chứng: `E2eClockApiIT` (mới), ca `advanceMovesServerNow`
- AC-4: `{at}` đặt đúng giờ, `{reset: true}` trả về giờ hệ thống — bằng chứng: `E2eClockApiIT`, ca `setAndResetClock`
- AC-5: Thân rỗng, có từ hai trường trở lên, hoặc giờ sai định dạng nhận 400 — bằng chứng: `E2eClockApiIT`, ca `invalidBodyAnswers400`
- AC-6: Bean được tiêm ở mọi nơi là `MutableClock` và giờ vẫn chạy sau khi tua — bằng chứng: `MutableClockTests` (mới); `E2eClockApiIT`, ca `injectedClockIsMutable`
- AC-7: Khởi động với `e2e` cộng `prod` thì lỗi, thông báo nêu hai profile — bằng chứng: `E2eProfileGuardTests` (mới)
- AC-8: Khởi động với `e2e` không kèm `prod` thành công, pepper có giá trị `dev-only-` đọc qua `Environment` — bằng chứng: `E2eProfileGuardTests`, ca `e2eAloneStartsWithDevOnlyPepper`
- AC-9: `/api/v1/test/**` không có trong OpenAPI khi bật `e2e` — bằng chứng: `E2eClockApiIT`, ca `testApiIsHiddenFromOpenApi`
- AC-10: Sau khi tua 3 giờ, đăng nhập mới vẫn gọi được API cần phiên — bằng chứng: `E2eClockAuthIT` (mới)
- AC-11: `ForbiddenKeysAssert` báo khóa cấm ở cấp sâu trong mảng, nêu đường dẫn JSON, không in giá trị — bằng chứng: `ForbiddenKeysAssertTests`, ca `reportsNestedPathWithoutValue`
- AC-12: Hai danh sách có đủ khóa của LT 7.3 và TM 10.1; JSON sạch thì đạt; khóa được phép (`points`, `inputHint`, `wordLimit`) không bị báo — bằng chứng: `ForbiddenKeysAssertTests`, ca `listsMatchSpecAndAllowedKeysPass`
- AC-13: Playwright khởi động backend với `e2e`, tua giờ qua API và đọc lại được — bằng chứng: `e2e/tests/test-clock.spec.ts` (mới)
- AC-14: Khởi động với profile `e2e`: các script `R__seed_*` trong `db/devdata` đã chạy — bằng chứng: `E2eProfileDevDataIT` (mới)

**Ảnh hưởng ngoài task:**

- Bộ giải mã JWT kiểm hạn theo bean `Clock` (`backend/src/main/java/vn/unicom/thi360/shared/security/TwoIssuerJwtDecoder.java:136`): tua quá thời hạn token thì mọi JWT hết hạn → bean riêng ở `e2e`; `E2eClockAuthIT` (mới), `TwoIssuerJwtDecoderTests`, `ExpiredTokenLogoutIT` xanh [chủ: task này]
- Bean `Clock` mặc định (`backend/src/main/java/vn/unicom/thi360/shared/time/ClockConfig.java`, `@ConditionalOnMissingBean`) → `Thi360ApplicationTests` xanh; AC-6 chứng minh `MutableClock` thắng ở `e2e` [chủ: task này]
- Phiên và cookie ngữ cảnh đọc `Clock` (`backend/src/main/java/vn/unicom/thi360/shared/security/SessionStore.java:246`, `CtxCookieSigner.java:26`) → `E2eClockAuthIT` (mới), `SessionStoreIT`, `CtxCookieSignerTests` xanh [chủ: task này]
- Danh sách mở của bảo mật (`backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java:115`, `.anyRequest().authenticated()`): `/api/v1/test/**` ẩn danh đang nhận 401 → `TestApiAbsentWithoutE2eIT` (mới) nhận 404, `SessionIT` xanh [chủ: task này]
- `CookieAuthFilter` so khớp đường dẫn bỏ qua (`backend/src/main/java/vn/unicom/thi360/shared/security/CookieAuthFilter.java:35`) → `E2eClockApiIT` (mới), `ExpiredTokenLogoutIT` xanh [chủ: task này]
- Lệnh backend của E2E (`e2e/playwright.config.ts:5`, `.github/workflows/e2e.yml:66`) → `pnpm --filter @thi360/e2e test -- tests/test-clock.spec.ts tests/session-refresh.spec.ts` xanh [chủ: task này]
- Xuất OpenAPI chạy với `api,openapi,test` (`backend/src/test/java/vn/unicom/thi360/OpenApiExportIT.java`, `packages/api-client/openapi.json`) → `diff -u ../packages/api-client/openapi.json target/openapi.json` rỗng [chủ: task này]
- Package `shared/testapi` trong module `shared` đang mở (`backend/src/main/java/vn/unicom/thi360/shared/package-info.java`) → `ModularityTest` xanh [chủ: task này]
- Endpoint mới được quét quyền (`backend/src/test/java/vn/unicom/thi360/EndpointPermissionCoverageTest.java`, `AdminApiForbiddenMatrixIT.java`): test chạy không có `e2e` nên không thấy handler → hai test này xanh [chủ: task này]
- `exam` đọc giờ thẳng từ `Instant.now()` nên chưa theo đồng hồ thử → EX-1 đổi sang `Clock` và tự kiểm giờ công bố đi theo đồng hồ thử [chủ: EX-1]
- `GET /api/v1/test/mail` thêm vào cùng quy tắc (`shared/mail`, mới) → `TestApiAbsentWithoutE2eIT` vẫn xanh sau ML [chủ: ML]
- Cổng giả lập chạy ở `e2e` theo cùng quy tắc → `TestApiAbsentWithoutE2eIT` vẫn xanh sau CM-3a [chủ: CM-3a]
- Giá trị pepper của `e2e` đọc cùng khóa `thi360.token.pepper` với `TokenPepperProperties` (mới) → `Thi360ApplicationTests` xanh sau khi C-0 và C-0b cùng merge [chủ: C-0]
- Các task dùng `ForbiddenKeysAssert` (`backend/src/test/java/vn/unicom/thi360/support/ForbiddenKeysAssert.java`, mới) → test của từng task gọi đúng danh sách; thêm khóa thì thêm vào danh sách chung [chủ: EX-1, CM-1, CM-6, AT-1, AT-2a, AT-2b, AT-3, QA]

**Kế hoạch kiểm thử:**

- AC-1: `TestApiAbsentWithoutE2eIT#clockEndpointAnswers404`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TestApiAbsentWithoutE2eIT`
- AC-2: `TestApiAbsentWithoutE2eIT#noHandlerUnderTestPrefix`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TestApiAbsentWithoutE2eIT`
- AC-3: `E2eClockApiIT#advanceMovesServerNow`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=E2eClockApiIT`
- AC-4: `E2eClockApiIT#setAndResetClock`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=E2eClockApiIT`
- AC-5: `E2eClockApiIT#invalidBodyAnswers400`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=E2eClockApiIT`
- AC-6: `MutableClockTests` (unit) và `E2eClockApiIT#injectedClockIsMutable` (tích hợp), `cd backend && ./mvnw -B -ntp test -Dtest=MutableClockTests`, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=E2eClockApiIT`
- AC-7: `E2eProfileGuardTests`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=E2eProfileGuardTests`
- AC-8: `E2eProfileGuardTests#e2eAloneStartsWithDevOnlyPepper`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=E2eProfileGuardTests`
- AC-9: `E2eClockApiIT#testApiIsHiddenFromOpenApi`, hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=E2eClockApiIT`
- AC-10: `E2eClockAuthIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=E2eClockAuthIT`
- AC-11: `ForbiddenKeysAssertTests#reportsNestedPathWithoutValue`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=ForbiddenKeysAssertTests`
- AC-12: `ForbiddenKeysAssertTests#listsMatchSpecAndAllowedKeysPass`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=ForbiddenKeysAssertTests`
- AC-13: `test-clock.spec.ts`, E2E, `pnpm --filter @thi360/e2e test -- tests/test-clock.spec.ts`
- AC-14: `E2eProfileDevDataIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=E2eProfileDevDataIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy:
  - Bảo mật, phiên, đồng hồ: `cd backend && ./mvnw -B -ntp verify -Dtest='TwoIssuerJwtDecoderTests,CtxCookieSignerTests,Thi360ApplicationTests,ModularityTest,EndpointPermissionCoverageTest' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='ExpiredTokenLogoutIT,SessionStoreIT,SessionIT,AdminApiForbiddenMatrixIT'`
  - OpenAPI: `cd backend && ./mvnw -B -ntp verify && diff -u ../packages/api-client/openapi.json target/openapi.json`
  - E2E với profile mới: `pnpm --filter @thi360/e2e test -- tests/session-refresh.spec.ts`
  - Dòng của EX-1, ML, CM-3a, C-0, CM-1, CM-6, AT-1, AT-2a, AT-2b, AT-3, QA: task chủ chạy phép kiểm đã ghi ở dòng đó.
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: Testcontainers của từng lane; `E2eClockApiIT`, `E2eClockAuthIT` chạy với `api,worker,e2e`, đặt lại đồng hồ sau mỗi ca; `E2eClockAuthIT` dùng Keycloak của `AbstractKeycloakIntegrationTest`; JSON mẫu của `ForbiddenKeysAssertTests` viết trong test.
- Không kiểm: tua lùi đồng hồ (không kịch bản nào cần); API và worker chạy ở hai tiến trình riêng (đồng hồ thử chỉ tác động trong một tiến trình, E2E chạy `api,worker` chung); gọi `POST /api/v1/test/clock` kèm cookie đăng nhập mà không có token XSRF, vẫn nhận 403 của `CsrfFilter` (Playwright gọi API này không kèm cookie); giờ của `questionbank` (B5).

## ML · Gửi thư

**Spec:** TM 4, C10, 5.3 (loại thư, điều kiện gửi thư đơn hàng), 9.4 bước 7, 16.4 (thư bộ đề); plan B2, B5, B6, B7. **Phụ thuộc mềm:** C-0b.

**Sản phẩm:**

- Bảng `shared.email_message` đủ cột TM 4, chỉ mục trên các thư chưa gửi theo `next_attempt_at`.
- `MailQueue.enqueue(kind, to, locale, model, userId)` chạy trong transaction của bên gọi (bắt buộc có transaction), dựng tiêu đề, HTML, văn bản ngay lúc xếp hàng. `MailKind` khai các khóa model từng loại thư cần; thiếu khóa thì báo lỗi lúc xếp hàng.
- Worker gửi theo lô (profile `worker`, cờ `thi360.mail.scheduling.enabled`, tắt ở khối `test`):
  - mỗi 5 giây lấy 20 thư bằng `FOR UPDATE SKIP LOCKED`;
  - giữ chỗ bằng cách đẩy `next_attempt_at` rồi commit, gửi SMTP ngoài transaction, ghi kết quả trong transaction mới (B2: không gọi mạng khi giữ khóa dòng);
  - gửi lại sau 1 phút, 5 phút, 30 phút, 2 giờ; sau 5 lần thì đánh dấu thất bại;
  - 5xx ở `RCPT TO` thì thất bại ngay;
  - dọn thư đã gửi sau 30 ngày.
- `JavaMailSender` theo biến `THI360_SMTP_HOST`, `THI360_SMTP_PORT`, `THI360_SMTP_USERNAME`, `THI360_SMTP_PASSWORD`, `THI360_SMTP_TLS` (`starttls`, `tls`, `none`), `THI360_MAIL_FROM`, `THI360_WEB_URL`; có thời gian chờ kết nối và đọc. `management.health.mail.enabled: false`, để SMTP lỗi không làm `/actuator/health` báo `DOWN`.
- Mẫu Thymeleaf `resources/mail/templates/<kind>.<vi|en>.{html,txt}` với một layout chung có màu thương hiệu theo `DESIGN.md`; tiêu đề là dòng đầu của tệp `.txt`. Loại thư: `EMAIL_VERIFY`, `EMAIL_CHANGE`, `EMAIL_CHANGED_NOTICE`, `PASSWORD_RESET`, `ORDER_FULFILLED`, `REFUND_DONE`. Mẫu `ORDER_FULFILLED` liệt kê được nhiều bài được cấp (TM 16.4).
- Kiểm mẫu lúc khởi động: thiếu mẫu thì không khởi động.
- Lớp hỗ trợ test `MailOutbox` đọc thư từ bảng `email_message` theo người nhận và loại (TM 4).
- `GET /api/v1/test/mail?to=&kind=` (profile `e2e`, B6) trả thư mới nhất, kể cả mã OTP trong thân thư.
- Nhật ký và `last_error` không chứa thân thư, mã OTP, mật khẩu SMTP.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/shared/mail/**` (mới)
- `backend/src/main/resources/mail/templates/**` (mới)
- `backend/src/main/resources/db/migration/shared/V*__create_email_message.sql` (mới)
- `backend/src/test/java/vn/unicom/thi360/shared/mail/**` (mới)
- `backend/src/test/java/vn/unicom/thi360/support/MailOutbox.java` (mới)
- thêm vào: `backend/pom.xml` (`spring-boot-starter-mail`, `spring-boot-starter-thymeleaf`, `com.icegreen:greenmail-junit5`, theo B7)
- thêm vào: `backend/src/main/resources/application.yml` (`spring.mail`, `thi360.mail`, tắt lịch ở khối `test`)
- thêm vào: `.env.example` (`THI360_SMTP_HOST`, `THI360_SMTP_USERNAME`, `THI360_SMTP_PASSWORD`, `THI360_SMTP_TLS`, `THI360_MAIL_FROM`; chỉ tên hoặc giá trị dev của Mailpit)

**Ngoài phạm vi:** quyết định gửi thư nào và lúc nào, gồm điều kiện "email đã xác minh" của `ORDER_FULFILLED`, `REFUND_DONE` (CM-3a, CM-5, CM-6); xếp thư OTP, đổi email, đặt lại mật khẩu (ID-1, ID-2); dựng danh sách bài được cấp của bộ đề (CM-6); ghi `preferred_locale` (C-0, ID-1); quy tắc chung của `/api/v1/test/**` (C-0b); SMTP của bản demo (VP-2).

**Nghiệm thu:**

- AC-1: Transaction của bên gọi rollback thì không có thư — bằng chứng: `MailQueueIT` (mới), ca `rollbackLeavesNoMail`
- AC-2: Gọi `enqueue` ngoài transaction thì báo lỗi — bằng chứng: `MailQueueIT`, ca `enqueueRequiresTransaction`
- AC-3: Thiếu khóa model của loại thư thì báo lỗi lúc xếp hàng, không có dòng mới — bằng chứng: `MailQueueIT`, ca `missingModelKeyIsRejected`
- AC-4: GreenMail nhận đúng tiêu đề, HTML, văn bản theo ngôn ngữ `vi`, `en` cho cả sáu loại thư — bằng chứng: `MailDispatchIT` (mới), ca `deliversSubjectHtmlAndTextPerLocale`
- AC-5: Giá trị trong model được thoát HTML; `ORDER_FULFILLED` liệt kê đủ các bài trong model — bằng chứng: `MailTemplateRenderTests` (mới)
- AC-6: 5xx ở `RCPT TO` thì thất bại ngay, không gửi lại — bằng chứng: `MailDispatchIT`, ca `permanentRcptFailureFailsAtOnce`
- AC-7: Lỗi tạm thì gửi lại theo lịch 1 phút, 5 phút, 30 phút, 2 giờ — bằng chứng: `MailRetryScheduleTests` (mới); `MailDispatchIT`, ca `transientFailureIsRescheduled`
- AC-8: Sau 5 lần lỗi thì đánh dấu `failed_at`, worker không lấy lại — bằng chứng: `MailDispatchIT`, ca `fifthFailureMarksFailed`
- AC-9: Hai worker chạy cùng lúc không gửi trùng — bằng chứng: `MailDispatchIT`, ca `concurrentWorkersDoNotSendTwice`
- AC-10: Mỗi lượt lấy tối đa 20 thư — bằng chứng: `MailDispatchIT`, ca `batchTakesAtMostTwenty`
- AC-11: Khi gửi SMTP không có transaction đang mở — bằng chứng: `MailDispatchIT`, ca `smtpRunsOutsideTransaction`
- AC-12: Thư đã gửi quá 30 ngày bị xóa; thư gửi chưa đủ 30 ngày và thư chưa gửi được giữ — bằng chứng: `MailCleanupIT` (mới)
- AC-13: Thiếu một tệp mẫu thì ứng dụng không khởi động, thông báo nêu tên tệp — bằng chứng: `MailTemplateStartupTests` (mới)
- AC-14: Tiêu đề là dòng đầu của tệp `.txt`, không xuất hiện lại trong thân văn bản — bằng chứng: `MailTemplateRenderTests`, ca `subjectIsFirstLineOfText`
- AC-15: `THI360_SMTP_TLS` nhận `starttls`, `tls`, `none`; giá trị khác làm ứng dụng không khởi động — bằng chứng: `MailPropertiesTests` (mới)
- AC-16: SMTP không kết nối được thì `/actuator/health` vẫn `UP` — bằng chứng: `Thi360ApplicationTests`, ca `healthEndpointReportsUp`
- AC-17: `MailOutbox` đọc đúng thư mới nhất theo người nhận và loại — bằng chứng: `MailQueueIT`, ca `mailOutboxReadsLatestByRecipientAndKind`
- AC-18: Profile `e2e`: `GET /api/v1/test/mail?to=&kind=` trả thư mới nhất kèm mã OTP trong thân; không có thư thì 404 — bằng chứng: `MailTestApiIT` (mới), ca `returnsLatestMail`
- AC-19: Không bật `e2e` thì `GET /api/v1/test/mail` nhận 404 và không có trong OpenAPI — bằng chứng: `MailTestApiIT`, ca `absentWithoutE2eProfile`; `TestApiAbsentWithoutE2eIT` (của C-0b)
- AC-20: Nhật ký và `last_error` không chứa thân thư, mã OTP — bằng chứng: `MailDispatchIT`, ca `logsAndLastErrorCarryNoBody`
- AC-21: Thư đi tới Mailpit của compose dev — bằng chứng: `MailpitSmokeIT` (mới)

**Ảnh hưởng ngoài task:**

- `/actuator/health` là điều kiện chờ của E2E (`e2e/playwright.config.ts:61`, `backend/src/test/java/vn/unicom/thi360/Thi360ApplicationTests.java:12`) → `Thi360ApplicationTests` xanh khi không có SMTP [chủ: task này]
- Thêm thư viện vào `backend/pom.xml` làm đổi tự cấu hình của Spring (`spring-boot-starter-thymeleaf`) → `spring.thymeleaf.check-template-location: false`; `Thi360ApplicationTests`, `OpenApiExportIT` xanh [chủ: task này]
- Migration mới trong schema `shared`, chạy cả ở test audit dùng Flyway riêng (`backend/src/test/java/vn/unicom/thi360/audit/AuditIntegrationTests.java:569`, `backend/src/test/java/vn/unicom/thi360/audit/AuditSharedRoleIntegrationTests.java:66`) → hai test này xanh [chủ: task này]
- Khối `test` của `application.yml` tắt các lịch chạy nền (`backend/src/main/resources/application.yml:199`) → thêm `thi360.mail.scheduling.enabled: false`; `OutboxRelayTests`, `Thi360ApplicationTests` xanh [chủ: task này]
- Package `shared/mail` trong module `shared` đang mở (`backend/src/main/java/vn/unicom/thi360/shared/package-info.java`) → `ModularityTest` xanh [chủ: task này]
- OpenAPI (`packages/api-client/openapi.json`, `.github/workflows/backend.yml`): ML không thêm API công khai → `diff -u ../packages/api-client/openapi.json target/openapi.json` rỗng [chủ: task này]
- Biến `THI360_SMTP_PORT` đã có, trỏ vào Mailpit (`.env.example:31`, `infra/docker-compose.yml:96`) → `MailpitSmokeIT` (mới) gửi qua đúng cổng này [chủ: task này]
- Khối `on-profile: e2e` và quy tắc `/api/v1/test/**` của C-0b (`shared/testapi`, mới) → `MailTestApiIT` (mới), `TestApiAbsentWithoutE2eIT` xanh sau khi rebase lên C-0b [chủ: C-0b]
- C-0, C-0b cùng sửa `application.yml`, `.env.example` trong đợt 1 (B9) → thêm ở cuối khối; lane merge sau rebase và chạy lại `Thi360ApplicationTests` [chủ: C-0, C-0b]
- `MailQueue` được `identity` gọi cho OTP, đổi email, đặt lại mật khẩu (`shared/mail`, mới) → test của ID-1, ID-2 đọc thư bằng `MailOutbox` [chủ: ID-1, ID-2]
- Thư `ORDER_FULFILLED` chỉ xếp khi email đã xác minh (TM 5.3) → test của CM-3a có cả hai trường hợp [chủ: CM-3a]
- Thư `REFUND_DONE` chỉ xếp khi email đã xác minh (TM 5.3, 9.4 bước 7) → test của CM-5 có cả hai trường hợp [chủ: CM-5]
- Thư `ORDER_FULFILLED` của bộ đề liệt kê các bài được cấp (TM 16.4) → test của CM-6 kiểm đủ tên bài trong thư [chủ: CM-6]
- E2E đọc mã OTP qua `GET /api/v1/test/mail` (A6) → E2E đăng ký của FS-2 đọc được mã thật [chủ: FS-2]
- Bản demo chạy worker nên sẽ gửi thư thật nếu cấu hình SMTP (`~/deploy/thi360-demo/.env` trên máy demo, ngoài repo) → VP-2 trỏ SMTP của demo vào hộp thư thử; kịch bản VP2-S1 không gửi tới địa chỉ thật [chủ: VP-2]

**Kế hoạch kiểm thử:**

- AC-1: `MailQueueIT#rollbackLeavesNoMail`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailQueueIT`
- AC-2: `MailQueueIT#enqueueRequiresTransaction`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailQueueIT`
- AC-3: `MailQueueIT#missingModelKeyIsRejected`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailQueueIT`
- AC-4: `MailDispatchIT#deliversSubjectHtmlAndTextPerLocale`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailDispatchIT`
- AC-5: `MailTemplateRenderTests`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=MailTemplateRenderTests`
- AC-6: `MailDispatchIT#permanentRcptFailureFailsAtOnce`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailDispatchIT`
- AC-7: `MailRetryScheduleTests` (unit) và `MailDispatchIT#transientFailureIsRescheduled` (tích hợp), `cd backend && ./mvnw -B -ntp test -Dtest=MailRetryScheduleTests`, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailDispatchIT`
- AC-8: `MailDispatchIT#fifthFailureMarksFailed`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailDispatchIT`
- AC-9: `MailDispatchIT#concurrentWorkersDoNotSendTwice`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailDispatchIT`
- AC-10: `MailDispatchIT#batchTakesAtMostTwenty`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailDispatchIT`
- AC-11: `MailDispatchIT#smtpRunsOutsideTransaction`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailDispatchIT`
- AC-12: `MailCleanupIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailCleanupIT`
- AC-13: `MailTemplateStartupTests`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MailTemplateStartupTests`
- AC-14: `MailTemplateRenderTests#subjectIsFirstLineOfText`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=MailTemplateRenderTests`
- AC-15: `MailPropertiesTests`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=MailPropertiesTests`
- AC-16: `Thi360ApplicationTests#healthEndpointReportsUp`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=Thi360ApplicationTests`
- AC-17: `MailQueueIT#mailOutboxReadsLatestByRecipientAndKind`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailQueueIT`
- AC-18: `MailTestApiIT#returnsLatestMail`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailTestApiIT`
- AC-19: `MailTestApiIT#absentWithoutE2eProfile` và `TestApiAbsentWithoutE2eIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='MailTestApiIT,TestApiAbsentWithoutE2eIT'`
- AC-20: `MailDispatchIT#logsAndLastErrorCarryNoBody`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailDispatchIT`
- AC-21: `MailpitSmokeIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MailpitSmokeIT`
- Hồi quy:
  - Khởi động, module, Flyway, nền: `cd backend && ./mvnw -B -ntp test -Dtest='Thi360ApplicationTests,ModularityTest,OutboxRelayTests,AuditIntegrationTests,AuditSharedRoleIntegrationTests'`
  - OpenAPI: `cd backend && ./mvnw -B -ntp verify && diff -u ../packages/api-client/openapi.json target/openapi.json`
  - Dòng của C-0, C-0b, ID-1, ID-2, CM-3a, CM-5, CM-6, FS-2, VP-2: task chủ chạy phép kiểm đã ghi ở dòng đó.
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: GreenMail cổng ngẫu nhiên cho từng lớp test; Testcontainers Postgres riêng của từng lane; `MailpitSmokeIT` chạy Mailpit `axllent/mailpit:v1.27` bằng Testcontainers; địa chỉ người nhận sinh ngẫu nhiên theo ca, chỉ dùng miền `example.test`; đồng hồ của test tiêm `Clock` cố định.
- Không kiểm: tiến trình chết sau khi SMTP đã nhận thư mà chưa ghi kết quả thì thư có thể gửi lại (gửi ít nhất một lần, TM không đòi đúng một lần); thư của tài khoản đã xóa mềm (TM không nói); giao diện thư trên từng ứng dụng đọc thư.

## TS · Dựng lại seed đề test `TEST-*` theo mẫu BA (UNI-2701, cổng G0c cũ)

**Spec:** `docs/specs/2026-11-17-de-test-gia-dinh.md` mục 4.4 (nhánh 2B); mô tả issue UNI-2701; việc tồn đọng FU-QA-SEEDS, FU-SEEDGEN-1, FU-BE13-2, FU-FE4-D9 của đợt 2B (UNI-2700).

**Sản phẩm:**

- Đưa `scripts/generate_test_exam_seed.py` lên cấu trúc bài v2 của đợt 2B: không sinh lại `task_type_item_id`, giữ cấu trúc v2 khi sinh lại (FU-SEEDGEN-1).
- Trình sinh chỉ khớp id là thành viên của `blocks[]`, không khớp mọi id lồng nhau; id nhóm đã xóa mà section dùng lại không giữ tham chiếu cũ (FU-BE13-2).
- Dựng lại bài `TEST-*` của HSA (01–04), QDA-01, BCA-01, SPT-01, SP2E (01–03), THPTQG-02, THPTQG-03 theo mẫu BA hiện hành, đúng từng dòng lệch ở mục 4.4 của tài liệu đề test.
- Thống nhất mã QDA của seed với tên và bản vẽ (`QĐ02` hay `QD02`, theo mã của danh mục; FU-FE4-D9).
- Dựng lại các bài lỗi có chủ ý `TEST-ERR-*` từ bài nền mới (review vòng 4, P4-8): mỗi bài lỗi là một bài nền cộng đúng một biến đổi ghi ở mục 4.2 của tài liệu đề test, kèm tập mã lỗi mong đợi lấy từ lần chạy thật. Bài đối chứng hợp lệ (như `TEST-ERR-08`, nếu tài liệu vẫn ghi vậy) giữ vai đối chứng. Trình sinh tạo lại cả bài nền và bài lỗi.
- Nâng DB đã có seed của đợt 2B (review vòng 4, P4-5). Seed ghi theo id cố định với `ON CONFLICT DO NOTHING`, nên tệp mới không tự thay bài cũ. Task thêm bước nâng: bài `TEST-*` còn đúng cấu trúc seed 2B (chưa ai sửa; nhận bằng dấu phiên bản seed hoặc so nội dung, task chọn và ghi lý do) được thay bằng cấu trúc mới; bài admin đã sửa được giữ, như `TestExamSeedIT` đang khẳng định. Không sửa migration đã merge. Bản demo: Advisor chạy lại seed sau khi merge; G3 cho phép bỏ dữ liệu cũ của demo.
- Cập nhật `docs/specs/2026-11-17-de-test-gia-dinh.md` theo số mới; DS lấy số HSA, QDA, BCA, SP2E, SPT từ tài liệu này.

**Đường dẫn sở hữu:**

- `scripts/generate_test_exam_seed.py`
- `backend/src/main/resources/db/devdata/R__seed_test_exam_data.sql` (sinh lại)
- `docs/specs/2026-11-17-de-test-gia-dinh.md`
- thêm vào: `backend/src/test/java/vn/unicom/thi360/exam/TestExamSeedIT.java`
- `backend/src/test/java/vn/unicom/thi360/exam/TestExamSeedComplianceIT.java` (mới), `TestExamSeedUpgradeIT.java` (mới), `backend/src/test/resources/exam/seed-2b/**` (mới, bản seed 2B dùng làm điểm xuất phát của AC-7)

**Ngoài phạm vi:** Seed câu hỏi của ngân hàng (`R__seed_questionbank_dev_data.sql`) trừ khi bài `TEST-*` thiếu câu để đủ ma trận; khi đó task chỉ thêm câu vào cuối tệp này (thêm vào, review vòng 4, P4-1) và ghi lý do trong PR. Tệp đính kèm cho câu BCA (FU-BE10-ASSET). Bài đã công bố của đợt 3 (DS). Sửa luật kiểm tra của đợt 2B.

**Nghiệm thu:**

- AC-1: Mỗi bài nền `TEST-*` (không gồm bài lỗi `TEST-ERR-*`) khớp mẫu BA hiện hành: số câu, nhóm, nhãn, chùm, ma trận, `timing`, `templateGroupCode`, `omitted` như cột "Mẫu hiện hành" của mục 4.4 — bằng chứng: `TestExamSeedIT#everyTestExamMatchesTemplate` (mới)
- AC-2: Trên server thật, mỗi bài `TEST-*` chạy kiểm tra cho đúng kết quả mà tài liệu đề test ghi (hợp lệ, hoặc đúng mã lỗi đã ghi) và sinh thử được khi hợp lệ — bằng chứng: `TestExamSeedComplianceIT#everyTestExamChecksAsDocumented`, `#validTestExamsGeneratePreview` (mới)
- AC-3: Chạy lại `scripts/generate_test_exam_seed.py` trên nhánh của task không làm đổi `backend/src/main/resources/db/devdata/R__seed_test_exam_data.sql`: tệp seed là đầu ra của script, không sửa tay — bằng chứng: lệnh sinh lại seed và `git diff --exit-code` (mới)
- AC-4: Trình sinh chỉ thay id trong `blocks[]`: id nhóm đã xóa mà một section dùng lại không còn tham chiếu cũ sau khi sinh lại — bằng chứng: `TestExamSeedIT#generatorMatchesBlockMembersOnly` (mới)
- AC-5: Mã QDA trong seed khớp mã của danh mục và cách viết trên màn tạo bài — bằng chứng: `TestExamSeedIT#qdaCodesMatchCatalog` (mới)
- AC-6: Mỗi bài lỗi `TEST-ERR-*` bằng bài nền mới cộng đúng một biến đổi đã ghi; kiểm tra trả đúng tập mã lỗi mong đợi của bài đó, không thêm lỗi khác; bài đối chứng hợp lệ đạt kiểm tra; sinh lại không làm mất bài lỗi nào — bằng chứng: `TestExamSeedIT#errorFixturesDifferByOneChange`, `TestExamSeedComplianceIT#errorFixturesReportExactCodes` (mới)
- AC-7: DB đã nạp seed của đợt 2B (không phải DB trắng): sau khi chạy seed mới, bài `TEST-*` chưa ai sửa mang cấu trúc mới; một bài admin đã sửa giữ nguyên — bằng chứng: `TestExamSeedUpgradeIT#upgradesUneditedWave2BSeed`, `#keepsAdminEditedExam` (mới)

**Ảnh hưởng ngoài task:**

- Dữ liệu dev, demo đổi bài `TEST-*` → chạy lại `TestExamSeedIT`, `QuestionSeedIT`, `SampleQuestionCoverageIT`, `VactThptSampleDataIT` [chủ: task này]
- E2E của đợt 2B đọc bài `TEST-*` → chạy lại toàn bộ E2E web-admin của phần đề (`pnpm --filter @thi360/e2e test`) [chủ: task này]
- DS lấy số từ tài liệu đề test → DS phụ thuộc cứng task này qua EX-1 [chủ: DS]
- Bản demo trên `bro` chạy seed dev → Advisor báo M.Trường kiểm lại dữ liệu demo sau khi merge (không có dữ liệu thật) [chủ: Advisor]

**Kế hoạch kiểm thử:**

- AC-1: `TestExamSeedIT#everyTestExamMatchesTemplate` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=TestExamSeedIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `TestExamSeedComplianceIT#everyTestExamChecksAsDocumented`, `#validTestExamsGeneratePreview` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=TestExamSeedComplianceIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: lệnh sinh lại seed, kiểm tra, `python scripts/generate_test_exam_seed.py && git diff --exit-code backend/src/main/resources/db/devdata/R__seed_test_exam_data.sql`
- AC-4: `TestExamSeedIT#generatorMatchesBlockMembersOnly` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=TestExamSeedIT`
- AC-5: `TestExamSeedIT#qdaCodesMatchCatalog` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=TestExamSeedIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `TestExamSeedIT#errorFixturesDifferByOneChange`, `TestExamSeedComplianceIT#errorFixturesReportExactCodes` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=TestExamSeedIT,TestExamSeedComplianceIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `TestExamSeedUpgradeIT#upgradesUneditedWave2BSeed`, `#keepsAdminEditedExam` (mới), tích hợp: nạp seed của `develop` `1774c74d` trước rồi chạy seed mới, `cd backend && ./mvnw -B -ntp verify -Dit.test=TestExamSeedUpgradeIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - `cd backend && ./mvnw -B -ntp verify -Dit.test=TestExamSeedIT,QuestionSeedIT,SampleQuestionCoverageIT,VactThptSampleDataIT,TestExamSeedComplianceIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `cd backend && ./mvnw -B -ntp verify`; `pnpm --filter @thi360/e2e test`
- Kiểm thử rủi ro:
  - biên: bài có phần `omitted`, nhóm có nhãn, chùm có `slots`, `SHARED` và `PER_PART`, năm ma trận 2025 và 2026;
  - sinh lại hai lần liên tiếp cho cùng kết quả;
  - dữ liệu đã có: DB mang seed 2B, một bài `TEST-*` admin đã sửa;
  - đỏ trước: AC-1, AC-2, AC-4, AC-6, AC-7;
  - chạy seed lần hai trên DB đã nâng giữ nguyên cấu trúc, tham chiếu và bài admin đã sửa (`TestExamSeedUpgradeIT#keepsAdminEditedExam` chạy seed hai lần). TS là task rủi ro cao vì đụng dữ liệu đã có (review vòng 5, P5-3).
- Dữ liệu test: migration đầy đủ của đợt 2B; câu trong ngân hàng do seed dev nạp; server thật trong Testcontainers.
- Không kiểm: nội dung chuyên môn của câu hỏi; tốc độ kiểm tra trên dữ liệu lớn.

## EX-1 · Module `exam`: thông tin bán, sự kiện, cổng đọc

**Spec:** TM 3, 6, E7, E24, E26, 10.1 (dữ liệu cần cho bảng chiếu), Q11, Q15; BE 5.2 (công bố); 2B 9; plan B5, B9; BS 2, 8, 17. **Cổng:** G0b. **Phụ thuộc mềm:** C-0b.

**Sản phẩm:**

- Bảng `exam.exam_listing` (đủ cột TM 6.1), `exam.exam_cover_asset` (`id`, `exam_id`, `object_key`, `content_type`, `size_bytes`, `width`, `height`, `created_at`, `created_by`, `orphaned_at`).
- API (TM 6.3):
  - `GET /api/v1/admin/exams/{id}/listing` (`EXAM.VIEW`): bản làm việc, `published` (bản của phiên bản mới nhất còn đường phục vụ, E24), `checkIssues` (kết quả kiểm tra thông tin bán), `minPriceVnd` (web không đọc được biến môi trường);
  - `PUT /api/v1/admin/exams/{id}/listing` (`EXAM.MODIFY`): có `expectedRevision`; nhận thêm `description` và `coverAssetId` vì hai giá trị này nằm ở bảng `exam` (TM 6.1);
  - `POST /api/v1/admin/exams/{id}/cover` (`EXAM.MODIFY`): `jpg`, `png`, `webp`, tối đa 2 MB, tối đa 4.096 × 4.096 px, cạnh ngắn từ 480 px (TM 6.2 và 6.3 cộng lại); nhận dạng theo chữ ký tệp; trả `{assetId, url}`. Lệnh này chỉ tạo ảnh, `PUT listing` mới gắn ảnh vào bài;
  - `GET /api/v1/admin/exams/{id}/cover/{assetId}` (`EXAM.VIEW`): đích của `url` để web admin xem ảnh bản nháp;
  - nhật ký `EXAM_LISTING_UPDATED`, `EXAM_COVER_UPLOADED`.
- Sửa thông tin bán tăng `revision`, đặt `has_unpublished_changes`, không vào `input_fingerprint`.
- Kiểm tra thông tin bán trong transaction công bố, đặt sau mọi kiểm tra sẵn có của `ExamPublishService.publishOnce` để các mã lỗi cũ giữ nguyên: `LISTING_PRICE_MISSING`, `LISTING_PRICE_ZERO`, `LISTING_PRICE_BELOW_MIN`, `LISTING_ATTEMPTS_MISSING`, `LISTING_DESCRIPTION_MISSING`. Thiếu ảnh chỉ là cảnh báo. Mức tối thiểu đọc từ `THI360_VNPAY_MIN_AMOUNT`, mặc định 5.000 (E26).
- Đổi kiểu kết quả kiểm tra: `GET /exams/{id}/check`, `POST /exams/{id}/check`, `GET /exams/{id}/check/runs/{runId}` trả thêm trường không bắt buộc `listingCheck` (nhóm "Thông tin bán", lỗi `LISTING_*`), tính lúc đọc. Không ghi vào `exam_check_run.result`, không đổi `status`, `issueCount`, `groups[]`, không làm cũ kết quả kiểm tra cấu hình. Mức `WARNING` đã có từ đợt 2B (2B 7.3).
- `exam_version.config.listing` chụp lúc công bố: đủ cột TM 6.1, cùng `name`, `description`, `coverAssetId`. `ExamPublishService` và `ExamVersionQueryService.currentConfig` dựng cấu hình giống nhau; phiên bản cũ thiếu `listing` được so sánh như chưa có trường này.
- **Quy tắc E24:** bài có lựa chọn sau khi bắt đầu (phần tự chọn sinh sau: phần 3 HSA, QDA FULL) thì công bố phải gồm mọi đường đang phục vụ. Thiếu thì 422 `PUBLISH_ALL_PATHS_REQUIRED`. Định hướng Tin học chọn trước khi bắt đầu (2B K2), không thuộc quy tắc này. Cách nhận diện và tập đường bắt buộc: xem Câu hỏi mở 1.
- Sự kiện `ExamPublished`, `ExamDiscontinued` qua outbox, ghi trong transaction của lệnh, relay xử lý sau commit. Thân sự kiện chỉ có `examId` và `versionNo`; bên nhận đọc lại qua cổng. Tên topic là hằng số trong `exam/spi/ExamEventTopics` (`exam.published`, `exam.discontinued`).
- `exam/spi/PublishedExamPort`:
  - `findServing(examId)`: gồm `parts[].paths[]` (số câu, điểm tối đa, thời lượng của từng đường), `config.combinations`, `config.omittedGroups` (2B 9), thông tin bán, `buildMode`, `timing`, `durationMinutes`, `practiceScoring`, `referenceMax`, đủ cho thẻ, chi tiết, màn chuẩn bị và bộ đề (CM-6);
  - `loadVersion(examId, versionNo)`;
  - `listPublishedExamIds()`: cho lệnh dựng lại bảng chiếu của CM-1;
  - `openCover(assetId)`.

  Kết quả không chứa câu hỏi, đáp án, nguồn, đề chứng minh. Bài `DISCONTINUED` vẫn đọc được phiên bản phục vụ cuối cùng. Bài ôn (`build_mode = REVIEW`) trả `buildMode` để bên đọc gắn nhãn (BS 2).
- Job worker chạy mỗi ngày dọn ảnh bìa không còn là bìa của bản làm việc hay của phiên bản nào sau 7 ngày (TM 6.2, B5). Lần quét đầu đặt `orphaned_at`, ảnh được dùng lại thì xóa dấu này, xóa object sau khi commit.
- Thay 9 chỗ gọi `Instant.now()` trong module `exam` bằng bean `Clock`: `ExamDiscontinueService:56`, `ExamKindService:116`, `ExamPublishService:162`, `ExamService:443, 598, 736, 768, 885, 935` (B5).
- Sao chép bài chép cả thông tin bán.
- Sửa BE spec mục 5.2 (quy tắc E24, kiểm tra thông tin bán).
- `PublishedExamPort.findServing` trả thêm `kindCode`, `parts[].displaySegments`, `parts[].sections[].audioRequirement` từ bản chụp phiên bản đã công bố (BS 4, 17), để `attempt` không đọc mẫu hiện hành.
- Quy tắc E24 theo BS 8: áp cả lần công bố đầu cho bài có phần `CHOICE` hoặc `codeSource`; tập đường bắt buộc là các đường `VALID` của lần kiểm tra mới nhất cộng các đường đang phục vụ; đường chưa đủ điều kiện được để lại.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/exam/api/ExamListing*.java`, `ExamCoverController.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/exam/application/ExamListing*.java`, `ExamCover*.java`, `ExamEventOutbox.java`, `PublishedExamReader.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/exam/domain/ExamListing.java`, `ExamCoverAsset.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/exam/infrastructure/ExamCoverCleanupJob.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/exam/spi/PublishedExamPort.java`, `ExamEventTopics.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/exam/application/ExamPublishService.java`, `ExamDiscontinueService.java`, `ExamService.java`, `ExamKindService.java`, `ExamVersionQueryService.java`
- `backend/src/main/java/vn/unicom/thi360/exam/api/ExamCheckController.java`, `ExamCheckApiModels.java`, `ExamErrorCodes.java`
- `backend/src/main/java/vn/unicom/thi360/exam/domain/Exam.java`
- `backend/src/main/resources/db/migration/exam/V<yyyyMMddHHmm>__exam_listing_cover.sql` (mới)
- `backend/src/test/java/vn/unicom/thi360/exam/**`: test mới của task và phần dựng dữ liệu của các test công bố ở dòng ảnh hưởng đầu tiên
- `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts` (sinh lại, B9)
- `docs/specs/2026-11-16-be-pbi-03-04.md` (mục 5.2)
- thêm vào: `backend/src/main/resources/application.yml` (`thi360.exam.listing.min-price-vnd`, `thi360.exam.cover-cleanup.*`)
- thêm vào: `.env.example` (`THI360_VNPAY_MIN_AMOUNT`)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã `LISTING_*`, `EXAM_LISTING_INVALID`, `PUBLISH_ALL_PATHS_REQUIRED`)

**Ngoài phạm vi:** tab Thông tin bán, nhóm "Thông tin bán" ở tab Kiểm tra, câu chữ lỗi công bố trên web (FA-1); bảng chiếu, bộ nhận sự kiện, API công khai và ảnh bìa công khai (CM-1); giá khuyến mãi (CM-2); bộ đề (CM-6); cổng sinh đề và cổng đọc đề sinh thử (EX-2); bộ dữ liệu bài đã công bố (DS); đổi giờ của `questionbank` (B5).

**Nghiệm thu:**

- AC-1: `GET listing` trả bản làm việc, `published`, `checkIssues`, `minPriceVnd`; `PUT listing` lưu đủ cột TM 6.1, `description`, `coverAssetId` và ghi nhật ký `EXAM_LISTING_UPDATED` — bằng chứng: `ExamListingApiIT`, ca `savesAndReadsEveryListingField`
- AC-2: `PUT listing` sai kiểu hoặc quá giới hạn (`summary` 301 ký tự, `attempts_granted` 0 và 101, giá âm, `result_view` lạ) trả 422 `EXAM_LISTING_INVALID` kèm `fieldErrors`; bản nháp thiếu giá vẫn lưu được — bằng chứng: `ExamListingApiIT`, ca `rejectsWrongTypesButKeepsIncompleteDrafts`
- AC-3: `PUT listing` lệch `expectedRevision` trả 412 `EXAM_REVISION_CONFLICT`, dữ liệu không đổi — bằng chứng: `ExamListingApiIT`, ca `staleRevisionIsRejected`
- AC-4: Không đăng nhập trả 401; thiếu `EXAM.VIEW` ở hai API đọc, thiếu `EXAM.MODIFY` ở `PUT listing` và `POST cover` trả 403 — bằng chứng: `ExamListingApiIT`, ca `eachEndpointRequiresItsOwnPermission`; `AdminApiForbiddenMatrixIT`
- AC-5: Sửa thông tin bán tăng `revision` và đặt `has_unpublished_changes` — bằng chứng: `ExamListingApiIT`, ca `editMarksUnpublishedChanges`
- AC-6: Chỉ sửa thông tin bán thì không phải kiểm tra lại cấu hình: `GET check` có `stale = false`, công bố thành công mà không chạy lại kiểm tra — bằng chứng: `ExamListingPublishIT`, ca `listingOnlyEditKeepsConfigCheckValid`
- AC-7: Xóa giá sau khi kiểm tra cấu hình đạt thì công bố bị chặn (`LISTING_PRICE_MISSING`); không có dòng `exam_version` mới, không có sự kiện — bằng chứng: `ExamListingPublishIT`, ca `removedPriceBlocksPublish`
- AC-8: Công bố bị chặn với `LISTING_PRICE_ZERO`, `LISTING_ATTEMPTS_MISSING`, `LISTING_DESCRIPTION_MISSING`; thiếu ảnh bìa chỉ cảnh báo, công bố vẫn thành công — bằng chứng: `ExamListingPublishIT`, ca `eachListingRuleBlocksPublish`, `missingCoverOnlyWarns`
- AC-9: Giá 4.999 đồng bị chặn với `LISTING_PRICE_BELOW_MIN`, giá 5.000 đồng qua; đổi mức tối thiểu bằng cấu hình thì ngưỡng đổi theo — bằng chứng: `ExamListingCheckTest`, ca `minimumPriceBoundary`
- AC-10: Bài có thông tin bán trống vẫn nhận đúng mã lỗi cũ khi đường không hợp lệ (`PATH_NOT_VALID`) hoặc chưa kiểm tra (`CHECK_REQUIRED`) — bằng chứng: `ExamListingPublishIT`, ca `existingRefusalsKeepTheirCodes`
- AC-11: Cả ba API kiểm tra trả `listingCheck`; `exam_check_run.result`, `status`, `issueCount`, `groups[]` không đổi khi thông tin bán đổi — bằng chứng: `ExamListingCheckIT`, ca `listingCheckIsComputedOnReadOnEveryRoute`
- AC-12: Bài chưa kiểm tra lần nào thì `GET check` vẫn trả rỗng như cũ, `GET listing` vẫn có `checkIssues` — bằng chứng: `ExamListingCheckIT`, ca `uncheckedExamStillReportsListingIssues`
- AC-13: `exam_version.config.listing` chụp đủ trường; sửa thông tin bán sau đó không đổi bản đã chụp; `published` lấy từ phiên bản mới nhất còn đường phục vụ — bằng chứng: `ExamListingPublishIT`, ca `publishSnapshotsListingImmutably`
- AC-14: Công bố HSA FULL chỉ một đường thì 422 `PUBLISH_ALL_PATHS_REQUIRED` — bằng chứng: `ExamPublishAllPathsIT`, ca `hsaFullSinglePathIsRejected`
- AC-15: Công bố QDA FULL thiếu một mã thì 422 `PUBLISH_ALL_PATHS_REQUIRED` — bằng chứng: `ExamPublishAllPathsIT`, ca `qdaFullMissingCodeIsRejected`
- AC-16: HSA FULL, QDA FULL công bố đủ đường thì mọi đường cùng một `version_no`, `kept` rỗng — bằng chứng: `ExamPublishAllPathsIT`, ca `allPathsPublishTogether`
- AC-17: BCA FULL và Tin học SPT công bố một đường vẫn thành công — bằng chứng: `ExamPublishAllPathsIT`, ca `preStartChoicesMayPublishOnePath`
- AC-18: `ExamPublished` phát đúng một lần sau commit, thân chỉ có `examId`, `versionNo`; gửi lại cùng `Idempotency-Key` không thêm sự kiện — bằng chứng: `ExamEventsOutboxIT`, ca `publishEmitsOneEvent`, `replayEmitsNoEvent`
- AC-19: Công bố bị rollback thì không phát sự kiện — bằng chứng: `ExamEventsOutboxIT`, ca `rollbackEmitsNoEvent`
- AC-20: `ExamDiscontinued` phát đúng một lần khi ngừng bán, thân chỉ có `examId`, `versionNo`; gửi lại cùng khóa không thêm sự kiện — bằng chứng: `ExamEventsOutboxIT`, ca `discontinueEmitsOneEvent`
- AC-21: `findServing` trả `parts[].paths[]`, `config.combinations`, `config.omittedGroups`, thông tin bán, `timing`, `durationMinutes`, `practiceScoring`, `referenceMax`; bài ôn trả `buildMode = REVIEW` — bằng chứng: `PublishedExamPortIT`, ca `findServingCarriesSaleAndPathData`
- AC-22: `loadVersion` trả đúng bản chụp của phiên bản đã nêu; phiên bản không có trả rỗng — bằng chứng: `PublishedExamPortIT`, ca `loadVersionReadsImmutableSnapshot`
- AC-23: `listPublishedExamIds` gồm bài đang bán và bài đã ngừng bán, không gồm bài nháp — bằng chứng: `PublishedExamPortIT`, ca `listsEveryPublishedExam`
- AC-24: Bài `DISCONTINUED` vẫn đọc được phiên bản phục vụ cuối cùng — bằng chứng: `PublishedExamPortIT`, ca `discontinuedExamStaysReadable`
- AC-25: `ForbiddenKeysAssert` đạt trên kết quả của `findServing` và `loadVersion` — bằng chứng: `PublishedExamPortIT`, ca `portResultsContainNoForbiddenKeys`
- AC-26: `openCover` trả nội dung và kiểu ảnh; mã không có trả rỗng — bằng chứng: `PublishedExamPortIT`, ca `openCoverReturnsStoredImage`
- AC-27: `POST cover` nhận `jpg`, `png`, `webp` hợp lệ, trả `{assetId, url}`, `url` mở được, ghi nhật ký `EXAM_COVER_UPLOADED` — bằng chứng: `ExamCoverApiIT`, ca `acceptsEachSupportedFormat`
- AC-28: `POST cover` trả 422 `EXAM_LISTING_INVALID` và không lưu object khi tệp sai chữ ký (kể cả đổi đuôi), lớn hơn 2 MB một byte, rộng 4.097 px, cạnh ngắn 479 px — bằng chứng: `ExamCoverApiIT`, ca `rejectsInvalidImages`
- AC-29: `PUT listing` gắn `coverAssetId` của bài khác trả 422 `EXAM_LISTING_INVALID` — bằng chứng: `ExamCoverApiIT`, ca `coverOfAnotherExamIsRejected`
- AC-30: Ảnh hết là bìa đủ 7 ngày thì bị xóa cả dòng và object; ảnh mới hết là bìa 6 ngày còn nguyên — bằng chứng: `ExamCoverCleanupJobIT`, ca `removesOnlyAfterSevenDays`
- AC-31: Ảnh đang là bìa của bản làm việc, của một phiên bản cũ, hoặc của bản sao không bị xóa — bằng chứng: `ExamCoverCleanupJobIT`, ca `keepsEveryReferencedCover`
- AC-32: Hai lần dọn chạy đồng thời không xóa trùng và không lỗi — bằng chứng: `ExamCoverCleanupJobIT`, ca `concurrentRunsDeleteOnce`
- AC-33: Sao chép bài chép cả thông tin bán và ảnh bìa; bản sao chưa có `published` — bằng chứng: `ExamDuplicateListingIT`, ca `duplicateCopiesListing`
- AC-34: Module `exam` không còn gọi `Instant.now()` không có `Clock` — bằng chứng: `ExamClockUsageTest`, ca `examModuleReadsTimeFromClock`
- AC-35: `published_at` và `discontinued_at` lấy đúng giờ của bean `Clock` — bằng chứng: `ExamClockIT`, ca `publishAndDiscontinueFollowClock`
- AC-36: Phiên bản cũ thiếu `config.listing` vẫn đọc được qua cổng và không bị báo đổi chỉ vì thiếu trường đó — bằng chứng: `ExamLegacyListingIT`, ca `versionsWithoutListingStayReadable`
- AC-37: BE spec mục 5.2 ghi quy tắc E24 và kiểm tra thông tin bán — bằng chứng: `git grep -n -E "PUBLISH_ALL_PATHS_REQUIRED|LISTING_PRICE_MISSING" -- docs/specs/2026-11-16-be-pbi-03-04.md`
- AC-38: `findServing` của bài H-SCA, V-SAT, THPTQG trả đúng `kindCode`; `displaySegments`, `audioRequirement` lấy từ bản chụp, đổi mẫu sau khi công bố không làm đổi kết quả — bằng chứng: `PublishedExamPortIT#servingCarriesKindSegmentsAndAudioFromSnapshot` (mới)
- AC-39: Lần công bố đầu của bài có lựa chọn sau khi bắt đầu: thiếu một đường `VALID` thì bị chặn; đường không `VALID` được để lại và công bố vẫn qua — bằng chứng: `ExamPublishE24IT#firstPublishNeedsEveryValidPath`, `#ineligiblePathMayStayOut` (mới)

**Ảnh hưởng ngoài task:**

- Các test đang công bố bài không có thông tin bán sẽ nhận `LISTING_*` (`ExamBcaTemplateCheckIT`, `ExamCheckUnitReuseIT`, `ExamPublishConcurrencyIT`, `ExamPublishIdempotencyIT`, `ExamPublishedTotalsIT`, `ExamQuestionUsageIT`, `ExamTemplateFlagsCheckIT`, `ExamTemplatePolicyValidityIT`) → thêm lớp dựng `ExamListingFixture` (mới) vào phần chuẩn bị của từng lớp; cả 8 lớp xanh [chủ: task này]
- Test mong công bố bị từ chối vì đường không hợp lệ (`ExamCheckRealTemplatesIT.publishRefused`) → chạy lại lớp này; vẫn 422 với mã cũ [chủ: task này]
- Test công bố từng mã riêng của bài `FULL` `SHARED` (`ExamCheckUnitReuseIT`, `codes:QD06`, `codes:QD05`) → chạy lại; không bị quy tắc E24 chặn [chủ: task này]
- So sánh cấu hình bản làm việc với phiên bản (`ExamPublishService` `matchingVersions`, `ExamVersionQueryService.currentConfig`) → `ExamVersionLifecycleIT`, `ExamLegacyListingIT`; đường đã công bố không bị báo chưa công bố sai [chủ: task này]
- Danh sách thay đổi giữa hai phiên bản liệt kê mọi trường của `config` (`ExamVersionChanges.between`) → thêm ca `listingChangeIsReported` vào `ExamVersionChangesTest` [chủ: task này]
- Nhãn trường `config/listing/*` trên màn phiên bản (`apps/web-admin/src/features/exams/components/exam-review-shared.tsx`, `useChangeWhere`) → thêm khóa `exams.versions.changes.where.field.*`; thay đổi giá hiện nhãn dễ đọc [chủ: FA-1]
- Bản chụp phiên bản (`ExamVersionSnapshot`) → `ExamVersionSnapshotTest` xanh, không sửa lớp này [chủ: task này]
- Tám nhóm kiểm tra cấu hình giữ nguyên (`ExamCheckIssues.GROUPS`, `ExamCheckAssembler`) → `ExamCheckerTests` vẫn khẳng định 8 nhóm [chủ: task này]
- Web đổi nhóm lạ thành `INFO` (`apps/web-admin/src/features/exams/review-api.ts:434`, `:568`), nên `listingCheck` không đi vào `groups[]` → `review-api.test.ts`, `review-findings.test.tsx`, `review-real-views.test.tsx` xanh [chủ: task này]
- Hiển thị nhóm "Thông tin bán" đọc từ `listingCheck` và `GET listing` (`apps/web-admin/src/features/exams/components/exam-review-check.tsx`, `review-types.ts`) → `listing-check-group.test.tsx` của FA-1 [chủ: FA-1]
- Hộp thoại công bố hiện mọi lỗi 422 khác thành `errors.PATH_NOT_VALID` (`apps/web-admin/src/features/exams/components/publish-exam-dialog.tsx:224`) → FA-1 ánh xạ `PUBLISH_ALL_PATHS_REQUIRED`, `LISTING_*`; kịch bản công bố thiếu giá hiện đúng lỗi [chủ: FA-1]
- Giới hạn ảnh bìa trên web là 5 MB, lệch TM 2 MB (`apps/web-admin/src/features/exams/create-exam-schema.ts:43`, `COVER_IMAGE_MAX_BYTES`) → FA-1 đổi còn 2 MB; `listing-cover.test.tsx` [chủ: FA-1]
- Client sinh từ OpenAPI (`packages/api-client/openapi.json`, `src/schema.d.ts`, `apps/web-admin/src/features/exams/__tests__/fake-exams-backend.ts` `WireCheck`) → `OpenApiExportIT`, bước `diff` ở `.github/workflows/backend.yml:28`, `pnpm typecheck`; `listingCheck` không bắt buộc nên fixture cũ vẫn hợp lệ [chủ: task này]
- Ma trận quyền tự dò endpoint (`AdminApiForbiddenMatrixIT`) → chạy lại; cả bốn endpoint mới đạt 401, 403 [chủ: task này]
- Tệp thông điệp ba ngôn ngữ (`messages*.properties`) → `MessageBundlesTest` xanh [chủ: task này]
- `OutboxRelay` chỉ giữ một handler mỗi topic và đánh hỏng vĩnh viễn sự kiện chưa có handler (`backend/src/main/java/vn/unicom/thi360/shared/outbox/OutboxRelay.java`) → CM-1 đăng ký bộ nhận và gọi `rebuild` sau khi merge; `StorefrontProjectionIT` [chủ: CM-1]
- CM-6 cần nghe cùng hai topic (`ExamEventTopics`) → mở rộng bộ nhận của CM-1, không `register` lại topic; test bộ đề của CM-6 [chủ: CM-6]
- Test đọc bảng outbox dùng chung (`AuditIntegrationTests`, `CatalogBasicApiIT`, `ExamApiIT`, `ContentReferenceIT`) → chạy lại; dòng topic mới không làm lệch số đếm [chủ: task này]
- Sao chép bài (`ExamService.doDuplicate`, `Exam.copyDetailsFrom`) → `ExamCreateIT`, `ExamIdempotencyIT`, `ExamOverviewIT`, `ExamStructureIT`, `ExamDuplicateListingIT` [chủ: task này]
- Đổi hàm dựng của service để nhận `Clock` (`ExamService`, `ExamPublishService`, `ExamDiscontinueService`, `ExamKindService`) → không có: đã tìm `new ExamService(`, `new ExamPublishService(`, `new ExamDiscontinueService(`, `new ExamKindService(` trong `backend/src`; `ExamClockIT` xanh [chủ: task này]
- Đồng hồ thử của profile `e2e` thay bean `Clock` (`backend/src/main/java/vn/unicom/thi360/shared/time/ClockConfig.java`, `MutableClock`) → `ExamClockIT` chứng minh module đọc giờ qua bean; `POST /api/v1/test/clock` do C-0b kiểm [chủ: C-0b]
- Ranh giới module (`backend/src/main/java/vn/unicom/thi360/exam/spi/package-info.java`, `ModularityTest`) → `ModularityTest` xanh; kiểu trong `exam/spi` không tham chiếu `exam.api`, `exam.application` [chủ: task này]
- Cấu hình dùng chung mức giá tối thiểu (`THI360_VNPAY_MIN_AMOUNT`) → CM-2, CM-3b đọc cùng biến, cùng mặc định 5.000; ca `minimumPriceBoundary` [chủ: task này]
- Job mới ở profile worker (`thi360.exam.scheduling.enabled`, tắt ở `backend/src/main/resources/application.yml` khối `test`) → `ExamCoverCleanupJobIT` gọi thẳng `run`; `Thi360ApplicationTests` khởi động được [chủ: task này]
- Lưu ảnh qua kho đối tượng (`backend/src/main/java/vn/unicom/thi360/shared/storage/ObjectStorage.java`, `thi360.s3.mode: memory` ở `backend/src/test/resources/application-test.yml`) → `ExamCoverApiIT`; `shared/**` không sửa (B9) [chủ: task này]
- Dữ liệu mẫu (`backend/src/main/resources/db/devdata/R__seed_*.sql`) → không có: đã tìm `exam_version` trong `backend/src/main/resources/db/devdata`; `TestExamSeedIT` xanh [chủ: task này]
- Hợp đồng `PublishedExamPort` cho bên dùng → `PublishedExamPortIT`; CM-1, CM-4b, CM-6, AT-1 kiểm phần của mình [chủ: CM-1, CM-4b, CM-6, AT-1]
- Bộ dựng dữ liệu phải điền thông tin bán trước khi công bố → test của DS [chủ: DS]

**Kế hoạch kiểm thử:**

- AC-1: `ExamListingApiIT#savesAndReadsEveryListingField`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamListingApiIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `ExamListingApiIT#rejectsWrongTypesButKeepsIncompleteDrafts`, tích hợp, cùng lệnh AC-1
- AC-3: `ExamListingApiIT#staleRevisionIsRejected`, tích hợp, cùng lệnh AC-1
- AC-4: `ExamListingApiIT#eachEndpointRequiresItsOwnPermission`, `AdminApiForbiddenMatrixIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test='ExamListingApiIT,AdminApiForbiddenMatrixIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `ExamListingApiIT#editMarksUnpublishedChanges`, tích hợp, cùng lệnh AC-1
- AC-6: `ExamListingPublishIT#listingOnlyEditKeepsConfigCheckValid`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamListingPublishIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `ExamListingPublishIT#removedPriceBlocksPublish`, tích hợp, cùng lệnh AC-6
- AC-8: `ExamListingPublishIT#eachListingRuleBlocksPublish`, `#missingCoverOnlyWarns`, tích hợp, cùng lệnh AC-6
- AC-9: `ExamListingCheckTest#minimumPriceBoundary`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=ExamListingCheckTest`
- AC-10: `ExamListingPublishIT#existingRefusalsKeepTheirCodes`, tích hợp, cùng lệnh AC-6
- AC-11: `ExamListingCheckIT#listingCheckIsComputedOnReadOnEveryRoute`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamListingCheckIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: `ExamListingCheckIT#uncheckedExamStillReportsListingIssues`, tích hợp, cùng lệnh AC-11
- AC-13: `ExamListingPublishIT#publishSnapshotsListingImmutably`, tích hợp, cùng lệnh AC-6
- AC-14: `ExamPublishAllPathsIT#hsaFullSinglePathIsRejected`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamPublishAllPathsIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `ExamPublishAllPathsIT#qdaFullMissingCodeIsRejected`, tích hợp, cùng lệnh AC-14
- AC-16: `ExamPublishAllPathsIT#allPathsPublishTogether`, tích hợp, cùng lệnh AC-14
- AC-17: `ExamPublishAllPathsIT#preStartChoicesMayPublishOnePath`, tích hợp, cùng lệnh AC-14
- AC-18: `ExamEventsOutboxIT#publishEmitsOneEvent`, `#replayEmitsNoEvent`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamEventsOutboxIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-19: `ExamEventsOutboxIT#rollbackEmitsNoEvent`, tích hợp, cùng lệnh AC-18
- AC-20: `ExamEventsOutboxIT#discontinueEmitsOneEvent`, tích hợp, cùng lệnh AC-18
- AC-21: `PublishedExamPortIT#findServingCarriesSaleAndPathData`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamPortIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-22: `PublishedExamPortIT#loadVersionReadsImmutableSnapshot`, tích hợp, cùng lệnh AC-21
- AC-23: `PublishedExamPortIT#listsEveryPublishedExam`, tích hợp, cùng lệnh AC-21
- AC-24: `PublishedExamPortIT#discontinuedExamStaysReadable`, tích hợp, cùng lệnh AC-21
- AC-25: `PublishedExamPortIT#portResultsContainNoForbiddenKeys`, hợp đồng, cùng lệnh AC-21
- AC-26: `PublishedExamPortIT#openCoverReturnsStoredImage`, tích hợp, cùng lệnh AC-21
- AC-27: `ExamCoverApiIT#acceptsEachSupportedFormat`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamCoverApiIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-28: `ExamCoverApiIT#rejectsInvalidImages`, tích hợp, cùng lệnh AC-27
- AC-29: `ExamCoverApiIT#coverOfAnotherExamIsRejected`, tích hợp, cùng lệnh AC-27
- AC-30: `ExamCoverCleanupJobIT#removesOnlyAfterSevenDays`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamCoverCleanupJobIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-31: `ExamCoverCleanupJobIT#keepsEveryReferencedCover`, tích hợp, cùng lệnh AC-30
- AC-32: `ExamCoverCleanupJobIT#concurrentRunsDeleteOnce`, tích hợp, cùng lệnh AC-30
- AC-33: `ExamDuplicateListingIT#duplicateCopiesListing`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamDuplicateListingIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-34: `ExamClockUsageTest#examModuleReadsTimeFromClock` (ArchUnit trên lớp đã biên dịch), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ExamClockUsageTest`
- AC-35: `ExamClockIT#publishAndDiscontinueFollowClock`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamClockIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-36: `ExamLegacyListingIT#versionsWithoutListingStayReadable`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamLegacyListingIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-37: kiểm tài liệu, hợp đồng, `git grep -n -E "PUBLISH_ALL_PATHS_REQUIRED|LISTING_PRICE_MISSING" -- docs/specs/2026-11-16-be-pbi-03-04.md`
- AC-38: `PublishedExamPortIT#servingCarriesKindSegmentsAndAudioFromSnapshot` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamPortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-39: `ExamPublishE24IT#firstPublishNeedsEveryValidPath`, `#ineligiblePathMayStayOut` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamPublishE24IT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy:
  - Test công bố và kiểm tra: `cd backend && ./mvnw -B -ntp verify -Dit.test='ExamBcaTemplateCheckIT,ExamCheckRealTemplatesIT,ExamCheckUnitReuseIT,ExamPublishConcurrencyIT,ExamPublishIdempotencyIT,ExamPublishedTotalsIT,ExamQuestionUsageIT,ExamTemplateFlagsCheckIT,ExamTemplatePolicyValidityIT,ExamVersionLifecycleIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
  - Sao chép, outbox, dữ liệu mẫu: `cd backend && ./mvnw -B -ntp verify -Dit.test='ExamCreateIT,ExamIdempotencyIT,ExamOverviewIT,ExamStructureIT,CatalogBasicApiIT,ExamApiIT,ContentReferenceIT,TestExamSeedIT,OpenApiExportIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
  - Lớp chạy bằng surefire: `cd backend && ./mvnw -B -ntp test -Dtest='AuditIntegrationTests,ExamVersionChangesTest,ExamVersionSnapshotTest,ExamCheckerTests,MessageBundlesTest,ModularityTest,Thi360ApplicationTests'`
  - Web và client: `pnpm --filter @thi360/api-client generate`, `pnpm typecheck`, `pnpm --filter @thi360/web-admin test -- review-api.test.ts review-findings.test.tsx review-real-views.test.tsx deep-check.test.tsx`
  - Toàn bộ backend trước khi nộp: `cd backend && ./mvnw -B -ntp verify`
- Kiểm thử rủi ro:
  - Quyền: từng endpoint với đúng một quyền cần có và với mọi quyền trừ quyền đó (AC-4); `AdminApiForbiddenMatrixIT` thêm tài khoản học viên và không đăng nhập.
  - Gửi lặp, đồng thời: gửi lại công bố và ngừng bán cùng `Idempotency-Key` không thêm phiên bản, sự kiện (AC-18, AC-20); hai `PUT listing` cùng `expectedRevision` thì một lệnh 412 (AC-3); sửa giá đồng thời với công bố (`ExamListingPublishIT`, ca `priceEditDuringPublishSerializes`, khóa dòng `exam`): không có phiên bản nào chụp giá đã bị xóa; hai lần dọn ảnh đồng thời (AC-32).
  - Migration trên DB đã có dữ liệu: `ExamLegacyListingIT` dựng bài và phiên bản theo hình dạng trước EX-1 rồi đọc qua API và cổng (AC-36); bài cũ chưa có dòng `exam_listing` trả bản làm việc rỗng và bị chặn công bố bằng `LISTING_PRICE_MISSING`.
  - Giá trị biên: giá 0, 4.999, 5.000; `attempts_granted` 0, 1, 100, 101; `summary` 300 và 301 ký tự; ảnh 2 MB và 2 MB + 1 byte, 4.096 và 4.097 px, cạnh ngắn 479 và 480 px; ảnh hết là bìa đúng 7 ngày.
  - Đỏ trước: AC-7, AC-10, AC-11, AC-14, AC-15, AC-18, AC-19, AC-20, AC-34.
- Dữ liệu test: bài tạo qua API từ mẫu thật (`HSA_FULL`, `QDA_FULL`, `BCA_FULL`, `SPT_INFORMATICS_2025`) hoặc fixture SQL như các test công bố hiện có; thông tin bán do `ExamListingFixture` (mới) điền; ảnh sinh trong test bằng `ImageIO` (`jpg`, `png`) và tệp `webp` nhỏ trong `backend/src/test/resources/exam/`; kho đối tượng chế độ `memory`. Mỗi test dùng mã bài ngẫu nhiên theo UUID; mỗi lane có Testcontainers riêng.
- Không kiểm:
  - Sửa thông tin bán đúng lúc một lần kiểm tra cấu hình đang chạy làm kết quả lần đó bị bỏ, vì `ExamCheckResultStore.save` so `revision`; người dùng bấm kiểm tra lại, task này không đổi cơ chế đó.
  - Bucket riêng `thi360-covers` trên S3 thật: xem Câu hỏi mở 2.
  - Hiển thị trên web (FA-1), bảng chiếu và ảnh công khai (CM-1).
  - `questionbank` còn 12 chỗ gọi `Instant.now()`: đồng hồ thử không tác động tới giờ ở module đó (B5).

**Câu hỏi mở:** M.Trường chốt nơi lưu ảnh bìa. TM 6.2 ghi bucket riêng `thi360-covers`, nhưng `S3Properties` chỉ có một bucket và `shared/**` thuộc C-0 (B9). Cách tạm: lưu trong bucket riêng tư hiện có, khóa có tiền tố `exam-covers/`.

## EX-2 · Module `exam`: cổng sinh đề

**Spec:** LT 3, F4, F5, F22; BE 5.4; 2B 6.3, 8, 9, 11, H17; ADD H28, H29; plan B2, B9. **Cổng:** G0b.

Đợt 2B đã làm thuật toán (17 − 1, chỉ tiêu chủ đề, ghép môn theo mã), `solvePick` loại câu, `PaperOrdering` và cảnh báo `MANUAL_GRADING_REQUIRED`. EX-2 chỉ mở cổng cho module `attempt`.

**Sản phẩm:**

- `exam/spi/PaperSolverPort.generate(examId, versionNo, partCodes, selections, seed, excludeQuestionIds)` (LT 3):
  - dựng bài toán từ **bản chụp phiên bản**: `parts`, `subjectPool`, `config.combinations` cho phần có mã, `config.examQuotas`;
  - mang đủ từ bản chụp vào sinh đề: `everyChildInScope`, chùm cỡ linh hoạt, `TrimPolicy`;
  - dựng ứng viên qua `QuestionSourcePort.findCandidates`;
  - gọi `solvePartExcluding`, hoặc `solvePick` cho HSA Khoa học (có bỏ câu theo `TrimPolicy`).

  Trả `(questionId, versionNo)` theo môn, nhóm, chùm, kèm câu đã bỏ. Không xếp thứ tự: AT-1 gọi `PaperOrdering`. Không ghi sổ dùng câu: AT-1 gọi `recordUsage`.
- `selections` nhận cả định hướng Tin học như một phương án `BRANCH` thường (2B K2). Không có `generateBranches`.
- Lỗi của cổng là `PaperGenerationException` trong `exam/spi`, có lý do `GENERATION_FAILED` hoặc `GENERATION_TIMEOUT` để AT-1 ghi `abort_reason`.
- `SolverLimits` riêng cho sinh đề, hạn 30 giây (LT F5), cấu hình ở `thi360.exam.solver.generate.*`. Giá trị này không đăng ký thành bean kiểu `SolverLimits`, vì bảy lớp của kiểm tra và xem trước đang nhận bean đó theo kiểu.
- `exam/spi/PreviewPaperPort.load(examId, previewId)` (quyết định D-b): trả đề sinh thử đã xếp thứ tự gồm phần, khối, câu, chùm, `questionId`, `versionNo`, số thứ tự, điểm, `optionOrder`, bằng record riêng trong `exam/spi`, không dùng kiểu của `exam.api`.
  - Đề sinh thử không nằm trong DB. `PreviewSnapshotStore` lưu ở Redis, khóa `exam:preview:<previewId>`, sống 30 phút, chỉ lưu khi sinh thử có `status = GENERATED`. Bản lưu đã có đề sau khi xếp (`presentation`), phiên bản câu và `optionOrder`, nên cổng chỉ đọc lại, không sinh lại.
  - `previewId` hết hạn, không có, hoặc thuộc bài khác đều trả lỗi `PREVIEW_EXPIRED` (410), giống `POST /exams/{id}/preview` hiện nay.
- Test `perf`: HSA FULL phần Toán và phần Văn với 2.000 ứng viên mỗi khối; ghi thời gian vào báo cáo lane.
- Sửa BE spec mục 5.4 (cổng mới).
- `PreviewPaperPort.extendForTrial(examId, previewId)`: đặt hạn đề sinh thử thành `max(hạn hiện tại, bây giờ + thời lượng bài + 30 phút)` (BS 9). Đề chưa bấm Làm thử vẫn giữ 30 phút.
- Bản chụp đề sinh thử (`PreviewSnapshotStore.Snapshot`) lưu thêm `kindCode`, `timing`, `durationMinutes` lúc sinh: `SHARED` lấy thời lượng chung của bài, `PER_PART` cộng các phần của đường đã sinh. Bản chụp cũ thiếu các trường này coi như hết hạn.
- Kết quả `PreviewPaperPort.load` trả thêm `kindCode`, `durationMinutes`, tệp đính kèm của câu đơn và câu gốc của chùm (`attachments`), `displaySegments` của phần, lấy từ đề đã sinh, không đọc lại bản nháp hay mẫu.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/exam/spi/PaperSolverPort.java`, `PaperGenerationException.java`, `PreviewPaperPort.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/exam/application/PaperGeneration*.java`, `PreviewPaperReader.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/exam/infrastructure/ExamGenerationSolverProperties.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/exam/application/PreviewPathResolver.java`, `PreviewPathSolver.java`, `PreviewPublishedCombinations.java`, `SolverInputBuilder.java`, `ExamPreviewStore.java`: chỉ mở phần dùng lại được cho sinh đề, không đổi hành vi xem trước
- `backend/src/test/java/vn/unicom/thi360/exam/PaperGeneration*IT.java`, `PreviewPaperPortIT.java` (mới)
- `backend/src/test/java/vn/unicom/thi360/exam/application/PaperGeneration*Test.java` (mới)
- `docs/specs/2026-11-16-be-pbi-03-04.md` (mục 5.4)
- thêm vào: `backend/src/main/resources/application.yml` (`thi360.exam.solver.generate.*`)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/exam/application/PreviewSnapshotStore.java` (kéo dài hạn)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/exam/application/ExamPreviewService.java` (lưu trường mới vào bản chụp), `backend/src/test/java/vn/unicom/thi360/exam/**/PreviewSnapshotStoreTest.java`

**Ngoài phạm vi:** thuật toán `PaperSolver`, `TrimPolicy`, `PaperOrdering` (đợt 2B, B9 cấm sửa); xếp thứ tự, đảo phương án, lưu đề, ghi sổ dùng câu, chạy nền và ánh xạ `abort_reason` (AT-1); sinh phần 3 sau khi chọn (AT-2b gọi cùng cổng); API chấm đề làm thử và bộ chấm (AT-5, AT-4); màn làm thử (FA-4); `PublishedExamPort` (EX-1).

**Nghiệm thu:**

- AC-1: Cùng hạt giống, cùng dữ liệu thì ra cùng kết quả — bằng chứng: `PaperGenerationIT`, ca `sameSeedSameInputSamePaper`
- AC-2: HSA Khoa học, 3 môn tự nhiên: môn nhận 16 câu theo hạt giống, đúng 16 câu — bằng chứng: `PaperGenerationHsaIT`, ca `scienceSubjectReceivesSixteenBySeed`
- AC-3: HSA Khoa học trả câu đã bỏ, câu đó không nằm trong đề — bằng chứng: `PaperGenerationHsaIT`, ca `scienceReportsDroppedQuestion`
- AC-4: HSA FULL lúc bắt đầu với `partCodes` là Toán, Văn chỉ trả hai phần đó, chưa có phần 3 — bằng chứng: `PaperGenerationHsaIT`, ca `onlyRequestedPartsAreGenerated`
- AC-5: QDA QĐ02 từ bản chụp: Hóa trước, Sinh sau — bằng chứng: `PaperGenerationQdaIT`, ca `combinationOrderComesFromSnapshot`
- AC-6: Thêm mã vào Global List sau khi công bố không đổi kết quả của phiên bản cũ — bằng chứng: `PaperGenerationQdaIT`, ca `globalListChangeDoesNotAffectOldVersion`
- AC-7: Sửa cấu trúc bản làm việc sau khi công bố không đổi kết quả của phiên bản đã công bố — bằng chứng: `PaperGenerationIT`, ca `workingCopyEditDoesNotAffectVersion`
- AC-8: Danh sách câu loại có hiệu lực ở `solvePartExcluding` — bằng chứng: `PaperGenerationIT`, ca `excludedQuestionsAreNotPickedInPart`
- AC-9: Danh sách câu loại có hiệu lực ở `solvePick` — bằng chứng: `PaperGenerationHsaIT`, ca `excludedQuestionsAreNotPickedInScience`
- AC-10: Tin học SPT V1 (22 câu): mỗi định hướng sinh đủ câu của đường đó — bằng chứng: `PaperGenerationInformaticsIT`, ca `sptEachBranchGeneratesTwentyTwo`
- AC-11: Tin học THPTQG (28 câu): mỗi định hướng sinh đủ câu của đường đó — bằng chứng: `PaperGenerationInformaticsIT`, ca `thptqgEachBranchGeneratesTwentyEight`
- AC-12: V-ACT Toán sinh 12 câu đơn và 18 câu con, mỗi chùm được lấy nguyên — bằng chứng: `PaperGenerationVactIT`, ca `mathTakesWholeClusters`
- AC-13: Nhóm chùm cỡ linh hoạt trong bản chụp sinh đúng tổng số câu con, không tách chùm — bằng chứng: `PaperGenerationVactIT`, ca `flexibleClustersKeepTotalAndStayWhole`
- AC-14: Bài ôn có `everyChildInScope = true`: mọi câu con của chùm được chọn đều thuộc phạm vi kiến thức — bằng chứng: `PaperGenerationReviewIT`, ca `everyChildStaysInScope`
- AC-15: Không sinh được thì lỗi `GENERATION_FAILED`, không bao giờ trả đề thiếu — bằng chứng: `PaperGenerationIT`, ca `shortageFailsWithoutPartialPaper`
- AC-16: Vượt hạn thì lỗi `GENERATION_TIMEOUT` — bằng chứng: `PaperGenerationLimitsTest`, ca `deadlineMapsToGenerationTimeout`
- AC-17: Phiên bản không có, `partCodes` hoặc `selections` không thuộc bản chụp đều trả `GENERATION_FAILED` và không gọi bộ giải — bằng chứng: `PaperGenerationIT`, ca `unknownVersionPartOrSelectionIsRefused`
- AC-18: Hạn của sinh đề đọc từ `thi360.exam.solver.generate.*`; hạn của kiểm tra và xem trước không đổi — bằng chứng: `PaperGenerationLimitsTest`, ca `generationLimitsAreSeparate`
- AC-19: Gọi cổng không ghi `question_usage_ledger` và không đổi dữ liệu bài thi — bằng chứng: `PaperGenerationIT`, ca `generationWritesNothing`
- AC-20: `PreviewPaperPort.load` trả đúng thứ tự câu, phiên bản câu và `optionOrder` như kết quả của `POST /exams/{id}/preview` — bằng chứng: `PreviewPaperPortIT`, ca `loadReturnsTheOrderedPaper`
- AC-21: `previewId` hết hạn hoặc không có trả `PREVIEW_EXPIRED` — bằng chứng: `PreviewPaperPortIT`, ca `expiredPreviewIsGone`
- AC-22: `previewId` của bài khác trả `PREVIEW_EXPIRED` — bằng chứng: `PreviewPaperPortIT`, ca `previewOfAnotherExamIsGone`
- AC-23: Có số đo `perf` trong báo cáo. Vượt 30 giây thì báo Advisor (Phần E), không tự đổi hạn — bằng chứng: `PaperGenerationPerfIT`, báo cáo lane
- AC-24: BE spec mục 5.4 ghi `PaperSolverPort` và `PreviewPaperPort` — bằng chứng: `git grep -n -E "PaperSolverPort|PreviewPaperPort" -- docs/specs/2026-11-16-be-pbi-03-04.md`
- AC-25: `extendForTrial` kéo dài đúng hạn, gọi lại nhiều lần không rút ngắn hạn đã có; đề đã hết hạn trả `PREVIEW_EXPIRED`; đề không gọi hàm này vẫn hết hạn sau 30 phút — bằng chứng: `PreviewPaperPortIT#trialExtensionNeverShortens`, `#extendExpiredPreviewIsGone`, `#untouchedPreviewKeepsThirtyMinutes` (mới)
- AC-26: V-ACT FULL `SHARED` 150 phút có thời lượng phần trống: bấm Làm thử giữ đề ít nhất 180 phút; HSA `PER_PART` tính đúng đường đã sinh; sửa bản nháp sau khi sinh không đổi hạn — bằng chứng: `PreviewPaperPortIT#sharedTrialUsesExamDuration`, `#perPartTrialSumsGeneratedPath`, `#draftEditDoesNotChangeTrial` (mới)
- AC-27: `load` của đề H-SCA Tiếng Anh trả `kindCode`, tệp audio ở câu gốc của chùm; đề V-SAT Tiếng Anh trả `displaySegments`; đổi bản nháp hay mẫu sau khi sinh không đổi các trường này; `ForbiddenKeysAssert` đạt — bằng chứng: `PreviewPaperPortIT#loadCarriesKindAttachmentsAndSegments`, `#loadIgnoresLaterDraftEdits` (mới)
- AC-28: Bản chụp cũ thiếu `kindCode`, `timing`, `durationMinutes` trả `PREVIEW_EXPIRED`, không lỗi 500; đề chưa bấm Làm thử vẫn hết hạn sau 30 phút; kéo dài hạn và đọc hạn cũ chạy nguyên tử, hai lần bấm đồng thời cho cùng một hạn — bằng chứng: `PreviewPaperPortIT#legacySnapshotIsExpired`, `#trialExtensionIsAtomicWithExpiry` (mới)

**Ảnh hưởng ngoài task:**

- Bean `SolverLimits` được nhận theo kiểu (`DefaultExamChecker`, `ExamCheckAssembler`, `ExamCheckOrchestrator`, `ExamCheckUnitEngine`, `ExamCheckUnitStates`, `ExamPreviewService`, `SolverInputBuilder`) → ca `generationLimitsAreSeparate`, `Thi360ApplicationTests` khởi động được; không thêm bean cùng kiểu [chủ: task này]
- Cấu hình bộ giải hiện có (`ExamSolverProperties`, tiền tố `thi360.exam.solver`) → `ExamCheckPerfIT` không đổi cấu hình; ca `generationLimitsAreSeparate` [chủ: task này]
- Dùng lại luồng sinh thử (`PreviewPathResolver`, `PreviewPathSolver`, `PreviewPublishedCombinations`, `SolverInputBuilder`, `ExamPreviewStore`) → `ExamPreviewIT`, `PreviewPaperOrderingTest` xanh [chủ: task này]
- Bản lưu Redis (`PreviewSnapshotStore`) thêm `kindCode`, `timing`, `durationMinutes` và kéo dài hạn khi bấm Làm thử; giữ tiền tố khóa và hạn 30 phút của đề chưa bấm; bản cũ thiếu trường trả `PREVIEW_EXPIRED`, không lỗi 500 → cập nhật `PreviewSnapshotStoreTest`, chạy `PreviewPaperPortIT` [chủ: task này]
- Bộ giải và cắt câu không sửa (`PaperSolver`, `TrimPolicy`) → `PartExclusionRegressionTest`, `TrimPolicyTest`, `FlexibleSetTest`, `SubjectMatrixCasesTest` xanh [chủ: task này]
- Xếp thứ tự không sửa (`backend/src/main/java/vn/unicom/thi360/exam/spi/PaperOrdering.java`) → `PaperOrderingTest` xanh [chủ: task này]
- Nguồn câu (`backend/src/main/java/vn/unicom/thi360/questionbank/spi/QuestionSourcePort.java`, `findCandidates`) → `SampleQuestionCoverageIT` xanh, không đổi chữ ký [chủ: task này]
- Bản chụp phiên bản do công bố ghi (`ExamVersionSnapshot`, `ExamPublishService`) → `ExamVersionSnapshotTest`; EX-1 sửa `ExamPublishService`, EX-2 chỉ đọc `exam_version` [chủ: task này]
- Ranh giới module (`backend/src/main/java/vn/unicom/thi360/exam/spi/package-info.java`) → `ModularityTest` xanh; `exam` không phụ thuộc `attempt`, kiểu trong `exam/spi` không tham chiếu `exam.api` [chủ: task này]
- Tệp cấu hình chung với EX-1 (`backend/src/main/resources/application.yml`) → thêm ở cuối khối theo B9; `Thi360ApplicationTests` xanh [chủ: task này]
- Hợp đồng API và client sinh tự động (`packages/api-client/openapi.json`) → không có: task không thêm endpoint; `OpenApiExportIT` không tạo khác biệt [chủ: task này]
- Mã lỗi mới trong `messages*.properties` → không có: `PREVIEW_EXPIRED` đã có ở `messages.properties:453`, `GENERATION_*` chỉ là lý do nội bộ của cổng [chủ: task này]
- Sinh đề khi bắt đầu lượt gọi `PaperSolverPort`, xếp bằng `PaperOrdering` → test của AT-1 với dữ liệu DS [chủ: AT-1]
- Sinh phần 3 sau khi chọn truyền câu đã phát vào `excludeQuestionIds` → test của AT-2b [chủ: AT-2b]
- Chấm đề làm thử đọc đề qua `PreviewPaperPort` và xử lý 410 khi hết hạn → test của AT-5 [chủ: AT-5]
- Câu của đề sinh thử đã được ghi sổ `PREVIEW` ngay lúc sinh (`ExamPreviewStore.recordPreview`) → AT-5 không ghi sổ lần nữa khi chấm; test của AT-5 đếm `question_usage_ledger` không tăng [chủ: AT-5]
- Bộ dữ liệu cần sinh được 3 lượt không trùng câu qua cổng này → test của DS [chủ: DS]

**Kế hoạch kiểm thử:**

- AC-1: `PaperGenerationIT#sameSeedSameInputSamePaper`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaperGenerationIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `PaperGenerationHsaIT#scienceSubjectReceivesSixteenBySeed`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaperGenerationHsaIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `PaperGenerationHsaIT#scienceReportsDroppedQuestion`, tích hợp, cùng lệnh AC-2
- AC-4: `PaperGenerationHsaIT#onlyRequestedPartsAreGenerated`, tích hợp, cùng lệnh AC-2
- AC-5: `PaperGenerationQdaIT#combinationOrderComesFromSnapshot`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaperGenerationQdaIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `PaperGenerationQdaIT#globalListChangeDoesNotAffectOldVersion`, tích hợp, cùng lệnh AC-5
- AC-7: `PaperGenerationIT#workingCopyEditDoesNotAffectVersion`, tích hợp, cùng lệnh AC-1
- AC-8: `PaperGenerationIT#excludedQuestionsAreNotPickedInPart`, tích hợp, cùng lệnh AC-1
- AC-9: `PaperGenerationHsaIT#excludedQuestionsAreNotPickedInScience`, tích hợp, cùng lệnh AC-2
- AC-10: `PaperGenerationInformaticsIT#sptEachBranchGeneratesTwentyTwo`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaperGenerationInformaticsIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `PaperGenerationInformaticsIT#thptqgEachBranchGeneratesTwentyEight`, tích hợp, cùng lệnh AC-10
- AC-12: `PaperGenerationVactIT#mathTakesWholeClusters`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaperGenerationVactIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-13: `PaperGenerationVactIT#flexibleClustersKeepTotalAndStayWhole`, tích hợp, cùng lệnh AC-12
- AC-14: `PaperGenerationReviewIT#everyChildStaysInScope`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaperGenerationReviewIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `PaperGenerationIT#shortageFailsWithoutPartialPaper`, tích hợp, cùng lệnh AC-1
- AC-16: `PaperGenerationLimitsTest#deadlineMapsToGenerationTimeout` (đồng hồ giả, `SolverLimits` hạn 0), unit, `cd backend && ./mvnw -B -ntp test -Dtest=PaperGenerationLimitsTest`
- AC-17: `PaperGenerationIT#unknownVersionPartOrSelectionIsRefused`, tích hợp, cùng lệnh AC-1
- AC-18: `PaperGenerationLimitsTest#generationLimitsAreSeparate`, unit, cùng lệnh AC-16
- AC-19: `PaperGenerationIT#generationWritesNothing`, tích hợp, cùng lệnh AC-1
- AC-20: `PreviewPaperPortIT#loadReturnsTheOrderedPaper`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewPaperPortIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-21: `PreviewPaperPortIT#expiredPreviewIsGone` (xóa khóa Redis trong test), tích hợp, cùng lệnh AC-20
- AC-22: `PreviewPaperPortIT#previewOfAnotherExamIsGone`, tích hợp, cùng lệnh AC-20
- AC-23: `PaperGenerationPerfIT`, hiệu năng, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaperGenerationPerfIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dgroups=perf`
- AC-24: kiểm tài liệu, hợp đồng, `git grep -n -E "PaperSolverPort|PreviewPaperPort" -- docs/specs/2026-11-16-be-pbi-03-04.md`
- AC-25: `PreviewPaperPortIT#trialExtensionNeverShortens`, `#extendExpiredPreviewIsGone`, `#untouchedPreviewKeepsThirtyMinutes` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewPaperPortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-26: `PreviewPaperPortIT#sharedTrialUsesExamDuration`, `#perPartTrialSumsGeneratedPath`, `#draftEditDoesNotChangeTrial` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewPaperPortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-27: `PreviewPaperPortIT#loadCarriesKindAttachmentsAndSegments`, `#loadIgnoresLaterDraftEdits` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewPaperPortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-28: `PreviewPaperPortIT#legacySnapshotIsExpired`, `#trialExtensionIsAtomicWithExpiry` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewPaperPortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy:
  - Sinh thử và kiểm tra: `cd backend && ./mvnw -B -ntp verify -Dit.test='ExamPreviewIT,SampleQuestionCoverageIT,OpenApiExportIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
  - Unit: `cd backend && ./mvnw -B -ntp test -Dtest='PreviewPaperOrderingTest,PreviewSnapshotStoreTest,PartExclusionRegressionTest,TrimPolicyTest,FlexibleSetTest,SubjectMatrixCasesTest,PaperOrderingTest,ExamVersionSnapshotTest,ModularityTest,Thi360ApplicationTests'`
  - Hiệu năng kiểm tra không đổi: `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamCheckPerfIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dgroups=perf`
  - Toàn bộ backend trước khi nộp: `cd backend && ./mvnw -B -ntp verify`
- Kiểm thử rủi ro:
  - Quyền: cổng là lời gọi nội bộ, không có endpoint mới nên không có ca thiếu quyền; `PreviewPaperPort` chặn đọc chéo bài (AC-22). Quyền `EXAM.VIEW` của API chấm thử do AT-5 kiểm.
  - Gửi lặp, đồng thời: 20 lời gọi đồng thời cùng hạt giống cho cùng kết quả và không làm hỏng trạng thái dùng chung (`PaperGenerationIT`, ca `concurrentCallsStayDeterministic`); gọi `load` nhiều lần cho cùng kết quả (`PreviewPaperPortIT`, ca `loadIsRepeatable`).
  - Migration trên DB đã có dữ liệu: không có migration. Phiên bản công bố trước EX-2 đọc được vì cổng dùng đúng `exam_version` hiện có (AC-6, AC-7).
  - Giá trị biên: `excludeQuestionIds` rỗng và loại đến khi vừa đủ câu, loại thêm một câu thì lỗi; `partCodes` rỗng; hạn 0 (AC-16); nhóm chùm có đúng số câu con cần lấy.
  - Đỏ trước: AC-9, AC-12, AC-14, AC-15, AC-16, AC-20, AC-22.
- Dữ liệu test: phiên bản dựng bằng công bố thật trên mẫu của đợt 2B (`HSA_FULL`, `QDA_FULL`, `VACT_SUBJECT_TOAN`, `SPT_INFORMATICS_2025`, `THPTQG_INFORMATICS_2025`, `VACT_REVIEW` là mẫu bật sẵn `everyChildInScope` theo ADD H29); câu lấy từ `R__seed_*` của profile `test`, câu thiếu do test tạo với mã ngẫu nhiên. Test `perf` tự tạo 2.000 ứng viên mỗi khối trong DB của nó. Mỗi lane có Testcontainers riêng.
- Không kiểm: thời gian sinh đề trên máy chủ thật (số đo `perf` chạy trên runner của lane); chất lượng phân bố câu giữa nhiều hạt giống; luồng làm thử đầu cuối (AT-5, FA-4).


## DS · Bộ dữ liệu bài đã công bố

**Spec:** `docs/specs/2026-11-17-de-test-gia-dinh.md`; BE 5.2; LT 13; TM 6; ADD mục 4, 5; BS 2, 17; 2B bổ sung H-SCA, V-SAT mục 4, 5. **Phụ thuộc mềm:** EX-2.

**Sản phẩm:**

- Bộ dựng dữ liệu đi qua service thật: tạo câu trong ngân hàng, tạo bài từ mẫu của đợt 2B, điền số liệu, điền thông tin bán, kiểm tra, công bố. Không chèn thẳng bằng SQL (`exam_version.check_run_id` là `NOT NULL`, và dữ liệu phải đi đúng đường thật).
- Nguồn số liệu: mẫu do migration nạp là chuẩn cho mọi bài; `de-test-gia-dinh` (bản dựng lại theo mẫu BA ở cổng G0c) chỉ dùng cho HSA, QDA, BCA, SP2E, SPT; V-ACT và THPTQG không theo tệp này.
- Đủ bài đã công bố:

  | Bài | Mẫu |
  |---|---|
  | HSA FULL (đủ hai nhánh phần 3, đủ 5 môn Khoa học, ma trận 17 câu) | `HSA_FULL` |
  | HSA theo phần | `HSA_PART_MATH` |
  | HSA theo môn | `HSA_SUBJECT_TOAN` |
  | QDA FULL (ma trận theo môn, QĐ01–QĐ05) | `QDA_FULL` |
  | QDA theo phần Khoa học | `QDA_PART_SCIENCE` |
  | BCA FULL (có câu tự luận; là bài duy nhất dùng cho ca chấm tay, vì Văn SP2E V1 bỏ phần Viết theo 2B K7) | `BCA_FULL` |
  | BCA theo phần trắc nghiệm tự chọn | `BCA_PART_TN_TU_CHON` |
  | V-ACT FULL (không có bản thu nhỏ) | `VACT_FULL` |
  | V-ACT theo phần | `VACT_PART_LANGUAGE` |
  | V-ACT theo môn | `VACT_SUBJECT_TOAN` |
  | SP2E Toán, đề minh họa cũ và 2026 | `SP2E_MATH_2025`, `SP2E_MATH_2026` |
  | SP2E 3 môn theo môn, cho bộ đề của CM-6 (Văn là bản mặc định 30 câu, không có tự luận, 2B K15) | `SP2E_MATH_2026`, `SP2E_VAN_OLD`, `SP2E_ANH_2026` |
  | SPT Tin học V1 (hai định hướng) | `SPT_INFORMATICS_2025` |
  | THPTQG Tin học | `THPTQG_INFORMATICS_2025` |
  | THPTQG theo phần: Toán Phần I | `THPTQG_MATH_2025`, phạm vi `PART`, phần `P1` |
  | Bài ôn (`build_mode = REVIEW`) | `HSA_REVIEW` |
  | H-SCA Toán (chọn nhiều nửa điểm) | `HSCA_SUBJECT_TOAN` |
  | H-SCA Tiếng Anh (Nghe có audio và Đọc) | `HSCA_SUBJECT_ANH` |
  | H-SCA Hóa học (mọi câu điền đáp án trong nguồn của bài có đáp án ion hoặc chuỗi ký hiệu; đáp án ghi trong tệp hằng số) | `HSCA_SUBJECT_HOA` |
  | V-SAT Toán (Đúng/Sai, ghép hợp, trả lời ngắn) | `VSAT_SUBJECT_TOAN` |
  | V-SAT Ngữ văn I–III (thang 120) | `VSAT_SUBJECT_VAN` |
  | V-SAT Tiếng Anh (phân đoạn hiển thị, cloze) | `VSAT_SUBJECT_ANH` |

- Thêm một bài đã ngừng bán, và một bài đang bán được đánh dấu trong tệp hằng số để dùng với voucher 100% (bài này có giá lớn hơn 0; bản thân voucher do FA-2 tạo).
- Bài dùng mẫu của đợt 2B (có `tagCodes`); câu trong ngân hàng của bộ dữ liệu gắn nhãn dạng câu và loại văn bản, lấy từ dữ liệu câu hỏi mẫu của 2B (BE-10, BE-14). Mẫu thiếu câu để đạt số lượt dưới đây thì bộ dựng tạo thêm câu cùng nhãn qua `QuestionService`.
- Ngân hàng câu đủ để mỗi bài sinh được ít nhất 3 lượt không trùng câu.
- Mỗi bài có một bản "thu nhỏ" (ít câu, thời lượng ngắn) cho E2E, trừ bài dùng mẫu `fixed_counts = true` (`VACT_FULL`, lệch số câu là lỗi `STRUCTURE_COUNT_LOCKED`).
- Đặt mã nguồn trong hai package con để không phá ranh giới module: `questionbank/devdata` gọi service của `questionbank`, `exam/devdata` gọi service của `exam`. Hai phần chạy nối tiếp bằng `@Order`, không gọi trực tiếp nhau. Bộ dựng chạy dưới danh tính `sys.admin` vì `IdempotentCommand` và các service cần người dùng hiện tại.
- Dùng được ở ba nơi:
  - test tích hợp: lớp hỗ trợ test gọi bộ dựng;
  - profile `dev`: chạy khi khởi động nếu chưa có;
  - profile `e2e`: chạy khi khởi động.
- Danh sách mã bài, mã sản phẩm và tài khoản mẫu ghi vào một tệp hằng số dùng chung cho test và E2E: `backend/src/main/resources/devdata/published-exam-set.json`. Mã bài có tiền tố `DS-`.
- Thêm một bài đang bán có quyền lợi `result_view = SCORE` (chỉ xem điểm), dùng cho ca "không có xem lại" của AT-4 và FS-6.
- Mẫu H-SCA, V-SAT có `fixed_block_counts = true` thì không có bản thu nhỏ. `DevQuestionAssetSeeder` chạy thêm ở profile `e2e`, để câu Nghe có tệp audio thật trong kho.
- Ảnh cho câu: bộ dựng tải ảnh PNG mẫu qua `QuestionAssetService` (đường tải lên thật của đợt 2B), gắn vào đề bài, một phương án và lời giải của ít nhất một câu trong một bài đã công bố; mã câu và mã tệp ghi vào tệp hằng số. Seed dev của đợt 2B chỉ có tệp audio (FU-BE10-ASSET).

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/exam/devdata/**` (mới)
- `backend/src/main/java/vn/unicom/thi360/questionbank/devdata/**` (mới)
- `backend/src/main/resources/devdata/published-exam-set.json` (mới)
- `backend/src/test/java/vn/unicom/thi360/support/PublishedExamSetSupport.java` (mới)
- `backend/src/test/java/vn/unicom/thi360/exam/devdata/**` (mới)
- `e2e/tests/helpers/published-exam-set.ts` (mới)
- `e2e/tests/published-exam-set.spec.ts` (mới)
- thêm vào: `backend/src/main/resources/application.yml` (`thi360.devdata.published-exams.enabled`, bật ở khối `dev` và khối `on-profile: e2e`)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/identity/application/DevDataInitializer.java` (chỉ thêm profile `e2e` vào `@Profile`)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/questionbank/application/DevQuestionAssetSeeder.java` (chỉ thêm profile `e2e` vào `@Profile`)
- `backend/src/main/resources/devdata/assets/ds-*.png` (mới, ảnh mẫu nhỏ)

**Ngoài phạm vi:** bộ SP2E 3 môn (CM-6 thêm vào bộ dựng này sau khi DS merge); voucher 100% (bảng voucher có từ CM-2, voucher do FA-2 tạo, FS-3 dùng); dòng bảng chiếu (CM-1 dựng từ sự kiện và `rebuild`); khối `on-profile: e2e` và các đường `/api/v1/test/**` (C-0b); lượt thi trên dữ liệu này (AT-1); xóa DB demo và nạp lại (VP-2).

**Nghiệm thu:**

- AC-1: DB mới với profile `dev`: mọi bài trong bảng hiện ở trạng thái đã công bố trong web admin — bằng chứng: `e2e/tests/published-exam-set.spec.ts`, kịch bản "mọi bài đã công bố"
- AC-2: Mọi bài trong bảng có kết quả kiểm tra cấu hình đạt (`VALID`) và có phiên bản đang phục vụ — bằng chứng: `PublishedExamSetIT`, ca `everyExamIsPublishedWithValidCheck`
- AC-3: Chạy lại bộ dựng không tạo trùng bài, câu, phiên bản — bằng chứng: `PublishedExamSetIT`, ca `rerunCreatesNothing`
- AC-4: Bộ dựng dừng giữa chừng thì lần chạy sau làm tiếp các bài còn thiếu, không tạo trùng — bằng chứng: `PublishedExamSetIT`, ca `resumesAfterPartialRun`
- AC-5: Test tích hợp gọi bộ dựng chạy dưới 60 giây — bằng chứng: `PublishedExamSetIT`, ca `buildsWithinSixtySeconds`
- AC-6: HSA FULL có đủ hai nhánh phần 3, 5 môn Khoa học, ma trận 17 câu; QDA FULL có QĐ01–QĐ05 — bằng chứng: `PublishedExamSetIT`, ca `fullExamsServeEveryPath`
- AC-7: BCA FULL có câu tự luận và cảnh báo `MANUAL_GRADING_REQUIRED` — bằng chứng: `PublishedExamSetIT`, ca `bcaFullHasManualGrading`
- AC-8: Có đủ bài V-ACT `PART`, V-ACT `SUBJECT`, THPTQG `PART` (Toán Phần I), số câu và điểm tối đa đúng theo mẫu do migration nạp — bằng chứng: `PublishedExamSetIT`, ca `partialScopesFollowTheirTemplates`
- AC-9: Bài ôn đã công bố có `build_mode = REVIEW`, đọc được qua `PublishedExamPort` với `buildMode = REVIEW` — bằng chứng: `PublishedExamSetIT`, ca `reviewExamIsPublished`
- AC-10: Tin học SPT V1 và THPTQG phục vụ cả hai định hướng — bằng chứng: `PublishedExamSetIT`, ca `informaticsServesBothBranches`
- AC-11: Bài ngừng bán có trạng thái `DISCONTINUED`; bài dành cho voucher đang bán và có giá lớn hơn 0 — bằng chứng: `PublishedExamSetIT`, ca `saleStatesMatchTheConstants`
- AC-12: Mỗi bài sinh được 3 lượt liên tiếp qua `PaperSolverPort`, lượt sau loại câu của các lượt trước, không lỗi, không trùng câu — bằng chứng: `PublishedExamSetGenerationIT`, ca `everyExamGeneratesThreeDisjointAttempts`
- AC-13: Mỗi bài có bản thu nhỏ đã công bố, trừ `VACT_FULL`; không có bản thu nhỏ của `VACT_FULL` — bằng chứng: `PublishedExamSetIT`, ca `miniCopiesExistExceptFixedCounts`
- AC-14: Mọi mã bài, mã sản phẩm, tài khoản trong tệp hằng số tồn tại sau khi dựng, và mọi bài do bộ dựng tạo đều có trong tệp — bằng chứng: `PublishedExamSetIT`, ca `constantsMatchBuiltData`
- AC-15: Profile `e2e` khởi động trên DB mới có đủ bộ dữ liệu — bằng chứng: `PublishedExamSetProfileIT`, ca `e2eProfileBuildsTheSet`
- AC-16: Tắt `thi360.devdata.published-exams.enabled` thì khởi động không tạo bài `DS-`; profile `test` mặc định không chạy bộ dựng — bằng chứng: `PublishedExamSetProfileIT`, ca `disabledFlagBuildsNothing`
- AC-17: Thiếu tài khoản `sys.admin` thì bộ dựng dừng với thông báo rõ, không tạo dữ liệu dở dang — bằng chứng: `PublishedExamSetProfileIT`, ca `missingActorStopsCleanly`
- AC-18: Bài có `result_view = SCORE` đã công bố, mã nằm trong tệp hằng số — bằng chứng: `PublishedExamSetIT`, ca `scoreOnlyExamIsPublished`
- AC-19: Sáu bài H-SCA, V-SAT đã công bố; `kindCode`, `displaySegments`, `audioRequirement` đọc được qua `PublishedExamPort` — bằng chứng: `PublishedExamSetIT#hscaVsatArePublished` (mới)
- AC-20: Ở profile `e2e`, mọi câu Nghe của bài H-SCA Tiếng Anh có tệp audio đọc được trong kho — bằng chứng: `PublishedExamSetIT#listeningAudioIsStoredInE2eProfile` (mới)
- AC-21: Bài đã công bố trong tệp hằng số có một câu với ảnh ở đề bài, phương án và lời giải; mỗi ảnh có dòng `question_asset` và đọc được từ kho — bằng chứng: `PublishedExamSetIT#imageQuestionHasStoredAssets` (mới)

**Ảnh hưởng ngoài task:**

- Ranh giới module với hai package con mới (`exam/devdata`, `questionbank/devdata`) → `ModularityTest` xanh [chủ: task này]
- Thứ tự chạy khi khởi động (`DevDataInitializer` `@Order(1)`, `DevClassSeeder` `@Order(2)`) → hai phần của DS đặt `@Order` 3 và 4; `DevDataInitializerIT`, `DevClassSeederIT` xanh [chủ: task này]
- Test tích hợp bật profile `dev` sẽ chạy bộ dựng khi khởi động (`DevClassSeederIT`, `DevDataInitializerIT`, `ExamStructureV2DevdataDevIT`) → ba lớp đặt `thi360.devdata.published-exams.enabled=false`; cả ba xanh [chủ: task này]
- E2E hiện có chạy với `api,worker,dev` nên thấy thêm các bài `DS-` (`.github/workflows/e2e.yml:66`, `e2e/tests/list-stability.spec.ts`, `e2e/tests/exam-create-step1.spec.ts`, `e2e/tests/exam-allocation.spec.ts`, `e2e/tests/exam-preview-qda.spec.ts`, `e2e/tests/exam-structure-frame.spec.ts`) → chạy toàn bộ E2E trên DB mới; không spec nào đỏ [chủ: task này]
- Playwright coi backend sẵn sàng khi `actuator/health` trả lời, hạn 180 giây (`e2e/playwright.config.ts`, `webServer`) → bộ dựng chạy xong trước khi health báo `UP`, hoặc `e2e/tests/helpers/published-exam-set.ts` chờ đủ dữ liệu; kịch bản "mọi bài đã công bố" đạt trên DB mới [chủ: task này]
- Dữ liệu mẫu dùng chung (`backend/src/main/resources/db/devdata/R__seed_*.sql`, `TestExamSeedIT` đếm mã `TEST-%`, `QuestionSeedIT`, `SampleQuestionCoverageIT`, `VactThptSampleDataIT`) → mã bài dùng tiền tố `DS-`, câu thêm dùng mã riêng; bốn lớp xanh [chủ: task này]
- Câu do bộ dựng tạo làm đổi nguồn câu của test dùng chung DB (`AbstractIntegrationTest`, container PostgreSQL tĩnh) → `PublishedExamSetSupport` dựng trong một database riêng của container; `SampleQuestionCoverageIT`, `ExamTemplateCheckIT` xanh [chủ: task này]
- Khối `on-profile: e2e` phải nạp `classpath:db/devdata` để có câu mẫu (`backend/src/main/resources/application.yml`, khối `dev` và `test` đang có) → ca `e2eProfileBuildsTheSet` [chủ: C-0b]
- Tài khoản mẫu ở profile `e2e` (`backend/src/main/java/vn/unicom/thi360/identity/application/DevDataInitializer.java`, đang `@Profile("dev")`) → `DevDataInitializerIT`, ca `e2eProfileBuildsTheSet` [chủ: task này]
- Công bố phát `ExamPublished`, `ExamDiscontinued`; trước khi có bộ nhận thì relay đánh hỏng sự kiện (`backend/src/main/java/vn/unicom/thi360/shared/outbox/OutboxRelay.java`) → CM-1 gọi `rebuild`; `StorefrontDataSetIT` [chủ: CM-1]
- Bài trên bảng chiếu theo dữ liệu DS → `StorefrontDataSetIT` [chủ: CM-1]
- Cảnh báo chấm tay kiểm bằng bài BCA FULL → test của FA-1 [chủ: FA-1]
- Lượt thi dùng dữ liệu DS → test của AT-1 [chủ: AT-1]
- Ca chấm thử dùng bài SP2E Toán của DS → test của FA-4 [chủ: FA-4]
- Bộ SP2E 3 môn thêm vào bộ dựng → test của CM-6 [chủ: CM-6]
- Bài ôn chọn được vào bộ đề (BS 2) → test của CM-6 [chủ: CM-6]
- Nhãn và thẻ của bài ôn trên trang học viên (BS 2) → test của FS-1 [chủ: FS-1]
- Demo dựng lại từ DS → kịch bản mua thật của VP-2 [chủ: VP-2]
- E2E đầu cuối dùng tệp hằng số và bản thu nhỏ → kịch bản của QA [chủ: QA]
- Hợp đồng API và client sinh tự động (`packages/api-client/openapi.json`) → không có: task không thêm endpoint; `OpenApiExportIT` không tạo khác biệt [chủ: task này]

**Kế hoạch kiểm thử:**

- AC-1: `e2e/tests/published-exam-set.spec.ts`, E2E, `pnpm --filter @thi360/e2e test -- tests/published-exam-set.spec.ts`
- AC-2: `PublishedExamSetIT#everyExamIsPublishedWithValidCheck`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamSetIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `PublishedExamSetIT#rerunCreatesNothing`, tích hợp, cùng lệnh AC-2
- AC-4: `PublishedExamSetIT#resumesAfterPartialRun`, tích hợp, cùng lệnh AC-2
- AC-5: `PublishedExamSetIT#buildsWithinSixtySeconds`, hiệu năng, cùng lệnh AC-2
- AC-6: `PublishedExamSetIT#fullExamsServeEveryPath`, tích hợp, cùng lệnh AC-2
- AC-7: `PublishedExamSetIT#bcaFullHasManualGrading`, tích hợp, cùng lệnh AC-2
- AC-8: `PublishedExamSetIT#partialScopesFollowTheirTemplates`, tích hợp, cùng lệnh AC-2
- AC-9: `PublishedExamSetIT#reviewExamIsPublished`, tích hợp, cùng lệnh AC-2
- AC-10: `PublishedExamSetIT#informaticsServesBothBranches`, tích hợp, cùng lệnh AC-2
- AC-11: `PublishedExamSetIT#saleStatesMatchTheConstants`, tích hợp, cùng lệnh AC-2
- AC-12: `PublishedExamSetGenerationIT#everyExamGeneratesThreeDisjointAttempts`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamSetGenerationIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-13: `PublishedExamSetIT#miniCopiesExistExceptFixedCounts`, tích hợp, cùng lệnh AC-2
- AC-14: `PublishedExamSetIT#constantsMatchBuiltData`, hợp đồng, cùng lệnh AC-2
- AC-15: `PublishedExamSetProfileIT#e2eProfileBuildsTheSet`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamSetProfileIT -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
- AC-16: `PublishedExamSetProfileIT#disabledFlagBuildsNothing`, tích hợp, cùng lệnh AC-15
- AC-17: `PublishedExamSetProfileIT#missingActorStopsCleanly`, tích hợp, cùng lệnh AC-15
- AC-18: `PublishedExamSetIT#scoreOnlyExamIsPublished`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamSetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-19: `PublishedExamSetIT#hscaVsatArePublished` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamSetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-20: `PublishedExamSetIT#listeningAudioIsStoredInE2eProfile` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamSetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-21: `PublishedExamSetIT#imageQuestionHasStoredAssets` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamSetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy:
  - Dữ liệu mẫu và khởi động: `cd backend && ./mvnw -B -ntp verify -Dit.test='DevClassSeederIT,DevDataInitializerIT,ExamStructureV2DevdataDevIT,TestExamSeedIT,QuestionSeedIT,SampleQuestionCoverageIT,VactThptSampleDataIT,ExamTemplateCheckIT,OpenApiExportIT' -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false`
  - Ranh giới module: `cd backend && ./mvnw -B -ntp test -Dtest=ModularityTest`
  - E2E hiện có trên DB mới: `pnpm --filter @thi360/e2e test`
  - Toàn bộ backend trước khi nộp: `cd backend && ./mvnw -B -ntp verify`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: `PublishedExamSetSupport` (mới) tạo một database riêng trong container PostgreSQL của `AbstractIntegrationTest`, chạy migration, nạp `R__seed_*`, tạo tài khoản `sys.admin` rồi gọi bộ dựng một lần cho mỗi JVM; test khác không thấy các bài `DS-`. Mỗi lane có Testcontainers riêng. E2E dùng DB mới của lần chạy.
- Không kiểm:
  - Nội dung câu hỏi có đúng chuyên môn không: câu là dữ liệu mẫu.
  - Việc bán và mua trên dữ liệu này (CM-1, CM-3a, FS-3, QA).
  - Voucher 100% (FA-2, FS-3).
  - Bộ SP2E 3 môn (CM-6).

**Câu hỏi mở:** M.Trường xác nhận ba môn của bộ SP2E: plan chỉ nêu Văn, DS tạm dùng Toán, Văn, Tiếng Anh.

## VP-1 · Thử VNPAY sandbox trước khi làm cổng thật

**Spec:** TM 8.4, E26, Q12, Q14; plan A3. **Cổng:** G2.

**Sản phẩm:**

- Script nhỏ `vp1_sandbox.py` (Python, đặt trong thư mục đợt chạy `D:/.Vietants_Project/.thi360-dev/dot-3/vnpay/`, không vào repo) ký và gọi sandbox, đọc khóa từ `THI360_VNPAY_TMN_CODE`, `THI360_VNPAY_HASH_SECRET`:
  1. Tạo URL `pay` cho 2 giao dịch, trả bằng thẻ thử của sandbox: một thành công, một hủy. Ghi tham số trang quay lại bằng một cổng nghe trên máy chạy (`localhost`), không mở đường hầm (A3).
  2. `querydr` cho cả hai, các lần gọi cùng giao dịch cách nhau hơn 5 phút (TM 8.4, mã `94`).
  3. `refund` toàn phần và một phần, mỗi lệnh trên một giao dịch thành công riêng, sau đó `querydr` lại.
  4. Thử số tiền 1.000, 4.999, 5.000 đồng để chốt mức tối thiểu (E26).
- Tệp bằng chứng trong `dot-3/vnpay/` (đã che khóa và chữ ký): `pay-params.json`, `return-params.json`, `querydr.json`, `refund.json`, `min-amount.md`.
- Trong repo (PR của VP-1):
  - `backend/src/test/resources/vnpay/vectors.json`: vector chữ ký cho `pay`, `querydr`, `refund`, cả yêu cầu và phản hồi. Vector dùng một khóa thử tự đặt, không phải khóa sandbox; chuỗi ký và quy tắc mã hóa lấy theo kết quả thật;
  - sửa TM 8.4, E26, Q12, Q14 theo kết quả đo, ghi ngày và nguồn.

**Đường dẫn sở hữu:**

- `backend/src/test/resources/vnpay/vectors.json` (mới)
- `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md` (chỉ mục 8.4, E26, Q12, Q14)
- Ngoài repo: `D:/.Vietants_Project/.thi360-dev/dot-3/vnpay/**` (script và bằng chứng)

**Ngoài phạm vi:** bản cài `VnpayGateway` và test đọc `vectors.json` (CM-3b); `VnpayGateway` giả lập (CM-3a); IPN thật tới bản demo (VP-2, bản demo chưa có endpoint ở thời điểm VP-1); đổi `THI360_VNPAY_MIN_AMOUNT` trong code nếu mức đo được khác 5.000 đồng (EX-1, CM-2, CM-3a, CM-3b); câu trả lời chính thức của VNPAY cho Q9, Q14 (M.Trường).

**Nghiệm thu:**

- AC-1: Có đủ năm tệp bằng chứng trong `dot-3/vnpay/` — bằng chứng: kịch bản VP1-S1: `ls` năm tệp, mỗi tệp khác rỗng
- AC-2: `pay-params.json`, `return-params.json` có một giao dịch thành công (`vnp_ResponseCode = 00`) và một giao dịch hủy (`24`) — bằng chứng: kịch bản VP1-S2 đọc hai tệp
- AC-3: `querydr.json` có kết quả của cả hai giao dịch, trước và sau khi hoàn — bằng chứng: kịch bản VP1-S3 đọc tệp
- AC-4: `refund.json` có một lệnh hoàn toàn phần và một lệnh hoàn một phần kèm mã phản hồi thật; API hoàn chưa được bật thì ghi đúng mã nhận được (Q9), không tự suy ra thành công — bằng chứng: kịch bản VP1-S4 đọc tệp
- AC-5: `min-amount.md` ghi kết quả của 1.000, 4.999, 5.000 đồng kèm mã phản hồi — bằng chứng: kịch bản VP1-S5 đọc tệp
- AC-6: Tìm khóa sandbox trong thư mục bằng chứng ra 0 kết quả — bằng chứng: lệnh quét VP1-SCAN-1
- AC-7: Tìm khóa sandbox trong diff ra 0 kết quả; hai lệnh quét của A7 mục 10 ra `0` — bằng chứng: lệnh quét VP1-SCAN-2
- AC-8: Bằng chứng không còn chữ ký thật: mọi `vnp_SecureHash` đã che — bằng chứng: lệnh quét VP1-SCAN-3
- AC-9: `vectors.json` có yêu cầu và phản hồi của `pay`, `querydr`, `refund`; tính lại chữ ký bằng khóa thử tự đặt khớp mọi vector — bằng chứng: `python vp1_sandbox.py verify-vectors backend/src/test/resources/vnpay/vectors.json` ra `OK`
- AC-10: Khóa trong `vectors.json` khác khóa sandbox — bằng chứng: lệnh quét VP1-SCAN-2 trên tệp này
- AC-11: TM ghi rõ quy tắc mã hóa URL của chuỗi ký `pay`, kèm ngày và nguồn — bằng chứng: kịch bản VP1-S6 đọc TM 8.4
- AC-12: TM ghi rõ hàm băm của `querydr` và `refund`, kèm ngày và nguồn — bằng chứng: kịch bản VP1-S6 đọc TM 8.4
- AC-13: TM ghi rõ mức tối thiểu đo được, kèm ngày và nguồn — bằng chứng: kịch bản VP1-S6 đọc TM E26, Q12
- AC-14: TM ghi rõ nghĩa của `00` khi hoàn theo những gì sandbox cho thấy, phần chưa đo được vẫn để mở — bằng chứng: kịch bản VP1-S6 đọc TM Q14

**Ảnh hưởng ngoài task:**

- Bản cài thật của cổng đọc vector chữ ký (`backend/src/test/resources/vnpay/vectors.json`, mới) → test vector chữ ký của CM-3b chạy trên tệp này [chủ: CM-3b]
- TM 8.4 đổi quy tắc ký nếu sandbox khác tài liệu (`docs/specs/2026-11-16-thuong-mai-pbi-05-09.md:553`) → CM-3b đối chiếu bản TM đã sửa trước khi nộp [chủ: CM-3b]
- Bản giả lập ký IPN theo cùng quy tắc (`VnpayGateway` giả lập của CM-3a) → test IPN của CM-3a xanh trên quy tắc đã sửa [chủ: CM-3a]
- Mức tối thiểu chặn công bố `LISTING_PRICE_BELOW_MIN` nếu đo được khác 5.000 đồng (TM E26) → test của EX-1 dùng mức đã chốt [chủ: EX-1]
- Mức tối thiểu giới hạn giá sau khuyến mãi (TM E26) → test của CM-2 dùng mức đã chốt [chủ: CM-2]
- Bằng chứng của VP-1 là mốc so sánh của luồng có IPN (`dot-3/vnpay/`) → VP-2 sửa TM, `VnpayGateway` nếu khác kết quả của VP-1 [chủ: VP-2]
- Thay đổi trong repo chỉ là tệp test và spec: đã tìm `vnpay` trong `backend/src/main`, `backend/src/test` trên `develop`, chưa có code đọc → `cd backend && ./mvnw -B -ntp verify` xanh [chủ: task này]

**Kế hoạch kiểm thử:**

- AC-1: VP1-S1, E2E, `ls -l D:/.Vietants_Project/.thi360-dev/dot-3/vnpay/{pay-params.json,return-params.json,querydr.json,refund.json,min-amount.md}`: đủ năm tệp, kích thước lớn hơn 0
- AC-2: VP1-S2, E2E, `python vp1_sandbox.py pay` rồi trả bằng thẻ thử: tệp có mã `00` và `24`
- AC-3: VP1-S3, E2E, `python vp1_sandbox.py querydr`: mỗi giao dịch có kết quả, không còn mã `94`
- AC-4: VP1-S4, E2E, `python vp1_sandbox.py refund`: có đủ hai lệnh kèm mã phản hồi, sau đó `querydr` lại
- AC-5: VP1-S5, E2E, `python vp1_sandbox.py min-amount`: có dòng kết quả cho ba mức tiền
- AC-6: VP1-SCAN-1, hợp đồng, `test -n "$THI360_VNPAY_HASH_SECRET" && test -n "$THI360_VNPAY_TMN_CODE" && grep -rlF -e "$THI360_VNPAY_HASH_SECRET" -e "$THI360_VNPAY_TMN_CODE" D:/.Vietants_Project/.thi360-dev/dot-3/vnpay --exclude=vp1_sandbox.py | wc -l` ra `0`
- AC-7: VP1-SCAN-2, hợp đồng, `git diff origin/integration/dot-3...HEAD | grep -cF -e "$THI360_VNPAY_HASH_SECRET" -e "$THI360_VNPAY_TMN_CODE"` ra `0`, cùng hai lệnh ở A7 mục 10
- AC-8: VP1-SCAN-3, hợp đồng, `grep -rEl "vnp_SecureHash\"?[=:] *\"?[0-9a-fA-F]{64,}" D:/.Vietants_Project/.thi360-dev/dot-3/vnpay --include=*.json --include=*.md | wc -l` ra `0`
- AC-9: `verify-vectors`, hợp đồng, `python vp1_sandbox.py verify-vectors backend/src/test/resources/vnpay/vectors.json`
- AC-10: VP1-SCAN-2 trên một tệp, hợp đồng, `grep -cF "$THI360_VNPAY_HASH_SECRET" backend/src/test/resources/vnpay/vectors.json` ra `0`
- AC-11: VP1-S6, hợp đồng, người duyệt PR đối chiếu TM 8.4 với `pay-params.json`: quy tắc mã hóa khớp chuỗi đã ký
- AC-12: VP1-S6, hợp đồng, người duyệt PR đối chiếu TM 8.4 với `querydr.json`, `refund.json`: hàm băm khớp
- AC-13: VP1-S6, hợp đồng, người duyệt PR đối chiếu TM E26, Q12 với `min-amount.md`: cùng con số
- AC-14: VP1-S6, hợp đồng, người duyệt PR đối chiếu TM Q14 với `refund.json`: không ghi quá những gì đã đo
- Hồi quy:
  - Repo vẫn xanh: `cd backend && ./mvnw -B -ntp verify`
  - Dòng của CM-3a, CM-3b, EX-1, CM-2, VP-2: task chủ chạy phép kiểm đã ghi ở dòng đó.
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: thẻ thử công khai của sandbox VNPAY; mã giao dịch sinh theo thời gian chạy, không trùng giữa các lần; khóa sandbox chỉ nằm trong biến môi trường của máy chạy VP (G2), không ghi vào script, bằng chứng, nhật ký, bình luận issue; một lane duy nhất chạy VP-1.
- Không kiểm: IPN (cần URL công khai, VP-2 làm trên bản demo); môi trường thật của VNPAY (A3 cấm); danh sách IP gọi IPN và bảng mã hoàn chính thức (Q9, Q14, chờ VNPAY trả lời); sandbox có chấp nhận `vnp_ReturnUrl` là `localhost` không: nếu bị từ chối thì chép tham số từ thanh địa chỉ của trình duyệt sau khi trả tiền và ghi cách làm vào `return-params.json`.

**Câu hỏi mở:** Q9 (M.Trường hỏi VNPAY): merchant sandbox đã bật API hoàn tiền chưa. Chưa bật thì AC-4 ghi đúng mã nhận được, phần `00` của AC-14 để mở và VP-2 kiểm lại.

## ID-1 · Đăng ký, xác minh email, đổi email

**Spec:** TM 4 (`preferred_locale`), 5.1, 5.2, 5.3, 13, E15, E17, E18, E19, C9, Q13; plan A6, B2, B6, B9.

**Sản phẩm:**

- `POST /api/v1/auth/register` (công khai). Tạo tài khoản và user Keycloak, có bước bù theo cách của PBI-01. Tạo phiên, xếp thư OTP. Ghi `preferred_locale`.
- Dòng tạm `PROVISIONING` của đăng ký ghi `provisioning_role_ids` là vai trò Học viên, để `ProvisioningReconcilerJob` không coi là thiếu vai trò rồi xóa.
- Cột `terms_version`, `terms_accepted_at`, `pending_email`.
- Bảng `identity.email_code`, mã HMAC (pepper của C-0). API `/me/email/verification`, `/verify`, `/change`, `/change/confirm`.
- Thư `EMAIL_CHANGED_NOTICE` tới địa chỉ cũ.
- Đổi email đồng bộ sang Keycloak; lỗi thì rollback, trả 503.
- `THI360_DEV_EMAIL_CODE` chỉ ở profile `dev` (A6).
- Giới hạn tần suất theo E15, E17, E19. `RateLimitFilter` có thêm nhiều cửa sổ cho một chính sách (30 lần mỗi giờ và 100 lần mỗi ngày); thời gian sống của khóa Redis tính theo cửa sổ dài nhất.
- Hợp đồng lỗi FS-2 dùng, làm bằng advice riêng `identity/api/EmailCodeExceptionHandler` (mới) theo mẫu `ImportBatchExceptionHandler`, không sửa `ApiError` và `GlobalExceptionHandler`:
  - 429 của E15, E17: header `Retry-After` và trường `retryAfter` (giây);
  - 422 `EMAIL_CODE_INVALID`: trường `remainingAttempts`;
  - lỗi đồng bộ Keycloak ở `/me/email/change/confirm`: 503.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/identity/api/AuthController.java` (thêm `register`; **đụng ID-2**, lane merge sau rebase, B9)
- `backend/src/main/java/vn/unicom/thi360/identity/api/MeEmailController.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/api/EmailCode*.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/api/RegisterRe*.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/identity/application/SelfRegistration*.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/application/EmailCode*.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/application/EmailChange*.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/application/DevEmailCode*.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/identity/domain/EmailCode*.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/infrastructure/EmailCode*.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/identity/domain/UserAccount.java` (thêm cột, giữ nguyên hàm dựng hiện có)
- `backend/src/main/java/vn/unicom/thi360/identity/application/AuthService.java` (mở hàm tạo phiên cho đăng ký)
- `backend/src/main/java/vn/unicom/thi360/identity/application/UserAdminService.java` (tách các bước bù để dùng lại, không đổi hành vi)
- `backend/src/main/resources/db/migration/identity/V<yyyyMMddHHmm>__*.sql` (mới)
- `backend/src/test/java/vn/unicom/thi360/identity/**` (test mới của task)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java` (mở `POST /api/v1/auth/register`; **đụng ID-2**, lane merge sau rebase, B9)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/CookieAuthFilter.java` (`SKIP_EXACT_PATHS`; đụng ID-2; B9: báo Advisor trước khi sửa)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/CsrfFilter.java` (miễn CSRF cho `register`; đụng ID-2; B9: báo Advisor trước khi sửa)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/RateLimitFilter.java`, `backend/src/main/java/vn/unicom/thi360/shared/security/SecurityProperties.java` (chính sách `register`, nhiều cửa sổ, thời gian sống của khóa; đụng ID-2; B9: báo Advisor trước khi sửa)
- thêm vào: `backend/src/test/java/vn/unicom/thi360/shared/security/ClientIpResolverTests.java` (hàm dựng `RateLimitProperties`), `backend/src/test/java/vn/unicom/thi360/shared/security/RateLimitFilterIT.java` (ca nhiều cửa sổ)
- thêm vào: `backend/src/main/resources/application.yml` (ngưỡng E19, `THI360_DEV_EMAIL_CODE` trong khối `on-profile: dev`), `.env.example` (tên biến)
- thêm vào: `backend/src/main/resources/messages.properties`, `backend/src/main/resources/messages_vi.properties`, `backend/src/main/resources/messages_en.properties` (mã lỗi mới)
- sinh lại sau rebase: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts` (B9)

**Ngoài phạm vi:** các màn đăng ký, xác minh, đổi email (FS-2); `proxy.ts` của web học viên (FS-1); quên mật khẩu, hồ sơ, ảnh đại diện (ID-2); bảng thư, worker gửi thư, mẫu thư, `MailOutbox`, `/api/v1/test/mail` (ML); `THI360_TOKEN_PEPPER`, cột `preferred_locale` và việc ghi cột này khi đăng nhập (C-0); khối `on-profile: e2e` trong `application.yml` (C-0b); điều kiện chỉ gửi thư đơn hàng cho email đã xác minh (CM-3a, CM-5); tài liệu triển khai `THI360_TRUST_REAL_IP` (QA). Q13 không có mã riêng: chủ thật liên hệ hỗ trợ, admin sửa email bằng màn có sẵn.

**Nghiệm thu:**

- AC-1: 20 yêu cầu đăng ký cùng tên đăng nhập chạy đồng thời: đúng một thành công, 19 yêu cầu còn lại nhận 409 `USERNAME_TAKEN`, DB và Keycloak mỗi nơi có đúng một tài khoản (mục AC 06.01 của đặc tả) — bằng chứng: `SelfRegistrationIT` (mới).
- AC-2: Email trùng tài khoản chưa xóa nhận 409 `EMAIL_TAKEN`, kể cả hai yêu cầu đồng thời khác tên đăng nhập, cùng email — bằng chứng: `SelfRegistrationIT` (mới).
- AC-3: Đăng ký thành công tạo tài khoản `ACTIVE`, `source = SELF_REGISTERED`, vai trò Học viên, đủ `terms_version`, `terms_accepted_at`, `preferred_locale` theo cookie `thi360_locale`, nhật ký `USER_SELF_REGISTERED`, một thư `EMAIL_VERIFY` — bằng chứng: `SelfRegistrationIT` (mới).
- AC-4: Đăng ký thành công đặt cookie phiên HttpOnly, phiên dùng được ngay ở `GET /api/v1/me`; `redirectTo` là `callbackUrl` hợp lệ, `callbackUrl` trỏ ra ngoài hoặc thiếu thì là `/home` — bằng chứng: `SelfRegistrationIT` (mới).
- AC-5: Tên đăng nhập sai `UsernamePolicy`, mật khẩu sai chính sách, `acceptTerms = false` đều bị từ chối kèm lỗi theo trường; DB và Keycloak không có tài khoản, không có thư — bằng chứng: `SelfRegistrationIT` (mới).
- AC-6: Keycloak lỗi khi tạo user hoặc khi đặt mật khẩu thì không còn tài khoản dở dang: không có dòng `user_account` đang dùng, không có user Keycloak, không có phiên, không có thư — bằng chứng: `SelfRegistrationIT` (mới).
- AC-7: Dòng `PROVISIONING` của đăng ký kẹt quá 10 phút được `ProvisioningReconcilerJob` hoàn tất với vai trò Học viên, không bị xóa vì thiếu vai trò — bằng chứng: `SelfRegistrationIT` (mới).
- AC-8: Mỗi IP: lần 31 trong một giờ và lần 101 trong một ngày nhận 429 `RATE_LIMITED` có `Retry-After`; IP khác không bị ảnh hưởng; khóa Redis của cửa sổ ngày không hết hạn sau 2 phút (E19) — bằng chứng: `SelfRegistrationRateLimitIT` (mới).
- AC-9: `register` gọi được khi không có cookie, và khi trình duyệt còn `thi360_at` hết hạn hoặc `thi360_rt` cũ mà không có XSRF (không 401, không 403 `CSRF_INVALID`) — bằng chứng: `SelfRegistrationIT` (mới).
- AC-10: OTP đúng đặt `email_verified_at`, ghi nhật ký `EMAIL_VERIFIED`, mã không dùng lại được — bằng chứng: `EmailVerificationIT` (mới).
- AC-11: OTP sai nhận 422 `EMAIL_CODE_INVALID`, `remainingAttempts` giảm dần 4, 3, 2, 1, 0 — bằng chứng: `EmailVerificationIT` (mới).
- AC-12: OTP hết hạn sau 10 phút hoặc đã dùng nhận 422 `EMAIL_CODE_EXPIRED` — bằng chứng: `EmailVerificationIT` (mới).
- AC-13: Sau 5 lần sai, lần nhập tiếp theo nhận 422 `EMAIL_CODE_EXPIRED` kể cả khi mã đúng; `email_verified_at` vẫn trống — bằng chứng: `EmailVerificationIT` (mới).
- AC-14: Gửi lại trước 60 giây nhận 429 `RATE_LIMITED` có `retryAfter`, mã cũ vẫn dùng được; gửi lại sau 60 giây nhận 202, mã cũ mất hiệu lực — bằng chứng: `EmailVerificationIT` (mới).
- AC-15: Lần gửi thứ 6 trong ngày nhận 429 `RATE_LIMITED` có `retryAfter`, không có thư mới — bằng chứng: `EmailVerificationIT` (mới).
- AC-16: DB chỉ giữ `code_hash` HMAC-SHA256, không có mã rõ; phản hồi API và log không chứa mã; mỗi tài khoản và mục đích chỉ có một mã còn hiệu lực — bằng chứng: `EmailVerificationIT` (mới).
- AC-17: Bốn API `/me/email/**` không có phiên nhận 401; mã của tài khoản A do tài khoản B gửi nhận 422 và không đổi trạng thái của A (đường dẫn không có id nên không có ca 404) — bằng chứng: `EmailVerificationIT` (mới).
- AC-18: Tài khoản chưa xác minh email vẫn đăng nhập được (E18) — bằng chứng: `EmailVerificationIT` (mới).
- AC-19: `/me/email/change` đúng mật khẩu gửi mã tới email mới, ghi `pending_email`, nhật ký `EMAIL_CHANGE_REQUESTED`, `email` chưa đổi — bằng chứng: `EmailChangeIT` (mới).
- AC-20: `/me/email/change` sai mật khẩu bị từ chối, không tạo mã, `pending_email` giữ nguyên; tài khoản realm `master` nhận 403 — bằng chứng: `EmailChangeIT` (mới).
- AC-21: Trước khi xác nhận, email cũ vẫn đăng nhập được và vẫn là email khôi phục, email mới chưa đăng nhập được (mục AC 06.05 của đặc tả) — bằng chứng: `EmailChangeIT` (mới).
- AC-22: Email mới đã có tài khoản nhận 409 `EMAIL_TAKEN` ở `/change`; email bị tài khoản khác lấy trong lúc chờ nhận 409 `EMAIL_TAKEN` ở `/change/confirm`, `email` giữ nguyên — bằng chứng: `EmailChangeIT` (mới).
- AC-23: Xác nhận đúng mã: `email` thành email mới, `email_verified_at` có giá trị, `pending_email` trống, Keycloak có email mới, thư `EMAIL_CHANGED_NOTICE` tới địa chỉ cũ, nhật ký `EMAIL_CHANGED`, các phiên đang có vẫn dùng được — bằng chứng: `EmailChangeIT` (mới).
- AC-24: Keycloak lỗi khi xác nhận: 503, `email` và `pending_email` giữ nguyên, mã chưa bị đánh dấu dùng, không có thư báo — bằng chứng: `EmailChangeIT` (mới).
- AC-25: Mã đổi email theo cùng quy tắc E15: 10 phút, 5 lần sai, gửi lại sau 60 giây, 5 lần mỗi ngày (E17) — bằng chứng: `EmailChangeIT` (mới).
- AC-26: Profile `dev` có `THI360_DEV_EMAIL_CODE` thì mã cố định được chấp nhận; không đặt biến thì mã là ngẫu nhiên — bằng chứng: `DevEmailCodeGuardTests` (mới).
- AC-27: Đặt `THI360_DEV_EMAIL_CODE` ở profile khác `dev` thì ứng dụng không khởi động — bằng chứng: `DevEmailCodeGuardTests` (mới).
- AC-28: Hai yêu cầu cùng mã đúng chạy đồng thời: một thành công, một nhận 422 `EMAIL_CODE_EXPIRED`; các lần sai đồng thời không làm bộ đếm vượt quá 5 lần cho phép — bằng chứng: `EmailVerificationIT` (mới).
- AC-29: Mọi API mới có trong OpenAPI kèm `retryAfter`, `remainingAttempts`; mã lỗi mới có ở cả ba tệp `messages*.properties`; mật khẩu không xuất hiện trong `toString` của request — bằng chứng: `OpenApiExportIT`, `MessageBundlesTest`, `CredentialRequestToStringTests`.

**Ảnh hưởng ngoài task:**

- `AuthController` do ID-1 và ID-2 cùng sửa (`backend/src/main/java/vn/unicom/thi360/identity/api/AuthController.java`) → lane merge sau rebase rồi chạy `AuthFlowIT`, `AuthRedirectAndLogoutCookieIT` [chủ: task này]
- `SecurityConfig` do ID-1 và ID-2 cùng sửa (`backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java`) → lane merge sau rebase rồi chạy `SessionIT`, `MultiIssuerIT` [chủ: task này]
- Danh sách bỏ qua cookie (`CookieAuthFilter.SKIP_EXACT_PATHS`) → chạy `ExpiredTokenLogoutIT`, `AuthRedirectAndLogoutCookieIT` [chủ: task này]
- Miễn CSRF (`backend/src/main/java/vn/unicom/thi360/shared/security/CsrfFilter.java`) → chạy `SessionIT`: các ca CSRF hiện có vẫn trả 403 [chủ: task này]
- Giới hạn đăng nhập và tải tệp dùng chung bộ lọc (`backend/src/main/java/vn/unicom/thi360/shared/security/RateLimitFilter.java`), cùng danh sách IP miễn của C-0 → chạy `RateLimitFilterIT`, `LoginThrottleIT` sau rebase [chủ: task này]
- Hàm dựng record đổi tham số (`SecurityProperties.RateLimitProperties`, gọi ở `backend/src/test/java/vn/unicom/thi360/shared/security/ClientIpResolverTests.java:15`) → sửa lời gọi, chạy `ClientIpResolverTests` [chủ: task này]
- Tạo phiên dùng chung với đăng nhập (`backend/src/main/java/vn/unicom/thi360/identity/application/AuthService.java`) → chạy `AuthFlowIT`, `SessionIT` [chủ: task này]
- Bước bù của tạo tài khoản, lớp này C-0 cũng sửa (`backend/src/main/java/vn/unicom/thi360/identity/application/UserAdminService.java`) → chạy `UserAccountAdminIT`, `ImportBatchAdminIT` [chủ: task này]
- Dọn tài khoản kẹt (`backend/src/main/java/vn/unicom/thi360/identity/application/ProvisioningReconcilerJob.java`) → chạy `ProvisioningReconcilerWorkerTests`, `UserAccountAdminIT` [chủ: task này]
- Keycloak trả 409 luôn được hiểu là trùng tên đăng nhập (`KeycloakIdentityClient.createUser`, dòng 110–112) → ca email trùng của AC-2 phải nhận `EMAIL_TAKEN` [chủ: task này]
- Cột mới trên bảng nhiều nơi đọc (`backend/src/main/java/vn/unicom/thi360/identity/domain/UserAccount.java`) → chạy `StudentProfileIT`, `DevDataInitializerIT` với `ddl-auto: validate` [chủ: task này]
- Admin sửa email xóa `email_verified_at` (`UserAccount.updateProfile`, `UserAdminService.updateUser`) → chạy `UserAccountAdminIT`; trường hợp đang có `pending_email` theo Câu hỏi mở [chủ: task này]
- Lớp học đọc email qua cổng (`backend/src/main/java/vn/unicom/thi360/identity/application/UserDirectoryImpl.java:152`) → ca mới trong `EmailChangeIT`: sau khi xác nhận, `UserDirectory` trả email mới [chủ: task này]
- Danh sách người dùng của admin lọc nguồn `SELF_REGISTERED` (`backend/src/main/java/vn/unicom/thi360/identity/api/AdminUserController.java:47`, `apps/web-admin/src/features/users/components/users-list-view.tsx:102`) → ca mới trong `SelfRegistrationIT`: tài khoản vừa đăng ký có trong kết quả lọc; chạy `users-list.test.tsx` [chủ: task này]
- Màn admin hiện email (`apps/web-admin/src/features/users/components/user-detail-view.tsx:415`, `apps/web-admin/src/features/classes/components/add-members-dialog.tsx:355`) → API admin không đổi dạng, chạy `user-detail-and-actions.test.tsx` [chủ: task này]
- Đăng nhập trên web sau khi đổi bộ lọc (`e2e/tests/sso.spec.ts`, `e2e/tests/session-refresh.spec.ts`) → chạy hai spec này [chủ: task này]
- Khởi động ứng dụng và ranh giới module (`backend/src/test/java/vn/unicom/thi360/ModularityTest.java`) → chạy `ModularityTest` [chủ: task này]
- Migration trên DB của đợt 2 (`backend/src/main/resources/db/migration/identity/`) → ca dữ liệu cũ ở Kiểm thử rủi ro [chủ: task này]
- Tệp thông điệp (`backend/src/main/resources/messages.properties`) → chạy `MessageBundlesTest`, `LocaleErrorLocalizationIT` [chủ: task này]
- API client (`packages/api-client/openapi.json`) → chạy `OpenApiExportIT`, so khớp và sinh lại client [chủ: task này]
- Email cũ vẫn khôi phục được (mục AC 06.05 của đặc tả) qua `forgot` (`POST /api/v1/auth/password/forgot`) → ca `pending_email`: `forgot` theo email cũ có thư, theo email đang chờ không có thư; lane nào trong ID-1, ID-2 merge sau thì viết ca này vào `PasswordForgotIT` (mới) [chủ: ID-2]
- Mẫu thư `EMAIL_VERIFY`, `EMAIL_CHANGE`, `EMAIL_CHANGED_NOTICE` và `MailQueue.enqueue` (`shared/mail/**`) → kiểm mẫu lúc khởi động và test của ML [chủ: ML]
- `THI360_TOKEN_PEPPER`, cột `preferred_locale` (`backend/src/main/resources/application.yml`) → thiếu pepper thì lỗi khởi động, theo nghiệm thu của C-0 [chủ: C-0]
- Web dùng hợp đồng đăng ký và lỗi OTP (`retryAfter`, `remainingAttempts`) → E2E đăng ký và OTP sai của FS-2 [chủ: FS-2]
- Thư đơn hàng chỉ gửi khi email đã xác minh, đọc `email_verified_at` (`ORDER_FULFILLED`) → test của CM-3a [chủ: CM-3a]
- Thư hoàn tiền chỉ gửi khi email đã xác minh, đọc `email_verified_at` (`REFUND_DONE`) → test của CM-5 [chủ: CM-5]
- Giới hạn theo IP qua proxy web cần `THI360_TRUST_REAL_IP=1` (`packages/web-kit/src/proxy.ts:71`) → tài liệu triển khai ghi điều kiện này [chủ: QA]

**Kế hoạch kiểm thử:**

- AC-1: `SelfRegistrationIT#twentyConcurrentSameUsername`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=SelfRegistrationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `SelfRegistrationIT#duplicateEmail`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=SelfRegistrationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `SelfRegistrationIT#createsStudentAccount`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=SelfRegistrationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-4: `SelfRegistrationIT#createsSessionAndResolvesRedirect`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=SelfRegistrationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `SelfRegistrationIT#rejectsInvalidInput`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=SelfRegistrationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `SelfRegistrationIT#keycloakFailureLeavesNothing` (spy `KeycloakIdentityClient`, Keycloak thật cho phần còn lại), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=SelfRegistrationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `SelfRegistrationIT#reconcilerCompletesStuckRegistration`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=SelfRegistrationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-8: `SelfRegistrationRateLimitIT` (Redis thật; ngưỡng mặc định kiểm bằng giá trị cấu hình, ngưỡng nhỏ để chạm cả hai cửa sổ), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=SelfRegistrationRateLimitIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `SelfRegistrationIT#publicWithStaleCookies`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=SelfRegistrationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `EmailVerificationIT#correctCode`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailVerificationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `EmailVerificationIT#wrongCodeCountsDown`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailVerificationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: `EmailVerificationIT#expiredOrUsedCode`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailVerificationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-13: `EmailVerificationIT#deadAfterFiveWrong`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailVerificationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-14: `EmailVerificationIT#resendCooldown`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailVerificationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `EmailVerificationIT#dailySendLimit`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailVerificationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-16: `EmailVerificationIT#storesOnlyHmacAndOneLiveCode` (đọc bảng, thư qua `MailOutbox`, log qua `OutputCaptureExtension`), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailVerificationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-17: `EmailVerificationIT#requiresSessionAndOwnCode`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailVerificationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-18: `EmailVerificationIT#unverifiedCanLogin`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailVerificationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-19: `EmailChangeIT#requestStoresPending`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailChangeIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-20: `EmailChangeIT#wrongPasswordAndMasterRealmRefused`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailChangeIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-21: `EmailChangeIT#oldEmailStaysUntilConfirmed`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailChangeIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-22: `EmailChangeIT#duplicateAtRequestAndConfirm`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailChangeIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-23: `EmailChangeIT#confirmAppliesEverywhere`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailChangeIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-24: `EmailChangeIT#keycloakFailureRollsBack`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailChangeIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-25: `EmailChangeIT#changeCodeFollowsSameLimits`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailChangeIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-26: `DevEmailCodeGuardTests#devProfileUsesFixedCode`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=DevEmailCodeGuardTests`
- AC-27: `DevEmailCodeGuardTests#otherProfilesFailStartup` (`ApplicationContextRunner`, khởi động context thật của lớp chặn), tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=DevEmailCodeGuardTests`
- AC-28: `EmailVerificationIT#concurrentAttempts` (DB thật), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EmailVerificationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-29: `OpenApiExportIT`, hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `MessageBundlesTest`, `CredentialRequestToStringTests`, unit, `cd backend && ./mvnw -B -ntp test -Dtest='MessageBundlesTest,CredentialRequestToStringTests'`
- Hồi quy:
  - `AuthController`, `AuthService`, bộ lọc: `cd backend && ./mvnw -B -ntp verify -Dit.test='AuthFlowIT,AuthRedirectAndLogoutCookieIT,SessionIT,MultiIssuerIT,ExpiredTokenLogoutIT,LoginThrottleIT,RateLimitFilterIT' -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `UserAdminService`, dọn tài khoản kẹt, cột mới: `cd backend && ./mvnw -B -ntp verify -Dit.test='UserAccountAdminIT,ImportBatchAdminIT,StudentProfileIT,DevDataInitializerIT,LocaleErrorLocalizationIT' -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - Unit dùng chung: `cd backend && ./mvnw -B -ntp test -Dtest='ClientIpResolverTests,ProvisioningReconcilerWorkerTests,ModularityTest,MessageBundlesTest'`
  - Toàn bộ backend, gồm ca lọc `SELF_REGISTERED` và ca `UserDirectory` mới: `cd backend && ./mvnw -B -ntp verify`
  - Màn admin: `pnpm --filter @thi360/web-admin test -- src/features/users/__tests__/users-list.test.tsx src/features/users/__tests__/user-detail-and-actions.test.tsx`
  - Đăng nhập trên web: `pnpm --filter @thi360/e2e test -- tests/sso.spec.ts tests/session-refresh.spec.ts`
  - API client: `diff -u packages/api-client/openapi.json backend/target/openapi.json && pnpm --filter @thi360/api-client generate && git diff --exit-code packages/api-client`
  - Dòng có chủ là ID-2, ML, C-0, FS-2, CM-3a, CM-5, QA do task đó chạy.
- Kiểm thử rủi ro:
  - Quyền: mỗi API `/me/email/**` có phiên học viên và không có phiên; tài khoản realm `master`; đăng ký không cần quyền nào. API này không gắn `@RequirePermission`, không có ca từng quyền riêng lẻ.
  - Dùng lại và đồng thời: 20 đăng ký cùng tên, 2 đăng ký cùng email, 2 lần xác minh cùng mã, nhiều lần sai cùng lúc, dùng lại mã đã dùng, xác nhận đổi email hai lần.
  - Migration trên DB đã có dữ liệu: `EmailChangeIT#existingAccountsSurviveMigration` chạy toàn bộ migration cùng dữ liệu mẫu; tài khoản cũ có ba cột mới trống, vẫn đăng nhập được, không bị coi là đã chấp nhận điều khoản.
  - Biên: mã còn 1 giây và vừa hết 10 phút; gửi lại ở giây 59 và 60; lần sai thứ 5 và lần nhập thứ 6; lần gửi thứ 5 và thứ 6; đăng ký lần 30 và 31, lần 100 và 101; tên đăng nhập 4 và 30 ký tự, 3 và 31 ký tự.
  - Đỏ trước: AC-1, AC-6, AC-7, AC-8, AC-13, AC-24, AC-28.
- Dữ liệu test: tài khoản tạo trong từng test với tên có hậu tố ngẫu nhiên, dọn DB, Keycloak và khóa Redis ở `@AfterEach` theo mẫu `NoHardDeleteIT`; thư đọc qua `MailOutbox` của ML; IP đặt bằng header ở tầng backend (B6), mỗi test một IP; hạn mã đặt bằng cách sửa `expires_at` qua JDBC; mỗi lane có Testcontainers riêng.
- Không kiểm: gửi SMTP thật (ML); giao diện (FS-2); mua hàng khi email chưa xác minh (phần "mua được" của E18 thuộc CM-3a); commit DB lỗi sau khi Keycloak đã nhận email mới, vì spec không nêu cách bù và `UserAdminService.updateUser` cũng có giới hạn này; quy trình hỗ trợ của Q13 (không có mã).

**Câu hỏi mở:** M.Trường quyết: (1) tài khoản đã tạo xong nhưng tạo phiên lỗi: tạm giữ tài khoản, trả lỗi, người dùng đăng nhập lại; (2) "ngày" ở E15, E19: tạm tính 24 giờ trượt; (3) 5 lần mỗi ngày đếm riêng theo mục đích `VERIFY`, `CHANGE` hay đếm chung: tạm đếm riêng; (4) admin sửa email khi tài khoản đang có `pending_email`: tạm giữ `pending_email`, bước xác nhận kiểm tra trùng lại; (5) gọi `/me/email/verification` khi tài khoản chưa có email: tạm trả 422; (6) đổi sang chính email hiện tại: tạm trả 409 `EMAIL_TAKEN`; (7) `termsVersion` khác bản hiện hành: tạm lưu đúng giá trị gửi lên.

## ID-2 · Quên và đặt lại mật khẩu, hồ sơ, ảnh đại diện

**Spec:** TM 5.4, 5.5, 13, E16, E20, E27, C9; plan B2, B6, B7, B9.

**Sản phẩm:**

- Bảng `identity.password_reset_token`, mã HMAC (pepper của C-0). API `/auth/password/forgot`, `/auth/password/reset` (công khai):
  - cùng phản hồi cho mọi email;
  - việc tra tài khoản và xếp thư chạy bất đồng bộ, sau khi đã trả phản hồi, trên một bộ thực thi riêng có giới hạn hàng đợi (theo mẫu `ExamCheckExecutorConfiguration`); tạo mã và `MailQueue.enqueue` cùng một transaction;
  - chỉ áp cho realm `thi-360`, gồm cả quản trị viên của trung tâm (C9);
  - đặt lại thì thu hồi mọi phiên;
  - không mở khóa tài khoản bị khóa.
- Giới hạn E16: mỗi tài khoản 1 lần mỗi 60 giây và 5 lần mỗi ngày (vượt thì vẫn 202, không có thư); mỗi IP 60 lần mỗi phút qua `RateLimitFilter`.
- `GET/PATCH /me/profile`, `PUT/DELETE /me/avatar`, `GET /api/v1/avatars/{assetId}`.
- Bảng `identity.user_avatar`, bucket `thi360-avatars`, ảnh PNG 256px (B7). Sửa TM E20 trong PR.
- Bucket ảnh đại diện dùng bean `ObjectStorage` riêng khai báo `defaultCandidate = false`, để các nơi đang tiêm `ObjectStorage` không bị nhập nhằng.
- Lỗi ảnh đại diện làm bằng advice riêng `identity/api/AvatarExceptionHandler` (mới) theo mẫu `QuestionAssetExceptionHandler`: 413 `AVATAR_TOO_LARGE`, 422 `AVATAR_INVALID_IMAGE`, 422 `AVATAR_DIMENSIONS_EXCEEDED`.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/identity/api/AuthController.java` (thêm `forgot`, `reset`; **đụng ID-1**, lane merge sau rebase, B9)
- `backend/src/main/java/vn/unicom/thi360/identity/api/MeProfileController.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/api/AvatarController.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/api/AvatarExceptionHandler.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/api/Password*Request.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/api/Profile*.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/identity/application/PasswordReset*.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/application/Profile*.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/application/Avatar*.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/identity/domain/PasswordResetToken.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/domain/UserAvatar.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/identity/infrastructure/PasswordResetToken*.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/infrastructure/UserAvatar*.java` (mới), `backend/src/main/java/vn/unicom/thi360/identity/infrastructure/AvatarStorage*.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/identity/application/UserAdminService.java` (mở hàm thu hồi phiên để dùng lại, không đổi hành vi)
- `backend/src/main/resources/db/migration/identity/V<yyyyMMddHHmm>__*.sql` (mới)
- `backend/src/test/java/vn/unicom/thi360/identity/**` (test mới của task)
- `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md` (chỉ dòng E20)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java` (mở `forgot`, `reset`; **đụng ID-1**, lane merge sau rebase, B9)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/CookieAuthFilter.java` (`SKIP_EXACT_PATHS`; đụng ID-1; B9: báo Advisor trước khi sửa)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/CsrfFilter.java` (miễn CSRF cho `forgot`, `reset`; đụng ID-1; B9: báo Advisor trước khi sửa)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/RateLimitFilter.java`, `backend/src/main/java/vn/unicom/thi360/shared/security/SecurityProperties.java` (chính sách `forgot`, `reset`; đụng ID-1; B9: báo Advisor trước khi sửa)
- thêm vào: `backend/src/test/java/vn/unicom/thi360/shared/security/ClientIpResolverTests.java` (hàm dựng `RateLimitProperties`)
- thêm vào: `backend/pom.xml` (`net.coobird:thumbnailator`, B7)
- thêm vào: `backend/src/main/resources/application.yml` (ngưỡng E16, tên bucket ảnh đại diện), `.env.example` (tên biến)
- thêm vào: `backend/src/main/resources/messages.properties`, `backend/src/main/resources/messages_vi.properties`, `backend/src/main/resources/messages_en.properties` (mã lỗi mới)
- sinh lại sau rebase: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts` (B9)

**Ngoài phạm vi:** các màn quên mật khẩu, đặt lại mật khẩu, hồ sơ và header `Referrer-Policy: no-referrer` của `/reset-password` (FS-2); `proxy.ts` của web học viên (FS-1); đăng ký, xác minh và đổi email (ID-1); bảng thư, worker gửi thư, mẫu thư `PASSWORD_RESET`, `MailOutbox` (ML); `THI360_TOKEN_PEPPER` (C-0); tạo bucket trên môi trường triển khai và tài liệu `THI360_TRUST_REAL_IP` (QA); đổi mật khẩu khi đã đăng nhập (trang `/change-password` có sẵn).

**Nghiệm thu:**

- AC-1: `forgot` cho email có tài khoản, không có tài khoản, tài khoản khóa, đã xóa, chưa xác minh và realm `master`: cùng 202, cùng thân (AC-06.04) — bằng chứng: `PasswordForgotIT` (mới).
- AC-2: Test cấu trúc (mock) cho thấy controller trả phản hồi trước khi tra tài khoản, cả hai trường hợp đi cùng một đường mã tới lúc trả — bằng chứng: `PasswordForgotDispatchTests` (mới).
- AC-3: Hàng đợi tác vụ nền đầy thì `forgot` vẫn trả 202 cùng thân, không có lỗi 5xx — bằng chứng: `PasswordForgotDispatchTests` (mới).
- AC-4: Chỉ tài khoản `ACTIVE`, chưa xóa, đã xác minh của realm `thi-360` nhận đúng một thư `PASSWORD_RESET` có liên kết `/reset-password?token=…`, kể cả quản trị viên của trung tâm; tài khoản khóa, đã xóa, chưa xác minh không có mã và không có thư — bằng chứng: `PasswordForgotIT` (mới).
- AC-5: Tài khoản realm `master` không nhận thư (E27) — bằng chứng: `PasswordForgotIT` (mới).
- AC-6: `MailQueue.enqueue` lỗi thì không còn dòng `password_reset_token` (cùng transaction) — bằng chứng: `PasswordForgotIT` (mới).
- AC-7: DB chỉ giữ `token_hash` HMAC, có `request_ip`; mã rõ không có trong DB và log — bằng chứng: `PasswordForgotIT` (mới).
- AC-8: Mỗi tài khoản: yêu cầu thứ hai trong 60 giây vẫn 202 cùng thân, không có thư mới, mã trước vẫn dùng được (E16) — bằng chứng: `PasswordForgotIT` (mới).
- AC-9: Mỗi tài khoản: yêu cầu thứ 6 trong ngày vẫn 202 cùng thân, không có thư mới (E16) — bằng chứng: `PasswordForgotIT` (mới).
- AC-10: Mỗi IP: lần gọi `forgot` thứ 61 trong một phút nhận 429 `RATE_LIMITED` có `Retry-After`, IP khác không bị ảnh hưởng (E16) — bằng chứng: `PasswordResetRateLimitIT` (mới).
- AC-11: `reset` có giới hạn tần suất theo IP: vượt ngưỡng cấu hình nhận 429 `RATE_LIMITED` (TM 13) — bằng chứng: `PasswordResetRateLimitIT` (mới).
- AC-12: `forgot`, `reset` gọi được khi không có cookie, và khi trình duyệt còn `thi360_at` hết hạn hoặc `thi360_rt` cũ mà không có XSRF — bằng chứng: `PasswordResetIT` (mới).
- AC-13: Đặt lại đúng mã: 204, mật khẩu mới đăng nhập được, mật khẩu cũ không đăng nhập được, mã đã dùng, nhật ký `PASSWORD_RESET`, phản hồi không đặt cookie phiên — bằng chứng: `PasswordResetIT` (mới).
- AC-14: Mã đã dùng nhận 422 `RESET_TOKEN_INVALID` — bằng chứng: `PasswordResetIT` (mới).
- AC-15: Mã quá 1 giờ nhận 422 `RESET_TOKEN_INVALID` — bằng chứng: `PasswordResetIT` (mới).
- AC-16: Yêu cầu mới làm mã cũ mất hiệu lực: mã cũ nhận 422 `RESET_TOKEN_INVALID`, mã mới dùng được — bằng chứng: `PasswordResetIT` (mới).
- AC-17: Hai yêu cầu `reset` cùng mã chạy đồng thời: đúng một 204, một 422 `RESET_TOKEN_INVALID` — bằng chứng: `PasswordResetIT` (mới).
- AC-18: Sau khi đặt lại, phiên cũ không dùng được: access token và refresh token cũ đều bị từ chối, khóa phiên Redis đã xóa — bằng chứng: `PasswordResetIT` (mới).
- AC-19: Tài khoản bị khóa: mã bị hủy, 422 `RESET_TOKEN_INVALID`, tài khoản vẫn khóa, mật khẩu không đổi — bằng chứng: `PasswordResetIT` (mới).
- AC-20: Mật khẩu mới sai chính sách bị từ chối kèm lỗi theo trường, mã chưa bị đánh dấu dùng — bằng chứng: `PasswordResetIT` (mới).
- AC-21: Keycloak lỗi khi đặt mật khẩu: mã chưa bị đánh dấu dùng, phiên đang có giữ nguyên, không có nhật ký `PASSWORD_RESET` — bằng chứng: `PasswordResetIT` (mới).
- AC-22: `GET /me/profile` trả tên đăng nhập, họ tên, email, trạng thái xác minh, email đang chờ, số điện thoại, ảnh đại diện; `PATCH` không đổi được tên đăng nhập — bằng chứng: `MeProfileIT` (mới).
- AC-23: Họ tên bắt buộc, tối đa 100 ký tự: 100 ký tự đạt, 101 ký tự và chuỗi trống bị từ chối — bằng chứng: `MeProfileIT` (mới).
- AC-24: Số điện thoại không bắt buộc; 10 chữ số bắt đầu bằng 0 đạt, dạng khác bị từ chối; không gửi `phone` thì số cũ giữ nguyên, kể cả số cũ không theo dạng này — bằng chứng: `MeProfileIT` (mới).
- AC-25: Sửa hồ sơ, đổi và xóa ảnh ghi nhật ký có giá trị trước và sau, không có mật khẩu — bằng chứng: `MeProfileIT` (mới).
- AC-26: Sửa hồ sơ và đặt lại mật khẩu không đổi `user_account.id`, vai trò, `email_verified_at` (AC-06.06) — bằng chứng: `MeProfileIT` (mới).
- AC-27: `/me/profile`, `/me/avatar`, `/avatars/{assetId}` không có phiên nhận 401; tài khoản chỉ đổi được hồ sơ và ảnh của chính mình; `assetId` không tồn tại nhận 404 — bằng chứng: `MeProfileIT` (mới), `AvatarMinioIT` (mới).
- AC-28: Ảnh `jpg`, `png`, `webp` hợp lệ được cắt vuông, lưu PNG 256 × 256 vào bucket `thi360-avatars`, không nằm trong bucket `thi360-private`; `GET /api/v1/avatars/{assetId}` trả đúng ảnh `image/png` — bằng chứng: `AvatarMinioIT` (mới).
- AC-29: Ảnh 6 MB nhận 413; ảnh đúng 5 MB được nhận — bằng chứng: `AvatarMinioIT` (mới).
- AC-30: Ảnh giả đuôi nhận 422 — bằng chứng: `AvatarMinioIT` (mới).
- AC-31: Ảnh 8.000 × 8.000 px nhận 422, bị từ chối từ thông tin kích thước trước khi giải mã toàn ảnh; ảnh 4.096 × 4.096 px được nhận, 4.097 px nhận 422 — bằng chứng: `AvatarImageProcessorTests` (mới), `AvatarMinioIT` (mới).
- AC-32: Thay ảnh xóa đối tượng cũ; `DELETE /me/avatar` xóa dòng và đối tượng, `assetId` cũ nhận 404 — bằng chứng: `AvatarMinioIT` (mới).
- AC-33: Lưu ảnh lên MinIO lỗi thì ảnh đại diện hiện tại giữ nguyên — bằng chứng: `AvatarMinioIT` (mới).
- AC-34: TM E20 ghi ảnh lưu PNG 256px — bằng chứng: `git grep -n "E20" -- docs/specs/2026-11-16-thuong-mai-pbi-05-09.md`.
- AC-35: Mọi API mới có trong OpenAPI; mã lỗi mới có ở cả ba tệp `messages*.properties`; mật khẩu không xuất hiện trong `toString` của request — bằng chứng: `OpenApiExportIT`, `MessageBundlesTest`, `CredentialRequestToStringTests`.

**Ảnh hưởng ngoài task:**

- `AuthController` do ID-1 và ID-2 cùng sửa (`backend/src/main/java/vn/unicom/thi360/identity/api/AuthController.java`) → lane merge sau rebase rồi chạy `AuthFlowIT`, `AuthRedirectAndLogoutCookieIT` [chủ: task này]
- `SecurityConfig` do ID-1 và ID-2 cùng sửa (`backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java`) → lane merge sau rebase rồi chạy `SessionIT`, `MultiIssuerIT` [chủ: task này]
- Danh sách bỏ qua cookie (`CookieAuthFilter.SKIP_EXACT_PATHS`) → chạy `ExpiredTokenLogoutIT`, `AuthRedirectAndLogoutCookieIT` [chủ: task này]
- Miễn CSRF (`backend/src/main/java/vn/unicom/thi360/shared/security/CsrfFilter.java`) → chạy `SessionIT`: các ca CSRF hiện có vẫn trả 403 [chủ: task này]
- Giới hạn đăng nhập, tải tệp và đăng ký dùng chung bộ lọc (`backend/src/main/java/vn/unicom/thi360/shared/security/RateLimitFilter.java`) → chạy `RateLimitFilterIT`, `LoginThrottleIT` sau rebase [chủ: task này]
- Hàm dựng record đổi tham số (`SecurityProperties.RateLimitProperties`, gọi ở `backend/src/test/java/vn/unicom/thi360/shared/security/ClientIpResolverTests.java:15`) → sửa lời gọi, chạy `ClientIpResolverTests` [chủ: task này]
- Thu hồi phiên dùng chung với khóa tài khoản, lớp này C-0 và ID-1 cũng sửa (`UserAdminService.revokeAllSessionsFailClosed`, dòng 1319) → chạy `UserAccountAdminIT`, `SessionStoreIT` [chủ: task này]
- Hồ sơ dùng lại hàm sửa thông tin, hàm này xóa `email_verified_at` khi email đổi (`UserAccount.updateProfile`, dòng 140) → ca AC-26 giữ nguyên `email_verified_at`; chạy `UserAccountAdminIT` [chủ: task này]
- Số điện thoại do admin nhập theo quy tắc rộng hơn, tối đa 30 ký tự (`UserAdminService.normalizeAndValidatePhone`, dòng 1832) → ca "không gửi `phone`" của AC-24 [chủ: task này]
- Lớp học đọc họ tên, số điện thoại qua cổng (`backend/src/main/java/vn/unicom/thi360/identity/application/UserDirectoryImpl.java:152`) → ca mới trong `MeProfileIT`: sau khi sửa, `UserDirectory` trả giá trị mới [chủ: task này]
- Màn admin hiện họ tên, số điện thoại (`apps/web-admin/src/features/users/components/user-detail-view.tsx`, `apps/web-admin/src/features/users/components/users-list-view.tsx`) → API admin không đổi dạng, chạy `user-detail-and-actions.test.tsx`, `users-list.test.tsx` [chủ: task này]
- Năm nơi đang tiêm `ObjectStorage` (`EncryptedObjectStorage`, `QuestionAssetCleanupJob`, `QuestionAssetService`, `QuestionImportCleanupJob`, `QuestionImportCommitService`) → bean mới không làm nhập nhằng: chạy `ImportBatchMinioIT`, `QuestionImportCleanupIT` [chủ: task này]
- Quét route xóa tài khoản (`backend/src/test/java/vn/unicom/thi360/identity/NoHardDeleteIT.java`) → `DELETE /api/v1/me/avatar` không bị tính là route xóa tài khoản: chạy `NoHardDeleteIT` [chủ: task này]
- Thư viện mới (`backend/pom.xml`) → chạy `cd backend && ./mvnw -B -ntp verify` [chủ: task này]
- Khởi động ứng dụng và ranh giới module (`backend/src/test/java/vn/unicom/thi360/ModularityTest.java`) → chạy `ModularityTest` [chủ: task này]
- Migration trên DB của đợt 2 (`backend/src/main/resources/db/migration/identity/`) → ca dữ liệu cũ ở Kiểm thử rủi ro [chủ: task này]
- Tệp thông điệp (`backend/src/main/resources/messages.properties`) → chạy `MessageBundlesTest`, `LocaleErrorLocalizationIT` [chủ: task này]
- API client (`packages/api-client/openapi.json`) → chạy `OpenApiExportIT`, so khớp và sinh lại client [chủ: task này]
- Đăng nhập trên web sau khi đổi bộ lọc (`e2e/tests/sso.spec.ts`, `e2e/tests/session-refresh.spec.ts`) → chạy hai spec này [chủ: task này]
- Email cũ vẫn khôi phục được khi đang đổi email (AC-06.05, cột `pending_email` của ID-1) → ca `pending_email`: `forgot` theo email cũ có thư, theo email đang chờ không có thư; lane nào trong ID-1, ID-2 merge sau thì viết ca này vào `PasswordForgotIT` (mới) [chủ: task này]
- Mẫu thư `PASSWORD_RESET` và `MailQueue.enqueue` (`shared/mail/**`) → kiểm mẫu lúc khởi động và test của ML [chủ: ML]
- `THI360_TOKEN_PEPPER` (`backend/src/main/resources/application.yml`) → thiếu pepper thì lỗi khởi động, theo nghiệm thu của C-0 [chủ: C-0]
- Web dùng các API này và đặt `Referrer-Policy: no-referrer` cho `/reset-password` (`apps/web-student/next.config.ts`) → E2E quên, đặt lại mật khẩu của FS-2 [chủ: FS-2]
- Bucket `thi360-avatars` trên môi trường triển khai: `auto-create-bucket` mặc định `false` (`backend/src/main/resources/application.yml:106`) và compose không tạo bucket (`infra/docker-compose.yml`) → tài liệu triển khai ghi bước tạo bucket [chủ: QA]
- Giới hạn theo IP qua proxy web cần `THI360_TRUST_REAL_IP=1` (`packages/web-kit/src/proxy.ts:71`) → tài liệu triển khai ghi điều kiện này [chủ: QA]

**Kế hoạch kiểm thử:**

- AC-1: `PasswordForgotIT#sameResponseForEveryEmail`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordForgotIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `PasswordForgotDispatchTests#respondsBeforeLookup` (mock bộ thực thi và repository), unit, `cd backend && ./mvnw -B -ntp test -Dtest=PasswordForgotDispatchTests`
- AC-3: `PasswordForgotDispatchTests#fullQueueStillAccepted`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=PasswordForgotDispatchTests`
- AC-4: `PasswordForgotIT#mailOnlyForEligibleAccounts`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordForgotIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `PasswordForgotIT#masterRealmGetsNoMail`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordForgotIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `PasswordForgotIT#tokenAndMailShareTransaction` (spy `MailQueue`, DB thật), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordForgotIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `PasswordForgotIT#storesOnlyHmac`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordForgotIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-8: `PasswordForgotIT#accountCooldown`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordForgotIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `PasswordForgotIT#accountDailyLimit`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordForgotIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `PasswordResetRateLimitIT#forgotPerIp` (Redis thật), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetRateLimitIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `PasswordResetRateLimitIT#resetPerIp` (Redis thật), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetRateLimitIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: `PasswordResetIT#publicWithStaleCookies`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-13: `PasswordResetIT#resetChangesPassword`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-14: `PasswordResetIT#usedTokenRefused`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `PasswordResetIT#expiredTokenRefused`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-16: `PasswordResetIT#newRequestInvalidatesOld`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-17: `PasswordResetIT#concurrentResetSingleWinner` (DB thật), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-18: `PasswordResetIT#revokesAllSessions` (Redis, Keycloak thật), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-19: `PasswordResetIT#lockedAccountStaysLocked`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-20: `PasswordResetIT#weakPasswordKeepsToken`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-21: `PasswordResetIT#keycloakFailureKeepsToken` (spy `KeycloakIdentityClient`), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PasswordResetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-22: `MeProfileIT#readsProfileUsernameReadOnly`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=MeProfileIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-23: `MeProfileIT#fullNameBoundaries`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=MeProfileIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-24: `MeProfileIT#phoneRules`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=MeProfileIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-25: `MeProfileIT#auditsBeforeAndAfter`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=MeProfileIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-26: `MeProfileIT#identityAndVerificationUnchanged`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=MeProfileIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-27: `MeProfileIT#requiresSessionAndOwnData`, `AvatarMinioIT#requiresSessionAndUnknownAsset404`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test='MeProfileIT,AvatarMinioIT' -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-28: `AvatarMinioIT#storesSquarePngInAvatarBucket` (MinIO thật), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AvatarMinioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-29: `AvatarMinioIT#sizeBoundary`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AvatarMinioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-30: `AvatarMinioIT#fakeExtensionRefused`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AvatarMinioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-31: `AvatarImageProcessorTests#rejectsFromHeaderWithoutDecoding`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=AvatarImageProcessorTests`; `AvatarMinioIT#dimensionBoundary`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AvatarMinioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-32: `AvatarMinioIT#replaceAndDeleteRemoveObjects`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AvatarMinioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-33: `AvatarMinioIT#storageFailureKeepsCurrentAvatar`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AvatarMinioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-34: dòng E20 ghi `PNG`, hợp đồng, `git grep -n "E20" -- docs/specs/2026-11-16-thuong-mai-pbi-05-09.md`
- AC-35: `OpenApiExportIT`, hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `MessageBundlesTest`, `CredentialRequestToStringTests`, unit, `cd backend && ./mvnw -B -ntp test -Dtest='MessageBundlesTest,CredentialRequestToStringTests'`
- Hồi quy:
  - `AuthController`, bộ lọc, thu hồi phiên: `cd backend && ./mvnw -B -ntp verify -Dit.test='AuthFlowIT,AuthRedirectAndLogoutCookieIT,SessionIT,MultiIssuerIT,ExpiredTokenLogoutIT,LoginThrottleIT,RateLimitFilterIT,SessionStoreIT' -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `UserAdminService`, lưu trữ, quét route xóa: `cd backend && ./mvnw -B -ntp verify -Dit.test='UserAccountAdminIT,ImportBatchMinioIT,QuestionImportCleanupIT,NoHardDeleteIT,LocaleErrorLocalizationIT' -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - Unit dùng chung: `cd backend && ./mvnw -B -ntp test -Dtest='ClientIpResolverTests,ModularityTest,MessageBundlesTest'`
  - Toàn bộ backend, gồm ca `UserDirectory` mới và thư viện mới: `cd backend && ./mvnw -B -ntp verify`
  - Màn admin: `pnpm --filter @thi360/web-admin test -- src/features/users/__tests__/users-list.test.tsx src/features/users/__tests__/user-detail-and-actions.test.tsx`
  - Đăng nhập trên web: `pnpm --filter @thi360/e2e test -- tests/sso.spec.ts tests/session-refresh.spec.ts`
  - API client: `diff -u packages/api-client/openapi.json backend/target/openapi.json && pnpm --filter @thi360/api-client generate && git diff --exit-code packages/api-client`
  - Dòng có chủ là ML, C-0, FS-2, QA do task đó chạy.
- Kiểm thử rủi ro:
  - Quyền: mỗi API `/me/**` và `/avatars/{assetId}` có phiên học viên và không có phiên; tài khoản A không đổi được dữ liệu của tài khoản B; tài khoản realm `master` ở `forgot`. Các API này không gắn `@RequirePermission`, không có ca từng quyền riêng lẻ.
  - Dùng lại và đồng thời: dùng lại mã, 2 lần `reset` cùng mã, mã cũ sau yêu cầu mới, 2 lần `forgot` đồng thời cho một tài khoản chỉ có một mã còn hiệu lực, 2 lần thay ảnh đồng thời chỉ còn một đối tượng.
  - Migration trên DB đã có dữ liệu: `MeProfileIT#existingAccountsHaveEmptyAvatar` chạy toàn bộ migration cùng dữ liệu mẫu; tài khoản cũ đọc được hồ sơ, chưa có ảnh đại diện.
  - Biên: mã còn 1 giây và vừa hết 1 giờ; yêu cầu ở giây 59 và 60; yêu cầu thứ 5 và thứ 6 trong ngày; lần gọi thứ 60 và 61 trong phút; ảnh đúng 5 MB và 5 MB + 1 byte; 4.096 px và 4.097 px; họ tên 100 và 101 ký tự; số điện thoại 9, 10, 11 chữ số.
  - Đỏ trước: AC-1, AC-2, AC-8, AC-10, AC-17, AC-18, AC-19, AC-31.
- Dữ liệu test: tài khoản tạo trong từng test với tên có hậu tố ngẫu nhiên, dọn DB, Keycloak và khóa Redis ở `@AfterEach` theo mẫu `NoHardDeleteIT`; tài khoản realm `master` dùng `platform` của `AbstractIntegrationTest`; thư đọc qua `MailOutbox` của ML; tác vụ nền chờ bằng Awaitility; ảnh sinh trong test bằng `BufferedImage`; MinIO theo mẫu `ImportBatchMinioIT`; mỗi lane có Testcontainers riêng.
- Không kiểm: đo thời gian phản hồi thật của `forgot` (nghiệm thu dùng test cấu trúc, đo thời gian trên CI không ổn định); gửi SMTP thật (ML); giao diện và `Referrer-Policy` (FS-2); ảnh hưởng tới đơn và quyền dùng thật (AC-06.06) vì bảng đơn chưa có ở sóng này, AC-26 chứng minh `user_account.id` không đổi.

**Câu hỏi mở:** M.Trường quyết: (1) ngưỡng theo IP của `reset`: TM 13 yêu cầu có giới hạn nhưng không nêu số, tạm dùng 60 lần mỗi phút như `forgot`, đặt trong cấu hình; (2) `must_change_password` sau khi đặt lại: tạm giữ nguyên cờ; (3) "ngày" ở E16: tạm tính 24 giờ trượt.

## CM-1 · Bảng chiếu sản phẩm, API công khai

**Spec:** TM 3, 10.1, 10.2, 13, E2–E4, E11, E24; plan B1, B6, Phụ lục U-3; BS 2. **Phụ thuộc mềm:** DS.

**Sản phẩm:**

- Bảng `commerce.storefront_product` đủ cột TM 10.1. `search_text` chuẩn hóa như BE D16 bằng hàm riêng trong `commerce/storefront` (không import `questionbank/domain`, RULES 4).
- Bộ nhận sự kiện `ExamPublished`, `ExamDiscontinued`:
  - đăng ký bằng `OutboxRelay.register` khi khởi động, theo mẫu `AuditOutboxHandler`;
  - đọc lại dữ liệu qua `PublishedExamPort.findServing`, không tin thân sự kiện;
  - ghi dòng trong transaction `REQUIRES_NEW` riêng, vì relay chạy handler trong transaction của chính nó;
  - idempotent, không ghi lùi `version_no`.
- `POST /api/v1/admin/storefront/rebuild` (`EXAM.PUBLISH`): dựng lại mọi dòng từ `PublishedExamPort`, ghi nhật ký `STOREFRONT_REBUILT` (mới).
- API công khai `GET /api/v1/public/storefront/{filters,products,products/{code},covers/{assetId}}`:
  - `Cache-Control: public, max-age=60`, `ETag`, trả 304 khi `If-None-Match` khớp;
  - `size` mặc định 12, lớn hơn 48 thì lấy 48 (cùng cách `PageQuery` xử lý giá trị vượt trần);
  - 404 `PRODUCT_NOT_FOUND`;
  - 410 `PRODUCT_DISCONTINUED`: định dạng lỗi chung cộng trường `product: {code, name}`, vì `code` của `ApiError` đã là mã lỗi.
- Mở `GET /api/v1/public/**` trong `SecurityConfig`; `CookieAuthFilter` bỏ qua theo tiền tố `/api/v1/public/`, để cookie hết hạn không làm hỏng trang công khai.
- Giới hạn 120 yêu cầu mỗi phút theo IP: dùng chính sách và danh sách `thi360.security.rate-limit.exempt-ips` do C-0 làm; CM-1 đặt cấu hình và kiểm endpoint của mình với cả hai trường hợp.
- Interface `commerce/pricing/PriceCalculator` với bản tạm `ListPriceCalculator` trả giá gốc (B1); CM-2 thay bằng bản thật.
- Bài ôn đã công bố là sản phẩm như bài theo môn, nhãn thẻ lấy từ `PublishedExamPort` (BS 2).

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/commerce/storefront/**` (mới)
- `backend/src/main/java/vn/unicom/thi360/commerce/pricing/PriceCalculator.java`, `backend/src/main/java/vn/unicom/thi360/commerce/pricing/ListPriceCalculator.java` (mới)
- `backend/src/main/resources/db/migration/commerce/V*__storefront_product.sql` (mới)
- `backend/src/test/java/vn/unicom/thi360/commerce/storefront/**` (mới)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java` (GET `/api/v1/public/**`)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/CookieAuthFilter.java` (tiền tố `/api/v1/public/`)
- thêm vào: `backend/src/main/resources/application.yml` (cấu hình giới hạn của API công khai)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (`PRODUCT_NOT_FOUND`, `PRODUCT_DISCONTINUED`)
- thêm vào: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts` (sinh lại, không sửa tay)

**Ngoài phạm vi:** giá khuyến mãi trên thẻ và sắp xếp theo giá sau khuyến mãi (CM-2); dòng `BUNDLE`, `items[]`, bộ lọc "Bộ đề" (CM-6); chính sách giới hạn tần suất cho GET và danh sách IP miễn trong `shared/security` (C-0); `exam-access` (CM-4b); thông tin bán, sự kiện, `PublishedExamPort` (EX-1); trang web (FS-1).

**Nghiệm thu:**

- AC-1: Mã của bài nháp nhận 404 `PRODUCT_NOT_FOUND`, thân giống mã không tồn tại (trừ `traceId`) — bằng chứng: `StorefrontPublicApiIT` (mới), ca `draftCodeLooksLikeUnknownCode`
- AC-2: Mã đã ngừng bán nhận 410 `PRODUCT_DISCONTINUED` kèm `product.code`, `product.name` — bằng chứng: `StorefrontPublicApiIT`, ca `discontinuedCodeAnswers410WithProduct`
- AC-3: Lọc theo môn trả cả đề FULL chứa môn đó — bằng chứng: `StorefrontPublicApiIT`, ca `subjectFilterIncludesFullExams`
- AC-4: `ForbiddenKeysAssert` đạt trên phản hồi của `filters`, `products`, `products/{code}` — bằng chứng: `StorefrontPublicApiIT`, ca `responsesCarryNoForbiddenKeys`
- AC-5: Gọi không có cookie nhận 200 — bằng chứng: `StorefrontPublicApiIT`, ca `anonymousCallSucceeds`
- AC-6: Gọi kèm cookie truy cập hết hạn hoặc sai vẫn nhận 200 — bằng chứng: `StorefrontPublicApiIT`, ca `staleAccessCookieIsIgnored`
- AC-7: Cùng một sự kiện gửi hai lần cho cùng kết quả — bằng chứng: `StorefrontProjectionIT` (mới), ca `replayedEventChangesNothing`
- AC-8: Sự kiện của phiên bản cũ đến sau không ghi lùi `version_no` — bằng chứng: `StorefrontProjectionIT`, ca `olderEventDoesNotMoveVersionBack`
- AC-9: `ExamDiscontinued` đưa dòng về `DISCONTINUED`; sản phẩm rời `products` và `filters` — bằng chứng: `StorefrontProjectionIT`, ca `discontinuedEventHidesProduct`
- AC-10: Tên, giá, số lượt, `version_no` lấy từ phiên bản mới nhất còn đường phục vụ (E24) — bằng chứng: `StorefrontProjectionIT`, ca `listingComesFromLatestServingVersion`
- AC-11: Số câu, thời lượng, điểm tối đa đọc từ `parts[].paths[]`; các đường khác nhau thì trả khoảng — bằng chứng: `StorefrontProjectionIT`, ca `pathsDifferReturnsRange`
- AC-12: Handler lỗi ở một sự kiện thì sự kiện đó được ghi lần thử và lịch thử lại, sự kiện sau trong hàng vẫn xử lý được — bằng chứng: `StorefrontProjectionIT`, ca `failingEventDoesNotBlockQueue`
- AC-13: `rebuild` dựng đủ dòng cho bài đã công bố dù sự kiện trước đó đã thất bại, không tạo dòng cho bài nháp; chạy lại không đổi kết quả — bằng chứng: `StorefrontRebuildIT` (mới), ca `rebuildRestoresMissingRows`
- AC-14: `rebuild` thiếu `EXAM.PUBLISH` nhận 403, không có phiên nhận 401, thành công có nhật ký `STOREFRONT_REBUILT` — bằng chứng: `StorefrontRebuildIT`, ca `rebuildRequiresExamPublish`; `AdminApiForbiddenMatrixIT`
- AC-15: `covers/{assetId}` trả ảnh đang là bìa của một dòng bảng chiếu khi không có cookie; `assetId` khác nhận 404 — bằng chứng: `StorefrontPublicApiIT`, ca `coverServesOnlyCurrentCovers`
- AC-16: Phản hồi có `Cache-Control: public, max-age=60` và `ETag`; `If-None-Match` khớp thì 304 — bằng chứng: `StorefrontPublicApiIT`, ca `cacheHeadersAndConditionalGet`
- AC-17: IP thường bị 429 `RATE_LIMITED` ở yêu cầu thứ 121 trong một phút — bằng chứng: `StorefrontRateLimitIT` (mới), ca `ordinaryIpIsLimitedAt121`
- AC-18: IP trong `exempt-ips` gửi quá 120 yêu cầu vẫn nhận 200 — bằng chứng: `StorefrontRateLimitIT`, ca `exemptIpIsNotLimited`
- AC-19: `filters` trả số sản phẩm đang bán của từng giá trị; giá trị có số 0 không xuất hiện — bằng chứng: `StorefrontPublicApiIT`, ca `filtersOmitZeroCounts`
- AC-20: `q` tìm theo tên không dấu và theo mã; mặc định sắp "Mới công bố", có "Giá tăng dần", "Giá giảm dần"; `size` 49 trả tối đa 48 dòng — bằng chứng: `StorefrontPublicApiIT`, ca `searchSortAndPaging`
- AC-21: Bản tạm `PriceCalculator` trả `price = listPrice`, `promotion = null`; test ghi rõ đây là hành vi tạm — bằng chứng: `ListPriceCalculatorTest` (mới)
- AC-22: Bài ôn đã công bố xuất hiện ở `products` với nhãn lấy từ `PublishedExamPort` (BS 2) — bằng chứng: `StorefrontProjectionIT`, ca `reviewExamIsSoldWithPortLabel`
- AC-23: Với dữ liệu của DS, `products` trả đủ các bài đang bán, không có bài đã ngừng bán — bằng chứng: `StorefrontDataSetIT` (mới)
- AC-24: `ProductCard` đủ trường ở TM 10.2; `ProductDetail` đủ các nhóm nội dung của `detail` ở TM 10.1 — bằng chứng: `StorefrontPublicApiIT`, ca `cardAndDetailCarrySpecFields`
- AC-25: Lọc theo `kind`, `format`, `part` chỉ trả sản phẩm khớp — bằng chứng: `StorefrontPublicApiIT`, ca `kindFormatPartFilters`

**Ảnh hưởng ngoài task:**

- Schema `commerce` phải có trong Flyway trước khi migration của task chạy (`backend/src/main/resources/application.yml:23`, `spring.flyway.schemas`) → `Thi360ApplicationTests` khởi động được với `ddl-auto: validate` [chủ: C-0]
- `RateLimitFilter` bỏ qua mọi yêu cầu không phải POST và chưa có chính sách cho `/api/v1/public/**` (`backend/src/main/java/vn/unicom/thi360/shared/security/RateLimitFilter.java:63`, `:160`) → C-0 thêm chính sách GET 120/phút, miễn IPN, kèm `exempt-ips`; `StorefrontRateLimitIT` (mới) và `RateLimitFilterIT` xanh [chủ: C-0]
- Danh sách mở của bảo mật (`backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java:99`) → `StorefrontPublicApiIT` (mới) gọi ẩn danh được; `SessionIT` xanh, đường ngoài `/api/v1/public/**` vẫn cần đăng nhập [chủ: task này]
- `CookieAuthFilter` đang so khớp đúng từng đường dẫn (`backend/src/main/java/vn/unicom/thi360/shared/security/CookieAuthFilter.java:23`, `:35`) → ca `staleAccessCookieIsIgnored`; `ExpiredTokenLogoutIT` xanh [chủ: task này]
- `OutboxRelay` chỉ giữ một handler cho mỗi topic, đăng ký lần hai sẽ thay handler cũ (`backend/src/main/java/vn/unicom/thi360/shared/outbox/OutboxRelay.java:55`) → CM-6 mở rộng bộ nhận của CM-1, không `register` lại topic; `StorefrontProjectionIT` vẫn xanh sau CM-6 [chủ: CM-6]
- Topic chưa có handler bị đánh dấu thất bại vĩnh viễn (`backend/src/main/java/vn/unicom/thi360/shared/outbox/OutboxRelay.java:126`): sự kiện phát từ lúc EX-1 merge tới lúc CM-1 merge không được xử lý lại → ca `rebuildRestoresMissingRows`; Advisor gọi `rebuild` một lần trên DB dùng lâu dài sau khi CM-1 merge [chủ: task này]
- Handler chạy trong transaction của relay (`backend/src/main/java/vn/unicom/thi360/shared/outbox/OutboxRelay.java:91`, `:139`): lỗi trong transaction chung làm hỏng cả việc ghi lần thử → ca `failingEventDoesNotBlockQueue`; `OutboxRelayTests` xanh [chủ: task này]
- Relay chỉ chạy ở profile `worker` (`OutboxRelay.OutboxRelayWorker`, `e2e/playwright.config.ts:5`) → test tích hợp gọi `relayBatch()`; E2E của FS-1 chạy với `api,worker,dev` thấy bài vừa công bố [chủ: FS-1]
- `PublishedExamPort` chưa có hàm liệt kê bài đã công bố cho `rebuild` (plan EX-1 chỉ có `findServing`, `loadVersion`, `openCover`) → EX-1 thêm `listPublishedExamIds()` (mới); ca `rebuildRestoresMissingRows` [chủ: EX-1]
- Tên topic và nhãn bài ôn phải đọc được từ `exam/spi` (`backend/src/main/java/vn/unicom/thi360/exam/spi/package-info.java`) → `ModularityTest` đạt [chủ: EX-1]
- Module `commerce` gọi `exam/spi`, `catalog/spi`, `audit/spi` (`backend/src/test/java/vn/unicom/thi360/ModularityTest.java`) → `ModularityTest` đạt [chủ: task này]
- Endpoint admin mới được quét tự động (`backend/src/test/java/vn/unicom/thi360/AdminApiForbiddenMatrixIT.java`, `backend/src/test/java/vn/unicom/thi360/EndpointPermissionCoverageTest.java`) → hai test này xanh [chủ: task này]
- Khóa lỗi mới trong ba tệp `messages*.properties` (`backend/src/test/java/vn/unicom/thi360/shared/web/MessageBundlesTest.java`) → `MessageBundlesTest` xanh [chủ: task này]
- OpenAPI và client sinh (`packages/api-client/openapi.json`, `.github/workflows/backend.yml:28`) → sinh lại; `diff -u ../packages/api-client/openapi.json target/openapi.json` rỗng; `pnpm typecheck` xanh [chủ: task này]
- `PriceCalculator` là hợp đồng CM-2 cài lại (`commerce/pricing/PriceCalculator.java`, mới) → `StorefrontPublicApiIT` chạy lại trên bản thật [chủ: CM-2]
- CM-3a đọc và khóa dòng bảng chiếu khi tạo đơn, CM-4b ánh xạ mã sản phẩm qua bảng này (`commerce.storefront_product`, mới) → test của CM-3a, CM-4b xanh sau khi rebase [chủ: CM-3a, CM-4b]
- Thân lỗi 410 và kiểu `ProductCard`, `ProductDetail` là hợp đồng của web (`packages/api-client/src/schema.d.ts`) → FS-1 hiện trang 410 từ `product.name` [chủ: FS-1]
- E2E chạy mọi lời gọi từ một IP nên có thể chạm 120/phút (`e2e/playwright.config.ts:64`, `.github/workflows/e2e.yml`) → FS-1 đặt `THI360_SECURITY_RATELIMIT_EXEMPTIPS` cho backend E2E; bộ E2E của FS-1 không có phản hồi 429 [chủ: FS-1]

**Kế hoạch kiểm thử:**

- AC-1: `StorefrontPublicApiIT#draftCodeLooksLikeUnknownCode`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-2: `StorefrontPublicApiIT#discontinuedCodeAnswers410WithProduct`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-3: `StorefrontPublicApiIT#subjectFilterIncludesFullExams`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-4: `StorefrontPublicApiIT#responsesCarryNoForbiddenKeys`, hợp đồng, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-5: `StorefrontPublicApiIT#anonymousCallSucceeds`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-6: `StorefrontPublicApiIT#staleAccessCookieIsIgnored`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-7: `StorefrontProjectionIT#replayedEventChangesNothing`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontProjectionIT`
- AC-8: `StorefrontProjectionIT#olderEventDoesNotMoveVersionBack`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontProjectionIT`
- AC-9: `StorefrontProjectionIT#discontinuedEventHidesProduct`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontProjectionIT`
- AC-10: `StorefrontProjectionIT#listingComesFromLatestServingVersion`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontProjectionIT`
- AC-11: `StorefrontProjectionIT#pathsDifferReturnsRange`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontProjectionIT`
- AC-12: `StorefrontProjectionIT#failingEventDoesNotBlockQueue`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontProjectionIT`
- AC-13: `StorefrontRebuildIT#rebuildRestoresMissingRows`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontRebuildIT`
- AC-14: `StorefrontRebuildIT#rebuildRequiresExamPublish`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontRebuildIT`
- AC-15: `StorefrontPublicApiIT#coverServesOnlyCurrentCovers`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-16: `StorefrontPublicApiIT#cacheHeadersAndConditionalGet`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-17: `StorefrontRateLimitIT#ordinaryIpIsLimitedAt121`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontRateLimitIT`
- AC-18: `StorefrontRateLimitIT#exemptIpIsNotLimited`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontRateLimitIT`
- AC-19: `StorefrontPublicApiIT#filtersOmitZeroCounts`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-20: `StorefrontPublicApiIT#searchSortAndPaging`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-21: `ListPriceCalculatorTest`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=ListPriceCalculatorTest`
- AC-22: `StorefrontProjectionIT#reviewExamIsSoldWithPortLabel`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontProjectionIT`
- AC-23: `StorefrontDataSetIT`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontDataSetIT`
- AC-24: `StorefrontPublicApiIT#cardAndDetailCarrySpecFields`, hợp đồng, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- AC-25: `StorefrontPublicApiIT#kindFormatPartFilters`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPublicApiIT`
- Hồi quy:
  - Khởi động, module, quyền, thông điệp: `cd backend && ./mvnw -B -ntp test -Dtest='Thi360ApplicationTests,ModularityTest,AdminApiForbiddenMatrixIT,EndpointPermissionCoverageTest,MessageBundlesTest'`
  - Bảo mật và outbox: `cd backend && ./mvnw -B -ntp test -Dtest='RateLimitFilterIT,SessionIT,ExpiredTokenLogoutIT,OutboxRelayTests'`
  - OpenAPI: `cd backend && ./mvnw -B -ntp verify && diff -u ../packages/api-client/openapi.json target/openapi.json`
  - Client sinh: `cp backend/target/openapi.json packages/api-client/openapi.json && pnpm --filter @thi360/api-client generate && pnpm typecheck`
  - Dòng của C-0, EX-1, CM-2, CM-3a, CM-4b, CM-6, FS-1: task chủ chạy phép kiểm đã ghi ở dòng đó.
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: bản giả của `PublishedExamPort` và sự kiện tự dựng cho `StorefrontProjectionIT`, `StorefrontRebuildIT`; bộ dựng của DS cho `StorefrontDataSetIT`. Mỗi lane có Testcontainers riêng; mã sản phẩm sinh ngẫu nhiên theo từng test; mỗi test đặt `X-Forwarded-For` riêng để không chung bộ đếm giới hạn tần suất.
- Không kiểm: giới hạn tần suất qua proxy web và nginx (B6: phụ thuộc cấu hình triển khai, test đặt header ở tầng backend); đổi tên kỳ thi, môn trong danh mục sau khi công bố (TM không nói dòng chiếu có tự dựng lại không, `rebuild` là cách làm mới hiện có).


## CM-2 · Khuyến mãi, voucher, tính giá

**Spec:** BS 10; TM 7, 8.3 bước 1 (điều kiện tài khoản), 8.6 (lấy lại lượt voucher khi tiền về muộn), E5–E9, E23, E26, 10.2 (`price-quote`), 13; plan B1, B2.

**Sản phẩm:**

- Các bảng `promotion`, `promotion_scope_item`, `promotion_audience`, `promotion_redemption` kèm các ràng buộc ở TM 7.1. `promotion_redemption.order_id` chưa có khóa ngoại (B1).
- Tạo bảng theo hình dạng đã có bộ đề (TM 7.1): `scope` có giá trị `BUNDLE`, `promotion_scope_item.bundle_id` (chưa có khóa ngoại; CM-6 thêm). API của CM-2 chưa nhận `scope = BUNDLE`; tính giá và API cho phạm vi này để CM-6 làm.
- `PriceCalculator` thật theo TM 7.2 bước 1–7, thay bản tạm của CM-1. Mức tối thiểu đọc từ `THI360_VNPAY_MIN_AMOUNT`, mặc định 5.000, đặt 0 thì tắt (E26).
- Interface `PurchaseHistory` (E8) với bản tạm "chưa mua lần nào" (B1).
- Service nội bộ giữ lượt: `reserve`, `confirm`, `release`, và `reclaim` (mới) cho tiền về muộn (TM 8.6), có khóa dòng và `UPDATE` có điều kiện; `over_limit_count`.
- Admin API `/api/v1/admin/promotions/**` đủ các dòng ở TM 7.3, nhật ký `PROMOTION_CREATED`, `PROMOTION_UPDATED`, `PROMOTION_STOPPED`, `PROMOTION_RESUMED`, `PROMOTION_DELETED`.
- `POST /api/v1/me/price-quote`, kèm giới hạn chống dò mã theo tài khoản: 10 lần mỗi phút, 50 lần mỗi ngày, bucket Redis theo mẫu `QuestionAssetUploadRateLimiter`.
- Thẻ trên API công khai hiện giá khuyến mãi tự động; sắp xếp theo giá sau khuyến mãi (E11).
- `PromotionSummary`, `PromotionDetail` có `capWarnings`; `PromotionDetail` có `totalDiscountPaid`, `history` (BS 10). Bảng `promotion_change_log` ghi người đổi, thời điểm, hành động, các trường đã đổi với giá trị cũ và mới, trong cùng transaction với thay đổi.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/commerce/promotion/**` (mới)
- `backend/src/main/java/vn/unicom/thi360/commerce/pricing/**` (nhận từ CM-1, xóa `ListPriceCalculator`)
- `backend/src/main/resources/db/migration/commerce/V*__promotion.sql` (mới)
- `backend/src/test/java/vn/unicom/thi360/commerce/promotion/**`, `backend/src/test/java/vn/unicom/thi360/commerce/pricing/**` (mới)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/commerce/storefront/**` (giá trên thẻ, sắp xếp theo giá sau khuyến mãi)
- thêm vào: `backend/src/test/java/vn/unicom/thi360/AdminApiForbiddenMatrixIT.java` (nhánh `Instant` trong `sampleValue`)
- thêm vào: `backend/src/main/resources/application.yml` (`thi360.commerce.voucher.*`, mức tối thiểu)
- thêm vào: `.env.example` (tên biến `THI360_VNPAY_MIN_AMOUNT`, nếu EX-1 chưa thêm)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi `VOUCHER_*`, `PROMOTION_*`)
- thêm vào: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts` (sinh lại, không sửa tay)
- `backend/src/main/resources/db/migration/commerce/V*__promotion_change_log.sql` (mới)

**Ngoài phạm vi:** màn `/promotions` (FA-2); gọi `reserve`, `confirm`, `release`, `reclaim` từ luồng đơn và bản thật của `PurchaseHistory` (CM-3a); trả lượt khi đơn hết hạn (CM-3b); phạm vi `BUNDLE` (CM-6); màn xác nhận mua và câu thông báo voucher (FS-3).

**Nghiệm thu:**

- AC-1: Bảng ca tính giá đúng cho %, trần `max_discount_vnd`, số tiền, làm tròn xuống tới đồng, giá không âm — bằng chứng: `PriceCalculatorTest` (mới), ca `discountTable`
- AC-2: Nhiều khuyến mãi tự động thì lấy cái giảm nhiều tiền nhất; bằng nhau thì cái kết thúc sớm hơn (E5) — bằng chứng: `PriceCalculatorTest`, ca `bestAutoPromotionWins`
- AC-3: Voucher `combinable` bật thì tính trên giá đã giảm và ghi `COMBINED`; tắt thì chọn giá nhỏ hơn và ghi `PROMOTION_ONLY` hoặc `VOUCHER_ONLY` (E6) — bằng chứng: `PriceCalculatorTest`, ca `voucherCombinationModes`
- AC-4: Khi `discount_applied = PROMOTION_ONLY`, mã đã nhập không được ghi vào kết quả và không giữ lượt voucher — bằng chứng: `PriceCalculatorTest`, ca `unusedVoucherIsNotRecorded`
- AC-5: Khuyến mãi tự động không đưa giá xuống dưới mức tối thiểu — bằng chứng: `PriceCalculatorTest`, ca `autoPromotionStopsAtMinimum`
- AC-6: Giá cuối sau voucher lớn hơn 0 và nhỏ hơn mức tối thiểu thì thành 0; đúng bằng mức tối thiểu thì giữ nguyên; đặt mức 0 thì tắt quy tắc — bằng chứng: `PriceCalculatorTest`, ca `voucherBelowMinimumBecomesFree`
- AC-7: 20 yêu cầu đồng thời với voucher còn 1 lượt: đúng 1 lượt được giữ, `used_count` tăng đúng 1 — bằng chứng: `PromotionRedemptionConcurrencyIT` (mới), ca `lastSlotGoesToOneRequest`
- AC-8: Các yêu cầu đồng thời của cùng tài khoản không vượt `per_user_limit` — bằng chứng: `PromotionRedemptionConcurrencyIT`, ca `perUserLimitHoldsUnderConcurrency`
- AC-9: Mã sai, chưa bắt đầu, đã ngừng, đã xóa đều trả cùng `VOUCHER_INVALID`, thân giống nhau (trừ `traceId`) — bằng chứng: `PriceQuoteApiIT` (mới), ca `unusableCodesLookTheSame`
- AC-10: Mã có thật trả đúng `VOUCHER_EXPIRED`, `VOUCHER_EXHAUSTED`, `VOUCHER_USER_LIMIT`, `VOUCHER_NOT_APPLICABLE`, `VOUCHER_NOT_ELIGIBLE` — bằng chứng: `PriceQuoteApiIT`, ca `specificVoucherErrors`
- AC-11: Phạm vi `ALL`, `EXAM_KIND`, `EXAM` chỉ áp đúng sản phẩm thuộc phạm vi — bằng chứng: `PriceQuoteApiIT`, ca `scopeRules`
- AC-12: Voucher "tài khoản mới" với bản tạm `PurchaseHistory` áp được; test ghi rõ đây là hành vi tạm — bằng chứng: `PriceQuoteApiIT`, ca `newAccountUsesProvisionalHistory`
- AC-13: Voucher `SELECTED` áp cho học viên được chỉ định và học viên đang ở lớp được chỉ định; rời lớp thì nhận `VOUCHER_NOT_ELIGIBLE` — bằng chứng: `PriceQuoteApiIT`, ca `selectedAudienceFollowsCurrentClass`
- AC-14: Hiệu lực theo khoảng `[starts_at, ends_at)`: đúng lúc bắt đầu thì dùng được, đúng lúc kết thúc thì hết hiệu lực — bằng chứng: `PriceQuoteApiIT`, ca `validityWindowBoundaries`
- AC-15: Lượt giữ của chính đơn chờ cùng sản phẩm không tính vào giới hạn tổng và giới hạn mỗi tài khoản khi tính giá — bằng chứng: `PriceCalculatorIT` (mới), ca `ownPendingReservationIsExcluded`
- AC-16: `confirm` chuyển `RESERVED` thành `CONFIRMED`; `release` chuyển thành `RELEASED` và giảm `used_count` đúng 1; gọi lặp không đổi số — bằng chứng: `PromotionRedemptionIT` (mới), ca `reserveConfirmReleaseStates`
- AC-17: `release` trên lượt `CONFIRMED` không trả lượt (E23) — bằng chứng: `PromotionRedemptionIT`, ca `confirmedRedemptionIsNeverReleased`
- AC-18: `reclaim` đưa `RELEASED` về `CONFIRMED`, tăng `used_count`; vượt `total_limit` thì vẫn nhận và tăng `over_limit_count` — bằng chứng: `PromotionRedemptionIT`, ca `lateReclaimMayExceedLimit`
- AC-19: Khuyến mãi tự động hết `total_limit` thì thẻ trở về giá gốc — bằng chứng: `StorefrontPromotionIT` (mới), ca `exhaustedAutoPromotionStopsApplying`
- AC-20: Sửa khuyến mãi đã có lượt dùng chỉ được đổi tên, mô tả, `ends_at`, `total_limit`, trạng thái; trường khác trả 409 `PROMOTION_IN_USE` — bằng chứng: `PromotionAdminApiIT` (mới), ca `usedPromotionLocksFields`
- AC-21: `ends_at` sớm hơn hiện tại hoặc `total_limit` nhỏ hơn `used_count` bị từ chối — bằng chứng: `PromotionAdminApiIT`, ca `usedPromotionLimitsEditableValues`
- AC-22: Ngừng có hiệu lực ngay, lượt đã giữ vẫn `confirm` được; mở lại được khi chưa hết hạn, đã hết hạn thì 409 `PROMOTION_ENDED` (mới) — bằng chứng: `PromotionAdminApiIT`, ca `stopAndResumeRules`
- AC-23: Xóa khi `used_count = 0` là xóa mềm; đã có lượt dùng thì 409 `PROMOTION_IN_USE` — bằng chứng: `PromotionAdminApiIT`, ca `deleteOnlyUnusedPromotion`
- AC-24: Tạo khuyến mãi: gửi lại cùng khóa nhận đúng kết quả cũ; cùng khóa khác thân nhận 409 `IDEMPOTENCY_KEY_REUSED`; thiếu khóa nhận 400 — bằng chứng: `PromotionAdminApiIT`, ca `createIsIdempotent`
- AC-25: Mã trùng sau chuẩn hóa, kể cả với voucher đã ngừng hoặc đã xóa, nhận 409 `VOUCHER_CODE_TAKEN`; hai yêu cầu đồng thời cùng mã chỉ một thành công — bằng chứng: `PromotionAdminApiIT`, ca `voucherCodeIsUniqueForever`
- AC-26: `expectedRevision` cũ ở `PATCH`, `stop`, `resume` nhận 412 — bằng chứng: `PromotionAdminApiIT`, ca `staleRevisionIsRejected`
- AC-27: Khuyến mãi tự động gửi kèm `code`, `per_user_limit`, `audience` hoặc `combinable` bị từ chối; `scope = BUNDLE` nhận 400 — bằng chứng: `PromotionAdminApiIT`, ca `autoPromotionRejectsVoucherFields`
- AC-28: Phạm vi `EXAM` chọn bài chưa công bố nhận 422 `PROMOTION_SCOPE_INVALID` (mới) — bằng chứng: `PromotionAdminApiIT`, ca `examScopeNeedsPublishedExam`
- AC-29: `GET /promotions` lọc đúng theo `kind`, `status`, `state`, `q`; chi tiết có trang các lần dùng — bằng chứng: `PromotionAdminApiIT`, ca `listFiltersAndDetail`
- AC-30: `suggested-code` trả 8 ký tự, không có `0`, `O`, `1`, `I`; `price-preview` trả `PriceQuote` — bằng chứng: `PromotionAdminApiIT`, ca `suggestedCodeAndPricePreview`
- AC-31: Mỗi lệnh ghi có đúng một nhật ký `PROMOTION_*` — bằng chứng: `PromotionAdminApiIT`, ca `writesAreAudited`
- AC-32: Endpoint đọc nhận 403 khi thiếu `PROMOTION.VIEW`; endpoint ghi và `suggested-code` nhận 403 khi chỉ có `PROMOTION.VIEW`; không có phiên nhận 401 — bằng chứng: `PromotionAdminApiIT`, ca `permissionsAreSeparate`; `AdminApiForbiddenMatrixIT`
- AC-33: Ràng buộc DB từ chối `discount_value <= 0`, `PERCENT` ngoài 1–100, `ends_at <= starts_at`, `total_limit < 1`, `used_count < 0`, dòng phạm vi không đúng một cột, `(promotion_id, order_id)` trùng — bằng chứng: `PromotionConstraintsIT` (mới)
- AC-34: `price-quote` trả giá gốc, khuyến mãi, voucher và cách áp, giá cuối, số lượt; `seenPrice` hoặc `seenVersionNo` lệch thì `changed = true` — bằng chứng: `PriceQuoteApiIT`, ca `quoteContentAndChangedFlag`
- AC-35: `price-quote` với mã không có nhận 404 `PRODUCT_NOT_FOUND`, mã ngừng bán nhận 410 `PRODUCT_DISCONTINUED` — bằng chứng: `PriceQuoteApiIT`, ca `quoteUnknownAndDiscontinued`
- AC-36: `price-quote` với tài khoản `MANAGE` hoặc không `ACTIVE` nhận 403; không có phiên nhận 401 — bằng chứng: `PriceQuoteApiIT`, ca `quoteRequiresActiveExamAccount`
- AC-37: Tính giá có voucher lần thứ 11 trong một phút nhận 429 `RATE_LIMITED`; tính giá không có voucher không bị đếm — bằng chứng: `PriceQuoteRateLimitIT` (mới), ca `perMinuteLimit`
- AC-38: Tính giá có voucher lần thứ 51 trong một ngày nhận 429 `RATE_LIMITED`; tài khoản khác không bị ảnh hưởng — bằng chứng: `PriceQuoteRateLimitIT`, ca `perDayLimit`
- AC-39: Thẻ công khai có `listPrice`, `price`, `promotion {name, endsAt}`; khách và học viên thấy cùng một giá (E9) — bằng chứng: `StorefrontPromotionIT`, ca `cardShowsAutoPromotion`
- AC-40: "Giá tăng dần", "Giá giảm dần" sắp theo giá sau khuyến mãi trên mọi trang; `ETag` đổi khi giá đổi — bằng chứng: `StorefrontPromotionIT`, ca `priceSortAndEtagFollowPromotion`
- AC-41: `capWarnings` chỉ liệt kê bài có giá sau giảm bị giữ ở mức tối thiểu, đủ `listPrice`, `discountedPrice`, `floorPrice`; đặt mức tối thiểu 0 thì danh sách rỗng — bằng chứng: `PromotionAdminDataIT#capWarningsListOnlyFlooredExams`, `#noFloorNoWarning` (mới)
- AC-42: `totalDiscountPaid` chỉ cộng lần dùng `CONFIRMED`; lần `RESERVED`, `RELEASED` không tính — bằng chứng: `PromotionAdminDataIT#totalDiscountCountsConfirmedOnly` (mới)
- AC-43: Tạo, sửa, ngừng, tiếp tục đều thêm đúng một dòng lịch sử có giá trị cũ và mới, mới nhất trước; thay đổi bị từ chối do `expectedRevision` cũ không có dòng lịch sử — bằng chứng: `PromotionAdminDataIT#historyRecordsEachChange`, `#rejectedChangeLeavesNoHistory` (mới)

**Ảnh hưởng ngoài task:**

- Bản tạm `ListPriceCalculator` bị thay (`commerce/pricing`, CM-1) → `StorefrontPublicApiIT` xanh trên bản thật; `ListPriceCalculatorTest` xóa cùng bản tạm, nêu lý do trong PR [chủ: task này]
- `AdminApiForbiddenMatrixIT` dựng thân mẫu từ các trường bắt buộc, `sampleValue` chưa có nhánh `Instant` (`backend/src/test/java/vn/unicom/thi360/AdminApiForbiddenMatrixIT.java:436`, `:472`), nên `startsAt`, `endsAt` bắt buộc sẽ làm test lỗi → thêm nhánh `Instant`; ràng buộc giữa các trường đặt ở service, không đặt trong bean validation; test này xanh [chủ: task này]
- Quyền `PROMOTION.VIEW`, `PROMOTION.MODIFY` do C-0 khai (`backend/src/main/java/vn/unicom/thi360/identity/spi/Permissions.java`) → `EndpointPermissionCoverageTest`, `AdminApiForbiddenMatrixIT` xanh [chủ: C-0]
- Đọc lớp hiện tại của học viên (`backend/src/main/java/vn/unicom/thi360/identity/spi/ClassMembershipPort.java`, `getUserClasses`) → ca `selectedAudienceFollowsCurrentClass`; `ClassroomAdminIT` xanh [chủ: task này]
- Kiểm tài khoản được chỉ định và trạng thái `ACTIVE` (`backend/src/main/java/vn/unicom/thi360/identity/spi/UserDirectory.java`, `findDetailsByIds`) → ca `quoteRequiresActiveExamAccount` [chủ: task này]
- Module `commerce` gọi thêm `identity/spi` (`backend/src/test/java/vn/unicom/thi360/ModularityTest.java`) → `ModularityTest` đạt [chủ: task này]
- Dùng lại idempotency chung (`backend/src/main/java/vn/unicom/thi360/shared/idempotency/IdempotentCommand.java`) → ca `createIsIdempotent`; `IdempotentCommandIT` xanh [chủ: task này]
- Khóa Redis mới `rl:voucher:user:` đặt cạnh `rl:login:ip:`, `rl:upload:ip:`, `rl:qasset:user:` (`RateLimitFilter.java:37`, `QuestionAssetUploadRateLimiter.java`) → `PriceQuoteRateLimitIT`, `RateLimitFilterIT` xanh [chủ: task này]
- Mức tối thiểu dùng chung biến với kiểm tra `LISTING_PRICE_BELOW_MIN` của EX-1 (`THI360_VNPAY_MIN_AMOUNT`, TM E26) → hai nơi cùng mặc định 5.000; ca `voucherBelowMinimumBecomesFree` và test `LISTING_PRICE_BELOW_MIN` của EX-1 xanh [chủ: EX-1]
- Khóa lỗi mới trong ba tệp `messages*.properties` (`backend/src/test/java/vn/unicom/thi360/shared/web/MessageBundlesTest.java`) → `MessageBundlesTest` xanh [chủ: task này]
- OpenAPI và client sinh (`packages/api-client/openapi.json`, `.github/workflows/backend.yml:28`) → sinh lại; `diff -u ../packages/api-client/openapi.json target/openapi.json` rỗng; `pnpm typecheck` xanh [chủ: task này]
- Hợp đồng `reserve`, `confirm`, `release`, `reclaim` và thứ tự khóa `purchase_order` → `promotion` (plan B2) → CM-3a gọi trong transaction tạo đơn; ca 20 đơn đồng thời ở tầng API của CM-3a chỉ tạo một đơn [chủ: CM-3a]
- Tính giá cần biết đơn chờ của chính học viên, nhưng bảng đơn chưa có (`PriceCalculator`, TM 7.2 bước 7) → CM-3a truyền mã đơn chờ vào `PriceCalculator` ở `/me/price-quote` và tạo đơn; test đơn chờ của CM-3a xanh [chủ: CM-3a]
- `PurchaseHistory` bản tạm và khóa ngoại `promotion_redemption.order_id` (plan B1) → CM-3a thay bản thật, sửa ca `newAccountUsesProvisionalHistory`, thêm khóa ngoại [chủ: CM-3a]
- Đơn hết hạn và đơn bị hủy trả lượt qua `release` (TM 7.2, 8.6) → test đơn hết hạn của CM-3b xanh [chủ: CM-3b]
- Hoàn tiền không được trả lượt voucher (E23) → test hoàn tiền của CM-5 khẳng định `used_count` không đổi [chủ: CM-5]
- `scope = BUNDLE`, `promotion_scope_item.bundle_id` để sẵn (TM 16.2) → CM-6 mở giá trị ở API, thêm khóa ngoại, sửa ca `autoPromotionRejectsVoucherFields` [chủ: CM-6]
- Kiểu `PromotionSummary`, `PromotionDetail`, `PriceQuote` là hợp đồng của màn admin và màn xác nhận mua (`packages/api-client/src/schema.d.ts`) → test của FA-2, FS-3 xanh sau khi client sinh lại [chủ: FA-2, FS-3]

**Kế hoạch kiểm thử:**

- AC-1: `PriceCalculatorTest#discountTable`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=PriceCalculatorTest`
- AC-2: `PriceCalculatorTest#bestAutoPromotionWins`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=PriceCalculatorTest`
- AC-3: `PriceCalculatorTest#voucherCombinationModes`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=PriceCalculatorTest`
- AC-4: `PriceCalculatorTest#unusedVoucherIsNotRecorded`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=PriceCalculatorTest`
- AC-5: `PriceCalculatorTest#autoPromotionStopsAtMinimum`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=PriceCalculatorTest`
- AC-6: `PriceCalculatorTest#voucherBelowMinimumBecomesFree`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=PriceCalculatorTest`
- AC-7: `PromotionRedemptionConcurrencyIT#lastSlotGoesToOneRequest`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionRedemptionConcurrencyIT`
- AC-8: `PromotionRedemptionConcurrencyIT#perUserLimitHoldsUnderConcurrency`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionRedemptionConcurrencyIT`
- AC-9: `PriceQuoteApiIT#unusableCodesLookTheSame`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteApiIT`
- AC-10: `PriceQuoteApiIT#specificVoucherErrors`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteApiIT`
- AC-11: `PriceQuoteApiIT#scopeRules`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteApiIT`
- AC-12: `PriceQuoteApiIT#newAccountUsesProvisionalHistory`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteApiIT`
- AC-13: `PriceQuoteApiIT#selectedAudienceFollowsCurrentClass`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteApiIT`
- AC-14: `PriceQuoteApiIT#validityWindowBoundaries`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteApiIT`
- AC-15: `PriceCalculatorIT#ownPendingReservationIsExcluded`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceCalculatorIT`
- AC-16: `PromotionRedemptionIT#reserveConfirmReleaseStates`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionRedemptionIT`
- AC-17: `PromotionRedemptionIT#confirmedRedemptionIsNeverReleased`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionRedemptionIT`
- AC-18: `PromotionRedemptionIT#lateReclaimMayExceedLimit`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionRedemptionIT`
- AC-19: `StorefrontPromotionIT#exhaustedAutoPromotionStopsApplying`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPromotionIT`
- AC-20: `PromotionAdminApiIT#usedPromotionLocksFields`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-21: `PromotionAdminApiIT#usedPromotionLimitsEditableValues`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-22: `PromotionAdminApiIT#stopAndResumeRules`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-23: `PromotionAdminApiIT#deleteOnlyUnusedPromotion`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-24: `PromotionAdminApiIT#createIsIdempotent`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-25: `PromotionAdminApiIT#voucherCodeIsUniqueForever`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-26: `PromotionAdminApiIT#staleRevisionIsRejected`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-27: `PromotionAdminApiIT#autoPromotionRejectsVoucherFields`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-28: `PromotionAdminApiIT#examScopeNeedsPublishedExam`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-29: `PromotionAdminApiIT#listFiltersAndDetail`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-30: `PromotionAdminApiIT#suggestedCodeAndPricePreview`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-31: `PromotionAdminApiIT#writesAreAudited`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionAdminApiIT`
- AC-32: `PromotionAdminApiIT#permissionsAreSeparate` và `AdminApiForbiddenMatrixIT`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest='PromotionAdminApiIT,AdminApiForbiddenMatrixIT'`
- AC-33: `PromotionConstraintsIT`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PromotionConstraintsIT`
- AC-34: `PriceQuoteApiIT#quoteContentAndChangedFlag`, hợp đồng, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteApiIT`
- AC-35: `PriceQuoteApiIT#quoteUnknownAndDiscontinued`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteApiIT`
- AC-36: `PriceQuoteApiIT#quoteRequiresActiveExamAccount`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteApiIT`
- AC-37: `PriceQuoteRateLimitIT#perMinuteLimit`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteRateLimitIT`
- AC-38: `PriceQuoteRateLimitIT#perDayLimit`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=PriceQuoteRateLimitIT`
- AC-39: `StorefrontPromotionIT#cardShowsAutoPromotion`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPromotionIT`
- AC-40: `StorefrontPromotionIT#priceSortAndEtagFollowPromotion`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=StorefrontPromotionIT`
- AC-41: `PromotionAdminDataIT#capWarningsListOnlyFlooredExams`, `#noFloorNoWarning` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PromotionAdminDataIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-42: `PromotionAdminDataIT#totalDiscountCountsConfirmedOnly` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PromotionAdminDataIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-43: `PromotionAdminDataIT#historyRecordsEachChange`, `#rejectedChangeLeavesNoHistory` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PromotionAdminDataIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy:
  - Bảng chiếu của CM-1: `cd backend && ./mvnw -B -ntp test -Dtest='StorefrontPublicApiIT,StorefrontProjectionIT,StorefrontRebuildIT,StorefrontRateLimitIT'`
  - Quyền, module, thông điệp: `cd backend && ./mvnw -B -ntp test -Dtest='AdminApiForbiddenMatrixIT,EndpointPermissionCoverageTest,ModularityTest,MessageBundlesTest'`
  - Dùng chung: `cd backend && ./mvnw -B -ntp test -Dtest='IdempotentCommandIT,RateLimitFilterIT,ClassroomAdminIT'`
  - OpenAPI: `cd backend && ./mvnw -B -ntp verify && diff -u ../packages/api-client/openapi.json target/openapi.json`
  - Client sinh: `cp backend/target/openapi.json packages/api-client/openapi.json && pnpm --filter @thi360/api-client generate && pnpm typecheck`
  - Dòng của C-0, EX-1, CM-3a, CM-3b, CM-5, CM-6, FA-2, FS-3: task chủ chạy phép kiểm đã ghi ở dòng đó.
- Kiểm thử rủi ro:
  - Quyền: từng quyền `PROMOTION.VIEW`, `PROMOTION.MODIFY` đứng riêng và khi thiếu, cho mọi endpoint admin (AC-32); tài khoản `MANAGE`, không `ACTIVE`, không phiên ở `price-quote` (AC-36).
  - Gửi lặp và đồng thời: AC-7, AC-8, AC-16, AC-24, AC-25.
  - Migration trên DB đã có dữ liệu: `PromotionConstraintsIT` chạy sau toàn bộ migration và dữ liệu mẫu `db/devdata`; migration chỉ tạo bảng mới, không đổi bảng của CM-1.
  - Giá trị biên: giảm 1% và 100%; giá cuối bằng mức tối thiểu trừ 1 đồng, bằng mức tối thiểu; `total_limit = 1`; đúng `starts_at`, đúng `ends_at`; lần thứ 10 và 11 mỗi phút, lần thứ 50 và 51 mỗi ngày.
  - đỏ trước: AC-1, AC-3, AC-6, AC-7, AC-8, AC-17, AC-18.
- Dữ liệu test: khuyến mãi và voucher tạo qua API hoặc service trong từng test, mã sinh ngẫu nhiên; sản phẩm là dòng `storefront_product` do test dựng; đồng hồ cố định bằng `Clock` của test cho các ca biên thời gian. Mỗi lane có Testcontainers riêng; bucket Redis tách theo tài khoản sinh ngẫu nhiên.
- Không kiểm: 20 đơn đồng thời ở tầng API (CM-3a, vì chưa có bảng đơn); giao diện cảnh báo khuyến mãi chạm mức tối thiểu và chỉ số "Tiền đã giảm" (chờ câu hỏi mở dưới đây); hành vi khi Redis lỗi (cho qua như `RateLimitFilter`, không thêm ca riêng).

## CM-4a · Bảng quyền dùng, `EntitlementPort`

**Spec:** TM 8.5 (bảng, `EntitlementPort`), 9.4 bước 6 (thu hồi), E21, E25, C12, C13, C15; LT 5.1 (dữ liệu chuẩn bị), 5.2 (`NO_ATTEMPTS_LEFT`), 12 (cấp bù); plan B1, B2.

**Sản phẩm:**

- Bảng `entitlement` (duy nhất `NULLS NOT DISTINCT`, ràng buộc `attempts_used + attempts_reserved + attempts_revoked ≤ attempts_total`), `entitlement_grant` (`kind`, `option_codes`; `order_id` chưa có khóa ngoại, B1), `entitlement_usage`.
- `entitlement_grant` có cột `exam_id`; ràng buộc duy nhất là `(order_id, exam_id)` ngay từ đầu (TM 8.5), không phải `order_id`. `compensation_attempt_id` duy nhất.
- Hai cột thêm vào `entitlement_grant` so với bảng ở TM 8.5:
  - `revoked_at` (mới): đánh dấu lần cấp đã bị hoàn hết, vì lần cấp không còn lượt để thu hồi thì `attempts_revoked` vẫn bằng 0 mà `release`, `compensate` vẫn phải nhận ra;
  - `order_code` (mới): `describe` trả mã đơn (LT 5.1) mà không đọc bảng đơn của CM-3a.
- Bản cài `EntitlementPort` theo hợp đồng của C-0: `describe`, `reserve`, `confirmUse`, `release`, `benefitsOf`, `compensate`. Mọi hàm idempotent theo `attemptId`; lần cấp dùng theo thứ tự cũ nhất trước (E21).
- Service nội bộ `commerce.entitlement.application.EntitlementGrantService`:
  - `grantForOrder` (CM-3a gọi): khóa theo thứ tự `exam_id` tăng dần; trùng `(order_id, exam_id)` thì bỏ qua dòng đó;
  - `revokeUnused(grantId)` (CM-5 gọi).
- `AccountDeletionGuard`: tài khoản có dòng `entitlement` thì không xóa được, trả `Blocker` nguồn `COMMERCE`, mã `USER_HAS_TRANSACTIONS`.
- Phần `entitlements` của `StudentLearningProvider`: mỗi dòng gồm `entitlementId`, `examId`, `source`, số lượt tổng, đã dùng, đang giữ, đã thu hồi, còn lại. Tài khoản chưa có quyền dùng trả `dataAvailable = false`.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/commerce/entitlement/**` (mới)
- `backend/src/main/resources/db/migration/commerce/V*__entitlement.sql` (mới)
- `backend/src/test/java/vn/unicom/thi360/commerce/entitlement/**` (mới)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (`NO_ATTEMPTS_LEFT`, `GRANT_REFUNDED`, `ALREADY_COMPENSATED`)

**Ngoài phạm vi:** `exam-access`, `/me/exams` (CM-4b); khóa ngoại `entitlement_grant.order_id`, "có đơn" trong `AccountDeletionGuard`, nhật ký `ENTITLEMENT_GRANTED` (CM-3a); nhật ký `ENTITLEMENT_REVOKED` và quyết định khi nào thu hồi (CM-5); kiểm "lượt đã đánh dấu hỏng" trước khi cấp bù và nhật ký cấp bù (AT-5); hiện dữ liệu quyền dùng ở tab Hồ sơ học tập của web admin (LP).

**Nghiệm thu:**

- AC-1: Hai lần cấp đồng thời cho cùng (tài khoản, bài) tạo một dòng `entitlement`, hai dòng `entitlement_grant`, `attempts_total` bằng tổng hai lần cấp — bằng chứng: `EntitlementGrantConcurrencyIT` (mới), ca `concurrentGrantsShareOneEntitlement`
- AC-2: Hai đơn cùng chứa hai bài, cấp đồng thời theo thứ tự bài ngược nhau, đều xong và không deadlock — bằng chứng: `EntitlementGrantConcurrencyIT`, ca `multiExamOrdersDoNotDeadlock`
- AC-3: `grantForOrder` gọi lặp cho cùng đơn không tạo thêm lần cấp, không cộng thêm lượt — bằng chứng: `EntitlementGrantServiceIT` (mới), ca `replayedOrderGrantsOnce`
- AC-4: Mua thêm cộng lượt vào dòng `entitlement` hiện có; mỗi lần cấp giữ `option_codes`, quyền lợi, `result_view` riêng (C13, E25) — bằng chứng: `EntitlementGrantServiceIT`, ca `repurchaseAddsAttemptsAndKeepsSnapshots`
- AC-5: `reserve` lặp lại cùng `attemptId` trả cùng lần cấp, không đổi số — bằng chứng: `EntitlementPortIT` (mới), ca `reserveIsIdempotent`
- AC-6: `confirmUse` lặp lại không đổi số — bằng chứng: `EntitlementPortIT`, ca `confirmUseIsIdempotent`
- AC-7: `release` lặp lại không đổi số — bằng chứng: `EntitlementPortIT`, ca `releaseIsIdempotent`
- AC-8: Gọi sai thứ tự (`release` sau `USED`, `confirmUse` sau `RELEASED`) không đổi số — bằng chứng: `EntitlementPortIT`, ca `outOfOrderCallsChangeNothing`
- AC-9: Lần cấp cũ nhất còn lượt được dùng trước, lần cấp đã hết lượt bị bỏ qua (E21) — bằng chứng: `EntitlementPortIT`, ca `oldestGrantIsUsedFirst`
- AC-10: `describe` trả đúng lần cấp mà `reserve` kế tiếp sẽ dùng, kèm số lượt còn lại, `option_codes`, nguồn, mã đơn, ngày cấp — bằng chứng: `EntitlementPortIT`, ca `describeMatchesNextReserve`
- AC-11: `describe`, `reserve` với quyền dùng của tài khoản khác nhận 404 — bằng chứng: `EntitlementPortIT`, ca `otherAccountGets404`
- AC-12: Hết lượt thì `reserve` nhận 409 `NO_ATTEMPTS_LEFT` — bằng chứng: `EntitlementPortIT`, ca `reserveWithoutAttemptsIsRejected`
- AC-13: Hai `reserve` đồng thời cho lượt cuối: một thành công, một nhận 409 `NO_ATTEMPTS_LEFT` — bằng chứng: `EntitlementConcurrencyIT` (mới), ca `lastAttemptGoesToOneRequest`
- AC-14: `benefitsOf` trả quyền lợi của lần cấp mà lượt đã dùng, dù sau đó có lần mua mới (E21) — bằng chứng: `EntitlementPortIT`, ca `benefitsFollowUsedGrant`
- AC-15: `revokeUnused` thu hồi lượt chưa dùng và chưa giữ, không đụng lượt đang giữ; gọi lặp không đổi số — bằng chứng: `EntitlementRevokeIT` (mới), ca `revokeTakesOnlyUnusedAttempts`
- AC-16: Lượt đang giữ của lần cấp đã thu hồi vẫn `confirmUse` được và tính là đã dùng — bằng chứng: `EntitlementRevokeIT`, ca `reservedAttemptFinishesAfterRevoke`
- AC-17: `release` trên lần cấp đã hoàn hết chuyển lượt sang thu hồi, không thành lượt dùng được, kể cả khi lúc hoàn không còn lượt nào để thu hồi — bằng chứng: `EntitlementRevokeIT`, ca `releaseAfterRefundRevokes`
- AC-18: `compensate` tạo một lần cấp `COMPENSATION` 1 lượt, không có `order_id`, mang quyền lợi của lần cấp mà lượt hỏng đã dùng — bằng chứng: `EntitlementCompensationIT` (mới), ca `compensationCopiesBenefits`
- AC-19: `compensate` lần hai cho cùng lượt nhận 409 `ALREADY_COMPENSATED` (mới), kể cả hai yêu cầu đồng thời — bằng chứng: `EntitlementCompensationIT`, ca `secondCompensationIsRejected`
- AC-20: Lần cấp đã hoàn hết thì `compensate` nhận 409 `GRANT_REFUNDED`, kể cả khi mọi lượt của lần cấp đã dùng trước lúc hoàn — bằng chứng: `EntitlementCompensationIT`, ca `refundedGrantCannotBeCompensated`
- AC-21: DB từ chối dòng `entitlement` thứ hai cùng (tài khoản, bài, nguồn) khi `source_ref` trống, số lượt vượt `attempts_total`, `(order_id, exam_id)` trùng và `compensation_attempt_id` trùng — bằng chứng: `EntitlementConstraintsIT` (mới)
- AC-22: Tài khoản có quyền dùng: `deletion-check` trả `deletable = false`, xóa nhận 409 `USER_HAS_TRANSACTIONS` với nguồn `COMMERCE` — bằng chứng: `EntitlementDeletionGuardIT` (mới), ca `accountWithEntitlementCannotBeDeleted`
- AC-23: Tài khoản không có quyền dùng không bị guard này chặn — bằng chứng: `EntitlementDeletionGuardIT`, ca `accountWithoutEntitlementIsDeletable`
- AC-24: `GET /api/v1/admin/users/{id}/learning` trả đúng số lượt của tài khoản có quyền dùng, `dataAvailable = true` — bằng chứng: `EntitlementLearningProviderIT` (mới), ca `learningListsEntitlements`
- AC-25: Tài khoản chưa có quyền dùng nhận `entitlements` rỗng, `dataAvailable = false` — bằng chứng: `EntitlementLearningProviderIT`, ca `learningStaysEmptyWithoutEntitlement`

**Ảnh hưởng ngoài task:**

- Schema `commerce` phải có trong Flyway (`backend/src/main/resources/application.yml:23`) → `Thi360ApplicationTests` khởi động được với `ddl-auto: validate` [chủ: C-0]
- `NULLS NOT DISTINCT` cần PostgreSQL 15 trở lên (`infra/docker-compose.yml:8`, `backend/src/test/java/vn/unicom/thi360/AbstractIntegrationTest.java:42`, đều là `postgres:16-alpine`) → `EntitlementConstraintsIT` xanh trên `postgres:16-alpine` [chủ: task này]
- `UserAdminService` nhận thêm một `AccountDeletionGuard` thật cạnh guard giả của test (`backend/src/main/java/vn/unicom/thi360/identity/application/UserAdminService.java:176`, `:1395`; `backend/src/test/java/vn/unicom/thi360/identity/UserAccountAdminIT.java:2426`) → `UserAccountAdminIT` xanh [chủ: task này]
- Xóa hàng loạt gọi guard cho từng tài khoản (`backend/src/main/java/vn/unicom/thi360/identity/application/UserAdminService.java:1426`) → `UserAccountAdminIT` xanh; truy vấn của guard đi theo chỉ mục có `user_id` đứng đầu [chủ: task này]
- Hộp thoại không xóa được hiện nhãn theo nguồn và mã (`apps/web-admin/src/features/users/components/user-action-dialogs.tsx:351`, `apps/web-admin/src/features/users/utils.ts:163`) → dùng nguồn `COMMERCE`, mã `USER_HAS_TRANSACTIONS` đã có bản dịch; `pnpm --filter @thi360/web-admin test -- src/features/users/__tests__/user-detail-and-actions.test.tsx` xanh [chủ: task này]
- `UserAdminService` hiện nhận một `StudentLearningProvider` (`backend/src/main/java/vn/unicom/thi360/identity/application/UserAdminService.java:179`, `:396`) → C-0 đổi sang danh sách, `dataAvailable` là true khi có ít nhất một bean trả true; ca `learningListsEntitlements` [chủ: C-0]
- Hai test đang khẳng định `dataAvailable = false` với học viên chưa có dữ liệu (`backend/src/test/java/vn/unicom/thi360/identity/StudentProfileIT.java:257`, `e2e/tests/learning-profile.spec.ts:86`) → `StudentProfileIT` xanh; `pnpm --filter @thi360/e2e test -- tests/learning-profile.spec.ts` xanh [chủ: task này]
- E2E mua hàng không được dùng `student01`, vì `e2e/tests/learning-profile.spec.ts:86` cần tài khoản này chưa có quyền dùng → FS-3 và QA tạo tài khoản riêng cho luồng mua; spec này vẫn xanh sau khi chạy cả bộ E2E [chủ: FS-3, QA]
- Tab Hồ sơ học tập luôn hiện khung "Chưa có dữ liệu", kể cả khi có dữ liệu (`apps/web-admin/src/features/users/components/learning-profile-tab.tsx:112`, `apps/web-admin/src/features/users/__tests__/learning-profile-tab.test.tsx:185`) → không sửa web; `pnpm --filter @thi360/web-admin test -- src/features/users/__tests__/learning-profile-tab.test.tsx` xanh [chủ: task này]
- `StudentLearningResponse.entitlements` là `List<Object>` nên OpenAPI không đổi (`backend/src/main/java/vn/unicom/thi360/identity/api/StudentLearningResponse.java:14`, `packages/api-client/src/schema.d.ts:5432`) → `cd backend && ./mvnw -B -ntp verify && diff -u ../packages/api-client/openapi.json target/openapi.json` rỗng [chủ: task này]
- Module `commerce` cài interface của `identity/spi` (`backend/src/test/java/vn/unicom/thi360/ModularityTest.java`) → `ModularityTest` đạt [chủ: task này]
- Khóa lỗi mới trong ba tệp `messages*.properties` (`backend/src/test/java/vn/unicom/thi360/shared/web/MessageBundlesTest.java`) → `MessageBundlesTest` xanh [chủ: task này]
- `EntitlementPort` là hợp đồng của module lượt thi, gồm 409 `NO_ATTEMPTS_LEFT` (LT 5.2) và mã đơn trong `describe` (LT 5.1) → test `POST /me/attempts`, `prepare` của AT-1 xanh [chủ: AT-1]
- `confirmUse` khi lượt kết thúc với mọi lý do (`EntitlementPort`) → test đóng lượt của AT-2a xanh [chủ: AT-2a]
- `compensate` và các mã 409 của nó (LT 12) → test cấp bù của AT-5 xanh [chủ: AT-5]
- `grantForOrder` nhận `order_code` và danh sách bài của đơn; khóa ngoại `entitlement_grant.order_id` (plan B1) → test cấp quyền của CM-3a xanh [chủ: CM-3a]
- `revokeUnused` đặt `revoked_at` (`EntitlementGrantService`, mới) → test hoàn toàn bộ của CM-5 xanh [chủ: CM-5]
- Đơn bộ cấp nhiều bài trong một transaction (TM 16.3) → test cấp quyền đơn bộ của CM-6 xanh [chủ: CM-6]

**Kế hoạch kiểm thử:**

- AC-1: `EntitlementGrantConcurrencyIT#concurrentGrantsShareOneEntitlement`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementGrantConcurrencyIT`
- AC-2: `EntitlementGrantConcurrencyIT#multiExamOrdersDoNotDeadlock`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementGrantConcurrencyIT`
- AC-3: `EntitlementGrantServiceIT#replayedOrderGrantsOnce`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementGrantServiceIT`
- AC-4: `EntitlementGrantServiceIT#repurchaseAddsAttemptsAndKeepsSnapshots`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementGrantServiceIT`
- AC-5: `EntitlementPortIT#reserveIsIdempotent`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementPortIT`
- AC-6: `EntitlementPortIT#confirmUseIsIdempotent`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementPortIT`
- AC-7: `EntitlementPortIT#releaseIsIdempotent`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementPortIT`
- AC-8: `EntitlementPortIT#outOfOrderCallsChangeNothing`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementPortIT`
- AC-9: `EntitlementPortIT#oldestGrantIsUsedFirst`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementPortIT`
- AC-10: `EntitlementPortIT#describeMatchesNextReserve`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementPortIT`
- AC-11: `EntitlementPortIT#otherAccountGets404`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementPortIT`
- AC-12: `EntitlementPortIT#reserveWithoutAttemptsIsRejected`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementPortIT`
- AC-13: `EntitlementConcurrencyIT#lastAttemptGoesToOneRequest`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementConcurrencyIT`
- AC-14: `EntitlementPortIT#benefitsFollowUsedGrant`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementPortIT`
- AC-15: `EntitlementRevokeIT#revokeTakesOnlyUnusedAttempts`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementRevokeIT`
- AC-16: `EntitlementRevokeIT#reservedAttemptFinishesAfterRevoke`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementRevokeIT`
- AC-17: `EntitlementRevokeIT#releaseAfterRefundRevokes`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementRevokeIT`
- AC-18: `EntitlementCompensationIT#compensationCopiesBenefits`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementCompensationIT`
- AC-19: `EntitlementCompensationIT#secondCompensationIsRejected`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementCompensationIT`
- AC-20: `EntitlementCompensationIT#refundedGrantCannotBeCompensated`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementCompensationIT`
- AC-21: `EntitlementConstraintsIT`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementConstraintsIT`
- AC-22: `EntitlementDeletionGuardIT#accountWithEntitlementCannotBeDeleted`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementDeletionGuardIT`
- AC-23: `EntitlementDeletionGuardIT#accountWithoutEntitlementIsDeletable`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementDeletionGuardIT`
- AC-24: `EntitlementLearningProviderIT#learningListsEntitlements`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementLearningProviderIT`
- AC-25: `EntitlementLearningProviderIT#learningStaysEmptyWithoutEntitlement`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=EntitlementLearningProviderIT`
- Hồi quy:
  - Tài khoản và module: `cd backend && ./mvnw -B -ntp test -Dtest='UserAccountAdminIT,StudentProfileIT,StudentLearningProviderTests,ModularityTest,MessageBundlesTest,Thi360ApplicationTests'`
  - Web admin: `pnpm --filter @thi360/web-admin test -- src/features/users/__tests__/user-detail-and-actions.test.tsx src/features/users/__tests__/learning-profile-tab.test.tsx`
  - E2E hồ sơ học tập: `pnpm --filter @thi360/e2e test -- tests/learning-profile.spec.ts`
  - OpenAPI không đổi: `cd backend && ./mvnw -B -ntp verify && diff -u ../packages/api-client/openapi.json target/openapi.json`
  - Dòng của C-0, FS-3, QA, AT-1, AT-2a, AT-5, CM-3a, CM-5, CM-6: task chủ chạy phép kiểm đã ghi ở dòng đó.
- Kiểm thử rủi ro:
  - Quyền: task không thêm endpoint; kiểm sở hữu ở AC-11; quyền `USER.LEARNING_VIEW` và `USER.MODIFY` của hai API đọc đã có ở `StudentProfileIT`, `UserAccountAdminIT`.
  - Gửi lặp và đồng thời: AC-1, AC-2, AC-3, AC-5, AC-6, AC-7, AC-13, AC-19.
  - Migration trên DB đã có dữ liệu: `EntitlementConstraintsIT` chạy sau toàn bộ migration và dữ liệu mẫu `db/devdata`; migration chỉ tạo bảng mới.
  - Giá trị biên: lần cấp 1 lượt; lượt cuối của lần cấp cũ rồi chuyển sang lần cấp kế tiếp; `used + reserved + revoked` đúng bằng `attempts_total` và vượt 1; lần cấp đã dùng hết rồi mới bị hoàn.
  - Bất biến sau mỗi ca: số lượt của `entitlement` bằng tổng số lượt của các `entitlement_grant`.
  - đỏ trước: AC-1, AC-2, AC-9, AC-13, AC-17, AC-20.
- Dữ liệu test: tài khoản, `examId`, `orderId`, `attemptId` sinh ngẫu nhiên trong từng test, cấp quyền qua `EntitlementGrantService`; không cần bài đã công bố nên task không chờ EX-1, DS. Mỗi lane có Testcontainers riêng.
- Không kiểm: `reserve` lại một `attemptId` đã `RELEASED` (AT-1 luôn tạo `attemptId` mới cho mỗi lần bắt đầu); nguồn `CLASS` (C12: đợt này chỉ có cột, chưa có luồng cấp); phiên bản PostgreSQL của máy demo (không đọc được từ repo, migration cần bản 15 trở lên).


## CM-4b · `exam-access`, `/me/exams`

**Spec:** TM 8.3 bước 1 (điều kiện tài khoản), 10.2 (`exam-access`), 12, C13; LT 3 (`AttemptStatusProvider`); plan B1.

**Sản phẩm:**

- Interface `PendingOrderLookup` với bản tạm "không có" (B1).
- `POST /api/v1/me/exam-access/query` với `{codes}` (tối đa 48): mỗi mã một dòng `{code, state, attemptsLeft, entitlementId?, activeAttemptId?, hasPendingFulfillment, pendingOrderCode?}`, thứ tự trạng thái theo TM 10.2, ánh xạ mã sản phẩm qua bảng chiếu. Mã không có trong bảng chiếu trả `NONE`.
- `GET /api/v1/me/exams`:
  - mỗi thẻ ứng với một `entitlement` nguồn `PURCHASE`; đơn đã thu tiền chưa cấp quyền là một thẻ "Đang kiểm tra" kèm mã đơn;
  - đọc lượt đang mở và lượt có kết quả qua `AttemptStatusProvider`, số câu và thời lượng qua `PublishedExamPort`, trạng thái bán qua bảng chiếu, số lượt từ quyền đã cấp;
  - thẻ có `actions[]` theo bảng nút ở TM 12: `CONTINUE`, `START`, `VIEW_RESULT`, `HISTORY`, `REBUY`, `BUY_MORE`, `CHECKING`.
- Hai API chỉ nhận tài khoản `ACTIVE` có `access = EXAM`.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/commerce/entitlement/access/**` (mới)
- `backend/src/test/java/vn/unicom/thi360/commerce/entitlement/access/**` (mới)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi mới, nếu có)
- thêm vào: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts` (sinh lại, không sửa tay)

**Ngoài phạm vi:** bản thật của `PendingOrderLookup` (CM-3a); bản thật của `AttemptStatusProvider` (AT-1); mã bộ đề trong `exam-access` và `bundles[]` của thẻ (CM-6); nút và thẻ trên web (FS-1, FS-3); chặn tạo lượt thứ hai khi đã có lượt mở (AT-1).

**Nghiệm thu:**

- AC-1: Có lượt đang làm thì `state = IN_PROGRESS` kèm `activeAttemptId`, kể cả khi lượt còn lại bằng 0 — bằng chứng: `ExamAccessApiIT` (mới), ca `openAttemptWinsOverEverything`
- AC-2: Lượt `STARTING` cho `IN_PROGRESS` kèm `activeAttemptId` (TM 10.2) — bằng chứng: `ExamAccessApiIT`, ca `startingAttemptCountsAsInProgress`
- AC-3: Còn lượt, không có lượt mở thì `CAN_START` kèm `entitlementId`, `attemptsLeft` đúng — bằng chứng: `ExamAccessApiIT`, ca `remainingAttemptsCanStart`
- AC-4: Chưa có lượt dùng được nhưng có đơn đã thu tiền chưa cấp quyền thì `CHECKING` kèm `pendingOrderCode`, thử bằng một bản `PendingOrderLookup` giả — bằng chứng: `ExamAccessApiIT`, ca `paidOrderWithoutAttemptsIsChecking`
- AC-5: Còn lượt và có đơn mua thêm đang kẹt thì `CAN_START`, `hasPendingFulfillment = true` — bằng chứng: `ExamAccessApiIT`, ca `stuckTopUpKeepsCanStart`
- AC-6: Đã dùng hết lượt, không có đơn kẹt thì `EXHAUSTED` — bằng chứng: `ExamAccessApiIT`, ca `usedUpIsExhausted`
- AC-7: Chưa có quyền dùng, hoặc mã không có trong bảng chiếu, thì `NONE` — bằng chứng: `ExamAccessApiIT`, ca `noEntitlementOrUnknownCodeIsNone`
- AC-8: 48 mã được nhận, 49 mã nhận 400 `VALIDATION_FAILED` — bằng chứng: `ExamAccessApiIT`, ca `codesLimitIs48`
- AC-9: Tài khoản `MANAGE` gọi hai API nhận 403 — bằng chứng: `MyExamsAccessIT` (mới), ca `manageAccountGets403`
- AC-10: Tài khoản không `ACTIVE` gọi hai API nhận 403; không có phiên nhận 401 — bằng chứng: `MyExamsAccessIT`, ca `inactiveAndAnonymousAreRejected`
- AC-11: Tài khoản khác không thấy quyền dùng, lượt mở, đơn đang kiểm tra của chủ tài khoản ở cả hai API — bằng chứng: `MyExamsAccessIT`, ca `otherAccountSeesNothing`
- AC-12: Bài ngừng bán còn lượt có `START`, không có `BUY_MORE` — bằng chứng: `MyExamsApiIT` (mới), ca `discontinuedWithAttemptsCanStartOnly`
- AC-13: Hết lượt và còn bán có `REBUY` — bằng chứng: `MyExamsApiIT`, ca `exhaustedOnSaleCanRebuy`
- AC-14: Hết lượt và ngừng bán không có `REBUY`, vẫn có `HISTORY` — bằng chứng: `MyExamsApiIT`, ca `exhaustedDiscontinuedKeepsHistory`
- AC-15: Còn lượt và còn bán có `START` và `BUY_MORE` — bằng chứng: `MyExamsApiIT`, ca `onSaleWithAttemptsCanBuyMore`
- AC-16: Có lượt đang làm thì `CONTINUE` đứng đầu, vẫn có khi lượt còn lại bằng 0 — bằng chứng: `MyExamsApiIT`, ca `continueComesFirst`
- AC-17: Có lượt đã có kết quả thì có `VIEW_RESULT`, `HISTORY` cùng các nút khác — bằng chứng: `MyExamsApiIT`, ca `resultActionsAreAdded`
- AC-18: Đơn đã thu tiền chưa cấp quyền hiện thẻ `CHECKING` kèm mã đơn, không có nút thanh toán lại — bằng chứng: `MyExamsApiIT`, ca `paidOrderShowsCheckingCard`
- AC-19: Số lượt lấy từ quyền đã cấp, không đổi khi sản phẩm công bố lại với số lượt khác — bằng chứng: `MyExamsApiIT`, ca `attemptsComeFromGrantedEntitlement`
- AC-20: Số câu và thời lượng theo đường phải làm; các đường khác nhau thì trả khoảng — bằng chứng: `MyExamsApiIT`, ca `questionCountFollowsPaths`
- AC-21: Tài khoản chưa có bài nhận danh sách rỗng, mã 200 — bằng chứng: `MyExamsApiIT`, ca `emptyListIsOk`
- AC-22: Bản tạm `PendingOrderLookup` luôn trả "không có"; test ghi rõ đây là hành vi tạm — bằng chứng: `NoPendingOrderLookupTest` (mới)
- AC-23: Chưa có module `attempt`, hai API vẫn chạy với bản rỗng mặc định của `AttemptStatusProvider` — bằng chứng: `MyExamsApiIT`, ca `worksWithDefaultAttemptStatusProvider`
- AC-24: Thẻ có tên sản phẩm, kỳ thi, hình thức, phần hoặc môn, lượt còn lại và tình trạng (Có thể bắt đầu, Đang làm, Hết lượt) — bằng chứng: `MyExamsApiIT`, ca `cardCarriesSpecFields`

**Ảnh hưởng ngoài task:**

- `AttemptStatusProvider` có bản rỗng mặc định, lấy qua `ObjectProvider` (`commerce/spi`, plan B1) → ca `worksWithDefaultAttemptStatusProvider` [chủ: C-0]
- AT-1 cài bản thật, trả cả lượt `STARTING` kèm trạng thái (LT 5.2, TM 10.2) → `ExamAccessApiIT` chạy lại trên bản thật của AT-1 [chủ: AT-1]
- `PendingOrderLookup` bản tạm (plan B1) → CM-3a thay bản thật, thêm test `exam-access` trả `CHECKING` với đơn thật [chủ: CM-3a]
- `PublishedExamPort.findServing` phải đọc được phiên bản phục vụ cuối của bài `DISCONTINUED` (plan EX-1) → ca `discontinuedWithAttemptsCanStartOnly` [chủ: EX-1]
- Đọc mã sản phẩm và `sale_state` từ bảng chiếu (`commerce.storefront_product`, CM-1) → `StorefrontProjectionIT` và `MyExamsApiIT` xanh [chủ: task này]
- Đọc số lượt qua bảng của CM-4a (`commerce/entitlement`) → `EntitlementPortIT` xanh [chủ: task này]
- `/api/v1/me/**` đã yêu cầu đăng nhập nhờ quy tắc chung (`backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java:115`) nhưng không phân biệt `access` → không sửa `SecurityConfig`; ca `manageAccountGets403`, `inactiveAndAnonymousAreRejected` [chủ: task này]
- Module `commerce` gọi `exam/spi`, `identity/spi` (`backend/src/test/java/vn/unicom/thi360/ModularityTest.java`) → `ModularityTest` đạt [chủ: task này]
- Khóa lỗi mới, nếu có, trong ba tệp `messages*.properties` (`backend/src/test/java/vn/unicom/thi360/shared/web/MessageBundlesTest.java`) → `MessageBundlesTest` xanh [chủ: task này]
- OpenAPI và client sinh (`packages/api-client/openapi.json`, `.github/workflows/backend.yml:28`) → sinh lại; `diff -u ../packages/api-client/openapi.json target/openapi.json` rỗng; `pnpm typecheck` xanh [chủ: task này]
- Nút ở trang chi tiết dùng `state`, `hasPendingFulfillment` (TM 11.3) → test nút theo `exam-access` của FS-1 xanh [chủ: FS-1]
- Thẻ `/home` và màn xác nhận mua dùng `actions[]`, `entitlementId`, `pendingOrderCode` (TM 8.2, 12) → test `/home`, `/checkout/[code]` của FS-3 xanh [chủ: FS-3]
- Mã bộ đề trong `exam-access`, `bundles[]` của thẻ (TM 16.4) → CM-6 mở rộng; `ExamAccessApiIT`, `MyExamsApiIT` vẫn xanh [chủ: CM-6]

**Kế hoạch kiểm thử:**

- AC-1: `ExamAccessApiIT#openAttemptWinsOverEverything`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=ExamAccessApiIT`
- AC-2: `ExamAccessApiIT#startingAttemptCountsAsInProgress`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=ExamAccessApiIT`
- AC-3: `ExamAccessApiIT#remainingAttemptsCanStart`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=ExamAccessApiIT`
- AC-4: `ExamAccessApiIT#paidOrderWithoutAttemptsIsChecking`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=ExamAccessApiIT`
- AC-5: `ExamAccessApiIT#stuckTopUpKeepsCanStart`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=ExamAccessApiIT`
- AC-6: `ExamAccessApiIT#usedUpIsExhausted`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=ExamAccessApiIT`
- AC-7: `ExamAccessApiIT#noEntitlementOrUnknownCodeIsNone`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=ExamAccessApiIT`
- AC-8: `ExamAccessApiIT#codesLimitIs48`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=ExamAccessApiIT`
- AC-9: `MyExamsAccessIT#manageAccountGets403`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsAccessIT`
- AC-10: `MyExamsAccessIT#inactiveAndAnonymousAreRejected`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsAccessIT`
- AC-11: `MyExamsAccessIT#otherAccountSeesNothing`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsAccessIT`
- AC-12: `MyExamsApiIT#discontinuedWithAttemptsCanStartOnly`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-13: `MyExamsApiIT#exhaustedOnSaleCanRebuy`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-14: `MyExamsApiIT#exhaustedDiscontinuedKeepsHistory`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-15: `MyExamsApiIT#onSaleWithAttemptsCanBuyMore`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-16: `MyExamsApiIT#continueComesFirst`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-17: `MyExamsApiIT#resultActionsAreAdded`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-18: `MyExamsApiIT#paidOrderShowsCheckingCard`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-19: `MyExamsApiIT#attemptsComeFromGrantedEntitlement`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-20: `MyExamsApiIT#questionCountFollowsPaths`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-21: `MyExamsApiIT#emptyListIsOk`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-22: `NoPendingOrderLookupTest`, unit, `cd backend && ./mvnw -B -ntp test -Dtest=NoPendingOrderLookupTest`
- AC-23: `MyExamsApiIT#worksWithDefaultAttemptStatusProvider`, tích hợp, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- AC-24: `MyExamsApiIT#cardCarriesSpecFields`, hợp đồng, `cd backend && ./mvnw -B -ntp test -Dtest=MyExamsApiIT`
- Hồi quy:
  - Module cùng vùng: `cd backend && ./mvnw -B -ntp test -Dtest='EntitlementPortIT,StorefrontProjectionIT,ModularityTest,MessageBundlesTest'`
  - OpenAPI: `cd backend && ./mvnw -B -ntp verify && diff -u ../packages/api-client/openapi.json target/openapi.json`
  - Client sinh: `cp backend/target/openapi.json packages/api-client/openapi.json && pnpm --filter @thi360/api-client generate && pnpm typecheck`
  - Dòng của C-0, AT-1, CM-3a, EX-1, FS-1, FS-3, CM-6: task chủ chạy phép kiểm đã ghi ở dòng đó.
- Kiểm thử rủi ro:
  - Quyền: tài khoản `EXAM` đang hoạt động được gọi; `MANAGE`, không `ACTIVE`, không phiên đều bị từ chối ở cả hai API (AC-9, AC-10); dữ liệu tách theo tài khoản (AC-11).
  - Gửi lặp và đồng thời: không áp dụng, hai API chỉ đọc.
  - Migration trên DB đã có dữ liệu: không áp dụng, task không thêm migration.
  - Giá trị biên: 48 và 49 mã; `attemptsLeft` bằng 0 và 1; lượt còn lại bằng 0 khi đang có lượt mở; quyền dùng có toàn bộ lượt đã bị thu hồi.
  - đỏ trước: AC-1, AC-4, AC-5, AC-12, AC-14.
- Dữ liệu test: quyền dùng cấp qua `EntitlementGrantService`; dòng bảng chiếu do test dựng; bản giả của `AttemptStatusProvider`, `PendingOrderLookup`, `PublishedExamPort` cho từng tình huống. Tài khoản và mã sản phẩm sinh ngẫu nhiên trong từng test; mỗi lane có Testcontainers riêng.
- Không kiểm: luồng đầu cuối với đơn thật và lượt thật (CM-3a, AT-1, FS-3 kiểm khi các task đó về); hiển thị nút trên web (FS-1, FS-3).

## CM-3a · Đơn hàng, cổng giả lập, IPN, cấp quyền

**Spec:** BS 14; TM 5.3 (thư đơn hàng), 8.1–8.4, 8.5 (thủ tục cấp quyền), 8.6, 13, E8, E12–E14, E22, E25, E26; TM 14 (ca đơn, VNPAY, cấp quyền); plan B1, B2, B4, B5, B6.

**Sản phẩm:**

- Bảng `purchase_order`, `payment`, `payment_event`, `order_history`. Trạng thái theo TM 8.1, gồm `REFUNDED`. `payment_event.payment_id` để trống được (sai chữ ký, không có `txn_ref`). Thêm khóa ngoại cho `promotion_redemption.order_id`, `entitlement_grant.order_id` (B1).
- Tạo bảng theo hình dạng đã có bộ đề (TM 8.1, 16.3), để CM-6 không phải đổi lại:
  - `purchase_order` có `product_type` (chỉ `EXAM` được dùng tới CM-6), `bundle_id`, `bundle_version_no` (để trống);
  - bảng `purchase_order_item`; đơn mua một bài ghi một dòng; cấp quyền chạy theo từng dòng (TM 8.5);
  - chỉ mục đơn chờ duy nhất theo `(user_id, product_code)`.
- `/me/orders` (tạo, xem, danh sách, `/pay`, `/cancel`). Kiểm `access = EXAM`, tài khoản, phiên bản, giá; E13 dùng lại hoặc thay đơn chờ.
  - Kết quả idempotency lưu trong transaction tạo đơn, trước khi cấp quyền (`IdempotencyService.executeOnce`), nên `next` của đơn 0 đồng tính sau commit theo trạng thái đơn, ở cả lần đầu và lần gửi lại.
  - 409 `PRICE_CHANGED` kèm `PriceQuote` mới và 410 `PRODUCT_DISCONTINUED`: `ApiError` không có chỗ cho dữ liệu kèm theo và `GlobalExceptionHandler` không có nhánh 410, nên xử lý bằng `@ExceptionHandler` đặt trong controller của đơn; thân lỗi giữ đủ `code`, `message`, `fieldErrors`, `traceId`, thêm `quote` (409) hoặc `product: {code, name}` (410, cùng dạng với API công khai của CM-1). Không sửa `shared/api`.
  - Tính giá và tạo đơn truyền đơn chờ của chính học viên vào `PriceCalculator`, để lượt giữ của đơn đó không tính vào giới hạn (TM 7.2 bước 7).
- Interface `VnpayGateway` (`pay`, `verify`, `queryDr`, `refund`) và bản giả lập đủ bốn hàm. `THI360_VNPAY_MODE` (`MOCK`, `SANDBOX`, `PRODUCTION`):
  - `MOCK` được bật cùng profile `dev`, `test`, `e2e`, hoặc khi chưa cấu hình khóa (bản demo chưa có khóa, TM 8.4);
  - `MOCK` cùng profile `prod` thì ứng dụng không khởi động;
  - khối `on-profile` của `dev`, `test`, `e2e` đặt mặc định `MOCK`; profile khác không có mặc định, thiếu biến thì không khởi động.
- Trang `/mock-vnpay` trên backend, chỉ có khi đang ở `MOCK` và profile `dev`, `test` hoặc `e2e`: ba nút "Thành công", "Thất bại", "Không trả lời"; hai nút đầu gọi IPN có chữ ký. Nút dùng `GET` tới URL IPN đã ký sẵn (cookie đăng nhập cùng host làm `CsrfFilter` chặn `POST`). Không có trong OpenAPI.
- IPN `GET /api/v1/public/payments/vnpay/ipn`: luôn HTTP 200, `RspCode` theo bảng TM 8.4, miễn giới hạn tần suất, lọc IP tùy chọn qua `ClientIpResolver` (B6).
- Trang quay lại `GET /api/v1/public/payments/vnpay/return`: chỉ kiểm chữ ký và ghi `payment_event`, không đổi trạng thái.
- Tiền về khi đơn đã `REPLACED` hoặc `CANCELLED` (TM 8.6); thu trùng (`is_duplicate`).
- Quy tắc tiền về theo BS 14, đặt trong một lớp `PaymentOutcomeApplier` (mới), dùng chung cho IPN và `querydr`:
  - cột `payment.excess_reason`, `payment.late_success_review`, `payment.late_success_at`; chỉ mục duy nhất từng phần `payment (order_id) WHERE state = 'SUCCEEDED' AND is_duplicate = false`;
  - ca A khoản được tính; ca B khoản thu thừa, kể cả đơn đã `REFUNDED`; ca C chờ đối soát; ca D thông báo lặp chỉ ghi `payment_event`;
  - khoản đang chờ đối soát tự thành khoản thu thừa khi đơn có khoản được tính khác;
  - `needs_review` chỉ đặt khi đơn `REPLACED` và đơn thay thế cùng đã trả tiền, theo cả hai thứ tự về tiền.
- Đơn 0 đồng (`FREE`).
- `FulfillmentService` theo B2, gọi `EntitlementGrantService.grantForOrder`; lỗi cấp quyền không làm hỏng lần thu tiền; worker cấp quyền lại mỗi phút trong 30 phút (B5).
- Thư `ORDER_FULFILLED` chỉ xếp hàng khi email của tài khoản đã xác minh (TM 5.3); model truyền danh sách bài được cấp theo mẫu của ML. Hàm mới `UserDirectory.findMailContact(userId)` (mới) trả email, trạng thái xác minh, ngôn ngữ ưa dùng.
- Cổng tạm `RefundProgress` (mới, trong `commerce/fulfillment`) cho TM 8.5 bước 1: bản tạm trả "chưa có lần hoàn", có test ghi rõ hành vi tạm; CM-5 thay bằng bản thật (theo quy ước cổng tạm ở B1).
- Thay bản tạm `PurchaseHistory`, `PendingOrderLookup` bằng bản thật. `AccountDeletionGuard`: thêm "có đơn" (`COMMERCE`, `USER_HAS_ORDERS`).
- Khóa `thi360.commerce.scheduling.enabled` (mặc định bật, khối `test` tắt), cho mọi worker của `commerce`.
- `.env.example` có tên các biến `THI360_VNPAY_*`.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/commerce/order/**`
- `backend/src/main/java/vn/unicom/thi360/commerce/payment/**` (trừ `payment/vnpay/**` của CM-3b)
- `backend/src/main/java/vn/unicom/thi360/commerce/fulfillment/**`
- `backend/src/main/resources/db/migration/commerce/` (migration mới của task)
- `backend/src/test/java/vn/unicom/thi360/commerce/order/**`, `commerce/payment/**`, `commerce/fulfillment/**`, `commerce/support/**`
- sửa đúng chỗ bản tạm: `PurchaseHistory` (trong `commerce/pricing` hoặc `commerce/promotion`, của CM-2), `PendingOrderLookup` (trong `commerce/entitlement`, của CM-4b) và test ghi hành vi tạm của hai bản đó
- thêm vào: `backend/src/main/java/vn/unicom/thi360/identity/spi/UserDirectory.java`, `identity/application/UserDirectoryImpl.java` (hàm `findMailContact`)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/shared/security/SecurityConfig.java`, `CookieAuthFilter.java` (đường `/mock-vnpay`)
- thêm vào: `backend/src/main/resources/application.yml` (`thi360.vnpay.*`, `thi360.commerce.scheduling.enabled`; khối `on-profile` của `dev`, `test`, `e2e`)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi đơn hàng)
- thêm vào: `.env.example` (`THI360_VNPAY_*`, chỉ tên)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** cổng VNPAY thật, `querydr`, worker đơn hết hạn, tiền về sau `EXPIRED` (CM-3b); admin xem đơn, hoàn tiền, `POST /orders/{code}/fulfill`, đối soát (CM-5); đơn `product_type = BUNDLE` (CM-6); trang `/checkout/**`, `/account/orders` (FS-3); chạy thật trên sandbox (VP-2).

**Nghiệm thu:**

- AC-1: Migration tạo đủ năm bảng; có chỉ mục duy nhất đơn chờ `(user_id, product_code) WHERE state = 'PENDING_PAYMENT'`, `txn_ref` duy nhất, `vnp_transaction_no` duy nhất khi có giá trị, duy nhất `(order_id, exam_id)` ở `purchase_order_item` — bằng chứng: `OrderSchemaMigrationIT` (mới)
- AC-2: Migration khóa ngoại chạy được trên DB đã có dòng: `entitlement_grant` loại `COMPENSATION` (`order_id` trống) còn nguyên; dòng có `order_id` không khớp đơn nào thì migration dừng báo lỗi, không xóa dữ liệu — bằng chứng: `OrderSchemaMigrationIT` (mới)
- AC-3: Tạo đơn hợp lệ: chụp đủ cột TM 8.1 và một dòng `purchase_order_item`; mã đơn và `txn_ref` đúng E14; trả `next = REDIRECT`, `paymentUrl` có `vnp_Amount = amount_due × 100` — bằng chứng: `OrderCreateIT` (mới)
- AC-4: Gửi lại cùng `Idempotency-Key` trả cùng đơn và cùng `paymentUrl`, không thêm đơn, không thêm `payment`. Cùng khóa khác thân thì 409 `IDEMPOTENCY_KEY_REUSED`; thiếu khóa thì 400 `IDEMPOTENCY_KEY_REQUIRED` — bằng chứng: `OrderCreateIT` (mới)
- AC-5: Đổi tài khoản giữa chừng thì 409 `ACCOUNT_CHANGED`, không tạo đơn — bằng chứng: `OrderCreateIT` (mới)
- AC-6: Giá hoặc phiên bản đổi thì 409 `PRICE_CHANGED`, thân lỗi có `PriceQuote` mới, không tạo đơn — bằng chứng: `OrderCreateIT` (mới)
- AC-7: Sản phẩm ngừng bán thì 410 `PRODUCT_DISCONTINUED`, không tạo đơn — bằng chứng: `OrderCreateIT` (mới)
- AC-8: Mọi API `/me/orders*`: không có cookie thì 401; tài khoản `MANAGE` hoặc không `ACTIVE` thì 403 — bằng chứng: `OrderAccessIT` (mới)
- AC-9: E13: cùng giá và cùng voucher thì dùng lại đơn chờ, thêm một `payment`; khác thì đơn cũ `REPLACED`, có liên kết sang đơn mới, lượt voucher được trả. 10 yêu cầu đồng thời khác khóa cho cùng sản phẩm chỉ để lại một đơn `PENDING_PAYMENT` — bằng chứng: `OrderReplaceIT` (mới)
- AC-10: Voucher sai hoặc hết lượt thì trả đúng mã `VOUCHER_*` của TM 7.2, không tạo đơn, `used_count` không đổi — bằng chứng: `OrderCreateIT` (mới)
- AC-11: `GET /me/orders` và `GET /me/orders/{code}` chỉ trả đơn của chính tài khoản; đơn của tài khoản khác trả 404 ở cả xem, `/pay`, `/cancel` — bằng chứng: `OrderAccessIT` (mới)
- AC-12: `/pay` tạo `payment` mới theo giá đã chụp kể cả khi khuyến mãi đã hết; `vnp_ExpireDate = min(lúc tạo + 15 phút, expires_at)`. Đơn đã hết hạn, sản phẩm ngừng bán hoặc đơn không còn chờ thì bị từ chối, không tạo `payment`. Gửi lại cùng khóa không tạo thêm `payment` — bằng chứng: `OrderPayCancelIT` (mới)
- AC-13: `/cancel` chỉ nhận đơn chờ: đơn thành `CANCELLED`, lượt voucher được trả; đơn ở trạng thái khác thì 409 — bằng chứng: `OrderPayCancelIT` (mới)
- AC-14: Biên E26 với mức tối thiểu 5.000: phải trả đúng 5.000 thì `REDIRECT`; 4.999 và 1 đồng thì thành đơn 0 đồng; đặt `THI360_VNPAY_MIN_AMOUNT = 0` thì 1 đồng vẫn `REDIRECT` — bằng chứng: `OrderAmountBoundaryIT` (mới)
- AC-15: Đơn 0 đồng thành `PAID` rồi `FULFILLED`, `payment_method = FREE`, không có dòng `payment`, không gọi `VnpayGateway`, trả `next = FULFILLED` — bằng chứng: `FreeOrderIT` (mới)
- AC-16: Đơn 0 đồng cấp quyền lỗi: đơn giữ `PAID`, trả `next = CHECKING`; worker cấp lại thành `FULFILLED` — bằng chứng: `FreeOrderIT` (mới)
- AC-17: Bản giả lập đủ bốn hàm. Trên `/mock-vnpay`: "Thành công" đưa đơn tới `FULFILLED`, "Thất bại" đưa `payment` về `FAILED`, "Không trả lời" giữ `CREATED`. Trang mở được khi trình duyệt còn cookie hết hạn — bằng chứng: `MockVnpayGatewayTest` (mới), `MockVnpayPageIT` (mới)
- AC-18: Khởi động theo bảng: `MOCK` với `dev`, `test`, `e2e` đạt; `MOCK` không có ba profile đó mà chưa có khóa đạt, đã có khóa thì lỗi; `MOCK` cùng `prod` luôn lỗi; thiếu `THI360_VNPAY_MODE` ở profile không có mặc định thì lỗi. Ở `SANDBOX` (kể cả profile `dev`) `/mock-vnpay` trả 404. `/mock-vnpay` không có trong `openapi.json` — bằng chứng: `VnpayModeStartupTest` (mới), `MockVnpayPageIT` (mới)
- AC-19: IPN sai chữ ký trả `97`, ghi `payment_event` với `signature_valid = false`, không đổi `payment`, đơn — bằng chứng: `VnpayIpnIT` (mới)
- AC-20: IPN không có `txn_ref` trả `01`; sai số tiền trả `04`, kể cả khi lần thanh toán đã có kết quả cuối (kiểm số tiền trước trạng thái) — bằng chứng: `VnpayIpnIT` (mới)
- AC-21: IPN `00`/`00`: `payment` thành `SUCCEEDED`, đơn `PAID` rồi `FULFILLED`, đúng một `entitlement_grant`, trả `00` — bằng chứng: `VnpayIpnIT` (mới)
- AC-22: Gửi lặp 10 lần tuần tự: một lần ghi nhận, một quyền, các lần sau trả `02`. 20 lần đồng thời: một quyền, đúng một lần trả `00`, còn lại `02` — bằng chứng: `VnpayIpnConcurrencyIT` (mới)
- AC-23: Lỗi nội bộ trước khi ghi nhận trả `99`, không để lại thay đổi dở; VNPAY gọi lại thì trả `00` và chỉ một quyền — bằng chứng: `VnpayIpnIT` (mới)
- AC-24: `vnp_ResponseCode` `09`, `10`, `11`, `12`, `13`, `24`, `51`, `65`, `75`, `79` thì `FAILED`; `07`, `99` và mã không có trong bảng thì `UNKNOWN`, không phải `FAILED`; đơn vẫn `PENDING_PAYMENT`, IPN trả `00` — bằng chứng: `VnpayIpnIT` (mới)
- AC-25: Mọi nhánh IPN trả HTTP 200 với JSON `{RspCode, Message}`, kể cả thiếu tham số; gọi không cần cookie; 130 lần gọi trong một phút từ một IP không có 429; IP ngoài `THI360_VNPAY_IPN_ALLOWED_IPS` trả `97` và ghi `payment_event`; `X-Forwarded-For` giả từ nguồn không tin cậy không qua được bộ lọc; danh sách trống thì không lọc — bằng chứng: `VnpayIpnIT` (mới)
- AC-26: Trang quay lại đúng chữ ký trả `{orderCode, paymentState}`, không có thông tin người mua, ghi `payment_event` nguồn `RETURN`, không đổi trạng thái. Sai chữ ký trả 422 `VNPAY_SIGNATURE_INVALID` (mã mới), không có `orderCode`, không cấp quyền — bằng chứng: `VnpayReturnIT` (mới)
- AC-27: Tiền về sau `REPLACED` hoặc `CANCELLED`: đơn `PAID`, cấp quyền theo giá và quyền lợi đã chụp kể cả khi sản phẩm đã ngừng bán, ghi lý do vào `order_history`; chỉ đặt `needs_review` khi đơn `REPLACED` có đơn thay thế đã trả tiền (BS 14); lượt voucher `RELEASED` thành `CONFIRMED`, `used_count` tăng, vượt `total_limit` thì `over_limit_count` tăng — bằng chứng: `LatePaymentIT` (mới)
- AC-28: Thu trùng: lần thu sau trên đơn `PAID` hoặc `FULFILLED` có `is_duplicate = true`, không cấp thêm, IPN trả `00`. Hai lần thanh toán của cùng đơn báo thành công đồng thời: một lần không trùng, một lần trùng, một quyền — bằng chứng: `LatePaymentIT` (mới), `VnpayIpnConcurrencyIT` (mới)
- AC-29: Cấp quyền theo TM 8.5: mỗi dòng `purchase_order_item` một `entitlement_grant` mang `option_codes`, quyền lợi đã chụp; `attempts_total` tăng; lần dùng voucher `CONFIRMED`; đơn `FULFILLED`; có nhật ký `ENTITLEMENT_GRANTED`. Gọi lại không đổi số — bằng chứng: `FulfillmentServiceIT` (mới)
- AC-30: Cấp quyền lỗi: đơn giữ `PAID`, bản ghi thu tiền còn nguyên, ghi `fulfillment_error`, tăng `fulfillment_tries`, IPN vẫn trả `00`. Worker thử lại mỗi phút và thành công thì `FULFILLED`; quá 30 phút từ `paid_at` thì worker không thử nữa — bằng chứng: `FulfillmentRetryWorkerIT` (mới)
- AC-31: IPN và worker cấp quyền cùng lúc cho một đơn: một `entitlement_grant`, `attempts_total` chỉ cộng một lần — bằng chứng: `FulfillmentConcurrencyIT` (mới)
- AC-32: `ORDER_FULFILLED` xếp đúng một thư khi email đã xác minh, theo ngôn ngữ ưa dùng; email chưa xác minh thì không có thư, đơn vẫn `FULFILLED` — bằng chứng: `OrderMailIT` (mới)
- AC-33: `exam-access` trả `CHECKING` cho đơn đã thu tiền chưa cấp quyền, kèm `pendingOrderCode`; còn lượt thì `CAN_START` và `hasPendingFulfillment = true` — bằng chứng: `OrderPortsIT` (mới)
- AC-34: `PurchaseHistory` bản thật: tài khoản có đơn `FULFILLED` không còn là "tài khoản mới"; tài khoản chỉ có đơn `CANCELLED` hoặc `PENDING_PAYMENT` vẫn là "tài khoản mới" (E8) — bằng chứng: `OrderPortsIT` (mới)
- AC-35: Tài khoản có đơn ở bất kỳ trạng thái nào không xóa được, blocker `COMMERCE`/`USER_HAS_ORDERS`; tài khoản không có đơn thì guard này không chặn — bằng chứng: `OrderDeletionGuardIT` (mới)
- AC-36: `.env.example` có đủ `THI360_VNPAY_MODE`, `THI360_VNPAY_TMN_CODE`, `THI360_VNPAY_HASH_SECRET`, `THI360_VNPAY_PAY_URL`, `THI360_VNPAY_API_URL`, `THI360_VNPAY_IPN_ALLOWED_IPS`, không có giá trị khóa — bằng chứng: lệnh `grep` ở Kế hoạch kiểm thử
- AC-37: Đặt `thi360.commerce.scheduling.enabled = false` thì không có bean worker của `commerce` — bằng chứng: `FulfillmentRetryWorkerIT` (mới)
- AC-38: 20 yêu cầu tạo đơn đồng thời từ 20 tài khoản với voucher còn 1 lượt: đúng một đơn được tạo, 19 yêu cầu nhận `VOUCHER_EXHAUSTED`, `used_count` bằng `total_limit` (TM 14) — bằng chứng: `OrderVoucherConcurrencyIT` (mới)
- AC-39: Học viên có đơn chờ đang giữ lượt cuối của voucher: `/me/price-quote` và tạo lại đơn với cùng voucher vẫn áp được; học viên khác nhận `VOUCHER_EXHAUSTED` (TM 7.2 bước 7) — bằng chứng: `OrderReplaceIT` (mới)
- AC-40: Mua thêm bài đã có quyền: thêm một `entitlement_grant`, `attempts_total` cộng dồn trên cùng dòng `entitlement` (TM 14) — bằng chứng: `FulfillmentServiceIT` (mới)
- AC-41: Mỗi đơn có tối đa một khoản được tính: chỉ mục duy nhất từ chối khoản thứ hai ở tầng DB; hai lần thanh toán báo thành công đồng thời cho một khoản được tính, một khoản thu thừa — bằng chứng: `PaymentCountingRulesIT#oneCountedPaymentPerOrder`, `#parallelSuccessCountsOnce` (mới)
- AC-42: Ca B: lần thanh toán `FAILED` hoặc chưa có kết quả báo thành công khi đơn đã có khoản được tính: `SUCCEEDED`, `is_duplicate = true`, `excess_reason = AFTER_COUNTED`, trạng thái đơn và số `entitlement_grant` không đổi, IPN trả `00` — bằng chứng: `PaymentCountingRulesIT#failedAttemptAfterCountedIsExcess`, `#openAttemptAfterCountedIsExcess` (mới)
- AC-43: Ca B trên đơn `REFUNDED`: khoản về sau là khoản thu thừa, đơn giữ `REFUNDED`, không cấp quyền, không mở lại bài — bằng chứng: `PaymentCountingRulesIT#successAfterRefundNeverReopens` (mới)
- AC-44: Ca C: lần `FAILED` báo thành công khi đơn chưa có khoản được tính: `payment` giữ `FAILED`, `late_success_review = PENDING`, không có `entitlement_grant`, IPN trả `00` — bằng chứng: `PaymentCountingRulesIT#failedAttemptWaitsForReconcile` (mới)
- AC-45: Đang chờ đối soát mà lần khác của đơn thành công: lần mới là khoản được tính, lần đang chờ thành khoản thu thừa với `late_success_review = AUTO_EXCESS`, một quyền — bằng chứng: `PaymentCountingRulesIT#pendingReviewBecomesExcessWhenAnotherCounts` (mới)
- AC-46: Ca D: thông báo lặp của ca B, ca C trả `02`, chỉ thêm dòng `payment_event`, số khoản thu và số tiền cần hoàn không đổi — bằng chứng: `PaymentCountingRulesIT#repeatedNoticeAddsNothing` (mới)
- AC-47: Ca chuẩn của BS 14: lần 1 ghi thất bại, lần 2 thành công, VNPAY báo lần 1 thành công: lần 1 là khoản thu thừa, còn hoàn được đúng số tiền của lần 1, học viên giữ bài; VNPAY gửi lại thông báo của lần 1 thì không ghi nhận thêm và không tạo yêu cầu hoàn mới — bằng chứng: `PaymentCountingRulesIT#failedThenRetriedThenLateSuccessScenario` (mới)
- AC-48: `needs_review`: đơn `REPLACED` nhận tiền khi đơn thay thế đã trả thì có cờ; đơn thay thế trả sau khi đơn `REPLACED` đã nhận tiền thì đơn cũ có cờ; đơn `REPLACED` có đơn thay thế chưa trả và đơn `CANCELLED` không có cờ — bằng chứng: `LatePaymentIT#flagWhenReplacementAlreadyPaid`, `#flagWhenReplacementPaysLater`, `#noFlagWithoutDoubleCharge` (mới)

**Ảnh hưởng ngoài task:**

- Luồng xác thực khi thêm đường công khai (`shared/security/SecurityConfig.java`, `CookieAuthFilter.SKIP_EXACT_PATHS`) → chạy `AuthFlowIT`, `SessionIT`; `MockVnpayPageIT` (mới) có ca cookie hết hạn [chủ: task này]
- Giới hạn tần suất của `/api/v1/public/**` và phần miễn cho IPN do C-0 làm (`shared/security/RateLimitFilter.java`) → `VnpayIpnIT` (mới) ca 130 lần gọi không có 429; chạy `RateLimitFilterIT` [chủ: task này]
- Lọc IP dựa vào proxy tin cậy (`shared/security/ClientIpResolver.java`, `TrustedProxyTomcatConfig.java`) → `VnpayIpnIT` (mới) ca `X-Forwarded-For` giả; chạy `ClientIpResolverTests`, `TrustedProxyTomcatIT` [chủ: task này]
- Cấu hình dùng chung và mọi profile (`backend/src/main/resources/application.yml`) → `VnpayModeStartupTest` (mới); chạy `Thi360ApplicationTests` [chủ: task này]
- Mọi test tích hợp đang bật profile `worker` (`AbstractIntegrationTest`: `api`, `worker`, `test`) nên worker mới chạy nền trong test nếu không tắt → khối `test` đặt `thi360.commerce.scheduling.enabled: false`; `FulfillmentRetryWorkerIT` (mới) gọi thẳng hàm của worker [chủ: task này]
- E2E khởi động backend bằng `api,worker,dev` (`e2e/playwright.config.ts`, `.github/workflows/e2e.yml`) → `VnpayModeStartupTest` (mới) phủ `dev` và `e2e`; luồng mua bằng cổng giả lập chạy ở E2E của FS-3 [chủ: FS-3]
- Mã lỗi mới (`messages.properties`, `messages_vi.properties`, `messages_en.properties`) → chạy `MessageBundlesTest` [chủ: task này]
- Xóa tài khoản đọc mọi guard (`identity/application/UserAdminService.java` `collectDeletionBlockers`, `identity/api/AdminUserController.java`, `identity/api/DeletionCheckResponse.java`) → chạy `UserAccountAdminIT`; `OrderDeletionGuardIT` (mới) có ca cùng lúc guard "có quyền dùng" của CM-4a, kết quả có đủ hai blocker [chủ: task này]
- Hợp đồng `UserDirectory` được `classroom`, `exam`, `questionbank` dùng (`identity/spi/UserDirectory.java`, `identity/application/UserDirectoryImpl.java`) → chỉ thêm hàm, không đổi record; `OrderMailIT` (mới) kiểm giá trị của hàm mới; chạy `UserAccountAdminIT`, `ModularityTest` [chủ: task này]
- Bản tạm `PurchaseHistory` và ca ghi hành vi tạm `newAccountUsesProvisionalHistory` của CM-2 (`commerce/pricing`, `commerce/promotion`) → sửa ca đó theo bản thật; `OrderPortsIT` (mới); chạy toàn bộ test `commerce/pricing`, `commerce/promotion` [chủ: task này]
- Bản tạm `PendingOrderLookup` và bản giả trong `ExamAccessApiIT`, `MyExamsApiIT` của CM-4b (`commerce/entitlement/access`) → `OrderPortsIT` (mới); chạy toàn bộ test `commerce/entitlement` [chủ: task này]
- Giữ, xác nhận, trả và lấy lại lượt voucher (`commerce/promotion`: `reserve`, `confirm`, `release`, `reclaim` của CM-2) → `LatePaymentIT` (mới) kiểm `reclaim` có tăng `over_limit_count`; `OrderVoucherConcurrencyIT` (mới) kiểm 20 yêu cầu đồng thời [chủ: task này]
- Cấp quyền theo đơn (`commerce/entitlement`: `EntitlementGrantService.grantForOrder`) → `FulfillmentServiceIT` (mới); chạy toàn bộ test `commerce/entitlement` [chủ: task này]
- Hàng thư và mẫu `ORDER_FULFILLED` (`MailQueue.enqueue`, `resources/mail/templates/`) → `OrderMailIT` (mới) đọc thư qua `MailOutbox` [chủ: task này]
- Đọc bảng chiếu có khóa dòng khi tạo đơn (`commerce/storefront`, `storefront_product`) → chạy toàn bộ test `commerce/storefront` [chủ: task này]
- Trang quay lại nằm dưới `/api/v1/public/**` (danh sách khóa cấm của `ForbiddenKeysAssert`) → `VnpayReturnIT` (mới) quét phản hồi bằng `ForbiddenKeysAssert` [chủ: task này]
- Hợp đồng API và kiểu sinh ra (`packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`) → chạy `OpenApiExportIT`, so `backend/target/openapi.json` với bản commit, sinh lại client, `pnpm typecheck` [chủ: task này]
- `ApplicationModules.verify()` khi `commerce` dùng thêm `identity/spi`, `shared/mail` (`ModularityTest`) → chạy `ModularityTest` [chủ: task này]
- `VnpayGateway`, dịch vụ ghi nhận thu tiền, hàm cấp quyền được cổng thật và `querydr` dùng → CM-3b chạy lại `VnpayIpnIT` (mới), `LatePaymentIT` (mới) [chủ: CM-3b]
- `RefundProgress`, `FulfillmentService` được hoàn tiền và nút tiếp tục cấp quyền dùng → CM-5 thay bản tạm, chạy lại `FulfillmentServiceIT` (mới) [chủ: CM-5]
- `purchase_order_item`, chỉ mục đơn chờ, cấp quyền theo dòng được đơn bộ dùng → CM-6 chạy lại toàn bộ test `commerce/order`, `commerce/fulfillment` [chủ: CM-6]
- API `/me/orders`, `/return` được web học viên gọi, gồm 422 `VNPAY_SIGNATURE_INVALID` ở trang kết quả → E2E mua thành công, thất bại, không trả lời của FS-3; test trang kết quả có ca chữ ký sai [chủ: FS-3]
- Trang đơn và trạng thái thanh toán ở web admin đọc các bảng này qua API của CM-5 → hợp đồng kiểm ở CM-5, màn kiểm ở FA-3 [chủ: FA-3]
- IPN thật qua bản demo (khối `location` nginx ở G3) → VP-2 có `payment_event` với chữ ký đúng [chủ: VP-2]
- Điều kiện chặn `MOCK` chỉ có tác dụng khi môi trường thật bật profile `prod`; repo chưa có nơi nào đặt profile này (đã tìm `prod` trong `backend`, `infra`, `.github`) → README triển khai ghi bắt buộc bật `prod` [chủ: QA]
- Dải "Cần xử lý", API xử lý ca C và hoàn khoản thu thừa dùng các cột mới của `payment` → `LateSuccessReviewIT`, `OrderAttentionIT` của CM-5 [chủ: CM-5]

**Kế hoạch kiểm thử:**

- AC-1: `OrderSchemaMigrationIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderSchemaMigrationIT`
- AC-2: `OrderSchemaMigrationIT` (mới) ca dữ liệu có sẵn và ca dòng mồ côi, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderSchemaMigrationIT`
- AC-3: `OrderCreateIT` (mới) ca tạo đơn, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderCreateIT`
- AC-4: `OrderCreateIT` (mới) ca idempotency, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderCreateIT`
- AC-5: `OrderCreateIT` (mới) ca đổi tài khoản, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderCreateIT`
- AC-6: `OrderCreateIT` (mới) ca đổi giá, đổi phiên bản, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderCreateIT`
- AC-7: `OrderCreateIT` (mới) ca ngừng bán, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderCreateIT`
- AC-8: `OrderAccessIT` (mới) ca 401, 403, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderAccessIT`
- AC-9: `OrderReplaceIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderReplaceIT`
- AC-10: `OrderCreateIT` (mới) ca voucher, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderCreateIT`
- AC-11: `OrderAccessIT` (mới) ca tài khoản khác, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderAccessIT`
- AC-12: `OrderPayCancelIT` (mới) ca `/pay`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderPayCancelIT`
- AC-13: `OrderPayCancelIT` (mới) ca `/cancel`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderPayCancelIT`
- AC-14: `OrderAmountBoundaryIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderAmountBoundaryIT`
- AC-15: `FreeOrderIT` (mới) ca thành công, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=FreeOrderIT`
- AC-16: `FreeOrderIT` (mới) ca cấp quyền lỗi, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=FreeOrderIT`
- AC-17: `MockVnpayGatewayTest` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=MockVnpayGatewayTest`; `MockVnpayPageIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MockVnpayPageIT`
- AC-18: `VnpayModeStartupTest` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=VnpayModeStartupTest`; `MockVnpayPageIT` (mới) ca `SANDBOX` và ca OpenAPI, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MockVnpayPageIT`
- AC-19: `VnpayIpnIT` (mới) ca sai chữ ký, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayIpnIT`
- AC-20: `VnpayIpnIT` (mới) ca `01`, `04`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayIpnIT`
- AC-21: `VnpayIpnIT` (mới) ca thành công, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayIpnIT`
- AC-22: `VnpayIpnConcurrencyIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayIpnConcurrencyIT`
- AC-23: `VnpayIpnIT` (mới) ca lỗi nội bộ rồi gọi lại, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayIpnIT`
- AC-24: `VnpayIpnIT` (mới) bảng mã phản hồi, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayIpnIT`
- AC-25: `VnpayIpnIT` (mới) ca định dạng trả lời, miễn giới hạn, lọc IP, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayIpnIT`
- AC-26: `VnpayReturnIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayReturnIT`
- AC-27: `LatePaymentIT` (mới) ca `REPLACED`, `CANCELLED`, vượt `total_limit`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=LatePaymentIT`
- AC-28: `LatePaymentIT` (mới) ca thu trùng, `VnpayIpnConcurrencyIT` (mới) ca hai lần thanh toán, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='LatePaymentIT,VnpayIpnConcurrencyIT'`
- AC-29: `FulfillmentServiceIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=FulfillmentServiceIT`
- AC-30: `FulfillmentRetryWorkerIT` (mới) ca lỗi, thử lại, quá 30 phút, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=FulfillmentRetryWorkerIT`
- AC-31: `FulfillmentConcurrencyIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=FulfillmentConcurrencyIT`
- AC-32: `OrderMailIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderMailIT`
- AC-33: `OrderPortsIT` (mới) ca `exam-access`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderPortsIT`
- AC-34: `OrderPortsIT` (mới) ca `PurchaseHistory`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderPortsIT`
- AC-35: `OrderDeletionGuardIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderDeletionGuardIT`
- AC-36: kiểm tên biến, hợp đồng, `grep -cE '^THI360_VNPAY_(MODE|TMN_CODE|HASH_SECRET|PAY_URL|API_URL|IPN_ALLOWED_IPS)=' .env.example` ra `6` và `grep -cE '^THI360_VNPAY_(TMN_CODE|HASH_SECRET)=.+' .env.example` ra `0`
- AC-37: `FulfillmentRetryWorkerIT` (mới) ca tắt worker, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=FulfillmentRetryWorkerIT`
- AC-38: `OrderVoucherConcurrencyIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderVoucherConcurrencyIT`
- AC-39: `OrderReplaceIT` (mới) ca đơn chờ giữ lượt cuối, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderReplaceIT`
- AC-40: `FulfillmentServiceIT` (mới) ca mua thêm, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=FulfillmentServiceIT`
- AC-41: `PaymentCountingRulesIT#oneCountedPaymentPerOrder`, `#parallelSuccessCountsOnce` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaymentCountingRulesIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-42: `PaymentCountingRulesIT#failedAttemptAfterCountedIsExcess`, `#openAttemptAfterCountedIsExcess` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaymentCountingRulesIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-43: `PaymentCountingRulesIT#successAfterRefundNeverReopens` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaymentCountingRulesIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-44: `PaymentCountingRulesIT#failedAttemptWaitsForReconcile` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaymentCountingRulesIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-45: `PaymentCountingRulesIT#pendingReviewBecomesExcessWhenAnotherCounts` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaymentCountingRulesIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-46: `PaymentCountingRulesIT#repeatedNoticeAddsNothing` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaymentCountingRulesIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-47: `PaymentCountingRulesIT#failedThenRetriedThenLateSuccessScenario` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PaymentCountingRulesIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-48: `LatePaymentIT#flagWhenReplacementAlreadyPaid`, `#flagWhenReplacementPaysLater`, `#noFlagWithoutDoubleCharge` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=LatePaymentIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy:
  - xác thực, phiên: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='AuthFlowIT,SessionIT'`
  - giới hạn tần suất, IP tin cậy: `cd backend && ./mvnw -B -ntp verify -Dtest=ClientIpResolverTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='RateLimitFilterIT,TrustedProxyTomcatIT'`
  - khởi động, thông điệp, module: `cd backend && ./mvnw -B -ntp test -Dtest='Thi360ApplicationTests,MessageBundlesTest,ModularityTest'`
  - xóa tài khoản: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=UserAccountAdminIT`
  - các gói `commerce` đã có: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='vn/unicom/thi360/commerce/**/*IT'`
  - hợp đồng API: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiExportIT && cd .. && diff -u packages/api-client/openapi.json backend/target/openapi.json && pnpm --filter @thi360/api-client generate && git diff --exit-code packages/api-client && pnpm typecheck`
  - toàn bộ trước khi nộp: `cd backend && ./mvnw -B -ntp verify`
- Hồi quy (bổ sung ngày 2026-11-19): chạy `PaymentCountingRulesIT`, `LatePaymentIT`, `VnpayIpnIT`, `VnpayIpnConcurrencyIT` sau khi CM-3b, CM-5 merge.
- Kiểm thử rủi ro:
  - quyền: task không có endpoint admin, `ORDER.*` kiểm ở CM-5. Phía học viên: tài khoản `EXAM` làm được, `MANAGE` nhận 403, không có cookie nhận 401, tài khoản khác nhận 404 (AC-8, AC-11);
  - gửi lặp và đồng thời: IPN lặp 10 lần và 20 lần đồng thời (AC-22); hai lần thanh toán cùng đơn (AC-28); IPN cùng worker (AC-31); cùng khóa tạo đơn (AC-4); 10 yêu cầu khác khóa cùng sản phẩm (AC-9); 20 yêu cầu tranh lượt voucher cuối (AC-38);
  - migration trên DB có dữ liệu: dùng Flyway `target` dừng trước migration của task trên một database riêng, chèn `entitlement_grant` loại `COMPENSATION` và dòng `promotion_redemption`, chạy tiếp (AC-2);
  - biên: phải trả 5.000, 4.999, 1, mức tối thiểu 0 (AC-14); hạn `vnp_ExpireDate` sát `expires_at` (AC-12); quá 30 phút của worker (AC-30); vượt `total_limit` (AC-27);
  - đỏ trước: AC-2, AC-19, AC-20, AC-22, AC-23, AC-24, AC-26, AC-27, AC-28, AC-30, AC-31, AC-32, AC-38.
- Dữ liệu test: `CommerceTestFixtures` (mới, `backend/src/test/java/vn/unicom/thi360/commerce/support/`) tạo tài khoản, dòng bảng chiếu, voucher với mã ngẫu nhiên; chữ ký IPN dùng khóa thử `dev-only-…` trong khối `test`. Mỗi lane có container PostgreSQL, Redis riêng (Testcontainers); test không giả định bảng rỗng, không đếm toàn bảng.
- Không kiểm: cổng thật và mạng VNPAY (CM-3b, VP-2); giao diện mua (FS-3); hậu tố `txn_ref` quá 99 lần cho một đơn, vì đơn chờ chỉ sống 15 phút; `MOCK` trên bản demo qua nginx, vì khối `location` ở G3 chỉ đưa `/api/v1/public/payments/vnpay/` tới backend và VP-2 chạy `SANDBOX`.


## CM-3b · Cổng VNPAY thật, tra cứu, hết hạn

**Spec:** TM 8.4, 8.6 (đơn hết hạn), 14 (ca VNPAY), E26, Q12, Q14; plan B2, B5. **Phụ thuộc mềm:** VP-1 (thiếu G2 thì Advisor bỏ qua điều kiện này và làm theo AC-1).

**Sản phẩm:**

- Bản cài thật của `VnpayGateway` trong `commerce/payment/vnpay`:
  - ký `pay`; ký và gọi `querydr`, `refund` theo các chuỗi trường ở TM 8.4, mỗi loại một hàm ký riêng;
  - kiểm checksum của phản hồi; sai thì coi như chưa có kết quả;
  - `vnp_RequestId` là UUID bỏ gạch nối, mỗi lời gọi một mã; `vnp_TransactionDate` lấy từ `payment.vnp_create_date`; `vnp_IpAddr` của `querydr`, `refund` là IP máy chủ.
- Mọi điểm TM ghi "cần kiểm trên sandbox" đặt sau cấu hình hoặc một hàm riêng: quy tắc mã hóa URL của chuỗi ký `pay`, hàm băm của `querydr` và `refund`.
- Khi `THI360_VNPAY_MODE` là `SANDBOX` hoặc `PRODUCTION`: kiểm đủ biến lúc khởi động, thiếu thì dừng và nêu tên biến, không in giá trị.
- Worker `querydr` mỗi 5 phút (B5): lần thanh toán `CREATED`, `UNKNOWN` đã quá 15 phút, trong tối đa 24 giờ; sau 24 giờ giữ `UNKNOWN`, không gọi nữa.
- Bảng ánh xạ kết quả `querydr` theo TM 8.4; mã không có trong bảng thì `UNKNOWN`. `00/00` đi qua đúng dịch vụ ghi nhận thu tiền của IPN (CM-3a). Mỗi lần tra ghi `payment_event` nguồn `QUERYDR`.
- Cột `payment.next_query_at` (mới): worker nhận việc bằng `FOR UPDATE SKIP LOCKED`, đặt mốc tra kế tiếp rồi commit, gọi mạng ngoài transaction (B2). Mỗi lần thanh toán được tra tối đa một lần trong 5 phút, kể cả từ worker đơn hết hạn (VNPAY trả `94` khi trùng trong 5 phút).
- Worker đơn hết hạn mỗi phút (B5): tìm đơn chờ quá `expires_at`, gọi `queryDr` cho các lần thanh toán còn mở; chưa trả tiền thì `EXPIRED` và trả lượt voucher (`release` của CM-2).
- Tiền về sau `EXPIRED` (TM 8.6), qua IPN và qua `querydr`.
- Kết quả `querydr` báo thành công đi qua `PaymentOutcomeApplier` của CM-3a, cùng quy tắc với IPN (BS 14).
- Sau lời gọi `querydr`, khóa và đọc lại `payment`, đơn rồi mới áp kết quả: kết quả không thành công đến muộn chỉ ghi `payment_event`, không hạ `payment` đã `SUCCEEDED` (BS 14).

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/commerce/payment/vnpay/**` (mới)
- `backend/src/main/java/vn/unicom/thi360/commerce/order/expiry/**` (mới)
- `backend/src/main/resources/db/migration/commerce/` (migration mới của task: `payment.next_query_at`)
- `backend/src/test/java/vn/unicom/thi360/commerce/payment/vnpay/**`, `backend/src/test/java/vn/unicom/thi360/commerce/order/expiry/**` (mới)
- thêm vào: `backend/src/main/resources/application.yml` (cấu hình ký, thời gian chờ của lời gọi VNPAY, lịch hai worker)
- thêm vào: `.env.example` (ghi chú biến bắt buộc khi `SANDBOX`, `PRODUCTION`; chỉ tên)

**Ngoài phạm vi:** bảng đơn, IPN, cổng giả lập, cấp quyền (CM-3a); gọi `refund` từ luồng hoàn tiền và ánh xạ mã hoàn (CM-5); tạo vector từ sandbox (VP-1); chạy thật trên sandbox có IPN (VP-2); hiện danh sách "thanh toán chưa rõ quá 24 giờ" cho admin (CM-5, FA-3).

**Nghiệm thu:**

- AC-1: Chữ ký khớp vector cho `pay`, `querydr`, `refund`, cả yêu cầu và phản hồi: dùng `backend/src/test/resources/vnpay/vectors.json` của VP-1 nếu đã có; chưa có (thiếu G2) thì lấy ví dụ trong tài liệu và ghi rõ trong PR — bằng chứng: `VnpaySignatureVectorTest` (mới)
- AC-2: Phản hồi `querydr` và `refund` sai checksum không được dùng làm kết quả — bằng chứng: `VnpayHttpGatewayIT` (mới)
- AC-3: URL `pay` có đủ tham số TM 8.4, GMT+7, `vnp_Amount = số tiền × 100`: 5.000 đồng ra `500000`; số tiền quá 12 chữ số sau khi nhân bị từ chối trước khi gọi — bằng chứng: `VnpayPayUrlTest` (mới)
- AC-4: `querydr` gửi `POST` JSON tới `THI360_VNPAY_API_URL`; `vnp_RequestId` 32 ký tự, khác nhau giữa hai lời gọi; `vnp_TransactionDate` bằng `payment.vnp_create_date` — bằng chứng: `VnpayHttpGatewayIT` (mới)
- AC-5: `00/00`: `SUCCEEDED`, đơn `PAID` rồi `FULFILLED`, một quyền. `querydr` và IPN cùng về đồng thời: một lần ghi nhận, một quyền — bằng chứng: `QueryDrMappingIT` (mới)
- AC-6: `00/01`: giữ nguyên, lần tra sau vẫn được chọn — bằng chứng: `QueryDrMappingIT` (mới)
- AC-7: `00/02`: `FAILED` — bằng chứng: `QueryDrMappingIT` (mới)
- AC-8: `00/04` và `00/07`: `UNKNOWN`, đơn có `needs_review`, không cấp quyền — bằng chứng: `QueryDrMappingIT` (mới)
- AC-9: `00/05`, `00/06`, `00/09` trên lần thanh toán: không đổi trạng thái thanh toán — bằng chứng: `QueryDrMappingIT` (mới)
- AC-10: `91` trước `vnp_ExpireDate` + 15 phút: giữ nguyên; đúng mốc trở đi: `FAILED` — bằng chứng: `QueryDrMappingIT` (mới)
- AC-11: `94`, `99`, lỗi mạng, quá thời gian, checksum sai: giữ nguyên, tra lại sau — bằng chứng: `QueryDrMappingIT` (mới)
- AC-12: `02`, `03`, `97`: giữ nguyên, có log mức lỗi để báo vận hành — bằng chứng: `QueryDrMappingIT` (mới)
- AC-13: Mã chưa rõ (`vnp_ResponseCode` hoặc `vnp_TransactionStatus` không có trong bảng TM 8.4) thành `UNKNOWN`, không phải `FAILED` — bằng chứng: `QueryDrMappingIT` (mới)
- AC-14: Mỗi lần tra có một `payment_event` nguồn `QUERYDR`, `signature_valid` đúng với phản hồi — bằng chứng: `QueryDrMappingIT` (mới)
- AC-15: Worker chọn đúng lần thanh toán: tuổi 14 phút 59 giây không chọn, 15 phút có chọn; 24 giờ chưa quá có chọn, quá 24 giờ không chọn và trạng thái giữ `UNKNOWN`; lần đã `SUCCEEDED`, `FAILED` không chọn — bằng chứng: `QueryDrWorkerIT` (mới)
- AC-16: Hai worker `querydr` chạy cùng lúc, hoặc worker `querydr` và worker đơn hết hạn cùng chọn một lần thanh toán: một lời gọi trong 5 phút. Không có transaction đang mở trong lúc gọi mạng — bằng chứng: `QueryDrWorkerIT` (mới)
- AC-17: Đơn chờ quá `expires_at`, tra ra chưa trả tiền: `EXPIRED`, có `order_history`, lượt voucher thành `RELEASED`, `used_count` giảm 1. Đơn chưa quá hạn không bị đụng — bằng chứng: `OrderExpiryWorkerIT` (mới)
- AC-18: Đơn quá hạn còn lần thanh toán chưa rõ (`94`, lỗi mạng, `00/01`): đơn giữ `PENDING_PAYMENT`, voucher chưa trả — bằng chứng: `OrderExpiryWorkerIT` (mới)
- AC-19: Đơn quá hạn mà tra ra `00/00`: đơn `PAID` rồi `FULFILLED`, không `EXPIRED` — bằng chứng: `OrderExpiryWorkerIT` (mới)
- AC-20: Tiền về sau `EXPIRED` qua IPN và qua `querydr`: đơn `PAID`, cấp quyền theo giá và quyền lợi đã chụp kể cả khi sản phẩm đã ngừng bán, không đặt `needs_review` (BS 14), ghi lý do vào `order_history`; lượt voucher `RELEASED` thành `CONFIRMED`, vượt `total_limit` thì `over_limit_count` tăng — bằng chứng: `LatePaymentAfterExpiryIT` (mới)
- AC-21: `THI360_VNPAY_MODE=SANDBOX` khởi động được khi có đủ biến — bằng chứng: `VnpayRealModeStartupTest` (mới)
- AC-22: Thiếu từng biến `THI360_VNPAY_TMN_CODE`, `THI360_VNPAY_HASH_SECRET`, `THI360_VNPAY_PAY_URL`, `THI360_VNPAY_API_URL` ở `SANDBOX` và `PRODUCTION`: không khởi động, lỗi nêu tên biến, không in giá trị của biến khác. Giá trị `THI360_VNPAY_MODE` không hợp lệ cũng không khởi động — bằng chứng: `VnpayRealModeStartupTest` (mới)
- AC-23: Đổi cấu hình quy tắc mã hóa và hàm băm thì chuỗi ký đổi theo, không sửa mã — bằng chứng: `VnpaySigningConfigTest` (mới)
- AC-24: Khóa bí mật không xuất hiện trong log, `payment_event.raw`, thông báo lỗi — bằng chứng: `VnpayHttpGatewayIT` (mới)
- AC-25: `querydr` báo thành công trên lần `FAILED`: đơn đã có khoản được tính thì thành khoản thu thừa, chưa có thì chờ đối soát, không cấp quyền — bằng chứng: `QueryDrMappingIT#queryDrFollowsCountingRules` (mới)
- AC-26: `querydr` đã gọi, IPN thành công về trước, kết quả `FAILED`, `UNKNOWN`, hết hạn hoặc mã lạ về sau: khoản được tính, đơn và quyền không đổi, có `payment_event` — bằng chứng: `QueryDrMappingIT#lateNegativeQueryKeepsCountedPayment` (mới)

**Ảnh hưởng ngoài task:**

- Dịch vụ ghi nhận thu tiền dùng chung với IPN (`commerce/payment`, CM-3a) → chạy lại `VnpayIpnIT`, `VnpayIpnConcurrencyIT`, `LatePaymentIT` của CM-3a [chủ: task này]
- Bean `VnpayGateway` chọn theo chế độ, cổng giả lập vẫn dùng ở `MOCK` (`commerce/payment/mock`, CM-3a) → chạy lại `MockVnpayGatewayTest`, `MockVnpayPageIT`, `VnpayModeStartupTest` của CM-3a [chủ: task này]
- Cột mới trên bảng đã có dữ liệu (`commerce.payment`) → `QueryDrWorkerIT` (mới) ca migration: dòng `CREATED`, `UNKNOWN` có sẵn được worker chọn, dòng đã kết thúc không bị chọn [chủ: task này]
- Trả và lấy lại lượt voucher (`commerce/promotion`: `release`, `reclaim` của CM-2) → `OrderExpiryWorkerIT` (mới), `LatePaymentAfterExpiryIT` (mới); chạy toàn bộ test `commerce/promotion` [chủ: task này]
- Hai worker mới chạy nền trong mọi test tích hợp nếu không tắt (`AbstractIntegrationTest` bật `worker`) → dùng khóa `thi360.commerce.scheduling.enabled` của CM-3a; test gọi thẳng hàm của worker [chủ: task này]
- Cấu hình dùng chung (`backend/src/main/resources/application.yml`) → chạy `Thi360ApplicationTests` [chủ: task này]
- `ApplicationModules.verify()` (`backend/src/test/java/vn/unicom/thi360/ModularityTest.java`) → chạy `ModularityTest` [chủ: task này]
- OpenAPI không đổi vì task không thêm endpoint (`packages/api-client/openapi.json`) → chạy `OpenApiExportIT`, so `backend/target/openapi.json` với bản commit [chủ: task này]
- `VnpayGateway.refund` thật và hàm ký `refund` được luồng hoàn tiền gọi → CM-5 chạy `RefundApiIT` (mới của CM-5) trên HTTP giả [chủ: CM-5]
- Danh sách thanh toán `UNKNOWN` quá 24 giờ, `needs_review` từ `00/04`, `00/07` đi vào dải "Cần xử lý" → `OrderAttentionIT` (mới của CM-5) [chủ: CM-5]
- Vector chữ ký thật và kết luận về mã hóa URL (`backend/src/test/resources/vnpay/vectors.json`) → VP-1 nộp tệp; `VnpaySignatureVectorTest` (mới) đọc tệp này khi có [chủ: VP-1]
- Luồng thật qua sandbox (thanh toán, IPN, `querydr`, hoàn) → kịch bản của VP-2 ghi `payment_event` chữ ký đúng ở cả ba nguồn [chủ: VP-2]

**Kế hoạch kiểm thử:**

- AC-1: `VnpaySignatureVectorTest` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=VnpaySignatureVectorTest`
- AC-2: `VnpayHttpGatewayIT` (mới) ca checksum sai, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayHttpGatewayIT`
- AC-3: `VnpayPayUrlTest` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=VnpayPayUrlTest`
- AC-4: `VnpayHttpGatewayIT` (mới) ca thân yêu cầu, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayHttpGatewayIT`
- AC-5: `QueryDrMappingIT` (mới) ca `00/00` và ca đồng thời với IPN, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrMappingIT`
- AC-6: `QueryDrMappingIT` (mới) ca `00/01`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrMappingIT`
- AC-7: `QueryDrMappingIT` (mới) ca `00/02`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrMappingIT`
- AC-8: `QueryDrMappingIT` (mới) ca `00/04`, `00/07`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrMappingIT`
- AC-9: `QueryDrMappingIT` (mới) ca `00/05`, `00/06`, `00/09`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrMappingIT`
- AC-10: `QueryDrMappingIT` (mới) ca `91` trước mốc, đúng mốc, sau mốc bằng `MutableClock`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrMappingIT`
- AC-11: `QueryDrMappingIT` (mới) ca `94`, `99`, lỗi mạng, quá thời gian, checksum sai, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrMappingIT`
- AC-12: `QueryDrMappingIT` (mới) ca `02`, `03`, `97` có bắt log, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrMappingIT`
- AC-13: `QueryDrMappingIT` (mới) ca mã lạ, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrMappingIT`
- AC-14: `QueryDrMappingIT` (mới) ca `payment_event`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrMappingIT`
- AC-15: `QueryDrWorkerIT` (mới) ca biên 15 phút, 24 giờ, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrWorkerIT`
- AC-16: `QueryDrWorkerIT` (mới) ca hai worker và ca kiểm transaction trong HTTP giả, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=QueryDrWorkerIT`
- AC-17: `OrderExpiryWorkerIT` (mới) ca hết hạn, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderExpiryWorkerIT`
- AC-18: `OrderExpiryWorkerIT` (mới) ca chưa rõ, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderExpiryWorkerIT`
- AC-19: `OrderExpiryWorkerIT` (mới) ca đã trả tiền, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderExpiryWorkerIT`
- AC-20: `LatePaymentAfterExpiryIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=LatePaymentAfterExpiryIT`
- AC-21: `VnpayRealModeStartupTest` (mới) ca đủ biến, unit, `cd backend && ./mvnw -B -ntp test -Dtest=VnpayRealModeStartupTest`
- AC-22: `VnpayRealModeStartupTest` (mới) ca thiếu từng biến, unit, `cd backend && ./mvnw -B -ntp test -Dtest=VnpayRealModeStartupTest`
- AC-23: `VnpaySigningConfigTest` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=VnpaySigningConfigTest`
- AC-24: `VnpayHttpGatewayIT` (mới) ca quét log và `payment_event`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=VnpayHttpGatewayIT`
- AC-25: `QueryDrMappingIT#queryDrFollowsCountingRules` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=QueryDrMappingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-26: `QueryDrMappingIT#lateNegativeQueryKeepsCountedPayment` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=QueryDrMappingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy:
  - IPN, tiền về muộn, cổng giả lập: `cd backend && ./mvnw -B -ntp verify -Dtest=MockVnpayGatewayTest,VnpayModeStartupTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='VnpayIpnIT,VnpayIpnConcurrencyIT,LatePaymentIT,MockVnpayPageIT'`
  - voucher: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='vn/unicom/thi360/commerce/promotion/**/*IT'`
  - khởi động, module: `cd backend && ./mvnw -B -ntp test -Dtest='Thi360ApplicationTests,ModularityTest'`
  - hợp đồng API: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiExportIT && cd .. && diff -u packages/api-client/openapi.json backend/target/openapi.json`
  - toàn bộ trước khi nộp: `cd backend && ./mvnw -B -ntp verify`
- Kiểm thử rủi ro:
  - quyền: task không thêm endpoint, không có quyền mới để kiểm;
  - gửi lặp và đồng thời: `querydr` cùng IPN (AC-5); hai worker tra (AC-16); worker đơn hết hạn và worker `querydr` cùng chọn một lần thanh toán chỉ gọi một lần trong 5 phút (AC-16);
  - migration trên DB có dữ liệu: `payment.next_query_at` thêm trên bảng đã có dòng ở mọi trạng thái, dùng Flyway `target` trên một database riêng (`QueryDrWorkerIT`);
  - biên: 5.000 đồng và 12 chữ số (AC-3); `91` đúng mốc (AC-10); 15 phút, 24 giờ (AC-15); vượt `total_limit` (AC-20);
  - đỏ trước: AC-2, AC-5, AC-8, AC-10, AC-13, AC-16, AC-18, AC-19, AC-20, AC-22.
- Dữ liệu test: HTTP giả dựng bằng `com.sun.net.httpserver.HttpServer` của JDK (repo chưa có WireMock), khóa ký là khóa thử tự đặt, không dùng khóa sandbox; `CommerceTestFixtures` của CM-3a tạo đơn và thanh toán với mã ngẫu nhiên; thời gian dùng `MutableClock` của C-0b. Mỗi lane có container riêng (Testcontainers).
- Không kiểm: chữ ký đúng với máy chủ VNPAY thật và kết luận mã hóa URL (VP-1, VP-2); cách báo vận hành ngoài log mức lỗi, vì TM 8.4 không nêu kênh.

**Câu hỏi mở:** M.Trường, BA chốt: đơn có lần thanh toán giữ `00/01` hoặc `UNKNOWN` quá 24 giờ thì đơn ở trạng thái nào; TM 8.4, 8.6 không nêu, AC-18 tạm giữ `PENDING_PAYMENT` và đơn nằm trên dải "Cần xử lý".

## CM-5 · Quản lý đơn, hoàn tiền, tiếp tục cấp quyền, đối soát

**Spec:** BS 14; TM 5.3 (thư đơn hàng), 8.4 (đối soát cuối ngày), 8.5 bước 1, 8.6, 9, 14 (ca hoàn tiền, người mua, quyền), C14, C15, E23; plan B2, B4, B5.

**Sản phẩm:**

- Admin API `GET /api/v1/admin/orders`, `GET /api/v1/admin/orders/{code}`, `GET /api/v1/admin/exams/{id}/buyers` (`ORDER.VIEW`), đủ cột, tìm kiếm, bộ lọc, dải "Cần xử lý" sáu loại (TM 9.1–9.3, BS 14). Người mua đọc từ `purchase_order_item`, để CM-6 tính được đơn bộ.
- Bảng `refund` đủ cột TM 9.4.
- Hoàn qua API, `POST /api/v1/admin/orders/{code}/payments/{paymentId}/refunds` (`ORDER.REFUND`, bắt buộc `Idempotency-Key`):
  - transaction một khóa `purchase_order` rồi `payment`, kiểm điều kiện TM 9.4 bước 2, ghi dòng `PROCESSING`, commit;
  - gọi `VnpayGateway.refund` ngoài transaction (B2): `IdempotentCommand` chỉ bọc transaction một, vì `IdempotencyService.executeOnce` giữ khóa suốt hàm được bọc;
  - transaction hai ghi kết quả theo bảng mã TM 9.4. Mã `02` (Q14) đặt sau cấu hình; mã lạ thành `UNKNOWN`.
- `GET /api/v1/admin/refunds/{id}` (`ORDER.VIEW`), `POST /api/v1/admin/refunds/{id}/query` (`ORDER.REFUND`).
- `POST /api/v1/admin/refunds/{id}/mark-failed` (mới, `ORDER.REFUND`): đánh dấu `FAILED` kèm chứng từ (TM 9.4 bước 5 yêu cầu, danh sách API ở TM 9.4 chưa có đường dẫn).
- Ghi tay `kind = MANUAL` theo điều kiện TM 9.4 bước 5, cùng endpoint tạo lần hoàn.
- Điều kiện liên trường (`verified_system_error` phải đúng, `external_ref` khi ghi tay) kiểm ở service, trả 422, vì `AdminApiForbiddenMatrixIT` dựng thân mẫu tự động.
- Ảnh hưởng tới quyền dùng: `EntitlementGrantService.revokeUnused`, đơn `REFUNDED`, nhật ký `ENTITLEMENT_REVOKED`.
- Bản thật của `RefundProgress` (cổng tạm của CM-3a) cho TM 8.5 bước 1.
- Thư `REFUND_DONE` chỉ xếp hàng khi email của tài khoản đã xác minh (TM 5.3), đọc qua `UserDirectory.findMailContact` của CM-3a.
- Nhật ký `REFUND_REQUESTED`, `REFUND_SUCCEEDED`, `REFUND_FAILED`, `REFUND_RECORDED_MANUALLY`.
- `POST /api/v1/admin/orders/{code}/fulfill` (`ORDER.RECOVER`, bắt buộc `Idempotency-Key`, có lý do) theo TM 9.5, nhật ký `ORDER_FULFILLED_MANUALLY`.
- Báo cáo đối soát 02:00 giờ Việt Nam (B5): bảng `reconciliation_report` (mới), duy nhất theo ngày; `GET /api/v1/admin/reconciliation-reports/latest` (mới, `ORDER.VIEW`) để hiện ở đầu `/orders`.
- `POST /api/v1/admin/orders/{code}/payments/{paymentId}/late-success` (`ORDER.RECOVER`, bắt buộc `Idempotency-Key`), thân `{decision, reason, evidenceRef}` theo BS 14; nhật ký `PAYMENT_LATE_SUCCESS_ACCEPTED`, `PAYMENT_LATE_SUCCESS_REJECTED`. Báo cáo đối soát có khoản thu thừa chưa hoàn và khoản chờ đối soát.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/commerce/refund/**` (mới)
- `backend/src/main/java/vn/unicom/thi360/commerce/admin/**` (mới)
- `backend/src/main/java/vn/unicom/thi360/commerce/reconciliation/**` (mới)
- `backend/src/main/resources/db/migration/commerce/` (migration mới của task: `refund`, `reconciliation_report`)
- `backend/src/test/java/vn/unicom/thi360/commerce/refund/**`, `commerce/admin/**`, `commerce/reconciliation/**` (mới)
- sửa đúng chỗ bản tạm: `RefundProgress` trong `backend/src/main/java/vn/unicom/thi360/commerce/fulfillment/` và test ghi hành vi tạm của bản đó
- thêm vào: `backend/src/main/resources/application.yml` (lịch đối soát, cờ mã `02` của Q14)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi `REFUND_*`, `ORDER_*`)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** màn `/orders`, hộp thoại hoàn tiền (FA-3); đơn bộ trong API admin và hoàn đơn bộ (CM-6); bản cài thật của `VnpayGateway.refund` (CM-3b); hoàn qua sandbox thật (VP-2); cấp lại lượt sau khi lần hoàn bị từ chối (`09`), vì TM 9.4 để admin quyết định.

**Nghiệm thu:**

- AC-1: Migration tạo `refund` với `vnp_request_id`, `idempotency_key` duy nhất và `reconciliation_report` duy nhất theo ngày; chạy được trên DB đã có đơn và thanh toán, các dòng đó không đổi — bằng chứng: `RefundSchemaMigrationIT` (mới)
- AC-2: Danh sách đơn đủ cột TM 9.1, mới nhất trước; tìm theo mã đơn, tên đăng nhập, mã giao dịch VNPAY; lọc theo bài thi, khoảng thời gian, trạng thái thanh toán, trạng thái hoàn tiền, hình thức — bằng chứng: `AdminOrderListIT` (mới)
- AC-3: Trạng thái thanh toán tính theo thứ tự TM 8.1 (Thành công, Chưa có kết quả, Thất bại, Miễn phí); trạng thái hoàn tiền đủ Chưa hoàn, Đang hoàn (`PROCESSING`, `UNKNOWN`), Đã hoàn một phần, Đã hoàn — bằng chứng: `AdminOrderListIT` (mới)
- AC-4: Dải "Cần xử lý" đủ sáu loại: VNPAY báo thành công trên lần đã ghi thất bại, đã thu chưa cấp, thanh toán chưa rõ quá 24 giờ (đúng 24 giờ chưa tính, quá 24 giờ có tính), tiền trùng chưa hoàn, `needs_review`, lần hoàn `UNKNOWN`. Tiền trùng đã hoàn hết thì rời dải — bằng chứng: `OrderAttentionIT` (mới)
- AC-5: Chi tiết đơn đủ thông tin đã chốt, bảng giao dịch (đã thu, cờ trùng, đã hoàn, còn hoàn được), quyền liên quan, lịch sử xử lý. Mã đơn không có trả 404 — bằng chứng: `AdminOrderDetailIT` (mới)
- AC-6: Tổng người mua là số tài khoản khác nhau, không phải số đơn (TM 9.3): một tài khoản hai đơn và một tài khoản một đơn ra tổng 2; mỗi dòng có số lần mua, các đơn, ngày thanh toán gần nhất, lượt cấp, đã dùng, còn lại — bằng chứng: `BuyersIT` (mới)
- AC-7: Hoàn qua API thành công: gửi loại `02` khi hoàn toàn bộ, `03` khi hoàn một phần, `vnp_Amount` nhân 100, `vnp_CreateBy` là tên đăng nhập admin; lần hoàn `SUCCEEDED`, lưu `vnp_TransactionStatus`; có nhật ký `REFUND_REQUESTED`, `REFUND_SUCCEEDED` — bằng chứng: `RefundApiIT` (mới)
- AC-8: Hoàn đúng số còn hoàn được thì nhận; lớn hơn 1 đồng thì 409 `REFUND_AMOUNT_EXCEEDED` (mới); số còn hoàn đã trừ cả phần đã hoàn và đang hoàn; số tiền 0 hoặc âm thì 400 — bằng chứng: `RefundApiIT` (mới)
- AC-9: Bấm lặp cùng `Idempotency-Key`: một lần hoàn, một lần gọi cổng, cùng kết quả. Cùng khóa khác thân thì 409 `IDEMPOTENCY_KEY_REUSED`; thiếu khóa thì 400 `IDEMPOTENCY_KEY_REQUIRED` — bằng chứng: `RefundApiIT` (mới)
- AC-10: 10 yêu cầu đồng thời khác khóa trên cùng lần thu: đúng một lần hoàn được tạo, còn lại 409, tổng hoàn không vượt số đã thu — bằng chứng: `RefundConcurrencyIT` (mới)
- AC-11: Lần thu chưa `SUCCEEDED` thì 409; lần thu đang có lần hoàn `PROCESSING` hoặc `UNKNOWN` thì 409 — bằng chứng: `RefundApiIT` (mới)
- AC-12: Thiếu lý do thì 400; `verified_system_error` khác true thì 422 — bằng chứng: `RefundApiIT` (mới)
- AC-13: Kết quả theo bảng TM 9.4: `00` thành `SUCCEEDED`; `94` giữ `PROCESSING`; `91`, `95`, từ chối theo số tiền hoặc loại hoàn thành `FAILED`; `97`, `03`, mã merchant sai thành `FAILED` có log báo vận hành; `98`, `99`, quá thời gian, checksum sai, mã lạ thành `UNKNOWN`; mã `02` đi theo cấu hình Q14 — bằng chứng: `RefundCodeMappingTest` (mới)
- AC-14: Không có transaction đang mở trong lúc gọi cổng hoàn tiền; cổng quá thời gian thì lần hoàn `UNKNOWN`, không giữ khóa dòng — bằng chứng: `RefundConcurrencyIT` (mới)
- AC-15: Lần hoàn `UNKNOWN`: `/query` đưa về kết quả cuối; tra ra `09` thì `FAILED`, vào dải "Cần xử lý", không tự cấp lại lượt — bằng chứng: `RefundQueryIT` (mới)
- AC-16: `mark-failed` chỉ nhận lần hoàn `UNKNOWN`, bắt buộc chứng từ và lý do, ghi `REFUND_FAILED`; trạng thái khác thì 409 — bằng chứng: `RefundManualIT` (mới)
- AC-17: Ghi tay chỉ được khi lần hoàn qua API của giao dịch đã `FAILED`, bắt buộc `external_ref` và lý do, ghi `REFUND_RECORDED_MANUALLY`. Chưa có lần API `FAILED` thì 409 — bằng chứng: `RefundManualIT` (mới)
- AC-18: Lần hoàn `UNKNOWN` thì cấm ghi tay (409), kể cả khi giao dịch có lần `FAILED` trước đó — bằng chứng: `RefundManualIT` (mới)
- AC-19: Hoàn toàn bộ khi lần cấp có 1 lượt đã dùng, 1 đang giữ, 1 chưa dùng: thu hồi đúng 1 lượt, lượt đang làm làm xong được, có nhật ký `ENTITLEMENT_REVOKED` — bằng chứng: `RefundEntitlementIT` (mới)
- AC-20: Hoàn khoản trùng (`is_duplicate`): giữ nguyên quyền, kể cả khi hoàn hết khoản đó — bằng chứng: `RefundEntitlementIT` (mới)
- AC-21: Hoàn một phần: không thu hồi. Hai lần hoàn một phần cộng lại bằng số đã thu: thu hồi đúng một lần — bằng chứng: `RefundEntitlementIT` (mới)
- AC-22: Đơn `PAID` chưa cấp mà đã hoàn hết thì thành `REFUNDED`, `exam-access` không còn `CHECKING`, worker không cấp nữa — bằng chứng: `RefundFulfillmentGateIT` (mới)
- AC-23: Cấp quyền theo TM 8.5 bước 1 trên bản thật: có lần hoàn `PROCESSING` hoặc `UNKNOWN` thì dừng, lần thử sau xét lại; mới hoàn một phần và không còn lần hoàn đang chạy thì vẫn cấp — bằng chứng: `RefundFulfillmentGateIT` (mới)
- AC-24: Hoàn tiền không trả lượt voucher: `used_count` không đổi (E23) — bằng chứng: `RefundEntitlementIT` (mới)
- AC-25: `REFUND_DONE` xếp đúng một thư cho mỗi lần hoàn xong, cả `API` và `MANUAL`, kể cả khi `/query` mới ra `SUCCEEDED`; email chưa xác minh hoặc lần hoàn `FAILED` thì không có thư — bằng chứng: `RefundMailIT` (mới)
- AC-26: `fulfill` đúng điều kiện TM 9.5: đơn `PAID` có lần thu `SUCCEEDED` không trùng, không có lần hoàn đang chạy, chưa hoàn hết thì cấp và ghi `ORDER_FULFILLED_MANUALLY` kèm lý do; đơn `FREE` chỉ cần `PAID`; sai điều kiện thì 409, thiếu lý do thì 400; gửi lại cùng khóa không cấp thêm — bằng chứng: `OrderRecoverIT` (mới)
- AC-27: Đã có `entitlement_grant` mà đơn chưa `FULFILLED`: `fulfill` chỉ đồng bộ trạng thái đơn, số lượt đã dùng giữ nguyên — bằng chứng: `OrderRecoverIT` (mới)
- AC-28: Hai admin bấm cùng lúc, và worker cấp đúng lúc đó: một quyền, `attempts_total` chỉ cộng một lần — bằng chứng: `OrderRecoverIT` (mới)
- AC-29: Báo cáo đối soát có đơn `PAID` chưa `FULFILLED`, thanh toán `UNKNOWN`, tiền trùng chưa hoàn; `reconciliation-reports/latest` trả báo cáo mới nhất — bằng chứng: `ReconciliationWorkerIT` (mới)
- AC-30: Hai worker đối soát cùng lúc: chỉ một chạy (`pg_try_advisory_xact_lock`). Chạy lại trong cùng ngày không tạo báo cáo thứ hai — bằng chứng: `ReconciliationWorkerIT` (mới)
- AC-31: Mỗi endpoint admin: không có phiên nhận 401; chỉ có đúng quyền yêu cầu thì qua; thiếu quyền đó nhận 403 dù có hai quyền `ORDER.*` còn lại; không ghi dữ liệu khi bị từ chối — bằng chứng: `OrderPermissionMatrixIT` (mới)
- AC-32: `ACCEPT`: lần chờ đối soát thành khoản được tính, đơn `PAID` rồi `FULFILLED`, một quyền, ghi nhật ký; gửi lại cùng khóa không cấp thêm — bằng chứng: `LateSuccessReviewIT#acceptCountsPaymentAndFulfills`, `#acceptReplayGrantsOnce` (mới)
- AC-33: `REFUND`: lần chờ đối soát thành khoản thu thừa `REJECTED_AFTER_RECONCILE`, đơn không đổi, không cấp quyền, hoàn được bằng `ORDER.REFUND` — bằng chứng: `LateSuccessReviewIT#refundDecisionMakesExcess` (mới)
- AC-34: Khoản không còn ở `PENDING` nhận 409 `LATE_SUCCESS_ALREADY_RESOLVED`; thiếu `reason` hoặc `evidenceRef` nhận 400 — bằng chứng: `LateSuccessReviewIT#resolvedReviewIsRejected`, `#reasonAndEvidenceRequired` (mới)
- AC-35: API xử lý ca C: chỉ `ORDER.RECOVER` qua; thiếu quyền nhận 403, không có phiên nhận 401, không ghi dữ liệu khi bị từ chối — bằng chứng: `LateSuccessReviewIT#onlyOrderRecoverMayDecide` (mới)
- AC-36: Ca chuẩn của BS 14: admin hoàn khoản thu thừa của lần 1: chỉ khoản đó được hoàn, đơn giữ `FULFILLED`, không có `ENTITLEMENT_REVOKED`, học viên còn nguyên lượt; VNPAY gửi lại thông báo của lần 1 không tạo lần hoàn mới — bằng chứng: `RefundEntitlementIT#refundingExcessKeepsExamAndIgnoresRepeatNotice` (mới)
- AC-37: Hoàn hết khoản được tính của đơn mới thu hồi lượt chưa dùng; khoản thu thừa còn lại trên đơn `REFUNDED` vẫn hoàn được và không đổi trạng thái đơn — bằng chứng: `RefundEntitlementIT#revocationFollowsCountedPaymentOnly` (mới)
- AC-38: Báo cáo đối soát có đủ khoản thu thừa chưa hoàn và khoản chờ đối soát — bằng chứng: `ReconciliationWorkerIT#reportsExcessAndPendingReview` (mới)
- AC-39: Hai admin gửi `ACCEPT` và `REFUND` với khóa khác nhau trên cùng khoản chờ đối soát: đúng một quyết định thắng, người thua nhận 409 `LATE_SUCCESS_ALREADY_RESOLVED`, nhật ký ghi cùng transaction với quyết định thắng — bằng chứng: `LateSuccessConcurrencyIT#acceptRacingRefundHasOneWinner` (mới)
- AC-40: Admin `ACCEPT` khoản 1 cạnh tranh với IPN, rồi với `querydr`, báo thành công của khoản 2 cùng đơn, chạy cả hai thứ tự. `ACCEPT` trước: khoản 1 được tính, khoản 2 thành `SUCCEEDED`, `is_duplicate`, `AFTER_COUNTED`, IPN nhận `00`, gửi lặp nhận `02`. IPN trước: khoản 2 được tính, khoản 1 thành `AUTO_EXCESS`, chỉ yêu cầu `ACCEPT` nhận 409. Mọi thứ tự: tối đa một khoản được tính, một quyền, khoản thu thừa hoàn riêng không thu hồi bài, IPN không nhận lỗi do tranh khóa, `querydr` cho cùng kết quả qua `PaymentOutcomeApplier` — bằng chứng: `LateSuccessConcurrencyIT#acceptFirstThenOtherIpnIsExcess`, `#ipnFirstThenAcceptIs409`, `#queryPathMatchesIpnPath` (mới)

**Ảnh hưởng ngoài task:**

- Bản tạm `RefundProgress` và thủ tục cấp quyền dùng chung (`commerce/fulfillment`, CM-3a) → `RefundFulfillmentGateIT` (mới); chạy lại `FulfillmentServiceIT`, `FulfillmentRetryWorkerIT`, `FulfillmentConcurrencyIT` của CM-3a [chủ: task này]
- `exam-access` đọc đơn đã thu chưa cấp qua `PendingOrderLookup` (`commerce/entitlement/access`) → `RefundFulfillmentGateIT` (mới) ca `REFUNDED`; chạy `ExamAccessApiIT`, `OrderPortsIT` [chủ: task này]
- Thu hồi lượt (`commerce.entitlement.application.EntitlementGrantService.revokeUnused`, CM-4a) → `RefundEntitlementIT` (mới); chạy `EntitlementRevokeIT` [chủ: task này]
- `compensate` từ chối 409 `GRANT_REFUNDED` sau khi lần cấp bị hoàn hết (`EntitlementPort`, LT 12) → test cấp bù của AT-5 xanh trên lần cấp đã `revokeUnused` [chủ: AT-5]
- Cổng hoàn tiền (`VnpayGateway.refund`; bản giả lập của CM-3a, bản thật của CM-3b) → `RefundApiIT` (mới) chạy trên bản giả lập; `VnpayHttpGatewayIT` của CM-3b giữ chuỗi ký [chủ: task này]
- Lượt voucher không đổi khi hoàn (`commerce/promotion`, E23) → `RefundEntitlementIT` (mới) ca `used_count`; chạy `PromotionRedemptionIT` [chủ: task này]
- Hàng thư và mẫu `REFUND_DONE` (`MailQueue.enqueue`, `backend/src/main/resources/mail/templates/`, ML) → `RefundMailIT` (mới) đọc thư qua `MailOutbox` [chủ: task này]
- Quét endpoint admin tự động (`backend/src/test/java/vn/unicom/thi360/AdminApiForbiddenMatrixIT.java`, `backend/src/test/java/vn/unicom/thi360/EndpointPermissionCoverageTest.java`) → hai test này xanh [chủ: task này]
- Hằng số quyền `ORDER.*` do C-0 khai (`backend/src/main/java/vn/unicom/thi360/identity/spi/Permissions.java`) → chạy `PermissionCatalogSyncTest` [chủ: C-0]
- Worker đối soát chạy nền trong test tích hợp nếu không tắt (`AbstractIntegrationTest` bật `worker`) → dùng khóa `thi360.commerce.scheduling.enabled` của CM-3a; `ReconciliationWorkerIT` (mới) gọi thẳng hàm của worker [chủ: task này]
- Cấu hình dùng chung (`backend/src/main/resources/application.yml`) → chạy `Thi360ApplicationTests` [chủ: task này]
- Mã lỗi mới (`messages.properties`, `messages_vi.properties`, `messages_en.properties`) → chạy `MessageBundlesTest` [chủ: task này]
- `ApplicationModules.verify()` (`backend/src/test/java/vn/unicom/thi360/ModularityTest.java`) → chạy `ModularityTest` [chủ: task này]
- Hợp đồng API và kiểu sinh ra (`packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`) → chạy `OpenApiExportIT`, so `backend/target/openapi.json` với bản commit, sinh lại client, `pnpm typecheck` [chủ: task này]
- Màn `/orders`, hộp thoại hoàn tiền, `mark-failed` dùng các API này (`apps/web-admin/src/features/orders/**`) → `order-attention.test.tsx`, `refund-dialog.test.tsx` của FA-3 xanh trên client sinh lại [chủ: FA-3]
- Báo cáo đối soát phải hiện ở đầu `/orders` (TM 8.4) mà sản phẩm của FA-3 chưa ghi → FA-3 thêm khối báo cáo đọc `reconciliation-reports/latest`, có test giao diện [chủ: FA-3]
- Nhãn nhật ký mới rơi về mã thô ở tab lịch sử người dùng (`apps/web-admin/src/features/users/components/user-history-tab.tsx:22` `KNOWN_AUDIT_ACTIONS`, `apps/web-admin/src/messages/vi.json`, `en.json`) → thêm nhãn cho `REFUND_*`, `ENTITLEMENT_REVOKED`, `ORDER_FULFILLED_MANUALLY`; `apps/web-admin/src/messages/messages.test.ts` xanh [chủ: FA-3]
- Người mua và API đơn phải tính đơn bộ, hoàn đơn bộ thu hồi mọi bài → `BundleAdminOrdersIT`, `BundleRefundIT` (mới của CM-6) [chủ: CM-6]
- Hoàn tiền rồi thu hồi lượt trên toàn luồng → `e2e/tests/qa-refund-revoke.spec.ts` (mới của QA) [chủ: QA]
- Hoàn thật qua sandbox, Q14 → kịch bản hoàn toàn phần và một phần của VP-2 ghi `payment_event` nguồn `REFUND_API` chữ ký đúng [chủ: VP-2]

**Kế hoạch kiểm thử:**

- AC-1: `RefundSchemaMigrationIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundSchemaMigrationIT`
- AC-2: `AdminOrderListIT` (mới) ca cột, tìm kiếm, bộ lọc, hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AdminOrderListIT`
- AC-3: `AdminOrderListIT` (mới) ca trạng thái suy ra, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AdminOrderListIT`
- AC-4: `OrderAttentionIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderAttentionIT`
- AC-5: `AdminOrderDetailIT` (mới), hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AdminOrderDetailIT`
- AC-6: `BuyersIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BuyersIT`
- AC-7: `RefundApiIT` (mới) ca thành công, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundApiIT`
- AC-8: `RefundApiIT` (mới) ca biên số tiền, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundApiIT`
- AC-9: `RefundApiIT` (mới) ca idempotency, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundApiIT`
- AC-10: `RefundConcurrencyIT` (mới) ca 10 yêu cầu, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundConcurrencyIT`
- AC-11: `RefundApiIT` (mới) ca điều kiện giao dịch, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundApiIT`
- AC-12: `RefundApiIT` (mới) ca lý do và xác minh, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundApiIT`
- AC-13: `RefundCodeMappingTest` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=RefundCodeMappingTest`
- AC-14: `RefundConcurrencyIT` (mới) ca kiểm transaction trong cổng giả và ca quá thời gian, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundConcurrencyIT`
- AC-15: `RefundQueryIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundQueryIT`
- AC-16: `RefundManualIT` (mới) ca `mark-failed`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundManualIT`
- AC-17: `RefundManualIT` (mới) ca ghi tay, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundManualIT`
- AC-18: `RefundManualIT` (mới) ca cấm ghi tay khi `UNKNOWN`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundManualIT`
- AC-19: `RefundEntitlementIT` (mới) ca hoàn toàn bộ, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundEntitlementIT`
- AC-20: `RefundEntitlementIT` (mới) ca khoản trùng, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundEntitlementIT`
- AC-21: `RefundEntitlementIT` (mới) ca hoàn một phần, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundEntitlementIT`
- AC-22: `RefundFulfillmentGateIT` (mới) ca `REFUNDED`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundFulfillmentGateIT`
- AC-23: `RefundFulfillmentGateIT` (mới) ca đang hoàn, đã hoàn một phần, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundFulfillmentGateIT`
- AC-24: `RefundEntitlementIT` (mới) ca `used_count`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundEntitlementIT`
- AC-25: `RefundMailIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RefundMailIT`
- AC-26: `OrderRecoverIT` (mới) ca điều kiện, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderRecoverIT`
- AC-27: `OrderRecoverIT` (mới) ca đã có lần cấp, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderRecoverIT`
- AC-28: `OrderRecoverIT` (mới) ca đồng thời, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderRecoverIT`
- AC-29: `ReconciliationWorkerIT` (mới) ca nội dung, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ReconciliationWorkerIT`
- AC-30: `ReconciliationWorkerIT` (mới) ca hai worker, chạy lại, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ReconciliationWorkerIT`
- AC-31: `OrderPermissionMatrixIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderPermissionMatrixIT`
- AC-32: `LateSuccessReviewIT#acceptCountsPaymentAndFulfills`, `#acceptReplayGrantsOnce` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=LateSuccessReviewIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-33: `LateSuccessReviewIT#refundDecisionMakesExcess` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=LateSuccessReviewIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-34: `LateSuccessReviewIT#resolvedReviewIsRejected`, `#reasonAndEvidenceRequired` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=LateSuccessReviewIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-35: `LateSuccessReviewIT#onlyOrderRecoverMayDecide` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=LateSuccessReviewIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-36: `RefundEntitlementIT#refundingExcessKeepsExamAndIgnoresRepeatNotice` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=RefundEntitlementIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-37: `RefundEntitlementIT#revocationFollowsCountedPaymentOnly` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=RefundEntitlementIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-38: `ReconciliationWorkerIT#reportsExcessAndPendingReview` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ReconciliationWorkerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-39: `LateSuccessConcurrencyIT#acceptRacingRefundHasOneWinner` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=LateSuccessConcurrencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-40: `LateSuccessConcurrencyIT#acceptFirstThenOtherIpnIsExcess`, `#ipnFirstThenAcceptIs409`, `#queryPathMatchesIpnPath` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=LateSuccessConcurrencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy:
  - cấp quyền, cổng tạm: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='FulfillmentServiceIT,FulfillmentRetryWorkerIT,FulfillmentConcurrencyIT,OrderPortsIT'`
  - quyền dùng, voucher: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='EntitlementRevokeIT,ExamAccessApiIT,PromotionRedemptionIT'`
  - quyền admin: `cd backend && ./mvnw -B -ntp verify -Dtest='EndpointPermissionCoverageTest,PermissionCatalogSyncTest' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AdminApiForbiddenMatrixIT`
  - khởi động, thông điệp, module: `cd backend && ./mvnw -B -ntp test -Dtest='Thi360ApplicationTests,MessageBundlesTest,ModularityTest'`
  - hợp đồng API: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiExportIT && cd .. && diff -u packages/api-client/openapi.json backend/target/openapi.json && pnpm --filter @thi360/api-client generate && git diff --exit-code packages/api-client && pnpm typecheck`
  - toàn bộ trước khi nộp: `cd backend && ./mvnw -B -ntp verify`
- Kiểm thử rủi ro:
  - quyền: `ORDER.VIEW`, `ORDER.REFUND`, `ORDER.RECOVER`, mỗi quyền chạy riêng một mình và chạy với hai quyền còn lại khi thiếu nó, trên mọi endpoint của task; không phiên nhận 401. Tập quyền dựng thẳng trong test như `AdminApiForbiddenMatrixIT`, vì vai trò thật có `ORDER.REFUND` luôn được thêm `ORDER.VIEW` (`PermissionSetNormalizer`) (AC-31);
  - gửi lặp và đồng thời: bấm lặp cùng khóa (AC-9); 10 yêu cầu khác khóa (AC-10); hai admin và worker cùng cấp (AC-28); hai worker đối soát (AC-30); hai lần hoàn một phần tới đủ tiền (AC-21);
  - migration trên DB có dữ liệu: dừng Flyway `target` trước migration của task trên một database riêng, chèn đơn, thanh toán, rồi chạy tiếp (AC-1);
  - biên: số còn hoàn, số còn hoàn + 1, 0, âm (AC-8); đúng 24 giờ và quá 24 giờ (AC-4);
  - đỏ trước: AC-8, AC-9, AC-10, AC-14, AC-18, AC-19, AC-20, AC-21, AC-22, AC-23, AC-28, AC-30, AC-31.
- Dữ liệu test: `CommerceTestFixtures` của CM-3a dựng đơn và thanh toán với mã ngẫu nhiên; cổng hoàn dùng bản giả lập, lập trình được mã trả về; tài khoản admin dựng theo từng tổ hợp quyền trong test. Mỗi lane có container riêng (Testcontainers); báo cáo đối soát kiểm theo mã đơn của test, không đếm toàn bảng.
- Không kiểm: tiền thật về tài khoản khách và thời hạn được hoàn (Q14, VP-2); giờ chạy cron 02:00 trên máy thật, vì test gọi thẳng hàm của worker, biểu thức lịch kiểm bằng cấu hình; cách báo vận hành ngoài log mức lỗi, vì TM 9.4 không nêu kênh.

**Câu hỏi mở:** BA chốt đơn ở trạng thái nào được tính là "người mua" (TM 9.3 không nêu); AC-6 tạm tính `PAID`, `FULFILLED`. M.Trường chốt: lần hoàn giữ `PROCESSING` sau mã `94` không có worker tra lại, TM 9.4 chỉ ghi "tra cứu lại"; tạm để admin gọi `/query`.

## CM-6 · Bộ đề

**Spec:** BS 2; TM C17, 16, 10.1, 7.2 (phạm vi `BUNDLE`), 5.3 (thư đơn hàng), 8.5; 2B K3. **Phụ thuộc mềm:** CM-5 (hoàn tiền đơn bộ).

**Sản phẩm:**

- Bảng `bundle`, `bundle_item`, `bundle_version`.
- API admin theo TM 16.4: `GET/POST /api/v1/admin/bundles`, `GET/PUT /api/v1/admin/bundles/{id}` (có `expectedRevision`), `POST /bundles/{id}/publish`, `POST /bundles/{id}/discontinue`, `GET /bundles/candidates?examKind=`; quyền `EXAM.VIEW`, `EXAM.MODIFY`, `EXAM.PUBLISH`; nhật ký `BUNDLE_CREATED`, `BUNDLE_UPDATED`, `BUNDLE_PUBLISHED`, `BUNDLE_DISCONTINUED`.
  - Số bài 2–12 kiểm ở service, trả 422, vì `AdminApiForbiddenMatrixIT` dựng danh sách mẫu một phần tử.
  - Bài ôn đang bán cùng kỳ thi chọn được vào bộ; `candidates` và `items[]` của bộ trả `buildMode` để web gắn nhãn "Bài ôn" (BS 2).
- Công bố, ngừng bán bộ. Bộ thành `UNAVAILABLE` khi một bài trong bộ ngừng bán (nghe `ExamDiscontinued`).
- Bài trong bộ công bố lại (nghe `ExamPublished`): dựng lại `items[]` của dòng bảng chiếu theo phiên bản đang phục vụ; `bundle_version`, giá, số lượt không đổi (TM 16.2).
- Hai sự kiện trên mở rộng bộ nhận của CM-1, không gọi `OutboxRelay.register` lần hai cho cùng topic, vì `OutboxRelay` chỉ giữ một handler mỗi topic.
- Bảng chiếu: `product_type`, dòng `BUNDLE`, `ProductDetail.items[]`, bộ lọc "Bộ đề". `POST /api/v1/admin/storefront/rebuild` dựng lại cả dòng bộ.
- Đơn `product_type = BUNDLE`: một dòng `purchase_order_item` cho mỗi bài, chụp từ `bundle_version`; `option_codes` là mọi phương án đang phục vụ lúc tạo đơn. `purchase_order_item`, chỉ mục đơn chờ và ràng buộc `entitlement_grant (order_id, exam_id)` đã có từ CM-3a, CM-4a.
- Cấp quyền mọi bài của đơn bộ trong một transaction. Khóa ngoại `bundle_id` cho `purchase_order`, `promotion_scope_item`.
- Hoàn toàn bộ đơn bộ thu hồi lượt chưa dùng ở mọi bài (sau khi CM-5 merge).
- API đơn admin và người mua theo bài tính cả đơn bộ, trả các bài của đơn (TM 16.4).
- Thư `ORDER_FULFILLED` liệt kê các bài được cấp (TM 16.4): model truyền đủ các bài của đơn, mẫu của ML đã hiện được nhiều bài; vẫn chỉ xếp hàng khi email đã xác minh.
- Phạm vi khuyến mãi `BUNDLE`.
- `exam-access/query` với mã bộ; `GET /me/exams` có `bundles[]` của thẻ.
- Bộ SP2E 3 môn ở profile `dev`, `e2e`, dùng 3 bài `SP2E_MATH_2026`, `SP2E_VAN_OLD`, `SP2E_ANH_2026` DS đã công bố. Mã nằm ở `commerce/bundle/devdata`, chạy nối tiếp bộ dựng của DS bằng `@Order`, theo cùng cờ `thi360.devdata.published-exams.enabled`: đặt trong `exam/devdata` sẽ làm `exam` phụ thuộc ngược `commerce`.
- Bộ nhận sự kiện của bộ đề ghi trong transaction riêng (`REQUIRES_NEW`, B2), để lỗi của một sự kiện không chặn hàng outbox.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/commerce/bundle/**` (mới)
- `backend/src/main/resources/db/migration/commerce/` (migration mới của task)
- `backend/src/test/java/vn/unicom/thi360/commerce/bundle/**` (mới)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/commerce/order/**` (đơn bộ)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/commerce/fulfillment/**` (cấp quyền nhiều dòng, thư liệt kê bài)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/commerce/storefront/**` (dòng bộ, `items[]`, bộ lọc, `rebuild`, bộ nhận sự kiện)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/commerce/promotion/**`, `backend/src/main/java/vn/unicom/thi360/commerce/pricing/**` (phạm vi `BUNDLE`)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/commerce/entitlement/access/**` (`exam-access` với mã bộ, `bundles[]`)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/commerce/refund/**`, `backend/src/main/java/vn/unicom/thi360/commerce/admin/**` (hoàn đơn bộ, API đơn, người mua)
- thêm vào: `backend/src/main/resources/devdata/published-exam-set.json`, `e2e/tests/helpers/published-exam-set.ts` (mã bộ SP2E 3 môn, tệp hằng số của DS)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi `BUNDLE_*`, `PRODUCT_UNAVAILABLE`)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** màn bộ đề trên web admin và web học viên, các bài của đơn bộ trên `/orders` (FB); ảnh bìa riêng của bộ (TM 16.1 không có); tự tính giảm so với tổng giá lẻ (TM 16.2 không làm); `build_mode` trong `PublishedExamPort` (EX-1).

**Nghiệm thu:**

- AC-1: Migration tạo ba bảng; thêm khóa ngoại `bundle_id` trên `purchase_order`, `promotion_scope_item` chạy được trên DB đã có dòng: dòng `bundle_id` trống còn nguyên; dòng `bundle_id` không khớp bộ nào thì migration dừng báo lỗi, không xóa dữ liệu — bằng chứng: `BundleSchemaMigrationIT` (mới)
- AC-2: Tạo, xem, sửa, liệt kê bộ; sửa với `expectedRevision` cũ thì 412; có nhật ký `BUNDLE_CREATED`, `BUNDLE_UPDATED` — bằng chứng: `BundleAdminApiIT` (mới)
- AC-3: Thành phần: 1 bài và 13 bài bị từ chối, 2 và 12 bài được nhận; bài lặp, khác kỳ thi, không `ON_SALE`, phạm vi đề đầy đủ đều bị từ chối 422 — bằng chứng: `BundleCompositionIT` (mới)
- AC-4: Bài ôn đang bán có trong `candidates` kèm `buildMode`, chọn vào bộ và công bố được; `items[]` của trang chi tiết bộ trả `buildMode` từng bài — bằng chứng: `BundleCompositionIT` (mới)
- AC-5: `code` duy nhất trên toàn bảng chiếu, không phân biệt hoa thường: trùng mã bài hoặc mã bộ khác thì 409 `BUNDLE_CODE_TAKEN` (mới); `attempts_per_exam` 0 và 101 bị từ chối, 1 và 100 được nhận — bằng chứng: `BundleCompositionIT` (mới)
- AC-6: `candidates` chỉ trả bài đang bán của kỳ thi, kèm số câu, thời lượng, điểm tối đa — bằng chứng: `BundleAdminApiIT` (mới)
- AC-7: Công bố kiểm giá theo `LISTING_PRICE_*`, E26: giá bằng mức tối thiểu được nhận, thấp hơn 1 đồng hoặc thiếu giá bị từ chối; thiếu mô tả, số lượt hoặc có bài không còn bán bị từ chối, không tạo `bundle_version` — bằng chứng: `BundlePublishIT` (mới)
- AC-8: Công bố thành công tạo `bundle_version` có `snapshot` đủ trường TM 16.1, ghi dòng bảng chiếu `product_type = BUNDLE`, nhật ký `BUNDLE_PUBLISHED` — bằng chứng: `BundlePublishIT` (mới)
- AC-9: `GET /storefront/products` trả cả bộ với `productType`, bộ lọc "Bộ đề" lọc đúng; `ProductDetail` của bộ có `items[]` (tên bài, môn, số câu, thời lượng, điểm tối đa) và quyền lợi theo từng bài — bằng chứng: `BundleStorefrontIT` (mới)
- AC-10: `ForbiddenKeysAssert` trên `ProductDetail` của bộ — bằng chứng: `BundleStorefrontIT` (mới)
- AC-11: `rebuild` dựng lại dòng bộ đã công bố, không tạo dòng cho bộ nháp, chạy lại không đổi kết quả — bằng chứng: `BundleStorefrontIT` (mới)
- AC-12: Một bài trong bộ ngừng bán: bộ `UNAVAILABLE`, tạo đơn nhận 409 `PRODUCT_UNAVAILABLE` (mới); đơn cũ không đổi; dòng bảng chiếu của chính bài đó vẫn cập nhật — bằng chứng: `BundleEventIT` (mới)
- AC-13: Bài trong bộ công bố lại: `items[]` đổi theo phiên bản mới; `bundle_version`, giá, số lượt không đổi; xử lý lại sự kiện hoặc sự kiện phiên bản cũ không ghi lùi; dòng bảng chiếu của chính bài đó vẫn cập nhật — bằng chứng: `BundleEventIT` (mới)
- AC-14: Ngừng bán bộ: `DISCONTINUED`, tạo đơn nhận 410 `PRODUCT_DISCONTINUED`, quyền đã cấp không đổi, có nhật ký `BUNDLE_DISCONTINUED` — bằng chứng: `BundlePublishIT` (mới)
- AC-15: Sửa thành phần rồi công bố lại: đơn cũ vẫn theo `bundle_version` cũ, đơn mới theo bản mới; sửa chưa công bố thì đơn mới vẫn theo bản đang bán — bằng chứng: `BundleOrderIT` (mới)
- AC-16: Tạo đơn bộ: `product_type = BUNDLE`, `bundle_id`, `bundle_version_no` có giá trị, `exam_id` trên đơn trống, mỗi bài một dòng `purchase_order_item` với `option_codes` là mọi phương án đang phục vụ; E13 áp theo `product_code` của bộ — bằng chứng: `BundleOrderIT` (mới)
- AC-17: Mua bộ 3 bài SP2E bằng cổng giả lập: 3 lần cấp, mỗi bài đúng số lượt; IPN gửi lại không cấp trùng — bằng chứng: `BundleFulfillmentIT` (mới)
- AC-18: Lỗi ở một dòng thì cả đơn chưa cấp, đơn giữ `PAID`; worker cấp lại đủ 3 bài — bằng chứng: `BundleFulfillmentIT` (mới)
- AC-19: Mua bộ khi đã có lượt lẻ của một bài: lượt cộng vào quyền hiện có, quyền lợi theo từng lần cấp (E21) — bằng chứng: `BundleFulfillmentIT` (mới)
- AC-20: Hoàn toàn bộ đơn bộ: thu hồi lượt chưa dùng ở cả 3 bài, lượt đang làm vẫn làm xong; hoàn một phần không thu hồi — bằng chứng: `BundleRefundIT` (mới)
- AC-21: Voucher phạm vi `BUNDLE` áp cho bộ đã chọn, không áp cho bài lẻ; `ALL`, `EXAM_KIND` áp cho bộ cùng kỳ thi; `EXAM` không áp cho bộ; `price-quote` nhận mã bộ như mã bài — bằng chứng: `BundlePromotionIT` (mới)
- AC-22: `exam-access/query` với mã bộ trả `state = NONE` kèm `items[]` theo trạng thái từng bài — bằng chứng: `BundleAccessIT` (mới)
- AC-23: `GET /me/exams` trả `bundles[]` trên thẻ của bài thuộc bộ đã mua — bằng chứng: `BundleAccessIT` (mới)
- AC-24: Người mua theo bài tính cả đơn bộ có bài đó; một tài khoản vừa mua lẻ vừa mua bộ chỉ tính một lần — bằng chứng: `BundleAdminOrdersIT` (mới)
- AC-25: API danh sách và chi tiết đơn admin trả sản phẩm bộ và các bài trong đơn; lọc theo bài thi tìm được đơn bộ có bài đó — bằng chứng: `BundleAdminOrdersIT` (mới)
- AC-26: Thư `ORDER_FULFILLED` của đơn bộ liệt kê đủ các bài được cấp; email chưa xác minh thì không có thư — bằng chứng: `BundleMailIT` (mới)
- AC-27: Đơn bài lẻ vẫn cấp quyền, hoàn tiền như trước: test của CM-3a, CM-5 xanh — bằng chứng: lệnh hồi quy ở Kế hoạch kiểm thử
- AC-28: Profile `dev`, `e2e` có một bộ SP2E 3 môn đã công bố; chạy lại bộ dựng không tạo trùng — bằng chứng: `BundleSeedIT` (mới)
- AC-29: Mỗi endpoint bộ: không có phiên nhận 401; có đúng quyền yêu cầu thì qua; thiếu quyền đó nhận 403 dù có hai quyền `EXAM.*` còn lại — bằng chứng: `BundlePermissionMatrixIT` (mới)
- AC-30: Bộ nhận bộ đề lỗi ở một sự kiện thì `attempts` của sự kiện đó tăng và sự kiện sau vẫn được xử lý — bằng chứng: `BundleEventIT`, ca `handlerFailureDoesNotBlockOutbox`

**Ảnh hưởng ngoài task:**

- `OutboxRelay` chỉ giữ một handler mỗi topic, đăng ký lần hai thay handler cũ (`backend/src/main/java/vn/unicom/thi360/shared/outbox/OutboxRelay.java`, `register`) → mở rộng bộ nhận của CM-1; `BundleEventIT` (mới) kiểm một sự kiện cập nhật cả dòng bài và dòng bộ; chạy `StorefrontProjectionIT`, `OutboxRelayTests` [chủ: task này]
- `rebuild` và API công khai của CM-1 (`commerce/storefront`) → chạy `StorefrontRebuildIT`, `StorefrontPublicApiIT`, `StorefrontDataSetIT` [chủ: task này]
- Tính giá và API khuyến mãi của CM-2 đang từ chối `scope = BUNDLE` (`commerce/promotion`, `commerce/pricing`) → sửa ca `autoPromotionRejectsVoucherFields` trong `PromotionAdminApiIT`; chạy `PriceCalculatorIT`, `PromotionAdminApiIT`, `StorefrontPromotionIT` [chủ: task này]
- Đơn hàng và cấp quyền bài lẻ dùng chung mã (`commerce/order`, `commerce/fulfillment`, CM-3a) → chạy toàn bộ test `commerce/order`, `commerce/payment`, `commerce/fulfillment` [chủ: task này]
- Hoàn tiền và API đơn bài lẻ (`commerce/refund`, `commerce/admin`, CM-5) → chạy toàn bộ test `commerce/refund`, `commerce/admin` [chủ: task này]
- Cấp nhiều bài trong một transaction (`commerce.entitlement.application.EntitlementGrantService.grantForOrder`, CM-4a) → `BundleFulfillmentIT` (mới); chạy `EntitlementGrantServiceIT`, `EntitlementGrantConcurrencyIT` [chủ: task này]
- `exam-access`, `/me/exams` của CM-4b (`commerce/entitlement/access`) → chạy `ExamAccessApiIT`, `MyExamsApiIT` [chủ: task này]
- Mẫu thư `ORDER_FULFILLED` của ML, CM-3a đang dùng (`backend/src/main/resources/mail/templates/`, `MailQueue.enqueue`) → `BundleMailIT` (mới) đọc thư qua `MailOutbox`; chạy `OrderMailIT` của CM-3a để thư bài lẻ không đổi [chủ: task này]
- Bộ dựng của DS được test tích hợp và E2E dùng chung (`backend/src/main/java/vn/unicom/thi360/exam/devdata/**`, `backend/src/test/java/vn/unicom/thi360/support/PublishedExamSetSupport.java`, `e2e/tests/published-exam-set.spec.ts`) → `BundleSeedIT` (mới) chạy sau bộ dựng; chạy toàn bộ test `exam/devdata`, vẫn dưới 60 giây; `pnpm --filter @thi360/e2e test -- tests/published-exam-set.spec.ts` xanh [chủ: task này]
- Quét endpoint admin tự động, danh sách mẫu chỉ có một phần tử (`backend/src/test/java/vn/unicom/thi360/AdminApiForbiddenMatrixIT.java`, `backend/src/test/java/vn/unicom/thi360/EndpointPermissionCoverageTest.java`) → hai test này xanh [chủ: task này]
- Mã lỗi mới (`messages.properties`, `messages_vi.properties`, `messages_en.properties`) → chạy `MessageBundlesTest` [chủ: task này]
- `ApplicationModules.verify()` (`backend/src/test/java/vn/unicom/thi360/ModularityTest.java`) → chạy `ModularityTest` [chủ: task này]
- Hợp đồng API và kiểu sinh ra (`packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`) → chạy `OpenApiExportIT`, so `backend/target/openapi.json` với bản commit, sinh lại client, `pnpm typecheck` [chủ: task này]
- `PublishedExamPort` phải trả `build_mode` và đủ trường cho `snapshot`, `candidates` (`exam/spi`, plan EX-1) → EX-1 khai trường; `BundleCompositionIT` (mới) ca bài ôn xanh [chủ: EX-1]
- `examId` trên đơn bộ để trống (`/me/orders`, `packages/api-client/src/schema.d.ts`) → test `/checkout/result`, `/account/orders` của FS-3 xanh với đơn bộ [chủ: FS-3]
- Màn bộ đề, trang chi tiết, `/home` nhóm theo bộ, các bài của đơn bộ trên `/orders` → E2E của FB: tạo và công bố bộ SP2E 3 môn, mua bằng cổng giả lập, `/home` có nhóm bộ với 3 thẻ [chủ: FB]
- Nhãn nhật ký `BUNDLE_*` rơi về mã thô ở tab lịch sử người dùng (`apps/web-admin/src/features/users/components/user-history-tab.tsx:22` `KNOWN_AUDIT_ACTIONS`) → thêm nhãn; `apps/web-admin/src/messages/messages.test.ts` xanh [chủ: FB]
- Luồng mua bộ thật trên sandbox → kịch bản bộ đề của VP-2 nếu VP-2 chọn sản phẩm bộ; không chọn thì đã phủ bằng cổng giả lập ở AC-17 [chủ: VP-2]

**Kế hoạch kiểm thử:**

- AC-1: `BundleSchemaMigrationIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleSchemaMigrationIT`
- AC-2: `BundleAdminApiIT` (mới) ca CRUD, `expectedRevision`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleAdminApiIT`
- AC-3: `BundleCompositionIT` (mới) ca biên số bài và bài không hợp lệ, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleCompositionIT`
- AC-4: `BundleCompositionIT` (mới) ca bài ôn, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleCompositionIT`
- AC-5: `BundleCompositionIT` (mới) ca mã trùng, biên số lượt, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleCompositionIT`
- AC-6: `BundleAdminApiIT` (mới) ca `candidates`, hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleAdminApiIT`
- AC-7: `BundlePublishIT` (mới) ca từ chối và biên giá, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundlePublishIT`
- AC-8: `BundlePublishIT` (mới) ca thành công, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundlePublishIT`
- AC-9: `BundleStorefrontIT` (mới) ca thẻ, bộ lọc, chi tiết, hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleStorefrontIT`
- AC-10: `BundleStorefrontIT` (mới) ca khóa cấm, hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleStorefrontIT`
- AC-11: `BundleStorefrontIT` (mới) ca `rebuild`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleStorefrontIT`
- AC-12: `BundleEventIT` (mới) ca `ExamDiscontinued`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleEventIT`
- AC-13: `BundleEventIT` (mới) ca `ExamPublished`, xử lý lại, phiên bản cũ, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleEventIT`
- AC-14: `BundlePublishIT` (mới) ca ngừng bán bộ, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundlePublishIT`
- AC-15: `BundleOrderIT` (mới) ca công bố lại, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleOrderIT`
- AC-16: `BundleOrderIT` (mới) ca tạo đơn, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleOrderIT`
- AC-17: `BundleFulfillmentIT` (mới) ca mua 3 bài, IPN gửi lại, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleFulfillmentIT`
- AC-18: `BundleFulfillmentIT` (mới) ca lỗi một dòng, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleFulfillmentIT`
- AC-19: `BundleFulfillmentIT` (mới) ca đã có lượt lẻ, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleFulfillmentIT`
- AC-20: `BundleRefundIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleRefundIT`
- AC-21: `BundlePromotionIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundlePromotionIT`
- AC-22: `BundleAccessIT` (mới) ca `exam-access`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleAccessIT`
- AC-23: `BundleAccessIT` (mới) ca `/me/exams`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleAccessIT`
- AC-24: `BundleAdminOrdersIT` (mới) ca người mua, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleAdminOrdersIT`
- AC-25: `BundleAdminOrdersIT` (mới) ca danh sách, chi tiết, bộ lọc, hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleAdminOrdersIT`
- AC-26: `BundleMailIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleMailIT`
- AC-27: test bài lẻ của CM-3a, CM-5, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='vn/unicom/thi360/commerce/order/**/*IT,vn/unicom/thi360/commerce/payment/**/*IT,vn/unicom/thi360/commerce/fulfillment/**/*IT,vn/unicom/thi360/commerce/refund/**/*IT,vn/unicom/thi360/commerce/admin/**/*IT'`
- AC-28: `BundleSeedIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundleSeedIT`
- AC-29: `BundlePermissionMatrixIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=BundlePermissionMatrixIT`
- AC-30: `BundleEventIT#handlerFailureDoesNotBlockOutbox`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=BundleEventIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy:
  - bảng chiếu, sự kiện: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='StorefrontProjectionIT,StorefrontRebuildIT,StorefrontPublicApiIT,StorefrontDataSetIT,OutboxRelayTests'`
  - khuyến mãi, tính giá: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='PriceCalculatorIT,PromotionAdminApiIT,StorefrontPromotionIT'`
  - quyền dùng: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='EntitlementGrantServiceIT,EntitlementGrantConcurrencyIT,ExamAccessApiIT,MyExamsApiIT'`
  - thư bài lẻ: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderMailIT`
  - bộ dựng của DS: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='vn/unicom/thi360/exam/devdata/**/*IT' && cd .. && pnpm --filter @thi360/e2e test -- tests/published-exam-set.spec.ts`
  - quyền admin: `cd backend && ./mvnw -B -ntp verify -Dtest=EndpointPermissionCoverageTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=AdminApiForbiddenMatrixIT`
  - thông điệp, module: `cd backend && ./mvnw -B -ntp test -Dtest='MessageBundlesTest,ModularityTest'`
  - hợp đồng API: `cd backend && ./mvnw -B -ntp verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiExportIT && cd .. && diff -u packages/api-client/openapi.json backend/target/openapi.json && pnpm --filter @thi360/api-client generate && git diff --exit-code packages/api-client && pnpm typecheck`
  - toàn bộ trước khi nộp: `cd backend && ./mvnw -B -ntp verify`
- Kiểm thử rủi ro:
  - quyền: `EXAM.VIEW`, `EXAM.MODIFY`, `EXAM.PUBLISH`, mỗi quyền chạy riêng một mình và chạy với hai quyền còn lại khi thiếu nó, trên mọi endpoint của bộ; không phiên nhận 401. Tập quyền dựng thẳng trong test như `AdminApiForbiddenMatrixIT`, vì vai trò thật luôn được thêm quyền `VIEW` (`PermissionSetNormalizer`) (AC-29);
  - gửi lặp và đồng thời: IPN gửi lại trên đơn bộ (AC-17); xử lý lại sự kiện (AC-13); chạy lại bộ dựng (AC-28);
  - migration trên DB có dữ liệu: dừng Flyway `target` trước migration của task trên một database riêng, chèn `purchase_order`, `promotion_scope_item` có `bundle_id` trống và một dòng mồ côi, rồi chạy tiếp (AC-1);
  - biên: 1, 2, 12, 13 bài (AC-3); `attempts_per_exam` 0, 1, 100, 101 (AC-5); giá bằng và thấp hơn mức tối thiểu E26 (AC-7);
  - đỏ trước: AC-1, AC-4, AC-12, AC-13, AC-15, AC-17, AC-18, AC-20, AC-24, AC-26, AC-29.
- Dữ liệu test: 3 bài SP2E theo môn lấy từ bộ dựng của DS qua `PublishedExamSetSupport` cho `BundleFulfillmentIT`, `BundleRefundIT`, `BundleSeedIT`; các test còn lại dùng bản giả của `PublishedExamPort` và `CommerceTestFixtures` của CM-3a với mã ngẫu nhiên. Mỗi lane có container riêng (Testcontainers); test không đếm toàn bảng.
- Không kiểm: giao diện bộ đề (FB); thanh toán bộ trên sandbox thật (VP-2); ảnh bìa riêng của bộ, vì TM 16.1 dùng hình mặc định.


## VP-2 · Luồng VNPAY sandbox thật có IPN

**Spec:** TM 8.4, 9.4; plan A3, B6. **Cổng:** G2 (G3 đã có).

**Sản phẩm:**

- Không cần giữ dữ liệu cũ của demo (G3): được xóa DB demo và dựng lại bằng dữ liệu DS. Sau VP-2 demo giữ `integration/dot-3`.
- Đưa `integration/dot-3` lên bản demo bằng `demo-setup.sh` có sẵn trên máy demo (`~/deploy/thi360-demo/`, ngoài repo), với `THI360_VNPAY_MODE=SANDBOX`, khóa và `THI360_TOKEN_PEPPER` riêng trong `.env` của demo; thêm khối `location` nginx của G3, đưa `/api/v1/public/payments/vnpay/` thẳng tới backend. Luôn chạy `nginx -t` trước khi nạp lại (nginx dùng chung).
- Chạy một lần mua thật trên `https://thi360.zone17.click` bằng thẻ thử: IPN tới, đơn `FULFILLED`, thẻ hiện ở Bài thi của tôi. Một lần hủy. Một lần hoàn toàn phần từ web admin.
- Bằng chứng (đã che) trong `dot-3/vnpay/`: nhật ký `payment_event`, ảnh chụp các màn.
- Sửa TM, `VnpayGateway` nếu khác kết quả của VP-1.

**Đường dẫn sở hữu:**

- Ngoài repo: `D:/.Vietants_Project/.thi360-dev/dot-3/vnpay/**` (bằng chứng)
- Ngoài repo, trên máy demo: `~/deploy/thi360-demo/.env`, `~/global-infra/nginx/conf.d/thi360.conf` (chỉ thêm khối `location` của G3)
- Chỉ khi kết quả khác VP-1: `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md` (mục 8.4, 9.4)
- Chỉ khi kết quả khác VP-1: `backend/src/main/java/vn/unicom/thi360/commerce/payment/vnpay/**`, `backend/src/test/resources/vnpay/vectors.json`, test của cổng trong `backend/src/test/java/vn/unicom/thi360/commerce/payment/vnpay/**`

**Ngoài phạm vi:** VNPAY môi trường thật (làm sau, khi M.Trường báo); đo mức tối thiểu và vector chữ ký (VP-1); logic IPN, trang quay lại, `/mock-vnpay` (CM-3a); cổng thật (CM-3b); API hoàn tiền (CM-5); màn mua (FS-1, FS-3), màn hoàn tiền (FA-3); dữ liệu bài đang bán (DS); tài liệu triển khai (QA).

**Nghiệm thu:**

- AC-1: VP2-S1 mua thành công: IPN trả `RspCode = 00`, đơn `FULFILLED`, thẻ hiện ở Bài thi của tôi — bằng chứng: kịch bản VP2-S1, ảnh chụp các màn
- AC-2: IPN của VP2-S1 có trong `payment_event` với `source = IPN`, `signature_valid = true` — bằng chứng: truy vấn VP2-Q1, lưu `payment-event.json` đã che
- AC-3: VP2-S2 hủy trên trang VNPAY: thanh toán có mã `24`, không có quyền dùng mới — bằng chứng: kịch bản VP2-S2, truy vấn VP2-Q2
- AC-4: VP2-S3 hoàn toàn phần từ web admin: đơn `REFUNDED`, lượt chưa dùng bị thu hồi, `querydr` của VNPAY khớp — bằng chứng: kịch bản VP2-S3, ảnh chụp màn đơn, truy vấn VP2-Q3
- AC-5: VP2-S4 gửi lại đúng IPN đã nhận: trả `RspCode = 02`, vẫn chỉ một quyền dùng — bằng chứng: kịch bản VP2-S4, truy vấn VP2-Q4
- AC-6: VP2-S5 IPN sai chữ ký: trả `RspCode = 97`, ghi `payment_event` với `signature_valid = false`, trạng thái đơn không đổi — bằng chứng: kịch bản VP2-S5, truy vấn VP2-Q1
- AC-7: VP2-S6 mở trang quay lại với tham số giả mạo: đơn không đổi trạng thái, không có quyền dùng — bằng chứng: kịch bản VP2-S6, truy vấn VP2-Q4
- AC-8: VP2-S7 khi `THI360_VNPAY_MODE=SANDBOX`, `/mock-vnpay` trên demo không dùng được để tự ký IPN — bằng chứng: kịch bản VP2-S7
- AC-9: VP2-S8 vai trò tự tạo chỉ có `ORDER.VIEW` gọi API hoàn tiền nhận 403 — bằng chứng: kịch bản VP2-S8
- AC-10: VP2-S9 bấm hoàn hai lần liên tiếp chỉ tạo một lệnh hoàn — bằng chứng: kịch bản VP2-S9, truy vấn VP2-Q3
- AC-11: VP2-S10 `nginx -t` đạt trước khi nạp lại; sau khi nạp, các vhost khác trả cùng mã trạng thái như trước — bằng chứng: kịch bản VP2-S10, lưu kết quả lệnh
- AC-12: VP2-S11 `GET /api/v1/public/payments/vnpay/ipn` trên tên miền demo trả JSON có `RspCode` từ backend, không phải trang của web — bằng chứng: kịch bản VP2-S11
- AC-13: Không có khóa trong bằng chứng, diff và nhật ký demo — bằng chứng: lệnh quét VP2-SCAN-1, VP2-SCAN-2, VP2-SCAN-3
- AC-14: Nếu phải sửa `VnpayGateway`: có test vector chữ ký tái hiện sai khác, đỏ trên code cũ, xanh sau khi sửa; không sửa thì ghi "không sửa" trong báo cáo lane — bằng chứng: test vector của cổng trong `backend/src/test/java/vn/unicom/thi360/commerce/payment/vnpay/**`
- AC-15: TM ghi lại mọi điểm khác kết quả VP-1, kèm ngày và nguồn; không có điểm khác thì báo cáo lane ghi rõ — bằng chứng: kịch bản VP2-S12 đối chiếu TM 8.4, 9.4 với bằng chứng

**Ảnh hưởng ngoài task:**

- Nginx dùng chung với dự án khác (`~/global-infra/nginx/conf.d/thi360.conf`, container `global_nginx` trên máy demo, ngoài repo) → VP2-S10: `nginx -t` đạt, các vhost khác trả cùng mã trạng thái trước và sau khi nạp lại [chủ: task này]
- Bản demo đang chạy `develop` với dữ liệu cũ (`~/deploy/thi360-demo/compose.yaml`, ngoài repo) → sau khi dựng lại, `demo-smoke.sh` đạt và trang chủ có bài của DS [chủ: task này]
- Bản demo chạy profile `dev` nên trang `/mock-vnpay` có thể còn bật theo TM 8.4 (`/mock-vnpay`, CM-3a) → VP2-S7 không tự ký được IPN khi ở `SANDBOX`; không đạt thì CM-3a sửa [chủ: CM-3a]
- Cổng thật ký và kiểm chữ ký (`VnpayGateway` bản thật của CM-3b) → VP2-S1, VP2-S5 đạt; khác VP-1 thì AC-14 [chủ: CM-3b]
- API hoàn tiền và quyền `ORDER.REFUND` → VP2-S3, VP2-S8, VP2-S9 đạt [chủ: CM-5]
- Màn hoàn tiền trên web admin → VP2-S3 làm được trên giao diện [chủ: FA-3]
- Màn xác nhận mua, kết quả thanh toán, Bài thi của tôi → VP2-S1 làm được trên giao diện [chủ: FS-3]
- Dữ liệu bài đang bán trên demo → VP2-S1 có sản phẩm mua được [chủ: DS]
- Thư trên demo không được gửi tới địa chỉ thật (A3, `~/deploy/thi360-demo/.env`) → VP2-S1 dùng tài khoản `example.test`, SMTP của demo trỏ vào hộp thư thử [chủ: task này]
- Yêu cầu IPN đi thẳng tới backend ghi vào tài liệu triển khai (B6) → tài liệu của QA có khối `location` đã dùng ở VP-2 [chủ: QA]
- Mã nguồn trong repo: không có thay đổi khi kết quả khớp VP-1, đã đối chiếu phần "chỉ khi kết quả khác VP-1" của Đường dẫn sở hữu → có thay đổi thì chạy `cd backend && ./mvnw -B -ntp verify` [chủ: task này]

**Kế hoạch kiểm thử:**

- AC-1: VP2-S1, E2E, trên `https://thi360.zone17.click` đăng nhập học viên thử, mua bài của DS, trả bằng thẻ thử: màn kết quả thành công, thẻ có ở Bài thi của tôi
- AC-2: VP2-Q1, tích hợp, trên máy demo `docker compose --env-file .env exec -T postgres psql -U "$THI360_DB_USER" -d "$THI360_DB_NAME" -c "select source, signature_valid, outcome, received_at from commerce.payment_event order by received_at"`: có dòng `IPN`, `signature_valid = t`
- AC-3: VP2-S2, E2E, bấm hủy trên trang VNPAY; VP2-Q2 `select state, response_code from commerce.payment order by created_at desc limit 1` có `response_code = 24`, và đếm `commerce.entitlement` của tài khoản không tăng
- AC-4: VP2-S3, E2E, hoàn toàn phần từ web admin; VP2-Q3 `select state, count(*) from commerce.refund group by state` có đúng một lệnh `SUCCEEDED`, đơn `REFUNDED`
- AC-5: VP2-S4, E2E, gửi lại nguyên URL IPN đã ghi: phản hồi `{"RspCode":"02"...}`; VP2-Q4 đếm quyền dùng của đơn bằng 1
- AC-6: VP2-S5, E2E, đổi một ký tự của `vnp_SecureHash` rồi gọi IPN: phản hồi `{"RspCode":"97"...}`; VP2-Q1 có dòng `signature_valid = f`
- AC-7: VP2-S6, E2E, mở `/api/v1/public/payments/vnpay/return` với `vnp_ResponseCode=00` và chữ ký giả trên đơn chờ: VP2-Q4 đếm quyền dùng bằng 0, đơn vẫn chờ
- AC-8: VP2-S7, E2E, `curl -s -o /dev/null -w "%{http_code}" https://thi360.zone17.click/mock-vnpay` và thử bấm "Thành công" trên đơn chờ: không tạo được IPN hợp lệ, đơn không đổi
- AC-9: VP2-S8, E2E, đăng nhập vai trò tự tạo chỉ có `ORDER.VIEW`, gọi API hoàn tiền của đơn: 403, không có lệnh hoàn mới
- AC-10: VP2-S9, E2E, hai yêu cầu hoàn cùng khóa idempotency gửi liên tiếp trên một đơn khác: VP2-Q3 có đúng một lệnh
- AC-11: VP2-S10, E2E, `docker exec global_nginx nginx -t && docker exec global_nginx nginx -s reload`; so `curl -s -o /dev/null -w "%{http_code}"` của từng vhost trước và sau
- AC-12: VP2-S11, E2E, `curl -s "https://thi360.zone17.click/api/v1/public/payments/vnpay/ipn"`: thân JSON có `RspCode`
- AC-13: VP2-SCAN-1 thư mục bằng chứng `test -n "$THI360_VNPAY_HASH_SECRET" && test -n "$THI360_VNPAY_TMN_CODE" && grep -rlF -e "$THI360_VNPAY_HASH_SECRET" -e "$THI360_VNPAY_TMN_CODE" D:/.Vietants_Project/.thi360-dev/dot-3/vnpay --exclude=vp1_sandbox.py | wc -l` ra `0`; VP2-SCAN-2 diff `git diff origin/integration/dot-3...HEAD | grep -cF -e "$THI360_VNPAY_HASH_SECRET" -e "$THI360_VNPAY_TMN_CODE"` ra `0` cùng hai lệnh ở A7 mục 10; VP2-SCAN-3 nhật ký demo `docker compose --env-file .env logs api | grep -cF "$THI360_VNPAY_HASH_SECRET"` ra `0`, mức hợp đồng
- AC-14: test vector chữ ký của cổng, unit, `cd backend && ./mvnw -B -ntp test -Dtest='Vnpay*Tests'`
- AC-15: VP2-S12, hợp đồng, người duyệt PR đối chiếu TM 8.4, 9.4 với `payment-event.json` và ảnh chụp: không có điểm khác chưa được ghi
- Hồi quy:
  - Nginx và các vhost khác: VP2-S10 (AC-11).
  - Demo sau khi dựng lại: `~/deploy/thi360-demo/demo-smoke.sh` đạt.
  - Có sửa code: `cd backend && ./mvnw -B -ntp verify`.
  - Dòng của CM-3a, CM-3b, CM-5, FA-3, FS-3, DS, QA: task chủ chạy phép kiểm đã ghi ở dòng đó; VP-2 chạy lại đầu cuối qua VP2-S1 đến VP2-S9.
- Kiểm thử rủi ro:
  - Từng quyền riêng lẻ và khi thiếu: AC-9 (chỉ `ORDER.VIEW` nhận 403); vai trò có `ORDER.REFUND` làm VP2-S3.
  - Gửi lại, đồng thời: AC-5 (IPN lặp), AC-10 (hoàn hai lần).
  - Giả mạo: AC-6 (sai chữ ký), AC-7 (trang quay lại giả), AC-8 (`/mock-vnpay`).
  - Migration trên DB có dữ liệu: không áp dụng, G3 cho phép xóa DB demo và dựng lại.
  - Giá trị biên: số tiền của đơn lấy theo mức tối thiểu VP-1 đã đo.
  - đỏ trước: AC-14 (chỉ khi phải sửa `VnpayGateway`). Các AC còn lại là kịch bản trên sandbox thật, không có trạng thái "trước thay đổi" để chạy đỏ.
- Dữ liệu test: dữ liệu DS trên DB demo dựng lại; thẻ thử công khai của sandbox; tài khoản học viên và vai trò tự tạo sinh cho đợt thử, email miền `example.test`; khóa sandbox chỉ nằm trong `.env` của demo và biến môi trường của máy chạy VP; một lane duy nhất chạy VP-2 vì mỗi Terminal Code chỉ có một URL IPN.
- Không kiểm: VNPAY môi trường thật (A3 cấm); lọc IP của IPN (Q9 chưa có danh sách, sandbox không lọc); hoàn một phần trên web admin (nghiệm thu VP-2 chỉ đòi hoàn toàn phần, CM-5 tự kiểm phần còn lại); tải nhiều đơn cùng lúc trên demo.

**Câu hỏi mở:** Q9 (M.Trường hỏi VNPAY): merchant sandbox chưa bật API hoàn tiền thì AC-4 không đạt qua API, lane dừng và báo, không tự chuyển sang ghi nhận tay. M.Trường chốt SMTP của bản demo trỏ vào đâu để không gửi tới địa chỉ thật (A3).

## AT-1 · Lượt thi: bắt đầu, sinh đề chạy nền, đảo, chuẩn bị, trạng thái

**Spec:** LT 3, 4, 5, 6.2, 7.3, 7.4, L7, L8, F2–F6, F13, F22; TM E25; 2B 11; SUP mục 5; phần bổ sung 2B mục 5.3, 7, H34; BS 4, 5.

**Sản phẩm:**

- Bảng `attempt` (gồm `abort_reason`), `attempt_part`, `attempt_item`, `attempt_event` (LT 4). `essay_grade` để AT-5 làm.
- `GET /me/entitlements/{id}/prepare` dùng `EntitlementPort.describe`; đủ nội dung LT 5.1, số câu và điểm tối đa đọc từ `parts[].paths[]`.
- `POST /me/attempts` theo LT 5.2:
  - transaction 1: kiểm, `reserve`, tạo `STARTING`, trả 202;
  - `GenerationExecutor` (B2): sinh đề ở nền; transaction 2 lưu đề, đặt `IN_PROGRESS`, mở phần đầu. Chỉ lưu khi lượt còn `STARTING` và `generation_token` còn khớp; việc chờ trong hàng quá 60 giây thì bỏ;
  - lỗi, quá hạn, hàng đầy: `ABORTED` đúng `abort_reason`, `release`.
- Chọn phiên bản theo F2. Chọn phương án trong phần giao giữa phạm vi đã mua và phương án còn phục vụ.
- Thứ tự câu và đảo phương án (gồm cột phải của Ghép đôi) theo F22 bằng `exam/spi/PaperOrdering` của đợt 2B (2B K12), với hạt giống của từng phần; lưu `optionOrder` trả về vào `attempt_item.option_order`. Câu có `shuffleOptions = false` giữ thứ tự soạn (SUP mục 5, `PaperOrdering` đã làm; task này không sửa hàm đó). Ghi sổ đã dùng qua `QuestionSourcePort.recordUsage`.
- `display_no` chạy liền từ 1 qua mọi phần (BS 5): dòng bộ câu hỏi không chiếm số, câu con chiếm số, Điền khuyết chiếm `unit_count` số; `unit_count`; `component_code` chụp theo môn của `ExamSection` (F15).
- Cột `attempt.exam_kind_code` và `attempt.navigation_mode` chụp lúc bắt đầu; task này luôn đặt `SEQUENTIAL`, AT-8 đặt `FREE` cho THPTQG (BS 4).
- `preChoice` nhận định hướng Tin học (LT 6.2).
- `GET /me/attempts/{id}` (`AttemptState`, gồm dạng rút gọn cho `STARTING`, `ABORTED`). Nội dung câu bỏ khóa cấm; không trả câu của phần chưa mở. Đường ảnh trong nội dung câu viết lại từ `/api/v1/admin/question-assets/{id}` sang `/api/v1/me/attempts/{attemptId}/assets/{assetId}` (AT-3 làm endpoint), vì tài khoản học viên không gọi được `/api/v1/admin/**`.
- `AttemptGuard`: kiểm sở hữu, phần đang mở, hạn. AT-2a, AT-2b, AT-3 dùng chung. Hai cổng tạm trong module (B1), bản tạm không làm gì: `OverdueAttemptCloser` (AT-2a cài) và `AttemptSessionChecker` (AT-3 cài).
- Cài `AttemptStatusProvider`, `ActiveAttemptChecker`, phần `attempts` của `StudentLearningProvider`.
- Worker đưa lượt `STARTING` quá 2 phút về `ABORTED` (`STALE_STARTING`), theo cờ `thi360.attempt.scheduling.enabled` (tắt ở profile `test`).
- Cột `attempt_item.points_max`, `attempt_item.earned_points` kiểu `numeric(10,4)`, để giữ đủ 0,125 của H-SCA (BS 17.5).

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/attempt/api/**`, `attempt/application/**`, `attempt/domain/**`, `attempt/infrastructure/**` (AT-1 mở các package; các task AT sau thêm lớp của mình theo B9)
- `backend/src/main/resources/db/migration/attempt/**`
- `backend/src/test/java/vn/unicom/thi360/attempt/**`
- thêm vào: `backend/src/main/resources/application.yml` (`thi360.attempt.generation.*`, `thi360.attempt.scheduling.enabled`; giá trị `false` trong khối `on-profile: test`)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi của task)
- thêm vào: `backend/src/test/java/vn/unicom/thi360/support/ForbiddenKeysAssert.java` (khóa cấm còn thiếu, B6)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** `essay_grade`, chấm tay (AT-5). Chuyển phần, `finish`, `submit`, đóng lượt, worker hạn giờ (AT-2a). Chọn phần 3, `retry` (AT-2b). `PUT answers`, phiên sửa, endpoint tài nguyên (AT-3). Chấm và kết quả (AT-4). Màn chuẩn bị và màn chờ (FS-5). Sửa `PaperOrdering` (không task nào của đợt 3 làm, B9).

**Nghiệm thu:** dùng dữ liệu DS.

- AC-1: hai yêu cầu bắt đầu đồng thời, khác khóa idempotency, của cùng tài khoản trên cùng bài tạo đúng một lượt và `reserve` đúng một lần; một yêu cầu nhận 202, yêu cầu kia nhận 200 `resumed = true` — bằng chứng: `AttemptStartIT#concurrentStartsCreateOneAttempt` (mới)
- AC-2: đã có lượt mở (`STARTING` hoặc `IN_PROGRESS`) thì trả 200 kèm lượt đó, `resumed = true`, không `reserve` thêm — bằng chứng: `AttemptStartIT#openAttemptIsResumed` (mới)
- AC-3: chỉ mục duy nhất từng phần `(user_id, exam_id) WHERE state IN ('STARTING','IN_PROGRESS')` chặn dòng lượt mở thứ hai ở tầng DB; lượt `ABORTED` cũ không chặn lượt mới — bằng chứng: `AttemptSchemaIT#partialUniqueIndexAllowsOneOpenAttempt` (mới)
- AC-4: trả 202 `{attemptId, state: STARTING}` rồi thành `IN_PROGRESS`; `started_at` và các hạn tính từ lúc lưu đề xong, không phải lúc bấm — bằng chứng: `AttemptStartIT#startedAtIsWhenPaperSaved` (mới)
- AC-5: hết lượt thì 409 `NO_ATTEMPTS_LEFT`, không tạo dòng `attempt` — bằng chứng: `AttemptStartIT#noAttemptsLeftIsRejected` (mới)
- AC-6: sinh lỗi thì `ABORTED` với `GENERATION_FAILED`, `release` đúng một lần, không có `attempt_item`, nhật ký vận hành có mã đề, phiên bản, đường và lý do — bằng chứng: `AttemptGenerationFailureIT#solverErrorAbortsAndReleases` (mới)
- AC-7: sinh quá hạn thì `ABORTED` với `GENERATION_TIMEOUT` và lượt được trả — bằng chứng: `AttemptGenerationFailureIT#solverTimeoutAbortsAndReleases` (mới)
- AC-8: hàng đầy (cấu hình 1 luồng, hàng 0) thì `ABORTED` với `GENERATION_BUSY`; việc chờ trong hàng quá 60 giây bị bỏ như hàng đầy — bằng chứng: `AttemptGenerationFailureIT#fullQueueAbortsWithBusy`, `#queuedJobOlderThan60sIsDropped` (mới)
- AC-9: worker đã đưa lượt về `ABORTED` rồi việc sinh mới xong: kết quả bị bỏ, không có `attempt_item`, lượt chỉ được trả một lần — bằng chứng: `AttemptGenerationFailureIT#lateResultIsDiscarded` (mới)
- AC-10: lượt `STARTING` quá 2 phút về `ABORTED` với `STALE_STARTING`; đúng 2 phút chưa bị đóng; hai worker chạy cùng lúc chỉ trả lượt một lần — bằng chứng: `AttemptStaleWorkerIT#abortsAfterTwoMinutes`, `#twoWorkersReleaseOnce` (mới)
- AC-11: `prepare` khi lượt đang `STARTING` trả lượt đó; `AttemptStatusProvider` trả cả lượt `STARTING` kèm trạng thái — bằng chứng: `AttemptPrepareIT#prepareReturnsStartingAttempt`, `AttemptSpiIT#statusProviderIncludesStarting` (mới)
- AC-12: `prepare` trả đủ mục LT 5.1; số câu và điểm tối đa của phần đọc từ `parts[].paths[]`, đã trừ nhóm trong `omittedGroups`; quyền của tài khoản khác nhận 404 — bằng chứng: `AttemptPrepareIT#prepareMatchesPublishedPaths`, `#otherUsersEntitlementIs404` (mới)
- AC-13: bài ngừng bán còn lượt vẫn bắt đầu được, dùng phiên bản phục vụ cuối cùng (F3) — bằng chứng: `AttemptStartIT#discontinuedExamCanStart` (mới)
- AC-14: bài chọn ở bước chuẩn bị lấy phiên bản đang phục vụ của đường đã chọn; bài một đường lấy phiên bản của đường đó (F2) — bằng chứng: `AttemptStartIT#versionFollowsChosenPath` (mới)
- AC-15: công bố phiên bản mới, sửa câu nguồn hoặc đổi mapping khi lượt đang mở không đổi `exam_version_no` và bộ câu đã giao (phần bổ sung 2B mục 7) — bằng chứng: `AttemptStartIT#runningAttemptKeepsIssuedPaper` (mới)
- AC-16: cùng hạt giống thì cùng thứ tự câu và phương án; chùm liền khối, câu con giữ thứ tự; nhóm giữ thứ tự theo mẫu; nhóm `shuffleUnits = false` giữ thứ tự câu — bằng chứng: `AttemptPaperAssemblerTest#sameSeedSameOrder`, `#setsStayContiguous`, `#unshuffledGroupKeepsOrder` (mới)
- AC-17: câu Một đáp án, Nhiều đáp án và câu con của chùm có `shuffleOptions = false` giữ thứ tự soạn; `option_order` vẫn được lưu (bằng thứ tự soạn); thứ tự của các câu khác với cùng hạt giống không đổi khi bật hay tắt cờ — bằng chứng: `AttemptPaperAssemblerTest#keepOptionOrderFlagIsHonoured`, `AttemptStartIT#optionOrderStoredWhenKept` (mới)
- AC-18: cột phải của Ghép đôi luôn được đảo và `option_order` lưu thứ tự đã đảo; mệnh đề Đúng/Sai và cột trái Ghép đôi giữ thứ tự soạn — bằng chứng: `AttemptPaperAssemblerTest#matchingRightColumnAlwaysShuffled` (mới)
- AC-19: Tin học SPT V1 và THPTQG chọn định hướng ở `preChoice`; đề chỉ có câu của nhánh đã chọn; mẫu số 22 và 28 — bằng chứng: `AttemptScopeMatrixIT#informaticsBranchDenominators` (mới)
- AC-20: bài theo môn của QDA và bài ôn vào thẳng, không cần `preChoice` — bằng chứng: `AttemptScopeMatrixIT#subjectAndReviewExamsNeedNoChoice` (mới)
- AC-21: `preChoice` ngoài phần giao thì 422 `CHOICE_NOT_ALLOWED`; phần giao rỗng thì 409 `NO_SERVING_OPTION` — bằng chứng: `AttemptStartIT#preChoiceOutsideScopeIsRejected`, `#emptyServingIntersectionIsRejected` (mới)
- AC-22: V-ACT theo phần, V-ACT theo môn, THPTQG theo phần và bài ôn: mẫu số và điểm tối đa lấy từ `parts[].paths[]` của đường thực làm (`VACT_PART_LANGUAGE` 60 câu, 600 điểm; `VACT_SUBJECT_TOAN` 30 câu, 300 điểm) — bằng chứng: `AttemptScopeMatrixIT#partialScopesReadPathMaxima` (mới)
- AC-23: bài `timing = SHARED` có `deadline_at = started_at + config.durationMinutes`, kể cả khi `paths[].durationMinutes` trống ở đề nhiều phần; V-ACT FULL 120 câu, ba phần, 150 phút — bằng chứng: `AttemptScopeMatrixIT#sharedTimingUsesExamDuration` (mới)
- AC-24: `display_no` chạy liền từ 1 qua mọi phần; dòng bộ câu hỏi không chiếm số; Điền khuyết chiếm `unit_count` số; `unit_count` của Điền khuyết bằng số vị trí; `component_code` đúng môn của mục — bằng chứng: `AttemptPaperAssemblerTest#numberingRunsThroughPaper`, `AttemptScopeMatrixIT#vactComponentCodes` (mới)
- AC-25: `ForbiddenKeysAssert` đạt trên phản hồi `prepare`, bắt đầu, `AttemptState` (đáp án đúng, `answers`, `pairs`, điểm từng ý, lời giải, `gradingGuide`, `rubric`, `sampleAnswer`, `commonMistakes`, MAPID, độ khó) — bằng chứng: `AttemptStateIT#forbiddenKeysAbsent` (mới)
- AC-26: `AttemptState` không có câu của phần chưa mở; `STARTING` chỉ có trạng thái; `ABORTED` có `abortReason`, `retryable` — bằng chứng: `AttemptStateIT#unopenedPartsHidden`, `#shortFormsForStartingAndAborted` (mới)
- AC-27: nội dung câu trong `AttemptState` không còn đường `/api/v1/admin/`, ảnh trỏ tới đường tài nguyên của lượt — bằng chứng: `AttemptStateIT#imageSourcesPointToAttemptAssets` (mới)
- AC-28: gửi lại cùng khóa trả đúng lượt đã tạo kèm trạng thái hiện tại, không `reserve` và không sinh đề thêm; cùng khóa khác thân thì 409 `IDEMPOTENCY_KEY_REUSED`; thiếu khóa thì 400 `IDEMPOTENCY_KEY_REQUIRED`; sau `ABORTED`, khóa mới tạo lượt mới (AC-10.13) — bằng chứng: `AttemptStartIT#replaySameKeyReturnsCurrentState`, `#sameKeyDifferentBodyConflicts`, `#missingKeyIsRejected`, `#newKeyAfterAbortStartsNewAttempt` (mới)
- AC-29: `attempt_no` là số thứ tự lượt đã mở thành công; lượt `ABORTED` không có số (F13) — bằng chứng: `AttemptStartIT#attemptNoSkipsAborted` (mới)
- AC-30: lưu đề ghi sổ `ATTEMPT` trong transaction 2; câu bị xóa giữa lúc sinh và lúc lưu thì lượt về `ABORTED` với `GENERATION_FAILED` và được trả, không kẹt ở `STARTING` — bằng chứng: `AttemptStartIT#usageRecordedWithPaper`, `AttemptGenerationFailureIT#questionDeletedBeforeSaveAborts` (mới)
- AC-31: tài khoản khác nhận 404 ở `GET /me/attempts/{id}`; không đăng nhập nhận 401; tài khoản `MANAGE` nhận 403 ở `POST /me/attempts` (LT 5.2 đòi `access = EXAM`) — bằng chứng: `AttemptStartIT#otherUserGets404`, `#anonymousGets401`, `#manageAccountForbidden` (mới)
- AC-32: `ActiveAttemptChecker` trả `true` với lượt `STARTING`, `IN_PROGRESS` và `false` khi không có lượt mở; `StudentLearningProvider` trả các lượt của tài khoản, tài khoản chưa có lượt giữ `dataAvailable = false` — bằng chứng: `AttemptSpiIT#activeAttemptChecker`, `#learningProviderAttempts` (mới)
- AC-33: `AttemptGuard` từ chối lượt của người khác, phần không mở, quá hạn; gọi `OverdueAttemptCloser` trước khi kiểm và `AttemptSessionChecker` với lệnh sửa bài; bản tạm của hai cổng không làm gì — bằng chứng: `AttemptGuardTest` (mới)
- AC-34: `exam_kind_code` chụp đúng mã kỳ thi của phiên bản, `navigation_mode` là `SEQUENTIAL`; đổi danh mục sau khi bắt đầu không đổi giá trị đã chụp — bằng chứng: `AttemptStartIT#kindAndNavigationAreSnapshotted` (mới)

**Ảnh hưởng ngoài task:**

- Kiểm tra lượt đang làm trước khi ngừng hoạt động tài khoản (`identity/application/UserAdminService.java:1001`, `GET /api/v1/admin/users/{id}/deactivation-check` ở `identity/api/AdminUserController.java:158`, web `apps/web-admin/src/features/users/api.ts:158`) → `AttemptSpiIT#deactivationCheckSeesOpenAttempt` (mới): lượt `STARTING`, `IN_PROGRESS` báo có lượt đang làm, lượt đã đóng thì không; chạy lại `UserAccountAdminIT` [chủ: task này]
- Hồ sơ học tập (`identity/application/UserAdminService.java:395`, `apps/web-admin/src/features/users/components/learning-profile-tab.tsx`, `e2e/tests/learning-profile.spec.ts:86` khẳng định `dataAvailable = false` với `student01`) → `AttemptSpiIT#learningProviderAttempts` (mới), `StudentLearningProviderTests`, `learning-profile-tab.test.tsx`, E2E `learning-profile.spec.ts` vẫn xanh [chủ: task này]
- `AttemptStatusProvider` (`commerce/spi`, C-0) do `exam-access` và `/me/exams` đọc → `ExamAccessApiIT`, ca `startingAttemptCountsAsInProgress`, chạy lại trên bản cài thật: lượt `STARTING` cho `IN_PROGRESS` kèm `activeAttemptId` [chủ: task này]
- `EntitlementPort.describe`, `reserve`, `release` (`commerce/spi`, CM-4a cài) → `AttemptStartIT` đối chiếu `entitlement_usage` (`RESERVED`, `RELEASED`); chạy lại `EntitlementPortIT` [chủ: task này]
- `PublishedExamPort.loadVersion` và `findServing` phải trả `timing`, `durationMinutes`, `parts[].paths[]`, `combinations`, `omittedGroups`, `practiceScoring`, `referenceMax` → `AttemptScopeMatrixIT` (mới) đọc đủ các trường trên qua cổng thật; chạy lại `PublishedExamPortIT` [chủ: task này]
- `PaperSolverPort.generate` (EX-2) và `PaperOrdering` (`exam/spi/PaperOrdering.java`) → `AttemptScopeMatrixIT` (mới) sinh đề thật; chạy lại `PaperOrderingTest`, `PreviewPaperOrderingTest` [chủ: task này]
- Sổ đã dùng: `QuestionSourcePort.recordUsage` (`questionbank/application/QuestionSourceAdapter.java:267`) thêm dòng `ATTEMPT`, làm câu bị chặn xóa (`questionbank/application/QuestionService.java:513`, `:626`, `:630`, `:839`, `:905`) → chạy lại `QuestionSourcePortIT`, `QuestionApiEdgesIT`, `QuestionStatusIT`; `AttemptStartIT#usedQuestionCannotBeDeleted` (mới) nhận `QUESTION_IN_USE` [chủ: task này]
- Màn "Nơi đang dùng" đọc dòng sổ `ATTEMPT` (`apps/web-admin/src/features/questions/usage-real.ts:137`, `question-usage-view.tsx:33`, `versions-mapping.ts:117`) → chạy lại `bank-api.test.ts`, `detail-views.test.tsx`; số lượt hiện ra là số dòng sổ, xem Câu hỏi mở [chủ: task này]
- `recordUsage` khóa dòng câu trong transaction 2, nhiều lượt cùng bài tranh nhau cùng dòng → `AttemptStartIT#concurrentSavesOnSameExamFinish` (mới): 5 lượt cùng bài đều thành `IN_PROGRESS`, không deadlock [chủ: task này]
- Lịch worker: `shared/time/WorkerSchedulingConfig.java` bật `@Scheduled` khi có profile `worker`, và `AbstractIntegrationTest` chạy với `api`, `worker`, `test` → `AttemptStaleWorkerIT#workerDisabledInTestProfile` (mới) và lệnh `verify` đầy đủ [chủ: task này]
- `shared/security/SecurityConfig.java` để `/api/v1/me/**` ở nhánh `anyRequest().authenticated()`, tài khoản `MANAGE` đi qua được bộ lọc → `AttemptStartIT#manageAccountForbidden` (mới) [chủ: task này]
- `ForbiddenKeysAssert` (`backend/src/test/java/vn/unicom/thi360/support/`, C-0b): khóa `answer` của mệnh đề Đúng/Sai trùng tên với câu trả lời đã lưu của học viên, và `points` được phép ở cấp câu nhưng bị cấm ở cấp mệnh đề (LT 7.3) → `AttemptStateIT#forbiddenKeysAbsent` (mới) kiểm riêng `statements[].answer`, `statements[].points` theo vị trí; đổi danh sách thì chạy lại `ForbiddenKeysAssertTests` [chủ: task này]
- Tệp chung `application.yml`, `messages*.properties` → `MessageBundlesTest`, `ModularityTest` [chủ: task này]
- OpenAPI và client sinh ra (`packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`) → `OpenApiExportIT`, so khớp `target/openapi.json`, `pnpm --filter @thi360/api-client generate`, `pnpm typecheck` [chủ: task này]
- `AttemptGuard` và hai cổng tạm → `AttemptGuardTest` (mới); AT-2a thay `OverdueAttemptCloser`, AT-3 thay `AttemptSessionChecker` [chủ: AT-2a, AT-3]
- Màn chuẩn bị, màn chờ đọc `prepare`, `POST /me/attempts`, `GET /me/attempts/{id}` → `e2e/tests/attempt-flow.spec.ts` của FS-5: đi từ chuẩn bị tới khi vào phần đầu [chủ: FS-5]
- Nút "Bắt đầu" ở Bài thi của tôi dẫn tới `prepare` → `e2e/tests/my-exams.spec.ts` của FS-3: bấm Bắt đầu mở đúng `/prepare/[entitlementId]` [chủ: FS-3]

**Kế hoạch kiểm thử:**

- AC-1: `AttemptStartIT#concurrentStartsCreateOneAttempt` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `AttemptStartIT#openAttemptIsResumed` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `AttemptSchemaIT#partialUniqueIndexAllowsOneOpenAttempt` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSchemaIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-4: `AttemptStartIT#startedAtIsWhenPaperSaved` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `AttemptStartIT#noAttemptsLeftIsRejected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `AttemptGenerationFailureIT#solverErrorAbortsAndReleases` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGenerationFailureIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `AttemptGenerationFailureIT#solverTimeoutAbortsAndReleases` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGenerationFailureIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-8: `AttemptGenerationFailureIT#fullQueueAbortsWithBusy`, `#queuedJobOlderThan60sIsDropped` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGenerationFailureIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `AttemptGenerationFailureIT#lateResultIsDiscarded` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGenerationFailureIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `AttemptStaleWorkerIT#abortsAfterTwoMinutes`, `#twoWorkersReleaseOnce` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStaleWorkerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `AttemptPrepareIT#prepareReturnsStartingAttempt`, `AttemptSpiIT#statusProviderIncludesStarting` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptPrepareIT,AttemptSpiIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: `AttemptPrepareIT#prepareMatchesPublishedPaths`, `#otherUsersEntitlementIs404` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptPrepareIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-13: `AttemptStartIT#discontinuedExamCanStart` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-14: `AttemptStartIT#versionFollowsChosenPath` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `AttemptStartIT#runningAttemptKeepsIssuedPaper` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-16: `AttemptPaperAssemblerTest#sameSeedSameOrder`, `#setsStayContiguous`, `#unshuffledGroupKeepsOrder` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=AttemptPaperAssemblerTest`
- AC-17: `AttemptPaperAssemblerTest#keepOptionOrderFlagIsHonoured` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=AttemptPaperAssemblerTest`; `AttemptStartIT#optionOrderStoredWhenKept` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-18: `AttemptPaperAssemblerTest#matchingRightColumnAlwaysShuffled` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=AttemptPaperAssemblerTest`
- AC-19: `AttemptScopeMatrixIT#informaticsBranchDenominators` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptScopeMatrixIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-20: `AttemptScopeMatrixIT#subjectAndReviewExamsNeedNoChoice` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptScopeMatrixIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-21: `AttemptStartIT#preChoiceOutsideScopeIsRejected`, `#emptyServingIntersectionIsRejected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-22: `AttemptScopeMatrixIT#partialScopesReadPathMaxima` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptScopeMatrixIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-23: `AttemptScopeMatrixIT#sharedTimingUsesExamDuration` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptScopeMatrixIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-24: `AttemptPaperAssemblerTest#numberingRunsThroughPaper` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=AttemptPaperAssemblerTest`; `AttemptScopeMatrixIT#vactComponentCodes` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptScopeMatrixIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-25: `AttemptStateIT#forbiddenKeysAbsent` (mới), hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStateIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-26: `AttemptStateIT#unopenedPartsHidden`, `#shortFormsForStartingAndAborted` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStateIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-27: `AttemptStateIT#imageSourcesPointToAttemptAssets` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStateIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-28: `AttemptStartIT#replaySameKeyReturnsCurrentState`, `#sameKeyDifferentBodyConflicts`, `#missingKeyIsRejected`, `#newKeyAfterAbortStartsNewAttempt` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-29: `AttemptStartIT#attemptNoSkipsAborted` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-30: `AttemptStartIT#usageRecordedWithPaper`, `AttemptGenerationFailureIT#questionDeletedBeforeSaveAborts` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT,AttemptGenerationFailureIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-31: `AttemptStartIT#otherUserGets404`, `#anonymousGets401`, `#manageAccountForbidden` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-32: `AttemptSpiIT#activeAttemptChecker`, `#learningProviderAttempts` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSpiIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-33: `AttemptGuardTest` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=AttemptGuardTest`
- AC-34: `AttemptStartIT#kindAndNavigationAreSnapshotted` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - khóa tài khoản và hồ sơ học tập: `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSpiIT,UserAccountAdminIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `cd backend && ./mvnw -B -ntp test -Dtest=StudentLearningProviderTests`; `pnpm --filter @thi360/web-admin test src/features/users/__tests__/learning-profile-tab.test.tsx`; `pnpm --filter @thi360/e2e test tests/learning-profile.spec.ts`
  - `exam-access`: `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamAccessApiIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `EntitlementPort`: `cd backend && ./mvnw -B -ntp verify -Dit.test=EntitlementPortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - cổng `exam/spi`: `cd backend && ./mvnw -B -ntp test -Dtest=PaperOrderingTest,PreviewPaperOrderingTest`; `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamPortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - sổ đã dùng: `cd backend && ./mvnw -B -ntp verify -Dit.test=QuestionSourcePortIT,QuestionApiEdgesIT,QuestionStatusIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `pnpm --filter @thi360/web-admin test src/features/questions/__tests__/bank-api.test.ts src/features/questions/__tests__/detail-views.test.tsx`
  - lịch worker và toàn bộ backend: `cd backend && ./mvnw -B -ntp verify`
  - tệp chung: `cd backend && ./mvnw -B -ntp test -Dtest=MessageBundlesTest,ModularityTest`
  - OpenAPI và client: `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `diff -u packages/api-client/openapi.json backend/target/openapi.json`; `pnpm --filter @thi360/api-client generate && pnpm typecheck`
  - màn FS-5, FS-3 (task đó chạy): `pnpm --filter @thi360/e2e test tests/attempt-flow.spec.ts tests/my-exams.spec.ts`
- Kiểm thử rủi ro:
  - đồng thời: hai yêu cầu cùng khóa và hai yêu cầu khác khóa (AC-1, AC-28); 5 lượt cùng bài lưu đề cùng lúc; hai worker cùng dọn một lượt `STARTING` (AC-10);
  - gửi lại: cùng khóa cùng thân, cùng khóa khác thân, khóa khác trong lúc đang `STARTING`, và cùng khóa sau khi lượt đã `IN_PROGRESS` (AC-2, AC-28);
  - kết quả sinh đến muộn: giữ bộ giải bằng chốt chờ, cho worker hủy lượt, thả chốt (AC-9); token không khớp thì không ghi dòng nào;
  - biên: hàng 0; việc chờ đúng 60 giây và 60 giây + 1; `STARTING` đúng 120 giây và 120 giây + 1; phần giao phương án rỗng;
  - quyền: `EXAM` được bắt đầu, `MANAGE` bị 403, tài khoản khác 404, không đăng nhập 401;
  - migration: chạy toàn bộ migration trên DB đã có dữ liệu DS của đợt trước;
  - đỏ trước: AC-1, AC-3, AC-9, AC-17, AC-25, AC-28.
- Dữ liệu test: bài và câu từ bộ dựng DS; `AttemptTestFixtures` (mới) tạo tài khoản và quyền dùng riêng cho từng test qua service của CM-4a; `MutableClock` (C-0b) đăng ký làm `Clock` trong `@TestConfiguration`; bộ giải giả bằng `@MockitoBean PaperSolverPort` chỉ ở `AttemptGenerationFailureIT`. Mỗi lane một compose project và Testcontainers riêng (A6).
- Không kiểm: hiệu năng sinh đề (đo ở nhóm `perf` của EX-2); tải đồng thời lớn trên `recordUsage` (chỉ kiểm 5 lượt, chưa có số đo trên môi trường thật); giao diện (FS-5).

**Câu hỏi mở:** `usage-real.ts:146` đặt `attemptCount: 1` cho mỗi dòng sổ, mà sổ chỉ có một dòng cho mỗi (câu, phiên bản, bài), nên màn "Nơi đang dùng" không hiện số lượt thật: M.Trường chốt có cần số lượt thật và task nào làm.

## AT-2a · Chuyển phần tuần tự, nộp, đóng lượt, worker hạn giờ

**Spec:** LT 6 (trừ 6.1, 6.2), 6.3, L7, F7, F9, F11, F19; TM 8.5 (`confirmUse`); BS 4.

**Sản phẩm:**

- `AttemptLifecycleService`:
  - mở phần kế tiếp khi phần trước đóng, ở mọi đề (L7, F7); đồng hồ từng phần hoặc chung theo `timing`;
  - `finish` (mọi phần trừ phần cuối), `submit` (`PART_NOT_LAST`);
  - quy trình đóng lượt dùng chung (LT 6.3) cho `SUBMITTED`, `TIME_UP`, `CHOICE_TIMEOUT`, `FAULT_CLOSED`; `confirmUse`; phát `AttemptFinished` qua outbox (chủ đề `attempt.finished`, thân chỉ có `attemptId`);
  - một điểm mở để AT-2b chèn bước "chờ chọn phần 3" (`PartTransitionHook`, giao diện nội bộ, bản mặc định không làm gì).
- `finish`, `submit` nhận `finalAnswers`. Interface nội bộ `FinalAnswerWriter` (B1) được gọi trong transaction đóng, trước khi đóng phần; bản tạm không ghi gì, AT-3 thay bằng bản thật. Phản hồi có `results` của từng câu (LT 6.3).
- Đóng lười (F11): cài `OverdueAttemptCloser` của AT-1, nên mọi yêu cầu vào lượt đóng phần hoặc bài đã quá hạn trước khi xử lý.
- Worker đóng phần và bài quá hạn (`closed_at = deadline_at`), chu kỳ 10 giây (B5), lấy dòng bằng `FOR UPDATE SKIP LOCKED`.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/attempt/application/AttemptLifecycleService.java` (mới), `PartTransitionHook.java` (mới), `FinalAnswerWriter.java` (mới), `NoopFinalAnswerWriter.java` (mới), `AttemptDeadlineWorker.java` (mới), bản cài `OverdueAttemptCloser` (mới)
- `backend/src/main/java/vn/unicom/thi360/attempt/api/AttemptLifecycleController.java` (mới)
- `backend/src/test/java/vn/unicom/thi360/attempt/**`
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi của task)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** chọn phần 3, `AWAITING_CHOICE`, `retry` (AT-2b). Ghi thật `finalAnswers`, kiểm phiên sửa (AT-3). Bộ nhận `AttemptFinished` và chấm (AT-4). Đánh dấu lượt hỏng (AT-5 gọi quy trình đóng lượt với `FAULT_CLOSED`). Màn "Hoàn thành phần", "Nộp bài" (FS-5).

**Nghiệm thu:** dùng dữ liệu DS.

- AC-1: HSA, QDA: phần 1 đóng thì phần 2 `OPEN` ngay, đồng hồ phần 2 chạy từ `closed_at` của phần 1; phần 2 đóng thì gọi `PartTransitionHook`, bản mặc định để phần 3 ở `PENDING` và không đóng lượt (AT-2b thay) — bằng chứng: `AttemptFlowIT#hsaAndQdaOpenPartTwoThenCallHook` (mới)
- AC-2: BCA CA2 81 câu, ba phần lần lượt trong một đồng hồ chung — bằng chứng: `AttemptFlowIT#bcaThreePartsSharedClock` (mới)
- AC-3: V-ACT 120 câu, ba phần lần lượt trong một đồng hồ 150 phút — bằng chứng: `AttemptFlowIT#vactThreePartsInOrder` (mới)
- AC-4: SP2E Toán theo mẫu `SP2E_MATH_2025` và `SP2E_MATH_2026`, các phần nối tiếp — bằng chứng: `AttemptFlowIT#sp2eMath2025And2026` (mới)
- AC-5: Lượt `SEQUENTIAL` nhiều phần (bài ba phần từ mẫu `THPTQG_MATH_2025`, trước khi AT-8 merge): làm tuần tự, không quay lại; hạn chung không đổi khi chuyển phần — bằng chứng: `AttemptFlowIT#thptqgThreePartsSequential` (mới)
- AC-6: `VACT_PART_LANGUAGE`, `VACT_SUBJECT_TOAN`, THPTQG Toán Phần I và bài ôn `HSA_REVIEW`: đề một phần, `submit` được ngay ở phần đó, không qua `finish`, hết hạn thì `TIME_UP` — bằng chứng: `AttemptFlowIT#singlePartScopesSubmitDirectly` (mới)
- AC-7: BCA hết giờ chung khi đang ở phần 2: lượt kết thúc `TIME_UP`, phần 2 `closed_at = deadline_at`, phần 3 `SKIPPED` — bằng chứng: `AttemptTimingIT#sharedDeadlineSkipsUnopenedParts` (mới)
- AC-8: phần đã đóng không mở lại; gửi lại `finish` cho phần đã đóng nhận trạng thái đã ghi, không đổi dữ liệu — bằng chứng: `AttemptFlowIT#closedPartNeverReopens` (mới)
- AC-9: nộp trùng lúc hết giờ: một lần đóng, `confirmUse` một lần, một sự kiện `AttemptFinished` (AC-12.01) — bằng chứng: `AttemptCloseConcurrencyIT#submitRacingWorkerClosesOnce` (mới)
- AC-10: worker đóng đúng hạn khi không có thiết bị (tua giờ bằng `MutableClock`): `closed_at = deadline_at`; phần kế tiếp tính hạn từ `deadline_at`, không từ giờ worker chạy — bằng chứng: `AttemptDeadlineWorkerIT#closesAtDeadlineWithoutDevice`, `#nextPartClockStartsFromDeadline` (mới)
- AC-11: đóng lười: sau hạn, khi worker chưa chạy, `GET /me/attempts/{id}`, `finish`, `submit` đều đóng phần hoặc bài quá hạn trước khi trả lời, với `closed_at = deadline_at` — bằng chứng: `AttemptTimingIT#anyRequestClosesOverdueFirst` (mới)
- AC-12: `submit` khi còn phần chưa mở thì 409 `PART_NOT_LAST`; `finish` ở phần cuối thì 409 — bằng chứng: `AttemptFlowIT#submitBeforeLastPartRejected`, `#finishOnLastPartRejected` (mới)
- AC-13: quy trình đóng chung đúng cho `SUBMITTED`, `TIME_UP`, `CHOICE_TIMEOUT`, `FAULT_CLOSED`: đặt `end_reason`, `finished_at`, phần chưa mở thành `SKIPPED`, trạng thái `GRADING`, gọi `confirmUse`, phát sự kiện — bằng chứng: `AttemptLifecycleServiceIT#closeProcedureForEveryReason` (mới)
- AC-14: `FinalAnswerWriter` được gọi đúng một lần, trong transaction đóng, trước khi đóng phần; writer ném lỗi thì cả transaction rollback, lượt vẫn mở; yêu cầu tới sau hạn thì không gọi writer (F9) — bằng chứng: `AttemptLifecycleServiceIT#finalAnswersWrittenBeforeClose`, `#writerFailureRollsBack`, `#lateRequestSkipsWriter` (mới)
- AC-15: bản tạm `NoopFinalAnswerWriter` không ghi gì và `results` rỗng (B1) — bằng chứng: `NoopFinalAnswerWriterTest` (mới)
- AC-16: `submit` gửi lại cùng khóa nhận đúng phản hồi cũ; cùng khóa khác thân thì 409 `IDEMPOTENCY_KEY_REUSED`; thiếu khóa thì 400 `IDEMPOTENCY_KEY_REQUIRED`; khóa khác sau khi đã đóng nhận trạng thái đã ghi, không đóng lần hai — bằng chứng: `AttemptSubmitIdempotencyIT` (mới)
- AC-17: hai worker chạy cùng lúc trên cùng lô chỉ đóng mỗi lượt một lần — bằng chứng: `AttemptDeadlineWorkerIT#twoWorkersCloseOnce` (mới)
- AC-18: `ForbiddenKeysAssert` đạt trên phản hồi `finish` và `submit` (LT 7.3) — bằng chứng: `AttemptSubmitIdempotencyIT#forbiddenKeysAbsent` (mới)
- AC-19: tài khoản khác nhận 404, không đăng nhập nhận 401, tài khoản `MANAGE` nhận 403 ở `finish`, `submit` — bằng chứng: `AttemptSubmitIdempotencyIT#ownershipAndAccess` (mới)
- AC-20: `attempt_event` ghi `PART_OPENED`, `PART_CLOSED`, `SUBMITTED`, `TIME_UP`, chỉ có id câu và `seq`, không có nội dung câu trả lời — bằng chứng: `AttemptLifecycleServiceIT#eventsCarryNoAnswerContent` (mới)

**Ảnh hưởng ngoài task:**

- `EntitlementPort.confirmUse` (`commerce/spi`, CM-4a cài) → `AttemptCloseConcurrencyIT` (mới) đối chiếu `entitlement_usage = USED` một dòng; chạy lại `EntitlementPortIT` [chủ: task này]
- Outbox: `shared/outbox/OutboxPublisher.java` nhận chủ đề `attempt.finished`; `shared/outbox/OutboxRelay.java` đánh dấu thất bại vĩnh viễn sự kiện không có bộ nhận, nên lượt đóng trước khi AT-4 merge không được chấm → `AttemptLifecycleServiceIT#eventIsPublishedInCloseTransaction` (mới) chỉ kiểm dòng outbox, không chạy relay; chạy lại `OutboxRelayTests` [chủ: task này]
- Lịch worker: `shared/time/WorkerSchedulingConfig.java` và cờ `thi360.attempt.scheduling.enabled` của AT-1 → `AttemptDeadlineWorkerIT#workerDisabledInTestProfile` (mới) [chủ: task này]
- Cổng `OverdueAttemptCloser` của `AttemptGuard` (AT-1): bản cài thật thay bản tạm → chạy lại `AttemptGuardTest`, `AttemptStateIT` của AT-1 [chủ: task này]
- `ActiveAttemptChecker` và `AttemptStatusProvider` (AT-1): lượt `GRADING` không còn là lượt mở → `AttemptSpiIT` của AT-1 thêm ca sau khi nộp [chủ: task này]
- Tệp chung `messages*.properties` → `MessageBundlesTest`; ranh giới module → `ModularityTest` [chủ: task này]
- OpenAPI và client sinh ra → `OpenApiExportIT`, so khớp `target/openapi.json`, `pnpm typecheck` [chủ: task này]
- `PartTransitionHook` đổi sang bản thật của AT-2b → `AttemptFlowIT` của task này dựng service với bản không làm gì nên vẫn đúng sau khi AT-2b merge; `AttemptChoiceFlowIT` kiểm bản thật [chủ: AT-2b]
- `FinalAnswerWriter` đổi sang bản thật của AT-3 → `AttemptFinalAnswersIT` của AT-3; AT-3 xóa `NoopFinalAnswerWriterTest` kèm lý do trong PR [chủ: AT-3]
- `AttemptFinished` là đầu vào của bộ chấm → `AttemptGradingIT` của AT-4 [chủ: AT-4]
- Quy trình đóng với `FAULT_CLOSED` được gọi từ vận hành lượt → `AdminAttemptOpsIT` của AT-5 [chủ: AT-5]
- Màn làm bài gọi `finish`, `submit` và hiện `results` → `e2e/tests/attempt-flow.spec.ts` của FS-5: BCA hết giờ chung khi đang ở phần 2 thì lượt đóng, màn khóa [chủ: FS-5]

**Kế hoạch kiểm thử:**

- AC-1: `AttemptFlowIT#hsaAndQdaOpenPartTwoThenCallHook` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `AttemptFlowIT#bcaThreePartsSharedClock` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `AttemptFlowIT#vactThreePartsInOrder` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-4: `AttemptFlowIT#sp2eMath2025And2026` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `AttemptFlowIT#thptqgThreePartsSequential` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `AttemptFlowIT#singlePartScopesSubmitDirectly` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `AttemptTimingIT#sharedDeadlineSkipsUnopenedParts` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptTimingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-8: `AttemptFlowIT#closedPartNeverReopens` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `AttemptCloseConcurrencyIT#submitRacingWorkerClosesOnce` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptCloseConcurrencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `AttemptDeadlineWorkerIT#closesAtDeadlineWithoutDevice`, `#nextPartClockStartsFromDeadline` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptDeadlineWorkerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `AttemptTimingIT#anyRequestClosesOverdueFirst` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptTimingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: `AttemptFlowIT#submitBeforeLastPartRejected`, `#finishOnLastPartRejected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-13: `AttemptLifecycleServiceIT#closeProcedureForEveryReason` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptLifecycleServiceIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-14: `AttemptLifecycleServiceIT#finalAnswersWrittenBeforeClose`, `#writerFailureRollsBack`, `#lateRequestSkipsWriter` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptLifecycleServiceIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `NoopFinalAnswerWriterTest` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=NoopFinalAnswerWriterTest`
- AC-16: `AttemptSubmitIdempotencyIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSubmitIdempotencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-17: `AttemptDeadlineWorkerIT#twoWorkersCloseOnce` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptDeadlineWorkerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-18: `AttemptSubmitIdempotencyIT#forbiddenKeysAbsent` (mới), hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSubmitIdempotencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-19: `AttemptSubmitIdempotencyIT#ownershipAndAccess` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSubmitIdempotencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-20: `AttemptLifecycleServiceIT#eventsCarryNoAnswerContent` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptLifecycleServiceIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - `EntitlementPort`: `cd backend && ./mvnw -B -ntp verify -Dit.test=EntitlementPortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - outbox: `cd backend && ./mvnw -B -ntp test -Dtest=OutboxRelayTests`; `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptLifecycleServiceIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `AttemptGuard`, trạng thái và SPI của AT-1: `cd backend && ./mvnw -B -ntp test -Dtest=AttemptGuardTest`; `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptStateIT,AttemptSpiIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - tệp chung, module: `cd backend && ./mvnw -B -ntp test -Dtest=MessageBundlesTest,ModularityTest`
  - OpenAPI và client: `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `diff -u packages/api-client/openapi.json backend/target/openapi.json`; `pnpm --filter @thi360/api-client generate && pnpm typecheck`
  - AT-2b, AT-3, AT-4, AT-5, FS-5 chạy dòng của mình khi merge: `cd backend && ./mvnw -B -ntp verify`; `pnpm --filter @thi360/e2e test tests/attempt-flow.spec.ts`
- Kiểm thử rủi ro:
  - đồng thời: `submit` và worker cùng đóng một lượt bằng chốt chờ, mỗi bên thắng một lần (AC-9); hai `submit` khác khóa; `finish` và worker cùng một phần; hai worker cùng lô (AC-17);
  - gửi lại: `submit` cùng khóa và khác khóa trước và sau khi đóng (AC-16); `finish` lặp (AC-8);
  - biên thời gian: yêu cầu ở `deadline_at − 1 giây` được nhận, ở đúng `deadline_at` và `deadline_at + 1 giây` thì phần đã đóng (F9); worker chạy trễ 5 phút vẫn ghi `closed_at = deadline_at`;
  - quyền: chủ lượt, tài khoản khác, `MANAGE`, không đăng nhập (AC-19);
  - đỏ trước: AC-9, AC-10, AC-11, AC-14, AC-16.
- Dữ liệu test: bài HSA, QDA, BCA, V-ACT, SP2E, bài theo phần, theo môn và bài ôn từ bộ dựng DS; danh sách bài của DS chỉ có THPTQG Toán Phần I, nên `AttemptFlowIT` tự công bố một bài ba phần từ mẫu `THPTQG_MATH_2025`, phạm vi `SUBJECT`, bằng bộ dựng DS; `AttemptTestFixtures` của AT-1 tạo tài khoản riêng cho từng test; `MutableClock` điều khiển giờ; Testcontainers riêng cho mỗi lane.
- Không kiểm: ghi thật `finalAnswers` (AT-3, AC-11.08); chấm sau khi đóng (AT-4); màn xác nhận (FS-5).

**Câu hỏi mở:** LT 6.3 không nêu mã lỗi của `finish` ở phần cuối; lane khai một mã 409 riêng và báo Advisor.

## AT-2b · Chọn phần 3, thử lại

**Spec:** LT 6.1, L4, L8, F4, F20 (phần sinh lỗi). Định hướng Tin học đã chuyển sang AT-1 (LT 6.2 sửa theo 2B K2).

**Sản phẩm:**

- `AttemptChoiceService`:
  - `AWAITING_CHOICE`, hạn chờ 15 phút; `choice` trả 202 rồi sinh phần ở nền;
  - `retry`, `GENERATION_FAILED`, `PART_GENERATING`; hàng đầy thì phần `GENERATION_FAILED` với `GENERATION_BUSY`, lượt không bị hủy;
  - worker thử lại bằng `GenerationExecutor` trong tiến trình worker (B2) sau 30 giây, 2 phút, 5 phút, và đưa phần kẹt `GENERATING` quá 2 phút về lỗi; kết quả đến muộn bị bỏ theo `generation_token`;
  - lỗi sinh 3 lần liên tiếp thì báo vận hành: một bản ghi mức `ERROR` trên logger `thi360.job`, có mã lượt, phần, đề, phiên bản, lý do;
  - quá hạn chọn: đóng lượt `CHOICE_TIMEOUT` qua `AttemptLifecycleService`;
  - HSA Khoa học: lưu cách chia 17–17–16 và hạt giống; môn 16 câu nhận câu đã bỏ từ `PaperSolverPort`, ghi vào `attempt_event`.
- Bản cài `PartTransitionHook` của AT-2a: phần 2 đóng thì phần 3 sang `AWAITING_CHOICE`, `choice_deadline_at = closed_at(phần 2) + 15 phút`.
- Các mã lỗi ở LT 6.1.
- Phần sinh sau khi đã bắt đầu nối tiếp `display_no` của phần liền trước (BS 5).

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/attempt/application/AttemptChoiceService.java` (mới), `AwaitingChoicePartTransitionHook.java` (mới), `PartGenerationRetryWorker.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/attempt/api/AttemptChoiceController.java` (mới)
- `backend/src/test/java/vn/unicom/thi360/attempt/**`
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi LT 6.1)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** định hướng Tin học (AT-1). Quy trình đóng lượt, chuyển phần (AT-2a, gọi qua hàm công khai). Vận hành kết thúc lượt, đánh dấu hỏng, cấp bù (AT-5). Màn `ChonPhan3` và màn chờ (FS-5). Kênh báo vận hành ngoài log (chưa có đặc tả).

**Nghiệm thu:** dùng dữ liệu DS.

- AC-1: HSA FULL qua nhánh Tiếng Anh: Toán → Văn → chọn → phần 3 sinh ở nền → `OPEN`, tổng 50 câu ở phần 3 (AC-10.02) — bằng chứng: `AttemptChoiceFlowIT#hsaEnglishBranch` (mới)
- AC-2: HSA FULL qua nhánh Khoa học, đủ 10 bộ ba môn: chia 17–17–16 đúng, môn nhận 16 câu theo hạt giống, câu đã bỏ ghi vào `attempt_event` chỉ bằng id (AC-10.01) — bằng chứng: `AttemptChoiceFlowIT#hsaScienceAllTenTriples` (mới)
- AC-3: tải lại sau khi sinh xong vẫn đúng 50 câu đã lưu, cùng thứ tự — bằng chứng: `AttemptChoiceFlowIT#reloadKeepsSameFiftyQuestions` (mới)
- AC-4: QDA QĐ02: Hóa 1–25, Sinh 26–50 theo `config.combinations` của phiên bản đã chốt (AC-10.03) — bằng chứng: `AttemptChoiceFlowIT#qdaQd02SubjectOrder` (mới)
- AC-5: phần 3 không có câu đã phát ở phần trước của lượt, truyền qua `excludeQuestionIds` của `PaperSolverPort` (F5) — bằng chứng: `AttemptChoiceFlowIT#partThreeExcludesIssuedQuestions` (mới)
- AC-6: đồng hồ phần 3 chỉ chạy khi sinh xong: `started_at` là lúc lưu đề, `deadline_at = started_at + thời lượng` — bằng chứng: `AttemptChoiceTimingIT#partThreeClockStartsWhenReady` (mới)
- AC-7: `choice_deadline_at` tính từ `closed_at` của phần 2, kể cả khi phần 2 do worker đóng trễ — bằng chứng: `AttemptChoiceTimingIT#choiceDeadlineFromPartTwoClose` (mới)
- AC-8: quá hạn chọn thì lượt đóng `CHOICE_TIMEOUT`, phần 3 `SKIPPED`, 0 điểm phần 3 — bằng chứng: `AttemptChoiceTimingIT#choiceTimeoutClosesAttempt` (mới)
- AC-9: sinh lỗi sau khi xác nhận thì không bị quá hạn chọn, kể cả khi đã qua 15 phút; `retry` giữ nguyên lựa chọn, hạt giống, cách chia — bằng chứng: `AttemptChoiceRetryIT#failedPartIsNotTimedOut`, `#retryKeepsChoiceSeedAndSplit` (mới)
- AC-10: hàng đầy khi xác nhận hoặc thử lại thì phần `GENERATION_FAILED` với `GENERATION_BUSY`, lượt vẫn `IN_PROGRESS` — bằng chứng: `AttemptChoiceRetryIT#fullQueueFailsPartNotAttempt` (mới)
- AC-11: kết quả sinh đến muộn sau khi đã có lần sinh mới bị bỏ theo `generation_token`, không ghi `attempt_item` — bằng chứng: `AttemptChoiceRetryIT#lateResultDiscardedByToken` (mới)
- AC-12: phần kẹt `GENERATING` quá 2 phút về `GENERATION_FAILED`; đúng 2 phút thì chưa — bằng chứng: `PartGenerationRetryWorkerIT#stuckPartFailsAfterTwoMinutes` (mới)
- AC-13: worker tự thử lại sau 30 giây, 2 phút, 5 phút; trước các mốc thì chưa thử — bằng chứng: `PartGenerationRetryWorkerIT#retriesAtScheduledDelays` (mới)
- AC-14: lỗi sinh 3 lần liên tiếp tạo đúng một bản ghi báo vận hành; lần sinh thành công đặt lại đếm; lượt không tự kết thúc — bằng chứng: `PartGenerationRetryWorkerIT#thirdConsecutiveFailureAlertsOps`, `#attemptStaysOpenAfterFailures` (mới)
- AC-15: mã lỗi `choice`, `retry` đúng LT 6.1: `CHOICE_WINDOW_CLOSED`, `CHOICE_NOT_ALLOWED`, `SUBJECTS_INVALID`, `CHOICE_LOCKED`, `PART_NOT_AWAITING`, `PART_GENERATING` — bằng chứng: `AttemptChoiceErrorsIT` (mới)
- AC-16: `choice` gửi lại cùng khóa nhận kết quả đã ghi và trạng thái hiện tại; cùng khóa khác thân thì 409 `IDEMPOTENCY_KEY_REUSED`; thiếu khóa thì 400; khóa khác với lựa chọn khác sau khi đã xác nhận thì 409 `CHOICE_LOCKED` — bằng chứng: `AttemptChoiceIdempotencyIT#replayChoice` (mới)
- AC-17: `retry` gửi lại cùng khóa không tạo thêm lần sinh; cùng khóa khác thân thì 409 `IDEMPOTENCY_KEY_REUSED`; thiếu khóa thì 400 — bằng chứng: `AttemptChoiceIdempotencyIT#replayRetry` (mới)
- AC-18: hai `choice` đồng thời chỉ khóa một lựa chọn và chỉ sinh một lần — bằng chứng: `AttemptChoiceIdempotencyIT#concurrentChoiceLocksOnce` (mới)
- AC-19: chỉ có một nhánh hoặc một mã vẫn phải xác nhận, phần 3 vẫn sinh sau khi xác nhận — bằng chứng: `AttemptChoiceFlowIT#singleOptionStillRequiresConfirm` (mới)
- AC-20: `ForbiddenKeysAssert` đạt trên phản hồi `choice`, `retry` (LT 7.3) — bằng chứng: `AttemptChoiceErrorsIT#forbiddenKeysAbsent` (mới)
- AC-21: tài khoản khác nhận 404, không đăng nhập nhận 401, tài khoản `MANAGE` nhận 403 ở `choice`, `retry` — bằng chứng: `AttemptChoiceErrorsIT#ownershipAndAccess` (mới)
- AC-22: Phần 3 HSA, QDA sinh sau khi chọn có `display_no` nối tiếp phần 2, không trùng số, cả khi sinh lại sau lỗi — bằng chứng: `AttemptPartThreeNumberingIT#partThreeContinuesNumbering`, `#retryKeepsNumbering` (mới)

**Ảnh hưởng ngoài task:**

- `PartTransitionHook` của AT-2a đổi từ bản mặc định sang bản thật (`attempt/application`) → chạy lại `AttemptFlowIT`, `AttemptLifecycleServiceIT` của AT-2a; ca HSA, QDA của AT-2a dựng service với bản không làm gì [chủ: task này]
- `AttemptLifecycleService` đóng lượt `CHOICE_TIMEOUT` → `AttemptChoiceTimingIT#choiceTimeoutClosesAttempt` (mới): `confirmUse` một lần, một sự kiện `AttemptFinished` [chủ: task này]
- `GenerationExecutor` của AT-1 chạy thêm trong tiến trình worker (`worker & !api` ở `application.yml`, `web-application-type: none`) → `PartGenerationRetryWorkerIT#executorRunsInWorkerProfile` (mới) khởi động ngữ cảnh chỉ có profile `worker` [chủ: task này]
- `PaperSolverPort.generate` với `selections`, `solvePick`, `TrimPolicy` (EX-2) → `AttemptChoiceFlowIT#hsaScienceAllTenTriples` (mới) dùng cổng thật [chủ: task này]
- `QuestionSourcePort.recordUsage` được gọi thêm khi lưu phần 3 → `AttemptChoiceFlowIT#partThreeRecordedInUsageLedger` (mới), chạy lại `QuestionSourcePortIT` [chủ: task này]
- Lịch worker: cờ `thi360.attempt.scheduling.enabled` và `shared/time/WorkerSchedulingConfig.java` → `PartGenerationRetryWorkerIT#workerDisabledInTestProfile` (mới) [chủ: task này]
- Tệp chung `messages*.properties` → `MessageBundlesTest`; ranh giới module → `ModularityTest` [chủ: task này]
- OpenAPI và client sinh ra → `OpenApiExportIT`, so khớp `target/openapi.json`, `pnpm typecheck` [chủ: task này]
- Phần `SKIPPED` chưa có câu và phần sinh lỗi là đầu vào của tổng hợp điểm → `AttemptGradingIT` của AT-4 (C3: AT-2b merge trước AT-4) [chủ: AT-4]
- Phần `GENERATION_FAILED` kéo dài do vận hành kết thúc → `AdminAttemptOpsIT` của AT-5 [chủ: AT-5]
- Màn `ChonPhan3`, màn chờ, nút Thử lại đọc `choice`, `retry`, `AttemptState` → `e2e/tests/attempt-flow.spec.ts` của FS-5: HSA FULL thu nhỏ đi qua bước chọn phần 3 [chủ: FS-5]

**Kế hoạch kiểm thử:**

- AC-1: `AttemptChoiceFlowIT#hsaEnglishBranch` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `AttemptChoiceFlowIT#hsaScienceAllTenTriples` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `AttemptChoiceFlowIT#reloadKeepsSameFiftyQuestions` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-4: `AttemptChoiceFlowIT#qdaQd02SubjectOrder` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `AttemptChoiceFlowIT#partThreeExcludesIssuedQuestions` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `AttemptChoiceTimingIT#partThreeClockStartsWhenReady` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceTimingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `AttemptChoiceTimingIT#choiceDeadlineFromPartTwoClose` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceTimingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-8: `AttemptChoiceTimingIT#choiceTimeoutClosesAttempt` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceTimingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `AttemptChoiceRetryIT#failedPartIsNotTimedOut`, `#retryKeepsChoiceSeedAndSplit` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceRetryIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `AttemptChoiceRetryIT#fullQueueFailsPartNotAttempt` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceRetryIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `AttemptChoiceRetryIT#lateResultDiscardedByToken` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceRetryIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: `PartGenerationRetryWorkerIT#stuckPartFailsAfterTwoMinutes` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PartGenerationRetryWorkerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-13: `PartGenerationRetryWorkerIT#retriesAtScheduledDelays` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PartGenerationRetryWorkerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-14: `PartGenerationRetryWorkerIT#thirdConsecutiveFailureAlertsOps`, `#attemptStaysOpenAfterFailures` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PartGenerationRetryWorkerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `AttemptChoiceErrorsIT` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceErrorsIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-16: `AttemptChoiceIdempotencyIT#replayChoice` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceIdempotencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-17: `AttemptChoiceIdempotencyIT#replayRetry` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceIdempotencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-18: `AttemptChoiceIdempotencyIT#concurrentChoiceLocksOnce` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceIdempotencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-19: `AttemptChoiceFlowIT#singleOptionStillRequiresConfirm` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-20: `AttemptChoiceErrorsIT#forbiddenKeysAbsent` (mới), hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceErrorsIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-21: `AttemptChoiceErrorsIT#ownershipAndAccess` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptChoiceErrorsIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-22: `AttemptPartThreeNumberingIT#partThreeContinuesNumbering`, `#retryKeepsNumbering` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptPartThreeNumberingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - luồng của AT-2a: `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFlowIT,AttemptLifecycleServiceIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - sổ đã dùng: `cd backend && ./mvnw -B -ntp verify -Dit.test=QuestionSourcePortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - tiến trình worker và lịch: `cd backend && ./mvnw -B -ntp verify -Dit.test=PartGenerationRetryWorkerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - tệp chung, module: `cd backend && ./mvnw -B -ntp test -Dtest=MessageBundlesTest,ModularityTest`
  - OpenAPI và client: `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `diff -u packages/api-client/openapi.json backend/target/openapi.json`; `pnpm --filter @thi360/api-client generate && pnpm typecheck`
  - EX-2, AT-4, AT-5, FS-5 chạy dòng của mình khi merge: `cd backend && ./mvnw -B -ntp verify`; `pnpm --filter @thi360/e2e test tests/attempt-flow.spec.ts`
- Kiểm thử rủi ro:
  - đồng thời: hai `choice` cùng lúc (AC-18); `choice` đúng lúc worker đóng vì quá hạn chọn, chỉ một bên thắng; `retry` của học viên trùng lần thử lại của worker, chỉ một lần sinh;
  - gửi lại: `choice`, `retry` cùng khóa và khác khóa (AC-16, AC-17);
  - kết quả sinh đến muộn: giữ bộ giải bằng chốt chờ, sinh lần mới, thả chốt (AC-11);
  - biên: `choice` ở `choice_deadline_at − 1 giây` được nhận, ở đúng hạn thì 409 `CHOICE_WINDOW_CLOSED`; `GENERATING` đúng 120 giây và 120 giây + 1; mốc thử lại 30 giây, 2 phút, 5 phút; lỗi lần 2 và lần 3;
  - quyền: chủ lượt, tài khoản khác, `MANAGE`, không đăng nhập (AC-21);
  - đỏ trước: AC-2, AC-9, AC-11, AC-14, AC-16, AC-18.
- Dữ liệu test: bài HSA FULL (hai nhánh, đủ 5 môn Khoa học) và QDA FULL (QĐ01–QĐ05) từ bộ dựng DS; lỗi sinh giả bằng `@MockitoBean PaperSolverPort` chỉ ở `AttemptChoiceRetryIT`, `PartGenerationRetryWorkerIT`; `MutableClock` điều khiển giờ; tài khoản riêng cho từng test; Testcontainers riêng cho mỗi lane.
- Không kiểm: gửi cảnh báo qua kênh ngoài (log là kênh duy nhất đang có); giao diện (FS-5).

**Câu hỏi mở:** LT 6.1 ghi "báo vận hành" nhưng không nêu kênh. Cách tạm là bản ghi `ERROR` trên logger `thi360.job` và dòng `GENERATION_FAILED` trong `attempt_event`, xem được ở `/admin/attempts`: M.Trường chốt nếu cần thư hoặc kênh khác.

## AT-3 · Lưu bài, phiên sửa, tài nguyên của lượt

**Spec:** LT 4 (bảng định dạng câu trả lời), 6.3 (`finalAnswers`, AC-11.08), 7.1, 7.3, F8–F10, F12 (phần máy chủ). **Phụ thuộc mềm:** AT-2a.

**Sản phẩm:**

- `PUT /me/attempts/{id}/answers`: `seq` theo câu, kiểm `AttemptGuard`, `position`, cờ đánh dấu, tối đa 50 thay đổi mỗi lần, giới hạn 20 yêu cầu trong 10 giây (429 `RATE_LIMITED` kèm `Retry-After`).
- Kiểm định dạng câu trả lời theo dạng câu (bảng LT 4); lọc HTML tự luận qua `questionbank/spi/HtmlContentSanitizer` với `Options.textOnly()`, giới hạn 64 KB.
- `EditSessionService`: `POST …/session` (`sessionId`, `takeover`), `heartbeat`, `ATTEMPT_OPEN_ELSEWHERE`, `ATTEMPT_SESSION_REVOKED`. Cài `AttemptSessionChecker` của AT-1 để mọi lệnh sửa bài kiểm header `X-Attempt-Session`.
- Bản thật của `FinalAnswerWriter` (B1) qua `AnswerService`, cùng quy tắc `seq`, thay bản tạm của AT-2a.
- `GET /me/attempts/{id}/assets/{assetId}`, chỉ tài nguyên được phép (LT 7.1). Module `attempt` đọc nội dung tệp qua cổng mới `questionbank/spi/QuestionAssetContentPort`, vì `questionbank/spi` hiện chỉ có `QuestionAssetResolver` trả đường phục vụ của admin.
- Sự kiện `attempt_event` cho lưu bị từ chối, nhận phiên, tiếp quản.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/attempt/application/AnswerService.java` (mới), `EditSessionService.java` (mới), `AttemptAssetService.java` (mới), `AttemptSaveRateLimiter.java` (mới), `AnswerValueValidator.java` (mới), bản cài `FinalAnswerWriter` và `AttemptSessionChecker` (mới)
- `backend/src/main/java/vn/unicom/thi360/attempt/api/AttemptAnswerController.java` (mới), `AttemptSessionController.java` (mới), `AttemptAssetController.java` (mới)
- xóa: `backend/src/main/java/vn/unicom/thi360/attempt/application/NoopFinalAnswerWriter.java` và `NoopFinalAnswerWriterTest` của AT-2a (B1)
- `backend/src/main/java/vn/unicom/thi360/questionbank/spi/QuestionAssetContentPort.java` (mới), bản cài `backend/src/main/java/vn/unicom/thi360/questionbank/application/QuestionAssetContentAdapter.java` (mới)
- `backend/src/test/java/vn/unicom/thi360/attempt/**`
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi của task)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** quy trình đóng lượt và điểm gọi `FinalAnswerWriter` (AT-2a). Chấm câu trả lời (AT-4). IndexedDB, nhịp gửi, `BroadcastChannel`, hộp thoại tiếp quản (FS-5). Tài nguyên trong lời giải ở màn xem lại (AT-4, BS 15). Phát audio và giới hạn lần nghe (AT-6).

**Nghiệm thu:** các ca "Lưu", "Phiên" ở LT 13.

- AC-1: `seq` đến sai thứ tự: A đến sau C thì vẫn là C, lần đến muộn nhận `STALE` (AC-11.02) — bằng chứng: `AttemptAnswerIT#outOfOrderSeqKeepsLatest` (mới)
- AC-2: xóa và bỏ chọn được lưu đúng cho từng dạng (AC-11.03) — bằng chứng: `AttemptAnswerIT#clearAndDeselectSaved` (mới)
- AC-3: lưu từng ý của Đúng/Sai, từng ô Điền khuyết, từng cặp Ghép đôi (AC-11.04) — bằng chứng: `AttemptAnswerIT#partialAnswersSaved` (mới)
- AC-4: gửi lại cùng `seq` trả kết quả đã ghi, không đổi dữ liệu (AC-11.05) — bằng chứng: `AttemptAnswerIT#resendSameSeqIsIdempotent` (mới)
- AC-5: phần đã đóng thì thay đổi bị từ chối với `PART_CLOSED`; quá hạn thì `TIME_UP` (AC-11.09) — bằng chứng: `AttemptAnswerIT#closedPartRejected`, `#afterDeadlineRejected` (mới)
- AC-6: câu không thuộc lượt hoặc không `in_scope` nhận `NOT_IN_SCOPE`; một thay đổi lỗi không làm hỏng các thay đổi khác trong cùng yêu cầu — bằng chứng: `AttemptAnswerIT#outOfScopeRejectedPerChange` (mới)
- AC-7: sai định dạng theo bảng LT 4 nhận `INVALID_ANSWER` cho đủ 7 dạng câu trả lời, gồm id phương án, id ô, id cặp không thuộc câu — bằng chứng: `AnswerValueValidatorTest` (mới)
- AC-8: tự luận được lọc HTML, thẻ ảnh và mã kịch bản bị loại; câu trả lời vượt 64 KB nhận `INVALID_ANSWER` — bằng chứng: `AttemptAnswerIT#essayHtmlSanitized`, `#answerOver64KbRejected` (mới)
- AC-9: quá 50 thay đổi trong một yêu cầu bị từ chối; yêu cầu thứ 21 trong 10 giây nhận 429 `RATE_LIMITED` kèm `Retry-After`, yêu cầu thứ 20 vẫn được nhận — bằng chứng: `AttemptAnswerIT#changeLimitEnforced`, `#rateLimitPerAttempt` (mới)
- AC-10: `position` lưu vào `last_position`, cờ đánh dấu lưu theo câu và đọc lại được qua `AttemptState` — bằng chứng: `AttemptAnswerIT#positionAndFlagsPersist` (mới)
- AC-11: `finish`, `submit` kèm `finalAnswers` ghi câu trả lời trước khi đóng, theo quy tắc `seq`; `results` phản ánh từng câu; yêu cầu tới trước hạn được ghi, tới sau hạn thì không (AC-11.08, F9) — bằng chứng: `AttemptFinalAnswersIT#submitWritesFinalAnswers`, `#finishWritesFinalAnswers`, `#staleSeqInFinalAnswersIgnored`, `#afterDeadlineNotWritten` (mới)
- AC-12: thiết bị gửi `sessionId` đang giữ thì nhận lại phiên, không hỏi — bằng chứng: `AttemptSessionIT#sameSessionIdIsReclaimed` (mới)
- AC-13: phiên khác đang giữ thì luôn 409 `ATTEMPT_OPEN_ELSEWHERE` kèm `lastSeenAt`, kể cả khi phiên cũ im quá 2 phút (F10) — bằng chứng: `AttemptSessionIT#otherSessionAlwaysAsks`, `#silentSessionStillAsks` (mới)
- AC-14: `takeover = true` chuyển phiên; phiên cũ gửi lệnh sửa bài, `heartbeat`, `finish`, `submit` đều nhận 409 `ATTEMPT_SESSION_REVOKED` (AC-11.07) — bằng chứng: `AttemptSessionIT#takeoverRevokesOldSession` (mới)
- AC-15: `heartbeat` trả `serverNow`, `state` và cập nhật `edit_session_seen_at` — bằng chứng: `AttemptSessionIT#heartbeatUpdatesSeenAt` (mới)
- AC-16: tài nguyên của đề bài, phương án, ngữ liệu của phần đã mở trả về được; tài nguyên của lời giải, bài mẫu, hướng dẫn chấm và của phần chưa mở nhận 404; lượt của tài khoản khác nhận 404 — bằng chứng: `AttemptAssetIT#allowedAssetIsServed`, `#solutionAssetIs404`, `#unopenedPartAssetIs404`, `#otherUserAssetIs404` (mới)
- AC-17: `ForbiddenKeysAssert` đạt trên phản hồi `answers` (LT 7.3) — bằng chứng: `AttemptAnswerIT#forbiddenKeysAbsent` (mới)
- AC-18: `attempt_event` ghi `SAVE_REJECTED`, `SESSION_CLAIMED`, `SESSION_TAKEN_OVER`, chỉ có id câu và `seq`, không có nội dung câu trả lời — bằng chứng: `AttemptSessionIT#eventsCarryNoAnswerContent` (mới)
- AC-19: không đăng nhập nhận 401, tài khoản khác nhận 404, tài khoản `MANAGE` nhận 403 ở `answers`, `session`, `heartbeat`, `assets` — bằng chứng: `AttemptAnswerIT#ownershipAndAccess` (mới)

**Ảnh hưởng ngoài task:**

- `FinalAnswerWriter` đổi từ bản tạm sang bản thật, được gọi trong transaction đóng của `AttemptLifecycleService` (AT-2a) → chạy lại `AttemptLifecycleServiceIT`, `AttemptSubmitIdempotencyIT` của AT-2a; xóa `NoopFinalAnswerWriterTest` có nêu lý do trong PR [chủ: task này]
- `AttemptSessionChecker` của `AttemptGuard` (AT-1) đổi sang bản thật, nên mọi lệnh sửa bài cần phiên hợp lệ → chạy lại `AttemptGuardTest`, `AttemptFlowIT`, `AttemptChoiceFlowIT` với phiên đã nhận [chủ: task này]
- Cổng mới trong `questionbank/spi` (`questionbank/spi/package-info.java` là named interface) → `ModularityTest`; `AdminQuestionAssetController` không đổi hành vi, chạy lại `QuestionAssetDownloadIT`, `QuestionAssetApiIT` [chủ: task này]
- `HtmlContentSanitizer` (`questionbank/application/HtmlContentSanitizerAdapter.java`) được gọi với `Options.textOnly()`, coi mọi `img` là vi phạm → `AttemptAnswerIT#essayHtmlSanitized` (mới) [chủ: task này]
- Giới hạn tần suất dùng `RateLimitedException` (`shared/api/GlobalExceptionHandler.java:190`) và cùng cách làm của `questionbank/api/QuestionAssetUploadRateLimiter.java`, Redis lỗi thì cho qua → `AttemptAnswerIT#rateLimitPerAttempt` (mới) [chủ: task này]
- Đường ảnh trong `AttemptState` do AT-1 viết lại trỏ tới endpoint của task này → `AttemptAssetIT#stateImageSourceIsServed` (mới): tải đúng đường lấy từ `AttemptState` [chủ: task này]
- `answer`, `answer_seq` là đầu vào của bộ chấm → `AttemptGradingIT` của AT-4 chấm câu trả lời lưu qua API này [chủ: AT-4]
- Tệp chung `messages*.properties` → `MessageBundlesTest` [chủ: task này]
- OpenAPI và client sinh ra → `OpenApiExportIT`, so khớp `target/openapi.json`, `pnpm typecheck` [chủ: task này]
- Hàng chờ lưu, tiếp quản, mất mạng của màn làm bài → `e2e/tests/attempt-resilience.spec.ts` của FS-5: mất mạng rồi có mạng lại không mất câu trả lời, thiết bị thứ hai tiếp quản thì thiết bị cũ bị khóa [chủ: FS-5]

**Kế hoạch kiểm thử:**

- AC-1: `AttemptAnswerIT#outOfOrderSeqKeepsLatest` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `AttemptAnswerIT#clearAndDeselectSaved` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `AttemptAnswerIT#partialAnswersSaved` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-4: `AttemptAnswerIT#resendSameSeqIsIdempotent` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `AttemptAnswerIT#closedPartRejected`, `#afterDeadlineRejected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `AttemptAnswerIT#outOfScopeRejectedPerChange` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `AnswerValueValidatorTest` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=AnswerValueValidatorTest`
- AC-8: `AttemptAnswerIT#essayHtmlSanitized`, `#answerOver64KbRejected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `AttemptAnswerIT#changeLimitEnforced`, `#rateLimitPerAttempt` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `AttemptAnswerIT#positionAndFlagsPersist` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `AttemptFinalAnswersIT#submitWritesFinalAnswers`, `#finishWritesFinalAnswers`, `#staleSeqInFinalAnswersIgnored`, `#afterDeadlineNotWritten` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFinalAnswersIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: `AttemptSessionIT#sameSessionIdIsReclaimed` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSessionIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-13: `AttemptSessionIT#otherSessionAlwaysAsks`, `#silentSessionStillAsks` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSessionIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-14: `AttemptSessionIT#takeoverRevokesOldSession` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSessionIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `AttemptSessionIT#heartbeatUpdatesSeenAt` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSessionIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-16: `AttemptAssetIT#allowedAssetIsServed`, `#solutionAssetIs404`, `#unopenedPartAssetIs404`, `#otherUserAssetIs404` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAssetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-17: `AttemptAnswerIT#forbiddenKeysAbsent` (mới), hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-18: `AttemptSessionIT#eventsCarryNoAnswerContent` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSessionIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-19: `AttemptAnswerIT#ownershipAndAccess` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - đóng lượt, luồng phần: `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptLifecycleServiceIT,AttemptSubmitIdempotencyIT,AttemptFlowIT,AttemptChoiceFlowIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `cd backend && ./mvnw -B -ntp test -Dtest=AttemptGuardTest`
  - tài nguyên câu hỏi: `cd backend && ./mvnw -B -ntp verify -Dit.test=QuestionAssetDownloadIT,QuestionAssetApiIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - lọc HTML, giới hạn tần suất, đường ảnh: `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAnswerIT,AttemptAssetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - tệp chung, module: `cd backend && ./mvnw -B -ntp test -Dtest=MessageBundlesTest,ModularityTest`
  - OpenAPI và client: `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `diff -u packages/api-client/openapi.json backend/target/openapi.json`; `pnpm --filter @thi360/api-client generate && pnpm typecheck`
  - AT-4, FS-5 chạy dòng của mình khi merge: `cd backend && ./mvnw -B -ntp verify`; `pnpm --filter @thi360/e2e test tests/attempt-resilience.spec.ts`
- Kiểm thử rủi ro:
  - đồng thời: hai yêu cầu cùng câu với `seq` khác nhau gửi cùng lúc, kết quả cuối là `seq` lớn hơn; lưu đúng lúc phần đóng, không có câu trả lời nào được ghi sau `closed_at`; hai thiết bị cùng tiếp quản, chỉ một phiên còn hiệu lực; `submit` kèm `finalAnswers` đua với lần lưu cùng câu;
  - gửi lại: cùng `seq` (AC-4), `seq` nhỏ hơn (AC-1), `finalAnswers` có `seq` cũ (AC-11);
  - biên: `seq` bằng và lớn hơn số đã lưu; lưu ở `deadline_at − 1 giây` và đúng `deadline_at`; 50 và 51 thay đổi; 20 và 21 yêu cầu; 64 KB và 64 KB + 1 byte; phiên im đúng 2 phút;
  - quyền: chủ lượt, tài khoản khác, `MANAGE`, không đăng nhập (AC-19); tài nguyên của lời giải qua đường của lượt (AC-16);
  - đỏ trước: AC-1, AC-5, AC-11, AC-14, AC-16.
- Dữ liệu test: bài có đủ 8 dạng câu và câu có ảnh ở đề bài, phương án, lời giải từ bộ dựng DS; `AttemptTestFixtures` của AT-1 tạo tài khoản riêng cho từng test; `MutableClock` điều khiển giờ; Redis và Postgres của Testcontainers riêng cho mỗi lane.
- Không kiểm: IndexedDB và hành vi hai tab trên trình duyệt (FS-5); giới hạn tần suất khi Redis lỗi chỉ kiểm cho qua, không đo tải.

**Câu hỏi mở:** cổng `QuestionAssetContentPort` nằm trong `questionbank/spi`, B9 chưa ghi task nào được sửa vùng này: Advisor xác nhận khi mở lane.

## AT-4 · Chấm, kết quả, xem lại, lịch sử

**Spec:** LT 9.1, 10, 11, F14–F16, F20 (ghi chú trên kết quả), L3, L5; BE 8; BS 3, 15, 17.5. **Phụ thuộc mềm:** AT-2b, AT-3 (thứ tự merge C3: AT-2a → AT-2b, AT-3 → AT-4).

**Sản phẩm:**

- Bộ nhận `AttemptFinished` (outbox): chấm từng câu theo BE 8, ghi `earned_points`, `outcome`, `grading_detail`. Việc chấm chạy trong transaction riêng (`REQUIRES_NEW`), để lỗi chấm không làm hỏng transaction của `OutboxRelay`.
- Bộ chấm từng câu `ItemGrader`: service nội bộ, hàm thuần, không cần dòng `attempt` (B9); AT-5 dùng lại cho API chấm đề làm thử.
- Tổng hợp RAW và LINEAR, phần `SKIPPED`, thành phần V-ACT. Khóa dòng `attempt` khi tổng hợp. Thang hiển thị theo BS 3: tổng `maxPoints` của đường thực làm trong `parts[].paths[]` (600, 300, 180, 120 với bài theo phần, theo môn), không ghi cứng 1.200.
- Trạng thái `PENDING_REVIEW`, `SCORED`, `GRADING_ERROR`. Hết số lần thử của outbox (`OutboxRelay.DEFAULT_MAX_ATTEMPTS`) thì lượt thành `GRADING_ERROR`.
- `GET /me/attempts/{id}/result`, `/review` (theo `result_view` lấy từ `benefitsOf`), `GET /me/attempts` (lịch sử, bộ lọc). Kết quả và lịch sử trả `regrading` (nhãn "Đang chấm lại"), cờ lượt hỏng và cờ đã cấp bù (F20), để FS-6 hiện ghi chú.
- `ItemGrader` nhận thêm tham số `GradingPolicy`; một hàm `GradingPolicies.forKind(examKindCode)` chọn chính sách, ở task này luôn trả luật `PARTIAL` tính theo tỉ lệ. AT-5 và AT-7 dùng lại hàm này.
- Kết quả V-ACT có `subsections` của Tư duy khoa học theo `ExamSection` đã chụp: "Logic và phân tích số liệu" 12 câu, "Suy luận khoa học" 18 câu (BS 3).
- `GET /me/attempts/{id}/solution-assets/{assetId}` theo BS 15; `/review` viết lại đường ảnh trong lời giải sang đường này.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/attempt/domain/grading/**` (mới; gồm `ItemGrader`, `ScoreAggregator`)
- `backend/src/main/java/vn/unicom/thi360/attempt/application/AttemptFinishedHandler.java` (mới), `AttemptGradingService.java` (mới), `AttemptResultService.java` (mới), `AttemptHistoryService.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/attempt/api/AttemptResultController.java` (mới)
- `backend/src/test/java/vn/unicom/thi360/attempt/**`
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi của task)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`
- `backend/src/main/java/vn/unicom/thi360/attempt/application/AttemptSolutionAssetService.java` (mới), `attempt/api/AttemptSolutionAssetController.java` (mới)

**Ngoài phạm vi:** hàng chờ chấm tự luận, `finalize`, `reopen`, `regrade` qua API, đánh dấu hỏng, API chấm đề làm thử (AT-5). Màn kết quả, xem lại, lịch sử (FS-6). IRT và điểm 4 thành phần × 300 của V-ACT (LT 2). Mức bao phủ MAPID của kết quả theo kiến thức (phần bổ sung 2B V-48): chưa làm trong đợt này.

**Nghiệm thu:**

- AC-1: Một đáp án: chọn đúng được P, chọn sai được 0, không chọn là `BLANK`; đảo phương án vẫn chấm theo mã phương án — bằng chứng: `ItemGraderTest#singleChoiceRows` (mới)
- AC-2: Nhiều đáp án `ALL_OR_NOTHING`: đúng tập được P, thiếu hoặc thừa được 0 — bằng chứng: `ItemGraderTest#multipleChoiceAllOrNothingRows` (mới)
- AC-3: Nhiều đáp án `PARTIAL`: chọn bất kỳ phương án sai được 0; còn lại P × số đúng đã chọn ÷ số đáp án đúng, kể cả phép chia không hết — bằng chứng: `ItemGraderTest#multipleChoicePartialRows` (mới)
- AC-4: Trả lời ngắn khớp sau chuẩn hóa (bỏ khoảng trắng đầu cuối, gộp khoảng trắng, NFC, không phân biệt hoa thường); không đổi dấu phẩy thành dấu chấm, không quy phân số về số thập phân (B7) — bằng chứng: `ItemGraderTest#shortAnswerNormalizationRows` (mới)
- AC-5: Điền khuyết: tổng `points` của các vị trí đúng, mỗi vị trí đối chiếu đáp án của chính nó; kết quả từng vị trí nằm trong `grading_detail` — bằng chứng: `ItemGraderTest#clozeRows` (mới)
- AC-6: Bộ câu hỏi: tổng điểm các câu con; điểm tối đa của lượt không đếm hai lần dòng bộ và dòng câu con — bằng chứng: `ItemGraderTest#questionSetRows`, `ScoreAggregatorTest#setRowNotCountedTwice` (mới)
- AC-7: Đúng/Sai `PER_STATEMENT`: tổng `points` của các mệnh đề đúng; bỏ trống không tính là đúng — bằng chứng: `ItemGraderTest#trueFalsePerStatementRows` (mới)
- AC-8: Đúng/Sai `COUNT_TABLE`: k = 0 được 0, còn lại `scoreTable[k−1]`; bảng mặc định THPTQG 0,1 / 0,25 / 0,5 / 1 cho k = 1, 2, 3, 4 — bằng chứng: `ItemGraderTest#trueFalseCountTableRows` (mới)
- AC-9: Ghép đôi: tổng `pairPoints` của các cặp đúng, cặp bỏ trống được 0; đảo cột phải vẫn chấm theo mã mục — bằng chứng: `ItemGraderTest#matchingRows` (mới)
- AC-10: Tự luận có nội dung là `PENDING_REVIEW`, chưa có điểm; tự luận rỗng (không còn chữ sau khi lọc HTML) là `BLANK`, 0 điểm, không vào hàng chờ — bằng chứng: `ItemGraderTest#essayRows`, `AttemptGradingIT#blankEssayNotQueued` (mới)
- AC-11: `ItemGrader` là hàm thuần: chỉ nhận thân câu và câu trả lời, không truy cập DB, cùng đầu vào cho cùng kết quả — bằng chứng: `ItemGraderTest#pureFunction`, `ModularityTest` (mới với ca đầu)
- AC-12: thống kê theo đơn vị F14: Đúng, Đúng một phần, Sai, Bỏ trống, Chờ chấm; mỗi đơn vị thuộc đúng một nhóm; Điền khuyết đếm từng vị trí, Đúng/Sai đếm một câu — bằng chứng: `ScoreAggregatorTest#statisticsPerUnit` (mới)
- AC-13: RAW: điểm hiển thị là điểm thô; `BigDecimal`, làm tròn nửa lên 2 chữ số một lần ở bước cuối — bằng chứng: `ScoreAggregatorTest#rawRoundsOnceHalfUp` (mới)
- AC-14: LINEAR: điểm hiển thị = điểm thô × thang ÷ điểm tối đa; thang là tổng `maxPoints` của đường thực làm: V-ACT FULL 1.200, `VACT_PART_LANGUAGE` 600, `VACT_SUBJECT_TOAN` 300, các mẫu khác 180, 120 — bằng chứng: `ScoreAggregatorTest#linearUsesPathScale`, `AttemptResultIT#vactPartialScopesShowPathScale` (mới)
- AC-15: bài ôn V-ACT có `practice_scoring = LINEAR` và `reference_max` trống: không lỗi, không hiện 0 thay kết quả; thang là tổng `points_max` của lượt — bằng chứng: `ScoreAggregatorTest#linearWithoutReferenceMax` (mới)
- AC-16: V-ACT trả nhãn "Điểm luyện tập", ghi chú "Không phải điểm V-ACT chính thức", số câu đúng và tỷ lệ đúng theo 4 thành phần có trong đề (F15, BS 3), không có điểm thành phần — bằng chứng: `AttemptResultIT#vactPracticeScoreAndComponents` (mới)
- AC-17: chấm lại ra cùng kết quả sau khi sửa câu nguồn (AC-12.06) — bằng chứng: `AttemptGradingIT#regradeIgnoresSourceEdits` (mới)
- AC-18: giao lại cùng sự kiện `AttemptFinished` không đổi kết quả, không ghi đè điểm tự luận đã chấm — bằng chứng: `AttemptGradingIT#redeliveryIsIdempotent` (mới)
- AC-19: lỗi chấm không hiện 0: lượt `GRADING_ERROR`, kết quả trả trạng thái và câu "Bài làm đã được ghi nhận, kết quả đang được xử lý", không có trường điểm (AC-12.07) — bằng chứng: `AttemptGradingFailureIT#gradingErrorShowsNoScore` (mới)
- AC-20: lỗi chấm làm tăng `attempts` của đúng sự kiện đó, các sự kiện outbox khác vẫn được xử lý; hết số lần thử thì lượt thành `GRADING_ERROR` và có `grading_error` — bằng chứng: `AttemptGradingFailureIT#failureDoesNotBlockOutbox`, `#exhaustedRetriesSetGradingError` (mới)
- AC-21: phần `SKIPPED` chưa có câu (phần 3 HSA quá hạn chọn) được 0 điểm, điểm tối đa lấy theo bản chụp, thống kê góp số câu phải làm vào Bỏ trống — bằng chứng: `AttemptGradingIT#skippedPartWithoutItems` (mới)
- AC-22: phần `SKIPPED` đã có câu (phần 3 BCA chưa mở khi hết giờ chung) tính theo câu như câu bỏ trống, không cộng điểm tối đa hai lần (LT 9.1) — bằng chứng: `AttemptGradingIT#skippedPartWithItemsNotDoubleCounted` (mới)
- AC-23: còn câu tự luận chờ chấm thì lượt `PENDING_REVIEW`, chưa có điểm tổng; phần không có câu chờ chấm vẫn hiện điểm riêng, ghi là điểm của phần (F16) — bằng chứng: `AttemptResultIT#pendingReviewHidesTotal` (mới)
- AC-24: không còn câu chờ chấm thì lượt `SCORED`, có `graded_at` — bằng chứng: `AttemptGradingIT#noEssayGoesToScored` (mới)
- AC-25: môn không chọn không vào mẫu số hay thống kê (AC-12.04) — bằng chứng: `AttemptResultIT#unchosenSubjectsExcluded` (mới)
- AC-26: `regrading = true` thì kết quả và lịch sử vẫn hiện điểm cũ kèm nhãn; lượt bị đánh dấu hỏng trả cờ hỏng và cờ đã cấp bù, kết quả vẫn xem được (F20) — bằng chứng: `AttemptResultIT#regradingKeepsOldScore`, `#faultNoteExposed` (mới)
- AC-27: `result_view = SCORE` thì `/review` trả 403 `REVIEW_NOT_INCLUDED` — bằng chứng: `AttemptReviewIT#scoreViewHasNoReview` (mới)
- AC-28: `SCORE_AND_ANSWERS` có câu hỏi, câu trả lời, điểm từng câu và từng ý, đáp án đúng, không có lời giải; `FULL_SOLUTION` có thêm lời giải, điểm từng tiêu chí và nhận xét tự luận; thứ tự phương án đúng `option_order` của lượt — bằng chứng: `AttemptReviewIT#answersViewHasNoSolution`, `#fullSolutionView`, `#reviewKeepsOptionOrder` (mới)
- AC-29: `/review` chỉ mở khi `SCORED`, hoặc khi `PENDING_REVIEW` thì chỉ cho các câu chấm tự động — bằng chứng: `AttemptReviewIT#pendingReviewShowsAutoGradedOnly` (mới)
- AC-30: lịch sử: lượt FULL là một dòng (AC-13.02); không có lượt `STARTING`, `ABORTED`; sắp theo `started_at` mới nhất trước — bằng chứng: `AttemptHistoryIT#fullAttemptIsOneRow`, `#hidesStartingAndAborted` (mới)
- AC-31: lọc theo `q`, `kind`, `scope`, `examId`, `from`, `to` (AC-13.04) — bằng chứng: `AttemptHistoryIT#filters` (mới)
- AC-32: điểm 0 chỉ hiện khi lượt đã có điểm và thật sự được 0 (AC-13.03) — bằng chứng: `AttemptHistoryIT#zeroOnlyWhenScored` (mới)
- AC-33: hai lần làm mở đúng kết quả của từng lượt (AC-13.01) — bằng chứng: `AttemptHistoryIT#twoAttemptsOpenOwnResults` (mới)
- AC-34: tài khoản khác nhận 404 ở `result`, `review` (AC-13.06); không đăng nhập nhận 401; tài khoản `MANAGE` nhận 403 — bằng chứng: `AttemptResultIT#ownershipAndAccess`, `AttemptHistoryIT#otherUsersAttemptsHidden` (mới)
- AC-35: `ForbiddenKeysAssert` đạt trên `result` và trên `review` của lượt `SCORE` — bằng chứng: `AttemptResultIT#forbiddenKeysAbsent` (mới)
- AC-36: Kết quả V-ACT FULL có hai tiểu mục 12 và 18 câu với số đúng đúng theo câu; đề không có tiểu mục thì không có `subsections` — bằng chứng: `AttemptResultIT#vactThinkingSubsections`, `#noSubsectionsWhenNotInPaper` (mới)
- AC-37: Đường ảnh lời giải trả tệp khi chủ lượt, lượt `SCORED` hoặc `regrading = true`, `FULL_SOLUTION`, tệp thuộc lời giải của câu đã giao; phản hồi có `Cache-Control: private, no-store` — bằng chứng: `AttemptSolutionAssetIT#ownerGetsSolutionAsset`, `#regradingAttemptKeepsAccess` (mới)
- AC-38: Đường ảnh lời giải trả 404 khi: tài khoản khác, lượt chưa chấm xong, `SCORE`, `SCORE_AND_ANSWERS`, tệp không thuộc lời giải của lượt — bằng chứng: `AttemptSolutionAssetIT#otherUserGets404`, `#ungradedGets404`, `#lowerResultViewGets404`, `#foreignAssetGets404` (mới)
- AC-39: `/review` của `FULL_SOLUTION` không còn đường `/api/v1/admin/` trong lời giải; đường tài nguyên của lượt vẫn từ chối tệp lời giải — bằng chứng: `AttemptSolutionAssetIT#reviewRewritesSolutionImages`, `#attemptAssetRouteStillHidesSolution` (mới)
- AC-40: Với mọi kỳ thi trừ H-SCA, `GradingPolicies.forKind` giữ luật `PARTIAL` theo tỉ lệ; luật riêng của H-SCA do AT-7 thêm — bằng chứng: `ItemGraderTest#nonHscaKindsKeepProportionalPartial` (mới)

**Ảnh hưởng ngoài task:**

- `shared/outbox/OutboxRelay.java` chạy bộ nhận trong transaction của relay; bộ nhận tham gia transaction đó rồi ném lỗi thì commit của relay hỏng, `attempts` không tăng và sự kiện chặn cả hàng đợi (`processNextAvailable`, `relayBatch`) → `AttemptGradingFailureIT#failureDoesNotBlockOutbox` (mới), chạy lại `OutboxRelayTests` [chủ: task này]
- Sự kiện `attempt.finished` do AT-2a phát: đăng ký bộ nhận bằng `outboxRelay.register`, ở tiến trình có relay → `AttemptGradingIT#handlerRegisteredForTopic` (mới) [chủ: task này]
- `EntitlementPort.benefitsOf` (`commerce/spi`, CM-4a cài) quyết định `result_view` → `AttemptReviewIT` (mới) với cả ba mức; chạy lại `EntitlementPortIT` [chủ: task này]
- `PublishedExamPort.loadVersion` trả `parts[].paths[].maxPoints`, `referenceMax`, `practiceScoring` của phiên bản đã chốt, `maxPoints` có thể trống (`exam/application/ExamVersionSnapshot.java`) → `AttemptResultIT#vactPartialScopesShowPathScale` (mới), chạy lại `PublishedExamPortIT` [chủ: task này]
- `QuestionSourcePort.loadVersions` cung cấp đáp án theo phiên bản đã phát → `AttemptGradingIT#regradeIgnoresSourceEdits` (mới), chạy lại `QuestionSourcePortIT` [chủ: task này]
- `AttemptStatusProvider` trả lượt có kết quả cho thẻ Bài thi của tôi (`commerce/spi`) → chạy lại `MyExamsApiIT` của CM-4b sau khi có lượt `SCORED` [chủ: task này]
- `ItemGrader` được API chấm đề làm thử dùng lại → `PreviewGradingIT` của AT-5 [chủ: AT-5]
- `finalize`, `reopen`, `regrade` gọi lại bước tổng hợp → `EssayGradingIT`, `AdminAttemptOpsIT` của AT-5 [chủ: AT-5]
- Tệp chung `messages*.properties` → `MessageBundlesTest`; ranh giới module → `ModularityTest` [chủ: task này]
- OpenAPI và client sinh ra → `OpenApiExportIT`, so khớp `target/openapi.json`, `pnpm typecheck` [chủ: task này]
- Màn kết quả, xem lại, lịch sử đọc `result`, `review`, `GET /me/attempts` → `e2e/tests/attempt-result.spec.ts` của FS-6: mở kết quả, xem lại, lịch sử của lượt đã chấm [chủ: FS-6]

**Kế hoạch kiểm thử:**

- AC-1: `ItemGraderTest#singleChoiceRows` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest`
- AC-2: `ItemGraderTest#multipleChoiceAllOrNothingRows` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest`
- AC-3: `ItemGraderTest#multipleChoicePartialRows` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest`
- AC-4: `ItemGraderTest#shortAnswerNormalizationRows` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest`
- AC-5: `ItemGraderTest#clozeRows` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest`
- AC-6: `ItemGraderTest#questionSetRows`, `ScoreAggregatorTest#setRowNotCountedTwice` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest,ScoreAggregatorTest`
- AC-7: `ItemGraderTest#trueFalsePerStatementRows` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest`
- AC-8: `ItemGraderTest#trueFalseCountTableRows` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest`
- AC-9: `ItemGraderTest#matchingRows` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest`
- AC-10: `ItemGraderTest#essayRows` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest`; `AttemptGradingIT#blankEssayNotQueued` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `ItemGraderTest#pureFunction` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest,ModularityTest`
- AC-12: `ScoreAggregatorTest#statisticsPerUnit` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ScoreAggregatorTest`
- AC-13: `ScoreAggregatorTest#rawRoundsOnceHalfUp` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ScoreAggregatorTest`
- AC-14: `ScoreAggregatorTest#linearUsesPathScale` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ScoreAggregatorTest`; `AttemptResultIT#vactPartialScopesShowPathScale` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptResultIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `ScoreAggregatorTest#linearWithoutReferenceMax` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ScoreAggregatorTest`
- AC-16: `AttemptResultIT#vactPracticeScoreAndComponents` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptResultIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-17: `AttemptGradingIT#regradeIgnoresSourceEdits` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-18: `AttemptGradingIT#redeliveryIsIdempotent` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-19: `AttemptGradingFailureIT#gradingErrorShowsNoScore` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingFailureIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-20: `AttemptGradingFailureIT#failureDoesNotBlockOutbox`, `#exhaustedRetriesSetGradingError` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingFailureIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-21: `AttemptGradingIT#skippedPartWithoutItems` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-22: `AttemptGradingIT#skippedPartWithItemsNotDoubleCounted` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-23: `AttemptResultIT#pendingReviewHidesTotal` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptResultIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-24: `AttemptGradingIT#noEssayGoesToScored` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-25: `AttemptResultIT#unchosenSubjectsExcluded` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptResultIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-26: `AttemptResultIT#regradingKeepsOldScore`, `#faultNoteExposed` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptResultIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-27: `AttemptReviewIT#scoreViewHasNoReview` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptReviewIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-28: `AttemptReviewIT#answersViewHasNoSolution`, `#fullSolutionView`, `#reviewKeepsOptionOrder` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptReviewIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-29: `AttemptReviewIT#pendingReviewShowsAutoGradedOnly` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptReviewIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-30: `AttemptHistoryIT#fullAttemptIsOneRow`, `#hidesStartingAndAborted` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptHistoryIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-31: `AttemptHistoryIT#filters` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptHistoryIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-32: `AttemptHistoryIT#zeroOnlyWhenScored` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptHistoryIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-33: `AttemptHistoryIT#twoAttemptsOpenOwnResults` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptHistoryIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-34: `AttemptResultIT#ownershipAndAccess`, `AttemptHistoryIT#otherUsersAttemptsHidden` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptResultIT,AttemptHistoryIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-35: `AttemptResultIT#forbiddenKeysAbsent` (mới), hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptResultIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-36: `AttemptResultIT#vactThinkingSubsections`, `#noSubsectionsWhenNotInPaper` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptResultIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-37: `AttemptSolutionAssetIT#ownerGetsSolutionAsset`, `#regradingAttemptKeepsAccess` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSolutionAssetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-38: `AttemptSolutionAssetIT#otherUserGets404`, `#ungradedGets404`, `#lowerResultViewGets404`, `#foreignAssetGets404` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSolutionAssetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-39: `AttemptSolutionAssetIT#reviewRewritesSolutionImages`, `#attemptAssetRouteStillHidesSolution` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSolutionAssetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-40: `ItemGraderTest#nonHscaKindsKeepProportionalPartial` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - outbox: `cd backend && ./mvnw -B -ntp test -Dtest=OutboxRelayTests`; `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingFailureIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `EntitlementPort`: `cd backend && ./mvnw -B -ntp verify -Dit.test=EntitlementPortIT,PublishedExamPortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - nguồn câu: `cd backend && ./mvnw -B -ntp verify -Dit.test=QuestionSourcePortIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `/me/exams`: `cd backend && ./mvnw -B -ntp verify -Dit.test=MyExamsApiIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - tệp chung, module: `cd backend && ./mvnw -B -ntp test -Dtest=MessageBundlesTest,ModularityTest`
  - OpenAPI và client: `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `diff -u packages/api-client/openapi.json backend/target/openapi.json`; `pnpm --filter @thi360/api-client generate && pnpm typecheck`
  - AT-5, FS-6 chạy dòng của mình khi merge: `cd backend && ./mvnw -B -ntp verify`; `pnpm --filter @thi360/e2e test tests/attempt-result.spec.ts`
- Kiểm thử rủi ro:
  - biên của bảng BE 8: Nhiều đáp án `PARTIAL` 1 trên 3 đáp án (ba câu cộng lại vẫn tròn điểm); chọn đủ đúng kèm một sai; Trả lời ngắn `3,14` với đáp án `3.14`, chữ dựng sẵn và tổ hợp NFC, hoa thường tiếng Việt, khoảng trắng kép; Điền khuyết đúng một phần; Đúng/Sai k = 0, 1, 4 và bỏ trống hết; Ghép đôi bỏ trống một cặp; tự luận `<p> </p>`;
  - làm tròn: 0,125 ra 0,13; 0,4449 ra 0,44, không làm tròn hai lần thành 0,45; LINEAR 40 trên 120 câu, thang 1.200 ra đúng 400,00;
  - đồng thời: worker chấm và `finalize` cùng lượt qua khóa dòng `attempt`, tổng cuối đúng; hai worker nhận cùng sự kiện, kết quả một lần;
  - gửi lại: giao lại sự kiện sau khi đã chấm (AC-18); lỗi ở lần 1 rồi thành công ở lần 2; lỗi đủ 5 lần (AC-20);
  - quyền: chủ lượt, tài khoản khác, `MANAGE`, không đăng nhập (AC-34); ba mức `result_view` (AC-27, AC-28);
  - đỏ trước: AC-3, AC-8, AC-13, AC-14, AC-15, AC-17, AC-20, AC-22.
- Dữ liệu test: bảng ca của `ItemGraderTest` dựng từ thân câu viết tay theo `questionbank/domain/QuestionBody.java`; lượt thật từ bộ dựng DS (HSA, BCA có tự luận, V-ACT FULL, `VACT_PART_LANGUAGE`, `VACT_SUBJECT_TOAN`, THPTQG theo phần, bài ôn); gọi `OutboxRelay.relayBatch` trực tiếp vì `thi360.outbox.scheduling.enabled = false` ở profile `test`; tài khoản riêng cho từng test; Testcontainers riêng cho mỗi lane.
- Không kiểm: bài ôn V-ACT trên dữ liệu thật (bài ôn của DS là `HSA_REVIEW`, ca LINEAR không có thang kiểm ở `ScoreAggregatorTest`); mức bao phủ MAPID (V-48, chưa làm); giao diện (FS-6).

**Câu hỏi mở:** (1) Bài ôn V-ACT không có thang tham chiếu (`ExamService.scopedReferenceMax` trả trống); cách tạm là thang bằng tổng `points_max` của lượt: M.Trường, BA. (2) Kết quả theo kiến thức ghi mức bao phủ MAPID (phần bổ sung 2B V-48) chưa task nào nhận: M.Trường chốt làm ở đợt nào.

## AT-5 · Hàng chờ chấm tự luận, chấm lại, vận hành lượt

**Spec:** BS 9, 11; LT 9.2, 12, F17, F18, F20; TM 8.5 (`compensate`); 2B H17 (phần backend của làm thử như học viên).

**Sản phẩm:**

- Bảng `essay_grade`. API `/admin/grading/items/**`: danh sách, chi tiết, nhận, lưu nháp, `finalize`, `reopen`. Chấm ẩn danh; mốc 30 phút tính bằng `last_activity_at`.
- `finalize` tính lại lượt. `reopen` đặt `regrading`.
- `/admin/attempts/**`: danh sách, chi tiết kèm dòng sự kiện; `regrade`; `fault` (đóng `FAULT_CLOSED` qua `AttemptLifecycleService`); `compensate`.
- Đọc đề làm thử, trong module `attempt`: `GET /api/v1/admin/exams/{id}/preview/{previewId}/paper`, quyền `EXAM.VIEW`. Trả đề đã xếp qua `PreviewPaperPort.load`, bỏ các khóa cấm của LT 7.3, vì `POST /exams/{id}/preview` trả cả đáp án và lời giải.
- Chấm đề làm thử, trong module `attempt`: `POST /api/v1/admin/exams/{id}/preview/{previewId}/grade`, quyền `EXAM.VIEW`. Thân gồm câu trả lời theo từng câu, dạng `AnswerValue`. Đọc đề sinh thử qua `exam/spi/PreviewPaperPort.load(examId, previewId)` (EX-2), chấm bằng `ItemGrader` của AT-4, trả điểm từng câu và từng ý. Không tạo dòng `attempt`, không tạo kết quả học tập. Câu đã được ghi sổ `PREVIEW` từ lúc sinh đề thử (2B H17).
- Nhật ký `ESSAY_GRADED`, `ESSAY_REOPENED`, `ATTEMPT_REGRADED`, `ATTEMPT_VIEWED_BY_ADMIN`, `ATTEMPT_FAULT_MARKED`, `ATTEMPT_COMPENSATED`.
- `GET /api/v1/admin/grading/items/counts`, quyền `GRADING.VIEW`, cùng bộ lọc `exam`, `kind`, `from`, `to` của danh sách, trả số câu theo từng `state` (BS 11).
- `POST /api/v1/admin/exams/{id}/preview/{previewId}/trial`, quyền `EXAM.VIEW`: gọi `PreviewPaperPort.extendForTrial`, trả `{expiresAt}` (BS 9). API `paper` trả `kindCode`, `attachments`, `displaySegments` của đề sinh thử. Chấm đề làm thử dùng `GradingPolicies.forKind`.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/attempt/application/EssayGradingService.java` (mới), `AttemptAdminService.java` (mới), `PreviewGradingService.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/attempt/api/AdminGradingController.java` (mới), `AdminAttemptController.java` (mới), `AdminPreviewGradingController.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/attempt/domain/EssayGrade.java` (mới), `attempt/infrastructure/EssayGradeRepository.java` (mới)
- `backend/src/main/resources/db/migration/attempt/V<yyyyMMddHHmm>__create_essay_grade.sql` (mới)
- `backend/src/test/java/vn/unicom/thi360/attempt/**`
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (mã lỗi của task)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** màn `/grading`, `/attempts`, làm thử như học viên, bỏ mock chấm (FA-4). `PreviewPaperPort` và nơi lưu đề sinh thử (EX-2). Bộ chấm từng câu và bước tổng hợp (AT-4). Cấp lượt bù trong `commerce` (CM-4a).

**Nghiệm thu:**

- AC-1: hai người chấm cùng nhận một câu: một người được, người kia nhận 409 `GRADING_CLAIMED` kèm tên người giữ — bằng chứng: `EssayGradingConcurrencyIT#twoGradersClaimOneWins` (mới)
- AC-2: người giữ câu im quá 30 phút (`last_activity_at`) thì người khác nhận được; đúng 30 phút thì chưa; lưu nháp làm mới mốc này — bằng chứng: `EssayGradingIT#claimAfterThirtyIdleMinutes`, `#draftRefreshesActivity` (mới)
- AC-3: câu đang có người giữ thì người khác chỉ xem được, không lưu được — bằng chứng: `EssayGradingIT#otherGraderCannotSave` (mới)
- AC-4: lưu nháp với `expectedVersion` cũ nhận 412 — bằng chứng: `EssayGradingIT#staleVersionRejected` (mới)
- AC-5: `finalize` từ chối điểm ngoài khoảng 0 đến `points_max` và tổng tiêu chí khác điểm — bằng chứng: `EssayGradingIT#finalizeValidatesPoints` (mới)
- AC-6: chấm xong câu tự luận cuối cùng thì lượt thành `SCORED`, có `graded_at`; còn câu chưa chấm thì lượt vẫn `PENDING_REVIEW` — bằng chứng: `EssayGradingIT#lastEssayScoresAttempt` (mới)
- AC-7: hai người `finalize` hai câu của cùng lượt cùng lúc: tổng điểm đúng, lượt `SCORED` một lần — bằng chứng: `EssayGradingConcurrencyIT#parallelFinalizeSumsCorrectly` (mới)
- AC-8: `reopen` bắt buộc có lý do, tạo bản `DRAFT` mới thay bản cũ, lượt về `PENDING_REVIEW` với `regrading = true`; học viên vẫn thấy điểm cũ kèm nhãn "Đang chấm lại"; lịch sử các lần chấm được giữ — bằng chứng: `EssayGradingIT#reopenKeepsOldScoreAndHistory`, `#reopenRequiresReason` (mới)
- AC-9: người chấm không thấy tên đăng nhập, họ tên, email, mã học viên ở danh sách và chi tiết câu chấm, chỉ thấy mã lượt — bằng chứng: `EssayGradingIT#graderSeesNoLearnerIdentity` (mới)
- AC-10: chi tiết câu chấm có đề bài, ngữ liệu, bài làm, hướng dẫn chấm, tiêu chí, bài mẫu, `wordLimit`, số chữ, các lần chấm trước theo đúng phiên bản câu đã phát — bằng chứng: `EssayGradingIT#itemDetailUsesIssuedVersion` (mới)
- AC-11: danh sách lọc theo `state`, `exam`, `kind`, `from`, `to`, có phân trang — bằng chứng: `EssayGradingIT#listFilters` (mới)
- AC-12: `regrade` chỉ nhận lượt `GRADING_ERROR`, chạy lại chấm, giữ các bản `essay_grade` `FINAL`; lượt ở trạng thái khác nhận 409 — bằng chứng: `AdminAttemptOpsIT#regradeKeepsFinalEssays`, `#regradeRejectedForOtherStates` (mới)
- AC-13: `fault` trên lượt đang mở vì phần 3 sinh lỗi kéo dài thì đóng `FAULT_CLOSED`, phần lỗi thành `SKIPPED`, gọi `confirmUse` một lần; lượt đã đóng thì chỉ đánh dấu, kết quả vẫn giữ — bằng chứng: `AdminAttemptOpsIT#faultClosesOpenAttempt`, `#faultOnClosedAttemptKeepsResult` (mới)
- AC-14: cấp bù chỉ cho lượt đã đánh dấu hỏng, phải có lý do, mỗi lượt một lần; gửi lại cùng khóa nhận kết quả cũ; khóa khác lần hai thì 409 `ALREADY_COMPENSATED`; lần cấp đã hoàn hết thì 409 `GRANT_REFUNDED` — bằng chứng: `AdminAttemptOpsIT#compensateOnlyFaultedOnce`, `#compensateReplay`, `#compensateRefundedGrant` (mới)
- AC-15: danh sách và chi tiết lượt của vận hành có các phần và hạn, dòng sự kiện, câu trả lời cuối kèm `answer_received_at` và `seq`; xem chi tiết ghi nhật ký `ATTEMPT_VIEWED_BY_ADMIN` — bằng chứng: `AdminAttemptOpsIT#detailShowsEventsAndAudit` (mới)
- AC-16: mỗi quyền riêng lẻ: `GRADING.VIEW` chỉ đọc hàng chờ; `GRADING.MODIFY` nhận, lưu, `finalize`, `reopen`, `regrade`; `ATTEMPT.VIEW` chỉ tra cứu lượt; `ATTEMPT.COMPENSATE` `fault`, `compensate`; thiếu quyền thì 403, học viên 403, không đăng nhập 401 — bằng chứng: `AdminAttemptPermissionsIT`, `AdminApiForbiddenMatrixIT` (mới với ca đầu)
- AC-17: chấm đề làm thử trả điểm từng câu, từng ý Đúng/Sai đúng quy tắc của `ItemGrader`, đúng `optionOrder` của đề sinh thử; câu tự luận có nội dung trả trạng thái chờ chấm, không có điểm — bằng chứng: `PreviewGradingIT#gradesPerQuestionAndStatement`, `#essayHasNoAutomaticScore` (mới)
- AC-18: chấm đề làm thử không tạo dòng `attempt`, `attempt_item`, không gọi `EntitlementPort`, số dòng `question_usage_ledger` không tăng — bằng chứng: `PreviewGradingIT#createsNoLearningRecords` (mới)
- AC-19: `previewId` hết hạn, không có hoặc của bài khác đều nhận 410 `PREVIEW_EXPIRED`, ở cả đọc đề và chấm (theo `PreviewPaperPort`); câu trả lời sai định dạng hoặc câu không thuộc đề thì 422 — bằng chứng: `PreviewGradingIT#expiredPreviewIs410`, `#otherExamPreviewIs410`, `#invalidAnswerIs422` (mới)
- AC-20: `EXAM.VIEW` riêng lẻ gọi được cả đọc đề và chấm; thiếu quyền thì 403 — bằng chứng: `PreviewGradingIT#examViewAloneIsEnough`, `AdminApiForbiddenMatrixIT` (mới với ca đầu)
- AC-21: đề làm thử đọc qua `paper` đúng thứ tự và `optionOrder` của đề sinh thử, `ForbiddenKeysAssert` đạt — bằng chứng: `PreviewGradingIT#paperKeepsOrderAndHasNoAnswerKeys` (mới)
- AC-22: API đếm trả đúng số theo từng `state`, áp cùng bộ lọc với danh sách, bỏ qua tham số `state`; `GRADING.VIEW` riêng lẻ gọi được, thiếu quyền nhận 403 — bằng chứng: `EssayGradingIT#countsFollowListFilters`, `#countsNeedGradingView` (mới)
- AC-23: `trial` đặt hạn bằng thời lượng bài cộng 30 phút, gọi lại không rút ngắn; đề đã hết hạn nhận 410 `PREVIEW_EXPIRED`; thiếu `EXAM.VIEW` nhận 403 — bằng chứng: `PreviewGradingIT#trialExtendsByDurationPlusThirty`, `#trialNeverShortens`, `#trialOnExpiredPreviewIs410`, `#trialNeedsExamView` (mới)
- AC-24: API `paper` của đề H-SCA, V-SAT giữ tệp audio và phân đoạn; chấm đề làm thử truyền `kindCode` của đề sinh thử vào `GradingPolicies.forKind` và cho cùng điểm với chấm lượt thật theo luật hiện hành — bằng chứng: `PreviewGradingIT#paperKeepsAudioAndSegments`, `#gradePassesPreviewKind` (mới)

**Ảnh hưởng ngoài task:**

- `AdminApiForbiddenMatrixIT` (`backend/src/test/java/vn/unicom/thi360/AdminApiForbiddenMatrixIT.java`) tự tìm mọi endpoint `/api/v1/admin/**` và dựng thân tối thiểu từ các trường `@NotNull`, `@NotEmpty`; kiểu đa hình `AnswerValue` không dựng được mẫu → thân API chấm thử dùng `Map` theo id câu, chạy lại `AdminApiForbiddenMatrixIT` [chủ: task này]
- `PreviewPaperPort.load` (`exam/spi`, EX-2) đọc đề sinh thử từ Redis (`exam/application/PreviewSnapshotStore.java`, hạn 30 phút, kéo dài khi bấm Làm thử) → `PreviewGradingIT#expiredPreviewIs410`, `#otherExamPreviewIs410` (mới), chạy lại `PreviewPaperPortIT` [chủ: task này]
- `ExamPreviewService` đã ghi sổ `PREVIEW` khi sinh đề thử (`exam/application/ExamPreviewService.java:205`, `exam/application/ExamPreviewStore.java:179`) → `PreviewGradingIT#createsNoLearningRecords` (mới) đối chiếu sổ không đổi sau khi chấm; chạy lại `ExamPreviewIT` [chủ: task này]
- `ItemGrader` và bước tổng hợp của AT-4 → chạy lại `ItemGraderTest`, `ScoreAggregatorTest`, `AttemptGradingIT` [chủ: task này]
- `AttemptLifecycleService` đóng `FAULT_CLOSED` (AT-2a) → `AdminAttemptOpsIT#faultClosesOpenAttempt` (mới), chạy lại `AttemptLifecycleServiceIT` [chủ: task này]
- `EntitlementPort.compensate` (`commerce/spi`, CM-4a cài) → `AdminAttemptOpsIT#compensateOnlyFaultedOnce` (mới), chạy lại `EntitlementCompensationIT` [chủ: task này]
- Kết quả và lịch sử của học viên hiện `regrading`, ghi chú lượt hỏng (AT-4) → `AttemptResultIT`, `AttemptHistoryIT` của AT-4 chạy lại sau khi `reopen`, `fault` [chủ: task này]
- Quyền `GRADING.*`, `ATTEMPT.*` (bit 23–26, C-0) gán mặc định theo BS 7 → `AdminAttemptPermissionsIT` (mới) vẫn dùng vai trò tự tạo để kiểm từng quyền [chủ: task này]
- Nhật ký: `audit/spi/AuditRecorder` nhận các mã mới → `AdminAttemptOpsIT#detailShowsEventsAndAudit` (mới), `EssayGradingIT#auditRecorded` (mới) [chủ: task này]
- Tệp chung `messages*.properties` → `MessageBundlesTest`; migration mới và ranh giới module → `ModularityTest`, lệnh `verify` đầy đủ [chủ: task này]
- OpenAPI và client sinh ra → `OpenApiExportIT`, so khớp `target/openapi.json`, `pnpm typecheck` [chủ: task này]
- Màn `/grading` đang chạy mock theo từng lượt (`apps/web-admin/src/features/grading/api.ts`, `api.mock.ts`, `apps/web-admin/src/lib/mock/flags.ts`), còn API thật theo từng câu → FA-4 đổi lớp `api.ts`, bỏ cờ mock; `e2e/tests/grading-attempts.spec.ts` chấm tự luận đầu cuối [chủ: FA-4]
- Menu `/grading` dùng `GRADING.VIEW` và `mockFlag` (`apps/web-admin/src/lib/navigation.ts:96`) → FA-4 bỏ `mockFlag`, chạy lại test điều hướng [chủ: FA-4]
- Màn làm thử gọi `paper` và `grade`, màn `/attempts`, `/attempts/[id]` đọc API vận hành → `e2e/tests/exam-trial.spec.ts` ("không lộ đáp án", "chấm SP2E Toán") và `grading-attempts.spec.ts` của FA-4 [chủ: FA-4]
- Số đếm trạng thái ở `/grading` đọc API đếm mới của task này → `grading-list.test.tsx` của FA-4 kiểm số đếm theo bộ lọc [chủ: FA-4]

**Kế hoạch kiểm thử:**

- AC-1: `EssayGradingConcurrencyIT#twoGradersClaimOneWins` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingConcurrencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `EssayGradingIT#claimAfterThirtyIdleMinutes`, `#draftRefreshesActivity` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `EssayGradingIT#otherGraderCannotSave` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-4: `EssayGradingIT#staleVersionRejected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `EssayGradingIT#finalizeValidatesPoints` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `EssayGradingIT#lastEssayScoresAttempt` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `EssayGradingConcurrencyIT#parallelFinalizeSumsCorrectly` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingConcurrencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-8: `EssayGradingIT#reopenKeepsOldScoreAndHistory`, `#reopenRequiresReason` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `EssayGradingIT#graderSeesNoLearnerIdentity` (mới), hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `EssayGradingIT#itemDetailUsesIssuedVersion` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `EssayGradingIT#listFilters` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: `AdminAttemptOpsIT#regradeKeepsFinalEssays`, `#regradeRejectedForOtherStates` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AdminAttemptOpsIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-13: `AdminAttemptOpsIT#faultClosesOpenAttempt`, `#faultOnClosedAttemptKeepsResult` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AdminAttemptOpsIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-14: `AdminAttemptOpsIT#compensateOnlyFaultedOnce`, `#compensateReplay`, `#compensateRefundedGrant` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AdminAttemptOpsIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `AdminAttemptOpsIT#detailShowsEventsAndAudit` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AdminAttemptOpsIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-16: `AdminAttemptPermissionsIT` (mới), `AdminApiForbiddenMatrixIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AdminAttemptPermissionsIT,AdminApiForbiddenMatrixIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-17: `PreviewGradingIT#gradesPerQuestionAndStatement`, `#essayHasNoAutomaticScore` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-18: `PreviewGradingIT#createsNoLearningRecords` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-19: `PreviewGradingIT#expiredPreviewIs410`, `#otherExamPreviewIs410`, `#invalidAnswerIs422` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-20: `PreviewGradingIT#examViewAloneIsEnough` (mới), `AdminApiForbiddenMatrixIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewGradingIT,AdminApiForbiddenMatrixIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-21: `PreviewGradingIT#paperKeepsOrderAndHasNoAnswerKeys` (mới), hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-22: `EssayGradingIT#countsFollowListFilters`, `#countsNeedGradingView` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=EssayGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-23: `PreviewGradingIT#trialExtendsByDurationPlusThirty`, `#trialNeverShortens`, `#trialOnExpiredPreviewIs410`, `#trialNeedsExamView` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-24: `PreviewGradingIT#paperKeepsAudioAndSegments`, `#gradePassesPreviewKind` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - ma trận quyền admin: `cd backend && ./mvnw -B -ntp verify -Dit.test=AdminApiForbiddenMatrixIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - đề sinh thử: `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamPreviewIT,PreviewPaperPortIT,PreviewGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - chấm và kết quả của AT-4: `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest,ScoreAggregatorTest`; `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingIT,AttemptResultIT,AttemptHistoryIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - đóng lượt: `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptLifecycleServiceIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `EntitlementPort`: `cd backend && ./mvnw -B -ntp verify -Dit.test=EntitlementCompensationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - tệp chung, migration, module: `cd backend && ./mvnw -B -ntp test -Dtest=MessageBundlesTest,ModularityTest`; `cd backend && ./mvnw -B -ntp verify`
  - OpenAPI và client: `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `diff -u packages/api-client/openapi.json backend/target/openapi.json`; `pnpm --filter @thi360/api-client generate && pnpm typecheck`
  - FA-4 chạy dòng của mình khi merge: `pnpm --filter @thi360/web-admin test`; `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts tests/exam-trial.spec.ts`
- Kiểm thử rủi ro:
  - đồng thời: hai người chấm cùng nhận một câu bằng chốt chờ, chỉ một dòng `essay_grade` chưa bị thay (AC-1); hai `finalize` cùng câu, một lần có hiệu lực; hai `finalize` hai câu cùng lượt (AC-7); `reopen` đua với `finalize`; `compensate` đồng thời hai khóa, chỉ một lượt bù;
  - gửi lại: `compensate` cùng khóa và khác khóa (AC-14); `finalize` lặp; `regrade` lặp;
  - biên: im đúng 30 phút và 30 phút + 1 giây; điểm 0, `points_max`, `points_max` + 0,01; tổng tiêu chí lệch 0,01; đề sinh thử vừa hết hạn;
  - quyền: từng quyền riêng lẻ và khi thiếu (AC-16, AC-20); học viên; không đăng nhập; người chấm không thấy danh tính học viên (AC-9);
  - migration: `essay_grade` chạy trên DB đã có lượt và câu trả lời của AT-1 đến AT-4;
  - đỏ trước: AC-1, AC-7, AC-9, AC-12, AC-14, AC-18, AC-21.
- Dữ liệu test: bài BCA FULL có câu tự luận từ bộ dựng DS (bài duy nhất cho ca chấm tay); đề sinh thử của bài SP2E Toán trong DS, sinh qua `POST /api/v1/admin/exams/{id}/preview`; hết hạn giả bằng cách xóa khóa `exam:preview:<previewId>` trong Redis của test; vai trò tự tạo cho từng quyền; `MutableClock` điều khiển giờ; tài khoản riêng cho từng test; Testcontainers riêng cho mỗi lane.
- Không kiểm: giao diện chấm và làm thử (FA-4).

**Câu hỏi mở:** (1) LT 12 không nêu cách xử lý `fault` với lượt `STARTING` hoặc `ABORTED`; cách tạm là từ chối 409: M.Trường, BA. (2) API `paper` không có trong quyết định D-b, được thêm vì FA-4 cần đề không kèm đáp án: M.Trường xác nhận.

## AT-6 · Phát audio có giới hạn lần nghe, kiểm tệp audio

**Spec:** BS 17.1, 17.2, 17.4; LT 5.1, 5.2, 7.1; 2B bổ sung H-SCA, V-SAT mục 6, 7.

**Sản phẩm:**

- Bảng `attempt_audio_play` và bốn API `plays`, `resume`, `progress`, `end` theo BS 17.2, đều kiểm `AttemptGuard` và `X-Attempt-Session`. Số lần nghe tối đa là hằng số 2 trong mã. `lease` 45 giây, dung sai 3 giây đặt ở `thi360.attempt.audio.*`, đọc lúc mở lần nghe.
- Mỗi lần nghe có `play_id`, `last_request_id` cho gửi lại, mốc đoạn phát, ngân sách dung sai (`segment_excess_ms`, `tolerance_used_ms`), `lease_until`. Mọi thay đổi trạng thái chạy dưới khóa dòng `attempt_audio_play`, xét theo thứ tự năm bước của BS 17.2. Khi phần đã đóng, mọi đường đưa lần nghe dở về `ENDED` với `CLOSED` dưới khóa dòng, rồi trả phản hồi theo bảng "Khi phần đã đóng" của BS 17.2: `plays`, `resume`, `progress` 409; `end` 200 không đổi dữ liệu; tải tệp 404; `AttemptState` trả bình thường với audio `CLOSED` (review vòng 4, S4-1). Lần nghe đã bấm được phát tiếp đến khi phần đóng, không có hạn riêng. Máy chủ không tự mở lần nghe mới.
- Đường tài nguyên của lượt phục vụ audio khi `playId` đúng lần đang `PLAYING`, còn hạn và phần còn mở, có `Range` (206), `Cache-Control: no-store`. Phần đóng, nộp bài, hết giờ đưa lần nghe dở về `ENDED` với `CLOSED`.
- `AttemptState` có `audioPolicy: {maxPlays: 2, seekable: false}`, tệp đính kèm của câu gốc `attachments[]` và trạng thái nghe của từng audio (`durationMs`, `playsUsed`, `playsLeft`, `state`, `endReason`, lần nghe dở). `prepare` có `answerFormats[]` và `listeningPolicy` (BS 17.1).
- Kiểm tệp audio sau khi ghép đề và trước transaction lưu đề, qua giao diện nội bộ `AudioFileProbe`, bản thật gọi `questionbank/spi/QuestionAttachmentPort` với `verifyStorage = true`: thiếu tệp thì `ABORTED` với `AUDIO_ASSET_MISSING`, `retryable = true`, trả lượt; kho lỗi kết nối thì `GENERATION_FAILED`, trả lượt.
- Đọc thời lượng MP3 bằng `AudioDurationReader`: đọc tệp qua cùng đường AT-3 dùng để phục vụ tệp, đếm khung MPEG bằng một thư viện đọc MP3 (chọn trong task, ghi vào `ARCHITECTURE.md`), đệm trong bảng `audio_asset_duration` theo `asset_id`. Task kiểm mã 2B rằng nội dung của một `asset_id` không đổi; nếu đổi được thì khóa đệm thêm `sha256`. Không đọc được thì `ABORTED` với `AUDIO_ASSET_INVALID`, `retryable = true`, trả lượt. Lưu đề tạo một dòng `attempt_audio_play` `IDLE` có `duration_ms` cho mỗi audio.
- Chỉ ở profile `e2e`: `POST`, `DELETE /api/v1/test/audio-faults/{examId}` bật, tắt tình huống thiếu tệp của một bài qua bản bọc `AudioFileProbe` (theo B6). Profile khác không có bean và đường này.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/attempt/application/AttemptAudioService.java` (mới), `AttemptAudioFileCheck.java` (mới), `AudioFileProbe.java` (mới), `AudioDurationReader.java` (mới)
- thêm vào: `backend/pom.xml` (thư viện đọc MP3), `ARCHITECTURE.md` (ghi thư viện và lý do)
- `backend/src/main/java/vn/unicom/thi360/attempt/api/AttemptAudioController.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/attempt/testsupport/E2eAudioFaultController.java` (mới, chỉ profile `e2e`)
- `backend/src/main/java/vn/unicom/thi360/attempt/domain/AttemptAudioPlay.java` (mới), `attempt/infrastructure/AttemptAudioPlayRepository.java` (mới)
- `backend/src/main/resources/db/migration/attempt/V<yyyyMMddHHmm>__create_attempt_audio_play.sql` (mới, tạo cả `audio_asset_duration`)
- `backend/src/test/java/vn/unicom/thi360/attempt/audio/**` (mới, gồm `AudioAssetFixture`), `backend/src/test/resources/attempt/audio/**` (mới, ba tệp MP3 mẫu)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/attempt/application/AttemptAssetService.java` (nhánh audio, `Range`), bước kiểm tệp trong luồng sinh đề của AT-1, `AttemptState`, `prepare`
- thêm vào: `backend/src/main/resources/application.yml` (`thi360.attempt.audio.*`)
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (`AUDIO_*`)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** Trình phát trên web (FS-7). Đếm lần nghe khi admin làm thử (FA-5). Giải mã âm thanh để tìm chỗ hỏng: chỉ đọc khung MP3 để lấy thời lượng (2B bổ sung H48). Kiểm ảnh và tệp không phải audio. Chống ghi âm hoặc giữ dữ liệu trình duyệt đã tải (BS 17.2).

**Nghiệm thu:**

- AC-1: Bấm Nghe lần đầu (`plays` với `requestId` mới) tăng `play_count` lên 1, trả `playId` và `durationMs` do máy chủ đọc; sau khi lần 1 kết thúc, lần mở thứ hai tăng lên 2 với `playId` mới; lần mở thứ ba nhận 409 `AUDIO_PLAYS_EXHAUSTED`, số lần không đổi — bằng chứng: `AttemptAudioIT#playsAreCountedUpToTwo`, `#thirdPlayIsRejected` (mới)
- AC-2: Gửi lại `plays` cùng `requestId` (mất phản hồi) trả đúng `playId` cũ, không tăng số lần; `plays` với `requestId` khác khi đang có lần nghe dở nhận 409 `AUDIO_PLAY_IN_PROGRESS` kèm `playId` — bằng chứng: `AttemptAudioIT#retriedOpenIsIdempotent`, `#openWhilePlayingIsRejected` (mới)
- AC-3: Bốn lần phát tiếp liên tiếp sau tải lại trang, mất mạng, trình duyệt từ chối phát, và một lần từ thiết bị tiếp quản: cùng `playId`, từ vị trí đã lưu, `play_count` không đổi — bằng chứng: `AttemptAudioIT#resumeNeverUsesPlay`, `#takeoverResumesSamePlay` (mới)
- AC-4: Phát tiếp sau 30 phút gián đoạn khi phần còn mở: cùng `playId`, từ vị trí đã lưu, `play_count` không đổi. Lần trước chưa `end` mà phần đã đóng, đã nộp hoặc hết giờ: `plays`, `resume` đều nhận 409 `PART_CLOSED` hoặc `TIME_UP`, lần dở thành `ENDED` với `CLOSED`, `play_count` không tăng, kể cả gửi lại `requestId` cũ; kiểm ngay trước, đúng, sau lúc đóng phần — bằng chứng: `AttemptAudioIT#resumeAfterLongGapWhilePartOpen`, `#openAfterPartClosedWithoutEnd`, `#retryOldRequestAfterCloseUsesNoPlay`, `#partCloseBoundaries` (mới)
- AC-5: Không gửi `progress`: quá `lease_until` thì tải tệp nhận 404, `resume` cấp lại quyền tải; gửi `progress` đứng yên giữ quyền tải tới khi phần đóng, sau đó nhận 404; `positionMs` bị chặn ở `duration_ms` do máy chủ đọc, không theo dữ liệu trình duyệt gửi — bằng chứng: `AttemptAudioAssetIT#expiredLeaseGets404`, `#resumeRenewsLease`, `#stalledProgressEndsAtPartClose`, `#positionCappedByServerDuration` (mới)
- AC-6: `progress` nhỏ hơn vị trí đã lưu, âm, vượt `duration_ms`, hoặc vượt vị trí dự kiến cộng dung sai còn lại bị từ chối và giữ vị trí; `progress` tiến theo thời gian thực và đúng biên dung sai được nhận — bằng chứng: `AttemptAudioIT#progressFollowingRealTimeAccepted`, `#backwardSeekRejected`, `#outOfRangeRejected`, `#forwardSeekBeyondCeilingRejected`, `#toleranceBoundaryAccepted` (mới)
- AC-7: Đồng hồ đứng yên: 100 yêu cầu `progress` chỉ tiến tổng cộng 3 giây; xen ba lần phát tiếp giữa các `progress` cũng không vượt tổng 3 giây của lần nghe; lần nghe mới bắt đầu lại từ 3 giây — bằng chứng: `AttemptAudioIT#toleranceIsNotCumulative`, `#resumeDoesNotRefillSeekTolerance`, `#newPlayGetsFreshTolerance` (mới)
- AC-8: `progress`, `end` của lần nghe 1 đến sau khi lần nghe 2 đã mở nhận 409 `AUDIO_PLAY_STALE`, trạng thái và vị trí của lần 2 không đổi; `end` lặp cùng `playId` không đổi dữ liệu — bằng chứng: `AttemptAudioIT#staleEventsCannotTouchNewPlay`, `#endIsIdempotent` (mới)
- AC-9: Tệp mẫu `silence-30s-cbr.mp3` và `silence-30s-vbr-xing.mp3` của task này đọc đúng 30 giây, tệp nghe `hsca-en-listening-single-01.mp3` của 2B đọc đúng 2,088 giây, sai số dưới một khung; `header-only.mp3` làm lượt `ABORTED` với `AUDIO_ASSET_INVALID`, trả lượt; lượt sau dùng bảng đệm, không đọc lại tệp. Thời lượng đọc từ byte thật, không giả lập kết quả — bằng chứng: `AttemptAudioFileCheckIT#serverReadsMp3Duration`, `#readsTwoBSample`, `#unreadableDurationAbortsAndReleases`, `#durationIsCached` (mới)
- AC-10: Đường tài nguyên trả audio khi đang phát, hỗ trợ `Range` (206) và có `Cache-Control: no-store`; sai `playId`, chưa mở lần nghe, đã `ENDED`, phần đã đóng, hết giờ đều nhận 404 — bằng chứng: `AttemptAudioAssetIT#servesRangeWhilePlaying`, `#wrongPlayIdGets404`, `#notPlayingGets404`, `#closedPartGets404`, `#afterDeadlineGets404` (mới)
- AC-11: Hai `plays` đồng thời với `requestId` khác chỉ mở một lần nghe; hai `resume` đồng thời giữ đúng một ngân sách dung sai — bằng chứng: `AttemptAudioConcurrencyIT#parallelOpenCountsOnce`, `#parallelResumeKeepsTolerance` (mới)
- AC-12: Phần đóng, nộp bài, hết giờ khi đang nghe: lần nghe thành `ENDED` với `CLOSED`, không mở được lần mới — bằng chứng: `AttemptAudioIT#closingPartEndsPlay` (mới)
- AC-13: Phiên sửa đã bị tiếp quản nhận 409 `ATTEMPT_SESSION_REVOKED`; tài khoản khác nhận 404, không đăng nhập nhận 401, tài khoản `MANAGE` nhận 403 ở bốn API — bằng chứng: `AttemptAudioIT#revokedSessionRejected`, `#ownershipAndAccess` (mới)
- AC-14: Thiếu tệp audio khi mở lượt: `ABORTED`, `AUDIO_ASSET_MISSING`, `retryable = true`, `release` đúng một lần, không có `attempt_item`, đồng hồ chưa chạy, nhật ký vận hành có mã đề, mã câu, mã tệp — bằng chứng: `AttemptAudioFileCheckIT#missingAudioAbortsAndReleases` (mới)
- AC-15: Kho lưu trữ lỗi kết nối khi kiểm tệp: `ABORTED` với `GENERATION_FAILED`, trả lượt; đề không có audio thì không gọi kho — bằng chứng: `AttemptAudioFileCheckIT#storageFailureAbortsAndReleases`, `#paperWithoutAudioSkipsStorage` (mới)
- AC-16: Đường `/api/v1/test/audio-faults/**` có ở profile `e2e`, trả 404 ở profile khác — bằng chứng: `AttemptAudioFileCheckIT#faultEndpointOnlyInE2eProfile` (mới)
- AC-17: `prepare` của bài có audio trả `listeningPolicy` hai lần nghe, không tua và `answerFormats`; bài không có audio có `listeningPolicy` trống — bằng chứng: `AttemptPrepareIT#listeningPolicyOnlyWithAudio` (mới)
- AC-18: `ForbiddenKeysAssert` đạt trên phản hồi thành công và lỗi của `plays`, `resume`, `progress`, `end` và trên `AttemptState` có `attachments[]` — bằng chứng: `AttemptAudioIT#forbiddenKeysAbsent` (mới)
- AC-19: Phần Nghe đã đóng (học viên đang ở phần Đọc), sau khi nộp và sau khi hết giờ, với ba trạng thái audio trước lúc đóng: `PLAYING` thành `ENDED`/`CLOSED`; đã `ENDED`/`FINISHED` giữ nguyên `FINISHED`; `IDLE` (`play_count = 0`) vẫn `IDLE`, không có `playId`, `playsLeft` không đổi nhưng mở bị chặn 409. `GET` lượt trả `AttemptState` bình thường theo đúng trạng thái lưu; `progress` nhận 409 `PART_CLOSED` hoặc `TIME_UP`, vị trí không đổi; `end` gửi lặp trả 200 với trạng thái thật của lần nghe đó (`CLOSED` hoặc `FINISHED`, không đổi `FINISHED` thành `CLOSED`), không đổi dữ liệu; tải tệp nhận 404; `play_count` không đổi ở mọi đường (review vòng 5, S5-1) — bằng chứng: `AttemptAudioIT#stateAfterListeningClosed`, `#progressAndEndAfterClose`, `#closedPartAudioStateMatrix` (mới)

**Ảnh hưởng ngoài task:**

- `AttemptAssetService` của AT-3 có nhánh audio → chạy lại `AttemptAssetIT`; ảnh và tệp thường vẫn theo LT 7.1 [chủ: task này]
- Luồng sinh đề của AT-1 thêm một bước trước transaction lưu đề và một `abort_reason` mới → chạy lại `AttemptGenerationFailureIT`, `AttemptStartIT` [chủ: task này]
- AT-8 cùng sửa khâu mở lượt và merge sau task này → AT-8 chạy `AttemptAudioFileCheckIT` trên SHA đã có cả hai [chủ: AT-8]
- `QuestionAttachmentPort` của đợt 2B được gọi thêm từ `attempt` → `ModularityTest` đạt [chủ: task này]
- Thêm thư viện đọc MP3 vào `backend/pom.xml` → `./mvnw -B -ntp verify` toàn bộ backend xanh, `ARCHITECTURE.md` ghi thư viện [chủ: task này]
- Màn chờ sinh đề hiện lý do `AUDIO_ASSET_MISSING` và nút Thử lại → `attempt-hsca-vsat.spec.ts` của FS-7 [chủ: FS-7]
- Tệp chung `messages*.properties` → `MessageBundlesTest`; OpenAPI → `OpenApiExportIT`, `pnpm typecheck` [chủ: task này]

**Kế hoạch kiểm thử:**

- AC-1: `AttemptAudioIT#playsAreCountedUpToTwo`, `#thirdPlayIsRejected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `AttemptAudioIT#retriedOpenIsIdempotent`, `#openWhilePlayingIsRejected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `AttemptAudioIT#resumeNeverUsesPlay`, `#takeoverResumesSamePlay` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-4: `AttemptAudioIT#resumeAfterLongGapWhilePartOpen`, `#openAfterPartClosedWithoutEnd`, `#retryOldRequestAfterCloseUsesNoPlay`, `#partCloseBoundaries` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `AttemptAudioAssetIT#expiredLeaseGets404`, `#resumeRenewsLease`, `#stalledProgressEndsAtPartClose`, `#positionCappedByServerDuration` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioAssetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `AttemptAudioIT#progressFollowingRealTimeAccepted`, `#backwardSeekRejected`, `#outOfRangeRejected`, `#forwardSeekBeyondCeilingRejected`, `#toleranceBoundaryAccepted` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `AttemptAudioIT#toleranceIsNotCumulative`, `#resumeDoesNotRefillSeekTolerance`, `#newPlayGetsFreshTolerance` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-8: `AttemptAudioIT#staleEventsCannotTouchNewPlay`, `#endIsIdempotent` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `AttemptAudioFileCheckIT#serverReadsMp3Duration`, `#readsTwoBSample`, `#unreadableDurationAbortsAndReleases`, `#durationIsCached` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioFileCheckIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `AttemptAudioAssetIT#servesRangeWhilePlaying`, `#wrongPlayIdGets404`, `#notPlayingGets404`, `#closedPartGets404`, `#afterDeadlineGets404` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioAssetIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `AttemptAudioConcurrencyIT#parallelOpenCountsOnce`, `#parallelResumeKeepsTolerance` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioConcurrencyIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: `AttemptAudioIT#closingPartEndsPlay` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-13: `AttemptAudioIT#revokedSessionRejected`, `#ownershipAndAccess` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-14: `AttemptAudioFileCheckIT#missingAudioAbortsAndReleases` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioFileCheckIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-15: `AttemptAudioFileCheckIT#storageFailureAbortsAndReleases`, `#paperWithoutAudioSkipsStorage` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioFileCheckIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-16: `AttemptAudioFileCheckIT#faultEndpointOnlyInE2eProfile` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioFileCheckIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-17: `AttemptPrepareIT#listeningPolicyOnlyWithAudio` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptPrepareIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-18: `AttemptAudioIT#forbiddenKeysAbsent` (mới), hợp đồng, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-19: `AttemptAudioIT#stateAfterListeningClosed`, `#progressAndEndAfterClose`, `#closedPartAudioStateMatrix` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAssetIT,AttemptGenerationFailureIT,AttemptStartIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `cd backend && ./mvnw -B -ntp test -Dtest=MessageBundlesTest,ModularityTest`
  - `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `pnpm --filter @thi360/api-client generate && pnpm typecheck`
- Kiểm thử rủi ro:
  - đồng thời: hai `plays` cùng lúc, `plays` đua với hết giờ, `end` cũ đua với `plays` mới, hai `resume` cùng lúc, hai thiết bị với phiên sửa khác nhau;
  - gửi lặp: `plays` cùng `requestId`, `end` lặp, `progress` lặp cùng vị trí, `resume` lặp;
  - biên: đúng lần nghe thứ hai, vị trí đúng biên dung sai còn lại và vượt 1 mili giây, ngay trước, đúng, sau `lease_until` và lúc đóng phần, `positionMs` bằng `duration_ms`, `Range` đầu tệp, cuối tệp và sai định dạng;
  - quyền: tài khoản khác, không đăng nhập, `MANAGE`, phiên bị thu hồi;
  - migration: bảng mới chạy trên DB đã có lượt của AT-1 đến AT-4;
  - đỏ trước: AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7, AC-8, AC-11, AC-13, AC-19.
- Dữ liệu test: bài H-SCA Tiếng Anh, Toán và V-SAT của DS; ba tệp mẫu mới của task này trong `backend/src/test/resources/attempt/audio/` (`silence-30s-cbr.mp3`, `silence-30s-vbr-xing.mp3`, `header-only.mp3`), được `AudioAssetFixture` nạp vào kho trong bộ nhớ với mã tệp mới, `sha256`, kích thước khớp nội dung, không ghi đè tệp có sẵn; tệp nghe 2B `hsca-en-listening-single-01.mp3` (2,088 giây) dùng cho ca đối chiếu; xóa tệp khỏi kho của test để tạo ca thiếu tệp; `MutableClock` điều khiển thời gian; tài khoản riêng cho từng test, dọn dữ liệu sau mỗi test.
- Không kiểm: phát âm thanh thật trên trình duyệt (FS-7); tệp hỏng nhưng vẫn có trong kho.

## AT-7 · Chấm H-SCA, V-SAT, kết quả theo phân đoạn

**Spec:** BS 17.3, 17.5, 17.6; 2B bổ sung H-SCA, V-SAT mục 7, D17, Q-32.

**Sản phẩm:**

- `GradingPolicies.forKind` trả luật nửa điểm cho H-SCA: có phương án sai được 0, đúng nhưng chưa đủ được nửa điểm, đủ và đúng được điểm tối đa. Các kỳ thi khác giữ luật `PARTIAL` theo tỉ lệ. Không đổi `MultipleChoiceRule` của ngân hàng câu hỏi.
- `earned_points` giữ đủ chữ số (0,125), cộng trước, làm tròn một lần ở kết quả cuối.
- Kết quả có `segments[]`: `code`, `name`, điểm đạt và `maxPoints` của từng phân đoạn, đọc `displaySegments` từ bản chụp phiên bản đã công bố.
- Chấm lại và API chấm đề làm thử dùng cùng chính sách theo `exam_kind_code` đã chụp.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/attempt/domain/grading/GradingPolicies.java`, `HalfCreditMultipleChoice.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/attempt/application/AttemptSegmentResultBuilder.java` (mới)
- `backend/src/test/java/vn/unicom/thi360/attempt/grading/**` (mới)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/attempt/application/AttemptResultService.java` (`segments[]`)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`
- thêm vào: `backend/src/test/java/vn/unicom/thi360/attempt/**/PreviewGradingIT.java` (tệp của AT-5)

**Ngoài phạm vi:** Band điểm (LT L5). Phân tích theo MAPID, AI giải thích. Chấm bài viết. Màn kết quả (FS-7).

**Nghiệm thu:**

- AC-1: H-SCA chọn nhiều: có phương án sai được 0; đúng nhưng chưa đủ được nửa điểm, kể cả câu có ba đáp án đúng chọn được hai; đủ và đúng được điểm tối đa; không chọn là `BLANK` — bằng chứng: `HalfCreditMultipleChoiceTest#anyWrongIsZero`, `#incompleteIsHalf`, `#twoOfThreeIsStillHalf`, `#completeIsFull`, `#noChoiceIsBlank` (mới)
- AC-2: Kỳ thi khác H-SCA vẫn chấm luật `PARTIAL` theo tỉ lệ — bằng chứng: `HalfCreditMultipleChoiceTest#otherKindsStayProportional` (mới)
- AC-3: Điểm 0,125 được giữ khi cộng; tổng làm tròn nửa lên một lần ở bước cuối — bằng chứng: `HalfCreditMultipleChoiceTest#eighthPointKeptUntilFinalRounding` (mới)
- AC-4: Lượt H-SCA Toán thật: điểm từng câu, `outcome = PARTIAL` cho câu nửa điểm, tổng trên thang 10 đúng theo bảng ca — bằng chứng: `HscaVsatGradingIT#hscaMathAttemptScores` (mới)
- AC-5: V-SAT theo mẫu thật: Đúng/Sai 0, 1, 2, 3, 6; chọn một 6; ghép hợp 1,5 mỗi cặp; trả lời ngắn 6; cloze 6 mỗi vị trí; Toán tối đa 150, Ngữ văn I–III tối đa 120 — bằng chứng: `HscaVsatGradingIT#vsatRulesMatchPublishedTemplates`, `#vsatLiteratureScaleIs120` (mới)
- AC-6: `segments[]` cộng đúng điểm trong dải `fromPosition`–`toPosition`, mẫu số là `maxPoints`, tổng các phân đoạn bằng tổng bài; chùm bị cắt ở ranh giới tính đúng theo từng vị trí; đề không có phân đoạn thì không có `segments` — bằng chứng: `HscaVsatGradingIT#segmentsSumByRange`, `#splitClusterCountsByPosition`, `#noSegmentsWhenAbsent` (mới)
- AC-7: Đổi mẫu sau khi công bố không đổi `segments[]` của lượt đã làm — bằng chứng: `HscaVsatGradingIT#segmentsReadPublishedSnapshot` (mới)
- AC-8: API chấm đề làm thử của bài H-SCA cho cùng điểm với lượt thật trên cùng câu trả lời — bằng chứng: `HscaVsatGradingIT#trialGradingUsesSamePolicy` (mới)
- AC-9: Chấm lại lượt H-SCA cho cùng kết quả, giao lại `AttemptFinished` không đổi điểm — bằng chứng: `HscaVsatGradingIT#regradeKeepsPolicy` (mới)
- AC-10: Chấm đề làm thử H-SCA chọn đúng hai trong ba đáp án đúng được nửa điểm, dùng `kindCode` của đề sinh thử; đề V-SAT cùng câu vẫn tính theo tỉ lệ — bằng chứng: `PreviewGradingIT#hscaTrialGetsHalfPoint`, `#vsatTrialKeepsProportional` (mới)
- AC-11: Lưu hai câu 0,125 vào PostgreSQL, tải lại, tổng trước làm tròn là 0,25; chấm lại giữ nguyên — bằng chứng: `HscaVsatGradingIT#eighthPointsSurviveDatabaseRoundTrip` (mới)

**Ảnh hưởng ngoài task:**

- `ItemGrader`, `ScoreAggregator` của AT-4 nhận chính sách mới → chạy lại `ItemGraderTest`, `ScoreAggregatorTest`, `AttemptGradingIT` [chủ: task này]
- API chấm đề làm thử của AT-5 dùng `GradingPolicies.forKind` → chạy lại `PreviewGradingIT` [chủ: task này]
- Kết quả có thêm `segments[]` → `AttemptResultIT`, `ForbiddenKeysAssert` đạt; OpenAPI → `OpenApiExportIT`, `pnpm typecheck` [chủ: task này]
- Màn kết quả hiện phân đoạn và "Đúng một phần" → `attempt-hsca-vsat.spec.ts` của FS-7 [chủ: FS-7]
- Đề làm thử của AT-5 chấm theo luật H-SCA mới → thêm ca vào `PreviewGradingIT` của AT-5, chạy cả tệp [chủ: task này]

**Kế hoạch kiểm thử:**

- AC-1: `HalfCreditMultipleChoiceTest#anyWrongIsZero`, `#incompleteIsHalf`, `#twoOfThreeIsStillHalf`, `#completeIsFull`, `#noChoiceIsBlank` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=HalfCreditMultipleChoiceTest`
- AC-2: `HalfCreditMultipleChoiceTest#otherKindsStayProportional` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=HalfCreditMultipleChoiceTest`
- AC-3: `HalfCreditMultipleChoiceTest#eighthPointKeptUntilFinalRounding` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=HalfCreditMultipleChoiceTest`
- AC-4: `HscaVsatGradingIT#hscaMathAttemptScores` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=HscaVsatGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `HscaVsatGradingIT#vsatRulesMatchPublishedTemplates`, `#vsatLiteratureScaleIs120` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=HscaVsatGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `HscaVsatGradingIT#segmentsSumByRange`, `#splitClusterCountsByPosition`, `#noSegmentsWhenAbsent` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=HscaVsatGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `HscaVsatGradingIT#segmentsReadPublishedSnapshot` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=HscaVsatGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-8: `HscaVsatGradingIT#trialGradingUsesSamePolicy` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=HscaVsatGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `HscaVsatGradingIT#regradeKeepsPolicy` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=HscaVsatGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `PreviewGradingIT#hscaTrialGetsHalfPoint`, `#vsatTrialKeepsProportional` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `HscaVsatGradingIT#eighthPointsSurviveDatabaseRoundTrip` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=HscaVsatGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - `cd backend && ./mvnw -B -ntp test -Dtest=ItemGraderTest,ScoreAggregatorTest`
  - `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptGradingIT,AttemptResultIT,PreviewGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `pnpm --filter @thi360/api-client generate && pnpm typecheck`
- Hồi quy (bổ sung ngày 2026-11-19): `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewGradingIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Kiểm thử rủi ro:
  - phép tính lõi: bảng ca đủ tổ hợp đúng, thiếu, thừa của câu 2, 3, 4 đáp án đúng; điểm câu 0,25 và 1;
  - biên: tổng có phần lẻ 0,125 và 0,375 khi làm tròn; bài toàn nửa điểm; bài bỏ trống hết;
  - gửi lại: giao lại sự kiện, chấm lại;
  - đỏ trước: AC-1, AC-3, AC-5, AC-6.
- Dữ liệu test: bài H-SCA Tiếng Anh, Toán và V-SAT của DS; câu trả lời dựng theo bảng ca trong test; đáp án đọc từ bản chụp câu đã giao.
- Không kiểm: giao diện (FS-7); bài viết và phần Nói (ngoài phạm vi BS 18).

**Câu hỏi mở:** BA xác nhận luật nửa điểm H-SCA (2B bổ sung Q-32). Đổi luật chỉ sửa `GradingPolicies` và bảng ca.

## AT-8 · THPTQG chuyển tự do giữa các phần

**Spec:** BS 4; LT 6.3, 7.1, L7, F7, F19; phần bổ sung 2B H23.

**Sản phẩm:**

- Lúc bắt đầu, kỳ thi THPTQG có `navigation_mode = FREE`. Mở lượt `FREE`: mọi phần `OPEN`, cùng hạn `attempt.deadline_at`.
- `finish` của lượt `FREE` trả 409 `PART_FINISH_NOT_ALLOWED`. `submit` gọi được từ mọi phần, không áp `PART_NOT_LAST`.
- Nộp hoặc hết giờ đóng mọi phần trong một transaction theo quy trình đóng chung của LT 6.3.
- `AttemptState` trả `navigationMode` và câu của mọi phần ngay từ đầu; `last_position` giữ phần và câu.

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/attempt/application/AttemptNavigationPolicy.java` (mới)
- `backend/src/test/java/vn/unicom/thi360/attempt/navigation/**` (mới)
- thêm vào: `backend/src/main/java/vn/unicom/thi360/attempt/application/AttemptLifecycleService.java` (nhánh `FREE`), luồng lưu đề của AT-1 (mở mọi phần), `AttemptGuard`
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties` (`PART_FINISH_NOT_ALLOWED`)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** Màn làm bài (FS-5). Chuyển tự do cho kỳ thi khác: các kỳ thi còn lại giữ L7.

**Nghiệm thu:**

- AC-1: Lượt THPTQG ba phần mở ra có mọi phần `OPEN`, cùng `deadline_at`, `AttemptState` có câu của cả ba phần — bằng chứng: `AttemptFreeNavigationIT#allPartsOpenWithSharedDeadline` (mới)
- AC-2: Lưu câu trả lời ở phần trước sau khi đã sang phần sau được nhận; `last_position` trả đúng phần và câu sau khi tải lại — bằng chứng: `AttemptFreeNavigationIT#answersSavedInAnyPart`, `#positionRestored` (mới)
- AC-3: `finish` của lượt `FREE` nhận 409 `PART_FINISH_NOT_ALLOWED`, dữ liệu không đổi — bằng chứng: `AttemptFreeNavigationIT#finishIsRejected` (mới)
- AC-4: `submit` từ phần đầu đóng mọi phần với `ATTEMPT_SUBMITTED`, gọi `confirmUse` một lần, phát một `AttemptFinished` — bằng chứng: `AttemptFreeNavigationIT#submitFromAnyPartClosesAll` (mới)
- AC-5: Hết giờ đóng mọi phần với `closed_at = deadline_at`, `end_reason = TIME_UP`; câu lưu sau hạn nhận `TIME_UP` — bằng chứng: `AttemptFreeNavigationIT#deadlineClosesAllParts`, `#saveAfterDeadlineRejected` (mới)
- AC-6: Nộp trùng lúc hết giờ: một lần đóng, `confirmUse` một lần — bằng chứng: `AttemptFreeNavigationIT#submitRacingDeadlineClosesOnce` (mới)
- AC-7: HSA, QDA, BCA, V-ACT, SP2E, SPT, H-SCA, V-SAT giữ `SEQUENTIAL`, `finish` và `PART_NOT_LAST` như cũ — bằng chứng: `AttemptFreeNavigationIT#otherKindsStaySequential` (mới)
- AC-8: THPTQG Tin học có `preChoice` vẫn `FREE`, đề chỉ có câu của định hướng đã chọn — bằng chứng: `AttemptFreeNavigationIT#informaticsBranchIsFree` (mới)
- AC-9: `submit` kèm `finalAnswers` ở phần đầu và phần cuối ghi đúng theo quy tắc `seq` trước khi đóng, `results` đúng từng câu; yêu cầu sau hạn hoặc từ phiên đã bị thu hồi không ghi thêm; `confirmUse` một lần — bằng chứng: `AttemptFreeNavigationIT#submitFlushesFinalAnswersAcrossParts`, `#lateOrRevokedSubmitWritesNothing` (mới)
- AC-10: Trên SHA đã có AT-6: lượt `FREE` có audio thiếu tệp không mở phần nào và đồng hồ chưa chạy; lượt `FREE` đủ tệp mở mọi phần — bằng chứng: `AttemptFreeNavigationIT#audioCheckRunsBeforeOpeningFreeParts` (mới)

**Ảnh hưởng ngoài task:**

- `AttemptFlowIT#thptqgThreePartsSequential` của AT-2a dùng kỳ thi THPTQG → đổi ca này sang một đề `SEQUENTIAL` khác có ba phần, chạy lại `AttemptFlowIT` [chủ: task này]
- `AttemptGuard`, `AttemptLifecycleService` dùng chung cho mọi kỳ thi → chạy lại `AttemptTimingIT`, `AttemptCloseConcurrencyIT`, `AttemptAnswerIT` [chủ: task này]
- Bài THPTQG theo phần chỉ có một phần → `AttemptFlowIT#singlePartScopesSubmitDirectly` vẫn xanh [chủ: task này]
- Màn làm bài đọc `navigationMode` → `attempt-flow.spec.ts` của FS-5 [chủ: FS-5]
- Tệp chung `messages*.properties` → `MessageBundlesTest`; OpenAPI → `OpenApiExportIT`, `pnpm typecheck` [chủ: task này]
- AT-6 cùng sửa khâu mở lượt, task này merge sau → giữ bước kiểm tệp trước khi mở phần, chạy `AttemptAudioFileCheckIT` cùng `AttemptFreeNavigationIT` trên cùng SHA [chủ: task này]

**Kế hoạch kiểm thử:**

- AC-1: `AttemptFreeNavigationIT#allPartsOpenWithSharedDeadline` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `AttemptFreeNavigationIT#answersSavedInAnyPart`, `#positionRestored` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `AttemptFreeNavigationIT#finishIsRejected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-4: `AttemptFreeNavigationIT#submitFromAnyPartClosesAll` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `AttemptFreeNavigationIT#deadlineClosesAllParts`, `#saveAfterDeadlineRejected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `AttemptFreeNavigationIT#submitRacingDeadlineClosesOnce` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-7: `AttemptFreeNavigationIT#otherKindsStaySequential` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-8: `AttemptFreeNavigationIT#informaticsBranchIsFree` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `AttemptFreeNavigationIT#submitFlushesFinalAnswersAcrossParts`, `#lateOrRevokedSubmitWritesNothing` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `AttemptFreeNavigationIT#audioCheckRunsBeforeOpeningFreeParts` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptFlowIT,AttemptTimingIT,AttemptCloseConcurrencyIT,AttemptAnswerIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `cd backend && ./mvnw -B -ntp test -Dtest=MessageBundlesTest,ModularityTest`
  - `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `pnpm --filter @thi360/api-client generate && pnpm typecheck`
- Hồi quy (bổ sung ngày 2026-11-19): `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptAudioFileCheckIT,AttemptFreeNavigationIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- Kiểm thử rủi ro:
  - đồng thời: nộp đua với worker hạn giờ, hai thiết bị lưu ở hai phần khác nhau;
  - gửi lặp: `submit` lặp cùng khóa, `finish` lặp;
  - biên: lưu đúng hạn và sau hạn 1 mili giây; bài một phần;
  - quyền: tài khoản khác, không đăng nhập, `MANAGE`;
  - đỏ trước: AC-1, AC-3, AC-4, AC-5, AC-7.
- Dữ liệu test: bài ba phần từ mẫu `THPTQG_MATH_2025`, `THPTQG_INFORMATICS_2025` và các bài của DS cho ca giữ `SEQUENTIAL`; `MutableClock`.
- Không kiểm: giao diện (FS-5).

## FS-1 · Trang giới thiệu, danh mục, chi tiết

**Spec:** TM 5.2 (đăng xuất về `/`), 11.1–11.3, C7, C8, Q1, Q2; DESIGN.md; plan B8. **Phụ thuộc mềm:** CM-4b, FS-4 (`e2e/tests/helpers/visual-matrix.ts`).

**Thiết kế:** `docs/design/dot-3/`, canvas trang 10: `TrangChuDot3`, `ChiTietBaiThiBan`, `BaiThiNgungBan`. Trạng thái `hasPendingFulfillment` ở trang chi tiết không có bản vẽ: dùng lại trạng thái thẻ "Đang kiểm tra" của `BaiThiCuaToi` (D-k). Trang 10 chỉ vẽ nền sáng: chế độ tối kiểm theo token `DESIGN.md`.

**Sản phẩm:**

- Thay trang `/` bằng 6 khối (TM 11.2); danh mục ở `#exams`, bộ lọc trên URL. Trang vẫn đọc cookie giao diện và ngôn ngữ; `revalidate: 60` đặt ở lời gọi API danh mục.
- `/exams/[code]` dựng phía máy chủ, có `metadata` riêng từng sản phẩm; 404 và trang "Bài thi đã ngừng bán" khi API trả 410.
- Nút theo `exam-access` (bảng TM 11.3); `hasPendingFulfillment` ở trang chi tiết (D-k). Thẻ bài ôn có nhãn "Bài ôn" theo `build_mode` (BS 2).
- Hàm đọc phiên tùy chọn cho trang công khai: khách nhận `null`, không kích hoạt luồng 401 về `/login` của `query-provider.tsx`.
- `proxy.ts` theo B8, đủ cho cả đợt: công khai `/`, tiền tố `/exams/`, `/register`, `/forgot-password`, `/reset-password`, `/checkout/result`; đã đăng nhập mà mở `/register` thì về `/home`.
- Thanh đầu trang theo B8, đủ cho cả đợt: Bài thi của tôi (`/home`), Lịch sử (`/history`), Hồ sơ (`/account/profile`), Đơn của tôi (`/account/orders`). Mục trỏ tới trang chưa có nằm trong danh sách ẩn ở cùng tệp; FS-2, FS-3, FS-6 mỗi lane bỏ một dòng ẩn.
- Đăng xuất từ menu tài khoản đưa về `/` (TM 5.2).
- Câu hỏi thường gặp và hỗ trợ trong file dịch, `NEXT_PUBLIC_SUPPORT_PHONE`, `NEXT_PUBLIC_SUPPORT_EMAIL`, `NEXT_PUBLIC_SUPPORT_HOURS`.

**Đường dẫn sở hữu:**

- `apps/web-student/src/proxy.ts`, `apps/web-student/src/proxy.test.ts`
- `apps/web-student/src/components/student-header.tsx`, `apps/web-student/src/components/student-header.test.tsx` (mới)
- `apps/web-student/src/app/page.tsx`, `apps/web-student/src/app/page.test.tsx`
- `apps/web-student/src/app/exams/[code]/**` (mới)
- `apps/web-student/src/app/metadata.test.ts` (tiêu đề mặc định)
- `apps/web-student/src/features/storefront/**` (mới)
- `apps/web-student/src/features/home/student-user-menu.tsx`, `apps/web-student/src/features/home/student-user-menu.test.tsx` (mới)
- thêm vào: `apps/web-student/src/features/session/api.ts` (hàm đọc phiên tùy chọn)
- thêm vào: `apps/web-student/src/messages/{vi,en}.json` (khối `landing` viết lại, `storefront`, `header`, `metadata`)
- thêm vào: `.env.example` (`NEXT_PUBLIC_SUPPORT_*`)
- `e2e/tests/storefront.spec.ts` (mới), `e2e/tests/storefront-visual.spec.ts` (mới)

**Ngoài phạm vi:** trang `/register`, `/forgot-password`, `/reset-password` và `Referrer-Policy` (FS-2). `/checkout/**`, `/home`, `/account/orders` (FS-3). `/history` (FS-6). Thẻ bộ đề và chi tiết bộ (FB). API danh mục (CM-1), `exam-access` (CM-4b). Đăng xuất ở web admin và nút đăng xuất của `forbidden-view.tsx`, `change-password-view.tsx` giữ nguyên.

**Nghiệm thu:**

- AC-1: Khách xem danh mục thật (dữ liệu DS): thẻ có ảnh, tên, chip kỳ thi, chip hình thức, "n câu · m phút · k lượt", giá; có khuyến mãi thì giá gốc gạch ngang, chip mức giảm, "Kết thúc dd/MM" — bằng chứng: `e2e/tests/storefront.spec.ts` (mới), `product-card.test.tsx` (mới).
- AC-2: Tìm theo tên hoặc mã, lọc Kỳ thi, Hình thức, Phần/môn, sắp xếp, Xóa bộ lọc: điều kiện nằm trên URL, còn sau khi tải lại; đổi điều kiện về trang 1 — bằng chứng: `storefront.spec.ts` (mới).
- AC-3: Chuyển trang giữ điều kiện; bấm thẻ mở đúng `/exams/<code>` — bằng chứng: `storefront.spec.ts` (mới).
- AC-4: Khách bấm Mua thì sang `/login?callbackUrl=/checkout/<code>?v=…&p=…`; đăng nhập xong về đúng đường dẫn đó, còn `v`, `p` — bằng chứng: `storefront.spec.ts` (mới).
- AC-5: Không hiện nút Mua trước khi biết quyền: đang tải thì giữ chỗ cố định; lỗi tải quyền thì có "Tải lại quyền" — bằng chứng: `access-action.test.tsx` (mới).
- AC-6: Nút đúng cả 8 tình huống của bảng TM 11.3, gồm `CAN_START` là "Vào bài thi" tới `/prepare/<entitlementId>` kèm "Mua thêm lượt", `CHECKING` không bấm được, quản trị viên (API trả 403) không có nút chính — bằng chứng: `access-action.test.tsx` (mới); khách, `NONE`, quản trị viên có thêm `storefront.spec.ts` (mới).
- AC-7: `CAN_START` kèm `hasPendingFulfillment` ở trang chi tiết hiện trạng thái đang kiểm tra kèm `pendingOrderCode`, không đề nghị thanh toán lại — bằng chứng: `access-action.test.tsx` (mới).
- AC-8: Mã ngừng bán hiện "Bài thi đã ngừng bán" kèm nút về danh mục; nút "Bài thi của tôi" chỉ có khi đã đăng nhập — bằng chứng: `storefront.spec.ts` (mới), `exam-detail-view.test.tsx` (mới).
- AC-9: Mã không có trả trang 404 "Không tìm thấy bài thi" kèm nút về danh mục, mã trạng thái HTTP là 404 — bằng chứng: `storefront.spec.ts` (mới).
- AC-10: Chưa có sản phẩm: "Hiện chưa có bài thi nào đang mở bán". Không có kết quả: "Không tìm thấy bài thi phù hợp" kèm Xóa bộ lọc. Lỗi tải: nút Thử lại, giữ bộ lọc. Đang tải: khung thẻ kích thước cố định — bằng chứng: `catalog-section.test.tsx` (mới).
- AC-11: Khách mở `/` và `/exams/<code>` không bị đẩy về `/login` dù `/api/v1/me` trả 401, và không có lời gọi `exam-access` — bằng chứng: `optional-session.test.ts` (mới), `storefront.spec.ts` (mới).
- AC-12: `proxy.ts` cho qua không cần phiên `/`, `/exams/<code>`, `/forgot-password`, `/reset-password`, `/checkout/result`; `/exams`, `/examsx`, `/checkout/<code>`, `/verify-email`, `/home` cần phiên — bằng chứng: `proxy.test.ts`.
- AC-13: Khách mở `/register` được đi tiếp; đã đăng nhập mở `/register` thì về `/home` — bằng chứng: `proxy.test.ts`.
- AC-14: Khách thấy Đăng nhập, Đăng ký; đã đăng nhập thấy mục Bài thi của tôi và menu tài khoản; ba mục trong danh sách ẩn không hiện; nút đổi giao diện và ngôn ngữ còn ở cả hai trạng thái — bằng chứng: `student-header.test.tsx` (mới).
- AC-15: Trang `/` đủ 6 khối, đủ 6 câu hỏi thường gặp; không còn chữ chỉ nói HSA và QDA, không có nội dung về thời hạn sử dụng (C8) — bằng chứng: `page.test.tsx`.
- AC-16: Có `NEXT_PUBLIC_SUPPORT_*` thì hiện kênh liên hệ; biến trống thì hiện "Thông tin liên hệ đang được cập nhật" — bằng chứng: `support-section.test.tsx` (mới).
- AC-17: HTML máy chủ của `/exams/<code>` đã có tên sản phẩm, và tiêu đề trang là tên sản phẩm — bằng chứng: `storefront.spec.ts` (mới).
- AC-18: Bài ôn đang bán có nhãn "Bài ôn" trên thẻ và trang chi tiết — bằng chứng: `product-card.test.tsx` (mới), `storefront.spec.ts` (mới).
- AC-19: Đăng xuất từ menu tài khoản về `/` — bằng chứng: `student-user-menu.test.tsx` (mới).
- AC-20: `/`, `/exams/<code>`, trang ngừng bán đúng khi sáng và tối, vi và en, 390px và 1280px: không tràn ngang, tương phản ≥ 4,5, không lẫn ngôn ngữ — bằng chứng: `e2e/tests/storefront-visual.spec.ts` (mới).

**Ảnh hưởng ngoài task:**

- Ca công khai và chuyển hướng hiện có của `proxy.ts` (`apps/web-student/src/proxy.test.ts`: `/`, `/login`, `/home?tab=1`, matcher, không gọi backend dù có `thi360_rt`) → giữ mọi ca cũ, `pnpm --filter @thi360/web-student test src/proxy.test.ts` [chủ: task này]
- Luồng 401 toàn cục đẩy khách về `/login` (`apps/web-student/src/providers/query-provider.tsx`, `query-provider.test.tsx`) → không sửa tệp; `pnpm --filter @thi360/web-student test src/providers/query-provider.test.tsx`, `optional-session.test.ts` (mới) [chủ: task này]
- Trang `/` phải đọc cookie và còn nút đổi giao diện (`e2e/tests/preferences.spec.ts:152-174`, `:258`) → `pnpm --filter @thi360/e2e test tests/preferences.spec.ts` [chủ: task này]
- Kiểm cấu trúc trang cũ (`apps/web-student/src/app/page.test.tsx`: một `<main>`, h1, h2 "Thi 360 giúp bạn điều gì") → viết lại theo 6 khối, nêu lý do trong PR [chủ: task này]
- Tiêu đề mặc định còn chữ HSA, QDA (`apps/web-student/src/app/metadata.test.ts:24`, khóa `metadata.title`, `metadata.description`) → đổi câu chữ, `pnpm --filter @thi360/web-student test src/app/metadata.test.ts` [chủ: task này]
- Thanh đầu trang dùng ở trang học viên (`apps/web-student/src/features/home/student-home-view.tsx`, `student-home-view.test.tsx`: thứ tự giao diện, ngôn ngữ, menu; không có menuitem "Hồ sơ") → `pnpm --filter @thi360/web-student test src/features/home/student-home-view.test.tsx` [chủ: task này]
- Khóa dịch phải khớp vi và en (`apps/web-student/src/messages/messages.test.ts`, `source-scan.test.ts`) → `pnpm --filter @thi360/web-student test src/messages` [chủ: task này]
- `callbackUrl` có chuỗi truy vấn đi qua web và máy chủ (`packages/web-kit/src/redirect.ts`, `backend/src/main/java/vn/unicom/thi360/identity/application/RedirectResolver.java`) → AC-4 trong `storefront.spec.ts` (mới) [chủ: task này]
- Đăng nhập, phiên và chuyển hướng theo quyền (`e2e/tests/sso.spec.ts`, `e2e/tests/session-refresh.spec.ts`) → `pnpm --filter @thi360/e2e test tests/sso.spec.ts tests/session-refresh.spec.ts` [chủ: task này]
- Lời gọi danh mục từ máy chủ web đi chung một IP, chịu giới hạn 120 yêu cầu mỗi phút (`thi360.security.rate-limit.exempt-ips`, plan B6) → test hai trường hợp ở CM-1 [chủ: CM-1]
- Hợp đồng API danh mục và `exam-access` (`packages/api-client/src/schema.d.ts`) → lane web không sinh lại; `pnpm typecheck` sau khi rebase [chủ: CM-1, CM-4b]
- Dòng ẩn Hồ sơ và trang `/register` (`apps/web-student/src/components/student-header.tsx`) → FS-2 bỏ dòng ẩn, chạy `student-header.test.tsx` (mới) và `student-home-view.test.tsx` [chủ: FS-2]
- Dòng ẩn Đơn của tôi → FS-3 bỏ dòng ẩn, chạy `student-header.test.tsx` (mới) [chủ: FS-3]
- Dòng ẩn Lịch sử → FS-6 bỏ dòng ẩn, chạy `student-header.test.tsx` (mới) [chủ: FS-6]
- Thẻ và trang chi tiết được mở rộng cho bộ đề (`apps/web-student/src/features/storefront/**`) → FB chạy lại `storefront.spec.ts` (mới) [chủ: FB]
- Trợ thủ kiểm giao diện (`e2e/tests/helpers/visual-matrix.ts`, do FS-4 tạo) → FS-1 chỉ gọi; FS-4 phải merge trước khi FS-1 nộp [chủ: FS-4]
- Giới hạn dung lượng bundle: không có: đã tìm `size-limit|bundlesize|bundle-analyzer|budget` trong `.github/workflows`, `scripts`, `package.json`, `apps/web-student/package.json`, `apps/web-student/next.config.ts` → `pnpm --filter @thi360/web-student build` vẫn qua

**Kế hoạch kiểm thử:**

- AC-1: `storefront.spec.ts` ca danh mục, E2E, `pnpm --filter @thi360/e2e test tests/storefront.spec.ts`; `product-card.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/storefront/product-card.test.tsx`
- AC-2: `storefront.spec.ts` ca bộ lọc, E2E, `pnpm --filter @thi360/e2e test tests/storefront.spec.ts`
- AC-3: `storefront.spec.ts` ca phân trang, E2E, `pnpm --filter @thi360/e2e test tests/storefront.spec.ts`
- AC-4: `storefront.spec.ts` ca mua khi là khách, E2E, `pnpm --filter @thi360/e2e test tests/storefront.spec.ts`
- AC-5: `access-action.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/storefront/access-action.test.tsx`
- AC-6: `access-action.test.tsx` bảng 8 tình huống, unit, `pnpm --filter @thi360/web-student test src/features/storefront/access-action.test.tsx`; `storefront.spec.ts` ca khách, `NONE`, quản trị viên, E2E, `pnpm --filter @thi360/e2e test tests/storefront.spec.ts`
- AC-7: `access-action.test.tsx` ca `hasPendingFulfillment`, unit, `pnpm --filter @thi360/web-student test src/features/storefront/access-action.test.tsx`
- AC-8: `storefront.spec.ts` ca ngừng bán, E2E, `pnpm --filter @thi360/e2e test tests/storefront.spec.ts`; `exam-detail-view.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/storefront/exam-detail-view.test.tsx`
- AC-9: `storefront.spec.ts` ca 404 kiểm mã HTTP, E2E, `pnpm --filter @thi360/e2e test tests/storefront.spec.ts`
- AC-10: `catalog-section.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/storefront/catalog-section.test.tsx`
- AC-11: `optional-session.test.ts`, unit, `pnpm --filter @thi360/web-student test src/features/storefront/optional-session.test.ts`; `storefront.spec.ts` ca khách, E2E, `pnpm --filter @thi360/e2e test tests/storefront.spec.ts`
- AC-12: `proxy.test.ts` ca đường công khai và tiền tố, unit, `pnpm --filter @thi360/web-student test src/proxy.test.ts`
- AC-13: `proxy.test.ts` ca `/register`, unit, `pnpm --filter @thi360/web-student test src/proxy.test.ts`
- AC-14: `student-header.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/components/student-header.test.tsx`
- AC-15: `page.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/app/page.test.tsx`
- AC-16: `support-section.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/storefront/support-section.test.tsx`
- AC-17: `storefront.spec.ts` ca HTML máy chủ và tiêu đề, E2E, `pnpm --filter @thi360/e2e test tests/storefront.spec.ts`
- AC-18: `product-card.test.tsx` ca bài ôn, unit, `pnpm --filter @thi360/web-student test src/features/storefront/product-card.test.tsx`; `storefront.spec.ts` ca bài ôn của DS, E2E, `pnpm --filter @thi360/e2e test tests/storefront.spec.ts`
- AC-19: `student-user-menu.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/home/student-user-menu.test.tsx`
- AC-20: `storefront-visual.spec.ts`, giao diện, `pnpm --filter @thi360/e2e test tests/storefront-visual.spec.ts`
- Hồi quy: `pnpm lint && pnpm typecheck && pnpm test && pnpm build`; `pnpm --filter @thi360/web-student test src/proxy.test.ts src/providers/query-provider.test.tsx src/app/metadata.test.ts src/features/home/student-home-view.test.tsx src/messages`; `pnpm --filter @thi360/e2e test tests/preferences.spec.ts tests/sso.spec.ts tests/session-refresh.spec.ts`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: sản phẩm lấy từ tệp hằng số của DS (gồm bài đã ngừng bán và bài ôn); tài khoản `student01`, `ops.admin` của dữ liệu dev; trạng thái rỗng, lỗi và 8 tình huống nút dùng API giả trong vitest. Chỉ đọc danh mục nên các lane song song không ảnh hưởng nhau.
- Không kiểm: mã HTTP 410 của tài liệu HTML: App Router không trả được 410 từ trang, nên trang ngừng bán có mã HTTP 200 và chỉ kiểm giao diện; mã 410 của API do CM-1 kiểm. Trạng thái `CAN_START`, `IN_PROGRESS`, `EXHAUSTED`, `CHECKING` không kiểm E2E ở task này vì FS-1 không phụ thuộc đơn hàng (CM-3a): kiểm bằng vitest, luồng thật do FS-3 và QA kiểm. Hiệu lực của `revalidate: 60` không đo.

**Câu hỏi mở:** TM 5.2 ghi "Đăng xuất đưa về `/`" mà không nói nút đăng xuất ở trang `/forbidden` và trang đổi mật khẩu: task giữ nguyên hai nút này; M.Trường quyết nếu muốn đổi.

## FS-2 · Đăng ký, xác minh, quên mật khẩu, hồ sơ

**Spec:** TM 5, 11.1, 11.3 (gợi ý tên đăng nhập, AC-06.02); plan B8. **Phụ thuộc mềm:** FS-1.

**Thiết kế:** `docs/design/dot-3/`, canvas trang 10: `DangKy`, `XacMinhEmail`, `QuenMatKhau`, `DatLaiMatKhau`, `HoSo`; đăng nhập theo `docs/design/dot-1/Main.dc.html`.

**Sản phẩm:**

- `/register`, `/verify-email`, `/forgot-password`, `/reset-password`, `/account/profile` (ảnh, họ tên, email, số điện thoại).
- Liên kết "Đăng ký", "Quên mật khẩu" trên `/login`, thay dòng ghi chú liên hệ quản trị viên.
- Mọi lối đi đều giữ `callbackUrl`.
- `/register` hiện quy tắc tên đăng nhập dưới ô nhập (TM 11.3).
- Đăng xuất ở web học viên đưa về `/` (TM 5.2).
- `/reset-password` trả header `Referrer-Policy: no-referrer` (TM 5.4).
- Bỏ dòng ẩn của các mục này ở thanh đầu trang (B8).

**Đường dẫn sở hữu:**

- `apps/web-student/src/app/register/**` (mới), `apps/web-student/src/app/verify-email/**` (mới), `apps/web-student/src/app/forgot-password/**` (mới), `apps/web-student/src/app/reset-password/**` (mới), `apps/web-student/src/app/account/profile/**` (mới)
- `apps/web-student/src/features/account/**` (mới)
- `apps/web-student/src/features/auth/login-view.tsx`, `apps/web-student/src/features/auth/login-view.test.tsx`
- `apps/web-student/src/features/home/student-user-menu.tsx`, `apps/web-student/src/features/home/student-user-menu.test.tsx` (mới)
- `apps/web-student/next.config.ts` (thêm `headers()` cho `/reset-password`)
- `e2e/tests/account-*.spec.ts` (mới), `e2e/tests/helpers/mail.ts` (mới)
- thêm vào: `apps/web-student/src/messages/vi.json`, `apps/web-student/src/messages/en.json` (khối riêng, sắp theo khóa; thay khóa `forgotNote`)
- thêm vào: `apps/web-student/src/components/student-header.tsx` (chỉ bỏ dòng ẩn của mục Hồ sơ trong danh sách FS-1 tạo, B8)
- thêm vào: `e2e/playwright.config.ts`, `.github/workflows/e2e.yml` (biến nới ngưỡng E19 cho bộ E2E, cạnh biến `THI360_SECURITY_RATELIMIT_LOGINPERIPLIMIT`)
- thêm vào: `DESIGN.md` (thành phần giao diện mới, nếu có)

**Ngoài phạm vi:** `apps/web-student/src/proxy.ts` và cấu trúc thanh đầu trang, gồm đường công khai, yêu cầu phiên của `/verify-email`, chuyển `/register` về `/home` khi đã đăng nhập (FS-1, B8); API đăng ký, xác minh, đổi email (ID-1); API quên mật khẩu, hồ sơ, ảnh đại diện và giới hạn E16 (ID-2); `GET /api/v1/test/mail` (ML); khối `on-profile: e2e` trong `application.yml` và việc chạy E2E với profile này (C-0b); `/account/orders` (FS-3); đăng xuất của web admin giữ nguyên.

**Nghiệm thu:**

- AC-1: E2E đăng ký → mã OTP (đọc qua `/api/v1/test/mail`) → quay đúng `callbackUrl` — bằng chứng: `e2e/tests/account-register.spec.ts` (mới).
- AC-2: E2E quên → đặt lại → đăng nhập: sau khi đặt lại về `/login` có thông báo thành công, không tự đăng nhập, mật khẩu mới đăng nhập được — bằng chứng: `e2e/tests/account-password-reset.spec.ts` (mới).
- AC-3: E2E đổi email: nhập email mới và mật khẩu → mã OTP gửi tới email mới → hồ sơ hiện email mới "Đã xác minh" — bằng chứng: `e2e/tests/account-email-change.spec.ts` (mới).
- AC-4: OTP sai hiện số lần còn lại theo `remainingAttempts` — bằng chứng: `verify-email-view.test.tsx` (mới).
- AC-5: Ảnh quá lớn hiện lỗi rõ, ảnh đại diện hiện tại giữ nguyên — bằng chứng: `profile-view.test.tsx` (mới).
- AC-6: `/login` có liên kết "Đăng ký", "Quên mật khẩu" mang theo `callbackUrl`; dòng ghi chú liên hệ quản trị viên không còn — bằng chứng: `login-view.test.tsx`.
- AC-7: Chuyển qua lại giữa `/login`, `/register`, `/forgot-password` giữ `callbackUrl`; nút "Để sau" ở `/verify-email` đi theo `callbackUrl`; `callbackUrl` trỏ ra ngoài không được dùng — bằng chứng: `account-links.test.tsx` (mới), `e2e/tests/account-register.spec.ts` (mới).
- AC-8: `/register` hiện quy tắc tên đăng nhập dưới ô nhập ở cả `vi` và `en` — bằng chứng: `register-view.test.tsx` (mới).
- AC-9: Đã đăng nhập mở `/register` thì về `/home` — bằng chứng: `e2e/tests/account-register.spec.ts` (mới).
- AC-10: Đăng xuất ở web học viên về `/`, phiên đã hết — bằng chứng: `student-user-menu.test.tsx` (mới), `e2e/tests/account-register.spec.ts` (mới).
- AC-11: Phản hồi của `/reset-password` có header `Referrer-Policy: no-referrer` — bằng chứng: `e2e/tests/account-password-reset.spec.ts` (mới).
- AC-12: `/register` hiện đúng lỗi cho tên đăng nhập trùng, email trùng, chưa chấp nhận điều khoản, quá giới hạn đăng ký; điều khoản mở trong hộp thoại — bằng chứng: `register-view.test.tsx` (mới).
- AC-13: `/verify-email` hiện đếm ngược gửi lại theo `retryAfter`, mã đã mất hiệu lực, hết lượt gửi trong ngày, xác minh thành công — bằng chứng: `verify-email-view.test.tsx` (mới).
- AC-14: `/forgot-password` hiện cùng một nội dung sau khi gửi với mọi email; email sai định dạng báo lỗi tại ô nhập, không gọi API — bằng chứng: `forgot-password-view.test.tsx` (mới).
- AC-15: `/reset-password` hiện quy tắc mật khẩu, lỗi hai mật khẩu không khớp, trang liên kết hết hiệu lực khi nhận `RESET_TOKEN_INVALID` kèm lối gửi yêu cầu mới — bằng chứng: `reset-password-view.test.tsx` (mới).
- AC-16: `/account/profile`: tên đăng nhập chỉ đọc; lưu họ tên, số điện thoại báo thành công và dữ liệu còn sau khi tải lại; sai quy tắc báo lỗi tại ô nhập — bằng chứng: `profile-view.test.tsx` (mới), `e2e/tests/account-profile.spec.ts` (mới).
- AC-17: Hồ sơ hiện "Đã xác minh" hoặc "Chưa xác minh" kèm nút xác minh (E18), khối email đang chờ, lỗi email trùng, lỗi hệ thống khi xác nhận (503) — bằng chứng: `profile-view.test.tsx` (mới).
- AC-18: Tải lên và xóa ảnh đại diện trên trang hồ sơ: ảnh mới hiện sau khi tải lại, xóa xong về ảnh mặc định — bằng chứng: `e2e/tests/account-profile.spec.ts` (mới).
- AC-19: Thanh đầu trang hiện mục Hồ sơ trỏ tới `/account/profile` — bằng chứng: `e2e/tests/account-profile.spec.ts` (mới).
- AC-20: Chưa đăng nhập mở được `/register`, `/forgot-password`, `/reset-password`; mở `/verify-email` hoặc `/account/profile` thì sang `/login` có `callbackUrl` — bằng chứng: `e2e/tests/account-register.spec.ts` (mới).
- AC-21: Năm màn đủ trạng thái đang tải, lỗi, có dữ liệu, và trạng thái rỗng của hồ sơ (chưa có ảnh, chưa có số điện thoại); chỉ dùng token của `packages/ui`; đúng ở sáng và tối, `vi` và `en`, 390px và 1280px, không cuộn ngang — bằng chứng: `e2e/tests/account-visual.spec.ts` (mới).

**Ảnh hưởng ngoài task:**

- Trang đăng nhập đổi dòng ghi chú (`apps/web-student/src/features/auth/login-view.tsx:192`, khóa `forgotNote` ở `apps/web-student/src/messages/vi.json:111`, `apps/web-student/src/messages/en.json:111`) → sửa và chạy `login-view.test.tsx`, `messages.test.ts` [chủ: task này]
- Đăng xuất của học viên đổi đích (`apps/web-student/src/features/home/student-user-menu.tsx:45`) → chạy `student-home-view.test.tsx`, test mới của AC-10 [chủ: task này]
- Hai lối "đổi tài khoản" vẫn về `/login` (`apps/web-student/src/features/auth/change-password-view.tsx:212`, `apps/web-student/src/features/session/forbidden-view.tsx:33`) → chạy `change-password-view.test.tsx`, `forbidden-view.test.tsx` [chủ: task này]
- Đăng xuất của web admin vẫn về `/login` của web học viên (`e2e/tests/sso.spec.ts:75`, `e2e/tests/rbac.spec.ts:21`) → chạy hai spec này [chủ: task này]
- Các spec đăng nhập qua form `/login` (`e2e/tests/session-refresh.spec.ts`, `e2e/tests/preferences.spec.ts`) → chạy hai spec này [chủ: task này]
- Header của web học viên (`apps/web-student/next.config.ts`) → `pnpm --filter @thi360/web-student build` đạt; E2E của AC-11 chạy trên bản build [chủ: task này]
- Thanh đầu trang dùng chung (`apps/web-student/src/components/student-header.tsx`, dùng ở `apps/web-student/src/app/page.tsx`, `apps/web-student/src/features/home/student-home-view.tsx`) → chạy `page.test.tsx`, `student-home-view.test.tsx` [chủ: task này]
- Tệp dịch (`apps/web-student/src/messages/vi.json`, `apps/web-student/src/messages/en.json`) → chạy `messages.test.ts`, `source-scan.test.ts` [chủ: task này]
- Proxy API coi `register`, `forgot`, `reset` là endpoint thường, có thể làm mới phiên trước khi gửi (`SESSION_ENDPOINTS`, `packages/web-kit/src/proxy.ts:90`) → không sửa; chạy `proxy.test.ts` của `@thi360/web-kit`; kịch bản trong `account-register.spec.ts` (mới): trình duyệt còn `thi360_rt` hỏng vẫn đăng ký được và nhận phiên mới [chủ: task này]
- Kiểm tra `callbackUrl` dùng chung (`validateCallbackUrl`, `packages/web-kit/src/redirect.ts`) → không sửa; chạy toàn bộ test của `@thi360/web-kit` [chủ: task này]
- Kiểu dữ liệu API sinh sẵn (`packages/api-client/src/schema.d.ts`) → không tự sinh (B9); `pnpm typecheck` [chủ: task này]
- Ngưỡng E19 có thể chặn bộ E2E chạy lại trong một giờ (`e2e/playwright.config.ts:72`, `.github/workflows/e2e.yml:71`) → thêm biến nới ngưỡng, chạy `account-register.spec.ts` hai lần liên tiếp đều xanh [chủ: task này]
- Đường công khai, phiên của `/verify-email`, chuyển `/register` về `/home` (`apps/web-student/src/proxy.ts`) → `proxy.test.ts` của FS-1; AC-9, AC-20 chạy lại sau khi FS-1 merge [chủ: FS-1]
- Mục Hồ sơ nằm trong danh sách ẩn của thanh đầu trang (`apps/web-student/src/components/student-header.tsx`) → FS-1 tạo đủ mục và danh sách ẩn [chủ: FS-1]
- E2E hiện chạy backend không có profile `e2e` (`.github/workflows/e2e.yml:66`, `e2e/playwright.config.ts:5`) → đổi lệnh chạy để bật profile `e2e`; `GET /api/v1/test/mail` trả 200 trong bộ E2E [chủ: C-0b]
- Đọc mã OTP và liên kết đặt lại trong thư (`GET /api/v1/test/mail`) → test của ML: trả thư mới nhất theo `to`, `kind` [chủ: ML]
- Hợp đồng đăng ký, OTP, đổi email (`POST /api/v1/auth/register`, `/api/v1/me/email/**`, `retryAfter`, `remainingAttempts`) → test của ID-1 [chủ: ID-1]
- Hợp đồng quên mật khẩu, hồ sơ, ảnh đại diện (`/api/v1/auth/password/**`, `/api/v1/me/profile`, `/api/v1/me/avatar`) → test của ID-2 [chủ: ID-2]
- Luồng mua sau khi đăng ký quay lại `/checkout/<code>` (AC-06.02) → E2E mua hàng của FS-3 [chủ: FS-3]

**Kế hoạch kiểm thử:**

- AC-1: `account-register.spec.ts` "đăng ký, nhập OTP, quay lại callbackUrl", E2E, `pnpm --filter @thi360/e2e test -- tests/account-register.spec.ts`
- AC-2: `account-password-reset.spec.ts` "quên, đặt lại, đăng nhập", E2E, `pnpm --filter @thi360/e2e test -- tests/account-password-reset.spec.ts`
- AC-3: `account-email-change.spec.ts` "đổi email bằng OTP", E2E, `pnpm --filter @thi360/e2e test -- tests/account-email-change.spec.ts`
- AC-4: `verify-email-view.test.tsx` "hiện số lần còn lại", giao diện, `pnpm --filter @thi360/web-student test -- src/features/account/verify-email-view.test.tsx`
- AC-5: `profile-view.test.tsx` "ảnh quá lớn", giao diện, `pnpm --filter @thi360/web-student test -- src/features/account/profile-view.test.tsx`
- AC-6: `login-view.test.tsx` "liên kết mang callbackUrl", giao diện, `pnpm --filter @thi360/web-student test -- src/features/auth/login-view.test.tsx`
- AC-7: `account-links.test.tsx`, giao diện, `pnpm --filter @thi360/web-student test -- src/features/account/account-links.test.tsx`; `account-register.spec.ts` "giữ callbackUrl qua các trang", E2E, `pnpm --filter @thi360/e2e test -- tests/account-register.spec.ts`
- AC-8: `register-view.test.tsx` "quy tắc tên đăng nhập", giao diện, `pnpm --filter @thi360/web-student test -- src/features/account/register-view.test.tsx`
- AC-9: `account-register.spec.ts` "đã đăng nhập mở /register về /home", E2E, `pnpm --filter @thi360/e2e test -- tests/account-register.spec.ts`
- AC-10: `student-user-menu.test.tsx`, giao diện, `pnpm --filter @thi360/web-student test -- src/features/home/student-user-menu.test.tsx`; `account-register.spec.ts` "đăng xuất về trang chủ", E2E, `pnpm --filter @thi360/e2e test -- tests/account-register.spec.ts`
- AC-11: `account-password-reset.spec.ts` "header Referrer-Policy" đọc header của phản hồi điều hướng, E2E, `pnpm --filter @thi360/e2e test -- tests/account-password-reset.spec.ts`
- AC-12: `register-view.test.tsx` "lỗi đăng ký", giao diện, `pnpm --filter @thi360/web-student test -- src/features/account/register-view.test.tsx`
- AC-13: `verify-email-view.test.tsx` "các trạng thái mã", giao diện, `pnpm --filter @thi360/web-student test -- src/features/account/verify-email-view.test.tsx`
- AC-14: `forgot-password-view.test.tsx`, giao diện, `pnpm --filter @thi360/web-student test -- src/features/account/forgot-password-view.test.tsx`
- AC-15: `reset-password-view.test.tsx`, giao diện, `pnpm --filter @thi360/web-student test -- src/features/account/reset-password-view.test.tsx`
- AC-16: `profile-view.test.tsx` "lỗi tại ô nhập", giao diện, `pnpm --filter @thi360/web-student test -- src/features/account/profile-view.test.tsx`; `account-profile.spec.ts` "lưu hồ sơ còn sau khi tải lại", E2E, `pnpm --filter @thi360/e2e test -- tests/account-profile.spec.ts`
- AC-17: `profile-view.test.tsx` "trạng thái email", giao diện, `pnpm --filter @thi360/web-student test -- src/features/account/profile-view.test.tsx`
- AC-18: `account-profile.spec.ts` "tải lên và xóa ảnh", E2E, `pnpm --filter @thi360/e2e test -- tests/account-profile.spec.ts`
- AC-19: `account-profile.spec.ts` "mục Hồ sơ trên thanh đầu trang", E2E, `pnpm --filter @thi360/e2e test -- tests/account-profile.spec.ts`
- AC-20: `account-register.spec.ts` "đường công khai và đường cần phiên", E2E, `pnpm --filter @thi360/e2e test -- tests/account-register.spec.ts`
- AC-21: `account-visual.spec.ts` chụp bằng `page.screenshot` đính vào báo cáo (repo chưa có ảnh chuẩn `toHaveScreenshot`), kiểm không cuộn ngang ở từng tổ hợp, giao diện, `pnpm --filter @thi360/e2e test -- tests/account-visual.spec.ts`
- Hồi quy:
  - Web học viên: `pnpm --filter @thi360/web-student test -- src/features/auth/login-view.test.tsx src/features/auth/change-password-view.test.tsx src/features/session/forbidden-view.test.tsx src/features/home/student-home-view.test.tsx src/app/page.test.tsx src/messages/messages.test.ts src/messages/source-scan.test.ts src/proxy.test.ts`
  - `@thi360/web-kit`: `pnpm --filter @thi360/web-kit test`
  - E2E có sẵn: `pnpm --filter @thi360/e2e test -- tests/sso.spec.ts tests/rbac.spec.ts tests/session-refresh.spec.ts tests/preferences.spec.ts`
  - Ngưỡng E19 trong bộ E2E: chạy `pnpm --filter @thi360/e2e test -- tests/account-register.spec.ts --repeat-each=2`
  - Bản build và kiểu: `pnpm lint && pnpm typecheck && pnpm build`
  - Dòng có chủ là FS-1, C-0b, ML, ID-1, ID-2, FS-3 do task đó chạy.
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: tài khoản E2E tạo mới trong từng test với tên và email có hậu tố ngẫu nhiên; mã OTP và liên kết đặt lại đọc qua `e2e/tests/helpers/mail.ts` (mới) từ `GET /api/v1/test/mail`; `callbackUrl` của AC-1 trỏ tới `/exams/<mã bài mẫu của DS>`; test giao diện giả lập API bằng `apps/web-student/src/test/render.tsx`; lane web dùng bộ dịch vụ riêng của lane khi chạy E2E, Playwright chạy một worker.
- Không kiểm: giới hạn E16, E19 phía máy chủ (ID-1, ID-2 kiểm; web chỉ kiểm cách hiện lỗi); gửi SMTP thật (ML); so khớp ảnh theo điểm ảnh, vì repo chưa có ảnh chuẩn; chế độ tối so với bản vẽ, vì trang 10 chỉ vẽ nền sáng (B8), kiểm theo token `DESIGN.md`; giữ `callbackUrl` qua liên kết đặt lại trong thư, vì E16 định nghĩa liên kết chỉ có `token`.

**Câu hỏi mở:** M.Trường quyết: (1) sau khi đặt lại mật khẩu có cần quay lại `callbackUrl` ban đầu không: tạm về `/login` không có `callbackUrl`, vì liên kết trong thư theo E16 chỉ có `token`; (2) tài khoản đã xác minh mở `/verify-email`: tạm hiện trạng thái đã xác minh và đi tiếp theo `callbackUrl`.

## FS-3 · Xác nhận mua, kết quả thanh toán, Bài thi của tôi

**Spec:** TM 8.2, 8.3 (phía web), 8.4 (trang quay lại), 11.3, 12, E22. **Phụ thuộc mềm:** AT-1, FS-2.

**Thiết kế:** `docs/design/dot-3/`, canvas trang 11: `XacNhanMua`, `ThanhToanThanhCong`, `ThanhToanDangKiemTra`, `ThanhToanThatBai`, `BaiThiCuaToi`, `DonCuaToi`. Chi tiết đơn không có bản vẽ riêng: theo bố cục `DonCuaToi` và `ChiTietDonHang` (D-k). Trang 11 chỉ vẽ nền sáng: chế độ tối kiểm theo token `DESIGN.md`.

**Sản phẩm:**

- `/checkout/[code]`: đã có quyền thì hiện "Bạn đang có N lượt"; ô voucher; giá đổi; đổi tài khoản.
- `/checkout/result`: công khai, hỏi lại trạng thái (TM 8.4).
- `/home`: thẻ "Bài đã mua", "Đang kiểm tra", đủ các nút ở TM 12. Nút Bắt đầu dẫn tới `/prepare/[entitlementId]` (FS-5).
- `/account/orders`: danh sách đơn của học viên (E22).
- `/account/orders/[code]`: chi tiết đơn, tiếp tục thanh toán và hủy đơn chờ (D-k).
- Bỏ dòng ẩn của mục Đơn của tôi ở thanh đầu trang.

**Đường dẫn sở hữu:**

- `apps/web-student/src/app/checkout/**` (mới)
- `apps/web-student/src/app/account/orders/**` (mới)
- `apps/web-student/src/app/home/page.tsx`
- `apps/web-student/src/app/metadata.test.ts` (tiêu đề `/home`)
- `apps/web-student/src/features/checkout/**` (mới)
- `apps/web-student/src/features/orders/**` (mới)
- `apps/web-student/src/features/my-exams/**` (mới)
- `apps/web-student/src/features/home/student-home-view.tsx`, `apps/web-student/src/features/home/student-home-view.test.tsx`
- thêm vào: `apps/web-student/src/components/student-header.tsx`, `apps/web-student/src/components/student-header.test.tsx` (bỏ dòng ẩn Đơn của tôi)
- thêm vào: `apps/web-student/src/messages/{vi,en}.json` (khối `checkout`, `orders`, `home`)
- `e2e/tests/sso.spec.ts` (đổi chỗ kiểm tiêu đề `/home`)
- `e2e/tests/checkout.spec.ts` (mới), `e2e/tests/my-exams.spec.ts` (mới), `e2e/tests/checkout-visual.spec.ts` (mới)

**Ngoài phạm vi:** API đơn, cổng giả lập, IPN (CM-3a); `/me/exams`, `exam-access` (CM-4b); tính giá và voucher (CM-2). Trang `/prepare/[entitlementId]` (FS-5), `/history` (FS-6). Mua bộ đề và nhóm thẻ theo bộ (FB). `proxy.ts` (FS-1). Thanh toán qua VNPAY sandbox thật (VP-2).

**Nghiệm thu:**

- AC-1: Mua bằng cổng giả lập, chọn Thành công: `/checkout/result` hiện "Đã cấp bài thi", nút "Vào Bài thi của tôi", thẻ có trên `/home` — bằng chứng: `e2e/tests/checkout.spec.ts` (mới).
- AC-2: Chọn Thất bại: hiện "Thanh toán chưa thành công", nút "Thanh toán lại" mở lần thanh toán mới của cùng đơn — bằng chứng: `checkout.spec.ts` (mới).
- AC-3: Chọn Không trả lời: hỏi lại mỗi 2 giây, quá 60 giây hiện "Đang kiểm tra thanh toán" kèm mã đơn và hướng dẫn liên hệ hỗ trợ — bằng chứng: `checkout.spec.ts` (mới), `checkout-result-view.test.tsx` (mới).
- AC-4: Voucher 100%: tổng ghi "0 đ (đã áp dụng voucher)", nút là "Dùng voucher và nhận bài thi", không qua cổng thanh toán, thẻ có trên `/home` — bằng chứng: `checkout.spec.ts` (mới).
- AC-5: Đơn 0 đồng trả `next = CHECKING` thì trang kết quả hiện "Đang kiểm tra" kèm mã đơn — bằng chứng: `checkout-view.test.tsx` (mới).
- AC-6: Voucher: có Áp dụng và gỡ mã; mỗi mã lỗi `VOUCHER_*` hiện một câu riêng ở vi và en — bằng chứng: `voucher-box.test.tsx` (mới).
- AC-7: Đổi tài khoản giữa chừng: máy chủ trả 409 `ACCOUNT_CHANGED`, trang tải lại với tài khoản mới, phải xác nhận lại, không có đơn nào được tạo cho tài khoản cũ — bằng chứng: `checkout.spec.ts` (mới).
- AC-8: `v`, `p` khác giá hiện tại, hoặc máy chủ trả 409 `PRICE_CHANGED`: hiện dải "Giá hoặc quyền lợi đã thay đổi" kèm giá cũ và giá mới, phải xác nhận lại — bằng chứng: `checkout-view.test.tsx` (mới).
- AC-9: Đã có quyền: hiện "Bạn đang có N lượt", nút "Vào bài thi" tới `/prepare/<entitlementId>`, "Mua thêm lượt" là nút phụ. `CHECKING` thì không cho mua, hiện mã đơn đang kiểm tra — bằng chứng: `checkout-view.test.tsx` (mới).
- AC-10: Sản phẩm ngừng bán (410 `PRODUCT_DISCONTINUED`): dừng, có nút về danh mục — bằng chứng: `checkout-view.test.tsx` (mới).
- AC-11: Bấm thanh toán nhiều lần chỉ gửi một `Idempotency-Key`, chỉ tạo một đơn — bằng chứng: `checkout-view.test.tsx` (mới), `checkout.spec.ts` (mới).
- AC-12: Khách mở `/checkout/<code>` thì về `/login?callbackUrl=` còn `v`, `p`; đăng nhập xong quay lại đúng trang — bằng chứng: `checkout.spec.ts` (mới).
- AC-13: Trang quay lại mở ở trình duyệt chưa đăng nhập: hiện kết quả theo `paymentState`, nút "Đăng nhập để xem bài thi" có `callbackUrl = /checkout/result?order=<mã>`, không bị đẩy về `/login` — bằng chứng: `checkout.spec.ts` (mới).
- AC-14: Đơn của tài khoản khác ở `/checkout/result` và `/account/orders/<code>` hiện "Không tìm thấy đơn trong tài khoản đang đăng nhập…" — bằng chứng: `checkout.spec.ts` (mới).
- AC-15: Thẻ `/home` đúng cả 8 tình huống nút của TM 12; số lượt lấy từ quyền đã cấp — bằng chứng: `my-exam-card.test.tsx` (mới).
- AC-16: Đã thu tiền, chưa cấp xong: thẻ "Đang kiểm tra" kèm mã đơn và hướng dẫn hỗ trợ, không đề nghị thanh toán lại — bằng chứng: `my-exam-card.test.tsx` (mới).
- AC-17: `/home` lỗi tải không hiện "Bạn chưa có bài thi nào": có thông báo và nút Thử lại; đang có dữ liệu cũ thì ghi "Dữ liệu chưa được cập nhật" — bằng chứng: `my-exams-view.test.tsx` (mới).
- AC-18: `/home` rỗng hiện "Bạn chưa có bài thi nào" kèm "Khám phá bài thi"; có dữ liệu thì nút này nằm cạnh tiêu đề; đang tải có khung giữ chỗ — bằng chứng: `my-exams-view.test.tsx` (mới).
- AC-19: Nút Bắt đầu dẫn tới `/prepare/<entitlementId>` — bằng chứng: `my-exams.spec.ts` (mới).
- AC-20: `/account/orders` hiện đơn của chính tài khoản, đủ bốn trạng thái — bằng chứng: `orders-view.test.tsx` (mới), `my-exams.spec.ts` (mới).
- AC-21: `/account/orders/<code>`: đơn chờ có tiếp tục thanh toán (`/pay`) và hủy (`/cancel`); máy chủ từ chối thì hiện lỗi và yêu cầu tạo đơn mới — bằng chứng: `order-detail-view.test.tsx` (mới).
- AC-22: Mục Đơn của tôi hiện trên thanh đầu trang khi đã đăng nhập — bằng chứng: `apps/web-student/src/components/student-header.test.tsx`.
- AC-23: Các màn của task đúng khi sáng và tối, vi và en, 390px và 1280px: không tràn ngang, tương phản ≥ 4,5, không lẫn ngôn ngữ — bằng chứng: `e2e/tests/checkout-visual.spec.ts` (mới).
- AC-24: `/checkout/result` nhận 422 `VNPAY_SIGNATURE_INVALID` thì hiện trạng thái lỗi, không hiện đã thanh toán — bằng chứng: `checkout-result.test.tsx` (mới)
- AC-25: Đơn bộ đề có `examId` trống vẫn hiện đúng ở `/account/orders` và `/account/orders/<code>` — bằng chứng: `account-orders.test.tsx` (mới)

**Ảnh hưởng ngoài task:**

- Tiêu đề `/home` đổi từ "Chào …" sang "Bài thi của tôi" (`e2e/tests/sso.spec.ts:29`, `:58`) → sửa hai chỗ kiểm, `pnpm --filter @thi360/e2e test tests/sso.spec.ts` [chủ: task này]
- Luồng về `/home` sau đăng nhập (`e2e/tests/sso.spec.ts:28,57,64,83`, `e2e/tests/session-refresh.spec.ts:107,112`, `e2e/tests/users.spec.ts:98`) → `pnpm --filter @thi360/e2e test tests/sso.spec.ts tests/session-refresh.spec.ts tests/users.spec.ts` [chủ: task này]
- Trang học viên cũ (`apps/web-student/src/features/home/student-home-view.test.tsx`: "Chưa có bài thi nào", "Chào Học viên 01", thứ tự thanh đầu trang) → viết lại theo TM 12, giữ các ca thanh đầu trang, `pnpm --filter @thi360/web-student test src/features/home/student-home-view.test.tsx` [chủ: task này]
- Tiêu đề trang `/home` (`apps/web-student/src/app/metadata.test.ts`, "Trang học viên") → cập nhật, `pnpm --filter @thi360/web-student test src/app/metadata.test.ts` [chủ: task này]
- Luồng 401 toàn cục ở trang công khai `/checkout/result` (`apps/web-student/src/providers/query-provider.tsx`) → dùng hàm đọc phiên tùy chọn của FS-1, AC-13; `pnpm --filter @thi360/web-student test src/providers/query-provider.test.tsx` [chủ: task này]
- Danh sách ẩn của thanh đầu trang (`apps/web-student/src/components/student-header.tsx`) → `pnpm --filter @thi360/web-student test src/components/student-header.test.tsx` [chủ: task này]
- Khóa dịch vi và en (`apps/web-student/src/messages/messages.test.ts`, `source-scan.test.ts`) → `pnpm --filter @thi360/web-student test src/messages` [chủ: task này]
- `proxy.ts` đã có `/checkout/result` công khai, `/checkout/<code>` cần phiên (`apps/web-student/src/proxy.ts`) → task không sửa; AC-12, AC-13 trong `checkout.spec.ts` (mới) [chủ: FS-1]
- Cổng giả lập phải bật được ở profile `e2e` (`THI360_VNPAY_MODE=MOCK`, trang `/mock-vnpay`, D-g) → test của CM-3a; `checkout.spec.ts` (mới) chạy với profile này [chủ: CM-3a]
- Liên kết tới `/prepare/<entitlementId>` trả 404 tới khi FS-5 merge → AC-19 chỉ kiểm đường dẫn; luồng thật ở `e2e` của FS-5 [chủ: FS-5]
- Liên kết Xem kết quả và Lịch sử (`/attempts/<id>/result`, `/history?examId=…`) trả 404 tới khi FS-6 merge → `my-exam-card.test.tsx` (mới) chỉ kiểm đường dẫn; FS-6 kiểm trang đích [chủ: FS-6]
- `/checkout/[code]` và `/home` được FB mở rộng cho bộ đề (`apps/web-student/src/features/checkout/**`, `apps/web-student/src/features/my-exams/**`) → FB chạy lại `checkout.spec.ts` (mới), `my-exams.spec.ts` (mới) [chủ: FB]
- Kiểu API đơn và `/me/exams` (`packages/api-client/src/schema.d.ts`) → lane web không sinh lại; `pnpm typecheck` sau khi rebase [chủ: CM-3a, CM-4b]
- Giới hạn dung lượng bundle: không có: đã tìm `size-limit|bundlesize|bundle-analyzer|budget` trong `.github/workflows`, `scripts`, `package.json`, `apps/web-student/package.json`, `apps/web-student/next.config.ts` → `pnpm --filter @thi360/web-student build` vẫn qua

**Kế hoạch kiểm thử:**

- AC-1: `checkout.spec.ts` ca thành công, E2E, `pnpm --filter @thi360/e2e test tests/checkout.spec.ts`
- AC-2: `checkout.spec.ts` ca thất bại, E2E, `pnpm --filter @thi360/e2e test tests/checkout.spec.ts`
- AC-3: `checkout.spec.ts` ca không trả lời, tua 60 giây bằng `page.clock`, E2E, `pnpm --filter @thi360/e2e test tests/checkout.spec.ts`; `checkout-result-view.test.tsx` nhịp 2 giây và mốc 60 giây bằng đồng hồ giả, unit, `pnpm --filter @thi360/web-student test src/features/checkout/checkout-result-view.test.tsx`
- AC-4: `checkout.spec.ts` ca voucher 100%, E2E, `pnpm --filter @thi360/e2e test tests/checkout.spec.ts`
- AC-5: `checkout-view.test.tsx` ca `next = CHECKING`, unit, `pnpm --filter @thi360/web-student test src/features/checkout/checkout-view.test.tsx`
- AC-6: `voucher-box.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/checkout/voucher-box.test.tsx`
- AC-7: `checkout.spec.ts` ca đổi tài khoản ở tab thứ hai, E2E, `pnpm --filter @thi360/e2e test tests/checkout.spec.ts`
- AC-8: `checkout-view.test.tsx` ca giá đổi, unit, `pnpm --filter @thi360/web-student test src/features/checkout/checkout-view.test.tsx`
- AC-9: `checkout-view.test.tsx` ca đã có quyền và `CHECKING`, unit, `pnpm --filter @thi360/web-student test src/features/checkout/checkout-view.test.tsx`
- AC-10: `checkout-view.test.tsx` ca 410, unit, `pnpm --filter @thi360/web-student test src/features/checkout/checkout-view.test.tsx`
- AC-11: `checkout-view.test.tsx` ca bấm nhiều lần, unit, `pnpm --filter @thi360/web-student test src/features/checkout/checkout-view.test.tsx`; `checkout.spec.ts` đếm đơn trên `/account/orders`, E2E, `pnpm --filter @thi360/e2e test tests/checkout.spec.ts`
- AC-12: `checkout.spec.ts` ca khách, E2E, `pnpm --filter @thi360/e2e test tests/checkout.spec.ts`
- AC-13: `checkout.spec.ts` ca trang quay lại ở ngữ cảnh chưa đăng nhập, E2E, `pnpm --filter @thi360/e2e test tests/checkout.spec.ts`
- AC-14: `checkout.spec.ts` ca đơn của tài khoản khác, E2E, `pnpm --filter @thi360/e2e test tests/checkout.spec.ts`
- AC-15: `my-exam-card.test.tsx` bảng 8 tình huống, unit, `pnpm --filter @thi360/web-student test src/features/my-exams/my-exam-card.test.tsx`
- AC-16: `my-exam-card.test.tsx` ca đang kiểm tra, unit, `pnpm --filter @thi360/web-student test src/features/my-exams/my-exam-card.test.tsx`
- AC-17: `my-exams-view.test.tsx` ca lỗi, unit, `pnpm --filter @thi360/web-student test src/features/my-exams/my-exams-view.test.tsx`
- AC-18: `my-exams-view.test.tsx` ca rỗng, có dữ liệu, đang tải, unit, `pnpm --filter @thi360/web-student test src/features/my-exams/my-exams-view.test.tsx`
- AC-19: `my-exams.spec.ts` ca nút Bắt đầu, E2E, `pnpm --filter @thi360/e2e test tests/my-exams.spec.ts`
- AC-20: `orders-view.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/orders/orders-view.test.tsx`; `my-exams.spec.ts` ca danh sách đơn, E2E, `pnpm --filter @thi360/e2e test tests/my-exams.spec.ts`
- AC-21: `order-detail-view.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/orders/order-detail-view.test.tsx`
- AC-22: `student-header.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/components/student-header.test.tsx`
- AC-23: `checkout-visual.spec.ts`, giao diện, `pnpm --filter @thi360/e2e test tests/checkout-visual.spec.ts`
- AC-24: "chữ ký sai", unit, `pnpm --filter @thi360/web-student test src/features/checkout/__tests__/checkout-result.test.tsx`
- AC-25: "đơn bộ", unit, `pnpm --filter @thi360/web-student test src/features/checkout/__tests__/account-orders.test.tsx`
- Hồi quy: `pnpm lint && pnpm typecheck && pnpm test && pnpm build`; `pnpm --filter @thi360/web-student test src/features/home/student-home-view.test.tsx src/app/metadata.test.ts src/providers/query-provider.test.tsx src/components/student-header.test.tsx src/messages`; `pnpm --filter @thi360/e2e test tests/sso.spec.ts tests/session-refresh.spec.ts tests/users.spec.ts`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: sản phẩm và voucher 100% lấy từ tệp hằng số của DS; mỗi ca E2E tạo một học viên mới có hậu tố riêng qua `POST /api/v1/admin/users` (như `e2e/tests/learning-profile.spec.ts`), nên đơn và quyền không dính nhau. Backend bật profile `e2e` qua biến `E2E_BACKEND_CMD`. Lane chạy compose project riêng (A6).
- Không kiểm: IPN gửi lặp và việc trang quay lại không đổi trạng thái đơn (CM-3a kiểm ở backend). Luồng VNPAY sandbox thật (VP-2). Nội dung thư sau khi mua (CM-3a, ML).

## FS-4 · Thành phần câu hỏi (`packages/ui`)

**Spec:** LT 4 (định dạng câu trả lời), 8, F14, F22; BE 4.3 (phần thân câu hỏi); SUP mục 5; plan B7, B8.

**Thiết kế:** `docs/design/dot-3/`, canvas trang 13, 06, 09: dạng mới `ThiTraLoiNgan`, `ThiDienKhuyet`, `ThiGhepDoi`, `ThiTuLuan`, `ThiMobileGhepDoi`; chế độ `review` `CauHoiCheDoXemLai`; dạng cũ chỉ lấy phần thân câu ở `CauHoi*Thi`, `ThiMobile*` (Kéo thả đã bỏ; bỏ dòng "dấu phẩy hoặc dấu chấm đều được" của `CauHoiDienDapAnThi`). Trang 13 chỉ vẽ nền sáng: chế độ tối kiểm theo token `DESIGN.md`.

**Sản phẩm:**

- Đưa cả bộ hiển thị chỉ đọc vào `packages/ui`, cổng xuất riêng `@thi360/ui/rich-text`: `RichTextView`, `renderMath`, `deferUntilMhchem`, `sanitizeQuestionHtml`, `hasUnbalancedMath`, `questionHtmlText`, các hằng và hàm của `html-contract`, CSS phần hiển thị; đủ mọi loại nút (`mathInline`, `mathBlock`, bảng cũ, `dataTable`, `imageFigure`, `crossDiagram`, `quote`, `underlineLabel`, `blank`), KaTeX 0.19.1, mhchem tải khi cần. Không sửa `apps/web-admin` (B8); FA-1 đổi import sau G0b.
- Thành phần cho 8 dạng câu ở cổng xuất riêng `@thi360/ui/question`, mỗi dạng có chế độ `answer` (làm bài) và `review` (chỉ đọc, hiện đáp án, điểm từng ý khi có). Giá trị trả ra đúng định dạng `answer` của LT 4.
- Ngữ liệu chùm; KaTeX; chữ cái phương án theo `option_order`.
- Tự luận: ô viết có chỗ cắm trình soạn, mặc định là ô nhập chữ thường; bộ đếm "n/`wordLimit` chữ"; `wordLimit` là số chữ tối thiểu, chưa đủ thì nhắc tại câu, không chặn thao tác (BS 6). Trình soạn tiptap do FS-5 cắm vào.
- Dùng được bằng bàn phím, có nhãn cho trình đọc màn hình.
- Trang thử `/dev/question-kit` trong web-student, chỉ có khi chạy dev.
- Trợ thủ kiểm giao diện `e2e/tests/helpers/visual-matrix.ts` cho các lane web của đợt: sáng và tối, vi và en, 390px và 1280px; chụp ảnh, kiểm tràn ngang, độ tương phản chữ ≥ 4,5, lẫn ngôn ngữ, `lang` của trang. Phép kiểm lẫn ngôn ngữ bỏ qua vùng nội dung đề có thuộc tính `data-exam-content`; thành phần câu hỏi của `@thi360/ui/question` và mọi chỗ hiện tên bài, phần, mục, phân đoạn gắn thuộc tính này.
- Hàm đếm chữ dùng chung `countWords(html)` trong `@thi360/ui/question` theo cách đếm của BS 6.
- Hàm chọn tên theo ngôn ngữ `pickName(item, locale)` trong `@thi360/web-kit` (chép từ `apps/web-admin/src/features/catalog/utils.ts`): giao diện `en` hiện `nameEn` nếu có, không thì `nameVi`. FS-4 tạo hàm, quy ước `data-exam-content` và phép kiểm trong `visual-matrix.ts`; việc nối vào từng màn thuộc lane làm màn đó (A7 mục 7, review vòng 4, P4-9). Web-admin giữ bản của mình trong đợt này.

**Đường dẫn sở hữu:**

- `packages/ui/src/rich-text/**` (mới)
- `packages/ui/src/question/**` (mới)
- `packages/ui/package.json` (cổng xuất `./rich-text`, `./question`; `katex` 0.19.1)
- `pnpm-lock.yaml`
- thêm vào: `packages/ui/src/messages/ui-strings.ts` (khối chuỗi của thành phần câu hỏi, vi và en)
- `apps/web-student/src/app/dev/question-kit/**` (mới)
- `e2e/tests/helpers/visual-matrix.ts` (mới)
- `e2e/tests/question-kit-visual.spec.ts` (mới)
- `e2e/tests/ui-bundle-boundary.spec.ts` (mới)
- thêm vào: `packages/web-kit/src/i18n/pick-name.ts` (mới), `packages/web-kit/src/index.ts` (xuất hàm)

**Ngoài phạm vi:** đổi import của web-admin và xóa bản cũ (FA-1). Trình soạn tiptap cho tự luận, hàng chờ lưu, phím tắt của màn làm bài (FS-5). Màn xem lại bài (FS-6). Làm thử như học viên (FA-4). `proxy.ts` (FS-1): trang thử đi theo luật cần đăng nhập hiện có. Mã trình soạn (tiptap, mathlive) không vào `packages/ui`.

**Nghiệm thu:**

- AC-1: Mọi mẫu hợp lệ của `docs/specs/fixtures/rich-text-samples.json` hiển thị qua `RichTextView` của `packages/ui` đúng như bản của web-admin; mẫu không hợp lệ bị bộ lọc từ chối — bằng chứng: `packages/ui/src/rich-text/__tests__/fixtures.test.tsx` (mới), chép ca từ `apps/web-admin/src/components/rich-text/__tests__/fixtures-roundtrip.test.tsx`.
- AC-2: Đủ 9 loại nút hiển thị đúng, kể cả đánh số ô trống theo `blankNumberStart` và số dòng của trích dẫn — bằng chứng: `packages/ui/src/rich-text/__tests__/rich-text-view.test.tsx` (mới).
- AC-3: Công thức `\ce` hiển thị sau khi tải mhchem; nội dung không có `\ce` thì không tải mhchem — bằng chứng: `packages/ui/src/rich-text/__tests__/math-render-mhchem.test.ts` (mới).
- AC-4: Cổng `@thi360/ui/rich-text` xuất đủ mọi hàm và hằng mà mã trình soạn và các màn của web-admin đang import từ bộ hiển thị — bằng chứng: `packages/ui/src/rich-text/__tests__/exports.test.ts` (mới), danh sách tên lấy từ import hiện có của web-admin.
- AC-5: `@thi360/ui` (tệp gốc `src/index.ts`) không xuất lại `rich-text`, `question`; JS tải ở `/` và `/login` của bản build không chứa KaTeX — bằng chứng: `packages/ui/src/rich-text/__tests__/barrel-boundary.test.ts` (mới), `e2e/tests/ui-bundle-boundary.spec.ts` (mới).
- AC-6: Bản build web-student không có mã trình soạn: tìm `ProseMirror|MathfieldElement|mathlive` trong `apps/web-student/.next/static/chunks` được 0 tệp; `packages/ui/package.json` không có `@tiptap/*`, `mathlive` — bằng chứng: lệnh ở Kế hoạch kiểm thử.
- AC-7: Một đáp án: chọn, đổi, bỏ chọn; giá trị `{optionId}`, bỏ chọn là `null` — bằng chứng: `packages/ui/src/question/__tests__/single-choice.test.tsx` (mới).
- AC-8: Nhiều đáp án: tích, bỏ tích, có dòng "Chọn các phương án đúng"; giá trị `{optionIds}`, bỏ hết là mảng rỗng — bằng chứng: `multiple-choice.test.tsx` (mới).
- AC-9: Trả lời ngắn: giá trị `{text}`, có hướng dẫn nhập, không có dòng "dấu phẩy hoặc dấu chấm đều được"; chuỗi chỉ có khoảng trắng tính là bỏ trống — bằng chứng: `short-answer.test.tsx` (mới).
- AC-10: Điền khuyết: ô nhập nằm trong đoạn văn, mỗi ô đổi riêng trong `{blanks}` — bằng chứng: `cloze.test.tsx` (mới).
- AC-11: Đúng/Sai: từng ý chọn Đúng, Sai, bỏ chọn về `null`, các ý khác không đổi — bằng chứng: `true-false.test.tsx` (mới).
- AC-12: Ghép đôi: chọn mục cột phải cho từng mục cột trái bằng danh sách chọn, bỏ cặp về `null`, không cần kéo thả — bằng chứng: `matching.test.tsx` (mới).
- AC-13: Tự luận: giá trị `{html, wordCount}`, đếm chữ đúng, hiện bộ đếm theo `wordLimit` tối thiểu, chưa đủ thì có lời nhắc, đủ thì hết nhắc; thiếu số chữ không chặn thao tác; chỗ cắm trình soạn thay được ô nhập mặc định — bằng chứng: `essay.test.tsx` (mới).
- AC-14: Bộ câu hỏi: ngữ liệu hiện cùng các câu con, ghim cạnh câu con ở 1280px, xếp dọc ở 390px — bằng chứng: `question-set.test.tsx` (mới), `e2e/tests/question-kit-visual.spec.ts` (mới).
- AC-15: Chữ cái phương án theo `option_order` đã nhận, kể cả khi đó là thứ tự soạn (`shuffleOptions = false`); câu trả lời lưu id, không lưu chữ cái — bằng chứng: `option-order.test.tsx` (mới).
- AC-16: Chế độ `review` của cả 8 dạng: không sửa được, hiện câu trả lời và đáp án đúng; có điểm từng ý thì hiện, không có thì không hiện số 0 — bằng chứng: `review-mode.test.tsx` (mới).
- AC-17: Mỗi dạng thao tác được hoàn toàn bằng bàn phím, mọi ô có tên truy cập — bằng chứng: `keyboard-a11y.test.tsx` (mới).
- AC-18: Trang thử gọi `notFound()` khi `NODE_ENV = production` — bằng chứng: `apps/web-student/src/app/dev/question-kit/page.test.tsx` (mới).
- AC-19: 8 dạng ở cả hai chế độ đúng khi sáng và tối, vi và en, 390px và 1280px: không tràn ngang, tương phản ≥ 4,5, không lẫn ngôn ngữ — bằng chứng: `e2e/tests/question-kit-visual.spec.ts` (mới), ảnh trong báo cáo lane.
- AC-20: Mọi chuỗi giao diện của thành phần có ở vi và en; quét nguồn `src/rich-text`, `src/question` không có chữ tiếng Việt viết thẳng — bằng chứng: `packages/ui/src/question/__tests__/source-scan.test.ts` (mới).
- AC-21: Diff không có tệp nào trong `apps/web-admin` — bằng chứng: lệnh ở Kế hoạch kiểm thử ra `0`.
- AC-22: `countWords` cho cùng kết quả với chữ tiếng Việt, khoảng trắng không ngắt, xuống dòng, thẻ rỗng, công thức đứng riêng — bằng chứng: `word-count.test.ts` "cách đếm chữ" (mới)
- AC-23: Ở giao diện `en`, bài có tên phần, câu hỏi tiếng Việt: phép kiểm lẫn ngôn ngữ của `visual-matrix.ts` bỏ qua vùng `data-exam-content` nhưng vẫn báo lỗi khi một nhãn giao diện ngoài vùng đó là tiếng Việt — bằng chứng: `question-kit-visual.spec.ts` "nội dung đề không tính lẫn ngôn ngữ" (mới)
- AC-24: `pickName` ở `en` trả `nameEn` khi có, trả `nameVi` khi `nameEn` trống hoặc chỉ có khoảng trắng; ở `vi` luôn trả `nameVi` — bằng chứng: `pick-name.test.ts` "chọn tên theo ngôn ngữ" (mới)

**Ảnh hưởng ngoài task:**

- Tệp gốc của `@thi360/ui`, được hai app import ở khắp nơi, gồm cả `layout.tsx` phía máy chủ (`packages/ui/src/index.ts`) → `barrel-boundary.test.ts` (mới), `pnpm typecheck`, `pnpm build` [chủ: task này]
- Cổng xuất cũ `.` và `./styles/tokens.css` (`packages/ui/package.json`) → `pnpm --filter @thi360/web-admin build`, `pnpm --filter @thi360/web-student build` [chủ: task này]
- Kiểu `UiStrings` và hai bộ chuỗi do hai app cấp (`packages/ui/src/messages/ui-strings.ts`, `apps/web-student/src/providers/preferences-provider.tsx`, `apps/web-admin/src/providers/preferences-provider.tsx`, `src/test/render.tsx` của hai app) → `pnpm test` [chủ: task này]
- Quét nguồn cũ chỉ phủ `src/components` (`packages/ui/src/components/source-scan.test.ts`) → giữ nguyên, thêm `source-scan.test.ts` (mới) cho hai thư mục mới [chủ: task này]
- Bản bộ hiển thị của web-admin còn nguyên tới khi FA-1 đổi (`apps/web-admin/src/components/rich-text/**`, test trong `__tests__/`) → `pnpm --filter @thi360/web-admin test src/components/rich-text` xanh, diff web-admin bằng 0 [chủ: task này]
- Đổi import của web-admin: `features/exams/components/exam-preview-paper.tsx`, `features/question-import/components/question-preview.tsx`, `features/questions/components/{cloze-passage,question-detail-body,question-detail-content,question-versions-compare,question-versions-view}.tsx`, `features/questions/preview/question-player.tsx`, `features/questions/types.ts`, `features/exams/create-exam-schema.ts`, `features/questions/editor-schema.ts`, và mã trình soạn `components/rich-text/{extensions/atom-block.ts,extensions/html-sanitize.ts,insert/chemistry/formula-span.tsx,insert/image/figure-html.ts,insert/quote/dialog.tsx,insert/table/dialog.tsx,insert/word/materialize.ts,rich-text-editor.tsx}` → `exports.test.ts` (mới) ở task này; test và build web-admin sau khi đổi [chủ: FA-1]
- CSS dùng chung cho phần hiển thị và trình soạn (`apps/web-admin/src/components/rich-text/rich-text.css`) → task này chỉ chép phần hiển thị; phần của trình soạn ở lại web-admin, `pnpm --filter @thi360/web-admin test src/components/rich-text` sau khi đổi [chủ: FA-1]
- Lớp Tailwind trong thư mục mới được quét nhờ `@source "../../../../packages/ui/src"` (`apps/web-student/src/app/globals.css`, `apps/web-admin/src/app/globals.css`) → `question-kit-visual.spec.ts` (mới) [chủ: task này]
- Quét nguồn web-student cấm chữ có dấu trong `.ts`, `.tsx` (`apps/web-student/src/messages/source-scan.test.ts`) → dữ liệu mẫu của trang thử đặt trong tệp JSON; `pnpm --filter @thi360/web-student test src/messages/source-scan.test.ts` [chủ: task này]
- Trang thử cần đăng nhập vì task không sửa `proxy.ts` (`apps/web-student/src/proxy.ts`) → `question-kit-visual.spec.ts` (mới) đăng nhập `student01`; `pnpm --filter @thi360/web-student test src/proxy.test.ts` [chủ: task này]
- E2E của CI chạy bản build nên trang thử là 404 (`.github/workflows/e2e.yml`, `E2E_USE_BUILT`, `e2e/playwright.config.ts`) → spec giao diện bỏ qua khi `E2E_USE_BUILT = 1`, lane chạy bằng `next dev` [chủ: task này]
- Giới hạn dung lượng bundle: không có: đã tìm `size-limit|bundlesize|bundle-analyzer|budget` trong `.github/workflows`, `scripts`, `package.json`, `apps/web-student/package.json`, `apps/web-student/next.config.ts` → `pnpm --filter @thi360/web-student build` vẫn qua
- Kiểu API sinh tự động (`packages/api-client/src/schema.d.ts`) → task không sửa; props của thành phần theo LT 4, lane dùng tự ánh xạ, `pnpm typecheck` ở lane đó [chủ: FS-5, FS-6, FA-4]
- Thành phần câu hỏi và chỗ cắm trình soạn là hợp đồng của lane sau → đổi API qua Advisor (B8); FS-5, FS-6, FA-4 chạy `pnpm typecheck` [chủ: FS-5, FS-6, FA-4]
- Trợ thủ `visual-matrix.ts` (mới) được các lane web dùng lại → lane sau chỉ gọi, muốn đổi thì qua Advisor [chủ: FS-1, FS-3, FS-5, FS-6, FB]
- `pickName` và quy ước `data-exam-content` (mới) được các màn dùng → mỗi lane màn nối và kiểm theo A7 mục 7 [chủ: FS-1, FS-3, FS-5, FS-6, FS-7, FB, FA-4, FA-5, LP]

**Kế hoạch kiểm thử:**

- AC-1: `fixtures.test.tsx`, unit, `pnpm --filter @thi360/ui test src/rich-text/__tests__/fixtures.test.tsx`
- AC-2: `rich-text-view.test.tsx`, unit, `pnpm --filter @thi360/ui test src/rich-text/__tests__/rich-text-view.test.tsx`
- AC-3: `math-render-mhchem.test.ts`, unit, `pnpm --filter @thi360/ui test src/rich-text/__tests__/math-render-mhchem.test.ts`
- AC-4: `exports.test.ts`, unit, `pnpm --filter @thi360/ui test src/rich-text/__tests__/exports.test.ts`
- AC-5: `barrel-boundary.test.ts`, unit, `pnpm --filter @thi360/ui test src/rich-text/__tests__/barrel-boundary.test.ts`; `ui-bundle-boundary.spec.ts`, E2E trên bản build, `E2E_USE_BUILT=1 pnpm --filter @thi360/e2e test tests/ui-bundle-boundary.spec.ts`
- AC-6: quét bản build, tích hợp, `pnpm --filter @thi360/web-student build && grep -rlE "ProseMirror|MathfieldElement|mathlive" apps/web-student/.next/static/chunks | wc -l` ra `0`
- AC-7: `single-choice.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/single-choice.test.tsx`
- AC-8: `multiple-choice.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/multiple-choice.test.tsx`
- AC-9: `short-answer.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/short-answer.test.tsx`
- AC-10: `cloze.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/cloze.test.tsx`
- AC-11: `true-false.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/true-false.test.tsx`
- AC-12: `matching.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/matching.test.tsx`
- AC-13: `essay.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/essay.test.tsx`
- AC-14: `question-set.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/question-set.test.tsx`; bố cục hai khổ màn, giao diện, `pnpm --filter @thi360/e2e test tests/question-kit-visual.spec.ts`
- AC-15: `option-order.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/option-order.test.tsx`
- AC-16: `review-mode.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/review-mode.test.tsx`
- AC-17: `keyboard-a11y.test.tsx`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/keyboard-a11y.test.tsx`
- AC-18: `page.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/app/dev/question-kit/page.test.tsx`
- AC-19: `question-kit-visual.spec.ts`, giao diện, `pnpm --filter @thi360/e2e test tests/question-kit-visual.spec.ts`
- AC-20: `source-scan.test.ts`, unit, `pnpm --filter @thi360/ui test src/question/__tests__/source-scan.test.ts`
- AC-21: kiểm diff, tích hợp, `git diff --name-only origin/integration/dot-3...HEAD -- apps/web-admin | wc -l` ra `0`
- AC-22: `word-count.test.ts` "cách đếm chữ", unit, `pnpm --filter @thi360/ui test src/question/word-count.test.ts`
- AC-23: `question-kit-visual.spec.ts` "nội dung đề không tính lẫn ngôn ngữ", E2E, `pnpm --filter @thi360/e2e test tests/question-kit-visual.spec.ts`
- AC-24: `pick-name.test.ts` "chọn tên theo ngôn ngữ", unit, `pnpm --filter @thi360/web-kit test src/i18n/pick-name.test.ts`
- Hồi quy: `pnpm lint && pnpm typecheck && pnpm test && pnpm build`; `pnpm --filter @thi360/web-admin test src/components/rich-text`; `pnpm --filter @thi360/web-student test src/messages/source-scan.test.ts`; `pnpm --filter @thi360/web-student test src/proxy.test.ts`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: mẫu nội dung lấy từ `docs/specs/fixtures/rich-text-samples.json`; câu hỏi mẫu 8 dạng là tệp JSON trong `apps/web-student/src/app/dev/question-kit/`; tài khoản `student01` của dữ liệu dev. Không ghi dữ liệu nên các lane song song không ảnh hưởng nhau.
- Không kiểm: gắn thành phần với API lượt thi (FS-5, FS-6 kiểm). Trình đọc màn hình thật: chỉ kiểm vai trò và tên truy cập trong DOM. Trang thử không chạy trong E2E của CI vì bản build trả 404. So ảnh tự động với ảnh gốc: repo không có ảnh gốc, ảnh chụp để người xem trong báo cáo lane.


## FS-5 · Chuẩn bị, chờ sinh đề, màn làm bài

**Spec:** BS 4, 5, 6; LT 5.1, 5.2 (bước 5), 6, 7.2, 7.4, 8, L7, L8, F10, F12; plan B7.

**Thiết kế:** `docs/design/dot-3/`, canvas trang 12: `ChuanBiLamBai`, `DangChuanBiDe`, `LamBaiTheoPhan`, `LamBaiMobileTheoPhan`, `HoanThanhPhan`, `ChonPhan3`, `NopBaiXacNhan`, `TiepQuanLuot`. Đã vẽ lại theo đợt 2B ngày 2026-11-17: `ChuanBiLamBai` có chọn định hướng Tin học (như mã BCA) và số câu, thời lượng, điểm tối đa của đường; `ChonPhan3` bỏ ô giữ chỗ định hướng, QDA hiện mã kèm tên môn theo thứ tự; `NopBaiXacNhan` bỏ cảnh báo định hướng. Trang 12 chỉ vẽ nền sáng: chế độ tối kiểm theo token `DESIGN.md`.

**Sản phẩm:**

- `/prepare/[entitlementId]`; màn "Đang chuẩn bị đề" hỏi lại trạng thái (LT 5.2 bước 5); lỗi thì nút Thử lại với khóa mới. Mở lại trang khi lượt đang `STARTING` thì vào thẳng màn chờ, không gửi yêu cầu bắt đầu mới.
- `/attempts/[id]` toàn màn hình, dựng trong chính trang này, không đặt `layout.tsx` ở `[id]` để không bọc trang của FS-6; phần làm lần lượt theo L7, nút "Hoàn thành phần" ở mọi phần trừ phần cuối; lượt `navigation_mode = FREE` (THPTQG) có thanh chuyển phần, không có "Hoàn thành phần", nộp được từ mọi phần (BS 4). Số câu chạy suốt bài theo `display_no` (BS 5).
- Đồng hồ theo `serverNow`.
- Hàng chờ lưu: gửi theo dạng câu, IndexedDB (lớp bọc tự viết, B7), thử lại, trạng thái Đang lưu / Đã lưu / Chưa lưu.
- Màn chuẩn bị có chọn định hướng Tin học (Khoa học máy tính hoặc Tin học ứng dụng), như chọn mã BCA; hiện số câu, thời lượng, điểm tối đa của đường đã chọn.
- Chọn phần 3 (chờ sinh ở nền như trên), đếm "Đã chọn n/3"; QDA hiện mã kèm tên môn theo thứ tự.
- Nộp bài, "Chưa xác nhận nộp bài".
- Tiếp quản, `BroadcastChannel` giữa các tab, cảnh báo khi rời trang.
- Ô viết tự luận bằng tiptap, cắm vào chỗ cắm của FS-4, tải riêng khi gặp câu tự luận; hiện bộ đếm và lời nhắc số chữ tối thiểu, màn xác nhận nộp liệt kê câu chưa đủ số chữ, không chặn nộp (BS 6).
- Xóa bản nháp khi đăng xuất hoặc đổi tài khoản (F12).

**Đường dẫn sở hữu:**

- `apps/web-student/src/app/prepare/[entitlementId]/**` (mới)
- `apps/web-student/src/app/attempts/[id]/page.tsx` (mới)
- `apps/web-student/src/features/attempt-prepare/**` (mới)
- `apps/web-student/src/features/attempt-player/**` (mới)
- `apps/web-student/src/features/attempt-draft/**` (mới)
- `apps/web-student/package.json` (`@tiptap/react`, `@tiptap/starter-kit` 3.34.0), `pnpm-lock.yaml`
- thêm vào: `apps/web-student/src/features/session/api.ts` (xóa bản nháp trong `logoutSession`)
- thêm vào: `apps/web-student/src/messages/{vi,en}.json` (khối `prepare`, `attempt`)
- `e2e/tests/attempt-flow.spec.ts` (mới), `e2e/tests/attempt-resilience.spec.ts` (mới), `e2e/tests/attempt-essay-perf.spec.ts` (mới), `e2e/tests/attempt-visual.spec.ts` (mới)

**Ngoài phạm vi:** API lượt thi (AT-1, AT-2a, AT-2b, AT-3). Thành phần câu hỏi (FS-4). `/attempts/[id]/result`, `/attempts/[id]/review`, `/history` (FS-6). Nút Bắt đầu ở `/home` (FS-3). Làm thử như học viên (FA-4). Ứng dụng di động.

**Nghiệm thu:**

- AC-1: `/prepare/<id>` hiện đủ nội dung LT 5.1; quyền của tài khoản khác hiện trang không tìm thấy — bằng chứng: `prepare-view.test.tsx` (mới), `e2e/tests/attempt-flow.spec.ts` (mới).
- AC-2: Bài có mã BCA hoặc định hướng Tin học: chưa chọn thì chưa bắt đầu được; đổi lựa chọn thì số câu, thời lượng, điểm tối đa đổi theo đường; chỉ có một lựa chọn thì điền sẵn — bằng chứng: `prepare-view.test.tsx` (mới).
- AC-3: Bắt đầu chuyển sang màn chờ, hỏi lại mỗi giây tới 90 giây, `IN_PROGRESS` thì vào màn làm bài; quá 90 giây hiện "Hệ thống đang bận" và hỏi lại thưa hơn — bằng chứng: `preparing-screen.test.tsx` (mới).
- AC-4: `ABORTED` hiện lý do và "Thử lại"; Thử lại gửi `Idempotency-Key` mới — bằng chứng: `preparing-screen.test.tsx` (mới).
- AC-5: Mở lại trang khi lượt `STARTING` thì vào màn chờ, không có `POST /api/v1/me/attempts` — bằng chứng: `prepare-view.test.tsx` (mới).
- AC-6: `NO_ATTEMPTS_LEFT`, `CHOICE_NOT_ALLOWED`, `NO_SERVING_OPTION` mỗi mã hiện một câu riêng, không vào màn chờ — bằng chứng: `prepare-view.test.tsx` (mới).
- AC-7: Mọi phần trừ phần cuối có "Hoàn thành phần", chỉ phần cuối có Nộp bài; không chuyển về phần đã đóng; màn xác nhận hiện tên phần và số câu hoặc ý còn thiếu — bằng chứng: `attempt-player.test.tsx` (mới).
- AC-8: Số câu chạy liền qua các phần theo `display_no`, không đánh lại ở phần mới — bằng chứng: `question-nav.test.tsx` (mới).
- AC-9: E2E HSA FULL thu nhỏ (DS) có tua giờ: làm hai phần đầu, chọn phần 3, nộp, sang trang kết quả — bằng chứng: `attempt-flow.spec.ts` (mới).
- AC-10: Chọn phần 3 HSA: đếm "Đã chọn n/3", đủ 3 thì phải bỏ một môn mới chọn được môn khác; có đồng hồ chờ chọn — bằng chứng: `part-choice.test.tsx` (mới).
- AC-11: QDA hiện mã kèm tên môn theo thứ tự; chỉ có một nhánh hoặc một mã thì vẫn có bước xác nhận, lựa chọn điền sẵn — bằng chứng: `part-choice.test.tsx` (mới).
- AC-12: Phần đang sinh hiện màn chờ; `GENERATION_FAILED` có "Thử lại"; `CHOICE_WINDOW_CLOSED`, `CHOICE_NOT_ALLOWED`, `SUBJECTS_INVALID`, `CHOICE_LOCKED`, `PART_NOT_AWAITING`, `PART_GENERATING` mỗi mã có câu riêng — bằng chứng: `part-choice.test.tsx` (mới).
- AC-13: E2E BCA thu nhỏ: hết giờ chung khi đang ở phần 2 thì lượt đóng, màn khóa và sang kết quả — bằng chứng: `attempt-flow.spec.ts` (mới).
- AC-14: Chỉnh giờ máy không đổi thời gian còn lại (AC-10.12) — bằng chứng: `attempt-resilience.spec.ts` (mới).
- AC-15: Nhịp gửi: câu chọn gửi ngay; ô nhập chữ sau 800 ms ngừng gõ; tự luận sau 2 giây ngừng gõ và tối thiểu mỗi 15 giây khi đang gõ; chuyển câu, chuyển phần, hoàn thành phần, nộp bài thì gửi ngay phần còn chờ — bằng chứng: `save-queue.test.ts` (mới).
- AC-16: "Đã lưu" chỉ hiện khi `seq` máy chủ trả về bằng `seq` mới nhất; "Chưa lưu" kèm lý do; danh sách câu tách "đã lưu" với "đã trả lời" — bằng chứng: `save-queue.test.ts` (mới), `question-nav.test.tsx` (mới).
- AC-17: Mất mạng rồi nối lại: thay đổi giữ trong IndexedDB, thử lại với thời gian chờ tăng dần; nối lại thì đọc `AttemptState` trước, chỉ gửi câu có `seq` lớn hơn của máy chủ — bằng chứng: `attempt-resilience.spec.ts` (mới), `save-queue.test.ts` (mới).
- AC-18: Đóng tab lúc mất mạng rồi mở lại không mất bản nháp — bằng chứng: `attempt-resilience.spec.ts` (mới).
- AC-19: Nộp bài hoặc hoàn thành phần mất phản hồi: màn hiện "Chưa xác nhận nộp bài"; nối lại thì đọc trạng thái lượt trước khi cho gửi lại — bằng chứng: `attempt-resilience.spec.ts` (mới).
- AC-20: Màn xác nhận nộp hiện số câu bỏ trống, số câu làm dở, tình trạng bài viết; `finish`, `submit` gửi kèm `finalAnswers` — bằng chứng: `attempt-player.test.tsx` (mới).
- AC-21: Hai ngữ cảnh trình duyệt: máy sau thấy hộp thoại tiếp quản với Tiếp tục tại đây và Hủy; tiếp quản xong máy trước hiện "Lượt thi đã được tiếp tục ở thiết bị khác." và khóa màn — bằng chứng: `attempt-resilience.spec.ts` (mới).
- AC-22: Hai tab cùng trình duyệt: tab sau chỉ xem, đóng tab đầu thì tab sau sửa được — bằng chứng: `attempt-resilience.spec.ts` (mới).
- AC-23: Còn thay đổi chưa lưu thì rời trang có cảnh báo `beforeunload`; đã lưu hết thì không — bằng chứng: `attempt-resilience.spec.ts` (mới).
- AC-24: Đăng xuất thì bản nháp của tài khoản bị xóa; tài khoản khác đăng nhập trên cùng trình duyệt không đọc được bản nháp đó, kể cả khi phiên đã kết thúc ở nơi khác — bằng chứng: `attempt-resilience.spec.ts` (mới).
- AC-25: Phiên hết hạn giữa lượt rồi đăng nhập lại đúng tài khoản thì bản nháp còn nguyên — bằng chứng: `attempt-resilience.spec.ts` (mới).
- AC-26: Gõ tự luận với CPU chậm 4 lần: p95 độ trễ từ phím tới khung hình kế tiếp ≤ 200 ms, số đo ghi vào báo cáo — bằng chứng: `e2e/tests/attempt-essay-perf.spec.ts` (mới).
- AC-27: Mã tiptap chỉ tải khi mở câu tự luận; bản build không có mathlive — bằng chứng: `attempt-essay-perf.spec.ts` (mới), lệnh quét ở Kế hoạch kiểm thử.
- AC-28: Tải lại trang ở từng bước của LT 7.4 đều về đúng bước: chờ sinh đề, đang làm, chọn phần 3, phần 3 đang sinh, phần sinh lỗi, đã đóng — bằng chứng: `attempt-player.test.tsx` (mới).
- AC-29: Phím tắt chọn phương án 1–4, câu trước, câu sau, đánh dấu hoạt động; không kích hoạt khi đang gõ trong ô nhập — bằng chứng: `attempt-player.test.tsx` (mới).
- AC-30: Tín hiệu còn hoạt động gửi mỗi 30 giây — bằng chứng: `edit-session.test.ts` (mới).
- AC-31: Ảnh trong câu hỏi lấy qua đường dẫn tài nguyên của lượt (LT 7.1), không dùng đường dẫn ảnh gốc — bằng chứng: `attempt-assets.test.ts` (mới).
- AC-32: Các màn của task đúng khi sáng và tối, vi và en, 390px và 1280px: không tràn ngang, tương phản ≥ 4,5, không lẫn ngôn ngữ — bằng chứng: `e2e/tests/attempt-visual.spec.ts` (mới).
- AC-33: Lượt THPTQG ba phần: chuyển qua lại giữa các phần, sửa câu ở phần trước, tải lại trang về đúng phần và câu đang xem, nộp từ phần đầu — bằng chứng: `attempt-flow.spec.ts` "THPTQG chuyển tự do" (mới)
- AC-34: Lượt `SEQUENTIAL` không hiện thanh chuyển phần tự do; lượt `FREE` không hiện "Hoàn thành phần" — bằng chứng: `part-navigation.test.tsx` "theo navigationMode" (mới)
- AC-35: Màn xác nhận nộp hiện số câu còn thiếu theo từng phần và các câu tự luận chưa đủ số chữ, nút Nộp vẫn bấm được — bằng chứng: `submit-confirm.test.tsx` "chưa đủ số chữ" (mới)

**Ảnh hưởng ngoài task:**

- Đăng xuất gọi chung `logoutSession` (`apps/web-student/src/features/home/student-user-menu.tsx:44`, `apps/web-student/src/features/session/forbidden-view.tsx:32`, `apps/web-student/src/features/auth/change-password-view.tsx:211`) → AC-24; `pnpm --filter @thi360/web-student test src/features/session/forbidden-view.test.tsx src/features/auth/change-password-view.test.tsx` [chủ: task này]
- `clearSessionBestEffort` chạy khi phiên hết hạn và khi đổi mật khẩu (`apps/web-student/src/providers/query-provider.tsx:62`, `apps/web-student/src/features/auth/change-password-view.tsx:180,191`) → không xóa bản nháp ở hàm này; AC-25; `pnpm --filter @thi360/web-student test src/providers/query-provider.test.tsx` [chủ: task này]
- Đăng xuất ở web admin kết thúc phiên mà không chạy mã của web-student (`e2e/tests/sso.spec.ts`, ca "đăng xuất ở web admin xóa phiên trên cả web học viên") → so tài khoản khi tải bản nháp, AC-24; `pnpm --filter @thi360/e2e test tests/sso.spec.ts` [chủ: task này]
- Luồng 401 toàn cục khi đang làm bài (`apps/web-student/src/providers/query-provider.tsx`) → AC-25 [chủ: task này]
- Thêm tiptap vào bundle web-student, D-n cấm mã trình soạn lọt vào bundle chung (`apps/web-student/package.json`) → AC-27; `pnpm --filter @thi360/web-student build` [chủ: task này]
- Lockfile (`pnpm-lock.yaml`) → `pnpm install --frozen-lockfile` [chủ: task này]
- Khóa dịch vi và en (`apps/web-student/src/messages/messages.test.ts`, `source-scan.test.ts`) → `pnpm --filter @thi360/web-student test src/messages` [chủ: task này]
- Đường dẫn cần phiên theo luật mặc định (`apps/web-student/src/proxy.ts`) → task không sửa; `pnpm --filter @thi360/web-student test src/proxy.test.ts` [chủ: task này]
- Thành phần câu hỏi và chỗ cắm trình soạn (`@thi360/ui/question`) → `pnpm typecheck`; đổi API qua Advisor [chủ: FS-4]
- Đồng hồ thử cần backend bật profile `e2e`, cấu hình mặc định chỉ có `api,worker,dev` (`e2e/playwright.config.ts:5-6`, `.github/workflows/e2e.yml:66`) → chạy với `E2E_BACKEND_CMD` có profile `e2e`; đổi cấu hình CI [chủ: C-0b]
- Nút Bắt đầu và Tiếp tục ở `/home` trỏ vào trang của task (`apps/web-student/src/features/my-exams/**`) → `pnpm --filter @thi360/e2e test tests/my-exams.spec.ts` [chủ: FS-3]
- Màn đã đóng chuyển sang `/attempts/<id>/result` → trang đích do FS-6 làm; `attempt-flow.spec.ts` (mới) chỉ kiểm đường dẫn nếu FS-6 chưa merge [chủ: FS-6]
- Kiểu API lượt thi (`packages/api-client/src/schema.d.ts`) → lane web không sinh lại; `pnpm typecheck` sau khi rebase [chủ: AT-1, AT-2a, AT-2b, AT-3]
- FA-4 dùng lại thành phần màn làm bài cho làm thử → nếu tách thành phần dùng chung thì báo Advisor [chủ: FA-4]

**Kế hoạch kiểm thử:**

- AC-1: `prepare-view.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-prepare/prepare-view.test.tsx`; `attempt-flow.spec.ts` ca quyền của tài khoản khác, E2E, `pnpm --filter @thi360/e2e test tests/attempt-flow.spec.ts`
- AC-2: `prepare-view.test.tsx` ca chọn mã và định hướng, unit, `pnpm --filter @thi360/web-student test src/features/attempt-prepare/prepare-view.test.tsx`
- AC-3: `preparing-screen.test.tsx` bằng đồng hồ giả, unit, `pnpm --filter @thi360/web-student test src/features/attempt-prepare/preparing-screen.test.tsx`
- AC-4: `preparing-screen.test.tsx` ca `ABORTED`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-prepare/preparing-screen.test.tsx`
- AC-5: `prepare-view.test.tsx` ca `STARTING`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-prepare/prepare-view.test.tsx`
- AC-6: `prepare-view.test.tsx` ca mã lỗi, unit, `pnpm --filter @thi360/web-student test src/features/attempt-prepare/prepare-view.test.tsx`
- AC-7: `attempt-player.test.tsx` ca chuyển phần, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/attempt-player.test.tsx`
- AC-8: `question-nav.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/question-nav.test.tsx`
- AC-9: `attempt-flow.spec.ts` ca HSA FULL, E2E, `pnpm --filter @thi360/e2e test tests/attempt-flow.spec.ts`
- AC-10: `part-choice.test.tsx` ca HSA, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/part-choice.test.tsx`
- AC-11: `part-choice.test.tsx` ca QDA, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/part-choice.test.tsx`
- AC-12: `part-choice.test.tsx` ca sinh lỗi và mã lỗi, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/part-choice.test.tsx`
- AC-13: `attempt-flow.spec.ts` ca BCA, E2E, `pnpm --filter @thi360/e2e test tests/attempt-flow.spec.ts`
- AC-14: `attempt-resilience.spec.ts` ca đổi giờ máy bằng `page.clock`, E2E, `pnpm --filter @thi360/e2e test tests/attempt-resilience.spec.ts`
- AC-15: `save-queue.test.ts` bằng đồng hồ giả, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/save-queue.test.ts`
- AC-16: `save-queue.test.ts` ca trạng thái lưu, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/save-queue.test.ts`; `question-nav.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/question-nav.test.tsx`
- AC-17: `attempt-resilience.spec.ts` ca mất mạng bằng `context.setOffline`, E2E, `pnpm --filter @thi360/e2e test tests/attempt-resilience.spec.ts`; `save-queue.test.ts` ca thời gian chờ tăng dần, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/save-queue.test.ts`
- AC-18: `attempt-resilience.spec.ts` ca đóng tab khi mất mạng, E2E, `pnpm --filter @thi360/e2e test tests/attempt-resilience.spec.ts`
- AC-19: `attempt-resilience.spec.ts` ca chặn phản hồi `submit`, E2E, `pnpm --filter @thi360/e2e test tests/attempt-resilience.spec.ts`
- AC-20: `attempt-player.test.tsx` ca xác nhận nộp, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/attempt-player.test.tsx`
- AC-21: `attempt-resilience.spec.ts` ca hai ngữ cảnh, E2E, `pnpm --filter @thi360/e2e test tests/attempt-resilience.spec.ts`
- AC-22: `attempt-resilience.spec.ts` ca hai tab, E2E, `pnpm --filter @thi360/e2e test tests/attempt-resilience.spec.ts`
- AC-23: `attempt-resilience.spec.ts` ca rời trang, E2E, `pnpm --filter @thi360/e2e test tests/attempt-resilience.spec.ts`
- AC-24: `attempt-resilience.spec.ts` ca đăng xuất và đổi tài khoản, đọc IndexedDB thật, E2E, `pnpm --filter @thi360/e2e test tests/attempt-resilience.spec.ts`
- AC-25: `attempt-resilience.spec.ts` ca phiên hết hạn, E2E, `pnpm --filter @thi360/e2e test tests/attempt-resilience.spec.ts`
- AC-26: `attempt-essay-perf.spec.ts` với `Emulation.setCPUThrottlingRate` bằng 4, hiệu năng, `E2E_USE_BUILT=1 pnpm --filter @thi360/e2e test tests/attempt-essay-perf.spec.ts`
- AC-27: `attempt-essay-perf.spec.ts` ca ghi lại JS đã tải, E2E, `E2E_USE_BUILT=1 pnpm --filter @thi360/e2e test tests/attempt-essay-perf.spec.ts`; quét bản build, tích hợp, `pnpm --filter @thi360/web-student build && grep -rlE "MathfieldElement|mathlive" apps/web-student/.next/static/chunks | wc -l` ra `0`
- AC-28: `attempt-player.test.tsx` bảng các bước khôi phục, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/attempt-player.test.tsx`
- AC-29: `attempt-player.test.tsx` ca phím tắt, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/attempt-player.test.tsx`
- AC-30: `edit-session.test.ts` bằng đồng hồ giả, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/edit-session.test.ts`
- AC-31: `attempt-assets.test.ts`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/attempt-assets.test.ts`
- AC-32: `attempt-visual.spec.ts`, giao diện, `pnpm --filter @thi360/e2e test tests/attempt-visual.spec.ts`
- AC-33: `attempt-flow.spec.ts` "THPTQG chuyển tự do", E2E, `pnpm --filter @thi360/e2e test tests/attempt-flow.spec.ts`
- AC-34: `part-navigation.test.tsx` "theo navigationMode", unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/part-navigation.test.tsx`
- AC-35: `submit-confirm.test.tsx` "chưa đủ số chữ", unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/submit-confirm.test.tsx`
- Hồi quy: `pnpm lint && pnpm typecheck && pnpm test && pnpm build`; `pnpm --filter @thi360/web-student test src/features/session/forbidden-view.test.tsx src/features/auth/change-password-view.test.tsx src/providers/query-provider.test.tsx src/proxy.test.ts src/messages`; `pnpm --filter @thi360/e2e test tests/sso.spec.ts`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: bài HSA FULL thu nhỏ, BCA thu nhỏ, SPT Tin học, QDA lấy từ tệp hằng số của DS; mỗi ca E2E tạo một học viên mới có hậu tố riêng qua `POST /api/v1/admin/users` và lấy quyền bằng API mua qua cổng giả lập của CM-3a. Tua giờ bằng `POST /api/v1/test/clock` (profile `e2e`); E2E chạy một worker và lane có compose project riêng (A6), nên đồng hồ không ảnh hưởng lane khác.
- Không kiểm: lớp bọc IndexedDB không có unit test vì jsdom không có IndexedDB và B7 không thêm thư viện: kiểm trên trình duyệt thật ở AC-17, AC-18, AC-24, AC-25. Trình duyệt ngoài Chromium: dự án chỉ chạy E2E trên Chromium. Quy tắc `seq` phía máy chủ (AT-3). Ngưỡng 200 ms của AC-26 là ngưỡng đo cho câu "không trễ thấy được", đặc tả không ghi số.


## FS-6 · Kết quả, xem lại, lịch sử

**Spec:** BS 3, 5, 15; LT 9.2 (nhãn "Đang chấm lại"), 10, 11, F14, F15, F16, F20.

**Thiết kế:** `docs/design/dot-3/`, canvas trang 14: `KetQuaLuot`, `KetQuaChoCham`, `XemLaiBaiLam`, `LichSuLamBai`. Trang 14 chỉ vẽ nền sáng: chế độ tối kiểm theo token `DESIGN.md`.

**Sản phẩm:**

- `/attempts/[id]/result` (hỏi lại khi đang chấm).
- `/attempts/[id]/review`, dùng thành phần câu hỏi của FS-4 ở chế độ `review`; số câu chạy suốt bài (BS 5); ảnh trong lời giải tải qua đường `solution-assets` (BS 15).
- `/history` (bộ lọc trên URL).
- V-ACT hiện "Điểm luyện tập" trên thang của đường đã làm, ghi chú "Không phải điểm V-ACT chính thức", số câu đúng và tỷ lệ đúng của 4 thành phần; Tư duy khoa học mở ra xem riêng hai tiểu mục 12 và 18 câu (BS 3).
- Ghi chú lượt hỏng F20 ("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt") và nhãn "Đang chấm lại" kèm điểm cũ, ở kết quả và lịch sử.
- Bỏ dòng ẩn của mục Lịch sử ở thanh đầu trang.

**Đường dẫn sở hữu:**

- `apps/web-student/src/app/attempts/[id]/result/**` (mới)
- `apps/web-student/src/app/attempts/[id]/review/**` (mới)
- `apps/web-student/src/app/history/**` (mới)
- `apps/web-student/src/features/attempt-result/**` (mới)
- `apps/web-student/src/features/attempt-review/**` (mới)
- `apps/web-student/src/features/attempt-history/**` (mới)
- thêm vào: `apps/web-student/src/components/student-header.tsx`, `apps/web-student/src/components/student-header.test.tsx` (bỏ dòng ẩn Lịch sử)
- thêm vào: `apps/web-student/src/messages/{vi,en}.json` (khối `result`, `review`, `history`)
- `e2e/tests/attempt-result.spec.ts` (mới), `e2e/tests/attempt-result-visual.spec.ts` (mới)

**Ngoài phạm vi:** API kết quả, xem lại, lịch sử (AT-4). Đánh dấu lượt hỏng, cấp bù, chấm lại (AT-5, FA-4). Thành phần câu hỏi (FS-4). Màn làm bài (FS-5). Gửi email bảng điểm không có (LT 10.1).

**Nghiệm thu:**

- AC-1: V-ACT có nhãn "Điểm luyện tập", ghi chú "Không phải điểm V-ACT chính thức" và số câu đúng theo 4 thành phần, không hiện điểm thành phần — bằng chứng: `result-view.test.tsx` (mới).
- AC-2: Thang V-ACT lấy từ dữ liệu kết quả: 1.200 với bài FULL, 600, 300, 180, 120 với bài riêng phần; mã nguồn không viết cứng 1.200 — bằng chứng: `result-view.test.tsx` (mới).
- AC-3: Chờ chấm thì không hiện điểm tổng, hiện "Chờ chấm"; phần hoặc môn không còn câu chờ chấm hiện điểm riêng, ghi là điểm của phần đó — bằng chứng: `result-view.test.tsx` (mới).
- AC-4: Điểm 0 chỉ hiện khi lượt đã có điểm (`SCORED` hoặc đang chấm lại) và thật sự được 0, ở cả kết quả và lịch sử — bằng chứng: `result-view.test.tsx` (mới), `history-view.test.tsx` (mới).
- AC-5: Đang chấm thì hỏi lại mỗi 3 giây, tối đa 2 phút, sau đó hiện "Đang tính điểm, bạn có thể quay lại sau" — bằng chứng: `result-view.test.tsx` (mới).
- AC-6: `GRADING_ERROR` hiện "Bài làm đã được ghi nhận, kết quả đang được xử lý", không hiện điểm 0 — bằng chứng: `result-view.test.tsx` (mới).
- AC-7: Thống kê đủ 5 nhóm Đúng, Đúng một phần, Sai, Bỏ trống, Chờ chấm; điểm thành phần theo phần, môn đã làm; hiện lựa chọn của lượt (nhánh, môn, mã, định hướng) — bằng chứng: `result-view.test.tsx` (mới).
- AC-8: Phần `SKIPPED` ghi lý do, ví dụ "Không làm phần 3: quá thời gian chọn" — bằng chứng: `result-view.test.tsx` (mới).
- AC-9: `SCORE` không có nút Xem lại — bằng chứng: `result-view.test.tsx` (mới).
- AC-10: Gọi thẳng URL xem lại khi API trả 403 `REVIEW_NOT_INCLUDED` thì hiện thông báo kèm nút về kết quả, không lỗi trắng — bằng chứng: `review-view.test.tsx` (mới), `e2e/tests/attempt-result.spec.ts` (mới).
- AC-11: `SCORE_AND_ANSWERS` hiện câu hỏi, câu trả lời, điểm từng câu và từng ý, đáp án đúng, đúng thứ tự phương án của lượt; `FULL_SOLUTION` thêm lời giải, tự luận thêm điểm từng tiêu chí và nhận xét — bằng chứng: `review-view.test.tsx` (mới).
- AC-12: Lượt đang `PENDING_REVIEW` chỉ xem lại các câu chấm tự động — bằng chứng: `review-view.test.tsx` (mới).
- AC-13: Lượt FULL là một dòng ở lịch sử; bài theo phần, theo môn làm riêng là dòng riêng; bấm dòng mở đúng kết quả của lượt đó — bằng chứng: `attempt-result.spec.ts` (mới), `history-view.test.tsx` (mới).
- AC-14: Lịch sử rỗng hiện "Chưa có lượt thi phù hợp" kèm Xóa bộ lọc — bằng chứng: `history-view.test.tsx` (mới).
- AC-15: Lịch sử lỗi tải hiện thông báo và nút Thử lại, không hiện trạng thái rỗng; đang tải có khung giữ chỗ — bằng chứng: `history-view.test.tsx` (mới).
- AC-16: Tìm theo tên, lọc kỳ thi, phạm vi, khoảng ngày nằm trên URL, còn sau khi quay lại; `/history?examId=…` lọc đúng bài — bằng chứng: `attempt-result.spec.ts` (mới).
- AC-17: Lượt của tài khoản khác ở kết quả và xem lại hiện trang không tìm thấy — bằng chứng: `attempt-result.spec.ts` (mới).
- AC-18: Lượt hỏng có ghi chú "Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt" ở kết quả và lịch sử; kết quả vẫn xem được — bằng chứng: `result-view.test.tsx` (mới), `history-view.test.tsx` (mới).
- AC-19: `regrading = true` hiện điểm cũ kèm nhãn "Đang chấm lại" ở kết quả và lịch sử — bằng chứng: `result-view.test.tsx` (mới), `history-view.test.tsx` (mới).
- AC-20: Mục Lịch sử hiện trên thanh đầu trang khi đã đăng nhập — bằng chứng: `apps/web-student/src/components/student-header.test.tsx`.
- AC-21: Các màn của task đúng khi sáng và tối, vi và en, 390px và 1280px: không tràn ngang, tương phản ≥ 4,5, không lẫn ngôn ngữ — bằng chứng: `e2e/tests/attempt-result-visual.spec.ts` (mới).
- AC-22: Tư duy khoa học mở ra hai tiểu mục 12 và 18 câu, dùng được bằng bàn phím; kết quả không có `subsections` thì không có nút mở — bằng chứng: `thinking-subsections.test.tsx` "tiểu mục" (mới)
- AC-23: Màn xem lại của quyền lợi `FULL_SOLUTION` hiện được ảnh trong lời giải; quyền lợi thấp hơn không gọi đường `solution-assets` — bằng chứng: `attempt-result.spec.ts` "ảnh lời giải" (mới)

**Ảnh hưởng ngoài task:**

- Danh sách ẩn của thanh đầu trang (`apps/web-student/src/components/student-header.tsx`) → `pnpm --filter @thi360/web-student test src/components/student-header.test.tsx` [chủ: task này]
- Khóa dịch vi và en (`apps/web-student/src/messages/messages.test.ts`, `source-scan.test.ts`) → `pnpm --filter @thi360/web-student test src/messages` [chủ: task này]
- Đường dẫn cần phiên theo luật mặc định (`apps/web-student/src/proxy.ts`) → task không sửa; `pnpm --filter @thi360/web-student test src/proxy.test.ts` [chủ: task này]
- Trang `/attempts/[id]` cùng cây thư mục với FS-5 (`apps/web-student/src/app/attempts/[id]/page.tsx`) → FS-5 không đặt `layout.tsx` ở `[id]`, FS-6 chỉ thêm `result/`, `review/`; `attempt-result-visual.spec.ts` (mới) thấy thanh đầu trang ở hai trang này [chủ: FS-5]
- Bài có `result_view = SCORE` phải có trong bộ dữ liệu (tệp hằng số của DS) → AC-10 trong `attempt-result.spec.ts` (mới) dùng bài này [chủ: DS]
- Thành phần câu hỏi chế độ `review` (`@thi360/ui/question`) → `pnpm typecheck`; đổi API qua Advisor [chủ: FS-4]
- Nút Xem kết quả và Lịch sử ở `/home` trỏ vào trang của task (`apps/web-student/src/features/my-exams/**`) → `pnpm --filter @thi360/e2e test tests/my-exams.spec.ts` [chủ: FS-3]
- Màn làm bài chuyển sang trang kết quả khi lượt đóng → `pnpm --filter @thi360/e2e test tests/attempt-flow.spec.ts` [chủ: FS-5]
- Dữ liệu `regrading`, lượt hỏng và đã cấp bù trong API kết quả, lịch sử (`packages/api-client/src/schema.d.ts`) → lane web không sinh lại; `pnpm typecheck` sau khi rebase [chủ: AT-4, AT-5]
- Luồng chấm lại và đánh dấu hỏng từ web admin tới màn học viên → kịch bản đầu cuối của FA-4: chấm lại rồi học viên thấy nhãn "Đang chấm lại"; đánh dấu hỏng, cấp bù rồi học viên thấy ghi chú F20 [chủ: FA-4]
- Giới hạn dung lượng bundle: không có: đã tìm `size-limit|bundlesize|bundle-analyzer|budget` trong `.github/workflows`, `scripts`, `package.json`, `apps/web-student/package.json`, `apps/web-student/next.config.ts` → `pnpm --filter @thi360/web-student build` vẫn qua

**Kế hoạch kiểm thử:**

- AC-1: `result-view.test.tsx` ca V-ACT, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx`
- AC-2: `result-view.test.tsx` bảng thang điểm theo đường, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx`
- AC-3: `result-view.test.tsx` ca chờ chấm, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx`
- AC-4: `result-view.test.tsx` ca điểm 0, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx`; `history-view.test.tsx` ca điểm 0, unit, `pnpm --filter @thi360/web-student test src/features/attempt-history/history-view.test.tsx`
- AC-5: `result-view.test.tsx` nhịp hỏi lại bằng đồng hồ giả, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx`
- AC-6: `result-view.test.tsx` ca `GRADING_ERROR`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx`
- AC-7: `result-view.test.tsx` ca thống kê, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx`
- AC-8: `result-view.test.tsx` ca `SKIPPED`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx`
- AC-9: `result-view.test.tsx` ca `SCORE`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx`
- AC-10: `review-view.test.tsx` ca 403, unit, `pnpm --filter @thi360/web-student test src/features/attempt-review/review-view.test.tsx`; `attempt-result.spec.ts` ca mở thẳng URL, E2E, `pnpm --filter @thi360/e2e test tests/attempt-result.spec.ts`
- AC-11: `review-view.test.tsx` ca hai mức xem, unit, `pnpm --filter @thi360/web-student test src/features/attempt-review/review-view.test.tsx`
- AC-12: `review-view.test.tsx` ca `PENDING_REVIEW`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-review/review-view.test.tsx`
- AC-13: `attempt-result.spec.ts` ca một dòng và mở kết quả, E2E, `pnpm --filter @thi360/e2e test tests/attempt-result.spec.ts`; `history-view.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/attempt-history/history-view.test.tsx`
- AC-14: `history-view.test.tsx` ca rỗng, unit, `pnpm --filter @thi360/web-student test src/features/attempt-history/history-view.test.tsx`
- AC-15: `history-view.test.tsx` ca lỗi và đang tải, unit, `pnpm --filter @thi360/web-student test src/features/attempt-history/history-view.test.tsx`
- AC-16: `attempt-result.spec.ts` ca bộ lọc, E2E, `pnpm --filter @thi360/e2e test tests/attempt-result.spec.ts`
- AC-17: `attempt-result.spec.ts` ca tài khoản khác, E2E, `pnpm --filter @thi360/e2e test tests/attempt-result.spec.ts`
- AC-18: `result-view.test.tsx`, `history-view.test.tsx` ca lượt hỏng, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx src/features/attempt-history/history-view.test.tsx`
- AC-19: `result-view.test.tsx`, `history-view.test.tsx` ca đang chấm lại, unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/result-view.test.tsx src/features/attempt-history/history-view.test.tsx`
- AC-20: `student-header.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/components/student-header.test.tsx`
- AC-21: `attempt-result-visual.spec.ts`, giao diện, `pnpm --filter @thi360/e2e test tests/attempt-result-visual.spec.ts`
- AC-22: `thinking-subsections.test.tsx` "tiểu mục", unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/thinking-subsections.test.tsx`
- AC-23: `attempt-result.spec.ts` "ảnh lời giải", E2E, `pnpm --filter @thi360/e2e test tests/attempt-result.spec.ts`
- Hồi quy: `pnpm lint && pnpm typecheck && pnpm test && pnpm build`; `pnpm --filter @thi360/web-student test src/components/student-header.test.tsx src/proxy.test.ts src/messages`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: bài của DS theo tệp hằng số, gồm `VACT_SUBJECT_TOAN`, `VACT_PART_LANGUAGE` và bài có `SCORE`; mỗi ca E2E tạo một học viên mới có hậu tố riêng qua `POST /api/v1/admin/users`, lấy quyền bằng API mua qua cổng giả lập của CM-3a, làm và nộp bài qua API lượt thi. Các trạng thái khó dựng (đang chấm lại, lượt hỏng, `GRADING_ERROR`, thang của từng đường) dùng API giả trong vitest.
- Không kiểm: E2E "Đang chấm lại" và ghi chú F20 ở task này, vì cần API của AT-5 mà FS-6 không phụ thuộc: kiểm bằng vitest, luồng đầu cuối do FA-4 kiểm. V-ACT FULL 120 câu không chạy E2E vì không có bản thu nhỏ (D-l): thang 1.200 kiểm bằng vitest. Cách tính điểm và thống kê phía máy chủ (AT-4).

**Câu hỏi mở:** F20 ghi chú "đã cấp bù 1 lượt", nhưng đánh dấu hỏng và cấp bù là hai thao tác riêng (LT 12). Lượt đã đánh dấu mà chưa cấp bù thì hiện gì: tạm chỉ hiện ghi chú khi đã cấp bù; M.Trường quyết.

## FS-7 · Audio, phân đoạn, nhập ký hiệu và kết quả của H-SCA, V-SAT

**Spec:** BS 17; 2B bổ sung H-SCA, V-SAT mục 7; plan B7, B8.

**Thiết kế:** chưa có bản vẽ riêng cho trình phát và tiêu đề phân đoạn: lane vẽ theo token `DESIGN.md`, gửi ảnh để Advisor duyệt, rồi cập nhật `DESIGN.md`.

**Sản phẩm:**

- Trình phát audio dùng chung trong `packages/ui`: chỉ có nút Nghe, số lần nghe còn lại, thời gian đã nghe; không có thanh tua, đổi tốc độ, tạm dừng. Mỗi lần bấm Nghe gọi `plays` với một `requestId` mới, gửi lại khi mất phản hồi dùng cùng `requestId`. `progress` mỗi 10 giây, `end` khi hết tệp. Tải lại trang, mất mạng, trình duyệt từ chối phát, `AUDIO_PLAY_IN_PROGRESS` thì gọi `resume`; trình phát không bao giờ tự mở lần nghe mới.
- Trình phát dừng ngay khi phần đóng, nộp bài, hết giờ hoặc phiên bị tiếp quản, kể cả khi tệp đã tải xong. Mất mạng thì hiện trạng thái chờ, có mạng lại thì phát tiếp. Trình duyệt từ chối phát thì hiện nút phát lại, không mất lần nghe. Gián đoạn lâu bao nhiêu, khi phần còn mở thì quay lại vẫn phát tiếp lần nghe dở. Nhận `PART_CLOSED` hoặc `TIME_UP` thì dừng phát và khóa nút Nghe kèm lý do.
- Màn chuẩn bị hiện số câu, phạm vi, thời lượng, định dạng đáp án và chính sách nghe (BS 17.1).
- Màn làm bài chèn tiêu đề phân đoạn trước câu `fromPosition`, số câu liền nhau, chùm bị cắt ở ranh giới vẫn xem được ngữ liệu ở cả hai phân đoạn.
- Câu trả lời ngắn hiện đơn vị, yêu cầu làm tròn, cách nhập từ `inputHint`. Thanh chèn ký hiệu cho câu ion và chuỗi ký hiệu trên điện thoại.
- Màn kết quả có điểm theo phân đoạn, trạng thái "Đúng một phần", điểm đạt trên điểm tối đa của phạm vi đã làm. Xem lại có "Đúng một phần".
- Màn chờ sinh đề hiện lý do thiếu tệp audio và nút Thử lại.

**Đường dẫn sở hữu:**

- `packages/ui/src/question/audio-player/**` (mới)
- `packages/ui/src/question/symbol-input/**` (mới)
- `apps/web-student/src/features/attempt-player/audio/**` (mới), `apps/web-student/src/features/attempt-player/segments/**` (mới)
- thêm vào: `apps/web-student/src/features/attempt-prepare/**`, `apps/web-student/src/features/attempt-result/**`, `apps/web-student/src/features/attempt-review/**`
- thêm vào: `packages/ui/src/messages/ui-strings.ts`, `apps/web-student/src/messages/{vi,en}.json` (khối `audio`, `segments`)
- `e2e/tests/attempt-hsca-vsat.spec.ts` (mới), `e2e/tests/attempt-hsca-vsat-visual.spec.ts` (mới)

**Ngoài phạm vi:** API audio và kiểm tệp (AT-6). Chấm và `segments[]` (AT-7). Band điểm, phân tích MAPID, AI giải thích (BS 18). Ứng dụng di động. Trình phát ở màn làm thử của admin (FA-5).

**Nghiệm thu:**

- AC-1: Trình phát không có thanh tua, đổi tốc độ, tạm dừng; hiện đúng số lần còn lại; hết lần nghe thì nút Nghe bị khóa kèm lý do; bấm Nghe hai lần liên tiếp chỉ gửi một `requestId` — bằng chứng: `audio-player.test.tsx` "chính sách nghe" (mới)
- AC-2: Tải lại khi đang phát: gọi `resume` với cùng `playId`, phát từ `positionMs`; `AUDIO_PLAY_IN_PROGRESS` thì chuyển sang `resume`; `AUDIO_SEEK_REJECTED` thì trở về vị trí máy chủ trả; `AUDIO_PLAY_STALE` thì đọc lại trạng thái, không gửi tiếp sự kiện cũ — bằng chứng: `audio-player.test.tsx` "khôi phục" (mới)
- AC-3: `audio.play()` bị trình duyệt từ chối bốn lần liên tiếp: mỗi lần hiện nút phát lại, bấm lại gọi `resume`, không gọi `plays`, số lần nghe không tăng — bằng chứng: `audio-player.test.tsx` "trình duyệt từ chối phát" (mới)
- AC-4: Quay lại sau gián đoạn dài khi phần còn mở: gọi `resume`, không gọi `plays`, phát từ vị trí đã lưu; nhận `PART_CLOSED` hoặc `TIME_UP`: dừng phát, khóa nút Nghe kèm lý do — bằng chứng: `audio-player.test.tsx` "gián đoạn dài và phần đóng" (mới)
- AC-5: H-SCA Tiếng Anh: nghe hai lần, lần thứ ba bị khóa, tải lại trang bốn lần giữa chừng không mất lần nghe, nộp bài và xem kết quả — bằng chứng: `attempt-hsca-vsat.spec.ts` "nghe hai lần" (mới)
- AC-6: Mất mạng khi đang nghe rồi có mạng lại: phát tiếp từ vị trí đã lưu, số lần nghe không đổi — bằng chứng: `attempt-hsca-vsat.spec.ts` "mất mạng khi nghe" (mới)
- AC-7: Hết giờ, nộp bài, hoặc thiết bị khác tiếp quản khi tệp đã tải xong: thời gian phát dừng tăng ngay — bằng chứng: `attempt-hsca-vsat.spec.ts` "dừng phát" (mới)
- AC-8: Màn chuẩn bị hiện đủ số câu, phạm vi, thời lượng, định dạng đáp án; có chính sách nghe khi đề có audio, không có khi đề không có audio — bằng chứng: `prepare-hsca-vsat.test.tsx` "màn chuẩn bị" (mới)
- AC-9: Tiêu đề phân đoạn đứng trước đúng câu `fromPosition`, số câu không đánh lại; chùm cắt ở ranh giới hiện ngữ liệu ở cả hai phân đoạn; không có đồng hồ theo phân đoạn — bằng chứng: `segments.test.tsx` "phân đoạn" (mới)
- AC-10: Câu trả lời ngắn hiện `inputHint`; thanh ký hiệu chèn đúng ký tự tại con trỏ và giá trị gửi đi khớp cách viết của đáp án — bằng chứng: `symbol-input.test.tsx` "chèn ký hiệu" (mới)
- AC-11: Ở khung 390px, trên bài H-SCA Hóa học của DS, nhập đáp án ion bằng thanh ký hiệu và kết quả của câu đó là Đúng theo điểm máy chủ trả — bằng chứng: `attempt-hsca-vsat.spec.ts` "nhập ion trên điện thoại" (mới)
- AC-12: Kết quả hiện điểm từng phân đoạn, nhãn "Đúng một phần", điểm đạt trên điểm tối đa; kết quả không có `segments` thì không có khối phân đoạn — bằng chứng: `segment-result.test.tsx` "kết quả phân đoạn" (mới)
- AC-13: V-SAT Tiếng Anh: làm hết 25 câu với tiêu đề Phần I–IV, kết quả theo phân đoạn cộng đúng tổng — bằng chứng: `attempt-hsca-vsat.spec.ts` "V-SAT phân đoạn" (mới)
- AC-14: Bật tình huống thiếu tệp qua `/api/v1/test/audio-faults/{examId}`: lượt bị hủy hiện lý do và nút Thử lại, lượt được trả; tắt tình huống thì Thử lại vào được bài — bằng chứng: `attempt-hsca-vsat.spec.ts` "thiếu tệp audio" (mới)
- AC-15: Màn chuẩn bị, làm bài, kết quả của H-SCA, V-SAT đạt ở sáng và tối, vi và en, 390px và 1280px, không lẫn ngôn ngữ — bằng chứng: `attempt-hsca-vsat-visual.spec.ts` "ma trận giao diện" (mới)

**Ảnh hưởng ngoài task:**

- Thành phần trong `packages/ui` được web-admin dùng ở FA-5 → `trial-audio.test.tsx` của FA-5 kiểm trình phát ở chế độ không gọi API [chủ: FA-5]
- Màn làm bài của FS-5 thêm trình phát và phân đoạn → chạy lại `attempt-flow.spec.ts`, `attempt-resilience.spec.ts` [chủ: task này]
- Màn kết quả của FS-6 thêm khối phân đoạn → chạy lại `attempt-result.spec.ts` [chủ: task này]
- Tệp thông điệp chung `messages/{vi,en}.json` → `pnpm --filter @thi360/web-student test` [chủ: task này]

**Kế hoạch kiểm thử:**

- AC-1: `audio-player.test.tsx` "chính sách nghe", unit, `pnpm --filter @thi360/ui test src/question/audio-player/audio-player.test.tsx`
- AC-2: `audio-player.test.tsx` "khôi phục", unit, `pnpm --filter @thi360/ui test src/question/audio-player/audio-player.test.tsx`
- AC-3: `audio-player.test.tsx` "trình duyệt từ chối phát", unit, `pnpm --filter @thi360/ui test src/question/audio-player/audio-player.test.tsx`
- AC-4: `audio-player.test.tsx` "gián đoạn dài và phần đóng", unit, `pnpm --filter @thi360/ui test src/question/audio-player/audio-player.test.tsx`
- AC-5: `attempt-hsca-vsat.spec.ts` "nghe hai lần", E2E, `pnpm --filter @thi360/e2e test tests/attempt-hsca-vsat.spec.ts`
- AC-6: `attempt-hsca-vsat.spec.ts` "mất mạng khi nghe", E2E, `pnpm --filter @thi360/e2e test tests/attempt-hsca-vsat.spec.ts`
- AC-7: `attempt-hsca-vsat.spec.ts` "dừng phát", E2E, `pnpm --filter @thi360/e2e test tests/attempt-hsca-vsat.spec.ts`
- AC-8: `prepare-hsca-vsat.test.tsx` "màn chuẩn bị", unit, `pnpm --filter @thi360/web-student test src/features/attempt-prepare/prepare-hsca-vsat.test.tsx`
- AC-9: `segments.test.tsx` "phân đoạn", unit, `pnpm --filter @thi360/web-student test src/features/attempt-player/segments/segments.test.tsx`
- AC-10: `symbol-input.test.tsx` "chèn ký hiệu", unit, `pnpm --filter @thi360/ui test src/question/symbol-input/symbol-input.test.tsx`
- AC-11: `attempt-hsca-vsat.spec.ts` "nhập ion trên điện thoại", E2E, `pnpm --filter @thi360/e2e test tests/attempt-hsca-vsat.spec.ts`
- AC-12: `segment-result.test.tsx` "kết quả phân đoạn", unit, `pnpm --filter @thi360/web-student test src/features/attempt-result/segment-result.test.tsx`
- AC-13: `attempt-hsca-vsat.spec.ts` "V-SAT phân đoạn", E2E, `pnpm --filter @thi360/e2e test tests/attempt-hsca-vsat.spec.ts`
- AC-14: `attempt-hsca-vsat.spec.ts` "thiếu tệp audio", E2E, `pnpm --filter @thi360/e2e test tests/attempt-hsca-vsat.spec.ts`
- AC-15: `attempt-hsca-vsat-visual.spec.ts` "ma trận giao diện", E2E, `pnpm --filter @thi360/e2e test tests/attempt-hsca-vsat-visual.spec.ts`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - `pnpm --filter @thi360/ui test`; `pnpm --filter @thi360/web-student test`
  - `pnpm --filter @thi360/e2e test tests/attempt-flow.spec.ts tests/attempt-resilience.spec.ts tests/attempt-result.spec.ts`
- Kiểm thử rủi ro:
  - gửi lặp và đồng thời: bấm Nghe hai lần liên tiếp chỉ mở một lần nghe; gửi lại `plays` sau khi mất phản hồi; `progress` đến muộn sau khi đã sang lần nghe mới;
  - biên: lần nghe cuối cùng, audio ngắn hơn chu kỳ `progress`, hết giờ đúng lúc đang tải tệp;
  - mất phiên: bị tiếp quản khi đang nghe, phiên đăng nhập hết hạn khi đang nghe;
  - hợp đồng dùng chung: trình phát chạy được không có API lượt thi cho FA-5;
  - đỏ trước: AC-1, AC-2, AC-3, AC-4, AC-5, AC-7.
- Dữ liệu test: bài H-SCA Tiếng Anh, Toán và V-SAT của DS và bài H-SCA Hóa học; tài khoản học viên riêng có hậu tố; quyền dùng lấy bằng cách mua qua cổng giả lập; tình huống thiếu tệp bật, tắt qua đường test của AT-6, tắt lại trong bước dọn của ca.
- Không kiểm: chất lượng âm thanh trên thiết bị thật; ứng dụng di động.

## FA-1 · Tab Thông tin bán, cảnh báo chấm tay, menu

**Spec:** BS 16; TM 6.3, 6.4; LT 9.3 (mục AC 12.08 của đặc tả); plan B8. **Phụ thuộc mềm:** DS.

**Thiết kế:** `docs/design/dot-3/`, canvas trang 15: `BaiThiThongTinBan`, `BaiThiKiemTraThuongMai`; menu theo các màn admin của trang 15–16.

**Sản phẩm:**

- Tab "Thông tin bán" tại `/exams/[id]/listing`, giữa Cấu trúc và Kiểm tra: biểu mẫu theo TM 6.1, xem trước thẻ bằng giá gốc, dải "Giá đang bán … · Sẽ đổi khi công bố", tải ảnh bìa.
- Nhóm "Thông tin bán" ở tab Kiểm tra.
- Cảnh báo "Có câu chấm tay" (`MANUAL_GRADING_REQUIRED`): máy chủ và tab Kiểm tra đã hiện cảnh báo này từ đợt 2B (2B H18, FE-4). Lane này chỉ chỉnh câu chữ và vị trí theo bản vẽ `BaiThiKiemTraThuongMai`, không làm lại.
- Menu của đợt theo B8 và D-f: thêm toàn bộ mục một lần.
  - Nhóm "Thương mại": Khuyến mãi (`PROMOTION.VIEW`), Đơn hàng (`ORDER.VIEW`), Bộ đề (`EXAM.VIEW`, TM 16.4).
  - Nhóm mới "Học viên" chứa mục "Lượt thi" (`ATTEMPT.VIEW`) (BS 16).
  - Danh sách ẩn `HIDDEN_NAV_ITEMS` (mới) trong `lib/navigation.ts` gồm `promotions`, `orders`, `bundles`, `attempts`, áp dụng trong `filterNavigation` để sidebar và lối tắt ở Bảng điều khiển cùng ẩn. FA-2, FA-3, FA-4, FB chỉ bỏ dòng của mình.
- Đổi import bộ hiển thị của web-admin sang bản FS-4 đặt trong `packages/ui`, xóa bản cũ (B8, D-n). Trình soạn ở lại web-admin. Phân loại trong `apps/web-admin/src/components/rich-text/`:
  - **Xóa ở web-admin (đã có ở `packages/ui`):** `view/rich-text-view.tsx`, `math-render.ts`, `sanitize.ts`, `html-contract.ts`. Cả bốn tệp đều có mã trình soạn dùng, nên phần trình soạn đổi import sang `packages/ui`.
  - **Tách:** `rich-text.css`. Quy tắc của `.rich-text-body` và các danh sách chọn dùng chung sang `packages/ui`; ở lại web-admin: `.rich-text-editor-content`, `.rich-text-editor .ProseMirror*`, `.selectedCell`, `.rich-text-math`, `.rich-text-atom`, `.rich-text-blank`, `.math-block.rich-text-math`.
  - **Ở lại:** `rich-text-editor.tsx`, `toolbar/**`, `extensions/**`, `insert/**`, `index.ts` (chỉ còn phần xuất của trình soạn).
  - **Test chuyển theo mã:** `__tests__/rich-text-view.test.tsx`, `__tests__/math-render-mhchem.test.ts`, `__tests__/sanitize.test.ts` chỉ được xóa sau khi `packages/ui` có ca tương đương.
  - `katex` vẫn là phụ thuộc trực tiếp của web-admin vì trình soạn import thẳng (`rich-text-editor.tsx`, `insert/chemistry/dialog.tsx`, `insert/math/katex-span.tsx`, `insert/math/latex-check.ts`, `insert/word/formula-chip.tsx`); toàn repo giữ một bản 0.19.1 vì mhchem đăng ký macro lên đúng bản được import.
- Tab Kiểm tra đọc nhóm "Thông tin bán" từ trường `listingCheck` (EX-1), không qua `groups[]`; hộp thoại công bố hiện đúng lỗi `LISTING_*` (`publish-exam-dialog.tsx`); `COVER_IMAGE_MAX_BYTES` đổi còn 2 MB (`create-exam-schema.ts`); thêm nhãn `field.*` của các trường bán cho `useChangeWhere`.

**Đường dẫn sở hữu:**

- `apps/web-admin/src/app/(shell)/exams/[id]/listing/page.tsx` (mới)
- `apps/web-admin/src/features/exams/listing/**` (mới)
- `apps/web-admin/src/features/exams/editor-tabs.ts`
- `apps/web-admin/src/features/exams/review-types.ts`, `apps/web-admin/src/features/exams/check.ts`, `apps/web-admin/src/features/exams/components/exam-review-check.tsx`
- `apps/web-admin/src/lib/navigation.ts`, `apps/web-admin/src/lib/navigation.test.ts`
- `apps/web-admin/src/components/rich-text/**` (xóa, tách và đổi import như trên)
- Đổi import: `apps/web-admin/src/features/exams/components/exam-preview-paper.tsx`, `apps/web-admin/src/features/exams/create-exam-schema.ts`, `apps/web-admin/src/features/question-import/components/question-preview.tsx`, `apps/web-admin/src/features/questions/editor-schema.ts`, `apps/web-admin/src/features/questions/preview/question-player.tsx`, và trong `apps/web-admin/src/features/questions/components/`: `cloze-passage.tsx`, `question-detail-body.tsx`, `question-detail-content.tsx`, `question-editor-choice.tsx`, `question-editor-set.tsx`, `question-versions-compare.tsx`, `question-versions-view.tsx`
- `apps/web-admin/src/features/exams/__tests__/listing-*.test.tsx` (mới), `apps/web-admin/src/components/rich-text/__tests__/renderer-boundary.test.ts` (mới), `e2e/tests/exam-listing.spec.ts` (mới)
- thêm vào: `apps/web-admin/src/messages/vi.json`, `apps/web-admin/src/messages/en.json` (`exams.editor.tabs.listing`, `exams.listing.*`, nhóm kiểm tra Thông tin bán, `nav.groups.commerce`, `nav.groups.learners`, `nav.items.{promotions,orders,bundles,attempts}.{label,description}`)
- thêm vào: `e2e/tests/rbac.spec.ts`, `e2e/tests/admin-responsive.spec.ts` (chỉ khi khẳng định về menu phải đổi)

**Ngoài phạm vi:** API `listing`, `cover` và các lỗi `LISTING_*` (EX-1); bộ hiển thị trong `packages/ui` (FS-4); luật `MANUAL_GRADING_REQUIRED` ở máy chủ (đợt 2B); các trang Khuyến mãi (FA-2), Đơn hàng (FA-3), Lượt thi (FA-4), Bộ đề (FB); dữ liệu DS (DS); ẩn nút ghi ở các tab khác của bài thi (`DESIGN.md` 5.14 ghi chưa làm, đợt này không đổi).

**Nghiệm thu:**

- AC-1: Tab "Thông tin bán" nằm giữa Cấu trúc và Kiểm tra, mở được bằng URL `/exams/[id]/listing` — bằng chứng: `listing-tab.test.tsx`, `exam-listing.spec.ts` "thứ tự tab".
- AC-2: Thẻ xem trước đổi theo dữ liệu đang nhập và dùng giá gốc — bằng chứng: `listing-form.test.tsx` "xem trước thẻ".
- AC-3: Bản công bố khác bản làm việc thì hiện dải "Giá đang bán: … · Sẽ đổi khi công bố"; giống nhau hoặc chưa công bố thì không hiện — bằng chứng: `listing-form.test.tsx` "dải giá đang bán".
- AC-4: Lưu gửi `expectedRevision` đang có và nhận `revision` mới — bằng chứng: `exam-listing.spec.ts` "lưu thông tin bán".
- AC-5: Máy chủ trả 412 khi lưu thì nạp lại dữ liệu mới và giữ thông báo trên màn — bằng chứng: `exam-listing.spec.ts` "412 khi hai người cùng sửa".
- AC-6: 422 `EXAM_LISTING_INVALID` hiện lỗi đúng ô theo `fieldErrors` — bằng chứng: `listing-form.test.tsx` "lỗi theo ô".
- AC-7: Ảnh bìa sai định dạng (ngoài jpg, png, webp) hiện lỗi rõ và không đổi ảnh đang có; lỗi giới hạn do máy chủ trả cũng hiện rõ — bằng chứng: `listing-cover.test.tsx`, `exam-listing.spec.ts` "ảnh bìa sai định dạng".
- AC-8: Thiếu `EXAM.MODIFY` thì tab chỉ xem: mọi ô khóa, ẩn Lưu, Đổi ảnh, Bỏ ảnh; dải Giá đang bán và thẻ xem trước vẫn hiện — bằng chứng: `listing-form.test.tsx` "chỉ xem", `exam-listing.spec.ts` "vai trò tùy chỉnh chỉ có EXAM.VIEW".
- AC-9: Tab Kiểm tra có nhóm "Thông tin bán": lỗi `LISTING_PRICE_MISSING`, `LISTING_PRICE_ZERO`, `LISTING_PRICE_BELOW_MIN`, `LISTING_ATTEMPTS_MISSING`, `LISTING_DESCRIPTION_MISSING` hiện đúng nhóm; thiếu ảnh bìa chỉ là cảnh báo — bằng chứng: `listing-check-group.test.tsx`.
- AC-10: Cảnh báo chấm tay hiện đúng phần, môn, khối có câu tự luận với câu chữ "Có câu chấm tay" / "Bài có câu tự luận. Điểm tổng hiện sau khi người chấm chấm xong." ở vị trí của bản vẽ, kiểm bằng bài BCA FULL của DS — bằng chứng: `exam-listing.spec.ts` "cảnh báo chấm tay BCA FULL".
- AC-11: Bài chỉ còn cảnh báo chấm tay vẫn công bố được — bằng chứng: `exam-listing.spec.ts` "công bố khi có cảnh báo chấm tay".
- AC-12: Mỗi mục menu mới chỉ hiện khi có đúng quyền của mục, kể cả với vai trò tùy chỉnh — bằng chứng: `navigation.test.ts` "menu thương mại theo quyền".
- AC-13: Mục trong `HIDDEN_NAV_ITEMS` không hiện ở sidebar và lối tắt dù đủ quyền; nhóm không còn mục thì không hiện — bằng chứng: `navigation.test.ts` "danh sách ẩn".
- AC-14: web-admin không còn bản `RichTextView` riêng: bốn tệp ở mục Xóa không tồn tại và không còn import nào trỏ tới — bằng chứng: `renderer-boundary.test.ts` (mới).
- AC-15: Trình soạn vẫn chạy đủ sau khi đổi import: chèn công thức, mhchem, bảng, trích dẫn, nhập Word — bằng chứng: `e2e/tests/rich-text-editor.spec.ts` (ED-QA flow 1–7).
- AC-16: Test và build của web-admin xanh — bằng chứng: `pnpm --filter @thi360/web-admin test`, `pnpm build`.
- AC-17: Tab có trạng thái đang tải và lỗi tải kèm nút thử lại — bằng chứng: `listing-form.test.tsx` "trạng thái tải".
- AC-18: Tab đúng ở 390px và 1280px, sáng và tối, vi và en, không lẫn ngôn ngữ — bằng chứng: ảnh chụp của `exam-listing.spec.ts` "ảnh tab".
- AC-19: Lỗi trong `listingCheck` hiện ở nhóm "Thông tin bán"; công bố bị từ chối vì `LISTING_*` hiện đúng lỗi trong hộp thoại — bằng chứng: `listing-check.test.tsx` (mới)
- AC-20: Ảnh bìa lớn hơn 2 MB bị từ chối ngay trên web, không gọi API — bằng chứng: `listing-cover.test.tsx`, ca "quá 2 MB"
- AC-21: Nhóm "Học viên" chứa mục "Lượt thi", nhóm ẩn khi vai trò không có `ATTEMPT.VIEW` — bằng chứng: `navigation.test.ts` "nhóm Học viên"

**Ảnh hưởng ngoài task:**

- Danh sách tab bài thi (`apps/web-admin/src/features/exams/editor-tabs.ts`, `EXAM_EDITOR_TABS`; dùng ở `exam-editor-layout.tsx`, `create-exam-page.tsx`, `exam-list-actions.tsx`, `exam-list-view.tsx`, `publish-exam-dialog.tsx`) → chạy `structure-frame.test.tsx`, `e2e/tests/exam-structure-frame.spec.ts`; tab cũ vẫn mở đúng [chủ: task này]
- Hộp thoại chưa lưu đọc nhãn tab (`apps/web-admin/src/features/exams/components/exam-structure-view.tsx:449-450`, `editor.tabs.${tab}`) → thêm ca rời Cấu trúc sang tab mới vào `structure-frame.test.tsx`, nhãn không rỗng [chủ: task này]
- Nhóm và mã lỗi của tab Kiểm tra (`review-types.ts:94` `CHECK_ISSUE_CODES`, `CheckGroup`; `exam-review-check.tsx:707-736`; `check.ts`) → chạy `issue-codes-2b.test.tsx`, `review-findings.test.tsx`, `review-real-check.test.tsx` [chủ: task này]
- Câu chữ cảnh báo (`exams.review.issue.MANUAL_GRADING_REQUIRED`, `apps/web-admin/src/messages/vi.json:4666`) → chạy `issue-codes-2b.test.tsx`, `messages.test.ts` [chủ: task này]
- Cấu trúc menu (`apps/web-admin/src/lib/navigation.ts`; dùng ở `features/shell/sidebar.tsx`, `features/shell/topbar.tsx`, `features/dashboard/dashboard-view.tsx`) → sửa `navigation.test.ts:104` theo nhóm mới; chạy `navigation.test.ts`, `shell.test.tsx`, `dashboard.test.tsx` [chủ: task này]
- Menu trên trình duyệt (`e2e/tests/rbac.spec.ts:118,151-155,284-286,339`; `e2e/tests/admin-responsive.spec.ts`, ngăn kéo và sidebar 248px; `e2e/tests/bulk-import.spec.ts:201-213`) → chạy ba spec, không có mục ẩn nào lọt ra [chủ: task này]
- Khóa dịch (`apps/web-admin/src/messages/messages.test.ts`, cùng khóa và cùng thứ tự ở vi, en) → chạy `messages.test.ts` [chủ: task này]
- Màn đang hiện nội dung câu hỏi (12 tệp ở mục đổi import) → chạy `pnpm --filter @thi360/web-admin test src/features/questions src/features/question-import src/features/exams`, `e2e/tests/exam-preview-qda.spec.ts` [chủ: task này]
- Lược đồ kiểm nội dung dùng `sanitizeQuestionHtml` (`features/exams/create-exam-schema.ts`, `features/questions/editor-schema.ts`) → chạy `e2e/tests/exam-create-step1.spec.ts` và test trong `src/features/questions` [chủ: task này]
- Lớp `.rich-text-body` dùng ngoài bộ hiển thị (`components/rich-text/insert/blank/dialog.tsx:134`, `features/questions/components/question-editor-set.tsx:182`) → CSS của `packages/ui` được nạp ở layout web-admin; kiểm bằng "ED-QA flow 6" (đoạn đọc có 5 chỗ trống) trong `rich-text-editor.spec.ts` [chủ: task này]
- Test của trình soạn trỏ tới mã đã chuyển (`__tests__/sanitize-memo.test.tsx` có `vi.mock("../sanitize", …)`; `rich-text-editor.test.tsx`, `insert-quote.test.tsx`, `atom-math.test.tsx`, `fixtures-roundtrip.test.tsx`, `insert-table.test.tsx`, `insert/chemistry/__tests__/chemistry-dialog.test.tsx`, `insert/cross/__tests__/cross-dialog.test.tsx`, `insert/word/__tests__/parse.test.ts`) → đổi đường dẫn; `sanitize-memo.test.tsx` phải mock đúng mô-đun mới và vẫn đếm được số lần gọi [chủ: task này]
- Mẫu Word của E2E (`e2e/tests/helpers/editor-qa.ts:56` đọc `insert/word/__fixtures__/word365-sample.html`) → tệp giữ nguyên chỗ; chạy `rich-text-editor.spec.ts` [chủ: task này]
- Một bản KaTeX (`apps/web-admin/package.json`, `pnpm-lock.yaml`) → `pnpm --filter @thi360/web-admin why katex` chỉ ra 0.19.1 [chủ: task này]
- Phần xuất của `packages/ui` (`RichTextView`, `RichTextViewProps`, `renderMath`, `deferUntilMhchem`, `sanitizeQuestionHtml`, `questionHtmlText`, `hasUnbalancedMath`, `SanitizeResult`, `SanitizeViolation`, `stripTrailingEmptyParagraph`, `FIGURE_WIDTHS`, CSS của `.rich-text-body`) → FS-4 xuất đủ, nên qua đường xuất con để KaTeX không vào mọi màn dùng `@thi360/ui`; thiếu thì `pnpm typecheck` của FA-1 đỏ [chủ: FS-4]
- Mức giá tối thiểu trên gợi ý của ô giá (`THI360_VNPAY_MIN_AMOUNT`, web không đọc được) → API `listing` trả mức tối thiểu; `listing-form.test.tsx` kiểm gợi ý lấy từ API, không viết cứng 5.000 [chủ: EX-1]
- Giới hạn ảnh bìa (TM 6.2 và 6.3 ghi khác nhau về cạnh ngắn ≥ 480px) → EX-1 chốt ở máy chủ; `listing-cover.test.tsx` chỉ kiểm hiện lỗi máy chủ trả [chủ: EX-1]
- Dòng ẩn của từng mục (`HIDDEN_NAV_ITEMS`) → bỏ dòng và chạy `navigation.test.ts` [chủ: FA-2, FA-3, FA-4, FB]

**Kế hoạch kiểm thử:**

- AC-1: `listing-tab.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-tab.test.tsx`; "thứ tự tab", E2E, `pnpm --filter @thi360/e2e test tests/exam-listing.spec.ts`
- AC-2: "xem trước thẻ", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-form.test.tsx`
- AC-3: "dải giá đang bán", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-form.test.tsx`
- AC-4: "lưu thông tin bán", E2E, `pnpm --filter @thi360/e2e test tests/exam-listing.spec.ts`
- AC-5: "412 khi hai người cùng sửa" (hai phiên trình duyệt), E2E, `pnpm --filter @thi360/e2e test tests/exam-listing.spec.ts`
- AC-6: "lỗi theo ô", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-form.test.tsx`
- AC-7: `listing-cover.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-cover.test.tsx`; "ảnh bìa sai định dạng", E2E, `pnpm --filter @thi360/e2e test tests/exam-listing.spec.ts`
- AC-8: "chỉ xem", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-form.test.tsx`; "vai trò tùy chỉnh chỉ có EXAM.VIEW", E2E, `pnpm --filter @thi360/e2e test tests/exam-listing.spec.ts`
- AC-9: `listing-check-group.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-check-group.test.tsx`
- AC-10: "cảnh báo chấm tay BCA FULL", E2E, `pnpm --filter @thi360/e2e test tests/exam-listing.spec.ts`
- AC-11: "công bố khi có cảnh báo chấm tay", E2E, `pnpm --filter @thi360/e2e test tests/exam-listing.spec.ts`
- AC-12: "menu thương mại theo quyền", unit, `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts`
- AC-13: "danh sách ẩn", unit, `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts`
- AC-14: `renderer-boundary.test.ts`, unit, `pnpm --filter @thi360/web-admin test src/components/rich-text/__tests__/renderer-boundary.test.ts`
- AC-15: ED-QA flow 1–7, E2E, `pnpm --filter @thi360/e2e test tests/rich-text-editor.spec.ts`
- AC-16: toàn bộ test và build, unit, `pnpm --filter @thi360/web-admin test` và `pnpm build`
- AC-17: "trạng thái tải", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-form.test.tsx`
- AC-18: "ảnh tab" chụp đủ 2 khổ, 2 nền, 2 ngôn ngữ rồi người duyệt đối chiếu bản vẽ, giao diện, `pnpm --filter @thi360/e2e test tests/exam-listing.spec.ts`
- AC-19: `listing-check.test.tsx` (mới), unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-check.test.tsx`
- AC-20: "quá 2 MB", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-cover.test.tsx`
- AC-21: `navigation.test.ts` "nhóm Học viên", unit, `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts`
- Hồi quy:
  - `pnpm --filter @thi360/web-admin test src/features/exams src/features/questions src/features/question-import src/components/rich-text`
  - `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts src/features/shell/shell.test.tsx src/features/dashboard/dashboard.test.tsx src/messages/messages.test.ts`
  - `pnpm --filter @thi360/e2e test tests/rbac.spec.ts tests/admin-responsive.spec.ts tests/bulk-import.spec.ts tests/exam-structure-frame.spec.ts tests/exam-create-step1.spec.ts tests/exam-preview-qda.spec.ts tests/rich-text-editor.spec.ts`
  - `pnpm --filter @thi360/web-admin why katex`
  - `pnpm lint`, `pnpm typecheck`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: bài BCA FULL do DS nạp, chỉ đọc; AC-4, AC-5, AC-11 dùng bản sao tạo trong spec, tên có tiền tố `FA1-`; vai trò tùy chỉnh tạo qua API phân quyền với tên có tiền tố `FA1-`; E2E chạy `workers: 1`.
- Không kiểm: thẻ sản phẩm ở web học viên (FS-1); so ảnh tự động vì repo chưa có `toHaveScreenshot`, ảnh do người duyệt đối chiếu.


## FA-2 · Khuyến mãi

**Spec:** BS 10; TM 7.2 (bước 5, sửa khi đã dùng), 7.3.

**Thiết kế:** `docs/design/dot-3/`, canvas trang 15: `KhuyenMai`, `TaoKhuyenMai`, `ChiTietKhuyenMai`.

**Sản phẩm:**

- `/promotions` hai tab theo loại, dải trạng thái (sắp diễn ra, đang chạy, đã kết thúc), tìm kiếm, phân trang.
- Trang tạo `/promotions/new` và sửa `/promotions/[id]/edit` (DESIGN 5.14), gợi ý mã voucher, trường bị khóa có lý do.
- Thử giá: chỉ có sau khi đã lưu, vì `price-preview` cần `promotionId`.
- Chi tiết `/promotions/[id]` kèm các lần dùng; ngừng, tiếp tục, xóa.
- Cảnh báo khuyến mãi bị giữ ở mức tối thiểu (TM 7.2 bước 5) ở danh sách và chi tiết.
- Bỏ dòng `promotions` khỏi `HIDDEN_NAV_ITEMS` ở menu.
- Chi tiết khuyến mãi hiện "Tiền đã giảm" (`totalDiscountPaid`) và khối lịch sử thay đổi (`history`); cảnh báo giá sàn đọc từ `capWarnings` (BS 10).

**Đường dẫn sở hữu:**

- `apps/web-admin/src/app/(shell)/promotions/**` (mới)
- `apps/web-admin/src/features/promotions/**` (mới)
- `e2e/tests/promotions.spec.ts` (mới)
- thêm vào: `apps/web-admin/src/lib/navigation.ts` (bỏ dòng ẩn `promotions`), `apps/web-admin/src/lib/navigation.test.ts` (ca mục hiện)
- thêm vào: `apps/web-admin/src/messages/vi.json`, `apps/web-admin/src/messages/en.json` (`promotions.*`)

**Ngoài phạm vi:** API và quy tắc tính giá (CM-2); dùng voucher khi mua (FS-3); luồng tạo voucher rồi dùng ở FS-3 (QA); cấu trúc menu (FA-1).

**Nghiệm thu:**

- AC-1: `/promotions` có hai tab và dải trạng thái; đổi tab, trạng thái, từ khóa thì gọi `GET /promotions` đúng tham số `kind`, `state`, `q` — bằng chứng: `promotion-list.test.tsx`.
- AC-2: Tạo khuyến mãi và voucher thành công; bấm lưu hai lần chỉ tạo một bản nhờ khóa idempotency — bằng chứng: `promotion-form.test.tsx` "một khóa idempotency", `promotions.spec.ts` "tạo voucher".
- AC-3: Mã trùng (409 `VOUCHER_CODE_TAKEN`) hiện lỗi ở ô mã, giữ dữ liệu đã nhập — bằng chứng: `promotion-form.test.tsx` "mã trùng".
- AC-4: Nút gợi ý mã điền mã từ `GET /promotions/suggested-code` — bằng chứng: `promotion-form.test.tsx` "gợi ý mã".
- AC-5: Trang tạo chưa lưu thì khối thử giá bị khóa kèm lý do; đã lưu thì `price-preview` trả giá và hiện kết quả — bằng chứng: `promotion-price-preview.test.tsx`.
- AC-6: Chi tiết hiện thông tin và trang các lần dùng (mã đơn, tài khoản, số tiền giảm, trạng thái) — bằng chứng: `promotion-detail.test.tsx`.
- AC-7: Thiếu `PROMOTION.MODIFY` thì chỉ xem: không có nút tạo, sửa, ngừng, tiếp tục, xóa — bằng chứng: `promotion-list.test.tsx` "chỉ xem", `promotions.spec.ts` "vai trò tùy chỉnh chỉ có PROMOTION.VIEW".
- AC-8: Thiếu `PROMOTION.VIEW` thì không vào được `/promotions` — bằng chứng: `promotions.spec.ts` "thiếu quyền xem".
- AC-9: Sửa voucher đã dùng: trường bị khóa hiện lý do; chỉ sửa được tên, mô tả, `ends_at`, `total_limit`, trạng thái — bằng chứng: `promotion-form.test.tsx` "voucher đã dùng".
- AC-10: 412 khi lưu thì nạp lại dữ liệu mới và báo cho người sửa — bằng chứng: `promotions.spec.ts` "412 khi hai người cùng sửa".
- AC-11: Ngừng và tiếp tục gửi `expectedRevision`; 412 xử lý như AC-10 — bằng chứng: `promotion-detail.test.tsx` "ngừng, tiếp tục".
- AC-12: Xóa khuyến mãi đã dùng (409 `PROMOTION_IN_USE`) hiện lỗi rõ và không mất dòng — bằng chứng: `promotion-detail.test.tsx` "xóa khi đã dùng".
- AC-13: Danh sách rỗng hiện trạng thái rỗng — bằng chứng: `promotion-list.test.tsx` "rỗng".
- AC-14: Lỗi tải hiện trạng thái lỗi kèm nút thử lại — bằng chứng: `promotion-list.test.tsx` "lỗi tải".
- AC-15: Khuyến mãi có bài thi bị giữ ở mức tối thiểu hiện cảnh báo ở danh sách và chi tiết, mức tiền lấy từ API — bằng chứng: `promotion-cap-warning.test.tsx`.
- AC-16: E2E tạo voucher thành công; dùng voucher ở FS-3 chạy trong QA — bằng chứng: `promotions.spec.ts` "tạo voucher".
- AC-17: Mục Khuyến mãi hiện ở menu khi có `PROMOTION.VIEW` — bằng chứng: `navigation.test.ts` "mục khuyến mãi hiện".
- AC-18: Các màn đúng ở 390px và 1280px, sáng và tối, vi và en, không lẫn ngôn ngữ — bằng chứng: ảnh chụp của `promotions.spec.ts` "ảnh màn".
- AC-19: Chi tiết hiện đúng "Tiền đã giảm", lịch sử mới nhất trước với giá trị cũ và mới, và bài bị giữ ở giá sàn; danh sách rỗng hiện trạng thái rỗng — bằng chứng: `promotion-detail-data.test.tsx` "dữ liệu chi tiết" (mới)

**Ảnh hưởng ngoài task:**

- Danh sách ẩn và nhóm Thương mại (`apps/web-admin/src/lib/navigation.ts`; dùng ở `features/shell/sidebar.tsx`, `features/shell/topbar.tsx`, `features/dashboard/dashboard-view.tsx`) → chạy `navigation.test.ts`, `shell.test.tsx`, `dashboard.test.tsx`; lối tắt mới có `nav.items.promotions.description` [chủ: task này]
- Menu trên trình duyệt (`e2e/tests/rbac.spec.ts`, `e2e/tests/admin-responsive.spec.ts`) → chạy hai spec; nhóm Thương mại hiện với quản trị hệ thống, ngăn kéo 390px cuộn được tới mục cuối [chủ: task này]
- Khóa dịch (`apps/web-admin/src/messages/messages.test.ts`) → chạy `messages.test.ts` [chủ: task này]
- Dữ liệu cho cảnh báo mức tối thiểu (`PriceQuote` của TM 7.3 không có trường này) → `GET /promotions`, `GET /promotions/{id}` trả các bài bị giữ và mức tối thiểu; `promotion-cap-warning.test.tsx` dùng đúng trường của `@thi360/api-client` [chủ: CM-2]
- Kiểu API sinh tự động (`packages/api-client`) → `pnpm typecheck` xanh sau khi CM-2 sinh lại [chủ: CM-2]
- Voucher tạo ở màn này được dùng khi mua (`/checkout/[code]`) → luồng voucher 100% của `e2e/tests/qa-voucher-100.spec.ts` (mới) [chủ: QA]

**Kế hoạch kiểm thử:**

- AC-1: `promotion-list.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-list.test.tsx`
- AC-2: "một khóa idempotency", unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-form.test.tsx`; "tạo voucher", E2E, `pnpm --filter @thi360/e2e test tests/promotions.spec.ts`
- AC-3: "mã trùng", unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-form.test.tsx`
- AC-4: "gợi ý mã", unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-form.test.tsx`
- AC-5: `promotion-price-preview.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-price-preview.test.tsx`
- AC-6: `promotion-detail.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-detail.test.tsx`
- AC-7: "chỉ xem", unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-list.test.tsx`; "vai trò tùy chỉnh chỉ có PROMOTION.VIEW", E2E, `pnpm --filter @thi360/e2e test tests/promotions.spec.ts`
- AC-8: "thiếu quyền xem", E2E, `pnpm --filter @thi360/e2e test tests/promotions.spec.ts`
- AC-9: "voucher đã dùng", unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-form.test.tsx`
- AC-10: "412 khi hai người cùng sửa" (hai phiên trình duyệt), E2E, `pnpm --filter @thi360/e2e test tests/promotions.spec.ts`
- AC-11: "ngừng, tiếp tục", unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-detail.test.tsx`
- AC-12: "xóa khi đã dùng", unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-detail.test.tsx`
- AC-13: "rỗng", unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-list.test.tsx`
- AC-14: "lỗi tải", unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-list.test.tsx`
- AC-15: `promotion-cap-warning.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-cap-warning.test.tsx`
- AC-16: "tạo voucher", E2E, `pnpm --filter @thi360/e2e test tests/promotions.spec.ts`
- AC-17: "mục khuyến mãi hiện", unit, `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts`
- AC-18: "ảnh màn" chụp đủ 2 khổ, 2 nền, 2 ngôn ngữ rồi người duyệt đối chiếu bản vẽ, giao diện, `pnpm --filter @thi360/e2e test tests/promotions.spec.ts`
- AC-19: `promotion-detail-data.test.tsx` "dữ liệu chi tiết", unit, `pnpm --filter @thi360/web-admin test src/features/promotions/__tests__/promotion-detail-data.test.tsx`
- Hồi quy:
  - `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts src/features/shell/shell.test.tsx src/features/dashboard/dashboard.test.tsx src/messages/messages.test.ts`
  - `pnpm --filter @thi360/e2e test tests/rbac.spec.ts tests/admin-responsive.spec.ts`
  - `pnpm lint`, `pnpm typecheck`, `pnpm build`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: unit dùng API giả trong `apps/web-admin/src/test/fake-api.ts`; E2E tự tạo khuyến mãi và vai trò tùy chỉnh có tiền tố `FA2-`, bài thi lấy từ DS chỉ đọc; E2E chạy `workers: 1`.
- Không kiểm: tính đúng giá sau giảm (CM-2 kiểm ở máy chủ); voucher dùng khi mua (QA); so ảnh tự động vì repo chưa có `toHaveScreenshot`, ảnh do người duyệt đối chiếu.


## FA-3 · Đơn hàng, người mua, hoàn tiền

**Spec:** BS 14; TM 9.

**Thiết kế:** `docs/design/dot-3/`, canvas trang 16: `DonHang`, `ChiTietDonHang`, `HoanTienDonHang`, `NguoiMua`.

**Sản phẩm:**

- `/orders` có dải "Cần xử lý", tìm kiếm và bộ lọc (TM 9.1).
- Chi tiết đơn `/orders/[code]`: thông tin đã chốt, bảng giao dịch, quyền liên quan, lịch sử xử lý (TM 9.2).
- `/orders/buyers?exam=<id>` và liên kết từ trang bài thi (TM 9.3).
- Hộp thoại hoàn tiền: qua API; ghi tay với xác nhận lần hai; lần hoàn `UNKNOWN` có "Tra cứu kết quả" và "Đánh dấu thất bại kèm chứng từ" (TM 9.4).
- Nút tiếp tục cấp quyền với hộp thoại xác nhận (TM 9.5).
- Bỏ dòng `orders` khỏi `HIDDEN_NAV_ITEMS` ở menu.
- Khối báo cáo đối soát mới nhất ở đầu `/orders` (`GET /reconciliation-reports/latest` của CM-5), và nhãn nhật ký thao tác `REFUND_*`, `ENTITLEMENT_REVOKED`, `ORDER_FULFILLED_MANUALLY` trong `messages/{vi,en}.json`.
- Bảng giao dịch ghi rõ khoản được tính, khoản thu thừa, khoản chờ đối soát. Hộp thoại "Đối soát VNPAY" cho khoản chờ đối soát: chọn cấp quyền hoặc hoàn tiền, bắt buộc lý do và mã đối chiếu, gọi `late-success` (BS 14). Nút chỉ hiện với `ORDER.RECOVER`.

**Đường dẫn sở hữu:**

- `apps/web-admin/src/app/(shell)/orders/**` (mới)
- `apps/web-admin/src/features/orders/**` (mới)
- `e2e/tests/orders-refund.spec.ts` (mới)
- thêm vào: `apps/web-admin/src/features/exams/listing/**` (liên kết "Người mua" tới `/orders/buyers?exam=<id>`)
- thêm vào: `apps/web-admin/src/lib/navigation.ts` (bỏ dòng ẩn `orders`), `apps/web-admin/src/lib/navigation.test.ts` (ca mục hiện)
- thêm vào: `apps/web-admin/src/messages/vi.json`, `apps/web-admin/src/messages/en.json` (`orders.*`)

**Ngoài phạm vi:** API đơn, hoàn tiền, cấp quyền và đối soát (CM-5); các bài của đơn bộ trên `/orders` và chi tiết đơn (FB); hoàn tiền qua sandbox VNPAY thật (VP-2); cấu trúc menu (FA-1).

**Nghiệm thu:**

- AC-1: `/orders` hiện đủ cột của TM 9.1, mới nhất trước — bằng chứng: `order-list.test.tsx` "cột và thứ tự".
- AC-2: Tìm theo mã đơn, tên đăng nhập, mã giao dịch VNPAY; lọc theo bài thi, khoảng thời gian, trạng thái thanh toán, trạng thái hoàn tiền, hình thức — bằng chứng: `order-list.test.tsx` "tìm và lọc".
- AC-3: Dải "Cần xử lý" hiện đủ sáu loại: VNPAY báo thành công trên lần đã ghi thất bại, đã thu chưa cấp quyền, thanh toán chưa rõ quá 24 giờ, tiền trùng chưa hoàn, `needs_review`, lần hoàn `UNKNOWN` — bằng chứng: `order-attention.test.tsx`.
- AC-4: Chi tiết đơn hiện đủ bốn khối của TM 9.2 — bằng chứng: `order-detail.test.tsx`.
- AC-5: Người mua theo bài thi mỗi dòng một tài khoản; tổng là số tài khoản khác nhau, không phải số đơn (mục AC 08 của đặc tả) — bằng chứng: `order-buyers.test.tsx`, `orders-refund.spec.ts` "một tài khoản mua hai đơn".
- AC-6: Trang bài thi có liên kết tới người mua của bài đó, chỉ hiện khi có `ORDER.VIEW` — bằng chứng: `order-buyers.test.tsx` "liên kết từ bài thi".
- AC-7: Thiếu `ORDER.VIEW` thì không vào được `/orders`, `/orders/[code]`, `/orders/buyers` — bằng chứng: `orders-refund.spec.ts` "thiếu quyền xem".
- AC-8: E2E hoàn tiền với cổng giả lập: hoàn qua API thành công, đơn hiện "Đã hoàn" — bằng chứng: `orders-refund.spec.ts` "hoàn tiền qua API".
- AC-9: Nút hoàn ẩn khi thiếu `ORDER.REFUND` — bằng chứng: `order-detail.test.tsx` "thiếu ORDER.REFUND", `orders-refund.spec.ts` "vai trò tùy chỉnh".
- AC-10: Nút tiếp tục cấp quyền ẩn khi thiếu `ORDER.RECOVER` — bằng chứng: `order-detail.test.tsx` "thiếu ORDER.RECOVER", `orders-refund.spec.ts` "vai trò tùy chỉnh".
- AC-11: Hoàn vượt số còn hoàn hiện lỗi 409 rõ và giữ hộp thoại — bằng chứng: `refund-dialog.test.tsx` "vượt số còn hoàn".
- AC-12: Lần hoàn `UNKNOWN` không có nút ghi tay; có "Tra cứu kết quả" gọi `POST /refunds/{id}/query` — bằng chứng: `refund-dialog.test.tsx` "UNKNOWN".
- AC-13: Lần hoàn `UNKNOWN` đánh dấu thất bại được khi có chứng từ; thiếu chứng từ thì không gửi — bằng chứng: `refund-dialog.test.tsx` "đánh dấu thất bại".
- AC-14: Ghi tay chỉ mở khi lần hoàn API của giao dịch đã `FAILED`, bắt buộc mã chứng từ và lý do, phải xác nhận lần hai — bằng chứng: `refund-dialog.test.tsx` "ghi tay".
- AC-15: Giao dịch có lần hoàn `PROCESSING` hoặc `UNKNOWN` thì khóa nút hoàn kèm "Có lần hoàn đang chạy" — bằng chứng: `order-detail.test.tsx` "có lần hoàn đang chạy".
- AC-16: Bấm gửi hoàn nhiều lần dùng cùng khóa idempotency, chỉ có một khoản hoàn — bằng chứng: `refund-dialog.test.tsx` "một khóa idempotency", `orders-refund.spec.ts` "bấm hoàn hai lần".
- AC-17: Nút tiếp tục cấp quyền chỉ hiện theo điều kiện TM 9.5, hộp thoại đúng câu chữ, xong thì đơn cập nhật — bằng chứng: `order-detail.test.tsx` "tiếp tục cấp quyền".
- AC-18: Danh sách rỗng hiện trạng thái rỗng — bằng chứng: `order-list.test.tsx` "rỗng".
- AC-19: Lỗi tải hiện trạng thái lỗi kèm nút thử lại — bằng chứng: `order-list.test.tsx` "lỗi tải".
- AC-20: Mục Đơn hàng hiện ở menu khi có `ORDER.VIEW` — bằng chứng: `navigation.test.ts` "mục đơn hàng hiện".
- AC-21: Các màn đúng ở 390px và 1280px, sáng và tối, vi và en, không lẫn ngôn ngữ — bằng chứng: ảnh chụp của `orders-refund.spec.ts` "ảnh màn".
- AC-22: `/orders` hiện báo cáo đối soát mới nhất; chưa có báo cáo hoặc lỗi tải thì khối không làm hỏng danh sách — bằng chứng: `reconciliation-report.test.tsx` (mới)
- AC-23: Nhãn nhật ký `REFUND_*`, `ENTITLEMENT_REVOKED`, `ORDER_FULFILLED_MANUALLY` có ở cả `vi` và `en` — bằng chứng: `order-audit-labels.test.ts` (mới)
- AC-24: Hộp thoại đối soát bắt buộc lý do và mã đối chiếu, gửi đúng quyết định; nhận 409 thì báo khoản đã được xử lý và tải lại; thiếu `ORDER.RECOVER` thì không có nút — bằng chứng: `order-late-success.test.tsx` "đối soát" (mới)
- AC-25: Hoàn khoản thu thừa: hộp thoại ghi rõ học viên giữ bài và không thu hồi lượt — bằng chứng: `order-late-success.test.tsx` "hoàn khoản thu thừa" (mới)

**Ảnh hưởng ngoài task:**

- Danh sách ẩn và nhóm Thương mại (`apps/web-admin/src/lib/navigation.ts`; dùng ở `features/shell/sidebar.tsx`, `features/shell/topbar.tsx`, `features/dashboard/dashboard-view.tsx`) → chạy `navigation.test.ts`, `shell.test.tsx`, `dashboard.test.tsx` [chủ: task này]
- Menu trên trình duyệt (`e2e/tests/rbac.spec.ts`, `e2e/tests/admin-responsive.spec.ts`) → chạy hai spec; mục Đơn hàng hiện đúng quyền [chủ: task này]
- Khóa dịch (`apps/web-admin/src/messages/messages.test.ts`) → chạy `messages.test.ts` [chủ: task này]
- Tab Thông tin bán nhận thêm liên kết (`apps/web-admin/src/features/exams/listing/**` của FA-1) → chạy `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/listing-form.test.tsx` [chủ: task này]
- Đánh dấu lần hoàn `UNKNOWN` là `FAILED` kèm chứng từ (TM 9.4 bước 5 yêu cầu, danh sách API của TM 9.4 không có endpoint) → CM-5 thêm endpoint và sinh lại `@thi360/api-client`; `refund-dialog.test.tsx` "đánh dấu thất bại" gọi đúng endpoint đó [chủ: CM-5]
- Dữ liệu dải "Cần xử lý" (`GET /api/v1/admin/orders`, TM 9.1) → CM-5 trả đủ sáu loại; `order-attention.test.tsx` dùng kiểu của `@thi360/api-client` [chủ: CM-5]
- Các bài của đơn bộ trên `/orders` và chi tiết đơn (`apps/web-admin/src/features/orders/**`) → FB thêm vào màn của task này; `order-list.test.tsx` và `order-detail.test.tsx` vẫn xanh [chủ: FB]
- Hoàn tiền rồi thu hồi lượt trên toàn luồng → `e2e/tests/qa-refund-revoke.spec.ts` (mới) [chủ: QA]

**Kế hoạch kiểm thử:**

- AC-1: "cột và thứ tự", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-list.test.tsx`
- AC-2: "tìm và lọc", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-list.test.tsx`
- AC-3: `order-attention.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-attention.test.tsx`
- AC-4: `order-detail.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-detail.test.tsx`
- AC-5: `order-buyers.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-buyers.test.tsx`; "một tài khoản mua hai đơn", E2E, `pnpm --filter @thi360/e2e test tests/orders-refund.spec.ts`
- AC-6: "liên kết từ bài thi", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-buyers.test.tsx`
- AC-7: "thiếu quyền xem", E2E, `pnpm --filter @thi360/e2e test tests/orders-refund.spec.ts`
- AC-8: "hoàn tiền qua API", E2E, `pnpm --filter @thi360/e2e test tests/orders-refund.spec.ts`
- AC-9: "thiếu ORDER.REFUND", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-detail.test.tsx`; "vai trò tùy chỉnh", E2E, `pnpm --filter @thi360/e2e test tests/orders-refund.spec.ts`
- AC-10: "thiếu ORDER.RECOVER", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-detail.test.tsx`; "vai trò tùy chỉnh", E2E, `pnpm --filter @thi360/e2e test tests/orders-refund.spec.ts`
- AC-11: "vượt số còn hoàn", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/refund-dialog.test.tsx`
- AC-12: "UNKNOWN", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/refund-dialog.test.tsx`
- AC-13: "đánh dấu thất bại", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/refund-dialog.test.tsx`
- AC-14: "ghi tay", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/refund-dialog.test.tsx`
- AC-15: "có lần hoàn đang chạy", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-detail.test.tsx`
- AC-16: "một khóa idempotency", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/refund-dialog.test.tsx`; "bấm hoàn hai lần", E2E, `pnpm --filter @thi360/e2e test tests/orders-refund.spec.ts`
- AC-17: "tiếp tục cấp quyền", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-detail.test.tsx`
- AC-18: "rỗng", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-list.test.tsx`
- AC-19: "lỗi tải", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-list.test.tsx`
- AC-20: "mục đơn hàng hiện", unit, `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts`
- AC-21: "ảnh màn" chụp đủ 2 khổ, 2 nền, 2 ngôn ngữ rồi người duyệt đối chiếu bản vẽ, giao diện, `pnpm --filter @thi360/e2e test tests/orders-refund.spec.ts`
- AC-22: `reconciliation-report.test.tsx` (mới), unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/reconciliation-report.test.tsx`
- AC-23: `order-audit-labels.test.ts` (mới), unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-audit-labels.test.ts`
- AC-24: `order-late-success.test.tsx` "đối soát", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-late-success.test.tsx`
- AC-25: `order-late-success.test.tsx` "hoàn khoản thu thừa", unit, `pnpm --filter @thi360/web-admin test src/features/orders/__tests__/order-late-success.test.tsx`
- Hồi quy:
  - `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts src/features/shell/shell.test.tsx src/features/dashboard/dashboard.test.tsx src/messages/messages.test.ts src/features/exams/__tests__/listing-form.test.tsx`
  - `pnpm --filter @thi360/e2e test tests/rbac.spec.ts tests/admin-responsive.spec.ts`
  - `pnpm lint`, `pnpm typecheck`, `pnpm build`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: unit dùng API giả trong `apps/web-admin/src/test/fake-api.ts`, gồm đủ trạng thái `PROCESSING`, `SUCCEEDED`, `FAILED`, `UNKNOWN`; E2E tạo tài khoản, đơn bằng cổng giả lập (`THI360_VNPAY_MODE=MOCK`) và vai trò tùy chỉnh có tiền tố `FA3-`, bài thi lấy từ DS chỉ đọc; E2E chạy `workers: 1`.
- Không kiểm: mã phản hồi hoàn tiền thật của VNPAY (VP-2); đúng sai của phép tính số còn hoàn và thu hồi lượt (CM-5 kiểm ở máy chủ); so ảnh tự động vì repo chưa có `toHaveScreenshot`, ảnh do người duyệt đối chiếu.

**Câu hỏi mở:** TM 9.3 yêu cầu liên kết tới người mua "từ trang bài thi" nhưng `BaiThiThongTinBan` không vẽ chỗ đặt. Tạm đặt ở tab Thông tin bán; M.Trường và BA chốt vị trí.

## FB · Bộ đề trên web

**Spec:** BS 2; TM 16.4, 16.5 (phần web). **Phụ thuộc mềm:** FA-3.

**Thiết kế:** `docs/design/dot-3/`, canvas trang 20: admin `BoDeAdmin`, `TaoBoDe`; học viên `ChiTietBoDe` (kèm thẻ bộ ở danh mục), `XacNhanMuaBo`, `BaiThiCuaToiTheoBo`. Chế độ tối kiểm theo token `DESIGN.md`.

**Sản phẩm:**

- **Web admin:** mục "Bộ đề" trong nhóm Thương mại (FA-1 đã thêm, FB chỉ bỏ dòng ẩn); danh sách, tạo, sửa (chọn kỳ thi, tích bài theo môn với số câu, thời lượng, điểm; giá, số lượt mỗi bài, quyền lợi, mức xem kết quả), công bố, ngừng bán. `/orders` và chi tiết đơn hiện các bài của đơn bộ.
- **Web học viên:** thẻ bộ ở danh mục, giá trị "Bộ đề" của bộ lọc Hình thức; trang chi tiết bộ (`/exams/[code]` nhận mã bộ) có danh sách bài và quyền lợi từng bài; `/checkout/[code]` cho bộ; `/home` nhóm thẻ theo bộ.
- Nhãn nhật ký thao tác `BUNDLE_*` trong `messages/{vi,en}.json` của web-admin.
- Màn tạo bộ và trang chi tiết bộ hiện nhãn "Bài ôn" ở từng bài có `buildMode = REVIEW` (BS 2).

**Đường dẫn sở hữu:**

- `apps/web-admin/src/app/(shell)/bundles/**` (mới)
- `apps/web-admin/src/features/bundles/**` (mới)
- thêm vào: `apps/web-admin/src/lib/navigation.ts` (bỏ dòng ẩn của "Bộ đề")
- thêm vào: phần đơn hàng do FA-3 tạo ở `apps/web-admin/src/features/orders/**` (hiện các bài của đơn bộ)
- thêm vào: `apps/web-admin/src/messages/{vi,en}.json` (khối `bundles`)
- thêm vào: `apps/web-student/src/features/storefront/**` của FS-1 (thẻ bộ, chi tiết bộ)
- thêm vào: `apps/web-student/src/app/exams/[code]/**` của FS-1 (nhận mã bộ)
- thêm vào: `apps/web-student/src/features/checkout/**` của FS-3 (mua bộ)
- thêm vào: `apps/web-student/src/features/my-exams/**` của FS-3 (nhóm thẻ theo bộ)
- thêm vào: `apps/web-student/src/messages/{vi,en}.json` (khối `bundle`)
- `e2e/tests/bundle.spec.ts` (mới), `e2e/tests/bundle-visual.spec.ts` (mới)

**Ngoài phạm vi:** API bộ đề, cấp quyền từng bài, hoàn đơn bộ, dữ liệu mẫu bộ SP2E (CM-6). Cấu trúc menu (FA-1). Màn đơn hàng và hoàn tiền (FA-3). Danh sách chọn do API `/bundles/candidates` quyết (CM-6); web chỉ hiện những gì API trả.

**Nghiệm thu:**

- AC-1: E2E: admin tạo và công bố bộ SP2E 3 môn → học viên mua bằng cổng giả lập → `/home` có nhóm bộ với 3 thẻ — bằng chứng: `e2e/tests/bundle.spec.ts` (mới).
- AC-2: Bắt đầu một môn trong bộ, các môn khác không đổi số lượt và tình trạng — bằng chứng: `bundle.spec.ts` (mới).
- AC-3: Bộ `UNAVAILABLE`: trang chi tiết không cho mua, nút bị vô hiệu kèm câu "Bộ đề tạm thời chưa bán được." — bằng chứng: `bundle-detail.test.tsx` (mới).
- AC-4: Bộ đã ngừng bán hiện trang ngừng bán của FS-1 — bằng chứng: `bundle-detail.test.tsx` (mới).
- AC-5: Danh sách bộ ở web admin đủ bốn trạng thái; tạo và sửa bộ: chọn kỳ thi, tích bài theo môn, mỗi dòng hiện tên bài, số câu, thời lượng, điểm; nhập giá, số lượt mỗi bài, quyền lợi, mức xem kết quả — bằng chứng: `bundle-form.test.tsx` (mới), `bundle-list.test.tsx` (mới).
- AC-6: Lưu có `expectedRevision`; máy chủ báo xung đột thì nạp lại và giữ thông báo, không ghi đè — bằng chứng: `bundle-form.test.tsx` (mới).
- AC-7: Công bố và ngừng bán có xác nhận; máy chủ từ chối thì hiện lý do, trạng thái bộ không đổi — bằng chứng: `bundle-actions.test.tsx` (mới).
- AC-8: Chỉ có `EXAM.VIEW` thì chỉ xem; thiếu `EXAM.MODIFY` thì không tạo, sửa được; thiếu `EXAM.PUBLISH` thì không có công bố, ngừng bán; thiếu `EXAM.VIEW` thì mục "Bộ đề" ẩn và mở thẳng URL bị từ chối — bằng chứng: `bundle-permissions.test.tsx` (mới), `apps/web-admin/src/lib/navigation.test.ts`.
- AC-9: `/orders` và chi tiết đơn hiện sản phẩm bộ và các bài trong đơn — bằng chứng: `bundle-order-items.test.tsx` (mới), `bundle.spec.ts` (mới).
- AC-10: Danh mục có thẻ bộ; lọc Hình thức "Bộ đề" chỉ còn bộ — bằng chứng: `bundle.spec.ts` (mới).
- AC-11: Trang chi tiết bộ hiện từng bài (tên, môn, số câu, thời lượng, điểm tối đa) và quyền lợi từng bài; luôn có nút "Mua bộ đề" khi bộ còn bán — bằng chứng: `bundle-detail.test.tsx` (mới).
- AC-12: Đã có quyền với vài bài thì ghi "Bạn đã có N lượt bài <tên>; mua bộ sẽ cộng thêm lượt" — bằng chứng: `bundle-detail.test.tsx` (mới).
- AC-13: `/checkout/<mã bộ>` hiện các bài trong bộ, tính giá và tạo đơn bằng mã bộ — bằng chứng: `bundle-checkout.test.tsx` (mới), `bundle.spec.ts` (mới).
- AC-14: Trên `/home`, mỗi bài trong nhóm có nút Bắt đầu, Tiếp tục, kết quả và lịch sử riêng; bài mua lẻ không nằm trong nhóm bộ — bằng chứng: `bundle-group.test.tsx` (mới).
- AC-15: Các màn của task đủ bốn trạng thái; đúng khi sáng và tối, vi và en, 390px và 1280px: không tràn ngang, tương phản ≥ 4,5, không lẫn ngôn ngữ — bằng chứng: `e2e/tests/bundle-visual.spec.ts` (mới).
- AC-16: Nhãn nhật ký `BUNDLE_*` có ở cả `vi` và `en` — bằng chứng: `bundle-audit-labels.test.ts` (mới)
- AC-17: Admin chọn bài ôn vào bộ, công bố; trang chi tiết bộ của học viên hiện nhãn "Bài ôn" đúng ở bài đó — bằng chứng: `bundle.spec.ts` "bài ôn trong bộ" (mới)

**Ảnh hưởng ngoài task:**

- Danh sách menu và lọc theo quyền (`apps/web-admin/src/lib/navigation.ts`, `navigation.test.ts`, `apps/web-admin/src/features/shell/sidebar.tsx`, `apps/web-admin/src/features/shell/shell.test.tsx`) → `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts src/features/shell` [chủ: task này]
- Menu trên trình duyệt theo vai trò và khổ màn (`e2e/tests/rbac.spec.ts`, `e2e/tests/admin-responsive.spec.ts`) → `pnpm --filter @thi360/e2e test tests/rbac.spec.ts tests/admin-responsive.spec.ts` [chủ: task này]
- Thẻ, danh mục, chi tiết bài lẻ của FS-1 (`apps/web-student/src/features/storefront/**`) → `pnpm --filter @thi360/e2e test tests/storefront.spec.ts` [chủ: task này]
- Mua bài lẻ và trang kết quả của FS-3 (`apps/web-student/src/features/checkout/**`) → `pnpm --filter @thi360/e2e test tests/checkout.spec.ts` [chủ: task này]
- Thẻ bài lẻ ở `/home` của FS-3 (`apps/web-student/src/features/my-exams/**`) → `pnpm --filter @thi360/e2e test tests/my-exams.spec.ts` [chủ: task này]
- Màn đơn hàng của FA-3 (`apps/web-admin/src/features/orders/**`) → chạy lại test của thư mục này, `pnpm --filter @thi360/web-admin test src/features/orders` [chủ: task này]
- Khóa dịch của hai app (`apps/web-student/src/messages/messages.test.ts`, `apps/web-student/src/messages/source-scan.test.ts`, `apps/web-admin/src/messages/messages.test.ts`, `apps/web-admin/src/messages/source-scan.test.ts`) → `pnpm --filter @thi360/web-student test src/messages`, `pnpm --filter @thi360/web-admin test src/messages` [chủ: task này]
- API bộ đề, `productType`, `items[]`, `bundles[]` của `/me/exams`, các bài của đơn bộ ở API admin (`packages/api-client/src/schema.d.ts`) → lane web không sinh lại; `pnpm typecheck` sau khi rebase [chủ: CM-6]
- Bộ SP2E 3 môn có đủ bài trong bộ dựng dữ liệu (tệp hằng số của DS) → test của CM-6; AC-1 dùng các bài này [chủ: CM-6]
- Cổng giả lập ở profile `e2e` (`THI360_VNPAY_MODE=MOCK`, D-g) → test của CM-3a; AC-1 chạy với profile này [chủ: CM-3a]
- Giới hạn dung lượng bundle: không có: đã tìm `size-limit|bundlesize|bundle-analyzer|budget` trong `.github/workflows`, `scripts`, `package.json`, `apps/web-student/package.json`, `apps/web-student/next.config.ts` → `pnpm --filter @thi360/web-student build` vẫn qua

**Kế hoạch kiểm thử:**

- AC-1: `bundle.spec.ts` ca đầu cuối, E2E, `pnpm --filter @thi360/e2e test tests/bundle.spec.ts`
- AC-2: `bundle.spec.ts` ca bắt đầu một môn, E2E, `pnpm --filter @thi360/e2e test tests/bundle.spec.ts`
- AC-3: `bundle-detail.test.tsx` ca `UNAVAILABLE`, unit, `pnpm --filter @thi360/web-student test src/features/storefront/bundle-detail.test.tsx`
- AC-4: `bundle-detail.test.tsx` ca ngừng bán, unit, `pnpm --filter @thi360/web-student test src/features/storefront/bundle-detail.test.tsx`
- AC-5: `bundle-form.test.tsx`, `bundle-list.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/bundles`
- AC-6: `bundle-form.test.tsx` ca xung đột, unit, `pnpm --filter @thi360/web-admin test src/features/bundles/bundle-form.test.tsx`
- AC-7: `bundle-actions.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/bundles/bundle-actions.test.tsx`
- AC-8: `bundle-permissions.test.tsx` từng quyền riêng lẻ và không quyền, unit, `pnpm --filter @thi360/web-admin test src/features/bundles/bundle-permissions.test.tsx`; `navigation.test.ts`, unit, `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts`
- AC-9: `bundle-order-items.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/orders/bundle-order-items.test.tsx`; `bundle.spec.ts` ca xem đơn bộ, E2E, `pnpm --filter @thi360/e2e test tests/bundle.spec.ts`
- AC-10: `bundle.spec.ts` ca danh mục, E2E, `pnpm --filter @thi360/e2e test tests/bundle.spec.ts`
- AC-11: `bundle-detail.test.tsx` ca danh sách bài, unit, `pnpm --filter @thi360/web-student test src/features/storefront/bundle-detail.test.tsx`
- AC-12: `bundle-detail.test.tsx` ca đã có lượt, unit, `pnpm --filter @thi360/web-student test src/features/storefront/bundle-detail.test.tsx`
- AC-13: `bundle-checkout.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/checkout/bundle-checkout.test.tsx`; `bundle.spec.ts` ca mua bộ, E2E, `pnpm --filter @thi360/e2e test tests/bundle.spec.ts`
- AC-14: `bundle-group.test.tsx`, unit, `pnpm --filter @thi360/web-student test src/features/my-exams/bundle-group.test.tsx`
- AC-15: `bundle-visual.spec.ts`, giao diện, `pnpm --filter @thi360/e2e test tests/bundle-visual.spec.ts`
- AC-16: `bundle-audit-labels.test.ts` (mới), unit, `pnpm --filter @thi360/web-admin test src/features/bundles/__tests__/bundle-audit-labels.test.ts`
- AC-17: `bundle.spec.ts` "bài ôn trong bộ", E2E, `pnpm --filter @thi360/e2e test tests/bundle.spec.ts`
- Hồi quy: `pnpm lint && pnpm typecheck && pnpm test && pnpm build`; `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts src/features/shell src/features/orders src/messages`; `pnpm --filter @thi360/web-student test src/messages`; `pnpm --filter @thi360/e2e test tests/rbac.spec.ts tests/admin-responsive.spec.ts tests/storefront.spec.ts tests/checkout.spec.ts tests/my-exams.spec.ts`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: ba bài SP2E theo môn lấy từ tệp hằng số của DS; bộ do ca E2E tự tạo với mã có hậu tố riêng, tài khoản quản trị `sys.admin`; học viên mới có hậu tố riêng qua `POST /api/v1/admin/users`. Ca thiếu quyền dùng API giả trong vitest với vai trò tùy chỉnh. Backend bật profile `e2e` qua biến `E2E_BACKEND_CMD`; lane chạy compose project riêng (A6).
- Không kiểm: E2E bộ `UNAVAILABLE`, vì phải ngừng bán một bài dùng chung của DS và làm hỏng các spec chạy sau: web kiểm bằng vitest, việc chuyển trạng thái do CM-6 kiểm ở backend. Cấp quyền từng bài, IPN gửi lại, hoàn đơn bộ, voucher phạm vi `BUNDLE`, công bố lại bộ giữ `bundle_version` cũ (TM 16.5, CM-6 kiểm ở backend). Mã HTTP 410 của trang bộ ngừng bán: App Router không trả được 410 từ trang, chỉ kiểm giao diện.


## FA-4 · Hàng chờ chấm thật, vận hành lượt, làm thử như học viên

**Spec:** BS 6, 9, 11; LT 8 (`wordLimit`), 9.2, 12; 2B H17 (R-16, Q-24); SUP mục 5.

**Thiết kế:** `docs/design/dot-3/`, canvas trang 16 và 20: `LuotThiAdmin`, `ChiTietLuotThiAdmin`; `/grading` giữ bố cục màn đợt 2, đổi sang làm việc theo từng câu và chấm ẩn danh (LT 9.2); làm thử như học viên `LamThuNhuHocVien` (nút vào ở tab Xem trước), `KetQuaLamThu` (trang 20).

**Sản phẩm:**

- `/grading` nối API thật của LT 9.2; bỏ cờ và mock theo B8.
  - API làm việc theo từng câu, nên danh sách và màn chấm đổi từ theo lượt sang theo câu: `/grading/[itemId]` thay `/grading/[attemptId]`; nhận câu, lưu nháp, chốt điểm, mở lại.
  - Chấm ẩn danh: bỏ cột, sắp xếp và mọi chỗ hiện tên học viên, chỉ còn mã lượt.
  - Nhãn trạng thái theo LT: `WAITING` là "Chờ chấm".
  - Gỡ `lib/mock/**`, `features/grading/api.mock.ts`, `features/grading/mock/**`, `components/mock-data-banner.tsx`, `components/mockup-placeholder.tsx`, khóa `mockData`, thuộc tính `flag` của `PermissionGate`, `mockFlag` và `MockFlags` của menu, dòng đặt cờ trong `withRealApi` của `src/test/fake-api.ts` (hàm vẫn giữ để dọn registry), biến ở `.env.example` và `src/env.d.ts`.
- `/attempts`, `/attempts/[id]`: dòng sự kiện, nút chấm lại, đánh dấu hỏng, cấp bù. Bỏ dòng `attempts` khỏi `HIDDEN_NAV_ITEMS` ở menu.
- **Làm thử như học viên** (2B H17, D-b): ở tab Xem trước của bài thi, nút "Làm thử" mở đề sinh thử trong tab mới tại `/exams/[id]/preview/[previewId]/try` (đã xếp và đảo phương án bằng `PaperOrdering`, như lượt thi thật) bằng thành phần câu hỏi của `packages/ui` (FS-4).
  - Không tính giờ, chuyển qua lại giữa các phần được, câu trả lời không lưu ở máy chủ trước khi nộp.
  - Nộp thì gọi `POST /api/v1/admin/exams/{id}/preview/{previewId}/grade` (AT-5 làm), hiện điểm từng câu, từng ý Đúng/Sai. Không tạo lượt, không tạo kết quả học tập.
  - Trang làm thử nằm ngoài khung tab: các trang hiện có của `exams/[id]` chuyển vào nhóm đường dẫn `exams/[id]/(editor)/`, theo mẫu `questions/[id]/(detail)`; URL không đổi.
- Màn làm thử đọc đề qua `GET /api/v1/admin/exams/{id}/preview/{previewId}/paper` (AT-5), không dùng phản hồi của `POST …/preview` vì phản hồi đó có đáp án.
- `/grading` hiện số đếm theo trạng thái từ API `counts`, đổi theo bộ lọc đang chọn (BS 11). Màn chấm tính số chữ bằng `countWords` từ bài làm và hiện cạnh yêu cầu tối thiểu (BS 6).
- Bấm "Làm thử" gọi `POST …/preview/{previewId}/trial` trước khi mở tab làm thử, hiện giờ hết hạn của đề (BS 9).

**Đường dẫn sở hữu:**

- `apps/web-admin/src/app/(shell)/grading/**`, `apps/web-admin/src/features/grading/**`
- `apps/web-admin/src/app/(shell)/attempts/**` (mới), `apps/web-admin/src/features/attempts/**` (mới)
- `apps/web-admin/src/app/(shell)/exams/[id]/(editor)/**` (mới, chuyển từ `exams/[id]/`), `apps/web-admin/src/app/(shell)/exams/[id]/preview/[previewId]/try/page.tsx` (mới)
- `apps/web-admin/src/features/exams/trial/**` (mới), `apps/web-admin/src/features/exams/__tests__/trial-*.test.tsx` (mới), `apps/web-admin/src/features/exams/components/exam-preview-panel.tsx`
- Gỡ mock: `apps/web-admin/src/lib/mock/**`, `apps/web-admin/src/components/mock-data-banner.tsx`, `apps/web-admin/src/components/mockup-placeholder.tsx`, `apps/web-admin/src/features/content/__tests__/mock-flags.test.ts`
- `apps/web-admin/src/features/session/permission-gate.tsx`, `apps/web-admin/src/features/session/__tests__/permission-gate.test.tsx` (mới)
- `apps/web-admin/src/lib/navigation.ts`, `apps/web-admin/src/lib/navigation.test.ts`
- `apps/web-admin/src/test/setup.ts`, `apps/web-admin/src/test/setup-fake-client.ts`, `apps/web-admin/src/test/fake-api.ts`, `apps/web-admin/src/test/fixtures/questions.json` (mới, chuyển từ `features/grading/mock/questions.json`)
- `apps/web-admin/src/features/questions/__tests__/api-fixtures.ts`, `apps/web-admin/src/features/questions/__tests__/bank-readiness.test.ts`, `apps/web-admin/src/features/content/api.ts` (chú thích dòng 8)
- `apps/web-admin/.env.example`, `apps/web-admin/src/env.d.ts`
- `e2e/tests/grading-attempts.spec.ts` (mới), `e2e/tests/exam-trial.spec.ts` (mới)
- thêm vào: `apps/web-admin/src/messages/vi.json`, `apps/web-admin/src/messages/en.json` (`grading.*`, `attempts.*`, `exams.trial.*`; xóa `mockData`)

**Ngoài phạm vi:** API chấm, vận hành lượt và API chấm đề làm thử (AT-5); cổng đọc đề thử `PreviewPaperPort` (EX-2); bộ chấm từng câu (AT-4); thành phần câu hỏi (FS-4); sửa `PaperOrdering` (không task nào của đợt sửa, D-a); cập nhật `DESIGN.md` (QA); ghi chú lượt hỏng ở kết quả và lịch sử của học viên (FS-6).

**Nghiệm thu:**

- AC-1: `/grading` chỉ gọi API thật; không còn cờ `NEXT_PUBLIC_THI360_GRADING_MOCK`, tệp mock hay `?mockState` — bằng chứng: `mock-removed.test.ts` (mới).
- AC-2: Danh sách lọc theo `state`, `exam`, `kind`, `from`, `to`; màn chấm không hiện tên đăng nhập hay họ tên học viên, chỉ hiện mã lượt — bằng chứng: `grading-list.test.tsx`, `grading-attempts.spec.ts` "chấm ẩn danh".
- AC-3: E2E nộp bài có tự luận → chấm → học viên thấy điểm tổng — bằng chứng: `grading-attempts.spec.ts` "tự luận tới điểm tổng".
- AC-4: Câu đã có người nhận thì chỉ xem; nhận trùng (409 `GRADING_CLAIMED`) hiện tên người đang giữ — bằng chứng: `grading-attempt.test.tsx` "câu đã có người nhận", `grading-attempts.spec.ts` "hai người chấm".
- AC-5: Người nhận im quá 30 phút thì người khác nhận được câu (F17) — bằng chứng: `grading-attempts.spec.ts` "nhận lại sau 30 phút".
- AC-6: Điểm ngoài khoảng hoặc tổng tiêu chí khác điểm thì không chốt được và hiện lỗi rõ — bằng chứng: `grading-attempt.test.tsx` "lỗi khi chốt".
- AC-7: Lưu nháp gửi `expectedVersion`; xung đột phiên bản thì báo và nạp lại — bằng chứng: `grading-attempt.test.tsx` "xung đột phiên bản".
- AC-8: Mở lại bản đã chốt phải nhập lý do — bằng chứng: `grading-attempt.test.tsx` "mở lại".
- AC-9: Thiếu `GRADING.MODIFY` thì chỉ xem; thiếu `GRADING.VIEW` thì không vào được `/grading` và mục menu ẩn — bằng chứng: `grading-attempts.spec.ts` "vai trò tùy chỉnh chấm", `permission-gate.test.tsx`.
- AC-10: `/attempts` lọc theo người dùng, bài, trạng thái, khoảng thời gian; thiếu `ATTEMPT.VIEW` thì không vào được — bằng chứng: `attempt-list.test.tsx`, `grading-attempts.spec.ts` "vai trò tùy chỉnh vận hành".
- AC-11: `/attempts/[id]` hiện thông tin lượt, các phần và hạn, dòng sự kiện, câu trả lời cuối kèm `answer_received_at` và `seq` — bằng chứng: `attempt-detail.test.tsx`.
- AC-12: Nút chấm lại chỉ hiện với lượt `GRADING_ERROR` và quyền `GRADING.MODIFY` — bằng chứng: `attempt-detail.test.tsx` "chấm lại".
- AC-13: Đánh dấu hỏng bắt buộc nhập lý do — bằng chứng: `attempt-detail.test.tsx` "đánh dấu hỏng".
- AC-14: Cấp bù chỉ có ở lượt đã đánh dấu hỏng, mỗi lượt một lần, bắt buộc lý do, gửi khóa idempotency; 409 `GRANT_REFUNDED` hiện lỗi rõ — bằng chứng: `attempt-detail.test.tsx` "cấp bù", `grading-attempts.spec.ts` "đánh dấu hỏng rồi cấp bù".
- AC-15: Thiếu `ATTEMPT.COMPENSATE` thì không có nút cấp bù và nút đánh dấu hỏng; thiếu cả quyền này và `GRADING.MODIFY` thì ẩn khối "Xử lý lượt" — bằng chứng: `attempt-detail.test.tsx` "theo quyền", `grading-attempts.spec.ts` "vai trò tùy chỉnh vận hành".
- AC-16: Mục Lượt thi hiện ở menu khi có `ATTEMPT.VIEW`; mục Chấm tự luận chỉ theo `GRADING.VIEW` — bằng chứng: `navigation.test.ts` "mục lượt thi hiện".
- AC-17: Nút "Làm thử" chỉ dùng được khi kết quả sinh thử có `previewId`; không có thì khóa kèm "Chưa có đề thử để làm" — bằng chứng: `trial-entry.test.tsx`.
- AC-18: Làm thử mở trong tab mới, không có đồng hồ, chuyển qua lại giữa các phần được — bằng chứng: `exam-trial.spec.ts` "mở tab mới".
- AC-19: Làm thử đề sinh thử SP2E Toán của DS: điểm từng câu đúng quy tắc chấm của câu, từng ý Đúng/Sai hiện riêng — bằng chứng: `exam-trial.spec.ts` "chấm SP2E Toán".
- AC-20: Làm thử không tạo dòng mới trong `attempt`: danh sách `GET /api/v1/admin/attempts?exam=<id>` không đổi trước và sau khi nộp — bằng chứng: `exam-trial.spec.ts` "không tạo lượt".
- AC-21: Câu Một đáp án, Nhiều đáp án và câu con của chùm có `shuffleOptions = false` giữ thứ tự phương án; câu khác cùng `seed` giữ nguyên thứ tự đã đảo; cột phải Ghép đôi luôn đảo (D-a) — bằng chứng: `exam-trial.spec.ts` "giữ thứ tự phương án".
- AC-22: Bài có tự luận (BCA FULL của DS): câu tự luận ghi "Chờ chấm", dòng điểm ghi "… phần trắc nghiệm · tự luận chưa chấm" — bằng chứng: `exam-trial.spec.ts` "tự luận BCA FULL".
- AC-23: Câu tự luận có `wordLimit` hiện bộ đếm, nhắc khi chưa đủ số chữ tối thiểu, không chặn nộp — bằng chứng: `trial-paper.test.tsx` "số chữ tối thiểu".
- AC-24: Rời trang khi đang làm thì hỏi "Bỏ lần làm thử này?" — bằng chứng: `trial-paper.test.tsx` "rời trang".
- AC-25: `PREVIEW_EXPIRED` khi mở và khi nộp đều hiện đúng trạng thái hết hạn — bằng chứng: `trial-paper.test.tsx` "đề thử hết hạn".
- AC-26: Trước khi nộp, màn làm thử không hiện đáp án, lời giải, tiêu chí chấm — bằng chứng: `exam-trial.spec.ts` "không lộ đáp án".
- AC-27: Thiếu `EXAM.VIEW` thì không mở được trang làm thử — bằng chứng: `exam-trial.spec.ts` "thiếu quyền xem bài".
- AC-28: Danh sách rỗng và lỗi tải của `/grading`, `/attempts` hiện đúng trạng thái — bằng chứng: `grading-list.test.tsx` "rỗng, lỗi", `attempt-list.test.tsx` "rỗng, lỗi".
- AC-29: Các màn đúng ở 390px và 1280px, sáng và tối, vi và en, không lẫn ngôn ngữ — bằng chứng: ảnh chụp của `grading-attempts.spec.ts` và `exam-trial.spec.ts` "ảnh màn".
- AC-30: Màn làm thử không nhận đáp án, lời giải, hướng dẫn chấm trước khi nộp: mọi yêu cầu lúc làm bài chỉ gọi API `paper` — bằng chứng: `trial-paper.test.tsx`, ca "không tải đáp án"
- AC-31: Chấm lại một lượt rồi học viên thấy nhãn "Đang chấm lại" kèm điểm cũ; đánh dấu hỏng và cấp bù rồi học viên thấy ghi chú F20 — bằng chứng: `grading-attempts.spec.ts`, kịch bản "chấm lại và lượt hỏng"
- AC-32: Số đếm theo trạng thái khớp API và đổi khi đổi bộ lọc — bằng chứng: `grading-list.test.tsx` "số đếm theo bộ lọc" (mới)
- AC-33: Màn chấm hiện số chữ và yêu cầu tối thiểu; bài chưa đủ số chữ vẫn chấm được — bằng chứng: `grading-item.test.tsx` "số chữ" (mới)
- AC-34: "Làm thử" gọi `trial` đúng một lần, hiện giờ hết hạn; `trial` trả 410 thì báo đề đã hết hạn, không mở đề — bằng chứng: `trial-paper.test.tsx` "kéo dài hạn" (mới)

**Ảnh hưởng ngoài task:**

- Cờ mock trong menu (`apps/web-admin/src/lib/navigation.ts:14,37-39,97,126-136`; tham số thứ ba của `filterNavigation`; `sidebar.tsx:35`) → sửa `navigation.test.ts:183-200`, chạy `navigation.test.ts`, `shell.test.tsx`, `dashboard.test.tsx` [chủ: task này]
- Cổng quyền (`apps/web-admin/src/features/session/permission-gate.tsx`; dùng ở `app/(shell)/grading/layout.tsx`, `app/(shell)/exams/layout.tsx`, `app/(shell)/exams/new/layout.tsx`, `app/(shell)/questions/layout.tsx`) → viết `permission-gate.test.tsx` (mới): có quyền thì hiện, thiếu quyền thì chặn, cả khi biến môi trường cũ còn đặt [chủ: task này]
- Thiết lập test (`apps/web-admin/src/test/setup.ts:6`, `src/test/setup-fake-client.ts:12`, `src/test/fake-api.ts:16,123-129`) → bỏ biến; chạy toàn bộ `pnpm --filter @thi360/web-admin test` ở cả hai project `fake-client`, `default` [chủ: task này]
- Hàm `withRealApi` còn được dùng (`features/question-import/__tests__/files-api.test.ts:16`, `features/question-import/__tests__/question-import-flow.test.tsx:11`, `features/questions/__tests__/api-fixtures.ts:756`, `features/questions/__tests__/asset-upload.test.ts:12`) → giữ hàm, chỉ bỏ `vi.stubEnv`; chạy `pnpm --filter @thi360/web-admin test src/features/question-import src/features/questions` [chủ: task này]
- Test câu hỏi đọc JSON mock (`features/questions/__tests__/api-fixtures.ts:11`, `features/questions/__tests__/bank-readiness.test.ts:4`) → trỏ sang `src/test/fixtures/questions.json` (mới); chạy `pnpm --filter @thi360/web-admin test src/features/questions` [chủ: task này]
- Test của cờ mock (`features/content/__tests__/mock-flags.test.ts`) → xóa cùng `lib/mock/flags.ts`; `mock-removed.test.ts` (mới) thay phần kiểm [chủ: task này]
- Test chấm viết trên mock (`features/grading/__tests__/grading-api.test.ts`, `grading-attempt.test.tsx`, `grading-list.test.tsx`, `grading-logic.test.ts`) → viết lại trên API giả, không dùng `?mockState` [chủ: task này]
- Khung tab bài thi chuyển sang `(editor)` (`app/(shell)/exams/[id]/layout.tsx`, `page.tsx`, `review/`, `structure/`, `versions/`, `listing/` của FA-1) → URL giữ nguyên; chạy `e2e/tests/exam-structure-frame.spec.ts`, `exam-allocation.spec.ts`, `exam-preview-qda.spec.ts`, `exam-listing.spec.ts` [chủ: task này]
- Nút mới ở tab Xem trước (`features/exams/components/exam-preview-panel.tsx`; dùng ở `exam-review-view.tsx`) → chạy `path-labels.test.tsx`, `review-preview-2b.test.tsx`, `review-real-check.test.tsx` [chủ: task này]
- Khóa dịch (`apps/web-admin/src/messages/messages.test.ts`) → chạy `messages.test.ts` sau khi xóa `mockData` và đổi `grading.status.*` [chủ: task này]
- Biến môi trường (`apps/web-admin/.env.example:12-13`, `src/env.d.ts:7`) → `git grep -n THI360_GRADING_MOCK -- apps e2e .github` không còn kết quả [chủ: task này]
- Menu trên trình duyệt (`e2e/tests/rbac.spec.ts`, `e2e/tests/admin-responsive.spec.ts`) → chạy hai spec; `/grading` không còn mở bằng cờ khi thiếu quyền [chủ: task này]
- Đọc đề thử theo `previewId` cho tab mới (openapi chỉ có `POST /api/v1/admin/exams/{id}/preview`; `ExamPreviewQuestion` trả cả `answer`, `solution`, `rubric`, `options[].correct`) → thêm API đọc đề không kèm đáp án, dựa trên `PreviewPaperPort.load`; `exam-trial.spec.ts` "không lộ đáp án" kiểm phản hồi và màn [chủ: AT-5]
- API chấm đề làm thử (`POST /api/v1/admin/exams/{id}/preview/{previewId}/grade`) và bằng chứng mức CSDL không có dòng `attempt`, ghi sổ `PREVIEW` → AT-5 kiểm bằng test tích hợp; `exam-trial.spec.ts` "chấm SP2E Toán" gọi thật [chủ: AT-5]
- Số đếm trạng thái và danh sách lọc của `/grading` (màn đợt 2 có, LT 9.2 không có API) → AT-5 thêm hoặc xác nhận bỏ; `grading-list.test.tsx` kiểm theo quyết định [chủ: AT-5]
- Câu chữ `DESIGN.md` về mock và nhãn trạng thái (`DESIGN.md:326-332,347`) → cập nhật theo code sau task này [chủ: QA]

**Kế hoạch kiểm thử:**

- AC-1: `mock-removed.test.ts`, unit, `pnpm --filter @thi360/web-admin test src/features/grading/__tests__/mock-removed.test.ts`
- AC-2: `grading-list.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/grading/__tests__/grading-list.test.tsx`; "chấm ẩn danh", E2E, `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts`
- AC-3: "tự luận tới điểm tổng", E2E, `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts`
- AC-4: "câu đã có người nhận", unit, `pnpm --filter @thi360/web-admin test src/features/grading/__tests__/grading-attempt.test.tsx`; "hai người chấm", E2E, `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts`
- AC-5: "nhận lại sau 30 phút" (đồng hồ thử của C-0b), E2E, `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts`
- AC-6: "lỗi khi chốt", unit, `pnpm --filter @thi360/web-admin test src/features/grading/__tests__/grading-attempt.test.tsx`
- AC-7: "xung đột phiên bản", unit, `pnpm --filter @thi360/web-admin test src/features/grading/__tests__/grading-attempt.test.tsx`
- AC-8: "mở lại", unit, `pnpm --filter @thi360/web-admin test src/features/grading/__tests__/grading-attempt.test.tsx`
- AC-9: `permission-gate.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/session/__tests__/permission-gate.test.tsx`; "vai trò tùy chỉnh chấm", E2E, `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts`
- AC-10: `attempt-list.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/attempts/__tests__/attempt-list.test.tsx`; "vai trò tùy chỉnh vận hành", E2E, `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts`
- AC-11: `attempt-detail.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/attempts/__tests__/attempt-detail.test.tsx`
- AC-12: "chấm lại", unit, `pnpm --filter @thi360/web-admin test src/features/attempts/__tests__/attempt-detail.test.tsx`
- AC-13: "đánh dấu hỏng", unit, `pnpm --filter @thi360/web-admin test src/features/attempts/__tests__/attempt-detail.test.tsx`
- AC-14: "cấp bù", unit, `pnpm --filter @thi360/web-admin test src/features/attempts/__tests__/attempt-detail.test.tsx`; "đánh dấu hỏng rồi cấp bù", E2E, `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts`
- AC-15: "theo quyền", unit, `pnpm --filter @thi360/web-admin test src/features/attempts/__tests__/attempt-detail.test.tsx`; "vai trò tùy chỉnh vận hành", E2E, `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts`
- AC-16: "mục lượt thi hiện", unit, `pnpm --filter @thi360/web-admin test src/lib/navigation.test.ts`
- AC-17: `trial-entry.test.tsx`, unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/trial-entry.test.tsx`
- AC-18: "mở tab mới", E2E, `pnpm --filter @thi360/e2e test tests/exam-trial.spec.ts`
- AC-19: "chấm SP2E Toán", E2E, `pnpm --filter @thi360/e2e test tests/exam-trial.spec.ts`
- AC-20: "không tạo lượt", E2E, `pnpm --filter @thi360/e2e test tests/exam-trial.spec.ts`
- AC-21: "giữ thứ tự phương án" (sinh thử hai lần cùng `seed`, so `optionOrder` với thứ tự trên màn), E2E, `pnpm --filter @thi360/e2e test tests/exam-trial.spec.ts`
- AC-22: "tự luận BCA FULL", E2E, `pnpm --filter @thi360/e2e test tests/exam-trial.spec.ts`
- AC-23: `trial-paper.test.tsx` "số chữ tối thiểu", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/trial-paper.test.tsx`
- AC-24: "rời trang", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/trial-paper.test.tsx`
- AC-25: "đề thử hết hạn", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/trial-paper.test.tsx`
- AC-26: "không lộ đáp án" (kiểm phản hồi mạng và DOM), E2E, `pnpm --filter @thi360/e2e test tests/exam-trial.spec.ts`
- AC-27: "thiếu quyền xem bài", E2E, `pnpm --filter @thi360/e2e test tests/exam-trial.spec.ts`
- AC-28: "rỗng, lỗi", unit, `pnpm --filter @thi360/web-admin test src/features/grading/__tests__/grading-list.test.tsx src/features/attempts/__tests__/attempt-list.test.tsx`
- AC-29: "ảnh màn" chụp đủ 2 khổ, 2 nền, 2 ngôn ngữ rồi người duyệt đối chiếu bản vẽ, giao diện, `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts tests/exam-trial.spec.ts`
- AC-30: "không tải đáp án", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/trial-paper.test.tsx`
- AC-31: "chấm lại và lượt hỏng", E2E, `pnpm --filter @thi360/e2e test tests/grading-attempts.spec.ts`
- AC-32: `grading-list.test.tsx` "số đếm theo bộ lọc", unit, `pnpm --filter @thi360/web-admin test src/features/grading/__tests__/grading-list.test.tsx`
- AC-33: `grading-item.test.tsx` "số chữ", unit, `pnpm --filter @thi360/web-admin test src/features/grading/__tests__/grading-item.test.tsx`
- AC-34: `trial-paper.test.tsx` "kéo dài hạn", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/trial-paper.test.tsx`
- Hồi quy:
  - `pnpm --filter @thi360/web-admin test`
  - `pnpm --filter @thi360/e2e test tests/rbac.spec.ts tests/admin-responsive.spec.ts tests/exam-structure-frame.spec.ts tests/exam-allocation.spec.ts tests/exam-preview-qda.spec.ts tests/exam-listing.spec.ts`
  - `git grep -n THI360_GRADING_MOCK -- apps e2e .github` không in dòng nào
  - `pnpm lint`, `pnpm typecheck`, `pnpm build`
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: bài SP2E Toán (`SP2E_MATH_2025`, `SP2E_MATH_2026`) và BCA FULL do DS nạp, chỉ đọc; câu có `shuffleOptions = false` lấy từ DS, thiếu thì spec tạo câu riêng có tiền tố `FA4-`; tài khoản học viên, người chấm và vai trò tùy chỉnh có tiền tố `FA4-`; E2E chạy `workers: 1`.
- Không kiểm: không có dòng `attempt` ở mức CSDL (AT-5 kiểm); đúng sai của từng quy tắc chấm (AT-4 kiểm); so ảnh tự động vì repo chưa có `toHaveScreenshot`, ảnh do người duyệt đối chiếu.


## FA-5 · Màn Sinh thử dùng giao diện câu hỏi chung

**Spec:** BS 12, 17.2; LT 8; 2B H17.

**Sản phẩm:**

- `ExamPreviewPaper` hiện đề sinh thử bằng thành phần câu hỏi của `packages/ui` ở chế độ `review`. Dữ liệu chỉ admin thấy (đáp án, nguồn, kết quả kiểm tra ma trận) vẫn giữ.
- Màn làm thử dùng trình phát audio của FS-7, đếm lần nghe trên trình duyệt, không ghi vào DB (BS 17.2). Phát audio qua `GET /api/v1/admin/question-assets/{id}` đã có.
- Kiểm lại màn Sinh thử trên đề của đủ 9 kỳ thi.

**Đường dẫn sở hữu:**

- `apps/web-admin/src/features/exams/components/exam-preview-paper.tsx`, `apps/web-admin/src/features/exams/__tests__/exam-preview-paper*.test.tsx`
- thêm vào: `apps/web-admin/src/features/exams/trial/**` (trình phát audio)
- `e2e/tests/exam-preview-nine-kinds.spec.ts` (mới)
- thêm vào: `apps/web-admin/src/messages/vi.json`, `apps/web-admin/src/messages/en.json` (`exams.preview.*`)

**Ngoài phạm vi:** Thành phần câu hỏi và trình phát (FS-4, FS-7). API sinh thử (đợt 2B). Sửa `PaperOrdering`.

**Nghiệm thu:**

- AC-1: Đủ 8 dạng câu hiện bằng thành phần dùng chung ở chế độ `review`, có đáp án, nguồn và đúng thứ tự phương án của đề sinh thử — bằng chứng: `exam-preview-paper.test.tsx` "tám dạng câu" (mới)
- AC-2: Không còn mã hiển thị câu riêng của màn Sinh thử: tệp chỉ import từ `@thi360/ui/question` — bằng chứng: `exam-preview-paper.test.tsx` "dùng thành phần chung" (mới)
- AC-3: Sinh thử trên đề của HSA, QDA, BCA, V-ACT, SP2E, SPT, THPTQG, H-SCA, V-SAT: mở được, đủ số câu, không lỗi trên trang — bằng chứng: `exam-preview-nine-kinds.spec.ts` "chín kỳ thi" (mới)
- AC-4: Làm thử bài có audio: nghe tối đa hai lần, lần thứ ba bị khóa, không gọi API ghi lần nghe — bằng chứng: `trial-audio.test.tsx` "hai lần nghe" (mới)
- AC-5: Màn Sinh thử đạt ở sáng và tối, vi và en, 390px và 1280px — bằng chứng: `exam-preview-nine-kinds.spec.ts` "ma trận giao diện" (mới)

**Ảnh hưởng ngoài task:**

- Màn làm thử của FA-4 thêm trình phát → chạy lại `exam-trial.spec.ts` [chủ: task này]
- Tab Xem trước và bộ giải thích ma trận dùng `ExamPreviewPaper` → chạy lại `exam-preview*.test.tsx` [chủ: task này]

**Kế hoạch kiểm thử:**

- AC-1: `exam-preview-paper.test.tsx` "tám dạng câu", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/exam-preview-paper.test.tsx`
- AC-2: `exam-preview-paper.test.tsx` "dùng thành phần chung", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/exam-preview-paper.test.tsx`
- AC-3: `exam-preview-nine-kinds.spec.ts` "chín kỳ thi", E2E, `pnpm --filter @thi360/e2e test tests/exam-preview-nine-kinds.spec.ts`
- AC-4: `trial-audio.test.tsx` "hai lần nghe", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/trial-audio.test.tsx`
- AC-5: `exam-preview-nine-kinds.spec.ts` "ma trận giao diện", E2E, `pnpm --filter @thi360/e2e test tests/exam-preview-nine-kinds.spec.ts`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - `pnpm --filter @thi360/web-admin test`
  - `pnpm --filter @thi360/e2e test tests/exam-trial.spec.ts`
- Dữ liệu test: bài của DS cho đủ 9 kỳ thi; tài khoản `sys.admin`; đề sinh thử tạo trong từng ca.
- Không kiểm: nội dung từng đề so với ngân hàng câu hỏi (đã kiểm ở đợt 2B).

## LP · Tab Hồ sơ học tập nối dữ liệu thật

**Spec:** BS 13; plan B1.

**Sản phẩm:**

- Đổi `StudentLearningData` (record lồng trong `StudentLearningProvider`) và `StudentLearningResponse` sang kiểu rõ ràng: `entitlements[]` (bài, số lượt đã cấp, đã dùng, còn lại, trạng thái) và `attempts[]` (mã lượt, tên bài, trạng thái, thời điểm bắt đầu, điểm khi có). Bản cài của CM-4a, AT-1 trả đúng kiểu này.
- Khối **Quyền sử dụng** và **Lịch sử lượt thi** của tab Hồ sơ học tập hiện dữ liệu từ `GET /api/v1/admin/users/{id}/learning`. Ba khối còn lại giữ trống.

**Đường dẫn sở hữu:**

- thêm vào: `backend/src/main/java/vn/unicom/thi360/identity/spi/StudentLearningProvider.java` (record lồng `StudentLearningData`), `identity/api/StudentLearningResponse.java` (kiểu phần tử rõ ràng), `identity/application/UserAdminService.java` (ánh xạ), các bản cài `StudentLearningProvider` của `commerce`, `attempt`
- `backend/src/test/java/vn/unicom/thi360/identity/application/StudentLearningProviderTests.java`
- `backend/src/test/java/vn/unicom/thi360/identity/AdminUserLearningIT.java` (mới)
- `apps/web-admin/src/features/users/components/learning-profile-tab.tsx`, `apps/web-admin/src/features/users/components/learning-profile-tab.test.tsx`
- thêm vào: `apps/web-admin/src/messages/vi.json`, `apps/web-admin/src/messages/en.json` (`users.learning.*`)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** Ba khối còn lại của tab. Sửa quyền dùng hoặc lượt thi từ tab này.

**Nghiệm thu:**

- AC-1: API trả đủ quyền dùng và lượt thi của đúng học viên; tài khoản chưa có dữ liệu giữ `dataAvailable = false` — bằng chứng: `AdminUserLearningIT#returnsEntitlementsAndAttempts`, `#emptyStudentHasNoData` (mới)
- AC-2: Quyền của API giữ như hiện tại: thiếu quyền nhận 403, không đăng nhập nhận 401; phản hồi không có đáp án và câu trả lời — bằng chứng: `AdminUserLearningIT#permissionUnchanged`, `#noAnswerDataLeaks` (mới)
- AC-3: Hai khối hiện dữ liệu thật, trạng thái rỗng và lỗi đúng; ba khối còn lại vẫn trống — bằng chứng: `learning-profile-tab.test.tsx` "hai khối có dữ liệu" (mới)

**Ảnh hưởng ngoài task:**

- `learning-profile-tab.test.tsx:185` đang khẳng định năm khối luôn trống → đổi theo hành vi mới [chủ: task này]
- `StudentLearningData`, `StudentLearningResponse` đổi từ `List<Object>` sang kiểu rõ ràng, nên OpenAPI đổi (thay ghi chú "OpenAPI không đổi" của CM-4a) → chạy lại `StudentLearningProviderTests`, `AttemptSpiIT`, test của CM-4a; màn dùng kiểu sinh ra qua `pnpm typecheck` [chủ: task này]
- OpenAPI → `OpenApiExportIT`, `pnpm typecheck` [chủ: task này]

**Kế hoạch kiểm thử:**

- AC-1: `AdminUserLearningIT#returnsEntitlementsAndAttempts`, `#emptyStudentHasNoData` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AdminUserLearningIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `AdminUserLearningIT#permissionUnchanged`, `#noAnswerDataLeaks` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=AdminUserLearningIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `learning-profile-tab.test.tsx` "hai khối có dữ liệu", unit, `pnpm --filter @thi360/web-admin test src/features/users/components/learning-profile-tab.test.tsx`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - `cd backend && ./mvnw -B -ntp verify -Dit.test=AttemptSpiIT,AdminUserLearningIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `cd backend && ./mvnw -B -ntp test -Dtest=StudentLearningProviderTests`
  - `cd backend && ./mvnw -B -ntp verify -Dit.test=OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`; `pnpm --filter @thi360/api-client generate && pnpm typecheck`
- Kiểm thử rủi ro:
  - dữ liệu cá nhân: học viên khác không lẫn vào kết quả, phản hồi không có email ngoài dữ liệu tài khoản đã có;
  - quyền: từng quyền của API khi có và khi thiếu;
  - đỏ trước: AC-1, AC-2.
- Dữ liệu test: tài khoản học viên có quyền dùng và lượt đã nộp dựng bằng hỗ trợ test của CM-4a, AT-1; API giả trong vitest cho trạng thái rỗng và lỗi.
- Không kiểm: ba khối còn lại của tab.

## FU · Việc tồn đọng của đợt 2B (UNI-2700)

**Spec:** UNI-2700; `D:/.Vietants_Project/.thi360-dev/dot-2b-cook/reports/final/follow-ups.md`; hồ sơ nghiệm thu QA đợt 2B mục 8 (`reports/QA/acceptance.md`); đặc tả và plan đợt 2B. M.Trường giao ngày 2026-11-19: "giao cả đợt 3 làm thành 1 task".

**Sản phẩm:**

- Danh sách nguồn có 45 dòng (review vòng 4, P4-7): 4 dòng sang TS (FU-QA-SEEDS, FU-SEEDGEN-1, FU-BE13-2, FU-FE4-D9), 1 dòng đóng vì đúng thiết kế (FU-FE1-1; FU-TEMPLATE-I18N cũng đóng theo cùng quyết định), còn **40 dòng do FU xử lý**. Hai tiểu mục F-7 của FU-FE2-3 và V1-01 của FU-FE3AA-V1 cũng đóng vì đúng thiết kế, không phải dòng riêng. Lead có thể chia cho worker backend, giao diện và tài liệu chạy song song.
- **Kiểm tra đề, đúng và hiệu năng (10):** FU-BE15-R6-1 (tóm tắt và bộ kiểm dùng chung một luật nhóm phần dư), FU-BE15-R1-5 (đọc phép chiếu theo khóa `publishable`, `fixed_counts` thay vì cả JSON mẫu), FU-BE15-R2-3..5 (phát hiện mẫu đổi giữa lúc kiểm và lúc lưu), FU-BE15-OPTION-BLOCKS, FU-BE9-1 (sửa danh mục chỉ giải lại môn bị ảnh hưởng), FU-BE9-2 (0,2 điểm mỗi ý đúng sai SP2E: chỉ kiểm khi câu được chọn vào khối Đúng/Sai của bài SP2E **mà mẫu khối chấm `PER_STATEMENT`**, ở bộ lọc nguồn và ở bộ kiểm tra đề; khối SP2E chấm `COUNT_TABLE` giữ luật theo bảng đã duyệt, không áp 0,2; điều kiện xét cả kỳ thi lẫn phương pháp chấm của khối, không suy từ kỳ thi SP2E đơn độc; không đổi mẫu `COUNT_TABLE` nào sang `PER_STATEMENT`; không kiểm lúc lưu ngân hàng, nên kỳ thi khác không bị áp; review vòng 5, P5-2), FU-BE12-1, FU-BE12-2, FU-BE13-3 (R3-1: bài V-ACT chấm thô nạp mẫu bài ôn thì giữ cách chấm, `scoringReset = false`), FU-QA-OMIT-LOCK-SHAPES (khóa nhóm bỏ ở bước 2 khớp `ExamCheckRules.reenabledBlocks`).
- **Hợp đồng API và thông điệp (4):** FU-QA-PREVIEW-LEGACY (`GeneratePreviewRequest` từ chối `pathId`, `subjectCodes` ở gốc bằng 400), FU-QA-ERROR-MESSAGES, FU-QA-OPENAPI-TYPES, FU-BE5-2 (con đệ quy của `ExamStructureSummaryRow` có trong OpenAPI, web-admin bỏ kiểu khai báo tay).
- **Giao diện soạn đề (12):** FU-NAVTABS-REVEAL, FU-FE5-R2-01, FU-FE3B-V03, FU-FE4A-R1-4, FU-FE4A-R1-7, FU-FE2-3 (FE2-R2-01, FE2-R2-02, F-8), FU-FE3AA-V1 (V1-02 đến V1-04), FU-FE5-R3-01, FU-FE3B-1..3, FU-FE4-D3D4, FU-FE4-PREVIEW (kèm trường backend cần thêm: đếm ngược thời gian giữ, giá trị gốc của câu bị bỏ, tên chủ đề; bấm lỗi mở đúng ô; `mapBlock` ánh xạ đủ ba trường lọc), FU-FE2-1 (API trả dạng câu của từng phần mẫu để DoiPhienBan2B hiện dòng thay đổi theo dạng câu; nút xác nhận chuyên gia **không** làm, ngoài phạm vi như ở 2B).
- **Dữ liệu và mẫu (5):** FU-FE1-2 (ô chọn môn ở bước 1 chỉ có môn có mẫu, không trùng), FU-BE6-3 (mẫu đang dùng còn id phần không phải UUID thì bù bằng migration), V1-04 (dữ liệu MAPID cho môn ngoài Toán), FU-BE7-1 (ghi lại cho BA, không đổi mô hình), FU-BE10-ASSET (câu BCA trong seed có tệp đính kèm thật).
- **Tài liệu và bản vẽ (5):** FU-QA-DOC-R2-01, FU-QA-DOC-R2-02, FU-DESIGN-1, FU-DESIGN-2, FU-G2B-CANVAS.
- **Cần bằng chứng hoặc BA (2):** FU-QA-PREVIEW-COMPLIANCE-VERSION: viết test chứng minh trước, có lỗi thì sửa, không có thì giữ test làm hồi quy. FU-RELOAD-EXAM-CLOCK: không sửa mã (M.Trường giữ hành vi hiện tại), chỉ ghi chờ BA.
- **Kiểm rồi đóng (2):** FU-PBI04-VACT-THPT (đã làm ở UNI-2671), FU-BE5-1 (đã làm ở BE-19, BE-20).
- Bảng đóng việc `reports/FU/close-list.md` trong thư mục đợt: mỗi mục một dòng với kết quả và bằng chứng; cùng nội dung đăng lên UNI-2700.

**Đường dẫn sở hữu:**

- thêm vào: `backend/src/main/java/vn/unicom/thi360/exam/**` (kiểm tra đề, tóm tắt, xem trước, API soạn đề)
- thêm vào: `backend/src/main/resources/db/migration/exam/V<yyyyMMddHHmm>__backfill_template_part_ids.sql` (mới, nếu FU-BE6-3 còn mẫu cần bù)
- thêm vào: `backend/src/main/resources/db/devdata/R__seed_questionbank_dev_data.sql` (siêu dữ liệu tệp của câu BCA, dữ liệu MAPID)
- `backend/src/main/resources/db/devdata/assets/bca/**` (mới, byte tệp của câu BCA); không sửa loader: `questionbank/application/DevQuestionAssetSeeder.java` đọc `db/devdata/assets/` + `file_name`, nên metadata câu BCA ghi `file_name = bca/<tệp>` là đủ (review vòng 5, P5-1). Nếu thật sự phải sửa lớp đó, báo Advisor trước: DS cũng sửa lớp này (thêm profile `e2e`), FU rebase sau DS và không tạo seeder thứ hai
- thêm vào: `backend/src/main/resources/messages.properties`, `messages_vi.properties`, `messages_en.properties`
- `backend/src/test/java/vn/unicom/thi360/exam/followup/**` (mới)
- thêm vào: `apps/web-admin/src/features/exams/**` (trừ phần EX-1, FA-1 đã đổi; nộp sau khi FA-1 merge và đã merge nhánh tích hợp, xem C1)
- `apps/web-admin/src/features/exams/__tests__/follow-ups-2b.test.tsx` (mới), `e2e/tests/exam-authoring-followups.spec.ts` (mới)
- thêm vào: thành phần `NavTabs` của web-admin
- thêm vào: `apps/web-admin/src/messages/vi.json`, `en.json` (khối riêng, khi đổi nhãn hay gợi ý soạn đề)
- sửa (review vòng 4, P4-3): `backend/src/test/java/vn/unicom/thi360/exam/**/ExamPreview*IT.java` có lời gọi dùng `pathId`, `subjectCodes` ở gốc: chuyển các ca sinh đề và phiên bản hợp lệ sang `target`, giữ assert cũ về đề sinh ra; ca riêng kiểm thân cũ nhận 400
- thêm vào: `docs/specs/2026-11-17-pbi04-dot-2b.md` (hai câu chữ), `docs/design/dot-2b/**` (`support.js`)
- sinh lại: `packages/api-client/openapi.json`, `packages/api-client/src/schema.d.ts`

**Ngoài phạm vi:** Các mục đã sang TS hoặc đã đóng vì đúng thiết kế. Đổi hành vi nạp lại đồng hồ bài (FU-RELOAD-EXAM-CLOCK) khi BA chưa trả lời. Đổi mô hình phân bố tham chiếu của SP2E (FU-BE7-1). Mẫu đề mới.

**Nghiệm thu:**

- AC-1: Bộ kiểm và tóm tắt cho cùng kết quả với nháp có nhóm phần dư; khối ở mức phương án được so với mẫu khi khóa số câu; khóa nhóm bỏ ở bước 2 khớp `reenabledBlocks` với nhóm bỏ trong `subjectPool`, khối phương án, section phương án không đứng đầu, mã trùng; mẫu đổi giữa lúc kiểm và lúc lưu thì kết quả kiểm không được lưu như hợp lệ — bằng chứng: `ExamCheckFollowUpIT#summaryAndCheckerShareRemainderRule`, `#optionBlocksComparedForCountLock`, `#omitLockMatchesReenabledBlocks`, `#templateChangeBetweenCheckAndSaveDetected` (mới)
- AC-2: Chẩn đoán thiếu câu của bộ lọc linh hoạt dừng ở hạn kiểm tra và giữ lỗi thiếu cơ bản; mục tiêu linh hoạt lớn với bộ lọc theo vị trí và ít câu cho `INVALID`, `SET_SHORTAGE`, không gán sai cho bộ lọc; câu Đúng/Sai chia điểm đều 0,2 mỗi ý được chọn vào khối SP2E chấm `PER_STATEMENT`, câu chia lệch bị bộ lọc nguồn của khối đó bỏ qua (bài vẫn hợp lệ nếu còn đủ câu hợp lệ khác; chỉ báo thiếu khi không đủ) và bộ kiểm báo lỗi khi câu lệch đã nằm trong khối; khối SP2E chấm `COUNT_TABLE` vẫn chọn, kiểm và sinh thử được như cũ; cùng câu chia lệch vẫn dùng được ở THPTQG; bài V-ACT chấm thô nạp mẫu bài ôn giữ cách chấm, `scoringReset = false` (R3-1 của BE-13) — bằng chứng: `ExamCheckFollowUpIT#flexibleDiagnosticStopsAtDeadline`, `#largeFlexibleTargetReportsSetShortage`, `#sp2eTrueFalseStatementPoints`, `#sp2eCountTableBlockUnaffected`, `#unevenStatementsAllowedOutsideSp2e`, `#be13R31Regression` (mới)
- AC-3: Kiểm tra đọc mẫu bằng phép chiếu theo khóa, không tải cả JSON; sửa một mục danh mục chỉ giải lại các môn dùng mục đó — bằng chứng: `ExamCheckPerformanceIT#checkReadsKeyedTemplateProjection`, `#catalogEditResolvesAffectedSubjectsOnly` (mới)
- AC-4: Xem trước tuân thủ của một phiên bản đã công bố đọc cấu trúc của phiên bản đó, không đọc bản đang sửa (test viết trước, đỏ nếu lỗi có thật) — bằng chứng: `PreviewComplianceVersionIT#publishedVersionPreviewReadsPublishedStructure` (mới)
- AC-5: `GeneratePreviewRequest` có `pathId` hoặc `subjectCodes` ở gốc nhận 400; `trueFalseScoring`, `structure` và con đệ quy của `ExamStructureSummaryRow` có kiểu rõ trong OpenAPI — bằng chứng: `ExamApiFollowUpIT#legacyPreviewFieldsRejected`, `#openApiHasTypedStructureFields` (mới)
- AC-6: `CHECK_RUN_SCOPE_CONFLICT`, `SECTION_NOT_FOUND` có thông điệp ở cả ba tệp — bằng chứng: `MessageBundlesTest#followUpCodesHaveMessages` (mới)
- AC-7: Các lỗi giao diện soạn đề của 2B đã sửa: NavTabs cuộn thẻ đang chọn vào khung ở 390px, vùng `role=status` của Ngữ văn được đọc khi nội dung đổi, hàng tham chiếu đang tải không hiện như lệch, danh sách thay đổi của nháp chỉ đổi thứ tự không rỗng, không nháy hàng thao tác, bảng ma trận không ẩn `PERCENT_TOTAL` khi còn chặn lưu, form về đúng chế độ sau khi xóa chip cuối, bước 1 tìm phần không phải tách khóa hàng, web-admin dùng kiểu sinh từ OpenAPI — bằng chứng: `follow-ups-2b.test.tsx` "lỗi soạn đề 2B" (mới)
- AC-8: Ở 390px và 1280px, sáng và tối, vi và en: thẻ câu hỏi, bảng nhóm câu, bảng tóm tắt, bảng chỉ tiêu, bảng môn không tràn ngang; KiemTra2B và SinhThu2B khớp bản vẽ (thanh thẻ, nút đầu trang, dải tóm tắt, phạm vi dạng thẻ chọn); xem trước có đếm ngược và giá trị gốc của câu bị bỏ — bằng chứng: `exam-authoring-followups.spec.ts` "giao diện soạn đề 2B" (mới)
- AC-12: Xem trước hiện tên chủ đề từng hàng; bấm một lỗi đưa tiêu điểm vào đúng ô của lỗi đó (không chỉ mở khối); `mapBlock` ánh xạ đủ ba trường lọc; DoiPhienBan2B hiện dòng thay đổi theo dạng câu của từng phần lấy từ API — bằng chứng: `follow-ups-2b.test.tsx` "xem trước và đổi phiên bản 2B" (mới), `ExamApiFollowUpIT#templatePartQuestionTypesExposed` (mới)
- AC-9: Sau migration, không mẫu đang dùng nào còn id phần không phải UUID; ô chọn môn của bước 1 chỉ có môn có mẫu, không trùng; mỗi nút MAPID chọn được ở ít nhất một môn ngoài Toán có câu; câu BCA trong seed có tệp đọc được trong kho — bằng chứng: `ExamDataFollowUpIT#activeTemplatesUseUuidPartIds`, `#stepOneSubjectsHaveTemplates`, `#mapidNodesHaveQuestions`, `#bcaSeedAttachmentsAreStored`, `#emptyStoreReseedsWithoutError` (mới)
- AC-10: Hai câu chữ của đặc tả BE đợt 2B được sửa (FU-QA-DOC-R2-01, FU-QA-DOC-R2-02); trang thiết kế `docs/design/dot-2b` có `support.js` trong repo nên hiện đủ dữ liệu, không còn `{{...}}` thô (FU-DESIGN-1, FU-DESIGN-2); sáu bản vẽ G2b có trên canvas thiết kế chung (FU-G2B-CANVAS) — bằng chứng: ảnh chụp các trang thiết kế trong báo cáo lane (mới)
- AC-11: Mỗi dòng trong 40 dòng FU xử lý có kết quả ghi ở bảng đóng việc và bình luận `[agent]` trên UNI-2700: đã sửa (kèm tên test), đã kiểm và đóng (kèm bằng chứng), hoặc chờ BA (FU-RELOAD-EXAM-CLOCK, FU-BE7-1); không mục nào bỏ trống — bằng chứng: `reports/FU/close-list.md` trong thư mục đợt và bình luận trên UNI-2700 (mới)

**Ảnh hưởng ngoài task:**

- Luật kiểm tra đề của đợt 2B đổi → chạy lại mọi `ExamCheck*IT`, `ExamPreview*IT` và E2E soạn đề của đợt 2B [chủ: task này]
- Bỏ thân xem trước cũ (`pathId`, `subjectCodes` ở gốc) → tìm mọi nơi gọi (`git grep` trong `backend/src/test`, `apps/web-admin`, `e2e`), phân loại trong báo cáo, chuyển ca hợp lệ sang `target` mà không đổi assert về đề sinh ra [chủ: task này]
- FA-1 cùng sửa màn soạn đề ở sóng 3 → FU nộp sau FA-1, chạy lại `pnpm --filter @thi360/web-admin test` và E2E soạn đề trên SHA có EX-1, EX-2, FA-1 và FU [chủ: task này]
- EX-1, EX-2 đã merge sửa cùng module `exam` → chạy `PublishedExamPortIT`, `PreviewPaperPortIT` trên SHA có cả hai [chủ: task này]
- TS đã dựng lại seed `TEST-*` → chạy `TestExamSeedIT`, `TestExamSeedComplianceIT`; dữ liệu MAPID, tệp BCA không đổi số câu của bài `TEST-*` [chủ: task này]
- OpenAPI đổi → `OpenApiExportIT`, `pnpm --filter @thi360/api-client generate && pnpm typecheck` [chủ: task này]
- Tệp chung `messages*.properties` → `MessageBundlesTest` [chủ: task này]
- Màn soạn đề của web-admin đổi → `pnpm --filter @thi360/web-admin test` [chủ: task này]

**Kế hoạch kiểm thử:**

- AC-1: `ExamCheckFollowUpIT#summaryAndCheckerShareRemainderRule`, `#optionBlocksComparedForCountLock`, `#omitLockMatchesReenabledBlocks`, `#templateChangeBetweenCheckAndSaveDetected` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamCheckFollowUpIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-2: `ExamCheckFollowUpIT#flexibleDiagnosticStopsAtDeadline`, `#largeFlexibleTargetReportsSetShortage`, `#sp2eTrueFalseStatementPoints`, `#sp2eCountTableBlockUnaffected`, `#unevenStatementsAllowedOutsideSp2e`, `#be13R31Regression` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamCheckFollowUpIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-3: `ExamCheckPerformanceIT#checkReadsKeyedTemplateProjection`, `#catalogEditResolvesAffectedSubjectsOnly` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamCheckPerformanceIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-4: `PreviewComplianceVersionIT#publishedVersionPreviewReadsPublishedStructure` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PreviewComplianceVersionIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-5: `ExamApiFollowUpIT#legacyPreviewFieldsRejected`, `#openApiHasTypedStructureFields` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamApiFollowUpIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `MessageBundlesTest#followUpCodesHaveMessages` (mới), unit, `cd backend && ./mvnw -B -ntp test -Dtest=MessageBundlesTest`
- AC-7: `follow-ups-2b.test.tsx` "lỗi soạn đề 2B", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/follow-ups-2b.test.tsx`
- AC-8: `exam-authoring-followups.spec.ts` "giao diện soạn đề 2B", E2E, `pnpm --filter @thi360/e2e test tests/exam-authoring-followups.spec.ts`
- AC-9: `ExamDataFollowUpIT#activeTemplatesUseUuidPartIds`, `#stepOneSubjectsHaveTemplates`, `#mapidNodesHaveQuestions`, `#bcaSeedAttachmentsAreStored`, `#emptyStoreReseedsWithoutError` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamDataFollowUpIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: `follow-ups-2b.test.tsx` "xem trước và đổi phiên bản 2B", unit, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/follow-ups-2b.test.tsx`; `ExamApiFollowUpIT#templatePartQuestionTypesExposed` (mới), tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=ExamApiFollowUpIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: ảnh chụp các trang thiết kế, kiểm tra tay của Advisor, `git grep -n "{{" docs/design/dot-2b` không còn chỗ hiện thô
- AC-11: `reports/FU/close-list.md` "đủ 45 dòng nguồn: 4 TS, 1 đóng, 40 FU", kiểm tra tay của Advisor, đối chiếu bảng với `D:/.Vietants_Project/.thi360-dev/dot-2b-cook/reports/final/follow-ups.md`
- Hồi quy: chạy mọi dòng Ảnh hưởng ngoài task:
  - `cd backend && ./mvnw -B -ntp verify -Dit.test=PublishedExamPortIT,PreviewPaperPortIT,TestExamSeedIT,TestExamSeedComplianceIT,OpenApiExportIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - `cd backend && ./mvnw -B -ntp verify`; `pnpm --filter @thi360/web-admin test`; `pnpm --filter @thi360/e2e test`
- Kiểm thử rủi ro:
  - đỏ trước: AC-1, AC-2, AC-4, AC-5, AC-9 (id phần), AC-12;
  - biên: nhóm phần dư rỗng, mẫu đổi đúng lúc lưu, hạn kiểm tra đúng mốc, 390px;
  - dữ liệu đã có: migration bù id phần chạy trên mẫu id không phải UUID đang có bài dùng; bài giữ tham chiếu đúng sau migration (`ExamDataFollowUpIT#backfillKeepsExamReferences`, mới);
  - đồng thời: kiểm tra đua với lưu khi mẫu đổi (`ExamCheckFollowUpIT#templateChangeBetweenCheckAndSaveDetected`);
  - quyền: API xem trước và API soạn đề đổi vẫn trả 401, 403 như cũ (`ExamApiFollowUpIT#accessUnchanged`, mới);
  - hiệu năng: đo thời gian kiểm tra bài HSA FULL trước và sau, ghi vào báo cáo lane.
- Dữ liệu test: seed dev sau TS; mẫu do migration nạp; tài khoản `sys.admin`, `content.admin`; MinIO trong Testcontainers cho tệp BCA.
- Không kiểm: câu trả lời của BA cho FU-RELOAD-EXAM-CLOCK, FU-BE7-1.

## QA · Nghiệm thu đợt

**Spec:** toàn bộ TM và LT; trọng tâm TM 14, 15; LT 13, 14. Phụ thuộc cứng: cả 42 task còn lại.

**Sản phẩm:**

- **E2E đầu cuối** (dữ liệu DS, số liệu từ `docs/specs/2026-11-17-de-test-gia-dinh.md`):
  - khách → đăng ký → xác minh → mua (cổng giả lập) → làm HSA FULL thu nhỏ → kết quả → lịch sử;
  - voucher 100% (tạo ở FA-2, dùng ở FS-3);
  - BCA có tự luận → chấm → điểm;
  - hoàn tiền → thu hồi lượt;
  - H-SCA Tiếng Anh: nghe hai lần → nộp → kết quả; V-SAT Tiếng Anh: phân đoạn → kết quả theo phân đoạn;
  - THPTQG: chuyển tự do giữa các phần → nộp;
  - lần 1 ghi thất bại → lần 2 thành công → VNPAY báo lần 1 thành công → admin hoàn khoản thu thừa → học viên giữ bài.
- **Kiểm thử bảo mật:**
  - quét `ForbiddenKeysAssert` trên mọi API học viên và công khai;
  - IDOR trên `/me/**`;
  - giới hạn tần suất của endpoint công khai;
  - `/api/v1/test/**` trả 404 khi không có profile `e2e`;
  - không có bí mật trong repo (lệnh ở A7).
- **Tài liệu:**
  - README triển khai: biến môi trường mới, Mailpit, VNPAY, worker, yêu cầu IPN tới thẳng backend (B6), bắt buộc bật profile `prod` ở môi trường thật (chốt chặn `e2e` và `MOCK` dựa vào profile này), `THI360_TRUST_REAL_IP` cùng `x-real-ip`, tạo bucket `thi360-avatars`;
  - `ARCHITECTURE.md` (module mới);
  - `DESIGN.md` (màn làm bài; nhãn "Chờ chấm" theo `PENDING_REVIEW`; bỏ phần mock của mục 5.14 sau FA-4).
- Cập nhật `TASK.md`, `PROGRESS.md`.
- **Khung đóng đợt**, chạy trên SHA cuối của `integration/dot-3`:
  1. Đi hết đặc tả: mỗi tiêu chí có task làm, test chứng minh, kết quả trên SHA cuối.
  2. Chạy đủ mọi kiểm tra, ghi số test so với gốc của đợt (`develop` lúc tách nhánh), kể cả nhóm `perf`.
  3. Chạy lại mọi dòng "Ảnh hưởng ngoài task" của mọi task.
  4. Luồng xuyên task, gồm đường lỗi: IPN lặp chỉ một quyền; tiền về sau khi đơn hết hạn; nộp trùng lúc hết giờ; phần 3 sinh lỗi → đánh dấu hỏng → cấp bù; hoàn tiền khi có lượt đang làm.
  5. Quét giao diện: 390px và 1280px, sáng và tối, vi và en.
  6. Đặc tả, plan và tài liệu dự án khớp với code; sai khác đã quyết được ghi lại kèm mã quyết định.
  7. Đóng sổ: mỗi việc hoãn, phát hiện để sau, test chập chờn, test cách ly có một trong ba kết cục: đã sửa, có mục theo dõi kèm người chịu trách nhiệm, hoặc quyết định không làm.
  8. Báo cáo QA theo `assets/qa-report-template.md` của `zone17-builder`, lưu ở `D:/.Vietants_Project/.thi360-dev/dot-3/`.
- **Khi tới QA vẫn thiếu G2:** Advisor xin M.Trường duyệt yêu cầu thay đổi tách VP-1, VP-2 khỏi đợt. QA chạy với cổng giả lập, báo cáo ghi rõ mẫu số 136 điểm (138 − 2). VP thành việc riêng sau đợt. Không đưa về `develop` với `THI360_VNPAY_MODE=SANDBOX` mặc định.
- QA tìm lỗi, không làm tính năng. Lỗi nhỏ sửa theo quy tắc phát hiện nhỏ; lỗi lớn tách thành task sửa.

**Đường dẫn sở hữu:**

- `e2e/tests/qa-*.spec.ts` (mới)
- `backend/src/test/java/vn/unicom/thi360/qa/**` (mới)
- `README.md`, `ARCHITECTURE.md`, `DESIGN.md`, `TASK.md`, `PROGRESS.md`
- thêm vào: `docs/plans/2026-11-17-thuong-mai-luot-thi-pbi-05-13.md`, `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md`, `docs/specs/2026-11-16-luot-thi-pbi-10-13.md` (chỉ ghi sai khác đã quyết)
- `D:/.Vietants_Project/.thi360-dev/dot-3/qa-report.md` (mới, ngoài repo)
- `e2e/tests/qa-nine-kinds.spec.ts` (mới), `e2e/tests/qa-payment-rules.spec.ts` (mới)

**Ngoài phạm vi:** tính năng mới; sửa lỗi lớn (thành task sửa riêng); luồng sandbox VNPAY thật (VP-2); trả lời các câu hỏi còn mở (người được nêu trong từng câu quyết định); merge vào `develop` (chờ M.Trường yêu cầu).

**Nghiệm thu:**

- AC-1: Mọi tiêu chí của đặc tả có bằng chứng trên SHA cuối; tiêu chí không có bằng chứng là lỗi — bằng chứng: bảng nghiệm thu trong báo cáo QA.
- AC-2: Lệnh chuẩn và E2E xanh trên đầu `integration/dot-3`, số test không thấp hơn gốc nếu không có lý do ghi rõ — bằng chứng: mục 3 của báo cáo QA với lệnh, mã thoát, số chạy, số bỏ qua.
- AC-3: Mọi dòng ảnh hưởng của mọi task đạt trên SHA cuối — bằng chứng: mục 4 của báo cáo QA.
- AC-4: Luồng khách → đăng ký → xác minh → mua (cổng giả lập) → làm HSA FULL thu nhỏ → kết quả → lịch sử chạy trọn — bằng chứng: `e2e/tests/qa-guest-purchase-hsa.spec.ts` (mới).
- AC-5: Voucher 100% tạo ở màn admin, dùng khi mua, cấp quyền ngay không qua cổng thanh toán — bằng chứng: `e2e/tests/qa-voucher-100.spec.ts` (mới).
- AC-6: Bài BCA có tự luận: nộp → chờ chấm → chấm → học viên thấy điểm — bằng chứng: `e2e/tests/qa-bca-essay.spec.ts` (mới).
- AC-7: Hoàn tiền toàn bộ thu hồi lượt chưa dùng — bằng chứng: `e2e/tests/qa-refund-revoke.spec.ts` (mới).
- AC-8: Mọi API học viên và công khai qua `ForbiddenKeysAssert` không lộ khóa cấm — bằng chứng: `StudentApiForbiddenKeysIT` (mới).
- AC-9: Gọi `/me/**` bằng mã của người khác trả 404, không lộ dữ liệu — bằng chứng: `MeApiIdorIT` (mới).
- AC-10: Endpoint công khai vượt giới hạn tần suất bị từ chối đúng mức đã đặc tả — bằng chứng: `PublicRateLimitIT` (mới).
- AC-11: `/api/v1/test/**` trả 404 khi không có profile `e2e` — bằng chứng: `TestApiProfileGuardIT` (mới).
- AC-12: Không có bí mật trong repo: hai lệnh quét của A7 trên toàn đợt đều in 0 — bằng chứng: lệnh ở Kế hoạch kiểm thử.
- AC-13: README triển khai có biến môi trường mới, Mailpit, VNPAY, worker, yêu cầu IPN tới thẳng backend (B6), bắt buộc bật profile `prod` ở môi trường thật (chốt chặn `e2e` và `MOCK` dựa vào profile này), `THI360_TRUST_REAL_IP` cùng `x-real-ip`, tạo bucket `thi360-avatars` — bằng chứng: rà `README.md` theo danh sách biến trong `application.yml`.
- AC-14: `ARCHITECTURE.md` có các module mới, dòng 100 không còn ghi `attempt`, `commerce` là "sau" — bằng chứng: rà `ARCHITECTURE.md` với `backend/src/main/java/vn/unicom/thi360/`.
- AC-15: `DESIGN.md` có màn làm bài; nhãn "Chờ chấm" ghi đúng mã `PENDING_REVIEW` (dòng 347 đang ghi `GRADING`); mục 5.14 không còn mô tả cờ mock và `MockDataBanner` — bằng chứng: rà `DESIGN.md`, `git grep -n THI360_GRADING_MOCK -- DESIGN.md` không in dòng nào.
- AC-16: `TASK.md`, `PROGRESS.md` phản ánh trạng thái cuối đợt — bằng chứng: rà hai tệp với danh sách task của plan.
- AC-17: Danh sách câu hỏi còn mở (TM 15, LT 14, BS 19) chép vào bình luận issue cha — bằng chứng: bình luận `[agent]` trên issue cha, dẫn trong báo cáo QA.
- AC-18: Luồng xuyên task và đường lỗi ở mục 4 của khung đóng đợt đều đạt — bằng chứng: `e2e/tests/qa-failure-paths.spec.ts` (mới).
- AC-19: Quét giao diện đạt ở 390px và 1280px, sáng và tối, vi và en, không lẫn ngôn ngữ — bằng chứng: `e2e/tests/qa-visual-sweep.spec.ts` (mới), ảnh trong thư mục đợt.
- AC-20: Đặc tả, plan và tài liệu dự án khớp với code; sai khác có mã quyết định — bằng chứng: mục tài liệu của báo cáo QA.
- AC-21: Sổ việc hoãn, phát hiện để sau, test chập chờn, test cách ly đều đã đóng — bằng chứng: mục sổ của báo cáo QA.
- AC-22: Báo cáo QA đủ các mục của mẫu và có kết luận — bằng chứng: `D:/.Vietants_Project/.thi360-dev/dot-3/qa-report.md`.
- AC-23: Khi thiếu G2: có yêu cầu thay đổi được M.Trường duyệt, báo cáo ghi mẫu số 136 (138 − 2), E2E chạy cổng giả lập, `THI360_VNPAY_MODE` mặc định không phải `SANDBOX` — bằng chứng: yêu cầu thay đổi dẫn trong báo cáo QA, `git grep -n THI360_VNPAY_MODE origin/integration/dot-3 -- backend/src/main/resources/application.yml`.
- AC-24: Mỗi kỳ thi trong 9 kỳ thi đi trọn đường mua, làm bài, nộp, kết quả trên bài của DS — bằng chứng: `qa-nine-kinds.spec.ts` "chín kỳ thi" (mới)
- AC-25: Ca chuẩn của BS 14 chạy đầu cuối với cổng giả lập: khoản thu thừa hiện ở "Cần xử lý", admin hoàn đúng khoản đó, học viên giữ bài, thông báo lặp không tạo gì thêm — bằng chứng: `qa-payment-rules.spec.ts` "khoản thu thừa" (mới)

**Ảnh hưởng ngoài task:**

- Thời lượng E2E trên CI (`.github/workflows/e2e.yml:35`, `timeout-minutes: 25`) → đo thời gian chạy đủ bộ E2E của đợt; vượt 25 phút thì báo Advisor và tách task sửa trước khi đóng QA [chủ: task này]
- Profile của E2E (`e2e/playwright.config.ts:5-6` và `.github/workflows/e2e.yml:66` chạy `api,worker,dev`, trong khi E2E đợt 3 cần khối `on-profile: e2e`) → đổi profile ở các dòng này; `pnpm --filter @thi360/e2e test tests/sso.spec.ts` xanh trên profile mới [chủ: C-0b]
- Tài liệu đang mô tả mock đã gỡ (`DESIGN.md:326-332,347`) → cập nhật theo AC-15 [chủ: task này]
- Bảng module (`ARCHITECTURE.md:100`) → cập nhật theo AC-14 [chủ: task này]
- Code sản phẩm: không có: đã tìm tệp dưới `backend/src/main` và `apps/*/src` trong Đường dẫn sở hữu của task này, task chỉ thêm test và sửa tài liệu → `git diff --name-only origin/develop...HEAD` của PR QA không có tệp dưới hai thư mục này

**Kế hoạch kiểm thử:**

- AC-1: đi hết đặc tả, rà bằng chứng, ghi vào báo cáo QA.
- AC-2: toàn bộ, unit, tích hợp, E2E:
  - `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`
  - `cd backend && ./mvnw -B -ntp verify`
  - `cd backend && ./mvnw -B -ntp verify -Dgroups=perf` (mức hiệu năng)
  - `E2E_USE_BUILT=1 pnpm --filter @thi360/e2e test`
  - `pnpm --filter @thi360/api-client generate && git diff --exit-code packages/api-client`
- AC-3: chạy lại lệnh ở dòng Hồi quy của từng task trên SHA cuối, ghi kết quả từng dòng vào mục 4 của báo cáo QA.
- AC-4: `qa-guest-purchase-hsa.spec.ts`, E2E, `pnpm --filter @thi360/e2e test tests/qa-guest-purchase-hsa.spec.ts`
- AC-5: `qa-voucher-100.spec.ts`, E2E, `pnpm --filter @thi360/e2e test tests/qa-voucher-100.spec.ts`
- AC-6: `qa-bca-essay.spec.ts`, E2E, `pnpm --filter @thi360/e2e test tests/qa-bca-essay.spec.ts`
- AC-7: `qa-refund-revoke.spec.ts`, E2E, `pnpm --filter @thi360/e2e test tests/qa-refund-revoke.spec.ts`
- AC-8: `StudentApiForbiddenKeysIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=StudentApiForbiddenKeysIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
- AC-9: `MeApiIdorIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=MeApiIdorIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
- AC-10: `PublicRateLimitIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=PublicRateLimitIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
- AC-11: `TestApiProfileGuardIT`, tích hợp, `cd backend && ./mvnw -B -ntp verify -Dit.test=TestApiProfileGuardIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
- AC-12: quét toàn đợt (lệnh A7 đổi gốc so sánh sang `develop`, vì trên đầu nhánh đợt diff với chính nó là rỗng), hợp đồng:
  - `git diff origin/develop...origin/integration/dot-3 | grep -ciE "hash_secret=.+|pepper=.{8,}|smtp_password=.+|tmn_code=[A-Z0-9]{8}"` in 0
  - `git diff --name-only origin/develop...origin/integration/dot-3 | grep -cE "(^|/)\.env$"` in 0
- AC-13: rà `README.md` theo danh sách ở AC-13, ghi kết quả vào báo cáo QA.
- AC-14: rà `ARCHITECTURE.md` theo AC-14, ghi kết quả vào báo cáo QA.
- AC-15: rà `DESIGN.md` theo AC-15 và `git grep -n THI360_GRADING_MOCK -- DESIGN.md`.
- AC-16: rà `TASK.md`, `PROGRESS.md` với bảng task của plan.
- AC-17: kiểm bình luận trên issue cha bằng `uniai issue get <mã issue cha> --output json`.
- AC-18: `qa-failure-paths.spec.ts`, E2E, `pnpm --filter @thi360/e2e test tests/qa-failure-paths.spec.ts`
- AC-19: `qa-visual-sweep.spec.ts` chụp đủ 2 khổ, 2 nền, 2 ngôn ngữ rồi người duyệt đối chiếu, giao diện, `pnpm --filter @thi360/e2e test tests/qa-visual-sweep.spec.ts`
- AC-20: rà đặc tả, plan, tài liệu với code, ghi vào báo cáo QA.
- AC-21: rà các sổ của đợt trong `D:/.Vietants_Project/.thi360-dev/dot-3/`, ghi vào báo cáo QA.
- AC-22: đối chiếu báo cáo QA với mẫu `qa-report-template.md`.
- AC-23: chỉ chạy khi thiếu G2: rà yêu cầu thay đổi và `git grep -n THI360_VNPAY_MODE origin/integration/dot-3 -- backend/src/main/resources/application.yml`.
- AC-24: `qa-nine-kinds.spec.ts` "chín kỳ thi", E2E, `pnpm --filter @thi360/e2e test tests/qa-nine-kinds.spec.ts`
- AC-25: `qa-payment-rules.spec.ts` "khoản thu thừa", E2E, `pnpm --filter @thi360/e2e test tests/qa-payment-rules.spec.ts`
- Hồi quy: lệnh của AC-2 và AC-3; so thời gian chạy `pnpm --filter @thi360/e2e test` với mốc 25 phút của `.github/workflows/e2e.yml:35`; `pnpm --filter @thi360/e2e test tests/sso.spec.ts` cho dòng profile E2E; AC-14, AC-15 cho hai dòng tài liệu.
- Kiểm thử rủi ro: không áp dụng (rủi ro chuẩn)
- Dữ liệu test: bộ DS của đợt, số liệu từ `docs/specs/2026-11-17-de-test-gia-dinh.md`; tài khoản và đơn tạo trong spec có tiền tố `QA-`; cổng thanh toán giả lập (`THI360_VNPAY_MODE=MOCK`); đồng hồ thử của C-0b cho các ca hết hạn; QA chạy một mình trên SHA cuối, E2E chạy `workers: 1`.
- Không kiểm: luồng sandbox VNPAY thật (VP-2, hoặc tách khỏi đợt khi thiếu G2); so ảnh tự động vì repo chưa có `toHaveScreenshot`, ảnh do người duyệt đối chiếu.

---

# Phần E: Rủi ro và cách xử lý khi kẹt

| Rủi ro | Dấu hiệu | Xử lý |
|---|---|---|
| G0 trễ (đợt 2 chưa về `develop`) | Không tạo được `integration/dot-3` | Chờ. Không tách từ `integration/dot-2` (M.Trường chốt ngày 2026-11-17). |
| Sinh đề chậm với ngân hàng lớn | Số đo `perf` của EX-2 vượt 30 giây, hoặc nhiều lượt `GENERATION_TIMEOUT` | Sinh đề đã chạy nền nên người dùng chỉ chờ lâu hơn. Advisor báo M.Trường kèm số đo; không tự tăng hạn quá 60 giây. |
| Hàng sinh đề nghẽn khi nhiều người bắt đầu cùng lúc | Nhiều lượt `GENERATION_BUSY` | Tăng số luồng hoặc độ dài hàng qua cấu hình; đo lại trong QA. |
| VNPAY sandbox khác tài liệu | VP-1 thấy chữ ký hoặc mã khác | VP-1 ghi vào TM và `vectors.json`; CM-3b theo đó. CM-3b đã để các điểm này sau cấu hình. |
| Không có G2 trước QA | VP-1, VP-2 chưa chạy | Advisor xin M.Trường duyệt yêu cầu thay đổi tách VP-1, VP-2 khỏi đợt (mẫu số còn 136). QA chạy với cổng giả lập. VP thành việc riêng sau đợt. Không đưa về `develop` với `THI360_VNPAY_MODE=SANDBOX` mặc định. |
| VNPAY chưa trả lời Q9, Q14 | Chưa có danh sách IP, chưa rõ nghĩa mã hoàn | Không chặn đợt. Ghi vào bình luận issue cha; phải có trước khi lên môi trường thật. |
| Xung đột tệp chung (B9) | Rebase lỗi nhiều lần | Advisor xếp lại thứ tự merge: task nhỏ trước, task lớn rebase sau. |
| Đổi hợp đồng `commerce/spi` giữa chừng | Lane cần thêm hàm | Qua Advisor; lane đổi sửa mọi nơi gọi trong cùng PR (B1). |
| Cách đọc R14 khác ý M.Trường | M.Trường xem đề và thấy dạng câu đảo sai | Lane **không** tự sửa `PaperOrdering` (thuộc `exam/**`, B9). Báo Advisor; Advisor mở một yêu cầu thay đổi cho module `exam` kèm test, để sinh thử và lượt thi cùng đổi. |
| Quyết định ở BS đổi sau khi task đã làm | M.Trường hoặc BA đổi một quyết định ngày 2026-11-19, gồm luật nửa điểm H-SCA (Q-32) | Yêu cầu thay đổi: sửa BS, sửa task chịu ảnh hưởng theo bảng OQ ở A2, báo lại mẫu số điểm nếu đổi. Luật nửa điểm chỉ sửa `GradingPolicies` và bảng ca của AT-7. |
| Trình duyệt chặn phát audio hoặc tệp lớn tải chậm | FS-7 thấy audio không phát trên điện thoại | Nút Nghe do người dùng bấm nên được phép tự phát. Tải theo `Range`; phát tiếp từ vị trí đã lưu, không tốn lần nghe. Vẫn kẹt thì Advisor báo M.Trường kèm thiết bị và số đo. |
| Đợt 2B đổi tiếp trước khi merge | `integration/dot-2b` có thay đổi ở `exam/spi`, bản chụp phiên bản, mẫu đề sau ngày 2026-11-18 | Khi qua G0b, Advisor rà lại dòng Ảnh hưởng ngoài task của EX-1, EX-2, DS, AT-1 với code mới trước khi mở lane. |
| DS không công bố được một bài vì mẫu khóa số câu hoặc thiếu câu mẫu | Kiểm tra cấu hình báo `STRUCTURE_COUNT_LOCKED`, `SET_SHORTAGE`, `TOPIC_QUOTA_MAPPING_MISSING` | Không hạ số câu của mẫu khóa (V-ACT FULL không có bản thu nhỏ). Thiếu câu thì DS thêm câu mẫu; thiếu MAPID ở chỉ tiêu bắt buộc thì báo Advisor. |
| Màn làm bài chậm trên điện thoại | Gõ tự luận bị trễ | Gom lưu theo nhịp (LT 7.2). Thành phần câu hỏi dùng `memo`. Đo bằng Playwright với CPU chậm 4 lần. |
| Lane không có commit mới sau một chu kỳ kiểm tra 15 phút của Advisor | Lane đứng | Theo pha cook của `zone17-builder`: Advisor hỏi lane, tách nhỏ task, hoặc đổi mô hình. Ghi vào sổ tiến độ. |

---

# Phụ lục

## Quyết định của M.Trường ngày 2026-11-17 (sau review lượt 1)

| # | Điểm | Chốt | Ảnh hưởng tới plan |
|---|---|---|---|
| 1 | LT R13: đề đồng hồ chung có chuyển phần tự do không | Mọi đề làm tuần tự (LT L7) | Bỏ `AttemptNavigationPolicy`; AT-2a làm một luồng cho mọi đề |
| 2 | LT R14: đảo những dạng câu nào | Đã chốt ở phiên PBI-03, PBI-04 (BE mục 12); LT F22 ghi cách đọc | `PaperOrdering` (module `exam`, đợt 2B) theo F22 |
| 3 | TM Q11, Q15; LT R11: thêm vào module `exam` | Duyệt cả 6 phần | EX-1, EX-2 như Phần D |
| 4 | VNPAY sandbox | Dùng merchant sandbox cũ của reap, nay chỉ cho Thi 360; M.Trường đặt URL IPN của bản demo | G2, G3, VP-1, VP-2 |
| 5 | TM Q9, Q14 | M.Trường hỏi VNPAY; đợt vẫn chạy | Phần E |
| 6 | Sinh đề quá hạn | Chạy nền ngay từ đầu (LT L8) | AT-1, AT-2b, FS-5; B2 |
| 7 | Bắt đầu đợt | Sau khi đợt PBI-03, PBI-04 xong | G0 |
| 8 | C-0 lớn, nằm đầu đường dài nhất (báo cáo lượt 2) | Tách C-0b (profile `e2e`, đồng hồ thử, `ForbiddenKeysAssert`) | C-0 3 điểm, C-0b 1 điểm; 34 task |
| 9 | Bản demo sau VP-2 | Làm sandbox; dựng trên VPS demo (sau có thể chuyển VPS khác); không cần giữ dữ liệu cũ; VNPAY thật làm sau khi M.Trường báo | G3 đã có; VP-2 không cần sao lưu |
| 10 | Ngưỡng giới hạn tần suất cho máy chủ web (agent chốt) | Miễn theo danh sách IP trong cấu hình | B6, CM-1 |
| 11 | UniNote PBI-04 bản 2026-11-17 đổi nhiều (ma trận theo môn, 17 − 1, Global List) | Làm đợt 2B trước các task đụng `exam` (`docs/plans/2026-11-17-pbi04-dot-2b.md`) | G0b; EX-1, EX-2, DS |
| 12 | Tự luận trong V1 (UniNote ghi "tạm bỏ qua") | Giữ LT L2: chấm tay, đề có tự luận vẫn bán (2B K1) | Không đổi AT-5, FA-4 |
| 13 | Định hướng Tin học | Chọn ở màn chuẩn bị, chỉ sinh nhánh đã chọn (2B K2) | AT-1, AT-2b, EX-2, FS-5 |
| 14 | Bộ đề SP2E | Làm trong đợt 3 (2B K3, TM C17) | CM-6, FB |
| 15 | Thứ tự nhóm, đảo trong nhóm | Nhóm giữ thứ tự theo mẫu, câu đảo trong nhóm; Văn nhóm 5 có công tắc (2B K4) | AT-1 dùng `PaperOrdering` |

## Bản nâng cấp ngày 2026-11-18

M.Trường yêu cầu ngày 2026-11-18: audit lại plan, rồi "Upgrade plan nhé. Theo quyết định mới." Báo cáo audit: `D:/.Vietants_Project/.thi360-dev/dot-3/audit-plan-2026-11-18.md` (mã A-n).

**Quyết định mới đã áp** (đều có trước bản nâng cấp, plan cũ chưa nhận):

| # | Quyết định | Nguồn | Ảnh hưởng tới plan |
|---|---|---|---|
| 16 | Cờ "Giữ thứ tự phương án": câu có `shuffleOptions = false` không bị đảo phương án | M.Trường, 2026-11-17 19:10 (UNI-2648; `docs/specs/2026-11-17-giu-thu-tu-phuong-an.md` mục 5) | Sửa LT F22, LT 13; AT-1, FA-4 thêm ca giữ thứ tự; không task nào sửa `PaperOrdering` (B9, Phần E) |
| 17 | V-ACT, THPTQG làm trọn trong đợt 2B: phạm vi V-ACT theo phần, theo môn; THPTQG theo phần; bài ôn; mẫu khóa số câu; chùm cỡ linh hoạt; `everyChildInScope` | M.Trường, 2026-11-18 (2B D5, D15; phần bổ sung 2B) | G0b; DS thêm bài; EX-2; AT-1, AT-2a, AT-4; OQ-1 đến OQ-4 |
| 18 | THPTQG Ngữ văn chỉ lưu nháp, không công bố, không vào bộ đề; đề có tự luận khác vẫn bán | M.Trường, 2026-11-18 (2B D11) | Ngoại lệ của dòng 12 ở bảng trên; DS không có bài Ngữ văn THPTQG; CM-6 chỉ nhận bài đang bán nên tự loại |
| 19 | Thang điểm V-ACT giữ 10 điểm mỗi câu, 1.200, `LINEAR`; cách hiển thị kết quả để đợt 3 quyết | Advisor đợt 2B, 2026-11-18 (2B D7) | OQ-2 |

**Điều chỉnh do agent làm khi nâng cấp** (không đổi hành vi nghiệp vụ; M.Trường duyệt cùng G1b):

| # | Điều chỉnh | Lý do (mã audit) |
|---|---|---|
| U-1 | API chấm đề làm thử chuyển từ FA-4 sang AT-5, nằm trong module `attempt`; EX-2 mở `PreviewPaperPort`; AT-4 để bộ chấm từng câu dùng lại được; FA-4 chỉ làm phần web | A-16: `exam` không được phụ thuộc `attempt`; lane web không sửa backend |
| U-2 | `finalAnswers` khi hoàn thành phần, nộp bài: AT-2a khai `FinalAnswerWriter`, AT-3 cài | A-10: chưa task nào nhận AC-11.08 |
| U-3 | Danh sách IP miễn giới hạn tần suất do C-0 làm, CM-1 dùng | A-20: `shared/security` thuộc vùng của C-0 |
| U-4 | EX-1 đổi các chỗ đọc giờ của module `exam` sang `Clock`; thêm việc dọn ảnh bìa bỏ quá 7 ngày | A-25, A-12 |
| U-5 | FA-1 thêm mọi mục menu của đợt, kể cả "Bộ đề"; FB chỉ bỏ dòng ẩn | A-19 |
| U-6 | Cổng giả lập bật được ở `dev`, `test`, `e2e` và bản demo chưa có khóa, đúng TM 8.4 | A-15 |
| U-7 | Thư đơn hàng chỉ gửi khi email đã xác minh (CM-3a, CM-5); bộ đề dựng lại `items[]` khi bài công bố lại, đếm người mua, thư liệt kê bài (CM-6) | A-11, A-13 |
| U-8 | ID-2 làm giới hạn E16; FS-2 đặt `Referrer-Policy: no-referrer` cho `/reset-password` | A-14 |
| U-9 | FS-3 thêm trang chi tiết đơn của học viên; FS-1 hiện trạng thái đang kiểm tra ở trang chi tiết | A-21 |
| U-10 | DS thêm bài V-ACT theo phần, theo môn, THPTQG theo phần, một bài ôn; V-ACT FULL không có bản thu nhỏ; CM-6 thêm bộ SP2E vào bộ dựng của DS | A-2, A-5, A-6 |
| U-11 | FS-4 đưa cả bộ hiển thị nội dung (mọi loại nút, mhchem) vào `packages/ui` | A-24 |
| U-12 | Phụ thuộc: FA-1, AT-3, FS-6, FA-4, VP-2 (ghi chú dưới bảng C1) | A-17, A-18 và các điểm nhỏ |
| U-13 | Profile là khối `on-profile` trong `application.yml`, không phải tệp riêng | A-23 |
| U-14 | C-0 nhận thêm chính sách giới hạn GET công khai; ID-1 mở rộng `RateLimitFilter` cho giới hạn nhiều khung thời gian | `RateLimitFilter` hiện chỉ xét POST đăng nhập, tải lên |
| U-15 | AT-5 thêm `GET …/preview/{previewId}/paper`, trả đề không kèm đáp án | Phản hồi `POST …/preview` có đáp án, FA-4 không dùng được |
| U-16 | CM-5 thêm `POST /refunds/{id}/mark-failed`, bảng `reconciliation_report` và `GET /reconciliation-reports/latest` | TM 9.4 bước 5 và báo cáo đối soát có hành vi nhưng không có API |
| U-17 | EX-1 thêm `GET /exams/{id}/cover/{assetId}`; `PUT listing` nhận `description`, `coverAssetId`; kết quả kiểm tra có trường riêng `listingCheck` | TM 6.3 trả `url` mà không có API xem; web đổi nhóm lạ trong `groups[]` thành `INFO` |
| U-18 | CM-4a thêm cột `revoked_at`, `order_code` vào `entitlement_grant` | TM 8.5 thiếu dữ liệu cho `release`, `compensate`, `describe` |

**Sửa đặc tả do task làm trong PR của mình** (đã có trong hợp đồng task hoặc do Advisor giao khi mở lane):

| Đặc tả | Sửa | Task |
|---|---|---|
| TM 8.4 | Trang `/mock-vnpay` có ở `dev`, `test`, `e2e` và chỉ khi `MODE=MOCK` | CM-3a |
| TM 8.5 | Thêm `revoked_at`, `order_code` của `entitlement_grant` | CM-4a |
| TM 10.2 | Thân lỗi 410: `code = PRODUCT_DISCONTINUED` kèm `product: {code, name}` | CM-1 |
| TM 6.2 | Ảnh bìa dùng tiền tố `exam-covers/` trong bucket riêng tư sẵn có (tạm, chờ M.Trường) | EX-1 |
| TM 9.4 | Thêm `POST /refunds/{id}/mark-failed` | CM-5 |
| LT F15, 10.1 | Thang hiển thị của V-ACT theo BS 3 | AT-4 |

**Trọng số:** giữ 103 điểm, 36 task. Việc tăng thêm so với bản ngày 2026-11-17 mà trọng số chưa tính: test theo Kế hoạch kiểm thử và phép kiểm hồi quy ở mọi task; AT-5 nhận API chấm đề làm thử (trước ở FA-4); EX-1 nhận đổi `Clock` và dọn ảnh bìa; C-0 nhận danh sách IP miễn; DS thêm 4 bài; FS-4 nhận cả bộ hiển thị; FS-3 thêm một trang; CM-5 thêm API đánh dấu thất bại và báo cáo đối soát. Đổi trọng số (nếu M.Trường muốn) là một phiên bản phạm vi mới.

**Câu hỏi mở:** OQ-1 đến OQ-15 đã được trả lời ngày 2026-11-19 (A2, BS).

## Bản cập nhật ngày 2026-11-19

M.Trường trả lời từng câu ngày 2026-11-19 sau khi đã kiểm lại PBI: 8 tài liệu UniNote PBI-05 đến PBI-13 không đổi so với bản lưu trong repo, 9 thẻ không có bình luận hay tệp mới; phần hoạt động của thẻ PBI-04 không tải được.

**Thay đổi về phạm vi:** đợt 3 làm đủ 9 kỳ thi. Phụ lục 2026-11-17 dòng 1 (mọi đề làm tuần tự) nay có ngoại lệ THPTQG theo BS 4.

| Việc | Điểm | Lý do |
|---|---|---|
| AT-6 (mới) | 3 | Phát audio, giới hạn lần nghe, kiểm tệp audio |
| AT-7 (mới) | 2 | Chấm H-SCA, V-SAT, kết quả theo phân đoạn |
| AT-8 (mới) | 2 | THPTQG chuyển tự do |
| FS-7 (mới) | 3 | Màn học viên của H-SCA, V-SAT |
| FA-5 (mới) | 2 | Màn Sinh thử dùng giao diện chung, kiểm 9 kỳ thi |
| LP (mới) | 2 | Tab Hồ sơ học tập |
| CM-2, CM-3a, CM-5, AT-4, AT-5, FS-6, FA-2, FA-3, DS, QA | +1 mỗi task | Việc thêm theo BS |
| TS (mới) | 3 | Dựng lại seed đề test `TEST-*` theo mẫu BA (UNI-2701); việc tồn đọng của đợt 2B, M.Trường đưa vào bước đầu của cook |
| FU (mới) | 8 | Việc tồn đọng của đợt 2B, 41 mục (UNI-2700); M.Trường giao ngày 2026-11-19 |
| **Tổng** | **103 → 138** | 36 → 44 task, 8 → 10 sóng |

**Việc tồn đọng của đợt 2B (UNI-2700, 45 mục, đối chiếu ngày 2026-11-19):**

- FU-QA-SEEDS, cùng FU-SEEDGEN-1, FU-BE13-2, FU-FE4-D9 (UNI-2701): task TS ở sóng 1, chặn EX-1, EX-2.
- FU-TEMPLATE-I18N, FU-FE1-1, phần chữ mẫu đề tiếng Việt ở trang `en` của FU-FE3AA-V1 (V1-01) và FU-FE2-3 (F-7): không làm. M.Trường quyết ngày 2026-11-19: nội dung đề không làm song ngữ.
- FU-BE10-ASSET: `DevQuestionAssetSeeder` (UNI-2691) chỉ có 18 tệp MP3 của H-SCA, V-SAT, đủ cho AT-6. Ảnh cho AT-3, AT-4 do DS tự tải lên qua service thật. Tệp cho câu BCA trong seed soạn đề thuộc task FU.
- 41 mục còn lại, gồm FU-QA-PREVIEW-COMPLIANCE-VERSION, FU-BE5-1, FU-RELOAD-EXAM-CLOCK (chỉ chờ BA): task FU, theo dõi thẳng ở UNI-2700 (M.Trường, 2026-11-19).

**Hợp đồng mới giữa các task:** `PublishedExamPort` trả `kindCode`, `displaySegments`, `audioRequirement` (EX-1); `attempt.exam_kind_code`, `navigation_mode` (AT-1); `GradingPolicies.forKind` (AT-4, AT-7 đổi); `PaymentOutcomeApplier` dùng chung cho IPN và `querydr` (CM-3a, CM-3b).

## Nhật ký review

- **Lượt 1 (Fable, chỉ review):** 3 Chặn, 18 Nên sửa, 8 Gợi ý. Đã xử lý:
  - Chặn: AT-1 thêm EX-1; ID-1, ID-2 thêm C-0; tách CM-4 thành CM-4a, CM-4b.
  - Nên sửa: tổng điểm đúng 100; khóa ngoại và cổng tạm (B1); tách VP-1, VP-2, thêm G3; profile `e2e`, `MutableClock` về C-0; OTP chỉ ở `dev`; chốt cách khóa worker (B5); kiểu kết quả kiểm tra thuộc EX-1; FS-2 mềm FS-1; `ForbiddenKeysAssert` về C-0; hợp đồng SPI đủ (B1); sinh hai định hướng ở EX-2; tách CM-3, AT-2; thêm DS; pepper có giá trị dev; menu, thanh đầu trang, cảnh báo chấm tay có chủ; nghiệm thu cụ thể hơn; lệnh quét bí mật; mốc khớp phụ thuộc; bỏ Storybook.
  - Gợi ý: ghi lại đường dài nhất; KaTeX chuyển kèm sửa web-admin; ca `perf` ở EX-2; EX-2 sửa BE 5.4; chỉ lane backend sinh lại OpenAPI; bỏ cột "song song"; FA-1 nhận thêm việc nên giữ riêng; câu chữ.
- **Lượt 2 (Fable, review và sửa, 2026-11-17):** 0 Chặn, 7 Nên sửa, 3 Gợi ý. Mọi phát hiện lượt 1 đã được xử lý đúng. Đã sửa:
  - Plan: `StudentLearningProvider` chắc chắn chỉ nhận một bean, C-0 đổi sang danh sách (B1, C-0); `GenerationExecutor` có ở cả tiến trình worker và kết quả đến muộn bị bỏ theo `generation_token` (B2, AT-1, AT-2b); mô tả đúng cách proxy web xử lý `X-Forwarded-For` và hệ quả cho giới hạn tần suất (B6); phần `SKIPPED` đã có câu (AT-4); lượt `STARTING` ở `exam-access` (CM-4b) và ở màn chuẩn bị (AT-1, FS-5); sao lưu DB demo trước VP-2.
  - LT: 5.1, 5.2, 6.1, 7.4, 9.1. TM: 10.2.
  - Ba điểm để người quyết đã chốt: Phụ lục, dòng 8 đến 10.
- **Review đợt 2B lượt 1 (Fable review, agent sửa, 2026-11-17):** 3 Nên sửa về phần sửa theo 2B. Đã sửa:
  - D1: FS-4 chỉ chép `RichTextView`, KaTeX vào `packages/ui`, không sửa web-admin; FA-1 (sau G0b) đổi import (B8, FS-4, FA-1).
  - D2: G0b sinh lại `openapi.json` và `packages/api-client` sau khi merge `develop`.
  - D3: CM-2, CM-3a, CM-4a tạo bảng theo hình dạng có bộ đề (`BUNDLE`, `purchase_order_item`, chỉ mục `(user_id, product_code)`, duy nhất `(order_id, exam_id)`); CM-6 không phải đổi lại.
- **Theo quyết định K6 đến K15 của đợt 2B (2026-11-17):** bỏ `OptionShufflePolicy`, đảo phương án nằm trong `PaperOrdering` của module `exam` (B9, AT-1, Phần E, Phụ lục dòng 2; K12); "Làm thử như học viên" dùng cùng `PaperOrdering` (FA-4); ca chấm tay chỉ dùng BCA vì Văn SP2E V1 bỏ phần Viết (DS; K7); bài và câu của DS dùng mẫu có nhãn, câu gắn nhãn lấy từ BE-10 của 2B (DS; K11, K15).
- **Review đợt 2B lượt 2 (Fable review và sửa, 2026-11-17):** cách sửa D1–D3 đúng. Sửa thêm: FA-1 phụ thuộc cứng FS-4 (C1, C2), vì FA-1 đổi import của web-admin sang bản `RichTextView`, KaTeX mà FS-4 đưa vào `packages/ui`; sóng không đổi. TM 8.5, 16.3 sửa câu chữ cho khớp việc tạo bảng theo hình dạng có bộ đề ngay từ CM-3a, CM-4a. Báo cáo `.thi360-dev/review-2b-r2.md`.
- **Review đợt 2B lượt 4 (Fable rà khớp và sửa, 2026-11-17):** plan khớp LT, TM và 2B sau K6–K15, H20–H22. Sửa một chỗ: FA-1 ghi rõ cảnh báo chấm tay đã có từ đợt 2B, lane chỉ chỉnh hiển thị theo bản vẽ và kiểm bằng bài BCA FULL. Trọng số, phụ thuộc, sóng không đổi: 36 task, 103 điểm. Báo cáo `.thi360-dev/review-2b-r4.md`.
- **Audit và nâng cấp theo `zone17-builder` (2026-11-18):** `plan-lint` trước nâng cấp không đạt (thiếu cột Điểm, Rủi ro; 36 task thiếu Đường dẫn sở hữu, Ảnh hưởng ngoài task, Kế hoạch kiểm thử). Audit: 1 Chặn (A-1), 25 Nên sửa, các điểm Nhỏ; đã xử lý trong bản này, trừ các câu hỏi mở OQ-1 đến OQ-15. Người rà: phiên tác giả cùng các lượt rà phụ, **chưa phải** lượt review độc lập. Báo cáo `D:/.Vietants_Project/.thi360-dev/dot-3/audit-plan-2026-11-18.md`.
- **Cập nhật theo câu trả lời của M.Trường (2026-11-19):** áp 17 câu trả lời và chính sách nghe vào BS và plan; thêm 7 task, 43 task, 9 sóng, 129 điểm.
- **Review độc lập vòng 1 (Codex, 2026-11-19):** 5 blocking, 12 should-fix, 1 minor, 6 bị bác. Đã sửa cả 18 phát hiện, không từ chối phát hiện nào: phiên phát audio có `playId`, hạn và mốc chống tua; thời lượng làm thử chụp vào đề sinh thử; cổng đề sinh thử trả đủ `kindCode`, tệp đính kèm, phân đoạn; các ca cạnh tranh của tiền; FS-7 lên rủi ro cao. Báo cáo `D:/.Vietants_Project/.thi360-dev/dot-3/review-1119/codex-round1.md`.
- **Review độc lập vòng 2 (Codex, 2026-11-19, delta):** 18 phát hiện cũ: 14 đã đóng, 2 còn mở, 2 được thay bằng phát hiện mới. Phát hiện mới: 5 blocking, 1 should-fix, 1 minor. Đã sửa cả 7, không từ chối: máy chủ đọc thời lượng MP3 thay vì tin trình duyệt; tách mở lần nghe (có `requestId`) khỏi phát tiếp, phát tiếp không tốn lượt, máy chủ không tự mở lần nghe mới; dung sai 3 giây là một ngân sách cho cả lần nghe; tách hai ma trận cạnh tranh của đối soát; bản lưu Redis của EX-2 được phép đổi hình dạng và hạn; phần nửa điểm H-SCA khi làm thử chuyển từ AT-5 sang AT-7. Báo cáo `D:/.Vietants_Project/.thi360-dev/dot-3/review-1119/codex-round2.md`.
- **Review độc lập vòng 3 (Codex, 2026-11-19, delta, vòng cuối):** 7 phát hiện vòng 2: 6 đã đóng, 1 thành câu hỏi cho người. Phát hiện mới: 0 blocking, 2 should-fix, 1 minor, 1 needs-human, 1 bị bác. M.Trường quyết needs-human S3-1: lần nghe đã bấm được phát tiếp đến khi phần đóng, bỏ hạn riêng của lần nghe. Đã sửa P3-1 (ba tệp MP3 mẫu có chủ, đối chiếu tệp 2B 2,088 giây), P3-2 (thứ tự năm bước khi mở lần nghe, kiểm phần đã đóng trước), P3-3 (đủ bốn API trong phép quét khóa cấm). Không mở vòng 4. Báo cáo `D:/.Vietants_Project/.thi360-dev/dot-3/review-1119/codex-round3.md`.
- **M.Trường duyệt (2026-11-19):** duyệt G1b (BS) và G2 (plan). Bỏ FA-6 (dịch tiếng Anh chữ của mẫu đề, 2 điểm): nội dung đề không làm song ngữ, tên phần, mục, phân đoạn của mẫu luôn hiện tiếng Việt kể cả khi giao diện ở tiếng Anh. Plan chốt 42 task, 9 sóng, 127 điểm.
- **Review độc lập vòng 4 (Codex, 2026-11-19, delta các thay đổi sau khi duyệt: TS, FU, bỏ FA-6, ảnh DS, S3-1):** 1 blocking, 8 should-fix, 1 minor, 3 bị bác. Đã sửa cả 10, không từ chối: BS 17.2 có bảng phản hồi riêng từng đường khi phần đã đóng, `AttemptState` không lỗi vì audio, tải tệp vẫn 404, AT-6 thêm AC-19 (S4-1); TS dựng lại cả bài lỗi `TEST-ERR-*` từ bài nền mới và nâng DB đã có seed 2B, thêm AC-6, AC-7 (P4-8, P4-5); A7 mục 7 giao việc nối `pickName`, `data-exam-content` cho từng lane màn (P4-9); FU: đường sở hữu cho byte tệp BCA, bộ chuỗi web-admin, các IT xem trước dùng thân cũ, ngoại lệ B9 cho TS và FU, luật 0,2 điểm chỉ áp khi chọn câu cho bài SP2E, AC-12 cho xem trước và đổi phiên bản, R3-1 chép rõ, mềm FA-1, rủi ro cao với ca dữ liệu cũ, đồng thời, quyền, số đếm 45 dòng nguồn = 4 TS + 1 đóng + 40 FU (P4-1 đến P4-7). Các lane C-0, C-0b, ML, VP-1 không chịu ảnh hưởng nên mở trước khi sửa xong.
- **Review độc lập vòng 5 (Codex, 2026-11-19, delta của bản sửa vòng 4):** 1 blocking, 3 should-fix, 0 minor, 2 bị bác; 9/10 mục vòng 4 đã đóng, P4-1 còn phần đường dẫn. Đã sửa cả 4: FU bỏ dòng sửa loader sai package, câu BCA ghi `file_name = bca/<tệp>` để loader hiện có đọc được (P5-1); luật 0,2 điểm mỗi ý chỉ áp khối SP2E chấm `PER_STATEMENT`, khối `COUNT_TABLE` giữ luật bảng, thêm `#sp2eCountTableBlockUnaffected` (P5-2); BS 17.2 và AT-6 AC-19 tách ba trạng thái audio khi phần đóng (`PLAYING` thành `CLOSED`, `FINISHED` giữ nguyên, `IDLE` giữ nguyên), thêm `#closedPartAudioStateMatrix` (S5-1); TS đổi rủi ro sang cao (P5-3). Báo cáo: `.thi360-dev/dot-3/review-1119/codex-round5.md`.
