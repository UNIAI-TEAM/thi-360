import { expect, type APIResponse, type Locator, type Page } from "@playwright/test";
import { ADMIN_URL, type AdminApi, RUN_ID } from "./editor-qa";

/**
 * Dùng chung cho các kịch bản E2E của nghiệm thu đợt 2B (`wave-2b-*.spec.ts`). Các kịch bản đi qua giao diện: bước 1 tạo
 * bài, bước 2 mở và lưu, "Tiếp tục" kiểm tra, tab Đề xem trước sinh thử (đổi mã, đổi nhánh ở đó), trang Phiên bản công bố.
 * API chỉ đọc kết quả để khẳng định (bài, cấu trúc) và dùng cho việc không thuộc phần được thử: đăng nhập, công bố bị từ
 * chối. Mọi giá trị nghiệp vụ (số câu, dải câu, điểm, mã kiểm tra) do từng spec khẳng định.
 */

/** Kiểm tra và sinh thử chạy bộ giải: có thể mất vài chục giây trên ngân hàng mẫu, nên mỗi lời gọi đồng bộ có hạn riêng. */
export const SOLVER_TIMEOUT_MS = 280_000;
export const SCENARIO_TIMEOUT_MS = 900_000;

/** Mã bài duy nhất theo từng lần chạy (tối đa 30 ký tự như máy chủ cho phép). */
export function examCode(prefix: string): string {
  return `${prefix}-${RUN_ID}`.toUpperCase().slice(0, 30);
}

/* ---------- Kiểu của phản hồi (chỉ các trường mà kịch bản đọc) ---------- */

export interface CreatedExam {
  id: string;
  code: string;
  revision: number;
  templateCode?: string | null;
  status: string;
  questionCount: number;
  maxScore?: number | null;
  durationMinutes?: number | null;
  servingVersion?: number | null;
}

export interface StructureBlock {
  id?: string;
  name?: string;
  questionType?: string | null;
  questionCount?: number | null;
  requiredPointsPerQuestion?: number | null;
  [key: string]: unknown;
}

export interface StructureSection {
  /** Chỉ tiêu khối lớp và độ khó cấp môn (mẫu QDA_FULL khai cho Lý, Hóa, Sinh, Sử, Địa; Tiếng Anh không có). */
  matrix?: { byGrade?: Array<{ code: string; value: number }>; byDifficulty?: Array<{ code: string; value: number }> } | null;
  id: string;
  subjectCode: string;
  questionCount?: number | null;
  blocks?: StructureBlock[];
  [key: string]: unknown;
}

export interface StructureOption {
  id: string;
  kind?: string;
  code?: string;
  sections?: StructureSection[];
}

export interface StructurePart {
  id: string;
  name?: string;
  questionCount?: number | null;
  durationMinutes?: number | null;
  maxPoints?: number | null;
  sections?: StructureSection[];
  subjectPool?: StructureSection[];
  options?: StructureOption[];
  [key: string]: unknown;
}

export interface SectionState {
  partId?: string;
  sectionId?: string;
  state?: "INCOMPLETE" | "CHECKED" | "NEEDS_CHECK";
  subjectCode?: string;
}

export interface Structure {
  revision: number;
  parts: StructurePart[];
  examQuotas?: unknown;
  sectionStates?: SectionState[] | null;
}

export interface CheckIssue {
  code?: string;
  severity?: string;
  subjectCode?: string;
  group?: string;
  affectedCodes?: string[];
}

export interface CheckChoice {
  code?: string;
  kind?: string;
  partId?: string;
  optionId?: string;
}

export interface CheckPath {
  pathId: string;
  result: string;
  choices?: CheckChoice[];
  issues?: CheckIssue[];
  totalQuestions?: number;
  totalPoints?: number;
}

export interface CheckResult {
  status: string;
  paths: CheckPath[];
  groups?: Array<{ group?: string; status?: string; issues?: CheckIssue[] }>;
  sectionStates?: SectionState[] | null;
  issueCount?: number;
  totalPoints?: number;
  maxScore?: number;
}

export interface PreviewQuestionLite {
  code?: string;
  number?: number;
  points?: number;
}

export interface PreviewItemLite {
  kind: "QUESTION" | "SET";
  question?: PreviewQuestionLite;
  children?: PreviewQuestionLite[];
}

