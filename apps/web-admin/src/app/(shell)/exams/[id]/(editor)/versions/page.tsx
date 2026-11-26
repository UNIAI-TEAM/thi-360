import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { ExamVersionsView } from "@/features/exams/components/exam-versions-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.versions");
  return { title: `${t("title")} · Thi360` };
}

/** Phiên bản và công bố (bản vẽ BaiThiPhienBan, hộp thoại CongBoBaiThi). */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ExamVersionsView examId={id} />;
}
