"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { Button, Card, ListCard, StatusChip, Table, type StatusTone, type TableCards } from "@thi360/ui";
import { ArrowRight, CircleCheck, Info, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo } from "react";
import { skipToken, useQuery } from "@tanstack/react-query";
import { useContentReference } from "@/features/content/queries";
import { DISPLAY_NUMBER_FORMAT } from "../display-number";
import { EXAMS_QUERY_KEYS } from "../keys";
import { useExamStructure } from "../queries";
import {
  buildMapidRuleIndex,
  buildTopicQuotaIndex,
  commandDenominators,
  competencyName,
  complianceDifference,
  complianceLevel,
  droppedCount,
  examQuotaNames,
  isAtLeastRow,
  isUnclassifiedRow,
  parseCriterion,
  resolveMapidRow,
  parseScope,
  topicAssignments,
  topicQuotaPending,
  type ComplianceLevel,
} from "../review-compliance";
import type { ExamDetail } from "../types";
import type { PreviewCompliance, PreviewPaper, PreviewQuestion } from "../review-types";
import { ExamContentText } from "./exam-content-text";
import { ExamName, richNodes, useLookup } from "./exam-review-shared";
import { useMapidRuleText } from "./mapid-rules-table";

const LEVEL_TONE: Record<ComplianceLevel, StatusTone> = {
  OK: "success",
  REFERENCE_DEVIATION: "warning",
  REQUIRED_DEVIATION: "danger",
};

export function mergeSubjectQuotasIntoCompliance(
  paper: PreviewPaper,
  compliance: readonly PreviewCompliance[] = [],
): PreviewCompliance[] {
  const seen = new Set(compliance.map((row) => `${row.scope}|${row.criterion}`));
  const extras: PreviewCompliance[] = [];
  for (const subject of paper.subjects) {
    const scope = `SECTION:${subject.sectionId}`;
    for (const quota of subject.quotas) {
      const criterion = `${quota.kind}:${quota.code}`;
      const key = `${scope}|${criterion}`;
      if (seen.has(key)) continue;
      seen.add(key);
      extras.push({
        criterion,
        scope,
        required: quota.required,
        actual: quota.generated,
        enforcement: "REQUIRED",
        basis: "QUESTION",
        independent: false,
        note: null,
        originalRequired: null,
        topicName: null,
      });
    }
  }
  return extras.length > 0 ? [...compliance, ...extras] : [...compliance];
}

export function ExamPreviewComplianceBanner({
  paper,
  compliance,
  examId,
  onOpenAllocation,
}: {
  paper: PreviewPaper;
  compliance: readonly PreviewCompliance[];
  examId?: string;
  onOpenAllocation: () => void;
}) {
  const t = useTranslations("exams.preview.compliance");
  const structureQuery = useExamStructure(examId ?? "", undefined, Boolean(examId));
  const structure = structureQuery.data;
  const quotas = useMemo(() => buildTopicQuotaIndex(structure), [structure]);
  const structureUnavailable = Boolean(examId) && structure === undefined;
  const rules = useMemo(() => buildMapidRuleIndex(structure), [structure]);
  const merged = useMemo(() => mergeSubjectQuotasIntoCompliance(paper, compliance), [paper, compliance]);
  const deviations = useMemo(
    () =>
      merged.filter(
        (row) => !topicQuotaPending(row, quotas, structureUnavailable) && complianceLevel(row, rules) !== "OK",
      ),
    [merged, quotas, structureUnavailable, rules],
  );
  if (deviations.length === 0) return null;
  const blocking = deviations.some((row) => complianceLevel(row, rules) === "REQUIRED_DEVIATION");
  return (
    <div
      className={`flex flex-wrap items-center justify-between gap-2 rounded-lg border px-3.5 py-2 text-body ${
        blocking ? "border-danger/30 bg-danger-bg" : "border-warning/30 bg-warning-bg"
      }`}
    >
      <span className="flex items-center gap-2 text-text">
        <TriangleAlert aria-hidden className={`size-4 shrink-0 ${blocking ? "text-danger" : "text-warning"}`} />
        <span>{t("banner", { count: deviations.length })}</span>
      </span>
      <Button type="button" variant="link" size="sm" onClick={onOpenAllocation}>
        {t("viewAllocation")}
        <ArrowRight />
      </Button>
    </div>
  );
}

export interface ExamPreviewComplianceProps {
  paper: PreviewPaper;
  compliance: readonly PreviewCompliance[];
  /** Câu đã bỏ của môn 16 câu (HSA Khoa học). */
  dropped: readonly PreviewQuestion[];
  examId?: string;
  original?: boolean;
}


