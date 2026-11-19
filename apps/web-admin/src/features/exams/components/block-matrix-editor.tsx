"use client";

import { Badge, cn, focusRing, Label, Switch } from "@thi360/ui";
import { CircleCheck, Info, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId, useState, type ReactNode } from "react";
import {
  type Apportionment,
  combinedGrid,
  computeAllocation,
  convertMatrixMode,
  convertMatrixUnit,
  dimensionValues,
  gridToCells,
  hasCompetencyDimension,
  type MatrixAxes,
  toDimension,
  validateBlock,
} from "../matrix";
import { ratePercent } from "../allocation";
import { DISPLAY_NUMBER_FORMAT } from "../display-number";
import type { AvailableQuestionCounts, BlockMatrix, MatrixMode, MatrixUnit, QuestionBlock, QuotaBasis, QuotaEnforcement } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { ChoiceCardGroup } from "./create-exam-fields";
import { EnforcementToggle } from "./enforcement-toggle";

export interface MatrixLabels {
  grade: (code: string) => string;
  difficulty: (code: string) => string;
}

export interface BlockMatrixEditorProps {
  /** Nhóm đã chuẩn hóa: chỉ tiêu riêng và số câu có giá trị (nhóm không có chỉ tiêu riêng hiện ma trận trống). */
  block: QuestionBlock & { matrix: BlockMatrix; questionCount: number };
  axes: MatrixAxes;
  labels: MatrixLabels;
  /**
   * Năng lực cho chiều năng lực (V-ACT): năng lực đã lọc ở phạm vi của khối, hoặc mọi năng lực của môn. Rỗng: kỳ thi
   * không dùng năng lực, không hiện chiều này.
   */
  competencies: ReadonlyArray<{ code: string; name: string }>;
  onChange: (matrix: BlockMatrix) => void;
  /** Số câu Sẵn sàng khớp bộ lọc của khối; `undefined` khi đang tải hoặc lỗi. */
  available: AvailableQuestionCounts | undefined;
  availableState: "loading" | "error" | "ready";
  /**
   * `false`: chỉ tiêu riêng tùy chọn của nhóm chưa được khai (ma trận trống chỉ để nhập): chưa cảnh báo tổng lệch.
   * Mặc định `true`.
   */
  declared?: boolean;
}

const CELL_INPUT = "h-9 w-16 px-2";

/**
 * Ma trận phân bổ của một khối (bản vẽ `BaiThiCauTruc`, UniNote PBI-04 mục 7): kết hợp Khối lớp × Độ khó hoặc riêng
 * từng chiều; nhập số câu hoặc tỷ lệ %. Tỷ lệ quy đổi thành số câu nguyên theo phần dư lớn nhất, có bảng quy đổi và các
 * bước. Mỗi ô có gợi ý số câu nguồn; tổng lệch số câu của khối thì cảnh báo.
 */
