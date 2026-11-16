import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { QuestionDetailContent } from "@/features/questions/components/question-detail-content";


export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questions.detail");
  return { title: `${t("tabs.content")} · ${t("title")} · Thi 360` };
}

export default async function QuestionDetailPage({ params }: { params: Promise<{ id: string }> }) {
  
  const { id } = await params;
  return <QuestionDetailContent questionId={id} />;
}
