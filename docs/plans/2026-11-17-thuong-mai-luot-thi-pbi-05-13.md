# Plan triển khai: Đợt 3, thương mại và lượt thi (PBI-05 đến PBI-13)

> **Ngày:** 2026-11-17 · **Issue đặc tả:** UNI-2622. Issue cha của đợt và các sub-issue do Advisor tạo ở bước 0 (A4).
>
> **Spec:**
> - `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md`, gọi tắt **TM**;
> - `docs/specs/2026-11-16-luot-thi-pbi-10-13.md`, gọi tắt **LT**;
> - `docs/specs/2026-11-16-be-pbi-03-04.md`, gọi tắt **BE** (bản trên `develop` sau khi đợt 2 và đợt 2B về);
> - `docs/specs/2026-11-17-pbi04-dot-2b.md`, gọi tắt **2B**: bài thi sau đợt 2B.
>
> **Trạng thái:** M.Trường duyệt ngày 2026-11-17 (G1). Chưa chạy; chờ G0, và G0b cho các task đụng `exam`. Đã qua review lượt 1 của Fable (3 Chặn, 18 Nên sửa, 8 Gợi ý; đã xử lý hết, xem Phụ lục). Sửa ngày 2026-11-17 theo đợt 2B: cổng G0b; EX-1, EX-2, DS, AT-1, AT-2a, AT-2b, FS-5, FA-4; thêm CM-6, FB; trọng số (Phụ lục, dòng 11 đến 15). Sửa theo review 2B lượt 1: G0b sinh lại client; FS-4 không sửa web-admin (FA-1 đổi import); CM-2, CM-3a, CM-4a tạo bảng theo hình dạng có bộ đề.
>
> **Cách dùng:**
> - Plan này đủ để một phiên Advisor chạy `zone17-cook-plan` mà không phải hỏi lại về nội dung.
> - Plan và spec khác nhau về *cách làm* thì theo plan. Khác nhau về *hành vi nghiệp vụ* thì theo spec và báo lại.
> - Quy ước kỹ thuật của đợt 1 (`docs/plans/2026-11-13-base-port-pbi-01-02.md` Phần B) và đợt 2 (`docs/plans/2026-11-16-be-pbi-03-04.md` Phần B) vẫn áp dụng. Phần B ở đây chỉ ghi phần thêm hoặc khác.

## Mục lục

- Phần A: cấu hình đợt chạy (nhánh, cổng chặn, ủy quyền, theo dõi, trọng số, môi trường, nghiệm thu chung)
- Phần B: quy ước kỹ thuật thêm cho đợt này
- Phần C: pipeline: task nào chạy song song, task nào chờ
- Phần D: hợp đồng từng task
- Phần E: rủi ro và cách xử lý khi kẹt
- Phụ lục: quyết định ngày 2026-11-17 và nhật ký review

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
| **G0** | Đợt PBI-03, PBI-04 cook xong: `integration/dot-2` đã merge về `develop`. PR spec của UNI-2622 (hai spec TM, LT và plan này) đã merge. | Cả đợt. M.Trường chốt ngày 2026-11-17: làm đợt này sau khi đợt 2 xong, để không xung đột. |
| **G0b** | Đợt 2B xong: `integration/dot-2b` đã merge về `develop` (`docs/plans/2026-11-17-pbi04-dot-2b.md`), và `develop` đã merge vào `integration/dot-3`. Sau khi merge, Advisor sinh lại `openapi.json` và `packages/api-client` trên `integration/dot-3` (hai bên đều đã sinh lại, nên xung đột ở các tệp này giải bằng cách sinh lại, không sửa tay), chạy kiểm tra web-admin và web-student, rồi mới mở EX-1. | EX-1, EX-2 và mọi task phụ thuộc vào hai task này (DS, CM-1, FA-1, AT-*, các task sau). Không chặn C-0, C-0b, ML, FS-4, VP-1, CM-4a, ID-1, ID-2, FS-2. |
| **G1** | M.Trường duyệt plan này. Các phần thêm vào module `exam` (TM Q11, Q15; LT R11) đã được duyệt ngày 2026-11-17. | Cả đợt |
| **G2** | Khóa sandbox VNPAY (`THI360_VNPAY_TMN_CODE`, `THI360_VNPAY_HASH_SECRET`) có trong biến môi trường của máy chạy VP. Merchant sandbox trước đây của dự án reap, từ ngày 2026-11-17 chỉ dùng cho Thi 360 (mỗi Terminal Code chỉ có một URL IPN). M.Trường đã chỉ nơi lấy khóa (ghi trong hợp đồng lane ở thư mục đợt chạy, **không** ghi vào repo). URL IPN `https://thi360.zone17.click/api/v1/public/payments/vnpay/ipn` **đã đặt** trên trang merchant sandbox ngày 2026-11-17, thay URL cũ của reap. Phần còn lại của G2 là nạp khóa vào biến môi trường của máy chạy VP. | VP-1, VP-2. CM-3a, CM-3b làm với cổng giả lập, không chờ G2. |
| **G3** | **Đã cho phép ngày 2026-11-17.** Đưa `integration/dot-3` lên bản demo trên `bro` (`thi360.zone17.click`; sau này có thể chuyển sang VPS khác) và thêm một khối `location` nginx đưa `/api/v1/public/payments/vnpay/` thẳng tới backend (B6). Đang trong giai đoạn phát triển, chưa có dữ liệu thật: không cần giữ dữ liệu cũ của demo. Bản demo giữ `integration/dot-3` sau VP-2. VNPAY môi trường thật làm sau, khi M.Trường báo. | Chỉ VP-2 |

**Câu hỏi còn mở nhưng không chặn** (làm theo cách tạm ghi trong spec, đặt sau cấu hình hoặc một hàm riêng để đổi được):

- TM Q1–Q10, Q13; LT R1–R10, R12.
- TM Q9, Q14 (VNPAY): M.Trường đang hỏi VNPAY. Đợt vẫn chạy theo cách tạm; phải có trả lời trước khi lên môi trường thật.
- Đã chốt ngày 2026-11-17, không còn mở: TM Q11, Q15; LT R11, R13 (mọi đề làm tuần tự, LT L7), R14 (theo BE mục 12, LT F22); sinh đề chạy nền (LT L8).

## A3. Ủy quyền cho đợt chạy

Khi người bảo bắt đầu, plan này là yêu cầu rõ ràng cho đợt chạy. Các việc sau được phép mà không hỏi lại:

- Tạo worktree, nhánh lane; commit; push nhánh lane và nhánh tích hợp.
- Mở PR lane → `integration/dot-3`; Advisor merge sau khi nghiệm thu.
- Trên UniAI: tạo issue cha và sub-issue theo A4; đặt `in_progress`, `in_review`, `blocked`; bình luận `[agent]`.
- Chạy Docker Compose dev (có Mailpit); cài phụ thuộc bằng `pnpm` và Maven.
- Thêm thư viện đã nêu tên ở B7. Thư viện khác phải nêu lý do trong PR.
- Sửa BE spec ở các chỗ TM Q11, Q15, LT R11 đã liệt kê. Sửa spec ở những chỗ plan này ghi "sửa spec". Sửa lỗi chính tả, liên kết.
- Gọi API VNPAY **sandbox** (VP-1, VP-2), sau G2.
- Cho VP-2 (G3 đã có): đưa `integration/dot-3` lên bản demo trên `bro` bằng `demo-setup.sh` sẵn có, và thêm khối `location` nginx đã nêu ở G3. Luôn chạy `nginx -t` trước khi nạp lại, vì nginx này dùng chung với dự án khác.

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
  2. Tạo 36 sub-issue theo bảng A5, tiêu đề `<mã task> · <tên>`.
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

Tổng 103 điểm, 36 task. Chỉ tính điểm khi Advisor đã nghiệm thu và merge vào `integration/dot-3`. Phần trăm tiến độ tính trên 103. Trọng số sửa ngày 2026-11-17 theo đợt 2B: EX-2, AT-2b, FS-5 giảm 1 vì phần định hướng Tin học và cảnh báo chấm tay chuyển đi; thêm CM-6 (3), FB (2); FA-4 tăng 1 vì có thêm làm thử như học viên.

| Task | Tên | Nhóm | Điểm |
|---|---|---|---|
| C-0 | Khung module `commerce`, `attempt`; quyền bit 18–26; hợp đồng SPI; `StudentLearningProvider` nhận danh sách; pepper; ngôn ngữ ưa dùng | Nền | 3 |
| C-0b | Profile `e2e`, đồng hồ thử, `/api/v1/test/**`, `ForbiddenKeysAssert` | Nền | 1 |
| ML | Gửi thư `shared/mail` (hàng thư, worker, SMTP, mẫu vi/en, endpoint đọc thư cho E2E) | Nền | 3 |
| EX-1 | Module `exam`: thông tin bán, ảnh bìa, kiểm tra khi công bố, quy tắc E24, sự kiện, `PublishedExamPort` | Nền | 4 |
| EX-2 | Module `exam`: `PaperSolverPort` trên mô hình 2B, đo hiệu năng | Nền | 2 |
| DS | Bộ dữ liệu bài đã công bố cho 7 kỳ thi, dùng cho test, dev, E2E | Nền | 3 |
| VP-1 | Thử VNPAY sandbox trước khi làm cổng thật; vector chữ ký thật | Nền | 1 |
| ID-1 | Đăng ký, xác minh email, đổi email | Tài khoản | 3 |
| ID-2 | Quên và đặt lại mật khẩu, hồ sơ, ảnh đại diện | Tài khoản | 2 |
| CM-1 | Bảng chiếu sản phẩm, API công khai | Thương mại | 3 |
| CM-2 | Khuyến mãi, voucher, tính giá | Thương mại | 4 |
| CM-4a | Bảng quyền dùng, `EntitlementPort`, cấp quyền nội bộ | Thương mại | 2 |
| CM-4b | `exam-access`, `/me/exams` | Thương mại | 2 |
| CM-3a | Đơn hàng, cổng giả lập, IPN, trang quay lại, đơn 0 đồng, cấp quyền sau thu tiền | Thương mại | 4 |
| CM-3b | Cổng VNPAY thật, `querydr`, đơn hết hạn, tiền về muộn sau hết hạn | Thương mại | 3 |
| CM-5 | Quản lý đơn, người mua, hoàn tiền, tiếp tục cấp quyền, đối soát | Thương mại | 4 |
| VP-2 | Luồng VNPAY sandbox thật có IPN, qua bản demo | Thương mại | 1 |
| CM-6 | Bộ đề: bảng, công bố, bảng chiếu, đơn nhiều bài, cấp quyền từng bài, hoàn tiền, voucher phạm vi bộ | Thương mại | 3 |
| AT-1 | Lượt thi: bảng, chuẩn bị, bắt đầu và sinh đề chạy nền, đảo, trạng thái, `AttemptGuard` | Lượt thi | 5 |
| AT-2a | Chuyển phần tuần tự, hoàn thành phần, nộp, đóng lượt, worker hạn giờ | Lượt thi | 4 |
| AT-2b | Chọn phần 3, thử lại, sinh lỗi | Lượt thi | 2 |
| AT-3 | Lưu bài, phiên sửa, tiếp quản, tài nguyên của lượt | Lượt thi | 4 |
| AT-4 | Chấm tự động, tổng hợp, kết quả, xem lại, lịch sử | Lượt thi | 4 |
| AT-5 | Hàng chờ chấm tự luận, chấm lại, vận hành lượt | Lượt thi | 3 |
| FS-1 | Web học viên: trang giới thiệu, danh mục, chi tiết; `proxy.ts`; thanh đầu trang | Web học viên | 3 |
| FS-2 | Web học viên: đăng ký, xác minh, quên mật khẩu, hồ sơ | Web học viên | 3 |
| FS-3 | Web học viên: xác nhận mua, kết quả thanh toán, Bài thi của tôi, đơn của tôi | Web học viên | 3 |
| FS-4 | `packages/ui`: thành phần 8 dạng câu (làm bài và chỉ đọc); đưa KaTeX, `RichTextView` vào `packages/ui` | Web học viên | 3 |
| FS-5 | Web học viên: chuẩn bị (có chọn định hướng Tin học), chờ sinh đề, màn làm bài, lưu, ngoại tuyến, tiếp quản | Web học viên | 4 |
| FS-6 | Web học viên: kết quả, xem lại, lịch sử | Web học viên | 2 |
| FB | Bộ đề trên web: màn admin "Bộ đề"; thẻ, chi tiết, mua bộ và nhóm thẻ theo bộ ở web học viên | Web học viên | 2 |
| FA-1 | Web admin: tab Thông tin bán, ảnh bìa, cảnh báo chấm tay, menu của đợt | Web admin | 2 |
| FA-2 | Web admin: Khuyến mãi | Web admin | 2 |
| FA-3 | Web admin: Đơn hàng, người mua, hoàn tiền | Web admin | 3 |
| FA-4 | Web admin: hàng chờ chấm thật, vận hành lượt, làm thử như học viên | Web admin | 3 |
| QA | E2E đầu cuối, kiểm thử bảo mật, tài liệu | Nghiệm thu | 3 |

