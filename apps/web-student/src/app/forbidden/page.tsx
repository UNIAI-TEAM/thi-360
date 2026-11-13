import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { StudentForbiddenView } from "@/features/session/forbidden-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("forbidden");
  return { title: t("title") };
}

export default function ForbiddenPage() {
  return <StudentForbiddenView />;
}
