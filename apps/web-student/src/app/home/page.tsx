import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { StudentHomeView } from "@/features/home/student-home-view";

export async function generateMetadata(): Promise<Metadata> {
  const common = await getTranslations("common");
  const home = await getTranslations("home");
  return { title: `${common("appName")} — ${home("pageTitle")}` };
}

export default function HomePage() {
  return <StudentHomeView />;
}
