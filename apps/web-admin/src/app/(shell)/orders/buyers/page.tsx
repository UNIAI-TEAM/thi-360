import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { OrderBuyersView } from "@/features/orders/components/order-buyers-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("orders.meta");
  return { title: t("buyersTitle") };
}

export default function OrderBuyersPage() {
  return (
    <Suspense>
      <OrderBuyersView />
    </Suspense>
  );
}
