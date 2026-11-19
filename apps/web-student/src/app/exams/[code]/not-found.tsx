import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { PublicShell } from "@/components/public-shell";
import { ExamNotFoundView } from "@/features/storefront/exam-status-views";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("storefront.notFound");
  return { title: t("title") };
}

export default function ExamNotFound() {
  return (
    <PublicShell>
      <ExamNotFoundView />
    </PublicShell>
  );
}
