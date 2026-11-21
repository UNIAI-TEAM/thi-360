import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { GradingItemView } from "@/features/grading/components/grading-item-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("grading");
  return { title: `${t("detail.title")} · ${t("list.title")} · Thi 360` };
}

export default async function GradingItemPage({
  params,
}: {
  params: Promise<{ itemId: string }>;
}) {
  const { itemId } = await params;
  return (
    <Suspense>
      <GradingItemView itemId={itemId} />
    </Suspense>
  );
}
