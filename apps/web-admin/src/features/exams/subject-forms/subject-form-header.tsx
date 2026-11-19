"use client";

import { Badge, Card, cn, FormField, Input, StatusChip } from "@thi360/ui";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { EditOverviewLink } from "../components/edit-overview-link";
import { LOCKED_FIELD } from "../components/locked-field";
import { LockReason } from "./lock-reason";

export interface SubjectFormHeaderProps {
  subjectName: string;
  /** Nhóm của môn trong phương án chọn môn (Tự nhiên, Xã hội); `null` ngoài phương án đó. */
  choiceGroupLabel?: string | null;
  /** Số câu của mục (chỉ đọc, sửa ở Thông tin tổng quan); `null` khi chưa biết. */
  count: number | null;
  /** Số câu đã phân bổ vào các nhóm. */
  allocated: number;
  examId: string;
  /** Mẫu khóa số câu của mục (H27). */
  templateLocked?: boolean;
  /** Nút bên phải đầu môn (Kiểm tra môn). */
  action?: ReactNode;
  children?: ReactNode;
}

/**
 * Đầu môn dùng chung của MT và DS (FTM R-7, R-15): tên môn, "Đã phân bổ x/y câu" kèm thiếu hoặc vượt (chữ, không chỉ màu), số câu
 * chỉ đọc kèm đường dẫn "Sửa thông tin tổng quan". Dùng cùng khóa chữ với màn môn mặc định để hai màn không lệch nhau.
 */
export function SubjectFormHeader({ subjectName, choiceGroupLabel = null, count, allocated, examId, templateLocked = false, action, children }: SubjectFormHeaderProps) {
  const t = useTranslations("exams.structure.subject");
  const tGap = useTranslations("exams.structure.gap");
  const missing = count === null ? null : count - allocated;

  return (
    <Card padding="md" className="flex flex-col gap-6 sm:p-6" data-testid="subject-form-header">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 className="flex flex-wrap items-center gap-2 text-h3 font-bold wrap-break-word text-text">
            {t("title", { subject: subjectName })}
            {choiceGroupLabel && <Badge className="font-medium">{choiceGroupLabel}</Badge>}
          </h2>
          <p className="text-caption text-text-muted">{count === null ? t("countUnknown") : t("countFixed", { count })}</p>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-3">
          <p role="status" className="flex flex-wrap items-center gap-2 text-body">
            <span className="text-text-muted">{t("allocated")}</span>
            <span className="font-semibold text-text tabular-nums">
              {count === null ? t("allocatedUnknown", { allocated }) : t("allocatedValue", { allocated, count })}
            </span>
            {missing !== null && (
              <StatusChip tone={missing === 0 ? "success" : missing < 0 ? "danger" : "warning"}>
                {missing === 0 ? t("matches") : missing > 0 ? tGap("under", { count: missing }) : tGap("over", { count: -missing })}
              </StatusChip>
            )}
          </p>
          {action}
        </div>
      </div>
      {count !== null && (
        <div className="flex max-w-md flex-col gap-1.5">
          <FormField label={t("countField.label")} hint={templateLocked ? undefined : t("countField.hint")}>
            {(controlProps) => (
              <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <Input {...controlProps} value={String(count)} readOnly aria-readonly="true" className={cn("w-24 tabular-nums", LOCKED_FIELD)} />
                {templateLocked && <LockReason code="templateTotal" />}
                <EditOverviewLink examId={examId} className="h-auto p-0" />
              </span>
            )}
          </FormField>
        </div>
      )}
      {children}
    </Card>
  );
}
