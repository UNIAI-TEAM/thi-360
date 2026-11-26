import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import {
  GradingItemSkeleton,
  GradingItemView,
} from "@/features/grading/components/grading-item-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("grading");
  return { title: `${t("detail.title")} · ${t("list.title")} · Thi360` };
}

export default async function GradingItemPage({
  params,
}: {
  params: Promise<{ itemId: string }>;
}) {
  const t = await getTranslations("grading");
  const { itemId } = await params;
  return (
    <Suspense fallback={<GradingItemSkeleton label={t("detail.loading")} />}>
      <GradingItemView itemId={itemId} />
    </Suspense>
  );
}
