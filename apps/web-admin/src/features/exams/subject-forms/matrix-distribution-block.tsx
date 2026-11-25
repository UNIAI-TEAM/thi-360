"use client";

import { Button, Chip, cn, DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger, Input, Label } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useRef, useState } from "react";
import { ratePercent } from "../allocation";
import { formatNumberText, parseNumberText, parsePercentText } from "../components/block-number-input";
import { LOCKED_FIELD } from "../components/locked-field";
import { apportionByPercent, sum as sumNumbers } from "../matrix";
import type { QuotaEnforcement } from "../types";
import { EnforcementControl } from "./enforcement-control";
import type { MatrixBlockProps } from "./matrix-block-props";
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

interface MatrixDimensionRowProps {
  label: string;
  field: string;
  stored: number | null;
  count: number | null;
  unit: "COUNT" | "PERCENT";
  total: number | null;
  twoColumns: boolean;
  readOnly?: boolean;
  onEdit: (edit: { unit: "COUNT" | "PERCENT"; value: number | null }) => void;
  onRaw: (raw: { text: string; code: string } | null) => void;
  raw?: { text: string; code: string } | null;
}

function MatrixDimensionRow({
  label,
  field,
  stored,
  count,
  unit,
  total,
  twoColumns,
  readOnly = false,
  onEdit,
  onRaw,
  raw = null,
}: MatrixDimensionRowProps) {
  const t = useTranslations("exams.subjectForm.matrix.distribution");
  const tCommon = useTranslations("exams.subjectForm.common.countPercent");
  const id = useId();

  const shownCount = stored === null ? null : unit === "COUNT" ? stored : count;
  const shownPercent = stored === null ? null : unit === "PERCENT" ? stored : total ? ratePercent(stored, total) : null;
  const rawIsPercent = raw?.code === "invalidPercent";

  const [countText, setCountText] = useState(() => (raw && !rawIsPercent ? raw.text : formatNumberText(shownCount)));
  const [percentText, setPercentText] = useState(() => (raw && rawIsPercent ? raw.text : formatNumberText(unit === "PERCENT" ? shownPercent : null)));
  const [last, setLast] = useState({ count: shownCount, percent: shownPercent });
  const [invalid, setInvalid] = useState(raw !== null);

  if (last.count !== shownCount || last.percent !== shownPercent) {
    setLast({ count: shownCount, percent: shownPercent });
    if (parseNumberText(countText, false) !== (shownCount ?? 0) || (countText.trim() === "") !== (shownCount === null)) {
      setCountText(formatNumberText(shownCount));
    }
    if (twoColumns && (percentText.trim() === "") !== (shownPercent === null || unit === "COUNT")) {
      setPercentText(formatNumberText(unit === "PERCENT" ? shownPercent : null));
    }
  }

  const accept = (edit: { unit: "COUNT" | "PERCENT"; value: number | null }) => {
    setInvalid(false);
    onRaw(null);
    onEdit(edit);
  };
  const reject = (text: string, code: "invalidNumber" | "invalidPercent") => {
    setInvalid(true);
    onRaw({ text, code });
  };
  const changeCount = (text: string) => {
    setCountText(text);
    setPercentText("");
    if (text.trim() === "") return accept({ unit: "COUNT", value: null });
    const parsed = parseNumberText(text, false);
    if (parsed === null) return reject(text, "invalidNumber");
    accept({ unit: "COUNT", value: parsed });
  };
  const changePercent = (text: string) => {
    setPercentText(text);
    if (text.trim() === "") return accept({ unit: "PERCENT", value: null });
    const parsed = parsePercentText(text);
    if (parsed === null) return reject(text, "invalidPercent");
    accept({ unit: "PERCENT", value: parsed });
  };

  const errorId = `${id}-error`;

  return (
    <tr data-form-field={field} className="hover:bg-subtle/20">
      <th scope="row" className="px-4 py-2 text-left font-normal text-text">
        <Label htmlFor={`${id}-count`} className="cursor-pointer font-medium text-text">
          {label}
        </Label>
        {invalid && (
          <p id={errorId} role="alert" className="mt-1 text-caption text-danger">
            {tCommon("invalidNumber")}
          </p>
        )}
      </th>
      <td className="px-3 py-2 text-right align-top">
        <Input
          id={`${id}-count`}
          aria-invalid={invalid || undefined}
          aria-describedby={invalid ? errorId : undefined}
          readOnly={readOnly}
          aria-readonly={readOnly || undefined}
          inputMode="numeric"
          autoComplete="off"
          value={countText}
          onChange={(event) => changeCount(event.target.value)}
          className={cn("ml-auto h-9 w-20 px-2.5 text-right tabular-nums", readOnly && LOCKED_FIELD)}
        />
      </td>
      <td className="px-4 py-2 text-right align-middle">
        {twoColumns ? (
          <Input
            id={`${id}-percent`}
            aria-invalid={invalid || undefined}
            aria-describedby={invalid ? errorId : undefined}
            aria-label={t("percentAria", { label })}
            readOnly={readOnly}
            aria-readonly={readOnly || undefined}
            inputMode="decimal"
            autoComplete="off"
            value={percentText}
            placeholder={unit === "COUNT" && shownPercent !== null ? formatNumberText(shownPercent) : undefined}
            onChange={(event) => changePercent(event.target.value)}
            className={cn("ml-auto h-9 w-20 px-2.5 text-right tabular-nums", readOnly && LOCKED_FIELD)}
          />
        ) : (
          <span className="text-caption font-medium text-text-muted tabular-nums">
            {shownPercent !== null ? tCommon("share", { percent: shownPercent }) : "—"}
          </span>
        )}
      </td>
    </tr>
  );
}

