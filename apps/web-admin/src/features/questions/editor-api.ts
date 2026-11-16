import { selectApi } from "@/features/content/api-switch";
import * as mock from "./editor-api.mock";
import * as real from "./editor-api.real";

/**
 * API soạn câu hỏi, nhân bản, lịch sử phiên bản và nơi đang dùng. Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì chạy
 * dữ liệu mẫu (`editor-api.mock.ts`); tắt thì `editor-api.real.ts` gọi `/api/v1/admin/questions`.
 */

export const createQuestion = selectApi(mock.createQuestion, real.createQuestion);
export const updateQuestion = selectApi(mock.updateQuestion, real.updateQuestion);
export const duplicateQuestion = selectApi(mock.duplicateQuestion, real.duplicateQuestion);
export const listQuestionVersions = selectApi(mock.listQuestionVersions, real.listQuestionVersions);
export const getQuestionUsage = selectApi(mock.getQuestionUsage, real.getQuestionUsage);
export const restoreQuestionVersion = selectApi(mock.restoreQuestionVersion, real.restoreQuestionVersion);

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
