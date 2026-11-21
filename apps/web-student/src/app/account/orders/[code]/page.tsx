import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { OrderDetailView } from "@/features/orders/order-detail-view";

type Props = { params: Promise<{ code: string }> };

function decodeCode(raw: string): string {
  try {
    return decodeURIComponent(raw);
  } catch {
    return raw;
  }
}

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("orders");
  return { title: t("detailPageTitle") };
}

export default async function OrderDetailPage({ params }: Props) {
  const { code } = await params;
  return (
    <Suspense>
      <OrderDetailView code={decodeCode(code)} />
    </Suspense>
  );
}
