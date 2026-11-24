"use client";

import { cn, focusRing, StatusChip } from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId } from "react";
import { ratePercent } from "../allocation";
import { DISPLAY_NUMBER_FORMAT, roundForDisplay } from "../display-number";
import { bucketCounts, canRequireQuota, hasChoicePath, pointsRows, quotaScope, quotaUnitOf } from "../exam-overview";
import { hsaQuotaAwaitsExpert } from "../hsa-enforcement";
import { sum } from "../matrix";
import type { ExamPart, ExamQuota, ExamStructureSummary, StructureCombination } from "../types";
import type { MatrixLabels } from "./block-matrix-editor";
import { AllocationGap } from "./allocation-gap";
import { BlockNumberInput } from "./block-number-input";
import { EnforcementControl } from "../subject-forms/enforcement-control";
import { ExamName, richNodes } from "./exam-review-shared";
import type { StructureSelection } from "./exam-structure-draft";
import type { StructureNames } from "./exam-structure-issues";
import { ExamStructureSummaryTable, type SummaryLoad } from "./exam-structure-summary";
import { TopicQuotaElsewhereAlert } from "./topic-quota-elsewhere-alert";
import type { TopicQuotaServerError } from "./topic-quota-table";

interface ExamOverviewPanelProps {
  /** Tên bài, đứng sau "Tổng quan bài". */
  examName: string;
  /** Dòng tóm tắt dưới tiêu đề ("81 câu · 180 phút chung · 100 điểm"). */
  summary: string;
  parts: readonly ExamPart[];
  quotas: readonly ExamQuota[];
  onQuotas: (quotas: ExamQuota[]) => void;
  names: StructureNames;
  labels: MatrixLabels;
  /** Lỗi máy chủ của chỉ tiêu cấp bài (422 `EXAM_QUOTA_SCOPE_INVALID`). */
  scopeError?: boolean;
  topicError?: TopicQuotaServerError;
  /** Mã bài thi của phần theo mã, để lấy một mã làm đường tính điểm và mẫu số. */
  combinations?: readonly StructureCombination[];
  /** Tổng hợp số câu toàn bài theo phần, môn và nhóm từ máy chủ. */
  structureSummary?: ExamStructureSummary | null;
  summaryLoad?: SummaryLoad;
  dirty?: boolean;
  onSelect?: (selection: StructureSelection) => void;
}

