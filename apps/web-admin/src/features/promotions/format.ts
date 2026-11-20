import { normalizeUiLocale } from "@thi360/web-kit/preferences";

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

export function formatPositiveInteger(value: string | number | null | undefined): string {
  if (value == null || value === "") return "";
  return String(value).replace(/\D+/g, "");
}

export function formatDiscountSummary(
  discountType: "PERCENT" | "AMOUNT",
  discountValue: number,
  maxDiscountVnd: number | null | undefined,
  locale: string,
  tDiscount: (key: string, values?: Record<string, string | number>) => string,
): string {
  if (discountType === "PERCENT") {
    if (maxDiscountVnd != null && maxDiscountVnd > 0) {
      return tDiscount("percentWithCap", {
        percent: discountValue,
        cap: formatVnd(maxDiscountVnd, locale),
      });
    }
    return tDiscount("percent", { percent: discountValue });
  }
  return tDiscount("amount", { amount: formatVnd(discountValue, locale) });
}
