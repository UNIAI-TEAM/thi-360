"use client";

import { Button, Card, cn, focusRing, FormField, Input } from "@thi360/ui";
import { Plus, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import type { CombinationOption } from "@/features/content/types";
import { countOf, sectionsPoints, sum, type StructureIssue } from "../matrix";
import type { ExamPart, ExamSectionState, ExamSection, ExamTiming } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { CombinationOptions, type CombinationOptionsProps } from "./combination-options";
import { OptionalNumberInput } from "./create-exam-fields";
import { SubjectChoiceOptions } from "./subject-choice-options";
import type { StructureNames } from "./exam-structure-issues";

export interface PartPanelProps
  extends Pick<
    CombinationOptionsProps,
    "showEnglish" | "branchSubjects" | "onAddBranch" | "onConfirm"
  > {
  part: ExamPart;
  /** `SHARED`: phần không có ô thời lượng, bài dùng thời lượng chung `sharedMinutes`. */
  timing: ExamTiming;
  sharedMinutes: number;
  names: StructureNames;
  combinations: readonly CombinationOption[];
  issues: readonly StructureIssue[];
  issueText: (issue: StructureIssue) => string;
  onChange: (update: (part: ExamPart) => ExamPart) => void;
  onQuestionCount: (questionCount: number) => void;
  onSectionCount: (optionId: string | null, sectionId: string, questionCount: number) => void;
  onAddBlock: (optionId: string | null, section: ExamSection) => void;
  onToggleEnglish: (enabled: boolean) => void;
  onAttachCombination: (combination: CombinationOption) => void;
  onRemoveOption: (optionId: string) => void;
  onSelectBlock: (blockId: string) => void;
  /** Trạng thái kiểm tra theo môn (H2); `null` hoặc bỏ trống: không hiện cột trạng thái. */
  sectionStates?: readonly ExamSectionState[] | null;
  /** Mở môn để soạn chỉ tiêu cấp môn và các nhóm. */
  onSelectSection: (sectionId: string) => void;
  onPoints: (points: number | null) => void;
  onOnPaper: (questionCountOnPaper: number) => void;
  onChoose: (choose: number) => void;
}

/**
 * Thông tin một phần thi (UniNote PBI-04 mục 5.2): tên, thứ tự, số câu, thời lượng. Phần cố định: bảng môn và số câu
 * của từng môn; phần tự chọn: nhánh Tiếng Anh và tổ hợp (bản vẽ `BaiThiToHop`).
 */
export function ExamStructurePartPanel({
  part,
  timing,
  sharedMinutes,
  names,
  combinations,
  issues,
  issueText,
  onChange,
  onQuestionCount,
  onSectionCount,
  onAddBlock,
  onToggleEnglish,
  onAttachCombination,
  onRemoveOption,
  onSelectBlock,
  onSelectSection,
  sectionStates,
  onPoints,
  onOnPaper,
  onChoose,
  showEnglish,
  branchSubjects,
  onAddBranch,
  onConfirm,
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
    <Card padding="md" className="flex flex-col gap-5 sm:p-6">
      <div className="flex flex-col gap-0.5">
        <h2 className="text-h3 font-bold wrap-break-word text-text">{part.name}</h2>
        <p className="text-caption text-text-muted">
          {part.mode === "CHOICE"
            ? t("choiceHint", {
                count: part.questionCount,
                minutes: part.durationMinutes ?? 0,
                shared: shared ? "yes" : "no",
              })
            : t("fixedHint")}
        </p>
      </div>

      <div className="grid grid-cols-[repeat(auto-fill,minmax(min(11rem,100%),1fr))] gap-4">
        <FormField label={t("name")} required error={part.name.trim() ? undefined : t("nameRequired")} className="col-span-full sm:col-span-2">
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
        <FormField label={t("order")} hint={t("orderHint")}>
          {(controlProps) => <Input {...controlProps} value={String(part.order)} readOnly className="tabular-nums" />}
        </FormField>
        <FormField
          label={t("questionCountOnPaper")}
          required
          hint={t("questionCountOnPaperHint")}
          error={paperInvalid ? t("paperInvalid") : undefined}
        >
          {(controlProps) => (
            <BlockNumberInput {...controlProps} value={part.questionCountOnPaper} onValueChange={onOnPaper} />
          )}
        </FormField>
        <FormField
          label={t("questionCount")}
          required
          hint={t("questionCountHint")}
          error={countInvalid ? t("countInvalid") : undefined}
        >
          {(controlProps) => (
            <BlockNumberInput {...controlProps} value={part.questionCount} onValueChange={onQuestionCount} />
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
      {shared && <p className="text-caption text-text-muted">{t("sharedHint")}</p>}

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

      {part.mode === "FIXED" ? (
        <SectionsTable
          part={part}
          names={names}
          onSectionCount={onSectionCount}
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
              onSectionCount={onSectionCount}
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
                sectionStates={sectionStates}
                onSelectSection={onSelectSection}
              />
            ))}
          <CombinationOptions
            part={part}
            combinations={combinations}
            names={names}
            issues={issues}
            showEnglish={showEnglish}
            branchSubjects={branchSubjects}
            onToggleEnglish={onToggleEnglish}
            onAttach={onAttachCombination}
            onAddBranch={onAddBranch}
            onConfirm={onConfirm}
            onRemove={onRemoveOption}
            onSectionCount={(optionId, sectionId, value) => onSectionCount(optionId, sectionId, value)}
            onEditBlocks={onSelectBlock}
          />
        </>
      )}
    </Card>
  );
}

