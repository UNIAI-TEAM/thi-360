"use client";

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  notify,
  Select,
  StatusChip,
} from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { matchingTemplates } from "../create-exam-schema";
import { useApplyExamTemplate } from "../queries";
import { ExamsApiError } from "../review-api";
import type { ExamDetail, ExamPart, ExamTemplate, StructureOverviewPolicy } from "../types";
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

/** Tên phiên bản hiện trong ô chọn: nhãn của máy chủ (`label`), mẫu cũ chưa có nhãn dùng tên dựng theo kỳ thi và năm. */
function versionLabel(template: ExamTemplate): string {
  return template.label?.trim() || template.name;
}

/**
 * Phiên bản cấu trúc của bài (spec 15.2 vùng (a), 5.4, K8): các mẫu khớp kỳ thi, phạm vi, phần hoặc môn, kèm nhãn và mô
 * tả của máy chủ; ẩn khi chỉ có một phiên bản. Đổi phiên bản qua hai bước: (1) hộp thoại liệt kê các phần sẽ nạp lại,
 * (2) nếu máy chủ trả 409 `EXAM_OVERVIEW_CONFIRM` (tổng quan đã sửa tay khác mẫu mới) thì hỏi giữ số đã sửa hay lấy số
 * của mẫu, rồi gửi lại kèm `overviewPolicy`. Hủy ở bước nào thì cấu trúc giữ nguyên.
 */
export function StructureVersionPicker({ exam, templates, dirty, onApplied, onAnnounce }: StructureVersionPickerProps) {
  const t = useTranslations("exams.structure.version");
  const apply = useApplyExamTemplate(exam.id);
  const [pending, setPending] = useState<ExamTemplate | null>(null);
  const [askOverview, setAskOverview] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const available = matchingTemplates(templates, {
    kind: exam.kind,
    scope: exam.scope,
    partCode: exam.partCode ?? "",
    subjectCode: exam.subjectCode ?? "",
  });
  // Chỉ có một phiên bản thì không có gì để chọn: ẩn ô (spec 15.2).
  if (available.length < 2) return null;

  const current = available.find((template) => template.code === exam.templateCode);
  const close = () => {
    setPending(null);
    setAskOverview(false);
    setError(null);
  };

  const submit = (template: ExamTemplate, overviewPolicy?: StructureOverviewPolicy) => {
    setError(null);
    apply.mutate(
      {
        templateCode: template.code,
        confirmReplace: true,
        overviewPolicy: overviewPolicy ?? null,
        expectedRevision: exam.revision,
      },
      {
        onSuccess: (updated) => {
          const message = t("reloaded", { name: versionLabel(template) });
          notify.success(message);
          onAnnounce(message);
          onApplied(updated.parts);
          close();
        },
        onError: (failure) => {
          if (failure instanceof ExamsApiError && failure.status === 409 && failure.code === "EXAM_OVERVIEW_CONFIRM") {
            setAskOverview(true);
            return;
          }
          setAskOverview(false);
          setError(t("reloadError"));
        },
      },
    );
  };

  return (
    <div className="flex flex-col gap-1" data-testid="structure-version">
      <FormField label={t("label")} hint={t("hint", { count: available.length })} className="max-w-xl">
        {(controlProps) => (
          <Select
            {...controlProps}
            value={current?.code}
            placeholder={t("placeholder")}
            onValueChange={(code) => {
              const next = available.find((template) => template.code === code);
              if (next && next.code !== exam.templateCode) setPending(next);
            }}
            options={available.map((template) => ({ value: template.code, label: versionLabel(template) }))}
          />
        )}
      </FormField>
      {current && (current.summary || current.expertNote) && (
        <div className="flex flex-wrap items-center gap-2 text-caption text-text-muted" data-testid="structure-version-summary">
          {current.summary && <span>{current.summary}</span>}
          {current.expertNote && <StatusChip tone="warning">{t("expertPending")}</StatusChip>}
        </div>
      )}
      <ReloadStructureDialog
        open={pending !== null && !askOverview}
        onOpenChange={(open) => {
          if (!open) close();
        }}
        title={t("reloadTitle", { name: pending ? versionLabel(pending) : "" })}
        description={dirty ? t("reloadDescriptionDirty") : t("reloadDescription")}
        parts={pending ? rowsOf(pending) : []}
        confirmLabel={t("reloadConfirm")}
        loading={apply.isPending}
        error={error}
        onConfirm={() => {
          if (pending) submit(pending);
        }}
      />
      <Dialog
        open={pending !== null && askOverview}
        onOpenChange={(open) => {
          if (!open && !apply.isPending) close();
        }}
      >
        <DialogContent size="md" closeLabel={t("overviewClose")} data-testid="overview-confirm-dialog">
          <DialogHeader>
            <DialogTitle>{t("overviewTitle")}</DialogTitle>
            <DialogDescription>{t("overviewDescription")}</DialogDescription>
          </DialogHeader>
          {error && (
            <p role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-body text-danger">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="secondary" disabled={apply.isPending} onClick={close}>
              {t("overviewCancel")}
            </Button>
            <Button
              type="button"
              variant="secondary"
              disabled={apply.isPending}
              onClick={() => pending && submit(pending, "USE_TEMPLATE")}
            >
              {t("overviewUseTemplate")}
            </Button>
            <Button
              type="button"
              loading={apply.isPending}
              onClick={() => pending && submit(pending, "KEEP_EDITED")}
            >
              {t("overviewKeepEdited")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
