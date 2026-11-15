import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { QuestionBankSkeleton, QuestionBankView } from "@/features/questions/components/question-bank-view";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questions.list");
  return { title: `${t("title")} · Thi 360` };
}

/** Ngân hàng câu hỏi (canvas `NganHangCauHoi`). Bộ lọc đọc từ URL nên cần Suspense cho `useSearchParams`. */
export default function QuestionsPage() {
  if (!isContentMockEnabled()) notFound();
  return (
    <Suspense fallback={<QuestionBankSkeleton />}>
      <QuestionBankView />
    </Suspense>
  );
}
