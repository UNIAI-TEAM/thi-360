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
  StatusChip,
  Switch,
  Textarea,
} from "@thi360/ui";
import { ChevronLeft, RefreshCw, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { Controller, get, useForm, useWatch } from "react-hook-form";
import { RichTextEditor } from "@/components/rich-text";
import { MockDataBanner } from "@/components/mock-data-banner";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { getErrorStatus } from "@/lib/api/query-client";
import {
  combinationsForKind,
  createExamSchema,
  type CreateBranchValues,
  type CreateCombinationValues,
  type CreateExamFormValues,
  type CreatePartValues,
  type CreateSectionValues,
  EXAM_CODE_MAX,
  EXAM_DESCRIPTION_MAX,
  EXAM_NAME_MAX,
  emptyPart,
  initialParts,
  kindOptionFor,
  latestTemplate,
  partsEdited,
  resolveScope,
  sameSelection,
  scopeDefaults,
  type ScopeDefaults,
  type ScopeSelection,
  summarizeParts,
  sumPoints,
  toCreateExamRequest,
} from "../create-exam-schema";
import { examTabHref } from "../editor-tabs";
import { useCreateExam, useExamTemplates, useSuggestedExamCode } from "../queries";
import type { ExamTemplate } from "../types";
import { CreateExamCover, type CoverImage } from "./create-exam-cover";
import {
  GenerateCodeButton,
  ScopeFields,
  TemplateNotes,
  TimingScoreFields,
  useCreateExamMessages,
  useKindLabel,
  usePartNames,
} from "./create-exam-fields";
import { CreateExamChoiceEditor, CreateExamPartsEditor } from "./create-exam-structure";
import { type CreateExamIssue, CreateExamSummaryPanel, focusField } from "./create-exam-summary";
import { UnsavedChangesDialog, useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";

const EXAMS_HREF = "/exams";

/** Focus ô nhập sau khi React vẽ xong thay đổi (thêm, xóa phần). */
function focusAfterChange(path: string) {
  requestAnimationFrame(() => focusField(path));
}

/** Lỗi của các ô hiện ngay khi gõ (không chờ rời ô): tổng số câu của tổ hợp, phần nhiều môn, phần chung, nhánh. */
const LIVE_ERROR_PATH = /\.(combinations(\.\d+\.subjectCounts)?|sections|branches\.\d+\.questionCount)$/;

/**
 * Trang Tạo bài thi (`/exams/new`, UniNote PBI-04 mục 3, 4, 5.1–5.3; spec 7 kỳ thi): Thông tin chung (kỳ thi, hình
 * thức, môn và mẫu theo năm), Thời gian và thang điểm, Cấu trúc khởi tạo theo mẫu, Phần tự chọn; bảng tổng hợp dính bên
 * phải. Tạo xong mở Cấu trúc và ma trận để soạn khối câu hỏi.
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
  scope: ScopeSelection;
  parts: CreatePartValues[];
  defaults: ScopeDefaults;
}

function CreateExamForm({ templates, reference }: { templates: readonly ExamTemplate[]; reference: ContentReference }) {
  const t = useTranslations("exams.create");
  const router = useRouter();
  const formId = useId();
  const pointsSwitchId = useId();
  const pointsHintId = useId();
  const tParts = useTranslations("exams.create.parts");
  const lookup = useReferenceLookup(reference);
  const subjectName = lookup.subject;
  const kindLabel = useKindLabel();
  const kinds = reference.examKinds;
  const partNames = usePartNames();
  const scopeName = partNames.partName;
  const pendingRef = useRef<HTMLDivElement>(null);
  const messages = useCreateExamMessages();
  const [takenCodes, setTakenCodes] = useState<ReadonlySet<string>>(() => new Set());
  const schema = useMemo(() => createExamSchema(messages, takenCodes, kinds), [messages, takenCodes, kinds]);
  const createExam = useCreateExam();

  /** Cấu trúc khởi tạo và giá trị thời lượng, thang điểm của một phạm vi. */
  const loadScope = useCallback(
    (scope: ScopeSelection): PendingScope => {
      const template = latestTemplate(templates, scope);
      return {
        scope,
        parts: initialParts({
          templates,
          kind: scope.kind,
          scope: scope.scope,
          partCode: scope.partCode,
          combinations: combinationsForKind(reference.combinations, scope.kind),
          scopeName,
          template: scope.scope === "PART" ? undefined : (template ?? null),
          subjectCode: scope.subjectCode,
          subjectName,
        }),
        defaults: scopeDefaults(template, kindOptionFor(kinds, scope.kind), scope.scope),
      };
    },
    [templates, reference.combinations, kinds, scopeName, subjectName],
  );

  const [initial] = useState<PendingScope>(() =>
    loadScope(
      resolveScope(
        templates,
        kinds,
        reference.subjects,
        { kind: kinds[0]?.code ?? "", scope: "FULL", partCode: "", subjectCode: "" },
        {},
      ),
    ),
  );
  const [baseline, setBaseline] = useState<CreatePartValues[]>(initial.parts);
  const form = useForm<CreateExamFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: {
      name: "",
      code: "",
      kind: initial.scope.kind,
      scope: initial.scope.scope,
      partCode: initial.scope.partCode,
      subjectCode: initial.scope.subjectCode,
      ...initial.defaults,
      yearsConfirmed: false,
      description: "",
      instructions: "",
      parts: initial.parts,
    },
  });
  const { control, register, handleSubmit, setValue, getValues, setError, resetField, getFieldState, formState } = form;
  const values = useWatch({ control }) as CreateExamFormValues;

  const [cover, setCover] = useState<CoverImage | null>(null);
  const [pendingScope, setPendingScope] = useState<PendingScope | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [liveMessage, setLiveMessage] = useState("");
  const guard = useUnsavedChangesGuard((formState.isDirty || cover !== null) && !createExam.isSuccess);

  // Mã gợi ý theo kỳ thi, phạm vi, phần hoặc môn; người dùng đã sửa mã thì giữ mã của họ.
  const suggestion = useSuggestedExamCode(
    {
      kind: values.kind,
      scope: values.scope,
      partCode: values.scope === "PART" ? values.partCode : null,
      subjectCode: values.scope === "SUBJECT" ? values.subjectCode || null : null,
    },
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
  const currentKind = kindOptionFor(kinds, values.kind);
  const currentTemplate = latestTemplate(templates, {
    kind: values.kind,
    scope: values.scope,
    partCode: values.partCode,
    subjectCode: values.subjectCode,
  });

  // ------------------------------------------------------------ đổi kỳ thi, phạm vi, phần hoặc môn

  const templateName = (scope: ScopeSelection) => {
    const kind = kindLabel(scope.kind);
    if (scope.scope === "PART") return t("templatePart", { kind, scope: scopeName(scope.partCode) });
    if (scope.scope === "SUBJECT") {
      const template = latestTemplate(templates, scope);
      const subject = scope.subjectCode ? subjectName(scope.subjectCode) : t("noSubject");
      return template
        ? t("templateSubject", { kind, subject, year: String(template.structureYear ?? "") })
        : t("templateBlank", { kind, subject });
    }
    return t("templateFull", { kind });
  };

  const currentScope = (): ScopeSelection => ({
    kind: getValues("kind"),
    scope: getValues("scope"),
    partCode: getValues("partCode"),
    subjectCode: getValues("subjectCode"),
  });

  const applyScope = ({ scope, parts, defaults }: PendingScope) => {
    const dirty = { shouldDirty: true } as const;
    setValue("kind", scope.kind, dirty);
    setValue("scope", scope.scope, dirty);
    setValue("partCode", scope.partCode, { ...dirty, shouldValidate: formState.isSubmitted });
    setValue("subjectCode", scope.subjectCode, { ...dirty, shouldValidate: formState.isSubmitted });
    setValue("timing", defaults.timing, dirty);
    setValue("sharedDuration", defaults.sharedDuration, dirty);
    setValue("maxScore", defaults.maxScore, dirty);
    setValue("pointsByPart", defaults.pointsByPart, dirty);
    setValue("yearsConfirmed", false, dirty);
    setValue("parts", parts, dirty);
    setBaseline(parts);
    setPendingScope(null);
    setLiveMessage(t("replace.loaded", { name: templateName(scope) }));
  };

  /** Đổi phạm vi sau khi đã sửa cấu trúc thì hỏi ngay tại chỗ trước khi nạp mẫu (bản vẽ); chưa sửa thì nạp luôn. */
  const requestScope = (change: Partial<ScopeSelection>) => {
    const current = currentScope();
    const scope = resolveScope(templates, kinds, reference.subjects, current, change);
    if (sameSelection(scope, current)) {
      setPendingScope(null);
      return;
    }
    const next = loadScope(scope);
    if (partsEdited(getValues("parts"), baseline)) setPendingScope(next);
    else applyScope(next);
  };

  // Hộp xác nhận tại chỗ hiện ra thì đưa con trỏ vào đó.
  useEffect(() => {
    if (pendingScope) pendingRef.current?.focus();
  }, [pendingScope]);

  const validateNow = { shouldDirty: true, shouldValidate: formState.isSubmitted } as const;
  const setCombinations = (partIndex: number, next: CreateCombinationValues[]) =>
    setValue(`parts.${partIndex}.combinations`, next, validateNow);
  const setSections = (partIndex: number, next: CreateSectionValues[]) =>
    setValue(`parts.${partIndex}.sections`, next, validateNow);
  const setBranches = (partIndex: number, next: CreateBranchValues[]) =>
    setValue(`parts.${partIndex}.branches`, next, validateNow);

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
      validateNow,
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

  /** Lỗi hiện tại chỗ: lỗi của biểu mẫu (sau khi rời ô hoặc bấm Tạo); lỗi tổng số câu hiện ngay. */
  const errorFor = useCallback(
    (path: string): string | undefined => {
      const formError = get(formState.errors, path) as { message?: string } | undefined;
      if (formError?.message) return formError.message;
      if (LIVE_ERROR_PATH.test(path)) return liveErrors.get(path);
      return undefined;
    },
    [formState.errors, liveErrors],
  );

  const fieldLabel = useCallback(
    (path: string): string => {
      const segments = path.split(".");
      const head = segments[0] ?? "";
      if (head !== "parts") {
        switch (head) {
          case "name":
          case "code":
          case "description":
          case "instructions":
          case "subjectCode":
          case "sharedDuration":
          case "maxScore":
            return t(`fields.${head}`);
          case "partCode":
            return t("fields.part");
          default:
            return t("fields.parts");
        }
      }
      if (segments.length < 3) return t("fields.parts");
      const index = Number(segments[1]);
      const order = index + 1;
      const part = values.parts[index];
      switch (segments[2]) {
        case "name":
          return t("fields.partName", { order });
        case "subjectCode":
          return t("fields.partSubject", { order });
        case "questionCount":
          return t("fields.partQuestions", { order });
        case "durationMinutes":
          return t("fields.partDuration", { order });
        case "points":
          return t("fields.partPoints", { order });
        case "sections":
          return t("fields.partSections", { order });
        case "branches": {
          const branch = part?.branches[Number(segments[3])];
          return branch ? t("fields.branch", { name: branch.name }) : t("fields.partOptions", { order });
        }
        default: {
          const combination = part?.combinations[Number(segments[3])];
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
  const summary = summarizeParts(values.parts, values);
  const choicePart = values.parts.find((part) => part.mode === "CHOICE");
  const pathText = (() => {
    if (!choicePart) return t("summary.pathsFixed");
    const segments = [
      ...(choicePart.englishOption ? [t("summary.pathsEnglishOnly")] : []),
      ...(choicePart.combinations.length > 0
        ? [t("summary.pathsCombinationsOnly", { count: choicePart.combinations.length })]
        : []),
      ...(choicePart.branches.length > 0 ? [t("summary.pathsBranches", { count: choicePart.branches.length })] : []),
    ];
    return segments.length > 0 ? segments.join(" + ") : t("summary.pathsNone");
  })();
  const pendingImpact = (() => {
    const attached = values.parts.reduce((total, part) => total + part.combinations.length, 0);
    return attached > 0
      ? t("replace.impactCombinations", { parts: values.parts.length, combinations: attached })
      : t("replace.impactParts", { parts: values.parts.length });
  })();
  const choiceParts = values.parts
    .map((part, index) => ({ part, index }))
    .filter(({ part }) => part.mode === "CHOICE");
  const pointsTotal = sumPoints(values.parts.map((part) => part.points ?? 0));
  const pointsOff = values.pointsByPart && values.maxScore !== null && pointsTotal !== values.maxScore;

  // ------------------------------------------------------------ gửi

  const onSubmit = handleSubmit((submitted) => {
    setSubmitError(null);
    createExam.mutate(toCreateExamRequest(submitted), {
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

            <ScopeFields
              kinds={kinds}
              subjects={reference.subjects}
              templates={templates}
              values={values}
              errors={{ partCode: formState.errors.partCode?.message, subjectCode: formState.errors.subjectCode?.message }}
              onChange={requestScope}
            />

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
                    <p className="font-semibold text-text">{t("replace.title", { name: templateName(pendingScope.scope) })}</p>
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

            <TemplateNotes template={currentTemplate} />

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

            <div data-field="instructions">
              <FormField label={t("instructionsLabel")} hint={t("instructionsHint")} error={formState.errors.instructions?.message}>
                {(controlProps) => (
                  <Controller
                    control={control}
                    name="instructions"
                    render={({ field }) => (
                      <RichTextEditor
                        {...controlProps}
                        value={field.value}
                        onChange={field.onChange}
                        allowImages={false}
                        invalid={Boolean(formState.errors.instructions)}
                        placeholder={t("instructionsPlaceholder")}
                      />
                    )}
                  />
                )}
              </FormField>
            </div>

            <CreateExamCover value={cover} onChange={setCover} onAnnounce={setLiveMessage} />
          </FormSection>

          <FormSection
            step={2}
            title={t("sections.timing")}
            description={t(values.timing === "SHARED" ? "sections.timingSharedDescription" : "sections.timingPerPartDescription")}
          >
            <Controller
              control={control}
              name="sharedDuration"
              render={({ field: durationField }) => (
                <Controller
                  control={control}
                  name="maxScore"
                  render={({ field: scoreField }) => (
                    <TimingScoreFields
                      timing={values.timing}
                      sharedDuration={durationField.value}
                      onSharedDurationChange={durationField.onChange}
                      sharedDurationError={errorFor("sharedDuration")}
                      partMinutes={summary.totalMinutes}
                      maxScore={scoreField.value}
                      onMaxScoreChange={scoreField.onChange}
                      maxScoreError={errorFor("maxScore")}
                      maxScoreOptional={currentKind?.maxScore === null}
                    >
                      <div className="flex flex-col gap-2 rounded-md border border-border p-3">
                        <div className="flex items-start gap-3">
                          <Controller
                            control={control}
                            name="pointsByPart"
                            render={({ field }) => (
                              <Switch
                                id={pointsSwitchId}
                                checked={field.value}
                                aria-describedby={pointsHintId}
                                onCheckedChange={(checked) => field.onChange(checked)}
                                className="mt-0.5"
                              />
                            )}
                          />
                          <div className="flex flex-col gap-0.5">
                            <label htmlFor={pointsSwitchId} className="cursor-pointer font-medium text-text">
                              {t("timing.pointsByPart")}
                            </label>
                            <p id={pointsHintId} className="text-caption text-text-muted">
                              {t("timing.pointsByPartHint")}
                            </p>
                          </div>
                        </div>
                        {values.pointsByPart && (
                          <p className="flex flex-wrap items-center gap-2 text-body font-semibold text-text tabular-nums">
                            {values.maxScore === null
                              ? t("timing.pointsTotal", { actual: pointsTotal })
                              : t("timing.pointsTotalOf", { actual: pointsTotal, expected: values.maxScore })}
                            {pointsOff && <StatusChip tone="warning">{t("timing.pointsMismatch")}</StatusChip>}
                          </p>
                        )}
                      </div>
                    </TimingScoreFields>
                  )}
                />
              )}
            />
          </FormSection>

          <FormSection
            step={3}
            title={t("sections.structure")}
            description={t("sections.structureDescription", { name: templateName(currentScope()) })}
          >
            <div data-field="parts">
              <CreateExamPartsEditor
                control={control}
                parts={values.parts}
                scope={values.scope}
                timing={values.timing}
                pointsByPart={values.pointsByPart}
                subjects={reference.subjects}
                subjectName={subjectName}
                errorFor={errorFor}
                onMove={movePart}
                onRemove={removePart}
                onAdd={addPart}
                onSectionsChange={setSections}
              />
            </div>
          </FormSection>

          {choiceParts.map(({ part, index }, choiceIndex) => {
            const common = part.sections.reduce((total, section) => total + Math.max(0, section.questionCount), 0);
            return (
              <FormSection
                key={part.key}
                step={4 + choiceIndex}
                title={t("sections.choice", { order: index + 1 })}
                description={
                  common > 0
                    ? t("sections.choiceCommonDescription", {
                        common,
                        option: Math.max(0, part.questionCount - common),
                        total: Math.max(0, part.questionCount),
                      })
                    : t("sections.choiceDescription", { count: Math.max(0, part.questionCount) })
                }
              >
                <CreateExamChoiceEditor
                  control={control}
                  part={part}
                  partIndex={index}
                  kindLabel={kindLabel(values.kind)}
                  combinations={kindCombinations}
                  subjectName={subjectName}
                  errorFor={errorFor}
                  onCombinationsChange={(next) => setCombinations(index, next)}
                  onSectionsChange={(next) => setSections(index, next)}
                  onBranchesChange={(next) => setBranches(index, next)}
                  onAnnounce={setLiveMessage}
                />
              </FormSection>
            );
          })}

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
          timing={values.timing}
          maxScore={values.maxScore}
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
