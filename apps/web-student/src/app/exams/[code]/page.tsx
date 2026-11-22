import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";
import { cache } from "react";
import { PublicShell } from "@/components/public-shell";
import { BundleDetailView } from "@/features/storefront/bundle-detail-view";
import { ExamDetailView } from "@/features/storefront/exam-detail-view";
import { BundleUnavailableView, ExamGoneView } from "@/features/storefront/exam-status-views";
import { isBundleProduct } from "@/features/storefront/format";
import { loadProductOnServer } from "@/features/storefront/server-api";

type Props = { params: Promise<{ code: string }> };

function decodeCode(raw: string): string {
  try {
    return decodeURIComponent(raw);
  } catch {
    return raw;
  }
}

// `generateMetadata` và trang cùng một lời gọi API trong một request.
const lookupProduct = cache(loadProductOnServer);

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const lookup = await lookupProduct(decodeCode((await params).code));
  if (lookup.status === "ok") {
    return { title: lookup.product.name, description: lookup.product.summary ?? undefined };
  }
  if (lookup.status === "unavailable") {
    if (lookup.product && isBundleProduct(lookup.product)) {
      return { title: lookup.product.name, description: lookup.product.summary ?? undefined };
    }
    const bundleT = await getTranslations("bundle");
    return { title: bundleT("unavailablePage.title") };
  }
  const t = await getTranslations("storefront");
  return { title: lookup.status === "gone" ? t("gone.title") : t("notFound.title") };
}

export default async function ExamPage({ params }: Props) {
  const lookup = await lookupProduct(decodeCode((await params).code));
  if (lookup.status === "notFound") notFound();

  return (
    <PublicShell>
      {lookup.status === "gone" ? (
        <ExamGoneView product={lookup.product} />
      ) : lookup.status === "unavailable" ? (
        lookup.product && isBundleProduct(lookup.product) ? (
          <BundleDetailView product={lookup.product} unavailable />
        ) : (
          <BundleUnavailableView />
        )
      ) : isBundleProduct(lookup.product) ? (
        <BundleDetailView product={lookup.product} />
      ) : (
        <ExamDetailView product={lookup.product} />
      )}
    </PublicShell>
  );
}
