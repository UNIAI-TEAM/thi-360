"use client";

import {
  Button,
  cn,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  focusRing,
} from "@thi360/ui";
import { useFormatter, useTranslations } from "next-intl";
import { useState } from "react";
import type { ExamDetail, ExamPart, ExamTemplate, StructureOverviewPolicy } from "../types";

export interface StructureOverviewConfirmDialogProps {
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

/**
 * Bước 2/2 của đổi phiên bản cấu trúc (bản vẽ `DoiPhienBan2B`), chỉ hiện khi máy chủ trả 409 `EXAM_OVERVIEW_CONFIRM`
 * (tổng quan đã sửa tay khác mẫu mới): bảng so sánh số của mẫu đang dùng, của bài hiện tại và của mẫu mới (chỉ hàng có số
 * từ máy chủ), rồi hai lựa chọn giữ số đã sửa hoặc lấy số của mẫu mới.
 */
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
  const options: Array<{ value: StructureOverviewPolicy; label: string; hint: string }> = [
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
    <Dialog open={open} onOpenChange={(next) => (!next && !loading ? onCancel() : undefined)}>
      <DialogContent size="lg" closeLabel={t("close")} data-testid="overview-confirm-dialog">
        <DialogHeader>
          <p className="text-caption font-semibold text-text-muted">{t("step")}</p>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>
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
        <fieldset className="flex flex-col gap-2" disabled={loading}>
          <legend className="sr-only">{t("legend")}</legend>
          {options.map((option) => (
            <label
              key={option.value}
              className={cn(
                "flex cursor-pointer items-start gap-2.5 rounded-md border p-3 transition-colors duration-150 motion-reduce:transition-none",
                policy === option.value ? "border-primary-text bg-primary-50" : "border-border bg-surface hover:border-border-strong",
              )}
            >
              <input
                type="radio"
                name="overview-policy"
                value={option.value}
                checked={policy === option.value}
                onChange={() => setPolicy(option.value)}
                className={cn("mt-0.5 size-4 shrink-0 accent-primary-fill", focusRing)}
              />
              <span className="flex min-w-0 flex-col gap-0.5">
                <span className="font-semibold text-text">{option.label}</span>
                <span className="text-text-muted">{option.hint}</span>
              </span>
            </label>
          ))}
        </fieldset>
        {error && (
          <p role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-body text-danger">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="secondary" disabled={loading} onClick={onCancel}>
            {t("cancel")}
          </Button>
          <Button type="button" loading={loading} onClick={() => onConfirm(policy)}>
            {t("confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
