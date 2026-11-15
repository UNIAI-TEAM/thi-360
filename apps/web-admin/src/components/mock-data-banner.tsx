"use client";

import { cn } from "@thi360/ui";
import { FlaskConical } from "lucide-react";
import { useTranslations } from "next-intl";

/**
 * Dải "Dữ liệu mẫu" đầu các màn chưa nối backend (DESIGN.md mục 5.14). Đặt ngay dưới tiêu đề trang, một lần mỗi màn;
 * hộp thoại không lặp lại dải này.
 */
export function MockDataBanner({ className }: { className?: string }) {
  const t = useTranslations("mockData");
  return (
    <p
      role="note"
      className={cn(
        "flex items-start gap-2.5 rounded-lg border border-info/30 bg-info-bg px-3.5 py-2.5 text-body text-info",
        className,
      )}
    >
      <FlaskConical aria-hidden className="mt-0.5 size-4 shrink-0" />
      <span>
        <strong className="font-semibold">{t("title")}</strong> {t("description")}
      </span>
    </p>
  );
}
