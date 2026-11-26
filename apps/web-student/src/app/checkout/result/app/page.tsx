import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { CheckoutAppReturnView } from "@/features/mobile/checkout-app-return-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("checkout");
  return { title: t("resultPageTitle") };
}

export default function CheckoutAppReturnPage() {
  return (
    <Suspense>
      <CheckoutAppReturnView />
    </Suspense>
  );
}
