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
import { ArrowRight, ChevronLeft, Info, Lock, RefreshCw, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useEffectEvent, useId, useMemo, useRef, useState, useSyncExternalStore, type ReactNode } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { RichTextEditor } from "@/components/rich-text";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import { apiErrorCode } from "@/features/content/api-error";
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
  subjectAudioParts,
  subjectPartOptions,
  templatePartAudio,
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
import { overviewRowLabel } from "../create-exam-row-label";
import { buildUpdateInfoRequest, examScope, examToFormValues, reloadPartRows } from "../create-exam-edit";
import { overviewRowsFromParts } from "../create-exam-overview-structure";
import { type PendingInfoSave, useExamInfoSave } from "../create-exam-save";
import { examTabHref } from "../editor-tabs";
import { useCreateExam, useExam, useExamStructure, useExamTemplates, useSuggestedExamCode } from "../queries";
import type { ExamDetail, ExamStructure, ExamTemplate } from "../types";
import { GenerateCodeButton, KindSelect, TemplateNotes, useExamInfoSchema, useKindLabel, usePartNames } from "./create-exam-fields";
import { CreateExamScopeFields } from "./create-exam-scope-fields";
import { CreateExamStepper } from "./create-exam-stepper";
import { type CreateExamIssue, CreateExamSummaryPanel, focusField } from "./create-exam-summary";
import { UnsavedChangesDialog, useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";
import { ExamOverviewTable } from "./exam-overview-table";
import { blockCountsLocked } from "./block-form";
import { ReloadStructureDialog } from "./reload-structure-dialog";

const EXAMS_HREF = "/exams";
/** Quyền tạo bài (cổng ở `app/(shell)/exams/new/layout.tsx`); chỉ để nêu trong thông báo khi máy chủ trả 403. */
const CREATE_PERMISSION = "EXAM.MODIFY";

/** Mã bài đang sửa (`/exams/new?exam=<id>`): `null` là tạo bài mới; `undefined` khi chưa đọc được (dựng trên máy chủ). */
function subscribeToLocation(onChange: () => void) {
  window.addEventListener("popstate", onChange);
  return () => window.removeEventListener("popstate", onChange);
}
function useEditingExamId(): string | null | undefined {
  return useSyncExternalStore(
    subscribeToLocation,
    () => new URLSearchParams(window.location.search).get("exam"),
    () => undefined,
  );
}

/** Bài đã tạo và cấu trúc đã lưu của nó, điền sẵn cho bước 1 ở chế độ sửa. */
interface ExistingExam {
  exam: ExamDetail;
  structure: ExamStructure;
}

/**
 * Trang Tạo bài thi, bước 1 (`/exams/new`, 2B mục 5, 15.1; TaoBaiThi2B): Thông tin bài thi, Phạm vi (kỳ thi, phạm vi,
 * môn, phần, `build_mode`), Cấu trúc tổng quan (bảng sửa ngay trên dòng, có tổng). Không chọn phiên bản cấu trúc ở bước
 * 1 (K8): bảng lấy số của mẫu mặc định, phiên bản đổi ở bước 2. Sửa một ô không nạp lại mẫu (R-09); đổi kỳ thi, phạm
 * vi, môn, phần hoặc `build_mode` thì nạp lại bảng và hỏi xác nhận khi bảng đã sửa. Tạo xong mở bước 2 (Cấu trúc).
 *
 * `/exams/new?exam=<id>` là bước 1 của bài đã tạo (đường quay lại từ bước Cấu trúc, UNI-2751): cùng màn, điền sẵn theo bài
 * và cấu trúc đã lưu; "Lưu và tiếp tục" ghi (cấu trúc rồi thông tin, như tab Thông tin chung) và về bước 2. Đổi kỳ thi,
 * phạm vi, phần hoặc môn thì hỏi nạp lại cấu trúc trước khi lưu.
 */
export function CreateExamPage() {
  const t = useTranslations("exams.create");
  const templatesQuery = useExamTemplates();
  const referenceQuery = useContentReference();
  const editingId = useEditingExamId();
  const forbidden = [templatesQuery, referenceQuery].some((query) => query.isError && getErrorStatus(query.error) === 403);

  let body: ReactNode;
  let showStepper = true;
  if (forbidden) {
    showStepper = false;
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
    showStepper = false;
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
  } else if (!referenceQuery.data || editingId === undefined) {
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
    const reference = referenceQuery.data;
    body = (
      <ExistingExamGate examId={editingId}>
        {(existing) => (
          <CreateExamForm
            key={existing?.exam.id ?? "new"}
            templates={templatesQuery.data ?? NO_TEMPLATES}
            templatesStatus={templatesStatus}
            onRetryTemplates={() => void templatesQuery.refetch()}
            reference={reference}
            existing={existing}
          />
        )}
      </ExistingExamGate>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <Link
          href={editingId ? examTabHref(editingId, "structure") : EXAMS_HREF}
          className={cn(
            "inline-flex w-fit items-center gap-1 rounded-sm text-body font-medium text-text-secondary hover:text-text",
            focusRing,
          )}
        >
          <ChevronLeft aria-hidden className="size-4" />
          {t(editingId ? "backToStructure" : "backToList")}
        </Link>
        <PageHeader title={t("title")} description={t("description")} />
        {showStepper && <CreateExamStepper />}
      </div>
      {body}
    </div>
  );
}

/**
 * Chế độ sửa (`?exam=<id>`): đọc bài và cấu trúc đã lưu rồi giao cho biểu mẫu; tạo mới (`examId = null`) giao ngay.
 * Bài và cấu trúc đã có thì giữ nguyên khi tải lại nền (biểu mẫu không dựng lại giữa chừng).
 */
function ExistingExamGate({ examId, children }: { examId: string | null; children: (existing: ExistingExam | undefined) => ReactNode }) {
  if (examId === null) return children(undefined);
  return <EditExamLoader examId={examId}>{children}</EditExamLoader>;
}

function EditExamLoader({ examId, children }: { examId: string; children: (existing: ExistingExam | undefined) => ReactNode }) {
  const t = useTranslations("exams.create");
  const examQuery = useExam(examId);
  const structureQuery = useExamStructure(examId);
  if (examQuery.data && structureQuery.data) return children({ exam: examQuery.data, structure: structureQuery.data });
  if (examQuery.isError || structureQuery.isError) {
    return (
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
                void examQuery.refetch();
                void structureQuery.refetch();
              }}
            >
              {t("retry")}
            </Button>
          }
        />
      </Card>
    );
  }
  return (
    <div className="flex flex-col gap-4" aria-busy="true">
      <Skeleton className="h-72" />
      <Skeleton className="h-56" />
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
  /** Bài đã tạo: bước 1 ở chế độ sửa, điền sẵn theo bài và cấu trúc đã lưu; bỏ trống là tạo bài mới. */
  existing?: ExistingExam;
}

