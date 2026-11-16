import { indexKnowledge, walkKnowledge } from "@/features/content/knowledge-tree";
import referenceFixture from "@/features/content/mock/reference.json";
import type { ContentReference, PersonRef, QuestionStatus } from "@/features/content/types";
import { examStore } from "@/features/exams/mock/store";
import type { ExamDetail, ExamSection, QuestionBlock } from "@/features/exams/types";
import { MockApiError, mockRequest, paginate } from "@/lib/mock/content-mock";
import { MOCK_QUESTION_AUTHORS, questionStore } from "./mock/store";
import { evaluateReadiness, firstReadinessIssue, type QuestionReadiness } from "./readiness";
import { toQuestionSummary } from "./question-summary";
import type {
  BulkQuestionStatusRequest,
  BulkQuestionStatusResult,
  ListQuestionsQuery,
  PageResponseQuestionSummary,
  QuestionDetail,
  QuestionListItem,
  QuestionRetireImpact,
  QuestionStatusCounts,
  QuestionSummary,
  QuestionUsageExam,
} from "./types";

/**
 * API của Ngân hàng câu hỏi. Hiện trả dữ liệu mẫu từ `mock/store.ts`.
 * Khi nối backend: thay phần thân từng hàm bằng `createBrowserApiClient().GET(...)` như `features/classes/api.ts`,
 * giữ nguyên chữ ký; hook trong `queries.ts` và giao diện không đổi.
 */

export const QUESTIONS_QUERY_KEYS = {
  all: ["questions"] as const,
  lists: () => ["questions", "list"] as const,
  list: (query: ListQuestionsQuery) => ["questions", "list", query] as const,
  statusCounts: (query: ListQuestionsQuery) => ["questions", "status-counts", query] as const,
  authors: () => ["questions", "authors"] as const,
  detail: (id: string) => ["questions", "detail", id] as const,
  readiness: (id: string) => ["questions", "readiness", id] as const,
  retireImpact: (id: string) => ["questions", "retire-impact", id] as const,
};

const reference = referenceFixture as ContentReference;

/**
 * Nút kiến thức của bộ lọc MAPID, kèm mọi nút con: chọn Chương hoặc Bài thì khớp mọi câu gắn kiến thức bên dưới
 * (canvas `NganHangCauHoi` lọc theo "1D4-2 Giới hạn của hàm số" là một Bài).
 */
function expandKnowledgeIds(ids: readonly string[]): Set<string> {
  const index = indexKnowledge(reference.knowledgeTree);
  const result = new Set<string>();
  for (const id of ids) {
    const entry = index.get(id);
    if (!entry) {
      result.add(id);
      continue;
    }
    walkKnowledge([entry.node], (node) => result.add(node.id));
  }
  return result;
}

function matchesQuery(question: QuestionDetail, query: ListQuestionsQuery): boolean {
  const text = query.q?.trim().toLocaleLowerCase("vi");
  if (
    text &&
    !question.code.toLocaleLowerCase("vi").includes(text) &&
    !question.contentPreview.toLocaleLowerCase("vi").includes(text) &&
    !(question.sourceCode ?? "").toLocaleLowerCase("vi").includes(text)
  ) {
    return false;
  }
  if (query.subjectCode && question.subjectCode !== query.subjectCode) return false;
  if (query.gradeCode && question.gradeCode !== query.gradeCode) return false;
  if (query.difficultyCode && question.difficultyCode !== query.difficultyCode) return false;
  if (query.type && question.type !== query.type) return false;
  if (query.status && question.status !== query.status) return false;
  if (query.competencyCode && question.competencyCode !== query.competencyCode) return false;
  if (query.taskTypeCode && question.taskTypeCode !== query.taskTypeCode) return false;
  if (query.tagCode && !question.tagCodes.includes(query.tagCode)) return false;
  if (query.createdById && question.createdBy.id !== query.createdById) return false;
  if (query.hasSolution !== undefined && question.hasSolution !== query.hasSolution) return false;
  if (query.knowledgeIds?.length) {
    const wanted = expandKnowledgeIds(query.knowledgeIds);
    if (!question.knowledge.some((ref) => wanted.has(ref.nodeId))) return false;
  }
  const updatedDay = question.updatedAt.slice(0, 10);
  if (query.updatedFrom && updatedDay < query.updatedFrom) return false;
  if (query.updatedTo && updatedDay > query.updatedTo) return false;
  const createdDay = question.createdAt.slice(0, 10);
  if (query.createdFrom && createdDay < query.createdFrom) return false;
  if (query.createdTo && createdDay > query.createdTo) return false;
  return true;
}

