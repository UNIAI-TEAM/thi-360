import type { ApiError, FieldError } from "@thi360/api-client";
import { ApiStatusError } from "@/lib/api/query-client";
import type { ExamDetail } from "./types";

/** Lỗi của API Bài thi: mang `{status, code, fieldErrors}` để màn hình xử lý chung. */
export class ExamsApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];

  constructor(status: number, message = "", code?: string, fieldErrors: readonly FieldError[] = []) {
    super(status, message);
    this.name = "ExamsApiError";
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function toExamsApiError(status: number, error: unknown): ExamsApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError>;
    const code = typeof body.code === "string" ? body.code : undefined;
    const message = typeof body.message === "string" ? body.message : "";
    const fieldErrors = Array.isArray(body.fieldErrors) ? body.fieldErrors : [];
    return new ExamsApiError(status, message, code, fieldErrors);
  }
  return new ExamsApiError(status);
}

/**
 * Tạo bài từ mẫu đã xong (POST 201) nhưng lưu cấu trúc tiếp theo thất bại: `exam` là bài Nháp đã có.
 * Nơi gọi chuyển sang bài đó thay vì POST lại (tránh trùng mã).
 */
export class ExamStructureSaveError extends ExamsApiError {
  readonly exam: ExamDetail;

  constructor(exam: ExamDetail, cause: unknown) {
    const base = cause instanceof ExamsApiError ? cause : undefined;
    super(base?.status ?? 0, base?.message, base?.code, base?.fieldErrors);
    this.name = "ExamStructureSaveError";
    this.exam = exam;
    this.cause = cause;
  }
}

/** Mã lỗi của backend (`ApiError.code`); `undefined` khi không có. */
export function errorCodeOf(error: unknown): string | undefined {
  if (!(error instanceof ApiStatusError)) return undefined;
  const { code } = error as ApiStatusError & { code?: unknown };
  return typeof code === "string" ? code : undefined;
}

/**
 * Lý do của 409 `CHECK_REQUIRED` khi công bố: backend gửi trong `fieldErrors[0]` (`{field: "reason", code: lý do}`;
 * spec 5.2 — `NO_CHECK`, `STRUCTURE_CHANGED`, `CONFIG_CHANGED`, `SOURCE_CHANGED`, `RULES_CHANGED`, `PATH_NOT_VALID`...).
 * `null` khi không phải lỗi đó; `"UNKNOWN"` khi thiếu lý do.
 */
export function checkRequiredReason(error: unknown): string | null {
  if (!(error instanceof ApiStatusError) || error.status !== 409 || errorCodeOf(error) !== "CHECK_REQUIRED") {
    return null;
  }
  const { fieldErrors } = error as ApiStatusError & { fieldErrors?: readonly FieldError[] };
  return fieldErrors?.find((item) => item.field === "reason")?.code ?? "UNKNOWN";
}
