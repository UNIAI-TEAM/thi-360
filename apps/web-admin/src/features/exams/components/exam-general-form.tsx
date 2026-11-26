"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery, useQueryClient } from "@tanstack/react-query";
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
  Skeleton,
  StatusChip,
  Textarea,
} from "@thi360/ui";
import { ArrowRight, Info, Lock, RefreshCw, Save, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useEffectEvent, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { RichTextEditor } from "@/components/rich-text";
import { apiErrorCode } from "@/features/content/api-error";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { usePermissions } from "@/features/session/session-provider";
import { getErrorStatus } from "@/lib/api/query-client";
import { getExam, updateExamInfo } from "../api";
import { buildUpdateInfoRequest, examScope, examToFormValues, reloadPartRows } from "../create-exam-edit";
import {
  overviewDiff,
  type OverviewIssue,
  type OverviewRow,
  overviewRowsEmpty,
  overviewRowsFromTemplate,
  overviewTotals,
  rowsDifferFromTemplate,
  updateRowCell,
  validateOverview,
} from "../create-exam-overview";
import { overviewRowsFromParts } from "../create-exam-overview-structure";
import { overviewRowLabel } from "../create-exam-row-label";
import { type PendingInfoSave, useExamInfoSave } from "../create-exam-save";
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
  subjectAudioParts,
  subjectPartOptions,
  templatePartAudio,
  toCreateExamRequest,
  yearsDiffer,
} from "../create-exam-schema";
import { examTabHref, isFromReview, reviewReturnHref } from "../editor-tabs";
import { EXAMS_QUERY_KEYS } from "../keys";
import { useCreateExam, useExamStructure, useExamTemplates, useSuggestedExamCode } from "../queries";
import { EXAM_CHECK_TONE, EXAM_STATUS_TONE } from "../status";
import type { ExamDetail, ExamStructure, ExamTemplate } from "../types";
import { blockCountsLocked } from "./block-form";
import { GenerateCodeButton, KindSelect, TemplateNotes, useExamInfoSchema, useKindLabel, usePartNames } from "./create-exam-fields";
import { CreateExamScopeFields } from "./create-exam-scope-fields";
import { type CreateExamIssue, CreateExamSummaryPanel, focusField } from "./create-exam-summary";
import { ExamDraftLeaveDialog } from "./exam-draft-leave-dialog";
import { ExamOverviewTable } from "./exam-overview-table";
import { ExamName, reviewHref, richNodes } from "./exam-review-shared";
import { UnsavedChangesDialog, useUnsavedChangesGuard } from "@/features/shell/unsaved-changes-guard";
import { ReloadStructureDialog } from "./reload-structure-dialog";

const EXAMS_HREF = "/exams";
const CREATE_PERMISSION = "EXAM.MODIFY";
const NO_TEMPLATES: readonly ExamTemplate[] = [];
const NO_CELL_ISSUES: ReadonlyMap<string, { questionCount?: string; durationMinutes?: string; maxPoints?: string }> = new Map();

type OverviewField = "questionCount" | "durationMinutes" | "maxPoints";
type TemplatesStatus = "loading" | "error" | "ready";
type CellEdits = Record<string, Partial<Record<OverviewField, string>>>;

const parseNumber = (text: string): number => {
  const value = Number(text.trim());
  return text.trim() === "" || !Number.isFinite(value) ? 0 : value;
};
const formatShared = (value: number): string => (value ? String(value) : "");
const formatScore = (value: number | null): string => (value == null ? "" : String(value));

function templatesState(templatesQuery: ReturnType<typeof useExamTemplates>): TemplatesStatus {
  if (templatesQuery.data) return "ready";
  return templatesQuery.isError && !templatesQuery.isFetching ? "error" : "loading";
}

function LoadingSkeletons() {
  return (
    <div className="flex flex-col gap-4" aria-busy="true">
      <Skeleton className="h-72" />
      <Skeleton className="h-56" />
    </div>
  );
}

interface ExamGeneralFormProps {
  mode: "create" | "edit";
  examId?: string;
  exam?: ExamDetail;
}

export function ExamGeneralForm({ mode, examId, exam: initialExam }: ExamGeneralFormProps) {
  const t = useTranslations("exams.create");
  const tGeneral = useTranslations("exams.general");
  const tEditor = useTranslations("exams.editor");
  const resolvedId = examId ?? initialExam?.id ?? "";
  const templatesQuery = useExamTemplates();
  const referenceQuery = useContentReference();
  const examQuery = useQuery({
    queryKey: EXAMS_QUERY_KEYS.detail(resolvedId),
    queryFn: () => getExam(resolvedId),
    enabled: mode === "edit" && Boolean(examId),
    staleTime: 1_000,
    ...(initialExam ? { initialData: initialExam } : {}),
  });
  const structureQuery = useExamStructure(resolvedId, mode === "edit" && Boolean(resolvedId));

  const catalogForbidden = [templatesQuery, referenceQuery].some((query) => query.isError && getErrorStatus(query.error) === 403);
  if (catalogForbidden) {
    return (
      <Card padding="md">
        <EmptyState
          icon={<Lock />}
          headingLevel={2}
          title={t("forbiddenTitle")}
          description={t("forbiddenDescription", { permission: CREATE_PERMISSION })}
          action={
            mode === "edit" ? (
              <Button asChild variant="secondary">
                <Link href={EXAMS_HREF}>{tEditor("backToList")}</Link>
              </Button>
            ) : undefined
          }
        />
      </Card>
    );
  }

  if (mode === "edit") {
    const exam = examQuery.data ?? initialExam;
    if (!exam) return null;
    if (referenceQuery.isError && !referenceQuery.data) {
      return (
        <Card padding="md">
          <EmptyState
            icon={<TriangleAlert />}
            headingLevel={2}
            title={tGeneral("loadErrorTitle")}
            description={tGeneral("loadErrorDescription")}
            action={
              <Button variant="secondary" loading={referenceQuery.isFetching} onClick={() => void referenceQuery.refetch()}>
                {tGeneral("retry")}
              </Button>
            }
          />
        </Card>
      );
    }
    if (!referenceQuery.data) {
      return <LoadingSkeletons />;
    }
    if (referenceQuery.data.examKinds.length === 0) {
      return (
        <Card padding="md">
          <EmptyState icon={<TriangleAlert />} headingLevel={2} title={t("step1.noKindsTitle")} description={t("step1.noKindsDescription")} />
        </Card>
      );
    }
    return (
      <ExamGeneralFormBody
        key={exam.id}
        mode="edit"
        exam={exam}
        structure={structureQuery.data}
        structureLoading={structureQuery.isPending}
        structureFailed={structureQuery.isError}
        onRetryStructure={() => void structureQuery.refetch()}
        templates={templatesQuery.data ?? NO_TEMPLATES}
        templatesStatus={templatesState(templatesQuery)}
        onRetryTemplates={() => void templatesQuery.refetch()}
        reference={referenceQuery.data}
      />
    );
  }

  if (referenceQuery.isError) {
    return (
      <Card padding="md">
        <EmptyState
          icon={<TriangleAlert />}
          headingLevel={2}
          title={t("loadErrorTitle")}
          description={t("loadErrorDescription")}
          action={
            <Button variant="secondary" loading={referenceQuery.isFetching} onClick={() => void referenceQuery.refetch()}>
              {t("retry")}
            </Button>
          }
        />
      </Card>
    );
  }
  if (!referenceQuery.data) {
    return <LoadingSkeletons />;
  }
  if (referenceQuery.data.examKinds.length === 0) {
    return (
      <Card padding="md">
        <EmptyState icon={<TriangleAlert />} headingLevel={2} title={t("step1.noKindsTitle")} description={t("step1.noKindsDescription")} />
      </Card>
    );
  }

  return (
    <ExamGeneralFormBody
      key="create"
      mode="create"
      templates={templatesQuery.data ?? NO_TEMPLATES}
      templatesStatus={templatesState(templatesQuery)}
      onRetryTemplates={() => void templatesQuery.refetch()}
      reference={referenceQuery.data}
    />
  );
}

