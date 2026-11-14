"use client";

import { cn } from "@thi360/ui";
import { Check } from "lucide-react";
import { useTranslations } from "next-intl";

export type BulkImportStepNumber = 1 | 2 | 3 | 4;

export interface BulkImportStepperProps {
  currentStep: BulkImportStepNumber;
}

export function BulkImportStepper({ currentStep }: BulkImportStepperProps) {
  const t = useTranslations("bulkImport.steps");

  const steps: Array<{ no: BulkImportStepNumber; label: string }> = [
    { no: 1, label: t("step1") },
    { no: 2, label: t("step2") },
    { no: 3, label: t("step3") },
    { no: 4, label: t("step4") },
  ];

  return (
    <ol
      aria-label={t("ariaLabel")}
      className="flex flex-wrap gap-2 rounded-lg border border-border bg-surface p-3"
    >
      {steps.map((step) => {
        const isCurrent = step.no === currentStep;
        const isDone = step.no < currentStep;
        return (
          <li
            key={step.no}
            aria-current={isCurrent ? "step" : undefined}
            className={cn(
              "flex min-h-11 flex-[1_1_180px] items-center gap-2.5 rounded-md px-3 text-label font-medium",
              isCurrent
                ? "bg-primary-50 font-semibold text-primary-text"
                : isDone
                  ? "text-text"
                  : "text-text-muted",
            )}
          >
            <span
              aria-hidden
              className={cn(
                "flex size-6 shrink-0 items-center justify-center rounded-full text-field-label font-semibold",
                isCurrent || isDone
                  ? "bg-primary-fill text-on-primary"
                  : "bg-subtle text-text-muted",
              )}
            >
              {isDone ? <Check className="size-3.5" /> : step.no}
            </span>
            <span>{step.label}</span>
          </li>
        );
      })}
    </ol>
  );
}
