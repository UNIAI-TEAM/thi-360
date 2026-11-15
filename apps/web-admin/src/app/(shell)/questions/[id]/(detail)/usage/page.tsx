import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { QuestionUsageView } from "@/features/questions/components/question-usage-view";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questions.detail");
  return { title: `${t("tabs.usage")} · ${t("title")} · Thi 360` };
}

export default async function QuestionUsagePage({ params }: { params: Promise<{ id: string }> }) {
  if (!isContentMockEnabled()) notFound();
  const { id } = await params;
  return <QuestionUsageView questionId={id} />;
}
