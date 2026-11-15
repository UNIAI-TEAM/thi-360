"use client";

import { Breadcrumb, LocaleSwitcher, ThemeToggle } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { NAVIGATION, resolveBreadcrumb } from "@/lib/navigation";
import { useBreadcrumbLeaf } from "./breadcrumb-leaf";
import { NavDrawer } from "./nav-drawer";
import { UserMenu } from "./user-menu";

/**
 * Thanh trên cao 56px: nút mở menu (dưới `lg`) và breadcrumb bên trái, nút sáng/tối, ngôn ngữ và menu avatar luôn ở
 * bên phải, kể cả khi trang không có breadcrumb (DESIGN 5.8). Dưới `sm` breadcrumb chỉ còn trang hiện tại và menu
 * avatar chỉ còn avatar, để cả thanh vừa một hàng ở 360px.
 */
export function Topbar({ rightSlot }: { rightSlot?: ReactNode } = {}) {
  const t = useTranslations();
  const pathname = usePathname();
  const trail = resolveBreadcrumb(NAVIGATION, pathname);
  const leaf = useBreadcrumbLeaf();
  const leafLabel = trail && pathname !== trail.item.href ? leaf : null;
  const items = trail
    ? [
        { label: t(`nav.groups.${trail.group.id}`) },
        leafLabel
          ? { label: t(`nav.items.${trail.item.id}.label`), href: trail.item.href }
          : { label: t(`nav.items.${trail.item.id}.label`) },
        ...(leafLabel ? [{ label: leafLabel }] : []),
      ]
    : [];

  return (
    <header className="flex h-14 shrink-0 items-center justify-end gap-2 border-b border-border bg-surface px-4 sm:gap-4 sm:px-6">
      {/* `flex-1 min-w-0`: nhóm trái chiếm phần còn lại và co được, breadcrumb cắt chữ thay vì đẩy nhóm phải ra ngoài. */}
      <div className="flex min-w-0 flex-1 items-center gap-2 sm:gap-3">
        <NavDrawer />
        <Breadcrumb
          aria-label={t("shell.breadcrumbLabel")}
          items={items}
          linkComponent={Link}
          collapseOnNarrow
        />
      </div>
      <div className="flex shrink-0 items-center gap-2 sm:gap-3">
        {rightSlot}
        <div className="flex items-center gap-2">
          <ThemeToggle />
          <LocaleSwitcher />
        </div>
        <UserMenu />
      </div>
    </header>
  );
}
