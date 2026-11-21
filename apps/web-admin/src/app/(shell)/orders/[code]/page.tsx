import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

import { OrderDetailView } from "@/features/orders/components/order-detail-view";
import { safeDecodeCode } from "@/features/orders/format";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ code: string }>;
}): Promise<Metadata> {
  const { code } = await params;
  const t = await getTranslations("orders.meta");
  return { title: t("detailTitle", { code: safeDecodeCode(code) }) };
}

export default async function OrderDetailPage({
  params,
}: {
  params: Promise<{ code: string }>;
}) {
  const { code } = await params;
  return <OrderDetailView code={safeDecodeCode(code)} />;
}
