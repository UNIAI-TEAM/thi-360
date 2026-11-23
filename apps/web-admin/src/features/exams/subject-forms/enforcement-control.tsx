"use client";

import { Chip, cn, focusRing, Switch } from "@thi360/ui";
import { Info } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, type ReactNode } from "react";
import { canRequire } from "../allocation";
import { richNodes } from "../components/exam-review-shared";
import { hsaFixedEnforcement, useHsaExam } from "../hsa-enforcement";
import type { QuotaBasis, QuotaEnforcement } from "../types";
import { LockReason, type LockReasonCode } from "./lock-reason";
import type { SubjectFormEntry } from "./registry";

interface SubjectEnforcementProps {
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

interface ToggleEnforcementProps {
  mode: "TOGGLE";
  /** Tên bảng đứng trước nhãn, để đọc màn hình phân biệt các nhóm radio cùng trang. */
  legend: ReactNode;
  name: string;
  value: QuotaEnforcement;
  onChange: (value: QuotaEnforcement) => void;
  basis?: QuotaBasis;
  lockRequiredReason?: string;
  /** Chỉ tiêu chờ chuyên gia xác nhận (HSA Tiếng Anh): trong bài HSA cố định Tham khảo kèm lý do chờ chuyên gia. */
  expertPending?: boolean;
  /** Chiều ma trận trống mà lớp chuẩn hóa giữ nguyên Tham khảo (R1-2): trong bài HSA chip khớp bản nháp Tham khảo kèm lý do chưa khai báo. */
  undeclared?: boolean;
  disabled?: boolean;
}

type EnforcementControlProps = SubjectEnforcementProps | ToggleEnforcementProps;

export function EnforcementControl(props: EnforcementControlProps) {
  return props.mode === "TOGGLE" ? <ToggleEnforcement {...props} /> : <SubjectEnforcement {...props} />;
}

function SubjectEnforcement({ mode, name, value, onChange, declared = true, lockReason }: SubjectEnforcementProps) {
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

function ToggleEnforcement({
  legend,
  name,
  value,
  onChange,
  basis,
  lockRequiredReason,
  expertPending = false,
  undeclared = false,
  disabled = false,
}: ToggleEnforcementProps) {
  const t = useTranslations("exams.allocation.enforcement");
  const reasonId = useId();
  const legendId = useId();
  const hsaExam = useHsaExam();
  const reason = lockRequiredReason ?? (!canRequire(basis) ? t("basisLocked") : null);
  const requiredLocked = reason !== null;
  // Chiều chưa khai báo vẫn ở Tham khảo trong bản nháp (R1-2): chip khớp bản nháp thay vì mức Bắt buộc công thức.
  const fixed = hsaExam ? (expertPending || undeclared ? "REFERENCE" : hsaFixedEnforcement(basis, requiredLocked)) : undefined;

  if (fixed === "REQUIRED") return null;
  if (fixed !== undefined) {
    const caption = expertPending ? t("expertPending") : (reason ?? (undeclared ? t("undeclared") : null));
    const plainLegend = typeof legend === "string";
    return (
      <div
        role="group"
        aria-label={plainLegend ? t("legend", { name: legend }) : undefined}
        aria-labelledby={plainLegend ? undefined : legendId}
        aria-describedby={caption ? reasonId : undefined}
        data-fixed={fixed}
        className="flex min-w-0 flex-col gap-1"
      >
        {!plainLegend && (
          <span id={legendId} className="sr-only">
            {richNodes(t, "legend", { name: legend })}
          </span>
        )}
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
      <legend className="sr-only">{richNodes(t, "legend", { name: legend })}</legend>
      <div className="inline-flex w-fit rounded-md border border-border bg-subtle p-0.5">
        {options.map((option) => {
          const checked = option.value === value;
          return (
            <label
              key={option.value}
              className={cn(
                "relative flex min-h-8 cursor-pointer items-center gap-2 rounded-sm px-3 text-label font-medium transition-colors duration-150 motion-reduce:transition-none",
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
