import { questionAssetErrorReason, uploadQuestionAsset } from "../asset-upload";

/**
 * Tệp đính kèm của câu hỏi (UniNote PBI-03 mục 4.1; spec 4.2): PDF, DOCX, XLSX, PNG, JPEG, WebP, MP3, tối đa 20 MB.
 * Máy chủ kiểm lại bằng chữ ký byte; ở đây chỉ chặn sớm theo phần mở rộng và dung lượng.
 *
 * Tải lên gọi `POST /question-assets` qua `uploadQuestionAsset` với `kind = ATTACHMENT` (QA UNI-2621: lớp mẫu đã gỡ).
 */

export const ATTACHMENT_MAX_BYTES = 20 * 1024 * 1024;
export const ATTACHMENT_EXTENSIONS = ["pdf", "docx", "xlsx", "png", "jpg", "jpeg", "webp", "mp3"] as const;
/** Giá trị `accept` của ô chọn tệp. */
export const ATTACHMENT_ACCEPT = ATTACHMENT_EXTENSIONS.map((extension) => `.${extension}`).join(",");

export interface AttachmentInfo {
  id: string;
  name: string;
  size: number;
}

export type AttachmentRejection = "TOO_LARGE" | "UNSUPPORTED_TYPE";

/** `null` khi tệp được nhận; còn lại là lý do từ chối (chữ ở `questions.editor.attachments.rejected.*`). */
export function checkAttachment(file: Pick<File, "name" | "size">): AttachmentRejection | null {
  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (!(ATTACHMENT_EXTENSIONS as readonly string[]).includes(extension)) return "UNSUPPORTED_TYPE";
  if (file.size > ATTACHMENT_MAX_BYTES) return "TOO_LARGE";
  return null;
}

/** Lý do hiển thị khi máy chủ từ chối tệp (chữ ở `questions.editor.attachments.rejected.*`); lỗi khác là `FAILED`. */
export type AttachmentUploadRejection = AttachmentRejection | "RATE_LIMITED" | "FAILED";

/**
 * Đổi lỗi của `POST /question-assets` thành lý do cho người dùng: 413 quá dung lượng, 415/422 chữ ký tệp không khớp,
 * 429 tải quá nhiều; mọi lỗi còn lại là lỗi chung.
 */
export function attachmentUploadRejection(error: unknown): AttachmentUploadRejection {
  switch (questionAssetErrorReason(error)) {
    case "TOO_LARGE":
      return "TOO_LARGE";
    case "TYPE_NOT_ALLOWED":
      return "UNSUPPORTED_TYPE";
    case "RATE_LIMITED":
      return "RATE_LIMITED";
    default:
      return "FAILED";
  }
}

const uploaded = new Map<string, AttachmentInfo>();

export async function uploadQuestionAttachment(file: File): Promise<AttachmentInfo> {
  const rejection = checkAttachment(file);
  if (rejection) throw new Error(rejection);
  const asset = await uploadQuestionAsset(file, "ATTACHMENT");
  const info: AttachmentInfo = { id: asset.id, name: asset.fileName, size: asset.size };
  uploaded.set(info.id, info);
  return info;
}

/** Tên và dung lượng của tệp đã tải lên trong phiên; `undefined` với tệp có sẵn trên câu (chỉ biết id). */
export function attachmentInfo(id: string): AttachmentInfo | undefined {
  return uploaded.get(id);
}
