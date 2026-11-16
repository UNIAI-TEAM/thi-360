"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, Card, ConfirmDialog, EmptyState, LiveStatus, notify, Skeleton, StatusChip } from "@thi360/ui";
import { TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo, useState, type ReactNode } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useContentReference, useReferenceLookup } from "@/features/content/queries";
import type { ContentReference } from "@/features/content/types";
import { getErrorStatus } from "@/lib/api/query-client";
import { sum } from "../matrix";
import { useApplyExamTemplate, useExam, useExamTemplates, useUpdateExamInfo } from "../queries";
import { EXAM_CHECK_TONE, EXAM_STATUS_TONE } from "../status";
import type { ExamDetail, ExamTemplate, UpdateExamInfoRequest } from "../types";
import {
  type ExamInfoFormValues,
  type ExamScope,
  findTemplate,
  formatOfScope,
  isSubjectKind,
  kindOptionFor,
  NO_TEMPLATE,
  resolveScope,
  scopeDefaults,
  scopeOfFormat,
  yearsDiffer,
} from "../create-exam-schema";
import { ExamInfoFields, TemplatePreview, useExamInfoSchema, useSingleScopes } from "./create-exam-fields";
import { UnsavedChangesDialog, useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";

interface PendingInfoSave {
  request: UpdateExamInfoRequest;
  /** Mẫu người dùng chọn ở biểu mẫu (năm cấu trúc); `null`: không có mẫu. */
  templateCode: string | null;
}

function toFormValues(exam: ExamDetail): ExamInfoFormValues {
  return {
    name: exam.name,
    code: exam.code,
    kind: exam.kind,
    format: formatOfScope(exam.scope),
    templateCode: exam.templateCode ?? NO_TEMPLATE,
    singleSubjectCode: exam.partCode ?? "",
    subjectCode: exam.subjectCode ?? "",
    timing: exam.timing,
    sharedDuration: exam.timing === "SHARED" ? exam.durationMinutes : 0,
    maxScore: exam.maxScore,
    yearsConfirmed: exam.yearsConfirmed,
    description: exam.description ?? "",
  };
}

function examScope(exam: ExamDetail): ExamScope {
  return {
    kind: exam.kind,
    format: formatOfScope(exam.scope),
    singleScope: exam.partCode ?? "",
    subjectCode: exam.subjectCode ?? "",
    templateCode: exam.templateCode ?? NO_TEMPLATE,
  };
}

function scopeChanged(exam: ExamDetail, values: ExamInfoFormValues, subjectKind: boolean): boolean {
  return (
    exam.kind !== values.kind ||
    formatOfScope(exam.scope) !== values.format ||
    (exam.templateCode ?? NO_TEMPLATE) !== values.templateCode ||
    (values.format === "SINGLE" && (exam.partCode ?? "") !== values.singleSubjectCode) ||
    (subjectKind && (exam.subjectCode ?? "") !== values.subjectCode)
  );
}

/**
 * Tab Thông tin chung (chưa có bản vẽ riêng; trường theo `TaoBaiThi`, UniNote PBI-04 mục 4; spec 7 kỳ thi). Sửa tên, mã,
 * mô tả, thời lượng chung, thang điểm, xác nhận năm cấu trúc và năm ma trận; đổi kỳ thi, hình thức, môn, mẫu hoặc
 * môn của bài lẻ thì nạp lại cấu trúc theo mẫu sau khi xác nhận, chỉ khi bài chưa công bố. Bên phải là các thông tin
 * chỉ để đọc.
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
  const singleScopes = useSingleScopes();
  const update = useUpdateExamInfo(exam.id);
  const applyTemplate = useApplyExamTemplate(exam.id);
  const [confirmReplace, setConfirmReplace] = useState<PendingInfoSave | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const lockScope = exam.servingVersion !== null;

  const form = useForm<ExamInfoFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: toFormValues(exam),
  });
  const { control, register, handleSubmit, setValue, getValues, setError, reset, formState } = form;
  const values = useWatch({ control }) as ExamInfoFormValues;
  const guard = useUnsavedChangesGuard(formState.isDirty);
  const subjectKind = isSubjectKind(kinds, values.kind);

  const blockCount = useMemo(
    () =>
      sum(
        exam.parts.map((part) =>
          sum([...part.sections, ...part.options.flatMap((option) => option.sections)].map((section) => section.blocks.length)),
        ),
      ),
    [exam.parts],
  );
  const branchCount = exam.parts.flatMap((part) => part.options).filter((option) => option.kind === "BRANCH").length;

  const done = () => {
    setConfirmReplace(null);
    const message = t("saved");
    notify.success(message);
    onAnnounce(message);
  };

  /**
   * Lưu Thông tin chung. API không còn nhận mẫu: đổi phạm vi thì máy chủ nạp mẫu mới nhất, mẫu khác (năm cấu trúc) nạp
   * tiếp bằng `applyExamTemplate`. Bước 1 và bước Cấu trúc theo phạm vi là việc của FE-3c.
   */
  const save = ({ request, templateCode }: PendingInfoSave) => {
    setSubmitError(null);
    update.mutate(request, {
      onSuccess: (updated) => {
        if (templateCode && templateCode !== updated.templateCode) {
          applyTemplate.mutate(
            { templateCode, confirmReplace: true, expectedRevision: updated.revision },
            { onSuccess: done, onError: () => setSubmitError(t("saveError")) },
          );
          return;
        }
        done();
      },
      onError: (error) => {
        setConfirmReplace(null);
        const status = getErrorStatus(error);
        if (status === 409 && !lockScope) {
          setError("code", { message: tExams("create.errors.codeTaken") }, { shouldFocus: true });
        } else {
          setSubmitError(t("saveError"));
        }
      },
    });
  };

  const onSubmit = handleSubmit((submitted) => {
    const template = findTemplate(templates, submitted.templateCode);
    const changed = scopeChanged(exam, submitted, isSubjectKind(kinds, submitted.kind));
    const request: UpdateExamInfoRequest = {
      name: submitted.name,
      code: submitted.code,
      kind: submitted.kind,
      scope: scopeOfFormat(submitted.format, kinds, submitted.kind),
      partCode: submitted.format === "SINGLE" ? submitted.singleSubjectCode : null,
      subjectCode: isSubjectKind(kinds, submitted.kind) ? submitted.subjectCode || null : null,
      durationMinutes: submitted.timing === "SHARED" ? submitted.sharedDuration : null,
      maxScore: submitted.maxScore,
      yearsConfirmed: yearsDiffer(template) ? submitted.yearsConfirmed : true,
      description: submitted.description.trim() || null,
      confirmReplace: changed,
      expectedRevision: exam.revision,
    };
    const pending: PendingInfoSave = { request, templateCode: template?.code ?? null };
    if (changed) setConfirmReplace(pending);
    else save(pending);
  });

  /** Đổi phạm vi: chọn lại mẫu; về đúng phạm vi đang lưu thì lấy lại giá trị đang lưu, không thì giá trị của mẫu mới. */
  const onScopeChange = (change: Partial<ExamScope>) => {
    const current: ExamScope = {
      kind: getValues("kind"),
      format: getValues("format"),
      singleScope: getValues("singleSubjectCode"),
      subjectCode: getValues("subjectCode"),
      templateCode: getValues("templateCode"),
    };
    const scope = resolveScope(templates, kinds, reference.subjects, current, change);
    const dirty = { shouldDirty: true } as const;
    setValue("kind", scope.kind, dirty);
    setValue("format", scope.format, dirty);
    setValue("singleSubjectCode", scope.singleScope, { ...dirty, shouldValidate: formState.isSubmitted });
    setValue("subjectCode", scope.subjectCode, { ...dirty, shouldValidate: formState.isSubmitted });
    setValue("templateCode", scope.templateCode, dirty);
    const saved = examScope(exam);
    const back =
      scope.kind === saved.kind &&
      scope.format === saved.format &&
      scope.singleScope === saved.singleScope &&
      scope.subjectCode === saved.subjectCode &&
      scope.templateCode === saved.templateCode;
    const defaults = back
      ? toFormValues(exam)
      : { ...scopeDefaults(findTemplate(templates, scope.templateCode), kindOptionFor(kinds, scope.kind), scope.format), yearsConfirmed: false };
    setValue("timing", defaults.timing, dirty);
    setValue("sharedDuration", defaults.sharedDuration, dirty);
    setValue("maxScore", defaults.maxScore, dirty);
    setValue("yearsConfirmed", defaults.yearsConfirmed, dirty);
  };

  const pathsText = (() => {
    if (exam.scope === "PART") return exam.partCode ? singleScopes.scopeName(exam.partCode) : "—";
    const segments = [
      ...(exam.englishOption ? [t("facts.pathsEnglishOnly")] : []),
      ...(exam.combinationCount > 0 ? [t("facts.pathsCombinationsOnly", { count: exam.combinationCount })] : []),
      ...(branchCount > 0 ? [t("facts.pathsBranches", { count: branchCount })] : []),
    ];
    return segments.length > 0 ? segments.join(" + ") : t("facts.pathsFixed");
  })();

  const showPreview = !lockScope && scopeChanged(exam, values, subjectKind);
  const template = templates.find((item) => item.code === values.templateCode);
  const appliedTemplate = templates.find((item) => item.code === exam.templateCode);
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
              partMinutes={shared ? 0 : exam.durationMinutes}
              lockScope={lockScope}
              lockScopeHint={t("scopeLocked")}
              onScopeChange={onScopeChange}
            />
            {showPreview && (
              <TemplatePreview
                template={template}
                format={values.format}
                subjectName={
                  values.format === "SINGLE"
                    ? values.singleSubjectCode
                      ? singleScopes.scopeName(values.singleSubjectCode)
                      : null
                    : values.subjectCode
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
                onClick={() => reset(toFormValues(exam))}
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

      <ConfirmDialog
        open={confirmReplace !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmReplace(null);
        }}
        title={t("replaceTitle")}
        description={t("replaceDescription", { parts: exam.parts.length, blocks: blockCount })}
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
