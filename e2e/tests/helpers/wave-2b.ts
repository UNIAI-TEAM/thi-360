import { createHash, createHmac } from "node:crypto";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { expect, type APIResponse, type Locator, type Page } from "@playwright/test";
import { ADMIN_URL, type AdminApi, RUN_ID, UNIT_LABEL } from "./editor-qa";

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
  kind?: string;
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

export interface KnowledgeScopeLite {
  nodeIds?: string[] | null;
  competencyCodes?: string[] | null;
  everyChildInScope?: boolean | null;
}

/** Khung chỉ tiêu một chiều (khối/độ khó/năng lực): giá trị COUNT hoặc PERCENT trên tổng đơn vị của mục. */
export interface MatrixDimensionRow {
  code: string;
  value?: number | null;
}

/** Ma trận cấp mục hoặc cấp khối như máy chủ trả (`byGrade`, `byDifficulty`, `byCompetency`, `cells` khi COMBINED). */
export interface BlockMatrixLite {
  mode?: "SEPARATE" | "COMBINED" | null;
  unit?: "COUNT" | "PERCENT" | null;
  basis?: string | null;
  gradeEnforcement?: string | null;
  difficultyEnforcement?: string | null;
  cells?: Array<{ gradeCode?: string; difficultyCode?: string; value?: number | null }> | null;
  byGrade?: MatrixDimensionRow[] | null;
  byDifficulty?: MatrixDimensionRow[] | null;
  byCompetency?: MatrixDimensionRow[] | null;
}

/** Chỉ tiêu chủ đề của mục/khối (`topicQuotas`): câu lấy từ tập `nodeIds`, cần `count` (hoặc `percent`) đơn vị. */
export interface TopicQuotaLite {
  id?: string;
  nodeIds?: string[] | null;
  count?: number | null;
  percent?: number | null;
  basis?: string | null;
  enforcement?: string | null;
}

/** Điều kiện câu con theo vị trí của khối chùm (`childConditions`): đoạn `[from, to]` cần MAPID/dạng/hình dạng. */
export interface ChildConditionLite {
  from?: number | null;
  to?: number | null;
  nodeIds?: string[] | null;
  answerType?: string | null;
  itemShape?: { statements?: number | null; leftItems?: number | null; rightItems?: number | null; options?: number | null } | null;
}

export interface StructureBlock {
  id?: string;
  name?: string;
  questionType?: string | null;
  questionCount?: number | null;
  setCount?: number | null;
  setSize?: number | null;
  knowledgeScope?: KnowledgeScopeLite | null;
  matrix?: BlockMatrixLite | null;
  taskTypeCode?: string | null;
  childAnswerType?: string | null;
  answerFormats?: string[] | null;
  itemShape?: ChildConditionLite["itemShape"];
  expectedScoring?: { trueFalseMethod?: string | null; trueFalseTable?: number[] | null; multipleChoiceRule?: string | null } | null;
  childConditions?: ChildConditionLite[] | null;
  slots?: Array<{ index?: number | null; knowledgeScope?: KnowledgeScopeLite | null; tagCodes?: string[] | null }> | null;
  tagCodes?: string[] | null;
  topicQuotas?: TopicQuotaLite[] | null;
  requiredPointsPerQuestion?: number | null;
  omitted?: boolean | null;
  [key: string]: unknown;
}

