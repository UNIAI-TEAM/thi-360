import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { CreateExamPage } from "@/features/exams/components/create-exam-page";


export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("exams.create");
  return { title: `${t("title")} · Thi 360` };
}

/** Tạo bài thi (bản vẽ TaoBaiThi, dữ liệu mẫu): trang riêng, không phải hộp thoại. */
export default function Page() {
  
  return <CreateExamPage />;
}
