"use client";

import { cn } from "@thi360/ui";
import { Lock } from "lucide-react";
import { useTranslations } from "next-intl";

export type LockReasonCode =
  | "answerType"
  | "multiSubject"
  | "omitted"
  | "promptBasis"
  | "rowCounts"
  | "rowHasConditions"
  | "rowNoSetSize"
  | "rowOmitted"
  | "templateTotal";

export function LockReason({ code, id, className }: { code: LockReasonCode; id?: string; className?: string }) {
  const t = useTranslations("exams.subjectForm.common.lock");
  return (
    <span id={id} data-lock-reason={code} className={cn("inline-flex items-start gap-1 text-caption text-text-muted", className)}>
      <Lock aria-hidden className="mt-0.5 size-3.5 shrink-0" />
      <span>{t(code)}</span>
    </span>
  );
}
