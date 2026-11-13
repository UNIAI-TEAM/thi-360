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
      <div className="mx-auto flex max-w-[1170px] flex-wrap items-center justify-between gap-3 px-6 py-3">
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
          {actions}
        </div>
      </div>
    </header>
  );
}
