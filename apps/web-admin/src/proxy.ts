import { buildLoginUrl, getEnvUrls } from "@thi360/web-kit/redirect";
import { continueWithSession, resolveNavigationSession, withSessionCookies } from "@thi360/web-kit/session";
import { type NextRequest, NextResponse } from "next/server";

export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const urls = getEnvUrls();
  // Hết hạn `thi360_ctx` mà còn refresh token thì phiên được làm mới tại đây, không về trang đăng nhập.
  const session = await resolveNavigationSession(request);
  const claims = session.claims;

  if (!claims) {
    const publicUrl = `${urls.adminUrl}${pathname}${search}`;
    return withSessionCookies(NextResponse.redirect(buildLoginUrl(urls.webUrl, publicUrl)), session);
  }

  if (pathname !== "/forbidden" && claims.access !== "MANAGE") {
    return withSessionCookies(NextResponse.redirect(new URL("/forbidden", `${urls.adminUrl}/`)), session);
  }

  return continueWithSession(request, session);
}

export const config = {
  matcher: [
    "/((?!api/|_next/static|_next/image|favicon.ico|.*\\.(?:ico|png|jpg|jpeg|gif|svg|webp|css|js|map|txt|xml|woff2?)$).*)",
  ],
};
