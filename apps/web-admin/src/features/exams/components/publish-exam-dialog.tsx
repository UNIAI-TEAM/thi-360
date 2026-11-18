"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Badge,
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  Skeleton,
  Textarea,
} from "@thi360/ui";
import { ArrowRight, ArrowUp, TriangleAlert } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import Link from "next/link";
import { useId, useMemo, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { getErrorStatus } from "@/lib/api/query-client";
import { checkRequiredReason, errorCodeOf } from "../review-api";
import { useExamCheck, useIdempotencyKey, usePublishExam } from "../review-queries";
import type { ExamCheckResult, ExamVersionList, PathCheck, PublishExamResponse } from "../review-types";
import type { ExamDetail } from "../types";
import { examTabHref } from "../editor-tabs";
import { PathResultChip, reviewHref, useIssueText, usePathLabel } from "./exam-review-shared";

export const PUBLISH_NOTE_MAX = 500;

export interface PublishExamDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  exam: ExamDetail;
  versions: ExamVersionList;
  onPublished: (response: PublishExamResponse) => void;
}

/**
 * Công bố bản làm việc (bản vẽ `CongBoBaiThi`): chọn đường lựa chọn được công bố. Chỉ đường Hợp lệ chọn được;
 * đường không chọn tiếp tục dùng phiên bản đang phục vụ. Lượt đang làm giữ bản chụp cũ.
 */
