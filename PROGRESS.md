# PROGRESS — Thi 360

> Cập nhật mỗi lần dừng việc. Trạng thái issue sống nằm trên UniAI; file này ghi **đã làm gì, vướng gì, làm gì tiếp**.

### Hoàn tất sửa 3 lỗi giao diện Cổng học viên (`UNI-2795` / `UNI-1710`, `UNI-2796` / `UNI-1711`, `UNI-2797` / `UNI-1712`) (2026-11-25)

- **`UNI-2795` (`uniworkos UNI-1710` — Độ rộng hiển thị từng khối khi làm bài theo từng màn hình, nhánh `fix/UNI-2795-attempt-player-widths`):**
  - Điều chỉnh container `<header>` và `<main>` của `AttemptPlayer` (`apps/web-student/src/features/attempt-player/attempt-player.tsx`), chiều rộng/khoảng cách của `QuestionNav` (`question-nav.tsx`) và `padding` thẻ câu hỏi (`20px`): màn `1366 × 768` dùng full-width trừ `padding-x: 16px` (`1317px` lòng trong = khối trái `1015px` + `gap: 20px` + sidebar `282px`); màn `1920 × 1080+` (`min-[1440px]`) căn giữa với `padding-x: 24px`, khối trái `1092px`, `gap: 24px`, sidebar `300px`.
  - Chuyển lưới 2 cột câu chùm đọc hiểu trong `QuestionSet` (`packages/ui/src/question/question-set.tsx`) sang `gap-4 lg:grid-cols-[minmax(0,7fr)_minmax(0,8fr)]` (`gap: 16px`, cột Passage ~`46%` : cột Câu hỏi & Đáp án ~`54%`).
- **`UNI-2796` (`uniworkos UNI-1711` — Điều chỉnh hiển thị khi xem chi tiết bài thi, nhánh `fix/UNI-2796-exam-detail-display`):**
  - Bổ sung tổng thời lượng làm bài kèm quy tắc đồng hồ (`{minutes} phút · ...`) tại mục `Thời gian` của khối `Cách làm và cách chấm` trong `ExamDetailView` (`apps/web-student/src/features/storefront/exam-detail-view.tsx`).
  - Đổi tiêu đề đầu mục `Quyền sau khi mua` thành `Quyền lợi sử dụng` (`vi.json`) / `Usage benefits` (`en.json`).
- **`UNI-2797` (`uniworkos UNI-1712` — Thay đổi hiển thị danh sách bài thi, nhánh `fix/UNI-2797-catalog-card-display`):**
  - Đồng bộ nút thao tác trên thẻ danh mục (`variant === "card"` tại `apps/web-student/src/features/storefront/access-action.tsx`) về 2 dạng xếp dọc toàn chiều ngang (`w-full`): **Đã mua** (còn lượt `CAN_START` / đang làm `IN_PROGRESS`: 3 nút `Vào bài thi`/`Tiếp tục`, `Mua thêm lượt`, `Xem chi tiết`) và **Chưa mua** (`guest`, `NONE`, và đã hết lượt `EXHAUSTED`: 2 nút `Mua bài thi`/`Mua bộ đề` + `Xem chi tiết`).
  - Tách `FormatChip` (`product-card.tsx`) thành 2 tag thông tin riêng biệt (hình thức bài thi và tên phần/môn đi kèm) để không bị gãy thành 2 cột bên trong một chip tròn.

### Hoàn tất sửa 2 lỗi giao diện Màn làm bài học viên `QuestionNav` (`UNI-2793` / `UNI-1708` & `UNI-2794` / `UNI-1709`) (2026-11-25)

