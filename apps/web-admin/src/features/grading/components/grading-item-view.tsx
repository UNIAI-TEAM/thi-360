"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Button,
  Card,
  cn,
  ConfirmDialog,
  EmptyState,
  focusRing,
  LiveStatus,
  notify,
  Skeleton,
  StatusChip,
} from "@thi360/ui";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import { ArrowLeft, TriangleAlert, WifiOff } from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { FormProvider, useForm, type FieldErrors } from "react-hook-form";
import { usePermissions, useSession } from "@/features/session/session-provider";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import { getErrorStatus } from "@/lib/api/query-client";
import {
  defaultGradingApiDeps,
  gradingErrorCode,
  resolveGradingErrorMessage,
  type GradingApiDeps,
} from "../api";
import {
  findFinalizeIssues,
  gradingDraftSchema,
  isItemClaimable,
  isItemClaimedByOther,
  isItemFinalized,
  isItemOwnedDraft,
  toFinalizeRequest,
  toItemFormValues,
  toSaveDraftRequest,
  type GradingItemFormValues,
} from "../grading-form";
import {
  useClaimGradingItem,
  useFinalizeEssayGrade,
  useGradingExamKinds,
  useGradingItemDetail,
  useReopenEssayGrade,
  useSaveEssayDraft,
} from "../queries";
import {
  formatScoreInput,
  parseScore,
  SCORE_NUMBER_FORMAT,
  sumScores,
} from "../scoring";
import { GRADING_STATUS_TONE, shortAttemptCode } from "../status";
import type { GradingItemDetailResponse, GradingQueueState } from "../types";
import {
  ActiveDraftGradingForm,
  ClaimActionCard,
  EssayItemContentPanel,
  FinalizedGradingCard,
  GradingHistoryCard,
  ReadOnlyGradingCard,
} from "./grading-essay-panels";
import { resolveExamKindLabel } from "./grading-filters";

function decimalSeparatorOf(locale: string): string {
  return (
    new Intl.NumberFormat(locale).formatToParts(1.5).find((part) => part.type === "decimal")
      ?.value ?? ","
  );
}

export interface GradingItemViewProps {
  itemId: string;
  deps?: Partial<GradingApiDeps>;
}

/**
 * Anonymous single-item essay grading page `/grading/[itemId]` (LT §9.2, BS §6).
 * Supports claiming/takeover, rubric + total scoring, draft saving with optimistic locking (412),
 * finalizing with confirmation and sum validation, and reopening finalized grades with a reason.
 */