export interface PreviewBlockLite {
  blockName?: string;
  subjectCode?: string;
  questionType?: string;
  firstNumber?: number;
  lastNumber?: number;
  questionCount?: number;
  setCount?: number | null;
  setSize?: number | null;
  manualGrading?: boolean;
  items?: PreviewItemLite[];
}

export interface PreviewPartLite {
  partId: string;
  name?: string;
  firstNumber?: number;
  lastNumber?: number;
  points?: number;
  maxPoints?: number | null;
  blocks?: PreviewBlockLite[];
}

export interface PreviewResultLite {
  status: string;
  reason?: string;
  issues?: CheckIssue[];
  seed?: number;
  previewId?: string | null;
  path?: { pathId?: string; choices?: CheckChoice[] };
  paper: {
    totalQuestions: number;
    totalPoints: number;
    hasManualGrading?: boolean;
    parts: PreviewPartLite[];
  };
}

/* ---------- API ---------- */

async function body<T>(response: APIResponse, expected: number): Promise<T> {
  const text = await response.text();
  expect(response.status(), text).toBe(expected);
  return JSON.parse(text) as T;
}

export async function getExam(admin: AdminApi, id: string): Promise<CreatedExam> {
  return body<CreatedExam>(await admin.api.get(`${ADMIN_URL}/api/v1/admin/exams/${id}`), 200);
}

export async function getStructure(admin: AdminApi, id: string): Promise<Structure> {
  return body<Structure>(await admin.api.get(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`), 200);
}

/** Mọi mục (môn) của cấu trúc: môn của phần, môn của kho `subjectPool` và môn trong phương án. */
export function allSections(structure: Structure): StructureSection[] {
  return structure.parts.flatMap((part) => [
    ...(part.sections ?? []),
    ...(part.subjectPool ?? []),
    ...(part.options ?? []).flatMap((option) => option.sections ?? []),
  ]);
}

/** Mọi lỗi và cảnh báo trong kết quả kiểm tra (nhóm, đường, khối, môn), mỗi lỗi một lần. */
export function collectIssues(value: unknown): CheckIssue[] {
  const found: CheckIssue[] = [];
  const walk = (node: unknown): void => {
    if (Array.isArray(node)) {
      node.forEach(walk);
      return;
    }
    if (node === null || typeof node !== "object") return;
    for (const [key, child] of Object.entries(node)) {
      if (key === "issues" && Array.isArray(child)) found.push(...(child as CheckIssue[]));
      else walk(child);
    }
  };
  walk(value);
  return found;
}

export function issueCodes(value: unknown): string[] {
  return [...new Set(collectIssues(value).map((issue) => issue.code ?? ""))].sort();
}

/** Lỗi chặn (mức `ERROR`): cảnh báo như `MANUAL_GRADING_REQUIRED`, `DISTRIBUTION_DEVIATION` không tính. */
export function blockingCodes(value: unknown): string[] {
  return [...new Set(collectIssues(value).filter((issue) => issue.severity === "ERROR").map((issue) => issue.code ?? ""))].sort();
}

/** Đường của kết quả kiểm tra có lựa chọn mang mã `code` (ví dụ `QD02`, `CA1`, `CS`). */
export function pathWithChoice(check: CheckResult, code: string): CheckPath {
  const path = check.paths.find((item) => (item.choices ?? []).some((choice) => choice.code === code));
  expect(path, `không có đường chứa lựa chọn ${code}: ${JSON.stringify(check.paths.map((item) => item.choices))}`).toBeDefined();
  return path!;
}

/** Công bố các đường `pathIds` ở bản hiện tại; trả phản hồi thô để kịch bản khẳng định mã trạng thái và mã lỗi. */
export async function publishApi(admin: AdminApi, id: string, pathIds: string[]): Promise<APIResponse> {
  const exam = await getExam(admin, id);
  return admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/publish`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: { pathIds, expectedRevision: exam.revision },
    timeout: SOLVER_TIMEOUT_MS,
  });
}

/* ---------- Đề thử: đọc kết quả ---------- */

export interface PaperQuestion {
  partIndex: number;
  blockName: string;
  subjectCode: string;
  code: string;
  number: number;
  points: number;
}

