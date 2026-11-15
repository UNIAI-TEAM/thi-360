import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { ExamStructureView } from "@/features/exams/components/exam-structure-view";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.structure");
  return { title: `${t("title")} · Thi 360` };
}

/** Cấu trúc và ma trận (bản vẽ BaiThiCauTruc, BaiThiToHop), trong khung ExamEditorLayout. */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  if (!isContentMockEnabled()) notFound();
  const { id } = await params;
  return <ExamStructureView examId={id} />;
}
