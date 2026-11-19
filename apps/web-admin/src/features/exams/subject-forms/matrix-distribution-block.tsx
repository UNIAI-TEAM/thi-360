"use client";

import { Button, cn, DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger, Input, Label } from "@thi360/ui";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { ratePercent } from "../allocation";
import { parseNumberText } from "../components/block-number-input";
import { apportionByPercent } from "../matrix";
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

/** Số đang lưu của ô để hiện lại: `text` rỗng là ô để trống (khác ô 0). */
const textOf = (value: number | null) => (value === null ? "" : String(value).replace(".", ","));

interface CellProps {
  /** Nhãn của ô (tên khối lớp hoặc độ khó). */
  label: string;
  field: string;
  stored: number | null;
  /** Số câu quy ra của ô: khi ma trận lưu theo %, các ô quy chung bằng phần dư lớn nhất. */
  count: number | null;
  unit: "COUNT" | "PERCENT";
  total: number | null;
  twoColumns: boolean;
  onEdit: (edit: { unit: "COUNT" | "PERCENT"; value: number | null }) => void;
  onRaw: (raw: { text: string; code: string } | null) => void;
}

/**
 * Một ô khối lớp hoặc độ khó. Ô trống là ô chưa khai (không ghi), "0" là ô khai 0. Hai cột Số câu / Tỷ lệ (V-ACT, R-9): gõ ở cột nào
 * thì ma trận ghi theo đơn vị đó (`COUNT` hay `PERCENT`) và cột kia hiện số quy đổi; các kỳ khác một ô số câu kèm % chỉ đọc bên cạnh.
 */
function MatrixCell({ label, field, stored, count, unit, total, twoColumns, onEdit, onRaw }: CellProps) {
  const t = useTranslations("exams.subjectForm.matrix.distribution");
  const tCommon = useTranslations("exams.subjectForm.common.countPercent");
  const id = useId();
  const shownCount = stored === null ? null : unit === "COUNT" ? stored : count;
  const shownPercent = stored === null ? null : unit === "PERCENT" ? stored : total ? ratePercent(stored, total) : null;
  const [countText, setCountText] = useState(textOf(shownCount));
  const [percentText, setPercentText] = useState(textOf(unit === "PERCENT" ? shownPercent : null));
  const [last, setLast] = useState({ count: shownCount, percent: shownPercent });
  const [invalid, setInvalid] = useState(false);
  // Giá trị đổi từ bên ngoài (đổi tổng, hoàn tác, ô khác quy đổi lại): hiện giá trị mới thay cho chữ cũ.
  if (last.count !== shownCount || last.percent !== shownPercent) {
    setLast({ count: shownCount, percent: shownPercent });
    if (parseNumberText(countText, false) !== (shownCount ?? 0) || (countText.trim() === "") !== (shownCount === null)) setCountText(textOf(shownCount));
    if (twoColumns && (percentText.trim() === "") !== (shownPercent === null || unit === "COUNT")) setPercentText(textOf(unit === "PERCENT" ? shownPercent : null));
  }

  const changeCount = (text: string) => {
    setCountText(text);
    setPercentText("");
    if (text.trim() === "") {
      setInvalid(false);
      onRaw(null);
      return onEdit({ unit: "COUNT", value: null });
    }
    const parsed = parseNumberText(text, false);
    if (parsed === null) {
      setInvalid(true);
      return onRaw({ text, code: "invalidNumber" });
    }
    setInvalid(false);
    onRaw(null);
    onEdit({ unit: "COUNT", value: parsed });
  };
  const changePercent = (text: string) => {
    setPercentText(text);
    if (text.trim() === "") {
      setInvalid(false);
      onRaw(null);
      return onEdit({ unit: "PERCENT", value: null });
    }
    const parsed = parseNumberText(text, true);
    if (parsed === null || parsed > 100) {
      setInvalid(true);
      return onRaw({ text, code: "invalidNumber" });
    }
    setInvalid(false);
    onRaw(null);
    onEdit({ unit: "PERCENT", value: parsed });
  };

  return (
    <div data-form-field={field} className="flex min-w-0 flex-col gap-1">
      <Label htmlFor={`${id}-count`}>{label}</Label>
      <span className="flex flex-wrap items-end gap-2">
        <span className="flex flex-col gap-1">
          {twoColumns && <span className="text-caption text-text-muted">{tCommon("count")}</span>}
          <Input id={`${id}-count`} aria-invalid={invalid || undefined} inputMode="numeric" autoComplete="off" value={countText} onChange={(event) => changeCount(event.target.value)} className="w-20 text-right tabular-nums" />
        </span>
        {twoColumns ? (
          <span className="flex flex-col gap-1">
            <Label htmlFor={`${id}-percent`} className="text-caption font-normal text-text-muted">
              {tCommon("percent")}
            </Label>
            <Input
              id={`${id}-percent`}
              aria-invalid={invalid || undefined}
              aria-label={t("percentAria", { label })}
              inputMode="decimal"
              autoComplete="off"
              value={percentText}
              placeholder={unit === "COUNT" && shownPercent !== null ? textOf(shownPercent) : undefined}
              onChange={(event) => changePercent(event.target.value)}
              className="w-20 text-right tabular-nums"
            />
          </span>
        ) : (
          shownPercent !== null && <span className="pb-2 text-caption text-text-muted tabular-nums">{tCommon("share", { percent: shownPercent })}</span>
        )}
      </span>
      {invalid && (
        <p role="alert" className="text-caption text-danger">
          {tCommon("invalidNumber")}
        </p>
      )}
    </div>
  );
}

