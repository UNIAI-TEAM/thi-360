import type { ApiError, FieldError } from "@thi360/api-client";
import { ApiStatusError } from "@/lib/api/query-client";

/**
 * Lỗi của API ngân hàng câu hỏi và dữ liệu tham chiếu: mã HTTP, mã nghiệp vụ của `ApiError.code` (ví dụ
 * `QUESTION_REVISION_CONFLICT`) và lỗi theo trường (422). Màn hình đọc qua `apiErrorCode`, `apiFieldErrors` để dùng
 * chung với các lớp lỗi API khác (`ExamsApiError`, lỗi nhập câu hỏi) cũng mang `code`.
 */
export class ContentApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];

  constructor(status: number, message = "", code?: string, fieldErrors: readonly FieldError[] = []) {
    super(status, message);
    this.name = "ContentApiError";
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

/** Dựng lỗi từ thân phản hồi lỗi chuẩn `{code, message, fieldErrors[], traceId}`; thân khác dạng thì chỉ giữ mã HTTP. */
export function toContentApiError(status: number, error: unknown): ContentApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError>;
    return new ContentApiError(
      status,
      typeof body.message === "string" ? body.message : "",
      typeof body.code === "string" ? body.code : undefined,
      Array.isArray(body.fieldErrors) ? body.fieldErrors : [],
    );
  }
  return new ContentApiError(status);
}

/** Mã nghiệp vụ của lỗi API; `undefined` khi lỗi không mang mã. */
export function apiErrorCode(error: unknown): string | undefined {
  if (typeof error !== "object" || error === null || !("code" in error)) return undefined;
  return typeof error.code === "string" && error.code !== "" ? error.code : undefined;
}

export function apiFieldErrors(error: unknown): readonly FieldError[] {
  if (typeof error !== "object" || error === null || !("fieldErrors" in error)) return [];
  return Array.isArray(error.fieldErrors) ? (error.fieldErrors as FieldError[]) : [];
}

/** Khóa lạc quan lệch (D13): người khác đã sửa câu hỏi sau khi màn này mở. */
export function isRevisionConflict(error: unknown): boolean {
  return apiErrorCode(error) === "QUESTION_REVISION_CONFLICT" || (error as { status?: unknown } | null)?.status === 412;
}