/** Mọi câu của đề thử theo thứ tự (câu con của chùm được trải ra), kèm phần, nhóm và môn. */
export function paperQuestions(result: PreviewResultLite): PaperQuestion[] {
  const out: PaperQuestion[] = [];
  result.paper.parts.forEach((part, partIndex) => {
    for (const block of part.blocks ?? []) {
      for (const item of block.items ?? []) {
        const questions = item.kind === "SET" ? (item.children ?? []) : item.question ? [item.question] : [];
        for (const question of questions) {
          out.push({
            partIndex,
            blockName: block.blockName ?? "",
            subjectCode: block.subjectCode ?? "",
            code: question.code ?? "",
            number: question.number ?? 0,
            points: question.points ?? 0,
          });
        }
      }
    }
  });
  return out;
}

/** Số câu theo môn trong phần `partIndex` (hoặc cả đề khi bỏ trống). */
export function countBySubject(result: PreviewResultLite, partIndex?: number): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const question of paperQuestions(result)) {
    if (partIndex !== undefined && question.partIndex !== partIndex) continue;
    counts[question.subjectCode] = (counts[question.subjectCode] ?? 0) + 1;
  }
  return counts;
}

/** Đề sinh hợp lệ: đã sinh, số câu trải ra khớp `totalQuestions`, không câu nào xuất hiện hai lần. */
export function expectWellFormedPaper(result: PreviewResultLite): PaperQuestion[] {
  expect(result.status, JSON.stringify({ reason: result.reason, issues: result.issues })).toBe("GENERATED");
  const questions = paperQuestions(result);
  expect(questions.length, "số câu trải ra khác totalQuestions").toBe(result.paper.totalQuestions);
  const codes = questions.map((question) => question.code);
  expect(new Set(codes).size, "đề có câu lặp").toBe(codes.length);
  return questions;
}

/* ---------- Giao diện: bước 1 (tạo), bước 2 (cấu trúc), kiểm tra, đề thử, công bố ---------- */

export type UiExamKind = "BCA" | "HSA" | "QDA" | "SP2E" | "SPT" | "THPTQG" | "VACT";

/** Tên kỳ thi trong danh mục kết thúc bằng mã trong ngoặc ("Đánh giá năng lực ĐHSPHN (QDA)"); V-ACT viết "V-ACT". */
const KIND_SUFFIX: Record<UiExamKind, string> = {
  BCA: "BCA",
  HSA: "HSA",
  QDA: "QDA",
  SP2E: "SP2E",
  SPT: "SPT",
  THPTQG: "THPTQG",
  VACT: "V-ACT",
};

/** Tên tiếng Việt của môn trong danh mục dữ liệu mẫu: ô chọn Môn ở bước 1, danh sách môn ở bước 2. */
export const SUBJECT_NAME: Record<string, string> = {
  TOAN: "Toán học",
  VAN: "Ngữ văn",
  ANH: "Tiếng Anh",
  LY: "Vật lí",
  HOA: "Hóa học",
  SINH: "Sinh học",
  SU: "Lịch sử",
  DIA: "Địa lí",
  INFORMATICS: "Tin học",
};

export interface UiExamSpec {
  kind: UiExamKind;
  scope: "FULL" | "SUBJECT";
  /** Mã môn (khóa của `SUBJECT_NAME`) khi phạm vi là Theo môn. */
  subject?: string;
  /** Chọn thẻ "Tự thiết lập bài ôn" (chỉ Theo môn). */
  review?: boolean;
  code: string;
  name: string;
  /** Câu báo phải hiện ở bước 1 sau khi chọn xong kỳ thi, phạm vi, môn (ví dụ "chỉ lưu nháp"). */
  notice?: string;
  /** Số nhập vào ô của bảng tổng quan (bài ôn không có số mặc định). Bỏ trống: giữ số của mẫu. */
  overview?: { questionCount?: number; durationMinutes?: number; maxScore?: number };
}

async function fillOverviewCell(page: Page, name: RegExp, value: number | undefined): Promise<void> {
  if (value === undefined) return;
  const cell = page.getByRole("textbox", { name }).first();
  await expect(cell, `bảng tổng quan không có ô ${String(name)}`).toBeVisible();
  await cell.fill(String(value));
}

