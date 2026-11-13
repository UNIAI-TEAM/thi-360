import { createApiClient } from "@thi360/api-client";
import {
  buildThemeVars,
  DEFAULT_APPEARANCE_CONFIG,
  parseAppearanceConfig,
  type AppearanceConfig,
  type ThemeMode,
} from "@thi360/ui";
import type { Metadata } from "next";
import { Be_Vietnam_Pro } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getTranslations } from "next-intl/server";
import type { CSSProperties, ReactNode } from "react";
import { SessionProvider } from "@/features/session/session-provider";
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

function resolveTheme(appearanceMode: ThemeMode): ThemeMode {
  return appearanceMode;
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const [locale, appearance] = await Promise.all([getLocale(), loadAppearance()]);
  const themeMode = resolveTheme(appearance.mode);
  const themeVars = buildThemeVars({ ...appearance, mode: themeMode }) as CSSProperties;
  return (
    <html
      lang={locale}
      className={beVietnamPro.variable}
      data-theme={themeMode}
      style={themeVars}
    >
      <body className="font-sans antialiased">
        <NextIntlClientProvider>
          <QueryProvider>
            <SessionProvider>{children}</SessionProvider>
          </QueryProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
