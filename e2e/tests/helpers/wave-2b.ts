import { expect, type APIResponse, type Locator, type Page } from "@playwright/test";
import { ADMIN_URL, type AdminApi, RUN_ID } from "./editor-qa";

/**
 * Dùng chung cho các kịch bản E2E của nghiệm thu đợt 2B (`wave-2b-*.spec.ts`): tạo bài, lưu cấu trúc, kiểm tra, sinh thử
 * và công bố qua API; mở tab Đề xem trước và bắt kết quả sinh thử qua giao diện. Mọi giá trị nghiệp vụ (số câu, dải câu,
 * điểm, mã kiểm tra) do từng spec khẳng định; ở đây chỉ có kiểu và hàm đọc.
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

/** Tạo bài (201) bằng mẫu mặc định của lựa chọn; trả bài vừa tạo kèm `templateCode`. */
export async function createExam(admin: AdminApi, data: Record<string, unknown>): Promise<CreatedExam> {
  const response = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams`, {
    headers: { ...admin.headers, "idempotency-key": crypto.randomUUID() },
    data,
  });
  return body<CreatedExam>(response, 201);
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

/**
 * Lưu cấu trúc (PUT) từ bản đang có, tùy chọn sửa trước. Máy chủ phải trả 200; trả cấu trúc đọc lại sau khi lưu.
 * Không sửa thì đây là "Lưu" của bước 2 trên bản mẫu nguyên vẹn.
 */
export async function saveStructure(admin: AdminApi, id: string, edit?: (structure: Structure) => void): Promise<Structure> {
  const current = await getStructure(admin, id);
  edit?.(current);
  const put = await admin.api.put(`${ADMIN_URL}/api/v1/admin/exams/${id}/structure`, {
    headers: admin.headers,
    data: { expectedRevision: current.revision, parts: current.parts, ...(current.examQuotas ? { examQuotas: current.examQuotas } : {}) },
  });
  expect(put.status(), await put.text()).toBe(200);
  return getStructure(admin, id);
}

/** Kiểm tra đồng bộ (200): `scope` mặc định là toàn bài. */
export async function checkExam(
  admin: AdminApi,
  id: string,
  request: { scope?: "EXAM" | "PART" | "SECTION"; sectionId?: string; partId?: string } = { scope: "EXAM" },
): Promise<CheckResult> {
  const response = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/check`, {
    headers: admin.headers,
    data: request,
    timeout: SOLVER_TIMEOUT_MS,
  });
  return body<CheckResult>(response, 200);
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

/** Sinh thử bằng API (không qua giao diện). */
export async function previewApi(admin: AdminApi, id: string, data: Record<string, unknown>): Promise<PreviewResultLite> {
  const response = await admin.api.post(`${ADMIN_URL}/api/v1/admin/exams/${id}/preview`, {
    headers: admin.headers,
    data,
    timeout: SOLVER_TIMEOUT_MS,
  });
  return body<PreviewResultLite>(response, 200);
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

/* ---------- Giao diện: tab Đề xem trước ---------- */

/** Mở tab Đề xem trước của bài (tùy chọn chọn sẵn một đường) và trả vùng nội dung của tab. */
export async function openPreview(page: Page, examId: string, pathId?: string): Promise<Locator> {
  const query = pathId ? `&path=${encodeURIComponent(pathId)}` : "";
  await page.goto(`${ADMIN_URL}/exams/${examId}/review?tab=preview${query}`);
  const panel = page.getByRole("tabpanel", { name: "Đề xem trước" });
  await expect(panel).toBeVisible();
  return panel;
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

/** Sinh đề lần đầu ở tab Đề xem trước (nút "Sinh đề xem trước") và trả vùng nội dung cùng kết quả. */
export async function generateInUi(
  page: Page,
  examId: string,
  pathId?: string,
): Promise<{ panel: Locator; request: Record<string, unknown>; result: PreviewResultLite }> {
  const panel = await openPreview(page, examId, pathId);
  const captured = await clickAndCapturePreview(page, examId, panel.getByRole("button", { name: "Sinh đề xem trước" }));
  return { panel, ...captured };
}
