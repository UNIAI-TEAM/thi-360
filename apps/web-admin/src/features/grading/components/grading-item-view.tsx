"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Button,
  Card,
  cn,
  ConfirmDialog,
  DetailHeader,
  EmptyState,
  LiveStatus,
  notify,
  Skeleton,
  StatusChip,
} from "@thi360/ui";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import { TriangleAlert, WifiOff } from "lucide-react";
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
        <StateHeader title={t("title")} />
        <Card>
          <EmptyState
            icon={<WifiOff />}
            title={t("offlineTitle")}
            description={t("offlineDescription")}
            headingLevel={2}
            action={
              <Button
                variant="secondary"
                onClick={() => {
                  void detailQuery.refetch();
                  void examKindsQuery.refetch();
                }}
              >
                {t("retry")}
              </Button>
            }
          />
        </Card>
      </div>
    );
  } else if (detailQuery.isPending) {
    body = <GradingItemSkeleton label={t("loading")} />;
  } else if (!detailQuery.data) {
    const notFound = detailQuery.isError && getErrorStatus(detailQuery.error) === 404;
    body = (
      <div className="flex flex-col gap-5">
        <StateHeader title={t("title")} />
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
                <Button
                  variant="secondary"
                  onClick={() => {
                    void detailQuery.refetch();
                    void examKindsQuery.refetch();
                  }}
                >
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
            key={detail.itemId ?? itemId}
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
    <div className="@container">
      <div className="flex flex-col gap-4" aria-busy="true" aria-label={label}>
        <Skeleton className="h-36" />
        <div className="grid gap-4 @4xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
          <Skeleton className="h-96" />
          <Skeleton className="h-96" />
        </div>
      </div>
    </div>
  );
}

