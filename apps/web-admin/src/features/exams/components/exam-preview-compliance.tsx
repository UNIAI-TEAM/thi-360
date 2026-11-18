"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { Card, ListCard, StatusChip, Table, type StatusTone, type TableCards } from "@thi360/ui";
import { CircleCheck, Info } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo } from "react";
import { DISPLAY_NUMBER_FORMAT } from "../display-number";
import {
  complianceLevel,
  droppedCount,
  parseCriterion,
  parseScope,
  topicAssignments,
  type ComplianceLevel,
} from "../review-compliance";
import type { PreviewCompliance, PreviewPaper, PreviewQuestion } from "../review-types";
import { useLookup } from "./exam-review-shared";

const LEVEL_TONE: Record<ComplianceLevel, StatusTone> = {
  OK: "success",
  REFERENCE_DEVIATION: "warning",
  REQUIRED_DEVIATION: "danger",
};

export interface ExamPreviewComplianceProps {
  paper: PreviewPaper;
  compliance: readonly PreviewCompliance[];
  /** Câu đã bỏ của môn 16 câu (HSA Khoa học). */
  dropped: readonly PreviewQuestion[];
}

/**
 * Bảng Yêu cầu / Thực tế của đề thử (bản vẽ `SinhThu2B`, 2B mục 8): mọi tiêu chí kể cả tham khảo, dòng lệch được đánh
 * dấu theo mức (Bắt buộc là lỗi, Tham khảo là cảnh báo không chặn). Môn 16 câu ghi "đã bỏ 1 câu" và nêu câu đã bỏ;
 * chỉ tiêu chủ đề nêu các câu được gán.
 */
