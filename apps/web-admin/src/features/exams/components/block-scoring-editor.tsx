"use client";

import { Button, Label, Switch } from "@thi360/ui";
import { CircleAlert, PenLine } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId } from "react";
import type { MultipleChoiceRule, ScoringDefaults } from "@/features/content/types";
import type { BlockExpectedScoring, QuestionBlock } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { ChoiceCardGroup } from "./create-exam-fields";

/** Số ý của câu Đúng/Sai cho phép trong bảng điểm mong đợi. */
export const MIN_STATEMENTS = 2;
export const MAX_STATEMENTS = 8;

function round(value: number): number {
  return Math.round(value * 10_000) / 10_000;
}

/** Bảng chia đều theo số ý: đúng `i` ý được `i / n` điểm của câu. */
export function evenTrueFalseTable(statementCount: number, points: number): number[] {
  return Array.from({ length: statementCount }, (_, index) => round((points * (index + 1)) / statementCount));
}

/**
 * Bảng điểm mặc định của kỳ thi quy theo điểm mỗi câu của khối (bảng THPTQG 0,1 / 0,25 / 0,5 / 1 tính trên điểm tối đa
 * 1); kỳ thi không có bảng thì chia đều 4 ý.
 */
export function defaultTrueFalseTable(defaults: ScoringDefaults | null, points: number): number[] {
  const table = defaults?.trueFalseTable;
  if (!table?.length) return evenTrueFalseTable(4, points);
  const max = table.at(-1) || 1;
  return table.map((value) => round((value * points) / max));
}

export type TrueFalseTableIssue = "LAST_NOT_POINTS" | "DECREASING";

/** Bảng hợp lệ: không giảm khi đúng thêm ý, phần tử cuối bằng điểm mỗi câu của khối. */
export function trueFalseTableIssues(table: readonly number[], points: number): TrueFalseTableIssue[] {
  const issues: TrueFalseTableIssue[] = [];
  if (Math.abs((table.at(-1) ?? 0) - points) > 1e-6) issues.push("LAST_NOT_POINTS");
  if (table.some((value, index) => index > 0 && value < (table[index - 1] ?? 0))) issues.push("DECREASING");
  return issues;
}

/**
 * Quy tắc chấm mặc định khi khối đổi sang dạng câu có quy tắc: Đúng/Sai theo bảng của kỳ thi (khi kỳ thi có bảng),
 * Nhiều đáp án theo quy tắc của kỳ thi. Dạng khác bỏ quy tắc.
 */
export function expectedScoringFor(
  questionType: QuestionBlock["questionType"],
  defaults: ScoringDefaults | null,
  points: number,
): BlockExpectedScoring | undefined {
  if (questionType === "TRUE_FALSE" && defaults?.trueFalseTable?.length) {
    return { trueFalseTable: defaultTrueFalseTable(defaults, points) };
  }
  if (questionType === "MULTIPLE_CHOICE" && defaults) return { multipleChoiceRule: defaults.multipleChoiceRule };
  return undefined;
}

export interface BlockScoringEditorProps {
  block: QuestionBlock;
  /** Quy tắc chấm mặc định của kỳ thi; `null` khi chưa tải được danh mục. */
  defaults: ScoringDefaults | null;
  onChange: (expectedScoring: BlockExpectedScoring | undefined) => void;
}

/**
 * Quy tắc chấm mong đợi của khối (UNI-2599 quyết định 2–4): quy tắc thật nằm trên từng câu, khối chỉ giữ quy tắc mong
 * đợi để Kiểm tra cấu hình báo câu nguồn có quy tắc khác. Đúng/Sai: bảng điểm theo số ý đúng; Nhiều đáp án: đúng hết
 * mới có điểm hoặc chấm từng phần; Tự luận: chấm tay theo tiêu chí của câu.
 */