Cộng theo nhóm: Nền 17, Tài khoản 5, Thương mại 26, Lượt thi 22, Web học viên 20, Web admin 10, Nghiệm thu 3. Tổng 103.

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
- **Profile `e2e` của backend** (C-0b tạo; chưa có trên `develop`):
  - đồng hồ điều khiển được và `POST /api/v1/test/clock` (C-0b);
  - đọc thư `GET /api/v1/test/mail` (ML);
  - cổng VNPAY giả lập (CM-3a).

  Mọi đường `/api/v1/test/**` chỉ tồn tại khi bật profile `e2e`. Profile này bật cùng một profile của môi trường thật (`prod`) thì ứng dụng không khởi động.
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
4. **Endpoint công khai:** nằm trong danh sách mở của `SecurityConfig` và `CookieAuthFilter`, có giới hạn tần suất, có test gọi không có cookie.
5. **Lệnh ghi có idempotency** (B4): có test gửi lại cùng khóa, và cùng khóa khác thân. Lệnh có `expectedRevision` hay `expectedVersion`: có test xung đột.
6. **Migration mới** chạy được trên DB của đợt 2 (Testcontainers chạy toàn bộ migration từ đầu, cộng dữ liệu mẫu). Test không giả định bảng rỗng.
7. **Màn web:**
   - đủ bốn trạng thái (đang tải, rỗng, lỗi, có dữ liệu);
   - chỉ dùng token của `packages/ui`;
   - đúng ở **cả sáng và tối**;
   - **không lẫn ngôn ngữ** (mọi khóa có ở `vi.json` và `en.json`);
   - đúng ở 390px và 1280px.
8. `ApplicationModules.verify()` đạt.
9. Không còn `TODO` thiếu mã issue, không còn mã chết.
10. **Không có bí mật trong diff.** Lane chạy và dán kết quả (chỉ là số dòng khớp, không in nội dung):

    ```
    git diff origin/integration/dot-3...HEAD | grep -ciE "hash_secret=.+|pepper=.{8,}|smtp_password=.+|tmn_code=[A-Z0-9]{8}"
    git diff --name-only origin/integration/dot-3...HEAD | grep -cE "(^|/)\.env$"
    ```

    Cả hai phải ra `0`. Giá trị dev công khai (B6) đặt trong `application-dev.yml`, `application-e2e.yml`, `application-test.yml` dưới khóa YAML lồng nhau (không có dạng `pepper=`), giá trị bắt đầu bằng `dev-only-`.

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

## B2. Mã lỗi, transaction, khóa

- **Khóa lạc quan:** 412 như đợt 1, 2. **Lỗi nghiệp vụ:** 422 `BusinessRuleException`. **Xung đột trạng thái:** 409 `ConflictException`. Không thêm kiểu lỗi mới.
- **Mã lỗi mới** thêm vào cả ba tệp `messages*.properties`, và khóa web vào `vi.json`, `en.json` của app tương ứng.
- **Hai transaction tách nhau** (TM 8.1): mọi đường thu tiền (IPN, `querydr`, đơn 0 đồng) commit `PAID` trước. Cấp quyền chạy ở `FulfillmentService.fulfill(orderId)` với transaction riêng (`REQUIRES_NEW`), gọi sau commit (`TransactionSynchronization.afterCommit`) và từ worker.
- **Khóa nhiều dòng** theo thứ tự cố định:
  - `purchase_order` → `payment` → `promotion` → `entitlement` → `entitlement_grant`;
  - `attempt` → `attempt_part` → `attempt_item`.
- **Không gọi mạng trong transaction đang giữ khóa dòng.** Áp cho VNPAY, SMTP, Keycloak, MinIO. Gọi trước rồi ghi DB sau, hoặc dùng outbox. Riêng Keycloak khi đăng ký và đổi email theo cách bù của PBI-01.
- **Sinh đề chạy nền** (LT L8, 5.2): `GenerationExecutor` chạy trong tiến trình nhận yêu cầu, số luồng và độ dài hàng giới hạn (`thi360.attempt.generation.*`), nhận việc sau commit. Bean này có ở cả profile `api` và `worker`: việc tự thử lại sinh phần (B5) chạy trong tiến trình worker. Bộ giải chạy ngoài transaction. Không dùng outbox cho việc này vì cần bắt đầu ngay; lượt hoặc phần kẹt do worker dọn (B5). Kết quả sinh chỉ được lưu khi trạng thái và `generation_token` còn khớp (LT 5.2 bước 3), để việc đến muộn không ghi đè lượt đã bị worker hủy.

## B3. Quyền

- **Bit cố định** theo TM 13: 18 `PROMOTION.VIEW`, 19 `PROMOTION.MODIFY`, 20 `ORDER.VIEW`, 21 `ORDER.REFUND`, 22 `ORDER.RECOVER`, 23 `GRADING.VIEW`, 24 `GRADING.MODIFY`, 25 `ATTEMPT.VIEW`, 26 `ATTEMPT.COMPENSATE`.
- **`PermissionObject`** thêm `PROMOTION`, `ORDER`, `GRADING`, `ATTEMPT`.
- **Vai trò mặc định:**
  - Gán qua `PermissionCatalogSync` như đợt 2; `SYSTEM_ADMIN` nhận cả 9 quyền.
  - Vai trò "Giáo viên" (nếu có trong `SYSTEM_ROLE_DEFAULTS`) nhận `GRADING.VIEW`, `GRADING.MODIFY`. Không có vai trò này thì không thêm vai trò mới.
- **Chỉ C-0 sửa** `Permission.java`, `PermissionObject.java`, `Permissions.java`, `PermissionCatalogSync`.

## B4. Idempotency

- Dùng `IdempotentCommand` của đợt 2. Phạm vi khóa `<userId>:<thao tác>:<id đối tượng>`.
- **Lệnh bắt buộc khóa:**
  - TM: tạo đơn, `/pay`, hoàn tiền, tạo khuyến mãi, tiếp tục cấp quyền.
  - LT: bắt đầu lượt, chọn phần 3, thử lại, nộp bài, cấp bù.
- Web sinh `crypto.randomUUID()` một lần cho mỗi lần bấm, gửi lại cùng khóa khi thử lại.

## B5. Đồng hồ và worker

- **Mọi chỗ đọc giờ** dùng bean `Clock` (`ClockConfig` sẵn có). Không dùng `Instant.now()` trực tiếp.
- **Profile `e2e`:** bean `MutableClock` thay `Clock`, kèm `POST /api/v1/test/clock` để Playwright tua giờ. Do C-0b làm.
- **Worker** chạy ở profile `worker` (`WorkerSchedulingConfig`, `on-profile: worker & !api` sẵn có), bằng `@Scheduled`. **Chống chạy trùng:**
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

## B6. Bảo mật

- **Endpoint công khai mới** (TM 13) có giới hạn tần suất theo IP qua `RateLimitFilter` sẵn có. IPN được miễn (TM 8.4).
- **IP thật của người gọi:** proxy của web (`packages/web-kit/src/proxy.ts`) bỏ `X-Forwarded-For` do trình duyệt gửi. Nó chỉ đặt lại header này từ `x-real-ip` khi `THI360_TRUST_REAL_IP=1`, hoặc qua tùy chọn `resolveClientIp`. Vì vậy:
  - Giới hạn tần suất theo IP của các endpoint công khai gọi qua proxy web (đăng ký, quên mật khẩu, danh mục phía trình duyệt) chỉ đúng khi nginx đặt `x-real-ip` và web chạy với `THI360_TRUST_REAL_IP=1`. Thiếu thì mọi người dùng chung một IP và chặn lẫn nhau. Tài liệu triển khai (QA) ghi điều kiện này; test của ID-1 và CM-1 đặt header ở tầng backend.
  - Trang công khai dựng phía máy chủ gọi API danh mục từ máy chủ web, luôn cùng một IP. Các lời gọi này được lưu đệm 60 giây; CM-1 miễn giới hạn tần suất cho danh sách IP của máy chủ web, đặt trong cấu hình (`thi360.security.rate-limit.exempt-ips`, mặc định rỗng). Mọi IP khác vẫn theo ngưỡng 120 yêu cầu mỗi phút. Test của CM-1 có cả hai trường hợp.
  - IPN và trang quay lại của VNPAY phải tới thẳng backend, không qua proxy của web. Trên bản demo là khối `location` nginx ở G3; tài liệu triển khai (QA) ghi rõ yêu cầu này.
  - Lọc IP của IPN (TM Q9) đọc IP từ kết nối tới backend sau nginx, theo cấu hình `server.forward-headers-strategy` hiện có.
- **Không lộ đáp án** (LT 7.3) và **không lộ dữ liệu ẩn của bảng chiếu** (TM 10.1):
  - C-0b tạo `ForbiddenKeysAssert` ở `backend/src/test/java/vn/unicom/thi360/support/`, quét JSON tìm khóa cấm; danh sách khóa cấm ghi ở một nơi trong lớp này.
  - EX-1, CM-1, AT-1 và QA dùng lớp này; task nào cần thêm khóa thì thêm vào danh sách chung.
- **Mã OTP và mã đặt lại** băm HMAC bằng `THI360_TOKEN_PEPPER`.
  - Thiếu biến thì ứng dụng không khởi động.
  - Profile `dev`, `e2e`, `test` có giá trị công khai `dev-only-…` trong tệp cấu hình của profile, để compose dev, E2E trên CI và bản demo chạy được. Bản demo dùng giá trị riêng trong `.env` của nó.
  - C-0 cập nhật `.github/workflows/e2e.yml` nếu workflow cần biến này.
- **IPN và trang quay lại:** kiểm chữ ký trước mọi xử lý khác. Ghi `payment_event` cả khi chữ ký sai.
- **`/api/v1/test/**`:** chỉ đăng ký khi bật profile `e2e`; không có trong OpenAPI; profile khác gọi thì 404.

## B7. Thư viện được thêm

| Nơi | Thư viện | Dùng cho | Task |
|---|---|---|---|
| backend | `spring-boot-starter-mail` | Gửi SMTP | ML |
| backend | `spring-boot-starter-thymeleaf` | Mẫu thư HTML và văn bản (TM 4) | ML |
| backend (test) | `com.icegreen:greenmail-junit5` | Test lớp gửi SMTP, mã 5xx | ML |
| backend | `net.coobird:thumbnailator` | Cắt vuông, thu nhỏ ảnh đại diện | ID-2 |
| packages/ui | `katex` 0.19.1 (chuyển từ web-admin) | Công thức trong câu hỏi | FS-4 |
| web-student | `@tiptap/react`, `@tiptap/starter-kit` 3.34.0 (cùng phiên bản với web-admin) | Ô viết tự luận | FS-5 |

- **Sửa spec E20:** ảnh đại diện lưu **PNG** 256px. ImageIO không ghi được WebP nếu không có thư viện; dùng PNG thì không cần thêm. ID-2 sửa TM E20 trong PR của mình.
- Không thêm thư viện IndexedDB: FS-5 viết một lớp bọc nhỏ.
- Không có Storybook trong repo và không thêm: thành phần giao diện thử trên trang thử, chỉ có khi chạy dev.
- Phiên bản cố định trong `pom.xml` và `package.json`.

## B8. Web

- **web-student:**
  - Trang công khai dựng phía máy chủ (`revalidate: 60`), phần theo phiên chạy phía client (TM 11.1).
  - **Chỉ FS-1 sửa `proxy.ts` và cấu trúc thanh đầu trang.** FS-1 thêm đủ mọi đường công khai của cả đợt ngay từ đầu (`/`, tiền tố `/exams/`, `/register`, `/forgot-password`, `/reset-password`, `/checkout/result`) và mọi mục của thanh đầu trang (Bài thi của tôi, Lịch sử, Hồ sơ, Đơn của tôi). Mục trỏ tới trang chưa có thì ẩn bằng một danh sách trong cùng tệp; lane làm trang đó bỏ dòng ẩn.
- **web-admin:**
  - **Chỉ FA-1 sửa cấu trúc menu.** FA-1 thêm nhóm "Thương mại" (Khuyến mãi, Đơn hàng) và mục "Lượt thi", ẩn theo quyền. Mục trỏ tới trang chưa có thì ẩn như trên; FA-2, FA-3, FA-4 bỏ dòng ẩn của mình.
  - FA-4 bỏ cờ `NEXT_PUBLIC_THI360_GRADING_MOCK` và các tệp mock của `/grading` còn từ đợt 2, cùng `questionStore` nếu không còn ai import.
