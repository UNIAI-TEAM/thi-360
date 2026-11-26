import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { BundleListView } from "@/features/bundles/components/bundle-list-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("bundles.list");
  return { title: `${t("title")} · Thi360` };
}

export default function BundlesPage() {
  return (
    <Suspense>
      <BundleListView />
    </Suspense>
  );
}
