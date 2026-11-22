"use client";

import { cn } from "@thi360/ui";
import { Lock } from "lucide-react";
import { useTranslations } from "next-intl";

/**
 * Lý do khóa bằng chữ (FTM R-15: khóa nào cũng có lý do, không chỉ dựa vào màu hay ô mờ). Mã là khóa của
 * `exams.subjectForm.common.lock`; nơi khóa gắn `id` vào `aria-describedby` của điều khiển bị khóa.
 */
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
