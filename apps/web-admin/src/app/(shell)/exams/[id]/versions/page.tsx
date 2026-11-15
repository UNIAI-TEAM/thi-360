import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { ExamVersionsView } from "@/features/exams/components/exam-versions-view";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.versions");
  return { title: `${t("title")} · Thi 360` };
}

/** Phiên bản và công bố (bản vẽ BaiThiPhienBan, hộp thoại CongBoBaiThi). */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  if (!isContentMockEnabled()) notFound();
  const { id } = await params;
  return <ExamVersionsView examId={id} />;
}
