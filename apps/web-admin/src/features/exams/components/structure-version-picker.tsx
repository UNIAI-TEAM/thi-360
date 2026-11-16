"use client";

import { FormField, notify, Select } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { matchingTemplates } from "../create-exam-schema";
import { useApplyExamTemplate } from "../queries";
import type { ExamDetail, ExamPart, ExamTemplate } from "../types";
import { ReloadStructureDialog, type ReloadPartRow } from "./reload-structure-dialog";

export interface StructureVersionPickerProps {
  exam: ExamDetail;
  templates: readonly ExamTemplate[];
  /** Bản nháp đang có thay đổi chưa lưu: sẽ mất khi nạp lại. */
  dirty: boolean;
  /** Cấu trúc mới sau khi nạp (thay bản nháp của màn). */
  onApplied: (parts: ExamPart[]) => void;
  onAnnounce: (message: string) => void;
}

function rowsOf(template: ExamTemplate): ReloadPartRow[] {
  return template.parts.map((part) => ({
    name: part.name,
    questionCount: part.questionCount,
    durationMinutes: part.durationMinutes,
    shared: template.timing === "SHARED",
  }));
}

/**
 * Phiên bản năm của cấu trúc (PBI-04 mục 5.1): các mẫu khớp kỳ thi, phạm vi, phần hoặc môn của bài. Đổi phiên bản thì
 * hộp thoại liệt kê các phần sẽ nạp lại và chỉ thay cấu trúc sau khi quản trị viên xác nhận (mục 5.3).
 */
export function StructureVersionPicker({ exam, templates, dirty, onApplied, onAnnounce }: StructureVersionPickerProps) {
  const t = useTranslations("exams.structure.version");
  const apply = useApplyExamTemplate(exam.id);
  const [pending, setPending] = useState<ExamTemplate | null>(null);
  const [error, setError] = useState<string | null>(null);

  const available = matchingTemplates(templates, {
    kind: exam.kind,
    scope: exam.scope,
    partCode: exam.partCode ?? "",
    subjectCode: exam.subjectCode ?? "",
  });
  if (available.length === 0) return null;

  const current = available.find((template) => template.code === exam.templateCode);
  const close = () => {
    setPending(null);
    setError(null);
  };

  return (
    <div className="flex flex-col gap-1" data-testid="structure-version">
      <FormField
        label={t("label")}
        hint={available.length > 1 ? t("hint", { count: available.length }) : t("hintSingle")}
        className="max-w-xl"
      >
        {(controlProps) => (
          <Select
            {...controlProps}
            value={current?.code}
            placeholder={t("placeholder")}
            disabled={available.length < 2}
            onValueChange={(code) => {
              const next = available.find((template) => template.code === code);
              if (next && next.code !== exam.templateCode) setPending(next);
            }}
            options={available.map((template) => ({ value: template.code, label: template.name }))}
          />
        )}
      </FormField>
      <ReloadStructureDialog
        open={pending !== null}
        onOpenChange={(open) => {
          if (!open) close();
        }}
        title={t("reloadTitle", { name: pending?.name ?? "" })}
        description={dirty ? t("reloadDescriptionDirty") : t("reloadDescription")}
        parts={pending ? rowsOf(pending) : []}
        confirmLabel={t("reloadConfirm")}
        loading={apply.isPending}
        error={error}
        onConfirm={() => {
          if (!pending) return;
          setError(null);
          apply.mutate(
            { templateCode: pending.code, confirmReplace: true, expectedRevision: exam.revision },
            {
              onSuccess: (updated) => {
                const message = t("reloaded", { name: pending.name });
                notify.success(message);
                onAnnounce(message);
                onApplied(updated.parts);
                close();
              },
              onError: () => setError(t("reloadError")),
            },
          );
        }}
      />
    </div>
  );
}
