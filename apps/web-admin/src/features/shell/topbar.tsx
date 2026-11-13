"use client";

import { Breadcrumb } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAVIGATION, resolveBreadcrumb } from "@/lib/navigation";
import { UserMenu } from "./user-menu";

/** Thanh trên cao 56px: breadcrumb bên trái, menu avatar bên phải (DESIGN 5.8). */
export function Topbar() {
  const t = useTranslations();
  const pathname = usePathname();
  const trail = resolveBreadcrumb(NAVIGATION, pathname);
  const items = trail
    ? [
        { label: t(`nav.groups.${trail.group.id}`) },
        { label: t(`nav.items.${trail.item.id}.label`) },
      ]
    : [];

  return (
    <header className="flex h-14 shrink-0 items-center justify-between gap-4 border-b border-border bg-surface px-6">
      <Breadcrumb aria-label={t("shell.breadcrumbLabel")} items={items} linkComponent={Link} />
      <UserMenu />
    </header>
  );
}
