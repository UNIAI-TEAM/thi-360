import type { Middleware } from "openapi-fetch";

export const XSRF_COOKIE_NAME = "thi360_xsrf";
export const XSRF_HEADER_NAME = "X-XSRF-TOKEN";

const SAFE_METHODS: Record<string, true> = {
  GET: true,
  HEAD: true,
  OPTIONS: true,
  TRACE: true,
};

export function readCookieValue(cookieHeader: string, name: string): string | null {
  if (!cookieHeader) return null;
  const prefix = `${name}=`;
  for (const part of cookieHeader.split(";")) {
    const trimmed = part.trim();
    if (trimmed.startsWith(prefix)) {
      const raw = trimmed.slice(prefix.length);
      try {
        return decodeURIComponent(raw);
      } catch {
        return raw;
      }
    }
  }
  return null;
}
export const xsrfMiddleware: Middleware = {
  onRequest({ request }) {
    if (Object.hasOwn(SAFE_METHODS, request.method.toUpperCase())) return request;
    if (request.headers.has(XSRF_HEADER_NAME)) return request;
    if (typeof document === "undefined") return request;

    const token = readCookieValue(document.cookie, XSRF_COOKIE_NAME);
    if (!token) return request;

    const headers = new Headers(request.headers);
    headers.set(XSRF_HEADER_NAME, token);
    return new Request(request, { headers });
  },
};
