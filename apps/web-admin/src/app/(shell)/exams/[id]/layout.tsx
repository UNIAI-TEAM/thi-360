import type { ReactNode } from "react";
import { ExamEditorLayout } from "@/features/exams/components/exam-editor-layout";

/** Khung chung của năm trang con soạn bài thi; tiêu đề và thanh tab không dựng lại khi đổi trang con. */
export default async function ExamEditorRouteLayout({
  params,
  children,
}: {
  params: Promise<{ id: string }>;
  children: ReactNode;
}) {
  const { id } = await params;
  return <ExamEditorLayout examId={id}>{children}</ExamEditorLayout>;
}
