import { isIP } from "node:net";
import { type NextRequest, NextResponse } from "next/server";
import { XSRF_COOKIE_NAME, XSRF_HEADER_NAME, readCookieValue } from "./client";
import { type AppLocale, LOCALE_COOKIE_NAME, parseLocaleCookie } from "./preferences";
import {
  ACCESS_TOKEN_COOKIE_NAME,
  REFRESH_TOKEN_COOKIE_NAME,
  type ServerRefreshResult,
  refreshSessionOnServer,
} from "./session-refresh";

const DEV_API_URL = "http://localhost:8080";
const UPSTREAM_HEADERS_TIMEOUT_MS = 60_000;
const REQUEST_ID_PATTERN = /^[A-Za-z0-9._-]{1,64}$/;

const FORWARDED_REQUEST_HEADERS = [
  "accept",
  "accept-language",
  "content-type",
  "cookie",
  "idempotency-key",
  "origin",
  "x-xsrf-token",
] as const;

const DROPPED_RESPONSE_HEADERS: Record<string, true> = {
  connection: true,
  "content-encoding": true,
  "content-length": true,
  "keep-alive": true,
  "proxy-authenticate": true,
  "proxy-authorization": true,
  "set-cookie": true,
  te: true,
  trailer: true,
  "transfer-encoding": true,
  upgrade: true,
};

export interface ProxyErrorMessages {
  notFound: string;
  proxyNotConfigured: string;
  upstreamTimeout: string;
  upstreamUnavailable: string;
}

export interface ProxyRouteContext {
  params: Promise<{ path: string[] }>;
}

export interface ProxyOptions {
  messages: ProxyErrorMessages;
  resolveClientIp?: (request: NextRequest) => string | null;
  messagesByLocale?: Record<AppLocale, ProxyErrorMessages>;
}

function normalizeIpLiteral(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const trimmed = raw.trim();
  if (!trimmed) return null;
  return isIP(trimmed) !== 0 ? trimmed : null;
}

/**
 * Lấy địa chỉ IP khách thực tế từ `x-real-ip` khi chạy sau reverse proxy tin cậy (như nginx
 * với `proxy_set_header X-Real-IP $remote_addr;`) và bật `THI360_TRUST_REAL_IP=1`.
 * Next.js 16 không cung cấp `request.ip`, và proxy không bao giờ chuyển tiếp `X-Forwarded-For`
 * do client gửi.
 */
export function resolveIncomingClientIp(request: NextRequest): string | null {
  if (process.env.THI360_TRUST_REAL_IP === "1") {
    return normalizeIpLiteral(request.headers.get("x-real-ip"));
  }
  return null;
}

function connectionNominated(headers: Headers): Set<string> {
  const nominated = new Set<string>();
  for (const token of (headers.get("connection") ?? "").split(",")) {
    const name = token.trim().toLowerCase();
    if (name) nominated.add(name);
  }
  return nominated;
}

/**
 * Các endpoint tự quản lý phiên: không làm mới phiên thay cho chúng. `auth/change-password` cần phiên
 * hợp lệ nên vẫn được làm mới như mọi endpoint khác.
 */
const SESSION_ENDPOINTS: Record<string, true> = {
  "v1/auth/login": true,
  "v1/auth/refresh": true,
  "v1/auth/logout": true,
};

export function resolveApiBase(): string | null {
  const configured = process.env.THI360_API_URL?.trim();
  if (configured) return configured.replace(/\/+$/, "");
  return process.env.NODE_ENV === "production" ? null : DEV_API_URL;
}

function isSafeSegment(segment: string): boolean {
  return (
    segment !== "" &&
    segment !== "." &&
    segment !== ".." &&
    !/[/\\\u0000-\u001f]/.test(segment)
  );
}

function resolveRequestId(request: NextRequest): string {
  const supplied = request.headers.get("x-request-id");
  return supplied && REQUEST_ID_PATTERN.test(supplied) ? supplied : crypto.randomUUID();
}

function errorResponse(status: number, code: string, message: string, traceId: string) {
  return NextResponse.json(
    { code, message, fieldErrors: [], traceId },
    { status, headers: { "x-request-id": traceId } },
  );
}

