"use client";

import { cn, focusRing } from "@thi360/ui";
import { useFormatter, useTranslations } from "next-intl";
import { useState } from "react";
import type { ExamDetail, ExamPart, ExamTemplate, StructureOverviewPolicy } from "../types";
import { ChoiceCardGroup, type ChoiceCardOption } from "./choice-card-group";
import { ReloadDialogShell } from "./reload-structure-dialog";

interface StructureOverviewConfirmDialogProps {
  open: boolean;
  exam: ExamDetail;
  /** Mẫu đang dùng của bài; `undefined` khi mẫu không còn trong danh sách. */
  currentTemplate: ExamTemplate | undefined;
  /** Mẫu sẽ nạp. */
  template: ExamTemplate;
  /** Các phần đang có trên màn (kể cả thay đổi chưa lưu), cột "Đang có". */
  currentParts: readonly ExamPart[];
  loading: boolean;
  error: string | null;
  onCancel: () => void;
  onConfirm: (policy: StructureOverviewPolicy) => void;
}

interface ComparisonRow {
  key: string;
  label: string;
  template: number | null;
  edited: number | null;
  next: number | null;
  minutes: boolean;
}

export function StructureOverviewConfirmDialog({
  open,
  exam,
  currentTemplate,
  template,
  currentParts,
  loading,
  error,
  onCancel,
  onConfirm,
}: StructureOverviewConfirmDialogProps) {
  const t = useTranslations("exams.structure.version.overview");
  const format = useFormatter();
  const [policy, setPolicy] = useState<StructureOverviewPolicy>("KEEP_EDITED");

  const rows: ComparisonRow[] = [
    {
      key: "duration",
      label: t("rowDuration"),
      template: currentTemplate?.durationMinutes ?? null,
      edited: exam.durationMinutes,
      next: template.durationMinutes,
      minutes: true,
    },
    ...Array.from({ length: Math.max(template.parts.length, currentParts.length) }, (_, index): ComparisonRow => ({
      key: `part-${index}`,
      label: t("rowQuestions", { part: template.parts[index]?.name ?? currentParts[index]?.name ?? "" }),
      template: currentTemplate?.parts[index]?.questionCount ?? null,
      edited: currentParts[index]?.questionCount ?? null,
      next: template.parts[index]?.questionCount ?? null,
      minutes: false,
    })),
    {
      key: "score",
      label: t("rowMaxScore"),
      template: currentTemplate?.maxScore ?? null,
      edited: exam.maxScore,
      next: template.maxScore,
      minutes: false,
    },
  ].filter((row) => row.template !== null || row.edited !== null || row.next !== null);

  const cell = (value: number | null, minutes: boolean) =>
    value === null ? "—" : minutes ? t("valueMinutes", { count: value }) : format.number(value, { maximumFractionDigits: 2 });
  const options: ChoiceCardOption<StructureOverviewPolicy>[] = [
    {
      value: "KEEP_EDITED",
      label: t("keepEdited"),
      hint: t("keepEditedHint", { minutes: exam.durationMinutes }),
    },
    {
      value: "USE_TEMPLATE",
      label: t("useTemplate"),
      hint: t("useTemplateHint", {
        name: template.label?.trim() || template.name,
        hasMinutes: template.durationMinutes === null ? "no" : "yes",
        minutes: template.durationMinutes ?? 0,
      }),
    },
  ];

  return (
    <ReloadDialogShell
      open={open}
      onOpenChange={(next) => {
        if (!next) onCancel();
      }}
      size="lg"
      closeLabel={t("close")}
      testId="overview-confirm-dialog"
      step={t("step")}
      title={t("title")}
      description={t("description")}
      cancelLabel={t("cancel")}
      confirmLabel={t("confirm")}
      loading={loading}
      error={error}
      onConfirm={() => onConfirm(policy)}
    >
      {rows.length > 0 && (
        <div role="region" tabIndex={0} aria-label={t("tableCaption")} className={cn("overflow-x-auto rounded-md border border-border", focusRing)}>
          <table className="w-full min-w-[32rem] border-collapse text-body tabular-nums">
            <caption className="sr-only">{t("tableCaption")}</caption>
            <thead>
              <tr className="bg-subtle text-left text-caption text-text-muted">
                <th scope="col" className="px-3 py-2 font-semibold">{t("columnItem")}</th>
                <th scope="col" className="px-2 py-2 text-right font-semibold">{t("columnTemplate")}</th>
                <th scope="col" className="px-2 py-2 text-right font-semibold">{t("columnEdited")}</th>
                <th scope="col" className="px-3 py-2 text-right font-semibold">{t("columnNext")}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const changed = row.template !== null && row.edited !== null && row.template !== row.edited;
                return (
                  <tr key={row.key} className="border-t border-border">
                    <th scope="row" className="px-3 py-2 text-left font-semibold text-text">{row.label}</th>
                    <td className="px-2 py-2 text-right text-text-secondary">{cell(row.template, row.minutes)}</td>
                    <td className={cn("px-2 py-2 text-right", changed ? "font-bold text-warning" : "text-text-secondary")}>
                      {cell(row.edited, row.minutes)}
                      {changed && <span> · {t("edited")}</span>}
                    </td>
                    <td className="px-3 py-2 text-right text-text-secondary">{cell(row.next, row.minutes)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      <ChoiceCardGroup
        layout="stack"
        ariaLabel={t("legend")}
        name="overview-policy"
        value={policy}
        onChange={setPolicy}
        disabled={loading}
        options={options}
      />
    </ReloadDialogShell>
  );
}
