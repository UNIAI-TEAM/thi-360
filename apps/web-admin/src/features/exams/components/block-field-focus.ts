import { useSyncExternalStore } from "react";
import { BLOCK_FIELDS, type BlockField } from "./block-form-fields";

/**
 * Yêu cầu đưa tiêu điểm vào đúng ô của nhóm đang mở khi người dùng bấm một lỗi (FU-FE4-PREVIEW). Hai đường vào:
 * `requestBlockFieldFocus` (bấm lỗi ở vùng tổng hợp của chính màn Cấu trúc) và tham số `field` của địa chỉ
 * `?block=<id>&field=<ô>` (bấm lỗi ở tab Kiểm tra, sang trang Cấu trúc). Màn nhóm nhận yêu cầu của nhóm mình qua
 * `useBlockFieldRequest`, đặt tiêu điểm sau khi vẽ xong rồi `consumeBlockFieldRequest` để không đặt lại. Chỉ màn nhóm của
 * biểu mẫu đầy đủ (MD) đọc: MT, DS đặt tiêu điểm theo `navigation.ts` của biểu mẫu theo môn.
 */
export interface BlockFieldRequest {
  blockId: string;
  field: BlockField;
}

let pending: BlockFieldRequest | null = null;
const listeners = new Set<() => void>();

const emit = () => listeners.forEach((listener) => listener());

export function requestBlockFieldFocus(blockId: string, field: BlockField): void {
  pending = { blockId, field };
  emit();
}

export function consumeBlockFieldRequest(): void {
  pending = null;
  emit();
}

export function isBlockField(value: string | null): value is BlockField {
  return value !== null && (BLOCK_FIELDS as readonly string[]).includes(value);
}

/** Ô của nhóm `blockId` mà địa chỉ đang xin (`?block=<id>&field=<ô>`); `null` khi không xin ô nào hoặc xin ô của nhóm khác. */
export function blockFieldFromUrl(search: string, blockId: string): BlockField | null {
  const params = new URLSearchParams(search);
  const field = params.get("field");
  return params.get("block") === blockId && isBlockField(field) ? field : null;
}

/** Bỏ tham số `field` khỏi địa chỉ sau khi đã đặt tiêu điểm: tải lại hay quay lại trang không nhảy tiêu điểm lần nữa. */
export function clearBlockFieldFromUrl(): void {
  const url = new URL(window.location.href);
  if (!url.searchParams.has("field")) return;
  url.searchParams.delete("field");
  window.history.replaceState(window.history.state, "", url);
}

/** Yêu cầu đang chờ của nhóm `blockId` (hoặc `null`): đổi mỗi lần có yêu cầu mới, kể cả cùng ô. */
export function useBlockFieldRequest(blockId: string): BlockFieldRequest | null {
  const request = useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => pending,
    () => null,
  );
  return request?.blockId === blockId ? request : null;
}
