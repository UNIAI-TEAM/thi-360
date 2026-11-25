import { buildAppleAppSiteAssociation } from "@/features/mobile/deep-links";

export const dynamic = "force-dynamic";

export function GET(): Response {
  return new Response(JSON.stringify(buildAppleAppSiteAssociation()), {
    status: 200,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-cache",
    },
  });
}