interface DimensionProps extends MatrixBlockProps {
  dimension: MatrixDimensionKey;
  title: string;
  codes: readonly string[];
  codeLabel: (code: string) => string;
}

/** Một hàng của MT (khối lớp hoặc độ khó): các ô, mức áp dụng theo kỳ (R-8), "Đã khai x/y" hoặc "Tổng x%, cần 100%" ngay dưới. */
function MatrixDimension({ dimension, title, codes, codeLabel, section, entry, total, axes, raw, onChange }: DimensionProps) {
  const t = useTranslations("exams.subjectForm.matrix.distribution");
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

  return (
    <div data-form-field={fieldBase} className="flex flex-col gap-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h4 className="text-label font-semibold text-text">{title}</h4>
        <EnforcementControl
          mode={entry.enforcement}
          name={title}
          value={enforcement}
          declared={declared}
          onChange={(next) => onChange((current) => ({ ...current, matrix: setDimensionEnforcement(current.matrix, dimension, next) }))}
        />
      </div>
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

/**
 * Hàng Khối lớp và Độ khó của MT (FTM R-4): hai hàng ô số, không có nút Khai báo/Bỏ, kết hợp hay đơn vị đếm (R-13). Liên lớp chỉ có ở V-ACT
 * Toán; khối lớp ẩn theo R-11; V-ACT Tiếng Việt chỉ hiện hàng khối lớp khi bài đã khai số hoặc người dùng thêm từ "Thêm điều kiện".
 */
export function MatrixDistributionBlock(props: MatrixBlockProps) {
  const { entry, section, axes, labels } = props;
  const t = useTranslations("exams.subjectForm.matrix.distribution");
  const [gradeAdded, setGradeAdded] = useState(false);
  const gradeCodes = axes.grades.filter((code) => code !== "GRADE_MIXED" || entry.gradeMixedColumn);
  const showGrade = !entry.hideGrade && (!entry.gradeOptional || gradeAdded || dimensionDeclared(section.matrix, "grade"));

  return (
    <section aria-label={t("title")} className="flex flex-col gap-4 rounded-md border border-border p-4">
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
                <DropdownMenuItem onSelect={() => setGradeAdded(true)}>{t("gradeCondition")}</DropdownMenuItem>
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
