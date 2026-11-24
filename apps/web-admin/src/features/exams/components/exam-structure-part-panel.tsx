"use client";

import { Button, cn, focusRing, FormField, Input } from "@thi360/ui";
import { Lock, Plus, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import type { ComponentProps, ReactNode } from "react";
import { examTabHref } from "../editor-tabs";
import { countOf, type MatrixAxes, sectionsPoints, sum, type StructureIssue } from "../matrix";
import type { ExamPart, ExamSectionState, ExamSection, ExamTiming, StructureCombination } from "../types";
import { BlockNumberInput } from "./block-number-input";
import type { MatrixLabels } from "./block-matrix-editor";
import { CodedPartPanel } from "./coded-part-panel";
import { AllocationGap } from "./allocation-gap";
import { OptionalNumberInput } from "./create-exam-fields";
import { PartOptions, type PartOptionsProps } from "./part-options";
import { SubjectChoiceOptions } from "./subject-choice-options";
import { LOCKED_FIELD } from "./locked-field";
import type { StructureNames } from "./exam-structure-issues";
import { ExamName, richNodes } from "./exam-review-shared";
import { TopicQuotaElsewhereAlert } from "./topic-quota-elsewhere-alert";
import type { TopicQuotaServerError } from "./topic-quota-table";

interface PartPanelProps
  extends Pick<PartOptionsProps, "showEnglish" | "branchSubjects" | "onAddBranch" | "onConfirm"> {
  part: ExamPart;
  /** `SHARED`: phần không có ô thời lượng, bài dùng thời lượng chung `sharedMinutes`. */
  timing: ExamTiming;
  sharedMinutes: number;
  names: StructureNames;
  /** Mã của kỳ thi thuộc các phần theo mã (đọc từ Global List, chỉ xem). */
  structureCombinations: readonly StructureCombination[];
  axes: MatrixAxes;
  labels: MatrixLabels;
  /** Mã bài: đường dẫn "Sửa thông tin tổng quan" của các ô số câu chỉ đọc. */
  examId: string;
  templateLocked?: boolean;
  onAddSubject: (subjectCode: string) => void;
  issues: readonly StructureIssue[];
  issueText: (issue: StructureIssue) => string;
  onChange: (update: (part: ExamPart) => ExamPart) => void;
  onAddBlock: (optionId: string | null, section: ExamSection) => void;
  onToggleEnglish: (enabled: boolean) => void;
  onRemoveOption: (optionId: string) => void;
  onSelectBlock: (blockId: string) => void;
  sectionStates?: readonly ExamSectionState[] | null;
  /** Mở môn để soạn chỉ tiêu cấp môn và các nhóm. */
  onSelectSection: (sectionId: string) => void;
  onPoints: (points: number | null) => void;
  onChoose: (choose: number) => void;
  headerAction?: ReactNode;
  topicError?: TopicQuotaServerError;
  rowsLocked?: boolean;
  reviewMode?: boolean;
  sectionForm?: (sectionId: string) => "REVIEW" | "MATRIX" | "ROWS" | "DEFAULT";
}

export function ExamStructurePartPanel({
  part,
  timing,
  sharedMinutes,
  names,
  structureCombinations,
  axes,
  labels,
  examId,
  templateLocked = false,
  onAddSubject,
  issues,
  issueText,
  onChange,
  onAddBlock,
  onToggleEnglish,
  onRemoveOption,
  onSelectBlock,
  onSelectSection,
  sectionStates,
  onPoints,
  onChoose,
  showEnglish,
  branchSubjects,
  onAddBranch,
  onConfirm,
  headerAction,
  topicError = null,
  rowsLocked = false,
  reviewMode = false,
  sectionForm,
}: PartPanelProps) {
  const t = useTranslations("exams.structure.part");
  const partIssues = issues.filter((issue) => issue.code !== "BLOCK");
  const countInvalid = partIssues.some((issue) => issue.code === "PART_COUNT_NOT_POSITIVE");
  const durationInvalid = partIssues.some((issue) => issue.code === "PART_DURATION_NOT_POSITIVE");
  const shared = timing === "SHARED";
  const pointsInvalid = partIssues.some((issue) => issue.code === "PART_POINTS");
  const paperInvalid = partIssues.some((issue) => issue.code === "PART_PAPER_COUNT");
  const chooseInvalid = partIssues.some((issue) => issue.code === "PART_CHOOSE_INVALID");
  const blockPoints = part.mode === "FIXED" ? sectionsPoints(part.sections) : null;
  const onlySubjectChoice = part.mode === "CHOICE" && part.options.length === 1 && part.options[0]?.kind === "SUBJECT_CHOICE";

  return (
    <div className="flex flex-col gap-5 p-4 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border pb-4">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 className="flex items-center gap-2 text-h3 font-bold wrap-break-word text-text">
            <span aria-hidden className="h-5 w-1 shrink-0 rounded-full bg-primary-600" />
            <span data-exam-content>{part.name}</span>
          </h2>
          <p className="pl-3 text-caption text-text-muted">
            {part.mode === "CHOICE"
              ? t("choiceHint", {
                  count: part.questionCount,
                  minutes: part.durationMinutes ?? 0,
                  shared: shared ? "yes" : "no",
                })
              : t("fixedHint")}
          </p>
        </div>
        {headerAction}
      </div>

      <TopicQuotaElsewhereAlert error={topicError} />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <FormField label={t("name")} required error={part.name.trim() ? undefined : t("nameRequired")}>
          {(controlProps) => (
            <Input
              {...controlProps}
              value={part.name}
              autoComplete="off"
              maxLength={200}
              onChange={(event) => onChange((current) => ({ ...current, name: event.target.value }))}
            />
          )}
        </FormField>
        {shared ? (
          <div className="flex flex-col gap-1.5">
            <span className="text-field-label font-medium text-text-secondary">{t("duration")}</span>
            <p className="flex min-h-10 items-center text-body text-text">{t("sharedDuration", { minutes: sharedMinutes })}</p>
          </div>
        ) : (
          <FormField label={t("duration")} required error={durationInvalid ? t("durationInvalid") : undefined}>
            {(controlProps) => (
              <BlockNumberInput
                {...controlProps}
                value={part.durationMinutes ?? 0}
                onValueChange={(durationMinutes) => onChange((current) => ({ ...current, durationMinutes }))}
              />
            )}
          </FormField>
        )}
        <FormField
          label={t("points")}
          hint={blockPoints !== null ? t("pointsHintFixed", { points: blockPoints }) : t("pointsHint")}
          error={pointsInvalid ? t("pointsInvalid") : undefined}
        >
          {(controlProps) => <OptionalNumberInput {...controlProps} value={part.maxPoints ?? null} onValueChange={onPoints} />}
        </FormField>
      </div>
      {shared && <p className="-mt-2 text-caption text-text-muted">{t("sharedHint")}</p>}

      <div className="flex flex-col gap-3 rounded-md border border-border bg-subtle/25 p-3.5">
        <span className="text-caption font-semibold text-text-secondary">{t("step1MetaTitle")}</span>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <FormField label={t("order")} hint={t("orderHint")}>
            {(controlProps) => <Input {...controlProps} value={String(part.order)} readOnly aria-readonly="true" className={cn("w-20 tabular-nums", LOCKED_FIELD)} />}
          </FormField>
          <FormField
            label={t("questionCountOnPaper")}
            required
            hint={t("questionCountOnPaperHint")}
            error={paperInvalid ? t("paperInvalid") : undefined}
          >
            {(controlProps) => (
              <LockedCount {...controlProps} value={part.questionCountOnPaper} examId={examId} templateLocked={templateLocked} />
            )}
          </FormField>
          <FormField
            label={t("questionCount")}
            required
            hint={t("questionCountHint")}
            error={countInvalid ? t("countInvalid") : undefined}
          >
            {(controlProps) => (
              <LockedCount {...controlProps} value={part.questionCount} examId={examId} templateLocked={templateLocked} />
            )}
          </FormField>
        </div>
      </div>

      {!part.codeSource && (
        <section aria-label={t("participation.title")} className="flex flex-col gap-1.5">
          <h3 className="text-title font-semibold text-text">{t("participation.title")}</h3>
          {part.mode !== "CHOICE" ? (
            <p className="text-body text-text">{t("participation.all")}</p>
          ) : onlySubjectChoice ? (
            // Phương án duy nhất là chọn môn (HSA Khoa học): luật chọn k môn nằm ở thẻ phương án, không lặp ô k ở đây (R1-10).
            <p className="text-body text-text">{t("participation.subjectChoice")}</p>
          ) : (
            <div className="flex flex-wrap items-center gap-2 text-body text-text">
              <span>{t("participation.choosePrefix")}</span>
              <BlockNumberInput
                className="h-9 w-20"
                value={part.participation.choose ?? 1}
                aria-invalid={chooseInvalid ? true : undefined}
                aria-label={t("participation.chooseAria")}
                onValueChange={onChoose}
              />
              <span>{t("participation.chooseSuffix", { count: part.options.length })}</span>
            </div>
          )}
          {!onlySubjectChoice && <p className="text-caption text-text-muted">{t("participation.hint")}</p>}
        </section>
      )}

      {partIssues.length > 0 && (
        <ul className="flex flex-col gap-1 rounded-md border border-warning/40 bg-warning-bg px-3 py-2">
          {partIssues.map((issue, index) => (
            <li key={index} className="flex items-start gap-1.5 text-body text-warning">
              <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
              {issueText(issue)}
            </li>
          ))}
        </ul>
      )}

      {part.codeSource ? (
        // Phần theo mã (R-24, D12): luôn là bảng môn và mã, kể cả khi `participation.mode` là CHOICE (dữ liệu cũ, seed, di trú).
        <CodedPartPanel
          part={part}
          names={names}
          axes={axes}
          combinations={structureCombinations}
          sectionStates={sectionStates}
          onSelectSection={onSelectSection}
          onAddSubject={onAddSubject}
        />
      ) : part.mode === "FIXED" ? (
        <SectionsTable
          part={part}
          names={names}
          examId={examId}
          templateLocked={templateLocked}
          canAddBlock={(section) => !rowsLocked && !reviewMode && (!sectionForm || sectionForm(section.id) === "DEFAULT")}
          onAddBlock={onAddBlock}
          onSelectSection={onSelectSection}
        />
      ) : (
        <>
          {part.sections.length > 0 && (
            <SectionsTable
              part={part}
              names={names}
              common
              examId={examId}
              templateLocked={templateLocked}
              canAddBlock={(section) => !rowsLocked && !reviewMode && (!sectionForm || sectionForm(section.id) === "DEFAULT")}
              onAddBlock={onAddBlock}
              onSelectSection={onSelectSection}
            />
          )}
          {part.options
            .filter((option) => option.kind === "SUBJECT_CHOICE")
            .map((option) => (
              <SubjectChoiceOptions
                key={option.id}
                option={option}
                names={names}
                axes={axes}
                labels={labels}
                sectionStates={sectionStates}
                onSelectSection={onSelectSection}
              />
            ))}
          <PartOptions
            part={part}
            examId={examId}
            names={names}
            issues={issues}
            showEnglish={showEnglish}
            branchSubjects={branchSubjects}
            onToggleEnglish={onToggleEnglish}
            onAddBranch={onAddBranch}
            onConfirm={onConfirm}
            onRemove={onRemoveOption}
            onEditBlocks={onSelectBlock}
          />
        </>
      )}
    </div>
  );
}

/** Môn và số câu của phần cố định, hoặc phần chung của phần tự chọn (`common`: mọi học viên làm, cộng một lựa chọn). */
function SectionsTable({
  part,
  names,
  common = false,
  examId,
  templateLocked,
  canAddBlock,
  onAddBlock,
  onSelectSection,
}: {
  part: ExamPart;
  names: StructureNames;
  common?: boolean;
  examId: string;
  templateLocked: boolean;
  canAddBlock: (section: ExamSection) => boolean;
  onAddBlock: PartPanelProps["onAddBlock"];
  onSelectSection: PartPanelProps["onSelectSection"];
}) {
  const t = useTranslations("exams.structure.part");
  const tSummary = useTranslations("exams.structure.summary");
  const single = !common && part.sections.length === 1;
  const total = sum(part.sections.map(countOf));
  const title = common ? t("commonTitle") : t("sectionsTitle");
  const totalMismatch = !common && total !== part.questionCount;
  const anyCanAddBlock = part.sections.some(canAddBlock);
  return (
    <section className="flex flex-col gap-2" aria-label={title}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-title font-semibold text-text">{title}</h3>
        <Button asChild variant="link" size="sm">
          <Link href={examTabHref(examId, "general")}>{tSummary("editOverview")}</Link>
        </Button>
      </div>
      {single && <p className="text-caption text-text-muted">{t("singleSectionHint")}</p>}
      {common && <p className="text-caption text-text-muted">{t("commonHint")}</p>}
      <div role="region" tabIndex={0} aria-label={title} className={cn("relative overflow-x-auto rounded-md border border-border", focusRing)}>
        <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
          <caption className="sr-only">
            {richNodes(t, common ? "commonCaption" : "sectionsCaption", { name: <ExamName>{part.name}</ExamName> })}
          </caption>
          <thead>
            <tr className="text-left text-caption text-text-muted">
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 font-semibold">
                {t("subject")}
              </th>
              <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">
                {t("sectionCount")}
              </th>
              <th scope="col" className="border-b border-border bg-subtle px-2 py-2 text-right font-semibold">
                {t("allocated")}
              </th>
              {anyCanAddBlock && (
                <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">
                  {t("actions")}
                </th>
              )}
            </tr>
          </thead>
          <tbody>
            {part.sections.map((section) => {
              const subject = names.subject(section.subjectCode);
              const allocated = sum(section.blocks.map(countOf));
              return (
                <tr key={section.id}>
                  <th scope="row" className="border-b border-border px-3 py-2 text-left font-medium text-text">
                    <Button
                      type="button"
                      variant="link"
                      size="sm"
                      className="h-auto p-0 text-left font-medium"
                      aria-label={t("openSubject", { subject })}
                      onClick={() => onSelectSection(section.id)}
                    >
                      {subject}
                    </Button>
                    <span className="block text-caption font-normal text-text-muted">
                      {t("blockCount", { count: section.blocks.length })}
                    </span>
                  </th>
                  <td className="border-b border-border px-2 py-2 text-right">
                    {/* Số câu của môn đặt ở thông tin tổng quan (UNI-2753): chỉ đọc, đường dẫn sửa ở đầu bảng. */}
                    <span className="inline-flex items-center justify-end gap-1.5">
                      {templateLocked && <Lock aria-hidden className="size-3.5 text-text-muted" />}
                      <span>{section.questionCount ?? 0}</span>
                      {templateLocked && <span className="sr-only">{t("countLocked")}</span>}
                    </span>
                  </td>
                  <td className="border-b border-border px-2 py-2 text-right whitespace-nowrap">
                    {t("allocatedValue", { allocated, count: section.questionCount ?? 0 })}
                    {allocated !== (section.questionCount ?? 0) && (
                      <span className="block text-caption">
                        <AllocationGap missing={(section.questionCount ?? 0) - allocated} />
                      </span>
                    )}
                  </td>
                  {anyCanAddBlock && (
                    <td className="border-b border-border px-3 py-1 text-right">
                      {canAddBlock(section) && (
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          aria-label={t("addBlockTarget", { subject })}
                          onClick={() => onAddBlock(null, section)}
                        >
                          <Plus />
                          <span>{t("addBlock")}</span>
                        </Button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr className="font-semibold text-text">
              <th scope="row" className="px-3 py-2 text-left">
                {t("total")}
              </th>
              <td className="px-2 py-2 text-right">
                <output>
                  {common
                    ? t("commonTotal", { count: total })
                    : t("allocatedValue", { allocated: total, count: part.questionCount })}
                </output>
                {totalMismatch && (
                  <span className="block text-caption">
                    <AllocationGap missing={part.questionCount - total} />
                  </span>
                )}
              </td>
              <td colSpan={anyCanAddBlock ? 2 : 1} />
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  );
}

function LockedCount({
  value,
  examId,
  templateLocked,
  ...controlProps
}: { value: number; examId: string; templateLocked: boolean } & Omit<ComponentProps<typeof Input>, "value">) {
  const t = useTranslations("exams.structure.part");
  const tSummary = useTranslations("exams.structure.summary");
  return (
    <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
      <Input {...controlProps} value={String(value)} readOnly aria-readonly="true" className={cn("w-24 tabular-nums", LOCKED_FIELD)} />
      {templateLocked && (
        <span className="inline-flex items-center gap-1 text-label font-medium text-text-secondary">
          <Lock aria-hidden className="size-3.5" />
          {t("countLocked")}
        </span>
      )}
      <Button asChild variant="link" size="sm" className="h-auto p-0">
        <Link href={examTabHref(examId, "general")}>{tSummary("editOverview")}</Link>
      </Button>
    </span>
  );
}
