"use client";

import { cn } from "@thi360/ui";
import { useTranslations } from "next-intl";

/**
 * Thanh bước của luồng Tạo bài thi (TaoBaiThi2B): bước 1 Thông tin chung (đang làm), bước 2 Cấu trúc và ma trận. Cùng
 * kiểu với `BulkImportStepper`/`ImportStepper`: bước hiện tại nền nhạt và `aria-current="step"`, bước sau mờ.
 */
export function CreateExamStepper() {
  const t = useTranslations("exams.create.steps");
  const steps = [
    { no: 1, label: t("step1"), current: true },
    { no: 2, label: t("step2"), current: false },
  ];
  return (
    <ol
      aria-label={t("ariaLabel")}
      className="flex flex-wrap items-center gap-1 rounded-lg border border-border bg-surface p-2 sm:gap-2"
    >
      {steps.map((step, index) => (
        <li key={step.no} className="flex items-center gap-2">
          {index > 0 && (
            <span aria-hidden className="text-text-muted">
              —
            </span>
          )}
          <span
            aria-current={step.current ? "step" : undefined}
            className={cn(
              "flex min-h-9 items-center gap-2 rounded-md px-2.5 text-label font-medium",
              step.current ? "bg-primary-50 font-semibold text-primary-text" : "text-text-muted",
            )}
          >
            <span
              aria-hidden
              className={cn(
                "flex size-6 shrink-0 items-center justify-center rounded-full text-field-label font-semibold",
                step.current ? "bg-primary-fill text-on-primary" : "border border-border-strong text-text-muted",
              )}
            >
              {step.no}
            </span>
            {step.label}
          </span>
        </li>
      ))}
    </ol>
  );
}
