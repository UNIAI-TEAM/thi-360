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

export interface RouteGuardOptions {
  forbiddenPath?: string;
  ignoredPaths?: readonly string[];
}

export function createRouteGuard(initialPathname: string, options: RouteGuardOptions = {}) {
  const forbiddenPath = options.forbiddenPath ?? "/forbidden";
  const ignoredPaths = new Set(options.ignoredPaths ?? []);
  let pathname = initialPathname;
  let forbiddenHandled = false;

  return {
    arrive(next: string) {
      pathname = next;
      forbiddenHandled = false;
    },
    pathname(): string {
      return pathname;
    },
    claimForbidden(): boolean {
      if (forbiddenHandled || pathname === forbiddenPath || ignoredPaths.has(pathname)) {
        return false;
      }
      forbiddenHandled = true;
      return true;
    },
  };
}

export function navigateOrReload(url: string): void {
  if (typeof window !== "undefined" && url === window.location.href) {
    window.location.reload();
    return;
  }
  window.location.assign(url);
}

const UNAUTHORIZED_RETRY_KEY = "thi360:401-refresh-url";
const UNAUTHORIZED_LOOP_WINDOW_MS = 10_000;

interface UnauthorizedRetryMarker {
  url: string;
  at: number;
}

function readUnauthorizedMarker(): UnauthorizedRetryMarker | null {
  try {
    const raw = window.sessionStorage.getItem(UNAUTHORIZED_RETRY_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<UnauthorizedRetryMarker>;
    if (typeof parsed.url === "string" && typeof parsed.at === "number") {
      return { url: parsed.url, at: parsed.at };
    }
    return null;
  } catch {
    return null;
  }
}

function writeUnauthorizedMarker(url: string | null): void {
  try {
    if (url === null) {
      window.sessionStorage.removeItem(UNAUTHORIZED_RETRY_KEY);
    } else {
      window.sessionStorage.setItem(UNAUTHORIZED_RETRY_KEY, JSON.stringify({ url, at: Date.now() }));
    }
  } catch {
    // ignore storage failures
  }
}

export function clearStaleUnauthorizedMarker(currentHref: string): void {
  const marker = readUnauthorizedMarker();
  if (marker && marker.url !== currentHref) {
    writeUnauthorizedMarker(null);
  }
}

export interface UnauthorizedRecoveryOptions {
  refreshSession: () => Promise<void>;
  clearSessionBestEffort: () => Promise<void>;
  getErrorStatus: (error: unknown) => number | null;
  onReload: (url: string) => void;
  onRedirectLogin: (currentHref: string) => void;
}

export function createUnauthorizedRecovery(options: UnauthorizedRecoveryOptions) {
  let refreshPromise: Promise<void> | null = null;
  let settled = false;

  async function handleUnauthorized(): Promise<void> {
    if (settled) return;

    const currentHref = typeof window !== "undefined" ? window.location.href : "";
    const marker = readUnauthorizedMarker();
    if (marker && marker.url === currentHref && Date.now() - marker.at < UNAUTHORIZED_LOOP_WINDOW_MS) {
      settled = true;
      writeUnauthorizedMarker(null);
      await options.clearSessionBestEffort();
      options.onRedirectLogin(currentHref);
      return;
    }

    if (!refreshPromise) {
      refreshPromise = options.refreshSession();
    }

    try {
      await refreshPromise;
      if (settled) return;
      settled = true;
      writeUnauthorizedMarker(currentHref);
      options.onReload(currentHref);
    } catch (error) {
      if (settled) return;
      settled = true;
      writeUnauthorizedMarker(null);
      if (options.getErrorStatus(error) !== 401) {
        await options.clearSessionBestEffort();
      }
      options.onRedirectLogin(currentHref);
    } finally {
      refreshPromise = null;
    }
  }

  return {
    handleUnauthorized,
    markSettled() {
      settled = true;
    },
  };
}