- **`UNI-2793` (`uniworkos UNI-1708` — PR #195, `def20c58`):** ghim cột `Danh sách câu hỏi` khi cuộn trên màn rộng kèm cuộn dọc nội bộ và cuộn mượt tới câu con trong `QUESTION_SET`.
- **`UNI-2794` (`uniworkos UNI-1709` — PR #196, `8735b45a`):** đổi màu ô/viền đỏ (`danger` semantic tokens) cho câu đánh dấu cần xem lại ở cả 3 mức hoàn thành và ô mẫu chú thích.
- Đã vượt 6/6 cổng CI, squash-merge vào `develop` và triển khai lên Demo (`https://thi360.zone17.click`, `demo-smoke.sh` xanh).

### Hoàn tất thiết kế lại Biểu mẫu Ma trận môn `v3` (`MatrixForm`) từ đầu tới cuối (2026-11-25)

- **Đã hoàn thành & lên Demo (`UNI-2776` — PR #194, `aea43b40`):**
  - **Bước 1 — `1 · Cấu trúc thành phần câu hỏi` (`MatrixStructureBlock`, `matrix-form-model.ts`, `fit.ts`, `matrix-blocking.ts`):**
    - Thay lưới 4 ô trừ ngược gây rối bằng **bảng thành phần cộng thẳng từ trên xuống** (`Vị trí câu`, `Tên nhóm trên đề`, `Loại câu hỏi`, `Quy cách lấy câu`, `Số câu`, `Tỷ lệ`, `Thao tác`), dòng chân bảng `Tổng cộng (N dòng thành phần)` kèm `StatusChip` (`Khớp {total} câu` / `Thiếu {count} câu` / `Vượt {count} câu`) và dải gom theo định dạng `Tóm tắt theo định dạng`.
    - Cho phép **thêm/xóa/đổi tên/đổi loại/đảo thứ tự `↑/↓`** các dòng loại câu động theo từng môn (đủ 8 `QUESTION_TYPES`, gắn nhãn `Chấm tay` cho `ESSAY`, khóa xóa khi chỉ còn 1 dòng) và nút **`↺ Mặc định của môn`** khôi phục từ template chuẩn cùng kỳ thi (kể cả sau khi đã lưu cấu hình tùy biến).
    - **Câu chùm linh hoạt & cỡ tùy ý (`QUESTION_SET`):** chuyển nhanh giữa **`Cố định cỡ`** (`[setCount] chùm × [setSize] câu/chùm` nhập số nguyên dương tùy ý `1, 2, 3, 4, 5...`, tự tính `questionCount = setCount × setSize`) và **`Linh hoạt cỡ`** (`setCount = null, setSize = null`, sửa trực tiếp tổng câu con). Bảo toàn đầy đủ `MatrixSetSource` (`Nguồn chùm`), `DiaMaterialSection` của HSA Địa lí và nhập 2 cột `%` của V-ACT.
    - **Công tắc `Trộn lẫn giữa các loại câu` (`ExamSection.mixBlocks`) + Banner thông báo quy tắc đảo câu (`data-testid="matrix-shuffle-banner"`):**
      - Khi tắt (mặc định): hiển thị khoảng vị trí tuần tự (`1–32`, `33–47`, `48–50`), cho phép đổi thứ tự `↑/↓` và hiện dòng hướng dẫn đảo riêng từng nhóm.
      - Khi bật (`mixBlocks: true`): đổi cột vị trí sang `⇄ Trộn chung`, khóa `↑/↓`, đổi sang thông báo trộn chung toàn môn; **Backend** (`ExamSection`, `PaperOrdering`, `AttemptPaperAssembler`, `PreviewPaperOrdering`, `ExamPreviewService`, `ExamStructurePresentation`) trộn xen kẽ câu đơn lẻ và nguyên cụm câu chùm (giữ nguyên thứ tự câu con bên trong chùm) trên cả đề thi học viên lẫn Đề xem trước / Làm thử, và bỏ dải `positionRange` cố định khi `mixBlocks = true`.
  - **Bước 2 & Bước 3 (`MatrixDistributionBlock`, `MatrixScopeBlock`, `MatrixFormHsa`):** đồng bộ thẻ bảng đôi có viền `2A · Khối lớp` & `2B · Độ khó` kèm dòng tổng cuối bảng (`Đã khai x/y` hoặc `Tổng tỷ lệ x%`), thẻ `3 · Phạm vi và chỉ tiêu chủ đề` / `MAPID`, tự gắn `deletedGroupName` cho quy tắc `mapidRules` mồ côi khi xóa hoặc đặt lại dòng.
  - **Kiểm chứng & Triển khai:** Pass 7/7 cổng CI trên PR #194, squash-merge vào `develop` (`aea43b40`), chạy `deploy-develop.sh` + `demo-smoke.sh` xanh trên VPS `bro` (`8735b45a`).

### Hoàn tất nâng cấp khung Master–Detail liền khối cho tab Cấu trúc & Ma trận `/exams/[id]/structure` (2026-11-24)

- **Đã hoàn thành & lên Demo (`UNI-2776` — PR #193, `31856a31`):**
  - **Khung làm việc Master–Detail liền khối (`ExamStructureView`, `ExamStructureList`, `SubjectChoiceNav`):** gom cây điều hướng `Cấu trúc` bên trái, vùng chi tiết bên phải và thanh công cụ bám dính đầu khung (`data-testid="structure-toolbar"`: `Hủy thay đổi`, `Lưu`, `Kiểm tra toàn bài`, `Tiếp tục`) vào 1 khối duy nhất thay vì tách thành nhiều Card rời rạc và đẩy nút thao tác lên tiêu đề trang.
  - **Thu gọn Tổng hợp toàn bài về đúng màn `Tổng quan bài` (`ExamOverviewPanel`, `ExamStructureSummaryTable`, `ExamStructureStatusStrip`, `ExamStructureAlertsBanner`):** chuyển bảng cây 40–50 dòng của toàn bài vào màn **Tổng quan bài (`?exam=1`)** kết hợp cùng `Điểm theo môn` và `Chỉ tiêu cấp bài`; khi đang sửa từng Phần/Môn chỉ hiện dải trạng thái 1 dòng kèm nút nhảy nhanh `Xem bảng tổng hợp`, và chỉ mở dải cảnh báo khi thực sự có lỗi hoặc kết quả kiểm tra.
  - **Gộp Header + Bảng dòng & mở `RowEditor` ngay dưới dòng đang sửa (`RowListForm`, `RowEditor`, `SubjectFormHeader`):** loại bỏ Card tiêu đề rỗng đứng riêng và mở khung `Sửa dòng` inline ngay bên dưới dòng được chọn (`data-form-row-editor-row`) thay vì nhảy xuống đáy bảng.
  - **Làm phẳng viền lồng nhau & chỉnh lưới biểu mẫu (`MatrixForm`, `MatrixStructureBlock`, `EnforcementControl`, `ExamStructurePartPanel`, `SubjectChoiceOptions`, `PracticeReviewSubjectPanel`, `ScoringExamples`):** bỏ Card-trong-Card, tách dải thông số chỉ đọc từ Bước 1 (`Thông số từ Thông tin chung`), căn ngang chip + công tắc `Áp dụng khi sinh đề`, mở rộng cột `Số nhóm` và gắn liền `Ví dụ chấm` / `Khung tham chiếu` bằng đường chia vùng.
  - **Kiểm chứng & Triển khai:** Pass `142/142` file unit test (`1.646` test), `typecheck`, `eslint`, `next build`, **6/6 cổng CI** trên PR #193, squash-merge vào `develop` (`31856a31`), chạy `deploy-develop.sh` + `demo-smoke.sh` xanh trên VPS `bro` và kiểm chứng thị giác bằng 11 ảnh chụp Playwright trực tiếp trên `https://admin.thi360.zone17.click/exams/[id]/structure`.

### Hoàn tất Refactor & Nâng cấp Layout Cấu hình Bài thi `/exams` + Auth v2 (2026-11-23)

- **Đã hoàn thành & lên Demo (`UNI-2775` — PR #191, `6c4fb0b5`):**
  - Xóa độ trễ 60s đồng bộ màu tùy chỉnh hệ thống ở trang học viên (`cache: "no-store"` tại `apps/web-student/src/app/layout.tsx`) và dựng đầy đủ 5 lớp studio 3D của thiết kế Option 2 (`AuthBackground`) theo semantic CSS tokens Sáng/Tối. Đã merge vào `develop` (`6c4fb0b5`) và triển khai trên `https://thi360.zone17.click/login`.
- **Đã hoàn thành & lên Demo (`UNI-2776`, sub-issues `UNI-2777..UNI-2781` — PR #192, `8a1cba87`):**
  - **Hợp nhất luồng & dọn kiến trúc (`-5.059 net prod LOC`, 269 tệp thay đổi `+7.416 / -11.613`, không đụng `backend/` hay `packages/api-client/`):**
    - Tách các thành phần dùng chung lên `@thi360/ui` (`packages/ui/src/components/nav-tabs.tsx`, `packages/ui/src/essay-editor/tiptap-essay-editor.tsx`, `packages/ui/src/question/wire-parsers.ts`) và cho cả `web-admin` lẫn `web-student` (`attempt-player`) dùng chung.
    - Xóa nhánh dead wizard 4 bước & trùng lặp 3 màn Thông tin chung; hợp nhất `/exams/new` và `/exams/[id]` về **1 vỏ `ExamEditorLayout`** (khóa tĩnh Tab 2..5 khi đang tạo mới, thanh tóm tắt `<dl>` có viền, `tabular-nums`) + **1 biểu mẫu `ExamGeneralForm` (`mode: "create" | "edit"`)**, kèm redirect server `/exams/new?exam=<id>` → `/exams/<id>`.
    - Xử lý trọn vẹn **6 lỗi nghiệp vụ biên tập `B-01..B-06`** (phân quyền `EXAM.CREATE` / `EXAM.MODIFY` / `EXAM.PUBLISH` / `EXAM.DELETE`, payload cập nhật khớp schema từng kỳ thi, cảnh báo rời trang/đổi kỳ thi khi đang sửa dở, chặn `+ Thêm nhóm` ngoài chế độ dự phòng `MD`, đồng bộ `useDiscontinueExam` giữa header và tab Phiên bản). Giữ nguyên nhánh dự phòng `MD` của `BlockEditor` và `PreviewAudioPlayer`.
  - **Nâng cấp UI/UX màn cấu hình bài thi (Tab 1..5 & Danh sách):**
    - **Tab 1 (`ExamGeneralForm`):** bố cục `@container` 2 cột với cột phải `Tóm tắt` + `Tình trạng` bám dính (`sticky`), tách thẻ Định danh/Hướng dẫn và thẻ Kỳ thi (`KindSelect`)/Phạm vi đối tượng, thanh nhấn `bg-primary-600` ở chế độ sửa đối xứng với huy hiệu bước `1..3` khi tạo mới.
    - **Tab 2 (`ExamStructureView`, `ExamOverviewPanel`, `PracticeReviewSubjectPanel`):** bỏ thanh `<dl>` trùng lặp và liên kết thừa `edit-overview-link`, cây cấu trúc bám dính bên trái, thẻ con hạn ngạch có viền rõ ràng.
    - **Tab 3 & 4 (`ExamReviewCheck`, `ExamPreviewPanel`, `ExamPreviewComplianceBanner`, `ExamPreviewAllocation`):** gom gọn 8 nhóm kiểm tra (mặc định thu gọn chi tiết khối, tự bung qua `flushSync` khi xem trùng lặp), rút gọn hộp tuân thủ ở tab Đề xem trước thành banner 1 dòng và gộp bảng đối chiếu hạn ngạch môn sang tab `Đối chiếu phân bổ`.
    - **Tab 5 (`ExamVersionsView`) & Danh sách (`ExamListingView`):** dải thông tin `VersionHeroCard` đồng bộ cấu trúc và thẻ xem trước bên phải bám dính trên màn danh sách.
  - **Kiểm chứng & Triển khai:** Pass toàn bộ **6/6 cổng CI** trên PR #192 (`link`, `api-client-drift`, `build`, `e2e (1..3)`), squash-merge vào `develop` (`8a1cba87`), và chạy `deploy-develop.sh` xanh trên VPS `bro` (`https://admin.thi360.zone17.click/exams`, `demo-smoke.sh` xanh). Tài liệu spec/plan/evidence lưu tại `D:/.Vietants_Project/.thi360-dev/exams-refactor/`.

### Hoàn thiện các điểm mở sau Đợt 3 & nâng cấp giao diện Auth (2026-11-22 / 2026-11-23)

- Đã hoàn thành trọn vẹn 5/5 issue mở sau Đợt 3, toàn bộ CI xanh và đã squash-merge vào `develop` (`13af543b`):
  - **UNI-2772** (PR #186, `a141facb`): nâng mức tối thiểu mặc định `THI360_VNPAY_MIN_AMOUNT` từ `5000` lên `10000` (`10.000đ`, TM Q12).
  - **UNI-2771** (PR #187, `6c0868b4`): tối ưu `PaperSolver` / `PathSearch` với bitmask và pre-pruning để `PaperGenerationPerfIT` chạy `< 30s` (~5s thực tế).
  - **UNI-2773** (PR #188, `16b8ba63`): bắt buộc xác minh OTP 6 chữ số qua email trước khi tạo tài khoản ở màn Đăng ký (`POST /api/v1/public/auth/register/request-otp` & `verify-otp`, mã hóa mật khẩu tạm `AES-256-GCM` qua `PendingPasswordCipher`, migration `V202611220100`, bước OTP 2 bước trên `web-student`, TM Q13).
  - **UNI-2775** (PR #189, `b4da6a56`): áp dụng nền **Option 2 — Geometric Orbital Rings & Soft Mesh** (`AuthBackground` dùng semantic CSS tokens Sáng/Tối) cho toàn bộ 6 màn xác thực học viên (`/login`, `/register`, `/forgot-password`, `/reset-password`, `/change-password`, `/verify-email`).
  - **UNI-2774** (PR #190, `13af543b`): nối màn Lớp học (`/classes/[id]`) với nguồn quyền thi `CLASS` (TM Q10, C12, C15 — bảng `commerce.class_exam_assignments` migration `V202611220200`, sự kiện đồng bộ thêm/xóa học viên qua `ApplicationModuleListener`, 3 endpoint admin có `@RequirePermission` + `AuditRecorder`, tab Bài thi được giao trên `web-admin`, thẻ `entitlementSource = CLASS` trên `web-student` `/home` & `/prepare/[entitlementId]`, E2E `class-exam-assignment.spec.ts`).
- Bản demo (`https://thi360.zone17.click`, `https://admin.thi360.zone17.click`): đã chạy `deploy-develop.sh` thành công trên VPS `bro` ở commit `13af543b` (backup DB `3.38 MB`, Flyway `202611220200` `95|0`, `demo-smoke.sh` xanh toàn bộ).

### Skill điều phối AGY qua file (2026-11-21, ngữ cảnh phiên UNI-2622)

- Theo yêu cầu riêng của người dùng, tạo `.agents/skills/zone17-skip-agy/`: Advisor X ghi handoff; relay Y mở worktree và CLI AGY trống, nhập prompt/Enter, chuyển câu hỏi, câu trả lời và `work_done`; lead dùng subagent native theo role. Không sửa skill cook-plan hiện có.
- Tài liệu và mẫu đi kèm quy định hub SSH dùng chung ngoài worktree, host map, thông điệp bất biến, checkbox tổng hợp, một relay giữ lock, biên nhận và phục hồi khi chưa chắc đã gửi prompt. Cần chọn hub thực tế và đường đánh thức đúng Advisor trước khi chạy unattended.
- Đã chạy `quick_validate.py` bằng Python 3.12: `Skill is valid!`; kiểm tra YAML, JSON ví dụ, liên kết tương đối và `git diff --check` đều qua. Chưa khởi chạy relay/scheduler, chưa tạo hub trên VPS, chưa kiểm chứng round trip liên server. Không commit/push/PR hoặc đổi trạng thái issue.

### Kiểm tra AGY CLI tương tác (2026-11-21, ngữ cảnh UNI-2622)

- Theo yêu cầu của người dùng, tạo worktree thử nghiệm detached từ `develop` tại `.git/agent-worktrees/agy-interactive`, mở Antigravity CLI trống qua Orca rồi nhập prompt và Enter. Đã thấy phản hồi `AGY interactive OK`; phiên đang chờ nhập tiếp.
- Terminal: `term_619ca97d-2466-47a9-909a-1e7f634cca47`. Chỉ kiểm tra luồng tương tác, chưa giao việc triển khai; không commit, push, mở PR hoặc đổi trạng thái issue.

## Trạng thái hiện tại (2026-11-24)

**Giai đoạn:** Đợt 1 (PBI-01, PBI-02 — PR #25 `14f983c`, 105/105 điểm), Đợt 2 + 2B (PBI-03, PBI-04 Ngân hàng câu hỏi, Bài thi 9 kỳ thi, Bộ soạn thảo — PR #63, #80, #120 `1774c74d`, 83/83 điểm đợt 2B), **Đợt 3 (PBI-05..09 Thương mại, PBI-10..13 Lượt thi, H-SCA/V-SAT, 44/44 task = 138/138 điểm = 100%)**, **Form theo môn (FTM, 6/6 task = 24/24 điểm = 100%)** (PR #185 `a4f3854a`), cùng **các đợt hoàn thiện & refactor sau Đợt 3 (`UNI-2771`..`UNI-2781`, PR #186..#193 `31856a31`)** đều đã merge vào `develop` và triển khai lên bản demo (`https://thi360.zone17.click`, `https://admin.thi360.zone17.click`). Các mục bên dưới giữ nguyên nhật ký chi tiết theo từng đợt.

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

### Thương mại và lượt thi · PBI-05 đến PBI-13 (2026-11-16)

- **UNI-2622** (đặc tả, nháp chờ duyệt). PBI-05 (uniworkos UNI-1634) giao cho M.Trường ngày 2026-11-16; BA thêm tài liệu PBI-06 đến PBI-13 cùng ngày.
  - Bản lưu UniNote: `docs/design/thuong-mai/` (PBI-05, 06, 07–08, 09) và `docs/design/luot-thi/` (PBI-10 đến 13).
  - **Đặc tả thương mại** `docs/specs/2026-11-16-thuong-mai-pbi-05-09.md`, C1–C16. M.Trường chốt:
    - thông tin bán gắn bài thi, đổi theo phiên bản công bố;
    - khuyến mãi tự động và voucher; voucher về 0 đồng thì cấp ngay;
    - một hệ thống tài khoản cho admin và học viên;
    - email theo cách của uniwork (OTP, liên kết đặt lại, hàng thư và worker);
    - VNPAY sandbox, khóa trong biến môi trường, có cổng giả lập;
    - chỉ quyền từ mua, có cột nguồn cho Bài theo lớp;
    - mua thêm cộng lượt;
    - hoàn tiền qua API VNPAY, ghi nhận tay khi API lỗi; hoàn toàn bộ đơn thì thu hồi lượt chưa dùng;
    - không có thời hạn sử dụng.
  - **Đặc tả lượt thi** `docs/specs/2026-11-16-luot-thi-pbi-10-13.md`, L1–L6. M.Trường chốt:
    - tự luận chấm tay, đề FULL có tự luận vẫn mở, điểm tổng hiện sau khi chấm xong;
    - kết quả xem mãi;
    - D04: chờ chọn phần 3 tối đa 15 phút, quá thì kết thúc lượt và phần 3 được 0 điểm; ~~đề đồng hồ chung chuyển phần tự do~~ (thay bằng L7 ngày 2026-11-17);
    - không có band điểm hay xếp loại, chỉ có tổng điểm (UniNote PBI-12 viết nhầm);
    - D05: giữ lượt khi bắt đầu, tính khi kết thúc.
  - **Chờ BA sửa UniNote:**
    - thời hạn sử dụng (PBI-05, 06, 07, 09);
    - mua thêm khi còn lượt;
    - "không gộp theo email" ở task PBI-06;
    - band điểm và chặn FULL có tự luận (PBI-12);
    - thời hạn xem kết quả (PBI-13);
    - D08, D09 chưa có nội dung.

    Chi tiết ở mục 15 và mục 14 của hai đặc tả.
  - **Review bằng Fable 5.2 (2026-11-16):** 9 điểm Chặn, 33 Nên sửa, 19 Nhỏ; đã sửa trong hai đặc tả. Thêm E24–E27 (thương mại) và F20–F21 (lượt thi). Phần VNPAY (chữ ký `querydr` và `refund`, mã trạng thái, số tiền tối thiểu) còn phải đối chiếu tài liệu và sandbox. Các chỗ cần sửa BE spec ghi ở Q11 và R11.
  - **Review đặc tả lượt 2 (Fable, 2026-11-17):** Fable review và tự sửa hai đặc tả.
  - **M.Trường chốt ngày 2026-11-17:**
    - mọi đề làm tuần tự từng phần, kể cả đề đồng hồ chung (LT L7, thay R13);
    - R14 theo BE spec mục 12 (đã chốt ở phiên PBI-03/04);
    - sinh đề chạy nền ngay từ đầu: bắt đầu trả 202, web hỏi lại (LT L8);
    - duyệt cả 6 phần thêm vào module `exam` (Q11, Q15, R11);
    - VNPAY sandbox: dùng merchant sandbox cũ của reap, nay chỉ cho Thi 360 (mỗi Terminal Code một URL IPN); khóa ngoài repo; URL IPN `https://thi360.zone17.click/api/v1/public/payments/vnpay/ipn` đã đặt trên trang merchant sandbox (2026-11-17);
    - Q9, Q14 M.Trường hỏi VNPAY, đợt vẫn chạy;
    - đợt 3 bắt đầu sau khi đợt PBI-03/04 xong;
    - tách C-0b;
    - được dựng `integration/dot-3` trên VPS demo, không cần giữ dữ liệu cũ; VNPAY thật làm sau.
  - **Plan** `docs/plans/2026-11-17-thuong-mai-luot-thi-pbi-05-13.md`:
    - 34 task, 100 điểm, 8 sóng, có pipeline song song và chờ ở Phần C;
    - đường dài nhất: C-0 → CM-4a → AT-1 → AT-2a → AT-4 → AT-5 → FA-4 → QA;
    - đã qua 2 lượt review của Fable: lượt 1 có 3 Chặn, 18 Nên sửa, 8 Gợi ý, tôi đã sửa; lượt 2 có 0 Chặn, 7 Nên sửa, Fable tự sửa.
  - **Tiếp theo:**
    - M.Trường duyệt đặc tả và plan (G1); commit, mở PR khi M.Trường yêu cầu.
    - Chưa cook. Cook sau khi đợt 2 về `develop` (G0).
  - **Thiết kế FE đợt 3 (kiểm tra 2026-11-17):** canvas thiết kế (artifact `4WyWEQAR8DTdZQTmQA5eG7`, sửa lần cuối 2026-11-15) chưa có trang nào cho đợt 3. Canvas có 24 màn chưa chép vào repo: 6 màn `CauHoi*Thi` (làm bài trên máy tính) và 8 màn `ThiMobile*` (trang 09) dùng được cho FS-4, FS-5; 10 màn `SoanThao*` (trang 07) thuộc trình soạn thảo. Các màn làm bài vẽ theo 6 dạng câu cũ và cách làm cả đề một lượt, chưa theo 8 dạng (B6) và LT L7 (làm tuần tự từng phần).
  - **Đã vẽ thiết kế đợt 3 (2026-11-17, M.Trường yêu cầu):**
    - 43 màn mới trên canvas, chia 7 trang:
      - trang 10: công khai, tài khoản;
      - trang 11: mua bài;
      - trang 12: làm bài theo phần;
      - trang 13: dạng câu mới, xem lại;
      - trang 14: kết quả;
      - trang 15: admin thông tin bán, khuyến mãi;
      - trang 16: admin đơn hàng, lượt thi.
    - Thêm ghi chú lên trang 06 và 09: màn làm bài cũ chỉ còn dùng phần thân câu.
    - Chép ra `docs/design/dot-3/` (43 màn mới và 14 màn làm bài cũ), kèm `README.md` ghi màn theo task, chỗ cần chốt và những gì agent tự đặt.
    - Plan: thêm dòng **Thiết kế** vào 10 task FS và FA, thêm mục B8; FS-1 sửa thành "Vào bài thi" theo TM 11.3.
    - Cần chốt: `wordLimit` tối thiểu hay tối đa; nhãn "Chờ chấm" (LT) khác "Đang chấm" (DESIGN 5.14); đánh số câu theo phần; nhãn trạng thái và câu chữ do agent đặt (README).
  - **Ảnh hưởng của UniNote PBI-04 bản 2026-11-17 (kiểm tra ngày 2026-11-17):**
    - Nguồn:
      - phân tích của Advisor ở `.thi360-dev/pbi04-gap-2026-11-17.md`;
      - 4 bản lưu cấu trúc HSA, QDA, BCA, SP2E/SPT ở `docs/design/dot-2/` (chưa commit).
    - M.Trường muốn làm đợt 2B (sửa theo PBI-04 mới) trước đợt 3.
    - Đợt 3 bị ảnh hưởng ở 7 điểm:
      1. ma trận theo môn dùng chung cho các mã QDA, BCA, kèm ghi phiên bản quan hệ mã–môn khi công bố (EX-1, EX-2, AT-1, AT-2b, DS);
      2. HSA 17 trừ 1 câu (EX-2 `solvePick`);
      3. thứ tự nhóm: code đã bỏ `groupOrder`, nhưng LT F22 còn đọc; Văn giữ thứ tự nhóm, Tiếng Anh theo vị trí cố định;
      4. V1 chưa có tự luận: BCA FULL không mở được bài có điểm; thang điểm V1 (LT L2, 9.2, 9.3; AT-5; FA-4; mẫu số Tin);
      5. định hướng Tin học chọn ở màn chuẩn bị, chỉ sinh một nhánh (LT F4, 6.2; AT-2b; FS-5);
      6. bộ đề SP2E bán nhiều bài trong một sản phẩm, trái TM C1;
      7. phạm vi bài, bài ôn chủ đề và điểm tối đa của phần thực làm (FS-1, FS-5, FS-6).
    - M.Trường chốt (2026-11-17): giữ tự luận chấm tay; định hướng Tin chọn ở màn chuẩn bị; bộ đề SP2E làm trong đợt 3; nhóm giữ thứ tự, câu đảo trong nhóm, Văn nhóm 5 có công tắc.
  - **Đợt 2B, UNI-2647 (2026-11-17):**
    - Đặc tả, bảng yêu cầu R-01 đến R-105, plan: `docs/specs/2026-11-17-pbi04-dot-2b*.md`, `docs/plans/2026-11-17-pbi04-dot-2b.md`.
    - Plan: 17 task, 100 điểm, 5 sóng; cổng G0 (đợt 2 và PR UNI-2622 đã merge), G1 (duyệt), G2 (bản vẽ canvas trang 17 trở đi, xuất `docs/design/dot-2b/`, xong trước khi BE-1 merge).
    - Review Fable: lượt 1 Fable sửa đặc tả 2B, TM; plan 1 Chặn, 12 Nên sửa (tôi sửa: tách BE-4, FE-3, thêm BE-10 câu hỏi mẫu, BE-1 viết lại `R__` đề thi). Lượt 2 Fable sửa thêm (H20: tối đa 8 chỉ tiêu chủ đề mỗi cấp), không còn Chặn. Báo cáo `.thi360-dev/review-2b-r1.md`, `review-2b-r2.md`.
    - Đợt 3 đã sửa theo 2B (nhánh `docs/UNI-2622-dac-ta-pbi-05`): LT (Tin chọn trước, 17 − 1, `PaperOrdering`, số câu và điểm từ `paths[]`); TM C17 và mục 16 bộ đề; plan 36 task, 103 điểm, cổng G0b chặn EX-1, EX-2; `docs/design/dot-3/README.md` có danh sách màn cần vẽ lại.
    - **M.Trường chốt K6 đến K15 (2026-11-17):** chỉ tiêu chủ đề là "ít nhất"; Văn SP2E V1 bỏ phần Viết, mặc định bản đề minh họa cũ 30 câu; phiên bản cấu trúc chọn ở bước 2; khối lớp của môn không phân lớp chỉ để đối chiếu; chỉ tiêu cấp phần để sau V1; bỏ dạng bài, phân biệt bằng nhãn và MAPID; sinh thử đảo phương án như đề thật; bỏ revision riêng của tập mã; THPTQG Tin 24 + 2 + 2 (BA xác nhận). Fable thêm H21 (độ khó tham khảo), H22 (nhãn khớp một trong).
    - Fable lượt 3, 3b áp quyết định vào đặc tả; tôi sửa hai plan; lượt 4 rà khớp, không còn Chặn (`.thi360-dev/review-2b-r3.md`, `r3b`, `r4`). Plan 2B vẫn 17 task, 100 điểm; đợt 3 36 task, 103 điểm.
    - **Hỏi BA:** danh sách và tên nhãn dạng câu; câu thật phải gắn nhãn; các câu còn lại ở 2B mục 18.
    - **M.Trường duyệt đặc tả và plan 2B, đợt 3 ngày 2026-11-17 (G1).** PR #70 (2B) đã merge vào `develop`; PR của UNI-2622 mở cùng ngày.
    - **Bản vẽ (2026-11-17, PR #78, nhánh `docs/UNI-2647-ban-ve-2b`):**
      - canvas trang 17–19: 14 màn 2B, xuất ra `docs/design/dot-2b/` kèm README;
      - trang 12: sửa `ChuanBiLamBai`, `ChonPhan3`, `NopBaiXacNhan` theo 2B;
      - trang 20: 7 màn bộ đề và làm thử, xuất ra `docs/design/dot-3/`;
      - dòng Thiết kế của FE-1…FE-4 (plan 2B) và FS-5, FB, FA-4 (plan đợt 3) đã cập nhật.
    - **Cook đợt 2B bắt đầu 2026-11-17 23:45 (UTC+7).** Issue cha UNI-2649, 17 sub-issue UNI-2650…UNI-2666 (bảng ở `TASK.md`). Nhánh `integration/dot-2b` từ `develop` 23c64377 (đã có đợt trình soạn và cờ Giữ thứ tự phương án). Orca Run `run_89ebffeca5a8`, thư mục đợt chạy `.thi360-dev/dot-2b-cook/` (tên `dot-2b/` của plan A1 đã dùng cho lane mockup UNI-2599). Sóng 1: BE-1 (bro), BE-2 (Windows), BE-3 (bro).
      - 2026-11-18 01:46 (UTC+7): BE-3 (UNI-2652, PR #81) merge vào `integration/dot-2b`: `answerFormat`, bộ lọc `findCandidates`, nhập câu hỏi. Advisor chạy lại web (261 test) và backend phần câu hỏi (86 test), xanh. Tiến độ 4/100.
      - 2026-11-18 03:15 (UTC+7): BE-1 (UNI-2650, PR #82) merge: mô hình cấu trúc v2, migration 28 cấu trúc mẫu, hợp đồng API cả đợt (stub 501), api-client mới. Advisor chạy lại backend phần đề thi (299 test) và web (236 test), xanh. Tạm thời đề mẫu HSA, QDA, BCA kiểm tra chưa hợp lệ cho tới BE-4a, BE-5. Tiến độ 14/100. Mở sóng 2: FE-1, FE-2, vòng sau của BE-2.
      - 2026-11-18 04:24 (UTC+7): BE-2 (UNI-2651, PR #83) merge: tổ hợp có nhánh và thứ tự môn, `listCombinations`, lớp tra tổ hợp có lọc nhánh, phạm vi bài thi theo kỳ thi, nhãn dạng câu. Advisor chạy lại backend 142 test và web, xanh (2 test web quá giờ khi máy bận, chạy lại riêng xanh). Tiến độ 18/100. Mở BE-4a, BE-6, BE-7 trên bro.
      - 2026-11-18 06:40 (UTC+7): BE-4a (UNI-2654, PR #84) merge: bộ giải v2, đường theo mã, chùm, bộ lọc nhóm nhãn/MAPID/năng lực, HSA 17 − 1, `PaperOrdering`, `matrix.ts`. Advisor chạy lại backend 693 test và web 505 test, xanh. Tạm thời tới BE-5: HSA Khoa học báo lệch tổng. Tiến độ 24/100. Mở sóng 3: BE-5, BE-4b, BE-8.
      - 2026-11-17 23:49 (UTC+7): BE-6 (UNI-2658, PR #85) merge: bước 1 tạo bài theo tổng quan (`buildMode`, `overview`, mẫu `is_default`), `PUT overview` giữ nhóm, đổi phiên bản cấu trúc ở bước 2 (`confirmReplace`, `overviewPolicy`), mẫu `<KIND>_REVIEW`, `P<order>` cho bài theo phần. Advisor chạy lại backend 312 test và web 244 test trên bản gộp, xanh. Kiểm "summary báo dư 10" chuyển sang BE-5.
      - 2026-11-18 00:22 (UTC+7): BE-7 (UNI-2659, PR #86) merge: mẫu cấu trúc theo số liệu BA (HSA, QDA, BCA mọi phạm vi; SP2E 2026 và đề minh họa cũ; SPT V1; giữ V-ACT, THPTQG), migration `V202611180652`. Advisor chạy lại backend 332 test, xanh. Ngoại lệ bộ kiểm tra (FU-BE1-6, FU-BE7-2, FU-BE7-3) chờ BE-5 gỡ.
      - 2026-11-18 00:38 (UTC+7): FE-1 (UNI-2661, PR #87) merge: bước 1 tạo bài trên web admin (phạm vi theo kỳ thi, môn rồi phần P<n>, kiểu xây bài, bảng tổng quan theo id phần/mục của mẫu, xác nhận nạp lại). Advisor chạy lại typecheck, lint, vitest web admin 1614 test, xanh (1 timeout không liên quan, chạy riêng đạt).
      - 2026-11-18 01:29 (UTC+7): BE-4b (UNI-2655, PR #88) merge: bộ giải chỉ tiêu chủ đề, chỉ tiêu cấp bài một môn (gộp qua phần), cắt 17 − 1 giữ chủ đề bắt buộc; ca 8 chỉ tiêu chạy 21 ms trên cloud. Advisor chạy lại backend 347 test, xanh.
      - 2026-11-18 01:39 (UTC+7): BE-10 (UNI-2653, PR #89) merge: dữ liệu câu hỏi mẫu đủ nguồn cho 78 mẫu STANDARD của BE-7. Advisor chạy lại backend 746 test, xanh.
      - 2026-11-18 02:19 (UTC+7): BE-8 (UNI-2657, PR #90) merge: sinh thử đề theo mục, đường, cả bài; lưu Redis 30 phút, sinh lại từng phần, gán chỉ tiêu chủ đề từ bộ giải, thứ tự PaperOrdering. Advisor chạy lại backend 383 test, xanh.
      - 2026-11-18 03:30 (UTC+7): BE-5 (UNI-2656, PR #91) merge: kiểm tra theo đơn vị, mục, phần và cả bài, chỉ giải lại đơn vị đã cũ; `sectionStates`, `structure/summary`, mã lỗi mục 7.3. Advisor chạy lại backend 445 test và web 1.614 test, xanh. Chuyển sang BE-9: ba mã thiếu nguồn theo bộ lọc, `COMBINATION_LIST_CHANGED`, điểm SP2E 2026.
      - 2026-11-18 04:13 (UTC+7): Duyệt phần bổ sung V-ACT, THPTQG (UNI-2671): thêm BE-11 đến BE-15, FE-5, tổng đợt 127 điểm. Chốt: THPTQG Ngữ văn chưa bán, bảng mã đối tác làm sau, chỉ tiêu mạch giữ "ít nhất". Đặc tả và kế hoạch ở `docs/specs`, `docs/plans` ngày 2026-11-18.
      - 2026-11-18 04:36 (UTC+7): G2b (UNI-2678, PR #92) merge: sáu bản vẽ bổ sung cho V-ACT, THPTQG. Chưa đưa lên canvas chung. FE-5 và FE-3b hết chờ thiết kế.
      - 2026-11-18 04:49 (UTC+7): FE-2 (UNI-2662, PR #93) merge: khung bước 2 bốn vùng, hộp thoại ba nút, đổi phiên bản cấu trúc. Advisor chạy lại typecheck, lint, vitest 1.662/1.664; hai test hết giờ khi chạy cả bộ, chạy riêng đạt. Mở FE-3a, FE-4.
      - 2026-11-18 05:30 (UTC+7): BE-11 (UNI-2672, PR #94) merge: cờ mẫu `publishable`, `fixed_counts`, `TopicQuota.percent`, `KnowledgeScope.everyChildInScope`. Advisor chạy lại backend 451 test, xanh. Mở đường cho BE-12, BE-13, FE-5.
      - 2026-11-18 06:20 (UTC+7): BE-9 (UNI-2660, PR #95) merge: công bố chụp danh sách tổ hợp, đường theo phần, cảnh báo `COMBINATION_LIST_CHANGED`, ba mã thiếu nguồn, SP2E 2026 = 10 điểm. Advisor chạy lại backend 472 test, xanh. Mở đường cho PR của BE-12 và BE-15.
      - 2026-11-18 06:42 (UTC+7): FE-3a (UNI-2663, PR #97) merge phần gốc 7 điểm: vùng sửa nhóm, điều kiện, slots, phạm vi kiến thức. Advisor chạy lại typecheck, lint, sinh api-client đều sạch; 4 test web quá hạn 5 giây khi chạy cả bộ trên Windows, chạy riêng đạt 3/3 lần, cloud đạt 1695/1695. Phần thêm 1 điểm chờ BE-12.
      - 2026-11-18 06:56 (UTC+7): BE-14 (UNI-2675, PR #96) merge: sáu nhãn D9 và câu hỏi mẫu THPTQG, V-ACT trong dữ liệu dev. Advisor chạy lại 38 test backend bị ảnh hưởng, xanh; cloud 650 test đạt. Sáu mã nhãn còn chờ BA xác nhận.
      - 2026-11-18 07:49 (UTC+7): BE-12 (UNI-2673, PR #98) merge: chùm cỡ linh hoạt trong bộ giải, `everyChildInScope`, miễn `SET_SIZE_MISMATCH` cho chùm linh hoạt. Advisor chạy lại backend 508 test, xanh; api-client sinh lại không đổi. Đề 120 câu giải trong 29 ms trên cloud.
      - 2026-11-18 16:00 (UTC+7): FE-4 (UNI-2665, PR #99) merge phần gốc: tab Kiểm tra và Xem trước ở bước 2, câu riêng cho 17 mã vấn đề 2B, E2E sinh thử QĐ02. Advisor chạy lại typecheck, lint và 1.778 test web trên kết quả merge, xanh; api-client sinh lại không đổi. Phần bổ sung V-ACT và THPTQG (1 điểm) làm sau BE-15.
      - 2026-11-18 16:33 (UTC+7): BE-13 (UNI-2674, PR #100) merge: mẫu THPTQG tách phần, V-ACT đầy đủ, 7 mẫu phạm vi riêng và `VACT_REVIEW`, migration `V202611181305`. Đổi giữa bài chuẩn và bài ôn bằng nạp mẫu sẽ đặt lại điểm tối đa và mốc điểm. Advisor chạy lại 527 test backend trên kết quả merge, xanh.
      - 2026-11-18 16:41 (UTC+7): FE-3a phần thêm (UNI-2663, PR #101) merge: chùm cỡ linh hoạt, ô "Mọi câu con thuộc chủ đề" ở mục và nhóm chùm, dòng tổng nhóm Đúng/Sai. Advisor chạy lại typecheck, lint và test web trên kết quả merge: 1.811/1.813; hai test quá thời gian 5 giây trên Windows (`insert-table`, `structure-view`) chạy riêng đạt 3/3 lần.
      - 2026-11-18 17:43 (UTC+7): FE-5 (UNI-2677, PR #102) merge: bước 1 của web quản trị cho V-ACT và THPTQG, gồm chọn phần, chọn môn, bảng V-ACT toàn bài chỉ đọc và thông báo Ngữ văn. Advisor chạy lại typecheck, lint, sinh API client và test web trên kết quả merge: 1.829/1.829.
      - 2026-11-18 18:03 (UTC+7): FE-3b (UNI-2664, PR #103) merge: phân bổ Bắt buộc/Tham khảo, chỉ tiêu chủ đề, chỉ tiêu cấp bài, phần có mã, HSA Khoa học và bài ôn. Advisor chạy lại typecheck, lint, sinh API client và test web trên kết quả merge: 1.923/1.923.
      - 2026-11-18 18:17 (UTC+7): BE-15 (UNI-2676, PR #104) merge: kiểm tra THPTQG và V-ACT với ba mã mới (chặn công bố, khóa số câu, chờ mapping), sửa lỗi 500 của tổng hợp cấu trúc khi có nhóm lấy phần còn lại. Advisor chạy lại test backend ảnh hưởng trên kết quả merge: 579/579; cloud `verify` 710 test, 0 lỗi.
      - 2026-11-18 18:34 (UTC+7): FE-4 phần thêm (UNI-2665, PR #105) merge: cột Chênh lệch, điểm tối đa từng câu trên đề thử, dòng chờ dữ liệu đối chiếu và câu thông báo riêng cho ba mã kiểm tra mới. Advisor chạy lại typecheck, lint, sinh API client và test web trên kết quả merge: 1.941/1.941.
      - 2026-11-18 14:23 (UTC+7): Duyệt phần bổ sung H-SCA, V-SAT (UNI-2687): thêm BE-16 đến BE-21, FE-6 đến FE-8, tổng đợt 160 điểm. Chốt: chỉ tiêu V-SAT theo lệnh chỉ để đối chiếu trong 2B. Đặc tả và kế hoạch ở `docs/specs`, `docs/plans` ngày 2026-11-18.
      - 2026-11-18 21:51 (UTC+7): FIX-QA (UNI-2686, PR #106) merge: sửa sáu lỗi QA tìm ra (bài ôn của cả bảy kỳ thi, bộ lọc năng lực của nhóm, thang LINEAR, phần tạm bỏ qua, công bố đường tổ hợp). Trước khi triển khai lên cơ sở dữ liệu đã có bài thi phải đếm lại các cấu trúc bị ảnh hưởng (FU-F3-CUTOVER).
      - 2026-11-18 23:48 (UTC+7): BE-16 (UNI-2688, PR #108) merge: mở rộng hợp đồng cấu trúc đề cho H-SCA, V-SAT (audio, phân đoạn hiển thị, điều kiện câu con, khóa dòng), thêm mã kỳ thi, hai nhãn Địa lí và năm mã kiểm tra mới. Mở sóng 2: BE-17, BE-18, BE-20, FE-6.
      - 2026-11-19 01:12 (UTC+7): FE-6 (UNI-2694, PR #109) merge: bước 1 tạo bài cho H-SCA, V-SAT theo mẫu và phạm vi, giữ nguyên các trường mới khi lưu. Màn H-SCA, V-SAT mới kiểm tra trên mẫu giả, QA chạy lại trên mẫu thật.
      - 2026-11-19 02:16 (UTC+7): BE-17 (UNI-2689, PR #110) merge: nguồn câu lọc theo audio, dạng câu con theo vị trí, số phương án và điểm; bộ đếm khả dụng và tra cứu nơi dùng câu hỏi khớp với điều kiện sinh đề.
      - 2026-11-18 20:18 (UTC+7): BE-19 (UNI-2691, PR #111) merge: dữ liệu câu mẫu H-SCA, V-SAT theo đặc tả 4.1 đến 4.3, 5.1, 5.2, 18 file MP3 tổng hợp và trình nạp tệp cho dev, test. Hàng 5 chờ BE-20 hoặc QA.
      - 2026-11-18 21:13 (UTC+7): BE-20 (UNI-2692, PR #113) merge: kiểm tra khóa dòng, dòng lược bị khóa, tệp audio thiếu trên kho, thang điểm có phần bị loại, đối chiếu theo lệnh, tệp đính kèm và phân đoạn hiển thị khi sinh thử. Hàng 5 của BE-19 đã chứng minh trong lane này.
      - 2026-11-18 21:32 (UTC+7): BE-18 (UNI-2690, PR #112) merge: mẫu H-SCA, V-SAT, hồ sơ kỳ thi, tạo bài theo phạm vi, chặn phân đoạn hiển thị trên bài theo phần và bài ôn. Cặp kiểm tra khóa dòng đạt trên code đã có BE-20, BE-21 đưa test này vào repo.
      - 2026-11-18 21:58 (UTC+7): FE-7 (UNI-2695, PR #114) merge: bước 2 tạo bài H-SCA, V-SAT gồm nhãn audio, bảng nhóm theo phần, khóa dòng, điều kiện chùm trộn dạng, ví dụ chấm, bảng mạch đối chiếu. Hàng 6 chờ BE-21, QA chạy lại trên máy chủ thật.
      - 2026-11-19 06:32 (UTC+7): FIX-AUDIO (UNI-2698, PR #115) merge: tệp đính kèm câu hỏi hỗ trợ HTTP Range, proxy web chuyển tiếp Range và phản hồi 206, để trình phát audio tua được.
      - 2026-11-19 07:31 (UTC+7): BE-21 (UNI-2693, PR #116) merge: mẫu V-SAT Tiếng Anh cả môn và các phần P2 đến P4, bài đọc dùng chung câu 8 đến 15, tìm mẫu phần riêng trước khi cắt từ mẫu môn.
      - 2026-11-19 08:07 (UTC+7): FE-8 (UNI-2696, PR #117) merge: trình phát audio trong đề sinh thử, tiêu đề Phần I đến IV theo phân đoạn, năm thông điệp kiểm tra mới và mẫu số đối chiếu theo lệnh.
      - 2026-11-19 09:20 (UTC+7): FIX-RELOAD (UNI-2699, PR #118) merge: nạp lại mẫu giữ thời lượng của đề dùng chung đồng hồ và thang điểm LINEAR; đề thiếu thời lượng không xuất bản được.
      - 2026-11-19 11:47 (UTC+7): QA (UNI-2666, PR #119) merge: E2E đợt 2B xanh trên cơ sở dữ liệu mới (24/24 kịch bản 2B, 83/83 toàn bộ, chạy hai lần), 10 mục chứng minh trên server thật đạt, khóa công tắc phần Viết của mẫu đã lược, tắt nút Công bố khi chưa chọn đường; backend 1767 test và web 2597 test xanh trên cloud.
    - **Tiếp theo:** M.Trường duyệt bản vẽ (G2) và các chỗ cần chốt ở `docs/design/dot-2b/README.md`, `docs/design/dot-3/README.md`; rồi PR. Chưa cook.

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
  - **PBI-10 bản 15:46 và PBI-04 bản 16:23 (2026-11-16):** spec thêm B10: HSA Khoa học chia 17–17–16 theo nhóm tự nhiên, xã hội (thay cách gán tạm theo thứ tự môn); đề của phần tự chọn sinh sau khi học viên xác nhận (đợt lượt thi), nên kiểm tra cấu hình phải chứng minh mọi bộ ba môn (12 trường hợp). Plan sửa A7, BE-4, BE-5a, BE-5b, FE-3a, FE-3c. Bản lưu PBI-04 lên 16:23 (chỉ đổi chữ). Còn chờ BA: môn nhận 16 khi chọn ba môn tự nhiên (đang theo đề xuất chọn ngẫu nhiên).
    - Sửa theo review plan lượt hai (6 điểm): FE-4 xuống sóng 3 và là lane duy nhất nối trình soạn vào các màn; trạng thái kiểm tra kỹ lưu ở bảng `exam_check_job`, FE-3a/FE-3c/FE-2 có phần giao diện và kiểu `ExamCheckRun`; FE-1, FE-2 chuyển test sang giả lập client, QA chỉ xóa mock và giữ phần `/grading` còn import (`questionStore`); hướng dẫn làm bài không nhận ảnh; test duyệt mọi câu trong bộ mẫu; BE-1 cấu hình loại test `perf`. Điểm: FE-1 5, FE-2 6, QA 3. Chưa commit, nằm ở worktree `p-2605b`, nhánh `docs/UNI-2605-sua-plan-review`.
  - **Tiến độ đợt chạy (2026-11-16, 10:45, 17/11):** 100/100 điểm. Đã nghiệm thu: BE-1 (UNI-2607, PR #46), FE-3a (UNI-2608, PR #49), BE-5a (UNI-2611, PR #50), BE-2a (UNI-2609, PR #48 + #51), BE-4 (UNI-2610, PR #52, `00420d2`), BE-2b (UNI-2614, PR #53, `58f3c69`; bucket dùng tiền tố `questions/` trong bucket riêng tư sẵn có thay cho tên `thi360-questions` của spec), FE-3b (UNI-2612, PR #54, `72badb7`), BE-3 (UNI-2615, PR #55, `7617c9e`; nhập ảnh trong tệp để việc sau), FE-3c (UNI-2613, PR #56), BE-5b (UNI-2616, PR #57, `a3a5ae2`), FE-4 (UNI-2617, PR #58, `78f1ec8`), BE-6 (UNI-2618, PR #59, `da11adf`), FE-1 (UNI-2619, PR #60, `f215b14`), FE-2 (UNI-2620, PR #61, `0456b58`). QA (UNI-2621, PR #62, `b89bfd8`). `integration/dot-2` đã vào `develop` qua PR #63 (`8986f0f`, 2026-11-17).
  - **Việc tồn UNI-2634 (PR #64, `5aafb80`, 2026-11-17):** theo trả lời BA của M.Trường ngày 17/11 (khối "M.Trường chốt ngày 2026-11-17" ở mục 12 của đặc tả backend): nhóm chùm Toán HSA ghi `childAnswerType = SINGLE_CHOICE`, chỉ lấy chùm có mọi câu con trắc nghiệm một đáp án, màn ma trận chia sẵn 35/15; luôn đảo câu trong phần và phương án trong câu, bỏ cờ `shuffleOptions`, cờ đảo cột ghép đôi, `BlockShuffleRule`, `groupOrder`, `FIXED_BLOCK_ORDER` (API vẫn nhận và bỏ qua trường cũ); bỏ quy tắc chuyển phần. Bộ đề test giả định theo `docs/specs/2026-11-17-de-test-gia-dinh.md`: dạng bài mới `SENTENCE_COMPLETION` ("Hoàn thành câu"), SP2E Toán 2026 90 phút, kho câu và 27 đề mẫu (14 hợp lệ, 13 lỗi đúng mã) sinh bằng `scripts/generate_test_exam_seed.py`, kiểm bằng `TestExamSeedIT`. Kiểm tra cấu hình thêm mã `SUBJECT_CHOICE_COUNTS_INVALID`, vấn đề thiếu nguồn có số cần/có. Sửa các phát hiện còn tồn của QA, FE-1, FE-2.
    - Sau khi triển khai lên CSDL dev/demo: bấm Kiểm tra lại các bài thi đã kiểm tra (đổi danh mục và bỏ khóa đảo cũ). Chưa có dữ liệu thật, chưa có môi trường thật (M.Trường, 17/11).
    - Để lại: ảnh trong nội dung nhập tệp (luồng nhập chỉ nhận chữ); chữ "(UniNote 11.2)" hiện trên banner bài thi đã công bố (tên tài liệu nội bộ, sửa đợt sau); nhãn tiếng Anh số ít khi đếm 1; G5 (chỉ báo chạy kiểm tra sâu sau khi đổi tab) mới kiểm bằng giả lập.
    - Chưa có xác nhận: dạng "Hoàn thành câu" thay cho phương án 12/12/11; SP2E Toán 2026 90 phút; THPTQG Tin học phần II 6 câu; thứ tự nhóm có luôn đảo không.
- **UNI-2638 trình soạn nội dung đầy đủ (2026-11-17):** đặc tả `docs/specs/2026-11-17-trinh-soan-noi-dung.md`, plan cùng tên; chạy trong đợt `run_29d79f69be23`, nhánh `integration/editor`, 100/100 điểm.
  - Sóng 1: ED-0a backend (PR #69), ED-0b web nền (PR #68). Sóng 2: ED-1 công thức mathlive (PR #73), ED-2 Hóa học, Ký hiệu, Sơ đồ lai (PR #76), ED-3 bảng, hình, đoạn trích (PR #74), ED-4 gạch chân đánh nhãn, chỗ trống đánh số (PR #75), ED-5 dán từ Word với mẫu clipboard Office 2024 thật (PR #72). QA (PR #77): E2E `e2e/tests/rich-text-editor.spec.ts` 7 luồng trên backend thật, test giao diện 128 hàng sáng/tối vi/en; kích thước gói `/questions/new` +23,8 KB gzip so với mốc `8da256cf` (giới hạn 30) sau khi tải mhchem khi cần và tách bộ nhận dạng Word; mã lỗi riêng `QUESTION_BLANK_NUMBER_START_INVALID`.
  - **Cờ "Giữ thứ tự phương án" (UNI-2648, PR #79):** M.Trường chốt 2026-11-17 19:10, đảo một phần BA12b. `body.shuffleOptions` (mặc định `true`) ở câu Một đáp án, Nhiều đáp án và câu con; cảnh báo Sẵn sàng không chặn `OPTION_REFERS_TO_OTHERS` khi phương án nhắc phương án khác ("Cả A và B", "Tất cả các đáp án trên"); cột nhập `keepOptionOrder`. `PaperOrdering` của đợt 2B và lượt thi phải đọc cờ (đặc tả bổ sung mục 5).
  - Test giao diện của lane chạy được trên VPS bằng Chromium headless (stack riêng mỗi lane), dùng khi máy Windows thiếu RAM.
  - Để lại: `reports/ED-QA/follow-ups.md` ở thư mục đợt chạy (32 mục, phần lớn mức thấp). Mức trung bình: FU-06, lưu câu chùm từ trình soạn ghi `passage = content`, câu chùm tạo qua API hoặc nhập có `content` khác `passage` sẽ mất đoạn đọc khi mở ra lưu lại. "Giữ làm hình" khi dán Word để đợt sau theo đặc tả 4.10.
  - Bản demo trên VPS chạy `develop` @ `5f2767c` từ 2026-11-17 (đã bỏ cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK`; Ngân hàng câu hỏi, Bài thi chạy trên backend thật với dữ liệu mẫu và 28 đề test; `/grading` vẫn là dữ liệu mẫu).

### Đợt 3 · Nâng cấp plan thương mại và lượt thi (2026-11-18)

- **UNI-2622**, nhánh `docs/UNI-2622-nang-cap-plan-dot-3` (từ `develop` @ `23c64377`), chưa commit. Plan `docs/plans/2026-11-17-thuong-mai-luot-thi-pbi-05-13.md` nâng cấp theo định dạng zone17-builder: 36 task, 8 sóng, 103 điểm; mỗi task có đường dẫn sở hữu, nghiệm thu đánh mã, ảnh hưởng ngoài task, kế hoạch kiểm thử. `plan-lint` đạt 0 lỗi, 0 cảnh báo.
  - Áp dụng cờ Giữ thứ tự phương án (UNI-2648) và phần bổ sung 2B (V-ACT, THPTQG). Đặc tả lượt thi sửa mục về `shuffleOptions = false`.
  - Báo cáo audit và trạng thái đợt: `.thi360-dev/dot-3/` (ngoài kho mã).
- **2026-11-19:** M.Trường trả lời 17 câu (OQ-1 đến OQ-15, phạm vi H-SCA/V-SAT, reviewer) và chính sách nghe. Câu trả lời ở `.thi360-dev/dot-3/decisions-2026-11-19.md`.
  - Đặc tả bổ sung mới `docs/specs/2026-11-19-dot-3-bo-sung.md` (chưa commit); hai đặc tả TM, LT có dòng trỏ sang. Plan: 43 task, 9 sóng, 129 điểm (thêm AT-6, AT-7, AT-8, FS-7, FA-5, FA-6, LP). `plan-lint` 0 lỗi, 0 cảnh báo.
  - Review độc lập bằng Codex qua Orca, ba vòng (báo cáo ở `.thi360-dev/dot-3/review-1119/`). Vòng 1: 5 blocking, 12 should-fix. Vòng 2: 5 blocking mới. Vòng 3: 0 blocking; 2 should-fix, 1 minor đã sửa; 1 needs-human do M.Trường quyết (lần nghe đã bấm được phát tiếp đến khi phần đóng).
  - M.Trường đã duyệt G1b và G2, bỏ FA-6: nội dung đề không làm song ngữ, chỉ chữ giao diện là song ngữ (ghi ở `DESIGN.md` mục 8 và BS 18). Plan chốt 42 task, 9 sóng, 127 điểm.
  - Danh mục dùng chung vẫn song ngữ trên web học viên (`pickName` vào `@thi360/web-kit`, FS-4).
  - Việc tồn đọng đợt 2B (UNI-2700) đã đối chiếu, ghi ở phụ lục của plan. FU-QA-SEEDS thành cổng **G0c**: dựng lại seed `TEST-*` theo mẫu BA, merge về `develop` trước khi cook, không tính điểm đợt 3.
  - M.Trường quyết UNI-2701 làm ở bước đầu của cook đợt 3: G0c thành **task TS** (sóng 1, 3 điểm), chỉ EX-1, EX-2 và các task sau chúng chờ TS. Plan: 43 task, 10 sóng, 130 điểm; `plan-lint` 0 lỗi, 1 cảnh báo chấp nhận (FS-2 nộp sau FS-1). DS nhận thêm việc tải ảnh mẫu cho câu có ảnh (seed dev chỉ có MP3, FU-BE10-ASSET).
  - **G0b đã qua** ngày 2026-11-19: PR #120 của 2B merge vào `develop` (`1774c74d`). Bản demo trên `bro` đã chạy commit này từ 12:10 (Advisor 2B dựng lại, sao lưu DB trước, 20 migration, smoke đạt). Seed `TEST-*` trên demo còn bản cũ đến khi task TS merge.
  - M.Trường giao 41 việc tồn đọng còn lại của UNI-2700 vào đợt 3 thành **task FU** (8 điểm, sóng 3, sau EX-1, EX-2, TS, không chặn task nào), theo dõi thẳng ở UNI-2700. Plan: 44 task, 10 sóng, 138 điểm; `plan-lint` 0 lỗi, 1 cảnh báo chấp nhận.
  - **Chờ M.Trường:** cho phép commit tài liệu và ra lệnh cook. Chưa commit, chưa cook.
  - Chưa kiểm chứng: các lệnh test trong plan chưa chạy thử.

### Đợt 3 · Cook thương mại và lượt thi (bắt đầu 2026-11-19)

- **Bắt đầu:** M.Trường yêu cầu ngày 2026-11-19 ("commit đi. và cook plan nhé. bộ roster thì vẫn như đợt 2b. chia task cho cả local, bro và cty nhé. tùy tải máy mà chia cho phù hợp"). Kỹ năng zone17-builder giai đoạn cook, Orca Run `run_c1ecbe5f9173`, thư mục run `.thi360-dev/dot-3/` (ngoài repo). Issue cha UNI-2702, issue con UNI-2703..2744, TS = UNI-2701, FU = UNI-2700; bảng task ở `TASK.md`.
- **Nhánh tích hợp** `integration/dot-3` = `develop` `1774c74d` + tài liệu đợt 3. Review độc lập vòng 4 và 5 (Codex) cho phần plan sửa sau khi duyệt; đã sửa ở `12d23578`, `e948ce88`. Quyết định trong lúc cook: B1 thêm `examId` vào `describe`/`reserve` của `EntitlementPort` (`16214331`); E26 mức tối thiểu 10.000 đồng cho sandbox, 5.000 mặc định môi trường thật (`8c43c5f2`).
- **Đã merge vào `integration/dot-3` (2026-11-19):**
  - VP-1 (UNI-2709, PR #121, `171ee506`): đo trên sandbox VNPAY; vector chữ ký cho CM-3b (khóa test tự đặt); TM 8.4, E26, Q12, Q14 ghi kết quả đo. Ngân hàng NCB của sandbox từ chối dưới 10.000 đồng. `querydr` luôn trả `99`, `refund` trả HTTP 500 dù yêu cầu khớp tài liệu (đối chiếu từng trường hai lần): AC-3, AC-12, AC-14 chưa đo, chờ VNPAY trả lời Q9, Q14; VP-2 đo lại.
  - C-0b (UNI-2704, PR #123, `2b72ce26`): profile `e2e`, `MutableClock` và `POST /api/v1/test/clock` (404 ngoài profile `e2e`), `ForbiddenKeysAssert`. Cloud verify 995/995.
  - FS-4 (UNI-2733, PR #122, `4f3820ab`): bộ thành phần câu hỏi 8 dạng trong `packages/ui`, chế độ xem lại có lời giải, ma trận ảnh. Advisor chạy lại web-admin 2156/2156.
  - C-0 (UNI-2703, PR #124, `b64c9e14`): module `commerce`, `attempt`; `EntitlementPort` (6 hàm, có `examId`) và `AttemptStatusProvider`; bit quyền 18–26; `THI360_TOKEN_PEPPER`; giới hạn `GET /api/v1/public/**` 120/phút/IP, miễn IPN VNPAY. Cloud verify 886 + 1016, 0 lỗi.
  - ML (UNI-2705, PR #127, `265ea998`): module `shared/mail`: hàng thư `email_message`, `MailQueue`, mẫu vi/en cho 6 loại thư, worker khóa từng thư, thử lại 1 phút/5 phút/30 phút/2 giờ, hỏng sau 5 lần, dọn sau 30 ngày, `GET /api/v1/test/mail` chỉ ở profile `e2e`. Cloud verify 962 + 1075, 0 lỗi. Đưa đợt 3 lên demo hoặc prod cần đặt `THI360_SMTP_*`. Sau merge mở ID-1 (bro) và ID-2 (cty) chạy song song.
  - CM-4a (UNI-2714, PR #128, `55994db7`): bảng quyền dùng bài thi, `EntitlementPort`, cấp quyền theo đơn, thu hồi quyền chưa dùng, chặn xóa khi còn quyền, hồ sơ học tập. Sau khi ML merge, lane merge lại integration và đổi tên migration thành `V202611191804` cho đứng sau migration của ML. Cloud: backend 2092/0/0, OpenAPI không đổi. Tiến độ 13/138.
  - CL (UNI-2759, PR #135, `b4e575c4`, ngoài plan, AD-34): 52 giá trị của 6 nhóm danh mục cố định thành giá trị hệ thống (migration `V202611191822`): chỉ sửa tên, mô tả, thứ tự; đổi mã/giá trị, xóa, ngừng, chuyển, nhập Excel trùng mã bị từ chối với `CATALOG_ITEM_SYSTEM`. Không đổi API. Cloud backend 967 + 1184, web 2929; Advisor chạy lại test danh mục và đề thi 1067/1067. Chỉ vào `integration/dot-3`.
  - TS (UNI-2701, PR #136, `5e70f8e8`): dựng lại đề test giả định `TEST-*` theo mẫu BA, kèm bước nâng cấp cho DB đã có seed 2B. Cloud backend 980 + 1197, web đạt, E2E 114/114, hồi quy 39/39. Demo chạy `develop`, nên seed mới chỉ lên demo khi đợt 3 vào `develop`. Tiến độ 16/138. Sau merge mở EX-1 (bro) và EX-2 (cty) chạy song song.
  - ID-1 (UNI-2710, PR #137, `9c73a563`): đăng ký tài khoản học viên, xác minh email bằng mã, đổi email; tạo tài khoản Keycloak có đối soát khi không rõ kết quả, khớp theo tên đăng nhập + email vì Keycloak 26 bỏ thuộc tính riêng (AD-55, rủi ro chấp nhận). Migration `V202611192026`. Cloud backend 2237, web 2929, E2E 15/15; Advisor chạy lại trên bản merge với integration mới nhất 108 test tích hợp, 0 lỗi. Việc để lại FU-ID1-R107. Tiến độ 19/138.
  - EX-2 (UNI-2707, PR #138, `f77deccb`): `PaperSolverPort` sinh đề trên mô hình 2B, `PreviewPaperPort` (đọc đề xem trước, kéo dài hạn khi bấm Làm thử, AD-64), hạn sinh đề `THI360_EXAM_SOLVER_GENERATE_DEADLINE` 30 giây, BE spec 5.4. Cloud 1008 unit + 1307 IT, sinh đề chậm nhất 2,37 giây; Advisor chạy lại 74 IT + 9 unit, 0 lỗi. Việc để lại: endpoint làm thử (AT-5), hợp đồng cổng cho AT-1/AT-2b. Tiến độ 21/138.
  - ID-2 (UNI-2711, PR #139, `1250752a`): quên và đặt lại mật khẩu, hồ sơ có `pendingEmail`, ảnh đại diện; migration `V202611192130`, `V202611192131`. Tiến độ 23/138.
  - EX-1 (UNI-2706, PR #140, `ba90c125`): thông tin bán và ảnh bìa, kiểm tra công bố có thông tin bán, chặn công bố theo E24 (AD-60a), `PublishedExamPort` theo phiên bản phục vụ, job dọn ảnh bìa. Migration `V202611192324`, `V202611192325`. Cloud 1.028 unit + 1.429 IT, web 2.929; Advisor chạy lại 198 IT + 13 unit trên kết quả merge. Tiến độ 27/138.
  - CM-1 (UNI-2712, PR #141, `25e2e8f7`): bảng chiếu sản phẩm và API công khai (danh mục, lọc Kỳ thi/Hình thức/Phần-môn, ETag); `PublishedExamPort` thêm tóm tắt dạng trả lời theo phần (AD-77, AD-78). Cloud 1.030 unit + 1.472 IT; Advisor chạy lại 101 IT + 7 unit. Tiến độ 30/138.
  - FS-2 (UNI-2731, PR #142, `db46d807`): web học viên đăng ký, xác minh email, quên mật khẩu, hồ sơ. Advisor chạy lại typecheck, test web-student 145/145, lint trên kết quả merge. Tiến độ 33/138.
- **Đã merge vào `integration/dot-3` (2026-11-20):**
  - FA-1 (PR #144, `ac60b21d`): Advisor chạy lại typecheck, web-admin 2.446/2.449 (3 test quá giờ do máy bận, chạy riêng tệp 108/108), `@thi360/ui` 520/520, lint.
  - DS (PR #145, `9389da7f`): cloud backend 2.531 + web 3.178; Advisor chạy lại 15 lớp IT (68 test) + ModularityTest trên bro, 0 lỗi.
  - CM-4b (UNI-2715, PR #146, `9ce34f90`): quyền dùng bài thi và `/me/exams`; Advisor chạy lại unit 1.031, IT 90, 0 lỗi.
  - HSA-D (UNI-2758, uniworkos UNI-1704, luật MAPID): vào `develop` qua PR #147 (`e6b3b71d`, Advisor chạy lại unit 108, IT 134), demo cập nhật lên `e6b3b71d`, uniworkos 1704 sang In review; đưa sang nhánh tích hợp qua PR #148 (`5bd43aff`, unit 103, IT 228).
  - FS-1 (UNI-2730, PR #149, `1e49a9e5`): trang giới thiệu, danh mục, chi tiết bài thi; Advisor chạy lại typecheck, lint, 271 test web-student trên kết quả merge.
  - CR-4 (UNI-2762, PR #150, `d66469eb`): luật máy chủ giới hạn chùm (1–2 chùm × 3 câu) và tối thiểu câu điền đáp án môn Khoa học HSA, QDA (`SECTION_SET_COUNT_RANGE`, `SHORT_ANSWER_MIN`, `STORED_ID_COLLISION`, `RULES_CHANGED`); cloud BE 2.783 + web 3.451, OpenAPI đồng nhất, hiệu năng `-3,30%` / `+0,25%`; Advisor chạy lại 67 IT + unit + web 51/51 trên bro, 0 lỗi.
  - CR-2 (UNI-2763, PR #151, `6600d6c4`): biểu mẫu Ma trận môn cho QDA, BCA, V-ACT (ô số câu + %, nguồn chùm BCA, phạm vi liên lớp V-ACT, chặn giới hạn R-12 khớp 1:1 với `SubjectFormLimits.java`); cloud web 3.531/3.531, E2E 7/7 + hồi quy 12/12, Jev `005-ac.json` `gate: true` (15/15 yes); Advisor chạy lại độc lập trên cty xanh 100%.
  - HSA-G (UNI-2770 / uniworkos UNI-1707, PR #152 `c69484fb` vào `develop`, forward-merge PR #153 `ada02513` vào `integration/dot-3`): lọc và ẩn cấp MAPID theo phạm vi + chặn lưu xung đột giữa các dòng trên `MapidRuleDialog`; Advisor chạy lại typecheck, lint, 76/76 test trên kết quả forward-merge.
  - CR-3 (UNI-2764, PR #154, `31cc852e`): biểu mẫu Danh sách dòng lấy câu (`ROWS`) cho HSA Văn/Anh, QDA Văn/Anh, BCA Văn, THPTQG, HSCA, V-SAT, SP2E (bảng dòng + sửa dòng + tách chùm + phân bổ đối chiếu riêng/kết hợp); cloud web 3.694/3.694 (378 tệp), hồi quy khóa BE 36/36, E2E 29/29, visual 80/80, 3 vòng FE review CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập typecheck, lint, 153/153 test xanh 100%.
  - FU (UNI-2700, PR #155, `858cb28c`, 8 điểm): đóng 45/45 mục tồn đọng đợt 2B + 12/12 mục bổ sung đợt 3 (quy tắc đếm nhóm dùng chung `BlockCounts`, khóa mẫu giữa kiểm tra và lưu, chỉ tay theo đơn vị môn, `V202611200712` điền UUID phần mẫu trước CR-5, giới hạn họ tên 200 ký tự, kiểm tra điểm ý Đúng/Sai SP2E 0,2, giữ `RAW` trên V-ACT ôn tập, đếm ngược giữ đề thử 30 phút, bọc `<ExamName>` nội dung đề, `Select` tự xuống dòng AD-117); cloud backend 2.822/2.822 (416 lớp), cloud web 3.711/3.711 (379 tệp, 0 cảnh báo lint), E2E 42/42, visual 48/48, cả hai delta review BE + FE CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập 101/101 unit BE, 340 test `@thi360/ui`, 272 test `@thi360/web-student`, typecheck + lint + 123/123 test `@thi360/web-admin` xanh 100%. (Còn chờ BA ngoài phạm vi FU: `FU-RELOAD-EXAM-CLOCK`, `FU-BE7-1`; bàn giao Advisor: `FU-G2B-CANVAS`.)
  - CM-2 (UNI-2713, PR #156, `e82928eb`, 5 điểm): bảng `promotion`, `promotion_redemption`, `promotion_change_log`, `RealPriceCalculator` thay `ListPriceCalculator`, khóa `FOR UPDATE` theo thứ tự unsigned UUID chống deadlock, `POST /api/v1/me/price-quote` (chống dò mã + giới hạn tần suất Redis), `/api/v1/admin/promotions/**` (`capWarnings`, `totalDiscountPaid`, lịch sử thay đổi), nối giá khuyến mãi vào storefront; cloud backend 2.876/2.876, cloud web 3.711/3.711, đỏ trước 7/7, cả 2 vòng review BE (full + delta) CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập Surefire + `ModularityTest` + `MessageBundlesTest` + `@thi360/api-client` typecheck xanh 100%.
  - AT-1 (UNI-2721, PR #157, `11527568`, 5 điểm): bảng `attempt`, `attempt_part`, `attempt_item`, `attempt_event` (`V202611202330`), chuẩn bị lượt thi (`GET /prepare`), bắt đầu + sinh đề bất đồng bộ qua `GenerationExecutor` + đảo câu/đáp án theo hạt giống, đọc trạng thái (`GET /state`), `AttemptGuard`, worker dọn lượt treo `AttemptStaleWorker`, SPI `AttemptStatusProviderImpl` + `AttemptLearningProvider`; cloud backend 2.911/2.911, cloud web 3.711/3.711, E2E 2/2, chứng minh đỏ trước 6/6, cả 2 vòng review BE (full + delta) CLEAR (`0` commit chưa review theo AD-122), đồng bộ sạch với CM-2 (`a415d8eb`); Advisor chạy lại độc lập Surefire + Failsafe (`AttemptPrepareIT`, `AttemptStateIT`, `AttemptSpiIT`) + `@thi360/api-client` typecheck xanh 100%.
  - HSA-F (UNI-2749: UNI-2751/1697, UNI-2752/1698, UNI-2753/1699, UNI-2755/1701, UNI-2756/1702; bỏ UNI-2754/1700 theo ADV:HSA-F:3 — PR #158, `4ab461eb`, ngoài plan): quay lại bước 1 `/exams/new?exam=<id>` để sửa thông tin và đổi chế độ dựng bài (`UpdateExamInfoRequest.buildMode` + `confirmReplace`), gộp Xem trước vào Kiểm tra, làm gọn tiêu đề thẻ môn và bảng phân bổ; cloud backend 2.835/2.835, cloud web 3.753/3.753, E2E 16/16, visual 24/24, review BE delta + FE delta r3 CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập `ExamBuildModeChangeIT` (13/13), typecheck, lint, 107/107 test web-admin xanh 100%.
  - CR-2H (UNI-2765, PR #159, `ac0f96d8`, 2/24 điểm FTM + uniworkos UNI-1706 Sử/Địa AD-98/AD-99 + câu chữ MAPID AD-125 + bàn giao E2E AD-129): bật Ma trận môn cho đủ 6 môn HSA (`TOAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA` — đủ 75/75 cặp môn trên toàn hệ thống), nhúng nguyên trạng bảng quy tắc MAPID của HSA-D/HSA-G, sắp xếp mạch kiến thức trước cấu trúc câu gọn cho Sử/Địa, điều kiện tư liệu động cho Địa lí (`DIA_BAN_DO`, `DIA_ATLAT`); cloud web 3.780/3.780 (383 tệp), E2E sống 24/24 trên 6 spec, visual 40/40, 3 vòng FE review (full + delta r2 + delta r3) CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập typecheck, lint, `subject-form-fit` + `hsa-matrix-form` (40/40 test) xanh 100%.
  - AT-2a (UNI-2722, PR #160, `d75b1369`, 4 điểm): `AttemptLifecycleService` (chuyển phần tuần tự, hoàn thành phần, nộp bài idempotent, đóng lượt dùng chung `SUBMITTED`/`TIME_UP`/`CHOICE_TIMEOUT`/`FAULT_CLOSED`, `confirmUse`, outbox `attempt.finished`, chốt giờ sau `FOR UPDATE` & đóng bù ngoài giao dịch), cổng B1 (`AttemptOverdueCloser`, `PartTransitionHook`, `FinalAnswerWriter`/`NoopFinalAnswerWriter`), `AttemptDeadlineWorker` (10 giây, `FOR UPDATE SKIP LOCKED`, tối đa 10 lô/tick), sửa lược đồ OpenAPI `AnswerValue` sạch lỗi vòng lặp TS2502 (`skipLibCheck: false` 0 chẩn đoán / 0 suy giảm `any`); cloud backend 3.034/3.034, cloud web 3.780/3.780, đỏ trước 6/6, 3 vòng BE review (full + delta r2 + delta r3) CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập 28/28 Surefire + `@thi360/api-client` 5/5 + typecheck 6/6 xanh 100%.
  - FA-2 (UNI-2739, PR #161, `664edae8`, 3 điểm): 4 màn admin Khuyến mãi (`/promotions`, `/new`, `/[id]`, `/[id]/edit`), mở menu `promotions`, BS §10 (`capWarnings`, `totalDiscountPaid`, lịch sử thay đổi), kiểm quyền `PROMOTION.VIEW`/`MODIFY` + `EXAM`/`CLASS`/`USER.VIEW`, khóa trường khi đã dùng, thử giá `price-preview`, xử lý xung đột `412`/`409`, phân trang lượt dùng `redemptionPage`/`redemptionSize`; cloud web 3.840/3.840 (390 tệp, 102/102 test FA-2), E2E sống 20/20 (`promotions.spec.ts` 12/12 + hồi quy 8/8), visual 45 ảnh sạch, 2 vòng FE review (full 4 lens + delta r2) CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập 59/59 test promotions + typecheck + lint xanh 100%.
  - CR-5 (UNI-2766, PR #162, `7fd21bf8`, 3/24 điểm FTM — **hoàn tất 24/24 điểm = 100% FTM**): báo cáo nghiệm thu `docs/acceptance/form-theo-mon.md` phủ đủ 59/59 AC hiệu lực (0 chỗ chờ) kèm tổng hợp việc để lại §5, kịch bản E2E đầu-cuối `e2e/tests/exam-subject-forms-qa.spec.ts`, chuẩn hóa `DESIGN.md` §5.14, làm mới fixture `exam-templates-dot3*.json` hậu-FU (AD-134, 107 mẫu / 254 mục / 75/75 cặp chuẩn) và sửa đa ngôn ngữ `MatrixSetSource` (AD-135); cloud backend 2.978/2.978, cloud web 3.781/3.781 (383 tệp), E2E cấu trúc sống 86/86 trên 13 tệp, visual AC-42 96/96 ô sạch (0 rò UI FTM, 0 tràn ngang FTM, tương phản >= 4,72:1), 2 vòng FE review (full + delta r2) CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập 45/45 test (`subject-form-fit` + `subject-matrix-form`) + typecheck + lint xanh 100%.
  - CM-3a (UNI-2716, PR #163, `29c6cbdf`, 5 điểm): bảng đơn hàng & thanh toán (`V202611210230__purchase_order_and_payment.sql`), tạo/thay thế/thanh toán lại/hủy đơn, đơn 0 đồng cấp ngay, cổng VNPAY giả lập + kiểm tra khởi động (`THI360_VNPAY_MODE` cấm `MOCK` ở `prod`), IPN có kiểm chữ ký & allowlist IP & idempotent, trang quay lại, cấp quyền sau thu tiền + worker thử lại + gửi thư xác nhận, quy tắc BS §14 một khoản được tính mỗi đơn (khoản thu thừa/về muộn gắn cờ đối soát); cloud backend 3.111/3.111 (Surefire 1.216 + Failsafe 1.895), cloud web 3.840/3.840 (390 tệp), đỏ trước 13/13 AC rủi ro cao, 3 vòng BE review (full + delta r2 + delta r3) CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập 19/19 Surefire + `@thi360/api-client` 5/5 + typecheck 6/6 xanh 100%.
  - AT-2b (UNI-2723, PR #164, `f0d05797`, 2 điểm): chọn phương án Phần 3 cho bài HSA đầy đủ (`POST /api/v1/me/attempts/{id}/parts/{partNo}/choice`), sinh đề nền cho phần đã chọn, giữ nguyên hạt giống và lựa chọn khi thử lại (`POST .../retry`), worker quét phần `GENERATING` kẹt quá hạn (`PartGenerationRetryWorker`), đánh số câu nối tiếp từ 101 ở Phần 3, 6 mã lỗi LT §6.1 đồng bộ 3 ngôn ngữ; cloud backend 3.149/3.149 (Surefire 1.216 + Failsafe 1.933), cloud web 3.841/3.841 (390 tệp), đỏ trước ALL RED -> GREEN cho 6 AC rủi ro cao + AC-8 + base revert, 4 vòng BE review (full + delta r2 + delta r3 + merge-delta r4) CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập 8/8 Surefire + `@thi360/api-client` 5/5 + typecheck 6/6 xanh 100%.
  - CM-3b (UNI-2717, PR #165, `04965899`, 3 điểm): cổng VNPAY thật (`RealVnpayGateway`, `VnpayRealSigner`, `VnpayRealModeStartupGuard`, khớp 100% vector VP-1, giới hạn 64 KiB & `readTimeout`, xóa bí mật trên log/raw), migration `V202611210400__payment_next_query_at.sql`, worker `querydr` (`FOR UPDATE SKIP LOCKED`, gọi HTTP ngoài transaction, cửa sổ 15m–24h, ánh xạ đầy đủ TM §8.4 & quy tắc một khoản được tính BS §14), worker đơn hết hạn (`OrderExpiryWorker` phân trang keyset `(expires_at, id)`, trả voucher, xử lý tiền về muộn sau `EXPIRED`); cloud backend 3.177/3.177 (Surefire 1.246 + Failsafe 1.931), đỏ trước 10/10 AC rủi ro cao + base revert, 3 vòng BE review (full + delta r2 + delta r3) CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập 36/36 Surefire xanh 100%.
  - AT-3 (UNI-2724, PR #166, `26db8dac`, 4 điểm): lưu câu trả lời & cờ theo `seq` lớn dần (`AnswerService`, `AnswerValueValidator` cho đủ 6 dạng câu, giới hạn tốc độ lưu `Retry-After`), phiên sửa một thiết bị trên Redis (`EditSessionService`, tiếp quản `takeover = true`, nhịp tim 30s/TTL 90s), nối `finalAnswers` khi nộp/hoàn thành phần, phục vụ tài nguyên câu hỏi của lượt có `Range`/`206`/`416` và `Cache-Control: private, max-age=14400` (`AttemptAssetService`, `QuestionAssetContentPort`); cloud backend R5 3.197/3.197 (Surefire 1.224 + Failsafe 1.973), cloud web 3.841/3.841 (390 tệp), đỏ trước đủ AC rủi ro cao, 4 vòng BE review (full + delta r2..r4) CLEAR (`0` commit chưa review theo AD-122); Advisor chạy lại độc lập 68/68 Surefire + `@thi360/api-client` 5/5 + typecheck 6/6 xanh 100%.
  - FS-3 (UNI-2732, PR #167, `0afe8021`, 3 điểm): màn xác nhận mua `/checkout/[code]` (kiểm tra quyền hiện có `exam-access`, ô voucher `VOUCHER_*` song ngữ, đơn `0 đ`, phát hiện đổi giá `PRICE_CHANGED` & đổi tài khoản `ACCOUNT_CHANGED`, `Idempotency-Key`), kết quả thanh toán công khai `/checkout/result` (`FULFILLED`/`FAILED`/`CHECKING` poll 2s–60s, chữ ký sai `422`, cô lập `404`), Bài thi của tôi `/home` (đủ 8 tình huống nút TM 12, dẫn `/prepare/<entitlementId>`), Đơn của tôi `/account/orders` & `/account/orders/[code]` (tiếp tục thanh toán, hủy đơn qua `ConfirmDialog`, hỗ trợ đơn bộ đề `examId` trống) và mở mục Đơn của tôi trên thanh đầu trang; cloud web R4 400/400 tệp (3.919/3.919 ca, `web-student` 47/47 tệp / 350/350 ca, `build` 2/2), E2E & visual R4 35/35 ca (88/88 PNG ma trận), 3 vòng FE review (`full -> delta -> delta-2`) CLEAR theo AD-122; Advisor chạy lại độc lập `@thi360/web-student` 47/47 (350/350) + `typecheck` xanh 100%.
  - CM-5 (UNI-2718, PR #168, `bcfdde0b`, 5 điểm): quản lý đơn admin & danh sách người mua theo bài (`AdminOrderController`, dải cần chú ý `OrderAttentionSummary`), hoàn tiền toàn phần/một phần & thu hồi lượt chưa dùng + cổng hoàn tiền thật VNPAY + hoàn thủ công `mark-failed` (`RefundService`, `RefundProgressImpl` chốt chặn cấp quyền khi đã hoàn đủ), tiếp tục cấp quyền `ORDER.RECOVER` (`OrderRecoverService`), worker đối soát hàng đêm (`ReconciliationWorker`, `V202611210410`), và xử lý tiền về muộn trên lần đã ghi thất bại (`LateSuccessReviewService`); cloud R3 Backend 3.333/3.333 (Surefire 1.273 + Failsafe 2.060, 18/18 lớp CM-5 xanh), Web 3.841/3.841 (390 tệp), đỏ trước đủ 13/13 AC rủi ro cao, 2 vòng BE review (R1 full + R2 delta) CLEAR theo AD-122; Advisor chạy lại độc lập Surefire + `@thi360/api-client` xanh 100%.
  - AT-6 (UNI-2727, PR #169, `ebefab8d`, 3 điểm): phát audio có giới hạn lần nghe & chống tua trên máy chủ (`AttemptAudioController`, `AttemptAudioService`, `AttemptAudioPlay`, `V202611210500__create_attempt_audio_play.sql`), đọc thời lượng khung MPEG `javazoom:jlayer` không giải mã âm thanh (`AudioDurationReader`), kiểm tệp audio trước khi mở lượt/mở phần tuần tự (`AttemptAudioFileCheck`), truyền phát tệp nghe qua MinIO (`AttemptAssetController`), và tự động đóng lượt nghe đang mở khi khóa phần/nộp bài; cloud R2 Backend 3.408/3.408 (Surefire 1.287 + Failsafe 2.121), Web 3.919/3.919 (400 tệp), đỏ trước đủ 11/11 AC rủi ro cao (47/47 test), 2 vòng BE review (R1 full + R2 delta) CLEAR theo AD-122; Advisor chạy lại độc lập Surefire + `@thi360/api-client` xanh 100%.
  - FA-3 (UNI-2740, PR #170, `5d7ddfa3`, 4 điểm): màn Đơn hàng `/orders` (9 cột, tìm kiếm + bộ lọc ngày múi giờ VN, dải cảnh báo đối soát/tiền về muộn/cần cấp quyền tiếp, thẻ đối soát ngày), Người mua `/orders/buyers` & liên kết từ tab Thông tin bán bài thi, Chi tiết đơn `/orders/[code]` (dòng thời gian kiểm toán dịch theo khóa, Tiếp tục cấp quyền có kiểm lý do, Xem xét tiền về muộn ACCEPT/REFUND, Hoàn tiền API/Ghi nhận thủ công 2 bước kèm `verifiedSystemError` & `Idempotency-Key`, hoàn tiền thừa) và mở mục Đơn hàng trên thanh điều hướng; cloud web R4 408/408 tệp (3.974/3.974 ca, `build` 2/2, quét `A7.10` 0/0/0), E2E & visual R4 24/24 ca (`orders-refund` 4/4 + hồi quy 20/20 gồm `promotions` 12/12 theo AD-155, 32/32 PNG chính thức `AC-21` + 16/16 spot-check), 2 vòng FE review (R1 full + R2 delta) CLEAR theo AD-122; Advisor chạy lại độc lập `@thi360/web-admin` (12 tệp / 86 ca) + `typecheck` (`web-admin`, `e2e`) xanh 100%.
  - AT-8 (UNI-2729, PR #171, `41a9e3f7`, 2 điểm): THPTQG chuyển tự do giữa các phần (`AttemptNavigationPolicy#resolve` cho phép `FREE` trên đề THPTQG thời lượng chung / 1 phần và đồng bộ `/prepare` theo AD-153, mở đồng thời mọi phần sau kiểm tệp audio AT-6, chặn `finish-part` với `409 PART_FINISH_NOT_ALLOWED` qua `AttemptGuard#requirePartFinishAllowed`, lưu tự động đa phần/đa thiết bị và đóng đồng bộ khi nộp/hết giờ); cloud R2 Backend 3.433/3.433 (Surefire 1.295 + Failsafe 2.138), đỏ trước đủ 6/6 ca rủi ro cao, `pnpm typecheck` 6/6, 2 vòng BE review (R1 full + R2 delta) CLEAR theo AD-122; Advisor chạy lại độc lập Surefire 21/21 + `@thi360/api-client` typecheck xanh 100%.
  - VP-2 (UNI-2719, PR #173, `0b2493f6`, 1 điểm): kiểm chứng luồng VNPAY sandbox thật có IPN server-to-server trên bản demo `https://thi360.zone17.click` & `https://admin.thi360.zone17.click` (`VP2-S1..S12`, `VP2-Q1..Q4`: `nginx-s10` 22/22 vhost giữ nguyên mã HTTP, IPN thật từ `113.20.124.26` khớp sạch chữ ký cho cả mua thành công `FULFILLED` và khách hủy mã `24`, không cần sửa `VnpayGateway`, kiểm hoàn tiền `UNKNOWN` + khóa chống hoàn trùng theo AD-116/AD-159, ma trận ảnh `24/24` đạt với 35 PNG, unit VNPAY `37/37`, cập nhật TM §8.4/§9.4 qua 2 vòng BE review CLEAR theo AD-122, quét bí mật `VP2-SCAN-1..3` & `A7.10` = `0`).
  - AT-4 (UNI-2725, PR #172, `6fc8bd9c`, 5 điểm): bộ chấm tự động 8 dạng câu (`ItemGrader`, `ExactRational`, `GradingPolicies`), tổng hợp điểm `ScoreAggregator` (`RAW`/`LINEAR`, V-ACT 4 thành phần + tiểu mục tư duy 12/18 câu), `AttemptFinishedHandler` & `AttemptGradingService` (`FOR UPDATE`, móc sẵn chấm lại cho AT-5), và API học viên `/result`, `/review`, `/me/attempts`, `/solution-assets/{assetId}` kiểm `result_view`; cloud R5 Backend 3.496/3.496 (Surefire 1.327 + Failsafe 2.169), Web 3.974/3.974 (408 tệp), đỏ trước 8/8 ca rủi ro cao, 4 vòng BE review (R1 full + R2..R4 delta) CLEAR theo AD-122; Advisor chạy lại độc lập Surefire 38/38 + `@thi360/api-client` typecheck xanh 100%.
  - CM-6 (UNI-2720, PR #174, `aa66e370`, 3 điểm): backend Bộ đề trọn vẹn (`V202611210600__create_bundle_tables.sql`, 7 API `/api/v1/admin/bundles/**`, bảng chiếu `product_type=BUNDLE` & `BundleEventService` cô lập `REQUIRES_NEW`, khuyến mãi/voucher `BUNDLE`, đơn nhiều bài cấp quyền nguyên tử từng bài, hoàn toàn phần/một phần, `/me/exam-access/query` & `/me/exams` trả `bundles[]`, dữ liệu mẫu `DS-BO-SP2E-3MON` theo AD-154, OpenAPI 3.1 + `@thi360/api-client`); cloud r4 Backend 3.536/3.536 (Surefire 1.327 + Failsafe 2.209, đủ 15 lớp `Bundle*IT` 40/40), Web 3.974/3.974 (408 tệp), đỏ trước 11/11 ca rủi ro cao, E2E `published-exam-set.spec.ts` xanh, 3 vòng BE review (R1 full + R2/R3 delta) CLEAR theo AD-122; Advisor chạy lại độc lập Surefire + `@thi360/api-client` typecheck xanh 100%.
  - LP (UNI-2743, PR #175, `7bf21d7c`, 2 điểm): định kiểu mạnh `StudentLearningProvider.StudentLearningData` (`EntitlementItem`, `AttemptItem`, xóa `*LearningRow` chết) và `StudentLearningResponse` OpenAPI 3.1, cập nhật `EntitlementLearningProvider` (batch `storefront_product` + fallback `PublishedExamPort.findServing`, trạng thái `ACTIVE`/`IN_USE`/`EXHAUSTED`/`REVOKED`) & `AttemptLearningProvider` (chỉ trả `score`/`maxScore` khi `SCORED` và không chấm lại, ẩn `STARTING`/`ABORTED`), nối tab **Hồ sơ học tập** trong `web-admin` (`Table`/`ListCard`, song ngữ `vi`/`en`, `data-exam-content`); cloud R2 Backend 3.553/3.553 (Surefire 1.339 + Failsafe 2.214), Web 3.977/3.977 (408 tệp), đỏ trước AC-1 & AC-2 đạt, 2 vòng BE + 2 vòng FE review CLEAR theo AD-122; Advisor chạy lại độc lập Surefire 24/24 + `learning-profile-tab.test.tsx` 9/9 + `@thi360/api-client` typecheck xanh 100%.
  - AT-5 (UNI-2726, PR #176, `6a6552f2`, 4 điểm): hàng chờ chấm tự luận ẩn danh theo câu (`V202611210700__create_essay_grade.sql`, `/api/v1/admin/grading/**`, số đếm, nhận câu hết hạn 30p, lưu nháp, chốt điểm hoàn tất lượt `PENDING_REVIEW -> SCORED`, mở lại tạo bản mới cập nhật tổng điểm), vận hành lượt (`/api/v1/admin/attempts/**`: danh sách, chi tiết ghi `ATTEMPT_VIEWED_BY_ADMIN`, chấm lại `GRADING_ERROR` giữ bản `FINAL`, đóng lỗi `FAULT_CLOSED`, cấp bù `Idempotency-Key`), và API đề làm thử (`/preview/{previewId}/{trial,paper,grade}` đạt `ForbiddenKeysAssert`, không ghi dữ liệu học tập); cloud R3 Backend 3.601/3.601 (Surefire 1.342 + Failsafe 2.259, 174 endpoint admin), Web 3.977/3.977, đỏ trước 7/7 ca rủi ro cao, 3 vòng BE review CLEAR (0 lỗi mở) theo AD-122; Advisor chạy lại độc lập Surefire 12/12 + `@thi360/api-client` typecheck xanh 100%.
  - FS-6 (UNI-2735, PR #177, `d67dab4f`, 3 điểm): kết quả lượt thi (`/attempts/[id]/result`, tự làm mới khi đang chấm, phân rã tiểu mục Suy luận khoa học V-ACT 12 & 18 câu), xem lại bài (`/attempts/[id]/review`, ẩn đáp án/lời giải khi `PENDING_REVIEW`, ảnh lời giải `/solution-assets/`), lịch sử làm bài (`/history`, lọc theo đề/trạng thái, phân trang, giữ ổn định khi ngoại tuyến `isPaused`) và mở mục `Lịch sử` trên `StudentHeader`; cloud R6 Web 4.005/4.005, Playwright E2E + 80/80 ảnh visual R5, 3 vòng FE review CLEAR (0H/0M theo AD-122), Jev `003-ac.json` `gate=true`; Advisor chạy lại độc lập `@thi360/web-student` `typecheck` + Vitest (51/51 tệp, 378/378 ca) trên `cty` xanh 100%.
  - FB (UNI-2737, PR #178, `85517a06`, 2 điểm): màn admin "Bộ đề" (`/bundles`, tạo/sửa/xếp thứ tự đề, công bố/tạm ẩn/ngừng bán, làm mới khi xung đột 409) và luồng bộ đề học viên (thẻ bộ đề trên danh mục, chi tiết bộ đề `/bundles/[code]` hiển thị tiết kiệm & đề đã sở hữu, mua bộ đề tích hợp checkout/đơn hàng, gom thẻ theo bộ trên `/my-exams`); cloud R5 Web 4.079/4.079 (422 tệp), Playwright E2E (`e2e-regression` 28/28, `bundle.spec.ts` 2/2, `bundle-visual.spec.ts` 16/16 + `extra-states` 32/32 = 104 ảnh PNG), 4 vòng FE review CLEAR (0 lỗi high theo AD-122), Jev `005-ac.json` `gate=true`; Advisor chạy lại độc lập Vitest + `typecheck` trên `bro` xanh 100%.
  - FS-5 (UNI-2734, PR #179, `5e578a36`, 4 điểm): màn chuẩn bị có chọn định hướng Tin học THPTQG (`/prepare/[entitlementId]`, `AD-160`), màn chờ sinh đề, màn làm bài toàn màn hình (`/attempts/[id]`: điều hướng tuần tự/tự do, chọn phần bắt đầu, lưu nháp IndexedDB + hàng đợi tự lưu `save-queue` chịu đứt mạng, cảnh báo ngoại tuyến, tiếp quản phiên đa thiết bị `TakeoverDialog` + `BroadcastChannel` + chuyển tiếp `x-attempt-session` ở BFF proxy theo `AD-165`, tải lười trình soạn tự luận); cloud R5 Web 4.151/4.151 (433 tệp) + R3 Backend 3.607/3.607, E2E & visual R5 32/32 ca + 64/64 ảnh PNG (`AC-26` essay perf `p95 = 26,5ms <= 200ms`, `AC-27` 0/53 chunk `mathlive`), 3 vòng BE review + 5 vòng FE review CLEAR theo AD-122, Jev `001-ac.json` `gate=true` (35/35 AC); Advisor chạy lại độc lập Vitest + Maven + `typecheck` xanh 100%.
  - AT-7 (UNI-2728, PR #180, `9037dacb`, 2 điểm): chính sách chấm trắc nghiệm nhiều lựa chọn nửa điểm H-SCA (`HalfCreditMultipleChoice`, `GradingPolicies.forKind("HSCA")`, giữ chính xác bậc `0,125` tới bước làm tròn cuối trên `numeric(10,4)`), chấm V-SAT trên động cơ BE §8, tổng hợp điểm theo phân đoạn hiển thị `segments[]` (`AttemptSegmentResultBuilder`, `AttemptResultResponse`, xử lý chùm câu tách qua ranh giới phân đoạn theo vị trí câu) và chấm thử H-SCA/V-SAT; cloud R3 Backend 3.632/3.632 (Surefire 1.360 + Failsafe 2.272) + Web 4.151/4.151 (433 tệp), đỏ trước đủ ca rủi ro cao (`AC-1, AC-3, AC-5, AC-6`), 3 vòng BE review CLEAR (0H/0M theo AD-122), Jev `ac` `gate=true`; Advisor chạy lại độc lập Surefire + `@thi360/api-client` `typecheck` xanh 100%.
  - FS-7 (UNI-2736, PR #181, `447d78fd`, 3 điểm): trình phát audio giới hạn lần nghe dùng chung `@thi360/ui` (`AttemptAudioPlayer` + `createTrialAudioAdapter` phục vụ `FA-5`, `plays`/`resume`/`progress` 10s/`end`, dừng tức thì khi `PART_CLOSED`/`TIME_UP`/nộp bài/tiếp quản, trình duyệt chặn phát không mất lượt), thanh chèn ký hiệu hóa học/ion di động `SymbolInputBar` (`44×44px`), bổ sung `AttemptPartStateView.displaySegments` (AD-174) và tiêu đề phân đoạn `Phần I..IV` + chùm câu cắt qua ranh giới trên màn làm bài, chính sách nghe & dạng câu hỏi ở màn chuẩn bị, bảng điểm phân đoạn + huy hiệu `Đúng một phần` ở kết quả/xem lại và thử lại khi lỗi tệp audio; Cloud Web R3 4.205/4.205 (439 tệp) + Cloud Backend R2 3.636/3.636, Live E2E `attempt-hsca-vsat.spec.ts` 6/6 + hồi quy 14/14 + 64/64 ảnh thị giác (AD-175), đỏ trước đủ `AC-1..AC-5, AC-7`, BE review R1 + FE review R1..R3 CLEAR (0H/0M theo AD-122), Jev `ac` `gate=true` (19/19); Advisor chạy lại độc lập `@thi360/ui`, `@thi360/web-student` và Surefire xanh 100%.
  - FA-4 (UNI-2741, PR #182, `a6ef4908`, 3 điểm): nối `/grading` & `/grading/[itemId]` sang API chấm ẩn danh thật (khóa nhận câu `409 GRADING_CLAIMED`, lưu nháp/khóa lạc quan `412 VERSION_CONFLICT`, chấm lại & mở lại bắt buộc lý do, đếm từ `countWords`), gỡ sạch 100% lớp mock theo B8 (`THI360_GRADING_MOCK = 0`, xóa `lib/mock/**`, `MockDataBanner`, `MockupPlaceholder`), xây `/attempts` & `/attempts/[id]` (dòng sự kiện song ngữ `vi/en`, đánh dấu hỏng, chấm lại `GRADING_ERROR`, cấp bù 1 lần kèm `Idempotency-Key`) và màn làm thử như học viên `/exams/[id]/preview/[previewId]/try` ngoài khung tab (`exams/[id]/(editor)/**`, chỉ gọi `GET .../paper` không lộ đáp án, nộp `POST .../grade` không sinh lượt thật); Cloud Web R5 4.212/4.212 (441 tệp), Live E2E 7/7 đặc tả (27/27 ca) + 48/48 ảnh thị giác sạch lỗi, 4 vòng `reviewer_fe` (R1 full + R2..R4 delta) CLEAR (0H theo AD-122), Jev `ac` `gate=true` (35/35); Advisor chạy lại độc lập `@thi360/web-admin` `typecheck` + 12 tệp / 97 ca Vitest trên `cty` xanh 100%.
  - FA-5 (UNI-2742, PR #183, `28bd7da2`, 2 điểm): chuyển `ExamPreviewPaper` sang thành phần câu hỏi dùng chung `@thi360/ui/question` chế độ `review` cho đủ 8 dạng câu (giữ đầy đủ siêu dữ liệu quản trị, phân đoạn `displaySegments` và thứ bậc tiêu đề `headingLevel` `1..6`), gắn `AttemptAudioPlayer` + `createTrialAudioAdapter` của FS-7 vào màn làm thử như học viên (tối đa 2 lần nghe trong bộ nhớ trình duyệt, tự dừng khi đổi tệp/đổi phần/nộp bài/rời trang, stream `/api/v1/admin/question-assets/{id}`, không ghi DB), sửa sạch cảnh báo React `key` của `richNodes()` và bổ sung E2E sinh thử đủ 9 kỳ thi + ma trận thị giác 24 ô (`exam-preview-nine-kinds.spec.ts`); Cloud Web R3 4.288/4.288 (450 tệp), Live E2E 9/9 ca (3 đặc tả) + 24/24 ảnh FA-5 + 16/16 ảnh hồi quy sạch lỗi, 2 vòng `reviewer_fe` (R1 full + R2 delta) CLEAR (0H theo AD-122), Jev `ac` `gate=true`; Advisor chạy lại độc lập `@thi360/ui` + `@thi360/web-admin` `typecheck` & Vitest trên `local` xanh 100%.
  - QA (UNI-2744, PR #184, `3c5f95d8`, 4 điểm): nghiệm thu tổng thể Đợt 3 (`0` tệp mã nguồn sản phẩm bị sửa) — bổ sung 8 đặc tả E2E xuyên task (`qa-guest-purchase-hsa`, `qa-voucher-100`, `qa-bca-essay`, `qa-refund-revoke`, `qa-failure-paths` 5 kịch bản lỗi, `qa-visual-sweep` 12 màn × 8 ô = 96/96 PNG, `qa-nine-kinds` đủ 9 kỳ thi trên bộ DS gồm `DS-THPTQG-INFORMATICS-MINI` `FREE`, `qa-payment-rules` BS 14 thu thừa), 4 kiểm thử tích hợp bảo mật (`StudentApiForbiddenKeysIT`, `MeApiIdorIT`, `PublicRateLimitIT`, `TestApiProfileGuardIT`), khôi phục fixture `ExamAudioPerfIT` (`102 ms VALID`), chia 3 mảnh `.github/workflows/e2e.yml` (< 14 phút/mảnh << 25 phút CI), và đồng bộ `README.md`, `ARCHITECTURE.md`, `DESIGN.md` (`THI360_GRADING_MOCK` = 0); Cloud Backend R3 3.653/3.653 thô (3.649 duy nhất, OpenAPI 0 diff, `-Dgroups=perf` 12/12 PASS; `PaperGenerationPerfIT` 94,89s > 30s leo thang M.Trường theo Plan dòng 5922 để mở task tối ưu MAPID solver riêng), Cloud Web R3 4.288/4.288 (450 tệp, `api-client` 0 diff), Full Built E2E 3 mảnh 364/364 ca thực thi PASS (19 skip theo build mode = 383 tổng), `reviewer_be` R2 delta & `reviewer_fe` R2 delta đều `CLEAR` (AD-122); báo cáo nghiệm thu tại `.thi360-dev/dot-3/qa-report.md` và danh sách câu hỏi mở TM §15 / LT §14 / BS §19 đã đăng lên `UNI-2702` (AC-17).
- **Tạm dừng (2026-11-20 ~13:40 UTC+7, M.Trường yêu cầu "dừng tất cả các lane nhé, đặt check point"):** tắt lịch Advisor, đặt cờ `automation/STOP`. Năm lane dừng ở điểm an toàn, mọi commit đã push, mỗi lane có checkpoint kèm các bước chạy lại:

  | Lane | Issue | Head | Còn lại khi chạy lại |
  |---|---|---|---|
  | CR-4 | UNI-2762 | `0992d21a` | Kiểm tra lại sau khi gộp 1e49a9e5, cloud, đo hiệu năng, PR |
  | CR-2 | UNI-2763 | `c1dc52d0` | Chờ CR-4 merge, gộp, cloud lần cuối, PR |
  | CR-3 | UNI-2764 | `4382b8b4` | Sửa F1/F2, cloud, E2E, visual, review, PR |
  | FU | UNI-2700 | `ba4948de` | Gộp 1e49a9e5, visual, review, PR |
  | HSA-F (ngoài plan, BA vòng 2: 1697, 1698, 1699, 1701, 1702) | UNI-2749 | `fec7e690` | Phần nối giao diện đổi cách tạo đề (1697) còn trong worktree chưa commit; sau đó cloud, visual, review, PR, lên `develop` và demo |

  Bàn giao và cách chạy lại: `.thi360-dev/dot-3/reports/handover-2026-11-20-pause.md`. Chưa mở: CR-2H (UNI-2765, gồm phạm vi uniworkos UNI-1706), CM-2 và các task sau.
- **Sự cố 2026-11-20 13:05:** Advisor dọn worktree đã merge trên máy cty làm xóa nhầm worktree của CR-2, CR-3 đang chạy (AD-108). Commit đều đã nằm trên origin; đã dựng lại worktree, chỉ mất một lần gộp chưa commit và một lượt E2E.
- **Form theo môn (FTM):** đặc tả và plan bản 4 qua 3 vòng review Codex, `plan-lint` 0 lỗi; 6 task, 24 điểm, issue UNI-2761 … UNI-2766. M.Trường 2026-11-19: "Còn plan thì cứ làm hết rồi deploy", Advisor coi là duyệt G2. Mở CR-1 và CR-4 trước; FTM lên `develop` cùng đợt 3. CR-1 merge PR #143 (`52168620`, 2026-11-20): bảng đăng ký 75 cặp môn (đều tắt), chọn biểu mẫu, điều hướng, chặn lưu; Advisor chạy lại typecheck, 1.036 test web-admin, lint trên kết quả merge. CR-4 merge PR #150 (`d66469eb`, 2026-11-20), CR-2 merge PR #151 (`6600d6c4`, 2026-11-20), CR-3 merge PR #154 (`31cc852e`, 2026-11-20), CR-2H merge PR #159 (`ac0f96d8`, 2026-11-21), và CR-5 merge PR #162 (`7fd21bf8`, 2026-11-21), **hoàn tất FTM 24/24 điểm (100%)** (75/75 cặp môn chuẩn đã bật; danh sách việc ngoài phạm vi để lại ghi ở `docs/acceptance/form-theo-mon.md` §5: dọn dữ liệu nháp cũ `STORED_ID_COLLISION`, cuộn ngang `NavTabs`/`ScrollableTable` ở khung ngoài `subject-forms` tại 390px, bản dịch `question_tag.name_en` dòng 1–40 của devdata, và một số unit test nhỏ).
- **Ngoài plan:** năm lane sửa lỗi HSA (uniworkos UNI-1695, UniAI UNI-2749), bảng ở `TASK.md`. Xong lane nào thì merge vào `integration/dot-3`, cherry-pick sang hotfix từ `develop`, PR vào `develop` và dựng lại bản demo (M.Trường đồng ý 2026-11-19). 2026-11-19 17:46 (UTC+7): HSA-C merge PR #125 (`ed52d43e`); Advisor chạy lại typecheck, lint, test web phần đề thi trên `27573141` (5 test quá giờ do máy bận, chạy lại 4 tệp: 102/102); cherry-pick vào `develop` là PR #126, đã merge (`fd67d223`, web 2200/2200), demo đã cập nhật lên `fd67d223`. 18:25: HSA-A merge PR #129 (`8c9eb3e6`); Advisor chạy lại typecheck, lint, test đề thi 901/901; vào `develop` qua PR #130 (`e0240ffd`, web 2235/2235), demo cập nhật theo. 18:50: HSA-E merge PR #131 (`cf789ed9`); Advisor chạy lại typecheck, lint, test đề thi 906/906; vào `develop` qua PR #132 (`9b8a7e2e`, web 2240/2240); demo chạy `develop` `9b8a7e2e`. 19:37: HSA-B merge PR #133 (`469c034e`); Advisor chạy lại typecheck, lint, test đề thi 938/938 trên kết quả merge; vào `develop` qua PR #134 (`12ec91a8`, backend verify trên bro 1805/0, web 2272/2272); demo chạy `develop` `12ec91a8`, Flyway thêm `202611190100`. Còn lại của UNI-1695: HSA-D (UNI-1704) tạm dừng, gộp vào đặc tả form theo môn. Mở HSA-E (UNI-2760, uniworkos UNI-1705: cột lệch ở bước Kiểm tra và Xem trước) trên cty.
- **Advisor:** từ 16:15 ngày 2026-11-19 chuyển sang phiên chat mới theo yêu cầu của M.Trường; checkpoint bàn giao ở `.thi360-dev/dot-3/reports/handover-2026-11-19.md`, checkpoint tạm dừng ở `handover-2026-11-20-pause.md`.
- **Vướng:** không còn vướng bên ngoài (M.Trường đã chốt đội hình chỉ dùng `agy` CLI theo AD-110 và chốt đủ 3 điểm VNPAY Q9, Q14 theo AD-116: SIT chỉ mở IPN và khóa `querydr`/`refund` mặc định nên backend cài đủ 2.1.0 theo tài liệu + `vectors.json` + hoàn thủ công C14, IP allowlist đọc từ biến môi trường, bảng mã hoàn tiền khóa theo TM §9.4 chống hoàn hai lần).
- **Tiến độ:** **138/138 điểm (100% — hoàn tất trọn vẹn 44/44 task Đợt 3!)**; **FTM 24/24 điểm (100% — hoàn tất)**; cùng 7 lane HSA ngoài plan (`UNI-2749`, `UNI-2770`) và `CL` (`UNI-2759`). Đã merge **QA (`UNI-2744`, PR #184 `3c5f95d8`, 4 điểm)** vào `integration/dot-3`, xóa sạch toàn bộ worktree thành phần trên cả 3 máy (`local`, `bro`, `cty`), đăng danh sách câu hỏi mở (AC-17) và số đo `PaperGenerationPerfIT` lên `UNI-2702`, chuẩn bị mở PR gộp `integration/dot-3` vào `develop` và dựng lại bản demo.

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