function compareBy(sort: string | undefined): (a: QuestionSummary, b: QuestionSummary) => number {
  const [field = "updatedAt", direction = "desc"] = (sort ?? "updatedAt,desc").split(",");
  const sign = direction === "asc" ? 1 : -1;
  return (a, b) => {
    const left = a[field as keyof QuestionSummary];
    const right = b[field as keyof QuestionSummary];
    if (typeof left === "number" && typeof right === "number") return (left - right) * sign;
    return String(left).localeCompare(String(right), "vi") * sign;
  };
}

export async function listQuestions(query: ListQuestionsQuery): Promise<PageResponseQuestionSummary> {
  return mockRequest(
    () => {
      const rows = questionStore
        .all()
        .filter((question) => matchesQuery(question, query))
        .map(
          (question): QuestionListItem => ({
            ...toQuestionSummary(question),
            readinessHint: question.status === "READY" ? null : firstReadinessIssue(readinessOf(question)),
          }),
        )
        .sort(compareBy(query.sort));
      return paginate(rows, query.page ?? 0, query.size ?? 20);
    },
    { empty: () => paginate<QuestionListItem>([], 0, query.size ?? 20) },
  );
}

/** Số câu theo trạng thái với cùng bộ lọc (bỏ `status`, trang, sắp xếp). */
export async function getQuestionStatusCounts(query: ListQuestionsQuery): Promise<QuestionStatusCounts> {
  const zero = (): QuestionStatusCounts => ({ total: 0, byStatus: { DRAFT: 0, READY: 0, RETIRED: 0 } });
  return mockRequest(
    () => {
      const filters: ListQuestionsQuery = { ...query, status: undefined, page: undefined, size: undefined, sort: undefined };
      const counts = zero();
      for (const question of questionStore.all()) {
        if (!matchesQuery(question, filters)) continue;
        counts.total += 1;
        counts.byStatus[question.status] += 1;
      }
      return counts;
    },
    { empty: zero },
  );
}

/** Người tạo câu hỏi, cho bộ lọc "Người tạo". */
export async function listQuestionAuthors(): Promise<PersonRef[]> {
  return mockRequest(() => [...MOCK_QUESTION_AUTHORS]);
}

export async function getQuestion(id: string): Promise<QuestionDetail> {
  return mockRequest(() => questionStore.require(id));
}

function readinessOf(question: QuestionDetail): QuestionReadiness {
  return evaluateReadiness(question, { reference, unavailableResources: [] });
}

/** Kiểm điều kiện chuyển sang Sẵn sàng (UniNote PBI-03 mục 7.2). */
export async function getQuestionReadiness(id: string): Promise<QuestionReadiness> {
  return mockRequest(() => readinessOf(questionStore.require(id)));
}

/** Khóa lạc quan (D13): lệch `revision` thì 412 `QUESTION_REVISION_CONFLICT`. */
export function assertQuestionRevision(question: Pick<QuestionDetail, "revision">, expectedRevision: number | undefined): void {
  if (expectedRevision !== undefined && expectedRevision !== question.revision) {
    throw new MockApiError(412, "QUESTION_REVISION_CONFLICT");
  }
}

