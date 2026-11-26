import { headers } from "next/headers";
import { webUrl } from "./env";

export interface SiteBaseInfo {
  /** Base origin with trailing slash, e.g. `https://thi360.vn/`. */
  siteUrl: string;
  /** Hostname (and port if non-standard), e.g. `thi360.vn`. */
  siteHost: string;
}

export function formatSiteBase(rawBase: string): SiteBaseInfo {
  try {
    const parsed = new URL(rawBase);
    const origin = parsed.origin.replace(/\/+$/, "");
    return {
      siteUrl: `${origin}/`,
      siteHost: parsed.host,
    };
  } catch {
    const trimmed = rawBase.trim().replace(/\/+$/, "");
    return {
      siteUrl: `${trimmed}/`,
      siteHost: trimmed.replace(/^https?:\/\//i, ""),
    };
  }
}

/**
 * Resolves the active web base URL from environment configuration or incoming request headers
 * so legal documents always reference the current web origin instead of a hardcoded domain.
 */
export async function resolveSiteBase(): Promise<SiteBaseInfo> {
  const configured = process.env.THI360_WEB_URL?.trim() || process.env.NEXT_PUBLIC_THI360_WEB_URL?.trim();
  if (configured) {
    return formatSiteBase(configured);
  }
  try {
    const headerList = await headers();
    const host = headerList.get("x-forwarded-host")?.split(",")[0]?.trim() || headerList.get("host")?.trim();
    if (host) {
      const proto = headerList.get("x-forwarded-proto")?.split(",")[0]?.trim() || (host.startsWith("localhost") ? "http" : "https");
      return formatSiteBase(`${proto}://${host}`);
    }
  } catch {
    // Static generation or unit test without active Next request scope.
  }
  return formatSiteBase(webUrl());
}