- **packages/ui:**
  - FS-4 làm thành phần câu hỏi; lane khác chỉ dùng. Đổi API thành phần thì qua Advisor.
  - FS-4 chép `RichTextView` và KaTeX từ web-admin vào `packages/ui`, **không sửa web-admin**: FS-4 không bị G0b chặn, còn đợt 2B đang viết lại `apps/web-admin/src/features/exams/**`.
  - FA-1 (sau G0b) đổi import của web-admin sang `packages/ui`, xóa bản cũ ở web-admin và chạy lại test của web-admin.
- **Kiểu dữ liệu** sinh từ OpenAPI (`packages/api-client`) là nguồn đúng.
- **Thiết kế:** `docs/design/dot-3/` (canvas trang 10–16), README ghi màn theo task và các chỗ còn chốt. Lấy bố cục, câu chữ, trạng thái (kể cả khối "Trạng thái khác"); màu, cỡ theo token `DESIGN.md`. Đặc tả thắng bản vẽ khi khác nhau.

## B9. Vùng dễ xung đột khi chạy song song

- **Tệp chung** (`backend/pom.xml`, `application.yml`, `.env.example`, `messages_*.properties`, `SecurityConfig`): thêm ở cuối khối, giữ tối thiểu.
- **Chỉ một task sửa:**
  - `identity/spi/Permission*`, `PermissionCatalogSync`: C-0.
  - Profile `e2e` (`application-e2e.yml`), `MutableClock`, phần dùng chung của `/api/v1/test/**`, `ForbiddenKeysAssert`: C-0b. ML và CM-3a chỉ thêm endpoint hoặc bean của mình vào profile này.
  - `commerce/spi/**`: C-0; đổi sau đó qua Advisor (B1).
  - `shared/mail/**`: ML.
  - `shared/**` khác: C-0, riêng phần đồng hồ thử của C-0b. Lane khác cần thì báo Advisor.
  - `exam/**`: EX-1 và EX-2, chia vùng. EX-1 sửa `listing`, `publish`, sự kiện, `PublishedExamPort` **và kiểu kết quả kiểm tra cấu hình** (thêm nhóm "Thông tin bán"; mức cảnh báo và `MANUAL_GRADING_REQUIRED` đã có từ đợt 2B). EX-2 sửa `PaperSolverPort` và phần dựng bài toán từ bản chụp; không sửa thuật toán bộ giải của đợt 2B.
- **`identity/api/AuthController`, `SecurityConfig`:** ID-1 và ID-2 cùng sửa. Lane merge sau rebase.
- **`commerce/**`:** mỗi task một package con.
  - CM-1 `storefront`; CM-2 `pricing`, `promotion`; CM-4a, CM-4b `entitlement`; CM-3a `order`, `payment`, `fulfillment`; CM-3b `payment.vnpay`, `order.expiry`; CM-5 `refund`, `admin`, `reconciliation`; CM-6 `bundle`, và các chỗ sửa nhỏ ở `order`, `fulfillment`, `storefront`, `promotion` (sau khi các task chủ đã merge).
  - `VnpayGateway` là interface do CM-3a tạo, gồm `pay`, `verify`, `queryDr`, `refund`. CM-3a cài bản giả lập đủ bốn hàm. CM-3b cài bản thật. CM-5 chỉ gọi interface.
- **`attempt/**`:**
  - AT-1 sở hữu `AttemptGuard`, `AttemptStartService`, `GenerationExecutor`. Thứ tự câu và đảo phương án thuộc `exam/spi/PaperOrdering` của đợt 2B (2B K12); module `attempt` không có lớp đảo riêng.
  - AT-2a sở hữu `AttemptLifecycleService` (chuyển phần, đóng lượt). AT-2b làm `AttemptChoiceService` riêng, chỉ gọi `AttemptLifecycleService` qua hàm công khai và điểm mở AT-2a để sẵn.
  - AT-3 sở hữu `AnswerService`, `EditSessionService`. AT-2a, AT-2b, AT-3 chỉ kiểm phần đang mở và hạn qua `AttemptGuard`.
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
- G0 và G1 chặn mọi task; bảng không ghi lại.

| Task | Phụ thuộc cứng | Phụ thuộc mềm | Sóng |
|---|---|---|---|
| C-0 | | | 1 |
| C-0b | | | 1 |
| ML | | C-0b (profile `e2e`) | 1 |
| EX-1 | G0b | C-0b (`ForbiddenKeysAssert`) | 1 |
| EX-2 | G0b | | 1 |
| FS-4 | | | 1 |
| VP-1 | G2 | | 1 |
| CM-4a | C-0 | | 2 |
| DS | EX-1 | EX-2 | 2 |
| ID-1 | C-0, ML | | 2 |
| ID-2 | C-0, ML | | 2 |
| CM-1 | C-0, C-0b, EX-1 | DS | 2 |
| FA-1 | EX-1, EX-2, FS-4 | | 2 |
| AT-1 | C-0, C-0b, EX-1, EX-2, CM-4a, DS | | 3 |
| CM-2 | CM-1 | | 3 |
| CM-4b | CM-1, CM-4a | | 3 |
| FS-1 | CM-1 | CM-4b (`exam-access`) | 3 |
| FS-2 | ID-1, ID-2 | FS-1 (`proxy.ts`) | 3 |
| AT-2a | AT-1 | | 4 |
| AT-3 | AT-1 | | 4 |
| CM-3a | CM-2, CM-4b, ML | | 4 |
| FA-2 | CM-2, FA-1 | | 4 |
| AT-2b | AT-2a | | 5 |
| AT-4 | AT-2a | AT-2b, AT-3 | 5 |
| CM-3b | CM-3a | VP-1 (vector thật) | 5 |
| CM-5 | CM-3a | | 5 |
| CM-6 | CM-1, CM-3a, CM-4b | CM-5 (hoàn tiền) | 5 |
| FS-3 | CM-3a, CM-4b, FS-1 | AT-1, FS-2 | 5 |
| AT-5 | AT-4 | | 6 |
| FS-5 | FS-4, AT-2b, AT-3 | | 6 |
| FS-6 | FS-4, AT-4 | | 6 |
| FA-3 | CM-5, FA-1 | | 6 |
| FB | CM-6, FS-1, FS-3, FA-1 | FA-3 (đơn bộ ở `/orders`) | 6 |
| VP-2 | CM-3b, G2 | | 6 |
| FA-4 | AT-5, FA-1, FS-4 | | 7 |
| QA | tất cả, trừ VP-1, VP-2 nếu chưa có G2 | | 8 |

## C2. Sơ đồ pipeline

Mỗi hàng là một sóng. `◄─` là phụ thuộc cứng; `(┄ X)` là phụ thuộc mềm.

```
G0 (đợt 2 về develop) + G1 (duyệt plan)          G0b (đợt 2B về develop) chặn EX-1, EX-2
│
├ Sóng 1 ─ C-0   C-0b   ML (┄ C-0b)   EX-1 ◄─ G0b (┄ C-0b)   EX-2 ◄─ G0b   FS-4   VP-1 ◄─ G2
│
├ Sóng 2 ─ CM-4a ◄─ C-0              DS ◄─ EX-1 (┄ EX-2)
│          ID-1, ID-2 ◄─ C-0, ML     CM-1 ◄─ C-0, C-0b, EX-1 (┄ DS)
│          FA-1 ◄─ EX-1, EX-2, FS-4
│
├ Sóng 3 ─ AT-1 ◄─ C-0, C-0b, EX-1, EX-2, CM-4a, DS
│          CM-2 ◄─ CM-1              CM-4b ◄─ CM-1, CM-4a
│          FS-1 ◄─ CM-1 (┄ CM-4b)    FS-2 ◄─ ID-1, ID-2 (┄ FS-1)
│
├ Sóng 4 ─ AT-2a ◄─ AT-1             AT-3 ◄─ AT-1
│          CM-3a ◄─ CM-2, CM-4b, ML  FA-2 ◄─ CM-2, FA-1
│
├ Sóng 5 ─ AT-2b ◄─ AT-2a            AT-4 ◄─ AT-2a (┄ AT-2b, AT-3)
│          CM-3b ◄─ CM-3a (┄ VP-1)   CM-5 ◄─ CM-3a   CM-6 ◄─ CM-1, CM-3a, CM-4b (┄ CM-5)
│          FS-3 ◄─ CM-3a, CM-4b, FS-1 (┄ AT-1, FS-2)
│
├ Sóng 6 ─ AT-5 ◄─ AT-4              FS-5 ◄─ FS-4, AT-2b, AT-3
│          FS-6 ◄─ FS-4, AT-4        FA-3 ◄─ CM-5, FA-1
│          VP-2 ◄─ CM-3b, G2              FB ◄─ CM-6, FS-1, FS-3, FA-1 (┄ FA-3)
│
├ Sóng 7 ─ FA-4 ◄─ AT-5, FA-1, FS-4
│
└ Sóng 8 ─ QA ◄─ tất cả
```

Ba luồng chạy song song từ sóng 2:

```
Lượt thi:   C-0 ─► CM-4a ─┐      (C-0b cũng vào AT-1)
            EX-1 ─► DS ───┼─► AT-1 ─► AT-2a ─┬─► AT-4 ─► AT-5 ─► FA-4 ─► QA
            EX-2 ─────────┘     └─► AT-3 ─┐  └─► AT-2b ─┐
                                          └─────────────┴─► FS-5 ─► QA

Thương mại: EX-1 ─► CM-1 ─┬─► CM-2 ──┬─► CM-3a ─┬─► CM-5 ─► FA-3 ─► QA
                          └─► CM-4b ─┘          ├─► CM-3b ─► VP-2
                                                └─► CM-6 ─► FB ─► QA

Tài khoản:  ML ─► ID-1, ID-2 ─► FS-2
```

## C3. Đường dài nhất và ưu tiên

- **Đường dài nhất (lượt thi):** G0b → EX-1 → DS → AT-1 → AT-2a → AT-4 → AT-5 → FA-4 → QA. Đợt 2B nằm trước đường này, nên trong lúc chờ G0b nên làm hết các task không bị chặn (C-0, C-0b, ML, FS-4, VP-1, CM-4a, ID-1, ID-2, FS-2).
- **Đường dài thứ hai (thương mại):** EX-1 → CM-1 → CM-2 → CM-3a → CM-5 → FA-3 → QA, gồm 7 task.
- **Ưu tiên người và máy:**
  1. C-0, C-0b, EX-1, CM-4a, DS: nhỏ, nhưng mở khóa AT-1. Mở ngay ở sóng 1, 2.
  2. AT-1, AT-2a, AT-4: dùng mô hình mạnh nhất có.
  3. CM-3a (IPN, tranh chấp đồng thời).
  4. FS-4, FS-5 (màn làm bài).
- **Song song tối đa** ở sóng 2 đến 6: tới 6 lane cùng lúc nếu máy chịu được.

## C4. Mốc

| Mốc | Gồm | Kết quả thấy được |
|---|---|---|
| M1 | C-0, C-0b, ML, EX-1, EX-2, FS-4, VP-1 (nếu có G2) | Nền sẵn sàng: thư tới Mailpit, cổng `exam` mới, thành phần câu hỏi trên trang thử, vector chữ ký VNPAY thật |
| M2 | CM-4a, DS, ID-1, ID-2, CM-1, CM-4b, FA-1, FS-1, FS-2 | Khách xem danh mục thật; đăng ký, xác minh, quên mật khẩu chạy thật; admin sửa thông tin bán |
| M3 | CM-2, CM-3a, CM-3b, FA-2, FS-3 | Mua đầu cuối với cổng giả lập; voucher; Bài thi của tôi. FS-3 cần AT-1 (M4) trước khi nộp. |
| M4 | AT-1, AT-2a, AT-2b, AT-3, AT-4, AT-5, FS-5, FS-6, FA-4 | Làm bài, chấm, kết quả, lịch sử, chấm tự luận |
| M5 | CM-5, CM-6, FA-3, FB, VP-2 (nếu có G2), QA | Quản lý đơn, hoàn tiền; E2E đầu cuối; PR `integration/dot-3` → `develop` |

M3 và M4 chạy song song; M5 cần cả hai.

---

# Phần D: Hợp đồng từng task

Mỗi task gồm ba mục:

- **Spec:** mục phải đọc.
- **Sản phẩm:** phải có khi nộp.
- **Nghiệm thu:** điều kiện ngoài A7.

