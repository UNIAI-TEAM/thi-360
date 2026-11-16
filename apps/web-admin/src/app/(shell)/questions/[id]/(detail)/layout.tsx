import type { ReactNode } from "react";
import { QuestionDetailLayout } from "@/features/questions/components/question-detail-layout";

/** Khung chung của ba trang con chi tiết câu hỏi; tiêu đề và thanh tab không dựng lại khi đổi trang con. */
export default async function QuestionDetailRouteLayout({
  params,
  children,
}: {
  params: Promise<{ id: string }>;
  children: ReactNode;
}) {
  const { id } = await params;
  return <QuestionDetailLayout questionId={id}>{children}</QuestionDetailLayout>;
}
