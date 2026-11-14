import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AppearanceView } from "@/features/appearance/appearance-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("appearance");
  return { title: `${t("title")} · Thi 360` };
}

export default function AppearancePage() {
  return <AppearanceView />;
}