Số mục trỏ vào TM, LT, BE.

## C-0 · Khung module, quyền, hợp đồng SPI

**Spec:** TM 3, 4 (ngôn ngữ ưa dùng), 8.5, 13; LT 3, 5.1, 12 (quyền); plan B1, B3, B6.

**Sản phẩm:**

- Package `commerce`, `attempt` với `package-info.java` (`@ApplicationModule`); `spi` có `@NamedInterface("spi")`.
- `commerce/spi`: `EntitlementPort` đủ các hàm ở B1, `AttemptStatusProvider` kèm bản rỗng mặc định, các record đi kèm. Chữ ký theo TM 8.5, LT 3, LT 5.1.
- `UserAdminService` nhận `List<StudentLearningProvider>` và gộp kết quả (B1). Có test với hai bean giả.
- Schema `commerce`, `attempt` trong Flyway; một migration rỗng tạo schema mỗi bên.
- Quyền bit 18–26 theo B3, gán trong `PermissionCatalogSync`.
- `THI360_TOKEN_PEPPER` trong cấu hình, giá trị `dev-only-…` cho `dev`, `test` (B6); giá trị cho `e2e` do C-0b thêm. `.env.example` chỉ có tên. Cập nhật `.github/workflows/e2e.yml` nếu cần.
- Cột `identity.user_account.preferred_locale` (TM 4), ghi khi đăng nhập từ cookie `thi360_locale`.

**Nghiệm thu:**

- `ApplicationModules.verify()` đạt với hai module mới.
- DB mới và DB của đợt 2: `SYSTEM_ADMIN` có 9 quyền mới. Vai trò đã sửa vẫn giữ lựa chọn cũ.
- `AdminApiForbiddenMatrixIT` vẫn xanh.
- Thiếu pepper ở profile không có giá trị mặc định thì lỗi khởi động.
- Compose dev khởi động được sau khi merge.

## C-0b · Profile e2e, đồng hồ thử, `ForbiddenKeysAssert`

**Spec:** plan A6, B5, B6; LT 13; TM 14.

**Sản phẩm:**

- Profile `e2e`:
  - tệp `application-e2e.yml` (kèm giá trị `dev-only-…` của pepper);
  - chốt chặn khởi động (A6);
  - `MutableClock` thay bean `Clock`;
  - `POST /api/v1/test/clock` (đặt giờ, tua thêm);
  - quy tắc `/api/v1/test/**` theo B6, để ML và CM-3a thêm endpoint của mình.
- `ForbiddenKeysAssert` và danh sách khóa cấm (B6), kèm test của chính lớp này.

**Nghiệm thu:**

- Profile `api` thường gọi `POST /api/v1/test/clock` nhận 404; profile `e2e` thì tua được giờ, và giờ mới thấy được qua một endpoint trả `serverNow`.
- Khởi động với `e2e` cộng `prod` thì lỗi.
- `/api/v1/test/**` không có trong OpenAPI.

## ML · Gửi thư

**Spec:** TM 4, C10, 5.3 (loại thư), 9.4 bước 7. **Phụ thuộc mềm:** C-0b.

**Sản phẩm:**

- Bảng `shared.email_message`, `MailQueue.enqueue(kind, to, locale, model, userId)` chạy trong transaction của bên gọi.
- Worker gửi theo lô, `FOR UPDATE SKIP LOCKED`, lịch gửi lại, 5xx thất bại ngay, dọn thư đã gửi sau 30 ngày.
- `JavaMailSender` theo biến `THI360_SMTP_*`, `THI360_MAIL_FROM`, `THI360_WEB_URL`.
- Mẫu Thymeleaf `resources/mail/templates/<kind>.<vi|en>.{html,txt}` với một layout chung. Loại thư: `EMAIL_VERIFY`, `EMAIL_CHANGE`, `EMAIL_CHANGED_NOTICE`, `PASSWORD_RESET`, `ORDER_FULFILLED`, `REFUND_DONE`.
- Kiểm mẫu lúc khởi động: thiếu mẫu thì không khởi động.
- Lớp hỗ trợ test `MailOutbox` đọc thư theo người nhận và loại.
- `GET /api/v1/test/mail?to=&kind=` (profile `e2e`, B6) trả thư mới nhất, kể cả mã OTP trong thân thư.

**Nghiệm thu:**

- Transaction rollback thì không có thư.
- GreenMail nhận đúng tiêu đề, HTML, văn bản theo ngôn ngữ.
- 5xx ở `RCPT TO` thì thất bại ngay. Lỗi tạm thì gửi lại theo lịch.
- Hai worker chạy cùng lúc không gửi trùng.

## EX-1 · Module `exam`: thông tin bán, sự kiện, cổng đọc

**Spec:** TM 3, 6, E24, E26, 10.1 (dữ liệu cần cho bảng chiếu), Q11, Q15; BE 5.2 (công bố); 2B 9. **Cổng:** G0b. **Phụ thuộc mềm:** C-0b.

**Sản phẩm:**

- Bảng `exam.exam_listing`, `exam.exam_cover_asset`. API `GET/PUT /exams/{id}/listing`, `POST /exams/{id}/cover` (TM 6.3).
- Sửa thông tin bán tăng `revision`, đặt `has_unpublished_changes`, không vào `input_fingerprint`.
- Kiểm tra thông tin bán trong transaction công bố.
- Đổi kiểu kết quả `GET /exams/{id}/check`: thêm nhóm "Thông tin bán" (lỗi `LISTING_*`, tính riêng, không làm cũ kết quả kiểm tra cấu hình). Mức `WARNING` đã có từ đợt 2B (2B 7.3).
- `exam_version.config.listing` chụp lúc công bố.
- **Quy tắc E24:** bài có lựa chọn sau khi bắt đầu (phần tự chọn sinh sau: phần 3 HSA, QDA FULL) thì công bố phải gồm mọi đường đang phục vụ. Thiếu thì 422 `PUBLISH_ALL_PATHS_REQUIRED`. Định hướng Tin học chọn trước khi bắt đầu (2B K2), không thuộc quy tắc này.
- Sự kiện `ExamPublished`, `ExamDiscontinued` qua outbox sau commit. Thân sự kiện chỉ có id và `versionNo`; bên nhận đọc lại qua cổng.
- `exam/spi/PublishedExamPort`:
  - `findServing(examId)`: gồm `parts[].paths[]` (số câu, điểm tối đa, thời lượng của từng đường), `config.combinations`, `config.omittedGroups` (2B 9), đủ cho thẻ, chi tiết, màn chuẩn bị và bộ đề (CM-6);
  - `loadVersion(examId, versionNo)`;
  - `openCover(assetId)`.

  Kết quả không chứa câu hỏi, đáp án, nguồn, đề chứng minh. Bài `DISCONTINUED` vẫn đọc được phiên bản phục vụ cuối cùng.
- Sao chép bài chép cả thông tin bán.
- Sửa BE spec mục 5.2 (quy tắc E24, kiểm tra thông tin bán).

**Nghiệm thu:**

- Xóa giá sau khi kiểm tra cấu hình đạt thì công bố bị chặn (`LISTING_PRICE_MISSING`).
- Chỉ sửa thông tin bán thì không phải kiểm tra lại cấu hình.
- Công bố HSA FULL chỉ một đường thì 422 `PUBLISH_ALL_PATHS_REQUIRED`.
- Sự kiện phát đúng một lần sau commit, không phát khi rollback.
- `ForbiddenKeysAssert` trên kết quả của `PublishedExamPort`.

## EX-2 · Module `exam`: cổng sinh đề

**Spec:** LT 3, F4, F5, F22; BE 5.4; 2B 6.3, 9, 11. **Cổng:** G0b.

Đợt 2B đã làm thuật toán (17 − 1, chỉ tiêu chủ đề, ghép môn theo mã), `solvePick` loại câu, `PaperOrdering` và cảnh báo `MANUAL_GRADING_REQUIRED`. EX-2 chỉ mở cổng cho module `attempt`.

**Sản phẩm:**

- `exam/spi/PaperSolverPort.generate(examId, versionNo, partCodes, selections, seed, excludeQuestionIds)` (LT 3):
  - dựng bài toán từ **bản chụp phiên bản**: `parts`, `subjectPool`, `config.combinations` cho phần có mã;
  - dựng ứng viên qua `QuestionSourcePort.findCandidates`;
  - gọi `solvePartExcluding`, hoặc `solvePick` cho HSA Khoa học (có bỏ câu theo `TrimPolicy`).

  Trả `(questionId, versionNo)` theo môn, nhóm, chùm, kèm câu đã bỏ. Không xếp thứ tự: AT-1 gọi `PaperOrdering`.
- `selections` nhận cả định hướng Tin học như một phương án `BRANCH` thường (2B K2). Không có `generateBranches`.
- `SolverLimits` riêng cho sinh đề, hạn 30 giây (LT F5), cấu hình ở `thi360.exam.solver.generate.*`.
- Test `perf`: HSA FULL phần Toán và phần Văn với 2.000 ứng viên mỗi khối; ghi thời gian vào báo cáo lane.
- Sửa BE spec mục 5.4 (cổng mới).

**Nghiệm thu:**

- Cùng hạt giống, cùng dữ liệu thì ra cùng kết quả.
- HSA Khoa học, 3 môn tự nhiên: môn nhận 16 câu theo hạt giống, đúng 16 câu, có câu đã bỏ.
- QDA QĐ02 từ bản chụp: Hóa trước, Sinh sau. Thêm mã vào Global List sau khi công bố không đổi kết quả của phiên bản cũ.
- Danh sách câu loại có hiệu lực ở cả `solvePartExcluding` và `solvePick`.
- Tin học SPT V1 (22 câu) và THPTQG (28 câu): mỗi định hướng sinh đủ câu của đường đó.
- Không sinh được thì lỗi, không bao giờ trả đề thiếu. Vượt hạn thì lỗi `GENERATION_TIMEOUT`.
- Có số đo `perf` trong báo cáo. Vượt 30 giây thì báo Advisor (Phần E), không tự đổi hạn.

## DS · Bộ dữ liệu bài đã công bố

**Spec:** `docs/specs/2026-11-17-de-test-gia-dinh.md`; BE 5.2; LT 13; TM 6. **Phụ thuộc mềm:** EX-2.

**Sản phẩm:**

- Bộ dựng dữ liệu đi qua service thật: tạo câu trong ngân hàng, tạo bài từ mẫu của đợt 2B, điền số liệu theo `de-test-gia-dinh` (bản QA của đợt 2B đã cập nhật), điền thông tin bán, kiểm tra, công bố. Không chèn thẳng bằng SQL (`exam_version.check_run_id` là `NOT NULL`, và dữ liệu phải đi đúng đường thật).
- Đủ bài đã công bố cho: HSA FULL (đủ hai nhánh phần 3, đủ 5 môn Khoa học, ma trận 17 câu), HSA theo phần, HSA theo môn, QDA FULL (ma trận theo môn, QĐ01–QĐ05), QDA theo phần Khoa học, BCA FULL (có câu tự luận; là bài duy nhất dùng cho ca chấm tay, vì Văn SP2E V1 bỏ phần Viết theo 2B K7), BCA theo phần trắc nghiệm tự chọn, V-ACT FULL, SP2E Toán (đề minh họa cũ và 2026), SP2E 3 môn theo môn (cho bộ đề của CM-6; Văn là bản mặc định 30 câu, không có tự luận, 2B K15), SPT Tin học V1 (hai định hướng), THPTQG Tin học. Thêm một bài có voucher 100% dùng được và một bài đã ngừng bán.
- Bài dùng mẫu của đợt 2B (có `tagCodes`); câu trong ngân hàng của bộ dữ liệu gắn nhãn dạng câu và loại văn bản, lấy từ dữ liệu câu hỏi mẫu của 2B (BE-10).
- Ngân hàng câu đủ để mỗi bài sinh được ít nhất 3 lượt không trùng câu. Mỗi bài có một bản "thu nhỏ" (ít câu, thời lượng ngắn) cho E2E.
- Dùng được ở ba nơi:
  - test tích hợp: lớp hỗ trợ test gọi bộ dựng;
  - profile `dev`: chạy khi khởi động nếu chưa có;
  - profile `e2e`: chạy khi khởi động.
- Danh sách mã bài, mã sản phẩm và tài khoản mẫu ghi vào một tệp hằng số dùng chung cho test và E2E.

**Nghiệm thu:**

- DB mới với profile `dev`: các bài trên hiện ở trạng thái đã công bố trong web admin, kiểm tra cấu hình đạt.
- Chạy lại bộ dựng không tạo trùng.
- Test tích hợp gọi bộ dựng chạy dưới 60 giây.

## VP-1 · Thử VNPAY sandbox trước khi làm cổng thật

