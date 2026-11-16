import { isContentMockEnabled } from "@/lib/mock/flags";
import * as http from "./api.http";
import * as mock from "./api.mock";
import { discontinueExam as discontinueReviewedExam } from "./review-api";

/**
 * API của Bài thi (danh sách, chi tiết, mẫu cấu trúc, tạo, sửa thông tin, sửa cấu trúc, nạp mẫu, sao chép, ngừng bán,
 * đếm nguồn). Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì chạy dữ liệu mẫu (`api.mock.ts`); tắt thì gọi API thật
 * (`api.http.ts`). Kiểm tra, xem trước, phiên bản và công bố nằm ở `review-api.ts`.
 */

export { EXAMS_QUERY_KEYS } from "./keys";
export { toExamSummary } from "./exam-summary";

export const listExams: typeof mock.listExams = (...args) => (isContentMockEnabled() ? mock.listExams(...args) : http.listExams(...args));
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
 * Ngừng bán. Dòng danh sách của máy chủ không có `revision`, nên nhánh thật đọc revision hiện tại của bài rồi ngừng bán
 * qua `review-api` (cùng khóa Idempotency-Key và xử lý lỗi với màn Phiên bản).
 */
export const discontinueExam: typeof mock.discontinueExam = async (id, expectedRevision) =>
  isContentMockEnabled()
    ? mock.discontinueExam(id, expectedRevision)
    : discontinueReviewedExam({ examId: id, expectedRevision: (await http.getExam(id)).revision });
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
