import { notify, toast } from "@thi360/ui";

/**
 * Toast lỗi tải ảnh chèn vào nội dung (không tự ẩn). Nhớ id để khi tệp đính kèm bị từ chối, dải lỗi mới thay chỗ
 * đúng các toast này (F-09) mà không đóng toast khác đang hiện (lưu thành công, lỗi mạng...).
 */
const imageUploadToastIds = new Set<string | number>();

export function notifyImageUploadError(message: string): void {
  imageUploadToastIds.add(notify.error(message));
}

export function dismissImageUploadToasts(): void {
  for (const id of imageUploadToastIds) toast.dismiss(id);
  imageUploadToastIds.clear();
}