**Spec:** TM 8.4, E26, Q12, Q14. **Cần G2.**

**Sản phẩm:**

- Script nhỏ (Python, đặt trong thư mục đợt chạy, không vào repo) ký và gọi sandbox, đọc khóa từ biến môi trường:
  1. Tạo URL `pay` cho 2 giao dịch, trả bằng thẻ thử của sandbox: một thành công, một hủy. Ghi tham số trang quay lại.
  2. `querydr` cho cả hai.
  3. `refund` toàn phần và một phần cho giao dịch thành công, sau đó `querydr` lại.
  4. Thử số tiền 1.000, 4.999, 5.000 đồng để chốt mức tối thiểu (E26).
- Tệp bằng chứng trong `dot-3/vnpay/` (đã che khóa và chữ ký): `pay-params.json`, `return-params.json`, `querydr.json`, `refund.json`, `min-amount.md`.
- Trong repo (PR của VP-1):
  - `backend/src/test/resources/vnpay/vectors.json`: vector chữ ký cho `pay`, `querydr`, `refund`, cả yêu cầu và phản hồi. Vector dùng một khóa thử tự đặt, không phải khóa sandbox; chuỗi ký và quy tắc mã hóa lấy theo kết quả thật;
  - sửa TM 8.4, E26, Q12, Q14 theo kết quả đo, ghi ngày và nguồn.

**Nghiệm thu:**

- Có đủ năm tệp bằng chứng; tìm khóa sandbox trong thư mục bằng chứng và trong diff ra 0 kết quả.
- TM ghi rõ: quy tắc mã hóa URL của chuỗi ký `pay`, hàm băm của `querydr` và `refund`, mức tối thiểu, nghĩa của `00` khi hoàn (theo những gì sandbox cho thấy).

## ID-1 · Đăng ký, xác minh email, đổi email

**Spec:** TM 5.1, 5.2, 5.3, E15, E17, E18, E19, C9, Q13.

**Sản phẩm:**

- `POST /api/v1/auth/register` (công khai). Tạo tài khoản và user Keycloak, có bước bù. Tạo phiên, xếp thư OTP. Ghi `preferred_locale`.
- Cột `terms_version`, `terms_accepted_at`, `pending_email`.
- Bảng `identity.email_code`, mã HMAC (pepper của C-0). API `/me/email/verification`, `/verify`, `/change`, `/change/confirm`.
- Thư `EMAIL_CHANGED_NOTICE` tới địa chỉ cũ.
- Đổi email đồng bộ sang Keycloak; lỗi thì rollback, trả 503.
- `THI360_DEV_EMAIL_CODE` chỉ ở profile `dev` (A6).
- Giới hạn tần suất theo E15, E19.

**Nghiệm thu:**

- 20 yêu cầu đăng ký cùng tên đăng nhập chạy đồng thời: một thành công (AC-06.01).
- Keycloak lỗi thì không còn tài khoản dở dang.
- OTP: đúng, sai (còn n lần), hết hạn, sai quá 5 lần, gửi lại trước 60 giây.
- Đổi email: email cũ vẫn dùng để khôi phục tới khi xác minh xong (AC-06.05). Email trùng thì 409.
- Đặt `THI360_DEV_EMAIL_CODE` ở profile khác `dev` thì ứng dụng không khởi động.

## ID-2 · Quên và đặt lại mật khẩu, hồ sơ, ảnh đại diện

**Spec:** TM 5.4, 5.5, E16, E20, E27.

**Sản phẩm:**

- Bảng `identity.password_reset_token`. API `/auth/password/forgot`, `/auth/password/reset` (công khai):
  - cùng phản hồi cho mọi email;
  - việc tra tài khoản và xếp thư chạy bất đồng bộ, sau khi đã trả phản hồi;
  - chỉ áp cho realm `thi-360`;
  - đặt lại thì thu hồi mọi phiên;
  - không mở khóa tài khoản bị khóa.
- `GET/PATCH /me/profile`, `PUT/DELETE /me/avatar`, `GET /api/v1/avatars/{assetId}`.
- Bảng `identity.user_avatar`, bucket `thi360-avatars`, ảnh PNG 256px (B7). Sửa TM E20 trong PR.

**Nghiệm thu:**

- `forgot` cho email có và không có tài khoản: cùng mã, cùng thân. Test cấu trúc (mock) cho thấy controller trả phản hồi trước khi tra tài khoản, cả hai trường hợp đi cùng một đường mã tới lúc trả.
- Mã dùng lại hoặc hết hạn thì 422. Yêu cầu mới làm mã cũ mất hiệu lực.
- Sau khi đặt lại, phiên cũ không dùng được.
- Tài khoản realm `master` không nhận thư.
- Ảnh 6 MB thì 413. Ảnh giả đuôi thì 422. Ảnh 8.000 × 8.000 px thì 422.

## CM-1 · Bảng chiếu sản phẩm, API công khai

**Spec:** TM 3, 10, E2–E4, E11, E24. **Phụ thuộc mềm:** DS.

**Sản phẩm:**

- Bảng `commerce.storefront_product`. Bộ nhận sự kiện `ExamPublished` và `ExamDiscontinued` ghi lại dòng (idempotent, không ghi lùi `version_no`).
- `POST /api/v1/admin/storefront/rebuild`.
- API công khai `/api/v1/public/storefront/{filters,products,products/{code},covers/{assetId}}`:
  - `Cache-Control`, `ETag`;
  - giới hạn 120 yêu cầu mỗi phút theo IP;
  - 404 `PRODUCT_NOT_FOUND`, 410 `PRODUCT_DISCONTINUED`.
- Interface `PriceCalculator` với bản tạm trả giá gốc (B1); CM-2 thay bằng bản thật.

**Nghiệm thu:**

- Bài nháp không lộ; 404 giống mã không có.
- Lọc theo môn trả cả đề FULL chứa môn đó.
- `ForbiddenKeysAssert` trên mọi phản hồi.
- Gọi không có cookie thì 200.
- Sự kiện lặp không đổi kết quả.
- Với dữ liệu của DS, `products` trả đủ các bài đang bán, không có bài ngừng bán.

## CM-2 · Khuyến mãi, voucher, tính giá

**Spec:** TM 7, E5–E9, E23, E26, 10.2 (`price-quote`).

**Sản phẩm:**

- Các bảng `promotion`, `promotion_scope_item`, `promotion_audience`, `promotion_redemption` (TM 7.1). `promotion_redemption.order_id` chưa có khóa ngoại (B1).
- Tạo bảng theo hình dạng đã có bộ đề (TM 7.1): `scope` có giá trị `BUNDLE`, `promotion_scope_item.bundle_id` (chưa có khóa ngoại; CM-6 thêm). Tính giá và API cho phạm vi `BUNDLE` để CM-6 làm.
- `PriceCalculator` thật, theo TM 7.2 bước 1–7.
- Interface `PurchaseHistory` (E8) với bản tạm "chưa mua lần nào" (B1).
- `reserve`, `confirm`, `release` lượt dùng, có khóa dòng và `UPDATE` có điều kiện; `over_limit_count`.
- Admin API `/promotions/**` (TM 7.3).
- `POST /api/v1/me/price-quote`, kèm giới hạn chống dò mã.
- Thẻ trên API công khai hiện giá khuyến mãi tự động.

**Nghiệm thu:**

- Bảng ca tính giá: %, trần, số tiền, làm tròn, E5, E6 cả ba cách áp, mức tối thiểu E26.
- 20 yêu cầu đồng thời với voucher còn 1 lượt: chỉ 1 lượt giữ.
- Mã sai, chưa bắt đầu, ngừng, đã xóa đều trả `VOUCHER_INVALID`.
- Sửa voucher đã có lượt dùng thì chỉ sửa được các trường cho phép.
- Voucher "tài khoản mới" với bản tạm `PurchaseHistory`: áp được; có test ghi rõ đây là hành vi tạm.

## CM-4a · Bảng quyền dùng, `EntitlementPort`

**Spec:** TM 8.5 (bảng, `EntitlementPort`), E21, E25, C12, C13; LT 5.1 (dữ liệu chuẩn bị); plan B1.

**Sản phẩm:**

- Bảng `entitlement` (`NULLS NOT DISTINCT`), `entitlement_grant` (`kind`, `option_codes`; `order_id` chưa có khóa ngoại, B1), `entitlement_usage`.
- `entitlement_grant` có cột `exam_id`; ràng buộc duy nhất là `(order_id, exam_id)` ngay từ đầu (TM 8.5), không phải `order_id`.
- Bản cài `EntitlementPort` theo hợp đồng của C-0. Mọi hàm idempotent theo `attemptId`; lần cấp dùng theo thứ tự cũ nhất trước (E21).
- Service nội bộ `EntitlementGrantService`: `grantForOrder` (CM-3a gọi), `revokeUnused(grantId)` (CM-5 gọi).
- `AccountDeletionGuard`: tài khoản có quyền dùng thì không xóa được.
- Phần `entitlements` của `StudentLearningProvider`.

**Nghiệm thu:**

- Hai lần cấp đồng thời cho cùng (tài khoản, bài): một dòng `entitlement`.
- `reserve`, `confirmUse`, `release` lặp lại không đổi số.
- `release` trên lần cấp đã hoàn hết thì chuyển sang thu hồi.
- Lần cấp cũ nhất được dùng trước; `describe` trả đúng lần cấp đó.
- `compensate` lần hai cho cùng lượt bị từ chối; lần cấp đã hoàn hết thì 409 `GRANT_REFUNDED`.

## CM-4b · `exam-access`, `/me/exams`

**Spec:** TM 10.2 (`exam-access`), 12, C13; LT 3 (`AttemptStatusProvider`).

**Sản phẩm:**

- Interface `PendingOrderLookup` với bản tạm "không có" (B1).
- `POST /me/exam-access/query`: thứ tự trạng thái và `hasPendingFulfillment` theo TM 10.2, ánh xạ mã sản phẩm qua bảng chiếu.
- `GET /me/exams`: thẻ PBI-09, đọc lượt đang mở qua `AttemptStatusProvider`, số câu và thời lượng qua `PublishedExamPort`, trạng thái bán qua bảng chiếu.

**Nghiệm thu:**

- Đủ năm trạng thái `exam-access`. `CHECKING` thử bằng một bản `PendingOrderLookup` giả trong test. Lượt `STARTING` cho `IN_PROGRESS` kèm `activeAttemptId` (TM 10.2).
- `/me/exams`: bài ngừng bán còn lượt vẫn có Bắt đầu, không có Mua thêm lượt; hết lượt và còn bán thì có Mua lại.
- Tài khoản `MANAGE` gọi hai API thì 403.

## CM-3a · Đơn hàng, cổng giả lập, IPN, cấp quyền

**Spec:** TM 8.1–8.4, 8.5 (thủ tục cấp quyền), 8.6, E12–E14, E25, E26; TM 14 (ca VNPAY).

**Sản phẩm:**

- Bảng `purchase_order`, `payment`, `payment_event`, `order_history`. Trạng thái theo TM 8.1, gồm `REFUNDED`. Thêm khóa ngoại cho `promotion_redemption.order_id`, `entitlement_grant.order_id` (B1).
- Tạo bảng theo hình dạng đã có bộ đề (TM 8.1, 16.3), để CM-6 không phải đổi lại:
  - `purchase_order` có `product_type` (chỉ `EXAM` được dùng tới CM-6), `bundle_id`, `bundle_version_no` (để trống);
  - bảng `purchase_order_item`; đơn mua một bài ghi một dòng; cấp quyền chạy theo từng dòng (TM 8.5);
  - chỉ mục đơn chờ duy nhất theo `(user_id, product_code)`.
- `/me/orders` (tạo, xem, danh sách, `/pay`, `/cancel`). Kiểm `access = EXAM`, tài khoản, phiên bản, giá; E13 dùng lại hoặc thay đơn chờ.
- Interface `VnpayGateway` (`pay`, `verify`, `queryDr`, `refund`) và bản giả lập đủ bốn hàm. Trang `/mock-vnpay` gọi IPN có chữ ký. `THI360_VNPAY_MODE` (`MOCK`, `SANDBOX`, `PRODUCTION`); `MOCK` chỉ được bật cùng profile `dev` hoặc `e2e`.
- IPN `GET /api/v1/public/payments/vnpay/ipn`: luôn HTTP 200, `RspCode` theo bảng, miễn giới hạn tần suất, lọc IP tùy chọn (B6).
- Trang quay lại `GET /api/v1/public/payments/vnpay/return`.
- Tiền về khi đơn đã `REPLACED` hoặc `CANCELLED` (TM 8.6); thu trùng (`is_duplicate`).
- Đơn 0 đồng (`FREE`).
- `FulfillmentService` theo B2, gọi `EntitlementGrantService.grantForOrder`; worker cấp quyền lại; thư `ORDER_FULFILLED`.
- Thay bản tạm `PurchaseHistory`, `PendingOrderLookup` bằng bản thật. `AccountDeletionGuard`: thêm "có đơn".
- `.env.example` có tên các biến `THI360_VNPAY_*`.

