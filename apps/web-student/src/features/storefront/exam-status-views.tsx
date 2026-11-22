"use client";

import { Button, cn } from "@thi360/ui";
import { Ban, SearchX, type LucideIcon } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import type { ReactNode } from "react";
import { useOptionalSession } from "@/features/session/use-optional-session";
import type { OptionalSessionLoader } from "@/features/session/api";

/**
 * Khung trang trạng thái nhúng (thẻ có icon, mã lỗi, tiêu đề và nút). Vẽ lại khuôn `StatusPage` của kit tại đây
 * vì `packages/ui` ngoài đường dẫn sở hữu của FS-1, mà canvas `BaiThiNgungBan` yêu cầu 410 dùng tông cảnh báo và
 * 404 dùng tông trung tính thay vì đỏ lỗi mặc định của `StatusPage`.
 */
function StatusLayout({
  icon: Icon,
  tone,
  eyebrow,
  title,
  description,
  meta,
  actions,
}: {
  icon: LucideIcon;
  /** `warning`: ô icon và dòng mã lỗi màu cảnh báo (410). `muted`: trung tính mờ (404). */
  tone: "warning" | "muted";
  eyebrow: string;
  title: string;
  description: ReactNode;
  meta?: ReactNode;
  actions: ReactNode;
}) {
  return (
    <div className="flex items-center justify-center p-4 sm:p-6">
      {/* `@container`: hàng nút xếp theo bề rộng của thẻ, không theo màn hình. */}
      <div className="@container flex w-full max-w-130 flex-col items-center gap-4 rounded-2xl border border-border bg-surface px-6 py-10 text-center shadow-xl sm:px-10 sm:py-12">
        <span
          aria-hidden
          className={cn(
            "flex size-14 items-center justify-center rounded-xl",
            tone === "warning" ? "bg-warning-bg text-warning" : "bg-subtle text-text-muted",
          )}
        >
          <Icon className="size-7" />
        </span>
        <p
          className={cn(
            "text-overline font-semibold tracking-[0.08em]",
            tone === "warning" ? "text-warning" : "text-text-muted",
          )}
        >
          {eyebrow}
        </p>
        <h1 className="text-page-title font-bold text-text">{title}</h1>
        <p className="text-text-muted">{description}</p>
        {meta && <p className="text-caption text-text-muted">{meta}</p>}
        {/*
         * Thẻ hẹp (điện thoại 320–360px): nút xếp chồng, rộng hết thẻ, nhãn dài được xuống dòng và nút cao theo
         * nhãn, thay vì một dòng tràn ra ngoài thẻ. Thẻ đủ rộng thì các nút đứng cạnh nhau như thường.
         */}
        <div className="mt-2 flex flex-wrap justify-center gap-3 @max-md:w-full @max-md:flex-col @max-md:items-stretch @max-md:[&>*]:h-auto @max-md:[&>*]:min-h-12 @max-md:[&>*]:py-3 @max-md:[&>*]:whitespace-normal">
          {actions}
        </div>
      </div>
    </div>
  );
}

/**
 * Bài thi đã ngừng bán (API 410 `PRODUCT_DISCONTINUED`). App Router không trả được HTTP 410 từ trang nên trang trả
 * 200 và hiện trạng thái này (plan B8). Tông cảnh báo theo canvas `BaiThiNgungBan`, không phải đỏ lỗi. Nút
 * "Bài thi của tôi" chỉ có khi đã đăng nhập.
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
    <StatusLayout
      icon={Ban}
      tone="warning"
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

/** Mã không có hoặc bài chưa từng công bố (API 404 `PRODUCT_NOT_FOUND`): tông trung tính mờ theo canvas. */
export function ExamNotFoundView() {
  const t = useTranslations("storefront.notFound");
  return (
    <StatusLayout
      icon={SearchX}
      tone="muted"
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

/** Bộ đề tạm thời chưa bán được khi không có dữ liệu chi tiết đi kèm (409 `PRODUCT_UNAVAILABLE`). */
export function BundleUnavailableView() {
  const t = useTranslations("bundle.unavailablePage");
  const buyLabel = useTranslations("bundle")("buyLabel");
  return (
    <StatusLayout
      icon={Ban}
      tone="warning"
      eyebrow={t("eyebrow")}
      title={t("title")}
      description={t("description")}
      actions={
        <>
          <Button type="button" size="lg" disabled>
            {buyLabel}
          </Button>
          <Button asChild size="lg" variant="secondary">
            <Link href="/#exams">{t("toCatalog")}</Link>
          </Button>
        </>
      }
    />
  );
}
