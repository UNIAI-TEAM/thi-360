import { contentApi } from "@/lib/mock/flags";
import { MockApiError } from "@/lib/mock/content-mock";
import * as mock from "./api.mock";

/**
 * API nhập câu hỏi từ tệp. Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì chạy dữ liệu mẫu (`api.mock.ts`); tắt thì nhánh
 * API thật, chưa nối (FE-1).
 */

export { QUESTION_IMPORT_QUERY_KEYS } from "./keys";
export type { DownloadedFile } from "./api.mock";

export const downloadQuestionTemplate = contentApi(mock.downloadQuestionTemplate);
export const uploadQuestionFile = contentApi(mock.uploadQuestionFile);
export const validateImport = contentApi(mock.validateImport);
export const commitImport = contentApi(mock.commitImport);
export const listImportHistory = contentApi(mock.listImportHistory);

/** Mã lỗi nghiệp vụ của lời gọi API (ví dụ `IMPORT_EMPTY_FILE`), để màn hình dịch; không có thì `undefined`. */
export function importApiErrorCode(error: unknown): string | undefined {
  return error instanceof MockApiError ? error.code : undefined;
}

export interface QuestionImportApiDeps {
  downloadQuestionTemplate: typeof downloadQuestionTemplate;
  uploadQuestionFile: typeof uploadQuestionFile;
  validateImport: typeof validateImport;
  commitImport: typeof commitImport;
  listImportHistory: typeof listImportHistory;
}

export const defaultQuestionImportApiDeps: QuestionImportApiDeps = {
  downloadQuestionTemplate,
  uploadQuestionFile,
  validateImport,
  commitImport,
  listImportHistory,
};
