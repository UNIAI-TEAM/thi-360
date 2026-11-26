"use client";

import {
  Button,
  Card,
  Chip,
  ConfirmDialog,
  DetailHeader,
  EmptyState,
  LiveStatus,
  Skeleton,
  notify,
} from "@thi360/ui";
import {
  AlertTriangle,
  Lock,
  PauseCircle,
  Pencil,
  PlayCircle,
  Trash2,
  WifiOff,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useId, useMemo, useRef, useState } from "react";

import { usePermissions } from "@/features/session/session-provider";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import { ApiStatusError } from "@/lib/api/query-client";
import { pickName } from "@thi360/web-kit/i18n";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";

import { formatPromotionDateTime } from "../datetime";
import { promotionErrorCode, resolvePromotionErrorMessage } from "../errors";
import { formatDiscountSummary, formatVnd } from "../format";
import {
  useClassPickerQuery,
  useDeletePromotionMutation,
  usePromotionDetailQuery,
  useResumePromotionMutation,
  useStopPromotionMutation,
} from "../queries";
import type { PromotionDetail, RedemptionStateFilter } from "../types";
import { PromotionCapWarningCard } from "./promotion-cap-warning-card";
import { PromotionHistoryCard } from "./promotion-history-card";
import { PromotionPricePreviewCard } from "./promotion-price-preview";
import { PromotionRedemptionsCard } from "./promotion-redemptions-card";
import { PromotionStatusChip } from "./promotion-status-chip";

type ActiveDetailDialog = "stop" | "resume" | "delete" | null;

