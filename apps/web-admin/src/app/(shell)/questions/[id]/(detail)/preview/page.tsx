import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { QuestionPreviewView } from "@/features/questions/preview/question-preview-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questions.detail");
  return { title: `${t("tabs.preview")} · ${t("title")} · Thi 360` };
}

export default async function QuestionPreviewPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <QuestionPreviewView questionId={id} />;
}