/** Mọi lần ghi (kể cả đổi trạng thái) tăng `revision`. */
function setStatus(question: QuestionDetail, status: QuestionStatus): QuestionDetail {
  return questionStore.update(question.id, (current) => ({
    ...current,
    status,
    revision: current.revision + 1,
    updatedAt: new Date().toISOString(),
  }));
}

/**
 * Chuyển câu sang Sẵn sàng. 409 khi đã Sẵn sàng; 422 `QUESTION_NOT_READY` khi chưa đủ điều kiện (câu giữ nguyên
 * trạng thái). Câu Ngừng sử dụng đủ điều kiện thì được dùng lại.
 */
export async function markQuestionReady({ id, expectedRevision }: { id: string; expectedRevision: number }): Promise<QuestionSummary> {
  return mockRequest(
    () => {
      const question = questionStore.require(id);
      assertQuestionRevision(question, expectedRevision);
      if (question.status === "READY") throw new MockApiError(409, "QUESTION_ALREADY_READY");
      if (!readinessOf(question).ready) throw new MockApiError(422, "QUESTION_NOT_READY");
      return toQuestionSummary(setStatus(question, "READY"));
    },
    { ignoreScenario: true },
  );
}

/** Ngừng sử dụng: không xóa câu, lượt thi đang làm và kết quả cũ giữ nguyên (UniNote PBI-03 mục 9). */
export async function retireQuestion({ id, expectedRevision }: { id: string; expectedRevision: number }): Promise<QuestionSummary> {
  return mockRequest(
    () => {
      const question = questionStore.require(id);
      assertQuestionRevision(question, expectedRevision);
      if (question.status === "RETIRED") throw new MockApiError(409, "QUESTION_ALREADY_RETIRED");
      return toQuestionSummary(setStatus(question, "RETIRED"));
    },
    { ignoreScenario: true },
  );
}

function bulkChange(
  ids: readonly string[],
  target: QuestionStatus,
  canChange: (question: QuestionDetail) => boolean,
): BulkQuestionStatusResult {
  const result: BulkQuestionStatusResult = { succeeded: [], skipped: [] };
  for (const id of new Set(ids)) {
    const question = questionStore.get(id);
    if (!question) {
      result.skipped.push({ id, code: id, reason: "NOT_FOUND" });
    } else if (question.status === target) {
      result.skipped.push({ id, code: question.code, reason: "ALREADY_IN_STATUS" });
    } else if (!canChange(question)) {
      result.skipped.push({ id, code: question.code, reason: "NOT_READY" });
    } else {
      result.succeeded.push(toQuestionSummary(setStatus(question, target)));
    }
  }
  return result;
}

/** Chuyển nhiều câu sang Sẵn sàng; câu chưa đủ điều kiện được bỏ qua và nêu trong kết quả. */
export async function bulkMarkQuestionsReady({ ids }: BulkQuestionStatusRequest): Promise<BulkQuestionStatusResult> {
  return mockRequest(() => bulkChange(ids, "READY", (question) => readinessOf(question).ready), {
    ignoreScenario: true,
  });
}

export async function bulkRetireQuestions({ ids }: BulkQuestionStatusRequest): Promise<BulkQuestionStatusResult> {
  return mockRequest(() => bulkChange(ids, "RETIRED", () => true), { ignoreScenario: true });
}

// --- Nơi đang dùng (dữ liệu mẫu) ------------------------------------------------------------------------------

interface BlockMatch {
  section: ExamSection;
  block: QuestionBlock | null;
  optionCode: string | null;
}

