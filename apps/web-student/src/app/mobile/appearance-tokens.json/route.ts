import type { NextRequest } from "next/server";
import {
  APPEARANCE_UNAVAILABLE_BODY,
  formatAppearanceEtag,
  loadMobileAppearanceTokens,
  matchesIfNoneMatch,
} from "@/features/mobile/appearance-tokens";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest): Promise<Response> {
  const tokens = await loadMobileAppearanceTokens();
  if (!tokens) {
    return new Response(JSON.stringify(APPEARANCE_UNAVAILABLE_BODY), {
      status: 503,
      headers: {
        "Content-Type": "application/json",
        "Cache-Control": "no-store",
      },
    });
  }

  const etag = formatAppearanceEtag(tokens.version);
  if (matchesIfNoneMatch(request.headers.get("if-none-match"), tokens.version)) {
    return new Response(null, {
      status: 304,
      headers: {
        ETag: etag,
        "Cache-Control": "no-cache",
      },
    });
  }

  return new Response(JSON.stringify(tokens), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      ETag: etag,
      "Cache-Control": "no-cache",
    },
  });
}
