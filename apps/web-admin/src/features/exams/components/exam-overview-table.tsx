"use client";

import { cn, focusRingInset, Input } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId } from "react";
import type { ExamTiming } from "../types";
import type { OverviewRow } from "../create-exam-overview";
import { overviewTotals } from "../create-exam-overview";

export interface OverviewNumberField {
  /** Ô số dưới bảng: "Thời lượng chung" khi `timing = SHARED`, "Điểm tối đa" khi bảng không có cột Điểm (2B 15.1). */
  value: string;
  onChange: (text: string) => void;
  /** Chữ lỗi của ô; có thì ô báo `aria-invalid` và dòng lỗi nằm cạnh ô, nối bằng `aria-describedby`. */
  error?: string;
}

export interface ExamOverviewTableProps {
  /** Dòng hiện tại (mẫu đã nạp hoặc bảng trống của bài ôn); sửa ô không nạp lại mẫu. */
  rows: readonly OverviewRow[];
  timing: ExamTiming;
  /** Cột Điểm chỉ hiện khi mẫu chia điểm theo phần hoặc người dùng đã nhập điểm. */
  showPoints: boolean;
  onCellChange: (key: string, field: "questionCount" | "durationMinutes" | "maxPoints", text: string) => void;
  /** Lỗi của ô, khóa theo `OverviewRow.key` → trường → chữ lỗi. */
  issues: ReadonlyMap<string, { questionCount?: string; durationMinutes?: string; maxPoints?: string }>;
  /** Tra tên môn từ `subjectCode` cho dòng môn thụt vào. */
  subjectName: (code: string) => string;
  /** Ô thời lượng chung ở cuối bảng; bỏ trống khi `timing = PER_PART`. */
  sharedDuration?: OverviewNumberField;
  /** Ô điểm tối đa của bài khi bảng không có cột Điểm (không có mẫu chia điểm theo phần). */
  maxScore?: OverviewNumberField;
}

const NUMERIC = { inputMode: "numeric" as const };
const cellInput = "h-9 w-20 text-end";

