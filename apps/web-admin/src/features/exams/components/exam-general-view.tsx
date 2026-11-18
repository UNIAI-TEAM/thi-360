"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, Card, EmptyState, LiveStatus, notify, Skeleton, StatusChip } from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useState, type ReactNode } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { getErrorStatus } from "@/lib/api/query-client";
import { useExam, useExamTemplates, useUpdateExamInfo } from "../queries";
import { EXAM_CHECK_TONE, EXAM_STATUS_TONE } from "../status";
import type { ExamDetail, ExamTemplate, UpdateExamInfoRequest } from "../types";
import {
  combinationsForKind,
  type ExamInfoFormValues,
  initialParts,
  kindOptionFor,
  latestTemplate,
  resolveScope,
  sameSelection,
  scopeDefaults,
  type ScopeSelection,
  yearsDiffer,
} from "../create-exam-schema";
import { ExamInfoFields, TemplatePreview, useExamInfoSchema, usePartNames } from "./create-exam-fields";
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

/** Bài ôn nhận ra qua mẫu đã nạp (bài không ghi `buildMode`): thang của bài ôn được để trống. */
function toFormValues(exam: ExamDetail, templates: readonly ExamTemplate[]): ExamInfoFormValues {
  const review = templates.find((item) => item.code === exam.templateCode)?.buildMode === "REVIEW";
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
    buildMode: review ? "REVIEW" : undefined,
    yearsConfirmed: exam.yearsConfirmed,
    description: exam.description ?? "",
    instructions: exam.instructions ?? "",
  };
}

function examScope(exam: ExamDetail): ScopeSelection {
  return { kind: exam.kind, scope: exam.scope, partCode: exam.partCode ?? "", subjectCode: exam.subjectCode ?? "" };
}

/**
 * Tab Thông tin chung (chưa có bản vẽ riêng; trường theo `TaoBaiThi`, UniNote PBI-04 mục 4; spec 7 kỳ thi). Sửa tên, mã,
 * mô tả, hướng dẫn làm bài, thời lượng chung, thang điểm, xác nhận năm cấu trúc và năm ma trận; đổi kỳ thi, phạm vi,
 * phần hoặc môn thì nạp lại cấu trúc theo mẫu mới nhất sau khi xác nhận (hộp thoại liệt kê các phần sẽ nạp lại), chỉ khi
 * bài chưa công bố. Phiên bản năm chọn ở bước Cấu trúc. Bên phải là các thông tin chỉ để đọc.
 */
