import { type NextRequest, NextResponse } from "next/server";
import { readCookieValue } from "./client";
import { CTX_COOKIE_NAME, type CtxClaims, verifyCtxCookie } from "./ctx";
import { LOCALE_COOKIE_NAME, parseLocaleCookie } from "./preferences";
import { resolveApiBase, resolveIncomingClientIp } from "./proxy";
import { REFRESH_TOKEN_COOKIE_NAME, refreshSessionOnServer } from "./session-refresh";

export interface NavigationSession {
  /** Thông tin điều hướng của phiên; `null` khi chưa đăng nhập hoặc không làm mới được. */
  claims: CtxClaims | null;
  /** `Set-Cookie` phải gửi kèm phản hồi (cookie mới, hoặc lệnh xóa khi backend từ chối phiên). */
  setCookies: string[];
  /** Header `Cookie` đã cập nhật cho phần xử lý phía sau; `null` khi không có gì đổi. */
  cookieHeader: string | null;
}

/**
 * Đọc phiên cho middleware trang. Cookie `thi360_ctx` chỉ sống 5 phút; khi nó hết hạn mà còn
 * `thi360_rt` thì làm mới phiên ở phía máy chủ thay vì coi như chưa đăng nhập.
 */
export async function resolveNavigationSession(request: NextRequest): Promise<NavigationSession> {
  const current = await verifyCtxCookie(request.cookies.get(CTX_COOKIE_NAME)?.value);
  if (current) {
    return { claims: current, setCookies: [], cookieHeader: null };
  }

  const cookieHeader = request.headers.get("cookie") ?? "";
  const apiBase = resolveApiBase();
  if (!apiBase || !readCookieValue(cookieHeader, REFRESH_TOKEN_COOKIE_NAME)) {
    return { claims: null, setCookies: [], cookieHeader: null };
  }

  const result = await refreshSessionOnServer({
    apiBase,
    cookieHeader,
    locale: parseLocaleCookie(readCookieValue(cookieHeader, LOCALE_COOKIE_NAME)),
    clientIp: resolveIncomingClientIp(request),
  });
  if (!result.ok) {
    return { claims: null, setCookies: result.setCookies, cookieHeader: null };
  }

  const claims = await verifyCtxCookie(readCookieValue(result.cookieHeader, CTX_COOKIE_NAME));
  return { claims, setCookies: result.setCookies, cookieHeader: result.cookieHeader };
}

/** Gắn `Set-Cookie` của lượt làm mới (nếu có) vào một phản hồi của middleware. */
export function withSessionCookies<T extends NextResponse>(response: T, session: NavigationSession): T {
  for (const cookie of session.setCookies) {
    response.headers.append("set-cookie", cookie);
  }
  return response;
}

/** Cho request đi tiếp; nếu phiên vừa được làm mới thì phần xử lý phía sau thấy cookie mới. */
export function continueWithSession(request: NextRequest, session: NavigationSession): NextResponse {
  if (session.cookieHeader === null) {
    return withSessionCookies(NextResponse.next(), session);
  }
  const headers = new Headers(request.headers);
  headers.set("cookie", session.cookieHeader);
  return withSessionCookies(NextResponse.next({ request: { headers } }), session);
}
