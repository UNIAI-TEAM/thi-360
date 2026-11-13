import { createApiClient } from "@thi360/api-client";
import {
  buildThemeVars,
  DEFAULT_APPEARANCE_CONFIG,
  parseAppearanceConfig,
  type AppearanceConfig,
} from "@thi360/ui";
import type { Metadata } from "next";
import { Be_Vietnam_Pro } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getLocale } from "next-intl/server";
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

export const metadata: Metadata = {
  title: "Thi 360 — Quản trị",
  description: "Trang quản trị cổng luyện thi Thi 360",
};

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

export default async function RootLayout({ children }: { children: ReactNode }) {
  const [locale, appearance] = await Promise.all([getLocale(), loadAppearance()]);
  const themeVars = buildThemeVars(appearance) as CSSProperties;
  return (
    <html
      lang={locale}
      className={beVietnamPro.variable}
      data-theme={appearance.mode}
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
