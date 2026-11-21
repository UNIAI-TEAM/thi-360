"use client";

import { Button, Card, cn, focusRing, FormField, Input, Label, StatusChip, Textarea } from "@thi360/ui";
import { countWords } from "@thi360/ui/question";
import { RichTextView } from "@thi360/ui/rich-text";
import { CircleCheckBig, History, Lock, RefreshCw, RotateCcw, Save, UserCheck } from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useId, useRef, type Ref } from "react";
import { useFormContext, useFormState, useWatch } from "react-hook-form";
import { criteriaInputTotal, type GradingItemFormValues } from "../grading-form";
import { COMMENT_MAX, SCORE_NUMBER_FORMAT } from "../scoring";
import { GRADING_STATUS_TONE } from "../status";
import type { EssayGradeView, GradingItemDetailResponse } from "../types";

/**
 * Left column of `/grading/[itemId]`:
 * Passage (if present), question stem, student essay with live word count via `@thi360/ui/question` `countWords`
 * (non-blocking minimum `wordLimit` indicator), grading guide, and sample answer.
 * All exam content is strictly wrapped inside `data-exam-content`; UI labels remain outside.
 */
export function EssayItemContentPanel({ detail }: { detail: GradingItemDetailResponse }) {
  const t = useTranslations("grading.detail.question");
  const format = useFormatter();
  const headingId = useId();

  const answerHtml = detail.answerHtml ?? "";
  const words = countWords(answerHtml);
  const wordLimit = detail.wordLimit ?? null;
  const underWordLimit = wordLimit !== null && words < wordLimit;
  const hasAnswer = answerHtml.trim() !== "";

  return (
    <section aria-labelledby={headingId} className="min-w-0">
      <Card padding="none" className="flex flex-col gap-5 p-4 sm:p-5">
        <header className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id={headingId} className="text-h3 font-bold text-text">
            {t("title", { number: detail.displayNo ?? 0 })}
          </h2>
          <p className="text-caption text-text-muted tabular-nums">
            {t("maxPoints", {
              points: format.number(detail.pointsMax ?? 0, SCORE_NUMBER_FORMAT),
            })}
          </p>
        </header>

        {detail.passage && (
          <details open className="group rounded-lg border border-border bg-subtle">
            <summary
              className={cn(
                "cursor-pointer rounded-lg px-4 py-2.5 text-label font-semibold text-text pointer-coarse:min-h-11",
                focusRing,
              )}
            >
              {t("passage")}
            </summary>
            <div className="px-4 pb-4 text-body text-text" data-exam-content>
              <RichTextView html={detail.passage} />
            </div>
          </details>
        )}

        <div className="flex flex-col gap-1.5">
          <h3 className="text-label font-semibold text-text-secondary">{t("prompt")}</h3>
          <div className="text-body text-text" data-exam-content>
            <RichTextView html={detail.content ?? ""} />
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-label font-semibold text-text-secondary">{t("answer")}</h3>
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-caption text-text-secondary tabular-nums" data-testid="essay-word-count">
                {wordLimit !== null
                  ? t("wordCountWithLimit", { count: words, limit: wordLimit })
                  : t("wordCount", { count: words })}
              </p>
              {underWordLimit && (
                <StatusChip tone="warning">{t("belowWordLimit")}</StatusChip>
              )}
            </div>
          </div>

          {hasAnswer ? (
            <div
              data-exam-content
              className="rounded-lg border border-border bg-surface px-4 py-3 text-body leading-relaxed text-text wrap-anywhere"
            >
              <RichTextView html={answerHtml} />
            </div>
          ) : (
            <p className="rounded-lg border border-dashed border-border px-4 py-3 text-body text-text-muted">
              {t("noAnswer")}
            </p>
          )}
        </div>

        {detail.gradingGuide && (
          <details open className="rounded-lg border border-border">
            <summary
              className={cn(
                "cursor-pointer rounded-lg px-4 py-2.5 text-label font-semibold text-text pointer-coarse:min-h-11",
                focusRing,
              )}
            >
              {t("gradingGuide")}
            </summary>
            <div className="px-4 pb-4 text-body text-text" data-exam-content>
              <RichTextView html={detail.gradingGuide} />
            </div>
          </details>
        )}

        {detail.sampleAnswer && (
          <details className="rounded-lg border border-border">
            <summary
              className={cn(
                "cursor-pointer rounded-lg px-4 py-2.5 text-label font-semibold text-text pointer-coarse:min-h-11",
                focusRing,
              )}
            >
              {t("sampleAnswer")}
            </summary>
            <div className="px-4 pb-4 text-body text-text" data-exam-content>
              <RichTextView html={detail.sampleAnswer} />
            </div>
          </details>
        )}
      </Card>
    </section>
  );
}

