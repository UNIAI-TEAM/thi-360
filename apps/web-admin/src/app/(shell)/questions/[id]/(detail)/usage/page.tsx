import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { QuestionUsageView } from "@/features/questions/components/question-usage-view";


export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questions.detail");
  return { title: `${t("tabs.usage")} · ${t("title")} · Thi 360` };
}

export default async function QuestionUsagePage({ params }: { params: Promise<{ id: string }> }) {
  
  const { id } = await params;
  return <QuestionUsageView questionId={id} />;
}
