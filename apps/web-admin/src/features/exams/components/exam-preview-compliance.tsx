"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { Card, ListCard, StatusChip, Table, type StatusTone, type TableCards } from "@thi360/ui";
import { CircleCheck, Info } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo } from "react";
import { skipToken, useQuery } from "@tanstack/react-query";
import { useContentReference } from "@/features/content/queries";
import { DISPLAY_NUMBER_FORMAT } from "../display-number";
import { EXAMS_QUERY_KEYS } from "../keys";
import { useExamStructure } from "../queries";
import {
  buildTopicQuotaIndex,
  commandDenominators,
  competencyName,
  complianceDifference,
  complianceLevel,
  droppedCount,
  examQuotaNames,
  isUnclassifiedRow,
  parseCriterion,
  parseScope,
  topicAssignments,
  topicQuotaPending,
  type ComplianceLevel,
} from "../review-compliance";
import type { ExamDetail } from "../types";
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
  /** Cần tra chỉ tiêu chủ đề trong cấu trúc (dòng `REFERENCE` chờ mapping, H31). */
  examId?: string;
  /** Xem thứ tự gốc (K12): số câu nêu ở chỉ tiêu chủ đề là số gốc, như số trên đề đang xem. */
  original?: boolean;
}


/**
 * Bảng Yêu cầu / Thực tế / Chênh lệch của đề thử (bản vẽ `SinhThu2B` + `SinhThuVact2B`, R-124): mọi tiêu chí kể cả
 * tham khảo, dòng lệch được đánh dấu theo mức (Bắt buộc là lỗi, Tham khảo là cảnh báo không chặn); dòng chỉ tiêu
 * `REFERENCE` chưa gắn MAPID (H31) hiện "Chưa có dữ liệu đối chiếu" và chip "Chờ mapping", không tính là lệch.
 * Môn 16 câu ghi "đã bỏ 1 câu" và nêu câu đã bỏ; chỉ tiêu chủ đề nêu các câu được gán.
 */
