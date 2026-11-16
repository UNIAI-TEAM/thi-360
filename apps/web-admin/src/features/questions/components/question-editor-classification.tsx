"use client";

import {
  Badge,
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  FormField,
  IconButton,
  Input,
  Select,
} from "@thi360/ui";
import { ChevronDown, Network, Plus, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { useId, useMemo, useState } from "react";
import { Controller, useFormContext, useWatch } from "react-hook-form";
import { KnowledgePickerDialog } from "@/features/content/components/knowledge-picker-dialog";
import { indexKnowledge, knowledgePath } from "@/features/content/knowledge-tree";
import type { ContentReference } from "@/features/content/types";
import { questionSetPoints, type QuestionEditorValues } from "../editor-form";
import { EditorSection, FieldErrorText, useAnnounce, useEditorError } from "./question-editor-shared";

/**
 * Khối Phân loại: môn, khối, mức độ, điểm, năng lực (V-ACT, không bắt buộc), kiến thức (MAPID, có kiến thức chính) và
 * nhãn.
 */
export function ClassificationSection({ reference }: { reference: ContentReference }) {
  const t = useTranslations("questions.editor.classification");
  const tErrors = useTranslations("questions.editor.errors");
  const titleId = useId();
  const { control, register, setValue, formState, trigger } = useFormContext<QuestionEditorValues>();
  const type = useWatch({ control, name: "type" });
  const children = useWatch({ control, name: "children" }) ?? [];
  const isSet = type === "QUESTION_SET";
  const pointsError = useEditorError("points");

  const catalogSelect = (name: "subjectCode" | "gradeCode" | "difficultyCode", options: ContentReference["subjects"]) => (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => (
        <FormField
          label={t(name)}
          required
          error={fieldState.error?.message ? tErrors(fieldState.error.message) : undefined}
        >
          {(controlProps) => (
            <Select
              {...controlProps}
              name={field.name}
              value={field.value}
              placeholder={t("choose")}
              options={options.map((option) => ({ value: option.code, label: option.name }))}
              onValueChange={(value) => {
                field.onChange(value);
                if (formState.isSubmitted) void trigger(name);
              }}
            />
          )}
        </FormField>
      )}
    />
  );

  return (
    <EditorSection title={t("title")} titleId={titleId}>
      <div className="grid gap-4 @xl:grid-cols-2 @4xl:grid-cols-4">
        {catalogSelect("subjectCode", reference.subjects)}
        {catalogSelect("gradeCode", reference.grades)}
        {catalogSelect("difficultyCode", reference.difficulties)}
        {isSet ? (
          <FormField label={t("pointsTotal")} hint={t("pointsTotalHint")}>
            {(controlProps) => <Input {...controlProps} value={String(questionSetPoints(children))} readOnly />}
          </FormField>
        ) : (
          <FormField
            label={t("points")}
            required
            hint={t("pointsHint")}
            error={pointsError}
          >
            {(controlProps) => (
              <Input {...controlProps} inputMode="decimal" autoComplete="off" {...register("points")} />
            )}
          </FormField>
        )}
        <CompetencyField reference={reference} />
      </div>
      <KnowledgeField reference={reference} onChange={(value) => setValue("knowledge", value, { shouldDirty: true })} />
      <TagsField reference={reference} />
    </EditorSection>
  );
}

const NO_COMPETENCY = "__none__";

/** Năng lực (`ContentReference.competencies`), dùng để dựng ma trận theo năng lực (V-ACT); để trống: không gắn. */
function CompetencyField({ reference }: { reference: ContentReference }) {
  const t = useTranslations("questions.editor.classification");
  const { control } = useFormContext<QuestionEditorValues>();
  const options = [
    { value: NO_COMPETENCY, label: t("competencyNone") },
    ...reference.competencies.map((option) => ({ value: option.code, label: option.name })),
  ];
  return (
    <Controller
      control={control}
      name="competencyCode"
      render={({ field }) => (
        <FormField label={t("competency")} hint={t("competencyHint")}>
          {(controlProps) => (
            <Select
              {...controlProps}
              name={field.name}
              value={field.value || NO_COMPETENCY}
              options={options}
              onValueChange={(value) => field.onChange(value === NO_COMPETENCY ? "" : value)}
            />
          )}
        </FormField>
      )}
    />
  );
}

