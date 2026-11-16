"use client";

import { Button, Checkbox, cn, FormField, IconButton, Input, Select, StatusChip } from "@thi360/ui";
import { ArrowDown, ArrowUp, Info, Link2, Plus, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { Controller, type Control } from "react-hook-form";
import type { CatalogOption, CombinationOption } from "@/features/content/types";
import {
  choicePathCount,
  commonCount,
  type CreateBranchValues,
  type CreateCombinationValues,
  type CreateExamFormValues,
  type CreatePartValues,
  type CreateSectionValues,
  defaultSubjectCounts,
  optionTarget,
  PART_NAME_MAX,
} from "../create-exam-schema";
import { countOf, sum } from "../matrix";
import type { ExamScope, ExamTemplateBlock, ExamTiming } from "../types";
import { BlockNumberInput } from "./block-number-input";
import { CombinationAttachDialog } from "./combination-options";
import { OptionalNumberInput } from "./create-exam-fields";

/** Lỗi của một trường theo đường dẫn (`parts.0.name`); `undefined` khi trường hợp lệ. */
export type FieldErrorLookup = (path: string) => string | undefined;

function safeNumber(value: number): number {
  return Number.isFinite(value) && value > 0 ? value : 0;
}

/** Lưới hàng của một phần theo số cột số (số câu, thời lượng nếu tính theo phần, điểm nếu chia điểm theo phần). */
const PART_GRID: Record<1 | 2 | 3, string> = {
  1: "@3xl:grid-cols-[minmax(0,2fr)_minmax(0,2fr)_minmax(0,1fr)]",
  2: "@3xl:grid-cols-[minmax(0,2fr)_minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)]",
  3: "@3xl:grid-cols-[minmax(0,2fr)_minmax(0,2fr)_repeat(3,minmax(0,1fr))]",
};

/**
 * Khối gợi ý của mẫu (dạng câu, số câu): chỉ để đọc ở trang Tạo bài thi, nạp vào bài khi tạo; sửa ở Cấu trúc và ma trận.
 * Khối Tự luận ghi "chấm tay".
 */
export function TemplateBlocksNote({ blocks }: { blocks: readonly ExamTemplateBlock[] }) {
  const t = useTranslations("exams.create.blocks");
  if (blocks.length === 0) return null;
  return (
    <div className="flex flex-col gap-1 text-caption text-text-muted">
      <p className="font-semibold text-text-secondary">{t("title", { count: sum(blocks.map(countOf)) })}</p>
      <ul className="flex flex-wrap gap-x-3 gap-y-1">
        {blocks.map((block, index) => (
          <li key={`${block.name}-${index}`} className="tabular-nums">
            {t("item", { name: block.name, count: block.questionCount ?? 0 })}
            {block.questionType === "ESSAY" && ` · ${t("essay")}`}
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * Số câu từng môn của phần nhiều môn (BCA Phần 2) hoặc của phần chung (THPTQG Tin học): mỗi môn một ô số; phần nhiều
 * môn so tổng với số câu của phần.
 */
function SectionCountsEditor({
  legend,
  path,
  sections,
  target,
  subjectName,
  error,
  onChange,
}: {
  legend: string;
  path: string;
  sections: readonly CreateSectionValues[];
  /** Tổng phải bằng (phần nhiều môn); `null`: không so (phần chung). */
  target: number | null;
  subjectName: (code: string) => string;
  error?: string;
  onChange: (next: CreateSectionValues[]) => void;
}) {
  const t = useTranslations("exams.create.choice");
  const idBase = useId();
  const actual = sum(sections.map((section) => safeNumber(section.questionCount)));
  const ok = !error;
  const sumId = `${idBase}-sum`;
  const errorId = `${idBase}-error`;
  return (
    <fieldset data-field={path} className="flex min-w-0 flex-col gap-2">
      <legend className="mb-1 text-caption font-semibold text-text-secondary">{legend}</legend>
      <div
        className={cn(
          "flex flex-wrap items-end gap-x-3.5 gap-y-2.5 rounded-lg border bg-surface py-2.5 pr-2 pl-3.5",
          ok ? "border-border" : "border-danger",
        )}
      >
        {sections.map((section, index) => {
          const inputId = `${idBase}-${index}`;
          return (
            <div key={`${section.subjectCode}-${index}`} className="flex w-28 flex-col gap-1">
              <label htmlFor={inputId} className="text-caption text-text-muted">
                {t("subjectLabel", { index: index + 1, subject: subjectName(section.subjectCode) })}
              </label>
              <BlockNumberInput
                id={inputId}
                value={section.questionCount ?? 0}
                onValueChange={(value) =>
                  onChange(sections.map((item, position) => (position === index ? { ...item, questionCount: value } : item)))
                }
                aria-invalid={ok ? undefined : true}
                aria-describedby={[sumId, error ? errorId : null].filter(Boolean).join(" ")}
                className="h-10"
              />
            </div>
          );
        })}
        <output
          id={sumId}
          className={cn(
            "flex h-10 items-center rounded-md px-2.5 text-body font-semibold tabular-nums",
            ok ? "bg-primary-50 text-primary-text" : "bg-danger-bg text-danger",
          )}
        >
          {target === null ? t("commonValue", { count: actual }) : t("sumValue", { actual, expected: target })}
        </output>
        {error && (
          <p id={errorId} className="w-full text-caption text-danger">
            {error}
          </p>
        )}
      </div>
    </fieldset>
  );
}

/**
 * Cấu trúc khởi tạo của trang Tạo bài thi (bản vẽ TaoBaiThi mục 2; UniNote PBI-04 mục 5.1–5.2; spec 7 kỳ thi): phần theo
 * thứ tự với tên, môn cố định (một hoặc nhiều môn) hoặc lựa chọn, số câu, thời lượng (khi tính theo phần), điểm (khi
 * chia điểm theo phần), khối gợi ý của mẫu; đổi thứ tự, xóa, thêm phần (bài FULL). Mỗi phần là một hàng có nhãn riêng
 * thay cho bảng của bản vẽ để ô nhập không phải cuộn ngang ở 360px.
 */
export function CreateExamPartsEditor({
  control,
  parts,
  scope,
  timing,
  pointsByPart,
  subjects,
  subjectName,
  errorFor,
  onMove,
  onRemove,
  onAdd,
  onSectionsChange,
}: {
  control: Control<CreateExamFormValues>;
  parts: readonly CreatePartValues[];
  scope: ExamScope;
  timing: ExamTiming;
  pointsByPart: boolean;
  subjects: readonly CatalogOption[];
  subjectName: (code: string) => string;
  errorFor: FieldErrorLookup;
  onMove: (index: number, delta: -1 | 1) => void;
  onRemove: (index: number) => void;
  onAdd: () => void;
  onSectionsChange: (index: number, next: CreateSectionValues[]) => void;
}) {
  const t = useTranslations("exams.create.parts");
  const subjectOptions = subjects.map((subject) => ({ value: subject.code, label: subject.name }));
  const single = scope === "PART";
  const perPart = timing === "PER_PART";
  const numberColumns = (1 + (perPart ? 1 : 0) + (pointsByPart ? 1 : 0)) as 1 | 2 | 3;

  return (
    <div className="flex flex-col gap-3">
      <ol aria-label={t("caption")} className="flex flex-col gap-3">
        {parts.map((part, index) => {
          const order = index + 1;
          const base = `parts.${index}` as const;
          const multi = part.mode === "FIXED" && part.sections.length > 0;
          return (
            <li key={part.key} className="@container flex flex-col gap-3 rounded-lg border border-border bg-surface p-3 sm:p-4">
              <div className="flex items-center justify-between gap-2">
                <p className="text-caption font-semibold tracking-wide text-text-muted uppercase">{t("orderValue", { order })}</p>
                {!single && (
                  <div className="flex items-center gap-0.5">
                    <IconButton
                      aria-label={t("moveUpAria", { order })}
                      icon={<ArrowUp />}
                      disabled={index === 0}
                      onClick={() => onMove(index, -1)}
                    />
                    <IconButton
                      aria-label={t("moveDownAria", { order })}
                      icon={<ArrowDown />}
                      disabled={index === parts.length - 1}
                      onClick={() => onMove(index, 1)}
                    />
                    <IconButton
                      tone="danger"
                      aria-label={t("removeAria", { order })}
                      icon={<Trash2 />}
                      onClick={() => onRemove(index)}
                    />
                  </div>
                )}
              </div>
              <div className={cn("grid grid-cols-1 gap-3 @lg:grid-cols-2", PART_GRID[numberColumns])}>
                <div data-field={`${base}.name`}>
                  <Controller
                    control={control}
                    name={`${base}.name`}
                    render={({ field }) => (
                      <FormField label={t("name")} required error={errorFor(`${base}.name`)}>
                        {(controlProps) => (
                          <Input
                            {...controlProps}
                            ref={field.ref}
                            name={field.name}
                            value={field.value}
                            onChange={field.onChange}
                            onBlur={field.onBlur}
                            autoComplete="off"
                            maxLength={PART_NAME_MAX}
                          />
                        )}
                      </FormField>
                    )}
                  />
                </div>
                <div data-field={`${base}.subjectCode`}>
                  {part.mode === "CHOICE" ? (
                    <FormField label={t("subject")}>
                      {(controlProps) => (
                        <p {...controlProps} className="flex min-h-10 flex-col justify-center text-body">
                          <span className="font-medium text-text">
                            {part.sections.length > 0
                              ? t("choiceCommon")
                              : part.branches.length > 0
                                ? t("choiceBranches")
                                : part.englishOption && part.combinations.length > 0
                                  ? t("choiceWithEnglish")
                                  : t("choiceCombinations")}
                          </span>
                          <span className="text-caption text-text-muted">
                            {t("choicePaths", { count: choicePathCount(part) })}
                          </span>
                        </p>
                      )}
                    </FormField>
                  ) : multi ? (
                    <FormField label={t("subject")}>
                      {(controlProps) => (
                        <p {...controlProps} className="flex min-h-10 flex-col justify-center text-body">
                          <span className="font-medium text-text">{t("multiSubject", { count: part.sections.length })}</span>
                          <span className="text-caption text-text-muted">
                            {part.sections.map((section) => subjectName(section.subjectCode)).join(" · ")}
                          </span>
                        </p>
                      )}
                    </FormField>
                  ) : single ? (
                    <FormField label={t("subject")}>
                      {(controlProps) => (
                        <p {...controlProps} className="flex min-h-10 items-center font-medium text-text">
                          {part.subjectCode ? subjectName(part.subjectCode) : "—"}
                        </p>
                      )}
                    </FormField>
                  ) : (
                    <Controller
                      control={control}
                      name={`${base}.subjectCode`}
                      render={({ field }) => (
                        <FormField label={t("subject")} required error={errorFor(`${base}.subjectCode`)}>
                          {(controlProps) => (
                            <Select
                              {...controlProps}
                              name={field.name}
                              value={field.value || undefined}
                              placeholder={t("subjectPlaceholder")}
                              onValueChange={field.onChange}
                              options={subjectOptions}
                            />
                          )}
                        </FormField>
                      )}
                    />
                  )}
                </div>
                <div data-field={`${base}.questionCount`}>
                  <Controller
                    control={control}
                    name={`${base}.questionCount`}
                    render={({ field }) => (
                      <FormField label={t("questionCount")} required error={errorFor(`${base}.questionCount`)}>
                        {(controlProps) => (
                          <BlockNumberInput
                            {...controlProps}
                            ref={field.ref}
                            name={field.name}
                            value={field.value}
                            onValueChange={field.onChange}
                            onBlur={field.onBlur}
                          />
                        )}
                      </FormField>
                    )}
                  />
                </div>
                {perPart && (
                  <div data-field={`${base}.durationMinutes`}>
                    <Controller
                      control={control}
                      name={`${base}.durationMinutes`}
                      render={({ field }) => (
                        <FormField label={t("duration")} required error={errorFor(`${base}.durationMinutes`)}>
                          {(controlProps) => (
                            <BlockNumberInput
                              {...controlProps}
                              ref={field.ref}
                              name={field.name}
                              value={field.value}
                              onValueChange={field.onChange}
                              onBlur={field.onBlur}
                            />
                          )}
                        </FormField>
                      )}
                    />
                  </div>
                )}
                {pointsByPart && (
                  <div data-field={`${base}.points`}>
                    <Controller
                      control={control}
                      name={`${base}.points`}
                      render={({ field }) => (
                        <FormField label={t("points")} required error={errorFor(`${base}.points`)}>
                          {(controlProps) => (
                            <OptionalNumberInput
                              {...controlProps}
                              ref={field.ref}
                              name={field.name}
                              value={field.value}
                              onValueChange={field.onChange}
                              onBlur={field.onBlur}
                            />
                          )}
                        </FormField>
                      )}
                    />
                  </div>
                )}
              </div>
              {multi && (
                <SectionCountsEditor
                  legend={t("sectionsLegend", { count: safeNumber(part.questionCount) })}
                  path={`${base}.sections`}
                  sections={part.sections}
                  target={safeNumber(part.questionCount)}
                  subjectName={subjectName}
                  error={errorFor(`${base}.sections`)}
                  onChange={(next) => onSectionsChange(index, next)}
                />
              )}
              {part.mode === "FIXED" && (
                <TemplateBlocksNote blocks={[...part.blocks, ...part.sections.flatMap((section) => section.blocks)]} />
              )}
            </li>
          );
        })}
      </ol>

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-subtle px-3 py-2">
        <p className="flex flex-wrap gap-x-4 gap-y-1 text-body font-semibold text-text tabular-nums">
          <span>{t("total", { count: parts.length })}</span>
          <span>{t("questionsValue", { count: sum(parts.map((part) => safeNumber(part.questionCount))) })}</span>
          {perPart && <span>{t("minutesValue", { count: sum(parts.map((part) => safeNumber(part.durationMinutes))) })}</span>}
        </p>
        {!single && (
          <Button type="button" variant="secondary" size="sm" onClick={onAdd}>
            <Plus />
            <span>{t("add")}</span>
          </Button>
        )}
      </div>
      <p className="flex items-start gap-2 text-caption text-text-muted">
        <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
        {perPart ? t("templateNote") : t("templateNoteShared")}
      </p>
    </div>
  );
}

/** Tổng của một đường lựa chọn so với số câu phải có ("51 / 50 câu · thừa 1 câu"). */
function useSumStatus() {
  const t = useTranslations("exams.create.choice");
  return (actual: number, target: number, cellsValid: boolean) => {
    const diff = actual - target;
    const ok = cellsValid && diff === 0;
    const status =
      diff > 0 ? t("sumOver", { count: diff }) : diff < 0 ? t("sumUnder", { count: -diff }) : ok ? null : t("sumInvalid");
    return { ok, status };
  };
}

/**
 * Phần tự chọn (bản vẽ TaoBaiThi mục 3; UniNote mục 5.3; spec 7 kỳ thi): phần chung (THPTQG Tin học) cộng một lựa
 * chọn: Tiếng Anh, tổ hợp của kỳ thi (số câu từng môn theo thứ tự, mặc định chia đều theo phần dư lớn nhất), hoặc nhánh
 * nội dung. Mỗi lựa chọn phải đủ số câu của phần trừ phần chung ("51 / 50 câu · thừa 1 câu"). Gắn thêm tổ hợp qua hộp
 * thoại.
 */
export function CreateExamChoiceEditor({
  control,
  part,
  partIndex,
  kindLabel,
  combinations,
  subjectName,
  errorFor,
  onCombinationsChange,
  onSectionsChange,
  onBranchesChange,
  onAnnounce,
}: {
  control: Control<CreateExamFormValues>;
  part: CreatePartValues;
  partIndex: number;
  /** Tên ngắn của kỳ thi, cho gợi ý tổ hợp. */
  kindLabel: string;
  combinations: readonly CombinationOption[];
  subjectName: (code: string) => string;
  errorFor: FieldErrorLookup;
  onCombinationsChange: (next: CreateCombinationValues[]) => void;
  onSectionsChange: (next: CreateSectionValues[]) => void;
  onBranchesChange: (next: CreateBranchValues[]) => void;
  onAnnounce: (message: string) => void;
}) {
  const t = useTranslations("exams.create.choice");
  const idBase = useId();
  const sumStatus = useSumStatus();
  const [attachOpen, setAttachOpen] = useState(false);
  const base = `parts.${partIndex}` as const;
  const target = Math.max(0, optionTarget({ ...part, questionCount: safeNumber(part.questionCount) }));
  const common = commonCount(part);
  const optionsError = errorFor(`${base}.combinations`);
  const byCode = new Map(combinations.map((combination) => [combination.code, combination]));
  const subjectsOf = (code: string) => byCode.get(code)?.subjectCodes ?? [];
  const available = combinations.filter((combination) => !part.combinations.some((item) => item.code === combination.code));
  const names = {
    subject: subjectName,
    combination: (code: string) => subjectsOf(code).map(subjectName).join(" – "),
  };
  const sizes = new Set(combinations.map((combination) => combination.subjectCodes.length));
  const showCombinations = combinations.length > 0 || part.combinations.length > 0;

  const attach = (combination: CombinationOption) => {
    // Giữ thứ tự của danh mục.
    const codes = new Set([...part.combinations.map((item) => item.code), combination.code]);
    const next = combinations
      .filter((item) => codes.has(item.code))
      .map(
        (item) =>
          part.combinations.find((existing) => existing.code === item.code) ?? {
            code: item.code,
            subjectCounts: defaultSubjectCounts(target, item.subjectCodes.length),
          },
      );
    onCombinationsChange(next);
    setAttachOpen(false);
    onAnnounce(t("attached", { code: combination.code }));
  };

  const detach = (code: string) => {
    onCombinationsChange(part.combinations.filter((item) => item.code !== code));
    onAnnounce(t("detached", { code }));
  };

  const setCount = (code: string, subjectIndex: number, value: number) => {
    onCombinationsChange(
      part.combinations.map((item) =>
        item.code === code
          ? { ...item, subjectCounts: item.subjectCounts.map((count, index) => (index === subjectIndex ? value : count)) }
          : item,
      ),
    );
  };

  return (
    <div className="flex flex-col gap-4">
      {part.sections.length > 0 && (
        <div className="flex flex-col gap-2">
          <SectionCountsEditor
            legend={t("commonLegend")}
            path={`${base}.sections`}
            sections={part.sections}
            target={null}
            subjectName={subjectName}
            error={errorFor(`${base}.sections`)}
            onChange={onSectionsChange}
          />
          <p className="text-caption text-text-muted">{t("commonHint", { common, option: target })}</p>
          <TemplateBlocksNote blocks={part.sections.flatMap((section) => section.blocks)} />
        </div>
      )}

      {part.englishAvailable && (
        <div data-field={`${base}.englishOption`} className="flex items-start gap-3 rounded-md border border-border p-3">
          <Controller
            control={control}
            name={`${base}.englishOption`}
            render={({ field }) => (
              <Checkbox
                id={`${idBase}-english`}
                ref={field.ref}
                checked={field.value}
                aria-describedby={`${idBase}-english-hint`}
                onCheckedChange={(checked) => field.onChange(checked === true)}
                className="mt-0.5"
              />
            )}
          />
          <div className="flex flex-col gap-0.5">
            <label htmlFor={`${idBase}-english`} className="cursor-pointer font-medium text-text">
              {t("english", { count: target })}
            </label>
            <p id={`${idBase}-english-hint`} className="text-caption text-text-muted">
              {t("englishHint")}
            </p>
          </div>
        </div>
      )}

      {part.branches.length > 0 && (
        <section aria-labelledby={`${idBase}-branches`} data-field={`${base}.branches`} className="flex flex-col gap-3">
          <div className="flex flex-col gap-0.5">
            <h3 id={`${idBase}-branches`} className="text-title font-semibold text-text">
              {t("branches")}
            </h3>
            <p className="text-caption text-text-muted">{t("branchesHint", { count: target })}</p>
          </div>
          <ul className="flex flex-col gap-2">
            {part.branches.map((branch, branchIndex) => {
              const path = `${base}.branches.${branchIndex}.questionCount`;
              const error = errorFor(path);
              const { ok, status } = sumStatus(safeNumber(branch.questionCount), target, Number.isInteger(branch.questionCount) && branch.questionCount > 0);
              const inputId = `${idBase}-branch-${branch.code}`;
              const sumId = `${inputId}-sum`;
              const errorId = `${inputId}-error`;
              return (
                <li
                  key={branch.code}
                  data-field={path}
                  className={cn(
                    "flex flex-col gap-2 rounded-lg border bg-surface py-2.5 pr-2 pl-3.5",
                    ok ? "border-border" : "border-danger",
                  )}
                >
                  <div className="flex flex-wrap items-end gap-x-3.5 gap-y-2.5">
                    <div className="flex min-w-36 flex-1 basis-40 flex-col gap-0.5 self-center">
                      <span className="font-semibold text-text">{branch.name}</span>
                      <span className="text-caption text-text-muted">{t("branchCode", { code: branch.code })}</span>
                    </div>
                    <div className="flex w-28 flex-col gap-1">
                      <label htmlFor={inputId} className="text-caption text-text-muted">
                        {t("branchCount")}
                      </label>
                      <BlockNumberInput
                        id={inputId}
                        value={branch.questionCount}
                        onValueChange={(value) =>
                          onBranchesChange(
                            part.branches.map((item, position) => (position === branchIndex ? { ...item, questionCount: value } : item)),
                          )
                        }
                        aria-invalid={ok ? undefined : true}
                        aria-describedby={[sumId, error ? errorId : null].filter(Boolean).join(" ")}
                        className="h-10"
                      />
                    </div>
                    <output
                      id={sumId}
                      className={cn(
                        "flex h-10 items-center rounded-md px-2.5 text-body font-semibold tabular-nums",
                        ok ? "bg-primary-50 text-primary-text" : "bg-danger-bg text-danger",
                      )}
                    >
                      {t("sumValue", { actual: safeNumber(branch.questionCount), expected: target })}
                      {status && ` · ${status}`}
                    </output>
                  </div>
                  <TemplateBlocksNote blocks={branch.blocks} />
                  {error && (
                    <p id={errorId} className="text-caption text-danger">
                      {error}
                    </p>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      )}

      {showCombinations && (
        <section aria-labelledby={`${idBase}-combinations`} data-field={`${base}.combinations`} className="flex flex-col gap-3">
          <div className="flex flex-wrap items-end justify-between gap-2">
            <div className="flex flex-col gap-0.5">
              <h3 id={`${idBase}-combinations`} className="text-title font-semibold text-text">
                {t("combinations")}
              </h3>
              {combinations.length > 0 && (
                <p className="text-caption text-text-muted">
                  {sizes.size === 1
                    ? t("hintSubjects", { kind: kindLabel, count: [...sizes][0] ?? 0 })
                    : t("hintMixed", { kind: kindLabel })}
                </p>
              )}
            </div>
            <Button type="button" variant="secondary" size="sm" onClick={() => setAttachOpen(true)}>
              <Link2 />
              <span>{t("attach")}</span>
            </Button>
          </div>

          {part.combinations.length === 0 ? (
            <p className="rounded-md border border-dashed border-border px-3 py-3 text-body text-text-muted">{t("emptyAllocation")}</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {part.combinations.map((combination, combinationIndex) => {
                const path = `${base}.combinations.${combinationIndex}.subjectCounts`;
                const error = errorFor(path);
                const actual = sum(combination.subjectCounts.map(safeNumber));
                const cellsValid = combination.subjectCounts.every((count) => Number.isInteger(count) && count > 0);
                const { ok, status } = sumStatus(actual, target, cellsValid);
                const sumId = `${idBase}-${combination.code}-sum`;
                const errorId = `${idBase}-${combination.code}-error`;
                return (
                  <li
                    key={combination.code}
                    data-field={path}
                    className={cn(
                      "flex flex-col gap-2 rounded-lg border bg-surface py-2.5 pr-2 pl-3.5",
                      ok ? "border-border" : "border-danger",
                    )}
                  >
                    <div className="flex flex-wrap items-end gap-x-3.5 gap-y-2.5">
                      <div className="flex min-w-36 flex-1 basis-40 flex-col gap-0.5 self-center">
                        <span className="font-semibold text-text">{combination.code}</span>
                        <span className="text-caption text-text-muted">{names.combination(combination.code)}</span>
                      </div>
                      {subjectsOf(combination.code).map((subjectCode, subjectIndex) => {
                        const label = t("subjectLabel", { index: subjectIndex + 1, subject: subjectName(subjectCode) });
                        const inputId = `${idBase}-${combination.code}-${subjectIndex}`;
                        return (
                          <div key={subjectCode} className="flex w-24 flex-col gap-1">
                            <label htmlFor={inputId} className="text-caption text-text-muted">
                              {label}
                            </label>
                            <BlockNumberInput
                              id={inputId}
                              value={combination.subjectCounts[subjectIndex] ?? 0}
                              onValueChange={(value) => setCount(combination.code, subjectIndex, value)}
                              aria-invalid={ok ? undefined : true}
                              aria-describedby={[sumId, error ? errorId : null].filter(Boolean).join(" ")}
                              className="h-10"
                            />
                          </div>
                        );
                      })}
                      <output
                        id={sumId}
                        className={cn(
                          "flex h-10 items-center rounded-md px-2.5 text-body font-semibold tabular-nums",
                          ok ? "bg-primary-50 text-primary-text" : "bg-danger-bg text-danger",
                        )}
                      >
                        {t("sumValue", { actual, expected: target })}
                        {status && ` · ${status}`}
                      </output>
                      <IconButton
                        tone="danger"
                        aria-label={t("removeAria", { code: combination.code })}
                        icon={<Trash2 />}
                        onClick={() => detach(combination.code)}
                      />
                    </div>
                    {combination.expertConfirmed === false && (
                      <StatusChip tone="warning" className="self-start">
                        {t("expertPending")}
                      </StatusChip>
                    )}
                    <TemplateBlocksNote blocks={combination.blocks ?? []} />
                    {error && (
                      <p id={errorId} className="text-caption text-danger">
                        {error}
                      </p>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
          <ul className="flex list-disc flex-col gap-1 pl-5 text-caption text-text-muted">
            <li>{t("rules.attached")}</li>
            <li>{t("rules.total", { count: target })}</li>
            <li>{t("rules.fixed")}</li>
            <li>{t("rules.catalog")}</li>
          </ul>
        </section>
      )}

      {optionsError && (
        <p role="alert" className="text-caption text-danger">
          {optionsError}
        </p>
      )}

      <CombinationAttachDialog
        open={attachOpen}
        onOpenChange={setAttachOpen}
        available={available}
        names={names}
        questionCount={target}
        onAttach={attach}
      />
    </div>
  );
}
