"use client";

import { cn, Input } from "@thi360/ui";
import { useFormatter, useTranslations } from "next-intl";
import { useId } from "react";
import type { ExamTiming } from "../types";
import type { OverviewBlockInfo, OverviewRow } from "../create-exam-overview";
import { overviewTotals } from "../create-exam-overview";
import { LOCKED_FIELD } from "./locked-field";
import { ScrollableTable } from "./scrollable-table";

export interface OverviewNumberField {
  /** Ô số dưới bảng: "Thời lượng chung" khi `timing = SHARED`, "Điểm tối đa" khi bảng không có cột Điểm (2B 15.1). */
  value: string;
  onChange: (text: string) => void;
  /** Chữ lỗi của ô; có thì ô báo `aria-invalid` và dòng lỗi nằm cạnh ô, nối bằng `aria-describedby`. */
  error?: string;
  /** Gợi ý dưới ô (nguồn của giá trị điền sẵn); khi có lỗi thì lỗi thay chỗ gợi ý. Nối với ô bằng `aria-describedby`. */
  hint?: string;
}

export interface ExamOverviewTableProps {
  /** Dòng hiện tại (mẫu đã nạp hoặc bảng trống của bài ôn); sửa ô không nạp lại mẫu. */
  rows: readonly OverviewRow[];
  timing: ExamTiming;
  /** Cột Điểm chỉ hiện khi mẫu chia điểm theo phần hoặc người dùng đã nhập điểm. */
  showPoints: boolean;
  /** Cột tỷ lệ của dòng so với cả bài (R-123): mẫu `fixedCounts` hoặc mẫu bài riêng có tiểu mục (V-ACT). */
  showPercents?: boolean;
  /** Đầu cột `showPercents`; mặc định "Tỷ lệ toàn bài", bài riêng V-ACT là "Tỷ lệ trong bài" (TaoBaiThiVact2B). */
  percentExamHeader?: string;
  /** Cột Tỷ lệ trong phần khi phần có tiểu mục (V-ACT toàn bài: 50/50/100/40/60%). */
  showPartPercent?: boolean;
  /** Cột Lệnh hỏi của các dòng lấy câu (V-SAT, `TaoBaiThiHscaVsat2B`); chỉ hiện khi bảng có dòng lấy câu. */
  showCommands?: boolean;
  onCellChange: (key: string, field: "questionCount" | "durationMinutes" | "maxPoints", text: string) => void;
  /** Lỗi của ô, khóa theo `OverviewRow.key` → trường → chữ lỗi. */
  issues: ReadonlyMap<string, { questionCount?: string; durationMinutes?: string; maxPoints?: string }>;
  /** Tra tên môn từ `subjectCode` cho dòng môn thụt vào. */
  subjectName: (code: string) => string;
  /**
   * Tên hiển thị của dòng phần hoặc tiểu mục theo ngôn ngữ đang chọn (V-ACT: tên phần, môn, Logic, Suy luận tách theo
   * năng lực); `undefined` thì dòng tiểu mục dùng `subjectName`, dòng phần dùng tên của mẫu.
   */
  rowLabel?: (row: OverviewRow) => string | undefined;
  /** Ô thời lượng chung ở cuối bảng; bỏ trống khi `timing = PER_PART`. */
  sharedDuration?: OverviewNumberField;
  /** Ô điểm tối đa của bài khi bảng không có cột Điểm (không có mẫu chia điểm theo phần). */
  maxScore?: OverviewNumberField;
  /**
   * Chỉ đọc: mọi ô số khóa như ô của mẫu khóa (`readonly aria-readonly`, vẫn trong thứ tự Tab). Dùng khi bảng hiện số của
   * mẫu sắp nạp (đổi phạm vi ở Thông tin chung), chưa phải số của bài.
   */
  readOnly?: boolean;
  /**
   * Bài Theo môn + Tự thiết lập (UNI-2752): một dòng, Điểm tối đa là cột cạnh Thời lượng (cùng trường `maxScore`, cùng lỗi),
   * không có hàng Tổng của bài và không có ô Điểm tối đa tách riêng dưới bảng.
   */
  reviewLayout?: boolean;
  /** Thời lượng chung chỉ để cộng vào hàng Tổng khi bảng không có ô nhập thời lượng chung (ô nằm ở biểu mẫu bên ngoài). */
  sharedTotal?: string;
}

