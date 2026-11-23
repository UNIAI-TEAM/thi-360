"use client";

import { Button, Chip, cn, DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useRef, useState } from "react";
import { apportionByPercent } from "../matrix";
import type { QuotaEnforcement } from "../types";
import { EnforcementControl } from "./enforcement-control";
import type { MatrixBlockProps } from "./matrix-block-props";
import { MatrixCell } from "./matrix-cell";
import {
  dimensionDeclared,
  dimensionEnforcement,
  dimensionTotal,
  editMatrixCell,
  initialEnforcement,
  matrixCell,
  setDimensionEnforcement,
  type MatrixDimensionKey,
} from "./matrix-form-model";
import { percentsAreComplete } from "./percent";

interface DimensionProps extends MatrixBlockProps {
  dimension: MatrixDimensionKey;
  title: string;
  codes: readonly string[];
  codeLabel: (code: string) => string;
}

function MatrixDimension({ dimension, title, codes, codeLabel, section, entry, kindCode, total, axes, raw, onChange }: DimensionProps) {
  const t = useTranslations("exams.subjectForm.matrix.distribution");
  const tHsa = useTranslations("exams.subjectForm.matrix.hsa");
  const matrix = section.matrix;
  const twoColumns = entry.percentEntry === "TWO_COLUMNS";
  const unit = matrix?.unit ?? "COUNT";
  const declared = dimensionDeclared(matrix, dimension);
  const declaredCodes = codes.filter((code) => matrixCell(matrix, dimension, code) !== null);
  const converted = unit === "PERCENT" && total !== null ? apportionByPercent(declaredCodes.map((code) => matrixCell(matrix, dimension, code) ?? 0), total).results : [];
  const sum = dimensionTotal(matrix, dimension);
  const fieldBase = dimension === "grade" ? "GRADE" : "DIFFICULTY";
  const enforcement: QuotaEnforcement = dimensionEnforcement(matrix, dimension);
  const percentMismatch = unit === "PERCENT" && declared && !percentsAreComplete(declaredCodes.map((code) => matrixCell(matrix, dimension, code) ?? 0));
  const gradeOptional = kindCode === "HSA" && (section.subjectCode === "SU" || section.subjectCode === "DIA") && dimension === "grade";

  return (
    <div data-form-field={fieldBase} className="flex flex-col gap-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h4 className="text-label font-semibold text-text">{title}</h4>
        {gradeOptional && !declared ? (
          <Chip variant="tag" className="w-fit">
            {tHsa("notApplied")}
          </Chip>
        ) : (
          <EnforcementControl
            mode={entry.enforcement}
            name={title}
            value={enforcement}
            declared={declared}
            lockReason={matrix?.basis !== undefined && matrix.basis !== "QUESTION" ? "promptBasis" : undefined}
            onChange={(next) => onChange((current) => ({ ...current, matrix: setDimensionEnforcement(current.matrix, dimension, next) }))}
          />
        )}
      </div>
      {gradeOptional && !declared && <p className="text-caption text-text-muted">{tHsa("gradeOptionalHint")}</p>}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {codes.map((code) => (
          <MatrixCell
            key={code}
            label={codeLabel(code)}
            field={`${fieldBase}:${code}`}
            stored={matrixCell(matrix, dimension, code)}
            count={converted[declaredCodes.indexOf(code)] ?? null}
            unit={unit}
            total={total}
            twoColumns={twoColumns}
            onEdit={(edit) =>
              onChange((current) => ({
                ...current,
                matrix: editMatrixCell(current.matrix, { dimension, code, ...edit }, { total: total ?? 0, axes, enforcement: initialEnforcement(entry.enforcement) }),
              }))
            }
            onRaw={(rawInput) => (rawInput ? raw.set(`${fieldBase}:${code}`, rawInput.text, rawInput.code) : raw.clear(`${fieldBase}:${code}`))}
            raw={raw.get(`${fieldBase}:${code}`)}
          />
        ))}
      </div>
      {declared && (
        <p role="status" className={cn("text-caption", percentMismatch || (unit === "COUNT" && total !== null && sum !== total && entry.enforcement === "DECLARED_REQUIRED") ? "text-danger" : "text-text-muted")}>
          {unit === "PERCENT" ? (percentMismatch ? t("percentNeeds100", { total: Math.round(sum * 100) / 100 }) : t("percentTotal", { total: Math.round(sum * 100) / 100 })) : t("declared", { declared: sum, total: total ?? 0 })}
        </p>
      )}
    </div>
  );
}

export function MatrixDistributionBlock(props: MatrixBlockProps) {
  const { entry, section, axes, labels, raw } = props;
  const t = useTranslations("exams.subjectForm.matrix.distribution");
  const [gradeAdded, setGradeAdded] = useState(false);
  const sectionRef = useRef<HTMLElement>(null);
  const gradeCodes = axes.grades.filter((code) => code !== "GRADE_MIXED" || entry.gradeMixedColumn);
  // Hàng khối lớp hiện khi bài đã khai số, người dùng vừa thêm, hoặc còn chữ gõ sai ở một ô lớp (nhập thô sống lâu hơn màn: mở lại không giấu lỗi).
  const showGrade = !entry.hideGrade && (!entry.gradeOptional || gradeAdded || dimensionDeclared(section.matrix, "grade") || raw.hasPrefix("GRADE:"));
  // Mục "Khối lớp" bỏ cả menu cùng nút gọi nó: đưa tiêu điểm về ô lớp đầu tiên để người dùng bàn phím không mất chỗ (R1-3).
  const addGrade = () => {
    setGradeAdded(true);
    requestAnimationFrame(() => sectionRef.current?.querySelector<HTMLElement>('[data-form-field="GRADE"] input')?.focus());
  };

  return (
    <section ref={sectionRef} aria-label={t("title")} className="flex flex-col gap-4 rounded-md border border-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h3 className="text-title font-semibold text-text">{t("title")}</h3>
          <p className="text-caption text-text-muted">{t("hint")}</p>
        </div>
        {!entry.hideGrade && entry.gradeOptional && !showGrade && (
          <div data-form-field="GRADE">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button type="button" variant="secondary" size="sm">
                  <Plus />
                  <span>{t("addCondition")}</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent aria-label={t("conditionMenu")} className="w-64 max-w-[calc(100vw-2rem)]">
                <DropdownMenuItem onSelect={addGrade}>{t("gradeCondition")}</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        )}
      </div>
      {showGrade && <MatrixDimension {...props} dimension="grade" title={t("grade")} codes={gradeCodes} codeLabel={labels.grade} />}
      <MatrixDimension {...props} dimension="difficulty" title={t("difficulty")} codes={axes.difficulties} codeLabel={labels.difficulty} />
    </section>
  );
}
