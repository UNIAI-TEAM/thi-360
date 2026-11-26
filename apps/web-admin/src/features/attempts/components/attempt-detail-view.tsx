"use client";

import type { ColumnDef } from "@tanstack/react-table";
import {
  Button,
  Card,
  cn,
  DetailHeader,
  EmptyState,
  ListCard,
  ListCardTag,
  LiveStatus,
  notify,
  Pagination,
  Select,
  Skeleton,
  StatusChip,
  Table,
  type StatusTone,
  type TableCards,
} from "@thi360/ui";
import { normalizeUiLocale } from "@thi360/web-kit/preferences";
import {
  AlertCircle,
  CheckCircle2,
  Flag,
  PlusCircle,
  RefreshCw,
  TriangleAlert,
  WifiOff,
} from "lucide-react";
import { useFormatter, useLocale, useTranslations } from "next-intl";
import Link from "next/link";
import { useMemo, useState } from "react";
import { DISPLAY_NUMBER_FORMAT } from "@/features/exams/display-number";
import { usePermissions } from "@/features/session/session-provider";
import { useSetBreadcrumbLeaf } from "@/features/shell/breadcrumb-leaf";
import { getErrorStatus } from "@/lib/api/query-client";
import { defaultAttemptApiDeps, resolveAttemptErrorMessage, type AttemptApiDeps } from "../api";
import {
  extractEventDetailInfo,
  formatChoiceSummary,
  hasAttemptScore,
  isIsoTimestamp,
  isKnownAbortReason,
  isKnownAttemptState,
  isKnownCloseReason,
  isKnownEndReason,
  isKnownEventType,
  isKnownNavigationMode,
  isKnownResultView,
  isKnownScope,
  isKnownTiming,
  parseAttemptAnswerDisplay,
  resolveExamKindLabel,
} from "../format";
import { useAdminAttemptDetail, useAttemptExamKinds, useRegradeAttempt } from "../queries";
import {
  ATTEMPT_EVENT_TONE,
  ATTEMPT_PART_STATE_TONE,
  ATTEMPT_STATE_TONE,
  shortAttemptCode,
  shortSessionCode,
} from "../status";
import type {
  AdminAttemptDetailResponse,
  AdminAttemptEventView,
  AdminAttemptItemView,
  AdminAttemptPartView,
  AttemptPartState,
  AttemptState,
} from "../types";
import { CompensateAttemptDialog, FaultAttemptDialog } from "./attempt-action-dialogs";

const ALL_PARTS = "__all__";
const ITEMS_PAGE_SIZE = 20;

const TIMELINE_DOT_CLASS: Record<StatusTone, string> = {
  success: "bg-success border-surface ring-1 ring-success",
  warning: "bg-warning border-surface ring-1 ring-warning",
  danger: "bg-danger border-surface ring-1 ring-danger",
  info: "bg-info border-surface ring-1 ring-info",
  neutral: "bg-text-muted border-surface ring-1 ring-border-strong",
};

const TIMELINE_TITLE_CLASS: Record<StatusTone, string> = {
  success: "text-success",
  warning: "text-warning",
  danger: "text-danger",
  info: "text-info",
  neutral: "text-text",
};

export interface AttemptDetailViewProps {
  attemptId: string;
  deps?: Partial<AttemptApiDeps>;
}

