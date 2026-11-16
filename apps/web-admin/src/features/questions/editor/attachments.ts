import { MockApiError, mockId, mockRequest } from "@/lib/mock/content-mock";

/**
 * Tệp đính kèm của câu hỏi (UniNote PBI-03 mục 4.1; spec 4.2): PDF, DOCX, XLSX, PNG, JPEG, WebP, MP3, tối đa 20 MB.
 * Máy chủ kiểm lại bằng chữ ký byte; ở đây chỉ chặn sớm theo phần mở rộng và dung lượng.
 *
 * Tải lên đang chạy trên dữ liệu mẫu: trả id giả và giữ tên, dung lượng trong bộ nhớ của phiên. Khi nối API (FE-1) thay
 * `uploadQuestionAttachment` bằng `uploadQuestionAsset` với `kind = ATTACHMENT`.
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

const uploaded = new Map<string, AttachmentInfo>();

export async function uploadQuestionAttachment(file: Pick<File, "name" | "size">): Promise<AttachmentInfo> {
  const rejection = checkAttachment(file);
  if (rejection) throw new MockApiError(422, rejection);
  return mockRequest(
    () => {
      const info: AttachmentInfo = { id: mockId("asset"), name: file.name, size: file.size };
      uploaded.set(info.id, info);
      return info;
    },
    { ignoreScenario: true },
  );
}

/** Tên và dung lượng của tệp đã tải lên trong phiên; `undefined` với tệp có sẵn trên câu (chỉ biết id). */
export function attachmentInfo(id: string): AttachmentInfo | undefined {
  return uploaded.get(id);
}