export function BlockMatrixEditor({
  block,
  axes,
  labels,
  competencies,
  onChange,
  available,
  availableState,
  declared = true,
}: BlockMatrixEditorProps) {
  const t = useTranslations("exams.matrix");
  const format = useFormatter();
  const titleId = useId();
  const competencySwitchId = useId();
  const competencyHintId = useId();
  const [switched, setSwitched] = useState(false);
  const { matrix } = block;
  const percent = matrix.unit === "PERCENT";
  const allocation = computeAllocation(block, axes);
  const issues = declared
    ? validateBlock(block, axes).filter(
        (issue) => issue.code === "PERCENT_TOTAL" || issue.code === "ALLOCATION_TOTAL" || issue.code === "INVALID_VALUE",
      )
    : [];
  const number = (value: number) => format.number(value, DISPLAY_NUMBER_FORMAT);
  const questionBasis = (matrix.basis ?? "QUESTION") === "QUESTION";
  const gradeEnforcement: QuotaEnforcement = matrix.gradeEnforcement ?? "REQUIRED";
  const difficultyEnforcement: QuotaEnforcement = matrix.difficultyEnforcement ?? "REQUIRED";
  const referenceOnly = gradeEnforcement === "REFERENCE" && difficultyEnforcement === "REFERENCE";
  const rateText = (count: number) => {
    const rate = ratePercent(count, block.questionCount);
    return rate === null ? null : t("realRate", { value: format.number(rate, { maximumFractionDigits: 1 }) });
  };
  const setEnforcement = (which: "grade" | "difficulty" | "both", value: QuotaEnforcement) => {
    const next: BlockMatrix = { ...matrix };
    if (which !== "difficulty") next.gradeEnforcement = value;
    if (which !== "grade") next.difficultyEnforcement = value;
    onChange(next);
  };
  const setBasis = (basis: QuotaBasis) => onChange({ ...matrix, basis });

  const sourceOf = (grade: string | null, difficulty: string | null) =>
    (available?.cells ?? [])
      .filter((cell) => (grade === null || cell.gradeCode === grade) && (difficulty === null || cell.difficultyCode === difficulty))
      .reduce((total, cell) => total + cell.value, 0);

  const sourceHint = (id: string, need: number, grade: string | null, difficulty: string | null) => {
    if (availableState !== "ready" || !questionBasis) return null;
    const source = sourceOf(grade, difficulty);
    const short = need > source;
    return (
      <span id={id} className={cn("flex items-center justify-end gap-1 text-caption", short ? "text-warning" : "text-text-muted")}>
        {short && <TriangleAlert aria-hidden className="size-3.5 shrink-0" />}
        {short ? t("sourceShort", { count: source }) : t("source", { count: source })}
      </span>
    );
  };

  const setMode = (mode: MatrixMode) => {
    if (mode === matrix.mode) return;
    setSwitched(true);
    const converted = convertMatrixMode(matrix, mode, axes);
    // Ma trận kết hợp chỉ có một mức cho cả hai chiều (spec 3.2): lấy mức của chiều lớp.
    onChange(mode === "COMBINED" ? { ...converted, difficultyEnforcement: gradeEnforcement } : converted);
  };
  const setUnit = (unit: MatrixUnit) => onChange(convertMatrixUnit(matrix, unit, block.questionCount, axes));

  const setCell = (row: number, column: number, value: number) => {
    const grid = combinedGrid(matrix, axes);
    grid[row]![column] = value;
    onChange({ ...matrix, cells: gridToCells(grid, axes) });
  };
  const setDimension = (dimension: "byGrade" | "byDifficulty", index: number, value: number) => {
    const codes = dimension === "byGrade" ? axes.grades : axes.difficulties;
    const values = dimensionValues(matrix[dimension], codes);
    values[index] = value;
    onChange({ ...matrix, [dimension]: toDimension(values, codes) });
  };

  const competencyOn = hasCompetencyDimension(matrix);
  const competencyName = (code: string) => competencies.find((item) => item.code === code)?.name ?? code;
  const setCompetencyOn = (on: boolean) => {
    if (!on) {
      onChange({ ...matrix, byCompetency: undefined });
      return;
    }
    const codes = competencies.map((item) => item.code);
    // Một năng lực thì nhận đủ số câu của khối; nhiều năng lực để 0 cho người dùng chia.
    const full = percent ? 100 : block.questionCount;
    onChange({ ...matrix, byCompetency: codes.map((code) => ({ code, value: codes.length === 1 ? full : 0 })) });
  };
  const setCompetencyValue = (index: number, value: number) =>
    onChange({
      ...matrix,
      byCompetency: (matrix.byCompetency ?? []).map((item, position) => (position === index ? { ...item, value } : item)),
    });
  const competencySource = (code: string) => available?.byCompetency?.find((item) => item.code === code)?.value ?? 0;

  const unitSuffix = percent ? t("percentAria") : t("countAria");
  const totalText = (value: number) => (percent ? t("percentValue", { value: number(value) }) : number(value));
  const input = combinedGrid(matrix, axes);

  return (
    <section aria-labelledby={titleId} className="flex flex-col gap-4">
      <h3 id={titleId} className="text-title font-semibold text-text">
        {t("title")}
      </h3>
      <div className="flex flex-wrap gap-x-6 gap-y-3">
        <ChoiceCardGroup
          variant="pill"
          legend={t("mode")}
          name={`${block.id}-mode`}
          value={matrix.mode}
          onChange={setMode}
          options={[
            { value: "COMBINED", label: t("modeCombined") },
            { value: "SEPARATE", label: t("modeSeparate") },
          ]}
        />
        <ChoiceCardGroup
          variant="pill"
          legend={t("unit")}
          name={`${block.id}-unit`}
          value={matrix.unit}
          onChange={setUnit}
          options={[
            { value: "COUNT", label: t("unitCount") },
            { value: "PERCENT", label: t("unitPercent") },
          ]}
        />
      </div>
      <div className="flex flex-wrap items-start gap-x-6 gap-y-3">
        {matrix.mode === "COMBINED" && (
          <EnforcementToggle
            legend={t("matrixName")}
            name={`${block.id}-enforcement`}
            value={gradeEnforcement}
            basis={matrix.basis}
            onChange={(value) => setEnforcement("both", value)}
          />
        )}
        {referenceOnly ? (
          <ChoiceCardGroup
            variant="pill"
            legend={t("basis")}
            name={`${block.id}-basis`}
            value={matrix.basis ?? "QUESTION"}
            onChange={setBasis}
            options={[
              { value: "QUESTION", label: t("basisQUESTION") },
              { value: "PROMPT", label: t("basisPROMPT") },
              { value: "POINTS", label: t("basisPOINTS") },
            ]}
          />
        ) : (
          <p className="text-label text-text-secondary">{t("basisFixed", { unit: t("basisQUESTION") })}</p>
        )}
      </div>
      {switched && (
        <p role="status" className="flex items-start gap-2 rounded-md border border-info/30 bg-info-bg px-3 py-2 text-body text-info">
          <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
          {t("switchedNotice")}
        </p>
      )}

      {matrix.mode === "COMBINED" ? (
        <>
          <MatrixTable caption={percent ? t("combinedCaptionPercent") : t("combinedCaptionCount")}>
            <thead>
              <tr>
                <HeadCell sticky>{t("gradeHeader")}</HeadCell>
                {axes.difficulties.map((difficulty) => (
                  <HeadCell key={difficulty}>{labels.difficulty(difficulty)}</HeadCell>
                ))}
                <HeadCell last>{t("total")}</HeadCell>
              </tr>
            </thead>
            <tbody>
              {axes.grades.map((grade, row) => (
                <tr key={grade}>
                  <RowHead>{labels.grade(grade)}</RowHead>
                  {axes.difficulties.map((difficulty, column) => {
                    const hintId = `${block.id}-src-${row}-${column}`;
                    return (
                      <td key={difficulty} className="border-b border-border px-2 py-1.5 align-top">
                        <div className="flex flex-col items-end gap-0.5">
                          <span className="flex items-center gap-1">
                            <BlockNumberInput
                              className={CELL_INPUT}
                              allowDecimal={percent}
                              value={input[row]?.[column] ?? 0}
                              onValueChange={(value) => setCell(row, column, value)}
                              aria-label={t("cellAria", {
                                grade: labels.grade(grade),
                                difficulty: labels.difficulty(difficulty),
                                unit: unitSuffix,
                              })}
                              aria-describedby={availableState === "ready" ? hintId : undefined}
                            />
                            {percent && <span aria-hidden className="text-text-muted">%</span>}
                          </span>
                          {sourceHint(hintId, allocation.grid?.[row]?.[column] ?? 0, grade, difficulty)}
                        </div>
                      </td>
                    );
                  })}
                  <TotalCell last>{totalText(allocation.inputRowTotals[row] ?? 0)}</TotalCell>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <RowHead footer>{t("total")}</RowHead>
                {axes.difficulties.map((difficulty, column) => (
                  <TotalCell key={difficulty}>{totalText(allocation.inputColumnTotals[column] ?? 0)}</TotalCell>
                ))}
                <TotalCell strong last>{totalText(allocation.inputTotal)}</TotalCell>
              </tr>
            </tfoot>
          </MatrixTable>
          {percent && allocation.gridConversion && (
            <ConversionPanel
              blockCount={block.questionCount}
              conversion={allocation.gridConversion}
              cellLabel={(index) =>
                `${labels.grade(axes.grades[Math.floor(index / axes.difficulties.length)] ?? "")} · ${labels.difficulty(
                  axes.difficulties[index % axes.difficulties.length] ?? "",
                )}`
              }
            >
              <MatrixTable caption={t("conversionCaption", { count: block.questionCount })} captionVisible={false}>
                <thead>
                  <tr>
                    <HeadCell sticky>{t("gradeHeader")}</HeadCell>
                    {axes.difficulties.map((difficulty) => (
                      <HeadCell key={difficulty}>{labels.difficulty(difficulty)}</HeadCell>
                    ))}
                    <HeadCell last>{t("total")}</HeadCell>
                  </tr>
                </thead>
                <tbody>
                  {axes.grades.map((grade, row) => (
                    <tr key={grade}>
                      <RowHead>{labels.grade(grade)}</RowHead>
                      {axes.difficulties.map((difficulty, column) => {
                        const index = row * axes.difficulties.length + column;
                        const conversion = allocation.gridConversion!;
                        return (
                          <ConversionCell
                            key={difficulty}
                            theoretical={number(conversion.theoretical[index] ?? 0)}
                            result={conversion.results[index] ?? 0}
                            bonus={conversion.bonus[index] ?? false}
                          />
                        );
                      })}
                      <TotalCell last>{number(allocation.rowTotals[row] ?? 0)}</TotalCell>
                    </tr>
                  ))}
                </tbody>
              </MatrixTable>
            </ConversionPanel>
          )}
        </>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 @3xl:grid-cols-2">
            {(
              [
                { key: "byGrade", codes: axes.grades, label: labels.grade, conversion: allocation.gradeConversion },
                { key: "byDifficulty", codes: axes.difficulties, label: labels.difficulty, conversion: allocation.difficultyConversion },
              ] as const
            ).map((dimension) => {
              const values = dimensionValues(matrix[dimension.key], dimension.codes);
              const counts = dimension.key === "byGrade" ? allocation.byGrade : allocation.byDifficulty;
              const inputTotal = dimension.key === "byGrade" ? allocation.inputGradeTotal : allocation.inputDifficultyTotal;
              const countTotal = dimension.key === "byGrade" ? allocation.gradeTotal : allocation.difficultyTotal;
              return (
                <div key={dimension.key} className="flex min-w-0 flex-col gap-2">
                <EnforcementToggle
                  legend={dimension.key === "byGrade" ? t("byGradeCaption") : t("byDifficultyCaption")}
                  name={`${block.id}-${dimension.key}-enforcement`}
                  value={dimension.key === "byGrade" ? gradeEnforcement : difficultyEnforcement}
                  basis={matrix.basis}
                  onChange={(value) => setEnforcement(dimension.key === "byGrade" ? "grade" : "difficulty", value)}
                />
                <MatrixTable
                  caption={dimension.key === "byGrade" ? t("byGradeCaption") : t("byDifficultyCaption")}
                  captionVisible
                >
                  <thead>
                    <tr>
                      <HeadCell sticky>{dimension.key === "byGrade" ? t("gradeHeader") : t("difficultyHeader")}</HeadCell>
                      <HeadCell>{percent ? t("unitPercent") : t("unitCount")}</HeadCell>
                      {percent && <HeadCell>{t("converted")}</HeadCell>}
                    </tr>
                  </thead>
                  <tbody>
                    {dimension.codes.map((code, index) => {
                      const hintId = `${block.id}-${dimension.key}-src-${index}`;
                      return (
                        <tr key={code}>
                          <RowHead>{dimension.label(code)}</RowHead>
                          <td className="border-b border-border px-2 py-1.5 align-top">
                            <div className="flex flex-col items-end gap-0.5">
                              <span className="flex items-center gap-1">
                                <BlockNumberInput
                                  className={CELL_INPUT}
                                  allowDecimal={percent}
                                  value={values[index] ?? 0}
                                  onValueChange={(value) => setDimension(dimension.key, index, value)}
                                  aria-label={t("dimensionAria", { label: dimension.label(code), unit: unitSuffix })}
                                  aria-describedby={availableState === "ready" ? hintId : undefined}
                                />
                                {percent && <span aria-hidden className="text-text-muted">%</span>}
                              </span>
                              {sourceHint(
                                hintId,
                                counts[index] ?? 0,
                                dimension.key === "byGrade" ? code : null,
                                dimension.key === "byDifficulty" ? code : null,
                              )}
                            </div>
                          </td>
                          {percent && dimension.conversion && (
                            <ConversionCell
                              theoretical={number(dimension.conversion.theoretical[index] ?? 0)}
                              result={dimension.conversion.results[index] ?? 0}
                              bonus={dimension.conversion.bonus[index] ?? false}
                              rate={rateText(dimension.conversion.results[index] ?? 0)}
                            />
                          )}
                        </tr>
                      );
                    })}
                  </tbody>
                  <tfoot>
                    <tr>
                      <RowHead footer>{t("total")}</RowHead>
                      <TotalCell strong>{totalText(inputTotal)}</TotalCell>
                      {percent && <TotalCell strong>{t("questionsValue", { count: countTotal })}</TotalCell>}
                    </tr>
                  </tfoot>
                </MatrixTable>
                </div>
              );
            })}
          </div>
          <p className="text-caption text-text-muted">{t("separateNote")}</p>
        </>
      )}

      {(competencies.length > 0 || competencyOn) && (
        <section aria-label={t("byCompetencyCaption")} className="flex flex-col gap-3">
          <div className="flex items-start gap-3">
            <Switch
              id={competencySwitchId}
              checked={competencyOn}
              aria-describedby={competencyHintId}
              onCheckedChange={setCompetencyOn}
            />
            <div className="flex flex-col gap-0.5">
              <Label htmlFor={competencySwitchId} className="cursor-pointer text-body font-medium text-text">
                {t("competencyToggle")}
              </Label>
              <p id={competencyHintId} className="text-caption text-text-muted">
                {t("competencyHint")}
              </p>
            </div>
          </div>
          {competencyOn && allocation.competency && (
            <MatrixTable caption={t("byCompetencyCaption")} captionVisible>
              <thead>
                <tr>
                  <HeadCell sticky>{t("competencyHeader")}</HeadCell>
                  <HeadCell>{percent ? t("unitPercent") : t("unitCount")}</HeadCell>
                  {percent && <HeadCell>{t("converted")}</HeadCell>}
                </tr>
              </thead>
              <tbody>
                {allocation.competency.codes.map((code, index) => {
                  const hintId = `${block.id}-competency-src-${index}`;
                  const conversion = allocation.competency?.conversion;
                  const need = allocation.competency?.counts[index] ?? 0;
                  const source = competencySource(code);
                  return (
                    <tr key={code}>
                      <RowHead>{competencyName(code)}</RowHead>
                      <td className="border-b border-border px-2 py-1.5 align-top">
                        <div className="flex flex-col items-end gap-0.5">
                          <span className="flex items-center gap-1">
                            <BlockNumberInput
                              className={CELL_INPUT}
                              allowDecimal={percent}
                              value={allocation.competency?.input[index] ?? 0}
                              onValueChange={(value) => setCompetencyValue(index, value)}
                              aria-label={t("dimensionAria", { label: competencyName(code), unit: unitSuffix })}
                              aria-describedby={availableState === "ready" ? hintId : undefined}
                            />
                            {percent && <span aria-hidden className="text-text-muted">%</span>}
                          </span>
                          {availableState === "ready" && (
                            <span
                              id={hintId}
                              className={cn(
                                "flex items-center justify-end gap-1 text-caption",
                                need > source ? "text-warning" : "text-text-muted",
                              )}
                            >
                              {need > source && <TriangleAlert aria-hidden className="size-3.5 shrink-0" />}
                              {need > source ? t("sourceShort", { count: source }) : t("source", { count: source })}
                            </span>
                          )}
                        </div>
                      </td>
                      {percent && conversion && (
                        <ConversionCell
                          theoretical={number(conversion.theoretical[index] ?? 0)}
                          result={conversion.results[index] ?? 0}
                          bonus={conversion.bonus[index] ?? false}
                        />
                      )}
                    </tr>
                  );
                })}
              </tbody>
              <tfoot>
                <tr>
                  <RowHead footer>{t("total")}</RowHead>
                  <TotalCell strong>{totalText(allocation.competency.inputTotal)}</TotalCell>
                  {percent && <TotalCell strong>{t("questionsValue", { count: allocation.competency.total })}</TotalCell>}
                </tr>
              </tfoot>
            </MatrixTable>
          )}
        </section>
      )}

      {questionBasis ? (
        <AllocationStatus
          blockCount={block.questionCount}
          totals={[
            ...(matrix.mode === "COMBINED"
              ? [{ label: null, total: allocation.total, pending: referenceOnly && allocation.total === 0 }]
              : [
                  { label: t("byGradeCaption"), total: allocation.gradeTotal, pending: gradeEnforcement === "REFERENCE" && allocation.gradeTotal === 0 },
                  { label: t("byDifficultyCaption"), total: allocation.difficultyTotal, pending: difficultyEnforcement === "REFERENCE" && allocation.difficultyTotal === 0 },
                ]),
            ...(allocation.competency ? [{ label: t("byCompetencyCaption"), total: allocation.competency.total }] : []),
          ]}
          messages={issues.map((issue) => {
            const dimension =
              "dimension" in issue && issue.dimension !== "COMBINED"
                ? issue.dimension === "GRADE"
                  ? t("byGradeCaption")
                  : issue.dimension === "COMPETENCY"
                    ? t("byCompetencyCaption")
                    : t("byDifficultyCaption")
                : null;
            if (issue.code === "INVALID_VALUE") return { text: t("issues.invalidValue"), over: false };
            if (issue.code === "PERCENT_TOTAL") {
              return {
                text: t("issues.percentTotal", { dimension: dimension ?? t("matrixName"), value: number(issue.actual) }),
                over: issue.actual > 100,
              };
            }
            if (issue.code === "ALLOCATION_TOTAL") {
              return {
                text: t("issues.allocationTotal", {
                  dimension: dimension ?? t("matrixName"),
                  actual: issue.actual,
                  expected: issue.expected,
                }),
                over: issue.actual > issue.expected,
              };
            }
            return { text: "", over: false };
          })}
          declared={declared}
        />
      ) : (
        <p className="text-body text-text-secondary">
          {t("basisTotal", { total: number(allocation.inputTotal), unit: t(`basis${matrix.basis}`) })}
        </p>
      )}
    </section>
  );
}

function MatrixTable({
  caption,
  captionVisible = false,
  children,
}: {
  caption: string;
  captionVisible?: boolean;
  children: ReactNode;
}) {
  const captionId = useId();
  return (
    <div
      role="region"
      tabIndex={0}
      aria-labelledby={captionId}
      className={cn("relative max-w-full overflow-x-auto rounded-md border border-border", focusRing)}
    >
      <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
        <caption
          id={captionId}
          className={cn(captionVisible ? "px-3 py-2 text-left text-label font-semibold text-text" : "sr-only")}
        >
          {caption}
        </caption>
        {children}
      </table>
    </div>
  );
}

function HeadCell({ children, sticky = false, last = false }: { children: ReactNode; sticky?: boolean; last?: boolean }) {
  return (
    <th
      scope="col"
      className={cn(
        "border-b border-border bg-subtle px-2 py-2 text-right text-caption font-semibold whitespace-nowrap text-text-muted",
        sticky && "sticky left-0 z-1 text-left",
        // Cột Tổng luôn nằm trong khung và có đệm phải: không dính mép thẻ, không bị cuộn ngang che (V1-05).
        last && "sticky right-0 z-1 border-l pr-4",
      )}
    >
      {children}
    </th>
  );
}

function RowHead({ children, footer = false }: { children: ReactNode; footer?: boolean }) {
  return (
    <th
      scope="row"
      className={cn(
        "sticky left-0 z-1 bg-surface px-3 py-1.5 text-left font-medium whitespace-nowrap text-text",
        !footer && "border-b border-border",
        footer && "font-semibold",
      )}
    >
      {children}
    </th>
  );
}

function TotalCell({ children, strong = false, last = false }: { children: ReactNode; strong?: boolean; last?: boolean }) {
  return (
    <td
      className={cn(
        "border-b border-border px-2 py-1.5 text-right whitespace-nowrap",
        strong && "font-semibold",
        last && "sticky right-0 z-1 border-l bg-surface pr-4",
      )}
    >
      <output>{children}</output>
    </td>
  );
}

function ConversionCell({
  theoretical,
  result,
  bonus,
  rate = null,
}: {
  theoretical: string;
  result: number;
  bonus: boolean;
  rate?: string | null;
}) {
  const t = useTranslations("exams.matrix");
  return (
    <td className={cn("border-b border-border px-2 py-1.5 text-right whitespace-nowrap", bonus && "bg-info-bg font-semibold")}>
      <span className="flex flex-col items-end gap-0.5">
        <span>{t("conversionValue", { theoretical, result })}</span>
        {rate && <span className="text-caption font-normal text-text-muted">{rate}</span>}
        {bonus && <Badge className="border-info/40 text-info">{t("remainderBadge")}</Badge>}
      </span>
    </td>
  );
}

function ConversionPanel({
  blockCount,
  conversion,
  cellLabel,
  children,
}: {
  blockCount: number;
  conversion: Apportionment;
  cellLabel: (index: number) => string;
  children: ReactNode;
}) {
  const t = useTranslations("exams.matrix");
  const format = useFormatter();
  const headingId = useId();
  const bonusCells = conversion.bonus
    .map((bonus, index) => (bonus ? index : -1))
    .filter((index) => index >= 0)
    .map((index) =>
      t("remainderCell", {
        cell: cellLabel(index),
        fraction: format.number((conversion.theoretical[index] ?? 0) - Math.floor((conversion.theoretical[index] ?? 0) + 1e-9), DISPLAY_NUMBER_FORMAT),
      }),
    );
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3 rounded-md border border-border bg-subtle p-3">
      <h4 id={headingId} className="text-label font-semibold text-text">
        {t("conversionTitle", { count: blockCount })}
      </h4>
      {children}
      <ol className="ml-5 list-decimal space-y-1 text-body text-text-secondary">
        <li>{t("steps.theoretical")}</li>
        <li>{t("steps.floor", { floor: conversion.floorTotal, missing: conversion.missing })}</li>
        <li>
          {conversion.missing > 0
            ? t("steps.remainder", { cells: bonusCells.join("; ") })
            : t("steps.noRemainder")}
        </li>
        <li>{t("steps.review")}</li>
      </ol>
      <p className="text-caption text-text-muted">{t("conversionNote")}</p>
    </section>
  );
}

function AllocationStatus({
  blockCount,
  totals,
  messages,
  declared,
}: {
  blockCount: number;
  /** `pending`: chiều chỉ để tham khảo và chưa có số (ví dụ HSA Tiếng Anh chờ chuyên gia): không báo thiếu. */
  totals: Array<{ label: string | null; total: number; pending?: boolean }>;
  messages: Array<{ text: string; over: boolean }>;
  /** `false`: chỉ tiêu riêng của nhóm chưa khai, chưa báo thiếu hay vượt. */
  declared: boolean;
}) {
  const t = useTranslations("exams.matrix");
  const ok = messages.length === 0;
  return (
    <div className="flex flex-col gap-2 rounded-md border border-border p-3">
      {totals.map(({ label, total, pending = false }, index) => {
        const meterId = `${index}-${label ?? "all"}`;
        const difference = total - blockCount;
        const state = !declared || pending ? "neutral" : difference > 0 ? "over" : difference < 0 ? "under" : "exact";
        const valueText =
          state === "over"
            ? t("meterOver", { allocated: total, count: blockCount, over: difference })
            : state === "under"
              ? t("meterUnder", { allocated: total, count: blockCount, short: -difference })
              : state === "exact"
                ? t("meterExact", { allocated: total, count: blockCount })
                : t("allocatedMeter", { allocated: total, count: blockCount });
        const filled = blockCount > 0 ? Math.min(100, Math.max(0, (total / blockCount) * 100)) : total > 0 ? 100 : 0;
        return (
          <div key={meterId} className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <span className="text-body text-text">
              {label ? `${label}: ` : ""}
              {t("allocated", { allocated: total, count: blockCount })}
            </span>
            <div
              role="meter"
              data-state={state}
              aria-label={t("allocatedMeter", { allocated: total, count: blockCount })}
              aria-valuemin={0}
              aria-valuemax={Math.max(blockCount, total, 1)}
              aria-valuenow={total}
              aria-valuetext={valueText}
              className="h-2 w-40 max-w-full overflow-hidden rounded-full bg-subtle"
            >
              <div
                className={cn(
                  "h-full rounded-full",
                  state === "over" ? "bg-danger" : state === "exact" ? "bg-success" : state === "under" ? "bg-warning" : "bg-text-muted",
                )}
                style={{ width: `${filled}%` }}
              />
            </div>
            {state === "over" && (
              <span className="flex items-center gap-1 text-body font-medium text-danger">
                <TriangleAlert aria-hidden className="size-4 shrink-0" />
                {t("over", { count: difference })}
              </span>
            )}
            {state === "under" && <span className="text-body font-medium text-warning">{t("under", { count: -difference })}</span>}
          </div>
        );
      })}
      {ok ? (
        <p className="flex items-center gap-1.5 text-body text-success">
          <CircleCheck aria-hidden className="size-4 shrink-0" />
          {t("matches")}
        </p>
      ) : (
        <ul className="flex flex-col gap-1">
          {messages.map((message) => (
            <li key={message.text} className={cn("flex items-start gap-1.5 text-body", message.over ? "text-danger" : "text-warning")}>
              <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
              {message.text}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
