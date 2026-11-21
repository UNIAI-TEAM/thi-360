import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { AttemptDetailSkeleton, AttemptDetailView } from "@/features/attempts/components/attempt-detail-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("attempts");
  return { title: `${t("detail.title")} · ${t("list.title")} · Thi 360` };
}

export default async function AttemptDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <Suspense fallback={<AttemptDetailSkeleton />}>
      <AttemptDetailView attemptId={id} />
    </Suspense>
  );
}
