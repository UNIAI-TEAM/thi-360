import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { CheckoutResultView } from "@/features/checkout/checkout-result-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("checkout");
  return { title: t("resultPageTitle") };
}

export default function CheckoutResultPage() {
  return (
    <Suspense>
      <CheckoutResultView />
    </Suspense>
  );
}
