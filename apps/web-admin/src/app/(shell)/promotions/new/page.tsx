import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { PromotionCreatePage } from "@/features/promotions/components/promotion-form-page";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("promotions.create");
  return { title: `${t("title")} · Thi360` };
}

export default function NewPromotionPage() {
  return (
    <Suspense>
      <PromotionCreatePage />
    </Suspense>
  );
}
