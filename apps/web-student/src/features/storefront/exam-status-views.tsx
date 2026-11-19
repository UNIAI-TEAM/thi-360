"use client";

import { Button, StatusPage } from "@thi360/ui";
import { Ban, SearchX } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useOptionalSession } from "@/features/session/use-optional-session";
import type { OptionalSessionLoader } from "@/features/session/api";

/**
 * Bài thi đã ngừng bán (API 410 `PRODUCT_DISCONTINUED`). App Router không trả được HTTP 410 từ trang nên trang trả
 * 200 và hiện trạng thái này (plan B8). Nút "Bài thi của tôi" chỉ có khi đã đăng nhập.
 */
export function ExamGoneView({
  product,
  loadCurrentSession,
}: {
  product: { code: string; name: string };
  loadCurrentSession?: OptionalSessionLoader;
}) {
  const t = useTranslations("storefront.gone");
  const session = useOptionalSession(loadCurrentSession);

  return (
    <StatusPage
      embedded
      icon={Ban}
      eyebrow={t("eyebrow")}
      title={t("title")}
      description={
        <>
          <strong data-exam-content className="font-semibold text-text">
            {product.name}
          </strong>{" "}
          <span data-exam-content>({product.code})</span> {t("descriptionTail")}
        </>
      }
      meta={t("owned")}
      actions={
        <>
          <Button asChild size="lg">
            <Link href="/#exams">{t("toCatalog")}</Link>
          </Button>
          {session.data && (
            <Button asChild size="lg" variant="secondary">
              <Link href="/home">{t("myExams")}</Link>
            </Button>
          )}
        </>
      }
    />
  );
}

/** Mã không có hoặc bài chưa từng công bố (API 404 `PRODUCT_NOT_FOUND`). */
export function ExamNotFoundView() {
  const t = useTranslations("storefront.notFound");
  return (
    <StatusPage
      embedded
      icon={SearchX}
      eyebrow={t("eyebrow")}
      title={t("title")}
      description={t("description")}
      actions={
        <Button asChild size="lg">
          <Link href="/#exams">{t("toCatalog")}</Link>
        </Button>
      }
    />
  );
}
