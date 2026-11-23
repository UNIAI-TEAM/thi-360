import { useCallback, useSyncExternalStore } from "react";

/** Nhịp cập nhật đếm ngược: số phút còn lại chỉ đổi mỗi phút nên không cần cập nhật từng giây. */
const TICK_MS = 10_000;

/** Số phút còn giữ đề thử, làm tròn lên; `null` khi máy chủ không gửi hạn (sinh thử lỗi). */
export function previewMinutesLeft(expiresAt: string | null, now: number): number | null {
  if (!expiresAt) return null;
  const end = Date.parse(expiresAt);
  if (Number.isNaN(end)) return null;
  return Math.max(0, Math.ceil((end - now) / 60_000));
}

export function usePreviewMinutesLeft(expiresAt: string | null, maxMinutes?: number): number | null {
  const end = expiresAt ? Date.parse(expiresAt) : Number.NaN;
  const subscribe = useCallback(
    (onTick: () => void) => {
      if (Number.isNaN(end) || Date.now() >= end) return () => {};
      const id = window.setInterval(() => {
        if (Date.now() >= end) window.clearInterval(id);
        onTick();
      }, TICK_MS);
      return () => window.clearInterval(id);
    },
    [end],
  );
  const getSnapshot = () => {
    const left = previewMinutesLeft(expiresAt, Date.now());
    return left !== null && maxMinutes !== undefined ? Math.min(maxMinutes, left) : left;
  };
  return useSyncExternalStore(subscribe, getSnapshot, () => null);
}