**Nghiệm thu:**

- IPN: sai chữ ký trả `97`; sai số tiền trả `04`; gửi lặp chỉ cấp một quyền (AC-07); lỗi DB trả `99`.
- Trang quay lại giả mạo không cấp quyền.
- Tiền về sau `REPLACED`, `CANCELLED`: đơn `PAID`, cấp quyền, `needs_review`. Thu trùng: `is_duplicate`, không cấp thêm.
- Đổi tài khoản giữa chừng thì 409. Giá đổi thì 409 kèm giá mới. Ngừng bán thì 410.
- Đơn 0 đồng cấp quyền mà không gọi VNPAY. Cấp quyền lỗi vẫn giữ `PAID`; worker cấp lại được.
- `exam-access` trả `CHECKING` cho đơn đã thu tiền chưa cấp quyền.

## CM-3b · Cổng VNPAY thật, tra cứu, hết hạn

**Spec:** TM 8.4, 8.6 (đơn hết hạn), E26, Q12, Q14. **Phụ thuộc mềm:** VP-1.

**Sản phẩm:**

- Bản cài thật của `VnpayGateway`: ký `pay`; ký và gọi `querydr`, `refund` theo các chuỗi trường ở TM 8.4. Mọi điểm TM ghi "cần kiểm trên sandbox" đặt sau cấu hình hoặc một hàm riêng.
- Worker đơn hết hạn: tìm đơn chờ quá `expires_at`, gọi `queryDr` cho các lần thanh toán còn mở, chưa trả tiền thì `EXPIRED` và trả lượt voucher.
- Bảng ánh xạ kết quả `querydr` sang trạng thái thanh toán; mã chưa rõ thành `UNKNOWN`.
- Tiền về sau `EXPIRED` (TM 8.6).

**Nghiệm thu:**

- Vector chữ ký: dùng `vectors.json` của VP-1 nếu đã có; chưa có (thiếu G2) thì lấy ví dụ trong tài liệu và ghi rõ trong PR.
- Mã chưa rõ thành `UNKNOWN`, không phải `FAILED`.
- Đơn hết hạn: `EXPIRED`, voucher được trả; tiền về sau đó vẫn cấp quyền và `needs_review`.
- `THI360_VNPAY_MODE=SANDBOX` khởi động được khi có đủ biến; thiếu biến thì lỗi khởi động rõ ràng.

## CM-5 · Quản lý đơn, hoàn tiền, tiếp tục cấp quyền, đối soát

**Spec:** TM 9, 8.5 bước 1, 8.6, C14, C15.

**Sản phẩm:**

- Admin API: danh sách và chi tiết đơn, người mua theo bài, dải "Cần xử lý" (TM 9.1–9.3).
- Bảng `refund`. Hoàn qua `VnpayGateway.refund` và ghi tay theo điều kiện TM 9.4 bước 5. Tra cứu lần hoàn `UNKNOWN`; đánh dấu `FAILED` kèm chứng từ.
- Ảnh hưởng tới quyền dùng (`revokeUnused`, `REFUNDED`). Thư `REFUND_DONE`.
- `POST /orders/{code}/fulfill` (`ORDER.RECOVER`) theo TM 9.5.
- Báo cáo đối soát 02:00 (B5).

**Nghiệm thu:**

- Hoàn vượt số còn hoàn thì 409. Bấm lặp chỉ ra một khoản.
- Lần hoàn `UNKNOWN` thì cấm ghi tay.
- Hoàn toàn bộ: thu hồi lượt chưa dùng, giữ lượt đang làm. Hoàn khoản trùng: giữ quyền. Hoàn một phần: không thu hồi.
- Đơn `PAID` chưa cấp mà đã hoàn hết thì thành `REFUNDED`, không còn "Đang kiểm tra".
- Hai admin bấm tiếp tục cấp quyền cùng lúc: một lần cấp.
- Tổng người mua là số tài khoản khác nhau (AC-08).
- Hai worker đối soát cùng lúc: chỉ một chạy.

## CM-6 · Bộ đề

**Spec:** TM C17, 16; 2B K3. **Phụ thuộc mềm:** CM-5 (hoàn tiền đơn bộ).

**Sản phẩm:**

- Bảng `bundle`, `bundle_item`, `bundle_version`. API admin theo TM 16.4, nhật ký `BUNDLE_*`.
- Công bố, ngừng bán bộ; bộ thành `UNAVAILABLE` khi một bài trong bộ ngừng bán (nghe `ExamDiscontinued`).
- Bảng chiếu: `product_type`, dòng `BUNDLE`, `ProductDetail.items[]`, bộ lọc "Bộ đề".
- Đơn `product_type = BUNDLE`: một dòng `purchase_order_item` cho mỗi bài, chụp từ `bundle_version`. `purchase_order_item`, chỉ mục đơn chờ và ràng buộc `entitlement_grant (order_id, exam_id)` đã có từ CM-3a, CM-4a.
- Cấp quyền mọi bài của đơn bộ trong một transaction. Khóa ngoại `bundle_id` cho `purchase_order`, `promotion_scope_item`.
- Hoàn toàn bộ đơn bộ thu hồi lượt chưa dùng ở mọi bài (sau khi CM-5 merge).
- Phạm vi khuyến mãi `BUNDLE`.
- `exam-access/query` với mã bộ; `GET /me/exams` có `bundles[]` của thẻ.
- DS thêm một bộ SP2E 3 môn ở profile `dev`, `e2e`.

**Nghiệm thu:** các ca ở TM 16.5, cộng:

- Đơn bài lẻ vẫn cấp quyền, hoàn tiền như trước (test hồi quy của CM-3a, CM-5 xanh).
- `ForbiddenKeysAssert` trên `ProductDetail` của bộ.

## VP-2 · Luồng VNPAY sandbox thật có IPN

**Spec:** TM 8.4, 9.4. **Cần G2** (G3 đã có).

**Sản phẩm:**

- Không cần giữ dữ liệu cũ của demo (G3): được xóa DB demo và dựng lại bằng dữ liệu DS. Sau VP-2 demo giữ `integration/dot-3`.
- Đưa `integration/dot-3` lên bản demo với `THI360_VNPAY_MODE=SANDBOX`, khóa trong `.env` của demo; thêm khối `location` nginx của G3.
- Chạy một lần mua thật trên `https://thi360.zone17.click` bằng thẻ thử: IPN tới, đơn `FULFILLED`, thẻ hiện ở Bài thi của tôi. Một lần hủy. Một lần hoàn toàn phần từ web admin.
- Bằng chứng (đã che) trong `dot-3/vnpay/`: nhật ký `payment_event`, ảnh chụp các màn.
- Sửa TM, `VnpayGateway` nếu khác kết quả của VP-1.

**Nghiệm thu:** đủ bằng chứng ba luồng; IPN có trong `payment_event` với chữ ký đúng; không có khóa trong bằng chứng.

## AT-1 · Lượt thi: bắt đầu, sinh đề chạy nền, đảo, chuẩn bị, trạng thái

**Spec:** LT 3, 4, 5, 6.2, 7.3, 7.4, L7, L8, F2–F6, F13, F22; TM E25; 2B 11.

**Sản phẩm:**

- Bảng `attempt` (gồm `abort_reason`), `attempt_part`, `attempt_item`, `attempt_event` (LT 4). `essay_grade` để AT-5 làm.
- `GET /me/entitlements/{id}/prepare` dùng `EntitlementPort.describe`.
- `POST /me/attempts` theo LT 5.2:
  - transaction 1: kiểm, `reserve`, tạo `STARTING`, trả 202;
  - `GenerationExecutor` (B2): sinh đề ở nền; transaction 2 lưu đề, đặt `IN_PROGRESS`, mở phần đầu. Chỉ lưu khi lượt còn `STARTING` và `generation_token` còn khớp; việc chờ trong hàng quá 60 giây thì bỏ;
  - lỗi, quá hạn, hàng đầy: `ABORTED` đúng `abort_reason`, `release`.
- Chọn phiên bản theo F2. Chọn phương án trong phần giao giữa phạm vi đã mua và phương án còn phục vụ.
- Thứ tự câu và đảo phương án (gồm cột phải của Ghép đôi) theo F22 bằng `exam/spi/PaperOrdering` của đợt 2B (2B K12); lưu `optionOrder` trả về vào `attempt_item.option_order`. Ghi sổ đã dùng qua `QuestionSourcePort.recordUsage`.
- `preChoice` nhận định hướng Tin học (LT 6.2).
- `GET /me/attempts/{id}` (`AttemptState`, gồm dạng rút gọn cho `STARTING`, `ABORTED`). Nội dung câu bỏ khóa cấm; không trả câu của phần chưa mở.
- `AttemptGuard`: kiểm sở hữu, phần đang mở, hạn. AT-2a, AT-2b, AT-3 dùng chung.
- Cài `AttemptStatusProvider`, `ActiveAttemptChecker`, phần `attempts` của `StudentLearningProvider`.
- Worker đưa lượt `STARTING` quá 2 phút về `ABORTED` (`STALE_STARTING`).

**Nghiệm thu** (dùng dữ liệu DS):

- Hai yêu cầu bắt đầu đồng thời: một lượt. Đã có lượt mở (`STARTING` hoặc `IN_PROGRESS`) thì trả lượt đó.
- Trả 202 rồi thành `IN_PROGRESS`; `started_at` là lúc lưu đề xong, không phải lúc bấm.
- Hết lượt thì 409. Sinh lỗi thì `ABORTED` và lượt được trả. Hàng đầy (cấu hình 1 luồng, hàng 0) thì `GENERATION_BUSY`.
- Worker đã đưa lượt về `ABORTED` rồi việc sinh mới xong: kết quả bị bỏ, không có `attempt_item`, lượt chỉ được trả một lần.
- `prepare` khi lượt đang `STARTING` trả lượt đó; `AttemptStatusProvider` trả cả lượt `STARTING`.
- Bài ngừng bán vẫn bắt đầu được (F3).
- Cùng hạt giống thì cùng thứ tự. Chùm liền khối. Nhóm giữ thứ tự theo mẫu; nhóm `shuffleUnits = false` giữ thứ tự câu.
- Tin học SPT V1 và THPTQG: chọn định hướng ở `preChoice`; đề chỉ có câu của nhánh đã chọn; mẫu số 22 và 28.
- Bài theo môn của QDA vào thẳng, không cần `preChoice`.
- `ForbiddenKeysAssert` trên `prepare`, bắt đầu, `AttemptState`.

## AT-2a · Chuyển phần tuần tự, nộp, đóng lượt, worker hạn giờ

**Spec:** LT 6 (trừ 6.1, 6.2), 6.3, L7, F7, F9, F11, F19; TM 8.5 (`confirmUse`).

**Sản phẩm:**

- `AttemptLifecycleService`:
  - mở phần kế tiếp khi phần trước đóng, ở mọi đề (L7, F7); đồng hồ từng phần hoặc chung theo `timing`;
  - `finish` (mọi phần trừ phần cuối), `submit` (`PART_NOT_LAST`);
  - quy trình đóng lượt dùng chung (LT 6.3) cho `SUBMITTED`, `TIME_UP`, `CHOICE_TIMEOUT`, `FAULT_CLOSED`; `confirmUse`; phát `AttemptFinished` qua outbox;
  - một điểm mở để AT-2b chèn bước "chờ chọn phần 3" (giao diện nội bộ, bản mặc định không làm gì).
- Worker đóng phần và bài quá hạn (`closed_at = deadline_at`).

**Nghiệm thu:**

- HSA, QDA tới hết phần 2 (phần 3 để AT-2b); BCA CA2 81 câu; V-ACT 120 câu, ba phần lần lượt; SP2E 2025 và 2026.
- BCA hết giờ chung khi đang ở phần 2: lượt kết thúc, phần 3 `SKIPPED`.
- Phần đã đóng không mở lại.
- Nộp trùng lúc hết giờ: một lần đóng.
- Worker đóng đúng hạn khi không có thiết bị (tua giờ bằng `MutableClock`).

## AT-2b · Chọn phần 3, thử lại

**Spec:** LT 6.1, L4, L8, F4, F20 (phần sinh lỗi). Định hướng Tin học đã chuyển sang AT-1 (LT 6.2 sửa theo 2B K2).