/**
 * Bước 1 ở giao diện: chọn kỳ thi, phạm vi, môn, cách dựng bài, nhập tên và mã, "Lưu và tiếp tục". Trả mã bài (id) đọc từ
 * địa chỉ của bước 2.
 */
export async function createExamInUi(page: Page, spec: UiExamSpec): Promise<string> {
  await page.goto(`${ADMIN_URL}/exams/new`);
  await expect(page.getByRole("form", { name: "Tạo bài thi" })).toBeVisible();
  await page.getByRole("combobox", { name: /^Kỳ thi/ }).click();
  await page.getByRole("option", { name: new RegExp(`\\(${KIND_SUFFIX[spec.kind]}\\)`) }).click();
  // Kỳ thi chỉ có một phạm vi thì không có nhóm radio (chỉ một dòng chữ).
  // Đợi bước phạm vi hiện ra (nhóm radio "Phạm vi" hoặc dòng "chỉ có phạm vi này") rồi mới đếm radio, để không bỏ qua khi
  // nhóm radio mount trễ một lượt render.
  await expect(
    page.getByRole("group", { name: /^Phạm vi/ }).or(page.getByText("Kỳ thi này chỉ có phạm vi này")),
    "bước chọn phạm vi chưa hiện",
  ).toBeVisible();
  const scopeRadio = page.getByRole("radio", { name: spec.scope === "FULL" ? /^Toàn bài/ : /^Theo môn/ });
  if ((await scopeRadio.count()) > 0) await scopeRadio.check();
  if (spec.subject) {
    await page.getByRole("combobox", { name: /^Môn/ }).click();
    await page.getByRole("option", { name: SUBJECT_NAME[spec.subject]!, exact: true }).click();
  }
  if (spec.review) await page.getByRole("radio", { name: /^Tự thiết lập bài ôn/ }).check();
  if (spec.notice) await expect(page.getByText(spec.notice)).toBeVisible();

  const countCell = page.getByRole("textbox", { name: /^Số câu của/ }).first();
  await expect(countCell).toBeVisible();
  if (!spec.review) await expect(countCell).not.toHaveValue("");
  await fillOverviewCell(page, /^Số câu của/, spec.overview?.questionCount);
  await fillOverviewCell(page, /^Thời lượng (của|chung)/, spec.overview?.durationMinutes);
  await fillOverviewCell(page, /^Điểm (của|tối đa)/, spec.overview?.maxScore);

  await page.getByRole("textbox", { name: /Mã bài thi/ }).fill(spec.code);
  await page.getByRole("textbox", { name: /Tên bài thi/ }).fill(spec.name);
  await page.getByRole("button", { name: "Lưu và tiếp tục" }).click();

  // Form có ô sai thì "Lưu và tiếp tục" không gửi gì: báo ngay tên ô sai thay vì chờ hết hạn của cả kịch bản.
  const form = page.getByRole("form", { name: "Tạo bài thi" });
  const invalid = form.locator('[aria-invalid="true"]');
  const stepTwo = /\/exams\/[^/]+\/structure$/;
  const outcome = await Promise.race([
    page.waitForURL(stepTwo, { timeout: 20_000 }).then(() => "created" as const, () => "timeout" as const),
    invalid.first().waitFor({ state: "attached", timeout: 20_000 }).then(() => "invalid" as const, () => "timeout" as const),
  ]);
  if (outcome === "invalid") {
    const fields = await invalid.evaluateAll((elements) =>
      elements.map((element) => {
        const input = element as HTMLInputElement;
        return input.getAttribute("aria-label") ?? input.labels?.[0]?.textContent?.trim() ?? input.id;
      }),
    );
    const alerts = await form.getByRole("alert").allTextContents();
    throw new Error(`Bước 1 không gửi được, ô sai: ${fields.join("; ")}. ${alerts.join(" ").trim()}`);
  }
  await page.waitForURL(stepTwo, { timeout: 20_000 });
  return /\/exams\/([^/]+)\/structure$/.exec(page.url())![1]!;
}

