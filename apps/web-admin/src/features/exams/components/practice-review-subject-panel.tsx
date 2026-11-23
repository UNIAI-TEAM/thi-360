"use client";

import { Button, Card, cn, focusRing, FormField, Input, Select } from "@thi360/ui";
import { ChevronRight, Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { type ReactNode, useState } from "react";
import { QUESTION_TYPES, type ContentReference, type QuestionType } from "@/features/content/types";
import { applyTopicTotal } from "../allocation";
import { examTabHref } from "../editor-tabs";
import { emptyMatrix, isSetBlock, type MatrixAxes } from "../matrix";
import { useSubjectAvailableQuestions } from "../queries";
import { effectiveScope, subjectAudioGroups } from "../subject-matrix";
import type { ExamSection, KnowledgeScope, QuestionBlock } from "../types";
import { applyBlockPatch, resizeSets } from "./block-form";
import { BlockMatrixEditor, type MatrixLabels } from "./block-matrix-editor";
import { BlockNumberInput } from "./block-number-input";
import type { SectionContext } from "./exam-structure-draft";
import { LOCKED_FIELD } from "./locked-field";
import { TopicQuotaElsewhereAlert } from "./topic-quota-elsewhere-alert";
import type { TopicQuotaServerError } from "./topic-quota-table";
import { KnowledgeScopeField } from "./knowledge-scope-field";

/** Giá trị của ô dạng câu "Bất kỳ" (nhóm lấy theo chỉ tiêu dạng trả lời của môn, `questionType = null`). */
const ANY_TYPE = "__ANY__";

interface PracticeReviewSubjectPanelProps {
  context: SectionContext;
  subjectName: string;
  axes: MatrixAxes;
  labels: MatrixLabels;
  reference: ContentReference;
  onChange: (update: (section: ExamSection) => ExamSection) => void;
  /** Số câu bước 1 đặt cho bài ôn: ô Số lượng chỉ đọc, báo lệch khi nhóm khác số này. */
  step1Count: number;
  /** Mã bài: đường dẫn "Sửa thông tin tổng quan" cạnh ô Số lượng chỉ đọc (UNI-2753). */
  examId: string;
  headerAction?: ReactNode;
  /** Lỗi 422 của chỉ tiêu chủ đề (H20): màn này không có bảng chỉ tiêu, nên báo ngay trong panel. */
  topicError?: TopicQuotaServerError;
  /** Thời lượng của bài (phút) và thang điểm (`null`: bước 1 không đặt), chỉ để xem: sửa ở thông tin tổng quan. */
  minutes: number;
  maxScore: number | null;
}

/**
 * Bài ôn (R-22, R-10.1, bản vẽ `BaiOn2B`): màn rút gọn của bài có một phần, một môn, một nhóm. Số câu, thời lượng và thang điểm lấy từ
 * bước 1 và chỉ đọc ở đây (sửa ở thông tin tổng quan). Phạm vi kiến thức, dạng câu, phân bố khối lớp, độ khó và câu chùm nằm
 * trong một vùng đóng "Tùy chọn thêm", không bắt buộc: để mặc định vẫn lưu và kiểm tra được.
 */
export function PracticeReviewSubjectPanel({
  context,
  subjectName,
  axes,
  labels,
  reference,
  onChange,
  step1Count,
  examId,
  minutes,
  maxScore,
  headerAction,
  topicError = null,
}: PracticeReviewSubjectPanelProps) {
  const t = useTranslations("exams.reviewExam");
  const tSummary = useTranslations("exams.structure.summary");
  const tContent = useTranslations("content");
  const { section } = context;
  const block: QuestionBlock | undefined = section.blocks[0];
  const quotaTypes = section.answerQuotas?.items.map((item) => item.answerType) ?? [];
  const availableQuery = useSubjectAvailableQuestions({
    subjectCode: section.subjectCode,
    groups: subjectAudioGroups(section, quotaTypes, effectiveScope),
  });
  const availableState = availableQuery.isError ? "error" : availableQuery.data ? "ready" : "loading";
  const [startOpen] = useState(() => (block ? isSetBlock(block) : false));
  if (!block) return null;

  const count = block.questionCount ?? 0;
  const isSet = isSetBlock(block);
  const updateBlock = (patch: Partial<QuestionBlock>) =>
    onChange((current) => ({ ...current, blocks: current.blocks.map((item, index) => (index === 0 ? applyBlockPatch(item, patch) : item)) }));
  const retotal = (current: ExamSection, total: number) =>
    current.topicQuotas?.some((quota) => quota.percent !== undefined && quota.percent !== null)
      ? { topicQuotas: applyTopicTotal(current.topicQuotas, total) }
      : {};
  const setScope = (knowledgeScope: KnowledgeScope | null) => onChange((current) => ({ ...current, knowledgeScope }));
  const setSets = (setCount: number, setSize: number) => {
    const { patch } = resizeSets(block, setCount, setSize);
    onChange((current) => ({
      ...current,
      ...retotal(current, setCount * setSize),
      questionCount: setCount * setSize,
      blocks: current.blocks.map((item, index) => (index === 0 ? applyBlockPatch(item, patch) : item)),
    }));
  };
  const typeOptions = [
    { value: ANY_TYPE, label: t("typeAny") },
    ...QUESTION_TYPES.filter((type) => type !== "QUESTION_SET" && type !== "CLOZE").map((type) => ({ value: type, label: tContent(`questionType.${type}`) })),
  ];
  const mismatch = count !== step1Count;
  const applyStep1Count = () =>
    onChange((current) => ({
      ...current,
      ...retotal(current, step1Count),
      questionCount: step1Count,
      blocks: current.blocks.map((item, index) => (index === 0 ? applyBlockPatch(item, { questionCount: step1Count }) : item)),
    }));

  return (
    <Card padding="md" className="flex flex-col gap-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 className="text-h3 font-bold wrap-break-word text-text">{t("title", { subject: subjectName, count })}</h2>
          <p className="text-caption text-text-muted">{t("hint")}</p>
        </div>
        {headerAction}
      </div>

      <TopicQuotaElsewhereAlert error={topicError} />

      <section aria-label={t("step1Title")} className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <FormField label={t("count")} hint={t("countHint")} error={mismatch ? t("countMismatch", { step1: step1Count, count }) : undefined}>
          {(controlProps) => (
            <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <Input {...controlProps} value={String(count)} readOnly aria-readonly="true" className={cn("w-24 tabular-nums", LOCKED_FIELD)} />
              <Button asChild variant="link" size="sm" className="h-auto p-0">
                <Link href={examTabHref(examId, "general")}>{tSummary("editOverview")}</Link>
              </Button>
              {mismatch && !isSet && (
                <Button type="button" variant="secondary" size="sm" onClick={applyStep1Count}>
                  {t("useStep1Count")}
                </Button>
              )}
            </span>
          )}
        </FormField>
        <FormField label={t("duration")}>
          {(controlProps) => <Input {...controlProps} value={String(minutes)} readOnly aria-readonly="true" className={cn("w-24 tabular-nums", LOCKED_FIELD)} />}
        </FormField>
        {maxScore !== null && (
          <FormField label={t("scale")}>
            {(controlProps) => <Input {...controlProps} value={String(maxScore)} readOnly aria-readonly="true" className={cn("w-24 tabular-nums", LOCKED_FIELD)} />}
          </FormField>
        )}
      </section>

      <details className="group rounded-md border border-border" open={startOpen}>
        <summary className={cn("flex cursor-pointer list-none items-center gap-2 px-4 py-3 text-label font-semibold text-text", focusRing)}>
          <ChevronRight aria-hidden className="size-4 transition-transform group-open:rotate-90 motion-reduce:transition-none" />
          {t("more")}
        </summary>
        <div className="flex flex-col gap-5 border-t border-border p-4">
          <KnowledgeScopeField
            value={section.knowledgeScope}
            onChange={setScope}
            reference={reference}
            subjectCode={section.subjectCode}
            name={subjectName}
          />

          <FormField label={t("type")} className="sm:max-w-sm">
            {(controlProps) => (
              <Select
                {...controlProps}
                value={isSet ? ANY_TYPE : (block.questionType ?? ANY_TYPE)}
                disabled={isSet}
                options={typeOptions}
                onValueChange={(value) => updateBlock({ questionType: value === ANY_TYPE ? null : (value as QuestionType) })}
              />
            )}
          </FormField>

          <details className="group rounded-md border border-border">
            <summary className={cn("flex cursor-pointer list-none items-center gap-2 px-4 py-3 text-label font-semibold text-text", focusRing)}>
              <ChevronRight aria-hidden className="size-4 transition-transform group-open:rotate-90 motion-reduce:transition-none" />
              {t("distribution")}
              <span className="text-caption font-normal text-text-muted">{section.matrix ? t("set") : t("unset")}</span>
            </summary>
            <div className="flex flex-col gap-3 border-t border-border p-4">
              <p className="text-caption text-text-muted">{t("distributionHint")}</p>
              {section.matrix ? (
                <>
                  <BlockMatrixEditor
                    block={{ ...block, matrix: section.matrix, questionCount: count }}
                    axes={axes}
                    labels={labels}
                    competencies={[]}
                    onChange={(matrix) => onChange((current) => ({ ...current, matrix }))}
                    available={availableQuery.data}
                    availableState={availableState}
                  />
                  <Button type="button" variant="secondary" size="sm" className="w-fit" onClick={() => onChange((current) => ({ ...current, matrix: null }))}>
                    {t("removeDistribution")}
                  </Button>
                </>
              ) : (
                <Button type="button" variant="secondary" size="sm" className="w-fit" onClick={() => onChange((current) => ({ ...current, matrix: emptyMatrix() }))}>
                  <Plus />
                  <span>{t("declareDistribution")}</span>
                </Button>
              )}
            </div>
          </details>

          <details className="group rounded-md border border-border" open={isSet}>
            <summary className={cn("flex cursor-pointer list-none items-center gap-2 px-4 py-3 text-label font-semibold text-text", focusRing)}>
              <ChevronRight aria-hidden className="size-4 transition-transform group-open:rotate-90 motion-reduce:transition-none" />
              {t("sets")}
              <span className="text-caption font-normal text-text-muted">{isSet ? t("setsOn") : t("setsOff")}</span>
            </summary>
            <div className={cn("flex flex-col gap-3 border-t border-border p-4")}>
              <p className="text-caption text-text-muted">{t("setsHint")}</p>
              {isSet ? (
                <div className="flex flex-wrap items-end gap-4">
                  <FormField label={t("setCount")}>
                    {(controlProps) => (
                      <BlockNumberInput
                        {...controlProps}
                        value={block.setCount ?? 1}
                        onValueChange={(next) => setSets(next, block.setSize ?? 1)}
                      />
                    )}
                  </FormField>
                  <FormField label={t("setSize")}>
                    {(controlProps) => (
                      <BlockNumberInput
                        {...controlProps}
                        value={block.setSize ?? 1}
                        onValueChange={(next) => setSets(block.setCount ?? 1, next)}
                      />
                    )}
                  </FormField>
                  <Button type="button" variant="secondary" size="sm" onClick={() => updateBlock({ questionType: null, setCount: null, setSize: null, childAnswerType: null })}>
                    {t("removeSets")}
                  </Button>
                </div>
              ) : (
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  className="w-fit"
                  onClick={() => updateBlock({ questionType: "QUESTION_SET", setCount: 1, setSize: Math.max(count, 1), questionCount: Math.max(count, 1) })}
                >
                  <Plus />
                  <span>{t("declareSets")}</span>
                </Button>
              )}
            </div>
          </details>
        </div>
      </details>
    </Card>
  );
}
