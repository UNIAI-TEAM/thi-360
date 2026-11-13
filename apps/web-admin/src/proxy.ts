import { CTX_COOKIE_NAME, verifyCtxCookie } from "@thi360/web-kit/ctx";
import { buildLoginUrl, getEnvUrls } from "@thi360/web-kit/redirect";
import { type NextRequest, NextResponse } from "next/server";

export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const urls = getEnvUrls();
  const claims = await verifyCtxCookie(request.cookies.get(CTX_COOKIE_NAME)?.value);

  if (!claims) {
    const publicUrl = `${urls.adminUrl}${pathname}${search}`;
    return NextResponse.redirect(buildLoginUrl(urls.webUrl, publicUrl));
  }

  if (pathname !== "/forbidden" && claims.access !== "MANAGE") {
    return NextResponse.redirect(new URL("/forbidden", `${urls.adminUrl}/`));
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/((?!api/|_next/static|_next/image|favicon.ico|.*\\.(?:ico|png|jpg|jpeg|gif|svg|webp|css|js|map|txt|xml|woff2?)$).*)",
  ],
};
