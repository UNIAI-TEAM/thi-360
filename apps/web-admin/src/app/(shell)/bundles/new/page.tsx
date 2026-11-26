import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { BundleCreatePage } from "@/features/bundles/components/bundle-form-page";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("bundles.create");
  return { title: `${t("title")} · Thi360` };
}

export default function NewBundlePage() {
  return (
    <Suspense>
      <BundleCreatePage />
    </Suspense>
  );
}
