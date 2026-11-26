import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { PublicShell } from "@/components/public-shell";
import { PrivacyPolicyView } from "@/features/legal/privacy-policy-view";
import { resolveSiteBase } from "@/lib/site-url";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("legal.privacy");
  return { title: t("title") };
}

export default async function PrivacyPolicyPage() {
  const site = await resolveSiteBase();
  return (
    <PublicShell>
      <PrivacyPolicyView site={site} />
    </PublicShell>
  );
}
