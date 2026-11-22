import { APP_TIME_ZONE, normalizeUiLocale } from "@thi360/web-kit/preferences";

const VN_OFFSET_MS = 7 * 60 * 60 * 1000;

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

/** Convert an ISO-8601 instant to `<input type="datetime-local">` value in Asia/Ho_Chi_Minh (UTC+7). */
export function isoToVietnamDatetimeLocal(iso: string | null | undefined): string {
  if (!iso) return "";
  const ms = Date.parse(iso);
  if (Number.isNaN(ms)) return "";
  const shifted = new Date(ms + VN_OFFSET_MS);
  return `${shifted.getUTCFullYear()}-${pad2(shifted.getUTCMonth() + 1)}-${pad2(shifted.getUTCDate())}T${pad2(shifted.getUTCHours())}:${pad2(shifted.getUTCMinutes())}`;
}

/** Convert `<input type="datetime-local">` (interpreted as Asia/Ho_Chi_Minh UTC+7) to an ISO-8601 instant. */
export function vietnamDatetimeLocalToIso(localValue: string): string | null {
  const trimmed = localValue.trim();
  const match =
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/.exec(trimmed);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const second = match[6] ? Number(match[6]) : 0;
  const ms = match[7] ? Number(match[7].padEnd(3, "0")) : 0;
  const check = new Date(Date.UTC(year, month - 1, day, hour, minute, second, ms));
  if (
    Number.isNaN(check.getTime()) ||
    check.getUTCFullYear() !== year ||
    check.getUTCMonth() !== month - 1 ||
    check.getUTCDate() !== day ||
    check.getUTCHours() !== hour ||
    check.getUTCMinutes() !== minute ||
    check.getUTCSeconds() !== second ||
    check.getUTCMilliseconds() !== ms
  ) {
    return null;
  }
  const date = new Date(check.getTime() - VN_OFFSET_MS);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export function parseVietnamDatetimeLocalMs(localValue: string): number | null {
  const iso = vietnamDatetimeLocalToIso(localValue);
  if (!iso) return null;
  const ms = Date.parse(iso);
  return Number.isNaN(ms) ? null : ms;
}

/** Default initial schedule for creating a promotion: +1h to +7d in Vietnam local time. */
export function defaultPromotionSchedule(now: Date = new Date()): { startsAt: string; endsAt: string } {
  const startMs = now.getTime() + 60 * 60 * 1000;
  const endMs = now.getTime() + 7 * 24 * 60 * 60 * 1000;
  return {
    startsAt: isoToVietnamDatetimeLocal(new Date(startMs).toISOString()),
    endsAt: isoToVietnamDatetimeLocal(new Date(endMs).toISOString()),
  };
}

/** Format an ISO-8601 instant for admin display in Asia/Ho_Chi_Minh timezone. */
export function formatPromotionDateTime(iso: string | null | undefined, locale: string): string {
  if (!iso) return "";
  const ms = Date.parse(iso);
  if (Number.isNaN(ms)) return "";
  const isEn = normalizeUiLocale(locale) === "en";
  const bcp47 = isEn ? "en-GB" : "vi-VN";
  return new Intl.DateTimeFormat(bcp47, {
    timeZone: APP_TIME_ZONE,
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(ms));
}
