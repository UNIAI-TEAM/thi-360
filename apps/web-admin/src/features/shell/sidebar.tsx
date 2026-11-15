"use client";

import { cn, focusRing } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useId } from "react";
import { filterNavigation, isActivePath, NAVIGATION } from "@/lib/navigation";
import { usePermissions } from "@/features/session/session-provider";

/** Thanh menu trái 248px (DESIGN 5.8). Chỉ hiện mục người dùng có quyền. */
export function Sidebar() {
  const t = useTranslations();
  const pathname = usePathname();
  const { isLoading, permissions } = usePermissions();
  const groups = filterNavigation(NAVIGATION, permissions);
  const groupIdPrefix = useId();

  return (
    <nav
      aria-label={t("shell.menuLabel")}
      className="sticky top-0 flex h-dvh w-62 shrink-0 flex-col gap-0.5 overflow-y-auto border-r border-border bg-surface px-3 py-4"
    >
      <div className="flex items-center gap-2.5 px-2 pb-4">
        <div
          aria-hidden
          className="flex size-9 items-center justify-center rounded-md bg-primary-fill text-field-label font-bold text-on-primary"
        >
          {t("shell.logoMark")}
        </div>
        <div className="text-title font-bold text-primary-900">{t("shell.brand")}</div>
      </div>

      {isLoading ? (
        <p role="status" className="px-3 py-2 text-caption text-text-muted">
          {t("shell.menuLoading")}
        </p>
      ) : (
        // Mỗi nhóm là một danh sách mang tên của nhóm, để trình đọc màn hình đọc "Người dùng, danh sách 2 mục".
        groups.map((group) => (
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
                      className={cn(
                        "relative flex h-10 items-center gap-2.5 rounded-sm px-3 text-label transition-colors duration-150 motion-reduce:transition-none",
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
        ))
      )}
    </nav>
  );
}