function CreateExamForm({ templates, templatesStatus, onRetryTemplates, reference, existing }: CreateExamFormProps) {
  const t = useTranslations("exams.create");
  const tStep = useTranslations("exams.create.step1");
  const tOverview = useTranslations("exams.create.overview");
  const router = useRouter();
  const tGeneral = useTranslations("exams.general");
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
  const exam = existing?.exam;
  const savedScope = exam ? examScope(exam) : undefined;
  const baseValues = exam ? examToFormValues(exam) : undefined;
  /** Bài đã công bố: kỳ thi, phạm vi, phần, môn và cách dựng bài chỉ đọc (như tab Thông tin chung). */
  const lockScope = exam !== undefined && exam.servingVersion !== null;
  const appliedTemplate = exam ? templates.find((item) => item.code === exam.templateCode) : undefined;
  const fixedCounts = appliedTemplate?.fixedCounts === true;
  const countsLocked = exam ? fixedCounts || blockCountsLocked(appliedTemplate, exam.scope, appliedTemplate?.buildMode === "REVIEW") : false;
  const [confirmReplace, setConfirmReplace] = useState<PendingInfoSave | null>(null);
  const [saved, setSaved] = useState(false);

  /** Mẫu mặc định, dòng tổng quan và thời lượng, thang điểm của một lựa chọn (kỳ thi, phạm vi, môn, phần, `build_mode`). */
  const loadScope = useCallback(
    (selection: ScopeSelection): LoadedScope => {
      // Chế độ sửa, lựa chọn đúng như đã lưu: dòng dựng từ cấu trúc đã lưu (số đang có), không phải số của mẫu.
      if (existing && savedScope && sameSelection(selection, savedScope)) {
        return { selection, template: appliedTemplate, rows: overviewRowsFromParts(existing.structure.parts, { fixedCounts, countsLocked }) };
      }
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
    [existing, savedScope, appliedTemplate, fixedCounts, countsLocked, templates, kinds, subjectName, partLabel, kindLabel],
  );

  const [initial] = useState<LoadedScope>(() =>
    loadScope(
      savedScope ??
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
  const [liveMessage, setLiveMessage] = useState("");

  const initialDefaults = scopeDefaults(initial.template, kindOptionFor(kinds, initial.selection.kind), initial.selection.scope, initial.selection.buildMode);
  // Chữ người dùng gõ ở hai ô số dưới bảng; giá trị số của biểu mẫu chỉ là bản đã phân tích để kiểm tra (R1-08).
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
  const { control, register, handleSubmit, setValue, setError, resetField, getFieldState, formState } = form;
  const values = useWatch({ control }) as ExamInfoFormValues;

  // Chế độ sửa, lựa chọn vẫn như đã lưu: mẫu đã nạp của bài và số đang lưu; đổi lựa chọn thì như tạo mới (bảng xem trước của mẫu mới).
  const editingSaved = savedScope !== undefined && sameSelection(selection, savedScope);
  const previewOnly = exam !== undefined && !editingSaved;
  const template = useMemo(
    () => (editingSaved ? appliedTemplate : scopeTemplate(templates, kinds, selection)),
    [editingSaved, appliedTemplate, templates, kinds, selection],
  );
  const defaults = useMemo(
    () => scopeDefaults(template, kindOptionFor(kinds, selection.kind), selection.scope, selection.buildMode),
    [template, kinds, selection],
  );
  const showPoints = editingSaved ? rows.some((row) => row.kind === "part" && row.base.maxPoints !== null) : defaults.pointsByPart;
  // H27: mẫu khóa số câu (V-ACT toàn bài) — ô số câu chỉ đọc, cột tỷ lệ hiện. Các cột tỷ lệ chỉ của V-ACT: mẫu
  // `fixedCounts` có "Tỷ lệ trong phần" + "Tỷ lệ toàn bài", bài riêng có tiểu mục có "Tỷ lệ trong bài" (R-118, R-123,
  // TaoBaiThiVact2B); kỳ thi khác không có cột tỷ lệ.
  // H42: mẫu `fixedBlockCounts` khóa số câu của bài phạm vi `SUBJECT` (không khóa bài theo phần và bài ôn).
  const blockLocked = template?.fixedBlockCounts === true && selection.scope === "SUBJECT" && selection.buildMode !== "REVIEW";
  const lockedCounts = template?.fixedCounts === true || blockLocked;
  const vact = selection.kind === "VACT";
  const showPartPercent = lockedCounts && vact && (template?.parts.length ?? 0) > 1;
  const showPercents = showPartPercent || (vact && selection.scope === "PART" && (template?.parts.some((part) => (part.sections?.length ?? 0) > 0) ?? false));
  // H-SCA Tiếng Anh: môn tách phần theo audio (Nghe, Đọc); tên dòng phần theo điều kiện audio để đúng ngôn ngữ.
  const audioSplit = selection.subjectCode ? subjectAudioParts(templates, selection.kind, selection.subjectCode).length > 0 : false;
  const rowLabel = useCallback(
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
            const index = Number(partRow.key.slice(2));
            if (editingSaved && existing) {
              const part = existing.structure.parts[index];
              return part?.mode === "FIXED" ? part.sections : [];
            }
            return template?.parts[index]?.sections ?? [];
          },
          // H-SCA Tiếng Anh: tên Nghe, Đọc theo điều kiện audio của phần, không lấy tên tiếng Việt lưu trong mẫu.
          audioLabel:
            audioSplit && !editingSaved
              ? (partRow) => {
                  const audio = templatePartAudio(template?.parts[Number(partRow.key.slice(2))]);
                  return audio ? tStep(audio === "REQUIRED" ? "skillListening" : "skillReading") : undefined;
                }
              : undefined,
        },
        row,
      ),
    [partNames, lookup, vact, template, audioSplit, editingSaved, existing, tStep],
  );
  // H35, R-141: bài riêng của V-ACT, bài theo phần và bài ôn không có thời lượng mặc định (mẫu để trống) — lỗi đặt ở ô
  // thời lượng với câu của thiết kế thay cho câu "phải là số dương" chung. Theo mẫu và phạm vi, không theo mã kỳ thi.
  const durationError = (() => {
    const generic = formState.errors.sharedDuration?.message;
    if (!generic || defaults.sharedDuration > 0) return generic;
    if (selection.kind === "VACT" && selection.scope !== "FULL") return tStep("durationRequiredCustom", { kind: kindLabel(selection.kind) });
    if (selection.scope === "PART") return tStep("durationRequiredPart");
    if (selection.scope === "SUBJECT" && selection.buildMode === "REVIEW") return tStep("durationRequiredReview");
    // F-1: bài cả môn từ mẫu để trống thời lượng (V-SAT Ngữ văn) và ô còn trống — câu của bản vẽ. Giá trị đã gõ mà sai
    // (chữ, 0, số âm, số thập phân) giữ câu số dương chung.
    if (selection.scope === "SUBJECT" && template && template.durationMinutes === null && sharedText.trim() === "") return tStep("durationRequiredSubject");
    return generic;
  })();
  // G2c "Trạng thái khác": gợi ý dưới ô thời lượng theo nguồn của giá trị — mẫu điền sẵn, bài theo phần tự nhập
  // (D24: không lấy thời lượng cả môn), mẫu để trống (D25/F-1).
  const durationHint = (() => {
    if (values.timing !== "SHARED" || editingSaved) return null;
    if (defaults.sharedDuration > 0) return tStep("durationHintPrefilled");
    if (selection.scope === "PART" && template) return tStep("durationHintPartCut");
    if (selection.scope === "SUBJECT" && template && template.durationMinutes === null) return tStep("durationHintEmpty");
    return null;
  })();
  // Mã gợi ý theo kỳ thi, phạm vi, phần hoặc môn; người dùng đã sửa mã thì giữ mã của họ.
  const suggestion = useSuggestedExamCode(
    {
      kind: selection.kind,
      scope: selection.scope,
      partCode: selection.scope === "PART" ? selection.partCode : null,
      subjectCode: selection.scope === "SUBJECT" ? selection.subjectCode || null : null,
    },
    !exam,
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
  const baselineShared = editingSaved && baseValues ? baseValues.sharedDuration : defaults.sharedDuration;
  const baselineMaxScore = editingSaved && baseValues ? baseValues.maxScore : defaults.maxScore;
  const tableEdited =
    rowsDifferFromTemplate(rows) ||
    (values.timing === "SHARED" && values.sharedDuration !== baselineShared) ||
    (!showPoints && values.maxScore !== baselineMaxScore);
  /** Chế độ sửa: bảng đang hiện số đã lưu của bài và người dùng đã sửa (không tính bảng xem trước của phạm vi mới). */
  const overviewChanged = editingSaved && rowsDifferFromTemplate(rows);
  const guard = useUnsavedChangesGuard((formState.isDirty || tableEdited) && !createExam.isSuccess && !saved);
  const { update, structureUpdate, submitError, setSubmitError, save, saveOverviewThenInfo } = useExamInfoSave({
    exam,
    structure: existing?.structure,
    rows,
    formDirty: formState.isDirty,
    lockScope,
    setError,
    onSaved: (message) => {
      setConfirmReplace(null);
      setLiveMessage(message);
      setSaved(true);
      if (exam) router.push(examTabHref(exam.id, "structure"));
    },
    onFailed: () => setConfirmReplace(null),
  });

  /** Nạp lại lựa chọn mới; `markDirty = false` khi nạp vì mẫu vừa tải về (người dùng chưa sửa gì). */
  const applyScope = (next: ScopeSelection, markDirty = true) => {
    const loaded = loadScope(next);
    const nextDefaults = scopeDefaults(loaded.template, kindOptionFor(kinds, next.kind), next.scope, next.buildMode);
    // Chế độ sửa: về đúng lựa chọn đã lưu thì lấy lại giá trị đang lưu của bài, không phải mặc định của mẫu.
    const nextValues = baseValues && savedScope && sameSelection(next, savedScope) ? baseValues : { ...nextDefaults, yearsConfirmed: false };
    const dirty = markDirty ? ({ shouldDirty: true } as const) : {};
    setSelection(next);
    setRows(loaded.rows);
    setSharedText(formatShared(nextValues.sharedDuration));
    setMaxScoreText(formatScore(nextValues.maxScore));
    setValue("kind", next.kind, dirty);
    setValue("scope", next.scope, dirty);
    setValue("buildMode", next.buildMode, dirty);
    setValue("partCode", next.partCode, { ...dirty, shouldValidate: formState.isSubmitted });
    setValue("subjectCode", next.subjectCode, { ...dirty, shouldValidate: formState.isSubmitted });
    setValue("timing", nextValues.timing, dirty);
    setValue("sharedDuration", nextValues.sharedDuration, dirty);
    setValue("maxScore", nextValues.maxScore, dirty);
    setValue("yearsConfirmed", nextValues.yearsConfirmed, dirty);
    setPendingSelection(null);
    setLiveMessage(tStep("loaded", { name: selectionLabel(next) }));
  };

  const requestScope = (change: Partial<ScopeSelection>) => {
    // Chế độ sửa: máy chủ giữ cách dựng bài đã lưu khi phạm vi mới là Theo môn (như tab Thông tin chung).
    const next = resolveScope(templates, kinds, reference.subjects, selection, exam ? { ...change, buildMode: exam.buildMode } : change);
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
    const next = resolveScope(templates, kinds, reference.subjects, selection, exam ? { buildMode: exam.buildMode } : {});
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
    error?.message
      ? [{ path, field: fieldLabel(path), message: path === "sharedDuration" ? (durationError ?? String(error.message)) : String(error.message) }]
      : [],
  );
  const rowIssueItems: CreateExamIssue[] = overviewIssues.map((issue) => {
    const row = rows.find((item) => item.key === issue.rowKey);
    return {
      path: `${issue.rowKey}.${issue.field}`,
      field: row ? (rowLabel(row) ?? (row.kind === "section" ? subjectName(row.subjectCode) : row.label)) : tOverview("total"),
      message: issueText(issue),
    };
  });
  const issues = [...formIssues, ...rowIssueItems];

  // ------------------------------------------------------------ gửi

  const onSubmit = handleSubmit((submitted) => {
    setSubmitError(null);
    // Chế độ sửa chỉ chặn khi chính bảng của bài bị sửa sai; bảng xem trước của phạm vi mới do máy chủ nạp lại.
    const blocking = overviewIssues.length > 0 && (exam === undefined || overviewChanged);
    if (blocking) {
      const first = overviewIssues[0]!;
      focusField(`${first.rowKey}.${first.field}`);
      return;
    }
    if (exam && savedScope) {
      const changed = !sameSelection(savedScope, selection);
      const request = buildUpdateInfoRequest({
        exam,
        submitted,
        kinds,
        appliedTemplate,
        changed,
        maxScore: changed && showPoints ? (totals.maxPoints ?? submitted.maxScore) : submitted.maxScore,
      });
      const pending: PendingInfoSave = {
        request,
        parts: changed
          ? reloadPartRows({ templates, kinds, combinations: reference.combinations, partName: partNames.partName, subjectName, next: selection })
          : [],
      };
      if (changed) setConfirmReplace(pending);
      else if (overviewChanged) void saveOverviewThenInfo(pending);
      else if (formState.isDirty) save(pending);
      else router.push(examTabHref(exam.id, "structure"));
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
      onSuccess: (created) => {
        const message = t("success", { code: created.code });
        notify.success(message);
        setLiveMessage(message);
        router.push(examTabHref(created.id, "structure"));
      },
      onError: (error) => {
        const status = getErrorStatus(error);
        if (status === 409) {
          const code = submitted.code.trim().toUpperCase();
          setTakenCodes((current) => new Set([...current, code]));
          setError("code", { message: t("errors.codeTaken") }, { shouldFocus: true });
        } else if (status === 422) {
          // Lỗi nghiệp vụ 422 hiện theo mã (`ExamErrorCodes`), có bản dịch; mã lạ thì câu chung. Không in thân lỗi của máy chủ.
          const reason = apiErrorCode(error);
          setSubmitError(reason && t.has(`errors.codes.${reason}`) ? t(`errors.codes.${reason}`) : t("errors.structure"));
        } else {
          setSubmitError(t("errors.generic"));
        }
      },
    });
  });

  const submitButton = (
    <Button type="submit" loading={createExam.isPending || update.isPending || structureUpdate.isPending} disabled={templatesStatus === "loading"}>
      {t("submit")}
      <ArrowRight aria-hidden className="size-4" />
    </Button>
  );
  const cancelButton = (
    <Button asChild variant="secondary">
      <Link href={exam ? examTabHref(exam.id, "structure") : EXAMS_HREF}>{t("cancel")}</Link>
    </Button>
  );

  // Như TaoBaiThi2B: Hủy / Lưu và tiếp tục nằm ngay dưới bảng tổng quan, Tóm tắt đứng bên phải khi đủ rộng, hẹp thì xuống
  // dưới các nút.
  return (
    <div className="flex min-w-0 flex-col gap-5">
      {exam && <p className="text-body text-text-secondary">{t("editing", { code: exam.code })}</p>}
      <div className="flex flex-wrap items-start gap-6">
        <form
          id={formId}
          noValidate
          aria-label={t("formAriaLabel")}
          onSubmit={(event) => void onSubmit(event)}
          className="flex min-w-0 flex-[999_1_640px] flex-col gap-5"
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
                <KindSelect kinds={kinds} value={selection.kind} onChange={(kind) => requestScope({ kind })} disabled={lockScope} />
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
              disabled={lockScope}
              // Chế độ sửa: cách dựng bài chỉ đọc (máy chủ chưa nhận `buildMode` khi sửa); bài đã công bố nêu lý do.
              buildModeLocked={exam !== undefined}
              buildModeHint={lockScope ? tStep("buildModePublished") : undefined}
              onChange={requestScope}
            />
            {lockScope && (
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
            <TemplateNotes template={template} />
            {previewOnly && <p className="text-body text-text-secondary">{tGeneral("overview.pendingDescription")}</p>}
            {lockScope && <p className="text-body text-text-secondary">{tGeneral("overview.publishedReadOnly")}</p>}
            {lockedCounts && (
              <p className="inline-flex w-fit items-center gap-1.5 rounded-full bg-subtle px-3 py-1 text-caption text-text-secondary">
                <Lock aria-hidden className="size-3.5" />
                {tOverview("lockedCounts")}
              </p>
            )}
            {blockLocked && <p className="text-body text-text-secondary">{tOverview("lockedBlockCountsNote")}</p>}
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
                readOnly={lockScope || previewOnly}
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

          {submitError && (
            <p role="alert" className="rounded-md border border-danger/40 bg-danger-bg px-3 py-2 text-body text-danger">
              {submitError}
            </p>
          )}

          <div className="flex flex-wrap justify-end gap-2">
            {cancelButton}
            {submitButton}
          </div>
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
      </div>

      <UnsavedChangesDialog guard={guard} />
      <ReloadStructureDialog
        open={confirmReplace !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmReplace(null);
        }}
        title={tGeneral("replaceTitle")}
        description={tGeneral("replaceDescription")}
        parts={confirmReplace?.parts ?? []}
        confirmLabel={tGeneral("replaceConfirm")}
        loading={update.isPending}
        onConfirm={() => {
          if (confirmReplace) save(confirmReplace);
        }}
      />
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
