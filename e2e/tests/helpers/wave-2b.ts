import { createHash, createHmac } from "node:crypto";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect, type APIResponse, type Locator, type Page } from "@playwright/test";
import { ADMIN_URL, type AdminApi, RUN_ID } from "./editor-qa";

/**
 * Dùng chung cho các kịch bản E2E của nghiệm thu đợt 2B (`wave-2b-*.spec.ts`). Các kịch bản đi qua giao diện: bước 1 tạo
 * bài, bước 2 mở và lưu, "Tiếp tục" kiểm tra, tab Đề xem trước sinh thử (đổi mã, đổi nhánh ở đó), trang Phiên bản công bố.
 * API chỉ đọc kết quả để khẳng định (bài, cấu trúc) và dùng cho việc không thuộc phần được thử: đăng nhập, công bố bị từ
 * chối, điều chế dữ liệu nguồn (tệp đính kèm của câu trong ngân hàng, bản xóa/khôi phục trên kho lưu trữ).
 * Mọi giá trị nghiệp vụ (số câu, dải câu, điểm, mã kiểm tra) do từng spec khẳng định.
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
  scope?: string;
  partCode?: string | null;
  timing?: string;
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
  /** Điều kiện audio của mục (H38): `REQUIRED` chỉ lấy câu có audio, `FORBIDDEN` chỉ câu không audio. */
  audioRequirement?: "REQUIRED" | "FORBIDDEN" | "ANY" | null;
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
  /** Phân đoạn hiển thị (H40, V-SAT Tiếng Anh FULL: Phần I-IV); bài theo phần và bài ôn không có. */
  displaySegments?: Array<{ code: string; name?: string; fromPosition?: number; toPosition?: number; commandCount?: number; maxPoints?: number }> | null;
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
  /** Mã câu gây lỗi (`ATTACHMENT_MISSING` liệt kê câu thiếu tệp). */
  questionCodes?: string[];
  /** Tên phần của lỗi cấp phần (`SCALE_INCLUDES_EXCLUDED_PART`: phần đã lược). */
  partName?: string | null;
  /** Điểm của phần đã lược, máy chủ tính sẵn (`SCALE_INCLUDES_EXCLUDED_PART`, BE-20). */
  excludedPoints?: number | null;
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

export interface PreviewAttachmentLite {
  id: string;
  name?: string;
  mime?: string;
}

export interface PreviewQuestionLite {
  code?: string;
  number?: number;
  points?: number;
  /** Tệp đính kèm của câu đơn (câu con của bộ luôn rỗng). */
  attachments?: PreviewAttachmentLite[];
}

