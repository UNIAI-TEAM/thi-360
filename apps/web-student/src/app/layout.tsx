import {
  buildThemeVars,
  DEFAULT_APPEARANCE_CONFIG,
  parseAppearanceConfig,
  type AppearanceConfig,
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
    const response = await fetch(`${apiBase}/api/v1/appearance`, {
      next: { revalidate: 60, tags: ["appearance"] },
      signal: AbortSignal.timeout(2000),
    });
    if (!response.ok) return DEFAULT_APPEARANCE_CONFIG;
    return parseAppearanceConfig(await response.json());
  } catch {
    return DEFAULT_APPEARANCE_CONFIG;
  }
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const appearance = await loadAppearance();
  const themeVars = buildThemeVars(appearance) as CSSProperties;
  return (
    <html
      lang="vi"
      className={beVietnamPro.variable}
      data-theme={appearance.mode}
      style={themeVars}
    >
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
