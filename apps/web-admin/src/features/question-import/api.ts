import { isContentMockEnabled } from "@/lib/mock/flags";
import * as mock from "./api.mock";
import * as real from "./api-real";

/**
 * API nhập câu hỏi từ tệp. Cờ `NEXT_PUBLIC_THI360_CONTENT_MOCK` bật thì chạy dữ liệu mẫu (`api.mock.ts`); tắt thì gọi
 * API thật (`api-real.ts`: tải tệp, kiểm tra, nhập với `Idempotency-Key`, lịch sử). Cờ đọc mỗi lần gọi.
 */

export { QUESTION_IMPORT_QUERY_KEYS } from "./keys";
export type { DownloadedFile } from "./api-real";

type AsyncFn<A extends unknown[], R> = (...args: A) => Promise<R>;

function selectApi<A extends unknown[], R>(mockFn: AsyncFn<A, R>, realFn: AsyncFn<A, R>): AsyncFn<A, R> {
  return (...args) => (isContentMockEnabled() ? mockFn(...args) : realFn(...args));
}

export const downloadQuestionTemplate = selectApi(mock.downloadQuestionTemplate, real.downloadQuestionTemplate);
export const uploadQuestionFile = selectApi(mock.uploadQuestionFile, real.uploadQuestionFile);
export const validateImport = selectApi(mock.validateImport, real.validateImport);
export const commitImport = selectApi(mock.commitImport, real.commitImport);
export const listImportHistory = selectApi(mock.listImportHistory, real.listImportHistory);

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
