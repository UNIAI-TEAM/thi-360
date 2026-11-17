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
import { ChevronLeft, Lock, RefreshCw, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useEffectEvent, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { RichTextEditor } from "@/components/rich-text";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { getErrorStatus } from "@/lib/api/query-client";
import {
  EXAM_CODE_MAX,
  EXAM_DESCRIPTION_MAX,
  EXAM_NAME_MAX,
  type ExamInfoFormValues,
  kindOptionFor,
  partNeedsSubject,
  resolveScope,
  sameSelection,
  scopeDefaults,
  type ScopeSelection,
  scopeTemplate,
  subjectPartOptions,
  toCreateExamRequest,
} from "../create-exam-schema";
import {
  overviewDiff,
  type OverviewIssue,
  overviewRowsEmpty,
  overviewRowsFromTemplate,
  type OverviewRow,
  overviewTotals,
  rowsDifferFromTemplate,
  updateRowCell,
  validateOverview,
} from "../create-exam-overview";
import { examTabHref } from "../editor-tabs";
import { useCreateExam, useExamTemplates, useSuggestedExamCode } from "../queries";
import type { ExamTemplate } from "../types";
import { GenerateCodeButton, KindSelect, TemplateNotes, useExamInfoSchema, useKindLabel, usePartNames } from "./create-exam-fields";
import { CreateExamScopeFields } from "./create-exam-scope-fields";
import { CreateExamStepper } from "./create-exam-stepper";
import { type CreateExamIssue, CreateExamSummaryPanel, focusField } from "./create-exam-summary";
import { UnsavedChangesDialog, useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";
import { ExamOverviewTable } from "./exam-overview-table";

const EXAMS_HREF = "/exams";
/** Quyền tạo bài (cổng ở `app/(shell)/exams/new/layout.tsx`); chỉ để nêu trong thông báo khi máy chủ trả 403. */
const CREATE_PERMISSION = "EXAM.MODIFY";

/**
 * Trang Tạo bài thi, bước 1 (`/exams/new`, 2B mục 5, 15.1; TaoBaiThi2B): Thông tin bài thi, Phạm vi (kỳ thi, phạm vi,
 * môn, phần, `build_mode`), Cấu trúc tổng quan (bảng sửa ngay trên dòng, có tổng). Không chọn phiên bản cấu trúc ở bước
 * 1 (K8): bảng lấy số của mẫu mặc định, phiên bản đổi ở bước 2. Sửa một ô không nạp lại mẫu (R-09); đổi kỳ thi, phạm
 * vi, môn, phần hoặc `build_mode` thì nạp lại bảng và hỏi xác nhận khi bảng đã sửa. Tạo xong mở bước 2 (Cấu trúc).
 */
export function CreateExamPage() {
  const t = useTranslations("exams.create");
  const templatesQuery = useExamTemplates();
  const referenceQuery = useContentReference();
  const forbidden = [templatesQuery, referenceQuery].some((query) => query.isError && getErrorStatus(query.error) === 403);

  let body: ReactNode;
  if (forbidden) {
    body = (
      <Card padding="md">
        <EmptyState
          icon={<Lock />}
          headingLevel={2}
          title={t("forbiddenTitle")}
          description={t("forbiddenDescription", { permission: CREATE_PERMISSION })}
        />
      </Card>
    );
  } else if (referenceQuery.isError) {
    body = (
      <Card padding="md">
        <EmptyState
          icon={<TriangleAlert />}
          headingLevel={2}
          title={t("loadErrorTitle")}
          description={t("loadErrorDescription")}
          action={
            <Button variant="secondary" onClick={() => void referenceQuery.refetch()}>
              {t("retry")}
            </Button>
          }
        />
      </Card>
    );
  } else if (!referenceQuery.data) {
    body = (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-72" />
        <Skeleton className="h-56" />
      </div>
    );
  } else if (referenceQuery.data.examKinds.length === 0) {
    body = (
      <Card padding="md">
        <EmptyState icon={<TriangleAlert />} headingLevel={2} title={t("step1.noKindsTitle")} description={t("step1.noKindsDescription")} />
      </Card>
    );
  } else {
    // Đang thử lại sau lỗi thì coi như đang tải: khối tổng quan hiện khung chờ thay vì lỗi cũ.
    const templatesStatus: TemplatesStatus = templatesQuery.data
      ? "ready"
      : templatesQuery.isError && !templatesQuery.isFetching
        ? "error"
        : "loading";
    body = (
      <CreateExamForm
        templates={templatesQuery.data ?? NO_TEMPLATES}
        templatesStatus={templatesStatus}
        onRetryTemplates={() => void templatesQuery.refetch()}
        reference={referenceQuery.data}
      />
    );
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
        <CreateExamStepper />
      </div>
      {body}
    </div>
  );
}

interface LoadedScope {
  selection: ScopeSelection;
  template: ExamTemplate | undefined;
  rows: OverviewRow[];
}

type OverviewField = "questionCount" | "durationMinutes" | "maxPoints";
/** Mẫu cấu trúc: `loading` và `error` chỉ che khối tổng quan, biểu mẫu vẫn dùng được (TaoBaiThi2B, trạng thái khác). */
type TemplatesStatus = "loading" | "error" | "ready";
const NO_TEMPLATES: readonly ExamTemplate[] = [];
/** Chữ không phải số hợp lệ thành 0 để schema báo "phải lớn hơn 0"; ô vẫn hiện đúng chữ người dùng đã gõ. */
const parseNumber = (text: string): number => {
  const value = Number(text.trim());
  return text.trim() === "" || !Number.isFinite(value) ? 0 : value;
};
const formatShared = (value: number): string => (value ? String(value) : "");
const formatScore = (value: number | null): string => (value == null ? "" : String(value));

interface CreateExamFormProps {
  /** Mẫu cấu trúc; rỗng khi chưa tải được (`templatesStatus` khác `ready`). */
  templates: readonly ExamTemplate[];
  templatesStatus: TemplatesStatus;
  onRetryTemplates: () => void;
  reference: ContentReference;
}

function CreateExamForm({ templates, templatesStatus, onRetryTemplates, reference }: CreateExamFormProps) {
  const t = useTranslations("exams.create");
  const tStep = useTranslations("exams.create.step1");
  const tOverview = useTranslations("exams.create.overview");
  const router = useRouter();
  const formId = useId();
  const lookup = useReferenceLookup(reference);
  const subjectName = lookup.subject;
  const kindLabel = useKindLabel();
  const partNames = usePartNames();
  const kinds = reference.examKinds;
  /** Tên phần: SP2E, SPT lấy từ tên phần của mẫu Theo môn (`P<thứ tự>`), kỳ thi khác theo mã phần. */
  const partLabel = useCallback(
    (next: ScopeSelection) =>
      partNeedsSubject(kinds, next.kind)
        ? (subjectPartOptions(templates, next.kind, next.subjectCode).find((option) => option.code === next.partCode)?.name ?? next.partCode)
        : partNames.partName(next.partCode),
    [templates, kinds, partNames],
  );
  const pendingRef = useRef<HTMLDivElement>(null);
  const [takenCodes, setTakenCodes] = useState<ReadonlySet<string>>(() => new Set());
  const schema = useExamInfoSchema(takenCodes, kinds);
  const createExam = useCreateExam();

  /** Mẫu mặc định, dòng tổng quan và thời lượng, thang điểm của một lựa chọn (kỳ thi, phạm vi, môn, phần, `build_mode`). */
  const loadScope = useCallback(
    (selection: ScopeSelection): LoadedScope => {
      const template = scopeTemplate(templates, kinds, selection);
      if (template) return { selection, template, rows: overviewRowsFromTemplate(template, selection.scope, selection.partCode) };
      // Không có mẫu (bài ôn chưa có mẫu, môn chưa có mẫu): một dòng trống mang tên của phạm vi.
      const blankLabel =
        selection.scope === "SUBJECT"
          ? subjectName(selection.subjectCode)
          : selection.scope === "PART"
            ? partLabel(selection)
            : kindLabel(selection.kind);
      const rows = overviewRowsEmpty(selection.scope, selection.subjectCode, selection.partCode).map((row) => ({
        ...row,
        label: blankLabel,
      }));
      return { selection, template: undefined, rows };
    },
    [templates, kinds, subjectName, partLabel, kindLabel],
  );

  const [initial] = useState<LoadedScope>(() =>
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
  const [selection, setSelection] = useState<ScopeSelection>(initial.selection);
  const [rows, setRows] = useState<OverviewRow[]>(initial.rows);
  const [pendingSelection, setPendingSelection] = useState<ScopeSelection | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [liveMessage, setLiveMessage] = useState("");

  const initialDefaults = scopeDefaults(initial.template, kindOptionFor(kinds, initial.selection.kind), initial.selection.scope);
  // Chữ người dùng gõ ở hai ô số dưới bảng; giá trị số của biểu mẫu chỉ là bản đã phân tích để kiểm tra (R1-08).
  const [sharedText, setSharedText] = useState(() => formatShared(initialDefaults.sharedDuration));
  const [maxScoreText, setMaxScoreText] = useState(() => formatScore(initialDefaults.maxScore));
  const form = useForm<ExamInfoFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: {
      name: "",
      code: "",
      kind: initial.selection.kind,
      scope: initial.selection.scope,
      partCode: initial.selection.partCode,
      subjectCode: initial.selection.subjectCode,
      timing: initialDefaults.timing,
      sharedDuration: initialDefaults.sharedDuration,
      maxScore: initialDefaults.maxScore,
      yearsConfirmed: false,
      description: "",
      instructions: "",
    },
  });
  const { control, register, handleSubmit, setValue, setError, resetField, getFieldState, formState } = form;
  const values = useWatch({ control }) as ExamInfoFormValues;

  const template = useMemo(() => scopeTemplate(templates, kinds, selection), [templates, kinds, selection]);
  const defaults = useMemo(
    () => scopeDefaults(template, kindOptionFor(kinds, selection.kind), selection.scope),
    [template, kinds, selection],
  );
  const showPoints = defaults.pointsByPart;
  // Mã gợi ý theo kỳ thi, phạm vi, phần hoặc môn; người dùng đã sửa mã thì giữ mã của họ.
  const suggestion = useSuggestedExamCode(
    {
      kind: selection.kind,
      scope: selection.scope,
      partCode: selection.scope === "PART" ? selection.partCode : null,
      subjectCode: selection.scope === "SUBJECT" ? selection.subjectCode || null : null,
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

  // ------------------------------------------------------------ đổi kỳ thi, phạm vi, phần, môn hoặc build_mode

  const templateName = (loaded: ExamTemplate | undefined) => loaded?.name || loaded?.label || null;

  const selectionLabel = (next: ScopeSelection) => {
    const kind = kindLabel(next.kind);
    if (next.scope === "PART") return tStep("labelPart", { kind, scope: partLabel(next) });
    if (next.scope === "SUBJECT") {
      const subject = next.subjectCode ? subjectName(next.subjectCode) : tStep("noSubject");
      return next.buildMode === "REVIEW" ? tStep("labelReview", { kind, subject }) : tStep("labelSubject", { kind, subject });
    }
    return tStep("labelFull", { kind });
  };

  /**
   * Bảng đã sửa (số câu, thời lượng, điểm, thời lượng chung hoặc thang điểm khác mẫu) thì đổi lựa chọn phải hỏi trước
   * khi nạp lại (2B 15.1); chưa sửa thì nạp luôn.
   */
  const tableEdited =
    rowsDifferFromTemplate(rows) ||
    (values.timing === "SHARED" && values.sharedDuration !== defaults.sharedDuration) ||
    (!showPoints && values.maxScore !== defaults.maxScore);
  const guard = useUnsavedChangesGuard((formState.isDirty || tableEdited) && !createExam.isSuccess);

  /** Nạp lại lựa chọn mới; `markDirty = false` khi nạp vì mẫu vừa tải về (người dùng chưa sửa gì). */
  const applyScope = (next: ScopeSelection, markDirty = true) => {
    const loaded = loadScope(next);
    const nextDefaults = scopeDefaults(loaded.template, kindOptionFor(kinds, next.kind), next.scope);
    const dirty = markDirty ? ({ shouldDirty: true } as const) : {};
    setSelection(next);
    setRows(loaded.rows);
    setSharedText(formatShared(nextDefaults.sharedDuration));
    setMaxScoreText(formatScore(nextDefaults.maxScore));
    setValue("kind", next.kind, dirty);
    setValue("scope", next.scope, dirty);
    setValue("partCode", next.partCode, { ...dirty, shouldValidate: formState.isSubmitted });
    setValue("subjectCode", next.subjectCode, { ...dirty, shouldValidate: formState.isSubmitted });
    setValue("timing", nextDefaults.timing, dirty);
    setValue("sharedDuration", nextDefaults.sharedDuration, dirty);
    setValue("maxScore", nextDefaults.maxScore, dirty);
    setValue("yearsConfirmed", false, dirty);
    setPendingSelection(null);
    setLiveMessage(tStep("loaded", { name: selectionLabel(next) }));
  };

  const requestScope = (change: Partial<ScopeSelection>) => {
    const next = resolveScope(templates, kinds, reference.subjects, selection, change);
    if (sameSelection(next, selection)) {
      setPendingSelection(null);
      return;
    }
    if (tableEdited) setPendingSelection(next);
    else applyScope(next);
  };

  // Hộp xác nhận tại chỗ hiện ra thì đưa con trỏ vào đó.
  useEffect(() => {
    if (pendingSelection) pendingRef.current?.focus();
  }, [pendingSelection]);

  // Mẫu về sau khi màn đã dựng (tải chậm, hoặc "Thử lại" ở khối tổng quan thành công): nạp bảng theo mẫu mặc định của
  // lựa chọn hiện tại; bảng đã sửa thì hỏi trước như mọi lần nạp lại.
  const templatesApplied = useRef(templatesStatus === "ready");
  const reloadForTemplates = useEffectEvent(() => {
    const next = resolveScope(templates, kinds, reference.subjects, selection, {});
    if (tableEdited) setPendingSelection(next);
    else applyScope(next, false);
  });
  useEffect(() => {
    if (templatesStatus !== "ready" || templatesApplied.current) return;
    templatesApplied.current = true;
    reloadForTemplates();
  }, [templatesStatus]);

  // ------------------------------------------------------------ bảng tổng quan

  const overviewIssues = useMemo(() => validateOverview(rows), [rows]);
  const issueText = (issue: OverviewIssue) => tOverview(`errors.${issue.code}`);
  const cellIssues = useMemo(() => {
    const map = new Map<string, { questionCount?: string; durationMinutes?: string; maxPoints?: string }>();
    for (const issue of overviewIssues) {
      map.set(issue.rowKey, { ...map.get(issue.rowKey), [issue.field]: tOverview(`errors.${issue.code}`) });
    }
    return map;
  }, [overviewIssues, tOverview]);
  const totals = overviewTotals(rows, values.timing, sharedText);

  const onCellChange = (key: string, field: OverviewField, text: string) => {
    // Sửa một ô chỉ đổi bảng: không nạp lại mẫu, không đụng tên, mã, mô tả (R-09).
    setRows((current) => updateRowCell(current, key, field, text));
  };

  // ------------------------------------------------------------ tóm tắt và lỗi

  const fieldLabel = (path: string) => (t.has(`fields.${path}`) ? t(`fields.${path}`) : path);
  const formIssues: CreateExamIssue[] = Object.entries(formState.errors).flatMap(([path, error]) =>
    error?.message ? [{ path, field: fieldLabel(path), message: String(error.message) }] : [],
  );
  const rowIssueItems: CreateExamIssue[] = overviewIssues.map((issue) => {
    const row = rows.find((item) => item.key === issue.rowKey);
    return {
      path: `${issue.rowKey}.${issue.field}`,
      field: row ? `${row.kind === "section" ? subjectName(row.subjectCode) : row.label}` : tOverview("total"),
      message: issueText(issue),
    };
  });
  const issues = [...formIssues, ...rowIssueItems];

  // ------------------------------------------------------------ gửi

  const onSubmit = handleSubmit((submitted) => {
    setSubmitError(null);
    if (overviewIssues.length > 0) {
      const first = overviewIssues[0]!;
      focusField(`${first.rowKey}.${first.field}`);
      return;
    }
    const request = toCreateExamRequest({
      name: submitted.name,
      code: submitted.code,
      selection,
      description: submitted.description,
      instructions: submitted.instructions,
      timing: submitted.timing,
      sharedDuration: submitted.timing === "SHARED" ? submitted.sharedDuration : null,
      // Bảng có cột Điểm: thang của bài là tổng điểm các phần (dòng bỏ qua không tính), không còn bắt khớp với mẫu.
      maxScore: showPoints ? (totals.maxPoints ?? submitted.maxScore) : submitted.maxScore,
      // Chỉ dòng khác mẫu đi theo yêu cầu; bảng giữ nguyên thì không gửi `overview`.
      overview: overviewDiff(rows),
    });
    createExam.mutate(request, {
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
    <Button
      type="submit"
      form={formId}
      loading={createExam.isPending}
      disabled={templatesStatus === "loading"}
      className="w-full sm:w-auto"
    >
      {t("submit")}
    </Button>
  );
  const cancelButton = (
    <Button asChild variant="secondary" className="w-full sm:w-auto">
      <Link href={EXAMS_HREF}>{t("cancel")}</Link>
    </Button>
  );

  return (
    <div className="flex min-w-0 flex-col gap-5">
      <form
        id={formId}
        noValidate
        aria-label={t("formAriaLabel")}
        onSubmit={(event) => void onSubmit(event)}
        className="flex min-w-0 flex-col gap-5"
      >
        <FormSection step={1} title={t("sections.info")}>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-[3fr_2fr]">
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
            <div data-field="kind">
              <KindSelect kinds={kinds} value={selection.kind} onChange={(kind) => requestScope({ kind })} />
            </div>
          </div>

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
                      aria-label={t("instructionsLabel")}
                      allowImages={false}
                      fieldContext="instructions"
                      invalid={Boolean(formState.errors.instructions)}
                      placeholder={t("instructionsPlaceholder")}
                    />
                  )}
                />
              )}
            </FormField>
          </div>
        </FormSection>

        <FormSection step={2} title={tStep("sections.scope")} description={tStep("sections.scopeDescription", { kind: kindLabel(selection.kind) })}>
          <CreateExamScopeFields
            kinds={kinds}
            subjects={reference.subjects}
            templates={templates}
            selection={selection}
            errors={{ partCode: formState.errors.partCode?.message, subjectCode: formState.errors.subjectCode?.message }}
            onChange={requestScope}
          />

          {pendingSelection && (
            <div
              ref={pendingRef}
              tabIndex={-1}
              role="alert"
              className={cn("flex flex-col gap-3 rounded-lg border border-warning/40 bg-warning-bg p-4", focusRing)}
            >
              <div className="flex items-start gap-2.5">
                <RefreshCw aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
                <div className="flex flex-col gap-1">
                  <p className="font-semibold text-text">{tStep("replace.title", { name: selectionLabel(pendingSelection) })}</p>
                  <p className="text-body text-text-secondary">{tStep("replace.description")}</p>
                </div>
              </div>
              <div className="flex flex-wrap justify-end gap-2">
                <Button type="button" variant="secondary" size="sm" onClick={() => setPendingSelection(null)}>
                  {tStep("replace.cancel")}
                </Button>
                <Button type="button" size="sm" onClick={() => applyScope(pendingSelection)}>
                  {tStep("replace.confirm")}
                </Button>
              </div>
            </div>
          )}
        </FormSection>

        <FormSection
          step={3}
          title={tOverview("title")}
          description={
            templatesStatus === "loading"
              ? undefined
              : templateName(template)
                ? tOverview("description", { template: templateName(template) ?? "" })
                : tOverview("descriptionNoTemplate")
          }
        >
          <TemplateNotes template={template} name={templateName(template) ?? undefined} />
          <p className="inline-flex w-fit rounded-full bg-subtle px-3 py-1 text-caption text-text-secondary">{tOverview("versionAtStep2")}</p>
          {templatesStatus === "error" && (
            <div
              role="alert"
              className="flex flex-col gap-3 rounded-lg border border-danger/40 bg-danger-bg p-3 sm:flex-row sm:items-center sm:justify-between"
            >
              <p className="text-body text-text-secondary">
                <strong className="font-semibold text-danger">{tOverview("loadError.title")}</strong> {tOverview("loadError.description")}
              </p>
              <Button type="button" variant="secondary" size="sm" onClick={onRetryTemplates}>
                {t("retry")}
              </Button>
            </div>
          )}
          {templatesStatus === "ready" && !template && <p className="text-body text-text-secondary">{tOverview("noTemplate")}</p>}
          {templatesStatus === "loading" ? (
            <div aria-busy="true" className="flex flex-col gap-2">
              <Skeleton className="h-10" />
              <Skeleton className="h-10 w-3/4" />
              <Skeleton className="h-10 w-1/2" />
              <span className="sr-only">{tOverview("loading")}</span>
            </div>
          ) : (
            <ExamOverviewTable
              rows={rows}
              timing={values.timing}
              showPoints={showPoints}
              onCellChange={onCellChange}
              issues={cellIssues}
              subjectName={subjectName}
              sharedDuration={{
                value: sharedText,
                error: formState.errors.sharedDuration?.message,
                onChange: (text) => {
                  setSharedText(text);
                  setValue("sharedDuration", parseNumber(text), { shouldDirty: true, shouldValidate: formState.isSubmitted });
                },
              }}
              maxScore={
                showPoints
                  ? undefined
                  : {
                      value: maxScoreText,
                      error: formState.errors.maxScore?.message,
                      onChange: (text) => {
                        setMaxScoreText(text);
                        setValue("maxScore", text.trim() === "" ? null : parseNumber(text), {
                          shouldDirty: true,
                          shouldValidate: formState.isSubmitted,
                        });
                      },
                    }
              }
            />
          )}
        </FormSection>
      </form>

      <CreateExamSummaryPanel
        data={{
          kindLabel: kindLabel(selection.kind),
          scopeLabel: selectionLabel(selection),
          totalQuestions: totals.questions,
          totalMinutes: totals.durationMinutes,
          totalPoints: showPoints ? totals.maxPoints : values.maxScore,
          templateName: templateName(template),
        }}
        timing={values.timing}
        issues={issues}
      />

      {submitError && (
        <p role="alert" className="rounded-md border border-danger/40 bg-danger-bg px-3 py-2 text-body text-danger">
          {submitError}
        </p>
      )}

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        {cancelButton}
        {submitButton}
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