const NUMERIC = { inputMode: "numeric" as const };
const cellInput = "h-9 w-20 text-end";

/** Ô số của bảng; `data-field` để bảng tóm tắt dẫn con trỏ tới đúng ô lỗi. */
function NumberCell({
  row,
  field,
  error,
  locked,
  onCellChange,
}: {
  row: OverviewRow;
  field: "questionCount" | "durationMinutes" | "maxPoints";
  error: string | undefined;
  /** Mẫu `fixedCounts` (H27): ô chỉ đọc — không cho sửa; vẫn trong thứ tự tab như bản vẽ (`readonly aria-readonly`). */
  locked?: boolean;
  onCellChange: ExamOverviewTableProps["onCellChange"];
}) {
  const t = useTranslations("exams.create.overview");
  const errorId = useId();
  return (
    <div data-field={`${row.key}.${field}`}>
      <Input
        {...NUMERIC}
        value={row[field]}
        readOnly={locked}
        aria-readonly={locked || undefined}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
        aria-label={t(`cell.${field}`, { name: row.label })}
        className={cn(cellInput, locked && LOCKED_FIELD)}
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

/**
 * Ô số ngoài bảng (thời lượng chung phía trên bảng, điểm tối đa phía dưới); `data-field` để tóm tắt dẫn con trỏ tới ô lỗi.
 * Gợi ý nằm ngay dưới ô; ô nối với lỗi nếu có, không thì nối với gợi ý bằng `aria-describedby` (R-141, FE-6 R2-2).
 */
function FooterField({
  field,
  label,
  unit,
  state,
  position,
}: {
  field: string;
  label: string;
  unit: string;
  state: OverviewNumberField;
  position: "top" | "bottom";
}) {
  const id = useId();
  const errorId = useId();
  const hintId = useId();
  return (
    <div className={cn("flex flex-col gap-1 border-border px-3 py-3", position === "top" ? "border-b" : "border-t")}>
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
            aria-describedby={state.error ? errorId : state.hint ? hintId : undefined}
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
      {state.hint && (
        <p id={hintId} className="text-caption text-text-muted">
          {state.hint}
        </p>
      )}
    </div>
  );
}

/** Ô Điểm tối đa của bài trong dòng của bảng (bài ôn): cùng trường và cùng lỗi với ô dưới bảng trước đây; `data-field` để Tóm tắt dẫn tới ô lỗi. */
function MaxScoreCell({ state }: { state: OverviewNumberField }) {
  const t = useTranslations("exams.create.overview");
  const errorId = useId();
  return (
    <div data-field="maxScore">
      <Input
        {...NUMERIC}
        value={state.value}
        aria-label={t("maxScore")}
        aria-invalid={Boolean(state.error)}
        aria-describedby={state.error ? errorId : undefined}
        className={cellInput}
        onChange={(event) => state.onChange(event.target.value)}
      />
      {state.error && (
        <p id={errorId} className="mt-1 text-caption text-danger">
          {state.error}
        </p>
      )}
    </div>
  );
}

const PERCENT = { style: "percent", maximumFractionDigits: 1 } as const;

/**
 * "Bảng tổng quan" (2B 15.1): dòng phần và dòng môn thụt vào (phần cố định nhiều môn), sửa số câu, thời lượng và
 * điểm trên dòng; có hàng Tổng ở cuối. `timing = SHARED` thay cột thời lượng từng phần bằng ô thời lượng chung dưới
 * bảng. Không đăng ký submit: trang gọi `overviewDiff` khi gửi. Mẫu `fixedCounts` (H27) khóa ô số câu và hiện tỷ
 * lệ trong phần / toàn bài của từng tiểu mục (V-ACT toàn bài, R-118, R-123).
 */
export function ExamOverviewTable({
  rows,
  timing,
  showPoints,
  showPercents = false,
  percentExamHeader,
  showPartPercent = false,
  showCommands = false,
  onCellChange,
  issues,
  subjectName,
  rowLabel,
  sharedDuration,
  maxScore,
  readOnly = false,
  reviewLayout = false,
  sharedTotal,
}: ExamOverviewTableProps) {
  const t = useTranslations("exams.create.overview");
  const format = useFormatter();
  const totals = overviewTotals(rows, timing, sharedDuration?.value ?? sharedTotal ?? "");
  const showDuration = timing === "PER_PART";
  const maxScoreColumn = reviewLayout && maxScore !== undefined;
  // H-SCA, V-SAT: dòng lấy câu của mẫu (chỉ đọc) kèm cột Tỷ lệ trong phần, Lệnh hỏi (V-SAT) và Điểm tối đa; kỳ thi khác không có.
  const blockMode = rows.some((row) => row.kind === "block");
  const partPercentColumn = showPartPercent || blockMode;
  const commandColumn = blockMode && showCommands;
  const blocksOf = (key: string) => rows.filter((row) => row.kind === "block" && row.parentKey === key);
  const blockSum = (key: string, pick: (info: OverviewBlockInfo) => number | null): number | null => {
    const values = blocksOf(key).flatMap((row) => (row.block ? [pick(row.block)] : [])).filter((value): value is number => value !== null);
    return values.length > 0 ? Math.round(values.reduce((total, value) => total + value, 0) * 100) / 100 : null;
  };
  const byKey = new Map(rows.map((row) => [row.key, row]));
  const label = (row: OverviewRow) =>
    rowLabel?.(row) ?? (row.kind === "section" ? subjectName(row.subjectCode) : row.label);
  const countOf = (row: OverviewRow): number | null => {
    const text = row.questionCount.trim();
    if (!text) return null;
    const value = Number(text);
    return Number.isFinite(value) ? value : null;
  };
  /** Tỷ lệ của dòng so với tổng bài; `null` khi tổng chưa có. */
  const inExam = (row: OverviewRow): number | null => {
    const count = countOf(row);
    if (count === null || !totals.questions) return null;
    return count / totals.questions;
  };
  /** Tỷ lệ của dòng tiểu mục so với phần cha; `null` khi dòng không thuộc phần nào. */
  const inPart = (row: OverviewRow): number | null => {
    const count = countOf(row);
    const parent = row.parentKey ? byKey.get(row.parentKey) : undefined;
    const parentCount = parent ? countOf(parent) : null;
    if (count === null || !parentCount) return null;
    return count / parentCount;
  };
  const percentText = (value: number | null): string => (value === null ? "—" : format.number(value, PERCENT));
  return (
    <div className="overflow-hidden rounded-lg border border-border">
      {sharedDuration && timing === "SHARED" && (
        <FooterField field="sharedDuration" label={t("sharedDuration")} unit={t("minutes")} state={sharedDuration} position="top" />
      )}
      <ScrollableTable label={t("regionLabel")}>
        <table className={cn("w-full border-collapse text-body", blockMode ? "min-w-[44rem]" : "min-w-[32rem]")}>
          <thead>
            <tr className="border-b border-border bg-subtle text-start">
              <th className="px-3 py-2 text-start font-medium text-text-secondary">{t(blockMode ? "col.partRows" : "col.part")}</th>
              <th className="px-3 py-2 text-end font-medium text-text-secondary">{t("col.count")}</th>
              {partPercentColumn && <th className="px-3 py-2 text-end font-medium text-text-secondary">{t("col.percentPart")}</th>}
              {showPercents && <th className="px-3 py-2 text-end font-medium text-text-secondary">{percentExamHeader ?? t("col.percentExam")}</th>}
              {showDuration && <th className="px-3 py-2 text-end font-medium text-text-secondary">{t("col.duration")}</th>}
              {maxScoreColumn && <th className="px-3 py-2 text-end font-medium text-text-secondary">{t("col.maxScore")}</th>}
              {showPoints && <th className="px-3 py-2 text-end font-medium text-text-secondary">{t("col.points")}</th>}
              {commandColumn && <th className="px-3 py-2 text-end font-medium text-text-secondary">{t("col.commands")}</th>}
              {blockMode && <th className="px-3 py-2 text-end font-medium text-text-secondary">{t("col.blockPoints")}</th>}
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
                      <span data-exam-content>{display}</span>
                      <span className="ms-2 text-caption">{t("omittedLabel")}</span>
                    </td>
                    <td className="px-3 py-2 text-end">{row.base.questionCount ?? "—"}</td>
                    {partPercentColumn && <td className="px-3 py-2 text-end">{t("notCounted")}</td>}
                    {showPercents && <td className="px-3 py-2 text-end">{t("notCounted")}</td>}
                    {showDuration && <td className="px-3 py-2 text-end">—</td>}
                    {showPoints && <td className="px-3 py-2 text-end">{t("notCounted")}</td>}
                    {commandColumn && <td className="px-3 py-2 text-end">{t("notCounted")}</td>}
                    {blockMode && <td className="px-3 py-2 text-end">{t("notCounted")}</td>}
                  </tr>
                );
              }
              if (row.kind === "block" && row.block) {
                const info = row.block;
                const total = blockSum(row.parentKey ?? "", (item) => item.questions);
                return (
                  <tr key={row.key} className="border-b border-border last:border-b-0">
                    <td className="px-3 py-2 pl-8 align-top text-text-secondary">
                      <span className="me-1 text-text-muted">–</span>
                      {display}
                    </td>
                    <td className="px-3 py-2 align-top text-end">
                      <span className="px-3 py-2 font-medium">{info.questions}</span>
                      {info.sets !== null && info.setSize !== null && (
                        <span className="block px-3 text-caption text-text-muted">{t("blockUnit", { sets: info.sets, size: info.setSize })}</span>
                      )}
                    </td>
                    <td className="px-3 py-2 align-top text-end text-text-secondary">
                      <span className="px-3 py-2">{percentText(total ? info.questions / total : null)}</span>
                    </td>
                    {commandColumn && (
                      <td className="px-3 py-2 align-top text-end text-text-secondary">
                        <span className="px-3 py-2">{info.commands}</span>
                      </td>
                    )}
                    <td className="px-3 py-2 align-top text-end text-text-secondary">
                      <span className="px-3 py-2">{info.points ?? "—"}</span>
                    </td>
                  </tr>
                );
              }
              return (
                <tr key={row.key} className="border-b border-border last:border-b-0">
                  <td className={cn("px-3 py-2 align-top", row.kind === "section" && "pl-8 text-text-secondary")}>
                    {row.kind === "section" ? <span className="me-1 text-text-muted">–</span> : null}
                    {row.kind === "part" ? <span data-exam-content>{display}</span> : display}
                    {row.choice && <span className="ms-2 text-caption text-text-muted">{t("choiceNote")}</span>}
                  </td>
                  <td className="px-3 py-2 align-top">
                    <div className="flex justify-end">
                      {row.group ? (
                        <span className="px-3 py-2 text-text-muted">{t("sumOfSubjects")}</span>
                      ) : (
                        <NumberCell
                          row={cellRow}
                          field="questionCount"
                          error={rowIssues?.questionCount}
                          locked={readOnly || row.lockedCount}
                          onCellChange={onCellChange}
                        />
                      )}
                    </div>
                  </td>
                  {partPercentColumn && (
                    <td className="px-3 py-2 align-top text-end">
                      <span className="px-3 py-2 text-text-secondary">
                        {row.kind === "section"
                          ? percentText(inPart(row))
                          : row.kind === "part" && blocksOf(row.key).length > 0
                            ? percentText(1)
                            : "—"}
                      </span>
                    </td>
                  )}
                  {showPercents && (
                    <td className="px-3 py-2 align-top text-end">
                      <span className="px-3 py-2 text-text-secondary">{percentText(inExam(row))}</span>
                    </td>
                  )}
                  {showDuration && (
                    <td className="px-3 py-2 align-top">
                      <div className="flex justify-end">
                        {row.kind === "part" ? (
                          <NumberCell row={cellRow} field="durationMinutes" error={rowIssues?.durationMinutes} locked={readOnly} onCellChange={onCellChange} />
                        ) : (
                          <span className="px-3 py-2 text-text-muted">—</span>
                        )}
                      </div>
                    </td>
                  )}
                  {maxScoreColumn && (
                    <td className="px-3 py-2 align-top">
                      <div className="flex justify-end">
                        {row.kind === "part" && maxScore ? <MaxScoreCell state={maxScore} /> : <span className="px-3 py-2 text-text-muted">—</span>}
                      </div>
                    </td>
                  )}
                  {showPoints && (
                    <td className="px-3 py-2 align-top">
                      <div className="flex justify-end">
                        {row.kind === "part" ? (
                          <NumberCell row={cellRow} field="maxPoints" error={rowIssues?.maxPoints} locked={readOnly} onCellChange={onCellChange} />
                        ) : (
                          <span className="px-3 py-2 text-text-muted">{row.maxPoints || "—"}</span>
                        )}
                      </div>
                    </td>
                  )}
                  {commandColumn && (
                    <td className="px-3 py-2 align-top text-end">
                      <span className="px-3 py-2 text-text-secondary">{row.kind === "part" ? (blockSum(row.key, (item) => item.commands) ?? "—") : "—"}</span>
                    </td>
                  )}
                  {blockMode && (
                    <td className="px-3 py-2 align-top text-end">
                      <span className="px-3 py-2 text-text-secondary">{row.kind === "part" ? (blockSum(row.key, (item) => item.points) ?? "—") : "—"}</span>
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
          {!reviewLayout && (
          <tfoot>
            <tr className="border-t border-border bg-subtle font-medium">
              <td className="px-3 py-2">{t("total")}</td>
              <td className="px-3 py-2 text-end">{totals.questions ?? "—"}</td>
              {partPercentColumn && <td className="px-3 py-2 text-end">{blockMode && totals.questions !== null ? format.number(1, PERCENT) : "—"}</td>}
              {showPercents && (
                <td className="px-3 py-2 text-end">{totals.questions !== null ? format.number(1, PERCENT) : "—"}</td>
              )}
              {showDuration && <td className="px-3 py-2 text-end">{totals.durationMinutes ?? "—"}</td>}
              {showPoints && <td className="px-3 py-2 text-end">{totals.maxPoints ?? "—"}</td>}
              {commandColumn && <td className="px-3 py-2 text-end">{totals.commands ?? "—"}</td>}
              {blockMode && <td className="px-3 py-2 text-end">{totals.blockPoints ?? "—"}</td>}
            </tr>
          </tfoot>
          )}
        </table>
      </ScrollableTable>
      {blockMode && !rows.some((row) => row.kind === "part" && row.lockedCount) && (
        <p className="border-t border-border px-3 py-2 text-caption text-text-muted">{t("blockRowsNote")}</p>
      )}
      {maxScore && !maxScoreColumn && <FooterField field="maxScore" label={t("maxScore")} unit={t("points")} state={maxScore} position="bottom" />}
    </div>
  );
}
