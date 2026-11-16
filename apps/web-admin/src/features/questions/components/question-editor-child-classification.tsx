"use client";

import { Badge, Button, FormField, Select } from "@thi360/ui";
import { Network } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useState } from "react";
import { Controller, useFormContext, useWatch } from "react-hook-form";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import type { ContentReference } from "@/features/content/types";
import type { QuestionEditorValues } from "../editor-form";
import { FieldErrorText, useAnnounce, useEditorError } from "./question-editor-shared";

/** Giá trị "kế thừa của bộ" trong ô chọn (ô chọn không nhận giá trị rỗng). */
const INHERIT = "__inherit__";

/**
 * Phân loại riêng của một câu con (PBI-03 mục 4.2, 10): khối lớp, độ khó, kiến thức. Mặc định kế thừa của bộ; chọn giá
 * trị khác là ghi đè riêng cho câu con. Môn học, dạng bài và năng lực luôn theo bộ.
 */
export function ChildClassification({ index, reference }: { index: number; reference: ContentReference }) {
  const t = useTranslations("questions.editor.set.classification");
  const { control } = useFormContext<QuestionEditorValues>();
  const parentGrade = useWatch({ control, name: "gradeCode" });
  const parentDifficulty = useWatch({ control, name: "difficultyCode" });
  const nameIn = (options: ContentReference["grades"], code: string) =>
    options.find((option) => option.code === code)?.name ?? t("notChosen");

  const catalog = (
    name: "gradeCode" | "difficultyCode",
    options: ContentReference["grades"],
    parentCode: string,
    label: string,
  ) => (
    <Controller
      control={control}
      name={`children.${index}.${name}`}
      render={({ field }) => (
        <FormField label={label}>
          {(controlProps) => (
            <Select
              {...controlProps}
              name={field.name}
              value={field.value || INHERIT}
              options={[
                { value: INHERIT, label: t("inherit", { value: nameIn(options, parentCode) }) },
                ...options.map((option) => ({ value: option.code, label: option.name })),
              ]}
              onValueChange={(value) => field.onChange(value === INHERIT ? "" : value)}
            />
          )}
        </FormField>
      )}
    />
  );

  return (
    <fieldset className="m-0 flex min-w-0 flex-col gap-3 rounded-lg border border-border p-3">
      <legend className="px-1 text-label font-semibold text-text">{t("legend")}</legend>
      <p className="text-caption text-text-muted">{t("hint")}</p>
      <div className="grid gap-3 @lg:grid-cols-2">
        {catalog("gradeCode", reference.grades, parentGrade, t("grade"))}
        {catalog("difficultyCode", reference.difficulties, parentDifficulty, t("difficulty"))}
      </div>
      <ChildKnowledge index={index} />
    </fieldset>
  );
}

function ChildKnowledge({ index }: { index: number }) {
  const t = useTranslations("questions.editor.set.classification");
  const announce = useAnnounce();
  const fieldId = useId();
  const [open, setOpen] = useState(false);
  const { control, setValue, formState, trigger } = useFormContext<QuestionEditorValues>();
  const override = useWatch({ control, name: `children.${index}.knowledgeOverride` });
  const knowledge = useWatch({ control, name: `children.${index}.knowledge` }) ?? [];
  const parentKnowledge = useWatch({ control, name: "knowledge" }) ?? [];
  const subjectCode = useWatch({ control, name: "subjectCode" });
  const grade = useWatch({ control, name: `children.${index}.gradeCode` });
  const parentGrade = useWatch({ control, name: "gradeCode" });
  const error = useEditorError(`children.${index}.knowledge`);
  const shown = override ? knowledge : parentKnowledge;

  return (
    <div className="flex flex-col gap-1.5">
      <span id={`${fieldId}-label`} className="text-field-label font-medium text-text-secondary">
        {t("knowledge")}
      </span>
      <div className="flex flex-wrap items-center gap-2">
        <Badge>{override ? t("own") : t("inherited")}</Badge>
        {shown.length > 0 ? (
          <ul aria-labelledby={`${fieldId}-label`} className="m-0 flex list-none flex-wrap gap-1.5 p-0">
            {shown.map((ref) => (
              <li key={ref.nodeId} className="rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold text-text">
                {ref.code} · {ref.name}
              </li>
            ))}
          </ul>
        ) : (
          <span className="text-body text-text-muted">{t("noKnowledge")}</span>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Button
          type="button"
          variant="secondary"
          size="sm"
          aria-haspopup="dialog"
          aria-describedby={`${fieldId}-label`}
          aria-invalid={error ? true : undefined}
          onClick={() => setOpen(true)}
        >
          <Network aria-hidden />
          {override ? t("change") : t("override")}
        </Button>
        {override && (
          <Button
            type="button"
            variant="link"
            size="sm"
            onClick={() => {
              setValue(`children.${index}.knowledgeOverride`, false, { shouldDirty: true });
              setValue(`children.${index}.knowledge`, [], { shouldDirty: true });
              announce(t("inheritedAnnounce"));
              if (formState.isSubmitted) void trigger(`children.${index}.knowledge`);
            }}
          >
            {t("backToInherit")}
          </Button>
        )}
      </div>
      <FieldErrorText id={`${fieldId}-error`} message={error} />
      <KnowledgePickerDialog
        open={open}
        onOpenChange={setOpen}
        value={override ? knowledge : parentKnowledge}
        defaultSubjectCode={subjectCode || null}
        defaultGradeCode={grade || parentGrade || null}
        onApply={(value) => {
          setValue(`children.${index}.knowledge`, value, { shouldDirty: true });
          setValue(`children.${index}.knowledgeOverride`, true, { shouldDirty: true });
          announce(t("knowledgeApplied", { count: value.length }));
          if (formState.isSubmitted) void trigger(`children.${index}.knowledge`);
        }}
      />
    </div>
  );
}