interface DimensionProps extends MatrixBlockProps {
  dimension: MatrixDimensionKey;
  badgeText: string;
  title: string;
  codes: readonly string[];
  codeLabel: (code: string) => string;
}

function MatrixDimension({ dimension, badgeText, title, codes, codeLabel, section, entry, kindCode, total, axes, raw, onChange }: DimensionProps) {
  const t = useTranslations("exams.subjectForm.matrix.distribution");
  const tHsa = useTranslations("exams.subjectForm.matrix.hsa");
  const tCommon = useTranslations("exams.subjectForm.common.countPercent");
  const matrix = section.matrix;
  const twoColumns = entry.percentEntry === "TWO_COLUMNS";
  const unit = matrix?.unit ?? "COUNT";
  const declared = dimensionDeclared(matrix, dimension);
  const declaredCodes = codes.filter((code) => matrixCell(matrix, dimension, code) !== null);
  const converted = unit === "PERCENT" && total !== null ? apportionByPercent(declaredCodes.map((code) => matrixCell(matrix, dimension, code) ?? 0), total).results : [];
  const sum = dimensionTotal(matrix, dimension);
  const convertedSum = sumNumbers(converted);
  const fieldBase = dimension === "grade" ? "GRADE" : "DIFFICULTY";
  const enforcement: QuotaEnforcement = dimensionEnforcement(matrix, dimension);
  const percentMismatch = unit === "PERCENT" && declared && !percentsAreComplete(declaredCodes.map((code) => matrixCell(matrix, dimension, code) ?? 0));
  const gradeOptional = kindCode === "HSA" && (section.subjectCode === "SU" || section.subjectCode === "DIA") && dimension === "grade";

  const countMismatch = unit === "COUNT" && total !== null && sum !== total && entry.enforcement === "DECLARED_REQUIRED";
  const computedPct =
    !declared
      ? null
      : unit === "PERCENT"
        ? Math.round(sum * 100) / 100
        : total && total > 0
          ? ratePercent(sum, total)
          : null;

  return (
    <div data-form-field={fieldBase} className="flex flex-col overflow-hidden rounded-lg border border-border bg-surface">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border bg-subtle/40 px-4 py-3">
        <div className="flex items-center gap-2">
          <span
            aria-hidden="true"
            className="inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-primary-subtle text-caption font-semibold text-primary"
          >
            {badgeText}
          </span>
          <h4 className="text-label font-semibold text-text">{title}</h4>
        </div>
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

      {gradeOptional && !declared && (
        <p className="border-b border-border bg-subtle/20 px-4 py-2 text-caption text-text-muted">{tHsa("gradeOptionalHint")}</p>
      )}

      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-body">
          <thead>
            <tr className="border-b border-border bg-subtle/25 text-left text-caption font-semibold text-text-secondary">
              <th scope="col" className="px-4 py-2">
                {dimension === "grade" ? t("colGrade") : t("colDifficulty")}
              </th>
              <th scope="col" className="w-24 px-3 py-2 text-right whitespace-nowrap">
                {t("colCount")}
              </th>
              <th scope="col" className="w-24 px-4 py-2 text-right whitespace-nowrap">
                {t("colPercent")}
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {codes.map((code) => (
              <MatrixDimensionRow
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
                    matrix: editMatrixCell(
                      current.matrix,
                      { dimension, code, ...edit },
                      { total: total ?? 0, axes, enforcement: initialEnforcement(entry.enforcement) },
                    ),
                  }))
                }
                onRaw={(rawInput) => (rawInput ? raw.set(`${fieldBase}:${code}`, rawInput.text, rawInput.code) : raw.clear(`${fieldBase}:${code}`))}
                raw={raw.get(`${fieldBase}:${code}`)}
              />
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t-2 border-border bg-subtle/40 text-caption font-semibold text-text">
              <th scope="row" className="px-4 py-2.5 text-left">
                <div className="flex flex-wrap items-center gap-2">
                  <span>{dimension === "grade" ? t("totalGrade") : t("totalDifficulty")}</span>
                  {declared && (
                    <>
                      <span aria-hidden="true" className="text-text-muted">
                        ·
                      </span>
                      <span role="status" className={cn("font-medium", percentMismatch || countMismatch ? "text-danger" : "text-text-muted")}>
                        {unit === "PERCENT"
                          ? percentMismatch
                            ? t("percentNeeds100", { total: Math.round(sum * 100) / 100 })
                            : t("percentTotal", { total: Math.round(sum * 100) / 100 })
                          : t("declared", { declared: sum, total: total ?? 0 })}
                      </span>
                    </>
                  )}
                </div>
              </th>
              <td className="px-3 py-2.5 text-right font-semibold tabular-nums whitespace-nowrap">
                {declared ? `${unit === "COUNT" ? sum : convertedSum} / ${total ?? 0}` : "—"}
              </td>
              <td className="px-4 py-2.5 text-right font-semibold tabular-nums whitespace-nowrap">
                {computedPct !== null ? tCommon("share", { percent: computedPct }) : "—"}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  );
}