export function ExamOverviewPanel({
  examName,
  summary,
  parts,
  quotas,
  onQuotas,
  names,
  labels,
  scopeError = false,
  topicError = null,
  combinations = [],
  structureSummary = null,
  summaryLoad = "ready",
  dirty = false,
  onSelect,
}: ExamOverviewPanelProps) {
  const t = useTranslations("exams.examQuota");
  const format = useFormatter();
  const pointsId = useId();
  const quotasId = useId();
  const rows = pointsRows(parts, combinations);
  const number = (value: number) => format.number(value, DISPLAY_NUMBER_FORMAT);
  const totalPoints = sum(rows.map((row) => row.totalPoints));
  const essayPoints = sum(rows.map((row) => row.essayPoints));

  const patch = (quotaId: string, change: (quota: ExamQuota) => ExamQuota) =>
    onQuotas(quotas.map((quota) => (quota.id === quotaId ? change(quota) : quota)));

  return (
    <div className="flex flex-col gap-6 p-4 sm:p-6">
      <div className="flex items-start gap-2.5 border-b border-border pb-4">
        <span aria-hidden className="mt-1 h-5 w-1 shrink-0 rounded-full bg-primary-600" />
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 className="text-h3 font-bold wrap-break-word text-text">
            {richNodes(t, "title", { name: <ExamName>{examName}</ExamName> })}
          </h2>
          <p className="text-caption text-text-muted">{summary}</p>
        </div>
      </div>
      {quotas.length === 0 && <TopicQuotaElsewhereAlert error={topicError} />}

      <section aria-labelledby={pointsId} className="flex flex-col gap-2.5">
        <h3 id={pointsId} className="text-title font-semibold text-text">
          {t("pointsTitle")}
        </h3>
        <div role="region" tabIndex={0} aria-label={t("pointsTitle")} className={cn("relative overflow-x-auto rounded-md border border-border", focusRing)}>
          <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
            <caption className="sr-only">{t("pointsCaption")}</caption>
            <thead>
              <tr className="text-left text-caption text-text-muted">
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("subject")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">{t("count")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">{t("each")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">{t("totalPoints")}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.key}>
                  <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">
                    {row.coded
                      ? richNodes(t, "codedRow", { part: <ExamName>{row.partName}</ExamName> })
                      : parts.length > 1
                        ? richNodes(t, "partRow", { part: <ExamName>{row.partName}</ExamName>, subject: names.subject(row.subjectCode) })
                        : names.subject(row.subjectCode)}
                  </th>
                  <td className="border-b border-border px-2 py-2 text-right">{row.count}</td>
                  <td className="border-b border-border px-2 py-2 text-right">{row.eachPoints === null ? t("mixed") : number(row.eachPoints)}</td>
                  <td className="border-b border-border px-3 py-2 text-right font-semibold text-text">{number(row.totalPoints)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="bg-subtle/30 font-semibold text-text">
                <th scope="row" className="px-3 py-2 text-left">
                  {essayPoints > 0 ? t("essaySplit", { essay: number(essayPoints), objective: number(totalPoints - essayPoints) }) : t("total")}
                </th>
                <td className="px-2 py-2 text-right">{sum(rows.map((row) => row.count))}</td>
                <td className="px-2 py-2" />
                <td className="px-3 py-2 text-right">{number(totalPoints)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
        {rows.length > 0 && parts.some((part) => hasChoicePath(part, combinations)) && (
          <p className="text-caption text-text-muted">{t("choiceCaption")}</p>
        )}
      </section>

      {onSelect && structureSummary && structureSummary.rows.length > 0 && (
        <div className="border-t border-border pt-6">
          <ExamStructureSummaryTable
            summary={structureSummary}
            load={summaryLoad}
            dirty={dirty}
            names={names}
            onSelect={onSelect}
          />
        </div>
      )}

      <section aria-labelledby={quotasId} className="flex flex-col gap-3 border-t border-border pt-6">
        <div className="flex flex-col gap-0.5">
          <h3 id={quotasId} className="text-title font-semibold text-text">
            {t("quotasTitle")}
          </h3>
          <p className="text-caption text-text-muted">{quotas.length > 0 ? t("quotasHint") : t("none")}</p>
        </div>
        {quotas.length > 0 && <TopicQuotaElsewhereAlert error={topicError} />}
        {scopeError && (
          <p role="alert" className="flex items-start gap-1.5 text-body text-danger">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
            {t("scopeError")}
          </p>
        )}
        {quotas.map((quota) => {
          const scope = quotaScope(parts, quota, combinations);
          const percent = quotaUnitOf(quota) === "PERCENT";
          const counts = bucketCounts(quota, scope.count);
          const label = quota.dimension === "GRADE" ? labels.grade : labels.difficulty;
          const total = sum(quota.buckets.map((bucket) => (percent ? (bucket.percent ?? 0) : (bucket.count ?? 0))));
          const mismatch = percent ? Math.abs(total - 100) > 1e-6 : scope.count > 0 && total !== scope.count;
          const quotaHeadingId = `${quotasId}-${quota.id}-title`;
          const quotaTitle = quota.name ? <ExamName>{quota.name}</ExamName> : t(quota.dimension === "GRADE" ? "gradeName" : "difficultyName");
          return (
            <section key={quota.id} aria-labelledby={quotaHeadingId} className="flex flex-col gap-3 rounded-md border border-border bg-subtle/20 p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex min-w-0 flex-col gap-0.5">
                  <h4 id={quotaHeadingId} className="text-label font-semibold text-text">
                    {quotaTitle}
                  </h4>
                  <p className="text-caption text-text-muted">
                    {t("scope", { count: roundForDisplay(scope.count), subjects: scope.subjectCodes.map(names.subject).join(", ") })}
                    {(quota.excludeSubjectCodes?.length ?? 0) > 0 && ` · ${t("excluding", { subjects: quota.excludeSubjectCodes?.map(names.subject).join(", ") ?? "" })}`}
                  </p>
                </div>
                <EnforcementControl
                  mode="TOGGLE"
                  legend={quotaTitle}
                  name={`quota-${quota.id}-enforcement`}
                  value={quota.enforcement}
                  basis={quota.basis}
                  lockRequiredReason={canRequireQuota(quota) ? undefined : t("multiSubjectLocked")}
                  expertPending={hsaQuotaAwaitsExpert(quota) && quota.enforcement === "REFERENCE"}
                  onChange={(enforcement) => patch(quota.id, (current) => ({ ...current, enforcement }))}
                />
              </div>
              <ul className="divide-y divide-border rounded-md border border-border bg-surface px-3">
                {quota.buckets.map((bucket, index) => {
                  const bucketName = bucket.codes.map(label).join(" + ");
                  const value = percent ? (bucket.percent ?? 0) : (bucket.count ?? 0);
                  const rate = ratePercent(counts[index] ?? 0, scope.count);
                  return (
                    <li
                      key={bucket.codes.join("+")}
                      className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1 py-2 sm:grid-cols-[minmax(8rem,1fr)_auto_minmax(6.5rem,auto)]"
                    >
                      <span className="min-w-0 text-body font-medium text-text">{bucketName}</span>
                      <span className="flex items-center gap-1">
                        <BlockNumberInput
                          className="h-9 w-20 px-2"
                          allowDecimal={percent}
                          value={value}
                          aria-label={t(percent ? "percentAria" : "countAria", { name: bucketName })}
                          onValueChange={(next) =>
                            patch(quota.id, (current) => ({
                              ...current,
                              buckets: current.buckets.map((item, position) => (position === index ? { ...item, [percent ? "percent" : "count"]: next } : item)),
                            }))
                          }
                        />
                        {percent && <span aria-hidden className="text-text-muted">%</span>}
                      </span>
                      <span className="col-span-2 text-caption text-text-muted tabular-nums sm:col-span-1 sm:text-right">
                        {percent ? t("approx", { count: roundForDisplay(counts[index] ?? 0) }) : rate === null ? "" : t("realRate", { value: format.number(rate, { maximumFractionDigits: 1 }) })}
                      </span>
                    </li>
                  );
                })}
              </ul>
              {mismatch && (
                <p role={percent ? "alert" : undefined} className={cn("flex items-start gap-1.5 text-caption", percent || total > scope.count ? "text-danger" : "text-warning")}>
                  <TriangleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0" />
                  <span>
                    {percent ? t("percentMismatch", { total: number(total) }) : t("countMismatch", { total: number(total), count: scope.count })}
                    {!percent && (
                      <>
                        {" "}
                        <AllocationGap missing={scope.count - total} />
                      </>
                    )}
                  </span>
                </p>
              )}
              <p className="flex flex-wrap items-center gap-2 text-caption text-text-muted">
                <StatusChip tone={quota.enforcement === "REQUIRED" ? "success" : "info"}>{t(quota.enforcement)}</StatusChip>
                {t("basisNote")}
              </p>
            </section>
          );
        })}
      </section>
    </div>
  );
}
