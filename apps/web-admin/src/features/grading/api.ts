import { gradingApi } from "@/lib/mock/flags";
import * as mock from "./api.mock";

/**
 * API của hàng chờ chấm tự luận. Cờ `NEXT_PUBLIC_THI360_GRADING_MOCK` bật thì chạy dữ liệu mẫu (`api.mock.ts`, đọc đề
 * từ kho câu hỏi mẫu nên không phụ thuộc cờ `CONTENT_MOCK`); tắt thì nhánh API thật, chưa nối (đợt chấm điểm).
 */

export type { PageResponseGradingAttempt } from "./api.mock";
export const GRADING_QUERY_KEYS = mock.GRADING_QUERY_KEYS;
export const GRADING_ERROR_CODES = mock.GRADING_ERROR_CODES;
export type { GradingErrorCode } from "./api.mock";
export const SORTABLE_GRADING_FIELDS = mock.SORTABLE_GRADING_FIELDS;
export const DEFAULT_GRADING_SORT = mock.DEFAULT_GRADING_SORT;

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
