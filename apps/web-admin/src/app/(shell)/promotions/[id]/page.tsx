import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { PromotionDetailView } from "@/features/promotions/components/promotion-detail-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("promotions.detail");
  return { title: `${t("title")} · Thi 360` };
}

export default async function PromotionDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <PromotionDetailView promotionId={id} />;
}
