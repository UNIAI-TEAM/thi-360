"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import type { UiLocale } from "../messages/ui-strings";
import type { ThemeMode } from "../theme/palettes";

const PREFERENCE_COOKIE_MAX_AGE = 60 * 60 * 24 * 365;
export const THEME_PREFERENCE_COOKIE = "thi360_theme";
export const LOCALE_PREFERENCE_COOKIE = "thi360_locale";

export function writeClientPreferenceCookie(
  name: string,
  value: string,
  cookieDomain?: string,
): void {
  if (typeof document === "undefined") return;
  const trimmedDomain = cookieDomain?.trim();
  const domainPart =
    trimmedDomain && !/[\s;\r\n]/.test(trimmedDomain) ? `; Domain=${trimmedDomain}` : "";
  const securePart =
    typeof window !== "undefined" && window.location?.protocol === "https:" ? "; Secure" : "";
  document.cookie = `${name}=${encodeURIComponent(value)}; Max-Age=${PREFERENCE_COOKIE_MAX_AGE}; Path=/; SameSite=Lax${domainPart}${securePart}`;
}

export interface UiPreferencesContextValue {
  theme: ThemeMode;
  locale: UiLocale;
  cookieDomain?: string;
  setTheme: (next: ThemeMode) => void;
  setLocale: (next: UiLocale) => void;
}

const UiPreferencesContext = createContext<UiPreferencesContextValue | null>(null);

export type UiPreferencesProviderProps = {
  initialTheme?: ThemeMode;
  locale?: UiLocale;
  cookieDomain?: string;
  onThemeChange?: (next: ThemeMode) => void;
  onLocaleChange?: (next: UiLocale) => void;
  children: ReactNode;
};

export function UiPreferencesProvider({
  initialTheme = "light",
  locale = "vi",
  cookieDomain,
  onThemeChange,
  onLocaleChange,
  children,
}: UiPreferencesProviderProps) {
  const [theme, setThemeState] = useState<ThemeMode>(initialTheme);

  useEffect(() => {
    setThemeState(initialTheme);
  }, [initialTheme]);

  useEffect(() => {
    if (typeof document === "undefined") return;
    const root = document.documentElement;
    const syncFromDom = () => {
      const domTheme = root.dataset.theme;
      if (domTheme === "light" || domTheme === "dark") {
        setThemeState(domTheme);
      }
    };
    const observer = new MutationObserver(syncFromDom);
    observer.observe(root, { attributes: true, attributeFilter: ["data-theme"] });
    return () => observer.disconnect();
  }, []);

  function setTheme(next: ThemeMode) {
    setThemeState(next);
    if (typeof document !== "undefined") {
      document.documentElement.dataset.theme = next;
    }
    if (onThemeChange) {
      onThemeChange(next);
    } else {
      writeClientPreferenceCookie(THEME_PREFERENCE_COOKIE, next, cookieDomain);
    }
  }

  function setLocale(next: UiLocale) {
    if (onLocaleChange) {
      onLocaleChange(next);
    } else {
      writeClientPreferenceCookie(LOCALE_PREFERENCE_COOKIE, next, cookieDomain);
    }
  }

  return (
    <UiPreferencesContext.Provider value={{ theme, locale, cookieDomain, setTheme, setLocale }}>
      {children}
    </UiPreferencesContext.Provider>
  );
}

export function useUiPreferences(): UiPreferencesContextValue | null {
  return useContext(UiPreferencesContext);
}
