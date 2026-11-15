import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { QuestionEditorView } from "@/features/questions/components/question-editor-view";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questions.editor");
  return { title: `${t("editTitle")} · Thi 360` };
}

export default async function QuestionEditPage({ params }: { params: Promise<{ id: string }> }) {
  if (!isContentMockEnabled()) notFound();
  const { id } = await params;
  return <QuestionEditorView mode="edit" questionId={id} />;
}
