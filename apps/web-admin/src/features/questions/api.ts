import { contentApi } from "@/lib/mock/flags";
import * as mock from "./api.mock";

/**
 * API của Ngân hàng câu hỏi. Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì chạy dữ liệu mẫu (`api.mock.ts`); tắt thì
 * nhánh API thật, chưa nối (FE-1). Chữ ký giữ nguyên để hook trong `queries.ts` và giao diện không đổi.
 */

export const QUESTIONS_QUERY_KEYS = mock.QUESTIONS_QUERY_KEYS;

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

export const listQuestions = contentApi(mock.listQuestions);
export const getQuestionStatusCounts = contentApi(mock.getQuestionStatusCounts);
export const listQuestionAuthors = contentApi(mock.listQuestionAuthors);
export const getQuestion = contentApi(mock.getQuestion);
export const getQuestionReadiness = contentApi(mock.getQuestionReadiness);
export const markQuestionReady = contentApi(mock.markQuestionReady);
export const retireQuestion = contentApi(mock.retireQuestion);
export const bulkMarkQuestionsReady = contentApi(mock.bulkMarkQuestionsReady);
export const bulkRetireQuestions = contentApi(mock.bulkRetireQuestions);
export const getQuestionRetireImpact = contentApi(mock.getQuestionRetireImpact);

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