/** Admin attempt operational detail view (`/attempts/[id]`, AC-11..AC-15). */
export function AttemptDetailView({ attemptId, deps }: AttemptDetailViewProps) {
  const t = useTranslations("attempts.detail");
  const tErrors = useTranslations("attempts.errors");
  const api = useMemo(() => ({ ...defaultAttemptApiDeps, ...deps }), [deps]);

  const attemptCode = shortAttemptCode(attemptId);
  useSetBreadcrumbLeaf(attemptCode);

  const detailQuery = useAdminAttemptDetail(attemptId, api);
  const examKindsQuery = useAttemptExamKinds(api);
  const [announcement, setAnnouncement] = useState("");
  const isOffline = detailQuery.fetchStatus === "paused" && detailQuery.isPending;

  if (isOffline) {
    return (
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
  }

  if (detailQuery.isPending) {
    return <AttemptDetailSkeleton label={t("loading")} />;
  }

  if (detailQuery.isError) {
    const notFound = getErrorStatus(detailQuery.error) === 404;
    return (
      <div className="flex flex-col gap-5">
        <StateHeader title={t("title")} />
        <Card>
          <EmptyState
            icon={<TriangleAlert />}
            title={notFound ? t("notFoundTitle") : t("errorTitle")}
            description={
              notFound
                ? t("notFoundDescription")
                : resolveAttemptErrorMessage(detailQuery.error, tErrors)
            }
            headingLevel={2}
            action={
              notFound ? (
                <Button asChild variant="secondary">
                  <Link href="/attempts">{t("backToList")}</Link>
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
  }

  const attempt = detailQuery.data;

  return (
    <div className="@container flex flex-col gap-5">
      <LiveStatus message={announcement} />
      <AttemptDetailContent
        key={attempt.attemptId ?? attemptId}
        attempt={attempt}
        examKinds={examKindsQuery.data}
        api={api}
        announce={setAnnouncement}
      />
    </div>
  );
}

/** Đầu trang của trạng thái mất mạng / lỗi: lối quay lại và tiêu đề chung, chưa có dữ liệu lượt. */
function StateHeader({ title }: { title: string }) {
  const tCommon = useTranslations("common");
  return (
    <DetailHeader back={{ href: "/attempts", label: tCommon("backTo.attempts") }} linkComponent={Link} title={title} />
  );
}

function AttemptDetailContent({
  attempt,
  examKinds,
  api,
  announce,
}: {
  attempt: AdminAttemptDetailResponse;
  examKinds: ReturnType<typeof useAttemptExamKinds>["data"];
  api: AttemptApiDeps;
  announce: (msg: string) => void;
}) {
  const t = useTranslations("attempts");
  const tCommon = useTranslations("common");
  const tContent = useTranslations("content.questionType");
  const format = useFormatter();
  const uiLocale = normalizeUiLocale(useLocale());
  const { can } = usePermissions();

  const canRegrade = can("GRADING.MODIFY");
  const canCompensate = can("ATTEMPT.COMPENSATE");
  const showActionsAside = canRegrade || canCompensate;

  const attemptCode = shortAttemptCode(attempt.attemptId);
  const state: AttemptState = attempt.state ?? "STARTING";
  const kindLabel = resolveExamKindLabel(attempt.examKindCode, examKinds, uiLocale);
  const scopeLabel = isKnownScope(attempt.scope) ? t(`scope.${attempt.scope}`) : attempt.scope ?? "";

  const parts = useMemo(() => attempt.parts ?? [], [attempt.parts]);
  const events = useMemo(() => attempt.events ?? [], [attempt.events]);
  const items = useMemo(() => attempt.items ?? [], [attempt.items]);

  const failedParts = useMemo(
    () => parts.filter((part) => part.state === "GENERATION_FAILED"),
    [parts],
  );

  const partNameById = useMemo(() => {
    const map = new Map<string, string>();
    for (const part of parts) {
      if (part.partId) {
        map.set(part.partId, part.partName || part.partCode || shortAttemptCode(part.partId));
      }
    }
    return map;
  }, [parts]);

  return (
    <div className="flex flex-col gap-5">
      <DetailHeader
        back={{ href: "/attempts", label: tCommon("backTo.attempts") }}
        linkComponent={Link}
        title={t("detail.heading", { code: attemptCode })}
        chips={
          <>
            <StatusChip tone={ATTEMPT_STATE_TONE[state]}>{t(`status.${state}`)}</StatusChip>
            {attempt.faultFlag && (
              <StatusChip tone="warning">
                {attempt.compensated ? t("list.faultCompensated") : t("list.faultFlag")}
              </StatusChip>
            )}
            {attempt.regrading && <StatusChip tone="info">{t("list.regrading")}</StatusChip>}
            {kindLabel && <ListCardTag>{kindLabel}</ListCardTag>}
            {scopeLabel && <ListCardTag>{scopeLabel}</ListCardTag>}
            {attempt.partCode && <ListCardTag>{attempt.partCode}</ListCardTag>}
          </>
        }
        description={
          (attempt.examName || attempt.username) && (
            <>
              {attempt.examName && <span data-exam-content>{attempt.examName}</span>}
              {attempt.examName && attempt.username && " · "}
              {attempt.username}
            </>
          )
        }
      />

      {attempt.state === "GRADING_ERROR" && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-xl border border-danger/40 border-l-4 border-l-danger bg-danger-bg px-4 py-3 text-body"
        >
          <AlertCircle aria-hidden className="mt-0.5 size-5 shrink-0 text-danger" />
          <div className="flex min-w-0 flex-col gap-1">
            <p className="font-semibold text-danger">{t("detail.alerts.gradingErrorTitle")}</p>
            <p className="text-text-secondary">
              {attempt.gradingError ? (
                <span data-exam-content>{attempt.gradingError}</span>
              ) : (
                t("detail.alerts.gradingErrorDescription")
              )}
            </p>
          </div>
        </div>
      )}

      {failedParts.length > 0 && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-xl border border-danger/40 border-l-4 border-l-danger bg-danger-bg px-4 py-3 text-body"
        >
          <AlertCircle aria-hidden className="mt-0.5 size-5 shrink-0 text-danger" />
          <div className="flex min-w-0 flex-col gap-1">
            <p className="font-semibold text-danger">
              {t.rich("detail.alerts.partFailedTitle", {
                names: failedParts
                  .map((part) => part.partName || part.partCode)
                  .filter(Boolean)
                  .join(", "),
                part: (chunks) => <span data-exam-content>{chunks}</span>,
                tries: format.number(
                  Math.max(...failedParts.map((part) => part.generationTries ?? 0)),
                ),
              })}
            </p>
            <p className="text-text-secondary">{t("detail.alerts.partFailedDescription")}</p>
          </div>
        </div>
      )}

      {attempt.faultFlag && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-xl border border-warning/40 border-l-4 border-l-warning bg-warning-bg px-4 py-3 text-body"
        >
          <Flag aria-hidden className="mt-0.5 size-5 shrink-0 text-warning" />
          <div className="flex min-w-0 flex-col gap-1">
            <p className="font-semibold text-warning">
              {attempt.compensated
                ? t("detail.alerts.faultCompensatedTitle")
                : t("detail.alerts.faultTitle")}
            </p>
            {attempt.faultReason && (
              <p className="text-text-secondary" data-exam-content>
                {attempt.faultReason}
              </p>
            )}
          </div>
        </div>
      )}

      <div
        className={cn(
          "grid items-start gap-4",
          showActionsAside && "@4xl:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]",
        )}
      >
        <div className="flex min-w-0 flex-col gap-4">
          <AttemptMetadataCard attempt={attempt} kindLabel={kindLabel} scopeLabel={scopeLabel} />
          <AttemptPartsSection parts={parts} attemptCode={attemptCode} />
        </div>

        {showActionsAside && (
          <AttemptActionsAside
            attempt={attempt}
            canRegrade={canRegrade}
            canCompensate={canCompensate}
            api={api}
            announce={announce}
          />
        )}
      </div>

      <AttemptTimelineSection events={events} partNameById={partNameById} />
      <AttemptAnswersSection
        items={items}
        parts={parts}
        partNameById={partNameById}
        attemptCode={attemptCode}
        tContent={tContent}
      />
    </div>
  );
}

function AttemptMetadataCard({
  attempt,
  kindLabel,
  scopeLabel,
}: {
  attempt: AdminAttemptDetailResponse;
  kindLabel: string;
  scopeLabel: string;
}) {
  const t = useTranslations("attempts");
  const format = useFormatter();

  const formatDt = (iso: string | undefined) =>
    iso ? format.dateTime(new Date(iso), "dateTime") : "—";

  const timingText = [
    isKnownTiming(attempt.timing) ? t(`timing.${attempt.timing}`) : attempt.timing,
    isKnownNavigationMode(attempt.navigationMode)
      ? t(`navigationMode.${attempt.navigationMode}`)
      : attempt.navigationMode,
  ]
    .filter(Boolean)
    .join(" · ");

  const scoreValue = hasAttemptScore(attempt)
    ? attempt.maxScore != null && Number.isFinite(attempt.maxScore)
      ? t("list.scoreValue", {
          score: format.number(attempt.displayScore!, DISPLAY_NUMBER_FORMAT),
          max: format.number(attempt.maxScore, DISPLAY_NUMBER_FORMAT),
        })
      : format.number(attempt.displayScore!, DISPLAY_NUMBER_FORMAT)
    : "—";

  const endReasonText = isKnownEndReason(attempt.endReason)
    ? t(`endReason.${attempt.endReason}`)
    : isKnownAbortReason(attempt.abortReason)
      ? t(`abortReason.${attempt.abortReason}`)
      : "";

  const resultViewText = isKnownResultView(attempt.resultView)
    ? t(`resultView.${attempt.resultView}`)
    : attempt.resultView || "—";

  const editSessionCode = shortSessionCode(attempt.editSessionId);
  const choicesEntries = Object.entries(attempt.choices ?? {});

  return (
    <Card className="flex flex-col gap-3">
      <h2 className="text-h3 font-bold text-text">{t("detail.infoTitle")}</h2>
      <dl className="m-0 grid grid-cols-[repeat(auto-fit,minmax(min(13.75rem,100%),1fr))] gap-x-6 gap-y-3 text-body tabular-nums">
        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.learner")}</dt>
          <dd className="m-0 font-semibold text-text">
            {attempt.username || shortAttemptCode(attempt.userId)}
            {attempt.userFullName && (
              <span className="font-normal text-text-muted"> · {attempt.userFullName}</span>
            )}
          </dd>
          {(attempt.userEmail || attempt.userCode) && (
            <dd className="m-0 text-caption wrap-anywhere text-text-muted">
              {[attempt.userEmail, attempt.userCode].filter(Boolean).join(" · ")}
            </dd>
          )}
        </div>

        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.exam")}</dt>
          <dd className="m-0 font-semibold text-text">
            {attempt.examName ? (
              <span data-exam-content>{attempt.examName}</span>
            ) : (
              "—"
            )}
            {attempt.examVersionNo != null && (
              <span className="font-normal text-text-muted">
                {" "}
                · {t("detail.fields.version", { version: attempt.examVersionNo })}
              </span>
            )}
          </dd>
          {(kindLabel || scopeLabel) && (
            <dd className="m-0 text-caption text-text-muted">
              {[kindLabel, scopeLabel, attempt.partCode].filter(Boolean).join(" · ")}
            </dd>
          )}
        </div>

        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.attemptNo")}</dt>
          <dd className="m-0 font-medium text-text">
            {attempt.attemptNo != null ? format.number(attempt.attemptNo) : "—"}
          </dd>
        </div>

        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.timing")}</dt>
          <dd className="m-0 font-medium text-text">{timingText || "—"}</dd>
        </div>

        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.startedAt")}</dt>
          <dd className="m-0 font-medium text-text">{formatDt(attempt.startedAt)}</dd>
        </div>

        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.deadlineAt")}</dt>
          <dd className="m-0 font-medium text-text">{formatDt(attempt.deadlineAt)}</dd>
        </div>

        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.finishedAt")}</dt>
          <dd className="m-0 font-medium text-text">
            {formatDt(attempt.finishedAt)}
            {endReasonText && (
              <span className="font-normal text-text-muted"> · {endReasonText}</span>
            )}
          </dd>
        </div>

        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.gradedAt")}</dt>
          <dd className="m-0 font-medium text-text">{formatDt(attempt.gradedAt)}</dd>
        </div>

        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.editSession")}</dt>
          <dd className="m-0 font-medium text-text">
            {editSessionCode ? (
              <>
                <span className="font-mono">{editSessionCode}</span>
                {attempt.editSessionSeenAt && (
                  <span className="font-normal text-text-muted">
                    {" "}
                    · {t("detail.fields.seenAt", { time: formatDt(attempt.editSessionSeenAt) })}
                  </span>
                )}
              </>
            ) : (
              "—"
            )}
          </dd>
        </div>

        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.resultView")}</dt>
          <dd className="m-0 font-medium text-text">{resultViewText}</dd>
        </div>

        <div className="flex flex-col gap-0.5">
          <dt className="text-caption text-text-muted">{t("detail.fields.score")}</dt>
          <dd className="m-0 font-semibold text-text">{scoreValue}</dd>
        </div>

        {(attempt.faultFlag || attempt.compensated) && (
          <div className="flex flex-col gap-0.5">
            <dt className="text-caption text-text-muted">{t("detail.fields.faultStatus")}</dt>
            <dd className="m-0 font-medium text-text">
              {attempt.compensated
                ? t("list.faultCompensated")
                : t("list.faultFlag")}
              {attempt.faultReason && (
                <span className="block font-normal text-text-secondary" data-exam-content>
                  {attempt.faultReason}
                </span>
              )}
            </dd>
          </div>
        )}

        {choicesEntries.length > 0 && (
          <div className="flex flex-col gap-0.5">
            <dt className="text-caption text-text-muted">{t("detail.fields.choices")}</dt>
            <dd className="m-0 font-medium text-text">
              {choicesEntries
                .map(([, choice]) => formatChoiceSummary(choice))
                .filter(Boolean)
                .join(" · ") || "—"}
            </dd>
          </div>
        )}
      </dl>
    </Card>
  );
}

function AttemptPartsSection({
  parts,
  attemptCode,
}: {
  parts: readonly AdminAttemptPartView[];
  attemptCode: string;
}) {
  const t = useTranslations("attempts");
  const format = useFormatter();

  const formatDt = useMemo(
    () => (iso: string | undefined) => (iso ? format.dateTime(new Date(iso), "dateTime") : "—"),
    [format],
  );

  const columns = useMemo<ColumnDef<AdminAttemptPartView>[]>(
    () => [
      {
        id: "part",
        header: t("detail.parts.part"),
        cell: ({ row }) => {
          const part = row.original;
          const metaParts: string[] = [];
          if (part.questionCountRequired != null) {
            metaParts.push(
              t("detail.parts.questionCount", { count: format.number(part.questionCountRequired) }),
            );
          }
          if (part.durationMinutes != null) {
            metaParts.push(
              t("detail.parts.durationMinutes", { count: format.number(part.durationMinutes) }),
            );
          }
          if (part.maxPoints != null) {
            metaParts.push(
              t("detail.parts.maxPoints", {
                points: format.number(part.maxPoints, DISPLAY_NUMBER_FORMAT),
              }),
            );
          }
          const choiceText = formatChoiceSummary(part.choice);
          return (
            <div className="flex min-w-44 flex-col gap-0.5">
              <span data-exam-content className="font-semibold text-text">
                {part.partName || part.partCode || "—"}
              </span>
              {metaParts.length > 0 && (
                <span className="text-caption text-text-muted">{metaParts.join(" · ")}</span>
              )}
              {choiceText && (
                <span className="text-caption text-text-secondary">
                  {t("detail.parts.choiceLabel", { choice: choiceText })}
                </span>
              )}
            </div>
          );
        },
      },
      {
        id: "state",
        header: t("detail.parts.state"),
        cell: ({ row }) => {
          const st: AttemptPartState = row.original.state ?? "PENDING";
          return (
            <StatusChip tone={ATTEMPT_PART_STATE_TONE[st]}>{t(`partState.${st}`)}</StatusChip>
          );
        },
      },
      {
        id: "startedAt",
        header: t("detail.parts.startedAt"),
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text tabular-nums">
            {formatDt(row.original.startedAt)}
          </span>
        ),
      },
      {
        id: "deadlineAt",
        header: t("detail.parts.deadlineAt"),
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text tabular-nums">
            {formatDt(row.original.deadlineAt)}
          </span>
        ),
      },
      {
        id: "closedAt",
        header: t("detail.parts.closedAt"),
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text tabular-nums">
            {formatDt(row.original.closedAt)}
          </span>
        ),
      },
      {
        id: "closeReason",
        header: t("detail.parts.closeReason"),
        cell: ({ row }) => {
          const reason = row.original.closeReason;
          return (
            <span className="text-text-secondary">
              {isKnownCloseReason(reason) ? t(`closeReason.${reason}`) : reason || "—"}
            </span>
          );
        },
      },
      {
        id: "generationTries",
        header: () => <span className="block text-end">{t("detail.parts.generationTries")}</span>,
        cell: ({ row }) => {
          const part = row.original;
          return (
            <div className="flex flex-col items-end gap-0.5 text-end">
              <span
                className={cn(
                  "font-semibold tabular-nums",
                  part.generationError ? "text-danger" : "text-text",
                )}
              >
                {part.generationTries != null ? format.number(part.generationTries) : "—"}
              </span>
              {part.generationError && (
                <span className="text-caption text-danger" data-exam-content>
                  {part.generationError}
                </span>
              )}
            </div>
          );
        },
      },
    ],
    [t, format, formatDt],
  );

  const cards = useMemo<TableCards<AdminAttemptPartView>>(
    () => ({
      render: (part) => {
        const st: AttemptPartState = part.state ?? "PENDING";
        const choiceText = formatChoiceSummary(part.choice);
        return (
          <ListCard
            title={<span data-exam-content>{part.partName || part.partCode || "—"}</span>}
            caption={choiceText ? t("detail.parts.choiceLabel", { choice: choiceText }) : undefined}
            status={
              <StatusChip tone={ATTEMPT_PART_STATE_TONE[st]}>{t(`partState.${st}`)}</StatusChip>
            }
            meta={[
              { label: t("detail.parts.startedAt"), value: formatDt(part.startedAt) },
              { label: t("detail.parts.deadlineAt"), value: formatDt(part.deadlineAt) },
              { label: t("detail.parts.closedAt"), value: formatDt(part.closedAt) },
              {
                label: t("detail.parts.closeReason"),
                value: isKnownCloseReason(part.closeReason)
                  ? t(`closeReason.${part.closeReason}`)
                  : part.closeReason || "—",
              },
              {
                label: t("detail.parts.generationTries"),
                value:
                  part.generationTries != null ? format.number(part.generationTries) : "—",
              },
            ]}
          >
            {part.generationError && (
              <p className="text-caption font-medium text-danger" data-exam-content>
                {part.generationError}
              </p>
            )}
          </ListCard>
        );
      },
    }),
    [t, format, formatDt],
  );

  const tableData = useMemo(() => [...parts], [parts]);

  return (
    <Card padding="none" className="overflow-hidden">
      <div className="border-b border-border px-4 py-3">
        <h2 className="text-h3 font-bold text-text">{t("detail.partsTitle")}</h2>
      </div>
      <Table
        aria-label={t("detail.partsAriaLabel", { code: attemptCode })}
        columns={columns}
        data={tableData}
        getRowId={(row) => row.partId ?? `${row.ord}-${row.partCode}`}
        cards={cards}
        empty={
          <EmptyState
            headingLevel={3}
            title={t("detail.partsEmpty")}
            className="py-8"
          />
        }
      />
    </Card>
  );
}

