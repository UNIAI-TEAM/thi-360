import { CTX_COOKIE_NAME, verifyCtxCookie } from "@thi360/web-kit/ctx";
import { buildLoginUrl, getEnvUrls } from "@thi360/web-kit/redirect";
import { type NextRequest, NextResponse } from "next/server";

export async function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const urls = getEnvUrls();
  const claims = await verifyCtxCookie(request.cookies.get(CTX_COOKIE_NAME)?.value);

  if (!claims) {
    return NextResponse.redirect(buildLoginUrl(urls.webUrl, request.nextUrl.href));
  }

  if (pathname !== "/forbidden" && claims.access !== "MANAGE") {
    return NextResponse.redirect(new URL("/forbidden", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api/|_next/static|_next/image|favicon.ico|.*\\..*).*)"],
};
