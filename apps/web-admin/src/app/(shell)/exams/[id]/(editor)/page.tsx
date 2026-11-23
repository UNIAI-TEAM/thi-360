import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { ExamGeneralForm } from "@/features/exams/components/exam-general-form";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.general");
  return { title: `${t("title")} · Thi 360` };
}

/** Tab 1 Thông tin chung của bài thi (`/exams/[id]`), dựng trong `ExamEditorLayout`. */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ExamGeneralForm mode="edit" examId={id} />;
}
