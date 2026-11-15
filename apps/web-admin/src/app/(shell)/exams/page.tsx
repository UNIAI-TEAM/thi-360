import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { ExamListView } from "@/features/exams/components/exam-list-view";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.list");
  return { title: `${t("title")} · Thi 360` };
}

/** Danh sách bài thi (bản vẽ BaiThi, dữ liệu mẫu). */
export default function Page() {
  if (!isContentMockEnabled()) notFound();
  return <ExamListView />;
}