export function ExamPreviewCompliance({ paper, compliance, dropped, examId, original = false }: ExamPreviewComplianceProps) {
  const t = useTranslations("exams.preview.compliance");
  const tContent = useTranslations("content");
  const format = useFormatter();
  const lookup = useLookup();
  const reference = useContentReference().data;
  const assigned = useMemo(() => topicAssignments(paper, original), [paper, original]);
  const structure = useExamStructure(examId ?? "", undefined, Boolean(examId)).data;
  const quotas = useMemo(() => buildTopicQuotaIndex(structure), [structure]);
  const { columns, cards } = useMemo(() => {
    const number = (value: number) => format.number(value, DISPLAY_NUMBER_FORMAT);
    const blocks = new Map(paper.parts.flatMap((part) => part.blocks).map((block) => [block.blockId, block]));
    const sections = new Map(paper.subjects.map((subject) => [subject.sectionId, subject]));

    const criterion = (row: PreviewCompliance) => {
      if (isUnclassifiedRow(row)) return t("criterion.UNCLASSIFIED");
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
          return t("criterion.COMPETENCY", { name: competencyName(ref.code, reference) });
        case "TOPIC": {
          const quota = quotas?.get(ref.code);
          if (quota?.name) {
            const levels = quota.difficultyCodes.map((code) => lookup.difficulty(code)).filter(Boolean);
            return t("criterion.TOPIC_NAMED", { name: [quota.name, ...levels].join(" · ") });
          }
          return t("criterion.TOPIC");
        }
        case "EXAM":
          return t("criterion.EXAM", { names: examQuotaNames(ref.codes, reference).join(" + ") });
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
      if (isUnclassifiedRow(row)) return t("requiredNone");
      const atLeast = parseCriterion(row.criterion).kind === "TOPIC";
      const removed = droppedCount(row.note);
      const value = removed > 0 ? t("requiredDropped", { value: number(row.required), count: removed }) : number(row.required);
      return atLeast ? t("requiredAtLeast", { value }) : value;
    };
    const actual = (row: PreviewCompliance) => {
      if (topicQuotaPending(row, quotas)) return t("pendingNoData");
      const ref = parseCriterion(row.criterion);
      const numbers = ref.kind === "TOPIC" ? (assigned.get(ref.code) ?? []) : [];
      return numbers.length > 0 ? t("actualAssigned", { value: number(row.actual), numbers: numbers.join(", ") }) : number(row.actual);
    };
    const difference = (row: PreviewCompliance) => {
      if (topicQuotaPending(row, quotas)) return t("pendingNoData");
      const value = complianceDifference(row);
      const shown = number(Math.abs(value));
      return value < 0 ? `\u2212${shown}` : value > 0 ? `+${shown}` : shown;
    };
    const result = (row: PreviewCompliance) => {
      if (isUnclassifiedRow(row)) {
        return row.actual > 0 ? (
          <StatusChip tone="neutral">{t("result.MISSING_LABEL")}</StatusChip>
        ) : (
          <StatusChip tone="success">{t("result.OK")}</StatusChip>
        );
      }
      if (topicQuotaPending(row, quotas)) return <StatusChip tone="neutral">{t("result.PENDING")}</StatusChip>;
      const value = complianceLevel(row);
      return <StatusChip tone={LEVEL_TONE[value]}>{t(`result.${value}`)}</StatusChip>;
    };

    const columns: ColumnDef<PreviewCompliance>[] = [
      { id: "criterion", header: t("columns.criterion"), cell: ({ row }) => <span className="block min-w-32 font-semibold text-text">{criterion(row.original)}</span> },
      { id: "scope", header: t("columns.scope"), cell: ({ row }) => <span className="block min-w-32">{scope(row.original)}</span> },
      { id: "level", header: t("columns.level"), cell: ({ row }) => <span>{level(row.original)}</span> },
      { id: "required", header: t("columns.required"), cell: ({ row }) => <span className="block min-w-16 text-right">{required(row.original)}</span> },
      { id: "actual", header: t("columns.actual"), cell: ({ row }) => <span className="block min-w-16 text-right">{actual(row.original)}</span> },
      { id: "difference", header: t("columns.difference"), cell: ({ row }) => <span className="block min-w-16 text-right">{difference(row.original)}</span> },
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
            { label: t("columns.difference"), value: difference(row) },
          ]}
        />
      ),
    };
    return { columns, cards };
  }, [t, tContent, format, lookup, reference, paper, assigned, quotas]);

  if (compliance.length === 0 && dropped.length === 0) return null;
  const deviations = compliance.filter((row) => !topicQuotaPending(row, quotas) && complianceLevel(row) !== "OK");
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
      {examId && <DenominatorNotes paper={paper} compliance={compliance} examId={examId} />}
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

/**
 * Mẫu số của đối chiếu theo lệnh (D21): "Toán cả môn · 67 lệnh" với bài cả môn, "Toán · Phần II · 6 lệnh" với bài theo
 * phần. Chỉ hiện cho bài `SUBJECT` và `PART`, vì đó là hai phạm vi có đối chiếu theo lệnh ở mức tham khảo.
 */
function DenominatorNotes({ paper, compliance, examId }: { paper: PreviewPaper; compliance: readonly PreviewCompliance[]; examId: string }) {
  const t = useTranslations("exams.preview.compliance");
  const lookup = useLookup();
  // Chỉ đọc bản đã có trong bộ nhớ đệm (trang xem trước đã tải bài): không gọi thêm API chỉ để biết phạm vi.
  const exam = useQuery<ExamDetail>({ queryKey: EXAMS_QUERY_KEYS.detail(examId), queryFn: skipToken }).data;
  const denominators = useMemo(() => commandDenominators(compliance), [compliance]);
  if (!exam || (exam.scope !== "SUBJECT" && exam.scope !== "PART") || denominators.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1">
      {denominators.map((denominator) => {
        const ref = parseScope(denominator.scope);
        const section =
          ref.kind === "SECTION"
            ? paper.subjects.find((subject) => subject.sectionId === ref.id)
            : paper.subjects.length === 1
              ? paper.subjects[0]
              : undefined;
        const subject = lookup.subject(section?.subjectCode ?? "");
        const key = exam.scope === "PART" ? "denominator.part" : "denominator.whole";
        return (
          <li key={denominator.scope} className="text-label font-semibold text-text">
            {t(key, { subject, part: paper.parts.map((part) => part.name).join(", "), count: denominator.total })}
          </li>
        );
      })}
    </ul>
  );
}
