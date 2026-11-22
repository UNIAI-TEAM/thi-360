import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { CheckoutView } from "@/features/checkout/checkout-view";
import { loadProductOnServer } from "@/features/storefront/server-api";

type Props = { params: Promise<{ code: string }> };

function decodeCode(raw: string): string {
  try {
    return decodeURIComponent(raw);
  } catch {
    return raw;
  }
}

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("checkout");
  return { title: t("pageTitle") };
}

export default async function CheckoutPage({ params }: Props) {
  const { code } = await params;
  const decodedCode = decodeCode(code);
  const lookup = await loadProductOnServer(decodedCode);
  return (
    <Suspense>
      <CheckoutView code={decodedCode} initialProduct={lookup} />
    </Suspense>
  );
}
