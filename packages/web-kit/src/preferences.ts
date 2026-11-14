export const LOCALE_COOKIE_NAME = "thi360_locale";
export const THEME_COOKIE_NAME = "thi360_theme";
export const DEFAULT_LOCALE = "vi" as const;
export const PREFERENCE_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

export const SUPPORTED_LOCALES = ["vi", "en"] as const;
export type AppLocale = (typeof SUPPORTED_LOCALES)[number];

export const SUPPORTED_THEMES = ["light", "dark"] as const;
export type AppTheme = (typeof SUPPORTED_THEMES)[number];

export function parseLocaleCookie(raw: string | null | undefined): AppLocale {
  const normalized = raw?.trim();
  if (normalized === "en") return "en";
  return DEFAULT_LOCALE;
}
export const APP_TIME_ZONE = "Asia/Ho_Chi_Minh" as const;

export const INTL_FORMATS = {
  dateTime: {
    short: { day: "2-digit", month: "2-digit", year: "numeric" },
    date: { day: "2-digit", month: "2-digit", year: "numeric" },
    dateTime: {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    },
  },
} as const;

export function resolveIntlLocale(locale: AppLocale): "vi" | "en-GB" {
  return locale === "en" ? "en-GB" : "vi";
}

export function normalizeUiLocale(locale: string | null | undefined): AppLocale {
  const normalized = locale?.trim();
  if (normalized === "en" || normalized === "en-GB") return "en";
  return DEFAULT_LOCALE;
}

export function parseThemeCookie(raw: string | null | undefined): AppTheme | null {
  const normalized = raw?.trim();
  if (normalized === "light") return "light";
  if (normalized === "dark") return "dark";
  return null;
}

export function resolveTheme(appearanceMode: AppTheme, cookieValue?: string | null): AppTheme {
  return parseThemeCookie(cookieValue) ?? appearanceMode;
}

export function normalizeCookieDomain(domain: string | null | undefined): string | undefined {
  const trimmed = domain?.trim();
  if (!trimmed) return undefined;
  if (/[\s;\r\n]/.test(trimmed)) return undefined;
  return trimmed;
}

export interface PreferenceCookieOptions {
  domain?: string | null;
  secure?: boolean;
  maxAgeSeconds?: number;
}

export function buildPreferenceCookieString(
  name: string,
  value: string,
  options: PreferenceCookieOptions = {},
): string {
  const maxAge = options.maxAgeSeconds ?? PREFERENCE_COOKIE_MAX_AGE_SECONDS;
  const parts = [
    `${name}=${encodeURIComponent(value)}`,
    `Max-Age=${maxAge}`,
    "Path=/",
    "SameSite=Lax",
  ];
  const domain = normalizeCookieDomain(options.domain);
  if (domain) {
    parts.push(`Domain=${domain}`);
  }
  if (options.secure) {
    parts.push("Secure");
  }
  return parts.join("; ");
}

export function writePreferenceCookie(
  name: string,
  value: string,
  options: { domain?: string | null } = {},
): void {
  if (typeof document === "undefined") return;
  const secure = typeof window !== "undefined" && window.location?.protocol === "https:";
  document.cookie = buildPreferenceCookieString(name, value, {
    domain: options.domain,
    secure,
  });
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function deepMergeMessages<T extends Record<string, unknown>>(
  base: T,
  override: Record<string, unknown> | null | undefined,
): T {
  if (!override || !isPlainRecord(override)) {
    return { ...base };
  }
  const result: Record<string, unknown> = { ...base };
  for (const [key, overrideVal] of Object.entries(override)) {
    if (overrideVal === undefined || overrideVal === null) continue;
    const baseVal = result[key];
    if (isPlainRecord(baseVal)) {
      if (!isPlainRecord(overrideVal)) continue;
      result[key] = deepMergeMessages(baseVal, overrideVal);
      continue;
    }
    if (typeof baseVal === "string") {
      if (typeof overrideVal !== "string" || overrideVal.trim().length === 0) continue;
      result[key] = overrideVal;
      continue;
    }
    if (typeof overrideVal === "string" && overrideVal.trim().length === 0) continue;
    result[key] = overrideVal;
  }
  return result as T;
}