export function ExamPreviewCompliance({ paper, compliance, dropped }: ExamPreviewComplianceProps) {
  const t = useTranslations("exams.preview.compliance");
  const tContent = useTranslations("content");
  const format = useFormatter();
  const lookup = useLookup();
  const assigned = useMemo(() => topicAssignments(paper), [paper]);

  const { columns, cards } = useMemo(() => {
    const number = (value: number) => format.number(value, DISPLAY_NUMBER_FORMAT);
    const blocks = new Map(paper.parts.flatMap((part) => part.blocks).map((block) => [block.blockId, block]));
    const sections = new Map(paper.subjects.map((subject) => [subject.sectionId, subject]));

    const criterion = (row: PreviewCompliance) => {
      const ref = parseCriterion(row.criterion);
      switch (ref.kind) {
        case "ANSWER_TYPE":
          return t("criterion.ANSWER_TYPE", { name: tContent(`questionType.${ref.code}`) });
        case "CELL":
          return t("criterion.CELL", { grade: lookup.grade(ref.gradeCode), difficulty: lookup.difficulty(ref.difficultyCode) });
        case "GRADE":
          return t("criterion.GRADE", { name: lookup.grade(ref.code) });
        case "DIFFICULTY":
          return t("criterion.DIFFICULTY", { name: lookup.difficulty(ref.code) });
        case "COMPETENCY":
          return t("criterion.COMPETENCY", { name: ref.code });
        case "TOPIC":
          return t("criterion.TOPIC");
        case "EXAM":
          return t("criterion.EXAM", { names: ref.codes.map((code) => (/^G/.test(code) ? lookup.grade(code) : lookup.difficulty(code))).join(" + ") });
        default:
          return ref.raw;
      }
    };
    const scope = (row: PreviewCompliance) => {
      const ref = parseScope(row.scope);
      if (ref.kind === "SECTION") return lookup.subject(sections.get(ref.id)?.subjectCode ?? "") || t("scope.EXAM");
      if (ref.kind === "BLOCK") {
        const block = blocks.get(ref.id);
        return block ? `${lookup.subject(block.subjectCode)} · ${block.blockName}` : ref.id;
      }
      return t("scope.EXAM");
    };
    const level = (row: PreviewCompliance) =>
      t("level", { enforcement: t(`enforcement.${row.enforcement}`), unit: t(`unit.${row.basis}`) });
    const required = (row: PreviewCompliance) => {
      const removed = droppedCount(row.note);
      return removed > 0 ? t("requiredDropped", { value: number(row.required), count: removed }) : number(row.required);
    };
    const actual = (row: PreviewCompliance) => {
      const ref = parseCriterion(row.criterion);
      const numbers = ref.kind === "TOPIC" ? (assigned.get(ref.code) ?? []) : [];
      return numbers.length > 0 ? t("actualAssigned", { value: number(row.actual), numbers: numbers.join(", ") }) : number(row.actual);
    };
    const result = (row: PreviewCompliance) => {
      const value = complianceLevel(row);
      return <StatusChip tone={LEVEL_TONE[value]}>{t(`result.${value}`)}</StatusChip>;
    };

    const columns: ColumnDef<PreviewCompliance>[] = [
      { id: "criterion", header: t("columns.criterion"), cell: ({ row }) => <span className="block min-w-40 font-semibold text-text">{criterion(row.original)}</span> },
      { id: "scope", header: t("columns.scope"), cell: ({ row }) => <span className="block min-w-40">{scope(row.original)}</span> },
      { id: "level", header: t("columns.level"), cell: ({ row }) => <span className="whitespace-nowrap">{level(row.original)}</span> },
      { id: "required", header: t("columns.required"), cell: ({ row }) => <span className="block min-w-24">{required(row.original)}</span> },
      { id: "actual", header: t("columns.actual"), cell: ({ row }) => <span className="block min-w-24">{actual(row.original)}</span> },
      { id: "result", header: t("columns.result"), cell: ({ row }) => result(row.original) },
    ];
    const cards: TableCards<PreviewCompliance> = {
      render: (row) => (
        <ListCard
          title={criterion(row)}
          caption={scope(row)}
          status={result(row)}
          meta={[
            { label: t("columns.level"), value: level(row) },
            { label: t("columns.required"), value: required(row) },
            { label: t("columns.actual"), value: actual(row) },
          ]}
        />
      ),
    };
    return { columns, cards };
  }, [t, tContent, format, lookup, paper, assigned]);

  if (compliance.length === 0 && dropped.length === 0) return null;
  const deviations = compliance.filter((row) => complianceLevel(row) !== "OK");
  const blocking = deviations.some((row) => complianceLevel(row) === "REQUIRED_DEVIATION");

  return (
    <section aria-labelledby="preview-compliance-title" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <h3 id="preview-compliance-title" className="text-title font-semibold text-text">
          {t("title")}
        </h3>
        {compliance.length > 0 &&
          (deviations.length === 0 ? (
            <StatusChip tone="success">{t("allPassed")}</StatusChip>
          ) : (
            <StatusChip tone={blocking ? "danger" : "warning"}>{t("deviations", { count: deviations.length })}</StatusChip>
          ))}
      </div>
      {dropped.length > 0 && (
        <ul className="flex flex-col gap-1">
          {dropped.map((question) => (
            <li key={question.questionId} className="flex items-start gap-2 text-body text-text-secondary">
              <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
              {t("droppedQuestion", {
                code: question.code,
                type: tContent(`questionType.${question.type}`),
                grade: lookup.grade(question.gradeCode),
                difficulty: lookup.difficulty(question.difficultyCode),
              })}
            </li>
          ))}
        </ul>
      )}
      {compliance.length > 0 && (
        <Card padding="none" className="overflow-hidden">
          <Table
            aria-label={t("tableLabel")}
            columns={columns}
            data={[...compliance]}
            getRowId={(row) => `${row.scope}|${row.criterion}`}
            empty={null}
            cards={cards}
          />
        </Card>
      )}
      {compliance.length > 0 && (
        <p className="flex items-start gap-2 text-caption text-text-muted">
          <CircleCheck aria-hidden className="mt-0.5 size-4 shrink-0" />
          {t("legend")}
        </p>
      )}
    </section>
  );
}
