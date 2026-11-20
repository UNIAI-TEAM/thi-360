import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const nextConfig: NextConfig = {
  transpilePackages: ["@thi360/ui", "@thi360/web-kit", "@thi360/api-client"],
  async headers() {
    return [
      {
        // Liên kết đặt lại mật khẩu mang `token` trên URL: không để lộ qua header Referer (TM 5.4).
        source: "/reset-password",
        headers: [{ key: "Referrer-Policy", value: "no-referrer" }],
      },
    ];
  },
};

export default withNextIntl(nextConfig);
