import type { Metadata } from "next";
import { getLocale, getTranslations } from "next-intl/server";
import { PublicShell } from "@/components/public-shell";
import { LegalDocumentPage } from "@/features/legal/legal-document-page";
import { getLegalDocument } from "@/features/legal/legal-documents";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("legal.privacy");
  return { title: t("title") };
}

export default async function PrivacyPolicyPage() {
  const [locale, t] = await Promise.all([getLocale(), getTranslations("legal.privacy")]);
  const doc = getLegalDocument("privacy", locale);
  return (
    <PublicShell>
      <LegalDocumentPage doc={doc} breadcrumbTitle={t("title")} />
    </PublicShell>
  );
}