export function MatrixDistributionBlock(props: MatrixBlockProps) {
  const { entry, section, axes, labels, raw } = props;
  const t = useTranslations("exams.subjectForm.matrix.distribution");
  const [gradeAdded, setGradeAdded] = useState(false);
  const sectionRef = useRef<HTMLElement>(null);
  const gradeCodes = axes.grades.filter((code) => code !== "GRADE_MIXED" || entry.gradeMixedColumn);
  const showGrade = !entry.hideGrade && (!entry.gradeOptional || gradeAdded || dimensionDeclared(section.matrix, "grade") || raw.hasPrefix("GRADE:"));

  const addGrade = () => {
    setGradeAdded(true);
    requestAnimationFrame(() => sectionRef.current?.querySelector<HTMLElement>('[data-form-field="GRADE"] input')?.focus());
  };

  return (
    <section ref={sectionRef} aria-label={t("title")} className="flex flex-col gap-3">
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

      <div className={cn("grid grid-cols-1 gap-4", showGrade && "lg:grid-cols-2")}>
        {showGrade && (
          <MatrixDimension {...props} dimension="grade" badgeText="2A" title={t("grade")} codes={gradeCodes} codeLabel={labels.grade} />
        )}
        <MatrixDimension
          {...props}
          dimension="difficulty"
          badgeText={showGrade ? "2B" : "2"}
          title={t("difficulty")}
          codes={axes.difficulties}
          codeLabel={labels.difficulty}
        />
      </div>
    </section>
  );
}
