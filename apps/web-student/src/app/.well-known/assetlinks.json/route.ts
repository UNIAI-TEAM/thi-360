import { buildAndroidAssetLinks } from "@/features/mobile/deep-links";

export const dynamic = "force-dynamic";

export function GET(): Response {
  return new Response(JSON.stringify(buildAndroidAssetLinks()), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-cache",
    },
  });
}
