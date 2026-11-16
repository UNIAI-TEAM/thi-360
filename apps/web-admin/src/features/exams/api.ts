import { contentApi } from "@/lib/mock/flags";
import * as mock from "./api.mock";

/**
 * API của Bài thi (danh sách, chi tiết, mẫu cấu trúc, tạo, sửa thông tin, sửa cấu trúc, nạp mẫu, sao chép, ngừng bán,
 * đếm nguồn). Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì chạy dữ liệu mẫu (`api.mock.ts`); tắt thì nhánh API thật,
 * chưa nối (FE-2). Kiểm tra, xem trước, phiên bản và công bố nằm ở `review-api.ts`.
 */

export { EXAMS_QUERY_KEYS } from "./keys";
export { toExamSummary } from "./exam-summary";

export const listExams = contentApi(mock.listExams);
export const getExam = contentApi(mock.getExam);
export const listExamTemplates = contentApi(mock.listExamTemplates);
export const getExamStructure = contentApi(mock.getExamStructure);
export const suggestExamCode = contentApi(mock.suggestExamCode);
export const createExam = contentApi(mock.createExam);
export const updateExamInfo = contentApi(mock.updateExamInfo);
export const updateExamStructure = contentApi(mock.updateExamStructure);
export const applyExamTemplate = contentApi(mock.applyExamTemplate);
export const duplicateExam = contentApi(mock.duplicateExam);
export const discontinueExam = contentApi(mock.discontinueExam);
export const countAvailableQuestions = contentApi(mock.countAvailableQuestions);
export const countSubjectAvailableQuestions = contentApi(mock.countSubjectAvailableQuestions);

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
