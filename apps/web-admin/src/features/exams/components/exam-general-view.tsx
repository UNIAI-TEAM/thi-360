"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, Card, EmptyState, LiveStatus, notify, Skeleton, StatusChip } from "@thi360/ui";
import { Lock, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useId, useState, type ReactNode } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { getErrorStatus } from "@/lib/api/query-client";
import { useExam, useExamStructure, useExamTemplates, useUpdateExamInfo, useUpdateExamStructure } from "../queries";
import { EXAM_CHECK_TONE, EXAM_STATUS_TONE } from "../status";
import type { ExamDetail, ExamStructure, ExamTemplate, UpdateExamInfoRequest } from "../types";
import {
  combinationsForKind,
  type ExamInfoFormValues,
  initialParts,
  kindOptionFor,
  latestTemplate,
  resolveScope,
  sameSelection,
  scopeDefaults,
  scopeTemplate,
  type ScopeSelection,
  yearsDiffer,
} from "../create-exam-schema";
import { type OverviewRow, overviewRowsFromTemplate, rowsDifferFromTemplate, validateOverview } from "../create-exam-overview";
import { applyOverviewToParts, overviewRowsFromParts } from "../create-exam-overview-structure";
import { overviewRowLabel } from "../create-exam-row-label";
import { blockCountsLocked } from "./block-form";
import { ExamInfoFields, useExamInfoSchema, usePartNames } from "./create-exam-fields";
import { focusField } from "./create-exam-summary";
import { ExamOverviewTable } from "./exam-overview-table";
import { ReloadStructureDialog, type ReloadPartRow } from "./reload-structure-dialog";
import { UnsavedChangesDialog, useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";
import { ExamsApiError } from "../review-api";

/** Trường của máy chủ (`fieldErrors[].field`) tới ô nhập của biểu mẫu Thông tin chung. */
const FORM_FIELD_OF: Record<string, keyof ExamInfoFormValues> = {
  name: "name",
  code: "code",
  maxScore: "maxScore",
  durationMinutes: "sharedDuration",
  description: "description",
  instructions: "instructions",
};

interface PendingInfoSave {
  request: UpdateExamInfoRequest;
  /** Các phần sẽ được nạp lại theo mẫu mới nhất của phạm vi mới (hiện ở hộp xác nhận). */
  parts: ReloadPartRow[];
}

type OverviewField = "questionCount" | "durationMinutes" | "maxPoints";

/** Bảng xem trước phạm vi mới chỉ đọc: ô lỗi của bảng đã lưu không đi theo sang đó. */
const NO_CELL_ISSUES: ReadonlyMap<string, { questionCount?: string }> = new Map();

/** Ô của bảng tổng quan người dùng đã sửa, theo khóa dòng; phủ lên số đang lưu để bảng đổi theo cấu trúc mới tải về. */
type CellEdits = Record<string, Partial<Record<OverviewField, string>>>;

/** Bài ôn nhận ra qua `buildMode` máy chủ trả (không đoán từ mẫu): thang của bài ôn được để trống. */
function toFormValues(exam: ExamDetail): ExamInfoFormValues {
  return {
    name: exam.name,
    code: exam.code,
    kind: exam.kind,
    scope: exam.scope,
    partCode: exam.partCode ?? "",
    subjectCode: exam.subjectCode ?? "",
    timing: exam.timing,
    sharedDuration: exam.timing === "SHARED" ? exam.durationMinutes : 0,
    maxScore: exam.maxScore,
    buildMode: exam.scope === "SUBJECT" ? exam.buildMode : undefined,
    yearsConfirmed: exam.yearsConfirmed,
    description: exam.description ?? "",
    instructions: exam.instructions ?? "",
  };
}

function examScope(exam: ExamDetail): ScopeSelection {
  return {
    kind: exam.kind,
    scope: exam.scope,
    partCode: exam.partCode ?? "",
    subjectCode: exam.subjectCode ?? "",
    buildMode: exam.scope === "SUBJECT" ? exam.buildMode : undefined,
  };
}

/**
 * Tab Thông tin chung (chưa có bản vẽ riêng; trường theo `TaoBaiThi`, UniNote PBI-04 mục 4; spec 7 kỳ thi). Sửa tên, mã,
 * mô tả, hướng dẫn làm bài, thời lượng chung, thang điểm, xác nhận năm cấu trúc và năm ma trận; đổi kỳ thi, phạm vi,
 * phần hoặc môn thì nạp lại cấu trúc theo mẫu mới nhất sau khi xác nhận (hộp thoại liệt kê các phần sẽ nạp lại), chỉ khi
 * bài chưa công bố. Có "Cấu trúc tổng quan" như bước 1: số câu, thời lượng, điểm của từng phần lấy từ cấu trúc đã lưu,
 * sửa ngay trên dòng; lưu thì ghi cấu trúc trước, thông tin sau. Phiên bản năm chọn ở bước Cấu trúc. Bên phải là các
 * thông tin chỉ để đọc.
 */
export function ExamGeneralView({ examId }: { examId: string }) {
  const t = useTranslations("exams.general");
  const examQuery = useExam(examId);
  const referenceQuery = useContentReference();
  const templatesQuery = useExamTemplates();
  const structureQuery = useExamStructure(examId);
  const [liveMessage, setLiveMessage] = useState("");
  // Lưu cấu trúc xong thì bài đổi `updatedAt` và biểu mẫu dựng lại; giữ khóa cũ cho tới khi phần thông tin cũng xong, để
  // thông tin đã gõ không mất nếu bước lưu thông tin lỗi.
  const [heldKey, setHeldKey] = useState<string | null>(null);
  if (!examQuery.data) return null;
  if (referenceQuery.isError || templatesQuery.isError) {
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
                void referenceQuery.refetch();
                void templatesQuery.refetch();
              }}
            >
              {t("retry")}
            </Button>
          }
        />
      </Card>
    );
  }
  if (!referenceQuery.data || !templatesQuery.data) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-72" />
      </div>
    );
  }
  const formKey = `${examQuery.data.id}-${examQuery.data.updatedAt}`;
  return (
    <>
      {/* Khóa theo lần cập nhật: lưu xong biểu mẫu nạp lại giá trị mới, hết "chưa lưu". */}
      <ExamGeneralForm
        key={heldKey ?? formKey}
        exam={examQuery.data}
        reference={referenceQuery.data}
        templates={templatesQuery.data}
        structure={structureQuery.data}
        structureFailed={structureQuery.isError}
        onRetryStructure={() => void structureQuery.refetch()}
        onHold={(hold) => setHeldKey((held) => (hold ? (held ?? formKey) : null))}
        onAnnounce={setLiveMessage}
      />
      <LiveStatus message={liveMessage} />
    </>
  );
}

