"use client";

import { Button, Checkbox, cn, focusRing } from "@thi360/ui";
import { pickName } from "@thi360/web-kit/i18n";
import { Clock, LoaderCircle, RotateCw, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import type { AfterStartChoiceView, ChoiceOptionView, ChoiceSubjectView } from "./api";
import type { AttemptPartStateView } from "./mappers";
import { formatClockDuration, getRemainingMs, type ServerClockAnchor } from "./server-clock";

export const PART_CHOICE_ERROR_CODES = [
  "CHOICE_WINDOW_CLOSED",
  "PART_NOT_AWAITING",
  "CHOICE_LOCKED",
  "CHOICE_NOT_ALLOWED",
  "SUBJECTS_INVALID",
  "PART_GENERATING",
  "ATTEMPT_SESSION_REVOKED",
  "RATE_LIMITED",
] as const;

export interface PartChoiceViewProps {
  part: AttemptPartStateView;
  afterStartChoice: AfterStartChoiceView | null;
  locale: string;
  serverClock: ServerClockAnchor;
  onConfirmChoice: (optionCode: string, selectedSubjects?: string[]) => Promise<void>;
  onRetryGeneration: () => Promise<void>;
  errorCode?: string | null;
  busyRetryAfterSec?: number | null;
  isSubmitting?: boolean;
  isRetrying?: boolean;
  readOnly?: boolean;
}

function getNormalizedState(state?: string): "AWAITING_CHOICE" | "GENERATING" | "GENERATION_FAILED" {
  if (state === "GENERATION_FAILED" || state === "GEN_FAILED") {
    return "GENERATION_FAILED";
  }
  if (state === "GENERATING") {
    return "GENERATING";
  }
  return "AWAITING_CHOICE";
}

function computeAnchorRemainingMs(
  deadlineIso: string | undefined,
  serverClock: ServerClockAnchor,
): number {
  if (!deadlineIso) return 0;
  const deadlineMs = Date.parse(deadlineIso);
  if (!Number.isFinite(deadlineMs)) return 0;
  return Math.max(0, deadlineMs - serverClock.serverNowMs);
}

function formatErrorMessage(
  t: ReturnType<typeof useTranslations<"attempt.partChoice">>,
  code: string | null | undefined,
): string | null {
  if (!code) return null;
  if ((PART_CHOICE_ERROR_CODES as readonly string[]).includes(code)) {
    return t(`errors.${code as (typeof PART_CHOICE_ERROR_CODES)[number]}`);
  }
  return t("errors.generic");
}

function formatSubjectName(subject: ChoiceSubjectView, locale: string): string {
  return pickName({ nameVi: subject.nameVi, nameEn: subject.nameEn }, locale) || subject.code || "";
}

export function PartChoiceView({
  part,
  afterStartChoice,
  locale,
  serverClock,
  onConfirmChoice,
  onRetryGeneration,
  errorCode = null,
  busyRetryAfterSec = null,
  isSubmitting = false,
  isRetrying = false,
  readOnly = false,
}: PartChoiceViewProps) {
  const t = useTranslations("attempt.partChoice");
  const normState = getNormalizedState(part.state);
  const options: ChoiceOptionView[] = afterStartChoice?.options ?? [];
  const defaultCode = options.length === 1 ? (options[0]?.code ?? "") : "";
  const selectionScopeKey = `${part.partId ?? ""}:${part.ord ?? 0}:${defaultCode}`;

  const [selectionState, setSelectionState] = useState<{
    scopeKey: string;
    selectedCode: string;
    selectedSubjects: string[];
  }>(() => ({
    scopeKey: selectionScopeKey,
    selectedCode: defaultCode,
    selectedSubjects: [],
  }));

  const selectedCode =
    selectionState.scopeKey === selectionScopeKey ? selectionState.selectedCode : defaultCode;
  const selectedSubjects =
    selectionState.scopeKey === selectionScopeKey ? selectionState.selectedSubjects : [];

  const deadlineKey = `${part.choiceDeadlineAt ?? ""}:${serverClock.serverNowMs}:${serverClock.perfNowMs}`;
  const [timerState, setTimerState] = useState<{
    deadlineKey: string;
    remainingMs: number;
  }>(() => ({
    deadlineKey,
    remainingMs: computeAnchorRemainingMs(part.choiceDeadlineAt, serverClock),
  }));

  const remainingMs =
    timerState.deadlineKey === deadlineKey
      ? timerState.remainingMs
      : computeAnchorRemainingMs(part.choiceDeadlineAt, serverClock);

  useEffect(() => {
    if (!part.choiceDeadlineAt || normState !== "AWAITING_CHOICE") return;
    const syncRemaining = () => {
      const nextMs = getRemainingMs(part.choiceDeadlineAt, serverClock) ?? 0;
      setTimerState({ deadlineKey, remainingMs: nextMs });
    };
    const syncTimeout = window.setTimeout(syncRemaining, 0);
    const interval = window.setInterval(syncRemaining, 1000);
    return () => {
      window.clearTimeout(syncTimeout);
      window.clearInterval(interval);
    };
  }, [deadlineKey, normState, part.choiceDeadlineAt, serverClock]);

  if (normState === "GENERATING") {
    return (
      <section
        role="status"
        aria-live="polite"
        className="mx-auto flex max-w-xl flex-col items-center gap-4 rounded-2xl border border-border bg-surface p-8 text-center shadow-sm"
      >
        <div className="flex size-12 items-center justify-center rounded-full bg-primary-50 text-primary-700">
          <LoaderCircle className="size-6 animate-spin motion-reduce:animate-none" aria-hidden="true" />
        </div>
        <h2 className="text-h3 font-bold text-text">{t("generatingTitle")}</h2>
        <p className="text-body text-text-muted">{t("generatingDescription")}</p>
        {part.partName ? (
          <p className="text-caption font-medium text-text-secondary" data-exam-content="">
            {part.partName}
          </p>
        ) : null}
      </section>
    );
  }

  if (normState === "GENERATION_FAILED") {
    return (
      <section
        role="alert"
        className="mx-auto flex max-w-xl flex-col items-center gap-4 rounded-2xl border border-danger bg-surface p-8 text-center shadow-sm"
      >
        <div className="flex size-12 items-center justify-center rounded-full bg-danger-bg text-danger">
          <TriangleAlert className="size-6" aria-hidden="true" />
        </div>
        <h2 className="text-h3 font-bold text-text">{t("failedTitle")}</h2>
        <p className="text-body text-text-muted">{t("failedDescription")}</p>
        <Button
          type="button"
          variant="primary"
          disabled={readOnly || isRetrying}
          onClick={() => void onRetryGeneration()}
        >
          <RotateCw className={cn("size-4", isRetrying && "animate-spin")} aria-hidden="true" />
          <span>{t("failedRetry")}</span>
        </Button>
      </section>
    );
  }

  const effectiveSelectedCode = selectedCode || (options.length === 1 ? (options[0]?.code ?? "") : "");
  const activeOption = options.find((opt) => opt.code === effectiveSelectedCode) ?? null;
  const requiredSubjectsCount = activeOption?.chooseSubjects ?? 0;
  const subjectPool: ChoiceSubjectView[] = activeOption?.subjects ?? [];
  const isSubjectPicker = requiredSubjectsCount > 0 && subjectPool.length > 0;
  const isExpired = Boolean(part.choiceDeadlineAt) && remainingMs <= 0;
  const isDeadlineUrgent = Boolean(part.choiceDeadlineAt) && remainingMs > 0 && remainingMs <= 30_000;

  const canConfirm =
    !readOnly &&
    !isSubmitting &&
    !isExpired &&
    Boolean(effectiveSelectedCode) &&
    (!isSubjectPicker || selectedSubjects.length === requiredSubjectsCount);

  const mappedError = formatErrorMessage(t, errorCode);

  function selectOption(code: string) {
    setSelectionState({
      scopeKey: selectionScopeKey,
      selectedCode: code,
      selectedSubjects: [],
    });
  }

  function toggleSubject(subjectCode: string) {
    setSelectionState((prev) => {
      const baseCode = prev.scopeKey === selectionScopeKey ? prev.selectedCode : effectiveSelectedCode;
      const baseSubjects = prev.scopeKey === selectionScopeKey ? prev.selectedSubjects : [];
      if (baseSubjects.includes(subjectCode)) {
        return {
          scopeKey: selectionScopeKey,
          selectedCode: baseCode,
          selectedSubjects: baseSubjects.filter((item) => item !== subjectCode),
        };
      }
      if (requiredSubjectsCount > 0 && baseSubjects.length >= requiredSubjectsCount) {
        return prev;
      }
      return {
        scopeKey: selectionScopeKey,
        selectedCode: baseCode,
        selectedSubjects: [...baseSubjects, subjectCode],
      };
    });
  }

  return (
    <section
      aria-labelledby="part-choice-heading"
      className="mx-auto flex max-w-2xl flex-col gap-5 rounded-2xl border border-border bg-surface p-6 shadow-sm"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h2 id="part-choice-heading" className="text-h3 font-bold text-text">
            {t("title")}
          </h2>
          <p className="text-body text-text-muted">{t("description")}</p>
          {part.partName ? (
            <p className="text-caption font-semibold text-primary-800" data-exam-content="">
              {part.partName}
            </p>
          ) : null}
        </div>

        {part.choiceDeadlineAt ? (
          <div
            role="timer"
            aria-label={t("timerLabel")}
            className={cn(
              "inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-label font-semibold tabular-nums",
              isExpired || isDeadlineUrgent
                ? "border-danger bg-danger-bg text-danger"
                : "border-warning bg-warning-bg text-warning",
            )}
          >
            <Clock className="size-4 shrink-0" aria-hidden="true" />
            <span>{isExpired ? t("deadlineExpired") : formatClockDuration(remainingMs)}</span>
          </div>
        ) : null}
      </div>

      {isDeadlineUrgent ? (
        <p role="status" className="rounded-lg border border-warning bg-warning-bg px-3 py-2 text-caption text-warning">
          {t("deadlineWarning")}
        </p>
      ) : null}

      {busyRetryAfterSec !== null && busyRetryAfterSec > 0 ? (
        <p role="status" className="rounded-lg border border-warning bg-warning-bg px-3 py-2 text-caption text-warning">
          {t("busyNotice")}
        </p>
      ) : null}

      {mappedError ? (
        <div
          role="alert"
          className="rounded-lg border border-danger bg-danger-bg px-3 py-2 text-caption font-medium text-danger"
        >
          {mappedError}
        </div>
      ) : null}

      <fieldset disabled={readOnly || isSubmitting} className="space-y-3">
        <legend className="sr-only">{t("legendOption")}</legend>
        {options.map((option) => {
          const code = option.code ?? "";
          const isChecked = effectiveSelectedCode === code;
          const orderedSubjects: ChoiceSubjectView[] = option.subjects ?? [];
          const counts = option.countsByPick?.length ? option.countsByPick.join("/") : null;

          return (
            <label
              key={code}
              className={cn(
                "flex cursor-pointer flex-col gap-2 rounded-xl border p-4 transition-colors",
                isChecked
                  ? "border-primary-600 bg-primary-50"
                  : "border-border bg-surface hover:border-border-strong hover:bg-subtle",
              )}
            >
              <div className="flex items-start gap-3">
                <input
                  type="radio"
                  name="attempt-part-choice"
                  value={code}
                  checked={isChecked}
                  onChange={() => selectOption(code)}
                  className={cn("mt-0.5 size-4.5 shrink-0 accent-primary-600", focusRing)}
                />
                <div className="min-w-0 flex-1 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    {code ? (
                      <span className="rounded bg-subtle px-1.5 py-0.5 font-mono text-caption font-semibold text-text-secondary">
                        {code}
                      </span>
                    ) : null}
                    <span className="text-title font-semibold text-text" data-exam-content="">
                      {option.name ?? code}
                    </span>
                  </div>

                  {counts ? (
                    <p className="text-caption text-text-muted">
                      {t("countsPerSubject", { counts })}
                    </p>
                  ) : null}

                  {orderedSubjects.length > 0 && !(option.chooseSubjects && option.chooseSubjects > 0) ? (
                    <div className="flex flex-wrap gap-1.5 pt-1">
                      {orderedSubjects.map((subject, idx) => (
                        <span
                          key={subject.code ?? idx}
                          className="rounded-md border border-border bg-surface px-2 py-0.5 text-caption text-text-secondary"
                        >
                          {idx + 1}. {formatSubjectName(subject, locale)}
                        </span>
                      ))}
                    </div>
                  ) : null}
                </div>
              </div>
            </label>
          );
        })}
      </fieldset>

      {isSubjectPicker ? (
        <fieldset
          disabled={readOnly || isSubmitting}
          className="space-y-3 rounded-xl border border-border bg-subtle p-4"
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <legend className="text-label font-semibold text-text">{t("legendSubjects")}</legend>
            <span
              aria-live="polite"
              className="rounded-full border border-border bg-surface px-2.5 py-0.5 text-caption font-semibold text-primary-800 tabular-nums"
            >
              {t("selectedProgress", {
                selected: selectedSubjects.length,
                total: requiredSubjectsCount,
              })}
            </span>
          </div>

          <div className="grid gap-2 sm:grid-cols-2">
            {subjectPool.map((subject) => {
              const sCode = subject.code ?? "";
              const checked = selectedSubjects.includes(sCode);
              const limitReached = !checked && selectedSubjects.length >= requiredSubjectsCount;

              return (
                <label
                  key={sCode}
                  className={cn(
                    "flex cursor-pointer items-center gap-2.5 rounded-lg border px-3 py-2.5 text-label transition-colors",
                    checked
                      ? "border-primary-600 bg-surface text-text"
                      : limitReached
                        ? "cursor-not-allowed border-border bg-surface text-text-muted opacity-55"
                        : "border-border bg-surface text-text-secondary hover:border-border-strong",
                  )}
                >
                  <Checkbox
                    checked={checked}
                    disabled={limitReached || readOnly || isSubmitting}
                    onCheckedChange={() => toggleSubject(sCode)}
                  />
                  <span>{formatSubjectName(subject, locale)}</span>
                </label>
              );
            })}
          </div>

          {selectedSubjects.length >= requiredSubjectsCount ? (
            <p className="text-caption text-text-muted">
              {t("subjectsLimitReached", { total: requiredSubjectsCount })}
            </p>
          ) : null}
        </fieldset>
      ) : null}

      <div className="flex justify-end pt-2">
        <Button
          type="button"
          variant="primary"
          disabled={!canConfirm}
          onClick={() =>
            void onConfirmChoice(
              effectiveSelectedCode,
              isSubjectPicker ? selectedSubjects : undefined,
            )
          }
        >
          {t("confirmCta")}
        </Button>
      </div>
    </section>
  );
}