/** Bước 2 vừa mở: dòng "Tổng số câu" của khối Tổng hợp cấu trúc khớp `questions`. */
export async function expectStructureQuestions(page: Page, questions: RegExp): Promise<void> {
  const summary = page.getByLabel("Tổng hợp cấu trúc");
  await expect(summary.locator("div").filter({ has: page.getByText("Tổng số câu", { exact: true }) }).locator("dd")).toHaveText(questions);
}

/** Bấm "Lưu" của bước 2 và đợi máy chủ nhận (PUT 200); sau lưu không còn thay đổi chưa lưu nên nút Lưu tắt. */
export async function saveStructureInUi(page: Page, examId: string): Promise<void> {
  const save = page.getByRole("button", { name: "Lưu", exact: true });
  const [response] = await Promise.all([
    page.waitForResponse((item) => item.url().endsWith(`/exams/${examId}/structure`) && item.request().method() === "PUT", {
      timeout: SOLVER_TIMEOUT_MS,
    }),
    save.click(),
  ]);
  expect(response.status(), await response.text()).toBe(200);
  await expect(save).toBeDisabled();
}

/**
 * Bước 2: mở nhóm lấy câu đầu tiên (đường dẫn `?block=` là cách màn mở lại đúng mục), đổi tên nhóm rồi bấm Lưu. Nút Lưu
 * của bản mẫu nguyên vẹn tắt, nên đổi tên là giá trị sửa được vô hại để lưu thật. Chỉ đọc cấu trúc ở API để chọn nhóm và
 * để xác nhận tên đã lưu. Trả nhóm sau khi đổi tên.
 */
export async function renameFirstBlockAndSaveInUi(page: Page, admin: AdminApi, examId: string): Promise<{ id: string; name: string }> {
  const blocks = allSections(await getStructure(admin, examId))
    .flatMap((section) => section.blocks ?? [])
    .filter((item) => item.id && item.name);
  // Ưu tiên nhóm đang tính điểm; bài chỉ còn nhóm tạm bỏ qua (THPTQG Ngữ văn) thì sửa nhóm đầu tiên.
  const block = blocks.find((item) => item.omitted !== true && (item.questionCount ?? 0) > 0) ?? blocks[0];
  expect(block, "bài không có nhóm lấy câu để sửa").toBeDefined();
  const renamed = `${block!.name} 2B`;
  await page.goto(`${ADMIN_URL}/exams/${examId}/structure?block=${encodeURIComponent(block!.id!)}`);
  const nameField = page.getByRole("textbox", { name: /^Tên nhóm/ });
  await expect(nameField).toHaveValue(block!.name!);
  await nameField.fill(renamed);
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeVisible();
  await saveStructureInUi(page, examId);
  const saved = allSections(await getStructure(admin, examId))
    .flatMap((section) => section.blocks ?? [])
    .find((item) => item.id === block!.id);
  expect(saved?.name, "tên nhóm đã lưu").toBe(renamed);
  return { id: block!.id!, name: renamed };
}

/**
 * "Tiếp tục" của bước 2: kiểm tra cả bài rồi sang tab Kiểm tra nếu không có lỗi chặn; có lỗi chặn thì ở lại bước 2. Trả kết
 * quả kiểm tra mà giao diện nhận từ máy chủ.
 */
export async function continueToReviewInUi(page: Page, examId: string): Promise<CheckResult> {
  const [response] = await Promise.all([
    page.waitForResponse((item) => item.url().endsWith(`/exams/${examId}/check`) && item.request().method() === "POST", {
      timeout: SOLVER_TIMEOUT_MS,
    }),
    page.getByRole("button", { name: "Tiếp tục", exact: true }).click(),
  ]);
  expect(response.status(), await response.text()).toBe(200);
  const check = (await response.json()) as CheckResult;
  if (blockingCodes(check).length === 0) await page.waitForURL(/\/exams\/[^/]+\/review/);
  return check;
}

/** Mở một trang con của bài bằng thanh tab (Thông tin chung, Cấu trúc và ma trận, Kiểm tra và xem trước, Phiên bản và công bố). */
export async function openEditorTab(page: Page, label: string, url: RegExp): Promise<void> {
  await page.getByRole("navigation", { name: "Các phần của bài thi" }).getByRole("link", { name: label }).click();
  await page.waitForURL(url);
}