export function GradingItemView({ itemId, deps }: GradingItemViewProps) {
  const t = useTranslations("grading.detail");
  const tErrors = useTranslations("grading.errors");
  const api = useMemo(() => ({ ...defaultGradingApiDeps, ...deps }), [deps]);

  const detailQuery = useGradingItemDetail(itemId, api);
  const examKindsQuery = useGradingExamKinds(api);
  const [prevItemId, setPrevItemId] = useState(itemId);
  const [announcement, setAnnouncement] = useState("");
  const [justFinalized, setJustFinalized] = useState(false);

  if (prevItemId !== itemId) {
    setPrevItemId(itemId);
    setJustFinalized(false);
    setAnnouncement("");
  }

  const breadcrumbLabel = detailQuery.data
    ? t("heading", {
        code: shortAttemptCode(detailQuery.data.attemptId),
        number: detailQuery.data.displayNo ?? 0,
      })
    : undefined;
  useSetBreadcrumbLeaf(breadcrumbLabel);

  const isOffline = detailQuery.fetchStatus === "paused" && detailQuery.isPending;

  let body;
  if (isOffline) {
    body = (
      <div className="flex flex-col gap-5">
        <BackLink />
        <h1 className="text-page-title font-bold text-text">{t("title")}</h1>
        <Card>
          <EmptyState
            icon={<WifiOff />}
            title={t("offlineTitle")}
            description={t("offlineDescription")}
            headingLevel={2}
            action={
              <Button variant="secondary" onClick={() => void detailQuery.refetch()}>
                {t("retry")}
              </Button>
            }
          />
        </Card>
      </div>
    );
  } else if (detailQuery.isPending) {
    body = <GradingItemSkeleton label={t("loading")} />;
  } else if (detailQuery.isError) {
    const notFound = getErrorStatus(detailQuery.error) === 404;
    body = (
      <div className="flex flex-col gap-5">
        <BackLink />
        <h1 className="text-page-title font-bold text-text">{t("title")}</h1>
        <Card>
          <EmptyState
            icon={<TriangleAlert />}
            title={notFound ? t("notFoundTitle") : t("errorTitle")}
            description={
              notFound
                ? t("notFoundDescription")
                : resolveGradingErrorMessage(detailQuery.error, tErrors)
            }
            headingLevel={2}
            action={
              notFound ? (
                <Button asChild variant="secondary">
                  <Link href="/grading">{t("backToList")}</Link>
                </Button>
              ) : (
                <Button variant="secondary" onClick={() => void detailQuery.refetch()}>
                  {t("retry")}
                </Button>
              )
            }
          />
        </Card>
      </div>
    );
  } else {
    const detail = detailQuery.data;
    body = (
      <div className="flex flex-col gap-5">
        <ItemHeader detail={detail} examKinds={examKindsQuery.data} />
        <div className="grid items-start gap-4 @4xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
          <EssayItemContentPanel detail={detail} />
          <ItemGradingRightColumn
            detail={detail}
            api={api}
            justFinalized={justFinalized}
            announce={setAnnouncement}
            onFinalized={() => setJustFinalized(true)}
            onReopened={() => setJustFinalized(false)}
            onRefetchDetail={() => detailQuery.refetch()}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="@container">
      <LiveStatus message={announcement} />
      {body}
    </div>
  );
}

export function GradingItemSkeleton({ label }: { label?: string }) {
  return (
    <div className="flex flex-col gap-4" aria-busy="true" aria-label={label}>
      <Skeleton className="h-36" />
      <div className="grid gap-4 @4xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
        <Skeleton className="h-96" />
        <Skeleton className="h-96" />
      </div>
    </div>
  );
}

function BackLink() {
  const t = useTranslations("grading.detail");
  return (
    <Link
      href="/grading"
      className={cn(
        "inline-flex w-fit items-center gap-1.5 rounded-sm text-label font-medium text-text-secondary hover:text-text pointer-coarse:min-h-11",
        focusRing,
      )}
    >
      <ArrowLeft aria-hidden className="size-4" />
      {t("backToList")}
    </Link>
  );
}

function ItemHeader({
  detail,
  examKinds,
}: {
  detail: GradingItemDetailResponse;
  examKinds: ReturnType<typeof useGradingExamKinds>["data"];
}) {
  const t = useTranslations("grading");
  const format = useFormatter();
  const uiLocale = normalizeUiLocale(useLocale());

  const state: GradingQueueState = detail.state ?? "WAITING";
  const attemptCode = shortAttemptCode(detail.attemptId);
  const kindLabel = resolveExamKindLabel(detail.examKindCode, examKinds, uiLocale);

  return (
    <Card padding="none" className="flex flex-col gap-4 p-4 sm:p-5">
      <BackLink />
      <div className="flex min-w-0 flex-col gap-2">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <h1 className="text-page-title font-bold wrap-anywhere text-text">
            {t("detail.heading", { code: attemptCode, number: detail.displayNo ?? 0 })}
          </h1>
          <StatusChip tone={GRADING_STATUS_TONE[state]}>{t(`status.${state}`)}</StatusChip>
          {detail.regrading && (
            <StatusChip tone="warning">{t("detail.regrading")}</StatusChip>
          )}
        </div>
        {detail.examName && (
          <p className="text-body text-text-secondary">
            <span data-exam-content>{detail.examName}</span>
            {detail.partName && (
              <>
                {" · "}
                <span data-exam-content>{detail.partName}</span>
              </>
            )}
          </p>
        )}
      </div>

      <dl className="m-0 flex flex-wrap gap-x-6 gap-y-2 text-body">
        <SummaryItem label={t("detail.summary.attemptCode")} value={`#${attemptCode}`} mono />
        {kindLabel && <SummaryItem label={t("detail.summary.kind")} value={kindLabel} />}
        {detail.submittedAt && (
          <SummaryItem
            label={t("detail.summary.submittedAt")}
            value={format.dateTime(new Date(detail.submittedAt), "dateTime")}
          />
        )}
        <SummaryItem
          label={t("detail.summary.grader")}
          value={detail.activeGrade?.graderName ?? t("list.noGrader")}
        />
        {detail.activeGrade?.finalizedAt && (
          <SummaryItem
            label={t("detail.summary.gradedAt")}
            value={format.dateTime(new Date(detail.activeGrade.finalizedAt), "dateTime")}
          />
        )}
      </dl>
    </Card>
  );
}

function SummaryItem({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="flex items-baseline gap-1.5">
      <dt className="text-text-muted">{label}</dt>
      <dd className={cn("m-0 font-semibold text-text", mono && "font-mono")}>{value}</dd>
    </div>
  );
}

function ItemGradingRightColumn({
  detail,
  api,
  justFinalized,
  announce,
  onFinalized,
  onReopened,
  onRefetchDetail,
}: {
  detail: GradingItemDetailResponse;
  api: GradingApiDeps;
  justFinalized: boolean;
  announce: (message: string) => void;
  onFinalized: () => void;
  onReopened: () => void;
  onRefetchDetail: () => Promise<unknown>;
}) {
  const t = useTranslations("grading.detail");
  const tErrors = useTranslations("grading.errors");
  const { can } = usePermissions();
  const sessionState = useSession();

  const itemId = detail.itemId ?? "";
  const canModify = can("GRADING.MODIFY");
  const currentUserId = sessionState.status === "ready" ? sessionState.session.id : null;

  const claimMutation = useClaimGradingItem(itemId, api);
  const reopenMutation = useReopenEssayGrade(itemId, api);

  const [claimAlert, setClaimAlert] = useState<string | null>(null);
  const [reopenOpen, setReopenOpen] = useState(false);
  const [reopenError, setReopenError] = useState<string | null>(null);

  const finalized = isItemFinalized(detail);
  const ownedDraft = canModify && isItemOwnedDraft(detail, currentUserId);
  const claimedByOther = !finalized && isItemClaimedByOther(detail, currentUserId);
  const claimable = canModify && isItemClaimable(detail);
  const isTakeover =
    claimable &&
    Boolean(detail.activeGrade?.claimExpired) &&
    Boolean(detail.activeGrade?.graderId) &&
    detail.activeGrade?.graderId !== currentUserId;

  async function handleClaim() {
    setClaimAlert(null);
    try {
      await claimMutation.mutateAsync();
      const msg = t("claim.done");
      announce(msg);
      notify.success(msg);
    } catch (error) {
      const msg = resolveGradingErrorMessage(error, tErrors);
      setClaimAlert(msg);
      announce(msg);
      if (gradingErrorCode(error) === "GRADING_CLAIMED" || getErrorStatus(error) === 409) {
        void onRefetchDetail();
      }
    }
  }

  async function handleReopen(reason: string) {
    setReopenError(null);
    try {
      await reopenMutation.mutateAsync({ reason });
      onReopened();
      setReopenOpen(false);
      const msg = t("reopen.done");
      announce(msg);
      notify.success(msg);
    } catch (error) {
      setReopenError(resolveGradingErrorMessage(error, tErrors));
    }
  }

  let primaryCard;
  if (finalized) {
    primaryCard = (
      <FinalizedGradingCard
        detail={detail}
        canModify={canModify}
        focusOnMount={justFinalized}
        onOpenReopen={() => {
          setReopenError(null);
          setReopenOpen(true);
        }}
      />
    );
  } else if (!canModify) {
    primaryCard = <ReadOnlyGradingCard detail={detail} reason="noPermission" />;
  } else if (ownedDraft) {
    primaryCard = (
      <OwnedDraftFormWorkspace
        key={`${itemId}-${detail.activeGrade?.id ?? "none"}`}
        detail={detail}
        api={api}
        announce={announce}
        onFinalized={onFinalized}
        onRefetchDetail={onRefetchDetail}
      />
    );
  } else if (claimedByOther) {
    primaryCard = (
      <ReadOnlyGradingCard detail={detail} reason="claimedByOther" alertMessage={claimAlert} />
    );
  } else {
    primaryCard = (
      <ClaimActionCard
        detail={detail}
        isTakeover={isTakeover}
        claiming={claimMutation.isPending}
        alertMessage={claimAlert}
        onClaim={() => void handleClaim()}
      />
    );
  }

  return (
    <div className="flex min-w-0 flex-col gap-4">
      {primaryCard}
      <GradingHistoryCard
        history={detail.history ?? []}
        pointsMax={detail.pointsMax ?? 0}
      />
      <ConfirmDialog
        open={reopenOpen}
        onOpenChange={(open) => {
          setReopenOpen(open);
          if (!open) setReopenError(null);
        }}
        title={t("reopen.title")}
        description={t("reopen.description")}
        reasonLabel={t("reopen.reasonLabel")}
        requireReason
        confirmLabel={t("reopen.confirm")}
        cancelLabel={t("reopen.cancel")}
        tone="danger"
        loading={reopenMutation.isPending}
        error={reopenError}
        onConfirm={(reason) => void handleReopen(reason)}
      />
    </div>
  );
}

function OwnedDraftFormWorkspace({
  detail,
  api,
  announce,
  onFinalized,
  onRefetchDetail,
}: {
  detail: GradingItemDetailResponse;
  api: GradingApiDeps;
  announce: (message: string) => void;
  onFinalized: () => void;
  onRefetchDetail: () => Promise<unknown>;
}) {
  const t = useTranslations("grading.detail");
  const tErrors = useTranslations("grading.errors");
  const locale = useLocale();
  const format = useFormatter();

  const itemId = detail.itemId ?? "";
  const decimalSeparator = useMemo(() => decimalSeparatorOf(locale), [locale]);
  const schema = useMemo(() => gradingDraftSchema(detail), [detail]);

  const form = useForm<GradingItemFormValues>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: toItemFormValues(detail, decimalSeparator),
    shouldFocusError: false,
  });

  const { handleSubmit, setError, clearErrors, setFocus, setValue, getValues, reset, formState } = form;
  const saveDraftMutation = useSaveEssayDraft(itemId, api);
  const finalizeMutation = useFinalizeEssayGrade(itemId, api);

  const [confirming, setConfirming] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [isVersionConflict, setIsVersionConflict] = useState(false);
  const [syncedVersion, setSyncedVersion] = useState<number | undefined>(
    detail.activeGrade?.version,
  );

  const confirmRef = useRef<HTMLDivElement>(null);
  const finalizeButtonRef = useRef<HTMLButtonElement>(null);
  const syncedVersionRef = useRef<number | undefined>(detail.activeGrade?.version);

  const incomingVersion = detail.activeGrade?.version;
  if ((!formState.isDirty || isVersionConflict) && incomingVersion !== syncedVersion) {
    setSyncedVersion(incomingVersion);
  }
  const staleServerNotice =
    incomingVersion !== undefined &&
    incomingVersion !== syncedVersion &&
    formState.isDirty &&
    !isVersionConflict;

  // Sync form when detail reloads with a newer version while clean or resolving a 412 conflict.
  useEffect(() => {
    const serverVersion = detail.activeGrade?.version;
    if (
      serverVersion !== undefined &&
      serverVersion !== syncedVersionRef.current &&
      (!formState.isDirty || isVersionConflict)
    ) {
      syncedVersionRef.current = serverVersion;
      reset(toItemFormValues(detail, decimalSeparator));
    }
  }, [detail, decimalSeparator, formState.isDirty, isVersionConflict, reset]);

  useEffect(() => {
    if (confirming) confirmRef.current?.focus();
  }, [confirming]);

  useEffect(() => {
    if (!formState.isDirty) return;
    const handler = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [formState.isDirty]);

  const handleCriterionChange = useCallback(
    (changedIndex: number, nextValue: string) => {
      const criteria = [...(getValues("criteria") ?? [])];
      criteria[changedIndex] = nextValue;
      const numeric = criteria.map(parseScore).filter((val) => Number.isFinite(val));
      const nextTotal =
        numeric.length === 0 ? "" : formatScoreInput(sumScores(numeric), decimalSeparator);
      clearErrors("totalPoints");
      setValue("totalPoints", nextTotal, { shouldDirty: true, shouldValidate: false });
    },
    [clearErrors, decimalSeparator, getValues, setValue],
  );

  const focusFirstFormError = useCallback(
    (errors: FieldErrors<GradingItemFormValues>) => {
      const rubricLen = detail.rubric?.length ?? 0;
      for (let i = 0; i < rubricLen; i += 1) {
        if (errors.criteria?.[i]) {
          setFocus(`criteria.${i}`);
          return;
        }
      }
      if (errors.totalPoints) {
        setFocus("totalPoints");
        return;
      }
      if (errors.comment) {
        setFocus("comment");
      }
    },
    [detail.rubric, setFocus],
  );

  const handleMutationError = useCallback(
    (error: unknown) => {
      const code = gradingErrorCode(error);
      const status = getErrorStatus(error);
      const message = resolveGradingErrorMessage(error, tErrors);
      setServerError(message);
      setConfirming(false);
      announce(message);

      if (code === "VERSION_CONFLICT" || status === 412) {
        setIsVersionConflict(true);
        void onRefetchDetail();
        return;
      }
      setIsVersionConflict(false);
      if (code === "GRADING_CLAIMED" || code === "ESSAY_ALREADY_FINALIZED" || status === 409) {
        void onRefetchDetail();
      }
    },
    [announce, onRefetchDetail, tErrors],
  );

  const onSaveDraft = () =>
    handleSubmit(
      async (values) => {
        setServerError(null);
        setIsVersionConflict(false);
        setConfirming(false);
        try {
          const updated = await saveDraftMutation.mutateAsync(toSaveDraftRequest(detail, values));
          syncedVersionRef.current = updated.version;
          setSyncedVersion(updated.version);
          reset(toItemFormValues({ ...detail, activeGrade: updated }, decimalSeparator));
          const msg = t("draft.saved");
          announce(msg);
          notify.success(msg);
        } catch (error) {
          handleMutationError(error);
        }
      },
      (errors) => {
        setConfirming(false);
        focusFirstFormError(errors);
        announce(t("validation.summary"));
      },
    )();

  const onRequestFinalize = () =>
    handleSubmit(
      (values) => {
        setServerError(null);
        setIsVersionConflict(false);
        const issues = findFinalizeIssues(detail, values);
        if (issues.length > 0) {
          for (const issue of issues) {
            setError(issue.field, { type: "manual", message: issue.code });
          }
          setConfirming(false);
          setFocus(issues[0]!.field);
          announce(t("validation.summary"));
          return;
        }
        setConfirming(true);
      },
      (errors) => {
        setConfirming(false);
        focusFirstFormError(errors);
        announce(t("validation.summary"));
      },
    )();

  const onConfirmFinalize = () =>
    handleSubmit(
      async (values) => {
        setServerError(null);
        setIsVersionConflict(false);
        const issues = findFinalizeIssues(detail, values);
        if (issues.length > 0) {
          for (const issue of issues) {
            setError(issue.field, { type: "manual", message: issue.code });
          }
          setConfirming(false);
          setFocus(issues[0]!.field);
          return;
        }
        try {
          const res = await finalizeMutation.mutateAsync(toFinalizeRequest(detail, values));
          const nextVersion = res.grade?.version ?? syncedVersionRef.current;
          syncedVersionRef.current = nextVersion;
          setSyncedVersion(nextVersion);
          onFinalized();
          const finalScore = res.grade?.points ?? parseScore(values.totalPoints);
          const msg = t("finish.done", {
            score: format.number(Number.isFinite(finalScore) ? finalScore : 0, SCORE_NUMBER_FORMAT),
            max: format.number(detail.pointsMax ?? 0, SCORE_NUMBER_FORMAT),
          });
          announce(msg);
          notify.success(msg);
        } catch (error) {
          handleMutationError(error);
        }
      },
      (errors) => {
        setConfirming(false);
        focusFirstFormError(errors);
      },
    )();

  const onCancelConfirm = () => {
    setConfirming(false);
    requestAnimationFrame(() => finalizeButtonRef.current?.focus());
  };

  const onReloadDetail = async () => {
    setServerError(null);
    setIsVersionConflict(false);
    const refetched = (await onRefetchDetail()) as { data?: GradingItemDetailResponse } | undefined;
    const nextDetail = refetched?.data ?? detail;
    const nextVersion = nextDetail.activeGrade?.version;
    syncedVersionRef.current = nextVersion;
    setSyncedVersion(nextVersion);
    reset(toItemFormValues(nextDetail, decimalSeparator));
  };

  return (
    <FormProvider {...form}>
      <form noValidate onSubmit={(event) => event.preventDefault()}>
        <ActiveDraftGradingForm
          detail={detail}
          decimalSeparator={decimalSeparator}
          savingDraft={saveDraftMutation.isPending}
          finalizing={finalizeMutation.isPending}
          confirming={confirming}
          confirmRef={confirmRef}
          finalizeButtonRef={finalizeButtonRef}
          serverError={serverError}
          isVersionConflict={isVersionConflict}
          staleServerNotice={staleServerNotice}
          onCriterionChange={handleCriterionChange}
          onSaveDraft={() => void onSaveDraft()}
          onRequestFinalize={() => void onRequestFinalize()}
          onConfirmFinalize={() => void onConfirmFinalize()}
          onCancelConfirm={onCancelConfirm}
          onReloadDetail={() => void onReloadDetail()}
        />
      </form>
    </FormProvider>
  );
}
