"use client";

import { Chip, cn, focusRing } from "@thi360/ui";
import { Info } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId } from "react";
import type { QuotaBasis, QuotaEnforcement } from "../types";
import { canRequire } from "../allocation";
import { hsaFixedEnforcement, useHsaExam } from "../hsa-enforcement";

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
  /** Chỉ tiêu chờ chuyên gia xác nhận (HSA Tiếng Anh): trong bài HSA cố định Tham khảo kèm lý do chờ chuyên gia. */
  expertPending?: boolean;
  /** Chiều ma trận trống mà lớp chuẩn hóa giữ nguyên Tham khảo (R1-2): trong bài HSA chip khớp bản nháp Tham khảo kèm lý do chưa khai báo. */
  undeclared?: boolean;
  disabled?: boolean;
}

/**
 * Nhãn "Bắt buộc" hoặc "Tham khảo" của một bảng phân bổ (K9, H21). Bắt buộc: bộ giải chỉ dùng phân bố này. Tham khảo: chỉ
 * đối chiếu khi sinh thử. Nút "Bắt buộc" bị khóa khi đơn vị không phải câu hoặc chỉ tiêu trải nhiều môn. Kỳ thi HSA sinh đề
 * luôn theo ma trận nên không có nút: chỉ chip chữ với lý do.
 */
export function EnforcementToggle({
  legend,
  name,
  value,
  onChange,
  basis,
  lockRequiredReason,
  expertPending = false,
  undeclared = false,
  disabled = false,
}: EnforcementToggleProps) {
  const t = useTranslations("exams.allocation.enforcement");
  const reasonId = useId();
  const hsaExam = useHsaExam();
  const basisLocked = !canRequire(basis);
  const reason = lockRequiredReason ?? (basisLocked ? t("basisLocked") : null);
  const requiredLocked = reason !== null;
  // Chiều chưa khai báo vẫn ở Tham khảo trong bản nháp (R1-2): chip khớp bản nháp thay vì mức Bắt buộc công thức.
  const fixed = hsaExam ? (expertPending || undeclared ? "REFERENCE" : hsaFixedEnforcement(basis, requiredLocked)) : undefined;

  if (fixed !== undefined) {
    const caption = fixed === "REQUIRED" ? t("fixedRequired") : expertPending ? t("expertPending") : (reason ?? (undeclared ? t("undeclared") : null));
    return (
      <div role="group" aria-label={t("legend", { name: legend })} aria-describedby={caption ? reasonId : undefined} data-fixed={fixed} className="flex min-w-0 flex-col gap-1">
        <Chip variant="tag" className="w-fit">
          {t(fixed)}
        </Chip>
        {caption && (
          <p id={reasonId} className="text-caption text-text-muted">
            {caption}
          </p>
        )}
      </div>
    );
  }

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
                "relative flex min-h-8 cursor-pointer items-center gap-2 rounded-sm px-3 text-label font-medium transition-colors duration-150 motion-reduce:transition-none",
                // Màn cảm ứng: vùng bấm 44px bằng phần tử giả, không đổi kích thước nhìn thấy (DESIGN.md 5.1).
                "pointer-coarse:after:absolute pointer-coarse:after:-inset-y-1.5 pointer-coarse:after:inset-x-0",
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

/** Ghi chú khi bản nháp HSA vừa chuyển mức Tham khảo đã lưu sang Bắt buộc: chưa lưu thì chưa áp dụng. */
export function HsaNormalizedNote() {
  const t = useTranslations("exams.allocation.enforcement");
  return (
    <p role="status" className="flex items-start gap-2 rounded-md border border-info/30 bg-info-bg px-3 py-2 text-body text-info">
      <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
      {t("normalized")}
    </p>
  );
}
