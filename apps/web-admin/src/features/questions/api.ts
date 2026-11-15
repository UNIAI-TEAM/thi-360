import { mockRequest, paginate, type MockPage } from "@/lib/mock/content-mock";
import { questionStore } from "./mock/store";
import type { ListQuestionsQuery, QuestionDetail, QuestionSummary } from "./types";

/**
 * API của Ngân hàng câu hỏi. Hiện trả dữ liệu mẫu từ `mock/store.ts`.
 * Khi nối backend: thay phần thân từng hàm bằng `createBrowserApiClient().GET(...)` như `features/classes/api.ts`,
 * giữ nguyên chữ ký; hook trong `queries.ts` và giao diện không đổi.
 */

export type PageResponseQuestionSummary = MockPage<QuestionSummary>;

export const QUESTIONS_QUERY_KEYS = {
  all: ["questions"] as const,
  lists: () => ["questions", "list"] as const,
  list: (query: ListQuestionsQuery) => ["questions", "list", query] as const,
  detail: (id: string) => ["questions", "detail", id] as const,
};

const SUMMARY_KEYS = [
  "id", "code", "type", "status", "contentPreview", "subjectCode", "gradeCode", "difficultyCode", "points",
  "knowledge", "tagCodes", "hasSolution", "version", "usedInExamCount", "sourceCode", "createdBy", "createdAt",
  "updatedAt",
] as const satisfies readonly (keyof QuestionSummary)[];

export function toQuestionSummary(question: QuestionDetail): QuestionSummary {
  return Object.fromEntries(SUMMARY_KEYS.map((key) => [key, question[key]])) as unknown as QuestionSummary;
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
  if (query.tagCode && !question.tagCodes.includes(query.tagCode)) return false;
  if (query.createdById && question.createdBy.id !== query.createdById) return false;
  if (query.hasSolution !== undefined && question.hasSolution !== query.hasSolution) return false;
  if (query.knowledgeIds?.length) {
    const wanted = new Set(query.knowledgeIds);
    if (!question.knowledge.some((ref) => wanted.has(ref.nodeId))) return false;
  }
  const updatedDay = question.updatedAt.slice(0, 10);
  if (query.updatedFrom && updatedDay < query.updatedFrom) return false;
  if (query.updatedTo && updatedDay > query.updatedTo) return false;
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
        .map(toQuestionSummary)
        .sort(compareBy(query.sort));
      return paginate(rows, query.page ?? 0, query.size ?? 20);
    },
    { empty: () => paginate<QuestionSummary>([], 0, query.size ?? 20) },
  );
}

export async function getQuestion(id: string): Promise<QuestionDetail> {
  return mockRequest(() => questionStore.require(id));
}

export interface QuestionsApiDeps {
  listQuestions: typeof listQuestions;
  getQuestion: typeof getQuestion;
}

export const defaultQuestionsApiDeps: QuestionsApiDeps = { listQuestions, getQuestion };
