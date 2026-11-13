import { CTX_COOKIE_NAME, verifyCtxCookie } from "@thi360/web-kit/ctx";
import { buildLoginUrl, getEnvUrls, resolveHomeUrl } from "@thi360/web-kit/redirect";
import { type NextRequest, NextResponse } from "next/server";

const PUBLIC_PATHS: Record<string, true> = {
  "/": true,
  "/dang-nhap": true,
};

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const urls = getEnvUrls();
  const claims = await verifyCtxCookie(request.cookies.get(CTX_COOKIE_NAME)?.value);

  if (pathname === "/dang-nhap") {
    if (claims) {
      return NextResponse.redirect(resolveHomeUrl(claims.access, urls));
    }
    return NextResponse.next();
  }

  if (Object.hasOwn(PUBLIC_PATHS, pathname)) {
    return NextResponse.next();
  }

  if (!claims) {
    return NextResponse.redirect(buildLoginUrl(request.nextUrl.origin, request.nextUrl.href));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api/|_next/static|_next/image|favicon.ico|.*\\..*).*)"],
};
