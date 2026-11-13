import { type NextRequest, NextResponse } from "next/server";
import vi from "@/messages/vi.json";

/**
 * Proxy cùng nguồn tới backend: trình duyệt gọi `/api/...`, route này chuyển tiếp tới
 * `${THI360_API_URL}/api/...` kèm cookie, XSRF, Origin, X-Request-Id và trả lại Set-Cookie.
 * Trình duyệt không bao giờ giữ token (RULES mục 5); việc kiểm tra phiên, XSRF, Origin do backend làm.
 *
 * Port theo luồng của reap `fe-elearning/src/app/api/[...path]/route.ts`; không mang sang
 * làm mới token, SSE, tên miền theo tenant, đổi miền cookie.
 */
export const dynamic = "force-dynamic";

type RouteContext = { params: Promise<{ path: string[] }> };

const DEV_API_URL = "http://localhost:8080";
/** Chờ tối đa cho tới khi backend trả header; thân phản hồi (tải file) không bị giới hạn. */
const UPSTREAM_HEADERS_TIMEOUT_MS = 60_000;
const REQUEST_ID_PATTERN = /^[A-Za-z0-9._-]{1,64}$/;

const FORWARDED_REQUEST_HEADERS = [
  "accept",
  "accept-language",
  "content-type",
  "cookie",
  "origin",
  "x-forwarded-for",
  "x-xsrf-token",
] as const;

/** Header của kết nối hoặc đã bị `fetch` giải nén/đặt lại, không được chép sang phản hồi. */
const DROPPED_RESPONSE_HEADERS: Record<string, true> = {
  connection: true,
  "content-encoding": true,
  "content-length": true,
  "keep-alive": true,
  "set-cookie": true,
  "transfer-encoding": true,
};

function resolveApiBase(): string | null {
  const configured = process.env.THI360_API_URL?.trim();
  if (configured) return configured.replace(/\/+$/, "");
  // Ở môi trường thật thiếu cấu hình là lỗi, không được âm thầm trỏ về localhost.
  return process.env.NODE_ENV === "production" ? null : DEV_API_URL;
}

/** Chặn `.`/`..` và dấu phân tách: `new URL` sẽ chuẩn hóa chúng và thoát khỏi tiền tố `/api/`. */
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

async function proxy(request: NextRequest, { params }: RouteContext): Promise<NextResponse> {
  const requestId = resolveRequestId(request);
  const base = resolveApiBase();
  if (!base) {
    return errorResponse(500, "INTERNAL_ERROR", vi.errors.proxyNotConfigured, requestId);
  }

  const { path } = await params;
  if (!path.every(isSafeSegment)) {
    return errorResponse(404, "NOT_FOUND", vi.errors.notFound, requestId);
  }
  const target = `${base}/api/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`;

  const headers = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  headers.set("x-request-id", requestId);

  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  const timeout = new AbortController();
  const timer = setTimeout(() => timeout.abort(), UPSTREAM_HEADERS_TIMEOUT_MS);

  let upstream: Response;
  try {
    upstream = await fetch(target, {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
      // Chuyển tiếp 3xx cho trình duyệt thay vì tự đi theo trên máy chủ.
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.any([request.signal, timeout.signal]),
      // Node yêu cầu `duplex` khi gửi thân dạng stream; chưa có trong kiểu RequestInit của DOM.
      ...(hasBody ? { duplex: "half" } : {}),
    } as RequestInit);
  } catch {
    if (request.signal.aborted) return new NextResponse(null, { status: 499 });
    if (timeout.signal.aborted) {
      return errorResponse(504, "UPSTREAM_TIMEOUT", vi.errors.upstreamTimeout, requestId);
    }
    return errorResponse(502, "UPSTREAM_UNAVAILABLE", vi.errors.upstreamUnavailable, requestId);
  } finally {
    clearTimeout(timer);
  }

  const responseHeaders = new Headers();
  upstream.headers.forEach((value, name) => {
    if (!Object.hasOwn(DROPPED_RESPONSE_HEADERS, name)) responseHeaders.set(name, value);
  });
  // Mỗi Set-Cookie là một header riêng; gộp bằng dấu phẩy sẽ làm hỏng thuộc tính Expires.
  for (const cookie of upstream.headers.getSetCookie()) {
    responseHeaders.append("set-cookie", cookie);
  }
  if (!responseHeaders.has("x-request-id")) responseHeaders.set("x-request-id", requestId);

  return new NextResponse(upstream.body, { status: upstream.status, headers: responseHeaders });
}

export async function GET(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function POST(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function PUT(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function PATCH(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}

export async function DELETE(request: NextRequest, context: RouteContext) {
  return proxy(request, context);
}
