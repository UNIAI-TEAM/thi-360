import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { CatalogPageSkeleton, CatalogView } from "@/features/catalog/components/catalog-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("catalog");
  return { title: `${t("title")} · Thi 360` };
}

export default function CatalogPage() {
  return (
    <Suspense fallback={<CatalogPageSkeleton />}>
      <CatalogView />
    </Suspense>
  );
}
