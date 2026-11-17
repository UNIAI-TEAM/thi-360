import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { ForbiddenView } from "@/features/session/forbidden-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("forbidden");
  const metadata = await getTranslations("metadata");
  return { title: metadata("titleTemplate", { title: t("title") }) };
}

export default async function ForbiddenPage({
  searchParams,
}: {
  searchParams: Promise<{ reason?: string | string[] }>;
}) {
  const { reason } = await searchParams;
  return <ForbiddenView reason={reason === "origin" ? "origin" : undefined} />;
}