function AttemptActionsAside({
  attempt,
  canRegrade,
  canCompensate,
  api,
  announce,
}: {
  attempt: AdminAttemptDetailResponse;
  canRegrade: boolean;
  canCompensate: boolean;
  api: AttemptApiDeps;
  announce: (msg: string) => void;
}) {
  const t = useTranslations("attempts.detail.actions");
  const tErrors = useTranslations("attempts.errors");
  const attemptId = attempt.attemptId ?? "";
  const attemptCode = shortAttemptCode(attempt.attemptId);

  const regradeMutation = useRegradeAttempt(attemptId, api);
  const [regradeError, setRegradeError] = useState<string | null>(null);
  const [faultOpen, setFaultOpen] = useState(false);
  const [compensateOpen, setCompensateOpen] = useState(false);

  const canRunRegrade = canRegrade && attempt.state === "GRADING_ERROR";
  const canMarkFault =
    canCompensate &&
    !attempt.faultFlag &&
    attempt.state !== "STARTING" &&
    attempt.state !== "ABORTED";
  const canRunCompensate = canCompensate && Boolean(attempt.faultFlag) && !attempt.compensated;

  async function handleRegrade() {
    setRegradeError(null);
    try {
      await regradeMutation.mutateAsync();
      const msg = t("regradeSuccess", { code: attemptCode });
      announce(msg);
      notify.success(msg);
    } catch (error) {
      const msg = resolveAttemptErrorMessage(error, tErrors);
      setRegradeError(msg);
      announce(msg);
    }
  }

  return (
    <aside
      aria-labelledby="attempt-actions-heading"
      className="flex flex-col gap-1 rounded-xl border border-border bg-surface p-4"
    >
      <h2 id="attempt-actions-heading" className="mb-1 text-h3 font-bold text-text">
        {t("title")}
      </h2>

      {canRegrade && (
        <div className="flex flex-col gap-2 border-t border-border py-3">
          <div className="flex items-center gap-2 font-semibold text-text">
            <RefreshCw aria-hidden className="size-4 text-text-secondary" />
            <span>{t("regradeHeading")}</span>
          </div>
          <p className="text-caption text-text-secondary">{t("regradeDescription")}</p>
          {regradeError && (
            <div
              role="alert"
              className="rounded-lg border border-danger/40 bg-danger-bg px-3 py-2 text-caption text-danger"
            >
              {regradeError}
            </div>
          )}
          {canRunRegrade && (
            <Button
              type="button"
              variant="primary"
              loading={regradeMutation.isPending}
              onClick={() => void handleRegrade()}
              className="self-start"
            >
              <RefreshCw />
              {t("regradeButton")}
            </Button>
          )}
        </div>
      )}

      {canCompensate && (
        <div className="flex flex-col gap-2 border-t border-border py-3">
          <div className="flex items-center gap-2 font-semibold text-danger">
            <Flag aria-hidden className="size-4" />
            <span>{t("faultHeading")}</span>
          </div>
          <p className="text-caption text-text-secondary">
            {attempt.faultFlag
              ? t("faultAlreadyMarked")
              : attempt.state === "STARTING" || attempt.state === "ABORTED"
                ? t("faultNotAllowed")
                : t("faultDescription")}
          </p>
          {canMarkFault && (
            <Button
              type="button"
              variant="danger"
              onClick={() => setFaultOpen(true)}
              className="self-start"
            >
              <Flag />
              {t("faultButton")}
            </Button>
          )}
        </div>
      )}

      {canCompensate && (
        <div className="flex flex-col gap-2 border-t border-border pt-3 pb-1">
          <div className="flex items-center gap-2 font-semibold text-text">
            <PlusCircle aria-hidden className="size-4 text-text-secondary" />
            <span>{t("compensateHeading")}</span>
          </div>
          {attempt.compensated ? (
            <p className="flex items-center gap-1.5 text-body font-medium text-success">
              <CheckCircle2 aria-hidden className="size-4 shrink-0" />
              <span>{t("alreadyCompensated")}</span>
            </p>
          ) : (
            <p className="text-caption text-text-secondary">{t("compensateDescription")}</p>
          )}
          {canRunCompensate && (
            <Button
              type="button"
              variant="primary"
              onClick={() => setCompensateOpen(true)}
              className="self-start"
            >
              <PlusCircle />
              {t("compensateButton")}
            </Button>
          )}
        </div>
      )}

      <p className="mt-1 border-t border-border pt-3 text-caption text-text-muted">
        {t("permissionFootnote")}
      </p>

      <FaultAttemptDialog
        open={faultOpen}
        onOpenChange={setFaultOpen}
        attempt={attempt}
        deps={api}
        onSuccess={() => announce(t("faultSuccess", { code: attemptCode }))}
      />

      <CompensateAttemptDialog
        open={compensateOpen}
        onOpenChange={setCompensateOpen}
        attempt={attempt}
        deps={api}
        onSuccess={() => announce(t("compensateSuccess", { code: attemptCode }))}
      />
    </aside>
  );
}