export async function proxyApiRequest(
  request: NextRequest,
  { params }: ProxyRouteContext,
  options: ProxyOptions,
): Promise<NextResponse> {
  const requestId = resolveRequestId(request);
  const cookieHeader = request.headers.get("cookie") ?? "";
  const rawLocale =
    request.cookies.get(LOCALE_COOKIE_NAME)?.value ?? readCookieValue(cookieHeader, LOCALE_COOKIE_NAME);
  const locale = parseLocaleCookie(rawLocale);
  const messages = options.messagesByLocale?.[locale] ?? options.messages;
  const base = resolveApiBase();
  if (!base) {
    return errorResponse(500, "INTERNAL_ERROR", messages.proxyNotConfigured, requestId);
  }

  const { path } = await params;
  if (!path.every(isSafeSegment)) {
    return errorResponse(404, "NOT_FOUND", messages.notFound, requestId);
  }
  const target = `${base}/api/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`;

  const headers = new Headers();
  const requestHopOnly = connectionNominated(request.headers);
  for (const name of FORWARDED_REQUEST_HEADERS) {
    if (name === "accept-language" || requestHopOnly.has(name)) continue;
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  headers.set("accept-language", locale);
  headers.set("x-request-id", requestId);

  const clientIp = normalizeIpLiteral(
    options.resolveClientIp ? options.resolveClientIp(request) : resolveIncomingClientIp(request),
  );
  if (clientIp) {
    headers.set("x-forwarded-for", clientIp);
  }
  const hasBody = request.method !== "GET" && request.method !== "HEAD";

  // Làm mới phiên ở phía máy chủ khi access token hết hạn mà refresh token còn hạn (spec: proxy "tự refresh").
  // Trình duyệt tự bỏ cookie `thi360_at` đúng lúc token hết hạn, nên thiếu cookie này mà còn `thi360_rt`
  // là dấu hiệu chắc chắn: làm mới trước rồi mới gửi, nhờ đó thân request chỉ phải đọc một lần.
  const canRefresh =
    !Object.hasOwn(SESSION_ENDPOINTS, path.join("/")) &&
    Boolean(readCookieValue(cookieHeader, REFRESH_TOKEN_COOKIE_NAME));
  const clientXsrfHeader = request.headers.get(XSRF_HEADER_NAME);
  const clientXsrfCookie = readCookieValue(cookieHeader, XSRF_COOKIE_NAME);
  let refresh: ServerRefreshResult | null = null;

  const runRefresh = async (): Promise<ServerRefreshResult> => {
    const result = await refreshSessionOnServer({
      apiBase: base,
      cookieHeader,
      locale,
      requestId,
      clientIp,
      origin: request.headers.get("origin"),
    });
    if (result.ok) {
      headers.set("cookie", result.cookieHeader);
      // Làm mới phiên đổi cả `thi360_xsrf`. Chỉ thay header khi trình duyệt đã gửi đúng cặp
      // header/cookie cũ, để kiểm tra double-submit của backend giữ nguyên ý nghĩa.
      const nextXsrf = readCookieValue(result.cookieHeader, XSRF_COOKIE_NAME);
      if (clientXsrfHeader && clientXsrfCookie && clientXsrfHeader === clientXsrfCookie && nextXsrf) {
        headers.set(XSRF_HEADER_NAME, nextXsrf);
      }
    }
    return result;
  };

  if (canRefresh && !readCookieValue(cookieHeader, ACCESS_TOKEN_COOKIE_NAME)) {
    refresh = await runRefresh();
  }

  const send = async (): Promise<Response | NextResponse> => {
    const timeout = new AbortController();
    const timer = setTimeout(() => timeout.abort(), UPSTREAM_HEADERS_TIMEOUT_MS);
    try {
      return await fetch(target, {
        method: request.method,
        headers,
        body: hasBody ? request.body : undefined,
        redirect: "manual",
        cache: "no-store",
        signal: AbortSignal.any([request.signal, timeout.signal]),
        ...(hasBody ? { duplex: "half" } : {}),
      } as RequestInit);
    } catch {
      if (request.signal.aborted) return new NextResponse(null, { status: 499 });
      if (timeout.signal.aborted) {
        return errorResponse(504, "UPSTREAM_TIMEOUT", messages.upstreamTimeout, requestId);
      }
      return errorResponse(502, "UPSTREAM_UNAVAILABLE", messages.upstreamUnavailable, requestId);
    } finally {
      clearTimeout(timer);
    }
  };

  // Phản hồi lỗi do proxy tự tạo (499/502/504) vẫn phải mang cookie của lượt làm mới đã thành công,
  // nếu không trình duyệt giữ token cũ dù backend đã cấp token mới.
  const withRefreshCookies = (response: NextResponse): NextResponse => {
    if (refresh) {
      for (const cookie of refresh.setCookies) {
        response.headers.append("set-cookie", cookie);
      }
      if (refresh.setCookies.length > 0) response.headers.set("cache-control", "private, no-store");
    }
    return response;
  };

  let upstream = await send();
  if (upstream instanceof NextResponse) return withRefreshCookies(upstream);

  // Cookie `thi360_at` còn nhưng backend đã từ chối: thử làm mới một lần rồi gửi lại. Chỉ áp dụng cho
  // request không có thân, vì thân đã được đẩy thẳng tới backend và không đọc lại được.
  if (upstream.status === 401 && canRefresh && !refresh && !hasBody) {
    refresh = await runRefresh();
    if (refresh.ok) {
      try {
        await upstream.body?.cancel();
      } catch {
        // bỏ phản hồi 401 cũ
      }
      upstream = await send();
      if (upstream instanceof NextResponse) return withRefreshCookies(upstream);
    }
  }

  const responseHopOnly = connectionNominated(upstream.headers);
  const responseHeaders = new Headers();
  upstream.headers.forEach((value, name) => {
    if (!Object.hasOwn(DROPPED_RESPONSE_HEADERS, name) && !responseHopOnly.has(name)) {
      responseHeaders.set(name, value);
    }
  });
  // Cookie của lượt làm mới đi trước; `Set-Cookie` của chính phản hồi (nếu có) đi sau và thắng.
  if (refresh && refresh.setCookies.length > 0) {
    for (const cookie of refresh.setCookies) {
      responseHeaders.append("set-cookie", cookie);
    }
    // Phản hồi mang cookie phiên của một người thì không bộ đệm dùng chung nào được giữ lại, kể cả khi
    // endpoint gốc cho phép cache công khai (ví dụ `GET /api/v1/appearance`).
    responseHeaders.set("cache-control", "private, no-store");
    responseHeaders.delete("etag");
  }
  if (!responseHopOnly.has("set-cookie")) {
    for (const cookie of upstream.headers.getSetCookie()) {
      responseHeaders.append("set-cookie", cookie);
    }
  }
  if (!responseHeaders.has("x-request-id")) responseHeaders.set("x-request-id", requestId);

  return new NextResponse(upstream.body, { status: upstream.status, headers: responseHeaders });
}

export function createProxyHandlers(options: ProxyOptions) {
  const handle = (request: NextRequest, context: ProxyRouteContext) =>
    proxyApiRequest(request, context, options);
  return {
    GET: handle,
    POST: handle,
    PUT: handle,
    PATCH: handle,
    DELETE: handle,
  };
}
