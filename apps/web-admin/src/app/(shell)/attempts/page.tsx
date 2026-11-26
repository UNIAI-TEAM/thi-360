import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { AttemptListSkeleton, AttemptListView } from "@/features/attempts/components/attempt-list-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("attempts.list");
  return { title: `${t("title")} · Thi360` };
}

export default function AttemptsPage() {
  return (
    <Suspense fallback={<AttemptListSkeleton />}>
      <AttemptListView />
    </Suspense>
  );
}
