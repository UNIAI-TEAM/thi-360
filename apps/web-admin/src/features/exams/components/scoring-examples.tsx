"use client";

import { cn, focusRing, StatusChip } from "@thi360/ui";
import { useFormatter, useTranslations } from "next-intl";
import { useId, useState, type ReactNode } from "react";
import type { QuestionType } from "@/features/content/types";
import type { StrandTable } from "../reference-distribution";
import type { ExamPart, QuestionBlock } from "../types";
import { ScrollableTable } from "./scrollable-table";

/** Điểm tối đa một câu V-SAT khi khối không khai (VS §2: mỗi câu 6 điểm). */
const VSAT_POINTS = 6;
/** Bảng Đúng/Sai của V-SAT tính trên điểm tối đa 6: đúng 0, 1, 2, 3, 4 ý được 0, 1, 2, 3, 6 điểm (VS §7). */
const VSAT_TRUE_FALSE = [0, 1, 2, 3, 6] as const;
const STATEMENTS = 4;

function round(value: number): number {
  return Math.round(value * 10_000) / 10_000;
}

/** Điểm của câu Đúng/Sai V-SAT theo số ý đúng 0..4, tỷ lệ theo điểm tối đa của câu. */
export function vsatTrueFalsePoints(max: number): number[] {
  return VSAT_TRUE_FALSE.map((value) => round((value * max) / VSAT_POINTS));
}

/** Điểm của câu Ghép đôi V-SAT theo số cặp đúng 0..4: mỗi cặp đúng được một phần tư điểm tối đa (6 điểm: 1,5 mỗi cặp). */
export function vsatMatchingPoints(max: number): number[] {
  return Array.from({ length: STATEMENTS + 1 }, (_, correct) => round((correct * max) / STATEMENTS));
}

function partBlocks(part: ExamPart): QuestionBlock[] {
  const sections = [...part.sections, ...part.options.flatMap((option) => option.sections), ...(part.subjectPool ?? [])];
  return [...sections.flatMap((section) => section.blocks), ...part.options.flatMap((option) => option.blocks ?? [])].filter((block) => !block.omitted);
}

export function scoringTypes(part: ExamPart): Map<QuestionType, number | null> {
  const types = new Map<QuestionType, number | null>();
  const add = (type: QuestionType | null | undefined, points: number | null) => {
    if (!type || type === "QUESTION_SET" || type === "ESSAY") return;
    if (!types.has(type) || types.get(type) === null) types.set(type, points);
  };
  for (const block of partBlocks(part)) {
    const points = block.requiredPointsPerQuestion;
    if (block.questionType === "QUESTION_SET") {
      add(block.childAnswerType, points);
      for (const condition of block.childConditions ?? []) add(condition.answerType, points);
    } else {
      add(block.questionType, points);
    }
  }
  return types;
}

const H_SCA_TYPES: readonly QuestionType[] = ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "SHORT_ANSWER"];
/** Dạng câu V-SAT có ví dụ chấm: hai bảng Đúng/Sai, Ghép đôi và bảng "Các dạng còn lại". */
const V_SAT_TYPES: readonly QuestionType[] = ["TRUE_FALSE", "MATCHING", "SINGLE_CHOICE", "SHORT_ANSWER", "CLOZE"];

export interface ScoringExamplesProps {
  /** Mã kỳ thi của bài: chỉ `HSCA`, `VSAT` có khối này; kỳ thi khác không hiện gì. */
  kind: string;
  /** Phần đang mở (hoặc phần chứa môn, nhóm đang mở). */
  part: ExamPart | null;
}

export function ScoringExamples({ kind, part }: ScoringExamplesProps) {
  const t = useTranslations("exams.structure.scoringExamples");
  const headingId = useId();
  if ((kind !== "HSCA" && kind !== "VSAT") || !part) return null;
  const types = scoringTypes(part);
  // Phần không có dạng nào có ví dụ (V-SAT Ngữ văn chỉ có Tự luận, H-SCA không có câu chọn, trả lời ngắn): không vẽ khung rỗng.
  if (!(kind === "VSAT" ? V_SAT_TYPES : H_SCA_TYPES).some((type) => types.has(type))) return null;
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-4 rounded-lg border border-border bg-surface p-4 sm:p-6">
      <div>
        <h2 id={headingId} className="text-h3 font-bold text-text">
          {t("title")}
        </h2>
        <p className="text-caption text-text-muted">{t("description")}</p>
      </div>
      {kind === "VSAT" ? <VsatExamples types={types} /> : <HscaExamples types={types} />}
    </section>
  );
}

