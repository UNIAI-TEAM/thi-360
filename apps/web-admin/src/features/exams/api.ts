import { mockRequest, paginate, type MockPage } from "@/lib/mock/content-mock";
import { examStore, MOCK_EXAM_TEMPLATES } from "./mock/store";
import type { ExamDetail, ExamSummary, ExamTemplate, ListExamsQuery } from "./types";

/**
 * API của Bài thi (danh sách, chi tiết, mẫu cấu trúc). Hiện trả dữ liệu mẫu từ `mock/store.ts`.
 * Khi nối backend: thay phần thân từng hàm bằng `createBrowserApiClient()`, giữ nguyên chữ ký.
 * Kiểm tra, xem trước, phiên bản và công bố nằm ở `review-api.ts`.
 */

export type PageResponseExamSummary = MockPage<ExamSummary>;

export const EXAMS_QUERY_KEYS = {
  all: ["exams"] as const,
  lists: () => ["exams", "list"] as const,
  list: (query: ListExamsQuery) => ["exams", "list", query] as const,
  detail: (id: string) => ["exams", "detail", id] as const,
  templates: () => ["exams", "templates"] as const,
};

const SUMMARY_KEYS = [
  "id", "code", "name", "kind", "format", "status", "servingVersion", "hasUnpublishedChanges", "totalQuestions",
  "durationMinutes", "englishOption", "combinationCount", "singleSubjectCode", "check", "attemptCount", "updatedAt",
  "updatedBy",
] as const satisfies readonly (keyof ExamSummary)[];

export function toExamSummary(exam: ExamDetail): ExamSummary {
  return Object.fromEntries(SUMMARY_KEYS.map((key) => [key, exam[key]])) as unknown as ExamSummary;
}

function matches(exam: ExamDetail, query: ListExamsQuery): boolean {
  const text = query.q?.trim().toLocaleLowerCase("vi");
  if (text && !exam.name.toLocaleLowerCase("vi").includes(text) && !exam.code.toLocaleLowerCase("vi").includes(text)) {
    return false;
  }
  if (query.kind && exam.kind !== query.kind) return false;
  if (query.format && exam.format !== query.format) return false;
  if (query.status && exam.status !== query.status) return false;
  if (query.checkStatus && exam.check.status !== query.checkStatus) return false;
  return true;
}

export async function listExams(query: ListExamsQuery): Promise<PageResponseExamSummary> {
  return mockRequest(
    () => {
      const rows = examStore
        .all()
        .filter((exam) => matches(exam, query))
        .map(toExamSummary)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
      return paginate(rows, query.page ?? 0, query.size ?? 20);
    },
    { empty: () => paginate<ExamSummary>([], 0, query.size ?? 20) },
  );
}

export async function getExam(id: string): Promise<ExamDetail> {
  return mockRequest(() => examStore.require(id));
}

export async function listExamTemplates(): Promise<ExamTemplate[]> {
  return mockRequest(() => [...MOCK_EXAM_TEMPLATES], { ignoreScenario: true });
}

export interface ExamsApiDeps {
  listExams: typeof listExams;
  getExam: typeof getExam;
  listExamTemplates: typeof listExamTemplates;
}

export const defaultExamsApiDeps: ExamsApiDeps = { listExams, getExam, listExamTemplates };
