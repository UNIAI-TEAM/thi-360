import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { ExamStructureView } from "@/features/exams/components/exam-structure-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.structure");
  return { title: `${t("title")} · Thi360` };
}

/** Cấu trúc và ma trận (bản vẽ BaiThiCauTruc, BaiThiToHop), trong khung ExamEditorLayout. */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ExamStructureView examId={id} />;
}