/** Ô số của bảng; `data-field` để bảng tóm tắt dẫn con trỏ tới đúng ô lỗi. */
function NumberCell({
  row,
  field,
  error,
  onCellChange,
}: {
  row: OverviewRow;
  field: "questionCount" | "durationMinutes" | "maxPoints";
  error: string | undefined;
  onCellChange: ExamOverviewTableProps["onCellChange"];
}) {
  const t = useTranslations("exams.create.overview");
  const errorId = useId();
  return (
    <div data-field={`${row.key}.${field}`}>
      <Input
        {...NUMERIC}
        value={row[field]}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
        aria-label={t(`cell.${field}`, { name: row.label })}
        className={cellInput}
        onChange={(event) => onCellChange(row.key, field, event.target.value)}
      />
      {error && (
        <p id={errorId} className="mt-1 text-caption text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

/** Ô số dưới bảng (thời lượng chung, điểm tối đa); `data-field` để tóm tắt dẫn con trỏ tới ô lỗi. */
function FooterField({ field, label, unit, state }: { field: string; label: string; unit: string; state: OverviewNumberField }) {
  const id = useId();
  const errorId = useId();
  return (
    <div className="flex flex-col gap-1 border-t border-border px-3 py-3">
      <div className="flex items-center gap-3">
        <label htmlFor={id} className="text-body font-medium">
          {label}
        </label>
        <div className="flex items-center gap-2" data-field={field}>
          <Input
            {...NUMERIC}
            id={id}
            value={state.value}
            aria-invalid={Boolean(state.error)}
            aria-describedby={state.error ? errorId : undefined}
            className={cellInput}
            onChange={(event) => state.onChange(event.target.value)}
          />
          <span className="text-body text-text-secondary">{unit}</span>
        </div>
      </div>
      {state.error && (
        <p id={errorId} className="text-caption text-danger">
          {state.error}
        </p>
      )}
    </div>
  );
}

/**
 * "Bảng tổng quan" (2B 15.1): dòng phần và dòng môn thụt vào (phần cố định nhiều môn), sửa số câu, thời lượng và
 * điểm trên dòng; có hàng Tổng ở cuối. `timing = SHARED` thay cột thời lượng từng phần bằng ô thời lượng chung dưới
 * bảng. Không đăng ký submit: trang gọi `overviewDiff` khi gửi.
 */
export function ExamOverviewTable({
  rows,
  timing,
  showPoints,
  onCellChange,
  issues,
  subjectName,
  sharedDuration,
  maxScore,
}: ExamOverviewTableProps) {
  const t = useTranslations("exams.create.overview");
  const totals = overviewTotals(rows, timing, sharedDuration?.value ?? "");
  const showDuration = timing === "PER_PART";
  const label = (row: OverviewRow) => (row.kind === "section" ? subjectName(row.subjectCode) : row.label);
  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <div role="region" tabIndex={0} aria-label={t("regionLabel")} className={cn("overflow-x-auto", focusRingInset)}>
        <table className="w-full min-w-[32rem] border-collapse text-body">
          <thead>
            <tr className="border-b border-border bg-subtle text-start">
              <th className="px-3 py-2 text-start font-medium text-text-secondary">{t("col.part")}</th>
              <th className="px-3 py-2 text-end font-medium text-text-secondary">{t("col.count")}</th>
              {showDuration && <th className="px-3 py-2 text-end font-medium text-text-secondary">{t("col.duration")}</th>}
              {showPoints && <th className="px-3 py-2 text-end font-medium text-text-secondary">{t("col.points")}</th>}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const rowIssues = issues.get(row.key);
              const display = label(row);
              const cellRow = { ...row, label: display };
              if (row.kind === "omitted") {
                return (
                  <tr key={row.key} className="border-b border-border text-text-muted last:border-b-0">
                    <td className="px-3 py-2 pl-8">
                      <span className="me-1">–</span>
                      {display}
                      <span className="ms-2 text-caption">{t("omittedLabel")}</span>
                    </td>
                    <td className="px-3 py-2 text-end">{row.base.questionCount ?? "—"}</td>
                    {showDuration && <td className="px-3 py-2 text-end">—</td>}
                    {showPoints && <td className="px-3 py-2 text-end">{t("notCounted")}</td>}
                  </tr>
                );
              }
              return (
                <tr key={row.key} className="border-b border-border last:border-b-0">
                  <td className={cn("px-3 py-2 align-top", row.kind === "section" && "pl-8 text-text-secondary")}>
                    {row.kind === "section" ? <span className="me-1 text-text-muted">–</span> : null}
                    {display}
                    {row.choice && <span className="ms-2 text-caption text-text-muted">{t("choiceNote")}</span>}
                  </td>
                  <td className="px-3 py-2 align-top">
                    <div className="flex justify-end">
                      {row.group ? (
                        <span className="px-3 py-2 text-text-muted">{t("sumOfSubjects")}</span>
                      ) : (
                        <NumberCell row={cellRow} field="questionCount" error={rowIssues?.questionCount} onCellChange={onCellChange} />
                      )}
                    </div>
                  </td>
                  {showDuration && (
                    <td className="px-3 py-2 align-top">
                      <div className="flex justify-end">
                        {row.kind === "part" ? (
                          <NumberCell row={cellRow} field="durationMinutes" error={rowIssues?.durationMinutes} onCellChange={onCellChange} />
                        ) : (
                          <span className="px-3 py-2 text-text-muted">—</span>
                        )}
                      </div>
                    </td>
                  )}
                  {showPoints && (
                    <td className="px-3 py-2 align-top">
                      <div className="flex justify-end">
                        {row.kind === "part" ? (
                          <NumberCell row={cellRow} field="maxPoints" error={rowIssues?.maxPoints} onCellChange={onCellChange} />
                        ) : (
                          <span className="px-3 py-2 text-text-muted">—</span>
                        )}
                      </div>
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr className="border-t border-border bg-subtle font-medium">
              <td className="px-3 py-2">{t("total")}</td>
              <td className="px-3 py-2 text-end">{totals.questions ?? "—"}</td>
              {showDuration && <td className="px-3 py-2 text-end">{totals.durationMinutes ?? "—"}</td>}
              {showPoints && <td className="px-3 py-2 text-end">{totals.maxPoints ?? "—"}</td>}
            </tr>
          </tfoot>
        </table>
      </div>
      {sharedDuration && timing === "SHARED" && (
        <FooterField field="sharedDuration" label={t("sharedDuration")} unit={t("minutes")} state={sharedDuration} />
      )}
      {maxScore && <FooterField field="maxScore" label={t("maxScore")} unit={t("points")} state={maxScore} />}
    </div>
  );
}
