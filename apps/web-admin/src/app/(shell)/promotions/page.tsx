import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { PromotionListView } from "@/features/promotions/components/promotion-list-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("promotions.list");
  return { title: `${t("title")} · Thi 360` };
}

export default function PromotionsPage() {
  return (
    <Suspense>
      <PromotionListView />
    </Suspense>
  );
}
