"use client";

import { useQuery } from "@tanstack/react-query";
import { Breadcrumb, Button, Checkbox, cn, EmptyState, focusRing, Skeleton } from "@thi360/ui";
import { RichTextView } from "@thi360/ui/rich-text";
import { pickName } from "@thi360/web-kit/i18n";
import {
  ArrowLeft,
  ArrowRight,
  Clock,
  Headphones,
  Info,
  ShieldCheck,
  TriangleAlert,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type ReactNode, useRef, useState } from "react";
import { type HeaderAccount, StudentHeader } from "@/components/student-header";
import { loadAttemptStartContext, saveAttemptStartContext } from "@/features/attempt-draft";
import { loadSession, SESSION_QUERY_KEY, type SessionLoader } from "@/features/session/api";
import { formatDayMonth, REVIEW_BUILD_MODE } from "@/features/storefront/format";
import { readSupportContact } from "@/features/storefront/support";
import { ApiStatusError } from "@/lib/api/client";
import { adminUrl } from "@/lib/env";
import {
  ATTEMPT_PREPARE_QUERY_KEY,
  type AttemptPrepareLoader,
  type AttemptPrepareResponse,
  type AttemptStarter,
  type AttemptStateLoader,
  type AttemptStateResponse,
  type ChoiceOptionWithMetrics,
  loadAttemptPrepare,
  loadAttemptState,
  type PreChoiceView,
  startAttempt,
  type StartAttemptRequest,
} from "./api";
import { resolveSelectedPathMetrics } from "./metrics";
import { PreparingScreen } from "./preparing-screen";

const KNOWN_ANSWER_FORMATS = new Set<string>([
  "SINGLE_CHOICE",
  "MULTIPLE_CHOICE",
  "SHORT_ANSWER",
  "CLOZE",
  "TRUE_FALSE",
  "MATCHING",
  "ESSAY",
  "QUESTION_SET",
]);

const KNOWN_START_ERRORS = new Set<string>([
  "NO_ATTEMPTS_LEFT",
  "CHOICE_NOT_ALLOWED",
  "NO_SERVING_OPTION",
  "SUBJECTS_INVALID",
  "RATE_LIMITED",
]);

type StartErrorKey =
  | "NO_ATTEMPTS_LEFT"
  | "CHOICE_NOT_ALLOWED"
  | "NO_SERVING_OPTION"
  | "SUBJECTS_INVALID"
  | "RATE_LIMITED"
  | "generic";

const CARD_CLASS = "flex flex-col gap-4 rounded-2xl border border-border bg-surface p-6";

function SectionHeading({ id, children }: { id: string; children: ReactNode }) {
  return (
    <div className="flex items-center gap-3">
      <span aria-hidden className="h-6 w-1 rounded-sm bg-primary-fill" />
      <h2 id={id} className="text-h3 font-bold text-text">
        {children}
      </h2>
    </div>
  );
}

function formatSupportSummary(): string {
  const contact = readSupportContact();
  const channels = [contact.phone, contact.email].filter(Boolean).join(" · ");
  if (!channels) return contact.hours;
  return contact.hours ? `${channels} (${contact.hours})` : channels;
}

function resolveInitialOptionCode(preChoice: PreChoiceView | undefined): string | null {
  const options = preChoice?.options ?? [];
  if (options.length === 0) return null;
  if (preChoice?.preselectedCode) {
    const matched = options.find((opt) => opt.code === preChoice.preselectedCode);
    if (matched?.code) return matched.code;
  }
  if (options.length === 1) {
    return options[0]?.code ?? null;
  }
  return null;
}

function mapStartError(error: unknown): StartErrorKey {
  if (error instanceof ApiStatusError) {
    if (error.status === 429 || error.code === "RATE_LIMITED") {
      return "RATE_LIMITED";
    }
    if (KNOWN_START_ERRORS.has(error.code)) {
      return error.code as StartErrorKey;
    }
  }
  return "generic";
}

function isNotFoundError(error: unknown): boolean {
  return error instanceof ApiStatusError && error.status === 404;
}

export interface PrepareViewProps {
  entitlementId: string;
  loadCurrentSession?: SessionLoader;
  loadPrepare?: AttemptPrepareLoader;
  onStartAttempt?: AttemptStarter;
  pollAttemptState?: AttemptStateLoader;
  navigate?: (url: string) => void;
}

