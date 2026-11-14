import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { DevUiGallery } from "./dev-ui-gallery";

export async function generateMetadata(): Promise<Metadata> {
  if (process.env.NODE_ENV === "production") return {};
  const t = await getTranslations("devUi");
  return { title: `${t("title")} · Thi 360` };
}

/** Chỉ mở ngoài production (plan D/T06). */
export default function DevUiPage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <DevUiGallery />;
}
