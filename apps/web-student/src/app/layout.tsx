import { createApiClient } from "@thi360/api-client";
import {
  DEFAULT_APPEARANCE_CONFIG,
  parseAppearanceConfig,
  THEME_STYLE_ELEMENT_ID,
  themeStyleTag,
  type AppearanceConfig,
} from "@thi360/ui";
import { normalizeCookieDomain, normalizeUiLocale, resolveTheme, THEME_COOKIE_NAME } from "@thi360/web-kit/preferences";
import type { Metadata } from "next";
import { Be_Vietnam_Pro } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getTranslations } from "next-intl/server";
import { cookies } from "next/headers";
import type { ReactNode } from "react";
import { PreferencesProvider } from "@/providers/preferences-provider";
import { QueryProvider } from "@/providers/query-provider";
import "./globals.css";

const beVietnamPro = Be_Vietnam_Pro({
  subsets: ["latin", "vietnamese"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-be-vietnam-pro",
  display: "swap",
});

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("metadata");
  return {
    title: t("title"),
    description: t("description"),
  };
}

async function loadAppearance(): Promise<AppearanceConfig> {
  const apiBase =
    process.env.THI360_API_URL?.trim().replace(/\/+$/, "") ||
    (process.env.NODE_ENV === "production" ? "" : "http://localhost:8080");
  if (!apiBase) return DEFAULT_APPEARANCE_CONFIG;
  try {
    const client = createApiClient(apiBase, {
      fetch: (request) =>
        fetch(request, {
          next: { revalidate: 60, tags: ["appearance"] },
          signal: AbortSignal.timeout(2000),
        }),
    });
    const { data, response } = await client.GET("/api/v1/appearance");
    if (!response.ok || !data) return DEFAULT_APPEARANCE_CONFIG;
    return parseAppearanceConfig(data);
  } catch {
    return DEFAULT_APPEARANCE_CONFIG;
  }
}

async function readThemeCookie(): Promise<string | undefined> {
  try {
    const store = await cookies();
    return store.get(THEME_COOKIE_NAME)?.value;
  } catch {
    return undefined;
  }
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const [locale, appearance, themeCookie] = await Promise.all([
    getLocale(),
    loadAppearance(),
    readThemeCookie(),
  ]);
  const themeMode = resolveTheme(appearance.mode, themeCookie);
  const themeCss = themeStyleTag(appearance);
  const uiLocale = normalizeUiLocale(locale);
  const cookieDomain = normalizeCookieDomain(process.env.THI360_COOKIE_DOMAIN);
  return (
    <html lang={uiLocale} className={beVietnamPro.variable} data-theme={themeMode}>
      <head>
        <style id={THEME_STYLE_ELEMENT_ID} dangerouslySetInnerHTML={{ __html: themeCss }} />
      </head>
      <body className="font-sans antialiased">
        <NextIntlClientProvider>
          <PreferencesProvider locale={uiLocale} initialTheme={themeMode} cookieDomain={cookieDomain}>
            <QueryProvider key={uiLocale}>{children}</QueryProvider>
          </PreferencesProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
