"use client";

import { Button } from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useId } from "react";
import { AllocationGap } from "../components/allocation-gap";
import type { MatrixLabels } from "../components/block-matrix-editor";
import { OptionalNumberInput } from "../components/create-exam-fields";
import { ScrollableTable } from "../components/scrollable-table";
import { combinedGrid, convertMatrixMode, gridToCells, type MatrixAxes, sum } from "../matrix";
import type { BlockMatrix } from "../types";
import { blankMatrix } from "./row-list-model";

/** Hướng chuyển ở bước xem trước (R-10): từ riêng từng chiều sang bảng kết hợp, hoặc ngược lại. */
export type CombinedDirection = "TO_COMBINED" | "TO_SEPARATE";

/** Ma trận sau chuyển đổi; ma trận chưa có mở đầu bằng bảng trống ở mức Chỉ đối chiếu (V-SAT mặc định, R-8). */
export function convertForPreview(matrix: BlockMatrix | null, direction: CombinedDirection, axes: MatrixAxes): BlockMatrix {
  const base = matrix ?? { ...blankMatrix(), gradeEnforcement: "REFERENCE" as const, difficultyEnforcement: "REFERENCE" as const };
  const converted = convertMatrixMode(base, direction === "TO_COMBINED" ? "COMBINED" : "SEPARATE", axes);
  const enforcement = base.gradeEnforcement === "REQUIRED" || base.difficultyEnforcement === "REQUIRED" ? "REQUIRED" : "REFERENCE";
  if (direction === "TO_COMBINED") return { ...converted, gradeEnforcement: enforcement, difficultyEnforcement: enforcement };
  return base.cells.every((cell) => cell.value === 0) ? { ...converted, byGrade: [], byDifficulty: [] } : converted;
}

interface CombinedPreviewProps {
  direction: CombinedDirection;
  matrix: BlockMatrix | null;
  axes: MatrixAxes;
  labels: MatrixLabels;
  onConfirm: (matrix: BlockMatrix) => void;
  onCancel: () => void;
}

/**
 * Bảng xem trước chuyển đổi giữa riêng từng chiều và kết hợp Lớp × Độ khó (R-10, CHUNG §7.1): hiện dữ liệu sẽ chuyển; ma trận chỉ ghi khi
 * người dùng xác nhận, hủy thì không đổi gì (AC 49).
 */
