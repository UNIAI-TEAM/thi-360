import * as real from "./editor-api.real";

/**
 * API soạn câu hỏi, nhân bản, lịch sử phiên bản và nơi đang dùng: `editor-api.real.ts` gọi
 * `/api/v1/admin/questions`. Lớp dữ liệu mẫu và cờ `CONTENT_MOCK` đã gỡ ở QA (UNI-2621).
 */

export const createQuestion = real.createQuestion;
export const updateQuestion = real.updateQuestion;
export const duplicateQuestion = real.duplicateQuestion;
export const listQuestionVersions = real.listQuestionVersions;
export const getQuestionUsage = real.getQuestionUsage;
export const restoreQuestionVersion = real.restoreQuestionVersion;

export interface QuestionEditorApiDeps {
  createQuestion: typeof createQuestion;
  updateQuestion: typeof updateQuestion;
  duplicateQuestion: typeof duplicateQuestion;
  listQuestionVersions: typeof listQuestionVersions;
  getQuestionUsage: typeof getQuestionUsage;
  restoreQuestionVersion: typeof restoreQuestionVersion;
}

export const defaultQuestionEditorApiDeps: QuestionEditorApiDeps = {
  createQuestion,
  updateQuestion,
  duplicateQuestion,
  listQuestionVersions,
  getQuestionUsage,
  restoreQuestionVersion,
};
