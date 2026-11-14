import type { CtxAccess } from "./ctx";

const DEFAULT_WEB_URL = "http://localhost:3000";
const DEFAULT_ADMIN_URL = "http://localhost:3001";
const DOUBLE_ENCODED_HEX = /%25[0-9a-fA-F]{2}/;

function stripTrailingSlashes(url: string): string {
  return url.replace(/\/+$/, "");
}

export function getEnvUrls(): { webUrl: string; adminUrl: string } {
  const webRaw = process.env.THI360_WEB_URL?.trim() || process.env.NEXT_PUBLIC_THI360_WEB_URL?.trim();
  const adminRaw = process.env.THI360_ADMIN_URL?.trim() || process.env.NEXT_PUBLIC_THI360_ADMIN_URL?.trim();
  return {
    webUrl: stripTrailingSlashes(webRaw || DEFAULT_WEB_URL),
    adminUrl: stripTrailingSlashes(adminRaw || DEFAULT_ADMIN_URL),
  };
}

export function resolveHomeUrl(
  access: CtxAccess,
  urls: { webUrl?: string; adminUrl?: string } = getEnvUrls(),
): string {
  const defaults = getEnvUrls();
  const webUrl = stripTrailingSlashes(urls.webUrl || defaults.webUrl);
  const adminUrl = stripTrailingSlashes(urls.adminUrl || defaults.adminUrl);
  return access === "MANAGE" ? adminUrl : `${webUrl}/home`;
}

export function buildLoginUrl(webBaseUrl: string, callbackUrl?: string): string {
  const base = `${stripTrailingSlashes(webBaseUrl)}/dang-nhap`;
  return callbackUrl ? `${base}?callbackUrl=${encodeURIComponent(callbackUrl)}` : base;
}

function normalizeOrigin(url: URL): string | null {
  const protocol = url.protocol.toLowerCase();
  if (protocol !== "http:" && protocol !== "https:") return null;
  if (!url.hostname) return null;
  return url.origin.toLowerCase();
}

export function validateCallbackUrl(
  raw: string | null | undefined,
  allowedBaseUrls?: readonly string[],
): string | null {
  if (!raw || raw.trim() !== raw || raw.length === 0) return null;
  if (raw.includes("\\") || /[\u0000-\u001f\u007f]/.test(raw)) return null;
  if (DOUBLE_ENCODED_HEX.test(raw)) return null;

  if (raw.startsWith("/")) {
    if (raw.startsWith("//") || raw.startsWith("/\\")) return null;
    try {
      const decodedPath = decodeURIComponent(raw.split(/[?#]/, 1)[0] ?? raw);
      if (decodedPath.startsWith("//") || decodedPath.includes("\\")) return null;
    } catch {
      return null;
    }
    return raw;
  }

  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    return null;
  }
  if (parsed.username !== "" || parsed.password !== "") return null;

  const origin = normalizeOrigin(parsed);
  if (!origin) return null;

  const defaults = getEnvUrls();
  const bases = allowedBaseUrls ?? [defaults.webUrl, defaults.adminUrl];
  const allowed = new Set<string>();
  for (const base of bases) {
    try {
      const normalized = normalizeOrigin(new URL(base));
      if (normalized) allowed.add(normalized);
    } catch {
      // ignore invalid config base
    }
  }

  return allowed.has(origin) ? raw : null;
}