export function CombinedPreview({ direction, matrix, axes, labels, onConfirm, onCancel }: CombinedPreviewProps) {
  const t = useTranslations("exams.subjectForm.rows.reference.combined");
  const titleId = useId();
  const next = convertForPreview(matrix, direction, axes);
  const toCombined = direction === "TO_COMBINED";
  const grid = combinedGrid(next, axes);
  const hasData = toCombined ? matrix !== null && (matrix.byGrade.length > 0 || matrix.byDifficulty.length > 0) : matrix !== null && matrix.cells.some((cell) => cell.value > 0);

  return (
    <section aria-labelledby={titleId} className="flex flex-col gap-3 rounded-md border border-border bg-subtle/40 p-4" data-combined-preview={direction}>
      <div className="flex flex-col gap-0.5">
        <h5 id={titleId} className="text-label font-semibold text-text">
          {t(toCombined ? "previewTitle" : "previewBackTitle")}
        </h5>
        <p className="text-caption text-text-muted">{hasData ? t(toCombined ? "previewHint" : "previewBackHint") : t(toCombined ? "previewEmpty" : "previewBackEmpty")}</p>
      </div>
      {toCombined ? (
        <ScrollableTable labelledBy={titleId} className="rounded-md border border-border">
          <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
            <caption className="sr-only">{t("previewCaption")}</caption>
            <thead>
              <tr className="text-left text-caption text-text-muted">
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("gradeHeader")}</th>
                {axes.difficulties.map((code) => (
                  <th key={code} scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">{labels.difficulty(code)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {axes.grades.map((grade, row) => (
                <tr key={grade}>
                  <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">{labels.grade(grade)}</th>
                  {axes.difficulties.map((difficulty, column) => (
                    <td key={difficulty} className="border-b border-border px-3 py-2 text-right">{grid[row]?.[column] ?? 0}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </ScrollableTable>
      ) : (
        hasData && (
          <ul className="flex flex-col gap-1 text-body text-text-secondary">
            <li>{t("backGrades", { values: next.byGrade.map((item) => `${labels.grade(item.code)} ${item.value}`).join(" · ") })}</li>
            <li>{t("backDifficulties", { values: next.byDifficulty.map((item) => `${labels.difficulty(item.code)} ${item.value}`).join(" · ") })}</li>
          </ul>
        )
      )}
      <div className="flex flex-wrap gap-2">
        <Button type="button" size="sm" onClick={() => onConfirm(next)}>
          {t("confirm")}
        </Button>
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          {t("cancel")}
        </Button>
      </div>
    </section>
  );
}

interface CombinedTableProps {
  matrix: BlockMatrix;
  axes: MatrixAxes;
  labels: MatrixLabels;
  /** Số câu của mục: mẫu số của "Tổng x/y câu". */
  count: number | null;
  /** Tổng tỷ lệ khác 100% khi nhập theo %: chặn lưu ở `saveBlockers` (MATRIX_PERCENT), màn hiện lỗi tại bảng. */
  percentError: number | null;
  onChange: (matrix: BlockMatrix) => void;
  onBack: () => void;
}

/** Bảng kết hợp Lớp × Độ khó của một phần V-SAT (R-10): ô nhập số câu hoặc tỷ lệ theo đơn vị của ma trận; lưu `section.matrix` `COMBINED`. */
export function CombinedTable({ matrix, axes, labels, count, percentError, onChange, onBack }: CombinedTableProps) {
  const t = useTranslations("exams.subjectForm.rows.reference.combined");
  const titleId = useId();
  const percent = matrix.unit === "PERCENT";
  const grid = combinedGrid(matrix, axes);
  const total = sum(grid.flat());
  const setCell = (row: number, column: number, value: number | null) => {
    const next = grid.map((cells) => [...cells]);
    next[row]![column] = Math.max(0, value ?? 0);
    const resetEnforcement =
      next.flat().every((cell) => cell === 0) &&
      (matrix.gradeEnforcement === "REQUIRED" || matrix.difficultyEnforcement === "REQUIRED")
        ? { gradeEnforcement: "REFERENCE" as const, difficultyEnforcement: "REFERENCE" as const }
        : {};
    onChange({ ...matrix, ...resetEnforcement, cells: gridToCells(next, axes) });
  };

  return (
    <section aria-labelledby={titleId} className="flex flex-col gap-2" data-reference-combined>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h5 id={titleId} className="text-label font-semibold text-text">
          {t("title")}
        </h5>
        <Button type="button" variant="link" size="sm" onClick={onBack}>
          {t("back")}
        </Button>
      </div>
      <ScrollableTable labelledBy={titleId} className="rounded-md border border-border">
        <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
          <caption className="sr-only">{t("caption")}</caption>
          <thead>
            <tr className="text-left text-caption text-text-muted">
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("gradeHeader")}</th>
              {axes.difficulties.map((code) => (
                <th key={code} scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">{labels.difficulty(code)}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {axes.grades.map((grade, row) => (
              <tr key={grade}>
                <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">{labels.grade(grade)}</th>
                {axes.difficulties.map((difficulty, column) => (
                  <td key={difficulty} className="border-b border-border px-3 py-1.5 text-right">
                    <OptionalNumberInput
                      className="ml-auto h-9 w-20"
                      aria-label={t(percent ? "cellPercentAria" : "cellAria", { grade: labels.grade(grade), difficulty: labels.difficulty(difficulty) })}
                      value={grid[row]?.[column] ?? 0}
                      onValueChange={(value) => setCell(row, column, percent ? value : value === null ? null : Math.trunc(value))}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </ScrollableTable>
      <p className="flex flex-wrap items-center gap-2 text-caption text-text-secondary" role="status">
        <output data-reference-declared="COMBINED">{percent ? t("totalPercent", { total }) : count === null ? t("totalNoCount", { total }) : t("total", { total, count })}</output>
        {!percent && count !== null && total !== count && <AllocationGap missing={count - total} />}
      </p>
      {percentError !== null && (
        <p role="alert" className="text-caption text-danger">
          {t("percentError", { total: percentError })}
        </p>
      )}
    </section>
  );
}
