import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { Suspense } from "react";
import { GradingAttemptView } from "@/features/grading/components/grading-attempt-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("grading");
  return { title: `${t("detail.title")} · ${t("list.title")} · Thi 360` };
}

/** Màn chấm một lượt thi. Câu đang mở đọc từ `?question=` nên cần Suspense cho `useSearchParams`. */
export default async function GradingAttemptPage({ params }: { params: Promise<{ attemptId: string }> }) {
  const { attemptId } = await params;
  return (
    <Suspense>
      <GradingAttemptView attemptId={attemptId} />
    </Suspense>
  );
}