export interface PreviewItemLite {
  kind: "QUESTION" | "SET";
  question?: PreviewQuestionLite;
  children?: PreviewQuestionLite[];
  /** Định danh và mã của bộ (chỉ mục `SET`). */
  setId?: string;
  code?: string;
  /** Tệp đính kèm của câu gốc bộ (mục `QUESTION` luôn rỗng). */
  attachments?: PreviewAttachmentLite[];
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

/** Phân đoạn hiển thị của phần (H40, V-SAT Tiếng Anh: Phần I–IV). */
export interface PreviewSegmentLite {
  code: string;
  name?: string;
  fromPosition?: number;
  toPosition?: number;
  commandCount?: number;
  maxPoints?: number;
}

export interface PreviewPartLite {
  partId: string;
  name?: string;
  firstNumber?: number;
  lastNumber?: number;
  points?: number;
  maxPoints?: number | null;
  blocks?: PreviewBlockLite[];
  displaySegments?: PreviewSegmentLite[];
}

/** Một dòng đối chiếu chỉ tiêu của đề thử (criterion/scope, bắt buộc so với thực sinh). */
export interface PreviewComplianceLite {
  criterion?: string;
  scope?: string;
  required?: number | null;
  actual?: number | null;
  enforcement?: string;
  basis?: string;
}

export interface PreviewResultLite {
  status: string;
  reason?: string;
  issues?: CheckIssue[];
  seed?: number;
  previewId?: string | null;
  path?: { pathId?: string; choices?: CheckChoice[] };
  compliance?: PreviewComplianceLite[];
  paper: {
    totalQuestions: number;
    totalPoints: number;
    totalMinutes?: number;
    sharedDuration?: boolean;
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

/** Một dòng của tổng hợp cấu trúc (`GET /exams/{id}/structure/summary`): phần, môn hoặc nhóm. */
export interface SummaryRowLite {
  partId?: string | null;
  blockId?: string | null;
  name?: string;
  target?: number | null;
  allocated?: number | null;
  commandCount?: number | null;
  maxPoints?: number | null;
  children?: SummaryRowLite[] | null;
}

export async function getStructureSummary(admin: AdminApi, id: string): Promise<{ parts: SummaryRowLite[] }> {
  return body<{ parts: SummaryRowLite[] }>(await admin.api.get(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure/summary`), 200);
}

/** Các thẻ phần của nhóm "Phần thi" ở bước 1 (bài theo phần): tên của từng thẻ. */
export async function partCardNames(page: Page): Promise<string[]> {
  const cards = page.getByRole("group", { name: /^Phần thi/ }).getByRole("radio");
  await expect(cards.first(), "nhóm Phần thi chưa hiện thẻ nào").toBeVisible();
  return cards.evaluateAll((elements) =>
    elements.map((element) => {
      const input = element as HTMLInputElement;
      return (input.getAttribute("aria-label") ?? input.labels?.[0]?.textContent ?? "").replace(/\s+/g, " ").trim();
    }),
  );
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

export type UiExamKind = "BCA" | "HSA" | "HSCA" | "QDA" | "SP2E" | "SPT" | "THPTQG" | "VACT" | "VSAT";

/** Chữ đặc trưng trong tên danh mục của kỳ thi: mục cũ ghi mã trong ngoặc ("…(QDA)"), H-SCA/V-SAT ghi trong tên gọi. */
const KIND_LABEL: Record<UiExamKind, RegExp> = {
  BCA: /\(BCA\)/,
  HSA: /\(HSA\)/,
  HSCA: /H-SCA/,
  QDA: /\(QDA\)/,
  SP2E: /\(SP2E\)/,
  SPT: /\(SPT\)/,
  THPTQG: /\(THPTQG\)/,
  VACT: /\(V-ACT\)/,
  VSAT: /V-SAT/,
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
  /** Phạm vi ngắn gọn cũ: "Cả môn" là `SUBJECT` với môn bắt buộc; "Theo phần" của kỳ thi mới đi qua `scopeLabel` + `part`. */
  scope: "FULL" | "SUBJECT";
  /** Nhãn thẻ phạm vi khi khác mặc định: "Cả môn", "Theo phần", "Nghe", "Đọc", "Nghe và Đọc". */
  scopeLabel?: string | RegExp;
  /** Mã môn (khóa của `SUBJECT_NAME`) khi phạm vi là Theo môn/Cả môn hoặc bài theo phần chọn môn trước. */
  subject?: string;
  /** Chọn môn trước phạm vi (H-SCA, V-SAT, THPTQG theo phần): bỏ trống để helper tự phát hiện ô Môn đã hiện. */
  subjectFirst?: boolean;
  /** Tên thẻ của phần trong nhóm "Phần thi" (bài theo phần; mặc định phần đầu tiên đã chọn sẵn). */
  part?: string | RegExp;
  /** Chọn thẻ "Tự thiết lập bài ôn" (chỉ phạm vi theo môn). */
  review?: boolean;
  code: string;
  name: string;
  /** Câu báo phải hiện ở bước 1 sau khi chọn xong kỳ thi, phạm vi, môn (ví dụ "chỉ lưu nháp"). */
  notice?: string;
  /** Số nhập vào ô của bảng tổng quan (bài ôn không có số mặc định). Bỏ trống: giữ số của mẫu. */
  overview?: { questionCount?: number; durationMinutes?: number; maxScore?: number };
}

/** Ô chọn môn của bước 1 (hiện khi phạm vi theo môn/cả môn hoặc theo phần chọn môn trước). */
export function subjectCombobox(page: Page): Locator {
  return page.getByRole("combobox", { name: /^Môn/ });
}

async function pickSubject(page: Page, subject: string): Promise<void> {
  await subjectCombobox(page).click();
  await page.getByRole("option", { name: SUBJECT_NAME[subject]!, exact: true }).click();
}

async function fillOverviewCell(page: Page, name: RegExp, value: number | undefined): Promise<void> {
  if (value === undefined) return;
  const cell = page.getByRole("textbox", { name }).first();
  await expect(cell, `bảng tổng quan không có ô ${String(name)}`).toBeVisible();
  await cell.fill(String(value));
}

/**
 * Điền bước 1 đến hết bảng tổng quan nhưng chưa bấm "Lưu và tiếp tục": kỳ thi, môn (khi hiện trước), phạm vi, phần,
 * cách dựng bài và các ô tổng quan. Dùng khi kịch bản cần khẳng định giá trị nạp sẵn trước khi gửi.
 */
export async function fillExamStep1InUi(page: Page, spec: UiExamSpec): Promise<void> {
  await page.goto(`${ADMIN_URL}/exams/new`);
  await expect(page.getByRole("form", { name: "Tạo bài thi" })).toBeVisible();
  await page.getByRole("combobox", { name: /^Kỳ thi/ }).click();
  await page.getByRole("option", { name: KIND_LABEL[spec.kind] }).click();

  const subject = spec.subject;
  const pickSubjectNow = async () => {
    if (subject && (await subjectCombobox(page).count()) > 0) {
      await pickSubject(page, subject);
      return true;
    }
    return false;
  };

  // Kỳ thi chọn môn trước (H-SCA, V-SAT, THPTQG theo phần) thì ô Môn hiện ngay; các kỳ thi khác ô Môn theo sau phạm vi.
  const subjectPicked = spec.subjectFirst === false ? false : await pickSubjectNow();
  // Kỳ thi chỉ có một phạm vi thì không có nhóm radio (chỉ một dòng chữ); môn chưa chọn thì nhóm hiện sau khi chọn.
  await expect(
    page.getByRole("group", { name: /^Phạm vi/ }).or(page.getByText("Kỳ thi này chỉ có phạm vi này")),
    "bước chọn phạm vi chưa hiện",
  ).toBeVisible();
  const scopeName =
    spec.scopeLabel ?? (spec.scope === "FULL" ? /^Toàn bài/ : spec.subject ? /^Cả môn|^Theo môn/ : /^Theo môn/);
  const scopeRadio = page.getByRole("radio", { name: scopeName });
  if ((await scopeRadio.count()) > 0) await scopeRadio.check();
  if (!subjectPicked) await pickSubjectNow();
  if (spec.part) await page.getByRole("radio", { name: spec.part }).check();
  if (spec.review) await page.getByRole("radio", { name: /^Tự thiết lập bài ôn/ }).check();
  if (spec.notice) await expect(page.getByText(spec.notice)).toBeVisible();

  const countCell = page.getByRole("textbox", { name: /^Số câu của/ }).first();
  await expect(countCell).toBeVisible();
  if (!spec.review) await expect(countCell).not.toHaveValue("");
  await fillOverviewCell(page, /^Số câu của/, spec.overview?.questionCount);
  await fillOverviewCell(page, /^Thời lượng (của|chung)/, spec.overview?.durationMinutes);
  await fillOverviewCell(page, /^Điểm tối đa$/, spec.overview?.maxScore);
}

/**
 * Nhập mã và tên rồi bấm "Lưu và tiếp tục"; trả mã bài (id) đọc từ địa chỉ của bước 2. Form có ô sai thì báo ngay tên
 * ô sai thay vì chờ hết hạn của cả kịch bản.
 */
export async function submitExamStep1InUi(page: Page, spec: UiExamSpec): Promise<string> {
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

/** Bước 1 trọn vẹn: điền lựa chọn và tổng quan rồi "Lưu và tiếp tục"; trả mã bài (id) của bước 2. */
export async function createExamInUi(page: Page, spec: UiExamSpec): Promise<string> {
  await fillExamStep1InUi(page, spec);
  return submitExamStep1InUi(page, spec);
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
 *
 * `expectedPaths` (tên ô chọn của từng đường, ví dụ `/^Q[DĐ]01 ·/`): trước khi bấm, hộp thoại phải liệt kê đúng ngần ấy
 * đường, đều đã chọn, kèm dòng "Sẽ công bố N / N đường lựa chọn" và nút "Công bố N đường lựa chọn". Các khẳng định này
 * dùng thời hạn expect (không phải SOLVER_TIMEOUT_MS) nên hộp thoại 0 đường hỏng ngay thay vì chờ phản hồi công bố.
 */
export async function publishInUi(
  page: Page,
  examId: string,
  expectedPaths?: RegExp[],
): Promise<{ version: number; publishedPathIds: string[] }> {
  await openEditorTab(page, "Phiên bản và công bố", /\/exams\/[^/]+\/versions/);
  await page.getByRole("button", { name: "Công bố", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  if (expectedPaths) {
    const count = expectedPaths.length;
    await expect(dialog.getByRole("status")).toHaveText(`Sẽ công bố ${count} / ${count} đường lựa chọn`);
    await expect(dialog.getByRole("checkbox")).toHaveCount(count);
    for (const name of expectedPaths) await expect(dialog.getByRole("checkbox", { name })).toBeChecked();
    await expect(dialog.getByRole("button", { name: `Công bố ${count} đường lựa chọn`, exact: true })).toBeVisible();
  }
  const [published] = await Promise.all([
    page.waitForResponse((item) => item.url().endsWith(`/exams/${examId}/publish`) && item.request().method() === "POST", {
      timeout: SOLVER_TIMEOUT_MS,
    }),
    dialog.getByRole("button", { name: /^Công bố \d+ đường lựa chọn$/ }).click(),
  ]);
  expect(published.status(), await published.text()).toBe(200);
  return (await published.json()) as { version: number; publishedPathIds: string[] };
}

/* ---------- Vùng sửa nhóm của bước 2 ---------- */

/** Mở vùng sửa của nhóm `blockId` (đường dẫn `?block=` là cách màn mở lại đúng mục) và đợi ô "Tên nhóm" nạp tên. */
export async function openBlockInUi(page: Page, examId: string, blockId: string, name?: string): Promise<void> {
  await page.goto(`${ADMIN_URL}/exams/${examId}/structure?block=${encodeURIComponent(blockId)}`);
  const nameField = page.getByRole("textbox", { name: /^Tên nhóm/ });
  await expect(nameField).toBeVisible();
  if (name !== undefined) await expect(nameField).toHaveValue(name);
}

/** Ô số cần lấy của nhóm đang mở: chỉ đọc khi mẫu khóa dòng (`fixedBlockCounts`, H42) hoặc nhập theo tỷ lệ. */
export function blockCountCell(page: Page): Locator {
  return page.getByRole("textbox", { name: /^Số câu cần lấy/ });
}

/** Công tắc "Tạm bỏ qua" của nhóm Tự luận đang mở (công tắc bật = nhóm không vào đề). */
export function writingOmittedSwitch(page: Page): Locator {
  return page.getByRole("switch", { name: "Tạm bỏ qua" });
}

/* ---------- Ngân hàng câu hỏi: đính kèm tệp (điều chế dữ liệu nguồn) ---------- */

export interface QuestionSummaryLite {
  id: string;
  code: string;
  type?: string;
  status?: string;
  subjectCode?: string | null;
  gradeCode?: string | null;
  difficultyCode?: string | null;
  taskTypeCode?: string | null;
  competencyCode?: string | null;
  points?: number;
  knowledge?: unknown[];
  tagCodes?: string[];
  version?: number;
  revision?: number;
  sourceCode?: string | null;
}

interface QuestionDetailLite {
  summary: QuestionSummaryLite;
  content?: string | null;
  solution?: string | null;
  commonMistakes?: string[];
  attachmentIds?: string[];
  body?: unknown;
}

/** Danh sách câu gốc khớp bộ lọc (phân trang đủ lớn cho toàn bộ kết quả). */
export async function listQuestions(admin: AdminApi, query: { subjectCode: string; type?: string; keyword?: string }): Promise<QuestionSummaryLite[]> {
  const params = new URLSearchParams({ subjectCode: query.subjectCode, size: "200" });
  if (query.type) params.set("type", query.type);
  if (query.keyword) params.set("keyword", query.keyword);
  const response = await admin.api.get(`${ADMIN_URL}/api/v1/admin/questions?${params}`, { headers: admin.headers });
  const page = await body<{ items: QuestionSummaryLite[] }>(response, 200);
  return page.items.filter((item) => !item.code.includes("/"));
}

/** Tải một tệp lên kho tài nguyên câu hỏi; trả id tài nguyên để đặt vào `attachmentIds` của câu. */
export async function uploadQuestionAsset(admin: AdminApi, name: string, mime: string, bytes: Buffer): Promise<string> {
  const response = await admin.api.post(`${ADMIN_URL}/api/v1/admin/question-assets?kind=ATTACHMENT`, {
    headers: admin.headers,
    multipart: { file: { name, mimeType: mime, buffer: bytes } },
  });
  const uploaded = await body<{ id: string }>(response, 201);
  return uploaded.id;
}

/**
 * Gắn tệp vào câu đang Sẵn sàng: PUT tạo phiên bản mới kèm `attachmentIds` (giữ nguyên mọi trường của bản đang phục vụ)
 * rồi `ready` để bản mới phục vụ bộ giải. Trả số phiên bản mới.
 */
export async function attachAssetToQuestion(admin: AdminApi, questionId: string, assetId: string): Promise<void> {
  const detail = await body<QuestionDetailLite>(await admin.api.get(`${ADMIN_URL}/api/v1/admin/questions/${questionId}`, { headers: admin.headers }), 200);
  const summary = detail.summary;
  const updated = await admin.api.put(`${ADMIN_URL}/api/v1/admin/questions/${questionId}`, {
    headers: admin.headers,
    data: {
      expectedRevision: summary.revision,
      type: summary.type,
      subjectCode: summary.subjectCode,
      gradeCode: summary.gradeCode,
      difficultyCode: summary.difficultyCode,
      taskTypeCode: summary.taskTypeCode,
      competencyCode: summary.competencyCode,
      points: summary.points,
      knowledge: summary.knowledge ?? [],
      tagCodes: summary.tagCodes ?? [],
      content: detail.content,
      solution: detail.solution,
      commonMistakes: detail.commonMistakes ?? [],
      attachmentIds: [...(detail.attachmentIds ?? []), assetId],
      body: detail.body,
      sourceCode: summary.sourceCode,
    },
  });
  const saved = await body<{ question: QuestionDetailLite; versionCreated: boolean }>(updated, 200);
  expect(saved.versionCreated, "PUT câu Sẵn sàng phải tạo phiên bản mới").toBe(true);
  const ready = await admin.api.post(`${ADMIN_URL}/api/v1/admin/questions/${questionId}/ready`, {
    headers: admin.headers,
    data: { expectedRevision: saved.question.summary.revision },
  });
  expect(ready.status(), await ready.text()).toBe(200);
}

/* ---------- Kho lưu trữ S3/MinIO: xóa và khôi phục tệp nguồn (kịch bản ATTACHMENT_MISSING) ---------- */

const HERE = dirname(fileURLToPath(import.meta.url));
const ASSET_DIR = resolve(HERE, "../../../backend/src/main/resources/db/devdata/assets");

/** `object_key` của tệp nghe H-SCA trong kho dev (R__seed_questionbank_dev_data.sql: `questions/dev-seed/<file>`). */
export function hscaAudioKey(fileName: string): string {
  return `questions/dev-seed/${fileName}`;
}

interface S3Config {
  endpoint: string;
  bucket: string;
  accessKey: string;
  secretKey: string;
}

function s3Config(): S3Config {
  return {
    endpoint: (process.env.THI360_S3_ENDPOINT ?? "http://localhost:9360").replace(/\/+$/, ""),
    bucket: process.env.THI360_S3_BUCKET ?? "thi360-private",
    accessKey: process.env.THI360_S3_ACCESS_KEY ?? "thi360",
    secretKey: process.env.THI360_S3_SECRET_KEY ?? "thi360-dev-only",
  };
}

function s3Sign(config: S3Config, method: string, key: string, payloadHash: string): Record<string, string> {
  const { protocol, hostname, port } = new URL(config.endpoint);
  const host = port ? `${hostname}:${port}` : hostname;
  const now = new Date();
  const amzDate = now.toISOString().replace(/[-:]/g, "").replace(/\.\d+Z$/, "Z");
  const dateStamp = amzDate.slice(0, 8);
  const scope = `${dateStamp}/us-east-1/s3/aws4_request`;
  const encodedKey = key.split("/").map(encodeURIComponent).join("/");
  const canonicalRequest = [
    method,
    `/${config.bucket}/${encodedKey}`,
    "",
    `host:${host}`,
    `x-amz-content-sha256:${payloadHash}`,
    `x-amz-date:${amzDate}`,
    "",
    "host;x-amz-content-sha256;x-amz-date",
    payloadHash,
  ].join("\n");
  const stringToSign = `AWS4-HMAC-SHA256\n${amzDate}\n${scope}\n${createHash("sha256").update(canonicalRequest).digest("hex")}`;
  const kDate = createHmac("sha256", `AWS4${config.secretKey}`).update(dateStamp).digest();
  const kRegion = createHmac("sha256", kDate).update("us-east-1").digest();
  const kService = createHmac("sha256", kRegion).update("s3").digest();
  const kSigning = createHmac("sha256", kService).update("aws4_request").digest();
  const signature = createHmac("sha256", kSigning).update(stringToSign).digest("hex");
  void protocol;
  return {
    "x-amz-date": amzDate,
    "x-amz-content-sha256": payloadHash,
    Authorization: `AWS4-HMAC-SHA256 Credential=${config.accessKey}/${scope}, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=${signature}`,
  };
}

/** Xóa một object khỏi kho lưu trữ (máy chủ chỉ đọc kho qua `question_asset.object_key`, nên xóa ở đây là đủ). */
export async function s3DeleteObject(key: string): Promise<void> {
  const config = s3Config();
  const url = `${config.endpoint}/${config.bucket}/${key.split("/").map(encodeURIComponent).join("/")}`;
  const hash = createHash("sha256").update("").digest("hex");
  const response = await fetch(url, { method: "DELETE", headers: s3Sign(config, "DELETE", key, hash) });
  expect(response.status, await response.text()).toBe(204);
}

/** Ghi lại object: khôi phục đúng byte của tệp nguồn trong `db/devdata/assets` sau khi xóa. */
export async function s3RestoreSeedAsset(fileName: string): Promise<void> {
  const config = s3Config();
  const key = hscaAudioKey(fileName);
  const bytes = readFileSync(resolve(ASSET_DIR, fileName));
  const url = `${config.endpoint}/${config.bucket}/${key.split("/").map(encodeURIComponent).join("/")}`;
  const hash = createHash("sha256").update(bytes).digest("hex");
  const response = await fetch(url, {
    method: "PUT",
    headers: { ...s3Sign(config, "PUT", key, hash), "content-type": "audio/mpeg" },
    body: bytes,
  });
  expect(response.status, await response.text()).toBe(200);
}
