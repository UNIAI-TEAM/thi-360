import { isContentMockEnabled } from "@/lib/mock/flags";
import * as http from "./api.http";
import * as mock from "./api.mock";
import { discontinueExam as discontinueReviewedExam } from "./review-api";
import type { ExamDetail, ExamListPage, ListExamsQuery } from "./types";

/**
 * API của Bài thi (danh sách, chi tiết, mẫu cấu trúc, tạo, sửa thông tin, sửa cấu trúc, nạp mẫu, sao chép, ngừng bán,
 * đếm nguồn). Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì chạy dữ liệu mẫu (`api.mock.ts`); tắt thì gọi API thật
 * (`api.http.ts`). Kiểm tra, xem trước, phiên bản và công bố nằm ở `review-api.ts`.
 */

export { EXAMS_QUERY_KEYS } from "./keys";
export { toExamSummary } from "./exam-summary";

export const listExams = (query: ListExamsQuery, signal?: AbortSignal): Promise<ExamListPage> =>
  isContentMockEnabled() ? mock.listExams(query) : http.listExams(query, signal);
export const getExam: typeof mock.getExam = (...args) => (isContentMockEnabled() ? mock.getExam(...args) : http.getExam(...args));
export const listExamTemplates: typeof mock.listExamTemplates = (...args) => (isContentMockEnabled() ? mock.listExamTemplates(...args) : http.listExamTemplates(...args));
export const getExamStructure: typeof mock.getExamStructure = (...args) => (isContentMockEnabled() ? mock.getExamStructure(...args) : http.getExamStructure(...args));
export const suggestExamCode: typeof mock.suggestExamCode = (...args) => (isContentMockEnabled() ? mock.suggestExamCode(...args) : http.suggestExamCode(...args));
export const createExam: typeof mock.createExam = (...args) => (isContentMockEnabled() ? mock.createExam(...args) : http.createExam(...args));
export const updateExamInfo: typeof mock.updateExamInfo = (...args) => (isContentMockEnabled() ? mock.updateExamInfo(...args) : http.updateExamInfo(...args));
export const updateExamStructure: typeof mock.updateExamStructure = (...args) => (isContentMockEnabled() ? mock.updateExamStructure(...args) : http.updateExamStructure(...args));
export const applyExamTemplate: typeof mock.applyExamTemplate = (...args) => (isContentMockEnabled() ? mock.applyExamTemplate(...args) : http.applyExamTemplate(...args));
export const duplicateExam: typeof mock.duplicateExam = (...args) => (isContentMockEnabled() ? mock.duplicateExam(...args) : http.duplicateExam(...args));
/**
 * Ngừng bán. Nơi gọi có `revision` của bản đang xem (màn Phiên bản) gửi làm `expectedRevision`: lệch thì máy chủ trả
 * 412 `EXAM_REVISION_CONFLICT`. Dòng danh sách của máy chủ không có `revision`, nên nơi gọi đó không gửi và nhánh
 * thật đọc revision hiện tại của bài rồi ngừng bán — bước "lần ghi cuối thắng" có chủ đích. `idempotencyKey` do nơi
 * gọi giữ: thử lại cùng một lần bấm dùng cùng khóa.
 */
export const discontinueExam = async (
  id: string,
  expectedRevision?: number,
  idempotencyKey?: string,
): Promise<ExamDetail> =>
  isContentMockEnabled()
    ? mock.discontinueExam(id, expectedRevision)
    : discontinueReviewedExam({
        examId: id,
        expectedRevision: expectedRevision ?? (await http.getExam(id)).revision,
        idempotencyKey,
      });
export const countAvailableQuestions: typeof mock.countAvailableQuestions = (...args) => (isContentMockEnabled() ? mock.countAvailableQuestions(...args) : http.countAvailableQuestions(...args));
export const countSubjectAvailableQuestions: typeof mock.countSubjectAvailableQuestions = (...args) => (isContentMockEnabled() ? mock.countSubjectAvailableQuestions(...args) : http.countSubjectAvailableQuestions(...args));

export interface ExamsApiDeps {
  listExams: typeof listExams;
  getExam: typeof getExam;
  listExamTemplates: typeof listExamTemplates;
  getExamStructure: typeof getExamStructure;
  suggestExamCode: typeof suggestExamCode;
  createExam: typeof createExam;
  updateExamInfo: typeof updateExamInfo;
  updateExamStructure: typeof updateExamStructure;
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
  suggestExamCode,
  createExam,
  updateExamInfo,
  updateExamStructure,
  applyExamTemplate,
  duplicateExam,
  discontinueExam,
  countAvailableQuestions,
  countSubjectAvailableQuestions,
};
