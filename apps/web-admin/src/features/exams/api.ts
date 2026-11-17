import * as http from "./api.http";
import { discontinueExam as discontinueReviewedExam } from "./review-api";
import type { ExamDetail, ExamListPage, ListExamsQuery } from "./types";

/**
 * API của Bài thi (danh sách, chi tiết, mẫu cấu trúc, tạo, sửa thông tin, sửa cấu trúc, nạp mẫu, sao chép, ngừng bán,
 * đếm nguồn): `api.http.ts` gọi API thật. Kiểm tra, xem trước, phiên bản và công bố nằm ở `review-api.ts`.
 * Lớp dữ liệu mẫu và cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` đã gỡ ở QA (UNI-2621).
 */

export { EXAMS_QUERY_KEYS } from "./keys";
export { toExamSummary } from "./exam-summary";

export const listExams = (query: ListExamsQuery, signal?: AbortSignal): Promise<ExamListPage> =>
  http.listExams(query, signal);
export const getExam = http.getExam;
export const listExamTemplates = http.listExamTemplates;
export const getExamStructure = http.getExamStructure;
export const getExamStructureSummary = http.getExamStructureSummary;
export const suggestExamCode = http.suggestExamCode;
export const createExam = http.createExam;
export const updateExamInfo = http.updateExamInfo;
export const updateExamStructure = http.updateExamStructure;
export const updateExamOverview = http.updateExamOverview;
export const applyExamTemplate = http.applyExamTemplate;
export const duplicateExam = http.duplicateExam;
/**
 * Ngừng bán. `expectedRevision` là `revision` của bản đang xem (dòng danh sách hoặc màn Phiên bản): lệch thì máy chủ
 * trả 412 `EXAM_REVISION_CONFLICT`. `idempotencyKey` do nơi gọi giữ: thử lại cùng một lần bấm dùng cùng khóa.
 */
export const discontinueExam = (id: string, expectedRevision: number, idempotencyKey?: string): Promise<ExamDetail> =>
  discontinueReviewedExam({ examId: id, expectedRevision, idempotencyKey });
export const countAvailableQuestions = http.countAvailableQuestions;
export const countSubjectAvailableQuestions = http.countSubjectAvailableQuestions;

export interface ExamsApiDeps {
  listExams: typeof listExams;
  getExam: typeof getExam;
  listExamTemplates: typeof listExamTemplates;
  getExamStructure: typeof getExamStructure;
  getExamStructureSummary: typeof getExamStructureSummary;
  suggestExamCode: typeof suggestExamCode;
  createExam: typeof createExam;
  updateExamInfo: typeof updateExamInfo;
  updateExamStructure: typeof updateExamStructure;
  updateExamOverview: typeof updateExamOverview;
  applyExamTemplate: typeof applyExamTemplate;
  duplicateExam: typeof duplicateExam;
  discontinueExam: typeof discontinueExam;
  countAvailableQuestions: typeof countAvailableQuestions;
  countSubjectAvailableQuestions: typeof countSubjectAvailableQuestions;
}

export const defaultExamsApiDeps: ExamsApiDeps = {
  listExams,
  getExam,
  listExamTemplates,
  getExamStructure,
  getExamStructureSummary,
  suggestExamCode,
  createExam,
  updateExamInfo,
  updateExamStructure,
  updateExamOverview,
  applyExamTemplate,
  duplicateExam,
  discontinueExam,
  countAvailableQuestions,
  countSubjectAvailableQuestions,
};
