# Plan triển khai: Biểu mẫu thiết lập theo môn (FTM)

> **Trạng thái:** đã duyệt (G2) · **Phiên bản:** 4 (sửa theo review vòng 3, G1 và đổi phạm vi UNI-1704) · **Đặc tả:** `docs/specs/2026-11-19-form-theo-mon.md` (FTM) phiên bản 4
>
> **Theo dõi:** thay đổi yêu cầu trong đợt 3, issue cha UNI-2702. Issue UniAI: CR-1 UNI-2761, CR-4 UNI-2762, CR-2 UNI-2763, CR-3 UNI-2764, CR-2H UNI-2765, CR-5 UNI-2766. UNI-2758 (uniworkos UNI-1704) thuộc lane HSA-D từ bản 4 (AD-67, AD-68); FTM không đóng, không bình luận issue đó.
>
> Các mã `R-n`, `AC-n`, `G1-n` ở Phần A–C là của FTM. Trong mục Nghiệm thu của Phần D, `AC-n` là tiêu chí của task, còn tiêu chí của đặc tả viết `FTM AC n` (không gạch nối) để `plan-lint` không lẫn. Mã của task ghi `CR-n AC-n`. Mã có tiền tố khác thuộc tài liệu đó: K, H, R- của 2B; AD-n của sổ quyết định Advisor; EX-1, EX-2, ID-2, FA-1, FU là lane của plan đợt 3.

# Phần A: Cấu hình đợt chạy

## A1. Kho mã và nhánh

- Kho `UNIAI-TEAM/thi-360`. Plan này chạy trong đợt 3, trên nhánh tích hợp `integration/dot-3`. Gốc: đầu `integration/dot-3` lúc mở lane đầu tiên (lúc viết plan là `f5df9654`).
- Nhánh task: `feature/UNI-<issue>-<ten-ngan>`, PR vào `integration/dot-3`.
- Không có PR cuối riêng: các task theo cùng PR cuối của đợt 3 (`integration/dot-3` → `develop`).
- Đưa sang `develop`: FTM lên cùng đợt 3 (G1-8, A3). HSA-D tự đi `develop` trước theo AD-22 rồi forward-merge vào `integration/dot-3`.

## A2. Cổng chặn

