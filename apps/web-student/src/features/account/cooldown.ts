import { useEffect, useState } from "react";

/** Từ ngưỡng này (giây) thời gian chờ là giới hạn theo ngày, không phải thời gian chờ giữa hai lần gửi. */
export const LONG_COOLDOWN_SECONDS = 3600;

/** Đếm ngược `mm:ss`, dùng cho thời gian chờ ngắn (dưới `LONG_COOLDOWN_SECONDS`). */
export function formatClock(seconds: number): string {
  const mm = Math.floor(seconds / 60);
  const ss = seconds % 60;
  return `${String(mm).padStart(2, "0")}:${String(ss).padStart(2, "0")}`;
}

export interface Cooldown {
  /** Số giây còn phải chờ, 0 khi gửi được. */
  secondsLeft: number;
  /** Tổng số giây của lần chờ gần nhất; `null` khi chưa chờ lần nào. */
  totalSeconds: number | null;
  start: (seconds: number) => void;
}

/** Thời gian chờ gửi lại: đồng hồ chỉ chạy khi đang chờ; `now` để test điều khiển giờ. */
export function useCooldown(now: () => number): Cooldown {
  const [state, setState] = useState<{ until: number; total: number } | null>(null);
  const [clock, setClock] = useState(now);

  useEffect(() => {
    if (!state) return;
    const timer = setInterval(() => setClock(now()), 500);
    return () => clearInterval(timer);
  }, [state, now]);

  function start(seconds: number) {
    const startedAt = now();
    setState({ until: startedAt + seconds * 1000, total: seconds });
    setClock(startedAt);
  }

  return {
    secondsLeft: state ? Math.max(0, Math.ceil((state.until - clock) / 1000)) : 0,
    totalSeconds: state?.total ?? null,
    start,
  };
}

/**
 * Câu cho vùng thông báo `role="status"`: một câu khi bắt đầu chờ và một câu khi hết chờ, không đọc từng giây.
 * Giới hạn theo ngày không có câu bắt đầu (cảnh báo hiện sẵn đã nói thời điểm thử lại).
 */
export function cooldownAnnouncement(
  cooldown: Cooldown,
  messages: { start: (time: string) => string; end: string },
): string {
  if (cooldown.totalSeconds === null) return "";
  if (cooldown.secondsLeft === 0) return messages.end;
  if (cooldown.totalSeconds >= LONG_COOLDOWN_SECONDS) return "";
  return messages.start(formatClock(cooldown.totalSeconds));
}