/** Claim CTA card shown when item is `WAITING` or claim expired. */
export function ClaimActionCard({
  detail,
  isTakeover,
  claiming,
  alertMessage,
  onClaim,
}: {
  detail: GradingItemDetailResponse;
  isTakeover: boolean;
  claiming: boolean;
  alertMessage: string | null;
  onClaim: () => void;
}) {
  const t = useTranslations("grading.detail.claim");
  const format = useFormatter();
  const headingId = useId();
  const rubric = detail.rubric ?? [];

  return (
    <section aria-labelledby={headingId} className="min-w-0">
      <Card padding="none" className="flex flex-col gap-4 p-4 sm:p-5">
        <header className="flex flex-col gap-1">
          <h2 id={headingId} className="text-h3 font-bold text-text">
            {isTakeover ? t("takeoverTitle") : t("title")}
          </h2>
          <p className="text-body text-text-secondary">
            {isTakeover
              ? t("takeoverDescription", {
                  name: detail.activeGrade?.graderName ?? t("anotherGrader"),
                })
              : t("description")}
          </p>
        </header>

        {alertMessage && (
          <p
            role="alert"
            className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
          >
            {alertMessage}
          </p>
        )}

        <Button type="button" loading={claiming} onClick={onClaim}>
          <UserCheck />
          <span>{isTakeover ? t("takeoverAction") : t("action")}</span>
        </Button>

        {rubric.length > 0 && (
          <div className="flex flex-col gap-2 border-t border-border pt-3">
            <h3 className="text-label font-semibold text-text-secondary">{t("rubricPreview")}</h3>
            <ul className="m-0 flex list-none flex-col gap-2 p-0 text-body">
              {rubric.map((criterion, index) => (
                <li key={criterion.id ?? index} className="flex items-start justify-between gap-3">
                  <span className="text-text">
                    {index + 1}.{" "}
                    <span data-exam-content>{criterion.description}</span>
                  </span>
                  <span className="shrink-0 text-text-muted tabular-nums">
                    {format.number(criterion.points ?? 0, SCORE_NUMBER_FORMAT)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </Card>
    </section>
  );
}

/** Read-only card shown when another grader holds an active non-expired claim, or user lacks `GRADING.MODIFY`. */
export function ReadOnlyGradingCard({
  detail,
  reason,
  alertMessage,
}: {
  detail: GradingItemDetailResponse;
  reason: "claimedByOther" | "noPermission";
  alertMessage?: string | null;
}) {
  const t = useTranslations("grading.detail");
  const format = useFormatter();
  const headingId = useId();
  const active = detail.activeGrade;
  const rubric = detail.rubric ?? [];

  return (
    <section aria-labelledby={headingId} className="min-w-0">
      <Card padding="none" className="flex flex-col gap-4 p-4 sm:p-5">
        <header className="flex items-start gap-2.5">
          <Lock aria-hidden className="mt-0.5 size-5 shrink-0 text-text-muted" />
          <div className="flex flex-col gap-1">
            <h2 id={headingId} className="text-h3 font-bold text-text">
              {reason === "claimedByOther" ? t("claim.heldTitle") : t("readOnlyTitle")}
            </h2>
            <p className="text-body text-text-secondary">
              {reason === "claimedByOther"
                ? t("claim.heldDescription", {
                    name: active?.graderName ?? t("claim.anotherGrader"),
                  })
                : t("readOnlyDescription")}
            </p>
            {reason === "claimedByOther" && active?.lastActivityAt && (
              <p className="text-caption text-text-muted tabular-nums">
                {t("claim.lastActivity", {
                  time: format.dateTime(new Date(active.lastActivityAt), "dateTime"),
                })}
              </p>
            )}
          </div>
        </header>

        {alertMessage && (
          <p
            role="alert"
            className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
          >
            {alertMessage}
          </p>
        )}

        {rubric.length > 0 && (
          <dl className="m-0 flex flex-col divide-y divide-border border-t border-border pt-3">
            {rubric.map((criterion, index) => {
              const saved = active?.criteria?.find((entry) => entry.id === criterion.id);
              return (
                <div
                  key={criterion.id ?? index}
                  className="flex items-start justify-between gap-3 py-2 first:pt-0"
                >
                  <dt className="text-body text-text">
                    {index + 1}. <span data-exam-content>{criterion.description}</span>
                  </dt>
                  <dd className="m-0 shrink-0 text-body font-medium text-text-muted tabular-nums">
                    {saved?.points != null
                      ? `${format.number(saved.points, SCORE_NUMBER_FORMAT)} / ${format.number(criterion.points ?? 0, SCORE_NUMBER_FORMAT)}`
                      : `— / ${format.number(criterion.points ?? 0, SCORE_NUMBER_FORMAT)}`}
                  </dd>
                </div>
              );
            })}
          </dl>
        )}
      </Card>
    </section>
  );
}

/** Active grading form when the current user owns the draft claim and has `GRADING.MODIFY`. */
export function ActiveDraftGradingForm({
  detail,
  decimalSeparator,
  savingDraft,
  finalizing,
  confirming,
  confirmRef,
  finalizeButtonRef,
  serverError,
  isVersionConflict,
  staleServerNotice = false,
  onCriterionChange,
  onSaveDraft,
  onRequestFinalize,
  onConfirmFinalize,
  onCancelConfirm,
  onReloadDetail,
}: {
  detail: GradingItemDetailResponse;
  decimalSeparator: string;
  savingDraft: boolean;
  finalizing: boolean;
  confirming: boolean;
  confirmRef: Ref<HTMLDivElement>;
  finalizeButtonRef: Ref<HTMLButtonElement>;
  serverError: string | null;
  isVersionConflict: boolean;
  staleServerNotice?: boolean;
  onCriterionChange: (index: number, nextValue: string) => void;
  onSaveDraft: () => void;
  onRequestFinalize: () => void;
  onConfirmFinalize: () => void;
  onCancelConfirm: () => void;
  onReloadDetail: () => void;
}) {
  const t = useTranslations("grading.detail");
  const validationT = useTranslations("grading.detail.validation");
  const format = useFormatter();
  const headingId = useId();
  const confirmTitleId = useId();
  const fieldIdPrefix = useId();

  const { control, register, formState } = useFormContext<GradingItemFormValues>();
  const { errors } = useFormState({ control });
  const watchedCriteria = useWatch({ control, name: "criteria" });
  const watchedTotal = useWatch({ control, name: "totalPoints" });

  const rubric = detail.rubric ?? [];
  const pointsMax = detail.pointsMax ?? 0;

  const translateError = (key: string | undefined, max?: number) => {
    if (!key) return undefined;
    return validationT(key, {
      max: format.number(max ?? pointsMax, SCORE_NUMBER_FORMAT),
      limit: format.number(COMMENT_MAX),
      sum: format.number(criteriaInputTotal(watchedCriteria), SCORE_NUMBER_FORMAT),
    });
  };

  void decimalSeparator;

  return (
    <section aria-labelledby={headingId} className="min-w-0">
      <Card padding="none" className="flex flex-col gap-4 p-4 sm:p-5">
        <header className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id={headingId} className="text-h3 font-bold text-text">
            {t("rubric.title")}
          </h2>
          <p className="text-caption text-text-muted tabular-nums">
            {t("rubric.max", { max: format.number(pointsMax, SCORE_NUMBER_FORMAT) })}
          </p>
        </header>

        {rubric.length > 0 && (
          <fieldset className="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
            <legend className="sr-only">{t("rubric.legend")}</legend>
            {rubric.map((criterion, index) => {
              const criterionMax = criterion.points ?? pointsMax;
              const reg = register(`criteria.${index}`);
              const inputId = `${fieldIdPrefix}-c-${index}`;
              const hintId = `${inputId}-hint`;
              const errorMsg = translateError(errors.criteria?.[index]?.message, criterionMax);
              const errorId = `${inputId}-error`;
              const describedBy = [errorMsg ? errorId : null, hintId].filter(Boolean).join(" ");
              return (
                <div key={criterion.id ?? index} className="flex flex-col gap-1.5">
                  <Label htmlFor={inputId}>
                    {t("rubric.criterionPrefix", { index: index + 1 })}{" "}
                    <span data-exam-content>{criterion.description}</span>
                  </Label>
                  <div className="flex items-center gap-2">
                    <Input
                      id={inputId}
                      aria-invalid={errorMsg ? true : undefined}
                      aria-describedby={describedBy || undefined}
                      {...reg}
                      onChange={(event) => {
                        void reg.onChange(event);
                        onCriterionChange(index, event.target.value);
                      }}
                      inputMode="decimal"
                      autoComplete="off"
                      className="w-28"
                    />
                    <span aria-hidden className="text-body text-text-muted tabular-nums">
                      / {format.number(criterionMax, SCORE_NUMBER_FORMAT)}
                    </span>
                  </div>
                  <p id={hintId} className="text-caption text-text-muted">
                    {t("rubric.criterionHint", {
                      max: format.number(criterionMax, SCORE_NUMBER_FORMAT),
                    })}
                  </p>
                  {errorMsg && (
                    <p id={errorId} role="alert" className="text-caption text-danger">
                      {errorMsg}
                    </p>
                  )}
                </div>
              );
            })}
            <p className="flex flex-wrap items-baseline justify-between gap-2 rounded-lg bg-subtle px-3 py-2 text-body text-text">
              <span>{t("rubric.criteriaSum")}</span>
              <strong className="font-semibold tabular-nums">
                {t("rubric.subtotalValue", {
                  score: format.number(criteriaInputTotal(watchedCriteria), SCORE_NUMBER_FORMAT),
                  max: format.number(pointsMax, SCORE_NUMBER_FORMAT),
                })}
              </strong>
            </p>
          </fieldset>
        )}

        <FormField
          label={t("rubric.totalPointsLabel")}
          hint={t("rubric.totalPointsHint", {
            max: format.number(pointsMax, SCORE_NUMBER_FORMAT),
          })}
          error={translateError(errors.totalPoints?.message, pointsMax)}
        >
          {(controlProps) => (
            <div className="flex items-center gap-2">
              <Input
                {...controlProps}
                {...register("totalPoints")}
                inputMode="decimal"
                autoComplete="off"
                className="w-28"
              />
              <span aria-hidden className="text-body text-text-muted tabular-nums">
                / {format.number(pointsMax, SCORE_NUMBER_FORMAT)}
              </span>
            </div>
          )}
        </FormField>

        <FormField
          label={t("rubric.comment")}
          hint={t("rubric.commentHint")}
          error={translateError(errors.comment?.message)}
        >
          {(controlProps) => (
            <Textarea
              {...controlProps}
              {...register("comment")}
              rows={3}
              maxLength={COMMENT_MAX}
            />
          )}
        </FormField>

        {serverError && (
          <div
            role="alert"
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption text-danger"
          >
            <span>{serverError}</span>
            {isVersionConflict && (
              <Button type="button" variant="secondary" size="sm" onClick={onReloadDetail}>
                <RefreshCw />
                <span>{t("reload")}</span>
              </Button>
            )}
          </div>
        )}

        {staleServerNotice && !serverError && (
          <div
            role="status"
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-warning/40 bg-warning-bg px-3 py-2 text-caption text-warning"
          >
            <span>{t("staleNotice")}</span>
            <Button type="button" variant="secondary" size="sm" onClick={onReloadDetail}>
              <RefreshCw />
              <span>{t("reload")}</span>
            </Button>
          </div>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-3">
          <p className="text-caption text-text-muted">
            {formState.isDirty ? t("draft.unsaved") : t("draft.hint")}
          </p>
          <div className="flex flex-wrap justify-end gap-2">
            <Button
              type="button"
              variant="secondary"
              loading={savingDraft}
              disabled={finalizing}
              onClick={onSaveDraft}
            >
              <Save />
              <span>{t("draft.save")}</span>
            </Button>
            <Button
              ref={finalizeButtonRef}
              type="button"
              disabled={savingDraft || finalizing}
              onClick={onRequestFinalize}
            >
              <CircleCheckBig />
              <span>{t("finish.action")}</span>
            </Button>
          </div>
        </div>

        {confirming && (
          <div
            ref={confirmRef}
            tabIndex={-1}
            role="region"
            aria-labelledby={confirmTitleId}
            className={cn(
              "flex flex-col gap-3 rounded-lg border border-warning/40 bg-warning-bg p-4",
              focusRing,
            )}
          >
            <div className="flex items-start gap-2.5">
              <CircleCheckBig aria-hidden className="mt-0.5 size-4 shrink-0 text-warning" />
              <div className="flex flex-col gap-1">
                <p id={confirmTitleId} className="font-semibold text-text">
                  {t("finish.confirmTitle")}
                </p>
                <p className="text-body text-text-secondary">
                  {t("finish.confirmDescription", {
                    score: watchedTotal || "0",
                    max: format.number(pointsMax, SCORE_NUMBER_FORMAT),
                  })}
                </p>
              </div>
            </div>
            <div className="flex flex-wrap justify-end gap-2">
              <Button
                type="button"
                variant="secondary"
                size="sm"
                disabled={finalizing}
                onClick={onCancelConfirm}
              >
                {t("finish.cancel")}
              </Button>
              <Button
                type="button"
                size="sm"
                loading={finalizing}
                onClick={onConfirmFinalize}
              >
                {t("finish.confirm")}
              </Button>
            </div>
          </div>
        )}
      </Card>
    </section>
  );
}

/** Finalized read-only breakdown card with optional Reopen button when user has `GRADING.MODIFY`. */
export function FinalizedGradingCard({
  detail,
  canModify,
  focusOnMount,
  onOpenReopen,
}: {
  detail: GradingItemDetailResponse;
  canModify: boolean;
  focusOnMount: boolean;
  onOpenReopen: () => void;
}) {
  const t = useTranslations("grading.detail");
  const format = useFormatter();
  const headingId = useId();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const active = detail.activeGrade;
  const rubric = detail.rubric ?? [];
  const pointsMax = detail.pointsMax ?? 0;

  useEffect(() => {
    if (focusOnMount) headingRef.current?.focus();
  }, [focusOnMount]);

  return (
    <section aria-labelledby={headingId} className="min-w-0">
      <Card padding="none" className="flex flex-col gap-4 border-success/40 p-4 sm:p-5">
        <header className="flex flex-wrap items-center justify-between gap-2">
          <h2
            ref={headingRef}
            id={headingId}
            tabIndex={focusOnMount ? -1 : undefined}
            className={cn("flex items-center gap-2 rounded-sm text-h3 font-bold text-text", focusRing)}
          >
            <CircleCheckBig aria-hidden className="size-5 shrink-0 text-success" />
            {t("result.title")}
          </h2>
          <p className="text-h3 font-bold text-text tabular-nums">
            {t("rubric.subtotalValue", {
              score: format.number(active?.points ?? 0, SCORE_NUMBER_FORMAT),
              max: format.number(pointsMax, SCORE_NUMBER_FORMAT),
            })}
          </p>
        </header>

        {rubric.length > 0 && (
          <dl className="m-0 flex flex-col divide-y divide-border">
            {rubric.map((criterion, index) => {
              const saved = active?.criteria?.find((entry) => entry.id === criterion.id);
              return (
                <div
                  key={criterion.id ?? index}
                  className="flex items-start justify-between gap-3 py-2.5 first:pt-0"
                >
                  <dt className="text-body text-text">
                    {index + 1}. <span data-exam-content>{criterion.description}</span>
                  </dt>
                  <dd className="m-0 shrink-0 font-semibold whitespace-nowrap text-text tabular-nums">
                    {t("rubric.subtotalValue", {
                      score: format.number(saved?.points ?? 0, SCORE_NUMBER_FORMAT),
                      max: format.number(criterion.points ?? 0, SCORE_NUMBER_FORMAT),
                    })}
                  </dd>
                </div>
              );
            })}
          </dl>
        )}

        <div className="flex flex-col gap-1">
          <h3 className="text-label font-semibold text-text-secondary">{t("rubric.comment")}</h3>
          <p
            data-exam-content={active?.comment ? true : undefined}
            className={cn(
              "text-body whitespace-pre-line",
              active?.comment ? "text-text" : "text-text-muted",
            )}
          >
            {active?.comment || t("rubric.noComment")}
          </p>
        </div>

        {(active?.graderName || active?.finalizedAt) && (
          <dl className="m-0 flex flex-wrap gap-x-6 gap-y-1 border-t border-border pt-3 text-caption text-text-muted">
            {active.graderName && (
              <div className="flex gap-1">
                <dt>{t("summary.grader")}</dt>
                <dd className="m-0 font-medium text-text">{active.graderName}</dd>
              </div>
            )}
            {active.finalizedAt && (
              <div className="flex gap-1">
                <dt>{t("summary.gradedAt")}</dt>
                <dd className="m-0 font-medium text-text tabular-nums">
                  {format.dateTime(new Date(active.finalizedAt), "dateTime")}
                </dd>
              </div>
            )}
          </dl>
        )}

        {canModify ? (
          <div className="flex justify-end border-t border-border pt-3">
            <Button type="button" variant="secondary" onClick={onOpenReopen}>
              <RotateCcw />
              <span>{t("reopen.action")}</span>
            </Button>
          </div>
        ) : (
          <p className="border-t border-border pt-3 text-caption text-text-muted">
            {t("readOnlyDescription")}
          </p>
        )}
      </Card>
    </section>
  );
}

/** Past grading versions list when `detail.history` is non-empty. */
export function GradingHistoryCard({
  history,
  pointsMax,
}: {
  history: readonly EssayGradeView[];
  pointsMax: number;
}) {
  const t = useTranslations("grading.detail.history");
  const format = useFormatter();
  const headingId = useId();

  if (history.length === 0) return null;

  return (
    <section aria-labelledby={headingId} className="min-w-0">
      <Card padding="none" className="flex flex-col gap-3 p-4 sm:p-5">
        <h2 id={headingId} className="flex items-center gap-2 text-title font-semibold text-text">
          <History aria-hidden className="size-4 text-text-muted" />
          {t("title")}
        </h2>
        <ul className="m-0 flex list-none flex-col divide-y divide-border p-0">
          {history.map((entry, index) => (
            <li key={entry.id ?? index} className="flex flex-col gap-1 py-2.5 first:pt-0 last:pb-0">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="flex items-center gap-2">
                  <StatusChip
                    tone={
                      entry.state === "FINAL"
                        ? GRADING_STATUS_TONE.DONE
                        : GRADING_STATUS_TONE.IN_PROGRESS
                    }
                  >
                    {t(`states.${entry.state ?? "DRAFT"}`)}
                  </StatusChip>
                  <span className="text-body font-medium text-text">
                    {entry.graderName ?? t("unknownGrader")}
                  </span>
                </span>
                <span className="text-body font-semibold text-text tabular-nums">
                  {entry.points != null
                    ? `${format.number(entry.points, SCORE_NUMBER_FORMAT)} / ${format.number(pointsMax, SCORE_NUMBER_FORMAT)}`
                    : "—"}
                </span>
              </div>
              {entry.finalizedAt && (
                <p className="text-caption text-text-muted tabular-nums">
                  {t("finalizedAt", {
                    time: format.dateTime(new Date(entry.finalizedAt), "dateTime"),
                  })}
                </p>
              )}
              {entry.reopenedReason && (
                <p className="text-caption text-text-secondary">
                  {t.rich("reopenedReason", {
                    reason: entry.reopenedReason,
                    text: (chunks) => <span data-exam-content>{chunks}</span>,
                  })}
                </p>
              )}
            </li>
          ))}
        </ul>
      </Card>
    </section>
  );
}
