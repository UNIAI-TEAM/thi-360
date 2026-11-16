"use client";

import { Button, Card, cn, focusRing, FormField, Input } from "@thi360/ui";
import { Plus, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import type { CombinationOption } from "@/features/content/types";
import { countOf, sectionsPoints, sum, type StructureIssue } from "../matrix";
import type { ExamPart, ExamSection, ExamTiming } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { CombinationOptions, type CombinationOptionsProps } from "./combination-options";
import type { StructureNames } from "./exam-structure-issues";

export interface PartPanelProps
  extends Pick<
    CombinationOptionsProps,
    "showEnglish" | "subjectPool" | "defaultCombinationSize" | "branchSubjects" | "onGenerate" | "onAddBranch" | "onConfirm"
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
  onPoints: (points: number) => void;
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
  onPoints,
  showEnglish,
  subjectPool,
  defaultCombinationSize,
  branchSubjects,
  onGenerate,
  onAddBranch,
  onConfirm,
}: PartPanelProps) {
  const t = useTranslations("exams.structure.part");
  const partIssues = issues.filter((issue) => issue.code !== "BLOCK");
  const countInvalid = partIssues.some((issue) => issue.code === "PART_COUNT_NOT_POSITIVE");
  const durationInvalid = partIssues.some((issue) => issue.code === "PART_DURATION_NOT_POSITIVE");
  const shared = timing === "SHARED";
  const pointsInvalid = partIssues.some((issue) => issue.code === "PART_POINTS");
  const blockPoints = part.mode === "FIXED" ? sectionsPoints(part.sections) : null;

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
        <FormField label={t("questionCount")} required error={countInvalid ? t("countInvalid") : undefined}>
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
        {part.maxPoints !== null && part.maxPoints !== undefined && (
          <FormField
            label={t("points")}
            required
            hint={blockPoints !== null ? t("pointsHintFixed", { points: blockPoints }) : t("pointsHint")}
            error={pointsInvalid ? t("pointsInvalid") : undefined}
          >
            {(controlProps) => (
              <BlockNumberInput {...controlProps} allowDecimal value={part.maxPoints ?? 0} onValueChange={onPoints} />
            )}
          </FormField>
        )}
      </div>
      {shared && <p className="text-caption text-text-muted">{t("sharedHint")}</p>}

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
        <SectionsTable part={part} names={names} onSectionCount={onSectionCount} onAddBlock={onAddBlock} />
      ) : (
        <>
          {part.sections.length > 0 && (
            <SectionsTable part={part} names={names} common onSectionCount={onSectionCount} onAddBlock={onAddBlock} />
          )}
          <CombinationOptions
            part={part}
            combinations={combinations}
            names={names}
            issues={issues}
            showEnglish={showEnglish}
            subjectPool={subjectPool}
            defaultCombinationSize={defaultCombinationSize}
            branchSubjects={branchSubjects}
            onToggleEnglish={onToggleEnglish}
            onAttach={onAttachCombination}
            onGenerate={onGenerate}
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
}: {
  part: ExamPart;
  names: StructureNames;
  common?: boolean;
  onSectionCount: PartPanelProps["onSectionCount"];
  onAddBlock: PartPanelProps["onAddBlock"];
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
                    {subject}
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