export interface StructureSection {
  /** Chỉ tiêu khối lớp và độ khó cấp môn (mẫu QDA_FULL khai cho Lý, Hóa, Sinh, Sử, Địa; Tiếng Anh không có). */
  matrix?: BlockMatrixLite | null;
  id: string;
  subjectCode: string;
  /** Điều kiện audio của mục (H38): `REQUIRED` chỉ lấy câu có audio, `FORBIDDEN` chỉ câu không audio. */
  audioRequirement?: "REQUIRED" | "FORBIDDEN" | "ANY" | null;
  knowledgeScope?: KnowledgeScopeLite | null;
  answerQuotas?: { unit?: string | null; items?: Array<{ answerType?: string | null; value?: number | null }> | null } | null;
  topicQuotas?: TopicQuotaLite[] | null;
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
  /** Câu điền chỗ trống (`body.type = CLOZE`) hiện một lần trên đề nhưng mỗi chỗ trống chiếm một số câu: `body.blanks`. */
  body?: { type?: string; blanks?: unknown[] } | null;
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

/**
 * Mọi câu của đề thử theo thứ tự (câu con của chùm được trải ra), kèm phần, nhóm và môn. Câu điền chỗ trống (CLOZE) hiện
 * một lần nhưng mỗi chỗ trống là một số câu: nó trải thành đúng số chỗ trống, chỗ đầu mang mã và điểm của câu, các chỗ sau
 * mang mã `<mã>#<thứ tự chỗ>` và điểm 0 (đề thử không trả điểm từng chỗ).
 */
export function paperQuestions(result: PreviewResultLite): PaperQuestion[] {
  const out: PaperQuestion[] = [];
  result.paper.parts.forEach((part, partIndex) => {
    for (const block of part.blocks ?? []) {
      for (const item of block.items ?? []) {
        const questions = item.kind === "SET" ? (item.children ?? []) : item.question ? [item.question] : [];
        for (const question of questions) {
          const blanks = question.body?.type === "CLOZE" ? (question.body.blanks?.length ?? 0) : 0;
          for (let blank = 0; blank < Math.max(blanks, 1); blank += 1) {
            out.push({
              partIndex,
              blockName: block.blockName ?? "",
              subjectCode: block.subjectCode ?? "",
              code: blank === 0 ? (question.code ?? "") : `${question.code ?? ""}#${blank + 1}`,
              number: (question.number ?? 0) + blank,
              points: blank === 0 ? (question.points ?? 0) : 0,
            });
          }
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
  await expect(page.getByRole("heading", { name: "Tạo bài thi", level: 1 })).toBeVisible();
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

/** Bước 2 vừa mở: dòng "Số câu" của khối Tổng hợp cấu trúc ở đầu trang khớp `questions`. */
export async function expectStructureQuestions(page: Page, questions: RegExp): Promise<void> {
  const summary = page.getByLabel("Tổng hợp cấu trúc");
  await expect(summary.locator("div").filter({ has: page.locator("dt", { hasText: /^Số câu$/ }) }).locator("dd")).toHaveText(questions);
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

const MATRIX_SUBJECTS_BY_KIND: Readonly<Record<string, ReadonlySet<string>>> = {
  HSA: new Set(["TOAN", "LY", "HOA", "SINH", "SU", "DIA"]),
  QDA: new Set(["TOAN", "LY", "HOA", "SINH", "SU", "DIA"]),
  BCA: new Set(["TOAN", "SU", "LY", "HOA", "SINH", "DIA"]),
  VACT: new Set(["TOAN", "VIETNAMESE"]),
};

function isEditableBlockSection(kindCode: string | undefined, section: StructureSection): boolean {
  if (kindCode && MATRIX_SUBJECTS_BY_KIND[kindCode]?.has(section.subjectCode)) return false;
  if (kindCode === "THPTQG" && section.subjectCode === "VAN") return false;
  return true;
}

/**
 * Bước 2: mở nhóm lấy câu đầu tiên (đường dẫn `?block=` là cách màn mở lại đúng mục), đổi tên nhóm rồi bấm Lưu. Nút Lưu
 * của bản mẫu nguyên vẹn tắt, nên đổi tên (hoặc một sửa đổi vô hại rồi khôi phục trên MT / đổi tên phần ở cặp chỉ đọc
 * `THPTQG:VAN`) là thao tác để lưu thật. Chỉ đọc cấu trúc ở API để chọn nhóm và xác nhận giá trị đã lưu.
 */
export async function renameFirstBlockAndSaveInUi(page: Page, admin: AdminApi, examId: string): Promise<{ id: string; name: string }> {
  const exam = await getExam(admin, examId);
  const sections = allSections(await getStructure(admin, examId));
  const editableBlocks = sections
    .filter((section) => isEditableBlockSection(exam.kind, section))
    .flatMap((section) => section.blocks ?? [])
    .filter((item) => item.id && item.name);
  const allBlocks = sections.flatMap((section) => section.blocks ?? []).filter((item) => item.id && item.name);
  const candidates = editableBlocks.length > 0 ? editableBlocks : allBlocks;
  // Ưu tiên nhóm đang tính điểm; bài chỉ còn nhóm tạm bỏ qua thì sửa nhóm đầu tiên.
  const block = candidates.find((item) => item.omitted !== true && (item.questionCount ?? 0) > 0) ?? candidates[0];
  expect(block, "bài không có nhóm lấy câu để sửa").toBeDefined();

  await page.goto(`${ADMIN_URL}/exams/${examId}/structure?block=${encodeURIComponent(block!.id!)}`);
  const nameField = page.getByRole("textbox", { name: /^Tên nhóm/ });
  const matrixForm = page.locator('[data-form-kind="MATRIX"]');
  const readOnlyNotice = page.locator("[data-read-only-notice]");
  await expect(nameField.or(matrixForm).or(readOnlyNotice).first()).toBeVisible();

  if ((await nameField.count()) > 0) {
    const renamed = `${block!.name} 2B`;
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

  if ((await matrixForm.count()) > 0) {
    const structureBlock = matrixForm.locator('[data-form-field="STRUCTURE"]');
    await expect(structureBlock).toBeVisible();

    const sectionBefore = allSections(await getStructure(admin, examId)).find((s) =>
      (s.blocks ?? []).some((b) => b.id === block!.id),
    );
    const targetBlockBefore =
      (sectionBefore?.blocks ?? []).find((b) => b.questionType === "SHORT_ANSWER") ??
      (sectionBefore?.blocks ?? []).find((b) => b.questionType !== "QUESTION_SET") ??
      (sectionBefore?.blocks ?? []).find((b) => b.id === block!.id);
    expect(targetBlockBefore?.id, "không tìm thấy khối mục tiêu trong section MATRIX").toBeTruthy();

    const countInput = structureBlock.locator(`[data-form-field="COUNT:${targetBlockBefore!.id}"] input`);
    await expect(countInput).toBeVisible();

    const origValue = await countInput.inputValue();
    const origNum = Number(origValue);
    const isFill = targetBlockBefore?.questionType === "SHORT_ANSWER";
    const tempValue = isFill ? String(origNum > 1 ? origNum - 1 : origNum + 1) : String(origNum + 1);
    const expectedTempCount = Number(tempValue);

    await countInput.fill(tempValue);
    await expect(page.getByText("Có thay đổi chưa lưu")).toBeVisible();
    await saveStructureInUi(page, examId);

    const savedBlock = allSections(await getStructure(admin, examId))
      .flatMap((section) => section.blocks ?? [])
      .find((item) => item.id === targetBlockBefore!.id);
    expect(savedBlock?.questionCount, "số câu khối MATRIX sau khi lưu").toBe(expectedTempCount);

    await countInput.fill(origValue);
    await expect(page.getByText("Có thay đổi chưa lưu")).toBeVisible();
    await saveStructureInUi(page, examId);
    await expect(countInput).toHaveValue(origValue);

    const restoredBlock = allSections(await getStructure(admin, examId))
      .flatMap((section) => section.blocks ?? [])
      .find((item) => item.id === targetBlockBefore!.id);
    expect(restoredBlock?.questionCount, "số câu khối MATRIX sau khi khôi phục").toBe(targetBlockBefore?.questionCount);
    return { id: block!.id!, name: block!.name! };
  }

  await expect(readOnlyNotice).toHaveText("Tự luận – tạm bỏ qua. Phần này không có ô sửa.");
  await expect(page.getByRole("button", { name: /^Sửa dòng/ })).toHaveCount(0);
  await page.getByRole("navigation", { name: "Cấu trúc" }).locator(":scope > ol > li:not(:first-child) > button[data-list-item]").first().click();
  const partNameField = page.getByRole("textbox", { name: /^Tên phần/ });
  await expect(partNameField).toBeVisible();
  const partName = await partNameField.inputValue();
  const renamedPart = `${partName} 2B`;
  await partNameField.fill(renamedPart);
  await expect(page.getByText("Có thay đổi chưa lưu")).toBeVisible();
  await saveStructureInUi(page, examId);
  expect((await getStructure(admin, examId)).parts[0]?.name, "tên phần đã lưu").toBe(renamedPart);
  return { id: block!.id!, name: block!.name! };
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
  if (blockingCodes(check.groups ?? []).length === 0) await page.waitForURL(/\/exams\/[^/]+\/review/);
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
 * Điền tối thiểu thông tin bán (FB yêu cầu đủ giá, số lượt và mô tả trước khi công bố) nếu còn trống rồi bấm "Lưu"
 * (nút Lưu nằm ngoài `<form aria-label="Thông tin bán">` nên tìm ở cấp trang).
 */
export async function ensureListingFilledInUi(page: Page, examId: string): Promise<void> {
  await openEditorTab(page, "Thông tin bán", /\/exams\/[^/]+\/listing/);
  const listingForm = page.getByRole("form", { name: "Thông tin bán" });
  await expect(listingForm).toBeVisible();
  const priceInput = listingForm.getByRole("textbox", { name: "Giá bán", exact: true });
  const attemptsInput = listingForm.getByRole("spinbutton", { name: "Số lượt mỗi lần mua" });
  const descriptionInput = listingForm.getByRole("textbox", { name: "Mô tả", exact: true });
  let shouldSave = false;
  const price = (await priceInput.inputValue()).trim();
  if (!price || price === "0") {
    await priceInput.fill("100000");
    shouldSave = true;
  }
  const attempts = (await attemptsInput.inputValue()).trim();
  if (!attempts || Number(attempts) < 1) {
    await attemptsInput.fill("3");
    shouldSave = true;
  }
  if ((await descriptionInput.inputValue()).trim() === "") {
    await descriptionInput.fill("Đề thi luyện tập chuẩn bị kỳ thi.");
    shouldSave = true;
  }
  const saveListing = page.getByRole("button", { name: "Lưu", exact: true });
  if (shouldSave || (await saveListing.isEnabled())) {
    await expect(saveListing).toBeEnabled();
    const [savedListing, refreshedExam] = await Promise.all([
      page.waitForResponse(
        (item) =>
          new URL(item.url()).pathname === `/api/v1/admin/exams/${examId}/listing` &&
          item.request().method() === "PUT",
      ),
      page.waitForResponse(
        (item) =>
          new URL(item.url()).pathname === `/api/v1/admin/exams/${examId}` && item.request().method() === "GET",
      ),
      saveListing.click(),
    ]);
    expect(savedListing.status(), await savedListing.text()).toBe(200);
    expect(refreshedExam.status(), await refreshedExam.text()).toBe(200);
    await expect(saveListing).toBeDisabled();
  }
}

/**
 * Điền tối thiểu thông tin bán (FB yêu cầu đủ giá, số lượt và mô tả trước khi công bố) nếu còn trống rồi công bố ở trang
 * Phiên bản và công bố: nút "Công bố", hộp thoại, "Công bố N đường lựa chọn". Trả phản hồi của máy chủ (đã khẳng
 * định 200).
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
  await ensureListingFilledInUi(page, examId);

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

/**
 * Nhóm Viết của mẫu `omittedLocked` (H43, H49, R-144): FTM `RowEditor` khóa và thay công tắc bằng trạng thái chỉ đọc
 * `[data-lock-reason="omitted"]` + `[data-omitted-locked]` để tránh bật lại nhóm đã lược; `BlockEditor` cũ giữ công tắc
 * tắt kèm lý do qua `aria-describedby` (R3-09).
 */
export async function expectWritingSwitchLocked(page: Page): Promise<void> {
  const rowEditor = page.locator("[data-form-row-editor]");
  if ((await rowEditor.count()) > 0) {
    await expect(rowEditor.locator('[data-lock-reason="omitted"]')).toContainText("Nhóm đã lược theo mẫu, không bật lại");
    const lockedOmitted = rowEditor.locator("[data-omitted-locked]");
    await expect(lockedOmitted).toContainText("Tạm bỏ qua");
    await expect(lockedOmitted).toContainText(/không bật lại được|chưa có trong V1/);
    await expect(writingOmittedSwitch(page)).toHaveCount(0);
    return;
  }
  const omit = writingOmittedSwitch(page);
  await expect(omit).toBeVisible();
  await expect(omit).toBeChecked();
  await expect(omit).toBeDisabled();
  await expect(omit).toHaveAttribute("aria-describedby", /\S/);
  await expect(omit).toHaveAccessibleDescription(/không bật lại được|chưa có trong V1/);
}

/**
 * Ép `omitted` của một nhóm bằng PUT thẳng `/structure` (giao diện không cho bật lại nhóm Viết): máy chủ nhận cấu trúc,
 * lỗi chỉ lộ ở kiểm tra (`OMITTED_BLOCK_LOCKED`) và công bố. Dùng để chứng minh lưới an toàn của máy chủ và để trả nhóm về
 * trạng thái tham chiếu.
 */
export async function forceBlockOmitted(admin: AdminApi, examId: string, blockId: string, omitted: boolean): Promise<void> {
  const structure = await getStructure(admin, examId);
  const block = allSections(structure)
    .flatMap((section) => section.blocks ?? [])
    .find((item) => item.id === blockId);
  expect(block, `không thấy nhóm ${blockId}`).toBeDefined();
  block!.omitted = omitted;
  const response = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${examId}/structure`, {
    headers: admin.headers,
    data: { parts: structure.parts, examQuotas: structure.examQuotas ?? [], expectedRevision: structure.revision },
  });
  expect(response.status(), await response.text()).toBe(200);
}

/**
 * Đặt thang điểm của bài qua tab "Thông tin chung": ô "Điểm tối đa" rồi "Lưu thông tin", đợi máy chủ nhận (200) và đọc lại
 * `maxScore` ở API để xác nhận giá trị đã lưu.
 */
export async function setMaxScoreInGeneralTab(page: Page, admin: AdminApi, examId: string, maxScore: number): Promise<void> {
  await openEditorTab(page, "Thông tin chung", /\/exams\/[^/]+$/);
  const field = page.getByRole("textbox", { name: /^Điểm tối đa$/ });
  await expect(field).toBeVisible();
  await field.fill(String(maxScore));
  const [response] = await Promise.all([
    page.waitForResponse((item) => item.url().endsWith(`/exams/${examId}`) && item.request().method() !== "GET", {
      timeout: SOLVER_TIMEOUT_MS,
    }),
    page.getByRole("button", { name: "Lưu thông tin", exact: true }).click(),
  ]);
  expect(response.status(), await response.text()).toBe(200);
  expect((await getExam(admin, examId)).maxScore).toBe(maxScore);
}

/**
 * Trả nhóm Viết bị ép bật lại (`forceBlockOmitted`) về trạng thái tham chiếu bằng giao diện: mở nhóm (công tắc "Tạm bỏ qua"
 * tắt và bấm được, vì nhóm đã bật từ bản nháp cũ vẫn sửa được), bật công tắc, thấy nó khóa kèm lý do, rồi Lưu. Chứng minh
 * đường sửa lại của ADV:QA:11 từ đầu đến cuối.
 */
export async function restoreOmittedBlockInUi(page: Page, admin: AdminApi, examId: string, blockId: string, name?: string): Promise<void> {
  await openBlockInUi(page, examId, blockId, name);
  const omit = writingOmittedSwitch(page);
  await expect(omit).toBeEnabled();
  await expect(omit).not.toBeChecked();
  await omit.click();
  await expectWritingSwitchLocked(page);
  await saveStructureInUi(page, examId);
  const saved = allSections(await getStructure(admin, examId))
    .flatMap((section) => section.blocks ?? [])
    .find((item) => item.id === blockId);
  expect(saved?.omitted, "nhóm Viết đã trả về tạm bỏ qua").toBe(true);
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

/** Tải một tệp lên kho tài nguyên câu hỏi; trả id tài nguyên để đặt vào `attachmentIds` của câu. */
export async function uploadQuestionAsset(admin: AdminApi, name: string, mime: string, bytes: Buffer): Promise<string> {
  const response = await admin.api.post(`${ADMIN_URL}/api/v1/admin/question-assets`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    multipart: { kind: "ATTACHMENT", file: { name, mimeType: mime, buffer: bytes } },
  });
  const uploaded = await body<{ id: string }>(response, 201);
  return uploaded.id;
}

/**
 * Đổi danh sách tệp đính kèm của câu đang Sẵn sàng: PUT tạo phiên bản mới kèm `attachmentIds` (giữ nguyên mọi trường của
 * bản đang phục vụ) rồi `ready` để bản mới phục vụ bộ giải. `change` trả danh sách mới, hoặc `null` khi không cần đổi
 * (không tạo phiên bản).
 */
async function changeQuestionAttachments(
  admin: AdminApi,
  questionId: string,
  change: (current: string[]) => string[] | null,
): Promise<boolean> {
  const detail = await body<QuestionDetailLite>(await admin.api.get(`${ADMIN_URL}/api/v1/admin/questions/${questionId}`, { headers: admin.headers }), 200);
  const summary = detail.summary;
  const attachmentIds = change(detail.attachmentIds ?? []);
  if (attachmentIds === null) return true;
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
      attachmentIds,
      body: detail.body,
      sourceCode: summary.sourceCode,
    },
  });
  if (updated.status() !== 200) return false;
  const saved = (await updated.json()) as { question?: QuestionDetailLite };
  const ready = await admin.api.post(`${ADMIN_URL}/api/v1/admin/questions/${questionId}/ready`, {
    headers: admin.headers,
    data: { expectedRevision: saved.question?.summary.revision ?? (summary.revision ?? 0) + 1 },
  });
  return ready.status() === 200;
}

/** Chạy `task` cho mọi câu của danh sách (tám luồng song song); trả số câu thành công và số câu lỗi. */
async function forEachQuestion(
  questions: QuestionSummaryLite[],
  task: (question: QuestionSummaryLite) => Promise<boolean>,
): Promise<{ ok: number; failed: number }> {
  let next = 0;
  let ok = 0;
  let failed = 0;
  const worker = async () => {
    while (next < questions.length) {
      const question = questions[next++]!;
      if (await task(question)) ok += 1;
      else failed += 1;
    }
  };
  await Promise.all(Array.from({ length: 8 }, worker));
  return { ok, failed };
}

/** Gắn cùng một tệp vào mọi câu của danh sách; trả số câu gắn được và số câu lỗi. */
export async function attachAssetToQuestions(admin: AdminApi, questions: QuestionSummaryLite[], assetId: string): Promise<{ ok: number; failed: number }> {
  return forEachQuestion(questions, (question) =>
    changeQuestionAttachments(admin, question.id, (current) => [...current, assetId]),
  );
}

/**
 * Gỡ tệp đã gắn bằng `attachAssetToQuestions`, trả mỗi câu về danh sách đính kèm ban đầu (câu không mang tệp thì bỏ qua,
 * nên gọi được cả khi lần gắn dở chừng). Mỗi câu có thêm phiên bản mới nhưng đính kèm và nội dung không đổi.
 */
export async function detachAssetFromQuestions(admin: AdminApi, questions: QuestionSummaryLite[], assetId: string): Promise<{ ok: number; failed: number }> {
  return forEachQuestion(questions, (question) =>
    changeQuestionAttachments(admin, question.id, (current) => (current.includes(assetId) ? current.filter((id) => id !== assetId) : null)),
  );
}

/* ---------- Ngân hàng câu hỏi: tạo câu riêng của luồng (bài thu hẹp theo MAPID riêng) ---------- */

/** Bản thân câu hỏi như `POST /api/v1/admin/questions` nhận (spec 9.2); `knowledge` là `nodeId` + `primary`. */
export interface QuestionDraft {
  type: string;
  subjectCode: string;
  gradeCode: string;
  difficultyCode: string;
  taskTypeCode?: string | null;
  competencyCode?: string | null;
  points: number;
  knowledge: Array<{ nodeId: string; primary: boolean }>;
  tagCodes?: string[];
  content: string;
  body: Record<string, unknown>;
  sourceCode: string;
}

/**
 * Thu hẹp mọi mục `subjectCode` của bài về đúng nút kiến thức `leafId` của luồng (PUT `/structure`): mục nhận
 * `knowledgeScope = {nodeIds: [leafId]}` giữ mã năng lực đang có; khối đã khai `nodeIds` riêng được thay bằng nút
 * của luồng (nút trong scope là điều kiện HOẶC nên giữ nút mẫu sẽ không thu hẹp). Trả cấu trúc đã lưu.
 */
export async function narrowSectionsToKnowledge(admin: AdminApi, examId: string, subjectCode: string, leafId: string): Promise<Structure> {
  const structure = await getStructure(admin, examId);
  const narrowed = allSections(structure).filter((section) => section.subjectCode === subjectCode);
  expect(narrowed.length, `${examId}: không có mục ${subjectCode} để thu hẹp`).toBeGreaterThan(0);
  for (const section of narrowed) {
    section.knowledgeScope = { nodeIds: [leafId], competencyCodes: section.knowledgeScope?.competencyCodes ?? [] };
    for (const block of section.blocks ?? []) {
      if ((block.knowledgeScope?.nodeIds?.length ?? 0) > 0) {
        block.knowledgeScope = {
          nodeIds: [leafId],
          competencyCodes: block.knowledgeScope?.competencyCodes ?? [],
        };
      }
    }
  }
  const response = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${examId}/structure`, {
    headers: admin.headers,
    data: { parts: structure.parts, examQuotas: structure.examQuotas ?? [], expectedRevision: structure.revision },
  });
  expect(response.status(), await response.text()).toBe(200);
  return structure;
}

/**
 * Giá trị `value` của chỉ tiêu `unit` PERCENT quy ra số đơn vị theo phần dư lớn nhất trên `total` (cùng quy tắc
 * `QuotaApportioner` của máy chủ); COUNT trả nguyên giá trị.
 */
function apportion(rows: Array<{ code: string; value: number }>, unit: string | null | undefined, total: number): Array<{ code: string; count: number }> {
  if (unit !== "PERCENT") return rows.map((row) => ({ code: row.code, count: Math.round(row.value) }));
  const sum = rows.reduce((acc, row) => acc + row.value, 0);
  const raw = rows.map((row) => ({ code: row.code, exact: sum === 0 ? 0 : (row.value * total) / sum }));
  const floored = raw.map((row) => ({ ...row, count: Math.floor(row.exact) }));
  let rest = total - floored.reduce((acc, row) => acc + row.count, 0);
  for (const row of [...floored].sort((a, b) => (b.exact - Math.floor(b.exact)) - (a.exact - Math.floor(a.exact)))) {
    if (rest <= 0) break;
    const target = floored.find((candidate) => candidate.code === row.code);
    if (target) { target.count += 1; rest -= 1; }
  }
  return floored.map(({ code, count }) => ({ code, count }));
}

/** Dãy mã dài `total` theo chỉ tiêu của chiều (ví dụ G10×3, G11×20, G12×27); thiếu tổng thì đệm `filler` của chính chiều đó. */
function expandCodes(rows: Array<{ code: string; count: number }>, total: number, filler: string | null): Array<string | null> {
  const result: Array<string | null> = [];
  for (const row of rows) for (let i = 0; i < row.count; i += 1) result.push(row.code);
  while (result.length < total) result.push(rows[0]?.code ?? filler);
  return result.slice(0, total);
}

/**
 * Đếm câu của một khối trong bản nháp: SINGLE = `questionCount` (null = phần còn lại của mục); QUESTION_SET/CLOZE
 * = `questionCount` đơn vị (chỗ trống/câu con) và `setCount` gốc — gốc linh hoạt (thiếu setCount/setSize) nhận 3
 * đơn vị mỗi gốc (đủ số câu con tối thiểu), dôi ra đơn vị thì gốc cuối nhận phần dư.
 */
function blockCounts(block: StructureBlock, remain: { units: number }): { roots: number; units: number; rootUnits: number[] } {
  const type = block.questionType;
  if (type === "QUESTION_SET" || type === "CLOZE") {
    const units = block.setCount == null && block.setSize == null && block.questionCount != null
      ? block.questionCount
      : (block.setCount ?? 0) * (block.setSize ?? 0) || Math.min(block.questionCount ?? 0, remain.units);
    const size = block.setSize ?? 3;
    const roots = block.setCount ?? Math.max(1, Math.ceil(units / size));
    const rootUnits: number[] = [];
    let left = units;
    for (let i = 0; i < roots; i += 1) {
      const take = i === roots - 1 ? left : Math.min(size, left);
      rootUnits.push(Math.max(take, 0));
      left -= take;
    }
    remain.units -= units;
    return { roots, units, rootUnits };
  }
  const units = block.questionCount ?? Math.max(0, remain.units);
  remain.units -= units;
  return { roots: units, units, rootUnits: Array.from({ length: units }, () => 1) };
}

/**
 * Dựng danh sách bản thân câu đủ lấp mọi mục `subjectCode` của cấu trúc (đã thu hẹp về `leafId`): phân phối
 * khối lớp/độ khó/năng lực theo ma trận, dạng trả lời theo `answerQuotas`, MAPID chủ đề theo `topicQuotas`,
 * điểm theo `requiredPointsPerQuestion`, hình dạng theo `itemShape`/`childConditions`, dạng con theo
 * `childAnswerType`/`slots`. Mọi câu và câu con gắn `leafId` để bộ lọc MAPID thu hẹp chỉ còn câu của luồng.
 */
export function planQuestionsForSections(structure: Structure, subjectCode: string, leafId: string, tag: string): QuestionDraft[] {
  const drafts: QuestionDraft[] = [];
  let seq = 0;
  const knowledge = (extra: Array<string | null | undefined>): QuestionDraft["knowledge"] => {
    const ids = [leafId, ...extra.filter((id): id is string => Boolean(id))];
    return [...new Set(ids)].map((nodeId, index) => ({ nodeId, primary: index === 0 }));
  };
  for (const section of allSections(structure).filter((item) => item.subjectCode === subjectCode)) {
    const total = section.questionCount ?? 0;
    if (total <= 0) continue;
    const matrix = section.matrix ?? null;
    const grades = expandCodes(
      matrix?.mode === "COMBINED" && matrix.cells?.length
        ? apportion(matrix.cells.map((cell) => ({ code: cell.gradeCode ?? "G12", value: cell.value ?? 0 })), matrix.unit, total)
        : apportion((matrix?.byGrade ?? []).filter((row) => row.value != null).map((row) => ({ code: row.code, value: row.value ?? 0 })), matrix?.unit, total),
      total,
      "G12",
    );
    const difficulties = expandCodes(
      matrix?.mode === "COMBINED" && matrix.cells?.length
        ? apportion(matrix.cells.map((cell) => ({ code: cell.difficultyCode ?? "TH", value: cell.value ?? 0 })), matrix.unit, total)
        : apportion((matrix?.byDifficulty ?? []).filter((row) => row.value != null).map((row) => ({ code: row.code, value: row.value ?? 0 })), matrix?.unit, total),
      total,
      "TH",
    );
    const competencyRows = (section.blocks ?? []).flatMap((block) =>
      (block.matrix?.byCompetency ?? []).filter((row) => row.value != null).map((row) => ({ code: row.code, value: row.value ?? 0 })));
    const scopeCodes = section.knowledgeScope?.competencyCodes ?? [];
    const competencyPlan = competencyRows.length
      ? apportion(competencyRows, (section.blocks ?? []).find((block) => block.matrix?.byCompetency?.length)?.matrix?.unit, total)
      : scopeCodes.map((code) => ({ code, count: total }));
    const competencyCodes = expandCodes(competencyPlan, total, null);
    const answers = expandCodes(
      apportion(
        (section.answerQuotas?.items ?? []).map((row) => ({ code: row.answerType ?? "SINGLE_CHOICE", value: row.value ?? 0 })),
        section.answerQuotas?.unit,
        total,
      ),
      total,
      "SINGLE_CHOICE",
    );
    const sectionTopicNodes = (section.topicQuotas ?? []).flatMap((quota) => quota.nodeIds ?? []);
    const remain = { units: total };
    let unitCursor = 0;
    for (const block of (section.blocks ?? []).filter((item) => !item.omitted)) {
      const { roots, rootUnits } = blockCounts(block, remain);
      if (roots <= 0) continue;
      const quotaNodes = [...sectionTopicNodes, ...(block.topicQuotas ?? []).flatMap((quota) => quota.nodeIds ?? [])];
      const slotTags = (block.slots ?? []).flatMap((slot) => slot.tagCodes ?? []);
      const type = block.questionType ?? answers[unitCursor] ?? "SINGLE_CHOICE";
      for (let rootIndex = 0; rootIndex < roots; rootIndex += 1) {
        const size = rootUnits[rootIndex] ?? 1;
        const grade = grades[Math.min(unitCursor, total - 1)] ?? "G12";
        const difficulty = difficulties[Math.min(unitCursor, total - 1)] ?? "TH";
        const competency = competencyCodes[Math.min(unitCursor, total - 1)] ?? null;
        const mapids = [...quotaNodes, ...((block.slots ?? []).find((slot) => slot.index === rootIndex + 1)?.knowledgeScope?.nodeIds ?? [])];
        seq += 1;
        const code = `W2BATT-${tag}-${seq}`.toUpperCase().replace(/[^A-Z0-9-]/g, "X").slice(0, 40);
        const points = block.requiredPointsPerQuestion ?? 1;
        const base = {
          subjectCode,
          gradeCode: grade,
          difficultyCode: difficulty,
          ...(block.taskTypeCode ? { taskTypeCode: block.taskTypeCode } : {}),
          ...(competency ? { competencyCode: competency } : {}),
          knowledge: knowledge(mapids),
          tagCodes: [...new Set([...(block.tagCodes ?? []), ...slotTags])],
          content: `<p>${UNIT_LABEL}QA điều chế ${RUN_ID}; câu ${code}.</p>`,
          sourceCode: `${code}-${RUN_ID}`.slice(0, 60),
        };
        if (type === "QUESTION_SET" || type === "CLOZE") {
          const conditions = block.childConditions ?? [];
          const children = Array.from({ length: size }, (_, childIndex) => {
            const position = childIndex + 1;
            const condition = conditions.find((item) => (item.from ?? 1) <= position && position <= (item.to ?? size));
            const childType = condition?.answerType ?? block.childAnswerType ?? "SINGLE_CHOICE";
            const childMapids = [...mapids, ...(condition?.nodeIds ?? [])];
            return {
              type: childType,
              content: `<p>${UNIT_LABEL}câu con ${childIndex + 1} của ${code}.</p>`,
              body: bodyOf(childType, condition?.itemShape ?? null, points, null),
              points,
              knowledgeItemIds: [...new Set([leafId, ...childMapids])],
            };
          });
          drafts.push({
            ...base,
            type,
            points: points * size,
            body: type === "CLOZE"
              ? { type: "CLOZE", passage: `<p>${UNIT_LABEL}đoạn có ${size} chỗ trống của ${code}: ${children.map((_, index) => `[[b${index + 1}]]`).join(" ")}.</p>`, blanks: children.map((_, index) => ({ id: `b${index + 1}`, answers: ["1"], points })) }
              : { type: "QUESTION_SET", passage: `<p>${UNIT_LABEL}ngữ liệu chung của ${code}.</p>`, blankNumberStart: 1, children },
          });
        } else {
          drafts.push({
            ...base,
            type,
            points,
            body: bodyOf(type, block.itemShape ?? null, points, block),
          });
        }
        unitCursor += size;
      }
    }
  }
  return drafts;
}


/** Phần thân tối thiểu hợp lệ của một dạng câu, bám `itemShape` và `expectedScoring` khi khối khai. */
function bodyOf(
  type: string,
  shape: ChildConditionLite["itemShape"] | null,
  points: number,
  block: StructureBlock | null,
): Record<string, unknown> {
  const option = (label: string, correct: boolean) => ({ id: crypto.randomUUID(), content: `<p>${label}</p>`, correct });
  switch (type) {
    case "MULTIPLE_CHOICE": {
      const count = Math.max(2, Math.min(10, shape?.options ?? 4));
      return { type, options: Array.from({ length: count }, (_, index) => option(String.fromCharCode(65 + index), index < 2)), scoringRule: "ALL_OR_NOTHING", shuffleOptions: true };
    }
    case "SHORT_ANSWER":
      return { type, answers: ["1"], inputHint: null, answerFormat: block?.answerFormats?.[0] ?? null };
    case "TRUE_FALSE": {
      const count = Math.max(2, Math.min(8, shape?.statements ?? 4));
      const table = block?.expectedScoring?.trueFalseTable;
      const method = block?.expectedScoring?.trueFalseMethod ?? "PER_STATEMENT";
      return {
        type,
        statements: Array.from({ length: count }, (_, index) => ({
          id: crypto.randomUUID(),
          content: `<p>Mệnh đề ${index + 1}.</p>`,
          answer: index % 2 === 0,
          points: method === "PER_STATEMENT" ? points / count : null,
        })),
        scoringMethod: method,
        scoreTable: method === "COUNT_TABLE" ? (table?.length === count ? table : Array.from({ length: count }, (_, i) => ((i + 1) * points) / count)) : [],
      };
    }
    case "MATCHING": {
      const count = Math.max(1, shape?.leftItems ?? 4);
      return {
        type,
        left: Array.from({ length: count }, (_, index) => ({ id: `l${index + 1}`, content: `<p>Trái ${index + 1}</p>` })),
        right: Array.from({ length: count + 1 }, (_, index) => ({ id: `r${index + 1}`, content: `<p>Phải ${index + 1}</p>` })),
        pairs: Array.from({ length: count }, (_, index) => ({ leftId: `l${index + 1}`, rightId: `r${index + 1}`, points: points / count })),
        reuseRight: false,
      };
    }
    case "ESSAY":
      return { type, gradingGuide: `<p>Hướng dẫn chấm điều chế ${RUN_ID}.</p>`, rubric: [], sampleAnswer: null, wordLimit: null };
    case "SINGLE_CHOICE":
    default: {
      const count = Math.max(2, Math.min(10, shape?.options ?? 4));
      return { type: "SINGLE_CHOICE", options: Array.from({ length: count }, (_, index) => option(String.fromCharCode(65 + index), index === 0)), shuffleOptions: true };
    }
  }
}

/**
 * Tạo toàn bộ `drafts` thành câu Nháp rồi đưa lên Sẵn sàng hàng loạt (8 luồng tạo, một lời gọi `bulk/ready`).
 * Trả tóm tắt của các câu đã Sẵn sàng.
 */
export async function createReadyQuestions(admin: AdminApi, drafts: QuestionDraft[]): Promise<QuestionSummaryLite[]> {
  const created: QuestionSummaryLite[] = [];
  let next = 0;
  const worker = async () => {
    while (next < drafts.length) {
      const draft = drafts[next++]!;
      const response = await admin.api.post(`${ADMIN_URL}/api/v1/admin/questions`, {
        headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
        data: draft,
      });
      const detail = await body<{ question?: QuestionDetailLite } & QuestionDetailLite>(response, 201);
      const summary = detail.question?.summary ?? detail.summary;
      if (summary?.id) created.push({ id: summary.id, code: summary.code });
    }
  };
  await Promise.all(Array.from({ length: 8 }, worker));
  expect(created.length, "tạo câu thất bại một phần").toBe(drafts.length);
  if (created.length === 0) return created;
  const ready = await admin.api.post(`${ADMIN_URL}/api/v1/admin/questions/bulk/ready`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data: { ids: created.map((question) => question.id) },
  });
  const result = await body<{ updated?: QuestionSummaryLite[]; skipped?: Array<{ questionId: string; reason: string; detail?: string }> }>(ready, 200);
  expect(
    result.skipped ?? [],
    `bulk ready bỏ qua ${result.skipped?.length ?? 0}/${created.length} câu`,
  ).toEqual([]);
  return created;
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

/**
 * Điểm cuối kho lưu trữ của stack đang chạy thử. Không có mặc định: `localhost:9360` là MinIO dùng chung của máy dev, và
 * bước chuẩn bị ở đây XÓA, GHI object trong đó (C-0b, 2026-11-19). Thiếu biến thì dừng thay vì đụng vào kho chung.
 */
export function s3Endpoint(): string {
  const endpoint = process.env.THI360_S3_ENDPOINT?.trim();
  if (!endpoint) {
    throw new Error("THI360_S3_ENDPOINT chưa đặt: đặt về MinIO của stack đang chạy thử (stack lane: http://localhost:<57400+k>), không dùng kho dev dùng chung.");
  }
  return endpoint.replace(/\/+$/, "");
}

function s3Config(): S3Config {
  return {
    endpoint: s3Endpoint(),
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

/**
 * Công bố bị từ chối sau một kiểm tra không đạt: hộp thoại của trang Phiên bản và công bố báo "Sẽ công bố 0 / N", đường
 * không chọn được, nút "Công bố 0 đường lựa chọn" tắt; gọi thẳng API (đường của kiểm tra) bị từ chối 422 `PATH_NOT_VALID`
 * và bài không có phiên bản phục vụ.
 */
export async function expectPublishRefused(page: Page, admin: AdminApi, examId: string, check: CheckResult): Promise<void> {
  await openEditorTab(page, "Phiên bản và công bố", /\/exams\/[^/]+\/versions/);
  await page.getByRole("button", { name: "Công bố", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole("status")).toHaveText(/^Sẽ công bố 0 \/ \d+ đường lựa chọn$/);
  await expect(dialog.getByRole("checkbox").first()).toBeDisabled();
  // Không đường nào hợp lệ thì nút xác nhận tắt (F-PUBLISH0, ADV:QA:11).
  await expect(dialog.getByRole("button", { name: /^Công bố \d+ đường lựa chọn$/ })).toBeDisabled();
  const refused = await publishApi(
    admin,
    examId,
    check.paths.map((path) => path.pathId),
  );
  expect(refused.status(), await refused.text()).toBe(422);
  const refusal: { code?: string } = await refused.json();
  expect(refusal.code).toBe("PATH_NOT_VALID");
  expect((await getExam(admin, examId)).servingVersion ?? null).toBeNull();
}
