import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { PromotionEditPage } from "@/features/promotions/components/promotion-form-page";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("promotions.edit");
  return { title: `${t("title")} · Thi360` };
}

export default async function EditPromotionPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <PromotionEditPage promotionId={id} />;
}
