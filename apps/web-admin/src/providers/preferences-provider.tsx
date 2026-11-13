"use client";

import {
  type ThemeMode,
  type UiLocale,
  UiPreferencesProvider,
  UiStringsProvider,
} from "@thi360/ui";
import {
  LOCALE_COOKIE_NAME,
  THEME_COOKIE_NAME,
  writePreferenceCookie,
} from "@thi360/web-kit/preferences";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";

export function PreferencesProvider({
  locale,
  initialTheme,
  cookieDomain,
  children,
}: {
  locale: UiLocale;
  initialTheme: ThemeMode;
  cookieDomain?: string;
  children: ReactNode;
}) {
  const router = useRouter();

  return (
    <UiStringsProvider locale={locale}>
      <UiPreferencesProvider
        locale={locale}
        initialTheme={initialTheme}
        cookieDomain={cookieDomain}
        onThemeChange={(next) => {
          writePreferenceCookie(THEME_COOKIE_NAME, next, { domain: cookieDomain });
        }}
        onLocaleChange={(next) => {
          writePreferenceCookie(LOCALE_COOKIE_NAME, next, { domain: cookieDomain });
          router.refresh();
        }}
      >
        {children}
      </UiPreferencesProvider>
    </UiStringsProvider>
  );
}
