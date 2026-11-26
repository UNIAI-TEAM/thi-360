"use client";

import { Button, cn, focusRingInset, Skeleton, StatusChip } from "@thi360/ui";
import { CheckCircle2, CircleCheck, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId } from "react";
import { DISPLAY_NUMBER_FORMAT, roundForDisplay } from "../display-number";
import { type StructureIssue, sum } from "../matrix";
import { structureIssueBlockField } from "./block-form-fields";
import type { ExamCheckResult } from "../review-types";
import type { ExamPart, ExamQuota, ExamStructureSummary, ExamStructureSummaryRow, StructureCombination } from "../types";
import { AllocationGap } from "./allocation-gap";
import type { StructureSelection } from "./exam-structure-draft";
import { issueTarget } from "./exam-structure-issues";
import { ExamStructureCheckResult, type CheckResultNames } from "./exam-structure-check-result";
import type { QuickCheckItem } from "./exam-structure-quick-check";
import { requestBlockFieldFocus } from "./block-field-focus";
import { ExamName, labelBeforeName } from "./exam-review-shared";

export type SummaryLoad = "loading" | "ready" | "unavailable";

function summaryRowTarget(row: ExamStructureSummaryRow): StructureSelection | null {
  if (row.blockId) return { kind: "block", blockId: row.blockId };
  if (row.sectionId) return { kind: "section", sectionId: row.sectionId };
  if (row.partId) return { kind: "part", partId: row.partId };
  return null;
}

type Headline = { state: "ok" | "missing" | "over"; allocated: number; target: number; missing: number; extra: number };

function headlineOf(allocated: number, target: number): Headline {
  const rounded = { allocated: roundForDisplay(allocated), target: roundForDisplay(target) };
  const missing = roundForDisplay(Math.max(0, rounded.target - rounded.allocated));
  const extra = roundForDisplay(Math.max(0, rounded.allocated - rounded.target));
  return { state: missing > 0 ? "missing" : extra > 0 ? "over" : "ok", ...rounded, missing, extra };
}

function computeHeadline(summary: ExamStructureSummary | null, local: { allocated: number; total: number }): Headline {
  const partRows = summary?.rows.filter((row) => row.partId && !row.sectionId && !row.blockId) ?? [];
  return summary && summary.rows.length > 0
    ? headlineOf(
        sum(partRows.map((row) => row.allocated ?? 0)),
        sum(partRows.map((row) => row.target ?? 0)),
      )
    : headlineOf(local.allocated, local.total);
}

export interface ExamStructureStatusStripProps {
  summary: ExamStructureSummary | null;
  load: SummaryLoad;
  local: { allocated: number; total: number; missing: number };
  statusNote: string;
  hasUnsavedChanges: boolean;
  unsavedLabel: string;
  issueCount: number;
  canOpenOverview: boolean;
  onOpenOverview: () => void;
}

/** Thanh trạng thái gọn 1 dòng ở đầu khung Cấu trúc: báo tiến độ phân bổ câu toàn bài và trạng thái lưu. */
export function ExamStructureStatusStrip({
  summary,
  load,
  local,
  statusNote,
  hasUnsavedChanges,
  unsavedLabel,
  issueCount,
  canOpenOverview,
  onOpenOverview,
}: ExamStructureStatusStripProps) {
  const t = useTranslations("exams.structure.summaryPanel");
  const structureT = useTranslations("exams.structure");
  const headline = computeHeadline(summary, local);

  return (
    <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">
      {load === "loading" ? (
        <Skeleton className="h-5 w-56 max-w-full" />
      ) : (
        <p
          data-testid="structure-headline"
          className={cn(
            "inline-flex items-center gap-1.5 text-body font-semibold",
            headline.state === "ok" ? "text-text" : "text-warning",
          )}
        >
          {headline.state === "ok" ? (
            <CheckCircle2 aria-hidden className="size-4 shrink-0 text-success" />
          ) : (
            <TriangleAlert aria-hidden className="size-4 shrink-0 text-warning" />
          )}
          <span>{t("headline", headline)}</span>
        </p>
      )}
      {hasUnsavedChanges && <StatusChip tone="warning">{unsavedLabel}</StatusChip>}
      {issueCount > 0 && <StatusChip tone="warning">{structureT("needsCompletionChip")}</StatusChip>}
      <span className="text-caption text-text-muted">{statusNote}</span>
      {load === "unavailable" && <span className="text-caption text-text-muted">· {t("unavailable")}</span>}
      {canOpenOverview && (
        <Button type="button" variant="link" size="sm" className="h-auto p-0 text-caption" onClick={onOpenOverview}>
          {t("openOverview")}
        </Button>
      )}
      {issueCount === 0 && (
        <h3 id="structure-issues-title" className="sr-only">
          {structureT("issuesSummary", { count: 0 })}
        </h3>
      )}
    </div>
  );
}

