import { notFound } from "next/navigation";
import type { ReactNode } from "react";
import { ExamEditorLayout } from "@/features/exams/components/exam-editor-layout";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

/** Khung chung của bốn trang con soạn bài thi; tiêu đề và thanh tab không dựng lại khi đổi trang con. */
export default async function ExamEditorRouteLayout({
  params,
  children,
}: {
  params: Promise<{ id: string }>;
  children: ReactNode;
}) {
  if (!isContentMockEnabled()) notFound();
  const { id } = await params;
  return <ExamEditorLayout examId={id}>{children}</ExamEditorLayout>;
}