function formatEventFieldValue(
  key: string,
  value: string,
  t: ReturnType<typeof useTranslations<"attempts">>,
  format: ReturnType<typeof useFormatter>,
): string {
  if ((key === "state" || key === "fromState" || key === "toState") && isKnownAttemptState(value)) {
    return t(`status.${value}`);
  }
  if (key === "endReason" && isKnownEndReason(value)) {
    return t(`endReason.${value}`);
  }
  if (key === "closeReason" && isKnownCloseReason(value)) {
    return t(`closeReason.${value}`);
  }
  if (key === "abortReason" && isKnownAbortReason(value)) {
    return t(`abortReason.${value}`);
  }
  if (
    (key === "reclaimed" || key === "pendingReview" || key === "regrading" || key === "compensated") &&
    (value === "true" || value === "false")
  ) {
    return t(value === "true" ? "detail.booleanYes" : "detail.booleanNo");
  }
  if (
    (key === "startedAt" ||
      key === "finishedAt" ||
      key === "closedAt" ||
      key === "deadlineAt" ||
      key === "finalizedAt" ||
      key === "submittedAt") &&
    isIsoTimestamp(value)
  ) {
    return format.dateTime(new Date(value), "dateTime");
  }
  return value;
}

function AttemptTimelineSection({
  events,
  partNameById,
}: {
  events: readonly AdminAttemptEventView[];
  partNameById: ReadonlyMap<string, string>;
}) {
  const t = useTranslations("attempts");
  const format = useFormatter();

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-h3 font-bold text-text">{t("detail.eventsTitle")}</h2>
        <span className="text-caption text-text-muted tabular-nums">
          {t("detail.eventsCount", { count: events.length })}
        </span>
      </div>
      {events.length === 0 ? (
        <p className="py-4 text-body text-text-muted">{t("detail.eventsEmpty")}</p>
      ) : (
        <ol className="m-0 flex list-none flex-col p-0">
          {events.map((event, index) => {
            const eventType = isKnownEventType(event.type) ? event.type : null;
            const tone: StatusTone = eventType ? ATTEMPT_EVENT_TONE[eventType] : "neutral";
            const title = eventType ? t(`events.${eventType}`) : event.type || "—";
            const sessionCode = shortSessionCode(event.sessionId);
            const detail = extractEventDetailInfo(event, partNameById);
            return (
              <li
                key={event.id ?? `${event.type}-${index}`}
                className="grid grid-cols-[9.5rem_1.25rem_minmax(0,1fr)] gap-x-3 text-body tabular-nums max-sm:grid-cols-[1.25rem_minmax(0,1fr)]"
              >
                <span className="py-2 text-caption whitespace-nowrap text-text-muted max-sm:col-start-2 max-sm:pb-0">
                  {event.at ? format.dateTime(new Date(event.at), "dateTime") : "—"}
                </span>
                <span
                  aria-hidden
                  className="relative flex justify-center max-sm:col-start-1 max-sm:row-span-2 max-sm:row-start-1"
                >
                  <span className="absolute inset-y-0 w-px bg-border" />
                  <span
                    className={cn(
                      "relative mt-3.5 size-2.5 rounded-full border-2",
                      TIMELINE_DOT_CLASS[tone],
                    )}
                  />
                </span>
                <div className="flex min-w-0 flex-col gap-0.5 py-2 pb-3 max-sm:col-start-2 max-sm:pt-0.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className={cn("font-semibold", TIMELINE_TITLE_CLASS[tone])}>
                      {title}
                    </span>
                    {sessionCode && (
                      <span className="inline-flex h-5.5 items-center rounded-full border border-border px-2 font-mono text-caption text-text-muted">
                        {t("detail.sessionBadge", { code: sessionCode })}
                      </span>
                    )}
                  </div>
                  {(detail.partName || detail.entries.length > 0) && (
                    <p className="text-caption wrap-anywhere text-text-secondary">
                      {detail.partName && <span data-exam-content>{detail.partName}</span>}
                      {detail.partName && detail.entries.length > 0 && " · "}
                      {detail.entries.map((entry, idx) => {
                        const displayValue = formatEventFieldValue(entry.key, entry.value, t, format);
                        return (
                          <span key={`${entry.key}-${idx}`}>
                            {idx > 0 && " · "}
                            {entry.labelKey ? t(`detail.eventFields.${entry.labelKey}`) : entry.key}:{" "}
                            {entry.isExamContent ? (
                              <span data-exam-content>{displayValue}</span>
                            ) : (
                              displayValue
                            )}
                          </span>
                        );
                      })}
                    </p>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </Card>
  );
}

function AttemptAnswersSection({
  items,
  parts,
  partNameById,
  attemptCode,
  tContent,
}: {
  items: readonly AdminAttemptItemView[];
  parts: readonly AdminAttemptPartView[];
  partNameById: ReadonlyMap<string, string>;
  attemptCode: string;
  tContent: ReturnType<typeof useTranslations<"content.questionType">>;
}) {
  const t = useTranslations("attempts");
  const format = useFormatter();
  const [selectedPartId, setSelectedPartId] = useState<string>(ALL_PARTS);
  const [page, setPage] = useState(1);

  const leafItems = useMemo(
    () => items.filter((item) => item.questionType !== "QUESTION_SET"),
    [items],
  );

  const filteredItems = useMemo(
    () =>
      selectedPartId === ALL_PARTS
        ? leafItems
        : leafItems.filter((item) => item.partId === selectedPartId),
    [leafItems, selectedPartId],
  );

  const answeredCount = useMemo(
    () =>
      leafItems.filter((item) => {
        const display = parseAttemptAnswerDisplay(item);
        return display.kind !== "blank" && display.kind !== "notApplicable";
      }).length,
    [leafItems],
  );

  const totalPages = Math.max(1, Math.ceil(filteredItems.length / ITEMS_PAGE_SIZE));
  const safePage = Math.min(page, totalPages);
  const pagedItems = useMemo(() => {
    const start = (safePage - 1) * ITEMS_PAGE_SIZE;
    return filteredItems.slice(start, start + ITEMS_PAGE_SIZE);
  }, [filteredItems, safePage]);

  const partOptions = useMemo(
    () => [
      { value: ALL_PARTS, label: t("detail.answers.allParts") },
      ...parts
        .filter((p) => Boolean(p.partId))
        .map((part) => {
          const name = part.partName || part.partCode || shortAttemptCode(part.partId);
          return {
            value: part.partId!,
            label: <span data-exam-content>{name}</span>,
            textValue: name,
          };
        }),
    ],
    [parts, t],
  );

  const columns = useMemo<ColumnDef<AdminAttemptItemView>[]>(
    () => [
      {
        id: "displayNo",
        header: t("detail.answers.questionNo"),
        cell: ({ row }) => {
          const item = row.original;
          const no = item.displayNo ?? item.ord ?? 0;
          return (
            <span className="font-semibold whitespace-nowrap text-text tabular-nums">
              {t("detail.answers.questionLabel", { number: no })}
            </span>
          );
        },
      },
      {
        id: "part",
        header: t("detail.answers.part"),
        cell: ({ row }) => {
          const name = row.original.partId ? partNameById.get(row.original.partId) : undefined;
          return name ? (
            <span data-exam-content className="whitespace-nowrap text-text-secondary">
              {name}
            </span>
          ) : (
            <span className="text-text-muted">—</span>
          );
        },
      },
      {
        id: "questionType",
        header: t("detail.answers.questionType"),
        cell: ({ row }) => {
          const qType = row.original.questionType;
          return (
            <span className="whitespace-nowrap text-text-secondary">
              {qType ? tContent(qType) : "—"}
            </span>
          );
        },
      },
      {
        id: "answer",
        header: t("detail.answers.answer"),
        cell: ({ row }) => (
          <div className="max-w-96 min-w-44">
            <AttemptAnswerSummary item={row.original} />
          </div>
        ),
      },
      {
        id: "receivedAt",
        header: t("detail.answers.receivedAt"),
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text tabular-nums">
            {row.original.answerReceivedAt
              ? format.dateTime(new Date(row.original.answerReceivedAt), "dateTime")
              : "—"}
          </span>
        ),
      },
      {
        id: "answerSeq",
        header: () => <span className="block text-end">{t("detail.answers.seq")}</span>,
        cell: ({ row }) => (
          <span className="block text-end whitespace-nowrap text-text tabular-nums">
            {row.original.answerSeq != null ? format.number(row.original.answerSeq) : "—"}
          </span>
        ),
      },
    ],
    [t, tContent, format, partNameById],
  );

  const cards = useMemo<TableCards<AdminAttemptItemView>>(
    () => ({
      render: (item) => {
        const no = item.displayNo ?? item.ord ?? 0;
        const partName = item.partId ? partNameById.get(item.partId) : undefined;
        return (
          <ListCard
            title={t("detail.answers.questionLabel", { number: no })}
            caption={item.questionType ? tContent(item.questionType) : undefined}
            tags={partName ? <ListCardTag><span data-exam-content>{partName}</span></ListCardTag> : undefined}
            meta={[
              { label: t("detail.answers.answer"), value: <AttemptAnswerSummary item={item} /> },
              {
                label: t("detail.answers.receivedAt"),
                value: item.answerReceivedAt
                  ? format.dateTime(new Date(item.answerReceivedAt), "dateTime")
                  : "—",
              },
              {
                label: t("detail.answers.seq"),
                value: item.answerSeq != null ? format.number(item.answerSeq) : "—",
              },
            ]}
          />
        );
      },
    }),
    [t, tContent, format, partNameById],
  );

  return (
    <Card padding="none" className="overflow-hidden">
      <div className="flex flex-wrap items-end justify-between gap-3 border-b border-border px-4 py-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h2 className="text-h3 font-bold text-text">{t("detail.answersTitle")}</h2>
          <p className="text-caption text-text-muted tabular-nums">
            {t("detail.answersSummary", {
              total: format.number(leafItems.length),
              answered: format.number(answeredCount),
            })}
          </p>
        </div>
        {parts.length > 0 && (
          <div className="min-w-44">
            <Select
              aria-label={t("detail.answers.partFilter")}
              options={partOptions}
              value={selectedPartId}
              onValueChange={(value) => {
                setSelectedPartId(value);
                setPage(1);
              }}
            />
          </div>
        )}
      </div>

      <Table
        aria-label={t("detail.answersAriaLabel", { code: attemptCode })}
        columns={columns}
        data={pagedItems}
        getRowId={(row) => row.itemId ?? `${row.partId}-${row.ord}`}
        cards={cards}
        empty={
          <EmptyState
            headingLevel={3}
            title={t("detail.answers.empty")}
            className="py-8"
          />
        }
      />

      {filteredItems.length > ITEMS_PAGE_SIZE && (
        <div className="border-t border-border px-4 py-3">
          <Pagination
            page={safePage}
            pageSize={ITEMS_PAGE_SIZE}
            total={filteredItems.length}
            onPageChange={setPage}
            labels={{
              nav: t("list.paginationNav"),
              previous: t("list.paginationPrevious"),
              next: t("list.paginationNext"),
              rowsPerPage: t("list.paginationRowsPerPage"),
              page: (p) => t("list.paginationPage", { page: format.number(p) }),
              range: ({ from, to, total }) =>
                t("list.paginationRange", {
                  from: format.number(from),
                  to: format.number(to),
                  total: format.number(total),
                }),
            }}
          />
        </div>
      )}
    </Card>
  );
}

function AttemptAnswerSummary({ item }: { item: AdminAttemptItemView }) {
  const t = useTranslations("attempts");
  const format = useFormatter();
  const parsed = parseAttemptAnswerDisplay(item);
  switch (parsed.kind) {
    case "blank":
      return <span className="text-text-muted">{t("detail.answers.blank")}</span>;
    case "notApplicable":
      return <span className="text-text-muted">—</span>;
    case "text":
      return parsed.isExamContent ? (
        <span data-exam-content className="wrap-anywhere text-text">
          {parsed.text}
        </span>
      ) : (
        <span className="wrap-anywhere text-text">{parsed.text}</span>
      );
    case "cloze":
      return (
        <span className="wrap-anywhere text-text">
          {parsed.entries.map((entry, idx) => (
            <span key={entry.index}>
              {idx > 0 && " · "}
              {t("detail.answers.clozeBlank", { index: entry.index })}{" "}
              <span data-exam-content>{entry.value}</span>
            </span>
          ))}
        </span>
      );
    case "trueFalse":
      return (
        <span className="wrap-anywhere text-text">
          {parsed.entries.map((entry, idx) => (
            <span key={entry.key}>
              {idx > 0 && " · "}
              {entry.key}: {entry.value ? t("detail.answers.true") : t("detail.answers.false")}
            </span>
          ))}
        </span>
      );
    case "essay":
      return (
        <span className="wrap-anywhere text-text">
          {t("detail.answers.essaySummary", { words: format.number(parsed.wordCount) })}
          {parsed.preview && (
            <>
              {" · "}
              <span data-exam-content className="text-text-secondary">
                {parsed.preview}
              </span>
            </>
          )}
        </span>
      );
  }
}

export function AttemptDetailSkeleton({ label }: { label?: string }) {
  return (
    <div className="flex flex-col gap-5" aria-busy="true" aria-label={label}>
      <Skeleton className="h-10 w-64 max-w-full" />
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,22rem)]">
        <Skeleton className="h-80" />
        <Skeleton className="h-80" />
      </div>
      <Skeleton className="h-64" />
    </div>
  );
}
