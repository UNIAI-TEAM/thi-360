import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const nextConfig: NextConfig = {
  transpilePackages: ["@thi360/ui", "@thi360/web-kit", "@thi360/api-client"],
};

export default withNextIntl(nextConfig);
