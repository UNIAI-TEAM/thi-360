import { useSyncExternalStore } from "react";
import { BLOCK_FIELDS, type BlockField } from "./block-form-fields";

export const BLOCK_FIELD_FOCUS_TTL_MS = 2000;

export interface BlockFieldRequest {
  blockId: string;
  field: BlockField;
  requestedAt: number;
}

let pending: BlockFieldRequest | null = null;
let expireTimer: ReturnType<typeof setTimeout> | null = null;
const listeners = new Set<() => void>();

const emit = () => listeners.forEach((listener) => listener());

function clearExpireTimer(): void {
  if (expireTimer !== null) {
    clearTimeout(expireTimer);
    expireTimer = null;
  }
}

function clearExpiredPending(): void {
  if (pending && Date.now() - pending.requestedAt >= BLOCK_FIELD_FOCUS_TTL_MS) {
    pending = null;
    clearExpireTimer();
  }
}

export function requestBlockFieldFocus(blockId: string, field: BlockField): void {
  clearExpireTimer();
  pending = { blockId, field, requestedAt: Date.now() };
  expireTimer = setTimeout(() => {
    expireTimer = null;
    if (pending && Date.now() - pending.requestedAt >= BLOCK_FIELD_FOCUS_TTL_MS) {
      pending = null;
      emit();
    }
  }, BLOCK_FIELD_FOCUS_TTL_MS);
  emit();
}

export function consumeBlockFieldRequest(): void {
  clearExpireTimer();
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

function subscribePending(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function getPending(): BlockFieldRequest | null {
  clearExpiredPending();
  return pending;
}

/** Yêu cầu đang chờ của nhóm `blockId` (hoặc `null`): đổi mỗi lần có yêu cầu mới, tự hết hạn nếu chưa kịp tiêu thụ. */
export function useBlockFieldRequest(blockId: string): BlockFieldRequest | null {
  const request = useSyncExternalStore(subscribePending, getPending, () => null);
  return request?.blockId === blockId ? request : null;
}