**Sản phẩm:**

- `AttemptChoiceService`:
  - `AWAITING_CHOICE`, hạn chờ 15 phút; `choice` trả 202 rồi sinh phần ở nền;
  - `retry`, `GENERATION_FAILED`, `PART_GENERATING`; hàng đầy thì phần `GENERATION_FAILED` với `GENERATION_BUSY`, lượt không bị hủy;
  - worker thử lại bằng `GenerationExecutor` trong tiến trình worker (B2) và đưa phần kẹt `GENERATING` về lỗi; kết quả đến muộn bị bỏ theo `generation_token`;
  - quá hạn chọn: đóng lượt `CHOICE_TIMEOUT` qua `AttemptLifecycleService`;
  - HSA Khoa học: lưu cách chia 17–17–16 và hạt giống; môn 16 câu nhận câu đã bỏ từ `PaperSolverPort`, ghi vào `attempt_event`.
- Các mã lỗi ở LT 6.1.

**Nghiệm thu:**

- HSA FULL qua cả hai nhánh, 10 bộ ba Khoa học, chia 17–17–16 đúng; tải lại vẫn đúng 50 câu.
- QDA QĐ02: Hóa 1–25, Sinh 26–50 theo `config.combinations`.
- Quá hạn chọn thì 0 điểm phần 3. Sinh lỗi sau khi xác nhận thì không bị quá hạn; thử lại giữ lựa chọn, hạt giống, cách chia.

## AT-3 · Lưu bài, phiên sửa, tài nguyên của lượt

**Spec:** LT 7.1, 7.3, F8–F10, F12 (phần máy chủ), F14 (định dạng câu trả lời).

**Sản phẩm:**

- `PUT /me/attempts/{id}/answers`: `seq` theo câu, kiểm `AttemptGuard`, `position`, cờ đánh dấu, giới hạn 20 yêu cầu trong 10 giây.
- Kiểm định dạng câu trả lời theo dạng câu; lọc HTML tự luận, giới hạn 64 KB.
- `EditSessionService`: `POST …/session` (`sessionId`, `takeover`), `heartbeat`, `ATTEMPT_OPEN_ELSEWHERE`, `ATTEMPT_SESSION_REVOKED`.
- `GET /me/attempts/{id}/assets/{assetId}`, chỉ tài nguyên được phép (LT 7.1).
- Sự kiện `attempt_event` cho lưu bị từ chối, tiếp quản.

**Nghiệm thu:** các ca "Lưu", "Phiên" ở LT 13.

- `seq` đến sai thứ tự (AC-11.02); xóa và bỏ chọn; lưu từng ý; gửi lại.
- Phần đã đóng hoặc hết giờ thì bị từ chối.
- Phiên cũ bị từ chối. Phiên cũ im quá 2 phút vẫn phải hỏi.
- Tài nguyên của lời giải và của phần chưa mở thì 404.

## AT-4 · Chấm, kết quả, xem lại, lịch sử

**Spec:** LT 9.1, 10, 11, F14–F16, L3, L5; BE 8. **Phụ thuộc mềm:** AT-2b, AT-3.

**Sản phẩm:**

- Bộ nhận `AttemptFinished` (outbox): chấm từng câu theo BE 8, ghi `earned_points`, `outcome`, `grading_detail`.
- Tổng hợp RAW và LINEAR, phần `SKIPPED`, thành phần V-ACT. Khóa dòng `attempt` khi tổng hợp.
- Trạng thái `PENDING_REVIEW`, `SCORED`, `GRADING_ERROR`.
- `GET /me/attempts/{id}/result`, `/review` (theo `result_view` lấy từ `benefitsOf`), `GET /me/attempts` (lịch sử, bộ lọc).

**Nghiệm thu:**

- Mọi dòng của bảng chấm BE 8 có test.
- Chấm lại ra cùng kết quả sau khi sửa câu nguồn (AC-12.06).
- Lỗi chấm không hiện 0 (AC-12.07).
- Tự luận rỗng là `BLANK`, không vào hàng chờ.
- Phần `SKIPPED`: phần 3 HSA quá hạn chọn (chưa có câu) lấy điểm tối đa theo bản chụp; phần 3 BCA chưa mở khi hết giờ chung (đã có câu) tính theo câu, không cộng hai lần (LT 9.1).
- Phần `SKIPPED` 0 điểm, vẫn tính vào điểm tối đa.
- `SCORE` không có xem lại (403).
- Lịch sử: lượt FULL là một dòng; lọc theo bài; tài khoản khác nhận 404; hai lần làm mở đúng kết quả.

## AT-5 · Hàng chờ chấm tự luận, chấm lại, vận hành lượt

**Spec:** LT 9.2, 12, F17, F18, F20; TM 8.5 (`compensate`).

**Sản phẩm:**

- Bảng `essay_grade`. API `/admin/grading/items/**`: danh sách, chi tiết, nhận, lưu nháp, `finalize`, `reopen`. Chấm ẩn danh; mốc 30 phút tính bằng `last_activity_at`.
- `finalize` tính lại lượt. `reopen` đặt `regrading`.
- `/admin/attempts/**`: danh sách, chi tiết kèm dòng sự kiện; `regrade`; `fault` (đóng `FAULT_CLOSED` qua `AttemptLifecycleService`); `compensate`.

**Nghiệm thu:**

- Hai người chấm cùng câu: 409 `GRADING_CLAIMED`. Quá 30 phút im thì nhận được.
- Chấm xong câu tự luận cuối cùng thì lượt thành `SCORED`.
- Mở lại chấm thì vẫn hiện điểm cũ kèm nhãn "Đang chấm lại".
- Người chấm không thấy tên học viên.
- Cấp bù chỉ cho lượt đã đánh dấu hỏng, một lần.

## FS-1 · Trang giới thiệu, danh mục, chi tiết

**Spec:** TM 11.1–11.3, C7, C8, Q1, Q2; DESIGN.md. **Phụ thuộc mềm:** CM-4b.

**Thiết kế** (`docs/design/dot-3/`, canvas trang 10): `TrangChuDot3`, `ChiTietBaiThiBan`, `BaiThiNgungBan`.

**Sản phẩm:**

- Thay trang `/` bằng 6 khối; danh mục ở `#exams`, bộ lọc trên URL.
- `/exams/[code]` dựng phía máy chủ, có `metadata`; 404 và 410.
- Nút theo `exam-access`.
- `proxy.ts` và thanh đầu trang theo B8 (đủ cho cả đợt).
- Câu hỏi thường gặp và hỗ trợ trong file dịch, `NEXT_PUBLIC_SUPPORT_*`.

**Nghiệm thu:**

- Khách xem danh mục thật (dữ liệu DS), lọc, phân trang, mở chi tiết.
- Bấm Mua thì sang `/login?callbackUrl=/checkout/<code>?v=…&p=…`.
- Không hiện nút Mua trước khi biết quyền; nút theo bảng TM 11.3 (còn lượt thì "Vào bài thi").
- Mã ngừng bán hiện trang 410; mã không có hiện 404.
- Trạng thái rỗng, lỗi, đang tải (khung cố định).

## FS-2 · Đăng ký, xác minh, quên mật khẩu, hồ sơ

**Spec:** TM 5, 11.1. **Phụ thuộc mềm:** FS-1.

**Thiết kế** (`docs/design/dot-3/`, canvas trang 10): `DangKy`, `XacMinhEmail`, `QuenMatKhau`, `DatLaiMatKhau`, `HoSo`; đăng nhập theo `docs/design/dot-1/Main.dc.html`.

**Sản phẩm:** `/register`, `/verify-email`, `/forgot-password`, `/reset-password`, `/account/profile` (ảnh, họ tên, email, số điện thoại). Liên kết trên `/login`. Mọi lối đi đều giữ `callbackUrl`. Bỏ dòng ẩn của các mục này ở thanh đầu trang (B8).

**Nghiệm thu:**

- E2E đăng ký → mã OTP (đọc qua `/api/v1/test/mail`) → quay đúng `callbackUrl`.
- E2E quên → đặt lại → đăng nhập.
- E2E đổi email.
- OTP sai hiện số lần còn lại; ảnh quá lớn hiện lỗi rõ.

## FS-3 · Xác nhận mua, kết quả thanh toán, Bài thi của tôi

**Spec:** TM 8.2, 8.3 (phía web), 8.4 (trang quay lại), 11.3, 12, E22. **Phụ thuộc mềm:** AT-1, FS-2.

**Thiết kế** (`docs/design/dot-3/`, canvas trang 11): `XacNhanMua`, `ThanhToanThanhCong`, `ThanhToanDangKiemTra`, `ThanhToanThatBai`, `BaiThiCuaToi`, `DonCuaToi`.

**Sản phẩm:**

- `/checkout/[code]`: đã có quyền thì hiện "Bạn đang có N lượt"; ô voucher; giá đổi; đổi tài khoản.
- `/checkout/result`: công khai, hỏi lại trạng thái.
- `/home`: thẻ "Bài đã mua", "Đang kiểm tra", đủ các nút ở TM 12. Nút Bắt đầu dẫn tới `/prepare/[entitlementId]` (FS-5).
- `/account/orders`.

**Nghiệm thu:**

- E2E mua bằng cổng giả lập: thành công, thất bại, không trả lời.
- Voucher 100%.
- Đổi tài khoản giữa chừng.
- Trang quay lại mở ở trình duyệt chưa đăng nhập.
- `/home` lỗi tải không hiện "Bạn chưa có bài thi nào".

## FS-4 · Thành phần câu hỏi (`packages/ui`)

**Spec:** LT 4 (định dạng câu trả lời), 8, F14, F22; BE 4.3 (phần thân câu hỏi).

**Thiết kế** (`docs/design/dot-3/`, canvas trang 13, 06, 09): dạng mới `ThiTraLoiNgan`, `ThiDienKhuyet`, `ThiGhepDoi`, `ThiTuLuan`, `ThiMobileGhepDoi`; chế độ `review` `CauHoiCheDoXemLai`; dạng cũ chỉ lấy phần thân câu ở `CauHoi*Thi`, `ThiMobile*` (Kéo thả đã bỏ; bỏ dòng "dấu phẩy hoặc dấu chấm đều được" của `CauHoiDienDapAnThi`).

**Sản phẩm:**

- Chép `RichTextView` và KaTeX vào `packages/ui`. Không sửa `apps/web-admin` (B8); FA-1 đổi import sau G0b.
- Thành phần cho 8 dạng câu, mỗi dạng có chế độ `answer` (làm bài) và `review` (chỉ đọc, hiện đáp án, điểm từng ý khi có).
- Ngữ liệu chùm; KaTeX; chữ cái phương án theo `option_order`.
- Dùng được bằng bàn phím, có nhãn cho trình đọc màn hình.
- Trang thử trong web-student, chỉ có khi chạy dev.

**Nghiệm thu:**

- Test vitest cho từng dạng: đổi, bỏ chọn, từng ý Đúng/Sai, ô Điền khuyết, cặp Ghép đôi.
- Ảnh chụp sáng và tối, 390px và 1280px.
- Diff không có tệp nào trong `apps/web-admin`.

## FS-5 · Chuẩn bị, chờ sinh đề, màn làm bài

**Spec:** LT 5.1, 5.2 (bước 5), 6, 7.2, 7.4, 8, L7, L8, F10, F12.

**Thiết kế** (`docs/design/dot-3/`, canvas trang 12): `ChuanBiLamBai`, `DangChuanBiDe`, `LamBaiTheoPhan`, `LamBaiMobileTheoPhan`, `HoanThanhPhan`, `ChonPhan3`, `NopBaiXacNhan`, `TiepQuanLuot`. Bản vẽ trước đợt 2B: `ChuanBiLamBai` chưa có chọn định hướng; `ChonPhan3`, `NopBaiXacNhan` còn ô giữ chỗ và cảnh báo định hướng. Theo đặc tả (LT 6.2), không theo các chỗ đó.

**Sản phẩm:**

- `/prepare/[entitlementId]`; màn "Đang chuẩn bị đề" hỏi lại trạng thái (LT 5.2 bước 5); lỗi thì nút Thử lại với khóa mới. Mở lại trang khi lượt đang `STARTING` thì vào thẳng màn chờ, không gửi yêu cầu bắt đầu mới.
- `/attempts/[id]` toàn màn hình; phần làm lần lượt, nút "Hoàn thành phần" ở mọi phần trừ phần cuối (L7).
- Đồng hồ theo `serverNow`.
- Hàng chờ lưu: gửi theo dạng câu, IndexedDB, thử lại, trạng thái Đang lưu / Đã lưu / Chưa lưu.
- Màn chuẩn bị có chọn định hướng Tin học (Khoa học máy tính hoặc Tin học ứng dụng), như chọn mã BCA; hiện số câu, thời lượng, điểm tối đa của đường đã chọn.
- Chọn phần 3 (chờ sinh ở nền như trên), đếm "Đã chọn n/3"; QDA hiện mã kèm tên môn theo thứ tự.
- Nộp bài, "Chưa xác nhận nộp bài".
- Tiếp quản, `BroadcastChannel` giữa các tab, cảnh báo khi rời trang.

