"use client";

import { Card, cn, focusRing, StatusChip } from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId } from "react";
import { ratePercent } from "../allocation";
import { bucketCounts, canRequireQuota, pointsRows, quotaScope, quotaUnitOf } from "../exam-overview";
import { sum } from "../matrix";
import type { ExamPart, ExamQuota } from "../types";
import type { MatrixLabels } from "./block-matrix-editor";
import { BlockNumberInput } from "./block-number-input";
import { EnforcementToggle } from "./enforcement-toggle";
import type { StructureNames } from "./exam-structure-issues";

export interface ExamOverviewPanelProps {
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
}

/**
 * Tổng quan bài ở bước 2 (bản vẽ `ChiTieuCapBai2B`): điểm theo môn, tách Tự luận và Trắc nghiệm (R-49); khối chỉ tiêu cấp
 * bài (R-52, 3.4). Chỉ tiêu của mẫu không thêm, xóa hay đổi tiêu chí được: chỉ sửa được giá trị từng mức và mức Bắt buộc
 * hay Tham khảo. Chỉ tiêu trải nhiều môn khóa nút Bắt buộc (H6).
 */
export function ExamOverviewPanel({ examName, summary, parts, quotas, onQuotas, names, labels, scopeError = false }: ExamOverviewPanelProps) {
  const t = useTranslations("exams.examQuota");
  const format = useFormatter();
  const pointsId = useId();
  const quotasId = useId();
  const rows = pointsRows(parts);
  const number = (value: number) => format.number(value, { maximumFractionDigits: 2 });
  const totalPoints = sum(rows.map((row) => row.totalPoints));
  const essayPoints = sum(rows.map((row) => row.essayPoints));

  const patch = (quotaId: string, change: (quota: ExamQuota) => ExamQuota) =>
    onQuotas(quotas.map((quota) => (quota.id === quotaId ? change(quota) : quota)));

  return (
    <Card padding="md" className="flex flex-col gap-6 sm:p-6">
      <div className="flex flex-col gap-0.5">
        <h2 className="text-h3 font-bold wrap-break-word text-text">{t("title", { name: examName })}</h2>
        <p className="text-caption text-text-muted">{summary}</p>
      </div>

      <section aria-labelledby={pointsId} className="flex flex-col gap-2">
        <h3 id={pointsId} className="text-title font-semibold text-text">
          {t("pointsTitle")}
        </h3>
        <div role="region" tabIndex={0} aria-label={t("pointsTitle")} className={cn("overflow-x-auto rounded-md border border-border", focusRing)}>
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
                    {row.coded ? t("codedRow", { part: row.partName }) : parts.length > 1 ? t("partRow", { part: row.partName, subject: names.subject(row.subjectCode) }) : names.subject(row.subjectCode)}
                  </th>
                  <td className="border-b border-border px-2 py-2 text-right">{row.count}</td>
                  <td className="border-b border-border px-2 py-2 text-right">{row.eachPoints === null ? t("mixed") : number(row.eachPoints)}</td>
                  <td className="border-b border-border px-3 py-2 text-right font-semibold text-text">{number(row.totalPoints)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="font-semibold text-text">
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
      </section>

      <section aria-labelledby={quotasId} className="flex flex-col gap-3">
        <div className="flex flex-col gap-0.5">
          <h3 id={quotasId} className="text-title font-semibold text-text">
            {t("quotasTitle")}
          </h3>
          <p className="text-caption text-text-muted">{quotas.length > 0 ? t("quotasHint") : t("none")}</p>
        </div>
        {scopeError && (
          <p role="alert" className="flex items-start gap-1.5 text-body text-danger">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
            {t("scopeError")}
          </p>
        )}
        {quotas.map((quota) => {
          const scope = quotaScope(parts, quota);
          const percent = quotaUnitOf(quota) === "PERCENT";
          const counts = bucketCounts(quota, scope.count);
          const label = quota.dimension === "GRADE" ? labels.grade : labels.difficulty;
          const total = sum(quota.buckets.map((bucket) => (percent ? (bucket.percent ?? 0) : (bucket.count ?? 0))));
          const mismatch = percent ? Math.abs(total - 100) > 1e-6 : scope.count > 0 && total !== scope.count;
          return (
            <section key={quota.id} aria-label={quota.name} className="flex flex-col gap-3 rounded-md border border-border p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex min-w-0 flex-col gap-0.5">
                  <h4 className="text-label font-semibold text-text">{quota.name || t(quota.dimension === "GRADE" ? "gradeName" : "difficultyName")}</h4>
                  <p className="text-caption text-text-muted">
                    {t("scope", { count: scope.count, subjects: scope.subjectCodes.map(names.subject).join(", ") })}
                    {(quota.excludeSubjectCodes?.length ?? 0) > 0 && ` · ${t("excluding", { subjects: quota.excludeSubjectCodes?.map(names.subject).join(", ") ?? "" })}`}
                  </p>
                </div>
                <EnforcementToggle
                  legend={quota.name}
                  name={`quota-${quota.id}-enforcement`}
                  value={quota.enforcement}
                  lockRequiredReason={canRequireQuota(quota) ? undefined : t("multiSubjectLocked")}
                  onChange={(enforcement) => patch(quota.id, (current) => ({ ...current, enforcement }))}
                />
              </div>
              <ul className="flex flex-col gap-2">
                {quota.buckets.map((bucket, index) => {
                  const bucketName = bucket.codes.map(label).join(" + ");
                  const value = percent ? (bucket.percent ?? 0) : (bucket.count ?? 0);
                  const rate = ratePercent(counts[index] ?? 0, scope.count);
                  return (
                    <li key={bucket.codes.join("+")} className="flex flex-wrap items-center gap-x-3 gap-y-1">
                      <span className="min-w-28 text-body font-medium text-text">{bucketName}</span>
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
                      <span className="text-caption text-text-muted">
                        {percent ? t("approx", { count: counts[index] ?? 0 }) : rate === null ? "" : t("realRate", { value: format.number(rate, { maximumFractionDigits: 1 }) })}
                      </span>
                    </li>
                  );
                })}
              </ul>
              {mismatch && (
                <p className="flex items-start gap-1.5 text-caption text-warning">
                  <TriangleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0" />
                  {percent ? t("percentMismatch", { total: number(total) }) : t("countMismatch", { total: number(total), count: scope.count })}
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
    </Card>
  );
}
