import { buildLoginUrl, getEnvUrls, resolveAuthenticatedLanding } from "@thi360/web-kit/redirect";
import { continueWithSession, resolveNavigationSession, withSessionCookies } from "@thi360/web-kit/session";
import { type NextRequest, NextResponse } from "next/server";

// Đường công khai của cả đợt 3 (TM 11.1, plan B8): khớp đúng từng đường dẫn...
const PUBLIC_PATHS: Record<string, true> = {
  "/": true,
  "/forgot-password": true,
  "/reset-password": true,
  "/checkout/result": true,
  "/mobile/appearance-tokens.json": true,
};

// ...và theo tiền tố có dấu gạch chéo cuối: `/exams/<code>` công khai, còn `/exams`, `/examsx` cần phiên.
const PUBLIC_PREFIXES = ["/exams/", "/.well-known/"] as const;

export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const urls = getEnvUrls();

  if (Object.hasOwn(PUBLIC_PATHS, pathname) || PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
    return NextResponse.next();
  }

  // Hết hạn `thi360_ctx` mà còn refresh token thì phiên được làm mới tại đây, không về trang đăng nhập.
  const session = await resolveNavigationSession(request);
  const claims = session.claims;

  if (pathname === "/login") {
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

  if (pathname === "/register") {
    // Công khai cho khách; đã có phiên thì không đăng ký nữa mà về trang chủ theo quyền (AC-9).
    if (claims) {
      return withSessionCookies(NextResponse.redirect(resolveAuthenticatedLanding(claims.access, null, urls)), session);
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
