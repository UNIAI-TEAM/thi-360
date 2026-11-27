"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { Card, EmptyState, FormField, ListCard, Select, StatusChip, Table, type TableCards } from "@thi360/ui";
import { CircleCheck, CircleMinus, CircleX } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { type ReactNode, useMemo, useState } from "react";
import type {
  PreviewBlock,
  PreviewCheck,
  PreviewCompliance,
  PreviewKnowledgeStat,
  PreviewPaper,
  PreviewQuestion,
} from "../review-types";
import { ExamPreviewCompliance, mergeSubjectQuotasIntoCompliance } from "./exam-preview-compliance";
import { ExamName, richNodes, useLookup } from "./exam-review-shared";

/** Đối chiếu phân bổ: chỉ tiêu yêu cầu với đề đã sinh (cả bài, môn và khối), kiểm tra trên đề, thống kê theo kiến thức. */
export function ExamPreviewAllocation({
  paper,
  compliance = [],
  dropped = [],
  examId,
  original = false,
}: {
  paper: PreviewPaper;
  compliance?: readonly PreviewCompliance[];
  dropped?: readonly PreviewQuestion[];
  examId?: string;
  original?: boolean;
}) {
  const t = useTranslations("exams.preview.allocation");
  const lookup = useLookup();
  const mergedCompliance = useMemo(() => mergeSubjectQuotasIntoCompliance(paper, compliance), [paper, compliance]);
  const blocks = paper.parts.flatMap((part) => part.blocks);
  const [blockId, setBlockId] = useState(
    () => (blocks.find((block) => block.requirement.mode === "COMBINED") ?? blocks[0])?.blockId ?? "",
  );
  const block = blocks.find((item) => item.blockId === blockId) ?? blocks[0];
  // Tên khối do mẫu đặt là nội dung đề (`ExamName`); tên môn theo danh mục ở ngoài.
  const name = (item: PreviewBlock) => (
    <>
      {lookup.subject(item.subjectCode)} · <ExamName>{item.blockName}</ExamName>
    </>
  );

  return (
    <div className="flex flex-col gap-5">
      <ExamPreviewCompliance
        paper={paper}
        compliance={mergedCompliance}
        dropped={dropped}
        examId={examId}
        original={original}
      />
      <Card padding="md" className="flex flex-col gap-3">
        <FormField label={t("blockLabel")} className="max-w-md">
          {(controlProps) => (
            <Select
              {...controlProps}
              value={block?.blockId}
              onValueChange={setBlockId}
              options={blocks.map((item) => ({
                value: item.blockId,
                label: (
                  <>
                    {name(item)} · {item.questionCount}
                  </>
                ),
                textValue: `${lookup.subject(item.subjectCode)} · ${item.blockName} · ${item.questionCount}`,
              }))}
            />
          )}
        </FormField>
        {block && <AllocationMatrix block={block} name={name(block)} />}
      </Card>
      <div className="@container">
        <div className="grid gap-5 @3xl:grid-cols-2">
          <ChecksList checks={paper.checks} />
          <KnowledgeTable stats={paper.knowledgeStats} />
        </div>
      </div>
    </div>
  );
}

function unique(values: readonly string[]): string[] {
  return [...new Set(values)];
}