function KnowledgeField({
  reference,
  onChange,
}: {
  reference: ContentReference;
  onChange: (value: QuestionEditorValues["knowledge"]) => void;
}) {
  const t = useTranslations("questions.editor.classification");
  const announce = useAnnounce();
  const fieldId = useId();
  const [open, setOpen] = useState(false);
  const { control, formState, trigger } = useFormContext<QuestionEditorValues>();
  const knowledge = useWatch({ control, name: "knowledge" }) ?? [];
  const subjectCode = useWatch({ control, name: "subjectCode" });
  const gradeCode = useWatch({ control, name: "gradeCode" });
  const error = useEditorError("knowledge");
  const index = useMemo(() => indexKnowledge(reference.knowledgeTree), [reference]);
  const subjectName = reference.subjects.find((subject) => subject.code === subjectCode)?.name;
  const gradeName = reference.grades.find((grade) => grade.code === gradeCode)?.name;
  const ordered = [...knowledge].sort((a, b) => Number(b.primary) - Number(a.primary));

  return (
    <div className="flex flex-col gap-1.5">
      <span id={`${fieldId}-label`} className="text-field-label font-medium text-text-secondary">
        {t("knowledge")}
        <span aria-hidden className="ml-0.5 text-danger">
          *
        </span>
      </span>
      {ordered.length > 0 ? (
        <ul aria-labelledby={`${fieldId}-label`} className="m-0 flex list-none flex-col gap-2 p-0">
          {ordered.map((ref) => {
            const entry = index.get(ref.nodeId);
            return (
              <li key={ref.nodeId} className="flex flex-col gap-0.5 rounded-lg border border-border px-3 py-2">
                <span className="flex flex-wrap items-center gap-2 font-medium text-text">
                  <span className="rounded-sm bg-subtle px-2 py-0.5 text-caption font-semibold">{ref.code}</span>
                  {ref.name}
                  {ref.primary && <Badge>{t("primary")}</Badge>}
                </span>
                {entry && <span className="text-caption text-text-muted">{knowledgePath(entry)}</span>}
              </li>
            );
          })}
        </ul>
      ) : (
        <p className="text-body text-text-muted">{t("noKnowledge")}</p>
      )}
      <Button
        type="button"
        variant="secondary"
        size="sm"
        className="self-start"
        aria-haspopup="dialog"
        aria-describedby={[`${fieldId}-label`, error ? `${fieldId}-error` : `${fieldId}-hint`].join(" ")}
        aria-invalid={error ? true : undefined}
        onClick={() => setOpen(true)}
      >
        <Network aria-hidden />
        {knowledge.length ? t("changeKnowledge") : t("chooseKnowledge")}
      </Button>
      {!error && (
        <p id={`${fieldId}-hint`} className="text-caption text-text-muted">
          {t("knowledgeHint")}
        </p>
      )}
      <FieldErrorText id={`${fieldId}-error`} message={error} />
      <KnowledgePickerDialog
        open={open}
        onOpenChange={setOpen}
        value={knowledge}
        defaultSubjectCode={subjectCode || null}
        defaultGradeCode={gradeCode || null}
        description={
          subjectName && gradeName ? t("pickerDescription", { subject: subjectName, grade: gradeName }) : undefined
        }
        onApply={(value) => {
          onChange(value);
          announce(t("knowledgeApplied", { count: value.length }));
          if (formState.isSubmitted) void trigger("knowledge");
        }}
      />
    </div>
  );
}

function TagsField({ reference }: { reference: ContentReference }) {
  const t = useTranslations("questions.editor.classification");
  const announce = useAnnounce();
  const labelId = useId();
  const { control, setValue, getValues } = useFormContext<QuestionEditorValues>();
  const tagCodes = useWatch({ control, name: "tagCodes" }) ?? [];
  const nameOf = (code: string) => reference.tags.find((tag) => tag.code === code)?.name ?? code;
  const available = reference.tags.filter((tag) => !tagCodes.includes(tag.code));

  const setTags = (next: string[]) => setValue("tagCodes", next, { shouldDirty: true });

  return (
    <div role="group" aria-labelledby={labelId} className="flex flex-col gap-1.5">
      <span id={labelId} className="text-field-label font-medium text-text-secondary">
        {t("tags")}
      </span>
      <div className="flex flex-wrap items-center gap-2">
        {tagCodes.length === 0 && <span className="text-body text-text-muted">{t("noTags")}</span>}
        <ul className="m-0 flex list-none flex-wrap gap-2 p-0">
          {tagCodes.map((code) => (
            <li
              key={code}
              className="inline-flex h-8 items-center gap-0.5 rounded-full border border-primary-600 bg-primary-50 ps-3 text-caption font-medium text-primary-text"
            >
              {nameOf(code)}
              <IconButton
                aria-label={t("removeTag", { name: nameOf(code) })}
                icon={<X />}
                className="size-8 rounded-full [&_svg]:size-3.5"
                onClick={() => {
                  setTags(getValues("tagCodes").filter((item) => item !== code));
                  announce(t("tagRemoved", { name: nameOf(code) }));
                }}
              />
            </li>
          ))}
        </ul>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button type="button" variant="link" size="sm" disabled={available.length === 0}>
              <Plus aria-hidden />
              {t("addTag")}
              <ChevronDown aria-hidden />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            {available.map((tag) => (
              <DropdownMenuItem
                key={tag.code}
                onSelect={() => {
                  setTags([...getValues("tagCodes"), tag.code]);
                  announce(t("tagAdded", { name: tag.name }));
                }}
              >
                {tag.name}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  );
}
