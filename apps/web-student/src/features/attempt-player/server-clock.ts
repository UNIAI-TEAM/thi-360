export interface ServerClockAnchor {
  serverNowMs: number;
  perfNowMs: number;
}

export function getPerfNow(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }
  return Date.now();
}

export function createServerClockAnchor(
  serverNowIso: string,
  perfNow = getPerfNow(),
): ServerClockAnchor {
  const parsed = Date.parse(serverNowIso);
  return {
    serverNowMs: Number.isFinite(parsed) ? parsed : Date.now(),
    perfNowMs: perfNow,
  };
}

export function reanchorServerClock(
  current: ServerClockAnchor | null,
  serverNowIso: string | null | undefined,
  perfNow = getPerfNow(),
): ServerClockAnchor | null {
  if (!serverNowIso) return current;
  const parsed = Date.parse(serverNowIso);
  if (!Number.isFinite(parsed)) return current;
  return {
    serverNowMs: parsed,
    perfNowMs: perfNow,
  };
}

export function getServerNowMs(
  anchor: ServerClockAnchor,
  perfNow = getPerfNow(),
): number {
  return anchor.serverNowMs + Math.max(0, perfNow - anchor.perfNowMs);
}

export function getRemainingMs(
  deadlineIso: string | null | undefined,
  anchor: ServerClockAnchor | null,
  perfNow = getPerfNow(),
): number | null {
  if (!deadlineIso) return null;
  const deadlineMs = Date.parse(deadlineIso);
  if (!Number.isFinite(deadlineMs)) return null;
  const nowMs = anchor ? getServerNowMs(anchor, perfNow) : Date.now();
  return Math.max(0, deadlineMs - nowMs);
}

export interface ClockDurationParts {
  totalSeconds: number;
  hours: number;
  minutes: number;
  seconds: number;
}

export function getClockDurationParts(remainingMs: number): ClockDurationParts {
  const totalSeconds = Math.max(0, Math.ceil(remainingMs / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return { totalSeconds, hours, minutes, seconds };
}

export function formatClockDuration(remainingMs: number): string {
  const { hours, minutes, seconds } = getClockDurationParts(remainingMs);
  const mm = String(minutes).padStart(2, "0");
  const ss = String(seconds).padStart(2, "0");
  if (hours > 0) {
    return `${hours}:${mm}:${ss}`;
  }
  return `${mm}:${ss}`;
}