function ExampleTable({ caption, children }: { caption: string; children: ReactNode }) {
  const captionId = useId();
  return (
    <ScrollableTable labelledBy={captionId} className="rounded-lg border border-border">
      <table className="w-full min-w-[22rem] border-separate border-spacing-0 text-body tabular-nums [&_tbody_tr:last-child>*]:border-b-0">
        <caption id={captionId} className="px-3.5 py-3 text-left text-title font-bold text-text">
          {caption}
        </caption>
        {children}
      </table>
    </ScrollableTable>
  );
}

const HEAD = "border-b border-border bg-subtle px-3.5 py-2 text-caption font-semibold text-text-muted";
const CELL = "border-b border-border px-3.5 py-2.5";

function VsatExamples({ types }: { types: Map<QuestionType, number | null> }) {
  const t = useTranslations("exams.structure.scoringExamples.vsat");
  const tContent = useTranslations("content");
  const format = useFormatter();
  const name = useId();
  const [correct, setCorrect] = useState(3);
  const number = (value: number) => format.number(value, { maximumFractionDigits: 4 });
  const hasTrueFalse = types.has("TRUE_FALSE");
  const hasMatching = types.has("MATCHING");
  const trueFalseMax = types.get("TRUE_FALSE") ?? VSAT_POINTS;
  const matchingMax = types.get("MATCHING") ?? VSAT_POINTS;
  const trueFalse = vsatTrueFalsePoints(trueFalseMax);
  const matching = vsatMatchingPoints(matchingMax);
  const others = (["SINGLE_CHOICE", "SHORT_ANSWER", "CLOZE"] as const).filter((type) => types.has(type));

  return (
    <div className="flex flex-col gap-4">
      {(hasTrueFalse || hasMatching) && (
        <>
          <div role="status" className="flex flex-col gap-1 rounded-md border border-warning/40 border-l-4 border-l-warning bg-warning-bg px-3.5 py-3">
            <strong className="text-title font-semibold text-text tabular-nums">
              {hasTrueFalse && hasMatching
                ? t("status", { n: correct, trueFalse: number(trueFalse[correct]!), matching: number(matching[correct]!) })
                : hasTrueFalse
                  ? t("statusTrueFalse", { n: correct, points: number(trueFalse[correct]!) })
                  : t("statusMatching", { n: correct, points: number(matching[correct]!) })}
            </strong>
            {hasTrueFalse && hasMatching && (
              <span className="text-body text-text-secondary">
                {trueFalse[correct] === matching[correct] ? t("contrastSame") : t("contrastDifferent", { perPair: number(matchingMax / STATEMENTS) })}
              </span>
            )}
          </div>
          <fieldset className="m-0 border-0 p-0">
            <legend className="mb-2 text-field-label font-medium text-text-secondary">{t(hasTrueFalse ? "picksLegend" : "picksLegendMatching")}</legend>
            <div className="flex flex-wrap gap-2">
              {Array.from({ length: STATEMENTS + 1 }, (_, value) => (
                <label
                  key={value}
                  className={cn(
                    "flex min-h-11 min-w-[4.75rem] cursor-pointer items-center justify-center gap-2 rounded-md border px-3 font-semibold tabular-nums",
                    value === correct ? "border-primary-text bg-primary-50 text-primary-text" : "border-border bg-surface text-text-secondary",
                  )}
                >
                  <input type="radio" name={name} checked={value === correct} onChange={() => setCorrect(value)} className={cn("size-4 accent-primary-fill", focusRing)} />
                  {t("pick", { n: value })}
                </label>
              ))}
            </div>
          </fieldset>
          <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-2">
            {hasTrueFalse && (
              <ExampleTable caption={t("trueFalseCaption", { max: number(trueFalseMax) })}>
                <thead>
                  <tr className="text-left">
                    <th scope="col" className={HEAD}>{t("colTrueFalse")}</th>
                    <th scope="col" className={cn(HEAD, "text-right")}>{t("colPoints")}</th>
                  </tr>
                </thead>
                <tbody>
                  {trueFalse.map((points, value) => (
                    <tr key={value} className={value === correct ? "bg-primary-50" : undefined}>
                      <th scope="row" className={cn(CELL, "text-left", value === correct ? "font-bold" : "font-medium")}>
                        {t("rowStatements", { n: value })}
                        {value === correct && <StatusChip tone="success" className="ms-2">{t("current")}</StatusChip>}
                      </th>
                      <td className={cn(CELL, "text-right", value === correct && "font-bold")}>{number(points)}</td>
                    </tr>
                  ))}
                </tbody>
              </ExampleTable>
            )}
            {hasMatching && (
              <ExampleTable caption={t("matchingCaption", { max: number(matchingMax) })}>
                <thead>
                  <tr className="text-left">
                    <th scope="col" className={HEAD}>{t("colMatching")}</th>
                    <th scope="col" className={cn(HEAD, "text-right")}>{t("colPoints")}</th>
                  </tr>
                </thead>
                <tbody>
                  {matching.map((points, value) => (
                    <tr key={value} className={value === correct ? "bg-primary-50" : undefined}>
                      <th scope="row" className={cn(CELL, "text-left", value === correct ? "font-bold" : "font-medium")}>
                        {t("rowPairs", { n: value })}
                        {value === correct && <StatusChip tone="success" className="ms-2">{t("current")}</StatusChip>}
                      </th>
                      <td className={cn(CELL, "text-right", value === correct && "font-bold")}>{number(points)}</td>
                    </tr>
                  ))}
                </tbody>
              </ExampleTable>
            )}
          </div>
        </>
      )}
      {others.length > 0 && (
        <ExampleTable caption={t("othersCaption")}>
          <thead>
            <tr className="text-left">
              <th scope="col" className={HEAD}>{t("colType")}</th>
              <th scope="col" className={HEAD}>{t("colRule")}</th>
              <th scope="col" className={HEAD}>{t("colExample")}</th>
            </tr>
          </thead>
          <tbody>
            {others.map((type) => {
              const points = types.get(type) ?? VSAT_POINTS;
              const rule = type === "SINGLE_CHOICE" ? "single" : type === "SHORT_ANSWER" ? "short" : "cloze";
              return (
                <tr key={type}>
                  <th scope="row" className={cn(CELL, "text-left font-semibold")}>
                    {type === "CLOZE" ? t("clozeName") : tContent(`questionType.${type}`)}
                  </th>
                  <td className={CELL}>{t(`${rule}Rule`, { points: number(points) })}</td>
                  <td className={CELL}>
                    {rule === "single"
                      ? t("singleExample", { part: number(2 * points), total: number(3 * points) })
                      : rule === "cloze"
                        ? t("clozeExample", { part: number(4 * points), total: number(5 * points) })
                        : t("shortExample")}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </ExampleTable>
      )}
    </div>
  );
}

function HscaExamples({ types }: { types: Map<QuestionType, number | null> }) {
  const t = useTranslations("exams.structure.scoringExamples.hsca");
  const tContent = useTranslations("content");
  const format = useFormatter();
  const number = (value: number) => format.number(value, { maximumFractionDigits: 4 });
  const rows = H_SCA_TYPES.filter((type) => types.has(type));
  const hasPartial = rows.includes("MULTIPLE_CHOICE");
  const notApplicable = <span className="text-text-muted">{t("notApplicable")}</span>;

  return (
    <div className="flex flex-col gap-4">
      <ExampleTable caption={t("caption")}>
        <thead>
          <tr className="text-left">
            <th scope="col" className={HEAD}>{t("colType")}</th>
            <th scope="col" className={cn(HEAD, "text-right")}>{t("colCorrect")}</th>
            <th scope="col" className={cn(HEAD, "text-right")}>{t("colPartial")}</th>
            <th scope="col" className={cn(HEAD, "text-right")}>{t("colWrong")}</th>
            <th scope="col" className={cn(HEAD, "text-right")}>{t("colBlank")}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((type) => {
            // Điểm mỗi câu của dòng chưa khai thì không có số để minh họa: hiện "—", không tự đặt mặc định.
            const points = types.get(type) ?? null;
            const partial = type === "MULTIPLE_CHOICE";
            return (
              <tr key={type} className={partial ? "bg-warning-bg" : undefined}>
                <th scope="row" className={cn(CELL, "text-left font-semibold")}>{tContent(`questionType.${type}`)}</th>
                <td className={cn(CELL, "text-right")}>{points === null ? "—" : number(points)}</td>
                <td className={cn(CELL, "text-right", partial && "font-bold")}>{!partial ? notApplicable : points === null ? "—" : number(points / 2)}</td>
                <td className={cn(CELL, "text-right")}>0</td>
                <td className={cn(CELL, "text-right")}>0</td>
              </tr>
            );
          })}
        </tbody>
      </ExampleTable>
      {hasPartial && (
        <div role="note" className="flex flex-col gap-1 rounded-md border border-warning/40 bg-warning-bg px-3 py-2.5 text-body text-text-secondary">
          <strong className="text-warning">{t("pendingTitle")}</strong>
          <span>{t("pendingBody")}</span>
        </div>
      )}
      <p className="text-body text-text-muted">{t("childNote")}</p>
    </div>
  );
}

export interface StrandReferenceProps {
  table: StrandTable | null;
  subjectName: string;
  difficultyName: (code: string) => string;
}

export function StrandReference({ table, subjectName, difficultyName }: StrandReferenceProps) {
  const t = useTranslations("exams.structure.strand");
  const headingId = useId();
  if (!table) return null;
  return (
    <section aria-labelledby={headingId} className="overflow-hidden rounded-lg border border-border bg-surface">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border bg-subtle px-3.5 py-3">
        <div>
          <h3 id={headingId} className="text-title font-bold text-text">
            {t("title")}
          </h3>
          <span className="text-caption text-text-muted">{t("denominator", { subject: subjectName, total: table.total })}</span>
        </div>
        <StatusChip tone="neutral">{t("reference")}</StatusChip>
      </div>
      <ScrollableTable labelledBy={headingId}>
        <table className="w-full min-w-[32rem] border-separate border-spacing-0 text-body tabular-nums">
          <caption className="sr-only">{t("caption", { subject: subjectName })}</caption>
          <thead>
            <tr className="text-left">
              <th scope="col" className={HEAD}>{t("colStrand")}</th>
              <th scope="col" className={cn(HEAD, "text-right")}>{t("colCommands")}</th>
              <th scope="col" className={HEAD}>{t("colStatus")}</th>
            </tr>
          </thead>
          <tbody>
            {table.rows.map((row) => (
              <tr key={row.id}>
                <th scope="row" className={cn(CELL, "text-left font-semibold")}>{row.name}</th>
                <td className={cn(CELL, "text-right")}>{row.commands}</td>
                <td className={CELL}>
                  <StatusChip tone={row.pending ? "warning" : "success"}>{t(row.pending ? "pending" : "mapped")}</StatusChip>
                </td>
              </tr>
            ))}
            <tr className="bg-subtle font-bold">
              <th scope="row" className="px-3.5 py-2.5 text-left">{t("total")}</th>
              <td className="px-3.5 py-2.5 text-right">{table.total}</td>
              <td className="px-3.5 py-2.5" />
            </tr>
          </tbody>
        </table>
      </ScrollableTable>
      <p className="border-t border-border px-3.5 py-3 text-body text-text-secondary tabular-nums">
        {table.difficulty.length > 0 ? `${t("difficulty", { list: table.difficulty.map((item) => `${difficultyName(item.code)} ${item.value}`).join(" · ") })} ` : ""}
        {t("note")}
      </p>
    </section>
  );
}
