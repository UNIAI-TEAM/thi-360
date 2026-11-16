import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { QuestionVersionsView } from "@/features/questions/components/question-versions-view";


export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questions.detail");
  return { title: `${t("tabs.versions")} · ${t("title")} · Thi 360` };
}

export default async function QuestionVersionsPage({ params }: { params: Promise<{ id: string }> }) {
  
  const { id } = await params;
  return <QuestionVersionsView questionId={id} />;
}
