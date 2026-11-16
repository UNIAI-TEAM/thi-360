import { selectApi } from "@/features/content/api-switch";
import * as mock from "./api.mock";
import * as real from "./api.real";

/**
 * API của Ngân hàng câu hỏi. Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì chạy dữ liệu mẫu (`api.mock.ts`); tắt thì
 * `api.real.ts` gọi `/api/v1/admin/questions`. Chữ ký giữ nguyên để hook trong `queries.ts` và giao diện không đổi.
 */

export { QUESTIONS_QUERY_KEYS } from "./keys";

/** Mã lỗi nghiệp vụ của thao tác đổi trạng thái (`ApiStatusError.code`); chữ ở `questions.statusDialogs.errors`. */
export const QUESTION_STATUS_ERROR_CODES = [
  "QUESTION_NOT_READY",
  "QUESTION_ALREADY_READY",
  "QUESTION_ALREADY_RETIRED",
  "QUESTION_REVISION_CONFLICT",
  "NOT_FOUND",
] as const;
export type QuestionStatusErrorCode = (typeof QUESTION_STATUS_ERROR_CODES)[number];

export { toQuestionSummary } from "./question-summary";

export const listQuestions = selectApi(mock.listQuestions, real.listQuestions);
export const getQuestionStatusCounts = selectApi(mock.getQuestionStatusCounts, real.getQuestionStatusCounts);
export const listQuestionAuthors = selectApi(mock.listQuestionAuthors, real.listQuestionAuthors);
export const getQuestion = selectApi(mock.getQuestion, real.getQuestion);
export const getQuestionReadiness = selectApi(mock.getQuestionReadiness, real.getQuestionReadiness);
export const markQuestionReady = selectApi(mock.markQuestionReady, real.markQuestionReady);
export const retireQuestion = selectApi(mock.retireQuestion, real.retireQuestion);
export const bulkMarkQuestionsReady = selectApi(mock.bulkMarkQuestionsReady, real.bulkMarkQuestionsReady);
export const bulkRetireQuestions = selectApi(mock.bulkRetireQuestions, real.bulkRetireQuestions);
export const getQuestionRetireImpact = selectApi(mock.getQuestionRetireImpact, real.getQuestionRetireImpact);

export type { QuestionAsset, QuestionAssetKind } from "./api.mock";

/**
 * Tải tài nguyên câu hỏi (`POST /question-assets`, spec 4.5): `kind` là `IMAGE` cho ảnh chèn vào
 * nội dung (PNG/JPEG/WebP, tối đa 5 MB) hoặc `ATTACHMENT` cho tệp đính kèm (tối đa 20 MB).
 */
export { uploadQuestionAsset } from "./asset-upload";

export interface QuestionsApiDeps {
  listQuestions: typeof listQuestions;
  getQuestion: typeof getQuestion;
}

export const defaultQuestionsApiDeps: QuestionsApiDeps = { listQuestions, getQuestion };

/** Phụ thuộc của màn danh sách và hai hộp thoại đổi trạng thái (thay từng hàm trong test). */
export interface QuestionBankApiDeps extends QuestionsApiDeps {
  getQuestionStatusCounts: typeof getQuestionStatusCounts;
  listQuestionAuthors: typeof listQuestionAuthors;
  getQuestionReadiness: typeof getQuestionReadiness;
  getQuestionRetireImpact: typeof getQuestionRetireImpact;
  markQuestionReady: typeof markQuestionReady;
  retireQuestion: typeof retireQuestion;
  bulkMarkQuestionsReady: typeof bulkMarkQuestionsReady;
  bulkRetireQuestions: typeof bulkRetireQuestions;
}

export const defaultQuestionBankApiDeps: QuestionBankApiDeps = {
  ...defaultQuestionsApiDeps,
  getQuestionStatusCounts,
  listQuestionAuthors,
  getQuestionReadiness,
  getQuestionRetireImpact,
  markQuestionReady,
  retireQuestion,
  bulkMarkQuestionsReady,
  bulkRetireQuestions,
};
