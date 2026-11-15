import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { QuestionVersionsView } from "@/features/questions/components/question-versions-view";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questions.detail");
  return { title: `${t("tabs.versions")} · ${t("title")} · Thi 360` };
}

export default async function QuestionVersionsPage({ params }: { params: Promise<{ id: string }> }) {
  if (!isContentMockEnabled()) notFound();
  const { id } = await params;
  return <QuestionVersionsView questionId={id} />;
}