export function BlockScoringEditor({ block, defaults, onChange }: BlockScoringEditorProps) {
  const t = useTranslations("exams.structure.scoring");
  const format = useFormatter();
  const switchId = useId();
  const hintId = useId();
  const number = (value: number) => format.number(value, { maximumFractionDigits: 4 });

  if (block.questionType === "ESSAY") {
    return (
      <section className="flex flex-col gap-2 rounded-md border border-border p-4" aria-label={t("title")}>
        <h3 className="flex items-center gap-2 text-label font-semibold text-text">
          <PenLine aria-hidden className="size-4 shrink-0" />
          {t("title")}
        </h3>
        <p className="text-body text-text-secondary">{t("essay")}</p>
      </section>
    );
  }
  if (block.questionType !== "TRUE_FALSE" && block.questionType !== "MULTIPLE_CHOICE") return null;

  const isTrueFalse = block.questionType === "TRUE_FALSE";
  const table = block.expectedScoring?.trueFalseTable;
  const rule = block.expectedScoring?.multipleChoiceRule;
  const enabled = isTrueFalse ? Boolean(table?.length) : Boolean(rule);
  const points = block.pointsPerQuestion;

  const toggle = (on: boolean) => {
    if (!on) onChange(undefined);
    else if (isTrueFalse) onChange({ trueFalseTable: defaultTrueFalseTable(defaults, points) });
    else onChange({ multipleChoiceRule: defaults?.multipleChoiceRule ?? "ALL_OR_NOTHING" });
  };

  const tableIssues = table ? trueFalseTableIssues(table, points) : [];

  return (
    <section className="flex flex-col gap-3 rounded-md border border-border p-4" aria-label={t("title")}>
      <h3 className="text-label font-semibold text-text">{t("title")}</h3>
      <div className="flex items-start gap-3">
        <Switch id={switchId} checked={enabled} aria-describedby={hintId} onCheckedChange={toggle} />
        <div className="flex flex-col gap-0.5">
          <Label htmlFor={switchId} className="cursor-pointer text-body font-medium text-text">
            {t("compare")}
          </Label>
          <p id={hintId} className="text-caption text-text-muted">
            {t("compareHint")}
          </p>
        </div>
      </div>

      {enabled && isTrueFalse && table && (
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`${switchId}-count`}>{t("statements")}</Label>
              <BlockNumberInput
                id={`${switchId}-count`}
                className="h-9 w-20"
                value={table.length}
                onValueChange={(count) => {
                  if (count >= MIN_STATEMENTS && count <= MAX_STATEMENTS) {
                    onChange({ trueFalseTable: evenTrueFalseTable(count, points) });
                  }
                }}
                aria-describedby={`${switchId}-count-hint`}
              />
              <span id={`${switchId}-count-hint`} className="text-caption text-text-muted">
                {t("statementsHint", { min: MIN_STATEMENTS, max: MAX_STATEMENTS })}
              </span>
            </div>
            {defaults?.trueFalseTable?.length ? (
              <Button
                type="button"
                variant="secondary"
                size="sm"
                onClick={() => onChange({ trueFalseTable: defaultTrueFalseTable(defaults, points) })}
              >
                {t("useKindDefault")}
              </Button>
            ) : null}
          </div>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-field-label font-medium text-text-secondary">
              {t("tableLegend", { points: number(points) })}
            </legend>
            <ol className="flex flex-wrap gap-2">
              {table.map((value, index) => (
                <li key={index}>
                  <label className="flex items-center gap-2 rounded-md border border-border py-1 pr-1 pl-2.5 text-label">
                    <span className="whitespace-nowrap text-text">{t("correctCount", { count: index + 1 })}</span>
                    <BlockNumberInput
                      className="h-8 w-20 px-2"
                      allowDecimal
                      value={value}
                      aria-invalid={tableIssues.length ? true : undefined}
                      onValueChange={(next) =>
                        onChange({ trueFalseTable: table.map((item, position) => (position === index ? next : item)) })
                      }
                    />
                  </label>
                </li>
              ))}
            </ol>
            {tableIssues.length > 0 && (
              <ul className="flex flex-col gap-1">
                {tableIssues.map((issue) => (
                  <li key={issue} className="flex items-start gap-1.5 text-caption text-danger">
                    <CircleAlert aria-hidden className="mt-0.5 size-3.5 shrink-0" />
                    {issue === "LAST_NOT_POINTS" ? t("lastNotPoints", { points: number(points) }) : t("decreasing")}
                  </li>
                ))}
              </ul>
            )}
          </fieldset>
        </div>
      )}

      {enabled && !isTrueFalse && rule && (
        <ChoiceCardGroup<MultipleChoiceRule>
          legend={t("ruleLegend")}
          name={`${block.id}-scoring-rule`}
          value={rule}
          onChange={(multipleChoiceRule) => onChange({ multipleChoiceRule })}
          options={[
            { value: "ALL_OR_NOTHING", label: t("rules.ALL_OR_NOTHING"), description: t("ruleHints.ALL_OR_NOTHING") },
            { value: "PARTIAL", label: t("rules.PARTIAL"), description: t("ruleHints.PARTIAL") },
          ]}
        />
      )}
    </section>
  );
}
