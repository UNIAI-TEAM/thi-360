import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { GradingListSkeleton, GradingListView } from "@/features/grading/components/grading-list-view";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("grading.list");
  return { title: `${t("title")} · Thi 360` };
}

/** Hàng chờ chấm tự luận (UNI-2604). Bộ lọc đọc từ URL nên cần Suspense cho `useSearchParams`. */
export default function GradingPage() {
  if (!isContentMockEnabled()) notFound();
  return (
    <Suspense fallback={<GradingListSkeleton />}>
      <GradingListView />
    </Suspense>
  );
}
