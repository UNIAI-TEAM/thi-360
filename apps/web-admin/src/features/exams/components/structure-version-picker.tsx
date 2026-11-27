"use client";

import { FormField, notify, Select, StatusChip } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { matchingTemplates } from "../create-exam-schema";
import { partTypeCounts } from "../part-type-counts";
import { useApplyExamTemplate } from "../queries";
import { ExamsApiError } from "../errors";
import type { ExamDetail, ExamPart, ExamTemplate, StructureOverviewPolicy } from "../types";
import { ExamName, richNodes } from "./exam-review-shared";
import { ReloadStructureDialog, type ReloadPartRow } from "./reload-structure-dialog";
import { StructureOverviewConfirmDialog } from "./structure-overview-confirm-dialog";

interface StructureVersionPickerProps {
  exam: ExamDetail;
  templates: readonly ExamTemplate[];
  /** Bản nháp đang có thay đổi chưa lưu: sẽ mất khi nạp lại. */
  dirty: boolean;
  /** Các phần đang có trên màn (kể cả thay đổi chưa lưu): cột "trước" của danh sách nạp lại và cột "Đang có" của bước 2. */
  currentParts: readonly ExamPart[];
  /** Cấu trúc mới sau khi nạp (thay bản nháp của màn). */
  onApplied: (parts: ExamPart[]) => void;
  onAnnounce: (message: string) => void;
}

/** Các phần sẽ nạp, kèm phần tương ứng (cùng vị trí) của cấu trúc hiện tại; chỉ dùng số mà máy chủ và màn đã có. */
function rowsOf(template: ExamTemplate, currentParts: readonly ExamPart[]): ReloadPartRow[] {
  return template.parts.map((part, index) => {
    const before = currentParts[index];
    return {
      name: part.name,
      questionCount: part.questionCount,
      durationMinutes: part.durationMinutes,
      shared: template.timing === "SHARED",
      typeCounts: part.questionTypes,
      before: before
        ? {
            name: before.name,
            questionCount: before.questionCount,
            durationMinutes: before.durationMinutes,
            shared: before.durationMinutes === null,
            typeCounts: partTypeCounts(before),
          }
        : null,
    };
  });
}

/** Kỳ thi bỏ khối "Phiên bản cấu trúc" ở bước 2 (UNI-2876, UNI-1734): bài luôn theo mẫu mặc định của kỳ. */
const VERSION_BLOCK_HIDDEN_KINDS: ReadonlySet<string> = new Set(["SPT", "SP2E"]);

/** Bước 2 có hiện khối "Phiên bản cấu trúc" (ô chọn mẫu, tóm tắt, "Nếu chọn …") và ghi chú của mẫu cho kỳ thi này không. */
export function showsStructureVersion(kind: string): boolean {
  return !VERSION_BLOCK_HIDDEN_KINDS.has(kind);
}

/** Tên phiên bản hiện trong ô chọn: nhãn của máy chủ (`label`), mẫu cũ chưa có nhãn dùng tên dựng theo kỳ thi và năm. */
function versionLabel(template: ExamTemplate): string {
  return template.label?.trim() || template.name;
}

export function StructureVersionPicker({ exam, templates, dirty, currentParts, onApplied, onAnnounce }: StructureVersionPickerProps) {
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
  // Chỉ có một phiên bản thì không có gì để chọn: ẩn ô (spec 15.2). SPT, SP2E không có khối này.
  if (available.length < 2 || !showsStructureVersion(exam.kind)) return null;

  const current = available.find((template) => template.code === exam.templateCode);
  // "Nếu chọn …": phiên bản khác kèm tóm tắt của máy chủ; phiên bản chưa có tóm tắt thì không hiện (không tự dựng).
  const previews = available.filter((template) => template.code !== exam.templateCode && template.summary?.trim());
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
    <div className="flex flex-col gap-3" data-testid="structure-version">
      <div className="flex flex-wrap items-start gap-x-6 gap-y-3">
        <div className="flex min-w-0 flex-[1_1_21rem] flex-col gap-1">
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
                options={available.map((template) => ({
                  value: template.code,
                  label: <ExamName>{versionLabel(template)}</ExamName>,
                  textValue: versionLabel(template),
                }))}
              />
            )}
          </FormField>
          {current && (current.summary || current.expertNote) && (
            <div className="flex flex-wrap items-center gap-2 text-caption text-text-muted" data-testid="structure-version-summary">
              {current.summary && <ExamName>{current.summary}</ExamName>}
              {current.expertNote && <StatusChip tone="warning">{t("expertPending")}</StatusChip>}
            </div>
          )}
        </div>
        {previews.map((template) => (
          <div key={template.code} className="flex min-w-0 flex-[1_1_18rem] flex-col gap-1" data-testid="structure-version-preview">
            <span className="text-caption text-text-muted">
              {richNodes(t, "previewTitle", { name: <ExamName>{versionLabel(template)}</ExamName> })}
            </span>
            <span className="text-body text-text-secondary">
              <ExamName>{template.summary}</ExamName>
            </span>
          </div>
        ))}
      </div>
      <ReloadStructureDialog
        open={pending !== null && !askOverview}
        onOpenChange={(open) => {
          if (!open) close();
        }}
        step={t("step", { step: 1 })}
        title={richNodes(t, "reloadTitle", { name: pending ? <ExamName>{versionLabel(pending)}</ExamName> : "" })}
        description={t("reloadDescription")}
        parts={pending ? rowsOf(pending, currentParts) : []}
        note={dirty ? t("reloadNoteDirty") : t("reloadNote")}
        confirmLabel={t("reloadConfirm")}
        cancelLabel={t("reloadCancel")}
        loading={apply.isPending}
        error={error}
        onConfirm={() => {
          if (pending) submit(pending);
        }}
      />
      {pending !== null && askOverview && (
        <StructureOverviewConfirmDialog
          open
          exam={exam}
          currentTemplate={current}
          template={pending}
          currentParts={currentParts}
          loading={apply.isPending}
          error={error}
          onCancel={close}
          onConfirm={(policy) => submit(pending, policy)}
        />
      )}
    </div>
  );
}
