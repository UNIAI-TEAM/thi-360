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
import type { CSSProperties, ReactNode } from "react";
import "./globals.css";

const beVietnamPro = Be_Vietnam_Pro({
  subsets: ["latin", "vietnamese"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-be-vietnam-pro",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Thi 360 — Luyện thi HSA, QDA",
  description: "Cổng luyện thi đánh giá năng lực HSA và QDA",
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

function resolveTheme(appearanceMode: ThemeMode): ThemeMode {
  return appearanceMode;
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const appearance = await loadAppearance();
  const themeMode = resolveTheme(appearance.mode);
  const themeVars = buildThemeVars({ ...appearance, mode: themeMode }) as CSSProperties;
  return (
    <html
      lang="vi"
      className={beVietnamPro.variable}
      data-theme={themeMode}
      style={themeVars}
    >
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