export function ExamGeneralView({ examId }: { examId: string }) {
  const t = useTranslations("exams.general");
  const examQuery = useExam(examId);
  const referenceQuery = useContentReference();
  const templatesQuery = useExamTemplates();
  const [liveMessage, setLiveMessage] = useState("");
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
  return (
    <>
      {/* Khóa theo lần cập nhật: lưu xong biểu mẫu nạp lại giá trị mới, hết "chưa lưu". */}
      <ExamGeneralForm
        key={`${examQuery.data.id}-${examQuery.data.updatedAt}`}
        exam={examQuery.data}
        reference={referenceQuery.data}
        templates={templatesQuery.data}
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
  onAnnounce,
}: {
  exam: ExamDetail;
  reference: ContentReference;
  templates: readonly ExamTemplate[];
  onAnnounce: (message: string) => void;
}) {
  const t = useTranslations("exams.general");
  const tExams = useTranslations("exams");
  const kinds = reference.examKinds;
  const lookup = useReferenceLookup(reference);
  const schema = useExamInfoSchema(undefined, kinds);
  const partNames = usePartNames();
  const update = useUpdateExamInfo(exam.id);
  const [confirmReplace, setConfirmReplace] = useState<PendingInfoSave | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const lockScope = exam.servingVersion !== null;

  const form = useForm<ExamInfoFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: toFormValues(exam, templates),
  });
  const { control, register, handleSubmit, setValue, getValues, setError, reset, formState } = form;
  const values = useWatch({ control }) as ExamInfoFormValues;
  const guard = useUnsavedChangesGuard(formState.isDirty);
  const selection: ScopeSelection = {
    kind: values.kind,
    scope: values.scope,
    partCode: values.partCode,
    subjectCode: values.subjectCode,
  };

  const branchCount = exam.parts.flatMap((part) => part.options).filter((option) => option.kind === "BRANCH").length;

  /** Các phần mà phạm vi mới sẽ nạp lại: cùng cách dựng với máy chủ (mẫu mới nhất, hoặc phần trống của môn chưa có mẫu). */
  const reloadRows = (next: ScopeSelection): ReloadPartRow[] => {
    const template = latestTemplate(templates, next);
    const shared = (template?.timing ?? kindOptionFor(kinds, next.kind)?.timing) === "SHARED";
    return initialParts({
      templates,
      kind: next.kind,
      scope: next.scope,
      partCode: next.partCode,
      combinations: combinationsForKind(reference.combinations, next.kind),
      scopeName: partNames.partName,
      template: next.scope === "PART" ? undefined : (template ?? null),
      subjectCode: next.subjectCode,
      subjectName: lookup.subject,
    }).map((part) => ({
      name: part.name,
      questionCount: part.questionCount,
      durationMinutes: part.durationMinutes,
      shared,
    }));
  };

  const done = () => {
    setConfirmReplace(null);
    const message = t("saved");
    notify.success(message);
    onAnnounce(message);
  };

  /** Lưu Thông tin chung. Đổi phạm vi thì máy chủ nạp mẫu mới nhất khớp; phiên bản năm khác chọn ở bước Cấu trúc. */
  const save = ({ request }: PendingInfoSave) => {
    setSubmitError(null);
    update.mutate(request, {
      onSuccess: done,
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
      },
    });
  };

  const onSubmit = handleSubmit((submitted) => {
    const next: ScopeSelection = {
      kind: submitted.kind,
      scope: submitted.scope,
      partCode: submitted.partCode,
      subjectCode: submitted.subjectCode,
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
    else save(pending);
  });

  /** Đổi phạm vi: về đúng phạm vi đang lưu thì lấy lại giá trị đang lưu, không thì giá trị của mẫu mới nhất. */
  const onScopeChange = (change: Partial<ScopeSelection>) => {
    const current: ScopeSelection = {
      kind: getValues("kind"),
      scope: getValues("scope"),
      partCode: getValues("partCode"),
      subjectCode: getValues("subjectCode"),
    };
    const scope = resolveScope(templates, kinds, reference.subjects, current, change);
    const dirty = { shouldDirty: true } as const;
    setValue("kind", scope.kind, dirty);
    setValue("scope", scope.scope, dirty);
    setValue("buildMode", scope.buildMode, dirty);
    setValue("partCode", scope.partCode, { ...dirty, shouldValidate: formState.isSubmitted });
    setValue("subjectCode", scope.subjectCode, { ...dirty, shouldValidate: formState.isSubmitted });
    const back = sameSelection(scope, examScope(exam));
    const defaults = back
      ? toFormValues(exam, templates)
      : { ...scopeDefaults(latestTemplate(templates, scope), kindOptionFor(kinds, scope.kind), scope.scope), yearsConfirmed: false };
    setValue("timing", defaults.timing, dirty);
    setValue("sharedDuration", defaults.sharedDuration, dirty);
    setValue("maxScore", defaults.maxScore, dirty);
    setValue("yearsConfirmed", defaults.yearsConfirmed, dirty);
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

  const scopeEdited = !sameSelection(examScope(exam), selection);
  const showPreview = !lockScope && scopeEdited;
  const appliedTemplate = templates.find((item) => item.code === exam.templateCode);
  // Chọn phạm vi khác thì ghi chú theo mẫu mới nhất của phạm vi đó; chưa đổi thì theo mẫu đã nạp của bài.
  const template = scopeEdited ? latestTemplate(templates, selection) : appliedTemplate;
  const shared = exam.timing === "SHARED";

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
            {showPreview && (
              <TemplatePreview
                template={latestTemplate(templates, selection)}
                scope={values.scope}
                subjectName={
                  values.scope === "PART"
                    ? values.partCode
                      ? partNames.partName(values.partCode)
                      : null
                    : values.scope === "SUBJECT" && values.subjectCode
                      ? lookup.subject(values.subjectCode)
                      : null
                }
              />
            )}
            {submitError && (
              <p role="alert" className="rounded-md border border-danger/30 bg-danger-bg px-3 py-2 text-body text-danger">
                {submitError}
              </p>
            )}
            <div className="flex flex-wrap justify-end gap-3">
              <Button
                type="button"
                variant="secondary"
                disabled={!formState.isDirty || update.isPending}
                onClick={() => reset(toFormValues(exam, templates))}
              >
                {t("discard")}
              </Button>
              <Button type="submit" loading={update.isPending}>
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