/** Đầu trang của trạng thái mất mạng / lỗi: lối quay lại và tiêu đề chung, chưa có dữ liệu câu. */
function StateHeader({ title }: { title: string }) {
  const tCommon = useTranslations("common");
  return (
    <DetailHeader back={{ href: "/grading", label: tCommon("backTo.grading") }} linkComponent={Link} title={title} />
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
  const tCommon = useTranslations("common");
  const format = useFormatter();
  const uiLocale = normalizeUiLocale(useLocale());

  const state: GradingQueueState = detail.state ?? "WAITING";
  const attemptCode = shortAttemptCode(detail.attemptId);
  const kindLabel = resolveExamKindLabel(detail.examKindCode, examKinds, uiLocale);

  return (
    <DetailHeader
      back={{ href: "/grading", label: tCommon("backTo.grading") }}
      linkComponent={Link}
      title={t("detail.heading", { code: attemptCode, number: detail.displayNo ?? 0 })}
      chips={
        <>
          <StatusChip tone={GRADING_STATUS_TONE[state]}>{t(`status.${state}`)}</StatusChip>
          {detail.regrading && <StatusChip tone="warning">{t("detail.regrading")}</StatusChip>}
        </>
      }
      description={
        detail.examName && (
          <>
            <span data-exam-content>{detail.examName}</span>
            {detail.partName && (
              <>
                {" · "}
                <span data-exam-content>{detail.partName}</span>
              </>
            )}
          </>
        )
      }
    >
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
    </DetailHeader>
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

function rightColumnViewKey(
  detail: GradingItemDetailResponse,
  canModify: boolean,
  currentUserId: string | null,
): string {
  if (isItemFinalized(detail)) return "finalized";
  if (!canModify) return "noPermission";
  if (isItemOwnedDraft(detail, currentUserId)) return `owned:${detail.activeGrade?.id ?? "none"}`;
  if (isItemClaimedByOther(detail, currentUserId)) return "claimedByOther";
  return "claimable";
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
  const [reloadFocusKey, setReloadFocusKey] = useState<string | null>(null);

  const finalized = isItemFinalized(detail);
  const ownedDraft = canModify && isItemOwnedDraft(detail, currentUserId);
  const claimedByOther = !finalized && isItemClaimedByOther(detail, currentUserId);
  const claimable = canModify && isItemClaimable(detail);
  const isTakeover =
    claimable &&
    Boolean(detail.activeGrade?.claimExpired) &&
    Boolean(detail.activeGrade?.graderId) &&
    detail.activeGrade?.graderId !== currentUserId;

  const rightViewKey = rightColumnViewKey(detail, canModify, currentUserId);
  const rightMountedRef = useRef(true);
  const prevRightViewKeyRef = useRef(rightViewKey);

  useEffect(() => {
    rightMountedRef.current = true;
    return () => {
      rightMountedRef.current = false;
    };
  }, []);

  useEffect(() => {
    const prevKey = prevRightViewKeyRef.current;
    prevRightViewKeyRef.current = rightViewKey;
    if (reloadFocusKey !== null && prevKey === reloadFocusKey && rightViewKey !== reloadFocusKey) {
      setReloadFocusKey(null);
    }
  }, [reloadFocusKey, rightViewKey]);

  async function handleReloadFromOwnedDraft() {
    const originKey = rightViewKey;
    const res = (await onRefetchDetail()) as
      | {
          data?: GradingItemDetailResponse;
          isError?: boolean;
          error?: unknown;
          fetchStatus?: string;
        }
      | undefined;
    if (
      rightMountedRef.current &&
      res &&
      !res.isError &&
      res.data &&
      res.fetchStatus !== "paused"
    ) {
      const nextKey = rightColumnViewKey(res.data, canModify, currentUserId);
      if (nextKey !== originKey) {
        setReloadFocusKey(nextKey);
      }
    }
    return res;
  }

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
        focusOnMount={justFinalized || reloadFocusKey === "finalized"}
        onOpenReopen={() => {
          setReopenError(null);
          setReopenOpen(true);
        }}
      />
    );
  } else if (!canModify) {
    primaryCard = (
      <ReadOnlyGradingCard
        detail={detail}
        reason="noPermission"
        focusOnMount={reloadFocusKey === "noPermission"}
      />
    );
  } else if (ownedDraft) {
    primaryCard = (
      <OwnedDraftFormWorkspace
        key={`${itemId}-${detail.activeGrade?.id ?? "none"}`}
        detail={detail}
        api={api}
        focusOnMount={reloadFocusKey === rightViewKey}
        announce={announce}
        onFinalized={onFinalized}
        onRefetchDetail={onRefetchDetail}
        onReloadRefetch={handleReloadFromOwnedDraft}
      />
    );
  } else if (claimedByOther) {
    primaryCard = (
      <ReadOnlyGradingCard
        detail={detail}
        reason="claimedByOther"
        alertMessage={claimAlert}
        focusOnMount={reloadFocusKey === "claimedByOther"}
      />
    );
  } else {
    primaryCard = (
      <ClaimActionCard
        detail={detail}
        isTakeover={isTakeover}
        claiming={claimMutation.isPending}
        alertMessage={claimAlert}
        focusOnMount={reloadFocusKey === "claimable"}
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

function withSyncedVersion(
  detail: GradingItemDetailResponse,
  version: number | undefined,
): GradingItemDetailResponse {
  if (!detail.activeGrade || version === undefined) return detail;
  return {
    ...detail,
    activeGrade: {
      ...detail.activeGrade,
      version,
    },
  };
}

function OwnedDraftFormWorkspace({
  detail,
  api,
  focusOnMount = false,
  announce,
  onFinalized,
  onRefetchDetail,
  onReloadRefetch,
}: {
  detail: GradingItemDetailResponse;
  api: GradingApiDeps;
  focusOnMount?: boolean;
  announce: (message: string) => void;
  onFinalized: () => void;
  onRefetchDetail: () => Promise<unknown>;
  onReloadRefetch: () => Promise<unknown>;
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
  const [reloading, setReloading] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const [isVersionConflict, setIsVersionConflict] = useState(false);
  const [syncedVersion, setSyncedVersion] = useState<number | undefined>(
    detail.activeGrade?.version,
  );

  const confirmRef = useRef<HTMLDivElement>(null);
  const finalizeButtonRef = useRef<HTMLButtonElement>(null);
  const syncedVersionRef = useRef<number | undefined>(detail.activeGrade?.version);
  const mountedRef = useRef(true);
  const rafRef = useRef<number | null>(null);
  const prevStaleNoticeRef = useRef(false);

  const scheduleFocus = useCallback((fn: () => void) => {
    if (rafRef.current !== null) {
      cancelAnimationFrame(rafRef.current);
    }
    rafRef.current = requestAnimationFrame(() => {
      rafRef.current = null;
      if (mountedRef.current) {
        fn();
      }
    });
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (!focusOnMount) return;
    const targetField =
      (detail.rubric?.length ?? 0) > 0 ? ("criteria.0" as const) : ("totalPoints" as const);
    scheduleFocus(() => setFocus(targetField));
  }, [focusOnMount, detail.rubric, scheduleFocus, setFocus]);

  const incomingVersion = detail.activeGrade?.version;
  if (!formState.isDirty && incomingVersion !== syncedVersion) {
    setSyncedVersion(incomingVersion);
  }
  const staleServerNotice =
    incomingVersion !== undefined &&
    syncedVersion !== undefined &&
    incomingVersion !== syncedVersion &&
    formState.isDirty;

  useEffect(() => {
    if (staleServerNotice && !prevStaleNoticeRef.current) {
      announce(t("staleNotice"));
    }
    prevStaleNoticeRef.current = staleServerNotice;
  }, [announce, staleServerNotice, t]);

  // Sync form when detail reloads with a newer version while clean.
  useEffect(() => {
    const serverVersion = detail.activeGrade?.version;
    if (
      serverVersion !== undefined &&
      serverVersion !== syncedVersionRef.current &&
      !formState.isDirty
    ) {
      syncedVersionRef.current = serverVersion;
      reset(toItemFormValues(detail, decimalSeparator));
    }
  }, [detail, decimalSeparator, formState.isDirty, reset]);

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
        void Promise.resolve(onRefetchDetail()).then((res) => {
          const refetched = res as
            | {
                data?: GradingItemDetailResponse;
                isError?: boolean;
                fetchStatus?: string;
              }
            | undefined;
          if (
            !mountedRef.current ||
            !refetched ||
            refetched.isError ||
            !refetched.data ||
            refetched.fetchStatus === "paused"
          ) {
            return;
          }
          const nextDetail = refetched.data;
          const nextVersion = nextDetail.activeGrade?.version;
          syncedVersionRef.current = nextVersion;
          setSyncedVersion(nextVersion);
          reset(toItemFormValues(nextDetail, decimalSeparator));
        });
        return;
      }
      setIsVersionConflict(false);
      if (code === "GRADING_CLAIMED" || code === "ESSAY_ALREADY_FINALIZED" || status === 409) {
        void onRefetchDetail();
      }
    },
    [announce, decimalSeparator, onRefetchDetail, reset, tErrors],
  );

  const onSaveDraft = () =>
    handleSubmit(
      async (values) => {
        if (reloading) return;
        setServerError(null);
        setIsVersionConflict(false);
        setConfirming(false);
        try {
          const versionedDetail = withSyncedVersion(detail, syncedVersionRef.current);
          const updated = await saveDraftMutation.mutateAsync(
            toSaveDraftRequest(versionedDetail, values),
          );
          if (!mountedRef.current) return;
          syncedVersionRef.current = updated.version;
          setSyncedVersion(updated.version);
          reset(toItemFormValues({ ...detail, activeGrade: updated }, decimalSeparator));
          const msg = t("draft.saved");
          announce(msg);
          notify.success(msg);
        } catch (error) {
          if (mountedRef.current) {
            handleMutationError(error);
          }
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
        if (reloading) return;
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
        if (reloading) return;
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
          const versionedDetail = withSyncedVersion(detail, syncedVersionRef.current);
          const res = await finalizeMutation.mutateAsync(
            toFinalizeRequest(versionedDetail, values),
          );
          if (!mountedRef.current) return;
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
          if (mountedRef.current) {
            handleMutationError(error);
          }
        }
      },
      (errors) => {
        setConfirming(false);
        focusFirstFormError(errors);
      },
    )();

  const onCancelConfirm = () => {
    setConfirming(false);
    scheduleFocus(() => finalizeButtonRef.current?.focus());
  };

  const onReloadDetail = async () => {
    if (reloading || saveDraftMutation.isPending || finalizeMutation.isPending) return;
    setReloading(true);
    try {
      const refetched = (await onReloadRefetch()) as
        | {
            data?: GradingItemDetailResponse;
            isError?: boolean;
            error?: unknown;
            fetchStatus?: string;
          }
        | undefined;
      if (!mountedRef.current) return;
      if (!refetched || refetched.isError || !refetched.data || refetched.fetchStatus === "paused") {
        const msg =
          refetched?.fetchStatus === "paused"
            ? tErrors("NETWORK")
            : resolveGradingErrorMessage(refetched?.error, tErrors);
        setServerError(msg);
        announce(msg);
        return;
      }
      const nextDetail = refetched.data;
      const nextVersion = nextDetail.activeGrade?.version;
      syncedVersionRef.current = nextVersion;
      setSyncedVersion(nextVersion);
      setServerError(null);
      setIsVersionConflict(false);
      setConfirming(false);
      reset(toItemFormValues(nextDetail, decimalSeparator));
      announce("");
      const targetField =
        (nextDetail.rubric?.length ?? 0) > 0 ? ("criteria.0" as const) : ("totalPoints" as const);
      scheduleFocus(() => setFocus(targetField));
    } finally {
      if (mountedRef.current) {
        setReloading(false);
      }
    }
  };

  return (
    <FormProvider {...form}>
      <form noValidate onSubmit={(event) => event.preventDefault()}>
        <ActiveDraftGradingForm
          detail={detail}
          decimalSeparator={decimalSeparator}
          savingDraft={saveDraftMutation.isPending}
          finalizing={finalizeMutation.isPending}
          reloading={reloading}
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
