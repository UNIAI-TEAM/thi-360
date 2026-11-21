"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Card,
  EmptyState,
  ListCard,
  SectionTitle,
  Skeleton,
  StatusChip,
  type StatusTone,
  Table,
  type TableCards,
} from "@thi360/ui";
import {
  Award,
  BookOpen,
  ClipboardList,
  KeyRound,
  LifeBuoy,
  TriangleAlert,
} from "lucide-react";
import { useFormatter, useTranslations } from "next-intl";
import { type ReactNode, useMemo } from "react";
import { DISPLAY_NUMBER_FORMAT } from "@/features/exams/display-number";
import { usePermissions } from "@/features/session/session-provider";
import { type StudentLearning, type UsersApiDeps, USERS_QUERY_KEYS } from "../api";

type StudentLearningEntitlement = StudentLearning["entitlements"][number];
type StudentLearningAttempt = StudentLearning["attempts"][number];
type StaticBlockKey = "assessments" | "practice" | "supportRequests";
type LearningBlockKey = "entitlements" | "attempts" | StaticBlockKey;

interface SkeletonBlockConfig {
  key: LearningBlockKey;
  fullWidth?: boolean;
}

interface StaticBlockConfig {
  key: StaticBlockKey;
  icon: ReactNode;
  fullWidth?: boolean;
}

const SKELETON_BLOCKS: readonly SkeletonBlockConfig[] = [
  { key: "entitlements", fullWidth: true },
  { key: "attempts", fullWidth: true },
  { key: "assessments" },
  { key: "practice" },
  { key: "supportRequests", fullWidth: true },
];

const STATIC_EMPTY_BLOCKS: readonly StaticBlockConfig[] = [
  { key: "assessments", icon: <Award /> },
  { key: "practice", icon: <BookOpen /> },
  { key: "supportRequests", icon: <LifeBuoy />, fullWidth: true },
];

const ENTITLEMENT_STATUS_TONES: Record<StudentLearningEntitlement["status"], StatusTone> = {
  ACTIVE: "success",
  IN_USE: "info",
  EXHAUSTED: "neutral",
  REVOKED: "danger",
};

const ATTEMPT_STATUS_TONES: Record<StudentLearningAttempt["status"], StatusTone> = {
  IN_PROGRESS: "info",
  GRADING: "warning",
  PENDING_REVIEW: "warning",
  SCORED: "success",
  GRADING_ERROR: "danger",
};

function resolveEntitlementExamDisplay(item: StudentLearningEntitlement): {
  primaryLabel: string;
  secondaryCode: string | null;
} {
  const trimmedName = item.examName?.trim() ?? "";
  const trimmedCode = item.examCode?.trim() ?? "";
  const primaryLabel = trimmedName || trimmedCode || item.examId;
  const secondaryCode = trimmedName && trimmedCode ? trimmedCode : null;
  return { primaryLabel, secondaryCode };
}

function resolveAttemptExamLabel(item: StudentLearningAttempt): string {
  return item.examName.trim() || item.examId;
}

function formatAttemptShortId(attemptId: string): string {
  return attemptId.slice(0, 8).toUpperCase();
}

function formatAttemptDateTime(
  iso: string | null | undefined,
  format: ReturnType<typeof useFormatter>,
): string {
  if (!iso) return "—";
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) return "—";
  return format.dateTime(parsed, "dateTime");
}

function formatAttemptScore(
  attempt: StudentLearningAttempt,
  format: ReturnType<typeof useFormatter>,
): string {
  // Guard against leaking scores or rendering 0 for any non-SCORED attempt.
  if (attempt.status !== "SCORED" || attempt.score == null || !Number.isFinite(attempt.score)) {
    return "—";
  }
  const formattedScore = format.number(attempt.score, DISPLAY_NUMBER_FORMAT);
  if (attempt.maxScore != null && Number.isFinite(attempt.maxScore)) {
    return `${formattedScore} / ${format.number(attempt.maxScore, DISPLAY_NUMBER_FORMAT)}`;
  }
  return formattedScore;
}

