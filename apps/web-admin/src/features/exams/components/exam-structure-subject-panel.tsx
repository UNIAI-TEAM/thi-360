"use client";

import { Badge, Button, Card, cn, focusRing, FormField, IconButton, Input, Select, StatusChip } from "@thi360/ui";
import { CircleMinus, Lock, Plus } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId, useRef, useState, type ReactNode } from "react";
import { QUESTION_TYPES, type ContentReference, type QuestionType } from "@/features/content/types";
import type { AudioCountQuery } from "../api.http";
import { applyTopicTotal, convertTopicEntry, ratePercent, topicCountFor, totalChangePending } from "../allocation";
import { apportionByPercent, computeAllocation, convertMatrixUnit, countsToPercents, emptyMatrix, isSetBlock, type MatrixAxes, sum } from "../matrix";
import { useSubjectAvailableQuestions } from "../queries";
import { referenceEntries, referenceToMatrix, type ReferenceEntry } from "../reference-distribution";
import { effectiveScope, effectiveScopeSource, groupAnswerType, remainderGroupCount, sectionCaseCounts, subjectAllocation, type QuotaRow } from "../subject-matrix";
import type { AnswerQuotas, ExamQuota, ExamSection, KnowledgeScope, MatrixUnit, QuestionBlock } from "../types";
import { BlockMatrixEditor, type MatrixLabels } from "./block-matrix-editor";
import { BlockNumberInput } from "./block-number-input";
import { ChoiceCardGroup } from "./create-exam-fields";
import { newBlock, type SectionContext } from "./exam-structure-draft";
import { KnowledgeScopeField } from "./knowledge-scope-field";
import { ScrollableTable } from "./scrollable-table";
import { ReferenceDistributions, TotalChangeNotice } from "./subject-allocation-extras";
import { TopicQuotaTable, type TopicQuotaServerError } from "./topic-quota-table";

/** Dạng trả lời khai được ở chỉ tiêu cấp môn: mọi dạng trừ Bộ câu hỏi (đếm theo câu con, D22). */
const QUOTA_TYPES = QUESTION_TYPES.filter((type) => type !== "QUESTION_SET");

/** Chỉ tiêu dạng trả lời nhập theo tỷ lệ quy về số câu của tổng `total` (phần dư lớn nhất); nhập theo số câu giữ nguyên. */
function toCountQuotas(quotas: AnswerQuotas | null, total: number): AnswerQuotas | null {
  if (quotas?.unit !== "PERCENT") return quotas;
  const counts = apportionByPercent(quotas.items.map((item) => item.value), total).results;
  return { unit: "COUNT", items: quotas.items.map((item, index) => ({ ...item, value: counts[index] ?? 0 })) };
}

export interface SubjectPanelProps {
  context: SectionContext;
  subjectName: string;
  axes: MatrixAxes;
  labels: MatrixLabels;
  reference: ContentReference;
  onChange: (update: (section: ExamSection) => ExamSection) => void;
  onSelectBlock: (blockId: string) => void;
  onAddBlock: () => void;
  /** Nút nằm ở đầu vùng chi tiết (bản vẽ `BaiThiBuoc2`: "Kiểm tra môn"). */
  headerAction?: ReactNode;
  /** Mẫu khóa số câu từng phần, mục (H27): ô "Số câu của mục" chỉ đọc; phân bổ bên trong mục vẫn sửa được. */
  countLocked?: boolean;
  /** Sửa số câu của mục; bỏ trống: ô chỉ đọc. */
  onCount?: (count: number) => void;
  /** Môn đã kiểm tra đạt và chưa sửa gì từ đó: hiện "Đủ nguồn theo ma trận" (R-122). */
  sourceVerified?: boolean;
  /** Lỗi máy chủ ở khối chỉ tiêu chủ đề của môn (H20). */
  topicError?: TopicQuotaServerError;
  /** Số câu của mục đã lưu hoặc đã xác nhận gần nhất, để phát hiện tổng đổi (R-121). */
  confirmedCount?: number | null;
  onConfirmCount?: (count: number) => void;
  /** Chỉ tiêu cấp bài của bài, để liệt kê phân bố tham khảo của riêng môn này. */
  examQuotas?: readonly ExamQuota[];
  onExamQuotas?: (examQuotas: ExamQuota[]) => void;
  /** Mã kỳ thi: HSA Tiếng Anh khối lớp luôn chờ chuyên gia xác nhận. */
  examKind?: string | null;
}