/** Tab Kiểm tra cấu hình: dòng tóm tắt "<hợp lệ> / <tổng> hợp lệ" của các đường. */
export async function expectValidPaths(page: Page, valid: number, total: number): Promise<void> {
  await expect(page.getByText(`${valid} / ${total} hợp lệ`)).toBeVisible({ timeout: SOLVER_TIMEOUT_MS });
}

/** Sang tab Đề xem trước của trang Kiểm tra và xem trước; trả vùng nội dung của tab. */
export async function openPreviewTab(page: Page): Promise<Locator> {
  await page.getByRole("tab", { name: "Đề xem trước" }).click();
  const panel = page.getByRole("tabpanel", { name: "Đề xem trước" });
  await expect(panel).toBeVisible();
  return panel;
}

/** Chọn một mã hoặc nhánh ở ô "Đường lựa chọn" của tab Đề xem trước; `choice` khớp chữ của dòng chọn. */
export async function selectPathInUi(page: Page, panel: Locator, choice: RegExp): Promise<void> {
  await panel.getByRole("combobox", { name: "Đường lựa chọn" }).click();
  await page.getByRole("option", { name: choice }).click();
}

/** Bấm nút của tab Đề xem trước và bắt phản hồi sinh thử: trả thân yêu cầu và kết quả. */
export async function clickAndCapturePreview(
  page: Page,
  examId: string,
  button: Locator,
): Promise<{ request: Record<string, unknown>; result: PreviewResultLite }> {
  const [response] = await Promise.all([
    page.waitForResponse((item) => item.url().endsWith(`/exams/${examId}/preview`) && item.request().method() === "POST", {
      timeout: SOLVER_TIMEOUT_MS,
    }),
    button.click(),
  ]);
  expect(response.status(), await response.text()).toBe(200);
  return {
    request: response.request().postDataJSON() as Record<string, unknown>,
    result: (await response.json()) as PreviewResultLite,
  };
}

/** Kết quả một lần sinh thử ở giao diện: vùng nội dung của tab, thân yêu cầu và kết quả máy chủ trả. */
export interface UiPreview {
  panel: Locator;
  request: Record<string, unknown>;
  result: PreviewResultLite;
}

/**
 * Sinh đề lần đầu ở tab Đề xem trước (nút "Sinh đề xem trước"), tùy chọn chọn trước một mã hoặc nhánh `choice`. Trả vùng
 * nội dung, thân yêu cầu và kết quả.
 */
export async function generateInUi(page: Page, examId: string, choice?: RegExp): Promise<UiPreview> {
  const panel = await openPreviewTab(page);
  if (choice) await selectPathInUi(page, panel, choice);
  const captured = await clickAndCapturePreview(page, examId, panel.getByRole("button", { name: "Sinh đề xem trước" }));
  return { panel, ...captured };
}

/** Đổi mã hoặc nhánh trên đề thử đang xem rồi bấm nút `button` (ví dụ "Sinh đề thử mới", "Đổi mã, sinh lại …"). */
export async function switchPathInUi(
  page: Page,
  examId: string,
  panel: Locator,
  choice: RegExp,
  button: RegExp,
): Promise<{ request: Record<string, unknown>; result: PreviewResultLite }> {
  await selectPathInUi(page, panel, choice);
  return clickAndCapturePreview(page, examId, panel.getByRole("button", { name: button }));
}

/**
 * Công bố ở trang Phiên bản và công bố: nút "Công bố", hộp thoại, "Công bố N đường lựa chọn". Trả phản hồi của máy chủ
 * (đã khẳng định 200).
 */
export async function publishInUi(page: Page, examId: string): Promise<{ version: number; publishedPathIds: string[] }> {
  await openEditorTab(page, "Phiên bản và công bố", /\/exams\/[^/]+\/versions/);
  await page.getByRole("button", { name: "Công bố", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  const [published] = await Promise.all([
    page.waitForResponse((item) => item.url().endsWith(`/exams/${examId}/publish`) && item.request().method() === "POST", {
      timeout: SOLVER_TIMEOUT_MS,
    }),
    dialog.getByRole("button", { name: /^Công bố \d+ đường lựa chọn$/ }).click(),
  ]);
  expect(published.status(), await published.text()).toBe(200);
  return (await published.json()) as { version: number; publishedPathIds: string[] };
}
