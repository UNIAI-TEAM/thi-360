import { APP_TIME_ZONE, normalizeUiLocale } from "@thi360/web-kit/preferences";

const VN_OFFSET_MS = 7 * 60 * 60 * 1000;

export function parseDigits(raw: string): number | null {
  const digits = raw.replace(/\D+/g, "");
  if (!digits) return null;
  const parsed = Number(digits);
  if (!Number.isSafeInteger(parsed)) return null;
  return parsed;
}

export function formatNumber(value: number, locale = "vi"): string {
  const isEn = normalizeUiLocale(locale) === "en";
  return new Intl.NumberFormat(isEn ? "en-GB" : "vi-VN").format(value);
}

export function formatVnd(amount: number | null | undefined, locale = "vi"): string {
  const isEn = normalizeUiLocale(locale) === "en";
  if (amount == null || !Number.isFinite(amount)) return isEn ? "0 VND" : "0 \u0111";
  const bcp47 = isEn ? "en-GB" : "vi-VN";
  const suffix = isEn ? " VND" : " \u0111";
  return `${new Intl.NumberFormat(bcp47).format(Math.trunc(amount))}${suffix}`;
}

export function formatOrderDateTime(iso: string | null | undefined, locale = "vi"): string {
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

export function formatOrderDate(dateStr: string | null | undefined, locale = "vi"): string {
  if (!dateStr) return "";
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateStr.trim());
  if (match) {
    const year = Number(match[1]);
    const month = Number(match[2]);
    const day = Number(match[3]);
    const ms = Date.UTC(year, month - 1, day, 12, 0, 0) - VN_OFFSET_MS;
    const isEn = normalizeUiLocale(locale) === "en";
    return new Intl.DateTimeFormat(isEn ? "en-GB" : "vi-VN", {
      timeZone: APP_TIME_ZONE,
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    }).format(new Date(ms));
  }
  const ms = Date.parse(dateStr);
  if (Number.isNaN(ms)) return dateStr;
  const isEn = normalizeUiLocale(locale) === "en";
  return new Intl.DateTimeFormat(isEn ? "en-GB" : "vi-VN", {
    timeZone: APP_TIME_ZONE,
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(ms));
}

export function formatVnpayPayDate(raw: string | null | undefined, locale = "vi"): string {
  if (!raw) return "";
  const trimmed = raw.trim();
  const compact = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})$/.exec(trimmed);
  if (compact) {
    const [, yyyy, mm, dd, hh, mi, ss] = compact;
    const iso = `${yyyy}-${mm}-${dd}T${hh}:${mi}:${ss}+07:00`;
    return formatOrderDateTime(iso, locale);
  }
  return formatOrderDateTime(trimmed, locale) || trimmed;
}

function parseVnCalendarDate(dateStr: string): { year: number; month: number; day: number } | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateStr.trim());
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const check = new Date(Date.UTC(year, month - 1, day));
  if (
    Number.isNaN(check.getTime()) ||
    check.getUTCFullYear() !== year ||
    check.getUTCMonth() !== month - 1 ||
    check.getUTCDate() !== day
  ) {
    return null;
  }
  return { year, month, day };
}

/** Convert local VN calendar date (YYYY-MM-DD) to start of day (00:00:00.000+07:00) in UTC ISO-8601. */
export function vnDateStartToIso(dateStr: string): string | undefined {
  const parsed = parseVnCalendarDate(dateStr);
  if (!parsed) return undefined;
  const utcMs = Date.UTC(parsed.year, parsed.month - 1, parsed.day, 0, 0, 0, 0) - VN_OFFSET_MS;
  return new Date(utcMs).toISOString();
}

/** Convert local VN calendar date (YYYY-MM-DD) to end of day (23:59:59.999+07:00) in UTC ISO-8601. */
export function vnDateEndToIso(dateStr: string): string | undefined {
  const parsed = parseVnCalendarDate(dateStr);
  if (!parsed) return undefined;
  const utcMs = Date.UTC(parsed.year, parsed.month - 1, parsed.day, 23, 59, 59, 999) - VN_OFFSET_MS;
  return new Date(utcMs).toISOString();
}

export function formatAttemptSeq(seq: number): string {
  return String(seq).padStart(2, "0");
}
