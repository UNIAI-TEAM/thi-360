import { toContentApiError } from "@/features/content/api-error";
import { createBrowserApiClient } from "@/lib/api/client";
import { ApiStatusError } from "@/lib/api/query-client";

/**
 * Tải tài nguyên câu hỏi (`POST /question-assets`, spec 4.5): `kind` là `IMAGE` cho ảnh chèn vào nội dung (PNG, JPEG,
 * WebP, tối đa 5 MB) hoặc `ATTACHMENT` cho tệp đính kèm (tối đa 20 MB). Máy chủ kiểm loại theo chữ ký byte, giới hạn
 * 60 tệp mỗi 10 phút mỗi người. Tải xuống là `GET /question-assets/{id}` (`QuestionAsset.url`).
 * Bản mẫu và cờ `CONTENT_MOCK` đã gỡ ở QA (UNI-2621).
 */

export type QuestionAssetKind = "IMAGE" | "ATTACHMENT";

export interface QuestionAsset {
  id: string;
  url: string;
  fileName: string;
  size: number;
}

export async function uploadQuestionAsset(file: File, kind: QuestionAssetKind): Promise<QuestionAsset> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/question-assets", {
    params: { query: { kind } },
    body: { file: file as unknown as string },
    bodySerializer: () => {
      const formData = new FormData();
      formData.append("file", file);
      return formData;
    },
  });
  if (!response.ok || !data?.id) throw toContentApiError(response.status, error);
  return {
    id: data.id,
    url: data.url ?? `/api/v1/admin/question-assets/${data.id}`,
    fileName: data.fileName ?? file.name,
    size: data.size ?? file.size,
  };
}

function errorCode(error: unknown): string | undefined {
  if (typeof error === "object" && error !== null && "code" in error && typeof error.code === "string") {
    return error.code;
  }
  return undefined;
}

export type QuestionAssetErrorReason = "TOO_LARGE" | "TYPE_NOT_ALLOWED" | "RATE_LIMITED" | "FORBIDDEN" | "UNKNOWN";

/**
 * Lý do tải thất bại. 413 (kể cả khi tầng web chặn trước nên không có thân lỗi) và `ASSET_TOO_LARGE` là quá dung
 * lượng; 422 `ASSET_TYPE_NOT_ALLOWED` là loại tệp không nhận; 429 là tải quá nhiều.
 */
export function questionAssetErrorReason(error: unknown): QuestionAssetErrorReason {
  const status = error instanceof ApiStatusError ? error.status : undefined;
  const code = errorCode(error);
  if (status === 413 || code === "ASSET_TOO_LARGE") return "TOO_LARGE";
  if (code === "ASSET_TYPE_NOT_ALLOWED") return "TYPE_NOT_ALLOWED";
  if (status === 429) return "RATE_LIMITED";
  if (status === 403) return "FORBIDDEN";
  return "UNKNOWN";
}

/** Khóa chữ dưới `questions.assets.errors` (vi và en cùng khóa); giới hạn 5 MB và 20 MB nên khác nhau theo `kind`. */
export type QuestionAssetErrorMessageKey =
  | "imageTooLarge"
  | "attachmentTooLarge"
  | "imageTypeNotAllowed"
  | "attachmentTypeNotAllowed"
  | "rateLimited"
  | "forbidden"
  | "uploadFailed";

export function questionAssetErrorMessageKey(error: unknown, kind: QuestionAssetKind): QuestionAssetErrorMessageKey {
  switch (questionAssetErrorReason(error)) {
    case "TOO_LARGE":
      return kind === "IMAGE" ? "imageTooLarge" : "attachmentTooLarge";
    case "TYPE_NOT_ALLOWED":
      return kind === "IMAGE" ? "imageTypeNotAllowed" : "attachmentTypeNotAllowed";
    case "RATE_LIMITED":
      return "rateLimited";
    case "FORBIDDEN":
      return "forbidden";
    default:
      return "uploadFailed";
  }
}
