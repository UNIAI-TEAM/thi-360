import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { PublicShell } from "@/components/public-shell";
import { TermsOfServiceView } from "@/features/legal/terms-of-service-view";
import { resolveSiteBase } from "@/lib/site-url";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("legal.terms");
  return { title: t("title") };
}

export default async function TermsOfServicePage() {
  const site = await resolveSiteBase();
  return (
    <PublicShell>
      <TermsOfServiceView site={site} />
    </PublicShell>
  );
}
