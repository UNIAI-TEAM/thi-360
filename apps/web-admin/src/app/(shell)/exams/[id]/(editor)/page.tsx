import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { ExamGeneralView } from "@/features/exams/components/exam-general-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.general");
  return { title: `${t("title")} · Thi 360` };
}

/** Thông tin chung của bài thi (theo trường của bản vẽ TaoBaiThi), trong khung ExamEditorLayout. */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ExamGeneralView examId={id} />;
}