function EntitlementStatusChip({ status }: { status: StudentLearningEntitlement["status"] }) {
  const t = useTranslations("users");
  const known = Object.hasOwn(ENTITLEMENT_STATUS_TONES, status);
  return (
    <StatusChip tone={known ? ENTITLEMENT_STATUS_TONES[status] : "neutral"}>
      {known ? t(`learning.entitlementStatuses.${status}`) : "—"}
    </StatusChip>
  );
}

function AttemptStatusChip({ status }: { status: StudentLearningAttempt["status"] }) {
  const t = useTranslations("users");
  const known = Object.hasOwn(ATTEMPT_STATUS_TONES, status);
  return (
    <StatusChip tone={known ? ATTEMPT_STATUS_TONES[status] : "neutral"}>
      {known ? t(`learning.attemptStatuses.${status}`) : "—"}
    </StatusChip>
  );
}

export interface LearningProfileTabProps {
  userId: string;
  getUserLearning: UsersApiDeps["getUserLearning"];
}

export function LearningProfileTab({ userId, getUserLearning }: LearningProfileTabProps) {
  const t = useTranslations("users");
  const common = useTranslations("common");
  const format = useFormatter();
  const { can } = usePermissions();
  const canViewLearning = can("USER.LEARNING_VIEW");

  const learningQuery = useQuery({
    queryKey: USERS_QUERY_KEYS.learning(userId),
    queryFn: () => getUserLearning(userId),
    enabled: canViewLearning,
  });

  const entitlementColumns = useMemo<ColumnDef<StudentLearningEntitlement>[]>(
    () => [
      {
        id: "exam",
        header: t("learning.entitlementsTable.columns.exam"),
        enableSorting: false,
        cell: ({ row }) => {
          const { primaryLabel, secondaryCode } = resolveEntitlementExamDisplay(row.original);
          return (
            <div className="flex min-w-44 flex-col gap-0.5 py-1.5">
              <span data-exam-content="" className="line-clamp-2 font-medium text-text">
                {primaryLabel}
              </span>
              {secondaryCode && (
                <span data-exam-content="" className="font-mono text-caption text-text-muted tabular-nums">
                  {secondaryCode}
                </span>
              )}
            </div>
          );
        },
      },
      {
        id: "attemptsTotal",
        header: t("learning.entitlementsTable.columns.attemptsTotal"),
        enableSorting: false,
        cell: ({ row }) => <span className="tabular-nums">{format.number(row.original.attemptsTotal)}</span>,
      },
      {
        id: "attemptsUsed",
        header: t("learning.entitlementsTable.columns.attemptsUsed"),
        enableSorting: false,
        cell: ({ row }) => <span className="tabular-nums">{format.number(row.original.attemptsUsed)}</span>,
      },
      {
        id: "attemptsLeft",
        header: t("learning.entitlementsTable.columns.attemptsLeft"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="font-medium text-text tabular-nums">{format.number(row.original.attemptsLeft)}</span>
        ),
      },
      {
        id: "status",
        header: t("learning.entitlementsTable.columns.status"),
        enableSorting: false,
        cell: ({ row }) => <EntitlementStatusChip status={row.original.status} />,
      },
    ],
    [t, format],
  );

  const entitlementCards = useMemo<TableCards<StudentLearningEntitlement>>(
    () => ({
      render: (item) => {
        const { primaryLabel, secondaryCode } = resolveEntitlementExamDisplay(item);
        return (
          <ListCard
            title={<span data-exam-content="">{primaryLabel}</span>}
            caption={
              secondaryCode ? (
                <span data-exam-content="" className="font-mono tabular-nums">
                  {secondaryCode}
                </span>
              ) : undefined
            }
            status={<EntitlementStatusChip status={item.status} />}
            meta={[
              {
                label: t("learning.entitlementsTable.columns.attemptsTotal"),
                value: format.number(item.attemptsTotal),
              },
              {
                label: t("learning.entitlementsTable.columns.attemptsUsed"),
                value: format.number(item.attemptsUsed),
              },
              {
                label: t("learning.entitlementsTable.columns.attemptsLeft"),
                value: format.number(item.attemptsLeft),
              },
              item.attemptsReserved > 0 && {
                label: t("learning.entitlementsTable.reservedLabel"),
                value: format.number(item.attemptsReserved),
              },
              item.attemptsRevoked > 0 && {
                label: t("learning.entitlementsTable.revokedLabel"),
                value: format.number(item.attemptsRevoked),
              },
            ]}
          />
        );
      },
    }),
    [t, format],
  );

  const attemptColumns = useMemo<ColumnDef<StudentLearningAttempt>[]>(
    () => [
      {
        id: "attempt",
        header: t("learning.attemptsTable.columns.attempt"),
        enableSorting: false,
        cell: ({ row }) => {
          const shortId = formatAttemptShortId(row.original.attemptId);
          return (
            <div className="flex flex-col gap-0.5 py-1.5">
              {row.original.attemptNo != null && (
                <span className="font-medium whitespace-nowrap text-text tabular-nums">
                  {t("learning.attemptNumber", { number: format.number(row.original.attemptNo) })}
                </span>
              )}
              <span className="font-mono text-caption text-text-muted tabular-nums">{shortId}</span>
            </div>
          );
        },
      },
      {
        id: "exam",
        header: t("learning.attemptsTable.columns.exam"),
        enableSorting: false,
        cell: ({ row }) => (
          <span data-exam-content="" className="line-clamp-2 block min-w-40 font-medium text-text">
            {resolveAttemptExamLabel(row.original)}
          </span>
        ),
      },
      {
        id: "status",
        header: t("learning.attemptsTable.columns.status"),
        enableSorting: false,
        cell: ({ row }) => <AttemptStatusChip status={row.original.status} />,
      },
      {
        id: "startedAt",
        header: t("learning.attemptsTable.columns.startedAt"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="whitespace-nowrap text-text-secondary tabular-nums">
            {formatAttemptDateTime(row.original.startedAt, format)}
          </span>
        ),
      },
      {
        id: "score",
        header: t("learning.attemptsTable.columns.score"),
        enableSorting: false,
        cell: ({ row }) => (
          <span className="font-medium whitespace-nowrap text-text tabular-nums">
            {formatAttemptScore(row.original, format)}
          </span>
        ),
      },
    ],
    [t, format],
  );

  const attemptCards = useMemo<TableCards<StudentLearningAttempt>>(
    () => ({
      render: (item) => {
        const shortId = formatAttemptShortId(item.attemptId);
        return (
          <ListCard
            title={<span data-exam-content="">{resolveAttemptExamLabel(item)}</span>}
            caption={
              <span className="tabular-nums">
                {item.attemptNo != null ? (
                  <>
                    {t("learning.attemptNumber", { number: format.number(item.attemptNo) })}
                    {" · "}
                    <span className="font-mono">{shortId}</span>
                  </>
                ) : (
                  <span className="font-mono">{shortId}</span>
                )}
              </span>
            }
            status={<AttemptStatusChip status={item.status} />}
            meta={[
              {
                label: t("learning.attemptsTable.columns.startedAt"),
                value: formatAttemptDateTime(item.startedAt, format),
              },
              {
                label: t("learning.attemptsTable.columns.score"),
                value: formatAttemptScore(item, format),
              },
            ]}
          />
        );
      },
    }),
    [t, format],
  );

  if (!canViewLearning) {
    return null;
  }

  if (learningQuery.isLoading) {
    return (
      <div
        role="status"
        aria-label={common("loading")}
        className="grid grid-cols-1 gap-4 @2xl:grid-cols-2"
      >
        {SKELETON_BLOCKS.map((block) => (
          <Card
            key={block.key}
            className={block.fullWidth ? "flex flex-col gap-4 @2xl:col-span-2" : "flex flex-col gap-4"}
          >
            <Skeleton className="h-6 w-48" />
            <Skeleton className="h-32 w-full" />
          </Card>
        ))}
      </div>
    );
  }

  if (learningQuery.isError) {
    return (
      <Card>
        <EmptyState
          icon={<TriangleAlert />}
          title={t("learning.errorTitle")}
          description={t("learning.errorDescription")}
          action={
            <Button
              variant="secondary"
              loading={learningQuery.isFetching}
              onClick={() => void learningQuery.refetch()}
            >
              {common("retry")}
            </Button>
          }
        />
      </Card>
    );
  }

  const data: StudentLearning | undefined = learningQuery.data;
  if (!data) {
    return null;
  }

  return (
    <div
      data-slot="learning-profile-tab"
      data-data-available={String(data.dataAvailable)}
      className="grid grid-cols-1 gap-4 @2xl:grid-cols-2"
    >
      <Card
        data-slot="learning-profile-block"
        data-block="entitlements"
        padding="none"
        className="flex flex-col overflow-hidden @2xl:col-span-2"
      >
        <div className={data.entitlements.length > 0 ? "border-b border-border px-6 py-4" : "px-6 pt-6 pb-2"}>
          <SectionTitle
            title={t("learning.blocks.entitlements.title")}
            count={data.entitlements.length > 0 ? format.number(data.entitlements.length) : undefined}
          />
        </div>
        {data.entitlements.length === 0 ? (
          <EmptyState
            icon={<KeyRound />}
            headingLevel={3}
            title={t("learning.emptyTitle")}
            description={t("learning.blocks.entitlements.description")}
            className="py-8"
          />
        ) : (
          <Table
            aria-label={t("learning.entitlementsTable.ariaLabel")}
            columns={entitlementColumns}
            data={data.entitlements}
            getRowId={(row) => row.entitlementId}
            cards={entitlementCards}
            empty={
              <EmptyState
                icon={<KeyRound />}
                headingLevel={3}
                title={t("learning.emptyTitle")}
                description={t("learning.blocks.entitlements.description")}
                className="py-8"
              />
            }
          />
        )}
      </Card>

      <Card
        data-slot="learning-profile-block"
        data-block="attempts"
        padding="none"
        className="flex flex-col overflow-hidden @2xl:col-span-2"
      >
        <div className={data.attempts.length > 0 ? "border-b border-border px-6 py-4" : "px-6 pt-6 pb-2"}>
          <SectionTitle
            title={t("learning.blocks.attempts.title")}
            count={data.attempts.length > 0 ? format.number(data.attempts.length) : undefined}
          />
        </div>
        {data.attempts.length === 0 ? (
          <EmptyState
            icon={<ClipboardList />}
            headingLevel={3}
            title={t("learning.emptyTitle")}
            description={t("learning.blocks.attempts.description")}
            className="py-8"
          />
        ) : (
          <Table
            aria-label={t("learning.attemptsTable.ariaLabel")}
            columns={attemptColumns}
            data={data.attempts}
            getRowId={(row) => row.attemptId}
            cards={attemptCards}
            empty={
              <EmptyState
                icon={<ClipboardList />}
                headingLevel={3}
                title={t("learning.emptyTitle")}
                description={t("learning.blocks.attempts.description")}
                className="py-8"
              />
            }
          />
        )}
      </Card>

      {STATIC_EMPTY_BLOCKS.map((block) => (
        <Card
          key={block.key}
          data-slot="learning-profile-block"
          data-block={block.key}
          className={block.fullWidth ? "flex flex-col gap-2 @2xl:col-span-2" : "flex flex-col gap-2"}
        >
          <SectionTitle title={t(`learning.blocks.${block.key}.title`)} />
          <EmptyState
            icon={block.icon}
            headingLevel={3}
            title={t("learning.emptyTitle")}
            description={t(`learning.blocks.${block.key}.description`)}
            className="py-8"
          />
        </Card>
      ))}
    </div>
  );
}
