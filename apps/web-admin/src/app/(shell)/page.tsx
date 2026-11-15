import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { DashboardView } from "@/features/dashboard/dashboard-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("dashboard");
  const metadata = await getTranslations("metadata");
  return { title: metadata("titleTemplate", { title: t("title") }) };
}

export default function DashboardPage() {
  return <DashboardView />;
}
