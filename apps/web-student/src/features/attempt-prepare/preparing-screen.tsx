"use client";

import { BrandLogo, Button, cn, focusRing } from "@thi360/ui";
import { Clock, LoaderCircle, RotateCw, TriangleAlert } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { type AttemptStateLoader, type AttemptStateResponse, loadAttemptState } from "./api";

export const PREPARING_FAST_POLL_MS = 1000;
export const PREPARING_SLOW_POLL_MS = 5000;
export const PREPARING_BUSY_THRESHOLD_MS = 90_000;

const KNOWN_ABORT_REASONS = new Set<string>([
  "GENERATION_FAILED",
  "GENERATION_TIMEOUT",
  "GENERATION_BUSY",
  "STALE_STARTING",
  "AUDIO_ASSET_MISSING",
  "AUDIO_ASSET_INVALID",
]);

type WaitingStatus = "waiting" | "busy" | "aborted";

export interface PreparingScreenProps {
  attemptId: string;
  examName?: string;
  examKindLabel?: string;
  scopeLabel?: string;
  firstPartName?: string;
  firstPartDurationMinutes?: number | null;
  timing?: "PER_PART" | "SHARED";
  initialState?: "STARTING" | "ABORTED";
  initialAbortReason?: AttemptStateResponse["abortReason"] | null;
  pollState?: AttemptStateLoader;
  onReady: (attemptId: string) => void;
  onRetry?: () => Promise<void> | void;
  retrying?: boolean;
  retryErrorMessage?: string | null;
}

