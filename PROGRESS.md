# PROGRESS — Thi 360

> Cập nhật mỗi lần dừng việc. Trạng thái issue sống nằm trên UniAI; file này ghi **đã làm gì, vướng gì, làm gì tiếp**.

## Trạng thái hiện tại (2026-11-14)

**Giai đoạn:** đợt 1 (nền, PBI-01, PBI-02) đã merge vào `develop` ngày 2026-11-14 (PR #25, `14f983c`; 105/105 điểm của plan). Đang làm các việc phát sinh sau khi dùng thử bản demo; mỗi việc một issue và một PR vào `develop`. Các mục `T…` bên dưới là nhật ký của đợt 1, đường dẫn trang trong đó là tên cũ (xem bảng đổi tên ở mục "Sau đợt 1").

### Sau đợt 1 (2026-11-14)

- **Bản demo tạm** chạy trên VPS từ `develop`: web học viên `https://thi360.zone17.click`, web admin `https://admin.thi360.zone17.click` (profile `api,worker,dev`, có tài khoản mẫu). Cách vận hành nằm trên VPS ở `~/deploy/thi360-demo/README.md`, ngoài kho mã.
- **UNI-2574** (PR #26): access token hết hạn (5 phút) làm trang tải lại hoặc bị đưa về trang đăng nhập. Sửa: proxy `/api/[...path]` và middleware trang của hai web tự làm mới phiên ở phía máy chủ; trang đăng nhập quay lại `callbackUrl` hợp lệ khi đã có phiên. Triển khai sau nginx cần `proxy_buffer_size 16k` (README, mục chạy trên máy chủ Keycloak thật); cách làm mới dựa vào việc realm không bật "Revoke Refresh Token".
- **UNI-2577** (PR #28): danh sách bị nhảy khi đổi bộ lọc. Sửa: bảng giữ dữ liệu cũ trong lúc tải lại (`Table.refreshing`, `keepPreviousWhileSameScope`), giữ chỗ cho thanh cuộn dọc.
- **UNI-2578** (PR #27): hộp thoại Tạo tài khoản bố cục hai cột, hiện đủ trong một khung (cỡ `xl` của `DialogContent`, `PasswordField.rulesColumns`).
- **UNI-2576** (PR này): đổi đường dẫn trang sang tiếng Anh. Chữ hiển thị không đổi; không có chuyển hướng từ đường dẫn cũ.

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
- Việc để lại đã biết: test `privilege-escalation.test.tsx` (hộp thoại Tạo tài khoản, 403) phụ thuộc thời điểm, từng fail một lần khi máy rất bận; chưa chạy thử phiên tự làm mới với Keycloak bật thu hồi refresh token.

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

1. Người duyệt và merge các PR sau đợt 1 theo thứ tự #26, #28, #27 rồi PR của UNI-2576 (PR này chồng lên ba PR kia). Việc để lại của đợt 1 nằm ở mục 6 của `docs/acceptance/dot-1.md`.
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
