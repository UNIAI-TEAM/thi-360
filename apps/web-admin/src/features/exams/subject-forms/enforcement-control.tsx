"use client";

import { Chip, Switch } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId, type ReactNode } from "react";
import type { QuotaEnforcement } from "../types";
import { LockReason, type LockReasonCode } from "./lock-reason";
import type { SubjectFormEntry } from "./registry";

export interface EnforcementControlProps {
  /** Cách áp dụng của kỳ (`SubjectFormEntry.enforcement`, FTM R-8). */
  mode: SubjectFormEntry["enforcement"];
  /** Tên bảng đứng trong nhãn nhóm, để đọc màn hình phân biệt các phân bố cùng trang. */
  name: string;
  /** Mức đang lưu của phân bố. */
  value: QuotaEnforcement;
  /** Chỉ dùng với `REFERENCE_WITH_APPLY`: bật hoặc tắt "Áp dụng khi sinh đề". */
  onChange?: (value: QuotaEnforcement) => void;
  /** `DECLARED_REQUIRED`: chiều đã khai ít nhất một ô (Bắt buộc); chiều để trống không áp. */
  declared?: boolean;
  /** Lý do khóa nút áp dụng: phân bố theo lệnh hỏi, hoặc chỉ tiêu nhiều môn (chỉ đối chiếu). */
  lockReason?: Extract<LockReasonCode, "promptBasis" | "multiSubject">;
}

/**
 * Mức áp dụng của một phân bố theo kỳ (FTM R-8), không có nút Bắt buộc/Tham khảo chung: HSA chip Bắt buộc, BCA chip Tham khảo, QDA và
 * THPTQG chip theo chiều đã khai; các kỳ còn lại mặc định Chỉ đối chiếu kèm nút "Áp dụng khi sinh đề" (khóa có lý do chữ).
 */
export function EnforcementControl({ mode, name, value, onChange, declared = true, lockReason }: EnforcementControlProps) {
  const t = useTranslations("exams.subjectForm.common.enforcement");
  const switchId = useId();
  const reasonId = useId();

  const group = (children: ReactNode) => (
    <div role="group" aria-label={t("legend", { name })} data-enforcement-mode={mode} className="flex min-w-0 flex-col gap-1">
      {children}
    </div>
  );

  if (mode === "FIXED_REQUIRED") return group(<Chip variant="tag" className="w-fit">{t("required")}</Chip>);
  if (mode === "FIXED_REFERENCE") return group(<Chip variant="tag" className="w-fit">{t("reference")}</Chip>);
  if (mode === "DECLARED_REQUIRED") {
    return group(<Chip variant="tag" className="w-fit">{declared ? t("required") : t("notDeclared")}</Chip>);
  }

  const applied = value === "REQUIRED";
  return group(
    <>
      <Chip variant="tag" className="w-fit">
        {applied ? t("required") : t("referenceOnly")}
      </Chip>
      <span className="flex flex-wrap items-center gap-2">
        <Switch
          id={switchId}
          checked={applied}
          disabled={lockReason !== undefined}
          aria-describedby={lockReason ? reasonId : undefined}
          onCheckedChange={(checked) => onChange?.(checked ? "REQUIRED" : "REFERENCE")}
        />
        <label htmlFor={switchId} className="text-label font-medium text-text-secondary">
          {t("apply")}
        </label>
      </span>
      {lockReason && <LockReason code={lockReason} id={reasonId} />}
    </>,
  );
}