/** Môn và số câu của phần cố định, hoặc phần chung của phần tự chọn (`common`: mọi học viên làm, cộng một lựa chọn). */
function SectionsTable({
  part,
  names,
  common = false,
  onSectionCount,
  onAddBlock,
  onSelectSection,
}: {
  part: ExamPart;
  names: StructureNames;
  common?: boolean;
  onSectionCount: PartPanelProps["onSectionCount"];
  onAddBlock: PartPanelProps["onAddBlock"];
  onSelectSection: PartPanelProps["onSelectSection"];
}) {
  const t = useTranslations("exams.structure.part");
  const single = !common && part.sections.length === 1;
  const total = sum(part.sections.map(countOf));
  const title = common ? t("commonTitle") : t("sectionsTitle");
  const totalMismatch = !common && total !== part.questionCount;
  return (
    <section className="flex flex-col gap-2" aria-label={title}>
      <h3 className="text-title font-semibold text-text">{title}</h3>
      {single && <p className="text-caption text-text-muted">{t("singleSectionHint")}</p>}
      {common && <p className="text-caption text-text-muted">{t("commonHint")}</p>}
      <div role="region" tabIndex={0} aria-label={title} className={cn("overflow-x-auto rounded-md border border-border", focusRing)}>
        <table className="w-full border-separate border-spacing-0 text-body tabular-nums">
          <caption className="sr-only">
            {common ? t("commonCaption", { name: part.name }) : t("sectionsCaption", { name: part.name })}
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
              <th scope="col" className="border-b border-border bg-subtle px-3 py-2 text-right font-semibold">
                {t("actions")}
              </th>
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
                    {single ? (
                      <span>{section.questionCount ?? 0}</span>
                    ) : (
                      <BlockNumberInput
                        className="ml-auto h-9 w-20"
                        value={section.questionCount ?? 0}
                        onValueChange={(value) => onSectionCount(null, section.id, value)}
                        aria-label={t("sectionCountAria", { subject })}
                      />
                    )}
                  </td>
                  <td
                    className={cn(
                      "border-b border-border px-2 py-2 text-right whitespace-nowrap",
                      allocated !== section.questionCount && "font-semibold text-danger",
                    )}
                  >
                    {t("allocatedValue", { allocated, count: section.questionCount ?? 0 })}
                  </td>
                  <td className="border-b border-border px-3 py-1 text-right">
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
                  </td>
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr className="font-semibold text-text">
              <th scope="row" className="px-3 py-2 text-left">
                {t("total")}
              </th>
              <td className={cn("px-2 py-2 text-right", totalMismatch && "text-danger")}>
                <output>
                  {common
                    ? t("commonTotal", { count: total })
                    : t("allocatedValue", { allocated: total, count: part.questionCount })}
                </output>
              </td>
              <td colSpan={2} />
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  );
}
