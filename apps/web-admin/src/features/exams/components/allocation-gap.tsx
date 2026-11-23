"use client";

import { cn } from "@thi360/ui";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";

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
