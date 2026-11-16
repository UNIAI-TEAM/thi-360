import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { ExamListView } from "@/features/exams/components/exam-list-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.list");
  return { title: `${t("title")} · Thi 360` };
}

/** Danh sách bài thi (bản vẽ BaiThi, dữ liệu mẫu). */
export default function Page() {
  return <ExamListView />;
}