export function PrepareView({
  entitlementId,
  loadCurrentSession = loadSession,
  loadPrepare = loadAttemptPrepare,
  onStartAttempt = startAttempt,
  pollAttemptState = loadAttemptState,
  navigate,
}: PrepareViewProps) {
  const router = useRouter();
  const t = useTranslations("prepare");
  const common = useTranslations("common");
  const locale = useLocale();

  const go = navigate ?? ((url: string) => router.push(url));

  const sessionQuery = useQuery({
    queryKey: SESSION_QUERY_KEY,
    queryFn: loadCurrentSession,
    staleTime: 60_000,
  });

  const session = sessionQuery.data;
  const isStudent = session?.access === "EXAM";

  const prepareQuery = useQuery({
    queryKey: ATTEMPT_PREPARE_QUERY_KEY(session?.id ?? "anon", entitlementId),
    queryFn: () => loadPrepare(entitlementId),
    enabled: isStudent,
    retry: (failureCount, error) => !isNotFoundError(error) && failureCount < 1,
  });

  const prepare = prepareQuery.data;

  const [selectionState, setSelectionState] = useState<{
    key: string;
    optionCode: string | null;
    subjectCodes: string[];
  } | null>(null);
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState<StartErrorKey | null>(null);
  const [retryError, setRetryError] = useState<StartErrorKey | null>(null);
  const [waitingAttempt, setWaitingAttempt] = useState<{
    attemptId: string;
    initialState: "STARTING" | "ABORTED";
    initialAbortReason?: AttemptStateResponse["abortReason"] | null;
    preChoice?: StartAttemptRequest["preChoice"];
  } | null>(null);

  const inFlightStartRef = useRef(false);

  const preChoiceOptions = (prepare?.preChoice?.options ?? []) as ChoiceOptionWithMetrics[];
  const preChoiceKey = prepare?.preChoice
    ? `${entitlementId}:${prepare.preChoice.partCode ?? ""}:${prepare.preChoice.preselectedCode ?? ""}:${preChoiceOptions.map((o) => o.code ?? "").join(",")}`
    : `${entitlementId}:none`;

  const selectedOptionCode =
    selectionState?.key === preChoiceKey
      ? selectionState.optionCode
      : resolveInitialOptionCode(prepare?.preChoice);
  const selectedSubjectCodes =
    selectionState?.key === preChoiceKey ? selectionState.subjectCodes : [];

  const selectedOption =
    preChoiceOptions.find((opt) => opt.code === selectedOptionCode) ?? null;

  const account: HeaderAccount = session
    ? { kind: "user", session }
    : sessionQuery.isLoading
      ? { kind: "loading" }
      : { kind: "none" };

  const resolveScopeLabel = (data: AttemptPrepareResponse): string => {
    const scopeKey = data.scope;
    const baseScope =
      scopeKey === "FULL" || scopeKey === "PART" || scopeKey === "SUBJECT"
        ? t(`scope.${scopeKey}`)
        : scopeKey;
    if (data.subject) {
      const subjectLabel = pickName(data.subject, locale) || data.subject.code || "";
      if (subjectLabel) {
        return `${baseScope} · ${subjectLabel}`;
      }
    }
    return baseScope;
  };

  async function executeStart(
    data: AttemptPrepareResponse,
    preChoicePayload: StartAttemptRequest["preChoice"] | undefined,
    fromRetry: boolean,
  ) {
    if (!session || inFlightStartRef.current) return;
    inFlightStartRef.current = true;
    setStarting(true);
    if (fromRetry) {
      setRetryError(null);
    } else {
      setStartError(null);
    }

    const metrics = resolveSelectedPathMetrics(data, selectedOption);
    const examKindLabel = pickName(data.examKind, locale) || data.examKind.code || "";
    const firstPart = [...data.parts].sort((a, b) => (a.ord ?? 0) - (b.ord ?? 0))[0];
    const firstPartDuration =
      data.timing === "SHARED"
        ? metrics.durationMinutes
        : (firstPart?.durationMinutes ?? null);

    try {
      const idempotencyKey = crypto.randomUUID();
      const response = await onStartAttempt(
        {
          entitlementId,
          ...(preChoicePayload ? { preChoice: preChoicePayload } : {}),
        },
        idempotencyKey,
      );

      await saveAttemptStartContext(session.id, response.attemptId, {
        entitlementId,
        preChoice: preChoicePayload ?? null,
        examName: data.examName,
        examKind: examKindLabel,
        firstPartName: firstPart?.name,
        firstPartDurationMinutes: firstPartDuration,
      });

      if (response.state === "IN_PROGRESS") {
        go(`/attempts/${encodeURIComponent(response.attemptId)}`);
        return;
      }

      if (response.state === "ABORTED") {
        setWaitingAttempt({
          attemptId: response.attemptId,
          initialState: "ABORTED",
          initialAbortReason: response.abortReason ?? null,
          preChoice: preChoicePayload,
        });
        return;
      }

      setWaitingAttempt({
        attemptId: response.attemptId,
        initialState: "STARTING",
        preChoice: preChoicePayload,
      });
    } catch (err) {
      const code = mapStartError(err);
      if (fromRetry) {
        setRetryError(code);
      } else {
        setStartError(code);
      }
    } finally {
      inFlightStartRef.current = false;
      setStarting(false);
    }
  }

  // Waiting / preparing screen (from clicking Start OR reloading while openAttempt is STARTING, AC-3, AC-4, AC-5)
  const openStartingId =
    !waitingAttempt && prepare?.openAttempt?.state === "STARTING" && prepare.openAttempt.attemptId
      ? prepare.openAttempt.attemptId
      : null;
  const activeWaiting = waitingAttempt ?? (openStartingId ? { attemptId: openStartingId, initialState: "STARTING" as const } : null);

  if (activeWaiting && prepare && session) {
    const examKindLabel = pickName(prepare.examKind, locale) || prepare.examKind.code || "";
    const scopeLabel = resolveScopeLabel(prepare);
    const orderedParts = [...prepare.parts].sort((a, b) => (a.ord ?? 0) - (b.ord ?? 0));
    const firstPart = orderedParts[0];
    const metrics = resolveSelectedPathMetrics(prepare, selectedOption);
    const firstPartDuration =
      prepare.timing === "SHARED"
        ? metrics.durationMinutes
        : (firstPart?.durationMinutes ?? null);

    const resolveRetryPreChoice = async (): Promise<{
      canRetryDirectly: boolean;
      payload?: StartAttemptRequest["preChoice"];
    }> => {
      if (activeWaiting.preChoice) {
        return { canRetryDirectly: true, payload: activeWaiting.preChoice };
      }
      const saved = await loadAttemptStartContext(session.id, activeWaiting.attemptId);
      if (saved?.preChoice) {
        return { canRetryDirectly: true, payload: saved.preChoice };
      }
      if (!prepare.preChoice) {
        return { canRetryDirectly: true, payload: undefined };
      }
      const autoCode = resolveInitialOptionCode(prepare.preChoice);
      const autoOption = preChoiceOptions.find((opt) => opt.code === autoCode);
      if (autoOption?.code && (autoOption.chooseSubjects ?? 0) <= 0) {
        return { canRetryDirectly: true, payload: { optionCode: autoOption.code } };
      }
      return { canRetryDirectly: false };
    };

    const handleWaitingRetry = async () => {
      const resolved = await resolveRetryPreChoice();
      if (!resolved.canRetryDirectly) {
        setWaitingAttempt(null);
        await prepareQuery.refetch();
        return;
      }
      await executeStart(prepare, resolved.payload, true);
    };

    const retryErrorMessage = retryError ? t(`startErrors.${retryError}`) : null;

    return (
      <PreparingScreen
        key={activeWaiting.attemptId}
        attemptId={activeWaiting.attemptId}
        examName={prepare.examName}
        examKindLabel={examKindLabel}
        scopeLabel={scopeLabel}
        firstPartName={firstPart?.name}
        firstPartDurationMinutes={firstPartDuration}
        timing={prepare.timing}
        initialState={activeWaiting.initialState}
        initialAbortReason={activeWaiting.initialAbortReason}
        pollState={pollAttemptState}
        onReady={(readyAttemptId) => go(`/attempts/${encodeURIComponent(readyAttemptId)}`)}
        onRetry={handleWaitingRetry}
        retrying={starting}
        retryErrorMessage={retryErrorMessage}
      />
    );
  }

  return (
    <div className="min-h-screen bg-page text-text">
      <StudentHeader account={account} />

      <main className="mx-auto flex max-w-[1440px] flex-col gap-6 px-4 py-8 sm:px-6">
        {(sessionQuery.isLoading || (isStudent && prepareQuery.isLoading && !prepare)) && (
          <div
            role="status"
            aria-busy="true"
            aria-label={common("loading")}
            className="flex flex-col gap-6"
          >
            <Skeleton className="h-5 w-48" />
            <div className="flex flex-col gap-2">
              <div className="flex gap-2">
                <Skeleton className="h-6 w-28 rounded-full" />
                <Skeleton className="h-6 w-24 rounded-full" />
              </div>
              <Skeleton className="h-9.5 w-96 max-w-full" />
              <Skeleton className="h-5 w-72 max-w-full" />
            </div>
            <div className="flex flex-wrap items-start gap-6">
              <div className="flex min-w-0 flex-[999_1_35rem] flex-col gap-5">
                <Skeleton className="h-64 w-full rounded-2xl" />
                <Skeleton className="h-44 w-full rounded-2xl" />
              </div>
              <Skeleton className="h-80 w-full flex-[1_1_18.75rem] rounded-2xl sm:max-w-[380px]" />
            </div>
          </div>
        )}

        {sessionQuery.isError && (
          <div className="rounded-2xl border border-border bg-surface">
            <EmptyState
              headingLevel={1}
              icon={<TriangleAlert />}
              title={t("errorLoad.title")}
              description={t("errorLoad.description")}
              action={
                <Button type="button" onClick={() => void sessionQuery.refetch()}>
                  {common("retry")}
                </Button>
              }
            />
          </div>
        )}

        {session && session.access === "MANAGE" && (
          <div className="rounded-2xl border border-border bg-surface">
            <EmptyState
              headingLevel={1}
              icon={<ShieldCheck />}
              title={t("adminNotice.title")}
              description={t("adminNotice.description")}
              action={
                <Button asChild className="h-11">
                  <a href={adminUrl()}>{t("adminNotice.cta")}</a>
                </Button>
              }
            />
          </div>
        )}

        {isStudent && prepareQuery.isError && isNotFoundError(prepareQuery.error) && (
          <div className="rounded-2xl border border-border bg-surface">
            <EmptyState
              headingLevel={1}
              icon={<TriangleAlert />}
              title={t("notFound.title")}
              description={t("notFound.description")}
              action={
                <Button asChild className="h-11">
                  <Link href="/home">{t("backToMyExams")}</Link>
                </Button>
              }
            />
          </div>
        )}

        {isStudent && prepareQuery.isError && !isNotFoundError(prepareQuery.error) && (
          <div className="rounded-2xl border border-border bg-surface">
            <EmptyState
              headingLevel={1}
              icon={<TriangleAlert />}
              title={t("errorLoad.title")}
              description={t("errorLoad.description")}
              action={
                <div className="flex flex-wrap justify-center gap-3">
                  <Button asChild variant="secondary" className="h-11">
                    <Link href="/home">{t("backToMyExams")}</Link>
                  </Button>
                  <Button type="button" className="h-11" onClick={() => void prepareQuery.refetch()}>
                    {common("retry")}
                  </Button>
                </div>
              }
            />
          </div>
        )}

        {isStudent && prepare && (
          <PopulatedPrepareContent
            prepare={prepare}
            locale={locale}
            selectedOptionCode={selectedOptionCode}
            selectedSubjectCodes={selectedSubjectCodes}
            selectedOption={selectedOption}
            preChoiceOptions={preChoiceOptions}
            starting={starting}
            startError={startError}
            onSelectOption={(code) => {
              setStartError(null);
              setSelectionState({ key: preChoiceKey, optionCode: code, subjectCodes: [] });
            }}
            onToggleSubject={(subjectCode, maxSelect) => {
              setStartError(null);
              const current = selectedSubjectCodes;
              if (current.includes(subjectCode)) {
                setSelectionState({
                  key: preChoiceKey,
                  optionCode: selectedOptionCode,
                  subjectCodes: current.filter((c) => c !== subjectCode),
                });
              } else if (current.length < maxSelect) {
                setSelectionState({
                  key: preChoiceKey,
                  optionCode: selectedOptionCode,
                  subjectCodes: [...current, subjectCode],
                });
              }
            }}
            onClickStart={(payload) => void executeStart(prepare, payload, false)}
            resolveScopeLabel={resolveScopeLabel}
          />
        )}
      </main>
    </div>
  );
}

