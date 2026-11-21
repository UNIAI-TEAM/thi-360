import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { BundleEditPage } from "@/features/bundles/components/bundle-form-page";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("bundles.edit");
  return { title: `${t("title")} · Thi 360` };
}

export default async function BundleDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <BundleEditPage bundleId={id} />;
}
