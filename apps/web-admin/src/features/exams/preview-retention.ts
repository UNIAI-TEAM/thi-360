import { useSyncExternalStore } from "react";

/** Nhịp cập nhật đếm ngược: số phút còn lại chỉ đổi mỗi phút nên không cần cập nhật từng giây. */
const TICK_MS = 10_000;

/** Số phút còn giữ đề thử, làm tròn lên; `null` khi máy chủ không gửi hạn (sinh thử lỗi). */
export function previewMinutesLeft(expiresAt: string | null, now: number): number | null {
  if (!expiresAt) return null;
  const end = Date.parse(expiresAt);
  if (Number.isNaN(end)) return null;
  return Math.max(0, Math.ceil((end - now) / 60_000));
}

/** Thời điểm hiện tại làm tròn xuống nhịp cập nhật: giá trị đọc không đổi giữa hai nhịp nên React không vẽ lại thừa. */
function currentTick(): number {
  return Math.floor(Date.now() / TICK_MS) * TICK_MS;
}

function subscribeToTicks(onTick: () => void): () => void {
  const id = window.setInterval(onTick, TICK_MS);
  return () => window.clearInterval(id);
}

/**
 * Đếm ngược thời gian giữ đề thử (FU-FE4-PREVIEW): hạn `expiresAt` do máy chủ gửi, không tự cộng 30 phút ở trình duyệt.
 * Số phút dừng ở 0 khi quá hạn.
 */
export function usePreviewMinutesLeft(expiresAt: string | null): number | null {
  const now = useSyncExternalStore(subscribeToTicks, currentTick, () => 0);
  return previewMinutesLeft(expiresAt, now);
}
