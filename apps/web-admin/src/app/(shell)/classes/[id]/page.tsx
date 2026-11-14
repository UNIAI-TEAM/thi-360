import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { ClassDetailView } from "@/features/classes/components/class-detail-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("classes.detail");
  return { title: `${t("title")} · Thi 360` };
}

export default async function ClassDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <ClassDetailView classId={id} />;
}
