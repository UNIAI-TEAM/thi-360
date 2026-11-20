"use client";

import { Badge, Button, Card, cn, focusRing, FormField, IconButton, Input, Select, StatusChip } from "@thi360/ui";
import { CircleMinus, Lock, Plus } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId, useState, type ReactNode } from "react";
import { QUESTION_TYPES, type ContentReference, type QuestionType } from "@/features/content/types";
import type { AudioCountQuery } from "../api.http";
import { ratePercent } from "../allocation";
import { apportionByPercent, countsToPercents, emptyMatrix, isSetBlock, type MatrixAxes, sum } from "../matrix";
import { useAvailableQuestions, useSubjectAvailableQuestions } from "../queries";
import { referenceEntries, referenceToMatrix, type ReferenceEntry } from "../reference-distribution";
import { effectiveScopeSource, groupAnswerType, remainderGroupCount, sectionCaseCounts, subjectAllocation, type QuotaRow } from "../subject-matrix";
import type { AnswerQuotas, ExamQuota, ExamSection, KnowledgeScope, MatrixUnit, QuestionBlock } from "../types";
import { BlockMatrixEditor, type MatrixLabels } from "./block-matrix-editor";
import { BlockNumberInput } from "./block-number-input";
import { ChoiceCardGroup } from "./create-exam-fields";
import { newBlock, type SectionContext } from "./exam-structure-draft";
import { KnowledgeScopeField, MapidModeField } from "./knowledge-scope-field";
import { AllocationGap } from "./allocation-gap";
import { EditOverviewLink } from "./edit-overview-link";
import { LOCKED_FIELD } from "./locked-field";
import { AudioChip, blockAvailableInput, blockShare, sectionAudio } from "./block-row-metrics";
import { ScrollableTable } from "./scrollable-table";
import { ReferenceDistributions } from "./subject-allocation-extras";
import { useSubjectTotalChange } from "./subject-total-change";
import { useHsaExam, isHsaExam } from "../hsa-enforcement";
import { deletedGroupRules, groupCountScope, groupEffectiveRules, mapidViewMode } from "../mapid-rules";
import { MapidRulesTable, useMapidRuleText } from "./mapid-rules-table";
import { TopicQuotaTable, type TopicQuotaServerError } from "./topic-quota-table";

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
  /** Mã bài: đường dẫn "Sửa thông tin tổng quan" cạnh ô số câu chỉ đọc (UNI-2753). */
  examId: string;
  /** Mẫu khóa số câu từng phần, mục (H27): thêm dòng "Mẫu đã khóa số câu"; ô "Số câu của mục" chỉ đọc cho mọi bài. */
  templateLocked?: boolean;
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
  examId,
  templateLocked = false,
  sourceVerified = false,
  topicError = null,
  confirmedCount = null,
  onConfirmCount,
  examQuotas = [],
  onExamQuotas,
  examKind = null,
}: SubjectPanelProps) {
  const t = useTranslations("exams.structure.subject");
  const tGap = useTranslations("exams.structure.gap");
  const tContent = useTranslations("content");
  const { section, option } = context;
  // Môn HSA dùng ma trận có hai chế độ MAPID; môn khác, kỳ thi khác, và môn còn dữ liệu cũ không chuyển được giữ kiểu cũ.
  const hsaContext = useHsaExam();
  const mapidMode = hsaContext || isHsaExam(examKind) ? mapidViewMode(section) : null;
  const cases = sectionCaseCounts(section, option);
  const [caseIndex, setCaseIndex] = useState(0);
  const count = cases[Math.min(caseIndex, cases.length - 1)] ?? null;
  const varies = cases.length > 1;
  const allocation = subjectAllocation(section, axes, count);
  const typeLabel = (type: QuestionType) => tContent(`questionType.${type}`);

  const declaredTypes = section.answerQuotas?.items.map((item) => item.answerType) ?? [];
  // Nhóm câu đơn mở không lấy câu chùm, nên truy vấn nguồn của nó không kể dòng "Bộ câu hỏi".
  const quotaTypes = declaredTypes.filter((type) => type !== "QUESTION_SET");
  const availableQuery = useSubjectAvailableQuestions({
    subjectCode: section.subjectCode,
    groups: section.blocks.map(
      (block): AudioCountQuery => ({
        subjectCode: section.subjectCode,
        questionType: block.questionType,
        answerTypes: block.questionType === null ? quotaTypes : [],
        childAnswerType: block.childAnswerType,
        knowledgeScope: groupCountScope(block, section),
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
  const usedTypes = new Set(declaredTypes);
  // Dạng mà nhóm dùng nhưng môn chưa khai chỉ tiêu: hiện thành hàng riêng, đã phân bổ vượt chỉ tiêu 0 (R1-6).
  const undeclaredRows = allocation.answerTypes?.filter((row) => !usedTypes.has(row.key as QuestionType)) ?? [];

  const matrixBlock: QuestionBlock | null = section.matrix
    ? { ...newBlock(subjectName), id: section.id, questionCount: count ?? 0, matrix: section.matrix }
    : null;
  const matrixVaries = section.matrix?.unit === "COUNT" && varies;
  // Hai bảng "Theo khối lớp / Theo độ khó" (đã phân bổ ở nhóm, còn thiếu) chỉ có nghĩa khi một nhóm khai chỉ tiêu riêng; không thì
  // chỉ lặp lại chỉ tiêu ở trên, nên ẩn (AD-15).
  const groupQuotaDeclared = [...(allocation.grades ?? []), ...(allocation.difficulties ?? [])].some((row) => row.allocated > 0);

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
  const { notice: totalChangeNotice, countRef } = useSubjectTotalChange({ section, axes, labels, confirmedCount, onConfirmCount, onChange });
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
              <StatusChip tone={allocation.missing === 0 ? "success" : allocation.missing < 0 ? "danger" : "warning"}>
                {allocation.missing === 0
                  ? t("matches")
                  : allocation.missing > 0
                    ? tGap("under", { count: allocation.missing })
                    : tGap("over", { count: -allocation.missing })}
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
            hint={templateLocked ? t("countField.lockedHint") : t("countField.hint")}
          >
            {(controlProps) => (
              <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <Input {...controlProps} value={String(sectionCount)} readOnly aria-readonly="true" className={cn("w-24 tabular-nums", LOCKED_FIELD)} />
                {templateLocked && (
                  <span className="inline-flex items-center gap-1 text-label font-medium text-text-secondary">
                    <Lock aria-hidden className="size-3.5" />
                    {t("countField.locked")}
                  </span>
                )}
                <EditOverviewLink examId={examId} className="h-auto p-0" />
              </span>
            )}
          </FormField>
        </div>
      )}

      {totalChangeNotice}

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
                          <td className="border-b border-border px-2 py-2 text-right">
                            {row ? <AllocationGap missing={row.missing} /> : "—"}
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
                        <td className="border-b border-border px-2 py-2 text-right">
                          <AllocationGap missing={row.missing} />
                        </td>
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
                      <td className={cn("px-2 py-2 text-right", quotaMismatch && quotas.unit === "PERCENT" && "text-danger")}>
                        <output>
                          {quotas.unit === "PERCENT"
                            ? t("totalPercent", { total: quotaTotal })
                            : count === null
                              ? quotaTotal
                              : t("totalOf", { total: quotaTotal, count })}
                        </output>
                        {quotas.unit === "COUNT" && quotaMismatch && count !== null && (
                          <span className="block text-caption">
                            <AllocationGap missing={count - quotaTotal} />
                          </span>
                        )}
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
                  options={QUESTION_TYPES.filter((type) => !usedTypes.has(type)).map((type) => ({ value: type, label: typeLabel(type) }))}
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
              {groupQuotaDeclared && (
                <>
                  <DimensionTable title={t("matrix.gradeTitle")} rows={allocation.grades} label={labels.grade} />
                  <DimensionTable title={t("matrix.difficultyTitle")} rows={allocation.difficulties} label={labels.difficulty} />
                </>
              )}
            </>
          ) : (
            <p className="text-body text-text-muted">{t("matrix.none")}</p>
          )}
        </div>

        {mapidMode !== null ? (
          <>
            <MapidModeField
              mode={mapidMode}
              onModeChange={(mode) => onChange((current) => ({ ...current, mapidMode: mode }))}
              ruleCount={section.mapidRules?.length ?? 0}
              invalidCount={deletedGroupRules(section).length}
            />
            <ReferenceDistributions entries={references} labels={labels} subjectName={subjectName} onUse={copyReference} onApply={applyReference} />
            {mapidMode === "MATRIX" && (
              <MapidRulesTable
                section={section}
                groups={section.blocks}
                subjectName={subjectName}
                reference={reference}
                onChange={(rules) => onChange((current) => ({ ...current, mapidRules: rules }))}
              />
            )}
          </>
        ) : (
          <>
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
          </>
        )}
      </section>

      <GroupList
        section={section}
        subjectName={subjectName}
        partName={context.part.name}
        partTotal={context.part.questionCount}
        total={allocation.count}
        allocationRemainder={allocation.remainder}
        onSelectBlock={onSelectBlock}
        onAddBlock={onAddBlock}
        reference={reference}
        mapid={mapidMode === "MATRIX"}
      />
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
              <td className="border-b border-border px-3 py-2 text-right">
                <AllocationGap missing={row.missing} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Nguồn khả dụng của một dòng của phần có audio: cùng bộ lọc và cùng truy vấn với vùng sửa nhóm (R-130). */
function AvailableSource({ block, section }: { block: QuestionBlock; section: ExamSection }) {
  const t = useTranslations("exams.structure.subject.groups");
  const query = useAvailableQuestions(blockAvailableInput(block, section, section.subjectCode));
  if (query.isError) return <span className="text-text-muted">{t("availableError")}</span>;
  if (!query.data) return <span className="text-text-muted">{t("availableLoading")}</span>;
  return <>{t("availableCount", { count: query.data.total })}</>;
}

/**
 * Danh sách nhóm lấy câu của môn (khởi tạo từ mẫu): dạng trả lời, số câu, phạm vi nguồn; mở nhóm để sửa. Môn của phần có
 * điều kiện audio (bản vẽ `NhomAudio2B`) thêm nhãn audio, số lượng, tổng câu, tỷ lệ trong phần, nguồn khả dụng và dòng tổng.
 */
function GroupList({
  section,
  subjectName,
  partName,
  partTotal,
  total,
  allocationRemainder,
  onSelectBlock,
  onAddBlock,
  reference,
  mapid,
}: {
  section: ExamSection;
  subjectName: string;
  partName: string;
  /** Số câu của phần chứa môn: mẫu số của "Tỷ lệ trong phần", cùng số với vùng sửa nhóm. */
  partTotal: number | null;
  total: number | null;
  allocationRemainder: number | null;
  onSelectBlock: (blockId: string) => void;
  onAddBlock: () => void;
  reference: ContentReference;
  /** Môn ở chế độ Theo ma trận MAPID: cột "Phạm vi nguồn" hiện quy tắc có hiệu lực. */
  mapid: boolean;
}) {
  const ruleText = useMapidRuleText(reference);
  const t = useTranslations("exams.structure.subject.groups");
  const tContent = useTranslations("content");
  const tRows = useTranslations("exams.structure.blockForm.rows");
  const headingId = useId();
  const format = useFormatter();
  const audio = sectionAudio(section);
  const percentText = (rate: number | null) => (rate === null ? "—" : t("rate", { value: format.number(rate, { maximumFractionDigits: 1 }) }));
  const questionsOf = (block: QuestionBlock) => block.questionCount ?? allocationRemainder ?? 0;
  const rateText = (block: QuestionBlock) => percentText(audio ? blockShare(questionsOf(block), partTotal) : total === null ? null : ratePercent(questionsOf(block), total));
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
  /** Cột "Số lượng": cách lấy (số chùm × cỡ chùm hoặc số câu đơn); tổng câu của dòng ở cột "Tổng câu". */
  const quantityText = (block: QuestionBlock) => {
    if (block.questionCount === null) return countText(block);
    if (isSetBlock(block)) return block.setCount && block.setSize ? t("quantitySets", { sets: block.setCount, size: block.setSize }) : t("count", { count: block.questionCount });
    return t("quantitySingle", { count: block.questionCount });
  };
  const scopeText = (block: QuestionBlock) => {
    const source = effectiveScopeSource(block, section);
    if (source === "GROUP") return t("scopeOwn", { count: block.knowledgeScope?.nodeIds.length ?? 0 });
    return source === "SECTION" ? t("scopeSection") : t("scopeNone");
  };
  // Môn ở chế độ Theo ma trận MAPID: cột này liệt kê quy tắc có hiệu lực với nguồn của nhóm (phạm vi và chỉ tiêu cũ không còn dùng).
  const scopeCell = (block: QuestionBlock): ReactNode => {
    if (!mapid) return scopeText(block);
    const effective = groupEffectiveRules(section, block.id);
    if (effective.length === 0) return t("scopeNone");
    return (
      <ul data-testid={`mapid-group-scope-${block.id}`} className="flex flex-col gap-0.5">
        {effective.map((item) => (
          <li key={item.rule.id}>{ruleText.short(section, item)}</li>
        ))}
      </ul>
    );
  };
  const sumQuestions = section.blocks.reduce((sumSoFar, block) => sumSoFar + questionsOf(block), 0);
  const headCell = "border-b border-border bg-subtle px-2 py-2 font-semibold whitespace-nowrap";

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id={headingId} className="text-title font-semibold text-text">{t("title")}</h3>
        <Button type="button" variant="secondary" size="sm" aria-label={t("addAria", { subject: subjectName })} onClick={onAddBlock}>
          <Plus />
          <span>{t("add")}</span>
        </Button>
      </div>
      {audio && (
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-label font-semibold text-text tabular-nums">
            {t("partSummary", { part: partName, known: partTotal !== null && partTotal > 0 ? "yes" : "no", count: partTotal ?? 0 })}
          </p>
          <AudioChip audio={audio} />
          <span className="text-caption text-text-muted">{tRows("audioNote")}</span>
        </div>
      )}
      {remainderGroups > 1 && <p className="text-caption text-danger">{t("remainderConflict", { count: remainderGroups })}</p>}
      {section.blocks.length === 0 ? (
        <p className="text-body text-text-muted">{t("empty")}</p>
      ) : (
        <ScrollableTable label={t("caption", { subject: subjectName })} className="rounded-md border border-border">
          <table className="w-full border-separate border-spacing-0 text-body">
            <caption className="sr-only">{t("caption", { subject: subjectName })}</caption>
            <thead>
              <tr className="text-left text-caption text-text-muted">
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold whitespace-nowrap">{t("name")}</th>
                {mapid && <th scope="col" className={headCell}>{t("scope")}</th>}
                <th scope="col" className={headCell}>{t("type")}</th>
                {audio && <th scope="col" className={headCell}>{t("audioHeader")}</th>}
                <th scope="col" className={headCell}>{t(audio ? "quantityHeader" : "countHeader")}</th>
                {audio && <th scope="col" className={cn(headCell, "text-right")}>{t("totalHeader")}</th>}
                <th scope="col" className={headCell}>{t(audio ? "shareHeader" : "rateHeader")}</th>
                {audio && <th scope="col" className={headCell}>{t("availableHeader")}</th>}
                {!mapid && <th scope="col" className={headCell}>{t("scope")}</th>}
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold whitespace-nowrap"><span className="sr-only">{t("actions")}</span></th>
              </tr>
            </thead>
            <tbody>
              {section.blocks.map((block) => (
                <tr key={block.id}>
                  <th scope="row" className={cn("border-b border-border px-3 py-2 text-left font-medium text-text", !mapid && "whitespace-nowrap")}>
                    {block.name || t("untitled")}
                  </th>
                  {mapid && <td className="min-w-56 border-b border-border px-2 py-2 text-text-secondary">{scopeCell(block)}</td>}
                  <td className={cn("border-b border-border px-2 py-2 text-text", !mapid && "whitespace-nowrap")}>
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
                  {audio && (
                    <td className="border-b border-border px-2 py-2 whitespace-nowrap">
                      <AudioChip audio={audio} />
                    </td>
                  )}
                  <td className={cn("border-b border-border px-2 py-2 tabular-nums text-text", !mapid && "whitespace-nowrap")}>{audio ? quantityText(block) : countText(block)}</td>
                  {audio && <td className="border-b border-border px-2 py-2 text-right font-semibold whitespace-nowrap tabular-nums text-text">{format.number(questionsOf(block))}</td>}
                  <td className="border-b border-border px-2 py-2 whitespace-nowrap tabular-nums text-text">{rateText(block)}</td>
                  {audio && (
                    <td className="border-b border-border px-2 py-2 whitespace-nowrap tabular-nums text-text-secondary">
                      <AvailableSource block={block} section={section} />
                    </td>
                  )}
                  {!mapid && <td className="border-b border-border px-2 py-2 whitespace-nowrap text-text-secondary">{scopeCell(block)}</td>}
                  <td className="border-b border-border px-3 py-1 text-right">
                    <Button type="button" variant="secondary" size="sm" aria-label={t("openAria", { name: block.name || t("untitled") })} onClick={() => onSelectBlock(block.id)}>
                      {t("open")}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
            {audio && (
              <tfoot>
                <tr className="bg-subtle font-bold tabular-nums text-text">
                  <th scope="row" colSpan={4} className="px-3 py-2.5 text-left">{t("partTotal", { part: partName })}</th>
                  <td className="px-2 py-2.5 text-right">{format.number(sumQuestions)}</td>
                  <td className="px-2 py-2.5 whitespace-nowrap">{percentText(blockShare(sumQuestions, partTotal))}</td>
                  <td colSpan={3} />
                </tr>
              </tfoot>
            )}
          </table>
        </ScrollableTable>
      )}
    </section>
  );
}
