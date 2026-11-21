import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";

import { OrderListView } from "@/features/orders/components/order-list-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("orders.meta");
  return { title: t("listTitle") };
}

export default function OrdersPage() {
  return (
    <Suspense>
      <OrderListView />
    </Suspense>
  );
}
