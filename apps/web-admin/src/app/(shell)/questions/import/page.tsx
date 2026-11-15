import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { QuestionImportView } from "@/features/question-import/components/question-import-view";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questionImport");
  return { title: `${t("title")} · Thi 360` };
}

/** Nhập câu hỏi từ tệp (UNI-2594), dữ liệu mẫu. */
export default function Page() {
  if (!isContentMockEnabled()) notFound();
  return <QuestionImportView />;
}
