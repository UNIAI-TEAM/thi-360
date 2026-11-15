import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { MockupPlaceholder } from "@/components/mockup-placeholder";
import { isContentMockEnabled } from "@/lib/mock/content-mock";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("questions.editor");
  return { title: `${t("title")} · Thi 360` };
}

/** Chỗ giữ của lane mockup; thay bằng màn thật (ChonDang). */
export default function Page() {
  if (!isContentMockEnabled()) notFound();
  return <MockupPlaceholder screen="ChonDang" />;
}
