"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Button, Card, ConfirmDialog, LiveStatus, notify, StatusChip } from "@thi360/ui";
import { useFormatter, useTranslations } from "next-intl";
import { useMemo, useState, type ReactNode } from "react";
import { useForm, useWatch } from "react-hook-form";
import { getErrorStatus } from "@/lib/api/query-client";
import { sum } from "../matrix";
import { useExam, useExamTemplates, useUpdateExamInfo } from "../queries";
import { EXAM_CHECK_TONE, EXAM_STATUS_TONE } from "../status";
import type { ExamDetail, ExamFormat, ExamKind, UpdateExamInfoRequest } from "../types";
import { type ExamInfoFormValues, NO_TEMPLATE, templateFor } from "../create-exam-schema";
import { ExamInfoFields, TemplatePreview, useExamInfoSchema, useSingleScopes } from "./create-exam-fields";
import { UnsavedChangesDialog, useUnsavedChangesGuard } from "./exam-structure-unsaved-guard";

function toFormValues(exam: ExamDetail): ExamInfoFormValues {
  return {
    name: exam.name,
    code: exam.code,
    kind: exam.kind,
    format: exam.format,
    templateCode: exam.templateCode ?? NO_TEMPLATE,
    singleSubjectCode: exam.singleSubjectCode ?? "",
    description: exam.description ?? "",
  };
}

function scopeChanged(exam: ExamDetail, values: ExamInfoFormValues): boolean {
  return (
    exam.kind !== values.kind ||
    exam.format !== values.format ||
    (exam.templateCode ?? NO_TEMPLATE) !== values.templateCode ||
    (values.format === "SINGLE" && (exam.singleSubjectCode ?? "") !== values.singleSubjectCode)
  );
}

/**
 * Tab Thông tin chung (chưa có bản vẽ riêng; trường theo `TaoBaiThi`, UniNote PBI-04 mục 4). Sửa tên, mã, mô tả; đổi kỳ
 * thi, hình thức, mẫu hoặc môn của bài lẻ thì nạp lại cấu trúc theo mẫu sau khi xác nhận, chỉ khi bài chưa công bố.
 * Bên phải là các thông tin chỉ để đọc.
 */
export function ExamGeneralView({ examId }: { examId: string }) {
  const examQuery = useExam(examId);
  const [liveMessage, setLiveMessage] = useState("");
  if (!examQuery.data) return null;
  return (
    <>
      {/* Khóa theo lần cập nhật: lưu xong biểu mẫu nạp lại giá trị mới, hết "chưa lưu". */}
      <ExamGeneralForm
        key={`${examQuery.data.id}-${examQuery.data.updatedAt}`}
        exam={examQuery.data}
        onAnnounce={setLiveMessage}
      />
      <LiveStatus message={liveMessage} />
    </>
  );
}

function ExamGeneralForm({ exam, onAnnounce }: { exam: ExamDetail; onAnnounce: (message: string) => void }) {
  const t = useTranslations("exams.general");
  const tExams = useTranslations("exams");
  const schema = useExamInfoSchema();
  const templatesQuery = useExamTemplates();
  const singleScopes = useSingleScopes();
  const templates = templatesQuery.data ?? [];
  const update = useUpdateExamInfo(exam.id);
  const [confirmReplace, setConfirmReplace] = useState<UpdateExamInfoRequest | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const lockScope = exam.servingVersion !== null;

  const form = useForm<ExamInfoFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: toFormValues(exam),
  });
  const { control, register, handleSubmit, setValue, setError, reset, formState } = form;
  const values = useWatch({ control }) as ExamInfoFormValues;
  const guard = useUnsavedChangesGuard(formState.isDirty);

  const blockCount = useMemo(
    () =>
      sum(
        exam.parts.map((part) =>
          sum([...part.sections, ...part.options.flatMap((option) => option.sections)].map((section) => section.blocks.length)),
        ),
      ),
    [exam.parts],
  );

  const save = (request: UpdateExamInfoRequest) => {
    setSubmitError(null);
    update.mutate(request, {
      onSuccess: () => {
        setConfirmReplace(null);
        const message = t("saved");
        notify.success(message);
        onAnnounce(message);
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
    const request: UpdateExamInfoRequest = {
      name: submitted.name,
      code: submitted.code,
      kind: submitted.kind,
      format: submitted.format,
      templateCode: submitted.templateCode === NO_TEMPLATE ? null : submitted.templateCode,
      singleSubjectCode: submitted.format === "SINGLE" ? submitted.singleSubjectCode : null,
      description: submitted.description.trim() || null,
    };
    if (scopeChanged(exam, submitted)) setConfirmReplace(request);
    else save(request);
  });

  const onScopeChange = ({ kind, format }: { kind: ExamKind; format: ExamFormat }) => {
    setValue("templateCode", templateFor(templates, kind, format)?.code ?? NO_TEMPLATE, { shouldDirty: true });
    if (format === "FULL") setValue("singleSubjectCode", "", { shouldDirty: true });
  };

  const pathsText = (() => {
    if (exam.format === "SINGLE") return exam.singleSubjectCode ? singleScopes.scopeName(exam.singleSubjectCode) : "—";
    if (exam.englishOption && exam.combinationCount > 0) {
      return t("facts.pathsEnglishAndCombinations", { count: exam.combinationCount });
    }
    if (exam.englishOption) return t("facts.pathsEnglishOnly");
    if (exam.combinationCount > 0) return t("facts.pathsCombinationsOnly", { count: exam.combinationCount });
    return t("facts.pathsFixed");
  })();

  const showPreview = !lockScope && scopeChanged(exam, values);
  const template = templates.find((item) => item.code === values.templateCode);
  const appliedTemplate = templates.find((item) => item.code === exam.templateCode);

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
              lockScope={lockScope}
              lockScopeHint={t("scopeLocked")}
              onScopeChange={onScopeChange}
            />
            {showPreview && (
              <TemplatePreview
                template={template}
                format={values.format}
                subjectName={values.singleSubjectCode ? singleScopes.scopeName(values.singleSubjectCode) : null}
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
            <Fact label={t("facts.template")}>{appliedTemplate?.name ?? t("facts.noTemplate")}</Fact>
            <Fact label={t("facts.structure")}>
              {t("facts.structureValue", {
                parts: exam.parts.length,
                questions: exam.totalQuestions,
                minutes: exam.durationMinutes,
              })}
            </Fact>
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
