"use client";

import { cn } from "@thi360/ui";
import { ChevronRight } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId } from "react";
import { AllocationGap } from "../components/allocation-gap";
import type { MatrixLabels } from "../components/block-matrix-editor";
import { OptionalNumberInput } from "../components/create-exam-fields";
import { ScrollableTable } from "../components/scrollable-table";
import type { MatrixAxes } from "../matrix";
import type { ExamSection } from "../types";
import { EnforcementControl } from "./enforcement-control";
import {
  dimensionCell,
  dimensionDeclared,
  dimensionEnforcement,
  enforcementModeOf,
  gradeVisible,
  type MatrixDimensionKey,
  setDimensionCell,
} from "./row-list-model";
import type { SubjectFormEntry } from "./registry";

export interface ReferenceBlockProps {
  section: ExamSection;
  entry: SubjectFormEntry;
  axes: MatrixAxes;
  labels: MatrixLabels;
  subjectName: string;
  /** Số câu của mục: mẫu số của "Đã khai x/y". */
  count: number | null;
  onChange: (update: (section: ExamSection) => ExamSection) => void;
}

/**
 * Phân bổ đối chiếu của mục (FTM R-7, R-8, R-11): khối gấp sẵn ở cuối Danh sách dòng, phân bố lớp và độ khó của mục với mức áp dụng
 * theo kỳ (`EnforcementControl`, không có nút Bắt buộc/Tham khảo chung). Khối lớp không hiện ở môn bị ẩn nhưng dữ liệu lớp giữ nguyên
 * khi lưu. Ô để trống khác ô nhập 0; chiều không còn ô nào ghi mảng rỗng (`emptyDimensionIsEmptyArray`). Nhập theo số câu: nhập %
 * ở các kỳ cho nhập % thuộc lát CR-3b.
 */
export function ReferenceBlock({ section, entry, axes, labels, subjectName, count, onChange }: ReferenceBlockProps) {
  const t = useTranslations("exams.subjectForm.rows.reference");
  const headingId = useId();
  const dimensions: MatrixDimensionKey[] = gradeVisible(entry) ? ["GRADE", "DIFFICULTY"] : ["DIFFICULTY"];

  return (
    <details className="group rounded-md border border-border" data-reference-block aria-labelledby={headingId}>
      <summary className="flex cursor-pointer list-none flex-wrap items-center gap-2 px-4 py-3 text-label font-semibold text-text">
        <ChevronRight aria-hidden className="size-4 transition-transform group-open:rotate-90 motion-reduce:transition-none" />
        <span id={headingId}>{t("title")}</span>
        <span className="text-caption font-normal text-text-muted">{t("hint")}</span>
      </summary>
      <div className="flex flex-col gap-4 border-t border-border p-4">
        {dimensions.map((dimension) => (
          <DimensionBlock
            key={dimension}
            dimension={dimension}
            section={section}
            entry={entry}
            axes={axes}
            labels={labels}
            subjectName={subjectName}
            count={count}
            onChange={onChange}
          />
        ))}
      </div>
    </details>
  );
}

function DimensionBlock({ dimension, section, entry, axes, labels, subjectName, count, onChange }: ReferenceBlockProps & { dimension: MatrixDimensionKey }) {
  const t = useTranslations("exams.subjectForm.rows.reference");
  const captionId = useId();
  const grade = dimension === "GRADE";
  const codes = grade ? axes.grades : axes.difficulties;
  const label = grade ? labels.grade : labels.difficulty;
  const title = t(grade ? "gradeTitle" : "difficultyTitle");
  const matrix = section.matrix;
  const declared = dimensionDeclared(matrix, dimension);
  const missing = count === null ? null : count - declared.total;

  const setCell = (code: string, value: number | null) =>
    onChange((current) => ({ ...current, matrix: setDimensionCell(current.matrix, dimension, code, value, axes, entry.enforcement) }));

  return (
    <section className="flex min-w-0 flex-col gap-2" aria-labelledby={captionId} data-reference-dimension={dimension}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h4 id={captionId} className="text-label font-semibold text-text">
          {t("dimensionTitle", { subject: subjectName, dimension: title })}
        </h4>
        <EnforcementControl
          mode={enforcementModeOf(entry, matrix, dimension)}
          name={title}
          value={dimensionEnforcement(matrix, dimension)}
          declared={declared.cells > 0}
        />
      </div>
      <ScrollableTable labelledBy={captionId} className="rounded-md border border-border">
        <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
          <caption className="sr-only">{t("caption", { subject: subjectName, dimension: title })}</caption>
          <thead>
            <tr className="text-left text-caption text-text-muted">
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{title}</th>
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">{t("questions")}</th>
            </tr>
          </thead>
          <tbody>
            {codes.map((code) => (
              <tr key={code}>
                <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">{label(code)}</th>
                <td className="border-b border-border px-3 py-1.5 text-right">
                  <OptionalNumberInput
                    className="ml-auto h-9 w-20"
                    aria-label={t("cellAria", { dimension: title, label: label(code) })}
                    value={dimensionCell(matrix, dimension, code)}
                    onValueChange={(value) => setCell(code, value === null ? null : Math.max(0, Math.trunc(value)))}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </ScrollableTable>
      {declared.cells > 0 && (
        <p className={cn("flex flex-wrap items-center gap-2 text-caption", missing !== 0 ? "text-text-secondary" : "text-text-muted")} role="status">
          <output data-reference-declared={dimension}>{count === null ? t("declaredNoTotal", { total: declared.total }) : t("declared", { total: declared.total, count })}</output>
          {missing !== null && missing !== 0 && <AllocationGap missing={missing} />}
        </p>
      )}
    </section>
  );
}