/**
 * Môn của phần thi (bản vẽ `BaiThiCauTruc`, UniNote PBI-04 mục 6.1, spec 9.1): khu chỉ tiêu cấp môn (dạng trả lời, khối
 * lớp, độ khó, phạm vi MAPID) kèm số đã phân bổ và còn thiếu, rồi danh sách nhóm lấy câu do mẫu khởi tạo. Câu con của
 * chùm được trừ vào chỉ tiêu dạng trả lời; nhóm mở rộng ra màn nhóm để sửa.
 */
export function ExamStructureSubjectPanel({
  context,
  subjectName,
  axes,
  labels,
  reference,
  onChange,
  onSelectBlock,
  onAddBlock,
  headerAction,
  countLocked = false,
  onCount,
  sourceVerified = false,
  topicError = null,
  confirmedCount = null,
  onConfirmCount,
  examQuotas = [],
  onExamQuotas,
  examKind = null,
}: SubjectPanelProps) {
  const t = useTranslations("exams.structure.subject");
  const tContent = useTranslations("content");
  const { section, option } = context;
  const cases = sectionCaseCounts(section, option);
  const [caseIndex, setCaseIndex] = useState(0);
  const count = cases[Math.min(caseIndex, cases.length - 1)] ?? null;
  const varies = cases.length > 1;
  const allocation = subjectAllocation(section, axes, count);
  const typeLabel = (type: QuestionType) => tContent(`questionType.${type}`);

  const quotaTypes = section.answerQuotas?.items.map((item) => item.answerType) ?? [];
  const availableQuery = useSubjectAvailableQuestions({
    subjectCode: section.subjectCode,
    groups: section.blocks.map(
      (block): AudioCountQuery => ({
        subjectCode: section.subjectCode,
        questionType: block.questionType,
        answerTypes: block.questionType === null ? quotaTypes : [],
        childAnswerType: block.childAnswerType,
        knowledgeScope: effectiveScope(block, section),
        tagCodes: block.tagCodes,
        requiredPointsPerQuestion: block.requiredPointsPerQuestion,
        itemShape: block.itemShape,
        answerFormats: block.answerFormats,
        childConditions: block.childConditions,
        // R-130, R-142: môn của phần Nghe chỉ đếm câu có audio, phần Đọc chỉ đếm câu không audio. `ANY` và bỏ trống: không gửi.
        ...(section.audioRequirement === "REQUIRED" || section.audioRequirement === "FORBIDDEN" ? { audioRequirement: section.audioRequirement } : {}),
      }),
    ),
  });
  const availableState = availableQuery.isError ? "error" : availableQuery.data ? "ready" : "loading";
  const sourceOf = (answerType: string) => availableQuery.data?.byAnswerType.find((item) => item.answerType === answerType)?.value;

  const setQuotas = (answerQuotas: AnswerQuotas | null) => onChange((current) => ({ ...current, answerQuotas }));
  const quotas = section.answerQuotas;
  const setUnit = (unit: MatrixUnit) => {
    if (!quotas || quotas.unit === unit) return;
    const values = quotas.items.map((item) => item.value);
    const base = count ?? 0;
    const next =
      base <= 0
        ? values
        : unit === "PERCENT"
          ? countsToPercents(values, base)
          : apportionByPercent(values, base).results;
    setQuotas({ unit, items: quotas.items.map((item, index) => ({ ...item, value: next[index] ?? 0 })) });
  };
  const quotaTotal = quotas ? sum(quotas.items.map((item) => item.value)) : 0;
  const quotaExpected = quotas?.unit === "PERCENT" ? 100 : count;
  const quotaMismatch = quotas !== null && quotaExpected !== null && quotas.items.length > 0 && quotaTotal !== quotaExpected;
  const quotaVaries = quotas?.unit === "COUNT" && varies;
  const usedTypes = new Set(quotaTypes);
  // Dạng mà nhóm dùng nhưng môn chưa khai chỉ tiêu: hiện thành hàng riêng, đã phân bổ vượt chỉ tiêu 0 (R1-6).
  const undeclaredRows = allocation.answerTypes?.filter((row) => !usedTypes.has(row.key as QuestionType)) ?? [];

  const matrixBlock: QuestionBlock | null = section.matrix
    ? { ...newBlock(subjectName), id: section.id, questionCount: count ?? 0, matrix: section.matrix }
    : null;
  const matrixVaries = section.matrix?.unit === "COUNT" && varies;

  const setScope = (knowledgeScope: KnowledgeScope | null) => onChange((current) => ({ ...current, knowledgeScope }));
  // Ô "Mọi câu con thuộc chủ đề" của phạm vi môn (mẫu VACT_REVIEW bật sẵn ở máy chủ): chùm kế thừa phạm vi này khi
  // chưa chọn MAPID riêng. Không có giá trị mặc định ở màn; tắt thì bỏ cờ (thiếu cờ là tắt).
  const setEveryChild = (checked: boolean) =>
    onChange((current) => {
      const scope: KnowledgeScope = { ...(current.knowledgeScope ?? { nodeIds: [] }) };
      delete scope.everyChildInScope;
      return { ...current, knowledgeScope: checked ? { ...scope, everyChildInScope: true } : scope };
    });

  const sectionCount = section.questionCount;
  const topicQuotas = section.topicQuotas ?? [];
  const totalChanged = totalChangePending(section, confirmedCount);
  const noticeLines: string[] = [];
  if (totalChanged && sectionCount !== null) {
    const grade = (code: string) => labels.grade(code);
    const difficulty = (code: string) => labels.difficulty(code);
    if (section.matrix?.unit === "PERCENT") {
      const next = computeAllocation({ questionCount: sectionCount, matrix: section.matrix }, axes);
      noticeLines.push(t("totalChange.grades", { values: axes.grades.map((code, index) => `${grade(code)} ${next.byGrade[index] ?? 0}`).join(" · ") }));
      noticeLines.push(t("totalChange.difficulties", { values: axes.difficulties.map((code, index) => `${difficulty(code)} ${next.byDifficulty[index] ?? 0}`).join(" · ") }));
    }
    if (quotas?.unit === "PERCENT") {
      const next = apportionByPercent(quotas.items.map((item) => item.value), sectionCount).results;
      noticeLines.push(t("totalChange.answerTypes", { values: quotas.items.map((item, index) => `${typeLabel(item.answerType)} ${next[index] ?? 0}`).join(" · ") }));
    }
    if (topicQuotas.some((quota) => quota.percent !== undefined && quota.percent !== null)) {
      noticeLines.push(
        t("totalChange.topics", {
          values: topicQuotas.map((quota) => `${quota.name || t("totalChange.untitled")} ${topicCountFor(quota, sectionCount)}`).join(" · "),
        }),
      );
    }
  }
  // Hai nút của thông báo biến mất khi bấm: đưa tiêu điểm về ô số câu để người dùng bàn phím không mất chỗ (R1-26).
  const countRef = useRef<HTMLDivElement>(null);
  const focusCount = () => requestAnimationFrame(() => countRef.current?.querySelector("input")?.focus());
  const applyNewTotal = () => {
    if (sectionCount === null) return;
    onChange((current) => ({ ...current, topicQuotas: applyTopicTotal(current.topicQuotas ?? [], sectionCount) }));
    onConfirmCount?.(sectionCount);
    focusCount();
  };
  const keepOldTotal = () => {
    if (sectionCount === null || confirmedCount === null) return;
    onChange((current) => ({
      ...current,
      matrix: current.matrix?.unit === "PERCENT" ? convertMatrixUnit(current.matrix, "COUNT", confirmedCount, axes) : current.matrix,
      answerQuotas: toCountQuotas(current.answerQuotas, confirmedCount),
      topicQuotas: convertTopicEntry(applyTopicTotal(current.topicQuotas ?? [], confirmedCount), "COUNT", confirmedCount),
    }));
    onConfirmCount?.(sectionCount);
    focusCount();
  };
  const references = referenceEntries(section, examQuotas, examKind);
  const copyReference = (entry: ReferenceEntry) => {
    if (entry.kind !== "quota") return;
    onChange((current) => ({ ...current, matrix: referenceToMatrix(current, entry.quota, axes) }));
  };
  const applyReference = (entry: ReferenceEntry) => {
    if (entry.kind === "quota") {
      onExamQuotas?.(examQuotas.map((quota) => (quota.id === entry.quota.id ? { ...quota, enforcement: "REQUIRED" } : quota)));
      return;
    }
    onChange((current) =>
      current.matrix
        ? {
            ...current,
            matrix: {
              ...current.matrix,
              ...(current.matrix.mode === "COMBINED"
                ? { gradeEnforcement: "REQUIRED" as const, difficultyEnforcement: "REQUIRED" as const }
                : entry.dimension === "GRADE"
                  ? { gradeEnforcement: "REQUIRED" as const }
                  : { difficultyEnforcement: "REQUIRED" as const }),
            },
          }
        : current,
    );
  };

  return (
    <Card padding="md" className="flex flex-col gap-6 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 className="flex flex-wrap items-center gap-2 text-h3 font-bold wrap-break-word text-text">
            {t("title", { subject: subjectName })}
            {section.choiceGroup && <Badge className="font-medium">{t(`choiceGroup.${section.choiceGroup}`)}</Badge>}
          </h2>
          <p className="text-caption text-text-muted">
            {cases.length === 0
              ? t("countUnknown")
              : varies
                ? t("countVaries", { counts: cases.join(" / ") })
                : t("countFixed", { count: cases[0] ?? 0 })}
          </p>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-3">
          <p role="status" className="flex flex-wrap items-center gap-2 text-body">
            <span className="text-text-muted">{t("allocated")}</span>
            <span className="font-semibold text-text tabular-nums">
              {allocation.count === null
                ? t("allocatedUnknown", { allocated: allocation.allocated })
                : t("allocatedValue", { allocated: allocation.allocated, count: allocation.count })}
            </span>
            {allocation.missing !== null && (
              <StatusChip tone={allocation.missing === 0 ? "success" : "warning"}>
                {allocation.missing === 0
                  ? t("matches")
                  : allocation.missing > 0
                    ? t("missing", { count: allocation.missing })
                    : t("over", { count: -allocation.missing })}
              </StatusChip>
            )}
            {sourceVerified && allocation.missing === 0 && <StatusChip tone="success">{t("sourceEnough")}</StatusChip>}
          </p>
          {headerAction}
        </div>
      </div>

      {sectionCount !== null && (
        <div ref={countRef} className="flex max-w-md flex-col gap-1.5">
          <FormField
            label={t("countField.label")}
            hint={countLocked ? t("countField.lockedHint") : t("countField.hint")}
          >
            {(controlProps) =>
              countLocked || !onCount ? (
                <span className="flex items-center gap-2">
                  <Input {...controlProps} value={String(sectionCount)} readOnly aria-readonly="true" className="w-24 tabular-nums" />
                  {countLocked && (
                    <span className="inline-flex items-center gap-1 text-label font-medium text-text-secondary">
                      <Lock aria-hidden className="size-3.5" />
                      {t("countField.locked")}
                    </span>
                  )}
                </span>
              ) : (
                <BlockNumberInput {...controlProps} className="w-24" value={sectionCount} onValueChange={onCount} />
              )
            }
          </FormField>
        </div>
      )}

      {totalChanged && sectionCount !== null && confirmedCount !== null && (
        <TotalChangeNotice oldTotal={confirmedCount} newTotal={sectionCount} lines={noticeLines} onApply={applyNewTotal} onKeep={keepOldTotal} />
      )}

      {varies && (
        <div className="flex flex-col gap-1">
          <ChoiceCardGroup
            variant="pill"
            legend={t("caseLegend")}
            name={`${section.id}-case`}
            value={String(count)}
            onChange={(value) => setCaseIndex(Math.max(0, cases.indexOf(Number(value))))}
            options={cases.map((item) => ({ value: String(item), label: t("caseOption", { count: item }) }))}
          />
          <p className="text-caption text-text-muted">{t("caseHint")}</p>
        </div>
      )}

      <section aria-label={t("quotaTitle")} className="flex flex-col gap-4">
        <h3 className="text-title font-semibold text-text">{t("quotaTitle")}</h3>
        <p className="text-caption text-text-muted">{t("quotaHint")}</p>

        <div className="flex flex-col gap-2 rounded-md border border-border p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="text-label font-semibold text-text">{t("answerTypes.title")}</h4>
            {quotas ? (
              <Button type="button" variant="link" size="sm" onClick={() => setQuotas(null)}>
                {t("answerTypes.remove")}
              </Button>
            ) : (
              <Button type="button" variant="secondary" size="sm" onClick={() => setQuotas({ unit: "COUNT", items: [] })}>
                <Plus />
                <span>{t("answerTypes.declare")}</span>
              </Button>
            )}
          </div>
          {quotas === null ? (
            <p className="text-body text-text-muted">{t("answerTypes.none")}</p>
          ) : (
            <>
              <ChoiceCardGroup
                variant="pill"
                legend={t("unit")}
                name={`${section.id}-quota-unit`}
                value={quotas.unit}
                onChange={setUnit}
                options={[
                  { value: "COUNT", label: t("unitCount") },
                  { value: "PERCENT", label: t("unitPercent") },
                ]}
              />
              <div role="region" tabIndex={0} aria-label={t("answerTypes.title")} className={cn("relative overflow-x-auto rounded-md border border-border", focusRing)}>
                <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
                  <caption className="sr-only">{t("answerTypes.caption", { subject: subjectName })}</caption>
                  <thead>
                    <tr className="text-left text-caption text-text-muted">
                      <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("answerTypes.type")}</th>
                      <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">{t("answerTypes.quota")}</th>
                      <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">{t("answerTypes.resolved")}</th>
                      <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">{t("answerTypes.allocated")}</th>
                      <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">{t("answerTypes.missing")}</th>
                      <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">{t("answerTypes.source")}</th>
                      <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold"><span className="sr-only">{t("actions")}</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {quotas.items.map((item, index) => {
                      const row = allocation.answerTypes?.find((candidate) => candidate.key === item.answerType);
                      const source = sourceOf(item.answerType);
                      return (
                        <tr key={item.answerType}>
                          <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">{typeLabel(item.answerType)}</th>
                          <td className="border-b border-border px-2 py-2 text-right">
                            <BlockNumberInput
                              className="ml-auto h-9 w-20"
                              value={item.value}
                              aria-label={t("answerTypes.quotaAria", { type: typeLabel(item.answerType) })}
                              onValueChange={(value) =>
                                setQuotas({ ...quotas, items: quotas.items.map((entry, position) => (position === index ? { ...entry, value } : entry)) })
                              }
                            />
                          </td>
                          <td className="border-b border-border px-2 py-2 text-right">{row ? row.quota : "—"}</td>
                          <td className="border-b border-border px-2 py-2 text-right">{row ? row.allocated : "—"}</td>
                          <td className={cn("border-b border-border px-2 py-2 text-right font-semibold", row && row.missing !== 0 && "text-warning")}>
                            {row ? row.missing : "—"}
                          </td>
                          <td className="border-b border-border px-2 py-2 text-right text-text-muted">
                            {availableState === "ready" && source !== undefined ? source : "—"}
                          </td>
                          <td className="border-b border-border px-3 py-1 text-right">
                            <IconButton
                              icon={<CircleMinus />}
                              aria-label={t("answerTypes.removeType", { type: typeLabel(item.answerType) })}
                              onClick={() => setQuotas({ ...quotas, items: quotas.items.filter((_, position) => position !== index) })}
                            />
                          </td>
                        </tr>
                      );
                    })}
                    {undeclaredRows.map((row) => (
                      <tr key={row.key}>
                        <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">
                          {typeLabel(row.key as QuestionType)}
                          <span className="block text-caption font-normal text-warning">{t("answerTypes.undeclared")}</span>
                        </th>
                        <td className="border-b border-border px-2 py-2 text-right text-text-muted">—</td>
                        <td className="border-b border-border px-2 py-2 text-right">{row.quota}</td>
                        <td className="border-b border-border px-2 py-2 text-right">{row.allocated}</td>
                        <td className="border-b border-border px-2 py-2 text-right font-semibold text-warning">{row.missing}</td>
                        <td className="border-b border-border px-2 py-2 text-right text-text-muted">
                          {availableState === "ready" && sourceOf(row.key) !== undefined ? sourceOf(row.key) : "—"}
                        </td>
                        <td className="border-b border-border px-3 py-1" />
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="font-semibold text-text">
                      <th scope="row" className="px-3 py-2 text-left">{t("total")}</th>
                      <td className={cn("px-2 py-2 text-right", quotaMismatch && "text-danger")}>
                        <output>
                          {quotas.unit === "PERCENT"
                            ? t("totalPercent", { total: quotaTotal })
                            : count === null
                              ? quotaTotal
                              : t("totalOf", { total: quotaTotal, count })}
                        </output>
                      </td>
                      <td colSpan={5} />
                    </tr>
                  </tfoot>
                </table>
              </div>
              {quotaMismatch && (
                <p className="text-caption text-danger">
                  {quotas.unit === "PERCENT" ? t("answerTypes.percentMismatch", { total: quotaTotal }) : t("answerTypes.countMismatch", { total: quotaTotal, count: count ?? 0 })}
                </p>
              )}
              {quotaVaries && <p className="text-caption text-danger">{t("countVariesError")}</p>}
              {allocation.openAllocated > 0 && allocation.openMissing !== null && (
                <p className="text-caption text-text-muted">
                  {allocation.openMissing === 0
                    ? t("answerTypes.openSplit", {
                        count: allocation.openAllocated,
                        split: allocation.openSplit.map((row) => `${row.count} ${typeLabel(row.key as QuestionType)}`).join(" + "),
                      })
                    : t("answerTypes.openDiff", { count: allocation.openAllocated, missing: allocation.openMissing })}
                </p>
              )}
              <div className="flex items-center gap-2">
                <Select
                  aria-label={t("answerTypes.addType")}
                  value=""
                  placeholder={t("answerTypes.addType")}
                  onValueChange={(value) => setQuotas({ ...quotas, items: [...quotas.items, { answerType: value as QuestionType, value: 0 }] })}
                  options={QUOTA_TYPES.filter((type) => !usedTypes.has(type)).map((type) => ({ value: type, label: typeLabel(type) }))}
                />
              </div>
            </>
          )}
        </div>

        <div className="flex flex-col gap-3 rounded-md border border-border p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="text-label font-semibold text-text">{t("matrix.title")}</h4>
            {section.matrix ? (
              <Button type="button" variant="link" size="sm" onClick={() => onChange((current) => ({ ...current, matrix: null }))}>
                {t("matrix.remove")}
              </Button>
            ) : (
              <Button type="button" variant="secondary" size="sm" onClick={() => onChange((current) => ({ ...current, matrix: emptyMatrix() }))}>
                <Plus />
                <span>{t("matrix.declare")}</span>
              </Button>
            )}
          </div>
          {matrixBlock && section.matrix ? (
            <>
              {matrixVaries && <p className="text-caption text-danger">{t("countVariesError")}</p>}
              <BlockMatrixEditor
                key={`${section.id}-${count}`}
                block={{ ...matrixBlock, matrix: section.matrix, questionCount: count ?? 0 }}
                axes={axes}
                labels={labels}
                competencies={[]}
                onChange={(matrix) => onChange((current) => ({ ...current, matrix }))}
                available={availableQuery.data}
                availableState={availableState}
              />
              <DimensionTable title={t("matrix.gradeTitle")} rows={allocation.grades} label={labels.grade} />
              <DimensionTable title={t("matrix.difficultyTitle")} rows={allocation.difficulties} label={labels.difficulty} />
            </>
          ) : (
            <p className="text-body text-text-muted">{t("matrix.none")}</p>
          )}
        </div>

        <KnowledgeScopeField
          value={section.knowledgeScope}
          onChange={setScope}
          reference={reference}
          subjectCode={section.subjectCode}
          name={subjectName}
          everyChild={{ checked: section.knowledgeScope?.everyChildInScope ?? false, onChange: setEveryChild }}
        />

        <ReferenceDistributions entries={references} labels={labels} subjectName={subjectName} onUse={copyReference} onApply={applyReference} />

        <TopicQuotaTable
          id={section.id}
          quotas={topicQuotas}
          onChange={(next) => onChange((current) => ({ ...current, topicQuotas: next }))}
          total={count ?? 0}
          levelName={subjectName}
          subjectCode={section.subjectCode}
          reference={reference}
          axes={axes}
          labels={labels}
          serverError={topicError}
        />
      </section>

      <GroupList section={section} subjectName={subjectName} total={allocation.count} allocationRemainder={allocation.remainder} onSelectBlock={onSelectBlock} onAddBlock={onAddBlock} />
    </Card>
  );
}

