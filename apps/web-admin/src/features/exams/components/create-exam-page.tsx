"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Button,
  Card,
  cn,
  EmptyState,
  focusRing,
  FormField,
  Input,
  LiveStatus,
  notify,
  PageHeader,
  Skeleton,
  Textarea,
} from "@thi360/ui";
import { ChevronLeft, RefreshCw, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { Controller, get, useForm, useWatch } from "react-hook-form";
import { MockDataBanner } from "@/components/mock-data-banner";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { getErrorStatus } from "@/lib/api/query-client";
import {
  combinationsForKind,
  createExamSchema,
  type CreateCombinationValues,
  type CreateExamFormValues,
  type CreatePartValues,
  EXAM_CODE_MAX,
  EXAM_DESCRIPTION_MAX,
  EXAM_NAME_MAX,
  emptyPart,
  initialParts,
  isSingleScope,
  partsEdited,
  type SingleScope,
  summarizeParts,
  toCreateExamRequest,
} from "../create-exam-schema";
import { examTabHref } from "../editor-tabs";
import { useCreateExam, useExamTemplates, useSuggestedExamCode } from "../queries";
import type { ExamFormat, ExamKind, ExamTemplate } from "../types";
import { CreateExamCover, type CoverImage } from "./create-exam-cover";
import { ChoiceCardGroup, GenerateCodeButton, useCreateExamMessages, useScopeOptions, useSingleScopes } from "./create-exam-fields";
import { CreateExamChoiceEditor, CreateExamPartsEditor } from "./create-exam-structure";
import { type CreateExamIssue, CreateExamSummaryPanel, focusField } from "./create-exam-summary";
import { UnsavedChangesDialog, useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";

const EXAMS_HREF = "/exams";

/** Focus ô nhập sau khi React vẽ xong thay đổi (thêm, xóa phần). */
function focusAfterChange(path: string) {
  requestAnimationFrame(() => focusField(path));
}

/**
 * Trang Tạo bài thi (`/exams/new`, UniNote PBI-04 mục 3, 4, 5.1–5.3): Thông tin chung, Cấu trúc khởi tạo theo mẫu, Phần
 * tự chọn; bảng tổng hợp dính bên phải. Tạo xong mở Cấu trúc và ma trận để soạn khối câu hỏi.
 */
export function CreateExamPage() {
  const t = useTranslations("exams.create");
  const templatesQuery = useExamTemplates();
  const referenceQuery = useContentReference();

  let body: ReactNode;
  if (templatesQuery.isError || referenceQuery.isError) {
    body = (
      <Card padding="md">
        <EmptyState
          icon={<TriangleAlert />}
          headingLevel={2}
          title={t("loadErrorTitle")}
          description={t("loadErrorDescription")}
          action={
            <Button
              variant="secondary"
              onClick={() => {
                void templatesQuery.refetch();
                void referenceQuery.refetch();
              }}
            >
              {t("retry")}
            </Button>
          }
        />
      </Card>
    );
  } else if (!templatesQuery.data || !referenceQuery.data) {
    body = (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-72" />
        <Skeleton className="h-56" />
      </div>
    );
  } else {
    body = <CreateExamForm templates={templatesQuery.data} reference={referenceQuery.data} />;
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <Link
          href={EXAMS_HREF}
          className={cn(
            "inline-flex w-fit items-center gap-1 rounded-sm text-body font-medium text-text-secondary hover:text-text",
            focusRing,
          )}
        >
          <ChevronLeft aria-hidden className="size-4" />
          {t("backToList")}
        </Link>
        <PageHeader title={t("title")} description={t("description")} />
      </div>
      <MockDataBanner />
      {body}
    </div>
  );
}

interface PendingScope {
  kind: ExamKind;
  format: ExamFormat;
  singleScope: string;
  parts: CreatePartValues[];
}

function CreateExamForm({ templates, reference }: { templates: readonly ExamTemplate[]; reference: ContentReference }) {
  const t = useTranslations("exams.create");
  const router = useRouter();
  const formId = useId();
  const tParts = useTranslations("exams.create.parts");
  const lookup = useReferenceLookup(reference);
  const subjectName = lookup.subject;
  const { kindOptions, formatOptions } = useScopeOptions();
  const singleScopes = useSingleScopes();
  const scopeName = singleScopes.scopeName;
  const pendingRef = useRef<HTMLDivElement>(null);
  const messages = useCreateExamMessages();
  const [takenCodes, setTakenCodes] = useState<ReadonlySet<string>>(() => new Set());
  const schema = useMemo(() => createExamSchema(messages, takenCodes), [messages, takenCodes]);
  const createExam = useCreateExam();

  const buildParts = useCallback(
    (kind: ExamKind, format: ExamFormat, singleScope: string) =>
      initialParts({
        templates,
        kind,
        format,
        singleScope,
        combinations: combinationsForKind(reference.combinations, kind),
        scopeName,
      }),
    [templates, reference.combinations, scopeName],
  );
  const [baseline, setBaseline] = useState<CreatePartValues[]>(() => buildParts("HSA", "FULL", ""));
  const form = useForm<CreateExamFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: { name: "", code: "", kind: "HSA", format: "FULL", singleSubjectCode: "", description: "", parts: baseline },
  });
  const { control, register, handleSubmit, setValue, getValues, setError, resetField, getFieldState, formState } = form;
  const values = useWatch({ control }) as CreateExamFormValues;

  const [cover, setCover] = useState<CoverImage | null>(null);
  const [pendingScope, setPendingScope] = useState<PendingScope | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [liveMessage, setLiveMessage] = useState("");
  const guard = useUnsavedChangesGuard((formState.isDirty || cover !== null) && !createExam.isSuccess);

  // Mã gợi ý theo kỳ thi và hình thức; người dùng đã sửa mã thì giữ mã của họ.
  const suggestion = useSuggestedExamCode(
    { kind: values.kind, format: values.format, singleScope: values.format === "SINGLE" ? values.singleSubjectCode : null },
    true,
  );
  useEffect(() => {
    if (suggestion.data && !getFieldState("code").isDirty) resetField("code", { defaultValue: suggestion.data });
  }, [suggestion.data, getFieldState, resetField]);

  const generateCode = async () => {
    const result = await suggestion.refetch();
    if (!result.data) return;
    setValue("code", result.data, { shouldDirty: true, shouldValidate: true });
    setLiveMessage(t("codeGenerated", { code: result.data }));
  };

  const kindCombinations = useMemo(
    () => combinationsForKind(reference.combinations, values.kind),
    [reference.combinations, values.kind],
  );

  // ------------------------------------------------------------ đổi kỳ thi, hình thức, phần/môn bài lẻ

  const templateName = (kind: ExamKind, format: ExamFormat, singleScope: string) =>
    format === "FULL" ? t("templateFull", { kind }) : t("templateSingle", { kind, scope: scopeName(singleScope) });

  const applyScope = ({ kind, format, singleScope, parts }: PendingScope) => {
    setValue("kind", kind, { shouldDirty: true });
    setValue("format", format, { shouldDirty: true });
    setValue("singleSubjectCode", singleScope, { shouldDirty: true, shouldValidate: formState.isSubmitted });
    setValue("parts", parts, { shouldDirty: true });
    setBaseline(parts);
    setPendingScope(null);
    setLiveMessage(t("replace.loaded", { name: templateName(kind, format, singleScope) }));
  };

  /** Đổi kỳ thi, hình thức hoặc môn bài lẻ sau khi đã sửa cấu trúc thì hỏi ngay tại chỗ trước khi nạp mẫu (bản vẽ). */
  const requestScope = (kind: ExamKind, format: ExamFormat, requestedScope?: string) => {
    const current = getValues("singleSubjectCode");
    const singleScope = format === "SINGLE" ? (requestedScope ?? (current || "MATH")) : "";
    if (kind === getValues("kind") && format === getValues("format") && singleScope === current) {
      setPendingScope(null);
      return;
    }
    const next: PendingScope = { kind, format, singleScope, parts: buildParts(kind, format, singleScope) };
    if (partsEdited(getValues("parts"), baseline)) setPendingScope(next);
    else applyScope(next);
  };

  // Hộp xác nhận tại chỗ hiện ra thì đưa con trỏ vào đó.
  useEffect(() => {
    if (pendingScope) pendingRef.current?.focus();
  }, [pendingScope]);

  const setCombinations = (partIndex: number, next: CreateCombinationValues[]) => {
    setValue(`parts.${partIndex}.combinations`, next, { shouldDirty: true, shouldValidate: formState.isSubmitted });
  };

  const movePart = (index: number, delta: -1 | 1) => {
    const parts = [...getValues("parts")];
    const target = index + delta;
    const moving = parts[index];
    const other = parts[target];
    if (!moving || !other) return;
    parts[index] = other;
    parts[target] = moving;
    setValue("parts", parts, { shouldDirty: true });
    setLiveMessage(tParts("moved", { order: target + 1 }));
  };

  const removePart = (index: number) => {
    setValue(
      "parts",
      getValues("parts").filter((_, position) => position !== index),
      { shouldDirty: true, shouldValidate: formState.isSubmitted },
    );
    setLiveMessage(tParts("removed", { order: index + 1 }));
    focusAfterChange("parts");
  };

  const addPart = () => {
    const parts = [...getValues("parts"), emptyPart()];
    setValue("parts", parts, { shouldDirty: true });
    setLiveMessage(tParts("added", { order: parts.length }));
    focusAfterChange(`parts.${parts.length - 1}.name`);
  };

  // ------------------------------------------------------------ lỗi và tổng hợp

  const live = schema.safeParse(values);
  const liveErrors = useMemo(() => {
    const map = new Map<string, string>();
    if (!live.success) {
      for (const issue of live.error.issues) {
        const path = issue.path.map(String).join(".");
        if (!map.has(path)) map.set(path, issue.message);
      }
    }
    return map;
  }, [live]);

  /** Lỗi hiện tại chỗ: lỗi của biểu mẫu (sau khi rời ô hoặc bấm Tạo); lỗi cấu trúc tổ hợp hiện ngay. */
  const errorFor = useCallback(
    (path: string): string | undefined => {
      const formError = get(formState.errors, path) as { message?: string } | undefined;
      if (formError?.message) return formError.message;
      if (/\.combinations(\.\d+\.subjectCounts)?$/.test(path)) return liveErrors.get(path);
      return undefined;
    },
    [formState.errors, liveErrors],
  );

  const fieldLabel = useCallback(
    (path: string): string => {
      const segments = path.split(".");
      if (segments[0] !== "parts") {
        const key = segments[0] === "singleSubjectCode" ? "singleSubject" : segments[0];
        return key === "name" || key === "code" || key === "singleSubject" || key === "description"
          ? t(`fields.${key}`)
          : t("fields.parts");
      }
      if (segments.length < 3) return t("fields.parts");
      const index = Number(segments[1]);
      const order = index + 1;
      switch (segments[2]) {
        case "name":
          return t("fields.partName", { order });
        case "subjectCode":
          return t("fields.partSubject", { order });
        case "questionCount":
          return t("fields.partQuestions", { order });
        case "durationMinutes":
          return t("fields.partDuration", { order });
        default: {
          const combination = values.parts[index]?.combinations[Number(segments[3])];
          return combination ? t("fields.combination", { code: combination.code }) : t("fields.partOptions", { order });
        }
      }
    },
    [t, values.parts],
  );

  const issues: CreateExamIssue[] = [...liveErrors].map(([path, message]) => ({
    path,
    field: fieldLabel(path),
    message,
  }));
  const summary = summarizeParts(values.parts);
  const choicePart = values.parts.find((part) => part.mode === "CHOICE");
  const pathText = !choicePart
    ? t("summary.pathsFixed")
    : choicePart.englishOption && choicePart.combinations.length > 0
      ? t("summary.pathsEnglishAndCombinations", { count: choicePart.combinations.length })
      : choicePart.englishOption
        ? t("summary.pathsEnglishOnly")
        : choicePart.combinations.length > 0
          ? t("summary.pathsCombinationsOnly", { count: choicePart.combinations.length })
          : t("summary.pathsNone");
  const pendingImpact = (() => {
    const attached = choicePart?.combinations.length ?? 0;
    return attached > 0
      ? t("replace.impactCombinations", { parts: values.parts.length, combinations: attached })
      : t("replace.impactParts", { parts: values.parts.length });
  })();
  const choiceParts = values.parts
    .map((part, index) => ({ part, index }))
    .filter(({ part }) => part.mode === "CHOICE");

  // ------------------------------------------------------------ gửi

  const onSubmit = handleSubmit((submitted) => {
    setSubmitError(null);
    createExam.mutate(toCreateExamRequest(submitted, templates), {
      onSuccess: (exam) => {
        const message = t("success", { code: exam.code });
        notify.success(message);
        setLiveMessage(message);
        router.push(examTabHref(exam.id, "structure"));
      },
      onError: (error) => {
        const status = getErrorStatus(error);
        if (status === 409) {
          const code = submitted.code.trim().toUpperCase();
          setTakenCodes((current) => new Set([...current, code]));
          setError("code", { message: t("errors.codeTaken") }, { shouldFocus: true });
        } else {
          setSubmitError(status === 422 ? t("errors.structure") : t("errors.generic"));
        }
      },
    });
  });

  const submitButton = (
    <Button type="submit" form={formId} loading={createExam.isPending} className="w-full sm:w-auto @4xl:w-full">
      {t("submit")}
    </Button>
  );
  const cancelButton = (
    <Button asChild variant="secondary" className="w-full sm:w-auto @4xl:w-full">
      <Link href={EXAMS_HREF}>{t("cancel")}</Link>
    </Button>
  );

  return (
    <div className="@container">
      <div className="grid grid-cols-1 gap-5 @4xl:grid-cols-[minmax(0,1fr)_20rem] @4xl:items-start">
        <form
          id={formId}
          noValidate
          aria-label={t("formAriaLabel")}
          onSubmit={(event) => void onSubmit(event)}
          className="flex min-w-0 flex-col gap-5"
        >
          <FormSection step={1} title={t("sections.info")}>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-[2fr_3fr]">
              <div data-field="code">
                <FormField label={t("code")} required hint={t("codeHint")} error={formState.errors.code?.message}>
                  {(controlProps) => (
                    <div className="flex gap-2">
                      <Input
                        {...controlProps}
                        {...register("code")}
                        autoComplete="off"
                        autoCapitalize="characters"
                        spellCheck={false}
                        maxLength={EXAM_CODE_MAX}
                        className="min-w-0 flex-1"
                      />
                      <GenerateCodeButton onClick={() => void generateCode()} loading={suggestion.isFetching} />
                    </div>
                  )}
                </FormField>
              </div>
              <div data-field="name">
                <FormField label={t("name")} required error={formState.errors.name?.message}>
                  {(controlProps) => (
                    <Input
                      {...controlProps}
                      {...register("name")}
                      autoComplete="off"
                      maxLength={EXAM_NAME_MAX}
                      placeholder={t("namePlaceholder")}
                    />
                  )}
                </FormField>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Controller
                control={control}
                name="kind"
                render={({ field }) => (
                  <ChoiceCardGroup
                    legend={t("kind")}
                    name={field.name}
                    value={field.value}
                    required
                    options={kindOptions}
                    onChange={(kind) => requestScope(kind, getValues("format"))}
                  />
                )}
              />
              <Controller
                control={control}
                name="format"
                render={({ field }) => (
                  <ChoiceCardGroup
                    legend={t("format")}
                    name={field.name}
                    value={field.value}
                    required
                    options={formatOptions}
                    onChange={(format) => requestScope(getValues("kind"), format)}
                  />
                )}
              />
            </div>

            {values.format === "SINGLE" && (
              <div data-field="singleSubjectCode" className="flex flex-col gap-1">
                <Controller
                  control={control}
                  name="singleSubjectCode"
                  render={({ field }) => (
                    <ChoiceCardGroup
                      legend={t("singleSubject")}
                      name={field.name}
                      value={(isSingleScope(field.value) ? field.value : "") as SingleScope}
                      required
                      variant="pill"
                      hint={t("singleSubjectHint")}
                      options={singleScopes.options}
                      onChange={(scope) => requestScope(getValues("kind"), "SINGLE", scope)}
                    />
                  )}
                />
                {formState.errors.singleSubjectCode?.message && (
                  <p role="alert" className="text-caption text-danger">
                    {formState.errors.singleSubjectCode.message}
                  </p>
                )}
              </div>
            )}

            {pendingScope && (
              <div
                ref={pendingRef}
                tabIndex={-1}
                role="alert"
                className={cn("flex flex-col gap-3 rounded-lg border border-warning/40 bg-warning-bg p-4", focusRing)}
              >
                <div className="flex items-start gap-2.5">
                  <RefreshCw aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
                  <div className="flex flex-col gap-1">
                    <p className="font-semibold text-text">
                      {t("replace.title", {
                        name: templateName(pendingScope.kind, pendingScope.format, pendingScope.singleScope),
                      })}
                    </p>
                    <p className="text-body text-text-secondary">{t("replace.description", { impact: pendingImpact })}</p>
                  </div>
                </div>
                <div className="flex flex-wrap justify-end gap-2">
                  <Button type="button" variant="secondary" size="sm" onClick={() => setPendingScope(null)}>
                    {t("replace.cancel")}
                  </Button>
                  <Button type="button" size="sm" onClick={() => applyScope(pendingScope)}>
                    {t("replace.confirm")}
                  </Button>
                </div>
              </div>
            )}

            <div data-field="description">
              <FormField label={t("descriptionLabel")} error={formState.errors.description?.message}>
                {(controlProps) => (
                  <Textarea
                    {...controlProps}
                    {...register("description")}
                    rows={3}
                    maxLength={EXAM_DESCRIPTION_MAX}
                    placeholder={t("descriptionPlaceholder")}
                  />
                )}
              </FormField>
            </div>

            <CreateExamCover value={cover} onChange={setCover} onAnnounce={setLiveMessage} />
          </FormSection>

          <FormSection
            step={2}
            title={t("sections.structure")}
            description={t("sections.structureDescription", {
              name: templateName(values.kind, values.format, values.singleSubjectCode),
            })}
          >
            <div data-field="parts">
              <CreateExamPartsEditor
                control={control}
                parts={values.parts}
                format={values.format}
                subjects={reference.subjects}
                subjectName={subjectName}
                errorFor={errorFor}
                onMove={movePart}
                onRemove={removePart}
                onAdd={addPart}
              />
            </div>
          </FormSection>

          {choiceParts.map(({ part, index }) => (
            <FormSection
              key={part.key}
              step={3}
              title={t("sections.choice", { order: index + 1 })}
              description={t("sections.choiceDescription", { count: Math.max(0, part.questionCount) })}
            >
              <CreateExamChoiceEditor
                control={control}
                part={part}
                partIndex={index}
                kind={values.kind}
                englishAvailable={values.format === "FULL"}
                combinations={kindCombinations}
                subjectName={subjectName}
                errorFor={errorFor}
                onCombinationsChange={(next) => setCombinations(index, next)}
                onAnnounce={setLiveMessage}
              />
            </FormSection>
          ))}

          {submitError && (
            <p role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-body text-danger">
              {submitError}
            </p>
          )}
          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <Button asChild variant="secondary">
              <Link href={EXAMS_HREF}>{t("cancel")}</Link>
            </Button>
            <Button type="submit" loading={createExam.isPending}>
              {t("submit")}
            </Button>
          </div>
        </form>

        <CreateExamSummaryPanel
          summary={summary}
          pathText={pathText}
          issues={issues}
          actions={
            <>
              {submitButton}
              {cancelButton}
            </>
          }
        />
      </div>

      <UnsavedChangesDialog guard={guard} />
      <LiveStatus message={liveMessage} />
    </div>
  );
}

function FormSection({
  step,
  title,
  description,
  children,
}: {
  step: number;
  title: string;
  description?: string;
  children: ReactNode;
}) {
  const headingId = useId();
  return (
    <Card padding="md" className="sm:p-6">
      <section aria-labelledby={headingId} className="flex flex-col gap-4">
        <div className="flex items-start gap-3">
          <span
            aria-hidden
            className="grid size-7 shrink-0 place-items-center rounded-full bg-primary-50 text-body font-bold text-primary-text"
          >
            {step}
          </span>
          <div className="flex min-w-0 flex-col gap-1">
            <h2 id={headingId} className="text-h3 font-bold wrap-break-word text-text">
              {title}
            </h2>
            {description && <p className="text-body text-text-muted">{description}</p>}
          </div>
        </div>
        {children}
      </section>
    </Card>
  );
}