function ExamGeneralForm({
  exam,
  reference,
  templates,
  structure,
  structureFailed,
  onRetryStructure,
  onHold,
  onAnnounce,
}: {
  exam: ExamDetail;
  reference: ContentReference;
  templates: readonly ExamTemplate[];
  /** Cấu trúc đã lưu (`GET /exams/{id}/structure`); chưa về thì bảng tổng quan hiện khung chờ. */
  structure: ExamStructure | undefined;
  structureFailed: boolean;
  onRetryStructure: () => void;
  /** Giữ (hoặc thả) khóa của biểu mẫu để lưu cấu trúc không làm dựng lại biểu mẫu giữa chừng. */
  onHold: (hold: boolean) => void;
  onAnnounce: (message: string) => void;
}) {
  const t = useTranslations("exams.general");
  const tExams = useTranslations("exams");
  const tOverview = useTranslations("exams.create.overview");
  const overviewHeadingId = useId();
  const kinds = reference.examKinds;
  const lookup = useReferenceLookup(reference);
  const schema = useExamInfoSchema(undefined, kinds);
  const partNames = usePartNames();
  const update = useUpdateExamInfo(exam.id);
  const structureUpdate = useUpdateExamStructure(exam.id);
  const [confirmReplace, setConfirmReplace] = useState<PendingInfoSave | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [cellEdits, setCellEdits] = useState<CellEdits>({});
  /** Bài đã công bố: phạm vi và bảng tổng quan chỉ đọc (AC-6); sửa số liệu ở bước Cấu trúc, trên bản nháp. */
  /** Bài đã công bố: phạm vi và bảng tổng quan chỉ đọc (AC-6); sửa số liệu ở bước Cấu trúc, trên bản nháp. */
  const lockScope = exam.servingVersion !== null;

  const form = useForm<ExamInfoFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: toFormValues(exam),
  });
  const { control, register, handleSubmit, setValue, getValues, setError, reset, formState } = form;
  const values = useWatch({ control }) as ExamInfoFormValues;
  const selection: ScopeSelection = {
    kind: values.kind,
    scope: values.scope,
    partCode: values.partCode,
    subjectCode: values.subjectCode,
    buildMode: values.buildMode,
  };

  const scopeEdited = !sameSelection(examScope(exam), selection);
  const showPreview = !lockScope && scopeEdited;
  const appliedTemplate = templates.find((item) => item.code === exam.templateCode);
  // Chọn phạm vi khác thì ghi chú theo mẫu mới nhất của phạm vi đó; chưa đổi thì theo mẫu đã nạp của bài.
  const template = scopeEdited ? latestTemplate(templates, selection) : appliedTemplate;
  const shared = exam.timing === "SHARED";

  // Khóa số câu theo cùng luật với bước Cấu trúc: mẫu `fixedCounts` (V-ACT toàn bài) hoặc mẫu `fixedBlockCounts` của bài Theo môn.
  const fixedCounts = appliedTemplate?.fixedCounts === true;
  const countsLocked = fixedCounts || blockCountsLocked(appliedTemplate, exam.scope, appliedTemplate?.buildMode === "REVIEW");
  const savedRows = structure ? overviewRowsFromParts(structure.parts, { fixedCounts, countsLocked }) : [];
  const rows = savedRows.map((row) => ({ ...row, ...cellEdits[row.key] }));
  const overviewIssues = validateOverview(rows);
  const cellIssues = new Map<string, { questionCount?: string; durationMinutes?: string; maxPoints?: string }>();
  for (const issue of overviewIssues) {
    cellIssues.set(issue.rowKey, { ...cellIssues.get(issue.rowKey), [issue.field]: tOverview(`errors.${issue.code}`) });
  }
  // Đổi phạm vi đang chờ xác nhận thì bảng hiện số của mẫu mới (chỉ đọc) và ô đã sửa không đi theo lần lưu này.
  const overviewChanged = !showPreview && rowsDifferFromTemplate(rows);
  const guard = useUnsavedChangesGuard(formState.isDirty || overviewChanged);

  /** Tên dòng của bảng đã lưu: cùng quy tắc với bước 1 (V-ACT tách tên theo năng lực), mục lấy từ cấu trúc đã lưu. */
  const rowLabel = (row: OverviewRow): string | undefined =>
    overviewRowLabel(
      {
        vact: exam.kind === "VACT",
        partName: partNames.partName,
        subjectOf: (code) => {
          const name = lookup.subject(code);
          return name === code ? undefined : name;
        },
        partSections: (partRow) => {
          const part = structure?.parts[Number(partRow.key.slice(2))];
          return part?.mode === "FIXED" ? part.sections : [];
        },
      },
      row,
    );

  const branchCount = exam.parts.flatMap((part) => part.options).filter((option) => option.kind === "BRANCH").length;

  /** Các phần mà phạm vi mới sẽ nạp lại: cùng cách dựng với máy chủ (mẫu mới nhất, hoặc phần trống của môn chưa có mẫu). */
  const reloadRows = (next: ScopeSelection): ReloadPartRow[] => {
    const nextTemplate = latestTemplate(templates, next);
    const sharedNext = (nextTemplate?.timing ?? kindOptionFor(kinds, next.kind)?.timing) === "SHARED";
    return initialParts({
      templates,
      kind: next.kind,
      scope: next.scope,
      partCode: next.partCode,
      combinations: combinationsForKind(reference.combinations, next.kind),
      scopeName: partNames.partName,
      template: next.scope === "PART" ? undefined : (nextTemplate ?? null),
      subjectCode: next.subjectCode,
      subjectName: lookup.subject,
    }).map((part) => ({
      name: part.name,
      questionCount: part.questionCount,
      durationMinutes: part.durationMinutes,
      shared: sharedNext,
    }));
  };

  /** `recheck`: chỉ ghi cấu trúc mà kết quả kiểm tra trước đó đã cũ, nên báo cần kiểm tra lại (như bước Cấu trúc). */
  const done = (recheck = false) => {
    setConfirmReplace(null);
    const message = t(recheck ? "overview.savedRecheck" : "saved");
    notify.success(message);
    onAnnounce(message);
  };

  /**
   * Lưu Thông tin chung. Đổi phạm vi thì máy chủ nạp mẫu mới nhất khớp; phiên bản năm khác chọn ở bước Cấu trúc.
   * `structureSaved`: số liệu của bảng tổng quan đã lưu trước đó, nên lỗi ở đây nói rõ phần nào đã lưu, phần nào chưa.
   */
  const save = ({ request }: PendingInfoSave, structureSaved = false) => {
    setSubmitError(null);
    update.mutate(request, {
      onSuccess: () => {
        onHold(false);
        done();
      },
      onError: (error) => {
        setConfirmReplace(null);
        const status = getErrorStatus(error);
        if (status === 409 && !lockScope) {
          setError("code", { message: tExams("create.errors.codeTaken") }, { shouldFocus: true });
        } else if (status === 412) {
          setSubmitError(t("saveConflict"));
        } else if (status === 422) {
          // Lỗi theo trường gắn vào ô nhập tương ứng; không gắn được thì báo chung (không in chữ của máy chủ).
          const fields = (error instanceof ExamsApiError ? error.fieldErrors : []).flatMap(
            (item) => FORM_FIELD_OF[item.field ?? ""] ?? [],
          );
          if (fields.length === 0) setSubmitError(t("saveInvalid"));
          fields.forEach((field, index) => setError(field, { message: t("fieldInvalid") }, { shouldFocus: index === 0 }));
        } else {
          setSubmitError(t("saveError"));
        }
        if (structureSaved) setSubmitError(t("overview.infoNotSaved"));
      },
    });
  };

  /**
   * Số liệu bảng tổng quan đã sửa: ghi cấu trúc TRƯỚC (cấu trúc đã đọc, chỉ đổi các số đó, `expectedRevision` của cấu
   * trúc). Lỗi thì không gửi gì khác và giữ nguyên giá trị người dùng đã gõ; xong mới ghi thông tin chung (chỉ khi biểu
   * mẫu có thay đổi) bằng revision mới của bài.
   */
  const saveOverviewThenInfo = async (pending: PendingInfoSave) => {
    if (!structure) return;
    setSubmitError(null);
    onHold(true);
    let saved: ExamDetail;
    try {
      saved = await structureUpdate.mutateAsync({
        parts: applyOverviewToParts(structure.parts, rows),
        examQuotas: structure.examQuotas ?? [],
        expectedRevision: structure.revision,
      });
    } catch (error) {
      onHold(false);
      const status = getErrorStatus(error);
      setSubmitError(status === 412 ? t("saveConflict") : status === 422 ? tExams("structure.saveInvalid") : tExams("structure.saveError"));
      return;
    }
    setCellEdits({});
    if (!formState.isDirty) {
      onHold(false);
      done(exam.check.status !== "UNCHECKED");
      return;
    }
    save({ ...pending, request: { ...pending.request, expectedRevision: saved.revision } }, true);
  };

  const onSubmit = handleSubmit((submitted) => {
    if (overviewChanged && overviewIssues.length > 0) {
      const first = overviewIssues[0]!;
      focusField(`${first.rowKey}.${first.field}`);
      return;
    }
    const next: ScopeSelection = {
      kind: submitted.kind,
      scope: submitted.scope,
      partCode: submitted.partCode,
      subjectCode: submitted.subjectCode,
      buildMode: submitted.buildMode,
    };
    const changed = !sameSelection(examScope(exam), next);
    const request: UpdateExamInfoRequest = {
      name: submitted.name,
      code: submitted.code,
      kind: submitted.kind,
      scope: submitted.scope,
      partCode: submitted.scope === "PART" ? submitted.partCode : null,
      subjectCode: submitted.scope === "SUBJECT" ? submitted.subjectCode : null,
      durationMinutes: submitted.timing === "SHARED" ? submitted.sharedDuration : null,
      maxScore: submitted.maxScore,
      yearsConfirmed: yearsDiffer(appliedTemplate) ? submitted.yearsConfirmed : true,
      description: submitted.description.trim() || null,
      instructions: submitted.instructions.trim() || null,
      confirmReplace: changed,
      expectedRevision: exam.revision,
    };
    const pending: PendingInfoSave = { request, parts: changed ? reloadRows(next) : [] };
    if (changed) setConfirmReplace(pending);
    else if (overviewChanged) void saveOverviewThenInfo(pending);
    else save(pending);
  });

  /** Đổi phạm vi: về đúng phạm vi đang lưu thì lấy lại giá trị đang lưu, không thì giá trị của mẫu mới nhất. */
  const onScopeChange = (change: Partial<ScopeSelection>) => {
    const current: ScopeSelection = {
      kind: getValues("kind"),
      scope: getValues("scope"),
      partCode: getValues("partCode"),
      subjectCode: getValues("subjectCode"),
      buildMode: getValues("buildMode"),
    };
    // Thông tin chung không có ô chọn chế độ dựng; máy chủ giữ `exam.buildMode` khi phạm vi mới là Theo môn, còn lại STANDARD.
    const scope = resolveScope(templates, kinds, reference.subjects, current, { ...change, buildMode: exam.buildMode });
    const dirty = { shouldDirty: true } as const;
    setValue("kind", scope.kind, dirty);
    setValue("scope", scope.scope, dirty);
    setValue("buildMode", scope.buildMode, dirty);
    setValue("partCode", scope.partCode, { ...dirty, shouldValidate: formState.isSubmitted });
    setValue("subjectCode", scope.subjectCode, { ...dirty, shouldValidate: formState.isSubmitted });
    const back = sameSelection(scope, examScope(exam));
    const defaults = back
      ? toFormValues(exam)
      : { ...scopeDefaults(latestTemplate(templates, scope), kindOptionFor(kinds, scope.kind), scope.scope, scope.buildMode), yearsConfirmed: false };
    setValue("timing", defaults.timing, dirty);
    setValue("sharedDuration", defaults.sharedDuration, dirty);
    setValue("maxScore", defaults.maxScore, dirty);
    setValue("yearsConfirmed", defaults.yearsConfirmed, dirty);
  };

  const onCellChange = (key: string, field: OverviewField, text: string) => {
    setCellEdits((current) => ({ ...current, [key]: { ...current[key], [field]: text } }));
  };

  const pathsText = (() => {
    if (exam.scope === "PART") return exam.partCode ? partNames.partName(exam.partCode) : "—";
    const segments = [
      ...(exam.englishOption ? [t("facts.pathsEnglishOnly")] : []),
      ...(exam.combinationCount > 0 ? [t("facts.pathsCombinationsOnly", { count: exam.combinationCount })] : []),
      ...(branchCount > 0 ? [t("facts.pathsBranches", { count: branchCount })] : []),
    ];
    return segments.length > 0 ? segments.join(" + ") : t("facts.pathsFixed");
  })();

  /** Số của mẫu của phạm vi đang chọn (chờ xác nhận nạp lại), chỉ đọc; cùng quy tắc chọn mẫu và cắt phần với bước 1. */
  const pendingTemplate = showPreview ? scopeTemplate(templates, kinds, selection) : undefined;
  const pendingRows = showPreview ? overviewRowsFromTemplate(pendingTemplate, selection.scope, selection.partCode) : [];

  const overviewBody = (() => {
    if (showPreview) {
      if (pendingRows.length === 0) return <p className="text-body text-text-secondary">{t("overview.pendingEmpty")}</p>;
      return (
        <ExamOverviewTable
          readOnly
          rows={pendingRows}
          timing={pendingTemplate?.timing ?? kindOptionFor(kinds, selection.kind)?.timing ?? "PER_PART"}
          showPoints={pendingRows.some((row) => row.kind === "part" && row.maxPoints !== "")}
          sharedTotal={String(pendingTemplate?.durationMinutes ?? "")}
          onCellChange={onCellChange}
          issues={NO_CELL_ISSUES}
          subjectName={lookup.subject}
        />
      );
    }
    if (structureFailed && !structure) {
      return (
        <div
          role="alert"
          className="flex flex-col gap-3 rounded-lg border border-danger/40 bg-danger-bg p-3 sm:flex-row sm:items-center sm:justify-between"
        >
          <p className="text-body text-text-secondary">
            <strong className="font-semibold text-danger">{t("overview.loadErrorTitle")}</strong> {t("overview.loadErrorDescription")}
          </p>
          <Button type="button" variant="secondary" size="sm" onClick={onRetryStructure}>
            {t("retry")}
          </Button>
        </div>
      );
    }
    if (!structure) {
      return (
        <div aria-busy="true" className="flex flex-col gap-2">
          <Skeleton className="h-10" />
          <Skeleton className="h-10 w-3/4" />
          <span className="sr-only">{tOverview("loading")}</span>
        </div>
      );
    }
    if (structure.parts.length === 0) return <p className="text-body text-text-secondary">{t("overview.empty")}</p>;
    return (
      <ExamOverviewTable
        readOnly={lockScope}
        rows={rows}
        timing={exam.timing}
        showPoints={rows.some((row) => row.kind === "part" && row.base.maxPoints !== null)}
        sharedTotal={String(values.sharedDuration ?? "")}
        onCellChange={onCellChange}
        issues={cellIssues}
        subjectName={lookup.subject}
        rowLabel={rowLabel}
      />
    );
  })();

  return (
    <div className="@container flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 @4xl:grid-cols-[minmax(0,1fr)_20rem]">
        <Card padding="md" className="flex flex-col gap-4 sm:p-6">
          <h2 className="text-h3 font-bold text-text">{t("formTitle")}</h2>
          <form noValidate onSubmit={(event) => void onSubmit(event)} aria-label={t("formTitle")} className="flex flex-col gap-4">
            <ExamInfoFields
              control={control}
              register={register}
              errors={formState.errors}
              values={values}
              kinds={kinds}
              subjects={reference.subjects}
              templates={templates}
              template={template}
              partMinutes={shared ? 0 : exam.durationMinutes}
              lockScope={lockScope}
              lockScopeHint={t("scopeLocked")}
              onScopeChange={onScopeChange}
            />
            <section aria-labelledby={overviewHeadingId} className="flex flex-col gap-3 rounded-lg border border-border p-4">
              <div className="flex flex-col gap-1">
                <h3 id={overviewHeadingId} className="text-title font-semibold text-text">
                  {tOverview("title")}
                </h3>
                <p className="text-body text-text-muted">{showPreview ? t("overview.pendingDescription") : lockScope ? t("overview.publishedReadOnly") : t("overview.description")}</p>
              </div>
              {countsLocked && !showPreview && (
                <p className="inline-flex w-fit items-center gap-1.5 rounded-full bg-subtle px-3 py-1 text-caption text-text-secondary">
                  <Lock aria-hidden className="size-3.5" />
                  {tOverview("lockedCounts")}
                </p>
              )}
              {overviewBody}
            </section>
            {submitError && (
              <p role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-body text-danger">
                {submitError}
              </p>
            )}
            <div className="flex flex-wrap justify-end gap-3">
              <Button
                type="button"
                variant="secondary"
                disabled={(!formState.isDirty && !overviewChanged) || update.isPending || structureUpdate.isPending}
                onClick={() => {
                  reset(toFormValues(exam));
                  setCellEdits({});
                  setSubmitError(null);
                  onHold(false);
                }}
              >
                {t("discard")}
              </Button>
              <Button type="submit" loading={update.isPending || structureUpdate.isPending}>
                {t("save")}
              </Button>
            </div>
          </form>
        </Card>

        <Card padding="md" className="flex flex-col gap-3 self-start">
          <h2 className="text-title font-semibold text-text">{t("factsTitle")}</h2>
          <dl className="flex flex-col gap-3 text-body">
            <Fact label={t("facts.status")}>
              <StatusChip tone={EXAM_STATUS_TONE[exam.status]}>
                {exam.status === "PUBLISHED" && exam.servingVersion !== null
                  ? tExams("status.servingVersion", { version: exam.servingVersion })
                  : tExams(`status.${exam.status}`)}
              </StatusChip>
            </Fact>
            <Fact label={t("facts.draft")}>
              {exam.hasUnpublishedChanges ? t("facts.draftYes") : t("facts.draftNo")}
            </Fact>
            <Fact label={t("facts.check")}>
              <span className="flex flex-col items-start gap-1">
                <StatusChip tone={EXAM_CHECK_TONE[exam.check.status]}>
                  {exam.check.status === "INVALID"
                    ? tExams("checkStatus.issues", { count: exam.check.issueCount })
                    : tExams(`checkStatus.${exam.check.status}`)}
                </StatusChip>
                {exam.check.checkedAt && <FormattedDate value={exam.check.checkedAt} />}
              </span>
            </Fact>
            <Fact label={t("facts.template")}>
              <span className="flex flex-col">
                <span>{appliedTemplate?.name ?? t("facts.noTemplate")}</span>
                {exam.structureYear !== null && (
                  <span className="text-caption font-normal text-text-muted">
                    {t("facts.years", {
                      structureYear: String(exam.structureYear),
                      matrixYear: exam.matrixYear === null ? "—" : String(exam.matrixYear),
                    })}
                  </span>
                )}
              </span>
            </Fact>
            {yearsDiffer(exam) && (
              <Fact label={t("facts.yearsConfirmed")}>
                <StatusChip tone={exam.yearsConfirmed ? "success" : "warning"}>
                  {exam.yearsConfirmed ? t("facts.yearsConfirmedYes") : t("facts.yearsConfirmedNo")}
                </StatusChip>
              </Fact>
            )}
            <Fact label={t("facts.structure")}>
              {t(shared ? "facts.structureValueShared" : "facts.structureValue", {
                parts: exam.parts.length,
                questions: exam.totalQuestions,
                minutes: exam.durationMinutes,
              })}
            </Fact>
            <Fact label={t("facts.maxScore")}>
              {exam.maxScore === null ? t("facts.maxScoreUnset") : t("facts.maxScoreValue", { count: exam.maxScore })}
            </Fact>
            {exam.hasEssay && <Fact label={t("facts.essay")}>{t("facts.essayValue")}</Fact>}
            <Fact label={t("facts.paths")}>{pathsText}</Fact>
            <Fact label={t("facts.attempts")}>{t("facts.attemptsValue", { count: exam.attemptCount })}</Fact>
            <Fact label={t("facts.updated")}>
              <span>
                <FormattedDate value={exam.updatedAt} /> · {exam.updatedBy.name}
              </span>
            </Fact>
          </dl>
        </Card>
      </div>

      <ReloadStructureDialog
        open={confirmReplace !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmReplace(null);
        }}
        title={t("replaceTitle")}
        description={t("replaceDescription")}
        parts={confirmReplace?.parts ?? []}
        confirmLabel={t("replaceConfirm")}
        loading={update.isPending}
        onConfirm={() => {
          if (confirmReplace) save(confirmReplace);
        }}
      />
      <UnsavedChangesDialog guard={guard} />
    </div>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-caption text-text-muted">{label}</dt>
      <dd className="m-0 font-medium text-text">{children}</dd>
    </div>
  );
}

function FormattedDate({ value }: { value: string }) {
  const format = useFormatter();
  return <time dateTime={value}>{format.dateTime(new Date(value), "dateTime")}</time>;
}
