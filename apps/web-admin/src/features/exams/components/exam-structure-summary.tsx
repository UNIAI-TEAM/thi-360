"use client";

import { Button, Card, cn, focusRingInset, Skeleton } from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import type { StructureIssue } from "../matrix";
import type { ExamCheckResult } from "../review-types";
import type { ExamStructureSummary, ExamStructureSummaryRow } from "../types";
import type { StructureSelection } from "./exam-structure-draft";
import { issueTarget } from "./exam-structure-issues";
import { ExamStructureCheckResult, type CheckResultNames } from "./exam-structure-check-result";

export type SummaryLoad = "loading" | "ready" | "unavailable";

export interface ExamStructureSummaryProps {
  /** Tổng hợp từ `structure/summary`; `null` khi máy chủ chưa có (BE-5 trả 501). */
  summary: ExamStructureSummary | null;
  load: SummaryLoad;
  /** Còn thay đổi chưa lưu: bảng tổng hợp theo bản đã lưu, không theo bản nháp. */
  dirty: boolean;
  /** Số câu tính trên màn hình, dùng khi máy chủ chưa trả tổng hợp. */
  local: { allocated: number; total: number; missing: number };
  issues: readonly StructureIssue[];
  issueText: (issue: StructureIssue) => string;
  checkResult: ExamCheckResult | null;
  names: CheckResultNames;
  onSelect: (selection: StructureSelection) => void;
}

/** Dòng bấm được: khối → khối, môn → môn, phần → phần (R-04). */
export function summaryRowTarget(row: ExamStructureSummaryRow): StructureSelection | null {
  if (row.blockId) return { kind: "block", blockId: row.blockId };
  if (row.sectionId) return { kind: "section", sectionId: row.sectionId };
  if (row.partId) return { kind: "part", partId: row.partId };
  return null;
}

type Headline = { state: "ok" | "missing" | "over"; allocated: number; target: number; missing: number; extra: number };

function headlineOf(allocated: number, target: number): Headline {
  const missing = Math.max(0, target - allocated);
  const extra = Math.max(0, allocated - target);
  return { state: missing > 0 ? "missing" : extra > 0 ? "over" : "ok", allocated, target, missing, extra };
}

/**
 * Vùng (d) của bước Cấu trúc (spec 15.2, R-04): đọc `structure/summary` (số câu đã phân bổ so với chỉ tiêu theo phần,
 * môn, nhóm), liệt kê lỗi cấu trúc trên màn hình và kết quả kiểm tra gần nhất; bấm một dòng hoặc một lỗi thì chọn đúng
 * phần, môn, nhóm trong danh sách và vùng sửa. Máy chủ chưa có tổng hợp thì dùng số tính trên màn hình.
 */
export function ExamStructureSummaryRegion({
  summary,
  load,
  dirty,
  local,
  issues,
  issueText,
  checkResult,
  names,
  onSelect,
}: ExamStructureSummaryProps) {
  const t = useTranslations("exams.structure.summaryPanel");
  const structureT = useTranslations("exams.structure");
  const format = useFormatter();

  const partRows = summary?.rows.filter((row) => row.partId && !row.sectionId && !row.blockId) ?? [];
  const headline =
    summary && summary.rows.length > 0
      ? headlineOf(
          partRows.reduce((total, row) => total + (row.allocated ?? 0), 0),
          partRows.reduce((total, row) => total + (row.target ?? 0), 0),
        )
      : headlineOf(local.allocated, local.total);

  return (
    <Card padding="md" className="flex flex-col gap-4" aria-labelledby="structure-summary-title">
      <div className="flex flex-col gap-1">
        <h2 id="structure-summary-title" className="text-title font-semibold text-text">
          {t("title")}
        </h2>
        {load === "loading" ? (
          <Skeleton className="h-5 w-64 max-w-full" />
        ) : (
          <p
            data-testid="structure-headline"
            className={cn("text-body font-medium", headline.state === "ok" ? "text-text" : "text-warning")}
          >
            {t("headline", headline)}
          </p>
        )}
        {load === "unavailable" && <p className="text-caption text-text-muted">{t("unavailable")}</p>}
        {load === "ready" && dirty && <p className="text-caption text-text-muted">{t("stale")}</p>}
      </div>

      {summary && summary.rows.length > 0 && (
        <div
          role="region"
          tabIndex={0}
          aria-label={t("caption")}
          className={cn("overflow-x-auto rounded-md border border-border", focusRingInset)}
        >
          <table className="w-full min-w-120 border-separate border-spacing-0 text-body tabular-nums">
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
                const difference = (row.allocated ?? 0) - (row.target ?? 0);
                const label = row.name ?? "";
                return (
                  <tr key={`${row.partId}-${row.sectionId}-${row.blockId}-${index}`}>
                    <th
                      scope="row"
                      className={cn(
                        "border-b border-border px-1 py-0.5 text-left",
                        level === 0 ? "font-semibold" : "font-medium",
                      )}
                    >
                      {target ? (
                        <button
                          type="button"
                          aria-label={t("rowGo", { name: label })}
                          onClick={() => onSelect(target)}
                          className={cn(
                            "min-h-9 w-full rounded-md px-2 text-left hover:bg-subtle",
                            level === 1 && "pl-5",
                            level === 2 && "pl-8",
                            focusRingInset,
                          )}
                        >
                          {label}
                        </button>
                      ) : (
                        <span className="px-2">{label}</span>
                      )}
                    </th>
                    <td className="border-b border-border px-2 py-1 text-right">{row.target ?? "—"}</td>
                    <td className="border-b border-border px-2 py-1 text-right">{row.allocated ?? "—"}</td>
                    <td
                      className={cn(
                        "border-b border-border px-2 py-1 text-right",
                        difference !== 0 && row.target !== null && "font-semibold text-warning",
                      )}
                    >
                      {row.target === null || row.allocated === null ? "—" : difference > 0 ? `+${difference}` : difference}
                    </td>
                    <td className="border-b border-border px-3 py-1 text-right">
                      {row.maxPoints === null ? "—" : format.number(row.maxPoints, { maximumFractionDigits: 2 })}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <section aria-labelledby="structure-issues-title" className="flex flex-col gap-2">
        <h3 id="structure-issues-title" className="flex items-center gap-2 text-label font-semibold text-text">
          {issues.length > 0 && <TriangleAlert aria-hidden className="size-4 text-warning" />}
          {structureT("issuesSummary", { count: issues.length })}
        </h3>
        {issues.length === 0 ? (
          <p className="text-body text-text-muted">{t("noIssues")}</p>
        ) : (
          <ul className="flex flex-col gap-1.5">
            {issues.map((issue, index) => (
              <li key={index} className="flex flex-wrap items-start justify-between gap-2 text-body text-text">
                <span className="min-w-0 flex-1">{issueText(issue)}</span>
                <Button type="button" variant="link" size="sm" onClick={() => onSelect(issueTarget(issue))}>
                  {structureT("issueGo")}
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {checkResult && (
        <ExamStructureCheckResult result={checkResult} names={names} onSelect={onSelect} />
      )}
    </Card>
  );
}