export function ExamPreviewCompliance({ paper, compliance, dropped, examId, original = false }: ExamPreviewComplianceProps) {
  const t = useTranslations("exams.preview.compliance");
  const tContent = useTranslations("content");
  const format = useFormatter();
  const lookup = useLookup();
  const reference = useContentReference().data;
  const assigned = useMemo(() => topicAssignments(paper, original), [paper, original]);
  const structureQuery = useExamStructure(examId ?? "", undefined, Boolean(examId));
  const structure = structureQuery.data;
  const quotas = useMemo(() => buildTopicQuotaIndex(structure), [structure]);
  // Chưa có cấu trúc để biết dòng chủ đề nào chờ mapping: trung tính trong lúc tải, kèm lời nhắn khi tải lỗi.
  const structureUnavailable = Boolean(examId) && structure === undefined;
  const rules = useMemo(() => buildMapidRuleIndex(structure), [structure]);
  const { sentence } = useMapidRuleText(reference);
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
          // Tên chủ đề do máy chủ gửi kèm từng dòng (`topicName`); chưa có thì lấy ở chỉ tiêu của cấu trúc. Độ khó của chỉ tiêu
          // chỉ có ở cấu trúc nên hiện thêm khi cấu trúc đã tải.
          const quota = quotas?.get(ref.code);
          const name = row.topicName?.trim() || quota?.name?.trim();
          if (name) {
            const levels = (quota?.difficultyCodes ?? []).map((code) => lookup.difficulty(code)).filter(Boolean);
            return levels.length > 0 ? (
              <>
                <ExamName>{name}</ExamName> · {levels.join(" · ")}
              </>
            ) : (
              <ExamName>{name}</ExamName>
            );
          }
          return t("criterion.TOPIC");
        }
        case "EXAM":
          return t("criterion.EXAM", { names: examQuotaNames(ref.codes, reference).join(" + ") });
        case "MAPID": {
          // Quy tắc của dòng gọi bằng câu mô tả của nó; "Chỉ xuất hiện ở nhóm này" có hai dòng (ngoài nhóm, số câu trong nhóm).
          const found = rules ? resolveMapidRow(row, rules) : null;
          if (!found) return t("criterion.MAPID_UNKNOWN", { id: ref.ruleId });
          const text = sentence(found.section, found.rule, true);
          const label =
            found.rule.type !== "ONLY_IN_GROUP"
              ? t("criterion.MAPID", { sentence: text })
              : t(ref.count ? "criterion.MAPID_COUNT" : "criterion.MAPID_OUTSIDE", { sentence: text });
          return <ExamContentText text={label} />;
        }
        default:
          return ref.raw;
      }
    };
    const scope = (row: PreviewCompliance) => {
      const ref = parseScope(row.scope);
      if (ref.kind === "SECTION") return lookup.subject(sections.get(ref.id)?.subjectCode ?? "") || t("scope.EXAM");
      if (ref.kind === "BLOCK") {
        const block = blocks.get(ref.id);
        return block ? (
          <>
            {lookup.subject(block.subjectCode)} · <ExamName>{block.blockName}</ExamName>
          </>
        ) : (
          ref.id
        );
      }
      return t("scope.EXAM");
    };
    const level = (row: PreviewCompliance) =>
      t("level", { enforcement: t(`enforcement.${row.enforcement}`), unit: t(`unit.${row.basis}`) });
    const required = (row: PreviewCompliance) => {
      if (isUnclassifiedRow(row) || row.required === null) return t("requiredNone");
      const atLeast = isAtLeastRow(row, rules);
      const removed = droppedCount(row.note);
      // Dòng đã bỏ câu: hiện cả số yêu cầu gốc của chỉ tiêu ("16 (17, đã bỏ 1 câu)"); máy chủ chưa gửi số gốc thì chỉ nêu số câu bỏ.
      const value =
        removed > 0
          ? row.originalRequired !== null
            ? t("requiredOriginal", { value: number(row.required), original: number(row.originalRequired), count: removed })
            : t("requiredDropped", { value: number(row.required), count: removed })
          : number(row.required);
      return atLeast ? t("requiredAtLeast", { value }) : value;
    };
    const actual = (row: PreviewCompliance) => {
      if (topicQuotaPending(row, quotas, structureUnavailable)) return t("pendingNoData");
      const ref = parseCriterion(row.criterion);
      const numbers = ref.kind === "TOPIC" ? (assigned.get(ref.code) ?? []) : [];
      return numbers.length > 0 ? t("actualAssigned", { value: number(row.actual), numbers: numbers.join(", ") }) : number(row.actual);
    };
    const difference = (row: PreviewCompliance) => {
      if (topicQuotaPending(row, quotas, structureUnavailable)) return t("pendingNoData");
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
      if (topicQuotaPending(row, quotas, structureUnavailable)) return <StatusChip tone="neutral">{t("result.PENDING")}</StatusChip>;
      const value = complianceLevel(row, rules);
      return <StatusChip tone={LEVEL_TONE[value]}>{t(`result.${value}`)}</StatusChip>;
    };

    const columns: ColumnDef<PreviewCompliance>[] = [
      { id: "criterion", header: t("columns.criterion"), cell: ({ row }) => <span className="block min-w-32 font-semibold text-text">{criterion(row.original)}</span> },
      { id: "scope", header: t("columns.scope"), cell: ({ row }) => <span className="block min-w-32">{scope(row.original)}</span> },
      { id: "level", header: t("columns.level"), cell: ({ row }) => <span>{level(row.original)}</span> },
      { id: "required", header: () => <span className="block text-right">{t("columns.required")}</span>, cell: ({ row }) => <span className="block min-w-16 text-right">{required(row.original)}</span> },
      { id: "actual", header: () => <span className="block text-right">{t("columns.actual")}</span>, cell: ({ row }) => <span className="block min-w-16 text-right">{actual(row.original)}</span> },
      { id: "difference", header: () => <span className="block text-right">{t("columns.difference")}</span>, cell: ({ row }) => <span className="block min-w-16 text-right">{difference(row.original)}</span> },
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
  }, [t, tContent, format, lookup, reference, paper, assigned, quotas, structureUnavailable, rules, sentence]);

  if (compliance.length === 0 && dropped.length === 0) return null;
  const hasPendingStructure = structureUnavailable && compliance.some((row) => topicQuotaPending(row, quotas, true));
  const deviations = compliance.filter(
    (row) => !topicQuotaPending(row, quotas, structureUnavailable) && complianceLevel(row, rules) !== "OK",
  );
  const blocking = deviations.some((row) => complianceLevel(row, rules) === "REQUIRED_DEVIATION");

  return (
    <section aria-labelledby="preview-compliance-title" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <h3 id="preview-compliance-title" className="text-title font-semibold text-text">
          {t("title")}
        </h3>
        {compliance.length > 0 &&
          (deviations.length > 0 ? (
            <StatusChip tone={blocking ? "danger" : "warning"}>{t("deviations", { count: deviations.length })}</StatusChip>
          ) : !hasPendingStructure ? (
            <StatusChip tone="success">{t("allPassed")}</StatusChip>
          ) : null)}
      </div>
      {structureQuery.isError && (
        <p role="note" className="flex items-start gap-2 text-caption text-text-muted">
          <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
          {t("structureUnavailable")}
        </p>
      )}
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

function DenominatorNotes({ paper, compliance, examId }: { paper: PreviewPaper; compliance: readonly PreviewCompliance[]; examId: string }) {
  const t = useTranslations("exams.preview.compliance");
  const lookup = useLookup();
  // Chỉ đọc bản đã có trong bộ nhớ đệm (trang xem trước đã tải bài): không gọi thêm API chỉ để biết phạm vi.
  const exam = useQuery<ExamDetail>({ queryKey: EXAMS_QUERY_KEYS.detail(examId), queryFn: skipToken }).data;
  const denominators = useMemo(() => commandDenominators(compliance), [compliance]);
  // Mã môn chung của đề (khi các mục môn cùng một môn): bài V-SAT có nhiều mục `paper.subjects` cùng `subjectCode`.
  const sectionSubjectCodes = new Set(paper.subjects.map((subject) => subject.subjectCode));
  if (!exam || (exam.scope !== "SUBJECT" && exam.scope !== "PART") || denominators.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1">
      {denominators.map((denominator) => {
        const ref = parseScope(denominator.scope);
        const section = ref.kind === "SECTION" ? paper.subjects.find((subject) => subject.sectionId === ref.id) : undefined;
        // Scope cả bài không gắn mục môn: lấy `subjectCode` của bài, không có thì lấy môn chung duy nhất của các mục.
        const subjectCode = section?.subjectCode ?? exam.subjectCode ?? (sectionSubjectCodes.size === 1 ? [...sectionSubjectCodes][0] : null);
        const subject = subjectCode ? lookup.subject(subjectCode) : null;
        const key = exam.scope === "PART" ? (subject ? "denominator.part" : "denominator.partNoSubject") : subject ? "denominator.whole" : "denominator.wholeNoSubject";
        return (
          <li key={denominator.scope} className="text-label font-semibold text-text">
            {richNodes(t, key, {
              part: <ExamName>{paper.parts.map((part) => part.name).join(", ")}</ExamName>,
              count: denominator.total,
              ...(subject ? { subject } : {}),
            })}
          </li>
        );
      })}
    </ul>
  );
}
