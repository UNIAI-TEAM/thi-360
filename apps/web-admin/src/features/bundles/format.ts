import { APP_TIME_ZONE, normalizeUiLocale } from "@thi360/web-kit/preferences";

import { roundForDisplay } from "@/features/exams/display-number";

import type { DecimalRange, IntRange } from "./types";

/** Keep only numeric digits for price/attempt inputs, stripping leading zeros. */
export function priceDigits(text: string): string {
  return text.replace(/\D/g, "").replace(/^0+(?=\d)/, "");
}

/** Parse a non-negative integer string, returning `null` if blank or unsafe. */
export function integerOf(digits: string): number | null {
  if (!/^\d+$/.test(digits)) return null;
  const value = Number(digits);
  return Number.isSafeInteger(value) ? value : null;
}

export function formatNumber(value: number, locale = "vi"): string {
  const isEn = normalizeUiLocale(locale) === "en";
  return new Intl.NumberFormat(isEn ? "en-GB" : "vi-VN").format(value);
}

export function formatDecimal(value: number, locale = "vi"): string {
  const isEn = normalizeUiLocale(locale) === "en";
  return new Intl.NumberFormat(isEn ? "en-GB" : "vi-VN", {
    maximumFractionDigits: 2,
  }).format(roundForDisplay(value));
}

export function formatVnd(amount: number | null | undefined, locale = "vi"): string {
  const isEn = normalizeUiLocale(locale) === "en";
  if (amount == null || !Number.isFinite(amount)) return isEn ? "0 VND" : "0 \u0111";
  const bcp47 = isEn ? "en-GB" : "vi-VN";
  const suffix = isEn ? " VND" : " \u0111";
  return `${new Intl.NumberFormat(bcp47).format(Math.trunc(amount))}${suffix}`;
}

export function formatIntRange(range: IntRange | null | undefined, locale = "vi"): string | null {
  if (!range) return null;
  if (range.min === range.max) return formatNumber(range.min, locale);
  return `${formatNumber(range.min, locale)}\u2013${formatNumber(range.max, locale)}`;
}

export function formatDecimalRange(range: DecimalRange | null | undefined, locale = "vi"): string | null {
  if (!range) return null;
  if (range.min === range.max) return formatDecimal(range.min, locale);
  return `${formatDecimal(range.min, locale)}\u2013${formatDecimal(range.max, locale)}`;
}

export function formatBundleDateTime(iso: string | null | undefined, locale = "vi"): string {
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
