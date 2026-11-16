import * as real from "./api-real";

/**
 * API nhập câu hỏi từ tệp: gọi API thật (`api-real.ts`: tải tệp, kiểm tra, nhập với `Idempotency-Key`,
 * lịch sử). Bản mẫu và cờ `CONTENT_MOCK` đã gỡ ở QA (UNI-2621).
 */

export { QUESTION_IMPORT_QUERY_KEYS } from "./keys";
export type { DownloadedFile } from "./api-real";

export const downloadQuestionTemplate = real.downloadQuestionTemplate;
export const uploadQuestionFile = real.uploadQuestionFile;
export const validateImport = real.validateImport;
export const commitImport = real.commitImport;
export const listImportHistory = real.listImportHistory;

/** Mã lỗi nghiệp vụ của lời gọi API (ví dụ `IMPORT_EMPTY_FILE`), để màn hình dịch; không có thì `undefined`. */
export function importApiErrorCode(error: unknown): string | undefined {
  if (typeof error === "object" && error !== null && "code" in error && typeof error.code === "string") {
    return error.code;
  }
  return undefined;
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
