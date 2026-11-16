import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { Suspense } from "react";
import { QuestionBankSkeleton, QuestionBankView } from "@/features/questions/components/question-bank-view";


export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questions.list");
  return { title: `${t("title")} · Thi 360` };
}

/** Ngân hàng câu hỏi (canvas `NganHangCauHoi`). Bộ lọc đọc từ URL nên cần Suspense cho `useSearchParams`. */
export default function QuestionsPage() {
  
  return (
    <Suspense fallback={<QuestionBankSkeleton />}>
      <QuestionBankView />
    </Suspense>
  );
}
