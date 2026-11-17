"use client";

import { normalizeUiLocale, type AppLocale } from "@thi360/web-kit/preferences";
import { useLocale } from "next-intl";

/** Ngôn ngữ giao diện (`vi` | `en`); `useLocale()` của next-intl trả `en-GB` cho tiếng Anh. */
export function useUiLocale(): AppLocale {
  return normalizeUiLocale(useLocale());
}
