import { LocaleSwitcher, ThemeToggle } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import type { ReactNode } from "react";

export function StudentHeader({
  rightSlot,
  actions,
}: {
  rightSlot?: ReactNode;
  actions?: ReactNode;
}) {
  const common = useTranslations("common");

  return (
    <header className="border-b border-border bg-surface">
      {/* Đệm ngang 16px dưới `sm` để logo, hai nút tùy chọn và nút tài khoản vừa một hàng từ 320px (không xuống hàng khi phiên tải xong). */}
      <div className="mx-auto flex max-w-[1170px] flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <Link href="/" className="flex items-center gap-3">
          <span
            aria-hidden
            className="flex size-10 items-center justify-center rounded-lg bg-primary-fill font-bold text-on-primary"
          >
            {common("logoMark")}
          </span>
          <span className="text-h3 font-bold text-primary-900">{common("appName")}</span>
        </Link>
        <div className="flex items-center gap-3">
          {rightSlot}
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <LocaleSwitcher />
          </div>
          {actions}
        </div>
      </div>
    </header>
  );
}
