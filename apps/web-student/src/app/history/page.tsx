import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { AttemptHistoryView } from "@/features/attempt-history/history-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("history");
  return {
    title: t("pageTitle"),
  };
}

export default function HistoryPage() {
  return (
    <Suspense fallback={<div aria-busy="true" className="min-h-screen bg-page" />}>
      <AttemptHistoryView />
    </Suspense>
  );
}