function PopulatedPrepareContent({
  prepare,
  locale,
  selectedOptionCode,
  selectedSubjectCodes,
  selectedOption,
  preChoiceOptions,
  starting,
  startError,
  onSelectOption,
  onToggleSubject,
  onClickStart,
  resolveScopeLabel,
}: {
  prepare: AttemptPrepareResponse;
  locale: string;
  selectedOptionCode: string | null;
  selectedSubjectCodes: string[];
  selectedOption: ChoiceOptionWithMetrics | null;
  preChoiceOptions: ChoiceOptionWithMetrics[];
  starting: boolean;
  startError: StartErrorKey | null;
  onSelectOption: (code: string) => void;
  onToggleSubject: (subjectCode: string, maxSelect: number) => void;
  onClickStart: (preChoicePayload?: StartAttemptRequest["preChoice"]) => void;
  resolveScopeLabel: (data: AttemptPrepareResponse) => string;
}) {
  const t = useTranslations("prepare");
  const supportSummary = formatSupportSummary();
  const examKindName = pickName(prepare.examKind, locale) || prepare.examKind.code || "";
  const scopeLabel = resolveScopeLabel(prepare);
  const orderedParts = [...prepare.parts].sort((a, b) => (a.ord ?? 0) - (b.ord ?? 0));
  const metrics = resolveSelectedPathMetrics(prepare, selectedOption);

  const summaryItems = [
    metrics.partCount > 0 ? t("units.partCount", { count: metrics.partCount }) : null,
    metrics.questionCount !== null ? t("units.questions", { count: metrics.questionCount }) : null,
    metrics.durationMinutes !== null
      ? t("units.minutes", { count: metrics.durationMinutes })
      : null,
    metrics.maxPoints !== null ? t("units.points", { value: metrics.maxPoints }) : null,
  ].filter(Boolean);

  const timingKey =
    prepare.navigationMode === "FREE"
      ? "FREE"
      : prepare.timing === "PER_PART"
        ? "PER_PART"
        : "SHARED";

  const hasOpenInProgress =
    prepare.openAttempt?.state === "IN_PROGRESS" && Boolean(prepare.openAttempt.attemptId);
  const hasPreChoice = Boolean(prepare.preChoice);
  const noServingOptions = hasPreChoice && preChoiceOptions.length === 0;
  const requiredSubjects =
    selectedOption?.kind === "SUBJECT_CHOICE" ? (selectedOption.chooseSubjects ?? 0) : 0;
  const needsSubjectSelection = requiredSubjects > 0;
  const isPreChoiceComplete =
    !hasPreChoice ||
    (Boolean(selectedOption?.code) &&
      (!needsSubjectSelection || selectedSubjectCodes.length === requiredSubjects));

  const canStart =
    !hasOpenInProgress &&
    prepare.attemptsLeft > 0 &&
    !noServingOptions &&
    isPreChoiceComplete &&
    !starting;

  const formatGrantSource = (): string | null => {
    if (!prepare.grantSource || prepare.attemptsLeft <= 0) return null;
    if (prepare.grantSource.source === "COMPENSATION") {
      return t("grantSource.compensation");
    }
    const dateText = prepare.grantSource.grantedAt
      ? formatDayMonth(prepare.grantSource.grantedAt)
      : "";
    if (prepare.grantSource.source === "CLASS") {
      return dateText
        ? t("grantSource.classAssigned", { date: dateText })
        : t("grantSource.classAssignedNoDate");
    }
    const orderCode = prepare.grantSource.orderCode?.trim() ?? "";
    if (orderCode) {
      return t("grantSource.purchase", { date: dateText, orderCode });
    }
    return t("grantSource.purchaseNoOrder", { date: dateText });
  };

  const grantSourceLabel = formatGrantSource();

  return (
    <div className="flex flex-col gap-6">
      <Breadcrumb
        aria-label={t("breadcrumbLabel")}
        linkComponent={Link}
        items={[{ label: t("breadcrumbMyExams"), href: "/home" }, { label: t("pageTitle") }]}
      />

      <div className="flex flex-col gap-2.5">
        <div className="flex flex-wrap items-center gap-2">
          {examKindName && (
            <span className="inline-flex h-6 items-center rounded-full bg-primary-50 px-2.5 text-caption font-semibold text-primary-text">
              {examKindName}
            </span>
          )}
          <span className="inline-flex h-6 items-center rounded-full bg-subtle px-2.5 text-caption font-medium text-text-secondary">
            {scopeLabel}
          </span>
          {prepare.buildMode === REVIEW_BUILD_MODE && (
            <span className="inline-flex h-6 items-center rounded-full bg-info-bg px-2.5 text-caption font-semibold text-info">
              {t("reviewBadge")}
            </span>
          )}
        </div>
        <h1 data-exam-content className="text-h1 font-bold text-text">
          {prepare.examName}
        </h1>
        {summaryItems.length > 0 && (
          <p className="text-body text-text-muted tabular-nums">{summaryItems.join(" · ")}</p>
        )}
      </div>

      <div className="flex flex-wrap items-start gap-6">
        <div className="flex min-w-0 flex-[999_1_35rem] flex-col gap-5">
          {hasPreChoice && !hasOpenInProgress && (
            <section aria-labelledby="prechoice-title" className={CARD_CLASS}>
              <SectionHeading id="prechoice-title">{t("preChoice.title")}</SectionHeading>

              {noServingOptions ? (
                <div
                  role="alert"
                  className="flex items-start gap-2.5 rounded-xl border border-danger bg-danger-bg px-4 py-3.5 text-body text-danger"
                >
                  <TriangleAlert aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                  <div className="flex flex-col gap-1">
                    <span className="font-semibold">{t("noServingOption.title")}</span>
                    <span className="text-text-secondary">
                      {supportSummary
                        ? t("noServingOption.descriptionWithContact", { contact: supportSummary })
                        : t("noServingOption.contactPending")}
                    </span>
                  </div>
                </div>
              ) : (
                <>
                  <fieldset className="m-0 flex min-w-0 flex-col gap-2.5 border-0 p-0">
                    <legend className="mb-2 text-field-label font-medium text-text-secondary">
                      {t("preChoice.legend")}
                    </legend>
                    {preChoiceOptions.map((option) => {
                      const code = option.code ?? "";
                      const checked = selectedOptionCode === code;
                      const optionSubjects = option.subjects ?? [];
                      const optionMetrics = [
                        typeof option.questionCount === "number"
                          ? t("units.questions", { count: option.questionCount })
                          : null,
                        typeof option.durationMinutes === "number"
                          ? t("units.minutes", { count: option.durationMinutes })
                          : null,
                        typeof option.maxPoints === "number"
                          ? t("units.points", { value: option.maxPoints })
                          : null,
                      ].filter(Boolean);

                      return (
                        <label
                          key={code}
                          className={cn(
                            "flex min-h-12 cursor-pointer items-start gap-3 rounded-xl border px-3.5 py-2.5 transition-colors duration-150 motion-reduce:transition-none",
                            checked
                              ? "border-2 border-primary-600 bg-primary-50"
                              : "border-border bg-surface hover:border-border-strong",
                          )}
                        >
                          <input
                            type="radio"
                            name="preChoice"
                            value={code}
                            checked={checked}
                            onChange={() => onSelectOption(code)}
                            className={cn("mt-0.5 size-4.5 shrink-0 accent-primary-600", focusRing)}
                          />
                          <div className="flex min-w-0 flex-1 flex-col gap-1">
                            <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                              {code && <strong className="font-semibold text-text">{code}</strong>}
                              {option.name && (
                                <span data-exam-content className="text-text-secondary">
                                  {option.name}
                                </span>
                              )}
                              {option.kind === "SUBJECT_CHOICE" &&
                                (option.chooseSubjects ?? 0) > 0 &&
                                optionSubjects.length > 0 && (
                                  <span className="text-caption text-text-muted">
                                    (
                                    {t("preChoice.chooseSubjectsCount", {
                                      count: option.chooseSubjects ?? 0,
                                      total: optionSubjects.length,
                                    })}
                                    )
                                  </span>
                                )}
                            </div>
                            {optionSubjects.length > 0 && option.kind !== "SUBJECT_CHOICE" && (
                              <div className="flex flex-wrap gap-1.5 text-caption text-text-muted">
                                {optionSubjects
                                  .map((sub) => pickName(sub, locale) || sub.code || "")
                                  .filter(Boolean)
                                  .join(" · ")}
                              </div>
                            )}
                            {optionMetrics.length > 0 && (
                              <div className="text-caption text-text-secondary tabular-nums">
                                {optionMetrics.join(" · ")}
                              </div>
                            )}
                          </div>
                        </label>
                      );
                    })}
                  </fieldset>

                  {needsSubjectSelection && selectedOption && (
                    <fieldset className="m-0 flex min-w-0 flex-col gap-2.5 rounded-xl border border-border bg-page p-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <legend className="text-label font-semibold text-text">
                          {t("preChoice.subjectLegend")}
                        </legend>
                        <span className="text-caption font-semibold text-primary-text tabular-nums">
                          {t("preChoice.chooseSubjectsProgress", {
                            selected: selectedSubjectCodes.length,
                            total: requiredSubjects,
                          })}
                        </span>
                      </div>
                      <div className="grid grid-cols-[repeat(auto-fit,minmax(min(12rem,100%),1fr))] gap-2.5">
                        {(selectedOption.subjects ?? []).map((subject) => {
                          const subCode = subject.code ?? "";
                          const isChecked = selectedSubjectCodes.includes(subCode);
                          const isLimitReached =
                            !isChecked && selectedSubjectCodes.length >= requiredSubjects;
                          const label = pickName(subject, locale) || subCode;
                          const inputId = `prechoice-subject-${subCode}`;
                          return (
                            <label
                              key={subCode}
                              htmlFor={inputId}
                              className={cn(
                                "flex min-h-11 items-center gap-2.5 rounded-lg border px-3 py-2 text-body",
                                isChecked
                                  ? "border-primary-600 bg-primary-50 font-medium text-text"
                                  : "border-border bg-surface text-text-secondary",
                                isLimitReached ? "cursor-not-allowed opacity-60" : "cursor-pointer",
                              )}
                            >
                              <Checkbox
                                id={inputId}
                                checked={isChecked}
                                disabled={isLimitReached}
                                onCheckedChange={() => onToggleSubject(subCode, requiredSubjects)}
                              />
                              <span>{label}</span>
                            </label>
                          );
                        })}
                      </div>
                    </fieldset>
                  )}

                  {selectedOption && (
                    <dl className="m-0 flex flex-wrap gap-x-4 gap-y-1 rounded-xl bg-page px-3.5 py-2.5 text-body tabular-nums">
                      <div className="flex flex-wrap items-center gap-2">
                        <dt className="text-text-muted">
                          {t("preChoice.selectedPathSummary", {
                            code: selectedOption.code ?? "",
                          })}
                        </dt>
                        <dd className="m-0 font-semibold text-text">
                          {[
                            metrics.questionCount !== null
                              ? t("units.questions", { count: metrics.questionCount })
                              : null,
                            metrics.durationMinutes !== null
                              ? t("units.minutes", { count: metrics.durationMinutes })
                              : null,
                            metrics.maxPoints !== null
                              ? t("units.points", { value: metrics.maxPoints })
                              : null,
                          ]
                            .filter(Boolean)
                            .join(" · ")}
                        </dd>
                      </div>
                    </dl>
                  )}
                </>
              )}
            </section>
          )}

          <section aria-labelledby="structure-title" className={CARD_CLASS}>
            <SectionHeading id="structure-title">{t("structure.title")}</SectionHeading>

            <div className="flex items-start gap-2.5 rounded-xl bg-page px-3.5 py-3">
              <Clock aria-hidden className="mt-0.5 size-4.5 shrink-0 text-primary-text" />
              <div className="flex flex-col gap-0.5">
                <div className="font-semibold text-text">{t(`timing.${timingKey}.title`)}</div>
                <div className="text-body text-text-secondary">
                  {t(`timing.${timingKey}.description`)}
                </div>
              </div>
            </div>

            {orderedParts.length > 0 && (
              <ol className="m-0 flex list-none flex-col gap-2.5 p-0">
                {orderedParts.map((part, idx) => (
                  <li
                    key={`${part.partCode ?? idx}-${idx}`}
                    className="flex flex-wrap items-center gap-3 rounded-xl border border-border px-4 py-3.5"
                  >
                    <span
                      aria-hidden
                      className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary-50 font-bold text-primary-text"
                    >
                      {idx + 1}
                    </span>
                    <div className="min-w-0 flex-[1_1_16rem]">
                      <div data-exam-content className="font-semibold text-text">
                        {part.name}
                      </div>
                    </div>
                    <div className="flex flex-wrap items-center gap-2.5 text-body text-text-secondary tabular-nums">
                      {typeof part.questionCount === "number" && (
                        <span>{t("units.questions", { count: part.questionCount })}</span>
                      )}
                      {typeof part.durationMinutes === "number" && (
                        <span>{t("units.minutes", { count: part.durationMinutes })}</span>
                      )}
                      {typeof part.maxPoints === "number" && (
                        <span>{t("units.points", { value: part.maxPoints })}</span>
                      )}
                      <span
                        className={cn(
                          "inline-flex h-6 items-center rounded-full px-2.5 text-caption font-medium",
                          part.required
                            ? "bg-subtle text-text-secondary"
                            : "bg-warning-bg text-warning",
                        )}
                      >
                        {part.required ? t("required") : t("optional")}
                      </span>
                    </div>
                  </li>
                ))}
              </ol>
            )}

            {prepare.answerFormats.length > 0 && (
              <div className="flex flex-col gap-2">
                <div className="text-field-label font-medium text-text-muted">
                  {t("answerFormatsLabel")}
                </div>
                <ul className="m-0 flex list-none flex-wrap gap-2 p-0">
                  {prepare.answerFormats.map((fmt) => (
                    <li
                      key={fmt}
                      className="inline-flex h-7 items-center rounded-full bg-subtle px-3 text-caption font-medium text-text-secondary"
                    >
                      {KNOWN_ANSWER_FORMATS.has(fmt) ? t(`answerFormats.${fmt}`) : fmt}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {prepare.listeningPolicy && (
              <div className="flex items-start gap-2.5 rounded-xl border border-info bg-info-bg px-4 py-3 text-body">
                <Headphones aria-hidden className="mt-0.5 size-4.5 shrink-0 text-info" />
                <div className="flex flex-col gap-0.5">
                  <div className="font-semibold text-info">{t("listeningPolicy.title")}</div>
                  <div className="text-text-secondary">
                    {t("listeningPolicy.maxPlays", {
                      maxPlays: prepare.listeningPolicy.maxPlays ?? 2,
                    })}
                    {prepare.listeningPolicy.seekable === false && (
                      <> {t("listeningPolicy.noSeek")}</>
                    )}
                  </div>
                </div>
              </div>
            )}

            {prepare.omittedGroups.length > 0 && (
              <div className="flex flex-col gap-1.5 rounded-xl bg-page px-4 py-3 text-field-label text-text-muted">
                <div className="font-medium text-text-secondary">{t("omittedGroupsLabel")}</div>
                <ul className="m-0 flex list-disc flex-col gap-1 pl-5">
                  {prepare.omittedGroups.map((group, idx) => (
                    <li key={`${group.name ?? "omitted"}-${idx}`}>
                      <span data-exam-content>{group.name}</span>
                      {typeof group.points === "number" && (
                        <> · {t("units.points", { value: group.points })}</>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </section>

          {prepare.afterStartChoices.length > 0 && (
            <section aria-labelledby="after-choices-title" className={CARD_CLASS}>
              <SectionHeading id="after-choices-title">
                {t("afterStartChoices.title")}
              </SectionHeading>
              <div className="flex flex-col gap-3">
                {prepare.afterStartChoices.map((choice, idx) => (
                  <div
                    key={`${choice.partCode ?? idx}-${idx}`}
                    className="flex flex-col gap-2 rounded-xl border border-border p-4"
                  >
                    {choice.partName && (
                      <h3 data-exam-content className="text-title font-semibold text-text">
                        {choice.partName}
                      </h3>
                    )}
                    <ul className="m-0 flex list-none flex-col gap-2 p-0">
                      {(choice.options ?? []).map((opt, optIdx) => {
                        const optSubjects = opt.subjects ?? [];
                        const subjectLine = optSubjects
                          .map((sub) => pickName(sub, locale) || sub.code || "")
                          .filter(Boolean)
                          .join(", ");
                        return (
                          <li
                            key={`${opt.code ?? optIdx}-${optIdx}`}
                            className="flex flex-col gap-0.5 text-body text-text-secondary"
                          >
                            <div className="flex flex-wrap items-center gap-x-2">
                              {opt.code && (
                                <strong className="font-semibold text-text">{opt.code}</strong>
                              )}
                              {opt.name && (
                                <span data-exam-content className="font-medium text-text">
                                  {opt.name}
                                </span>
                              )}
                              {(opt.chooseSubjects ?? 0) > 0 && optSubjects.length > 0 && (
                                <span className="text-text-muted">
                                  (
                                  {t("afterStartChoices.chooseSubjects", {
                                    count: opt.chooseSubjects ?? 0,
                                    total: optSubjects.length,
                                  })}
                                  )
                                </span>
                              )}
                            </div>
                            {subjectLine && <div className="text-caption text-text-muted">{subjectLine}</div>}
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                ))}
              </div>
              <ul className="m-0 flex flex-col gap-1.5 pl-5 text-body text-text-secondary">
                <li>{t("afterStartChoices.deadlineNote")}</li>
                <li>{t("afterStartChoices.timerNote")}</li>
              </ul>
            </section>
          )}

          <section aria-labelledby="instructions-title" className={CARD_CLASS}>
            <SectionHeading id="instructions-title">{t("instructions.title")}</SectionHeading>
            {prepare.instructions && prepare.instructions.trim().length > 0 && (
              <div data-exam-content className="text-body text-text">
                <RichTextView html={prepare.instructions} allowImages={false} />
              </div>
            )}
            <ul className="m-0 flex flex-col gap-2 pl-5 text-body text-text-secondary">
              <li>{t("instructions.noteAutoSave")}</li>
              <li>{t("instructions.noteClock")}</li>
              <li>{t("instructions.noteOffline")}</li>
              <li>{t("instructions.noteSingleDevice")}</li>
            </ul>
          </section>
        </div>

        <aside
          aria-labelledby="sidebar-title"
          className="flex w-full min-w-0 flex-[1_1_18.75rem] flex-col gap-4 rounded-2xl border border-border bg-surface p-6 sm:max-w-[380px]"
        >
          <h2 id="sidebar-title" className="text-h3 font-bold text-text">
            {t("sidebar.title")}
          </h2>

          <dl className="m-0 flex flex-col gap-3">
            <div className="flex justify-between gap-3">
              <dt className="text-text-muted">{t("sidebar.attemptsLeft")}</dt>
              <dd className="m-0 font-bold text-text tabular-nums">
                {t("sidebar.attemptsValue", { count: prepare.attemptsLeft })}
              </dd>
            </div>
            {grantSourceLabel && (
              <div className="flex flex-col gap-0.5">
                <dt className="text-text-muted">{t("sidebar.grantSource")}</dt>
                <dd className="m-0 font-semibold text-text">{grantSourceLabel}</dd>
              </div>
            )}
            <div className="flex justify-between gap-3">
              <dt className="text-text-muted">{t("sidebar.questions")}</dt>
              <dd className="m-0 font-semibold text-text tabular-nums">
                {metrics.questionCount !== null
                  ? t("units.questions", { count: metrics.questionCount })
                  : "—"}
              </dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-text-muted">{t("sidebar.duration")}</dt>
              <dd className="m-0 font-semibold text-text tabular-nums">
                {metrics.durationMinutes !== null
                  ? t("units.minutes", { count: metrics.durationMinutes })
                  : "—"}
              </dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-text-muted">{t("sidebar.maxPoints")}</dt>
              <dd className="m-0 font-semibold text-text tabular-nums">
                {metrics.maxPoints !== null
                  ? t("units.points", { value: metrics.maxPoints })
                  : "—"}
              </dd>
            </div>
          </dl>

          {hasOpenInProgress && prepare.openAttempt?.attemptId ? (
            <>
              <div
                role="status"
                className="flex items-start gap-2.5 rounded-xl border border-info bg-info-bg px-3.5 py-3 text-body text-text"
              >
                <Info aria-hidden className="mt-0.5 size-4.5 shrink-0 text-info" />
                <span>{t("inProgressBanner")}</span>
              </div>
              <Button asChild variant="exam" size="lg" className="w-full">
                <Link
                  href={`/attempts/${encodeURIComponent(prepare.openAttempt.attemptId)}`}
                  prefetch={false}
                >
                  {t("continueAttempt")}
                </Link>
              </Button>
            </>
          ) : (
            <>
              {prepare.attemptsLeft <= 0 && (
                <div className="flex flex-col gap-3">
                  <div
                    role="alert"
                    className="flex items-start gap-2.5 rounded-xl border border-danger bg-danger-bg px-3.5 py-3 text-body"
                  >
                    <TriangleAlert aria-hidden className="mt-0.5 size-4.5 shrink-0 text-danger" />
                    <div className="flex flex-col gap-0.5">
                      <span className="font-semibold text-danger">{t("exhaustedTitle")}</span>
                      <span className="text-text-secondary">
                        {prepare.grantSource?.source === "CLASS"
                          ? t("exhaustedDescriptionClass")
                          : prepare.servingStatus === "PUBLISHED"
                            ? t("exhaustedDescription")
                            : t("exhaustedDescriptionDiscontinued")}
                      </span>
                    </div>
                  </div>
                  {prepare.servingStatus === "PUBLISHED" &&
                    prepare.grantSource?.source !== "CLASS" && (
                      <Button asChild variant="outline" className="h-11 w-full">
                        <Link href={`/checkout/${encodeURIComponent(prepare.examCode)}`}>
                          {t("buyMoreAttempts")}
                        </Link>
                      </Button>
                    )}
                </div>
              )}

              {startError && (
                <div
                  role="alert"
                  className="flex items-start gap-2.5 rounded-xl border border-danger bg-danger-bg px-3.5 py-3 text-body text-danger"
                >
                  <TriangleAlert aria-hidden className="mt-0.5 size-4.5 shrink-0" />
                  <div className="flex flex-col gap-1">
                    <span className="font-semibold">{t(`startErrors.${startError}`)}</span>
                    {startError === "NO_SERVING_OPTION" && (
                      <span className="text-text-secondary">
                        {supportSummary
                          ? t("noServingOption.descriptionWithContact", { contact: supportSummary })
                          : t("noServingOption.contactPending")}
                      </span>
                    )}
                  </div>
                </div>
              )}

              <Button
                type="button"
                variant="exam"
                size="xl"
                className="w-full"
                disabled={!canStart}
                loading={starting}
                onClick={() => {
                  const payload: StartAttemptRequest["preChoice"] | undefined =
                    selectedOption?.code
                      ? {
                          optionCode: selectedOption.code,
                          ...(needsSubjectSelection ? { subjectCodes: selectedSubjectCodes } : {}),
                        }
                      : undefined;
                  onClickStart(payload);
                }}
              >
                {t("startCta")}
                <ArrowRight aria-hidden />
              </Button>

              {prepare.attemptsLeft > 0 && !noServingOptions && !isPreChoiceComplete && (
                <p className="m-0 text-caption text-warning">
                  {selectedOption && needsSubjectSelection
                    ? t("preChoice.hintSelectSubjects", { count: requiredSubjects })
                    : t("preChoice.hintSelectOption")}
                </p>
              )}

              <p className="m-0 text-caption text-text-muted">{t("sidebar.startNote")}</p>
            </>
          )}

          <Link
            href="/home"
            className={cn(
              "inline-flex min-h-11 items-center gap-1.5 self-start rounded-md font-semibold text-primary-text hover:underline",
              focusRing,
            )}
          >
            <ArrowLeft aria-hidden className="size-4" />
            {t("backToMyExams")}
          </Link>
        </aside>
      </div>
    </div>
  );
}

export function PreparePageClient({ entitlementId }: { entitlementId: string }) {
  return <PrepareView entitlementId={entitlementId} />;
}