| Gate | Điều kiện | Trạng thái |
|---|---|---|
| G1 | Các câu G1 có quyết định (FTM mục 12, Quyết định G1); đặc tả cập nhật theo đó | đạt (2026-11-19 23:55, AD-67) |
| G2 | M.Trường duyệt plan này; `plan-lint` 0 lỗi | đạt (2026-11-20, chỉ đạo "cứ làm hết rồi deploy"; `plan-lint` 0 lỗi) |
| EX-1 | Lane EX-1 (kiểm tra và công bố theo đường, đợt 3) đã merge vào `integration/dot-3` | đang chạy |
| EX-2 | Lane EX-2 (xem trước, bộ giải, đợt 3) đã merge vào `integration/dot-3` | đạt (PR #138, `f77deccb`, AD-66) |
| HSA-D | Lane HSA-D (UNI-2758, quy tắc MAPID của HSA) đã forward-merge vào `integration/dot-3` (PR 2 của pha 0 v2) | đang chạy |

G1 đã có quyết định cho cả chín câu (FTM mục 12): bốn câu do UNI-1704 bản BA 23:52 / AD-68 trả lời, các câu còn lại theo đề xuất. Câu nào chỉ mở lại khi người duyệt yêu cầu. Cả sáu task chờ G2. **Trước G2 không mở lane, không viết code**; chỉ đọc nguồn để chuẩn bị. EX-1, EX-2, HSA-D là mốc ngoài plan: CR-4 nộp sau EX-1, EX-2; CR-2H mở sau HSA-D (C1). M.Trường 23:55: "Còn plan thì cứ làm hết rồi deploy"; Advisor mở lane sau G2.

## A3. Ủy quyền

- Như đợt 3 (`docs/plans/2026-11-17-thuong-mai-luot-thi-pbi-05-13.md` A3): lane commit, đẩy nhánh task, mở PR vào `integration/dot-3`; Advisor nghiệm thu và merge.
- **Đưa sang `develop`:** mặc định **không** cherry-pick task FTM nào; FTM lên `develop` cùng đợt 3 (G1-8). AD-22 chỉ ủy quyền sửa nóng trong cây UNI-2749; HSA-D dùng ủy quyền đó cho UNI-2758, FTM không dùng.
- Việc sửa nóng (HSA-D và các lane AD-22 khác) nằm ngoài tổng điểm FTM; FTM không có lát demo riêng.
- **Migration:** FTM không có migration (B3). Nếu sau này có yêu cầu thay thế được duyệt cần migration: migration thông thường, dấu thời gian muộn nhất tại lúc mở PR; với sửa nóng hai đích theo cửa sổ AD-37 và `team-rules.md` (mục thứ tự Flyway); không dùng `outOfOrder` ở prod, không sửa lịch sử.

## A4. Theo dõi

- Mỗi task một issue UniAI con của UNI-2702, tạo sau G2. Lane trên cty và bro không chạy `uniai`; Advisor ghi theo dõi.
- UNI-2758 thuộc HSA-D. CR-2H ghi trong báo cáo lane SHA của HSA-D đã forward-merge mà nó nhúng.

## A5. Sổ tiến độ

Total weight: 24

Lịch sử: plan v2 (sau review vòng 1) 22 điểm: CR-1 5, CR-2 5, CR-3 6, CR-4 3, CR-5 3. Plan v3: 23 (CR-4 3 → 4: dấu kiểm cả bài phối hợp EX-1, ánh xạ mã và lý do trên web, IT chuỗi kiểm). Plan v4: 24. UNI-1704 (luồng MAPID, phân loại bài cũ, so tập ứng viên) sang HSA-D; MT tách hai task theo quyết định Advisor 2026-11-20: CR-2 (QDA, BCA, V-ACT) 3 điểm và CR-2H (HSA, nhúng HSA-D) 2 điểm, cộng 5 thay cho 4 vì CR-2H là lane riêng có cổng test, review, rebase lên HSA-D và E2E sinh thử của riêng nó; CR-1 5 → 6 vì "thay đổi chưa lưu" gồm nhập thô, nối với cảnh báo rời trang, hộp chuyển mục, đổi mẫu (review P3-01). CR-3 giữ 6 (thêm khóa tách chùm linh hoạt, số dương, `shuffleUnits`, fixture % riêng); CR-4 giữ 4 (dấu độc lập kết luận tổng, ca E24). G1-9 (A): giữ mẫu số đã duyệt 138 của đợt 3, báo FTM riêng 24, tổng phạm vi 162 (G1-9 duyệt với 23/161 lúc tạm tính; số đổi vì tách CR-2H). HSA-D không cộng vào FTM. Đường dài nhất ở C3 là trọng số nội bộ, không phải lịch; ngày và trạng thái EX-1, HSA-D theo dõi riêng ở sổ của Advisor.

## A6. Môi trường

- Theo đợt 3 (A6 của plan đợt 3): Node ≥ 22, pnpm 10, JDK 21, Docker.
- Lệnh chuẩn:
  - Web: `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build`
  - Một tệp test web: `pnpm --filter @thi360/web-admin test <tệp>`
  - Backend: `cd backend && ./mvnw -B -ntp verify`; một lớp IT: `./mvnw -B -ntp verify -Dit.test=<Lớp> -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
  - E2E một tệp: `pnpm --filter @thi360/e2e test tests/<tệp>`
  - E2E bước Cấu trúc (chạy trong thư mục gói, bộ lọc là regex đã quote): `pnpm --filter @thi360/e2e exec playwright test 'tests/exam-.*[.]spec[.]ts$' --list` để xác nhận danh sách, rồi chạy cùng lệnh bỏ `--list`; báo cáo ghi số test chạy, đạt, bỏ qua.
- Bộ test đầy đủ chạy trên cloud runner (profile `thi360`). E2E và kiểm tra giao diện chạy trên stack riêng của lane (compose project `thi360-<task>`).
- Máy và tải: theo `team-rules.md` của run (cổng tải, `mvn.lock`).

## A7. Nghiệm thu chung

A7 của plan đợt 3 áp nguyên văn, cộng thêm:

1. Mỗi `CR-n AC-n` có test nêu ở Kiểm thử đã chạy; báo cáo lane ghi lệnh, log, SHA, fixture (kèm SHA của fixture) và kết quả.
2. Task rủi ro **cao** có bằng chứng đỏ trước cho tiêu chí đánh dấu "(đỏ trước)": test viết trước, chạy đỏ trên SHA gốc, ghi log. Bất biến vốn đã đúng (ví dụ MD không đổi) ghi "xanh trước" thay cho đỏ trước.
3. Màn web: kiểm ở sáng và tối, vi và en, 390px và 1280px; không trộn ngôn ngữ (review giao diện soát nhãn lẫn ngôn ngữ và cả hai chế độ màu). Ảnh mỗi ô của ma trận kiểm tra, tên tệp ghi SHA.
4. Biểu mẫu mặc định (MD) không đổi hành vi: test hiện có của `exam-structure-subject-panel`, `block-editor`, `review-subject-panel` xanh mà không sửa assert. Test hiện có chỉ được đổi theo bảng B5; ngoài bảng phải nêu lý do trong PR và được Advisor duyệt.
5. Mỗi cờ biểu mẫu chỉ bật trên `integration/dot-3` khi lát cắt tương ứng đủ AC (B7).

# Phần B: Quy ước kỹ thuật

## B1. Hợp đồng chốt trước (CR-1 làm)

- Thư mục mới `apps/web-admin/src/features/exams/subject-forms/`.
- `registry.ts`: `subjectFormFor(kindCode, subjectCode) → SubjectFormEntry | null`. Tra bằng mã canonical; bí danh chỉ đổi mã tra ở kỳ có mã canonical tương ứng (FTM R-1); không trả hay ghi lại mã đã đổi. Mỗi mục có cờ `enabled` riêng để bật theo lát cắt:

  ```ts
  type SubjectFormKind = "MATRIX" | "ROWS";
  interface SubjectFormEntry {
    form: SubjectFormKind;
    enabled: boolean;                 // bật theo lát cắt (B7)
    percentEntry: "NONE" | "TWO_COLUMNS" | "COUNT_OR_PERCENT";        // R-9
    enforcement: "FIXED_REQUIRED" | "DECLARED_REQUIRED" | "FIXED_REFERENCE" | "REFERENCE_WITH_APPLY"; // R-8
    combinedMatrix: "NONE" | "PART_LEVEL";                            // R-10
    hideGrade: boolean;               // R-11
    gradeOptional: boolean;           // V-ACT Tiếng Việt (R-4)
    gradeMixedColumn: boolean;        // V-ACT Toán
    setCount?: { min: number; max: number; size: number };            // R-12
    shortAnswerMin?: number;          // R-12
    flexibleSets: boolean;            // V-ACT Toán (H28)
    mapidFlow: "HSA_RULES" | "SCOPE_AND_QUOTA" | "NONE";              // R-5 (nhúng HSA-D), R-6
    setSource: boolean;               // "Nguồn chùm" (R-3)
    rowsEditable: "NONE" | "ADD_AND_SPLIT";                           // R-7
    readOnlyNotice?: "ESSAY_SKIPPED"; // THPTQG Văn
  }
  ```

- `selection.ts`: `selectSubjectForm(exam, section, draft) → { form: "REVIEW" | "MATRIX" | "ROWS" | "DEFAULT"; notice: boolean; reason?: FitReason }` theo thứ tự FTM R-1 (bài ôn trước, theo `exam.buildMode`). Tính một lần khi mở bước Cấu trúc hoặc nạp mẫu (sau HSA-C và chuyển đổi MAPID của HSA-D), lưu trong trạng thái của view, không tính lại khi bản nháp đổi (R-2); Bỏ thay đổi hay nạp mẫu đã xác nhận thì tính và ghim lại.
- `fit.ts`: `fitsForm(section, entry, context) → { fits: true } | { fits: false; reason: FitReason }` (R-2, gồm ngoại lệ dữ liệu MAPID của HSA ở R-2/R-5, chùm cỡ cố định ≤ 0, R-11 điều kiện lớp Bắt buộc). Không được nới để test xanh (B8).
- `navigation.ts`: `formTarget(selection, blockId | issue) → { kind: "MATRIX_FIELD"; field } | { kind: "ROW"; blockId } | { kind: "LEGACY_BLOCK"; blockId }`. CR-1 nối vào `?block=`, bấm lỗi, bấm tóm tắt ở `exam-structure-view.tsx`; CR-2, CR-3 điền ánh xạ đích cho dạng mình.
- `raw-input-store.ts`: trạng thái nhập thô theo `sectionId`/`blockId` (giá trị đã gõ mà chưa ghi được vào bản nháp, ví dụ tỷ lệ quy ra không chia hết cỡ chùm). Sống theo phiên của view, không vào DTO, không mất khi đóng màn sửa dòng; "Bỏ thay đổi" hay nạp mẫu đã xác nhận xóa đúng phần bị bỏ. Có `hasRawInputs()`; CR-1 dựng `hasUnsavedChanges = draftDirty || hasRawInputs()` và dùng nó cho cảnh báo rời trang (`useUnsavedChangesGuard`), hộp chuyển mục, `StructureVersionPicker`, chuyển tab (FTM R-15 "Thay đổi chưa lưu"); `draftDirty` giữ nghĩa cũ cho danh sách thay đổi.
- `blocking.ts`: `formBlockingIssues({ exam, draft, pinnedForms, rawInputs }) → BlockingIssue[]`. Chỉ xét mục có biểu mẫu ghim là MT/DS (mục MD không bị xét), trên **bản nháp của mọi môn** cộng trạng thái nhập thô, không trên component đang mở. CR-1 gọi guard: trước mọi `PUT` (Lưu, Lưu và chuyển, tự lưu) và **trước Kiểm tra / Tiếp tục kể cả khi `dirty = false`** (`runCheck` hôm nay đi thẳng khi không dirty). Còn lỗi thì không gửi `PUT` hay kiểm tra, đưa tới đích của lỗi đầu tiên qua `navigation.ts` và đặt focus. Danh sách luật là một mảng; CR-2, CR-3 mỗi task thêm một phần tử trỏ tới tệp luật của mình.
- `limits.ts`: bảng giới hạn R-12 phía web, đọc từ `registry.ts`.
- Thành phần dùng chung: đầu môn (`subject-form-header.tsx`), điều khiển mức áp dụng (`enforcement-control.tsx`, R-8, có trạng thái khóa kèm lý do), ô số câu / tỷ lệ (`count-percent-input.tsx`, R-9, dùng hàm quy đổi `percent.ts`), dòng thông báo MD (`full-form-notice.tsx`), chữ lý do khóa (`lock-reason.tsx`).
- Đổi chữ ký của B1 sau khi CR-1 merge phải qua Advisor.

## B2. Tệp chung, người sửa duy nhất, thứ tự hợp nhất

| Tệp hoặc vùng | Người sửa duy nhất | Task khác được |
|---|---|---|
| `subject-forms/registry.ts` | CR-1 | CR-2, CR-2H, CR-3: chỉ sửa hàng cấu hình và cờ `enabled` của mục mình, kèm test |
| `subject-forms/selection.ts`, `fit.ts`, `navigation.ts`, `blocking.ts`, `limits.ts`, `percent.ts` | CR-1 | CR-2, CR-2H, CR-3: thêm ánh xạ đích và một phần tử luật chặn lưu; thêm lý do không khớp của dạng mình qua Advisor |
| `components/exam-structure-view.tsx` | CR-1 | không |
| `components/exam-structure-subject-panel.tsx`, `block-editor.tsx` (MD) | không task nào đổi hành vi | CR-2, CR-3: tách hàm hoặc thành phần con ra tệp riêng để dùng lại, không đổi hành vi, test MD giữ nguyên |
| `components/knowledge-scope-field.tsx`, `topic-quota-table.tsx` | CR-2 (chỉ cho R-6, kỳ không phải HSA) | CR-3: chỉ dùng. HSA-D có thể sửa cho HSA: lane merge sau hợp nhất, chạy lại test của cả hai |
| Thành phần quy tắc MAPID của HSA-D (`mapid-rules.ts`, `mapid-rules-table.tsx`, `mapid-rule-dialog.tsx`), phần HSA-D gắn vào `exam-structure-subject-panel.tsx` | HSA-D | CR-2H: chỉ nhúng, không sửa; cần thêm prop thì gửi HSA-D (đã đóng thì qua Advisor); giao diện ở B9 |
| `components/block-conditions.tsx`, `block-set-conditions.tsx`, `block-matrix-editor.tsx`, `block-switches.tsx` | CR-3 | CR-2, CR-2H: chỉ dùng |
| `subject-forms/matrix-form.tsx`, `matrix-form-model.ts`, `matrix-blocking.ts` | CR-2 | CR-2H: thêm phần HSA qua chỗ gắn theo kỳ, không đổi hành vi QDA, BCA, V-ACT |
| `apps/web-admin/src/messages/{vi,en}.json` | mỗi task một khối: CR-1 `exams.subjectForm.common`, CR-2 `exams.subjectForm.matrix`, CR-2H `exams.subjectForm.matrix.hsa`, HSA-D `exams.mapidRules`, CR-3 `exams.subjectForm.rows`, CR-4 `exams.review.issue.SECTION_SET_COUNT_RANGE`, `…SHORT_ANSWER_MIN`, lý do `RULES_CHANGED` | thêm khối riêng, sắp theo khóa |
| `backend/src/main/resources/messages*.properties` | CR-4 (khối của mình) | EX-1, ID-2, FU cũng thêm: lane merge sau hợp nhất đúng ba tệp, chạy `MessageBundlesTest` trên SHA ghép |
| `ExamCheckRules.java`, `DefaultExamChecker.java` | CR-4 (hai luật, phần đọc mã kỳ và `buildMode`) | FU sửa phần kiểm tra đề: lane merge sau rebase và chạy lại hồi quy |
| `ExamPublishService.java`, `ExamCheckValidity.java`, `ExamCheckCompletion.java`, `ExamCheckResultQuery.java` | EX-1 | CR-4: chỉ phần dấu kiểm tra toàn bài và lý do `RULES_CHANGED`, sau khi EX-1 merge và Advisor cùng EX-1 chốt cách làm (CR-4 Sản phẩm) |
| `review-types.ts`, `review-api.ts`, `components/publish-exam-dialog.tsx` | CR-4 (chỉ ánh xạ mã, lý do, nhãn) | không task FTM nào khác; FA-1, EX-1 nếu sửa cùng tệp thì lane merge sau hợp nhất và chạy lại test review, công bố |
| `apps/web-admin/src/features/exams/**` còn lại | CR-1..CR-3 theo dòng trên | FU: theo B6 |
| `DESIGN.md` mục 5.14 | CR-5 | CR-2, CR-2H, CR-3 ghi nháp vào báo cáo lane, CR-5 viết vào |

**Chủ E2E** (sửa tệp E2E của chủ khác thì gửi qua chủ đó): `exam-allocation.spec.ts`, `exam-structure-frame.spec.ts`: CR-1. `exam-preview-qda.spec.ts`, `exam-subject-matrix-form.spec.ts` (mới): CR-2 (khối ca HSA: CR-2H). `exam-hsa-matrix.spec.ts`: CR-2H. `exam-hsa-mapid.spec.ts`: HSA-D (FTM không sửa). `exam-hsa-structure.spec.ts`, `exam-subject-row-form.spec.ts` (mới): CR-3. `exam-subject-forms-qa.spec.ts` (mới): CR-5.

**Thứ tự rebase trên tệp chung:** CR-1 merge trước mọi task web; CR-2H sau CR-2. Giữa CR-2 (hoặc CR-2H) và CR-3, task merge sau rebase, giữ cả hai khối của `registry.ts`, `blocking.ts`, `navigation.ts`, `messages`, chạy lại `subject-form-registry.test.ts`, `subject-form-selection.test.tsx`, `subject-form-blocking.test.tsx`.

EX-1 (đang chạy) và EX-2 (đã merge, AD-66) không đụng `apps/web-admin` (đã soát diff `origin/feature/UNI-2706-…`, `origin/feature/UNI-2707-…` ngày 2026-11-19). EX-1 có `ExamPublishService`, `ExamCheckApiModels`, mã lỗi, BE spec và messages; EX-2 có `ExamPreviewService`, `SolverInputBuilder`, BE spec; ID-2 có messages và OpenAPI. FA-1 sửa tab Thông tin bán (`exam-editor-layout.tsx`, `editor-tabs.ts`), không đụng bước Cấu trúc. HSA-D sửa `exam-structure-subject-panel.tsx` (MD của HSA), `SolverInputBuilder`, `ExamCheckRules` (`MAPID_RULE_CONFLICT`), solver và `questionbank` SPI (pha 0 v2 mục c, g): CR-4 rebase lên `ExamCheckRules` của HSA-D nếu HSA-D vào trước; MD đổi do HSA-D thì test MD của FTM chạy trên SHA đã có HSA-D.

## B3. Migration, mã sinh, đặt tên

- Plan này **không có migration**. Lane thấy cần migration thì dừng, báo Advisor; nếu được duyệt, theo A3 (mục Migration).
- Không đổi OpenAPI, không sinh lại `packages/api-client`. CR-4 chứng minh bằng so JSON OpenAPI đã chuẩn hóa (B4).
- Mã kiểm tra mới viết hoa, gạch dưới, có ở cả ba tệp `messages*.properties` và ở `vi.json`, `en.json`.
- Không viết chữ tiếng Việt trong component (`source-scan.test.ts`).
- Dữ liệu mẫu trong ảnh kiểm tra giao diện đặt tên theo quy ước `data-exam-content` của `e2e/tests/helpers/visual-matrix.ts`; nhãn giao diện không được miễn.

## B4. Bằng chứng dữ liệu và API

- **Thân `PUT` không đổi (FTM AC 6).** Test chặn request tại `api.http.ts` (không chỉ so model), lấy thân `PUT /structure` và so **toàn bộ cấu trúc wire** với bản đã nạp sau chuẩn hóa HSA-C, theo DTO thật (mọi trường của `parts`, `sections`, `blocks`, `options`, `subjectPool`, `examQuotas`, `expectedRevision`), không theo danh sách chọn tay. Chỉ được loại trừ, và phải gọi tên trong test: trường dẫn xuất do `api-mapping.ts` tính, và các cặp thiếu trường / `null` mà `api-mapping.ts` đã chuẩn hóa như vậy hôm nay. Ví dụ các trường phải có mặt: `section.audioRequirement`, `section.choiceGroup`, `part.durationMinutes`, `part.displaySegments`. Sau khi lưu, `GET` lại và so lần nữa. Sau đó sửa một trường do người dùng nhập và khẳng định chỉ trường đó và các trường dẫn xuất được gọi tên đổi. Ca thật trên stack lane, tối thiểu: một bài MT (HSA `FULL` có `options`/`choiceGroup`, không chỉ một lớp `sections`); một bài DS H-SCA Anh (mục Nghe `REQUIRED`, Đọc `FORBIDDEN`); một bài SPT dùng mã `GDKTPL` (mã gốc giữ nguyên, FTM AC 55); một bài MD. Test model theo mọi mẫu giữ để phủ rộng.
- **Số request (FTM mục 9).** CR-1: test router khẳng định chọn biểu mẫu không gọi request. CR-2, CR-2H, CR-3: test component mở MT, DS **thật** và MD của cùng môn, cùng fixture, cùng trạng thái cache, đếm request lúc mở; không tính request do người dùng chủ động mở hộp chọn MAPID, nguồn. Không đặt ngưỡng thời gian cứng.
- **OpenAPI.** Xuất JSON bằng cùng profile (`OpenApiExportIT`) ở SHA baseline và ở head CR-4 sau rebase, chuẩn hóa (sắp khóa) rồi so; khác biệt duy nhất được phép là phần có chủ của EX/ID/FU đã có ở baseline.

## B5. Test hiện có: giữ hay chuyển

| Test | Điểm neo hôm nay | Xử lý | Chủ |
|---|---|---|---|
| `e2e/tests/exam-hsa-matrix.spec.ts:74–123` | Mở bảng "Các nhóm lấy câu", radio Câu chùm ở Toán/Khoa học HSA; nhánh Văn ở `:89–102` | Chỉ đổi assert UI của Toán/Khoa học: khẳng định số chùm ở Cấu trúc câu; giữ nguyên kiểm API sinh nguyên chùm. Nhánh Văn **không** đổi thành MT: giữ khẳng định nhóm, đi qua DS (phối hợp CR-3). Ca cũ giữ trên fixture MD | CR-2H (Văn: CR-3 cung cấp bộ chọn DS) |
| `e2e/tests/exam-hsa-matrix.spec.ts:127–162` | Bất biến HSA-C ghi Bắt buộc khi lưu | Giữ nguyên assert, chạy trên MT | CR-2H |
| `__tests__/total-change-screen.test.tsx:16–31` | Bọc thẳng `ExamStructureSubjectPanel` | Giữ cho MD; thêm ca tương đương cho MT/DS (đổi tổng) | CR-2 (MT), CR-3 (DS) |
| `__tests__/structure-frame.test.tsx:407–413` | URL/đường mở nhóm cũ | Chuyển qua `navigation.ts`; giữ ca MD | CR-1 |
| `__tests__/structure-view.test.tsx:49` | Đường mở nhóm cũ | Như trên | CR-1 |
| `__tests__/review-step2.test.tsx:74–97` | Ca **ON**: `?block=` của bài ôn mở màn bài ôn | Giữ nguyên assert URL và ON. Khi `selection.ts` đọc `exam.buildMode` thay vì mẫu, fixture phải khai `buildMode: "REVIEW"` ở wire của bài (hôm nay chỉ khai ở mẫu) | CR-1 |
| `__tests__/issue-codes-hsca-vsat.test.tsx:301–331` | Chỉ đo URL tạo ra từ tab Kiểm tra, không render đích | Giữ nguyên assert URL. Đích DS được chứng minh ở test mới `subject-form-navigation.test.tsx`: `StructureEditor` nhận URL thật, mỗi biểu mẫu một ca | URL: giữ; ca đích: CR-1 (khung), CR-3 (DS), CR-2 và CR-2H (MT) |
| E2E HSA Văn (`exam-hsa-structure.spec.ts`) | Nhóm lấy câu Văn | Giữ khẳng định nhóm, đi qua DS | CR-3 |

Test điều hướng và fixture dùng chung (`subject-form-navigation.test.tsx`, fixture bài trong `__tests__/fixtures/`) chỉ CR-1 sửa khung; CR-2, CR-2H, CR-3 thêm ca của dạng mình trong khối riêng. Sau khi bật mỗi lát cắt, lane chạy lại: guard đổi tổng, vị trí lỗi, đọc tài nguyên audio, mở lại mẫu khóa. Không bỏ assert về kết quả sinh, không mở rộng fixture MD để che lỗi của biểu mẫu mới.

## B6. Việc FU (UNI-2700) giao nhau với FTM

| Mục FU (plan đợt 3, FU AC-7, AC-8, AC-12) | Còn ở | Ai sửa ở biểu mẫu mới |
|---|---|---|
| Khối tỷ lệ đang chặn lưu, bảng ma trận không ẩn `PERCENT_TOTAL…` | MD, MT (V-ACT), DS (Phân bổ đối chiếu) | Hàm dùng chung: lane merge sau mang sửa sang dạng của mình |
| Bấm lỗi đưa focus vào đúng ô | MD, MT, DS | FU sửa ở MD; CR-1 `navigation.ts` làm cho MT, DS (FTM R-15) |
| `mapBlock` ánh xạ đủ ba trường lọc | Hàm dùng chung của màn nhóm và DS | Lane merge sau chạy lại `row-list-model.test.ts` và test FU |
| Danh sách thay đổi của nháp chỉ đổi thứ tự | `exam-structure-changes.ts`, mọi dạng | FU; CR-1 chạy lại ca `defaultFormSaveIsIdentity` |
| `role=status` của Ngữ văn | MD, DS (HSA Văn) | FU ở MD; CR-3 cho DS |
| Bảng không tràn ngang ở 390px (bảng nhóm, chỉ tiêu, môn) | MD, MT, DS | FU ở MD; CR-2, CR-2H, CR-3 cho dạng mình; CR-5 soát |
| Migration bù UUID phần của mẫu (FU-BE6-3) | Dữ liệu mẫu | FU; nếu merge trước CR-5 thì xuất lại fixture (B8) và chạy lại ca khớp mẫu |

Thứ tự: FU backend nộp sau EX-1, EX-2 theo plan đợt 3; mục FU web chạm `subject-forms/**`, `exam-structure-view.tsx` hoặc thành phần chung ở B2 nộp sau CR-1 và task sở hữu tương ứng, hoặc Advisor giao theo tệp. Lane nộp sau chạy lại ca tương ứng trên merge head; FU đến sau CR-5 thì CR-5 cập nhật bằng chứng. Không sửa migration đã merge.

## B7. Lát cắt và cờ

Cờ bật theo **cặp**, chỉ khi cặp đó có đủ mọi hành vi FTM yêu cầu cho nó. Cặp chưa bật dùng MD như hôm nay.

| Lát | Nội dung | Cặp bật khi lát đủ AC | AC phải đạt trước khi bật |
|---|---|---|---|
| CR-2a | MT lõi: Cấu trúc câu, lớp và độ khó, mức áp dụng, R-6, Nguồn chùm, giới hạn R-12 phía web | QDA, BCA (MATRIX) | FTM AC 18, 26, 27 (BCA), 35, 44 (a, b, d, e, g, h), 45, 53 (QDA) |
| CR-2a′ | Nhập % hai cột của V-ACT (Cấu trúc câu, lớp, độ khó, bảng mạch) | V-ACT `TOAN`, `VIETNAMESE` (MATRIX) | FTM AC 9, 11, 19, 44 (i), 50, 54 (a–c, f phần ma trận) |
| CR-2H (task riêng) | MT của HSA: Cấu trúc câu, lớp, độ khó, R-12 web, nhúng Quy tắc MAPID của HSA-D (R-5). Mở sau CR-2 và HSA-D | HSA `TOAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA` (MATRIX) | FTM AC 7, 8, 10, 12–17, 27 (HSA), 29, 34, 35 (HSA), 44 (c, f) |
| CR-3a | DS lõi: bảng dòng, màn sửa dòng, Thêm điều kiện, khóa, mức áp dụng, ẩn lớp; không nhập %, không tách | HSA, QDA, BCA (`VAN`, `ANH`), THPTQG (ROWS) | FTM AC 20, 21, 24, 52 (a, c, d), 53 (THPTQG), 57 (HSA Anh, THPTQG) |
| CR-3b | Nhập % ở DS, tách dòng, bảng kết hợp V-SAT, khóa H-SCA/V-SAT, ẩn lớp SP2E/SPT/V-SAT | V-ACT `ANH`, `SCIENTIFIC_THINKING`; SP2E, SPT; HSCA; VSAT (ROWS) | FTM AC 22, 23, 25, 28, 30, 31, 32, 33, 47, 48, 49, 51, 52 (b), 54 (d–g), 57 (SP2E, SPT, V-SAT) |

**Bộ gate chung**, chạy cho **mỗi lát** trên SHA bật cờ của lát đó, chỉ với các cặp lát đó bật: FTM AC 5, 6 (khứ hồi wire B4), 40, 41, 56, 59 (điều hướng, lưu), và "thay đổi chưa lưu" của R-15. Cột AC ở trên chỉ gồm ca của cặp mà lát bật; ca của kỳ chưa bật không phải điều kiện của lát và không là phụ thuộc giữa các lane. Ca chéo: nhánh Văn của `exam-hsa-matrix.spec.ts` (chủ CR-2H) chạy trên MD khi CR-3a chưa merge, và chạy lại qua DS trên SHA hợp nhất sau khi CR-3a bật.

Mỗi lát có test fixture **trước khi bật** (cặp mở MD, không lỗi, kể cả bài có tỷ lệ hay có Logic/Suy luận) và **sau khi bật** (cặp mở đúng biểu mẫu, đủ ô). MT không bật trên `integration/dot-3` trước khi CR-4 merge (C1). Lát gặp ca dữ liệu bế tắc thì dừng ở cờ tắt, báo Advisor; không nới `fitsForm` để bật.

## B8. Fixture mẫu

- `scripts/export-exam-templates.mjs` gọi `GET /api/v1/admin/exam-templates` trên stack lane với token admin dev (Keycloak realm dev của lane), lọc `active = true`, tách mẫu `REVIEW` ra tệp riêng (chỉ dùng cho ca AC-4/AC-46).
- Tệp `__tests__/fixtures/exam-templates-dot3.json` ghi kèm SHA nguồn và ngày xuất; cập nhật bằng cách chạy lại script, commit cùng lý do.
- Test khớp mẫu in bảng phủ: mã mẫu, kỳ, `buildMode`, phạm vi, phần, môn, phiên bản, vị trí mục (`sections`, `subjectPool`, `options[].sections`). Mẫu thất bại được nêu tên, không bỏ qua.
- Fixture là dữ liệu khám phá, không thay oracle BA: kỳ vọng (FTM R-1, R-7) viết tay trong test. Đề nghị cho một mẫu chuẩn dùng MD phải sửa đặc tả và AC với người duyệt.

## B9. Giao diện với HSA-D

Do HSA-D cung cấp, liệt kê trong phần mô tả PR của HSA-D (PR forward-merge vào `integration/dot-3`):

- `data-testid` cho từng hàng quy tắc của bảng "Quy tắc MAPID" (theo `id` của quy tắc) và cho từng trường của hộp "Thêm quy tắc" (Áp dụng cho, Loại quy tắc, MAPID, so sánh, số câu hoặc %, nút lưu);
- neo để focus vào hàng quy tắc từ lỗi kiểm tra (`MAPID_RULE_CONFLICT`, thiếu theo quy tắc): mã lỗi trả `id` quy tắc, và hàng có phần tử nhận focus theo `id` đó;
- props của bảng và hộp đủ để gắn ngoài MD (bản nháp của mục, hàm cập nhật, danh sách nhóm), và hàm của `mapid-rules.ts` cho "Phạm vi nguồn" và tính lại %.

CR-2H dùng đúng các tên ở PR đó trong test (CR-2H AC-6, AC-10). Thiếu mục nào thì CR-2H dừng, báo Advisor để gửi HSA-D; không tự thêm `data-testid` vào thành phần của HSA-D.

# Phần C: Pipeline

## C1. Bảng phụ thuộc

| Task | Phụ thuộc cứng | Phụ thuộc mềm | Sóng | Điểm | Rủi ro |
|---|---|---|---|---|---|
| CR-1 | G1, G2 | - | 1 | 6 | cao |
| CR-4 | G1, G2 | EX-1, EX-2 | 1 | 4 | cao |
| CR-2 | CR-1 | CR-4 | 2 | 3 | cao |
| CR-2H | CR-1, CR-2, HSA-D | CR-4 | 3 | 2 | cao |
| CR-3 | CR-1 | - | 2 | 6 | cao |
| CR-5 | CR-2, CR-2H, CR-3, CR-4 | - | 4 | 3 | chuẩn |

Cứng: không mở được trước khi task kia merge vào `integration/dot-3`. Mềm: mở được, chỉ **nộp** sau khi task hoặc mốc kia merge. Các cạnh mềm đi một chiều: CR-4 làm song song CR-1, nộp trên SHA đã có EX-1 và EX-2; CR-2 làm song song, nộp sau CR-4, nên MT không bật trên nhánh tích hợp trước khi luật máy chủ có. CR-2 là lane đối chiếu bảng giới hạn web (`limits.ts`) với bảng máy chủ (`SubjectFormLimits`) khi nộp. CR-2H chờ cứng CR-1, CR-2 (dùng lại `matrix-form.tsx` và model của CR-2) và HSA-D đã forward-merge vào `integration/dot-3` (nhúng thành phần của HSA-D; AD-67); Advisor chỉ định CR-1 + HSA-D, CR-2 thêm vì CR-2H không có lõi MT để gắn nếu thiếu nó. CR-2H nộp sau CR-4 như CR-2. CR-3 chỉ chờ CR-1.

## C2. Sơ đồ

```text
Ngoài plan: EX-1 (EX-2 đã merge) ─┐   HSA-D (develop → forward-merge) ─┐
Sóng 1 ─ CR-1 (web: đăng ký, khớp, chọn, điều hướng, chặn lưu)
       └ CR-4 (máy chủ: hai mã, cổng công bố)  ◄┘ nộp sau EX-1, EX-2
Sóng 2 ─ CR-2 (Ma trận môn QDA, BCA, V-ACT) ◄─ CR-1   (nộp sau CR-4)
       └ CR-3 (Danh sách dòng)      ◄─ CR-1
Sóng 3 ─ CR-2H (Ma trận môn HSA, nhúng HSA-D) ◄─ CR-1, CR-2, HSA-D   (nộp sau CR-4)
Sóng 4 ─ CR-5 (QA, DESIGN.md)       ◄─ CR-2, CR-2H, CR-3, CR-4
```

## C3. Đường dài nhất và ưu tiên

- Trong plan: đường dài nhất theo số sóng (plan-lint) là CR-1 (6) → CR-2 (3) → CR-2H (2) → CR-5 (3) = 14 điểm; theo trọng số, CR-1 (6) → CR-3 (6) → CR-5 (3) = 15 điểm. Đây là trọng số, không phải lịch.
- Mốc ngoài theo dõi riêng bằng ngày và trạng thái: EX-1 (chặn nộp CR-4), HSA-D forward-merge (chặn mở CR-2H). Nếu một mốc trễ, đường thành EX-1 → CR-4 → CR-2/CR-2H → CR-5 hoặc HSA-D → CR-2H → CR-5; Advisor báo khi ngày dự kiến của mốc vượt ngày dự kiến xong CR-3.
- Mở trước: CR-1, vì CR-2 và CR-3 chờ nó. CR-4 mở cùng lúc nếu có máy.
- Tối đa ba lane cùng lúc (sóng 2: CR-2, CR-3, và CR-4 nếu chưa nộp; CR-2H thay chỗ CR-2 khi CR-2 merge và HSA-D vào). Máy chật thì CR-4 chạy test nặng trên cloud runner thay vì hoãn, vì CR-2, CR-2H chờ nó để nộp.

## C4. Phủ đặc tả

| Mã đặc tả | Task |
|---|---|
| R-1 / AC-1, AC-2, AC-3, AC-4, AC-55 | CR-1 |
| R-2 / AC-5, AC-6, AC-56 | CR-1 (khớp, ổn định trong phiên, MD); CR-2, CR-2H, CR-3 (AC-6 cho dạng mình) |
| R-3 / AC-7, AC-8, AC-9, AC-44, AC-45, AC-50 phần cấu trúc | CR-2 (AC-9, AC-44 a, b, d, e, g, h, i, AC-45, AC-50); CR-2H (AC-7, AC-8, AC-44 c, f) |
| R-4 / AC-10, AC-11, AC-50 phần lớp | CR-2 (AC-11, AC-50); CR-2H (AC-10) |
| R-5 / AC-12 … AC-17 (AC-43 bỏ ở bản 4, thuộc HSA-D) | CR-2H (nhúng thành phần HSA-D) |
| R-6 / AC-18, AC-19 | CR-2 |
| R-7 / AC-20 … AC-25, AC-47, AC-51, AC-52 | CR-3 |
| R-8 / AC-26, AC-27, AC-28, AC-48, AC-53 | CR-2 (AC-26, AC-27 phần BCA, AC-53 QDA phía web); CR-2H (AC-27 phần HSA); CR-3 (AC-28, AC-48, AC-53 THPTQG phía web); CR-4 (AC-53 cả hai qua kiểm tra) |
| R-9 / AC-29, AC-30, AC-54 | CR-2 (AC-54 phần V-ACT); CR-2H (AC-29); CR-3 (AC-30, AC-54 phần còn lại) |
| R-10 / AC-31, AC-32, AC-49 | CR-3 (AC-32 phần MD: CR-1) |
| R-11 / AC-33, AC-34, AC-57 | CR-3 (AC-33, AC-57 cho DS); CR-2 (AC-57 cho MT); CR-2H (AC-34); CR-1 (lý do không khớp) |
| R-12 / AC-35 … AC-39, AC-46, AC-58, AC-60 | CR-2 (AC-35 phần QDA); CR-2H (AC-35 phần HSA); CR-4 (AC-36 … AC-39, AC-46, AC-58, AC-60); CR-1 (AC-46 phần chọn biểu mẫu bài ôn, AC-59 phần bài cũ vi phạm mở MD) |
| R-13 / AC-40 | CR-2, CR-2H, CR-3; CR-5 soát toàn bộ |
| R-14 / AC-41 | CR-1, CR-2, CR-2H, CR-3 (mỗi task giữ MD xanh, theo B5); CR-5 |
| R-15 / AC-42, AC-59 | CR-1 (AC-59 điều hướng, chặn lưu); CR-2, CR-2H, CR-3 (màn của mình); CR-5 (AC-42 toàn bộ) |

# Phần D: Hợp đồng từng task

## CR-1 · Bảng đăng ký, khớp, chọn biểu mẫu, điều hướng, chặn lưu, thành phần chung

**Đặc tả:** R-1, R-2, R-14, R-15 (điều hướng, lưu, thay đổi chưa lưu); AC-1 … AC-6, AC-32 (phần MD), AC-41, AC-55, AC-56, AC-59, phần chọn biểu mẫu của AC-46; D-1, D-2, D-3.

**Sản phẩm:**

- `subject-forms/registry.ts` đủ 75 cặp, mã canonical và bí danh theo FTM R-1, cấu hình theo R-1, R-4, R-8 đến R-12; mọi mục `enabled: false`.
- `selection.ts`, `fit.ts`, `navigation.ts`, `raw-input-store.ts`, `blocking.ts`, `limits.ts`, `percent.ts` theo B1 (`percent.ts` bọc `apportionByPercent` của `matrix.ts`, không viết thuật toán mới).
- `exam-structure-view.tsx`: gọi `selection.ts` (một lần mỗi lần mở hoặc nạp mẫu, ghim trong phiên); nối `?block=`, bấm lỗi, bấm tóm tắt qua `navigation.ts`; gọi `blocking.ts` trước mọi `PUT` và trước Kiểm tra / Tiếp tục kể cả khi không dirty; dựng `hasUnsavedChanges` (B1) và nối vào cảnh báo rời trang, hộp chuyển mục, `StructureVersionPicker`, chuyển tab; Bỏ thay đổi, nạp mẫu đã xác nhận xóa nhập thô và ghim lại biểu mẫu; dữ liệu nền tải lại không thay bản nháp đang sửa.
- Thành phần chung B1, có test riêng.
- Fixture và script theo B8.
- Khóa `exams.subjectForm.common.*` ở `vi.json`, `en.json`.

**Đường dẫn sở hữu:**

- `apps/web-admin/src/features/exams/subject-forms/**` (mới)
- `apps/web-admin/src/features/exams/components/exam-structure-view.tsx` (chọn màn môn, điều hướng, nối lưu, thay đổi chưa lưu), `exam-structure-unsaved-guard.tsx` (nhận `hasUnsavedChanges`)
- `apps/web-admin/src/features/exams/__tests__/subject-form-*.test.ts(x)` (mới), `__tests__/fixtures/exam-templates-dot3*.json` (mới); các test ở B5 có chủ CR-1
- `scripts/export-exam-templates.mjs` (mới)
- `apps/web-admin/src/messages/{vi,en}.json` (khối `exams.subjectForm.common`)
- `e2e/tests/exam-allocation.spec.ts`, `exam-structure-frame.spec.ts` (bộ chọn, theo B5)

**Ngoài phạm vi:** vẽ MT và DS (CR-2, CR-2H, CR-3); luật máy chủ (CR-4); đổi hành vi MD hay ON.

**Nghiệm thu:**

- AC-1: `subjectFormFor` trả đúng dạng cho đúng 75 cặp canonical (FTM AC 1) — bằng chứng: `subject-form-registry.test.ts`, ca bảng 75 dòng viết tay theo FTM R-1
- AC-2: bí danh tra cùng mục ở kỳ có mã canonical; bí danh ở kỳ khác trả `null`; mã hệ thống cũ, mã lạ trả `null`; đổi tên hiển thị không đổi kết quả (FTM AC 2, AC 3, AC 55) — bằng chứng: `subject-form-registry.test.ts`, ca `aliasesOnlyWhereCanonical`, `unknownAndLegacyCodes`
- AC-3: thứ tự chọn của FTM R-1: bài ôn theo `exam.buildMode` không bao giờ ra MT/DS, kể cả kỳ lạ và bài ôn nhiều nhóm; kỳ lạ ra MD không dòng chữ; dạng tắt ra MD không dòng chữ (FTM AC 3, AC 4, phần chọn của AC 46) (xanh trước: bất biến, baseline không có MT/DS; chạy lại sau khi bật cờ thật ở CR-2, CR-2H, CR-3) — bằng chứng: `subject-form-selection.test.tsx`, ca `reviewNeverUsesSubjectForms`, `unknownPairUsesDefault`
- AC-4: mỗi điều kiện không khớp ở FTM R-2 (điều kiện lớp Bắt buộc của R-11, mục vi phạm R-12 khi nạp, nhóm chùm cỡ cố định lệch số câu hoặc `setCount` ≤ 0), một mình, trả `fits: false` đúng lý do; chùm cỡ linh hoạt (`null`) và dữ liệu MAPID của HSA không làm rơi MD; mọi mẫu chuẩn của fixture khớp, in bảng phủ theo B8 (FTM AC 5) — bằng chứng: `subject-form-fit.test.ts`
- AC-5: dạng bật mà không khớp vẽ MD kèm dòng chữ; biểu mẫu ghim trong phiên khi bản nháp thành khớp; tải lại thì tính lại (FTM AC 56) (đỏ trước: hôm nay không có ghim) — bằng chứng: `subject-form-selection.test.tsx`, ca `notFittingFallsBackWithNotice`, `formStableWithinSession`
- AC-6: Lưu MD không sửa gì: thân `PUT` bằng bản đã nạp theo B4 (FTM AC 6 phần MD) — bằng chứng: `subject-form-payload.test.tsx`, ca `defaultFormSaveIsIdentity` (chặn request ở `api.http.ts`)
- AC-7: với mọi dạng đang tắt, màn môn vẽ đúng như trước (FTM AC 41) (xanh trước) — bằng chứng: test hiện có của `exam-structure-subject-panel`, `block-editor`, `review-subject-panel` xanh không sửa assert
- AC-8: `?block=`, bấm lỗi, bấm tóm tắt đi qua `navigation.ts`; với dạng MD/ON giữ đích cũ (xanh trước, giữ assert B5); khung nhận đích MT/DS và đặt focus; không lộ nút thêm, xóa, đổi thứ tự trái dạng (FTM AC 59 phần điều hướng). Đỏ trước có ý nghĩa ở nghiệm thu 9 của CR-2, 10 của CR-2H và 13 của CR-3 (đích compact sau khi bật cờ thật), không ở khung này — bằng chứng: `subject-form-navigation.test.tsx`; test ở B5 có chủ CR-1
- AC-9: guard trên `StructureEditor` thật, với luật thử đăng ký qua `blocking.ts`: (a) lỗi ở môn MT chưa mở: Lưu, Lưu và chuyển, Kiểm tra, Tiếp tục không gửi `PUT` hay kiểm tra, focus về ô lỗi; (b) trạng thái nhập thô sai khi bản nháp sạch (`dirty = false`): Kiểm tra, Tiếp tục không gửi request (đỏ trước: `runCheck` hôm nay đi thẳng); (c) đóng màn sửa dòng không làm mất lỗi; "Bỏ thay đổi" xóa đúng lỗi nhập thô, lưu lại được; (d) nháp trộn: mục MD có dữ liệu mà luật MT sẽ coi là sai không bị chặn, mục DS bên cạnh có lỗi thì bị chặn; (e) sửa đúng rồi lưu được, môn khác không đổi (FTM AC 59 phần lưu) (đỏ trước a, b) — bằng chứng: `subject-form-blocking.test.tsx`
- AC-10: chọn biểu mẫu không gọi request (hồi quy router; đếm khi mở MT/DS thật thuộc nghiệm thu 11 của CR-2, 12 của CR-2H và 16 của CR-3) — bằng chứng: `subject-form-payload.test.tsx`, ca `selectionMakesNoRequests`
- AC-11: thành phần chung đúng ở sáng/tối, vi/en, khóa có lý do; không chữ tiếng Việt trong component — bằng chứng: `subject-form-common.test.tsx`, `source-scan.test.ts`, `messages.test.ts`
- AC-12: trên `StructureEditor` thật, bản nháp sạch chỉ có nhập thô lỗi: chuyển tab, tải lại (cảnh báo rời trang), chuyển mục, đổi mẫu ở `StructureVersionPicker` đều hỏi; Ở lại giữ chữ đã gõ; Bỏ thay đổi và nạp mẫu đã xác nhận xóa nhập thô, ghim lại biểu mẫu, sau đó Kiểm tra không còn lỗi từ `id` cũ; dữ liệu nền tải lại không thay bản nháp đang sửa; thân `PUT` không chứa giá trị chưa hợp lệ (FTM AC 59 phần thay đổi chưa lưu) (đỏ trước: hôm nay `dirty` chỉ so JSON nên không hỏi) — bằng chứng: `subject-form-unsaved.test.tsx`

**Ảnh hưởng ngoài task:**

- Màn Cấu trúc của mọi kỳ (`exam-structure-view.tsx`) → test hiện có của `exam-structure-view`, E2E `exam-hsa-structure.spec.ts`, `exam-structure-frame.spec.ts`, `exam-allocation.spec.ts` xanh [chủ: task này]
- Đường lưu chung (`runSave`, lưu và chuyển, tự lưu trước Kiểm tra / Tiếp tục) → test hiện có của view và cảnh báo rời trang xanh; ca AC-9 [chủ: task này]
- Bài ôn (`rendersReviewPanel`, `review-subject-panel.tsx`) → test `review-subject-panel`, ca bài ôn của `exam-allocation.spec.ts` [chủ: task này]
- Cảnh báo rời trang, hộp chuyển mục, đổi mẫu và danh sách thay đổi (`exam-structure-unsaved-guard.tsx`, `StructureVersionPicker`, `exam-structure-changes.ts`) → test hiện có xanh không sửa assert với bản nháp bẩn; ca `defaultFormSaveIsIdentity`; AC-12 [chủ: task này]
- Chuyển đổi MAPID của HSA-D lúc nạp (khi HSA-D đã vào) → chọn biểu mẫu chạy sau chuyển đổi; `subject-form-selection.test.tsx` có ca bài HSA cũ [chủ: task này]
- Chuẩn hóa HSA-C (`useHsaNormalizedDraft`, `hsaNeedsNormalizing`) → test `hsa-enforcement` xanh; chọn biểu mẫu chạy sau chuẩn hóa [chủ: task này]
- Tệp thông điệp web (`messages/{vi,en}.json`) → `messages.test.ts` [chủ: task này]
- FU sửa focus lỗi, danh sách thay đổi ở MD (B6) → lane merge sau chạy lại `subject-form-navigation.test.tsx` và test FU [chủ: task này hoặc FU, theo thứ tự merge]
- CR-2, CR-2H, CR-3 dùng B1 → các task đó chạy lại `subject-form-*.test.ts(x)` khi đổi cờ hoặc thêm ánh xạ [chủ: CR-2, CR-2H, CR-3]

**Kiểm thử:**

- AC-1, AC-2: `subject-form-registry.test.ts`, đơn vị, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/subject-form-registry.test.ts`
- AC-3, AC-5: `subject-form-selection.test.tsx`, thành phần, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/subject-form-selection.test.tsx`
- AC-4: `subject-form-fit.test.ts`, đơn vị, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/subject-form-fit.test.ts`
- AC-6, AC-10: `subject-form-payload.test.tsx`, thành phần, chặn request tại `api.http.ts`
- AC-7: bộ test web hiện có của màn môn, nhóm, bài ôn, `pnpm --filter @thi360/web-admin test src/features/exams`
- AC-8: `subject-form-navigation.test.tsx`, thành phần; các test ở B5 có chủ CR-1
- AC-9: `subject-form-blocking.test.tsx`, thành phần trên `StructureEditor`
- AC-11: `subject-form-common.test.tsx`, thành phần; `source-scan.test.ts`, `messages.test.ts`
- AC-12: `subject-form-unsaved.test.tsx`, thành phần, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/subject-form-unsaved.test.tsx`
- Hồi quy: các dòng Ảnh hưởng ở trên; bộ web đầy đủ và E2E bước Cấu trúc (A6) trên cloud runner
- Rủi ro (cao): đỏ trước AC-5, AC-9 (a, b), AC-12 ở các điểm tích hợp; xanh trước AC-3, AC-7, phần MD/ON của AC-8. Mỗi ca ghi điểm quan sát, fixture, SHA
- Dữ liệu test: fixture B8; bài dựng trong test bằng hàm tạo sẵn, không dùng chung DB
- Không kiểm: giao diện MT, DS (chưa có ở task này)

**Thiết kế / tham chiếu:** FTM mục 4, 6; B1, B4, B5, B8; gap-analysis mục 3, 5.

## CR-2 · Biểu mẫu Ma trận môn của QDA, BCA, V-ACT

**Đặc tả:** R-2 (AC-6 cho MT), R-3, R-4, R-6, R-8 (AC-26, AC-27, AC-53 phía web), R-9 (AC-54 phần V-ACT), R-11 (AC-57 cho MT), R-12 (AC-35 phần QDA), R-13 (phần MT), R-15 (MT); AC-9, AC-11, AC-18, AC-19, AC-44 (a, b, d, e, g, h, i), AC-45, AC-50, AC-40; D-5. Phần HSA của MT ở CR-2H. R-3 theo G1-6 (A).

**Sản phẩm:**

- Lát CR-2a:
  - `subject-forms/matrix-form.tsx`: đầu môn; Cấu trúc câu (R-3) có Nguồn chùm; hàng Khối lớp và Độ khó (R-4) với mức áp dụng (R-8) và %; khối Phạm vi + Thêm chỉ tiêu chủ đề (R-6); bảng mạch V-ACT Tiếng Việt; lỗi giới hạn tại ô (R-12). Chừa chỗ gắn phần riêng theo kỳ (CR-2H thêm phần HSA), không vẽ gì cho HSA.
  - `subject-forms/matrix-form-model.ts`: nhận vai trò nhóm; ba ô nhập (T, k, S) và ô tự tính theo FTM R-3; chùm cỡ cố định ghi nhất quán `setCount`, `setSize`, `questionCount`, Số chùm ≥ 1; chùm cỡ linh hoạt giữ `setCount`, `setSize` `null`, `questionCount` là tổng câu con; cập nhật nhóm theo `id`, không xóa, không tạo nhóm (đơn, điền về 0 giữ nhóm); giữ thứ tự và trường không chỉnh; `answerQuotas` tính lại chỉ khi đổi Cấu trúc câu, giữ dạng đang lưu; ma trận mục `SEPARATE`, `unit: COUNT` (QDA, BCA) hoặc `unit: PERCENT` khi nhập % (V-ACT, FTM R-9); chiều trống ghi mảng rỗng, ma trận `null` đã lưu giữ `null` (R-8).
  - `subject-forms/matrix-blocking.ts`: luật chặn lưu R-12, "Ít nhất 1 chùm", "Trắc nghiệm nhỏ hơn câu con" và % Cấu trúc câu V-ACT chưa đủ 100% trên bản nháp cộng nhập thô, chỉ cho mục ghim MT, đăng ký ở `blocking.ts`; ánh xạ đích MT ở `navigation.ts`.
  - Bật MATRIX cho QDA, BCA.
- Lát CR-2a′: nhập % hai cột của V-ACT; bật MATRIX cho V-ACT `TOAN`, `VIETNAMESE`.
- Khóa `exams.subjectForm.matrix.*`.
- E2E `e2e/tests/exam-subject-matrix-form.spec.ts` (khung và ca QDA, BCA, V-ACT); chuyển test theo B5 (chủ CR-2).

**Đường dẫn sở hữu:**

- `apps/web-admin/src/features/exams/subject-forms/matrix-form.tsx`, `matrix-form-model.ts`, `matrix-blocking.ts` (mới); `subject-forms/registry.ts` (hàng MATRIX của QDA, BCA, V-ACT), `blocking.ts`, `navigation.ts` (một phần tử của MT)
- `apps/web-admin/src/features/exams/components/knowledge-scope-field.tsx`, `topic-quota-table.tsx`, `topic-quota-elsewhere-alert.tsx` (chỉ cho R-6, theo B2)
- `apps/web-admin/src/features/exams/__tests__/subject-matrix-*.test.ts(x)`, `matrix-form-model.test.ts` (mới); `total-change-screen.test.tsx` (thêm ca MT)
- `apps/web-admin/src/messages/{vi,en}.json` (khối ở B2)
- `e2e/tests/exam-subject-matrix-form.spec.ts` (mới, trừ khối ca HSA); `exam-preview-qda.spec.ts` (theo B5)

**Ngoài phạm vi:** MT của HSA và quy tắc MAPID (CR-2H, HSA-D); DS (CR-3); luật máy chủ (CR-4); đổi bộ sinh; đổi MD.

**Nghiệm thu:**

- AC-1: hai nhóm cùng vai trò khác nguồn mở MD; BCA Sử T = 10 đổi số chùm 1 → 2: chùm `setCount` 2, `questionCount` 6, nhóm đơn 4, các trường không dẫn xuất (`id`, `tagCodes`, `templateGroupCode`, thứ tự, điểm) giữ nguyên, Kiểm tra không có `SET_SIZE_MISMATCH`; BCA Sử Số chùm 0 báo "Ít nhất 1 chùm", không gửi `PUT`; T nhỏ hơn câu con báo lỗi tại ô; môn không quota giữ trống (FTM AC 44 a, b, d, e, g, h) (đỏ trước) — bằng chứng: `matrix-form-model.test.ts`, ca `consistentSetCounts`, `fixedSetMinOne`, `noQuotaStaysNull`; E2E `exam-subject-matrix-form.spec.ts`, ca BCA Sử (0/1/2 chùm) trên stack
- AC-2: BCA Hóa đặt MAPID ở Nguồn chùm chỉ đổi `knowledgeScope` của nhóm chùm (FTM AC 45) — bằng chứng: `subject-matrix-form.test.tsx`, ca `setSourceOnlyTouchesSetGroup`
- AC-3: V-ACT Toán từ mẫu thật mở MT (không rơi MD); tổng câu con 18 lưu chùm cỡ linh hoạt tổng 18; lưu không sửa và lưu sau khi sửa đều giữ `setCount`, `setSize` là `null` trong thân `PUT` (FTM AC 9, AC 44 i) — bằng chứng: `matrix-form-model.test.ts`, ca `vactFlexibleSets`; `subject-form-payload.test.tsx`, ca `vactFlexibleKeepsNulls`
- AC-4: V-ACT Tiếng Việt không có hàng lớp khi chưa có số, có khi bài cũ đã khai; FULL 30 chỉ đọc, tiểu mục 12 sửa được; 8 dòng mạch chờ mapping lưu được; V-ACT Toán có cột Liên lớp (FTM AC 11, AC 50) — bằng chứng: `subject-matrix-form.test.tsx`, ca `vactVietnameseScopes`, `vactMathMixedGrade`
- AC-5: QDA Toán có Phạm vi kiến thức và nút "Thêm chỉ tiêu chủ đề"; V-ACT Toán 10% trên 30 câu hiện "3 câu · Phần còn lại 27 câu", lưu `count` 3 và `percent` 10; V-ACT độ khó 25/50/25% của 30 hiện 8/15/7, lưu `unit: PERCENT` 25/50/25, tải lại vẫn %, đổi tổng 40 (trong phiên và sau tải lại) ra 10/20/10; Cấu trúc câu 40%/60% lưu 12/18, tải lại hiện số; Cấu trúc câu 40%/50% hiện "cần 100%", không quy ra số, chặn lưu; ma trận độ khó tổng 90% hiện "cần 100%" và web không gửi `PUT` (guard `MATRIX_PERCENT` giữ nguyên); gửi thẳng API thì Kiểm tra ra `PERCENT_TOTAL_INVALID` (FTM AC 18, AC 19, AC 54 a–c, f) — bằng chứng: `subject-matrix-form.test.tsx`, ca `scopeAndQuotaFlow`, `vactPercentStorage`; `percent.test.ts` khớp quy đổi máy chủ (FTM AC 54 g)
- AC-6: QDA Lý để trống Vận dụng cao lưu `REQUIRED` 3 giá trị; chiều trống hết ghi mảng rỗng; khai 10/5/5 lưu được và bước Kiểm tra (E2E) ra `SUBJECT_MATRIX_TOTAL_MISMATCH` ở mục Lý; hiện "Đã khai x/y"; BCA chip Tham khảo, không nút đổi (FTM AC 26, AC 27 phần BCA, AC 53 phía web) — bằng chứng: `subject-matrix-form.test.tsx`, ca `enforcementByKind`, `declaredDimensions`
- AC-7: QDA Lý sửa 2 → 3 chùm hoặc 0 câu điền trong MT báo lỗi tại ô; chuyển môn rồi Lưu và chuyển, Kiểm tra, Tiếp tục không gửi request, focus về ô; QDA Sử 0 câu điền không lỗi; bài QDA cũ lưu nháp 3 chùm ở Lý mở MD có dòng chữ, các môn khác lưu được, Lưu không sửa gửi `PUT` như bản đã nạp (FTM AC 35 phần QDA, AC 59) (đỏ trước) — bằng chứng: `subject-matrix-form.test.tsx`, ca `scienceLimits`; `subject-form-blocking.test.tsx`, ca `matrixLimitsBlockAllSaves`, `legacyViolationOpensDefault`; E2E `exam-subject-matrix-form.spec.ts`
- AC-8: MT không hiện trường nào ở FTM R-13; thân `PUT` không đổi theo B4 với bài MT thật và mọi mẫu MT của QDA, BCA, V-ACT trong fixture; ẩn lớp theo R-11 không áp ở MT của ba kỳ này (FTM AC 6, AC 40, AC 57 cho MT) — bằng chứng: `subject-form-payload.test.tsx`, ca `matrixFormSaveIsIdentity`; `matrix-form-model.test.ts`, ca `roundTripAllMatrixTemplates`; `subject-matrix-form.test.tsx`, ca `noRemovedFields`
- AC-9: `?block=` thật, bấm lỗi, tóm tắt của nhóm thuộc môn MT vào đúng ô và đặt focus, cả khi đi qua Lưu và chuyển (FTM AC 59 cho MT) (đỏ trước: sau khi bật cờ, đích hôm nay là editor nhóm) — bằng chứng: `subject-form-navigation.test.tsx`, ca `matrixTargets` trên `StructureEditor`
- AC-10: kiểm tra giao diện QDA Lý (lỗi giới hạn, focus), V-ACT Toán (hai cột), BCA Hóa (Nguồn chùm mở, menu Thêm điều kiện mở và đi bằng bàn phím): sáng/tối, vi/en, 390/1280, không trộn ngôn ngữ, tương phản ≥ 4,5:1 — bằng chứng: tester_visual, ảnh trong báo cáo lane; ca bàn phím trong `subject-matrix-form.test.tsx`
- AC-11: mở môn MT thật không gọi nhiều request hơn MD của cùng môn, cùng fixture và cache; hộp chọn MAPID mở chủ động không tính (FTM mục 9) — bằng chứng: `subject-matrix-form.test.tsx`, ca `noExtraRequestsOnOpen`

**Ảnh hưởng ngoài task:**

- `knowledge-scope-field.tsx` dùng ở màn nhóm (`block-editor.tsx`), bài ôn (`review-subject-panel.tsx`), MD → test hiện có của ba màn xanh không sửa assert [chủ: task này]
- `topic-quota-table.tsx` dùng ở MD và màn nhóm → test `topic-quota-ui` hiện có xanh [chủ: task này]
- Bộ sinh và kiểm tra đọc `knowledgeScope`, `topicQuotas` cho QDA, BCA, V-ACT (`SolverInputBuilder`, `EffectiveKnowledgeScope`, `ExamCheckRules.topicQuotas`) → `exam-preview-qda.spec.ts`, ca R-6 của `exam-subject-matrix-form.spec.ts`; `TOPIC_QUOTA_MAPPING_MISSING`, `TOPIC_QUOTA_NESTED` vẫn hiện ở bước Kiểm tra [chủ: task này]
- Xem trước QDA (`exam-preview-qda.spec.ts`) → chạy lại [chủ: task này]
- Giới hạn R-12 phía máy chủ → khi nộp (sau CR-4), so `limits.ts` với `SubjectFormLimits` bằng test dữ liệu chung [chủ: task này]
- Test ở B5 có chủ CR-2 → chuyển theo bảng, không bỏ assert về kết quả sinh [chủ: task này]
- HSA vẫn ở MD tới CR-2H → test MD của HSA xanh, cờ MATRIX của HSA tắt [chủ: task này]
- `registry.ts`, `blocking.ts`, `navigation.ts` của CR-1 → `subject-form-*.test.ts(x)` xanh sau khi bật MATRIX [chủ: task này]

**Kiểm thử:**

- AC-2, AC-4, AC-5, AC-6, AC-7, AC-8, AC-10 (bàn phím), AC-11: `subject-matrix-form.test.tsx`, thành phần, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/subject-matrix-form.test.tsx`
- AC-1, AC-3, AC-8: `matrix-form-model.test.ts`, đơn vị, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/matrix-form-model.test.ts`
- AC-3, AC-7, AC-8, AC-9: `subject-form-blocking.test.tsx`, `subject-form-payload.test.tsx`, `subject-form-navigation.test.tsx` (ca của MT)
- AC-1, AC-6, AC-7: `exam-subject-matrix-form.spec.ts`, E2E, `pnpm --filter @thi360/e2e test tests/exam-subject-matrix-form.spec.ts`
- AC-10: tester_visual, các màn ở AC-10, dùng `e2e/tests/helpers/visual-matrix.ts`
- Hồi quy: mọi dòng Ảnh hưởng; `exam-hsa-structure.spec.ts`, `exam-allocation.spec.ts`, `exam-preview-qda.spec.ts`; bộ web đầy đủ trên cloud runner; bộ gate chung B7
- Rủi ro (cao): đỏ trước AC-1, AC-7, AC-9; xanh trước phần bất biến (MD, HSA còn MD). Mỗi lát (CR-2a, CR-2a′) có test fixture trước và sau khi bật (B7), kiểm rơi MD, payload không đổi tại màn; biên: 0, 1, 2, 3 chùm, 0 câu điền, chùm linh hoạt `null`, % làm tròn và hòa của V-ACT
- Dữ liệu test: fixture B8; E2E tạo bài và chủ đề riêng có hậu tố theo lane
- Không kiểm: MT của HSA (CR-2H)

**Thiết kế / tham chiếu:** QDA §4, §6; BCA §5–§7; VACT §3–§5; FTM R-3, R-4, R-6, R-8, R-9.

## CR-2H · Biểu mẫu Ma trận môn của HSA (nhúng Quy tắc MAPID của HSA-D)

**Đặc tả:** R-2 (AC-6 cho MT của HSA, ngoại lệ HSA-D), R-3 (phần HSA), R-4 (phần HSA), R-5, R-8 (AC-27 phần HSA), R-9 (AC-29), R-11 (AC-34), R-12 (AC-35 phần HSA), R-13 (MT của HSA), R-15 (MT của HSA); AC-7, AC-8, AC-10, AC-12 … AC-17, AC-29, AC-34, AC-44 (c, f); D-7. R-5 là nhúng thành phần của HSA-D (AD-67, AD-68).

**Sản phẩm:**

- Mở sau khi CR-2 đã merge và HSA-D đã forward-merge vào `integration/dot-3` (A2, C1). Dùng lại `matrix-form.tsx`, `matrix-form-model.ts`, `matrix-blocking.ts` của CR-2; thêm phần HSA, không đổi hành vi QDA, BCA, V-ACT.
- `subject-forms/matrix-form-hsa.tsx`: phần HSA của MT. Nhúng nguyên trạng bảng "Quy tắc MAPID" và hộp "Thêm quy tắc" của HSA-D, với cùng props như MD. Không hiện Phạm vi kiến thức, Thêm chỉ tiêu chủ đề, Nguồn chùm. "Phạm vi nguồn" của nhóm hiện dưới hàng vai trò, bằng hàm của HSA-D. Đổi số ở Cấu trúc câu thì gọi hàm tính lại % của HSA-D. `answerQuotas` dạng HSA-B và dạng cũ tính như R-3. Chip Bắt buộc theo HSA-C.
- Ánh xạ đích `MAPID_RULE_CONFLICT` và lỗi thiếu theo quy tắc ở `navigation.ts`, theo neo ở B9.
- Bật MATRIX cho 6 môn MT của HSA (`TOAN`, `LY`, `HOA`, `SINH`, `SU`, `DIA`).
- Ca HSA trong `e2e/tests/exam-subject-matrix-form.spec.ts` (khối riêng); chuyển test theo B5 (chủ CR-2H).
- Ghi trong báo cáo lane SHA của HSA-D mà task nhúng.

**Đường dẫn sở hữu:**

- `apps/web-admin/src/features/exams/subject-forms/matrix-form-hsa.tsx` (mới); `subject-forms/registry.ts` (chỉ hàng MATRIX của HSA), `navigation.ts` (đích quy tắc MAPID)
- `apps/web-admin/src/features/exams/__tests__/hsa-matrix-form.test.tsx`, `hsa-matrix-rules-embed.test.tsx` (mới)
- `e2e/tests/exam-subject-matrix-form.spec.ts` (khối ca HSA); `exam-hsa-matrix.spec.ts` (theo B5)
- `apps/web-admin/src/messages/{vi,en}.json` (khóa HSA dưới `exams.subjectForm.matrix.hsa`)

**Ngoài phạm vi:** MT của QDA, BCA, V-ACT (CR-2); quy tắc MAPID của HSA (dữ liệu, chuyển đổi bài cũ, bộ sinh, kiểm tra, bảng, hộp: HSA-D); nhóm lấy câu và bài ôn của HSA; luật máy chủ (CR-4); đổi MD.

**Nghiệm thu:**

- AC-1: HSA Toán mặc định hiện Trắc nghiệm 35, Điền 15, Chùm 1 × 3, Trắc nghiệm đơn 32; Điền 12 hiện "47/50, thiếu 3" và vẫn lưu nháp (FTM AC 7) — bằng chứng: `hsa-matrix-form.test.tsx`, ca `hsaMathDefaultsAndGap`
- AC-2: sửa Trắc nghiệm của bài từ mẫu HSA ghi `answerQuotas` dạng HSA-B đúng số; bài cũ 35/15 giữ dạng cũ; kiểm tra cấu hình không báo `ANSWER_QUOTA_DUPLICATE` hay lệch dạng (FTM AC 8, AC 44 c) — bằng chứng: `hsa-matrix-form.test.tsx`, ca `recomputesQuotasInStoredShape`, `legacyQuotaShape`; E2E `exam-subject-matrix-form.spec.ts`, ca HSA lưu rồi chạy kiểm tra
- AC-3: bài HSA Sử dựng riêng có nhóm điền 1 câu (mẫu mặc định không có nhóm điền, `V202611180652:104`): Điền 1 → 0 → lưu → tải lại → 1 → lưu giữ cùng nhóm qua `PUT`/`GET` (FTM AC 44 f) (đỏ trước) — bằng chứng: E2E `exam-subject-matrix-form.spec.ts`, ca `hsaHistoryFillZero`
- AC-4: HSA Toán hiện lớp 3/20/27, độ khó 2/21/20/7, ghi `SEPARATE`/`QUESTION`; chip Bắt buộc, không nút đổi; không nhập theo tỷ lệ; HSA Lịch sử có hàng khối lớp; HSA Anh không thuộc MT (FTM AC 10, AC 27 phần HSA, AC 29, AC 34) — bằng chứng: `hsa-matrix-form.test.tsx`, ca `gradeAndDifficultyRows`, `hsaEnforcementNoPercent`, `hsaHistoryKeepsGrade`
- AC-5: HSA Hóa nhập 3 chùm hoặc 0 câu điền báo lỗi tại ô, mọi đường lưu không gửi request; HSA Sử 0 câu điền không lỗi (FTM AC 35 phần HSA) (đỏ trước) — bằng chứng: `hsa-matrix-form.test.tsx`, ca `hsaScienceLimits`
- AC-6: MT của HSA Toán vẽ bảng "Quy tắc MAPID" và hộp "Thêm quy tắc" của HSA-D (cùng component với MD, khẳng định bằng component và các `data-testid` ở B9); không có Phạm vi kiến thức, Thêm chỉ tiêu chủ đề, Nguồn chùm; "Phạm vi nguồn" hiện dưới hàng vai trò (FTM AC 12) (đỏ trước) — bằng chứng: `hsa-matrix-rules-embed.test.tsx`, ca `embedsHsaDRules`
- AC-7: cùng thao tác trên cùng bài HSA Toán (thêm `COUNT` đúng 3 câu Hàm số cho nhóm Điền đáp án; thêm `SOURCE` cho nhóm Câu chùm) ở MT và ở MD cho thân `PUT` giống nhau ở `mapidMode`, `mapidRules` (FTM AC 13) — bằng chứng: `hsa-matrix-rules-embed.test.tsx`, ca `sameRulesAsDefaultForm` (chặn request ở `api.http.ts`)
- AC-8: đổi Điền đáp án 15 → 12 ở Cấu trúc câu: quy tắc % của nhóm đó tính lại giống MD; Điền về 0 rồi lên lại: quy tắc vẫn trỏ đúng `id` (FTM AC 14) (đỏ trước) — bằng chứng: `hsa-matrix-rules-embed.test.tsx`, ca `percentRulesFollowStructure`, `zeroKeepsRuleTarget`
- AC-9: bài HSA cũ có phạm vi và chỉ tiêu chủ đề mở MT, không rơi MD vì dữ liệu MAPID; lưu không sửa thì thân `PUT` khác bản đã nạp chỉ ở phần do hàm chuyển đổi của HSA-D tạo trên cùng bản nạp; mọi mẫu MT của HSA trong fixture khứ hồi theo B4 (FTM AC 15, AC 6 cho HSA, AC 40 cho HSA) — bằng chứng: `subject-form-payload.test.tsx`, ca `hsaLegacyOnlyHsaDConversion`, `hsaMatrixSaveIsIdentity`
- AC-10: bấm lỗi `MAPID_RULE_CONFLICT` ở bước Kiểm tra mở môn MT và đặt focus vào hàng quy tắc theo neo ở B9; `?block=` của nhóm HSA vào đúng ô (FTM AC 16, AC 59 cho HSA) (đỏ trước) — bằng chứng: `subject-form-navigation.test.tsx`, ca `mapidRuleTarget`, `hsaMatrixTargets`
- AC-11: trên trình duyệt, HSA Toán ở MT thêm quy tắc loại 2 và 5 (kịch bản AC-12 của HSA-D) rồi sinh thử: đề đạt cả hai quy tắc (FTM AC 17) — bằng chứng: E2E `exam-subject-matrix-form.spec.ts`, ca `hsaMathRulesInMatrix`
- AC-12: mở môn MT của HSA không gọi nhiều request hơn MD của cùng môn, cùng fixture và cache; hộp Thêm quy tắc, hộp chọn MAPID mở chủ động không tính (FTM mục 9) — bằng chứng: `hsa-matrix-form.test.tsx`, ca `noExtraRequestsOnOpen`
- AC-13: kiểm tra giao diện HSA Toán (bảng Quy tắc MAPID nhúng, hộp Thêm quy tắc mở, đi bằng bàn phím), HSA Hóa (lỗi giới hạn, focus): sáng/tối, vi/en, 390/1280, không trộn ngôn ngữ, tương phản ≥ 4,5:1 — bằng chứng: tester_visual, ảnh trong báo cáo lane; ca bàn phím trong `hsa-matrix-rules-embed.test.tsx`

**Ảnh hưởng ngoài task:**

- Thành phần quy tắc MAPID của HSA-D nhúng ở MT → test của HSA-D (`mapid-rules`, bảng, hộp) và `exam-hsa-mapid.spec.ts` chạy lại xanh trên SHA của task, không sửa [chủ: task này]
- MT của QDA, BCA, V-ACT (CR-2) dùng chung `matrix-form.tsx` → `subject-matrix-form.test.tsx`, `matrix-form-model.test.ts` xanh không sửa assert [chủ: task này]
- Chế độ HSA cố định Bắt buộc (`hsa-enforcement.ts`, HSA-C) → test `hsa-enforcement`; E2E `exam-hsa-matrix.spec.ts:127–162` giữ assert [chủ: task này]
- Đếm dạng trả lời của HSA-B (`AnswerQuotaCounting`, `V202611190100`) → ca `recomputesQuotasInStoredShape`, `legacyQuotaShape` và kiểm tra cấu hình trong E2E không báo lệch [chủ: task này]
- MD của HSA do HSA-D gắn thành phần → test MD của HSA trên SHA đã có HSA-D xanh [chủ: task này]
- `registry.ts`, `navigation.ts` của CR-1 → `subject-form-*.test.ts(x)` xanh sau khi bật MATRIX cho HSA [chủ: task này]

**Kiểm thử:**

- AC-1, AC-2, AC-4, AC-5, AC-12: `hsa-matrix-form.test.tsx`, thành phần, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/hsa-matrix-form.test.tsx`
- AC-6, AC-7, AC-8, AC-13 (bàn phím): `hsa-matrix-rules-embed.test.tsx`, thành phần, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/hsa-matrix-rules-embed.test.tsx`
- AC-9, AC-10: `subject-form-payload.test.tsx`, `subject-form-navigation.test.tsx` (ca HSA)
- AC-2, AC-3, AC-11: `exam-subject-matrix-form.spec.ts` (khối HSA), E2E, `pnpm --filter @thi360/e2e test tests/exam-subject-matrix-form.spec.ts`
- AC-13: tester_visual, các màn ở AC-13, dùng `e2e/tests/helpers/visual-matrix.ts`
- Hồi quy: mọi dòng Ảnh hưởng; `exam-hsa-matrix.spec.ts`, `exam-hsa-mapid.spec.ts`, `exam-hsa-structure.spec.ts`; bộ web đầy đủ trên cloud runner; bộ gate chung B7
- Rủi ro (cao): đỏ trước AC-3, AC-5, AC-6, AC-8, AC-10; xanh trước HSA-C, MD, test của HSA-D. Fixture trước và sau khi bật (B7); biên: 1, 2, 3 chùm, 0 câu điền, quy tắc trỏ nhóm về 0, bài cũ có chỉ tiêu chủ đề
- Dữ liệu test: fixture B8; ngân hàng câu dev có MAPID cho HSA Toán (seed dev, như E2E của HSA-D); E2E tạo bài và chủ đề riêng có hậu tố theo lane
- Không kiểm: luật quy tắc MAPID, bộ sinh, chuyển đổi bài cũ (HSA-D kiểm)

**Thiết kế / tham chiếu:** HSA §4a, §6, §8; `uniai/hsa/1704-v2.md`; pha 0 v2 HSA-D mục (b), (d), (e); B9; FTM R-3, R-4, R-5, R-12.

## CR-3 · Biểu mẫu Danh sách dòng lấy câu

**Đặc tả:** R-2 (AC-6 cho DS), R-7, R-8 (AC-28, AC-48, AC-53 phần THPTQG), R-9 (AC-30, AC-54 trừ phần V-ACT), R-10, R-11 (AC-33, AC-57 cho DS), R-13 (phần DS), R-15 (DS); AC-20 … AC-25, AC-28, AC-30 … AC-33, AC-40, AC-47 … AC-49, AC-51, AC-52; D-5, D-6. R-10 theo câu trả lời G1-7, R-11 theo G1-1.

**Sản phẩm:**

- Lát CR-3a:
  - `subject-forms/row-list-form.tsx`: đầu môn, bảng dòng một mục (tiêu đề phân đoạn khi có `displaySegments`), chip audio, chỉ số chỉ đọc của dòng, khối Phân bổ đối chiếu (gấp sẵn; mức áp dụng R-8 có khóa kèm lý do với `PROMPT` và chỉ tiêu nhiều môn; ẩn lớp R-11).
  - `subject-forms/row-editor.tsx`: màn sửa dòng gọn (R-7), Thêm điều kiện với các mục và trường ghi ở bảng R-7, trường mẫu đặt chỉ đọc.
  - `subject-forms/row-list-model.ts`: đọc và ghi nhóm của môn theo `id`; năm loại khóa của R-7 tách riêng, mỗi loại một lý do; ẩn lớp mà giữ dữ liệu lớp; chiều trống ghi mảng rỗng (R-8).
  - Ánh xạ đích DS ở `navigation.ts`.
  - Bật ROWS theo B7: HSA, QDA, BCA (`VAN`, `ANH`), THPTQG. Không nhập %, không tách dòng ở lát này.
- Lát CR-3b:
  - `subject-forms/row-split.ts`: luật tách R-7: chỉ cho tách khi dòng không có `slots`, `childConditions`, `matrix`, `topicQuotas`, không bị `groupIds` của chỉ tiêu mục tham chiếu, không là chùm cỡ linh hoạt, không đang tạm bỏ qua (ngược lại nút tách khóa kèm lý do); mỗi dòng con số nguyên dương; chép `shuffleUnits` đúng giá trị; con đầu giữ `id` (và `templateGroupCode`) của dòng cha, con sau `id` mới; chỉ chép trường ở bảng "Trường của dòng con" R-7; `audioRequirement` ở mục, không chép; chia nguyên, chặn tổng lệch; luật chặn lưu đăng ký ở `blocking.ts`.
  - Nhập % ở Phân bổ đối chiếu (lưu `unit: PERCENT`, chưa đủ 100% thì giữ guard `MATRIX_PERCENT` không `PUT`) và màn sửa dòng (một dòng quy trên số câu của mục, được dưới 100%, lưu số câu, không giữ %), lỗi chùm nguyên, quy đổi qua `percent.ts` (R-9).
  - Bảng kết hợp V-SAT cấp phần qua Thêm điều kiện, có xem trước chuyển đổi (R-10).
  - Bật ROWS theo B7: V-ACT `ANH`, `SCIENTIFIC_THINKING`; SP2E, SPT; HSCA; VSAT.
- `block-conditions.tsx`, `block-set-conditions.tsx`, `block-matrix-editor.tsx`, `block-switches.tsx`: thêm prop cho mục Độ khó, Khối lớp, chế độ chỉ đọc theo mẫu; mặc định giữ hành vi MD.
- Khóa `exams.subjectForm.rows.*`.
- E2E `e2e/tests/exam-subject-row-form.spec.ts`; chuyển test theo B5 (chủ CR-3).

**Đường dẫn sở hữu:**

- `apps/web-admin/src/features/exams/subject-forms/row-*.ts(x)`, `reference-block.tsx` (mới); `subject-forms/registry.ts` (chỉ hàng ROWS), `blocking.ts`, `navigation.ts` (một phần tử của DS)
- `apps/web-admin/src/features/exams/components/block-conditions.tsx`, `block-set-conditions.tsx`, `block-matrix-editor.tsx`, `block-switches.tsx`
- `apps/web-admin/src/features/exams/__tests__/subject-row-*.test.ts(x)`, `row-list-model.test.ts`, `row-split.test.ts` (mới); `issue-codes-hsca-vsat.test.tsx`, `total-change-screen.test.tsx` (ca DS, theo B5)
- `apps/web-admin/src/messages/{vi,en}.json` (khối ở B2)
- `e2e/tests/exam-subject-row-form.spec.ts` (mới); `exam-hsa-structure.spec.ts` (theo B5)

**Ngoài phạm vi:** MT (CR-2, CR-2H); luật máy chủ; khung tổng quan Khoa học HSA, danh sách môn theo mã, nhánh Tin học, màn phần; đổi mẫu (kể cả SP2E Toán Phần II); bảng kết hợp cấp toàn môn; đổi MD.

**Nghiệm thu:**

- AC-1: HSA Văn hiện 5 dòng đúng thứ tự mẫu, cột theo FTM R-7; nhóm 5 hiện 5 chùm con có loại văn bản; công tắc đảo ở nhóm 5; QDA Văn đúng 5 dòng theo thứ tự, dòng 5 là một nhóm 5 chùm × 5 có nhãn từng chùm (`slots`) và một công tắc đổi được (FTM AC 20, AC 52a) — bằng chứng: `subject-row-form.test.tsx`, ca `hsaLiteratureRows`, `qdaLiteratureRowsAndSlots` (đếm số dòng và số slot)
- AC-2: đổi Câu đơn sang Câu chùm hiện Số chùm, Số câu con; Thêm điều kiện → Độ khó ghi `block.matrix` `SEPARATE` theo số câu; MAPID (lọc) ghi `knowledgeScope`, Phân bổ theo MAPID ghi `topicQuotas` (FTM AC 21, AC 52b) (đỏ trước: mục Độ khó, Phân bổ theo MAPID chưa có trong menu) — bằng chứng: `subject-row-editor.test.tsx`, ca `switchToSetAndAddDifficulty`, `filterVersusAllocation`
- AC-3: dòng hiện chỉ số chỉ đọc (THPTQG 4 câu Đ/S: 16 ý, thang 4 điểm); ví dụ cách chấm 2B-HV còn (FTM AC 52c, AC 52d) — bằng chứng: `subject-row-form.test.tsx`, ca `readOnlyFigures`
- AC-4: khóa theo bảng R-7: H-SCA Toán, V-SAT Toán bài `SUBJECT` chỉ đọc kèm lý do, bài `PART` cùng mẫu sửa được; dòng viết đã lược của H-SCA, V-SAT Ngữ văn không có công tắc bật lại; SP2E Ngữ văn công tắc đổi được; thân `PUT` không đổi trường khóa (FTM AC 23, AC 47) (đỏ trước chỉ phần hiện khóa ở DS sau khi bật cờ; xanh trước phần máy chủ và MD) — bằng chứng: `subject-row-form.test.tsx`, ca `locksBySource`; `row-list-model.test.ts`, ca `lockedFieldsUnchangedInPayload`; máy chủ: E2E `exam-subject-row-form.spec.ts` ca `omittedReenableViaApi`, tham số hóa bằng **hai mẫu thật** H-SCA Ngữ văn và V-SAT Ngữ văn: `PUT /structure` bật lại nhóm viết đã lược, `GET` lại, chạy kiểm tra nhận `OMITTED_BLOCK_LOCKED`; ca `lockedCountViaApi` (H-SCA Toán, V-SAT Toán bài `SUBJECT`) đổi số câu nhóm khóa nhận `BLOCK_COUNT_LOCKED`; ca SP2E Ngữ văn bật/tắt "Tạm bỏ qua" lưu được, không lỗi khóa; `ExamCheckRowLockTests`, `HscaVsatExamScopeIT` chạy lại không sửa assert (hồi quy, không thay ca mẫu thật)
- AC-5: THPTQG Văn không có ô sửa; BCA Văn một dòng tự luận sửa được MAPID (FTM AC 24) — bằng chứng: `subject-row-form.test.tsx`, ca `essayCases`
- AC-6: SP2E Toán Phần II đổi được Câu đơn ↔ Câu chùm (FTM AC 25) — bằng chứng: `subject-row-editor.test.tsx`, ca `sp2eMathPartTwoSwitch`
- AC-7: V-SAT Toán phân bố 67 lệnh: nút áp dụng khóa kèm lý do, mẫu số lệnh, lưu giữ `PROMPT`/`REFERENCE`; phân bố độ khó theo câu ở Phần II bật được, lưu `REQUIRED`/`QUESTION`; "Chưa phân loại" hiện; chỉ tiêu nhiều môn khóa (FTM AC 28, AC 48) (đỏ trước) — bằng chứng: `subject-row-form.test.tsx`, ca `referenceWithApply`, `promptStaysReference`
- AC-8: SP2E Lịch sử không có khối lớp ở mọi chỗ của DS; lưu không sửa giữ phân bố lớp; tham số hóa bảng R-11 gồm cả `ECON_LAW`, `GDKTPL`; điều kiện lớp `REQUIRED` ở chỗ ẩn mở MD (FTM AC 33, AC 57 cho DS) (đỏ trước: K9 đang hiện) — bằng chứng: `subject-row-form.test.tsx`, ca `hiddenGradeKeepsData`, `hiddenGradeTable`
- AC-9: tách 4 → 4 rồi sửa một dòng con về 0 (0 + 2 + 1 + 1) báo lỗi tại dòng, không gửi `PUT`; dòng chùm cỡ linh hoạt và dòng đang tạm bỏ qua có nút tách khóa kèm lý do; dòng `shuffleUnits: false` tách ra mọi con `false` qua `PUT`/`GET` (FTM AC 51 g–i); H-SCA Anh có nút tách Đọc, HSA Văn không có thêm, tách, lên, xuống, xóa; tách Logic 12 thay dòng cha, con đầu giữ `id`, `templateGroupCode`, Suy luận 18 không đổi; tách Đọc 4 × 10 không đổi Nghe, `audioRequirement` của hai mục (Nghe `REQUIRED`, Đọc `FORBIDDEN`) giữ nguyên; V-SAT Anh một dòng; 4 chùm thành 5 dòng không chọn được; dòng có `slots`, `childConditions`, `matrix`, `topicQuotas` hoặc bị chỉ tiêu mục tham chiếu có nút tách khóa kèm lý do; tổng con lệch chặn lưu; sau tách `PUT` thành công, `GET` lại khớp, Kiểm tra không có lỗi khóa hay `slot` (FTM AC 22, AC 51) (đỏ trước) — bằng chứng: `row-split.test.ts`, ca `firstChildKeepsId`, `splitBlockedWithConstraints`, `positiveChildren`, `flexibleAndOmittedLocked`, `keepsShuffleUnits`; `subject-row-form.test.tsx`, ca `splitOnlyWhereAllowed`; E2E `exam-subject-row-form.spec.ts`, ca tách H-SCA Anh và V-ACT Logic qua `PUT`/`GET`/Kiểm tra
- AC-10: fixture dựng tại chỗ, mục 40 câu không khóa: một dòng câu đơn nhập 30% hiện "12 câu", lưu 12, `GET` lại và tải lại hiện số (không giữ %); mẫu H-SCA Toán thật bài `PART`: mẫu số là số câu của mục chứa dòng (mục 25 câu, 40% hiện "10 câu"); fixture riêng có dòng chùm cỡ 4, nhập 25% ra 10 câu báo lỗi tại dòng ngay khi bản nháp chưa đổi, Kiểm tra, Tiếp tục và mọi đường lưu không gửi request, focus về ô; bỏ thay đổi thì lưu lại được; Phân bổ đối chiếu nhập % lưu `unit: PERCENT`, tải lại vẫn %; Phân bổ đối chiếu tổng 90% không gửi `PUT`; SP2E một dòng 30% (dưới 100%) lưu được, hiện đúng mẫu số (FTM AC 30, AC 54 d–g) (đỏ trước) — bằng chứng: `subject-row-editor.test.tsx`, ca `percentStoresCount`, `rowPercentBelowHundred`, `wholeSetsOnly`; E2E `exam-subject-row-form.spec.ts`, ca `rowPercentPutGet`; `subject-form-blocking.test.tsx`, ca `rowPercentBlocksCheckWhenClean`, `discardClearsRawInput`
- AC-11: V-SAT Toán Phần I mở bảng kết hợp từ Thêm điều kiện, xem trước rồi xác nhận lưu `COMBINED`, hủy thì không đổi, mở lại vẫn DS; cấp toàn môn và kỳ khác không có mục kết hợp (FTM AC 31, AC 32, AC 49) — bằng chứng: `subject-row-form.test.tsx`, ca `combinedOnlyVsatPartLevel`
- AC-12: DS không hiện trường nào ở FTM R-13; thân `PUT` không đổi theo B4 với bài DS thật và mọi mẫu DS của fixture (FTM AC 6, AC 40) — bằng chứng: `subject-form-payload.test.tsx`, ca `rowFormSaveIsIdentity`; `row-list-model.test.ts`, ca `roundTripAllRowTemplates`; `subject-row-form.test.tsx`, ca `noRemovedFields`
- AC-13: `?block=` thật, bấm lỗi, tóm tắt của dòng DS mở đúng màn sửa dòng và đặt focus, cả khi qua Lưu và chuyển (FTM AC 59 cho DS) (đỏ trước: sau khi bật cờ, đích hôm nay là editor nhóm) — bằng chứng: `subject-form-navigation.test.tsx`, ca `rowTargets` trên `StructureEditor`; `issue-codes-hsca-vsat.test.tsx` giữ assert URL theo B5
- AC-14: luồng đầy đủ trên trình duyệt: HSA Văn sửa nhóm 5, H-SCA Anh tách dòng, V-SAT Toán kết hợp, SP2E Lịch sử lưu; bước Kiểm tra cho đúng mã ở đúng dòng, tổng, đơn vị — bằng chứng: E2E `exam-subject-row-form.spec.ts`
- AC-15: kiểm tra giao diện HSA Văn (bảng, màn sửa dòng, Thêm điều kiện mở), H-SCA Anh (audio, tách), H-SCA Toán `SUBJECT` (khóa có lý do), V-SAT Toán (kết hợp, nút khóa có lý do), Phân bổ đối chiếu gấp và mở: sáng/tối, vi/en, 390/1280, không trộn ngôn ngữ, tương phản ≥ 4,5:1 — bằng chứng: tester_visual, ảnh trong báo cáo lane; menu Thêm điều kiện đi bằng bàn phím trong `subject-row-editor.test.tsx`
- AC-16: mở môn DS thật không gọi nhiều request hơn MD của cùng môn, cùng fixture và cache; hộp chọn mở chủ động không tính (FTM mục 9) — bằng chứng: `subject-row-form.test.tsx`, ca `noExtraRequestsOnOpen`
- AC-17: THPTQG Lý Phần I (18 câu) khai độ khó 8/6/4 lưu được, Kiểm tra không lỗi tổng; khai 8/6/2 lưu được và Kiểm tra ra `SUBJECT_MATRIX_TOTAL_MISMATCH` ở đúng mục; chiều trống ghi mảng rỗng (FTM AC 53, phần THPTQG) — bằng chứng: `row-list-model.test.ts`, ca `emptyDimensionIsEmptyArray`; E2E `exam-subject-row-form.spec.ts`, ca THPTQG Lý

**Ảnh hưởng ngoài task:**

- `block-conditions.tsx`, `block-set-conditions.tsx`, `block-matrix-editor.tsx`, `block-switches.tsx` dùng ở MD (`block-editor.tsx`) → test hiện có của màn nhóm xanh không sửa assert [chủ: task này]
- Chip audio, khóa dòng của mẫu (`V202611182201`), `fixed_counts`, `fixed_block_counts`, `omitted_locked` → E2E bước Cấu trúc của H-SCA, V-SAT hiện có; `exam-hsa-structure.spec.ts`; mở lại mẫu khóa [chủ: task này]
- Luật khóa máy chủ (`STRUCTURE_COUNT_LOCKED`, `BLOCK_COUNT_LOCKED`, `OMITTED_BLOCK_LOCKED`, `reenabledBlocks` khớp theo `templateGroupCode`) → bước Kiểm tra sau khi tách dòng không phát sinh lỗi khóa; ca trong `exam-subject-row-form.spec.ts` [chủ: task này]
- Điều kiện từng chùm, theo vị trí câu con (`slots`, `childConditions`) đọc ở bộ sinh → bước Kiểm tra và sinh thử trong `exam-subject-row-form.spec.ts` [chủ: task này]
- `STRAND_KINDS` (SP2E, SPT dùng "mạch kiến thức") ở `block-editor.tsx` → DS dùng cùng nhãn; test `block-editor` hiện có [chủ: task này]
- Phân bố tham khảo (`ReferenceDistributions`, `reference-distribution.ts`) → test hiện có xanh [chủ: task này]
- Test ở B5 có chủ CR-3 → chuyển theo bảng [chủ: task này]
- `registry.ts`, `blocking.ts`, `navigation.ts` của CR-1 → `subject-form-*.test.ts(x)` xanh sau khi bật ROWS [chủ: task này]
- Màn môn MT của CR-2, CR-2H cùng dùng thành phần B1 → không sửa thành phần B1; cần đổi thì báo Advisor [chủ: CR-1]

**Kiểm thử:**

- AC-1, AC-3, AC-4, AC-5, AC-7, AC-8, AC-9, AC-11, AC-12, AC-16: `subject-row-form.test.tsx`, thành phần, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/subject-row-form.test.tsx`
- AC-2, AC-6, AC-10, AC-15 (bàn phím): `subject-row-editor.test.tsx`, thành phần, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/subject-row-editor.test.tsx`
- AC-4 (máy chủ): ca `omittedReenableViaApi`, `lockedCountViaApi` trong `exam-subject-row-form.spec.ts` (E2E qua API, hai mẫu thật); hồi quy `ExamCheckRowLockTests`, `HscaVsatExamScopeIT`, `cd backend && ./mvnw -B -ntp verify -Dit.test=HscaVsatExamScopeIT -Dtest=ExamCheckRowLockTests -Dsurefire.failIfNoSpecifiedTests=false`
- AC-4, AC-12, AC-17: `row-list-model.test.ts`, đơn vị, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/row-list-model.test.ts`
- AC-9: `row-split.test.ts`, đơn vị, `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/row-split.test.ts`
- AC-10, AC-12, AC-13: `subject-form-blocking.test.tsx`, `subject-form-payload.test.tsx`, `subject-form-navigation.test.tsx` (ca của DS)
- AC-4 (máy chủ), AC-9, AC-10, AC-14, AC-17: `exam-subject-row-form.spec.ts`, E2E trên stack lane, `pnpm --filter @thi360/e2e test tests/exam-subject-row-form.spec.ts`
- AC-15: tester_visual, các màn ở AC-15
- Hồi quy: mọi dòng Ảnh hưởng; E2E bước Cấu trúc (A6); bộ web đầy đủ trên cloud runner
- Rủi ro (cao): đỏ trước AC-2, AC-4 (phần hiện ở DS), AC-7, AC-8, AC-9, AC-10, AC-13; xanh trước khóa máy chủ, MD. Mỗi lát (CR-3a, CR-3b) có test fixture trước và sau khi bật (B7, kể cả bài có tỷ lệ hay Logic/Suy luận khi chưa bật), kiểm rơi MD, payload không đổi; biên: tách tới tối đa, chia không hết, dòng con 0, chùm linh hoạt, `shuffleUnits: false`, % làm tròn (R-142, H30), dòng khóa
- Dữ liệu test: fixture B8; seed dev có audio cho H-SCA Anh (đợt 2B); E2E tạo bài riêng có hậu tố theo lane
- Không kiểm: màn phần, khung Khoa học HSA, nhánh Tin học (không đổi)

**Thiết kế / tham chiếu:** HSA §5, §7; QDA §5, §7; BCA §4, §5; VACT §4.2, §6; SP §5, §7, §12; THPT §3–§6; HSCA §5, §6; VSAT §3–§5; 2B-HV H40–H44, H49; FTM R-7 … R-11.

## CR-4 · Luật máy chủ: giới hạn chùm và câu điền của môn Khoa học HSA, QDA

**Đặc tả:** R-12 (AC-36 … AC-39, AC-46, AC-58, AC-60), R-8 (AC-53 qua kiểm tra); D-8.

**Sản phẩm:**

- `backend/src/main/java/vn/unicom/thi360/exam/application/SubjectFormLimits.java`: bảng giới hạn theo kỳ + môn của FTM R-12.
- Hai luật trong `ExamCheckRules`: `SECTION_SET_COUNT_RANGE`, `SHORT_ANSWER_MIN`, mức lỗi, cấp môn, chặn công bố; chỉ với bài `buildMode` khác `REVIEW`; xét mục ở `sections`, `subjectPool`, `options[].sections`. Mã kỳ và `buildMode` đọc ở nơi dựng `ExamCheckRules`. Không đổi chữ ký hàm công khai mà `ExamPreviewService` (EX-2) đang gọi; cần thì thêm hàm nạp chồng.
- **Cổng công bố cho kết quả cũ (AC-58):** dấu có nghĩa: **kết quả `EXAM` đã được đánh giá và lưu dưới luật mới**, độc lập với kết luận tổng `VALID`/`INVALID`; kiểm tra một môn, một phần, hay lần kiểm không hoàn tất không đặt dấu; dấu không thay kiểm độ mới cấu trúc, cấu hình, nguồn. Có dấu thì công bố từng đường theo E24 như hôm nay (`ExamCheckAssembler` giữ kết quả từng đường và witness). Dấu lưu cùng chỗ `ExamCheckCompletion` lưu kết quả `EXAM` (kết quả dùng cho công bố), không migration, không dựa vào thời điểm deploy hay đánh dấu chung mọi bài. Trước khi làm, Advisor và lane EX-1 chốt cách lưu, ghi vào báo cáo lane. Đề nghị: `ExamCheckValidity` trả lý do `RULES_CHANGED` khi kết quả `EXAM` đã lưu không mang dấu **và** bài là bài chuẩn HSA, QDA có môn Khoa học; `ExamPublishService` đã từ chối mọi lý do khác rỗng; `ExamCheckResultQuery` (GET kiểm tra) và danh sách trạng thái đọc cùng lý do. Kiểm tra và lần công bố thất bại không đổi đường đang phục vụ hay bản đã công bố; lần công bố thành công đổi đường theo E24 (AD-60, AD-60a). Chỉ sửa vùng EX-1 sau khi EX-1 merge.
- **Web hiện đúng mã và lý do mới (AC-60):** thêm hai mã vào `CHECK_ISSUE_CODES` (`review-types.ts`), `RULES_CHANGED` vào danh sách lý do (`review-types.ts`, `review-api.ts`), nhãn lý do ở `publish-exam-dialog.tsx` để không ra `INFO_MISSING` hay `UNKNOWN`; chỉ ánh xạ và nhãn, không đổi bố cục bước Kiểm tra. Giao với FA-1/EX-1 ở ba tệp này thì lane merge sau hợp nhất, báo Advisor.
- Thông điệp ở `messages.properties`, `messages_vi.properties`, `messages_en.properties`; nhãn ở `vi.json`, `en.json` (`exams.review.issue` cho hai mã; lý do `RULES_CHANGED` ở cả cụm kiểm tra và cụm công bố).
- Bảng mã kiểm tra trong `docs/specs/2026-11-16-be-pbi-03-04.md` và `docs/specs/2026-11-17-pbi04-dot-2b.md`: thêm hai mã và lý do `RULES_CHANGED`, đúng phần được chia (EX-1, EX-2 giữ phần của mình).

**Đường dẫn sở hữu:**

- `backend/src/main/java/vn/unicom/thi360/exam/application/SubjectFormLimits.java` (mới)
- `backend/src/main/java/vn/unicom/thi360/exam/application/ExamCheckRules.java` (thêm hai luật, phần đọc mã kỳ và `buildMode`), `DefaultExamChecker.java` (truyền mã kỳ, `buildMode`)
- `ExamCheckValidity.java`, `ExamCheckCompletion.java`, `ExamCheckResultQuery.java`, `ExamCheckOrchestrator.java` (chỉ phần dấu và lý do, sau khi EX-1 merge và cách làm đã chốt)
- `apps/web-admin/src/features/exams/review-types.ts`, `review-api.ts`, `components/publish-exam-dialog.tsx` (chỉ ánh xạ mã, lý do, nhãn); `__tests__/review-api.test.ts`, `review-api-real.test.ts`, `review-real-views.test.tsx` (thêm ca), `__tests__/publish-exam-dialog.test.tsx` (mới)
- `backend/src/main/resources/messages*.properties` (khối của mình)
- `backend/src/test/java/vn/unicom/thi360/exam/ExamCheckSubjectLimitsIT.java`, `ExamCheckSubjectLimitsTemplatesIT.java`, `ExamPublishRulesChangedIT.java`, `backend/src/test/java/vn/unicom/thi360/exam/application/SubjectFormLimitsTest.java` (mới)
- `apps/web-admin/src/messages/{vi,en}.json` (khóa ở B2)
- `docs/specs/2026-11-16-be-pbi-03-04.md`, `docs/specs/2026-11-17-pbi04-dot-2b.md` (bảng mã)

**Ngoài phạm vi:** chặn lưu nháp; luật cho kỳ khác; đổi `TrimPolicy`, bộ sinh; đổi hình dạng API hay OpenAPI; sửa bản đã công bố; migration; đổi bố cục bước Kiểm tra hay hộp công bố.

**Nghiệm thu:**

- AC-1: `PUT /structure` cho QDA Lý 3 chùm lưu được; kiểm tra cấu hình ra `SECTION_SET_COUNT_RANGE`, công bố bị từ chối (FTM AC 36) (đỏ trước) — bằng chứng: `ExamCheckSubjectLimitsIT`, ca `qdaPhysicsThreeSetsBlocksPublish`
- AC-2: HSA Hóa không có câu điền ra `SHORT_ANSWER_MIN`; HSA Sử 0 câu điền không có lỗi này (FTM AC 37) (đỏ trước) — bằng chứng: `ExamCheckSubjectLimitsIT`, ca `hsaChemistryNeedsShortAnswer`, `hsaHistoryAllowsZero`
- AC-3: trên fixture công bố đủ nguồn, tổng, điểm, chỉ đổi số chùm 0/1/2/3, cỡ chùm 2/3/4 và số câu điền 0/1: đúng mã, đúng mục, mỗi ca chỉ một luật đổi kết quả — bằng chứng: `ExamCheckSubjectLimitsIT`, ca `isolatedBounds` (tham số hóa)
- AC-4: BCA Lý 3 chùm, H-SCA Lý, môn admin thêm sau, mã kỳ lạ không bị hai luật (FTM AC 38) — bằng chứng: `ExamCheckSubjectLimitsIT`, ca `otherKindsUnaffected`
- AC-5: bài ôn QDA Sinh theo BA (7 đơn + 1 chùm, không câu điền) và bài ôn HSA toàn trắc nghiệm không có hai mã; bài chuẩn cùng cấu hình sai có mã (FTM AC 46) (đỏ trước) — bằng chứng: `ExamCheckSubjectLimitsIT`, ca `reviewExamsExcluded`
- AC-6: bài dựng từ mọi mẫu chuẩn đang hoạt động của HSA, QDA (`FULL`, `PART`, `SUBJECT`; mục ở `sections`, `subjectPool`, `options[].sections`) không có hai mã mới; mẫu `REVIEW` loại trừ — bằng chứng: `ExamCheckSubjectLimitsTemplatesIT` (tham số hóa theo mẫu, so mã và mục)
- AC-7: chuỗi trên cùng bài HSA có Khoa học: kết quả `EXAM` `VALID` lưu không dấu → công bố bị từ chối `RULES_CHANGED`, GET kiểm tra trả cùng lý do → kiểm tra một môn → vẫn `RULES_CHANGED` → kiểm tra toàn bài → công bố được. QDA, kết quả tổng `INVALID` dưới luật mới: đường lỗi chưa phục vụ → không còn `RULES_CHANGED`, đường đủ điều kiện công bố được; đường lỗi đang phục vụ → từ chối theo E24, phiên bản không đổi. Qua các lần kiểm và lần công bố thất bại, bản đã công bố và đường đang phục vụ không đổi (không khẳng định sau lần công bố thành công). Bài BCA, H-SCA và bài `REVIEW` không bị, kể cả GET; HSA môn sinh 17 rồi cắt 16 (FTM AC 58 a–e) (đỏ trước: kết quả cũ hôm nay công bố được) — bằng chứng: `ExamPublishRulesChangedIT`, ca `subjectCheckDoesNotClear`, `examCheckClears`, `invalidOverallStampedE24`, `servedInvalidBlocks`, `failedOpsKeepServed`, `otherKindsAndReviewUnaffected`
- AC-8: bảng giới hạn khớp FTM R-12 — bằng chứng: `SubjectFormLimitsTest`
- AC-9: QDA Lý (mục 25 câu: 10/10/5 không lỗi, 10/5/5 ra `SUBJECT_MATRIX_TOTAL_MISMATCH` ở mục) và THPTQG Lý Phần I (18 câu: 8/6/4 không lỗi, 8/6/2 ra `SUBJECT_MATRIX_TOTAL_MISMATCH` ở mục); chiều trống là mảng rỗng không lỗi, ô 0 hợp lệ; tổng lệch ở nhóm ra `MATRIX_TOTAL_MISMATCH` (FTM AC 53) (xanh trước: luật 2B giữ nguyên) — bằng chứng: `ExamCheckSubjectLimitsIT`, ca `declaredDimensionTotals` (khẳng định mã, `sectionId`, chiều)
- AC-10: hai mã và lý do có ở ba tệp `messages*.properties` và ở `vi.json`, `en.json` (FTM AC 39) — bằng chứng: `MessageBundlesTest`; `messages.test.ts`
- AC-13: qua adapter thật: GET kiểm tra có `SECTION_SET_COUNT_RANGE`, `SHORT_ANSWER_MIN` hiện đúng mã và nhãn (không ra `INFO_MISSING`), lý do `RULES_CHANGED` hiện đúng; hộp công bố nhận 409 `CHECK_REQUIRED` lý do `RULES_CHANGED` hiện nhãn (không ra `UNKNOWN`) và dẫn tới kiểm tra lại; trang `ExamReviewCheck` render qua adapter thật (transport giả lập như `review-real-views.test.tsx:85–124`) với GET `stale` + `RULES_CHANGED` và hai lỗi mới ở hai môn khác nhau: chữ dịch vi/en của `staleReason` và `publish.errors.checkRequired`, đúng môn và đích, nút kiểm tra lại gửi kiểm tra (FTM AC 60) (đỏ trước) — bằng chứng: `review-api.test.ts`, `review-api-real.test.ts`, ca `newCodesAndRulesChanged`; `review-real-views.test.tsx`, ca `rulesChangedOnCheckPage`; `publish-exam-dialog.test.tsx`, ca `rulesChangedLeadsToRecheck`; không dùng `messages.test.ts` một mình
- AC-11: seed hiện có (`TEST-*`) không sinh lỗi mới — bằng chứng: `ExamCheckRealTemplatesIT`, `TestExamSeedIT`, `TestExamSeedComplianceIT` xanh không sửa (hồi quy, không thay AC-6)
- AC-12: OpenAPI không đổi theo B4 — bằng chứng: JSON chuẩn hóa từ `OpenApiExportIT` ở baseline và head, so bằng `diff`

**Ảnh hưởng ngoài task:**

- Mọi nơi dựng `ExamCheckRules` (`DefaultExamChecker`, `ExamCheckTemplateFacts`, `ExamCheckUnitEvidence`, `ExamCheckUnitTopicEvidence`, `ExamPreviewService`) → `ExamCheckApiIT`, `ExamCheckCompletionIT`, `ExamCheckUnitCombinationIT`, `ExamPreviewIT` xanh [chủ: task này]
- Công bố và kiểm tra của EX-1 (`ExamPublishService`, `ExamCheckValidity`, `ExamCheckController`, `ExamCheckApiModels`) → `ExamPublish*IT`, `ExamCheckApiIT` trên SHA đã có EX-1 [chủ: task này]
- Xem trước và bộ giải của EX-2 (`ExamPreviewService`, `SolverInputBuilder`) → `ExamPreview*IT` trên SHA đã có EX-2 [chủ: task này]
- Bộ dữ liệu bài đã công bố của DS (đợt 3) → nếu DS đã merge, `exam/devdata` chạy kiểm tra không lỗi mới và không bị `RULES_CHANGED` ngoài ý muốn; nếu DS merge sau, DS chạy lại [chủ: task này hoặc DS, theo thứ tự merge]
- Hiệu năng kiểm tra (`ExamCheckPerfIT`) → xanh, không tăng quá 5% [chủ: task này]
- Thông điệp backend dùng chung với EX-1, ID-2, FU → hợp nhất đúng ba tệp, `MessageBundlesTest` trên SHA ghép; nếu ID-2 chưa merge, lane merge sau chịu hợp nhất [chủ: task này]
- Bước Kiểm tra và hộp công bố trên web (`review-types.ts`, `review-api.ts`, `publish-exam-dialog.tsx`, `exam-review-findings.tsx`) → test hiện có của review và công bố xanh không sửa assert; AC-13 [chủ: task này]
- `ExamCheckCompletion`, `ExamCheckResultQuery`, danh sách trạng thái bài đọc `ExamCheckValidity` → `ExamCheckCompletionIT`, `ExamCheckApiIT`, test danh sách bài xanh; bài không thuộc HSA, QDA có Khoa học không đổi trạng thái [chủ: task này]
- Giới hạn phía web của CR-2, CR-2H (`limits.ts`) → hai task đó nộp sau và so hai bảng [chủ: CR-2, CR-2H]

**Kiểm thử:**

- AC-1, AC-2, AC-3, AC-4, AC-5, AC-9: `ExamCheckSubjectLimitsIT`, tích hợp, `./mvnw -B -ntp verify -Dit.test=ExamCheckSubjectLimitsIT -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false`
- AC-6: `ExamCheckSubjectLimitsTemplatesIT`, tích hợp, cùng dạng lệnh
- AC-7: `ExamPublishRulesChangedIT`, tích hợp, cùng dạng lệnh
- AC-8: `SubjectFormLimitsTest`, đơn vị, `./mvnw -B -ntp test -Dtest=SubjectFormLimitsTest`
- AC-10: `MessageBundlesTest`, đơn vị; `pnpm --filter @thi360/web-admin test src/messages/messages.test.ts`
- AC-13: `pnpm --filter @thi360/web-admin test src/features/exams/__tests__/review-api.test.ts src/features/exams/__tests__/review-api-real.test.ts src/features/exams/__tests__/review-real-views.test.tsx src/features/exams/__tests__/publish-exam-dialog.test.tsx`
- AC-11: `ExamCheckRealTemplatesIT`, `TestExamSeedIT`, `TestExamSeedComplianceIT`, tích hợp
- AC-12: `OpenApiExportIT` ở baseline và head, so JSON chuẩn hóa
- Hồi quy: mọi dòng Ảnh hưởng; `./mvnw -B -ntp verify` đầy đủ trên cloud runner, trên SHA đã có EX-1 và EX-2
- Rủi ro (cao): đỏ trước AC-1, AC-2, AC-5, AC-7, AC-13; xanh trước AC-9, AC-11
- Dữ liệu test: fixture công bố dựng tại chỗ trong IT (nguồn, tổng, điểm đủ), từ mẫu HSA, QDA, BCA, H-SCA có sẵn; Testcontainers riêng của lớp; có và không có quyền như test API hiện có
- Không kiểm: chặn lưu nháp (ngoài phạm vi, FTM mục 11)

**Thiết kế / tham chiếu:** QDA §6a (dòng 141), §8; HSA §6b–§6c, §8; 2B bảng mã kiểm tra; FTM R-12; AD-60a (E24).

## CR-5 · Nghiệm thu, DESIGN.md

**Đặc tả:** toàn bộ FTM; trọng tâm R-13, R-14, R-15 (AC-40, AC-41, AC-42).

**Sản phẩm:**

- Báo cáo nghiệm thu FTM: bảng FTM AC 1 … AC 60 (trừ AC 43) ↔ test, lệnh, log, kết quả trên SHA nhánh tích hợp sau CR-1 … CR-4 và CR-2H (SHA đó chứa EX-1, EX-2, HSA-D, và FU nếu đã merge).
- `DESIGN.md` mục 5.14: thay các gạch đầu "Soạn theo môn", "Nhóm lấy câu" bằng MT, DS, Thêm điều kiện, Phân bổ đối chiếu, khóa và lý do, dòng chữ MD; ghi rõ MD còn dùng ở đâu.
- Kiểm tra giao diện toàn bộ (FTM AC-42), gồm notice MD, khối gấp/mở, lỗi có focus, khóa dòng và nhóm viết, 390/1280 × sáng/tối × vi/en.
- Danh sách việc để lại (nếu có) cho Advisor.

**Đường dẫn sở hữu:**

- `DESIGN.md` (mục 5.14)
- `docs/acceptance/form-theo-mon.md` (mới)
- `e2e/tests/exam-subject-forms-qa.spec.ts` (mới, nếu cần ca đầu cuối chưa có)

**Ngoài phạm vi:** tính năng mới; sửa lỗi lớn (báo Advisor, mở task con).

**Nghiệm thu:**

- AC-1: mọi FTM AC (AC 1 … AC 60, trừ AC 43 đã bỏ ở bản 4) có bằng chứng trên SHA cuối của nhánh tích hợp, đối chiếu nội dung test chứ không theo tên tệp — bằng chứng: bảng trong `docs/acceptance/form-theo-mon.md`
- AC-2: lệnh chuẩn web, backend, E2E bước Cấu trúc xanh trên SHA đó; báo cáo ghi số test chạy, đạt, bỏ qua — bằng chứng: báo cáo cloud runner; lệnh E2E ở A6 (có `--list`)
- AC-3: mọi dòng Ảnh hưởng của CR-1 … CR-4 chạy lại xanh trên SHA đó; test B5 đúng xử lý; mục FU ở B6 đã chạy lại nếu FU merge — bằng chứng: bảng hồi quy trong báo cáo
- AC-4: kiểm tra giao diện FTM AC 42 đạt (sáng/tối, vi/en, 390/1280, không trộn ngôn ngữ), ảnh mỗi ô có tên SHA — bằng chứng: tester_visual, ảnh trong báo cáo
- AC-5: `DESIGN.md` mục 5.14 khớp màn thật — bằng chứng: review tài liệu đối chiếu ảnh của AC-4

**Ảnh hưởng ngoài task:**

- none: task này chỉ sửa tài liệu và thêm test; đã soát `DESIGN.md` mục 5.13, 5.14 và `docs/acceptance/` (không có tệp FTM cũ).

**Kiểm thử:**

- AC-1: đối chiếu đặc tả với bằng chứng, viết báo cáo
- AC-2: `pnpm install --frozen-lockfile && pnpm lint && pnpm typecheck && pnpm test && pnpm build`; `cd backend && ./mvnw -B -ntp verify`; `pnpm --filter @thi360/e2e exec playwright test 'tests/exam-.*[.]spec[.]ts$' --list` rồi chạy không `--list`
- AC-3: chạy lại mọi lệnh ở mục Kiểm thử của CR-1 … CR-4
- AC-4: tester_visual với `e2e/tests/helpers/visual-matrix.ts`, dữ liệu theo quy ước `data-exam-content`
- AC-5: review `DESIGN.md` so với ảnh AC-4
- Không kiểm: hành vi ngoài bước Cấu trúc (không đổi), trừ hồi quy do việc ghi cấu trúc hoặc cổng kiểm tra tác động

**Thiết kế / tham chiếu:** `DESIGN.md` mục 5.13, 5.14; FTM.

# Phần E: Rủi ro và cách xử lý

| Rủi ro | Dấu hiệu | Làm gì |
|---|---|---|
| Mẫu chuẩn của một cặp không khớp biểu mẫu của nó | `subject-form-fit.test.ts` đỏ với fixture | Không sửa mẫu, không nới `fitsForm`. Ghi mẫu và lý do, báo Advisor; để cặp đó dùng MD phải sửa đặc tả và AC với người duyệt |
| Khứ hồi đổi dữ liệu (thân `PUT` khác bản đã nạp) | ca `*SaveIsIdentity`, `roundTrip*` đỏ | Dừng; sửa model, không sửa test |
| Thành phần quy tắc MAPID của HSA-D không nhúng được nguyên trạng vào MT (props gắn với MD) | CR-2H không dựng được bảng với cùng props, hoặc PR của HSA-D thiếu mục ở B9 | Không sao chép, không sửa tại chỗ; gửi HSA-D (hoặc Advisor) yêu cầu thêm prop; HSA giữ MD tới khi có |
| HSA-D forward-merge muộn | PR 2 của HSA-D chưa vào `integration/dot-3` khi CR-2 xong | CR-2H chưa mở (C1); Advisor báo ngày; CR-2, CR-3 chạy tiếp, HSA giữ MD |
| HSA-D đổi hình dạng `mapidRules` sau khi CR-2H mở | diff `ExamSection`, `mapid-rules.ts` trên `integration/dot-3` | CR-2H rebase, chạy lại test nhúng và test của HSA-D; không vá hình dạng ở FTM |
| EX-1 merge muộn (EX-2 đã merge) | ngày merge dự kiến trễ | CR-4 làm xong và chờ nộp; CR-2 giữ cờ MT tắt; Advisor báo đường tới hạn (C3) |
| Cách làm cổng `RULES_CHANGED` không chốt được với EX-1 | tranh chấp `ExamPublishService`/`ExamCheckValidity` | Advisor quyết; không sửa vùng EX-1 khi chưa chốt |
| FU (đợt 3) sửa cùng màn | xung đột khi merge `apps/web-admin/src/features/exams/**` | Theo B6; lane merge sau hợp nhất, chạy lại test MD và biểu mẫu |
| EX-1 hoặc FU sửa `ExamCheckRules` cùng lúc CR-4 | xung đột ở luật kiểm tra | CR-4 rebase, chạy lại `ExamCheck*IT`; không gộp logic của lane khác |
| Luật R-12 làm bài demo hoặc seed đang có không công bố lại được | IT mẫu hay seed đỏ | Dừng, báo Advisor; không sửa seed trong CR-4 |
| Lát cắt gặp ca dữ liệu bế tắc | AC của lát không đạt | Giữ cờ tắt (B7), báo Advisor; không nhân đôi editor, không mở task hình thức |
| Máy bro, cty quá tải | cổng tải của `team-rules.md` | Chạy test nặng trên cloud runner |
| Đổi guard % ma trận (cho lưu dưới 100%) được yêu cầu | yêu cầu mới từ FU hoặc người duyệt | Không làm trong FTM; là thay đổi nghiệp vụ, quyết cùng FU (FTM R-9) |

# Nhật ký review

| Vòng | Ngày | Người review | Chặn | Nên sửa | Nhỏ | Không nhận, lý do | Báo cáo |
|---|---|---|---|---|---|---|---|
| 1 | 2026-11-19 | Codex | 5 | 9 | 1 | Không | `.thi360-dev/dot-3/review-ftm/codex-round1.md`, `fix-round1.md` |
| 2 | 2026-11-19 | Codex | 3 | 6 | 0 | Không | `.thi360-dev/dot-3/review-ftm/codex-round2.md`, `fix-round2.md` |
| 3 | 2026-11-20 | Codex | 1 | 5 | 1 | P3-03 không còn áp dụng (so tập ứng viên MAPID chuyển cho HSA-D, AD-67/68); các điểm khác đã sửa | `.thi360-dev/dot-3/review-ftm/codex-round3.md`, `fix-round3.md` |