/** Đã phân bổ (ở chỉ tiêu riêng của nhóm) và còn thiếu theo từng khối lớp hoặc độ khó của môn. */
function DimensionTable({ title, rows, label }: { title: string; rows: QuotaRow[] | null; label: (code: string) => string }) {
  const t = useTranslations("exams.structure.subject.matrix");
  if (!rows) return null;
  return (
    <div role="region" tabIndex={0} aria-label={title} className={cn("relative overflow-x-auto rounded-md border border-border", focusRing)}>
      <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
        <caption className="sr-only">{title}</caption>
        <thead>
          <tr className="text-left text-caption text-text-muted">
            <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{title}</th>
            <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">{t("quota")}</th>
            <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">{t("allocated")}</th>
            <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">{t("missing")}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.key}>
              <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text last-of-type:border-b-0">{label(row.key)}</th>
              <td className="border-b border-border px-2 py-2 text-right">{row.quota}</td>
              <td className="border-b border-border px-2 py-2 text-right">{row.allocated}</td>
              <td className={cn("border-b border-border px-3 py-2 text-right font-semibold", row.missing !== 0 && "text-warning")}>{row.missing}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Danh sách nhóm lấy câu của môn (khởi tạo từ mẫu): dạng trả lời, số câu, phạm vi nguồn; mở nhóm để sửa. */
function GroupList({
  section,
  subjectName,
  total,
  allocationRemainder,
  onSelectBlock,
  onAddBlock,
}: {
  section: ExamSection;
  subjectName: string;
  total: number | null;
  allocationRemainder: number | null;
  onSelectBlock: (blockId: string) => void;
  onAddBlock: () => void;
}) {
  const t = useTranslations("exams.structure.subject.groups");
  const tContent = useTranslations("content");
  const headingId = useId();
  const format = useFormatter();
  const rateText = (block: QuestionBlock) => {
    const rate = total === null ? null : ratePercent(block.questionCount ?? allocationRemainder ?? 0, total);
    return rate === null ? "—" : t("rate", { value: format.number(rate, { maximumFractionDigits: 1 }) });
  };
  const typeLabel = (type: QuestionType) => tContent(`questionType.${type}`);

  const typeText = (block: QuestionBlock) => {
    if (block.questionType === null) return t("typeOpen");
    if (block.questionType === "QUESTION_SET") {
      const child = groupAnswerType(block);
      return child ? t("typeSetOf", { type: typeLabel(block.questionType), child: typeLabel(child) }) : typeLabel(block.questionType);
    }
    return typeLabel(block.questionType);
  };
  const remainderGroups = remainderGroupCount(section.blocks);
  const countText = (block: QuestionBlock) => {
    if (block.questionCount === null) return remainderGroups > 1 ? t("countRemainderInvalid") : t("countRemainder", { count: allocationRemainder ?? 0 });
    if (isSetBlock(block) && block.setCount && block.setSize) {
      return t("countSets", { sets: block.setCount, size: block.setSize, total: block.questionCount });
    }
    return t("count", { count: block.questionCount });
  };
  const scopeText = (block: QuestionBlock) => {
    const source = effectiveScopeSource(block, section);
    if (source === "GROUP") return t("scopeOwn", { count: block.knowledgeScope?.nodeIds.length ?? 0 });
    return source === "SECTION" ? t("scopeSection") : t("scopeNone");
  };

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id={headingId} className="text-title font-semibold text-text">{t("title")}</h3>
        <Button type="button" variant="secondary" size="sm" aria-label={t("addAria", { subject: subjectName })} onClick={onAddBlock}>
          <Plus />
          <span>{t("add")}</span>
        </Button>
      </div>
      {remainderGroups > 1 && <p className="text-caption text-danger">{t("remainderConflict", { count: remainderGroups })}</p>}
      {section.blocks.length === 0 ? (
        <p className="text-body text-text-muted">{t("empty")}</p>
      ) : (
        <ScrollableTable label={t("caption", { subject: subjectName })} className="rounded-md border border-border">
          <table className="w-full border-separate border-spacing-0 text-body">
            <caption className="sr-only">{t("caption", { subject: subjectName })}</caption>
            <thead>
              <tr className="text-left text-caption text-text-muted">
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">{t("name")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("type")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("countHeader")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("rateHeader")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-2 py-2 font-semibold">{t("scope")}</th>
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold"><span className="sr-only">{t("actions")}</span></th>
              </tr>
            </thead>
            <tbody>
              {section.blocks.map((block) => (
                <tr key={block.id}>
                  <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">
                    {block.name || t("untitled")}
                  </th>
                  <td className="border-b border-border px-2 py-2 text-text">
                    <span className="inline-flex items-center gap-1.5">
                      {typeText(block)}
                      {block.answerTypeLocked && (
                        <>
                          <Lock aria-hidden className="size-3.5 text-text-muted" />
                          <span className="sr-only">{t("typeLocked")}</span>
                        </>
                      )}
                    </span>
                  </td>
                  <td className="border-b border-border px-2 py-2 whitespace-nowrap tabular-nums text-text">{countText(block)}</td>
                  <td className="border-b border-border px-2 py-2 whitespace-nowrap tabular-nums text-text">{rateText(block)}</td>
                  <td className="border-b border-border px-2 py-2 text-text-secondary">{scopeText(block)}</td>
                  <td className="border-b border-border px-3 py-1 text-right">
                    <Button type="button" variant="secondary" size="sm" aria-label={t("openAria", { name: block.name || t("untitled") })} onClick={() => onSelectBlock(block.id)}>
                      {t("open")}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </ScrollableTable>
      )}
    </section>
  );
}