interface LoadedScope {
  selection: ScopeSelection;
  template: ExamTemplate | undefined;
  rows: OverviewRow[];
}

interface ExamGeneralFormBodyProps {
  mode: "create" | "edit";
  exam?: ExamDetail;
  structure?: ExamStructure;
  structureLoading?: boolean;
  structureFailed?: boolean;
  onRetryStructure?: () => void;
  templates: readonly ExamTemplate[];
  templatesStatus: TemplatesStatus;
  onRetryTemplates: () => void;
  reference: ContentReference;
}

function ExamGeneralFormBody({
  mode,
  exam,
  structure,
  structureLoading = false,
  structureFailed = false,
  onRetryStructure,
  templates,
  templatesStatus,
  onRetryTemplates,
  reference,
}: ExamGeneralFormBodyProps) {
  const t = useTranslations("exams.create");
  const tStep = useTranslations("exams.create.step1");
  const tOverview = useTranslations("exams.create.overview");
  const tGeneral = useTranslations("exams.general");
  const tExams = useTranslations("exams");
  const router = useRouter();
  const queryClient = useQueryClient();
  const permissions = usePermissions();
  const canModify = !permissions.isLoading && permissions.can("EXAM.MODIFY");
  const formId = useId();
  const lookup = useReferenceLookup(reference);
  const subjectName = lookup.subject;
  const kindLabel = useKindLabel();
  const partNames = usePartNames();
  const kinds = reference.examKinds;

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
  const [confirmingYears, setConfirmingYears] = useState(false);
  const [held, setHeld] = useState(false);

  const savedScope = exam ? examScope(exam) : undefined;
  const baseValues = exam ? examToFormValues(exam) : undefined;
  const isPublished = exam !== undefined && exam.servingVersion !== null;
  const isDiscontinued = exam?.status === "DISCONTINUED";
  const lockForm = !canModify || isDiscontinued;
  const lockScope = lockForm || isPublished;
  const appliedTemplate = exam ? templates.find((item) => item.code === exam.templateCode) : undefined;
  const fixedCounts = appliedTemplate?.fixedCounts === true;
  const savedCountsLocked = exam ? fixedCounts || blockCountsLocked(appliedTemplate, exam.scope, exam.buildMode === "REVIEW") : false;
  const [confirmReplace, setConfirmReplace] = useState<PendingInfoSave | null>(null);
  const [confirmReplaceIntent, setConfirmReplaceIntent] = useState<"continue" | "review" | "exit" | "leave">("continue");
  const [cellEdits, setCellEdits] = useState<CellEdits>({});
  const [scopeTouched, setScopeTouched] = useState(false);
  const [leaveError, setLeaveError] = useState<string | null>(null);
  const [fromReview] = useState(() => (typeof window !== "undefined" ? isFromReview(window.location.search) : false));
  const [activeSubmitIntent, setActiveSubmitIntent] = useState<"continue" | "review" | "exit" | "leave">(fromReview ? "review" : "continue");

  // Đi từ danh sách lỗi bước 4 tới một trường ở bước 1 (`?field=yearsConfirmed`): đưa tiêu điểm tới ô đó.
  useEffect(() => {
    if (mode !== "edit" || typeof window === "undefined") return;
    const field = new URLSearchParams(window.location.search).get("field");
    if (!field) return;
    const frame = requestAnimationFrame(() => focusField(field));
    return () => cancelAnimationFrame(frame);
  }, [mode]);

  const loadDraftScope = useCallback(
    (selection: ScopeSelection): LoadedScope => {
      const nextTemplate = scopeTemplate(templates, kinds, selection);
      if (nextTemplate) {
        return { selection, template: nextTemplate, rows: overviewRowsFromTemplate(nextTemplate, selection.scope, selection.partCode) };
      }
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

  const [initial] = useState<LoadedScope>(() => {
    if (savedScope) return { selection: savedScope, template: appliedTemplate, rows: [] };
    return loadDraftScope(
      resolveScope(
        templates,
        kinds,
        reference.subjects,
        { kind: kinds[0]?.code ?? "", scope: "FULL", partCode: "", subjectCode: "" },
        {},
      ),
    );
  });

  const [selection, setSelection] = useState<ScopeSelection>(initial.selection);
  const [draftRows, setDraftRows] = useState<OverviewRow[]>(initial.rows);
  const [pendingSelection, setPendingSelection] = useState<ScopeSelection | null>(null);
  const [liveMessage, setLiveMessage] = useState("");

  const initialDefaults = scopeDefaults(initial.template, kindOptionFor(kinds, initial.selection.kind), initial.selection.scope, initial.selection.buildMode);
  const [sharedText, setSharedText] = useState(() => formatShared((baseValues ?? initialDefaults).sharedDuration));
  const [maxScoreText, setMaxScoreText] = useState(() => formatScore((baseValues ?? initialDefaults).maxScore));

  const form = useForm<ExamInfoFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: baseValues ?? {
      name: "",
      code: "",
      kind: initial.selection.kind,
      scope: initial.selection.scope,
      partCode: initial.selection.partCode,
      subjectCode: initial.selection.subjectCode,
      buildMode: initial.selection.buildMode,
      timing: initialDefaults.timing,
      sharedDuration: initialDefaults.sharedDuration,
      maxScore: initialDefaults.maxScore,
      yearsConfirmed: false,
      description: "",
      instructions: "",
    },
  });
  const { control, register, handleSubmit, setValue, setError, reset, resetField, getFieldState, formState } = form;
  const { errors, isDirty, isSubmitted } = formState;
  const values = useWatch({ control }) as ExamInfoFormValues;

  const editingSaved = mode === "edit" && savedScope !== undefined && sameSelection(selection, savedScope);
  const previewOnly = mode === "edit" && !editingSaved;
  const template = useMemo(
    () => (editingSaved ? appliedTemplate : scopeTemplate(templates, kinds, selection)),
    [editingSaved, appliedTemplate, templates, kinds, selection],
  );
  const defaults = useMemo(
    () => scopeDefaults(template, kindOptionFor(kinds, selection.kind), selection.scope, selection.buildMode),
    [template, kinds, selection],
  );

  const savedRows = useMemo(() => {
    if (!exam) return [];
    const sourceParts = structure?.parts ?? exam.parts;
    return overviewRowsFromParts(sourceParts, { fixedCounts, countsLocked: savedCountsLocked });
  }, [exam, structure, fixedCounts, savedCountsLocked]);

  const rows = useMemo(
    () => (editingSaved ? savedRows.map((row) => ({ ...row, ...cellEdits[row.key] })) : draftRows),
    [editingSaved, savedRows, cellEdits, draftRows],
  );

  const showPoints = editingSaved
    ? rows.some((row) => row.kind === "part" && row.base.maxPoints !== null)
    : defaults.pointsByPart;
  const blockLocked = blockCountsLocked(template, selection.scope, selection.buildMode === "REVIEW");
  const lockedCounts = editingSaved ? savedCountsLocked : template?.fixedCounts === true || blockLocked;
  const vact = selection.kind === "VACT";
  const reviewLayout = selection.scope === "SUBJECT" && selection.buildMode === "REVIEW";
  const showPartPercent = lockedCounts && vact && ((editingSaved ? (structure?.parts ?? exam?.parts) : template?.parts)?.length ?? 0) > 1;
  const showPercents =
    showPartPercent ||
    (vact && selection.scope === "PART" && (template?.parts.some((part) => (part.sections?.length ?? 0) > 0) ?? false));
  const audioSplit = selection.subjectCode ? subjectAudioParts(templates, selection.kind, selection.subjectCode).length > 0 : false;

  const baseRowLabel = useCallback(
    (row: OverviewRow): string | undefined =>
      overviewRowLabel(
        {
          vact,
          partName: partNames.partName,
          subjectOf: (code) => {
            const name = lookup.subject(code);
            return name === code ? undefined : name;
          },
          partSections: (partRow) => {
            if (partRow.partIndex === undefined) return [];
            if (editingSaved) {
              const part = (structure?.parts ?? exam?.parts)?.[partRow.partIndex];
              return part?.mode === "FIXED" ? part.sections : [];
            }
            return template?.parts[partRow.partIndex]?.sections ?? [];
          },
          audioLabel:
            audioSplit && !editingSaved
              ? (partRow) => {
                  const audio = templatePartAudio(partRow.partIndex === undefined ? undefined : template?.parts[partRow.partIndex]);
                  return audio ? tStep(audio === "REQUIRED" ? "skillListening" : "skillReading") : undefined;
                }
              : undefined,
        },
        row,
      ),
    [partNames, lookup, vact, template, audioSplit, editingSaved, structure, exam, tStep],
  );

  const rowLabel = (row: OverviewRow): string | undefined =>
    reviewLayout && row.kind === "part" && selection.subjectCode ? subjectName(selection.subjectCode) : baseRowLabel(row);

  const durationError = (() => {
    const generic = formState.errors.sharedDuration?.message;
    if (!generic || defaults.sharedDuration > 0) return generic;
    if (selection.kind === "VACT" && selection.scope !== "FULL") return tStep("durationRequiredCustom", { kind: kindLabel(selection.kind) });
    if (selection.scope === "PART") return tStep("durationRequiredPart");
    if (selection.scope === "SUBJECT" && selection.buildMode === "REVIEW") return tStep("durationRequiredReview");
    if (selection.scope === "SUBJECT" && template && template.durationMinutes === null && sharedText.trim() === "") return tStep("durationRequiredSubject");
    return generic;
  })();

  const durationHint = (() => {
    if (values.timing !== "SHARED" || editingSaved) return null;
    if (defaults.sharedDuration > 0) return tStep("durationHintPrefilled");
    if (selection.scope === "PART" && template) return tStep("durationHintPartCut");
    if (selection.scope === "SUBJECT" && template && template.durationMinutes === null) return tStep("durationHintEmpty");
    return null;
  })();

  // Chỉ tự gọi gợi ý mã ở chế độ tạo mới khi có quyền `EXAM.MODIFY` (`R-04`, `AC-08`).
  const suggestion = useSuggestedExamCode(
    {
      kind: selection.kind,
      scope: selection.scope,
      partCode: selection.scope === "PART" ? selection.partCode : null,
      subjectCode: selection.scope === "SUBJECT" ? selection.subjectCode || null : null,
    },
    mode === "create" && canModify,
  );
  useEffect(() => {
    if (mode === "create" && canModify && suggestion.data && !getFieldState("code").isDirty) {
      resetField("code", { defaultValue: suggestion.data });
    }
  }, [mode, canModify, suggestion.data, getFieldState, resetField]);

  const generateCode = async () => {
    if (!canModify) return;
    const result = await suggestion.refetch();
    if (!result.data) return;
    setValue("code", result.data, { shouldDirty: true, shouldValidate: true });
    setLiveMessage(t("codeGenerated", { code: result.data }));
  };

  const templateName = (loaded: ExamTemplate | undefined) =>
    loaded?.buildMode === "REVIEW" ? tOverview("reviewTemplate", { kind: kindLabel(loaded.kind) }) : loaded?.name || loaded?.label || null;

  const selectionLabel = (next: ScopeSelection) => {
    const kind = kindLabel(next.kind);
    if (next.scope === "PART") return tStep("labelPart", { kind, scope: partLabel(next) });
    if (next.scope === "SUBJECT") {
      const subject = next.subjectCode ? subjectName(next.subjectCode) : tStep("noSubject");
      return next.buildMode === "REVIEW" ? tStep("labelReview", { kind, subject }) : tStep("labelSubject", { kind, subject });
    }
    return tStep("labelFull", { kind });
  };

  const totals = overviewTotals(rows, values.timing, sharedText);
  const baselineShared = editingSaved && baseValues ? baseValues.sharedDuration : defaults.sharedDuration;
  const baselineMaxScore = editingSaved && baseValues ? baseValues.maxScore : defaults.maxScore;
  const tableEdited =
    rowsDifferFromTemplate(rows) ||
    (values.timing === "SHARED" && values.sharedDuration !== baselineShared) ||
    (!showPoints && values.maxScore !== baselineMaxScore);
  const hasCellEdits = Object.keys(cellEdits).length > 0;
  const overviewChanged = editingSaved && Boolean(structure) && rowsDifferFromTemplate(rows);
  const maxScoreEdited = getFieldState("maxScore", formState).isDirty;
  const partPointsEdited =
    editingSaved &&
    rows.some((row) => row.kind === "part" && row.maxPoints.trim() !== "" && Number(row.maxPoints) !== row.base.maxPoints);
  const maxScoreSynced =
    editingSaved &&
    showPoints &&
    partPointsEdited &&
    !maxScoreEdited &&
    totals.maxPoints !== null &&
    exam !== undefined &&
    totals.maxPoints !== exam.maxScore;
  const hasUnsavedChanges = mode === "edit" ? isDirty || overviewChanged || hasCellEdits || !editingSaved : isDirty || tableEdited;
  const guard = useUnsavedChangesGuard(canModify && hasUnsavedChanges && !createExam.isSuccess && !confirmingYears);

  const { update, structureUpdate, submitError, setSubmitError, save, saveOverviewThenInfo } = useExamInfoSave({
    exam,
    structure,
    rows,
    formDirty: isDirty || maxScoreSynced,
    setError,
    onHold: setHeld,
    onSaved: (message, savedDetail) => {
      setConfirmReplace(null);
      setLiveMessage(message);
      setCellEdits({});
      setScopeTouched(false);
      setTakenCodes(new Set());
      const latest = savedDetail ?? exam;
      if (latest) {
        const nextValues = examToFormValues(latest);
        reset(nextValues);
        setSelection(examScope(latest));
        setSharedText(formatShared(nextValues.sharedDuration));
        setMaxScoreText(formatScore(nextValues.maxScore));
      }
    },
    onFailed: () => setConfirmReplace(null),
    onStructureSaved: () => setCellEdits({}),
  });

  // Khi dữ liệu bài đổi ngoài phiên lưu hai bước (ví dụ cập nhật từ hộp thoại khác) và biểu mẫu sạch, đồng bộ lại giá trị nền.
  const [syncedUpdatedAt, setSyncedUpdatedAt] = useState(exam?.updatedAt);
  if (exam && !held && exam.updatedAt !== syncedUpdatedAt) {
    setSyncedUpdatedAt(exam.updatedAt);
    if (!isDirty && Object.keys(cellEdits).length === 0) {
      const nextValues = examToFormValues(exam);
      setSelection(examScope(exam));
      setScopeTouched(false);
      setSharedText(formatShared(nextValues.sharedDuration));
      setMaxScoreText(formatScore(nextValues.maxScore));
    }
  }
  const lastResetUpdatedAtRef = useRef(exam?.updatedAt);
  useEffect(() => {
    if (!exam || held || exam.updatedAt === lastResetUpdatedAtRef.current) return;
    lastResetUpdatedAtRef.current = exam.updatedAt;
    if (!isDirty && Object.keys(cellEdits).length === 0) {
      reset(examToFormValues(exam));
    }
  }, [exam, held, isDirty, cellEdits, reset]);

  const applyScope = (next: ScopeSelection, markDirty = true) => {
    const backToSaved = mode === "edit" && savedScope !== undefined && sameSelection(next, savedScope);
    const loaded = backToSaved ? { selection: next, template: appliedTemplate, rows: [] } : loadDraftScope(next);
    const nextDefaults = scopeDefaults(loaded.template, kindOptionFor(kinds, next.kind), next.scope, next.buildMode);
    const nextValues = backToSaved && baseValues ? baseValues : { ...nextDefaults, yearsConfirmed: false };
    const dirty = markDirty ? ({ shouldDirty: true } as const) : {};
    if (markDirty) setScopeTouched(true);
    setSelection(next);
    if (!backToSaved) setDraftRows(loaded.rows);
    setSharedText(formatShared(nextValues.sharedDuration));
    setMaxScoreText(formatScore(nextValues.maxScore));
    setValue("kind", next.kind, dirty);
    setValue("scope", next.scope, dirty);
    setValue("buildMode", next.buildMode, dirty);
    setValue("partCode", next.partCode, { ...dirty, shouldValidate: isSubmitted });
    setValue("subjectCode", next.subjectCode, { ...dirty, shouldValidate: isSubmitted });
    setValue("timing", nextValues.timing, dirty);
    setValue("sharedDuration", nextValues.sharedDuration, dirty);
    setValue("maxScore", nextValues.maxScore, dirty);
    setValue("yearsConfirmed", nextValues.yearsConfirmed, dirty);
    setPendingSelection(null);
    setLiveMessage(tStep("loaded", { name: selectionLabel(next) }));
  };

  const requestScope = (change: Partial<ScopeSelection>) => {
    if (lockScope) return;
    // Đổi từ Toàn bài sang Theo môn ở chế độ sửa mà người dùng chưa bấm chọn kiểu dựng: giữ `exam.buildMode` của bài.
    const effectiveChange =
      mode === "edit" && exam && change.buildMode === undefined && (change.scope ?? selection.scope) === "SUBJECT"
        ? { ...change, buildMode: selection.buildMode ?? exam.buildMode }
        : change;
    const next = resolveScope(templates, kinds, reference.subjects, selection, effectiveChange);
    if (sameSelection(next, selection)) {
      setPendingSelection(null);
      return;
    }
    if (mode === "create" && tableEdited) setPendingSelection(next);
    else applyScope(next);
  };

  useEffect(() => {
    if (pendingSelection) pendingRef.current?.focus();
  }, [pendingSelection]);

  const templatesApplied = useRef(templatesStatus === "ready");
  const reloadForTemplates = useEffectEvent(() => {
    if (mode === "edit") return;
    const next = resolveScope(templates, kinds, reference.subjects, selection, {});
    if (tableEdited) setPendingSelection(next);
    else applyScope(next, false);
  });
  useEffect(() => {
    if (templatesStatus !== "ready" || templatesApplied.current) return;
    templatesApplied.current = true;
    reloadForTemplates();
  }, [templatesStatus]);

  const suppressOverviewIssues = previewOnly || lockForm;
  const overviewIssues = useMemo(() => (suppressOverviewIssues ? [] : validateOverview(rows)), [suppressOverviewIssues, rows]);
  const cellIssues = useMemo(() => {
    if (suppressOverviewIssues) return NO_CELL_ISSUES;
    const map = new Map<string, { questionCount?: string; durationMinutes?: string; maxPoints?: string }>();
    for (const issue of overviewIssues) {
      map.set(issue.rowKey, { ...map.get(issue.rowKey), [issue.field]: tOverview(`errors.${issue.code}`) });
    }
    return map;
  }, [suppressOverviewIssues, overviewIssues, tOverview]);

  const onCellChange = (key: string, field: OverviewField, text: string) => {
    if (editingSaved) {
      setCellEdits((current) => ({ ...current, [key]: { ...current[key], [field]: text } }));
    } else {
      setDraftRows((current) => updateRowCell(current, key, field, text));
    }
  };

  const fieldLabel = (path: string) => (t.has(`fields.${path}`) ? t(`fields.${path}`) : path);
  const formIssues: CreateExamIssue[] = Object.entries(errors).flatMap(([path, error]) =>
    error?.message
      ? [{ path, field: fieldLabel(path), message: path === "sharedDuration" ? (durationError ?? String(error.message)) : String(error.message) }]
      : [],
  );
  const rowIssueItems: CreateExamIssue[] = overviewIssues.map((issue: OverviewIssue) => {
    const row = rows.find((item) => item.key === issue.rowKey);
    return {
      path: `${issue.rowKey}.${issue.field}`,
      field: row ? (rowLabel(row) ?? (row.kind === "section" ? subjectName(row.subjectCode) : row.label)) : tOverview("total"),
      message: tOverview(`errors.${issue.code}`),
    };
  });
  const issues = [...formIssues, ...rowIssueItems];

  const onDiscard = () => {
    if (!exam) return;
    const savedValues = examToFormValues(exam);
    reset(savedValues);
    setSelection(examScope(exam));
    setScopeTouched(false);
    setPendingSelection(null);
    setSharedText(formatShared(savedValues.sharedDuration));
    setMaxScoreText(formatScore(savedValues.maxScore));
    setCellEdits({});
    setTakenCodes(new Set());
    setSubmitError(null);
    setHeld(false);
  };

  const navigateAfterEditSave = (intent: "continue" | "review" | "exit" | "leave") => {
    if (!exam) return;
    setLeaveError(null);
    if (intent === "leave") {
      guard.leave();
      return;
    }
    if (intent === "exit") {
      guard.leaveTo(EXAMS_HREF);
      return;
    }
    if (intent === "review") {
      guard.leaveTo(reviewReturnHref(exam.id));
      return;
    }
    guard.leaveTo(examTabHref(exam.id, "structure"));
  };

  const submitWithIntent = (intent: "continue" | "review" | "exit" | "leave") => {
    setActiveSubmitIntent(intent);
    return handleSubmit(
      async (submitted) => {
        if (!canModify || isDiscontinued) return;
        setSubmitError(null);
        setLeaveError(null);
        if ((mode === "create" || overviewChanged || (editingSaved && hasCellEdits)) && overviewIssues.length > 0) {
          const first = overviewIssues[0]!;
          if (intent === "leave") setLeaveError(tGeneral("saveInvalid"));
          else focusField(`${first.rowKey}.${first.field}`);
          return;
        }

        if (mode === "edit" && exam && savedScope) {
          if (!hasUnsavedChanges) {
            if (intent === "exit") guard.leaveTo(EXAMS_HREF);
            else if (intent === "review") guard.leaveTo(reviewHref(exam.id));
            else if (intent === "leave") guard.leave();
            else guard.leaveTo(examTabHref(exam.id, "structure"));
            return;
          }
          const changed = !sameSelection(savedScope, selection);
          const effectiveMaxScore =
            showPoints && partPointsEdited && !maxScoreEdited ? (totals.maxPoints ?? submitted.maxScore) : submitted.maxScore;
          const request = buildUpdateInfoRequest({
            exam,
            submitted,
            kinds,
            appliedTemplate: changed ? template : appliedTemplate,
            changed,
            maxScore: effectiveMaxScore,
          });
          const pending: PendingInfoSave = {
            request,
            parts: changed
              ? reloadPartRows({ templates, kinds, combinations: reference.combinations, partName: partNames.partName, subjectName, next: selection })
              : [],
            modeChange:
              savedScope.scope === "SUBJECT" &&
              selection.scope === "SUBJECT" &&
              (savedScope.buildMode ?? "STANDARD") !== (selection.buildMode ?? "STANDARD"),
          };
          const callbacks = {
            onSuccess: () => navigateAfterEditSave(intent),
            onError: (message: string) => {
              if (intent === "leave") setLeaveError(message);
            },
          };
          if (changed) {
            setConfirmReplaceIntent(intent);
            setConfirmReplace(pending);
          } else if (overviewChanged) {
            await saveOverviewThenInfo(pending, callbacks);
          } else if (isDirty || maxScoreSynced || scopeTouched) {
            save(pending, false, callbacks);
          } else {
            navigateAfterEditSave(intent);
          }
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
          maxScore: showPoints ? (totals.maxPoints ?? submitted.maxScore) : submitted.maxScore,
          overview: overviewDiff(rows),
        });

        try {
          const created = await createExam.mutateAsync(request);
          if (submitted.yearsConfirmed && yearsDiffer(template)) {
            setConfirmingYears(true);
            try {
              await updateExamInfo(created.id, {
                expectedRevision: created.revision,
                yearsConfirmed: true,
              });
              await queryClient.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.all });
            } catch {
              // `POST /exams` already created the draft (`201`); still route onward instead of stranding on `/exams/new` (`SF-2`).
            } finally {
              setConfirmingYears(false);
            }
          }
          const message = t("success", { code: created.code });
          notify.success(message);
          setLiveMessage(message);
          router.push(intent === "exit" ? EXAMS_HREF : examTabHref(created.id, "structure"));
        } catch (error) {
          const status = getErrorStatus(error);
          if (status === 409) {
            const code = submitted.code.trim().toUpperCase();
            setTakenCodes((current) => new Set([...current, code]));
            setError("code", { message: t("errors.codeTaken") }, { shouldFocus: true });
          } else if (status === 422) {
            const reason = apiErrorCode(error);
            setSubmitError(reason && t.has(`errors.codes.${reason}`) ? t(`errors.codes.${reason}`) : t("errors.structure"));
          } else {
            setSubmitError(t("errors.generic"));
          }
        }
      },
      () => {
        if (intent === "leave") {
          setLeaveError(tGeneral("saveInvalid"));
        }
      },
    );
  };

  const handleSaveDraftAndExit = () => {
    if (mode === "edit" && exam && !hasUnsavedChanges) {
      guard.leaveTo(EXAMS_HREF);
      return;
    }
    void submitWithIntent("exit")();
  };

  const handleLeaveSaveAndGo = () => {
    setLeaveError(null);
    void submitWithIntent("leave")();
  };

  const handleLeaveDiscardAndGo = () => {
    onDiscard();
    setLeaveError(null);
    guard.leave();
  };

  const handleLeaveStay = () => {
    setLeaveError(null);
    guard.stay();
  };

  const isBusy = createExam.isPending || confirmingYears || update.isPending || structureUpdate.isPending;

  return (
    <div className="flex min-w-0 flex-col gap-5">
      {isDiscontinued && (
        <p role="note" className="flex items-start gap-2 rounded-lg border border-border bg-subtle px-4 py-3 text-body text-text-secondary">
          <Info aria-hidden className="mt-0.5 size-4 shrink-0 text-text-muted" />
          {tGeneral("discontinuedReadOnly")}
        </p>
      )}
      {exam && exam.servingVersion !== null && canModify && !isDiscontinued && (
        <p role="note" className="flex items-start gap-2 rounded-lg border border-border-primary bg-primary-50 px-4 py-3 text-body text-text">
          <Info aria-hidden className="mt-0.5 size-4 shrink-0 text-primary-text" />
          {tGeneral("workingDraftBanner", { version: exam.servingVersion })}
        </p>
      )}

      <div className="@container">
        <div className="grid grid-cols-1 items-start gap-6 @5xl:grid-cols-[minmax(0,1fr)_21rem]">
          <form
            id={formId}
            noValidate
            aria-label={mode === "create" ? t("formAriaLabel") : tGeneral("formTitle")}
            onSubmit={(event) => void submitWithIntent(mode === "create" ? "continue" : fromReview ? "review" : "continue")(event)}
            className="flex min-w-0 flex-col gap-5"
          >
            <FormSection mode={mode} step={1} title={t("sections.info")}>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-[3fr_2fr]">
                <div data-field="name">
                  <FormField label={t("name")} required error={formState.errors.name?.message}>
                    {(controlProps) => (
                      <Input
                        {...controlProps}
                        {...register("name")}
                        disabled={lockForm}
                        autoComplete="off"
                        maxLength={EXAM_NAME_MAX}
                        placeholder={t("namePlaceholder")}
                      />
                    )}
                  </FormField>
                </div>
                <div data-field="code">
                  <FormField label={t("code")} required hint={t("codeHint")} error={formState.errors.code?.message}>
                    {(controlProps) => (
                      <div className="flex gap-2">
                        <Input
                          {...controlProps}
                          {...register("code")}
                          disabled={lockForm}
                          autoComplete="off"
                          autoCapitalize="characters"
                          spellCheck={false}
                          maxLength={EXAM_CODE_MAX}
                          className="min-w-0 flex-1"
                        />
                        {canModify && !isDiscontinued && (
                          <GenerateCodeButton onClick={() => void generateCode()} loading={suggestion.isFetching} />
                        )}
                      </div>
                    )}
                  </FormField>
                </div>
              </div>

              <div data-field="description">
                <FormField label={t("descriptionLabel")} error={formState.errors.description?.message}>
                  {(controlProps) => (
                    <Textarea
                      {...controlProps}
                      {...register("description")}
                      disabled={lockForm}
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
                          disabled={lockForm}
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

            <FormSection
              mode={mode}
              step={2}
              title={tStep("sections.scope")}
              description={tStep("sections.scopeDescription", { kind: kindLabel(selection.kind) })}
            >
              <div data-field="kind">
                <KindSelect kinds={kinds} value={selection.kind} onChange={(kind) => requestScope({ kind })} disabled={lockScope} />
              </div>
              <CreateExamScopeFields
                kinds={kinds}
                subjects={reference.subjects}
                templates={templates}
                selection={selection}
                errors={{ partCode: formState.errors.partCode?.message, subjectCode: formState.errors.subjectCode?.message }}
                disabled={lockScope}
                buildModeLocked={lockScope}
                buildModeHint={isPublished ? tStep("buildModePublished") : undefined}
                onChange={requestScope}
              />
              {isPublished && (
                <p className="flex items-start gap-2 text-caption text-text-muted">
                  <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
                  {tGeneral("scopeLocked")}
                </p>
              )}

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
              mode={mode}
              step={3}
              title={tOverview("title")}
              description={
                templatesStatus === "loading"
                  ? undefined
                  : previewOnly
                    ? tGeneral("overview.pendingDescription")
                    : templateName(template)
                      ? template?.buildMode === "REVIEW"
                        ? tOverview("descriptionReview", { template: templateName(template) ?? "" })
                        : richNodes(tOverview, "description", { template: <ExamName>{templateName(template) ?? ""}</ExamName> })
                      : mode === "edit"
                        ? tGeneral("overview.description")
                        : tOverview("descriptionNoTemplate")
              }
            >
              <div data-field="yearsConfirmed">
                <Controller
                  control={control}
                  name="yearsConfirmed"
                  render={({ field }) => (
                    <TemplateNotes
                      template={templatesStatus === "loading" ? undefined : editingSaved ? (template ?? exam) : template}
                      yearsConfirmed={field.value}
                      onYearsConfirmedChange={field.onChange}
                      disabled={lockForm}
                    />
                  )}
                />
              </div>
              {lockedCounts && !previewOnly && (
                <p className="inline-flex w-fit items-center gap-1.5 rounded-full bg-subtle px-3 py-1 text-caption text-text-secondary">
                  <Lock aria-hidden className="size-3.5" />
                  {tOverview("lockedCounts")}
                </p>
              )}
              {blockLocked && !previewOnly && <p className="text-body text-text-secondary">{tOverview("lockedBlockCountsNote")}</p>}
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
              {mode === "edit" && structureFailed && !structure && rows.length === 0 && (
                <div
                  role="alert"
                  className="flex flex-col gap-3 rounded-lg border border-danger/40 bg-danger-bg p-3 sm:flex-row sm:items-center sm:justify-between"
                >
                  <p className="text-body text-text-secondary">
                    <strong className="font-semibold text-danger">{tGeneral("overview.loadErrorTitle")}</strong>{" "}
                    {tGeneral("overview.loadErrorDescription")}
                  </p>
                  {onRetryStructure && (
                    <Button type="button" variant="secondary" size="sm" onClick={onRetryStructure}>
                      {tGeneral("retry")}
                    </Button>
                  )}
                </div>
              )}
              {templatesStatus === "ready" && mode === "create" && !template && (
                <p className="text-body text-text-secondary">{tOverview("noTemplate")}</p>
              )}
              {templatesStatus === "loading" || (editingSaved && structureLoading && !structure) ? (
                <div aria-busy="true" className="flex flex-col gap-2">
                  <Skeleton className="h-10" />
                  <Skeleton className="h-10 w-3/4" />
                  <Skeleton className="h-10 w-1/2" />
                  <span className="sr-only">{tOverview("loading")}</span>
                </div>
              ) : editingSaved && rows.length === 0 ? (
                <p className="text-body text-text-secondary">{tGeneral("overview.empty")}</p>
              ) : previewOnly && rows.length === 0 ? (
                <p className="text-body text-text-secondary">{tGeneral("overview.pendingEmpty")}</p>
              ) : (
                <ExamOverviewTable
                  rows={rows}
                  readOnly={lockForm || previewOnly || (editingSaved && !structure)}
                  fieldsReadOnly={lockForm}
                  reviewLayout={reviewLayout}
                  timing={values.timing}
                  showPoints={showPoints}
                  showPercents={showPercents}
                  percentExamHeader={selection.scope === "PART" ? tOverview("col.percentExamCustom") : undefined}
                  showPartPercent={showPartPercent}
                  showCommands={selection.kind === "VSAT"}
                  onCellChange={onCellChange}
                  issues={cellIssues}
                  subjectName={subjectName}
                  rowLabel={rowLabel}
                  sharedDuration={{
                    value: sharedText,
                    error: formState.errors.sharedDuration ? durationError : undefined,
                    hint: templatesStatus === "ready" ? (durationHint ?? undefined) : undefined,
                    onChange: (text) => {
                      setSharedText(text);
                      setValue("sharedDuration", parseNumber(text), { shouldDirty: true, shouldValidate: formState.isSubmitted });
                    },
                  }}
                  maxScore={
                    mode === "create" && showPoints
                      ? undefined
                      : {
                          value: maxScoreSynced ? formatScore(totals.maxPoints) : maxScoreText,
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

            {submitError && (
              <p role="alert" className="rounded-md border border-danger/40 bg-danger-bg px-3 py-2 text-body text-danger">
                {submitError}
              </p>
            )}

            {canModify && !isDiscontinued && (
              <div className="flex flex-wrap items-center justify-end gap-2">
                {mode === "create" ? (
                  <>
                    <Button asChild variant="secondary">
                      <Link href={EXAMS_HREF}>{t("cancel")}</Link>
                    </Button>
                    <Button
                      type="button"
                      variant="secondary"
                      loading={isBusy && activeSubmitIntent === "exit"}
                      disabled={isBusy || templatesStatus === "loading"}
                      onClick={handleSaveDraftAndExit}
                    >
                      <Save aria-hidden className="size-4" />
                      {t("saveDraftAndExit")}
                    </Button>
                    <Button
                      type="submit"
                      loading={isBusy && activeSubmitIntent !== "exit"}
                      disabled={templatesStatus === "loading"}
                    >
                      {t("continueToStructure")}
                      <ArrowRight aria-hidden className="size-4" />
                    </Button>
                  </>
                ) : (
                  <>
                    <Button type="button" variant="secondary" disabled={!hasUnsavedChanges || isBusy} onClick={onDiscard}>
                      {tGeneral("discard")}
                    </Button>
                    <Button
                      type="button"
                      variant="secondary"
                      loading={isBusy && activeSubmitIntent === "exit"}
                      disabled={isBusy || templatesStatus === "loading"}
                      onClick={handleSaveDraftAndExit}
                    >
                      <Save aria-hidden className="size-4" />
                      {t("saveDraftAndExit")}
                    </Button>
                    <Button
                      type="submit"
                      loading={isBusy && activeSubmitIntent !== "exit"}
                      disabled={templatesStatus === "loading"}
                    >
                      {fromReview ? tGeneral("saveAndReturnReview") : t("continueToStructure")}
                      <ArrowRight aria-hidden className="size-4" />
                    </Button>
                  </>
                )}
              </div>
            )}
          </form>

          <div className="scroll-shadows-y flex min-w-0 flex-col gap-4 @5xl:sticky @5xl:top-4 @5xl:max-h-[calc(100dvh-2rem)] @5xl:overflow-y-auto">
            <CreateExamSummaryPanel
              data={{
                kindLabel: kindLabel(selection.kind),
                scopeLabel: selectionLabel(selection),
                totalQuestions: totals.questions,
                totalMinutes: totals.durationMinutes,
                totalPoints:
                  mode === "create"
                    ? showPoints
                      ? totals.maxPoints
                      : values.maxScore
                    : maxScoreSynced
                      ? totals.maxPoints
                      : values.maxScore,
                templateName: templateName(template),
              }}
              timing={values.timing}
              issues={issues}
              editing={mode === "edit"}
            />

            {mode === "edit" && exam && (
              <ExamFactsCard
                exam={exam}
                appliedTemplate={appliedTemplate}
                partName={partNames.partName}
                tGeneral={tGeneral}
                tExams={tExams}
              />
            )}
          </div>
        </div>
      </div>

      {mode === "edit" && exam ? (
        <ExamDraftLeaveDialog
          open={guard.pendingHref !== null}
          saving={isBusy}
          error={leaveError}
          examId={exam.id}
          pendingHref={guard.pendingHref}
          onSaveAndGo={handleLeaveSaveAndGo}
          onDiscardAndGo={handleLeaveDiscardAndGo}
          onStay={handleLeaveStay}
        />
      ) : (
        <UnsavedChangesDialog guard={guard} />
      )}
      <ReloadStructureDialog
        open={confirmReplace !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmReplace(null);
        }}
        title={confirmReplace?.modeChange ? tStep("buildModeChange.title") : tGeneral("replaceTitle")}
        description={confirmReplace?.modeChange ? tStep("buildModeChange.description") : tGeneral("replaceDescription")}
        parts={confirmReplace?.parts ?? []}
        confirmLabel={confirmReplace?.modeChange ? tStep("buildModeChange.confirm") : tGeneral("replaceConfirm")}
        cancelLabel={confirmReplace?.modeChange ? tStep("buildModeChange.cancel") : undefined}
        loading={update.isPending}
        onConfirm={() => {
          if (confirmReplace) {
            save(confirmReplace, false, {
              onSuccess: () => navigateAfterEditSave(confirmReplaceIntent),
              onError: (message) => {
                if (confirmReplaceIntent === "leave") setLeaveError(message);
              },
            });
          }
        }}
      />
      <LiveStatus message={liveMessage} />
    </div>
  );
}

function ExamFactsCard({
  exam,
  appliedTemplate,
  partName,
  tGeneral,
  tExams,
}: {
  exam: ExamDetail;
  appliedTemplate: ExamTemplate | undefined;
  partName: (code: string) => string;
  tGeneral: ReturnType<typeof useTranslations<"exams.general">>;
  tExams: ReturnType<typeof useTranslations<"exams">>;
}) {
  const shared = exam.timing === "SHARED";
  const branchCount = exam.parts.flatMap((part) => part.options).filter((option) => option.kind === "BRANCH").length;
  const pathsText = (() => {
    if (exam.scope === "PART") return exam.partCode ? partName(exam.partCode) : "—";
    const segments = [
      ...(exam.englishOption ? [tGeneral("facts.pathsEnglishOnly")] : []),
      ...(exam.combinationCount > 0 ? [tGeneral("facts.pathsCombinationsOnly", { count: exam.combinationCount })] : []),
      ...(branchCount > 0 ? [tGeneral("facts.pathsBranches", { count: branchCount })] : []),
    ];
    return segments.length > 0 ? segments.join(" + ") : tGeneral("facts.pathsFixed");
  })();

  return (
    <Card padding="md" className="flex flex-col gap-3">
      <h2 className="text-title font-semibold text-text">{tGeneral("factsTitle")}</h2>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-2.5 text-body">
        <Fact label={tGeneral("facts.status")}>
          <StatusChip tone={EXAM_STATUS_TONE[exam.status]}>
            {exam.status === "PUBLISHED" && exam.servingVersion !== null
              ? tExams("status.servingVersion", { version: exam.servingVersion })
              : tExams(`status.${exam.status}`)}
          </StatusChip>
        </Fact>
        <Fact label={tGeneral("facts.draft")}>
          {exam.hasUnpublishedChanges ? tGeneral("facts.draftYes") : tGeneral("facts.draftNo")}
        </Fact>
        <Fact label={tGeneral("facts.check")} wide={Boolean(exam.check.checkedAt)}>
          <span className="flex flex-col items-start gap-1">
            <StatusChip tone={EXAM_CHECK_TONE[exam.check.status]}>
              {exam.check.status === "INVALID"
                ? tExams("checkStatus.issues", { count: exam.check.issueCount })
                : tExams(`checkStatus.${exam.check.status}`)}
            </StatusChip>
            {exam.check.checkedAt && <FormattedDate value={exam.check.checkedAt} />}
          </span>
        </Fact>
        {yearsDiffer(exam) && (
          <Fact label={tGeneral("facts.yearsConfirmed")}>
            <StatusChip tone={exam.yearsConfirmed ? "success" : "warning"}>
              {exam.yearsConfirmed ? tGeneral("facts.yearsConfirmedYes") : tGeneral("facts.yearsConfirmedNo")}
            </StatusChip>
          </Fact>
        )}
        <Fact label={tGeneral("facts.template")} wide>
          <span className="flex flex-col">
            <span>{appliedTemplate?.name ?? tGeneral("facts.noTemplate")}</span>
            {exam.structureYear !== null && (
              <span className="text-caption font-normal text-text-muted">
                {tGeneral("facts.years", {
                  structureYear: String(exam.structureYear),
                  matrixYear: exam.matrixYear === null ? "—" : String(exam.matrixYear),
                })}
              </span>
            )}
          </span>
        </Fact>
        <Fact label={tGeneral("facts.structure")} wide>
          {tGeneral(shared ? "facts.structureValueShared" : "facts.structureValue", {
            parts: exam.parts.length,
            questions: exam.totalQuestions,
            minutes: exam.durationMinutes,
          })}
        </Fact>
        <Fact label={tGeneral("facts.maxScore")}>
          {exam.maxScore === null ? tGeneral("facts.maxScoreUnset") : tGeneral("facts.maxScoreValue", { count: exam.maxScore })}
        </Fact>
        <Fact label={tGeneral("facts.attempts")}>{tGeneral("facts.attemptsValue", { count: exam.attemptCount })}</Fact>
        {exam.hasEssay && <Fact label={tGeneral("facts.essay")} wide>{tGeneral("facts.essayValue")}</Fact>}
        <Fact label={tGeneral("facts.paths")} wide>{pathsText}</Fact>
        <Fact label={tGeneral("facts.updated")} wide>
          <span>
            <FormattedDate value={exam.updatedAt} /> · {exam.updatedBy.name}
          </span>
        </Fact>
      </dl>
    </Card>
  );
}

function Fact({ label, wide = false, children }: { label: string; wide?: boolean; children: ReactNode }) {
  return (
    <div className={cn("flex min-w-0 flex-col gap-0.5", wide && "col-span-2")}>
      <dt className="text-caption text-text-muted">{label}</dt>
      <dd className="m-0 font-medium text-text tabular-nums">{children}</dd>
    </div>
  );
}

function FormattedDate({ value }: { value: string }) {
  const format = useFormatter();
  return <time dateTime={value}>{format.dateTime(new Date(value), "dateTime")}</time>;
}

function FormSection({
  mode,
  step,
  title,
  description,
  children,
}: {
  mode: "create" | "edit";
  step: number;
  title: string;
  description?: ReactNode;
  children: ReactNode;
}) {
  const headingId = useId();
  return (
    <Card padding="md" className="sm:p-6">
      <section aria-labelledby={headingId} className="flex flex-col gap-4">
        <div className="flex items-start gap-3">
          {mode === "create" ? (
            <span
              aria-hidden
              className="grid size-7 shrink-0 place-items-center rounded-full bg-primary-50 text-body font-bold text-primary-text"
            >
              {step}
            </span>
          ) : (
            <span aria-hidden className="mt-1 h-5 w-1 shrink-0 rounded-full bg-primary-600" />
          )}
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
