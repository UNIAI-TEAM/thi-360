"use client";

import { cn } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { filterNavigation, isActivePath, NAVIGATION } from "@/lib/navigation";
import { usePermissions } from "@/features/session/session-provider";

/** Thanh menu trái 248px (DESIGN 5.8). Chỉ hiện mục người dùng có quyền. */
export function Sidebar() {
  const t = useTranslations();
  const pathname = usePathname();
  const { isLoading, permissions } = usePermissions();
  const groups = filterNavigation(NAVIGATION, permissions);

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
        groups.map((group) => (
          <div key={group.id} className="flex flex-col gap-0.5">
            <div className="px-3 pt-3.5 pb-1.5 text-overline font-semibold tracking-[0.08em] text-text-muted uppercase">
              {t(`nav.groups.${group.id}`)}
            </div>
            {group.items.map((item) => {
              const active = isActivePath(item, pathname);
              const Icon = item.icon;
              return (
                <Link
                  key={item.id}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "flex h-10 items-center gap-2.5 rounded-sm px-3 text-label transition-colors duration-150 motion-reduce:transition-none",
                    "focus-visible:ring-3 focus-visible:ring-primary-600/30 focus-visible:outline-none",
                    active
                      ? "bg-primary-50 font-semibold text-primary-text"
                      : "font-medium text-text-secondary hover:bg-subtle",
                  )}
                >
                  <Icon aria-hidden className="size-[18px] shrink-0" />
                  <span>{t(`nav.items.${item.id}.label`)}</span>
                </Link>
              );
            })}
          </div>
        ))
      )}
    </nav>
  );
}
