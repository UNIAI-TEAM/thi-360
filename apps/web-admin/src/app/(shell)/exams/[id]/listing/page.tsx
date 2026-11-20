import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { ExamListingView } from "@/features/exams/listing/listing-tab";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.editor");
  return { title: `${t("tabs.listing")} · Thi 360` };
}

/** Thông tin bán của bài thi (bản vẽ BaiThiThongTinBan), giữa Cấu trúc và Kiểm tra. */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ExamListingView examId={id} />;
}
