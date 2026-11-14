import { buildLoginUrl, getEnvUrls, resolveAuthenticatedLanding } from "@thi360/web-kit/redirect";
import { continueWithSession, resolveNavigationSession, withSessionCookies } from "@thi360/web-kit/session";
import { type NextRequest, NextResponse } from "next/server";

const PUBLIC_PATHS: Record<string, true> = {
  "/": true,
};

export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const urls = getEnvUrls();

  if (Object.hasOwn(PUBLIC_PATHS, pathname)) {
    return NextResponse.next();
  }

  // Hết hạn `thi360_ctx` mà còn refresh token thì phiên được làm mới tại đây, không về trang đăng nhập.
  const session = await resolveNavigationSession(request);
  const claims = session.claims;

  if (pathname === "/dang-nhap") {
    if (claims) {
      // Đã có phiên: quay lại trang đang mở trước đó nếu `callbackUrl` hợp lệ, không thì về trang chủ.
      const landing = resolveAuthenticatedLanding(
        claims.access,
        request.nextUrl.searchParams.get("callbackUrl"),
        urls,
      );
      return withSessionCookies(NextResponse.redirect(landing), session);
    }
    return continueWithSession(request, session);
  }

  if (!claims) {
    const publicUrl = `${urls.webUrl}${pathname}${search}`;
    return withSessionCookies(NextResponse.redirect(buildLoginUrl(urls.webUrl, publicUrl)), session);
  }

  return continueWithSession(request, session);
}

export const config = {
  matcher: [
    "/((?!api/|_next/static|_next/image|favicon.ico|.*\\.(?:ico|png|jpg|jpeg|gif|svg|webp|css|js|map|txt|xml|woff2?)$).*)",
  ],
};
