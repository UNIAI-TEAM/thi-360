import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { QuestionImportView } from "@/features/question-import/components/question-import-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questionImport");
  return { title: `${t("title")} · Thi360` };
}

/** Nhập câu hỏi từ tệp (UNI-2594), dữ liệu mẫu. */
export default function Page() {
  return <QuestionImportView />;
}