function findBlock(exam: ExamDetail, question: QuestionDetail): BlockMatch | null {
  let subjectOnly: BlockMatch | null = null;
  for (const part of exam.parts) {
    const groups = [
      { optionCode: null, sections: part.sections },
      ...part.options.map((option) => ({
        optionCode: option.kind === "COMBINATION" ? option.code : null,
        sections: option.sections,
      })),
    ];
    for (const group of groups) {
      for (const section of group.sections) {
        if (section.subjectCode !== question.subjectCode) continue;
        const block = section.blocks.find((item) => item.questionType === question.type);
        if (block) return { section, block, optionCode: group.optionCode };
        subjectOnly ??= { section, block: null, optionCode: group.optionCode };
      }
    }
  }
  return subjectOnly;
}

/** Số câu khối cần ở ô Lớp × Độ khó của câu (ma trận gộp: ô; ma trận tách: chiều nhỏ hơn). */
function neededFor(block: QuestionBlock, question: QuestionDetail): number | null {
  const { matrix } = block;
  const toCount = (value: number) =>
    matrix.unit === "PERCENT" ? Math.round((value * block.questionCount) / 100) : value;
  if (matrix.mode === "COMBINED") {
    const cell = matrix.cells.find(
      (item) => item.gradeCode === question.gradeCode && item.difficultyCode === question.difficultyCode,
    );
    return cell ? toCount(cell.value) : null;
  }
  const byGrade = matrix.byGrade.find((item) => item.code === question.gradeCode);
  const byDifficulty = matrix.byDifficulty.find((item) => item.code === question.difficultyCode);
  if (!byGrade || !byDifficulty) return null;
  return toCount(Math.min(byGrade.value, byDifficulty.value));
}

function hashOf(text: string): number {
  let hash = 0;
  for (const char of text) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return hash;
}

/**
 * Bài thi đang dùng câu hỏi. Dữ liệu mẫu chưa lưu câu nào thuộc đề nào, nên lấy `usedInExamCount` bài có khối
 * cùng môn và dạng câu (ưu tiên bài đang phục vụ), xoay vòng theo mã câu để mỗi câu ra một danh sách khác nhau.
 */
function usageExams(question: QuestionDetail): QuestionUsageExam[] {
  if (question.usedInExamCount <= 0) return [];
  const rank = (match: BlockMatch | null, exam: ExamDetail) =>
    (match?.block ? 0 : match ? 2 : 4) + (exam.status === "PUBLISHED" ? 0 : 1);
  const candidates = examStore
    .all()
    .map((exam) => ({ exam, match: findBlock(exam, question) }))
    .sort((a, b) => rank(a.match, a.exam) - rank(b.match, b.exam) || a.exam.code.localeCompare(b.exam.code));
  const offset = candidates.length ? hashOf(question.id) % Math.min(candidates.length, 3) : 0;
  const rotated = [...candidates.slice(offset), ...candidates.slice(0, offset)];
  const available = questionStore
    .all()
    .filter(
      (other) =>
        other.id !== question.id &&
        other.status === "READY" &&
        other.subjectCode === question.subjectCode &&
        other.type === question.type &&
        other.gradeCode === question.gradeCode &&
        other.difficultyCode === question.difficultyCode,
    ).length;
  return rotated.slice(0, question.usedInExamCount).map(({ exam, match }) => ({
    examId: exam.id,
    examCode: exam.code,
    examName: exam.name,
    examStatus: exam.status,
    servingVersion: exam.servingVersion,
    subjectCode: match?.section.subjectCode ?? question.subjectCode,
    questionType: match?.block?.questionType ?? question.type,
    optionCode: match?.optionCode ?? null,
    needed: match?.block ? neededFor(match.block, question) : null,
    available,
  }));
}

/** Bài thi đang dùng câu và ảnh hưởng tới nguồn sinh đề nếu ngừng sử dụng câu (canvas `NgungCauHoi`). */
export async function getQuestionRetireImpact(id: string): Promise<QuestionRetireImpact> {
  return mockRequest(() => {
    const question = questionStore.require(id);
    return {
      questionId: question.id,
      exams: usageExams(question),
      hardDeleteBlocked: question.usedInExamCount > 0,
    };
  });
}
