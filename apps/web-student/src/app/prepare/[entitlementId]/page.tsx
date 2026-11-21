import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { PreparePageClient } from "@/features/attempt-prepare";

type Props = { params: Promise<{ entitlementId: string }> };

function decodeEntitlementId(raw: string): string {
  try {
    return decodeURIComponent(raw);
  } catch {
    return raw;
  }
}

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("prepare");
  return { title: t("pageTitle") };
}

export default async function PreparePage({ params }: Props) {
  const { entitlementId } = await params;
  return <PreparePageClient entitlementId={decodeEntitlementId(entitlementId)} />;
}