export function PreparingScreen({
  attemptId,
  examName,
  examKindLabel,
  scopeLabel,
  firstPartName,
  firstPartDurationMinutes,
  timing,
  initialState = "STARTING",
  initialAbortReason = null,
  pollState = loadAttemptState,
  onReady,
  onRetry,
  retrying = false,
  retryErrorMessage = null,
}: PreparingScreenProps) {
  const t = useTranslations("attempt.waiting");
  const abortT = useTranslations("attempt.abortReasons");

  const pollKey = `${attemptId}:${initialState}`;
  const [pollOutcome, setPollOutcome] = useState<{
    key: string;
    status: WaitingStatus;
    abortReason: AttemptStateResponse["abortReason"] | null;
  } | null>(null);

  const status: WaitingStatus =
    initialState === "ABORTED"
      ? "aborted"
      : pollOutcome?.key === pollKey
        ? pollOutcome.status
        : "waiting";
  const abortReason: AttemptStateResponse["abortReason"] | null =
    initialState === "ABORTED"
      ? (initialAbortReason ?? null)
      : pollOutcome?.key === pollKey
        ? pollOutcome.abortReason
        : null;

  const pollStateRef = useRef(pollState);
  const onReadyRef = useRef(onReady);

  useEffect(() => {
    pollStateRef.current = pollState;
    onReadyRef.current = onReady;
  }, [pollState, onReady]);

  useEffect(() => {
    if (initialState === "ABORTED") {
      return;
    }

    let cancelled = false;
    let pollTimer: ReturnType<typeof setTimeout> | undefined;
    let inFlight = false;
    const startedAt = Date.now();

    const busyTimer = setTimeout(() => {
      if (!cancelled) {
        setPollOutcome((prev) =>
          prev?.key === pollKey && prev.status === "aborted"
            ? prev
            : { key: pollKey, status: "busy", abortReason: null },
        );
      }
    }, PREPARING_BUSY_THRESHOLD_MS);

    const scheduleNext = (delayMs: number) => {
      if (cancelled) return;
      pollTimer = setTimeout(() => {
        void runPoll();
      }, delayMs);
    };

    const runPoll = async () => {
      if (cancelled || inFlight) return;
      inFlight = true;
      try {
        const res = await pollStateRef.current(attemptId);
        if (cancelled) return;

        if (res.state === "ABORTED") {
          clearTimeout(busyTimer);
          setPollOutcome({
            key: pollKey,
            status: "aborted",
            abortReason: res.abortReason ?? null,
          });
          return;
        }

        if (res.state !== "STARTING") {
          clearTimeout(busyTimer);
          onReadyRef.current(attemptId);
          return;
        }
      } catch {
        if (cancelled) return;
      } finally {
        inFlight = false;
      }

      const elapsed = Date.now() - startedAt;
      if (elapsed >= PREPARING_BUSY_THRESHOLD_MS) {
        setPollOutcome((prev) =>
          prev?.key === pollKey && prev.status === "aborted"
            ? prev
            : { key: pollKey, status: "busy", abortReason: null },
        );
        scheduleNext(PREPARING_SLOW_POLL_MS);
      } else {
        scheduleNext(PREPARING_FAST_POLL_MS);
      }
    };

    scheduleNext(PREPARING_FAST_POLL_MS);

    return () => {
      cancelled = true;
      if (pollTimer) clearTimeout(pollTimer);
      clearTimeout(busyTimer);
    };
  }, [attemptId, initialState, pollKey]);

  const subtitle = [examKindLabel, scopeLabel].filter(Boolean).join(" · ");
  const hasMetadata = Boolean(firstPartName) || typeof firstPartDurationMinutes === "number";
  const reasonKey = abortReason && KNOWN_ABORT_REASONS.has(abortReason) ? abortReason : "generic";

  return (
    <div className="min-h-screen bg-page text-text">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex max-w-[1280px] flex-wrap items-center justify-between gap-3 px-4 py-2.5 sm:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <BrandLogo className="size-10" />
            <div className="min-w-0">
              {examName ? (
                <div data-exam-content className="truncate text-title font-semibold text-text">
                  {examName}
                </div>
              ) : (
                <div className="truncate text-title font-semibold text-text">{t("title")}</div>
              )}
              {subtitle && <div className="truncate text-caption text-text-muted">{subtitle}</div>}
            </div>
          </div>

          <div className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-dashed border-border-strong px-3.5 text-label font-medium text-text-muted">
            <Clock aria-hidden className="size-4.5 shrink-0" />
            <span>{t("clockPaused")}</span>
          </div>
        </div>
      </header>

      <main className="mx-auto flex max-w-[1280px] flex-col items-center px-4 py-12 sm:px-6">
        {status === "waiting" && (
          <section
            role="status"
            aria-labelledby="wait-title"
            className="flex w-full max-w-[560px] flex-col items-center gap-4 rounded-2xl border border-border bg-surface px-6 py-10 text-center sm:px-8"
          >
            <span
              aria-hidden
              className="flex size-16 items-center justify-center rounded-full bg-primary-50 text-primary-text"
            >
              <LoaderCircle className="size-8 animate-spin motion-reduce:animate-[spin_6s_linear_infinite]" />
            </span>
            <h1 id="wait-title" className="text-h2 font-bold text-text">
              {t("title")}
            </h1>
            <p className="max-w-[440px] text-body text-text-secondary">{t("description")}</p>
            <div
              aria-hidden
              className="h-1.5 w-full max-w-[360px] overflow-hidden rounded-full bg-subtle"
            >
              <div className="h-full w-2/5 animate-pulse rounded-full bg-primary-fill motion-reduce:animate-none" />
            </div>

            {hasMetadata && (
              <dl className="mt-2 flex w-full flex-col gap-2 rounded-xl bg-page px-4 py-3.5 text-left">
                {firstPartName && (
                  <div className="flex flex-wrap justify-between gap-x-3 gap-y-1">
                    <dt className="text-text-muted">{t("firstPartLabel")}</dt>
                    <dd data-exam-content className="font-semibold text-text">
                      {firstPartName}
                    </dd>
                  </div>
                )}
                {typeof firstPartDurationMinutes === "number" && (
                  <div className="flex flex-wrap justify-between gap-x-3 gap-y-1">
                    <dt className="text-text-muted">
                      {timing === "SHARED" ? t("durationSharedLabel") : t("durationLabel")}
                    </dt>
                    <dd className="font-semibold text-text tabular-nums">
                      {t("minutesValue", { minutes: firstPartDurationMinutes })}
                    </dd>
                  </div>
                )}
              </dl>
            )}

            <p className="text-caption text-text-muted">
              {t.rich("reassureNote", {
                myExams: (chunks) => (
                  <Link
                    href="/home"
                    className={cn("font-semibold text-primary-text hover:underline", focusRing)}
                  >
                    {chunks}
                  </Link>
                ),
              })}
            </p>
          </section>
        )}

        {status === "busy" && (
          <section
            role="status"
            aria-labelledby="busy-title"
            className="flex w-full max-w-[560px] flex-col items-center gap-4 rounded-2xl border border-border bg-surface px-6 py-10 text-center sm:px-8"
          >
            <p className="text-caption font-semibold text-text-muted">{t("busyNoticeLabel")}</p>
            <span
              aria-hidden
              className="flex size-14 items-center justify-center rounded-full bg-warning-bg text-warning"
            >
              <Clock className="size-7" />
            </span>
            <h1 id="busy-title" className="text-h2 font-bold text-text">
              {t("busyTitle")}
            </h1>
            <p className="max-w-[420px] text-body text-text-secondary">{t("busyDescription")}</p>

            {hasMetadata && (
              <dl className="mt-1 flex w-full flex-col gap-2 rounded-xl bg-page px-4 py-3.5 text-left">
                {firstPartName && (
                  <div className="flex flex-wrap justify-between gap-x-3 gap-y-1">
                    <dt className="text-text-muted">{t("firstPartLabel")}</dt>
                    <dd data-exam-content className="font-semibold text-text">
                      {firstPartName}
                    </dd>
                  </div>
                )}
                {typeof firstPartDurationMinutes === "number" && (
                  <div className="flex flex-wrap justify-between gap-x-3 gap-y-1">
                    <dt className="text-text-muted">
                      {timing === "SHARED" ? t("durationSharedLabel") : t("durationLabel")}
                    </dt>
                    <dd className="font-semibold text-text tabular-nums">
                      {t("minutesValue", { minutes: firstPartDurationMinutes })}
                    </dd>
                  </div>
                )}
              </dl>
            )}

            <Button asChild variant="secondary" className="mt-2 h-11">
              <Link href="/home">{t("backToMyExams")}</Link>
            </Button>
          </section>
        )}

        {status === "aborted" && (
          <section
            aria-labelledby="aborted-title"
            className="flex w-full max-w-[560px] flex-col items-center gap-4 rounded-2xl border border-border bg-surface px-6 py-10 text-center sm:px-8"
          >
            <p className="text-caption font-semibold text-text-muted">{t("failedNoticeLabel")}</p>
            <span
              aria-hidden
              className="flex size-14 items-center justify-center rounded-full bg-danger-bg text-danger"
            >
              <TriangleAlert className="size-7" />
            </span>
            <h1 id="aborted-title" className="text-h2 font-bold text-text">
              {t("failedHeading")}
            </h1>

            <div
              role="alert"
              className="flex max-w-[440px] flex-col gap-1.5 rounded-xl border border-danger bg-danger-bg px-4 py-3 text-body text-text-secondary"
            >
              <span className="font-semibold text-danger">{abortT(reasonKey)}</span>
              <span>{t("failedRefundNote")}</span>
            </div>

            <div className="mt-1 flex flex-wrap justify-center gap-3">
              <Button asChild variant="secondary" className="h-11">
                <Link href="/home">{t("backToMyExams")}</Link>
              </Button>
              {onRetry && (
                <Button
                  type="button"
                  variant="primary"
                  className="h-11"
                  loading={retrying}
                  onClick={() => void onRetry()}
                >
                  <RotateCw aria-hidden />
                  {t("retry")}
                </Button>
              )}
            </div>

            {retryErrorMessage && (
              <div
                role="alert"
                className="w-full rounded-xl border border-danger bg-danger-bg px-4 py-3 text-left text-body text-danger"
              >
                {retryErrorMessage}
              </div>
            )}
          </section>
        )}
      </main>
    </div>
  );
}
