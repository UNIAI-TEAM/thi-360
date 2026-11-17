import { gradingApi } from "@/lib/mock/flags";
import * as mock from "./api.mock";

/**
 * API của hàng chờ chấm tự luận. Cờ `NEXT_PUBLIC_THI360_GRADING_MOCK` bật thì chạy dữ liệu mẫu (`api.mock.ts`, đọc đề
 * từ kho câu hỏi mẫu của chính feature); tắt thì nhánh API thật, chưa nối (đợt chấm điểm).
 */

export type { PageResponseGradingAttempt } from "./api.mock";
export {
  DEFAULT_GRADING_SORT,
  GRADING_ERROR_CODES,
  GRADING_QUERY_KEYS,
  SORTABLE_GRADING_FIELDS,
  type GradingErrorCode,
} from "./keys";

export const listGradingAttempts = gradingApi(mock.listGradingAttempts);
export const getGradingStatusCounts = gradingApi(mock.getGradingStatusCounts);
export const getGradingFilterOptions = gradingApi(mock.getGradingFilterOptions);
export const getGradingAttempt = gradingApi(mock.getGradingAttempt);
export const saveGradingDraft = gradingApi(mock.saveGradingDraft);
export const finishGrading = gradingApi(mock.finishGrading);

export interface GradingApiDeps {
  listGradingAttempts: typeof listGradingAttempts;
  getGradingStatusCounts: typeof getGradingStatusCounts;
  getGradingFilterOptions: typeof getGradingFilterOptions;
  getGradingAttempt: typeof getGradingAttempt;
  saveGradingDraft: typeof saveGradingDraft;
  finishGrading: typeof finishGrading;
}

export const defaultGradingApiDeps: GradingApiDeps = {
  listGradingAttempts,
  getGradingStatusCounts,
  getGradingFilterOptions,
  getGradingAttempt,
  saveGradingDraft,
  finishGrading,
};