export interface ExamStructureAlertsBannerProps {
  issues: readonly StructureIssue[];
  issueText: (issue: StructureIssue) => string;
  checkResult: ExamCheckResult | null;
  quickCheck?: {
    open: boolean;
    items: readonly QuickCheckItem[];
    onGoTo: (item: QuickCheckItem) => void;
  };
  names: CheckResultNames;
  parts?: ExamPart[];
  quotas?: readonly ExamQuota[];
  combinations?: readonly StructureCombination[];
  onSelect: (selection: StructureSelection) => void;
}

/** Dải cảnh báo/kết quả kiểm tra ngay dưới thanh công cụ: chỉ hiện khi thực sự có lỗi cấu trúc hoặc vừa chạy Kiểm tra. */
export function ExamStructureAlertsBanner({
  issues,
  issueText,
  checkResult,
  quickCheck,
  names,
  parts,
  quotas,
  combinations,
  onSelect,
}: ExamStructureAlertsBannerProps) {
  const structureT = useTranslations("exams.structure");
  const showQuick = Boolean(quickCheck?.open);
  const showDraftIssues = !showQuick && issues.length > 0;
  if (!showQuick && !showDraftIssues && !checkResult) return null;

  const openIssue = (issue: StructureIssue) => {
    const field = issue.code === "BLOCK" ? structureIssueBlockField(issue.issue) : null;
    if (issue.code === "BLOCK" && field) requestBlockFieldFocus(issue.blockId, field);
    onSelect(issueTarget(issue));
  };

  return (
    <div className="flex flex-col gap-3 border-b border-border bg-subtle/35 px-4 py-3 sm:px-5">
      {showQuick && quickCheck && (
        <section
          aria-labelledby="structure-quick-check-title"
          data-testid="structure-quick-check-result"
          className={cn(
            "flex flex-col gap-2 rounded-md border px-3.5 py-2.5",
            quickCheck.items.length === 0 ? "border-success/40 bg-success-bg" : "border-danger/30 bg-danger-bg",
          )}
        >
          <h3 id="structure-quick-check-title" className="flex items-center gap-2 text-label font-semibold text-text">
            {quickCheck.items.length === 0 ? (
              <>
                <CircleCheck aria-hidden className="size-4 shrink-0 text-success" />
                {structureT("quickCheck.valid")}
              </>
            ) : (
              <>
                <TriangleAlert aria-hidden className="size-4 shrink-0 text-danger" />
                {structureT("quickCheck.invalidTitle", { count: quickCheck.items.length })}
              </>
            )}
          </h3>
          {quickCheck.items.length > 0 && (
            <ul className="flex flex-col gap-1.5">
              {quickCheck.items.map((item) => (
                <li key={item.key} className="flex flex-wrap items-start justify-between gap-2 text-body text-text">
                  <span className="min-w-0 flex-1">{item.message}</span>
                  <Button type="button" variant="link" size="sm" className="h-auto p-0" onClick={() => quickCheck.onGoTo(item)}>
                    {structureT("quickCheck.goToFix")}
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
      {showDraftIssues && (
        <section
          aria-labelledby="structure-issues-title"
          className="flex flex-col gap-2 rounded-md border border-warning/40 bg-warning-bg px-3.5 py-2.5"
        >
          <h3 id="structure-issues-title" className="flex items-center gap-2 text-label font-semibold text-text">
            <TriangleAlert aria-hidden className="size-4 shrink-0 text-warning" />
            {structureT("issuesSummary", { count: issues.length })}
          </h3>
          <ul className="flex flex-col gap-1">
            {issues.map((issue, index) => (
              <li key={index} className="flex flex-wrap items-start justify-between gap-2 text-body text-text">
                <span className="min-w-0 flex-1">{issueText(issue)}</span>
                <Button type="button" variant="link" size="sm" className="h-auto p-0" onClick={() => openIssue(issue)}>
                  {structureT("issueGo")}
                </Button>
              </li>
            ))}
          </ul>
        </section>
      )}
      {checkResult && (
        <ExamStructureCheckResult
          result={checkResult}
          names={names}
          parts={parts}
          quotas={quotas}
          combinations={combinations}
          onSelect={onSelect}
        />
      )}
    </div>
  );
}

export interface ExamStructureSummaryTableProps {
  summary: ExamStructureSummary | null;
  load: SummaryLoad;
  dirty: boolean;
  names: Pick<CheckResultNames, "subject">;
  onSelect: (selection: StructureSelection) => void;
}

/** Bảng tổng hợp toàn bài theo phần, môn và nhóm — đặt trong màn Tổng quan bài (`?exam=1`). */
export function ExamStructureSummaryTable({
  summary,
  load,
  dirty,
  names,
  onSelect,
}: ExamStructureSummaryTableProps) {
  const t = useTranslations("exams.structure.summaryPanel");
  const format = useFormatter();
  const idBase = useId();

  if (!summary || summary.rows.length === 0) return null;

  return (
    <section aria-labelledby={`${idBase}-title`} className="flex flex-col gap-2.5">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 id={`${idBase}-title`} className="text-title font-semibold text-text">
          {t("caption")}
        </h3>
        {load === "ready" && dirty && <p className="text-caption text-text-muted">{t("stale")}</p>}
      </div>

      <div
        role="region"
        tabIndex={0}
        aria-label={t("caption")}
        className={cn("relative overflow-x-auto rounded-md border border-border", focusRingInset)}
      >
        <table className="w-full min-w-120 border-separate border-spacing-0 text-body tabular-nums [&_tbody_tr:last-child>*]:border-b-0">
          <caption className="sr-only">{t("caption")}</caption>
          <thead>
            <tr className="text-left text-caption text-text-muted">
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">
                {t("name")}
              </th>
              <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">
                {t("target")}
              </th>
              <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">
                {t("allocated")}
              </th>
              <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">
                {t("difference")}
              </th>
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">
                {t("points")}
              </th>
            </tr>
          </thead>
          <tbody>
            {summary.rows.map((row, index) => {
              const target = summaryRowTarget(row);
              const level = row.blockId ? 2 : row.sectionId ? 1 : 0;
              const difference = roundForDisplay((row.allocated ?? 0) - (row.target ?? 0));
              const isSubject = Boolean(row.sectionId && !row.blockId && row.name);
              const label = isSubject ? names.subject(row.name ?? "") : (row.name ?? "");
              const labelNode = isSubject ? label : <ExamName>{label}</ExamName>;
              const buttonId = `${idBase}-go-${index}`;
              const nameId = `${buttonId}-name`;
              return (
                <tr key={`${row.partId}-${row.sectionId}-${row.blockId}-${index}`} className={level === 0 ? "bg-subtle/25" : undefined}>
                  <th
                    scope="row"
                    className={cn(
                      "border-b border-border px-1 py-0.5 text-left",
                      level === 0 ? "font-semibold text-text" : "font-medium text-text-secondary",
                    )}
                  >
                    {target ? (
                      <button
                        id={buttonId}
                        type="button"
                        aria-label={labelBeforeName(t, "rowGo", "name")}
                        aria-labelledby={`${buttonId} ${nameId}`}
                        onClick={() => onSelect(target)}
                        className={cn(
                          "min-h-9 w-full rounded-md px-2 text-left hover:bg-subtle hover:text-text",
                          level === 1 && "pl-5",
                          level === 2 && "pl-8",
                          focusRingInset,
                        )}
                      >
                        <span id={nameId}>{labelNode}</span>
                      </button>
                    ) : (
                      <span className="px-2">{labelNode}</span>
                    )}
                  </th>
                  <td className="border-b border-border px-2 py-1.5 text-right">
                    {row.target === null ? "—" : format.number(row.target, DISPLAY_NUMBER_FORMAT)}
                  </td>
                  <td className="border-b border-border px-2 py-1.5 text-right">
                    {row.allocated === null ? "—" : format.number(row.allocated, DISPLAY_NUMBER_FORMAT)}
                  </td>
                  <td className="border-b border-border px-2 py-1.5 text-right">
                    {row.target === null || row.allocated === null ? "—" : <AllocationGap missing={-difference} />}
                  </td>
                  <td className="border-b border-border px-3 py-1.5 text-right">
                    {row.maxPoints === null ? "—" : format.number(row.maxPoints, DISPLAY_NUMBER_FORMAT)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
