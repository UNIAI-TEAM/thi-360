"use client";

import { Button, cn, Skeleton } from "@thi360/ui";
import { RefreshCw } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { withCallbackUrl } from "@/features/account/account-links";
import type { AccessView } from "./exam-access";

export interface AccessProduct {
  code: string;
  versionNo: number;
  /** Giá phải trả đang thấy, đồng: đi cùng `versionNo` trên URL xác nhận mua để phát hiện giá đổi (TM 8.2). */
  price: number;
  /** Bộ đề luôn dùng nhãn CTA "Mua bộ đề" thay vì "Mua bài thi". */
  isBundle?: boolean;
}

/** `/checkout/<code>?v=<versionNo>&p=<price>` (TM 11.3). */
export function checkoutPath(product: AccessProduct): string {
  return `/checkout/${encodeURIComponent(product.code)}?v=${product.versionNo}&p=${product.price}`;
}

/**
 * Nút hành động của thẻ và trang chi tiết theo quyền của người xem (bảng TM 11.3). Chưa biết quyền thì giữ chỗ cố
 * định, không hiện nút Mua; quản trị viên không có nút chính.
 */
export function AccessAction({
  product,
  view,
  onReload,
  variant,
}: {
  product: AccessProduct;
  view: AccessView;
  onReload: () => void;
  /** `card`: thẻ ở danh mục, thêm nút phụ Xem chi tiết. `detail`: khung Giá bán ở trang chi tiết. */
  variant: "card" | "detail";
}) {
  const t = useTranslations("storefront.access");
  const detailHref = `/exams/${encodeURIComponent(product.code)}`;
  const primarySize = variant === "detail" ? "xl" : "lg";
  const primaryHeight = variant === "detail" ? "h-13" : "h-11";
  const checkout = checkoutPath(product);
  const buyLabel = product.isBundle ? t("buyBundle") : t("buy");

  const viewDetail =
    variant === "card" ? (
      <Button asChild variant="secondary" className="h-11 w-full">
        <Link href={detailHref}>{t("viewDetail")}</Link>
      </Button>
    ) : null;

  const wrapper = variant === "card" ? "flex flex-col gap-2" : "flex flex-col gap-3";
  const grow = "w-full";
  if (view.kind === "loading") {
    return (
      <div role="status" aria-busy="true" aria-label={t("loading")}>
        <Skeleton className={cn("w-full rounded-lg", primaryHeight)} />
      </div>
    );
  }

  if (view.kind === "error") {
    return (
      <div className={wrapper}>
        <p role="alert" className="w-full text-field-label text-danger">
          {t("loadError")}
        </p>
        <Button variant="secondary" className={cn(grow, primaryHeight)} onClick={onReload}>
          <RefreshCw aria-hidden />
          {t("reload")}
        </Button>
        {viewDetail}
      </div>
    );
  }

  if (view.kind === "admin") {
    return (
      <div className={wrapper}>
        {variant === "detail" && <p className="text-field-label text-text-muted">{t("adminNote")}</p>}
        {viewDetail}
      </div>
    );
  }

  if (view.kind === "guest") {
    return (
      <div className={wrapper}>
        <Button asChild size={primarySize} className={grow}>
          <Link href={withCallbackUrl("/login", checkout)} prefetch={false}>{buyLabel}</Link>
        </Button>
        {viewDetail}
      </div>
    );
  }

  const { row } = view;
  const buyMore = !row.hasPendingFulfillment && (
    <Button asChild variant="outline" className={cn("h-11", grow)}>
      <Link href={checkout} prefetch={false}>{t("buyMore")}</Link>
    </Button>
  );
  // Còn lượt mà đơn mua thêm đang kẹt (TM 10.2): không đề nghị thanh toán lại, ghi mã đơn đang kiểm tra.
  const pendingNote = row.hasPendingFulfillment && (
    <p className="w-full text-field-label text-warning">
      {t("pendingTopUp", { orderCode: row.pendingOrderCode ?? "" })}
    </p>
  );
  const attemptsNote = (
    <p className="w-full text-field-label text-text-secondary">{t("attemptsLeft", { count: row.attemptsLeft })}</p>
  );

  switch (row.state) {
    case "IN_PROGRESS":
      return (
        <div className={wrapper}>
          <p className="w-full text-field-label text-text-secondary">{t("continueNote")}</p>
          <Button asChild variant="exam" size={primarySize} className={grow}>
            <Link href={row.activeAttemptId ? `/attempts/${row.activeAttemptId}` : "/home"}>{t("continue")}</Link>
          </Button>
          {buyMore}
          {pendingNote}
          {viewDetail}
        </div>
      );
    case "CAN_START":
      return (
        <div className={wrapper}>
          {attemptsNote}
          <Button asChild variant="exam" size={primarySize} className={grow}>
            <Link href={row.entitlementId ? `/prepare/${row.entitlementId}` : "/home"} prefetch={false}>{t("enter")}</Link>
          </Button>
          {buyMore}
          {pendingNote}
          {viewDetail}
        </div>
      );
    case "EXHAUSTED":
      if (variant === "card") {
        return (
          <div className={wrapper}>
            <Button asChild size={primarySize} className={grow}>
              <Link href={checkout} prefetch={false}>{buyLabel}</Link>
            </Button>
            {viewDetail}
          </div>
        );
      }
      return (
        <div className={wrapper}>
          <p className="w-full text-field-label text-text-secondary">{t("exhaustedNote")}</p>
          <Button asChild size={primarySize} className={grow}>
            <Link href={checkout} prefetch={false}>{t("buyAgain")}</Link>
          </Button>
          {viewDetail}
        </div>
      );
    case "CHECKING":
      return (
        <div className={wrapper}>
          <Button
            variant="secondary"
            size={primarySize}
            className={cn(grow, "border-warning bg-warning-bg text-warning")}
            disabled
            aria-disabled="true"
          >
            {t("checking")}
          </Button>
          {row.pendingOrderCode && (
            <p className="w-full text-field-label text-text-secondary">
              {t("checkingNote", { orderCode: row.pendingOrderCode })}
            </p>
          )}
          {viewDetail}
        </div>
      );
    case "NONE":
      return (
        <div className={wrapper}>
          <Button asChild size={primarySize} className={grow}>
            <Link href={checkout} prefetch={false}>{buyLabel}</Link>
          </Button>
          {viewDetail}
        </div>
      );
  }
}
