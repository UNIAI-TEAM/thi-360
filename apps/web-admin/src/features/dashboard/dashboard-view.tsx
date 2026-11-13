"use client";

import { Button, Card, EmptyState, PageHeader, Skeleton } from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { usePermissions, useSession } from "@/features/session/session-provider";
import { NAVIGATION, shortcutItems } from "@/lib/navigation";

/**
 * Trang chủ quản trị: lời chào và lối tắt sinh từ `navigation.ts` theo quyền (BangDieuKhien.dc.html).
 * Bốn trạng thái: đang tải, lỗi, rỗng (không có lối tắt nào: chỉ lời chào), có dữ liệu.
 */
export function DashboardView() {
  const t = useTranslations();
  const state = useSession();
  const { permissions } = usePermissions();

  if (state.status === "loading") {
    return (
      <div role="status" aria-label={t("common.loading")} className="flex flex-col gap-3">
        <Skeleton className="h-7.5 w-64" />
        <Skeleton className="h-5 w-96 max-w-full" />
        <div className="mt-2 grid grid-cols-[repeat(auto-fit,minmax(16.25rem,1fr))] gap-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      </div>
    );
  }

  if (state.status === "error") {
    return (
      <EmptyState
        icon={<TriangleAlert />}
        title={t("dashboard.errorTitle")}
        description={t("dashboard.errorDescription")}
        action={<Button onClick={state.retry}>{t("common.retry")}</Button>}
      />
    );
  }

  const shortcuts = shortcutItems(NAVIGATION, permissions);
  const name = state.session.fullName.trim() || state.session.username;

  return (
    <>
      <PageHeader title={t("dashboard.greeting", { name })} description={t("dashboard.intro")} />
      {shortcuts.length > 0 && (
        <section aria-labelledby="dashboard-shortcuts" className="flex flex-col gap-2.5">
          <h2 id="dashboard-shortcuts" className="text-title font-semibold">
            {t("dashboard.shortcuts")}
          </h2>
          <ul className="grid grid-cols-[repeat(auto-fit,minmax(16.25rem,1fr))] gap-3">
            {shortcuts.map((item) => {
              const Icon = item.icon;
              return (
                <li key={item.id}>
                  <Card padding="none">
                    <Link
                      href={item.href}
                      className="flex gap-3.5 rounded-lg p-4.5 text-text transition-colors duration-150 hover:bg-subtle focus-visible:ring-3 focus-visible:ring-primary-600/30 focus-visible:outline-none motion-reduce:transition-none"
                    >
                      <span
                        aria-hidden
                        className="flex size-10 shrink-0 items-center justify-center rounded-md bg-primary-50 text-primary-700"
                      >
                        <Icon className="size-5" />
                      </span>
                      <span className="flex flex-col">
                        <span className="font-semibold">{t(`nav.items.${item.id}.label`)}</span>
                        <span className="text-caption text-text-muted">
                          {t(`nav.items.${item.id}.description`)}
                        </span>
                      </span>
                    </Link>
                  </Card>
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </>
  );
}
