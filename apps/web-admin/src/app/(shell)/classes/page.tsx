import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { ClassesListView } from "@/features/classes/components/classes-list-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("classes.list");
  return { title: `${t("title")} · Thi 360` };
}

export default function ClassesPage() {
  return <ClassesListView />;
}
