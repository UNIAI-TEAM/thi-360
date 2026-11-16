import { contentApi } from "@/lib/mock/flags";
import * as mock from "./review-api.mock";

/**
 * API của Kiểm tra cấu hình, Kiểm tra kỹ, Sinh đề xem trước, Phiên bản và Công bố (PBI-04 mục 9–11). Cờ
 * `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì chạy dữ liệu mẫu (`review-api.mock.ts`); tắt thì nhánh API thật, chưa nối (FE-2).
 */

export { EXAM_REVIEW_QUERY_KEYS } from "./review-keys";

export const getExamCheck = contentApi(mock.getExamCheck);
export const runExamCheck = contentApi(mock.runExamCheck);
export const startDeepCheck = contentApi(mock.startDeepCheck);
export const getCheckRun = contentApi(mock.getCheckRun);
export const generatePreview = contentApi(mock.generatePreview);
export const listExamVersions = contentApi(mock.listExamVersions);
export const getExamVersion = contentApi(mock.getExamVersion);
export const publishExam = contentApi(mock.publishExam);
export const discontinueExam = contentApi(mock.discontinueExam);

export interface ExamReviewApiDeps {
  getExamCheck: typeof getExamCheck;
  runExamCheck: typeof runExamCheck;
  startDeepCheck: typeof startDeepCheck;
  getCheckRun: typeof getCheckRun;
  generatePreview: typeof generatePreview;
  listExamVersions: typeof listExamVersions;
  getExamVersion: typeof getExamVersion;
  publishExam: typeof publishExam;
  discontinueExam: typeof discontinueExam;
}

export const defaultExamReviewApiDeps: ExamReviewApiDeps = {
  getExamCheck,
  runExamCheck,
  startDeepCheck,
  getCheckRun,
  generatePreview,
  listExamVersions,
  getExamVersion,
  publishExam,
  discontinueExam,
};
