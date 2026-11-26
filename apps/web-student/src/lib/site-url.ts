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
 * Resolves the canonical web base URL strictly from trusted environment configuration
 * (`THI360_WEB_URL` / `NEXT_PUBLIC_THI360_WEB_URL` / `webUrl()`), never from untrusted
 * client request headers such as `Host` or `X-Forwarded-Host`.
 */
export async function resolveSiteBase(): Promise<SiteBaseInfo> {
  const configured = process.env.THI360_WEB_URL?.trim() || process.env.NEXT_PUBLIC_THI360_WEB_URL?.trim();
  return formatSiteBase(configured || webUrl());
}
