"use client";

import { cn } from "@thi360/ui";
import { Check } from "lucide-react";
import { useTranslations } from "next-intl";

export type ImportStepNumber = 1 | 2 | 3 | 4;

/**
 * Thanh bốn bước, cùng kiểu với `BulkImportStepper` (cấp tài khoản hàng loạt; thành phần đó không xuất ra dùng chung
 * nên giữ một bản ở đây). Khung hẹp: bước đang làm có tên, các bước khác chỉ còn số (DESIGN.md mục 5.13).
 */
export function ImportStepper({ currentStep }: { currentStep: ImportStepNumber }) {
  const t = useTranslations("questionImport.steps");
  const steps: Array<{ no: ImportStepNumber; label: string }> = [
    { no: 1, label: t("step1") },
    { no: 2, label: t("step2") },
    { no: 3, label: t("step3") },
    { no: 4, label: t("step4") },
  ];

  return (
    <div className="@container">
      <ol
        aria-label={t("ariaLabel")}
        className="flex gap-1 rounded-lg border border-border bg-surface p-2 @2xl:gap-2 @2xl:p-3"
      >
        {steps.map((step) => {
          const isCurrent = step.no === currentStep;
          const isDone = step.no < currentStep;
          return (
            <li
              key={step.no}
              aria-current={isCurrent ? "step" : undefined}
              className={cn(
                "flex min-h-11 items-center gap-2.5 rounded-md text-label font-medium @2xl:min-w-0 @2xl:flex-1 @2xl:px-3",
                isCurrent ? "min-w-0 flex-1 px-3" : "shrink-0 px-2.5",
                isCurrent ? "bg-primary-50 font-semibold text-primary-text" : isDone ? "text-text" : "text-text-muted",
              )}
            >
              <span
                aria-hidden
                className={cn(
                  "flex size-6 shrink-0 items-center justify-center rounded-full text-field-label font-semibold",
                  isCurrent || isDone ? "bg-primary-fill text-on-primary" : "bg-subtle text-text-muted",
                )}
              >
                {isDone ? <Check className="size-3.5" /> : step.no}
              </span>
              <span className={cn("min-w-0", !isCurrent && "sr-only @2xl:not-sr-only")}>{step.label}</span>
              {isDone && <span className="sr-only">{t("doneLabel")}</span>}
            </li>
          );
        })}
      </ol>
    </div>
  );
}
