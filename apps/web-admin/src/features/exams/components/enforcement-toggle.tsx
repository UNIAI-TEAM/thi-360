"use client";

import { cn, focusRing } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId } from "react";
import type { QuotaBasis, QuotaEnforcement } from "../types";
import { canRequire } from "../allocation";

export interface EnforcementToggleProps {
  /** Tên bảng đứng trước nhãn, để đọc màn hình phân biệt các nhóm radio cùng trang. */
  legend: string;
  name: string;
  value: QuotaEnforcement;
  onChange: (value: QuotaEnforcement) => void;
  /** Đơn vị đếm của bảng: mức Bắt buộc chỉ nhận đơn vị câu (H7), đơn vị khác thì khóa nút kèm lý do. */
  basis?: QuotaBasis;
  /** Chỉ tiêu trải nhiều môn không đặt Bắt buộc được (H6): khóa nút kèm lý do riêng. */
  lockRequiredReason?: string;
  disabled?: boolean;
}

/**
 * Nhãn "Bắt buộc" hoặc "Tham khảo" của một bảng phân bổ (K9, H21). Bắt buộc: bộ giải chỉ dùng phân bố này. Tham khảo: chỉ
 * đối chiếu khi sinh thử. Nút "Bắt buộc" bị khóa khi đơn vị không phải câu hoặc chỉ tiêu trải nhiều môn.
 */
export function EnforcementToggle({ legend, name, value, onChange, basis, lockRequiredReason, disabled = false }: EnforcementToggleProps) {
  const t = useTranslations("exams.allocation.enforcement");
  const reasonId = useId();
  const basisLocked = !canRequire(basis);
  const reason = lockRequiredReason ?? (basisLocked ? t("basisLocked") : null);
  const requiredLocked = reason !== null;
  const options: Array<{ value: QuotaEnforcement; label: string; locked: boolean }> = [
    { value: "REQUIRED", label: t("REQUIRED"), locked: requiredLocked },
    { value: "REFERENCE", label: t("REFERENCE"), locked: false },
  ];
  return (
    <fieldset className="flex min-w-0 flex-col gap-1" disabled={disabled} aria-describedby={requiredLocked ? reasonId : undefined}>
      <legend className="sr-only">{t("legend", { name: legend })}</legend>
      <div className="inline-flex w-fit rounded-md border border-border bg-subtle p-0.5">
        {options.map((option) => {
          const checked = option.value === value;
          return (
            <label
              key={option.value}
              className={cn(
                "flex min-h-8 cursor-pointer items-center gap-2 rounded-sm px-3 text-label font-medium transition-colors duration-150 motion-reduce:transition-none",
                checked ? "bg-surface font-semibold text-primary-text shadow-sm" : "text-text-secondary hover:text-text",
                option.locked && !checked && "cursor-not-allowed opacity-60",
              )}
            >
              <input
                type="radio"
                name={name}
                value={option.value}
                checked={checked}
                disabled={option.locked && !checked}
                onChange={() => onChange(option.value)}
                className={cn("size-4 shrink-0 accent-primary-fill", focusRing)}
              />
              {option.label}
            </label>
          );
        })}
      </div>
      {requiredLocked && (
        <p id={reasonId} className="text-caption text-text-muted">
          {reason}
        </p>
      )}
    </fieldset>
  );
}
