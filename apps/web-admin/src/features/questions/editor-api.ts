import { contentApi } from "@/lib/mock/flags";
import * as mock from "./editor-api.mock";

/**
 * API soạn câu hỏi, nhân bản, lịch sử phiên bản và nơi đang dùng. Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì chạy
 * dữ liệu mẫu (`editor-api.mock.ts`); tắt thì nhánh API thật, chưa nối (FE-1).
 */

export const createQuestion = contentApi(mock.createQuestion);
export const updateQuestion = contentApi(mock.updateQuestion);
export const duplicateQuestion = contentApi(mock.duplicateQuestion);
export const listQuestionVersions = contentApi(mock.listQuestionVersions);
export const getQuestionUsage = contentApi(mock.getQuestionUsage);
export const restoreQuestionVersion = contentApi(mock.restoreQuestionVersion);

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