function AllocationMatrix({ block, name }: { block: PreviewBlock; name: ReactNode }) {
  const t = useTranslations("exams.preview.allocation");
  const format = useFormatter();
  const lookup = useLookup();
  const combined = block.requirement.mode === "COMBINED";
  const grades = combined
    ? unique(block.allocation.map((cell) => cell.gradeCode))
    : block.requirement.byGrade.map((item) => item.code);
  const levels = combined
    ? unique(block.allocation.map((cell) => cell.difficultyCode))
    : block.requirement.byDifficulty.map((item) => item.code);
  const cellOf = (grade: string, level: string) =>
    block.allocation.find((cell) => cell.gradeCode === grade && cell.difficultyCode === level);
  const sum = (filter: (gradeCode: string, levelCode: string) => boolean, field: "required" | "generated") =>
    block.allocation
      .filter((cell) => filter(cell.gradeCode, cell.difficultyCode))
      .reduce((total, cell) => total + (cell[field] ?? 0), 0);
  const pair = (required: number, generated: number) => t("cell", { required, generated });
  const requiredOf = (dimension: "GRADE" | "DIFFICULTY", code: string) =>
    combined
      ? sum((g, d) => (dimension === "GRADE" ? g === code : d === code), "required")
      : ((dimension === "GRADE" ? block.requirement.byGrade : block.requirement.byDifficulty).find(
          (item) => item.code === code,
        )?.required ?? 0);
  const generatedOf = (dimension: "GRADE" | "DIFFICULTY", code: string) =>
    sum((g, d) => (dimension === "GRADE" ? g === code : d === code), "generated");

  const mismatches = combined
    ? block.allocation.filter((cell) => cell.required !== cell.generated).length
    : [
        ...grades.map((code) => ["GRADE", code] as const),
        ...levels.map((code) => ["DIFFICULTY", code] as const),
      ].filter(([dimension, code]) => requiredOf(dimension, code) !== generatedOf(dimension, code)).length;
  const captionId = `allocation-${block.blockId}`;

  return (
    <div className="flex flex-col gap-2">
      <div>
        <h3 id={captionId} className="text-label font-semibold text-text">
          {richNodes(t, "heading", { block: name, count: block.questionCount })}
        </h3>
        <p className="text-caption text-text-muted">{combined ? t("combinedNote") : t("separateNote")}</p>
      </div>
      <div
        role="region"
        tabIndex={0}
        aria-labelledby={captionId}
        className="overflow-x-auto rounded-md border border-border"
      >
        <table className="w-full min-w-3xl table-fixed border-collapse text-body tabular-nums">
          <caption className="sr-only">{richNodes(t, "tableLabel", { block: name })}</caption>
          <colgroup>
            <col className="w-32" />
            {levels.map((level) => (
              <col key={level} />
            ))}
            <col />
          </colgroup>
          <thead>
            <tr className="bg-table-head text-field-label text-text-muted">
              <th scope="col" className="px-3 py-2 text-left font-semibold">
                {t("grade")}
              </th>
              {levels.map((level) => (
                <th key={level} scope="col" className="px-3 py-2 text-right font-semibold break-words">
                  {lookup.difficulty(level)}
                </th>
              ))}
              <th scope="col" className="px-3 py-2 text-right font-semibold break-words">
                {t("total")}
              </th>
            </tr>
          </thead>
          <tbody>
            {grades.map((grade) => (
              <tr key={grade} className="border-t border-border">
                <th scope="row" className="px-3 py-2 text-left font-medium break-words text-text">
                  {lookup.grade(grade)}
                </th>
                {levels.map((level) => {
                  const cell = cellOf(grade, level);
                  const text = !cell
                    ? "—"
                    : combined
                      ? pair(cell.required ?? 0, cell.generated)
                      : format.number(cell.generated);
                  return (
                    <td key={level} className="px-3 py-2 text-right whitespace-nowrap">
                      {text}
                    </td>
                  );
                })}
                <td className="px-3 py-2 text-right font-semibold whitespace-nowrap">
                  {pair(requiredOf("GRADE", grade), generatedOf("GRADE", grade))}
                </td>
              </tr>
            ))}
            <tr className="border-t border-border bg-table-head font-semibold">
              <th scope="row" className="px-3 py-2 text-left break-words text-text">
                {t("total")}
              </th>
              {levels.map((level) => (
                <td key={level} className="px-3 py-2 text-right whitespace-nowrap">
                  {pair(requiredOf("DIFFICULTY", level), generatedOf("DIFFICULTY", level))}
                </td>
              ))}
              <td className="px-3 py-2 text-right whitespace-nowrap">
                {pair(
                  block.questionCount,
                  sum(() => true, "generated"),
                )}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p className={`flex items-center gap-2 text-body ${mismatches ? "text-danger" : "text-success"}`}>
        {mismatches ? (
          <CircleX aria-hidden className="size-4 shrink-0" />
        ) : (
          <CircleCheck aria-hidden className="size-4 shrink-0" />
        )}
        {mismatches ? t("mismatch", { count: mismatches }) : t("matchAll")}
      </p>
    </div>
  );
}

const CHECK_TONE = { PASS: "success", FAIL: "danger", NOT_APPLICABLE: "neutral" } as const;

function ChecksList({ checks }: { checks: PreviewCheck[] }) {
  const t = useTranslations("exams.preview.checks");
  return (
    <Card padding="md" className="flex flex-col gap-3">
      <h3 className="text-label font-semibold text-text">{t("title")}</h3>
      <ul className="flex flex-col gap-2">
        {checks.map((check) => {
          const Icon = check.status === "PASS" ? CircleCheck : check.status === "FAIL" ? CircleX : CircleMinus;
          return (
            <li key={check.code} className="flex items-start gap-2.5">
              <Icon
                aria-hidden
                className={`mt-0.5 size-[18px] shrink-0 ${check.status === "PASS" ? "text-success" : check.status === "FAIL" ? "text-danger" : "text-text-muted"}`}
              />
              <span className="min-w-0 flex-1 text-body text-text">
                {t.has(`name.${check.code}`) ? t(`name.${check.code}`, { value: check.value ?? 0 }) : t("nameUnknown")}
              </span>
              <StatusChip tone={CHECK_TONE[check.status]}>{t(`status.${check.status}`)}</StatusChip>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

function KnowledgeTable({ stats }: { stats: PreviewKnowledgeStat[] }) {
  const t = useTranslations("exams.preview.knowledge");
  const format = useFormatter();
  const lookup = useLookup();
  const { columns, cards } = useMemo(() => {
    const columns: ColumnDef<PreviewKnowledgeStat>[] = [
      {
        id: "chapter",
        header: t("chapter"),
        cell: ({ row }) => (
          <span className="flex min-w-40 flex-col">
            <span className="font-medium text-text">{row.original.name}</span>
            <span className="text-caption text-text-muted">{row.original.code}</span>
          </span>
        ),
      },
      { id: "subject", header: t("subject"), cell: ({ row }) => lookup.subject(row.original.subjectCode) },
      {
        id: "count",
        header: t("count"),
        cell: ({ row }) => <span className="tabular-nums">{format.number(row.original.count)}</span>,
      },
    ];
    const cards: TableCards<PreviewKnowledgeStat> = {
      render: (stat) => (
        <ListCard
          title={stat.name}
          caption={`${stat.code} · ${lookup.subject(stat.subjectCode)}`}
          meta={[{ label: t("count"), value: format.number(stat.count) }]}
        />
      ),
    };
    return { columns, cards };
  }, [t, format, lookup]);

  return (
    <Card padding="none" className="flex flex-col overflow-hidden">
      <h3 className="px-4 pt-4 pb-2 text-label font-semibold text-text">{t("title")}</h3>
      <Table
        aria-label={t("tableLabel")}
        columns={columns}
        data={stats}
        getRowId={(stat) => stat.nodeId}
        empty={<EmptyState headingLevel={4} title={t("empty")} />}
        cards={cards}
      />
    </Card>
  );
}
