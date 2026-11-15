"use client";

import { cn, focusRing } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useId } from "react";
import { filterNavigation, isActivePath, NAVIGATION } from "@/lib/navigation";
import { usePermissions } from "@/features/session/session-provider";

/** Logo và tên sản phẩm ở đầu menu, dùng chung cho sidebar và ngăn menu trên màn hẹp. */
export function BrandMark({ className }: { className?: string }) {
  const t = useTranslations("shell");
  return (
    <div className={cn("flex items-center gap-2.5 px-2", className)}>
      <div
        aria-hidden
        className="flex size-9 shrink-0 items-center justify-center rounded-md bg-primary-fill text-field-label font-bold text-on-primary"
      >
        {t("logoMark")}
      </div>
      <div className="text-title font-bold text-primary-900">{t("brand")}</div>
    </div>
  );
}

/**
 * Danh sách nhóm menu theo quyền. Một nguồn cho cả sidebar (từ `lg`) và ngăn menu (dưới `lg`); `onNavigate` chạy khi
 * bấm một mục, để ngăn menu đóng lại kể cả khi bấm vào chính trang đang mở.
 */
export function NavigationGroups({ onNavigate }: { onNavigate?: () => void }) {
  const t = useTranslations();
  const pathname = usePathname();
  const { isLoading, permissions } = usePermissions();
  const groups = filterNavigation(NAVIGATION, permissions);
  const groupIdPrefix = useId();

  if (isLoading) {
    return (
      <p role="status" className="px-3 py-2 text-caption text-text-muted">
        {t("shell.menuLoading")}
      </p>
    );
  }

  // Mỗi nhóm là một danh sách mang tên của nhóm, để trình đọc màn hình đọc "Người dùng, danh sách 2 mục".
  return groups.map((group) => (
    <div key={group.id} className="flex flex-col gap-0.5">
      <div
        id={`${groupIdPrefix}-${group.id}`}
        className="px-3 pt-3.5 pb-1.5 text-overline font-semibold tracking-[0.08em] text-text-muted uppercase"
      >
        {t(`nav.groups.${group.id}`)}
      </div>
      <ul aria-labelledby={`${groupIdPrefix}-${group.id}`} className="flex flex-col gap-0.5">
        {group.items.map((item) => {
          const active = isActivePath(item, pathname);
          const Icon = item.icon;
          return (
            <li key={item.id}>
              <Link
                href={item.href}
                aria-current={active ? "page" : undefined}
                onClick={onNavigate}
                className={cn(
                  // Màn cảm ứng: mục cao 44px (DESIGN mục 8).
                  "relative flex h-10 items-center gap-2.5 rounded-sm px-3 text-label transition-colors duration-150 pointer-coarse:h-11 motion-reduce:transition-none",
                  focusRing,
                  // Thanh dọc của mục đang mở vẽ bằng `border` (không phải nền): chế độ tương phản cao của hệ
                  // điều hành bỏ nền và bóng nhưng tô lại viền, nên mục đang mở vẫn phân biệt được (DESIGN 5.13).
                  active
                    ? "bg-primary-50 font-semibold text-primary-text before:absolute before:top-2 before:bottom-2 before:left-0 before:border-l-2 before:border-primary-text"
                    : "font-medium text-text-secondary hover:bg-subtle",
                )}
              >
                <Icon aria-hidden className="size-[18px] shrink-0" />
                <span>{t(`nav.items.${item.id}.label`)}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </div>
  ));
}

/**
 * Thanh menu trái 248px, chỉ hiện từ `lg` (1024px); dưới `lg` menu nằm trong ngăn kéo mở từ nút ở thanh trên
 * (`NavDrawer`, DESIGN 5.8). Chỉ hiện mục người dùng có quyền.
 */
export function Sidebar() {
  const t = useTranslations("shell");
  return (
    <nav
      aria-label={t("menuLabel")}
      className="sticky top-0 hidden h-dvh w-62 shrink-0 flex-col gap-0.5 overflow-y-auto border-r border-border bg-surface px-3 py-4 lg:flex"
    >
      <BrandMark className="pb-4" />
      <NavigationGroups />
    </nav>
  );
}
