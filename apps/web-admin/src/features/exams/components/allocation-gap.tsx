"use client";

import { cn } from "@thi360/ui";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";

/**
 * Chênh lệch giữa số câu phân bổ và chỉ tiêu (UNI-1702): `missing = chỉ tiêu − đã phân bổ`. Vượt là chữ đỏ kèm "Vượt N câu",
 * thiếu là chữ cảnh báo kèm "Thiếu N câu" (không chỉ nhờ màu); đúng thì hiện `exact` (mặc định 0) với màu của ô.
 */
export function AllocationGap({ missing, exact = "0", className }: { missing: number; exact?: ReactNode; className?: string }) {
  const t = useTranslations("exams.structure.gap");
  if (missing === 0) return <span className={className}>{exact}</span>;
  const over = missing < 0;
  return (
    <span className={cn("font-semibold", over ? "text-danger" : "text-warning", className)}>
      {t(over ? "over" : "under", { count: Math.abs(missing) })}
    </span>
  );
}