export function PromotionDetailView({ promotionId }: { promotionId: string }) {
  const tErrors = useTranslations("promotions.errors");
  const t = useTranslations("promotions");
  const tCommon = useTranslations("common");
  const locale = useLocale();
  const uiLocale = normalizeUiLocale(locale);
  const router = useRouter();
  const { can } = usePermissions();
  const canModify = can("PROMOTION.MODIFY");
  const canViewClasses = can("CLASS.VIEW");

  const [redemptionPage, setRedemptionPage] = useState(1);
  const [redemptionState, setRedemptionState] = useState<RedemptionStateFilter>("ALL");
  const [searchInput, setSearchInput] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");

  const [dialog, setDialog] = useState<ActiveDetailDialog>(null);
  const [dialogError, setDialogError] = useState<unknown>(null);
  const [conflictAlert, setConflictAlert] = useState<unknown>(null);
  const [liveMessage, setLiveMessage] = useState("");
  const [retainedDetail, setRetainedDetail] = useState<{
    id: string;
    data: PromotionDetail;
  } | null>(null);
  const searchTimerRef = useRef<number | null>(null);
  const deleteReasonId = useId();

  useEffect(() => {
    return () => {
      if (searchTimerRef.current !== null) {
        window.clearTimeout(searchTimerRef.current);
      }
    };
  }, []);

  const handleSearchInputChange = (nextQ: string) => {
    if (searchTimerRef.current !== null) {
      window.clearTimeout(searchTimerRef.current);
      searchTimerRef.current = null;
    }
    setSearchInput(nextQ);
    if (nextQ === "") {
      setDebouncedQ("");
      setRedemptionPage(1);
      return;
    }
    searchTimerRef.current = window.setTimeout(() => {
      setDebouncedQ(nextQ);
      setRedemptionPage(1);
      searchTimerRef.current = null;
    }, 300);
  };

  const detailQuery = usePromotionDetailQuery(promotionId, {
    page: redemptionPage - 1,
    size: 20,
    redemptionState,
    redemptionQ: debouncedQ,
  });

  if (detailQuery.data && detailQuery.data.id === promotionId) {
    if (retainedDetail?.id !== promotionId || retainedDetail.data !== detailQuery.data) {
      setRetainedDetail({ id: promotionId, data: detailQuery.data });
    }
  } else if (retainedDetail && retainedDetail.id !== promotionId) {
    setRetainedDetail(null);
  }

  const detail =
    detailQuery.data ??
    (retainedDetail?.id === promotionId ? retainedDetail.data : undefined);
  useSetBreadcrumbLeaf(detail ? detail.name : t("detail.title"));

  const hasNullClassMetadata = Boolean(
    detail?.audienceClasses.some((cls) => !cls.name && !cls.code),
  );
  const classesLookupQuery = useClassPickerQuery(
    "",
    Boolean(detail && hasNullClassMetadata && canViewClasses),
    100,
  );

  const classLookupMap = useMemo(() => {
    const map: Record<string, { code?: string; name?: string }> = {};
    for (const cls of classesLookupQuery.data ?? []) {
      map[cls.id] = cls;
    }
    return map;
  }, [classesLookupQuery.data]);

  const stopMutation = useStopPromotionMutation();
  const resumeMutation = useResumePromotionMutation();
  const deleteMutation = useDeletePromotionMutation();
  const mutating = stopMutation.isPending || resumeMutation.isPending || deleteMutation.isPending;

  if (detailQuery.isLoading && !detail) {
    return (
      <div className="flex flex-col gap-5" aria-busy="true">
        <Skeleton className="h-10 w-72" />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  const errCode = detailQuery.error ? promotionErrorCode(detailQuery.error) : undefined;
  const notFound =
    errCode === "PROMOTION_NOT_FOUND" ||
    errCode === "NOT_FOUND" ||
    (detailQuery.error instanceof ApiStatusError && detailQuery.error.status === 404);
  const isInitialOffline = !detail && detailQuery.fetchStatus === "paused" && !detailQuery.isError;

  if (!detail || notFound) {
    return (
      <EmptyState
        headingLevel={1}
        icon={isInitialOffline ? <WifiOff /> : undefined}
        title={
          isInitialOffline
            ? t("list.offlineTitle")
            : notFound
              ? t("detail.notFoundTitle")
              : t("detail.errorTitle")
        }
        description={
          isInitialOffline
            ? t("list.offlineDescription")
            : notFound
              ? t("detail.notFoundDescription")
              : resolvePromotionErrorMessage(detailQuery.error, tErrors)
        }
        action={
          notFound ? (
            <Button variant="secondary" asChild>
              <Link href="/promotions">{t("actions.backToList")}</Link>
            </Button>
          ) : (
            <Button variant="secondary" onClick={() => void detailQuery.refetch()}>
              {t("actions.retry")}
            </Button>
          )
        }
      />
    );
  }

  const isRedemptionsOffline =
    detailQuery.fetchStatus === "paused" && detailQuery.isPlaceholderData;
  const redemptionsError = detailQuery.isError ? detailQuery.error : null;

  const label = detail.code ?? detail.name;
  const canToggleStatus = detail.state !== "ENDED";
  const isStopped = detail.status === "STOPPED";
  const canDelete = detail.usedCount === 0;

  const openDialog = (next: Exclude<ActiveDetailDialog, null>) => {
    setDialogError(null);
    setConflictAlert(null);
    setDialog(next);
  };

  const handleConfirmDialog = () => {
    setDialogError(null);
    if (dialog === "stop") {
      stopMutation.mutate(
        { id: detail.id, expectedRevision: detail.revision },
        {
          onSuccess: () => {
            setDialog(null);
            const msg = t("form.toast.stopped");
            notify.success(msg);
            setLiveMessage(msg);
          },
          onError: (err) => {
            setDialogError(err);
            if (promotionErrorCode(err) === "VERSION_CONFLICT") {
              setConflictAlert(err);
              void detailQuery.refetch();
            }
          },
        },
      );
      return;
    }

    if (dialog === "resume") {
      resumeMutation.mutate(
        { id: detail.id, expectedRevision: detail.revision },
        {
          onSuccess: () => {
            setDialog(null);
            const msg = t("form.toast.resumed");
            notify.success(msg);
            setLiveMessage(msg);
          },
          onError: (err) => {
            setDialogError(err);
            const code = promotionErrorCode(err);
            if (code === "VERSION_CONFLICT" || code === "PROMOTION_ENDED") {
              if (code === "VERSION_CONFLICT") {
                setConflictAlert(err);
              }
              void detailQuery.refetch();
            }
          },
        },
      );
      return;
    }

    if (dialog === "delete") {
      deleteMutation.mutate(detail.id, {
        onSuccess: () => {
          setDialog(null);
          notify.success(t("form.toast.deleted"));
          router.push("/promotions");
        },
        onError: (err) => {
          setDialogError(err);
          if (promotionErrorCode(err) === "PROMOTION_IN_USE") {
            void detailQuery.refetch();
          }
        },
      });
    }
  };

  const renderedDialogError = dialogError
    ? promotionErrorCode(dialogError) === "PROMOTION_IN_USE"
      ? t("dialogs.delete.errorInUse")
      : resolvePromotionErrorMessage(dialogError, tErrors)
    : undefined;
  const renderedConflictAlert = conflictAlert
    ? resolvePromotionErrorMessage(conflictAlert, tErrors)
    : null;

  const dialogConfig =
    dialog === "stop"
      ? {
          title: t("dialogs.stop.title"),
          description: t("dialogs.stop.description", { name: label }),
          confirmLabel: t("dialogs.stop.confirm"),
          tone: "danger" as const,
        }
      : dialog === "resume"
        ? {
            title: t("dialogs.resume.title"),
            description: t("dialogs.resume.description", { name: label }),
            confirmLabel: t("dialogs.resume.confirm"),
            tone: "primary" as const,
          }
        : dialog === "delete"
          ? {
              title: t("dialogs.delete.title"),
              description: t("dialogs.delete.description", { name: label }),
              confirmLabel: t("dialogs.delete.confirm"),
              tone: "danger" as const,
            }
          : null;

  const discountLabel = formatDiscountSummary(
    detail.discountType,
    detail.discountValue,
    detail.maxDiscountVnd,
    locale,
    (k, v) => t(`discountLabels.${k}`, v),
  );

  const scopeLabel =
    detail.scope === "ALL"
      ? t("scopeLabels.ALL")
      : detail.scope === "EXAM_KIND"
        ? t("scopeLabels.EXAM_KIND", { count: detail.scopeItems.length })
        : detail.scope === "EXAM"
          ? t("scopeLabels.EXAM", { count: detail.scopeItems.length })
          : detail.scope === "BUNDLE"
            ? t("scopeLabels.BUNDLE")
            : detail.scope;

  const audienceSummaryText =
    detail.kind === "AUTO"
      ? t("audienceLabels.ALL")
      : detail.audience === "NEW_ACCOUNT"
        ? t("audienceLabels.NEW_ACCOUNT")
        : detail.audience === "SELECTED"
          ? t("audienceLabels.SELECTED", {
              classes: detail.audienceClasses.length,
              users: detail.audienceUsers.length,
            })
          : t("audienceLabels.ALL");

  return (
    <div className="@container flex flex-col gap-5">
      <LiveStatus message={liveMessage} />

      <DetailHeader
        back={{ href: "/promotions", label: tCommon("backTo.promotions") }}
        linkComponent={Link}
        title={detail.name}
        titleProps={{ "data-exam-content": "" }}
        chips={
          <>
            <PromotionStatusChip status={detail.status} state={detail.state} />
            <Chip variant="tag">{t(`kinds.${detail.kind}`)}</Chip>
            <Chip variant="tag">{t("detail.revision", { revision: detail.revision })}</Chip>
          </>
        }
        description={
          <>
            {detail.kind === "VOUCHER" && detail.code
              ? t("detail.subtitleVoucher", { code: detail.code })
              : t("detail.subtitleAuto")}
            {" · "}
            {t("detail.updatedAt")}: {formatPromotionDateTime(detail.updatedAt, locale)}
          </>
        }
        actions={
          canModify && (
            <>
              <Button variant="secondary" asChild>
                <Link href={`/promotions/${detail.id}/edit`}>
                  <Pencil />
                  <span>{t("actions.edit")}</span>
                </Link>
              </Button>

              {canToggleStatus && (
                <Button variant="secondary" onClick={() => openDialog(isStopped ? "resume" : "stop")}>
                  {isStopped ? <PlayCircle /> : <PauseCircle />}
                  <span>{isStopped ? t("actions.resume") : t("actions.stop")}</span>
                </Button>
              )}

              <Button
                variant="danger"
                disabled={!canDelete}
                aria-describedby={!canDelete ? deleteReasonId : undefined}
                onClick={() => {
                  if (canDelete) openDialog("delete");
                }}
              >
                <Trash2 />
                <span>{t("actions.delete")}</span>
              </Button>
            </>
          )
        }
      >
        {canModify && !canDelete && (
          <p id={deleteReasonId} className="flex items-center gap-1.5 text-caption text-text-secondary">
            <Lock aria-hidden className="size-3.5 shrink-0 text-text-muted" />
            <span>{t("actions.deleteDisabledUsed")}</span>
          </p>
        )}
      </DetailHeader>

      {renderedConflictAlert && (
        <div
          role="alert"
          className="flex flex-col gap-3 rounded-lg border border-danger/40 bg-danger-bg p-4 text-danger @xl:flex-row @xl:items-center @xl:justify-between"
        >
          <div className="flex items-start gap-2.5">
            <AlertTriangle aria-hidden className="mt-0.5 size-5 shrink-0" />
            <p className="text-body">{renderedConflictAlert}</p>
          </div>
          <Button
            variant="secondary"
            onClick={() => {
              setConflictAlert(null);
              void detailQuery.refetch();
            }}
          >
            {t("actions.reload")}
          </Button>
        </div>
      )}

      {/* 4 Metrics strip */}
      <dl className="grid grid-cols-1 gap-4 @md:grid-cols-2 @4xl:grid-cols-4">
        <Card className="flex flex-col gap-1 p-4">
          <dt className="text-caption text-text-muted">{t("detail.metrics.usage")}</dt>
          <dd className="text-title font-semibold text-text tabular-nums">
            {detail.totalLimit != null
              ? t("detail.metrics.usageWithLimit", {
                  used: detail.usedCount,
                  limit: detail.totalLimit,
                })
              : t("detail.metrics.usageWithoutLimit", { used: detail.usedCount })}
          </dd>
          <dd className="text-caption text-text-secondary tabular-nums">
            {t("detail.metrics.confirmedReserved", {
              confirmed: detail.confirmedCount,
              reserved: detail.reservedCount,
            })}
          </dd>
        </Card>

        <Card className="flex flex-col gap-1 p-4">
          <dt className="text-caption text-text-muted">{t("detail.metrics.totalDiscountPaid")}</dt>
          <dd className="text-title font-semibold text-text tabular-nums">
            {formatVnd(detail.totalDiscountPaid, locale)}
          </dd>
        </Card>

        <Card className="flex flex-col gap-1 p-4">
          <dt className="text-caption text-text-muted">{t("detail.metrics.discountValue")}</dt>
          <dd className="text-title font-semibold text-text tabular-nums">{discountLabel}</dd>
        </Card>

        <Card className="flex flex-col gap-1 p-4">
          <dt className="text-caption text-text-muted">{t("detail.metrics.period")}</dt>
          <dd className="text-body font-medium text-text tabular-nums">
            {t("detail.metrics.periodRange", {
              startsAt: formatPromotionDateTime(detail.startsAt, locale),
              endsAt: formatPromotionDateTime(detail.endsAt, locale),
            })}
          </dd>
        </Card>
      </dl>

      {/* Cap warnings card */}
      <PromotionCapWarningCard warnings={detail.capWarnings} />

      <div className="grid grid-cols-1 gap-6 @4xl:grid-cols-[minmax(0,1fr)_20rem] @4xl:items-start">
        <div className="@container flex min-w-0 flex-col gap-5">
          {/* Config info card */}
          <Card className="flex flex-col gap-4 p-5">
            <h2 className="text-title font-semibold text-text">{t("detail.info.title")}</h2>

            <dl className="grid grid-cols-1 gap-4 text-body @md:grid-cols-2">
              {detail.kind === "VOUCHER" && detail.code && (
                <div className="flex flex-col gap-1">
                  <dt className="text-caption text-text-muted">{t("detail.info.code")}</dt>
                  <dd data-exam-content className="font-mono font-semibold wrap-anywhere text-text">
                    {detail.code}
                  </dd>
                </div>
              )}

              <div className="flex flex-col gap-1">
                <dt className="text-caption text-text-muted">{t("detail.info.discount")}</dt>
                <dd className="font-medium text-text tabular-nums">{discountLabel}</dd>
              </div>

              {detail.kind === "VOUCHER" && (
                <>
                  <div className="flex flex-col gap-1">
                    <dt className="text-caption text-text-muted">{t("detail.info.perUserLimit")}</dt>
                    <dd className="text-text tabular-nums">
                      {detail.perUserLimit != null
                        ? t("discountLabels.perUserOnce", { limit: detail.perUserLimit })
                        : t("common.unlimited")}
                    </dd>
                  </div>

                  <div className="flex flex-col gap-1">
                    <dt className="text-caption text-text-muted">{t("detail.info.combinable")}</dt>
                    <dd className="text-text">
                      {detail.combinable ? t("detail.info.combinableYes") : t("detail.info.combinableNo")}
                    </dd>
                  </div>
                </>
              )}

              {detail.kind === "AUTO" && (
                <div className="flex flex-col gap-1 @md:col-span-2">
                  <dt className="text-caption text-text-muted">{t("detail.info.combinable")}</dt>
                  <dd className="text-caption text-text-secondary">{t("detail.info.stackingNote")}</dd>
                </div>
              )}

              <div className="flex flex-col gap-1 @md:col-span-2">
                <dt className="text-caption text-text-muted">{t("detail.info.description")}</dt>
                <dd className="wrap-anywhere text-text">
                  {detail.description ? (
                    <span data-exam-content className="wrap-anywhere">
                      {detail.description}
                    </span>
                  ) : (
                    <span className="text-text-muted">{t("common.noDescription")}</span>
                  )}
                </dd>
              </div>

              <div className="flex flex-col gap-1.5 @md:col-span-2">
                <dt className="text-caption text-text-muted">{t("detail.info.scope")}</dt>
                <dd className="flex flex-col gap-2">
                  <span className="font-medium text-text">{scopeLabel}</span>
                  {detail.scopeItems.length > 0 && (
                    <div className="flex flex-wrap gap-1.5">
                      {detail.scopeItems.map((item) => {
                        if (item.type === "EXAM_KIND") {
                          const kindText =
                            pickName({ nameVi: item.name, nameEn: item.nameEn }, uiLocale) ||
                            item.code ||
                            item.id.slice(0, 8);
                          return (
                            <Chip
                              key={item.id}
                              variant="tag"
                              className="h-auto max-w-full whitespace-normal py-1"
                            >
                              <span className="wrap-anywhere">{kindText}</span>
                            </Chip>
                          );
                        }
                        const text = item.name ?? item.code ?? item.id.slice(0, 8);
                        return (
                          <Chip
                            key={item.id}
                            variant="tag"
                            className="h-auto max-w-full whitespace-normal py-1"
                          >
                            <span data-exam-content className="wrap-anywhere">
                              {text}
                            </span>
                          </Chip>
                        );
                      })}
                    </div>
                  )}
                </dd>
              </div>

              <div className="flex flex-col gap-1.5 @md:col-span-2">
                <dt className="text-caption text-text-muted">{t("detail.info.audience")}</dt>
                <dd className="flex flex-col gap-2">
                  <span className="font-medium text-text">{audienceSummaryText}</span>
                  {(detail.audienceClasses.length > 0 || detail.audienceUsers.length > 0) && (
                    <div className="flex flex-wrap gap-1.5">
                      {detail.audienceClasses.map((cls) => {
                        const resolved = classLookupMap[cls.id];
                        const code = cls.code ?? resolved?.code;
                        const name = cls.name ?? resolved?.name;
                        const clsLabel =
                          code && name
                            ? `${code} - ${name}`
                            : (name ?? code ?? t("audienceLabels.unknownClass", { id: cls.id.slice(0, 8) }));
                        return (
                          <Chip
                            key={cls.id}
                            variant="tag"
                            className="h-auto max-w-full whitespace-normal py-1"
                          >
                            <span data-exam-content className="wrap-anywhere">
                              {clsLabel}
                            </span>
                          </Chip>
                        );
                      })}
                      {detail.audienceUsers.map((usr) => {
                        const usrLabel =
                          usr.fullName && usr.username
                            ? `${usr.fullName} (@${usr.username})`
                            : (usr.fullName ??
                              usr.username ??
                              t("audienceLabels.unknownUser", { id: usr.id.slice(0, 8) }));
                        return (
                          <Chip
                            key={usr.id}
                            variant="tag"
                            className="h-auto max-w-full whitespace-normal py-1"
                          >
                            <span data-exam-content className="wrap-anywhere">
                              {usrLabel}
                            </span>
                          </Chip>
                        );
                      })}
                    </div>
                  )}
                </dd>
              </div>
            </dl>
          </Card>

          <PromotionRedemptionsCard
            redemptions={detail.redemptions}
            page={redemptionPage}
            onPageChange={setRedemptionPage}
            stateFilter={redemptionState}
            onStateFilterChange={(nextState) => {
              setRedemptionState(nextState);
              setRedemptionPage(1);
            }}
            searchInput={searchInput}
            debouncedQ={debouncedQ}
            onSearchInputChange={handleSearchInputChange}
            onClearFilters={() => {
              if (searchTimerRef.current !== null) {
                window.clearTimeout(searchTimerRef.current);
                searchTimerRef.current = null;
              }
              setRedemptionState("ALL");
              setSearchInput("");
              setDebouncedQ("");
              setRedemptionPage(1);
            }}
            refreshing={detailQuery.isFetching && !detailQuery.isLoading && !isRedemptionsOffline}
            error={redemptionsError}
            isOffline={isRedemptionsOffline}
            onRetry={() => void detailQuery.refetch()}
          />

          <PromotionHistoryCard history={detail.history} discountType={detail.discountType} />
        </div>

        <aside className="flex flex-col gap-4 @4xl:sticky @4xl:top-4">
          <PromotionPricePreviewCard
            promotionId={detail.id}
            scopeItems={detail.scopeItems}
            capWarnings={detail.capWarnings}
          />
        </aside>
      </div>

      {dialogConfig && (
        <ConfirmDialog
          open={dialog !== null}
          onOpenChange={(nextOpen) => {
            if (!mutating && !nextOpen) {
              setDialogError(null);
              setDialog(null);
            }
          }}
          title={dialogConfig.title}
          description={dialogConfig.description}
          confirmLabel={dialogConfig.confirmLabel}
          cancelLabel={t("actions.cancel")}
          tone={dialogConfig.tone}
          loading={mutating}
          error={renderedDialogError}
          onConfirm={handleConfirmDialog}
        />
      )}
    </div>
  );
}