**Nghiệm thu:**

- E2E HSA FULL thu nhỏ (DS) có tua giờ: hai phần đầu, chọn phần 3, nộp.
- E2E BCA thu nhỏ: hết giờ chung khi đang ở phần 2.
- Mất mạng rồi nối lại.
- Hai ngữ cảnh trình duyệt thì tiếp quản; hai tab cùng trình duyệt thì tab sau chỉ xem.
- Chỉnh giờ máy không đổi thời gian còn lại.
- Gõ tự luận với CPU chậm 4 lần không trễ thấy được (đo bằng Playwright, ghi số vào báo cáo).

## FS-6 · Kết quả, xem lại, lịch sử

**Spec:** LT 10, 11.

**Thiết kế** (`docs/design/dot-3/`, canvas trang 14): `KetQuaLuot`, `KetQuaChoCham`, `XemLaiBaiLam`, `LichSuLamBai`.

**Sản phẩm:** `/attempts/[id]/result` (hỏi lại khi đang chấm), `/attempts/[id]/review`, `/history` (bộ lọc trên URL). Bỏ dòng ẩn của mục Lịch sử ở thanh đầu trang.

**Nghiệm thu:**

- V-ACT có nhãn "Điểm luyện tập".
- Chờ chấm thì không hiện điểm tổng; điểm 0 chỉ hiện khi đã chấm.
- Lượt FULL là một dòng; phần `SKIPPED` ghi lý do.
- `SCORE` không có nút Xem lại; gọi thẳng URL xem lại thì hiện thông báo, không lỗi trắng.
- Lịch sử rỗng và lỗi tải hiện đúng trạng thái.

## FA-1 · Tab Thông tin bán, cảnh báo chấm tay, menu

**Spec:** TM 6.4; LT 9.3.

**Thiết kế** (`docs/design/dot-3/`, canvas trang 15): `BaiThiThongTinBan`, `BaiThiKiemTraThuongMai`; menu theo các màn admin của trang 15–16.

**Sản phẩm:**

- Tab giữa Cấu trúc và Kiểm tra: xem trước thẻ, dải "Giá đang bán … sẽ đổi khi công bố", tải ảnh bìa.
- Nhóm "Thông tin bán" ở tab Kiểm tra.
- Cảnh báo "Có câu chấm tay" (`MANUAL_GRADING_REQUIRED`): máy chủ và tab Kiểm tra đã hiện cảnh báo này từ đợt 2B (2B H18, FE-4). Lane này chỉ chỉnh câu chữ và vị trí theo bản vẽ `BaiThiKiemTraThuongMai`, không làm lại.
- Menu của đợt theo B8.
- Đổi import `RichTextView` và KaTeX của web-admin sang `packages/ui`, xóa bản cũ (B8).

**Nghiệm thu:**

- Lưu có `expectedRevision`; 412 thì nạp lại và giữ thông báo.
- web-admin không còn bản `RichTextView` riêng; test và build của web-admin xanh.
- Thiếu quyền sửa bài thì tab chỉ xem.
- Ảnh bìa sai định dạng hiện lỗi rõ.
- Cảnh báo chấm tay hiện đúng phần, môn (kiểm bằng bài BCA FULL của DS); công bố vẫn được.
- Menu ẩn theo quyền.

## FA-2 · Khuyến mãi

**Spec:** TM 7.3.

**Thiết kế** (`docs/design/dot-3/`, canvas trang 15): `KhuyenMai`, `TaoKhuyenMai`, `ChiTietKhuyenMai`.

**Sản phẩm:** `/promotions` hai tab, dải trạng thái, trang tạo và sửa (DESIGN 5.14), trường bị khóa có lý do, thử giá, chi tiết kèm các lần dùng. Bỏ dòng ẩn ở menu.

**Nghiệm thu:**

- Thiếu `PROMOTION.MODIFY` thì chỉ xem.
- Sửa voucher đã dùng: trường bị khóa hiện lý do.
- 412 khi lưu thì nạp lại.
- Danh sách rỗng, lỗi tải.
- E2E tạo voucher; dùng voucher ở FS-3 chạy trong QA.

## FA-3 · Đơn hàng, người mua, hoàn tiền

**Spec:** TM 9.

**Thiết kế** (`docs/design/dot-3/`, canvas trang 16): `DonHang`, `ChiTietDonHang`, `HoanTienDonHang`, `NguoiMua`.

**Sản phẩm:** `/orders` có dải "Cần xử lý" và bộ lọc; chi tiết đơn; `/orders/buyers`; hộp thoại hoàn tiền (API, ghi tay với xác nhận lần hai); nút tiếp tục cấp quyền. Bỏ dòng ẩn ở menu.

**Nghiệm thu:**

- E2E hoàn tiền với cổng giả lập.
- Nút hoàn ẩn khi thiếu `ORDER.REFUND`; nút tiếp tục cấp quyền ẩn khi thiếu `ORDER.RECOVER`.
- Hoàn vượt số còn hoàn hiện lỗi 409 rõ.
- Lần hoàn `UNKNOWN` không có nút ghi tay.
- Danh sách rỗng, lỗi tải.

## FB · Bộ đề trên web

**Spec:** TM 16.4. **Phụ thuộc mềm:** FA-3.

**Thiết kế:** chưa có. Cần vẽ trước khi lane bắt đầu: màn admin "Bộ đề" (danh sách, tạo, sửa), thẻ và trang chi tiết bộ, trang mua bộ, nhóm thẻ theo bộ ở `/home`. Chưa có bản vẽ thì lane không bắt đầu (Phần E).

**Sản phẩm:**

- **Web admin:** mục "Bộ đề" trong nhóm Thương mại; danh sách, tạo, sửa (chọn kỳ thi, tích bài theo môn với số câu, thời lượng, điểm), công bố, ngừng bán. `/orders` và chi tiết đơn hiện các bài của đơn bộ.
- **Web học viên:** thẻ bộ ở danh mục, trang chi tiết bộ (`/exams/[code]` nhận mã bộ) có danh sách bài và quyền lợi từng bài; `/checkout/[code]` cho bộ; `/home` nhóm thẻ theo bộ.

**Nghiệm thu:**

- E2E: admin tạo và công bố bộ SP2E 3 môn → học viên mua bằng cổng giả lập → `/home` có nhóm bộ với 3 thẻ → bắt đầu một môn, các môn khác không đổi.
- Bộ `UNAVAILABLE`: trang chi tiết không cho mua.
- Bốn trạng thái; sáng và tối; vi và en; 390px và 1280px.

## FA-4 · Hàng chờ chấm thật, vận hành lượt, làm thử như học viên

**Spec:** LT 9.2, 12; 2B H17 (R-16, Q-24).

**Thiết kế** (`docs/design/dot-3/`, canvas trang 16): `LuotThiAdmin`, `ChiTietLuotThiAdmin`; `/grading` giữ màn đợt 2.

**Sản phẩm:**

- `/grading` nối API thật; bỏ cờ và mock theo B8.
- `/attempts`, `/attempts/[id]`: dòng sự kiện, nút chấm lại, đánh dấu hỏng, cấp bù. Bỏ dòng ẩn ở menu.
- **Làm thử như học viên** (2B H17): ở tab Xem trước của bài thi, nút "Làm thử" mở đề sinh thử (đã xếp và đảo phương án bằng `PaperOrdering`, như lượt thi thật) bằng thành phần câu hỏi của `packages/ui` (FS-4). Nộp thì gọi `POST /api/v1/admin/exams/{id}/preview/{previewId}/grade` (thêm ở lane này, dùng bộ chấm của AT-4, quyền `EXAM.VIEW`), hiện điểm từng câu, từng ý Đúng/Sai. Không tạo lượt, không tạo kết quả học tập.

**Nghiệm thu:**

- E2E nộp bài có tự luận → chấm → học viên thấy điểm tổng.
- Câu đã có người nhận thì chỉ xem.
- Thiếu `ATTEMPT.COMPENSATE` thì không có nút cấp bù.
- Làm thử đề sinh thử SPT Toán: điểm từng câu đúng quy tắc chấm của câu; không có dòng mới trong `attempt`.

## QA · Nghiệm thu đợt

**Spec:** TM 14, LT 13.

**Sản phẩm:**

- **E2E đầu cuối** (dữ liệu DS, số liệu từ `docs/specs/2026-11-17-de-test-gia-dinh.md`):
  - khách → đăng ký → xác minh → mua (cổng giả lập) → làm HSA FULL thu nhỏ → kết quả → lịch sử;
  - voucher 100% (tạo ở FA-2, dùng ở FS-3);
  - BCA có tự luận → chấm → điểm;
  - hoàn tiền → thu hồi lượt.
- **Kiểm thử bảo mật:**
  - quét `ForbiddenKeysAssert` trên mọi API học viên và công khai;
  - IDOR trên `/me/**`;
  - giới hạn tần suất của endpoint công khai;
  - `/api/v1/test/**` trả 404 khi không có profile `e2e`;
  - không có bí mật trong repo (lệnh ở A7).
- **Tài liệu:**
  - README triển khai: biến môi trường mới, Mailpit, VNPAY, worker, yêu cầu IPN tới thẳng backend (B6);
  - `ARCHITECTURE.md` (module mới);
  - `DESIGN.md` (màn làm bài).
- Cập nhật `TASK.md`, `PROGRESS.md`.

**Nghiệm thu:**

- Lệnh chuẩn và E2E xanh trên đầu `integration/dot-3`.
- Danh sách câu hỏi còn mở (TM 15, LT 14) chép vào bình luận issue cha.

---

# Phần E: Rủi ro và cách xử lý khi kẹt

| Rủi ro | Dấu hiệu | Xử lý |
|---|---|---|
| G0 trễ (đợt 2 chưa về `develop`) | Không tạo được `integration/dot-3` | Chờ. Không tách từ `integration/dot-2` (M.Trường chốt ngày 2026-11-17). |
| Sinh đề chậm với ngân hàng lớn | Số đo `perf` của EX-2 vượt 30 giây, hoặc nhiều lượt `GENERATION_TIMEOUT` | Sinh đề đã chạy nền nên người dùng chỉ chờ lâu hơn. Advisor báo M.Trường kèm số đo; không tự tăng hạn quá 60 giây. |
| Hàng sinh đề nghẽn khi nhiều người bắt đầu cùng lúc | Nhiều lượt `GENERATION_BUSY` | Tăng số luồng hoặc độ dài hàng qua cấu hình; đo lại trong QA. |
| VNPAY sandbox khác tài liệu | VP-1 thấy chữ ký hoặc mã khác | VP-1 ghi vào TM và `vectors.json`; CM-3b theo đó. CM-3b đã để các điểm này sau cấu hình. |
| Không có G2 trước QA | VP-1, VP-2 chưa chạy | QA chạy với cổng giả lập. VP thành việc riêng sau đợt. Không đưa về `develop` với `THI360_VNPAY_MODE=SANDBOX` mặc định. |
| VNPAY chưa trả lời Q9, Q14 | Chưa có danh sách IP, chưa rõ nghĩa mã hoàn | Không chặn đợt. Ghi vào bình luận issue cha; phải có trước khi lên môi trường thật. |
| Xung đột tệp chung (B9) | Rebase lỗi nhiều lần | Advisor xếp lại thứ tự merge: task nhỏ trước, task lớn rebase sau. |
| Đổi hợp đồng `commerce/spi` giữa chừng | Lane cần thêm hàm | Qua Advisor; lane đổi sửa mọi nơi gọi trong cùng PR (B1). |
| Cách đọc R14 khác ý M.Trường | M.Trường xem đề và thấy dạng câu đảo sai | Sửa `PaperOrdering` của module `exam` cùng test (sinh thử và lượt thi cùng đổi); không mở task mới. |
| Màn làm bài chậm trên điện thoại | Gõ tự luận bị trễ | Gom lưu theo nhịp (LT 7.2). Thành phần câu hỏi dùng `memo`. Đo bằng Playwright với CPU chậm 4 lần. |
| Lane không có commit mới sau một chu kỳ kiểm tra 15 phút của Advisor | Lane đứng | Theo `zone17-cook-plan`: Advisor hỏi lane, tách nhỏ task, hoặc đổi mô hình. Ghi vào sổ tiến độ. |

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
