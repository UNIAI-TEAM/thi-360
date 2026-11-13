import type { Metadata } from "next";
import { Be_Vietnam_Pro } from "next/font/google";
import type { ReactNode } from "react";
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

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="vi" className={beVietnamPro.variable}>
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