export function PublishExamDialog({ open, onOpenChange, exam, versions, onPublished }: PublishExamDialogProps) {
  const t = useTranslations("exams.publish");
  const [submitting, setSubmitting] = useState(false);
  const nextVersion = Math.max(0, ...versions.items.map((item) => item.version)) + 1;
  return (
    <Dialog open={open} onOpenChange={(next) => (submitting ? undefined : onOpenChange(next))}>
      <DialogContent size="lg" closeLabel={t("close")} className="flex max-h-[90vh] flex-col overflow-hidden">
        <DialogHeader className="shrink-0">
          <DialogTitle>{t("title", { version: nextVersion })}</DialogTitle>
          <DialogDescription>{t("description", { version: nextVersion })}</DialogDescription>
        </DialogHeader>
        {open && (
          <PublishBody
            exam={exam}
            versions={versions}
            nextVersion={nextVersion}
            onCancel={() => onOpenChange(false)}
            onSubmittingChange={setSubmitting}
            onPublished={(response) => {
              onOpenChange(false);
              onPublished(response);
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function PublishBody({
  exam,
  versions,
  nextVersion,
  onCancel,
  onSubmittingChange,
  onPublished,
}: {
  exam: ExamDetail;
  versions: ExamVersionList;
  nextVersion: number;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onPublished: (response: PublishExamResponse) => void;
}) {
  const t = useTranslations("exams.publish");
  const checkQuery = useExamCheck(exam.id);

  if (checkQuery.isPending) {
    return (
      <div className="flex flex-col gap-3 py-2" aria-busy="true">
        <Skeleton className="h-16" />
        <Skeleton className="h-40" />
      </div>
    );
  }
  const check = checkQuery.data;
  if (checkQuery.isError || !check || exam.check.status === "NEEDS_RECHECK") {
    return (
      <>
        <p className="flex items-start gap-2 rounded-lg border border-warning/30 bg-warning-bg px-3.5 py-2.5 text-body text-text">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
          {checkQuery.isError ? t("checkError") : t("checkRequired")}
        </p>
        <DialogFooter>
          <Button variant="secondary" onClick={onCancel}>
            {t("cancel")}
          </Button>
          <Button asChild>
            <Link href={reviewHref(exam.id)}>
              {t("toCheck")}
              <ArrowRight />
            </Link>
          </Button>
        </DialogFooter>
      </>
    );
  }
  return (
    <PublishForm
      exam={exam}
      versions={versions}
      check={check}
      nextVersion={nextVersion}
      onCancel={onCancel}
      onSubmittingChange={onSubmittingChange}
      onPublished={onPublished}
    />
  );
}

function PublishForm({
  exam,
  versions,
  check,
  nextVersion,
  onCancel,
  onSubmittingChange,
  onPublished,
}: {
  exam: ExamDetail;
  versions: ExamVersionList;
  check: ExamCheckResult;
  nextVersion: number;
  onCancel: () => void;
  onSubmittingChange: (submitting: boolean) => void;
  onPublished: (response: PublishExamResponse) => void;
}) {
  const t = useTranslations("exams.publish");
  const format = useFormatter();
  const pathLabel = usePathLabel(exam.id);
  const issueText = useIssueText();
  const publish = usePublishExam(exam.id);
  const fieldsetId = useId();
  const errorId = useId();
  const idempotency = useIdempotencyKey();
  const [serverError, setServerError] = useState<{ message: string; recheck: boolean } | null>(null);

  /** Đường của bản làm việc còn chờ công bố; công bố một phần trước đó thì chỉ còn các đường chưa chọn. */
  const pending = new Set(versions.workingCopy?.unpublishedPaths.map((path) => path.pathId) ?? []);
  const rows = check.paths.filter((path) => pending.size === 0 || pending.has(path.pathId));
  const servedBy = new Map<string, number>();
  const everPublished = new Set<string>();
  for (const item of versions.items) {
    for (const path of item.servingPaths) if (!servedBy.has(path.pathId)) servedBy.set(path.pathId, item.version);
    for (const path of item.publishedPaths) everPublished.add(path.pathId);
  }

  const schema = useMemo(
    () =>
      z.object({
        pathIds: z.array(z.string()).min(1, t("validation.pathRequired")),
        note: z.string().max(PUBLISH_NOTE_MAX, t("validation.noteMax", { max: PUBLISH_NOTE_MAX })),
      }),
    [t],
  );
  type Values = z.infer<typeof schema>;

  const {
    control,
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: { pathIds: rows.filter((path) => path.result === "VALID").map((path) => path.pathId), note: "" },
    // Lỗi đầu tiên được focus: ô chọn đường hợp lệ đầu tiên (`field.ref`), rồi ô ghi chú.
    shouldFocusError: true,
  });
  const selected = useWatch({ control, name: "pathIds" });

  const submit = handleSubmit(
    (values) => {
      setServerError(null);
      onSubmittingChange(true);
      const note = values.note.trim() || null;
      // Một khóa cho mỗi lần bấm Công bố: thử lại cùng nội dung giữ khóa, sửa lựa chọn hay ghi chú thì khóa mới.
      const idempotencyKey = idempotency.keyFor(JSON.stringify([[...values.pathIds].sort(), note, exam.revision]));
      publish.mutate(
        { pathIds: values.pathIds, note, expectedRevision: exam.revision, idempotencyKey },
        {
          onSuccess: (response) => {
            idempotency.release();
            onPublished(response);
          },
          onError: (error) => {
            const reason = checkRequiredReason(error);
            const code = errorCodeOf(error);
            if (reason !== null) {
              const known = ["NO_CHECK", "STRUCTURE_CHANGED", "CONFIG_CHANGED", "SOURCE_CHANGED", "PATH_NOT_VALID"];
              setServerError({
                message: t(`errors.checkRequired.${known.includes(reason) ? reason : "UNKNOWN"}`),
                recheck: true,
              });
              return;
            }
            setServerError({
              message:
                code === "PATH_NOT_VALID" || code === "NOTHING_TO_PUBLISH"
                  ? t(`errors.${code}`)
                  : getErrorStatus(error) === 422
                    ? t("errors.PATH_NOT_VALID")
                    : getErrorStatus(error) === 412
                      ? t("errors.revisionConflict")
                      : t("errors.generic"),
              recheck: false,
            });
          },
          onSettled: () => onSubmittingChange(false),
        },
      );
    },
  );

  const description = (path: PathCheck, checked: boolean) => {
    const served = servedBy.get(path.pathId);
    if (path.result === "VALID") {
      if (checked) return t("row.willPublish", { version: nextVersion });
      return served ? t("row.keepServing", { version: served }) : t("row.notOnSale");
    }
    return served ? t("row.blockedServing", { version: served }) : t("row.blockedNew");
  };

  const keptServing = rows.some((path) => !selected.includes(path.pathId) && servedBy.has(path.pathId));
  const servingVersion = exam.servingVersion;
  const firstEnabled = rows.find((path) => path.result === "VALID")?.pathId;
  // Năm cấu trúc khác năm ma trận chưa xác nhận chặn mọi đường; sửa ở tab Thông tin chung, không phải ở Kiểm tra.
  const yearsIssue = check.paths.flatMap((path) => path.issues).find((issue) => issue.code === "YEARS_UNCONFIRMED");
  /** Vấn đề chặn của đường (bỏ cảnh báo); vấn đề đầu tiên nêu ngay dưới dòng để biết vì sao không chọn được. */
  const blockersOf = (path: PathCheck) => path.issues.filter((issue) => issue.severity !== "WARNING");

  return (
    <form onSubmit={(event) => void submit(event)} noValidate className="flex min-h-0 flex-1 flex-col gap-4">
      <div className="-mx-1 flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-1 scroll-shadows-y">
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 rounded-lg bg-subtle px-3.5 py-3 text-body">
          <dt className="text-text-muted">{t("summary.exam")}</dt>
          <dd className="m-0 font-medium wrap-anywhere text-text">{`${exam.code} · ${exam.name}`}</dd>
          {versions.workingCopy && versions.workingCopy.baseVersion !== null && (
            <>
              <dt className="text-text-muted">{t("summary.changes", { version: versions.workingCopy.baseVersion })}</dt>
              <dd className="m-0 text-text">{t("summary.changesValue", { count: versions.changes.length })}</dd>
            </>
          )}
          <dt className="text-text-muted">{t("summary.check")}</dt>
          <dd className="m-0 text-text">{format.dateTime(new Date(check.checkedAt), "dateTime")}</dd>
        </dl>

        {yearsIssue && (
          <p className="flex flex-wrap items-start gap-x-2 gap-y-1 rounded-lg border border-warning/30 bg-warning-bg px-3.5 py-2.5 text-body text-text">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
            <span className="min-w-0 flex-1">{issueText(yearsIssue)}</span>
            <Link
              href={examTabHref(exam.id, "general")}
              className="rounded-sm font-medium text-primary-text hover:underline"
            >
              {t("yearsLink")}
            </Link>
          </p>
        )}

        <fieldset
          id={fieldsetId}
          aria-describedby={errors.pathIds ? errorId : undefined}
          className="flex flex-col gap-2"
        >
          <legend className="mb-2 text-label font-semibold text-text">{t("pathsLegend")}</legend>
          <Controller
            control={control}
            name="pathIds"
            render={({ field }) => (
              <>
                {rows.map((path) => {
                  const id = `${fieldsetId}-${path.pathId}`;
                  const descId = `${id}-desc`;
                  const enabled = path.result === "VALID";
                  const checked = enabled && field.value.includes(path.pathId);
                  const blockers = enabled ? [] : blockersOf(path);
                  const blocker = blockers[0];
                  return (
                    <div
                      key={path.pathId}
                      className={`flex items-start gap-3 rounded-lg border p-3 ${
                        checked ? "border-primary-600 bg-primary-50" : enabled ? "border-border bg-surface" : "border-border bg-subtle"
                      }`}
                    >
                      <Checkbox
                        id={id}
                        ref={path.pathId === firstEnabled ? field.ref : undefined}
                        checked={checked}
                        disabled={!enabled}
                        aria-describedby={descId}
                        aria-invalid={errors.pathIds ? true : undefined}
                        onCheckedChange={(value) =>
                          field.onChange(
                            value === true
                              ? [...field.value, path.pathId]
                              : field.value.filter((item) => item !== path.pathId),
                          )
                        }
                        onBlur={field.onBlur}
                        className="mt-0.5"
                      />
                      <div className="flex min-w-0 flex-1 flex-col gap-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <label htmlFor={id} className={`font-semibold ${enabled ? "text-text" : "text-text-secondary"}`}>
                            {pathLabel(path)}
                          </label>
                          <PathResultChip result={path.result} />
                          {!everPublished.has(path.pathId) && versions.items.length > 0 && <Badge>{t("row.new")}</Badge>}
                        </div>
                        <p id={descId} className={`text-body ${path.result === "INVALID" ? "text-danger" : "text-text-secondary"}`}>
                          {description(path, checked)}
                        </p>
                        {blocker && (
                          <p className="text-caption text-text-secondary">
                            {t("row.firstIssue", { issue: issueText(blocker), more: blockers.length - 1 })}
                          </p>
                        )}
                        {!enabled && (
                          <Link href={reviewHref(exam.id)} className="w-fit rounded-sm text-body font-medium text-primary-text hover:underline">
                            {t("row.viewCheck")}
                          </Link>
                        )}
                      </div>
                    </div>
                  );
                })}
              </>
            )}
          />
          {errors.pathIds && (
            <p id={errorId} className="text-caption text-danger">
              {errors.pathIds.message}
            </p>
          )}
        </fieldset>

        {keptServing && servingVersion !== null && (
          <p className="flex items-start gap-2 text-body text-text-secondary">
            <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
            {t("partialNote", { version: servingVersion })}
          </p>
        )}

        <FormField label={t("note")} hint={t("noteHint", { max: PUBLISH_NOTE_MAX })} error={errors.note?.message}>
          {(controlProps) => <Textarea {...controlProps} rows={2} {...register("note")} />}
        </FormField>

        {serverError && (
          <div
            role="alert"
            className="flex flex-wrap items-start gap-x-3 gap-y-1 rounded-lg border border-danger/30 bg-danger-bg px-3.5 py-2.5 text-body text-danger"
          >
            <span className="min-w-0 flex-1">{serverError.message}</span>
            {serverError.recheck && (
              <Link href={reviewHref(exam.id)} className="rounded-sm font-medium text-primary-text hover:underline">
                {t("errors.recheck")}
              </Link>
            )}
          </div>
        )}
      </div>

      <DialogFooter className="shrink-0 items-center">
        <output className="me-auto text-body text-text-secondary">
          {t("selectedSummary", { count: selected.length, total: rows.length })}
        </output>
        <Button variant="secondary" onClick={onCancel} disabled={publish.isPending}>
          {t("cancel")}
        </Button>
        <Button type="submit" loading={publish.isPending}>
          <ArrowUp />
          {t("submit", { count: selected.length })}
        </Button>
      </DialogFooter>
    </form>
  );
}
